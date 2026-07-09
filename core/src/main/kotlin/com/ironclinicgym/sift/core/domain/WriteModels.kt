package com.ironclinicgym.sift.core.domain

/**
 * Inputs and outputs for the plain write layer ([TaskWriteService]). All UI-agnostic so Phase 4
 * AI capture can build a [TaskDraft] and call the same service the board uses.
 *
 * Priority and Bucket are expressed as option *names* (not ids) because a name is what the user
 * types, what Notion matches, and what auto-creates a brand-new option on first write.
 */

/** A new task to create. Only [title] is required; everything else is optional capture. */
data class TaskDraft(
    val mappingId: String,
    val title: String,
    val priorityOptionName: String? = null,
    val bucketOptionName: String? = null,
    val dueIso: String? = null,
    val notes: String? = null,
    val recurrenceRule: String? = null,
    // Optimistic-placement hints only: the option ids the names resolve to, so the new row lands
    // in the right priority/bucket instantly. Null for a brand-new option (a refresh resolves the
    // real id). Never sent to Notion; the write itself is name-driven so new options auto-create.
    val priorityOptionId: String? = null,
    val bucketOptionId: String? = null,
    val labels: List<String>? = null,
)

/**
 * A patch to an existing task. Each field is a nullable-of-nullable via [Field], so "leave
 * unchanged" is distinct from "clear this value".
 */
data class TaskEdits(
    val title: Field<String>? = null,
    val priorityOptionName: Field<String?>? = null,
    val bucketOptionName: Field<String?>? = null,
    val dueIso: Field<String?>? = null,
    val notes: Field<String?>? = null,
    val recurrenceRule: Field<String?>? = null,
    val labels: Field<List<String>>? = null,
    // Optimistic-placement hints, applied only when the matching name field is set (see TaskDraft).
    val priorityOptionId: String? = null,
    val bucketOptionId: String? = null,
) {
    val isEmpty: Boolean
        get() = title == null && priorityOptionName == null && bucketOptionName == null &&
            dueIso == null && notes == null && recurrenceRule == null && labels == null

    /** Wrapper marking a field as present (to be written). Absent field == leave as-is. */
    data class Field<out T>(val value: T)
}

/** How a swipe-to-snooze resolves. The ViewModel picks the branch from the task's state. */
sealed interface SnoozeDecision {
    /** Undated task: bump down to a less urgent priority (by its option name + id for placement). */
    data class BumpToPriority(val priorityOptionName: String, val priorityOptionId: String?) : SnoozeDecision

    /** Dated task or explicit picker: move the due date to [iso]. */
    data class SetDate(val iso: String) : SnoozeDecision

    /** Already at the lowest priority with no date: nothing to do, show a gentle notice. */
    data object AlreadyLowest : SnoozeDecision
}

/**
 * The result of a write. On success carries an optional [undo] token for the single active undo.
 * On failure the optimistic change has already been rolled back and [message] is plain-language,
 * hyphen-free, and actionable; [recoverableDraft] preserves the user's input for an add/edit.
 */
sealed interface WriteResult {
    data class Success(val pageId: String, val undo: UndoToken? = null) : WriteResult
    data class Failure(val message: String, val recoverableDraft: TaskDraft? = null) : WriteResult

    /** Snooze at the floor: no write happened, surface the gentle notice. */
    data object AtLowestPriority : WriteResult

    /** Recurrence requested on a database that has not consented to the Sift fields. */
    data object NeedsRecurrenceConsent : WriteResult
}

/**
 * A single, reversible action. [inverse] performs the opposite write when the user taps Undo;
 * it stays a suspend lambda so the whole reversal (including any spawned recurrence occurrence)
 * lives in core, never in the UI. In-memory only: undo history beyond the current action is
 * explicitly out of scope for v1.
 */
class UndoToken(
    val label: String,
    val highlightPageId: String?,
    val inverse: suspend () -> WriteResult,
)
