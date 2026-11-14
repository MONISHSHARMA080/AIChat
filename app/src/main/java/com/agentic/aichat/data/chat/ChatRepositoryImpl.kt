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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

	private val syncedChatId = MutableStateFlow<String?>(null)

	override fun observeSyncedChatId(): StateFlow<String?> = syncedChatId.asStateFlow()

	override fun getAllChats(): Flow<List<ChatEntity>> = chatDao.observeAll()

	override fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeById(chatId)

	override fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
		messageDao.observeForChat(chatId)

	override suspend fun updateChatModel(chatId: String, modelId: String) = chatDao.updateModel(chatId, modelId)

	override suspend fun sendMessage(
		chatId: String?,
		messageId: String,
		text: String,
		modelId: String,
		reasoning: String?,
		attachments: List<ChatAttachment>,
	): Outcome<Flow<String>, ChatError> = withContext(Dispatchers.IO) {
		val now = System.currentTimeMillis()
		val resolvedTitle = deriveTitle(text)
		val userMessage = MessageEntity(
			id = messageId,
			chatId = chatId.orEmpty(),
			role = MessageRole.USER,
			content = text,
			createdAt = now,
			status = MessageStatus.DONE,
			attachments = attachments,
		)
		val payload = when (val built = buildPayload(text, attachments, modelId, reasoning, chatId, resolvedTitle)) {
			is Outcome.Ok -> built.value
			is Outcome.Err -> return@withContext Outcome.Err(ChatError.AttachmentUnreadable(built.error))
		}
		val localChatId = chatId
		if (localChatId != null) {
			val preview = text.ifBlank { attachments.firstOrNull()?.name.orEmpty() }
			database.withWriteTransaction {
				messageDao.insert(userMessage)
				chatDao.updateLastMessage(localChatId, preview, now)
			}
		}
		val replyId = UUID.randomUUID().toString()
		Outcome.Ok(
			writeReplyFlow(streamChat(payload)) { serverChatId, content ->
				val effectiveServerId = serverChatId ?: localChatId ?: UUID.randomUUID().toString()
				adoptServerChatId(localChatId, effectiveServerId, resolvedTitle, modelId, userMessage, replyId, content, now)
			},
		)
	}

	override suspend fun retryMessage(
		chatId: String,
		messageId: String,
		modelId: String,
		reasoning: String?,
	): Outcome<Flow<String>, ChatError> = withContext(Dispatchers.IO) {
		val retriedMessage = messageDao.getForChat(chatId).find { it.id == messageId }
		val chatTitle = chatDao.getById(chatId)?.title
		val payload = when (val built = buildPayload(retriedMessage?.content.orEmpty(), retriedMessage?.attachments ?: emptyList(), modelId, reasoning, chatId, chatTitle)) {
			is Outcome.Ok -> built.value
			is Outcome.Err -> return@withContext Outcome.Err(ChatError.AttachmentUnreadable(built.error))
		}
		val existingTitle = chatTitle ?: "New chat"
		Outcome.Ok(
			writeReplyFlow(streamChat(payload)) { serverChatId, content ->
				adoptRetryResult(chatId, serverChatId, messageId, existingTitle, modelId, content)
			},
		)
	}

	private suspend fun adoptServerChatId(
		localChatId: String?,
		serverChatId: String,
		title: String,
		modelId: String,
		userMessage: MessageEntity,
		replyId: String,
		assistantContent: String,
		now: Long,
	) {
		database.withWriteTransaction {
			when {
				localChatId == null -> {
					if (chatDao.getById(serverChatId) == null) {
						chatDao.insert(
							ChatEntity(
								id = serverChatId,
								title = title,
								modelId = modelId,
								createdAt = now,
								updatedAt = now,
								lastMessageText = assistantContent,
								lastMessageAt = now,
							),
						)
					}
					messageDao.insert(userMessage.copy(chatId = serverChatId))
					messageDao.insert(assistantMessageOf(replyId, serverChatId, assistantContent, now))
				}
				localChatId != serverChatId -> {
					migrateChatId(localChatId, serverChatId, title, modelId, assistantContent, now)
					messageDao.insert(assistantMessageOf(replyId, serverChatId, assistantContent, now))
				}
				else -> {
					messageDao.insert(assistantMessageOf(replyId, serverChatId, assistantContent, now))
					chatDao.updateLastMessage(serverChatId, assistantContent, now)
				}
			}
		}
		syncedChatId.value = serverChatId
	}

	private suspend fun adoptRetryResult(
		localChatId: String,
		serverChatId: String?,
		messageId: String,
		title: String,
		modelId: String,
		assistantContent: String,
	) {
		val now = System.currentTimeMillis()
		val targetId = serverChatId ?: localChatId
		database.withWriteTransaction {
			if (targetId != localChatId) {
				migrateChatId(localChatId, targetId, title, modelId, assistantContent, now)
			}
			messageDao.updateContent(messageId, assistantContent)
			messageDao.updateStatus(messageId, MessageStatus.DONE)
			chatDao.updateLastMessage(targetId, assistantContent, now)
		}
		syncedChatId.value = targetId
	}

	private suspend fun migrateChatId(
		localChatId: String,
		serverChatId: String,
		title: String,
		modelId: String,
		assistantContent: String,
		now: Long,
	) {
		if (chatDao.getById(serverChatId) == null) {
			val oldChat = chatDao.getById(localChatId)
			chatDao.insert(
				ChatEntity(
					id = serverChatId,
					title = oldChat?.title ?: title,
					modelId = oldChat?.modelId ?: modelId,
					createdAt = oldChat?.createdAt ?: now,
					updatedAt = now,
					lastMessageText = assistantContent,
					lastMessageAt = now,
				),
			)
		}
		messageDao.moveToChat(localChatId, serverChatId)
		chatDao.deleteById(localChatId)
	}

	private fun assistantMessageOf(
		replyId: String,
		chatId: String,
		assistantContent: String,
		now: Long,
	): MessageEntity = MessageEntity(
		id = replyId,
		chatId = chatId,
		role = MessageRole.ASSISTANT,
		content = assistantContent,
		createdAt = now,
		status = MessageStatus.DONE,
	)

	private fun deriveTitle(text: String): String =
		text.lines().firstOrNull().orEmpty().take(48).ifBlank { "New chat" }

	private fun buildPayload(
		text: String,
		attachments: List<ChatAttachment>,
		modelId: String,
		reasoning: String?,
		chatId: String?,
		title: String?,
	): Outcome<Map<String, Any?>, String> {
		val files = attachments.map { attachment ->
			val bytes = readAttachmentBytes(attachment) ?: return Outcome.Err(attachment.name)
			mapOf(
				"name" to attachment.name,
				"mimeType" to attachment.mimeType,
				"data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
			)
		}
		val payload = mutableMapOf<String, Any?>(
			"model" to modelId,
			"chatId" to chatId,
			"title" to title,
			"message" to mapOf(
				"content" to text,
				"attachments" to files,
			),
		)
		reasoning?.ifBlank { null }?.let { payload["reasoning"] = it }
		return Outcome.Ok(payload)
	}

	private fun streamChat(payload: Map<String, Any?>): Flow<StreamEvent> = functions.getHttpsCallable("chat")
		.stream(payload)
		.asFlow()
		.mapNotNull { response ->
			when (response) {
				is StreamResponse.Message -> parseReplyText(response.message.data)?.let(StreamEvent::Token)
				is StreamResponse.Result -> StreamEvent.Done(parseResultChatId(response.result.data))
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

	private fun parseResultChatId(raw: Any?): String? {
		if (raw !is Map<*, *>) return null
		return raw["chatId"]?.toString()?.ifEmpty { null }
	}

	private fun writeReplyFlow(
		upstream: Flow<StreamEvent>,
		onComplete: suspend (String?, String) -> Unit,
	): Flow<String> = flow {
		val content = StringBuilder()
		var serverChatId: String? = null
		upstream.collect { event ->
			when (event) {
				is StreamEvent.Token -> {
					content.append(event.text)
					emit(event.text)
				}
				is StreamEvent.Done -> serverChatId = event.chatId
			}
		}
		onComplete(serverChatId, content.toString())
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

	private sealed interface StreamEvent {
		data class Token(val text: String) : StreamEvent
		data class Done(val chatId: String?) : StreamEvent
	}
}
