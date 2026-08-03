package org.armman.supervisor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.assignitem.InventoryTransactionSyncScheduler
import org.armman.supervisor.data.meetingtraining.SupervisorEventSyncScheduler
import javax.inject.Inject

/** Application entry point; enables Hilt dependency injection app-wide. */
@HiltAndroidApp
class SupervisorApplication : Application(), Configuration.Provider {

  // Only field injected on the Application itself: WorkManager.getInstance() is triggered
  // synchronously the moment ANY @Inject Application field whose graph touches WorkManager is
  // set, and that happens mid-member-injection, before later fields in this class are assigned.
  // Fetching the schedulers via an EntryPoint below — a separate lookup run only after
  // super.onCreate() returns — avoids that ordering trap entirely.
  @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface SyncSchedulersEntryPoint {
    fun inventoryTransactionSyncScheduler(): InventoryTransactionSyncScheduler
    fun supervisorEventSyncScheduler(): SupervisorEventSyncScheduler
  }

  override fun onCreate() {
    super.onCreate()
    val entryPoint = EntryPointAccessors.fromApplication(this, SyncSchedulersEntryPoint::class.java)
    // Standing safety net for the offline write-queues: WorkManager's own NetworkType.CONNECTED
    // constraint on this periodic job is what actually delivers reconnect responsiveness, not
    // this call site — see InventoryTransactionSyncScheduler/SupervisorEventSyncScheduler.
    entryPoint.inventoryTransactionSyncScheduler().ensurePeriodicSyncScheduled()
    entryPoint.supervisorEventSyncScheduler().ensurePeriodicSyncScheduled()
  }
}
