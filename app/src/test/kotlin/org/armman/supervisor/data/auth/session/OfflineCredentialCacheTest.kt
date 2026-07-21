package org.armman.supervisor.data.auth.session

import org.armman.supervisor.data.auth.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCredentialCacheTest {
  private val store = FakeSecureKeyValueStore()
  private val cache = OfflineCredentialCache(store)

  private fun fakeSession(username: String = "super01") = UserSession(
    username = username,
    subjectId = "subject-1",
    roles = listOf("SUPERVISOR"),
    projectId = null,
    geographyUnitId = null,
    accessToken = "access-token",
    refreshToken = "refresh-token",
    accessTokenExpiresAtEpochSeconds = 1_800_000_000L,
  )

  @Test
  fun `store never persists plaintext password`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    val rawStored = store.getString("offline_cred_entry")
    assertTrue(rawStored != null)
    assertFalse(rawStored!!.contains("Super@123"))
  }

  @Test
  fun `verify matches correct username and password`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertTrue(cache.verify("super01", "Super@123".toCharArray()))
  }

  @Test
  fun `verify rejects wrong password`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertFalse(cache.verify("super01", "WrongPassword".toCharArray()))
  }

  @Test
  fun `verify rejects different username exact case`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertFalse(cache.verify("other01", "Super@123".toCharArray()))
  }

  @Test
  fun `verify rejects username differing only in case`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertFalse(cache.verify("SUPER01", "Super@123".toCharArray()))
  }

  @Test
  fun `verify with no cache present returns false not a crash`() {
    assertFalse(cache.verify("super01", "Super@123".toCharArray()))
  }

  @Test
  fun `new successful online login overwrites the previous cached hash`() {
    cache.store("super01", "OldPassword1".toCharArray(), fakeSession())
    cache.store("super01", "NewPassword2".toCharArray(), fakeSession())

    assertTrue(cache.verify("super01", "NewPassword2".toCharArray()))
    assertFalse(cache.verify("super01", "OldPassword1".toCharArray()))
  }

  @Test
  fun `verifyAndRestoreSession returns the stored session on match`() {
    val session = fakeSession()
    cache.store("super01", "Super@123".toCharArray(), session)

    assertEquals(session, cache.verifyAndRestoreSession("super01", "Super@123".toCharArray()))
  }

  @Test
  fun `verifyAndRestoreSession returns null on mismatch`() {
    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertNull(cache.verifyAndRestoreSession("super01", "wrong".toCharArray()))
  }

  @Test
  fun `hasAnyEntry is false before any store and true after`() {
    assertFalse(cache.hasAnyEntry())

    cache.store("super01", "Super@123".toCharArray(), fakeSession())

    assertTrue(cache.hasAnyEntry())
  }

  @Test
  fun `corrupted cache entry is treated as no cache, never throws`() {
    store.putString("offline_cred_entry", "{not valid json")

    assertFalse(cache.verify("super01", "Super@123".toCharArray()))
    assertFalse(cache.hasAnyEntry())
  }
}
