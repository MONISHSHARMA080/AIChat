package com.agentic.aichat.data.chat.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

	@Query("SELECT * FROM chats ORDER BY updatedAt DESC")
	fun observeAll(): Flow<List<ChatEntity>>

	@Query("SELECT * FROM chats WHERE id = :chatId")
	suspend fun getById(chatId: String): ChatEntity?

	@Query("SELECT * FROM chats WHERE id = :chatId")
	fun observeById(chatId: String): Flow<ChatEntity?>

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(chat: ChatEntity)

	@Update
	suspend fun update(chat: ChatEntity)

	@Query("UPDATE chats SET title = :title WHERE id = :chatId")
	suspend fun rename(chatId: String, title: String)

	@Query("UPDATE chats SET modelId = :modelId WHERE id = :chatId")
	suspend fun updateModel(chatId: String, modelId: String)

	@Query(
		"UPDATE chats SET lastMessageText = :text, lastMessageAt = :at, updatedAt = :at " +
			"WHERE id = :chatId"
	)
	suspend fun updateLastMessage(chatId: String, text: String?, at: Long)

	@Query("DELETE FROM chats WHERE id = :chatId")
	suspend fun deleteById(chatId: String)
}
