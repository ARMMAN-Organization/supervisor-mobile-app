package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.events.SupervisorEventsApi
import org.armman.supervisor.data.meetingtraining.GatheringSyncScheduler
import org.armman.supervisor.data.meetingtraining.SupervisorEventSyncScheduler
import org.armman.supervisor.data.meetingtraining.WorkManagerGatheringSyncScheduler
import org.armman.supervisor.data.meetingtraining.WorkManagerSupervisorEventSyncScheduler
import retrofit2.Retrofit
import javax.inject.Singleton

/** Provides the real supervisor-events API and its offline-write-queue schedulers, used by
 * `MeetingTrainingRepositoryImpl` to create meetings/trainings and their Training gatherings.
 * Reads (list/detail) stay local-Room-only — see plan §4 Option A. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SupervisorEventsModule {
  @Binds
  @Singleton
  abstract fun bindSupervisorEventSyncScheduler(
    impl: WorkManagerSupervisorEventSyncScheduler,
  ): SupervisorEventSyncScheduler

  @Binds
  @Singleton
  abstract fun bindGatheringSyncScheduler(impl: WorkManagerGatheringSyncScheduler): GatheringSyncScheduler

  companion object {
    @Provides
    @Singleton
    fun provideSupervisorEventsApi(retrofit: Retrofit): SupervisorEventsApi =
      retrofit.create(SupervisorEventsApi::class.java)
  }
}
