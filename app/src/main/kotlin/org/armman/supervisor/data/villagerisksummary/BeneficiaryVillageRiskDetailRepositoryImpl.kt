package org.armman.supervisor.data.villagerisksummary

import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryRiskConditionSummaryDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryWithRiskDto
import org.armman.supervisor.data.beneficiaries.fetchAllBeneficiaryPages
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskDetail
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskLevel
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetail
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetailRepository
import javax.inject.Inject

private const val CASE_TYPE_MOTHER = "MOTHER"

/**
 * Concrete [VillageRiskDetailRepository]. Fetches the Sakhi's at-risk beneficiaries (same call
 * [org.armman.supervisor.data.risksummary.RiskSummaryRepositoryImpl] uses, so counts stay
 * consistent with Risk Summary), filters to the selected village, then enriches with risk grade
 * and risk-condition name via two batched calls — [BeneficiaryListApi.getBeneficiariesWithRisk]
 * and [BeneficiaryListApi.getRiskConditionSummaries] — rather than one detail call per
 * beneficiary. Visit and referral status aren't exposed by any batch-friendly beneficiary-service
 * endpoint (only a single-case detail call, and referrals live in a separate service entirely),
 * so [BeneficiaryRiskDetail] intentionally omits them.
 */
class BeneficiaryVillageRiskDetailRepositoryImpl @Inject constructor(
  private val api: BeneficiaryListApi,
) : VillageRiskDetailRepository {

  override suspend fun getVillageRiskDetail(sakhiId: String, villageName: String): VillageRiskDetail {
    val villageCases = fetchAllBeneficiaryPages { cursor -> api.getAtRiskBeneficiaries(sakhiId, cursor = cursor) }
      .filter { it.villageName == villageName }
    if (villageCases.isEmpty()) return VillageRiskDetail(villageName = villageName, mothers = emptyList(), children = emptyList())

    val ids = villageCases.map(BeneficiaryCaseDto::id)
    val riskLevelById = fetchRiskLevelsById(ids)
    val riskConditionNameById = fetchRiskConditionNamesById(ids)

    val (mothers, children) = villageCases
      .map { case -> case.toBeneficiaryRiskDetail(riskLevelById, riskConditionNameById) }
      .partition { it.registrationType == CASE_TYPE_MOTHER }
    return VillageRiskDetail(villageName = villageName, mothers = mothers, children = children)
  }

  private suspend fun fetchRiskLevelsById(ids: List<String>): Map<String, String> {
    val response = api.getBeneficiariesWithRisk(ids.joinToString(","))
    if (!response.isSuccessful) error("Failed to load beneficiary risk levels: HTTP ${response.code()}")
    val envelope = response.body() ?: error("Empty beneficiary risk levels response")
    if (!envelope.success) error(envelope.message ?: "Failed to load beneficiary risk levels")
    return envelope.data.orEmpty().associateBy(BeneficiaryWithRiskDto::id) { it.riskLevel }
  }

  private suspend fun fetchRiskConditionNamesById(ids: List<String>): Map<String, String> {
    val response = api.getRiskConditionSummaries(ids.joinToString(","))
    if (!response.isSuccessful) error("Failed to load risk condition summaries: HTTP ${response.code()}")
    val envelope = response.body() ?: error("Empty risk condition summaries response")
    if (!envelope.success) error(envelope.message ?: "Failed to load risk condition summaries")
    return envelope.data.orEmpty().associateBy(BeneficiaryRiskConditionSummaryDto::beneficiaryId) { summary ->
      summary.riskConditionSummaries.firstOrNull { it.conditionName != null }?.conditionName.orEmpty()
    }
  }

  private fun BeneficiaryCaseDto.toBeneficiaryRiskDetail(
    riskLevelById: Map<String, String>,
    riskConditionNameById: Map<String, String>,
  ) = BeneficiaryRiskDetail(
    id = id,
    name = pii.fullName,
    registrationType = caseType,
    riskDetails = riskConditionNameById[id].orEmpty(),
    riskType = toBeneficiaryRiskLevel(riskLevelById[id]),
  )

  private fun toBeneficiaryRiskLevel(riskLevel: String?): BeneficiaryRiskLevel = when (riskLevel) {
    "mild" -> BeneficiaryRiskLevel.MILD
    "moderate" -> BeneficiaryRiskLevel.MODERATE
    "high" -> BeneficiaryRiskLevel.HIGH
    else -> BeneficiaryRiskLevel.NONE
  }
}
