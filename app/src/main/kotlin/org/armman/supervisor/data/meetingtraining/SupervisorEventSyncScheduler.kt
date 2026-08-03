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

private const val ONE_TIME_WORK_NAME = "supervisor_event_sync_now"
private const val PERIODIC_WORK_NAME = "supervisor_event_sync_periodic"
private const val PERIODIC_INTERVAL_MINUTES = 15L
private const val BACKOFF_DELAY_SECONDS = 30L

interface SupervisorEventSyncScheduler {
  fun syncNow()
  fun ensurePeriodicSyncScheduled()
}

@Singleton
class WorkManagerSupervisorEventSyncScheduler @Inject constructor(
  private val workManager: WorkManager,
) : SupervisorEventSyncScheduler {
  private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

  override fun syncNow() {
    val request = OneTimeWorkRequestBuilder<SupervisorEventSyncWorker>()
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
  }

  override fun ensurePeriodicSyncScheduled() {
    val request = PeriodicWorkRequestBuilder<SupervisorEventSyncWorker>(
      PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
    )
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
