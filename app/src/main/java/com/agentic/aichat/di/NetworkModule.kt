package com.agentic.aichat.di

import com.agentic.aichat.BuildConfig
import com.agentic.aichat.data.ai.BackendApi
import com.agentic.aichat.data.ai.ModelsRepository
import com.agentic.aichat.data.ai.ModelsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
	@Provides
	@Singleton
	fun provideJson(): Json = Json { ignoreUnknownKeys = true }

	@Provides
	@Singleton
	fun provideOkHttpClient(): OkHttpClient {
		val builder = OkHttpClient.Builder()
			.connectTimeout(15, TimeUnit.SECONDS)
			.readTimeout(15, TimeUnit.SECONDS)
		if (BuildConfig.DEBUG) {
			builder.addInterceptor(
				HttpLoggingInterceptor().apply {
					level = HttpLoggingInterceptor.Level.BODY
				},
			)
		}
		return builder.build()
	}

	@OptIn(ExperimentalSerializationApi::class)
	@Provides
	@Singleton
	fun provideRetrofit(
		client: OkHttpClient,
		json: Json,
	): Retrofit = Retrofit.Builder()
		.baseUrl(BuildConfig.FUNCTIONS_BASE_URL)
		.client(client)
		.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
		.build()

	@Provides
	@Singleton
	fun provideFunctionsApi(retrofit: Retrofit): BackendApi =
		retrofit.create(BackendApi::class.java)

	@Provides
	@Singleton
	fun provideModelsRepository(impl: ModelsRepositoryImpl): ModelsRepository = impl
}
