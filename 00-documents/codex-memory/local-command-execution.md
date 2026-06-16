---
name: Local command execution preference
description: Prefer direct host command execution when local verification requires it
type: user
---

When working in this repository, the user allows commands to be executed directly on the local host when needed, instead of being limited to sandbox execution.

Apply this by using escalated/local execution for build, Gradle, dependency, and verification commands that need normal filesystem or network access. Still follow tool permission requirements, and do not run destructive commands unless the user explicitly requests them.
