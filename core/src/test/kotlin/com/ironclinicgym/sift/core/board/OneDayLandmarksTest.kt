package com.ironclinicgym.sift.core.board

import org.junit.Assert.assertEquals
import org.junit.Test

class OneDayLandmarksTest {

    private val today = "2026-07-04"

    @Test
    fun `null due returns NO_DATE`() {
        assertEquals(Landmark.NO_DATE, OneDayLandmarks.assignLandmark(null, today))
    }

    @Test
    fun `same month returns THIS_MONTH`() {
        assertEquals(Landmark.THIS_MONTH, OneDayLandmarks.assignLandmark("2026-07-20", today))
        assertEquals(Landmark.THIS_MONTH, OneDayLandmarks.assignLandmark("2026-07-31", today))
    }

    @Test
    fun `within 90 days but different month returns NEXT_90`() {
        assertEquals(Landmark.NEXT_90, OneDayLandmarks.assignLandmark("2026-08-15", today))
        assertEquals(Landmark.NEXT_90, OneDayLandmarks.assignLandmark("2026-10-01", today))
    }

    @Test
    fun `same year beyond 90 days returns LATER_THIS_YEAR`() {
        assertEquals(Landmark.LATER_THIS_YEAR, OneDayLandmarks.assignLandmark("2026-12-25", today))
    }

    @Test
    fun `next year returns BEYOND`() {
        assertEquals(Landmark.BEYOND, OneDayLandmarks.assignLandmark("2027-03-15", today))
    }

    @Test
    fun `display labels are correct`() {
        assertEquals("This month", OneDayLandmarks.displayLabel(Landmark.THIS_MONTH))
        assertEquals("Next 90 days", OneDayLandmarks.displayLabel(Landmark.NEXT_90))
        assertEquals("Later this year", OneDayLandmarks.displayLabel(Landmark.LATER_THIS_YEAR))
        assertEquals("Beyond", OneDayLandmarks.displayLabel(Landmark.BEYOND))
        assertEquals("No date", OneDayLandmarks.displayLabel(Landmark.NO_DATE))
    }
}
