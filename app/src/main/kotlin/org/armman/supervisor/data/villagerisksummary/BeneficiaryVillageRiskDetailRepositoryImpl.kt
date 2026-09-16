package org.armman.supervisor.data.villagerisksummary

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.beneficiaries.BeneficiaryCaseDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryListApi
import org.armman.supervisor.data.beneficiaries.BeneficiaryRiskConditionSummaryDto
import org.armman.supervisor.data.beneficiaries.BeneficiaryWithRiskDto
import org.armman.supervisor.data.beneficiaries.RiskConditionSummaryDto
import org.armman.supervisor.data.beneficiaries.fetchAllBeneficiaryPages
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskDetail
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskLevel
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetail
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetailRepository
import retrofit2.Response
import javax.inject.Inject

private const val CASE_TYPE_MOTHER = "MOTHER"

/** Batch size for [BeneficiaryListApi.getBeneficiariesWithRisk]/[BeneficiaryListApi.getRiskConditionSummaries]
 * id query params — keeps the comma-separated id list well under common URL-length limits even
 * for a large village. */
private const val ID_BATCH_SIZE = 100

/** Grade order used to pick a beneficiary's *worst* risk condition, matching [BeneficiaryRiskLevel]'s
 * severity ordering. Mirrors beneficiary-service's own mild/moderate/high grade vocabulary. */
private val GRADE_SEVERITY = listOf("high", "moderate", "mild")

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

  override suspend fun getVillageRiskDetail(sakhiId: String, villageName: String): VillageRiskDetail = coroutineScope {
    val villageCases = fetchAllBeneficiaryPages { cursor -> api.getAtRiskBeneficiaries(sakhiId, cursor = cursor) }
      .filter { it.villageName.normalizeVillageName() == villageName.normalizeVillageName() }
    if (villageCases.isEmpty()) return@coroutineScope VillageRiskDetail(villageName = villageName, mothers = emptyList(), children = emptyList())

    val ids = villageCases.map(BeneficiaryCaseDto::id)
    val riskLevelByIdDeferred = async { fetchRiskLevelsById(ids) }
    val riskConditionNameByIdDeferred = async { fetchRiskConditionNamesById(ids) }
    val riskLevelById = riskLevelByIdDeferred.await()
    val riskConditionNameById = riskConditionNameByIdDeferred.await()

    val (mothers, children) = villageCases
      .map { case -> case.toBeneficiaryRiskDetail(riskLevelById, riskConditionNameById) }
      .partition { it.registrationType == CASE_TYPE_MOTHER }
    VillageRiskDetail(villageName = villageName, mothers = mothers, children = children)
  }

  private suspend fun fetchRiskLevelsById(ids: List<String>): Map<String, String> =
    fetchInBatches(ids, api::getBeneficiariesWithRisk, "beneficiary risk levels", { it.success }, { it.message }) { envelope ->
      envelope.data.orEmpty().associateBy(BeneficiaryWithRiskDto::id) { it.riskLevel }
    }

  private suspend fun fetchRiskConditionNamesById(ids: List<String>): Map<String, String> =
    fetchInBatches(ids, api::getRiskConditionSummaries, "risk condition summaries", { it.success }, { it.message }) { envelope ->
      envelope.data.orEmpty().associateBy(BeneficiaryRiskConditionSummaryDto::beneficiaryId) { summary ->
        summary.riskConditionSummaries.worstGradeConditionName()
      }
    }

  /** Splits [ids] into [ID_BATCH_SIZE]-sized chunks (each call site's endpoint takes ids as one
   * comma-separated query param with no server-side batching), fetches+unwraps each chunk's
   * envelope, and merges the per-chunk maps [toResultMap] produces. Collapses the same
   * `isSuccessful` -> `body() ?: error(...)` -> `envelope.success` triad
   * [org.armman.supervisor.data.masterdata.MasterDataRepositoryImpl.unwrap] uses, generalized to
   * return a map instead of a count. */
  private suspend fun <T> fetchInBatches(
    ids: List<String>,
    call: suspend (String) -> Response<T>,
    label: String,
    isSuccess: (T) -> Boolean,
    message: (T) -> String?,
    toResultMap: (T) -> Map<String, String>,
  ): Map<String, String> = ids.chunked(ID_BATCH_SIZE)
    .map { batch ->
      val response = call(batch.joinToString(","))
      if (!response.isSuccessful) error("Failed to load $label: HTTP ${response.code()}")
      val envelope = response.body() ?: error("Empty $label response")
      if (!isSuccess(envelope)) error(message(envelope) ?: "Failed to load $label")
      toResultMap(envelope)
    }
    .fold(emptyMap()) { acc, batchResult -> acc + batchResult }

  private fun String?.normalizeVillageName(): String = this.orEmpty().trim().lowercase()

  /** The name of the beneficiary's *worst*-graded risk condition, picked by [GRADE_SEVERITY]
   * rather than list order — a beneficiary's overall [BeneficiaryRiskLevel] (from
   * [fetchRiskLevelsById]) reflects their worst grade, so the displayed condition name must match it. */
  private fun List<RiskConditionSummaryDto>.worstGradeConditionName(): String = this
    .filter { it.conditionName != null }
    .minByOrNull { GRADE_SEVERITY.indexOf(it.latestGrade).let { index -> if (index == -1) GRADE_SEVERITY.size else index } }
    ?.conditionName.orEmpty()

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
