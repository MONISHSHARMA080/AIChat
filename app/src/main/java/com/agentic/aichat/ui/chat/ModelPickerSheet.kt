package com.agentic.aichat.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Bottom sheet listing the AI models selectable for the active chat.
 *
 * Blank on purpose. Planned content:
 * - ModalBottomSheet listing the available models (fast / balanced / reasoning).
 * - Selected model id → ViewModel → persisted with the chat.
 */
@Composable
fun ModelPickerSheet(
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement model picker sheet.
}
