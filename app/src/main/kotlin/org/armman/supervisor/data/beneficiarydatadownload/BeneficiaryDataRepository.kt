package org.armman.supervisor.data.beneficiarydatadownload

import org.armman.supervisor.ui.beneficiarydatadownload.BeneficiaryDataEntity

/** One beneficiary-data table's download outcome. */
sealed interface BeneficiaryDataResult {
  data class Success(val recordCount: Int) : BeneficiaryDataResult
  data object Empty : BeneficiaryDataResult

  /** The backend has no endpoint for this entity yet (see
   * `docs/BENEFICIARY_DATA_DOWNLOAD_ENDPOINTS.md`) — distinct from [Failure] so the chain doesn't
   * treat a known, expected gap as a network error. */
  data object NotAvailable : BeneficiaryDataResult
  data class Failure(val cause: Throwable) : BeneficiaryDataResult
}

/**
 * Downloads one beneficiary-data table identified by [BeneficiaryDataEntity]. Each entity maps to
 * either a real repository call (only [BeneficiaryDataEntity.ready] entities have one today) or
 * [BeneficiaryDataResult.NotAvailable] for entities with no backend endpoint yet.
 */
interface BeneficiaryDataRepository {
  suspend fun download(entity: BeneficiaryDataEntity): BeneficiaryDataResult
}
