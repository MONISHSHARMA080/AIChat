import type { AuthUser } from "./auth.ts";

export interface ModelPricing {
	input: number;
	output: number;
}

export interface ChatRow {
	id: string;
	userId: string;
	title: string;
	modelId: string;
	createdAt: number;
	updatedAt: number;
	lastMessageText: string | null;
	lastMessageAt: number | null;
}

export interface FileRow {
	id: string;
	messageId: string | null;
	chatId: string;
	userId: string;
	name: string;
	mimeType: string;
	size: number;
	r2Key: string;
	createdAt: number;
}

export interface StoredFile extends FileRow {
	url: string;
}

export interface ChatListCursor {
	updatedAt: number;
	id: string;
}

export interface AttachmentInput {
	name: string;
	mimeType: string;
	data: string;
}

export interface ChatTurnPayload {
	user: AuthUser;
	chatId: string;
	coinBalance: number;
	model: {
		id: string;
		pricing: ModelPricing;
	};
	reasoning?: string;
	message: {
		text: string;
		attachments: AttachmentInput[];
	};
}
