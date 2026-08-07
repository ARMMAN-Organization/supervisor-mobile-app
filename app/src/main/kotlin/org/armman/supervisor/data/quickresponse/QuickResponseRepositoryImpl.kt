package org.armman.supervisor.data.quickresponse

import org.armman.supervisor.ui.quickresponse.QuickResponseRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseRequest
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestStatus
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestType
import org.armman.supervisor.ui.quickresponse.ReasonOption
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** approval-service's `cardType` value for Data Restore cards — the only type this screen shows. */
private const val CARD_TYPE_DATA_RESTORE = "DATA_RESTORE"

/** approval-service's `cardSource` value for cards backed by `approval_requests` (as opposed to
 * `escalation_events`) — DATA_RESTORE cards always come from this source. */
private const val CARD_SOURCE_APPROVAL_REQUESTS = "approval_requests"

/** Default `status` query value — Quick Response cards not yet decided. */
private const val STATUS_PENDING = "PENDING"

/**
 * Backed by approval-service's Quick Response endpoints via [QuickResponseApi]. Only
 * [CARD_TYPE_DATA_RESTORE] cards are surfaced — the merged feed can also return REOPEN,
 * EDD_NEARING and other card types this screen doesn't model yet.
 *
 * project/Sakhi names aren't in the Quick Response card payload, so they're left blank rather
 * than guessing at an unrelated field; see [QuickResponseRequest.projectName]'s call sites.
 */
@Singleton
class QuickResponseRepositoryImpl @Inject constructor(
  private val api: QuickResponseApi,
) : QuickResponseRepository {

  override suspend fun getRequests(): List<QuickResponseRequest> {
    val response = api.getQuickResponseCards(status = STATUS_PENDING, cursor = null, limit = null)
    if (!response.isSuccessful) error("Failed to load Quick Response cards: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Quick Response response")
    if (!body.success) error(body.message ?: "Failed to load Quick Response cards")
    val cards = body.data?.cards.orEmpty()
    return cards
      .filter { it.cardType == CARD_TYPE_DATA_RESTORE }
      .map {
        QuickResponseRequest(
          id = it.cardId,
          requestedAtEpochMillis = Instant.parse(it.raisedAt).toEpochMilli(),
          requestType = QuickResponseRequestType.DATA_RESTORE,
          projectName = "",
          sakhiName = "",
          status = QuickResponseRequestStatus.PENDING,
        )
      }
  }

  override suspend fun submitReason(requestId: String, reason: ReasonOption) {
    val request = DecideQuickResponseRequestDto(
      cardSource = CARD_SOURCE_APPROVAL_REQUESTS,
      decision = reason.name,
      decisionReasonCodeLookupId = null,
      decisionNotes = null,
    )
    val response = api.decideQuickResponseCard(requestId, request)
    if (!response.isSuccessful) error("Failed to submit decision: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty decision response")
    if (!body.success) error(body.message ?: "Failed to submit decision")
  }
}
