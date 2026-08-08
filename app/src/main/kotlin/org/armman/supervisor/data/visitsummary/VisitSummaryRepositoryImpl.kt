package org.armman.supervisor.data.visitsummary

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.fetchAllBeneficiaryPages
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.visitsummary.SakhiVisitSummary
import org.armman.supervisor.ui.visitsummary.VillageVisitRow
import org.armman.supervisor.ui.visitsummary.VisitSummaryRepository
import javax.inject.Inject

private const val VISIT_STATUS_CATEGORY = "VISIT_STATUS"
private const val VISIT_STATUS_PENDING = "PENDING"
private const val VISIT_STATUS_MISSED = "MISSED"

/**
 * Concrete [VisitSummaryRepository]. `GET /visits` has no Sakhi/project filter and no working
 * pagination (confirmed against the live gateway), so every load fetches the same server-side
 * page of visits and filters/groups it client-side — visits outside that page are invisible to
 * this screen until the backend adds real filtering. For each Sakhi in [locationId]'s roster,
 * her visits are matched by `sakhiId`, then joined to her beneficiaries (fetched the same way as
 * [org.armman.supervisor.data.registrations.RegistrationsRepositoryImpl]) to resolve a village
 * name, since a visit record itself carries no village. A Sakhi whose beneficiary fetch fails is
 * dropped from the result rather than failing the whole screen.
 */
class VisitSummaryRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val visitApi: VisitApi,
  private val beneficiaryApi: BeneficiaryListApi,
  private val lookupsRepository: LookupsRepository,
) : VisitSummaryRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getVisitSummary(locationId: String?): List<SakhiVisitSummary> = coroutineScope {
    if (locationId == null) return@coroutineScope emptyList()
    val sakhis = projectsRepository.getSakhis(locationId)
    val visits = fetchVisits()
    val statusIdsByCode = lookupsRepository.getValueIdsByCode(VISIT_STATUS_CATEGORY)
    val pendingId = statusIdsByCode[VISIT_STATUS_PENDING]
    val missedId = statusIdsByCode[VISIT_STATUS_MISSED]

    sakhis
      .map { sakhi ->
        val sakhiVisits = visits.filter { it.sakhiId == sakhi.id }
        val deferredCases = if (sakhiVisits.isEmpty()) null else async { runCatching { fetchCases(sakhi.id) } }
        Triple(sakhi, sakhiVisits, deferredCases)
      }
      .mapNotNull { (sakhi, sakhiVisits, deferredCases) ->
        if (deferredCases == null) return@mapNotNull SakhiVisitSummary(sakhi.name, emptyList())

        val cases = deferredCases.await().getOrNull() ?: return@mapNotNull null
        val villageByBeneficiaryId = cases.associate { it.id to it.villageName.orEmpty() }

        val villages = sakhiVisits
          .groupBy { villageByBeneficiaryId[it.beneficiaryId].orEmpty() }
          .map { (villageName, villageVisits) ->
            VillageVisitRow(
              villageName = villageName,
              total = villageVisits.size,
              due = villageVisits.count { it.statusLookupValueId == pendingId },
              missed = villageVisits.count { it.statusLookupValueId == missedId },
            )
          }
        SakhiVisitSummary(sakhiName = sakhi.name, villages = villages)
      }
  }

  private suspend fun fetchVisits(): List<VisitInstanceDto> {
    val response = visitApi.getVisits()
    if (!response.isSuccessful) error("Failed to load visits: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty visits response")
    if (!body.success) error(body.message ?: "Failed to load visits")
    return body.data.orEmpty()
  }

  private suspend fun fetchCases(sakhiId: String): List<BeneficiaryCaseDto> =
    fetchAllBeneficiaryPages { cursor -> beneficiaryApi.getBeneficiaries(sakhiId, cursor) }
}
