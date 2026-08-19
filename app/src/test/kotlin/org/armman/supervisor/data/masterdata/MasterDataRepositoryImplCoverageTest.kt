package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-entity coverage: the wired-branch regression guard and the one entity with no bulk
 * backend endpoint at all. See MasterDataRepositoryImplGeographyTest / ProjectsAndSakhiTest /
 * RiskAndFundersTest / CategoriesTest / ItemMasterAndTrainingTest for the per-domain download
 * tests. */
class MasterDataRepositoryImplCoverageTest {

  // Regression test for a real bug: ITEM_MASTER_LIST and TRAINING_TOPIC_MASTER were flipped to
  // `ready = true` without a matching `when` branch in MasterDataRepositoryImpl, so they fell
  // through to the `else -> error(...)` branch — caught by runCatching and surfaced as a Failure
  // that the screen showed as a generic "no internet connection" dialog, hiding the real bug.
  // This asserts every `ready` entity resolves to something other than that fallback error by
  // checking a curated list of entities added after the initial wiring pass; a future ready-entity
  // added here without its own `when` branch will fail this the same way it failed in production.
  @Test
  fun `every ready entity used by the app resolves without falling through to the wired-branch error`() = runTest {
    val repo = testMasterDataRepository()
    val readyEntities = MasterDataEntity.entries.filter { it.ready }

    for (entity in readyEntities) {
      val result = repo.download(entity)
      val isWiredBranchError = result is MasterDataResult.Failure &&
        result.cause.message?.contains("is marked ready but has no download case wired") == true
      assertTrue("$entity fell through to the unwired-branch error", !isWiredBranchError)
    }
  }

  @Test
  fun `an entity with no bulk backend endpoint returns NotAvailable when mocking is off`() = runTest {
    val result = testMasterDataRepository().download(MasterDataEntity.INCENTIVE_RATE)

    assertEquals(MasterDataResult.NotAvailable, result)
  }

  @Test
  fun `an entity with no bulk backend endpoint mock-succeeds when mocking is on`() = runTest {
    val result = testMasterDataRepository(mockUnreadyEntities = true).download(MasterDataEntity.INCENTIVE_RATE)

    assertTrue(result is MasterDataResult.Success)
  }

  // Confirmed with backend: /incentive-rates/active resolves one rate for a required rateType,
  // not a bulk list — there is no download-everything endpoint, so this stays NotAvailable.
  @Test
  fun `Incentive Rate stays NotAvailable since no bulk list endpoint exists`() = runTest {
    val result = testMasterDataRepository().download(MasterDataEntity.INCENTIVE_RATE)

    assertEquals(MasterDataResult.NotAvailable, result)
  }
}
