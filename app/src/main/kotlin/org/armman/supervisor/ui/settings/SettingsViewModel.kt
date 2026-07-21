package org.armman.supervisor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.auth.AuthRepository
import javax.inject.Inject

/** Identifies which not-yet-implemented Settings action was tapped. */
enum class SettingsAction {
  DOWNLOAD_MASTER_DATA,
  DOWNLOAD_BENEFICIARY_DATA,
  LANGUAGE_SETUP,
  CHANGE_PASSWORD,
  SHARE_DATABASE_FILE,
  CHECK_UPDATE,
}

data class SettingsUiState(
  val showLogoutConfirmation: Boolean = false,
  val logoutCompleted: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
) : ViewModel() {
  val versionName: String = BuildConfig.VERSION_NAME

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /**
   * Every action currently just surfaces a "Coming soon" message (shown by the screen itself).
   * This hook exists so each action gets a real, deliberate implementation later instead of the
   * screen silently growing behavior with no ViewModel entry point.
   */
  fun onActionTapped(action: SettingsAction) {
    // Intentionally no-op — see class doc.
  }

  /** Tapping "Log out" opens a confirmation dialog rather than logging out immediately. */
  fun onLogOutClicked() {
    _uiState.update { it.copy(showLogoutConfirmation = true) }
  }

  fun onLogoutConfirmDismissed() {
    _uiState.update { it.copy(showLogoutConfirmation = false) }
  }

  fun onLogoutConfirmed() {
    _uiState.update { it.copy(showLogoutConfirmation = false) }
    viewModelScope.launch {
      authRepository.logout()
      _uiState.update { it.copy(logoutCompleted = true) }
    }
  }

  /** Reset the one-shot logout flag after the screen has navigated away. */
  fun onLogoutNavigated() {
    _uiState.update { it.copy(logoutCompleted = false) }
  }
}
