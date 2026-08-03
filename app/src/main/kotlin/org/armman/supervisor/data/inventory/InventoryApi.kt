package org.armman.supervisor.data.inventory

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.POST

/** One inventory item as returned by the supervisor-operations-service items master data. */
data class InventoryItemDto(
  val id: String,
  val itemCode: String,
  val itemName: String,
  val itemCategory: String,
  val unit: String,
  val status: String,
)

/** One inventory transaction row as returned/accepted by supervisor-operations-service. A single
 * logical submission with N items maps to N of these rows (one per item). */
data class InventoryTransactionDto(
  val id: String,
  val projectId: String,
  val supervisorId: String,
  val sakhiId: String,
  val itemId: String,
  val transactionType: String,
  val quantity: Int,
  val transactionDate: String,
  val remarks: String?,
  val createdAt: String,
  val updatedAt: String,
)

data class InventoryItemsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<InventoryItemDto>?,
)

data class InventoryTransactionsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<InventoryTransactionDto>?,
)

data class InventoryTransactionEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: InventoryTransactionDto?,
)

data class DeleteTransactionResultDto(val deleted: Boolean)

data class DeleteTransactionEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: DeleteTransactionResultDto?,
)

/** One item + quantity line in a [CreateInventoryTransactionRequest]. */
data class InventoryTransactionItemRequest(val itemId: String, val quantity: Int)

/** Request body for `POST inventory-transactions`. `supervisorId` is never sent — the server
 * always stamps the authenticated caller. */
data class CreateInventoryTransactionRequest(
  val projectId: String,
  val sakhiId: String,
  val transactionType: String,
  val transactionDate: String,
  val remarks: String?,
  val items: List<InventoryTransactionItemRequest>,
)

/** Partial-update body for `PUT inventory-transactions/{id}` — only quantity/date/remarks are
 * mutable server-side; item lines cannot be changed via this endpoint. At least one field must
 * be non-null. */
data class UpdateInventoryTransactionRequest(
  val quantity: Int? = null,
  val transactionDate: String? = null,
  val remarks: String? = null,
)

/** Retrofit contract for inventory items/transactions, owned by supervisor-operations-service.
 * Paths are relative to `API_BASE_URL` (`.../api/v1/`). */
interface InventoryApi {
  @GET("inventory-items")
  suspend fun getInventoryItems(): Response<InventoryItemsEnvelopeDto>

  @GET("inventory-transactions/by-sakhi/{sakhiId}")
  suspend fun getTransactionsBySakhi(@Path("sakhiId") sakhiId: String): Response<InventoryTransactionsEnvelopeDto>

  @POST("inventory-transactions")
  suspend fun createTransaction(
    @Body request: CreateInventoryTransactionRequest,
  ): Response<InventoryTransactionsEnvelopeDto>

  @PUT("inventory-transactions/{id}")
  suspend fun updateTransaction(
    @Path("id") id: String,
    @Body request: UpdateInventoryTransactionRequest,
  ): Response<InventoryTransactionEnvelopeDto>

  @DELETE("inventory-transactions/{id}")
  suspend fun deleteTransaction(@Path("id") id: String): Response<DeleteTransactionEnvelopeDto>
}
