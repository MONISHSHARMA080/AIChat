package com.agentic.aichat.ui.chat


import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import com.agentic.aichat.ui.theme.AIChatTheme
import java.util.UUID
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
				id = "preview-m2",
				chatId = chat.id,
				role = MessageRole.ASSISTANT,
				content = "Yes, doors at 8. I saved the tickets to the shared album.",
				createdAt = now - day - 2 * 60 * 60 * 1000L + 60_000L,
				status = MessageStatus.DONE,
			),
			MessageEntity(
				id = "PREVIEW_IMAGE_MESSAGE_ID",
				chatId = chat.id,
				role = MessageRole.USER,
				content = "Make me a fit that's athleisure but elevated",
				createdAt = now - 3 * 60 * 60 * 1000L,
				status = MessageStatus.DONE,
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
		modifier: Modifier = Modifier,
		onOpenChatList: () -> Unit = {},
) {
	val scheme = MaterialTheme.colorScheme
	val (chat, messages) = rememberPreviewChat()
	var imageMessageIds by remember { mutableStateOf(setOf(PREVIEW_IMAGE_MESSAGE_ID)) }
	var draft by remember { mutableStateOf("") }
	var stagedImage by remember { mutableStateOf(false) }
	var selectedModel by remember(chat.id, chat.modelId) { mutableStateOf(modelById(chat.modelId)) }
	var selectedEffort by remember(chat.id) { mutableStateOf(ReasoningEffort.Medium) }
	var showModelSheet by remember { mutableStateOf(false) }
	val listState = rememberLazyListState()
	val context = LocalContext.current
	val density = LocalDensity.current
	val haptic = LocalHapticFeedback.current

	var flights by remember { mutableStateOf<List<SendFlight>>(emptyList()) }
	val flightIds = flights.map { it.id }.toSet()
	val flightTargets = remember { mutableStateMapOf<String, Rect>() }
	var containerOrigin by remember { mutableStateOf(Offset.Zero) }
	var containerSize by remember { mutableStateOf(IntSize.Zero) }
	val composerBounds = remember { ComposerBounds() }

	var seenAssistantIds by remember {
		mutableStateOf(
			messages.filter { it.role == MessageRole.ASSISTANT }.map { it.id }.toSet(),
		)
	}

	// Captured at send time only: the list never resizes with the keyboard, so this lifts the new slot above the composer.
	var sendRevealBottomPx by remember { mutableStateOf(0f) }
	var composerHeightPx by remember { mutableStateOf(0f) }
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
				navigationIcon = {
					IconButton(
						onClick = onOpenChatList,
						shapes = IconButtonDefaults.shapes(
							shape = CircleShape,
							pressedShape = RoundedCornerShape(16.dp),
						),
					) {
						Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chat list")
					}
				},
			)
		},
	) { padding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.consumeWindowInsets(padding)
				.onGloballyPositioned {
					containerOrigin = it.positionInRoot()
					containerSize = it.size
				},
		) {
			val display = messages.filter { it.role != MessageRole.SYSTEM }.reversed()
			CompositionLocalProvider(LocalOverscrollFactory provides null) {
				LazyColumn(
					state = listState,
					reverseLayout = true,
					contentPadding = PaddingValues(
						start = LIST_SIDE_PADDING,
						end = LIST_SIDE_PADDING,
						top = 8.dp,
						bottom = (if (composerHeightPx > 0f) with(density) { composerHeightPx.toDp() } else 84.dp) + 8.dp + with(density) { sendRevealBottomPx.toDp() },
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
									dampingRatio = FlightMotion.DAMPING,
									stiffness = FlightMotion.STIFFNESS,
									visibilityThreshold = IntOffset.VisibilityThreshold,
								),
							),
						)
					}
				}
			}

			ChatComposer(
				draft = draft,
				onDraftChange = { draft = it },
				selectedModel = selectedModel,
				onModelClick = { showModelSheet = true },
				stagedImage = stagedImage,
				onStageImage = { stagedImage = true },
				onClearImage = { stagedImage = false },
				onTextFieldBoundsInRoot = { composerBounds.textField = it },
				onSend = {
					val text = draft.trim()
					if (text.isEmpty() && !stagedImage) return@ChatComposer
					val id = UUID.randomUUID().toString()
					val hasImage = stagedImage
					if (hasImage) imageMessageIds += id
					sendRevealBottomPx = imeBottomPx.toFloat()

					haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

					val durationScale = Settings.Global.getFloat(
						context.contentResolver,
						Settings.Global.ANIMATOR_DURATION_SCALE,
						1f,
					)

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

					val sidePx = with(density) { LIST_SIDE_PADDING.toPx() }
					val maxWidthPx = 0.85f * (containerSize.width - 2f * sidePx)

					if (durationScale > 0f && origin != null && maxWidthPx > 0f) {
						flights = flights + SendFlight(
							id = id,
							text = text,
							withImage = hasImage,
							origin = origin,
							maxWidthPx = maxWidthPx,
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
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
					.imePadding()
					.onGloballyPositioned {
						composerBounds.composer = it.rectInRoot()
						val contentHeightPx = (it.size.height - imeBottomPx).coerceAtLeast(0).toFloat()
						if (contentHeightPx != composerHeightPx) composerHeightPx = contentHeightPx
					},
			)

			flights.forEach { flight ->
				key(flight.id) {
					SendFlightGhost(
						flight = flight,
						liveTarget = { flightTargets[flight.id] },
						containerOrigin = { containerOrigin },
						onLanded = {
							flights = flights.filterNot { it.id == flight.id }
							flightTargets.remove(flight.id)
						},
					)
				}
			}

			if (showModelSheet) {
				ModelPickerSheet(
					selectedModel = selectedModel,
					selectedEffort = selectedEffort,
					onModelSelected = {
						selectedModel = it
						showModelSheet = false
					},
					onEffortSelected = { selectedEffort = it },
					onDismiss = { showModelSheet = false },
				)
			}
		}
	}
}

private val LIST_SIDE_PADDING = 12.dp


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
		// Column hugs the bubble (unlike fillMaxWidth(0.85f)) so its bounds are the ghost's landing target.
		Spacer(modifier = Modifier.weight(0.15f))
		Column(
			horizontalAlignment = Alignment.End,
			modifier = Modifier
				.weight(0.85f, fill = false)
				// Stays laid out while hidden so it keeps reporting the landing bounds.
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatComposer(
		draft: String,
		onDraftChange: (String) -> Unit,
		selectedModel: AiChatModel,
		onModelClick: () -> Unit,
		stagedImage: Boolean,
		onStageImage: () -> Unit,
		onClearImage: () -> Unit,
		onSend: () -> Unit,
		modifier: Modifier = Modifier,
		onTextFieldBoundsInRoot: (Rect) -> Unit = {},
) {
	val scheme = MaterialTheme.colorScheme
	val motion = MaterialTheme.motionScheme
	val density = LocalDensity.current
	val canSend = draft.isNotBlank() || stagedImage

	val imeVisible = WindowInsets.ime.getBottom(density) > 0
	val expanded = imeVisible || draft.isNotBlank() || stagedImage

	val corner by animateDpAsState(
		targetValue = if (expanded) 28.dp else 96.dp,
		animationSpec = motion.fastSpatialSpec(),
		label = "composer-corners",
	)
	val container by animateColorAsState(
		targetValue = if (expanded) scheme.surfaceContainerHigh else scheme.surfaceContainer,
		animationSpec = motion.defaultEffectsSpec(),
		label = "composer-container",
	)
	// Ghost starts on the typed glyphs, not the full-width field, so offset by the text layout bounds.
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
		shape = RoundedCornerShape(corner),
		color = container,
		contentColor = scheme.onSurface,
		tonalElevation = 2.dp,
		modifier = modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.padding(horizontal = 8.dp, vertical = 8.dp)
				.animateContentSize(
					animationSpec = spring(
						dampingRatio = FlightMotion.DAMPING,
						stiffness = FlightMotion.STIFFNESS,
					),
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
					Icon(Icons.Filled.Add, contentDescription = "Attach")
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
							val placeholderAlpha by animateFloatAsState(
								targetValue = if (draft.isEmpty()) 1f else 0f,
								animationSpec = if (draft.isEmpty()) {
									tween(durationMillis = 220, delayMillis = 140)
								} else {
									tween(durationMillis = 60)
								},
								label = "placeholder-alpha",
							)
							if (placeholderAlpha > 0f) {
								Text(
									text = "Ask anything",
									style = MaterialTheme.typography.bodyLarge,
									color = scheme.onSurfaceVariant,
									modifier = Modifier.graphicsLayer { alpha = placeholderAlpha },
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
			AnimatedVisibility(
				visible = expanded,
				enter = expandVertically(animationSpec = motion.slowSpatialSpec()) +
					fadeIn(animationSpec = motion.defaultEffectsSpec()),
				exit = shrinkVertically(animationSpec = motion.slowSpatialSpec()) +
					fadeOut(animationSpec = motion.defaultEffectsSpec()),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(start = 4.dp, top = 4.dp),
				) {
					ModelSelectorChip(
						model = selectedModel,
						onClick = onModelClick,
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelSelectorChip(
		model: AiChatModel,
		onClick: () -> Unit,
		modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	Surface(
		shape = CircleShape,
		color = scheme.secondaryContainer,
		contentColor = scheme.onSecondaryContainer,
		onClick = onClick,
		modifier = modifier,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
		) {
			Text(
				text = model.name,
				style = MaterialTheme.typography.labelLarge,
			)
			Icon(
				imageVector = Icons.Filled.KeyboardArrowDown,
				contentDescription = "Choose model",
				modifier = Modifier.size(18.dp),
			)
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
