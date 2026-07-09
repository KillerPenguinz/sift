package com.ironclinicgym.sift.core.domain.recurrence

/**
 * Generates the plain-English label Sift stores in the "Sift Repeats" field so Notion stays
 * legible. The spec is explicit that Sift generates this text (not the library), which also
 * lets us guarantee no em dashes or hyphens in user-facing output.
 *
 * Best effort over the common cases (daily, weekly with weekdays, monthly, yearly, intervals,
 * every weekday). Anything exotic falls back to a neutral, hyphen-free phrase.
 */
object RecurrenceText {

    private val dayNames = linkedMapOf(
        "MO" to "Monday", "TU" to "Tuesday", "WE" to "Wednesday", "TH" to "Thursday",
        "FR" to "Friday", "SA" to "Saturday", "SU" to "Sunday",
    )
    private val weekdaySet = setOf("MO", "TU", "WE", "TH", "FR")

    /** A friendly, hyphen-free sentence for [rrule], or a neutral fallback. */
    fun describe(rrule: String): String {
        val parts = parse(rrule)
        val freq = parts["FREQ"] ?: return FALLBACK
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val byDay = parts["BYDAY"]?.split(",")?.map { it.trim().takeLast(2) }?.filter { it in dayNames }.orEmpty()

        return when (freq) {
            "DAILY" -> if (interval <= 1) "Every day" else "Every $interval days"
            "WEEKLY" -> describeWeekly(interval, byDay)
            "MONTHLY" -> if (interval <= 1) "Every month" else "Every $interval months"
            "YEARLY" -> if (interval <= 1) "Every year" else "Every $interval years"
            else -> FALLBACK
        }
    }

    private fun describeWeekly(interval: Int, byDay: List<String>): String {
        if (byDay.isEmpty()) return if (interval <= 1) "Every week" else "Every $interval weeks"
        if (interval <= 1 && byDay.toSet() == weekdaySet) return "Every weekday"
        val labels = byDay.mapNotNull { dayNames[it] }
        val joined = joinFriendly(labels)
        return if (interval <= 1) "Every $joined" else "Every $interval weeks on $joined"
    }

    /** "Monday", "Monday and Tuesday", "Monday, Tuesday and Wednesday" (no Oxford comma issues). */
    private fun joinFriendly(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun parse(rrule: String): Map<String, String> =
        rrule.removePrefix("RRULE:").split(";")
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).uppercase().trim() to part.substring(i + 1).trim()
            }
            .toMap()

    const val FALLBACK = "Repeats on a custom schedule"
}
