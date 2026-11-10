package com.agentic.aichat.di

import com.agentic.aichat.BuildConfig
import com.agentic.aichat.util.log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
	@Provides
	@Singleton
	fun provideFirebaseAuth(): FirebaseAuth {
		val auth = FirebaseAuth.getInstance()
		if (BuildConfig.DEBUG) {
			auth.useEmulator(BuildConfig.FIREBASE_AUTH_EMULATOR_HOST, BuildConfig.FIREBASE_AUTH_EMULATOR_PORT)
			log("auth emulator: ${BuildConfig.FIREBASE_AUTH_EMULATOR_HOST}:${BuildConfig.FIREBASE_AUTH_EMULATOR_PORT}")
		} else {
			log("auth emulator: disabled (release build)")
		}
		return auth
	}

	@Provides
	@Singleton
	fun provideFirebaseFunctions(): FirebaseFunctions {
		val functions = FirebaseFunctions.getInstance("us-central1")
		if (BuildConfig.DEBUG) {
			functions.useEmulator(BuildConfig.FUNCTIONS_EMULATOR_HOST, BuildConfig.FUNCTIONS_EMULATOR_PORT)
			log("functions emulator: ${BuildConfig.FUNCTIONS_EMULATOR_HOST}:${BuildConfig.FUNCTIONS_EMULATOR_PORT}")
		} else {
			log("functions emulator: disabled (release build)")
		}
		return functions
	}
}
