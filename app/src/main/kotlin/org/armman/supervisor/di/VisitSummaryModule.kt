package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.visitsummary.VisitApi
import org.armman.supervisor.data.visitsummary.VisitSummaryRepositoryImpl
import org.armman.supervisor.ui.visitsummary.VisitSummaryRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Visit Summary detail-screen data source (see [VisitSummaryRepositoryImpl]). Reuses
 * [org.armman.supervisor.data.beneficiaries.BeneficiaryListApi], provided by
 * [BeneficiaryListModule]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class VisitSummaryModule {
  @Binds
  abstract fun bindVisitSummaryRepository(impl: VisitSummaryRepositoryImpl): VisitSummaryRepository

  companion object {
    @Provides
    @Singleton
    fun provideVisitApi(retrofit: Retrofit): VisitApi = retrofit.create(VisitApi::class.java)
  }
}
