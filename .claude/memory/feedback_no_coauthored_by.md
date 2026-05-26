---
name: No Co-Authored-By in commits
description: Never add Co-Authored-By trailer or noreply@anthropic.com in git commit messages
type: feedback
---

Do NOT add "Co-Authored-By: Claude ..." lines to git commit messages.

**Why:** The user explicitly does not want AI attribution in commit messages. They've asked for this multiple times.

**How to apply:** When creating git commits, omit the `Co-Authored-By` trailer entirely. The author should always be the user's own git config (Michael Leo), never include noreply@anthropic.com.
