package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.dashboard.DashboardRepositoryImpl
import org.armman.supervisor.ui.dashboard.DashboardRepository

/** Binds the dashboard data source. Currently local sample data; swaps to network calls in place. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
  @Binds
  abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository
}
