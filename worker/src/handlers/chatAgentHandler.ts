import { getAgentByName } from "agents";
import { AppError } from "../errors.ts";
import { getChatById } from "../repositories/chatRepository.ts";
import type { AuthUser } from "../types/auth.ts";
import type { ChatAgentProps, ChatRow } from "../types/chat.ts";

const MAX_CHAT_ID_LENGTH = 128;

export async function handleChatAgentRequest(
	request: Request,
	env: Env,
	user: AuthUser,
	chatId: string | null,
): Promise<Response> {
	let resolvedChatId: string;
	let chat: ChatRow | null = null;

	if (chatId === null) {
		resolvedChatId = crypto.randomUUID();
	} else {
		const trimmedChatId = chatId.trim();
		if (!trimmedChatId || trimmedChatId.length > MAX_CHAT_ID_LENGTH) {
			throw new AppError("invalid-argument", "invalid chat id");
		}
		chat = await getChatById(env.DB, user.uid, trimmedChatId);
		if (!chat) {
			throw new AppError("not-found", "chat not found");
		}
		resolvedChatId = trimmedChatId;
	}

	const props: ChatAgentProps = { user, chatId: resolvedChatId, chat };
	const agentStub = await getAgentByName(env.CHAT_AGENT, `${user.uid}:${resolvedChatId}`, {
		props,
	});
	return agentStub.fetch(request);
}
