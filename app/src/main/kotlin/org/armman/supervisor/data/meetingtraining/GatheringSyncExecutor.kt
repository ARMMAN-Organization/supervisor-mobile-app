package org.armman.supervisor.data.meetingtraining

import com.google.gson.Gson
import org.armman.supervisor.data.auth.ErrorResponseDto
import org.armman.supervisor.data.events.AddGatheringRequest
import org.armman.supervisor.data.events.PendingGatheringDao
import org.armman.supervisor.data.events.PendingGatheringEntity
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.SupervisorEventOperationsApi
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.masterdata.ItemMasterAndTrainingApi
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TRAINING_TOPIC_ACTIVE_STATUS = "ACTIVE"

/** Outcome of a full [GatheringSyncExecutor.run] batch. */
enum class GatheringSyncOutcome { COMPLETED, RETRYABLE_FAILURE }

/** Outcome of syncing a single pending gathering. */
sealed interface GatheringSyncItemResult {
  data object Synced : GatheringSyncItemResult
  data object AwaitingParentEvent : GatheringSyncItemResult
  data class Failed(val message: String?) : GatheringSyncItemResult
  data class Retryable(val message: String?) : GatheringSyncItemResult
}

/**
 * Drains queued Training gathering creations to the real API — the gathering-level counterpart to
 * [SupervisorEventSyncExecutor]. A gathering can only sync once its parent event has (its
 * `remoteId` is required to call `POST /supervisor-events/{id}/gatherings`); rows whose parent
 * hasn't synced yet are left untouched ([GatheringSyncItemResult.AwaitingParentEvent]) rather than
 * treated as a failure, since they'll become syncable on a later pass once the event itself syncs.
 *
 * On success, both the local [org.armman.supervisor.data.local.EventGatheringEntity.remoteId] and
 * each of its [org.armman.supervisor.data.local.EventTopicEntity.remoteId] are populated —
 * matching topic names to the server's response, the same resolution
 * [MeetingTrainingRepositoryImpl.addGathering] already does for its own online-create path.
 */
@Singleton
class GatheringSyncExecutor @Inject constructor(
  private val pendingDao: PendingGatheringDao,
  private val pendingEventDao: PendingSupervisorEventDao,
  private val eventDao: SupervisorEventDao,
  private val operationsApi: SupervisorEventOperationsApi,
  private val itemMasterAndTrainingApi: ItemMasterAndTrainingApi,
) {
  private val gson = Gson()
  private val syncMutex = Mutex()

  suspend fun run(): GatheringSyncOutcome = syncMutex.withLock {
    val pending = pendingDao.getPendingSync()
    if (pending.isEmpty()) return@withLock GatheringSyncOutcome.COMPLETED

    var anyRetryableFailure = false
    for (row in pending) {
      when (syncRow(row)) {
        is GatheringSyncItemResult.Synced, is GatheringSyncItemResult.AwaitingParentEvent -> Unit
        is GatheringSyncItemResult.Failed, is GatheringSyncItemResult.Retryable -> anyRetryableFailure = true
      }
    }
    if (anyRetryableFailure) GatheringSyncOutcome.RETRYABLE_FAILURE else GatheringSyncOutcome.COMPLETED
  }

  suspend fun runOne(id: String): GatheringSyncItemResult = syncMutex.withLock {
    val row = pendingDao.getById(id) ?: return@withLock GatheringSyncItemResult.Failed("Unknown pending gathering: $id")
    syncRow(row)
  }

  private suspend fun syncRow(entity: PendingGatheringEntity): GatheringSyncItemResult {
    val remoteEventId = pendingEventDao.getById(entity.eventId)?.remoteId
      ?: return GatheringSyncItemResult.AwaitingParentEvent

    return try {
      val topicNames = entity.topicNamesJoined.split(GATHERING_TOPIC_NAME_DELIMITER).filter { it.isNotEmpty() }
      val catalog = loadActiveTrainingTopics()
        ?: return markFailed(entity, "Failed to load training topics")
      val realTopicIds = topicNames.map { name ->
        catalog.firstOrNull { it.topicName == name }?.id
          ?: return markFailed(entity, "Unknown training topic: $name")
      }

      val request = AddGatheringRequest(
        gatheringDate = entity.gatheringDate,
        topicIds = realTopicIds,
        remarks = entity.remarks,
      )
      val response = operationsApi.addGathering(remoteEventId, request)
      if (!response.isSuccessful) return markFailed(entity, serverErrorMessage(response, "add gathering"))
      val body = response.body()
      if (body?.success != true) return markFailed(entity, body?.message ?: "Failed to add gathering")
      val gathering = body.data ?: return markFailed(entity, "Empty add-gathering response")

      eventDao.setGatheringRemoteId(entity.id, gathering.id)
      for (name in topicNames) {
        val realId = catalog.firstOrNull { it.topicName == name }?.id ?: continue
        eventDao.setTopicRemoteId(entity.id, name, realId)
      }
      pendingDao.deleteById(entity.id)
      GatheringSyncItemResult.Synced
    } catch (e: IOException) {
      GatheringSyncItemResult.Retryable(e.message)
    }
  }

  private suspend fun loadActiveTrainingTopics() = try {
    val response = itemMasterAndTrainingApi.getTrainingTopics()
    if (!response.isSuccessful) null else response.body()?.data.orEmpty().filter { it.status == TRAINING_TOPIC_ACTIVE_STATUS }
  } catch (e: IOException) {
    null
  }

  private fun serverErrorMessage(response: Response<*>, action: String): String {
    val errorJson = response.errorBody()?.string()
    val parsed = errorJson?.let { runCatching { gson.fromJson(it, ErrorResponseDto::class.java) }.getOrNull() }
    val detail = parsed?.message ?: parsed?.errorCode
    return if (detail != null) "Failed to $action: $detail" else "Failed to $action: HTTP ${response.code()}"
  }

  private suspend fun markFailed(entity: PendingGatheringEntity, message: String?): GatheringSyncItemResult.Failed {
    pendingDao.upsert(
      entity.copy(
        syncStatus = "FAILED",
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        retryCount = entity.retryCount + 1,
        lastErrorMessage = message,
      ),
    )
    return GatheringSyncItemResult.Failed(message)
  }
}
