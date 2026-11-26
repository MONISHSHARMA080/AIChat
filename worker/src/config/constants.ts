import { z } from "zod";
import { AppError } from "../types/errors.ts";

export const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";
export const VERCEL_AI_GATEWAY_MODELS_URL = "https://ai-gateway.vercel.sh/v1/models";
export const MODEL_CATALOG_CACHE_URL = "https://cache.aichat.internal/ai-models/catalog";
export const FIREBASE_JWKS_URL =
	"https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

export const DEFAULT_MODEL_CACHE_TTL_HOURS = 6;
export const MS_PER_HOUR = 60 * 60 * 1000;
export const JWKS_REFRESH_COOLDOWN_MS = 60_000;
export const AUTH_CLOCK_SKEW_SECONDS = 60;
export const TITLE_LENGTH = 48;
export const PREVIEW_LENGTH = 200;
export const MAX_AGENT_STEPS = 6;
export const MIN_OUTPUT_RESERVE_TOKENS = 1024;
export const MAX_OUTPUT_TOKENS_CAP = 8192;
export const MAX_GENERATED_FILES = 10;
export const MAX_GENERATED_FILE_BYTES = 8 * 1024 * 1024;
export const REQUEST_TIMEOUT_MS = 15_000;
export const DEFAULT_CHAT_LIST_LIMIT = 20;
export const MAX_CHAT_LIST_LIMIT = 50;

const EXTENSION_BY_MIME_TYPE: Record<string, string> = {
	"text/plain": "txt",
	"text/markdown": "md",
	"application/json": "json",
	"application/pdf": "pdf",
	"image/jpeg": "jpg",
	"image/svg+xml": "svg",
};

export function extensionForMediaType(mediaType: string): string {
	const base = mediaType.split(";")[0].trim().toLowerCase();
	return EXTENSION_BY_MIME_TYPE[base] ?? (base.split("/")[1]?.split("+")[0] || "bin");
}

export function buildFileR2Key(
	uid: string,
	chatId: string,
	fileId: string,
	originalName: string,
): string {
	const trimmed = originalName.trim();
	let sanitized = "";
	let lastWasUnderscore = false;

	for (let index = 0; index < trimmed.length; index += 1) {
		const code = trimmed.charCodeAt(index);
		const isAlphaNum =
			(code >= 48 && code <= 57) || (code >= 65 && code <= 90) || (code >= 97 && code <= 122);
		const isAllowedPunct = code === 46 || code === 45 || code === 95;
		if (isAlphaNum || isAllowedPunct) {
			sanitized += trimmed[index];
			lastWasUnderscore = code === 95;
		} else if (!lastWasUnderscore) {
			sanitized += "_";
			lastWasUnderscore = true;
		}
	}

	let startIndex = 0;
	while (
		startIndex < sanitized.length &&
		(sanitized[startIndex] === "." || sanitized[startIndex] === "_")
	) {
		startIndex += 1;
	}
	const cleaned = sanitized.slice(startIndex, startIndex + 80) || "file";
	return `users/${uid}/chats/${chatId}/${fileId}-${cleaned}`;
}

export const firebaseAuthConfigSchema = z.object({
	FIREBASE_PROJECT_ID: z.string().min(1),
});

export const modelCatalogConfigSchema = firebaseAuthConfigSchema.extend({
	AI_GATEWAY_API_KEY: z.string().min(1),
	MODEL_CACHE_TTL_HOURS: z.coerce.number().positive().default(DEFAULT_MODEL_CACHE_TTL_HOURS),
	LOGO_BASE_URL: z.string().default(""),
});

export const runtimeConfigSchema = modelCatalogConfigSchema.extend({
	REVENUECAT_PROJECT_ID: z.string().min(1),
	REVENUECAT_COIN_CODE: z.string().min(1).default("COIN"),
	USD_PER_COIN: z.coerce.number().positive(),
	REVENUECAT_SECRET_API_KEY: z.string().min(1),
});

export function parseEnv<T>(schema: z.ZodType<T>, rawEnv: unknown): T {
	const parsed = schema.safeParse(rawEnv);
	if (!parsed.success) {
		console.error(JSON.stringify({ event: "invalid_environment", issues: parsed.error.issues }));
		throw new AppError("internal", "server misconfigured");
	}
	return parsed.data;
}
