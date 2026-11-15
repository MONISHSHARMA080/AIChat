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
		/** Model picked before the first message of a new chat; once the chat exists, `chat.modelId` wins. */
		val newChatModelId: String? = null,
		val pendingTurn: PendingTurn? = null,
		val errorMessage: String? = null,
		val isPaywallVisible: Boolean = false,
		val isSignInVisible: Boolean = false,
) {
	val isSending: Boolean get() = pendingTurn?.isFailed == false
}

/**
 * A message that has been sent but is not in the database yet. It shows the user's text and the
 * reply streaming in. It is replaced by the persisted rows on success, and stays on screen with
 * [isFailed] set so the user can retry it.
 */
data class PendingTurn(
		val request: SendRequest,
		val replyText: String = "",
		val isFailed: Boolean = false,
)

data class SendRequest(
		val messageId: String,
		val text: String,
		val modelId: String,
		val reasoning: String? = null,
		val attachments: List<ChatAttachment> = emptyList(),
)
