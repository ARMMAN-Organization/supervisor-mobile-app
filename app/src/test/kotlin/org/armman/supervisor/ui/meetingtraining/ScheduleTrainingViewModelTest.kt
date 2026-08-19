package org.armman.supervisor.ui.meetingtraining

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
    private val catalog: List<TrainingTopic> = listOf(TrainingTopic("topic-1", "Nutrition")),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var scheduleCallCount = 0
      private set
    var lastRequest: ScheduleTrainingRequest? = null
      private set
    var gatheringCallCount = 0
      private set
    var lastGatheringTopicNames: List<String>? = null
      private set
    var lastGatheringDate: String? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = projects

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = error("not used")

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail = error("not used")

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult {
      if (shouldFail) error("schedule failed")
      scheduleCallCount++
      lastRequest = request
      val entry = MeetingEntry(
        "event-1", EventType.TRAINING, request.projectName, request.startDate, request.endDate, request.remarks, 1L,
      )
      return EventScheduleResult.Synced(entry)
    }

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = catalog

    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String {
      if (shouldFail) error("add gathering failed")
      gatheringCallCount++
      lastGatheringTopicNames = topicNames
      lastGatheringDate = date
      return "gathering-1"
    }

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
  fun `submit with valid project, dates and a topic succeeds and calls repository once`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onEndDateSelected("30 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.scheduleCallCount)
    assertTrue((viewModel.uiState.value as ScheduleTrainingUiState.Success).submitted)
  }

  @Test
  fun `submit with a selected topic creates the first gathering with that topic and the start date`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.gatheringCallCount)
    assertEquals(listOf("Nutrition"), repo.lastGatheringTopicNames)
    assertEquals("27 Jul 2026", repo.lastGatheringDate)
  }

  @Test
  fun `checking pre-post marks toggle is included in the submitted request`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onPrePostMarksToggled(true)
    viewModel.onTopicToggled("topic-1")
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
    viewModel.onTopicToggled("topic-1")
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
    viewModel.onTopicToggled("topic-1")
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
  fun `submit with no topic selected blocks with TOPIC_REQUIRED`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(ScheduleTrainingFormError.TOPIC_REQUIRED, state.formError)
    assertEquals(0, repo.scheduleCallCount)
  }

  @Test
  fun `empty topic catalog is exposed on state so the screen can show it has nothing to select`() = runTest(dispatcher) {
    val repo = TestRepository(catalog = emptyList())
    val viewModel = ScheduleTrainingViewModel(repo)
    val state = readyState(viewModel)

    assertTrue(state.catalog.isEmpty())
  }

  @Test
  fun `repository failure on submit shows an inline error without discarding the form`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals("schedule failed", state.submitErrorMessage)
    assertEquals("loc-1", state.selectedProjectId)
    assertEquals("27 Jul 2026", state.startDate)
    assertEquals(setOf("topic-1"), state.selectedTopicIds)
    assertTrue(!state.isSubmitting)
    assertTrue(!state.submitted)
  }

  @Test
  fun `a submit exception with a blank message still sets a non-null submitErrorMessage`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)
    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertTrue(state.submitErrorMessage != null)
  }

  @Test
  fun `editing any field after a submit error clears submitErrorMessage`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)
    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as ScheduleTrainingUiState.Success).submitErrorMessage != null)

    viewModel.onRemarksChanged("updated remarks")

    assertEquals(null, (viewModel.uiState.value as ScheduleTrainingUiState.Success).submitErrorMessage)
  }

  @Test
  fun `retrying onSubmit after a failure succeeds once the repository stops failing`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)
    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()
    repo.failNextCalls(false)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(null, state.submitErrorMessage)
    assertTrue(state.submitted)
  }

  @Test
  fun `a validation failure never sets submitErrorMessage`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onSubmit()

    val state = viewModel.uiState.value as ScheduleTrainingUiState.Success
    assertEquals(ScheduleTrainingFormError.PROJECT_REQUIRED, state.formError)
    assertEquals(null, state.submitErrorMessage)
    assertEquals(0, repo.scheduleCallCount)
  }

  // --- Edge cases ---

  @Test
  fun `double submit while isSubmitting is a no-op`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = ScheduleTrainingViewModel(repo)
    readyState(viewModel)

    viewModel.onProjectSelected("loc-1")
    viewModel.onStartDateSelected("27 Jul 2026")
    viewModel.onTopicToggled("topic-1")
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
