# Phase 3.5 UAT Round 3 and Sign-off

On-device acceptance script for BJ. Build and install with:

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:installDebug
```

Each item is tagged `[logic]` (Claude runs the automated test), `[ui-auto]` (Compose element assertion where instrumentation exists), or `[manual]` (BJ verifies on device: visual, feel, real hardware). Claude owns `[logic]`/`[ui-auto]`; the `[manual]` items below are yours. This round covers the full Phase 3.5 surface plus the three gap features shipped in this batch (T-006 blocked flag, T-008 configurable date bands, T-009 configurable quick-date defaults).

Standing rules to watch for throughout: no em dashes or hyphens in any user-facing text; colors always look right in both light and dark mode.

---

## 1. Add-task flow (spec section 16, Round-1 D-items)

- [ ] [manual] The FAB (+) appears bottom-right, above the bottom tabs and system nav, on both board and brain dump views.
- [ ] [manual] Tapping the FAB opens the add-task drawer. Compact state (keyboard up) shows only the title field and the Task / Brain dump toggle. Swiping up expands to the full form and dismisses the keyboard.
- [ ] [manual] The full form shows title, toggle, compact bucket icons, the WHEN section, and "Add task to board". Swiping up further reveals notes and recurrence.
- [ ] [manual] The user never picks a priority label during creation. Paths are: pick a date, a rough range (soon-ish / later / whenever), or Brain dump.
- [ ] [manual] When a date is selected, the rough-range pills collapse away and "Sift handles the priority. Adjust anytime." appears. Removing the date reverses the animation.
- [ ] [manual] After saving, the add sheet stays open for rapid-fire adding; the task animates into its bucket on the board behind the sheet.
- [ ] [manual] Add a task with a rough range and confirm it lands in the matching priority.
- [ ] [manual] Add a Brain dump item and confirm it appears in the brain dump space, not on the ladder.
- [ ] [logic] Saving with no date and no rough range defaults the task to ASAP. (Covered by existing add-flow logic tests.)
- [ ] [manual] Note: the self-quieting "Added to [priority]. Change?" chip (T-007) is NOT in this batch; do not expect the chip to shorten yet.

## 2. Drag and redirect (spec section 16)

- [ ] [manual] Dragging a DATED task between priorities shows the redirect prompt (Change the date / Snooze / Pin / Remove the date). Each option acts immediately.
- [ ] [manual] Dragging an UNDATED task between priorities works freely with no prompt.

## 3. Pinning, Protected, safety catch, Signal Inflation (spec section 16, Round-1 B-items)

- [ ] [manual] The Pinned section appears above ASAP when any task is pinned, and disappears when nothing is pinned. Minimized view shows Pinned + ASAP + Today.
- [ ] [manual] Pin cycle works: unpinned then pinned then persistently pinned (recurrence indicator) then unpinned. Unpinning animates the task to its natural priority.
- [ ] [manual] The pin icon renders as a pin graphic (not stray text).
- [ ] [manual] Protected shows firm friction on snooze (dated) or drag-down (undated), and carries to recurring occurrences.
- [ ] [manual] Safety catch fires when a dated task reaches asap/today territory with a gentle prompt.
- [ ] [manual] Signal Inflation nudges fire at the configured thresholds; the Settings > Signal Inflation controls adjust them.

## 4. Blocked flag (NEW, T-006)

- [ ] [logic] Toggling blocked persists through local Room state, keyed to page id; blocked does NOT carry to a recurring task's next occurrence (new occurrence is a new page). (Data round-trip has existing coverage.)
- [ ] [ui-auto] The task detail drawer shows a "Mark blocked" / "Blocked" control in the secondary actions; the board row renders a "BLOCKED" tag when the task is blocked. (Assertable where Compose instrumentation exists.)
- [ ] [manual] Open a task's detail drawer and tap the blocked control. Its label flips to "Blocked" and back on repeated taps, and the state matches after reopening the drawer.
- [ ] [manual] A blocked task shows a small "BLOCKED" pill on its board row, alongside (not replacing) the overdue tag or time. It reads correctly in light and dark mode.
- [ ] [manual] Rapidly tapping the blocked control several times leaves the persisted state matching the final on-screen state (no stuck-wrong value).

## 5. Configurable date bands (NEW, T-008)

- [ ] [logic] `DateBandConfig.fromBoundaries` produces correct bands and re-buckets dates; clamping keeps Soon < Later with no gaps. (See `DateBandBoundariesTest`.)
- [ ] [manual] Settings > Priority timing shows "Soon: up to N days" and "Later: up to N days" steppers.
- [ ] [manual] After tightening "Soon" (e.g. to 3 days), add a NEW task dated 5 days out and confirm it lands in Later rather than Soon. (Existing dated tasks are not re-placed; see Section 7.)
- [ ] [manual] Scope toggle "Use across all databases" is ON by default. With it ON, a band edit applies to every connected database. Turn it OFF for one database, change a band, and confirm that database diverges while the others keep the global values. Turn it back ON and confirm the database returns to the global values.
- [ ] [logic] The scope router (`routePriorityEdit`) sends edits to the global store under global scope and to the per-database override otherwise; malformed persisted values are normalized. (See `PrioritySettingsTest`.)

## 6. Configurable quick-date defaults (NEW, T-009)

- [ ] [logic] Tomorrow / Next week / Next month land at the configured local time; Next week uses the configured first day of week; parse-failure fallbacks honor the configured time. (See `QuickDateTest`.)
- [ ] [manual] Settings > Priority timing shows time rows for Tomorrow / Next week / Next month and a "First day of week" row. Times display in your clock format (respecting the 24 hour setting).
- [ ] [manual] Change "Tomorrow time" to, say, 6:00 AM. In the add sheet, tap the Tomorrow quick-date chip and confirm the task is scheduled tomorrow at 6:00 AM local.
- [ ] [manual] Change "First day of week" to Monday. Tap the Next week chip and confirm it targets the next Monday.
- [ ] [manual] The "Later today" chip still lands roughly 4 hours out (this offset is intentionally not configurable in this batch).

## 7. Known failing spec criteria (do not sign off full Phase 3.5 until closed)

Two Phase 3.5 spec criteria were confirmed UNIMPLEMENTED by code inspection during this batch's review. They are NOT regressions from this work, and this batch does not attempt them. They are tracked as issues; full Phase 3.5 sign-off is BLOCKED until both are closed.

- [ ] [manual] ISS-001 (self-managing dated ladder): a dated task's priority should auto-climb as its date nears, and changing the bands should re-place existing dated tasks. Today, priority is assigned only at task creation. Expect this to FAIL on device; do not treat it as passing.
- [ ] [manual] ISS-002 (in-picker band preview): the date picker should show which priority band the selected date falls into while selecting. Today there is no live in-picker preview. Expect this to FAIL on device.

(A related low-severity concurrency hardening item for local-state writes is tracked as ISS-003; the blocked field itself is serialized and idempotent in this batch.)

## 8. Round-1 [manual] regressions to re-check

- [ ] [manual] Light mode status bar has dark, readable text.
- [ ] [manual] Bottom tabs sit fully above the system navigation bar (gesture and 3-button nav).
- [ ] [manual] The top notification bar slides in below the header, auto-dismisses in 3 to 5 seconds, and can be swiped away; it never stacks.
- [ ] [manual] Board and brain dump tab icons are visually distinct.
- [ ] [manual] The "View in Notion" affordance uses a generic external-link icon, not the Notion logo.

## 9. Claude-owned test results (this batch)

Run on 2026-07-27 from the worktree with the Android Studio JBR as JAVA_HOME:

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

- `:core:test` — BUILD SUCCESSFUL, 184 tests, 0 failures, 0 errors. Includes the new `DateBandBoundariesTest` (4), `PrioritySettingsTest` (10), and `QuickDateTest` (5).
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 9 tests, 0 failures, 0 errors (includes `BoardIconsFontTest`, confirming no missing/tofu glyph; the blocked UI reuses the existing `event_busy` glyph and adds no codepoint).
- `:app:assembleDebug` — BUILD SUCCESSFUL, app-debug.apk produced (~16 MB).

All Claude-owned `[logic]` criteria pass. `[ui-auto]`/`[manual]` items above are for on-device verification.

**Sign-off status:** Phase 3.5 sign-off (marking T-005 complete and phase p35 done) is pending BJ's on-device pass of the `[manual]` items in sections 1 to 6 and 8. Full sign-off additionally requires closing ISS-001 and ISS-002 (section 7).
