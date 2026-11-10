package com.agentic.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.data.chat.ChatRepository
import com.agentic.aichat.data.chat.Outcome
import com.agentic.aichat.data.chat.ChatError
import com.agentic.aichat.data.chat.ChatStreamException
import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.util.log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
	private val chatRepository: ChatRepository,
	private val modelsRepository: ModelsRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
	val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
	private val activeChatId = MutableStateFlow<String?>(null)
	private var modelsJob: Job? = null
	private var sendJob: Job? = null

	init {
		viewModelScope.launch {
			modelsRepository.observeModels().collect { models ->
				_uiState.update { it.copy(models = models) }
			}
		}
		viewModelScope.launch {
			activeChatId.filterNotNull().flatMapLatest { chatRepository.observeChat(it) }.collect { chat ->
				_uiState.update { it.copy(chat = chat) }
			}
		}
		viewModelScope.launch {
			activeChatId.filterNotNull().flatMapLatest { chatRepository.observeMessages(it) }.collect { messages ->
				_uiState.update { it.copy(messages = messages) }
			}
		}
		refreshModels()
	}

	fun openChat(chatId: String?) {
		activeChatId.value = chatId
	}

	fun send(
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment> = emptyList(),
	) {
		val content = text.trim()
		if ((content.isEmpty() && attachments.isEmpty()) || _uiState.value.isSending) return
		runStream(next = {
			val chatId = activeChatId.value ?: createChat(content.ifBlank {
				attachments.firstOrNull()?.name.orEmpty()
			}, modelId)
			chatRepository.sendMessage(chatId, messageId, content, modelId, reasoning?.ifBlank { null }, attachments)
		})
	}

	fun send(request: SendRequest) {
		send(request.messageId, request.text, request.modelId, request.reasoning)
	}

	fun retry(messageId: String, modelId: String, reasoning: String?) {
		val chatId = activeChatId.value ?: return
		if (_uiState.value.isSending) return
		runStream(next = {
			chatRepository.retryMessage(chatId, messageId, modelId, reasoning?.ifBlank { null })
		})
	}

	fun retry(request: RetryRequest) {
		retry(request.messageId, request.modelId, request.reasoning)
	}

	fun selectModel(modelId: String) {
		val chatId = activeChatId.value ?: return
		viewModelScope.launch {
			try {
				chatRepository.updateChatModel(chatId, modelId)
			} catch (error: CancellationException) {
				throw error
			} catch (error: Exception) {
				log("select model failed: $error")
				_uiState.update { it.copy(chatErrorMessage = chatErrorMessage(error)) }
			}
		}
	}

	fun clearError() {
		_uiState.update { it.copy(chatErrorMessage = null) }
	}

	fun clearModelsError() {
		_uiState.update { it.copy(modelsErrorMessage = null) }
	}

	fun refreshModels() {
		modelsJob?.cancel()
		_uiState.update { it.copy(isLoading = true, modelsErrorMessage = null) }
		modelsJob = viewModelScope.launch {
			modelsRepository.refreshModels()
				.onSuccess {
					_uiState.update { it.copy(isLoading = false) }
				}
				.onFailure { error ->
					_uiState.update { it.copy(isLoading = false, modelsErrorMessage = "Could not load models. Please try again.") }
				}
		}
	}

	private suspend fun createChat(content: String, modelId: String): String {
		val title = content.lines().firstOrNull().orEmpty().take(48).ifBlank { "New chat" }
		val chat = chatRepository.createChat(title, modelId)
		activeChatId.value = chat.id
		return chat.id
	}

	private fun runStream(next: suspend () -> Outcome<Flow<String>, ChatError>) {
		sendJob?.cancel()
		_uiState.update { it.copy(isSending = true, streamingText = "", chatErrorMessage = null) }
		sendJob = viewModelScope.launch {
			try {
				when (val result = next()) {
					is Outcome.Ok -> {
						result.value.collect { token ->
							_uiState.update { it.copy(streamingText = it.streamingText + token) }
						}
						stopStreaming()
					}
					is Outcome.Err -> stopStreaming(chatErrorMessage(result.error))
				}
			} catch (error: CancellationException) {
				stopStreaming()
				throw error
			} catch (error: Exception) {
				log("chat stream failed: $error")
				stopStreaming(chatErrorMessage(error))
			}
		}
	}

	private fun stopStreaming(message: String? = null) {
		_uiState.update { it.copy(isSending = false, streamingText = "", chatErrorMessage = message) }
	}

	private fun chatErrorMessage(error: ChatError): String = when (error) {
		ChatError.Unauthorized -> "Please sign in again to keep chatting."
		ChatError.OutOfCredits -> "You're out of credits."
		is ChatError.AttachmentUnreadable -> "Couldn't read ${error.name}."
		ChatError.ServiceUnavailable -> "Couldn't reach the chat service. Please try again."
		is ChatError.Rejected -> error.detail ?: "That request was rejected."
	}

	private fun chatErrorMessage(error: Exception): String {
		(error as? ChatStreamException)?.let { return chatErrorMessage(it.error) }
		return "Couldn't send that message. Please try again."
	}
}
