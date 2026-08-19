package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataRepositoryImplCategoriesTest {
  private val categoryApi = FakeMasterDataCategoryApi()

  private fun repository() = testMasterDataRepository(categoryApi = categoryApi)

  @Test
  fun `downloading Risk Category returns Success with the category's value count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK_CATEGORY)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering Types returns Success with the category's value count`() = runTest {
    val result = repository().download(MasterDataEntity.GATHERING_TYPES)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(2, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a category with no values returns Empty`() = runTest {
    categoryApi.riskCategories = category("RISK_GRADE", 0)

    val result = repository().download(MasterDataEntity.RISK_CATEGORY)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `a category-endpoint failure is surfaced as Failure, not thrown`() = runTest {
    categoryApi.failing = true

    val result = repository().download(MasterDataEntity.VISIT_CATEGORY)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `downloading DDL Item sums values across every returned category`() = runTest {
    val result = repository().download(MasterDataEntity.DDL_ITEM)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(7, (result as MasterDataResult.Success).recordCount)
  }
}
