package org.armman.supervisor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

/** A [SupervisorEventEntity] with its attendance and photo rows, as one query result. */
data class SupervisorEventWithDetails(
  @Embedded val event: SupervisorEventEntity,
  @Relation(parentColumn = "id", entityColumn = "eventId")
  val attendance: List<EventAttendanceEntity>,
  @Relation(parentColumn = "id", entityColumn = "eventId")
  val photos: List<EventPhotoEntity>,
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

  @Query("DELETE FROM event_attendance WHERE eventId = :eventId")
  suspend fun deleteAttendanceForEvent(eventId: String)

  @Insert
  suspend fun insertAttendance(rows: List<EventAttendanceEntity>)

  @Insert
  suspend fun insertPhoto(photo: EventPhotoEntity)

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

  @Transaction
  suspend fun saveAttendance(eventId: String, rows: List<EventAttendanceEntity>) {
    requireScheduled(eventId)
    deleteAttendanceForEvent(eventId)
    if (rows.isNotEmpty()) insertAttendance(rows)
  }

  @Transaction
  suspend fun addPhoto(eventId: String, filePath: String, capturedAt: Long) {
    requireScheduled(eventId)
    insertPhoto(EventPhotoEntity(eventId = eventId, filePath = filePath, capturedAt = capturedAt))
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
