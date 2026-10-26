package com.agentic.aichat.ui.signin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentic.aichat.data.auth.AuthRepository
import com.agentic.aichat.data.auth.GoogleSignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignInUiState {
	data object Idle : SignInUiState
	data object Loading : SignInUiState
	data object Success : SignInUiState
	data class Error(val message: String) : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
	private val authRepository: AuthRepository,
) : ViewModel() {
	private val _uiState = MutableStateFlow<SignInUiState>(SignInUiState.Idle)
	val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

	fun onGoogleSignIn(activity: Activity) {
		if (_uiState.value == SignInUiState.Loading) return
		_uiState.value = SignInUiState.Loading
		viewModelScope.launch {
			when (val result = authRepository.signInWithGoogle(activity)) {
				GoogleSignInResult.Cancelled -> _uiState.value = SignInUiState.Idle
				GoogleSignInResult.NoAccounts ->
					_uiState.value = SignInUiState.Error("No Google accounts found on this device")
				GoogleSignInResult.Interrupted ->
					_uiState.value = SignInUiState.Error("Sign-in was interrupted, please try again")
				is GoogleSignInResult.Failed ->
					_uiState.value = SignInUiState.Error(result.cause.message ?: "Google sign-in failed")
				is GoogleSignInResult.Success -> _uiState.value = SignInUiState.Success
			}
		}
	}

	fun consumeError() {
		if (_uiState.value is SignInUiState.Error) {
			_uiState.value = SignInUiState.Idle
		}
	}

}
