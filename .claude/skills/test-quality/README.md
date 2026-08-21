# Test Quality (JUnit 5, repo style)

**Load**: `view .claude/skills/test-quality/SKILL.md`

---

## Description

Helps Claude write and review tests in this repository's established style: JUnit 5 with built-in
assertions (no AssertJ), `@DisplayName("Verify: ...")`, `// Given / When / Then` structure, and
mandatory coverage of both valid and invalid cases. The reference implementations are the
`growthbook-cache-*` test classes.

---

## Use Cases

- "Add tests for GBFeaturesRepository refresh"
- "Review existing tests in CliEndToEndTest"
- "Improve coverage for the render package"

---

## Examples

```
> "Add unit tests for ConstantNameMapper with edge cases"
→ JUnit 5 tests with Verify:-style DisplayNames covering happy path,
  every rejection (assertThrows), empty/boundary inputs
```

---

## Notes / Tips

- The style rules live in `.claude/rules/development-guidelines.md` — this skill applies them.
- HTTP behavior is tested with OkHttp MockWebServer; generated output against golden files
  in `src/test/resources/expected/`.
