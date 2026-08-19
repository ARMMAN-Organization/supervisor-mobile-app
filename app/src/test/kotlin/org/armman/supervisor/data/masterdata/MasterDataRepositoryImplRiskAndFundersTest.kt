package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDataRepositoryImplRiskAndFundersTest {
  private val riskAndFundersApi = FakeRiskAndFundersApi()

  private fun repository() = testMasterDataRepository(riskAndFundersApi = riskAndFundersApi)

  @Test
  fun `downloading Risk Parameter returns Success with the parameter count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK_PARAMETER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Visit Master returns Success with the visit count`() = runTest {
    val result = repository().download(MasterDataEntity.VISIT_MASTER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Funders returns Success with the funder count`() = runTest {
    val result = repository().download(MasterDataEntity.FUNDERS)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Risk returns Success with the condition count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a risk-and-funders failure is surfaced as Failure, not thrown`() = runTest {
    riskAndFundersApi.failing = true

    val result = repository().download(MasterDataEntity.FUNDERS)

    assertTrue(result is MasterDataResult.Failure)
  }
}
