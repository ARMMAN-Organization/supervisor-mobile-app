package org.armman.supervisor.data.callsheet

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.armman.supervisor.data.calllog.CallLogApi
import org.armman.supervisor.data.calllog.CallLogDto
import org.armman.supervisor.data.calllog.CallSheetStatsDto
import org.armman.supervisor.data.calllog.CreateCallLogRequestDto
import org.armman.supervisor.data.calllog.UpdateCallLogRequestDto
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
import org.armman.supervisor.ui.callsheet.ClosurePendingItem
import org.armman.supervisor.ui.callsheet.DueVisitItem
import org.armman.supervisor.ui.callsheet.FailureReason
import org.armman.supervisor.ui.callsheet.FollowupPendingItem
import org.armman.supervisor.ui.callsheet.HighRiskItem
import org.armman.supervisor.ui.callsheet.HighRiskType
import org.armman.supervisor.ui.callsheet.ReasonContext
import org.armman.supervisor.ui.callsheet.ReasonSubmission
import org.armman.supervisor.ui.callsheet.RegistrationType
import org.armman.supervisor.ui.callsheet.SakhiCallSummary
import org.armman.supervisor.ui.callsheet.SuccessOutcome
import org.armman.supervisor.ui.callsheet.SyncReasonItem
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Backend `callStatus` values that mean the call connected — the app splits these into
 * [SuccessOutcome], while the remaining four values become [FailureReason]. Derived from the
 * enum itself (not a hardcoded string list) so a renamed/added [SuccessOutcome] constant can't
 * silently drift out of sync with this set. [SuccessOutcome.UNKNOWN] is excluded — it's a
 * display-only fallback, never a real wire value to match against. */
private val CONNECTED_STATUSES = (SuccessOutcome.entries - SuccessOutcome.UNKNOWN).mapTo(mutableSetOf()) { it.name }

/** Backend enum lookup that falls back to [SuccessOutcome.UNKNOWN]/[FailureReason.UNKNOWN]/
 * [CallResponder.UNKNOWN] instead of throwing. The backend's call-status/responder lookup values
 * can grow independently of an app release (see SRS `call_logs.call_status` — an open-ended
 * lookup, not a fixed enum), so one unrecognized value must degrade gracefully rather than
 * breaking the whole history/summary fetch. */
private inline fun <reified T : Enum<T>> enumOfOrUnknown(name: String, unknown: T): T =
  enumValues<T>().firstOrNull { it.name == name } ?: unknown

private const val SECONDS_PER_MINUTE = 60

/**
 * Concrete [CallSheetRepository]. Locations and Sakhis come from the real auth-service roster via
 * [ProjectsRepository] (shared with Dashboard/Assign Item). Per-Sakhi stats come from
 * `GET /call-sheet-stats` (supervisor-operations-service); a sakhiId the caller can't access is
 * silently omitted by the backend rather than erroring, so it falls back to [emptyStats] here too.
 * Drill-down lists/reason-submission remain placeholder data — no backend endpoint exists yet for
 * any of these; swap for a real API call when one ships, matching [getSakhiSummaries]. Call logs
 * are read/written through [CallLogApi] (supervisor-operations-service); the backend models a
 * single flat `callStatus` enum where this app models two ([CallConnected] + [SuccessOutcome] or
 * [FailureReason]) — see [toEntry]/[toCreateRequest] for the mapping.
 */
class CallSheetRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val callLogApi: CallLogApi,
) : CallSheetRepository {

  private fun emptyStats(lastDataSyncDate: String = todayDisplayDate()) = CallSheetStats(
    rows = CallSheetStatKind.entries.map { CallSheetStatValue(it, updated = 0, count = 0) },
    lastDataSyncDate = lastDataSyncDate,
  )

  private fun CallSheetStatsDto.toDomain(): CallSheetStats {
    val rowsByKind = rows.associateBy { it.kind }
    return CallSheetStats(
      rows = CallSheetStatKind.entries.map { kind ->
        val row = rowsByKind[kind.name]
        CallSheetStatValue(kind, updated = row?.updated ?: 0, count = row?.count ?: 0)
      },
      lastDataSyncDate = lastDataSyncDate,
    )
  }

  /**
   * A failed/errored stats fetch degrades to an empty map (every Sakhi falls back to
   * [emptyStats] in [getSakhiSummaries]) rather than throwing — unlike every other fetch in this
   * file. Stats are one column of the Call Sheet list; the rest of the screen (names, call
   * buttons, last-called timestamps) has nothing to do with `GET /call-sheet-stats` and a brief
   * backend outage on that one endpoint shouldn't blank out the whole list.
   */
  private suspend fun fetchStatsBySakhiId(sakhiIds: List<String>): Map<String, CallSheetStats> {
    if (sakhiIds.isEmpty()) return emptyMap()
    return runCatching {
      val response = callLogApi.getCallSheetStatsBatch(sakhiIds.joinToString(","))
      if (!response.isSuccessful) error("Failed to load call-sheet stats: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty call-sheet stats response")
      if (!body.success) error(body.message ?: "Failed to load call-sheet stats")
      body.data.orEmpty().associateBy({ it.sakhiId }, { it.toDomain() })
    }.getOrElse { cause ->
      if (cause is CancellationException) throw cause
      emptyMap()
    }
  }

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> {
    if (locationId == null) return emptyList()
    val sakhis = projectsRepository.getSakhis(locationId)
    return coroutineScope {
      val statsBySakhiId = async { fetchStatsBySakhiId(sakhis.map { it.id }) }
      sakhis.map { sakhi -> sakhi to async { fetchCallHistory(sakhi.id) } }
        .map { (sakhi, history) ->
          SakhiCallSummary(
            sakhi = sakhi,
            stats = statsBySakhiId.await()[sakhi.id] ?: emptyStats(),
            lastCalledAtEpochMillis = history.await().firstOrNull()?.timestampEpochMillis,
          )
        }
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
      successOutcome = if (connected == CallConnected.YES) enumOfOrUnknown(callStatus, SuccessOutcome.UNKNOWN) else null,
      failureReason = if (connected == CallConnected.NO) enumOfOrUnknown(callStatus, FailureReason.UNKNOWN) else null,
      responder = responder?.let { enumOfOrUnknown(it, CallResponder.UNKNOWN) },
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
    // callDatetime and callStartAt intentionally share one timestamp: the Supervisor logs a call
    // after it has already happened (FR-SV-3.1/3.2), so there's no separately-observed "call
    // initiated" moment distinct from "when this log entry represents" — see ERD §4.7 call_logs.
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

  private fun displayDate(epochMillis: Long): String =
    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(epochMillis))

  // --- Call Sheet drill-downs: placeholder data — no backend endpoint exists yet for any of
  // these (see class doc). Each mock row is keyed off [sakhiId] only so the UI is fully
  // navigable/demoable; swap for a real API call when one ships, matching [getSakhiSummaries].

  override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = listOf(
    DueVisitItem(
      beneficiaryId = "$sakhiId-due-1",
      beneficiaryName = "Sushma T Test",
      villageName = "SushilTest",
      uniqueId = "test4test_taluka1test_phc031W51",
      registrationType = RegistrationType.WOMEN,
      visit = "ANC2.1",
      scheduledDate = "19-08-2026",
      balancedDays = 10,
      risk = "Hypertension",
    ),
  )

  override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = emptyList()

  override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = emptyList()

  /**
   * The backend's own Followup Pending definition (`countPendingFollowups`) only checks that the
   * Sakhi's single most recent call is CALL_BACK — it does not know about [submitReason] and never
   * excludes a call once a reason has been recorded against it. This list adds that missing
   * exclusion on the app side (a call with [CallLogEntry.followUpAction] already set is treated as
   * actioned, not pending) so a submitted item disappears here; the stats card's count can still
   * disagree with an empty list until the backend adopts the same check. [fetchCallHistory]
   * already returns newest-first.
   */
  override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> {
    val latest = fetchCallHistory(sakhiId).firstOrNull() ?: return emptyList()
    if (latest.successOutcome != SuccessOutcome.CALL_BACK) return emptyList()
    if (!latest.followUpAction.isNullOrBlank()) return emptyList()
    return listOf(
      FollowupPendingItem(
        callLogId = latest.id,
        callDate = displayDate(latest.timestampEpochMillis),
        notes = latest.notes,
      ),
    )
  }

  override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = listOf(
    ClosurePendingItem(
      beneficiaryId = "$sakhiId-closure-1",
      beneficiaryName = "Child 1 K Salvi",
      villageName = "SushilTest",
      uniqueId = "test4test_taluka1test_phc031I53",
      registrationType = RegistrationType.CHILD,
      registrationDate = "06-08-2026",
      dateOfBirth = "11-08-2025",
      risk = "Managed",
      overdueDays = 3,
    ),
    ClosurePendingItem(
      beneficiaryId = "$sakhiId-closure-2",
      beneficiaryName = "Child 1 K Salvi",
      villageName = "SushilTest",
      uniqueId = "test4test_taluka1test_phc031I54",
      registrationType = RegistrationType.CHILD,
      registrationDate = "06-08-2026",
      dateOfBirth = "11-08-2025",
      risk = "Managed",
      overdueDays = 3,
    ),
  )

  override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = when (type) {
    HighRiskType.ANC -> listOf(
      HighRiskItem(
        beneficiaryId = "$sakhiId-anc-1",
        beneficiaryName = "Sushma T Test",
        villageName = "SushilTest",
        uniqueId = "test4test_taluka1test_phc031W51",
        riskName = "Hypertension",
      ),
      HighRiskItem(
        beneficiaryId = "$sakhiId-anc-2",
        beneficiaryName = "Test t test",
        villageName = "SushilTest",
        uniqueId = "test4test_taluka1test_phc031W53",
        riskName = "Age",
      ),
    )
    HighRiskType.PNC -> emptyList()
  }

  override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? =
    SyncReasonItem(syncDate = "10-08-2026", reason = "Forgot to sync")

  /**
   * Only [ReasonContext.FOLLOWUP_PENDING] persists for real today, via `PATCH /call-logs/:id`
   * (the only real write path this drill-down family has — see [FollowupPendingItem.callLogId]).
   * Closure Pending and Last Sync remain no-ops: neither has a backend record to attach a reason
   * to yet (no closure-form-pending or sync-failure data model exists — see class doc).
   */
  override suspend fun submitReason(submission: ReasonSubmission) {
    require(submission.reasonCode.isNotBlank()) { "reasonCode must not be blank" }
    when (submission.context) {
      ReasonContext.FOLLOWUP_PENDING -> {
        val callLogId = requireNotNull(submission.itemId) { "itemId (callLogId) is required for FOLLOWUP_PENDING" }
        val response = callLogApi.updateCallLog(
          callLogId,
          UpdateCallLogRequestDto(
            followupAction = submission.reasonCode,
            notes = mergedNotes(submission, callLogId),
          ),
        )
        if (!response.isSuccessful) error("Failed to submit reason: HTTP ${response.code()}")
        val body = response.body() ?: error("Empty submit-reason response")
        if (!body.success) error(body.message ?: "Failed to submit reason")
      }
      ReasonContext.CLOSURE_PENDING, ReasonContext.LAST_SYNC -> {
        // No-op placeholder — no backend endpoint exists yet to persist this submission.
      }
    }
  }

  /**
   * The PATCH's `notes` field replaces the call log's existing notes rather than appending
   * ([UpdateCallLogRequestDto] doc comment — "only send what changed"). A blank remark should
   * leave the existing notes untouched (return `null`, so the field isn't sent at all); a non-blank
   * remark must be combined with whatever notes the call already has, or recording a Followup
   * Pending reason would silently erase the original call's notes. [submission.sakhiId] is
   * expected to be present for [ReasonContext.FOLLOWUP_PENDING] (see [Routes.callSheetAddReason]'s
   * caller in `AppNavHost`); falling back to `null` there would just mean an empty existing-notes
   * lookup, not a crash.
   */
  private suspend fun mergedNotes(submission: ReasonSubmission, callLogId: String): String? {
    val remark = submission.remark?.takeIf { it.isNotBlank() } ?: return null
    val sakhiId = submission.sakhiId ?: return remark
    val existingNotes = fetchCallHistory(sakhiId).firstOrNull { it.id == callLogId }?.notes?.takeIf { it.isNotBlank() }
    return if (existingNotes == null) remark else "$existingNotes\n$remark"
  }
}
