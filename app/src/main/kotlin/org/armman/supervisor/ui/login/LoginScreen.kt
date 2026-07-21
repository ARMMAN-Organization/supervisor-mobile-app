package org.armman.supervisor.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.StatusBanner
import org.armman.supervisor.ui.components.StatusBannerVariant
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.Primary
import org.armman.supervisor.ui.theme.White

/**
 * Login screen mirrored from the shared `activity_login.xml` design spec: a fixed-height
 * dark-green header ([Dimens.LoginHeaderHeight], matching [DashboardHeaderGreen]) with a
 * circular white logo card centered in it, and a white all-corners-rounded card
 * ([Dimens.LoginCardRadius]) that overlaps the header by [Dimens.LoginCardOverlap] with
 * [Dimens.LoginCardMargin] side margins, holding the Username/Password fields and pill
 * Login button. Colors use this app's [DashboardHeaderGreen] token (not the spec's literal
 * hex) to stay consistent with the Dashboard header.
 */
@Composable
fun LoginScreen(
  onLoginSuccess: () -> Unit,
  showLogoutBanner: Boolean = false,
  viewModel: LoginViewModel = hiltViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(state.loginSucceeded) {
    if (state.loginSucceeded) {
      viewModel.onLoginHandled()
      onLoginSuccess()
    }
  }

  Surface(color = White, modifier = Modifier.fillMaxSize()) {
    if (state.isCheckingSession) {
      // Avoid reading EncryptedSharedPreferences result on the main thread; show a spinner
      // while the async session check runs in the background.
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Primary)
      }
    } else {
      BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding()) {
        val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        ) {
          LoginHeader()
          LoginCard(
            state = state,
            showLogoutBanner = showLogoutBanner,
            onUsernameChanged = viewModel::onUsernameChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onLoginClicked = viewModel::onLoginClicked,
            modifier = Modifier
              .align(Alignment.CenterHorizontally)
              .offset(y = -Dimens.LoginCardOverlap)
              .widthIn(max = if (isTablet) Dimens.ContentMaxWidthTablet else Dp.Unspecified)
              .padding(horizontal = Dimens.LoginCardMargin),
          )
        }
      }
    }
  }
}

@Composable
private fun LoginHeader() {
  Surface(
    color = DashboardHeaderGreen,
    modifier = Modifier
      .fillMaxWidth()
      .height(Dimens.LoginHeaderHeight),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding(),
    ) {
      Surface(
        color = White,
        shape = CircleShape,
        modifier = Modifier.size(Dimens.LoginLogoOuterSize),
      ) {
        Image(
          painter = painterResource(R.drawable.arogya),
          contentDescription = stringResource(R.string.login_logo_content_description),
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxSize()
            .padding((Dimens.LoginLogoOuterSize - Dimens.LoginLogoInnerSize) / 2),
        )
      }
      Spacer(Modifier.height(Dimens.ItemSpacing))
      Text(
        text = stringResource(R.string.login_title),
        style = MaterialTheme.typography.titleLarge,
        color = White,
      )
    }
  }
}

@Composable
private fun LoginCard(
  state: LoginUiState,
  showLogoutBanner: Boolean,
  onUsernameChanged: (String) -> Unit,
  onPasswordChanged: (String) -> Unit,
  onLoginClicked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.LoginCardRadius),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.LoginCardPadding),
    ) {
      Text(
        text = stringResource(R.string.login_heading),
        style = MaterialTheme.typography.headlineMedium,
        color = DashboardHeaderGreen,
      )
      Spacer(Modifier.height(Dimens.ItemSpacing))
      AppTextField(
        value = state.username,
        onValueChange = onUsernameChanged,
        label = stringResource(R.string.login_username_label),
        placeholder = stringResource(R.string.login_username_placeholder),
        errorText = state.usernameError?.let { stringResource(it) },
        enabled = !state.isSubmitting,
        filled = true,
        labelColor = DashboardHeaderGreen,
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Text,
          imeAction = ImeAction.Next,
        ),
      )
      Spacer(Modifier.height(Dimens.ItemSpacing))
      AppTextField(
        value = state.password,
        onValueChange = onPasswordChanged,
        label = stringResource(R.string.login_password_label),
        placeholder = stringResource(R.string.login_password_placeholder),
        errorText = state.passwordError?.let { stringResource(it) },
        enabled = !state.isSubmitting,
        filled = true,
        labelColor = DashboardHeaderGreen,
        visualTransformation = if (passwordVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        },
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
              imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
              contentDescription = stringResource(
                if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password,
              ),
            )
          }
        },
        keyboardOptions = KeyboardOptions(
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onLoginClicked() }),
      )
      Spacer(Modifier.height(Dimens.ItemSpacing))
      PrimaryButton(
        text = stringResource(R.string.login_button),
        onClick = onLoginClicked,
        loading = state.isSubmitting,
        containerColor = DashboardHeaderGreen,
        modifier = Modifier.padding(horizontal = Dimens.LoginButtonMarginH),
      )

      when {
        state.loginError != null -> {
          Spacer(Modifier.height(Dimens.ItemSpacing))
          StatusBanner(
            message = stringResource(state.loginError),
            variant = StatusBannerVariant.Error,
          )
        }
        showLogoutBanner -> {
          Spacer(Modifier.height(Dimens.ItemSpacing))
          StatusBanner(
            message = stringResource(R.string.login_logout_success),
            variant = StatusBannerVariant.Success,
          )
        }
      }
    }
  }
}
