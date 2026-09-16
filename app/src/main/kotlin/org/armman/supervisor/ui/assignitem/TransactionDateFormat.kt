package org.armman.supervisor.ui.assignitem

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The app-wide display format for a transaction date, used by both the detail screen's cards
 * and the Add/Edit Item Transaction form. */
val TransactionDateDisplayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** [org.armman.supervisor.data.assignitem.TransactionEntry.date] may be an ISO instant (any row
 * read straight from the server, per supervisor-operations-service's `transactionDate:
 * z.string().datetime()` response contract — every server response, not just "fresh" ones) or
 * already "dd MMM yyyy" (a value this app itself produced, e.g. echoed back into
 * [org.armman.supervisor.ui.assignitem.AddItemTransactionUiState.Success.transactionDate] after a
 * local edit). Normalize both to the app-wide display format so every reader of this field
 * (display text, the date picker's initial value, and [AddItemTransactionViewModel]'s future-date
 * validation) sees a consistent format instead of failing to parse — an unguarded parse of the
 * ISO form previously crashed the app on Save (java.time.format.DateTimeParseException) since
 * this normalization only existed on the detail screen, not the edit form. */
fun String.toTransactionDisplayDate(): String =
  runCatching { TransactionDateDisplayFormatter.format(Instant.parse(this).atZone(ZoneId.systemDefault())) }
    .getOrDefault(this)
