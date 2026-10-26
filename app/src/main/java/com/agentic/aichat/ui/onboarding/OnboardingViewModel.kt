package com.agentic.aichat.ui.onboarding

import androidx.lifecycle.ViewModel
import com.agentic.aichat.ui.chat.ReasoningEffort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingResult(
    val modelId: String,
    val effort: ReasoningEffort,
    val goal: String,
)

class OnboardingViewModel : ViewModel() {
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _goal = MutableStateFlow("")
    val goal: StateFlow<String> = _goal.asStateFlow()

    private val _effort = MutableStateFlow(ReasoningEffort.Medium)
    val effort: StateFlow<ReasoningEffort> = _effort.asStateFlow()

    private val _modelId = MutableStateFlow("spark-1")
    val modelId: StateFlow<String> = _modelId.asStateFlow()

    val totalSteps: Int = 5

    fun selectGoal(goal: String) {
        _goal.value = goal
    }

    fun selectEffort(effort: ReasoningEffort) {
        _effort.value = effort
    }

    fun selectModel(modelId: String) {
        _modelId.value = modelId
    }

    fun next() {
        if (_step.value < totalSteps - 1) _step.value = _step.value + 1
    }

    fun back(): Boolean {
        if (_step.value > 0) {
            _step.value = _step.value - 1
            return true
        }
        return false
    }

    fun canContinue(): Boolean =
        when (_step.value) {
            1 -> _goal.value.isNotEmpty()
            else -> true
        }

    fun result(): OnboardingResult =
        OnboardingResult(
            modelId = _modelId.value,
            effort = _effort.value,
            goal = _goal.value.ifEmpty { "everyday" },
        )
}
