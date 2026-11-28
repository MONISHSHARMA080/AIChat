import { isFileUIPart, isTextUIPart, type UIMessage } from "ai";
import { z } from "zod";
import { REQUEST_TIMEOUT_MS, type RevenueCatConfig, type RuntimeConfig } from "../env.ts";
import { AppError } from "../errors.ts";
import type { ModelPricing } from "../types/chat.ts";
import {
	extractFileIdFromUrl,
	MAX_CONTEXT_MEDIA_FILES,
	MAX_CONTEXT_MEDIA_TURNS,
} from "./chatMediaService.ts";

const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";
const MIN_OUTPUT_RESERVE_TOKENS = 256;
const MAX_OUTPUT_TOKENS_CAP = 8192;
const ESTIMATED_CHARS_PER_TOKEN = 3.2;
const ESTIMATED_TOKENS_PER_MEDIA_PART = 1000;

const gatewayCostSchema = z.object({
	gateway: z.object({
		cost: z
			.union([z.string().trim().min(1), z.number()])
			.transform(Number)
			.pipe(z.number().nonnegative()),
	}),
});

const coinBalancesSchema = z.object({
	items: z.array(
		z.object({
			currency_code: z.string().min(1),
			name: z.string().nullish(),
			balance: z.number(),
		}),
	),
});

interface Usage {
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

export function assertSufficientCredits(input: {
	coinBalance: number;
	usdPerCoin: number;
	pricing: ModelPricing;
	messages: readonly UIMessage[];
}): number {
	const availableBudgetUsd = input.coinBalance * input.usdPerCoin;
	let textChars = 0;
	let mediaCount = 0;
	const mediaWindowStart = Math.max(0, input.messages.length - (MAX_CONTEXT_MEDIA_TURNS * 2 + 1));

	for (let index = 0; index < input.messages.length; index += 1) {
		const message = input.messages[index];
		const inMediaWindow = index >= mediaWindowStart;
		for (const part of message.parts) {
			if (isTextUIPart(part)) {
				textChars += part.text.length;
			} else if (
				inMediaWindow &&
				isFileUIPart(part) &&
				(part.url.startsWith("data:") || extractFileIdFromUrl(part.url) !== null)
			) {
				mediaCount += 1;
			}
		}
	}

	const estimatedInputTokens =
		Math.ceil(textChars / ESTIMATED_CHARS_PER_TOKEN) +
		Math.min(MAX_CONTEXT_MEDIA_FILES, mediaCount) * ESTIMATED_TOKENS_PER_MEDIA_PART;
	const estimatedMinTurnUsd =
		estimatedInputTokens * input.pricing.input + MIN_OUTPUT_RESERVE_TOKENS * input.pricing.output;

	if (
		input.coinBalance <= 0 ||
		availableBudgetUsd <= 0 ||
		availableBudgetUsd < estimatedMinTurnUsd
	) {
		throw new AppError("resource-exhausted", "insufficient credits");
	}
	return availableBudgetUsd;
}

export function computeStepMaxOutputTokens(
	remainingUsd: number,
	outputPricePerToken: number,
): number {
	if (outputPricePerToken <= 0) {
		return MAX_OUTPUT_TOKENS_CAP;
	}
	return Math.min(
		MAX_OUTPUT_TOKENS_CAP,
		Math.max(
			MIN_OUTPUT_RESERVE_TOKENS,
			Math.floor(Math.max(0, remainingUsd) / outputPricePerToken),
		),
	);
}

export function extractStepCostUsd(
	step: {
		providerMetadata?: unknown;
		usage: { inputTokens?: number; outputTokens?: number };
	},
	pricing: ModelPricing,
): number {
	const parsed = gatewayCostSchema.safeParse(step.providerMetadata);
	if (parsed.success) {
		return parsed.data.gateway.cost;
	}
	return (
		(step.usage.inputTokens ?? 0) * pricing.input + (step.usage.outputTokens ?? 0) * pricing.output
	);
}
