package com.agentic.aichat.ui.chat

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import com.agentic.aichat.ui.theme.AIChatTheme
import java.util.UUID

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
            MessageEntity(
                id = "preview-m4",
                chatId = chat.id,
                role = MessageRole.ASSISTANT,
                content = "Love this brief. Here are three elevated athleisure fits.",
                createdAt = now - 3 * 60 * 60 * 1000L + 60_000L,
                status = MessageStatus.DONE,
            ),
            MessageEntity(
                id = "preview-m5",
                chatId = chat.id,
                role = MessageRole.USER,
                content = "Second one. Can you save it to the album?",
                createdAt = now - 2 * 60 * 60 * 1000L,
                status = MessageStatus.DONE,
            ),
            MessageEntity(
                id = "preview-m6",
                chatId = chat.id,
                role = MessageRole.ASSISTANT,
                content = "Saving it now.",
                createdAt = now - 2 * 60 * 60 * 1000L + 30_000L,
                status = MessageStatus.STREAMING,
            ),
			MessageEntity(
				id = "preview-m7",
				chatId = chat.id,
				role = MessageRole.USER,
				content = "Second one. Can you save it to the album? 222",
				createdAt = now - 2 * 60 * 60 * 1000L,
				status = MessageStatus.DONE,
			),
			MessageEntity(
				id = "preview-m8",
				chatId = chat.id,
				role = MessageRole.ASSISTANT,
				content = "Saving it now. 0---",
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

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val visible = messages.filter { it.role != MessageRole.SYSTEM }
            val display = visible.reversed()
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(display, key = { message -> message.id }) { message ->
                    MessageRow(
                        message = message,
                        withImage = message.id in imageMessageIds,
                    )
                }
            }
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                stagedImage = stagedImage,
                onStageImage = { stagedImage = true },
                onClearImage = { stagedImage = false },
                onSend = {
                    val text = draft.trim()
                    if (text.isEmpty() && !stagedImage) return@Composer
                    val id = UUID.randomUUID().toString()
                    if (stagedImage) imageMessageIds += id
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
                },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).imePadding(),
            )
        }
    }
}

@Composable
private fun MessageRow(message: MessageEntity, withImage: Boolean) {
    when (message.role) {
        MessageRole.USER -> UserMessage(message = message, withImage = withImage)
        else -> AssistantMessage(message = message)
    }
}

private val userBubbleShape = RoundedCornerShape(
    topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 8.dp,
)
private val assistantBubbleShape = RoundedCornerShape(
    topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp,
)

@Composable
private fun UserMessage(message: MessageEntity, withImage: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(0.85f),
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

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    stagedImage: Boolean,
    onStageImage: () -> Unit,
    onClearImage: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val canSend = draft.isNotBlank() || stagedImage
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
                    textStyle = TextStyle(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 4,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
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
                FilledTonalIconButton(onClick = onSend, enabled = canSend) {
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
