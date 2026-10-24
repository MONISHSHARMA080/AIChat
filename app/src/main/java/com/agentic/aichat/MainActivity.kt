package com.agentic.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavKey
import com.agentic.aichat.ui.nav.AppNavDisplay
import com.agentic.aichat.ui.nav.ChatHome
import com.agentic.aichat.ui.nav.Settings
import com.agentic.aichat.ui.nav.rememberAppBackStack
import com.agentic.aichat.ui.theme.AIChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			AIChatTheme {
				AIChatApp()
			}
		}
	}
}

@Composable
fun AIChatApp() {
	val backStack = rememberAppBackStack()

	// Hide the navigation suite on full-screen destinations (Onboarding, Sign-in, Paywall).
	// See Step 2.1 "Control navigation area visibility" of the adaptive guidance.
	val suiteState = rememberNavigationSuiteScaffoldState()
	val currentDestination = AppDestinations.entries.firstOrNull { it.key == backStack.lastOrNull() }
	val isNavAreaVisible = currentDestination != null
	LaunchedEffect(isNavAreaVisible) {
		if (isNavAreaVisible) suiteState.show() else suiteState.hide()
	}

	NavigationSuiteScaffold(
		state = suiteState,
		navigationSuiteItems = {
			AppDestinations.entries.forEach { destination ->
				item(
					icon = {
						Icon(
							painterResource(destination.icon),
							contentDescription = destination.label
						)
					},
					label = { Text(destination.label) },
					selected = destination == currentDestination,
					onClick = {
						if (destination != currentDestination) {
							backStack.clear()
							backStack.add(destination.key)
						}
					}
				)
			}
		},
	) {
		AppNavDisplay(
			backStack = backStack,
			modifier = Modifier,
		)
	}
}

internal enum class AppDestinations(
	val label: String,
	val icon: Int,
	val key: NavKey,
) {
	CHAT("Chats", R.drawable.ic_home, ChatHome),
	SETTINGS("Settings", R.drawable.ic_settings, Settings),
}
