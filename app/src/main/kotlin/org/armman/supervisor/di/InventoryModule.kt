package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.assignitem.InventoryTransactionSyncScheduler
import org.armman.supervisor.data.assignitem.WorkManagerInventoryTransactionSyncScheduler
import org.armman.supervisor.data.inventory.InventoryApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Provides the real inventory items/transactions API and its offline-write-queue scheduler,
 * used by `AssignItemRepositoryImpl`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryModule {
  @Binds
  @Singleton
  abstract fun bindInventoryTransactionSyncScheduler(
    impl: WorkManagerInventoryTransactionSyncScheduler,
  ): InventoryTransactionSyncScheduler

  companion object {
    @Provides
    @Singleton
    fun provideInventoryApi(retrofit: Retrofit): InventoryApi = retrofit.create(InventoryApi::class.java)
  }
}
