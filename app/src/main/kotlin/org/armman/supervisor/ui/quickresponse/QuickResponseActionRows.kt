package org.armman.supervisor.ui.quickresponse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens

@Composable
internal fun ApproveRejectRow(isDeciding: Boolean, enabled: Boolean, onReject: () -> Unit, onApprove: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = onReject, enabled = enabled, modifier = Modifier.weight(1f)) {
      Text(text = stringResource(R.string.quick_response_action_reject))
    }
    PrimaryButton(
      text = stringResource(R.string.quick_response_action_approve),
      onClick = onApprove,
      enabled = enabled,
      loading = isDeciding,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun TransferCloseRow(isDeciding: Boolean, enabled: Boolean, onTransfer: () -> Unit, onClose: () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = onTransfer, enabled = enabled, modifier = Modifier.weight(1f)) {
      Text(text = stringResource(R.string.quick_response_action_transfer))
    }
    PrimaryButton(
      text = stringResource(R.string.quick_response_action_close),
      onClick = onClose,
      enabled = enabled,
      loading = isDeciding,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
internal fun OkayRow(isDeciding: Boolean, enabled: Boolean, onOkay: () -> Unit) {
  PrimaryButton(
    text = stringResource(R.string.quick_response_action_okay),
    onClick = onOkay,
    enabled = enabled,
    loading = isDeciding,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
internal fun RejectNotesRow(
  notes: String,
  onNotesChange: (String) -> Unit,
  isDeciding: Boolean,
  enabled: Boolean,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
    AppTextField(
      value = notes,
      onValueChange = onNotesChange,
      label = stringResource(R.string.quick_response_field_supervisor_notes),
      placeholder = stringResource(R.string.quick_response_supervisor_notes_placeholder),
      enabled = enabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.weight(1f)) {
        Text(text = stringResource(R.string.cancel))
      }
      PrimaryButton(
        text = stringResource(R.string.quick_response_action_reject),
        onClick = onConfirm,
        enabled = enabled,
        loading = isDeciding,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
