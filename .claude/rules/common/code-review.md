# Code Review Standards

## Purpose

Code review ensures quality, security, and maintainability before code is merged. This rule defines when and how to conduct code reviews.

## When to Review

**MANDATORY review triggers:**

- After writing or modifying code
- Before any commit to shared branches
- When security-sensitive code is changed (auth, secrets/keystore, user data, native/JNI)
- When architectural changes are made
- Before merging pull requests

**Pre-Review Requirements:**

Before requesting review, ensure:

- All automated checks (CI/CD) are passing
- Merge conflicts are resolved
- Branch is up to date with target branch

## Review Checklist

Before marking code complete:

- [ ] Code is readable and well-named
- [ ] Functions are focused (<50 lines)
- [ ] Files are cohesive (<800 lines)
- [ ] No deep nesting (>4 levels)
- [ ] Errors are handled explicitly
- [ ] No hardcoded secrets or credentials
- [ ] No leftover debug logging (use the project `log` module, gated by level)
- [ ] Tests exist for new functionality
- [ ] Test coverage meets target (see testing.md) where practically testable

## Security Review Triggers

**STOP and use security-reviewer agent when:**

- Authentication or authorization code
- External input handling (network payloads, IPC/Intent extras)
- Room/SQLite queries
- File system operations
- Network calls / WebView usage
- Cryptographic operations or secret/keystore handling
- Native/JNI code crossing the Kotlin boundary

## Review Severity Levels

| Level | Meaning | Action |
|-------|---------|--------|
| CRITICAL | Security vulnerability or data loss risk | **BLOCK** - Must fix before merge |
| HIGH | Bug or significant quality issue | **WARN** - Should fix before merge |
| MEDIUM | Maintainability concern | **INFO** - Consider fixing |
| LOW | Style or minor suggestion | **NOTE** - Optional |

## Agent Usage

Use these agents for code review:

| Agent | Purpose |
|-------|---------|
| **code-reviewer** | General code quality, patterns, best practices |
| **security-reviewer** | Security vulnerabilities, OWASP Top 10 |
| **kotlin-reviewer** | Kotlin / Android-specific issues (primary for this project) |
| **cpp-reviewer** | C/C++ / JNI native code issues |

## Review Workflow

```
1. Run git diff to understand changes
2. Check security checklist first
3. Review code quality checklist
4. Run relevant tests
5. Verify coverage >= 80%
6. Use appropriate agent for detailed review
```

## Common Issues to Catch

### Security

- Hardcoded credentials (API keys, passwords, tokens, keystore passwords)
- SQL injection in Room/SQLite (string concatenation in queries)
- Path traversal (unsanitized file paths)
- Cleartext network traffic / missing certificate pinning
- Authentication bypasses
- Native/JNI memory safety (unchecked buffer sizes, leaked `nativeHandle`)

### Code Quality

- Large functions (>50 lines) - split into smaller
- Large files (>800 lines) - extract modules
- Deep nesting (>4 levels) - use early returns
- Missing error handling - handle explicitly
- Mutation patterns - prefer immutable operations (except justified perf/native paths)
- Missing tests - add test coverage

### Performance

- Allocations in hot loops (media/codec paths) - reuse buffers
- Main-thread blocking - move I/O and heavy work off the UI thread
- Unbounded collections/caches - add constraints
- Missing caching - cache expensive operations

## Approval Criteria

- **Approve**: No CRITICAL or HIGH issues
- **Warning**: Only HIGH issues (merge with caution)
- **Block**: CRITICAL issues found

## Integration with Other Rules

This rule works with:

- [testing.md](testing.md) - Test coverage requirements
- [security.md](security.md) - Security checklist
- [git-workflow.md](git-workflow.md) - Commit standards
- [agents.md](agents.md) - Agent delegation
