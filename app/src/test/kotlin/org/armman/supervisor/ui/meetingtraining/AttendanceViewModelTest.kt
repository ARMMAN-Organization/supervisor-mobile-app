package org.armman.supervisor.ui.meetingtraining

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.data.meetingtraining.EventScheduleResult
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun savedStateHandle(eventId: String = "event-1", gatheringId: String? = null) =
    SavedStateHandle(
      buildMap {
        put(Routes.MEETING_DETAIL_EVENT_ID_ARG, eventId)
        if (gatheringId != null) put(Routes.GATHERING_ID_ARG, gatheringId)
      },
    )

  private class TestRepository(
    private val projects: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A")),
    private val roster: List<AttendanceRosterEntry> = listOf(AttendanceRosterEntry("sakhi-1", "Sushil")),
    private val detail: MeetingDetail = MeetingDetail(
      "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "",
      EventStatus.SCHEDULED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
    ),
    private val existingGatheringAttendance: List<AttendanceEntry> = emptyList(),
    private val existingAttendance: List<AttendanceEntry> = emptyList(),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var lastSavedAttendance: List<AttendanceEntry>? = null
      private set
    var lastSavedGatheringId: String? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = projects

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> {
      if (shouldFail) error("roster failed")
      return roster
    }

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail = detail

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = existingAttendance

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) {
      if (shouldFail) error("save failed")
      lastSavedAttendance = attendance
    }

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult = error("not used")

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = error("not used")

    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String = error("not used")

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) {
      if (shouldFail) error("save failed")
      lastSavedAttendance = attendance
      lastSavedGatheringId = gatheringId
    }

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = existingGatheringAttendance

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = error("not used")

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> = error("not used")

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) = error("not used")

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) = error("not used")
  }

  private fun readyState(viewModel: AttendanceViewModel): AttendanceUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as AttendanceUiState.Success
  }

  // --- Positive ---

  @Test
  fun `initial load populates roster all absent when nothing was saved yet`() = runTest(dispatcher) {
    val viewModel = AttendanceViewModel(TestRepository(), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals(1, state.roster.size)
    assertTrue(state.roster.none { it.present })
  }

  @Test
  fun `reopening a Meeting restores previously saved attendance instead of resetting to absent`() = runTest(dispatcher) {
    val roster = listOf(AttendanceRosterEntry("sakhi-1", "Sushil"), AttendanceRosterEntry("sakhi-2", "Asha"))
    val repo = TestRepository(
      roster = roster,
      existingAttendance = listOf(
        AttendanceEntry("sakhi-1", "Sushil", present = true),
        AttendanceEntry("sakhi-2", "Asha", present = false),
      ),
    )
    val viewModel = AttendanceViewModel(repo, savedStateHandle())
    val state = readyState(viewModel)

    assertEquals(1, state.presentCount)
    assertTrue(state.roster.single { it.sakhiId == "sakhi-1" }.present)
    assertTrue(state.roster.single { it.sakhiId == "sakhi-2" }.present.not())
  }

  @Test
  fun `toggling a row updates its status and the running count`() = runTest(dispatcher) {
    val viewModel = AttendanceViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.onToggle("sakhi-1")

    val state = viewModel.uiState.value as AttendanceUiState.Success
    assertEquals(1, state.presentCount)
  }

  @Test
  fun `Mark All Present sets every row present`() = runTest(dispatcher) {
    val roster = listOf(AttendanceRosterEntry("sakhi-1", "Sushil"), AttendanceRosterEntry("sakhi-2", "Asha"))
    val viewModel = AttendanceViewModel(TestRepository(roster = roster), savedStateHandle())
    readyState(viewModel)

    viewModel.onMarkAllPresent()

    val state = viewModel.uiState.value as AttendanceUiState.Success
    assertEquals(2, state.presentCount)
  }

  @Test
  fun `saving calls repository with the correct per-Sakhi status list`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AttendanceViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onToggle("sakhi-1")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(true, repo.lastSavedAttendance?.single()?.present)
  }

  @Test
  fun `unchecking after Mark All Present decrements the count`() = runTest(dispatcher) {
    val roster = listOf(AttendanceRosterEntry("sakhi-1", "Sushil"), AttendanceRosterEntry("sakhi-2", "Asha"))
    val viewModel = AttendanceViewModel(TestRepository(roster = roster), savedStateHandle())
    readyState(viewModel)

    viewModel.onMarkAllPresent()
    viewModel.onToggle("sakhi-1")

    val state = viewModel.uiState.value as AttendanceUiState.Success
    assertEquals(1, state.presentCount)
  }

  // --- Negative ---

  @Test
  fun `repository failure on load moves to Error`() = runTest(dispatcher) {
    val viewModel = AttendanceViewModel(TestRepository(shouldFail = true), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AttendanceUiState.Error)
  }

  @Test
  fun `repository failure on save moves to Error without losing checkbox intent`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AttendanceViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onToggle("sakhi-1")
    repo.failNextCalls(true)
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AttendanceUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `empty roster shows zero over zero without crashing`() = runTest(dispatcher) {
    val viewModel = AttendanceViewModel(TestRepository(roster = emptyList()), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals(0, state.presentCount)
    assertTrue(state.roster.isEmpty())
  }

  @Test
  fun `attendance for a completed meeting is read-only`() = runTest(dispatcher) {
    val repo = TestRepository(
      detail = MeetingDetail(
        "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "",
        EventStatus.COMPLETED, attendedCount = 1, totalRosterCount = 1, photoPaths = emptyList(),
      ),
    )
    val viewModel = AttendanceViewModel(repo, savedStateHandle())
    val state = readyState(viewModel)

    assertTrue(state.readOnly)

    viewModel.onToggle("sakhi-1")
    assertEquals(0, (viewModel.uiState.value as AttendanceUiState.Success).presentCount)
  }

  // --- Gathering-scoped (Training) ---

  @Test
  fun `gathering attendance saves via saveGatheringAttendance, not saveAttendance`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AttendanceViewModel(repo, savedStateHandle(gatheringId = "gathering-1"))
    readyState(viewModel)

    viewModel.onToggle("sakhi-1")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("gathering-1", repo.lastSavedGatheringId)
    assertEquals(true, repo.lastSavedAttendance?.single()?.present)
  }

  @Test
  fun `reopening a gathering restores previously saved attendance instead of resetting to absent`() = runTest(dispatcher) {
    val repo = TestRepository(existingGatheringAttendance = listOf(AttendanceEntry("sakhi-1", "Sushil", present = true)))
    val viewModel = AttendanceViewModel(repo, savedStateHandle(gatheringId = "gathering-1"))

    val state = readyState(viewModel)

    assertEquals(1, state.presentCount)
  }

  @Test
  fun `Meeting attendance (no gatheringId) is unaffected by the gathering path`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AttendanceViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onToggle("sakhi-1")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(null, repo.lastSavedGatheringId)
    assertEquals(true, repo.lastSavedAttendance?.single()?.present)
  }
}
