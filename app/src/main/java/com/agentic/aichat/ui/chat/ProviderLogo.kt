package com.agentic.aichat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ProviderLogo(
	logoUrl: String?,
	provider: String,
	modifier: Modifier = Modifier,
	size: Dp = 28.dp,
) {
	var loaded by remember(logoUrl) { mutableStateOf(false) }
	val logoPadding = if (size > 24.dp) 6.dp else 4.dp
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier.size(size),
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier.matchParentSize()
				.clip(CircleShape)
				.background(Color.White),
		) {
			if (!loaded) {
				Text(
					text = provider.firstOrNull()?.uppercase() ?: "?",
					style = MaterialTheme.typography.labelLarge,
					color = Color.Black,
				)
			}
		}
		if (logoUrl != null) {
			AsyncImage(
				model = logoUrl,
				contentDescription = "$provider logo",
				contentScale = ContentScale.Fit,
				onLoading = { loaded = false },
				onSuccess = { loaded = true },
				onError = { loaded = false },
				modifier = Modifier.matchParentSize()
					.clip(CircleShape)
					.background(Color.White)
					.padding(logoPadding),
			)
		}
	}
}