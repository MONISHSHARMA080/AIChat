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

CREATE TABLE IF NOT EXISTS message_files (
  id TEXT PRIMARY KEY,
  messageId TEXT,
  chatId TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
  userId TEXT NOT NULL REFERENCES users(uid) ON DELETE CASCADE,
  name TEXT NOT NULL,
  mimeType TEXT NOT NULL,
  size INTEGER NOT NULL,
  r2Key TEXT NOT NULL,
  createdAt INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id TEXT NOT NULL,
    chatId TEXT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    reasoning TEXT,
    parts TEXT NOT NULL DEFAULT '[]',
    createdAt INTEGER NOT NULL,
    status TEXT NOT NULL,
    attachments TEXT NOT NULL DEFAULT '[]',
    PRIMARY KEY (chatId, id)
    );


CREATE INDEX IF NOT EXISTS chats_user_id_updated_at_id ON chats (userId, updatedAt DESC, id DESC);
CREATE INDEX IF NOT EXISTS message_files_chat_id ON message_files (chatId);
CREATE INDEX IF NOT EXISTS message_files_user_id ON message_files (userId);
CREATE INDEX IF NOT EXISTS messages_chat_id_created_at ON messages (chatId, createdAt);
CREATE INDEX IF NOT EXISTS message_files_message_id ON message_files (messageId);
