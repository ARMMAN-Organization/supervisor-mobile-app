package org.armman.supervisor.data.meetingtraining

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.local.EventAttendanceEntity
import org.armman.supervisor.data.local.EventPhotoEntity
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.SupervisorEventDao
import org.armman.supervisor.data.local.SupervisorEventEntity
import org.armman.supervisor.data.local.SupervisorEventWithDetails
import org.armman.supervisor.ui.meetingtraining.AttendanceEntry
import org.armman.supervisor.ui.meetingtraining.EventType
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-written in-memory fake — see AssignItemRepositoryImplTest for why (Room needs a Context). */
private class FakeSupervisorEventDao : SupervisorEventDao {
  private val events = mutableMapOf<String, SupervisorEventEntity>()
  private val attendanceByEvent = mutableMapOf<String, MutableList<EventAttendanceEntity>>()
  private val photosByEvent = mutableMapOf<String, MutableList<EventPhotoEntity>>()

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

  override suspend fun deleteAttendanceForEvent(eventId: String) {
    attendanceByEvent.remove(eventId)
  }

  override suspend fun insertAttendance(rows: List<EventAttendanceEntity>) {
    rows.forEach { attendanceByEvent.getOrPut(it.eventId) { mutableListOf() }.add(it) }
  }

  override suspend fun insertPhoto(photo: EventPhotoEntity) {
    photosByEvent.getOrPut(photo.eventId) { mutableListOf() }.add(photo)
  }

  override suspend fun getAllPhotoFilePaths(): List<String> =
    photosByEvent.values.flatten().map { it.filePath }

  override suspend fun deleteEvent(entity: SupervisorEventEntity) {
    events.remove(entity.id)
    attendanceByEvent.remove(entity.id)
    photosByEvent.remove(entity.id)
  }

  private fun SupervisorEventEntity.toDetails() =
    SupervisorEventWithDetails(this, attendanceByEvent[id].orEmpty(), photosByEvent[id].orEmpty())
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
}
