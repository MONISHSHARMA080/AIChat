import { AppError } from "../errors.ts";
import type { AuthUser } from "../middleware/firebaseAuth.ts";
import { upsertUser } from "../repositories/userRepository.ts";

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
