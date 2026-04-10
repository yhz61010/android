---
name: No Co-Authored-By in commits
description: Do not add Co-Authored-By Claude line in git commit messages
type: feedback
---

Do not add `Co-Authored-By: Claude ...` or any similar attribution line in git commit messages.

**Why:** User wants commit messages to stay concise and clean.

**How to apply:** When generating git commit messages, omit the `Co-Authored-By` trailer entirely.
