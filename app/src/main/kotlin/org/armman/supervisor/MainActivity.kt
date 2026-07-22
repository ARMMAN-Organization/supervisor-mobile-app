package org.armman.supervisor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.armman.supervisor.ui.navigation.AppNavHost
import org.armman.supervisor.ui.theme.ArogyaTheme

// AppCompatActivity (not ComponentActivity) so per-app locales work below API 33.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      ArogyaTheme { AppNavHost() }
    }
  }
}
