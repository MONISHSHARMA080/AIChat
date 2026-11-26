import { createGateway } from "ai";
import {
	MODEL_CATALOG_CACHE_URL,
	MS_PER_HOUR,
	REQUEST_TIMEOUT_MS,
	VERCEL_AI_GATEWAY_MODELS_URL,
} from "../config/constants.ts";
import {
	cachedModelCatalogSchema,
	catalogSchema,
	type CachedModelCatalog,
	type FetchAvailableModelsOptions,
	type ModelInfo,
	type ModelReasoning,
} from "../types/models.ts";

export async function fetchAvailableModels(
	options: FetchAvailableModelsOptions,
): Promise<ModelInfo[]> {
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

	const [sdkCatalog, reasoningResponse] = await Promise.all([
		createGateway({ apiKey: options.apiKey }).getAvailableModels(),
		fetch(VERCEL_AI_GATEWAY_MODELS_URL, {
			signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
		}),
	]);

	if (!reasoningResponse.ok) {
		throw new Error(`catalog request failed: ${reasoningResponse.status}`);
	}
	const parsedCatalog = catalogSchema.parse(await reasoningResponse.json());
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

	const baseLogoUrl = options.logoBaseUrl.endsWith("/")
		? options.logoBaseUrl.slice(0, -1)
		: options.logoBaseUrl;

	const models: ModelInfo[] = sdkCatalog.models
		.filter((model) => (model.modelType ?? "language") === "language")
		.map((model) => {
			const input = Number(model.pricing?.input);
			const output = Number(model.pricing?.output);
			const provider = model.id.split("/")[0] ?? "other";
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
				logoUrl: baseLogoUrl ? `${baseLogoUrl}/logos/${provider.toLowerCase()}.svg` : null,
			};
		});

	const cachePayload: CachedModelCatalog = {
		fetchedAt: Date.now(),
		models,
	};
	const cacheWrite = caches.default
		.put(
			cacheKeyRequest,
			new Response(JSON.stringify(cachePayload), {
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
