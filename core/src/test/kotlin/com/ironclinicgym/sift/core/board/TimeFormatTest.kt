package com.ironclinicgym.sift.core.board

import org.junit.Assert.*
import org.junit.Test

class TimeFormatTest {

    // formatOrdinalDate — ordinal suffix rules
    @Test fun `ordinal 1st`() = assertEquals("July 1st, 2026", formatOrdinalDate("2026-07-01"))
    @Test fun `ordinal 2nd`() = assertEquals("August 2nd, 2026", formatOrdinalDate("2026-08-02"))
    @Test fun `ordinal 3rd`() = assertEquals("March 3rd, 2026", formatOrdinalDate("2026-03-03"))
    @Test fun `ordinal 4th`() = assertEquals("April 4th, 2026", formatOrdinalDate("2026-04-04"))
    @Test fun `ordinal 11th is th not st`() = assertEquals("November 11th, 2026", formatOrdinalDate("2026-11-11"))
    @Test fun `ordinal 12th is th not nd`() = assertEquals("December 12th, 2026", formatOrdinalDate("2026-12-12"))
    @Test fun `ordinal 13th is th not rd`() = assertEquals("March 13th, 2026", formatOrdinalDate("2026-03-13"))
    @Test fun `ordinal 21st`() = assertEquals("January 21st, 2026", formatOrdinalDate("2026-01-21"))
    @Test fun `ordinal 22nd`() = assertEquals("February 22nd, 2026", formatOrdinalDate("2026-02-22"))
    @Test fun `ordinal 23rd`() = assertEquals("March 23rd, 2026", formatOrdinalDate("2026-03-23"))
    @Test fun `ordinal 31st`() = assertEquals("January 31st, 2026", formatOrdinalDate("2026-01-31"))

    // priorityLabelForTask
    @Test fun `dated today returns Due today`() {
        val label = priorityLabelForTask(isDated = true, dueIso = "2026-07-09T10:00:00", todayIso = "2026-07-09", priorityDisplayName = "ASAP")
        assertEquals("Due today", label)
    }
    @Test fun `dated other day returns ordinal date`() {
        val label = priorityLabelForTask(isDated = true, dueIso = "2026-07-15T10:00:00", todayIso = "2026-07-09", priorityDisplayName = "ASAP")
        assertEquals("Due July 15th, 2026", label)
    }
    @Test fun `undated returns display name only`() {
        val label = priorityLabelForTask(isDated = false, dueIso = null, todayIso = "2026-07-09", priorityDisplayName = "ASAP")
        assertEquals("ASAP", label)
    }
    @Test fun `undated Today has no prefix`() {
        val label = priorityLabelForTask(isDated = false, dueIso = null, todayIso = "2026-07-09", priorityDisplayName = "Today")
        assertFalse(label.startsWith("in "))
        assertFalse(label.startsWith("Due "))
        assertEquals("Today", label)
    }

    // quickDateLaterToday
    @Test fun `later today adds 4 hours same day`() {
        val (date, time) = quickDateLaterToday(10, 0, "2026-07-09")
        assertEquals("2026-07-09", date)
        assertEquals(14, time.first)
        assertEquals(0, time.second)
    }
    @Test fun `later today crosses midnight`() {
        val (date, time) = quickDateLaterToday(22, 30, "2026-07-09")
        assertEquals("2026-07-10", date)
        assertEquals(2, time.first)
        assertEquals(30, time.second)
    }

    // quickDateTomorrow
    @Test fun `tomorrow is next day at 8am`() {
        val (date, time) = quickDateTomorrow("2026-07-09")
        assertEquals("2026-07-10", date)
        assertEquals(8, time.first)
        assertEquals(0, time.second)
    }

    // quickDateNextWeek
    @Test fun `nextWeek from Monday reaches next Sunday`() {
        // 2026-07-06 is Monday (dow=0), next Sunday is 2026-07-12
        val (date, time) = quickDateNextWeek("2026-07-06")
        assertEquals("2026-07-12", date)
        assertEquals(8, time.first)
        assertEquals(0, time.second)
    }

    @Test fun `nextWeek from Sunday reaches 7 days later`() {
        // 2026-07-05 is Sunday (dow=6), next Sunday is also 2026-07-12 (7 days)
        val (date, time) = quickDateNextWeek("2026-07-05")
        assertEquals("2026-07-12", date)
        assertEquals(8, time.first)
        assertEquals(0, time.second)
    }

    @Test fun `nextWeek from Saturday reaches next day`() {
        // 2026-07-11 is Saturday (dow=5), next Sunday is 2026-07-12 (1 day)
        val (date, time) = quickDateNextWeek("2026-07-11")
        assertEquals("2026-07-12", date)
        assertEquals(8, time.first)
        assertEquals(0, time.second)
    }

    // quickDateNextMonth
    @Test fun `next month is 1st of next month at 8am`() {
        val (date, time) = quickDateNextMonth("2026-07-09")
        assertEquals("2026-08-01", date)
        assertEquals(8, time.first)
        assertEquals(0, time.second)
    }
    @Test fun `next month wraps December to January`() {
        val (date, _) = quickDateNextMonth("2026-12-15")
        assertEquals("2027-01-01", date)
    }
}
