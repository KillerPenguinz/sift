# Sift

Sift turns any Notion database into an opinionated, urgency-bucketed task board on Android.
Notion is the backend (API 2025-09-03). Open source, GPL 3.0.

## Current build status

Phases 0 (theming), 1 (onboarding + mapping), 2 (read grid), 3 (plain write layer) BUILT and verified.
Phase 3.5 (Two Axis Model) IMPLEMENTED, currently in UAT and bug-fix iterations.
Phase 4 and beyond: not started. See "What NOT to build."

The live roadmap, backlog, open issues, and per-session handovers live in **storybloq** (`.story/` at the repo root). This file holds the standing constraints; storybloq holds the current work. Load it at the start of every session (see "Ways of working").

## Ways of working (required, every session, every agent)

This project is built with a fixed process so the work is the same no matter who does it (a human, Claude, or Codex) or when. Two systems are mandatory on every session: **storybloq** (what to work on, and what was decided) and the **superpowers** process discipline (how to think, build, and verify). Both govern architectural and product design decisions, not just coding tasks.

**Start of every session.** Load the tracker before anything else: `/story` in Claude Code, `$story` in Codex, or `storybloq status` then `storybloq recap` then `storybloq handover latest` via the CLI. The `.story/` directory at the repo root is the single source of truth for current state, the roadmap, open tickets and issues, and prior handovers. Work from a ticket; if the work is not tracked yet, create the ticket first.

**Every architectural or product decision follows this loop. Do not skip steps.**

1. **Brainstorm first.** Before adding or changing any feature, component, or behavior, use the superpowers brainstorming skill (Claude Code: the `brainstorming` skill) to explore intent and options before writing code. Agents without the skill follow the same discipline: clarify the problem and weigh options before implementing.
2. **Record the decision in two places.** Write it as a storybloq note or ticket (in-repo, travels with the code) and mirror it to the Notion decision log (Notion stays authoritative; see "Decision log"). A decision that lives only in a chat transcript does not exist.
3. **Plan before building.** Use the superpowers writing-plans and executing-plans skills to turn the decision into bite-sized tasks, wired as storybloq tickets with dependencies.
4. **Build test first.** Follow the superpowers test-driven-development skill: write the [logic] tests before the implementation (see "Testing convention"). Set the ticket to `inprogress` when you start.
5. **Debug systematically.** When something breaks, use the superpowers systematic-debugging skill instead of guessing. Log anything out of scope as a storybloq issue rather than fixing it inline.
6. **Verify before claiming done.** Use the superpowers verification-before-completion skill: run the tests and build and confirm the output before saying anything is complete. Mark the ticket `complete` in the same commit as the code.
7. **Review significant changes.** Use the superpowers requesting-code-review skill (or `/story review T-XXX`) before merging.

**End of every session.** Write a handover (`storybloq handover create` or `/story handover`) capturing what changed and why, run `storybloq snapshot`, and capture non-obvious learnings as storybloq lessons.

**Tooling notes.**

- Storybloq works for every agent: `/story` (Claude Code), `$story` (Codex), or the `storybloq` CLI. If the MCP tools or hooks are missing, run `storybloq setup --client all` and restart the client.
- The superpowers skills ship in Claude Code and are invoked with the Skill tool. Agents without the plugin must still follow the same discipline above: brainstorm, plan, test first, verify, review.
- Governance is agent-neutral. `CLAUDE.md` is the canonical instruction file; `AGENTS.md` points here so Codex and other agents load the same rules.

## Stack

- **Language:** Kotlin
- **UI framework:** Jetpack Compose (Material 3)
- **Widget:** Glance (not yet implemented, Phase 5)
- **Min SDK:** 26 | **Target SDK:** 36
- **Build:** Gradle with version catalogs (`libs.versions.toml`)
- **DI:** Manual (AppContainer pattern, no Hilt/Dagger/Koin)
- **Local storage:** Room (for app-operational state, keyed to Notion page ID)
- **Auth:** OAuth 2.0 via Cloudflare Worker proxy (`proxy/worker.js`)
- **Fonts:** Bricolage Grotesque (headings), Hanken Grotesk (body)
- **Icons:** Material Symbols Rounded (filled)
- **Themes:** Five built (Paper is default), token-driven via `derive(theme)`. Paper Light + Paper Dark both exist.

## Module structure

```
:core   Pure Kotlin library. Business logic, mapping, Notion client, domain models.
        Must stay UI-agnostic and KMP-extractable (no Android/Compose imports).
        Path: core/src/main/kotlin/com/ironclinicgym/sift/core/

:app    Android application. Compose UI, ViewModels, DI, auth, WorkManager.
        Path: app/src/main/java/com/ironclinicgym/sift/
```

## Key directories

```
core/
  board/              Board projection, bucket/priority grouping
  domain/             SiftTask, TaskWriteService, OnboardingService, TwoAxisPolicy,
                      UndoManager, SyncRules, RecurrenceSetupService, WriteModels
  domain/ports/       Port interfaces (repository contracts)
  domain/recurrence/  RRULE handling
  mapping/            Role.kt, Mapping.kt, AutoMatcher.kt, MappingValidator.kt,
                      PropertyType.kt, SchemaTemplate.kt, SynonymLoader.kt
  notion/             NotionClient, API models, data source / database resolution
  oauth/              OAuthRequestFactory
  theme/              Token definitions, theme derivation, five theme objects

app/
  auth/           AuthRepository, OAuthLauncher, OAuthRedirectActivity, TokenStore
  data/           Room database, DAOs, local entities
  di/             AppContainer (manual DI)
  ui/board/       BoardScreen, BoardComponents, TaskDetailSheet, AddTaskSheet,
                  FocusedBucketScreen, CustomizeBoardScreen, BrainDumpScreen
  ui/common/      Shared composables (notification bar, swipe actions, etc.)
  ui/navigation/  NavHost, bottom tabs
  ui/onboarding/  OnboardingViewModel, ByoMappingScreen, AutoSetupScreen
  ui/settings/    SettingsScreen
  ui/theme/       Compose theme wiring, color schemes
  work/           WorkManager periodic sync

proxy/
  worker.js       Cloudflare Worker for OAuth token exchange (deployed)

docs/
  PHASE_1_SPEC.md, Phase_3_Spec.md, PHASE_3_5_SPEC.md
  SIFT_DESIGN_DIRECTION.md, sift-tokens.js
  Various UAT/fix docs

.story/           Storybloq tracker: roadmap, tickets, issues, notes, handovers.
                  Source of truth for current work. Load with /story or $story.
```

## Terminology (LOCKED, System A)

These terms are permanent. Internal identifiers use the stable concept name, not the marketing label.

- **Priority** = urgency tier (asap, today, tomorrow, soon, later, one day). Code: `PRIORITY`, `PRIORITY_META`.
- **Bucket** = life area (Work, Personal). Code: `BUCKET`, `BUCKETS`.
- **Brain dump** = separate uncommitted capture space. NOT a priority tier.
- Notion's "data source" API concept stays `DataSource`/`data_source`.
- Display strings ("Buckets", "Priorities") live in a presentation layer only. Never bake these words into logic, storage keys, or the mapping.
- Bucket is OPTIONAL for bring-your-own database users (see ADR Section 5c).

## Hard constraints (violating any of these is a bug)

1. **No em dashes or hyphens in any user-facing text.** Use commas, semicolons, or rewording instead.
2. **Token discipline.** Every UI element references design tokens. Never hardcode colors.
3. **Mapping layer.** Never hardcode a Notion property name. Every read and write goes through the stored mapping (`Role` to property binding). A single hardcoded name breaks the bring-your-own path silently.
4. **Notion API version 2025-09-03.** Search filter uses `"data_source"` (not `"database"`). Data source IDs and database IDs are NOT interchangeable. POST `/data_sources/{id}/query` accepts ONLY data source IDs. Page creation parent: `{"type":"data_source_id","data_source_id":"<ID>"}`.
5. **Rate limit.** ~3 req/sec to Notion. All calls must use exponential backoff (1s, 2s, 4s, 8s, max 60s) with max 3 retries per sync cycle. After max retries, transition to FAILED state and STOP retrying until next manual or WorkManager trigger.
6. **Writes are optimistic then reconciled.** On failure, roll back the optimistic UI change and surface an actionable error. Never silently lose user input. Retries must be idempotent (never duplicate a task).
7. **Pagination.** Explicit `page_size: 100` on all Notion queries. Handle `has_more` / `next_cursor`.
8. **Storage split.** App-operational state (pin, protected, created_by, counts, dismissals) lives in Room keyed to Notion page ID. User content (notes) in the Notion page body or mapped notes field. Only recurrence adds real Notion columns (with Sift prefix and consent).
9. **Sift-managed fields.** Any field Sift adds to a user's database uses the "Sift" prefix and requires consent (bring-your-own path shows a prompt; auto-setup path creates silently).
10. **No Notion logo.** Trademark risk. Use a generic external-link icon for "View in Notion" actions.
11. **collectAsStateWithLifecycle.** Never use `collectAsState()`. Always use `collectAsStateWithLifecycle()` to prevent background collection and battery drain.
12. **No FLAG_KEEP_SCREEN_ON.** Unless explicitly justified.
13. **Bottom navigation padding.** Always apply `Modifier.navigationBarsPadding()` or `WindowInsets.navigationBars` so UI elements are never obscured by the system nav bar.

## Phase 3.5 (Two Axis Model) key behaviors

These are the current design decisions governing how the app works. Read the full spec at `docs/PHASE_3_5_SPEC.md`. These rules affect code throughout the entire project, not just the spec file, so they live here as standing constraints.

- **Dates auto-assign priority** via configurable bands (overdue/due today = asap, due tomorrow = tomorrow, 2-7 days = soon, 8-30 = later, 30+ = one day).
- **Dated tasks cannot be dragged between priorities.** Attempting to drag shows a redirect prompt: Change the date / Snooze / Pin / Remove the date.
- **Undated tasks can be dragged freely** between priorities.
- **ASAP** is the special undated urgent tier (no date required, means "I need this now").
- **Pinning = visibility**, not priority override. Pinned tasks appear in a separate Pinned section at the top of the board. Priority is unchanged.
- **Pin cycle (recurring tasks):** unpinned -> pinned (this instance) -> persistently pinned (all future occurrences) -> unpinned.
- **Protected flag** guards against deprioritization. Fires on snooze (dated) or drag-down (undated). Carries to recurring occurrences.
- **Brain dump** is a separate space for uncommitted ideas, NOT a priority tier.
- **Add-task flow:** user never picks a priority. Three paths: date (auto-assigns), quick-date chips (Later today/Tomorrow/Next week/Next month), or brain dump. No date = ASAP default.
- **Quick-date defaults:** Later today = +4hrs, Tomorrow = 8am, Next week = first-day-of-week at 8am, Next month = 1st of next month at 8am. All configurable in Settings.
- **When a date is selected,** the How-soon pills collapse away and "Sift handles the priority. Adjust anytime." appears.
- **Priority labels:** Dated = "Due today" / "Due July 8th, 2026". Undated = "Today" / "Soon" (no preposition, no "due"). Never "in asap" or "in today."
- **Move button removed** from task detail. Edit and Remove are secondary actions.
- **FAB (+)** in bottom right opens the add-task drawer. Replaces the persistent text bar.

## What NOT to build

These are out of scope. The coding agent (Claude or Codex) must not implement any of these unprompted, even if they appear to be gaps in the codebase. Each has its own phase, spec, or deliberate deferral reason, and each has a storybloq ticket. Pick one up only when its ticket is explicitly selected for work.

- AI capture / natural language parsing, plus a Pebble Watch 2 companion that routes on-watch actions to Notion (Phase 4; see storybloq Phase 4 and note N-002)
- Home screen widget (Phase 5, unspec'd)
- Pricing / IAP / Supporter tier (Phase 6, unspec'd)
- AI coach (Phase 7, unspec'd)
- Push notifications / reminders (own scope, unspec'd)
- Sub-tasks (own scope, deliberately deferred, complexity risk)
- Leaderboards or gamification (future, requires backend)
- Multi-database consolidation (future)
- Personalized header with user name (backlog, Phase 6)
- Brain dump categories/tags (backlog, will reuse labels system)
- Customizable swipe direction assignments (backlog, settings placeholder exists)

## Testing convention

Every acceptance criterion is tagged by testing tier. The coding agent (Claude or Codex) owns [logic] and [ui-auto]; the maintainer owns [manual].

- **[logic]** Fully autonomous. Write and run unit/integration tests. Pass/fail is unambiguous. Includes: domain functions, ViewModel state transitions, Notion write-layer payloads, mapping validation, string scanning, RRULE generation, date-to-priority bands.
- **[ui-auto]** Partially autonomous. Compose UI tests asserting element presence/absence/tap response. Cannot judge visual appearance. Catches "element is missing" but not "element looks wrong."
- **[manual]** Human only. Visual correctness, animation feel, real-device behavior, color/spacing judgment.

**Standing instruction:** For every item implemented, write the [logic] tests as you build. Mark an item done only when its logic tests pass. At the end of a session, produce a Build Report (recorded as the storybloq handover for the session) with: what was implemented, test results (X of Y pass), every [manual] item listed separately for the maintainer's review, anything that could not be verified and why, and any assumptions made (flag, never decide silently).

## Build commands

```bash
./gradlew :app:installDebug        # Build and install debug APK
./gradlew build                     # Full build with lint
./gradlew :core:test                # Run core module unit tests
./gradlew :app:testDebugUnitTest    # Run app module unit tests
```

## Secrets (git-ignored, in local.properties)

```
notion.clientId=<public OAuth client ID>
notion.oauthProxyUrl=<Cloudflare Worker URL>
notion.devToken=<optional: internal integration token for debug>
```

Dev token is seeded only in DEBUG builds when no stored OAuth token exists. OAuth token always takes priority.

## Decision log

All design and architecture decisions must be recorded in two places: a storybloq note or ticket in `.story/` (in-repo, so it travels with the code and reaches every agent) and the Sift Notion workspace. Notion remains the single source of truth for the canonical decision record; storybloq is the working tracker. The local `docs/` folder contains exported snapshots for the coding agent to reference during a build session, but Notion is authoritative.

- **Where decisions live:** In storybloq, a note (for rationale and open questions) or a ticket (for committed work). In Notion, the Phase Tracker row for the relevant phase, the Consolidated Open Questions page, or a dedicated feature concept page inside the relevant phase row.
- **When to update:** After any decision is made during a session (via conversation, brainstorming, or mid-build clarification), capture it in storybloq immediately and mirror it to the relevant Notion page before the session ends. Keep the two in sync. Do not let decisions live only in the conversation transcript.
- **Backlog review:** When speccing or planning any new feature or phase, always check the Consolidated Open Questions page, the Future Ideas and Backlog page, and any relevant concept pages for items that may have been deferred there. Surface anything that belongs in scope before locking the spec.
- **Notion workspace hub:** `https://app.notion.com/p/38ee3d39a44580038dc3ff7861777ff7`
- **Phase Tracker:** `https://app.notion.com/p/f592c4d4278945c4b824d5325aaf8b2b`
- **Consolidated Open Questions:** `https://app.notion.com/p/38ee3d39a445816491c2f3b7d0ab2641`
- **Future Ideas and Backlog:** `https://app.notion.com/p/391e3d39a445813c8376cf5521f436e6`

## Design reference

- Design tokens: `docs/sift-tokens.js` and `docs/SIFT_DESIGN_DIRECTION.md`
- Claude Design project: `https://claude.ai/design/p/22b31bdd-54db-4e9b-9ea3-509b19454cdd`
- Notion workspace hub: `https://app.notion.com/p/38ee3d39a44580038dc3ff7861777ff7`
