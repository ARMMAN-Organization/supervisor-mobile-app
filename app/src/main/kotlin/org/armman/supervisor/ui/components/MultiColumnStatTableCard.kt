package org.armman.supervisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.SerifTitle
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One row of a [MultiColumnStatTableCard] — a label plus any number of numeric columns. */
data class MultiColumnStatRow(val label: String, val values: List<Int>)

/**
 * Generalization of [StatTableCard] for tables with more than two numeric columns (e.g. Visit
 * Summary's Village/Total/Due/Missed). The label column plus each entry in [columnHeaders]
 * share the row width equally amongst the value columns; the label column keeps a fixed 2x
 * weight, matching [StatTableCard]'s proportions.
 */
@Composable
fun MultiColumnStatTableCard(
  title: String,
  columnHeaderLabel: String,
  columnHeaders: List<String>,
  rows: List<MultiColumnStatRow>,
  headerBackgroundColor: Color,
  headerTextColor: Color,
  alternateRowColor: Color,
  textColor: Color,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  badgeBackgroundColor: Color = Color.Unspecified,
  badgeTextColor: Color = Color.Unspecified,
) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = SerifTitle, color = textColor, modifier = Modifier.weight(1f))
        if (badgeText != null) {
          Surface(color = badgeBackgroundColor, shape = RoundedCornerShape(50)) {
            Text(
              text = badgeText,
              style = MaterialTheme.typography.labelMedium,
              color = badgeTextColor,
              modifier = Modifier.padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing),
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(Dimens.SmallSpacing))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = Dimens.TableRowHeight)
          .background(headerBackgroundColor),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TableCell(columnHeaderLabel, weight = 2f, color = headerTextColor, singleLine = false)
        columnHeaders.forEach { header -> TableCell(header, weight = 1f, color = headerTextColor, singleLine = false) }
      }
      rows.forEachIndexed { index, row ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.TableRowHeight)
            .background(if (index % 2 == 1) alternateRowColor else White),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TableCell(row.label, weight = 2f, color = textColor)
          row.values.forEach { value -> TableCell("$value", weight = 1f, color = textColor) }
        }
      }
    }
  }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, color: Color, singleLine: Boolean = true) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
    modifier = Modifier
      .weight(weight)
      .padding(horizontal = Dimens.ExtraSmallSpacing, vertical = Dimens.ExtraSmallSpacing),
    textAlign = if (weight == 2f) TextAlign.Start else TextAlign.Center,
  )
}
