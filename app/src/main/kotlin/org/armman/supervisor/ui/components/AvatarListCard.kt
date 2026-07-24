package org.armman.supervisor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One row in an [AvatarListCard] — a circular avatar icon, a name, and a click action. */
data class AvatarListRowItem(val label: String, val onClick: (() -> Unit)? = null)

/**
 * A vertical list of separate white cards, each showing a circular avatar + name. Reusable for
 * any "pick a person" list (e.g. Assign Item's Sakhi list).
 */
@Composable
fun AvatarListCard(rows: List<AvatarListRowItem>, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
    rows.forEach { row -> AvatarListRow(row) }
  }
}

@Composable
private fun AvatarListRow(row: AvatarListRowItem) {
  val clickModifier = row.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius).then(clickModifier),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.TilePadding, vertical = Dimens.SmallSpacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        painter = painterResource(R.drawable.arogya_sakhi),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(Dimens.AvatarSize).clip(CircleShape),
      )
      Text(
        text = row.label,
        style = MaterialTheme.typography.bodyLarge,
        color = NeutralG400,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = Dimens.ItemSpacing),
      )
    }
  }
}
