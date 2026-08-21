package org.armman.supervisor.ui.quickresponse

import org.armman.supervisor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickResponseModelsTest {

  @Test
  fun `quickResponseStatusLabelRes maps every known backend status code`() {
    assertEquals(R.string.quick_response_status_pending, "PENDING".quickResponseStatusLabelRes())
    assertEquals(R.string.quick_response_status_approved, "APPROVED".quickResponseStatusLabelRes())
    assertEquals(R.string.quick_response_status_rejected, "REJECTED".quickResponseStatusLabelRes())
  }

  @Test
  fun `quickResponseStatusLabelRes returns null for an unrecognized status code`() {
    assertNull("SOME_FUTURE_STATUS".quickResponseStatusLabelRes())
  }
}
