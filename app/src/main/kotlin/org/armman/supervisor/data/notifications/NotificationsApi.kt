package org.armman.supervisor.data.notifications

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/** One notification as returned by `GET /notifications`. */
data class NotificationDto(
  val id: String,
  val recipientUserId: String,
  val notificationType: String,
  val title: String,
  val body: String?,
  val priority: Int,
  val ctaType: String?,
  val linkedEntityType: String?,
  val linkedEntityId: String?,
  val status: String,
  val readAt: String?,
  val dismissedAt: String?,
  val createdAt: String,
  val updatedAt: String,
)

/** Envelope every api-gateway response uses, success or failure. */
data class NotificationListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<NotificationDto>?,
)

data class NotificationEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: NotificationDto?,
)

/** Request body for `PATCH /notifications/{id}`. */
data class UpdateNotificationStatusRequestDto(
  val status: String,
)

/** Retrofit contract for the Notifications endpoints. Paths are relative to `API_BASE_URL`
 * (`.../api/v1/`). */
interface NotificationsApi {
  @GET("notifications")
  suspend fun getNotifications(): Response<NotificationListEnvelopeDto>

  @PATCH("notifications/{id}")
  suspend fun updateNotificationStatus(
    @Path("id") id: String,
    @Body request: UpdateNotificationStatusRequestDto,
  ): Response<NotificationEnvelopeDto>
}
