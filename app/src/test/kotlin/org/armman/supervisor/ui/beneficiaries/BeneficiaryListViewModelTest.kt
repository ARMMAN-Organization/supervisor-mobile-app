package org.armman.supervisor.ui.beneficiaries

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BeneficiaryListViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  private val mother = BeneficiaryDetail.Mother(
    name = "Sushma T Test",
    registrationDate = "04-08-2026",
    phone = "9898989893",
    edd = "06-01-2027",
    lmp = "01-04-2026",
    heightCm = 156.0,
    weightKg = 56.0,
  )
  private val child = BeneficiaryDetail.Child(
    name = "Child 1 T Test",
    registrationDate = "04-08-2026",
    phone = "9898989894",
    birthdate = "04-07-2026",
  )

  private class TestRepository(
    private var listsBySakhiId: Map<String, SakhiBeneficiaryList>,
    private var shouldFail: Boolean = false,
  ) : BeneficiaryListRepository {
    fun shouldSucceedNow(listsBySakhiId: Map<String, SakhiBeneficiaryList>) {
      shouldFail = false
      this.listsBySakhiId = listsBySakhiId
    }

    override suspend fun getBeneficiaries(sakhiId: String): SakhiBeneficiaryList {
      if (shouldFail) error("beneficiaries failed")
      return listsBySakhiId[sakhiId] ?: throw NoSuchElementException(sakhiId)
    }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel(
    sakhiId: String = "sakhi-komal",
    repository: BeneficiaryListRepository = TestRepository(
      mapOf("sakhi-komal" to SakhiBeneficiaryList("SakhiKomal", "Test-4", "Test", listOf(child, mother))),
    ),
  ) = BeneficiaryListViewModel(repository, SavedStateHandle(mapOf(BeneficiaryListViewModel.SAKHI_ID_ARG to sakhiId)))

  @Test
  fun `initial state is Loading`() {
    assertEquals(BeneficiaryListUiState.Loading, viewModel().uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with sakhi context and both beneficiaries`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as BeneficiaryListUiState.Success
    assertEquals("SakhiKomal", state.sakhiName)
    assertEquals("Test-4", state.projectName)
    assertEquals("Test", state.address)
    assertEquals(2, state.visibleBeneficiaries.size)
  }

  @Test
  fun `zero beneficiaries reaches Success with empty list`() = runTest(dispatcher) {
    val repo = TestRepository(mapOf("sakhi-komal" to SakhiBeneficiaryList("SakhiKomal", "Test-4", "Test", emptyList())))
    val vm = viewModel(repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as BeneficiaryListUiState.Success
    assertTrue(state.allBeneficiaries.isEmpty())
    assertTrue(state.visibleBeneficiaries.isEmpty())
  }

  @Test
  fun `search filters by case-insensitive name substring across mother and child`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    vm.onSearchQueryChanged("sushma")
    var state = vm.uiState.value as BeneficiaryListUiState.Success
    assertEquals(1, state.visibleBeneficiaries.size)
    assertEquals("Sushma T Test", state.visibleBeneficiaries.first().name)

    vm.onSearchQueryChanged("child")
    state = vm.uiState.value as BeneficiaryListUiState.Success
    assertEquals(1, state.visibleBeneficiaries.size)
    assertEquals("Child 1 T Test", state.visibleBeneficiaries.first().name)
  }

  @Test
  fun `search with no matches yields empty visible list without touching allBeneficiaries`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    vm.onSearchQueryChanged("no-such-name")

    val state = vm.uiState.value as BeneficiaryListUiState.Success
    assertTrue(state.visibleBeneficiaries.isEmpty())
    assertEquals(2, state.allBeneficiaries.size)
  }

  @Test
  fun `clearing search query restores full list`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    vm.onSearchQueryChanged("sushma")
    vm.onSearchQueryChanged("")

    val state = vm.uiState.value as BeneficiaryListUiState.Success
    assertEquals(2, state.visibleBeneficiaries.size)
  }

  @Test
  fun `unknown sakhiId moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(mapOf("sakhi-komal" to SakhiBeneficiaryList("SakhiKomal", "Test-4", "Test", listOf(mother))))
    val vm = viewModel(sakhiId = "unknown-sakhi", repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value is BeneficiaryListUiState.Error)
  }

  @Test
  fun `retry after transient failure re-fetches and can reach Success`() = runTest(dispatcher) {
    val failing = TestRepository(emptyMap(), shouldFail = true)
    val vm = viewModel(repository = failing)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(vm.uiState.value is BeneficiaryListUiState.Error)

    failing.shouldSucceedNow(mapOf("sakhi-komal" to SakhiBeneficiaryList("SakhiKomal", "Test-4", "Test", listOf(mother))))
    vm.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value is BeneficiaryListUiState.Success)
  }

  @Test
  fun `mother bmi is computed from height and weight`() {
    assertEquals(23.01, mother.bmi, 0.01)
  }
}
