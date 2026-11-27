import { createGateway } from "ai";
import { z } from "zod";
import { REQUEST_TIMEOUT_MS } from "../env.ts";

const VERCEL_AI_GATEWAY_MODELS_URL = "https://ai-gateway.vercel.sh/v1/models";
const MODEL_CATALOG_CACHE_URL = "https://cache.aichat.internal/ai-models/catalog";
const MS_PER_HOUR = 60 * 60 * 1000;

const catalogSchema = z.object({
	data: z.array(
		z.object({
			id: z.string(),
			reasoning_options: z
				.array(
					z.object({
						type: z.string(),
						values: z.array(z.string()).nullish(),
					}),
				)
				.nullish(),
		}),
	),
});

const modelReasoningSchema = z.object({
	levels: z.array(z.string()),
	toggle: z.boolean(),
	budgetTokens: z.boolean(),
});

const modelInfoSchema = z.object({
	id: z.string(),
	name: z.string(),
	description: z.string().nullable(),
	pricing: z.object({ input: z.number(), output: z.number() }).nullable(),
	reasoning: modelReasoningSchema,
	provider: z.string(),
	logoUrl: z.string().nullable(),
});

const cachedModelCatalogSchema = z.object({
	fetchedAt: z.number(),
	models: z.array(modelInfoSchema),
});

type ModelReasoning = z.infer<typeof modelReasoningSchema>;
export type ModelInfo = z.infer<typeof modelInfoSchema>;

function fetchWithTimeout(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
	const timeoutSignal = AbortSignal.timeout(REQUEST_TIMEOUT_MS);
	const signal = init?.signal ? AbortSignal.any([init.signal, timeoutSignal]) : timeoutSignal;
	return fetch(input, { ...init, signal });
}

export async function fetchAvailableModels(options: {
	apiKey: string;
	logoBaseUrl: string;
	cacheTtlHours: number;
	ctx?: Pick<ExecutionContext, "waitUntil">;
}): Promise<ModelInfo[]> {
	const cacheTtlMs = options.cacheTtlHours * MS_PER_HOUR;
	const cacheTtlSeconds = Math.max(1, Math.floor(cacheTtlMs / 1000));
	const cacheKeyRequest = new Request(MODEL_CATALOG_CACHE_URL, { method: "GET" });

	try {
		const cachedResponse = await caches.default.match(cacheKeyRequest);
		if (cachedResponse) {
			const parsed = cachedModelCatalogSchema.safeParse(await cachedResponse.json());
			if (parsed.success && Date.now() - parsed.data.fetchedAt < cacheTtlMs) {
				return parsed.data.models;
			}
		}
	} catch (error) {
		console.warn(
			JSON.stringify({
				event: "model_catalog_cache_read_failed",
				error: error instanceof Error ? error.message : String(error),
			}),
		);
	}

	const [sdkCatalog, parsedCatalog] = await Promise.all([
		createGateway({
			apiKey: options.apiKey,
			fetch: fetchWithTimeout,
		}).getAvailableModels(),
		(async () => {
			const response = await fetchWithTimeout(VERCEL_AI_GATEWAY_MODELS_URL);
			if (!response.ok) {
				await response.body?.cancel();
				throw new Error(`catalog request failed: ${response.status}`);
			}
			return catalogSchema.parse(await response.json());
		})(),
	]);

	const reasoningByModel = new Map<string, ModelReasoning>(
		parsedCatalog.data.map((entry) => {
			const reasoningOptions = entry.reasoning_options ?? [];
			const levels = reasoningOptions
				.filter((option) => option.type === "effort")
				.flatMap((option) => option.values ?? []);
			return [
				entry.id,
				{
					levels: [...new Set(levels)],
					toggle: reasoningOptions.some((option) => option.type === "toggle"),
					budgetTokens: reasoningOptions.some((option) => option.type === "budget_tokens"),
				},
			];
		}),
	);

	const trimmedLogoBase = options.logoBaseUrl.trim();
	const normalizedLogoBase = trimmedLogoBase
		? trimmedLogoBase.endsWith("/")
			? trimmedLogoBase
			: `${trimmedLogoBase}/`
		: "";

	const models: ModelInfo[] = sdkCatalog.models
		.filter((model) => (model.modelType ?? "language") === "language")
		.map((model) => {
			const input = Number(model.pricing?.input);
			const output = Number(model.pricing?.output);
			const provider = model.id.split("/")[0] || "other";
			return {
				id: model.id,
				name: model.name,
				description: model.description ?? null,
				pricing: Number.isFinite(input) && Number.isFinite(output) ? { input, output } : null,
				reasoning: reasoningByModel.get(model.id) ?? {
					levels: [],
					toggle: false,
					budgetTokens: false,
				},
				provider,
				logoUrl: normalizedLogoBase
					? new URL(`logos/${provider.toLowerCase()}.svg`, normalizedLogoBase).toString()
					: null,
			};
		});

	const cacheWrite = caches.default
		.put(
			cacheKeyRequest,
			new Response(JSON.stringify({ fetchedAt: Date.now(), models }), {
				headers: {
					"Content-Type": "application/json",
					"Cache-Control": `public, max-age=${cacheTtlSeconds}`,
				},
			}),
		)
		.catch((error) => {
			console.warn(
				JSON.stringify({
					event: "model_catalog_cache_write_failed",
					error: error instanceof Error ? error.message : String(error),
				}),
			);
		});

	if (options.ctx) {
		options.ctx.waitUntil(cacheWrite);
	} else {
		await cacheWrite;
	}

	return models;
}
