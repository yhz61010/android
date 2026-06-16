# Codex Memory Snapshot

This directory contains a repository-owned snapshot of the Codex memory files for this Android
project. It is intended for future agents and contributors who clone the repository and need the
project's historical working context.

## Load Order

When starting from a fresh clone, read these files in order:

1. `memory_summary.md`
2. `MEMORY.md`
3. `extensions/ad_hoc/notes/`
4. Task-specific files under `rollout_summaries/` when `MEMORY.md` points to them

`local-command-execution.md` records the local command execution preference that was part of the
original Codex memory set.

## Included Files

- `MEMORY.md`: searchable registry of durable project memories.
- `memory_summary.md`: compact overview of user preferences and recurring project knowledge.
- `local-command-execution.md`: local verification preference.
- `extensions/ad_hoc/`: explicit user-requested memory notes.
- `rollout_summaries/`: summarized historical sessions referenced by `MEMORY.md`.

## Excluded Files

The following files are intentionally not mirrored here:

- `.git/`: local Git metadata for the source memory folder.
- `raw_memories.md`: large raw memory export with more noise and potentially more local detail.
- Raw session JSONL files: implementation logs are not needed for normal project onboarding.

## Synchronization Rule

The active Codex memory source remains the local `~/.codex/memories` directory. This directory is a
shareable snapshot for the repository. When the active memory changes and the change should be
shared with future clones, refresh this directory from `~/.codex/memories` before committing.

Do not modify Claude-owned files while refreshing this snapshot. `CLAUDE.md`, `.claude/**`, and
external Claude memory directories are read-only references unless the user explicitly requests
otherwise.
