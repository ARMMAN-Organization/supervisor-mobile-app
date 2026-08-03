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

/**
 * Concrete [DashboardRepository]. The Supervisor's own name comes from the logged-in session.
 * [getLocations] and the KPI/summary numbers below remain local sample data — [ProjectsRepository]
 * has no real project list yet (auth-service exposes no such endpoint), and no dashboard-stats
 * endpoint exists either — the interface and its Hilt binding in `di/DashboardModule.kt` stay
 * unchanged when those are wired up.
 */
class DashboardRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val sessionStore: SessionStore,
) : DashboardRepository {

  private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.getDefault())

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getDashboard(locationId: String?): DashboardData {
    // Vary the placeholder numbers slightly per location so switching the selector visibly
    // changes data, until a real dashboard-stats endpoint exists.
    val seed = locationId?.hashCode()?.mod(SEED_RANGE) ?: 0
    return DashboardData(
      supervisorName = sessionStore.readSession()?.displayName.orEmpty(),
      roleLabel = "Field Supervisor",
      date = LocalDate.now().format(dateFormatter),
      unsyncedCount = seed,
      kpi = KpiSummary(dueVisit = seed, mother = seed, child = seed, monitor = seed),
      visitSummary = listOf(
        SummaryRow(SummaryRowLabel.TOTAL, seed, seed),
        SummaryRow(SummaryRowLabel.DUE, seed, seed),
        SummaryRow(SummaryRowLabel.UPCOMING, seed, seed),
        SummaryRow(SummaryRowLabel.MISSED, seed, seed),
        SummaryRow(SummaryRowLabel.COMPLETE, seed, seed),
      ),
      registrationSummary = listOf(
        SummaryRow(SummaryRowLabel.TARGET, seed, seed),
        SummaryRow(SummaryRowLabel.COMPLETE, seed, seed),
      ),
      riskSummary = listOf(SummaryRow(SummaryRowLabel.TOTAL_RISK, seed, seed)),
      monitoringSummary = listOf(SummaryRow(SummaryRowLabel.TOTAL, seed, seed)),
      staleSakhis = emptyList(),
    )
  }

  private companion object {
    const val SEED_RANGE = 10
  }
}
