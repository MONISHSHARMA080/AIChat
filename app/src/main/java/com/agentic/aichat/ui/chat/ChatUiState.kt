package com.agentic.aichat.ui.chat

import com.agentic.aichat.model.AiChatModel

sealed interface ChatUiState {
	data object Loading : ChatUiState
	data object Empty : ChatUiState
	data class Success(val models: List<AiChatModel>) : ChatUiState
	data class Error(val message: String) : ChatUiState
}
