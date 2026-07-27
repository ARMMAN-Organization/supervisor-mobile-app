package org.armman.supervisor.ui.meetingtraining

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeetingTrainingViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class TestRepository(
    private val projects: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A")),
    private val scheduledEvents: List<MeetingEntry> = listOf(
      MeetingEntry("event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "Testing", 1L),
    ),
    private val completedEvents: List<MeetingEntry> = emptyList(),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> {
      if (shouldFail) error("projects failed")
      return projects
    }

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = error("not used")

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> {
      if (shouldFail) error("events failed")
      return if (status == EventStatus.SCHEDULED) scheduledEvents else completedEvents
    }

    override suspend fun getEventDetail(eventId: String): MeetingDetail = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = emptyList()
  }

  private class FakePhotoCleanup : EventPhotoCleanup {
    var deleteCallCount = 0
      private set

    override suspend fun deleteUnreferenced(referencedFilePaths: Set<String>) {
      deleteCallCount++
    }
  }

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = MeetingTrainingViewModel(TestRepository(), FakePhotoCleanup())
    assertEquals(MeetingTrainingUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success on the SCHEDULED tab with both event types selected`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(TestRepository(), FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertEquals(MeetingTrainingTab.SCHEDULED, state.tab)
    assertEquals(setOf(EventType.MEETING, EventType.TRAINING), state.selectedEventTypes)
    assertEquals(1, state.events.size)
  }

  @Test
  fun `orphaned photo cleanup runs once on init`() = runTest(dispatcher) {
    val photoCleanup = FakePhotoCleanup()
    MeetingTrainingViewModel(TestRepository(), photoCleanup)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, photoCleanup.deleteCallCount)
  }

  @Test
  fun `switching to COMPLETED tab reloads to the completed list`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(
      TestRepository(completedEvents = listOf(MeetingEntry("event-2", EventType.MEETING, "Zone A", "1 Jan 2026", "1 Jan 2026", "", 2L))),
      FakePhotoCleanup(),
    )
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTabSelected(MeetingTrainingTab.COMPLETED)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertEquals(MeetingTrainingTab.COMPLETED, state.tab)
    assertEquals("event-2", state.events.single().id)
  }

  @Test
  fun `toggling off Meeting chip filters meeting events out`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(TestRepository(), FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onEventTypeToggled(EventType.MEETING)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertEquals(setOf(EventType.TRAINING), state.selectedEventTypes)
    assertTrue(state.events.isEmpty())
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = MeetingTrainingViewModel(repo, FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is MeetingTrainingUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MeetingTrainingUiState.Success)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(TestRepository(shouldFail = true), FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MeetingTrainingUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `zero events for the current filter combination is an empty Success, not an error`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(TestRepository(scheduledEvents = emptyList()), FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertTrue(state.events.isEmpty())
  }

  @Test
  fun `photo cleanup failure does not affect the events list`() = runTest(dispatcher) {
    val failingCleanup = object : EventPhotoCleanup {
      override suspend fun deleteUnreferenced(referencedFilePaths: Set<String>) {
        error("disk error")
      }
    }
    val viewModel = MeetingTrainingViewModel(TestRepository(), failingCleanup)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertEquals(1, state.events.size)
  }

  @Test
  fun `toggling both chips off shows an empty list without crashing`() = runTest(dispatcher) {
    val viewModel = MeetingTrainingViewModel(TestRepository(), FakePhotoCleanup())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onEventTypeToggled(EventType.MEETING)
    viewModel.onEventTypeToggled(EventType.TRAINING)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MeetingTrainingUiState.Success
    assertTrue(state.selectedEventTypes.isEmpty())
    assertTrue(state.events.isEmpty())
  }
}
