package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl
import org.armman.supervisor.ui.assignitem.AssignItemRepository

/** Binds the Assign Item data source. Projects/Sakhis are real (see [org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl]);
 * the item catalog and transactions are still local. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssignItemModule {
  @Binds
  abstract fun bindAssignItemRepository(impl: AssignItemRepositoryImpl): AssignItemRepository
}
