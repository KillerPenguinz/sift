package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.theme.PriorityKey
import kotlinx.serialization.Serializable

/**
 * Maps a due date to its natural priority via configurable day-range bands. Pure and
 * clock-injected (todayIso) so it stays deterministic and KMP-portable. No java.time.
 */

@Serializable
data class DateBandConfig(
    val bands: List<DateBand> = DEFAULT_BANDS,
) {
    companion object {
        val DEFAULT_BANDS: List<DateBand> = listOf(
            DateBand(rangeDaysStart = Int.MIN_VALUE, rangeDaysEnd = 0, target = PriorityKey.ASAP),
            DateBand(rangeDaysStart = 0, rangeDaysEnd = 1, target = PriorityKey.TODAY),
            DateBand(rangeDaysStart = 1, rangeDaysEnd = 2, target = PriorityKey.TOMORROW),
            DateBand(rangeDaysStart = 2, rangeDaysEnd = 8, target = PriorityKey.SOON),
            DateBand(rangeDaysStart = 8, rangeDaysEnd = 31, target = PriorityKey.LATER),
            DateBand(rangeDaysStart = 31, rangeDaysEnd = Int.MAX_VALUE, target = PriorityKey.ONEDAY),
        )

        val DEFAULT = DateBandConfig()
    }
}

@Serializable
data class DateBand(
    /** Inclusive lower bound in days from today (0 = today, negative = overdue). */
    val rangeDaysStart: Int,
    /** Exclusive upper bound in days from today. */
    val rangeDaysEnd: Int,
    val target: PriorityKey,
)

object DateBandEngine {

    /**
     * Resolves a due date to its natural priority. Returns null for undated tasks (dueIso == null).
     * [dueIso] is YYYY-MM-DD (time portion ignored). [todayIso] is the local device date.
     */
    fun resolveBand(dueIso: String?, todayIso: String, config: DateBandConfig = DateBandConfig.DEFAULT): PriorityKey? {
        if (dueIso == null) return null
        val days = daysBetween(todayIso, dueIso.take(10))
        return config.bands.firstOrNull { days >= it.rangeDaysStart && days < it.rangeDaysEnd }?.target
    }

    /**
     * Human-readable preview label for the date picker: shows which priority band the date
     * falls into before the user confirms. Returns something like "Soon" or "Tomorrow".
     */
    fun previewLabel(dueIso: String, todayIso: String, config: DateBandConfig = DateBandConfig.DEFAULT): String {
        val key = resolveBand(dueIso, todayIso, config) ?: return ""
        return LABELS[key] ?: key.name.lowercase()
    }

    /**
     * Whether a dated task's due date has reached the "imminent" threshold (asap or today).
     * Used by the safety catch: fires when a far-future task auto-climbs to imminence.
     */
    fun isImminent(dueIso: String?, todayIso: String, config: DateBandConfig = DateBandConfig.DEFAULT): Boolean {
        val key = resolveBand(dueIso, todayIso, config) ?: return false
        return key == PriorityKey.ASAP || key == PriorityKey.TODAY
    }

    private val LABELS = mapOf(
        PriorityKey.ASAP to "ASAP",
        PriorityKey.TODAY to "Today",
        PriorityKey.TOMORROW to "Tomorrow",
        PriorityKey.SOON to "Soon",
        PriorityKey.LATER to "Later",
        PriorityKey.ONEDAY to "One day",
    )

    /**
     * Days between two ISO dates (YYYY-MM-DD). Positive means [to] is in the future.
     * Pure arithmetic on year/month/day: converts to a day serial via the proleptic Gregorian
     * calendar and subtracts. Accurate for any reasonable task date range.
     */
    internal fun daysBetween(from: String, to: String): Int {
        return toDaySerial(to) - toDaySerial(from)
    }

    private fun toDaySerial(iso: String): Int {
        val y = iso.substring(0, 4).toInt()
        val m = iso.substring(5, 7).toInt()
        val d = iso.substring(8, 10).toInt()
        // Algorithm: count days from a fixed epoch using the proleptic Gregorian calendar.
        val adjY = if (m <= 2) y - 1 else y
        val adjM = if (m <= 2) m + 9 else m - 3
        return 365 * adjY + adjY / 4 - adjY / 100 + adjY / 400 + (adjM * 306 + 5) / 10 + d - 1
    }
}
