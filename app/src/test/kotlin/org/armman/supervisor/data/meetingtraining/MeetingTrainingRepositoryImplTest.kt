package org.armman.supervisor.data.meetingtraining

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.auth.UserSession
import org.armman.supervisor.data.auth.session.FakeSecureKeyValueStore
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.events.AddGatheringRequest
import org.armman.supervisor.data.events.AttachPhotoDto
import org.armman.supervisor.data.events.AttachPhotoEnvelopeDto
import org.armman.supervisor.data.events.AttachPhotoRequest
import org.armman.supervisor.data.events.AttendanceEnvelopeDto
import org.armman.supervisor.data.events.CompleteMarkRequest
import org.armman.supervisor.data.events.CreateSupervisorEventRequest
import org.armman.supervisor.data.events.GatheringDto
import org.armman.supervisor.data.events.GatheringEnvelopeDto
import org.armman.supervisor.data.events.MarkDto
import org.armman.supervisor.data.events.MarksEnvelopeDto
import org.armman.supervisor.data.events.PendingGatheringDao
import org.armman.supervisor.data.events.PendingGatheringEntity
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.RescheduleEventRequest
import org.armman.supervisor.data.events.SaveAttendanceRequest
import org.armman.supervisor.data.events.SaveMarkRequest
import org.armman.supervisor.data.events.SupervisorEventCacheDao
import org.armman.supervisor.data.events.SupervisorEventCacheEntity
import org.armman.supervisor.data.events.SupervisorEventDto
import org.armman.supervisor.data.events.SupervisorEventEnvelopeDto
import org.armman.supervisor.data.events.SupervisorEventOperationsApi
import org.armman.supervisor.data.events.SupervisorEventsApi
import org.armman.supervisor.data.events.SupervisorEventsEnvelopeDto
import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventGatheringEntity
import org.armman.supervisor.data.local.EventMarksCompletionEntity
import org.armman.supervisor.data.local.EventMarksEntity
import org.armman.supervisor.data.local.EventPhotoEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.EventTopicEntity
import org.armman.supervisor.data.local.GatheringWithTopics
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.data.local.TopicAndGatheringRemoteIds
import org.armman.supervisor.data.masterdata.ItemMasterAndTrainingApi
import org.armman.supervisor.data.masterdata.ItemMasterListEnvelopeDto
import org.armman.supervisor.data.masterdata.TrainingTopicDto
import org.armman.supervisor.data.masterdata.TrainingTopicsEnvelopeDto
import org.armman.supervisor.data.media.MediaRepository
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.meetingtraining.AttendanceEntry
import org.armman.supervisor.ui.meetingtraining.EventType
import org.armman.supervisor.ui.meetingtraining.MarksEntry
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingRequest
import org.armman.supervisor.ui.meetingtraining.ScheduleTrainingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import retrofit2.Response

/** Hand-written in-memory fake — see AssignItemRepositoryImplTest for why (Room needs a Context). */
private class FakeSupervisorEventDao : SupervisorEventDao {
  private val events = mutableMapOf<String, SupervisorEventEntity>()
  private val attendanceByEvent = mutableMapOf<String, MutableList<EventAttendanceEntity>>()
  private val photosByEvent = mutableMapOf<String, MutableList<EventPhotoEntity>>()
  private var nextPhotoRowId = 1L
  private val gatheringsByEvent = mutableMapOf<String, MutableList<EventGatheringEntity>>()
  private val topicsByGathering = mutableMapOf<String, MutableList<EventTopicEntity>>()
  private val attendanceByGathering = mutableMapOf<String, MutableList<EventAttendanceEntity>>()
  private val marksByTopic = mutableMapOf<String, MutableList<EventMarksEntity>>()
  private val marksCompletionByTopic = mutableMapOf<String, MutableList<EventMarksCompletionEntity>>()

  override suspend fun getByStatus(status: String): List<SupervisorEventWithDetails> =
    events.values.filter { it.status == status }.sortedByDescending { it.createdAt }.map { it.toDetails() }

  override suspend fun getById(eventId: String): SupervisorEventWithDetails? = events[eventId]?.toDetails()

  override suspend fun insertEvent(entity: SupervisorEventEntity) {
    check(!events.containsKey(entity.id)) { "duplicate id: ${entity.id}" }
    events[entity.id] = entity
  }

  override suspend fun updateEvent(entity: SupervisorEventEntity) {
    check(events.containsKey(entity.id)) { "Unknown event id: ${entity.id}" }
    events[entity.id] = entity
  }

  override suspend fun deleteMeetingAttendanceForEvent(eventId: String) {
    attendanceByEvent.remove(eventId)
  }

  override suspend fun deleteAttendanceForGathering(gatheringId: String) {
    attendanceByGathering.remove(gatheringId)
  }

  override suspend fun insertAttendance(rows: List<EventAttendanceEntity>) {
    rows.forEach { row ->
      val gatheringId = row.gatheringId
      if (gatheringId != null) {
        attendanceByGathering.getOrPut(gatheringId) { mutableListOf() }.add(row)
      } else {
        attendanceByEvent.getOrPut(row.eventId) { mutableListOf() }.add(row)
      }
    }
  }

  override suspend fun insertPhoto(photo: EventPhotoEntity) {
    photosByEvent.getOrPut(photo.eventId) { mutableListOf() }.add(photo.copy(rowId = nextPhotoRowId++))
  }

  override suspend fun setPhotoRemoteMediaId(rowId: Long, remoteMediaId: String) {
    photosByEvent.values.forEach { photos ->
      val index = photos.indexOfFirst { it.rowId == rowId }
      if (index >= 0) photos[index] = photos[index].copy(remoteMediaId = remoteMediaId)
    }
  }

  override suspend fun insertGathering(gathering: EventGatheringEntity) {
    gatheringsByEvent.getOrPut(gathering.eventId) { mutableListOf() }.add(gathering)
  }

  override suspend fun insertTopics(topics: List<EventTopicEntity>) {
    topics.forEach { topicsByGathering.getOrPut(it.gatheringId) { mutableListOf() }.add(it) }
  }

  override suspend fun setGatheringRemoteId(gatheringId: String, remoteId: String) {
    gatheringsByEvent.values.forEach { gatherings ->
      val index = gatherings.indexOfFirst { it.id == gatheringId }
      if (index >= 0) gatherings[index] = gatherings[index].copy(remoteId = remoteId)
    }
  }

  override suspend fun setTopicRemoteId(gatheringId: String, topicName: String, remoteId: String) {
    val topics = topicsByGathering[gatheringId] ?: return
    val index = topics.indexOfFirst { it.topicName == topicName }
    if (index >= 0) topics[index] = topics[index].copy(remoteId = remoteId)
  }

  override suspend fun getTopicAndGatheringRemoteIds(topicId: String): TopicAndGatheringRemoteIds? {
    val (gatheringId, topics) = topicsByGathering.entries.firstOrNull { (_, topics) -> topics.any { it.id == topicId } }
      ?: return null
    val topic = topics.first { it.id == topicId }
    val gathering = gatheringsByEvent.values.flatten().firstOrNull { it.id == gatheringId } ?: return null
    return TopicAndGatheringRemoteIds(topic.id, topic.remoteId, gathering.id, gathering.remoteId)
  }

  override suspend fun getGatheringWithTopics(gatheringId: String): GatheringWithTopics? {
    val gathering = gatheringsByEvent.values.flatten().find { it.id == gatheringId } ?: return null
    return GatheringWithTopics(gathering, topicsByGathering[gatheringId].orEmpty())
  }

  override suspend fun getAttendanceForGathering(gatheringId: String): List<EventAttendanceEntity> =
    attendanceByGathering[gatheringId].orEmpty()

  override suspend fun getTopicIdsForGathering(gatheringId: String): List<String> =
    topicsByGathering[gatheringId].orEmpty().map { it.id }

  override suspend fun getGatheringIdForTopic(topicId: String): String? =
    topicsByGathering.entries.firstOrNull { (_, topics) -> topics.any { it.id == topicId } }?.key

  override suspend fun insertMarks(rows: List<EventMarksEntity>) {
    rows.forEach { marksByTopic.getOrPut("${it.topicId}:${it.marksType}") { mutableListOf() }.add(it) }
  }

  override suspend fun deleteMarksForTopic(topicId: String, marksType: String) {
    marksByTopic.remove("$topicId:$marksType")
  }

  override suspend fun getMarksForTopic(topicId: String, marksType: String): List<EventMarksEntity> =
    marksByTopic["$topicId:$marksType"].orEmpty()

  override suspend fun getMarksCompletionForTopic(topicId: String): List<EventMarksCompletionEntity> =
    marksCompletionByTopic[topicId].orEmpty()

  override suspend fun getMarksCompletionForTopics(topicIds: List<String>): List<EventMarksCompletionEntity> =
    topicIds.flatMap { marksCompletionByTopic[it].orEmpty() }

  override suspend fun insertMarksCompletion(completion: EventMarksCompletionEntity) {
    marksCompletionByTopic.getOrPut(completion.topicId) { mutableListOf() }.add(completion)
  }

  override suspend fun getAllPhotoFilePaths(): List<String> =
    photosByEvent.values.flatten().map { it.filePath }

  override suspend fun deleteEvent(entity: SupervisorEventEntity) {
    events.remove(entity.id)
    attendanceByEvent.remove(entity.id)
    photosByEvent.remove(entity.id)
    gatheringsByEvent.remove(entity.id)
  }

  private fun SupervisorEventEntity.toDetails() =
    SupervisorEventWithDetails(this, attendanceByEvent[id].orEmpty(), photosByEvent[id].orEmpty(), gatheringsByEvent[id].orEmpty())
}

private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> =
    listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = when (projectId) {
    "loc-1" -> listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil"))
    "loc-2" -> listOf(SakhiOption("sakhi-3", "Kavita Sharma"))
    else -> emptyList()
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> = error("not used")

  override fun clearCache() = Unit
}

private class FakeSupervisorEventsApi : SupervisorEventsApi {
  var createFailure: (() -> Nothing)? = null
  var createErrorResponse: Response<SupervisorEventEnvelopeDto>? = null
  var getEventsFailure: (() -> Nothing)? = null
  var getEventsErrorResponse: Response<SupervisorEventsEnvelopeDto>? = null
  var serverEvents: List<SupervisorEventDto> = emptyList()
  var getEventsCallCount = 0
    private set
  private var nextId = 1

  override suspend fun getEvents(): Response<SupervisorEventsEnvelopeDto> {
    getEventsCallCount++
    getEventsFailure?.invoke()
    getEventsErrorResponse?.let { return it }
    return Response.success(SupervisorEventsEnvelopeDto(success = true, message = "OK", data = serverEvents))
  }

  override suspend fun createEvent(request: CreateSupervisorEventRequest): Response<SupervisorEventEnvelopeDto> {
    createFailure?.invoke()
    createErrorResponse?.let { return it }
    val event = SupervisorEventDto(
      id = "srv-event-${nextId++}",
      projectId = request.projectId,
      supervisorId = "sup-1",
      eventType = request.eventType,
      eventDate = request.eventDate,
      topicsJson = request.topicsJson,
      remarks = request.remarks,
      status = request.status,
      photoMediaId = request.photoMediaId,
      createdAt = "2026-01-01T00:00:00.000Z",
      updatedAt = "2026-01-01T00:00:00.000Z",
    )
    return Response.success(SupervisorEventEnvelopeDto(success = true, message = "OK", data = event))
  }
}

private class FakeSupervisorEventOperationsApi : SupervisorEventOperationsApi {
  var failWith: (() -> Nothing)? = null
  var attendanceErrorResponse: Response<AttendanceEnvelopeDto>? = null
  var cancelErrorResponse: Response<SupervisorEventEnvelopeDto>? = null
  var completeErrorResponse: Response<SupervisorEventEnvelopeDto>? = null
  var attachPhotoErrorResponse: Response<AttachPhotoEnvelopeDto>? = null
  var rescheduleErrorResponse: Response<SupervisorEventEnvelopeDto>? = null
  var gatheringErrorResponse: Response<GatheringEnvelopeDto>? = null
  var marksErrorResponse: Response<MarksEnvelopeDto>? = null
  /** Fails only the Nth `saveMark`/`completeMark` call in a fan-out (1-indexed), to test
   * stop-on-first-failure without aborting the whole fan-out on call 1. */
  var failMarksCallNumber: Int? = null

  var saveAttendanceCallCount = 0
    private set
  var cancelEventCallCount = 0
    private set
  var completeEventCallCount = 0
    private set
  var attachPhotoCallCount = 0
    private set
  var lastAttachPhotoRequest: AttachPhotoRequest? = null
  var rescheduleEventCallCount = 0
    private set
  var addGatheringCallCount = 0
    private set
  val saveMarkRequests = mutableListOf<SaveMarkRequest>()
  val completeMarkRequests = mutableListOf<CompleteMarkRequest>()
  var lastSaveAttendanceRequest: SaveAttendanceRequest? = null
  var lastRescheduleRequest: RescheduleEventRequest? = null
  var lastAddGatheringRequest: AddGatheringRequest? = null

  override suspend fun getAttendance(eventId: String): Response<AttendanceEnvelopeDto> = error("not used")

  override suspend fun saveAttendance(eventId: String, request: SaveAttendanceRequest): Response<AttendanceEnvelopeDto> {
    saveAttendanceCallCount++
    lastSaveAttendanceRequest = request
    failWith?.invoke()
    attendanceErrorResponse?.let { return it }
    return Response.success(AttendanceEnvelopeDto(success = true, message = "OK", data = request.attendance))
  }

  override suspend fun cancelEvent(eventId: String): Response<SupervisorEventEnvelopeDto> {
    cancelEventCallCount++
    failWith?.invoke()
    cancelErrorResponse?.let { return it }
    return Response.success(SupervisorEventEnvelopeDto(success = true, message = "OK", data = sampleEventDto(eventId)))
  }

  override suspend fun completeEvent(eventId: String): Response<SupervisorEventEnvelopeDto> {
    completeEventCallCount++
    failWith?.invoke()
    completeErrorResponse?.let { return it }
    return Response.success(SupervisorEventEnvelopeDto(success = true, message = "OK", data = sampleEventDto(eventId)))
  }

  override suspend fun attachPhoto(eventId: String, request: AttachPhotoRequest): Response<AttachPhotoEnvelopeDto> {
    attachPhotoCallCount++
    lastAttachPhotoRequest = request
    failWith?.invoke()
    attachPhotoErrorResponse?.let { return it }
    return Response.success(
      AttachPhotoEnvelopeDto(success = true, message = "OK", data = AttachPhotoDto("photo-link-1", eventId, request.mediaId)),
    )
  }

  override suspend fun rescheduleEvent(eventId: String, request: RescheduleEventRequest): Response<SupervisorEventEnvelopeDto> {
    rescheduleEventCallCount++
    lastRescheduleRequest = request
    failWith?.invoke()
    rescheduleErrorResponse?.let { return it }
    return Response.success(SupervisorEventEnvelopeDto(success = true, message = "OK", data = sampleEventDto(eventId)))
  }

  override suspend fun addGathering(eventId: String, request: AddGatheringRequest): Response<GatheringEnvelopeDto> {
    addGatheringCallCount++
    lastAddGatheringRequest = request
    failWith?.invoke()
    gatheringErrorResponse?.let { return it }
    val gathering = GatheringDto(id = "srv-gathering-1", gatheringDate = request.gatheringDate, topicIds = request.topicIds, remarks = request.remarks)
    return Response.success(GatheringEnvelopeDto(success = true, message = "OK", data = gathering))
  }

  override suspend fun getMarks(topicId: String, gatheringId: String, sakhiId: String, markType: String): Response<MarksEnvelopeDto> =
    error("not used")

  override suspend fun saveMark(topicId: String, request: SaveMarkRequest): Response<MarksEnvelopeDto> {
    saveMarkRequests.add(request)
    failWith?.invoke()
    if (failMarksCallNumber == saveMarkRequests.size) return marksErrorResponse ?: defaultMarksFailure()
    return Response.success(MarksEnvelopeDto(success = true, message = "OK", data = MarkDto(request.sakhiId, request.markType, request.score)))
  }

  override suspend fun completeMark(topicId: String, request: CompleteMarkRequest): Response<MarksEnvelopeDto> {
    completeMarkRequests.add(request)
    failWith?.invoke()
    if (failMarksCallNumber == completeMarkRequests.size) return marksErrorResponse ?: defaultMarksFailure()
    return Response.success(MarksEnvelopeDto(success = true, message = "OK", data = MarkDto(request.sakhiId, request.markType, 0)))
  }

  private fun defaultMarksFailure() = Response.success(MarksEnvelopeDto(success = false, message = "Rejected", data = null))

  private fun sampleEventDto(eventId: String) = SupervisorEventDto(
    id = eventId,
    projectId = "loc-1",
    supervisorId = "sup-1",
    eventType = "MEETING",
    eventDate = "2026-07-22T00:00:00.000Z",
    topicsJson = "{}",
    remarks = null,
    status = "SCHEDULED",
    photoMediaId = null,
    createdAt = "2026-01-01T00:00:00.000Z",
    updatedAt = "2026-01-01T00:00:00.000Z",
  )
}

private class FakeItemMasterAndTrainingApi : ItemMasterAndTrainingApi {
  var topics = listOf(
    TrainingTopicDto("real-topic-1", "TOPIC-1", "Antenatal Care Basics", "ACTIVE"),
    TrainingTopicDto("real-topic-2", "TOPIC-2", "Nutrition Counselling", "ACTIVE"),
    TrainingTopicDto("real-topic-3", "TOPIC-3", "Retired Topic", "INACTIVE"),
  )
  var errorResponse: Response<TrainingTopicsEnvelopeDto>? = null

  override suspend fun getItemMasterList(): Response<ItemMasterListEnvelopeDto> = error("not used")

  override suspend fun getTrainingTopics(): Response<TrainingTopicsEnvelopeDto> {
    errorResponse?.let { return it }
    return Response.success(TrainingTopicsEnvelopeDto(success = true, message = "OK", data = topics))
  }
}

private class FakeSupervisorEventCacheDao : SupervisorEventCacheDao {
  private val events = mutableMapOf<String, SupervisorEventCacheEntity>()

  override suspend fun upsert(event: SupervisorEventCacheEntity) {
    events[event.id] = event
  }

  override suspend fun getById(id: String): SupervisorEventCacheEntity? = events[id]

  override suspend fun getAll(): List<SupervisorEventCacheEntity> = events.values.toList()
}

private class FakePendingSupervisorEventDao : PendingSupervisorEventDao {
  val entities = mutableMapOf<String, PendingSupervisorEventEntity>()

  override suspend fun upsert(entity: PendingSupervisorEventEntity) {
    entities[entity.id] = entity
  }

  override suspend fun getById(id: String): PendingSupervisorEventEntity? = entities[id]

  override suspend fun getByRemoteId(remoteId: String): PendingSupervisorEventEntity? =
    entities.values.firstOrNull { it.remoteId == remoteId }

  override suspend fun deleteById(id: String) {
    entities.remove(id)
  }

  override suspend fun getPendingSync(): List<PendingSupervisorEventEntity> =
    entities.values.filter { it.syncStatus in setOf("PENDING", "FAILED") }.sortedBy { it.createdAtEpochMillis }

  override suspend fun getAll(): List<PendingSupervisorEventEntity> = entities.values.toList()
}

private class FakeSupervisorEventSyncScheduler : SupervisorEventSyncScheduler {
  var syncNowCallCount = 0
    private set

  override fun syncNow() {
    syncNowCallCount++
  }

  override fun ensurePeriodicSyncScheduled() = Unit
}

private class FakePendingGatheringDao : PendingGatheringDao {
  val entities = mutableMapOf<String, PendingGatheringEntity>()

  override suspend fun upsert(entity: PendingGatheringEntity) {
    entities[entity.id] = entity
  }

  override suspend fun getById(id: String): PendingGatheringEntity? = entities[id]

  override suspend fun deleteById(id: String) {
    entities.remove(id)
  }

  override suspend fun getPendingSync(): List<PendingGatheringEntity> =
    entities.values.filter { it.syncStatus in setOf("PENDING", "FAILED") }.sortedBy { it.createdAtEpochMillis }

  override suspend fun getAll(): List<PendingGatheringEntity> = entities.values.toList()
}

private class FakeGatheringSyncScheduler : GatheringSyncScheduler {
  var syncNowCallCount = 0
    private set

  override fun syncNow() {
    syncNowCallCount++
  }

  override fun ensurePeriodicSyncScheduled() = Unit
}

private class FakeConnectivityChecker(var online: Boolean = true) : ConnectivityChecker {
  override fun isOnline(): Boolean = online
}

private class FakeMediaRepository : MediaRepository {
  var failWith: (() -> Nothing)? = null
  var nextMediaId = "media-1"
  var uploadCallCount = 0
    private set
  val uploadedFilePaths = mutableListOf<String>()

  override suspend fun uploadPhoto(filePath: String, assetType: String): String {
    uploadCallCount++
    uploadedFilePaths.add(filePath)
    failWith?.invoke()
    return nextMediaId
  }
}

class MeetingTrainingRepositoryImplTest {
  private val dao = FakeSupervisorEventDao()
  private val eventsApi = FakeSupervisorEventsApi()
  private val eventCacheDao = FakeSupervisorEventCacheDao()
  private val pendingDao = FakePendingSupervisorEventDao()
  private val syncScheduler = FakeSupervisorEventSyncScheduler()
  private val connectivityChecker = FakeConnectivityChecker()
  private val projectsRepository = FakeProjectsRepository()
  private val sessionStore = SessionStore(FakeSecureKeyValueStore()).apply {
    saveSession(
      UserSession(
        username = "sup1",
        subjectId = "sup-1",
        roles = listOf("SUPERVISOR"),
        projectId = null,
        geographyUnitId = null,
        accessToken = "token",
        refreshToken = "refresh",
        accessTokenExpiresAtEpochSeconds = 0,
      ),
    )
  }
  private val syncExecutor = SupervisorEventSyncExecutor(pendingDao, eventCacheDao, eventsApi)
  private val operationsApi = FakeSupervisorEventOperationsApi()
  private val itemMasterAndTrainingApi = FakeItemMasterAndTrainingApi()
  private val mediaRepository = FakeMediaRepository()
  private val pendingGatheringDao = FakePendingGatheringDao()
  private val gatheringSyncScheduler = FakeGatheringSyncScheduler()
  private val gatheringSyncExecutor = GatheringSyncExecutor(
    pendingGatheringDao, pendingDao, dao, operationsApi, itemMasterAndTrainingApi,
  )
  private val repository = MeetingTrainingRepositoryImpl(
    dao, pendingDao, syncScheduler, syncExecutor, projectsRepository, sessionStore, connectivityChecker,
    operationsApi, itemMasterAndTrainingApi, mediaRepository,
    pendingGatheringDao, gatheringSyncScheduler, gatheringSyncExecutor,
    eventsApi, eventCacheDao,
  )

  private suspend fun scheduleSample(): String {
    val result = repository.scheduleMeeting(
      ScheduleMeetingRequest("loc-1", "Unrestricted Armman", "22 Jul 2026", "22 Jul 2026", "Testing"),
    )
    return (result as EventScheduleResult.Synced).entry.id
  }

  /** Schedules an event but leaves it permanently un-synced (no `remoteId`), by making the
   * connectivity check false only for the schedule call, restoring it after — used to test the
   * "event hasn't synced yet, all writes local-only" gate on every operation below. */
  private suspend fun scheduleUnsyncedSample(): String {
    connectivityChecker.online = false
    val result = repository.scheduleMeeting(
      ScheduleMeetingRequest("loc-1", "Unrestricted Armman", "22 Jul 2026", "22 Jul 2026", "Testing"),
    ) as EventScheduleResult.QueuedOffline
    connectivityChecker.online = true
    return result.entry.id
  }

  @Test
  fun `getProjects delegates to and returns exactly what ProjectsRepository returns`() = runTest {
    assertEquals(projectsRepository.getProjects(), repository.getProjects())
  }

  @Test
  fun `getSakhiRoster delegates to ProjectsRepository mapped to AttendanceRosterEntry`() = runTest {
    val roster = repository.getSakhiRoster("loc-1")

    assertEquals(2, roster.size)
    assertTrue(roster.any { it.sakhiId == "sakhi-1" && it.sakhiName == "Sushil" })
  }

  @Test
  fun `getSakhiRoster falls back to empty for unknown or null project`() = runTest {
    assertTrue(repository.getSakhiRoster("unknown").isEmpty())
    assertTrue(repository.getSakhiRoster(null).isEmpty())
  }

  @Test
  fun `scheduleMeeting online inserts the server event into the local supervisor-event cache`() = runTest {
    val id = scheduleSample()

    assertEquals(id, eventCacheDao.getById(id)?.id)
  }

  @Test(expected = IllegalStateException::class)
  fun `scheduleMeeting online throws when the API rejects the request`() = runTest {
    eventsApi.createErrorResponse = Response.success(
      SupervisorEventEnvelopeDto(success = false, message = "Invalid projectId", data = null),
    )

    scheduleSample()
  }

  @Test
  fun `scheduleMeeting online throwing leaves no orphaned local event or pending row`() = runTest {
    eventsApi.createErrorResponse = Response.success(
      SupervisorEventEnvelopeDto(success = false, message = "Invalid projectId", data = null),
    )

    runCatching { scheduleSample() }

    assertEquals(0, repository.getEvents(EventStatus.SCHEDULED).size)
    assertEquals(0, pendingDao.getAll().size)
  }

  @Test
  fun `scheduleMeeting offline creates the local event immediately and queues sync`() = runTest {
    connectivityChecker.online = false

    val result = repository.scheduleMeeting(
      ScheduleMeetingRequest("loc-1", "Unrestricted Armman", "22 Jul 2026", "22 Jul 2026", "Testing"),
    ) as EventScheduleResult.QueuedOffline

    val id = result.entry.id
    assertEquals(EventType.MEETING, repository.getEventDetail(id).eventType)
    assertEquals(1, pendingDao.getPendingSync().size)
    assertEquals(1, syncScheduler.syncNowCallCount)
    assertEquals(null, eventCacheDao.getById(id))
  }

  @Test
  fun `scheduling offline then syncing populates the cache without touching the local event`() = runTest {
    connectivityChecker.online = false
    val result = repository.scheduleMeeting(
      ScheduleMeetingRequest("loc-1", "Unrestricted Armman", "22 Jul 2026", "22 Jul 2026", "Testing"),
    ) as EventScheduleResult.QueuedOffline
    val id = result.entry.id
    repository.saveAttendance(id, listOf(AttendanceEntry("sakhi-1", "Sushil", present = true)))

    syncExecutor.run()

    assertEquals("SYNCED", pendingDao.getAll().first { it.id == id }.syncStatus)
    assertEquals(id, eventCacheDao.getById(id)?.id)
    assertEquals(1, repository.getEventDetail(id).attendedCount)
  }

  @Test
  fun `scheduleTraining offline creates the local event immediately with the marks flag preserved`() = runTest {
    connectivityChecker.online = false

    val result = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", true, ""),
    ) as EventScheduleResult.QueuedOffline

    val detail = repository.getEventDetail(result.entry.id)
    assertTrue(detail.prePostMarksApplicable)
    assertEquals(1, syncScheduler.syncNowCallCount)
  }

  @Test
  fun `scheduleMeeting inserts a SCHEDULED event with MEETING type`() = runTest {
    val id = scheduleSample()
    val detail = repository.getEventDetail(id)

    assertEquals(EventType.MEETING, detail.eventType)
    assertEquals("Unrestricted Armman", detail.projectName)
  }

  @Test
  fun `getEvents filters by status`() = runTest {
    val id = scheduleSample()

    assertEquals(1, repository.getEvents(EventStatus.SCHEDULED).size)
    assertTrue(repository.getEvents(EventStatus.COMPLETED).none { it.id == id })
  }

  // --- Reconciliation: server-confirmed events with no surviving local row ---

  private fun serverEvent(
    id: String = "srv-shadow-1",
    projectId: String = "loc-1",
    status: String = "SCHEDULED",
    eventType: String = "MEETING",
    supervisorId: String = "sup-1",
  ) = SupervisorEventDto(
    id = id,
    projectId = projectId,
    supervisorId = supervisorId,
    eventType = eventType,
    eventDate = "2026-08-19T00:00:00.000Z",
    topicsJson = "{}",
    remarks = "server remarks",
    status = status,
    photoMediaId = null,
    createdAt = "2026-08-18T00:00:00.000Z",
    updatedAt = "2026-08-18T00:00:00.000Z",
  )

  @Test
  fun `getEvents reconciles a server event with no local row into the Scheduled list`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent())

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(1, events.size)
    assertEquals("srv-shadow-1", events.single().id)
    assertEquals("Unrestricted Armman", events.single().projectName)
  }

  @Test
  fun `getEvents does not duplicate or overwrite an event that already exists locally`() = runTest {
    val id = scheduleSample()
    repository.saveAttendance(id, listOf(AttendanceEntry("sakhi-1", "Sushil", present = true)))
    eventsApi.serverEvents = listOf(serverEvent(id = id))

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(1, events.count { it.id == id })
    // The richer local row (with attendance) must survive untouched, not be replaced by the
    // reconciled shadow shape.
    assertEquals(1, repository.getEventDetail(id).attendedCount)
  }

  @Test
  fun `getEvents does not duplicate a self-created event once it has synced under a different server id`() = runTest {
    // scheduleSample() syncs immediately: the local row keeps its client-generated "event-..." id
    // forever, while the server assigns its own distinct "srv-event-1" id, recorded only as this
    // pending row's remoteId — reproducing the real duplicate-event scenario, not just a same-id one.
    val localId = scheduleSample()
    val remoteId = pendingDao.getById(localId)?.remoteId
    checkNotNull(remoteId) { "sample event should have synced with a remoteId" }
    eventsApi.serverEvents = listOf(serverEvent(id = remoteId))

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(1, events.size)
    assertEquals(localId, events.single().id)
  }

  @Test
  fun `a reconciled event is fully synced immediately — remote-gated actions work without further setup`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent())

    repository.getEvents(EventStatus.SCHEDULED)
    repository.cancelMeeting("srv-shadow-1")

    assertEquals(1, operationsApi.cancelEventCallCount)
    assertEquals(EventStatus.CANCELLED, repository.getEventDetail("srv-shadow-1").status)
  }

  @Test
  fun `reconciliation merges multiple server events, some already local and some not`() = runTest {
    val localId = scheduleSample()
    eventsApi.serverEvents = listOf(serverEvent(id = localId), serverEvent(id = "srv-shadow-2"))

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(setOf(localId, "srv-shadow-2"), events.map { it.id }.toSet())
  }

  @Test
  fun `reconciliation is skipped entirely when offline, without throwing`() = runTest {
    connectivityChecker.online = false
    eventsApi.serverEvents = listOf(serverEvent())

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertTrue(events.none { it.id == "srv-shadow-1" })
    assertEquals(0, eventsApi.getEventsCallCount)
  }

  @Test
  fun `a reconciled COMPLETED event shows correctly in the Completed tab, not Scheduled`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent(status = "COMPLETED"))

    val scheduled = repository.getEvents(EventStatus.SCHEDULED)
    val completed = repository.getEvents(EventStatus.COMPLETED)

    assertTrue(scheduled.none { it.id == "srv-shadow-1" })
    assertEquals(1, completed.count { it.id == "srv-shadow-1" })
  }

  @Test
  fun `getEvents when the server list call fails does not throw, returns local-only results`() = runTest {
    val id = scheduleSample()
    eventsApi.getEventsErrorResponse = Response.error(500, "{}".toResponseBody(null))

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(1, events.size)
    assertEquals(id, events.single().id)
  }

  @Test
  fun `getEventDetail reconciles an id that only exists server-side, on demand`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent())

    val detail = repository.getEventDetail("srv-shadow-1")

    assertEquals(EventStatus.SCHEDULED, detail.status)
    assertEquals(0, detail.attendedCount)
    assertTrue(detail.photoPaths.isEmpty())
    assertTrue(detail.gatherings.isEmpty())
  }

  @Test
  fun `adding a photo to a reconciled event works exactly like a normal local event`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent())
    repository.getEventDetail("srv-shadow-1")

    repository.addPhoto("srv-shadow-1", "/data/event_photos/1.jpg")

    assertEquals(listOf("/data/event_photos/1.jpg"), repository.getEventDetail("srv-shadow-1").photoPaths)
  }

  @Test
  fun `completing a reconciled event succeeds end-to-end after adding a photo and attendance`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent())
    repository.getEventDetail("srv-shadow-1")
    repository.addPhoto("srv-shadow-1", "/data/event_photos/1.jpg")
    repository.saveAttendance("srv-shadow-1", listOf(AttendanceEntry("sakhi-1", "Sushil", present = true)))

    repository.completeMeeting("srv-shadow-1")

    assertEquals(EventStatus.COMPLETED, repository.getEventDetail("srv-shadow-1").status)
    assertEquals(1, operationsApi.completeEventCallCount)
  }

  // --- Reconciliation: GET /supervisor-events is NOT scoped by caller — must filter client-side ---

  @Test
  fun `getEvents never reconciles another supervisor's event, even though the endpoint returned it`() = runTest {
    eventsApi.serverEvents = listOf(serverEvent(id = "srv-other-sup", supervisorId = "some-other-supervisor"))

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertTrue(events.none { it.id == "srv-other-sup" })
  }

  @Test
  fun `getEvents reconciles this supervisor's own event while ignoring another's in the same response`() = runTest {
    eventsApi.serverEvents = listOf(
      serverEvent(id = "srv-mine", supervisorId = "sup-1"),
      serverEvent(id = "srv-other-sup", supervisorId = "some-other-supervisor"),
    )

    val events = repository.getEvents(EventStatus.SCHEDULED)

    assertTrue(events.any { it.id == "srv-mine" })
    assertTrue(events.none { it.id == "srv-other-sup" })
  }

  @Test
  fun `a previously-reconciled other-supervisor shadow event is cleaned up on the next getEvents call`() = runTest {
    // Simulate the pre-fix state: another supervisor's event was already reconciled onto this
    // device (e.g. from a build before the supervisorId filter existed).
    eventsApi.serverEvents = listOf(serverEvent(id = "srv-stale", supervisorId = "some-other-supervisor"))
    repository.getEvents(EventStatus.SCHEDULED) // pre-fix behavior isn't reproducible directly;
    // instead seed the stale rows the same way reconciliation itself would have.
    pendingDao.entities["srv-stale"] = PendingSupervisorEventEntity(
      id = "srv-stale",
      projectId = "loc-1",
      supervisorId = "some-other-supervisor",
      eventType = "MEETING",
      eventDate = "19 Aug 2026",
      topicsJson = "{}",
      remarks = null,
      status = "SCHEDULED",
      syncStatus = "SYNCED",
      createdAtEpochMillis = 0L,
      lastAttemptAtEpochMillis = null,
      retryCount = 0,
      remoteId = "srv-stale",
      lastErrorMessage = null,
    )
    dao.insertEvent(
      SupervisorEventEntity(
        id = "srv-stale",
        projectId = "loc-1",
        projectName = "loc-1",
        eventType = "MEETING",
        startDate = "19 Aug 2026",
        endDate = "19 Aug 2026",
        remarks = "",
        status = "SCHEDULED",
        createdAt = 0L,
      ),
    )
    eventsApi.serverEvents = emptyList()

    repository.getEvents(EventStatus.SCHEDULED)

    assertEquals(null, pendingDao.getById("srv-stale"))
    assertTrue(repository.getEvents(EventStatus.SCHEDULED).none { it.id == "srv-stale" })
  }

  @Test
  fun `cleanup never removes an event the current supervisor actually created themselves`() = runTest {
    val id = scheduleSample()

    repository.getEvents(EventStatus.SCHEDULED)

    assertTrue(repository.getEvents(EventStatus.SCHEDULED).any { it.id == id })
  }

  @Test
  fun `saveAttendance replaces rather than duplicates rows on repeated saves`() = runTest {
    val id = scheduleSample()
    val roster = repository.getSakhiRoster("loc-1")
    val attendance = roster.map { AttendanceEntry(it.sakhiId, it.sakhiName, present = true) }

    repository.saveAttendance(id, attendance)
    repository.saveAttendance(id, attendance)

    assertEquals(roster.size, repository.getEventDetail(id).attendedCount)
  }

  @Test
  fun `getSavedAttendance returns each Sakhi's saved presence, not just the aggregate count`() = runTest {
    val id = scheduleSample()
    val roster = repository.getSakhiRoster("loc-1")
    val attendance = roster.mapIndexed { index, entry -> AttendanceEntry(entry.sakhiId, entry.sakhiName, present = index == 0) }

    repository.saveAttendance(id, attendance)
    val saved = repository.getSavedAttendance(id)

    assertEquals(attendance.toSet(), saved.toSet())
  }

  @Test
  fun `getSavedAttendance is empty before any attendance has been saved`() = runTest {
    val id = scheduleSample()

    assertTrue(repository.getSavedAttendance(id).isEmpty())
  }

  @Test
  fun `addPhoto then completeMeeting transitions status to COMPLETED`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")

    repository.completeMeeting(id)

    assertEquals(EventStatus.COMPLETED, repository.getEventDetail(id).status)
  }

  @Test
  fun `completeMeeting throws when no photo has been added`() = runTest {
    val id = scheduleSample()

    assertThrows(IllegalStateException::class.java) { runTest { repository.completeMeeting(id) } }
  }

  @Test
  fun `completeMeeting uploads the photo, attaches it, then calls complete, in that order`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    mediaRepository.nextMediaId = "media-42"

    repository.completeMeeting(id)

    assertEquals(1, mediaRepository.uploadCallCount)
    assertEquals("/data/event_photos/1.jpg", mediaRepository.uploadedFilePaths.single())
    assertEquals(1, operationsApi.attachPhotoCallCount)
    assertEquals("media-42", operationsApi.lastAttachPhotoRequest?.mediaId)
    assertEquals(1, operationsApi.completeEventCallCount)
  }

  @Test
  fun `completeMeeting skips re-uploading a photo that already has a remoteMediaId from a prior attempt`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    operationsApi.completeErrorResponse = Response.success(SupervisorEventEnvelopeDto(success = false, message = "Missing attendance", data = null))
    runCatching { repository.completeMeeting(id) }
    assertEquals(1, mediaRepository.uploadCallCount)

    operationsApi.completeErrorResponse = null
    repository.completeMeeting(id)

    assertEquals(1, mediaRepository.uploadCallCount)
    assertEquals(EventStatus.COMPLETED, repository.getEventDetail(id).status)
  }

  @Test
  fun `completeMeeting when upload fails never attempts attachPhoto or complete, and nothing is marked uploaded`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    mediaRepository.failWith = { error("Failed to upload photo to storage: HTTP 500") }

    val result = runCatching { repository.completeMeeting(id) }

    assertTrue(result.isFailure)
    assertEquals(0, operationsApi.attachPhotoCallCount)
    assertEquals(0, operationsApi.completeEventCallCount)
    assertEquals(EventStatus.SCHEDULED, repository.getEventDetail(id).status)
  }

  @Test
  fun `completeMeeting when attachPhoto fails never calls complete, but the upload still counts as done`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    operationsApi.attachPhotoErrorResponse = Response.success(AttachPhotoEnvelopeDto(success = false, message = "Rejected", data = null))

    val result = runCatching { repository.completeMeeting(id) }

    assertTrue(result.isFailure)
    assertEquals(1, mediaRepository.uploadCallCount)
    assertEquals(0, operationsApi.completeEventCallCount)
    assertEquals(EventStatus.SCHEDULED, repository.getEventDetail(id).status)

    // Retrying after the attach rejection is fixed reuses the already-uploaded media id.
    operationsApi.attachPhotoErrorResponse = null
    repository.completeMeeting(id)
    assertEquals(1, mediaRepository.uploadCallCount)
    assertEquals(EventStatus.COMPLETED, repository.getEventDetail(id).status)
  }

  @Test
  fun `completeMeeting when the event has not synced yet throws without attempting any remote call`() = runTest {
    val id = scheduleUnsyncedSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")

    val result = runCatching { repository.completeMeeting(id) }

    assertTrue(result.isFailure)
    assertEquals(0, mediaRepository.uploadCallCount)
    assertEquals(0, operationsApi.attachPhotoCallCount)
    assertEquals(0, operationsApi.completeEventCallCount)
  }

  @Test
  fun `completeMeeting when offline throws without attempting any remote call, even with a photo present`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    connectivityChecker.online = false

    val result = runCatching { repository.completeMeeting(id) }

    assertTrue(result.isFailure)
    assertEquals(0, mediaRepository.uploadCallCount)
    assertEquals(EventStatus.SCHEDULED, repository.getEventDetail(id).status)
  }

  @Test
  fun `completeMeeting with multiple photos uploads only one of them, not all`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/first.jpg")
    repository.addPhoto(id, "/data/event_photos/second.jpg")

    repository.completeMeeting(id)

    assertEquals(1, mediaRepository.uploadCallCount)
    assertEquals(1, operationsApi.attachPhotoCallCount)
  }

  @Test
  fun `cancelMeeting soft-cancels without deleting the event`() = runTest {
    val id = scheduleSample()

    repository.cancelMeeting(id)

    assertEquals(EventStatus.CANCELLED, repository.getEventDetail(id).status)
  }

  @Test
  fun `rescheduleMeeting updates the date range`() = runTest {
    val id = scheduleSample()

    repository.rescheduleMeeting(id, "23 Jul 2026", "24 Jul 2026")

    val detail = repository.getEventDetail(id)
    assertEquals("23 Jul 2026", detail.startDate)
    assertEquals("24 Jul 2026", detail.endDate)
  }

  @Test
  fun `rescheduleMeeting on a completed event throws`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    repository.completeMeeting(id)

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.rescheduleMeeting(id, "25 Jul 2026", "25 Jul 2026") }
    }
  }

  @Test
  fun `getEventDetail for unknown id throws a clear error`() = runTest {
    assertThrows(IllegalStateException::class.java) { runTest { repository.getEventDetail("unknown-id") } }
  }

  // --- Training / Gatherings ---

  private suspend fun scheduleTrainingSample(prePostMarksApplicable: Boolean = false): String {
    val result = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", prePostMarksApplicable, ""),
    )
    return (result as EventScheduleResult.Synced).entry.id
  }

  @Test
  fun `scheduleTraining inserts a SCHEDULED event with TRAINING type and marks flag`() = runTest {
    val id = scheduleTrainingSample(prePostMarksApplicable = true)
    val detail = repository.getEventDetail(id)

    assertEquals(EventType.TRAINING, detail.eventType)
    assertTrue(detail.prePostMarksApplicable)
  }

  @Test
  fun `getTrainingTopicsCatalog returns topics from the real training-topics endpoint`() = runTest {
    val catalog = repository.getTrainingTopicsCatalog()

    assertEquals(2, catalog.size)
    assertTrue(catalog.any { it.id == "real-topic-1" && it.name == "Antenatal Care Basics" })
  }

  @Test
  fun `getTrainingTopicsCatalog filters out INACTIVE topics`() = runTest {
    val catalog = repository.getTrainingTopicsCatalog()

    assertTrue(catalog.none { it.name == "Retired Topic" })
  }

  @Test
  fun `getTrainingTopicsCatalog surfaces a remote failure as an error`() = runTest {
    itemMasterAndTrainingApi.errorResponse = Response.success(
      TrainingTopicsEnvelopeDto(success = false, message = "Server error", data = null),
    )

    assertThrows(IllegalStateException::class.java) { runTest { repository.getTrainingTopicsCatalog() } }
  }

  @Test
  fun `addGathering creates a gathering with its topics`() = runTest {
    val id = scheduleTrainingSample()
    val topicNames = repository.getTrainingTopicsCatalog().take(2).map { it.name }

    val gatheringId = repository.addGathering(id, topicNames, "27 Jul 2026")

    val detail = repository.getEventDetail(id)
    assertEquals(1, detail.gatherings.size)
    assertEquals(gatheringId, detail.gatherings.single().gatheringId)
    assertEquals(topicNames, detail.gatherings.single().topics.map { it.topicName })
  }

  @Test
  fun `addGathering called twice creates two independent gatherings`() = runTest {
    val id = scheduleTrainingSample()
    val catalog = repository.getTrainingTopicsCatalog()

    val first = repository.addGathering(id, listOf(catalog[0].name), "27 Jul 2026")
    val second = repository.addGathering(id, listOf(catalog[1].name), "28 Jul 2026")

    val gatherings = repository.getEventDetail(id).gatherings
    assertEquals(2, gatherings.size)
    assertEquals(setOf(first, second), gatherings.map { it.gatheringId }.toSet())
  }

  @Test
  fun `saveGatheringAttendance is scoped to the gathering and does not affect Meeting attendance`() = runTest {
    val trainingId = scheduleTrainingSample()
    val gatheringId = repository.addGathering(trainingId, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val roster = repository.getSakhiRoster("loc-1")

    repository.saveGatheringAttendance(trainingId, gatheringId, roster.map { AttendanceEntry(it.sakhiId, it.sakhiName, true) })

    val gathering = repository.getEventDetail(trainingId).gatherings.single()
    assertEquals(roster.size, gathering.attendedCount)

    val meetingId = scheduleSample()
    assertEquals(0, repository.getEventDetail(meetingId).attendedCount)
  }

  @Test
  fun `getTopicsForGathering only returns topics belonging to that gathering`() = runTest {
    val id = scheduleTrainingSample()
    val catalog = repository.getTrainingTopicsCatalog()
    val first = repository.addGathering(id, listOf(catalog[0].name), "27 Jul 2026")
    repository.addGathering(id, listOf(catalog[1].name), "28 Jul 2026")

    val topics = repository.getTopicsForGathering(first)

    assertEquals(listOf(catalog[0].name), topics.map { it.name })
  }

  @Test
  fun `saveMarks then completeMarks locks that topic marksType only`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    val entries = roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) }

    repository.saveMarks(id, topicId, MarksType.PRE, entries)
    repository.completeMarks(id, topicId, MarksType.PRE)

    val topicStatus = repository.getEventDetail(id).gatherings.single().topics.single()
    assertTrue(topicStatus.preMarksCompleted)
    assertTrue(!topicStatus.postMarksCompleted)
  }

  @Test
  fun `saveMarks with a mark above 100 throws`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val entries = repository.getSakhiRoster("loc-1").map { MarksEntry(it.sakhiId, it.sakhiName, marks = 954) }

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.saveMarks(id, topicId, MarksType.PRE, entries) }
    }
  }

  @Test
  fun `saveMarks on an already-completed topic marksType throws`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val entries = repository.getSakhiRoster("loc-1").map { MarksEntry(it.sakhiId, it.sakhiName, marks = 10) }
    repository.saveMarks(id, topicId, MarksType.PRE, entries)
    repository.completeMarks(id, topicId, MarksType.PRE)

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.saveMarks(id, topicId, MarksType.PRE, entries) }
    }
  }

  @Test
  fun `completeMarks on an already-completed topic marksType throws`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    repository.completeMarks(id, topicId, MarksType.PRE)

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.completeMarks(id, topicId, MarksType.PRE) }
    }
  }

  @Test
  fun `getMarks returns saved entries for the requested marksType only`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    repository.saveMarks(id, topicId, MarksType.PRE, roster.map { MarksEntry(it.sakhiId, it.sakhiName, 10) })
    repository.saveMarks(id, topicId, MarksType.POST, roster.map { MarksEntry(it.sakhiId, it.sakhiName, 20) })

    val pre = repository.getMarks(topicId, MarksType.PRE)
    val post = repository.getMarks(topicId, MarksType.POST)

    assertTrue(pre.all { it.marks == 10 })
    assertTrue(post.all { it.marks == 20 })
  }

  @Test
  fun `addPhoto then completeMeeting on a Training transitions status to COMPLETED`() = runTest {
    val id = scheduleTrainingSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")

    repository.completeMeeting(id)

    assertEquals(EventStatus.COMPLETED, repository.getEventDetail(id).status)
  }

  // --- Remote wiring: sync-gating (Decision 1) ---

  @Test
  fun `cancelMeeting when the parent event has not synced yet only writes locally, never calls the remote API`() = runTest {
    val id = scheduleUnsyncedSample()

    repository.cancelMeeting(id)

    assertEquals(EventStatus.CANCELLED, repository.getEventDetail(id).status)
    assertEquals(0, operationsApi.cancelEventCallCount)
  }

  @Test
  fun `cancelMeeting when the parent event is synced and online calls the remote endpoint`() = runTest {
    val id = scheduleSample()

    repository.cancelMeeting(id)

    assertEquals(EventStatus.CANCELLED, repository.getEventDetail(id).status)
    assertEquals(1, operationsApi.cancelEventCallCount)
  }

  @Test
  fun `cancelMeeting when the remote call is rejected throws and does not apply the local write`() = runTest {
    val id = scheduleSample()
    operationsApi.cancelErrorResponse = Response.success(SupervisorEventEnvelopeDto(success = false, message = "Rejected", data = null))

    assertThrows(IllegalStateException::class.java) { runTest { repository.cancelMeeting(id) } }
    assertEquals(EventStatus.SCHEDULED, repository.getEventDetail(id).status)
  }

  @Test
  fun `cancelMeeting when synced but the remote call throws IOException falls back to local-only`() = runTest {
    val id = scheduleSample()
    operationsApi.failWith = { throw IOException("connection dropped") }

    repository.cancelMeeting(id)

    assertEquals(EventStatus.CANCELLED, repository.getEventDetail(id).status)
  }

  // --- Remote wiring: attendance ---

  @Test
  fun `saveAttendance when synced and online sends the roster to the real endpoint`() = runTest {
    val id = scheduleSample()
    val roster = repository.getSakhiRoster("loc-1")
    val attendance = roster.mapIndexed { index, entry -> AttendanceEntry(entry.sakhiId, entry.sakhiName, present = index == 0) }

    repository.saveAttendance(id, attendance)

    assertEquals(1, operationsApi.saveAttendanceCallCount)
    val sent = operationsApi.lastSaveAttendanceRequest!!.attendance
    assertEquals(attendance.size, sent.size)
    assertTrue(sent.any { it.sakhiId == roster[0].sakhiId && it.attendanceStatus == "PRESENT" })
    assertTrue(sent.any { it.attendanceStatus == "ABSENT" })
  }

  @Test
  fun `saveGatheringAttendance sends the same flat per-event attendance shape as saveAttendance`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val roster = repository.getSakhiRoster("loc-1")

    repository.saveGatheringAttendance(id, gatheringId, roster.map { AttendanceEntry(it.sakhiId, it.sakhiName, true) })

    assertEquals(1, operationsApi.saveAttendanceCallCount)
  }

  @Test
  fun `saveAttendance when remote save fails throws but the local roster still reflects the save`() = runTest {
    val id = scheduleSample()
    val roster = repository.getSakhiRoster("loc-1")
    val attendance = roster.map { AttendanceEntry(it.sakhiId, it.sakhiName, present = true) }
    operationsApi.attendanceErrorResponse = Response.success(AttendanceEnvelopeDto(success = false, message = "Rejected", data = null))

    val thrown = runCatching { repository.saveAttendance(id, attendance) }.exceptionOrNull()

    assertTrue(thrown is IllegalStateException)
    assertEquals(attendance.toSet(), repository.getSavedAttendance(id).toSet())
  }

  @Test
  fun `saveAttendance when the event has not synced yet only saves locally`() = runTest {
    val id = scheduleUnsyncedSample()
    val roster = repository.getSakhiRoster("loc-1")
    val attendance = roster.map { AttendanceEntry(it.sakhiId, it.sakhiName, present = true) }

    repository.saveAttendance(id, attendance)

    assertEquals(attendance.toSet(), repository.getSavedAttendance(id).toSet())
    assertEquals(0, operationsApi.saveAttendanceCallCount)
  }

  // --- Remote wiring: reschedule ---

  @Test
  fun `rescheduleMeeting when synced and online sends synthesized remarks and the new date`() = runTest {
    val id = scheduleSample()

    repository.rescheduleMeeting(id, "23 Jul 2026", "24 Jul 2026")

    val request = operationsApi.lastRescheduleRequest!!
    assertEquals("23 Jul 2026", request.eventDate)
    assertTrue(request.remarks.length >= 3)
  }

  @Test
  fun `rescheduleMeeting on a completed event throws before attempting any remote call`() = runTest {
    val id = scheduleSample()
    repository.addPhoto(id, "/data/event_photos/1.jpg")
    repository.completeMeeting(id)

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.rescheduleMeeting(id, "25 Jul 2026", "25 Jul 2026") }
    }
    assertEquals(0, operationsApi.rescheduleEventCallCount)
  }

  @Test
  fun `rescheduleMeeting when remote rejects throws and local dates are not updated`() = runTest {
    val id = scheduleSample()
    operationsApi.rescheduleErrorResponse = Response.success(SupervisorEventEnvelopeDto(success = false, message = "Rejected", data = null))

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.rescheduleMeeting(id, "23 Jul 2026", "24 Jul 2026") }
    }

    val detail = repository.getEventDetail(id)
    assertEquals("22 Jul 2026", detail.startDate)
    assertEquals("22 Jul 2026", detail.endDate)
  }

  // --- Remote wiring: gatherings (topic name -> id resolution) ---

  @Test
  fun `addGathering resolves topic names to real catalog ids before calling the remote endpoint`() = runTest {
    val id = scheduleTrainingSample()
    val catalog = repository.getTrainingTopicsCatalog()

    repository.addGathering(id, listOf(catalog[0].name), "27 Jul 2026")

    assertEquals(listOf(catalog[0].id), operationsApi.lastAddGatheringRequest!!.topicIds)
  }

  @Test
  fun `addGathering with a topic name not in the current catalog throws locally before any remote call`() = runTest {
    val id = scheduleTrainingSample()

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.addGathering(id, listOf("Not A Real Topic"), "27 Jul 2026") }
    }
    assertEquals(0, operationsApi.addGatheringCallCount)
  }

  @Test
  fun `addGathering when the event has not synced yet only creates the gathering locally`() = runTest {
    connectivityChecker.online = false
    val trainingResult = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", false, ""),
    ) as EventScheduleResult.QueuedOffline
    connectivityChecker.online = true
    val id = trainingResult.entry.id

    repository.addGathering(id, listOf("Any Topic Name"), "27 Jul 2026")

    assertEquals(1, repository.getEventDetail(id).gatherings.size)
    assertEquals(0, operationsApi.addGatheringCallCount)
  }

  @Test
  fun `addGathering when remote rejects throws and no local gathering row is left behind`() = runTest {
    val id = scheduleTrainingSample()
    val catalog = repository.getTrainingTopicsCatalog()
    operationsApi.gatheringErrorResponse = Response.success(GatheringEnvelopeDto(success = false, message = "Rejected", data = null))

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.addGathering(id, listOf(catalog[0].name), "27 Jul 2026") }
    }

    assertEquals(0, repository.getEventDetail(id).gatherings.size)
  }

  // --- Regression: real server ids reconciled onto local rows (the "topicId: Invalid uuid" fix) ---

  @Test
  fun `addGathering online persists the real server gathering id onto the local row, not the placeholder`() = runTest {
    val id = scheduleTrainingSample()
    val topicName = itemMasterAndTrainingApi.topics[0].topicName

    val gatheringId = repository.addGathering(id, listOf(topicName), "27 Jul 2026")

    val ids = dao.getTopicAndGatheringRemoteIds(repository.getTopicsForGathering(gatheringId).single().id)
    assertEquals("srv-gathering-1", ids?.gatheringRemoteId)
    // The local id remains the row's own identity everywhere else (attendance/marks key off it).
    assertTrue(gatheringId.startsWith("gathering-"))
  }

  @Test
  fun `addGathering online persists the real catalog topic id onto the matching local topic row`() = runTest {
    val id = scheduleTrainingSample()
    val topicName = itemMasterAndTrainingApi.topics[0].topicName

    val gatheringId = repository.addGathering(id, listOf(topicName), "27 Jul 2026")

    val localTopicId = repository.getTopicsForGathering(gatheringId).single().id
    val ids = dao.getTopicAndGatheringRemoteIds(localTopicId)
    assertEquals(itemMasterAndTrainingApi.topics[0].id, ids?.topicRemoteId)
    assertTrue(localTopicId.startsWith("topic-"))
  }

  @Test
  fun `addGathering online with multiple topics maps each real id to the correct topic by name, not by index`() = runTest {
    val id = scheduleTrainingSample()
    val names = listOf(itemMasterAndTrainingApi.topics[1].topicName, itemMasterAndTrainingApi.topics[0].topicName)

    val gatheringId = repository.addGathering(id, names, "27 Jul 2026")

    val topics = repository.getTopicsForGathering(gatheringId)
    val firstAdded = topics.first { it.name == itemMasterAndTrainingApi.topics[1].topicName }
    val secondAdded = topics.first { it.name == itemMasterAndTrainingApi.topics[0].topicName }
    assertEquals(itemMasterAndTrainingApi.topics[1].id, dao.getTopicAndGatheringRemoteIds(firstAdded.id)?.topicRemoteId)
    assertEquals(itemMasterAndTrainingApi.topics[0].id, dao.getTopicAndGatheringRemoteIds(secondAdded.id)?.topicRemoteId)
  }

  @Test
  fun `saveMarks skips the remote call entirely when the gathering has not resolved a real id yet`() = runTest {
    connectivityChecker.online = false
    val trainingResult = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", false, ""),
    ) as EventScheduleResult.QueuedOffline
    connectivityChecker.online = true
    val id = trainingResult.entry.id
    val gatheringId = repository.addGathering(id, listOf("Any Topic Name"), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id

    repository.saveMarks(id, topicId, MarksType.PRE, listOf(MarksEntry("sakhi-1", "Sushil", marks = 45)))

    assertEquals(0, operationsApi.saveMarkRequests.size)
    // Local save still succeeds — write-through is preserved even when the remote push is skipped.
    assertEquals(1, repository.getMarks(topicId, MarksType.PRE).size)
  }

  @Test
  fun `completeMarks skips the remote call entirely when the topic has not resolved a real id yet`() = runTest {
    connectivityChecker.online = false
    val trainingResult = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", false, ""),
    ) as EventScheduleResult.QueuedOffline
    connectivityChecker.online = true
    val id = trainingResult.entry.id
    val gatheringId = repository.addGathering(id, listOf("Any Topic Name"), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    repository.saveMarks(id, topicId, MarksType.PRE, listOf(MarksEntry("sakhi-1", "Sushil", marks = 45)))

    repository.completeMarks(id, topicId, MarksType.PRE)

    assertEquals(0, operationsApi.completeMarkRequests.size)
    assertEquals(1, dao.getMarksCompletionForTopic(topicId).size)
  }

  /** Schedules a Training online, then forces a connectivity-drop [IOException] on the very next
   * [operationsApi] call so [MeetingTrainingRepositoryImpl.addGathering]'s online attempt fails
   * mid-request and falls into its offline/queued branch — the only way a gathering ends up
   * genuinely queued in [pendingGatheringDao] even though its parent event is already synced,
   * which the [GatheringSyncExecutor] tests below need to exercise a real pending row. */
  private suspend fun scheduleTrainingWithQueuedGathering(topicName: String): Pair<String, String> {
    val id = scheduleTrainingSample()
    operationsApi.failWith = { throw IOException("network down") }
    val gatheringId = repository.addGathering(id, listOf(topicName), "27 Jul 2026")
    operationsApi.failWith = null
    return id to gatheringId
  }

  @Test
  fun `saveMarks resumes remote push once the gathering resolves a real id after GatheringSyncExecutor runs`() = runTest {
    val (id, gatheringId) = scheduleTrainingWithQueuedGathering(itemMasterAndTrainingApi.topics[0].topicName)
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    repository.saveMarks(id, topicId, MarksType.PRE, listOf(MarksEntry("sakhi-1", "Sushil", marks = 45)))
    assertEquals(0, operationsApi.saveMarkRequests.size)

    gatheringSyncExecutor.runOne(gatheringId)
    repository.saveMarks(id, topicId, MarksType.PRE, listOf(MarksEntry("sakhi-1", "Sushil", marks = 50)))

    assertEquals(1, operationsApi.saveMarkRequests.size)
    assertEquals("srv-gathering-1", operationsApi.saveMarkRequests.single().gatheringId)
  }

  // --- Pending gathering queue: GatheringSyncExecutor ---

  @Test
  fun `addGathering when the online attempt drops mid-request queues a PendingGatheringEntity`() = runTest {
    val (id, gatheringId) = scheduleTrainingWithQueuedGathering(itemMasterAndTrainingApi.topics[0].topicName)

    val pending = pendingGatheringDao.getById(gatheringId)
    assertEquals(id, pending?.eventId)
    assertEquals("PENDING", pending?.syncStatus)
  }

  @Test
  fun `GatheringSyncExecutor stays AwaitingParentEvent when the parent event has not synced yet`() = runTest {
    connectivityChecker.online = false
    val trainingResult = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", false, ""),
    ) as EventScheduleResult.QueuedOffline
    val id = trainingResult.entry.id
    val gatheringId = repository.addGathering(id, listOf("Any Topic Name"), "27 Jul 2026")
    connectivityChecker.online = true

    val result = gatheringSyncExecutor.runOne(gatheringId)

    assertTrue(result is GatheringSyncItemResult.AwaitingParentEvent)
    assertEquals("PENDING", pendingGatheringDao.getById(gatheringId)?.syncStatus)
    assertEquals(0, operationsApi.addGatheringCallCount)
  }

  @Test
  fun `GatheringSyncExecutor syncing successfully resolves both gathering and topic remote ids, then dequeues`() = runTest {
    val topicName = itemMasterAndTrainingApi.topics[0].topicName
    val (_, gatheringId) = scheduleTrainingWithQueuedGathering(topicName)

    val result = gatheringSyncExecutor.runOne(gatheringId)

    assertTrue(result is GatheringSyncItemResult.Synced)
    assertEquals(null, pendingGatheringDao.getById(gatheringId))
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val ids = dao.getTopicAndGatheringRemoteIds(topicId)
    assertEquals("srv-gathering-1", ids?.gatheringRemoteId)
    assertEquals(itemMasterAndTrainingApi.topics[0].id, ids?.topicRemoteId)
  }

  @Test
  fun `GatheringSyncExecutor marks the row FAILED on a hard server rejection`() = runTest {
    val (_, gatheringId) = scheduleTrainingWithQueuedGathering(itemMasterAndTrainingApi.topics[0].topicName)
    operationsApi.gatheringErrorResponse = Response.success(GatheringEnvelopeDto(success = false, message = "Rejected", data = null))

    val result = gatheringSyncExecutor.runOne(gatheringId)

    assertTrue(result is GatheringSyncItemResult.Failed)
    assertEquals("FAILED", pendingGatheringDao.getById(gatheringId)?.syncStatus)
  }

  @Test
  fun `GatheringSyncExecutor returns Retryable on a connectivity drop, row stays queued`() = runTest {
    val (_, gatheringId) = scheduleTrainingWithQueuedGathering(itemMasterAndTrainingApi.topics[0].topicName)
    operationsApi.failWith = { throw IOException("network down") }

    val result = gatheringSyncExecutor.runOne(gatheringId)

    assertTrue(result is GatheringSyncItemResult.Retryable)
    assertEquals("PENDING", pendingGatheringDao.getById(gatheringId)?.syncStatus)
  }

  @Test
  fun `GatheringSyncExecutor runOne on an already-synced (dequeued) row never calls the remote API again`() = runTest {
    val (_, gatheringId) = scheduleTrainingWithQueuedGathering(itemMasterAndTrainingApi.topics[0].topicName)
    gatheringSyncExecutor.runOne(gatheringId)
    val callCountAfterFirstSync = operationsApi.addGatheringCallCount

    // A synced row is deleted from the pending table (see GatheringSyncExecutor.syncRow), so a
    // second runOne for the same id reports "unknown" rather than re-attempting the API call.
    val result = gatheringSyncExecutor.runOne(gatheringId)

    assertTrue(result is GatheringSyncItemResult.Failed)
    assertEquals(callCountAfterFirstSync, operationsApi.addGatheringCallCount)
  }

  // --- Remote wiring: marks (bulk local -> per-sakhi remote fan-out) ---

  @Test
  fun `saveMarks fans out one remote call per non-null roster entry, using the real server ids`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    val entries = roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) }

    repository.saveMarks(id, topicId, MarksType.PRE, entries)

    assertEquals(roster.size, operationsApi.saveMarkRequests.size)
    // gatheringId in the request must be the real server id resolved by addGathering, never the
    // local placeholder — sending the local one is exactly the "topicId: Invalid uuid" bug this
    // regression test guards against.
    assertTrue(operationsApi.saveMarkRequests.all { it.gatheringId == "srv-gathering-1" && it.markType == "PRE" && it.score == 45 })
  }

  @Test
  fun `saveMarks skips roster entries with a null mark when fanning out remotely`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    val entries = roster.mapIndexed { index, entry -> MarksEntry(entry.sakhiId, entry.sakhiName, marks = if (index == 0) null else 50) }

    repository.saveMarks(id, topicId, MarksType.PRE, entries)

    assertEquals(roster.size - 1, operationsApi.saveMarkRequests.size)
  }

  @Test
  fun `saveMarks when one remote call in the fan-out is rejected stops immediately, local write still stands`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    check(roster.size >= 2) { "test needs at least 2 roster entries" }
    val entries = roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) }
    operationsApi.failMarksCallNumber = 2

    val thrown = runCatching { repository.saveMarks(id, topicId, MarksType.PRE, entries) }.exceptionOrNull()

    assertTrue(thrown is IllegalStateException)
    assertEquals(2, operationsApi.saveMarkRequests.size)
    assertEquals(roster.size, repository.getMarks(topicId, MarksType.PRE).size)
  }

  @Test
  fun `completeMarks fans out one remote completeMark call per roster sakhi, using the real server ids`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    repository.saveMarks(id, topicId, MarksType.PRE, roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) })
    operationsApi.saveMarkRequests.clear()

    repository.completeMarks(id, topicId, MarksType.PRE)

    assertEquals(roster.size, operationsApi.completeMarkRequests.size)
    assertTrue(operationsApi.completeMarkRequests.all { it.gatheringId == "srv-gathering-1" && it.markType == "PRE" })
  }

  @Test
  fun `completeMarks when a remote completion call is rejected stops the fan-out, but the local topic stays completed`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf(itemMasterAndTrainingApi.topics[0].topicName), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")
    repository.saveMarks(id, topicId, MarksType.PRE, roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) })
    operationsApi.failMarksCallNumber = 1

    val thrown = runCatching { repository.completeMarks(id, topicId, MarksType.PRE) }.exceptionOrNull()

    assertTrue(thrown is IllegalStateException)
    val topicStatus = repository.getEventDetail(id).gatherings.single().topics.single()
    assertTrue(topicStatus.preMarksCompleted)
  }

  @Test
  fun `saveMarks and completeMarks when the event has not synced yet only apply locally`() = runTest {
    connectivityChecker.online = false
    val trainingResult = repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", false, ""),
    ) as EventScheduleResult.QueuedOffline
    connectivityChecker.online = true
    val id = trainingResult.entry.id
    val gatheringId = repository.addGathering(id, listOf("Any Topic Name"), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val roster = repository.getSakhiRoster("loc-1")

    repository.saveMarks(id, topicId, MarksType.PRE, roster.map { MarksEntry(it.sakhiId, it.sakhiName, marks = 45) })
    repository.completeMarks(id, topicId, MarksType.PRE)

    assertEquals(0, operationsApi.saveMarkRequests.size)
    assertEquals(0, operationsApi.completeMarkRequests.size)
    assertTrue(repository.getEventDetail(id).gatherings.single().topics.single().preMarksCompleted)
  }
}
