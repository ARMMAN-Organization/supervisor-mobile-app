package org.armman.supervisor.data.meetingtraining

import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.SupervisorEventSyncStatus
import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventGatheringEntity
import org.armman.supervisor.data.local.EventMarksEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.data.projects.ProjectsRepository
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val EMPTY_TOPICS_JSON = "{}"

/**
 * Concrete [MeetingTrainingRepository]. Projects and the Sakhi roster delegate to
 * [projectsRepository] — the same real source Dashboard/Assign Item/Call Sheet use.
 * [scheduleMeeting]/[scheduleTraining] follow sakhi-mobile-app's offline write-queue pattern:
 * the local [SupervisorEventEntity] row (attendance/marks/photos) is created immediately with a
 * client-generated id, regardless of connectivity; a [PendingSupervisorEventEntity] is queued and
 * either synced now (online) or left for [syncScheduler]'s background WorkManager job (offline).
 * Every other flow on this event (attendance, marks, photos, reschedule, cancel, complete) keeps
 * working exactly as today, keyed on the stable client id — none of those have a backend
 * endpoint, so they stay 100% local. Reads ([getEvents]/[getEventDetail]) stay local-Room-only:
 * the server's flat `SupervisorEvent` row has no gatherings/attendance/photo concept, so
 * reconciling server-only events (created elsewhere) into this richer local shape is out of
 * scope (see plan §4 Option A).
 */
class MeetingTrainingRepositoryImpl @Inject constructor(
  private val eventDao: SupervisorEventDao,
  private val pendingDao: PendingSupervisorEventDao,
  private val syncScheduler: SupervisorEventSyncScheduler,
  private val syncExecutor: SupervisorEventSyncExecutor,
  private val projectsRepository: ProjectsRepository,
  private val sessionStore: SessionStore,
  private val connectivityChecker: ConnectivityChecker,
) : MeetingTrainingRepository {

  private val trainingTopicsCatalog = listOf(
    TrainingTopic("topic-1", "Antenatal Care Basics"),
    TrainingTopic("topic-2", "Nutrition Counselling"),
    TrainingTopic("topic-3", "Danger Signs in Pregnancy"),
    TrainingTopic("topic-4", "Newborn Care"),
    TrainingTopic("topic-5", "Family Planning"),
    TrainingTopic("topic-6", "Data Collection & App Usage"),
  )

  override suspend fun getProjects(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> =
    projectId?.let { projectsRepository.getSakhis(it) }.orEmpty().map { AttendanceRosterEntry(it.id, it.name) }

  override suspend fun getEvents(status: EventStatus): List<MeetingEntry> =
    eventDao.getByStatus(status.name).map { it.toEntry() }

  override suspend fun getEventDetail(eventId: String): MeetingDetail {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    val roster = projectsRepository.getSakhis(details.event.projectId)
    val rosterSize = roster.ifEmpty { null }?.size ?: details.attendance.size
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

  override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult {
    val id = "event-${UUID.randomUUID()}"
    val createdAt = System.currentTimeMillis()
    val localEvent = SupervisorEventEntity(
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
    val entry = MeetingEntry(
      id = id,
      eventType = EventType.MEETING,
      projectName = request.projectName,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      createdAt = createdAt,
    )
    return queueAndSync(id, localEvent, request.projectId, EventType.MEETING, request.startDate, request.remarks, entry)
  }

  override suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult {
    val id = "event-${UUID.randomUUID()}"
    val createdAt = System.currentTimeMillis()
    val localEvent = SupervisorEventEntity(
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
    val entry = MeetingEntry(
      id = id,
      eventType = EventType.TRAINING,
      projectName = request.projectName,
      startDate = request.startDate,
      endDate = request.endDate,
      remarks = request.remarks,
      createdAt = createdAt,
    )
    return queueAndSync(id, localEvent, request.projectId, EventType.TRAINING, request.startDate, request.remarks, entry)
  }

  /** Inserts [localEvent] into the local encrypted DB immediately (so attendance/marks/photos work
   * right away, offline or not) and queues [id] for sync to the real `supervisor-events` API —
   * synced immediately when online, or left for background sync when offline. Mirrors
   * [org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl]'s write-queue pattern. Always
   * submits `status=SCHEDULED`: a newly created event has no photo yet, and the server requires
   * `photoMediaId` for `COMPLETED`. [ScheduleMeetingRequest.projectName]/`endDate` have no server
   * field, so they're only persisted locally, never sent. A real online rejection still throws —
   * and rolls back both [localEvent] and its [PendingSupervisorEventEntity] row, so no orphaned
   * local-only event or forever-retried pending row survives a hard failure; only connectivity
   * failures are queued (with both rows kept, exactly as offline scheduling does). */
  private suspend fun queueAndSync(
    id: String,
    localEvent: SupervisorEventEntity,
    projectId: String,
    eventType: EventType,
    eventDate: String,
    remarks: String,
    entry: MeetingEntry,
  ): EventScheduleResult {
    eventDao.insertEvent(localEvent)
    val supervisorId = checkNotNull(sessionStore.readSession()?.subjectId) { "No active session" }
    val pending = PendingSupervisorEventEntity(
      id = id,
      projectId = projectId,
      supervisorId = supervisorId,
      eventType = eventType.name,
      eventDate = eventDate,
      topicsJson = EMPTY_TOPICS_JSON,
      remarks = remarks.ifBlank { null },
      status = EventStatus.SCHEDULED.name,
      syncStatus = SupervisorEventSyncStatus.PENDING.name,
      createdAtEpochMillis = Instant.now().toEpochMilli(),
      lastAttemptAtEpochMillis = null,
      retryCount = 0,
      remoteId = null,
      lastErrorMessage = null,
    )
    pendingDao.upsert(pending)

    if (!connectivityChecker.isOnline()) {
      syncScheduler.syncNow()
      return EventScheduleResult.QueuedOffline(entry)
    }
    return when (val result = syncExecutor.runOne(id)) {
      is SupervisorEventSyncItemResult.Synced -> EventScheduleResult.Synced(entry)
      is SupervisorEventSyncItemResult.Failed -> {
        eventDao.deleteEvent(localEvent)
        pendingDao.deleteById(id)
        error(result.message ?: "Failed to schedule event")
      }
      is SupervisorEventSyncItemResult.Retryable -> {
        syncScheduler.syncNow()
        EventScheduleResult.QueuedOffline(entry)
      }
    }
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
