package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.assignitem.AssignItemRepositoryImpl
import org.armman.supervisor.ui.assignitem.AssignItemRepository

/** Binds the Assign Item data source. Currently local sample data; swaps to network calls in place. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssignItemModule {
  @Binds
  abstract fun bindAssignItemRepository(impl: AssignItemRepositoryImpl): AssignItemRepository
}
