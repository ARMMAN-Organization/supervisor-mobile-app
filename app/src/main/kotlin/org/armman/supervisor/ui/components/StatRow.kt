package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.armman.supervisor.ui.theme.Dimens

/** One value+label pair in a [StatRow] (e.g. a KPI number and its caption). */
data class StatItem(val value: String, val label: String)

/**
 * A row of stat items separated by hairline dividers — reusable for any KPI-style stat strip,
 * not just the dashboard header.
 */
@Composable
fun StatRow(
  items: List<StatItem>,
  valueStyle: TextStyle,
  valueColor: Color,
  labelStyle: TextStyle,
  labelColor: Color,
  dividerColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
    items.forEachIndexed { index, item ->
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = item.value, style = valueStyle, color = valueColor)
        Text(
          text = item.label,
          style = labelStyle,
          color = labelColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (index != items.lastIndex) {
        Surface(color = dividerColor, modifier = Modifier.width(Dimens.HairlineWidth).height(Dimens.AvatarSize)) {}
      }
    }
  }
}
