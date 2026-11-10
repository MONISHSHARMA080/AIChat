package com.agentic.aichat.data.ai.db

import androidx.room3.ColumnTypeConverter
import com.agentic.aichat.data.ai.PricingDto
import com.agentic.aichat.data.ai.ReasoningDto
import kotlinx.serialization.json.Json

class ModelConverters {
	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	@ColumnTypeConverter
	fun fromReasoning(value: String): ReasoningDto = json.decodeFromString<ReasoningDto>(value)

	@ColumnTypeConverter
	fun toReasoning(reasoning: ReasoningDto): String = json.encodeToString(reasoning)

	@ColumnTypeConverter
	fun fromPricing(value: String?): PricingDto? =
		value?.let { json.decodeFromString<PricingDto>(it) }

	@ColumnTypeConverter
	fun toPricing(pricing: PricingDto?): String? =
		pricing?.let { json.encodeToString(it) }
}
