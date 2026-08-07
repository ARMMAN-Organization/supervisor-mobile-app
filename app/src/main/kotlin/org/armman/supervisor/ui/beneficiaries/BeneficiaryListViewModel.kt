package org.armman.supervisor.ui.beneficiaries

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
import javax.inject.Inject

/** UI state for the Sakhi beneficiary-list screen — covers loading, error and success. */
sealed interface BeneficiaryListUiState {
  data object Loading : BeneficiaryListUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : BeneficiaryListUiState

  data class Success(
    val sakhiName: String,
    val projectName: String,
    val address: String,
    val searchQuery: String,
    val allBeneficiaries: List<BeneficiaryDetail>,
    val visibleBeneficiaries: List<BeneficiaryDetail>,
  ) : BeneficiaryListUiState
}

@HiltViewModel
class BeneficiaryListViewModel @Inject constructor(
  private val repository: BeneficiaryListRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<BeneficiaryListUiState>(BeneficiaryListUiState.Loading)
  val uiState: StateFlow<BeneficiaryListUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onSearchQueryChanged(query: String) {
    val current = _uiState.value as? BeneficiaryListUiState.Success ?: return
    _uiState.value = current.copy(searchQuery = query, visibleBeneficiaries = filter(current.allBeneficiaries, query))
  }

  private fun load() {
    _uiState.value = BeneficiaryListUiState.Loading
    viewModelScope.launch {
      try {
        val result = repository.getBeneficiaries(sakhiId)
        _uiState.value = BeneficiaryListUiState.Success(
          sakhiName = result.sakhiName,
          projectName = result.projectName,
          address = result.address,
          searchQuery = "",
          allBeneficiaries = result.beneficiaries,
          visibleBeneficiaries = result.beneficiaries,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = BeneficiaryListUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }

  private fun filter(beneficiaries: List<BeneficiaryDetail>, query: String): List<BeneficiaryDetail> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return beneficiaries
    return beneficiaries.filter { it.name.contains(trimmed, ignoreCase = true) }
  }

  companion object {
    const val SAKHI_ID_ARG = "sakhiId"
  }
}
