package org.armman.supervisor.data.masterdata

import org.armman.supervisor.ui.masterdata.MasterDataEntity

/** One master-data table's download outcome. */
sealed interface MasterDataResult {
  data class Success(val recordCount: Int) : MasterDataResult
  data object Empty : MasterDataResult

  /** The backend has no endpoint for this entity yet (see the endpoint scope-decision doc) —
   * distinct from [Failure] so the chain doesn't treat a known, expected gap as a network error. */
  data object NotAvailable : MasterDataResult
  data class Failure(val cause: Throwable) : MasterDataResult
}

/**
 * Downloads one master-data table identified by [MasterDataEntity]. Each entity maps to either a
 * real repository call (only [MasterDataEntity.ready] entities have one today) or
 * [MasterDataResult.NotAvailable] for entities with no backend endpoint yet.
 */
interface MasterDataRepository {
  suspend fun download(entity: MasterDataEntity): MasterDataResult
}
