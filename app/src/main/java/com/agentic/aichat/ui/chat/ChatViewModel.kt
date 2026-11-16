package com.agentic.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.data.chat.ChatError
import com.agentic.aichat.data.chat.ChatException
import com.agentic.aichat.data.chat.ChatRepository
import com.agentic.aichat.data.chat.SendMessageEvent
import com.agentic.aichat.util.log
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
		private val firebaseAuth: FirebaseAuth,
) : ViewModel() {
	private val _uiState = MutableStateFlow(ChatUiState())
	val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

	private val activeChatId = MutableStateFlow<String?>(null)
	private var sendJob: Job? = null
	private var refreshModelsJob: Job? = null

	init {
		viewModelScope.launch {
			modelsRepository.observeModels().collect { models ->
				_uiState.update { it.copy(models = models) }
			}
		}
		viewModelScope.launch {
			activeChatId.filterNotNull()
				.flatMapLatest { chatRepository.observeChat(it) }
				.collect { chat -> _uiState.update { it.copy(chat = chat) } }
		}
		viewModelScope.launch {
			activeChatId.filterNotNull()
				.flatMapLatest { chatRepository.observeMessages(it) }
				.collect { messages ->
					_uiState.update { it.copy(messages = messages).withoutPersistedPendingTurn() }
				}
		}
		refreshModels()
	}

	fun openChat(chatId: String?) {
		if (chatId == activeChatId.value) return
		sendJob?.cancel()
		activeChatId.value = chatId
		_uiState.update {
			it.copy(chat = null, messages = emptyList(), pendingTurn = null, errorMessage = null)
		}
	}

	fun sendMessage(request: SendRequest) {
		if (_uiState.value.isSending) return
		val trimmedRequest = request.copy(text = request.text.trim())
		if (trimmedRequest.text.isEmpty() && trimmedRequest.attachments.isEmpty()) return
		startTurn(trimmedRequest)
	}

	fun retryFailedMessage() {
		val failedTurn = _uiState.value.pendingTurn?.takeIf { it.isFailed } ?: return
		startTurn(failedTurn.request)
	}

	private fun startTurn(request: SendRequest) {
		if (firebaseAuth.currentUser == null) {
			_uiState.update { it.copy(isSignInVisible = true) }
			return
		}
		_uiState.update { it.copy(pendingTurn = PendingTurn(request), errorMessage = null) }
		sendJob = viewModelScope.launch {
			try {
				chatRepository.sendMessage(
					chatId = activeChatId.value,
					text = request.text,
					modelId = request.modelId,
					reasoning = request.reasoning?.ifBlank { null },
					attachments = request.attachments,
				).collect { event ->
					when (event) {
						is SendMessageEvent.ReplyChunk -> _uiState.update { state ->
							state.copy(pendingTurn = state.pendingTurn?.let { it.copy(replyText = it.replyText + event.text) })
						}
						is SendMessageEvent.Saved -> {
							_uiState.update { state ->
								state.copy(pendingTurn = state.pendingTurn?.copy(savedUserMessageId = event.userMessageId))
									.withoutPersistedPendingTurn()
							}
							activeChatId.value = event.chatId
						}
					}
				}
			} catch (error: Exception) {
				if (error is CancellationException) throw error
				log("send failed: $error")
				_uiState.update { state ->
					val failedState = state.copy(pendingTurn = state.pendingTurn?.copy(isFailed = true))
					when (val chatError = (error as? ChatException)?.error) {
						ChatError.OutOfCredits -> failedState.copy(isPaywallVisible = true)
						ChatError.Unauthorized -> failedState.copy(isSignInVisible = true)
						ChatError.ServiceUnavailable ->
							failedState.copy(errorMessage = "Couldn't reach the chat service. Please try again.")
						ChatError.NotSaved ->
							failedState.copy(errorMessage = "Your reply couldn't be saved. Please try again.")
						is ChatError.AttachmentUnreadable ->
							failedState.copy(errorMessage = "Couldn't read ${chatError.name}.")
						is ChatError.Rejected ->
							failedState.copy(errorMessage = chatError.detail ?: "That request was rejected.")
						null -> failedState.copy(errorMessage = "Couldn't send that message. Please try again.")
					}
				}
			}
		}
	}

	fun selectModel(modelId: String) {
		val chatId = activeChatId.value
		if (chatId == null) {
			_uiState.update { it.copy(newChatModelId = modelId) }
			return
		}
		viewModelScope.launch {
			try {
				chatRepository.updateChatModel(chatId, modelId)
			} catch (error: Exception) {
				if (error is CancellationException) throw error
				log("select model failed: $error")
				_uiState.update { it.copy(errorMessage = "Couldn't change the model. Please try again.") }
			}
		}
	}

	fun refreshModels() {
		refreshModelsJob?.cancel()
		_uiState.update { it.copy(isLoadingModels = true, modelsErrorMessage = null) }
		refreshModelsJob = viewModelScope.launch {
			val result = modelsRepository.refreshModels()
			_uiState.update {
				it.copy(
					isLoadingModels = false,
					modelsErrorMessage = if (result.isFailure) "Could not load models. Please try again." else null,
				)
			}
		}
	}

	fun dismissError() {
		_uiState.update { it.copy(errorMessage = null) }
	}

	fun dismissPaywall() {
		_uiState.update { it.copy(isPaywallVisible = false) }
	}

	fun dismissSignIn() {
		_uiState.update { it.copy(isSignInVisible = false) }
	}
}

private fun ChatUiState.withoutPersistedPendingTurn(): ChatUiState {
	val savedUserMessageId = pendingTurn?.savedUserMessageId ?: return this
	return if (messages.any { it.id == savedUserMessageId }) copy(pendingTurn = null) else this
}
