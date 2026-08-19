package org.armman.supervisor.data.masterdata

import android.util.Log
import javax.inject.Inject

/** Thin seam around [android.util.Log] so [MasterDataRepositoryImpl] doesn't need
 * `testOptions.unitTests.isReturnDefaultValues` to unit-test its failure path — that Gradle flag
 * is module-wide and would silently default *any* unmocked Android SDK call across every test in
 * this module, not just this one `Log.w`. A fake implementation lets tests exercise the real
 * failure-logging branch without touching the Android framework at all. */
interface MasterDataLogger {
  fun warn(message: String)
}

class AndroidMasterDataLogger @Inject constructor() : MasterDataLogger {
  override fun warn(message: String) {
    Log.w("MasterDataDownload", message)
  }
}
