import { getAgentByName } from "agents";
import { z } from "zod";
import { AppError } from "../errors.ts";
import type { AuthUser } from "../middleware/firebaseAuth.ts";
import {
	createChat,
	getChatById,
	listChats,
	type ChatListCursor,
} from "../repositories/chatRepository.ts";

const DEFAULT_CHAT_LIST_LIMIT = 20;
const MAX_CHAT_LIST_LIMIT = 50;

const createChatBodySchema = z.object({
	modelId: z.string().trim().default(""),
});

const chatListLimitSchema = z
	.string()
	.trim()
	.min(1)
	.transform(Number)
	.pipe(z.number().int().min(1).max(MAX_CHAT_LIST_LIMIT));

const chatListCursorSchema = z.string().transform((value, ctx): ChatListCursor => {
	const separatorIndex = value.indexOf(":");
	if (separatorIndex <= 0 || separatorIndex === value.length - 1) {
		ctx.addIssue({ code: "custom", message: "invalid cursor" });
		return z.NEVER;
	}
	const updatedAtText = value.slice(0, separatorIndex);
	const updatedAt = Number(updatedAtText);
	if (updatedAtText.trim() === "" || !Number.isSafeInteger(updatedAt) || updatedAt < 0) {
		ctx.addIssue({ code: "custom", message: "invalid cursor" });
		return z.NEVER;
	}
	return { updatedAt, id: value.slice(separatorIndex + 1) };
});

async function getChatAgentStub(env: Env, user: AuthUser, chatId: string) {
	if (!chatId.trim()) {
		throw new AppError("invalid-argument", "invalid chat id");
	}
	const chat = await getChatById(env.DB, user.uid, chatId);
	if (!chat) {
		throw new AppError("not-found", "chat not found");
	}
	return getAgentByName(env.CHAT_AGENT, `${user.uid}:${chat.id}`, {
		props: { user, chat },
	});
}

export async function handleCreateChatRequest(
	request: Request,
	env: Env,
	user: AuthUser,
): Promise<Response> {
	const rawBody = await request.text();
	let body: unknown = {};
	if (rawBody.trim().length > 0) {
		try {
			body = JSON.parse(rawBody);
		} catch {
			throw new AppError("invalid-argument", "invalid json body");
		}
	}
	const parsed = createChatBodySchema.safeParse(body);
	if (!parsed.success) {
		throw new AppError("invalid-argument", "invalid chat payload");
	}
	const chat = await createChat(env.DB, user, parsed.data.modelId);
	return Response.json(chat, { status: 201 });
}

export async function handleChatAgentRequest(
	request: Request,
	env: Env,
	user: AuthUser,
	chatId: string,
): Promise<Response> {
	const agentStub = await getChatAgentStub(env, user, chatId);
	return agentStub.fetch(request);
}

export async function handleListChatsRequest(
	request: Request,
	env: Env,
	user: AuthUser,
): Promise<Response> {
	const url = new URL(request.url);

	let limit = DEFAULT_CHAT_LIST_LIMIT;
	const limitParam = url.searchParams.get("limit");
	if (limitParam !== null) {
		const parsedLimit = chatListLimitSchema.safeParse(limitParam);
		if (!parsedLimit.success) {
			throw new AppError(
				"invalid-argument",
				`limit must be an integer between 1 and ${MAX_CHAT_LIST_LIMIT}`,
			);
		}
		limit = parsedLimit.data;
	}

	let cursor: ChatListCursor | null = null;
	const cursorParam = url.searchParams.get("cursor");
	if (cursorParam !== null) {
		const parsedCursor = chatListCursorSchema.safeParse(cursorParam);
		if (!parsedCursor.success) {
			throw new AppError("invalid-argument", "invalid cursor");
		}
		cursor = parsedCursor.data;
	}

	const page = await listChats(env.DB, user.uid, limit, cursor);
	return Response.json(page);
}

export async function handleGetChatMessagesRequest(
	env: Env,
	user: AuthUser,
	chatId: string,
): Promise<Response> {
	const agentStub = await getChatAgentStub(env, user, chatId);
	return agentStub.fetch("https://chat-agent/get-messages");
}
