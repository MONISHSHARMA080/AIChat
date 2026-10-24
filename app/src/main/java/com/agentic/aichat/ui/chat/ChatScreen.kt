package com.agentic.aichat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Chat screen — the default entry of the app (ChatHome).
 *
 * Blank on purpose: the implementation (message list, streaming bubbles, composer,
 * chats side panel host, model picker trigger) happens here.
 *
 * Wire-up notes:
 * - ViewModel: [ChatViewModel] (Hilt, screen-scoped to this entry).
 * - Side panel: host [com.agentic.aichat.ui.chatlist.ChatListPanel] inside an adaptive
 *   drawer/pane in this screen (phones: modal drawer, wide screens: persistent pane).
 * - Stream errors surfaced by the ViewModel (e.g. backend quota / "limit reached"
 *   state) route the user to the paywall via [onOpenPaywall].
 * - Streaming design: AI source streams deltas; UI rerenders as tokens arrive.
 */
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement chat UI (ChatViewModel is currently empty).
}
