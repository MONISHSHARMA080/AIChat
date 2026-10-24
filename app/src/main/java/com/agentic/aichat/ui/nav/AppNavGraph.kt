package com.agentic.aichat.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.agentic.aichat.ChatScreen
import com.agentic.aichat.FavoritesScreen
import com.agentic.aichat.ProfileScreen

@Composable
fun rememberAppBackStack(): NavBackStack<NavKey> = rememberNavBackStack(ChatHome)

@Composable
fun AppNavDisplay(
		backStack: NavBackStack<NavKey>,
		modifier: Modifier = Modifier,
) {
	NavDisplay(
		modifier = modifier,
		backStack = backStack,
		onBack = { backStack.removeLastOrNull() },
		entryProvider = entryProvider {
			entry(key = ChatHome) {
				ChatScreen()
			}
			entry(key = Favorites) {
				FavoritesScreen()
			}
			entry(key = Profile) {
				ProfileScreen()
			}
		},
	)
}
