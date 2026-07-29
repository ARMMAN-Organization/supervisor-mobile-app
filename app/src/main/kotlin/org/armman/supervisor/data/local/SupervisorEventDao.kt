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

  @Insert
  suspend fun insertGathering(gathering: EventGatheringEntity)

  @Insert
  suspend fun insertTopics(topics: List<EventTopicEntity>)

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

  private suspend fun requireScheduled(eventId: String): SupervisorEventEntity {
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
    deleteMarksForTopic(topicId, marksType)
    if (rows.isNotEmpty()) insertMarks(rows)
  }

  @Transaction
  suspend fun completeMarks(eventId: String, topicId: String, marksType: String, completedAt: Long) {
    requireScheduled(eventId)
    requireGatheringTopicNotCompleted(topicId, marksType)
    insertMarksCompletion(EventMarksCompletionEntity(topicId = topicId, marksType = marksType, completedAt = completedAt))
  }

  @Transaction
  suspend fun completeEvent(eventId: String) {
    val details = getById(eventId) ?: error("Unknown event id: $eventId")
    check(details.event.status == EventStatus.SCHEDULED.name) {
      "Event $eventId is not SCHEDULED (status=${details.event.status})"
    }
    check(details.photos.isNotEmpty()) { "Cannot complete event $eventId without at least one photo" }
    updateEvent(details.event.copy(status = EventStatus.COMPLETED.name))
  }
}

/** Lifecycle status of a [SupervisorEventEntity] — mirrors the ERD's `supervisor_events.status` enum. */
enum class EventStatus { SCHEDULED, COMPLETED, CANCELLED }
