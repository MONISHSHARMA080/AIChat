package com.agentic.aichat.data.chat

import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

	fun observeChat(chatId: String): Flow<ChatEntity?>

	fun observeMessages(chatId: String): Flow<List<MessageEntity>>

	suspend fun getMessages(chatId: String): List<MessageEntity>

	suspend fun createChat(title: String, modelId: String): ChatEntity

	suspend fun renameChat(chatId: String, title: String)

	suspend fun updateChatModel(chatId: String, modelId: String)

	suspend fun deleteChat(chatId: String)

	suspend fun addMessage(message: MessageEntity)

	suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

	suspend fun deleteMessage(messageId: String)

	suspend fun sendMessage(
		chatId: String,
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment> = emptyList(),
	): Outcome<Flow<String>, ChatError>

	suspend fun retryMessage(
		chatId: String,
		messageId: String,
		modelId: String,
		reasoning: String?,
	): Outcome<Flow<String>, ChatError>
}
