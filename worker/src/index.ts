import {
	handleChatAgentRequest,
	handleGetChatMessagesRequest,
	handleListChatsRequest,
} from "./handlers/chatHandler.ts";
import { handleGetFileRequest } from "./handlers/filesHandler.ts";
import { handleListModelsRequest } from "./handlers/modelsHandler.ts";
import { withFirebaseAuth } from "./middleware/firebaseAuth.ts";
import { AppError } from "./types/errors.ts";

export { ChatAgent } from "./agents/chatAgent.ts";

function errorResponse(status: number, code: string, message: string): Response {
	return Response.json({ error: { code, message } }, { status });
}

function decodePathSegments(pathname: string): string[] | null {
	const rawSegments = pathname.split("/").filter(Boolean);
	const segments: string[] = [];
	for (const segment of rawSegments) {
		try {
			segments.push(decodeURIComponent(segment));
		} catch {
			return null;
		}
	}
	return segments;
}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		try {
			const url = new URL(request.url);
			const segments = decodePathSegments(url.pathname);
			if (!segments) {
				return errorResponse(400, "invalid-argument", "invalid path encoding");
			}

			if (segments.length === 1 && segments[0] === "health") {
				if (request.method !== "GET") {
					return errorResponse(405, "invalid-argument", "method not allowed");
				}
				return Response.json({ status: "ok" });
			}

			return await withFirebaseAuth(request, env, async (user) => {
				if (segments.length >= 3 && segments[0] === "agents" && segments[1] === "chat-agent") {
					return await handleChatAgentRequest(request, env, user, segments[2]);
				}

				if (segments.length === 1 && segments[0] === "models") {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleListModelsRequest(env, ctx);
				}

				if (segments.length === 1 && segments[0] === "chats") {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleListChatsRequest(request, env, user);
				}

				if (segments.length === 3 && segments[0] === "chats" && segments[2] === "messages") {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleGetChatMessagesRequest(env, user, segments[1]);
				}

				if (segments.length === 2 && segments[0] === "files") {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleGetFileRequest(env, user, segments[1]);
				}

				return errorResponse(404, "not-found", "not found");
			});
		} catch (error) {
			if (error instanceof AppError) {
				return errorResponse(error.status, error.code, error.message);
			}
			console.error(
				JSON.stringify({
					event: "unhandled_worker_error",
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			return errorResponse(500, "internal", "internal server error");
		}
	},
} satisfies ExportedHandler<Env>;
