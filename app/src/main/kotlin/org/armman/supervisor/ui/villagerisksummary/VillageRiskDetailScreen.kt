package org.armman.supervisor.ui.villagerisksummary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.Primary
import org.armman.supervisor.ui.theme.RiskHigh
import org.armman.supervisor.ui.theme.RiskLow
import org.armman.supervisor.ui.theme.RiskMild
import org.armman.supervisor.ui.theme.RiskModerate
import org.armman.supervisor.ui.theme.SerifTitle
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** Village Risk Detail screen: per-beneficiary risk cards for one village under a Sakhi, with a
 * Mother/Child tab toggle. Reached by tapping a village name on [org.armman.supervisor.ui.risksummary.RiskSummaryScreen]. */
@Composable
fun VillageRiskDetailScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: VillageRiskDetailViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val title = (uiState as? VillageRiskDetailUiState.Success)?.sakhiName
    ?: stringResource(R.string.risk_summary_title)

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = title, onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is VillageRiskDetailUiState.Loading -> LoadingContent()
        is VillageRiskDetailUiState.Error -> ErrorContent(state = state, onRetry = viewModel::onRetry)
        is VillageRiskDetailUiState.Success -> SuccessContent(
          state = state,
          onTabSelected = viewModel::onTabSelected,
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
private fun ErrorContent(state: VillageRiskDetailUiState.Error, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
      style = MaterialTheme.typography.bodyLarge,
    )
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(state: VillageRiskDetailUiState.Success, onTabSelected: (Tab) -> Unit) {
  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    ContextRow(
      label = stringResource(R.string.village_risk_detail_village_name_label),
      value = state.villageName,
    )
    if (state.isEmpty) {
      EmptyMessage(stringResource(R.string.village_risk_detail_empty))
      return@Column
    }
    MotherChildTabRow(
      selectedTab = state.selectedTab,
      onTabSelected = onTabSelected,
      modifier = Modifier.padding(top = Dimens.ItemSpacing, bottom = Dimens.SmallSpacing),
    )
    if (state.visibleBeneficiaries.isEmpty()) {
      EmptyMessage(
        text = when (state.selectedTab) {
          Tab.MOTHER -> stringResource(R.string.village_risk_detail_empty_mothers)
          Tab.CHILD -> stringResource(R.string.village_risk_detail_empty_children)
        },
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
        items(state.visibleBeneficiaries, key = BeneficiaryRiskDetail::id) { beneficiary ->
          BeneficiaryRiskCard(beneficiary)
        }
      }
    }
  }
}

@Composable
private fun ContextRow(label: String, value: String, modifier: Modifier = Modifier) {
  Row(modifier = modifier) {
    Text(text = "$label: ", style = MaterialTheme.typography.bodyMedium, color = Primary)
    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Primary)
  }
}

@Composable
private fun EmptyMessage(text: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = NeutralG200)
  }
}

/** Minimal two-segment Mother/Child tab toggle — no reusable segmented-control component exists
 * yet in `ui/components`; built here from [Dimens]/typography tokens only. */
@Composable
private fun MotherChildTabRow(selectedTab: Tab, onTabSelected: (Tab) -> Unit, modifier: Modifier = Modifier) {
  Surface(
    color = NeutralG50,
    shape = RoundedCornerShape(Dimens.SmallRadius),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(Dimens.ExtraSmallSpacing)) {
      TabSegment(
        text = stringResource(R.string.village_risk_detail_tab_mother),
        selected = selectedTab == Tab.MOTHER,
        onClick = { onTabSelected(Tab.MOTHER) },
      )
      TabSegment(
        text = stringResource(R.string.village_risk_detail_tab_child),
        selected = selectedTab == Tab.CHILD,
        onClick = { onTabSelected(Tab.CHILD) },
      )
    }
  }
}

@Composable
private fun RowScope.TabSegment(text: String, selected: Boolean, onClick: () -> Unit) {
  Surface(
    color = if (selected) Primary else NeutralG50,
    shape = RoundedCornerShape(Dimens.SmallRadius),
    modifier = Modifier.weight(1f).clickable(onClick = onClick),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      color = if (selected) White else NeutralG200,
      textAlign = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Dimens.SmallSpacing),
    )
  }
}

@Composable
private fun BeneficiaryRiskCard(beneficiary: BeneficiaryRiskDetail) {
  val accentColor = riskColor(beneficiary.riskType)
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(Dimens.CardAccentHeight)
          .background(accentColor, RoundedCornerShape(topStart = Dimens.CardRadius, topEnd = Dimens.CardRadius)),
      )
      Column(
        modifier = Modifier.padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.TinySpacing),
      ) {
        Text(
          text = beneficiary.name,
          style = SerifTitle,
          color = NeutralG400,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
        DetailRow(stringResource(R.string.village_risk_detail_label_registration_type), beneficiary.registrationType)
        DetailRow(stringResource(R.string.village_risk_detail_label_risk_details), beneficiary.riskDetails)
        DetailRow(
          label = stringResource(R.string.village_risk_detail_label_risk_type),
          value = riskLevelLabel(beneficiary.riskType),
          valueColor = accentColor,
        )
        DetailRow(stringResource(R.string.village_risk_detail_label_visit), beneficiary.visit)
        DetailRow(stringResource(R.string.village_risk_detail_label_visit_date), beneficiary.visitDate)
        DetailRow(
          label = stringResource(R.string.village_risk_detail_label_referred),
          value = if (beneficiary.referred) {
            stringResource(R.string.village_risk_detail_referred_yes)
          } else {
            stringResource(R.string.village_risk_detail_referred_no)
          },
        )
      }
    }
  }
}

@Composable
private fun riskLevelLabel(riskType: BeneficiaryRiskLevel): String = when (riskType) {
  BeneficiaryRiskLevel.HIGH -> stringResource(R.string.village_risk_detail_risk_level_high)
  BeneficiaryRiskLevel.MODERATE -> stringResource(R.string.village_risk_detail_risk_level_moderate)
  BeneficiaryRiskLevel.MILD -> stringResource(R.string.village_risk_detail_risk_level_mild)
  BeneficiaryRiskLevel.LOW -> stringResource(R.string.village_risk_detail_risk_level_low)
}

private fun riskColor(riskType: BeneficiaryRiskLevel) = when (riskType) {
  BeneficiaryRiskLevel.HIGH -> RiskHigh
  BeneficiaryRiskLevel.MODERATE -> RiskModerate
  BeneficiaryRiskLevel.MILD -> RiskMild
  BeneficiaryRiskLevel.LOW -> RiskLow
}

@Composable
private fun DetailRow(
  label: String,
  value: String,
  valueColor: Color = NeutralG400,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeutralG200)
    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
  }
}
