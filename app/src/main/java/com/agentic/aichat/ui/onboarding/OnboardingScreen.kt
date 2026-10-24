package com.agentic.aichat.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * First-launch onboarding (welcome pages).
 *
 * Blank on purpose. Planned content:
 * - A small swipe pager (3 pages) of value props.
 * - "Get started" → [onGetStarted] which routes to the Sign-in screen
 *   (wired in [com.agentic.aichat.ui.nav.AppNavGraph]).
 */
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO: implement onboarding pagers (OnboardingViewModel marks isOnboarded).
}
