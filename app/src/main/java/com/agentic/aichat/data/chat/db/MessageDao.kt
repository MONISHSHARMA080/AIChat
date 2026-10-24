package com.agentic.aichat.data.chat.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.agentic.aichat.data.chat.db.entity.MessageEntity
import com.agentic.aichat.data.chat.db.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

	/** Messages of one chat in display order; re-emits as rows change (e.g. streaming). */
	@Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
	fun observeForChat(chatId: String): Flow<List<MessageEntity>>

	@Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
	suspend fun getForChat(chatId: String): List<MessageEntity>

	@Query("SELECT * FROM messages WHERE id = :messageId")
	suspend fun getById(messageId: String): MessageEntity?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(message: MessageEntity)

	@Update
	suspend fun update(message: MessageEntity)

	/** Appends one streamed token chunk to the message content in place. */
	@Query("UPDATE messages SET content = content || :delta WHERE id = :messageId")
	suspend fun appendContent(messageId: String, delta: String)

	@Query("UPDATE messages SET content = :content WHERE id = :messageId")
	suspend fun updateContent(messageId: String, content: String)

	@Query("UPDATE messages SET status = :status WHERE id = :messageId")
	suspend fun updateStatus(messageId: String, status: MessageStatus)

	@Query("DELETE FROM messages WHERE id = :messageId")
	suspend fun deleteById(messageId: String)

	@Query("DELETE FROM messages WHERE chatId = :chatId")
	suspend fun deleteForChat(chatId: String)
}
