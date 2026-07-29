package org.armman.supervisor.data.meetingtraining

import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventGatheringEntity
import org.armman.supervisor.data.local.EventMarksEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.meetingtraining.AttendanceEntry
import org.armman.supervisor.ui.meetingtraining.AttendanceRosterEntry
import org.armman.supervisor.ui.meetingtraining.EventType
import org.armman.supervisor.ui.meetingtraining.GatheringSummary
import org.armman.supervisor.ui.meetingtraining.GatheringTopicStatus
import org.armman.supervisor.ui.meetingtraining.MarksEntry
import org.armman.supervisor.ui.meetingtraining.MeetingDetail
import org.armman.supervisor.ui.meetingtraining.MeetingEntry
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingRepository
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingRequest
import org.armman.supervisor.ui.meetingtraining.ScheduleTrainingRequest
import org.armman.supervisor.ui.meetingtraining.TrainingTopic
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

  private val trainingTopicsCatalog = listOf(
    TrainingTopic("topic-1", "Antenatal Care Basics"),
    TrainingTopic("topic-2", "Nutrition Counselling"),
    TrainingTopic("topic-3", "Danger Signs in Pregnancy"),
    TrainingTopic("topic-4", "Newborn Care"),
    TrainingTopic("topic-5", "Family Planning"),
    TrainingTopic("topic-6", "Data Collection & App Usage"),
  )

  override suspend fun getProjects(): List<LocationOption> = projects

  override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> =
    rosterByProject[projectId].orEmpty()

  override suspend fun getEvents(status: EventStatus): List<MeetingEntry> =
    eventDao.getByStatus(status.name).map { it.toEntry() }

  override suspend fun getEventDetail(eventId: String): MeetingDetail {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    val rosterSize = rosterByProject[details.event.projectId]?.size ?: details.attendance.size
    val gatherings = details.gatherings.map { it.toSummary(rosterSize) }
    return details.toDetail(rosterSize, gatherings)
  }

  private suspend fun EventGatheringEntity.toSummary(rosterSize: Int): GatheringSummary {
    val withTopics = eventDao.getGatheringWithTopics(id) ?: return GatheringSummary(id, date, emptyList(), 0, rosterSize)
    val attendance = eventDao.getAttendanceForGathering(id)
    val topicIds = withTopics.topics.map { it.id }
    val completion = if (topicIds.isEmpty()) emptyList() else eventDao.getMarksCompletionForTopics(topicIds)
    val topicStatuses = withTopics.topics.map { topic ->
      GatheringTopicStatus(
        topicId = topic.id,
        topicName = topic.topicName,
        preMarksCompleted = completion.any { it.topicId == topic.id && it.marksType == MarksType.PRE.name },
        postMarksCompleted = completion.any { it.topicId == topic.id && it.marksType == MarksType.POST.name },
      )
    }
    return GatheringSummary(
      gatheringId = id,
      date = date,
      topics = topicStatuses,
      attendedCount = attendance.count { it.attendanceStatus == "PRESENT" },
      totalRosterCount = rosterSize,
    )
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

  override suspend fun scheduleTraining(request: ScheduleTrainingRequest): MeetingEntry {
    val id = "event-${UUID.randomUUID()}"
    val createdAt = System.currentTimeMillis()
    val entity = SupervisorEventEntity(
      id = id,
      projectId = request.projectId,
      projectName = request.projectName,
      eventType = EventType.TRAINING.name,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      status = EventStatus.SCHEDULED.name,
      createdAt = createdAt,
      prePostMarksApplicable = request.prePostMarksApplicable,
    )
    eventDao.insertEvent(entity)
    return MeetingEntry(
      id = id,
      eventType = EventType.TRAINING,
      projectName = request.projectName,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      createdAt = createdAt,
    )
  }

  override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = trainingTopicsCatalog

  override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String {
    val gatheringId = "gathering-${UUID.randomUUID()}"
    val topicIds = topicNames.map { "topic-${UUID.randomUUID()}" }
    eventDao.addGathering(eventId, gatheringId, topicIds, topicNames, date, System.currentTimeMillis())
    return gatheringId
  }

  override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) {
    val rows = attendance.map {
      EventAttendanceEntity(
        eventId = eventId,
        gatheringId = gatheringId,
        sakhiId = it.sakhiId,
        sakhiName = it.sakhiName,
        attendanceStatus = if (it.present) "PRESENT" else "ABSENT",
      )
    }
    eventDao.saveGatheringAttendance(eventId, gatheringId, rows)
  }

  override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> =
    eventDao.getAttendanceForGathering(gatheringId).map { AttendanceEntry(it.sakhiId, it.sakhiName, it.attendanceStatus == "PRESENT") }

  override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> =
    eventDao.getGatheringWithTopics(gatheringId)?.topics?.map { TrainingTopic(it.id, it.topicName) }.orEmpty()

  override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> =
    eventDao.getMarksForTopic(topicId, marksType.name).map { MarksEntry(it.sakhiId, it.sakhiName, it.marks) }

  override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) {
    val rows = entries.mapNotNull { entry ->
      entry.marks?.let { EventMarksEntity(topicId = topicId, marksType = marksType.name, sakhiId = entry.sakhiId, sakhiName = entry.sakhiName, marks = it) }
    }
    eventDao.saveMarks(eventId, topicId, marksType.name, rows)
  }

  override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) =
    eventDao.completeMarks(eventId, topicId, marksType.name, System.currentTimeMillis())

  override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) =
    eventDao.rescheduleEvent(eventId, newStartDate, newEndDate)

  override suspend fun cancelMeeting(eventId: String) = eventDao.cancelEvent(eventId)

  override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) {
    val rows = attendance.map {
      EventAttendanceEntity(
        eventId = eventId,
        gatheringId = null,
        sakhiId = it.sakhiId,
        sakhiName = it.sakhiName,
        attendanceStatus = if (it.present) "PRESENT" else "ABSENT",
      )
    }
    eventDao.saveAttendance(eventId, rows)
  }

  override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    return details.attendance
      .filter { it.gatheringId == null }
      .map { AttendanceEntry(it.sakhiId, it.sakhiName, present = it.attendanceStatus == "PRESENT") }
  }

  override suspend fun addPhoto(eventId: String, filePath: String) =
    eventDao.addPhoto(eventId, filePath, System.currentTimeMillis())

  override suspend fun completeMeeting(eventId: String) = eventDao.completeEvent(eventId)

  override suspend fun getAllPhotoFilePaths(): List<String> = eventDao.getAllPhotoFilePaths()

  private fun SupervisorEventWithDetails.toEntry(): MeetingEntry = MeetingEntry(
    id = event.id,
    eventType = EventType.valueOf(event.eventType),
    projectName = event.projectName,
    startDate = event.startDate,
    endDate = event.endDate,
    remarks = event.remarks,
    createdAt = event.createdAt,
  )

  private fun SupervisorEventWithDetails.toDetail(rosterSize: Int, gatherings: List<GatheringSummary>): MeetingDetail = MeetingDetail(
    id = event.id,
    eventType = EventType.valueOf(event.eventType),
    projectName = event.projectName,
    startDate = event.startDate,
    endDate = event.endDate,
    remarks = event.remarks,
    status = EventStatus.valueOf(event.status),
    attendedCount = attendance.count { it.attendanceStatus == "PRESENT" && it.gatheringId == null },
    totalRosterCount = rosterSize,
    photoPaths = photos.map { it.filePath },
    prePostMarksApplicable = event.prePostMarksApplicable,
    gatherings = gatherings,
  )
}
