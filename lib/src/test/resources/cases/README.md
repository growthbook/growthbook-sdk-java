# Shared conformance cases

- `cases.json` is an unedited copy of the JS SDK's
  [`packages/sdk-js/test/cases.json`](https://github.com/growthbook/growthbook/blob/main/packages/sdk-js/test/cases.json).
  Never edit it by hand.
- `source.json` records the upstream commit, `specVersion` and SHA-256 of `cases.json`.
- `java.json` holds Java-only cases, with the same suite layout as `cases.json`. A Java case
  may not reuse an upstream case name, so it cannot override an upstream expectation. If
  upstream adds an equivalent case, delete the Java copy.
- `exclusions.json` lists suites and cases the Java SDK does not support yet, each with a
  reason. Excluded cases stay in `cases.json`. Nested suites use dot-separated paths, such
  as `savedGroupReferencesV2.feature`. An exclusion that matches nothing fails the tests.

`TestCasesJsonHelper` merges the three files when tests run. Nothing is generated or
downloaded.

The `specVersion` of `cases.json` does not declare SDK capabilities. Those are declared in
`packages/shared/src/sdk-versioning/sdk-versions/java.json` in the main GrowthBook repo.

## Updating

1. Copy the upstream file from a specific commit, keeping its bytes unchanged:

   ```sh
   curl -fsSL https://raw.githubusercontent.com/growthbook/growthbook/<commit>/packages/sdk-js/test/cases.json \
     -o lib/src/test/resources/cases/cases.json
   ```

2. Update `source.json` with the commit, the new `specVersion` and the output of
   `shasum -a 256 lib/src/test/resources/cases/cases.json`.
3. Run `./gradlew :lib:test`. For each new failure, fix the SDK or add an exclusion with
   a reason.
4. Run `python3 scripts/check_corpus_freshness.py --pinned` to confirm the copy.

The Corpus Freshness workflow runs that check on PRs. Each week it also compares
`cases.json` with upstream `main` and fails when upstream has new or changed cases.
