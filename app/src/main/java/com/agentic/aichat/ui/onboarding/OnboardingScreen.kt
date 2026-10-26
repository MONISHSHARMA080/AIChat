package com.agentic.aichat.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentic.aichat.ui.chat.AvailableModels
import com.agentic.aichat.ui.chat.ReasoningEffort

private data class QuizOption(
    val id: String,
    val title: String,
    val outcome: String,
)

private val GoalOptions = listOf(
    QuizOption("work", "Nail my work", "Decide faster and write sharper"),
    QuizOption("learn", "Learn anything", "Understand it the first time"),
    QuizOption("create", "Make something", "Drafts, plans and ideas that ship"),
    QuizOption("everyday", "Handle everyday stuff", "Quick answers that hold up"),
)

private fun effortOptions(): List<Triple<ReasoningEffort, String, String>> = listOf(
    Triple(ReasoningEffort.Less, "Get moving in seconds", "Light touches, off your plate fast"),
    Triple(ReasoningEffort.Medium, "Keep it balanced", "Solid answers without the wait"),
    Triple(ReasoningEffort.Hard, "Work through the tricky stuff", "Stays with hard problems longer"),
    Triple(ReasoningEffort.Ultra, "Leave no stone unturned", "Takes its time when it matters most"),
)

private fun modelOutcome(modelId: String): Pair<String, String> = when (modelId) {
    "spark-1-mini" -> Pair("When seconds matter", "Off your plate in a flash")
    "spark-reasoner" -> Pair("For the hard ones", "Stays with a problem till it cracks")
    else -> Pair("Your everyday winner", "Gets it right the first time, most days")
}

@Composable
fun OnboardingScreen(
    onFinished: (modelId: String, effort: ReasoningEffort, goal: String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(),
) {
    val step by viewModel.step.collectAsState()
    val goal by viewModel.goal.collectAsState()
    val effort by viewModel.effort.collectAsState()
    val modelId by viewModel.modelId.collectAsState()
    val total = viewModel.totalSteps

    BackHandler(enabled = step > 0) {
        viewModel.back()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        IconButton(onClick = { viewModel.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onSkip) {
                        Text("Skip")
                    }
                }
                LinearProgressIndicator(
                    progress = { (step + 1).toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val cta = if (step == total - 1) "Start chatting" else "Continue"
                val canContinue = if (step == 1) goal.isNotEmpty() else true
                Button(
                    onClick = {
                        if (step == total - 1) {
                            val result = viewModel.result()
                            onFinished(result.modelId, result.effort, result.goal)
                        } else {
                            viewModel.next()
                        }
                    },
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(cta)
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = step,
            label = "onboarding-step",
            modifier = Modifier.padding(padding),
        ) { current ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (current) {
                    0 -> WelcomeStep()
                    1 -> QuizStep(
                        title = "What will a win look like this week?",
                        subtitle = "We will tune your first chat around it.",
                        options = GoalOptions,
                        selectedId = goal,
                        onSelect = {
                            viewModel.selectGoal(it)
                            viewModel.next()
                        },
                    )
                    2 -> EffortStep(
                        selected = effort,
                        onSelect = viewModel::selectEffort,
                    )
                    3 -> ModelStep(
                        selectedId = modelId,
                        onSelect = viewModel::selectModel,
                    )
                    else -> ReadyStep(
                        goal = goal,
                        effort = effort,
                        modelId = modelId,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text(
        "Ask once. Get on with your day.",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "No more switching between AI apps or second-guessing answers. Tell us what matters and start with the right lineup.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutcomeRow("Clear answers you can act on")
    OutcomeRow("The right mind for the job")
    OutcomeRow("Faster every time you come back")
}

@Composable
private fun OutcomeRow(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun QuizStep(
    title: String,
    subtitle: String,
    options: List<QuizOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(4.dp))
    options.forEach { option ->
        val selected = option.id == selectedId
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(option.id) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(option.outcome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = selected, onClick = { onSelect(option.id) })
            }
        }
    }
}

@Composable
private fun EffortStep(
    selected: ReasoningEffort,
    onSelect: (ReasoningEffort) -> Unit,
) {
    Text("How should it think for you?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Bigger thinking wins on hard problems. Lighter thinking wins on speed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    effortOptions().forEach { (value, title, outcome) ->
        val isSelected = value == selected
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(value) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(outcome, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = isSelected, onClick = { onSelect(value) })
            }
        }
    }
}

@Composable
private fun ModelStep(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Text("Meet your starting lineup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(
        "Pick one to start with. You can switch or compare models anytime in chat.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    AvailableModels.forEach { model ->
        val outcome = modelOutcome(model.id)
        val isSelected = model.id == selectedId
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(model.id) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(outcome.first, style = MaterialTheme.typography.bodyMedium)
                    Text(outcome.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = isSelected, onClick = { onSelect(model.id) })
            }
        }
    }
}

@Composable
private fun ReadyStep(
    goal: String,
    effort: ReasoningEffort,
    modelId: String,
) {
    val goalTitle = GoalOptions.firstOrNull { it.id == goal }?.title ?: "Handle everyday stuff"
    val modelName = AvailableModels.firstOrNull { it.id == modelId }?.name ?: modelId
    Text("You are set.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(
        "Here is what happens next: open your first chat already tuned to your goal.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutcomeRow("Tuned for: $goalTitle")
    OutcomeRow("Thinking style: ${effort.label}")
    OutcomeRow("Starting model: $modelName")
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "You can change any of this later in chat. Comparing models takes one tap.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
