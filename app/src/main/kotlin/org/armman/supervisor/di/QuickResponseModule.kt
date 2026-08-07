package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.quickresponse.QuickResponseApi
import org.armman.supervisor.data.quickresponse.QuickResponseRepositoryImpl
import org.armman.supervisor.ui.quickresponse.QuickResponseRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Quick Response repository interface to its approval-service-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class QuickResponseModule {
  @Binds
  @Singleton
  abstract fun bindQuickResponseRepository(impl: QuickResponseRepositoryImpl): QuickResponseRepository

  companion object {
    @Provides
    @Singleton
    fun provideQuickResponseApi(retrofit: Retrofit): QuickResponseApi = retrofit.create(QuickResponseApi::class.java)
  }
}
