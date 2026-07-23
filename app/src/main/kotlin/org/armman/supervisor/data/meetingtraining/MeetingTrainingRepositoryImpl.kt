package org.armman.supervisor.data.meetingtraining

import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.meetingtraining.AttendanceEntry
import org.armman.supervisor.ui.meetingtraining.AttendanceRosterEntry
import org.armman.supervisor.ui.meetingtraining.EventType
import org.armman.supervisor.ui.meetingtraining.MeetingDetail
import org.armman.supervisor.ui.meetingtraining.MeetingEntry
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingRepository
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingRequest
import java.util.UUID
import javax.inject.Inject

/**
 * Concrete [MeetingTrainingRepository]. Projects and the Sakhi roster are local sample data,
 * standing in for future read-only GET endpoints (same rationale as `AssignItemRepositoryImpl`).
 * Events, attendance and photos the Supervisor actually creates are persisted in the local
 * encrypted database ([SupervisorEventDao]) so they survive process death.
 */
class MeetingTrainingRepositoryImpl @Inject constructor(
  private val eventDao: SupervisorEventDao,
) : MeetingTrainingRepository {

  private val projects = listOf(
    LocationOption("loc-1", "Unrestricted Armman"),
    LocationOption("loc-2", "Wardha - Zone A"),
  )

  private val rosterByProject = mapOf(
    "loc-1" to listOf(
      AttendanceRosterEntry("sakhi-1", "Sushil"),
      AttendanceRosterEntry("sakhi-2", "Asha Patil"),
    ),
    "loc-2" to listOf(AttendanceRosterEntry("sakhi-3", "Kavita Sharma")),
  )

  override suspend fun getProjects(): List<LocationOption> = projects

  override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> =
    rosterByProject[projectId].orEmpty()

  override suspend fun getEvents(status: EventStatus): List<MeetingEntry> =
    eventDao.getByStatus(status.name).map { it.toEntry() }

  override suspend fun getEventDetail(eventId: String): MeetingDetail {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    val rosterSize = rosterByProject[details.event.projectId]?.size ?: details.attendance.size
    return details.toDetail(rosterSize)
  }

  override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry {
    val id = "event-${UUID.randomUUID()}"
    val createdAt = System.currentTimeMillis()
    val entity = SupervisorEventEntity(
      id = id,
      projectId = request.projectId,
      projectName = request.projectName,
      eventType = EventType.MEETING.name,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      status = EventStatus.SCHEDULED.name,
      createdAt = createdAt,
    )
    eventDao.insertEvent(entity)
    return MeetingEntry(
      id = id,
      eventType = EventType.MEETING,
      projectName = request.projectName,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      createdAt = createdAt,
    )
  }

  override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) =
    eventDao.rescheduleEvent(eventId, newStartDate, newEndDate)

  override suspend fun cancelMeeting(eventId: String) = eventDao.cancelEvent(eventId)

  override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) {
    val rows = attendance.map {
      EventAttendanceEntity(
        eventId = eventId,
        sakhiId = it.sakhiId,
        sakhiName = it.sakhiName,
        attendanceStatus = if (it.present) "PRESENT" else "ABSENT",
      )
    }
    eventDao.saveAttendance(eventId, rows)
  }

  override suspend fun addPhoto(eventId: String, filePath: String) =
    eventDao.addPhoto(eventId, filePath, System.currentTimeMillis())

  override suspend fun completeMeeting(eventId: String) = eventDao.completeEvent(eventId)

  private fun SupervisorEventWithDetails.toEntry(): MeetingEntry = MeetingEntry(
    id = event.id,
    eventType = EventType.valueOf(event.eventType),
    projectName = event.projectName,
    startDate = event.startDate,
    endDate = event.endDate,
    remarks = event.remarks,
    createdAt = event.createdAt,
  )

  private fun SupervisorEventWithDetails.toDetail(rosterSize: Int): MeetingDetail = MeetingDetail(
    id = event.id,
    eventType = EventType.valueOf(event.eventType),
    projectName = event.projectName,
    startDate = event.startDate,
    endDate = event.endDate,
    remarks = event.remarks,
    status = EventStatus.valueOf(event.status),
    attendedCount = attendance.count { it.attendanceStatus == "PRESENT" },
    totalRosterCount = rosterSize,
    photoPaths = photos.map { it.filePath },
  )
}
