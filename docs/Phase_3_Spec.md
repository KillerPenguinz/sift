# Phase 3 Handoff to Claude Code (plain write layer)

> A self contained handoff for Claude Code to plan and build Phase 3 of Sift, the PLAIN write layer. Everything Claude Code needs is on this page. Source of truth remains the Phase 3 PRD and the ADR; this is an assembled export for the build. No em dashes or hyphens in any user facing text the app produces.
> 

## 0. Context: what Sift is, and what Phase 3 is

Sift is an Android app (Kotlin, Jetpack Compose) plus a home screen widget (Glance, later phase) that turns a Notion database into an opinionated task board organized by urgency. Notion is the backend; Sift renders its own way. Phases 0 (theming), 1 (onboarding and the mapping layer), and 2 (the read only Priority grid) are BUILT and verified on device. Phase 3 makes the board writable: add, edit, remove, complete, move, recurrence, snooze, undo, all written back to Notion through the existing Phase 1 mapping.

**Scope split (important):** Phase 3 is the PLAIN write layer only. The Two Axis Model (dates auto assigning Priorities, pinning, the Protected flag, mismatch intent capture, the caring layer) is a SEPARATE later phase built on top of this. Do NOT build any of that now. Where a manual move happens, just update the Priority, no date logic, no pinning, no prompts. Keep the move path clean so the Two Axis layer can wrap it later without a rewrite.

## 1. Terminology and naming (post refactor, authoritative)

The codebase was refactored so internal identifiers match the permanent concept:

- **Priority** = the urgency dimension (asap, today, tomorrow, soon, later, one day). Code: PRIORITY / PRIORITY_META etc.
- **Bucket** = the life area dimension (Work, Personal). Code: BUCKET / BUCKETS etc.
- Notion's own data source API concept is unrelated and stays as DataSource / data_source.
- Rule going forward: name new internal identifiers after the stable concept, not the marketing label; keep display strings in a separate presentation layer. See ADR Section 5b.

## 2. Locked architecture that governs this build (from the ADR, unchanged)

- Native Kotlin, Compose for the app. Business logic stays a clean, UI agnostic, KMP ready layer (the mapping layer, Notion client, cache, sync rules). The write layer must live in that core, decoupled from Compose, so Phase 4 AI capture can reuse it unchanged.
- Auth: existing public OAuth 2.0 with capabilities Read content, Insert content, Update content. Update and Insert cover all Phase 3 writes (create page, update properties, archive). No new capability is needed.
- Refresh: existing pull based periodic (WorkManager, ~15 min floor) plus manual. Writes are optimistic locally, then reconciled with Notion.
- Rate limit (~3 req/sec) with retry and backoff; paginate with explicit page_size 100. All still apply to writes.
- Never hardcode a Notion property name; every write targets properties through the stored mapping.
- No em dashes or hyphens in user facing strings.

## 3. Phase 3 specification (plain write layer)

### 3.1 Goals

- Add a new task (title, Priority, Bucket, plus optional mapped fields), written to the mapped Notion database.
- Edit an existing task's mapped fields.
- Complete a task (swipe or tap, with undo), setting its done status and moving it to Completed today.
- Remove a task (archive to Notion trash, reversible), never a silent hard delete.
- Move a task between Priorities (full drag and drop; vertical position is priority).
- Full recurring tasks via the RRULE standard, stored in Sift managed fields, regenerating on completion.
- First class swipe to snooze.
- Single active undo on major actions with a highlight flash on the affected row.
- Every change optimistic in the UI, reconciled with Notion, graceful on failure.
- All writes driven by the mapping, never hardcoded property names.

### 3.2 Non goals

- No AI capture (Phase 4, reuses this write layer).
- No widget editing (Phase 5).
- No Two Axis Model behavior (separate later phase): no date driven Priority auto assignment, no pinning, no Protected flag, no mismatch intent prompt.
- No bulk operations unless trivially free once single item write exists.
- No reminders/notifications system (its own later scope). Board level snooze IS in scope; time based reminders are not.
- No permanent destructive delete that bypasses Notion trash.

### 3.3 Functional requirements

**Add**

- A native add interface lets the user set title, choose a Priority, choose a Bucket, and set any optional mapped fields (for example due date, notes).
- On device dictation must work by using normal text inputs the platform keyboard voice input can fill. This is free and must not be gated.
- Creating a task writes a new page to the mapped database with values in the correct mapped properties.
- The new task appears in the correct Priority immediately (optimistic) and persists after refresh.

**Edit**

- Open an existing task and edit its mapped fields; saving updates the Notion page's mapped properties; reflects immediately and survives refresh.
- The add and edit form shows mapped fields by default (title, Priority, Bucket, due date, notes) for fast capture, with an optional expand to surface additional properties. Computed or read only Notion types (formulas, rollups) are never presented as editable.

**Complete vs Remove (separate actions)**

- Complete: swipe or tap, with a brief undo window. Sets the mapped status or done property; the task moves to a Completed today group rather than disappearing. For a recurring task, completing also generates the next occurrence (see Recurrence).
- Remove: archives the Notion page to Notion trash (recoverable), via a safe reversible interaction (brief undo window), never a silent hard delete. Explain the behavior in plain language.

**Move between Priorities**

- Full drag and drop. Vertical position is priority: dragging higher re prioritizes (can move to a more urgent Priority), not merely reorders. Moving updates the mapped priority property to the new Priority's option, reflects immediately, persists after refresh.
- PLACEHOLDER (Two Axis out of scope): a move just updates the Priority. No date logic, pinning, or prompts. Keep this path clean so the later Two Axis layer can wrap it.

**Sort within a Priority**

- Sensible default order (overdue, then dated, then undated) plus manual drag to reorder within a Priority (falls out of the drag and drop). No extra sort toggles in v1.

**New Bucket and Priority values**

- Users can create their own Buckets; typing or adding a new one is allowed, not restricted to existing options.
- App created Buckets or Priorities that do not yet exist as an option in the mapped Notion property should create that option via the write layer (Sift prefix and consent convention where writing to a user's own database). This resolves the Phase 2 Added in Sift only limbo.

**Recurring tasks (full recurrence via RRULE)**

- Use the RRULE standard (iCalendar). Do not invent a custom engine; use an established library. Covers every realistic case (every weekday, every 2nd Tuesday, monthly last day, every N days until a date, etc).
- Store recurrence in two Sift managed fields: a technical field holding the RRULE string (for example FREQ=WEEKLY;BYDAY=TU,TH) and a plain English display field Sift generates (for example Every Tuesday and Thursday) so Notion stays legible.
- Field naming uses a clear Sift prefix (for example Sift Recurrence, Sift Repeats) so provenance is obvious. Display field on by default, user can turn it off.
- Completing a recurring task generates the next occurrence from the RRULE with the next due date.

**Sift managed fields and consent (general convention)**

- Any field Sift adds to a user's own database uses the Sift prefix AND a plain consent prompt first. Bring your own path: To support recurring tasks, Sift needs to add a field called Sift Recurrence to your database. Okay to add it? Yes proceeds; No means that feature is unavailable for that database (graceful degradation). Automatic setup path: created as part of Sift's schema without a separate prompt.

**Snooze and defer (first class)**

- Swipe to snooze. Default: bump the task down one Priority instantly, with a snackbar (Snoozed to [Priority]. Change?) whose Change action opens further options (a lower Priority, or a specific date and time).
- Setting Always prompt me for a new date (off by default): swipe opens the picker every time.
- At the lowest Priority (one day) with no date, snooze does not bump further; show a gentle already lowest priority notice.
- For a task that already has a due date, snooze acts on the DATE (later today, tomorrow, next week), not the Priority.

**Undo and redo**

- Single active undo on major actions (complete, remove, move), a snackbar with the action label (Task removed, Task restored). On undo or redo, the affected row gets a brief accent colored highlight flash that fades over about a second, so the user sees what changed and where. A longer timestamped undo history is backlog, not v1.

**Storage note (metadata placement, matters even in the plain layer)**

- Keep the user's Notion database CLEAN. Do not add app operational state as database columns. Recurrence is the one thing that needs real fields (with consent, Sift prefix). Any other Sift only operational state should live in Sift local storage (Room), keyed to the Notion page id, not as new columns. User meaningful task notes belong in the task's Notion page body or an existing mapped notes field, not a new column. (This mirrors the storage split locked for the Two Axis Model, applied here too.)

**Write integrity**

- Every write (add, edit, complete, remove, move) targets properties via the Phase 1 mapping.
- All writes optimistic in the UI, then reconciled with Notion; on failure roll back the optimistic change and surface an actionable error, never silently losing the user's input (entered data recoverable).
- Respect the rate limit with retry and backoff; retries idempotent so a flaky connection never duplicates a task.

### 3.4 Non functional requirements

- Add, edit, remove, move each feel immediate, reconciliation non blocking.
- A failed write preserves the user's input.
- Retries idempotent (no duplicate tasks).
- The write layer is cleanly separated in the KMP ready core so Phase 4 AI capture reuses it unchanged.
- No em dashes or hyphens in user facing text.

### 3.5 Acceptance criteria

- Add a task (title, Priority, Bucket, optional mapped fields); it writes to the correct mapped properties and appears in the right Priority immediately and after refresh.
- On device voice input fills title and notes; never gated.
- Edit a task's mapped fields; updates the Notion page and survives refresh.
- Complete sets the done status and moves the task to Completed today, with undo; recurring tasks generate the next occurrence.
- Remove archives to Notion trash via a reversible interaction explained in plain language.
- Move via drag and drop updates the mapped priority property to the new Priority's option, immediately and after refresh.
- Recurrence stored as RRULE plus a plain English display field, both Sift prefixed, display on by default and toggleable; consent prompt shown before adding fields to a bring your own database.
- New Bucket or Priority values can be created and sync a real Notion option via write back.
- Snooze works per spec (bump one Priority, Change snackbar, always prompt setting, lowest Priority notice, dated tasks snooze the date).
- Single active undo with the highlight flash works on complete, remove, and move.
- App operational state is NOT added as Notion columns; only recurrence adds fields, with consent.
- All writes go through the mapping, are optimistic with reconciliation, roll back on failure with an actionable error, respect the rate limit, and never duplicate on retry.
- The write layer is reusable by Phase 4 without changes.
- No user facing text contains em dashes or hyphens.

## 4. The planning prompt to run first

Open the Sift project in Claude Code (it already contains Phases 0 to 2), then run this:

```
I am working on Sift, an Android app in Kotlin with Jetpack Compose. Phases 0, 1, and 2 are already built (theming, onboarding and the mapping layer, and the read only Priority grid). Here is the FINAL SPEC for Phase 3, the plain write layer (add, edit, remove, complete, move, recurrence, snooze, undo). It is in the repo at docs/PHASE_3_SPEC.md. Read it.

Please:
1. Inspect the existing project, especially the mapping layer, the Notion client, the cache, and the domain models from Phases 1 and 2.
2. Propose a concrete implementation plan that aligns with the project as it exists right now.
3. List:
   - Files to create
   - Files to modify
   - For each file: what changes at a high level (no full code yet)
   - Any new composables, view models, data models, repositories, services, or persistence schemas to add
4. Call out conflicts, missing pieces, or concerns based on the current codebase.

Rules:
- Do NOT write full implementations yet. Short snippets or signatures to illustrate intent are fine; the goal is a file level plan consistent with the project.
- Build the write layer inside the clean, UI agnostic, KMP ready core (alongside the existing mapping layer and Notion client), decoupled from Compose, so Phase 4 AI capture can reuse it unchanged. Flag any step that would entangle write logic with UI.
- Never hardcode a Notion property name; every write targets properties through the stored mapping. Flag any step that would violate this.
- Writes are optimistic then reconciled with Notion, roll back on failure with an actionable error, respect the rate limit (~3 req/sec) with backoff, and are idempotent on retry (never duplicate a task).
- Complete and Remove are separate: complete sets the done status and moves to Completed today; remove archives to Notion trash. Both reversible via single active undo with a highlight flash.
- Recurrence uses the RRULE standard via an established library (do not invent an engine), stored in two Sift prefixed fields (technical RRULE plus plain English display), created only after a consent prompt for bring your own databases.
- Do NOT build any Two Axis Model behavior (date driven Priority assignment, pinning, Protected flag, mismatch prompts). A manual move just updates the Priority. Keep the move path clean so that layer can wrap it later.
- Keep the user's Notion database clean: only recurrence adds columns (with consent); other Sift operational state lives in local storage keyed to the Notion page id; user task notes go in the page body or an existing mapped notes field.
- Respect Android conventions (Compose, lifecycle, WorkManager, Room, secure storage) and the Notion data contract (database vs data source; the mapping resolves to a data source within a database).

Output format:
# Implementation Plan
## 1. Overview
## 2. Files to Create
## 3. Files to Update
## 4. Notes / Concerns
```

Then review the plan (watch the Notes and Concerns), have Claude Code implement from its own plan, and run the validation prompt (below).

## 5. The validation prompt (after implementation)

```
Review the Phase 3 code you just wrote against the rest of the project.
1. Fix any incorrect imports, package paths, type names, or naming mismatches.
2. Ensure it is valid Kotlin for Jetpack Compose in this project and compiles against the targeted Android API level.
3. Confirm no Notion property name is hardcoded; all writes go through the mapping, which resolves to a data source within a database.
4. Confirm writes are optimistic then reconciled, roll back on failure with an actionable error, respect the rate limit with backoff, and are idempotent on retry (no duplicate tasks).
5. Confirm complete vs remove behave separately and correctly, and that single active undo plus the highlight flash work on complete, remove, and move.
6. Confirm recurrence uses a real RRULE library, stores the two Sift prefixed fields, gates field creation behind consent for bring your own databases, and regenerates the next occurrence on completion.
7. Confirm NO Two Axis Model behavior was built (no date driven Priority assignment, pinning, Protected flag, or mismatch prompts) and that a manual move simply updates the Priority.
8. Confirm the write layer is free of Compose and Android UI dependencies so it stays KMP extractable, and that the user's Notion database gains no columns except the consented recurrence fields.
9. Point out remaining TODOs or risky assumptions.

Give me minimal, copy pasteable fixes or corrected versions of the affected files.
```

## 6. Things to verify outside the AI tools (for BJ)

- On device: add, edit, complete (with undo), remove (with undo), and drag to move all persist to Notion and survive refresh.
- Recurrence: complete a recurring task and confirm the next occurrence appears with the right next date, and the two Sift fields read correctly in Notion.
- Bring your own database: confirm the consent prompt appears before Sift adds the recurrence fields, and No degrades gracefully.
- Confirm the user's Notion database did not gain stray columns (only the consented recurrence fields).
- Confirm a failed write (toggle airplane mode mid save) rolls back cleanly and preserves your input.