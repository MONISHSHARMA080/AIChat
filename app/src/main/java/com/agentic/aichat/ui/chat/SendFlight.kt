package com.agentic.aichat.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

// Vertical spring doubles as the list's item placement spec, so rows rise in step with the bubble.
internal object FlightMotion {
	const val DAMPING = 0.74f
	const val STIFFNESS = 380f

	const val X_DAMPING = 1f
	const val X_STIFFNESS = 240f
}

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
	val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
	return t * t * (3f - 2f * t)
}

// boundsInRoot() clips against ancestors, so it misreports a fresh bubble that is still off-screen or behind the composer.
internal fun LayoutCoordinates.rectInRoot(): Rect {
	val p = positionInRoot()
	return Rect(p.x, p.y, p.x + size.width, p.y + size.height)
}

internal class ComposerBounds {
	var composer: Rect? = null
	var textField: Rect? = null
}

internal data class SendFlight(
		val id: String,
		val text: String,
		val withImage: Boolean,
		val origin: Rect,
		val maxWidthPx: Float,
)

@Composable
internal fun SendFlightGhost(
		flight: SendFlight,
		liveTarget: () -> Rect?,
		containerOrigin: () -> Offset,
		onLanded: () -> Unit,
) {
	val scheme = MaterialTheme.colorScheme
	val density = LocalDensity.current
	val currentOnLanded by rememberUpdatedState(onLanded)

	val travelX = remember { Animatable(0f) }
	val travelY = remember { Animatable(0f) }

	LaunchedEffect(flight.id) {
		val first = withTimeoutOrNull(300) {
			snapshotFlow { liveTarget() }.filterNotNull().first()
		}
		if (first == null) {
			currentOnLanded()
			return@LaunchedEffect
		}

		coroutineScope {
			launch {
				travelY.animateTo(
					1f,
					spring(dampingRatio = FlightMotion.DAMPING, stiffness = FlightMotion.STIFFNESS),
				)
			}
			launch {
				travelX.animateTo(
					1f,
					spring(dampingRatio = FlightMotion.X_DAMPING, stiffness = FlightMotion.X_STIFFNESS),
				)
			}
		}

		// Hand off only once the target has stopped moving (list settled), so the
		// swap to the real bubble is pixel-exact.
		withTimeoutOrNull(900.milliseconds) {
			var last = liveTarget()
			var stable = 0
			while (stable < 3) {
				withFrameNanos { }
				val now = liveTarget()
				if (now == last) {
					stable++
				} else {
					stable = 0
					last = now
				}
			}
		}
		currentOnLanded()
	}

	val flightT = travelY.value.coerceIn(0f, 1f)
	val colorT = smoothstep(0.04f, 0.62f, flightT)
	val tailT = smoothstep(0.45f, 0.95f, flightT)
	val shape = RoundedCornerShape(
		topStart = 24.dp,
		topEnd = 24.dp,
		bottomStart = 24.dp,
		bottomEnd = lerpF(24f, 8f, tailT).dp,
	)
	val bubbleColor = lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, colorT)
	val contentColor = lerp(scheme.onSurface, scheme.onPrimaryContainer, colorT)
	val elevation = (14f * sin(PI.toFloat() * flightT)).dp

	val origin = flight.origin
	val maxWidth = with(density) { flight.maxWidthPx.toDp() }

	Column(
		horizontalAlignment = Alignment.End,
		modifier = Modifier
			.widthIn(max = maxWidth)
			.graphicsLayer {
				val container = containerOrigin()
				val target = liveTarget() ?: origin
				val px = travelX.value
				val py = travelY.value
				val cx = lerpF(origin.center.x, target.center.x, px)
				val cy = lerpF(origin.center.y, target.center.y, py)

				val t = py.coerceIn(0f, 1f)
				val pop = 1f + 0.05f * sin(PI.toFloat() * min(1f, t * 1.4f))
				val stretch = 0.05f * sin(PI.toFloat() * t)
				val squash = ((py - 1f).coerceIn(0f, 0.2f) * 2.5f).coerceAtMost(0.05f)
				val base = if (flight.withImage && size.width > 0f && size.height > 0f) {
					val startScale = min(origin.width / size.width, origin.height / size.height)
						.coerceIn(0.5f, 1.1f)
					lerpF(startScale, 1f, t)
				} else {
					1f
				}

				scaleX = base * pop * (1f - stretch * 0.6f + squash * 0.8f)
				scaleY = base * pop * (1f + stretch - squash)
				translationX = cx - container.x - size.width / 2f
				translationY = cy - container.y - size.height / 2f
			}
			.clearAndSetSemantics {},
	) {
		if (flight.withImage) {
			ImagePlaceholder(modifier = Modifier.fillMaxWidth().height(260.dp))
			Spacer(modifier = Modifier.height(6.dp))
		}
		if (flight.text.isNotBlank()) {
			Surface(
				shape = shape,
				color = bubbleColor,
				contentColor = contentColor,
				shadowElevation = elevation,
			) {
				Text(
					text = flight.text,
					style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
				)
			}
		}
	}
}
