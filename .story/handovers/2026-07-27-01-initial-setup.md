# Initial setup and backlog mapping

## What happened this session
1. Published the project to GitHub and hardened it (repo now at github.com/KillerPenguinz/sift, public, GPL-3.0).
   - Removed a leaked Cloudflare account-id cache (proxy/.wrangler) from all of main's history; added a .githooks/pre-commit guard (blocks caches, local.properties, keystores, and token-shaped content) plus GitHub secret-scanning + push protection.
   - Renamed the GitHub owner from bjnelson262 to KillerPenguinz; rewrote all 46 commits to KillerPenguinz <noreply> so the personal gmail is out of history. Repo-local git identity set accordingly.
   - Dropped the maintainer's name from CLAUDE.md (kept the README credit line).
2. Initialized this storybloq tracker at the repo root and mapped all remaining work.

## Project state
Phases 0-3 complete and verified. Phase 3.5 (Two Axis Model) in progress (on-device UAT). Phase 4+ not started.

## Roadmap decisions recorded here
- Phase 4 was expanded from "AI capture, unspec'd" to "AI capture + Pebble Watch 2 companion." Watch is a thin BLE client; the Sift Android app is the bridge (PebbleKit AppMessage) holding the Notion connection and running the AI capture. See note N-002 for the full vision and open questions.
- Phase 3.5 now carries four concrete, spec'd-but-unbuilt items surfaced by an independent review: blocked-flag UI, self-quieting Added-to-priority chip, wiring date-to-priority bands to Settings, wiring quick-date defaults to Settings.
- Backlog additions from the review: stand up support.siftapp.com (settings links are dead today), multi-database DIY article + in-app note, Daily Briefing Space, remove dead AddTaskSheet V1, verify BYO recurrence-column consent.
- Rejected (note N-001): complete/delete dual-swipe in task detail.

## Open follow-ups for the maintainer
- Mirror the Phase 4 Pebble decision into the Notion Phase Tracker (could not reach Notion from this non-interactive session).
- Confirm GitHub Settings > Emails > "Block command line pushes that expose my email" is enabled.
- storybloq CLI is installed but the MCP full toolset and hooks may need `storybloq setup --client all` + a client restart for hook automation.

## Next work
Start with T-005 (Phase 3.5 UAT round 3 sign-off), or pick from the Phase 3.5 gap tickets (T-006 to T-009).
