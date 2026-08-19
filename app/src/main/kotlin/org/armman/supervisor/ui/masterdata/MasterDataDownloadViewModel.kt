package org.armman.supervisor.ui.masterdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.masterdata.MasterDataRepository
import org.armman.supervisor.data.masterdata.MasterDataResult
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Per-row download state, matching the reference screen's Pending/Downloading/done states. */
enum class DownloadRowStatus { PENDING, DOWNLOADING, COMPLETED, EMPTY, NOT_AVAILABLE }

data class DownloadRow(val entity: MasterDataEntity, val status: DownloadRowStatus)

/**
 * UI state for the Download Master Data screen. The screen has no whole-screen loading/error/
 * empty phase of its own — the row list is the content from the moment the screen mounts — so,
 * following this app's [org.armman.supervisor.ui.registrations.RegistrationsUiState]/
 * [org.armman.supervisor.ui.meetingtraining.MarksUiState] convention, everything lives inside a
 * single [Content] variant, with the per-row loading/error/empty/success states and the dialog
 * flags folded in as fields, the same way those screens fold `isLoadingRoster`/`isSaving` into
 * their own `Success` variant.
 */
sealed interface MasterDataDownloadUiState {
  data class Content(
    val rows: List<DownloadRow> = MasterDataEntity.entries.map { DownloadRow(it, DownloadRowStatus.PENDING) },
    val activeIndex: Int = -1,
    val showNetworkErrorDialog: Boolean = false,
    val showQuitConfirmation: Boolean = false,
    val showCompletionDialog: Boolean = false,
  ) : MasterDataDownloadUiState {
    val isDownloading: Boolean get() = activeIndex in rows.indices && !showNetworkErrorDialog
  }
}

/**
 * Downloads every [MasterDataEntity] one at a time, in enum order — mirroring the reference app's
 * strict sequential chain (no parallel fetches). A network drop pauses the chain and offers
 * Retry (restarts from row 0) / Stop, matching the reference app's behavior; it never resumes
 * from the failed row.
 */
@HiltViewModel
class MasterDataDownloadViewModel @Inject constructor(
  private val repository: MasterDataRepository,
  private val connectivityChecker: ConnectivityChecker,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MasterDataDownloadUiState.Content())
  val uiState: StateFlow<MasterDataDownloadUiState> = _uiState.asStateFlow()

  private var downloadJob: Job? = null

  private companion object {
    /** Every row stays on screen at least this long, even when its real API call finishes almost
     * instantly — small lookup-category payloads otherwise flash past too fast to read, unlike
     * Beneficiary Data Download's naturally heavier per-row payloads. Matches
     * [org.armman.supervisor.data.masterdata.MasterDataRepositoryImpl.MOCK_DOWNLOAD_DELAY_MS] so
     * real and mocked rows pace the same. */
    val MIN_ROW_DISPLAY_DURATION = 1200.milliseconds
  }

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

      val entities = MasterDataEntity.entries
      for (index in entities.indices) {
        _uiState.update { it.setStatus(index, DownloadRowStatus.DOWNLOADING).copy(activeIndex = index) }

        val startMark = TimeSource.Monotonic.markNow()
        val result = repository.download(entities[index])
        val elapsed = startMark.elapsedNow()
        if (result !is MasterDataResult.Failure && elapsed < MIN_ROW_DISPLAY_DURATION) {
          delay(MIN_ROW_DISPLAY_DURATION - elapsed)
        }

        when (result) {
          is MasterDataResult.Success -> _uiState.update { it.setStatus(index, DownloadRowStatus.COMPLETED) }
          is MasterDataResult.Empty -> _uiState.update { it.setStatus(index, DownloadRowStatus.EMPTY) }
          is MasterDataResult.NotAvailable -> _uiState.update { it.setStatus(index, DownloadRowStatus.NOT_AVAILABLE) }
          is MasterDataResult.Failure -> {
            _uiState.update { it.copy(showNetworkErrorDialog = true) }
            return@launch
          }
        }
      }

      _uiState.update { it.copy(activeIndex = -1, showCompletionDialog = true) }
    }
  }

  /** Restarts the entire sequence from the first row — the reference app never resumes mid-chain. */
  fun onRetryClicked() {
    _uiState.update {
      it.copy(
        rows = MasterDataEntity.entries.map { entity -> DownloadRow(entity, DownloadRowStatus.PENDING) },
        activeIndex = -1,
        showNetworkErrorDialog = false,
      )
    }
    startDownload()
  }

  /** Dismisses the network-error dialog without resuming; the partial state stays visible, but the
   * row that was mid-download when the error hit must stop showing as DOWNLOADING now that
   * activeIndex no longer points at anything in progress. */
  fun onStopClicked() {
    downloadJob?.cancel()
    _uiState.update { it.copy(activeIndex = -1, showNetworkErrorDialog = false) }
  }

  /** Back pressed while still downloading asks for confirmation; once complete, back navigates
   * immediately (the screen itself decides that based on [MasterDataDownloadUiState.Content.isDownloading]). */
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

  private fun MasterDataDownloadUiState.Content.setStatus(
    index: Int,
    status: DownloadRowStatus,
  ): MasterDataDownloadUiState.Content =
    copy(rows = rows.mapIndexed { i, row -> if (i == index) row.copy(status = status) else row })
}
