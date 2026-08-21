package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/**
 * Common Scaffold for every Call Sheet drill-down screen: brand header, then a
 * loading/error/empty/[items] switch, rendering each item with [itemContent]. Shared by
 * [DueVisitScreen], [FollowupPendingScreen], [ClosurePendingScreen], [HighRiskListScreen].
 */
@Composable
fun <T> DrillDownScaffold(
  title: String,
  isLoading: Boolean,
  errorMessage: String?,
  onRetry: () -> Unit,
  items: List<T>?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  itemContent: @Composable (T) -> Unit,
) {
  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = title, onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when {
        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        errorMessage != null -> Column(
          modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
          verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(text = errorMessage, style = MaterialTheme.typography.bodyLarge)
          PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
        }
        items.isNullOrEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(text = stringResource(R.string.call_sheet_drilldown_empty), style = MaterialTheme.typography.bodyLarge)
        }
        else -> LazyColumn(
          modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
          verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
          items(items) { item -> itemContent(item) }
        }
      }
    }
  }
}

/** A labeled field row (label left, value right) used inside every drill-down item card. */
@Composable
fun DrillDownFieldRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.TinySpacing)) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      color = NeutralG400,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = NeutralG400,
      modifier = Modifier.weight(1f),
    )
  }
}

/** Card chrome for a drill-down item, optionally tinted (e.g. [org.armman.supervisor.ui.theme.RiskHighSurface]
 * for due/overdue rows, [org.armman.supervisor.ui.theme.StatusSuccessSurface] for on-track rows). */
@Composable
fun DrillDownCard(backgroundColor: Color = White, content: @Composable () -> Unit) {
  Surface(
    color = backgroundColor,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) { content() }
  }
}

/** A highlighted single-line banner row inside a [DrillDownCard] (e.g. "Over Due (in days): 3"). */
@Composable
fun DrillDownBannerRow(label: String, value: String, backgroundColor: Color) {
  Row(
    modifier = Modifier.fillMaxWidth().background(backgroundColor).padding(Dimens.SmallSpacing),
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
  }
}
