package org.armman.supervisor.data.beneficiaries

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.ui.beneficiaries.BeneficiaryDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BeneficiaryListRepositoryImplTest {
  private val repository = BeneficiaryListRepositoryImpl()

  @Test
  fun `getBeneficiaries returns the seeded list for a known sakhiId`() = runTest {
    val result = repository.getBeneficiaries("sakhi-komal")

    assertEquals("SakhiKomal", result.sakhiName)
    assertEquals(2, result.beneficiaries.size)
    assertEquals(1, result.beneficiaries.filterIsInstance<BeneficiaryDetail.Child>().size)
    assertEquals(1, result.beneficiaries.filterIsInstance<BeneficiaryDetail.Mother>().size)
  }

  @Test
  fun `getBeneficiaries throws for an unknown sakhiId`() {
    assertThrows(NoSuchElementException::class.java) { runTest { repository.getBeneficiaries("unknown-sakhi") } }
  }
}
