import { z } from "zod";

export const catalogSchema = z.object({
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

export const modelPricingSchema = z.object({
	input: z.number(),
	output: z.number(),
});

export const modelReasoningSchema = z.object({
	levels: z.array(z.string()),
	toggle: z.boolean(),
	budgetTokens: z.boolean(),
});

export const modelInfoSchema = z.object({
	id: z.string(),
	name: z.string(),
	description: z.string().nullable(),
	pricing: modelPricingSchema.nullable(),
	reasoning: modelReasoningSchema,
	provider: z.string(),
	logoUrl: z.string().nullable(),
});

export const cachedModelCatalogSchema = z.object({
	fetchedAt: z.number(),
	models: z.array(modelInfoSchema),
});

export type ModelPricing = z.infer<typeof modelPricingSchema>;
export type ModelReasoning = z.infer<typeof modelReasoningSchema>;
export type ModelInfo = z.infer<typeof modelInfoSchema>;
export type CachedModelCatalog = z.infer<typeof cachedModelCatalogSchema>;

export interface FetchAvailableModelsOptions {
	apiKey: string;
	logoBaseUrl: string;
	cacheTtlHours: number;
	ctx?: Pick<ExecutionContext, "waitUntil">;
}

export interface ListModelsResponse {
	models: ModelInfo[];
}
