package org.armman.supervisor.data.risksummary

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryListEnvelopeDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListPageDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryPiiDto
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.risksummary.VillageRiskRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

private fun case(caseType: String, villageName: String?) = BeneficiaryCaseDto(
  id = "case-${caseType}-$villageName",
  caseType = caseType,
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

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> = error("not used")

  override fun clearCache() = Unit
}

private class FakeBeneficiaryListApi : BeneficiaryListApi {
  var atRiskCasesBySakhiId: Map<String, List<BeneficiaryCaseDto>> = emptyMap()
  /** Second page of at-risk cases for a sakhi, keyed by the cursor returned with their first page. */
  var secondPageByCursor: Map<String, List<BeneficiaryCaseDto>> = emptyMap()
  var failingSakhiIds: Set<String> = emptySet()

  override suspend fun getBeneficiaries(sakhiId: String, cursor: String?): Response<BeneficiaryListEnvelopeDto> =
    error("not used")

  override suspend fun getAtRiskBeneficiaries(
    sakhiId: String,
    atRiskOnly: Boolean,
    cursor: String?,
  ): Response<BeneficiaryListEnvelopeDto> {
    if (sakhiId in failingSakhiIds) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    val items = if (cursor == null) atRiskCasesBySakhiId[sakhiId].orEmpty() else secondPageByCursor[cursor].orEmpty()
    val nextCursor = if (cursor == null && secondPageByCursor.containsKey("cursor-$sakhiId")) "cursor-$sakhiId" else null
    return Response.success(
      BeneficiaryListEnvelopeDto(
        success = true,
        message = "OK",
        data = BeneficiaryListPageDto(items = items, nextCursor = nextCursor),
      ),
    )
  }
}

class RiskSummaryRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val api = FakeBeneficiaryListApi()
  private val repository = RiskSummaryRepositoryImpl(projectsRepository, api)

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
  fun `getRiskSummary groups a sakhi's at-risk beneficiaries by village`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.atRiskCasesBySakhiId = mapOf(
      "sakhi-1" to listOf(case("MOTHER", "Village A"), case("CHILD", "Village A"), case("MOTHER", "Village B")),
    )

    val summary = repository.getRiskSummary("loc-1").single()

    assertEquals("Demo Sakhi", summary.sakhiName)
    assertEquals(
      setOf(
        VillageRiskRow("Village A", motherCount = 1, childCount = 1),
        VillageRiskRow("Village B", motherCount = 1, childCount = 0),
      ),
      summary.villages.toSet(),
    )
  }

  @Test
  fun `getRiskSummary reports empty villages for a sakhi with no at-risk beneficiaries`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.atRiskCasesBySakhiId = emptyMap()

    val summary = repository.getRiskSummary("loc-1").single()

    assertEquals(true, summary.villages.isEmpty())
  }

  @Test
  fun `getRiskSummary follows nextCursor to collect at-risk beneficiaries across multiple pages`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.atRiskCasesBySakhiId = mapOf("sakhi-1" to listOf(case("MOTHER", "Village A")))
    api.secondPageByCursor = mapOf("cursor-sakhi-1" to listOf(case("MOTHER", "Village B")))

    val summary = repository.getRiskSummary("loc-1").single()

    assertEquals(2, summary.villages.sumOf { it.motherCount })
  }

  @Test
  fun `getRiskSummary drops a sakhi whose at-risk fetch fails instead of failing the screen`() = runTest {
    projectsRepository.sakhisByProject = mapOf(
      "loc-1" to listOf(SakhiOption("sakhi-broken", "Broken Sakhi"), SakhiOption("sakhi-1", "Demo Sakhi")),
    )
    api.failingSakhiIds = setOf("sakhi-broken")
    api.atRiskCasesBySakhiId = mapOf("sakhi-1" to listOf(case("MOTHER", "Village A")))

    val summaries = repository.getRiskSummary("loc-1")

    assertEquals(1, summaries.size)
    assertEquals("Demo Sakhi", summaries[0].sakhiName)
  }

  @Test
  fun `getRiskSummary returns an empty list for a null locationId`() = runTest {
    assertEquals(emptyList<Any>(), repository.getRiskSummary(null))
  }
}
