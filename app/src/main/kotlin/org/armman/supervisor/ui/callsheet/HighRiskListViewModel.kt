package org.armman.supervisor.ui.callsheet

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the "High Risk ANC"/"High Risk PNC" drill-down screen. */
sealed interface HighRiskListUiState {
  data object Loading : HighRiskListUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : HighRiskListUiState

  data class Success(val items: List<HighRiskItem>) : HighRiskListUiState
}

@HiltViewModel
class HighRiskListViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])
  val riskType: HighRiskType = HighRiskType.valueOf(checkNotNull(savedStateHandle[Routes.HIGH_RISK_TYPE_ARG]))

  private val _uiState = MutableStateFlow<HighRiskListUiState>(HighRiskListUiState.Loading)
  val uiState: StateFlow<HighRiskListUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  private fun load() {
    _uiState.value = HighRiskListUiState.Loading
    viewModelScope.launch {
      try {
        val items = repository.getHighRisk(sakhiId, riskType)
        _uiState.value = HighRiskListUiState.Success(items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = HighRiskListUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
