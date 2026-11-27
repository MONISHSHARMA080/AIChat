import { z } from "zod";
import { REQUEST_TIMEOUT_MS, type RevenueCatConfig, type RuntimeConfig } from "../env.ts";
import { AppError } from "../errors.ts";

const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";

const coinBalancesSchema = z.object({
	items: z.array(
		z.object({
			currency_code: z.string().min(1),
			name: z.string().nullish(),
			balance: z.number(),
		}),
	),
});

export interface Usage {
	synced: boolean;
	balance: number;
	balances: { code: string; name: string; balance: number }[];
}

function requestRevenueCat(
	config: RevenueCatConfig,
	path: string,
	init: Omit<RequestInit, "headers"> & { headers?: Record<string, string> } = {},
): Promise<Response> {
	const url = new URL(`${REVENUECAT_BASE_URL}${path}`);
	url.searchParams.set("include_empty_balances", "true");
	return fetch(url, {
		...init,
		headers: {
			Authorization: `Bearer ${config.REVENUECAT_SECRET_API_KEY}`,
			Accept: "application/json",
			...init.headers,
		},
		signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
	});
}

function parseBalances(payload: unknown, coinCode: string): Usage | null {
	const parsed = coinBalancesSchema.safeParse(payload);
	if (!parsed.success) {
		return null;
	}
	const balances = parsed.data.items.map((item) => ({
		code: item.currency_code,
		name: item.name ?? item.currency_code,
		balance: Math.max(0, Math.floor(item.balance)),
	}));
	const balance = balances.find((entry) => entry.code === coinCode)?.balance ?? 0;
	return { synced: true, balance, balances };
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

export async function getUserUsage(config: RevenueCatConfig, uid: string): Promise<Usage> {
	const response = await requestRevenueCat(
		config,
		`/projects/${encodeURIComponent(config.REVENUECAT_PROJECT_ID)}/customers/${encodeURIComponent(uid)}/virtual_currencies`,
	);
	if (response.status === 404) {
		await response.body?.cancel();
		return { synced: false, balance: 0, balances: [] };
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
		throw new AppError("unavailable", "balance is temporarily unavailable");
	}
	const usage = parseBalances(await response.json(), config.REVENUECAT_COIN_CODE);
	if (!usage) {
		console.error(
			JSON.stringify({
				event: "revenuecat_balance_unexpected_payload",
				uid,
			}),
		);
		throw new AppError("unavailable", "balance is temporarily unavailable");
	}
	return usage;
}

export async function getUserCoinBalance(config: RevenueCatConfig, uid: string): Promise<number> {
	return (await getUserUsage(config, uid)).balance;
}

export async function deductCoinsOrThrow(
	config: RuntimeConfig,
	uid: string,
	gatewayCostUsd: number,
	idempotencyKey: string,
): Promise<void> {
	if (gatewayCostUsd <= 0) {
		return;
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
			return;
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
			parseBalances(await fallbackResponse.json(), config.REVENUECAT_COIN_CODE)?.balance ??
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
		return;
	}

	if (!response.ok) {
		throw new Error(`deduction ${response.status}: ${await response.text()}`);
	}
	await response.body?.cancel();
}
