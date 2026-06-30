package org.armman.supervisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import org.armman.supervisor.ui.navigation.AppNavHost
import org.armman.supervisor.ui.theme.ArogyaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ArogyaTheme { AppNavHost() }
    }
  }
}
