package org.armman.supervisor.ui.quickresponse

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import javax.inject.Inject

/** UI state for the Quick Response list screen — covers loading, error and success. */
sealed interface QuickResponseUiState {
  data object Loading : QuickResponseUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : QuickResponseUiState

  data class Success(val requests: List<QuickResponseRequest>) : QuickResponseUiState
}

@HiltViewModel
class QuickResponseViewModel @Inject constructor(
  private val repository: QuickResponseRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<QuickResponseUiState>(QuickResponseUiState.Loading)
  val uiState: StateFlow<QuickResponseUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadRequests()
  }

  fun onRetry() {
    loadRequests()
  }

  private fun loadRequests() {
    _uiState.value = QuickResponseUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val requests = repository.getRequests()
        _uiState.value = QuickResponseUiState.Success(requests)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = QuickResponseUiState.Error(R.string.quick_response_error_load, e.message)
      }
    }
  }
}
