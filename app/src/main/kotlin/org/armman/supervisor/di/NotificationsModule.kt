package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.notifications.NotificationsApi
import org.armman.supervisor.data.notifications.NotificationsRepositoryImpl
import org.armman.supervisor.data.notifications.NotificationsSeenStore
import org.armman.supervisor.data.notifications.SharedPreferencesNotificationsSeenStore
import org.armman.supervisor.ui.notifications.NotificationsRepository
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the Notifications repository interface to its notification-escalation-service-backed
 * implementation, and the seen-notifications marker store. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
  @Binds
  @Singleton
  abstract fun bindNotificationsRepository(impl: NotificationsRepositoryImpl): NotificationsRepository

  @Binds
  @Singleton
  abstract fun bindNotificationsSeenStore(impl: SharedPreferencesNotificationsSeenStore): NotificationsSeenStore

  companion object {
    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)
  }
}
