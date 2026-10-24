package com.agentic.aichat.data.chat.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity

@Database(
	entities = [ChatEntity::class, MessageEntity::class],
	version = 1,
	exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
	abstract fun chatDao(): ChatDao
	abstract fun messageDao(): MessageDao

	companion object {
		const val NAME = "ai_chat.db"
	}
}
