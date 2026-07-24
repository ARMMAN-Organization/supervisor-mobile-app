package org.armman.supervisor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.PlaceholderScreen
import org.armman.supervisor.ui.dashboard.DashboardScreen
import org.armman.supervisor.ui.login.LoginScreen
import org.armman.supervisor.ui.settings.SettingsScreen

object Routes {
  const val LOGIN = "login"
  const val LOGIN_LOGGED_OUT_ARG = "loggedOut"
  const val LOGIN_ROUTE = "$LOGIN?$LOGIN_LOGGED_OUT_ARG={$LOGIN_LOGGED_OUT_ARG}"
  const val DASHBOARD = "dashboard"
  const val ITEMS = "items"
  const val MEETING_TRAINING = "meeting_training"
  const val CALL_SHEET = "call_sheet"
  const val QUICK_RESPONSE = "quick_response"
  const val PROFILE = "profile"
  const val SETTINGS = "settings"
  const val NOTIFICATIONS = "notifications"
}

/** Top-level navigation graph for the Supervisor app. */
@Composable
fun AppNavHost() {
  val navController = rememberNavController()
  NavHost(navController = navController, startDestination = Routes.LOGIN_ROUTE) {
    composable(
      Routes.LOGIN_ROUTE,
      arguments = listOf(navArgument(Routes.LOGIN_LOGGED_OUT_ARG) { type = NavType.BoolType; defaultValue = false }),
    ) { backStackEntry ->
      LoginScreen(
        onLoginSuccess = {
          navController.navigate(Routes.DASHBOARD) {
            popUpTo(Routes.LOGIN_ROUTE) { inclusive = true }
          }
        },
        showLogoutBanner = backStackEntry.arguments?.getBoolean(Routes.LOGIN_LOGGED_OUT_ARG) ?: false,
      )
    }
    composable(Routes.DASHBOARD) {
      DashboardScreen(onNavigate = { route -> navController.navigate(route) })
    }
    composable(Routes.ITEMS) {
      PlaceholderStub(navController, R.string.quick_action_items)
    }
    composable(Routes.MEETING_TRAINING) {
      PlaceholderStub(navController, R.string.quick_action_meeting_training)
    }
    composable(Routes.CALL_SHEET) {
      PlaceholderStub(navController, R.string.quick_action_call_sheet)
    }
    composable(Routes.QUICK_RESPONSE) {
      PlaceholderStub(navController, R.string.quick_action_quick_response)
    }
    composable(Routes.PROFILE) {
      PlaceholderStub(navController, R.string.profile_title)
    }
    composable(Routes.SETTINGS) {
      SettingsScreen(
        onBack = { navController.popBackStack() },
        onLoggedOut = {
          navController.navigate("${Routes.LOGIN}?${Routes.LOGIN_LOGGED_OUT_ARG}=true") {
            popUpTo(Routes.DASHBOARD) { inclusive = true }
          }
        },
      )
    }
    composable(Routes.NOTIFICATIONS) {
      PlaceholderStub(navController, R.string.notifications_title)
    }
  }
}

@Composable
private fun PlaceholderStub(navController: NavHostController, titleRes: Int) {
  PlaceholderScreen(title = stringResource(titleRes), onBack = { navController.popBackStack() })
}
