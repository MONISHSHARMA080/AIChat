package com.agentic.aichat.ui.chat

// Sample data for previews and the demo screen. Delete once real data is wired in.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus

//internal const val PREVIEW_IMAGE_MESSAGE_ID = "preview-m3"
//
//@Composable
//internal fun rememberPreviewChat(): Pair<ChatEntity, SnapshotStateList<MessageEntity>> {
//	val now = System.currentTimeMillis()
//	val day = 24 * 60 * 60 * 1000L
//	val chat = remember {
//		ChatEntity(
//			id = "preview-chat",
//			title = "Music night out",
//			modelId = "spark-1",
//			createdAt = now - day,
//			updatedAt = now,
//			lastMessageText = null,
//			lastMessageAt = null,
//		)
//	}
//	val messages = remember {
//		mutableStateListOf(
////			MessageEntity(
////				id = "preview-m1",
////				chatId = chat.id,
////				role = MessageRole.USER,
////				content = "Are we still on for Saturday?",
////				createdAt = now - day - 2 * 60 * 60 * 1000L,
////				status = MessageStatus.DONE,
////			),
//			MessageEntity(
//				id = "preview-m2",
//				chatId = chat.id,
//				role = MessageRole.ASSISTANT,
//				content = "Yes, doors at 8. I saved the tickets to the shared album.",
//				createdAt = now - day - 2 * 60 * 60 * 1000L + 60_000L,
//				status = MessageStatus.DONE,
//			),
//			MessageEntity(
//				id = PREVIEW_IMAGE_MESSAGE_ID,
//				chatId = chat.id,
//				role = MessageRole.USER,
//				content = "Make me a fit that's athleisure but elevated",
//				createdAt = now - 3 * 60 * 60 * 1000L,
//				status = MessageStatus.DONE,
//			),
////            MessageEntity(
////                id = "preview-m4",
////                chatId = chat.id,
////                role = MessageRole.ASSISTANT,
////                content = "Love this brief. Here are three elevated athleisure fits.",
////                createdAt = now - 3 * 60 * 60 * 1000L + 60_000L,
////                status = MessageStatus.DONE,
////            ),
////            MessageEntity(
////                id = "preview-m5",
////                chatId = chat.id,
////                role = MessageRole.USER,
////                content = "Second one. Can you save it to the album?",
////                createdAt = now - 2 * 60 * 60 * 1000L,
////                status = MessageStatus.DONE,
////            ),
//			MessageEntity(
//				id = "preview-m6",
//				chatId = chat.id,
//				role = MessageRole.ASSISTANT,
//				content = "Saving it now.",
//				createdAt = now - 2 * 60 * 60 * 1000L + 30_000L,
//				status = MessageStatus.STREAMING,
//			),
//		)
//	}
//	return chat to messages
//}
