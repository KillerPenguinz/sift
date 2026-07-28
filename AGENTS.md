# Sift agent instructions

This project's governance, standing constraints, and required workflow live in
[CLAUDE.md](CLAUDE.md). It is the canonical instruction file. Read it in full and
follow it exactly, whichever agent or client you are (Claude Code, Codex, or
otherwise). Do not start work without loading it.

Two systems are mandatory on every session (see "Ways of working" in CLAUDE.md):

- **Storybloq** is the tracker. Load it first: `$story` in Codex, `/story` in
  Claude Code, or the `storybloq` CLI (`storybloq status`, `storybloq recap`,
  `storybloq handover latest`). The `.story/` directory at the repo root is the
  single source of truth for current work, the roadmap, open tickets and issues,
  and prior handovers. Work from a ticket; create one first if the work is not
  tracked yet. If the MCP tools or hooks are missing, run
  `storybloq setup --client all` and restart the client.

- **The superpowers process discipline** governs how decisions get made:
  brainstorm before designing anything, plan before building, build test first,
  verify before claiming done, and review before merging. These ship as skills in
  Claude Code; agents without the plugin must still follow the same discipline.

Every architectural or product decision follows the loop in CLAUDE.md's "Ways of
working" and is recorded in both storybloq and the Notion decision log.
