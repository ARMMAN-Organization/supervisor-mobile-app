package org.armman.supervisor.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.components.CircleIconButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.components.StatItem
import org.armman.supervisor.ui.components.StatRow
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.DashboardKpiGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.KpiNumber
import org.armman.supervisor.ui.theme.RiskModerate
import org.armman.supervisor.ui.theme.White

/**
 * Dark-green header: profile row (name/role/date + icon buttons), location selector and the
 * KPI stat row. Always stacks the location selector under the name row on both form factors —
 * an inline-next-to-name arrangement was tried for tablet but crushed the name column to ~80dp
 * on devices near the tablet threshold width (~600-650dp), wrapping "Niharika Supervisor" onto
 * two lines. Confirmed via on-device bounds inspection; see plan history.
 *
 * In landscape, screen height is scarce, so the same stacked structure is kept but padding,
 * inter-row spacing and icon-button size shrink to [Dimens.ScreenPaddingCompact]/
 * [Dimens.TinySpacing]/[Dimens.IconButtonSizeCompact] — applies identically on phone and
 * tablet landscape. Portrait (either form factor) is unaffected.
 */
@Composable
fun DashboardHeaderSection(
  data: DashboardData,
  locations: List<LocationOption>,
  selectedLocationId: String?,
  onLocationSelected: (String) -> Unit,
  onProfileClick: () -> Unit,
  onNotificationsClick: () -> Unit,
  onSettingsClick: () -> Unit,
  isTablet: Boolean,
  isLandscape: Boolean,
  modifier: Modifier = Modifier,
) {
  val selectedLocation = locations.firstOrNull { it.id == selectedLocationId }
  val verticalPadding = if (isLandscape) Dimens.ScreenPaddingCompact else Dimens.ScreenPadding
  val rowSpacing = if (isLandscape) Dimens.TinySpacing else Dimens.ItemSpacing
  val iconButtonSize = if (isLandscape) Dimens.IconButtonSizeCompact else Dimens.IconButtonSize

  Surface(color = DashboardHeaderGreen, modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = verticalPadding),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = data.supervisorName, style = MaterialTheme.typography.titleLarge, color = White)
          Text(text = data.roleLabel, style = MaterialTheme.typography.bodyMedium, color = White.copy(alpha = 0.8f))
          Text(text = data.date, style = MaterialTheme.typography.labelSmall, color = White.copy(alpha = 0.7f))
        }
        HeaderIconButtons(
          unsyncedCount = data.unsyncedCount,
          onProfileClick = onProfileClick,
          onNotificationsClick = onNotificationsClick,
          onSettingsClick = onSettingsClick,
          iconButtonSize = iconButtonSize,
        )
      }

      Spacer(modifier = Modifier.height(rowSpacing))
      LocationDropdown(
        locations = locations,
        selectedLocation = selectedLocation,
        onLocationSelected = onLocationSelected,
        modifier = if (isTablet) Modifier.width(Dimens.SearchBarWidthTablet) else Modifier.fillMaxWidth(),
      )

      Spacer(modifier = Modifier.height(rowSpacing))
      StatRow(
        items = listOf(
          StatItem("${data.kpi.dueVisit}", stringResource(R.string.kpi_due_visit)),
          StatItem("${data.kpi.mother}", stringResource(R.string.kpi_mother)),
          StatItem("${data.kpi.child}", stringResource(R.string.kpi_child)),
          StatItem("${data.kpi.monitor}", stringResource(R.string.kpi_monitor)),
        ),
        valueStyle = KpiNumber,
        valueColor = DashboardKpiGreen,
        labelStyle = MaterialTheme.typography.labelSmall,
        labelColor = White.copy(alpha = 0.8f),
        dividerColor = White.copy(alpha = 0.3f),
      )
    }
  }
}

@Composable
private fun LocationDropdown(
  locations: List<LocationOption>,
  selectedLocation: LocationOption?,
  onLocationSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleSelectDropdown(
    options = locations,
    selected = selectedLocation,
    optionLabel = { it.name },
    placeholder = stringResource(
      if (locations.isEmpty()) R.string.location_selector_empty else R.string.location_selector_placeholder,
    ),
    onSelected = { onLocationSelected(it.id) },
    modifier = modifier,
  )
}

@Composable
private fun HeaderIconButtons(
  unsyncedCount: Int,
  onProfileClick: () -> Unit,
  onNotificationsClick: () -> Unit,
  onSettingsClick: () -> Unit,
  iconButtonSize: Dp,
) {
  Row {
    CircleIconButton(onClick = onProfileClick, size = iconButtonSize) {
      Image(
        painter = painterResource(R.drawable.arogya_sakhi),
        contentDescription = stringResource(R.string.cd_profile_icon),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(iconButtonSize).clip(CircleShape),
      )
    }
    Spacer(modifier = Modifier.width(Dimens.SmallSpacing))
    CircleIconButton(onClick = onNotificationsClick, size = iconButtonSize) {
      BadgedBox(badge = {
        if (unsyncedCount > 0) Badge(containerColor = RiskModerate) { Text("$unsyncedCount") }
      }) {
        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.cd_notifications_icon))
      }
    }
    Spacer(modifier = Modifier.width(Dimens.SmallSpacing))
    CircleIconButton(onClick = onSettingsClick, size = iconButtonSize) {
      Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings_icon))
    }
  }
}
