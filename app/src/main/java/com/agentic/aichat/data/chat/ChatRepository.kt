package com.agentic.aichat.data.chat

import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for chats and their messages.
 *
 * [ChatEntity] and [MessageEntity] are the single model; there is no
 * separate domain model or mapper.
 * The UI observes the [Flow]s; writes go through the suspend functions.
 * Implementations persist to the `chats` / `messages` tables and must keep
 * the chat row's denormalized preview (`lastMessageText` / `lastMessageAt` /
 * `updatedAt`) in sync with message writes in the same transaction.
 */
interface ChatRepository {

	/** All chats for the side panel, most recently updated first. */
	fun observeChats(): Flow<List<ChatEntity>>

	fun observeChat(chatId: String): Flow<ChatEntity?>

	/** Messages of one chat in display order; re-emits while a reply streams. */
	fun observeMessages(chatId: String): Flow<List<MessageEntity>>

	suspend fun getChat(chatId: String): ChatEntity?

	suspend fun getMessages(chatId: String): List<MessageEntity>

	/** Creates an empty chat and returns it. */
	suspend fun createChat(title: String, modelId: String): ChatEntity

	suspend fun renameChat(chatId: String, title: String)

	suspend fun updateChatModel(chatId: String, modelId: String)

	/** Deletes the chat and, by cascade, all of its messages. */
	suspend fun deleteChat(chatId: String)

	/** Inserts a message (user message, or an empty STREAMING assistant placeholder). */
	suspend fun addMessage(message: MessageEntity)

	/** Appends one streamed token chunk to an assistant message. */
	suspend fun appendMessageContent(messageId: String, delta: String)

	suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

	suspend fun deleteMessage(messageId: String)
}
