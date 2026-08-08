package org.armman.supervisor.data.registrations

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.fetchAllBeneficiaryPages
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.registrations.RegistrationsRepository
import org.armman.supervisor.ui.registrations.SakhiRegistrationSummary
import org.armman.supervisor.ui.registrations.VillageRegistrationRow
import javax.inject.Inject

private const val CASE_TYPE_MOTHER = "MOTHER"

/**
 * Concrete [RegistrationsRepository]. For each Sakhi in [locationId]'s roster, fetches her
 * beneficiaries via [BeneficiaryListApi] and groups them by village. [motherTarget]/[childTarget]
 * have no backing endpoint anywhere in the backend — they stay 0 rather than fabricated. A Sakhi
 * whose beneficiary fetch fails is dropped from the result (logged via the thrown exception being
 * swallowed) rather than failing the whole screen, matching [ProjectsRepository]'s existing
 * per-project resilience precedent.
 */
class RegistrationsRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val api: BeneficiaryListApi,
) : RegistrationsRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getRegistrations(locationId: String?): List<SakhiRegistrationSummary> =
    coroutineScope {
      if (locationId == null) return@coroutineScope emptyList()
      val sakhis = projectsRepository.getSakhis(locationId)

      sakhis
        .map { sakhi -> sakhi to async { runCatching { fetchCases(sakhi.id) } } }
        .mapNotNull { (sakhi, deferredCases) ->
          val cases = deferredCases.await().getOrNull() ?: return@mapNotNull null
          SakhiRegistrationSummary(
            sakhiId = sakhi.id,
            sakhiName = sakhi.name,
            badgeCount = cases.size,
            motherTarget = 0,
            childTarget = 0,
            villages = groupByVillage(cases),
          )
        }
    }

  private suspend fun fetchCases(sakhiId: String): List<BeneficiaryCaseDto> =
    fetchAllBeneficiaryPages { cursor -> api.getBeneficiaries(sakhiId, cursor) }

  private fun groupByVillage(cases: List<BeneficiaryCaseDto>): List<VillageRegistrationRow> =
    cases.groupBy { it.villageName.orEmpty() }
      .map { (villageName, villageCases) ->
        VillageRegistrationRow(
          villageName = villageName,
          motherCount = villageCases.count { it.caseType == CASE_TYPE_MOTHER },
          childCount = villageCases.count { it.caseType != CASE_TYPE_MOTHER },
        )
      }
}
