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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.ui.theme.AIChatTheme
import com.agentic.aichat.ui.theme.navIconButtonShapes

private val GroupContainerShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatListScreen(
	modifier: Modifier = Modifier,
	onBack: () -> Unit = {},
	onChatSelected: (String) -> Unit = {},
	onOpenSettings: () -> Unit = {},
	onNewChat: () -> Unit = {},
	viewModel: ChatListViewModel = hiltViewModel(),
) {
	val chats by viewModel.chats.collectAsStateWithLifecycle()
	ChatListContent(
		chats = chats,
		onBack = onBack,
		onChatSelected = onChatSelected,
		onOpenSettings = onOpenSettings,
		onNewChat = onNewChat,
		modifier = modifier,
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatListContent(
	chats: List<ChatEntity>,
	onBack: () -> Unit,
	onChatSelected: (String) -> Unit,
	onOpenSettings: () -> Unit,
	onNewChat: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val pinned = remember(chats) { chats.filter { it.isPinned() } }
	val others = remember(chats) { chats.filterNot { it.isPinned() } }
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
	) { padding ->
		val layoutDirection = LocalLayoutDirection.current
		val scheme = MaterialTheme.colorScheme
		Box(modifier = Modifier.fillMaxSize()) {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.consumeWindowInsets(padding),
				contentPadding = PaddingValues(
					start = padding.calculateStartPadding(layoutDirection) + 16.dp,
					end = padding.calculateEndPadding(layoutDirection) + 16.dp,
					top = padding.calculateTopPadding(),
					bottom = padding.calculateBottomPadding() + 112.dp,
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
					itemsIndexed(pinned, key = { _, chat -> chat.id }) { index, chat ->
						ChatRow(
							chat = chat,
							onClick = { onChatSelected(chat.id) },
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
					itemsIndexed(others, key = { _, chat -> chat.id }) { index, chat ->
						ChatRow(
							chat = chat,
							onClick = { onChatSelected(chat.id) },
							shape = groupRowShape(index, others.size),
							modifier = Modifier.animateItem(),
						)
					}
				}
			}
			Box(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.height(152.dp)
					.background(
						Brush.verticalGradient(
							listOf(
								Color.Transparent,
								scheme.surface,
							),
						),
					),
			)
			Row(
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.navigationBarsPadding()
					.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
			) {
				FloatingActionButton(onClick = onOpenSettings) {
					Icon(Icons.Outlined.Settings, contentDescription = "Settings")
				}
				ExtendedFloatingActionButton(
					onClick = onNewChat,
					icon = {
						Icon(Icons.Filled.Add, contentDescription = null)
					},
					text = { Text(text = "New chat") },
				)
			}
		}
	}
}

private fun ChatEntity.isPinned(): Boolean = false

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
	chat: ChatEntity,
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
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(horizontal = 12.dp),
		) {
			Text(
				text = chat.title,
				style = MaterialTheme.typography.titleMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Text(
			text = DateUtils.getRelativeTimeSpanString(chat.updatedAt).toString(),
			style = MaterialTheme.typography.labelSmall,
			color = scheme.onSurfaceVariant,
		)
	}
}

@Preview(name = "Chats light", showBackground = true)
@Composable
private fun ChatListScreenPreviewLight() {
	AIChatTheme(darkTheme = false) {
		ChatListContent(
			chats = emptyList(),
			onBack = {},
			onChatSelected = {},
			onOpenSettings = {},
			onNewChat = {},
		)
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
		ChatListContent(
			chats = emptyList(),
			onBack = {},
			onChatSelected = {},
			onOpenSettings = {},
			onNewChat = {},
		)
	}
}
