package org.armman.supervisor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.calllog.CallLogApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Provides the real call-logs API, backed by supervisor-operations-service. */
@Module
@InstallIn(SingletonComponent::class)
object CallLogModule {
  @Provides
  @Singleton
  fun provideCallLogApi(retrofit: Retrofit): CallLogApi = retrofit.create(CallLogApi::class.java)
}
