package org.armman.supervisor.data.villagerisksummary

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticVillageRiskDetailRepositoryImplTest {
  private val repository = StaticVillageRiskDetailRepositoryImpl()

  @Test
  fun `getVillageRiskDetail returns seeded mothers and children for a known village`() = runTest {
    val result = repository.getVillageRiskDetail("SushilTest")

    assertEquals("SushilTest", result.villageName)
    assertTrue(result.mothers.isNotEmpty())
    assertTrue(result.children.isNotEmpty())
    assertTrue(result.mothers.any { it.name == "Sushma T Test" && it.riskDetails == "Hypertension" })
  }

  @Test
  fun `getVillageRiskDetail returns empty result for an unknown village without throwing`() = runTest {
    val result = repository.getVillageRiskDetail("unknown-village")

    assertEquals("unknown-village", result.villageName)
    assertTrue(result.mothers.isEmpty())
    assertTrue(result.children.isEmpty())
  }
}
