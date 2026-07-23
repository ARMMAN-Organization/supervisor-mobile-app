package org.armman.supervisor.data.callsheet

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.local.CallLogDao
import org.armman.supervisor.data.local.CallLogEntity
import org.armman.supervisor.ui.callsheet.CallConnected
import org.armman.supervisor.ui.callsheet.CallLogSubmission
import org.armman.supervisor.ui.callsheet.CallResponder
import org.armman.supervisor.ui.callsheet.FailureReason
import org.armman.supervisor.ui.callsheet.SuccessOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CallLogDao] has no JVM-testable implementation — see the same pattern/reasoning documented on
 * `AssignItemRepositoryImplTest`'s `FakeTransactionDao`. */
private class FakeCallLogDao : CallLogDao {
  // Mirrors the real DAO's "timestampEpochMillis DESC, rowid DESC" ordering: entries is
  // insertion-ordered, so reversing it breaks same-millisecond ties by most-recently-inserted.
  private val entries = mutableListOf<CallLogEntity>()

  override suspend fun getBySakhi(sakhiId: String): List<CallLogEntity> =
    entries.asReversed().filter { it.sakhiId == sakhiId }
      .sortedByDescending { it.timestampEpochMillis }

  override suspend fun getLatestForSakhi(sakhiId: String): CallLogEntity? = getBySakhi(sakhiId).firstOrNull()

  override suspend fun insert(entity: CallLogEntity) {
    entries += entity
  }
}

class CallSheetRepositoryImplTest {
  private val dao = FakeCallLogDao()
  private val repository = CallSheetRepositoryImpl(dao)

  @Test
  fun `getSakhiSummaries returns only sakhis under the given location`() = runTest {
    val summaries = repository.getSakhiSummaries("loc-1")
    assertEquals(setOf("sakhi-1", "sakhi-2"), summaries.map { it.sakhi.id }.toSet())
  }

  @Test
  fun `getSakhiSummaries for unknown location returns empty list`() = runTest {
    assertTrue(repository.getSakhiSummaries("unknown-loc").isEmpty())
  }

  @Test
  fun `getSakhiSummaries reflects lastCalledAtEpochMillis from the latest logged call`() = runTest {
    val beforeCall = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-1" }
    assertNull(beforeCall.lastCalledAtEpochMillis)

    repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-1",
        connected = CallConnected.NO,
        successOutcome = null,
        failureReason = FailureReason.RINGING,
        responder = null,
        durationMinutes = null,
        notes = null,
        followUpAction = null,
      ),
    )

    val afterCall = repository.getSakhiSummaries("loc-1").first { it.sakhi.id == "sakhi-1" }
    assertTrue(afterCall.lastCalledAtEpochMillis != null)
  }

  @Test
  fun `getCallHistory returns entries newest first`() = runTest {
    val first = repository.logCall(
      CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.RINGING, null, null, null, null),
    )
    val second = repository.logCall(
      CallLogSubmission("sakhi-1", CallConnected.NO, null, FailureReason.PHONE_OFF, null, null, null, null),
    )

    val history = repository.getCallHistory("sakhi-1")
    assertEquals(listOf(second.id, first.id), history.map { it.id })
  }

  @Test
  fun `getCallHistory for sakhi with no calls returns empty list`() = runTest {
    assertTrue(repository.getCallHistory("sakhi-2").isEmpty())
  }

  @Test
  fun `logCall persists connected call with success outcome and responder round trip`() = runTest {
    val created = repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-1",
        connected = CallConnected.YES,
        successOutcome = SuccessOutcome.PICKED_UP_TALKED,
        failureReason = null,
        responder = CallResponder.RELATIVE,
        durationMinutes = 5,
        notes = "Discussed follow-up visit",
        followUpAction = "Schedule visit next week",
        ),
      )

    val history = repository.getCallHistory("sakhi-1")
    val stored = history.single { it.id == created.id }
    assertEquals(CallConnected.YES, stored.connected)
    assertEquals(SuccessOutcome.PICKED_UP_TALKED, stored.successOutcome)
    assertEquals(CallResponder.RELATIVE, stored.responder)
    assertEquals(5, stored.durationMinutes)
    assertEquals("Discussed follow-up visit", stored.notes)
    assertEquals("Schedule visit next week", stored.followUpAction)
    assertNull(stored.failureReason)
  }

  @Test
  fun `logCall persists failed call with failure reason and no success fields`() = runTest {
    val created = repository.logCall(
      CallLogSubmission(
        sakhiId = "sakhi-2",
        connected = CallConnected.NO,
        successOutcome = null,
        failureReason = FailureReason.OUT_OF_NETWORK,
        responder = null,
        durationMinutes = null,
        notes = null,
        followUpAction = null,
      ),
    )

    val stored = repository.getCallHistory("sakhi-2").single { it.id == created.id }
    assertEquals(CallConnected.NO, stored.connected)
    assertEquals(FailureReason.OUT_OF_NETWORK, stored.failureReason)
    assertNull(stored.successOutcome)
    assertNull(stored.responder)
    assertNull(stored.durationMinutes)
  }

  @Test
  fun `getSakhiOption returns the matching sakhi across locations`() = runTest {
    val sakhi = repository.getSakhiOption("sakhi-3")
    assertEquals("sakhi-3", sakhi.id)
  }
}
