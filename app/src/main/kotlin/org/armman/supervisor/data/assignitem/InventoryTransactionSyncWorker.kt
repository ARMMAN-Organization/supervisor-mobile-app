package org.armman.supervisor.data.assignitem

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Thin WorkManager adapter — deliberately trivial and untested (needs a real Context/
 * WorkerParameters this repo's JVM-only test setup can't construct); all logic lives in
 * [TransactionSyncExecutor], which is fully unit-tested. */
@HiltWorker
class InventoryTransactionSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val executor: TransactionSyncExecutor,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result =
    when (executor.run()) {
      TransactionSyncOutcome.COMPLETED -> Result.success()
      TransactionSyncOutcome.RETRYABLE_FAILURE -> Result.retry()
    }
}
