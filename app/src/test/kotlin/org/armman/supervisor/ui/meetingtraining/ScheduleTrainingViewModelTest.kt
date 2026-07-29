package org.armman.supervisor.ui.meetingtraining

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleTrainingViewModelTest {
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
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var scheduleCallCount = 0
      private set
    var lastRequest: ScheduleTrainingRequest? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = projects

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = error("not used")

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail = error("not used")

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): MeetingEntry {
      if (shouldFail) error("schedule failed")
      scheduleCallCount++
      lastRequest = request
      return MeetingEntry(
        "event-1", EventType.TRAINING, request.projectName, request.startDate, request.endDate, request.remarks, 1L,
      )
    }

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = error("not used")


    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String = error("not used")

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = error("not used")

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = error("not used")

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> = error("not used")

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) = error("not used")

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) = error("not used")
  }

  private fun readyState(viewModel: ScheduleTrainingViewModel): ScheduleTrainingUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as ScheduleTrainingUiState.Success
  }

  // --- Positive ---

  @Test
  fun `initial load populates project list and defaults start date to today`() = runTest(dispatcher) {
    val viewModel = ScheduleTrainingViewModel(TestRepository())
    val state = readyState(viewModel)

    assertEquals(1, state.projects.size)
    assertTrue(state.startDate!!.isNotBlank())
  }

  @Test
  fun `submit with valid project and dates succeeds and calls repository once`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onEndDateSelected("30 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.scheduleCallCount)
    assertTrue((viewModel.uiState.value as ScheduleTrainingUiState.Success).submitted)
  }

  @Test
  fun `checking pre-post marks toggle is included in the submitted request`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onPrePostMarksToggled(true)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(repo.lastRequest!!.prePostMarksApplicable)
  }

  @Test
  fun `remarks are included in the submitted request`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onRemarksChanged("Test remarks")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("Test remarks", repo.lastRequest!!.remarks)
  }

  @Test
  fun `end date left blank defaults to start date`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("27 Jul 2026", repo.lastRequest!!.endDate)
  }

  // --- Negative ---

  @Test
  fun `submit with no project selected blocks with PROJECT_REQUIRED`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(ScheduleTrainingFormError.PROJECT_REQUIRED, state.formError)
    assertEquals(0, repo.scheduleCallCount)
  }

  @Test
  fun `submit with no start date blocks with START_DATE_REQUIRED`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(ScheduleTrainingFormError.START_DATE_REQUIRED, state.formError)
    assertEquals(0, repo.scheduleCallCount)
  }

  @Test
  fun `end date before start date blocks with INVALID_DATE_RANGE`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("28 Jul 2026")
    viewModel.onEndDateSelected("27 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(ScheduleTrainingFormError.INVALID_DATE_RANGE, state.formError)
    assertEquals(0, repo.scheduleCallCount)
  }

  @Test
  fun `repository failure on submit moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is ScheduleTrainingUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `double submit while isSubmitting is a no-op`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.scheduleCallCount)
  }

  @Test
  fun `pre-post marks defaults to unchecked`() = runTest(dispatcher) {
    val viewModel = ScheduleTrainingViewModel(TestRepository())
    val state = readyState(viewModel)

    assertEquals(false, state.prePostMarksApplicable)
    assertEquals(false, state.submitted)
  }
}
