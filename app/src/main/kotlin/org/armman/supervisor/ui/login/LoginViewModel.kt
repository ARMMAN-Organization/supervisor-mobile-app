package org.armman.supervisor.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.data.auth.AuthRepository
import org.armman.supervisor.data.auth.LoginFailureReason
import org.armman.supervisor.data.auth.LoginRequest
import org.armman.supervisor.data.auth.LoginResult
import org.armman.supervisor.data.auth.session.SessionStore
import javax.inject.Inject

/**
 * UI state for the login screen. Error messages are string resource ids so
 * they localize with the rest of the app (EN/Marathi).
 */
data class LoginUiState(
  val username: String = "",
  val password: String = "",
  val isSubmitting: Boolean = false,
  val isCheckingSession: Boolean = false,
  @StringRes val usernameError: Int? = null,
  @StringRes val passwordError: Int? = null,
  @StringRes val loginError: Int? = null,
  val loginSucceeded: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
  private val authRepository: AuthRepository,
  private val sessionStore: SessionStore,
) : ViewModel() {

  // Start in the "checking session" state to avoid reading EncryptedSharedPreferences on the
  // main thread (which can block while the Android Keystore warms up on first launch).
  private val _uiState = MutableStateFlow(LoginUiState(isCheckingSession = true))
  val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

  init {
    // Check for an existing "stay logged in" session off the main thread.
    viewModelScope.launch(Dispatchers.IO) {
      val hasSession = sessionStore.readSession() != null
      _uiState.update { it.copy(isCheckingSession = false, loginSucceeded = hasSession) }
    }
  }

  fun onUsernameChanged(value: String) {
    _uiState.update { it.copy(username = value, usernameError = null, loginError = null) }
  }

  fun onPasswordChanged(value: String) {
    _uiState.update { it.copy(password = value, passwordError = null, loginError = null) }
  }

  fun onLoginClicked() {
    val state = _uiState.value
    if (state.isSubmitting) return

    val usernameError = if (state.username.isBlank()) R.string.login_error_username_required else null
    val passwordError = if (state.password.isBlank()) R.string.login_error_password_required else null
    if (usernameError != null || passwordError != null) {
      _uiState.update { it.copy(usernameError = usernameError, passwordError = passwordError) }
      return
    }

    // Set synchronously so a second tap before the coroutine runs is ignored.
    _uiState.update { it.copy(isSubmitting = true, loginError = null) }
    viewModelScope.launch {
      // Leading/trailing whitespace is trimmed (accidental autocorrect/copy-paste); case is
      // preserved — the real auth-service does an exact, case-sensitive username match.
      val result = authRepository.login(
        LoginRequest(username = state.username.trim(), password = state.password),
      )
      when (result) {
        is LoginResult.Success ->
          _uiState.update { it.copy(isSubmitting = false, loginSucceeded = true) }
        is LoginResult.Failure ->
          _uiState.update { it.copy(isSubmitting = false, loginError = result.reason.toMessageRes()) }
      }
    }
  }

  /** Reset the one-shot success flag after navigation has been performed. */
  fun onLoginHandled() {
    _uiState.update { it.copy(loginSucceeded = false) }
  }

  @StringRes
  private fun LoginFailureReason.toMessageRes(): Int = when (this) {
    LoginFailureReason.INVALID_CREDENTIALS -> R.string.login_error_invalid_credentials
    LoginFailureReason.VALIDATION_ERROR -> R.string.login_error_generic
    LoginFailureReason.NETWORK_ERROR -> R.string.login_error_network
    LoginFailureReason.WRONG_ROLE -> R.string.login_error_wrong_role
    LoginFailureReason.OFFLINE_NO_CACHE -> R.string.login_error_offline_no_cache
    LoginFailureReason.OFFLINE_SESSION_EXPIRED -> R.string.login_error_offline_session_expired
    LoginFailureReason.UNKNOWN -> R.string.login_error_generic
  }
}
