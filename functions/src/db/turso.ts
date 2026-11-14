import { connect as connectRemote } from "@tursodatabase/serverless";
import { connect as connectLocal } from "@tursodatabase/database";
import { TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";

type Statement = { sql: string; args?: unknown[] };

type DatabaseHandle = {
    exec(sql: string): Promise<unknown>;
    run(sql: string, ...params: unknown[]): Promise<unknown>;
    get(sql: string, ...params: unknown[]): Promise<unknown>;
    all<T>(sql: string, ...params: unknown[]): Promise<T[]>;
    batch(statements: Statement[], mode?: string): Promise<{ rowsAffected?: number; rows?: unknown[] }[]>;
};

let databasePromise: Promise<DatabaseHandle> | null = null;

const schemaSql = `
  CREATE TABLE IF NOT EXISTS users (
    uid TEXT PRIMARY KEY,
    email TEXT,
    display_name TEXT,
    photo_url TEXT,
    phone_number TEXT,
    provider TEXT NOT NULL,
    email_verified INTEGER,
    is_anonymous INTEGER,
    tenant_id TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS chats (
    id TEXT PRIMARY KEY,
    userId TEXT NOT NULL,
    title TEXT NOT NULL,
    modelId TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    lastMessageText TEXT,
    lastMessageAt INTEGER,
    FOREIGN KEY (userId) REFERENCES users(uid) ON DELETE CASCADE
  );
  CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    chatId TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    status TEXT NOT NULL,
    attachments TEXT NOT NULL DEFAULT '[]'
  );
  CREATE INDEX IF NOT EXISTS chats_user_id_updated_at ON chats (userId, updatedAt);
  CREATE INDEX IF NOT EXISTS messages_chat_id_created_at ON messages (chatId, createdAt);
`;

function connectToTursoCloud(): DatabaseHandle {
    const secretUrl = process.env.TURSO_DATABASE_URL ?? TURSO_DATABASE_URL.value();
    const secretToken = process.env.TURSO_AUTH_TOKEN ?? TURSO_AUTH_TOKEN.value();
    if (!secretUrl) throw new Error("TURSO_DATABASE_URL is not set");
    if (!secretToken) throw new Error("TURSO_AUTH_TOKEN is not set");
    return connectRemote({ url: secretUrl, authToken: secretToken });
}

function connectForEmulator(): Promise<DatabaseHandle> {
    const emulatorPath = process.env.TURSO_EMULATOR_DATABASE_PATH;
    if (emulatorPath) return connectLocal(emulatorPath);
    return Promise.resolve(connectToTursoCloud());
}

function getDatabase(): Promise<DatabaseHandle> {
    if (!databasePromise) {
        databasePromise = connectForEmulator()
            .then(async (database) => {
                await database.exec(schemaSql);
                return database;
            })
            .catch((error) => {
                databasePromise = null;
                throw error;
            });
    }
    return databasePromise;
}

export type ChatRow = {
    id: string;
    userId: string;
    title: string;
    modelId: string;
    createdAt: number;
    updatedAt: number;
    lastMessageText: string | null;
    lastMessageAt: number | null;
};

export type MessageRow = {
    id: string;
    chatId: string;
    role: string;
    content: string;
    createdAt: number;
    status: string;
    attachments: string;
};

export type UserRow = {
    uid: string;
    email: string | null;
    display_name: string | null;
    photo_url: string | null;
    phone_number: string | null;
    provider: string;
    email_verified: number | null;
    is_anonymous: number | null;
    tenant_id: string | null;
    created_at: number;
    updated_at: number;
    last_seen_at: number;
};

export type UpsertUserInput = {
    uid: string;
    email: string | null;
    displayName: string | null;
    photoUrl: string | null;
    phoneNumber: string | null;
    provider: string;
    emailVerified: boolean;
    isAnonymous: boolean;
    tenantId: string | null;
    createdAt: number;
    updatedAt: number;
    lastSeenAt: number;
};

const CHAT_COLUMNS = "id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt";
const MESSAGE_COLUMNS = "id, chatId, role, content, createdAt, status, attachments";
const USER_COLUMNS =
    "uid, email, display_name, photo_url, phone_number, provider, email_verified, is_anonymous, tenant_id, created_at, updated_at, last_seen_at";

const marks = (count: number) => Array(count).fill("?").join(", ");

const TOUCH_USER_SQL =
    "INSERT INTO users (uid, provider, created_at, updated_at, last_seen_at) VALUES (?, 'firebase', ?, ?, ?) ON CONFLICT(uid) DO UPDATE SET last_seen_at = excluded.last_seen_at";

export async function listChatRows(userId: string): Promise<ChatRow[]> {
    const database = await getDatabase();
    return database.all<ChatRow>(`SELECT ${CHAT_COLUMNS} FROM chats WHERE userId = ? ORDER BY updatedAt DESC`, userId);
}

export async function getChatRow(userId: string, chatId: string): Promise<ChatRow | null> {
    const database = await getDatabase();
    const row = (await database.get(`SELECT ${CHAT_COLUMNS} FROM chats WHERE id = ? AND userId = ?`, chatId, userId)) as
        | ChatRow
        | undefined;
    return row ?? null;
}

type ChatMessageJoinRow = ChatRow & {
    messageId: string | null;
    messageRole: string | null;
    messageContent: string | null;
    messageCreatedAt: number | null;
    messageStatus: string | null;
    messageAttachments: string | null;
};

export async function getChatWithMessages(
    userId: string,
    chatId: string,
): Promise<{ chat: ChatRow; messages: MessageRow[] } | null> {
    const database = await getDatabase();
    const rows = await database.all<ChatMessageJoinRow>(
        `SELECT c.id, c.userId, c.title, c.modelId, c.createdAt, c.updatedAt, c.lastMessageText, c.lastMessageAt,
            m.id AS messageId, m.role AS messageRole, m.content AS messageContent,
            m.createdAt AS messageCreatedAt, m.status AS messageStatus, m.attachments AS messageAttachments
     FROM chats c
     LEFT JOIN messages m ON m.chatId = c.id
     WHERE c.id = ? AND c.userId = ?
     ORDER BY m.createdAt ASC`,
        chatId,
        userId,
    );
    if (rows.length === 0) return null;

    const first = rows[0];
    const chat: ChatRow = {
        id: first.id,
        userId: first.userId,
        title: first.title,
        modelId: first.modelId,
        createdAt: first.createdAt,
        updatedAt: first.updatedAt,
        lastMessageText: first.lastMessageText,
        lastMessageAt: first.lastMessageAt,
    };
    const messages: MessageRow[] = rows
        .filter((row) => row.messageId !== null)
        .map((row) => ({
            id: row.messageId as string,
            chatId: row.id,
            role: row.messageRole as string,
            content: row.messageContent as string,
            createdAt: row.messageCreatedAt as number,
            status: row.messageStatus as string,
            attachments: row.messageAttachments as string,
        }));
    return { chat, messages };
}

export async function deleteChatRow(userId: string, chatId: string): Promise<boolean> {
    const database = await getDatabase();
    const args = [chatId, userId];
    const [, chatResult] = await database.batch(
        [
            { sql: "DELETE FROM messages WHERE chatId IN (SELECT id FROM chats WHERE id = ? AND userId = ?)", args },
            { sql: "DELETE FROM chats WHERE id = ? AND userId = ?", args },
        ],
        "write",
    );
    return (chatResult?.rowsAffected ?? 0) > 0;
}

export async function listMessageRows(chatId: string): Promise<MessageRow[]> {
    const database = await getDatabase();
    return database.all<MessageRow>(
        `SELECT ${MESSAGE_COLUMNS} FROM messages WHERE chatId = ? ORDER BY createdAt ASC`,
        chatId,
    );
}

export type SaveChatTurnInput = {
    userId: string;
    chatId: string;
    newChat: { title: string; modelId: string } | null;
    messages: MessageRow[];
    preview: string;
    at: number;
};

export async function saveChatTurn({ userId, chatId, newChat, messages, preview, at }: SaveChatTurnInput): Promise<void> {
    const database = await getDatabase();
    await database.batch(
        [
            { sql: TOUCH_USER_SQL, args: [userId, at, at, at] },
            newChat
                ? {
                    sql: `INSERT INTO chats (${CHAT_COLUMNS}) VALUES (${marks(8)})`,
                    args: [chatId, userId, newChat.title, newChat.modelId, at, at, preview, at],
                }
                : {
                    sql: "UPDATE chats SET lastMessageText = ?, lastMessageAt = ?, updatedAt = ? WHERE id = ? AND userId = ?",
                    args: [preview, at, at, chatId, userId],
                },
            {
                sql: `INSERT INTO messages (${MESSAGE_COLUMNS}) VALUES ${messages.map(() => `(${marks(7)})`).join(", ")}`,
                args: messages.flatMap((m) => [m.id, m.chatId, m.role, m.content, m.createdAt, m.status, m.attachments]),
            },
        ],
        "write",
    );
}

export async function ensureUserRow(uid: string, nowMs: number): Promise<void> {
    const database = await getDatabase();
    await database.run(TOUCH_USER_SQL, uid, nowMs, nowMs, nowMs);
}

export async function upsertUser(input: UpsertUserInput): Promise<UserRow> {
    const database = await getDatabase();
    await database.run(
        `INSERT INTO users (${USER_COLUMNS}) VALUES (${marks(12)}) ON CONFLICT(uid) DO UPDATE SET email = excluded.email, display_name = excluded.display_name, photo_url = excluded.photo_url, phone_number = excluded.phone_number, provider = excluded.provider, email_verified = excluded.email_verified, updated_at = excluded.updated_at, last_seen_at = excluded.last_seen_at`,
        input.uid,
        input.email,
        input.displayName,
        input.photoUrl,
        input.phoneNumber,
        input.provider,
        input.emailVerified ? 1 : 0,
        input.isAnonymous ? 1 : 0,
        input.tenantId,
        input.createdAt,
        input.updatedAt,
        input.lastSeenAt,
    );
    const row = (await database.get(`SELECT ${USER_COLUMNS} FROM users WHERE uid = ?`, input.uid)) as UserRow | undefined;
    if (!row) throw new Error("upsertUser did not return a row");
    return row;
}
