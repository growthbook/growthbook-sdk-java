## What

<!-- What does this PR change? -->

## Why

<!-- Why is the change needed? Link issues if any. -->

## Checklist

- [ ] PR title and commits follow Conventional Commits (`feat:`/`fix:` drive the release version)
- [ ] Tests cover both valid **and** invalid cases (see the test style section in `AGENTS.md`)
- [ ] No Java 9+ APIs (compiled with `--release 8` on JDK 9+; the Java 8 matrix job is the backstop)
- [ ] Release-please-owned files untouched (`CHANGELOG.md`, `versions.txt`, `Version.java`, `gradle.properties` version)
- [ ] Public API of `lib` unchanged — or the change is deliberate and marked `feat!:` / `BREAKING CHANGE:`
- [ ] New dependencies: none in `lib` (or explicitly approved); cache providers stay `compileOnly`
