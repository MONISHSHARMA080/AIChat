import { z } from "zod";
import { AppError } from "./errors.ts";

export const REQUEST_TIMEOUT_MS = 15_000;

export const firebaseAuthConfigSchema = z.object({
	FIREBASE_PROJECT_ID: z.string().min(1),
});

export const modelCatalogConfigSchema = z.object({
	AI_GATEWAY_API_KEY: z.string().min(1),
	MODEL_CACHE_TTL_HOURS: z.coerce.number().positive().default(6),
	LOGO_BASE_URL: z.string().default(""),
});

export const revenueCatConfigSchema = z.object({
	REVENUECAT_PROJECT_ID: z.string().min(1),
	REVENUECAT_COIN_CODE: z
		.string()
		.optional()
		.transform((value) => value?.trim() || "COIN"),
	REVENUECAT_SECRET_API_KEY: z.string().min(1),
});

export const runtimeConfigSchema = modelCatalogConfigSchema
	.extend(revenueCatConfigSchema.shape)
	.extend({
		USD_PER_COIN: z.coerce.number().positive(),
	});

export type RevenueCatConfig = z.infer<typeof revenueCatConfigSchema>;
export type RuntimeConfig = z.infer<typeof runtimeConfigSchema>;

export function parseEnv<T>(schema: z.ZodType<T>, rawEnv: unknown): T {
	const parsed = schema.safeParse(rawEnv);
	if (!parsed.success) {
		console.error(JSON.stringify({ event: "invalid_environment", issues: parsed.error.issues }));
		throw new AppError("internal", "server misconfigured");
	}
	return parsed.data;
}
