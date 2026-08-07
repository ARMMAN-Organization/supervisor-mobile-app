package org.armman.supervisor.ui.quickresponse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.PrimarySurface
import org.armman.supervisor.ui.theme.RiskLow
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One Quick Response card: date, request type pill, project, Sakhi name, status pill (SRS FR-SV-4.6). */
@Composable
fun QuickResponseRequestCard(request: QuickResponseRequest, onCardClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier
      .fillMaxWidth()
      .softShadow(Dimens.CardRadius)
      .semantics { role = Role.Button }
      .clickable(onClick = onCardClick),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.TilePadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    ) {
      Text(
        text = request.requestedAtEpochMillis.toDisplayDate(),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralG400,
      )
      LabeledRow(labelRes = R.string.quick_response_field_request_type) {
        StatusPill(text = stringResource(request.requestType.labelRes()), containerColor = PrimarySurface, contentColor = RiskLow)
      }
      LabeledRow(labelRes = R.string.quick_response_field_project) {
        Text(text = request.projectName, style = MaterialTheme.typography.bodyLarge, color = NeutralG400)
      }
      LabeledRow(labelRes = R.string.quick_response_field_sakhi_name) {
        Text(text = request.sakhiName, style = MaterialTheme.typography.bodyLarge, color = NeutralG400)
      }
      LabeledRow(labelRes = R.string.quick_response_field_request_status) {
        StatusPill(
          text = stringResource(request.status.labelRes()),
          containerColor = NeutralG50,
          contentColor = NeutralG400,
        )
      }
    }
  }
}

@Composable
private fun LabeledRow(@StringRes labelRes: Int, content: @Composable () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
    Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.titleMedium,
      color = NeutralG400,
    )
    content()
  }
}

@Composable
private fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
  Surface(color = containerColor, shape = RoundedCornerShape(Dimens.ChipHeight), contentColor = contentColor) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(horizontal = Dimens.PillButtonPaddingH, vertical = Dimens.TinySpacing),
    )
  }
}

private fun Long.toDisplayDate(): String =
  SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(this))
