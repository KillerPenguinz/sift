# Phase 3 UAT: Category C Items (Design Discussion Needed)

> Items from BJ's Phase 3 UAT that need design discussion before they can be built. Numbered to match the original UAT notes. Work through these with BJ, then hand the resolved ones to Claude Code.
> 

## #1 + #3b: should adding a task lead with WHEN, not which priority?

BJ's feedback: asking the user to pick a priority (asap/today/tomorrow) feels forced. It would be more intuitive to ask WHEN it is due (a date, or a rough range like soon/later/next week) and let the priority be inferred from that. But many tasks won't have a specific date, so we need a flow that handles both smoothly without forcing a date OR forcing a priority pick.

This is deeply tied to the Two Axis Model (Phase 3.5). The add task flow should probably be: enter title and bucket, then either set a date (which auto assigns a priority) or choose a rough urgency range (soon/later/one day), with the brain dump as an explicit option. The exact UX needs designing, not just a priority picker swap.

Also from #1: brain dump items should go to the brain dump space, not just the lowest priority. And brain dump items might still benefit from a date (a remind me to revisit this on date), which is an interesting wrinkle.

## #2: Daily Briefing Space (separate feature concept, not just polish)

**RESOLVED: a real, separate feature, not the minimized view.**

The minimized view = focused execution (immediate tasks, go). The briefing space = mission control for the day: full overview, coaching, potentially calendar, what happened, what is coming, how the plan looks. Same data, different mode and intent.

The briefing space is where daily briefing notifications land the user (Phase 7), where caring layer check ins live, and is always accessible on the user's own terms. Needs its own feature concept page and proper scoping. The minimized view stays as is. Name needs workshopping.

Phase 7 handles real briefings (scheduled, coach driven, configurable cadence). The space itself could land earlier as infrastructure.

## #4: redesign the add task UI

BJ finds the current pill button layout plain and overwhelming. Wants a simpler yet robust task creation interface that can grow to support future enhancements (time, date, pin, notes, protected flag, etc) without becoming cluttered.

Needs a design pass, likely with Felipe or through a focused design session. Should account for the two axis add flow (#1 above) and the bucket recolor (#5, already in the fix batch). Consider progressive disclosure: start with just title and bucket, expand for date/time/notes/etc.

## #6: labels via Notion's existing properties (RESOLVED)

Labels are just another mappable role using Notion's existing multi select or select properties. No custom Sift label system. The user maps an existing property as labels and Sift surfaces it as a filterable dimension.

**Matching architecture:** type inference (language proof, uses Notion property types) + name matching (synonym maps per language) combine for high confidence suggestions. Both run, highest confidence wins, user always has manual override. No conflicts between the two.

**Language support strategy (research grounded on Notion's actual user distribution, 100M+ users across 85 countries, 80%+ outside the US):**

Tier 1 (ship at launch): English, Korean (~12% of Notion users, 2nd largest market), Japanese (~11%), Portuguese/Brazil (~7%), French (~4%+), Spanish, German, Russian (native speaker available for immediate verification).

Tier 2 (near term): Chinese (Simplified + Traditional), Dutch, Italian, Hindi, Indonesian, Turkish, Polish.

Tier 3 (community requested): everything else, added as contributions come in.

Scale: ~25 synonyms per language, 15 languages = ~375 entries. Tiny data files.

**Community verification model (long term quality):**

- Initial drafts: AI generated, published as draft/needs verification in the repo.
- First verification: a native speaker reviews and corrects. Draft becomes verified.
- Ongoing: PRs to add synonyms, each needing at least one other native speaker to approve. More verifications over time produce stronger signals (2nd verifier catches what the 1st missed).
- Each language's synonym file includes metadata: verified (boolean), verifier count, last updated. Transparency drives contributions.

**For Claude Code now:** build the architecture, ship English complete, structure for easy language addition. See the Claude Code batch doc for specifics.

## #7 + #11 + #12: multi database support (RESOLVED, deferred)

Confirmed deferred: not v1, candidate Supporter feature, own future spec. Already architecturally anticipated (mapping layer is multi ready).

**DIY guide for free tier users:** document the Path 3 workaround (Notion does the merge) as a support site FAQ: How to use multiple Notion databases with Sift on the free tier. In the app, when the user might want to add another database, show a helpful note linking to this guide and noting that app managed multi database support is coming for Supporter tier users.

Notion database IDs are stable UUIDs that survive moves. Only revoking integration access breaks the connection; detect and prompt re sharing.

## Status

- **#2:** RESOLVED (Daily Briefing Space is its own feature concept, needs a page and proper scoping, not buildable yet)
- **#6:** RESOLVED (labels via mapped Notion property + smarter AutoMatcher + mapping UI redesign, actionable for Claude Code)
- **#7/#11/#12:** RESOLVED (deferred, DIY guide for free tier, support site article)
- **#1/#3b + #4:** OPEN, the big conversation, the add task flow redesign. These two are the same problem (what the add flow asks, and how it looks). Most urgent since they affect the core experience.

#1/#3b and #4 are deeply tied to the Two Axis Model (Phase 3.5) and should be resolved together. The add task UI cannot be finalized without deciding whether it leads with when or which priority.