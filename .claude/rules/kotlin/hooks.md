---
paths:
  - "**/*.kt"
  - "**/*.kts"
  - "**/build.gradle.kts"
---
# Kotlin Hooks

> This file extends [common/hooks.md](../common/hooks.md) with Kotlin-specific content.

## PostToolUse Hooks

Configure in `~/.claude/settings.json`:

- **ktlint**: Auto-format `.kt` and `.kts` files after edit (`./gradlew ktlintFormat`)
- **detekt**: Run static analysis after editing Kotlin files (`./gradlew detekt`)
- **./gradlew staticCheck**: Run the full quality suite (lint, detekt, ktlint, tests) before commit
