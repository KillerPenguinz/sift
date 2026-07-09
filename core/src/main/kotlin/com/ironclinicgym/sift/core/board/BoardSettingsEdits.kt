package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.theme.PRIORITY_META

/**
 * Pure edits to [BoardSettings]. Priority editing is display-only and non-destructive: renaming,
 * recoloring, reordering, hiding (remove), and adding never touch Notion data. Hiding a priority
 * unmaps it in Sift while the Notion priority option and its records stay put. Aligning an
 * added priority to a Notion option is what gives it data. All functions return a new settings
 * value; the platform layer persists it.
 */

private fun BoardSettings.updatePriority(id: String, transform: (PriorityView) -> PriorityView): BoardSettings =
    copy(priorities = priorities.map { if (it.id == id) transform(it) else it })

fun BoardSettings.renamePriority(id: String, name: String): BoardSettings =
    updatePriority(id) { it.copy(displayName = name) }

fun BoardSettings.recolorPriority(id: String, colorKey: String): BoardSettings =
    updatePriority(id) { it.copy(colorKey = colorKey) }

fun BoardSettings.toggleGlance(id: String): BoardSettings =
    updatePriority(id) { it.copy(showInGlance = !it.showInGlance) }

/** Remove a priority from Sift: hidden and unmapped, Notion data untouched. */
fun BoardSettings.hidePriority(id: String): BoardSettings =
    updatePriority(id) { it.copy(hidden = true, showInGlance = false) }

fun BoardSettings.showPriority(id: String): BoardSettings =
    updatePriority(id) { it.copy(hidden = false) }

/**
 * Remove a priority the right way: a Sift-only priority (no Notion option) is deleted outright
 * since there is nothing to preserve; a priority backed by a Notion option is hidden
 * non-destructively so it can be restored and its Notion data stays put.
 */
fun BoardSettings.removePriority(id: String): BoardSettings {
    val priority = priorities.firstOrNull { it.id == id } ?: return this
    return if (priority.isUnmapped) copy(priorities = priorities.filterNot { it.id == id }) else hidePriority(id)
}

/** Apply a new priority order from a fully ordered list of ids (drag-to-reorder). */
fun BoardSettings.reorderPriorities(orderedIds: List<String>): BoardSettings {
    val orderOf = orderedIds.withIndex().associate { (index, id) -> id to index }
    return copy(priorities = priorities.map { it.copy(order = orderOf[it.id] ?: it.order) })
}

/** Move a priority up (delta -1) or down (delta +1) in the order, reindexing. */
fun BoardSettings.movePriority(id: String, delta: Int): BoardSettings {
    val ordered = priorities.sortedBy { it.order }.toMutableList()
    val index = ordered.indexOfFirst { it.id == id }
    val target = index + delta
    if (index < 0 || target !in ordered.indices) return this
    ordered.add(target, ordered.removeAt(index))
    return copy(priorities = ordered.mapIndexed { i, b -> b.copy(order = i) })
}

/**
 * Add a priority. Supply [id] (the platform generates a stable one for custom priorities; for a
 * priority backed by a Notion option, use the option id). Leave [optionId] null for a custom
 * priority that has no matching data yet.
 */
fun BoardSettings.addPriority(
    id: String,
    displayName: String,
    colorKey: String,
    optionId: String? = null,
    optionName: String? = null,
): BoardSettings {
    val nextOrder = (priorities.maxOfOrNull { it.order } ?: -1) + 1
    return copy(priorities = priorities + PriorityView(id, displayName, nextOrder, colorKey, optionId, optionName))
}

/** Point an added priority at a Notion priority option so its data flows in. */
fun BoardSettings.alignPriority(id: String, optionId: String, optionName: String): BoardSettings =
    updatePriority(id) { it.copy(optionId = optionId, optionName = optionName) }

fun BoardSettings.setTimeGating(enabled: Boolean): BoardSettings = copy(timeGatingEnabled = enabled)

fun BoardSettings.setUse24HourTime(enabled: Boolean): BoardSettings = copy(use24HourTime = enabled)

fun BoardSettings.setOneDayLandmarkEnabled(name: String, enabled: Boolean): BoardSettings {
    val updated = if (enabled) oneDayLandmarksEnabled + name else oneDayLandmarksEnabled - name
    return copy(oneDayLandmarksEnabled = updated)
}

private fun BoardSettings.updateBucket(optionId: String?, transform: (BucketView) -> BucketView): BoardSettings =
    copy(buckets = buckets.map { if (it.optionId == optionId) transform(it) else it })

fun BoardSettings.renameBucket(optionId: String?, name: String): BoardSettings =
    updateBucket(optionId) { it.copy(displayName = name) }

fun BoardSettings.recolorBucket(optionId: String?, colorKey: String?): BoardSettings =
    updateBucket(optionId) { it.copy(colorKey = colorKey) }

fun BoardSettings.setBucketIcon(optionId: String?, icon: String): BoardSettings =
    updateBucket(optionId) { it.copy(icon = icon) }

fun BoardSettings.setBucketSchedule(optionId: String?, schedule: PrioritySchedule): BoardSettings =
    updateBucket(optionId) { it.copy(schedule = schedule) }

/** Notion priority options that no visible priority maps to, offered for "add to board". */
fun unmappedOptions(mapping: DatabaseMapping, settings: BoardSettings): List<PriorityBinding> =
    mapping.priorities.filter { option ->
        settings.priorities.none { !it.hidden && it.optionId == option.optionId }
    }

/** A sensible default accent for a newly added priority, cycling the urgency palette. */
fun BoardSettings.nextColorKey(): String = PRIORITY_META[priorities.size % PRIORITY_META.size].key.name
