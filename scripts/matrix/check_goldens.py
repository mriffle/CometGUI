#!/usr/bin/env python3
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
"""R-TEST-07's review rule, enforced: no golden without a reviewed explanation.

``R-TEST-07``: "Updating a golden shall require a reviewed written explanation
of why the scientific change is expected, recorded alongside the golden."

The goldens live under ``cometgui-app/src/test/resources/goldens/``, one
directory per scientific tool pair (``comet-<v>_percolator-<v>``) or single
tool (``percolator-<v>``, ``percolator-<v>-local``), one file per fixture and
platform: ``fixture-<the fixture's SHA-256>.<platform>.json``. Each directory
holds ``REVIEW.json``, the review record. This check fails when:

* a golden file's CURRENT SHA-256 is not in a review record for that file --
  so a golden whose content changes without a new record is red, whoever
  changed it and however small the change;
* a review record has no reviewer, a date that is not an ISO date or lies in
  the future, or an explanation that is short or a placeholder
  (``TODO``, ``TBD``, ``FIXME``, ``XXX``, ``placeholder``, ``explain``,
  ``lorem``, ``n/a``, ``DESCRIBE ...``);
* a review record names a file that does not exist (stale);
* a golden is malformed: its ``key``, ``fixtureDigest`` or ``platform``
  disagree with its directory and file name, its ``expected`` is empty, or a
  tolerance has no written ``why`` (gate item 3's rule: a tolerance is
  justified in writing, never left implicit);
* a directory has no ``REVIEW.json``, or holds a file that is neither.

A changed golden keeps its old records as history; only the current digest has
to be covered. Standard library only. ``--self-test`` proves each red on a copy
of the tree in a temporary directory and a clean copy green.

Exit status: 0 clean; 1 a rule broken (each printed); 2 misuse; 7 self-test
failure.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
GOLDENS = ROOT / "cometgui-app" / "src" / "test" / "resources" / "goldens"
REVIEW = "REVIEW.json"
KEY_RE = re.compile(r"^(comet-\d+(\.\d+)*_percolator-\d+(\.\d+)*|percolator-\d+(\.\d+)*)(-local)?$")
GOLDEN_RE = re.compile(r"^fixture-([0-9a-f]{64})\.([a-z0-9]+-[a-z0-9-]+)\.json$")
PLACEHOLDER_RE = re.compile(r"\b(todo|tbd|fixme|xxx|placeholder|explain|lorem|n/a)\b|^describe\b", re.IGNORECASE)
MIN_EXPLANATION = 80
MIN_WHY = 40
ALLOWED_EXTRA = {".gitattributes"}


def sha256_of(path: Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def is_placeholder(text: str, minimum: int) -> bool:
    stripped = (text or "").strip()
    if len(stripped) < minimum:
        return True
    if PLACEHOLDER_RE.search(stripped):
        return True
    return minimum >= MIN_WHY and len(set(stripped.replace(" ", ""))) < 8


def check(root: Path = GOLDENS, today: datetime.date = None) -> list:
    """Every rule broken under ``root``, as sentences; [] when clean."""
    today = today or datetime.date.today()
    root = Path(root)
    problems = []
    if not root.is_dir():
        return [f"{root} does not exist: there are no goldens to check, which is not a pass"]
    directories = sorted(path for path in root.iterdir() if path.is_dir())
    goldens_seen = 0
    for path in sorted(root.iterdir()):
        if path.is_file() and path.name not in ALLOWED_EXTRA:
            problems.append(f"{path.name}: a file directly under goldens/; goldens live in a tool-pair directory")
    if not directories:
        problems.append(f"{root} holds no golden directory")
    for directory in directories:
        key = directory.name
        if not KEY_RE.match(key):
            problems.append(f"{key}/: not a tool-pair key (comet-<v>_percolator-<v>, percolator-<v>[-local])")
        review_file = directory / REVIEW
        records = []
        if not review_file.is_file():
            problems.append(f"{key}/: no {REVIEW} -- a golden with no review record (R-TEST-07)")
        else:
            try:
                document = json.loads(review_file.read_text(encoding="utf-8"))
                records = document["reviews"]
                if not isinstance(records, list):
                    raise TypeError("reviews is not a list")
            except (ValueError, KeyError, TypeError) as error:
                problems.append(f"{key}/{REVIEW}: unreadable: {error}")
                records = []
        for index, record in enumerate(records):
            where = f"{key}/{REVIEW} record {index + 1}"
            if not isinstance(record, dict):
                problems.append(f"{where}: not an object")
                continue
            for field in ("file", "sha256", "date", "reviewer", "explanation"):
                if not str(record.get(field, "")).strip():
                    problems.append(f"{where}: no '{field}'")
            try:
                date = datetime.date.fromisoformat(str(record.get("date", "")))
                if date > today:
                    problems.append(f"{where}: dated {date}, which is in the future")
            except ValueError:
                problems.append(f"{where}: date {record.get('date')!r} is not YYYY-MM-DD")
            if is_placeholder(str(record.get("explanation", "")), MIN_EXPLANATION):
                problems.append(f"{where}: the explanation is empty, short or a placeholder -- R-TEST-07 "
                                "requires a written explanation of why the scientific change is expected")
            if str(record.get("reviewer", "")).strip() and is_placeholder(str(record["reviewer"]), 3):
                problems.append(f"{where}: the reviewer is a placeholder")
            if record.get("file") and not (directory / str(record["file"])).is_file():
                problems.append(f"{where}: names {record['file']}, which does not exist (stale)")
        for path in sorted(directory.iterdir()):
            if path.name == REVIEW or path.name in ALLOWED_EXTRA:
                continue
            match = GOLDEN_RE.match(path.name)
            if not path.is_file() or not match:
                problems.append(f"{key}/{path.name}: neither a golden (fixture-<sha256>.<platform>.json) "
                                f"nor {REVIEW}")
                continue
            goldens_seen += 1
            digest = sha256_of(path)
            covered = [r for r in records if isinstance(r, dict) and r.get("file") == path.name
                       and r.get("sha256") == digest]
            if not covered:
                problems.append(f"{key}/{path.name}: its content (SHA-256 {digest}) has no review record -- a "
                                "golden changed or added without a reviewed explanation (R-TEST-07)")
            try:
                golden = json.loads(path.read_text(encoding="utf-8"))
            except ValueError as error:
                problems.append(f"{key}/{path.name}: not JSON: {error}")
                continue
            if golden.get("key") != key:
                problems.append(f"{key}/{path.name}: its key {golden.get('key')!r} is not its directory {key!r}")
            if golden.get("fixtureDigest") != match.group(1):
                problems.append(f"{key}/{path.name}: its fixtureDigest is not the one in its name")
            if golden.get("platform") != match.group(2):
                problems.append(f"{key}/{path.name}: its platform {golden.get('platform')!r} is not the one "
                                "in its name")
            if not isinstance(golden.get("expected"), dict) or not golden["expected"]:
                problems.append(f"{key}/{path.name}: expects nothing")
            if is_placeholder(str(golden.get("fixture", "")), 20):
                problems.append(f"{key}/{path.name}: says nothing about its fixture")
            if is_placeholder(str(golden.get("why", "")), MIN_WHY):
                problems.append(f"{key}/{path.name}: does not say why its comparison is "
                                f"{golden.get('comparison', '?')}")
            for name, tolerance in (golden.get("tolerances") or {}).items():
                if name not in (golden.get("expected") or {}):
                    problems.append(f"{key}/{path.name}: a tolerance for {name!r}, which it does not expect")
                if not isinstance(tolerance, dict) or is_placeholder(str(tolerance.get("why", "")), MIN_WHY):
                    problems.append(f"{key}/{path.name}: the tolerance for {name!r} is not justified in writing")
    if directories and goldens_seen == 0:
        problems.append(f"{root}: golden directories but no golden file")
    return problems


def count(root: Path = GOLDENS) -> int:
    return sum(1 for d in Path(root).iterdir() if d.is_dir() for f in d.iterdir() if GOLDEN_RE.match(f.name))


# ---------------------------------------------------------------------------
# Self-test
# ---------------------------------------------------------------------------

def self_test(root: Path = GOLDENS) -> int:
    if not root.is_dir() or count(root) == 0:
        print(f"goldens: self-test cannot run: no goldens under {root}")
        return 7
    work = Path(tempfile.mkdtemp(prefix="goldens-selftest-"))
    cases = []

    def fresh() -> Path:
        copy = work / f"case-{len(cases)}"
        shutil.copytree(root, copy)
        return copy

    def first_golden(tree: Path) -> Path:
        for directory in sorted(p for p in tree.iterdir() if p.is_dir()):
            for path in sorted(directory.iterdir()):
                if GOLDEN_RE.match(path.name):
                    return path
        raise AssertionError("no golden")

    def reviews(golden: Path):
        file = golden.parent / REVIEW
        return file, json.loads(file.read_text(encoding="utf-8"))

    def case(name, tree, needle):
        problems = check(tree)
        if needle is None:
            ok = problems == []
        else:
            ok = any(needle in problem for problem in problems)
        cases.append((name, ok, problems))

    case("the committed goldens -> clean", fresh(), None)

    tree = fresh()
    golden = first_golden(tree)
    document = json.loads(golden.read_text(encoding="utf-8"))
    first_metric = sorted(document["expected"])[0]
    value = document["expected"][first_metric]
    document["expected"][first_metric] = (value + 1) if isinstance(value, int) else f"{value}-changed"
    golden.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    case("a golden value changed with no new review record -> red", tree, "has no review record")

    tree = fresh()
    golden = first_golden(tree)
    golden.write_bytes(golden.read_bytes() + b"\n")
    case("a golden's bytes changed by one trailing newline -> red", tree, "has no review record")

    tree = fresh()
    golden = first_golden(tree)
    document = json.loads(golden.read_text(encoding="utf-8"))
    document["expected"][sorted(document["expected"])[0]] = "changed"
    golden.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    file, record = reviews(golden)
    record["reviews"].append({"file": golden.name, "sha256": sha256_of(golden), "date": "2026-11-06",
                              "reviewer": "a reviewer", "explanation": "TODO"})
    file.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    case("a changed golden recorded with a placeholder explanation -> red", tree, "placeholder")

    tree = fresh()
    golden = first_golden(tree)
    file, record = reviews(golden)
    for entry in record["reviews"]:
        entry["explanation"] = ""
    file.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    case("an empty explanation -> red", tree, "no 'explanation'")

    tree = fresh()
    golden = first_golden(tree)
    file, record = reviews(golden)
    for entry in record["reviews"]:
        entry["date"] = "2999-01-01"
    file.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    case("a review dated in the future -> red", tree, "in the future")

    tree = fresh()
    golden = first_golden(tree)
    (golden.parent / REVIEW).unlink()
    case("a golden directory with no REVIEW.json -> red", tree, "no REVIEW.json")

    tree = fresh()
    golden = first_golden(tree)
    file, record = reviews(golden)
    record["reviews"].append(dict(record["reviews"][0], file="fixture-" + "0" * 64 + ".linux-x86-64.json"))
    file.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    case("a review record naming a golden that does not exist -> red", tree, "(stale)")

    tree = fresh()
    golden = first_golden(tree)
    document = json.loads(golden.read_text(encoding="utf-8"))
    document["tolerances"] = {sorted(document["expected"])[0]: {"absolute": 1}}
    golden.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
    file, record = reviews(golden)
    record["reviews"].append(dict(record["reviews"][0], file=golden.name, sha256=sha256_of(golden)))
    file.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    case("a tolerance with no written justification -> red", tree, "not justified in writing")

    tree = fresh()
    golden = first_golden(tree)
    other = golden.parent.parent / "percolator-0.0.1"
    other.mkdir()
    shutil.copy(golden, other / golden.name)
    shutil.copy(golden.parent / REVIEW, other / REVIEW)
    case("a golden copied under another tool pair's key -> red", tree, "is not its directory")

    tree = fresh()
    golden = first_golden(tree)
    (golden.parent / "notes.txt").write_text("x", encoding="utf-8")
    case("a stray file in a golden directory -> red", tree, "neither a golden")

    shutil.rmtree(work, ignore_errors=True)
    failures = 0
    for name, ok, problems in cases:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}")
        if not ok:
            failures += 1
            for problem in problems[:5]:
                print(f"          {problem}")
    if failures:
        print(f"goldens: self-test FAILED -- {failures}/{len(cases)} case(s) did not behave as required")
        return 7
    print(f"goldens: self-test OK -- {len(cases)}/{len(cases)} cases")
    return 0


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="check_goldens.py", description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--root", default=str(GOLDENS))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    root = Path(args.root)
    if args.self_test:
        return self_test(root)
    problems = check(root)
    if problems:
        for problem in problems:
            print(f"goldens: {problem}")
        print(f"goldens: FAILED -- {len(problems)} problem(s) (R-TEST-07)")
        return 1
    print(f"goldens: OK -- {count(root)} golden file(s), every one covered by a reviewed explanation (R-TEST-07)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
