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
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RescheduleMeetingViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun savedStateHandle(eventId: String = "event-1") =
    SavedStateHandle(mapOf(Routes.MEETING_DETAIL_EVENT_ID_ARG to eventId))

  private class TestRepository(
    private val detail: MeetingDetail = MeetingDetail(
      "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "",
      EventStatus.SCHEDULED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
    ),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var lastNewStart: String? = null
      private set
    var lastNewEnd: String? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = error("not used")

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = error("not used")

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail = detail

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) {
      if (shouldFail) error("reschedule failed")
      lastNewStart = newStartDate
      lastNewEnd = newEndDate
    }

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): MeetingEntry = error("not used")

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = error("not used")


    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String = error("not used")

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = error("not used")

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = error("not used")

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> = error("not used")

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) = error("not used")

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) = error("not used")
  }

  private fun readyState(viewModel: RescheduleMeetingViewModel): RescheduleMeetingUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as RescheduleMeetingUiState.Success
  }

  // --- Positive ---

  @Test
  fun `loads existing meeting's dates as read-only originals`() = runTest(dispatcher) {
    val viewModel = RescheduleMeetingViewModel(TestRepository(), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals("22 Jul 2026", state.originalStartDate)
    assertEquals("22 Jul 2026", state.originalEndDate)
  }

  @Test
  fun `submitting a valid new range calls repository with new dates`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = RescheduleMeetingViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onNewStartDateSelected("23 Jul 2026")
    viewModel.onNewEndDateSelected("24 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("23 Jul 2026", repo.lastNewStart)
    assertEquals("24 Jul 2026", repo.lastNewEnd)
    assertTrue((viewModel.uiState.value as RescheduleMeetingUiState.Success).submitted)
  }

  // --- Negative ---

  @Test
  fun `new end date before new start date blocks with invalidRange`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = RescheduleMeetingViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onNewStartDateSelected("24 Jul 2026")
    viewModel.onNewEndDateSelected("23 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as RescheduleMeetingUiState.Success
    assertTrue(state.invalidRange)
    assertEquals(null, repo.lastNewStart)
  }

  @Test
  fun `repository failure on reschedule moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = RescheduleMeetingViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is RescheduleMeetingUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `rescheduling an already-completed meeting fails to load`() = runTest(dispatcher) {
    val repo = TestRepository(
      detail = MeetingDetail(
        "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "",
        EventStatus.COMPLETED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
      ),
    )
    val viewModel = RescheduleMeetingViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is RescheduleMeetingUiState.Error)
  }
}
