import { AppError } from "../errors.ts";
import { upsertUser } from "../repositories/userRepository.ts";
import type { AuthUser } from "../types/auth.ts";

export async function handleSyncUserRequest(env: Env, user: AuthUser): Promise<Response> {
	try {
		const syncedUser = await upsertUser(env.DB, user);
		return Response.json(syncedUser);
	} catch (error) {
		console.error(
			JSON.stringify({
				event: "sync_user_failed",
				uid: user.uid,
				error: error instanceof Error ? error.message : String(error),
			}),
		);
		throw new AppError("internal", "could not sync user");
	}
}
