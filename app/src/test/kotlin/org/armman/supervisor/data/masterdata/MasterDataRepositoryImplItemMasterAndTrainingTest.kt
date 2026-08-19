package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataRepositoryImplItemMasterAndTrainingTest {
  private val itemMasterAndTrainingApi = FakeItemMasterAndTrainingApi()

  private fun repository() = testMasterDataRepository(itemMasterAndTrainingApi = itemMasterAndTrainingApi)

  @Test
  fun `downloading Application Parameter returns Success with the param count`() = runTest {
    val result = testMasterDataRepository().download(MasterDataEntity.APPLICATION_PARAMETER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Item Master List returns Success with the item count`() = runTest {
    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Item Master List with no items returns Empty`() = runTest {
    itemMasterAndTrainingApi.items = emptyList()

    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `downloading Training Topic Master returns Success with the topic count`() = runTest {
    val result = repository().download(MasterDataEntity.TRAINING_TOPIC_MASTER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `an item-master-and-training failure is surfaced as Failure, not thrown`() = runTest {
    itemMasterAndTrainingApi.failing = true

    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertTrue(result is MasterDataResult.Failure)
  }
}
