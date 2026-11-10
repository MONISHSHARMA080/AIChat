package com.agentic.aichat.data.ai

import com.agentic.aichat.data.ai.db.ModelDao
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface ModelsRepository {
	fun observeModels(): Flow<List<ModelDto>>
	suspend fun refreshModels(): Result<Unit>
}

class ModelsRepositoryImpl @Inject constructor(
	private val api: BackendApi,
	private val modelDao: ModelDao,
) : ModelsRepository {
	override fun observeModels(): Flow<List<ModelDto>> = modelDao.observeAll()

	override suspend fun refreshModels(): Result<Unit> = try {
		withContext(Dispatchers.IO) {
			modelDao.replaceAll(api.listModels().models)
			Result.success(Unit)
		}
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		Result.failure(e)
	}
}
