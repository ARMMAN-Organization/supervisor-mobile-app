package org.armman.supervisor.data.monitoringsummary

import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.monitoringsummary.MonitoringSummaryRepository
import org.armman.supervisor.ui.monitoringsummary.SakhiMonitoringSummary
import org.armman.supervisor.ui.monitoringsummary.VillageMonitoringRow
import javax.inject.Inject

/**
 * Concrete [MonitoringSummaryRepository]. Per-Sakhi/per-village monitoring counts remain local
 * sample data — no monitoring-summary-detail endpoint exists yet; the interface and its Hilt
 * binding in `di/MonitoringSummaryModule.kt` stay unchanged when one is wired up.
 */
class MonitoringSummaryRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
) : MonitoringSummaryRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getMonitoringSummary(locationId: String?): List<SakhiMonitoringSummary> {
    val seed = locationId?.hashCode()?.mod(SEED_RANGE) ?: 0
    return listOf(
      SakhiMonitoringSummary(
        sakhiName = "SakhiKomal",
        villages = listOf(VillageMonitoringRow("SushilTest", motherCount = seed, childCount = 0)),
      ),
      SakhiMonitoringSummary(
        sakhiName = "SakhiMeera",
        villages = listOf(VillageMonitoringRow("SushilTest1", motherCount = seed, childCount = 0)),
      ),
    )
  }

  private companion object {
    const val SEED_RANGE = 10
  }
}
