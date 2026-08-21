package org.armman.supervisor.data.callsheet

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.calllog.CallLogApi
import org.armman.supervisor.data.calllog.CallLogDto
import org.armman.supervisor.data.calllog.CallLogEnvelopeDto
import org.armman.supervisor.data.calllog.CallLogsEnvelopeDto
import org.armman.supervisor.data.calllog.CallSheetStatRowDto
import org.armman.supervisor.data.calllog.CallSheetStatsDto
import org.armman.supervisor.data.calllog.CallSheetStatsListEnvelopeDto
import org.armman.supervisor.data.calllog.CreateCallLogRequestDto
import org.armman.supervisor.data.calllog.UpdateCallLogRequestDto
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.callsheet.CallConnected
import org.armman.supervisor.ui.callsheet.CallLogSubmission
import org.armman.supervisor.ui.callsheet.CallResponder
import org.armman.supervisor.ui.callsheet.CallSheetStatKind
import org.armman.supervisor.ui.callsheet.FailureReason
import org.armman.supervisor.ui.callsheet.ReasonContext
import org.armman.supervisor.ui.callsheet.ReasonSubmission
import org.armman.supervisor.ui.callsheet.SuccessOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.util.UUID

/** [ProjectsRepositoryImplTest] covers the real implementation — this fake exists only so this
 * file's call-log-persistence tests (its actual purpose) don't depend on a real network call. */
private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> =
    listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = when (projectId) {
    "loc-1" -> listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil"))
    "loc-2" -> listOf(SakhiOption("sakhi-3", "Kavita Sharma"))
    else -> emptyList()
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = when (sakhiId) {
    "sakhi-1" -> SakhiDetail("Sushil", "Unrestricted Armman", "")
    "sakhi-2" -> SakhiDetail("Asha Patil", "Unrestricted Armman", "")
    "sakhi-3" -> SakhiDetail("Kavita Sharma", "Wardha - Zone A", "")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = when (sakhiId) {
    "sakhi-1" -> SakhiOption("sakhi-1", "Sushil")
    "sakhi-2" -> SakhiOption("sakhi-2", "Asha Patil")
    "sakhi-3" -> SakhiOption("sakhi-3", "Kavita Sharma")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override suspend fun getSakhiProjectId(sakhiId: String): String = when (sakhiId) {
    "sakhi-1", "sakhi-2" -> "loc-1"
    "sakhi-3" -> "loc-2"
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override fun clearCache() = Unit
}

/** In-memory fake standing in for supervisor-operations-service's call-logs endpoints — mirrors
 * the real backend's newest-first ordering and its single flat `callStatus` field. Supports
 * injecting non-2xx / empty-body / `success: false` responses so tests can exercise the three
 * failure branches in [CallSheetRepositoryImpl.logCall]/[CallSheetRepositoryImpl.fetchCallHistory]. */
private class FakeCallLogApi : CallLogApi {
  private val entries = mutableListOf<CallLogDto>()
  private val stats = mutableMapOf<String, CallSheetStatsDto>()
  var createCallLogResponse: Response<CallLogEnvelopeDto>? = null
  var getCallLogsResponse: Response<CallLogsEnvelopeDto>? = null
  var getCallSheetStatsBatchResponse: Response<CallSheetStatsListEnvelopeDto>? = null
  var updateCallLogResponse: Response<CallLogEnvelopeDto>? = null

  /** Injects the stats card the backend would return for [sakhiId]; a sakhiId with none seeded is
   * omitted from [getCallSheetStatsBatch]'s response, matching the backend's silent-omit behavior
   * for an unauthorized/unknown id. */
  fun seedStats(sakhiId: String, dto: CallSheetStatsDto) {
    stats[sakhiId] = dto
  }

  /** Injects a raw [CallLogDto] bypassing [createCallLog], for seeding a backend-shaped payload
   * (e.g. an unrecognized `callStatus` or a hand-written timestamp) that this app's own submission
   * path would never produce. */
  fun seed(entry: CallLogDto) {
    entries.add(0, entry)
  }

  override suspend fun createCallLog(request: CreateCallLogRequestDto): Response<CallLogEnvelopeDto> {
    createCallLogResponse?.let { return it }
    val created = CallLogDto(
      id = "call-${UUID.randomUUID()}",
      sakhiId = request.sakhiId,
      callStatus = request.callStatus,
      notes = request.notes,
      followupAction = request.followupAction,
      callStartAt = request.callStartAt,
      callEndAt = null,
      callDurationSeconds = request.callDurationSeconds,
      responder = request.responder,
    )
    entries.add(0, created)
    return Response.success(CallLogEnvelopeDto(success = true, message = "OK", data = created))
  }

  override suspend fun getCallLogsBySakhi(sakhiId: String): Response<CallLogsEnvelopeDto> {
    getCallLogsResponse?.let { return it }
    return Response.success(
      CallLogsEnvelopeDto(success = true, message = "OK", data = entries.filter { it.sakhiId == sakhiId }),
    )
  }

  override suspend fun updateCallLog(callLogId: String, request: UpdateCallLogRequestDto): Response<CallLogEnvelopeDto> {
    updateCallLogResponse?.let { return it }
    val index = entries.indexOfFirst { it.id == callLogId }
    if (index < 0) return Response.error(404, "".toResponseBody("application/json".toMediaType()))
    val updated = entries[index].copy(
      notes = request.notes ?: entries[index].notes,
      followupAction = request.followupAction ?: entries[index].followupAction,
    )
    entries[index] = updated
    return Response.success(CallLogEnvelopeDto(success = true, message = "OK", data = updated))
  }

  override suspend fun getCallSheetStatsBatch(sakhiIds: String): Response<CallSheetStatsListEnvelopeDto> {
    getCallSheetStatsBatchResponse?.let { return it }
    val requested = sakhiIds.split(",")
    return Response.success(
      CallSheetStatsListEnvelopeDto(success = true, message = "OK", data = requested.mapNotNull { stats[it] }),
    )
  }
}

private fun errorResponse(code: Int): Response<CallLogsEnvelopeDto> =
  Response.error(code, "".toResponseBody("application/json".toMediaType()))

private fun statsErrorResponse(code: Int): Response<CallSheetStatsListEnvelopeDto> =
  Response.error(code, "".toResponseBody("application/json".toMediaType()))

private fun followupPendingStats(sakhiId: String, count: Int) = CallSheetStatsDto(
  sakhiId = sakhiId,
  lastDataSyncDate = "2026-08-21",
  rows = listOf(CallSheetStatRowDto(kind = "FOLLOWUP_PENDING", updated = 0, count = count)),
)

class CallSheetRepositoryImplTest {
  private val api = FakeCallLogApi()
  private val repository = CallSheetRepositoryImpl(FakeProjectsRepository(), api)

  @Test
  fun `getSakhiSummaries returns only sakhis under the given location`() = runTest {
    val summaries = repository.getSakhiSummaries("loc-1")
    assertEquals(setOf("sakhi-1", "sakhi-2"), summaries.map { it.sakhi.id }.toSet())
  }

  @Test
  fun `getSakhiSummaries for unknown location returns empty list`() = runTest {
    assertTrue(repository.getSakhiSummaries("unknown-loc").isEmpty())
  }

  @Test
  fun `getSakhiSummaries for null location returns empty list`() = runTest {
    assertTrue(repository.getSakhiSummaries(null).isEmpty())
  }

  @Test
  fun `getSakhiSummaries reflects lastCalledAtEpochMillis from the latest logged call`() = runTest {
    val beforeCall = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-1" }
    assertNull(beforeCall.lastCalledAtEpochMillis)

    repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-1",
        connected = CallConnected.NO,
        successOutcome = null,
        failureReason = FailureReason.RINGING,
        responder = null,
        durationMinutes = null,
        notes = null,
        followUpAction = null,
      ),
    )

    val afterCall = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-1" }
    assertTrue(afterCall.lastCalledAtEpochMillis != null)
  }

  @Test
  fun `getCallHistory returns entries newest first`() = runTest {
    val first = repository.logCall(
      CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.RINGING, null, null, null, null),
    )
    val second = repository.logCall(
      CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.PHONE_OFF, null, null, null, null),
    )

    val history = repository.getCallHistory("sakhi-1")
    assertEquals(listOf(second.id, first.id), history.map { it.id })
  }

  @Test
  fun `getCallHistory for sakhi with no calls returns empty list`() = runTest {
    assertTrue(repository.getCallHistory("sakhi-2").isEmpty())
  }

  @Test
  fun `logCall persists connected call with success outcome and responder round trip`() = runTest {
    val created = repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-1",
        connected = CallConnected.YES,
        successOutcome = SuccessOutcome.PICKED_UP_TALKED,
        failureReason = null,
        responder = CallResponder.RELATIVE,
        durationMinutes = 5,
        notes = "Discussed follow-up visit",
        followUpAction = "Schedule visit next week",
        ),
      )

    val history = repository.getCallHistory("sakhi-1")
    val stored = history.single { it.id == created.id }
    assertEquals(CallConnected.YES, stored.connected)
    assertEquals(SuccessOutcome.PICKED_UP_TALKED, stored.successOutcome)
    assertEquals(CallResponder.RELATIVE, stored.responder)
    assertEquals(5, stored.durationMinutes)
    assertEquals("Discussed follow-up visit", stored.notes)
    assertEquals("Schedule visit next week", stored.followUpAction)
    assertNull(stored.failureReason)
  }

  @Test
  fun `logCall rounds duration to the nearest minute rather than truncating`() = runTest {
    val created = repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-1",
        connected = CallConnected.YES,
        successOutcome = SuccessOutcome.PICKED_UP_TALKED,
        failureReason = null,
        responder = null,
        durationMinutes = 2,
        notes = null,
        followUpAction = null,
      ),
    )

    val stored = repository.getCallHistory("sakhi-1").single { it.id == created.id }
    assertEquals(2, stored.durationMinutes)
  }

  @Test
  fun `logCall persists failed call with failure reason and no success fields`() = runTest {
    val created = repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-2",
        connected = CallConnected.NO,
        successOutcome = null,
        failureReason = FailureReason.OUT_OF_NETWORK,
        responder = null,
        durationMinutes = null,
        notes = null,
        followUpAction = null,
      ),
    )

    val stored = repository.getCallHistory("sakhi-2").single { it.id == created.id }
    assertEquals(CallConnected.NO, stored.connected)
    assertEquals(FailureReason.OUT_OF_NETWORK, stored.failureReason)
    assertNull(stored.successOutcome)
    assertNull(stored.responder)
    assertNull(stored.durationMinutes)
  }

  @Test
  fun `getSakhiOption returns the matching sakhi across locations`() = runTest {
    val sakhi = repository.getSakhiOption("sakhi-3")
    assertEquals("sakhi-3", sakhi.id)
  }

  @Test
  fun `toEntry parses a hand-written backend-shaped ISO instant, not just Instant now toString`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-backend-1",
        sakhiId = "sakhi-1",
        callStatus = "PICKED_UP_TALKED",
        notes = null,
        followupAction = null,
        callStartAt = "2026-06-15T09:30:00.000Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )

    val history = repository.getCallHistory("sakhi-1")
    val stored = history.single { it.id == "call-backend-1" }
    assertEquals(Instant.parse("2026-06-15T09:30:00.000Z").toEpochMilli(), stored.timestampEpochMillis)
  }

  @Test
  fun `toEntry maps an unrecognized callStatus to UNKNOWN instead of throwing`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-unknown-status",
        sakhiId = "sakhi-1",
        callStatus = "SWITCHED_OFF",
        notes = null,
        followupAction = null,
        callStartAt = "2026-06-15T09:30:00.000Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = "SOME_NEW_RESPONDER",
      ),
    )

    val history = repository.getCallHistory("sakhi-1")
    val stored = history.single { it.id == "call-unknown-status" }
    assertEquals(CallConnected.NO, stored.connected)
    assertEquals(FailureReason.UNKNOWN, stored.failureReason)
    assertNull(stored.successOutcome)
    assertEquals(CallResponder.UNKNOWN, stored.responder)
  }

  @Test
  fun `logCall throws on a non-2xx response`() = runTest {
    api.createCallLogResponse = Response.error(500, "".toResponseBody("application/json".toMediaType()))

    assertThrows(IllegalStateException::class.java) {
      runBlocking {
        repository.logCall(
          CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.RINGING, null, null, null, null),
        )
      }
    }
  }

  @Test
  fun `logCall throws when the response envelope reports success false`() = runTest {
    api.createCallLogResponse = Response.success(CallLogEnvelopeDto(success = false, message = "Rejected", data = null))

    assertThrows(IllegalStateException::class.java) {
      runBlocking {
        repository.logCall(
          CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.RINGING, null, null, null, null),
        )
      }
    }
  }

  @Test
  fun `logCall throws on an empty response body`() = runTest {
    api.createCallLogResponse = Response.success(null)

    assertThrows(IllegalStateException::class.java) {
      runBlocking {
        repository.logCall(
          CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.RINGING, null, null, null, null),
        )
      }
    }
  }

  @Test
  fun `getCallHistory throws on a non-2xx response`() = runTest {
    api.getCallLogsResponse = errorResponse(404)

    assertThrows(IllegalStateException::class.java) {
      runBlocking { repository.getCallHistory("sakhi-1") }
    }
  }

  @Test
  fun `getCallHistory throws when the response envelope reports success false`() = runTest {
    api.getCallLogsResponse = Response.success(CallLogsEnvelopeDto(success = false, message = "Denied", data = null))

    assertThrows(IllegalStateException::class.java) {
      runBlocking { repository.getCallHistory("sakhi-1") }
    }
  }

  @Test
  fun `getSakhiSummaries reflects real stats from the call-sheet-stats batch endpoint`() = runTest {
    api.seedStats("sakhi-1", followupPendingStats("sakhi-1", count = 1))

    val summary = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-1" }

    val followupPending = summary.stats.rows.single { it.kind == CallSheetStatKind.FOLLOWUP_PENDING }
    assertEquals(1, followupPending.count)
  }

  @Test
  fun `getSakhiSummaries falls back to empty stats for a sakhi omitted from the batch response`() = runTest {
    // sakhi-2 has no seeded stats, mirroring the backend silently omitting an unauthorized/unknown id.
    val summary = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-2" }

    assertTrue(summary.stats.rows.all { it.count == 0 && it.updated == 0 })
    assertEquals(CallSheetStatKind.entries.toSet(), summary.stats.rows.map { it.kind }.toSet())
  }

  @Test
  fun `getSakhiSummaries throws on a non-2xx call-sheet-stats response`() = runTest {
    api.getCallSheetStatsBatchResponse = statsErrorResponse(500)

    assertThrows(IllegalStateException::class.java) {
      runBlocking { repository.getSakhiSummaries("loc-1") }
    }
  }

  @Test
  fun `getSakhiSummaries throws when the call-sheet-stats envelope reports success false`() = runTest {
    api.getCallSheetStatsBatchResponse =
      Response.success(CallSheetStatsListEnvelopeDto(success = false, message = "Denied", data = null))

    assertThrows(IllegalStateException::class.java) {
      runBlocking { repository.getSakhiSummaries("loc-1") }
    }
  }

  @Test
  fun `getFollowupPending returns the sakhi's most recent call when it is CALL_BACK`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-followup-1",
        sakhiId = "sakhi-1",
        callStatus = "CALL_BACK",
        notes = "Discussed referral",
        followupAction = null,
        callStartAt = "2026-08-20T07:25:05.803Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )

    val items = repository.getFollowupPending("sakhi-1")

    assertEquals(1, items.size)
    assertEquals("call-followup-1", items.single().callLogId)
    assertEquals("Discussed referral", items.single().notes)
  }

  @Test
  fun `getFollowupPending returns empty when the most recent call is not CALL_BACK`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-not-followup",
        sakhiId = "sakhi-1",
        callStatus = "NOT_PICKED_UP",
        notes = null,
        followupAction = null,
        callStartAt = "2026-08-20T07:25:05.803Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )

    assertTrue(repository.getFollowupPending("sakhi-1").isEmpty())
  }

  @Test
  fun `getFollowupPending returns empty for a sakhi with no call history`() = runTest {
    assertTrue(repository.getFollowupPending("sakhi-2").isEmpty())
  }

  @Test
  fun `getFollowupPending excludes a CALL_BACK entry that already has a followupAction`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-already-actioned",
        sakhiId = "sakhi-1",
        callStatus = "CALL_BACK",
        notes = "Discussed referral",
        followupAction = "HOSPITALIZE",
        callStartAt = "2026-08-20T07:25:05.803Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )

    assertTrue(repository.getFollowupPending("sakhi-1").isEmpty())
  }

  @Test
  fun `submitReason for FOLLOWUP_PENDING persists followupAction and notes via PATCH call-logs`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-followup-2",
        sakhiId = "sakhi-1",
        callStatus = "CALL_BACK",
        notes = null,
        followupAction = null,
        callStartAt = "2026-08-20T07:25:05.803Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )

    repository.submitReason(
      ReasonSubmission(
        context = ReasonContext.FOLLOWUP_PENDING,
        itemId = "call-followup-2",
        sakhiId = null,
        reasonCode = "HOSPITALIZE",
        remark = "Admitted for delivery",
      ),
    )

    val updated = repository.getCallHistory("sakhi-1").single { it.id == "call-followup-2" }
    assertEquals("HOSPITALIZE", updated.followUpAction)
    assertEquals("Admitted for delivery", updated.notes)
  }

  @Test
  fun `getFollowupPending no longer returns the item after submitReason succeeds for it`() = runTest {
    api.seed(
      CallLogDto(
        id = "call-followup-3",
        sakhiId = "sakhi-1",
        callStatus = "CALL_BACK",
        notes = null,
        followupAction = null,
        callStartAt = "2026-08-20T07:25:05.803Z",
        callEndAt = null,
        callDurationSeconds = null,
        responder = null,
      ),
    )
    assertEquals(1, repository.getFollowupPending("sakhi-1").size)

    repository.submitReason(
      ReasonSubmission(context = ReasonContext.FOLLOWUP_PENDING, itemId = "call-followup-3", sakhiId = null, reasonCode = "HOSPITALIZE", remark = null),
    )

    assertTrue(repository.getFollowupPending("sakhi-1").isEmpty())
  }

  @Test
  fun `submitReason for FOLLOWUP_PENDING throws when itemId is missing`() = runTest {
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking {
        repository.submitReason(
          ReasonSubmission(context = ReasonContext.FOLLOWUP_PENDING, itemId = null, sakhiId = null, reasonCode = "HOSPITALIZE", remark = null),
        )
      }
    }
  }

  @Test
  fun `submitReason for FOLLOWUP_PENDING throws on a non-2xx PATCH response`() = runTest {
    api.updateCallLogResponse = Response.error(404, "".toResponseBody("application/json".toMediaType()))

    assertThrows(IllegalStateException::class.java) {
      runBlocking {
        repository.submitReason(
          ReasonSubmission(context = ReasonContext.FOLLOWUP_PENDING, itemId = "missing-call", sakhiId = null, reasonCode = "HOSPITALIZE", remark = null),
        )
      }
    }
  }
}
