package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.visitsummary.VisitSummaryRepositoryImpl
import org.armman.supervisor.ui.visitsummary.VisitSummaryRepository

/** Binds the Visit Summary detail-screen data source. Per-Sakhi/per-village counts are still
 * local sample data (see [VisitSummaryRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class VisitSummaryModule {
  @Binds
  abstract fun bindVisitSummaryRepository(impl: VisitSummaryRepositoryImpl): VisitSummaryRepository
}
