package com.agentic.aichat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
	val balance: Int = 0,
	val isLoading: Boolean = true,
	val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
	private val billingRepository: BillingRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow(SettingsUiState())
	val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

	init {
		refreshBalance()
	}

	fun refreshBalance() {
		_uiState.update { it.copy(isLoading = true, error = null) }
		viewModelScope.launch {
			billingRepository.getBalance()
				.onSuccess { balance ->
					_uiState.update { it.copy(balance = balance, isLoading = false) }
				}
				.onFailure { error ->
					if (error is CancellationException) throw error
					_uiState.update { it.copy(isLoading = false, error = "Could not load balance.") }
				}
		}
	}
}
