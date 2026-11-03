package com.agentic.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.util.log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
	private val modelsRepository: ModelsRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
	val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
	private var refreshJob: Job? = null

	init {
		viewModelScope.launch {
			modelsRepository.observeModels().collect { models ->
				_uiState.update { it.copy(models = models) }
			}
		}
		refresh()
	}

	fun retry() {
		refresh()
	}

	private fun refresh() {
		refreshJob?.cancel()
		_uiState.update { it.copy(isLoading = true, errorMessage = null) }
		refreshJob = viewModelScope.launch {
			modelsRepository.refreshModels()
				.onSuccess {
					_uiState.update { it.copy(isLoading = false) }
				}
				.onFailure { res ->
					log("got the error and it is $res")
					_uiState.update {
						it.copy(
							isLoading = false,
							errorMessage = "Could not load models. Please try again.",
						)
					}
				}
		}
	}
}
