---
name: Always take the best approach, ignore released versions
description: Do not keep compatibility paths for published versions; note migration in KDoc instead
type: feedback
---

When changing code, do NOT preserve compatibility paths for already-published
versions. Pick the best approach and replace the old one. If the change affects
users of an older release (signature change, behavior change, removed/replaced
function), append the explanation to that function's **KDoc** - that is enough.
`@Deprecated` (with `ReplaceWith`) is a transition marker only, not a long-term
compatibility layer; delete it once migration is done.

**Why:** The user stated it directly ("修改代码时，无需考虑已发布版本，按最佳方案进行修改。
若会影响旧版本用户的使用，则在函数的中的 KDoc 中追加说明即可。我希望用户升级到最新版时，
也要同时使用最新方案，或替换成最新方案。"). The intent is that consumers who upgrade
move to the new approach rather than keeping old call sites alive.

**How to apply:** Default to the clean design instead of adding overloads, extra
params, compat flags, or dual code paths. Document the migration in KDoc. Two things
are still NOT waived: the `minSdk` 21 constraint, and recording breaking changes in
`CHANGELOG.md`. Recorded in CLAUDE.md under "API 演进约定".
Related: [[feedback_git_and_code_language]], [[feedback_no_local_compile]].
