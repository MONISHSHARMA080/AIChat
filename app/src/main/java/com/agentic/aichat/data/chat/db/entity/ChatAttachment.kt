package com.agentic.aichat.data.chat.db.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatAttachment(
	val uri: String = "",
	val name: String,
	val mimeType: String,
	@SerialName("id") val remoteId: String? = null,
	val size: Long? = null,
)
