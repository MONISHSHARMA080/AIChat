package com.agentic.aichat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class PickerScreen {
	Models,
	Effort,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelPickerSheet(
	selectedModel: AiChatModel,
	selectedEffort: ReasoningEffort,
	onModelSelected: (AiChatModel) -> Unit,
	onEffortSelected: (ReasoningEffort) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	models: List<AiChatModel> = AvailableModels,
) {
	val scheme = MaterialTheme.colorScheme
	var screen by remember { mutableStateOf(PickerScreen.Models) }
	val sheetState = rememberBottomSheetState(
		initialValue = SheetValue.Hidden,
		enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
	)
	val scope = rememberCoroutineScope()

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		modifier = modifier,
		sheetState = sheetState,
		shape = MaterialTheme.shapes.extraLarge,
		containerColor = scheme.surfaceContainerLow,
	) {
		AnimatedContent(
			targetState = screen,
			label = "picker-screen",
		) { current ->
			when (current) {
				PickerScreen.Models -> ModelsScreen(
					models = models,
					selectedModel = selectedModel,
					selectedEffort = selectedEffort,
					onModelSelected = { model ->
						scope.launch {
							sheetState.hide()
							onModelSelected(model)
						}
					},
					onOpenEffort = { screen = PickerScreen.Effort },
				)
				PickerScreen.Effort -> EffortScreen(
					selectedEffort = selectedEffort,
					onEffortSelected = onEffortSelected,
					onBack = { screen = PickerScreen.Models },
				)
			}
		}
	}
}

@Composable
private fun SheetScreenColumn(
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		content()
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelsScreen(
	models: List<AiChatModel>,
	selectedModel: AiChatModel,
	selectedEffort: ReasoningEffort,
	onModelSelected: (AiChatModel) -> Unit,
	onOpenEffort: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	SheetScreenColumn(modifier = modifier) {
		Text(
			text = "Choose model",
			style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
			color = scheme.onSurface,
			modifier = Modifier.padding(horizontal = 8.dp),
		)
		SectionLabel(text = "Model")
		models.forEach { model ->
			ModelRow(
				model = model,
				selected = model.id == selectedModel.id,
				onClick = { onModelSelected(model) },
			)
		}
		Spacer(modifier = Modifier.height(8.dp))
		SectionLabel(text = "Reasoning")
		EffortRow(
			selectedEffort = selectedEffort,
			onClick = onOpenEffort,
		)
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelRow(
	model: AiChatModel,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	val container = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
	val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
	Surface(
		shape = MaterialTheme.shapes.largeIncreased,
		color = container,
		contentColor = content,
		modifier = modifier.fillMaxWidth(),
		onClick = onClick,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = model.name,
					style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
				)
				Text(
					text = model.description,
					style = MaterialTheme.typography.bodySmall,
					color = content.copy(alpha = 0.75f),
				)
			}
			if (selected) {
				Icon(
					imageVector = Icons.Filled.Check,
					contentDescription = "Selected",
				)
			}
		}
	}
}

@Composable
private fun EffortPill(content: @Composable () -> Unit) {
	val scheme = MaterialTheme.colorScheme
	Surface(
		shape = CircleShape,
		color = scheme.onSecondaryContainer.copy(alpha = 0.12f),
		contentColor = scheme.onSecondaryContainer,
	) {
		content()
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EffortRow(
	selectedEffort: ReasoningEffort,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	Surface(
		shape = MaterialTheme.shapes.large,
		color = scheme.secondaryContainer,
		contentColor = scheme.onSecondaryContainer,
		modifier = modifier.fillMaxWidth(),
		onClick = onClick,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Reasoning effort",
					style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
				)
				AnimatedContent(
					targetState = selectedEffort,
					label = "effort-description",
				) { effort ->
					Text(
						text = effort.description,
						style = MaterialTheme.typography.bodySmall,
						color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
					)
				}
			}
			EffortPill {
				AnimatedContent(
					targetState = selectedEffort,
					label = "effort-label",
				) { effort ->
					Text(
						text = effort.label,
						style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
					)
				}
			}
			EffortPill {
				Icon(
					imageVector = Icons.Filled.ChevronRight,
					contentDescription = null,
					modifier = Modifier.padding(6.dp).size(20.dp),
				)
			}
		}
	}
}

@Composable
private fun SectionLabel(
	text: String,
	modifier: Modifier = Modifier,
) {
	Text(
		text = text.uppercase(),
		style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.padding(start = 8.dp, top = 8.dp),
	)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EffortScreen(
	selectedEffort: ReasoningEffort,
	onEffortSelected: (ReasoningEffort) -> Unit,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	val motion = MaterialTheme.motionScheme
	val levels = ReasoningEffort.entries
	SheetScreenColumn(modifier = modifier) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back to models",
				)
			}
			Text(
				text = "Reasoning effort",
				style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
				color = scheme.onSurface,
			)
		}
		Surface(
			shape = MaterialTheme.shapes.largeIncreased,
			color = scheme.surfaceContainerHigh,
			contentColor = scheme.onSurface,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
				AnimatedContent(
					targetState = selectedEffort,
					transitionSpec = {
						(fadeIn(animationSpec = motion.defaultEffectsSpec()) +
							scaleIn(
								initialScale = 0.96f,
								animationSpec = motion.fastSpatialSpec(),
							)) togetherWith
							(fadeOut(animationSpec = motion.fastEffectsSpec()) +
								scaleOut(
									targetScale = 0.96f,
									animationSpec = motion.fastSpatialSpec(),
								)) using
							SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
					},
					label = "effort-hero",
				) { effort ->
					Column {
						Text(
							text = effort.label,
							style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
							color = scheme.primary,
						)
						Text(
							text = effort.description,
							style = MaterialTheme.typography.bodyMedium,
							color = scheme.onSurfaceVariant,
						)
					}
				}
				Spacer(modifier = Modifier.height(16.dp))
				val sliderState = rememberSliderState(
					value = levels.indexOf(selectedEffort).toFloat(),
					steps = levels.size - 2,
					trackRange = 0f..(levels.size - 1).toFloat(),
				)
				LaunchedEffect(selectedEffort) {
					sliderState.value = levels.indexOf(selectedEffort).toFloat()
				}
				Slider(
					state = sliderState,
					onValueChange = { raw ->
						sliderState.value = raw
						onEffortSelected(levels[raw.roundToInt().coerceIn(levels.indices)])
					},
				)
				Row(
					horizontalArrangement = Arrangement.SpaceBetween,
					modifier = Modifier.fillMaxWidth(),
				) {
					levels.forEach { level ->
						Text(
							text = level.label,
							style = MaterialTheme.typography.labelMedium.copy(
								fontWeight = if (level == selectedEffort) {
									FontWeight.Bold
								} else {
									FontWeight.Normal
								},
							),
							color = if (level == selectedEffort) {
								scheme.primary
							} else {
								scheme.onSurfaceVariant
							},
						)
					}
				}
			}
		}
	}
}
