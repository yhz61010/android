---
name: Bilingual documentation
description: General docs in both Chinese and English; CLAUDE.md and superpowers docs use Chinese only
type: feedback
---

When generating documentation, always produce two versions: English and Chinese (中文).

**Exceptions (Chinese only, no bilingual):**
- `CLAUDE.md` — always written in Chinese
- Superpowers generated documents (specs, plans)
- Everything under `00-documents/` — per CLAUDE.md, Chinese only

This narrowing is intentional, confirmed by the user on 2026-09-15. Do not
treat it as a conflict with this memory or re-raise it.

**Why:** User explicitly requested bilingual docs as default, with CLAUDE.md and superpowers output as Chinese-only exceptions.

**How to apply:** For general docs (guides, READMEs, etc.), create both English and Chinese versions. For CLAUDE.md and superpowers specs/plans, use Chinese only.
