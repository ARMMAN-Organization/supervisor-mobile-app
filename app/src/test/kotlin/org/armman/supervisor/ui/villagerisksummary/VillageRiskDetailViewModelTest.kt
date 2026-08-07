package org.armman.supervisor.ui.villagerisksummary

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VillageRiskDetailViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  private val mother = BeneficiaryRiskDetail(
    id = "beneficiary-sushma-t-test",
    name = "Sushma T Test",
    registrationType = "Mother",
    riskDetails = "Hypertension",
    riskType = BeneficiaryRiskLevel.HIGH,
    visit = "ANC2",
    visitDate = "04-08-2026",
    referred = true,
  )
  private val child = BeneficiaryRiskDetail(
    id = "beneficiary-child-1-t-test",
    name = "Child 1 T Test",
    registrationType = "Child",
    riskDetails = "Low Birth Weight",
    riskType = BeneficiaryRiskLevel.MILD,
    visit = "PNC1",
    visitDate = "10-08-2026",
    referred = false,
  )

  private class TestRepository(
    private var detailsByVillageId: Map<String, VillageRiskDetail>,
    private var shouldFail: Boolean = false,
  ) : VillageRiskDetailRepository {
    fun shouldSucceedNow(detailsByVillageId: Map<String, VillageRiskDetail>) {
      shouldFail = false
      this.detailsByVillageId = detailsByVillageId
    }

    override suspend fun getVillageRiskDetail(villageId: String): VillageRiskDetail {
      if (shouldFail) error("village risk detail failed")
      return detailsByVillageId[villageId] ?: VillageRiskDetail(villageId, emptyList(), emptyList())
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
    villageId: String = "SushilTest",
    sakhiName: String = "SakhiKomal",
    repository: VillageRiskDetailRepository = TestRepository(
      mapOf("SushilTest" to VillageRiskDetail("SushilTest", listOf(mother), listOf(child))),
    ),
  ) = VillageRiskDetailViewModel(
    repository,
    SavedStateHandle(
      mapOf(
        VillageRiskDetailViewModel.VILLAGE_ID_ARG to villageId,
        VillageRiskDetailViewModel.SAKHI_NAME_ARG to sakhiName,
      ),
    ),
  )

  @Test
  fun `initial state is Loading`() {
    assertEquals(VillageRiskDetailUiState.Loading, viewModel().uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with default tab Mother`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertEquals(Tab.MOTHER, state.selectedTab)
    assertEquals("SushilTest", state.villageName)
    assertEquals("SakhiKomal", state.sakhiName)
    assertEquals(1, state.mothers.size)
    assertEquals(1, state.children.size)
    assertEquals(listOf(mother), state.visibleBeneficiaries)
  }

  @Test
  fun `switching tab to Child filters the visible list to children`() = runTest(dispatcher) {
    val vm = viewModel()
    dispatcher.scheduler.advanceUntilIdle()

    vm.onTabSelected(Tab.CHILD)

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertEquals(Tab.CHILD, state.selectedTab)
    assertEquals(listOf(child), state.visibleBeneficiaries)
  }

  @Test
  fun `empty mothers for selected tab yields empty visible list without touching children`() = runTest(dispatcher) {
    val repo = TestRepository(mapOf("SushilTest" to VillageRiskDetail("SushilTest", emptyList(), listOf(child))))
    val vm = viewModel(repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertTrue(state.visibleBeneficiaries.isEmpty())
    assertFalse(state.children.isEmpty())
    assertFalse(state.isEmpty)
  }

  @Test
  fun `both mothers and children empty is the screen-level empty state`() = runTest(dispatcher) {
    val repo = TestRepository(mapOf("SushilTest" to VillageRiskDetail("SushilTest", emptyList(), emptyList())))
    val vm = viewModel(repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertTrue(state.isEmpty)
  }

  @Test
  fun `unknown village id resolves to empty result without throwing`() = runTest(dispatcher) {
    val repo = TestRepository(emptyMap())
    val vm = viewModel(villageId = "unknown-village", repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertTrue(state.isEmpty)
  }

  @Test
  fun `repository failure moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(emptyMap(), shouldFail = true)
    val vm = viewModel(repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(vm.uiState.value is VillageRiskDetailUiState.Error)
  }

  @Test
  fun `retry after failure re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(emptyMap(), shouldFail = true)
    val vm = viewModel(repository = repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(vm.uiState.value is VillageRiskDetailUiState.Error)

    repo.shouldSucceedNow(mapOf("SushilTest" to VillageRiskDetail("SushilTest", listOf(mother), listOf(child))))
    vm.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertEquals(1, state.mothers.size)
  }

  @Test
  fun `village id and sakhi name are URL-decoded from nav args`() = runTest(dispatcher) {
    val repo = TestRepository(mapOf("Sushil Test" to VillageRiskDetail("Sushil Test", listOf(mother), emptyList())))
    val vm = viewModel(villageId = "Sushil%20Test", sakhiName = "Sakhi%20Komal", repository = repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = vm.uiState.value as VillageRiskDetailUiState.Success
    assertEquals("Sushil Test", state.villageName)
    assertEquals("Sakhi Komal", state.sakhiName)
  }
}
