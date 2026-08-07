package org.armman.supervisor.data.quickresponse

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestStatus
import org.armman.supervisor.ui.quickresponse.QuickResponseRequestType
import org.armman.supervisor.ui.quickresponse.ReasonOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeQuickResponseApi : QuickResponseApi {
  var listResult: Response<QuickResponseListEnvelopeDto> =
    Response.success(QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)))
  var decisionResult: Response<DecideQuickResponseEnvelopeDto> =
    Response.success(
      DecideQuickResponseEnvelopeDto(
        success = true,
        message = "OK",
        data = DecideQuickResponseDto("card-1", "approval_requests", "APPROVE"),
      ),
    )
  var lastDecisionRequest: DecideQuickResponseRequestDto? = null

  override suspend fun getQuickResponseCards(status: String, cursor: String?, limit: Int?) = listResult

  override suspend fun decideQuickResponseCard(cardId: String, request: DecideQuickResponseRequestDto): Response<DecideQuickResponseEnvelopeDto> {
    lastDecisionRequest = request
    return decisionResult
  }
}

class QuickResponseRepositoryImplTest {
  private val api = FakeQuickResponseApi()
  private val repository = QuickResponseRepositoryImpl(api)

  private fun card(id: String, cardType: String, raisedAt: String = "2026-08-07T10:00:00Z") =
    QuickResponseCardDto(cardId = id, cardType = cardType, cardSource = "approval_requests", beneficiaryId = null, raisedAt = raisedAt)

  // --- Positive ---

  @Test
  fun `getRequests maps DATA_RESTORE cards from the API`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(cards = listOf(card("card-1", "DATA_RESTORE")), nextCursor = null),
      ),
    )

    val requests = repository.getRequests()

    assertEquals(1, requests.size)
    assertEquals("card-1", requests[0].id)
    assertEquals(QuickResponseRequestType.DATA_RESTORE, requests[0].requestType)
    assertEquals(QuickResponseRequestStatus.PENDING, requests[0].status)
  }

  @Test
  fun `submitReason calls the decision endpoint with the card source and decision`() = runTest {
    repository.submitReason("card-1", ReasonOption.APPROVE)

    assertEquals("approval_requests", api.lastDecisionRequest?.cardSource)
    assertEquals("APPROVE", api.lastDecisionRequest?.decision)
  }

  // --- Negative ---

  @Test(expected = IllegalStateException::class)
  fun `getRequests throws on an HTTP error`() = runTest {
    api.listResult = Response.error(500, "".toResponseBody(null))

    repository.getRequests()
  }

  @Test(expected = IllegalStateException::class)
  fun `getRequests throws when the envelope reports failure`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(success = false, message = "Unauthorized", data = null),
    )

    repository.getRequests()
  }

  @Test(expected = IllegalStateException::class)
  fun `submitReason throws when the decision is not yet implemented`() = runTest {
    api.decisionResult = Response.error(501, "".toResponseBody(null))

    repository.submitReason("card-1", ReasonOption.RESTORED)
  }

  // --- Edge cases ---

  @Test
  fun `getRequests filters out non-DATA_RESTORE cards`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(
        success = true,
        message = "OK",
        data = QuickResponseListDto(
          cards = listOf(card("card-1", "DATA_RESTORE"), card("card-2", "REOPEN"), card("card-3", "EDD_NEARING")),
          nextCursor = null,
        ),
      ),
    )

    val requests = repository.getRequests()

    assertTrue(requests.all { it.requestType == QuickResponseRequestType.DATA_RESTORE })
    assertEquals(1, requests.size)
  }

  @Test
  fun `getRequests returns an empty list when there are no cards`() = runTest {
    api.listResult = Response.success(
      QuickResponseListEnvelopeDto(success = true, message = "OK", data = QuickResponseListDto(emptyList(), null)),
    )

    assertTrue(repository.getRequests().isEmpty())
  }
}
