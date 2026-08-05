package org.armman.supervisor.data.visitsummary

import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.visitsummary.SakhiVisitSummary
import org.armman.supervisor.ui.visitsummary.VillageVisitRow
import org.armman.supervisor.ui.visitsummary.VisitSummaryRepository
import javax.inject.Inject

/**
 * Concrete [VisitSummaryRepository]. Per-Sakhi/per-village visit counts remain local sample data
 * — no visit-summary-detail endpoint exists yet; the interface and its Hilt binding in
 * `di/VisitSummaryModule.kt` stay unchanged when one is wired up.
 */
class VisitSummaryRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
) : VisitSummaryRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getVisitSummary(locationId: String?): List<SakhiVisitSummary> {
    val seed = locationId?.hashCode()?.mod(SEED_RANGE) ?: 0
    return listOf(
      SakhiVisitSummary(
        sakhiName = "SakhiKomal",
        villages = listOf(VillageVisitRow("SushilTest", total = 5 + seed, due = 0, missed = 0)),
      ),
      SakhiVisitSummary(
        sakhiName = "SakhiMeera",
        villages = listOf(VillageVisitRow("SushilTest1", total = 3 + seed, due = 0, missed = 0)),
      ),
    )
  }

  private companion object {
    const val SEED_RANGE = 10
  }
}
