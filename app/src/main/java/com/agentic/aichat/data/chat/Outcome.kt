package com.agentic.aichat.data.chat

sealed interface Outcome<out V, out E> {
	data class Ok<V>(val value: V) : Outcome<V, Nothing>
	data class Err<E>(val error: E) : Outcome<Nothing, E>
}
