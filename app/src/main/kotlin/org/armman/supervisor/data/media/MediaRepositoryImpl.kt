package org.armman.supervisor.data.media

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.armman.supervisor.data.auth.ErrorResponseDto
import org.armman.supervisor.di.UnauthenticatedClient
import java.io.File
import javax.inject.Inject

private const val DEFAULT_MIME_TYPE = "image/jpeg"

/**
 * Uploads a Meeting/Training completion photo end to end: request a presigned S3 URL, PUT the
 * file's bytes directly to S3 (never through the API Gateway), then finalize via `POST /media` to
 * get a `mediaId` the event can be completed with. [s3HttpClient] is the plain, unauthenticated
 * client — S3 presigned URLs reject requests carrying an unexpected `Authorization` header.
 */
class MediaRepositoryImpl @Inject constructor(
  private val mediaApi: MediaApi,
  @UnauthenticatedClient private val s3HttpClient: OkHttpClient,
) : MediaRepository {

  private val gson = Gson()

  override suspend fun uploadPhoto(filePath: String, assetType: String): String {
    val file = File(filePath)
    check(file.exists()) { "Photo file does not exist: $filePath" }
    val sizeBytes = file.length()

    val uploadUrlResponse = mediaApi.requestUploadUrl(UploadUrlRequest(assetType, DEFAULT_MIME_TYPE, sizeBytes))
    if (!uploadUrlResponse.isSuccessful) error(errorMessage(uploadUrlResponse.errorBody()?.string(), "request an upload URL"))
    val uploadUrlBody = uploadUrlResponse.body() ?: error("Empty upload-url response")
    if (!uploadUrlBody.success) error(uploadUrlBody.message ?: "Failed to request an upload URL")
    val uploadUrl = uploadUrlBody.data ?: error("Empty upload-url payload")

    check(sizeBytes <= uploadUrl.maxSizeBytes) {
      "Photo is too large: $sizeBytes bytes (max ${uploadUrl.maxSizeBytes})"
    }

    putFileToS3(uploadUrl.uploadUrl, file)

    val finalizeResponse = mediaApi.finalizeMedia(
      FinalizeMediaRequest(assetType = assetType, s3Key = uploadUrl.s3Key, expectedSizeBytes = sizeBytes),
    )
    if (!finalizeResponse.isSuccessful) error(errorMessage(finalizeResponse.errorBody()?.string(), "finalize the photo upload"))
    val finalizeBody = finalizeResponse.body() ?: error("Empty finalize-media response")
    if (!finalizeBody.success) error(finalizeBody.message ?: "Failed to finalize the photo upload")
    return finalizeBody.data?.id ?: error("Finalize-media response had no media id")
  }

  private suspend fun putFileToS3(uploadUrl: String, file: File) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
      .url(uploadUrl)
      .put(file.asRequestBody(DEFAULT_MIME_TYPE.toMediaType()))
      .build()
    s3HttpClient.newCall(request).execute().use { response ->
      check(response.isSuccessful) { "Failed to upload photo to storage: HTTP ${response.code}" }
    }
  }

  private fun errorMessage(errorJson: String?, action: String): String {
    val parsed = errorJson?.let { runCatching { gson.fromJson(it, ErrorResponseDto::class.java) }.getOrNull() }
    val detail = parsed?.message ?: parsed?.errorCode
    return if (detail != null) "Failed to $action: $detail" else "Failed to $action"
  }
}
