# Sift — Build Synopsis

A full record of what has been built, added, removed, and changed to date. Sift is an Android
app (Jetpack Compose) that turns a Notion database into an opinionated, priority-sorted task
board. Notion is the backend; the app renders it its own way. Tagline: Everything sorted.
Nothing buried.

Status at time of writing: **Phase 0 (theming), Phase 1 (onboarding + mapping), and Phase 2
(priority grid + read rendering) are complete and verified on-device.** Write-back (Phase 3),
AI (Phase 4), and the widget (Phase 5) are not started.

---

## 1. Architecture and tech stack

**Two Gradle modules, ports-and-adapters split:**

- **`:core`** — pure Kotlin (`kotlin("jvm")`), **no Android or Compose dependencies**. All
  business logic and data live here so it can be lifted into a Kotlin Multiplatform
  `commonMain` later. Contains the theme/token math, Notion client, OAuth logic, mapping
  layer, board projection, and the domain ports (interfaces the platform implements). 31 main
  files, 11 test files.
- **`:app`** — Android + Compose. Implements the core ports (OkHttp, Room, Keystore, DataStore,
  WorkManager, Custom Tabs) and holds all UI. 44 Kotlin files.

**Dependency injection is manual** (`di/AppContainer.kt`, reached via `SiftApp.container`).
Hilt was attempted and removed: its Gradle plugin is incompatible with AGP 9.2 (looks for the
removed `Android BaseExtension`). Core uses constructor injection; the container wires adapters
to it.

**Build stack (bleeding edge):** Kotlin 2.2.10, AGP 9.2.1, Gradle 9.4.1, Compose BOM
2026.02.01, KSP, minSdk 26 / target+compile 36. Notable build workarounds:
- `android.disallowKotlinBucketSets=false` in `gradle.properties` so KSP (Room) can register
  generated buckets under AGP 9's built-in Kotlin.
- The kotlinx-serialization **compiler plugin is applied only in `:core`**; `:app` uses the
  runtime library and core-generated serializers (avoids a built-in-Kotlin conflict).
- There is no system Java on the build machine; Gradle runs with Android Studio's bundled JBR
  as `JAVA_HOME` (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

**Notion API:** version `2025-09-03` (data sources as the primary abstraction). Capabilities
requested are exactly **Read content, Insert content, Update content** — nothing else (no user
info, no comments).

---

## 2. Phase 0 — Theming and token system

Ported `docs/sift-tokens.js` into `:core` as the KMP-ready design foundation.

**Added (`:core/theme`):**
- `Color.kt` — OKLCH/OKLab color model and the native equivalent of CSS `color-mix(in oklab)`
  (the web bucket composites tints this way; native resolves them up front to sRGB).
- `Tokens.kt` — `BUCKETS`, `PRIORITY_META` (the fixed urgency order), five `THEMES` (Paper,
  Slate, Ink, Linen, Cyber), `THEME_ORDER`, and the sample board data.
- `Derive.kt` — `derive(theme)` → `DerivedTheme` (header washes, count pills, accent text,
  bucket colors, overdue colors) resolved to concrete colors.

**Added (`:app/ui/theme`):**
- `Theme.kt` — `SiftTheme` provider (a token swap = a theme switch) exposing named tokens via a
  CompositionLocal, plus a Material3 color-scheme mapping so stock widgets stay coherent.
- `Color.kt` — the single `SiftColor` → Compose `Color` bridge.
- `Fonts.kt` / `Type.kt` — Bricolage Grotesque (display), Hanken Grotesk (body), Material
  Symbols Rounded (icons).

Default theme is **Paper**. The five themes exist in core but there is **no theme-switcher UI
yet** (Settings shows "Themes — coming soon").

---

## 3. Phase 1 — Onboarding and the mapping layer

The spine of the product: get a user from install to a working, mapped Notion database, through
two doors that converge on one mapping everything reads and writes through.

### Core (`:core`)

**Notion client (`notion/`):**
- `HttpTransport.kt` — the one seam to a platform HTTP engine (plain request/response types).
- `RateLimiter.kt` — ~3 requests/second, shared process-wide.
- `Backoff.kt` — exponential retry honoring HTTP 429 `Retry-After`.
- `Pagination.kt` — cursor pagination with an **explicit `page_size` of 100**.
- `NotionClient.kt` — data-bucket-aware API: search data sources, retrieve database/data
  bucket, `POST /data_sources/{id}/query`, create database, create/update page, search pages,
  retrieve page blocks. Sets `Notion-Version`.
- `NotionParser.kt`, `NotionError.kt` (typed failures incl. `Unauthorized` → reconnect),
  `model/NotionModels.kt`.

**OAuth (`oauth/`):** `OAuthConfig`, `OAuthRequestFactory` (authorize URL, redirect parse,
token exchange), `OAuthModels` (`TokenBundle`, `AuthorizationResult`). Token exchange goes to a
**backend proxy** so the client secret never ships in the app.

**Mapping layer (`mapping/`) — the highest-risk correctness property:**
- `Role.kt` (PRIORITY, BUCKET, STATUS, TITLE required; DUE_DATE, NOTES, WORK_FLAG optional),
  `PropertyType.kt`.
- `Mapping.kt` — `DataSourceRef` (resolves to a **data source within a database**, not just a
  db id), `RolePropertyBinding`, priority/bucket bindings, `DatabaseMapping`, and `MappingSet`
  which **holds a list of mappings** (v1 uses one) so multi-database consolidation is additive.
- `MappingValidator.kt` (required roles + type compatibility, plain-language messages),
  `AutoMatcher.kt` (Door B option matching, never collapses extras), `SchemaTemplate.kt`
  (Door A opinionated schema, seed rows, and the derived mapping).
- **No property name is ever hardcoded** — all reads/writes resolve through the mapping by id.

**Domain (`domain/`):** `SiftTask`, `NotionRecordMapper` (reads a Notion page **by property id,
not name**), `OnboardingService` (resumable Door A provisioning + Door B), `SyncRules`, and the
ports `TokenStore` / `MappingStore` / `TaskCache`.

### App (`:app`)

- **Adapters:** `OkHttpTransport`, `KeystoreCrypto` (AES-GCM in the Android Keystore),
  `SecureStores` (token + mapping in Keystore-encrypted DataStore), `TaskCacheRoom` (Room, cache
  only), `SiftRepository`, `AuthRepository`, `OAuthLauncher` (Custom Tabs), `OAuthRedirectActivity`
  (captures `sift://oauth`), `RefreshWorker` + `RefreshScheduler` (WorkManager, ~15 min + manual).
- **DI/App:** `AppContainer`, `SiftApp`.
- **UI (`ui/onboarding`):** Welcome, Connect explainer, Door choice, Door A provision (with a
  granted-page picker when more than one page is shared), Door B database picker, Door B mapping,
  Confirmation. Plus navigation and `MainActivity`.

**OAuth specifics (resolved during bring-up):**
- Uses a **Cloudflare Worker proxy** (`proxy/worker.js`) that holds the client secret and does
  the token exchange. Config comes from `local.properties` via `BuildConfig`
  (`notion.clientId`, `notion.oauthProxyUrl`, optional debug `notion.devToken`).
- Notion only accepts **https** redirect URIs, so the proxy exposes `/oauth/callback` that
  **302-bounces** the browser to the app's `sift://oauth` scheme (which the manifest catches).
- Reconnect is **guarded** when OAuth is not configured (shows a message instead of a broken
  link). A debug **dev-token path** seeds a personal integration token so the real Notion API
  can be exercised without OAuth.

---

## 4. Phase 2 — The priority grid and read rendering

The product's visible identity: the user's mapped tasks as a color-coded, app-controlled grid.
Read-only in this phase.

### Core (`:core/board`)

- `BoardSettings.kt` — the editable display layer (separate from the Notion binding truth):
  `PriorityView` (name, order, color, glance, hidden), `BucketView` (name, color, icon, schedule),
  `PrioritySchedule`, plus `minimized`, `twoColumn`, `use24HourTime`, `sampleMode`. Seeds from a
  mapping via `fromMapping()`.
- `BoardProjection.kt` — `projectBoard(tasks, settings, ClockSnapshot)` groups tasks into visible
  priorities, applies minimize and **per-bucket** time gating, orders overdue → dated → undated, and
  splits out completed items. Clock-injected and pure (no `java.time` in core).
- `BoardColors.kt` — resolves real priority/bucket colors from the active theme.
- `BoardSettingsEdits.kt` — pure edits: rename/recolor/reorder/hide/remove/add/align priorities;
  bucket rename/recolor/icon/schedule; `reorderPriorities`.
- `TimeFormat.kt` — AM/PM (default) and 24-hour formatting.
- `SampleBoard.kt` — debug generator that fills priorities with varied counts.
- `BoardSettingsStore` port (added to `domain/ports/Ports.kt`).

### App (`:app`)

- **State/storage:** `BoardSettingsDataStore` (plain DataStore), `BoardViewModel`,
  `CustomizeViewModel`.
- **Board UI:** `BoardScreen` (adaptive staggered grid, 2-column glance vs 1-column fuller rows,
  minimize/expand, layout toggle, refresh, an **"Updated at …" pill**, and an **Add-task FAB**),
  `BoardComponents` (priority card, task row, bucket tile, count pill), `BoardIcons` (Material
  Symbols by codepoint).
- **Focused priority:** `FocusedPriorityScreen` — full-screen priority with bucket filter chips, a
  "Completed today" group, and an "Add a task — coming soon" affordance. Opened via a scale+fade
  transition.
- **Task detail:** `TaskDetailSheet` — a bottom sheet showing mapped fields plus the task's
  **Notion page body** (fetched via `retrievePageBlocks`); "Notion comments — coming soon".
- **Customize:** `CustomizeBoardScreen` — **tabbed** (Priorities / Buckets / Archived).
  Tapping a priority or bucket opens a full **editor bottom sheet**; priorities **reorder by
  press-and-hold anywhere on the row** (drag-to-reorder). `AddTaskSheet` is a placeholder for
  the Phase 3 add flow.
- **Settings:** `SettingsScreen` (redesigned, grouped: Board / Display / Your Notion / Coming
  soon / Developer / About), `DeveloperScreen` (sample-data toggle + data-bucket details),
  `SettingsComponents` (`ComingSoonPill`, `SettingsGroup`, `SettingsRow`).

---

## 5. Notable changes and removals across iterations

- **DI:** Hilt removed → manual `AppContainer` (AGP 9 incompatibility).
- **Fonts:** the Google Fonts **downloadable provider was removed** — it did not serve fonts on
  device (icons showed literal words, then tofu; text fell back to system sans). All three
  families are now **bundled local variable fonts** in `app/bucket/main/res/font/`. Material Symbols
  is **subset** to the ~60 glyphs used (15 MB → ~130 KB), which cut the APK from 21.5 MB to
  ~14.8 MB. `font_certs.xml` and the `ui-text-google-fonts` dependency were deleted.
- **Startup performance:** added an on-open refresh, cached the Keystore key (was reloaded per
  decrypt), and shared the mapping flow (decrypted once) — cold start went from sluggish to
  near-instant.
- **Settings:** the flat list was **redesigned** into grouped cards; data-bucket technicals and
  the sample-data toggle moved into a dedicated **Developer** screen.
- **Customize UX:** replaced tiny inline icon buttons with **tap-to-open editor sheets**; added
  **tabs**; added **drag-to-reorder** (whole row is the handle); moved "Add" to the top.
- **Time gating:** **removed from priorities** entirely (it made no sense); it is now
  **per-bucket only**, configured inside each bucket's editor. The global gating toggle was
  removed.
- **Clock preference:** the 24-hour toggle **moved from Customize to Settings → Display**.
- **Board interaction:** tapping a **task** opens its detail drawer; tapping the **priority
  header/card** opens the focused priority; an **Add FAB** is present in both layouts.
- **"Coming soon" pills** added wherever a feature is intentionally not built (Settings roadmap,
  task-detail comments, add-task).

---

## 6. What is not built yet (deferred, marked "coming soon" in-app)

- **Write-back (Phase 3):** add, edit, complete, and move tasks; drag-to-reprioritize; and
  app-created priorities syncing to Notion (creating a Notion priority option is a write). App-added
  priorities are currently Sift-only display priorities labeled "Added in Sift only".
- **Notion comments** in the task detail (needs the Read-comments capability + a re-auth).
- **Theme switcher UI** (five themes exist in core).
- **Home screen widget (Phase 5, Glance)** and **AI (Phase 4)**.
- **Persisted last-sync time** across cold start (currently in-memory for the session).

---

## 7. Building, running, and testing

- **Set JAVA_HOME:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Logic tests (no device):** `./gradlew :core:test` (theme, Notion infra, mapping, OAuth,
  onboarding, board projection/edits — all passing).
- **Install:** `./gradlew :app:installDebug` with an emulator or device attached.
- **Secrets** live in `local.properties` (git-ignored) → `BuildConfig`:
  `notion.clientId`, `notion.oauthProxyUrl`, and optional debug `notion.devToken`. The OAuth
  proxy is deployed separately (`proxy/`, a Cloudflare Worker; see `proxy/README.md`). The client
  secret is a Cloudflare secret, never in the app.
- **Dogfooding:** set `notion.devToken` to a personal integration token and toggle
  Settings → Developer → "Show sample tasks" to exercise layouts without live data.

---

## 8. Bucket tree reference

```
core/                       (pure Kotlin, KMP-ready)
  theme/     Color, Tokens, Derive
  notion/    HttpTransport, RateLimiter, Backoff, Pagination, NotionClient,
             NotionParser, NotionError, model/NotionModels
  oauth/     OAuthConfig, OAuthRequestFactory, OAuthModels
  mapping/   Role, PropertyType, Mapping, MappingValidator, AutoMatcher, SchemaTemplate
  board/     BoardSettings, BoardProjection, BoardColors, BoardSettingsEdits,
             TimeFormat, SampleBoard
  domain/    SiftTask, NotionRecordMapper, OnboardingService, SyncRules, ports/Ports

app/  (com.ironclinicgym.sift)
  MainActivity, SiftApp
  di/        AppContainer
  data/      notion/OkHttpTransport, secure/{KeystoreCrypto, SecureStores, SecureStateGenerator},
             local/{TaskCacheRoom, BoardSettingsDataStore}, repository/SiftRepository
  auth/      AuthRepository, OAuthLauncher, OAuthRedirectActivity
  work/      RefreshWorker, RefreshScheduler
  ui/theme/  Theme, Color, Fonts, Type
  ui/common/ SiftUi, AppViewModel
  ui/navigation/ Routes, SiftNavHost
  ui/onboarding/ Welcome, ConnectExplainer, DoorChoice, DoorAProvision,
                 DoorBDatabasePicker, DoorBMapping, Confirmation, OnboardingViewModel
  ui/board/  BoardScreen, BoardViewModel, BoardComponents, BoardIcons, FocusedPriorityScreen,
             TaskDetailSheet, CustomizeBoardScreen, CustomizeViewModel, AddTaskSheet
  ui/settings/ SettingsScreen, SettingsComponents, SettingsViewModel, DeveloperScreen

proxy/  worker.js, wrangler.toml, README (OAuth token-exchange proxy)
res/font/ bricolage_grotesque, hanken_grotesk, material_symbols_rounded (bundled, subset)
```
