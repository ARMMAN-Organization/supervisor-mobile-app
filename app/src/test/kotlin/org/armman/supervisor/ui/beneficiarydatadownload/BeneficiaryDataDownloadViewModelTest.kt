package org.armman.supervisor.ui.beneficiarydatadownload

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataRepository
import org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataResult
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BeneficiaryDataDownloadViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  /** Queues one result per call, in order; entities not queued fall back to [default]. */
  private class FakeBeneficiaryDataRepository(
    private val default: BeneficiaryDataResult = BeneficiaryDataResult.Success(1),
  ) : BeneficiaryDataRepository {
    val calls = mutableListOf<BeneficiaryDataEntity>()
    private val queuedResults = ArrayDeque<BeneficiaryDataResult>()

    fun enqueue(result: BeneficiaryDataResult) {
      queuedResults.addLast(result)
    }

    override suspend fun download(entity: BeneficiaryDataEntity): BeneficiaryDataResult {
      calls.add(entity)
      return if (queuedResults.isNotEmpty()) queuedResults.removeFirst() else default
    }
  }

  private class FakeConnectivityChecker(var online: Boolean = true) : ConnectivityChecker {
    override fun isOnline(): Boolean = online
  }

  private lateinit var repository: FakeBeneficiaryDataRepository
  private lateinit var connectivityChecker: FakeConnectivityChecker

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeBeneficiaryDataRepository()
    connectivityChecker = FakeConnectivityChecker()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(): BeneficiaryDataDownloadViewModel =
    BeneficiaryDataDownloadViewModel(repository, connectivityChecker)

  private fun BeneficiaryDataDownloadViewModel.content(): BeneficiaryDataDownloadUiState.Content =
    uiState.value as BeneficiaryDataDownloadUiState.Content

  @Test
  fun `all rows start Pending before the dispatcher is advanced`() {
    val viewModel = createViewModel()

    // StandardTestDispatcher runs no coroutine body (including the init-block download) until
    // explicitly advanced, so every row is still at its initial Pending status here.
    val state = viewModel.content()
    assertEquals(BeneficiaryDataEntity.entries.size, state.rows.size)
    assertTrue(state.rows.all { it.status == BeneficiaryDataRowStatus.PENDING })
  }

  @Test
  fun `successful download marks a row Completed and advances to the next`() {
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.content()
    assertEquals(BeneficiaryDataRowStatus.COMPLETED, state.rows[0].status)
  }

  @Test
  fun `empty result marks a row Empty and the chain continues`() {
    repository.enqueue(BeneficiaryDataResult.Empty)
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.content()
    assertEquals(BeneficiaryDataRowStatus.EMPTY, state.rows[0].status)
    assertEquals(BeneficiaryDataRowStatus.COMPLETED, state.rows[1].status)
  }

  @Test
  fun `not-available result is marked distinctly and does not block the chain`() {
    repository.enqueue(BeneficiaryDataResult.NotAvailable)
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(BeneficiaryDataRowStatus.NOT_AVAILABLE, viewModel.content().rows[0].status)
    assertEquals(BeneficiaryDataRowStatus.COMPLETED, viewModel.content().rows[1].status)
    assertTrue(viewModel.content().showCompletionDialog)
  }

  @Test
  fun `all rows finishing shows the completion dialog`() {
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.content().showCompletionDialog)
    assertEquals(-1, viewModel.content().activeIndex)
  }

  @Test
  fun `network failure mid-chain shows the network error dialog and halts further calls`() {
    repository.enqueue(BeneficiaryDataResult.Success(1))
    repository.enqueue(BeneficiaryDataResult.Failure(RuntimeException("no network")))
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.content().showNetworkErrorDialog)
    assertFalse(viewModel.content().showCompletionDialog)
    assertEquals(2, repository.calls.size)
  }

  @Test
  fun `retry restarts the whole chain from the first row`() {
    repository.enqueue(BeneficiaryDataResult.Failure(RuntimeException("no network")))
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.content().showNetworkErrorDialog)
    repository.calls.clear()

    viewModel.onRetryClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.content().showNetworkErrorDialog)
    assertEquals(BeneficiaryDataEntity.entries.first(), repository.calls.first())
    assertTrue(viewModel.content().showCompletionDialog)
  }

  @Test
  fun `stop dismisses the network error dialog without resuming`() {
    repository.enqueue(BeneficiaryDataResult.Failure(RuntimeException("no network")))
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onStopClicked()

    assertFalse(viewModel.content().showNetworkErrorDialog)
    assertFalse(viewModel.content().showCompletionDialog)
  }

  @Test
  fun `no network at start shows the network error dialog immediately`() {
    connectivityChecker.online = false
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.content().showNetworkErrorDialog)
    assertTrue(repository.calls.isEmpty())
  }

  @Test
  fun `back requested while downloading shows quit confirmation`() {
    val viewModel = createViewModel()

    viewModel.onBackRequested()

    assertTrue(viewModel.content().showQuitConfirmation)
  }

  @Test
  fun `quit confirmed invokes the callback and hides the dialog`() {
    val viewModel = createViewModel()
    var quit = false

    viewModel.onBackRequested()
    viewModel.onQuitConfirmed { quit = true }

    assertTrue(quit)
    assertFalse(viewModel.content().showQuitConfirmation)
  }

  @Test
  fun `quit dismissed keeps the dialog closed without invoking a callback`() {
    val viewModel = createViewModel()

    viewModel.onBackRequested()
    viewModel.onQuitDismissed()

    assertFalse(viewModel.content().showQuitConfirmation)
  }

  @Test
  fun `completion acknowledged invokes the callback and hides the dialog`() {
    val viewModel = createViewModel()
    dispatcher.scheduler.advanceUntilIdle()
    var done = false

    viewModel.onCompletionAcknowledged { done = true }

    assertTrue(done)
    assertFalse(viewModel.content().showCompletionDialog)
  }

  @Test
  fun `isDownloading is true only while a row is active and no error dialog is showing`() {
    // A deferred that never completes suspends the first row's download indefinitely, letting the
    // test observe the mid-chain state deterministically instead of racing a synchronous fake.
    val neverCompletes = CompletableDeferred<BeneficiaryDataResult>()
    val suspendingRepository = object : BeneficiaryDataRepository {
      var callCount = 0
      override suspend fun download(entity: BeneficiaryDataEntity): BeneficiaryDataResult {
        callCount++
        return neverCompletes.await()
      }
    }
    val viewModel = BeneficiaryDataDownloadViewModel(suspendingRepository, connectivityChecker)

    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, suspendingRepository.callCount)
    assertTrue(viewModel.content().isDownloading)
  }

  @Test
  fun `isDownloading is false once every row has finished`() {
    val viewModel = createViewModel()

    dispatcher.scheduler.advanceUntilIdle()

    assertFalse(viewModel.content().isDownloading)
  }
}
