package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.media.MediaApi
import org.armman.supervisor.data.media.MediaRepository
import org.armman.supervisor.data.media.MediaRepositoryImpl
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Meeting/Training completion photo upload flow. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
  @Binds
  @Singleton
  abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

  companion object {
    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)
  }
}
