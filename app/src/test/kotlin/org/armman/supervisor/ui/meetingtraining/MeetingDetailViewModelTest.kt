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
class MeetingDetailViewModelTest {
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
    private var detail: MeetingDetail = MeetingDetail(
      "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "Testing",
      EventStatus.SCHEDULED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
    ),
    private var shouldFail: Boolean = false,
  ) : MeetingTrainingRepository {
    var completeCallCount = 0
      private set
    var cancelCallCount = 0
      private set

    /** When > 0, the next that many [completeMeeting] calls fail; decremented on each attempt.
     * Lets a test simulate "complete fails once (e.g. missing attendance), then succeeds on
     * retry" without failing every repository call the way [shouldFail] does. */
    var failCompleteCallsRemaining = 0

    fun setDetail(newDetail: MeetingDetail) {
      detail = newDetail
    }

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

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): EventScheduleResult = error("not used")

    override suspend fun rescheduleMeeting(eventId: String, newStartDate: String, newEndDate: String) = error("not used")

    override suspend fun cancelMeeting(eventId: String) {
      cancelCallCount++
      detail = detail.copy(status = EventStatus.CANCELLED)
    }

    override suspend fun saveAttendance(eventId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun addPhoto(eventId: String, filePath: String) {
      detail = detail.copy(photoPaths = detail.photoPaths + filePath)
    }

    override suspend fun completeMeeting(eventId: String) {
      completeCallCount++
      if (failCompleteCallsRemaining > 0) {
        failCompleteCallsRemaining--
        error("Missing attendance")
      }
      detail = detail.copy(status = EventStatus.COMPLETED)
    }

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")

    override suspend fun scheduleTraining(request: ScheduleTrainingRequest): EventScheduleResult = error("not used")

    override suspend fun getTrainingTopicsCatalog(): List<TrainingTopic> = error("not used")


    override suspend fun addGathering(eventId: String, topicNames: List<String>, date: String): String = error("not used")

    override suspend fun saveGatheringAttendance(eventId: String, gatheringId: String, attendance: List<AttendanceEntry>) = error("not used")

    override suspend fun getGatheringAttendanceRoster(gatheringId: String): List<AttendanceEntry> = error("not used")

    override suspend fun getTopicsForGathering(gatheringId: String): List<TrainingTopic> = error("not used")

    override suspend fun getMarks(topicId: String, marksType: MarksType): List<MarksEntry> = error("not used")

    override suspend fun saveMarks(eventId: String, topicId: String, marksType: MarksType, entries: List<MarksEntry>) = error("not used")

    override suspend fun completeMarks(eventId: String, topicId: String, marksType: MarksType) = error("not used")
  }

  private fun readyState(viewModel: MeetingDetailViewModel): MeetingDetailUiState.Success {
    dispatcher.scheduler.advanceUntilIdle()
    return viewModel.uiState.value as MeetingDetailUiState.Success
  }

  // --- Positive ---

  @Test
  fun `initial load populates the header detail`() = runTest(dispatcher) {
    val viewModel = MeetingDetailViewModel(TestRepository(), savedStateHandle())
    val state = readyState(viewModel)

    assertEquals("Zone A", state.detail.projectName)
    assertEquals(0, state.detail.attendedCount)
  }

  @Test
  fun `refresh after data is already showing does not flash back to Loading`() = runTest(dispatcher) {
    val viewModel = MeetingDetailViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.refresh()

    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Success)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Success)
  }

  @Test
  fun `a resume-triggered refresh racing an in-flight guarded action does not lose the action's result`() =
    runTest(dispatcher) {
      val viewModel = MeetingDetailViewModel(TestRepository(), savedStateHandle())
      readyState(viewModel)

      // Simulates: onAddPhoto launches its guarded reload, then the camera activity returns and
      // LifecycleResumeEffect fires refresh() before the guarded action's own reload has settled.
      viewModel.onAddPhoto("/data/1.jpg")
      viewModel.refresh()
      dispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.uiState.value as MeetingDetailUiState.Success
      assertEquals(listOf("/data/1.jpg"), state.detail.photoPaths)
      assertTrue(!state.isActionInProgress)
    }

  @Test
  fun `adding a photo does not flash back to Loading before the reload completes`() = runTest(dispatcher) {
    val viewModel = MeetingDetailViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.onAddPhoto("/data/1.jpg")

    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Success)
  }

  @Test
  fun `adding a photo updates the pictures list`() = runTest(dispatcher) {
    val viewModel = MeetingDetailViewModel(TestRepository(), savedStateHandle())
    readyState(viewModel)

    viewModel.onAddPhoto("/data/1.jpg")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("/data/1.jpg"), (viewModel.uiState.value as MeetingDetailUiState.Success).detail.photoPaths)
  }

  @Test
  fun `complete succeeds when at least one photo exists`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onAddPhoto("/data/1.jpg")
    dispatcher.scheduler.advanceUntilIdle()
    viewModel.onComplete()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.completeCallCount)
    assertEquals(EventStatus.COMPLETED, (viewModel.uiState.value as MeetingDetailUiState.Success).detail.status)
  }

  @Test
  fun `cancel sets status to CANCELLED without ever calling delete`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onCancel()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.cancelCallCount)
    assertEquals(EventStatus.CANCELLED, (viewModel.uiState.value as MeetingDetailUiState.Success).detail.status)
  }

  // --- Negative ---

  @Test
  fun `complete with zero photos is blocked and never calls the repository`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onComplete()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repo.completeCallCount)
    assertTrue((viewModel.uiState.value as MeetingDetailUiState.Success).completeBlockedNoPhoto)
  }

  @Test
  fun `repository failure on load moves to Error`() = runTest(dispatcher) {
    val viewModel = MeetingDetailViewModel(TestRepository(shouldFail = true), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Error)
  }

  @Test
  fun `retrying a failed complete re-attempts complete itself, not just a reload`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    readyState(viewModel)
    viewModel.onAddPhoto("/data/1.jpg")
    dispatcher.scheduler.advanceUntilIdle()
    repo.failCompleteCallsRemaining = 1
    viewModel.onComplete()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Error)

    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(2, repo.completeCallCount)
    assertEquals(EventStatus.COMPLETED, (viewModel.uiState.value as MeetingDetailUiState.Success).detail.status)
  }

  @Test
  fun `retrying an error from initial load just reloads, not any prior action`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MeetingDetailUiState.Success)
    assertEquals(0, repo.completeCallCount)
  }

  // --- Edge cases ---

  @Test
  fun `actions on an already-cancelled meeting are no-ops, not crashes`() = runTest(dispatcher) {
    val repo = TestRepository(
      detail = MeetingDetail(
        "event-1", EventType.MEETING, "Zone A", "22 Jul 2026", "22 Jul 2026", "",
        EventStatus.CANCELLED, attendedCount = 0, totalRosterCount = 1, photoPaths = emptyList(),
      ),
    )
    val viewModel = MeetingDetailViewModel(repo, savedStateHandle())
    readyState(viewModel)

    viewModel.onCancel()
    viewModel.onAddPhoto("/data/1.jpg")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, repo.cancelCallCount)
    assertEquals(EventStatus.CANCELLED, (viewModel.uiState.value as MeetingDetailUiState.Success).detail.status)
  }
}
