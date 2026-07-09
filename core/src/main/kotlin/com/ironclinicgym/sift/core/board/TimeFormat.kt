package com.ironclinicgym.sift.core.board

/**
 * Clock formatting. Default is 12 hour with AM/PM; users can opt into 24 hour. Pure and
 * shared by the board time labels and the schedule editor so both read the same way.
 */

fun formatHourMinute(hour: Int, minute: Int, use24Hour: Boolean): String =
    if (use24Hour) {
        "%02d:%02d".format(hour, minute)
    } else {
        val suffix = if (hour < 12) "AM" else "PM"
        val h12 = ((hour + 11) % 12) + 1
        "%d:%02d %s".format(h12, minute, suffix)
    }

/** Reformat an "HH:mm" string (as Notion returns it) to the user's preferred clock. */
fun formatClock(hhmm: String, use24Hour: Boolean): String {
    if (hhmm.length < 5) return hhmm
    val hour = hhmm.substring(0, 2).toIntOrNull() ?: return hhmm
    val minute = hhmm.substring(3, 5).toIntOrNull() ?: 0
    return formatHourMinute(hour, minute, use24Hour)
}

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val DAYS = arrayOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

/**
 * Convert an ISO date (yyyy-MM-dd) to a human-friendly label relative to [todayIso].
 * Examples: "Today", "Tomorrow", "Tuesday", "Jul 15", "Jan 3, 2027".
 */
fun humanDate(dateIso: String?, todayIso: String): String? {
    if (dateIso == null || dateIso.length < 10) return null
    val dy = dateIso.substring(0, 4).toIntOrNull() ?: return dateIso
    val dm = dateIso.substring(5, 7).toIntOrNull() ?: return dateIso
    val dd = dateIso.substring(8, 10).toIntOrNull() ?: return dateIso
    val ty = todayIso.substring(0, 4).toIntOrNull() ?: return dateIso
    val tm = todayIso.substring(5, 7).toIntOrNull() ?: return dateIso
    val td = todayIso.substring(8, 10).toIntOrNull() ?: return dateIso

    val dateDays = daysSinceEpoch(dy, dm, dd)
    val todayDays = daysSinceEpoch(ty, tm, td)
    val diff = dateDays - todayDays

    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Tomorrow"
        diff == -1L -> "Yesterday"
        diff in 2L..6L -> DAYS[dayOfWeek(dy, dm, dd)]
        dy == ty -> "${MONTHS[dm - 1]} $dd"
        else -> "${MONTHS[dm - 1]} $dd, $dy"
    }
}

private fun daysSinceEpoch(y: Int, m: Int, d: Int): Long {
    val a = (14 - m) / 12
    val yAdj = y.toLong() - a
    val mAdj = m + 12 * a - 3
    return d + (153 * mAdj + 2) / 5 + 365 * yAdj + yAdj / 4 - yAdj / 100 + yAdj / 400 - 719469
}

private fun dayOfWeek(y: Int, m: Int, d: Int): Int {
    val days = daysSinceEpoch(y, m, d)
    return ((days % 7 + 10) % 7).toInt()
}

private fun epochDaysToIso(epochDays: Long): String {
    val z = epochDays + 719468L
    val era = z / 146097L
    val doe = (z - era * 146097L).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe.toLong() + era * 400L
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1L else y
    return "%04d-%02d-%02d".format(year, m, d)
}

private val LONG_MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun ordinalSuffix(day: Int): String {
    val mod100 = day % 100
    if (mod100 in 11..13) return "th"
    return when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

/**
 * Formats an ISO date string as "July 8th, 2026". Returns the raw string on parse failure.
 */
fun formatOrdinalDate(isoDate: String): String {
    val year  = isoDate.substring(0, 4).toIntOrNull() ?: return isoDate
    val month = isoDate.substring(5, 7).toIntOrNull() ?: return isoDate
    val day   = isoDate.substring(8, 10).toIntOrNull() ?: return isoDate
    return "${LONG_MONTHS[month - 1]} $day${ordinalSuffix(day)}, $year"
}

/**
 * Returns the priority context label for the task detail sheet.
 * Dated tasks: "Due today" or "Due July 8th, 2026". Undated: the display name as-is.
 */
fun priorityLabelForTask(
    isDated: Boolean,
    dueIso: String?,
    todayIso: String,
    priorityDisplayName: String,
): String {
    if (!isDated || dueIso.isNullOrBlank()) return priorityDisplayName
    val dueDate = dueIso.take(10)
    return if (dueDate == todayIso) "Due today"
    else "Due ${formatOrdinalDate(dueDate)}"
}

/** Returns (dateIso, Pair<hour, minute>) for the "Later today" chip: now + 4 hours. */
fun quickDateLaterToday(nowHour: Int, nowMinute: Int, todayIso: String): Pair<String, Pair<Int, Int>> {
    val totalMinutes = nowHour * 60 + nowMinute + 240
    return if (totalMinutes >= 1440) {
        quickDateTomorrow(todayIso).first to (totalMinutes % 1440 / 60 to totalMinutes % 60)
    } else {
        todayIso to (totalMinutes / 60 to totalMinutes % 60)
    }
}

/** Returns (dateIso, Pair<8, 0>) for the "Tomorrow" chip. */
fun quickDateTomorrow(todayIso: String): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (8 to 0)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (8 to 0)
    val d = todayIso.substring(8, 10).toIntOrNull() ?: return todayIso to (8 to 0)
    return epochDaysToIso(daysSinceEpoch(y, m, d) + 1L) to (8 to 0)
}

/** Returns (dateIso, Pair<8, 0>) for "Next week": next Sunday at 8am. Default first day of week;
 *  configurable in Settings (not yet wired — update this when Settings wires the preference). */
fun quickDateNextWeek(todayIso: String): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (8 to 0)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (8 to 0)
    val d = todayIso.substring(8, 10).toIntOrNull() ?: return todayIso to (8 to 0)
    val dow = dayOfWeek(y, m, d) // 0=Monday … 6=Sunday
    val daysToAdd = if (dow == 6) 7L else (6 - dow).toLong()
    return epochDaysToIso(daysSinceEpoch(y, m, d) + daysToAdd) to (8 to 0)
}

/** Returns (dateIso, Pair<8, 0>) for "Next month": 1st of next calendar month at 8am. */
fun quickDateNextMonth(todayIso: String): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (8 to 0)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (8 to 0)
    val nextY = if (m == 12) y + 1 else y
    val nextM = if (m == 12) 1 else m + 1
    return "%04d-%02d-01".format(nextY, nextM) to (8 to 0)
}
