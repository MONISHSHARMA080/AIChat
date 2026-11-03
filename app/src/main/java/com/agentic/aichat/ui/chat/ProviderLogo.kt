package com.agentic.aichat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier.size(size),
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier.matchParentSize()
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.surfaceContainerHighest),
		) {
			Text(
				text = provider.firstOrNull()?.uppercase() ?: "?",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		if (logoUrl != null) {
			AsyncImage(
				model = logoUrl,
				contentDescription = "$provider logo",
				contentScale = ContentScale.Fit,
				modifier = Modifier.matchParentSize().clip(CircleShape),
			)
		}
	}
}
