package org.armman.supervisor.di

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The access token attached by `AuthInterceptor` must never reach Logcat, even in debug builds
 * where [HttpLoggingInterceptor] logs headers — regression test for that specific leak, not a
 * full test of [NetworkModule]'s Hilt wiring (its `@Provides` methods aren't unit-testable in
 * isolation without an instrumented/Robolectric setup, per this repo's established pattern). */
class NetworkModuleTest {

  /** Returns a canned response without any real network I/O, so this test runs on the plain JVM. */
  private class StubChain(private val original: Request) : Interceptor.Chain {
    override fun request(): Request = original

    override fun proceed(request: Request): Response = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(200)
      .message("OK")
      .body("{}".toResponseBody("application/json".toMediaType()))
      .build()

    override fun connection() = null
    override fun call() = throw UnsupportedOperationException()
    override fun connectTimeoutMillis() = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
    override fun readTimeoutMillis() = 0
    override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
    override fun writeTimeoutMillis() = 0
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
  }

  @Test
  fun `redacted Authorization header is masked, not logged in plaintext`() {
    val logged = StringBuilder()
    val logging = HttpLoggingInterceptor { message -> logged.append(message).append('\n') }.apply {
      level = HttpLoggingInterceptor.Level.HEADERS
      redactHeader("Authorization")
    }
    val request = Request.Builder()
      .url("https://example.armman.org/api/v1/projects")
      .header("Authorization", "Bearer super-secret-access-token")
      .build()

    logging.intercept(StubChain(request))

    assertFalse(logged.toString().contains("super-secret-access-token"))
    assertTrue(logged.toString().contains("Authorization: ██"))
  }
}
