package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.dashboard.DashboardApi
import org.armman.supervisor.data.dashboard.DashboardRepositoryImpl
import org.armman.supervisor.ui.dashboard.DashboardRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the dashboard data source (see [DashboardRepositoryImpl]). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {
  @Binds
  abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

  companion object {
    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)
  }
}
