package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.theme.PriorityKey
import org.junit.Assert.assertEquals
import org.junit.Test

class DateBandBoundariesTest {

    @Test fun `default boundaries match DEFAULT_BANDS`() {
        assertEquals(DateBandConfig.DEFAULT_BANDS, DateBandConfig.fromBoundaries(7, 30).bands)
    }

    @Test fun `day five is soon by default and later when soon tightened`() {
        val today = "2026-07-01"
        val plus5 = "2026-07-06"
        assertEquals(PriorityKey.SOON, DateBandEngine.resolveBand(plus5, today, DateBandConfig.fromBoundaries(7, 30)))
        assertEquals(PriorityKey.LATER, DateBandEngine.resolveBand(plus5, today, DateBandConfig.fromBoundaries(3, 30)))
    }

    @Test fun `inverted input is clamped to soon less than later`() {
        val cfg = DateBandConfig.fromBoundaries(30, 5)
        val soonEnd = cfg.bands.first { it.target == PriorityKey.SOON }.rangeDaysEnd
        val laterEnd = cfg.bands.first { it.target == PriorityKey.LATER }.rangeDaysEnd
        assert(soonEnd < laterEnd) { "expected soonEnd < laterEnd, got $soonEnd / $laterEnd" }
    }

    @Test fun `below minimum boundary clamps up to two`() {
        val cfg = DateBandConfig.fromBoundaries(0, 1)
        val soon = cfg.bands.first { it.target == PriorityKey.SOON }
        assertEquals(2, soon.rangeDaysStart)
        assert(soon.rangeDaysEnd > soon.rangeDaysStart)
    }
}
