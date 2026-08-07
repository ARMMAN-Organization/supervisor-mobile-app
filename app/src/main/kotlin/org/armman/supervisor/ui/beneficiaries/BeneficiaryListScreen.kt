package org.armman.supervisor.ui.beneficiaries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BeneficiaryTypeTag
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SearchField
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.Primary
import org.armman.supervisor.ui.theme.PrimarySurface
import org.armman.supervisor.ui.theme.Secondary
import org.armman.supervisor.ui.theme.SerifTitle
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow
import java.util.Locale

/** Sakhi's beneficiary list: search + project/address context + one themed card per beneficiary,
 * reached by tapping a Sakhi card on [org.armman.supervisor.ui.registrations.RegistrationsScreen]. */
@Composable
fun BeneficiaryListScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BeneficiaryListViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val title = (uiState as? BeneficiaryListUiState.Success)?.sakhiName ?: stringResource(R.string.registrations_title)

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = title, onBack = onBack) },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

      when (val state = uiState) {
        is BeneficiaryListUiState.Loading -> LoadingContent()
        is BeneficiaryListUiState.Error -> ErrorContent(state = state, onRetry = viewModel::onRetry)
        is BeneficiaryListUiState.Success -> SuccessContent(
          state = state,
          isTablet = isTablet,
          onSearchQueryChanged = viewModel::onSearchQueryChanged,
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
private fun ErrorContent(state: BeneficiaryListUiState.Error, onRetry: () -> Unit) {
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
private fun SuccessContent(
  state: BeneficiaryListUiState.Success,
  isTablet: Boolean,
  onSearchQueryChanged: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    SearchField(
      value = state.searchQuery,
      onValueChange = onSearchQueryChanged,
      placeholder = stringResource(R.string.beneficiary_search_placeholder),
      modifier = Modifier.fillMaxWidth(),
    )
    Column(modifier = Modifier.padding(top = Dimens.ItemSpacing)) {
      ContextRow(label = stringResource(R.string.beneficiary_project_label), value = state.projectName)
      ContextRow(
        label = stringResource(R.string.beneficiary_address_label),
        value = state.address,
        modifier = Modifier.padding(top = Dimens.ExtraSmallSpacing),
      )
    }
    Text(
      text = stringResource(R.string.beneficiary_count, state.visibleBeneficiaries.size),
      style = MaterialTheme.typography.titleMedium,
      color = NeutralG400,
      modifier = Modifier.padding(top = Dimens.ItemSpacing, bottom = Dimens.SmallSpacing),
    )
    when {
      state.allBeneficiaries.isEmpty() -> EmptyMessage(stringResource(R.string.beneficiary_empty))
      state.visibleBeneficiaries.isEmpty() -> EmptyMessage(stringResource(R.string.beneficiary_search_empty))
      isTablet -> BeneficiaryGrid(state.visibleBeneficiaries)
      else -> BeneficiaryColumn(state.visibleBeneficiaries)
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

@Composable
private fun BeneficiaryColumn(beneficiaries: List<BeneficiaryDetail>) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
  ) {
    beneficiaries.forEach { beneficiary -> BeneficiaryCard(beneficiary) }
  }
}

@Composable
private fun BeneficiaryGrid(beneficiaries: List<BeneficiaryDetail>) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(TABLET_GRID_COLUMNS),
    horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(beneficiaries) { beneficiary -> BeneficiaryCard(beneficiary) }
  }
}

@Composable
private fun BeneficiaryCard(beneficiary: BeneficiaryDetail) {
  val accentColor = when (beneficiary) {
    is BeneficiaryDetail.Mother -> Primary
    is BeneficiaryDetail.Child -> Secondary
  }
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
      Column(modifier = Modifier.padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.SmallSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text = beneficiary.name, style = SerifTitle, color = NeutralG400, modifier = Modifier.weight(1f))
          BeneficiaryTypeTag(
            text = when (beneficiary) {
              is BeneficiaryDetail.Mother -> stringResource(R.string.beneficiary_type_mother)
              is BeneficiaryDetail.Child -> stringResource(R.string.beneficiary_type_child)
            },
            containerColor = accentColor,
          )
        }
        HorizontalDivider(color = NeutralG50, modifier = Modifier.padding(vertical = Dimens.TinySpacing))
        DetailRow(stringResource(R.string.beneficiary_label_registration_date), beneficiary.registrationDate)
        when (beneficiary) {
          is BeneficiaryDetail.Child -> {
            DetailRow(stringResource(R.string.beneficiary_label_birthdate), beneficiary.birthdate)
            DetailRow(stringResource(R.string.beneficiary_label_phone), beneficiary.phone)
          }
          is BeneficiaryDetail.Mother -> {
            DetailRow(stringResource(R.string.beneficiary_label_edd), beneficiary.edd)
            DetailRow(stringResource(R.string.beneficiary_label_lmp), beneficiary.lmp)
            DetailRow(stringResource(R.string.beneficiary_label_phone), beneficiary.phone)
            MotherVitalsRow(beneficiary)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeutralG200)
    Text(text = value, style = MaterialTheme.typography.bodyMedium, color = NeutralG400)
  }
}

@Composable
private fun MotherVitalsRow(mother: BeneficiaryDetail.Mother) {
  Surface(
    color = PrimarySurface,
    shape = RoundedCornerShape(Dimens.SmallRadius),
    modifier = Modifier.fillMaxWidth().padding(top = Dimens.TinySpacing),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      VitalStat(stringResource(R.string.beneficiary_label_height_short), "%.2f".format(Locale.US, mother.heightCm))
      VitalStat(stringResource(R.string.beneficiary_label_weight_short), "%.2f".format(Locale.US, mother.weightKg))
      VitalStat(stringResource(R.string.beneficiary_label_bmi_short), "%.2f".format(Locale.US, mother.bmi))
    }
  }
}

@Composable
private fun VitalStat(label: String, value: String) {
  Row {
    Text(text = "$label: ", style = MaterialTheme.typography.labelLarge, color = Primary)
    Text(text = value, style = MaterialTheme.typography.labelLarge, color = NeutralG400)
  }
}

private const val TABLET_GRID_COLUMNS = 2
