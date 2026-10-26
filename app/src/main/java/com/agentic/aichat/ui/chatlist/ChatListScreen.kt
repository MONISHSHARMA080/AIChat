package com.agentic.aichat.ui.chatlist


import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.ui.theme.AIChatTheme
import com.agentic.aichat.ui.theme.navIconButtonShapes
import java.util.concurrent.TimeUnit

private data class ChatListEntry(
		val chat: ChatEntity,
		val isPinned: Boolean,
)

private val GroupContainerShape = RoundedCornerShape(28.dp)

@Composable
private fun rememberDummyChats(): List<ChatListEntry> = remember {
	val now = System.currentTimeMillis()
	listOf(
			dummyChat("c-music", "Music night out", "Doors at 8 — see you there!", now - TimeUnit.MINUTES.toMillis(12), isPinned = true),
			dummyChat("c-trip", "Weekend trip", "Train tickets are booked for Saturday.", now - TimeUnit.HOURS.toMillis(3), isPinned = true, modelId = "spark-reasoner"),
			dummyChat("c-recipes", "Smoothie recipes", "This mango one is a keeper, saved.", now - TimeUnit.HOURS.toMillis(26)),
			dummyChat("c-rent", "Apartment hunt", "Landlord replied, viewing at 5pm.", now - TimeUnit.HOURS.toMillis(30), modelId = "spark-1-mini"),
			dummyChat("c-gym", "Gym plan", "I added two more pull-up days.", now - TimeUnit.DAYS.toMillis(2)),
			dummyChat("c-thesis", "Thesis outline", "Chapter 3 skeleton is ready for review.", now - TimeUnit.DAYS.toMillis(4), modelId = "spark-reasoner"),
			dummyChat("c-plants", "Plant care", "The fiddle leaf needs water tomorrow.", now - TimeUnit.DAYS.toMillis(6), modelId = "spark-1-mini"),
		)
}

private fun dummyChat(
		id: String,
		title: String,
		preview: String,
		updatedAt: Long,
		isPinned: Boolean = false,
		modelId: String = "spark-1",
) = ChatListEntry(
		chat = ChatEntity(
			id = id,
			title = title,
			modelId = modelId,
			createdAt = updatedAt - TimeUnit.DAYS.toMillis(3),
			updatedAt = updatedAt,
			lastMessageText = preview,
			lastMessageAt = updatedAt,
		),
		isPinned = isPinned,
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatListScreen(
		modifier: Modifier = Modifier,
		onBack: () -> Unit = {},
		onChatSelected: (String) -> Unit = {},
		onOpenSettings: () -> Unit = {},
) {
	val chats = rememberDummyChats()
	val pinned = remember(chats) { chats.filter { it.isPinned } }
	val others = remember(chats) { chats.filterNot { it.isPinned } }
	val topBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

	Scaffold(
		modifier = modifier
			.fillMaxSize()
			.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
		topBar = {
			MediumFlexibleTopAppBar(
				title = { Text(text = "Chats") },
				navigationIcon = {
					IconButton(
						onClick = onBack,
						shapes = navIconButtonShapes(),
					) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
					}
				},
				scrollBehavior = topBarScrollBehavior,
			)
		},
		floatingActionButton = {
			FloatingActionButton(onClick = onOpenSettings) {
				Icon(Icons.Outlined.Settings, contentDescription = "Settings")
			}
		},
	) { padding ->
		val layoutDirection = LocalLayoutDirection.current
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.consumeWindowInsets(padding),
			contentPadding = PaddingValues(
				start = padding.calculateStartPadding(layoutDirection) + 16.dp,
				end = padding.calculateEndPadding(layoutDirection) + 16.dp,
				top = padding.calculateTopPadding(),
				bottom = padding.calculateBottomPadding() + 72.dp,
			),
		) {
			if (pinned.isNotEmpty()) {
				item(key = "pinned-header") {
					SectionHeader(
						title = "Pinned",
						icon = Icons.Filled.PushPin,
						modifier = Modifier.animateItem(),
					)
				}
				itemsIndexed(pinned, key = { _, entry -> entry.chat.id }) { index, entry ->
					ChatRow(
						entry = entry,
						onClick = { onChatSelected(entry.chat.id) },
						shape = groupRowShape(index, pinned.size),
						modifier = Modifier.animateItem(),
					)
				}
			}
			if (others.isNotEmpty()) {
				item(key = "all-header") {
					SectionHeader(
						title = "All chats",
						modifier = Modifier.animateItem(),
					)
				}
				itemsIndexed(others, key = { _, entry -> entry.chat.id }) { index, entry ->
					ChatRow(
						entry = entry,
						onClick = { onChatSelected(entry.chat.id) },
						shape = groupRowShape(index, others.size),
						modifier = Modifier.animateItem(),
					)
				}
			}
		}
	}
}

@Composable
private fun SectionHeader(
		title: String,
		modifier: Modifier = Modifier,
		icon: ImageVector? = null,
) {
	val scheme = MaterialTheme.colorScheme
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 4.dp, top = 20.dp, bottom = 10.dp),
	) {
		icon?.let {
			Icon(
				imageVector = it,
				contentDescription = null,
				tint = scheme.primary,
				modifier = Modifier.size(14.dp),
			)
			Spacer(modifier = Modifier.width(6.dp))
		}
		Text(
			text = title,
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.SemiBold,
			color = scheme.onSurfaceVariant,
		)
	}
}

private fun groupRowShape(index: Int, count: Int): Shape = when (index) {
	0 if count - 1 == index -> GroupContainerShape
	0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
	count - 1 -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
	else -> RectangleShape
}

@Composable
private fun ChatRow(
		entry: ChatListEntry,
		onClick: () -> Unit,
		shape: Shape,
		modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.fillMaxWidth()
			.background(scheme.surfaceContainerLow, shape)
			.clip(shape)
			.clickable(onClickLabel = "Open chat", onClick = onClick)
			.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
	) {
		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.size(44.dp)
				.clip(CircleShape)
				.background(scheme.tertiaryContainer),
		) {
			Icon(
				imageVector = Icons.AutoMirrored.Filled.Chat,
				contentDescription = null,
				tint = scheme.onTertiaryContainer,
				modifier = Modifier.size(22.dp),
			)
		}
		Column(
			verticalArrangement = Arrangement.spacedBy(2.dp),
			modifier = Modifier
				.weight(1f)
				.padding(horizontal = 12.dp),
		) {
			Text(
				text = entry.chat.title,
				style = MaterialTheme.typography.titleMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			val preview = entry.chat.lastMessageText.orEmpty()
			if (preview.isNotBlank()) {
				Text(
					text = preview,
					style = MaterialTheme.typography.bodyMedium,
					color = scheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
		Text(
			text = DateUtils.getRelativeTimeSpanString(entry.chat.updatedAt).toString(),
			style = MaterialTheme.typography.labelSmall,
			color = scheme.onSurfaceVariant,
		)
	}
}

@Preview(name = "Chats light", showBackground = true)
@Composable
private fun ChatListScreenPreviewLight() {
	AIChatTheme(darkTheme = false) {
		ChatListScreen()
	}
}

@Preview(
	name = "Chats dark",
	showBackground = true,
	uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatListScreenPreviewDark() {
	AIChatTheme(darkTheme = true) {
		ChatListScreen()
	}
}
