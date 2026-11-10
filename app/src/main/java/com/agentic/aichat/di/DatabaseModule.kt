package com.agentic.aichat.di

import android.content.Context
import androidx.room3.Room
import com.agentic.aichat.data.ai.db.ModelDao
import com.agentic.aichat.data.chat.ChatRepository
import com.agentic.aichat.data.chat.ChatRepositoryImpl
import com.agentic.aichat.data.chat.db.ChatDao
import com.agentic.aichat.data.chat.db.ChatDatabase
import com.agentic.aichat.data.chat.db.MessageDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

	@Binds
	abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

	companion object {
		@Provides
		@Singleton
		fun provideChatDatabase(
			@ApplicationContext context: Context,
		): ChatDatabase = Room.databaseBuilder(context, ChatDatabase::class.java, ChatDatabase.NAME).build()

		@Provides
		@Singleton
		fun provideModelDao(database: ChatDatabase): ModelDao = database.modelDao()

		@Provides
		@Singleton
		fun provideChatDao(database: ChatDatabase): ChatDao = database.chatDao()

		@Provides
		@Singleton
		fun provideMessageDao(database: ChatDatabase): MessageDao = database.messageDao()
	}
}
