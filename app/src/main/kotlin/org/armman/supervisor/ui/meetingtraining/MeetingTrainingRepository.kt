package org.armman.supervisor.ui.meetingtraining

import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.meetingtraining.EventScheduleResult
import org.armman.supervisor.model.LocationOption

/** Data access for the Meeting & Training feature. See `MeetingTrainingRepositoryImpl` — projects/
 * roster are real; scheduling is offline-first (queued and synced now or in the background);
 * attendance/marks/photos have no backend endpoint and stay 100% local. */
interface MeetingTrainingRepository {
  suspend fun getProjects(): List<LocationOption>
  suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry>
  suspend fun getEvents(status: EventStatus): List<MeetingEntry>
  suspend fun getEventDetail(eventId: String): MeetingDetail
  suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult
  suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String)
  suspend fun cancelMeeting(eventId: String)

  /** Meeting attendance: one roster per event. */
  suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>)

  /** Per-Sakhi attendance previously saved for this event via [saveAttendance], empty if none yet. */
  suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry>

  suspend fun addPhoto(eventId: String, filePath: String)
  suspend fun completeMeeting(eventId: String)

  suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult

  /** Predefined catalog a supervisor picks Training topics from (local sample data for now). */
  suspend fun getTrainingTopicsCatalog(): List<TrainingTopic>

  /** Creates one Gathering Date for a Training from an Add Training Topics submission. Returns
   * the new gathering's id. A Training can accumulate multiple gatherings over its life. */
  suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String

  /** Training attendance: one roster per gathering, not per event. */
  suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>)

  suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry>

  /** Topics belonging to one gathering, for the Marks screen's topic picker. */
  suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic>

  suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry>

  /** Fails if this (topic, marksType) is already completed and locked. */
  suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>)

  /** Locks this (topic, marksType) against further edits. Fails if already completed. */
  suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType)

  /** Every event photo file path currently referenced by a Room row — the set
   * [EventPhotoCleanup.deleteUnreferenced] must preserve. */
  suspend fun getAllPhotoFilePaths(): List<String>
}
