package org.armman.supervisor.ui.meetingtraining

import org.armman.supervisor.data.local.EventStatus

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
)

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
