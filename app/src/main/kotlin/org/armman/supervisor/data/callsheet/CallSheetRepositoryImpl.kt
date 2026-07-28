package org.armman.supervisor.data.callsheet

import org.armman.supervisor.data.local.CallLogDao
import org.armman.supervisor.data.local.CallLogEntity
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.callsheet.CallConnected
import org.armman.supervisor.ui.callsheet.CallLogEntry
import org.armman.supervisor.ui.callsheet.CallLogSubmission
import org.armman.supervisor.ui.callsheet.CallResponder
import org.armman.supervisor.ui.callsheet.CallSheetRepository
import org.armman.supervisor.ui.callsheet.CallSheetStatKind
import org.armman.supervisor.ui.callsheet.CallSheetStatValue
import org.armman.supervisor.ui.callsheet.CallSheetStats
import org.armman.supervisor.ui.callsheet.FailureReason
import org.armman.supervisor.ui.callsheet.SakhiCallSummary
import org.armman.supervisor.ui.callsheet.SuccessOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Concrete [CallSheetRepository]. Reference/lookup data (locations, Sakhis, their stats) is local
 * sample data — it stands in for the future read-only GET endpoints and carries no risk of data
 * loss. Call logs the Supervisor actually creates are persisted in the local encrypted database
 * ([CallLogDao]) so they survive process death — the app has no real call-logs API yet. When that
 * API is ready, only [logCall]/[getCallHistory] change to HTTP calls returning/accepting the same
 * models — the interface, its Hilt binding in `di/CallSheetModule.kt`, and every caller (ViewModels,
 * screens) stay unchanged.
 */
class CallSheetRepositoryImpl @Inject constructor(
  private val callLogDao: CallLogDao,
) : CallSheetRepository {

  private val locations = listOf(
    LocationOption("loc-1", "Unrestricted Armman"),
    LocationOption("loc-2", "Wardha - Zone A"),
  )

  private val sakhisByLocation = mapOf(
    "loc-1" to listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil")),
    "loc-2" to listOf(SakhiOption("sakhi-3", "Test sakhi 3")),
  )

  private val statsBySakhi = mapOf(
    "sakhi-1" to sampleStats(closureFormPending = 96, highRiskAnc = 21),
    "sakhi-2" to sampleStats(),
    "sakhi-3" to sampleStats(closureFormPending = 96, highRiskAnc = 21),
  )

  private fun sampleStats(
    visitDue: Int = 0,
    visitThreeDaysToExpire: Int = 0,
    followupPending: Int = 0,
    closureFormPending: Int = 0,
    missedVisit: Int = 0,
    highRiskAnc: Int = 0,
    highRiskPnc: Int = 0,
    lastDataSyncDate: String = todayDisplayDate(),
  ) = CallSheetStats(
    rows = listOf(
      CallSheetStatValue(CallSheetStatKind.VISIT_DUE, updated = 0, count = visitDue),
      CallSheetStatValue(CallSheetStatKind.VISIT_3_DAYS_TO_EXPIRE, updated = 0, count = visitThreeDaysToExpire),
      CallSheetStatValue(CallSheetStatKind.FOLLOWUP_PENDING, updated = 0, count = followupPending),
      CallSheetStatValue(CallSheetStatKind.CLOSURE_FORM_PENDING, updated = 0, count = closureFormPending),
      CallSheetStatValue(CallSheetStatKind.MISSED_VISIT, updated = 0, count = missedVisit),
      CallSheetStatValue(CallSheetStatKind.HIGH_RISK_ANC, updated = 0, count = highRiskAnc),
      CallSheetStatValue(CallSheetStatKind.HIGH_RISK_PNC, updated = 0, count = highRiskPnc),
    ),
    lastDataSyncDate = lastDataSyncDate,
  )

  override suspend fun getLocations(): List<LocationOption> = locations

  override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> {
    val sakhis = sakhisByLocation[locationId].orEmpty()
    return sakhis.map { sakhi ->
      val stats = statsBySakhi[sakhi.id] ?: error("Unknown sakhi id: ${sakhi.id}")
      val latest = callLogDao.getLatestForSakhi(sakhi.id)
      SakhiCallSummary(sakhi = sakhi, stats = stats, lastCalledAtEpochMillis = latest?.timestampEpochMillis)
    }
  }

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption =
    sakhisByLocation.values.flatten().firstOrNull { it.id == sakhiId } ?: error("Unknown sakhi id: $sakhiId")

  override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> =
    callLogDao.getBySakhi(sakhiId).map { it.toEntry() }

  override suspend fun logCall(submission: CallLogSubmission): CallLogEntry {
    val id = "call-${UUID.randomUUID()}"
    val entity = submission.toEntity(id)
    callLogDao.insert(entity)
    return entity.toEntry()
  }

  private fun CallLogEntity.toEntry(): CallLogEntry = CallLogEntry(
    id = id,
    timestampEpochMillis = timestampEpochMillis,
    connected = if (connected) CallConnected.YES else CallConnected.NO,
    successOutcome = successOutcome?.let { SuccessOutcome.valueOf(it) },
    failureReason = failureReason?.let { FailureReason.valueOf(it) },
    responder = responder?.let { CallResponder.valueOf(it) },
    durationMinutes = durationMinutes,
    notes = notes,
    followUpAction = followUpAction,
  )

  private fun CallLogSubmission.toEntity(id: String): CallLogEntity = CallLogEntity(
    id = id,
    sakhiId = sakhiId,
    timestampEpochMillis = System.currentTimeMillis(),
    connected = connected == CallConnected.YES,
    successOutcome = successOutcome?.name,
    failureReason = failureReason?.name,
    responder = responder?.name,
    durationMinutes = durationMinutes,
    notes = notes,
    followUpAction = followUpAction,
  )

  private fun todayDisplayDate(): String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
}
