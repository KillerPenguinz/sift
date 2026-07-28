package com.ironclinicgym.sift.core.board

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickDateTest {
    // 2026-07-01 is a Wednesday.
    private val wed = "2026-07-01"

    @Test fun `tomorrow honors configured time`() {
        assertEquals("2026-07-02" to (9 to 30), quickDateTomorrow(wed, 9, 30))
    }

    @Test fun `tomorrow defaults to 8am`() {
        assertEquals("2026-07-02" to (8 to 0), quickDateTomorrow(wed))
    }

    @Test fun `next week sunday default matches legacy`() {
        // From Wed 2026-07-01, next Sunday is 2026-07-05.
        assertEquals("2026-07-05" to (8 to 0), quickDateNextWeek(wed))
    }

    @Test fun `next week honors monday first day and time`() {
        // From Wed 2026-07-01, next Monday is 2026-07-06.
        assertEquals("2026-07-06" to (7 to 15), quickDateNextWeek(wed, firstDayOfWeek = 1, hour = 7, minute = 15))
    }

    @Test fun `next month first of next month at configured time`() {
        assertEquals("2026-08-01" to (6 to 45), quickDateNextMonth(wed, 6, 45))
    }
}
