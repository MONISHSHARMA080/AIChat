package com.agentic.aichat.ui.signin

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SignInScreen(
	onSignInSuccess: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: SignInViewModel = hiltViewModel(),
) {
	val uiState by viewModel.uiState.collectAsState()
	val snackBarHostState = remember { SnackbarHostState() }
	val context = LocalContext.current

	LaunchedEffect(uiState) {
		when (val state = uiState) {
			SignInUiState.Success -> onSignInSuccess()
			is SignInUiState.Error -> {
				Log.d("AiChat","error and it is state:${state.message}")
				snackBarHostState.showSnackbar(state.message)
				viewModel.consumeError()
			}
			SignInUiState.Idle, SignInUiState.Loading -> Unit
		}
	}


	Scaffold(
		modifier = modifier.fillMaxSize(),
		snackbarHost = { SnackbarHost(snackBarHostState) },
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Icon(
				imageVector = Icons.Filled.AccountCircle,
				contentDescription = null,
				modifier = Modifier.size(72.dp),
				tint = MaterialTheme.colorScheme.primary,
			)
			Spacer(Modifier.height(16.dp))
			Text(
				text = "Welcome to AIChat",
				style = MaterialTheme.typography.headlineMedium,
			)
			Spacer(Modifier.height(8.dp))
			Text(
				text = "Sign in to chat with AI on any device.",
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(Modifier.height(32.dp))
			val loading = uiState == SignInUiState.Loading
			Button(
				onClick = {
					val activity = context as? Activity ?: return@Button
					viewModel.onGoogleSignIn(activity)
				},
				enabled = !loading,
				modifier = Modifier.fillMaxWidth(),
			) {
				if (loading) {
					CircularProgressIndicator(
						modifier = Modifier.size(20.dp),
						strokeWidth = 2.dp,
					)
				} else {
					Text("Continue with Google")
				}
			}
			Spacer(Modifier.height(8.dp))
			TextButton(
				onClick = onSignInSuccess,
				enabled = !loading,
			) {
				Text("Skip for now")
			}
		}
	}
}


