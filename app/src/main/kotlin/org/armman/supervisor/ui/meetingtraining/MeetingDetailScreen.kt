package org.armman.supervisor.ui.meetingtraining

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens

/** Meeting Detail screen: header, Cancel/Reschedule menu, Attendance, Pictures, Complete. */
@Composable
fun MeetingDetailScreen(
  onBack: () -> Unit,
  onReschedule: () -> Unit,
  onAttendance: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MeetingDetailViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose { /* no-op */ }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.meeting_detail_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is MeetingDetailUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is MeetingDetailUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is MeetingDetailUiState.Success -> SuccessContent(
          state = state,
          viewModel = viewModel,
          onReschedule = onReschedule,
          onAttendance = onAttendance,
        )
      }
    }
  }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(
  state: MeetingDetailUiState.Success,
  viewModel: MeetingDetailViewModel,
  onReschedule: () -> Unit,
  onAttendance: () -> Unit,
) {
  val context = LocalContext.current
  var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
  var showCompleteDialog by remember { mutableStateOf(false) }

  val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
    val path = pendingPhotoPath
    if (success && path != null) viewModel.onAddPhoto(path)
    pendingPhotoPath = null
  }

  val isEditable = state.detail.status == EventStatus.SCHEDULED

  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    MeetingHeaderCard(state = state, isEditable = isEditable, onCancel = viewModel::onCancel, onReschedule = onReschedule)
    PrimaryButton(
      text = stringResource(R.string.meeting_detail_attendance_button),
      onClick = onAttendance,
      enabled = isEditable,
      containerColor = DashboardHeaderGreen,
      modifier = Modifier.padding(top = Dimens.ItemSpacing),
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing),
    ) {
      Text(text = stringResource(R.string.meeting_detail_pictures_label), style = MaterialTheme.typography.titleMedium)
      IconButton(
        enabled = isEditable,
        onClick = {
          val (path, uri) = createEventPhotoFile(context, state.detail.id)
          pendingPhotoPath = path
          takePicture.launch(uri)
        },
      ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.cd_add_photo))
      }
    }
    if (state.detail.photoPaths.isNotEmpty()) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
        items(state.detail.photoPaths) { path -> EventPhotoThumbnail(filePath = path) }
      }
    }
    if (state.completeBlockedNoPhoto) {
      Text(
        text = stringResource(R.string.meeting_detail_complete_no_photo),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
    }
    PrimaryButton(
      text = stringResource(R.string.meeting_detail_complete_button),
      onClick = { showCompleteDialog = true },
      enabled = isEditable && !state.isActionInProgress,
      containerColor = DashboardHeaderGreen,
      modifier = Modifier.padding(top = Dimens.ItemSpacing),
    )
  }

  if (showCompleteDialog) {
    CompleteConfirmDialog(
      onConfirm = { showCompleteDialog = false; viewModel.onComplete() },
      onDismiss = { showCompleteDialog = false },
    )
  }
}
