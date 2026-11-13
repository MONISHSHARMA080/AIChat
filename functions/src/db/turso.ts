import { connect as connectRemote } from "@tursodatabase/serverless";
import { connect as connectLocal } from "@tursodatabase/database";
import { TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";

type DatabaseHandle = {
  exec(sql: string): Promise<unknown>;
  run(sql: string, ...params: unknown[]): Promise<{ changes?: number; rowsAffected?: number }>;
  get(sql: string, ...params: unknown[]): Promise<unknown>;
  all<T>(sql: string, ...params: unknown[]): Promise<T[]>;
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
  lastMessageAt INTEGER
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
  if (emulatorPath) {
    return connectLocal(emulatorPath).then((handle) => handle as unknown as DatabaseHandle);
  }
  return Promise.resolve(connectToTursoCloud());
}

function getDatabase(): Promise<DatabaseHandle> {
  if (!databasePromise) {
    databasePromise = connectForEmulator().then(async (database) => {
      await database.exec(schemaSql);
      return database;
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

export async function insertChatRow(chat: ChatRow): Promise<ChatRow> {
  const database = await getDatabase();
  await database.run(
    "INSERT INTO chats (id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    chat.id,
    chat.userId,
    chat.title,
    chat.modelId,
    chat.createdAt,
    chat.updatedAt,
    chat.lastMessageText,
    chat.lastMessageAt,
  );
  return chat;
}

export async function listChatRows(userId: string): Promise<ChatRow[]> {
  const database = await getDatabase();
  return database.all<ChatRow>(
    "SELECT id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt FROM chats WHERE userId = ? ORDER BY updatedAt DESC",
    userId,
  );
}

export async function getChatRow(userId: string, chatId: string): Promise<ChatRow | null> {
  const database = await getDatabase();
  const row = (await database.get(
    "SELECT id, userId, title, modelId, createdAt, updatedAt, lastMessageText, lastMessageAt FROM chats WHERE id = ? AND userId = ?",
    chatId,
    userId,
  )) as ChatRow | undefined;
  return row ?? null;
}

export async function deleteChatRow(userId: string, chatId: string): Promise<boolean> {
  const database = await getDatabase();
  const result = await database.run("DELETE FROM chats WHERE id = ? AND userId = ?", chatId, userId);
  return (result.rowsAffected ?? result.changes ?? 0) > 0;
}

export async function insertMessageRow(message: MessageRow): Promise<MessageRow> {
  const database = await getDatabase();
  await database.run(
    "INSERT INTO messages (id, chatId, role, content, createdAt, status, attachments) VALUES (?, ?, ?, ?, ?, ?, ?)",
    message.id,
    message.chatId,
    message.role,
    message.content,
    message.createdAt,
    message.status,
    message.attachments,
  );
  return message;
}

export async function listMessageRows(chatId: string): Promise<MessageRow[]> {
  const database = await getDatabase();
  return database.all<MessageRow>(
    "SELECT id, chatId, role, content, createdAt, status, attachments FROM messages WHERE chatId = ? ORDER BY createdAt ASC",
    chatId,
  );
}

export async function touchChatLastMessage(chatId: string, text: string, at: number): Promise<void> {
  const database = await getDatabase();
  await database.run(
    "UPDATE chats SET lastMessageText = ?, lastMessageAt = ?, updatedAt = ? WHERE id = ?",
    text,
    at,
    at,
    chatId,
  );
}

export async function upsertUser(input: UpsertUserInput): Promise<UserRow> {
  const database = await getDatabase();
  await database.run(
    "INSERT INTO users (uid, email, display_name, photo_url, phone_number, provider, email_verified, is_anonymous, tenant_id, created_at, updated_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uid) DO UPDATE SET email = excluded.email, display_name = excluded.display_name, photo_url = excluded.photo_url, phone_number = excluded.phone_number, provider = excluded.provider, email_verified = excluded.email_verified, updated_at = excluded.updated_at, last_seen_at = excluded.last_seen_at",
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
  const row = (await database.get("SELECT uid, email, display_name, photo_url, phone_number, provider, email_verified, is_anonymous, tenant_id, created_at, updated_at, last_seen_at FROM users WHERE uid = ?", input.uid)) as UserRow | undefined;
  if (!row) throw new Error("upsertUser did not return a row");
  return row;
}
