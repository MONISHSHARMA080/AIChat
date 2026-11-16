package com.agentic.aichat.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.agentic.aichat.data.ai.ModelDto

private enum class PickerScreen {
	Models,
	Effort,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelPickerSheet(
	selectedModel: ModelDto,
	selectedLevel: String,
	onModelSelected: (ModelDto) -> Unit,
	onLevelSelected: (String) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier,
	models: List<ModelDto>,
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
					selectedLevel = selectedLevel,
					onModelSelected = { model ->
						scope.launch {
							sheetState.hide()
							onModelSelected(model)
						}
					},
					onOpenEffort = { screen = PickerScreen.Effort },
				)
				PickerScreen.Effort -> EffortScreen(
					levels = selectedModel.reasoning.levels,
					selectedLevel = selectedLevel,
					onLevelSelected = onLevelSelected,
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
			.imePadding()
			.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		content()
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelsScreen(
	models: List<ModelDto>,
	selectedModel: ModelDto,
	selectedLevel: String,
	onModelSelected: (ModelDto) -> Unit,
	onOpenEffort: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	val motion = MaterialTheme.motionScheme
	var isSearching by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
	val searchFocusRequester = remember { FocusRequester() }
	val keyboardController = LocalSoftwareKeyboardController.current
	val filteredModels = remember(models, searchQuery) {
		filterModels(models = models, searchQuery = searchQuery)
	}
	val exitSearch: () -> Unit = {
		searchQuery = ""
		isSearching = false
		keyboardController?.hide()
	}
	BackHandler(enabled = isSearching) {
		exitSearch()
	}
	LaunchedEffect(isSearching) {
		if (isSearching) {
			searchFocusRequester.requestFocus()
		}
	}
	SheetScreenColumn(modifier = modifier) {
		AnimatedContent(
			targetState = isSearching,
			contentAlignment = Alignment.CenterEnd,
			transitionSpec = {
				(fadeIn(animationSpec = motion.defaultEffectsSpec()) +
					expandHorizontally(
						expandFrom = Alignment.End,
						animationSpec = motion.defaultSpatialSpec(),
					)) togetherWith
					(fadeOut(animationSpec = motion.fastEffectsSpec()) +
						shrinkHorizontally(
							shrinkTowards = Alignment.End,
							animationSpec = motion.fastSpatialSpec(),
						)) using
					SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
			},
			label = "model-search-header",
		) { searching ->
			if (searching) {
				ModelSearchField(
					searchQuery = searchQuery,
					onSearchQueryChange = { searchQuery = it },
					onExitSearch = exitSearch,
					onSearchAction = { keyboardController?.hide() },
					focusRequester = searchFocusRequester,
				)
			} else {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(
						text = "Choose model",
						style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
						color = scheme.onSurface,
						modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
					)
					Surface(
						shape = CircleShape,
						color = scheme.surfaceContainerHigh,
						contentColor = scheme.onSurfaceVariant,
						modifier = Modifier.size(48.dp),
						onClick = { isSearching = true },
					) {
						Box(
							contentAlignment = Alignment.Center,
							modifier = Modifier.size(48.dp),
						) {
							Icon(
								imageVector = Icons.Filled.Search,
								contentDescription = "Search models",
							)
						}
					}
				}
			}
		}
		if (filteredModels.isEmpty()) {
			Text(
				text = "No models found",
				style = MaterialTheme.typography.bodyMedium,
				color = scheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
			)
		} else {
			LazyColumn(
				modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				items(filteredModels, key = { it.id }) { model ->
					ModelRow(
						model = model,
						selected = model.id == selectedModel.id,
						onClick = { onModelSelected(model) },
					)
				}
			}
		}
		if (!isSearching && selectedModel.reasoning.levels.isNotEmpty()) {
			Spacer(modifier = Modifier.height(8.dp))
			SectionLabel(text = "Reasoning")
			EffortRow(
				level = selectedLevel,
				onClick = onOpenEffort,
			)
		}
	}
}

@Composable
private fun ModelSearchField(
	searchQuery: String,
	onSearchQueryChange: (String) -> Unit,
	onExitSearch: () -> Unit,
	onSearchAction: () -> Unit,
	focusRequester: FocusRequester,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	TextField(
		value = searchQuery,
		onValueChange = onSearchQueryChange,
		placeholder = { Text(text = "Choose model") },
		leadingIcon = {
			IconButton(onClick = onExitSearch) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = "Back to models",
				)
			}
		},
		trailingIcon = {
			if (searchQuery.isNotEmpty()) {
				IconButton(onClick = { onSearchQueryChange("") }) {
					Icon(
						imageVector = Icons.Filled.Close,
						contentDescription = "Clear search",
					)
				}
			}
		},
		singleLine = true,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
		shape = MaterialTheme.shapes.extraLarge,
		colors = TextFieldDefaults.colors(
			focusedContainerColor = scheme.surfaceContainerHigh,
			unfocusedContainerColor = scheme.surfaceContainerHigh,
			focusedIndicatorColor = Color.Transparent,
			unfocusedIndicatorColor = Color.Transparent,
			cursorColor = scheme.primary,
		),
		modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
	)
}

private fun filterModels(
	models: List<ModelDto>,
	searchQuery: String,
): List<ModelDto> {
	val searchTerms = searchQuery
		.lowercase()
		.replace('-', ' ')
		.replace('/', ' ')
		.replace('_', ' ')
		.split(' ', '\t', '\n')
		.filter { it.isNotEmpty() }
	if (searchTerms.isEmpty()) {
		return models
	}
	return models.filter { model ->
		val searchableText = listOf(model.name, model.id, model.provider, model.description.orEmpty())
			.joinToString(" ")
			.lowercase()
			.replace('-', ' ')
			.replace('/', ' ')
			.replace('_', ' ')
		searchTerms.all { searchTerm -> searchTerm in searchableText }
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModelRow(
	model: ModelDto,
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
			ProviderLogo(
				logoUrl = model.logoUrl,
				provider = model.provider,
				modifier = Modifier.padding(end = 12.dp),
			)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = model.name,
					style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
				)
				if (model.provider.isNotBlank()) {
					Text(
						text = model.provider,
						style = MaterialTheme.typography.labelMedium,
						color = content.copy(alpha = 0.7f),
					)
				}
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
	level: String,
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
			}
			EffortPill {
				AnimatedContent(
					targetState = level,
					label = "effort-label",
				) { current ->
					Text(
						text = current,
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
	levels: List<String>,
	selectedLevel: String,
	onLevelSelected: (String) -> Unit,
	onBack: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val scheme = MaterialTheme.colorScheme
	val motion = MaterialTheme.motionScheme
	if (levels.isEmpty()) return
	val selectedIndex = levels.indexOf(selectedLevel).coerceAtLeast(0)
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
					targetState = selectedLevel,
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
				) { current ->
					Text(
						text = current,
						style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
						color = scheme.primary,
					)
				}
				Spacer(modifier = Modifier.height(16.dp))
				val sliderState = rememberSliderState(
					value = selectedIndex.toFloat(),
					steps = (levels.size - 2).coerceAtLeast(0),
					trackRange = 0f..(levels.size - 1).toFloat(),
				)
				LaunchedEffect(selectedLevel, levels) {
					sliderState.value = levels.indexOf(selectedLevel).coerceAtLeast(0).toFloat()
				}
				Slider(
					state = sliderState,
					onValueChange = { raw ->
						sliderState.value = raw
						onLevelSelected(levels[raw.roundToInt().coerceIn(levels.indices)])
					},
				)
				Row(
					horizontalArrangement = Arrangement.SpaceBetween,
					modifier = Modifier.fillMaxWidth(),
				) {
					levels.forEach { level ->
						Text(
							text = level,
							style = MaterialTheme.typography.labelMedium.copy(
								fontWeight = if (level == selectedLevel) {
									FontWeight.Bold
								} else {
									FontWeight.Normal
								},
							),
							color = if (level == selectedLevel) {
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
