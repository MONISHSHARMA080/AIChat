package com.agentic.aichat.data.chat.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class MessageRole {
	USER,
	ASSISTANT,
	SYSTEM,
}

enum class MessageStatus {
	SENDING,
	STREAMING,
	DONE,
	ERROR,
}

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
	@ColumnInfo(defaultValue = "'[]'")
	val attachments: List<ChatAttachment> = emptyList(),
)
