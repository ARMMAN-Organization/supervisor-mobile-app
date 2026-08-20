package org.armman.supervisor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

/** A [SupervisorEventEntity] with its attendance, photo and gathering rows, as one query result. */
data class SupervisorEventWithDetails(
  @Embedded val event: SupervisorEventEntity,
  @Relation(parentColumn = "id", entityColumn = "eventId")
  val attendance: List<EventAttendanceEntity>,
  @Relation(parentColumn = "id", entityColumn = "eventId")
  val photos: List<EventPhotoEntity>,
  @Relation(parentColumn = "id", entityColumn = "eventId")
  val gatherings: List<EventGatheringEntity>,
)

/** An [EventGatheringEntity] with its topics, as one query result. */
data class GatheringWithTopics(
  @Embedded val gathering: EventGatheringEntity,
  @Relation(parentColumn = "id", entityColumn = "gatheringId")
  val topics: List<EventTopicEntity>,
)

/** Projection for [SupervisorEventDao.getTopicAndGatheringRemoteIds] — the local and (if resolved)
 * real server ids for a topic and its parent gathering, needed together to send a valid marks
 * save/complete request. */
data class TopicAndGatheringRemoteIds(
  val topicId: String,
  val topicRemoteId: String?,
  val gatheringId: String,
  val gatheringRemoteId: String?,
)

@Dao
interface SupervisorEventDao {
  @Transaction
  @Query("SELECT * FROM supervisor_events WHERE status = :status ORDER BY createdAt DESC")
  suspend fun getByStatus(status: String): List<SupervisorEventWithDetails>

  @Transaction
  @Query("SELECT * FROM supervisor_events WHERE id = :eventId LIMIT 1")
  suspend fun getById(eventId: String): SupervisorEventWithDetails?

  @Insert
  suspend fun insertEvent(entity: SupervisorEventEntity)

  @Update
  suspend fun updateEvent(entity: SupervisorEventEntity)

  @Query("DELETE FROM event_attendance WHERE eventId = :eventId AND gatheringId IS NULL")
  suspend fun deleteMeetingAttendanceForEvent(eventId: String)

  @Query("DELETE FROM event_attendance WHERE gatheringId = :gatheringId")
  suspend fun deleteAttendanceForGathering(gatheringId: String)

  @Insert
  suspend fun insertAttendance(rows: List<EventAttendanceEntity>)

  @Insert
  suspend fun insertPhoto(photo: EventPhotoEntity)

  /** Marks a photo as uploaded once `POST /media` has registered it — so a retried complete
   * reuses this id instead of re-uploading the same file. */
  @Query("UPDATE event_photos SET remoteMediaId = :remoteMediaId WHERE rowId = :rowId")
  suspend fun setPhotoRemoteMediaId(rowId: Long, remoteMediaId: String)

  @Insert
  suspend fun insertGathering(gathering: EventGatheringEntity)

  @Insert
  suspend fun insertTopics(topics: List<EventTopicEntity>)

  /** Marks a gathering as created server-side once its `PendingGatheringEntity` row syncs — so
   * `saveMarks`/`completeMarks` can send this real id instead of the local placeholder. */
  @Query("UPDATE event_gatherings SET remoteId = :remoteId WHERE id = :gatheringId")
  suspend fun setGatheringRemoteId(gatheringId: String, remoteId: String)

  /** Same as [setGatheringRemoteId], for the one topic within that gathering matching [topicName]
   * — topics are resolved to their real training-topic catalog UUID by name, same lookup already
   * used when creating the gathering online. */
  @Query("UPDATE event_topics SET remoteId = :remoteId WHERE gatheringId = :gatheringId AND topicName = :topicName")
  suspend fun setTopicRemoteId(gatheringId: String, topicName: String, remoteId: String)

  @Transaction
  @Query("SELECT * FROM event_gatherings WHERE id = :gatheringId LIMIT 1")
  suspend fun getGatheringWithTopics(gatheringId: String): GatheringWithTopics?

  @Query("SELECT * FROM event_attendance WHERE gatheringId = :gatheringId")
  suspend fun getAttendanceForGathering(gatheringId: String): List<EventAttendanceEntity>

  @Query(
    """
    SELECT et.id FROM event_topics et
    INNER JOIN event_gatherings eg ON eg.id = et.gatheringId
    WHERE eg.id = :gatheringId
    """,
  )
  suspend fun getTopicIdsForGathering(gatheringId: String): List<String>

  /** Reverse lookup for the marks fan-out to the real backend, which needs a `gatheringId` per
   * mark save/complete call — [org.armman.supervisor.data.local.EventTopicEntity] already ties
   * one topic to exactly one gathering, so this is a direct column read, not a new relationship. */
  @Query("SELECT gatheringId FROM event_topics WHERE id = :topicId LIMIT 1")
  suspend fun getGatheringIdForTopic(topicId: String): String?

  /** The topic and its parent gathering, together — [saveMarks]/[completeMarks] need both rows'
   * `remoteId` before attempting a remote call, since the backend requires a real topic UUID
   * ([EventTopicEntity.remoteId]) and a real gathering UUID ([EventGatheringEntity.remoteId]) in
   * the same request; sending either as a local placeholder id is rejected as an invalid UUID. */
  @Query(
    """
    SELECT et.id AS topicId, et.remoteId AS topicRemoteId,
           eg.id AS gatheringId, eg.remoteId AS gatheringRemoteId
    FROM event_topics et
    INNER JOIN event_gatherings eg ON eg.id = et.gatheringId
    WHERE et.id = :topicId
    LIMIT 1
    """,
  )
  suspend fun getTopicAndGatheringRemoteIds(topicId: String): TopicAndGatheringRemoteIds?

  @Insert
  suspend fun insertMarks(rows: List<EventMarksEntity>)

  @Query("DELETE FROM event_marks WHERE topicId = :topicId AND marksType = :marksType")
  suspend fun deleteMarksForTopic(topicId: String, marksType: String)

  @Query("SELECT * FROM event_marks WHERE topicId = :topicId AND marksType = :marksType")
  suspend fun getMarksForTopic(topicId: String, marksType: String): List<EventMarksEntity>

  @Query("SELECT * FROM event_marks_completion WHERE topicId = :topicId")
  suspend fun getMarksCompletionForTopic(topicId: String): List<EventMarksCompletionEntity>

  @Query("SELECT * FROM event_marks_completion WHERE topicId IN (:topicIds)")
  suspend fun getMarksCompletionForTopics(topicIds: List<String>): List<EventMarksCompletionEntity>

  @Insert
  suspend fun insertMarksCompletion(completion: EventMarksCompletionEntity)

  /** Every photo file path still referenced by an event — the set of files
   * `EventPhotoCleanup.deleteUnreferenced` must never delete, since events/photo rows are never
   * deleted (soft-cancel only). */
  @Query("SELECT filePath FROM event_photos")
  suspend fun getAllPhotoFilePaths(): List<String>

  @Delete
  suspend fun deleteEvent(entity: SupervisorEventEntity)

  /** Public so callers (e.g. [org.armman.supervisor.data.meetingtraining.MeetingTrainingRepositoryImpl])
   * can pre-validate a state transition before attempting a remote call, without performing the
   * local write yet — used by remote-gated ops (cancel/complete/reschedule) that must not commit
   * locally ahead of server confirmation. */
  suspend fun requireScheduled(eventId: String): SupervisorEventEntity {
    val event = getById(eventId)?.event ?: error("Unknown event id: $eventId")
    check(event.status == EventStatus.SCHEDULED.name) { "Event $eventId is not SCHEDULED (status=${event.status})" }
    return event
  }

  @Transaction
  suspend fun rescheduleEvent(eventId: String, newStartDate: String, newEndDate: String) {
    val event = requireScheduled(eventId)
    updateEvent(event.copy(startDate = newStartDate, endDate = newEndDate))
  }

  @Transaction
  suspend fun cancelEvent(eventId: String) {
    val event = requireScheduled(eventId)
    updateEvent(event.copy(status = EventStatus.CANCELLED.name))
  }

  /** Meeting attendance: one row per event, [EventAttendanceEntity.gatheringId] always null. */
  @Transaction
  suspend fun saveAttendance(eventId: String, rows: List<EventAttendanceEntity>) {
    requireScheduled(eventId)
    deleteMeetingAttendanceForEvent(eventId)
    if (rows.isNotEmpty()) insertAttendance(rows)
  }

  /** Training attendance: one row set per gathering. */
  @Transaction
  suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, rows: List<EventAttendanceEntity>) {
    requireScheduled(eventId)
    checkNotNull(getGatheringWithTopics(gatheringId)) { "Unknown gathering id: $gatheringId" }
    deleteAttendanceForGathering(gatheringId)
    if (rows.isNotEmpty()) insertAttendance(rows)
  }

  @Transaction
  suspend fun addPhoto(eventId: String, filePath: String, capturedAt: Long) {
    requireScheduled(eventId)
    insertPhoto(EventPhotoEntity(eventId = eventId, filePath = filePath, capturedAt = capturedAt))
  }

  @Transaction
  suspend fun addGathering(
    eventId: String,
    gatheringId: String,
    topicIds: List<String>,
    topicNames: List<String>,
    date: String,
    addedAt: Long,
  ) {
    requireScheduled(eventId)
    insertGathering(EventGatheringEntity(id = gatheringId, eventId = eventId, date = date, createdAt = addedAt))
    if (topicNames.isEmpty()) return
    insertTopics(
      topicIds.zip(topicNames) { topicId, topicName ->
        EventTopicEntity(id = topicId, gatheringId = gatheringId, topicName = topicName, addedAt = addedAt)
      },
    )
  }

  private suspend fun requireGatheringTopicNotCompleted(topicId: String, marksType: String) {
    val completed = getMarksCompletionForTopic(topicId).any { it.marksType == marksType }
    check(!completed) { "Marks for topic $topicId ($marksType) are already completed and locked" }
  }

  @Transaction
  suspend fun saveMarks(eventId: String, topicId: String, marksType: String, rows: List<EventMarksEntity>) {
    requireScheduled(eventId)
    requireGatheringTopicNotCompleted(topicId, marksType)
    check(rows.all { it.marks in MARKS_RANGE }) { "Marks for topic $topicId must be in $MARKS_RANGE" }
    deleteMarksForTopic(topicId, marksType)
    if (rows.isNotEmpty()) insertMarks(rows)
  }

  @Transaction
  suspend fun completeMarks(eventId: String, topicId: String, marksType: String, completedAt: Long) {
    requireScheduled(eventId)
    requireGatheringTopicNotCompleted(topicId, marksType)
    insertMarksCompletion(EventMarksCompletionEntity(topicId = topicId, marksType = marksType, completedAt = completedAt))
  }

  /** Same pre-check [completeEvent] performs before its write — public for the same reason as
   * [requireScheduled]: remote-gated completion needs to validate locally before attempting the
   * remote call, without writing yet. */
  suspend fun requireScheduledWithPhotoForComplete(eventId: String): SupervisorEventEntity {
    val details = getById(eventId) ?: error("Unknown event id: $eventId")
    check(details.event.status == EventStatus.SCHEDULED.name) {
      "Event $eventId is not SCHEDULED (status=${details.event.status})"
    }
    check(details.photos.isNotEmpty()) { "Cannot complete event $eventId without at least one photo" }
    return details.event
  }

  @Transaction
  suspend fun completeEvent(eventId: String) {
    val event = requireScheduledWithPhotoForComplete(eventId)
    updateEvent(event.copy(status = EventStatus.COMPLETED.name))
  }

  companion object {
    val MARKS_RANGE = 0..100
  }
}

/** Lifecycle status of a [SupervisorEventEntity] — mirrors the ERD's `supervisor_events.status` enum. */
enum class EventStatus { SCHEDULED, COMPLETED, CANCELLED }
