package org.armman.supervisor.ui.meetingtraining

import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType

/** One Meeting or Training event type, selectable via the gathering-type filter chips. */
enum class EventType { MEETING, TRAINING }

/** One Sakhi selectable on the Attendance roster for an event's project. */
data class AttendanceRosterEntry(val sakhiId: String, val sakhiName: String)

/** One event card shown on the Meeting & Training list screen. */
data class MeetingEntry(
  val id: String,
  val eventType: EventType,
  val projectName: String,
  val startDate: String,
  val endDate: String,
  val remarks: String,
  val createdAt: Long,
)

/** Full detail for the Meeting Detail screen. */
data class MeetingDetail(
  val id: String,
  val eventType: EventType,
  val projectName: String,
  val startDate: String,
  val endDate: String,
  val remarks: String,
  val status: EventStatus,
  val attendedCount: Int,
  val totalRosterCount: Int,
  val photoPaths: List<String>,
  val prePostMarksApplicable: Boolean = false,
  val gatherings: List<GatheringSummary> = emptyList(),
)

/** One Training topic within a [GatheringSummary], with its Pre/Post marks lock status. */
data class GatheringTopicStatus(
  val topicId: String,
  val topicName: String,
  val preMarksCompleted: Boolean,
  val postMarksCompleted: Boolean,
)

/** One "Gathering Date" shown on the Training Detail screen — one Add Training Topics
 * submission, with its own Attendance/Pre Marks/Post Marks status. */
data class GatheringSummary(
  val gatheringId: String,
  val date: String,
  val topics: List<GatheringTopicStatus>,
  val attendedCount: Int,
  val totalRosterCount: Int,
)

/** One Sakhi's Pre/Post mark entry on the Marks screen. */
data class MarksEntry(val sakhiId: String, val sakhiName: String, val marks: Int?)

/** One Sakhi's attendance selection, submitted from the Attendance screen. */
data class AttendanceEntry(val sakhiId: String, val sakhiName: String, val present: Boolean)

/** Payload to schedule a new Meeting. */
data class ScheduleMeetingRequest(
  val projectId: String,
  val projectName: String,
  val startDate: String,
  val endDate: String,
  val remarks: String,
)

/** Payload to schedule a new Training. */
data class ScheduleTrainingRequest(
  val projectId: String,
  val projectName: String,
  val startDate: String,
  val endDate: String,
  val prePostMarksApplicable: Boolean,
  val remarks: String,
)

/** One selectable topic from the Training topics catalog. */
data class TrainingTopic(val id: String, val name: String)
