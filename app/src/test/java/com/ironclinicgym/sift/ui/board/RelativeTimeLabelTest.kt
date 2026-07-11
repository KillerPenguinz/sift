package com.ironclinicgym.sift.ui.board

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeLabelTest {

    private val now = 1_752_000_000_000L

    private val minuteMs = 60_000L
    private val hourMs = 3_600_000L
    private val dayMs = 86_400_000L
    private val weekMs = 7L * dayMs

    @Test
    fun `under one minute is now`() {
        assertEquals("now", relativeTimeLabel(now, now))
        assertEquals("now", relativeTimeLabel(now - 59_999L, now))
    }

    @Test
    fun `future timestamp clamps to now`() {
        assertEquals("now", relativeTimeLabel(now + 5 * minuteMs, now))
    }

    @Test
    fun `minutes from one to fifty nine`() {
        assertEquals("1m", relativeTimeLabel(now - minuteMs, now))
        assertEquals("5m", relativeTimeLabel(now - 5 * minuteMs, now))
        assertEquals("59m", relativeTimeLabel(now - 59 * minuteMs - 59_999L, now))
    }

    @Test
    fun `hours from one to twenty three`() {
        assertEquals("1h", relativeTimeLabel(now - hourMs, now))
        assertEquals("2h", relativeTimeLabel(now - 2 * hourMs, now))
        assertEquals("23h", relativeTimeLabel(now - 24 * hourMs + 1L, now))
    }

    @Test
    fun `days from one to six`() {
        assertEquals("1d", relativeTimeLabel(now - dayMs, now))
        assertEquals("3d", relativeTimeLabel(now - 3 * dayMs, now))
        assertEquals("6d", relativeTimeLabel(now - 7 * dayMs + 1L, now))
    }

    @Test
    fun `weeks at seven days and beyond`() {
        assertEquals("1w", relativeTimeLabel(now - weekMs, now))
        assertEquals("2w", relativeTimeLabel(now - 2 * weekMs, now))
        assertEquals("4w", relativeTimeLabel(now - 30 * dayMs, now))
    }
}
