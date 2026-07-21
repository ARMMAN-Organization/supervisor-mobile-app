package org.armman.supervisor.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.SerifTitle
import org.armman.supervisor.ui.theme.White

/** Choose Language dialog: English / Marathi radio rows + Apply. */
@Composable
internal fun LanguageDialog(
  onSelected: (AppLanguage) -> Unit,
  onDismiss: () -> Unit,
) {
  val currentTag = remember {
    AppCompatDelegate.getApplicationLocales().toLanguageTags()
      .ifBlank { LocaleListCompat.getDefault().toLanguageTags() }
  }
  var selected by remember {
    mutableStateOf(
      if (currentTag.startsWith(AppLanguage.MARATHI.tag)) AppLanguage.MARATHI else AppLanguage.ENGLISH,
    )
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = RoundedCornerShape(Dimens.CardRadius), color = White) {
      Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
        Text(
          text = stringResource(R.string.settings_choose_language),
          style = SerifTitle,
          color = NeutralG400,
        )
        LanguageOption(
          label = stringResource(R.string.settings_language_english),
          selected = selected == AppLanguage.ENGLISH,
          onClick = { selected = AppLanguage.ENGLISH },
        )
        LanguageOption(
          label = stringResource(R.string.settings_language_marathi),
          selected = selected == AppLanguage.MARATHI,
          onClick = { selected = AppLanguage.MARATHI },
        )
        PrimaryButton(
          text = stringResource(R.string.settings_apply_language),
          onClick = { onSelected(selected) },
          modifier = Modifier.padding(top = Dimens.ItemSpacing),
          containerColor = DashboardHeaderGreen,
        )
      }
    }
  }
}

@Composable
private fun LanguageOption(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(top = Dimens.ItemSpacing),
  ) {
    Image(
      painter = painterResource(
        if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected,
      ),
      colorFilter = if (selected) ColorFilter.tint(DashboardHeaderGreen) else null,
      contentDescription = null,
      modifier = Modifier.size(Dimens.RadioIconSize),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.titleMedium,
      color = NeutralG400,
      modifier = Modifier.padding(start = Dimens.ItemSpacing),
    )
  }
}
