package com.agentic.aichat.data.chat.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.agentic.aichat.data.ai.ModelDto
import com.agentic.aichat.data.ai.db.ModelDao
import com.agentic.aichat.data.chat.db.entity.ChatEntity
import com.agentic.aichat.data.chat.db.entity.MessageEntity

import androidx.room3.ColumnTypeConverters
import com.agentic.aichat.data.ai.db.ModelConverters

@Database(
	entities = [ChatEntity::class, MessageEntity::class, ModelDto::class],
	version = 1,
	exportSchema = false,
)
@ColumnTypeConverters(ModelConverters::class, ChatConverters::class)
abstract class ChatDatabase : RoomDatabase() {
	abstract fun chatDao(): ChatDao
	abstract fun messageDao(): MessageDao
	abstract fun modelDao(): ModelDao

	companion object {
		const val NAME = "ai_chat.db"
	}
}
