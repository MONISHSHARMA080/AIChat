package com.agentic.aichat.data.ai

fun ModelDto.toModel(): AiChatModel = AiChatModel(
	id = id,
	name = name,
	description = description.orEmpty(),
)
