package com.ironclinicgym.sift.core.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {

    private val engine = RecurrenceEngineDmfs()

    @Test
    fun `daily advances by one day`() {
        assertEquals("2026-07-03", engine.next("FREQ=DAILY", "2026-07-02"))
    }

    @Test
    fun `every N days respects the interval`() {
        assertEquals("2026-07-05", engine.next("FREQ=DAILY;INTERVAL=3", "2026-07-02"))
    }

    @Test
    fun `weekly by weekday finds the next matching day`() {
        // 2026-07-02 is a Thursday; next Tuesday-or-Thursday rule day after it is Tuesday 07-07.
        assertEquals("2026-07-07", engine.next("FREQ=WEEKLY;BYDAY=TU,TH", "2026-07-02"))
    }

    @Test
    fun `preserves a time-of-day suffix`() {
        assertEquals("2026-07-03T09:00", engine.next("FREQ=DAILY", "2026-07-02T09:00"))
    }

    @Test
    fun `an exhausted count rule returns null`() {
        // One occurrence only; nothing strictly after the start date.
        assertNull(engine.next("FREQ=DAILY;COUNT=1", "2026-07-02"))
    }

    @Test
    fun `invalid rules are rejected`() {
        assertFalse(engine.isValid("not a rule"))
        assertTrue(engine.isValid("FREQ=WEEKLY;BYDAY=MO"))
        assertNull(engine.next("garbage", "2026-07-02"))
    }

    @Test
    fun `plain English is friendly and hyphen free`() {
        assertEquals("Every day", RecurrenceText.describe("FREQ=DAILY"))
        assertEquals("Every 2 days", RecurrenceText.describe("FREQ=DAILY;INTERVAL=2"))
        assertEquals("Every weekday", RecurrenceText.describe("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("Every Tuesday and Thursday", RecurrenceText.describe("FREQ=WEEKLY;BYDAY=TU,TH"))
        assertEquals("Every month", RecurrenceText.describe("FREQ=MONTHLY"))
        assertFalse(RecurrenceText.describe("FREQ=DAILY;INTERVAL=2").contains("-"))
    }
}
