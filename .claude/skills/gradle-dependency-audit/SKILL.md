---
name: gradle-dependency-audit
description: Audit Gradle dependencies for outdated versions, security vulnerabilities, and conflicts. Use when user says "check dependencies", "audit dependencies", "outdated deps", or before releases.
---

# Gradle Dependency Audit Skill

Audit Gradle dependencies for updates, vulnerabilities, and conflicts in this multi-module build
(`lib`, `growthbook-cache-*`, `growthbook-codegen`).

## When to Use
- User says "check dependencies" / "audit dependencies" / "outdated dependencies"
- Before a release
- After a security advisory

## Repo Policy (see `.claude/rules/development-guidelines.md`)

- `lib` is a published SDK: **no new third-party dependencies without explicit approval**.
- Versions are pinned exactly — no dynamic (`+`) or range versions.
- Cache provider clients (Jedis, Lettuce) stay `compileOnly` in adapter modules.
- Version bumps land as dedicated `chore`/`build` commits, never mixed into feature work.

## Audit Workflow

### 1. Inventory current dependencies

```bash
# Declared versions per module
grep -n "implementation\|api \|compileOnly\|testImplementation" lib/build.gradle growthbook-*/build.gradle

# Full resolved tree for one module
./gradlew :lib:dependencies --configuration runtimeClasspath

# Why is X on the classpath / which version won?
./gradlew :lib:dependencyInsight --dependency okhttp --configuration runtimeClasspath
```

### 2. Check for updates

No versions plugin is configured in this repo, so check current releases via
[Maven Central](https://central.sonatype.com/) / library changelogs for the handful of pinned deps
(Gson, OkHttp, Guava, commons-math3, Lombok, SLF4J, JUnit, Mockito, Caffeine, cache APIs).
If recurring audits are wanted, suggest adding the `com.github.ben-manes.versions` plugin
(`./gradlew dependencyUpdates`) as a separate proposal.

### 3. Categorize updates

| Category | Criteria | Action |
|----------|----------|--------|
| **Security** | CVE fixed in newer version | Update ASAP, dedicated commit |
| **Major** | x.0.0 change | Review changelog; check Java 8 compatibility first |
| **Minor/Patch** | x.y / x.y.z change | Usually safe; run full `./gradlew build` |

**Java 8 gate:** every dependency of a published module must still target Java 8 bytecode.
Newer majors often move to Java 11+ (e.g. OkHttp 5.x-alphas, Guava `-jre` vs `-android`) — verify
before proposing an upgrade.

### 4. Conflict analysis

`dependencyInsight` shows version conflicts and their resolution. Flags to report:
- Same library resolved to different versions across modules
- A transitive dependency overriding a pinned version
- Anything leaking into the `api` configuration of adapters beyond `:lib` and the provider API

### 5. Security scan

- GitHub Dependabot alerts (repository settings) — primary channel.
- OWASP `dependency-check-gradle` plugin can be added for on-demand CVE scans; propose separately,
  do not add ad hoc.

## Report Format

```markdown
## Dependency Audit — {date}

### Security
| Module | Dependency | Current | CVE | Fixed in |

### Outdated (safe: minor/patch)
| Module | Dependency | Current | Latest | Java 8 OK? |

### Outdated (major — needs review)
...

### Conflicts / hygiene
...

### Recommendations (prioritized)
1. ...
```

## Quick Commands Reference

| Task | Command |
|------|---------|
| Resolved tree | `./gradlew :lib:dependencies --configuration runtimeClasspath` |
| Why this version | `./gradlew :lib:dependencyInsight --dependency <name>` |
| Full verification | `./gradlew build` |
| Per-module trees | repeat with `:growthbook-cache-redis:`, `:growthbook-codegen:`, ... |
