package org.armman.supervisor.ui.meetingtraining

import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.model.LocationOption

/** Data access for the Meeting & Training feature. See `MeetingTrainingRepositoryImpl` for the
 * local-first implementation (reference data hardcoded in-memory; events/attendance/photos in Room). */
interface MeetingTrainingRepository {
  suspend fun getProjects(): List<LocationOption>
  suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry>
  suspend fun getEvents(status: EventStatus): List<MeetingEntry>
  suspend fun getEventDetail(eventId: String): MeetingDetail
  suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry
  suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String)
  suspend fun cancelMeeting(eventId: String)
  suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>)
  suspend fun addPhoto(eventId: String, filePath: String)
  suspend fun completeMeeting(eventId: String)
}
