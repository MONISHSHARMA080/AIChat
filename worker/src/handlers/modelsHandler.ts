import { modelCatalogConfigSchema, parseEnv } from "../config/constants.ts";
import { fetchAvailableModels } from "../services/modelCatalogService.ts";
import type { ListModelsResponse } from "../types/models.ts";
import { resolveOrThrowUnavailable } from "../utils/errors.ts";

export async function handleListModelsRequest(env: Env, ctx: ExecutionContext): Promise<Response> {
	const config = parseEnv(modelCatalogConfigSchema, env);

	const models = await resolveOrThrowUnavailable(
		fetchAvailableModels({
			apiKey: config.AI_GATEWAY_API_KEY,
			logoBaseUrl: config.LOGO_BASE_URL,
			cacheTtlHours: config.MODEL_CACHE_TTL_HOURS,
			ctx,
		}),
		"could not load models",
	);
	const responseBody: ListModelsResponse = { models };
	return Response.json(responseBody);
}
