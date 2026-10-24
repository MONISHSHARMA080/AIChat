package com.agentic.aichat.data.chat.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey


/**
 * Row in the `chats` table — one conversation.
 *
 * [lastMessageText] / [lastMessageAt] are denormalized from the latest message
 * (updated in the same transaction as the message write) so the side panel
 * reads only this table.
 */
@Entity(tableName = "chats")
data class ChatEntity(
		@PrimaryKey val id: String,
		val title: String,
		val modelId: String,
		val createdAt: Long,
		val updatedAt: Long,
		val lastMessageText: String?,
		val lastMessageAt: Long?,
)
