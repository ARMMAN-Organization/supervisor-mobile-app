package org.armman.supervisor.data.dashboard

import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.dashboard.DashboardData
import org.armman.supervisor.ui.dashboard.DashboardRepository
import org.armman.supervisor.ui.dashboard.KpiSummary
import org.armman.supervisor.ui.dashboard.StaleSakhiEntry
import org.armman.supervisor.ui.dashboard.SummaryRow
import org.armman.supervisor.ui.dashboard.SummaryRowLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Concrete [DashboardRepository]. Currently returns local sample data; the method bodies will be
 * swapped to call `beneficiary-service`/`reporting-etl-service` via Retrofit once those endpoints
 * are wired up — the interface and its Hilt binding in `di/DashboardModule.kt` stay unchanged.
 */
class DashboardRepositoryImpl @Inject constructor() : DashboardRepository {

  private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", Locale.getDefault())

  private val locations = listOf(
    LocationOption("loc-1", "Wardha - Zone A"),
    LocationOption("loc-2", "Wardha - Zone B"),
    LocationOption("loc-3", "Nagpur - Zone A"),
  )

  override suspend fun getLocations(): List<LocationOption> = locations

  override suspend fun getDashboard(locationId: String?): DashboardData {
    // Vary the numbers slightly per location so switching the selector visibly changes data.
    val seed = locations.indexOfFirst { it.id == locationId }.let { if (it < 0) 0 else it }
    return DashboardData(
      supervisorName = "Niharika Supervisor",
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
}
