package com.agentic.aichat.ui.chat

import com.agentic.aichat.data.ai.ModelDto

sealed interface ChatUiState {
	data object Loading : ChatUiState
	data object Empty : ChatUiState
	data class Success(val models: List<ModelDto>) : ChatUiState
	data class Error(val message: String) : ChatUiState
}
