import { z } from "zod";
import { AppError } from "../errors.ts";
import { listChats } from "../repositories/chatRepository.ts";
import type { AuthUser } from "../types/auth.ts";
import type { ChatListCursor } from "../types/chat.ts";

const DEFAULT_CHAT_LIST_LIMIT = 20;
const MAX_CHAT_LIST_LIMIT = 50;

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
