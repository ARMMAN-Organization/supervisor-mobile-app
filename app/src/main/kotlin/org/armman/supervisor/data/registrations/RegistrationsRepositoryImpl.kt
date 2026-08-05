package org.armman.supervisor.data.registrations

import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.registrations.RegistrationsRepository
import org.armman.supervisor.ui.registrations.SakhiRegistrationSummary
import org.armman.supervisor.ui.registrations.VillageRegistrationRow
import javax.inject.Inject

/**
 * Concrete [RegistrationsRepository]. Per-Sakhi/per-village registration counts remain local
 * sample data — no registrations-detail endpoint exists yet; the interface and its Hilt binding
 * in `di/RegistrationsModule.kt` stay unchanged when one is wired up.
 */
class RegistrationsRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
) : RegistrationsRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getRegistrations(locationId: String?): List<SakhiRegistrationSummary> {
    val seed = locationId?.hashCode()?.mod(SEED_RANGE) ?: 0
    return listOf(
      SakhiRegistrationSummary(
        sakhiName = "SakhiKomal",
        badgeCount = 2 + seed,
        motherTarget = 0,
        childTarget = 0,
        villages = listOf(VillageRegistrationRow("SushilTest", motherCount = 1, childCount = 1)),
      ),
      SakhiRegistrationSummary(
        sakhiName = "SakhiMeera",
        badgeCount = 1 + seed,
        motherTarget = 0,
        childTarget = 0,
        villages = listOf(VillageRegistrationRow("SushilTest1", motherCount = 1, childCount = 0)),
      ),
    )
  }

  private companion object {
    const val SEED_RANGE = 10
  }
}
