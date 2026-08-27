---
name: Do not compile/build locally
description: Skip gradle compile/build/test runs; the user verifies builds locally
type: feedback
---

Do NOT run gradle compile, build, static-check, or test tasks in this environment
(`./gradlew ...` compile/assemble/detekt/ktlint/test). The user runs all build and
test verification locally.

**Why:** Builds are slow in this environment (e.g. a single `:lib-bytes:testDebugUnitTest`
took ~2m14s) and the user prefers to compile/verify on their own machine. The user
stated this repeatedly ("你不需要编译代码。我会在本地进行。" / "不需要编译。").

**How to apply:** When implementing changes, make the edits and hand off. In plans,
mark compile/detekt/ktlint/test steps as "user runs locally". It is fine to reason
about correctness and imports statically, but do not invoke gradle to verify. If a
RED test run is essential to a TDD step, ask first rather than compiling by default.
Related: [[project_build_env]].
