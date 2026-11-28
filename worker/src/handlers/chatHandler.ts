import { getAgentByName } from "agents";
import { z } from "zod";
import { AppError } from "../errors.ts";
import { getChatById, getChatOwnerId, listChats } from "../repositories/chatRepository.ts";
import type { AuthUser } from "../types/auth.ts";
import type { ChatAgentProps, ChatListCursor } from "../types/chat.ts";

const DEFAULT_CHAT_LIST_LIMIT = 20;
const MAX_CHAT_LIST_LIMIT = 50;
const MAX_CHAT_ID_LENGTH = 128;

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

export async function handleChatAgentRequest(
	request: Request,
	env: Env,
	user: AuthUser,
	chatId: string,
): Promise<Response> {
	const trimmedChatId = chatId.trim();
	if (!trimmedChatId || trimmedChatId.length > MAX_CHAT_ID_LENGTH) {
		throw new AppError("invalid-argument", "invalid chat id");
	}
	const ownerId = await getChatOwnerId(env.DB, trimmedChatId);
	if (ownerId && ownerId !== user.uid) {
		throw new AppError("not-found", "chat not found");
	}
	const chat = ownerId ? await getChatById(env.DB, user.uid, trimmedChatId) : null;
	const props: ChatAgentProps = { user, chatId: trimmedChatId, chat };
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${trimmedChatId}`, {
		props,
	});
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
	const trimmedChatId = chatId.trim();
	if (!trimmedChatId) {
		throw new AppError("invalid-argument", "invalid chat id");
	}
	const chat = await getChatById(env.DB, user.uid, trimmedChatId);
	if (!chat) {
		throw new AppError("not-found", "chat not found");
	}
	const props: ChatAgentProps = { user, chatId: chat.id, chat };
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${chat.id}`, { props });
	return agentStub.fetch("https://chat-agent/get-messages");
}
