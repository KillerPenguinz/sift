# Phase 3.5 Gap Tickets and Sign-off Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the three unbuilt Phase 3.5 gap features (blocked-flag UI, configurable date bands, configurable quick-date defaults), then assemble the UAT round 3 script and run all owned tests so BJ can sign off Phase 3.5.

**Architecture:** All three follow existing seams. The blocked flag reuses the local-flag pattern (`isProtected`); its data round-trip already exists, so only UI is added. Date bands and quick-date defaults share a new `PrioritySettings` group persisted in a global DataStore, with a per-database override toggle resolved by a pure function, then fed into `TwoAxisPolicy` and the add-task sheet.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), DataStore (Preferences), kotlinx.serialization, JUnit (`:core:test`), manual DI (AppContainer).

**User decisions (already made):**
- "Build gaps first, then sign off" — build T-006/T-008/T-009, then one UAT round 3, then Phase 3.5 sign-off.
- T-006 blocked entry: "Task detail only (drop the swipe)" plus a board card indicator.
- T-008 band editor: "Edit the boundaries only" (Soon/Later day boundaries; today/tomorrow fixed).
- Settings scope: global by default, "toggleable to be per database for the power user."
- Quick-date: "tomorrow would be tomorrow at 8 am" in the user's local time (local time-of-day, not an absolute instant).

**Standing constraints (CLAUDE.md):** no em dashes or hyphens in user-facing text; token discipline (no hardcoded colors); `collectAsStateWithLifecycle` only; all reads/writes go through the mapping. Do NOT add a new Material Symbols glyph: the bundled font is a subset with no in-repo regen tooling, and `BoardIconsFontTest` fails on a missing glyph. Reuse existing glyphs / the text-tag pattern.

---

### Task 1: Date-band boundaries builder (core, T-008)

**Goal:** A pure `DateBandConfig.fromBoundaries(soonMaxDays, laterMaxDays)` that moves only the Soon/Later end lines and keeps overdue/today/tomorrow fixed, clamped so `2 <= soonMaxDays < laterMaxDays`.

**Files:**
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/DateBandEngine.kt` (add to the `DateBandConfig.companion`)
- Test: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/DateBandBoundariesTest.kt`

**Acceptance Criteria:**
- [ ] `fromBoundaries(7, 30)` equals the current `DEFAULT_BANDS` (SOON 2..8, LATER 8..31).
- [ ] `resolveBand` with a tuned config re-buckets dates (e.g. day 5 -> SOON at default, -> LATER when `soonMaxDays = 3`).
- [ ] Clamping repairs invalid input: `fromBoundaries(30, 5)` yields `soon < later` with no gap/overlap; boundaries below 2 clamp up.

**Verify:** `./gradlew :core:test --tests "*DateBandBoundariesTest*"` -> BUILD SUCCESSFUL, all tests pass.

**Steps:**

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/com/ironclinicgym/sift/core/board/DateBandBoundariesTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "*DateBandBoundariesTest*"`
Expected: FAIL, `fromBoundaries` unresolved reference.

- [ ] **Step 3: Add `fromBoundaries` to the companion**

In `DateBandEngine.kt`, inside `companion object` of `DateBandConfig` (right after `val DEFAULT = DateBandConfig()`), add:

```kotlin
        /**
         * Build a config from the two tunable boundaries. Overdue/today/tomorrow stay fixed; SOON
         * covers days 2..soonMaxDays, LATER covers up to laterMaxDays, ONEDAY beyond. rangeDaysEnd
         * is exclusive, so "up to N days" maps to end = N + 1. Clamps to 2 <= soonMaxDays < laterMaxDays
         * so the bands always tile with no gap or overlap.
         */
        fun fromBoundaries(soonMaxDays: Int, laterMaxDays: Int): DateBandConfig {
            val soon = soonMaxDays.coerceIn(2, 363)
            val later = laterMaxDays.coerceIn(soon + 1, 364)
            return DateBandConfig(
                bands = listOf(
                    DateBand(rangeDaysStart = Int.MIN_VALUE, rangeDaysEnd = 0, target = PriorityKey.ASAP),
                    DateBand(rangeDaysStart = 0, rangeDaysEnd = 1, target = PriorityKey.TODAY),
                    DateBand(rangeDaysStart = 1, rangeDaysEnd = 2, target = PriorityKey.TOMORROW),
                    DateBand(rangeDaysStart = 2, rangeDaysEnd = soon + 1, target = PriorityKey.SOON),
                    DateBand(rangeDaysStart = soon + 1, rangeDaysEnd = later + 1, target = PriorityKey.LATER),
                    DateBand(rangeDaysStart = later + 1, rangeDaysEnd = Int.MAX_VALUE, target = PriorityKey.ONEDAY),
                ),
            )
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "*DateBandBoundariesTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/DateBandEngine.kt core/src/test/kotlin/com/ironclinicgym/sift/core/board/DateBandBoundariesTest.kt
git commit -m "feat(core): DateBandConfig.fromBoundaries for tunable Soon/Later bands (T-008)"
```

---

### Task 2: PrioritySettings model, resolver, and scope fields (core)

**Goal:** A serializable `PrioritySettings` group (bands + quick-date times + first day of week) with clamped edits, a `dateBandConfig()` helper, a `resolvePrioritySettings` resolver, `BoardSettings` scope fields, and a `PrioritySettingsStore` port.

**Files:**
- Create: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/PrioritySettings.kt`
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt` (add two fields)
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettingsEdits.kt` (add scope edits)
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/domain/ports/Ports.kt` (add port)
- Test: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/PrioritySettingsTest.kt`

**Acceptance Criteria:**
- [ ] `PrioritySettings()` defaults: soon 7, later 30, all times 08:00, firstDayOfWeek 7 (Sunday).
- [ ] `setSoonMaxDays` / `setLaterMaxDays` keep `soon < later`; time/day setters clamp to valid ranges.
- [ ] `resolvePrioritySettings` returns the global value when `useGlobalPrioritySettings` is true, and the override otherwise (falling back to global when the override is null), always `normalized()`.
- [ ] `PrioritySettings.normalized()` clamps all fields into valid ranges.
- [ ] `routePriorityEdit(board, global, transform)` returns `SaveGlobal` under global scope and `SaveBoard` (with the override set) under per-database scope.
- [ ] `BoardSettings` gains `useGlobalPrioritySettings = true` and `prioritySettingsOverride = null` (absent JSON decodes to these defaults; legacy-JSON test proves it).

**Verify:** `./gradlew :core:test --tests "*PrioritySettingsTest*"` -> all pass.

**Steps:**

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/com/ironclinicgym/sift/core/board/PrioritySettingsTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "*PrioritySettingsTest*"`
Expected: FAIL, unresolved `PrioritySettings` / `resolvePrioritySettings` / `useGlobalPrioritySettings`.

- [ ] **Step 3: Create `PrioritySettings.kt`**

```kotlin
package com.ironclinicgym.sift.core.board

import kotlinx.serialization.Serializable

/**
 * User-tunable priority-timing behavior: how due dates map to priority bands (the Soon/Later
 * boundaries) and the local time-of-day defaults for the quick-date chips. Global by default
 * (one shared value across databases) with an optional per-database override, resolved by
 * [resolvePrioritySettings]. Pure and serializable so it stays KMP-portable.
 */
@Serializable
data class PrioritySettings(
    /** "Soon" covers dates up to this many days out. Default 7. */
    val soonMaxDays: Int = 7,
    /** "Later" covers dates up to this many days out. Default 30. */
    val laterMaxDays: Int = 30,
    /** Local time-of-day for the "Tomorrow" quick-date chip. */
    val tomorrowHour: Int = 8,
    val tomorrowMinute: Int = 0,
    /** Local time-of-day for the "Next week" quick-date chip. */
    val nextWeekHour: Int = 8,
    val nextWeekMinute: Int = 0,
    /** Local time-of-day for the "Next month" quick-date chip. */
    val nextMonthHour: Int = 8,
    val nextMonthMinute: Int = 0,
    /** First day of the week (1 = Monday .. 7 = Sunday). Default Sunday. Used by "Next week". */
    val firstDayOfWeek: Int = 7,
) {
    /** The date bands derived from the two tunable boundaries. */
    fun dateBandConfig(): DateBandConfig = DateBandConfig.fromBoundaries(soonMaxDays, laterMaxDays)

    companion object {
        val DEFAULT = PrioritySettings()
    }
}

fun PrioritySettings.setSoonMaxDays(days: Int): PrioritySettings {
    val soon = days.coerceIn(2, 363)
    return copy(soonMaxDays = soon, laterMaxDays = laterMaxDays.coerceAtLeast(soon + 1))
}

fun PrioritySettings.setLaterMaxDays(days: Int): PrioritySettings =
    copy(laterMaxDays = days.coerceIn(soonMaxDays + 1, 364))

fun PrioritySettings.setTomorrowTime(hour: Int, minute: Int): PrioritySettings =
    copy(tomorrowHour = hour.coerceIn(0, 23), tomorrowMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setNextWeekTime(hour: Int, minute: Int): PrioritySettings =
    copy(nextWeekHour = hour.coerceIn(0, 23), nextWeekMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setNextMonthTime(hour: Int, minute: Int): PrioritySettings =
    copy(nextMonthHour = hour.coerceIn(0, 23), nextMonthMinute = minute.coerceIn(0, 59))

fun PrioritySettings.setFirstDayOfWeek(day: Int): PrioritySettings =
    copy(firstDayOfWeek = day.coerceIn(1, 7))

/**
 * Clamp every field into a valid range. Applied after decoding persisted JSON and inside
 * [resolvePrioritySettings] so a malformed or future-written blob can never reach the UI or the
 * quick-date helpers with an invalid hour, minute, day, or boundary.
 */
fun PrioritySettings.normalized(): PrioritySettings {
    val soon = soonMaxDays.coerceIn(2, 363)
    return copy(
        soonMaxDays = soon,
        laterMaxDays = laterMaxDays.coerceIn(soon + 1, 364),
        tomorrowHour = tomorrowHour.coerceIn(0, 23),
        tomorrowMinute = tomorrowMinute.coerceIn(0, 59),
        nextWeekHour = nextWeekHour.coerceIn(0, 23),
        nextWeekMinute = nextWeekMinute.coerceIn(0, 59),
        nextMonthHour = nextMonthHour.coerceIn(0, 23),
        nextMonthMinute = nextMonthMinute.coerceIn(0, 59),
        firstDayOfWeek = firstDayOfWeek.coerceIn(1, 7),
    )
}

/** Effective priority settings for a database: the global value unless it opts into an override. Always normalized. */
fun resolvePrioritySettings(global: PrioritySettings, board: BoardSettings): PrioritySettings =
    (if (board.useGlobalPrioritySettings) global else (board.prioritySettingsOverride ?: global)).normalized()

/** Where a priority-timing edit must be persisted. */
sealed interface PriorityEdit {
    data class SaveGlobal(val global: PrioritySettings) : PriorityEdit
    data class SaveBoard(val board: BoardSettings) : PriorityEdit
}

/**
 * Pure routing for a priority-timing edit: apply [transform] to the current effective settings,
 * then persist to the global store when the board follows global, or to the board's override
 * otherwise. Extracted so the scope-routing decision is unit-testable without a ViewModel.
 */
fun routePriorityEdit(
    board: BoardSettings,
    global: PrioritySettings,
    transform: (PrioritySettings) -> PrioritySettings,
): PriorityEdit {
    val updated = transform(resolvePrioritySettings(global, board))
    return if (board.useGlobalPrioritySettings) PriorityEdit.SaveGlobal(updated)
    else PriorityEdit.SaveBoard(board.setPrioritySettingsOverride(updated))
}
```

- [ ] **Step 4: Add fields to `BoardSettings`**

In `BoardSettings.kt`, add two fields to the `data class BoardSettings(...)` constructor, immediately after `val protectedInflationPercent: Int = 30,` (line 97):

```kotlin
    /** True: this database uses the global priority-timing settings. False: uses [prioritySettingsOverride]. */
    val useGlobalPrioritySettings: Boolean = true,
    /** Per-database override; consulted only when [useGlobalPrioritySettings] is false. */
    val prioritySettingsOverride: PrioritySettings? = null,
```

- [ ] **Step 5: Add scope edits to `BoardSettingsEdits.kt`**

Append to `BoardSettingsEdits.kt`:

```kotlin
/**
 * Toggle whether this database follows the global priority-timing settings. Turning the override
 * ON seeds it from the current global values so nothing visibly changes at the flip; turning it OFF
 * leaves the override in place (unused) so it can be restored later.
 */
fun BoardSettings.setUseGlobalPrioritySettings(useGlobal: Boolean, seedFromGlobal: PrioritySettings): BoardSettings =
    if (useGlobal) copy(useGlobalPrioritySettings = true)
    else copy(useGlobalPrioritySettings = false, prioritySettingsOverride = prioritySettingsOverride ?: seedFromGlobal)

fun BoardSettings.setPrioritySettingsOverride(settings: PrioritySettings): BoardSettings =
    copy(prioritySettingsOverride = settings)
```

- [ ] **Step 6: Add the port to `Ports.kt`**

In `Ports.kt`, add the import near the top (after `import com.ironclinicgym.sift.core.board.BoardSettings`):

```kotlin
import com.ironclinicgym.sift.core.board.PrioritySettings
```

Then append the interface at the end of the file:

```kotlin
/** Global (not mapping-keyed) priority-timing settings. Backed by DataStore in the platform layer. */
interface PrioritySettingsStore {
    fun observe(): Flow<PrioritySettings>
    suspend fun load(): PrioritySettings
    suspend fun save(settings: PrioritySettings)
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :core:test --tests "*PrioritySettingsTest*"`
Expected: PASS. Also run `./gradlew :core:test` to confirm no regression in existing band tests.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/PrioritySettings.kt core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettingsEdits.kt core/src/main/kotlin/com/ironclinicgym/sift/core/domain/ports/Ports.kt core/src/test/kotlin/com/ironclinicgym/sift/core/board/PrioritySettingsTest.kt
git commit -m "feat(core): PrioritySettings group, resolver, scope fields, and port (T-008/T-009)"
```

---

### Task 3: Parameterize quick-date defaults (core, T-009)

**Goal:** Make `quickDateTomorrow`, `quickDateNextWeek`, and `quickDateNextMonth` accept the configured local time (and first day of week) instead of hardcoding 8am / Sunday, keeping default params so existing callers stay valid.

**Files:**
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/TimeFormat.kt` (lines 138-164)
- Test: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/QuickDateTest.kt`

**Acceptance Criteria:**
- [ ] `quickDateTomorrow(today, 9, 30)` returns tomorrow's date at 09:30.
- [ ] `quickDateNextWeek(today, firstDayOfWeek = 1, 8, 0)` returns the next Monday (strictly future) at 08:00; `firstDayOfWeek = 7` returns the next Sunday, matching prior behavior.
- [ ] `quickDateNextMonth(today, 7, 15)` returns the 1st of next month at 07:15.
- [ ] Called with no time args, all three still default to 08:00 (and next week to Sunday).

**Verify:** `./gradlew :core:test --tests "*QuickDateTest*"` -> all pass.

**Steps:**

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/com/ironclinicgym/sift/core/board/QuickDateTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "*QuickDateTest*"`
Expected: FAIL, too many arguments for the current single-arg functions.

- [ ] **Step 3: Replace the three functions in `TimeFormat.kt` (lines 138-164)**

Replace `quickDateTomorrow`, `quickDateNextWeek`, and `quickDateNextMonth` with:

```kotlin
/** Returns (dateIso, time) for the "Tomorrow" chip at the configured local time. */
fun quickDateTomorrow(todayIso: String, hour: Int = 8, minute: Int = 0): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (hour to minute)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (hour to minute)
    val d = todayIso.substring(8, 10).toIntOrNull() ?: return todayIso to (hour to minute)
    return epochDaysToIso(daysSinceEpoch(y, m, d) + 1L) to (hour to minute)
}

/**
 * Returns (dateIso, time) for "Next week": the next occurrence of [firstDayOfWeek] strictly in the
 * future, at the configured local time. [firstDayOfWeek] is 1 = Monday .. 7 = Sunday (default Sunday).
 */
fun quickDateNextWeek(
    todayIso: String,
    firstDayOfWeek: Int = 7,
    hour: Int = 8,
    minute: Int = 0,
): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (hour to minute)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (hour to minute)
    val d = todayIso.substring(8, 10).toIntOrNull() ?: return todayIso to (hour to minute)
    val dow = dayOfWeek(y, m, d)                 // 0 = Monday .. 6 = Sunday
    val target = (firstDayOfWeek.coerceIn(1, 7) - 1)  // 0 = Monday .. 6 = Sunday
    var delta = (target - dow + 7) % 7
    if (delta == 0) delta = 7                    // always strictly in the future
    return epochDaysToIso(daysSinceEpoch(y, m, d) + delta.toLong()) to (hour to minute)
}

/** Returns (dateIso, time) for "Next month": 1st of next calendar month at the configured local time. */
fun quickDateNextMonth(todayIso: String, hour: Int = 8, minute: Int = 0): Pair<String, Pair<Int, Int>> {
    val y = todayIso.substring(0, 4).toIntOrNull() ?: return todayIso to (hour to minute)
    val m = todayIso.substring(5, 7).toIntOrNull() ?: return todayIso to (hour to minute)
    val nextY = if (m == 12) y + 1 else y
    val nextM = if (m == 12) 1 else m + 1
    return "%04d-%02d-01".format(nextY, nextM) to (hour to minute)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "*QuickDateTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/TimeFormat.kt core/src/test/kotlin/com/ironclinicgym/sift/core/board/QuickDateTest.kt
git commit -m "feat(core): parameterize quick-date defaults by configured time and first day (T-009)"
```

---

### Task 4: Global PrioritySettings DataStore and DI (app)

**Goal:** A global (not mapping-keyed) DataStore implementing `PrioritySettingsStore`, wired into `AppContainer` and cleared on reset.

**Files:**
- Create: `app/src/main/java/com/ironclinicgym/sift/data/local/PrioritySettingsDataStore.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/di/AppContainer.kt`

**Acceptance Criteria:**
- [ ] `PrioritySettingsDataStore` observes/loads/saves a single global JSON blob, defaulting to `PrioritySettings.DEFAULT` when unset.
- [ ] `AppContainer` exposes `val prioritySettingsStore: PrioritySettingsStore`.
- [ ] `resetToNewUserState()` clears the priority settings store.

**Verify:** `./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL.

**Steps:**

- [ ] **Step 1: Create `PrioritySettingsDataStore.kt`**

```kotlin
package com.ironclinicgym.sift.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// Global priority-timing settings: a single JSON blob, not keyed by mapping. Non-sensitive.
private val Context.priorityDataStore by preferencesDataStore(name = "sift_priority_settings")

/** DataStore-backed [PrioritySettingsStore]. */
class PrioritySettingsDataStore(context: Context) : PrioritySettingsStore {
    private val dataStore = context.priorityDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("priority_settings")

    override fun observe(): Flow<PrioritySettings> =
        dataStore.data.map { prefs -> prefs[key]?.let(::decode) ?: PrioritySettings.DEFAULT }

    override suspend fun load(): PrioritySettings =
        dataStore.data.map { it[key] }.first()?.let(::decode) ?: PrioritySettings.DEFAULT

    override suspend fun save(settings: PrioritySettings) {
        dataStore.edit { it[key] = json.encodeToString(PrioritySettings.serializer(), settings) }
    }

    /** Debug reset: drop the global priority settings. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun decode(raw: String): PrioritySettings? =
        runCatching { json.decodeFromString(PrioritySettings.serializer(), raw).normalized() }.getOrNull()
}
```

(Import `com.ironclinicgym.sift.core.board.normalized` alongside `PrioritySettings`.)

- [ ] **Step 2: Wire into `AppContainer.kt`**

Add the import near the other port imports (after `import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore`):

```kotlin
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
```

After line 99 (`private val boardSettingsDataStore = BoardSettingsDataStore(appContext)`) add:

```kotlin
    private val prioritySettingsDataStore = PrioritySettingsDataStore(appContext)
```

After line 102 (`val boardSettingsStore: BoardSettingsStore = boardSettingsDataStore`) add:

```kotlin
    val prioritySettingsStore: PrioritySettingsStore = prioritySettingsDataStore
```

In `resetToNewUserState()`, after `boardSettingsDataStore.clearAll()` (line 167) add:

```kotlin
        prioritySettingsDataStore.clearAll()
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/data/local/PrioritySettingsDataStore.kt app/src/main/java/com/ironclinicgym/sift/di/AppContainer.kt
git commit -m "feat(app): global PrioritySettings DataStore wired into AppContainer (T-008/T-009)"
```

---

### Task 5: CustomizeViewModel scope-aware priority edits (app)

**Goal:** Give `CustomizeViewModel` the global store, an effective-settings flow, a scope toggle, and edit methods that route to the active scope (global vs per-database override).

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/CustomizeViewModel.kt`

**Acceptance Criteria:**
- [ ] Constructor takes `prioritySettingsStore: PrioritySettingsStore`; the `AppContainer` secondary constructor passes `container.prioritySettingsStore`.
- [ ] `globalPrioritySettings` and `effectivePriority` StateFlows are exposed.
- [ ] `setUseGlobalPrioritySettings`, `setSoonMaxDays`, `setLaterMaxDays`, `setTomorrowTime`, `setNextWeekTime`, `setNextMonthTime`, `setFirstDayOfWeek` write to global when the database follows global, else to its override.

**Verify:** `./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL.

**Steps:**

- [ ] **Step 1: Add imports**

In `CustomizeViewModel.kt`, add to the import block:

```kotlin
import com.ironclinicgym.sift.core.board.PriorityEdit
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.board.normalized
import com.ironclinicgym.sift.core.board.resolvePrioritySettings
import com.ironclinicgym.sift.core.board.routePriorityEdit
import com.ironclinicgym.sift.core.board.setFirstDayOfWeek
import com.ironclinicgym.sift.core.board.setLaterMaxDays
import com.ironclinicgym.sift.core.board.setNextMonthTime
import com.ironclinicgym.sift.core.board.setNextWeekTime
import com.ironclinicgym.sift.core.board.setSoonMaxDays
import com.ironclinicgym.sift.core.board.setTomorrowTime
import com.ironclinicgym.sift.core.board.setUseGlobalPrioritySettings
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

- [ ] **Step 2: Change the constructor**

Replace the class header + secondary constructor (lines 48-50) with:

```kotlin
class CustomizeViewModel(
    private val repository: SiftRepository,
    private val store: BoardSettingsStore,
    private val prioritySettingsStore: PrioritySettingsStore,
) : ViewModel() {

    constructor(container: AppContainer) : this(container.repository, container.boardSettingsStore, container.prioritySettingsStore)
```

- [ ] **Step 3: Add the flows and edit methods**

After the `unmapped` StateFlow (line 65), add:

```kotlin
    // Serializes scope toggles and priority-timing edits so a toggle and the edits around it apply
    // in order, and concurrent edits never overwrite one another (single ViewModel instance).
    private val priorityEditMutex = Mutex()

    val globalPrioritySettings: StateFlow<PrioritySettings> = prioritySettingsStore.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrioritySettings.DEFAULT)

    /** The priority-timing settings this database actually uses right now (global or its override). */
    val effectivePriority: StateFlow<PrioritySettings?> = combine(settings, globalPrioritySettings) { s, g ->
        if (s == null) null else resolvePrioritySettings(g, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUseGlobalPrioritySettings(useGlobal: Boolean) {
        val current = settings.value ?: return
        viewModelScope.launch {
            priorityEditMutex.withLock {
                // Read BOTH authoritative values from their stores inside the lock: the StateFlows
                // (settings, globalPrioritySettings) lag DataStore writes, so seeding an override
                // from the flow could copy a stale global.
                val board = store.load(current.mappingId) ?: current
                val global = prioritySettingsStore.load().normalized()
                store.save(board.setUseGlobalPrioritySettings(useGlobal, global))
            }
        }
    }

    fun setSoonMaxDays(days: Int) = editPriority { it.setSoonMaxDays(days) }
    fun setLaterMaxDays(days: Int) = editPriority { it.setLaterMaxDays(days) }
    fun setTomorrowTime(hour: Int, minute: Int) = editPriority { it.setTomorrowTime(hour, minute) }
    fun setNextWeekTime(hour: Int, minute: Int) = editPriority { it.setNextWeekTime(hour, minute) }
    fun setNextMonthTime(hour: Int, minute: Int) = editPriority { it.setNextMonthTime(hour, minute) }
    fun setFirstDayOfWeek(day: Int) = editPriority { it.setFirstDayOfWeek(day) }

    /**
     * Route a priority-timing edit to the active scope through the pure [routePriorityEdit], under
     * the mutex. Reads BOTH authoritative values (board and global) from their stores inside the
     * lock rather than from the lagging StateFlows, so back-to-back edits never transform a stale
     * snapshot, and serializes concurrent edits so neither is lost.
     */
    private fun editPriority(transform: (PrioritySettings) -> PrioritySettings) {
        val current = settings.value ?: return
        viewModelScope.launch {
            priorityEditMutex.withLock {
                val board = store.load(current.mappingId) ?: current
                val global = prioritySettingsStore.load().normalized()
                when (val edit = routePriorityEdit(board, global, transform)) {
                    is PriorityEdit.SaveGlobal -> prioritySettingsStore.save(edit.global)
                    is PriorityEdit.SaveBoard -> store.save(edit.board)
                }
            }
        }
    }
```

> **Ordering (required):** declare these members after the existing `settings` StateFlow (line 56) so the initializers resolve in order: `settings` -> `globalPrioritySettings` -> `effectivePriority` (which references both). The insertion point above (after `unmapped`, line 65) satisfies this. The pure routing decision is unit-tested in Task 2 (`routePriorityEdit`); the mutex guarantees ordering. Cross-ViewModel-instance races (two live settings screens) remain theoretically possible but are not a real scenario for a single settings surface, and a full atomic-store `update(transform)` refactor is deferred (tracked outside this batch).

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/CustomizeViewModel.kt
git commit -m "feat(app): scope-aware priority-timing edits in CustomizeViewModel (T-008/T-009)"
```

---

### Task 6: BoardViewModel exposes effective PrioritySettings (app)

**Goal:** Expose the effective `PrioritySettings` on `BoardViewModel` for the add-task sheet.

**Why no policy rebuild (verified):** `resolveBand` is only called at task creation (`AddTaskSheetV2.resolvedPriority`, wired in Task 7). The only `DateBandConfig` consumer inside `TwoAxisPolicy` is `evaluateDateChange`, which has NO call site anywhere in the app today. So rebuilding `twoAxisPolicy` from the config would be dead churn and add a needless mutable-state race. We therefore leave the injected `TwoAxisPolicy()` untouched and only expose the flow. If `evaluateDateChange` is wired later, build a policy from `effectivePrioritySettings.value.dateBandConfig()` at that call site.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt` (constructor lines 79-103; add flow after the `settings` StateFlow near line 165)

**Acceptance Criteria:**
- [ ] Constructor takes `prioritySettingsStore: PrioritySettingsStore`, passed from the `AppContainer` constructor.
- [ ] `effectivePrioritySettings: StateFlow<PrioritySettings>` is exposed for the add-task sheet.
- [ ] The injected `twoAxisPolicy` parameter is unchanged (no rename, no mutable rebuild).

**Verify:** `./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL.

**Steps:**

- [ ] **Step 1: Add imports**

Add to the import block of `BoardViewModel.kt`:

```kotlin
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.board.resolvePrioritySettings
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
```

- [ ] **Step 2: Add the store parameter (leave `twoAxisPolicy` as-is)**

In the primary constructor (lines 79-90), add `prioritySettingsStore` as the last parameter. `twoAxisPolicy` stays `private val twoAxisPolicy: TwoAxisPolicy,` unchanged. The header becomes:

```kotlin
class BoardViewModel(
    private val repository: SiftRepository,
    private val settingsStore: BoardSettingsStore,
    private val undoManager: UndoManager,
    private val recurrenceSetup: RecurrenceSetupService,
    private val appPreferences: AppPreferencesDataStore,
    private val twoAxisPolicy: TwoAxisPolicy,
    private val localStateStore: TaskLocalStateStore,
    private val notificationStore: NotificationStore,
    private val actionHistoryStore: ActionHistoryStore,
    private val labelStore: LabelStore,
    private val prioritySettingsStore: PrioritySettingsStore,
) : ViewModel() {
```

In the secondary constructor (lines 92-103), add `container.prioritySettingsStore` as the last argument:

```kotlin
    constructor(container: AppContainer) : this(
        container.repository,
        container.boardSettingsStore,
        container.undoManager,
        container.recurrenceSetup,
        container.appPreferences,
        TwoAxisPolicy(),
        container.localStateStore,
        container.notificationStore,
        container.actionHistoryStore,
        container.labelStore,
        container.prioritySettingsStore,
    )
```

- [ ] **Step 3: Add the effective-settings flow (AFTER the `settings` StateFlow)**

Find the `settings` StateFlow (declared around line 159-166). Immediately after it (so `settings` is initialized before this references it), add:

```kotlin
    /** The priority-timing settings the active board uses (global or its per-database override). */
    val effectivePrioritySettings: StateFlow<PrioritySettings> =
        combine(settings, prioritySettingsStore.observe()) { s, g ->
            if (s == null) g else resolvePrioritySettings(g, s)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrioritySettings.DEFAULT)
```

(`combine`, `stateIn`, `SharingStarted`, `StateFlow` are already imported in this file. No `init` block or mutable policy is added.)

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt
git commit -m "feat(app): expose effective PrioritySettings flow on BoardViewModel (T-008)"
```

---

### Task 7: AddTaskSheetV2 uses effective config (app, T-008 + T-009)

**Goal:** The add-task sheet resolves the priority a dated task is assigned (its placement) and the quick-date chip times from the effective `PrioritySettings` instead of hardcoded defaults.

**Scope note (verified):** `resolveBand` at line 194 lives in `resolvedPriority()`, which is a local function inside the `AddTaskSheetV2` composable body (not a separate state holder), so a `val prioritySettings` collected in that body IS in scope there and in the quick-date chip handlers. `resolvedPriority()` runs at submit, so this drives where the dated task LANDS. There is no separate live date-picker preview to change here; if an in-picker preview is later added it should reuse the same effective config.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt` (collect the flow near line 167; band preview line 194; quick-date chips lines 587/593/599)

**Acceptance Criteria:**
- [ ] The sheet collects `viewModel.effectivePrioritySettings` via `collectAsStateWithLifecycle`.
- [ ] `resolveBand` at line 194 uses `prioritySettings.dateBandConfig()`.
- [ ] The Tomorrow / Next week / Next month chips pass the configured times (and first day of week) to the core functions.

**Verify:** `./gradlew :app:compileDebugKotlin` -> BUILD SUCCESSFUL.

**Steps:**

- [ ] **Step 1: Add imports and collect the flow**

Add to the import block:

```kotlin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
```

(If `collectAsStateWithLifecycle` is already imported, skip.) Inside `AddTaskSheetV2`, right after `val todayIso = LocalDate.now().toString()` (line 168), add:

```kotlin
    val prioritySettings by viewModel.effectivePrioritySettings.collectAsStateWithLifecycle()
```

- [ ] **Step 2: Use the config for date-based placement (line 194)**

Replace:

```kotlin
                val band = DateBandEngine.resolveBand(selectedDate, todayIso)
```

with:

```kotlin
                val band = DateBandEngine.resolveBand(selectedDate, todayIso, prioritySettings.dateBandConfig())
```

- [ ] **Step 3: Pass configured times to the quick-date chips (lines 587, 593, 599)**

Replace the three chip handlers:

```kotlin
                            val (tmDate, tmTime) = quickDateTomorrow(todayIso)
```
with:
```kotlin
                            val (tmDate, tmTime) = quickDateTomorrow(todayIso, prioritySettings.tomorrowHour, prioritySettings.tomorrowMinute)
```

```kotlin
                            val (nwDate, nwTime) = quickDateNextWeek(todayIso)
```
with:
```kotlin
                            val (nwDate, nwTime) = quickDateNextWeek(todayIso, prioritySettings.firstDayOfWeek, prioritySettings.nextWeekHour, prioritySettings.nextWeekMinute)
```

```kotlin
                            val (nmDate, nmTime) = quickDateNextMonth(todayIso)
```
with:
```kotlin
                            val (nmDate, nmTime) = quickDateNextMonth(todayIso, prioritySettings.nextMonthHour, prioritySettings.nextMonthMinute)
```

(The "Later today" chip at line 581 is intentionally unchanged: its +4h offset is out of scope.)

- [ ] **Step 4: Verify compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt
git commit -m "feat(app): add-task sheet reads bands and quick-date defaults from settings (T-008/T-009)"
```

---

### Task 8: Settings "Priority timing" section (app UI)

**Goal:** A Settings section exposing the scope toggle, the two band steppers, the three default-time rows, and the first-day-of-week selector.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt`

**Acceptance Criteria:**
- [ ] A "Priority timing" group renders a scope Switch ("Use across all databases"), two day steppers ("Soon: up to N days", "Later: up to M days"), three time rows (Tomorrow / Next week / Next month), and a first-day-of-week row.
- [ ] Editing any control calls the matching `customizeVm` method; the scope toggle calls `setUseGlobalPrioritySettings`.
- [ ] Time rows display in the user's clock format (respect `use24`) and open a time picker; the day row opens a 7-day chooser.
- [ ] No hardcoded colors (tokens only); no hyphens/em dashes in strings.

**Verify:** `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL. [manual] visual check on device.

**Steps:**

- [ ] **Step 1: Add imports**

Add to `SettingsScreen.kt`:

```kotlin
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.board.formatHourMinute
```

(With `Column` imported, use `Column { ... }` instead of the fully qualified name in `FirstDayOfWeekRow`.)

- [ ] **Step 2: Collect the effective settings**

After line 83 (`val use24 = boardSettings?.use24HourTime == true`) add:

```kotlin
    val priority by customizeVm.effectivePriority.collectAsStateWithLifecycle()
    val useGlobalPriority = boardSettings?.useGlobalPrioritySettings != false
```

- [ ] **Step 3: Insert the "Priority timing" group**

Immediately after the closing brace of the `SettingsGroup("Board") { ... }` block (line 130) add:

```kotlin
        priority?.let { p ->
            SettingsGroup("Priority timing") {
                SettingsRow(
                    title = "Use across all databases",
                    subtitle = if (useGlobalPriority) "One set of timing rules everywhere" else "This database has its own timing",
                    icon = "tune",
                    showDivider = true,
                    onClick = null,
                    trailing = {
                        Switch(checked = useGlobalPriority, onCheckedChange = { customizeVm.setUseGlobalPrioritySettings(it) })
                    },
                )
                SettingsNumberRow(
                    title = "Soon: up to N days",
                    subtitle = "Dates within this many days count as Soon",
                    icon = "schedule",
                    value = p.soonMaxDays,
                    showDivider = true,
                    onValueChange = { customizeVm.setSoonMaxDays(it) },
                )
                SettingsNumberRow(
                    title = "Later: up to N days",
                    subtitle = "Dates within this many days count as Later; beyond is One day",
                    icon = "schedule",
                    value = p.laterMaxDays,
                    showDivider = true,
                    onValueChange = { customizeVm.setLaterMaxDays(it) },
                )
                TimeSettingRow("Tomorrow time", p.tomorrowHour, p.tomorrowMinute, use24, showDivider = true) { h, m ->
                    customizeVm.setTomorrowTime(h, m)
                }
                TimeSettingRow("Next week time", p.nextWeekHour, p.nextWeekMinute, use24, showDivider = true) { h, m ->
                    customizeVm.setNextWeekTime(h, m)
                }
                TimeSettingRow("Next month time", p.nextMonthHour, p.nextMonthMinute, use24, showDivider = true) { h, m ->
                    customizeVm.setNextMonthTime(h, m)
                }
                FirstDayOfWeekRow(p.firstDayOfWeek, showDivider = false) { customizeVm.setFirstDayOfWeek(it) }
            }
        }
```

- [ ] **Step 4: Add the two helper composables**

At the end of `SettingsScreen.kt` (after `SettingsNumberRow`), add:

```kotlin
private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@Composable
private fun TimeSettingRow(
    title: String,
    hour: Int,
    minute: Int,
    use24: Boolean,
    showDivider: Boolean,
    onTimeSet: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    SettingsRow(
        title = title,
        subtitle = formatHourMinute(hour, minute, use24),
        icon = "schedule",
        showDivider = showDivider,
        onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onTimeSet(h, m) },
                hour,
                minute,
                use24,
            ).show()
        },
    )
}

@Composable
private fun FirstDayOfWeekRow(firstDay: Int, showDivider: Boolean, onDaySet: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    SettingsRow(
        title = "First day of week",
        subtitle = WEEKDAY_NAMES[(firstDay - 1).coerceIn(0, 6)],
        icon = "calendar_today",
        showDivider = showDivider,
        onClick = { show = true },
    )
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("First day of week") },
            text = {
                androidx.compose.foundation.layout.Column {
                    WEEKDAY_NAMES.forEachIndexed { index, name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDaySet(index + 1); show = false }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { show = false }) { Text("Close") } },
        )
    }
}
```

- [ ] **Step 5: Verify compile and build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt
git commit -m "feat(app): Priority timing settings section with scope toggle (T-008/T-009)"
```

---

### Task 9: Blocked toggle in the task detail sheet (app, T-006)

**Goal:** A blocked toggle in the task detail drawer that calls the existing `toggleBlocked`, with optimistic in-place state.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt` (add idempotent `setBlocked`)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/TaskDetailSheet.kt` (signature ~line 59; secondary-action area ~line 272)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt` (call site line 255-279)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt` (call site line 241-265)

**Acceptance Criteria:**
- [ ] `BoardViewModel.setBlocked(pageId, blocked)` persists the DESIRED value idempotently (not derived from a possibly stale projection).
- [ ] `upsertLocalField` is serialized behind `localStateMutex` so concurrent/ rapid local-state writes to a page never overwrite one another; for the blocked field, the last tap wins.
- [ ] `TaskDetailSheet` takes `onSetBlocked: (Boolean) -> Unit`; both call sites pass `{ blocked -> viewModel.setBlocked(item.task.pageId, blocked) }`.
- [ ] A blocked control in the secondary actions is labelled "Mark blocked" / "Blocked" and drives a `remember`-backed optimistic local state; the FINAL persisted value matches the last tap.
- [ ] Uses an existing subsetted glyph (`event_busy`); no new codepoint added.

**Verify:** `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL. [ui-auto] where instrumentation exists; [manual] on device.

**Steps:**

- [ ] **Step 1: Add idempotent `setBlocked` and serialize local-state writes in `BoardViewModel`**

In `BoardViewModel.kt`, next to the existing `toggleBlocked` (line 932), add:

```kotlin
    /** Idempotent: persist the desired blocked value (safe under rapid taps). */
    fun setBlocked(pageId: String, blocked: Boolean) {
        viewModelScope.launch {
            upsertLocalField(pageId) { it.copy(isBlocked = blocked) }
        }
    }
```

Then serialize `upsertLocalField` so rapid same-field taps (and concurrent pin / protected / brain-dump / blocked writes to the same page) apply in order and never clobber one another. Add a mutex field and wrap the existing body (line ~945):

```kotlin
    // Orders every TaskLocalState read-modify-upsert so concurrent flag writes to the same page
    // never overwrite one another; for a given field, the last intent wins.
    private val localStateMutex = Mutex()

    private suspend fun upsertLocalField(pageId: String, transform: (TaskLocalState) -> TaskLocalState) {
        localStateMutex.withLock {
            val mappingId = repository.mappingSet.value.active?.id ?: return
            val current = localStateStore.get(pageId) ?: TaskLocalState(pageId = pageId, mappingId = mappingId)
            localStateStore.upsert(transform(current).copy(lastModifiedAt = System.currentTimeMillis()))
        }
    }
```

Add imports if missing: `import kotlinx.coroutines.sync.Mutex` and `import kotlinx.coroutines.sync.withLock`. (The `return` inside the inline `withLock` is a non-local return from `upsertLocalField`; the lock is released either way. `toggleBlocked` can stay for other callers; the UI uses `setBlocked`.)

- [ ] **Step 2: Add the parameter**

In `TaskDetailSheet.kt`, add to the parameter list (after `onChangePriority: (PriorityView) -> Unit,` line ~57):

```kotlin
    onSetBlocked: (Boolean) -> Unit,
```

- [ ] **Step 3: Add optimistic local state**

After `var localPinLevel by remember(item.task.pageId) { mutableIntStateOf(item.task.pinLevel) }` (line 86) add:

```kotlin
    var localBlocked by remember(item.task.pageId) { mutableStateOf(item.task.isBlocked) }
```

- [ ] **Step 4: Add the blocked control to the secondary row**

In the "Edit + Remove secondary row" (lines 266-272), add a third button after Remove so the row reads:

```kotlin
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SecondaryActionButton("edit", "Edit", onEdit, Modifier.weight(1f))
                SecondaryActionButton("delete", "Remove", onRemove, Modifier.weight(1f))
                SecondaryActionButton(
                    "event_busy",
                    if (localBlocked) "Blocked" else "Mark blocked",
                    { val next = !localBlocked; localBlocked = next; onSetBlocked(next) },
                    Modifier.weight(1f),
                )
            }
```

- [ ] **Step 5: Wire the two call sites**

In `BoardScreen.kt`, in the `TaskDetailSheet(...)` call, after `onChangePriority = { ... },` (line 276) add:

```kotlin
            onSetBlocked = { blocked -> viewModel.setBlocked(item.task.pageId, blocked) },
```

In `FocusedBucketScreen.kt`, in its `TaskDetailSheet(...)` call, after `onChangePriority = { ... },` (line 262) add:

```kotlin
                onSetBlocked = { blocked -> viewModel.setBlocked(item.task.pageId, blocked) },
```

- [ ] **Step 6: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt app/src/main/java/com/ironclinicgym/sift/ui/board/TaskDetailSheet.kt app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt
git commit -m "feat(app): idempotent setBlocked + blocked toggle in task detail sheet (T-006)"
```

---

### Task 10: Blocked indicator on the board card (app, T-006)

**Goal:** A "BLOCKED" tag on the board task row when a task is blocked, reusing the existing `OverdueTag` text-tag pattern (no new glyph).

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardComponents.kt` (`OverdueTag` ~line 82; `TaskRow` trailing ~lines 130-134)

**Acceptance Criteria:**
- [ ] A `BlockedTag()` composable mirrors `OverdueTag`'s shape/typography, using neutral tokens.
- [ ] `TaskRow` shows the blocked tag when `item.task.isBlocked`, alongside (not replacing) the overdue/time trailing.
- [ ] No hardcoded colors; no new codepoint.

**Verify:** `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL. [manual] visual check.

**Steps:**

- [ ] **Step 1: Add the `BlockedTag` composable**

After `OverdueTag()` (line 93) add:

```kotlin
@Composable
private fun BlockedTag() {
    val tokens = SiftTheme.tokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tokens.neutrals.border.toColor())
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("BLOCKED", color = tokens.neutrals.textSecondary.toColor(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
```

- [ ] **Step 2: Render it in `TaskRow`**

Replace the trailing `when` block (lines 130-134) with a Row that can show the blocked tag plus the existing trailing:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.task.isBlocked) BlockedTag()
            when {
                item.isOverdue -> OverdueTag()
                item.timeLabel.isNotEmpty() && !expanded ->
                    Text(item.timeLabel, color = SiftTheme.tokens.neutrals.textTertiary.toColor(), fontSize = 12.sp)
            }
        }
```

(`Row`, `Alignment`, `Arrangement`, `FontWeight`, `RoundedCornerShape`, `Box`, `background`, `clip`, `padding` are already imported in this file; confirm `FontWeight` import exists, add `import androidx.compose.ui.text.font.FontWeight` if the compiler flags it.)

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/BoardComponents.kt
git commit -m "feat(app): BLOCKED tag on board task rows (T-006)"
```

---

### Task 11: UAT round 3 script and owned-test run (docs, T-005)

**USER-ORDERED GATE — NON-SKIPPABLE.** This task was requested by the user in the current conversation. It MUST NOT be closed by walking around it, by declaring it "verified inline", or by substituting a cheaper check. Close only after every item in `acceptanceCriteria` has been re-validated independently, with output captured.

**Goal:** Assemble the consolidated on-device UAT round 3 script for BJ and run every Claude-owned test with captured pass/fail counts, so Phase 3.5 can be signed off. The script also flags the two known Phase 3.5 spec gaps found during review (ISS-001 self-managing ladder, ISS-002 in-picker band preview) for explicit device verification, so the sign-off is honest rather than silently over-claiming.

**Files:**
- Create: `docs/PHASE_3_5_UAT_round3.md`

**Acceptance Criteria:**
- [ ] `docs/PHASE_3_5_UAT_round3.md` covers the full Phase 3.5 surface: spec section 16 acceptance criteria, section 19 device checklist, the Round-1 tagged [manual] items, and the three new features (blocked flag, configurable date bands, configurable quick-date defaults incl. the global/per-database scope toggle).
- [ ] The script has an explicit "known failing spec criteria" section that lists ISS-001 (self-managing dated ladder / auto-climb) and ISS-002 (in-picker priority preview) with issue links, and states that full Phase 3.5 sign-off is blocked until both are closed.
- [ ] The script is organized by area with checkboxes and marks each item [logic] / [ui-auto] / [manual].
- [ ] `./gradlew :core:test` passes; capture the summary line.
- [ ] `./gradlew :app:testDebugUnitTest` passes (includes `BoardIconsFontTest`); capture the summary line.
- [ ] `./gradlew :app:assembleDebug` succeeds; capture BUILD SUCCESSFUL.
- [ ] A "Claude-owned test results" section records the three captured summaries; sign-off is explicitly gated on BJ's on-device pass.

**Verify:** `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug` -> all BUILD SUCCESSFUL; the three summaries are pasted into the script's results section.

**Steps:**

- [ ] **Step 1: Run the owned tests and capture output**

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. Record the per-module result lines.

- [ ] **Step 2: Write `docs/PHASE_3_5_UAT_round3.md`**

Assemble the script with these sections (each item a `- [ ]` checkbox, tagged):
1. **Add-task flow** (spec 16 + round-1 D-items): three-step flow, no priority picker; date auto-assign with live preview; rough ranges; brain dump; sheet stays open; self-quieting chip status (note T-007 is out of this batch).
2. **Drag / redirect** (spec 16): dated redirect prompt; undated free drag.
3. **Pinning + Protected + safety catch + Signal Inflation** (spec 16 + round-1 B-items).
4. **Blocked flag (NEW, T-006):** toggle in task detail marks blocked; BLOCKED tag shows on the board row; blocked does not carry to a recurring task's next occurrence.
5. **Date bands (NEW, T-008):** after changing Settings > Priority timing > "Soon/Later up to N days", a NEWLY added dated task lands in the band dictated by the new boundaries (existing dated tasks are not re-placed; that is pre-existing ladder behavior, see Notes). Global vs per-database scope toggle behaves: an edit under "Use across all databases" applies everywhere; flip it off and confirm this database diverges while others stay put.
6. **Quick-date defaults (NEW, T-009):** Tomorrow/Next week/Next month land at the configured local times; changing "First day of week" changes the Next-week target day; "Later today" still +4h.
7. **Known failing spec criteria (documented, not device-tested as if passing):** the T-008 review confirmed by code inspection that two Phase 3.5 spec criteria are unimplemented, so list them here as KNOWN FAILING with issue links rather than pass/fail device steps: (a) a dated task's priority is self-managing / auto-climbs as its date nears (ISS-001); (b) the date picker shows a live priority-band preview during selection (ISS-002). **Full Phase 3.5 sign-off is BLOCKED until ISS-001 and ISS-002 are closed.** The device pass below covers only the surface that can actually pass.
8. **Round-1 [manual] regressions:** status bar text in light mode, bottom tabs above nav bar, top notification bar auto-dismiss/swipe, tab icons distinct, Notion external-link icon.
9. **Claude-owned test results:** paste the three captured summary lines from Step 1; state that sign-off is pending BJ's on-device pass, and list any ISS-001 / ISS-002 findings surfaced above.

- [ ] **Step 3: Commit**

```bash
git add docs/PHASE_3_5_UAT_round3.md
git commit -m "docs(phase35): UAT round 3 script and owned-test results (T-005)"
```

- [ ] **Step 4: Hand to BJ**

Report the captured test summaries and tell BJ the on-device UAT script is ready; Phase 3.5 sign-off (marking T-005 complete and p35 done) waits on the device pass.

---

## Notes / Out of scope

- **T-007** (self-quieting Added-to-priority chip) is the fourth Phase 3.5 gap ticket, deliberately not in this batch.
- **"Later today" offset** stays hardcoded at +4h (not in the Round-2 settings list).
- **Board swipe for blocked** is backlog (both swipe directions are committed to complete/snooze).
- **Scope of T-008 wiring (verified during review).** `resolveBand` is called in exactly one app location, `AddTaskSheetV2.resolvedPriority` (task creation), and `TwoAxisPolicy.evaluateDateChange` (the only `DateBandConfig` consumer) has no call site anywhere today. So feeding the configured bands into task creation is the complete surface for making the bands "used." The following are deliberately NOT in scope, being pre-existing product gaps rather than T-008 wiring, and are tracked as filed issues:
  - **ISS-001** (high): dated priorities are not self-managing across all date paths (no re-derivation on snooze/brain-dump/remove-date, no day-rollover or boundary-change reprojection, `BoardProjection` groups by stored priority).
  - **ISS-002** (medium): the date picker lacks the live priority-band preview during selection.
  - **ISS-003** (low): `TaskLocalState` flag writes (pin/protected/brain-dump/blocked) can drop concurrent field updates. T-006 makes `setBlocked` idempotent for the value-inversion sub-case; the broader per-page serialization is tracked here.
  - Codex flagged these as reasons a blanket sign-off would over-claim; they are addressed by filing the issues AND making Task 11's UAT verify ISS-001/ISS-002 on device, without expanding the three scoped gap tickets.
- **New Material Symbols glyph** avoided on purpose (subset font, no in-repo regen tooling). A dedicated "block"/"front_hand" glyph can replace `event_busy` and the text tag later, once a subset regen is run.
