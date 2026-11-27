import { AppError } from "./errors.ts";
import { handleSyncUserRequest } from "./handlers/authHandler.ts";
import {
	handleChatAgentRequest,
	handleCreateChatRequest,
	handleGetChatMessagesRequest,
	handleListChatsRequest,
} from "./handlers/chatHandler.ts";
import { handleGetFileRequest } from "./handlers/filesHandler.ts";
import { handleListModelsRequest } from "./handlers/modelsHandler.ts";
import { handleGetUsageRequest } from "./handlers/usageHandler.ts";
import { withFirebaseAuth } from "./middleware/firebaseAuth.ts";

export { ChatAgent } from "./agents/chatAgent.ts";

const HEALTH_ROUTE = new URLPattern({ pathname: "/health{/}?" });
const AUTH_SYNC_ROUTE = new URLPattern({ pathname: "/auth/sync{/}?" });
const USAGE_ROUTE = new URLPattern({ pathname: "/usage{/}?" });
const MODELS_ROUTE = new URLPattern({ pathname: "/models{/}?" });
const CHATS_ROUTE = new URLPattern({ pathname: "/chats{/}?" });
const CHAT_MESSAGES_ROUTE = new URLPattern({ pathname: "/chats/:chatId/messages{/}?" });
const FILE_ROUTE = new URLPattern({ pathname: "/files/:fileId{/}?" });
const CHAT_AGENT_ROUTE = new URLPattern({ pathname: "/agents/chat-agent/:chatId{/*}?" });

function errorResponse(status: number, code: string, message: string): Response {
	return Response.json({ error: { code, message } }, { status });
}

function decodeParam(raw: string | undefined): string {
	if (!raw) {
		throw new AppError("invalid-argument", "invalid path parameter");
	}
	try {
		return decodeURIComponent(raw);
	} catch {
		throw new AppError("invalid-argument", "invalid path encoding");
	}
}

export default {
	async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		try {
			const url = new URL(request.url);
			const pathname = { pathname: url.pathname };

			if (HEALTH_ROUTE.test(pathname)) {
				if (request.method !== "GET") {
					return errorResponse(405, "invalid-argument", "method not allowed");
				}
				return Response.json({ status: "ok" });
			}

			return await withFirebaseAuth(request, env, async (user) => {
				const agentMatch = CHAT_AGENT_ROUTE.exec(pathname);
				if (agentMatch) {
					return await handleChatAgentRequest(
						request,
						env,
						user,
						decodeParam(agentMatch.pathname.groups.chatId),
					);
				}

				if (AUTH_SYNC_ROUTE.test(pathname)) {
					if (request.method !== "POST") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleSyncUserRequest(env, user);
				}

				if (USAGE_ROUTE.test(pathname)) {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleGetUsageRequest(env, user);
				}

				if (MODELS_ROUTE.test(pathname)) {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleListModelsRequest(env, ctx);
				}

				if (CHATS_ROUTE.test(pathname)) {
					if (request.method === "GET") {
						return await handleListChatsRequest(request, env, user);
					}
					if (request.method === "POST") {
						return await handleCreateChatRequest(request, env, user);
					}
					return errorResponse(405, "invalid-argument", "method not allowed");
				}

				const chatMessagesMatch = CHAT_MESSAGES_ROUTE.exec(pathname);
				if (chatMessagesMatch) {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleGetChatMessagesRequest(
						env,
						user,
						decodeParam(chatMessagesMatch.pathname.groups.chatId),
					);
				}

				const fileMatch = FILE_ROUTE.exec(pathname);
				if (fileMatch) {
					if (request.method !== "GET") {
						return errorResponse(405, "invalid-argument", "method not allowed");
					}
					return await handleGetFileRequest(
						env,
						user,
						decodeParam(fileMatch.pathname.groups.fileId),
					);
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
