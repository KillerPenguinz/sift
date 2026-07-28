# Phase 3.5 gap tickets and sign-off — design

Date: 2026-07-27
Tickets: T-006, T-008, T-009 (build), then T-005 (UAT round 3 + sign-off)
Phase: p35 (Two Axis Model)

## Goal and sequencing

Phase 3.5's core Two Axis Model is built and had one round of on-device UAT fixes.
Three spec'd-but-unbuilt gap items remain, each part of Phase 3.5's own acceptance
criteria. Decision: **build the three gap tickets first, then run one UAT round 3
over the now-complete phase, then a real Phase 3.5 sign-off.** This means signing
off a finished phase and testing it once, rather than signing off an incomplete
phase and re-testing later.

Build order: T-006, T-008, T-009 (mutually independent), then T-005.

## Shared approach

There is no architectural fork. All three follow patterns already in the codebase;
the real decisions were UX ones, recorded below. Each slots into an existing seam:

- Blocked reuses the local-flag pattern of `isProtected` / `isBrainDump`.
- Both settings follow the existing chain:
  `BoardSettings` (per-mapping DataStore blob) -> `BoardSettingsEdits`
  -> `CustomizeViewModel` -> `SettingsScreen`. New JSON fields decode to their
  defaults for existing users on upgrade (the documented forward-compat pattern).

Standing constraints (from CLAUDE.md) that apply throughout: no em dashes or
hyphens in user-facing text; token discipline (no hardcoded colors);
`collectAsStateWithLifecycle`; `navigationBarsPadding` on bottom UI; all reads and
writes go through the stored mapping.

## Recorded UX decisions

1. **T-006 blocked entry:** task detail toggle only, plus a board card indicator.
   No swipe. (Both board swipe directions are already committed: right = complete,
   left = snooze.) A board swipe for blocked is deferred to backlog.
2. **T-008 band editor:** edit the boundaries only. In practice about two steppers,
   "Soon: up to N days" and "Later: up to M days"; the definitional overdue / today
   / tomorrow bands stay fixed. No gaps or overlaps possible by construction.
3. **Sign-off:** Phase 3.5 sign-off comes after the three builds and is gated on
   BJ's on-device pass of the UAT round 3 script.

## Component 1 — T-006: Blocked flag UI

**Data layer: no changes.** The full round-trip already exists:
`SiftTask.isBlocked` (core) -> `TaskLocalState` port -> Room column + migration
(`ALTER TABLE task_local_state ADD COLUMN isBlocked`) -> repo merge in
`SiftRepository.activeTasks` -> `BoardViewModel.toggleBlocked(pageId)`. `isBlocked`
is purely local operational state with no Notion mapping (mirrors `isProtected`).

**Build:**
- A **Blocked toggle in the task detail drawer** (`TaskDetailSheet`), among the
  secondary / expanded actions, calling `toggleBlocked(pageId)`. Mirrors how
  Protected is surfaced.
- A **small blocked indicator on the board task card** when `isBlocked` is true:
  a token-colored Material Symbol (candidate: `block` or `front_hand`).
- **Recurrence:** blocked must not carry to future occurrences (spec section 12).
  Because it is local state keyed to the page ID and each occurrence is a new page,
  this is satisfied with no special handling.

**Watch-out:** if the indicator uses a Material Symbol glyph not already in the
subset font, it needs both a codepoint entry and a regenerated subset font, or it
renders as tofu. Prefer an already-subsetted icon; otherwise include the font-regen
step.

**Label:** "Blocked".

**Tests:**
- [logic] toggle flips and persists through the local-state store.
- [ui-auto] toggle present in detail; indicator renders when `isBlocked` is true and
  is absent otherwise.
- [manual] indicator looks correct on the card in light and dark mode.

## Component 2 — T-008: Date bands to Settings (boundaries only)

**Feature recap:** a due date auto-assigns a task's priority via date bands
(overdue -> asap, today -> today, tomorrow -> tomorrow, ~2-7d -> soon,
~8-30d -> later, 30+d -> one day). The spec requires these be user-adjustable in
settings; today the app always uses the hardcoded `DateBandConfig.DEFAULT`.

**Core:** add a pure builder `DateBandConfig.fromBoundaries(soonMaxDays, laterMaxDays)`
in `DateBandEngine.kt`. It keeps overdue / today / tomorrow fixed and moves only the
Soon and Later end lines. It clamps so `laterMaxDays > soonMaxDays >`
(the tomorrow boundary), producing a valid, gap-free, overlap-free config.

Mapping to the existing band model (`rangeDaysEnd` exclusive): default bands are
SOON 2..8 (up to 7 days) and LATER 8..31 (up to 30 days). So `soonMaxDays = 7` maps
to SOON end 8, and `laterMaxDays = 30` maps to LATER end 31; LATER start tracks
SOON end.

**Settings storage:** two new `BoardSettings` ints, `soonMaxDays = 7` and
`laterMaxDays = 30`, with clamped `setSoonMaxDays` / `setLaterMaxDays` extensions in
`BoardSettingsEdits.kt` and matching `CustomizeViewModel` methods.

**Settings UI:** a "How dates set priority" section in `SettingsScreen.kt` with two
steppers: "Soon: up to N days" and "Later: up to M days" (reuse the existing stepper
component).

**Wiring:** `BoardViewModel` (currently `TwoAxisPolicy()` at line 98, hardcoded
default) builds `TwoAxisPolicy(config = DateBandConfig.fromBoundaries(...))` from the
live `BoardSettings`, and rebuilds the policy when settings change so edits take
effect without an app restart.

**Tests:**
- [logic] `fromBoundaries` produces the correct bands; `resolveBand` with a tuned
  config returns the tuned priorities; clamping rejects/repairs invalid input
  (later <= soon).
- [ui-auto] both steppers present in Settings.

## Component 3 — T-009: Quick-date defaults to Settings

**Current state:** defaults are hardcoded in `TimeFormat.kt` — later today = now + 4h,
tomorrow = 8:00, next week = Sunday 8:00, next month = 1st of next month 8:00, with a
"not-yet-wired" comment on next week.

**Settings storage:** new `BoardSettings` fields matching the Round-2 tagged criteria:
default time for **tomorrow**, **next week**, **next month** (each defaulting to 8:00),
and **first day of week** (default Sunday). `use24HourTime` already exists and is
unchanged. Times stored as minute-of-day (or hour/minute) ints.

**Core:** thread these into `TimeFormat.quickDateTomorrow` / `quickDateNextWeek` /
`quickDateNextMonth` as parameters, removing the not-yet-wired comment.
`quickDateNextWeek` uses the configured first day of week.

**Consumption:** `AddTaskSheetV2` passes settings-derived values into the
"Later today / Tomorrow / Next week / Next month" quick-date chips.

**Scope note:** the "Later today = +4h" offset is not in the Round-2 settings list,
so it stays hardcoded and is flagged as a possible follow-up rather than expanding
scope now.

**Tests:**
- [logic] each quick-date function honors its configured time; next-week uses the
  configured first day of week (Round-2 acceptance line: "first day of week setting is
  used by the Next week quick-date chip").
- [ui-auto] the four settings controls present.

## Component 4 — T-005: UAT round 3 and sign-off

T-005 is a [manual] ticket; on-device UAT is BJ's to run. The deliverables here are:

1. A single consolidated on-device UAT script at `docs/PHASE_3_5_UAT_round3.md`,
   assembled from: spec section 16 acceptance criteria, section 19 device checklist,
   Round-1's tagged [manual] items, and the three new features above.
2. All [logic] and [ui-auto] criteria that Claude Code owns run and reported with
   pass/fail counts (`./gradlew :core:test :app:testDebugUnitTest` plus Compose UI
   tests).

Phase 3.5 sign-off (marking T-005 complete and p35 done) is gated on BJ's device
pass of the script.

## Assumptions (flagged, not decided silently)

1. The three per-action default times are stored as three separate settings to match
   the Round-2 criteria literally, even though all default to 8:00. Could be collapsed
   into one shared "default task time" if preferred.
2. Settings are per-connected-database, because `BoardSettings` is already keyed to
   the mapping id. Band and time preferences are therefore scoped to the mapping, not
   global.

## Out of scope

- T-007 (self-quieting Added-to-priority chip) — the fourth Phase 3.5 gap ticket,
  deliberately not in this batch.
- Configurable "Later today" offset.
- Board swipe gesture for blocked (backlog).
