package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataRepositoryImplGeographyTest {
  private val geographyApi = FakeGeographyApi()

  private fun repository() = testMasterDataRepository(geographyApi = geographyApi)

  @Test
  fun `downloading State returns Success with the root count and resets the geography cache`() = runTest {
    val result = repository().download(MasterDataEntity.STATE)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading District after State descends using the roots just fetched`() = runTest {
    val repo = repository()
    repo.download(MasterDataEntity.STATE)

    val result = repo.download(MasterDataEntity.DISTRICT)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading District without a prior State call finds no parents and returns Empty`() = runTest {
    val result = repository().download(MasterDataEntity.DISTRICT)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `Village List re-downloads the same level as Village without advancing the cache`() = runTest {
    geographyApi.unitsByParent = mapOf(
      "state-1" to listOf(geographyUnit("village-1", "state-1", "VILLAGE", "Test Village")),
    )
    val repo = repository()
    repo.download(MasterDataEntity.STATE)

    val villageResult = repo.download(MasterDataEntity.VILLAGE)
    val villageListResult = repo.download(MasterDataEntity.VILLAGE_LIST)

    assertEquals((villageResult as MasterDataResult.Success).recordCount, (villageListResult as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a geography failure is surfaced as Failure, not thrown`() = runTest {
    geographyApi.failing = true

    val result = repository().download(MasterDataEntity.STATE)

    assertTrue(result is MasterDataResult.Failure)
  }
}
