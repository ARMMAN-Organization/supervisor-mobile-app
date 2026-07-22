package org.armman.supervisor.data.auth.session

/** In-memory fake so SessionStore/OfflineCredentialCache can be unit-tested without
 * EncryptedSharedPreferences (no Robolectric/instrumented test setup in this repo). */
class FakeSecureKeyValueStore : SecureKeyValueStore {
  private val values = mutableMapOf<String, String>()

  override fun getString(key: String): String? = values[key]

  override fun putString(key: String, value: String) {
    values[key] = value
  }

  override fun remove(key: String) {
    values.remove(key)
  }
}
