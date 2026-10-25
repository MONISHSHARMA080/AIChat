package com.agentic.aichat.ui.chat


import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlinx.coroutines.delay

private const val PREVIEW_IMAGE_MESSAGE_ID = "preview-m3"

private val simulatedReplies = listOf(
	"Got it. Give me a second to put that together.",
	"Nice one. Here's what I'd try first.",
	"Makes sense. Want me to go deeper on any part?",
)

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
//			MessageEntity(
//				id = "preview-m1",
//				chatId = chat.id,
//				role = MessageRole.USER,
//				content = "Are we still on for Saturday?",
//				createdAt = now - day - 2 * 60 * 60 * 1000L,
//				status = MessageStatus.DONE,
//			),
			MessageEntity(
				id = "preview-m2",
				chatId = chat.id,
				role = MessageRole.ASSISTANT,
				content = "Yes, doors at 8. I saved the tickets to the shared album.",
				createdAt = now - day - 2 * 60 * 60 * 1000L + 60_000L,
				status = MessageStatus.DONE,
			),
			MessageEntity(
				id = "jhbefc",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
		modifier: Modifier = Modifier,
		onOpenSettings: () -> Unit = {},
	// Demo only: after a send, show the typing indicator and add a canned reply.
	// Turn off once your real assistant drives the message list.
		simulateReply: Boolean = true,
) {
	val scheme = MaterialTheme.colorScheme
	val (chat, messages) = rememberPreviewChat()
	var imageMessageIds by remember { mutableStateOf(setOf(PREVIEW_IMAGE_MESSAGE_ID)) }
	var draft by remember { mutableStateOf("") }
	var stagedImage by remember { mutableStateOf(false) }
	val listState = rememberLazyListState()
	val context = LocalContext.current
	val density = LocalDensity.current
	val haptic = LocalHapticFeedback.current

	// Messages currently "in the air". Each one has its own independent ghost,
	// so rapid sends overlap naturally instead of queueing behind each other.
	var flights by remember { mutableStateOf<List<SendFlight>>(emptyList()) }
	val flightIds = flights.map { it.id }.toSet()
	// Live bounds (root coordinates) of each in-flight message's real, hidden
	// bubble. The ghost reads this every frame, so it tracks the bubble even
	// while the list is still moving.
	val flightTargets = remember { mutableStateMapOf<String, Rect>() }
	var containerOrigin by remember { mutableStateOf(Offset.Zero) }
	var containerSize by remember { mutableStateOf(IntSize.Zero) }
	val composerBounds = remember { ComposerBounds() }

	// The real user bubble is capped at 85% of the row (list width minus the
	// 12dp content padding on each side). The ghost uses the same cap so it
	// wraps text exactly like the bubble it is about to become.
	val maxBubbleWidthPx = 0.85f * (containerSize.width - with(density) { 24.dp.toPx() }).coerceAtLeast(0f)

	// Messages that already exist must not play the "new assistant message"
	// entrance when the screen first opens.
	var seenAssistantIds by remember {
		mutableStateOf(
			messages.filter { it.role == MessageRole.ASSISTANT }.map { it.id }.toSet(),
		)
	}

	// Extra bottom padding captured at send time only. The list is stationary
	// and full-height, so with the keyboard open the newest slot would sit
	// behind it; this lifts the slot above the composer as part of the send.
	// Tapping the text box never touches it. Rows glide (animateItem) when it
	// changes.
	var sendRevealBottomPx by remember { mutableStateOf(0f) }
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	LaunchedEffect(imeBottomPx) {
		if (imeBottomPx == 0) sendRevealBottomPx = 0f
	}

	// Bring the newest slot into view after every send. scrollToItem is
	// consumed on the next measure pass, so it is not racing the insert.
	var scrollTick by remember { mutableIntStateOf(0) }
	LaunchedEffect(scrollTick) {
		if (scrollTick > 0) listState.scrollToItem(0)
	}

	// Demo assistant: wait for the bubble to land, show "...", then reply.
	var assistantTyping by remember { mutableStateOf(false) }
	var replyRequest by remember { mutableIntStateOf(0) }
	LaunchedEffect(replyRequest) {
//		if (replyRequest == 0 || !simulateReply) return@LaunchedEffect
//		delay(850)
//		assistantTyping = true
//		delay(1_600)
//		assistantTyping = false
//		messages.add(
//			MessageEntity(
//				id = UUID.randomUUID().toString(),
//				chatId = chat.id,
//				role = MessageRole.ASSISTANT,
//				content = simulatedReplies[replyRequest % simulatedReplies.size],
//				createdAt = System.currentTimeMillis(),
//				status = MessageStatus.DONE,
//			),
//		)
	}
	// Keep the typing bubble / reply in view if the user is at the bottom.
	LaunchedEffect(assistantTyping, messages.size) {
		if (flights.isEmpty() && listState.firstVisibleItemIndex <= 1) {
			listState.animateScrollToItem(0)
		}
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
						start = 12.dp,
						end = 12.dp,
						top = 8.dp,
						bottom = 84.dp + with(density) { sendRevealBottomPx.toDp() },
					),
					verticalArrangement = Arrangement.spacedBy(2.dp),
					userScrollEnabled = flights.isEmpty(),
					modifier = Modifier.fillMaxSize(),
				) {
					// Reverse layout: the first item sits at the very bottom.
					if (assistantTyping) {
						item(key = "typing-indicator") {
							TypingIndicatorRow(
								modifier = Modifier.animateItem(
									placementSpec = spring(
										stiffness = Spring.StiffnessMediumLow,
										visibilityThreshold = IntOffset.VisibilityThreshold,
									),
								),
							)
						}
					}
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
							// Older rows glide into their new positions when a
							// message is inserted instead of snapping. No fade
							// on the new row: its ghost is what the user sees.
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

					// A crisp tick the instant the bubble leaves the field.
					haptic.performHapticFeedback(HapticFeedbackType.ContextClick)

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
					replyRequest++
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
						maxBubbleWidthPx = maxBubbleWidthPx,
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
