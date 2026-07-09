package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.theme.PriorityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DateBandEngineTest {

    private val today = "2026-07-04"

    @Test
    fun `null due returns null`() {
        assertNull(DateBandEngine.resolveBand(null, today))
    }

    @Test
    fun `overdue maps to ASAP`() {
        assertEquals(PriorityKey.ASAP, DateBandEngine.resolveBand("2026-07-01", today))
        assertEquals(PriorityKey.ASAP, DateBandEngine.resolveBand("2026-06-01", today))
        assertEquals(PriorityKey.ASAP, DateBandEngine.resolveBand("2025-12-31", today))
    }

    @Test
    fun `due today maps to TODAY`() {
        assertEquals(PriorityKey.TODAY, DateBandEngine.resolveBand("2026-07-04", today))
    }

    @Test
    fun `due tomorrow maps to TOMORROW`() {
        assertEquals(PriorityKey.TOMORROW, DateBandEngine.resolveBand("2026-07-05", today))
    }

    @Test
    fun `due in 2 to 7 days maps to SOON`() {
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand("2026-07-06", today))
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand("2026-07-10", today))
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand("2026-07-11", today))
    }

    @Test
    fun `due in 8 to 30 days maps to LATER`() {
        assertEquals(PriorityKey.LATER, DateBandEngine.resolveBand("2026-07-12", today))
        assertEquals(PriorityKey.LATER, DateBandEngine.resolveBand("2026-07-20", today))
        assertEquals(PriorityKey.LATER, DateBandEngine.resolveBand("2026-08-03", today))
    }

    @Test
    fun `due in 31+ days maps to ONEDAY`() {
        assertEquals(PriorityKey.ONEDAY, DateBandEngine.resolveBand("2026-08-04", today))
        assertEquals(PriorityKey.ONEDAY, DateBandEngine.resolveBand("2027-01-01", today))
    }

    @Test
    fun `datetime strings are handled by taking the date portion`() {
        assertEquals(PriorityKey.TODAY, DateBandEngine.resolveBand("2026-07-04T14:30:00", today))
        assertEquals(PriorityKey.TOMORROW, DateBandEngine.resolveBand("2026-07-05T09:00:00", today))
    }

    @Test
    fun `preview label returns human text`() {
        assertEquals("Today", DateBandEngine.previewLabel("2026-07-04", today))
        assertEquals("Tomorrow", DateBandEngine.previewLabel("2026-07-05", today))
        assertEquals("Soon", DateBandEngine.previewLabel("2026-07-08", today))
        assertEquals("Later", DateBandEngine.previewLabel("2026-07-20", today))
        assertEquals("One day", DateBandEngine.previewLabel("2026-12-25", today))
        assertEquals("ASAP", DateBandEngine.previewLabel("2026-07-01", today))
    }

    @Test
    fun `isImminent for overdue and today`() {
        assertTrue(DateBandEngine.isImminent("2026-07-01", today))
        assertTrue(DateBandEngine.isImminent("2026-07-04", today))
        assertFalse(DateBandEngine.isImminent("2026-07-05", today))
        assertFalse(DateBandEngine.isImminent("2026-08-01", today))
        assertFalse(DateBandEngine.isImminent(null, today))
    }

    @Test
    fun `daysBetween computes correctly`() {
        assertEquals(0, DateBandEngine.daysBetween("2026-07-04", "2026-07-04"))
        assertEquals(1, DateBandEngine.daysBetween("2026-07-04", "2026-07-05"))
        assertEquals(-3, DateBandEngine.daysBetween("2026-07-04", "2026-07-01"))
        assertEquals(365, DateBandEngine.daysBetween("2026-01-01", "2027-01-01"))
        assertEquals(31, DateBandEngine.daysBetween("2026-07-01", "2026-08-01"))
    }

    @Test
    fun `custom config overrides defaults`() {
        val config = DateBandConfig(
            bands = listOf(
                DateBand(Int.MIN_VALUE, 0, PriorityKey.ASAP),
                DateBand(0, 1, PriorityKey.TODAY),
                DateBand(1, 4, PriorityKey.SOON),
                DateBand(4, Int.MAX_VALUE, PriorityKey.ONEDAY),
            ),
        )
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand("2026-07-05", today, config))
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand("2026-07-07", today, config))
        assertEquals(PriorityKey.ONEDAY, DateBandEngine.resolveBand("2026-07-08", today, config))
    }
}
