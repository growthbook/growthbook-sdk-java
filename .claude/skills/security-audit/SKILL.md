---
name: security-audit
description: Java security checklist for a published client SDK library - secrets handling, input validation, safe deserialization (Gson), TLS for HTTP clients (OkHttp), and dependency scanning. Use when reviewing code security, before releases, or when user asks about vulnerabilities.
---

# Security Audit Skill

Security checklist for a published Java client library, based on OWASP guidance and secure coding practices. This repo is an SDK that makes outbound HTTP calls (OkHttp) and parses JSON (Gson) — it serves no web endpoints, so server-side controls (XSS, CSRF, security headers, authorization filters) are out of scope.

## When to Use
- Security code review
- Before releases
- User asks about "security", "vulnerability", "OWASP"
- Reviewing HTTP client, deserialization, or secrets-handling code

---

## OWASP Top 10 Quick Reference (client-SDK view)

| # | Risk | SDK Mitigation |
|---|------|----------------|
| A02 | Cryptographic Failures | Strong algorithms for payload decryption, no hardcoded secrets |
| A03 | Injection | Input validation; never build executable strings from untrusted input |
| A04 | Insecure Design | Secure defaults: TLS on, data minimisation, quiet logging |
| A05 | Security Misconfiguration | Never disable certificate/hostname verification |
| A06 | Vulnerable Components | Dependency scanning, pinned versions |
| A08 | Data Integrity Failures | Safe deserialization (Gson into known types) |
| A09 | Logging Failures | No secrets/PII in logs or exception messages |
| A10 | SSRF | Validate configured endpoint URLs, treat redirects with care |

---

## Input Validation

### Validate at the Public API Boundary

A library's boundary is its public constructors, builders, and setters — validate there with plain Java guard clauses (no Bean Validation dependency in `lib`).

```java
// ✅ GOOD: Validate at boundary (builder/constructor), fail fast with a clear message
public Options build() {
    if (apiHost == null || apiHost.trim().isEmpty()) {
        throw new IllegalArgumentException("apiHost is required");
    }
    if (refreshIntervalSeconds <= 0) {
        throw new IllegalArgumentException("refreshIntervalSeconds must be positive");
    }
    return new Options(this);
}

// ❌ BAD: Accept anything, fail later with NPE deep inside evaluation
```

### Allowlist vs Blocklist

```java
// ❌ BAD: Blocklist (attackers find bypasses)
if (input.contains("..")) {
    throw new IllegalArgumentException("Invalid input");
}

// ✅ GOOD: Allowlist (only permit known-good)
private static final Pattern SAFE_KEY = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

if (!SAFE_KEY.matcher(input).matches()) {
    throw new IllegalArgumentException("Invalid key format");
}
```

Treat everything crossing a trust boundary as untrusted: consumer-supplied configuration, user attributes, and JSON payloads fetched from the network.

---

## Secrets Management

### Never Hardcode Secrets

```java
// ❌ BAD: Hardcoded secrets
private static final String API_KEY = "sk-xxxx-placeholder";

// ✅ GOOD: Environment variables (repo convention: GROWTHBOOK_API_KEY)
String apiKey = System.getenv("GROWTHBOOK_API_KEY");

// ✅ GOOD: Accept the secret from the consumer via configuration,
// never bundle a default one in the SDK
Options options = Options.builder()
    .clientKey(clientKeyFromCallersConfig)
    .build();
```

Additional SDK rules:

- Never put API keys/tokens in exception messages, log statements, or generated code.
- Test fixtures and examples use synthetic placeholder keys only.

### .gitignore

```gitignore
# Never commit these
.env
*.pem
*.key
*credentials*
*secret*
```

---

## Secure Deserialization

### Avoid Java Serialization

```java
// ❌ DANGEROUS: Java ObjectInputStream
ObjectInputStream ois = new ObjectInputStream(untrustedInput);
Object obj = ois.readObject();  // Remote Code Execution risk!

// ✅ GOOD: JSON with Gson, deserialized into a concrete known type
Gson gson = GrowthBookJsonUtils.getInstance().gson;
FeaturesResponse response = gson.fromJson(json, FeaturesResponse.class);
```

### Gson Security

- Deserialize into concrete model classes — never let the payload decide which class gets instantiated (no type adapters that read a class name from untrusted JSON).
- Treat fetched feature payloads as untrusted input: handle `JsonSyntaxException`/`JsonParseException` at the fetch boundary instead of letting malformed JSON propagate.
- Remember Gson's default leniency — validate required fields after parsing rather than assuming they are non-null.
- All Gson configuration goes through `GrowthBookJsonUtils` (repo rule) — no ad-hoc `Gson` instances with divergent settings.

---

## Dependency Security

### OWASP Dependency Check

**Gradle:**
```groovy
plugins {
    id 'org.owasp.dependencycheck'
}

dependencyCheck {
    failBuildOnCVSS = 7  // Fail on high severity
}
```

**Run:**
```bash
./gradlew dependencyCheckAnalyze
# Report: build/reports/dependency-check-report.html
```

### Keep Dependencies Pinned and Updated

- Pin exact versions — no dynamic `+`/ranges (repo rule).
- `lib` is a published SDK: every dependency lands on consumers' classpaths, so no new third-party dependency without explicit approval.
- Version bumps go in dedicated `chore`/`build` commits.
- See the `gradle-dependency-audit` skill for the full audit workflow.

---

## HTTP Client Security (OkHttp)

- Always use `https://` endpoints. Never disable TLS verification — no trust-all `X509TrustManager`, no `hostnameVerifier` that returns `true`, even "temporarily for testing".
- Be deliberate about redirects: OkHttp follows redirects by default, including HTTPS-to-HTTP unless `followSslRedirects(false)` is set. Review any custom interceptor that (re-)attaches credentials — a redirect must not leak the API key to an unexpected host.
- Set explicit connect/read timeouts; an SDK must not hang the host application on a stalled connection.
- SSE reconnect logic (okhttp-sse) must reconnect only to the originally configured host with the originally configured credentials.
- Validate consumer-supplied endpoint URLs at configuration time (scheme, well-formedness) — this is the SDK-side SSRF guard.

---

## Logging Security Events

```java
// ✅ Log security-relevant events (without secrets)
log.warn("Features fetch rejected with status {}, check client key configuration", statusCode);
log.warn("Feature payload decryption failed", e);

// ❌ NEVER log sensitive data
log.info("Fetching features. apiKey={}", apiKey);        // NEVER!
log.debug("Decrypted payload: {}", decryptedFeatures);   // NEVER!
log.debug("User attributes: {}", userAttributes);        // Potentially PII
```

---

## Security Checklist

### Code Review

- [ ] Input validated at public API boundary with allowlist patterns
- [ ] No hardcoded secrets; keys come from consumer config or environment variables
- [ ] No API keys/tokens/PII in logs, exception messages, or generated code
- [ ] Deserialization uses Gson into concrete known types (no Java serialization of untrusted input)
- [ ] Malformed JSON handled at the fetch boundary

### HTTP Client

- [ ] HTTPS endpoints only; TLS certificate/hostname verification never disabled
- [ ] Redirect handling reviewed (no credential leak to other hosts)
- [ ] Connect/read timeouts set
- [ ] Endpoint URLs validated at configuration time

### Dependencies

- [ ] No known vulnerabilities (OWASP check)
- [ ] Versions pinned exactly (no dynamic ranges)
- [ ] Unnecessary dependencies removed; no new `lib` dependency without approval

---

## Related Skills

- `java-code-review` - General code review
- `gradle-dependency-audit` - Dependency vulnerability scanning
- `logging-patterns` - Secure logging practices
