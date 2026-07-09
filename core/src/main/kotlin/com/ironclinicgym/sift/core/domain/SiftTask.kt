package com.ironclinicgym.sift.core.domain

/**
 * A task as Sift understands it, produced by reading a Notion record *through* a mapping.
 * Option ids/names are carried so the UI can resolve priority/bucket bindings without ever
 * touching a hardcoded property name. Phase 1 only needs this for the confirmation count;
 * Phase 2 renders the board from it.
 */
data class SiftTask(
    val pageId: String,
    val mappingId: String,
    val title: String,
    val priorityOptionId: String?,
    val priorityOptionName: String?,
    val bucketOptionId: String?,
    val bucketOptionName: String?,
    val isDone: Boolean,
    val due: String? = null,
    val notes: String? = null,
    val recurrenceRule: String? = null,
    val recurrenceDisplay: String? = null,
    val labels: List<String> = emptyList(),
    // ---- Local operational state, merged from local storage keyed by [pageId]. ----
    val completedOnIso: String? = null,
    val manualSortIndex: Int? = null,
    val pinLevel: Int = 0,
    val isProtected: Boolean = false,
    val isBrainDump: Boolean = false,
    val isBlocked: Boolean = false,
    val reviewDateIso: String? = null,
    val rescheduleCount: Int = 0,
    val createdBy: String = "sift",
) {
    val isRecurring: Boolean get() = !recurrenceRule.isNullOrBlank()
    val isDated: Boolean get() = due != null
    val isPinned: Boolean get() = pinLevel > 0
}
