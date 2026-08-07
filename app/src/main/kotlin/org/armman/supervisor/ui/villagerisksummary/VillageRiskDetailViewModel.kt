package org.armman.supervisor.ui.villagerisksummary

import androidx.lifecycle.SavedStateHandle
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
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class VillageRiskDetailViewModel @Inject constructor(
  private val repository: VillageRiskDetailRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val villageId: String = decode(checkNotNull(savedStateHandle[VILLAGE_ID_ARG]))
  private val villageName: String = decode(checkNotNull(savedStateHandle[VILLAGE_NAME_ARG]))
  private val sakhiName: String = decode(checkNotNull(savedStateHandle[SAKHI_NAME_ARG]))

  private val _uiState = MutableStateFlow<VillageRiskDetailUiState>(VillageRiskDetailUiState.Loading)
  val uiState: StateFlow<VillageRiskDetailUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onTabSelected(tab: Tab) {
    val current = _uiState.value as? VillageRiskDetailUiState.Success ?: return
    _uiState.value = current.copy(selectedTab = tab)
  }

  private fun load() {
    _uiState.value = VillageRiskDetailUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val detail = repository.getVillageRiskDetail(villageId)
        _uiState.value = VillageRiskDetailUiState.Success(
          villageName = villageName.ifBlank { detail.villageName },
          sakhiName = sakhiName,
          mothers = detail.mothers,
          children = detail.children,
          selectedTab = Tab.MOTHER,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = VillageRiskDetailUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }

  companion object {
    const val VILLAGE_ID_ARG = "villageId"
    const val VILLAGE_NAME_ARG = "villageName"
    const val SAKHI_NAME_ARG = "sakhiName"

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
  }
}
