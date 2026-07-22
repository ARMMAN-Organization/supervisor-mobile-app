package org.armman.supervisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.style.TextOverflow
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One row of an [AlertReportCard] — three text columns (e.g. name / last-updated / days). */
data class ReportRow(val primary: String, val secondary: String, val tertiary: String)

/**
 * Reusable "colored alert banner + 3-column table" card. By default hidden entirely when [rows]
 * is empty (an empty alert would misleadingly still read as a warning); pass [alwaysShow] = true
 * to keep the banner and column headers visible with no rows underneath (e.g. a persistent
 * dashboard section that should always be present). Powers the stale-Sakhi report and is generic
 * enough for any future alert-style report.
 */
@Composable
fun AlertReportCard(
  bannerText: String,
  columnHeaderPrimary: String,
  columnHeaderSecondary: String,
  columnHeaderTertiary: String,
  rows: List<ReportRow>,
  bannerColor: Color,
  bannerTextColor: Color,
  headerTextColor: Color,
  alternateRowColor: Color,
  textColor: Color,
  modifier: Modifier = Modifier,
  alwaysShow: Boolean = false,
) {
  if (rows.isEmpty() && !alwaysShow) return

  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column {
      Surface(color = bannerColor, shape = RoundedCornerShape(topStart = Dimens.CardRadius, topEnd = Dimens.CardRadius)) {
        Text(
          text = bannerText,
          style = MaterialTheme.typography.titleMedium,
          color = bannerTextColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(Dimens.ItemSpacing),
        )
      }
      Row(modifier = Modifier.fillMaxWidth().height(Dimens.TableRowHeight), verticalAlignment = Alignment.CenterVertically) {
        ReportCell(columnHeaderPrimary, weight = 2f, color = headerTextColor)
        ReportCell(columnHeaderSecondary, weight = 1.5f, color = headerTextColor)
        ReportCell(columnHeaderTertiary, weight = 1f, color = headerTextColor)
      }
      rows.forEachIndexed { index, row ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.TableRowHeight)
            .background(if (index % 2 == 1) alternateRowColor else White),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ReportCell(row.primary, weight = 2f, color = textColor)
          ReportCell(row.secondary, weight = 1.5f, color = textColor)
          ReportCell(row.tertiary, weight = 1f, color = textColor)
        }
      }
    }
  }
}

@Composable
private fun RowScope.ReportCell(text: String, weight: Float, color: Color) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    textAlign = if (weight == 1f) TextAlign.Center else TextAlign.Start,
    modifier = Modifier.weight(weight).padding(horizontal = Dimens.SmallSpacing),
  )
}
