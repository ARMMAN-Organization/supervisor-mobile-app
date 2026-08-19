package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** One beneficiary case row, as returned by the bulk `/beneficiaries` list (offline-download
 * shape — only the fields this app needs to count/identify a row). */
data class BeneficiaryDownloadCaseDto(val id: String, val caseType: String)

data class BeneficiaryDownloadPageDto(
  val items: List<BeneficiaryDownloadCaseDto>,
  val nextCursor: String?,
)

data class BeneficiaryDownloadEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: BeneficiaryDownloadPageDto?,
)

/** One inventory transaction row, as returned by the bulk `/inventory-transactions` list (no
 * `sakhiId` filter — every transaction across the caller's scope). */
data class InventoryTransactionRowDto(val id: String, val sakhiId: String, val itemId: String)

data class InventoryTransactionsListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<InventoryTransactionRowDto>?,
)

/** One inventory transaction's detail (currently an alias for the single-transaction fetch — see
 * `docs/BENEFICIARY_DATA_DOWNLOAD_ENDPOINTS.md`), as returned by `/inventory-transactions/{id}/details`. */
data class InventoryTransactionDetailDto(val id: String, val itemId: String, val quantity: Int)

data class InventoryTransactionDetailEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: InventoryTransactionDetailDto?,
)

/** One call log row, as returned by `/sakhi-calls` (currently an alias for
 * `/call-logs/by-sakhi/{sakhiId}` — see `docs/BENEFICIARY_DATA_DOWNLOAD_ENDPOINTS.md`). */
data class SakhiCallDto(val id: String, val sakhiId: String, val callStatus: String)

data class SakhiCallsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<SakhiCallDto>?,
)

/** Retrofit contract for the bulk Beneficiaries List, Sakhi Item Transaction (+ Detail), and Call
 * Details endpoints used by the Beneficiary Data Download flow. Grouped in one file because each
 * is a single simple GET reusing this download flow's own naming, distinct from the per-screen
 * [org.armman.supervisor.data.beneficiaries.BeneficiaryListApi]/
 * [org.armman.supervisor.data.inventory.InventoryApi]/[org.armman.supervisor.data.calllog.CallLogApi]
 * which are scoped/shaped for their own screens, not for a bulk offline download. Paths are
 * relative to `API_BASE_URL` (`.../api/v1/`). */
interface BeneficiaryDownloadListApi {
  @GET("beneficiaries")
  suspend fun getBeneficiariesDownload(
    @Query("limit") limit: Int = 100,
    @Query("cursor") cursor: String? = null,
  ): Response<BeneficiaryDownloadEnvelopeDto>

  @GET("inventory-transactions")
  suspend fun getAllInventoryTransactions(): Response<InventoryTransactionsListEnvelopeDto>

  @GET("inventory-transactions/{id}/details")
  suspend fun getInventoryTransactionDetails(@Path("id") id: String): Response<InventoryTransactionDetailEnvelopeDto>

  @GET("sakhi-calls")
  suspend fun getSakhiCalls(@Query("sakhiId") sakhiId: String): Response<SakhiCallsEnvelopeDto>
}
