package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl
import org.armman.supervisor.ui.assignitem.AssignItemRepository

/** Binds the Assign Item data source (see [org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl]) —
 * projects/Sakhis, the item catalog, and transactions are all backed by real APIs, with the local
 * database as an offline-read cache. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssignItemModule {
  @Binds
  abstract fun bindAssignItemRepository(impl: AssignItemRepositoryImpl): AssignItemRepository
}
