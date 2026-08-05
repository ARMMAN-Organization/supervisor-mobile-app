package org.armman.supervisor.ui.visitsummary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VisitSummaryViewModelTest {
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
    private val locations: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A"), LocationOption("loc-2", "Zone B")),
    private val sakhisByLocation: Map<String, List<SakhiVisitSummary>> = mapOf(
      "loc-1" to listOf(
        SakhiVisitSummary("SakhiKomal", listOf(VillageVisitRow("SushilTest", 5, 0, 0))),
        SakhiVisitSummary("SakhiMeera", listOf(VillageVisitRow("SushilTest1", 3, 0, 0))),
      ),
      "loc-2" to listOf(SakhiVisitSummary("SakhiAsha", listOf(VillageVisitRow("Village2", 2, 1, 0)))),
    ),
    private var shouldFail: Boolean = false,
  ) : VisitSummaryRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getVisitSummary(locationId: String?): List<SakhiVisitSummary> {
      if (shouldFail) error("visit summary failed")
      return sakhisByLocation[locationId].orEmpty()
    }
  }

  @Test
  fun `initial state is Loading`() {
    val viewModel = VisitSummaryViewModel(TestRepository())
    assertEquals(VisitSummaryUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with first location and multiple sakhis`() = runTest(dispatcher) {
    val viewModel = VisitSummaryViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as VisitSummaryUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals(2, state.sakhis.size)
    assertEquals("SakhiKomal", state.sakhis.first().sakhiName)
    assertEquals(5, state.sakhis.first().villages.first().total)
  }

  @Test
  fun `single sakhi group renders as a single-item list`() = runTest(dispatcher) {
    val viewModel = VisitSummaryViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as VisitSummaryUiState.Success
    assertEquals(1, state.sakhis.size)
    assertEquals("SakhiAsha", state.sakhis.first().sakhiName)
  }

  @Test
  fun `zero sakhi groups reaches Success with empty list`() = runTest(dispatcher) {
    val viewModel = VisitSummaryViewModel(TestRepository(sakhisByLocation = emptyMap()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as VisitSummaryUiState.Success
    assertTrue(state.sakhis.isEmpty())
  }

  @Test
  fun `large number of sakhi groups is not truncated`() = runTest(dispatcher) {
    val many = (1..20).map { SakhiVisitSummary("Sakhi$it", listOf(VillageVisitRow("Village$it", it, 0, 0))) }
    val viewModel = VisitSummaryViewModel(TestRepository(sakhisByLocation = mapOf("loc-1" to many)))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as VisitSummaryUiState.Success
    assertEquals(20, state.sakhis.size)
  }

  @Test
  fun `selecting a location re-fetches and preserves the location list`() = runTest(dispatcher) {
    val viewModel = VisitSummaryViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as VisitSummaryUiState.Success
    assertEquals(2, state.locations.size)
    assertEquals("loc-2", state.selectedLocationId)
  }

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = VisitSummaryViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is VisitSummaryUiState.Error)
  }

  @Test
  fun `failure during location change moves to Error without keeping stale data`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = VisitSummaryViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is VisitSummaryUiState.Error)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = VisitSummaryViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is VisitSummaryUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is VisitSummaryUiState.Success)
  }
}
