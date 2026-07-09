# Phase 3.5 Handoff to Claude Code (Two Axis Model)

> A self contained handoff for Claude Code to plan and build Phase 3.5 of Sift, the Two Axis Model. Everything Claude Code needs is on this page. Source of truth remains the Two Axis Model feature concept page and the ADR; this is an assembled export for the build. No em dashes or hyphens in any user facing text the app produces.
> 

## 0. Context: what Phase 3.5 is and what it builds on

Phase 3 (the plain write layer) is built: add, edit, remove, complete, move, recurrence, snooze, undo. Phase 3.5 layers the Two Axis Model on top: dates auto assign priorities, a redesigned add-task flow, pinning as visibility, the Protected flag, the brain dump as a separate space, and the redirect model for dated task moves. This phase makes Sift intelligent rather than just writable.

The current Phase 3 add-task UI (where the user picks a priority directly) is REPLACED by this phase. The current plain drag-to-move behavior is WRAPPED by this phase's redirect logic for dated tasks.

## 1. Terminology (unchanged, post refactor)

- **Priority** = urgency dimension (asap, today, tomorrow, soon, later, one day). Code: PRIORITY / PRIORITY_META.
- **Bucket** = life area dimension (Work, Personal). Code: BUCKET / BUCKETS.
- Bucket is optional for BYO users.
- Brain dump = a separate space for uncommitted capture, NOT a priority tier.

## 2. The Two Axes

Sift has two independent notions of when:

1. **Priority** (asap, today, tomorrow, soon, later, one day): relative, fuzzy urgency. What should I look at next?
2. **Due date and time** (optional): absolute schedule. When must this actually happen?

The key link: a due date AUTO ASSIGNS a default Priority via date bands. The priority ladder is self managing for dated tasks. Undated tasks are placed by rough urgency ranges chosen at creation.

## 3. Default date to Priority bands (adjustable in settings)

- overdue, or due today with little time left -> asap
- due today -> today
- due tomorrow -> tomorrow
- due in about 2 to 7 days -> soon
- due in about 8 to 30 days -> later
- due 30+ days out -> one day (the calm floor; auto climbs as the date nears)
- no date -> NOT on the ladder; placed by the user's chosen rough range, or in the brain dump

These bands must be stored as configurable defaults the user can adjust in settings.

## 4. The redesigned add-task flow

The user NEVER picks a priority during creation. Progressive disclosure: three steps by default, everything else collapsed.

**Step 1 (always): What needs doing?**

A text field for the title. Big, prominent, first. Dictation works via platform keyboard voice input (free, never gated).

**Step 2 (if bucket is mapped): Which bucket?**

Bucket pills with configured color and icon. Tapping one applies the bucket's accent color as a subtle accent on the view (pill highlight, borders). If no bucket property is mapped, skip this step.

**Step 3: When?**

Three paths:

- **It has a date** -> opens the date picker. Optionally add a time via a clock icon affordance (no default time shown; time is additive, not assumed). The date auto assigns the priority via the bands. **Date picker priority preview:** as the user selects a date, a small non-intrusive indicator shows which priority band it falls into BEFORE they confirm (for example Next Tuesday -> Soon). This makes the system visible during creation and builds trust.
- **Rough range (soon-ish / later / whenever)** -> for tasks without a hard date. These map to priority tiers: soon-ish -> soon, later -> later, whenever -> one day. Presented in human language, not system language. The user never sees asap/today/tomorrow during creation.
- **Just a thought** -> sends to the brain dump (separate uncommitted capture space). Optionally with a remind me to revisit on [date] affordance for a review date (this is a REVIEW date that triggers a Loose Ends nudge, not a due date that places the item on the ladder).

**Collapsed by default (More options):** notes, labels, recurrence, protected flag. Not visible unless tapped.

**After saving:**

- The add sheet STAYS OPEN for rapid-fire adding.
- Behind the sheet, the task visually drops/slides into the correct priority bucket on the board. The board scrolls to show the bucket if needed. The task glows briefly with the bucket's accent color. Animation: ~250 to 300ms, directional (toward the bucket's position), same easing family as the existing shared-element transitions, skippable (new animation interrupts previous).
- A small non-blocking chip at the bottom of the SHEET (not the board): Added to Soon. Change? Tapping Change opens a quick picker. Ignoring it, it fades. Each new save replaces the previous chip (never stacks).
- When the user closes the sheet, all newly added tasks glow briefly in their buckets.

**Self-quieting chip:**

- First several uses: Added to [Priority]. Change?
- After ~10 to 15 tasks where the user never tapped Change: shortens to a small indicator (-> Soon)
- Always available in settings to restore or turn off
- Reads behavior: never corrected = trusts the system, gets out of the way

## 5. The brain dump (separate space)

A separate space for uncommitted capture, NOT a priority tier. Items here are considerations (might do, might delete, might ponder), not commitments.

- Only the user deliberately places things here (via Just a thought in the add flow)
- Dates never auto-route here; a date makes something a commitment on the ladder
- A brain dump item CAN have a review date (remind me to revisit), which triggers a Loose Ends nudge on that date, NOT a priority placement
- The coach (Phase 7) mines the brain dump (research it, help start it, ask probing questions)
- The brain dump needs a dedicated, reachable area in the app UI (a tab, a section, a swipe target), not buried in settings

## 6. Drag behavior (the simplified model)

**Dated tasks CANNOT be dragged between priorities.** The date IS the priority. If the user tries to drag a dated task, gently redirect with a non-blocking prompt:

This task is in [priority] because it is due [date]. Want to:

- **Change the date** -> opens date picker; new date auto assigns new priority
- **Snooze it** -> pushes the date back (same as swipe-to-snooze)
- **Pin it** -> keeps it visible in the Pinned section; priority stays where the date put it
- **Remove the date** -> task becomes undated; user picks a rough range or it goes to ASAP

Each choice is an immediate action. The coach reads which path the user chose as behavioral signal (reschedule count, pin events, etc).

**Undated tasks CAN be dragged freely between priorities.** No date to conflict with. Dragging is just reorganizing rough estimates. No redirect, no prompt.

**ASAP is the special undated urgent tier.** No date required. It means I need this NOW, I just do not know when. The coach targets ASAP items sitting more than a day or two (can I help make this happen?).

## 7. Pinning (VISIBILITY, not priority override)

Pinning is purely about visibility. It does NOT change a task's priority.

A pinned task keeps its natural priority internally but ALSO appears in a **Pinned section at the top of the board**, above ASAP. It is not IN asap; it is in its own focused area.

**Layout:**

- Full expanded view: Pinned section is the first thing above ASAP
- Minimized view: **Pinned + ASAP + Today** (three rows)
- If nothing is pinned, the section does not appear (zero clutter)
- Each pinned task shows its real priority as a small colored indicator (dot or tag matching its priority bucket's color)

**Pin cycle (three states, one gesture):**

- **Unpinned** (default): task lives only in its priority bucket
- **Pinned** (tap pin): this occurrence appears in the Pinned section. For recurring tasks, the next occurrence enters unpinned. Visual: pin icon.
- **Persistently pinned** (tap pin again on an already-pinned recurring task): every future occurrence regenerates pinned. Visual: pin with a small recurrence indicator (circular arrow). Standing instruction: every time this comes back, keep it visible.
- Tapping again on a persistently pinned task: unpins completely (back to unpinned). Cycle: unpinned -> pinned -> persistently pinned -> unpinned.

When unpinned, the task slides to its natural priority position with the animation and highlight, so the user sees where it went.

## 8. The Protected flag

Distinct from pinning. Protected guards against DEPRIORITIZATION (moving down the urgency ladder or snoozing past a safe point). Pinning controls VISIBILITY. A task can be both.

**Behavior: firm friction, not a wall.**

- For DATED tasks: Protected fires when the user tries to SNOOZE (push the date back). You marked this as important. Sure you want to push it back? Bends if the user means it.
- For UNDATED tasks: Protected fires when the user tries to DRAG it to a lower priority. Same prompt.
- Protected tasks carry a visible marker.
- Name: Protected (Protect this).
- Protected flag CARRIES to all future occurrences of a recurring task (standing policy).

**Guards against overuse (Signal Inflation):** too many Protected tasks triggers a gentle free-tier nudge. See Signal Inflation below.

## 9. Safety catch

Fires when a task's DATE becomes genuinely imminent, regardless of other state.

**Threshold: asap + today.** When a dated task's due date reaches today territory (one day of warning before the deadline), the app surfaces it gently: This one is due tomorrow. Want a hand with it, or should we find it a better spot? Two constructive doors, never a scold.

Since pinning no longer moves priority and dated tasks cannot be dragged, the safety catch mainly applies to tasks that were created with a far-future date and have auto-climbed to imminence. It is the system saying: this one just became real.

## 10. Signal Inflation (keeping the tools meaningful)

Free-tier threshold-based nudges that fire when tools are overused:

- **ASAP inflation:** configurable threshold (default ~5 items in asap at once, or a percentage). Nudge: You have [N] things in asap. Want to thin it out?
- **Protected inflation:** configurable (default ~30% of all tasks marked Protected). Nudge: That is a lot of Protected tasks. Want to review them?
- Users can adjust the percentage in settings OR switch to a hard number.
- Can turn it off entirely. When they do, a gentle, non-blocking, one-time note: this helps keep things manageable, but it is your call.
- Thresholds should scale with total task volume.
- The coach (Phase 7) interprets the PATTERNS (not just counts).

## 11. Visual landmarks within the one-day floor

Because one day holds committed low-urgency items across a huge date range, it uses soft visual separation:

- **This month** (due within 30 days)
- **Next 90 days** (31 to 90)
- **Later this year** (91 to 365)
- **Beyond** (365+)
- **No date** (undated committed intentions; the Loose Ends nudge targets these)

Landmark set is adjustable in settings. Items visibly migrate upward through landmarks over time.

## 12. Recurrence interaction

- Next occurrence auto assigns its priority from its new due date via the bands (enters the ladder fresh).
- **Pin:** follows the three-state cycle. Single pin = this instance only. Persistent pin = every occurrence.
- **Protected:** carries to all future occurrences (standing policy).
- **Blocked status:** does NOT carry (it was about this instance being stuck).

## 13. Storage (unchanged from Phase 3 decisions)

- **App state** (pin state, protected flag, reschedule counts, blocked status, Signal Inflation dismissals, chip quiet-level): Sift local storage (Room), keyed to Notion page ID. Zero Notion columns.
- **User content** (notes for a task): the task's Notion page body in a structured Sift section, or an existing mapped notes field.
- **Only recurrence** as real queryable Notion fields (with Sift prefix and consent).

## 14. Blocked / waiting status

A status flag available on any task anytime (from task detail or a swipe), independent of dragging. Marks a task as blocked with a visible indicator. The coach reads this as signal. In v1, this is just a flag, not a real sub-task tree. Real sub-tasks are parked as a future feature.

## 15. Non-goals for Phase 3.5

- No AI coach (Phase 7). The free-tier behaviors (redirect prompt, Signal Inflation nudges, Loose Ends space) capture signal the coach will later consume.
- No reminders/notifications system (own scope). The safety catch is an IN-APP prompt, not a push notification.
- No real sub-tasks (parked future feature). Blocked is a status flag only.
- No Daily Briefing Space (own feature concept, needs scoping).
- No gamification (future).

## 16. Acceptance criteria

- [ ]  The add-task flow presents three steps: title, bucket (if mapped), and when (date with priority preview / rough range / brain dump). The user never picks a priority label during creation.
- [ ]  Setting a date auto assigns the task to the correct priority via the configured bands. The date picker shows a live priority preview as the user selects a date.
- [ ]  Rough range options (soon-ish, later, whenever) map to their corresponding priority tiers and the user sees human language, not system labels.
- [ ]  Just a thought sends the item to the brain dump space, with an optional review date.
- [ ]  The add sheet stays open after saving. The task animates into its priority bucket on the board behind the sheet. A non-blocking chip shows where it landed with a Change option.
- [ ]  The chip self-quiets after ~10 to 15 uncorrected saves (shortens, then can be turned off in settings).
- [ ]  Dragging a DATED task between priorities is intercepted with the redirect prompt (change date / snooze / pin / remove date). Each option performs its action immediately.
- [ ]  Dragging an UNDATED task between priorities works freely with no prompt.
- [ ]  The Pinned section appears at the top of the board (above ASAP) when any task is pinned, and disappears when nothing is pinned.
- [ ]  The minimized view shows Pinned + ASAP + Today (three rows).
- [ ]  Pinned tasks show their real priority as a small colored indicator.
- [ ]  The pin cycle works: unpinned -> pinned -> persistently pinned -> unpinned. Persistent pin shows a recurrence indicator. Unpinning animates the task to its natural priority position.
- [ ]  The Protected flag shows firm friction on snooze (dated tasks) or drag-down (undated tasks). Protected carries to recurring task occurrences.
- [ ]  The safety catch fires when a dated task reaches asap or today territory, with a gentle, constructive prompt.
- [ ]  Signal Inflation nudges fire at configurable thresholds (default ~30% Protected, ~5 in ASAP). Users can adjust, switch to hard numbers, or turn off with a gentle one-time note.
- [ ]  Visual landmarks appear within the one-day priority (This month / Next 90 / Later this year / Beyond / No date).
- [ ]  The brain dump is a visually distinct, reachable area in the app (tab, section, or swipe target), separate from the priority ladder.
- [ ]  Recurrence: next occurrence enters auto-priority from its new date, pin follows the three-state cycle, Protected carries, blocked does not carry.
- [ ]  All new state (pin, protected, counts, blocked) stored in local Room storage keyed to page ID, NOT as Notion columns.
- [ ]  Token discipline: all new UI elements reference design tokens, never hardcoded colors.
- [ ]  No em dashes or hyphens in user facing text.

## 17. The planning prompt

```
I am working on Sift, an Android app in Kotlin with Jetpack Compose. Phases 0 through 3 are built (theming, onboarding, the priority grid, and the plain write layer). Here is the FINAL SPEC for Phase 3.5, the Two Axis Model. It is in the repo at docs/PHASE_3_5_SPEC.md. Read it.

Please:
1. Inspect the existing project, especially the add-task UI, the drag-to-move logic, the board rendering, and the priority assignment in the domain layer.
2. Propose a concrete implementation plan that aligns with the project as it exists now.
3. List files to create, files to modify, and what changes at a high level.
4. Call out conflicts, missing pieces, or concerns.

Rules:
- The add-task flow is REPLACED: the user never picks a priority. They pick a date (auto-assigned via bands), a rough range, or brain dump. The current priority picker is removed.
- Dated tasks CANNOT be dragged between priorities. Intercept the drag and show a redirect prompt (change date / snooze / pin / remove date). Undated tasks CAN be dragged freely.
- Pinning is VISIBILITY ONLY: a separate Pinned section at the top of the board. It does NOT change priority. Three-state cycle for recurring tasks.
- The Protected flag guards against deprioritization (snooze for dated, drag-down for undated). Firm friction, not a wall.
- The brain dump is a SEPARATE space in the UI, not a priority tier.
- All new app state (pin, protected, counts, blocked) lives in Room keyed to page ID, NOT as Notion columns.
- Keep the existing write layer intact. This phase wraps and extends it, not replaces it.
- Token discipline: all new UI references design tokens.
- No em dashes or hyphens in user facing text.

Output format:
# Implementation Plan
## 1. Overview
## 2. Files to Create
## 3. Files to Update
## 4. Notes / Concerns
```

## 18. The validation prompt

```
Review the Phase 3.5 code against the spec and the rest of the project.
1. Confirm the add-task flow presents date / rough range / brain dump, never a priority picker.
2. Confirm the date picker shows a live priority preview based on the configured bands.
3. Confirm dragging a dated task shows the redirect prompt and does NOT move the task between priorities.
4. Confirm dragging an undated task works freely.
5. Confirm the Pinned section appears above ASAP when items are pinned and disappears when empty.
6. Confirm the minimized view shows Pinned + ASAP + Today.
7. Confirm the pin three-state cycle works (unpinned, pinned, persistently pinned) with correct visuals.
8. Confirm Protected fires on snooze (dated) and drag-down (undated) with a friction prompt.
9. Confirm the safety catch fires at the asap/today threshold with a gentle prompt.
10. Confirm Signal Inflation nudges are configurable and fire at thresholds.
11. Confirm one-day landmarks render correctly.
12. Confirm the brain dump is a separate, reachable UI area.
13. Confirm all new state is in Room, not Notion columns.
14. Confirm token discipline (no hardcoded colors).
15. Point out remaining TODOs or risky assumptions.
```

## 19. Things to verify on device (for BJ)

- Add a task with a date and confirm it lands in the correct priority automatically. Watch the date picker priority preview.
- Add a task with a rough range and confirm it lands in the right priority.
- Add a brain dump item and confirm it appears in the brain dump space, not on the ladder.
- Try to drag a dated task and confirm the redirect prompt appears.
- Drag an undated task and confirm it moves freely.
- Pin a task and confirm it appears in the Pinned section. Unpin and confirm it slides back.
- On a recurring task, pin once (single instance), then pin again (persistent). Complete the task and confirm the next occurrence respects the pin state.
- Mark a task Protected, then try to snooze (dated) or drag down (undated). Confirm the friction prompt.
- Let a far-future dated task's date reach today and confirm the safety catch fires.
- Add enough tasks to ASAP or mark enough as Protected to trigger the Signal Inflation nudge.
- Check the one-day priority for the visual landmarks.
- Rapid-fire add several tasks and confirm the sheet stays open, the chip replaces itself, and the animations play behind the sheet.