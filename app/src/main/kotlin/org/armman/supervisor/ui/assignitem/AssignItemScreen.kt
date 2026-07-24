package org.armman.supervisor.ui.assignitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.components.AvatarListCard
import org.armman.supervisor.ui.components.AvatarListRowItem
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.Dimens

/** Assign Item list screen: project/location selector + a Sakhi list to assign items to. */
@Composable
fun AssignItemScreen(
  onBack: () -> Unit,
  onSakhiSelected: (SakhiOption) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AssignItemViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.assign_item_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is AssignItemUiState.Loading -> LoadingContent()
        is AssignItemUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is AssignItemUiState.Success -> SuccessContent(
          state = state,
          onLocationSelected = viewModel::onLocationSelected,
          onSakhiSelected = onSakhiSelected,
        )
      }
    }
  }
}

@Composable
private fun LoadingContent() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(
  state: AssignItemUiState.Success,
  onLocationSelected: (String) -> Unit,
  onSakhiSelected: (SakhiOption) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    SingleSelectDropdown(
      options = state.locations,
      selected = state.locations.find { it.id == state.selectedLocationId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.location_selector_placeholder),
      onSelected = { onLocationSelected(it.id) },
    )
    if (state.sakhis.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.assign_item_empty_sakhis), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      AvatarListCard(
        rows = state.sakhis.map { sakhi -> AvatarListRowItem(label = sakhi.name, onClick = { onSakhiSelected(sakhi) }) },
        modifier = Modifier.padding(top = Dimens.ItemSpacing),
      )
    }
  }
}
