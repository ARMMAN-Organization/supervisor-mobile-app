package org.armman.supervisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One row in a [GroupedListCard] — a leading icon, a label, an optional chevron, and a click action. */
data class GroupedListRowItem(
  val label: String,
  val icon: @Composable () -> Unit,
  val showChevron: Boolean = true,
  val onClick: (() -> Unit)? = null,
)

/**
 * A section label above a white card of [rows], each separated by a hairline divider (none after
 * the last row). Reusable for any grouped-list-style screen (Settings today, others later).
 */
@Composable
fun GroupedListCard(title: String, rows: List<GroupedListRowItem>, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = NeutralG400,
      modifier = Modifier.padding(bottom = Dimens.SmallSpacing),
    )
    Surface(
      color = White,
      shape = RoundedCornerShape(Dimens.CardRadius),
      modifier = Modifier.softShadow(Dimens.CardRadius),
    ) {
      Column {
        rows.forEachIndexed { index, row ->
          GroupedListRow(row)
          if (index != rows.lastIndex) {
            Surface(color = NeutralG50, modifier = Modifier.fillMaxWidth().height(Dimens.HairlineWidth)) {}
          }
        }
      }
    }
  }
}

@Composable
private fun GroupedListRow(row: GroupedListRowItem) {
  val clickModifier = row.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(clickModifier)
      .padding(horizontal = Dimens.TilePadding, vertical = Dimens.ItemSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    row.icon()
    Text(
      text = row.label,
      style = MaterialTheme.typography.bodyLarge,
      color = NeutralG400,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).padding(start = Dimens.ItemSpacing),
    )
    if (row.showChevron) {
      Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NeutralG400)
    }
  }
}
