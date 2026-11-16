package com.agentic.aichat.data.chat

import android.content.Context
import android.net.Uri
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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.StreamResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
	private val filesDir: File by lazy { File(context.filesDir, FILES_DIR_NAME).apply { mkdirs() } }

	override fun observeChats(): Flow<List<ChatEntity>> = chatDao.observeAll()

	override fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeById(chatId)

	override fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
		messageDao.observeForChat(chatId)

	override suspend fun updateChatModel(chatId: String, modelId: String) =
		chatDao.updateModel(chatId, modelId)

	override suspend fun resolveAttachment(attachment: ChatAttachment): Uri = withContext(Dispatchers.IO) {
		val remoteId = attachment.remoteId
		if (remoteId == null) {
			if (attachment.uri.isEmpty()) throw ChatException(ChatError.AttachmentUnreadable(attachment.name))
			return@withContext attachment.uri.toUri()
		}
		val file = chatFile(remoteId) ?: throw ChatException(ChatError.AttachmentUnreadable(attachment.name))
		if (!file.exists()) downloadFile(remoteId, file, attachment.name)
		file.toUri()
	}

	override fun sendMessage(
			chatId: String?,
			text: String,
			modelId: String,
			reasoning: String?,
			attachments: List<ChatAttachment>,
	): Flow<SendMessageEvent> = flow {
		val encodedAttachments = attachments.map { attachment ->
			val bytes = try {
				context.contentResolver.openInputStream(attachment.uri.toUri())?.use { it.readBytes() }
			} catch (error: IOException) {
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
			put("message", mapOf("content" to text, "attachments" to encodedAttachments))
			if (!reasoning.isNullOrBlank()) put("reasoning", reasoning)
		}

		val replyBuilder = StringBuilder()
		val streamedFiles = mutableListOf<ChatAttachment>()
		var savedTurn: Map<*, *>? = null
		var committed = false
		try {
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
							val data = response.message.data as? Map<*, *> ?: return@collect
							when (data["type"]) {
								"text" -> {
									val chunk = data["text"] as? String
									if (!chunk.isNullOrEmpty()) {
										replyBuilder.append(chunk)
										emit(SendMessageEvent.ReplyChunk(chunk))
									}
								}
								"file" -> {
									val attachment = writeStreamedFile(data)
									if (attachment != null) {
										streamedFiles += attachment
										emit(SendMessageEvent.ReplyFile(attachment))
									}
								}
							}
						}
						is StreamResponse.Result ->
							savedTurn = (response.result.data as? Map<*, *>)?.get("savedTurn") as? Map<*, *>
					}
				}

			val turn = savedTurn ?: throw ChatException(ChatError.NotSaved)
			val chat = turn["chat"] as Map<*, *>
			val userMessage = turn["userMessage"] as Map<*, *>
			val assistantMessage = turn["assistantMessage"] as Map<*, *>
			val savedChatId = chat["id"] as String
			val userMessageId = userMessage["id"] as String
			val userCreatedAt = (userMessage["createdAt"] as Number).toLong()
			val assistantCreatedAt = (assistantMessage["createdAt"] as Number).toLong()
			val reply = replyBuilder.toString()
			val replyFiles = (assistantMessage["files"] as? List<*>).orEmpty()
				.mapNotNull { (it as? Map<*, *>)?.toAttachment() }
			val preview = reply.take(PREVIEW_LENGTH).ifEmpty { if (replyFiles.isNotEmpty()) FILE_PREVIEW else "" }

			database.withWriteTransaction {
				if (chatDao.getById(savedChatId) == null) {
					chatDao.insert(
						ChatEntity(
							id = savedChatId,
							title = chat["title"] as String,
							modelId = modelId,
							createdAt = userCreatedAt,
							updatedAt = assistantCreatedAt,
							lastMessageText = preview,
							lastMessageAt = assistantCreatedAt,
						),
					)
				} else {
					chatDao.updateLastMessage(savedChatId, preview, assistantCreatedAt)
				}
				messageDao.insert(
					MessageEntity(
						id = userMessageId,
						chatId = savedChatId,
						role = MessageRole.USER,
						content = text,
						createdAt = userCreatedAt,
						status = MessageStatus.DONE,
						attachments = attachments,
					),
				)
				messageDao.insert(
					MessageEntity(
						id = assistantMessage["id"] as String,
						chatId = savedChatId,
						role = MessageRole.ASSISTANT,
						content = reply,
						createdAt = assistantCreatedAt,
						status = MessageStatus.DONE,
						attachments = replyFiles,
					),
				)
			}
			committed = true
			emit(SendMessageEvent.Saved(chatId = savedChatId, userMessageId = userMessageId))
		} finally {
			if (!committed) streamedFiles.forEach { attachment ->
				attachment.remoteId?.let(::chatFile)?.delete()
			}
		}
	}.flowOn(Dispatchers.IO)

	private fun chatFile(id: String): File? = if (isValidFileId(id)) File(filesDir, id) else null

	private fun writeFile(target: File, encoded: String) {
		val temp = File(filesDir, "${target.name}.tmp")
		temp.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
		if (!temp.renameTo(target)) {
			if (target.exists()) target.delete()
			if (!temp.renameTo(target)) {
				temp.delete()
				throw IOException("could not store ${target.name}")
			}
		}
	}

	private fun writeStreamedFile(data: Map<*, *>): ChatAttachment? {
		val id = data["id"] as? String ?: return null
		val encoded = data["data"] as? String ?: return null
		val file = chatFile(id) ?: return null
		return try {
			writeFile(file, encoded)
			data.toAttachment()
		} catch (error: Exception) {
			log("streamed file write failed: $error")
			file.delete()
			null
		}
	}

	private fun downloadFile(remoteId: String, target: File, displayName: String) {
		val data = try {
			val result = Tasks.await(functions.getHttpsCallable("getFile").call(mapOf("fileId" to remoteId)))
			result.data as? Map<*, *>
		} catch (error: ExecutionException) {
			val functionsError = (error.cause as? FirebaseFunctionsException) ?: error
			log("getFile failed: ${functionsError.message}")
			throw ChatException(
				when ((functionsError as? FirebaseFunctionsException)?.code) {
					FirebaseFunctionsException.Code.UNAUTHENTICATED -> ChatError.Unauthorized
					FirebaseFunctionsException.Code.NOT_FOUND -> ChatError.AttachmentUnreadable(displayName)
					else -> ChatError.ServiceUnavailable
				},
			)
		}
		val encoded = data?.get("data") as? String ?: throw ChatException(ChatError.ServiceUnavailable)
		try {
			writeFile(target, encoded)
		} catch (error: Exception) {
			log("getFile write failed: $error")
			target.delete()
			throw ChatException(ChatError.ServiceUnavailable)
		}
	}

	private fun Map<*, *>.toAttachment(): ChatAttachment? {
		val id = this["id"] as? String ?: return null
		val name = this["name"] as? String ?: return null
		val mimeType = this["mimeType"] as? String ?: return null
		val local = chatFile(id)?.takeIf { it.exists() }
		return ChatAttachment(
			uri = local?.toUri()?.toString().orEmpty(),
			name = name,
			mimeType = mimeType,
			remoteId = id,
			size = (this["size"] as? Number)?.toLong(),
		)
	}

	private fun isValidFileId(id: String): Boolean {
		if (id.length !in 1..MAX_ID_LENGTH) return false
		return id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
	}

	private companion object {
		const val FILES_DIR_NAME = "chat_files"
		const val PREVIEW_LENGTH = 200
		const val FILE_PREVIEW = "[file]"
		const val MAX_ID_LENGTH = 64
	}
}
