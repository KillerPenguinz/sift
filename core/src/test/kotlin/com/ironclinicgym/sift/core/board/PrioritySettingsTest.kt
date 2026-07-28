package com.ironclinicgym.sift.core.board

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PrioritySettingsTest {

    private fun boardWith(useGlobal: Boolean, override: PrioritySettings?): BoardSettings =
        BoardSettings(mappingId = "m1", priorities = emptyList(), buckets = emptyList())
            .copy(useGlobalPrioritySettings = useGlobal, prioritySettingsOverride = override)

    @Test fun `defaults`() {
        val p = PrioritySettings()
        assertEquals(7, p.soonMaxDays)
        assertEquals(30, p.laterMaxDays)
        assertEquals(8, p.tomorrowHour)
        assertEquals(0, p.tomorrowMinute)
        assertEquals(7, p.firstDayOfWeek)
    }

    @Test fun `boundary setters keep soon below later`() {
        val p = PrioritySettings().setSoonMaxDays(40)
        assert(p.soonMaxDays < p.laterMaxDays)
        val q = PrioritySettings().setLaterMaxDays(3)
        assert(q.laterMaxDays > q.soonMaxDays)
    }

    @Test fun `time and day setters clamp`() {
        val p = PrioritySettings().setTomorrowTime(30, 90).setFirstDayOfWeek(9)
        assertEquals(23, p.tomorrowHour)
        assertEquals(59, p.tomorrowMinute)
        assertEquals(7, p.firstDayOfWeek)
    }

    @Test fun `resolver picks global or override`() {
        val global = PrioritySettings(soonMaxDays = 7)
        val override = PrioritySettings(soonMaxDays = 3)
        assertEquals(global, resolvePrioritySettings(global, boardWith(true, override)))
        assertEquals(override, resolvePrioritySettings(global, boardWith(false, override)))
        assertEquals(global, resolvePrioritySettings(global, boardWith(false, null)))
    }

    @Test fun `dateBandConfig delegates to fromBoundaries`() {
        val p = PrioritySettings(soonMaxDays = 4, laterMaxDays = 20)
        assertEquals(DateBandConfig.fromBoundaries(4, 20), p.dateBandConfig())
    }

    @Test fun `normalized clamps out of range values`() {
        val bad = PrioritySettings(
            soonMaxDays = 0, laterMaxDays = 0, tomorrowHour = 40, nextWeekMinute = -5, firstDayOfWeek = 12,
        ).normalized()
        assert(bad.soonMaxDays >= 2)
        assert(bad.laterMaxDays > bad.soonMaxDays)
        assertEquals(23, bad.tomorrowHour)
        assertEquals(0, bad.nextWeekMinute)
        assertEquals(7, bad.firstDayOfWeek)
    }

    @Test fun `legacy BoardSettings JSON decodes new fields to defaults`() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"mappingId":"m1","priorities":[],"buckets":[]}"""
        val decoded = json.decodeFromString(BoardSettings.serializer(), legacy)
        assertEquals(true, decoded.useGlobalPrioritySettings)
        assertEquals(null, decoded.prioritySettingsOverride)
    }

    @Test fun `resolver normalizes a malformed override`() {
        val global = PrioritySettings()
        val bad = PrioritySettings(tomorrowHour = 99)
        assertEquals(23, resolvePrioritySettings(global, boardWith(false, bad)).tomorrowHour)
    }

    @Test fun `routePriorityEdit under global scope saves global`() {
        val target = routePriorityEdit(boardWith(true, null), PrioritySettings()) { it.setSoonMaxDays(5) }
        assertEquals(PriorityEdit.SaveGlobal(PrioritySettings(soonMaxDays = 5)), target)
    }

    @Test fun `routePriorityEdit under per-database scope saves the board override`() {
        val board = boardWith(false, PrioritySettings())
        val target = routePriorityEdit(board, PrioritySettings()) { it.setSoonMaxDays(5) }
        assert(target is PriorityEdit.SaveBoard)
        assertEquals(5, (target as PriorityEdit.SaveBoard).board.prioritySettingsOverride?.soonMaxDays)
    }
}
