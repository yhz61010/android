# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens, keystore passwords)
- [ ] All external input validated (network payloads, IPC/Intent extras, file content)
- [ ] Parameterized queries for any Room/SQLite access (never string-concatenate input)
- [ ] No cleartext network traffic; certificate pinning for sensitive endpoints
- [ ] Sensitive data stored via `EncryptedSharedPreferences`, not plain prefs
- [ ] File paths from external input sanitized (prevent path traversal)
- [ ] Error/log messages don't leak secrets or PII

> This is an Android library (no server endpoints). Web-server concerns such as
> CSRF, server-side XSS, and endpoint rate limiting generally do not apply.

## Secret Management

- NEVER hardcode secrets in source code
- ALWAYS use environment variables or a secret manager
- Validate that required secrets are present at startup
- Rotate any secrets that may have been exposed

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Use **security-reviewer** agent
3. Fix CRITICAL issues before continuing
4. Rotate any exposed secrets
5. Review entire codebase for similar issues
