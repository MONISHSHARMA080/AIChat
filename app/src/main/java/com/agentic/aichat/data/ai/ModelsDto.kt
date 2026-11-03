package com.agentic.aichat.data.ai

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class ListModelsResponseDto(
	val models: List<ModelDto> = emptyList(),
)

@Serializable
@Entity(tableName = "models")
data class ModelDto(
	@PrimaryKey val id: String,
	val name: String,
	val description: String? = null,
	val pricing: PricingDto? = null,
	val reasoning: ReasoningDto = ReasoningDto(),
	val provider: String = "",
	val logoUrl: String? = null,
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
