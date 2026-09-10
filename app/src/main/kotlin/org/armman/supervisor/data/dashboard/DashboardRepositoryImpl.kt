package org.armman.supervisor.data.dashboard

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.notifications.NotificationsSeenStore
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.dashboard.DashboardData
import org.armman.supervisor.ui.dashboard.DashboardRepository
import org.armman.supervisor.ui.dashboard.KpiSummary
import org.armman.supervisor.ui.dashboard.SummaryRow
import org.armman.supervisor.ui.dashboard.SummaryRowLabel
import org.armman.supervisor.ui.notifications.AppNotification
import org.armman.supervisor.ui.notifications.NotificationStatus
import org.armman.supervisor.ui.notifications.NotificationsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val VISIT_STATUS_PENDING = "PENDING"
private const val VISIT_STATUS_MISSED = "MISSED"
private const val VISIT_STATUS_COMPLETED = "COMPLETED"
private const val LOG_TAG = "DashboardRepositoryImpl"

/**
 * Concrete [DashboardRepository]. The Supervisor's own name comes from the logged-in session,
 * locations from [ProjectsRepository]. KPI/summary numbers come from the aggregate summary
 * endpoints via [DashboardApi] — these are project-wide totals, not scoped by [getDashboard]'s
 * `locationId` (the three summary endpoints take no project filter), so switching the location
 * selector does not currently change these numbers. `kpi.monitor`,
 * `monitoringSummary` and `staleSakhis` have no backing endpoint yet and stay at 0/empty.
 */
class DashboardRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val sessionStore: SessionStore,
  private val api: DashboardApi,
  private val notificationsRepository: NotificationsRepository,
  private val notificationsSeenStore: NotificationsSeenStore,
) : DashboardRepository {

  private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.getDefault())

  // Guards detectAndMarkSeen's read-then-write over notificationsSeenStore: the polling job and
  // a location-switch/refresh job can both call getDashboard() around the same time, and without
  // this lock both could read the same notification id as unseen before either commits markSeen,
  // reporting it as newly-detected twice (a duplicate notification sound for one notification).
  private val notificationsSeenMutex = Mutex()

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getDashboard(locationId: String?): DashboardData {
    val registrationSummary = fetchRegistrationSummary()
    val riskSummary = fetchRiskSummary()
    val visitSummary = fetchVisitSummary()
    val notifications = runCatching { notificationsRepository.getNotifications() }
      .onFailure { Log.w(LOG_TAG, "Failed to load notifications", it) }
      .getOrDefault(emptyList())
    val unreadNotificationCount = notifications.count { it.status == NotificationStatus.UNREAD }
    val newlyDetectedNotifications = detectAndMarkSeen(notifications)

    val dueVisit = visitSummary.byStatus[VISIT_STATUS_PENDING] ?: 0
    val missedVisit = visitSummary.byStatus[VISIT_STATUS_MISSED] ?: 0
    val completeVisit = visitSummary.byStatus[VISIT_STATUS_COMPLETED] ?: 0

    return DashboardData(
      supervisorName = sessionStore.readSession()?.displayName.orEmpty(),
      roleLabel = "Field Supervisor",
      date = LocalDate.now().format(dateFormatter),
      unreadNotificationCount = unreadNotificationCount,
      newlyDetectedNotifications = newlyDetectedNotifications,
      kpi = KpiSummary(
        dueVisit = dueVisit,
        mother = registrationSummary.motherCount,
        child = registrationSummary.childCount,
        monitor = 0,
      ),
      visitSummary = listOf(
        SummaryRow(SummaryRowLabel.TOTAL, visitSummary.total, visitSummary.total),
        SummaryRow(SummaryRowLabel.DUE, dueVisit, dueVisit),
        SummaryRow(SummaryRowLabel.UPCOMING, 0, 0),
        SummaryRow(SummaryRowLabel.MISSED, missedVisit, missedVisit),
        SummaryRow(SummaryRowLabel.COMPLETE, completeVisit, completeVisit),
      ),
      registrationSummary = listOf(
        SummaryRow(SummaryRowLabel.TARGET, 0, 0),
        SummaryRow(
          SummaryRowLabel.COMPLETE,
          registrationSummary.motherCount,
          registrationSummary.childCount,
        ),
      ),
      riskSummary = listOf(
        SummaryRow(SummaryRowLabel.TOTAL_RISK, riskSummary.everAtRiskCount, riskSummary.everAtRiskCount),
      ),
      monitoringSummary = listOf(SummaryRow(SummaryRowLabel.TOTAL, 0, 0)),
      staleSakhis = emptyList(),
    )
  }

  /** Which of [notifications] have never been marked seen before, marking each one seen as it's
   * found (so a notification is reported here at most once, ever). On the very first call this
   * store has ever made, the whole backlog is marked seen silently and nothing is reported —
   * only notifications that arrive after that point are ever treated as "new". [notificationsSeenMutex]
   * serializes the whole read-then-write sequence so two concurrent callers (poll job + a
   * location-switch/refresh job) can't both read the same id as unseen before either commits
   * [NotificationsSeenStore.markSeen]. [NotificationsSeenStore.markRunStarted] is called
   * unconditionally (not only as a side effect of marking an id seen) so a caller whose
   * [notifications] happens to be empty still flips [NotificationsSeenStore.isFirstRun] to false —
   * otherwise a user with no notifications for a while would have it stuck true, and their actual
   * first real notification would be wrongly treated as backlog. */
  private suspend fun detectAndMarkSeen(notifications: List<AppNotification>): List<AppNotification> =
    notificationsSeenMutex.withLock {
      val isFirstRun = notificationsSeenStore.isFirstRun()
      notificationsSeenStore.markRunStarted()
      val newlyDetected = notifications.filter { !notificationsSeenStore.isSeen(it.id) }
      newlyDetected.forEach { notificationsSeenStore.markSeen(it.id) }
      if (isFirstRun) emptyList() else newlyDetected
    }

  private suspend fun fetchRegistrationSummary(): RegistrationSummaryDto {
    val response = api.getRegistrationSummary()
    if (!response.isSuccessful) error("Failed to load registration summary: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty registration summary response")
    if (!body.success) error(body.message ?: "Failed to load registration summary")
    return body.data ?: error("Empty registration summary data")
  }

  private suspend fun fetchRiskSummary(): RiskSummaryTotalsDto {
    val response = api.getRiskSummary()
    if (!response.isSuccessful) error("Failed to load risk summary: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty risk summary response")
    if (!body.success) error(body.message ?: "Failed to load risk summary")
    return body.data ?: error("Empty risk summary data")
  }

  private suspend fun fetchVisitSummary(): VisitSummaryTotalsDto {
    val response = api.getVisitSummary()
    if (!response.isSuccessful) error("Failed to load visit summary: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty visit summary response")
    if (!body.success) error(body.message ?: "Failed to load visit summary")
    return body.data ?: error("Empty visit summary data")
  }
}
