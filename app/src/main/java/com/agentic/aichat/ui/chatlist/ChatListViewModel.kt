package com.agentic.aichat.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.chat.ChatRepository
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatListViewModel @Inject constructor(
	private val chatRepository: ChatRepository,
) : ViewModel() {
	private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
	val chats: StateFlow<List<ChatEntity>> = _chats.asStateFlow()

	init {
		viewModelScope.launch {
			chatRepository.getAllChats().collect { list ->
				_chats.value = list
			}
		}
	}
}
