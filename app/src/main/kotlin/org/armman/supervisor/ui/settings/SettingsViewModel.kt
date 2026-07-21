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

/** Supported app languages with their BCP-47 tags. */
enum class AppLanguage(val tag: String) { ENGLISH("en"), MARATHI("mr") }

/**
 * [languageRequest] is a one-shot signal consumed by the screen: it applies the
 * locale change via AppCompat, then calls [SettingsViewModel.onLanguageApplied].
 * A monotonically increasing request id (not just the tag) is required so a
 * repeat selection of the SAME language — or a selection made right after an
 * activity recreate from a prior locale change — is still observed as a new
 * event by `LaunchedEffect`, which only re-fires on an actual key change.
 */
data class SettingsUiState(
  val showLogoutConfirmation: Boolean = false,
  val showLanguageDialog: Boolean = false,
  val languageRequest: LanguageRequest? = null,
)

data class LanguageRequest(val tag: String, val requestId: Int)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val authRepository: AuthRepository,
) : ViewModel() {
  val versionName: String = BuildConfig.VERSION_NAME

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
  private var languageRequestCounter = 0

  /**
   * Every action other than [SettingsAction.LANGUAGE_SETUP] currently just surfaces a
   * "Coming soon" message (shown by the screen itself). This hook exists so each action
   * gets a real, deliberate implementation later instead of the screen silently growing
   * behavior with no ViewModel entry point.
   */
  fun onActionTapped(action: SettingsAction) {
    when (action) {
      SettingsAction.LANGUAGE_SETUP -> _uiState.update { it.copy(showLanguageDialog = true) }
      else -> { /* no-op — see function doc. */ }
    }
  }

  fun onDismissLanguageDialog() {
    _uiState.update { it.copy(showLanguageDialog = false) }
  }

  /** Emits the one-shot locale request; the screen applies it via AppCompat. */
  fun onLanguageSelected(language: AppLanguage) {
    languageRequestCounter++
    _uiState.update {
      it.copy(
        showLanguageDialog = false,
        languageRequest = LanguageRequest(language.tag, languageRequestCounter),
      )
    }
  }

  /** Reset after the screen has applied the locale. */
  fun onLanguageApplied() {
    _uiState.update { it.copy(languageRequest = null) }
  }

  /** Tapping "Log out" opens a confirmation dialog rather than logging out immediately. */
  fun onLogOutClicked() {
    _uiState.update { it.copy(showLogoutConfirmation = true) }
  }

  fun onLogoutConfirmDismissed() {
    _uiState.update { it.copy(showLogoutConfirmation = false) }
  }

  fun onLogoutConfirmed(onLoggedOut: () -> Unit) {
    _uiState.update { it.copy(showLogoutConfirmation = false) }
    viewModelScope.launch {
      authRepository.logout()
      onLoggedOut()
    }
  }
}
