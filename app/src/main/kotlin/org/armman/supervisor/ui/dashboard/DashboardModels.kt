package org.armman.supervisor.ui.dashboard

import org.armman.supervisor.ui.notifications.AppNotification

/** Today's headline counts shown in the header stat row. */
data class KpiSummary(val dueVisit: Int, val mother: Int, val child: Int, val monitor: Int)

/**
 * Fixed vocabulary for summary-table row labels — kept as an enum (not raw String) so the
 * label text is resolved from `strings.xml` (EN + MR) at display time instead of being
 * hardcoded UI text carried through the data layer.
 */
enum class SummaryRowLabel { TOTAL, DUE, UPCOMING, MISSED, COMPLETE, TARGET, TOTAL_RISK }

/** One row of a Mother/Child summary table (e.g. "Total", "Due", "Target"). */
data class SummaryRow(val label: SummaryRowLabel, val motherValue: Int, val childValue: Int)

/** One row of the "not uploaded in 3+ days" report. */
data class StaleSakhiEntry(val sakhiName: String, val lastUpdated: String, val daysSinceUpdate: Int)

/** Full payload for the Supervisor dashboard, scoped to one selected location.
 * [newlyDetectedNotifications] are notifications never seen before this exact call (see
 * `NotificationsSeenStore`) — each one is marked seen as part of producing this list, so a
 * notification is reported here at most once, ever, regardless of read/unread status or how many
 * times the Dashboard polls afterward. The Dashboard plays a sound per entry here; it does not
 * render them. Distinct from [unreadNotificationCount], which is the bell icon's badge count and
 * doesn't reset once a notification has been surfaced this way. */
data class DashboardData(
  val supervisorName: String,
  val roleLabel: String,
  val date: String,
  val unreadNotificationCount: Int,
  val newlyDetectedNotifications: List<AppNotification>,
  val kpi: KpiSummary,
  val visitSummary: List<SummaryRow>,
  val registrationSummary: List<SummaryRow>,
  val riskSummary: List<SummaryRow>,
  val monitoringSummary: List<SummaryRow>,
  val staleSakhis: List<StaleSakhiEntry>,
)
