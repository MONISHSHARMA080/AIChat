package com.agentic.aichat.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Onboarding : NavKey

@Serializable
data object SignIn : NavKey

@Serializable
data object ChatHome : NavKey

@Serializable
data object ChatList : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object Paywall : NavKey