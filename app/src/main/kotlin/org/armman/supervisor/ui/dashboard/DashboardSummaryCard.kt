package org.armman.supervisor.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.StatTableCard
import org.armman.supervisor.ui.components.StatTableRow
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.DashboardPillNeutral
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.White

/** Resolves a [SummaryRowLabel] to its localized display text. */
@Composable
fun SummaryRowLabel.displayText(): String = stringResource(
  when (this) {
    SummaryRowLabel.TOTAL -> R.string.summary_row_total
    SummaryRowLabel.DUE -> R.string.summary_row_due
    SummaryRowLabel.UPCOMING -> R.string.summary_row_upcoming
    SummaryRowLabel.MISSED -> R.string.summary_row_missed
    SummaryRowLabel.COMPLETE -> R.string.summary_row_complete
    SummaryRowLabel.TARGET -> R.string.summary_row_target
    SummaryRowLabel.TOTAL_RISK -> R.string.summary_row_total_risk
  },
)

/** "<Title> + Current Month pill + Detail/Mother/Child table" card, reused for all four summary sections. */
@Composable
fun DashboardSummaryCard(title: String, rows: List<SummaryRow>, modifier: Modifier = Modifier) {
  StatTableCard(
    title = title,
    columnHeaderLabel = stringResource(R.string.table_header_detail),
    columnHeaderA = stringResource(R.string.table_header_mother),
    columnHeaderB = stringResource(R.string.table_header_child),
    rows = rows.map { StatTableRow(it.label.displayText(), it.motherValue, it.childValue) },
    headerBackgroundColor = DashboardHeaderGreen,
    headerTextColor = White,
    alternateRowColor = NeutralG50,
    textColor = NeutralG400,
    badgeText = stringResource(R.string.current_month_label),
    badgeBackgroundColor = DashboardPillNeutral,
    badgeTextColor = NeutralG400,
    modifier = modifier,
  )
}
