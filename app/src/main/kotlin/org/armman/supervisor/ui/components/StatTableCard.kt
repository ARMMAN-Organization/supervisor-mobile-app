package org.armman.supervisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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

/** One row of a [StatTableCard] — a label plus two numeric columns (e.g. Mother/Child counts). */
data class StatTableRow(val label: String, val valueA: Int, val valueB: Int)

/**
 * Reusable "title + optional badge pill + 3-column (label/A/B) table" card. Powers all four
 * dashboard summary sections and is generic enough for any future label+two-number report.
 */
@Composable
fun StatTableCard(
  title: String,
  columnHeaderLabel: String,
  columnHeaderA: String,
  columnHeaderB: String,
  rows: List<StatTableRow>,
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
        modifier = Modifier.fillMaxWidth().height(Dimens.TableRowHeight).background(headerBackgroundColor),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TableCell(columnHeaderLabel, weight = 2f, color = headerTextColor)
        TableCell(columnHeaderA, weight = 1f, color = headerTextColor)
        TableCell(columnHeaderB, weight = 1f, color = headerTextColor)
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
          TableCell("${row.valueA}", weight = 1f, color = textColor)
          TableCell("${row.valueB}", weight = 1f, color = textColor)
        }
      }
    }
  }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, color: Color) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    modifier = Modifier.weight(weight).padding(horizontal = Dimens.SmallSpacing),
    textAlign = if (weight == 2f) TextAlign.Start else TextAlign.Center,
  )
}
