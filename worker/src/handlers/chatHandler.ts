import { getAgentByName } from "agents";
import { DEFAULT_CHAT_LIST_LIMIT, MAX_CHAT_LIST_LIMIT } from "../config/constants.ts";
import { listChats } from "../repositories/chatRepository.ts";
import type { FirebaseIdToken } from "../types/auth.ts";
import type { ChatListCursor, TouchUserInput } from "../types/database.ts";
import { AppError } from "../types/errors.ts";

function toTouchUserInput(user: FirebaseIdToken): TouchUserInput {
	return {
		uid: user.uid,
		email: user.email ?? null,
		displayName: user.name ?? null,
		photoUrl: user.picture ?? null,
		phoneNumber: user.phone_number ?? null,
		provider: user.firebase?.sign_in_provider ?? "firebase",
		emailVerified: user.email_verified === true,
		isAnonymous: user.firebase?.sign_in_provider === "anonymous",
		tenantId: user.firebase?.tenant ?? null,
	};
}

export async function handleChatAgentRequest(
	request: Request,
	env: Env,
	user: FirebaseIdToken,
	chatId: string,
): Promise<Response> {
	if (!chatId.trim()) {
		throw new AppError("invalid-argument", "invalid chat id");
	}
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${chatId}`, {
		props: {
			user: toTouchUserInput(user),
			chatId,
		},
	});
	return agentStub.fetch(request);
}

export async function handleListChatsRequest(
	request: Request,
	env: Env,
	user: FirebaseIdToken,
): Promise<Response> {
	const url = new URL(request.url);

	let limit = DEFAULT_CHAT_LIST_LIMIT;
	const limitParam = url.searchParams.get("limit");
	if (limitParam !== null) {
		const parsedLimit = Number(limitParam);
		if (
			limitParam.trim() === "" ||
			!Number.isInteger(parsedLimit) ||
			parsedLimit < 1 ||
			parsedLimit > MAX_CHAT_LIST_LIMIT
		) {
			throw new AppError(
				"invalid-argument",
				`limit must be an integer between 1 and ${MAX_CHAT_LIST_LIMIT}`,
			);
		}
		limit = parsedLimit;
	}

	let cursor: ChatListCursor | null = null;
	const cursorParam = url.searchParams.get("cursor");
	if (cursorParam !== null) {
		const separatorIndex = cursorParam.indexOf(":");
		if (separatorIndex <= 0 || separatorIndex === cursorParam.length - 1) {
			throw new AppError("invalid-argument", "invalid cursor");
		}
		const updatedAtText = cursorParam.slice(0, separatorIndex);
		const cursorId = cursorParam.slice(separatorIndex + 1);
		const updatedAt = Number(updatedAtText);
		if (
			updatedAtText.trim() === "" ||
			!Number.isSafeInteger(updatedAt) ||
			updatedAt < 0 ||
			cursorId.length === 0
		) {
			throw new AppError("invalid-argument", "invalid cursor");
		}
		cursor = { updatedAt, id: cursorId };
	}

	const page = await listChats(env.DB, user.uid, limit, cursor);
	return Response.json(page);
}

export async function handleGetChatMessagesRequest(
	env: Env,
	user: FirebaseIdToken,
	chatId: string,
): Promise<Response> {
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${chatId}`, {
		props: {
			user: toTouchUserInput(user),
			chatId,
		},
	});
	return agentStub.fetch("https://chat-agent/get-messages");
}
