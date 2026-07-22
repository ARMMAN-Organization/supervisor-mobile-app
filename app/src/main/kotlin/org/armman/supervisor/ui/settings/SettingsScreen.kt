package org.armman.supervisor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.GroupedListCard
import org.armman.supervisor.ui.components.GroupedListRowItem
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.RiskHigh
import org.armman.supervisor.ui.theme.White

/** Settings screen: grouped action lists (Sync/General/Share/Account/About) + a Log out button. */
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
  onLoggedOut: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val comingSoonMessage = stringResource(R.string.placeholder_coming_soon)
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  fun onAction(action: SettingsAction) {
    viewModel.onActionTapped(action)
    if (action != SettingsAction.LANGUAGE_SETUP) {
      scope.launch { snackbarHostState.showSnackbar(comingSoonMessage) }
    }
  }

  // One-shot: apply the chosen locale app-wide (persisted by autoStoreLocales).
  // Keyed on the whole request (including its id) so a repeat selection of the
  // same language — or one made right after the activity recreate that
  // setApplicationLocales itself triggers — is still treated as a new event.
  LaunchedEffect(uiState.languageRequest) {
    uiState.languageRequest?.let { request ->
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(request.tag))
      viewModel.onLanguageApplied()
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().background(White).padding(innerPadding),
    ) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

      Column(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .widthIn(max = if (isTablet) Dimens.ContentMaxWidthTablet else Dp.Unspecified)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
      ) {
        GroupedListCard(
          title = stringResource(R.string.settings_section_sync),
          rows = listOf(
            GroupedListRowItem(
              label = stringResource(R.string.settings_download_master_data),
              icon = { Icon(painterResource(R.drawable.ic_file_text), contentDescription = null) },
              onClick = { onAction(SettingsAction.DOWNLOAD_MASTER_DATA) },
            ),
            GroupedListRowItem(
              label = stringResource(R.string.settings_download_beneficiary_data),
              icon = { Icon(painterResource(R.drawable.ic_file_text), contentDescription = null) },
              onClick = { onAction(SettingsAction.DOWNLOAD_BENEFICIARY_DATA) },
            ),
          ),
        )
        GroupedListCard(
          title = stringResource(R.string.settings_section_general),
          rows = listOf(
            GroupedListRowItem(
              label = stringResource(R.string.settings_language_setup),
              icon = { Icon(Icons.Filled.Language, contentDescription = null) },
              onClick = { onAction(SettingsAction.LANGUAGE_SETUP) },
            ),
            GroupedListRowItem(
              label = stringResource(R.string.settings_change_password),
              icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
              onClick = { onAction(SettingsAction.CHANGE_PASSWORD) },
            ),
          ),
        )
        GroupedListCard(
          title = stringResource(R.string.settings_section_share),
          rows = listOf(
            GroupedListRowItem(
              label = stringResource(R.string.settings_share_database_file),
              icon = { Icon(Icons.Filled.Share, contentDescription = null) },
              onClick = { onAction(SettingsAction.SHARE_DATABASE_FILE) },
            ),
          ),
        )
        GroupedListCard(
          title = stringResource(R.string.settings_section_account),
          rows = listOf(
            GroupedListRowItem(
              label = stringResource(R.string.settings_check_update),
              icon = { Icon(Icons.Filled.Sync, contentDescription = null) },
              onClick = { onAction(SettingsAction.CHECK_UPDATE) },
            ),
          ),
        )
        GroupedListCard(
          title = stringResource(R.string.settings_section_about),
          rows = listOf(
            GroupedListRowItem(
              label = stringResource(R.string.settings_version_label, viewModel.versionName),
              icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
              showChevron = false,
              onClick = null,
            ),
          ),
        )
        LogOutButton(onClick = viewModel::onLogOutClicked)
      }
    }
  }

  if (uiState.showLogoutConfirmation) {
    LogoutConfirmationDialog(
      onConfirm = { viewModel.onLogoutConfirmed(onLoggedOut) },
      onDismiss = viewModel::onLogoutConfirmDismissed,
    )
  }

  if (uiState.showLanguageDialog) {
    LanguageDialog(
      onSelected = viewModel::onLanguageSelected,
      onDismiss = viewModel::onDismissLanguageDialog,
    )
  }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.settings_logout_confirm_title)) },
    text = { Text(stringResource(R.string.settings_logout_confirm_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(stringResource(R.string.settings_logout_confirm_confirm), color = RiskHigh)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.settings_logout_confirm_cancel))
      }
    },
  )
}

@Composable
private fun LogOutButton(onClick: () -> Unit) {
  Text(
    text = stringResource(R.string.settings_log_out),
    style = MaterialTheme.typography.titleMedium,
    color = RiskHigh,
    textAlign = TextAlign.Center,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = Dimens.ItemSpacing),
  )
}
