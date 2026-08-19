package org.armman.supervisor.data.meetingtraining

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.events.CreateSupervisorEventRequest
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.SupervisorEventCacheDao
import org.armman.supervisor.data.events.SupervisorEventCacheEntity
import org.armman.supervisor.data.events.SupervisorEventDto
import org.armman.supervisor.data.events.SupervisorEventEnvelopeDto
import org.armman.supervisor.data.events.SupervisorEventSyncStatus
import org.armman.supervisor.data.events.SupervisorEventsApi
import org.armman.supervisor.data.events.SupervisorEventsEnvelopeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** Mirrors [org.armman.supervisor.data.events.PendingSupervisorEventDao]'s private retry cap. */
private const val MAX_SYNC_RETRIES = 5

private class ExecutorFakePendingDao : PendingSupervisorEventDao {
  val entities = mutableMapOf<String, PendingSupervisorEventEntity>()

  override suspend fun upsert(entity: PendingSupervisorEventEntity) {
    entities[entity.id] = entity
  }

  override suspend fun getById(id: String): PendingSupervisorEventEntity? = entities[id]

  override suspend fun deleteById(id: String) {
    entities.remove(id)
  }

  override suspend fun getPendingSync(): List<PendingSupervisorEventEntity> =
    entities.values.filter { it.syncStatus == "PENDING" || (it.syncStatus == "FAILED" && it.retryCount < MAX_SYNC_RETRIES) }
      .sortedBy { it.createdAtEpochMillis }

  override suspend fun getAll(): List<PendingSupervisorEventEntity> = entities.values.toList()
}

private class ExecutorFakeCacheDao : SupervisorEventCacheDao {
  val entities = mutableMapOf<String, SupervisorEventCacheEntity>()

  override suspend fun upsert(event: SupervisorEventCacheEntity) {
    entities[event.id] = event
  }

  override suspend fun getById(id: String): SupervisorEventCacheEntity? = entities[id]

  override suspend fun getAll(): List<SupervisorEventCacheEntity> = entities.values.toList()
}

private class ExecutorFakeApi : SupervisorEventsApi {
  var createFailure: (() -> Nothing)? = null
  var createErrorResponse: Response<SupervisorEventEnvelopeDto>? = null
  var createCallCount = 0
  var lastRequest: CreateSupervisorEventRequest? = null

  override suspend fun getEvents(): Response<SupervisorEventsEnvelopeDto> = error("not used")

  override suspend fun createEvent(request: CreateSupervisorEventRequest): Response<SupervisorEventEnvelopeDto> {
    createCallCount++
    lastRequest = request
    createFailure?.invoke()
    createErrorResponse?.let { return it }
    val event = SupervisorEventDto(
      id = "srv-event-1",
      projectId = request.projectId,
      supervisorId = "sup-1",
      eventType = request.eventType,
      eventDate = request.eventDate,
      topicsJson = request.topicsJson,
      remarks = request.remarks,
      status = request.status,
      photoMediaId = request.photoMediaId,
      createdAt = "2026-01-01T00:00:00.000Z",
      updatedAt = "2026-01-01T00:00:00.000Z",
    )
    return Response.success(SupervisorEventEnvelopeDto(success = true, message = "OK", data = event))
  }
}

private fun createRow(id: String = "event-1") = PendingSupervisorEventEntity(
  id = id,
  projectId = "loc-1",
  supervisorId = "sup-1",
  eventType = "MEETING",
  eventDate = "22 Jul 2026",
  topicsJson = "{}",
  remarks = null,
  status = "SCHEDULED",
  syncStatus = SupervisorEventSyncStatus.PENDING.name,
  createdAtEpochMillis = 1L,
  lastAttemptAtEpochMillis = null,
  retryCount = 0,
  remoteId = null,
  lastErrorMessage = null,
)

class SupervisorEventSyncExecutorTest {
  private val pendingDao = ExecutorFakePendingDao()
  private val cacheDao = ExecutorFakeCacheDao()
  private val api = ExecutorFakeApi()
  private val executor = SupervisorEventSyncExecutor(pendingDao, cacheDao, api)

  @Test
  fun `run with no pending rows returns COMPLETED without calling the API`() = runTest {
    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.COMPLETED, outcome)
    assertEquals(0, api.createCallCount)
  }

  @Test
  fun `run syncs a PENDING row to SYNCED and populates the cache`() = runTest {
    pendingDao.upsert(createRow())

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.COMPLETED, outcome)
    assertEquals("SYNCED", pendingDao.entities["event-1"]?.syncStatus)
    assertEquals("srv-event-1", pendingDao.entities["event-1"]?.remoteId)
    assertEquals("event-1", cacheDao.getById("event-1")?.id)
  }

  @Test
  fun `run marks a row FAILED and bumps retryCount on non-2xx`() = runTest {
    pendingDao.upsert(createRow())
    api.createErrorResponse = Response.error(500, okhttp3.ResponseBody.create(null, ""))

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.RETRYABLE_FAILURE, outcome)
    assertEquals("FAILED", pendingDao.entities["event-1"]?.syncStatus)
    assertEquals(1, pendingDao.entities["event-1"]?.retryCount)
  }

  @Test
  fun `run resets a row to PENDING without bumping retryCount on IOException`() = runTest {
    pendingDao.upsert(createRow())
    api.createFailure = { throw IOException("offline") }

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.RETRYABLE_FAILURE, outcome)
    assertEquals("PENDING", pendingDao.entities["event-1"]?.syncStatus)
    assertEquals(0, pendingDao.entities["event-1"]?.retryCount)
  }

  @Test
  fun `runOne short-circuits to Synced without calling the API when already SYNCED`() = runTest {
    pendingDao.upsert(createRow().copy(syncStatus = SupervisorEventSyncStatus.SYNCED.name))

    val result = executor.runOne("event-1")

    assertEquals(SupervisorEventSyncItemResult.Synced, result)
    assertEquals(0, api.createCallCount)
  }

  @Test
  fun `runOne on an unknown pending id returns Failed`() = runTest {
    val result = executor.runOne("unknown") as SupervisorEventSyncItemResult.Failed
    assertTrue(result.message?.contains("Unknown") == true)
  }

  @Test
  fun `the rich local SupervisorEventEntity is never touched by this executor`() = runTest {
    // This executor only reads/writes PendingSupervisorEventDao and SupervisorEventCacheDao —
    // it has no reference to SupervisorEventDao (attendance/marks/photos) at all, confirmed by
    // its constructor signature. Regression guard: attendance already saved against a client id
    // survives a sync exactly because this executor cannot touch that table.
    pendingDao.upsert(createRow())
    executor.run()
    assertEquals("SYNCED", pendingDao.entities["event-1"]?.syncStatus)
  }

  @Test
  fun `run stops retrying a row once it exhausts MAX_SYNC_RETRIES`() = runTest {
    pendingDao.upsert(createRow().copy(syncStatus = "FAILED", retryCount = MAX_SYNC_RETRIES))
    api.createErrorResponse = Response.error(500, okhttp3.ResponseBody.create(null, ""))

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.COMPLETED, outcome)
    assertEquals(0, api.createCallCount)
    assertEquals(MAX_SYNC_RETRIES, pendingDao.entities["event-1"]?.retryCount)
  }

  @Test
  fun `run still retries a FAILED row below the retry cap`() = runTest {
    pendingDao.upsert(createRow().copy(syncStatus = "FAILED", retryCount = MAX_SYNC_RETRIES - 1))

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.COMPLETED, outcome)
    assertEquals(1, api.createCallCount)
    assertEquals("SYNCED", pendingDao.entities["event-1"]?.syncStatus)
  }

  @Test
  fun `sends eventDate as an ISO-8601 timestamp, not the dd MMM yyyy display string`() = runTest {
    pendingDao.upsert(createRow().copy(eventDate = "22 Jul 2026"))

    executor.run()

    assertEquals("2026-07-22T00:00:00Z", api.lastRequest?.eventDate)
  }

  @Test
  fun `a row with an unparseable eventDate is marked FAILED without calling the API`() = runTest {
    pendingDao.upsert(createRow().copy(eventDate = "not-a-date"))

    val outcome = executor.run()

    assertEquals(SupervisorEventSyncOutcome.RETRYABLE_FAILURE, outcome)
    assertEquals("FAILED", pendingDao.entities["event-1"]?.syncStatus)
    assertEquals(0, api.createCallCount)
  }

  @Test
  fun `a non-2xx failure surfaces the server's own error message, not just the HTTP code`() = runTest {
    pendingDao.upsert(createRow())
    val errorJson = """{"success":false,"message":"An event already exists for this project on this date","errorCode":"CONFLICT"}"""
    api.createErrorResponse = Response.error(
      409,
      errorJson.toResponseBody("application/json".toMediaType()),
    )

    executor.run()

    assertEquals(
      "Failed to schedule event: An event already exists for this project on this date",
      pendingDao.entities["event-1"]?.lastErrorMessage,
    )
  }

  @Test
  fun `a non-2xx failure with an unparseable error body falls back to the bare HTTP code`() = runTest {
    pendingDao.upsert(createRow())
    api.createErrorResponse = Response.error(500, "not json".toResponseBody("text/plain".toMediaType()))

    executor.run()

    assertEquals("Failed to schedule event: HTTP 500", pendingDao.entities["event-1"]?.lastErrorMessage)
  }
}
