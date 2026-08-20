package org.armman.supervisor.data.meetingtraining

import com.google.gson.Gson
import org.armman.supervisor.data.auth.ErrorResponseDto
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.events.AddGatheringRequest
import org.armman.supervisor.data.events.AttachPhotoRequest
import org.armman.supervisor.data.events.AttendanceEntryDto
import org.armman.supervisor.data.events.CompleteMarkRequest
import org.armman.supervisor.data.events.PendingGatheringDao
import org.armman.supervisor.data.events.PendingGatheringEntity
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.RescheduleEventRequest
import org.armman.supervisor.data.events.SaveAttendanceRequest
import org.armman.supervisor.data.events.SaveMarkRequest
import org.armman.supervisor.data.events.SupervisorEventCacheDao
import org.armman.supervisor.data.events.SupervisorEventCacheEntity
import org.armman.supervisor.data.events.SupervisorEventOperationsApi
import org.armman.supervisor.data.events.SupervisorEventSyncStatus
import org.armman.supervisor.data.events.SupervisorEventsApi
import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventGatheringEntity
import org.armman.supervisor.data.local.EventMarksEntity
import org.armman.supervisor.data.local.EventPhotoEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.data.masterdata.ItemMasterAndTrainingApi
import org.armman.supervisor.data.media.MediaRepository
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
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val EMPTY_TOPICS_JSON = "{}"
private const val TRAINING_TOPIC_ACTIVE_STATUS = "ACTIVE"

private val RECONCILED_EVENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** The server's `eventDate` is an ISO-8601 instant; this app's own [SupervisorEventEntity]/
 * [org.armman.supervisor.data.events.PendingSupervisorEventEntity] rows store "dd MMM yyyy" —
 * same normalization AssignItemDetailScreen's `toDisplayDate` already does for transaction dates. */
private fun String.toDisplayDate(): String =
  runCatching { RECONCILED_EVENT_DATE_FORMATTER.format(Instant.parse(this).atZone(ZoneId.systemDefault())) }
    .getOrDefault(this)

/** Shared with [GatheringSyncExecutor] — training topic names are short catalog labels (e.g.
 * "Antenatal Care Basics"), never free text, so this delimiter can't collide with a real name. */
const val GATHERING_TOPIC_NAME_DELIMITER = "|"

/** The real server ids [MeetingTrainingRepositoryImpl.saveMarks]/[MeetingTrainingRepositoryImpl.completeMarks]
 * need together before attempting a remote call. */
private data class RemoteTopicAndGatheringIds(val topicId: String, val gatheringId: String)

/**
 * Concrete [MeetingTrainingRepository]. Projects and the Sakhi roster delegate to
 * [projectsRepository] — the same real source Dashboard/Assign Item/Call Sheet use.
 * [scheduleMeeting]/[scheduleTraining] follow sakhi-mobile-app's offline write-queue pattern:
 * the local [SupervisorEventEntity] row (attendance/marks/photos) is created immediately with a
 * client-generated id, regardless of connectivity; a [PendingSupervisorEventEntity] is queued and
 * either synced now (online) or left for [syncScheduler]'s background WorkManager job (offline).
 *
 * Attendance, cancel/complete/reschedule, gatherings, and marks are ALSO wired to the real
 * `supervisor-events` sub-resource endpoints (see [SupervisorEventOperationsApi]), but only once
 * this event's own creation has synced — every one of these is a *dependent* write that needs the
 * server's own event id ([PendingSupervisorEventEntity.remoteId]), which is null until the parent
 * event itself syncs. [remoteEventIdOrNull] is the single gate all of them share:
 * - No `remoteId` yet (event still mid-sync): local Room write only, nothing else — no error,
 *   this is expected and resolves itself once the event syncs.
 * - `remoteId` present, offline, or a genuine [IOException] mid-call: local Room write only, same
 *   as above (this app has no persistent retry queue for these — see class doc in the repo's
 *   design notes; they're all resumable "reopen the screen and resubmit" actions).
 * - `remoteId` present and online: attendance/marks are write-through (local write first, then
 *   best-effort remote — a remote rejection throws but the local copy stands, since resubmitting
 *   is always safe). Cancel/complete/reschedule/addGathering are remote-gated (remote call first,
 *   local write only commits on success) — these are one-way state transitions with no retry
 *   queue to reconcile a local/server split later, so they must not diverge.
 *
 * Reads ([getEvents]/[getEventDetail]) reconcile with the backend via [reconcileServerEvents] —
 * any server-confirmed event with no local [SupervisorEventEntity] row (e.g. after a schema-
 * migration wipe, reinstall, or a fresh device) gets a minimal local "shadow" row created for it,
 * so the list/detail screens never silently lose track of a real, server-confirmed event. A
 * shadow row starts with zero attendance/gatherings/topics/marks/photos — the server's flat
 * `SupervisorEvent` model has none of that to give back — but is otherwise a completely normal,
 * fully-interactive local event from that point on (its [PendingSupervisorEventEntity] is created
 * already `SYNCED` with the server's own id as `remoteId`, so every remote-gated action above
 * treats it exactly like one created and synced on this device).
 */
class MeetingTrainingRepositoryImpl @Inject constructor(
  private val eventDao: SupervisorEventDao,
  private val pendingDao: PendingSupervisorEventDao,
  private val syncScheduler: SupervisorEventSyncScheduler,
  private val syncExecutor: SupervisorEventSyncExecutor,
  private val projectsRepository: ProjectsRepository,
  private val sessionStore: SessionStore,
  private val connectivityChecker: ConnectivityChecker,
  private val operationsApi: SupervisorEventOperationsApi,
  private val itemMasterAndTrainingApi: ItemMasterAndTrainingApi,
  private val mediaRepository: MediaRepository,
  private val pendingGatheringDao: PendingGatheringDao,
  private val gatheringSyncScheduler: GatheringSyncScheduler,
  private val gatheringSyncExecutor: GatheringSyncExecutor,
  private val eventsApi: SupervisorEventsApi,
  private val eventCacheDao: SupervisorEventCacheDao,
) : MeetingTrainingRepository {

  private val gson = Gson()

  /** Null if the parent event hasn't synced yet (still PENDING/SYNCING/FAILED, or predates this
   * feature and has no pending row at all). See class doc for what each caller does with this. */
  private suspend fun remoteEventIdOrNull(eventId: String): String? = pendingDao.getById(eventId)?.remoteId

  private suspend fun canAttemptRemote(eventId: String): String? =
    remoteEventIdOrNull(eventId)?.takeIf { connectivityChecker.isOnline() }

  private fun <T> errorMessage(response: Response<T>, action: String): String {
    val errorJson = response.errorBody()?.string()
    val parsed = errorJson?.let { runCatching { gson.fromJson(it, ErrorResponseDto::class.java) }.getOrNull() }
    val detail = parsed?.message ?: parsed?.errorCode
    return if (detail != null) "Failed to $action: $detail" else "Failed to $action: HTTP ${response.code()}"
  }

  override suspend fun getProjects(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> =
    projectId?.let { projectsRepository.getSakhis(it) }.orEmpty().map { AttendanceRosterEntry(it.id, it.name) }

  override suspend fun getEvents(status: EventStatus): List<MeetingEntry> {
    reconcileServerEvents()
    return eventDao.getByStatus(status.name).map { it.toEntry() }
  }

  override suspend fun getEventDetail(eventId: String): MeetingDetail {
    if (eventDao.getById(eventId) == null) reconcileServerEvents()
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    val roster = projectsRepository.getSakhis(details.event.projectId)
    val rosterSize = roster.ifEmpty { null }?.size ?: details.attendance.size
    val gatherings = details.gatherings.map { it.toSummary(rosterSize) }
    return details.toDetail(rosterSize, gatherings)
  }

  /** Fetches every event the backend has confirmed and creates a local "shadow" row (see class
   * doc) for any that has none yet AND belongs to this supervisor. `GET /supervisor-events` is
   * NOT scoped by the caller's token — confirmed live (a single response returned events for
   * three distinct `supervisorId` values) — so this filters to [SessionStore]'s own subject id
   * client-side; without it, other supervisors' events would reconcile in with an unresolvable
   * `projectId` (this session's own [ProjectsRepository] has no reason to know their projects),
   * showing as a raw UUID instead of a real project name. A no-op when offline, unauthenticated,
   * or on any network failure — reconciliation is a best-effort enrichment of the local-first read
   * path, never a hard requirement for [getEvents]/[getEventDetail] to return something. Existing
   * local rows (with or without richer local-only data) are never touched, so this can only ADD
   * events to what the screens already show, never remove or overwrite one. */
  private suspend fun reconcileServerEvents() {
    if (!connectivityChecker.isOnline()) return
    val ownSupervisorId = sessionStore.readSession()?.subjectId ?: return
    removeOtherSupervisorsShadowEvents(ownSupervisorId)
    val serverEvents = try {
      val response = eventsApi.getEvents()
      if (!response.isSuccessful) return
      val body = response.body() ?: return
      if (!body.success) return
      body.data.orEmpty().filter { it.supervisorId == ownSupervisorId }
    } catch (e: IOException) {
      return
    }
    if (serverEvents.isEmpty()) return

    val projectNamesById = projectsRepository.getProjects().associate { it.id to it.name }
    for (dto in serverEvents) {
      if (eventDao.getById(dto.id) != null) continue
      // A self-created event keeps its original client-generated id forever once synced (see
      // SupervisorEventSyncExecutor), so a match here means this event already exists locally
      // under a different id and reconciling it again would create a duplicate visible row.
      if (pendingDao.getByRemoteId(dto.id) != null) continue
      eventCacheDao.upsert(
        SupervisorEventCacheEntity(
          id = dto.id,
          projectId = dto.projectId,
          supervisorId = dto.supervisorId,
          eventType = dto.eventType,
          eventDate = dto.eventDate,
          topicsJson = dto.topicsJson,
          remarks = dto.remarks,
          status = dto.status,
          photoMediaId = dto.photoMediaId,
          createdAt = dto.createdAt,
          updatedAt = dto.updatedAt,
        ),
      )
      val displayDate = dto.eventDate.toDisplayDate()
      eventDao.insertEvent(
        SupervisorEventEntity(
          id = dto.id,
          projectId = dto.projectId,
          projectName = projectNamesById[dto.projectId] ?: dto.projectId,
          eventType = dto.eventType,
          startDate = displayDate,
          endDate = displayDate,
          remarks = dto.remarks.orEmpty(),
          status = dto.status,
          createdAt = createdAtEpochMillisOrNow(dto.createdAt),
        ),
      )
      pendingDao.upsert(
        PendingSupervisorEventEntity(
          id = dto.id,
          projectId = dto.projectId,
          supervisorId = dto.supervisorId,
          eventType = dto.eventType,
          eventDate = displayDate,
          topicsJson = dto.topicsJson,
          remarks = dto.remarks,
          status = dto.status,
          syncStatus = SupervisorEventSyncStatus.SYNCED.name,
          createdAtEpochMillis = createdAtEpochMillisOrNow(dto.createdAt),
          lastAttemptAtEpochMillis = null,
          retryCount = 0,
          // The server never invented a separate client id for this event — its own id IS this
          // shadow row's permanent local identity, so remoteId just points back to itself.
          remoteId = dto.id,
          lastErrorMessage = null,
        ),
      )
    }
  }

  private fun createdAtEpochMillisOrNow(createdAt: String): Long =
    runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(Instant.now().toEpochMilli())

  /** One-time cleanup for shadow rows [reconcileServerEvents] created before it filtered by
   * [ownSupervisorId] — `GET /supervisor-events` returns every supervisor's events (see
   * [reconcileServerEvents]'s doc), so an earlier run of this method could have reconciled other
   * supervisors' events onto this device. Only removes rows that are BOTH a reconciled shadow
   * (identified the same way [PendingSupervisorEventEntity.remoteId] always equals [id] for one —
   * an event this app itself scheduled always starts with `remoteId = null`) AND tagged with a
   * different supervisor — an event the current user actually created is never touched, since its
   * `PendingSupervisorEventEntity.supervisorId` always matches [ownSupervisorId]. */
  private suspend fun removeOtherSupervisorsShadowEvents(ownSupervisorId: String) {
    val staleShadowIds = pendingDao.getAll()
      .filter { it.supervisorId != ownSupervisorId && it.remoteId == it.id }
      .map { it.id }
    for (id in staleShadowIds) {
      eventDao.getById(id)?.let { eventDao.deleteEvent(it.event) }
      pendingDao.deleteById(id)
    }
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

  override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> {
    val response = itemMasterAndTrainingApi.getTrainingTopics()
    if (!response.isSuccessful) error(errorMessage(response, "load training topics"))
    val body = response.body() ?: error("Empty training topics response")
    if (!body.success) error(body.message ?: "Failed to load training topics")
    return body.data.orEmpty()
      .filter { it.status == TRAINING_TOPIC_ACTIVE_STATUS }
      .map { TrainingTopic(it.id, it.topicName) }
  }

  /**
   * Creates a Training gathering. Same three-way gate as events ([canAttemptRemote]'s class doc),
   * with one addition: a gathering created while online has its real server ids reconciled onto
   * the local rows immediately ([EventGatheringEntity.remoteId] and each
   * [org.armman.supervisor.data.local.EventTopicEntity.remoteId], matched by topic name) — unlike
   * the discarded-mapping bug this replaces, [saveMarks]/[completeMarks] now depend on these ids
   * being real UUIDs, not the local placeholder.
   *
   * Whenever the online attempt can't complete right now (event not yet synced, offline, or a
   * connectivity drop mid-call), the gathering is created locally with placeholder ids AND queued
   * as a [PendingGatheringEntity] — [gatheringSyncExecutor] resolves it (and its topics' remote
   * ids) once the parent event has synced and connectivity allows, exactly mirroring how
   * [PendingSupervisorEventEntity] durably syncs events. Marks entered before that resolution
   * still save locally (see [saveMarks]) and become sendable once this gathering's ids resolve.
   */
  override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String {
    val gatheringId = "gathering-${UUID.randomUUID()}"
    val topicIds = topicNames.map { "topic-${UUID.randomUUID()}" }
    val remarks = "Added $date"

    val remoteEventId = canAttemptRemote(eventId)
    if (remoteEventId != null) {
      val catalog = getTrainingTopicsCatalog()
      val realTopicIds = topicNames.map { name ->
        catalog.firstOrNull { it.name == name }?.id ?: error("Unknown training topic: $name")
      }
      val request = AddGatheringRequest(gatheringDate = date, topicIds = realTopicIds, remarks = remarks)
      try {
        val response = operationsApi.addGathering(remoteEventId, request)
        if (!response.isSuccessful) error(errorMessage(response, "add gathering"))
        val body = response.body() ?: error("Empty add-gathering response")
        if (!body.success) error(body.message ?: "Failed to add gathering")
        val gathering = body.data ?: error("Empty add-gathering data")

        eventDao.addGathering(eventId, gatheringId, topicIds, topicNames, date, System.currentTimeMillis())
        eventDao.setGatheringRemoteId(gatheringId, gathering.id)
        for (name in topicNames) {
          val realId = catalog.firstOrNull { it.name == name }?.id ?: continue
          eventDao.setTopicRemoteId(gatheringId, name, realId)
        }
        return gatheringId
      } catch (e: IOException) {
        eventDao.addGathering(eventId, gatheringId, topicIds, topicNames, date, System.currentTimeMillis())
        queuePendingGathering(gatheringId, eventId, date, topicNames, remarks)
        return gatheringId
      }
    }

    eventDao.addGathering(eventId, gatheringId, topicIds, topicNames, date, System.currentTimeMillis())
    queuePendingGathering(gatheringId, eventId, date, topicNames, remarks)
    return gatheringId
  }

  private suspend fun queuePendingGathering(
    gatheringId: String,
    eventId: String,
    date: String,
    topicNames: List<String>,
    remarks: String,
  ) {
    pendingGatheringDao.upsert(
      PendingGatheringEntity(
        id = gatheringId,
        eventId = eventId,
        gatheringDate = date,
        topicNamesJoined = topicNames.joinToString(GATHERING_TOPIC_NAME_DELIMITER),
        remarks = remarks,
        syncStatus = "PENDING",
        createdAtEpochMillis = Instant.now().toEpochMilli(),
        lastAttemptAtEpochMillis = null,
        retryCount = 0,
        lastErrorMessage = null,
      ),
    )
    if (connectivityChecker.isOnline()) gatheringSyncScheduler.syncNow()
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
    submitAttendanceIfSynced(eventId, attendance)
  }

  /** Shared remote submission for both Meeting attendance ([saveAttendance]) and per-gathering
   * Training attendance ([saveGatheringAttendance]) — the server has one flat attendance concept
   * per eventId, no gathering scoping, so both call sites send the same shape. Write-through: the
   * local Room write above always happens first and is never rolled back on a remote rejection —
   * attendance is always safely resubmittable, unlike the state-transition ops below. */
  private suspend fun submitAttendanceIfSynced(eventId: String, attendance: List<AttendanceEntry>) {
    val remoteEventId = canAttemptRemote(eventId) ?: return
    val request = SaveAttendanceRequest(
      attendance.map { AttendanceEntryDto(sakhiId = it.sakhiId, attendanceStatus = if (it.present) "PRESENT" else "ABSENT") },
    )
    try {
      val response = operationsApi.saveAttendance(remoteEventId, request)
      if (!response.isSuccessful) error(errorMessage(response, "save attendance"))
      val body = response.body() ?: error("Empty save-attendance response")
      if (!body.success) error(body.message ?: "Failed to save attendance")
    } catch (e: IOException) {
      // Connectivity dropped mid-request — local write already stands, matches offline behavior.
    }
  }

  override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> =
    eventDao.getAttendanceForGathering(gatheringId).map { AttendanceEntry(it.sakhiId, it.sakhiName, it.attendanceStatus == "PRESENT") }

  override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> =
    eventDao.getGatheringWithTopics(gatheringId)?.topics?.map { TrainingTopic(it.id, it.topicName) }.orEmpty()

  override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> =
    eventDao.getMarksForTopic(topicId, marksType.name).map { MarksEntry(it.sakhiId, it.sakhiName, it.marks) }

  /**
   * Saves Pre/Post marks locally (write-through, always) and best-effort pushes them remotely —
   * but only once BOTH this topic and its parent gathering have a real server UUID
   * ([org.armman.supervisor.data.local.SupervisorEventDao.getTopicAndGatheringRemoteIds]).
   * Sending the local placeholder id in either field is what caused "topicId: Invalid uuid": the
   * backend validates both as real UUIDs. If either hasn't resolved yet (gathering still pending
   * in [gatheringSyncExecutor]'s queue), the remote push is skipped for now — these rows are
   * already durably queued via the gathering's own pending-sync entry and this repository has no
   * separate marks-retry queue, so once the gathering resolves, the NEXT save/complete call for
   * this topic (or a background sync extension) is what actually delivers them; see class doc.
   */
  override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) {
    val rows = entries.mapNotNull { entry ->
      entry.marks?.let { EventMarksEntity(topicId = topicId, marksType = marksType.name, sakhiId = entry.sakhiId, sakhiName = entry.sakhiName, marks = it) }
    }
    eventDao.saveMarks(eventId, topicId, marksType.name, rows)

    canAttemptRemote(eventId) ?: return
    val remoteIds = remoteTopicAndGatheringIds(topicId) ?: return
    try {
      for (row in rows) {
        val request = SaveMarkRequest(gatheringId = remoteIds.gatheringId, sakhiId = row.sakhiId, markType = marksType.name, score = row.marks)
        val response = operationsApi.saveMark(remoteIds.topicId, request)
        if (!response.isSuccessful) error(errorMessage(response, "save marks"))
        val body = response.body() ?: error("Empty save-marks response")
        if (!body.success) error(body.message ?: "Failed to save marks")
      }
    } catch (e: IOException) {
      // Connectivity dropped mid-fan-out — local bulk write already stands.
    }
  }

  /** The real server (topicId, gatheringId) pair for [localTopicId], or null if either hasn't
   * resolved yet — see [saveMarks]'s doc for why both are required before any remote marks call. */
  private suspend fun remoteTopicAndGatheringIds(localTopicId: String): RemoteTopicAndGatheringIds? {
    val ids = eventDao.getTopicAndGatheringRemoteIds(localTopicId) ?: return null
    val topicRemoteId = ids.topicRemoteId ?: return null
    val gatheringRemoteId = ids.gatheringRemoteId ?: return null
    return RemoteTopicAndGatheringIds(topicRemoteId, gatheringRemoteId)
  }

  override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) {
    eventDao.completeMarks(eventId, topicId, marksType.name, System.currentTimeMillis())

    canAttemptRemote(eventId) ?: return
    val remoteIds = remoteTopicAndGatheringIds(topicId) ?: return
    val roster = eventDao.getMarksForTopic(topicId, marksType.name)
    try {
      for (row in roster) {
        val request = CompleteMarkRequest(gatheringId = remoteIds.gatheringId, sakhiId = row.sakhiId, markType = marksType.name)
        val response = operationsApi.completeMark(remoteIds.topicId, request)
        if (!response.isSuccessful) error(errorMessage(response, "complete marks"))
        val body = response.body() ?: error("Empty complete-marks response")
        if (!body.success) error(body.message ?: "Failed to complete marks")
      }
    } catch (e: IOException) {
      // Connectivity dropped mid-fan-out — local completion already stands.
    }
  }

  override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) {
    val remoteEventId = canAttemptRemote(eventId)
    if (remoteEventId != null) {
      eventDao.requireScheduled(eventId)
      val request = RescheduleEventRequest(eventDate = newStartDate, remarks = "Rescheduled to $newStartDate")
      try {
        val response = operationsApi.rescheduleEvent(remoteEventId, request)
        if (!response.isSuccessful) error(errorMessage(response, "reschedule event"))
        val body = response.body() ?: error("Empty reschedule response")
        if (!body.success) error(body.message ?: "Failed to reschedule event")
      } catch (e: IOException) {
        eventDao.rescheduleEvent(eventId, newStartDate, newEndDate)
        return
      }
    }
    eventDao.rescheduleEvent(eventId, newStartDate, newEndDate)
  }

  override suspend fun cancelMeeting(eventId: String) {
    val remoteEventId = canAttemptRemote(eventId)
    if (remoteEventId != null) {
      eventDao.requireScheduled(eventId)
      try {
        val response = operationsApi.cancelEvent(remoteEventId)
        if (!response.isSuccessful) error(errorMessage(response, "cancel event"))
        val body = response.body() ?: error("Empty cancel response")
        if (!body.success) error(body.message ?: "Failed to cancel event")
      } catch (e: IOException) {
        eventDao.cancelEvent(eventId)
        return
      }
    }
    eventDao.cancelEvent(eventId)
  }

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
    submitAttendanceIfSynced(eventId, attendance)
  }

  override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    return details.attendance
      .filter { it.gatheringId == null }
      .map { AttendanceEntry(it.sakhiId, it.sakhiName, present = it.attendanceStatus == "PRESENT") }
  }

  override suspend fun addPhoto(eventId: String, filePath: String) =
    eventDao.addPhoto(eventId, filePath, System.currentTimeMillis())

  /**
   * Completing requires a real, server-attached photo — the backend rejects `/complete` without a
   * `photoMediaId` (FR-SV-2.3), so this is remote-gated unconditionally, unlike attendance/marks:
   * - Event not yet synced to the server (no `remoteId`): blocked outright. A local-only complete
   *   would leave an event the server can never accept once it does sync (it has no photo there),
   *   so there is no safe offline path for this action.
   * - Event synced but offline right now: also blocked — uploading a photo has no local-write-
   *   through equivalent.
   * - Event synced and online: uploads the oldest not-yet-uploaded photo (skips re-uploading one
   *   that already has a [EventPhotoEntity.remoteMediaId] from an earlier attempt), attaches it via
   *   `/photos`, then calls `/complete`. The upload and attach steps persist their result
   *   ([SupervisorEventDao.setPhotoRemoteMediaId]) independently of whether `/complete` itself then
   *   succeeds, so a retry after a later-stage failure (e.g. missing attendance) never re-uploads.
   */
  override suspend fun completeMeeting(eventId: String) {
    val remoteEventId = remoteEventIdOrNull(eventId)
      ?: error("This event hasn't finished syncing yet — try again once it's synced.")
    check(connectivityChecker.isOnline()) { "Connect to the internet to complete this event." }

    eventDao.requireScheduledWithPhotoForComplete(eventId)
    val mediaId = ensurePhotoUploaded(eventId)

    val attachResponse = operationsApi.attachPhoto(remoteEventId, AttachPhotoRequest(mediaId))
    if (!attachResponse.isSuccessful) error(errorMessage(attachResponse, "attach photo"))
    val attachBody = attachResponse.body() ?: error("Empty attach-photo response")
    if (!attachBody.success) error(attachBody.message ?: "Failed to attach photo")

    val response = operationsApi.completeEvent(remoteEventId)
    if (!response.isSuccessful) error(errorMessage(response, "complete event"))
    val body = response.body() ?: error("Empty complete-event response")
    if (!body.success) error(body.message ?: "Failed to complete event")
    eventDao.completeEvent(eventId)
  }

  /** Returns the [EventPhotoEntity.remoteMediaId] for this event's oldest photo, uploading it
   * first if it doesn't have one yet. [eventDao.requireScheduledWithPhotoForComplete] already
   * guarantees at least one photo exists by the time this is called. */
  private suspend fun ensurePhotoUploaded(eventId: String): String {
    val details = eventDao.getById(eventId) ?: error("Unknown event id: $eventId")
    val photo = details.photos.minBy { it.capturedAt }
    photo.remoteMediaId?.let { return it }

    val mediaId = mediaRepository.uploadPhoto(photo.filePath)
    eventDao.setPhotoRemoteMediaId(photo.rowId, mediaId)
    return mediaId
  }

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
