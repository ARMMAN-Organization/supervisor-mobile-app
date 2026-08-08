package org.armman.supervisor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.lookups.LookupsApi
import retrofit2.Retrofit
import javax.inject.Singleton

/** Provides [LookupsApi]. [org.armman.supervisor.data.lookups.LookupsRepository] is
 * `@Singleton`-constructor-injected directly — no interface/impl split needed for a single-method
 * cache with no UI-testing fake requirement. */
@Module
@InstallIn(SingletonComponent::class)
object LookupsModule {
  @Provides
  @Singleton
  fun provideLookupsApi(retrofit: Retrofit): LookupsApi = retrofit.create(LookupsApi::class.java)
}
