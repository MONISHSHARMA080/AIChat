package com.agentic.aichat.data.chat

import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {

	fun getAllChats(): Flow<List<ChatEntity>>

	fun observeChat(chatId: String): Flow<ChatEntity?>

	fun observeMessages(chatId: String): Flow<List<MessageEntity>>

	suspend fun updateChatModel(chatId: String, modelId: String)

	fun observeSyncedChatId(): StateFlow<String?>

	suspend fun sendMessage(
		chatId: String?,
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
