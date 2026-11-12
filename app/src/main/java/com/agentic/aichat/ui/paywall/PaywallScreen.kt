package com.agentic.aichat.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentic.aichat.ui.theme.navIconButtonShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaywallScreen(
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: PaywallViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	LaunchedEffect(state.purchased) {
		if (state.purchased) {
			viewModel.consumePurchased()
			onClose()
		}
	}
	Scaffold(
		modifier = modifier.fillMaxSize(),
		topBar = {
			CenterAlignedTopAppBar(
				title = { Text(text = "Get coins") },
				navigationIcon = {
					IconButton(onClick = onClose, shapes = navIconButtonShapes()) {
						Icon(Icons.Filled.Close, contentDescription = "Close")
					}
				},
			)
		},
	) { padding ->
		Column(
			verticalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 16.dp),
		) {
			Text(
				text = "Get more coins to keep chatting.",
				style = MaterialTheme.typography.titleLarge,
			)
			if (state.isLoading) {
				CircularProgressIndicator()
			} else if (state.packages.isEmpty()) {
				Text(
					text = state.error ?: "No plans available right now.",
					style = MaterialTheme.typography.bodyMedium,
				)
				OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
					Text(text = "Retry")
				}
			} else {
				val activity = context as? Activity
				state.packages.forEach { pkg ->
					Button(
						onClick = { if (activity != null) viewModel.purchase(activity, pkg) },
						enabled = !state.isPurchasing,
						modifier = Modifier.fillMaxWidth(),
					) {
						Text(text = "${pkg.product.title} — ${pkg.product.price.formatted}")
					}
				}
			}
			if (state.error != null && state.packages.isNotEmpty()) {
				Text(
					text = state.error.orEmpty(),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
				)
			}
			if (state.isPurchasing) {
				CircularProgressIndicator()
			}
			Spacer(modifier = Modifier.height(4.dp))
			OutlinedButton(
				onClick = viewModel::restore,
				enabled = !state.isPurchasing,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(text = "Restore purchase")
			}
		}
	}
}
