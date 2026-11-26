import type {
	ChatListCursor,
	ChatListPage,
	ChatRow,
	FileRow,
	SaveChatSummaryInput,
} from "../types/database.ts";

const CHAT_COLUMNS =
	"id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt";
const FILE_COLUMNS = "id, messageId, chatId, userId, name, mimeType, size, r2Key, createdAt";

const UPSERT_USER_SQL =
	"INSERT INTO users (uid, email, display_name, photo_url, phone_number, provider, email_verified, is_anonymous, tenant_id, created_at, updated_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uid) DO UPDATE SET email = COALESCE(excluded.email, users.email), display_name = COALESCE(excluded.display_name, users.display_name), photo_url = COALESCE(excluded.photo_url, users.photo_url), phone_number = COALESCE(excluded.phone_number, users.phone_number), provider = excluded.provider, email_verified = excluded.email_verified, is_anonymous = excluded.is_anonymous, tenant_id = COALESCE(excluded.tenant_id, users.tenant_id), updated_at = excluded.updated_at, last_seen_at = excluded.last_seen_at";

const UPSERT_CHAT_SQL = `INSERT INTO chats (${CHAT_COLUMNS}) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET modelId = excluded.modelId, lastMessageText = excluded.lastMessageText, lastMessageAt = excluded.lastMessageAt, updatedAt = excluded.updatedAt WHERE chats.userId = excluded.userId`;

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
): Promise<ChatListPage> {
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

export async function saveChatSummary(db: D1Database, input: SaveChatSummaryInput): Promise<void> {
	const statements: D1PreparedStatement[] = [
		db
			.prepare(UPSERT_USER_SQL)
			.bind(
				input.user.uid,
				input.user.email,
				input.user.displayName,
				input.user.photoUrl,
				input.user.phoneNumber,
				input.user.provider,
				input.user.emailVerified ? 1 : 0,
				input.user.isAnonymous ? 1 : 0,
				input.user.tenantId,
				input.at,
				input.at,
				input.at,
			),
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

	await db.batch(statements);
}
