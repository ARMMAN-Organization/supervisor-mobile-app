package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White

/** Meeting Detail's header card: project, dates, attendance count, remarks, Cancel/Reschedule menu. */
@Composable
fun MeetingHeaderCard(
  state: MeetingDetailUiState.Success,
  isEditable: Boolean,
  onCancel: () -> Unit,
  onReschedule: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Surface(color = White, shape = RoundedCornerShape(Dimens.CardRadius), modifier = modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(text = state.detail.projectName, style = MaterialTheme.typography.titleMedium, color = NeutralG400)
        Box {
          IconButton(enabled = isEditable, onClick = { menuExpanded = true }) {
            Icon(
              Icons.Filled.MoreVert,
              contentDescription = stringResource(R.string.cd_transaction_overflow_menu),
              tint = NeutralG400,
            )
          }
          DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.meeting_detail_action_cancel)) },
              onClick = { menuExpanded = false; onCancel() },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.meeting_detail_action_reschedule)) },
              onClick = { menuExpanded = false; onReschedule() },
            )
          }
        }
      }
      MeetingHeaderRow(stringResource(R.string.meeting_detail_start_date), state.detail.startDate)
      MeetingHeaderRow(stringResource(R.string.meeting_detail_end_date), state.detail.endDate)
      if (state.detail.eventType == EventType.MEETING) {
        MeetingHeaderRow(
          stringResource(R.string.meeting_detail_sakhi_attended),
          stringResource(R.string.meeting_training_attended_count, state.detail.attendedCount, state.detail.totalRosterCount),
        )
      }
      if (state.detail.remarks.isNotBlank()) {
        Text(
          text = state.detail.remarks,
          style = MaterialTheme.typography.bodyMedium,
          color = DashboardHeaderGreen,
          modifier = Modifier.padding(top = Dimens.SmallSpacing),
        )
      }
    }
  }
}

@Composable
private fun MeetingHeaderRow(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = Dimens.TinySpacing)) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeutralG400)
    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = NeutralG400)
  }
}

@Composable
fun CompleteConfirmDialog(eventType: EventType, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  val titleRes = if (eventType == EventType.TRAINING) {
    R.string.training_detail_complete_dialog_title
  } else {
    R.string.meeting_detail_complete_dialog_title
  }
  val messageRes = if (eventType == EventType.TRAINING) {
    R.string.training_detail_complete_dialog_message
  } else {
    R.string.meeting_detail_complete_dialog_message
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(titleRes)) },
    text = { Text(stringResource(messageRes)) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.meeting_detail_complete_confirm)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
  )
}
