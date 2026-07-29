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
class AddTrainingTopicsViewModelTest {
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
      "event-1", EventType.TRAINING, "Armman Test", "27 Jul 2026", "27 Jul 2026", "",
      EventStatus.SCHEDULED, attendedCount = 0, totalRosterCount = 0, photoPaths = emptyList(),
    ),
    private val catalog: List<TrainingTopic> = listOf(
      TrainingTopic("topic-1", "Antenatal Care Basics"),
      TrainingTopic("topic-2", "Nutrition Counselling"),
    ),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var saveCallCount = 0
      private set
    var lastSavedTopicNames: List<String>? = null
      private set
    var lastSavedDate: String? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getProjects(): List<LocationOption> = error("not used")

    override suspend fun getSakhiRoster(projectId: String?): List<AttendanceRosterEntry> = error("not used")

    override suspend fun getEvents(status: EventStatus): List<MeetingEntry> = error("not used")

    override suspend fun getEventDetail(eventId: String): MeetingDetail {
      if (shouldFail) error("load failed")
      return detail
    }

    override suspend fun getSavedAttendance(eventId: String): List<AttendanceEntry> = error("not used")

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) = error("not used")

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) = error("not used")

    override suspend fun completeMeeting(eventId: String) = error("not used")

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): MeetingEntry = error("not used")

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = catalog

    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String {
      if (shouldFail) error("save failed")
      saveCallCount++
      lastSavedTopicNames = topicNames
      lastSavedDate = date
      return "gathering-1"
    }

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) =
      error("not used")

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = error("not used")

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = error("not used")

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> = error("not used")

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) =
      error("not used")

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) = error("not used")
  }

  private fun readyState(viewModel: AddTrainingTopicsViewModel): AddTrainingTopicsUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as AddTrainingTopicsUiState.Success
  }

  // --- Positive ---

  @Test
  fun `initial load populates project name and topic catalog`() = runTest(dispatcher) {
    val viewModel = AddTrainingTopicsViewModel(TestRepository(), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals("Armman Test", state.projectName)
    assertEquals(2, state.catalog.size)
    assertTrue(state.selectedTopicIds.isEmpty())
  }

  @Test
  fun `selecting topics and saving persists all selected topic names`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddTrainingTopicsViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onTopicToggled("topic-1")
    viewModel.onTopicToggled("topic-2")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.saveCallCount)
    assertEquals(listOf("Antenatal Care Basics", "Nutrition Counselling"), repo.lastSavedTopicNames)
    assertTrue((viewModel.uiState.value as AddTrainingTopicsUiState.Success).saved)
  }

  @Test
  fun `changing date is reflected in the saved request`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddTrainingTopicsViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onDateSelected("28 Jul 2026")
    viewModel.onTopicToggled("topic-1")
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("28 Jul 2026", repo.lastSavedDate)
  }

  // --- Negative ---

  @Test
  fun `save with zero topics selected is a no-op`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddTrainingTopicsViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repo.saveCallCount)
  }

  @Test
  fun `repository failure on save moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddTrainingTopicsViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onTopicToggled("topic-1")
    repo.failNextCalls(true)
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AddTrainingTopicsUiState.Error)
  }

  @Test
  fun `repository failure on load moves to Error`() = runTest(dispatcher) {
    val viewModel = AddTrainingTopicsViewModel(TestRepository(shouldFail = true), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AddTrainingTopicsUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `toggling a topic twice deselects it`() = runTest(dispatcher) {
    val viewModel = AddTrainingTopicsViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.onTopicToggled("topic-1")
    viewModel.onTopicToggled("topic-1")

    val state = viewModel.uiState.value as AddTrainingTopicsUiState.Success
    assertTrue(state.selectedTopicIds.isEmpty())
  }

  @Test
  fun `double save while isSaving is a no-op`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddTrainingTopicsViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onTopicToggled("topic-1")
    viewModel.onSave()
    viewModel.onSave()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.saveCallCount)
  }
}
