package com.agentic.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.agentic.aichat.BuildConfig
import com.agentic.aichat.ui.nav.AppNavDisplay
import com.agentic.aichat.ui.theme.AIChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	private val localNetworkRequest = registerForActivityResult(
		androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
	) {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (BuildConfig.DEBUG) {
			if (
				androidx.core.content.ContextCompat.checkSelfPermission(
					this,
					android.Manifest.permission.ACCESS_LOCAL_NETWORK,
				) != android.content.pm.PackageManager.PERMISSION_GRANTED
			) {
				localNetworkRequest.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK)
			}
		}
		enableEdgeToEdge()
		setContent {
			AIChatTheme {
				AppNavDisplay()
			}
		}
	}
}