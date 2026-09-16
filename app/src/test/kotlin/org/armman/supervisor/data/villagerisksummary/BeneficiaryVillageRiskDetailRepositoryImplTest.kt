package org.armman.supervisor.data.villagerisksummary

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryListEnvelopeDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListPageDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryPiiDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryRiskConditionSummaryDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryRiskConditionSummaryEnvelopeDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryWithRiskDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryWithRiskEnvelopeDto
import org.armman.supervisor.data.beneficiaries.RiskConditionSummaryDto
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

private fun case(id: String, caseType: String, villageName: String?) = BeneficiaryCaseDto(
  id = id,
  caseType = caseType,
  registrationDate = "2026-08-01T00:00:00.000Z",
  pii = BeneficiaryPiiDto(fullName = "Name-$id", mobileNumber = "9876500001"),
  motherCaseDetails = null,
  childCaseDetails = null,
  sakhiName = "Sakhi",
  projectName = "Project",
  villageName = villageName,
)

private class FakeBeneficiaryListApi : BeneficiaryListApi {
  var atRiskCases: List<BeneficiaryCaseDto> = emptyList()
  var riskLevelsById: List<BeneficiaryWithRiskDto> = emptyList()
  var riskConditionSummariesById: List<BeneficiaryRiskConditionSummaryDto> = emptyList()
  var batchCallIds: MutableList<String> = mutableListOf()
  var failBatchCalls = false

  override suspend fun getBeneficiaries(sakhiId: String, cursor: String?): Response<BeneficiaryListEnvelopeDto> =
    error("not used")

  override suspend fun getAtRiskBeneficiaries(
    sakhiId: String,
    atRiskOnly: Boolean,
    cursor: String?,
  ): Response<BeneficiaryListEnvelopeDto> = Response.success(
    BeneficiaryListEnvelopeDto(success = true, message = "OK", data = BeneficiaryListPageDto(atRiskCases, null)),
  )

  override suspend fun getBeneficiariesWithRisk(ids: String): Response<BeneficiaryWithRiskEnvelopeDto> {
    batchCallIds += ids
    if (failBatchCalls) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(BeneficiaryWithRiskEnvelopeDto(success = true, message = "OK", data = riskLevelsById))
  }

  override suspend fun getRiskConditionSummaries(
    beneficiaryIds: String,
  ): Response<BeneficiaryRiskConditionSummaryEnvelopeDto> {
    batchCallIds += beneficiaryIds
    if (failBatchCalls) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(
      BeneficiaryRiskConditionSummaryEnvelopeDto(success = true, message = "OK", data = riskConditionSummariesById),
    )
  }
}

class BeneficiaryVillageRiskDetailRepositoryImplTest {
  private val api = FakeBeneficiaryListApi()
  private val repository = BeneficiaryVillageRiskDetailRepositoryImpl(api)

  @Test
  fun `returns real mothers and children for the selected village enriched with risk grade and reason`() = runTest {
    api.atRiskCases = listOf(
      case("m1", "MOTHER", "Semadoh"),
      case("c1", "CHILD", "Semadoh"),
      case("m2", "MOTHER", "Other Village"),
    )
    api.riskLevelsById = listOf(
      BeneficiaryWithRiskDto(id = "m1", beneficiaryName = "Name-m1", riskLevel = "high"),
      BeneficiaryWithRiskDto(id = "c1", beneficiaryName = "Name-c1", riskLevel = "mild"),
    )
    api.riskConditionSummariesById = listOf(
      BeneficiaryRiskConditionSummaryDto(
        beneficiaryId = "m1",
        riskConditionSummaries = listOf(
          RiskConditionSummaryDto(riskConditionId = "rc-1", latestGrade = "high", conditionName = "Hypertension"),
        ),
      ),
    )

    val result = repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    assertEquals(1, result.mothers.size)
    assertEquals(1, result.children.size)
    assertEquals(BeneficiaryRiskLevel.HIGH, result.mothers[0].riskType)
    assertEquals("Hypertension", result.mothers[0].riskDetails)
    assertEquals(BeneficiaryRiskLevel.MILD, result.children[0].riskType)
    assertEquals("", result.children[0].riskDetails)
  }

  @Test
  fun `village with no matching beneficiaries returns empty result without calling batch endpoints`() = runTest {
    api.atRiskCases = listOf(case("m1", "MOTHER", "Other Village"))

    val result = repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    assertEquals(true, result.mothers.isEmpty())
    assertEquals(true, result.children.isEmpty())
    assertEquals(true, api.batchCallIds.isEmpty())
  }

  @Test
  fun `beneficiary with no risk data resolves to NONE with blank risk details`() = runTest {
    api.atRiskCases = listOf(case("m1", "MOTHER", "Semadoh"))
    api.riskLevelsById = emptyList()
    api.riskConditionSummariesById = emptyList()

    val result = repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    assertEquals(BeneficiaryRiskLevel.NONE, result.mothers[0].riskType)
    assertEquals("", result.mothers[0].riskDetails)
  }

  @Test
  fun `batch endpoint failure propagates as an exception`() = runTest {
    api.atRiskCases = listOf(case("m1", "MOTHER", "Semadoh"))
    api.failBatchCalls = true

    assertThrows(Exception::class.java) {
      runTest { repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh") }
    }
  }

  @Test
  fun `risk details name matches the worst-graded condition, not list order`() = runTest {
    api.atRiskCases = listOf(case("m1", "MOTHER", "Semadoh"))
    api.riskLevelsById = listOf(BeneficiaryWithRiskDto(id = "m1", beneficiaryName = "Name-m1", riskLevel = "high"))
    api.riskConditionSummariesById = listOf(
      BeneficiaryRiskConditionSummaryDto(
        beneficiaryId = "m1",
        riskConditionSummaries = listOf(
          RiskConditionSummaryDto(riskConditionId = "rc-1", latestGrade = "mild", conditionName = "Anemia"),
          RiskConditionSummaryDto(riskConditionId = "rc-2", latestGrade = "high", conditionName = "Hypertension"),
        ),
      ),
    )

    val result = repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    assertEquals("Hypertension", result.mothers[0].riskDetails)
  }

  @Test
  fun `village filter ignores case and surrounding whitespace drift between calls`() = runTest {
    api.atRiskCases = listOf(case("m1", "MOTHER", " semadoh "))

    val result = repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    assertEquals(1, result.mothers.size)
  }

  @Test
  fun `large beneficiary lists are chunked across multiple batch calls`() = runTest {
    val ids = (1..250).map { "m$it" }
    api.atRiskCases = ids.map { case(it, "MOTHER", "Semadoh") }

    repository.getVillageRiskDetail(sakhiId = "sakhi-1", villageName = "Semadoh")

    val idsPerCall = api.batchCallIds.map { it.split(",").size }
    assertEquals(true, idsPerCall.all { it <= 100 })
    assertEquals(250, idsPerCall.sum() / 2)
  }
}
