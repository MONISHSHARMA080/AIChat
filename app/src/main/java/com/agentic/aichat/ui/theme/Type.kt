@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.agentic.aichat.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// M3 Expressive typography. The scale defines the core text tiers used by the
// app, plus *Emphasized* variants (M3 Expressive's emphasized style tier: same
// size tier but heavier weight and tighter letter spacing). Remaining styles
// for which no explicit TextStyle is supplied fall back to Material's
// expressive token defaults.
val Typography = Typography(
	displayLarge = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Normal,
		fontSize = 64.sp,
		lineHeight = 76.sp,
		letterSpacing = (-0.25).sp
	),
	displayLargeEmphasized = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.SemiBold,
		fontSize = 64.sp,
		lineHeight = 76.sp,
		letterSpacing = (-0.5).sp
	),
	headlineSmall = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Normal,
		fontSize = 24.sp,
		lineHeight = 32.sp,
		letterSpacing = 0.sp
	),
	headlineSmallEmphasized = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.SemiBold,
		fontSize = 24.sp,
		lineHeight = 32.sp,
		letterSpacing = (-0.2).sp
	),
	titleLarge = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 22.sp,
		lineHeight = 28.sp,
		letterSpacing = 0.sp
	),
	titleMedium = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.15.sp
	),
	titleLargeEmphasized = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.SemiBold,
		fontSize = 22.sp,
		lineHeight = 28.sp,
		letterSpacing = (-0.1).sp
	),
	titleMediumEmphasized = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.SemiBold,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.1.sp
	),
	bodyLarge = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Normal,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.5.sp
	),
	bodyMedium = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Normal,
		fontSize = 14.sp,
		lineHeight = 20.sp,
		letterSpacing = 0.25.sp
	),
	bodySmall = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Normal,
		fontSize = 12.sp,
		lineHeight = 16.sp,
		letterSpacing = 0.4.sp
	),
	bodyLargeEmphasized = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 16.sp,
		lineHeight = 24.sp,
		letterSpacing = 0.4.sp
	),
	labelLarge = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 14.sp,
		lineHeight = 20.sp,
		letterSpacing = 0.1.sp
	),
	labelMedium = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 12.sp,
		lineHeight = 16.sp,
		letterSpacing = 0.5.sp
	),
	labelSmall = TextStyle(
		fontFamily = FontFamily.Default,
		fontWeight = FontWeight.Medium,
		fontSize = 11.sp,
		lineHeight = 16.sp,
		letterSpacing = 0.5.sp
	)
)
