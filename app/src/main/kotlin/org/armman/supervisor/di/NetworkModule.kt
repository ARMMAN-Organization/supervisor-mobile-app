package org.armman.supervisor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.auth.session.AuthInterceptor
import org.armman.supervisor.data.auth.session.TokenAuthenticator
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private const val TIMEOUT_SECONDS = 30L

/** Marks the plain, un-authenticated OkHttpClient [TokenAuthenticator] uses to call
 * `auth/refresh` directly — it must never carry [AuthInterceptor] (which would attach the stale
 * token being refreshed) or [TokenAuthenticator] itself (which would recurse back into itself on
 * a 401). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UnauthenticatedClient

/** Provides the Retrofit client pointing at the API Gateway. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  private fun loggingInterceptor(): HttpLoggingInterceptor =
    // Never log request/response bodies in release — they may carry PII or tokens. Even in
    // debug, the Authorization header itself must never reach Logcat.
    HttpLoggingInterceptor().apply {
      level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
      } else {
        HttpLoggingInterceptor.Level.NONE
      }
      redactHeader("Authorization")
    }

  @Provides
  @Singleton
  fun provideOkHttp(authInterceptor: AuthInterceptor, tokenAuthenticator: TokenAuthenticator): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .addInterceptor(authInterceptor)
      .authenticator(tokenAuthenticator)
      .addInterceptor(loggingInterceptor())
      .build()

  @Provides
  @Singleton
  fun provideRetrofit(client: OkHttpClient): Retrofit =
    Retrofit.Builder()
      .baseUrl(BuildConfig.API_BASE_URL)
      .client(client)
      .addConverterFactory(GsonConverterFactory.create())
      .build()

  @Provides
  @Singleton
  @UnauthenticatedClient
  fun provideUnauthenticatedOkHttp(): OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .addInterceptor(loggingInterceptor())
      .build()
}
