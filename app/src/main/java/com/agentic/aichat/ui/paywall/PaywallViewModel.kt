package com.agentic.aichat.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.billing.BillingRepository
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
	val packages: List<Package> = emptyList(),
	val isLoading: Boolean = true,
	val isPurchasing: Boolean = false,
	val error: String? = null,
	val purchased: Boolean = false,
)

@HiltViewModel
class PaywallViewModel @Inject constructor(
	private val billingRepository: BillingRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow(PaywallUiState())
	val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

	init {
		refresh()
	}

	fun refresh() {
		_uiState.update { it.copy(isLoading = true, error = null) }
		viewModelScope.launch {
			billingRepository.getCurrentOffering()
				.onSuccess { offering ->
					_uiState.update { it.copy(packages = offering?.availablePackages.orEmpty(), isLoading = false) }
				}
				.onFailure { error ->
					if (error is CancellationException) throw error
					_uiState.update { it.copy(isLoading = false, error = "Could not load plans.") }
				}
		}
	}

	fun purchase(activity: Activity, pkg: Package) {
		_uiState.update { it.copy(isPurchasing = true, error = null) }
		viewModelScope.launch {
			billingRepository.purchase(activity, pkg)
				.onSuccess {
					_uiState.update { it.copy(isPurchasing = false, purchased = true) }
				}
				.onFailure { error ->
					if (error is CancellationException) throw error
					_uiState.update { it.copy(isPurchasing = false, error = "Purchase failed. Try again.") }
				}
		}
	}

	fun restore() {
		_uiState.update { it.copy(isPurchasing = true, error = null) }
		viewModelScope.launch {
			billingRepository.restore()
				.onSuccess { info ->
					if (info.entitlements.active.isNotEmpty()) {
						_uiState.update { it.copy(isPurchasing = false, purchased = true) }
					} else {
						_uiState.update { it.copy(isPurchasing = false, error = "No purchases found.") }
					}
				}
				.onFailure { error ->
					if (error is CancellationException) throw error
					_uiState.update { it.copy(isPurchasing = false, error = "Restore failed. Try again.") }
				}
		}
	}

	fun consumePurchased() {
		_uiState.update { it.copy(purchased = false) }
	}

	fun clearError() {
		_uiState.update { it.copy(error = null) }
	}
}
