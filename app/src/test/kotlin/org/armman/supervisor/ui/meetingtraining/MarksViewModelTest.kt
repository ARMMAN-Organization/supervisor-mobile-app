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
class MarksViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun savedStateHandle(marksType: MarksType = MarksType.PRE) = SavedStateHandle(
    mapOf(
      Routes.MEETING_DETAIL_EVENT_ID_ARG to "event-1",
      Routes.GATHERING_ID_ARG to "gathering-1",
      Routes.MARKS_TYPE_ARG to marksType.name,
    ),
  )

  private class TestRepository(
    private val topics: List<TrainingTopic> = listOf(TrainingTopic("topic-1", "Topic A")),
    private val roster: List<AttendanceRosterEntry> = listOf(AttendanceRosterEntry("sakhi-1", "Sushil")),
    private val existingMarks: Map<String, List<MarksEntry>> = emptyMap(),
    private val topicStatuses: List<GatheringTopicStatus> = listOf(
      GatheringTopicStatus("topic-1", "Topic A", preMarksCompleted = false, postMarksCompleted = false),
    ),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var lastSavedTopicId: String? = null
      private set
    var lastSavedMarksType: MarksType? = null
      private set
    var lastSavedEntries: List<MarksEntry>? = null
      private set
    var completeCallCount = 0
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = listOf(LocationOption("loc-1", "Zone A"))

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = roster

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail = MeetingDetail(
      "event-1", EventType.TRAINING, "Zone A", "27 Jul 2026", "27 Jul 2026", "",
      EventStatus.SCHEDULED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
      gatherings = listOf(GatheringSummary("gathering-1", "27 Jul 2026", topicStatuses, 0, 1)),
    )

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult = error("not used")

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = error("not used")

    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String = error("not used")

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) =
      error("not used")

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = error("not used")

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = topics

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> =
      existingMarks["$topicId:$marksType"].orEmpty()

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) {
      if (shouldFail) error("save failed")
      lastSavedTopicId = topicId
      lastSavedMarksType = marksType
      lastSavedEntries = entries
    }

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) {
      if (shouldFail) error("complete failed")
      completeCallCount++
    }
  }

  private fun readyState(viewModel: MarksViewModel): MarksUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as MarksUiState.Success
  }

  // --- Positive ---

  @Test
  fun `initial load populates topics scoped to the gathering`() = runTest(dispatcher) {
    val viewModel = MarksViewModel(TestRepository(), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals(1, state.topics.size)
    assertEquals(MarksType.PRE, state.marksType)
  }

  @Test
  fun `selecting a topic loads its roster`() = runTest(dispatcher) {
    val viewModel = MarksViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MarksUiState.Success
    assertEquals(1, state.roster.size)
    assertEquals("Sushil", state.roster.single().sakhiName)
  }

  @Test
  fun `entering marks and saving persists without completing`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onMarksChanged("sakhi-1", "45")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("topic-1", repo.lastSavedTopicId)
    assertEquals(MarksType.PRE, repo.lastSavedMarksType)
    assertEquals(45, repo.lastSavedEntries?.single()?.marks)
    assertEquals(0, repo.completeCallCount)
  }

  @Test
  fun `complete and close saves then locks the topic`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onMarksChanged("sakhi-1", "45")
    viewModel.onCompleteAndClose()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.completeCallCount)
    assertTrue((viewModel.uiState.value as MarksUiState.Success).completed)
  }

  @Test
  fun `already-completed topic loads as read-only`() = runTest(dispatcher) {
    val repo = TestRepository(
      topicStatuses = listOf(GatheringTopicStatus("topic-1", "Topic A", preMarksCompleted = true, postMarksCompleted = false)),
    )
    val viewModel = MarksViewModel(repo, savedStateHandle(MarksType.PRE))
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue((viewModel.uiState.value as MarksUiState.Success).completed)
  }

  // --- Negative ---

  @Test
  fun `entering a negative number blocks save with a validation error`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onMarksChanged("sakhi-1", "-5")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue((viewModel.uiState.value as MarksUiState.Success).invalidValueError)
    assertEquals(null, repo.lastSavedTopicId)
  }

  @Test
  fun `entering a number above 100 blocks save with a validation error`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onMarksChanged("sakhi-1", "954")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue((viewModel.uiState.value as MarksUiState.Success).invalidValueError)
    assertEquals(null, repo.lastSavedTopicId)
  }

  @Test
  fun `saving immediately after selecting a topic waits for the roster load instead of saving an empty roster`() =
    runTest(dispatcher) {
      val repo = TestRepository()
      val viewModel = MarksViewModel(repo, savedStateHandle())
      readyState(viewModel)

      // No advanceUntilIdle between these two calls: onTopicSelected's async roster load is still
      // pending when onSave fires, mirroring a user tapping Save right after picking a topic.
      viewModel.onTopicSelected("topic-1")
      viewModel.onSave()
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals(null, repo.lastSavedTopicId)
    }

  @Test
  fun `switching topics again before the first roster load resolves does not resurrect the stale load`() =
    runTest(dispatcher) {
      val repo = TestRepository(
        topics = listOf(TrainingTopic("topic-1", "Topic A"), TrainingTopic("topic-2", "Topic B")),
        topicStatuses = listOf(
          GatheringTopicStatus("topic-1", "Topic A", preMarksCompleted = false, postMarksCompleted = false),
          GatheringTopicStatus("topic-2", "Topic B", preMarksCompleted = false, postMarksCompleted = false),
        ),
      )
      val viewModel = MarksViewModel(repo, savedStateHandle())
      readyState(viewModel)

      viewModel.onTopicSelected("topic-1")
      viewModel.onTopicSelected("topic-2")
      dispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.uiState.value as MarksUiState.Success
      assertEquals("topic-2", state.selectedTopicId)
      assertTrue(!state.isLoadingRoster)
    }

  @Test
  fun `save on an already-completed topic is blocked client-side`() = runTest(dispatcher) {
    val repo = TestRepository(
      topicStatuses = listOf(GatheringTopicStatus("topic-1", "Topic A", preMarksCompleted = true, postMarksCompleted = false)),
    )
    val viewModel = MarksViewModel(repo, savedStateHandle(MarksType.PRE))
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(null, repo.lastSavedTopicId)
  }

  @Test
  fun `repository failure on save moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MarksUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `switching topic before saving discards unsaved input for the previous topic`() = runTest(dispatcher) {
    val repo = TestRepository(
      topics = listOf(TrainingTopic("topic-1", "Topic A"), TrainingTopic("topic-2", "Topic B")),
      topicStatuses = listOf(
        GatheringTopicStatus("topic-1", "Topic A", preMarksCompleted = false, postMarksCompleted = false),
        GatheringTopicStatus("topic-2", "Topic B", preMarksCompleted = false, postMarksCompleted = false),
      ),
    )
    val viewModel = MarksViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()
    viewModel.onMarksChanged("sakhi-1", "45")

    viewModel.onTopicSelected("topic-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MarksUiState.Success
    assertEquals(null, state.roster.single().marks)
  }

  @Test
  fun `zero Sakhis in roster renders an empty state without crashing`() = runTest(dispatcher) {
    val viewModel = MarksViewModel(TestRepository(roster = emptyList()), savedStateHandle())
    readyState(viewModel)
    viewModel.onTopicSelected("topic-1")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MarksUiState.Success
    assertTrue(state.roster.isEmpty())
  }
}
