package org.armman.supervisor.ui.quickresponse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens

/** Quick Response list screen: cards for pending approvals/escalations (SRS FR-SV-4.1). */
@Composable
fun QuickResponseScreen(
  onBack: () -> Unit,
  onRequestSelected: (QuickResponseRequest) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: QuickResponseViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // The ViewModel already fetches once on creation — skip that first resume so this doesn't
  // fire a redundant second load (and a Loading-flicker) right on screen entry. Only resumes
  // after the first one (e.g. returning after submitting a reason) should re-fetch.
  var isInitialResume by remember { mutableStateOf(true) }
  LifecycleResumeEffect(Unit) {
    if (isInitialResume) {
      isInitialResume = false
    } else {
      viewModel.onRetry()
    }
    onPauseOrDispose { }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.quick_action_quick_response), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is QuickResponseUiState.Loading -> LoadingContent()
        is QuickResponseUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is QuickResponseUiState.Success -> SuccessContent(requests = state.requests, onRequestSelected = onRequestSelected)
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
private fun SuccessContent(requests: List<QuickResponseRequest>, onRequestSelected: (QuickResponseRequest) -> Unit) {
  if (requests.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text = stringResource(R.string.quick_response_empty), style = MaterialTheme.typography.bodyLarge)
    }
    return
  }
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
    LazyVerticalGrid(
      columns = if (isTablet) GridCells.Fixed(2) else GridCells.Fixed(1),
      horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
      modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    ) {
      items(requests, key = { it.id }) { request ->
        QuickResponseRequestCard(request = request, onCardClick = { onRequestSelected(request) })
      }
    }
  }
}
