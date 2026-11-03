package com.agentic.aichat.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentic.aichat.ui.chat.ReasoningEffort

@Composable
fun OnboardingScreen(
    onFinished: (modelId: String, effort: ReasoningEffort, goal: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
}
