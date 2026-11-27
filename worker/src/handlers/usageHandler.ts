import { parseEnv, revenueCatConfigSchema } from "../env.ts";
import { resolveOrThrowUnavailable } from "../errors.ts";
import { getUserUsage } from "../services/billingService.ts";
import type { AuthUser } from "../types/auth.ts";

export async function handleGetUsageRequest(env: Env, user: AuthUser): Promise<Response> {
	const config = parseEnv(revenueCatConfigSchema, env);
	const usage = await resolveOrThrowUnavailable(
		getUserUsage(config, user.uid),
		"balance is temporarily unavailable",
	);
	return Response.json(usage);
}
