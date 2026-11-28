import { isReasoningUIPart, isTextUIPart, safeValidateUIMessages, type UIMessage } from "ai";
import type { MessageAttachmentMetadata, MessageRow, StoredFile } from "../types/chat.ts";

export function extractMessageText(message: UIMessage): string {
	return message.parts
		.filter(isTextUIPart)
		.map((part) => part.text)
		.join("");
}

function extractMessageReasoning(message: UIMessage): string | null {
	const reasoning = message.parts
		.filter(isReasoningUIPart)
		.map((part) => part.text.trim())
		.filter((text) => text.length > 0)
		.join("\n\n");
	return reasoning.length > 0 ? reasoning : null;
}

export function buildMessageRow(input: {
	chatId: string;
	message: UIMessage;
	createdAt: number;
	status: string;
	files: readonly StoredFile[];
}): MessageRow {
	const attachments: MessageAttachmentMetadata[] = input.files.map(
		({ id, name, mimeType, size }) => ({ id, name, mimeType, size }),
	);
	return {
		id: input.message.id,
		chatId: input.chatId,
		role: input.message.role,
		content: extractMessageText(input.message),
		reasoning: extractMessageReasoning(input.message),
		parts: JSON.stringify(input.message.parts),
		createdAt: input.createdAt,
		status: input.status,
		attachments: JSON.stringify(attachments),
	};
}

function buildFallbackUIMessage(row: MessageRow): UIMessage {
	const parts: UIMessage["parts"] = [];
	if (row.reasoning) {
		parts.push({ type: "reasoning", text: row.reasoning });
	}
	if (row.content) {
		parts.push({ type: "text", text: row.content });
	}
	return {
		id: row.id,
		role: row.role,
		parts,
	};
}

function rowToCandidateUIMessage(row: MessageRow): UIMessage {
	let parsedParts: unknown = null;
	try {
		parsedParts = JSON.parse(row.parts);
	} catch {
		parsedParts = null;
	}

	if (Array.isArray(parsedParts) && parsedParts.length > 0) {
		return {
			id: row.id,
			role: row.role,
			parts: parsedParts as UIMessage["parts"],
		};
	}

	return buildFallbackUIMessage(row);
}

export async function hydrateUIMessagesFromRows(rows: readonly MessageRow[]): Promise<UIMessage[]> {
	if (rows.length === 0) {
		return [];
	}

	const candidates = rows.map(rowToCandidateUIMessage);
	const validated = await safeValidateUIMessages({ messages: candidates });
	if (validated.success) {
		return validated.data;
	}

	console.error(
		JSON.stringify({
			event: "db_messages_validation_failed",
			error: validated.error.message,
		}),
	);

	const validMessages: UIMessage[] = [];
	for (let index = 0; index < rows.length; index += 1) {
		const single = await safeValidateUIMessages({ messages: [candidates[index]] });
		if (single.success) {
			validMessages.push(...single.data);
			continue;
		}
		const fallback = await safeValidateUIMessages({
			messages: [buildFallbackUIMessage(rows[index])],
		});
		if (fallback.success && fallback.data[0].parts.length > 0) {
			validMessages.push(...fallback.data);
		}
	}
	return validMessages;
}
