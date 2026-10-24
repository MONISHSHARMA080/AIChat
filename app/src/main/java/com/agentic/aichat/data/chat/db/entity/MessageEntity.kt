package com.agentic.aichat.data.chat.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Which side of the conversation a [MessageEntity] belongs to. */
enum class MessageRole {
	USER,
	ASSISTANT,
	SYSTEM,
}

/** Lifecycle of a [MessageEntity], used to drive the streaming UI. */
enum class MessageStatus {
	SENDING,
	STREAMING,
	DONE,
	ERROR,
}

/**
 * Row in the `messages` table — one bubble in a chat.
 *
 * Deleting a chat cascades to its messages via the foreign key. The composite
 * `(chatId, createdAt)` index backs the messages-per-chat read, which is the
 * hot query for the chat screen.
 */
@Entity(
	tableName = "messages",
	foreignKeys = [
		ForeignKey(
			entity = ChatEntity::class,
			parentColumns = ["id"],
			childColumns = ["chatId"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index(value = ["chatId", "createdAt"])],
)
data class MessageEntity(
	@PrimaryKey val id: String,
	val chatId: String,
	val role: MessageRole,
	val content: String,
	val createdAt: Long,
	val status: MessageStatus,
)
