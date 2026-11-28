import type { AuthUser } from "../types/auth.ts";
import type { ChatListCursor, ChatRow, FileRow, MessageRow } from "../types/chat.ts";
import { prepareTouchUserStatement } from "./userRepository.ts";

const CHAT_COLUMNS =
	"id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt";
const MESSAGE_COLUMNS =
	"id, chatId, role, content, reasoning, parts, createdAt, status, attachments";
const FILE_COLUMNS = "id, messageId, chatId, userId, name, mimeType, size, r2Key, createdAt";

const UPSERT_CHAT_SQL = `INSERT INTO chats (${CHAT_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET modelId = excluded.modelId, lastMessageText = excluded.lastMessageText, lastMessageAt = excluded.lastMessageAt, updatedAt = excluded.updatedAt WHERE chats.userId = excluded.userId RETURNING ${CHAT_COLUMNS}`;
const UPSERT_MESSAGE_SQL = `INSERT INTO messages (${MESSAGE_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(chatId, id) DO UPDATE SET content = excluded.content, reasoning = excluded.reasoning, parts = excluded.parts, status = excluded.status, attachments = CASE WHEN excluded.attachments = '[]' THEN messages.attachments ELSE excluded.attachments END`;

export async function getChatById(
	db: D1Database,
	userId: string,
	chatId: string,
): Promise<ChatRow | null> {
	return db
		.prepare(`SELECT ${CHAT_COLUMNS} FROM chats WHERE id = ? AND userId = ? LIMIT 1`)
		.bind(chatId, userId)
		.first<ChatRow>();
}

export async function listRecentChatMessages(
	db: D1Database,
	chatId: string,
	limit: number,
): Promise<MessageRow[]> {
	const boundedLimit = Math.max(1, limit);
	const result = await db
		.prepare(
			`SELECT ${MESSAGE_COLUMNS} FROM messages
       WHERE chatId = ?
       ORDER BY createdAt DESC, rowid DESC
       LIMIT ?`,
		)
		.bind(chatId, boundedLimit)
		.all<MessageRow>();
	return result.results.reverse();
}

export async function getChatFilesMetadata(
	db: D1Database,
	userId: string,
	chatId: string,
): Promise<FileRow[]> {
	const result = await db
		.prepare(
			`SELECT ${FILE_COLUMNS} FROM message_files WHERE chatId = ? AND userId = ? ORDER BY createdAt DESC`,
		)
		.bind(chatId, userId)
		.all<FileRow>();

	return result.results;
}

export async function listChats(
	db: D1Database,
	userId: string,
	limit: number,
	cursor: ChatListCursor | null,
): Promise<{ chats: ChatRow[]; nextCursor: string | null }> {
	const boundedLimit = Math.max(1, limit);
	const queryLimit = boundedLimit + 1;
	const statement = cursor
		? db
				.prepare(
					`SELECT ${CHAT_COLUMNS} FROM chats
           WHERE userId = ? AND (updatedAt < ? OR (updatedAt = ? AND id < ?))
           ORDER BY updatedAt DESC, id DESC
           LIMIT ?`,
				)
				.bind(userId, cursor.updatedAt, cursor.updatedAt, cursor.id, queryLimit)
		: db
				.prepare(
					`SELECT ${CHAT_COLUMNS} FROM chats
           WHERE userId = ?
           ORDER BY updatedAt DESC, id DESC
           LIMIT ?`,
				)
				.bind(userId, queryLimit);

	const result = await statement.all<ChatRow>();
	const rows = result.results;
	const hasMore = rows.length > boundedLimit;
	const chats = hasMore ? rows.slice(0, boundedLimit) : rows;
	const lastChat = hasMore ? chats[chats.length - 1] : undefined;
	const nextCursor = lastChat ? `${lastChat.updatedAt}:${lastChat.id}` : null;

	return { chats, nextCursor };
}

export async function getFileById(
	db: D1Database,
	userId: string,
	fileId: string,
): Promise<FileRow | null> {
	return db
		.prepare(`SELECT ${FILE_COLUMNS} FROM message_files WHERE id = ? AND userId = ? LIMIT 1`)
		.bind(fileId, userId)
		.first<FileRow>();
}

export async function getChatFileById(
	db: D1Database,
	userId: string,
	chatId: string,
	fileId: string,
): Promise<FileRow | null> {
	return db
		.prepare(
			`SELECT ${FILE_COLUMNS} FROM message_files WHERE id = ? AND userId = ? AND chatId = ? LIMIT 1`,
		)
		.bind(fileId, userId, chatId)
		.first<FileRow>();
}

export async function saveChatTurn(
	db: D1Database,
	input: {
		user: AuthUser;
		chatId: string;
		title: string;
		modelId: string;
		messages: readonly MessageRow[];
		files: readonly FileRow[];
		preview: string;
		at: number;
	},
): Promise<ChatRow | null> {
	const statements: D1PreparedStatement[] = [
		prepareTouchUserStatement(db, input.user, input.at),
		db
			.prepare(UPSERT_CHAT_SQL)
			.bind(
				input.chatId,
				input.user.uid,
				input.title,
				input.modelId,
				input.at,
				input.at,
				input.preview,
				input.at,
			),
	];

	for (const message of input.messages) {
		statements.push(
			db
				.prepare(UPSERT_MESSAGE_SQL)
				.bind(
					message.id,
					message.chatId,
					message.role,
					message.content,
					message.reasoning,
					message.parts,
					message.createdAt,
					message.status,
					message.attachments,
				),
		);
	}

	for (const file of input.files) {
		statements.push(
			db
				.prepare(`INSERT INTO message_files (${FILE_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`)
				.bind(
					file.id,
					file.messageId,
					file.chatId,
					file.userId,
					file.name,
					file.mimeType,
					file.size,
					file.r2Key,
					file.createdAt,
				),
		);
	}

	const results = await db.batch(statements);
	const chatRows = results[1]?.results as ChatRow[] | undefined;
	return chatRows?.[0] ?? null;
}
