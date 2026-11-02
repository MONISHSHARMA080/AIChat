package com.agentic.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
	private val modelsRepository: ModelsRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
	val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
	private var loadJob: Job? = null

	init {
		loadModels()
	}

	fun loadModels() {
		log("loadign the models")
		loadJob?.cancel()
		_uiState.value = ChatUiState.Loading
		loadJob = viewModelScope.launch {
			modelsRepository.getModels()
				.onSuccess { models ->
					log("got the result and it is $models")
					_uiState.value = if (models.isEmpty()) {
						ChatUiState.Empty
					} else {
						ChatUiState.Success(models)
					}
				}
				.onFailure { res->
					log("got the error and it is $res")
					_uiState.value = ChatUiState.Error(
						"Could not load models. Please try again.",
					)
				}
		}
	}

	fun retry() {
		loadModels()
	}
}
