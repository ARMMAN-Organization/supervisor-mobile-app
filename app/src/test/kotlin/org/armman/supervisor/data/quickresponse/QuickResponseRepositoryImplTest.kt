package org.armman.supervisor.data.quickresponse

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.auth.UserSession
import org.armman.supervisor.data.auth.session.SecureKeyValueStore
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupValueDto
import org.armman.supervisor.data.lookups.LookupsApi
import org.armman.supervisor.data.lookups.LookupsEnvelopeDto
import org.armman.supervisor.data.lookups.LookupsRepository
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
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

/** In-memory [SecureKeyValueStore] so [SessionStore] can be exercised without Android Keystore. */
private class FakeSecureKeyValueStore : SecureKeyValueStore {
  private val values = mutableMapOf<String, String>()
  override fun getString(key: String): String? = values[key]
  override fun putString(key: String, value: String): Boolean {
    values[key] = value
    return true
  }
  override fun remove(key: String) {
    values.remove(key)
  }
}

/** A logged-in session for Supervisor "sup-1" on project "proj-1", the default every test in
 * this file runs as unless a test overrides it — matches this suite's existing card fixtures,
 * which were all written before Quick Response was scoped to the caller's own Sakhis. */
private val DEFAULT_SESSION = UserSession(
  username = "supervisor@example.com",
  subjectId = "sup-1",
  roles = listOf("SUPERVISOR"),
  projectId = "proj-1",
  geographyUnitId = null,
  accessToken = "token",
  refreshToken = "refresh",
  accessTokenExpiresAtEpochSeconds = Long.MAX_VALUE,
)

/**
 * Fake [ProjectsRepository] whose [getMySakhiIds] returns everything by default — this suite's
 * existing tests exercise card-mapping behavior, not Sakhi-scoping, so they must keep seeing
 * every card unless a test explicitly narrows [assignedSakhiIds]. Only the "Sakhi-scoping"
 * tests below override it to a restrictive set.
 *
 * [getProjects] defaults to just [DEFAULT_SESSION]'s own project ("proj-1"), matching every
 * existing test's single-project assumption; multi-project tests override [projects] and use
 * [assignedSakhiIdsByProject] to give each project its own roster.
 */
private class FakeProjectsRepository : ProjectsRepository {
  /** `null` (the default) means "assign every Sakhi the test ever asks about" — see
   * [getMySakhiIds]. Tests that need real filtering set this explicitly. Ignored once
   * [assignedSakhiIdsByProject] is set. */
  var assignedSakhiIds: Set<String>? = null

  /** Per-project override for multi-project tests — takes priority over [assignedSakhiIds] when
   * non-null. Keyed by projectId, same "missing key -&gt; empty roster" semantics as a real
   * roster fetch that returns no Sakhis for a project the supervisor isn't assigned in. */
  var assignedSakhiIdsByProject: Map<String, Set<String>>? = null
  var projects: List<LocationOption> = listOf(LocationOption(id = "proj-1", name = "Project 1"))
  var failGetMySakhiIds = false
  var failGetProjects = false
  val requestedProjectIds = mutableListOf<String>()

  override suspend fun getProjects(): List<LocationOption> {
    if (failGetProjects) error("Simulated failure loading projects")
    return projects
  }
  override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used by these tests")
  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used by these tests")
  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used by these tests")
  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used by these tests")
  override fun clearCache() = error("not used by these tests")

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> {
    if (failGetMySakhiIds) error("Simulated failure loading Sakhi roster")
    requestedProjectIds += projectId
    assignedSakhiIdsByProject?.let { return it[projectId] ?: emptySet() }
    return assignedSakhiIds ?: ALL_SAKHI_IDS
  }

  companion object {
    /** Stands in for "every Sakhi id any test fixture in this file uses" so the default
     * (`assignedSakhiIds == null`) behaves as "unrestricted" without each existing test having
     * to enumerate the Sakhi ids its own fixtures happen to use. */
    val ALL_SAKHI_IDS = setOf("sakhi-1", "sakhi-2", "sakhi-3", "sakhi-assigned", "sakhi-other-supervisor")
  }
}

/** Converts one single-card [QuickResponseCardDetailDto] fixture into its batch-shaped
 * equivalent — [detail]'s existing fixtures are reused unchanged (same field values, `error =
 * null`) so every pre-existing test built around single-card fixtures keeps working against the
 * batch endpoint without being rewritten. */
private fun QuickResponseCardDetailDto.toBatchDto() = QuickResponseCardBatchDetailDto(
  cardId = cardId,
  cardType = cardType,
  cardSource = cardSource,
  beneficiaryId = beneficiaryId,
  raisedAt = raisedAt,
  error = null,
  padaName = padaName,
  sakhiName = sakhiName,
  sakhiId = sakhiId,
  sakhiEmployeeCode = sakhiEmployeeCode,
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
)

private class FakeQuickResponseApi : QuickResponseApi {
  var listResult: Response<QuickResponseListEnvelopeDto> =
    Response.success(QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)))
  var detailResultsById: Map<String, Response<QuickResponseCardDetailEnvelopeDto>> = emptyMap()

  /** Set directly (instead of via [detailResultsById]) by tests exercising the batch endpoint's
   * own semantics — a per-card `error` string, or a card entirely missing from the response. */
  var batchDetailsResult: Response<QuickResponseBatchDetailEnvelopeDto>? = null
  var failBatchDetailsCall = false
  var lastBatchCardIds: String? = null

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

  /** [batchDetailsResult], when set, takes priority — otherwise synthesizes a batch response
   * from [detailResultsById] so existing single-card-fixture-based tests need no changes. A
   * cardId with no fixture (an unrecognized/decode-failure case) is simply absent from the
   * batch response, matching how a real batch endpoint would omit an id it couldn't resolve at
   * all (as opposed to resolving it with `error` set). */
  override suspend fun getQuickResponseCardDetails(cardIds: String): Response<QuickResponseBatchDetailEnvelopeDto> {
    lastBatchCardIds = cardIds
    if (failBatchDetailsCall) return Response.error(500, "".toResponseBody(null))
    batchDetailsResult?.let { return it }
    val ids = cardIds.split(",")
    val cards = ids.mapNotNull { id ->
      detailResultsById[id]?.takeIf { it.isSuccessful }?.body()?.takeIf { it.success }?.data?.toBatchDto()
    }
    return Response.success(QuickResponseBatchDetailEnvelopeDto(success = true, message = "OK", data = cards))
  }

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
  private val sessionStore = SessionStore(FakeSecureKeyValueStore()).apply { saveSession(DEFAULT_SESSION) }
  private val projectsRepository = FakeProjectsRepository()
  private val repository = QuickResponseRepositoryImpl(
    api,
    LookupsRepository(lookupsApi),
    missedVisitEscalationApi,
    eddNearingApi,
    sessionStore,
    projectsRepository,
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
        sakhiEmployeeCode = null,
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
  fun `getRequests parses an offset-less local datetime date field`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "LMP_CHANGE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "LMP_CHANGE", oldLmpDate = "2026-07-15T10:30:00", newLmpDate = null),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.LmpChange
    assertEquals(
      java.time.LocalDateTime.parse("2026-07-15T10:30:00").toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
      detail.oldLmpDateEpochMillis,
    )
  }

  @Test
  fun `getRequests resolves Closure Review reason label via lookups`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "CLOSURE_REVIEW"))
    api.detailResultsById = mapOf(
      "card-1" to detail(
        "card-1",
        "CLOSURE_REVIEW",
        closureReasonLookupValueId = "reason-1",
        closureType = "MOTHER_CLOSURE",
        closureDate = "2026-08-20T00:00:00Z",
        supervisorNotes = "note",
      ),
    )
    lookupsApi.categories = listOf(
      LookupCategoryDto(categoryCode = "CLOSURE_REASON", values = listOf(LookupValueDto(id = "reason-1", valueCode = "MIGRATION", valueLabel = "Migration"))),
    )

    val requests = repository.getRequests()

    val detail = requests[0].detail as QuickResponseCardDetail.ClosureReview
    assertEquals("Migration", detail.reasonLabel)
    assertEquals("MOTHER_CLOSURE", detail.closureType)
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
    // No entry in detailResultsById -> the batch response omits it entirely.

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

  // --- Sakhi scoping (defense-in-depth against a backend gap: GET /quick-response does not yet
  // scope cards to the calling Supervisor's own Sakhis) ---

  @Test
  fun `getRequests keeps a card whose sakhiId is assigned to the calling supervisor`() = runTest {
    projectsRepository.assignedSakhiIds = setOf("sakhi-assigned")
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-assigned"),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-1", requests[0].id)
  }

  @Test
  fun `getRequests drops a card whose sakhiId belongs to a different supervisor`() = runTest {
    projectsRepository.assignedSakhiIds = setOf("sakhi-assigned")
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-other-supervisor"),
    )

    val requests = repository.getRequests()

    assertTrue(requests.isEmpty())
  }

  @Test
  fun `getRequests keeps a card whose sakhiId could not be resolved (null)`() = runTest {
    projectsRepository.assignedSakhiIds = setOf("sakhi-assigned")
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "REOPEN", sakhiId = null),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
  }

  @Test(expected = IllegalStateException::class)
  fun `getRequests fails closed when loading the assigned-Sakhi roster fails`() = runTest {
    projectsRepository.failGetMySakhiIds = true
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "REOPEN", sakhiId = "sakhi-assigned"))

    repository.getRequests()
  }

  @Test
  fun `getRequests keeps only assigned and null-sakhi cards out of a mixed list`() = runTest {
    projectsRepository.assignedSakhiIds = setOf("sakhi-assigned")
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(
          cards = listOf(
            card("card-assigned", "DATA_RESTORE", beneficiaryId = null),
            card("card-other", "DATA_RESTORE", beneficiaryId = null),
            card("card-unresolved", "REOPEN"),
          ),
          nextCursor = null,
        ),
      ),
    )
    api.detailResultsById = mapOf(
      "card-assigned" to detail("card-assigned", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-assigned"),
      "card-other" to detail("card-other", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-other-supervisor"),
      "card-unresolved" to detail("card-unresolved", "REOPEN", sakhiId = null),
    )

    val requests = repository.getRequests()

    assertEquals(setOf("card-assigned", "card-unresolved"), requests.map { it.id }.toSet())
  }

  @Test
  fun `getRequests returns an empty list, not an error, when the supervisor has no assigned sakhis`() = runTest {
    projectsRepository.assignedSakhiIds = emptySet()
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-assigned"),
    )

    val requests = repository.getRequests()

    assertTrue(requests.isEmpty())
  }

  @Test
  fun `getRequests keeps a card for the caller's own sakhi in a project other than the session's`() = runTest {
    projectsRepository.projects = listOf(
      LocationOption(id = "proj-1", name = "Project 1"),
      LocationOption(id = "proj-2", name = "Project 2"),
    )
    projectsRepository.assignedSakhiIdsByProject = mapOf(
      "proj-1" to setOf("sakhi-assigned"),
      "proj-2" to setOf("sakhi-in-other-project"),
    )
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-in-other-project"),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-1", requests[0].id)
  }

  @Test
  fun `getRequests drops a card for a sakhi in none of the caller's projects`() = runTest {
    projectsRepository.projects = listOf(
      LocationOption(id = "proj-1", name = "Project 1"),
      LocationOption(id = "proj-2", name = "Project 2"),
    )
    projectsRepository.assignedSakhiIdsByProject = mapOf(
      "proj-1" to setOf("sakhi-assigned"),
      "proj-2" to setOf("sakhi-in-other-project"),
    )
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-other-supervisor"),
    )

    val requests = repository.getRequests()

    assertTrue(requests.isEmpty())
  }

  @Test
  fun `getRequests behaves exactly as before for a single-project supervisor`() = runTest {
    projectsRepository.assignedSakhiIds = setOf("sakhi-assigned")
    api.listResult = Response.success(listOf1("card-1", "DATA_RESTORE"))
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "DATA_RESTORE", beneficiaryName = null, sakhiId = "sakhi-assigned"),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals(listOf("proj-1"), projectsRepository.requestedProjectIds)
  }

  @Test(expected = IllegalStateException::class)
  fun `getRequests fails closed when loading the caller's projects fails`() = runTest {
    projectsRepository.failGetProjects = true
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.detailResultsById = mapOf("card-1" to detail("card-1", "REOPEN", sakhiId = "sakhi-assigned"))

    repository.getRequests()
  }

  // --- Batch detail endpoint (GET /quick-response/details) semantics ---

  @Test
  fun `getRequests calls the batch endpoint once with every pending card's id`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(cards = listOf(card("card-1", "REOPEN"), card("card-2", "CLOSURE_REVIEW")), nextCursor = null),
      ),
    )
    api.detailResultsById = mapOf(
      "card-1" to detail("card-1", "REOPEN"),
      "card-2" to detail("card-2", "CLOSURE_REVIEW"),
    )

    repository.getRequests()

    assertEquals(setOf("card-1", "card-2"), api.lastBatchCardIds?.split(",")?.toSet())
  }

  @Test
  fun `getRequests does not call the batch endpoint when there are no pending cards`() = runTest {
    api.listResult = Response.success(QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)))

    repository.getRequests()

    assertNull(api.lastBatchCardIds)
  }

  @Test
  fun `getRequests drops only the card whose batch entry carries an error, keeping the rest`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(cards = listOf(card("card-bad", "REOPEN"), card("card-good", "REOPEN")), nextCursor = null),
      ),
    )
    api.batchDetailsResult = Response.success(
      QuickResponseBatchDetailEnvelopeDto(
        success = true,
        message = "OK",
        data = listOf(
          detail("card-bad", "REOPEN").body()!!.data!!.toBatchDto().copy(error = "The beneficiary linked to this card was not found."),
          detail("card-good", "REOPEN").body()!!.data!!.toBatchDto(),
        ),
      ),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-good", requests[0].id)
  }

  @Test
  fun `getRequests returns an empty list, not an error, when the batch call itself fails`() = runTest {
    api.listResult = Response.success(listOf1("card-1", "REOPEN"))
    api.failBatchDetailsCall = true

    val requests = repository.getRequests()

    assertTrue(requests.isEmpty())
  }

  @Test
  fun `getRequests drops a card missing from the batch response entirely`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(cards = listOf(card("card-missing", "REOPEN"), card("card-present", "REOPEN")), nextCursor = null),
      ),
    )
    api.batchDetailsResult = Response.success(
      QuickResponseBatchDetailEnvelopeDto(
        success = true,
        message = "OK",
        data = listOf(detail("card-present", "REOPEN").body()!!.data!!.toBatchDto()),
      ),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-present", requests[0].id)
  }
}
