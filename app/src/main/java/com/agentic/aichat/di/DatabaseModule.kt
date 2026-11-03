package com.agentic.aichat.di

import android.content.Context
import androidx.room3.Room
import com.agentic.aichat.data.ai.db.ModelDao
import com.agentic.aichat.data.chat.db.ChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

	@Provides
	@Singleton
	fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase =
		Room.databaseBuilder(context, ChatDatabase::class.java, ChatDatabase.NAME).build()

	@Provides
	fun provideModelDao(database: ChatDatabase): ModelDao = database.modelDao()
}
