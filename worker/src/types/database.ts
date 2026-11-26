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

export interface TouchUserInput {
	uid: string;
	email: string | null;
	displayName: string | null;
	photoUrl: string | null;
	phoneNumber: string | null;
	provider: string;
	emailVerified: boolean;
	isAnonymous: boolean;
	tenantId: string | null;
}

export interface SaveChatSummaryInput {
	user: TouchUserInput;
	chatId: string;
	title: string;
	modelId: string;
	files: FileRow[];
	preview: string;
	at: number;
}

export interface ChatListCursor {
	updatedAt: number;
	id: string;
}

export interface ChatListPage {
	chats: ChatRow[];
	nextCursor: string | null;
}
