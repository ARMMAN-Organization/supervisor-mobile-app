package org.armman.supervisor.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG100
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG75
import org.armman.supervisor.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Label + read-only date box that opens the platform date picker on tap. Mirrors the Sakhi app's
 * date field; the selected date is formatted "dd MMM yyyy" and returned via [onDateSelected].
 */
@Composable
fun AppDateField(
  label: String,
  placeholder: String,
  value: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
  val openPicker = {
    val seed = value ?: LocalDate.now()
    DatePickerDialog(
      context,
      { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
      seed.year,
      seed.monthValue - 1,
      seed.dayOfMonth,
    ).show()
  }
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = NeutralG400,
      modifier = Modifier.padding(bottom = Dimens.TinySpacing),
    )
    val dateFieldShape = RoundedCornerShape(Dimens.InputFieldRadius)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .height(Dimens.SearchBarHeight)
        .clip(dateFieldShape)
        .background(White)
        .border(Dimens.HairlineWidth, NeutralG75, dateFieldShape)
        .clickable(onClick = openPicker)
        .padding(horizontal = Dimens.ItemSpacing),
    ) {
      Text(
        text = value?.format(formatter) ?: placeholder,
        style = MaterialTheme.typography.bodyLarge,
        color = if (value != null) NeutralG400 else NeutralG100,
      )
    }
  }
}
