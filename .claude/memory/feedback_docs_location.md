---
name: Documents go in 00-documents
description: All generated docs (specs, plans, superpowers output) must be saved under 00-documents/, not docs/. Superpowers docs use Chinese only.
type: feedback
---

Generated documents must be saved under `00-documents/` directory, not `docs/`.

This includes superpowers specs and plans — they go to `00-documents/superpowers/specs/` and `00-documents/superpowers/plans/`.

Superpowers generated documents (specs, plans) should be written in **Chinese only** — no need for bilingual versions.

**Why:** Project convention — all documentation is centralized in `00-documents/`. User explicitly requested Chinese-only for superpowers output.

**How to apply:** Whenever creating spec files, plan files, or any generated documentation, use `00-documents/` as the root. Override any skill defaults that use `docs/`. Write superpowers specs/plans in Chinese.
