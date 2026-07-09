PHASE 3.5 ROUND 1 UAT FIXES — DESIGN SPEC
Date: 2026-07-09
Status: Approved, ready for implementation planning

Standing rules: token discipline (no hardcoded colors), no em dashes or hyphens in user-facing text.

This spec covers all outstanding items from the Phase 3.5 Round 1 UAT bug/UX document plus three features confirmed in scope during the 2026-07-09 brainstorm session: safety catch, Signal Inflation nudges, and one-day landmarks. Work is organized into four component groups matching the approved implementation approach.

Decisions logged in Notion: https://app.notion.com/p/395e3d39a445801ba739fee76f3f8ba4

---

## Scope summary

Items already verified as implemented (no work needed):
- A1 Brain dump routing
- A3 Pin button (three-state cycle)
- A4 Light mode status bar dark icons
- A5 Bottom tabs nav bar insets
- B1 Brain dump Task/Brain dump toggle
- B2 TopNotificationBar composable
- B6 Swipe actions colored background + text label
- B8 Distinct tab icons
- B9 Notion external-link icon
- B10 Refresh compact time-ago label + spin animation

Items in scope for this batch (four groups below):
- A2 Move/redirect never triggers from UI
- A6 Old SnackbarHost coexists with TopNotificationBar
- B3 No minimized drawer state
- B4 Date/time format wrong, collapse animation missing
- B5 Recurrence picker not redesigned
- B7 Priority label shows band name not actual date
- NEW Safety catch
- NEW Signal Inflation nudges (ASAP + Protected)
- NEW One-day landmarks

Deferred:
- Brain dump review date (Loose Ends feature, needs own spec — Phase 3.6 candidate)
- Rough-range pills: intentionally removed. Undated committed tasks default to ASAP. User never picks a rough range.
- Customizable swipe direction assignments (Category C backlog)
- Personalized header (Category C backlog)
- Brain dump categories/tags (Category C backlog)

---

## Group 1: Add-task drawer

### B3 — Two-state drawer

The drawer has two states:

MINIMIZED (default after saving a task): shows only the title field ("What needs doing?") and the Task/Brain dump toggle. The title field sits at the same vertical position as the "Add task to board" button in the expanded state, so tapping into the minimized drawer feels like tapping the same spot. The TopNotificationBar shows "Added to [priority]" as the placement confirmation.

EXPANDED (on FAB tap, or when the user taps into the title field from minimized): shows the full form — title, Task/Brain dump toggle, bucket icons, date/time row, quick-date chips, and the "Add task to board" button. Swiping the drawer up further reveals the additional options area (notes, recurrence, Protected flag toggle, labels selector). No "More options" tap target; swipe-up is the only path to additional options. A subtle visual cue (small upward chevron or handle affordance) indicates more is available above.

DRAFT PRESERVATION: when the user dismisses the drawer via back button or swipe-down without saving, the current draft state (title text, bucket selection, date, type) is saved in memory and restored when the drawer is reopened. This is in-memory only — not persisted to disk. Cleared on process kill. After a successful save, no draft is retained.

Button label: "Add task to board" (distinguishes from the drawer header label).

### B4 — Date and time display

The "When" section replaces any sub-header text and pill buttons with a single calendar icon indicating an optional date. No label needed.

Once a date is selected:
- Display format: full month name, ordinal day, four-digit year. Example: "July 8th, 2026". Never "2026-07-08" or "Jul 8".
- A clock icon appears inline next to the date. Tapping it adds a time.
- Time display: 12-hour format by default ("6:00 AM", "8:24 PM"). If the user has 24-hour time enabled in Settings, show "06:00" or "20:24".
- Priority preview ("→ Soon") appears ONLY while the user is actively selecting a date inside the date picker. It disappears when the picker closes. It never shows on the form after a date is confirmed.
- The quick-date chips (Later today / Tomorrow / Next week / Next month) collapse away with a ~200ms animation when a date is set. In their place, a small muted helper line appears: "Sift handles the priority. Adjust anytime." Removing the date reverses the animation and restores the chips.

Quick-date chip defaults (configurable in Settings):
- Later today: today's date, time = now + 4 hours
- Tomorrow: tomorrow's date, time = 8:00 AM
- Next week: first day of configured week (default Sunday), time = 8:00 AM
- Next month: 1st of next calendar month, time = 8:00 AM

### B5 — Recurrence picker redesign

A repeat icon (circular arrow) sits inline in the date/time row, next to the date and time controls. It is only tappable when a date has been set (disabled when no date selected). Tapping it opens a compact sub-sheet within the drawer.

Sub-sheet layout:
1. Top: a plain-English summary sentence of the current rule, generated live from selections. Example: "Every 2 weeks on Tuesday and Thursday." "Every month on the 1st."
2. Below the summary, the controls that build it:
   - Frequency: Daily / Weekly / Monthly / Yearly (horizontal pill selector)
   - Interval: "Every [N]" with a number picker (1, 2, 3...)
   - Weekly only: a row of tappable day-of-week buttons (M T W T F S S), multi-selectable
   - Monthly only: choice between a specific day of month (e.g., 15th) or a relative day (e.g., first Monday, last Friday)

The generated RRULE is stored via the existing recurrence layer (RRULE technical field + plain-English display field per the Phase 3 recurrence spec).

---

## Group 2: Board and focused view

### One-day landmarks

Sub-headers appear only inside FocusedPriorityScreen when the user taps into the "one day" priority. They do NOT appear on the main board card.

Five landmark groups, in this order:
1. This month (due within 30 days)
2. Next 90 days (due in 31–90 days)
3. Later this year (due in 91–365 days)
4. Beyond (due 365+ days out)
5. No date (undated tasks placed in one day via the board logic)

Empty groups hide entirely — no section header rendered if no tasks fall in that range.

Settings: a "One-day landmarks" section with a toggle per landmark (show/hide each individually). Stored in the existing BoardSettings DataStore.

Items auto-migrate upward through groups as their due dates approach; this is automatic because the grouping is computed fresh on each board projection.

### Safety catch

The safety catch evaluates on every sync completion and every board refresh. It checks all dated tasks whose due date has newly entered the ASAP or today priority band since the last evaluation.

When triggered:
- Surface a dismissible in-app prompt for the affected task. Displayed as a card or dialog — never a push notification.
- Copy adapts to the actual due date: "This one is due today. Want a hand with it, or should we find it a better spot?" / "This one was due [Month Dth]. Want a hand with it, or should we find it a better spot?" The date portion uses the same formatting as B7 (full month name, ordinal day).
- Two actions:
  - "Find it a better spot" (active): opens the existing RedirectPromptSheet (change date / snooze / pin / remove date)
  - "Want a hand with it?" (disabled, Coming soon badge): reserved for Phase 7 AI coach

If multiple tasks cross the threshold at the same sync, show one prompt at a time. Dismiss the first to see the next.

Storage: a `safetyCatchFiredAt` timestamp stored in Room for each task, keyed to page ID and the threshold band (asap or today). The prompt does not fire again for the same task at the same band until the task exits and re-enters that band.

### Protected review screen

A transient screen reachable only via the Signal Inflation nudge. Not in the tab bar, nav menu, or any other entry point.

Layout:
- Header: "Protected tasks" with a back button
- Flat list of all currently protected tasks
- Each row: task title, priority badge (color-coded), bucket indicator
- Single row action: tap to toggle protection off (unprotect)

No filtering, sorting controls, or search in v1. Back button returns to the board.

---

## Group 3: Notification infrastructure

### Signal Inflation

Checks run after every sync completion and after every task write operation that could affect the counts (add, move, complete, protect/unprotect).

ASAP inflation check:
- Count active (non-done, non-brain-dump) tasks in the ASAP priority
- If count >= ASAP threshold (default: 5), fire the nudge
- Nudge text: "You have [N] things in ASAP. Want to thin it out?"
- Nudge action: navigate to FocusedPriorityScreen for ASAP

Protected inflation check:
- Count active protected tasks as a percentage of all active tasks
- If percentage >= Protected threshold (default: 30%), fire the nudge
- Nudge text: "That is a lot of Protected tasks. Want to review them?"
- Nudge action: navigate to the Protected review screen

Nudge delivery: a persistent TopNotificationBar variant that does not auto-dismiss. The user must swipe it away or tap the action. It does not re-fire while already visible, and does not re-fire after dismissal until the count drops below threshold and crosses it again. Dismissal timestamp stored in Room.

Encouraging message: after the user returns to the board from a review screen, the system checks if the relevant count is now below threshold. If so, a standard auto-dismissing TopNotificationBar fires with a randomly selected message from a pool. Pool (minimum 8 strings, no consecutive repeats):
- "Nice work clearing those out"
- "Good job re-prioritizing"
- "ASAP is looking cleaner now"
- "That is more like it"
- "Well done"
- "Much more manageable now"
- "Good call on those"
- "Nicely done"

Can be extended over time without a spec change.

Settings (new "Signal Inflation" section):
- ASAP threshold: number input (default 5). Toggle to switch between hard number and percentage of total tasks.
- Protected threshold: percentage input (default 30%). Toggle to switch to hard number.
- Master on/off toggle. Turning off shows a one-time gentle TopNotificationBar: "This helps keep things manageable, but it is your call." The note fires once; dismissal is stored in Room.

Settings stored in BoardSettingsDataStore alongside existing settings.

### A6 — SnackbarHost removal

Remove the SnackbarHost from BoardScreen and FocusedBucketScreen. All notification types route through TopNotificationBar:
- Snooze confirmation: Reversible variant
- Error messages (sync fail, connection error): RefreshError variant with Retry button
- All other message types already routed to TopNotificationBar remain unchanged

Verify every notification type defined in the bug doc (task added, task moved, task completed, task removed, sync success, sync fail, errors) routes through the single TopNotificationBar composable and no other notification surface exists in the app.

---

## Group 4: Task detail and move

### B7 — Priority label

The priority context label in the task detail drawer follows a strict rule:

For DATED tasks:
- Due date is today: "Due today"
- Due date is any other date: "Due [Month Dth, YYYY]" — e.g., "Due July 9th, 2026"
- Never shows the priority band name (not "Due soon", not "Due tomorrow" as a band label)

For UNDATED tasks:
- Show only the priority display name, no preposition, no "due": "ASAP", "Today", "Tomorrow", "Soon", "Later", "One day"
- Never "in ASAP", "in today", "in soon"

The word "due" appears if and only if there is an actual due date on the task.

### A2 — Move/redirect trigger

Add a secondary action button to the task detail drawer. Label logic:
- Dated task: "Change date"
- Undated task: "Change priority"

Tapping "Change date" on a dated task opens RedirectPromptSheet (change date / snooze / pin / remove date). This sheet already exists and is fully implemented — it just has never been triggered from the task detail.

Tapping "Change priority" on an undated task opens a direct priority picker that calls moveTask() with the selected priority. moveTask() already exists in BoardViewModel — this is a wiring fix, not a new implementation.

Both paths must update the task in the data layer, trigger an undo-able notification event via TopNotificationBar, and reflect optimistically in the UI with rollback on failure.

---

## Acceptance criteria (tagged by testing tier)

[logic] = Claude Code writes and runs tests
[ui-auto] = Compose test asserts element presence
[manual] = Human review on device

### Group 1: Add-task drawer
- [ui-auto] Drawer opens in expanded state on FAB tap. After saving, transitions to minimized state showing only title field and toggle.
- [manual] Minimized state shows title field at same vertical position as "Add task to board" button was. Feels like tapping the same spot.
- [manual] Swiping up on the expanded drawer reveals notes, recurrence, Protected toggle, and labels (no "More options" button visible).
- [logic] Draft state (title, bucket, date, type) is saved in memory on drawer dismiss without saving. Restored on reopen. Cleared after successful save.
- [logic] Date displays as "July 8th, 2026" for a date value of 2026-07-08.
- [logic] Time displays as "6:00 AM" in 12-hour mode and "06:00" in 24-hour mode.
- [manual] Priority preview ("→ Soon") appears inside the date picker during selection. Disappears when picker closes. Not shown on the form after date is confirmed.
- [manual] When a date is selected, quick-date chips collapse away with smooth animation and helper text appears. Removing the date reverses it.
- [logic] Tapping "Later today" sets date = today, time = now + 4h. "Tomorrow" = tomorrow at 8:00 AM. "Next week" = configured first day at 8:00 AM. "Next month" = 1st of next month at 8:00 AM.
- [logic] Recurrence picker generates a valid RRULE from frequency + interval + day selections. Plain-English summary matches the RRULE.
- [ui-auto] Tapping the repeat icon opens the recurrence sub-sheet.
- [manual] Recurrence sub-sheet is compact, readable, and controls are intuitive.

### Group 2: Board and focused view
- [ui-auto] FocusedPriorityScreen for "one day" shows landmark sub-headers. Empty landmark groups are absent from the layout tree.
- [manual] Landmarks appear in correct order (This month, Next 90 days, Later this year, Beyond, No date). Items are in the right group for their due dates.
- [ui-auto] Settings contains a toggle per landmark group.
- [logic] Safety catch fires when a dated task newly enters ASAP or today band. Does not fire again for the same task at the same band until it exits and re-enters.
- [ui-auto] Safety catch prompt shows "Find it a better spot" (enabled) and "Want a hand with it?" (disabled with Coming soon indicator).
- [manual] Safety catch prompt is non-intrusive, dismissible, and the copy reads naturally without em dashes or hyphens.
- [ui-auto] Protected review screen lists all protected tasks. Each row shows title, priority badge, bucket.
- [logic] Tapping unprotect on a row in the Protected review screen removes the protected flag in the data layer.

### Group 3: Notification infrastructure
- [logic] ASAP inflation check fires nudge when count >= configured threshold. Does not re-fire while already visible or until count drops and re-crosses threshold.
- [logic] Protected inflation check fires nudge when percentage >= configured threshold. Same re-fire rules.
- [logic] Encouraging message is selected from the pool without consecutive repeats.
- [ui-auto] Signal Inflation settings section is present with ASAP threshold, Protected threshold, and master on/off toggle.
- [logic] Master off toggle fires one-time note (stored in Room, not repeated).
- [ui-auto] No SnackbarHost is present in BoardScreen or FocusedBucketScreen layout trees.
- [logic] All notification types (add, move, complete, remove, sync success, sync fail, error) emit to TopNotificationBar state.

### Group 4: Task detail and move
- [logic] Priority label for dated task due today = "Due today". Dated task due 2026-07-09 = "Due July 9th, 2026". Undated task in today priority = "Today". Never "in asap" or "in today".
- [ui-auto] Task detail shows "Change date" for dated tasks and "Change priority" for undated tasks as a secondary action.
- [logic] Tapping "Change priority" on an undated task calls moveTask() and updates priority in the data layer.
- [manual] Tapping "Change date" on a dated task opens the redirect prompt with four options (change date, snooze, pin, remove date).
- [logic] Successful move emits an undo-able notification event. Undo restores the previous state.

---

## What NOT to build in this batch

- Brain dump review date (Loose Ends, own spec — Phase 3.6)
- Customizable swipe direction assignments (Category C backlog)
- Personalized header with user name (Category C backlog)
- Brain dump categories/tags (Category C backlog)
- Signal Inflation coach behaviors (Phase 7)
- Loose Ends nudge and space (Phase 3.6)
- Any push notifications (own scope, unspec'd)
