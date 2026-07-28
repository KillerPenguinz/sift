# Sift agent instructions

Governance, standing constraints, and the full workflow live in
[CLAUDE.md](CLAUDE.md), the canonical instruction file. Read it in full before
doing anything.

## Roles

- **Claude Code** does all planning and implementation, using the superpowers
  workflow (brainstorm, spec, plan, tasks, TDD, review), and owns every design
  and product decision. That is where the work happens.
- **Codex (you, when invoked) is an occasional coding executor only.** You write
  code for a specific ticket that Claude Code has already brainstormed, specced,
  and planned. You do not brainstorm, spec, plan, or make design or product
  decisions.

## When you are asked to code a task

1. Load the tracker: `$story`, or `storybloq status` then
   `storybloq ticket get <id>`. The `.story/` directory at the repo root is the
   source of truth for the ticket, its plan or spec, and status.
2. Open the assigned ticket, read its linked plan or spec, and read the hard
   constraints in CLAUDE.md.
3. Implement exactly that ticket, test first, honoring every constraint. Do not
   expand scope or redesign.
4. If anything is ambiguous or would require a design choice, stop, file a
   storybloq issue or blocker, and hand back to Claude Code or the maintainer.
   Do not guess.
5. Update the ticket status and keep the automated tests green.

If you need docs for a library, framework, SDK, API, or CLI and the Context7 MCP
is available, use it (`resolve-library-id` then `query-docs`) instead of relying
on training data. See the Context7 section in CLAUDE.md for the project's seed
library ids.

Superpowers is a Claude Code plugin and is not available or used here. If
storybloq's tools are missing, run `storybloq setup --client all` and restart.
