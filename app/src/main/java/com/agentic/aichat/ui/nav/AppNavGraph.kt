package com.agentic.aichat.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultTransitionSpec
import com.agentic.aichat.ui.chat.ChatScreen
import com.agentic.aichat.ui.chatlist.ChatListScreen
import com.agentic.aichat.ui.onboarding.OnboardingScreen
import com.agentic.aichat.ui.paywall.PaywallScreen
import com.agentic.aichat.ui.settings.SettingsScreen
import com.agentic.aichat.ui.signin.SignInScreen

/** Default (start) destination is the chat. */
@Composable
fun rememberAppBackStack(startKey: NavKey = ChatHome): NavBackStack<NavKey> = rememberNavBackStack(startKey)

private fun NavBackStack<NavKey>.popSafely() {
	// The root entry (ChatHome) must never be popped: rapid/double back gestures
	// would drain the stack and NavDisplay's last-entry pop crashes the app.
	if (size > 1) removeAt(size - 1)
}

private fun chatListTransform(pop: Boolean): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
	val touchesChatList = targetState.key == ChatList || initialState.key == ChatList
	when {
		!touchesChatList -> defaultTransitionSpec<NavKey>()(this)
		pop -> (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
		else -> (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
	}
}

@Composable
fun AppNavDisplay(
	modifier: Modifier = Modifier,
) {
	val backStack = rememberAppBackStack()

	NavDisplay(
		modifier = modifier,
		backStack = backStack,
		// Guarded pop: a second back gesture while the stack is at its root is a no-op.
		onBack = { backStack.popSafely() },
		transitionSpec = chatListTransform(pop = false),
		popTransitionSpec = chatListTransform(pop = true),
		predictivePopTransitionSpec = { chatListTransform(pop = true).invoke(this) },
		entryProvider = entryProvider {
			entry<Onboarding> {
				OnboardingScreen(
					onGetStarted = {
						backStack.clear()
						backStack.add(SignIn)
					},
				)
			}
			entry<SignIn> {
				SignInScreen(
					onSignInSuccess = {
						backStack.clear()
						backStack.add(ChatHome)
					},
				)
			}
			entry<ChatHome> {
				ChatScreen(onOpenChatList = { backStack.add(ChatList) })
			}
			entry<ChatList> {
				ChatListScreen(
					onBack = { backStack.popSafely() },
					onChatSelected = { backStack.popSafely() },
					onOpenSettings = { backStack.add(Settings) },
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
