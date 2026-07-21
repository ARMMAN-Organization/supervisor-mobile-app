package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG100
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.NeutralG75

/**
 * Style-guide text field: label above an input with optional error subtext
 * below (title + placeholder + subtext states from the design system). Set
 * [filled] to render a borderless grey-filled field (e.g. the Login screen)
 * instead of the default outlined style.
 */
@Composable
fun AppTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  placeholder: String,
  modifier: Modifier = Modifier,
  errorText: String? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  trailingIcon: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
  filled: Boolean = false,
  labelColor: Color = NeutralG400,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = labelColor,
      modifier = Modifier.padding(bottom = Dimens.ExtraSmallSpacing),
    )
    OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = { Text(placeholder, color = NeutralG100) },
      isError = errorText != null,
      singleLine = true,
      enabled = enabled,
      keyboardOptions = keyboardOptions,
      keyboardActions = keyboardActions,
      visualTransformation = visualTransformation,
      trailingIcon = trailingIcon,
      shape = RoundedCornerShape(Dimens.SmallRadius),
      colors = if (filled) {
        OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = Color.Transparent,
          focusedBorderColor = Color.Transparent,
          errorBorderColor = MaterialTheme.colorScheme.error,
          unfocusedContainerColor = NeutralG50,
          focusedContainerColor = NeutralG50,
          errorContainerColor = NeutralG50,
        )
      } else {
        OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = NeutralG75,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          errorBorderColor = MaterialTheme.colorScheme.error,
        )
      },
      modifier = Modifier.fillMaxWidth(),
    )
    if (errorText != null) {
      Text(
        text = errorText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = Dimens.ExtraSmallSpacing),
      )
    }
  }
}
