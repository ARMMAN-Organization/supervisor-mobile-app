package org.armman.supervisor.data.quickresponse

import android.util.Log
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseCardDetail
import org.armman.supervisor.ui.quickresponse.QuickResponseDecision
import org.armman.supervisor.ui.quickresponse.QuickResponseDecisionException
import org.armman.supervisor.ui.quickresponse.QuickResponseEscalationAction
import org.armman.supervisor.ui.quickresponse.QuickResponseRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseRequest
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestType
import org.armman.supervisor.ui.quickresponse.QuickResponseRiskCondition
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
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
 * Backed by approval-service's Quick Response endpoints via [QuickResponseApi]. The batch
 * `GET /quick-response/details` call resolves every field for every card type in one round trip
 * — Sakhi name/contact, Pada name, beneficiary name, risk details, and the type-specific fields
 * (SRS FR-SV-4.2–4.9) — instead of one `GET /quick-response/{cardId}` call per card (that
 * per-card approach overloaded backend's downstream fan-out once fired concurrently for a real
 * card list, causing cards to silently drop — see the commit that replaced it).
 * `closureReasonLookupValueId` is the one field backend still returns as a raw lookup id rather
 * than a resolved label, so [LookupsRepository] is still used for that one field.
 */
@Singleton
class QuickResponseRepositoryImpl @Inject constructor(
  private val api: QuickResponseApi,
  private val lookupsRepository: LookupsRepository,
  private val missedVisitEscalationApi: MissedVisitEscalationApi,
  private val eddNearingApi: EddNearingApi,
  private val sessionStore: SessionStore,
  private val projectsRepository: ProjectsRepository,
) : QuickResponseRepository {

  /**
   * Backend's `GET /quick-response` does not yet scope results to the calling Supervisor's own
   * Sakhis (a reported backend gap) — it can return cards for Sakhis assigned to a different
   * Supervisor, or in a different project. Until that's fixed server-side, this cross-checks
   * every card against the caller's own assigned-Sakhi roster and drops anything that doesn't
   * belong to them, as a defense-in-depth measure against showing another Supervisor's data.
   *
   * The roster is unioned across every project [ProjectsRepository.getProjects] returns for the
   * caller, not just `session.projectId` — a Supervisor can have Sakhis assigned in more than one
   * project (the Dashboard's own location switcher confirms this), so scoping to only the
   * session's project would wrongly drop a legitimate card for one of the caller's own Sakhis in
   * a different project.
   *
   * A card whose `sakhiId` couldn't be resolved at all (`null` — e.g. the backend's own
   * enrichment lookup failed) is kept rather than dropped: absence of a Sakhi id is not proof the
   * card belongs to someone else, and dropping it would hide a legitimate card for an unrelated
   * reason.
   */
  override suspend fun getRequests(): List<QuickResponseRequest> {
    val session = sessionStore.readSession() ?: error("No active session")
    val projects = projectsRepository.getProjects()
    val mySakhiIds = projects.flatMapTo(mutableSetOf()) { projectsRepository.getMySakhiIds(it.id, session.subjectId) }

    val response = api.getQuickResponseCards(status = STATUS_PENDING, cursor = null, limit = null)
    if (!response.isSuccessful) error("Failed to load Quick Response cards: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Quick Response response")
    if (!body.success) error(body.message ?: "Failed to load Quick Response cards")
    val cardsByType = body.data?.cards.orEmpty().mapNotNull { card ->
      val type = SUPPORTED_CARD_TYPES[card.cardType]
      if (type == null) {
        Log.w(LOG_TAG, "Dropping card ${card.cardId}: unrecognized cardType \"${card.cardType}\"")
        return@mapNotNull null
      }
      card.cardId to type
    }
    if (cardsByType.isEmpty()) return emptyList()

    return fetchCardDetails(cardsByType.toMap())
      .filter { request ->
        val sakhiId = request.sakhiId
        val belongsToCaller = sakhiId == null || sakhiId in mySakhiIds
        if (!belongsToCaller) {
          Log.w(LOG_TAG, "Dropping card ${request.id}: sakhiId $sakhiId is not assigned to the current Supervisor")
        }
        belongsToCaller
      }
  }

  /** One batch call for every card's full detail, keyed by [typeByCardId] (cardId -> its already-
   * validated [QuickResponseRequestType]). A card missing from the response entirely, or present
   * with [QuickResponseCardBatchDetailDto.error] set (backend resolved it independently and that
   * one card's resolution failed — e.g. its beneficiary record wasn't found), is dropped rather
   * than rendered with missing data — mirrors the single-card fallback's "a card with no data
   * can't render anything meaningful" stance. The batch call itself failing (network exception,
   * non-2xx, unsuccessful envelope) is a different case and propagates instead of being treated
   * the same as "the backend legitimately returned zero cards" — otherwise a transient outage
   * would present as an empty Quick Response list instead of the screen's existing error/retry
   * state. */
  private suspend fun fetchCardDetails(typeByCardId: Map<String, QuickResponseRequestType>): List<QuickResponseRequest> {
    val response = runCatching { api.getQuickResponseCardDetails(typeByCardId.keys.joinToString(",")) }
      .onFailure { cause -> Log.w(LOG_TAG, "Failed to load Quick Response card details", cause) }
      .getOrThrow()
    if (!response.isSuccessful) error("Failed to load Quick Response card details: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Quick Response card details response")
    if (!body.success) error(body.message ?: "Failed to load Quick Response card details")
    val cards = body.data.orEmpty()

    return cards.mapNotNull { card ->
      val type = typeByCardId[card.cardId] ?: return@mapNotNull null
      if (card.error != null) {
        Log.w(LOG_TAG, "Dropping card ${card.cardId}: ${card.error}")
        return@mapNotNull null
      }
      runCatching { buildRequest(card, type) }
        .onFailure { cause -> Log.w(LOG_TAG, "Dropping card ${card.cardId}: failed to build detail", cause) }
        .getOrNull()
    }
  }

  private suspend fun buildRequest(card: QuickResponseCardBatchDetailDto, type: QuickResponseRequestType): QuickResponseRequest {
    val riskConditions = card.riskDetails.orEmpty().map {
      QuickResponseRiskCondition(conditionName = it.conditionName, latestGrade = it.latestGrade)
    }
    return QuickResponseRequest(
      id = card.cardId,
      requestedAtEpochMillis = Instant.parse(card.raisedAt).toEpochMilli(),
      requestType = type,
      beneficiaryName = card.beneficiaryName,
      sakhiName = card.sakhiName,
      sakhiId = card.sakhiId,
      sakhiEmployeeCode = card.sakhiEmployeeCode,
      sakhiPhoneNumber = card.sakhiContactNumber,
      padaName = card.padaName,
      requestStatus = card.status,
      riskConditions = riskConditions,
      detail = fetchDetail(type, card),
    )
  }

  private suspend fun fetchDetail(type: QuickResponseRequestType, card: QuickResponseCardBatchDetailDto): QuickResponseCardDetail? =
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
        closureType = card.closureType,
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

  /** Backend date fields come in one of three shapes depending on the field: a bare date
   * (`"2026-07-15"`), a full ISO-8601 instant (`"2026-05-01T00:00:00.000Z"`), or (seen on some
   * fields) an offset-less local datetime (`"2026-07-15T10:30:00"`) — [Instant.parse] only
   * accepts the instant form, so the other two are retried in turn: first as a bare date with a
   * midnight-UTC suffix, then (if that still fails, i.e. it already had a time component) as a
   * local datetime assumed to be UTC. */
  private fun parseEpochMillisOrNull(dateString: String): Long? =
    runCatching { Instant.parse(dateString).toEpochMilli() }
      .recoverCatching { Instant.parse("${dateString}T00:00:00.000Z").toEpochMilli() }
      .recoverCatching { LocalDateTime.parse(dateString).toInstant(ZoneOffset.UTC).toEpochMilli() }
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
