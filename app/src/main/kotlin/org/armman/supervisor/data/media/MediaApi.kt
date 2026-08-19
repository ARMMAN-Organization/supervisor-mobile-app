package org.armman.supervisor.data.media

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** `assetType` values accepted by `POST /media/upload-url` and `POST /media` — this app only ever
 * uploads Meeting/Training completion photos, and the backend has no dedicated `MEETING_PHOTO`
 * type, so `TRAINING_PHOTO` is used for both event types (confirmed with backend). */
object MediaAssetType {
  const val TRAINING_PHOTO = "TRAINING_PHOTO"
}

data class UploadUrlRequest(val assetType: String, val mimeType: String, val sizeBytes: Long)

data class UploadUrlDto(
  val uploadUrl: String,
  val s3Key: String,
  val expiresInSeconds: Int,
  val maxSizeBytes: Long,
)

data class UploadUrlEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: UploadUrlDto?,
)

data class FinalizeMediaRequest(
  val assetType: String,
  val s3Key: String,
  val expectedSizeBytes: Long,
  val encryptedFlag: Boolean = true,
)

data class MediaDto(val id: String)

data class MediaEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: MediaDto?,
)

/** Retrofit contract for the media upload flow, confirmed live on `API_BASE_URL`
 * (`.../api/v1/`): request a presigned S3 upload URL, then finalize the upload by registering its
 * metadata and getting back a `mediaId`. The actual S3 PUT is a separate unauthenticated call —
 * see [MediaRepository] — since it targets an absolute presigned URL, not this API's base URL. */
interface MediaApi {
  @POST("media/upload-url")
  suspend fun requestUploadUrl(@Body request: UploadUrlRequest): Response<UploadUrlEnvelopeDto>

  @POST("media")
  suspend fun finalizeMedia(@Body request: FinalizeMediaRequest): Response<MediaEnvelopeDto>
}
