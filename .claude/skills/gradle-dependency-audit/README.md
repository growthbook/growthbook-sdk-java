# Gradle Dependency Audit

**Load**: `view .claude/skills/gradle-dependency-audit/SKILL.md`

---

## Description

Helps Claude audit the Gradle dependencies of this multi-module build for outdated versions,
CVEs, and version conflicts — while respecting the repo policy (lean `lib`, pinned versions,
Java 8 compatibility, `compileOnly` cache providers).

---

## Use Cases

- "Check dependencies before the release"
- "Is our OkHttp affected by this CVE?"
- "Why do we resolve two versions of Gson?"

---

## Examples

```
> "Audit dependencies"
→ Inventories build.gradle files, runs dependencyInsight where needed,
  reports security/major/minor updates with a Java 8 compatibility column
```

---

## Notes / Tips

- The repo intentionally has no versions plugin; adding `com.github.ben-manes.versions`
  or OWASP dependency-check is a separate decision, not part of a routine audit.
- Any proposed major upgrade must be checked against the Java 8 bytecode requirement first.
