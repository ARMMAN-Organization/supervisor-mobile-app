package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.RiskModerate
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/**
 * One Sakhi card on the Call Sheet list screen: name + call button, then her stats table.
 * Highlighted in orange when [isRecentlyCalled] (SRS FR-SV-3.4).
 */
@Composable
fun CallSheetSakhiCard(
  summary: SakhiCallSummary,
  isRecentlyCalled: Boolean,
  onCallClick: () -> Unit,
  onCardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    border = if (isRecentlyCalled) BorderStroke(Dimens.HairlineWidth * 2, RiskModerate) else null,
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius).clickable(onClick = onCardClick),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = summary.sakhi.name,
          style = MaterialTheme.typography.titleMedium,
          color = NeutralG400,
          modifier = Modifier.weight(1f),
        )
        Surface(color = DashboardHeaderGreen, shape = CircleShape, modifier = Modifier.size(Dimens.IconButtonSize)) {
          IconButton(onClick = onCallClick) {
            Icon(
              imageVector = Icons.Filled.Call,
              contentDescription = stringResource(R.string.cd_call_sakhi),
              tint = White,
            )
          }
        }
      }
      Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.TinySpacing),
      ) {
        StatsHeaderRow()
        summary.stats.rows.forEach { StatsValueRow(it) }
        Text(
          text = "${stringResource(R.string.call_sheet_stat_last_sync_date)} ${summary.stats.lastDataSyncDate}",
          style = MaterialTheme.typography.labelSmall,
          color = NeutralG400,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
      }
    }
  }
}

@Composable
private fun StatsHeaderRow() {
  Row(
    modifier = Modifier.fillMaxWidth().background(DashboardHeaderGreen).padding(Dimens.SmallSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.call_sheet_stat_column_info),
      style = MaterialTheme.typography.labelLarge,
      color = White,
      modifier = Modifier.weight(2f),
    )
    Text(
      text = stringResource(R.string.call_sheet_stat_column_updated),
      style = MaterialTheme.typography.labelLarge,
      color = White,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = stringResource(R.string.call_sheet_stat_column_count),
      style = MaterialTheme.typography.labelLarge,
      color = White,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun StatsValueRow(value: CallSheetStatValue) {
  Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing)) {
    Text(
      text = stringResource(value.kind.labelRes()),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(2f),
    )
    Text(text = "${value.updated}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Text(text = "${value.count}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
  }
}
