package com.agentic.aichat.ui.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OnboardingResult(
    val modelId: String,
    val effort: String,
    val goal: String,
)

class OnboardingViewModel : ViewModel() {
    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _goal = MutableStateFlow("")
    val goal: StateFlow<String> = _goal.asStateFlow()

    private val _effort = MutableStateFlow("Medium")
    val effort: StateFlow<String> = _effort.asStateFlow()

    private val _modelId = MutableStateFlow("spark-1")
    val modelId: StateFlow<String> = _modelId.asStateFlow()

    val totalSteps: Int = 5

    fun selectGoal(goal: String) {
        _goal.value = goal
    }

    fun selectEffort(effort: String) {
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
