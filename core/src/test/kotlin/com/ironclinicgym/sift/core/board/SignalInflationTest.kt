package com.ironclinicgym.sift.core.board

import org.junit.Assert.*
import org.junit.Test

class SignalInflationTest {

    @Test fun `fires ASAP_OVERLOAD when count equals threshold`() {
        val alert = SignalInflation.checkInflation(
            asapCount = 5, totalTasks = 20, protectedCount = 0,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNotNull(alert)
        assertEquals(InflationKind.ASAP_OVERLOAD, alert!!.kind)
    }

    @Test fun `does not fire ASAP_OVERLOAD when count below threshold`() {
        val alert = SignalInflation.checkInflation(
            asapCount = 4, totalTasks = 20, protectedCount = 0,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNull(alert)
    }

    @Test fun `fires PROTECTED_SATURATION when protected pct reaches threshold`() {
        val alert = SignalInflation.checkInflation(
            asapCount = 0, totalTasks = 10, protectedCount = 3,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNotNull(alert)
        assertEquals(InflationKind.PROTECTED_SATURATION, alert!!.kind)
    }

    @Test fun `does not fire protected when pct below threshold`() {
        val alert = SignalInflation.checkInflation(
            asapCount = 0, totalTasks = 10, protectedCount = 2,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNull(alert)
    }

    @Test fun `ASAP takes priority over protected when both exceed threshold`() {
        val alert = SignalInflation.checkInflation(
            asapCount = 5, totalTasks = 5, protectedCount = 5,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNotNull(alert)
    }

    @Test fun `default asapThreshold is 5 not 7`() {
        val atFive = SignalInflation.checkInflation(asapCount = 5, totalTasks = 10, protectedCount = 0)
        assertNotNull(atFive)
        val atFour = SignalInflation.checkInflation(asapCount = 4, totalTasks = 10, protectedCount = 0)
        assertNull(atFour)
    }
}
