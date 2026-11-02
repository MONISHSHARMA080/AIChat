package com.agentic.aichat.data.ai

import retrofit2.http.GET

interface BackendApi {
	@GET("listModels")
	suspend fun listModels(): ListModelsResponseDto
}
