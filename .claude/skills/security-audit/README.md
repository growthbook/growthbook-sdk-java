# Security Audit

**Load**: `view .claude/skills/security-audit/SKILL.md`

---

## Description

Java security checklist for a published client SDK library, based on OWASP guidance. Covers what applies to an HTTP client library: secrets handling, input validation at the public API boundary, safe deserialization (Gson), TLS/redirect concerns (OkHttp), and dependency scanning. Server-side controls (XSS, CSRF, security headers, authorization) are out of scope for this repo.

---

## Use Cases

- "Review this code for security issues"
- "Are we handling the API key safely?"
- "Is this deserialization safe?"
- "Security audit before release"
- "OWASP compliance check"

---

## Topics Covered

| Topic | Applies To |
|-------|------------|
| **Input Validation** | Public API boundary (builders, constructors), allowlist patterns |
| **Secrets Management** | Env vars / consumer config, no secrets in logs or fixtures |
| **Secure Deserialization** | Gson into concrete types, untrusted payload handling |
| **HTTP Client Security** | OkHttp TLS verification, redirects, timeouts, SSE reconnects |
| **Dependency Security** | Gradle, OWASP Dependency Check, pinned versions |

---

## OWASP Top 10 Coverage (client-SDK view)

| Risk | Covered |
|------|---------|
| A02 Cryptographic Failures | ✅ |
| A03 Injection | ✅ (one-line overview; no databases in this repo) |
| A04 Insecure Design | ✅ |
| A05 Security Misconfiguration | ✅ |
| A06 Vulnerable Components | ✅ |
| A08 Data Integrity Failures | ✅ |
| A09 Logging Failures | ✅ |
| A10 SSRF | ✅ |

---

## Related Skills

- `java-code-review` - General review
- `gradle-dependency-audit` - Dependency scanning
- `logging-patterns` - Secure logging

---

## Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP Java Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Java_Security_Cheat_Sheet.html)
- [OWASP Dependency Check](https://owasp.org/www-project-dependency-check/)
