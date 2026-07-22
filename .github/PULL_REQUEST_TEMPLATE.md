## What

<!-- What does this PR change? -->

## Why

<!-- Why is the change needed? Link issues if any. -->

## Checklist

- [ ] PR title and commits follow Conventional Commits (`feat:`/`fix:` drive the release version)
- [ ] Tests cover both valid **and** invalid cases (`Verify:` DisplayNames, Given/When/Then)
- [ ] No Java 9+ APIs in published modules (they compile with `--release 8`)
- [ ] Release-please-owned files untouched (`CHANGELOG.md`, `versions.txt`, `Version.java`, `gradle.properties` version)
- [ ] Public API of `lib` unchanged — or the change is deliberate and marked `feat!:` / `BREAKING CHANGE:`
- [ ] New dependencies: none in `lib` (or explicitly approved); cache providers stay `compileOnly`
