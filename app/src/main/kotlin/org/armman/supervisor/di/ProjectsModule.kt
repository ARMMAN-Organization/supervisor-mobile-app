package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.projects.ProjectsApi
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.data.projects.ProjectsRepositoryImpl
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the real project/Sakhi master data source, shared by Dashboard and Assign Item. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProjectsModule {
  @Binds
  @Singleton
  abstract fun bindProjectsRepository(impl: ProjectsRepositoryImpl): ProjectsRepository

  companion object {
    @Provides
    @Singleton
    fun provideProjectsApi(retrofit: Retrofit): ProjectsApi = retrofit.create(ProjectsApi::class.java)
  }
}
