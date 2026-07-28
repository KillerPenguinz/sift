package com.ironclinicgym.sift.core.board

import kotlinx.serialization.Serializable

/**
 * User-tunable priority-timing behavior: how due dates map to priority bands (the Soon/Later
 * boundaries) and the local time-of-day defaults for the quick-date chips. Global by default
 * (one shared value across databases) with an optional per-database override, resolved by
 * [resolvePrioritySettings]. Pure and serializable so it stays KMP-portable.
 */
@Serializable
data class PrioritySettings(
    /** "Soon" covers dates up to this many days out. Default 7. */
    val soonMaxDays: Int = 7,
    /** "Later" covers dates up to this many days out. Default 30. */
    val laterMaxDays: Int = 30,
    /** Local time-of-day for the "Tomorrow" quick-date chip. */
    val tomorrowHour: Int = 8,
    val tomorrowMinute: Int = 0,
    /** Local time-of-day for the "Next week" quick-date chip. */
    val nextWeekHour: Int = 8,
    val nextWeekMinute: Int = 0,
    /** Local time-of-day for the "Next month" quick-date chip. */
    val nextMonthHour: Int = 8,
    val nextMonthMinute: Int = 0,
    /** First day of the week (1 = Monday .. 7 = Sunday). Default Sunday. Used by "Next week". */
    val firstDayOfWeek: Int = 7,
) {
    /** The date bands derived from the two tunable boundaries. */
    fun dateBandConfig(): DateBandConfig = DateBandConfig.fromBoundaries(soonMaxDays, laterMaxDays)

    companion object {
        val DEFAULT = PrioritySettings()
    }
}

fun PrioritySettings.setSoonMaxDays(days: Int): PrioritySettings {
    val soon = days.coerceIn(2, 363)
    return copy(soonMaxDays = soon, laterMaxDays = laterMaxDays.coerceAtLeast(soon + 1))
}

fun PrioritySettings.setLaterMaxDays(days: Int): PrioritySettings =
    copy(laterMaxDays = days.coerceIn(soonMaxDays + 1, 364))

fun PrioritySettings.setTomorrowTime(hour: Int, minute: Int): PrioritySettings =
    copy(tomorrowHour = hour.coerceIn(0, 23), tomorrowMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setNextWeekTime(hour: Int, minute: Int): PrioritySettings =
    copy(nextWeekHour = hour.coerceIn(0, 23), nextWeekMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setNextMonthTime(hour: Int, minute: Int): PrioritySettings =
    copy(nextMonthHour = hour.coerceIn(0, 23), nextMonthMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setFirstDayOfWeek(day: Int): PrioritySettings =
    copy(firstDayOfWeek = day.coerceIn(1, 7))

/**
 * Clamp every field into a valid range. Applied after decoding persisted JSON and inside
 * [resolvePrioritySettings] so a malformed or future-written blob can never reach the UI or the
 * quick-date helpers with an invalid hour, minute, day, or boundary.
 */
fun PrioritySettings.normalized(): PrioritySettings {
    val soon = soonMaxDays.coerceIn(2, 363)
    return copy(
        soonMaxDays = soon,
        laterMaxDays = laterMaxDays.coerceIn(soon + 1, 364),
        tomorrowHour = tomorrowHour.coerceIn(0, 23),
        tomorrowMinute = tomorrowMinute.coerceIn(0, 59),
        nextWeekHour = nextWeekHour.coerceIn(0, 23),
        nextWeekMinute = nextWeekMinute.coerceIn(0, 59),
        nextMonthHour = nextMonthHour.coerceIn(0, 23),
        nextMonthMinute = nextMonthMinute.coerceIn(0, 59),
        firstDayOfWeek = firstDayOfWeek.coerceIn(1, 7),
    )
}

/** Effective priority settings for a database: the global value unless it opts into an override. Always normalized. */
fun resolvePrioritySettings(global: PrioritySettings, board: BoardSettings): PrioritySettings =
    (if (board.useGlobalPrioritySettings) global else (board.prioritySettingsOverride ?: global)).normalized()

/** Where a priority-timing edit must be persisted. */
sealed interface PriorityEdit {
    data class SaveGlobal(val global: PrioritySettings) : PriorityEdit
    data class SaveBoard(val board: BoardSettings) : PriorityEdit
}

/**
 * Pure routing for a priority-timing edit: apply [transform] to the current effective settings,
 * then persist to the global store when the board follows global, or to the board's override
 * otherwise. Extracted so the scope-routing decision is unit-testable without a ViewModel.
 */
fun routePriorityEdit(
    board: BoardSettings,
    global: PrioritySettings,
    transform: (PrioritySettings) -> PrioritySettings,
): PriorityEdit {
    val updated = transform(resolvePrioritySettings(global, board))
    return if (board.useGlobalPrioritySettings) PriorityEdit.SaveGlobal(updated)
    else PriorityEdit.SaveBoard(board.setPrioritySettingsOverride(updated))
}
