package org.armman.supervisor.data.auth.session

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal encrypted key-value abstraction. [SessionStore] and [OfflineCredentialCache] depend on
 * this interface rather than `SharedPreferences` directly so their own logic (serialization,
 * corrupted-data handling, same-user policy) can be unit-tested with an in-memory fake — this
 * repo has no Robolectric/instrumented test setup, so the real Android Keystore-backed
 * implementation below is intentionally left uncovered by JVM unit tests; verify it manually
 * on-device.
 */
interface SecureKeyValueStore {
  fun getString(key: String): String?

  /** Returns true if [value] was durably written, false if the underlying store was unavailable
   * (e.g. a broken Keystore) and the write was silently dropped. Most callers can ignore the
   * result — a failed write degrades the next [getString] to "nothing there", which they already
   * handle — but a caller relying on the value surviving until the next read (e.g. a
   * generate-once secret) must check it. */
  fun putString(key: String, value: String): Boolean
  fun remove(key: String)
}

private const val PREFS_FILE_NAME = "auth_secure_prefs"

/**
 * Android Keystore-backed implementation. Not unit-testable in this repo's JVM-only test setup.
 *
 * Some OEM Keystore/StrongBox implementations (observed on a vivo/iQOO OriginOS device, likely
 * after an OS update invalidated a previously-working key) throw out of [MasterKey.Builder.build]
 * or [EncryptedSharedPreferences.create] instead of returning — as a broken hardware-backed
 * provider (e.g. `ProviderException`/`KeyStoreException`), not only the checked
 * `GeneralSecurityException`/`IOException` the Android docs mention. This crashed the app on
 * launch, before any screen existed to show an error, because [SessionStore.readSession]'s own
 * corrupted-data handling never got a chance to run. [openPrefs] deliberately catches broadly
 * (any `Exception`, re-deriving [openPrefs] rather than caching a null) so any such Keystore
 * failure — checked or not — degrades to "treat as empty" instead of crashing, and the store can
 * recover on its own if the underlying issue is transient.
 */
@Singleton
class EncryptedSharedPreferencesStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : SecureKeyValueStore {

  private fun openPrefs() = runCatching {
    val masterKey = MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    EncryptedSharedPreferences.create(
      context,
      PREFS_FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }.onFailure { e ->
    Log.w("EncryptedSharedPreferencesStore", "Keystore-backed prefs unavailable; treating as empty", e)
  }.getOrNull()

  override fun getString(key: String): String? = openPrefs()?.getString(key, null)

  override fun putString(key: String, value: String): Boolean {
    val prefs = openPrefs() ?: return false
    prefs.edit().putString(key, value).apply()
    return true
  }

  override fun remove(key: String) {
    openPrefs()?.edit()?.remove(key)?.apply()
  }
}
