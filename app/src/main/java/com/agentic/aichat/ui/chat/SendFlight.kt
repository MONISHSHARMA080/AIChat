package com.agentic.aichat.ui.chat

// Everything about the "send flight": the data describing one flight and the
// ghost bubble that detaches from the composer, flies up and lands in the list.


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Unclipped bounds in root coordinates. boundsInRoot() clips against ancestors,
 * which gives the wrong rect for a list row that is partly off-screen or
 * behind the composer, exactly where a freshly sent bubble first appears.
 */
internal fun LayoutCoordinates.rectInRoot(): Rect {
	val p = positionInRoot()
	return Rect(p.x, p.y, p.x + size.width, p.y + size.height)
}

/** Latest composer / text bounds, written by the composer, read at send time. */
internal class ComposerBounds {
	var composer: Rect? = null
	var textField: Rect? = null
}

internal data class SendFlight(
		val id: String,
		val text: String,
		val withImage: Boolean,
		/** Where the ghost starts, in root coordinates. */
		val origin: Rect,
)

/**
 * The bubble that flies from the composer to the message list.
 *
 * What makes it feel like iMessage:
 *  1. It appears on the SAME frame the draft clears, exactly over the typed
 *     text, so the text never blinks out. It sizes itself (same max width and
 *     padding as the real bubble), so it does not have to wait for the list.
 *  2. At the start the bubble is the composer's own colour, so you only see
 *     the text lift; the blue bubble then fades in around it.
 *  3. x and y use different springs: x is critically damped, y is softer with
 *     overshoot. The path curves and the bubble settles into its slot.
 *  4. Squash & stretch from the real y-velocity: it elongates while moving
 *     fast and relaxes as it settles. A small "pop" scale adds weight.
 *  5. A lift shadow peaks mid-air and is flat on landing.
 *  6. The tail corner forms late in the flight, like the real thing.
 *  7. The target is read live every frame, so it follows the real bubble while
 *     older rows glide up, the keyboard moves, or the list is still settling.
 *  8. Hand-off to the real bubble happens only once the target has stopped
 *     moving, in the same recomposition that removes the ghost.
 *
 * All motion is a graphicsLayer transform, so it never triggers layout and
 * the text never reflows.
 */
@Composable
internal fun SendFlightGhost(
		flight: SendFlight,
		maxBubbleWidthPx: Float,
		liveTarget: () -> Rect?,
		containerOrigin: () -> Offset,
		onLanded: () -> Unit,
) {
	val scheme = MaterialTheme.colorScheme
	val density = LocalDensity.current
	val currentOnLanded by rememberUpdatedState(onLanded)

	val travelX = remember { Animatable(0f) }
	val travelY = remember { Animatable(0f) }
	val morph = remember { Animatable(0f) }
	val tail = remember { Animatable(0f) }

	LaunchedEffect(flight.id) {
		// The ghost is already on screen (holding at the origin). Wait for the
		// real bubble to report where it will land, then go.
		val first = withTimeoutOrNull(500) {
			snapshotFlow { liveTarget() }.filterNotNull().first()
		}
		if (first == null) {
			// Never got a target: skip the flight and just reveal the message.
			currentOnLanded()
			return@LaunchedEffect
		}

		coroutineScope {
			// Colour / corner morph: starts a hair after lift-off.
			launch { morph.animateTo(1f, tween(340, delayMillis = 30, easing = FastOutSlowInEasing)) }
			// The tail forms in the last third of the flight.
			launch { tail.animateTo(1f, tween(200, delayMillis = 190, easing = FastOutSlowInEasing)) }
			launch {
				travelX.animateTo(1f, spring(dampingRatio = 1f, stiffness = 380f))
			}
			launch {
				travelY.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 280f))
			}
		}

		// Hand off only once the target has stopped moving (list settled), so
		// the swap to the real bubble is pixel-exact.
		withTimeoutOrNull(900) {
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

	val origin = flight.origin
	val startCorner = min(28f, with(density) { origin.height.toDp().value } / 2f)

	val morphT = morph.value.coerceIn(0f, 1f)
	val tailT = tail.value.coerceIn(0f, 1f)
	val flightT = travelX.value.coerceIn(0f, 1f)

	val roundCorner = lerpF(startCorner, 24f, morphT)
	val tailCorner = lerpF(roundCorner, 8f, tailT) // 8dp == the real bubble's tail
	val shape = RoundedCornerShape(
		topStart = roundCorner.dp,
		topEnd = roundCorner.dp,
		bottomStart = roundCorner.dp,
		bottomEnd = tailCorner.dp,
	)
	val bubbleColor = lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, morphT)
	val contentColor = lerp(scheme.onSurface, scheme.onPrimaryContainer, morphT)
	// Lifts off the composer, peaks mid-air, flat again on landing.
	val elevation = (12f * sin(PI.toFloat() * flightT)).dp

	Column(
		horizontalAlignment = Alignment.End,
		modifier = Modifier
			// Same max width as the real bubble, so it wraps text identically.
			.widthIn(max = with(density) { maxBubbleWidthPx.toDp() })
			.graphicsLayer {
				val w = size.width
				val h = size.height
				if (w <= 0f || h <= 0f) {
					alpha = 0f
					return@graphicsLayer
				}
				val target = liveTarget()
				val container = containerOrigin()

				// Until the target is known the ghost simply holds the origin.
				val tx = target?.center?.x ?: origin.center.x
				val ty = target?.center?.y ?: origin.center.y
				val px = travelX.value
				val py = travelY.value
				val cx = lerpF(origin.center.x, tx, px)
				val cy = lerpF(origin.center.y, ty, py)

				val t = px.coerceIn(0f, 1f)
				val startScale = min(origin.width / w, origin.height / h).coerceIn(0.6f, 1.15f)
				// Small "pop" mid-flight so it feels like a physical object.
				val pop = 1f + 0.035f * sin(PI.toFloat() * t)
				val s = lerpF(startScale, 1f, t) * pop

				// Squash & stretch from the real vertical speed (px / second).
				val speed = abs(travelY.velocity) * abs(ty - origin.center.y)
				val stretch = (speed / 24_000f).coerceAtMost(0.07f)

				translationX = cx - container.x - w / 2f
				translationY = cy - container.y - h / 2f
				scaleX = s * (1f - stretch * 0.5f)
				scaleY = s * (1f + stretch)
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
