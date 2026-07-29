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
  val prePostMarksApplicable: Boolean = false,
)

/** One Sakhi's attendance row for a [SupervisorEventEntity]. Deleted when its parent is deleted.
 * [gatheringId] is null for Meeting attendance (one attendance per event, unchanged behavior) and
 * set for Training attendance, which is scoped per [EventGatheringEntity] instead of per event. */
@Entity(
  tableName = "event_attendance",
  foreignKeys = [
    ForeignKey(
      entity = SupervisorEventEntity::class,
      parentColumns = ["id"],
      childColumns = ["eventId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = EventGatheringEntity::class,
      parentColumns = ["id"],
      childColumns = ["gatheringId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("eventId"), Index("gatheringId")],
)
data class EventAttendanceEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val eventId: String,
  val gatheringId: String? = null,
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

/** One "Gathering Date" for a Training [SupervisorEventEntity] — created by one Add Training
 * Topics submission. A Training can have multiple gatherings over its life; each has its own
 * date, topics, attendance and marks. Deleted when its parent event is deleted. */
@Entity(
  tableName = "event_gatherings",
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
data class EventGatheringEntity(
  @PrimaryKey val id: String,
  val eventId: String,
  val date: String,
  val createdAt: Long,
)

/** One Training topic added within an [EventGatheringEntity]. Deleted when its parent gathering
 * is deleted. */
@Entity(
  tableName = "event_topics",
  foreignKeys = [
    ForeignKey(
      entity = EventGatheringEntity::class,
      parentColumns = ["id"],
      childColumns = ["gatheringId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("gatheringId")],
)
data class EventTopicEntity(
  @PrimaryKey val id: String,
  val gatheringId: String,
  val topicName: String,
  val addedAt: Long,
)

/** One Sakhi's Pre/Post mark for one Training topic within a gathering. Deleted when its parent
 * topic is deleted. */
@Entity(
  tableName = "event_marks",
  foreignKeys = [
    ForeignKey(
      entity = EventTopicEntity::class,
      parentColumns = ["id"],
      childColumns = ["topicId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("topicId")],
)
data class EventMarksEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val topicId: String,
  val marksType: String,
  val sakhiId: String,
  val sakhiName: String,
  val marks: Int,
)

/** Presence of a row means Pre/Post marks for that topic are completed and locked — no further
 * edits are allowed for that (topic, marksType) pair. Deleted when its parent topic is deleted. */
@Entity(
  tableName = "event_marks_completion",
  primaryKeys = ["topicId", "marksType"],
  foreignKeys = [
    ForeignKey(
      entity = EventTopicEntity::class,
      parentColumns = ["id"],
      childColumns = ["topicId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("topicId")],
)
data class EventMarksCompletionEntity(
  val topicId: String,
  val marksType: String,
  val completedAt: Long,
)

/** Which pass of marks a [EventMarksEntity]/[EventMarksCompletionEntity] row belongs to. */
enum class MarksType { PRE, POST }
