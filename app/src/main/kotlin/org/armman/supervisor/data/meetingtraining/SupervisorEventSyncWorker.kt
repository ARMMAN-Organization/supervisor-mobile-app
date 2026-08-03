package org.armman.supervisor.data.meetingtraining

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Thin WorkManager adapter — deliberately trivial and untested, matching
 * [org.armman.supervisor.data.assignitem.InventoryTransactionSyncWorker]'s convention. */
@HiltWorker
class SupervisorEventSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val executor: SupervisorEventSyncExecutor,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result =
    when (executor.run()) {
      SupervisorEventSyncOutcome.COMPLETED -> Result.success()
      SupervisorEventSyncOutcome.RETRYABLE_FAILURE -> Result.retry()
    }
}
