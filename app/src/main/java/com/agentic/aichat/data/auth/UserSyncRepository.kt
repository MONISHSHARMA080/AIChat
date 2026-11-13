package com.agentic.aichat.data.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSyncRepository @Inject constructor(
	private val functions: FirebaseFunctions,
) {
	suspend fun syncUser() {
		withContext(Dispatchers.IO) {
			Tasks.await(functions.getHttpsCallable("syncUser").call())
		}
	}
}
