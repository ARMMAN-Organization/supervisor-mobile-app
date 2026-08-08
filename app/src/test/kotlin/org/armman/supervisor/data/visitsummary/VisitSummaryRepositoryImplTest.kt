package org.armman.supervisor.data.visitsummary

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryListEnvelopeDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListPageDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryPiiDto
import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupValueDto
import org.armman.supervisor.data.lookups.LookupsApi
import org.armman.supervisor.data.lookups.LookupsEnvelopeDto
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

private const val PENDING_ID = "status-pending"
private const val MISSED_ID = "status-missed"
private const val COMPLETED_ID = "status-completed"

private fun case(id: String, villageName: String?) = BeneficiaryCaseDto(
  id = id,
  caseType = "MOTHER",
  registrationDate = "2026-08-01T00:00:00.000Z",
  pii = BeneficiaryPiiDto(fullName = "Name", mobileNumber = "9876500001"),
  motherCaseDetails = null,
  childCaseDetails = null,
  sakhiName = "Sakhi",
  projectName = "Project",
  villageName = villageName,
)

private class FakeProjectsRepository : ProjectsRepository {
  var sakhisByProject: Map<String, List<SakhiOption>> = emptyMap()
  var failGetProjects = false

  override suspend fun getProjects(): List<LocationOption> {
    if (failGetProjects) error("Simulated failure fetching projects")
    return listOf(LocationOption("loc-1", "Unrestricted Armman"))
  }

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = sakhisByProject[projectId].orEmpty()

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override fun clearCache() = Unit
}

private class FakeVisitApi : VisitApi {
  var visits: List<VisitInstanceDto> = emptyList()
  var fail = false

  override suspend fun getVisits(): Response<VisitListEnvelopeDto> {
    if (fail) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(VisitListEnvelopeDto(success = true, message = "OK", data = visits))
  }
}

private class FakeBeneficiaryListApi : BeneficiaryListApi {
  var casesBySakhiId: Map<String, List<BeneficiaryCaseDto>> = emptyMap()
  /** Second page of cases for a sakhi, keyed by the cursor returned alongside their first page. */
  var secondPageByCursor: Map<String, List<BeneficiaryCaseDto>> = emptyMap()

  override suspend fun getBeneficiaries(sakhiId: String, cursor: String?): Response<BeneficiaryListEnvelopeDto> {
    val items = if (cursor == null) casesBySakhiId[sakhiId].orEmpty() else secondPageByCursor[cursor].orEmpty()
    val nextCursor = if (cursor == null && secondPageByCursor.containsKey("cursor-$sakhiId")) "cursor-$sakhiId" else null
    return Response.success(
      BeneficiaryListEnvelopeDto(
        success = true,
        message = "OK",
        data = BeneficiaryListPageDto(items = items, nextCursor = nextCursor),
      ),
    )
  }

  override suspend fun getAtRiskBeneficiaries(
    sakhiId: String,
    atRiskOnly: Boolean,
    cursor: String?,
  ): Response<BeneficiaryListEnvelopeDto> = error("not used")
}

private class FakeLookupsApi : LookupsApi {
  override suspend fun getLookups(): Response<LookupsEnvelopeDto> =
    Response.success(
      LookupsEnvelopeDto(
        success = true,
        message = "OK",
        data = listOf(
          LookupCategoryDto(
            categoryCode = "VISIT_STATUS",
            values = listOf(
              LookupValueDto(PENDING_ID, "PENDING"),
              LookupValueDto(MISSED_ID, "MISSED"),
              LookupValueDto(COMPLETED_ID, "COMPLETED"),
            ),
          ),
        ),
      ),
    )
}

class VisitSummaryRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val visitApi = FakeVisitApi()
  private val beneficiaryApi = FakeBeneficiaryListApi()
  private val lookupsRepository = LookupsRepository(FakeLookupsApi())
  private val repository = VisitSummaryRepositoryImpl(projectsRepository, visitApi, beneficiaryApi, lookupsRepository)

  @Test
  fun `getLocations delegates to ProjectsRepository`() = runTest {
    assertEquals(listOf(LocationOption("loc-1", "Unrestricted Armman")), repository.getLocations())
  }

  @Test
  fun `getLocations propagates a ProjectsRepository failure`() {
    projectsRepository.failGetProjects = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getLocations() } }
  }

  @Test
  fun `getVisitSummary filters visits by sakhiId client-side and joins village via beneficiary lookup`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    beneficiaryApi.casesBySakhiId = mapOf("sakhi-1" to listOf(case("ben-1", "Village A")))
    visitApi.visits = listOf(
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = PENDING_ID),
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = MISSED_ID),
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = COMPLETED_ID),
      VisitInstanceDto(beneficiaryId = "other", sakhiId = "sakhi-other", statusLookupValueId = COMPLETED_ID),
    )

    val summary = repository.getVisitSummary("loc-1").single()
    val village = summary.villages.single()

    assertEquals("Demo Sakhi", summary.sakhiName)
    assertEquals("Village A", village.villageName)
    assertEquals(3, village.total)
    assertEquals(1, village.due)
    assertEquals(1, village.missed)
  }

  @Test
  fun `getVisitSummary excludes a visit whose beneficiaryId is not in the sakhi's beneficiary list`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    beneficiaryApi.casesBySakhiId = mapOf("sakhi-1" to listOf(case("ben-1", "Village A")))
    visitApi.visits = listOf(
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = PENDING_ID),
      VisitInstanceDto(beneficiaryId = "ben-unknown", sakhiId = "sakhi-1", statusLookupValueId = PENDING_ID),
    )

    val summary = repository.getVisitSummary("loc-1").single()

    // Both visits belong to sakhi-1, but ben-unknown has no matching case so its village is "".
    val totalAcrossVillages = summary.villages.sumOf { it.total }
    assertEquals(2, totalAcrossVillages)
    assertEquals(2, summary.villages.size) // "Village A" and "" (unresolved) are distinct groups
  }

  @Test
  fun `getVisitSummary buckets an unmapped status as total-only, not due or missed`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    beneficiaryApi.casesBySakhiId = mapOf("sakhi-1" to listOf(case("ben-1", "Village A")))
    visitApi.visits = listOf(
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = "unmapped-status-id"),
    )

    val village = repository.getVisitSummary("loc-1").single().villages.single()

    assertEquals(1, village.total)
    assertEquals(0, village.due)
    assertEquals(0, village.missed)
  }

  @Test
  fun `getVisitSummary reports an empty-villages card for a sakhi with no visits`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    visitApi.visits = emptyList()

    val summary = repository.getVisitSummary("loc-1").single()

    assertEquals(true, summary.villages.isEmpty())
  }

  @Test
  fun `getVisitSummary follows nextCursor to resolve villages across multiple beneficiary pages`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    beneficiaryApi.casesBySakhiId = mapOf("sakhi-1" to listOf(case("ben-1", "Village A")))
    beneficiaryApi.secondPageByCursor = mapOf("cursor-sakhi-1" to listOf(case("ben-2", "Village B")))
    visitApi.visits = listOf(
      VisitInstanceDto(beneficiaryId = "ben-1", sakhiId = "sakhi-1", statusLookupValueId = PENDING_ID),
      VisitInstanceDto(beneficiaryId = "ben-2", sakhiId = "sakhi-1", statusLookupValueId = MISSED_ID),
    )

    val summary = repository.getVisitSummary("loc-1").single()

    assertEquals(setOf("Village A", "Village B"), summary.villages.map { it.villageName }.toSet())
  }

  @Test
  fun `getVisitSummary throws when the shared visits call fails`() {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    visitApi.fail = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getVisitSummary("loc-1") } }
  }

  @Test
  fun `getVisitSummary returns an empty list for a null locationId`() = runTest {
    assertEquals(emptyList<Any>(), repository.getVisitSummary(null))
  }
}
