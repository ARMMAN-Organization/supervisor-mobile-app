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
import org.armman.supervisor.ui.beneficiaries.BeneficiaryListScreen
import org.armman.supervisor.ui.beneficiarydatadownload.BeneficiaryDataDownloadScreen
import org.armman.supervisor.ui.masterdata.MasterDataDownloadScreen
import org.armman.supervisor.ui.callsheet.AddReasonScreen as CallSheetAddReasonScreen
import org.armman.supervisor.ui.callsheet.CallHistoryScreen
import org.armman.supervisor.ui.callsheet.CallOutcomeScreen
import org.armman.supervisor.ui.callsheet.CallSheetScreen
import org.armman.supervisor.ui.callsheet.CallSheetStatKind
import org.armman.supervisor.ui.callsheet.ClosurePendingScreen
import org.armman.supervisor.ui.callsheet.DueVisitKind
import org.armman.supervisor.ui.callsheet.DueVisitScreen
import org.armman.supervisor.ui.callsheet.FollowupPendingScreen
import org.armman.supervisor.ui.callsheet.HighRiskListScreen
import org.armman.supervisor.ui.callsheet.HighRiskType
import org.armman.supervisor.ui.callsheet.LastSyncReasonScreen
import org.armman.supervisor.ui.callsheet.ReasonContext
import org.armman.supervisor.ui.components.PlaceholderScreen
import org.armman.supervisor.ui.dashboard.DashboardScreen
import org.armman.supervisor.ui.login.LoginScreen
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.ui.meetingtraining.AddTrainingTopicsScreen
import org.armman.supervisor.ui.meetingtraining.AttendanceScreen
import org.armman.supervisor.ui.meetingtraining.MarksScreen
import org.armman.supervisor.ui.meetingtraining.MeetingDetailScreen
import org.armman.supervisor.ui.meetingtraining.MeetingTrainingScreen
import org.armman.supervisor.ui.meetingtraining.RescheduleMeetingScreen
import org.armman.supervisor.ui.meetingtraining.ScheduleMeetingScreen
import org.armman.supervisor.ui.meetingtraining.ScheduleTrainingScreen
import org.armman.supervisor.ui.monitoringsummary.MonitoringSummaryScreen
import org.armman.supervisor.ui.quickresponse.AddReasonScreen as QuickResponseAddReasonScreen
import org.armman.supervisor.ui.quickresponse.QuickResponseScreen
import org.armman.supervisor.ui.registrations.RegistrationsScreen
import org.armman.supervisor.ui.risksummary.RiskSummaryScreen
import org.armman.supervisor.ui.settings.SettingsScreen
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetailScreen
import org.armman.supervisor.ui.visitsummary.VisitSummaryScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
  const val LOGIN = "login"
  const val LOGIN_LOGGED_OUT_ARG = "loggedOut"
  const val LOGIN_ROUTE = "$LOGIN?$LOGIN_LOGGED_OUT_ARG={$LOGIN_LOGGED_OUT_ARG}"
  const val DASHBOARD = "dashboard"
  const val VISIT_SUMMARY = "visit_summary"
  const val RISK_SUMMARY = "risk_summary"
  const val MONITORING_SUMMARY = "monitoring_summary"
  const val REGISTRATIONS = "registrations"
  const val SAKHI_BENEFICIARIES_SAKHI_ID_ARG = "sakhiId"
  const val SAKHI_BENEFICIARIES = "sakhi_beneficiaries/{$SAKHI_BENEFICIARIES_SAKHI_ID_ARG}"
  const val VILLAGE_RISK_DETAIL_VILLAGE_ID_ARG = "villageId"
  const val VILLAGE_RISK_DETAIL_VILLAGE_NAME_ARG = "villageName"
  const val VILLAGE_RISK_DETAIL_SAKHI_NAME_ARG = "sakhiName"
  const val VILLAGE_RISK_DETAIL =
    "village_risk_detail/{$VILLAGE_RISK_DETAIL_VILLAGE_ID_ARG}/{$VILLAGE_RISK_DETAIL_VILLAGE_NAME_ARG}" +
      "/{$VILLAGE_RISK_DETAIL_SAKHI_NAME_ARG}"
  const val ITEMS = "items"
  const val ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG = "sakhiId"
  const val ADD_ITEM_TRANSACTION_EDIT_ID_ARG = "editTransactionId"
  const val ASSIGN_ITEM_DETAIL = "assign_item_detail/{$ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG}"
  const val ADD_ITEM_TRANSACTION =
    "add_item_transaction/{$ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG}?$ADD_ITEM_TRANSACTION_EDIT_ID_ARG={$ADD_ITEM_TRANSACTION_EDIT_ID_ARG}"
  const val MEETING_TRAINING = "meeting_training"
  const val MEETING_DETAIL_EVENT_ID_ARG = "eventId"
  const val SCHEDULE_MEETING = "schedule_meeting"
  const val SCHEDULE_TRAINING = "schedule_training"
  const val MEETING_DETAIL = "meeting_detail/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val RESCHEDULE_MEETING = "reschedule_meeting/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val MEETING_ATTENDANCE = "meeting_attendance/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val ADD_TRAINING_TOPICS = "add_training_topics/{$MEETING_DETAIL_EVENT_ID_ARG}"
  const val GATHERING_ID_ARG = "gatheringId"
  const val MARKS_TYPE_ARG = "marksType"
  const val GATHERING_ATTENDANCE =
    "gathering_attendance/{$MEETING_DETAIL_EVENT_ID_ARG}/{$GATHERING_ID_ARG}"
  const val GATHERING_MARKS =
    "gathering_marks/{$MEETING_DETAIL_EVENT_ID_ARG}/{$GATHERING_ID_ARG}/{$MARKS_TYPE_ARG}"
  const val CALL_SHEET = "call_sheet"
  const val CALL_SHEET_SAKHI_ID_ARG = "sakhiId"
  const val CALL_HISTORY = "call_history/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val CALL_OUTCOME = "call_outcome/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val DUE_VISIT_KIND_ARG = "dueVisitKind"
  const val DUE_VISIT = "due_visit/{$CALL_SHEET_SAKHI_ID_ARG}/{$DUE_VISIT_KIND_ARG}"
  const val FOLLOWUP_PENDING = "followup_pending/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val CLOSURE_PENDING = "closure_pending/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val HIGH_RISK_TYPE_ARG = "highRiskType"
  const val HIGH_RISK_LIST = "high_risk_list/{$CALL_SHEET_SAKHI_ID_ARG}/{$HIGH_RISK_TYPE_ARG}"
  const val LAST_SYNC_REASON = "last_sync_reason/{$CALL_SHEET_SAKHI_ID_ARG}"
  const val REASON_CONTEXT_ARG = "reasonContext"
  const val REASON_ITEM_ID_ARG = "reasonItemId"
  const val CALL_SHEET_ADD_REASON =
    "call_sheet_add_reason/{$REASON_CONTEXT_ARG}?$CALL_SHEET_SAKHI_ID_ARG={$CALL_SHEET_SAKHI_ID_ARG}" +
      "&$REASON_ITEM_ID_ARG={$REASON_ITEM_ID_ARG}"
  const val QUICK_RESPONSE = "quick_response"
  const val QUICK_RESPONSE_REQUEST_ID_ARG = "requestId"
  const val QUICK_RESPONSE_ADD_REASON = "quick_response_add_reason/{$QUICK_RESPONSE_REQUEST_ID_ARG}"
  const val PROFILE = "profile"
  const val SETTINGS = "settings"
  const val BENEFICIARY_DATA_DOWNLOAD = "beneficiary_data_download"
  const val MASTER_DATA_DOWNLOAD = "master_data_download"
  const val NOTIFICATIONS = "notifications"

  fun sakhiBeneficiaries(sakhiId: String) = "sakhi_beneficiaries/$sakhiId"

  fun villageRiskDetail(villageId: String, villageName: String, sakhiName: String): String {
    val encodedVillageId = URLEncoder.encode(villageId, Charsets.UTF_8.name())
    val encodedVillageName = URLEncoder.encode(villageName, Charsets.UTF_8.name())
    val encodedSakhiName = URLEncoder.encode(sakhiName, Charsets.UTF_8.name())
    return "village_risk_detail/$encodedVillageId/$encodedVillageName/$encodedSakhiName"
  }

  fun assignItemDetail(sakhiId: String) = "assign_item_detail/$sakhiId"

  fun addItemTransaction(sakhiId: String, editTransactionId: String? = null) =
    "add_item_transaction/$sakhiId" + if (editTransactionId != null) "?$ADD_ITEM_TRANSACTION_EDIT_ID_ARG=$editTransactionId" else ""

  fun quickResponseAddReason(requestId: String) = "quick_response_add_reason/$requestId"

  fun callHistory(sakhiId: String) = "call_history/$sakhiId"

  fun callOutcome(sakhiId: String) = "call_outcome/$sakhiId"

  fun dueVisit(sakhiId: String, kind: DueVisitKind) = "due_visit/$sakhiId/${kind.name}"

  fun followupPending(sakhiId: String) = "followup_pending/$sakhiId"

  fun closurePending(sakhiId: String) = "closure_pending/$sakhiId"

  fun highRiskList(sakhiId: String, type: HighRiskType) = "high_risk_list/$sakhiId/${type.name}"

  fun lastSyncReason(sakhiId: String) = "last_sync_reason/$sakhiId"

  /** [sakhiId]/[itemId] are mutually exclusive per [ReasonContext] — Followup/Closure pass
   * [itemId], Last Sync passes [sakhiId] (see [org.armman.supervisor.ui.callsheet.ReasonSubmission]). */
  fun callSheetAddReason(context: ReasonContext, sakhiId: String? = null, itemId: String? = null): String {
    val base = "call_sheet_add_reason/${context.name}"
    val query = listOfNotNull(
      sakhiId?.let { "$CALL_SHEET_SAKHI_ID_ARG=$it" },
      itemId?.let { "$REASON_ITEM_ID_ARG=$it" },
    ).joinToString("&")
    return if (query.isEmpty()) base else "$base?$query"
  }

  fun meetingDetail(eventId: String) = "meeting_detail/$eventId"

  fun rescheduleMeeting(eventId: String) = "reschedule_meeting/$eventId"

  fun meetingAttendance(eventId: String) = "meeting_attendance/$eventId"

  fun addTrainingTopics(eventId: String) = "add_training_topics/$eventId"

  fun gatheringAttendance(eventId: String, gatheringId: String) = "gathering_attendance/$eventId/$gatheringId"

  fun gatheringMarks(eventId: String, gatheringId: String, marksType: MarksType) =
    "gathering_marks/$eventId/$gatheringId/${marksType.name}"
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
    composable(Routes.VISIT_SUMMARY) {
      VisitSummaryScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.RISK_SUMMARY) {
      RiskSummaryScreen(
        onBack = { navController.popBackStack() },
        onVillageSelected = { villageId, villageName, sakhiName ->
          navController.navigate(Routes.villageRiskDetail(villageId, villageName, sakhiName))
        },
      )
    }
    composable(
      Routes.VILLAGE_RISK_DETAIL,
      arguments = listOf(
        navArgument(Routes.VILLAGE_RISK_DETAIL_VILLAGE_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.VILLAGE_RISK_DETAIL_VILLAGE_NAME_ARG) { type = NavType.StringType },
        navArgument(Routes.VILLAGE_RISK_DETAIL_SAKHI_NAME_ARG) { type = NavType.StringType },
      ),
    ) {
      VillageRiskDetailScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.MONITORING_SUMMARY) {
      MonitoringSummaryScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.REGISTRATIONS) {
      RegistrationsScreen(
        onBack = { navController.popBackStack() },
        onSakhiSelected = { sakhiId -> navController.navigate(Routes.sakhiBeneficiaries(sakhiId)) },
      )
    }
    composable(
      Routes.SAKHI_BENEFICIARIES,
      arguments = listOf(navArgument(Routes.SAKHI_BENEFICIARIES_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) {
      BeneficiaryListScreen(onBack = { navController.popBackStack() })
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
        onNewTraining = { navController.navigate(Routes.SCHEDULE_TRAINING) },
        onEventSelected = { eventId -> navController.navigate(Routes.meetingDetail(eventId)) },
      )
    }
    composable(Routes.SCHEDULE_MEETING) {
      ScheduleMeetingScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
    }
    composable(Routes.SCHEDULE_TRAINING) {
      ScheduleTrainingScreen(
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
        onAddTopics = { navController.navigate(Routes.addTrainingTopics(eventId)) },
        onGatheringAttendance = { gatheringId -> navController.navigate(Routes.gatheringAttendance(eventId, gatheringId)) },
        onGatheringMarks = { gatheringId, marksType ->
          navController.navigate(Routes.gatheringMarks(eventId, gatheringId, marksType))
        },
      )
    }
    composable(
      Routes.ADD_TRAINING_TOPICS,
      arguments = listOf(navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType }),
    ) {
      AddTrainingTopicsScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
      )
    }
    composable(
      Routes.GATHERING_ATTENDANCE,
      arguments = listOf(
        navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.GATHERING_ID_ARG) { type = NavType.StringType },
      ),
    ) {
      AttendanceScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
      )
    }
    composable(
      Routes.GATHERING_MARKS,
      arguments = listOf(
        navArgument(Routes.MEETING_DETAIL_EVENT_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.GATHERING_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.MARKS_TYPE_ARG) { type = NavType.StringType },
      ),
    ) {
      MarksScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
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
        onStatClick = { sakhi, kind ->
          val route = when (kind) {
            CallSheetStatKind.VISIT_DUE -> Routes.dueVisit(sakhi.id, DueVisitKind.DUE)
            CallSheetStatKind.VISIT_3_DAYS_TO_EXPIRE -> Routes.dueVisit(sakhi.id, DueVisitKind.EXPIRING_SOON)
            CallSheetStatKind.MISSED_VISIT -> Routes.dueVisit(sakhi.id, DueVisitKind.MISSED)
            CallSheetStatKind.FOLLOWUP_PENDING -> Routes.followupPending(sakhi.id)
            CallSheetStatKind.CLOSURE_FORM_PENDING -> Routes.closurePending(sakhi.id)
            CallSheetStatKind.HIGH_RISK_ANC -> Routes.highRiskList(sakhi.id, HighRiskType.ANC)
            CallSheetStatKind.HIGH_RISK_PNC -> Routes.highRiskList(sakhi.id, HighRiskType.PNC)
          }
          navController.navigate(route)
        },
        onLastSyncDateClick = { sakhi -> navController.navigate(Routes.lastSyncReason(sakhi.id)) },
      )
    }
    composable(
      Routes.DUE_VISIT,
      arguments = listOf(
        navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.DUE_VISIT_KIND_ARG) { type = NavType.StringType },
      ),
    ) { backStackEntry ->
      val kind = DueVisitKind.valueOf(checkNotNull(backStackEntry.arguments?.getString(Routes.DUE_VISIT_KIND_ARG)))
      val titleRes = when (kind) {
        DueVisitKind.DUE -> R.string.call_sheet_stat_visit_due
        DueVisitKind.EXPIRING_SOON -> R.string.call_sheet_stat_visit_3_days_expire
        DueVisitKind.MISSED -> R.string.call_sheet_stat_missed_visit
      }
      DueVisitScreen(titleRes = titleRes, onBack = { navController.popBackStack() })
    }
    composable(
      Routes.FOLLOWUP_PENDING,
      arguments = listOf(navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) {
      FollowupPendingScreen(
        onBack = { navController.popBackStack() },
        onAddReason = { itemId ->
          navController.navigate(Routes.callSheetAddReason(ReasonContext.FOLLOWUP_PENDING, itemId = itemId))
        },
      )
    }
    composable(
      Routes.CLOSURE_PENDING,
      arguments = listOf(navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) {
      ClosurePendingScreen(
        onBack = { navController.popBackStack() },
        onAddReason = { itemId ->
          navController.navigate(Routes.callSheetAddReason(ReasonContext.CLOSURE_PENDING, itemId = itemId))
        },
      )
    }
    composable(
      Routes.HIGH_RISK_LIST,
      arguments = listOf(
        navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType },
        navArgument(Routes.HIGH_RISK_TYPE_ARG) { type = NavType.StringType },
      ),
    ) {
      HighRiskListScreen(onBack = { navController.popBackStack() })
    }
    composable(
      Routes.LAST_SYNC_REASON,
      arguments = listOf(navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
      val sakhiId = backStackEntry.arguments?.getString(Routes.CALL_SHEET_SAKHI_ID_ARG).orEmpty()
      LastSyncReasonScreen(
        onBack = { navController.popBackStack() },
        onAddReason = { navController.navigate(Routes.callSheetAddReason(ReasonContext.LAST_SYNC, sakhiId = sakhiId)) },
      )
    }
    composable(
      Routes.CALL_SHEET_ADD_REASON,
      arguments = listOf(
        navArgument(Routes.REASON_CONTEXT_ARG) { type = NavType.StringType },
        navArgument(Routes.CALL_SHEET_SAKHI_ID_ARG) { type = NavType.StringType; nullable = true },
        navArgument(Routes.REASON_ITEM_ID_ARG) { type = NavType.StringType; nullable = true },
      ),
    ) {
      CallSheetAddReasonScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
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
      QuickResponseScreen(
        onBack = { navController.popBackStack() },
        onRequestSelected = { request -> navController.navigate(Routes.quickResponseAddReason(request.id)) },
      )
    }
    composable(
      Routes.QUICK_RESPONSE_ADD_REASON,
      arguments = listOf(navArgument(Routes.QUICK_RESPONSE_REQUEST_ID_ARG) { type = NavType.StringType }),
    ) {
      QuickResponseAddReasonScreen(
        onBack = { navController.popBackStack() },
        onSubmitted = { navController.popBackStack() },
      )
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
        onNavigateToBeneficiaryDataDownload = { navController.navigate(Routes.BENEFICIARY_DATA_DOWNLOAD) },
        onNavigateToMasterDataDownload = { navController.navigate(Routes.MASTER_DATA_DOWNLOAD) },
      )
    }
    composable(Routes.BENEFICIARY_DATA_DOWNLOAD) {
      BeneficiaryDataDownloadScreen(onBack = { navController.popBackStack() })
    }
    composable(Routes.MASTER_DATA_DOWNLOAD) {
      MasterDataDownloadScreen(onBack = { navController.popBackStack() })
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
