package org.armman.supervisor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.armman.supervisor.ui.home.HomeScreen
import org.armman.supervisor.ui.login.LoginScreen
import org.armman.supervisor.ui.settings.SettingsScreen

object Routes {
  const val LOGIN = "login"
  const val LOGIN_LOGGED_OUT_ARG = "loggedOut"
  const val LOGIN_ROUTE = "$LOGIN?$LOGIN_LOGGED_OUT_ARG={$LOGIN_LOGGED_OUT_ARG}"
  const val HOME = "home"
  const val SETTINGS = "settings"
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
          navController.navigate(Routes.HOME) {
            popUpTo(Routes.LOGIN_ROUTE) { inclusive = true }
          }
        },
        showLogoutBanner = backStackEntry.arguments?.getBoolean(Routes.LOGIN_LOGGED_OUT_ARG) ?: false,
      )
    }
    composable(Routes.HOME) {
      HomeScreen()
    }
    composable(Routes.SETTINGS) {
      SettingsScreen(
        onBack = { navController.popBackStack() },
        onLoggedOut = {
          navController.navigate("${Routes.LOGIN}?${Routes.LOGIN_LOGGED_OUT_ARG}=true") {
            popUpTo(Routes.HOME) { inclusive = true }
          }
        },
      )
    }
  }
}
