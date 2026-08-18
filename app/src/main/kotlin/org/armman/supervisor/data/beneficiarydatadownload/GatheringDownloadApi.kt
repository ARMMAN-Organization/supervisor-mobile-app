package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** One gathering (Training session), as returned by `/gatherings`. */
data class GatheringDto(val id: String, val eventId: String, val gatheringDate: String, val status: String)

data class GatheringsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<GatheringDto>?,
)

/** One pre/post training mark row, as returned by `/gatherings/{id}/training-marks`. */
data class GatheringTrainingMarkDto(val id: String, val gatheringId: String, val sakhiId: String, val markType: String)

data class GatheringTrainingMarksEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<GatheringTrainingMarkDto>?,
)

/** One gathering photo, as returned nested under `/gatherings/{id}/images`. */
data class GatheringPhotoDto(val id: String, val mediaId: String)

/** A gathering's photos (completion photo + gallery) — not a flat list, so its own "record count"
 * is `photos.size`. */
data class GatheringImagesDto(
  val gatheringId: String,
  val completionPhotoMediaId: String?,
  val photos: List<GatheringPhotoDto>,
)

data class GatheringImagesEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: GatheringImagesDto?,
)

/** Retrofit contract for the Gathering List / Training Marks / Images endpoints owned by
 * Supervisor Operations service, built specifically for the Beneficiary Data Download flow.
 * Paths are relative to `API_BASE_URL` (`.../api/v1/`). */
interface GatheringDownloadApi {
  @GET("gatherings")
  suspend fun getGatherings(@Query("sakhiId") sakhiId: String): Response<GatheringsEnvelopeDto>

  @GET("gatherings/{gatheringId}/training-marks")
  suspend fun getTrainingMarks(@Path("gatheringId") gatheringId: String): Response<GatheringTrainingMarksEnvelopeDto>

  @GET("gatherings/{gatheringId}/images")
  suspend fun getGatheringImages(@Path("gatheringId") gatheringId: String): Response<GatheringImagesEnvelopeDto>
}
