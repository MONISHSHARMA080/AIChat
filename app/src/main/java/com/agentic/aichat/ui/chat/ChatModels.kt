package com.agentic.aichat.ui.chat

import androidx.compose.runtime.Immutable

@Immutable
data class AiChatModel(
	val id: String,
	val name: String,
	val description: String,
)

enum class ReasoningEffort(
	val label: String,
	val description: String,
) {
	Less(
		label = "Less",
		description = "Fastest answers for simple questions.",
	),
	Medium(
		label = "Medium",
		description = "Balanced speed and depth for everyday chats.",
	),
	Hard(
		label = "Hard",
		description = "Thinks longer for tricky problems and code.",
	),
	Ultra(
		label = "Ultra",
		description = "Maximum reasoning for the hardest tasks.",
	),
}

val AvailableModels: List<AiChatModel> = listOf(
	AiChatModel(
		id = "spark-1",
		name = "Spark 1",
		description = "Flagship model for everyday chats.",
	),
	AiChatModel(
		id = "spark-1-mini",
		name = "Spark 1 Mini",
		description = "Faster, lighter model for quick questions.",
	),
	AiChatModel(
		id = "spark-reasoner",
		name = "Spark Reasoner",
		description = "Deep reasoning for math and code.",
	),
)

fun modelById(id: String): AiChatModel =
	AvailableModels.firstOrNull { it.id == id }
		?: AvailableModels.firstOrNull()
		?: error("AvailableModels must not be empty")
