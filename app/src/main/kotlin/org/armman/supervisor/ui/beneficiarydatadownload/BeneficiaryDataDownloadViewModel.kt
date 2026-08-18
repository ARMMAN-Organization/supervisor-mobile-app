package org.armman.supervisor.ui.beneficiarydatadownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataRepository
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataResult
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import javax.inject.Inject

/** Per-row download state, matching [org.armman.supervisor.ui.masterdata.DownloadRowStatus]. */
enum class BeneficiaryDataRowStatus { PENDING, DOWNLOADING, COMPLETED, EMPTY, NOT_AVAILABLE }

data class BeneficiaryDataRow(val entity: BeneficiaryDataEntity, val status: BeneficiaryDataRowStatus)

/**
 * UI state for the Download Beneficiary Data screen, following the same single-[Content]-variant
 * convention as [org.armman.supervisor.ui.masterdata.MasterDataDownloadUiState].
 */
sealed interface BeneficiaryDataDownloadUiState {
  data class Content(
    val rows: List<BeneficiaryDataRow> = BeneficiaryDataEntity.entries.map {
      BeneficiaryDataRow(it, BeneficiaryDataRowStatus.PENDING)
    },
    val activeIndex: Int = -1,
    val showNetworkErrorDialog: Boolean = false,
    val showQuitConfirmation: Boolean = false,
    val showCompletionDialog: Boolean = false,
  ) : BeneficiaryDataDownloadUiState {
    val isDownloading: Boolean get() = activeIndex in rows.indices && !showNetworkErrorDialog
  }
}

/**
 * Downloads every [BeneficiaryDataEntity] one at a time, in enum order — mirroring
 * [org.armman.supervisor.ui.masterdata.MasterDataDownloadViewModel]'s strict sequential chain (no
 * parallel fetches). A network drop pauses the chain and offers Retry (restarts from row 0) /
 * Stop; it never resumes from the failed row.
 */
@HiltViewModel
class BeneficiaryDataDownloadViewModel @Inject constructor(
  private val repository: BeneficiaryDataRepository,
  private val connectivityChecker: ConnectivityChecker,
) : ViewModel() {

  private val _uiState = MutableStateFlow(BeneficiaryDataDownloadUiState.Content())
  val uiState: StateFlow<BeneficiaryDataDownloadUiState> = _uiState.asStateFlow()

  private var downloadJob: Job? = null

  init {
    startDownload()
  }

  private fun startDownload() {
    downloadJob?.cancel()
    downloadJob = viewModelScope.launch {
      if (!connectivityChecker.isOnline()) {
        _uiState.update { it.copy(showNetworkErrorDialog = true) }
        return@launch
      }

      val entities = BeneficiaryDataEntity.entries
      for (index in entities.indices) {
        _uiState.update { it.setStatus(index, BeneficiaryDataRowStatus.DOWNLOADING).copy(activeIndex = index) }

        when (val result = repository.download(entities[index])) {
          is BeneficiaryDataResult.Success -> _uiState.update { it.setStatus(index, BeneficiaryDataRowStatus.COMPLETED) }
          is BeneficiaryDataResult.Empty -> _uiState.update { it.setStatus(index, BeneficiaryDataRowStatus.EMPTY) }
          is BeneficiaryDataResult.NotAvailable ->
            _uiState.update { it.setStatus(index, BeneficiaryDataRowStatus.NOT_AVAILABLE) }
          is BeneficiaryDataResult.Failure -> {
            _uiState.update { it.copy(showNetworkErrorDialog = true) }
            return@launch
          }
        }
      }

      _uiState.update { it.copy(activeIndex = -1, showCompletionDialog = true) }
    }
  }

  /** Restarts the entire sequence from the first row — never resumes mid-chain. */
  fun onRetryClicked() {
    _uiState.update {
      it.copy(
        rows = BeneficiaryDataEntity.entries.map { entity -> BeneficiaryDataRow(entity, BeneficiaryDataRowStatus.PENDING) },
        activeIndex = -1,
        showNetworkErrorDialog = false,
      )
    }
    startDownload()
  }

  /** Dismisses the network-error dialog without resuming; the partial state stays visible. */
  fun onStopClicked() {
    downloadJob?.cancel()
    _uiState.update { it.copy(showNetworkErrorDialog = false) }
  }

  /** Back pressed while still downloading asks for confirmation; once complete, back navigates
   * immediately (the screen itself decides that based on [BeneficiaryDataDownloadUiState.Content.isDownloading]). */
  fun onBackRequested() {
    _uiState.update { it.copy(showQuitConfirmation = true) }
  }

  fun onQuitConfirmed(onQuit: () -> Unit) {
    downloadJob?.cancel()
    _uiState.update { it.copy(showQuitConfirmation = false) }
    onQuit()
  }

  fun onQuitDismissed() {
    _uiState.update { it.copy(showQuitConfirmation = false) }
  }

  fun onCompletionAcknowledged(onDone: () -> Unit) {
    _uiState.update { it.copy(showCompletionDialog = false) }
    onDone()
  }

  private fun BeneficiaryDataDownloadUiState.Content.setStatus(
    index: Int,
    status: BeneficiaryDataRowStatus,
  ): BeneficiaryDataDownloadUiState.Content =
    copy(rows = rows.mapIndexed { i, row -> if (i == index) row.copy(status = status) else row })
}
