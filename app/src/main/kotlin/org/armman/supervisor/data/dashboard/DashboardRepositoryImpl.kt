package org.armman.supervisor.data.dashboard

import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.dashboard.DashboardData
import org.armman.supervisor.ui.dashboard.DashboardRepository
import org.armman.supervisor.ui.dashboard.KpiSummary
import org.armman.supervisor.ui.dashboard.SummaryRow
import org.armman.supervisor.ui.dashboard.SummaryRowLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private const val VISIT_STATUS_PENDING = "PENDING"
private const val VISIT_STATUS_MISSED = "MISSED"
private const val VISIT_STATUS_COMPLETED = "COMPLETED"

/**
 * Concrete [DashboardRepository]. The Supervisor's own name comes from the logged-in session,
 * locations from [ProjectsRepository]. KPI/summary numbers come from the aggregate summary
 * endpoints via [DashboardApi] — these are project-wide totals, not scoped by [getDashboard]'s
 * `locationId` (the three summary endpoints take no project filter), so switching the location
 * selector does not currently change these numbers. `kpi.monitor`, `unsyncedCount`,
 * `monitoringSummary` and `staleSakhis` have no backing endpoint yet and stay at 0/empty.
 */
class DashboardRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val sessionStore: SessionStore,
  private val api: DashboardApi,
) : DashboardRepository {

  private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.getDefault())

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getDashboard(locationId: String?): DashboardData {
    val registrationSummary = fetchRegistrationSummary()
    val riskSummary = fetchRiskSummary()
    val visitSummary = fetchVisitSummary()

    val dueVisit = visitSummary.byStatus[VISIT_STATUS_PENDING] ?: 0
    val missedVisit = visitSummary.byStatus[VISIT_STATUS_MISSED] ?: 0
    val completeVisit = visitSummary.byStatus[VISIT_STATUS_COMPLETED] ?: 0

    return DashboardData(
      supervisorName = sessionStore.readSession()?.displayName.orEmpty(),
      roleLabel = "Field Supervisor",
      date = LocalDate.now().format(dateFormatter),
      unsyncedCount = 0,
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
