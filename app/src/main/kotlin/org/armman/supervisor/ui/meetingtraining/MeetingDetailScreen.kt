package org.armman.supervisor.ui.meetingtraining

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.White

/** Meeting/Training Detail screen: header, Cancel/Reschedule menu, Attendance or Add Topics,
 * Pictures, Complete. Shows the Attendance action for Meetings and Add Topics for Trainings. */
@Composable
fun MeetingDetailScreen(
  onBack: () -> Unit,
  onReschedule: () -> Unit,
  onAttendance: () -> Unit,
  onAddTopics: () -> Unit,
  onGatheringAttendance: (String) -> Unit,
  onGatheringMarks: (String, MarksType) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MeetingDetailViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose { /* no-op */ }
  }

  val isTraining = (uiState as? MeetingDetailUiState.Success)?.detail?.eventType == EventType.TRAINING
  val title = if (isTraining) stringResource(R.string.training_detail_title) else stringResource(R.string.meeting_detail_title)

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = title, onBack = onBack) },
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
          onAddTopics = onAddTopics,
          onGatheringAttendance = onGatheringAttendance,
          onGatheringMarks = onGatheringMarks,
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
  onAddTopics: () -> Unit,
  onGatheringAttendance: (String) -> Unit,
  onGatheringMarks: (String, MarksType) -> Unit,
) {
  val context = LocalContext.current
  var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
  var showCompleteDialog by remember { mutableStateOf(false) }

  val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
    val path = pendingPhotoPath
    if (success && path != null) viewModel.onAddPhoto(path)
    pendingPhotoPath = null
  }

  val isEditable = state.detail.status == EventStatus.SCHEDULED

  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    MeetingHeaderCard(state = state, isEditable = isEditable, onCancel = viewModel::onCancel, onReschedule = onReschedule)
    if (state.detail.eventType == EventType.TRAINING) {
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing)) {
        OutlinedButton(
          onClick = onAddTopics,
          enabled = isEditable,
          contentPadding = PaddingValues(horizontal = Dimens.PillButtonPaddingH),
          modifier = Modifier.height(Dimens.SmallButtonHeight),
        ) {
          Text(stringResource(R.string.meeting_detail_add_topics_button))
        }
      }
      if (state.detail.gatherings.isNotEmpty()) {
        Text(
          text = stringResource(R.string.meeting_detail_gathering_dates_label),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = Dimens.ItemSpacing, bottom = Dimens.SmallSpacing),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
          state.detail.gatherings.forEach { gathering ->
            GatheringCard(
              gathering = gathering,
              enabled = isEditable,
              onAttendance = { onGatheringAttendance(gathering.gatheringId) },
              onPreMarks = { onGatheringMarks(gathering.gatheringId, MarksType.PRE) },
              onPostMarks = { onGatheringMarks(gathering.gatheringId, MarksType.POST) },
            )
          }
        }
      }
    } else {
      PrimaryButton(
        text = stringResource(R.string.meeting_detail_attendance_button),
        onClick = onAttendance,
        enabled = isEditable,
        containerColor = DashboardHeaderGreen,
        modifier = Modifier.padding(top = Dimens.ItemSpacing),
      )
    }
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
    val completeButtonText = if (state.detail.eventType == EventType.TRAINING) {
      stringResource(R.string.training_detail_complete_button)
    } else {
      stringResource(R.string.meeting_detail_complete_button)
    }
    PrimaryButton(
      text = completeButtonText,
      onClick = { showCompleteDialog = true },
      enabled = isEditable && !state.isActionInProgress,
      containerColor = DashboardHeaderGreen,
      modifier = Modifier.padding(top = Dimens.ItemSpacing),
    )
  }

  if (showCompleteDialog) {
    CompleteConfirmDialog(
      eventType = state.detail.eventType,
      onConfirm = { showCompleteDialog = false; viewModel.onComplete() },
      onDismiss = { showCompleteDialog = false },
    )
  }
}

/** One "Gathering Date" card on Training Detail: date, topic names, Attendance/Pre Marks/Post
 * Marks status rows. Pre/Post Marks are disabled once every topic's marks are locked. */
@Composable
private fun GatheringCard(
  gathering: GatheringSummary,
  enabled: Boolean,
  onAttendance: () -> Unit,
  onPreMarks: () -> Unit,
  onPostMarks: () -> Unit,
) {
  val allPreCompleted = gathering.topics.isNotEmpty() && gathering.topics.all { it.preMarksCompleted }
  val allPostCompleted = gathering.topics.isNotEmpty() && gathering.topics.all { it.postMarksCompleted }

  Surface(color = White, shape = RoundedCornerShape(Dimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Text(text = gathering.date, style = MaterialTheme.typography.bodyLarge)
      if (gathering.topics.isNotEmpty()) {
        Text(
          text = stringResource(
            R.string.meeting_detail_gathering_topics,
            gathering.topics.joinToString(", ") { it.topicName },
          ),
          style = MaterialTheme.typography.bodyMedium,
          color = NeutralG200,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
      }
      GatheringStatusRow(
        label = stringResource(R.string.meeting_detail_attendance_button),
        statusText = stringResource(
          R.string.meeting_training_attended_count_status,
          gathering.attendedCount,
          gathering.totalRosterCount,
        ),
        filled = true,
        enabled = enabled,
        onClick = onAttendance,
      )
      GatheringStatusRow(
        label = stringResource(R.string.training_detail_pre_marks_button),
        statusText = stringResource(if (allPreCompleted) R.string.status_completed else R.string.status_pending),
        filled = false,
        enabled = enabled && !allPreCompleted,
        onClick = onPreMarks,
      )
      GatheringStatusRow(
        label = stringResource(R.string.training_detail_post_marks_button),
        statusText = stringResource(if (allPostCompleted) R.string.status_completed else R.string.status_pending),
        filled = false,
        enabled = enabled && !allPostCompleted,
        onClick = onPostMarks,
      )
    }
  }
}

@Composable
private fun GatheringStatusRow(label: String, statusText: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing)
      .semantics { role = Role.Button }
      .clickable(enabled = enabled, onClick = onClick),
  ) {
    if (filled) {
      Surface(color = DashboardHeaderGreen, shape = RoundedCornerShape(Dimens.TileRadius)) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = White,
          modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = Dimens.SmallSpacing),
        )
      }
    } else {
      Surface(
        color = White,
        shape = RoundedCornerShape(Dimens.TileRadius),
        border = BorderStroke(Dimens.HairlineWidth, NeutralG50),
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = DashboardHeaderGreen,
          modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = Dimens.SmallSpacing),
        )
      }
    }
    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = NeutralG200)
  }
}
