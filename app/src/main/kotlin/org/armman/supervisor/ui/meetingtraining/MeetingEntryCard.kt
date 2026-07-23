package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.DashboardKpiGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.Information
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One event card on the Meeting & Training list: type badge, project, date range, remarks. */
@Composable
fun MeetingEntryCard(event: MeetingEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius).clickable(onClick = onClick),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        EventTypeBadge(eventType = event.eventType)
        Text(
          text = event.projectName,
          style = MaterialTheme.typography.bodyLarge,
          color = NeutralG400,
          modifier = Modifier.padding(start = Dimens.SmallSpacing),
        )
      }
      Text(
        text = stringResource(R.string.meeting_training_date_range, event.startDate, event.endDate),
        style = MaterialTheme.typography.bodyMedium,
        color = NeutralG200,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
      if (event.remarks.isNotBlank()) {
        Text(
          text = event.remarks,
          style = MaterialTheme.typography.bodyMedium,
          color = DashboardKpiGreen,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
      }
    }
  }
}

@Composable
private fun EventTypeBadge(eventType: EventType) {
  val (label, color) = when (eventType) {
    EventType.MEETING -> stringResource(R.string.meeting_training_chip_meeting) to DashboardKpiGreen
    EventType.TRAINING -> stringResource(R.string.meeting_training_chip_training) to Information
  }
  Surface(color = color, shape = RoundedCornerShape(Dimens.SmallRadius)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = White,
      modifier = Modifier.padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing),
    )
  }
}
