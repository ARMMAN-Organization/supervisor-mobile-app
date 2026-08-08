package org.armman.supervisor.data.beneficiaries

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.beneficiaries.BeneficiaryDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

private fun motherCase(
  id: String = "case-mother-1",
  fullName: String = "Sunita Devi",
  villageName: String? = "Test Village",
) = BeneficiaryCaseDto(
  id = id,
  caseType = "MOTHER",
  registrationDate = "2026-08-01T00:00:00.000Z",
  pii = BeneficiaryPiiDto(fullName = fullName, mobileNumber = "9876500001"),
  motherCaseDetails = MotherCaseDetailsDto(
    lmpDate = "2026-06-01T00:00:00.000Z",
    eddDate = "2027-03-08T00:00:00.000Z",
    heightCm = "155",
    bmiAtRegistration = "24.14",
  ),
  childCaseDetails = null,
  sakhiName = "Demo Sakhi",
  projectName = "Pemma Single Program",
  villageName = villageName,
)

private fun childCase(id: String = "case-child-1") = BeneficiaryCaseDto(
  id = id,
  caseType = "CHILD",
  registrationDate = "2026-08-01T00:00:00.000Z",
  pii = BeneficiaryPiiDto(fullName = "Baby Sunita", mobileNumber = "9876500001"),
  motherCaseDetails = null,
  childCaseDetails = ChildCaseDetailsDto(dateOfBirth = "2026-07-01T00:00:00.000Z"),
  sakhiName = "Demo Sakhi",
  projectName = "Pemma Single Program",
  villageName = "Test Village",
)

private class FakeBeneficiaryListApi : BeneficiaryListApi {
  var items: List<BeneficiaryCaseDto> = emptyList()
  /** Maps a page's cursor (null = first page) to (items, nextCursor), for pagination tests. */
  var pagesByCursor: Map<String?, Pair<List<BeneficiaryCaseDto>, String?>>? = null
  var httpErrorCode: Int? = null
  var envelopeSuccess = true

  override suspend fun getBeneficiaries(sakhiId: String, cursor: String?): Response<BeneficiaryListEnvelopeDto> {
    httpErrorCode?.let { return Response.error(it, okhttp3.ResponseBody.create(null, "")) }
    val (pageItems, nextCursor) = pagesByCursor?.get(cursor) ?: (items to null)
    return Response.success(
      BeneficiaryListEnvelopeDto(
        success = envelopeSuccess,
        message = if (envelopeSuccess) "OK" else "boom",
        data = BeneficiaryListPageDto(items = pageItems, nextCursor = nextCursor),
      ),
    )
  }

  override suspend fun getAtRiskBeneficiaries(
    sakhiId: String,
    atRiskOnly: Boolean,
    cursor: String?,
  ): Response<BeneficiaryListEnvelopeDto> = error("not used")
}

private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> = error("not used")

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used")

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail =
    SakhiDetail(sakhiName = "Fallback Sakhi", projectName = "Fallback Project", address = "")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = SakhiOption(sakhiId, "Fallback Sakhi")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override fun clearCache() = Unit
}

class BeneficiaryListRepositoryImplTest {
  private val api = FakeBeneficiaryListApi()
  private val projectsRepository = FakeProjectsRepository()
  private val repository = BeneficiaryListRepositoryImpl(api, projectsRepository)

  @Test
  fun `getBeneficiaries splits mother and child cases and reads name context off the first case`() = runTest {
    api.items = listOf(childCase(), motherCase())

    val result = repository.getBeneficiaries("sakhi-1")

    assertEquals("Demo Sakhi", result.sakhiName)
    assertEquals("Pemma Single Program", result.projectName)
    assertEquals("Test Village", result.address)
    assertEquals(2, result.beneficiaries.size)
    assertEquals(1, result.beneficiaries.filterIsInstance<BeneficiaryDetail.Child>().size)
    assertEquals(1, result.beneficiaries.filterIsInstance<BeneficiaryDetail.Mother>().size)
  }

  @Test
  fun `getBeneficiaries returns an empty list, not an error, for a sakhi with no beneficiaries`() = runTest {
    api.items = emptyList()

    val result = repository.getBeneficiaries("sakhi-1")

    assertEquals(true, result.beneficiaries.isEmpty())
  }

  @Test
  fun `getBeneficiaries falls back to ProjectsRepository for the sakhi name when the list is empty`() = runTest {
    api.items = emptyList()

    val result = repository.getBeneficiaries("sakhi-1")

    assertEquals("Fallback Sakhi", result.sakhiName)
    assertEquals("Fallback Project", result.projectName)
  }

  @Test
  fun `getBeneficiaries back-calculates mother weightKg from height and BMI`() = runTest {
    api.items = listOf(motherCase())

    val mother = repository.getBeneficiaries("sakhi-1").beneficiaries.filterIsInstance<BeneficiaryDetail.Mother>().single()

    // 24.14 * (1.55)^2 ~= 58.0
    assertEquals(58.0, mother.weightKg, 0.1)
  }

  @Test
  fun `getBeneficiaries formats ISO dates as dd-MM-yyyy`() = runTest {
    api.items = listOf(motherCase())

    val mother = repository.getBeneficiaries("sakhi-1").beneficiaries.filterIsInstance<BeneficiaryDetail.Mother>().single()

    assertEquals("08-03-2027", mother.edd)
    assertEquals("01-06-2026", mother.lmp)
  }

  @Test
  fun `getBeneficiaries defaults phone to empty string when mobileNumber is null`() = runTest {
    api.items = listOf(motherCase().copy(pii = BeneficiaryPiiDto(fullName = "No Phone", mobileNumber = null)))

    val mother = repository.getBeneficiaries("sakhi-1").beneficiaries.filterIsInstance<BeneficiaryDetail.Mother>().single()

    assertEquals("", mother.phone)
  }

  @Test
  fun `getBeneficiaries follows nextCursor to collect beneficiaries across multiple pages`() = runTest {
    api.pagesByCursor = mapOf(
      null to (listOf(motherCase(id = "case-1")) to "cursor-2"),
      "cursor-2" to (listOf(motherCase(id = "case-2")) to null),
    )

    val result = repository.getBeneficiaries("sakhi-1")

    assertEquals(2, result.beneficiaries.size)
  }

  @Test
  fun `formatDisplayDate returns empty string instead of crashing on a too-short non-blank date`() {
    assertEquals("", formatDisplayDate("2026"))
  }

  @Test
  fun `getBeneficiaries throws on an HTTP error`() {
    api.httpErrorCode = 500

    assertThrows(IllegalStateException::class.java) { runTest { repository.getBeneficiaries("sakhi-1") } }
  }

  @Test
  fun `getBeneficiaries throws when the envelope reports success false`() {
    api.envelopeSuccess = false

    assertThrows(IllegalStateException::class.java) { runTest { repository.getBeneficiaries("sakhi-1") } }
  }
}
