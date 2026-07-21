package org.armman.supervisor.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.White

/**
 * Brand-colored (dark green) top app bar with a white back arrow + white bold title — the
 * standard header for real screens (as opposed to [PlaceholderScreen]'s default Material bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopAppBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  TopAppBar(
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = White) },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = White)
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = DashboardHeaderGreen),
    modifier = modifier,
  )
}
