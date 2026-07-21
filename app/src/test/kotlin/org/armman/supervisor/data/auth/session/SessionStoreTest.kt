package org.armman.supervisor.data.auth.session

import org.armman.supervisor.data.auth.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {
  private val store = FakeSecureKeyValueStore()
  private val sessionStore = SessionStore(store)

  private fun fakeSession() = UserSession(
    username = "super01",
    subjectId = "subject-1",
    roles = listOf("SUPERVISOR"),
    projectId = "project-1",
    geographyUnitId = "geo-1",
    accessToken = "access-token",
    refreshToken = "refresh-token",
    accessTokenExpiresAtEpochSeconds = 1_800_000_000L,
  )

  @Test
  fun `save then read returns identical session`() {
    val session = fakeSession()
    sessionStore.saveSession(session)

    assertEquals(session, sessionStore.readSession())
  }

  @Test
  fun `no session saved returns null`() {
    assertNull(sessionStore.readSession())
  }

  @Test
  fun `logout clears session but not offline credential cache`() {
    val session = fakeSession()
    sessionStore.saveSession(session)
    val offlineCache = OfflineCredentialCache(store)
    offlineCache.store("super01", "Super@123".toCharArray(), session)

    sessionStore.clearSession()

    assertNull(sessionStore.readSession())
    assertEquals(true, offlineCache.verify("super01", "Super@123".toCharArray()))
  }

  @Test
  fun `corrupted store read fails safe`() {
    store.putString("session_json", "{not valid json")

    assertNull(sessionStore.readSession())
  }
}
