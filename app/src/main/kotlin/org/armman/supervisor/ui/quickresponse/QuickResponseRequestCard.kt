package org.armman.supervisor.ui.quickresponse

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.PrimarySurface
import org.armman.supervisor.ui.theme.RiskLow
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The action a Supervisor took on a card, whatever its [QuickResponseCtaKind]. */
sealed interface QuickResponseCardAction {
  data class Decide(val decision: QuickResponseDecision, val notes: String?) : QuickResponseCardAction
  data class Escalate(val action: QuickResponseEscalationAction) : QuickResponseCardAction
  data object Acknowledge : QuickResponseCardAction
}

/**
 * One Quick Response card: base fields shown on every type (date, request type, project,
 * Sakhi name/contact, Pada name, request status, beneficiary name, risk details — SRS
 * FR-SV-4.1), per-type SRS detail fields ([QuickResponseCardDetail]), and a CTA row that varies
 * by [QuickResponseRequestType.ctaKind] — Approve/Reject, Transfer/Close (Missed Visit
 * Escalation, SRS FR-SV-4.3), or a single Okay (EDD Nearing, SRS FR-SV-4.8). Each field is a
 * label-above-value block rather than a side-by-side row — a side-by-side layout squeezed long
 * values (long names, multi-word status codes) into a cramped, wrapping pill next to the label.
 * Reject on a Closure Review card (the only type with an SRS-defined supervisor-notes field)
 * expands an inline notes field with its own confirm step, rather than firing immediately.
 *
 * [isDeciding] drives *this* card's button spinner; [actionsEnabled] independently controls
 * whether *this* card's buttons can be tapped at all — the ViewModel's in-flight guard blocks a
 * decision on any card while another card's decision is still in flight (avoiding double
 * submissions racing each other), so every other card must show its buttons as disabled too,
 * not just the one actually spinning.
 */
@Composable
fun QuickResponseRequestCard(
  request: QuickResponseRequest,
  onAction: (QuickResponseCardAction) -> Unit,
  isDeciding: Boolean,
  actionsEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  var isRejecting by remember(request.id) { mutableStateOf(false) }
  var rejectNotes by remember(request.id) { mutableStateOf("") }
  val supportsRejectNotes = request.detail is QuickResponseCardDetail.ClosureReview

  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(
      modifier = Modifier.padding(Dimens.TilePadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(text = stringResource(request.requestType.labelRes()), containerColor = PrimarySurface, contentColor = RiskLow)
          request.requestStatus?.let { status ->
            val label = status.quickResponseStatusLabelRes()?.let { stringResource(it) } ?: status
            StatusPill(text = label, containerColor = NeutralG50, contentColor = NeutralG200)
          }
        }
        Text(text = request.requestedAtEpochMillis.toDisplayDate(), style = MaterialTheme.typography.labelMedium, color = NeutralG200)
      }

      Column(verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)) {
        request.beneficiaryName?.let { FieldBlock(labelRes = R.string.quick_response_field_beneficiary_name, value = it) }
        request.sakhiName?.let { FieldBlock(labelRes = R.string.quick_response_field_sakhi_name, value = it) }
        request.sakhiId?.let { FieldBlock(labelRes = R.string.quick_response_field_sakhi_id, value = it) }
        request.sakhiPhoneNumber?.let { FieldBlock(labelRes = R.string.quick_response_field_sakhi_contact, value = it) }
        request.padaName?.let { FieldBlock(labelRes = R.string.quick_response_field_pada_name, value = it) }
        RiskDetailsBlock(request.riskConditions)
        CardDetailBlocks(request.detail)
      }

      HorizontalDivider(color = NeutralG50)

      if (isRejecting && supportsRejectNotes) {
        RejectNotesRow(
          notes = rejectNotes,
          onNotesChange = { rejectNotes = it },
          isDeciding = isDeciding,
          enabled = actionsEnabled,
          onCancel = { isRejecting = false },
          onConfirm = { onAction(QuickResponseCardAction.Decide(QuickResponseDecision.REJECT, rejectNotes)) },
        )
      } else {
        when (request.requestType.ctaKind()) {
          QuickResponseCtaKind.APPROVE_REJECT -> ApproveRejectRow(
            isDeciding = isDeciding,
            enabled = actionsEnabled,
            onReject = {
              if (supportsRejectNotes) isRejecting = true else onAction(QuickResponseCardAction.Decide(QuickResponseDecision.REJECT, null))
            },
            onApprove = { onAction(QuickResponseCardAction.Decide(QuickResponseDecision.APPROVE, null)) },
          )
          QuickResponseCtaKind.TRANSFER_CLOSE -> TransferCloseRow(
            isDeciding = isDeciding,
            enabled = actionsEnabled,
            onTransfer = { onAction(QuickResponseCardAction.Escalate(QuickResponseEscalationAction.TRANSFER)) },
            onClose = { onAction(QuickResponseCardAction.Escalate(QuickResponseEscalationAction.CLOSE)) },
          )
          QuickResponseCtaKind.OKAY -> OkayRow(
            isDeciding = isDeciding,
            enabled = actionsEnabled,
            onOkay = { onAction(QuickResponseCardAction.Acknowledge) },
          )
        }
      }
    }
  }
}

@Composable
private fun ApproveRejectRow(isDeciding: Boolean, enabled: Boolean, onReject: () -> Unit, onApprove: () -> Unit) {
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
private fun TransferCloseRow(isDeciding: Boolean, enabled: Boolean, onTransfer: () -> Unit, onClose: () -> Unit) {
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
private fun OkayRow(isDeciding: Boolean, enabled: Boolean, onOkay: () -> Unit) {
  PrimaryButton(
    text = stringResource(R.string.quick_response_action_okay),
    onClick = onOkay,
    enabled = enabled,
    loading = isDeciding,
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun RejectNotesRow(
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

/** Renders the extra fields specific to [detail]'s card type, if any (SRS FR-SV-4.2, 4.4, 4.5,
 * 4.7). [QuickResponseCardDetail.ReferralIncomplete] deliberately renders the same fields as
 * [QuickResponseCardDetail.AccompaniedReferral] — they share the underlying `referrals` table —
 * but stays a distinct branch since the two are separate request types server-side. */
@Composable
private fun CardDetailBlocks(detail: QuickResponseCardDetail?) {
  when (detail) {
    is QuickResponseCardDetail.LmpChange -> {
      detail.oldLmpDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_old_lmp, value = it.toDisplayDateOnly()) }
      detail.newLmpDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_new_lmp, value = it.toDisplayDateOnly()) }
      // detail.sonographyImageAssetId: no image-loading capability in the app yet — see
      // QUICK_RESPONSE_BACKEND_TASKS.md. Every record checked so far has this null anyway.
    }
    is QuickResponseCardDetail.ClosureReview -> {
      detail.reasonLabel?.let { FieldBlock(labelRes = R.string.quick_response_field_closure_reason, value = it) }
      detail.closureDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_closure_date, value = it.toDisplayDateOnly()) }
      detail.supervisorNotes?.let { FieldBlock(labelRes = R.string.quick_response_field_supervisor_notes, value = it) }
    }
    is QuickResponseCardDetail.Reopen -> {
      detail.reasonForReopen?.let { FieldBlock(labelRes = R.string.quick_response_field_reopen_reason, value = it) }
    }
    is QuickResponseCardDetail.AccompaniedReferral -> {
      detail.facilityName?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility, value = it) }
      detail.facilityType?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility_type, value = it) }
      detail.referralDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_date, value = it.toDisplayDateOnly()) }
      // detail.photoEvidenceAssetId: no image-loading capability in the app yet — see
      // QUICK_RESPONSE_BACKEND_TASKS.md. Every record checked so far has this null anyway.
    }
    is QuickResponseCardDetail.ReferralIncomplete -> {
      detail.facilityName?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility, value = it) }
      detail.facilityType?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility_type, value = it) }
      detail.referralDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_date, value = it.toDisplayDateOnly()) }
      detail.visitReference?.let { FieldBlock(labelRes = R.string.quick_response_field_visit_reference, value = it) }
      detail.referralsMissedCount?.let { FieldBlock(labelRes = R.string.quick_response_field_referrals_missed_count, value = it.toString()) }
      detail.reason?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_incomplete_reason, value = it) }
    }
    is QuickResponseCardDetail.MissedVisitEscalation -> {
      detail.visitType?.let { FieldBlock(labelRes = R.string.quick_response_field_visit_type, value = it) }
    }
    is QuickResponseCardDetail.EddNearing -> {
      detail.eddDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_edd_date, value = it.toDisplayDateOnly()) }
      detail.reason?.let { FieldBlock(labelRes = R.string.quick_response_field_edd_reason, value = it) }
    }
    null -> Unit
  }
}

/** Renders the SRS "Beneficiary risk details" field as one comma-joined value, if any. */
@Composable
private fun RiskDetailsBlock(riskConditions: List<QuickResponseRiskCondition>) {
  if (riskConditions.isEmpty()) return
  val value = riskConditions.joinToString { condition -> condition.latestGrade?.let { "${condition.conditionName} ($it)" } ?: condition.conditionName }
  FieldBlock(labelRes = R.string.quick_response_field_risk_details, value = value)
}

/** One label/value field, the label in a fixed-width column so every field's value starts at
 * the same x-position — a plain inline "Label: value" left each row's value starting wherever
 * that label's text happened to end, which read as uneven from row to row. */
@Composable
private fun FieldBlock(@StringRes labelRes: Int, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
    Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.SemiBold,
      color = NeutralG200,
      modifier = Modifier.width(Dimens.QuickResponseFieldLabelWidth),
    )
    Text(text = value, style = MaterialTheme.typography.bodyLarge, color = NeutralG400, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
  Surface(color = containerColor, shape = RoundedCornerShape(Dimens.ChipHeight), contentColor = contentColor) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = Dimens.PillButtonPaddingH, vertical = Dimens.TinySpacing),
    )
  }
}

private fun Long.toDisplayDate(): String =
  SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(this))

private fun Long.toDisplayDateOnly(): String =
  SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))
