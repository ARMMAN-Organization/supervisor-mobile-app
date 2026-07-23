package org.armman.supervisor.ui.assignitem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG100
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG75
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/**
 * "All Items" list grouped by category, each item with a quantity input. Categories with no items
 * are omitted. Quantities are held by the caller ([quantities]); a blank/invalid entry means 0.
 */
@Composable
fun ItemQuantityList(
  items: List<InventoryItem>,
  quantities: Map<String, Int>,
  onQuantityChanged: (itemId: String, quantity: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
    Text(
      text = stringResource(R.string.assign_item_all_items_label_plain),
      style = MaterialTheme.typography.titleMedium,
      color = NeutralG400,
    )
    ItemCategory.entries.forEach { category ->
      val categoryItems = items.filter { it.category == category }
      if (categoryItems.isNotEmpty()) {
        CategoryCard(title = stringResource(category.labelRes()), items = categoryItems, quantities = quantities, onQuantityChanged = onQuantityChanged)
      }
    }
  }
}

@Composable
private fun CategoryCard(
  title: String,
  items: List<InventoryItem>,
  quantities: Map<String, Int>,
  onQuantityChanged: (itemId: String, quantity: Int) -> Unit,
) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column {
      Surface(color = NeutralG200, shape = RoundedCornerShape(topStart = Dimens.CardRadius, topEnd = Dimens.CardRadius)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = White,
          modifier = Modifier.fillMaxWidth().padding(Dimens.SmallSpacing),
        )
      }
      Column(modifier = Modifier.padding(Dimens.TilePadding), verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
        items.forEach { item ->
          ItemQuantityRow(
            name = item.name,
            quantity = quantities[item.id],
            onQuantityChanged = { qty -> onQuantityChanged(item.id, qty) },
          )
        }
      }
    }
  }
}

@Composable
private fun ItemQuantityRow(name: String, quantity: Int?, onQuantityChanged: (Int) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = name,
      style = MaterialTheme.typography.bodyLarge,
      color = NeutralG400,
      modifier = Modifier.weight(1f),
    )
    val quantityFieldShape = RoundedCornerShape(Dimens.InputFieldRadius)
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .width(Dimens.QuantityFieldWidth)
        .height(Dimens.QuantityFieldHeight)
        .clip(quantityFieldShape)
        .background(White)
        .border(Dimens.HairlineWidth, NeutralG75, quantityFieldShape)
        .padding(horizontal = Dimens.SmallSpacing),
    ) {
      BasicTextField(
        value = quantity?.toString() ?: "",
        onValueChange = { raw -> onQuantityChanged(raw.filter { it.isDigit() }.toIntOrNull() ?: 0) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = NeutralG400, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { innerField ->
          if (quantity == null) {
            Text(
              text = stringResource(R.string.assign_item_quantity_hint),
              style = MaterialTheme.typography.labelLarge,
              color = NeutralG100,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
            )
          }
          innerField()
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
