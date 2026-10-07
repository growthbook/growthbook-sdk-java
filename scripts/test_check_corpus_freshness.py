"""Tests for check_corpus_freshness.py. Run: python3 -m unittest discover -s scripts"""

import contextlib
import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import check_corpus_freshness as checker


def quietly(function, *args):
    with contextlib.redirect_stdout(io.StringIO()):
        return function(*args)


class DiffTests(unittest.TestCase):
    def test_new_cases_and_nested_suites_are_reported_missing(self):
        upstream = {"feature": [["old", 1], ["new", 2]], "newCapability": {"feature": [["nested", True]]}}
        missing, _, _ = checker.diff(upstream, {"feature": [["old", 1]]})
        self.assertEqual(missing["feature"], ["new"])
        self.assertEqual(missing["newCapability.feature"], ["nested"])

    def test_changed_bodies_and_duplicate_counts_are_reported(self):
        upstream = {"feature": [["same", True], ["same", True]]}
        for local in [{"feature": [["same", True]]}, {"feature": [["same", True], ["same", False]]}]:
            _, changed, _ = checker.diff(upstream, local)
            self.assertEqual(changed["feature"], ["same"])

    def test_repeated_names_with_different_bodies_match_in_any_order(self):
        # hash cases reuse the seed as their first element
        upstream = {"hash": [["", "a", 1, 0.22], ["", "b", 1, 0.077]]}
        missing, changed, extra = checker.diff(upstream, {"hash": list(reversed(upstream["hash"]))})
        self.assertEqual((missing["hash"], changed["hash"], extra["hash"]), ([], [], []))

    def test_numeric_case_names_are_compared(self):
        _, changed, _ = checker.diff({"getEqualWeights": [[2, [0.5, 0.5]]]}, {"getEqualWeights": [[2, [1, 0]]]})
        self.assertEqual(changed["getEqualWeights"], ["2"])

    def test_cases_removed_upstream_are_extra(self):
        _, _, extra = checker.diff({"feature": []}, {"feature": [["gone", 1]]})
        self.assertEqual(extra["feature"], ["gone"])


class ModeTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.dir = Path(directory.name)
        self.raw = b'{"specVersion":"0.9.0","feature":[["a",1]]}'
        self.local = json.loads(self.raw)
        self.source = {"repository": "r", "commit": "c" * 40, "path": "p", "specVersion": "0.9.0",
                       "sha256": hashlib.sha256(self.raw).hexdigest()}

    def upstream(self, content):
        path = self.dir / "upstream.json"
        path.write_bytes(content if isinstance(content, bytes) else json.dumps(content).encode())
        return str(path)

    def test_edited_snapshot_is_rejected(self):
        cases = self.dir / "cases.json"
        cases.write_bytes(self.raw)
        with patch.object(checker, "LOCAL_CASES", cases):
            self.assertEqual(checker.load_local_cases(self.source)[1], self.local)
            cases.write_bytes(self.raw + b"\n")
            with self.assertRaisesRegex(ValueError, "sha256"):
                checker.load_local_cases(self.source)

    def test_pinned_mode_requires_identical_bytes(self):
        self.assertEqual(quietly(checker.check_pinned, self.source, self.raw, self.upstream(self.raw)), 0)
        reformatted = json.dumps(self.local, indent=2).encode()
        self.assertEqual(quietly(checker.check_pinned, self.source, self.raw, self.upstream(reformatted)), 1)

    def test_latest_mode_fails_on_missing_or_changed_cases(self):
        self.assertEqual(quietly(checker.check_latest, self.source, self.local, self.upstream(self.local)), 0)
        added = {"specVersion": "0.9.1", "feature": [["a", 1], ["b", 2]]}
        self.assertEqual(quietly(checker.check_latest, self.source, self.local, self.upstream(added)), 1)
        changed = {"specVersion": "0.9.0", "feature": [["a", 2]]}
        self.assertEqual(quietly(checker.check_latest, self.source, self.local, self.upstream(changed)), 1)


if __name__ == "__main__":
    unittest.main()
