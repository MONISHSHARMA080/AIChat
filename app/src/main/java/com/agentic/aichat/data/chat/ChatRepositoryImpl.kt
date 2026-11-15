package com.agentic.aichat.data.chat

import android.content.Context
import android.util.Base64
import androidx.core.net.toUri
import androidx.room3.withWriteTransaction
import com.agentic.aichat.data.chat.db.ChatDao
import com.agentic.aichat.data.chat.db.ChatDatabase
import com.agentic.aichat.data.chat.db.MessageDao
import com.agentic.aichat.data.chat.db.entity.ChatAttachment
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageRole
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import com.agentic.aichat.util.log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.StreamResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow

@Singleton
class ChatRepositoryImpl @Inject constructor(
	@ApplicationContext private val context: Context,
	private val functions: FirebaseFunctions,
	private val database: ChatDatabase,
	private val chatDao: ChatDao,
	private val messageDao: MessageDao,
) : ChatRepository {

	override fun observeChats(): Flow<List<ChatEntity>> = chatDao.observeAll()

	override fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeById(chatId)

	override fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
		messageDao.observeForChat(chatId)

	override suspend fun updateChatModel(chatId: String, modelId: String) =
		chatDao.updateModel(chatId, modelId)

	override fun sendMessage(
		chatId: String?,
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment>,
	): Flow<SendMessageEvent> = flow {
		val sentAt = System.currentTimeMillis()
		val title = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX_LENGTH)
			?: "New chat"
		val encodedAttachments = attachments.map { attachment ->
			val bytes = try {
				context.contentResolver.openInputStream(attachment.uri.toUri())?.use { it.readBytes() }
			} catch (_: IOException) {
				null
			} catch (_: SecurityException) {
				null
			}
			if (bytes == null) throw ChatException(ChatError.AttachmentUnreadable(attachment.name))
			mapOf(
				"name" to attachment.name,
				"mimeType" to attachment.mimeType,
				"data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
			)
		}
		val requestData = buildMap<String, Any?> {
			put("model", modelId)
			put("chatId", chatId)
			put("title", title)
			put("message", mapOf("content" to text, "attachments" to encodedAttachments))
			if (!reasoning.isNullOrBlank()) put("reasoning", reasoning)
		}
		val replyBuilder = StringBuilder()
		var serverChatId: String? = null
		functions.getHttpsCallable("chat")
			.stream(requestData)
			.asFlow()
			.catch { cause ->
				if (cause !is FirebaseFunctionsException) throw cause
				log("chat callable failed: code=${cause.code} message=${cause.message}")
				throw ChatException(
					when {
						cause.message == "Error retrieving context" -> ChatError.Unauthorized
						cause.code == FirebaseFunctionsException.Code.UNAUTHENTICATED -> ChatError.Unauthorized
						cause.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ChatError.OutOfCredits
						cause.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT -> ChatError.Rejected(cause.message)
						else -> ChatError.ServiceUnavailable
					},
				)
			}
			.collect { response ->
				when (response) {
					is StreamResponse.Message -> {
						val chunk = (response.message.data as? Map<*, *>)?.get("text") as? String
						if (!chunk.isNullOrEmpty()) {
							replyBuilder.append(chunk)
							emit(SendMessageEvent.ReplyChunk(chunk))
						}
					}
					is StreamResponse.Result ->
						serverChatId = (response.result.data as? Map<*, *>)?.get("chatId") as? String
				}
			}
		val savedChatId = serverChatId ?: chatId ?: UUID.randomUUID().toString()
		val reply = replyBuilder.toString()
		val savedAt = System.currentTimeMillis()
		database.withWriteTransaction {
			if (chatDao.getById(savedChatId) == null) {
				chatDao.insert(
					ChatEntity(
						id = savedChatId,
						title = title,
						modelId = modelId,
						createdAt = sentAt,
						updatedAt = savedAt,
						lastMessageText = reply,
						lastMessageAt = savedAt,
					),
				)
			} else {
				chatDao.updateLastMessage(savedChatId, reply, savedAt)
			}
			messageDao.insert(
				MessageEntity(
					id = messageId,
					chatId = savedChatId,
					role = MessageRole.USER,
					content = text,
					createdAt = sentAt,
					status = MessageStatus.DONE,
					attachments = attachments,
				),
			)
			messageDao.insert(
				MessageEntity(
					id = UUID.randomUUID().toString(),
					chatId = savedChatId,
					role = MessageRole.ASSISTANT,
					content = reply,
					createdAt = savedAt,
					status = MessageStatus.DONE,
				),
			)
		}
		emit(SendMessageEvent.Saved(savedChatId))
	}.flowOn(Dispatchers.IO)

	private companion object {
		const val TITLE_MAX_LENGTH = 48
	}
}
