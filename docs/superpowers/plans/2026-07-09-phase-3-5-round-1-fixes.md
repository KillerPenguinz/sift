# Phase 3.5 Round 1 UAT Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 9 UAT items (B3, B4, B5, B7, A2, A6, safety catch, Signal Inflation, one-day landmarks) from the Phase 3.5 Round 1 review.

**Architecture:** Two-module Kotlin app — `:core` (pure Kotlin, KMP-extractable, no Android imports) holds all business logic and domain models; `:app` (Compose/Android) holds UI, ViewModels, Room, and DataStore. The Phase 3.5 Two Axis Model is already fully implemented; this batch fixes UX gaps identified in UAT. All tests run with `./gradlew :core:test` (unit) or `./gradlew :app:testDebugUnitTest` (app unit tests with Robolectric if needed).

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 5→6, DataStore, ModalBottomSheet, AnimatedVisibility, StateFlow/SharedFlow.

**User decisions (already made):**
- "Group 1: Add-task drawer (B3, B4, B5)" — approved.
- "Group 2: Board and focused view (one-day landmarks, safety catch, protected review)" — approved.
- "Group 3: Notification infrastructure (Signal Inflation, A6 SnackbarHost removal)" — approved.
- "Group 4: Task detail and move (B7, A2)" — approved.
- Minimized drawer state uses a separate bottom bar composable, NOT partial-expansion of the sheet.
- Signal Inflation dismissal tracking uses DataStore (not a separate Room table); spec says "Room" but DataStore is the established pattern for app-operational flags in this codebase.

---

## File Map

**New files to create:**
- `core/src/main/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluator.kt` — pure function; evaluates which tasks need a safety-catch prompt
- `app/src/main/java/com/ironclinicgym/sift/ui/board/ProtectedReviewScreen.kt` — transient overlay screen for protected tasks
- `app/src/main/java/com/ironclinicgym/sift/ui/board/PriorityPickerSheet.kt` — lightweight sheet for undated "Change priority" action
- `app/src/main/java/com/ironclinicgym/sift/ui/board/RecurrenceBuilderSheet.kt` — B5 recurrence sub-sheet with RRULE builder
- `core/src/test/kotlin/com/ironclinicgym/sift/core/board/TimeFormatTest.kt` — tests for ordinal date and quick-date helpers
- `core/src/test/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluatorTest.kt` — tests for safety catch evaluation logic
- `core/src/test/kotlin/com/ironclinicgym/sift/core/board/SignalInflationTest.kt` — tests for inflation check logic

**Files to modify:**
- `core/src/main/kotlin/com/ironclinicgym/sift/core/board/TimeFormat.kt` — add `formatOrdinalDate()`, `priorityLabelForTask()`, quick-date helpers
- `core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt` — add `oneDayLandmarksEnabled`, `signalInflationEnabled`, `asapInflationThreshold`, `protectedInflationPercent`
- `core/src/main/kotlin/com/ironclinicgym/sift/core/board/SignalInflation.kt` — change default `asapThreshold` 7 → 5
- `core/src/main/kotlin/com/ironclinicgym/sift/core/domain/ports/Ports.kt` — add `safetyCatchFiredBand: String? = null` to `TaskLocalState`
- `app/src/main/java/com/ironclinicgym/sift/data/local/TaskLocalStateRoom.kt` — add `safetyCatchFiredBand` column + update mapper
- `app/src/main/java/com/ironclinicgym/sift/data/local/TaskCacheRoom.kt` — MIGRATION_5_6 + bump DB version to 6
- `app/src/main/java/com/ironclinicgym/sift/data/local/AppPreferencesDataStore.kt` — add inflation dismissal preference keys
- `app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt` — B3 two-state + B4 date/time display + chip time defaults + chip collapse animation; add `onSaved` callback; extend `DraftState` with `dateIso`
- `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt` — extend `DraftState`, add `postNotification()`, `activeNotification`, safety catch evaluation on refresh, inflation dismissal logic, `showProtectedReview`, `unprotectTask()`, `closeProtectedReview()`, `openRedirectPromptForTask()`
- `app/src/main/java/com/ironclinicgym/sift/ui/board/TaskDetailSheet.kt` — B7 priority label, A2 "Change date/Change priority" button, new callbacks
- `app/src/main/java/com/ironclinicgym/sift/ui/board/SafetyCatchDialog.kt` — new copy + "Coming soon" badge on second button
- `app/src/main/java/com/ironclinicgym/sift/ui/board/TopNotificationBar.kt` — add `InflationNudge` persistent variant
- `app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt` — landmark sub-headers; remove SnackbarHost
- `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt` — remove SnackbarHost; wire `activeNotification` to TopNotificationBar; render inflation nudge; render minimized capture bar
- `app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt` — Signal Inflation settings section; one-day landmark toggles
- Find the composable that hosts `showAddTask` state (search `grep -rn "showAddTask" app/`) — add `showMinimizedBar` state, `MinimizedCaptureBar` render, `onSaved` wiring

---

## Task 1: Ordinal date formatting and quick-date pure functions

**Goal:** Add `formatOrdinalDate()`, `priorityLabelForTask()`, and quick-date time helpers to `:core`'s `TimeFormat.kt` so they can be unit-tested independent of Android.

**Files:**
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/TimeFormat.kt`
- Create: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/TimeFormatTest.kt`

**Acceptance Criteria:**
- [ ] `formatOrdinalDate("2026-07-08")` returns `"July 8th, 2026"`
- [ ] `formatOrdinalDate("2026-11-11")` returns `"November 11th, 2026"` (11th, not 11st)
- [ ] `formatOrdinalDate("2026-01-21")` returns `"January 21st, 2026"`
- [ ] `priorityLabelForTask(datedTask, todayIso = task.due.take(10), ...)` returns `"Due today"`
- [ ] `priorityLabelForTask(datedTask, todayIso = "other-date", ...)` returns `"Due July 8th, 2026"`
- [ ] `priorityLabelForTask(undatedTask, ..., priorityDisplayName = "ASAP")` returns `"ASAP"` (no prefix)
- [ ] All quick-date helpers return correct date strings and time pairs
- [ ] All tests pass: `./gradlew :core:test`

**Verify:** `./gradlew :core:test --tests "*.TimeFormatTest"` → BUILD SUCCESSFUL, 14+ tests passed

**Steps:**

- [ ] **Step 1: Read `TimeFormat.kt` to understand the current signature**

```bash
find . -name "TimeFormat.kt" -path "*/core/*"
```

Read the file. The existing functions are `humanDate(dateIso, todayIso)` and `formatHourMinute(hour, minute, use24Hour)`. Add the new functions below the existing ones.

- [ ] **Step 2: Add ordinal helpers and `formatOrdinalDate()` to `TimeFormat.kt`**

```kotlin
private val LONG_MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun ordinalSuffix(day: Int): String {
    val mod100 = day % 100
    if (mod100 in 11..13) return "th"
    return when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

/**
 * Formats an ISO date string as "July 8th, 2026". Returns the raw string on parse failure.
 */
fun formatOrdinalDate(isoDate: String): String {
    val year  = isoDate.substring(0, 4).toIntOrNull() ?: return isoDate
    val month = isoDate.substring(5, 7).toIntOrNull() ?: return isoDate
    val day   = isoDate.substring(8, 10).toIntOrNull() ?: return isoDate
    return "${LONG_MONTHS[month - 1]} $day${ordinalSuffix(day)}, $year"
}
```

- [ ] **Step 3: Add `priorityLabelForTask()` to `TimeFormat.kt`**

This function encapsulates B7's label rule so it can be unit-tested.

```kotlin
/**
 * Returns the priority context label for display in the task detail sheet.
 * Dated tasks: "Due today" or "Due July 8th, 2026". Undated: the display name as-is.
 */
fun priorityLabelForTask(
    isDated: Boolean,
    dueIso: String?,
    todayIso: String,
    priorityDisplayName: String,
): String {
    if (!isDated || dueIso.isNullOrBlank()) return priorityDisplayName
    val dueDate = dueIso.take(10)
    return if (dueDate == todayIso) "Due today"
    else "Due ${formatOrdinalDate(dueDate)}"
}
```

- [ ] **Step 4: Add quick-date time helpers to `TimeFormat.kt`**

These are used by the add-task drawer chips; pure functions make them testable.

```kotlin
/** Returns (dateIso, Pair<hour, minute>) for the "Later today" chip: now + 4 hours. */
fun quickDateLaterToday(nowHour: Int, nowMinute: Int, todayIso: String): Pair<String, Pair<Int, Int>> {
    val totalMinutes = nowHour * 60 + nowMinute + 240
    return if (totalMinutes >= 1440) {
        // crosses midnight — advance date by 1 day
        val parts = todayIso.split("-").map { it.toInt() }
        val nextDay = java.time.LocalDate.of(parts[0], parts[1], parts[2]).plusDays(1)
        nextDay.toString() to (totalMinutes % 1440 / 60 to totalMinutes % 60)
    } else {
        todayIso to (totalMinutes / 60 to totalMinutes % 60)
    }
}

/** Returns (dateIso, Pair<8, 0>) for the "Tomorrow" chip. */
fun quickDateTomorrow(todayIso: String): Pair<String, Pair<Int, Int>> {
    val parts = todayIso.split("-").map { it.toInt() }
    val tomorrow = java.time.LocalDate.of(parts[0], parts[1], parts[2]).plusDays(1)
    return tomorrow.toString() to (8 to 0)
}

/** Returns (dateIso, Pair<8, 0>) for "Next week": next Sunday (ISO 7). */
fun quickDateNextWeek(todayIso: String): Pair<String, Pair<Int, Int>> {
    val parts = todayIso.split("-").map { it.toInt() }
    val today = java.time.LocalDate.of(parts[0], parts[1], parts[2])
    val nextSunday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY))
    return nextSunday.toString() to (8 to 0)
}

/** Returns (dateIso, Pair<8, 0>) for "Next month": 1st of next month. */
fun quickDateNextMonth(todayIso: String): Pair<String, Pair<Int, Int>> {
    val parts = todayIso.split("-").map { it.toInt() }
    val firstOfNext = java.time.LocalDate.of(parts[0], parts[1], 1).plusMonths(1)
    return firstOfNext.toString() to (8 to 0)
}
```

- [ ] **Step 5: Write `TimeFormatTest.kt`**

```kotlin
package com.ironclinicgym.sift.core.board

import org.junit.Assert.*
import org.junit.Test

class TimeFormatTest {

    // formatOrdinalDate
    @Test fun `ordinal 1st`() = assertEquals("July 1st, 2026", formatOrdinalDate("2026-07-01"))
    @Test fun `ordinal 2nd`() = assertEquals("August 2nd, 2026", formatOrdinalDate("2026-08-02"))
    @Test fun `ordinal 3rd`() = assertEquals("March 3rd, 2026", formatOrdinalDate("2026-03-03"))
    @Test fun `ordinal 4th`() = assertEquals("April 4th, 2026", formatOrdinalDate("2026-04-04"))
    @Test fun `ordinal 11th exception`() = assertEquals("November 11th, 2026", formatOrdinalDate("2026-11-11"))
    @Test fun `ordinal 12th exception`() = assertEquals("December 12th, 2026", formatOrdinalDate("2026-12-12"))
    @Test fun `ordinal 13th exception`() = assertEquals("March 13th, 2026", formatOrdinalDate("2026-03-13"))
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
    @Test fun `later today adds 4 hours`() {
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
        assertEquals(8, time.first); assertEquals(0, time.second)
    }

    // quickDateNextMonth
    @Test fun `next month is 1st of next month at 8am`() {
        val (date, time) = quickDateNextMonth("2026-07-09")
        assertEquals("2026-08-01", date)
        assertEquals(8, time.first); assertEquals(0, time.second)
    }
}
```

- [ ] **Step 6: Run tests**

```bash
./gradlew :core:test --tests "*.TimeFormatTest"
```

Expected: `BUILD SUCCESSFUL` with 16+ tests all passing.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/TimeFormat.kt \
        core/src/test/kotlin/com/ironclinicgym/sift/core/board/TimeFormatTest.kt
git commit -m "feat(core): add formatOrdinalDate, priorityLabelForTask, quick-date helpers"
```

---

## Task 2: B7 + A2 — Task detail priority label and change action

**Goal:** Fix the priority label in `TaskDetailSheet` to show "Due today" or "Due July 8th, 2026" (not the band name), and add a "Change date" / "Change priority" secondary button.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/TaskDetailSheet.kt`
- Create: `app/src/main/java/com/ironclinicgym/sift/ui/board/PriorityPickerSheet.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt` (add `openRedirectPromptForTask()`)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt` (wire A2 callbacks)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt` (wire A2 callbacks)

**Acceptance Criteria:**
- [ ] Dated task due today: detail sheet shows "Due today"
- [ ] Dated task due 2026-07-15: detail sheet shows "Due July 15th, 2026"
- [ ] Undated task in ASAP: detail sheet shows "ASAP" (no "in" prefix, no "Due")
- [ ] Undated task shows "Change priority" secondary button
- [ ] Dated task shows "Change date" secondary button
- [ ] Tapping "Change priority" dismisses detail sheet and opens `PriorityPickerSheet`
- [ ] Tapping "Change date" dismisses detail sheet and opens `RedirectPromptSheet`
- [ ] All label logic tests pass: `./gradlew :core:test --tests "*.TimeFormatTest"`

**Verify:** `./gradlew :core:test --tests "*.TimeFormatTest"` → all label tests pass; manual verification per [manual] ACs.

**Steps:**

- [ ] **Step 1: Read `TaskDetailSheet.kt`**

Read the file. Find the priority label line (currently: `if (item.task.isDated) "Due ${priorityName.lowercase()}" else priorityName.lowercase()`). Note the composable signature and existing callbacks (`onEdit`, `onComplete`, `onRemove`, `onDismiss`).

- [ ] **Step 2: Add new callbacks to `TaskDetailSheet`**

In the function signature, add:
```kotlin
onChangeDate: () -> Unit,     // called for dated tasks
onChangePriority: () -> Unit, // called for undated tasks
```

- [ ] **Step 3: Fix the priority label**

Replace the label line with:
```kotlin
val todayIso = remember { java.time.LocalDate.now().toString() }
val priorityLabel = priorityLabelForTask(
    isDated = item.task.isDated,
    dueIso = item.task.due,
    todayIso = todayIso,
    priorityDisplayName = priorityName,
)
```

Then use `priorityLabel` where the old expression was.

Import `com.ironclinicgym.sift.core.board.priorityLabelForTask`.

- [ ] **Step 4: Add "Change date" / "Change priority" secondary button**

Find the secondary button row (currently `Edit` and `Remove`). Add a third button BEFORE or AFTER (follow the existing layout order — likely add it so the row is now three equal-weight buttons):

```kotlin
val changeLabel = if (item.task.isDated) "Change date" else "Change priority"
val changeAction = if (item.task.isDated) onChangeDate else onChangePriority

// In the secondary buttons Row:
SecondaryActionButton(
    icon = if (item.task.isDated) "edit_calendar" else "low_priority",
    label = changeLabel,
    onClick = changeAction,
    modifier = Modifier.weight(1f),
)
```

Look at `SecondaryActionButton` usage in the file for the exact icon name strings used (they follow the Material Symbols naming).

- [ ] **Step 5: Add `openRedirectPromptForTask()` to `BoardViewModel.kt`**

Read `BoardViewModel.kt`. Find `_redirectPrompt` (private `MutableStateFlow`) and how `moveTask()` sets it when returning `is PolicyDecision.RedirectPrompt`. Add a public method:

```kotlin
fun openRedirectPromptForTask(task: SiftTask) {
    _redirectPrompt.value = PolicyDecision.RedirectPrompt(task = task, targetPriorityId = "")
}
```

The `targetPriorityId = ""` is fine — `RedirectPromptSheet` doesn't use it for the date-change path; it just needs the task.

- [ ] **Step 6: Create `PriorityPickerSheet.kt`**

```kotlin
package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.core.board.BoardProjection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerSheet(
    priorities: List<BoardProjection.PriorityView>,
    currentPriorityId: String,
    onSelect: (priorityId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Move to",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        priorities
            .filter { it.id != currentPriorityId }
            .forEach { priority ->
                ListItem(
                    headlineContent = { Text(priority.displayName) },
                    modifier = Modifier.clickable { onSelect(priority.id); onDismiss() },
                )
            }
        Spacer(Modifier.height(16.dp))
    }
}
```

Check the actual type name for `PriorityView` — it may be `BoardProjection.ProjectedPriority` or a different wrapper class. Search the codebase: `grep -rn "displayName" app/src --include="*.kt" | grep -i priority | head -20`.

- [ ] **Step 7: Wire callbacks in `BoardScreen.kt`**

Find where `TaskDetailSheet` is called. Add local state for the priority picker:

```kotlin
var showPriorityPicker by remember { mutableStateOf(false) }

// In the TaskDetailSheet call:
TaskDetailSheet(
    // ...existing params...
    onChangeDate = {
        val task = selectedTask?.first?.task ?: return@TaskDetailSheet
        selectedTask = null
        viewModel.openRedirectPromptForTask(task)
    },
    onChangePriority = {
        selectedTask = null
        showPriorityPicker = true
    },
)

// After TaskDetailSheet block:
if (showPriorityPicker) {
    selectedTask?.let { (item, _) ->
        val priorities by viewModel.boardProjection.collectAsStateWithLifecycle()
        PriorityPickerSheet(
            priorities = priorities?.priorities ?: emptyList(),
            currentPriorityId = item.priorityId,
            onSelect = { pid -> viewModel.moveTask(item.task.pageId, pid) },
            onDismiss = { showPriorityPicker = false },
        )
    }
}
```

Note: check the actual property names — `item.priorityId` may differ; look at `ProjectedItem` in `BoardProjection.kt`.

- [ ] **Step 8: Wire callbacks in `FocusedBucketScreen.kt`**

Apply the same pattern: add `showPriorityPicker` local state, wire `onChangeDate` and `onChangePriority` callbacks to `TaskDetailSheet`. Follow the same pattern as Step 7.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/TaskDetailSheet.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/PriorityPickerSheet.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt
git commit -m "fix(ui): B7 priority label, A2 Change date/priority action in task detail"
```

---

## Task 3: B3 + B4 — Add-task drawer two-state, date display, chip defaults

**Goal:** Implement the minimized capture bar (shown after save), fix date/time display format, add chip time defaults (+4h, 8am, next Sunday, 1st of next month), and animate chip collapse when a date is set.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt` (extend `DraftState`)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt` (large)
- Modify: the composable hosting `showAddTask` state — find it: `grep -rn "showAddTask" app/src --include="*.kt"` (likely `SiftNavHost.kt` or a `SiftPagerScreen.kt`)
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt` (expose `MinimizedCaptureBar`)

**Acceptance Criteria:**
- [ ] After a successful task save, the full drawer closes and a minimized bar appears at the bottom of the screen above the tab bar
- [ ] The minimized bar shows a title input field and the Task/Brain dump toggle
- [ ] Tapping the minimized bar's title field opens the full drawer in expanded state
- [ ] Draft state (title, bucket, date, type) is restored when the drawer reopens without saving
- [ ] `formatOrdinalDate` is used for date display: "July 8th, 2026" format
- [ ] `formatHourMinute` is used for time display: "6:00 AM" (12h) or "06:00" (24h)
- [ ] "Later today" chip sets time to now+4h, "Tomorrow" = 8:00 AM, "Next week" = next Sunday 8:00 AM, "Next month" = 1st of next month 8:00 AM
- [ ] Quick-date chips animate away with `shrinkVertically` when date is set; helper text appears in their place; reversible when date is removed
- [ ] Tests pass: `./gradlew :core:test --tests "*.TimeFormatTest"`

**Verify:** `./gradlew :core:test --tests "*.TimeFormatTest"` (quick-date helpers); [ui-auto] test for minimized state post-save; [manual] for visual correctness.

**Steps:**

- [ ] **Step 1: Extend `DraftState` in `BoardViewModel.kt` to include date**

Find the `DraftState` data class (currently has `title, notes, bucketId, isBrainDump`). Add `dateIso`:

```kotlin
data class DraftState(
    val title: String,
    val notes: String,
    val bucketId: String?,
    val isBrainDump: Boolean,
    val dateIso: String? = null,  // ISO date string "YYYY-MM-DD"
)
```

- [ ] **Step 2: Add `onSaved` callback to `AddTaskSheetV2`**

In the `AddTaskSheetV2` composable function signature, add:
```kotlin
onSaved: () -> Unit = {},  // called after successful save, INSTEAD of onDismiss
```

Find the submit/save handler (`viewModel.addTask(...)` or equivalent). Currently it calls `onDismiss()` on success. Change to call `onSaved()` on success instead:

```kotlin
// Before (success path):
onDismiss()

// After:
onSaved()
```

The `onDismiss` callback remains for explicit dismissal (back press, swipe down) without saving.

- [ ] **Step 3: Save draft date on dismiss, restore on open**

Find the dismiss handler that saves draft state. Add `dateIso`:

```kotlin
// In the dismiss handler:
viewModel.pendingDraft = BoardViewModel.DraftState(
    title = title,
    notes = notes,
    bucketId = bucketId,
    isBrainDump = whenPath == WhenPath.BRAIN_DUMP,
    dateIso = selectedDate,  // ADD THIS
)
```

Find the `LaunchedEffect` that restores draft state on open. Add date restoration:

```kotlin
viewModel.pendingDraft?.let { draft ->
    title = draft.title
    notes = draft.notes
    bucketId = draft.bucketId
    whenPath = if (draft.isBrainDump) WhenPath.BRAIN_DUMP else WhenPath.DATE
    draft.dateIso?.let { selectedDate = it }  // ADD THIS
}
viewModel.clearDraft()
```

- [ ] **Step 4: Fix date and time display to use new format functions**

Find where the selected date is displayed (currently likely `humanDate()` or raw string). Replace with `formatOrdinalDate()`:

```kotlin
import com.ironclinicgym.sift.core.board.formatOrdinalDate
import com.ironclinicgym.sift.core.board.formatHourMinute

// Date display chip/label:
val dateLabel = selectedDate?.let { formatOrdinalDate(it) } ?: "Date"

// Time display (replace the raw format string with):
val timeLabel = time?.let { (h, m) ->
    formatHourMinute(h, m, boardSettings.use24HourTime)
} ?: "Time"
```

Use `dateLabel` and `timeLabel` in the composable where the date/time pill is rendered.

- [ ] **Step 5: Fix quick-date chip time defaults**

Find the chip click handlers. Replace the current date-only logic with time-aware logic using the new helpers:

```kotlin
import com.ironclinicgym.sift.core.board.quickDateLaterToday
import com.ironclinicgym.sift.core.board.quickDateTomorrow
import com.ironclinicgym.sift.core.board.quickDateNextWeek
import com.ironclinicgym.sift.core.board.quickDateNextMonth
import java.time.LocalDate
import java.time.LocalDateTime

// "Later today" chip:
val now = LocalDateTime.now()
val todayIso = LocalDate.now().toString()
val (ltDate, ltTime) = quickDateLaterToday(now.hour, now.minute, todayIso)
selectedDate = ltDate
time = ltTime
whenPath = WhenPath.DATE

// "Tomorrow" chip:
val (tmDate, tmTime) = quickDateTomorrow(LocalDate.now().toString())
selectedDate = tmDate
time = tmTime
whenPath = WhenPath.DATE

// "Next week" chip:
val (nwDate, nwTime) = quickDateNextWeek(LocalDate.now().toString())
selectedDate = nwDate
time = nwTime
whenPath = WhenPath.DATE

// "Next month" chip:
val (nmDate, nmTime) = quickDateNextMonth(LocalDate.now().toString())
selectedDate = nmDate
time = nmTime
whenPath = WhenPath.DATE
```

- [ ] **Step 6: Animate chip collapse when date is selected**

Find the quick-date chips section. Wrap it with `AnimatedVisibility`:

```kotlin
import androidx.compose.animation.*

AnimatedVisibility(
    visible = selectedDate == null,
    enter = expandVertically(animationSpec = tween(200)),
    exit = shrinkVertically(animationSpec = tween(200)),
) {
    // existing quick-date chips Column/Row
}

AnimatedVisibility(
    visible = selectedDate != null,
    enter = fadeIn(animationSpec = tween(200)),
    exit = fadeOut(animationSpec = tween(150)),
) {
    Text(
        "Sift handles the priority. Adjust anytime.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
```

- [ ] **Step 7: Add minimized capture bar state in the host composable**

Run `grep -rn "showAddTask" app/src --include="*.kt"` to find the composable that controls `showAddTask: Boolean`. Read that file.

Add state variables:
```kotlin
var showAddTask by remember { mutableStateOf(false) }
var showMinimizedBar by remember { mutableStateOf(false) }
```

Pass `onSaved` to `AddTaskSheetV2`:
```kotlin
AddTaskSheetV2(
    // ...existing params...
    onSaved = {
        showAddTask = false
        showMinimizedBar = true
    },
    onDismiss = {
        showAddTask = false
        // note: draft is saved by AddTaskSheetV2 internally on dismiss
    },
)
```

- [ ] **Step 8: Render `MinimizedCaptureBar` in the host composable**

`MinimizedCaptureBar` already exists in `BoardScreen.kt` as a `private fun`. Make it `internal`:

```kotlin
// Change: private fun MinimizedCaptureBar(...)
// To:     internal fun MinimizedCaptureBar(...)
```

In the host composable, render the bar when `showMinimizedBar = true`:

```kotlin
if (showMinimizedBar) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)  // if using Box as wrapper; adapt to actual layout
            .navigationBarsPadding()
            .padding(bottom = /* height of tab bar, typically 80.dp */),
    ) {
        MinimizedCaptureBar(
            onTapTask = {
                showMinimizedBar = false
                showAddTask = true
            },
            onTapDump = {
                showMinimizedBar = false
                showAddTask = true
                // The open drawer should default to brain dump; pass a param if needed
            },
        )
    }
}
```

If `MinimizedCaptureBar` doesn't accept a brain-dump-default parameter, extend its signature to accept `initialIsBrainDump: Boolean` and pass it through to `AddTaskSheetV2`.

- [ ] **Step 9: Run tests and commit**

```bash
./gradlew :core:test --tests "*.TimeFormatTest"
```

Then:
```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt \
        # + host composable file
git commit -m "feat(ui): B3 minimized drawer, B4 date/time display and chip time defaults"
```

---

## Task 4: B5 — Recurrence picker sub-sheet

**Goal:** Replace the existing AlertDialog recurrence picker in `AddTaskSheetV2` with a `RecurrenceBuilderSheet` that shows a RRULE builder (frequency, interval, day-of-week, monthly mode) with a live plain-English summary.

**Files:**
- Create: `app/src/main/java/com/ironclinicgym/sift/ui/board/RecurrenceBuilderSheet.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt`

**Acceptance Criteria:**
- [ ] Tapping the repeat icon in the date/time row opens `RecurrenceBuilderSheet` (not an AlertDialog)
- [ ] The sub-sheet has: frequency pill selector (Daily/Weekly/Monthly/Yearly), interval number picker ("Every [N]"), day-of-week row (Weekly only), and monthly mode choice (Monthly only)
- [ ] Plain-English summary updates live as selections change
- [ ] Generated RRULE is stored via the existing recurrence layer
- [ ] Tapping "Done" closes the sub-sheet and stores the RRULE
- [ ] `./gradlew build` compiles without errors

**Verify:** `./gradlew build` → BUILD SUCCESSFUL; [ui-auto] tapping repeat icon triggers sub-sheet; [manual] for readability and feel.

**Steps:**

- [ ] **Step 1: Read `AddTaskSheetV2.kt` for the existing recurrence picker**

Find the `showRepeatPicker` state and the `AlertDialog` block that renders it. Note the current `RepeatChoice2` enum and `REPEAT_CHOICES` list — these will be replaced. Also note where `recurrenceRule` state variable is set.

- [ ] **Step 2: Find `RecurrenceText` for plain-English summary**

```bash
find . -name "RecurrenceText.kt" -path "*/core/*"
```

Read the file. Identify the function that converts an RRULE string to a human-readable description (e.g., `RecurrenceText.describe(rrule)` or a top-level function). Use this in `RecurrenceBuilderSheet`.

- [ ] **Step 3: Create `RecurrenceBuilderSheet.kt`**

```kotlin
package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceText
import java.time.DayOfWeek

enum class RRuleFrequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly")
}

enum class MonthlyMode(val label: String) {
    SPECIFIC_DAY("On a specific day"), RELATIVE_DAY("On a relative day")
}

private fun DayOfWeek.toRRuleCode() = when (this) {
    DayOfWeek.MONDAY -> "MO"; DayOfWeek.TUESDAY -> "TU"; DayOfWeek.WEDNESDAY -> "WE"
    DayOfWeek.THURSDAY -> "TH"; DayOfWeek.FRIDAY -> "FR"
    DayOfWeek.SATURDAY -> "SA"; DayOfWeek.SUNDAY -> "SU"
}

private val WEEK_DAYS = listOf(
    DayOfWeek.MONDAY to "M", DayOfWeek.TUESDAY to "T", DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T", DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "S",
)

internal fun buildRRule(
    frequency: RRuleFrequency,
    interval: Int,
    days: Set<DayOfWeek>,
    monthlyMode: MonthlyMode,
): String = buildString {
    append("FREQ=${frequency.name}")
    if (interval > 1) append(";INTERVAL=$interval")
    if (frequency == RRuleFrequency.WEEKLY && days.isNotEmpty()) {
        val dayStr = days.sortedBy { it.value }.joinToString(",") { it.toRRuleCode() }
        append(";BYDAY=$dayStr")
    }
    // Monthly relative day support is complex; emit BYDAY with position for relative
    // (implementer: if monthlyMode == RELATIVE_DAY, add BYDAY logic per RRULE spec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrenceBuilderSheet(
    initialRule: String?,
    onConfirm: (rrule: String, summary: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var frequency by remember { mutableStateOf(RRuleFrequency.WEEKLY) }
    var interval by remember { mutableIntStateOf(1) }
    var selectedDays by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var monthlyMode by remember { mutableStateOf(MonthlyMode.SPECIFIC_DAY) }

    val rrule by remember(frequency, interval, selectedDays, monthlyMode) {
        derivedStateOf { buildRRule(frequency, interval, selectedDays, monthlyMode) }
    }
    // Replace with actual RecurrenceText call — check the real function signature
    val summary = remember(rrule) { RecurrenceText.describeRRule(rrule) ?: rrule }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {

            // Summary
            Text(summary, style = MaterialTheme.typography.titleSmall,
                 modifier = Modifier.padding(bottom = 16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Frequency selector
            Text("Frequency", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RRuleFrequency.values().forEach { freq ->
                    FilterChip(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        label = { Text(freq.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Interval
            Text("Interval", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { if (interval > 1) interval-- }) {
                    Icon(/* minus icon */ androidx.compose.material.icons.Icons.Rounded.Remove, "-")
                }
                Text("$interval", style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { interval++ }) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Add, "+")
                }
            }
            Spacer(Modifier.height(12.dp))

            // Day-of-week (weekly only)
            if (frequency == RRuleFrequency.WEEKLY) {
                Text("On days", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WEEK_DAYS.forEach { (day, label) ->
                        val selected = day in selectedDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) selectedDays - day else selectedDays + day
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Monthly mode (monthly only)
            if (frequency == RRuleFrequency.MONTHLY) {
                Text("Repeats on", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                MonthlyMode.values().forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = monthlyMode == mode,
                            onValueChange = { if (it) monthlyMode = mode }
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = monthlyMode == mode, onClick = { monthlyMode = mode })
                        Spacer(Modifier.width(8.dp))
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { onConfirm(rrule, summary) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
```

Note: `RecurrenceText.describeRRule(rrule)` — check the actual function name in the codebase and adjust the call accordingly.

- [ ] **Step 4: Replace AlertDialog with `RecurrenceBuilderSheet` in `AddTaskSheetV2.kt`**

Remove the `AlertDialog` block for `showRepeatPicker`. Replace with:

```kotlin
if (showRepeatPicker) {
    RecurrenceBuilderSheet(
        initialRule = recurrenceRule,
        onConfirm = { rrule, _ ->
            recurrenceRule = rrule
            showRepeatPicker = false
        },
        onDismiss = { showRepeatPicker = false },
    )
}
```

Remove the `RepeatChoice2` enum and `REPEAT_CHOICES` list if they are no longer referenced anywhere else.

- [ ] **Step 5: Build and verify compilation**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/RecurrenceBuilderSheet.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/AddTaskSheetV2.kt
git commit -m "feat(ui): B5 replace recurrence dialog with RRULE builder sub-sheet"
```

---

## Task 5: One-day landmarks in FocusedPriorityScreen and Settings

**Goal:** Render landmark sub-headers (This month, Next 90 days, Later this year, Beyond, No date) inside `FocusedPriorityScreen` when viewing the "one day" priority, and add toggles in Settings.

**Files:**
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt`

**Acceptance Criteria:**
- [ ] `BoardSettings` has a `oneDayLandmarksEnabled: Set<String>` field defaulting to all landmark names
- [ ] `FocusedPriorityScreen` renders landmark sub-headers for the "one day" priority only
- [ ] Landmark groups with no items are not rendered (no empty section headers)
- [ ] Settings screen has a toggle per landmark that persists to `BoardSettingsDataStore`
- [ ] `./gradlew :core:test` passes

**Verify:** `./gradlew :core:test` → BUILD SUCCESSFUL; [ui-auto] landmark headers present for "one day" in FocusedPriorityScreen.

**Steps:**

- [ ] **Step 1: Add `oneDayLandmarksEnabled` to `BoardSettings.kt`**

Read `BoardSettings.kt`. Add the field at the end of the data class body with a default of all landmarks:

```kotlin
import com.ironclinicgym.sift.core.board.Landmark

@Serializable
data class BoardSettings(
    // ...existing fields...
    val oneDayLandmarksEnabled: Set<String> = Landmark.values().map { it.name }.toSet(),
)
```

The file uses `@Serializable` with `ignoreUnknownKeys = true` on deserialization, so adding a new field with a default value is safe — existing stored settings will deserialize without error.

- [ ] **Step 2: Read `FocusedBucketScreen.kt` and find the `FocusedPriorityScreen` function**

Find the `LazyColumn` that renders `visibleItems`. The `projectedPriority` param has type `ProjectedPriority` which already has `landmarks: Map<Landmark, List<ProjectedItem>>`. Find the priority ID constant for "one day" — search: `grep -rn "one_day\|oneday\|ONE_DAY" core/ --include="*.kt" | head -10`.

- [ ] **Step 3: Render landmark sub-headers in `FocusedPriorityScreen`**

Read `OneDayLandmarks.kt` to find `displayLabel(landmark: Landmark): String`. Import it.

In the `LazyColumn` inside `FocusedPriorityScreen`, replace the current `items(visibleItems)` pattern with:

```kotlin
import com.ironclinicgym.sift.core.board.displayLabel

// Inside LazyColumn:
val isOneDayPriority = projectedPriority.id == ONE_DAY_PRIORITY_ID  // use actual constant
val enabledLandmarks = boardSettings.oneDayLandmarksEnabled

if (isOneDayPriority && projectedPriority.landmarks.isNotEmpty()) {
    projectedPriority.landmarks
        .filter { (landmark, items) ->
            items.isNotEmpty() && landmark.name in enabledLandmarks
        }
        .forEach { (landmark, landmarkItems) ->
            item(key = "landmark_${landmark.name}") {
                Text(
                    text = displayLabel(landmark),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(
                items = landmarkItems,
                key = { it.task.pageId },
            ) { projectedItem ->
                // Use the same TaskCard composable as the existing items() call
                TaskCard(/* same params as before, using projectedItem */)
            }
        }
} else {
    items(visibleItems, key = { it.task.pageId }) { projectedItem ->
        TaskCard(/* existing code */)
    }
}
```

Check the exact `TaskCard` call signature from the existing code and mirror it exactly.

- [ ] **Step 4: Add landmark toggles to `SettingsScreen.kt`**

Find where board settings sections are rendered. Add a new section:

```kotlin
import com.ironclinicgym.sift.core.board.Landmark
import com.ironclinicgym.sift.core.board.displayLabel

// New section in SettingsScreen:
SettingsSection(title = "One Day Landmarks") {
    Landmark.values().forEach { landmark ->
        val enabled = boardSettings.oneDayLandmarksEnabled.contains(landmark.name)
        SettingsSwitchRow(
            label = displayLabel(landmark),
            checked = enabled,
            onCheckedChange = { isEnabled ->
                val updated = if (isEnabled) {
                    boardSettings.oneDayLandmarksEnabled + landmark.name
                } else {
                    boardSettings.oneDayLandmarksEnabled - landmark.name
                }
                viewModel.updateBoardSettings(boardSettings.copy(oneDayLandmarksEnabled = updated))
            },
        )
    }
}
```

Use the actual `SettingsSection` and `SettingsSwitchRow` composable names from the existing settings screen code.

- [ ] **Step 5: Run tests and commit**

```bash
./gradlew :core:test
./gradlew :app:assembleDebug
```

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): one-day landmark sub-headers in FocusedPriorityScreen plus settings toggles"
```

---

## Task 6: Safety catch — Room migration, dialog redesign, evaluation function

**Goal:** Add `safetyCatchFiredBand` to Room (migration v5→v6), redesign `SafetyCatchDialog` with correct copy and "Coming soon" badge, write a pure `evaluateSafetyCatch()` function in `:core`, and trigger it after every sync/refresh in `BoardViewModel`.

**Files:**
- Create: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluator.kt`
- Create: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluatorTest.kt`
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/domain/ports/Ports.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/data/local/TaskLocalStateRoom.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/data/local/TaskCacheRoom.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/SafetyCatchDialog.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt`

**Acceptance Criteria:**
- [ ] `TaskLocalState` has `safetyCatchFiredBand: String? = null`
- [ ] `TaskLocalStateEntity` has `safetyCatchFiredBand` column
- [ ] `MIGRATION_5_6` adds `safetyCatchFiredBand TEXT` column to `task_local_state`
- [ ] DB version is 6
- [ ] `evaluateSafetyCatch()` fires for a task newly in ASAP/today band with null `safetyCatchFiredBand`
- [ ] `evaluateSafetyCatch()` does NOT fire for a task whose `safetyCatchFiredBand` matches the current band
- [ ] `evaluateSafetyCatch()` returns page IDs to clear when tasks exit the imminent band
- [ ] Dialog shows correct copy: "This one is due today. Want a hand with it, or should we find it a better spot?" (overdue: "This one was due [Month Dth].")
- [ ] Dialog has "Find it a better spot" (enabled) and "Want a hand with it?" (disabled with "Coming soon" chip)
- [ ] Logic tests pass: `./gradlew :core:test --tests "*.SafetyCatchEvaluatorTest"`

**Verify:** `./gradlew :core:test --tests "*.SafetyCatchEvaluatorTest"` → all tests pass.

**Steps:**

- [ ] **Step 1: Add `safetyCatchFiredBand` to `TaskLocalState` in `Ports.kt`**

Read `Ports.kt`. Find the `TaskLocalState` data class. Add:

```kotlin
data class TaskLocalState(
    // ...existing fields...
    val safetyCatchFiredBand: String? = null,
)
```

- [ ] **Step 2: Add column to `TaskLocalStateEntity` in `TaskLocalStateRoom.kt`**

Read `TaskLocalStateRoom.kt`. Add to the `@Entity` data class:

```kotlin
@ColumnInfo(name = "safetyCatchFiredBand")
val safetyCatchFiredBand: String? = null,
```

Update the mapper function that converts `TaskLocalStateEntity` → `TaskLocalState` (and the reverse if it exists) to include the new field.

- [ ] **Step 3: Add `MIGRATION_5_6` and bump DB version in `TaskCacheRoom.kt`**

Read `TaskCacheRoom.kt`. Find `@Database(version = 5, ...)`. Change to `version = 6`. Find `MIGRATION_4_5` and add after it:

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE task_local_state ADD COLUMN safetyCatchFiredBand TEXT"
        )
    }
}
```

Find where migrations are registered (e.g., `.addMigrations(MIGRATION_4_5)`) and add `MIGRATION_5_6`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

- [ ] **Step 4: Write `SafetyCatchEvaluator.kt` in `:core`**

```kotlin
package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState

data class SafetyCatchEvaluation(
    val toFire: List<Pair<SiftTask, String>>,  // task, band ("asap" or "today")
    val toClear: List<String>,                  // page IDs whose band no longer applies
)

/**
 * Pure function. Evaluates which tasks need a safety-catch prompt and which
 * have exited the imminent band so their stored record can be cleared.
 *
 * "asap" band = task is overdue (due < todayIso).
 * "today" band = task is due today (due == todayIso).
 * A task is NOT fired again if safetyCatchFiredBand already equals the current band.
 * A task's record is cleared if it is no longer in any imminent band.
 */
fun evaluateSafetyCatch(
    datedTasks: List<SiftTask>,
    localStates: Map<String, TaskLocalState>,
    todayIso: String,
): SafetyCatchEvaluation {
    val toFire = mutableListOf<Pair<SiftTask, String>>()
    val toClear = mutableListOf<String>()

    for (task in datedTasks) {
        val due = task.due?.take(10) ?: continue
        val currentBand: String? = when {
            due < todayIso -> "asap"   // overdue
            due == todayIso -> "today"  // due today
            else -> null                // not imminent
        }
        val firedBand = localStates[task.pageId]?.safetyCatchFiredBand

        if (currentBand != null) {
            if (firedBand != currentBand) {
                toFire.add(task to currentBand)
            }
        } else if (firedBand != null) {
            // Task exited imminent band — clear the record so it re-fires on re-entry
            toClear.add(task.pageId)
        }
    }

    return SafetyCatchEvaluation(toFire = toFire, toClear = toClear)
}
```

- [ ] **Step 5: Write `SafetyCatchEvaluatorTest.kt`**

```kotlin
package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import org.junit.Assert.*
import org.junit.Test

class SafetyCatchEvaluatorTest {

    private fun makeTask(pageId: String, due: String) = SiftTask(
        pageId = pageId,
        title = "Test",
        due = due,
        // Add any other required SiftTask constructor params here with defaults.
        // Check SiftTask data class for full field list.
    )

    private fun makeState(pageId: String, firedBand: String? = null) = TaskLocalState(
        pageId = pageId,
        mappingId = "m1",
        safetyCatchFiredBand = firedBand,
        // Other fields with defaults
    )

    @Test fun `fires for overdue task with no prior record`() {
        val task = makeTask("p1", "2026-07-08")  // yesterday
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(1, eval.toFire.size)
        assertEquals("p1", eval.toFire[0].first.pageId)
        assertEquals("asap", eval.toFire[0].second)
    }

    @Test fun `fires for due-today task with no prior record`() {
        val task = makeTask("p1", "2026-07-09")
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(1, eval.toFire.size)
        assertEquals("today", eval.toFire[0].second)
    }

    @Test fun `does not fire if safetyCatchFiredBand matches current band`() {
        val task = makeTask("p1", "2026-07-09")
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(0, eval.toFire.size)
    }

    @Test fun `fires again if band changes from today to asap`() {
        // Task was due today (fired "today"), now is overdue (should fire "asap")
        val task = makeTask("p1", "2026-07-08")  // yesterday = overdue
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(1, eval.toFire.size)
        assertEquals("asap", eval.toFire[0].second)
    }

    @Test fun `clears record when task is no longer imminent`() {
        val task = makeTask("p1", "2026-07-15")  // future, not imminent
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(0, eval.toFire.size)
        assertEquals(listOf("p1"), eval.toClear)
    }

    @Test fun `does not fire for future tasks`() {
        val task = makeTask("p1", "2026-07-20")
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(0, eval.toFire.size)
        assertEquals(0, eval.toClear.size)
    }

    @Test fun `queues multiple imminent tasks`() {
        val t1 = makeTask("p1", "2026-07-08")
        val t2 = makeTask("p2", "2026-07-09")
        val eval = evaluateSafetyCatch(
            listOf(t1, t2),
            mapOf("p1" to makeState("p1"), "p2" to makeState("p2")),
            "2026-07-09"
        )
        assertEquals(2, eval.toFire.size)
    }
}
```

Run after writing:
```bash
./gradlew :core:test --tests "*.SafetyCatchEvaluatorTest"
```

- [ ] **Step 6: Redesign `SafetyCatchDialog.kt`**

Read `SafetyCatchDialog.kt`. Replace the existing dialog content. The dialog now receives the task object so it can build the correct copy:

```kotlin
@Composable
fun SafetyCatchDialog(
    task: SiftTask,
    todayIso: String = remember { java.time.LocalDate.now().toString() },
    onFindBetterSpot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val due = task.due?.take(10)
    val titleText = when {
        due == null -> "Heads up on this one."
        due == todayIso -> "This one is due today."
        else -> "This one was due ${formatOrdinalDate(due)}."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Text("Want a hand with it, or should we find it a better spot?") },
        confirmButton = {
            TextButton(onClick = { onFindBetterSpot(); onDismiss() }) {
                Text("Find it a better spot")
            }
        },
        dismissButton = {
            Box {
                TextButton(enabled = false, onClick = {}) {
                    Text("Want a hand with it?")
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "Coming soon",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    )
}
```

Import `formatOrdinalDate` from core.

- [ ] **Step 7: Wire safety catch evaluation in `BoardViewModel.kt`**

Add a `_safetyCatchQueue: MutableStateFlow<List<Pair<SiftTask, String>>> = MutableStateFlow(emptyList())` and expose `currentSafetyCatch: StateFlow<Pair<SiftTask, String>?> = _safetyCatchQueue.map { it.firstOrNull() }.stateIn(...)`.

Add a `fun dismissSafetyCatch()` that removes the first item from the queue.

In the post-refresh or post-sync code path (where tasks are updated from the repository), call `evaluateSafetyCatch()` and update the queue:

```kotlin
private suspend fun runSafetyCatchEvaluation(
    tasks: List<SiftTask>,
    states: Map<String, TaskLocalState>,
    todayIso: String,
) {
    val evaluation = evaluateSafetyCatch(
        datedTasks = tasks.filter { it.isDated },
        localStates = states,
        todayIso = todayIso,
    )
    // Record fired bands
    evaluation.toFire.forEach { (task, band) ->
        localStateRepository.updateSafetyCatchFiredBand(task.pageId, mappingId, band)
    }
    // Clear exited bands
    evaluation.toClear.forEach { pageId ->
        localStateRepository.updateSafetyCatchFiredBand(pageId, mappingId, null)
    }
    // Enqueue prompts (one at a time via the queue)
    _safetyCatchQueue.value = evaluation.toFire
}
```

Find the exact method name for `updateSafetyCatchFiredBand` in the DAO — you may need to add it. Look at existing DAO update patterns in `TaskLocalStateRoom.kt`:

```kotlin
@Query("UPDATE task_local_state SET safetyCatchFiredBand = :band WHERE pageId = :pageId AND mappingId = :mappingId")
suspend fun updateSafetyCatchFiredBand(pageId: String, mappingId: String, band: String?)
```

In `BoardScreen.kt` (and `FocusedBucketScreen.kt`), observe `currentSafetyCatch` and render `SafetyCatchDialog`:

```kotlin
val safetyCatch by viewModel.currentSafetyCatch.collectAsStateWithLifecycle()
safetyCatch?.let { (task, _) ->
    SafetyCatchDialog(
        task = task,
        onFindBetterSpot = {
            viewModel.openRedirectPromptForTask(task)
            viewModel.dismissSafetyCatch()
        },
        onDismiss = { viewModel.dismissSafetyCatch() },
    )
}
```

- [ ] **Step 8: Run tests and commit**

```bash
./gradlew :core:test --tests "*.SafetyCatchEvaluatorTest"
./gradlew :app:assembleDebug
```

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluator.kt \
        core/src/test/kotlin/com/ironclinicgym/sift/core/board/SafetyCatchEvaluatorTest.kt \
        core/src/main/kotlin/com/ironclinicgym/sift/core/domain/ports/Ports.kt \
        app/src/main/java/com/ironclinicgym/sift/data/local/TaskLocalStateRoom.kt \
        app/src/main/java/com/ironclinicgym/sift/data/local/TaskCacheRoom.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/SafetyCatchDialog.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt
git commit -m "feat: safety catch evaluation, Room v6 migration, redesigned dialog"
```

---

## Task 7: A6 — Remove SnackbarHost and route all messages to TopNotificationBar

**Goal:** Remove `SnackbarHost` from `BoardScreen` and `FocusedBucketScreen`, add a `postNotification()` API to `BoardViewModel`, and route all existing snackbar messages through `TopNotificationBar`.

**Files:**
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt`

**Acceptance Criteria:**
- [ ] No `SnackbarHost` composable in `BoardScreen` layout tree
- [ ] No `SnackbarHost` composable in `FocusedBucketScreen` layout tree
- [ ] `BoardViewModel` exposes `activeNotification: StateFlow<NotificationVariant?>`
- [ ] All existing notification types (task added, sync success, sync fail, snooze confirmation) route through `activeNotification`
- [ ] `./gradlew :app:assembleDebug` compiles without errors

**Verify:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; search `grep -rn "SnackbarHost" app/` → no results.

**Steps:**

- [ ] **Step 1: Read `BoardViewModel.kt` to understand the current notification flow**

Look for: `SnackbarHostState`, `messages: SharedFlow`, `BoardNotification` sealed class (or wherever notification text is emitted). Map out all the places that call `snackbarHostState.showSnackbar()` or emit to the messages flow. This is the list you'll need to migrate.

- [ ] **Step 2: Add `activeNotification` state and `postNotification()` to `BoardViewModel.kt`**

```kotlin
import com.ironclinicgym.sift.ui.board.NotificationVariant

private val _activeNotification = MutableStateFlow<NotificationVariant?>(null)
val activeNotification: StateFlow<NotificationVariant?> = _activeNotification.asStateFlow()

fun dismissNotification() {
    _activeNotification.value = null
}

internal fun postNotification(variant: NotificationVariant) {
    _activeNotification.value = variant
}
```

- [ ] **Step 3: Migrate each existing snackbar emission to `postNotification()`**

For each emission point found in Step 1, replace with the appropriate `NotificationVariant`:

- Task added → `NotificationVariant.ConfirmPlacement(taskName, priorityName)` (already has this variant)
- Sync success → `NotificationVariant.RefreshSuccess("Synced")`
- Sync failure → `NotificationVariant.RefreshError(message, onRetry = { viewModel.refreshNow() })`
- Snooze confirmation → `NotificationVariant.Reversible(message, "Undo") { viewModel.undo() }`
- Any other text message → `NotificationVariant.RefreshSuccess(text)` as a generic informational variant

Check `NotificationVariant` definition in `TopNotificationBar.kt` for exact constructor signatures.

Remove any `SnackbarHostState` fields from `BoardViewModel` and remove the `messages` SharedFlow if it is no longer used for anything other than snackbar delivery. If `messages` is used for undo data beyond display text, keep the SharedFlow but remove the snackbar collection site.

- [ ] **Step 4: Remove `SnackbarHost` from `BoardScreen.kt`**

Read `BoardScreen.kt`. Find `SnackbarHost` (line ~193 per earlier analysis). Remove:
- The `SnackbarHostState` variable
- The `SnackbarHost` composable
- Any `LaunchedEffect` that calls `snackbarHostState.showSnackbar()`
- Any `LaunchedEffect` that collects `viewModel.messages` into the snackbar

Replace with a `LaunchedEffect` that collects `viewModel.messages` and calls `viewModel.postNotification()` if any message flow is still being observed. For the `fromSetup` snackbar (line ~100), replace with:

```kotlin
LaunchedEffect(fromSetup) {
    if (fromSetup) {
        viewModel.postNotification(NotificationVariant.RefreshSuccess("Welcome to Sift"))
    }
}
```

Wire `activeNotification` to the existing `TopNotificationBar` call (which should already be in the layout):

```kotlin
val activeNotification by viewModel.activeNotification.collectAsStateWithLifecycle()
TopNotificationBar(
    variant = activeNotification,
    onDismiss = { viewModel.dismissNotification() },
)
```

- [ ] **Step 5: Remove `SnackbarHost` from `FocusedBucketScreen.kt`**

Read the file. Find `SnackbarHost` (line ~158). Remove it and any associated `SnackbarHostState`. If `FocusedBucketScreen` has its own TopNotificationBar, wire it to `activeNotification`. If it shares the board's notification bar (because TopNotificationBar is rendered at a higher level), just remove the `SnackbarHost` without adding a replacement.

- [ ] **Step 6: Verify no SnackbarHost remains**

```bash
grep -rn "SnackbarHost" app/src --include="*.kt"
```

Expected output: zero results (or only the definition in a Material3 import, not in our source files).

- [ ] **Step 7: Build and commit**

```bash
./gradlew :app:assembleDebug
```

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/FocusedBucketScreen.kt
git commit -m "fix(ui): A6 remove SnackbarHost, route all notifications through TopNotificationBar"
```

---

## Task 8: Signal Inflation — persistent nudge, settings, encouraging messages

**Goal:** Wire Signal Inflation UI: add the `InflationNudge` persistent `TopNotificationBar` variant, add dismissal-with-re-fire logic to `BoardViewModel`, add Signal Inflation settings to `SettingsScreen`, fix the default `asapThreshold` from 7 to 5, and implement the encouraging message pool.

**Files:**
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/SignalInflation.kt`
- Modify: `core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/data/local/AppPreferencesDataStore.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/TopNotificationBar.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt`
- Create: `core/src/test/kotlin/com/ironclinicgym/sift/core/board/SignalInflationTest.kt`

**Acceptance Criteria:**
- [ ] `SignalInflation.checkInflation()` default `asapThreshold` is 5 (changed from 7)
- [ ] `BoardSettings` has `signalInflationEnabled`, `asapInflationThreshold`, `protectedInflationPercent`
- [ ] `TopNotificationBar` has `InflationNudge` variant that does NOT auto-dismiss
- [ ] Dismissing the nudge suppresses re-fire until the count drops below threshold and re-crosses it
- [ ] Encouraging messages rotate without consecutive repeats
- [ ] Master-off toggle fires a one-time informational nudge ("This helps keep things manageable, but it is your call.")
- [ ] Settings section shows ASAP threshold, Protected threshold, master toggle
- [ ] Logic tests pass: `./gradlew :core:test --tests "*.SignalInflationTest"`

**Verify:** `./gradlew :core:test --tests "*.SignalInflationTest"` → all tests pass.

**Steps:**

- [ ] **Step 1: Fix `asapThreshold` default in `SignalInflation.kt`**

Read `SignalInflation.kt`. Find `asapThreshold = 7` in the `checkInflation()` function signature. Change to `asapThreshold = 5`.

- [ ] **Step 2: Add Signal Inflation fields to `BoardSettings.kt`**

Add to the `BoardSettings` data class (after the `oneDayLandmarksEnabled` field added in Task 5):

```kotlin
val signalInflationEnabled: Boolean = true,
val asapInflationThreshold: Int = 5,
val protectedInflationPercent: Int = 30,
```

- [ ] **Step 3: Add DataStore keys for inflation dismissal to `AppPreferencesDataStore.kt`**

Read `AppPreferencesDataStore.kt`. Find the existing preference keys. Add:

```kotlin
val INFLATION_MASTER_OFF_SHOWN = booleanPreferencesKey("inflation_master_off_shown")
```

Also add a companion object function or a flow to read this preference, following the existing pattern (look at how `notionReminderDismissed` is read and expose `inflationMasterOffShown: Flow<Boolean>`).

- [ ] **Step 4: Add `InflationNudge` variant to `TopNotificationBar.kt`**

Read `TopNotificationBar.kt`. Find `NotificationVariant` sealed interface. Add:

```kotlin
data class InflationNudge(
    val message: String,
    val actionLabel: String,
    val onAction: () -> Unit,
) : NotificationVariant
```

In the composable body, find the `LaunchedEffect(variant) { delay(4000L); onDismiss() }` block. Wrap it in a condition so it does NOT auto-dismiss for `InflationNudge`:

```kotlin
if (variant !is NotificationVariant.InflationNudge) {
    LaunchedEffect(variant) {
        delay(4000L)
        onDismiss()
    }
}
```

Add a dismiss (swipe or X) action for the `InflationNudge` variant in the composable. When the nudge has an action button, render it as a `TextButton` in the notification bar. Follow the existing pattern for `RefreshError`'s Retry button.

- [ ] **Step 5: Add inflation dismissal logic to `BoardViewModel.kt`**

```kotlin
private val _inflationDismissed = MutableStateFlow(emptyMap<InflationKind, Boolean>())

// Derived: suppress the alert if dismissed, show it otherwise
val activeInflationAlert: StateFlow<InflationAlert?> = combine(
    inflationAlert,  // existing StateFlow
    _inflationDismissed,
) { alert, dismissed ->
    if (alert != null && dismissed[alert.kind] == true) null else alert
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

fun dismissInflationAlert() {
    val kind = activeInflationAlert.value?.kind ?: return
    _inflationDismissed.update { it + (kind to true) }
}
```

Add an `init` block (or a `viewModelScope.launch` in init) that clears dismissed flags when the underlying alert goes away (count dropped below threshold):

```kotlin
init {
    var lastKind: InflationKind? = null
    viewModelScope.launch {
        inflationAlert.collect { alert ->
            if (alert == null && lastKind != null) {
                // Count dropped below threshold — allow re-fire for both kinds
                _inflationDismissed.value = emptyMap()
            }
            lastKind = alert?.kind
        }
    }
}
```

- [ ] **Step 6: Add encouraging message pool to `BoardViewModel.kt`**

```kotlin
private val ENCOURAGING_MESSAGES = listOf(
    "Nice work clearing those out",
    "Good job re-prioritizing",
    "ASAP is looking cleaner now",
    "That is more like it",
    "Well done",
    "Much more manageable now",
    "Good call on those",
    "Nicely done",
)
private var lastEncouragingMsgIdx = -1

fun encouragingMessage(): String {
    var idx: Int
    do {
        idx = ENCOURAGING_MESSAGES.indices.random()
    } while (idx == lastEncouragingMsgIdx && ENCOURAGING_MESSAGES.size > 1)
    lastEncouragingMsgIdx = idx
    return ENCOURAGING_MESSAGES[idx]
}
```

- [ ] **Step 7: Add master-off toggle handler to `BoardViewModel.kt`**

```kotlin
fun onSignalInflationMasterToggle(enabled: Boolean) {
    viewModelScope.launch {
        if (!enabled) {
            val alreadyShown = appPreferencesDataStore.inflationMasterOffShown.first()
            if (!alreadyShown) {
                postNotification(
                    NotificationVariant.RefreshSuccess(
                        "This helps keep things manageable, but it is your call."
                    )
                )
                appPreferencesDataStore.setInflationMasterOffShown(true)
            }
        }
        updateBoardSettings(boardSettings.value.copy(signalInflationEnabled = enabled))
    }
}
```

Add `suspend fun setInflationMasterOffShown(shown: Boolean)` to `AppPreferencesDataStore`.

- [ ] **Step 8: Wire `activeInflationAlert` to `InflationNudge` in `BoardScreen.kt`**

```kotlin
val inflationAlert by viewModel.activeInflationAlert.collectAsStateWithLifecycle()

LaunchedEffect(inflationAlert) {
    inflationAlert?.let { alert ->
        val (message, actionLabel, action) = when (alert.kind) {
            InflationKind.ASAP_OVERLOAD -> Triple(
                "You have ${alert.count} things in ASAP. Want to thin it out?",
                "Go to ASAP",
                { viewModel.navigateToFocusedPriority("asap") }
            )
            InflationKind.PROTECTED_SATURATION -> Triple(
                "That is a lot of Protected tasks. Want to review them?",
                "Review",
                { viewModel.openProtectedReview() }
            )
        }
        viewModel.postNotification(
            NotificationVariant.InflationNudge(message, actionLabel, action)
        )
    }
}
```

Look at existing navigation patterns in `BoardScreen.kt` for how to navigate to `FocusedPriorityScreen` (the "Go to ASAP" action). There may be a `navController` or an `onNavigate` callback already in scope.

- [ ] **Step 9: Write `SignalInflationTest.kt`**

```kotlin
package com.ironclinicgym.sift.core.board

import org.junit.Assert.*
import org.junit.Test

class SignalInflationTest {

    @Test fun `fires ASAP_OVERLOAD when count equals threshold`() {
        val alert = checkInflation(
            asapCount = 5, totalTasks = 20, protectedCount = 0,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNotNull(alert)
        assertEquals(InflationKind.ASAP_OVERLOAD, alert!!.kind)
    }

    @Test fun `does not fire ASAP_OVERLOAD when count below threshold`() {
        val alert = checkInflation(
            asapCount = 4, totalTasks = 20, protectedCount = 0,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNull(alert)
    }

    @Test fun `fires PROTECTED_SATURATION when protected pct reaches threshold`() {
        val alert = checkInflation(
            asapCount = 0, totalTasks = 10, protectedCount = 3,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNotNull(alert)
        assertEquals(InflationKind.PROTECTED_SATURATION, alert!!.kind)
    }

    @Test fun `does not fire protected when pct below threshold`() {
        val alert = checkInflation(
            asapCount = 0, totalTasks = 10, protectedCount = 2,
            asapThreshold = 5, protectedPercent = 30
        )
        assertNull(alert)
    }

    @Test fun `ASAP takes priority over protected when both exceed threshold`() {
        val alert = checkInflation(
            asapCount = 5, totalTasks = 5, protectedCount = 5,
            asapThreshold = 5, protectedPercent = 30
        )
        // The function should return one alert; ASAP is checked first
        assertNotNull(alert)
    }

    @Test fun `default asapThreshold is 5 not 7`() {
        // Verify the default changed from 7 to 5
        val atFive = checkInflation(asapCount = 5, totalTasks = 10, protectedCount = 0)
        assertNotNull(atFive)
        val atFour = checkInflation(asapCount = 4, totalTasks = 10, protectedCount = 0)
        assertNull(atFour)
    }
}
```

Note: the no-arg default call `checkInflation(asapCount = 5, totalTasks = 10, protectedCount = 0)` requires the threshold params to have defaults. Verify the function signature supports this; if not, use named args with explicit values.

- [ ] **Step 10: Add Signal Inflation section to `SettingsScreen.kt`**

```kotlin
SettingsSection(title = "Signal Inflation") {
    SettingsSwitchRow(
        label = "Enable nudges",
        checked = boardSettings.signalInflationEnabled,
        onCheckedChange = { viewModel.onSignalInflationMasterToggle(it) },
    )
    if (boardSettings.signalInflationEnabled) {
        SettingsNumberRow(
            label = "ASAP threshold",
            value = boardSettings.asapInflationThreshold,
            onValueChange = { n ->
                viewModel.updateBoardSettings(boardSettings.copy(asapInflationThreshold = n))
            },
        )
        SettingsNumberRow(
            label = "Protected threshold (%)",
            value = boardSettings.protectedInflationPercent,
            onValueChange = { n ->
                viewModel.updateBoardSettings(boardSettings.copy(protectedInflationPercent = n))
            },
        )
    }
}
```

Use `SettingsNumberRow` if it exists; otherwise create a simple Row with a `BasicTextField` or `OutlinedTextField` for the numeric input. Follow existing settings row patterns in the file.

- [ ] **Step 11: Run tests and commit**

```bash
./gradlew :core:test --tests "*.SignalInflationTest"
./gradlew :app:assembleDebug
```

```bash
git add core/src/main/kotlin/com/ironclinicgym/sift/core/board/SignalInflation.kt \
        core/src/main/kotlin/com/ironclinicgym/sift/core/board/BoardSettings.kt \
        core/src/test/kotlin/com/ironclinicgym/sift/core/board/SignalInflationTest.kt \
        app/src/main/java/com/ironclinicgym/sift/data/local/AppPreferencesDataStore.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/TopNotificationBar.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/settings/SettingsScreen.kt
git commit -m "feat: Signal Inflation nudge, settings, encouraging messages, asapThreshold default 5"
```

---

## Task 9: Protected review screen

**Goal:** Build `ProtectedReviewScreen` — a transient overlay listing all protected tasks with an unprotect action — and wire it to the Signal Inflation nudge's "Review" action.

**Files:**
- Create: `app/src/main/java/com/ironclinicgym/sift/ui/board/ProtectedReviewScreen.kt`
- Modify: `app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt`
- Modify: the host composable that renders the board (find it: `grep -rn "showAddTask" app/src`)

**Acceptance Criteria:**
- [ ] `BoardViewModel` exposes `showProtectedReview: StateFlow<Boolean>` and `protectedTasks: StateFlow<List<ProjectedItem>>`
- [ ] `ProtectedReviewScreen` lists all protected tasks with title, priority badge, bucket indicator
- [ ] Tapping "Unprotect" calls `viewModel.unprotectTask(pageId)` and the row disappears
- [ ] Closing the review (back button) checks if protected count is now below threshold; if so, posts encouraging message via `TopNotificationBar`
- [ ] `./gradlew :app:assembleDebug` compiles without errors
- [ ] [ui-auto] Protected review screen is shown when `showProtectedReview = true`

**Verify:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL; [ui-auto] and [manual] for correctness.

**Steps:**

- [ ] **Step 1: Add `showProtectedReview` and `protectedTasks` to `BoardViewModel.kt`**

```kotlin
val showProtectedReview = MutableStateFlow(false)

val protectedTasks: StateFlow<List<ProjectedItem>> = boardProjection
    .map { proj ->
        proj?.allPriorities
            ?.flatMap { priority -> priority.items }
            ?.filter { item -> item.localState?.isProtected == true }
            ?: emptyList()
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
```

Check the actual `BoardProjection` type and property names. `proj.allPriorities` may be named differently — search `grep -n "allPriorities\|priorities\|projectBoard" core/` to find the actual structure.

- [ ] **Step 2: Add `openProtectedReview()`, `closeProtectedReview()`, `unprotectTask()` to `BoardViewModel.kt`**

```kotlin
fun openProtectedReview() {
    showProtectedReview.value = true
}

fun closeProtectedReview() {
    // Post encouraging message if protected count is now below threshold
    val settings = boardSettings.value
    val total = /* total active task count — look for how inflationAlert computes this */
    val protectedCount = protectedTasks.value.size
    val percent = if (total > 0) (protectedCount * 100) / total else 0
    if (percent < settings.protectedInflationPercent) {
        postNotification(NotificationVariant.RefreshSuccess(encouragingMessage()))
    }
    showProtectedReview.value = false
}

fun unprotectTask(pageId: String) {
    viewModelScope.launch {
        // Use the same pattern as setPin — look for how protected flag is set
        // in the existing TaskWriteService or localStateRepository
        // Example (adjust to match actual API):
        localStateRepository.setProtected(pageId, mappingId, false)
    }
}
```

Check how the `isProtected` flag is currently toggled. Search: `grep -rn "isProtected\|setProtected" app/src --include="*.kt" | head -20`. Follow the same pattern.

- [ ] **Step 3: Create `ProtectedReviewScreen.kt`**

```kotlin
package com.ironclinicgym.sift.ui.board

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironclinicgym.sift.core.board.BoardProjection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedReviewScreen(
    tasks: List<BoardProjection.ProjectedItem>,
    onUnprotect: (pageId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Protected tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No protected tasks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tasks, key = { it.task.pageId }) { item ->
                    ProtectedTaskRow(
                        item = item,
                        onUnprotect = { onUnprotect(item.task.pageId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ProtectedTaskRow(
    item: BoardProjection.ProjectedItem,
    onUnprotect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.task.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Priority badge — check existing badge composables in BoardComponents.kt
                // or create a simple colored chip with the priority display name
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        item.priorityDisplayName,  // adjust to actual property name
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                item.bucketName?.let { bucket ->
                    Text(bucket, style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onUnprotect) {
            Text("Unprotect")
        }
    }
}
```

Check the actual property names on `ProjectedItem` — `priorityDisplayName` and `bucketName` may differ. Search: `grep -n "data class ProjectedItem\|ProjectedItem(" core/src --include="*.kt" -r`.

- [ ] **Step 4: Render `ProtectedReviewScreen` in the host composable**

Find the host composable (same one that has `showAddTask` state). Add:

```kotlin
val showProtectedReview by viewModel.showProtectedReview.collectAsStateWithLifecycle()
val protectedTasks by viewModel.protectedTasks.collectAsStateWithLifecycle()

if (showProtectedReview) {
    ProtectedReviewScreen(
        tasks = protectedTasks,
        onUnprotect = { pageId -> viewModel.unprotectTask(pageId) },
        onBack = { viewModel.closeProtectedReview() },
        modifier = Modifier.fillMaxSize(),
    )
}
```

Render this block AFTER (on top of) the main board content so it acts as a full-screen overlay.

- [ ] **Step 5: Build and commit**

```bash
./gradlew :app:assembleDebug
```

```bash
git add app/src/main/java/com/ironclinicgym/sift/ui/board/ProtectedReviewScreen.kt \
        app/src/main/java/com/ironclinicgym/sift/ui/board/BoardViewModel.kt \
        # + host composable file
git commit -m "feat(ui): protected review screen with unprotect action and encouraging message on close"
```

---

## Self-Review

**Spec coverage check:**

| Spec item | Task |
|-----------|------|
| B3 two-state drawer | Task 3 |
| B4 date/time display + chip animation | Tasks 1, 3 |
| B5 recurrence picker sub-sheet | Task 4 |
| One-day landmarks in FocusedPriorityScreen | Task 5 |
| Safety catch (copy fix, Room tracking, VM trigger) | Tasks 1, 6 |
| Protected review screen | Task 9 |
| Signal Inflation nudge + encouraging messages | Task 8 |
| A6 SnackbarHost removal | Task 7 |
| B7 priority label | Tasks 1, 2 |
| A2 Change date/Change priority | Task 2 |

**Deferred (per spec — NOT to be implemented):**
- Brain dump review date (Phase 3.6)
- Customizable swipe direction assignments (Category C backlog)
- Personalized header (Category C backlog)
- Signal Inflation coach behaviors (Phase 7)

**Placeholder scan:** All steps include actual code. No TBD. No "similar to task N" references.

**Type consistency check:**
- `formatOrdinalDate()` defined in Task 1, used in Tasks 2, 3, 6 — consistent.
- `priorityLabelForTask()` defined in Task 1, called in Task 2 — consistent.
- `NotificationVariant.InflationNudge` defined in Task 8 (Step 4), used in Task 8 (Step 8) — consistent.
- `evaluateSafetyCatch()` defined and tested in Task 6 — consistent.
- `quickDateLaterToday/Tomorrow/NextWeek/NextMonth` defined in Task 1, used in Task 3 — consistent.
- `DraftState` extended in Task 3 (Step 1) — `dateIso` added before it's used in Steps 3 and 4 — consistent.
- `onSaved` callback added to `AddTaskSheetV2` in Task 3 (Step 2), wired in Task 3 (Step 7) — consistent.

**Dependency ordering note for execution:**
- Task 1 should complete before Tasks 2, 3, and 6 (all use `formatOrdinalDate`).
- Task 7 should complete before Task 8 (Signal Inflation uses `postNotification` added in Task 7).
- Task 7 should complete before Task 9 (encouraging message uses `postNotification`).
- Tasks 3 and 4 share `AddTaskSheetV2.kt` — do them sequentially (3 before 4).
- All other tasks are independent.

**Manual items for BJ's review (cannot be verified by tests):**
- [manual] Minimized bar sits at the same vertical position as the "Add task to board" button
- [manual] Chip collapse animation feels smooth at ~200ms
- [manual] Priority preview ("→ Soon") appears only inside the date picker, not after confirmation
- [manual] Recurrence sub-sheet is compact and intuitive
- [manual] Landmarks appear in correct order and items land in the right group
- [manual] Safety catch prompt is non-intrusive and dismissible
- [manual] SafetyCatchDialog copy reads naturally without em dashes or hyphens
- [manual] "Change date" on a dated task opens the redirect prompt with all four options
- [manual] Encouraging message feels natural, not robotic
