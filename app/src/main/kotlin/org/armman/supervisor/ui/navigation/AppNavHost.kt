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
import org.armman.supervisor.ui.assignitem.AddItemTransactionScreen
import org.armman.supervisor.ui.assignitem.AssignItemDetailScreen
import org.armman.supervisor.ui.assignitem.AssignItemScreen
import org.armman.supervisor.ui.callsheet.CallHistoryScreen
import org.armman.supervisor.ui.callsheet.CallOutcomeScreen
import org.armman.supervisor.ui.callsheet.CallSheetScreen
import org.armman.supervisor.ui.components.PlaceholderScreen
import org.armman.supervisor.ui.dashboard.DashboardScreen
import org.armman.supervisor.ui.login.LoginScreen
import org.armman.supervisor.ui.meetingtraining.AttendanceScreen
import org.armman.supervisor.ui.meetingtraining.MeetingDetailScreen
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingScreen
import org.armman.supervisor.ui.meetingtraining.RescheduleMeetingScreen
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingScreen
import org.armman.supervisor.ui.settings.SettingsScreen

object Routes {
  const val LOGIN = "login"
  const val LOGIN_LOGGED_OUT_ARG = "loggedOut"
  const val LOGIN_ROUTE = "$LOGIN?$LOGIN_LOGGED_OUT_ARG={$LOGIN_LOGGED_OUT_ARG}"
  const val DASHBOARD = "dashboard"
  const val ITEMS = "items"
  const val ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG = "sakhiId"
  const val ADD_ITEM_TRANSACTION_EDIT_ID_ARG = "editTransactionId"
  const val ASSIGN_ITEM_DETAIL = "assign_item_detail/{$ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG}"
  const val ADD_ITEM_TRANSACTION =
    "add_item_transaction/{$ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG}?$ADD_ITEM_TRANSACTION_EDIT_ID_ARG={$ADD_ITEM_TRANSACTION_EDIT_ID_ARG}"
  const val MEETING_TRAINING = "meeting_training"
  const val MEETING_DETAIL_EVENT_ID_ARG = "eventId"
  const val SCHEDULE_MEETING = "schedule_meeting"
  const val MEETING_DETAIL = "meeting_detail/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val RESCHEDULE_MEETING = "reschedule_meeting/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val MEETING_ATTENDANCE = "meeting_attendance/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val CALL_SHEET = "call_sheet"
  const val CALL_SHEET_SAKHI_ID_ARG = "sakhiId"
  const val CALL_HISTORY = "call_history/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val CALL_OUTCOME = "call_outcome/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val QUICK_RESPONSE = "quick_response"
  const val PROFILE = "profile"
  const val SETTINGS = "settings"
  const val NOTIFICATIONS = "notifications"

  fun assignItemDetail(sakhiId: String) = "assign_item_detail/$sakhiId"

  fun addItemTransaction(sakhiId: String, editTransactionId: String? = null) =
    "add_item_transaction/$sakhiId" + if (editTransactionId != null) "?$ADD_ITEM_TRANSACTION_EDIT_ID_ARG=$editTransactionId" else ""

  fun callHistory(sakhiId: String) = "call_history/$sakhiId"

  fun callOutcome(sakhiId: String) = "call_outcome/$sakhiId"

  fun meetingDetail(eventId: String) = "meeting_detail/$eventId"

  fun rescheduleMeeting(eventId: String) = "reschedule_meeting/$eventId"

  fun meetingAttendance(eventId: String) = "meeting_attendance/$eventId"
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
      AssignItemScreen(
        onBack = { navController.popBackStack() },
        onSakhiSelected = { sakhi -> navController.navigate(Routes.assignItemDetail(sakhi.id)) },
      )
    }
    composable(
      Routes.ASSIGN_ITEM_DETAIL,
      arguments = listOf(navArgument(Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
      val sakhiId = backStackEntry.arguments?.getString(Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG).orEmpty()
      AssignItemDetailScreen(
        onBack = { navController.popBackStack() },
        onAddTransaction = { navController.navigate(Routes.addItemTransaction(sakhiId)) },
        onEditTransaction = { transactionId ->
          navController.navigate(Routes.addItemTransaction(sakhiId, transactionId))
        },
      )
    }
    composable(
      Routes.ADD_ITEM_TRANSACTION,
      arguments = listOf(
        navArgument(Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.ADD_ITEM_TRANSACTION_EDIT_ID_ARG) {
          type = NavType.StringType
          nullable = true
          defaultValue = null
        },
      ),
    ) {
      AddItemTransactionScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
    }
    composable(Routes.MEETING_TRAINING) {
      MeetingTrainingScreen(
        onBack = { navController.popBackStack() },
        onNewMeeting = { navController.navigate(Routes.SCHEDULE_MEETING) },
        onNewTraining = { /* Training is not yet implemented */ },
        onEventSelected = { eventId -> navController.navigate(Routes.meetingDetail(eventId)) },
      )
    }
    composable(Routes.SCHEDULE_MEETING) {
      ScheduleMeetingScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
    }
    composable(
      Routes.MEETING_DETAIL,
      arguments = listOf(navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
      val eventId = backStackEntry.arguments?.getString(Routes.MEETING_DETAIL_EVENT_ID_ARG).orEmpty()
      MeetingDetailScreen(
        onBack = { navController.popBackStack() },
        onReschedule = { navController.navigate(Routes.rescheduleMeeting(eventId)) },
        onAttendance = { navController.navigate(Routes.meetingAttendance(eventId)) },
      )
    }
    composable(
      Routes.RESCHEDULE_MEETING,
      arguments = listOf(navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType }),
    ) {
      RescheduleMeetingScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
    }
    composable(
      Routes.MEETING_ATTENDANCE,
      arguments = listOf(navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType }),
    ) {
      AttendanceScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
      )
    }
    composable(Routes.CALL_SHEET) {
      CallSheetScreen(
        onBack = { navController.popBackStack() },
        onSakhiSelected = { sakhi -> navController.navigate(Routes.callHistory(sakhi.id)) },
      )
    }
    composable(
      Routes.CALL_HISTORY,
      arguments = listOf(navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
      val sakhiId = backStackEntry.arguments?.getString(Routes.CALL_SHEET_SAKHI_ID_ARG).orEmpty()
      CallHistoryScreen(
        onBack = { navController.popBackStack() },
        onCallClick = { navController.navigate(Routes.callOutcome(sakhiId)) },
      )
    }
    composable(
      Routes.CALL_OUTCOME,
      arguments = listOf(navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) {
      CallOutcomeScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
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
