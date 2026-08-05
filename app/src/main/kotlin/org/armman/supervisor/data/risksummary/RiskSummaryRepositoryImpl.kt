package org.armman.supervisor.data.risksummary

import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.risksummary.RiskSummaryRepository
import org.armman.supervisor.ui.risksummary.SakhiRiskSummary
import org.armman.supervisor.ui.risksummary.VillageRiskRow
import javax.inject.Inject

/**
 * Concrete [RiskSummaryRepository]. Per-Sakhi/per-village risk counts remain local sample data
 * — no risk-summary-detail endpoint exists yet; the interface and its Hilt binding in
 * `di/RiskSummaryModule.kt` stay unchanged when one is wired up.
 */
class RiskSummaryRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
) : RiskSummaryRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getRiskSummary(locationId: String?): List<SakhiRiskSummary> {
    val seed = locationId?.hashCode()?.mod(SEED_RANGE) ?: 0
    return listOf(
      SakhiRiskSummary(
        sakhiName = "SakhiKomal",
        villages = listOf(VillageRiskRow("SushilTest", motherCount = 1 + seed, childCount = 0)),
      ),
      SakhiRiskSummary(
        sakhiName = "SakhiMeera",
        villages = listOf(VillageRiskRow("SushilTest1", motherCount = 1 + seed, childCount = 0)),
      ),
    )
  }

  private companion object {
    const val SEED_RANGE = 10
  }
}
