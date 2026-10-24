package com.agentic.aichat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
	primary = PrimaryDark,
	onPrimary = OnPrimaryDark,
	primaryContainer = PrimaryContainerDark,
	onPrimaryContainer = OnPrimaryContainerDark,
	secondary = SecondaryDark,
	onSecondary = OnSecondaryDark,
	secondaryContainer = SecondaryContainerDark,
	onSecondaryContainer = OnSecondaryContainerDark,
	tertiary = TertiaryDark,
	onTertiary = TertiaryDark,
	tertiaryContainer = TertiaryContainerDark,
	onTertiaryContainer = OnTertiaryContainerDark,
	error = ErrorLight,
	onError = OnErrorLight,
	errorContainer = ErrorContainerLight,
	onErrorContainer = OnErrorContainerLight,
	background = BackgroundDark,
	onBackground = OnBackgroundDark,
	surface = SurfaceDark,
	onSurface = OnSurfaceDark,
	surfaceVariant = SurfaceVariantDark,
	onSurfaceVariant = OnSurfaceVariantDark,
	inversePrimary = InversePrimaryDark,
	inverseSurface = InverseSurfaceDark,
	inverseOnSurface = InverseOnSurfaceDark,
	outline = OutlineDark,
	outlineVariant = OutlineVariantDark,
	scrim = ScrimDark,
	surfaceBright = SurfaceBrightDark,
	surfaceDim = SurfaceDimDark,
	surfaceContainer = SurfaceContainerDark,
	surfaceContainerLow = SurfaceContainerLowDark,
	surfaceContainerLowest = SurfaceContainerLowestDark,
	surfaceContainerHigh = SurfaceContainerHighDark,
	surfaceContainerHighest = SurfaceContainerHighestDark,
	primaryFixed = PrimaryFixedLight,
	primaryFixedDim = PrimaryFixedDimLight,
	onPrimaryFixed = OnPrimaryFixedLight,
	onPrimaryFixedVariant = OnPrimaryFixedVariantLight,
	secondaryFixed = SecondaryFixedLight,
	secondaryFixedDim = SecondaryFixedDimLight,
	onSecondaryFixed = OnSecondaryFixedLight,
	onSecondaryFixedVariant = OnSecondaryFixedVariantLight,
	tertiaryFixed = TertiaryFixedLight,
	tertiaryFixedDim = TertiaryFixedDimLight,
	onTertiaryFixed = OnTertiaryFixedLight,
	onTertiaryFixedVariant = OnTertiaryFixedVariantLight
)

private val LightColorScheme = lightColorScheme(
	primary = PrimaryLight,
	onPrimary = OnPrimaryLight,
	primaryContainer = PrimaryContainerLight,
	onPrimaryContainer = OnPrimaryContainerLight,
	secondary = SecondaryLight,
	onSecondary = OnSecondaryLight,
	secondaryContainer = SecondaryContainerLight,
	onSecondaryContainer = OnSecondaryContainerLight,
	tertiary = TertiaryLight,
	onTertiary = OnTertiaryLight,
	tertiaryContainer = TertiaryContainerLight,
	onTertiaryContainer = OnTertiaryContainerLight,
	error = ErrorLight,
	onError = OnErrorLight,
	errorContainer = ErrorContainerLight,
	onErrorContainer = OnErrorContainerLight,
	background = BackgroundLight,
	onBackground = OnBackgroundLight,
	surface = SurfaceLight,
	onSurface = OnSurfaceLight,
	surfaceVariant = SurfaceVariantLight,
	onSurfaceVariant = OnSurfaceVariantLight,
	inversePrimary = InversePrimaryLight,
	inverseSurface = InverseSurfaceLight,
	inverseOnSurface = InverseOnSurfaceLight,
	outline = OutlineLight,
	outlineVariant = OutlineVariantLight,
	scrim = ScrimLight,
	surfaceBright = SurfaceBrightLight,
	surfaceDim = SurfaceDimLight,
	surfaceContainer = SurfaceContainerLight,
	surfaceContainerLow = SurfaceContainerLowLight,
	surfaceContainerLowest = SurfaceContainerLowestLight,
	surfaceContainerHigh = SurfaceContainerHighLight,
	surfaceContainerHighest = SurfaceContainerHighestLight,
	primaryFixed = PrimaryFixedLight,
	primaryFixedDim = PrimaryFixedDimLight,
	onPrimaryFixed = OnPrimaryFixedLight,
	onPrimaryFixedVariant = OnPrimaryFixedVariantLight,
	secondaryFixed = SecondaryFixedLight,
	secondaryFixedDim = SecondaryFixedDimLight,
	onSecondaryFixed = OnSecondaryFixedLight,
	onSecondaryFixedVariant = OnSecondaryFixedVariantLight,
	tertiaryFixed = TertiaryFixedLight,
	tertiaryFixedDim = TertiaryFixedDimLight,
	onTertiaryFixed = OnTertiaryFixedLight,
	onTertiaryFixedVariant = OnTertiaryFixedVariantLight
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AIChatTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	val context = LocalContext.current
	val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

	// Material 3 Expressive theming: passes the (dynamic or branded) color
	// scheme through with the expressive motion scheme and expressive
	// typography for spring-based, shape-reactive motion and emphasized type.
	MaterialExpressiveTheme(
		colorScheme = colorScheme,
		motionScheme = MotionScheme.expressive(),
		typography = Typography,
		content = content
	)
}
