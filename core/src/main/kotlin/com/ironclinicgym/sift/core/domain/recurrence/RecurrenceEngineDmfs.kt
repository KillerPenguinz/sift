package com.ironclinicgym.sift.core.domain.recurrence

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule

/**
 * [RecurrenceEngine] backed by org.dmfs:lib-recur, a mature RFC 5545 implementation. The only
 * file in core that touches the library, so the dependency stays isolated behind the interface.
 *
 * Occurrences are computed on the date component only (all-day), then the original time-of-day
 * suffix from the source due value is reattached, so "every Tuesday at 09:00" keeps its time.
 */
class RecurrenceEngineDmfs : RecurrenceEngine {

    override fun isValid(rrule: String): Boolean = parse(rrule) != null

    override fun next(rrule: String, afterIso: String): String? {
        val rule = parse(rrule) ?: return null
        val datePart = afterIso.take(10)
        val ymd = parseYmd(datePart) ?: return null
        val (y, m, d) = ymd

        val start = DateTime(y, m - 1, d) // lib-recur months are 0-based, all-day floating.
        val iterator = rule.iterator(start)
        var guard = 0
        while (iterator.hasNext() && guard++ < MAX_STEPS) {
            val dt = iterator.nextDateTime()
            val cy = dt.year
            val cm = dt.month + 1
            val cd = dt.dayOfMonth
            if (isAfter(cy, cm, cd, y, m, d)) {
                val timeSuffix = if (afterIso.length > 10) afterIso.substring(10) else ""
                return formatYmd(cy, cm, cd) + timeSuffix
            }
        }
        return null
    }

    private fun parse(rrule: String): RecurrenceRule? = try {
        RecurrenceRule(rrule.removePrefix("RRULE:").trim(), RecurrenceRule.RfcMode.RFC5545_LAX)
    } catch (e: Exception) {
        null
    }

    private fun parseYmd(iso: String): Triple<Int, Int, Int>? {
        val bits = iso.split("-")
        if (bits.size != 3) return null
        val y = bits[0].toIntOrNull() ?: return null
        val m = bits[1].toIntOrNull() ?: return null
        val d = bits[2].toIntOrNull() ?: return null
        return Triple(y, m, d)
    }

    private fun isAfter(y1: Int, m1: Int, d1: Int, y0: Int, m0: Int, d0: Int): Boolean =
        y1 > y0 || (y1 == y0 && (m1 > m0 || (m1 == m0 && d1 > d0)))

    private fun formatYmd(y: Int, m: Int, d: Int): String =
        y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')

    private companion object {
        // Bounds the walk for pathological rules; ~10 years of daily steps is far past any real due.
        const val MAX_STEPS = 3660
    }
}
