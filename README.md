# Sift

Sift turns any Notion database into an opinionated, urgency-bucketed task board on Android.

Notion is the backend; Sift is the front end. Your tasks live in your own Notion workspace,
and Sift projects them onto a board organized by how soon each thing actually needs doing.
There is no separate account, no second database to keep in sync, and no lock-in: delete the
app and your data is exactly where it always was.

> Status: work in progress. Phases 0 through 3 are built and verified. Phase 3.5 (the Two
> Axis Model) is implemented and currently in user-acceptance testing. See
> [Build status](#build-status) below.

## Why Sift

Most task apps make you assign a priority by hand and then quietly lie to you as those
priorities go stale. Sift takes a different stance:

- **Dates drive priority.** When a task has a due date, Sift assigns its urgency tier for you
  and keeps it current as time passes. You never pick "high / medium / low."
- **Two axes, not one.** *Priority* is how soon (asap, today, tomorrow, soon, later, one day).
  *Bucket* is which part of life (Work, Personal). They are independent.
- **Pinning is visibility, not priority.** Pin a task to surface it without lying about when
  it is actually due.
- **Brain dump is a real place.** Uncommitted ideas get their own space instead of polluting
  your live board.

## How it works

Sift reads and writes through a configurable **mapping layer**: you tell it once which Notion
properties play which roles (title, date, status, bucket, labels), and every read and write
goes through that binding. Nothing about a property name is hardcoded, so a bring-your-own
Notion database works the same as one Sift sets up for you.

- **Priority** is derived from due dates via configurable bands (overdue or due today = asap,
  due tomorrow = tomorrow, 2 to 7 days = soon, 8 to 30 days = later, 30+ = one day).
- **Undated tasks** can be dragged freely between tiers. **Dated tasks** stay honest: dragging
  one prompts you to change the date, snooze, pin, or remove the date instead.
- App-operational state (pins, protected flags, dismissals, counts) lives in a local Room
  database keyed to the Notion page ID. Your actual content stays in Notion.

## Stack

| Area | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Min / Target SDK | 26 / 36 |
| Build | Gradle with version catalogs |
| Dependency injection | Manual (AppContainer pattern, no Hilt or Dagger) |
| Local storage | Room, keyed to Notion page ID |
| Auth | OAuth 2.0 via a Cloudflare Worker proxy |
| Notion API | Version 2025-09-03 (data-source model) |
| Fonts | Bricolage Grotesque (headings), Hanken Grotesk (body) |

## Module structure

```
:core   Pure Kotlin. Business logic, Notion client, mapping, domain models.
        UI-agnostic and KMP-extractable (no Android or Compose imports).

:app    Android application. Compose UI, ViewModels, DI, auth, WorkManager.

proxy/  Cloudflare Worker that performs the confidential-client OAuth token
        exchange so the Notion client secret never ships in the APK.

docs/   Phase specs, design direction, tokens, and build synopses.
```

A fuller architecture and terminology reference lives in [CLAUDE.md](CLAUDE.md).

## Build status

| Phase | Scope | State |
| --- | --- | --- |
| 0 | Theming | Built and verified |
| 1 | Onboarding and mapping | Built and verified |
| 2 | Read grid | Built and verified |
| 3 | Plain write layer | Built and verified |
| 3.5 | Two Axis Model | Implemented, in UAT |
| 4+ | AI capture, widget, and beyond | Not started |

## Building

You need Android Studio (for its bundled JDK) or a JDK 17+ and the Android SDK.

```bash
./gradlew :app:installDebug        # Build and install the debug APK
./gradlew build                    # Full build with lint
./gradlew :core:test               # Core module unit tests
./gradlew :app:testDebugUnitTest   # App module unit tests
```

### Secrets

OAuth configuration is read from `local.properties`, which is git-ignored:

```
notion.clientId=<public OAuth client ID>
notion.oauthProxyUrl=<Cloudflare Worker URL>
notion.devToken=<optional internal integration token for debug builds>
```

The OAuth token exchange is handled by the Cloudflare Worker in [`proxy/`](proxy/); see
[proxy/README.md](proxy/README.md) for deployment. The Notion client secret lives only as a
Worker secret binding and never touches the app.

## License

Sift is free software licensed under the GNU General Public License, version 3.0. See
[LICENSE](LICENSE) for the full text.

## Author

Built by BJ Nelson.
