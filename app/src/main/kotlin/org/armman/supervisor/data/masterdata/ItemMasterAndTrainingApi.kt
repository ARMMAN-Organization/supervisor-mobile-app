package org.armman.supervisor.data.masterdata

import retrofit2.Response
import retrofit2.http.GET

/** One inventory item, as returned by the item master list endpoint. */
data class ItemMasterDto(
  val id: String,
  val itemCode: String,
  val itemName: String,
  val itemCategory: String,
  val unit: String,
  val status: String,
)

data class ItemMasterListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<ItemMasterDto>?,
)

/** One training topic, as returned by the training topics endpoint. */
data class TrainingTopicDto(
  val id: String,
  val topicCode: String,
  val topicName: String,
  val status: String,
)

data class TrainingTopicsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<TrainingTopicDto>?,
)

/** Retrofit contract for Item Master List and Training Topics — flat-array master data that
 * doesn't fit the category/value shape [MasterDataCategoryApi] covers. Paths are relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface ItemMasterAndTrainingApi {
  @GET("item-master-list")
  suspend fun getItemMasterList(): Response<ItemMasterListEnvelopeDto>

  @GET("training-topics")
  suspend fun getTrainingTopics(): Response<TrainingTopicsEnvelopeDto>
}
