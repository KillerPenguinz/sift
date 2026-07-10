# Phase 3.5 Round 2: UAT Feedback Spec

## Overview

Comprehensive redesign addressing all Round 1 UAT feedback. Covers notification system overhaul, menu hub, add-task drawer improvements, board layout changes, task detail sheet fixes, brain dump labels system, safety catch bug fix, and protected pin discoverability.

**Architecture:** Hub-centric. The menu hub (replacing the settings gear) is the structural backbone. Notifications, action history, and settings consolidate under it. Every other feature plugs into this foundation.

## User Decisions (already made)

- Lucide icon migration is backlogged; keep Material Symbols Rounded for now.
- Full notification center with action history, not a partial build.
- Full labels system for brain dump (user-created labels with color and icon).
- Voice input on both title and notes fields using SpeechRecognizer API.
- Recurrence: tap-to-cycle through presets, long-press for detailed configuration.
- Post-save bar: 5 second countdown, swipeable in any direction to dismiss.
- Priority picker: inline scrollable row with colored chips.
- Menu hub: replaces settings gear, opens a full-screen menu.
- Notification badge clears when the menu is opened (not when you tap into notifications).
- Two notification tiers: transient (informational, no badge) and actionable (badge + persisted as unread).

---

## Section 1: Menu Hub & Navigation

### What changes

The settings gear icon in the header bar is replaced by a menu icon (three-line or grid icon). Tapping it opens a full-screen menu.

### Menu structure

- **Notifications** — notification history, stored actionable alerts (safety catch, inflation nudges, future AI coach messages). Shows count of unread items.
- **Action History** — log of recent user actions with undo capability.
- **Settings** — the current settings screen, relocated here.

Future features (AI coach, etc.) add rows to this menu without further navigation changes.

### Badge behavior

A badge dot appears on the menu icon whenever there are unread actionable notifications. The badge clears as soon as the user opens the menu (not when they tap into a specific notification section).

### Header bar layout

`[sift. logo] — [sort/filter] [grid toggle] [refresh icon + timestamp] [menu icon with badge]`

---

## Section 2: Notification System Overhaul

### Position

The notification bar stops overlapping the logo/header. It replaces the subheader ("Sift Tasks" + checkmark icon, or "Brain dump" + count badge). When a notification is active, the subheader slides out and the notification slides in. When the notification dismisses, the subheader returns. Vertical space stays constant.

### Two tiers

**Transient notifications** (no badge, no unread state):
- "Added to asap.", "Added to brain dump.", "Removed [task name]" with undo button
- Show in the banner briefly, logged in action history, but no badge on menu icon

**Actionable notifications** (badge + persisted):
- Safety catch alerts, signal inflation nudges, warnings
- Banner shows, badge appears on menu icon, persists as unread in notification center

### Sync indicator (no banner)

No more "Up to date. Refreshed just now." banner notification. Instead:
- The refresh arrow icon in the header bar spins while syncing.
- The timestamp text ("now", "1m", etc.) is replaced by three dots with a sequential bounce animation during sync.
- When sync completes, the dots collapse and the timestamp fades in showing the updated time.
- Sync status lives entirely in the header bar; it never consumes the notification slot.

### Queuing

When multiple notifications fire:
- One notification displays at a time for its full duration.
- When it expires, it slides out to the left.
- The next one slides in from the right.
- Each notification gets its full display time; no more overwriting.
- Transient notifications auto-dismiss after their timer. Actionable notifications stay until explicitly dismissed or acted on.

### Text fixes

- Adding a brain dump item: "Added to brain dump." (not "Added to Task.")

---

## Section 3: Notification Center & Action History

### Notifications tab

- Chronological list of actionable notifications (safety catch alerts, inflation nudges, future AI coach messages).
- Each entry: icon, message, timestamp, action button if applicable (e.g., "Find it a better spot" for overdue tasks).
- Unread items have a visual indicator (accent dot or background tint).
- Opening this tab marks all as read (badge clears when menu opens per user spec, but items stay visible).
- Capped at last 30 days or 50 entries, whichever is smaller.

### Action History tab

- Chronological list of all user actions: task added, moved, completed, removed, pinned, priority changed, label assigned, etc.
- Each entry: action description, task name, timestamp.
- Undo button on reversible actions (remove, complete, move, priority change).
- Undo availability expires after the action has been synced to Notion and a subsequent sync has occurred (Notion state is authoritative after that point).
- Remove action: the banner says "Removed [task name]" with inline undo button, AND the action is logged here as a fallback undo path.

---

## Section 4: Add-Task Drawer

### 4a: Inline voice input

Both the title field ("What needs doing?") and the notes field ("Add a note...") get a mic icon on the right side. Tapping it:
- Starts Android's `SpeechRecognizer` API directly (no intent, no full-screen Google UI).
- The mic icon animates (pulsing or waveform) while listening.
- Partial results stream into the text field in real time.
- Tapping the mic again (or silence timeout) stops recording.
- Requires `RECORD_AUDIO` permission with a runtime prompt on first use.

### 4b: Remove redundant date icon

The second calendar icon at the far right of the quick-date chip row is removed. The "Date" chip under WHEN already opens the date picker. One path to the same action.

### 4c: Bucket icon scaling

Bucket icons scale based on how many buckets the user has:
- 1 to 4 buckets: icons fill the available horizontal space (larger, easier to tap).
- 5+ buckets: icons shrink to a minimum size and become horizontally scrollable.
- The current tiny circle size is the minimum; icons grow when there's room.

### 4d: Date chip fixed sizing

Selecting a date no longer makes the chip balloon in size and shift the layout. The date chip has a fixed max width. Long dates truncate or use a shorter format ("Jul 16") to stay within bounds.

### 4e: Recurrence tap-to-cycle

The recurrence icon cycles through states on each tap: Off -> Daily -> Weekly -> Monthly -> Yearly -> Off. The current state shows as a small label below the icon (e.g., "Daily", "Weekly").

Long-press opens the detailed configuration inline (interval picker, day-of-week selector for weekly). This expands in place on the same screen; no separate bottom sheet.

### 4f: Post-save behavior

After saving a task or brain dump item:
1. The bar text changes to "What else needs doing?" with Task/Dump toggle still visible.
2. A progress fill animates left-to-right over 5 seconds.
3. Swiping the bar in any direction dismisses it immediately.
4. When the timer expires or the bar is swiped, a fold-up animation plays, collapsing the bar into the FAB.
5. Tapping the bar before it collapses opens a fresh add-task drawer.

### 4g: Notes field mic icon

The "Add a note..." field gets the same mic icon and inline voice behavior as the title field (see 4a).

---

## Section 5: Board Layout

### 5a: Pinned section becomes a scrollable bucket

The pinned section stops being fixed at the top. It becomes a bucket card styled like ASAP, Today, Tomorrow, etc., with its own header ("Pinned") and count badge. It appears first in the scroll order (above ASAP) but scrolls with the rest of the content. Same card styling, same expand/collapse behavior.

### 5b: Single-column task limit

In the two-column grid view, buckets already cap at 4 items with a "+N more" indicator. The single-column view currently shows all items with no cap.

A new setting lets the user choose how many items to show per bucket in single-column view before a "+N more" tap-to-expand appears. Default: 8. Range: 4 to 20, plus "Show all."

### 5c: Refresh indicator animation

- The circular refresh arrow in the header bar spins while a sync is in progress.
- The timestamp ("now", "1m", etc.) is replaced by three dots with a sequential bounce animation during sync.
- When sync completes, the dots collapse and the timestamp fades in showing the updated time.
- No banner notification for sync status.

---

## Section 6: Task Detail Sheet

### 6a: Pin doesn't close the sheet

Tapping the pin icon toggles pin state (unpinned -> pinned -> protected pin -> unpinned) but the task detail sheet stays open. The pin icon updates in place. The user can continue viewing or editing.

### 6b: Swipe up works

The "Swipe up for notes and details" prompt responds to actual swipe gestures, not just taps. A swipe-up on the sheet expands it to reveal notes and details. Tap behavior also remains as an alternative.

### 6c: Inline priority picker

"Change priority" no longer navigates to a full-screen selector. A horizontal scrollable row of priority chips appears inline within the task detail sheet. Each chip is colored to match its bucket color from the board:
- ASAP: red
- Today: amber
- Tomorrow: yellow
- Soon: green
- Later: blue
- One Day: purple

Tapping a chip selects it. The row stays visible until the user taps elsewhere or scrolls away.

### 6d: Edit doesn't auto-focus keyboard

Tapping the edit button enters edit mode (fields become editable) but does NOT automatically focus the title field or open the keyboard. The user taps whichever field they want to edit.

### 6e: Remove shows undo

Tapping "Remove" dismisses the task detail sheet and shows a transient notification banner: "Removed [task name]" with an "Undo" button. The action is also logged in Action History in the menu hub. The undo button in the banner reverts the removal. If the banner expires without undo, the removal is committed on the next sync.

---

## Section 7: Brain Dump

### 7a: Tappable items

Brain dump items are tappable. Tapping opens a detail view (similar to the task detail sheet) showing:
- Title (editable)
- Notes (editable)
- Labels (assignable)
- Creation date
- Last modified date
- Actions: edit, assign label, promote to task (moves to board with date or ASAP), delete

### 7b: Labels system

Users create labels from within the brain dump:
- Each label has a **name**, a **color** (picked from a palette matching Sift's token-driven theme colors), and an **icon** (selected from Material Symbols set).
- Labels are stored in Room, scoped to the user's mapping.
- A brain dump item can have one label assigned.
- The label color appears as a left-edge accent bar on the item card.
- The label icon replaces the current placeholder square icon next to the title.
- Label management (create, edit, delete labels) is accessible from the brain dump header or from within the item detail view.

### 7c: Sorting

A sort control in the brain dump header (next to the "N thoughts" count). Options:
- **By label** (default) — groups items under label headers, unlabeled items at the bottom.
- **By creation date**
- **By last modified**

Each sort mode has a toggle for ascending/descending. The selected sort persists across sessions (stored in DataStore preferences).

### 7d: Placeholder icon fix

The current small square icon next to each brain dump item title is replaced by the item's label icon (if assigned) or removed entirely (if no label).

---

## Section 8: Safety Catch

### Bug fix

The safety catch dialog does not fire when a task is overdue and a sync completes. Root cause investigation needed during implementation; the evaluator logic passes unit tests, so the issue is likely in how/when `runSafetyCatchEvaluation()` is called or how the task list and local states are passed to it.

### On-launch behavior

Safety catch evaluation runs on app launch (cold start and resume from background), not just after manual sync. To avoid being annoying, the dialog fires at most twice per day per task. This is tracked by storing a `lastSafetyCatchShownAt` timestamp in the task's `TaskLocalState` record. If the dialog was shown within the last 12 hours for that task, it is suppressed until the next window.

### Notification center integration

Safety catch alerts that the user dismisses without acting on ("Find it a better spot" not tapped) are stored as actionable notifications in the notification center. The user can revisit them from the menu hub. The badge appears on the menu icon for these.

---

## Section 9: Protected Pin

### Terminology

The persistent/recurring pin state is called "Protected pin" in all user-facing language: UI labels, notifications, settings references.

### Discoverability

The pin cycle (unpinned -> pinned -> protected pin -> unpinned) is made visible:
- Tapping the pin icon cycles through states with a brief label that appears next to the icon for 2 seconds: "Pinned", "Protected pin", "Unpinned".
- Three distinct icon states: empty outline (unpinned), filled pin (pinned this instance), filled pin with a small shield accent (protected pin, all future occurrences).
- The task detail sheet shows the current pin state as text below the pin icon: "Not pinned", "Pinned (this instance)", "Protected pin (all occurrences)".
- Entering protected pin state shows a transient notification: "Protected pin applied to all future occurrences."

### Task detail integration

Since pinning no longer closes the sheet (Section 6a), the user sees the state label update in real time as they tap through the cycle.

---

## Bug Fixes (additional)

### Bottom navigation bar cut off

The bottom navigation bar labels ("Board", "Brain dump") are cut off at the bottom of the screen. This is a `navigationBarsPadding()` or `WindowInsets.navigationBars` issue that needs to be fixed on the main scaffold.

### Minus icon renders as Chinese character

The minus icon in the recurrence builder sheet's interval stepper renders as a Chinese character instead of a minus symbol. This is a font/icon rendering issue with the `MaterialSymbol` composable. Fix the icon name or rendering path.

---

## Out of Scope (backlogged)

- Lucide icon migration (aesthetic preference, not blocked)
- AI coach (Phase 7)
- Push notifications / reminders (own scope)
- Sub-tasks (deliberately deferred)
- Multi-database consolidation (future)

---

## Hard Constraints (carried forward)

1. No em dashes or hyphens in any user-facing text.
2. Token discipline: every UI element references design tokens via `SiftTheme.tokens`.
3. Mapping layer: never hardcode a Notion property name.
4. Notion API version 2025-09-03.
5. Rate limit ~3 req/sec with exponential backoff.
6. Writes are optimistic then reconciled.
7. Explicit `page_size: 100` on all Notion queries with pagination.
8. App-operational state in Room keyed to Notion page ID.
9. Sift-managed fields use "Sift" prefix and require consent.
10. No Notion logo.
11. Always `collectAsStateWithLifecycle()`, never `collectAsState()`.
12. No `FLAG_KEEP_SCREEN_ON`.
13. Always apply `navigationBarsPadding()` or `WindowInsets.navigationBars`.
