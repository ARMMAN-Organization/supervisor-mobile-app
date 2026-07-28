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

/** Binds the Meeting & Training data source. Currently local sample data; swaps to network calls in place. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MeetingTrainingModule {
  @Binds
  abstract fun bindMeetingTrainingRepository(impl: MeetingTrainingRepositoryImpl): MeetingTrainingRepository

  @Binds
  @Singleton
  abstract fun bindEventPhotoCleanup(impl: AndroidEventPhotoCleanup): EventPhotoCleanup
}
