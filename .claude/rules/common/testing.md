# Testing Requirements

## Coverage Target: 80% (where practically testable)

Aim for 80%+ on pure-logic modules. This is an Android utility library — large
parts (native/JNI codecs, camera, NFC, WebRTC, screen capture, OpenGL) depend on
hardware or the Android framework and are not meaningfully unit-testable; do not
force coverage on them.

Test Types:
1. **Unit Tests** (required for pure-logic modules) — functions, utilities, codecs' Kotlin layer
2. **Robolectric Tests** — code needing a stubbed Android framework
3. **Instrumented / E2E Tests** (only where applicable) — device-dependent flows; N/A for most library modules

## Test-Driven Development

MANDATORY workflow:
1. Write test first (RED)
2. Run test - it should FAIL
3. Write minimal implementation (GREEN)
4. Run test - it should PASS
5. Refactor (IMPROVE)
6. Verify coverage (80%+)

## Troubleshooting Test Failures

1. Use **tdd-guide** agent
2. Check test isolation
3. Verify mocks are correct
4. Fix implementation, not tests (unless tests are wrong)

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features, enforces write-tests-first

## Test Stack (this project)

- **JUnit 5 (Jupiter)** — primary test framework
- **Mockk** — mocking
- **Kluent** — fluent assertions
- **Robolectric** — Android unit tests without a device

See [kotlin/testing.md](../kotlin/testing.md) for Kotlin/coroutine specifics.

## Test Structure (AAA Pattern)

Prefer Arrange-Act-Assert structure for tests:

```kotlin
@Test
fun `cosine similarity of orthogonal vectors is zero`() {
    // Arrange
    val v1 = floatArrayOf(1f, 0f, 0f)
    val v2 = floatArrayOf(0f, 1f, 0f)

    // Act
    val similarity = cosineSimilarity(v1, v2)

    // Assert
    similarity shouldBeEqualTo 0f
}
```

### Test Naming

Use backtick-quoted descriptive names that explain the behavior under test:

```kotlin
@Test fun `returns empty list when no items match query`() {}
@Test fun `throws when required key is missing`() {}
```
