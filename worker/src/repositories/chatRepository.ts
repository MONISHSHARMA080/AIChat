import type { AuthUser } from "../types/auth.ts";
import type { ChatListCursor, ChatRow, FileRow } from "../types/chat.ts";
import { prepareTouchUserStatement } from "./userRepository.ts";

const CHAT_COLUMNS =
	"id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt";
const FILE_COLUMNS = "id, messageId, chatId, userId, name, mimeType, size, r2Key, createdAt";

const UPSERT_CHAT_SQL = `INSERT INTO chats (${CHAT_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET title = CASE WHEN chats.lastMessageAt IS NULL THEN excluded.title ELSE chats.title END, modelId = excluded.modelId, lastMessageText = excluded.lastMessageText, lastMessageAt = excluded.lastMessageAt, updatedAt = excluded.updatedAt WHERE chats.userId = excluded.userId RETURNING ${CHAT_COLUMNS}`;

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

export async function saveChatSummary(
	db: D1Database,
	input: {
		user: AuthUser;
		chatId: string;
		title: string;
		modelId: string;
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
