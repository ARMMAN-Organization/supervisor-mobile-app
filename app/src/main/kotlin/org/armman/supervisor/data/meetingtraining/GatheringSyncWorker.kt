package org.armman.supervisor.data.meetingtraining

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Thin WorkManager adapter for [GatheringSyncExecutor] — mirrors [SupervisorEventSyncWorker]. */
@HiltWorker
class GatheringSyncWorker @AssistedInject constructor(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val executor: GatheringSyncExecutor,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result =
    when (executor.run()) {
      GatheringSyncOutcome.COMPLETED -> Result.success()
      GatheringSyncOutcome.RETRYABLE_FAILURE -> Result.retry()
    }
}
