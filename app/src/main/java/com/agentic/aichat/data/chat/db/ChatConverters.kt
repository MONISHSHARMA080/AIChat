package com.agentic.aichat.data.chat.db

import androidx.room3.ColumnTypeConverter
import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import kotlinx.serialization.json.Json

class ChatConverters {
	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	@ColumnTypeConverter
	fun fromAttachments(value: String): List<ChatAttachment> =
		json.decodeFromString<List<ChatAttachment>>(value)

	@ColumnTypeConverter
	fun toAttachments(attachments: List<ChatAttachment>): String =
		json.encodeToString(attachments)
}
