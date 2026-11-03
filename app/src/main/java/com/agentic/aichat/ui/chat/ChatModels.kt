package com.agentic.aichat.ui.chat

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
)

fun reasoningEffortFor(level: String): ReasoningEffort? =
	ReasoningEffort.entries.firstOrNull { it.label.equals(level.trim(), ignoreCase = true) }
