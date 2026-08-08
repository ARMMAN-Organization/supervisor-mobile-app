package org.armman.supervisor.data.risksummary

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.fetchAllBeneficiaryPages
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.risksummary.RiskSummaryRepository
import org.armman.supervisor.ui.risksummary.SakhiRiskSummary
import org.armman.supervisor.ui.risksummary.VillageRiskRow
import javax.inject.Inject

private const val CASE_TYPE_MOTHER = "MOTHER"

/**
 * Concrete [RiskSummaryRepository]. For each Sakhi in [locationId]'s roster, fetches her
 * at-risk beneficiaries (`atRiskOnly=true`) via [BeneficiaryListApi] and groups them by village.
 * `atRiskOnly` is a binary flag on beneficiary-service — this reports at-risk mother/child
 * counts per village, not a graded risk breakdown (no such breakdown exists on the list endpoint).
 * A Sakhi whose fetch fails is dropped from the result rather than failing the whole screen,
 * matching [ProjectsRepository]'s existing per-project resilience precedent.
 */
class RiskSummaryRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val api: BeneficiaryListApi,
) : RiskSummaryRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getRiskSummary(locationId: String?): List<SakhiRiskSummary> = coroutineScope {
    if (locationId == null) return@coroutineScope emptyList()
    val sakhis = projectsRepository.getSakhis(locationId)

    sakhis
      .map { sakhi -> sakhi to async { runCatching { fetchAtRiskCases(sakhi.id) } } }
      .mapNotNull { (sakhi, deferredCases) ->
        val cases = deferredCases.await().getOrNull() ?: return@mapNotNull null
        SakhiRiskSummary(sakhiName = sakhi.name, villages = groupByVillage(cases))
      }
  }

  private suspend fun fetchAtRiskCases(sakhiId: String): List<BeneficiaryCaseDto> =
    fetchAllBeneficiaryPages { cursor -> api.getAtRiskBeneficiaries(sakhiId, cursor = cursor) }

  private fun groupByVillage(cases: List<BeneficiaryCaseDto>): List<VillageRiskRow> =
    cases.groupBy { it.villageName.orEmpty() }
      .map { (villageName, villageCases) ->
        VillageRiskRow(
          villageName = villageName,
          motherCount = villageCases.count { it.caseType == CASE_TYPE_MOTHER },
          childCount = villageCases.count { it.caseType != CASE_TYPE_MOTHER },
        )
      }
}
