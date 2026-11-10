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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext

@Singleton
class ChatRepositoryImpl @Inject constructor(
	@ApplicationContext private val context: Context,
	private val functions: FirebaseFunctions,
	private val database: ChatDatabase,
	private val chatDao: ChatDao,
	private val messageDao: MessageDao,
) : ChatRepository {

	override fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeById(chatId)

	override fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
		messageDao.observeForChat(chatId)

	override suspend fun getMessages(chatId: String): List<MessageEntity> =
		messageDao.getForChat(chatId)

	override suspend fun createChat(title: String, modelId: String): ChatEntity {
		val now = System.currentTimeMillis()
		val chat = ChatEntity(
			id = UUID.randomUUID().toString(),
			title = title,
			modelId = modelId,
			createdAt = now,
			updatedAt = now,
			lastMessageText = null,
			lastMessageAt = null,
		)
		chatDao.insert(chat)
		return chat
	}

	override suspend fun renameChat(chatId: String, title: String) =
		chatDao.rename(chatId, title)

	override suspend fun updateChatModel(chatId: String, modelId: String) =
		chatDao.updateModel(chatId, modelId)

	override suspend fun deleteChat(chatId: String) = chatDao.deleteById(chatId)

	override suspend fun addMessage(message: MessageEntity) {
		database.withWriteTransaction {
			messageDao.insert(message)
			chatDao.updateLastMessage(message.chatId, message.content, message.createdAt)
		}
	}

	override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) =
		messageDao.updateStatus(messageId, status)

	override suspend fun deleteMessage(messageId: String) = messageDao.deleteById(messageId)

	override suspend fun sendMessage(
		chatId: String,
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment>,
	): Outcome<Flow<String>, ChatError> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		val userMessage = MessageEntity(
			id = messageId,
			chatId = chatId,
			role = MessageRole.USER,
			content = text,
			createdAt = now,
			status = MessageStatus.DONE,
			attachments = attachments,
		)
		val payload = when (val built = buildPayload(messageDao.getForChat(chatId) + userMessage, modelId, reasoning)) {
			is Outcome.Ok -> built.value
			is Outcome.Err -> return@withContext Outcome.Err(ChatError.AttachmentUnreadable(built.error))
		}
		val preview = text.ifBlank { attachments.firstOrNull()?.name.orEmpty() }
		database.withWriteTransaction {
			messageDao.insert(userMessage)
			chatDao.updateLastMessage(chatId, preview, now)
		}
		val replyId = UUID.randomUUID().toString()
		Outcome.Ok(
			writeReplyFlow(streamChat(payload)) { content ->
				writeAssistantMessage(chatId, replyId, content)
			},
		)
	}

	override suspend fun retryMessage(
		chatId: String,
		messageId: String,
		modelId: String,
		reasoning: String?,
	): Outcome<Flow<String>, ChatError> = withContext(Dispatchers.IO) {
		val history = messageDao.getForChat(chatId).filterNot { it.id == messageId }
		val payload = when (val built = buildPayload(history, modelId, reasoning)) {
			is Outcome.Ok -> built.value
			is Outcome.Err -> return@withContext Outcome.Err(ChatError.AttachmentUnreadable(built.error))
		}
		Outcome.Ok(
			writeReplyFlow(streamChat(payload)) { content ->
				database.withWriteTransaction {
					messageDao.updateContent(messageId, content)
					messageDao.updateStatus(messageId, MessageStatus.DONE)
					chatDao.updateLastMessage(chatId, content, System.currentTimeMillis())
				}
			},
		)
	}

	private suspend fun writeAssistantMessage(
		chatId: String,
		messageId: String,
		content: String,
	) {
		val now = System.currentTimeMillis()
		database.withWriteTransaction {
			messageDao.insert(
				MessageEntity(
					id = messageId,
					chatId = chatId,
					role = MessageRole.ASSISTANT,
					content = content,
					createdAt = now,
					status = MessageStatus.DONE,
				),
			)
			chatDao.updateLastMessage(chatId, content, now)
		}
	}

	private fun buildPayload(
		history: List<MessageEntity>,
		modelId: String,
		reasoning: String?,
	): Outcome<Map<String, Any?>, String> {
		val messages = history.map { message ->
			val files = message.attachments.map { attachment ->
				val bytes = readAttachmentBytes(attachment) ?: return Outcome.Err(attachment.name)
				mapOf(
					"name" to attachment.name,
					"mimeType" to attachment.mimeType,
					"data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
				)
			}
			mapOf(
				"role" to message.role.name.lowercase(),
				"content" to message.content,
				"attachments" to files,
			)
		}
		val payload = mutableMapOf<String, Any?>(
			"model" to modelId,
			"messages" to messages,
		)
		reasoning?.ifBlank { null }?.let { payload["reasoning"] = it }
		return Outcome.Ok(payload)
	}

	private fun streamChat(payload: Map<String, Any?>): Flow<String> = functions.getHttpsCallable("chat")
		.stream(payload)
		.asFlow()
		.mapNotNull { response ->
			when (response) {
				is StreamResponse.Message -> parseReplyText(response.message.data)
				is StreamResponse.Result -> null
				else -> null
			}
		}
		.catch { e ->
			if (e is CancellationException) throw e
			val functionException = e as? FirebaseFunctionsException
			log("chat callable failed: code=${functionException?.code} message=${functionException?.message}")
			throw functionException?.let(::mapHttpsError)?.let(::ChatStreamException) ?: e
		}

	private fun parseReplyText(raw: Any?): String? {
		if (raw == null) return null
		if (raw is Map<*, *>) return raw["text"]?.toString()?.ifEmpty { null }
		return raw.toString().ifEmpty { null }
	}

	private fun writeReplyFlow(
		upstream: Flow<String>,
		onComplete: suspend (String) -> Unit,
	): Flow<String> = flow {
		val content = StringBuilder()
		upstream.collect { token ->
			content.append(token)
			emit(token)
		}
		onComplete(content.toString())
	}.flowOn(Dispatchers.IO)

	private fun mapHttpsError(error: FirebaseFunctionsException): ChatError {
		if (error.message == "Error retrieving context") return ChatError.Unauthorized
		return when (error.code) {
			FirebaseFunctionsException.Code.UNAUTHENTICATED -> ChatError.Unauthorized
			FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ChatError.OutOfCredits
			FirebaseFunctionsException.Code.INVALID_ARGUMENT -> ChatError.Rejected(error.message)
			else -> ChatError.ServiceUnavailable
		}
	}

	private fun readAttachmentBytes(attachment: ChatAttachment): ByteArray? =
		try {
			context.contentResolver.openInputStream(attachment.uri.toUri())?.use { it.readBytes() }
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			null
		}
}
