package org.armman.supervisor.data.callsheet

import org.armman.supervisor.data.calllog.CallLogApi
import org.armman.supervisor.data.calllog.CallLogDto
import org.armman.supervisor.data.calllog.CreateCallLogRequestDto
import org.armman.supervisor.data.projects.ProjectsRepository
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
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Backend `callStatus` values that mean the call connected — the app splits these into
 * [SuccessOutcome], while the remaining four values become [FailureReason]. Derived from the
 * enum itself (not a hardcoded string list) so a renamed/added [SuccessOutcome] constant can't
 * silently drift out of sync with this set. */
private val CONNECTED_STATUSES = SuccessOutcome.entries.mapTo(mutableSetOf()) { it.name }

private const val SECONDS_PER_MINUTE = 60

/**
 * Concrete [CallSheetRepository]. Locations and Sakhis come from the real auth-service roster via
 * [ProjectsRepository] (shared with Dashboard/Assign Item). Per-Sakhi stats (visits due, risk
 * counts, etc.) remain placeholder data — no dashboard/call-sheet-stats endpoint exists yet; only
 * [getSakhiSummaries]'s stats lookup changes when one ships. Call logs are read/written through
 * [CallLogApi] (supervisor-operations-service); the backend models a single flat `callStatus`
 * enum where this app models two ([CallConnected] + [SuccessOutcome] or [FailureReason]) — see
 * [toEntry]/[toCreateRequest] for the mapping.
 */
class CallSheetRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val callLogApi: CallLogApi,
) : CallSheetRepository {

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

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> {
    if (locationId == null) return emptyList()
    val sakhis = projectsRepository.getSakhis(locationId)
    return sakhis.map { sakhi ->
      val history = fetchCallHistory(sakhi.id)
      SakhiCallSummary(sakhi = sakhi, stats = sampleStats(), lastCalledAtEpochMillis = history.firstOrNull()?.timestampEpochMillis)
    }
  }

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = projectsRepository.getSakhiOption(sakhiId)

  override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = fetchCallHistory(sakhiId)

  override suspend fun logCall(submission: CallLogSubmission): CallLogEntry {
    val response = callLogApi.createCallLog(submission.toCreateRequest())
    if (!response.isSuccessful) error("Failed to log call: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty call log response")
    if (!body.success) error(body.message ?: "Failed to log call")
    val created = body.data ?: error("Empty call log response")
    return created.toEntry()
  }

  private suspend fun fetchCallHistory(sakhiId: String): List<CallLogEntry> {
    val response = callLogApi.getCallLogsBySakhi(sakhiId)
    if (!response.isSuccessful) error("Failed to load call history: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty call history response")
    if (!body.success) error(body.message ?: "Failed to load call history")
    // Newest first (SRS FR-SV-3.3) — the backend already returns newest-first, but sort
    // defensively since [SakhiCallSummary.lastCalledAtEpochMillis] relies on entry order.
    return body.data.orEmpty().map { it.toEntry() }.sortedByDescending { it.timestampEpochMillis }
  }

  private fun CallLogDto.toEntry(): CallLogEntry {
    val connected = if (callStatus in CONNECTED_STATUSES) CallConnected.YES else CallConnected.NO
    return CallLogEntry(
      id = id,
      timestampEpochMillis = Instant.parse(callStartAt).toEpochMilli(),
      connected = connected,
      successOutcome = if (connected == CallConnected.YES) SuccessOutcome.valueOf(callStatus) else null,
      failureReason = if (connected == CallConnected.NO) FailureReason.valueOf(callStatus) else null,
      responder = responder?.let { CallResponder.valueOf(it) },
      // Round rather than truncate — 90s should read back as 2 minutes, not 1.
      durationMinutes = callDurationSeconds?.let { (it + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE },
      notes = notes,
      followUpAction = followupAction,
    )
  }

  private suspend fun CallLogSubmission.toCreateRequest(): CreateCallLogRequestDto {
    val projectId = projectsRepository.getSakhiProjectId(sakhiId)
    val callStatus = when (connected) {
      CallConnected.YES -> checkNotNull(successOutcome).name
      CallConnected.NO -> checkNotNull(failureReason).name
    }
    val nowIso = Instant.now().toString()
    return CreateCallLogRequestDto(
      projectId = projectId,
      sakhiId = sakhiId,
      callDatetime = nowIso,
      callStatus = callStatus,
      callStartAt = nowIso,
      callDurationSeconds = durationMinutes?.let { it * SECONDS_PER_MINUTE },
      notes = notes,
      followupAction = followUpAction,
      responder = responder?.name,
    )
  }

  private fun todayDisplayDate(): String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
}
