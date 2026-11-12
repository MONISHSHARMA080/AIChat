package com.agentic.aichat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentic.aichat.ui.theme.navIconButtonShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
	onBack: () -> Unit,
	onOpenPaywall: () -> Unit,
	onSignOut: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SettingsViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	LaunchedEffect(Unit) {
		viewModel.refreshBalance()
	}
	Scaffold(
		modifier = modifier.fillMaxSize(),
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(text = "Settings") },
				navigationIcon = {
					IconButton(onClick = onBack, shapes = navIconButtonShapes()) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				},
			)
		},
	) { padding ->
		Column(
			verticalArrangement = Arrangement.spacedBy(16.dp),
			modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 16.dp),
		) {
			Surface(
				shape = MaterialTheme.shapes.medium,
				color = MaterialTheme.colorScheme.surfaceContainerLow,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(text = "Coins", style = MaterialTheme.typography.titleMedium)
						Text(
							text = if (state.isLoading) "Loading..." else "${state.balance} coins",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						if (state.error != null) {
							Text(
								text = state.error.orEmpty(),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.error,
							)
						}
					}
					if (state.isLoading) {
						CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
					} else {
						FilledTonalButton(onClick = onOpenPaywall) {
							Text(text = if (state.balance == 0) "Get coins" else "Upgrade")
						}
					}
				}
			}
			Spacer(modifier = Modifier.height(4.dp))
			OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
				Text(text = "Sign out")
			}
		}
	}
}
