package org.armman.supervisor.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.AlertReportCard
import org.armman.supervisor.ui.components.ReportRow
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.RiskHigh
import org.armman.supervisor.ui.theme.White

/** Red "Sakhi not uploaded data in 7+ days" alert card — always shown, even when [entries] is empty. */
@Composable
fun DashboardStaleSakhiCard(entries: List<StaleSakhiEntry>, modifier: Modifier = Modifier) {
  AlertReportCard(
    bannerText = stringResource(R.string.stale_sakhi_banner_title),
    columnHeaderPrimary = stringResource(R.string.stale_sakhi_table_header_name),
    columnHeaderSecondary = stringResource(R.string.stale_sakhi_table_header_last_updated),
    columnHeaderTertiary = stringResource(R.string.stale_sakhi_table_header_days),
    rows = entries.map { ReportRow(it.sakhiName, it.lastUpdated, "${it.daysSinceUpdate}") },
    bannerColor = RiskHigh,
    bannerTextColor = White,
    headerTextColor = NeutralG400,
    alternateRowColor = NeutralG50,
    textColor = NeutralG400,
    modifier = modifier,
    alwaysShow = true,
  )
}
