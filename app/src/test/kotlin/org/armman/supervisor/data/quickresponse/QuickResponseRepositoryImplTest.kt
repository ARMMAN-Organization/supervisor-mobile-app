package org.armman.supervisor.data.quickresponse

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupValueDto
import org.armman.supervisor.data.lookups.LookupsApi
import org.armman.supervisor.data.lookups.LookupsEnvelopeDto
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.ui.quickresponse.QuickResponseCardDetail
import org.armman.supervisor.ui.quickresponse.QuickResponseDecision
import org.armman.supervisor.ui.quickresponse.QuickResponseDecisionException
import org.armman.supervisor.ui.quickresponse.QuickResponseEscalationAction
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeQuickResponseApi : QuickResponseApi {
  var listResult: Response<QuickResponseListEnvelopeDto> =
    Response.success(QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)))
  var detailResultsById: Map<String, Response<QuickResponseCardDetailEnvelopeDto>> = emptyMap()
  var decisionResult: Response<DecideQuickResponseEnvelopeDto> =
    Response.success(
      DecideQuickResponseEnvelopeDto(
        success = true,
        message = "OK",
        data = DecideQuickResponseDto("card-1", "approval_requests", "APPROVE"),
      ),
    )
  var lastDecisionCardId: String? = null
  var lastDecisionRequest: DecideQuickResponseRequestDto? = null

  override suspend fun getQuickResponseCards(status: String, cursor: String?, limit: Int?) = listResult

  override suspend fun getQuickResponseCardDetail(cardId: String): Response<QuickResponseCardDetailEnvelopeDto> =
    detailResultsById[cardId] ?: Response.error(404, "".toResponseBody(null))

  override suspend fun decideQuickResponseCard(cardId: String, request: DecideQuickResponseRequestDto): Response<DecideQuickResponseEnvelopeDto> {
    lastDecisionCardId = cardId
    lastDecisionRequest = request
    return decisionResult
  }
}

private class FakeMissedVisitEscalationApi : MissedVisitEscalationApi {
  var result: Response<DecideMissedVisitEscalationEnvelopeDto> =
    Response.success(DecideMissedVisitEscalationEnvelopeDto(success = true, message = "OK"))
  var lastRequestId: String? = null
  var lastAction: String? = null

  override suspend fun decide(id: String, request: DecideMissedVisitEscalationRequestDto): Response<DecideMissedVisitEscalationEnvelopeDto> {
    lastRequestId = id
    lastAction = request.action
    return result
  }
}

private class FakeEddNearingApi : EddNearingApi {
  var result: Response<AcknowledgeEddNearingEnvelopeDto> =
    Response.success(AcknowledgeEddNearingEnvelopeDto(success = true, message = "OK"))
  var lastRequestId: String? = null

  override suspend fun acknowledge(id: String): Response<AcknowledgeEddNearingEnvelopeDto> {
    lastRequestId = id
    return result
  }
}

private class FakeLookupsApi : LookupsApi {
  var categories: List<LookupCategoryDto> = emptyList()

  override suspend fun getLookups(): Response<LookupsEnvelopeDto> =
    Response.success(LookupsEnvelopeDto(success = true, message = "OK", data = categories))

  override suspend fun getCategory(categoryCode: String) = error("not used by these tests")
}

class QuickResponseRepositoryImplTest {
  private val api = FakeQuickResponseApi()
  private val lookupsApi = FakeLookupsApi()
  private val missedVisitEscalationApi = FakeMissedVisitEscalationApi()
  private val eddNearingApi = FakeEddNearingApi()
  private val repository = QuickResponseRepositoryImpl(
    api,
    LookupsRepository(lookupsApi),
    missedVisitEscalationApi,
    eddNearingApi,
  )

  private fun card(id: String, cardType: String, beneficiaryId: String? = "ben-1", raisedAt: String = "2026-08-07T10:00:00Z") =
    QuickResponseCardDto(cardId = id, cardType = cardType, cardSource = "approval_requests", beneficiaryId = beneficiaryId, raisedAt = raisedAt)

  /** Builds a detail response with every field defaulted to `null`/empty except what a test
   * explicitly overrides — mirrors how backend's single enriched endpoint returns every field
   * for every card type, with only the relevant ones populated per [cardType]. */
  private fun detail(
    cardId: String,
    cardType: String,
    raisedAt: String = "2026-08-07T10:00:00Z",
    beneficiaryName: String? = "Test Beneficiary",
    padaName: String? = null,
    sakhiName: String? = null,
    sakhiId: String? = null,
    sakhiContactNumber: String? = null,
    riskDetails: List<RiskDetailDto>? = null,
    status: String? = null,
    oldLmpDate: String? = null,
    newLmpDate: String? = null,
    sonographyImageAssetId: String? = null,
    visitType: String? = null,
    closureType: String? = null,
    closureReasonLookupValueId: String? = null,
    closureDate: String? = null,
    supervisorNotes: String? = null,
    referralDate: String? = null,
    facilityName: String? = null,
    facilityType: String? = null,
    photoEvidenceAssetId: String? = null,
    visitReference: String? = null,
    referralsMissedCount: Int? = null,
    reason: String? = null,
    reasonForReopen: String? = null,
    eddDate: String? = null,
  ) = Response.success(
    QuickResponseCardDetailEnvelopeDto(
      success = true,
      message = "OK",
      data = QuickResponseCardDetailDto(
        cardId = cardId,
        cardType = cardType,
        cardSource = "approval_requests",
        beneficiaryId = "ben-1",
        raisedAt = raisedAt,
        padaName = padaName,
        sakhiName = sakhiName,
        sakhiId = sakhiId,
        sakhiContactNumber = sakhiContactNumber,
        beneficiaryName = beneficiaryName,
        riskDetails = riskDetails,
        status = status,
        oldLmpDate = oldLmpDate,
        newLmpDate = newLmpDate,
        sonographyImageAssetId = sonographyImageAssetId,
        visitType = visitType,
        closureType = closureType,
        closureReasonLookupValueId = closureReasonLookupValueId,
        closureDate = closureDate,
        supervisorNotes = supervisorNotes,
        referralDate = referralDate,
        facilityName = facilityName,
        facilityType = facilityType,
        photoEvidenceAssetId = photoEvidenceAssetId,
        visitReference = visitReference,
        referralsMissedCount = referralsMissedCount,
        reason = reason,
        reasonForReopen = reasonForReopen,
        eddDate = eddDate,
      ),
    ),
  )

  private fun listOf1(cardId: String, cardType: String) = QuickResponseListEnvelopeDto(
    success = true,
    message = "OK",
    data = QuickResponseListDto(cards = listOf(card(cardId, cardType)), nextCursor = null),
  )

  // --- Positive ---

  @Test
  fun `getRequests maps each of the 8 supported card types`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(
          cards = listOf(
            card("card-1", "LMP_CHANGE"),
            card("card-2", "CLOSURE_REVIEW"),
            card("card-3", "REOPEN"),
            card("card-4", "ACCOMPANIED_REFERRAL"),
            card("card-5", "REFERRAL_INCOMPLETE"),
            card("card-6", "DATA_RESTORE", beneficiaryId = null),
            card("card-7", "MISSED_VISIT"),
            card("card-8", "EDD_NEARING"),
          ),
          nextCursor = null,
        ),
      ),
    )
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "LMP_CHANGE"),
      "card-2" to detail("card-2", "CLOSURE_REVIEW"),
      "card-3" to detail("card-3", "REOPEN"),
      "card-4" to detail("card-4", "ACCOMPANIED_REFERRAL"),
      "card-5" to detail("card-5", "REFERRAL_INCOMPLETE"),
      "card-6" to detail("card-6", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-1"),
      "card-7" to detail("card-7", "MISSED_VISIT"),
      "card-8" to detail("card-8", "EDD_NEARING"),
    )

    val requests = repository.getRequests()

    assertEquals(8, requests.size)
    assertEquals(
      listOf(
        QuickResponseRequestType.LMP_CHANGE,
        QuickResponseRequestType.CLOSURE_REVIEW,
        QuickResponseRequestType.REOPEN,
        QuickResponseRequestType.ACCOMPANIED_REFERRAL,
        QuickResponseRequestType.REFERRAL_INCOMPLETE,
        QuickResponseRequestType.DATA_RESTORE,
        QuickResponseRequestType.MISSED_VISIT_ESCALATION,
        QuickResponseRequestType.EDD_NEARING,
      ),
      requests.map { it.requestType },
    )
  }

  @Test
  fun `getRequests maps base fields directly from the enriched detail response`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf(
      "card-1" to detail(
        "card-1", "REOPEN",
        beneficiaryName = "Sunita Devi",
        padaName = "Test Pada",
        sakhiName = "Meera Sakhi",
        sakhiContactNumber = "+919000000003",
        status = "PENDING",
        riskDetails = listOf(RiskDetailDto(conditionName = "Hypertension (High BP)", latestGrade = "SEVERE")),
      ),
    )

    val requests = repository.getRequests()

    assertEquals("Sunita Devi", requests[0].beneficiaryName)
    assertEquals("Test Pada", requests[0].padaName)
    assertEquals("Meera Sakhi", requests[0].sakhiName)
    assertEquals("+919000000003", requests[0].sakhiPhoneNumber)
    assertEquals("PENDING", requests[0].requestStatus)
    assertEquals(1, requests[0].riskConditions.size)
    assertEquals("Hypertension (High BP)", requests[0].riskConditions[0].conditionName)
    assertEquals("SEVERE", requests[0].riskConditions[0].latestGrade)
  }

  @Test
  fun `getRequests maps LMP Change detail fields`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "LMP_CHANGE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "LMP_CHANGE", oldLmpDate = "2026-05-01T00:00:00.000Z", newLmpDate = "2026-07-15", sonographyImageAssetId = null),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.LmpChange
    assertTrue(detail.oldLmpDateEpochMillis != null)
    assertTrue(detail.newLmpDateEpochMillis != null)
    assertNull(detail.sonographyImageAssetId)
  }

  @Test
  fun `getRequests resolves Closure Review reason label via lookups`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "CLOSURE_REVIEW"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "CLOSURE_REVIEW", closureReasonLookupValueId = "reason-1", closureDate = "2026-08-20T00:00:00Z", supervisorNotes = "note"),
    )
    lookupsApi.categories = listOf(
      LookupCategoryDto(categoryCode = "CLOSURE_REASON", values = listOf(LookupValueDto(id = "reason-1", valueCode = "MIGRATION", valueLabel = "Migration"))),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.ClosureReview
    assertEquals("Migration", detail.reasonLabel)
    assertEquals("note", detail.supervisorNotes)
  }

  @Test
  fun `getRequests maps Reopen reasonForReopen`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "REOPEN", reasonForReopen = "CLOSED_BY_MISTAKE"))

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.Reopen
    assertEquals("CLOSED_BY_MISTAKE", detail.reasonForReopen)
  }

  @Test
  fun `getRequests maps Accompanied Referral fields`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "ACCOMPANIED_REFERRAL"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "ACCOMPANIED_REFERRAL", facilityName = "Test PHC", facilityType = "PHC", referralDate = "2026-08-20T00:00:00Z", photoEvidenceAssetId = null),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.AccompaniedReferral
    assertEquals("Test PHC", detail.facilityName)
    assertEquals("PHC", detail.facilityType)
    assertNull(detail.photoEvidenceAssetId)
  }

  @Test
  fun `getRequests maps Referral Follow-up Incomplete fields as a distinct detail type`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REFERRAL_INCOMPLETE"))
    api.detailResultsById = mapOf(
      "card-1" to detail(
        "card-1", "REFERRAL_INCOMPLETE",
        facilityName = "Test PHC", facilityType = "PHC", referralDate = "2026-08-20T00:00:00Z",
        visitReference = "ANC-3", referralsMissedCount = 2, reason = "Beneficiary unreachable",
      ),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.ReferralIncomplete
    assertEquals("ANC-3", detail.visitReference)
    assertEquals(2, detail.referralsMissedCount)
    assertEquals("Beneficiary unreachable", detail.reason)
  }

  @Test
  fun `getRequests maps Missed Visit Escalation visitType, no visits-missed count field`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "MISSED_VISIT"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "MISSED_VISIT", visitType = "ANC_2_MISSED"))

    val requests = repository.getRequests()

    assertEquals(QuickResponseRequestType.MISSED_VISIT_ESCALATION, requests[0].requestType)
    val detail = requests[0].detail as QuickResponseCardDetail.MissedVisitEscalation
    assertEquals("ANC_2_MISSED", detail.visitType)
  }

  @Test
  fun `getRequests maps EDD Nearing fields`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "EDD_NEARING"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "EDD_NEARING", eddDate = "2027-01-15T00:00:00.000Z", reason = "EDD approaching on 2027-01-15"))

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.EddNearing
    assertTrue(detail.eddDateEpochMillis != null)
    assertEquals("EDD approaching on 2027-01-15", detail.reason)
  }

  @Test
  fun `getRequests maps Data Restore sakhi fields with no beneficiary`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(cards = listOf(card("card-1", "DATA_RESTORE", beneficiaryId = null)), nextCursor = null),
      ),
    )
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiName = "Meera Sakhi", sakhiId = "sakhi-1"),
    )

    val requests = repository.getRequests()

    assertNull(requests[0].beneficiaryName)
    assertEquals("Meera Sakhi", requests[0].sakhiName)
    assertEquals("sakhi-1", requests[0].sakhiId)
    assertNull(requests[0].detail)
  }

  @Test
  fun `decide calls the decision endpoint with the card source and decision`() = runTest {
    repository.decide("card-1", QuickResponseDecision.APPROVE)

    assertEquals("card-1", api.lastDecisionCardId)
    assertEquals("approval_requests", api.lastDecisionRequest?.cardSource)
    assertEquals("APPROVE", api.lastDecisionRequest?.decision)
    assertNull(api.lastDecisionRequest?.decisionReasonCodeLookupId)
  }

  @Test
  fun `decide sends REJECT`() = runTest {
    repository.decide("card-1", QuickResponseDecision.REJECT)

    assertEquals("REJECT", api.lastDecisionRequest?.decision)
  }

  @Test
  fun `decide sends notes on reject`() = runTest {
    repository.decide("card-1", QuickResponseDecision.REJECT, notes = "Beneficiary confirmed by phone.")

    assertEquals("Beneficiary confirmed by phone.", api.lastDecisionRequest?.decisionNotes)
  }

  @Test
  fun `decide omits blank notes rather than sending an empty string`() = runTest {
    repository.decide("card-1", QuickResponseDecision.REJECT, notes = "   ")

    assertNull(api.lastDecisionRequest?.decisionNotes)
  }

  @Test
  fun `decideEscalation sends the action verbatim`() = runTest {
    repository.decideEscalation("escalation-1", QuickResponseEscalationAction.CLOSE)

    assertEquals("escalation-1", missedVisitEscalationApi.lastRequestId)
    assertEquals("CLOSE", missedVisitEscalationApi.lastAction)
  }

  @Test
  fun `decideEscalation sends TRANSFER`() = runTest {
    repository.decideEscalation("escalation-1", QuickResponseEscalationAction.TRANSFER)

    assertEquals("TRANSFER", missedVisitEscalationApi.lastAction)
  }

  @Test
  fun `decideEscalation throws QuickResponseDecisionException on the known 501 for Transfer`() = runTest {
    missedVisitEscalationApi.result = Response.error(501, "".toResponseBody(null))

    val exception = runCatching { repository.decideEscalation("escalation-1", QuickResponseEscalationAction.TRANSFER) }.exceptionOrNull()

    assertTrue(exception is QuickResponseDecisionException)
    assertEquals(501, (exception as QuickResponseDecisionException).httpStatusCode)
  }

  @Test
  fun `acknowledgeEddNearing calls the acknowledge endpoint`() = runTest {
    repository.acknowledgeEddNearing("edd-1")

    assertEquals("edd-1", eddNearingApi.lastRequestId)
  }

  @Test(expected = IllegalStateException::class)
  fun `acknowledgeEddNearing throws when the envelope reports failure`() = runTest {
    eddNearingApi.result = Response.success(AcknowledgeEddNearingEnvelopeDto(success = false, message = "Escalation event not found."))

    repository.acknowledgeEddNearing("edd-1")
  }

  // --- Negative ---

  @Test(expected = IllegalStateException::class)
  fun `getRequests throws on an HTTP error`() = runTest {
    api.listResult = Response.error(500, "".toResponseBody(null))

    repository.getRequests()
  }

  @Test(expected = IllegalStateException::class)
  fun `getRequests throws when the envelope reports failure`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(success = false, message = "Unauthorized", data = null),
    )

    repository.getRequests()
  }

  @Test
  fun `decide throws QuickResponseDecisionException carrying the HTTP status on conflict`() = runTest {
    api.decisionResult = Response.error(409, "".toResponseBody(null))

    val exception = runCatching { repository.decide("card-1", QuickResponseDecision.APPROVE) }.exceptionOrNull()

    assertTrue(exception is QuickResponseDecisionException)
    assertEquals(409, (exception as QuickResponseDecisionException).httpStatusCode)
  }

  @Test(expected = IllegalStateException::class)
  fun `decide throws when the envelope reports failure`() = runTest {
    api.decisionResult = Response.success(
      DecideQuickResponseEnvelopeDto(success = false, message = "Card not found", data = null),
    )

    repository.decide("card-1", QuickResponseDecision.APPROVE)
  }

  // --- Edge cases ---

  @Test
  fun `getRequests filters out unsupported card types`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(
          cards = listOf(card("card-1", "LMP_CHANGE"), card("card-2", "SOME_FUTURE_UNMODELLED_TYPE")),
          nextCursor = null,
        ),
      ),
    )
    api.detailResultsById = mapOf("card-1" to detail("card-1", "LMP_CHANGE"))

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-1", requests[0].id)
  }

  @Test
  fun `getRequests returns an empty list when there are no cards`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)),
    )

    assertTrue(repository.getRequests().isEmpty())
  }

  @Test
  fun `getRequests drops a card entirely when its detail call fails`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "LMP_CHANGE"))
    // No entry in detailResultsById -> FakeQuickResponseApi returns 404 for it.

    val requests = repository.getRequests()

    assertTrue(requests.isEmpty())
  }

  @Test
  fun `getRequests drops only the card whose raisedAt is unparseable, keeping the rest`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(
          cards = listOf(card("card-bad", "LMP_CHANGE"), card("card-good", "REOPEN")),
          nextCursor = null,
        ),
      ),
    )
    api.detailResultsById = mapOf(
      "card-bad" to detail("card-bad", "LMP_CHANGE", raisedAt = "not-a-real-date"),
      "card-good" to detail("card-good", "REOPEN"),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-good", requests[0].id)
  }

  @Test
  fun `getRequests leaves reasonLabel null when the closure reason lookup id is unresolved`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "CLOSURE_REVIEW"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "CLOSURE_REVIEW", closureReasonLookupValueId = "unknown-id"))
    lookupsApi.categories = emptyList()

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.ClosureReview
    assertNull(detail.reasonLabel)
  }

  @Test
  fun `getRequests handles an empty riskDetails list without error`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "REOPEN", riskDetails = emptyList()))

    val requests = repository.getRequests()

    assertTrue(requests[0].riskConditions.isEmpty())
  }

  @Test
  fun `getRequests handles a null riskDetails field without error`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "REOPEN", riskDetails = null))

    val requests = repository.getRequests()

    assertTrue(requests[0].riskConditions.isEmpty())
  }
}
