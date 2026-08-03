package org.armman.supervisor.data.meetingtraining

import org.armman.supervisor.data.events.CreateSupervisorEventRequest
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.SupervisorEventCacheDao
import org.armman.supervisor.data.events.SupervisorEventCacheEntity
import org.armman.supervisor.data.events.SupervisorEventSyncStatus
import org.armman.supervisor.data.events.SupervisorEventsApi
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of a full [SupervisorEventSyncExecutor.run] batch. */
enum class SupervisorEventSyncOutcome { COMPLETED, RETRYABLE_FAILURE }

/** Outcome of syncing a single pending row. */
sealed interface SupervisorEventSyncItemResult {
  data object Synced : SupervisorEventSyncItemResult
  data class Failed(val message: String?) : SupervisorEventSyncItemResult
  data class Retryable(val message: String?) : SupervisorEventSyncItemResult
}

/**
 * Drains queued supervisor-event creations to the real API. Mirrors [TransactionSyncExecutor]'s
 * control flow (itself mirroring sakhi-mobile-app's `EnrollmentSyncExecutor`): a plain injectable
 * class, JVM-testable without Robolectric. On success, the pending row's `remoteId` is populated
 * and [SupervisorEventCacheDao] is upserted — the rich local-only
 * [org.armman.supervisor.data.local.SupervisorEventEntity] (same client id, attendance/marks/
 * photos) is left completely untouched.
 */
@Singleton
class SupervisorEventSyncExecutor @Inject constructor(
  private val pendingDao: PendingSupervisorEventDao,
  private val cacheDao: SupervisorEventCacheDao,
  private val api: SupervisorEventsApi,
) {

  // Serializes every entry point below within this process: the interactive online path (runOne,
  // called directly from MeetingTrainingRepositoryImpl) and the background WorkManager job (run)
  // both read-then-mark-SYNCING the same pending rows, and SYNCING is advisory-only (no DB-level
  // lock) — without this, a periodic tick racing an interactive submit (or racing syncNow(),
  // which enqueues under a different unique-work name and so isn't deduped against the periodic
  // job by WorkManager itself) could both re-POST the same row.
  private val syncMutex = Mutex()

  suspend fun run(): SupervisorEventSyncOutcome = syncMutex.withLock {
    val pending = pendingDao.getPendingSync()
    if (pending.isEmpty()) return@withLock SupervisorEventSyncOutcome.COMPLETED

    var anyRetryableFailure = false
    for (row in pending) {
      when (syncRow(row)) {
        is SupervisorEventSyncItemResult.Synced -> Unit
        is SupervisorEventSyncItemResult.Failed, is SupervisorEventSyncItemResult.Retryable -> anyRetryableFailure = true
      }
    }
    if (anyRetryableFailure) SupervisorEventSyncOutcome.RETRYABLE_FAILURE else SupervisorEventSyncOutcome.COMPLETED
  }

  suspend fun runOne(id: String): SupervisorEventSyncItemResult = syncMutex.withLock {
    val row = pendingDao.getById(id) ?: return@withLock SupervisorEventSyncItemResult.Failed("Unknown pending event: $id")
    if (row.syncStatus == SupervisorEventSyncStatus.SYNCED.name) return@withLock SupervisorEventSyncItemResult.Synced
    syncRow(row)
  }

  private suspend fun syncRow(entity: PendingSupervisorEventEntity): SupervisorEventSyncItemResult {
    pendingDao.upsert(entity.copy(syncStatus = SupervisorEventSyncStatus.SYNCING.name))

    return try {
      val request = CreateSupervisorEventRequest(
        projectId = entity.projectId,
        supervisorId = entity.supervisorId,
        eventType = entity.eventType,
        eventDate = entity.eventDate,
        topicsJson = entity.topicsJson,
        remarks = entity.remarks,
        status = entity.status,
      )
      val response = api.createEvent(request)
      if (!response.isSuccessful) return markFailed(entity, "Failed to schedule event: HTTP ${response.code()}")
      val body = response.body()
      if (body?.success != true) return markFailed(entity, body?.message ?: "Failed to schedule event")
      val event = body.data ?: return markFailed(entity, "Empty schedule-event data")

      cacheDao.upsert(
        SupervisorEventCacheEntity(
          // Keyed by the stable client-generated local id, never the server's own id — the local
          // SupervisorEventEntity (attendance/marks/photos) uses this same id, so the two stay
          // addressable together. The server id is only recorded as entity.remoteId below.
          id = entity.id,
          projectId = event.projectId,
          supervisorId = event.supervisorId,
          eventType = event.eventType,
          eventDate = event.eventDate,
          topicsJson = event.topicsJson,
          remarks = event.remarks,
          status = event.status,
          photoMediaId = event.photoMediaId,
          createdAt = event.createdAt,
          updatedAt = event.updatedAt,
        ),
      )
      pendingDao.upsert(
        entity.copy(
          syncStatus = SupervisorEventSyncStatus.SYNCED.name,
          lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
          remoteId = event.id,
          lastErrorMessage = null,
        ),
      )
      SupervisorEventSyncItemResult.Synced
    } catch (e: IOException) {
      pendingDao.upsert(entity.copy(syncStatus = SupervisorEventSyncStatus.PENDING.name))
      SupervisorEventSyncItemResult.Retryable(e.message)
    }
  }

  private suspend fun markFailed(entity: PendingSupervisorEventEntity, message: String?): SupervisorEventSyncItemResult.Failed {
    pendingDao.upsert(
      entity.copy(
        syncStatus = SupervisorEventSyncStatus.FAILED.name,
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        retryCount = entity.retryCount + 1,
        lastErrorMessage = message,
      ),
    )
    return SupervisorEventSyncItemResult.Failed(message)
  }
}
