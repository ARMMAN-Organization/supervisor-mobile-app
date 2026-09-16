package org.armman.supervisor.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ComponentActivity
import kotlinx.coroutines.launch

/** Window within which a second back press exits the app (see [ExitOnDoubleBackHandler]). */
private const val EXIT_ON_BACK_WINDOW_MS = 2000L

/**
 * Absorbs every back press at a nav-graph root screen with a standard double-tap-to-exit prompt,
 * so an unhandled back press never falls through to the Activity's default finish() (a rapid
 * second back press racing that finish/relaunch can leave a blank window, only fixed by a full
 * app restart — see `AppNavHost`'s `popUpTo(..., inclusive = true)` call sites for the screens
 * this applies to: Dashboard at the bottom of the initial back stack, and Login again after a
 * logout resets the stack).
 *
 * The first back press shows [exitMessage] via [snackbarHostState]; a second press within
 * [EXIT_ON_BACK_WINDOW_MS] finishes the host Activity.
 */
@Composable
fun ExitOnDoubleBackHandler(exitMessage: String, snackbarHostState: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  val activity = LocalContext.current as? ComponentActivity
  var lastBackPressAtMs by remember { mutableLongStateOf(0L) }

  BackHandler {
    val now = System.currentTimeMillis()
    if (now - lastBackPressAtMs <= EXIT_ON_BACK_WINDOW_MS) {
      activity?.finish()
    } else {
      lastBackPressAtMs = now
      scope.launch { snackbarHostState.showSnackbar(exitMessage) }
    }
  }
}
