package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.theme.PRIORITY_META
import com.ironclinicgym.sift.core.theme.BUCKETS
import com.ironclinicgym.sift.core.theme.BucketKey
import kotlinx.serialization.Serializable

/**
 * The user-editable display layer for the board, kept separate from the Notion binding truth
 * in the mapping. It seeds from the mapping's priorities/buckets but can diverge: priorities can be
 * renamed, reordered, recolored, added (no matching option yet), or removed (hidden + unmapped
 * without touching Notion). Color keys and schedule are stored as plain data so this stays
 * KMP-portable and serializes to non-sensitive storage.
 *
 * Enum-valued keys are stored as strings (the enum name) to match the Phase 1 mapping
 * convention and avoid coupling the persisted shape to the theme enums.
 */

/** A per-priority schedule. Built for per priority, per day, custom hour gating from the start. */
@Serializable
data class PrioritySchedule(
    val enabled: Boolean = false,
    /** ISO days the window applies to (1 = Monday .. 7 = Sunday). Empty means every day. */
    val days: Set<Int> = emptySet(),
    /** Minutes from midnight. When start > end the window wraps past midnight. */
    val startMinute: Int = 0,
    val endMinute: Int = 24 * 60,
) {
    companion object {
        val ALWAYS = PrioritySchedule(enabled = false)
    }
}

/** One priority as Sift renders it. [optionId] links to a Notion priority option, null if unmapped. */
@Serializable
data class PriorityView(
    val id: String,
    val displayName: String,
    val order: Int,
    /** PriorityKey name selecting the urgency accent color. */
    val colorKey: String,
    val optionId: String?,
    val optionName: String?,
    /** Removed from Sift: hidden and unmapped, Notion option untouched. */
    val hidden: Boolean = false,
    /** Part of the minimized glance subset. */
    val showInGlance: Boolean = false,
) {
    /** True when the priority has no matching Notion option (added in Sift, not yet aligned). */
    val isUnmapped: Boolean get() = optionId == null
}

/** One bucket's color + icon coding. [optionId] null is the neutral unset default. */
@Serializable
data class BucketView(
    val optionId: String?,
    val displayName: String,
    /** BucketKey name selecting the identity color, or null for the neutral default. */
    val colorKey: String?,
    /** Material Symbols ligature name. */
    val icon: String,
    /** Optional per-bucket schedule: hides this bucket's items outside the window. */
    val schedule: PrioritySchedule = PrioritySchedule.ALWAYS,
)

@Serializable
data class BoardSettings(
    val mappingId: String,
    val priorities: List<PriorityView>,
    val buckets: List<BucketView>,
    val minimized: Boolean = false,
    /** Layout toggle: 2 column at a glance vs 1 column fuller rows. */
    val twoColumn: Boolean = true,
    /**
     * How many tasks a bucket shows in single column view before "plus N more". Two column view
     * keeps its own fixed cap regardless of this value. Range is 4 to 20; 0 is the sentinel for
     * "Show all" (no cap). Default 8 for existing users on upgrade, since the field is absent
     * from previously saved JSON and decodes to this default.
     */
    val singleColumnLimit: Int = 8,
    /** Global opt-in for time gating. Defaults off so nothing hides for a new user. */
    val timeGatingEnabled: Boolean = false,
    /** Clock format for times shown in the board and schedule editor. Default is AM/PM. */
    val use24HourTime: Boolean = false,
    /** Debug-only: overlay generated sample tasks so the dynamic layouts can be exercised. */
    val sampleMode: Boolean = false,
    val labelsAvailable: Boolean = false,
    /** Which Landmark groups are visible in the one-day focused view. Defaults to all. */
    val oneDayLandmarksEnabled: Set<String> = Landmark.entries.map { it.name }.toSet(),
    /** When false the Signal Inflation nudge is suppressed entirely. */
    val signalInflationEnabled: Boolean = true,
    /** Number of ASAP tasks that triggers the overload nudge. */
    val asapInflationThreshold: Int = 5,
    /** Percentage of total tasks that are Protected before the saturation nudge fires. */
    val protectedInflationPercent: Int = 30,
    /** True: this database uses the global priority-timing settings. False: uses [prioritySettingsOverride]. */
    val useGlobalPrioritySettings: Boolean = true,
    /** Per-database override; consulted only when [useGlobalPrioritySettings] is false. */
    val prioritySettingsOverride: PrioritySettings? = null,
) {
    fun bucketFor(optionId: String?): BucketView =
        buckets.firstOrNull { it.optionId == optionId } ?: unsetBucket

    val unsetBucket: BucketView
        get() = buckets.firstOrNull { it.optionId == null } ?: DEFAULT_UNSET_BUCKET

    companion object {
        /** Neutral coding for a task whose bucket is unset or unrecognized. */
        val DEFAULT_UNSET_BUCKET = BucketView(optionId = null, displayName = "No bucket", colorKey = null, icon = "label")

        /**
         * Seed display settings from a freshly mapped database. Priority colors reuse the default
         * urgency accent when the option matched one, otherwise cycle the palette by position.
         * The default glance subset is the asap and today priorities (or the first two).
         */
        fun fromMapping(mapping: DatabaseMapping): BoardSettings {
            val palette = PRIORITY_META.map { it.key.name }
            val priorities = mapping.priorities.sortedBy { it.order }.mapIndexed { index, b ->
                val colorKey = b.priorityKey ?: palette[index % palette.size]
                PriorityView(
                    id = b.optionId,
                    displayName = b.optionName,
                    order = index,
                    colorKey = colorKey,
                    optionId = b.optionId,
                    optionName = b.optionName,
                    showInGlance = colorKey == "ASAP" || colorKey == "TODAY",
                )
            }
            val glanceSeeded = if (priorities.any { it.showInGlance }) priorities
            else priorities.mapIndexed { i, b -> if (i < 2) b.copy(showInGlance = true) else b }

            val buckets = mapping.buckets.map { s ->
                val key = s.bucketKey
                BucketView(
                    optionId = s.optionId,
                    displayName = s.optionName,
                    colorKey = key,
                    icon = key?.let { runCatching { BUCKETS.getValue(BucketKey.valueOf(it)).icon }.getOrNull() } ?: "label",
                )
            } + DEFAULT_UNSET_BUCKET

            val hasLabels = mapping.bindings.any { it.role == Role.LABELS }
            return BoardSettings(mapping.id, glanceSeeded, buckets, labelsAvailable = hasLabels)
        }
    }
}
