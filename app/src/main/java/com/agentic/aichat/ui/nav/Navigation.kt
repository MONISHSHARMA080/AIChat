package com.agentic.aichat.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.agentic.aichat.ui.chat.ChatScreen
import com.agentic.aichat.ui.chatlist.ChatListScreen
import com.agentic.aichat.ui.onboarding.OnboardingScreen
import com.agentic.aichat.ui.paywall.PaywallScreen
import com.agentic.aichat.ui.settings.SettingsScreen
import com.agentic.aichat.ui.signin.SignInScreen

private fun NavBackStack<NavKey>.popSafely() {
	if (size > 1) removeAt(size - 1)
}

@Composable
fun AppNavDisplay(
	modifier: Modifier = Modifier,
) {
	val backStack = rememberNavBackStack(ChatDetail())

	Surface(
		modifier = modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.background,
	) {
	NavDisplay(
		backStack = backStack,
		onBack = { backStack.popSafely() },
		transitionSpec = { NavTransitions.forward() },
		popTransitionSpec = { NavTransitions.pop() },
		predictivePopTransitionSpec = { NavTransitions.pop() },
		entryProvider = entryProvider {
			entry<Onboarding> {
				OnboardingScreen(
					onFinished = { modelId, effort, goal ->
						backStack.clear()
						backStack.add(ChatDetail())
					},
					onSkip = {
						backStack.clear()
						backStack.add(ChatDetail())
					},
				)
			}
			entry<SignIn> {
				SignInScreen(
					onSignInSuccess = {
						backStack.clear()
						backStack.add(ChatDetail())
					},
				)
			}
			entry<ChatDetail> { key ->
				ChatScreen(
					chatId = key.chatId,
					onOpenChatList = { backStack.add(ChatList) },
					onOpenPaywall = { backStack.add(Paywall) },
					onOpenSignIn = { backStack.add(SignIn) },
					viewModel = hiltViewModel(key = key.chatId ?: "chat_new"),
				)
			}
			entry<ChatList> {
				ChatListScreen(
					onBack = { backStack.popSafely() },
					onChatSelected = { chatId -> backStack.add(ChatDetail(chatId)) },
					onOpenSettings = { backStack.add(Settings) },
					onNewChat = { backStack.add(ChatDetail()) },
				)
			}
			entry<Settings> {
				SettingsScreen(
					onBack = { backStack.popSafely() },
					onOpenPaywall = { backStack.add(Paywall) },
					onSignOut = {
						backStack.clear()
						backStack.add(SignIn)
					},
				)
			}
			entry<Paywall> {
				PaywallScreen(
					onClose = { backStack.popSafely() },
				)
			}
		},
	)
	}
}
