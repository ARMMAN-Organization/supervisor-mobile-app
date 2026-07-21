package org.armman.supervisor.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.armman.supervisor.data.auth.AuthApi
import org.armman.supervisor.data.auth.AuthRepository
import org.armman.supervisor.data.auth.RemoteAuthRepository
import org.armman.supervisor.data.auth.session.EncryptedSharedPreferencesStore
import org.armman.supervisor.data.auth.session.SecureKeyValueStore
import org.armman.supervisor.data.connectivity.AndroidConnectivityChecker
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the real auth-service-backed implementation and its collaborators. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: RemoteAuthRepository): AuthRepository

  @Binds
  @Singleton
  abstract fun bindConnectivityChecker(impl: AndroidConnectivityChecker): ConnectivityChecker

  @Binds
  @Singleton
  abstract fun bindSecureKeyValueStore(impl: EncryptedSharedPreferencesStore): SecureKeyValueStore

  companion object {
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
  }
}
