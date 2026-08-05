package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.risksummary.RiskSummaryRepositoryImpl
import org.armman.supervisor.ui.risksummary.RiskSummaryRepository

/** Binds the Risk Summary detail-screen data source. Per-Sakhi/per-village counts are still
 * local sample data (see [RiskSummaryRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RiskSummaryModule {
  @Binds
  abstract fun bindRiskSummaryRepository(impl: RiskSummaryRepositoryImpl): RiskSummaryRepository
}
