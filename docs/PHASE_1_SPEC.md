# Phase 1 Handoff to Claude Code (consolidated)

> This is a self contained handoff for Claude Code to plan and build Phase 1 of Sift. Everything Claude Code needs to begin is on this page. It consolidates the Phase 1 PRD, the locked architecture decisions that touch this phase, the verified Notion API facts, and the planning prompt. Bucket of truth remains the individual docs in this workspace; this is an assembled export for the build. No em dashes or hyphens in any user facing text the app produces.
> 

## 0. What Sift is (one paragraph of context)

Sift is an Android app and home screen widget that turns any Notion database into an opinionated, priority-sorted task board. Notion is the backend, not the frontend: the app pulls raw data through the Notion API and renders it its own way. Phase 1 is onboarding and the mapping layer: get a user from fresh install to a working, mapped Notion database in under a minute, through two doors (auto provision, or bring your own with field mapping) that converge on one mapping layer that everything later reads and writes through. Tagline: Everything sorted. Nothing buried.

## 1. Locked architecture decisions that govern this build

- **Stack: native Kotlin.** Jetpack Compose for the app, Glance for the widget (widget is a later phase, but structure for it). This is the most documented, lowest friction Android path.
- **Business logic is a clean, UI agnostic, KMP ready layer.** The entire logic core (the mapping layer, the Notion API client with rate limiting and pagination, the local cache, sync rules, and later the AI calls) must be built as standalone, UI independent primitives, as if they will be extracted into a Kotlin Multiplatform shared module later for an iOS build. Do not entangle this logic with Compose or Android UI types. This is plain Kotlin and must stay portable.
- **Build the mapping layer and Notion client first, before any UI.** Everything reads through them.
- **Auth: public OAuth 2.0** (Notion public connection). No token pasting in the shipped app. A personal access token path is acceptable only for early personal dogfooding, not the shipped flow.
- **Capabilities requested: exactly Read content, Insert content, Update content. Nothing else.** Do NOT request Read user information (including email) in v1.
- **Refresh model: pull based, periodic plus manual.** Use WorkManager for periodic refresh (realistic floor around fifteen minutes) plus a manual refresh action. Local cache (Room is the default assumption) so the UI shows last known data instantly. No real time, no webhooks in v1.
- **Rate limit and pagination are mandatory.** Respect about three requests per second with retry and backoff. Paginate list queries, explicitly passing page_size 100 (the implicit default of 100 is no longer guaranteed; pass it explicitly).
- **Secure storage.** The OAuth token and the mapping must be stored using the platform's secure storage.
- **No em dashes or hyphens** in any user facing string the app renders.

## 2. Verified Notion API facts (current as of mid 2026, confirmed by research)

- **Capability model, not scopes.** Notion permissions are capabilities chosen at connection creation. A task tool that creates and edits records needs Read, Insert, and Update content. That trio is exactly Sift's need.
- **Public connection uses OAuth 2.0 with Notion's own page picker.** After a user authorizes, only that user can interact with the pages they shared; each user runs the auth flow themselves. Handle user cancel and OAuth error redirects gracefully.
- **A connection can only create a database inside a page it has been granted access to.** This is why Door A creates the Sift database inside the page the user picks or creates during the OAuth grant.
- **Database vs data source.** Notion now treats a database as a container holding one or more data sources (the actual tables of records). The mapping must resolve to a specific data source within a database, not just a database id. Build the client and mapping with the data source abstraction in mind (API version line around 2025-09-03 and later introduces data sources as the primary abstraction).
- **Self identity after OAuth.** The connected workspace and authenticated user identity can be retrieved (a self lookup) so the app can show which workspace was connected. This needs only the granted content capabilities, not the user information capability.
- **Public connection listing requires a Notion security review.** Creating and using a public connection does not require listing, but listing it on the Marketplace does require passing review. That review is a release task (later phase), not a Phase 1 blocker, but build cleanly with it in mind.

## 3. Phase 1 specification

### 3.1 Goals

- Authenticate the user to their own Notion workspace through Notion's OAuth page picker, with no token pasting.
- Offer two clearly distinguished onboarding doors: recommended auto provision, and bring your own database.
- Door A: create an opinionated task database inside the user's chosen granted page, seeded with default priorities and a couple of example rows, and pre fill the mapping automatically.
- Door B: let the user select an existing database and map their properties to the required roles (priority, bucket, status, title) plus optional roles.
- Persist the mapping locally so all later phases read through it.
- Validate that the selected or created database can satisfy the required roles before completing onboarding.
- Recommended door reaches a working board in under a minute.

### 3.2 Non Goals

- No board rendering beyond a minimal confirmation (the grid is Phase 2).
- No task write back beyond creating the Door A database and seed rows (add and edit is Phase 3).
- No AI (Phase 4). No widget (Phase 5).
- App side consolidation of more than one database into one grid is out of v1 (deferred), but the mapping layer is built multi ready (a list of mappings) so it can be added later. v1 reads exactly one mapped database.
- No token paste onboarding in the shipped app.

### 3.3 Functional requirements

**Authentication**

- The user connects their Notion workspace through Notion's OAuth flow and grants access to selected pages or databases via Notion's page picker.
- Request exactly Read content, Insert content, Update content. Do NOT request Read user information.
- Before handing off to OAuth, tell the user in plain language that they will pick or create a page where Sift lives, so the grant screen is not confusing.
- If the user grants too little (for example no database), guide them back to grant access or to create one via Door A.
- Authorization must be revocable from Notion's side without the app crashing; on revoked access show a clear reconnect path.
- After OAuth, optionally use the self lookup to show the user which workspace they connected.

**Door A: recommended auto provision**

- The user chooses a recommended setup that creates a task database for them.
- Create the database inside a page the user picked or created during the OAuth grant. No separate post auth location picker.
- The created database uses the opinionated schema: a priority property whose options are the default priorities (asap, today, tomorrow, soon, later, one day); a bucket property seeded with the design's default buckets (Work, Personal) that the user can edit; a status or done property; and a title property.
- Seeded bucket values are the design defaults (Work, Personal), not creator specific. No BJ preset.
- Seed a small number of example rows so the board is not empty on first view.
- Pre fill the internal mapping from the created schema, requiring no mapping input from the user.
- After provisioning, show a confirmation that setup succeeded and the board is ready.

**Door B: bring your own database**

- The user chooses to use an existing database they granted access to.
- List the databases the connection can see and let the user pick one.
- Read the selected database's property schema and present a mapping screen.
- The user maps their properties to the required roles: priority (the urgency property), bucket (the color and icon grouping property), status or done, and title.
- Let the user map optional roles where present (for example due date, notes or description, a work flag for time gating).
- For the priority role, the user's own options are the source of truth and become the grid's priorities (the six defaults do not impose in Door B). Auto match obvious name matches to pre fill color and order, show any unmatched options for the user to resolve, and never silently collapse extras into a catch all.
- Validate that chosen property types are compatible with each role (for example priority maps to a select or status type, not freeform text) and explain incompatibilities in plain language.
- Allow the user to proceed only when the required roles are satisfied.

**The mapping layer (shared, the spine of the product)**

- Store an internal mapping from role to the user's actual property identity, and use it for every read and write in all later phases.
- The mapping resolves to a specific data source within a database, not merely a database id.
- The mapping layer must hold a list of mappings, not a single mapping, so app side multi database consolidation can be added later without rearchitecting. v1 uses exactly one entry.
- The mapping survives app restarts and is re editable from settings without redoing onboarding, including re pointing or re mapping the single board to a different database.
- Never hardcode a property name; all access is through the mapping. This is the highest risk correctness property in the app: a single hardcoded name silently breaks the bring your own door.

### 3.4 Non functional requirements

- Door A reaches a ready board in under a minute on a normal connection.
- All Notion API calls respect the rate limit (about three per second) with retry and backoff, and paginate with explicit page_size 100.
- Resilient to partial failure: if database creation or mapping fails midway, surface an actionable error and do not leave a dead state.
- No em dashes or hyphens in user facing text.
- The mapping and tokens are stored in secure storage.

### 3.5 Inputs and outputs

- **Inputs:** the user's OAuth authorization and page grant; door choice; for Door A the chosen creation page and any edits to seeded buckets; for Door B the chosen database and the property to role mapping.
- **Outputs:** for Door A a created Notion database with the opinionated schema and seed rows; for both doors a persisted internal mapping and stored authorization; a completion state indicating the board is ready for Phase 2.

### 3.6 Acceptance criteria

- A new user connects Notion via OAuth without pasting any token.
- Door A creates a database with the default priorities, an editable bucket property seeded with the design defaults (Work, Personal), a status or done property, and a title, seeds example rows, pre fills the mapping with no user mapping input, and reaches ready in under a minute.
- Door B lists visible databases, reads the chosen schema, and presents a mapping screen for priority, bucket, status, title, plus optional roles.
- In Door B, the user's priority options drive the priorities; auto match pre fills, unmatched options are surfaced, incompatible types are explained and blocked.
- Onboarding completes only when required roles are satisfied.
- The mapping persists across restart and is re editable from settings without redoing onboarding.
- Revoking access in Notion leads to a clear reconnect path, not a crash.
- All API calls respect rate limits and pagination; partial failures surface actionable errors.
- The mapping layer holds a list of mappings (v1 uses one) and resolves to a data source within a database.
- No hardcoded property names anywhere; all access flows through the mapping.
- No user facing text contains em dashes or hyphens.

## 4. The planning prompt to run first

Set up an empty native Kotlin project with Jetpack Compose (and a Glance widget target stubbed for later) and open it where Claude Code runs, then run this:

```
I am working on Sift, an Android application built in Kotlin with Jetpack Compose, and a home screen widget built with Glance (widget comes in a later phase). Here is a FINAL SPEC for Phase 1, onboarding and the mapping layer.

Please:

1. Inspect the existing project.
2. Propose a concrete implementation plan that aligns with the project as it exists right now.
3. List:
   - Files to create
   - Files to modify
   - For each file: what needs to change at a high level (no full code yet)
   - Any new composables, view models, data models, repositories, services, or persistence schemas that should be added
4. Call out any conflicts, missing pieces, or concerns based on the current codebase.

Rules:

- Do NOT write full implementations yet.
- You may show short code snippets or signatures to illustrate intent, but your main goal is a file level plan that is consistent with the project.
- Build the business logic (mapping layer, Notion API client, cache, sync rules) as a clean, UI agnostic, KMP ready layer, decoupled from Compose and Android UI types, so it can be extracted into a Kotlin Multiplatform shared module later. Flag any plan step that would entangle logic with UI.
- Build the mapping layer and Notion client first, before any UI.
- The app must never hardcode a Notion property name; all reads and writes go through the stored mapping. The mapping must hold a list of mappings (v1 uses one) and resolve to a specific data source within a database. Flag any plan step that would violate this.
- Respect Android conventions for Compose UI, app lifecycle, background work with WorkManager, local persistence (Room assumed), secure token storage, and the Notion OAuth and data contract.
- Notion capabilities requested are exactly Read content, Insert content, Update content. Respect the rate limit (about three requests per second) with backoff, and paginate with explicit page_size 100.
- If the spec clashes with the current architecture, point that out and suggest alternatives.

Output format:

# Implementation Plan
## 1. Overview
High level summary.
## 2. Files to Create
- path/to/file.kt: short description
## 3. Files to Update
- path/to/file.kt: short description of changes
## 4. Notes / Concerns
- Any integration risks, inconsistencies, or questions.
```

After the plan, let Claude Code implement directly from its own plan in the project, then run the validation prompt from the Build Pipeline Prompts page. Bring in a second model as an independent reviewer at the validation step if desired.

## 5. Things to verify outside the AI tools (for BJ)

- Test the OAuth flow with a real second Notion account that is not the developer's own, to confirm the bring your own door and the access grant work for a stranger's workspace shape.
- Confirm the OAuth token and mapping land in secure storage, not plain preferences.
- Keep the public connection security review in mind as a later release gate; it does not block Phase 1.