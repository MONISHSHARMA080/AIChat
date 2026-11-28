import { getAgentByName } from "agents";
import { AppError } from "../errors.ts";
import { getChatById, getChatOwnerId } from "../repositories/chatRepository.ts";
import type { AuthUser } from "../types/auth.ts";
import type { ChatAgentProps } from "../types/chat.ts";

const MAX_CHAT_ID_LENGTH = 128;

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
