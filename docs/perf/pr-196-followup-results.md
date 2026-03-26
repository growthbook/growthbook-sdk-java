# PR-196 Follow-Up Performance Notes

Commands used:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :lib:runPerfHarness -PperfWarmupIterations=20000 -PperfIterations=100000
```

JFR capture command:

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :lib:runPerfHarness \
  -PperfWarmupIterations=10000 \
  -PperfIterations=50000 \
  -PperfJfrFile=/tmp/growthbook-followup.jfr
```

Measured `ns/op`:

| Scenario | main | pr-196 | fix/perf-imp-pr-196 |
| --- | ---: | ---: | ---: |
| feature-no-overrides | 586.8 | 201.4 | 201.5 |
| feature-global-override | 1171.7 | 318.6 | 152.2 |
| feature-user-override | 874.0 | 463.6 | 234.5 |
| experiment-no-forced-variations | 1482.1 | 1123.6 | 1065.6 |
| experiment-global-forced-variation | 300.6 | 384.9 | 197.2 |
| experiment-user-forced-variation | 353.7 | 481.3 | 203.4 |

Artifacts:

- `/tmp/growthbook-main.jfr`
- `/tmp/growthbook-pr-196.jfr`
- `/tmp/growthbook-followup.jfr`
- `docs/perf/pr-196-feature-overrides.svg`
- `docs/perf/pr-196-experiment-forced-variations.svg`
