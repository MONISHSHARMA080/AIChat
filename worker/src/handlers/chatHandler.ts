import { getAgentByName } from "agents";
import { z } from "zod";
import { parseEnv, runtimeConfigSchema } from "../env.ts";
import { AppError, resolveOrThrowUnavailable } from "../errors.ts";
import { getChatById, listChats } from "../repositories/chatRepository.ts";
import { assertSufficientCredits, getUserCoinBalance } from "../services/billingService.ts";
import {
	MAX_USER_ATTACHMENTS_PER_MESSAGE,
	MAX_USER_TEXT_CHARS,
} from "../services/chatMediaService.ts";
import { fetchAvailableModels, resolveChatModel } from "../services/modelCatalogService.ts";
import type { AuthUser } from "../types/auth.ts";
import type { ChatListCursor, ChatTurnPayload } from "../types/chat.ts";

const DEFAULT_CHAT_LIST_LIMIT = 20;
const MAX_CHAT_LIST_LIMIT = 50;

const attachmentSchema = z.object({
	name: z.string().trim().min(1).max(255),
	mimeType: z.string().trim().min(1).max(128),
	data: z.string().min(1),
});

const chatRequestBodySchema = z.object({
	chatId: z.string().trim().min(1).max(128).nullable().optional(),
	model: z.string().trim().min(1).max(128),
	reasoning: z.string().trim().min(1).max(64).optional(),
	message: z
		.object({
			text: z.string().max(MAX_USER_TEXT_CHARS).default(""),
			attachments: z.array(attachmentSchema).max(MAX_USER_ATTACHMENTS_PER_MESSAGE).default([]),
		})
		.refine((message) => message.text.trim().length > 0 || message.attachments.length > 0, {
			message: "message needs text or attachments",
		}),
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

export async function handleChatRequest(
	request: Request,
	env: Env,
	ctx: ExecutionContext,
	user: AuthUser,
): Promise<Response> {
	let body: unknown;
	try {
		body = await request.json();
	} catch {
		throw new AppError("invalid-argument", "invalid json body");
	}

	const parsed = chatRequestBodySchema.safeParse(body);
	if (!parsed.success) {
		const formattedIssues = parsed.error.issues
			.map((issue) => {
				const path = issue.path.map(String).join(".");
				return path ? `${path}: ${issue.message}` : issue.message;
			})
			.join("; ");
		throw new AppError("invalid-argument", formattedIssues || "invalid chat payload");
	}

	const config = parseEnv(runtimeConfigSchema, env);
	const [availableModels, coinBalance, existingChat] = await Promise.all([
		resolveOrThrowUnavailable(
			fetchAvailableModels({
				apiKey: config.AI_GATEWAY_API_KEY,
				logoBaseUrl: config.LOGO_BASE_URL,
				cacheTtlHours: config.MODEL_CACHE_TTL_HOURS,
				ctx,
			}),
			"models are temporarily unavailable",
		),
		resolveOrThrowUnavailable(
			getUserCoinBalance(config, user.uid),
			"balance is temporarily unavailable",
		),
		parsed.data.chatId ? getChatById(env.DB, user.uid, parsed.data.chatId) : Promise.resolve(null),
	]);

	if (parsed.data.chatId && !existingChat) {
		throw new AppError("not-found", "chat not found");
	}

	const model = resolveChatModel(availableModels, parsed.data.model, parsed.data.reasoning);
	assertSufficientCredits({
		coinBalance,
		usdPerCoin: config.USD_PER_COIN,
		pricing: model.pricing,
		recentHistory: [],
		userText: parsed.data.message.text,
		newAttachmentCount: parsed.data.message.attachments.length,
	});

	const chatId = existingChat ? existingChat.id : crypto.randomUUID();
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${chatId}`);
	const payload: ChatTurnPayload = {
		user,
		chatId,
		coinBalance,
		model,
		...(parsed.data.reasoning !== undefined ? { reasoning: parsed.data.reasoning } : {}),
		message: parsed.data.message,
	};

	return agentStub.fetch("https://chat-agent/turn", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(payload),
		signal: request.signal,
	});
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
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${chat.id}`);
	return agentStub.fetch("https://chat-agent/messages");
}
