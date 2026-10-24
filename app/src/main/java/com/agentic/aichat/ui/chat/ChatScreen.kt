package com.agentic.aichat.ui.chat


import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import com.agentic.aichat.ui.theme.AIChatTheme
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.collections.filter

private const val PREVIEW_IMAGE_MESSAGE_ID = "preview-m3"

@Composable
private fun rememberPreviewChat(): Pair<ChatEntity, SnapshotStateList<MessageEntity>> {
	val now = System.currentTimeMillis()
	val day = 24 * 60 * 60 * 1000L
	val chat = remember {
		ChatEntity(
			id = "preview-chat",
			title = "Music night out",
			modelId = "spark-1",
			createdAt = now - day,
			updatedAt = now,
			lastMessageText = null,
			lastMessageAt = null,
		)
	}
	val messages = remember {
		mutableStateListOf(
			MessageEntity(
				id = "preview-m1",
				chatId = chat.id,
				role = MessageRole.USER,
				content = "Are we still on for Saturday?",
				createdAt = now - day - 2 * 60 * 60 * 1000L,
				status = MessageStatus.DONE,
			),
			MessageEntity(
				id = "preview-m2",
				chatId = chat.id,
				role = MessageRole.ASSISTANT,
				content = "Yes, doors at 8. I saved the tickets to the shared album.",
				createdAt = now - day - 2 * 60 * 60 * 1000L + 60_000L,
				status = MessageStatus.DONE,
			),
			MessageEntity(
				id = PREVIEW_IMAGE_MESSAGE_ID,
				chatId = chat.id,
				role = MessageRole.USER,
				content = "Make me a fit that's athleisure but elevated",
				createdAt = now - 3 * 60 * 60 * 1000L,
				status = MessageStatus.DONE,
			),
//            MessageEntity(
//                id = "preview-m4",
//                chatId = chat.id,
//                role = MessageRole.ASSISTANT,
//                content = "Love this brief. Here are three elevated athleisure fits.",
//                createdAt = now - 3 * 60 * 60 * 1000L + 60_000L,
//                status = MessageStatus.DONE,
//            ),
//            MessageEntity(
//                id = "preview-m5",
//                chatId = chat.id,
//                role = MessageRole.USER,
//                content = "Second one. Can you save it to the album?",
//                createdAt = now - 2 * 60 * 60 * 1000L,
//                status = MessageStatus.DONE,
//            ),
            MessageEntity(
                id = "preview-m6",
                chatId = chat.id,
                role = MessageRole.ASSISTANT,
                content = "Saving it now.",
                createdAt = now - 2 * 60 * 60 * 1000L + 30_000L,
                status = MessageStatus.STREAMING,
            ),
		)
	}
	return chat to messages
}

private fun statusLabel(status: MessageStatus): String = when (status) {
	MessageStatus.SENDING -> "Sending"
	MessageStatus.STREAMING -> "Generating"
	MessageStatus.ERROR -> "Not sent"
	MessageStatus.DONE -> ""
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Unclipped bounds in root coordinates. boundsInRoot() clips against ancestors,
 * which gives the wrong rect for a list row that is partly off-screen or
 * behind the composer, exactly where a freshly sent bubble first appears.
 */
private fun LayoutCoordinates.rectInRoot(): Rect {
	val p = positionInRoot()
	return Rect(p.x, p.y, p.x + size.width, p.y + size.height)
}

/** Latest composer / text bounds, written by the composer, read at send time. */
private class ComposerBounds {
	var composer: Rect? = null
	var textField: Rect? = null
}

private data class SendFlight(
		val id: String,
		val text: String,
		val withImage: Boolean,
		/** Where the ghost starts, in root coordinates. */
		val origin: Rect,
)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
		modifier: Modifier = Modifier,
		onOpenSettings: () -> Unit = {},
) {
	val scheme = MaterialTheme.colorScheme
	val (chat, messages) = rememberPreviewChat()
	var imageMessageIds by remember { mutableStateOf(setOf(PREVIEW_IMAGE_MESSAGE_ID)) }
	var draft by remember { mutableStateOf("") }
	var stagedImage by remember { mutableStateOf(false) }
	val listState = rememberLazyListState()
	val context = LocalContext.current
	val density = LocalDensity.current

	var flights by remember { mutableStateOf<List<SendFlight>>(emptyList()) }
	val flightIds = flights.map { it.id }.toSet()
	val flightTargets = remember { mutableStateMapOf<String, Rect>() }
	var containerOrigin by remember { mutableStateOf(Offset.Zero) }
	val composerBounds = remember { ComposerBounds() }

	var seenAssistantIds by remember {
		mutableStateOf(
			messages.filter { it.role == MessageRole.ASSISTANT }.map { it.id }.toSet(),
		)
	}

	var sendRevealBottomPx by remember { mutableFloatStateOf(0f) }
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	LaunchedEffect(imeBottomPx) {
		if (imeBottomPx == 0) sendRevealBottomPx = 0f
	}

	var scrollTick by remember { mutableIntStateOf(0) }
	LaunchedEffect(scrollTick) {
		if (scrollTick > 0) listState.scrollToItem(0)
	}

	Scaffold(
		modifier = modifier.fillMaxSize(),
		containerColor = scheme.surface,
		topBar = {
			CenterAlignedTopAppBar(
				title = {
					Text(text = chat.title, style = MaterialTheme.typography.titleMedium)
				},
				actions = {
					IconButton(onClick = onOpenSettings) {
						Icon(Icons.Filled.Settings, contentDescription = "Settings")
					}
				},
			)
		},
	) { padding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.onGloballyPositioned { containerOrigin = it.positionInRoot() },
		) {
			val display = messages.filter { it.role != MessageRole.SYSTEM }.reversed()
			CompositionLocalProvider(LocalOverscrollFactory provides null) {
				LazyColumn(
					state = listState,
					reverseLayout = true,
					contentPadding = PaddingValues(
						start = 12.dp,
						end = 12.dp,
						top = 8.dp,
						bottom = 84.dp + with(density) { sendRevealBottomPx.toDp() },
					),
					verticalArrangement = Arrangement.spacedBy(2.dp),
					userScrollEnabled = flights.isEmpty(),
					modifier = Modifier.fillMaxSize(),
				) {
					items(display, key = { message -> message.id }) { message ->
						MessageRow(
							message = message,
							withImage = message.id in imageMessageIds,
							isHiddenForFlight = message.id in flightIds,
							isNewAssistant = message.role == MessageRole.ASSISTANT &&
									message.id !in seenAssistantIds,
							onAssistantSeen = { seenAssistantIds += message.id },
							onBubbleBoundsInRoot = { rect ->
								if (flightTargets[message.id] != rect) {
									flightTargets[message.id] = rect
								}
							},
							modifier = Modifier.animateItem(
								fadeInSpec = null,
								placementSpec = spring(
									stiffness = Spring.StiffnessMediumLow,
									visibilityThreshold = IntOffset.VisibilityThreshold,
								),
							),
						)
					}
				}
			}

			Composer(
				draft = draft,
				onDraftChange = { draft = it },
				stagedImage = stagedImage,
				onStageImage = { stagedImage = true },
				onClearImage = { stagedImage = false },
				onTextFieldBoundsInRoot = { composerBounds.textField = it },
				onSend = {
					val text = draft.trim()
					if (text.isEmpty() && !stagedImage) return@Composer
					val id = UUID.randomUUID().toString()
					val hasImage = stagedImage
					if (hasImage) imageMessageIds += id
					sendRevealBottomPx = imeBottomPx.toFloat()

					// Reduce-motion (animator duration scale 0): skip the
					// flight. The message is inserted and readable immediately
					// either way, so it is announced without waiting for any
					// animation.
					val durationScale = Settings.Global.getFloat(
						context.contentResolver,
						Settings.Global.ANIMATOR_DURATION_SCALE,
						1f,
					)

					// Snapshot where the ghost starts BEFORE the draft clears:
					// on the typed text for a text message (padded to match
					// the bubble's own padding), on the whole composer when
					// an image is attached.
					val padX = with(density) { 16.dp.toPx() }
					val padY = with(density) { 12.dp.toPx() }
					val textRect = composerBounds.textField?.let {
						Rect(it.left - padX, it.top - padY, it.right + padX, it.bottom + padY)
					}
					val origin = if (hasImage || text.isBlank()) {
						composerBounds.composer
					} else {
						textRect ?: composerBounds.composer
					}

					if (durationScale > 0f && origin != null) {
						flights = flights + SendFlight(
							id = id,
							text = text,
							withImage = hasImage,
							origin = origin,
						)
					}
					messages.add(
						MessageEntity(
							id = id,
							chatId = chat.id,
							role = MessageRole.USER,
							content = text,
							createdAt = System.currentTimeMillis(),
							status = MessageStatus.DONE,
						),
					)
					draft = ""
					stagedImage = false
					scrollTick++
				},
				// Only the composer consumes the IME inset (once, here).
				// It overlays the stationary list instead of resizing it.
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
					.imePadding()
					.onGloballyPositioned { composerBounds.composer = it.rectInRoot() },
			)

			// Ghosts draw above both the list and the composer.
			flights.forEach { flight ->
				key(flight.id) {
					SendFlightGhost(
						flight = flight,
						liveTarget = { flightTargets[flight.id] },
						containerOrigin = { containerOrigin },
						onLanded = {
							// Removing the ghost and un-hiding the real bubble
							// happen in the same recomposition: no flicker.
							flights = flights.filterNot { it.id == flight.id }
							flightTargets.remove(flight.id)
						},
					)
				}
			}
		}
	}
}

// ---------------------------------------------------------------------------
// Send flight
// ---------------------------------------------------------------------------

/**
 * The bubble that flies from the composer to the message list.
 *
 * - It is laid out ONCE at the final bubble size, so text never reflows. All
 *   motion is a graphicsLayer tranueewsform (translate + uniform scale), which
 *   stays smooth because it never triggers layout.
 * - Position = lerp(origin, liveTarget, progress). The target is read live every
 *   frame, so the ghost follows the real bubble even while the list scrolls,
 *   rows glide, or the keyboard moves.
 * - Horizontal and vertical progress use different springs: x is stiff and
 *   critically damped, y is softer with overshoot. The path therefore curves
 *   and the bubble settles with a small bounce.
 * - Colour, corner radii and shadow morph from "text box" to "sent bubble".
 */
@Composable
private fun SendFlightGhost(
		flight: SendFlight,
		liveTarget: () -> Rect?,
		containerOrigin: () -> Offset,
		onLanded: () -> Unit,
) {
	val scheme = MaterialTheme.colorScheme
	val density = LocalDensity.current
	val currentOnLanded by rememberUpdatedState(onLanded)

	var ghostPx by remember { mutableStateOf<Size?>(null) }
	val travelX = remember { Animatable(0f) }
	val travelY = remember { Animatable(0f) }
	val morph = remember { Animatable(0f) }

	LaunchedEffect(flight.id) {
		// Wait for the real bubble to report where it will land.
		val first = withTimeoutOrNull(400) {
			snapshotFlow { liveTarget() }.filterNotNull().first()
		}
		if (first == null) {
			// Never got a target: skip the flight and just reveal the message.
			currentOnLanded()
			return@LaunchedEffect
		}
		ghostPx = first.size

		coroutineScope {
			launch { morph.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
			launch {
				travelX.animateTo(
					1f,
					spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f),
				)
			}
			launch {
				travelY.animateTo(
					1f,
					spring<Float>(dampingRatio = 0.7f, stiffness = 300f),
				)
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

	val ghost = ghostPx ?: return
	val origin = flight.origin
	val startScale = min(origin.width / ghost.width, origin.height / ghost.height)
		.coerceIn(0.6f, 1.15f)
	val startCorner = min(28f, with(density) { origin.height.toDp().value } / 2f)

	val morphT = morph.value.coerceIn(0f, 1f)
	val flightT = travelX.value.coerceIn(0f, 1f)
	fun corner(end: Float) = lerpF(startCorner, end, morphT).dp
	val shape = RoundedCornerShape(
		topStart = corner(24f),
		topEnd = corner(24f),
		bottomStart = corner(24f),
		bottomEnd = corner(8f), // the tail forms during the flight
	)
	val bubbleColor = lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, morphT)
	val contentColor = lerp(scheme.onSurface, scheme.onPrimaryContainer, morphT)
	// Lifts off the composer, peaks mid-air, is flat again on landing.
	val elevation = (10f * sin(PI.toFloat() * flightT)).dp

	Column(
		horizontalAlignment = Alignment.End,
		modifier = Modifier
			.width(with(density) { ghost.width.toDp() })
			.height(with(density) { ghost.height.toDp() })
			.graphicsLayer {
				val target = liveTarget()
				if (target == null) {
					alpha = 0f
					return@graphicsLayer
				}
				val container = containerOrigin()
				val px = travelX.value
				val py = travelY.value
				val cx = lerpF(origin.center.x, target.center.x, px)
				val cy = lerpF(origin.center.y, target.center.y, py)
				val pxClamped = px.coerceIn(0f, 1f)
				// Tiny "pop" mid-flight so it feels like a physical object.
				val pop = 1f + 0.03f * sin(PI.toFloat() * pxClamped)
				val s = lerpF(startScale, 1f, pxClamped) * pop
				translationX = cx - container.x - ghost.width / 2f
				translationY = cy - container.y - ghost.height / 2f
				scaleX = s
				scaleY = s
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

// ---------------------------------------------------------------------------
// Message rows
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
		message: MessageEntity,
		withImage: Boolean,
		modifier: Modifier = Modifier,
		isHiddenForFlight: Boolean = false,
		isNewAssistant: Boolean = false,
		onAssistantSeen: () -> Unit = {},
		onBubbleBoundsInRoot: (Rect) -> Unit = {},
) {
	when (message.role) {
		MessageRole.USER -> UserMessage(
			message = message,
			withImage = withImage,
			hidden = isHiddenForFlight,
			onBoundsInRoot = onBubbleBoundsInRoot,
			modifier = modifier,
		)
		else -> {
			// AI replies: normal item entry from the left, no flight.
			var visible by remember(message.id) { mutableStateOf(!isNewAssistant) }
			LaunchedEffect(message.id) {
				if (isNewAssistant) {
					visible = true
					onAssistantSeen()
				}
			}
			AnimatedVisibility(
				visible = visible,
				modifier = modifier,
				enter = slideInHorizontally(
					animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
					initialOffsetX = { -it / 2 },
				) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
			) {
				AssistantMessage(message = message)
			}
		}
	}
}

private val userBubbleShape = RoundedCornerShape(
	topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp,
)
private val assistantBubbleShape = RoundedCornerShape(
	topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp,
)

@Composable
private fun UserMessage(
		message: MessageEntity,
		withImage: Boolean,
		modifier: Modifier = Modifier,
		hidden: Boolean = false,
		onBoundsInRoot: (Rect) -> Unit = {},
) {
	val scheme = MaterialTheme.colorScheme
	Row(
		modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.End,
	) {
		// 15% spacer + a content-sized column capped at 85%. Unlike
		// fillMaxWidth(0.85f), the column's bounds hug the bubble, so those
		// bounds are exactly where the ghost has to land.
		Spacer(modifier = Modifier.weight(0.15f))
		Column(
			horizontalAlignment = Alignment.End,
			modifier = Modifier
				.weight(0.85f, fill = false)
				// Still laid out (so its bounds are the flight destination)
				// while the ghost is flying; revealed when the ghost lands.
				.graphicsLayer { alpha = if (hidden) 0f else 1f }
				.onGloballyPositioned { if (hidden) onBoundsInRoot(it.rectInRoot()) },
		) {
			if (withImage) {
				ImagePlaceholder(modifier = Modifier.fillMaxWidth().height(260.dp))
				Spacer(modifier = Modifier.height(6.dp))
			}
			if (message.content.isNotBlank()) {
				Surface(
					shape = userBubbleShape,
					color = scheme.primaryContainer,
					contentColor = scheme.onPrimaryContainer,
				) {
					Text(
						text = message.content,
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
					)
				}
			}
			val label = statusLabel(message.status)
			if (label.isNotEmpty()) {
				Text(
					text = label,
					style = MaterialTheme.typography.labelSmall,
					color = scheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 4.dp, end = 4.dp),
				)
			}
		}
	}
}

@Composable
private fun AssistantMessage(message: MessageEntity) {
	if (message.content.isBlank()) return
	val scheme = MaterialTheme.colorScheme
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.Start,
	) {
		Column(
			horizontalAlignment = Alignment.Start,
			modifier = Modifier.fillMaxWidth(0.85f),
		) {
			Surface(
				shape = assistantBubbleShape,
				color = scheme.secondaryContainer,
				contentColor = scheme.onSecondaryContainer,
			) {
				Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
					if (message.status == MessageStatus.STREAMING) {
						StreamingText(text = message.content)
					} else {
						Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
					}
				}
			}
		}
	}
}

@Composable
private fun StreamingText(text: String, modifier: Modifier = Modifier) {
	val base = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f)
	val transition = rememberInfiniteTransition(label = "streaming-text")
	val offset by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1_200f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1_800),
			repeatMode = RepeatMode.Restart,
		),
		label = "shimmer",
	)
	val brush = Brush.linearGradient(
		colors = listOf(base, base, Color.White, base, base),
		start = Offset(offset - 500f, 0f),
		end = Offset(offset, 0f),
	)
	Text(
		text = text,
		style = MaterialTheme.typography.bodyLarge.copy(brush = brush),
		modifier = modifier,
	)
}

@Composable
private fun ImagePlaceholder(modifier: Modifier = Modifier) {
	val scheme = MaterialTheme.colorScheme
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier
			.clip(RoundedCornerShape(20.dp))
			.background(
				Brush.linearGradient(
					listOf(
						scheme.primaryContainer,
						scheme.tertiaryContainer,
						scheme.secondaryContainer,
					),
				),
			),
	) {
		Icon(
			imageVector = Icons.Filled.Image,
			contentDescription = null,
			tint = scheme.onPrimaryContainer,
			modifier = Modifier.size(40.dp),
		)
	}
}

// ---------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Composer(
		draft: String,
		onDraftChange: (String) -> Unit,
		stagedImage: Boolean,
		onStageImage: () -> Unit,
		onClearImage: () -> Unit,
		onSend: () -> Unit,
		modifier: Modifier = Modifier,
		onTextFieldBoundsInRoot: (Rect) -> Unit = {},
) {
	val scheme = MaterialTheme.colorScheme
	val canSend = draft.isNotBlank() || stagedImage
	// The field's layout box spans the full composer width; the ghost must
	// start on the typed glyphs, so report field bounds offset by the
	// actual text layout bounds inside the field.
	var fieldBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
	var textBoundsInField by remember { mutableStateOf<Rect?>(null) }
	val reportTextBounds = {
		val field = fieldBoundsInRoot
		if (field != null) {
			val local = textBoundsInField
			onTextFieldBoundsInRoot(
				if (local != null && local.width > 0f) {
					Rect(
						left = field.left + local.left,
						top = field.top + local.top,
						right = field.left + local.right,
						bottom = field.top + local.bottom,
					)
				} else {
					field
				},
			)
		}
	}
	Surface(
		shape = RoundedCornerShape(28.dp),
		color = scheme.surfaceContainerHighest,
		contentColor = scheme.onSurface,
		tonalElevation = 2.dp,
		modifier = modifier.fillMaxWidth(),
	) {
		Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).animateContentSize()) {
			if (stagedImage) {
				Box {
					ImagePlaceholder(modifier = Modifier.fillMaxWidth().height(180.dp))
					Surface(
						shape = CircleShape,
						color = scheme.scrim.copy(alpha = 0.55f),
						modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
						onClick = onClearImage,
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(
								imageVector = Icons.Filled.Close,
								contentDescription = "Remove image",
								tint = scheme.surface,
							)
						}
					}
				}
				Spacer(modifier = Modifier.height(8.dp))
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				IconButton(onClick = onStageImage) {
					Icon(Icons.Filled.Image, contentDescription = "Attach image")
				}
				BasicTextField(
					value = draft,
					onValueChange = onDraftChange,
					onTextLayout = { result ->
						if (result.lineCount > 0) {
							var right = 0f
							for (line in 0 until result.lineCount) {
								right = maxOf(right, result.getLineRight(line))
							}
							textBoundsInField = Rect(
								left = result.getLineLeft(0),
								top = result.getLineTop(0),
								right = right,
								bottom = result.getLineBottom(result.lineCount - 1),
							)
							reportTextBounds()
						}
					},
					// Same type style as the sent bubble, so the glyphs don't
					// change size when the ghost takes over from the field.
					textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
					cursorBrush = SolidColor(scheme.primary),
					maxLines = 4,
					modifier = Modifier
						.weight(1f)
						.padding(horizontal = 4.dp)
						.onGloballyPositioned {
							fieldBoundsInRoot = it.rectInRoot()
							reportTextBounds()
						},
					decorationBox = { inner ->
						Box(contentAlignment = Alignment.CenterStart) {
							if (draft.isEmpty()) {
								Text(
									text = "Ask anything",
									style = MaterialTheme.typography.bodyLarge,
									color = scheme.onSurfaceVariant,
								)
							}
							inner()
						}
					},
				)
				val sendInteraction = remember { MutableInteractionSource() }
				val pressed by sendInteraction.collectIsPressedAsState()
				val sendScale by animateFloatAsState(
					targetValue = if (pressed) 0.82f else 1f,
					animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
					label = "send-press",
				)
				FilledTonalIconButton(
					onClick = onSend,
					enabled = canSend,
					interactionSource = sendInteraction,
					modifier = Modifier.scale(sendScale),
				) {
					Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
				}
			}
		}
	}
}

@Preview(name = "Chat light", showBackground = true)
@Composable
private fun ChatScreenPreviewLight() {
	AIChatTheme(darkTheme = false) {
		ChatScreen()
	}
}

@Preview(
	name = "Chat dark",
	showBackground = true,
	uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatScreenPreviewDark() {
	AIChatTheme(darkTheme = true) {
		ChatScreen()
	}
}
