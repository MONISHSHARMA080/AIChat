package com.agentic.aichat.ui.signin

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.agentic.aichat.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
				Log.d("AiChat", "error and it is state:${state.message}")
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
				.padding(horizontal = 24.dp)
				.padding(top = 64.dp, bottom = 24.dp),
			horizontalAlignment = Alignment.Start,
		) {
			Surface(
				modifier = Modifier.size(96.dp),
				shape = RoundedCornerShape(28.dp),
				color = MaterialTheme.colorScheme.primaryContainer,
				contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.Rounded.CloudSync,
						contentDescription = null,
						modifier = Modifier.size(48.dp),
					)
				}
			}
			Spacer(Modifier.height(32.dp))
			Text(
				text = "Sign in",
				style = typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
				color = colorScheme.onSurface,
			)
			Spacer(Modifier.height(12.dp))
			Text(
				text = "Save your progress and sync chats across devices, so you never lose them.",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(Modifier.height(4.dp))
			Text(
				text = "Model, goal, and setup carry over automatically.",
				style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(Modifier.weight(1f))
			Spacer(Modifier.height(16.dp))
			val loading = uiState == SignInUiState.Loading
			Button(
				onClick = {
					val activity = context as? Activity ?: return@Button
					viewModel.onGoogleSignIn(activity)
				},
				enabled = !loading,
				modifier = Modifier
					.fillMaxWidth()
					.height(64.dp),
				shapes = ButtonDefaults.shapes(),
			) {
				if (loading) {
					LoadingIndicator(modifier = Modifier.size(24.dp))
				} else {
					Icon(
						painter = painterResource(R.drawable.ic_google_g),
						contentDescription = null,
						modifier = Modifier.size(20.dp),
						tint = Color.Unspecified,
					)
					Spacer(Modifier.size(ButtonDefaults.IconSpacing))
					Text(
						text = "Continue with Google",
						style = typography.titleMedium,
					)
				}
			}
			Spacer(Modifier.height(4.dp))
			TextButton(
				onClick = onSignInSuccess,
				enabled = !loading,
				modifier = Modifier.align(Alignment.CenterHorizontally),
			) {
				Text("Skip for now")
			}
		}
	}
}
