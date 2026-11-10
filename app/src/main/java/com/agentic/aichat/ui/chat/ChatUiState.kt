package com.agentic.aichat.ui.chat

import com.agentic.aichat.data.ai.ModelDto
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity

data class SendRequest(
	val messageId: String,
	val text: String,
	val modelId: String,
	val reasoning: String?,
)

data class RetryRequest(
	val messageId: String,
	val modelId: String,
	val reasoning: String?,
)

data class ChatUiState(
	val chat: ChatEntity? = null,
	val messages: List<MessageEntity> = emptyList(),
	val streamingText: String = "",
	val models: List<ModelDto> = emptyList(),
	val isLoading: Boolean = false,
	val isSending: Boolean = false,
	val chatErrorMessage: String? = null,
	val modelsErrorMessage: String? = null,
)
