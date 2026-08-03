package org.armman.supervisor.data.assignitem

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

private const val ONE_TIME_WORK_NAME = "inventory_transaction_sync_now"
private const val PERIODIC_WORK_NAME = "inventory_transaction_sync_periodic"
private const val PERIODIC_INTERVAL_MINUTES = 15L
private const val BACKOFF_DELAY_SECONDS = 30L

interface InventoryTransactionSyncScheduler {
  fun syncNow()
  fun ensurePeriodicSyncScheduled()
}

@Singleton
class WorkManagerInventoryTransactionSyncScheduler @Inject constructor(
  private val workManager: WorkManager,
) : InventoryTransactionSyncScheduler {
  private val networkConstraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

  override fun syncNow() {
    val request = OneTimeWorkRequestBuilder<InventoryTransactionSyncWorker>()
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
  }

  override fun ensurePeriodicSyncScheduled() {
    val request = PeriodicWorkRequestBuilder<InventoryTransactionSyncWorker>(
      PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
    )
      .setConstraints(networkConstraints)
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
