package org.armman.supervisor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local persisted row for one Meeting/Training event — stands in for the future
 * `supervisor_events` backend table. Uses a start/end date range (unlike the ERD's single
 * `event_date`) to support the Reschedule flow, which moves both ends of the range.
 */
@Entity(tableName = "supervisor_events")
data class SupervisorEventEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val projectName: String,
  val eventType: String,
  val startDate: String,
  val endDate: String,
  val remarks: String,
  val status: String,
  val createdAt: Long,
)

/** One Sakhi's attendance row for a [SupervisorEventEntity]. Deleted when its parent is deleted. */
@Entity(
  tableName = "event_attendance",
  foreignKeys = [
    ForeignKey(
      entity = SupervisorEventEntity::class,
      parentColumns = ["id"],
      childColumns = ["eventId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("eventId")],
)
data class EventAttendanceEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val eventId: String,
  val sakhiId: String,
  val sakhiName: String,
  val attendanceStatus: String,
)

/** One captured photo belonging to a [SupervisorEventEntity]. Deleted when its parent is deleted. */
@Entity(
  tableName = "event_photos",
  foreignKeys = [
    ForeignKey(
      entity = SupervisorEventEntity::class,
      parentColumns = ["id"],
      childColumns = ["eventId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("eventId")],
)
data class EventPhotoEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val eventId: String,
  val filePath: String,
  val capturedAt: Long,
)
