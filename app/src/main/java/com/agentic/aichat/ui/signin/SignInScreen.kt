package com.agentic.aichat.ui.signin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Sign-in screen (shown after onboarding; account creation is required).
 *
 * Blank on purpose. Planned content:
 * - Google sign-in via Credential Manager (androidx.credentials + googleid, both
 *   already declared in the version catalog).
 * - On success → [onSignInSuccess] which routes to ChatHome
 *   (wired in [com.agentic.aichat.ui.nav.AppNavGraph]).
 * - Requires Firebase Auth + a web client ID constant (no google-services.json yet —
 *   Firebase needs its config before real sign-in works).
 */
@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement Google sign-in (SignInViewModel is currently empty).
}
