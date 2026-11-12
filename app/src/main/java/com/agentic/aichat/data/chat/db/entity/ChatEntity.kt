package com.agentic.aichat.data.chat.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
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
