package org.armman.supervisor.ui.quickresponse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Quick Response list screen: cards for pending approvals with direct Approve/Reject CTAs
 * (SRS FR-SV-4.1). */
@Composable
fun QuickResponseScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: QuickResponseViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // The ViewModel already fetches once on creation — skip that first resume so this doesn't
  // fire a redundant second load (and a Loading-flicker) right on screen entry.
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
        is QuickResponseUiState.Success -> SuccessContent(
          state = state,
          onAction = { requestId, action ->
            when (action) {
              is QuickResponseCardAction.Decide -> viewModel.onDecide(requestId, action.decision, action.notes)
              is QuickResponseCardAction.Escalate -> viewModel.onEscalationAction(requestId, action.action)
              QuickResponseCardAction.Acknowledge -> viewModel.onAcknowledgeEddNearing(requestId)
            }
          },
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
  state: QuickResponseUiState.Success,
  onAction: (String, QuickResponseCardAction) -> Unit,
) {
  if (state.requests.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text = stringResource(R.string.quick_response_empty), style = MaterialTheme.typography.bodyLarge)
    }
    return
  }
  Column(modifier = Modifier.fillMaxSize()) {
    state.decisionErrorMessageRes?.let { messageRes ->
      Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
      )
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      // Cards vary a lot in height by type (Data Restore: 3 fields; Closure Review: 6+), so a
      // 2-column grid paired mismatched-height cards side by side and looked uneven. A single
      // column avoids that regardless of form factor; on tablet the column is width-capped and
      // centered instead of stretching phone-width cards across the full screen.
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        modifier = Modifier
          .fillMaxSize()
          .then(if (isTablet) Modifier.widthIn(max = Dimens.ContentMaxWidthTablet) else Modifier)
          .padding(Dimens.ScreenPadding),
      ) {
        items(state.requests, key = { it.id }) { request ->
          QuickResponseRequestCard(
            request = request,
            onAction = { action -> onAction(request.id, action) },
            isDeciding = state.decidingRequestId == request.id,
            // The ViewModel's guard blocks a decision on ANY card while one is in flight, so
            // every card's buttons must show as disabled while that's true, not just the one
            // actually spinning — otherwise every other card looks tappable but silently no-ops.
            actionsEnabled = state.decidingRequestId == null,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
