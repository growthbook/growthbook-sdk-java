#!/usr/bin/env python3
"""Check the vendored cases.json against the JS SDK's cases.json.

Two modes:

  --pinned  Confirm cases.json is an unedited copy of the upstream commit recorded
            in source.json. Deterministic, so it is safe to run on every PR.
  (default) Compare cases.json against upstream main, case by case, to find
            upstream cases that are new or changed since the pinned commit.

java.json and exclusions.json are not read. A Java-only case or an exclusion never
hides a missing or changed upstream case.

Exit codes: 0 in sync, 1 out of sync, 2 local error or failed fetch.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.request
from collections import Counter
from pathlib import Path

CASES_DIR = Path(__file__).resolve().parents[1] / "lib" / "src" / "test" / "resources" / "cases"
LOCAL_CASES = CASES_DIR / "cases.json"
SOURCE = CASES_DIR / "source.json"
RAW_URL = "https://raw.githubusercontent.com/{repository}/{ref}/{path}"


def load_source() -> dict:
    return json.loads(SOURCE.read_text())


def load_local_cases(source: dict) -> tuple[bytes, dict]:
    raw = LOCAL_CASES.read_bytes()
    if hashlib.sha256(raw).hexdigest() != source["sha256"]:
        raise ValueError("cases.json does not match the sha256 in source.json. Replace the whole file and update source.json together.")
    cases = json.loads(raw)
    if cases.get("specVersion") != source["specVersion"]:
        raise ValueError("specVersion in cases.json does not match source.json")
    return raw, cases


def fetch(url: str) -> bytes:
    if not url.startswith(("http://", "https://")):
        return Path(url).read_bytes()
    request = urllib.request.Request(url, headers={"User-Agent": "growthbook-sdk-java-corpus-check"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return response.read()


def suites(corpus: dict, prefix: str = "") -> dict[str, list]:
    """Flatten nested suites, such as savedGroupReferencesV2.feature, into dot-separated paths."""
    found = {}
    for key, value in corpus.items():
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, list):
            found[path] = value
        elif isinstance(value, dict):
            found.update(suites(value, path))
    return found


def signatures(cases: list) -> dict[str, Counter]:
    grouped: dict[str, Counter] = {}
    for case in cases:
        if not isinstance(case, list) or not case:
            raise ValueError("Every case must be a non-empty array")
        # Most suites name cases with a string; getEqualWeights uses a number.
        name = case[0] if isinstance(case[0], str) else json.dumps(case[0], sort_keys=True)
        body = json.dumps(case[1:], sort_keys=True, separators=(",", ":"))
        grouped.setdefault(name, Counter())[body] += 1
    return grouped


def diff(upstream: dict, local: dict) -> tuple[dict, dict, dict]:
    missing, changed, extra = {}, {}, {}
    upstream_suites, local_suites = suites(upstream), suites(local)
    for suite in sorted(upstream_suites.keys() | local_suites.keys()):
        expected = signatures(upstream_suites.get(suite, []))
        actual = signatures(local_suites.get(suite, []))
        missing[suite] = [name for name in expected if name not in actual]
        changed[suite] = [name for name in expected if name in actual and expected[name] != actual[name]]
        extra[suite] = [name for name in actual if name not in expected]
    return missing, changed, extra


def check_pinned(source: dict, raw: bytes, url: str | None) -> int:
    url = url or RAW_URL.format(repository=source["repository"], ref=source["commit"], path=source["path"])
    upstream = fetch(url)
    if upstream != raw:
        print(f"cases.json is not an exact copy of {url}")
        return 1
    print(f"OK: cases.json matches {source['repository']}@{source['commit'][:9]} (specVersion {source['specVersion']})")
    return 0


def check_latest(source: dict, local: dict, url: str | None) -> int:
    url = url or RAW_URL.format(repository=source["repository"], ref="main", path=source["path"])
    upstream = json.loads(fetch(url))
    missing, changed, extra = diff(upstream, local)
    counts = [sum(map(len, findings.values())) for findings in (missing, changed, extra)]
    failed = counts[0] > 0 or counts[1] > 0
    print(f"Upstream specVersion {upstream.get('specVersion')}, vendored {local.get('specVersion')}")
    print(f"{'OUT OF SYNC' if failed else 'OK'}: {counts[0]} missing, {counts[1]} changed, {counts[2]} removed upstream")
    for label, findings in (("Missing", missing), ("Changed", changed), ("Removed upstream", extra)):
        for suite, names in findings.items():
            if names:
                print(f"\n{label}: {suite}")
                for name in names:
                    print(f"  - {name}")
    if failed:
        print("\nTo update: copy the upstream file unedited, update source.json, then run the tests and adjust exclusions.json.")
    return int(failed)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--pinned", action="store_true", help="Check against the commit in source.json instead of main")
    parser.add_argument("--upstream", help="Override the upstream URL or local path")
    args = parser.parse_args(argv)
    try:
        source = load_source()
        raw, local = load_local_cases(source)
    except (OSError, ValueError, KeyError) as error:
        print(f"corpus check error: {error}", file=sys.stderr)
        return 2
    try:
        if args.pinned:
            return check_pinned(source, raw, args.upstream)
        return check_latest(source, local, args.upstream)
    except (OSError, ValueError) as error:
        print(f"corpus check error: could not read upstream cases: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
