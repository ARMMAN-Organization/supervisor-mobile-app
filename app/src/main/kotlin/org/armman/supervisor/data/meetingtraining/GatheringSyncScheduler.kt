package org.armman.supervisor.data.meetingtraining

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val ONE_TIME_WORK_NAME = "gathering_sync_now"
private const val PERIODIC_WORK_NAME = "gathering_sync_periodic"
private const val PERIODIC_INTERVAL_MINUTES = 15L
private const val BACKOFF_DELAY_SECONDS = 30L

/** Mirrors [SupervisorEventSyncScheduler] — a separate periodic job rather than folding into the
 * event sync worker, since a gathering's own retry timing is independent of its parent event's
 * (a gathering can be stuck AWAITING_PARENT_EVENT for a while after the event itself is fine). */
interface GatheringSyncScheduler {
  fun syncNow()
  fun ensurePeriodicSyncScheduled()
}

@Singleton
class WorkManagerGatheringSyncScheduler @Inject constructor(
  private val workManager: WorkManager,
) : GatheringSyncScheduler {
  private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

  override fun syncNow() {
    val request = OneTimeWorkRequestBuilder<GatheringSyncWorker>()
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
  }

  override fun ensurePeriodicSyncScheduled() {
    val request = PeriodicWorkRequestBuilder<GatheringSyncWorker>(
      PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
    )
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
