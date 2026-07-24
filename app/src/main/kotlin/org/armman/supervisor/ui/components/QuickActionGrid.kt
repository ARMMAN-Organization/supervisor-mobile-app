package org.armman.supervisor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.armman.supervisor.ui.theme.Dimens

/** One tile in a [QuickActionGrid] — a circular icon, a label and a click action. */
data class ActionItem(val label: String, val onClick: () -> Unit, val icon: @Composable () -> Unit)

/**
 * Adaptive grid of icon+label action tiles: 2x2 on mobile, a single row on tablet. Reusable for
 * any icon-menu screen (dashboard quick actions today, other action menus later).
 */
@Composable
fun QuickActionGrid(
  actions: List<ActionItem>,
  isTablet: Boolean,
  iconSurfaceColor: Color,
  labelColor: Color,
  modifier: Modifier = Modifier,
) {
  if (isTablet) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      actions.forEach { QuickActionTile(it, iconSurfaceColor, labelColor) }
    }
  } else {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)) {
      actions.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
          pair.forEach { QuickActionTile(it, iconSurfaceColor, labelColor) }
        }
      }
    }
  }
}

@Composable
private fun QuickActionTile(action: ActionItem, iconSurfaceColor: Color, labelColor: Color) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = action.onClick),
  ) {
    Surface(shape = CircleShape, color = iconSurfaceColor, modifier = Modifier.size(Dimens.QuickActionIconSize)) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Dimens.QuickActionIconSize)) {
        action.icon()
      }
    }
    Text(
      text = action.label,
      style = MaterialTheme.typography.labelLarge,
      color = labelColor,
      modifier = Modifier.padding(top = Dimens.SmallSpacing),
    )
  }
}
