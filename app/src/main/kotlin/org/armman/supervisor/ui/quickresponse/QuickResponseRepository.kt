package org.armman.supervisor.ui.quickresponse

/** Data source for the Quick Response flow. Bound to `QuickResponseRepositoryImpl`. */
interface QuickResponseRepository {
  /** Pending Quick Response cards for the current Supervisor (SRS FR-SV-4.1). */
  suspend fun getRequests(): List<QuickResponseRequest>

  /**
   * Submits [decision] for the card [requestId] (SRS FR-SV-4.2, 4.4, 4.5, 4.6, 4.7, 4.9 CTAs).
   * [notes] is the Closure Review card's editable supervisor notes field (SRS FR-SV-4.4) — only
   * meaningful on Reject; blank/null is omitted from the request rather than sent as an empty
   * string.
   */
  suspend fun decide(requestId: String, decision: QuickResponseDecision, notes: String? = null)

  /** Submits [action] for the Missed Visit Escalation card [requestId] (SRS FR-SV-4.3 CTAs). */
  suspend fun decideEscalation(requestId: String, action: QuickResponseEscalationAction)

  /** Acknowledges the EDD Nearing card [requestId] (SRS FR-SV-4.8's single "Okay" CTA). */
  suspend fun acknowledgeEddNearing(requestId: String)
}

/** Thrown by [QuickResponseRepository.decide] on a non-2xx response, carrying [httpStatusCode]
 * so callers can distinguish e.g. 409 (already decided) from other failures. */
class QuickResponseDecisionException(val httpStatusCode: Int, message: String) : Exception(message)
