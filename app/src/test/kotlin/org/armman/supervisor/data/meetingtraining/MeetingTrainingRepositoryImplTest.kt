package org.armman.supervisor.data.meetingtraining

import kotlinx.coroutines.test.runTest
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
import org.armman.supervisor.ui.meetingtraining.AttendanceEntry
import org.armman.supervisor.ui.meetingtraining.EventType
import org.armman.supervisor.ui.meetingtraining.MarksEntry
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingRequest
import org.armman.supervisor.ui.meetingtraining.ScheduleTrainingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-written in-memory fake — see AssignItemRepositoryImplTest for why (Room needs a Context). */
private class FakeSupervisorEventDao : SupervisorEventDao {
  private val events = mutableMapOf<String, SupervisorEventEntity>()
  private val attendanceByEvent = mutableMapOf<String, MutableList<EventAttendanceEntity>>()
  private val photosByEvent = mutableMapOf<String, MutableList<EventPhotoEntity>>()
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
    photosByEvent.getOrPut(photo.eventId) { mutableListOf() }.add(photo)
  }

  override suspend fun insertGathering(gathering: EventGatheringEntity) {
    gatheringsByEvent.getOrPut(gathering.eventId) { mutableListOf() }.add(gathering)
  }

  override suspend fun insertTopics(topics: List<EventTopicEntity>) {
    topics.forEach { topicsByGathering.getOrPut(it.gatheringId) { mutableListOf() }.add(it) }
  }

  override suspend fun getGatheringWithTopics(gatheringId: String): GatheringWithTopics? {
    val gathering = gatheringsByEvent.values.flatten().find { it.id == gatheringId } ?: return null
    return GatheringWithTopics(gathering, topicsByGathering[gatheringId].orEmpty())
  }

  override suspend fun getAttendanceForGathering(gatheringId: String): List<EventAttendanceEntity> =
    attendanceByGathering[gatheringId].orEmpty()

  override suspend fun getTopicIdsForGathering(gatheringId: String): List<String> =
    topicsByGathering[gatheringId].orEmpty().map { it.id }

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

class MeetingTrainingRepositoryImplTest {
  private val dao = FakeSupervisorEventDao()
  private val repository = MeetingTrainingRepositoryImpl(dao)

  private suspend fun scheduleSample(): String =
    repository.scheduleMeeting(
      ScheduleMeetingRequest("loc-1", "Unrestricted Armman", "22 Jul 2026", "22 Jul 2026", "Testing"),
    ).id

  @Test
  fun `getProjects returns the expected stub list`() = runTest {
    assertEquals(2, repository.getProjects().size)
  }

  @Test
  fun `getSakhiRoster falls back to empty for unknown or null project`() = runTest {
    assertTrue(repository.getSakhiRoster("unknown").isEmpty())
    assertTrue(repository.getSakhiRoster(null).isEmpty())
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

  private suspend fun scheduleTrainingSample(prePostMarksApplicable: Boolean = false): String =
    repository.scheduleTraining(
      ScheduleTrainingRequest("loc-1", "Unrestricted Armman", "27 Jul 2026", "27 Jul 2026", prePostMarksApplicable, ""),
    ).id

  @Test
  fun `scheduleTraining inserts a SCHEDULED event with TRAINING type and marks flag`() = runTest {
    val id = scheduleTrainingSample(prePostMarksApplicable = true)
    val detail = repository.getEventDetail(id)

    assertEquals(EventType.TRAINING, detail.eventType)
    assertTrue(detail.prePostMarksApplicable)
  }

  @Test
  fun `getTrainingTopicsCatalog returns a non-empty stub catalog`() = runTest {
    assertTrue(repository.getTrainingTopicsCatalog().isNotEmpty())
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
    val gatheringId = repository.addGathering(trainingId, listOf("Topic A"), "27 Jul 2026")
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
    val gatheringId = repository.addGathering(id, listOf("Topic A"), "27 Jul 2026")
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
    val gatheringId = repository.addGathering(id, listOf("Topic A"), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    val entries = repository.getSakhiRoster("loc-1").map { MarksEntry(it.sakhiId, it.sakhiName, marks = 954) }

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.saveMarks(id, topicId, MarksType.PRE, entries) }
    }
  }

  @Test
  fun `saveMarks on an already-completed topic marksType throws`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf("Topic A"), "27 Jul 2026")
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
    val gatheringId = repository.addGathering(id, listOf("Topic A"), "27 Jul 2026")
    val topicId = repository.getTopicsForGathering(gatheringId).single().id
    repository.completeMarks(id, topicId, MarksType.PRE)

    assertThrows(IllegalStateException::class.java) {
      runTest { repository.completeMarks(id, topicId, MarksType.PRE) }
    }
  }

  @Test
  fun `getMarks returns saved entries for the requested marksType only`() = runTest {
    val id = scheduleTrainingSample()
    val gatheringId = repository.addGathering(id, listOf("Topic A"), "27 Jul 2026")
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
}
