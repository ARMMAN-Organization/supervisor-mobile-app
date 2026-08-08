package org.armman.supervisor.data.registrations

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
import org.armman.supervisor.ui.registrations.VillageRegistrationRow
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

  override fun clearCache() = Unit
}

private class FakeBeneficiaryListApi : BeneficiaryListApi {
  var casesBySakhiId: Map<String, List<BeneficiaryCaseDto>> = emptyMap()
  var failingSakhiIds: Set<String> = emptySet()

  override suspend fun getBeneficiaries(sakhiId: String): Response<BeneficiaryListEnvelopeDto> {
    if (sakhiId in failingSakhiIds) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(
      BeneficiaryListEnvelopeDto(
        success = true,
        message = "OK",
        data = BeneficiaryListPageDto(items = casesBySakhiId[sakhiId].orEmpty(), nextCursor = null),
      ),
    )
  }

  override suspend fun getAtRiskBeneficiaries(sakhiId: String, atRiskOnly: Boolean): Response<BeneficiaryListEnvelopeDto> =
    error("not used")
}

class RegistrationsRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val api = FakeBeneficiaryListApi()
  private val repository = RegistrationsRepositoryImpl(projectsRepository, api)

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
  fun `getRegistrations groups a sakhi's beneficiaries by village with real mother-child counts`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.casesBySakhiId = mapOf(
      "sakhi-1" to listOf(
        case("MOTHER", "Village A"),
        case("CHILD", "Village A"),
        case("MOTHER", "Village B"),
      ),
    )

    val summaries = repository.getRegistrations("loc-1")

    assertEquals(1, summaries.size)
    assertEquals("sakhi-1", summaries[0].sakhiId)
    assertEquals("Demo Sakhi", summaries[0].sakhiName)
    assertEquals(3, summaries[0].badgeCount)
    assertEquals(
      setOf(
        VillageRegistrationRow("Village A", motherCount = 1, childCount = 1),
        VillageRegistrationRow("Village B", motherCount = 1, childCount = 0),
      ),
      summaries[0].villages.toSet(),
    )
  }

  @Test
  fun `getRegistrations always reports zero mother and child targets`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.casesBySakhiId = mapOf("sakhi-1" to listOf(case("MOTHER", "Village A")))

    val summary = repository.getRegistrations("loc-1").single()

    assertEquals(0, summary.motherTarget)
    assertEquals(0, summary.childTarget)
  }

  @Test
  fun `getRegistrations reports a zero-badge empty-villages card for a sakhi with no beneficiaries`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to listOf(SakhiOption("sakhi-1", "Demo Sakhi")))
    api.casesBySakhiId = emptyMap()

    val summary = repository.getRegistrations("loc-1").single()

    assertEquals(0, summary.badgeCount)
    assertEquals(true, summary.villages.isEmpty())
  }

  @Test
  fun `getRegistrations drops a sakhi whose beneficiary fetch fails instead of failing the screen`() = runTest {
    projectsRepository.sakhisByProject = mapOf(
      "loc-1" to listOf(SakhiOption("sakhi-broken", "Broken Sakhi"), SakhiOption("sakhi-1", "Demo Sakhi")),
    )
    api.failingSakhiIds = setOf("sakhi-broken")
    api.casesBySakhiId = mapOf("sakhi-1" to listOf(case("MOTHER", "Village A")))

    val summaries = repository.getRegistrations("loc-1")

    assertEquals(1, summaries.size)
    assertEquals("sakhi-1", summaries[0].sakhiId)
  }

  @Test
  fun `getRegistrations returns an empty list for a null locationId`() = runTest {
    assertEquals(emptyList<Any>(), repository.getRegistrations(null))
  }

  @Test
  fun `getRegistrations returns an empty list when the location has no sakhis`() = runTest {
    projectsRepository.sakhisByProject = mapOf("loc-1" to emptyList())

    assertEquals(emptyList<Any>(), repository.getRegistrations("loc-1"))
  }
}
