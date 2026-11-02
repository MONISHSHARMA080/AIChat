package com.agentic.aichat.data.ai

import com.agentic.aichat.log
import com.agentic.aichat.model.AiChatModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelsRepository {
	suspend fun getModels(): Result<List<AiChatModel>>
}

class ModelsRepositoryImpl @Inject constructor(
		private val api: BackendApi,
) : ModelsRepository {
	override suspend fun getModels(): Result<List<AiChatModel>> = try {
		withContext(Dispatchers.IO) {
			log("getting the models")
			Result.success(api.listModels().models.map { it.toModel() })
		}
	} catch (e: Exception) {
		Result.failure(e)
	}
}
