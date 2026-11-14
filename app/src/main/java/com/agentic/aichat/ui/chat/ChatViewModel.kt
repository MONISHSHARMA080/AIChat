package com.agentic.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.data.chat.ChatRepository
import com.agentic.aichat.data.chat.Outcome
import com.agentic.aichat.data.chat.ChatError
import com.agentic.aichat.data.chat.ChatStreamException
import com.agentic.aichat.util.log
import com.google.firebase.auth.FirebaseAuth
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
	private val firebaseAuth: FirebaseAuth,
) : ViewModel() {
	private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
	val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
	private val activeChatId = MutableStateFlow<String?>(null)
	private var modelsJob: Job? = null
	private val sendJobs = mutableMapOf<String, Job>()

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
		viewModelScope.launch {
			chatRepository.observeSyncedChatId().collect { syncedId ->
				if (syncedId != null && syncedId != activeChatId.value) {
					activeChatId.value = syncedId
				}
			}
		}
		refreshModels()
	}

	fun openChat(chatId: String?) {
		activeChatId.value = chatId
		_uiState.update { current ->
			val retained = current.pendingMessages.filter { it.chatId == chatId }
			val retainedIds = retained.map { it.id }.toSet()
			val retainedStreaming = current.streamingByMessageId.filterKeys { it in retainedIds }
			current.copy(
				chat = if (chatId == null) null else current.chat,
				messages = if (chatId == null) emptyList() else current.messages,
				pendingMessages = retained,
				streamingByMessageId = retainedStreaming,
				pendingModelId = if (chatId == null) null else current.pendingModelId,
				isSending = sendJobs.isNotEmpty(),
				chatErrorMessage = if (chatId == null) null else current.chatErrorMessage,
			)
		}
	}

	fun send(request: SendRequest) {
		val content = request.text.trim()
		if (content.isEmpty()) return
		if (firebaseAuth.currentUser == null) {
			_uiState.update { it.copy(showSignIn = true) }
			return
		}
		val chatId = activeChatId.value
		if (chatId == null) {
			_uiState.update { it.copy(pendingModelId = null) }
		}
		val pending = PendingMessage(id = request.messageId, text = content, chatId = chatId)
		_uiState.update {
			it.copy(pendingMessages = it.pendingMessages + pending, isSending = true, chatErrorMessage = null)
		}
		launchStream(request.messageId, cleanup = ::removePending) {
			chatRepository.sendMessage(chatId, request.messageId, content, request.modelId, request.reasoning?.ifBlank { null })
		}
	}

	fun retry(request: RetryRequest) {
		val chatId = activeChatId.value ?: return
		if (firebaseAuth.currentUser == null) {
			_uiState.update { it.copy(showSignIn = true) }
			return
		}
		if (sendJobs.containsKey(request.messageId)) return
		_uiState.update { it.copy(isSending = true, chatErrorMessage = null) }
		launchStream(request.messageId, cleanup = ::removeStreaming) {
			chatRepository.retryMessage(chatId, request.messageId, request.modelId, request.reasoning?.ifBlank { null })
		}
	}

	private fun launchStream(
		messageId: String,
		cleanup: (String) -> Unit,
		next: suspend () -> Outcome<Flow<String>, ChatError>,
	) {
		val job = viewModelScope.launch {
			try {
				when (val result = next()) {
					is Outcome.Ok -> {
						result.value.collect { token -> appendStreaming(messageId, token) }
						cleanup(messageId)
					}
					is Outcome.Err -> {
						cleanup(messageId)
						handleChatError(result.error)
					}
				}
			} catch (error: CancellationException) {
				cleanup(messageId)
				throw error
			} catch (error: Exception) {
				cleanup(messageId)
				log("chat stream failed: $error")
				val chatError = (error as? ChatStreamException)?.error
				if (chatError != null) handleChatError(chatError) else _uiState.update {
					it.copy(chatErrorMessage = chatErrorMessage(error))
				}
			} finally {
				sendJobs.remove(messageId)
				_uiState.update { it.copy(isSending = sendJobs.isNotEmpty()) }
			}
		}
		sendJobs[messageId] = job
	}

	private fun appendStreaming(messageId: String, token: String) {
		_uiState.update { current ->
			val previous = current.streamingByMessageId[messageId].orEmpty()
			current.copy(streamingByMessageId = current.streamingByMessageId + (messageId to previous + token))
		}
	}

	fun selectModel(modelId: String) {
		val chatId = activeChatId.value
		if (chatId == null) {
			_uiState.update { it.copy(pendingModelId = modelId) }
			return
		}
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

	fun consumePaywall() {
		_uiState.update { it.copy(showPaywall = false) }
	}

	fun consumeSignIn() {
		_uiState.update { it.copy(showSignIn = false) }
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

	private fun removePending(messageId: String) {
		_uiState.update { current ->
			current.copy(
				pendingMessages = current.pendingMessages.filterNot { it.id == messageId },
				streamingByMessageId = current.streamingByMessageId.filterKeys { it != messageId },
			)
		}
	}

	private fun removeStreaming(messageId: String) {
		_uiState.update { current ->
			current.copy(streamingByMessageId = current.streamingByMessageId.filterKeys { it != messageId })
		}
	}

	private fun handleChatError(error: ChatError) {
		when (error) {
			ChatError.OutOfCredits -> _uiState.update {
				it.copy(chatErrorMessage = null, showPaywall = true)
			}
			ChatError.Unauthorized -> _uiState.update {
				it.copy(chatErrorMessage = null, showSignIn = true)
			}
			else -> _uiState.update { it.copy(chatErrorMessage = chatErrorMessage(error)) }
		}
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
