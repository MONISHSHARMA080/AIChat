package com.agentic.aichat.data.chat

sealed interface ChatError {
	data object Unauthorized : ChatError
	data object OutOfCredits : ChatError
	data class AttachmentUnreadable(val name: String) : ChatError
	data object ServiceUnavailable : ChatError
	data class Rejected(val detail: String?) : ChatError
	data object NotSaved : ChatError
}

internal class ChatException(val error: ChatError) : Exception(error.toString())
