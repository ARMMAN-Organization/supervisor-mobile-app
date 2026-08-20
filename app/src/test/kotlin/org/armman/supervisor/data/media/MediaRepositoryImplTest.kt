package org.armman.supervisor.data.media

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import retrofit2.Response as RetrofitResponse

private class FakeMediaApi : MediaApi {
  var uploadUrl = "https://s3.example.com/presigned"
  var s3Key = "training_photo/abc"
  var maxSizeBytes = 26_214_400L
  var uploadUrlErrorResponse: RetrofitResponse<UploadUrlEnvelopeDto>? = null
  var finalizeErrorResponse: RetrofitResponse<MediaEnvelopeDto>? = null
  var finalizedMediaId = "media-1"

  var requestUploadUrlCallCount = 0
    private set
  var finalizeCallCount = 0
    private set
  var lastFinalizeRequest: FinalizeMediaRequest? = null

  override suspend fun requestUploadUrl(request: UploadUrlRequest): RetrofitResponse<UploadUrlEnvelopeDto> {
    requestUploadUrlCallCount++
    uploadUrlErrorResponse?.let { return it }
    return RetrofitResponse.success(
      UploadUrlEnvelopeDto(
        success = true,
        message = "OK",
        data = UploadUrlDto(uploadUrl = uploadUrl, s3Key = s3Key, expiresInSeconds = 900, maxSizeBytes = maxSizeBytes),
      ),
    )
  }

  override suspend fun finalizeMedia(request: FinalizeMediaRequest): RetrofitResponse<MediaEnvelopeDto> {
    finalizeCallCount++
    lastFinalizeRequest = request
    finalizeErrorResponse?.let { return it }
    return RetrofitResponse.success(MediaEnvelopeDto(success = true, message = "OK", data = MediaDto(finalizedMediaId)))
  }
}

/** Stands in for the real S3 endpoint so [MediaRepositoryImplTest] doesn't need a live server or a
 * new test dependency — intercepts every call before it leaves the process and returns [s3Code]. */
private class FakeS3Interceptor(var s3Code: Int = 200) : okhttp3.Interceptor {
  var putCallCount = 0
    private set
  var lastMethod: String? = null

  override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
    putCallCount++
    lastMethod = chain.request().method
    return Response.Builder()
      .request(chain.request())
      .protocol(okhttp3.Protocol.HTTP_1_1)
      .code(s3Code)
      .message(if (s3Code in 200..299) "OK" else "Error")
      .body("".toResponseBody(null))
      .build()
  }
}

class MediaRepositoryImplTest {
  private lateinit var api: FakeMediaApi
  private lateinit var s3Interceptor: FakeS3Interceptor
  private lateinit var repository: MediaRepositoryImpl
  private lateinit var photoFile: File

  @Before
  fun setUp() {
    api = FakeMediaApi()
    s3Interceptor = FakeS3Interceptor()
    val client = OkHttpClient.Builder().addInterceptor(s3Interceptor).build()
    repository = MediaRepositoryImpl(api, client)
    photoFile = File.createTempFile("photo", ".jpg").apply { writeBytes(ByteArray(10)) }
  }

  @After
  fun tearDown() {
    photoFile.delete()
  }

  @Test
  fun `uploadPhoto runs upload-url, S3 PUT, then finalize in order and returns the media id`() = runTest {
    val mediaId = repository.uploadPhoto(photoFile.path)

    assertEquals("media-1", mediaId)
    assertEquals(1, api.requestUploadUrlCallCount)
    assertEquals(1, s3Interceptor.putCallCount)
    assertEquals("PUT", s3Interceptor.lastMethod)
    assertEquals(1, api.finalizeCallCount)
    assertEquals(api.s3Key, api.lastFinalizeRequest?.s3Key)
    assertEquals(photoFile.length(), api.lastFinalizeRequest?.expectedSizeBytes)
  }

  @Test(expected = IllegalStateException::class)
  fun `uploadPhoto throws when the file does not exist`() = runTest {
    repository.uploadPhoto("/no/such/file.jpg")
  }

  @Test
  fun `uploadPhoto throws when requesting the upload URL fails, without attempting S3 or finalize`() = runTest {
    api.uploadUrlErrorResponse = RetrofitResponse.error(500, "{}".toResponseBody(null))

    val result = runCatching { repository.uploadPhoto(photoFile.path) }

    assertTrue(result.isFailure)
    assertEquals(0, s3Interceptor.putCallCount)
    assertEquals(0, api.finalizeCallCount)
  }

  @Test
  fun `uploadPhoto throws when the file exceeds maxSizeBytes, before any network call`() = runTest {
    api.maxSizeBytes = 1

    val result = runCatching { repository.uploadPhoto(photoFile.path) }

    assertTrue(result.isFailure)
    assertEquals(0, s3Interceptor.putCallCount)
    assertEquals(0, api.finalizeCallCount)
  }

  @Test
  fun `uploadPhoto throws when the S3 PUT itself fails, without attempting finalize`() = runTest {
    s3Interceptor.s3Code = 500

    val result = runCatching { repository.uploadPhoto(photoFile.path) }

    assertTrue(result.isFailure)
    assertEquals(0, api.finalizeCallCount)
  }

  @Test
  fun `uploadPhoto throws when finalize fails after a successful S3 upload`() = runTest {
    api.finalizeErrorResponse = RetrofitResponse.success(MediaEnvelopeDto(success = false, message = "Upload not found", data = null))

    val result = runCatching { repository.uploadPhoto(photoFile.path) }

    assertTrue(result.isFailure)
    assertEquals(1, s3Interceptor.putCallCount)
    assertEquals(1, api.finalizeCallCount)
  }

  @Test
  fun `a fresh call fetches a new presigned URL rather than reusing one from an earlier attempt`() = runTest {
    s3Interceptor.s3Code = 500
    runCatching { repository.uploadPhoto(photoFile.path) }
    assertEquals(1, api.requestUploadUrlCallCount)

    s3Interceptor.s3Code = 200
    repository.uploadPhoto(photoFile.path)

    assertEquals(2, api.requestUploadUrlCallCount)
  }
}
