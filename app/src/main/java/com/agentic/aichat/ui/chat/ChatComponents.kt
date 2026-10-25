package com.agentic.aichat.ui.chat

// Message rows, bubbles and the composer. No send-flight logic lives here
// (see SendFlight.kt); this file only knows how to draw and how to report
// bounds / trigger onSend.

// ChatComponents.kt  (file 3 of 3)
//
// Keep the same `package ...` line as your other files, and import your own
// MessageEntity / MessageRole / MessageStatus.
//
// Message rows, typing indicator and composer.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Shapes / labels
// ---------------------------------------------------------------------------

internal fun statusLabel(status: MessageStatus): String = when (status) {
	MessageStatus.SENDING -> "Sending"
	MessageStatus.STREAMING -> "Generating"
	MessageStatus.ERROR -> "Not sent"
	MessageStatus.DONE -> ""
}

// The user bubble's tail corner is 8dp. SendFlight.kt morphs to exactly this.
internal val userBubbleShape = RoundedCornerShape(
	topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp,
)
internal val assistantBubbleShape = RoundedCornerShape(
	topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp,
)

// ---------------------------------------------------------------------------
// Message rows
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageRow(
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
			// AI replies: slide + scale in from the bottom-left, no flight.
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
					initialOffsetX = { -it / 3 },
				) + scaleIn(
					animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
					initialScale = 0.85f,
					transformOrigin = TransformOrigin(0f, 1f),
				) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
			) {
				AssistantMessage(message = message)
			}
		}
	}
}

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
		// bounds are exactly where the ghost has to land. The ghost uses the
		// same 85% cap (see maxBubbleWidthPx in ChatScreen).
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

/** The assistant's "..." bubble: pops in from the bottom-left, three dots wave. */
@Composable
internal fun TypingIndicatorRow(modifier: Modifier = Modifier) {
	val scheme = MaterialTheme.colorScheme
	var shown by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) { shown = true }
	Row(
		modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.Start,
	) {
		androidx.compose.animation.AnimatedVisibility(
			visible = shown,
			enter = fadeIn(tween(160)) + scaleIn(
				animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
				initialScale = 0.4f,
				transformOrigin = TransformOrigin(0f, 1f),
			),
		) {
			Surface(
				shape = assistantBubbleShape,
				color = scheme.secondaryContainer,
				contentColor = scheme.onSecondaryContainer,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(5.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					repeat(3) { index -> TypingDot(delayMillis = index * 160) }
				}
			}
		}
	}
}

@Composable
private fun TypingDot(delayMillis: Int) {
	val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
	val transition = rememberInfiniteTransition(label = "typing-dot")
	val phase = transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 1_200, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
			initialStartOffset = StartOffset(delayMillis),
		),
		label = "dot-phase",
	)
	Box(
		modifier = Modifier
			.size(8.dp)
			.graphicsLayer {
				val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * phase.value)
				alpha = 0.35f + 0.65f * wave
				val s = 0.8f + 0.25f * wave
				scaleX = s
				scaleY = s
			}
			.background(dotColor, CircleShape),
	)
}

@Composable
internal fun ImagePlaceholder(modifier: Modifier = Modifier) {
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
internal fun Composer(
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
		// The composer springs back to one line after a send.
		Column(
			modifier = Modifier
				.padding(horizontal = 8.dp, vertical = 8.dp)
				.animateContentSize(
					animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
				),
		) {
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
							// The placeholder fades back in a beat after a send,
							// once the text has left the field.
							androidx.compose.animation.AnimatedVisibility(
								visible = draft.isEmpty(),
								enter = fadeIn(tween(220, delayMillis = 140)),
								exit = fadeOut(tween(60)),
							) {
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
				// Fixed 48dp slot so the field never shifts when the button
				// pops in or out. It springs in as soon as there is something
				// to send, and shrinks away while the bubble flies off.
				Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
					androidx.compose.animation.AnimatedVisibility(
						visible = canSend,
						enter = scaleIn(
							animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
							initialScale = 0.3f,
						) + fadeIn(tween(120)),
						exit = scaleOut(
							animationSpec = tween(140),
							targetScale = 0.3f,
						) + fadeOut(tween(140)),
					) {
						FilledIconButton(
							onClick = onSend,
							interactionSource = sendInteraction,
							modifier = Modifier.scale(sendScale),
						) {
							Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
						}
					}
				}
			}
		}
	}
}
