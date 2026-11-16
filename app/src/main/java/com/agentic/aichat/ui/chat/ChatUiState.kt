package com.agentic.aichat.ui.chat

import com.agentic.aichat.data.ai.ModelDto
import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity

data class ChatUiState(
		val models: List<ModelDto> = emptyList(),
		val isLoadingModels: Boolean = true,
		val modelsErrorMessage: String? = null,
		val chat: ChatEntity? = null,
		val messages: List<MessageEntity> = emptyList(),
		val newChatModelId: String? = null,
		val pendingTurn: PendingTurn? = null,
		val errorMessage: String? = null,
		val isPaywallVisible: Boolean = false,
		val isSignInVisible: Boolean = false,
) {
	val isSending: Boolean get() = pendingTurn?.isFailed == false
}

data class PendingTurn(
		val request: SendRequest,
		val replyText: String = "",
		val isFailed: Boolean = false,
		val savedUserMessageId: String? = null,
)

data class SendRequest(
		val localKey: String,
		val text: String,
		val modelId: String,
		val reasoning: String? = null,
		val attachments: List<ChatAttachment> = emptyList(),
)
