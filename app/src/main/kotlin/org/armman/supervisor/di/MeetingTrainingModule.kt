package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.events.SupervisorEventOperationsApi
import org.armman.supervisor.data.meetingtraining.MeetingTrainingRepositoryImpl
import org.armman.supervisor.ui.meetingtraining.AndroidEventPhotoCleanup
import org.armman.supervisor.ui.meetingtraining.EventPhotoCleanup
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Meeting & Training data source (see [org.armman.supervisor.data.meetingtraining.MeetingTrainingRepositoryImpl]) —
 * projects/Sakhi roster and scheduling are real; attendance/cancel/complete/reschedule/gatherings/
 * marks are wired to the real `supervisor-events` sub-resource endpoints too (see
 * [SupervisorEventOperationsApi]). Photo upload has no backend endpoint yet and stays local-only. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MeetingTrainingModule {
  @Binds
  abstract fun bindMeetingTrainingRepository(impl: MeetingTrainingRepositoryImpl): MeetingTrainingRepository

  @Binds
  @Singleton
  abstract fun bindEventPhotoCleanup(impl: AndroidEventPhotoCleanup): EventPhotoCleanup

  companion object {
    @Provides
    @Singleton
    fun provideSupervisorEventOperationsApi(retrofit: Retrofit): SupervisorEventOperationsApi =
      retrofit.create(SupervisorEventOperationsApi::class.java)
  }
}
