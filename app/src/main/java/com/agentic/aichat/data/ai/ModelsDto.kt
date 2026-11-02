package com.agentic.aichat.data.ai

import kotlinx.serialization.Serializable

@Serializable
data class ListModelsResponseDto(
	val models: List<ModelDto> = emptyList(),
)

@Serializable
data class ModelDto(
	val id: String,
	val name: String,
	val description: String? = null,
	val pricing: PricingDto? = null,
	val reasoning: ReasoningDto = ReasoningDto(),
)

@Serializable
data class PricingDto(
	val input: Double,
	val output: Double,
)

@Serializable
data class ReasoningDto(
	val levels: List<String> = emptyList(),
	val toggle: Boolean = false,
	val budgetTokens: Boolean = false,
)
