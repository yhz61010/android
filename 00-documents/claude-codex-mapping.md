# Claude to CodeX Mapping

This repository keeps both Claude Code metadata and CodeX repository guidance.
The goal of this document is to explain how the existing Claude assets are reused by CodeX without removing or renaming any Claude-specific files.

## Principles

- Keep all Claude Code files intact.
- Use `AGENTS.md` as the primary CodeX entry point.
- Treat `CLAUDE.md` and `.claude/**` as supplemental guidance for CodeX.
- Promote only the most stable, high-value rules into `AGENTS.md`.
- Reuse existing Claude skills and memory files by reference instead of duplicating their full contents.

## Mapping

### `AGENTS.md`

`AGENTS.md` is the primary repository-level instruction file for CodeX.
It should contain:

- Stable repository workflow rules
- Language expectations
- File placement rules
- Explicit pointers to Claude files that CodeX should read when relevant

### `CLAUDE.md`

`CLAUDE.md` remains the Claude Code project guide.
For CodeX, it is treated as supplemental project context, especially for:

- Build and development commands
- Module overview
- Native build notes
- Testing and quality tooling
- SDK and tooling constraints

### `.claude/rules/*`

Claude rules map to on-demand supplemental instructions for CodeX.
Use them when the task is about coding style, writing style, or personal workflow conventions.

Current example:

- `.claude/rules/personal-style.md`

### `.claude/memory/*`

Claude memory files map to reusable repository preferences and historical decisions.
For CodeX, these should be referenced when relevant and partially promoted into `AGENTS.md` when they become stable team rules.

Current examples include:

- communication language preferences
- documentation directory conventions
- English-only requirements for comments and commits
- no `Co-Authored-By` trailer preference

### `.claude/skills/*`

Claude skills do not automatically become native CodeX skills.
In this repository they are reused through `AGENTS.md` instructions that tell CodeX when to read a specific skill file.

Current examples:

- `.claude/skills/mobile-android-design/SKILL.md`
- `.claude/skills/find-skills/SKILL.md`

If a Claude skill needs to become a first-class CodeX skill in the future, it should be migrated into the CodeX skills system explicitly rather than assumed to be auto-discovered.

### `.claude/commands/*`

Claude commands do not directly map to CodeX command primitives.
Keep them as Claude-specific workflow assets unless there is a clear need to convert them into:

- repository documentation
- shell scripts
- task-specific instructions in `AGENTS.md`

## Recommended Read Order For CodeX

When working in this repository, CodeX should use this order:

1. Read `AGENTS.md` first.
2. Read `CLAUDE.md` as supplemental project guidance.
3. Read specific `.claude/**` files only when the task matches their purpose.

Examples:

- For repository style or contributor conventions, read `.claude/rules/personal-style.md` and `.claude/memory/MEMORY.md`.
- For Android design work, read `.claude/skills/mobile-android-design/SKILL.md`.
- For skill discovery or skill-management requests, read `.claude/skills/find-skills/SKILL.md`.

## Maintenance Rules

When adding new Claude files in the future:

1. Keep the Claude file in its original location.
2. Decide whether the content is:
   - a stable repository rule
   - supplemental project context
   - a task-specific workflow
3. If it is a stable rule, summarize it in `AGENTS.md`.
4. If it is task-specific, add a short pointer in `AGENTS.md` only if CodeX should consult it automatically for matching tasks.
5. Avoid copying large Claude documents into `AGENTS.md` unless the instruction must always apply.

## Non-Goals

This mapping does not:

- remove `.claude/**`
- rename Claude files
- convert every Claude artifact into a native CodeX skill
- force both systems to share identical file formats

The purpose is interoperability, not full consolidation.
