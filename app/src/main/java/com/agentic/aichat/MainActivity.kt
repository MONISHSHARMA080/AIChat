package com.agentic.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agentic.aichat.ui.nav.AppNavDisplay
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
				AppNavDisplay()
			}
		}
	}
}