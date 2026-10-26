package com.agentic.aichat.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Settings screen.
 *
 * Blank on purpose. Planned content:
 * - Default AI model picker (same options as the in-chat model picker).
 * - Account row (signed-in Firebase user).
 * - Upgrade entry → [onOpenPaywall].
 * - Send feedback (triggers Play in-app review from the review/ wiring).
 * - Sign out → [onSignOut].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement settings UI (SettingsViewModel is currently empty).
}
