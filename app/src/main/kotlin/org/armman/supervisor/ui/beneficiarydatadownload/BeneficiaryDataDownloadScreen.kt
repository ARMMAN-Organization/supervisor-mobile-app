package org.armman.supervisor.ui.beneficiarydatadownload

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.DownloadProgressHeader
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.MasterDataCompleted
import org.armman.supervisor.ui.theme.MasterDataDownloading
import org.armman.supervisor.ui.theme.MasterDataDownloadingTrack
import org.armman.supervisor.ui.theme.MasterDataEmpty
import org.armman.supervisor.ui.theme.MasterDataPending
import org.armman.supervisor.ui.theme.NeutralG10
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.RiskHigh
import org.armman.supervisor.ui.theme.StatusSuccess
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/**
 * Download Beneficiary Data screen: a live-updating list of beneficiary-data tables (Pending →
 * Downloading → Completed/Empty/Not available), auto-scrolling to the active row, with
 * quit-confirmation on back and a completion dialog once the whole chain finishes. Mirrors
 * [org.armman.supervisor.ui.masterdata.MasterDataDownloadScreen] exactly.
 */
@Composable
fun BeneficiaryDataDownloadScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BeneficiaryDataDownloadViewModel = hiltViewModel(),
) {
  // Exhaustive per this app's sealed-UiState convention — BeneficiaryDataDownloadUiState has only
  // one variant today, but dispatching through `when` (not a direct cast) means the compiler
  // forces a new branch here the moment a second variant is ever added.
  when (val state = viewModel.uiState.collectAsStateWithLifecycle().value) {
    is BeneficiaryDataDownloadUiState.Content ->
      ContentScreen(state = state, onBack = onBack, modifier = modifier, viewModel = viewModel)
  }
}

@Composable
private fun ContentScreen(
  state: BeneficiaryDataDownloadUiState.Content,
  onBack: () -> Unit,
  modifier: Modifier,
  viewModel: BeneficiaryDataDownloadViewModel,
) {
  val listState = rememberLazyListState()

  // Alignment intent: the active row is scrolled to the top-visible position, matching the
  // Download Master Data screen's "auto-scrolls to the row currently in progress" behavior.
  LaunchedEffect(state.activeIndex) {
    if (state.activeIndex >= 0) listState.animateScrollToItem(state.activeIndex)
  }

  fun handleBack() {
    if (state.isDownloading) viewModel.onBackRequested() else onBack()
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      BrandTopAppBar(title = stringResource(R.string.beneficiary_data_download_title), onBack = ::handleBack)
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().background(NeutralG10).padding(innerPadding),
    ) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
      val doneCount = state.rows.count {
        it.status != BeneficiaryDataRowStatus.PENDING && it.status != BeneficiaryDataRowStatus.DOWNLOADING
      }

      Column(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .widthIn(max = if (isTablet) Dimens.ContentMaxWidthTablet else Dp.Unspecified)
          .fillMaxSize(),
      ) {
        DownloadProgressHeader(
          doneCount = doneCount,
          totalCount = state.rows.size,
          progressText = stringResource(R.string.beneficiary_data_download_progress, doneCount, state.rows.size),
          progressContentDescription = stringResource(
            R.string.beneficiary_data_download_progress_content_description,
            doneCount,
            state.rows.size,
          ),
          progressColor = MasterDataDownloading,
          progressTrackColor = MasterDataDownloadingTrack,
        )
        LazyColumn(
          state = listState,
          // weight(1f), not fillMaxSize(): as an unweighted Column child this LazyColumn would be
          // measured against the full incoming height in addition to the header's own height
          // above it, overflowing the screen by roughly the header's height.
          modifier = Modifier.weight(1f).padding(Dimens.ScreenPadding),
          verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
        ) {
          itemsIndexed(state.rows, key = { _, row -> row.entity.name }) { index, row ->
            BeneficiaryDataRowView(row = row, isActive = index == state.activeIndex)
          }
        }
      }
    }
  }

  if (state.showQuitConfirmation) {
    QuitConfirmationDialog(onConfirm = { viewModel.onQuitConfirmed(onBack) }, onDismiss = viewModel::onQuitDismissed)
  }

  state.networkErrorKind?.let { kind ->
    NetworkErrorDialog(kind = kind, onRetry = viewModel::onRetryClicked, onStop = viewModel::onStopClicked)
  }

  if (state.showCompletionDialog) {
    CompletionDialog(onConfirm = { viewModel.onCompletionAcknowledged(onBack) })
  }
}

@Composable
private fun BeneficiaryDataRowView(row: BeneficiaryDataRow, isActive: Boolean) {
  val (statusText, statusColor) = statusLabelAndColor(row.status)
  val cardShape = RoundedCornerShape(Dimens.SmallRadius)
  val label = stringResource(row.entity.labelRes)
  val rowContentDescription =
    stringResource(R.string.beneficiary_data_download_row_content_description, label, statusText)

  Surface(
    color = if (isActive) NeutralG10 else White,
    shape = cardShape,
    modifier = Modifier
      .fillMaxWidth()
      .softShadow(Dimens.SmallRadius)
      // Merges the row's own label + status text into one TalkBack announcement (e.g.
      // "Beneficiaries List: Completed") instead of two separate reads, and re-fires whenever the
      // status text changes so a screen reader hears each status transition.
      .clearAndSetSemantics { contentDescription = rowContentDescription }
      .testTag("beneficiary_data_download_row_${row.entity.name}"),
  ) {
    // The progress bar sits outside the text column's padding so it spans the card's full
    // width edge-to-edge, clipped to the card's own corner radius, instead of being inset
    // like the label text above it.
    Column(modifier = Modifier.fillMaxWidth().clip(cardShape)) {
      Column(modifier = Modifier.fillMaxWidth().padding(Dimens.ItemSpacing)) {
        Text(
          text = label,
          style = MaterialTheme.typography.titleMedium,
          color = NeutralG200,
        )
        Text(
          text = statusText,
          style = MaterialTheme.typography.labelLarge,
          color = statusColor,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
      }
      if (row.status == BeneficiaryDataRowStatus.DOWNLOADING) {
        // Decorative: the row's own contentDescription above already announces "Downloading" —
        // this bar carries no additional information for a screen reader.
        LinearProgressIndicator(
          color = MasterDataDownloading,
          trackColor = MasterDataDownloadingTrack,
          modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.MasterDataProgressBarHeight)
            .clearAndSetSemantics {},
        )
      }
    }
  }
}

@Composable
private fun statusLabelAndColor(status: BeneficiaryDataRowStatus): Pair<String, androidx.compose.ui.graphics.Color> =
  when (status) {
    BeneficiaryDataRowStatus.PENDING ->
      stringResource(R.string.beneficiary_data_download_status_pending) to MasterDataPending
    BeneficiaryDataRowStatus.DOWNLOADING ->
      stringResource(R.string.beneficiary_data_download_status_downloading) to MasterDataDownloading
    BeneficiaryDataRowStatus.COMPLETED ->
      stringResource(R.string.beneficiary_data_download_status_completed) to MasterDataCompleted
    BeneficiaryDataRowStatus.EMPTY ->
      stringResource(R.string.beneficiary_data_download_status_empty) to MasterDataEmpty
    // Distinct from the other "settled" states above: this row never downloaded at all, so it
    // renders in the same error color used elsewhere in this screen (e.g. the Quit confirm
    // button) instead of the neutral gray used for body text.
    BeneficiaryDataRowStatus.NOT_AVAILABLE ->
      stringResource(R.string.beneficiary_data_download_status_not_available) to RiskHigh
  }

@Composable
private fun QuitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.beneficiary_data_download_quit_title)) },
    text = { Text(stringResource(R.string.beneficiary_data_download_quit_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(R.string.beneficiary_data_download_quit_confirm), color = RiskHigh)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.beneficiary_data_download_quit_cancel)) }
    },
  )
}

@Composable
private fun NetworkErrorDialog(kind: NetworkErrorKind, onRetry: () -> Unit, onStop: () -> Unit) {
  val (titleRes, messageRes) = when (kind) {
    NetworkErrorKind.OFFLINE ->
      R.string.beneficiary_data_download_network_error_title to R.string.beneficiary_data_download_network_error_message
    NetworkErrorKind.SERVER_ERROR ->
      R.string.beneficiary_data_download_server_error_title to R.string.beneficiary_data_download_server_error_message
  }
  AlertDialog(
    onDismissRequest = onStop,
    title = { Text(stringResource(titleRes)) },
    text = { Text(stringResource(messageRes)) },
    confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.beneficiary_data_download_retry)) } },
    dismissButton = { TextButton(onClick = onStop) { Text(stringResource(R.string.beneficiary_data_download_stop)) } },
  )
}

@Composable
private fun CompletionDialog(onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onConfirm,
    title = { Text(stringResource(R.string.beneficiary_data_download_completion_title)) },
    text = { Text(stringResource(R.string.beneficiary_data_download_completion_message), color = StatusSuccess) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.beneficiary_data_download_ok)) } },
  )
}
