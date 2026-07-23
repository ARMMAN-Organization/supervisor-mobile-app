package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One line item within a [TransactionCard] (e.g. "Sugar strips", "Qty: 20"). */
data class TransactionItemRow(val itemName: String, val quantityLabel: String)

/**
 * A single transaction's card: type + an Edit/Delete overflow menu, then an "All Items" list.
 */
@Composable
fun TransactionCard(
  transactionTypeLabel: String,
  allItemsLabel: String,
  items: List<TransactionItemRow>,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
          text = transactionTypeLabel,
          style = MaterialTheme.typography.bodyLarge,
          color = NeutralG400,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        OverflowMenu(onEdit = onEdit, onDelete = onDelete)
      }
      Text(
        text = allItemsLabel,
        style = MaterialTheme.typography.bodyLarge,
        color = NeutralG400,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
      items.forEach { item ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(text = item.itemName, style = MaterialTheme.typography.bodyMedium, color = NeutralG200)
          Text(text = item.quantityLabel, style = MaterialTheme.typography.bodyMedium, color = NeutralG200)
        }
      }
    }
  }
}

@Composable
private fun OverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  // Box anchors the menu directly under the 3-dot icon instead of the screen's left edge.
  Box {
    IconButton(onClick = { expanded = true }) {
      Icon(
        Icons.Filled.MoreVert,
        contentDescription = stringResource(R.string.cd_transaction_overflow_menu),
        tint = NeutralG400,
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_edit)) },
        onClick = {
          expanded = false
          onEdit()
        },
      )
      DropdownMenuItem(
        text = { Text(stringResource(R.string.action_delete)) },
        onClick = {
          expanded = false
          onDelete()
        },
      )
    }
  }
}
