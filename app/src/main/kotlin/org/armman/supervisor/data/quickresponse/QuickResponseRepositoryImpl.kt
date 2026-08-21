package org.armman.supervisor.data.quickresponse

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseCardDetail
import org.armman.supervisor.ui.quickresponse.QuickResponseDecision
import org.armman.supervisor.ui.quickresponse.QuickResponseDecisionException
import org.armman.supervisor.ui.quickresponse.QuickResponseEscalationAction
import org.armman.supervisor.ui.quickresponse.QuickResponseRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseRequest
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestType
import org.armman.supervisor.ui.quickresponse.QuickResponseRiskCondition
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** approval-service/escalation-service `cardType` values this screen models a card for (SRS
 * FR-SV-4.1's 8 event types). Note escalation-service's Missed Visit Escalation wire value is
 * `"MISSED_VISIT"`, not `"MISSED_VISIT_ESCALATION"`. */
private val SUPPORTED_CARD_TYPES = mapOf(
  "LMP_CHANGE" to QuickResponseRequestType.LMP_CHANGE,
  "CLOSURE_REVIEW" to QuickResponseRequestType.CLOSURE_REVIEW,
  "REOPEN" to QuickResponseRequestType.REOPEN,
  "ACCOMPANIED_REFERRAL" to QuickResponseRequestType.ACCOMPANIED_REFERRAL,
  "REFERRAL_INCOMPLETE" to QuickResponseRequestType.REFERRAL_INCOMPLETE,
  "DATA_RESTORE" to QuickResponseRequestType.DATA_RESTORE,
  "MISSED_VISIT" to QuickResponseRequestType.MISSED_VISIT_ESCALATION,
  "EDD_NEARING" to QuickResponseRequestType.EDD_NEARING,
)

/** approval-service's `cardSource` value for cards backed by `approval_requests` (as opposed to
 * `escalation_events`) — every supported card type currently comes from this source. */
private const val CARD_SOURCE_APPROVAL_REQUESTS = "approval_requests"

/** Default `status` query value — Quick Response cards not yet decided. */
private const val STATUS_PENDING = "PENDING"

private const val CLOSURE_REASON_LOOKUP_CATEGORY = "CLOSURE_REASON"

private const val LOG_TAG = "QuickResponseRepository"

/**
 * Backed by approval-service's Quick Response endpoints via [QuickResponseApi]. A single
 * `GET /quick-response/{cardId}` call per card returns every field for every card type — Sakhi
 * name/contact, Pada name, beneficiary name, risk details, and the type-specific fields (SRS
 * FR-SV-4.2–4.9) — so no separate beneficiary/Sakhi/Pada/risk-state/type-detail joins are
 * needed. `closureReasonLookupValueId` is the one field backend still returns as a raw lookup
 * id rather than a resolved label, so [LookupsRepository] is still used for that one field.
 */
@Singleton
class QuickResponseRepositoryImpl @Inject constructor(
  private val api: QuickResponseApi,
  private val lookupsRepository: LookupsRepository,
  private val missedVisitEscalationApi: MissedVisitEscalationApi,
  private val eddNearingApi: EddNearingApi,
) : QuickResponseRepository {

  override suspend fun getRequests(): List<QuickResponseRequest> = coroutineScope {
    val response = api.getQuickResponseCards(status = STATUS_PENDING, cursor = null, limit = null)
    if (!response.isSuccessful) error("Failed to load Quick Response cards: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Quick Response response")
    if (!body.success) error(body.message ?: "Failed to load Quick Response cards")
    val cards = body.data?.cards.orEmpty()
    cards
      .mapNotNull { card ->
        val type = SUPPORTED_CARD_TYPES[card.cardType]
        if (type == null) {
          Log.w(LOG_TAG, "Dropping card ${card.cardId}: unrecognized cardType \"${card.cardType}\"")
          return@mapNotNull null
        }
        card to type
      }
      .map { (card, type) -> async { fetchCardDetail(card.cardId, type) } }
      .awaitAll()
      .filterNotNull()
  }

  /** Falls back to `null` (card dropped from the list) if the detail call fails, or if anything
   * else building this card from it throws (a malformed date, a lookup fetch failure inside
   * [fetchDetail]) — a card with no data at all can't render anything meaningful, unlike a
   * partial-join failure in the old per-field-join design. */
  private suspend fun fetchCardDetail(cardId: String, type: QuickResponseRequestType): QuickResponseRequest? =
    runCatching {
      val response = api.getQuickResponseCardDetail(cardId)
      val card = response.takeIf { it.isSuccessful }?.body()?.takeIf { it.success }?.data ?: return null
      val riskConditions = card.riskDetails.orEmpty().map {
        QuickResponseRiskCondition(conditionName = it.conditionName, latestGrade = it.latestGrade)
      }
      QuickResponseRequest(
        id = card.cardId,
        requestedAtEpochMillis = Instant.parse(card.raisedAt).toEpochMilli(),
        requestType = type,
        beneficiaryName = card.beneficiaryName,
        sakhiName = card.sakhiName,
        sakhiId = card.sakhiId,
        sakhiPhoneNumber = card.sakhiContactNumber,
        padaName = card.padaName,
        requestStatus = card.status,
        riskConditions = riskConditions,
        detail = fetchDetail(type, card),
      )
    }.onFailure { cause ->
      Log.w(LOG_TAG, "Dropping card $cardId: failed to build detail", cause)
    }.getOrNull()

  private suspend fun fetchDetail(type: QuickResponseRequestType, card: QuickResponseCardDetailDto): QuickResponseCardDetail? =
    when (type) {
      QuickResponseRequestType.LMP_CHANGE -> QuickResponseCardDetail.LmpChange(
        oldLmpDateEpochMillis = card.oldLmpDate?.let { parseEpochMillisOrNull(it) },
        newLmpDateEpochMillis = card.newLmpDate?.let { parseEpochMillisOrNull(it) },
        sonographyImageAssetId = card.sonographyImageAssetId,
      )
      QuickResponseRequestType.CLOSURE_REVIEW -> QuickResponseCardDetail.ClosureReview(
        reasonLabel = card.closureReasonLookupValueId?.let {
          lookupsRepository.getValueLabelById(CLOSURE_REASON_LOOKUP_CATEGORY, it)
        },
        closureDateEpochMillis = card.closureDate?.let { parseEpochMillisOrNull(it) },
        supervisorNotes = card.supervisorNotes,
      )
      QuickResponseRequestType.REOPEN -> QuickResponseCardDetail.Reopen(reasonForReopen = card.reasonForReopen)
      QuickResponseRequestType.ACCOMPANIED_REFERRAL -> QuickResponseCardDetail.AccompaniedReferral(
        facilityName = card.facilityName,
        facilityType = card.facilityType,
        referralDateEpochMillis = card.referralDate?.let { parseEpochMillisOrNull(it) },
        photoEvidenceAssetId = card.photoEvidenceAssetId,
      )
      QuickResponseRequestType.REFERRAL_INCOMPLETE -> QuickResponseCardDetail.ReferralIncomplete(
        facilityName = card.facilityName,
        facilityType = card.facilityType,
        referralDateEpochMillis = card.referralDate?.let { parseEpochMillisOrNull(it) },
        referralsMissedCount = card.referralsMissedCount,
        visitReference = card.visitReference,
        reason = card.reason,
      )
      QuickResponseRequestType.MISSED_VISIT_ESCALATION -> QuickResponseCardDetail.MissedVisitEscalation(visitType = card.visitType)
      QuickResponseRequestType.EDD_NEARING -> QuickResponseCardDetail.EddNearing(
        eddDateEpochMillis = card.eddDate?.let { parseEpochMillisOrNull(it) },
        reason = card.reason,
      )
      QuickResponseRequestType.DATA_RESTORE -> null
    }

  /** Backend date fields come as either a bare date (`"2026-07-15"`) or a full ISO-8601 instant
   * (`"2026-05-01T00:00:00.000Z"`) depending on the field — [Instant.parse] only accepts the
   * latter, so a bare date is retried with a midnight-UTC suffix. */
  private fun parseEpochMillisOrNull(dateString: String): Long? =
    runCatching { Instant.parse(dateString).toEpochMilli() }
      .recoverCatching { Instant.parse("${dateString}T00:00:00.000Z").toEpochMilli() }
      .getOrNull()

  override suspend fun decide(requestId: String, decision: QuickResponseDecision, notes: String?) {
    val request = DecideQuickResponseRequestDto(
      cardSource = CARD_SOURCE_APPROVAL_REQUESTS,
      decision = decision.name,
      decisionNotes = notes?.takeIf { it.isNotBlank() },
    )
    val response = api.decideQuickResponseCard(requestId, request)
    if (!response.isSuccessful) {
      throw QuickResponseDecisionException(response.code(), "Failed to submit decision: HTTP ${response.code()}")
    }
    val body = response.body() ?: error("Empty decision response")
    if (!body.success) error(body.message ?: "Failed to submit decision")
  }

  override suspend fun decideEscalation(requestId: String, action: QuickResponseEscalationAction) {
    val response = missedVisitEscalationApi.decide(requestId, DecideMissedVisitEscalationRequestDto(action = action.name))
    if (!response.isSuccessful) {
      throw QuickResponseDecisionException(response.code(), "Failed to submit escalation decision: HTTP ${response.code()}")
    }
    val body = response.body() ?: error("Empty decision response")
    if (!body.success) error(body.message ?: "Failed to submit escalation decision")
  }

  override suspend fun acknowledgeEddNearing(requestId: String) {
    val response = eddNearingApi.acknowledge(requestId)
    if (!response.isSuccessful) {
      throw QuickResponseDecisionException(response.code(), "Failed to acknowledge EDD Nearing card: HTTP ${response.code()}")
    }
    val body = response.body() ?: error("Empty acknowledge response")
    if (!body.success) error(body.message ?: "Failed to acknowledge EDD Nearing card")
  }
}
