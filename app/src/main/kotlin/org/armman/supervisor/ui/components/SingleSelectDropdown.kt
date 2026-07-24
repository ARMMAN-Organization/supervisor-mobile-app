package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG75
import org.armman.supervisor.ui.theme.White

/**
 * Generic read-only single-select dropdown (an [ExposedDropdownMenuBox] wrapper) — reusable for
 * any "pick one of a list" filter (location, category, ...) instead of a feature-local dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleSelectDropdown(
  options: List<T>,
  selected: T?,
  optionLabel: (T) -> String,
  placeholder: String,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val canExpand = options.isNotEmpty()
  val displayText = selected?.let(optionLabel) ?: placeholder

  ExposedDropdownMenuBox(
    expanded = expanded && canExpand,
    onExpandedChange = { if (canExpand) expanded = it },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = displayText,
      onValueChange = {},
      readOnly = true,
      enabled = canExpand,
      trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
      textStyle = MaterialTheme.typography.bodyMedium,
      shape = RoundedCornerShape(Dimens.InputFieldRadius),
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = White,
        focusedContainerColor = White,
        unfocusedBorderColor = NeutralG75,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
      ),
      modifier = Modifier.menuAnchor().fillMaxWidth().height(Dimens.SearchBarHeight),
    )
    ExposedDropdownMenu(expanded = expanded && canExpand, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(optionLabel(option)) },
          onClick = {
            onSelected(option)
            expanded = false
          },
        )
      }
    }
  }
}
