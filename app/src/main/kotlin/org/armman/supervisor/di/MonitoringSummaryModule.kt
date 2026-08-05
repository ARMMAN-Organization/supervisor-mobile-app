package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.monitoringsummary.MonitoringSummaryRepositoryImpl
import org.armman.supervisor.ui.monitoringsummary.MonitoringSummaryRepository

/** Binds the Monitoring Summary detail-screen data source. Per-Sakhi/per-village counts are
 * still local sample data (see [MonitoringSummaryRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringSummaryModule {
  @Binds
  abstract fun bindMonitoringSummaryRepository(impl: MonitoringSummaryRepositoryImpl): MonitoringSummaryRepository
}
