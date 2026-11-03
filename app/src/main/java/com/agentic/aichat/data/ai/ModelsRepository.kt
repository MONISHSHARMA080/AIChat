package com.agentic.aichat.data.ai

import com.agentic.aichat.util.log
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelsRepository {
	suspend fun getModels(): Result<List<ModelDto>>
}

class ModelsRepositoryImpl @Inject constructor(
	private val api: BackendApi,
) : ModelsRepository {
	override suspend fun getModels(): Result<List<ModelDto>> = try {
		withContext(Dispatchers.IO) {
			log("getting the models")
			Result.success(api.listModels().models)
		}
	} catch (e: Exception) {
		Result.failure(e)
	}
}
