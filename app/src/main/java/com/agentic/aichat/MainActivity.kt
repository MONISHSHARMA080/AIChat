package com.agentic.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation3.runtime.NavKey
import com.agentic.aichat.ui.nav.AppNavDisplay
import com.agentic.aichat.ui.nav.ChatHome
import com.agentic.aichat.ui.nav.Favorites
import com.agentic.aichat.ui.nav.Profile
import com.agentic.aichat.ui.nav.rememberAppBackStack
import com.agentic.aichat.review.ReviewHelper
import com.agentic.aichat.ui.theme.AIChatTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		ReviewHelper.recordLaunch(this)
		ReviewHelper.maybeRequestInAppReview(applicationContext, this)
		setContent {
			AIChatTheme {
				AIChatApp()
			}
		}
	}
}

@PreviewScreenSizes
@Composable
fun AIChatApp() {
	val backStack = rememberAppBackStack()

	val currentDestination = when (backStack.lastOrNull()) {
		ChatHome -> AppDestinations.HOME
		Favorites -> AppDestinations.FAVORITES
		Profile -> AppDestinations.PROFILE
		else -> AppDestinations.HOME
	}

	NavigationSuiteScaffold(
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
		}
	) {
		Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
			AppNavDisplay(
				backStack = backStack,
				modifier = Modifier.padding(innerPadding)
			)
		}
	}
}

enum class AppDestinations(
		val label: String,
		val icon: Int,
		val key: NavKey,
) {
	HOME("Home", R.drawable.ic_home, ChatHome),
	FAVORITES("Favorites", R.drawable.ic_favorite, Favorites),
	PROFILE("Profile", R.drawable.ic_account_box, Profile),
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
	Text(
		text = "Hello Android!",
		modifier = modifier
	)
}

@Composable
fun FavoritesScreen(modifier: Modifier = Modifier) {
	Text(
		text = "Favorites",
		modifier = modifier
	)
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
	Text(
		text = "Profile",
		modifier = modifier
	)
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
	AIChatTheme {
		ChatScreen()
	}
}
