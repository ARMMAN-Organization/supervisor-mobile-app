package org.armman.supervisor.ui.meetingtraining

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.local.EventStatus
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

    override suspend fun scheduleMeeting(request: ScheduleMeetingRequest): MeetingEntry = error("not used")

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
      detail = detail.copy(status = EventStatus.COMPLETED)
    }

    override suspend fun getAllPhotoFilePaths(): List<String> = error("not used")
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
