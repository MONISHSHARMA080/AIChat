package com.agentic.aichat.data.ai

import com.agentic.aichat.model.AiChatModel

fun ModelDto.toModel(): AiChatModel = AiChatModel(
	id = id,
	name = name,
	description = description.orEmpty(),
)
