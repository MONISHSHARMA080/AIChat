import { REQUEST_TIMEOUT_MS, REVENUECAT_BASE_URL } from "../config/constants.ts";
import { coinBalancesSchema, type BillingSummary } from "../types/billing.ts";
import type { RuntimeConfig } from "../types/config.ts";
import { AppError } from "../types/errors.ts";

function requestRevenueCat(
	config: RuntimeConfig,
	path: string,
	init: Omit<RequestInit, "headers"> & { headers?: Record<string, string> } = {},
): Promise<Response> {
	return fetch(`${REVENUECAT_BASE_URL}${path}`, {
		...init,
		headers: {
			Authorization: `Bearer ${config.REVENUECAT_SECRET_API_KEY}`,
			...init.headers,
		},
		signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
	});
}

function parseCoinBalance(payload: unknown, coinCode: string): number | null {
	const parsed = coinBalancesSchema.safeParse(payload);
	if (!parsed.success) {
		return null;
	}
	return parsed.data.items.find((item) => item.currency_code === coinCode)?.balance ?? 0;
}

function postCoinDeduction(
	config: RuntimeConfig,
	uid: string,
	coins: number,
	idempotencyKey: string,
): Promise<Response> {
	return requestRevenueCat(
		config,
		`/projects/${encodeURIComponent(config.REVENUECAT_PROJECT_ID)}/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions`,
		{
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				"Idempotency-Key": idempotencyKey,
			},
			body: JSON.stringify({
				adjustments: { [config.REVENUECAT_COIN_CODE]: -coins },
			}),
		},
	);
}

export async function getUserCoinBalance(config: RuntimeConfig, uid: string): Promise<number> {
	const response = await requestRevenueCat(
		config,
		`/projects/${encodeURIComponent(config.REVENUECAT_PROJECT_ID)}/customers/${encodeURIComponent(uid)}/virtual_currencies`,
	);
	if (response.status === 404) {
		await response.body?.cancel();
		return 0;
	}
	if (!response.ok) {
		console.error(
			JSON.stringify({
				event: "revenuecat_balance_lookup_failed",
				uid,
				status: response.status,
				body: await response.text(),
			}),
		);
		throw new AppError("unavailable", "balance lookup failed");
	}
	const balance = parseCoinBalance(await response.json(), config.REVENUECAT_COIN_CODE);
	if (balance === null) {
		throw new AppError("unavailable", "unexpected balance response");
	}
	return balance;
}

export interface BillingDeductionResult extends BillingSummary {
	failed: boolean;
}

export async function deductCoinsOrThrow(
	config: RuntimeConfig,
	uid: string,
	userCoinBalance: number,
	gatewayCostUsd: number,
	idempotencyKey: string = crypto.randomUUID(),
): Promise<BillingSummary> {
	if (gatewayCostUsd <= 0) {
		return { charged: 0, balance: userCoinBalance };
	}

	const coinsToCharge = Math.max(
		1,
		Math.ceil(Number((gatewayCostUsd / config.USD_PER_COIN).toFixed(7))),
	);

	const response = await postCoinDeduction(config, uid, coinsToCharge, idempotencyKey);
	if (response.status === 422) {
		await response.body?.cancel();
		const currentBalance = await getUserCoinBalance(config, uid);
		const fallbackCoins = Math.min(coinsToCharge, currentBalance);
		if (fallbackCoins <= 0) {
			console.warn(
				JSON.stringify({
					event: "billing_shortfall",
					uid,
					requestedCoins: coinsToCharge,
					chargedCoins: 0,
					shortfallCoins: coinsToCharge,
					balance: currentBalance,
					gatewayCostUsd,
				}),
			);
			return { charged: 0, balance: currentBalance };
		}

		const fallbackResponse = await postCoinDeduction(
			config,
			uid,
			fallbackCoins,
			`${idempotencyKey}:fallback`,
		);
		if (!fallbackResponse.ok) {
			throw new Error(
				`fallback deduction ${fallbackResponse.status}: ${await fallbackResponse.text()}`,
			);
		}
		const updatedBalance =
			parseCoinBalance(await fallbackResponse.json(), config.REVENUECAT_COIN_CODE) ??
			Math.max(0, currentBalance - fallbackCoins);
		console.warn(
			JSON.stringify({
				event: "billing_shortfall",
				uid,
				requestedCoins: coinsToCharge,
				chargedCoins: fallbackCoins,
				shortfallCoins: coinsToCharge - fallbackCoins,
				balance: updatedBalance,
				gatewayCostUsd,
			}),
		);
		return { charged: fallbackCoins, balance: updatedBalance };
	}

	if (!response.ok) {
		throw new Error(`deduction ${response.status}: ${await response.text()}`);
	}
	const updatedBalance =
		parseCoinBalance(await response.json(), config.REVENUECAT_COIN_CODE) ??
		userCoinBalance - coinsToCharge;
	return { charged: coinsToCharge, balance: updatedBalance };
}

export async function deductCoinsForCost(
	config: RuntimeConfig,
	uid: string,
	userCoinBalance: number,
	gatewayCostUsd: number,
	idempotencyKey: string = crypto.randomUUID(),
): Promise<BillingDeductionResult> {
	try {
		const summary = await deductCoinsOrThrow(
			config,
			uid,
			userCoinBalance,
			gatewayCostUsd,
			idempotencyKey,
		);
		return { ...summary, failed: false };
	} catch (error) {
		console.error(
			JSON.stringify({
				event: "billing_deduction_failed",
				uid,
				gatewayCostUsd,
				idempotencyKey,
				error: error instanceof Error ? error.message : String(error),
			}),
		);
		return { charged: 0, balance: userCoinBalance, failed: true };
	}
}
