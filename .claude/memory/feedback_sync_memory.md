---
name: Sync memory to project repo
description: When updating main memory, also sync the changed files to .claude/memory/ in the project repo
type: feedback
---

When main memory files are updated, always sync the changes to the project's `.claude/memory/` directory and commit.

**Why:** The project repo contains a copy of Claude Code's memory so other users or future clones can access it.

**How to apply:** After writing/updating any memory file in the system memory directory, copy it to `.claude/memory/` in the project, then `git add` and commit.
