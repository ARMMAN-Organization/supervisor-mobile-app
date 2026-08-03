package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.meetingtraining.MeetingTrainingRepositoryImpl
import org.armman.supervisor.ui.meetingtraining.AndroidEventPhotoCleanup
import org.armman.supervisor.ui.meetingtraining.EventPhotoCleanup
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingRepository
import javax.inject.Singleton

/** Binds the Meeting & Training data source (see [org.armman.supervisor.data.meetingtraining.MeetingTrainingRepositoryImpl]) —
 * projects/Sakhi roster are real, and scheduling posts to the real supervisor-events API;
 * attendance/marks/photos have no backend endpoint yet and stay local-only. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MeetingTrainingModule {
  @Binds
  abstract fun bindMeetingTrainingRepository(impl: MeetingTrainingRepositoryImpl): MeetingTrainingRepository

  @Binds
  @Singleton
  abstract fun bindEventPhotoCleanup(impl: AndroidEventPhotoCleanup): EventPhotoCleanup
}
