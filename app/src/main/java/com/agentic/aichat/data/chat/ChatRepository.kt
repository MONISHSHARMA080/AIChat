package com.agentic.aichat.data.chat

import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

	fun observeChats(): Flow<List<ChatEntity>>

	fun observeChat(chatId: String): Flow<ChatEntity?>

	fun observeMessages(chatId: String): Flow<List<MessageEntity>>

	suspend fun updateChatModel(chatId: String, modelId: String)

	fun sendMessage(
		chatId: String?,
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment> = emptyList(),
	): Flow<SendMessageEvent>
}

sealed interface SendMessageEvent {
	data class ReplyChunk(val text: String) : SendMessageEvent

	data class Saved(val chatId: String) : SendMessageEvent
}

class ChatException(val error: ChatError) : Exception(error.toString())
