package com.agentic.aichat.ui.chat

import com.agentic.aichat.data.ai.ModelDto

data class ChatUiState(
	val models: List<ModelDto> = emptyList(),
	val isLoading: Boolean = false,
	val errorMessage: String? = null,
)
