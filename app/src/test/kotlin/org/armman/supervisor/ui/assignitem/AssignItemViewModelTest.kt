package org.armman.supervisor.ui.assignitem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignItemViewModelTest {
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
    private val sakhisByLocation: Map<String, List<SakhiOption>> = mapOf(
      "loc-1" to listOf(SakhiOption("sakhi-1", "Sushil")),
      "loc-2" to listOf(SakhiOption("sakhi-2", "Asha")),
    ),
    private var shouldFail: Boolean = false,
    private val delaysMs: Map<String, Long> = emptyMap(),
  ) : AssignItemRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getSakhis(locationId: String?): List<SakhiOption> {
      delaysMs[locationId]?.let { delay(it) }
      if (shouldFail) error("sakhis failed")
      return sakhisByLocation[locationId].orEmpty()
    }

    override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

    override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> = error("not used")

    override suspend fun getPrograms(): List<LocationOption> = error("not used")

    override suspend fun getInventoryItems(): List<InventoryItem> = error("not used")

    override suspend fun submitTransaction(submission: TransactionSubmission): TransactionEntry = error("not used")

    override suspend fun updateTransaction(transactionId: String, submission: TransactionSubmission): TransactionEntry =
      error("not used")

    override suspend fun deleteTransaction(sakhiId: String, transactionId: String) = error("not used")
  }

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = AssignItemViewModel(TestRepository())
    assertEquals(AssignItemUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with first location selected`() = runTest(dispatcher) {
    val viewModel = AssignItemViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals(1, state.sakhis.size)
    assertEquals("Sushil", state.sakhis.first().name)
  }

  @Test
  fun `selecting a location updates selection and sakhi list`() = runTest(dispatcher) {
    val viewModel = AssignItemViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemUiState.Success
    assertEquals("loc-2", state.selectedLocationId)
    assertEquals("Asha", state.sakhis.first().name)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = AssignItemViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is AssignItemUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemUiState.Success)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = AssignItemViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `empty locations list still reaches Success with null selection`() = runTest(dispatcher) {
    val viewModel = AssignItemViewModel(TestRepository(locations = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemUiState.Success
    assertTrue(state.locations.isEmpty())
    assertNull(state.selectedLocationId)
  }

  @Test
  fun `empty sakhi list for a location is passed through as empty`() = runTest(dispatcher) {
    val viewModel = AssignItemViewModel(TestRepository(sakhisByLocation = emptyMap()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemUiState.Success
    assertTrue(state.sakhis.isEmpty())
  }

  @Test
  fun `rapid double location selection resolves to the last one requested`() = runTest(dispatcher) {
    val repo = TestRepository(delaysMs = mapOf("loc-1" to 1_000L))
    val viewModel = AssignItemViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-1")
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemUiState.Success
    assertEquals("loc-2", state.selectedLocationId)
    assertEquals("Asha", state.sakhis.first().name)
  }
}
