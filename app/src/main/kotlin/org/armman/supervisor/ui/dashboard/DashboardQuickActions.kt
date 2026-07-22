package org.armman.supervisor.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.ActionItem
import org.armman.supervisor.ui.components.QuickActionGrid
import org.armman.supervisor.ui.navigation.Routes
import org.armman.supervisor.ui.theme.DashboardActionSurface
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/**
 * The 4 dashboard quick-action tiles (Items / Meeting & Training / Call Sheet / Quick Response),
 * in a card matching [DashboardSummaryCard]'s surface/radius/shadow for visual consistency.
 */
@Composable
fun DashboardQuickActions(onNavigate: (String) -> Unit, isTablet: Boolean, modifier: Modifier = Modifier) {
  val actions = listOf(
    ActionItem(stringResource(R.string.quick_action_items), { onNavigate(Routes.ITEMS) }) {
      Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = DashboardHeaderGreen)
    },
    ActionItem(stringResource(R.string.quick_action_meeting_training), { onNavigate(Routes.MEETING_TRAINING) }) {
      Icon(Icons.Filled.Groups, contentDescription = null, tint = DashboardHeaderGreen)
    },
    ActionItem(stringResource(R.string.quick_action_call_sheet), { onNavigate(Routes.CALL_SHEET) }) {
      Icon(painterResource(R.drawable.ic_file_text), contentDescription = null, tint = DashboardHeaderGreen)
    },
    ActionItem(stringResource(R.string.quick_action_quick_response), { onNavigate(Routes.QUICK_RESPONSE) }) {
      Icon(Icons.Filled.RocketLaunch, contentDescription = null, tint = DashboardHeaderGreen)
    },
  )

  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    QuickActionGrid(
      actions = actions,
      isTablet = isTablet,
      iconSurfaceColor = DashboardActionSurface,
      labelColor = NeutralG400,
      modifier = Modifier.padding(Dimens.TilePadding),
    )
  }
}
