package com.agentic.aichat.ui.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Side panel listing the user's chats. Hosted inside the chat screen's adaptive
 * drawer (phones: modal drawer; wide screens: persistent pane).
 *
 * Blank on purpose. Planned content:
 * - List of chats (Room-backed via ChatRepository) + "New chat" action.
 * - Selection / swipe-to-delete.
 */
@Composable
fun ChatListPanel(
    onNewChat: () -> Unit,
    onChatSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement chat list panel (ChatListViewModel is currently empty).
}
