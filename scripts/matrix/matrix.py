#!/usr/bin/env python3
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
"""The version matrix (Phase 15 unit 6; PHASE-15 exit gate item 1).

"Every version/platform combination the product offers has a passing matrix
run, and every combination it does not offer is explicitly recorded as
unavailable rather than untested."

What this does
==============

``run`` drives three opt-in JUnit classes in ``cometgui-app``
(``org.cometgui.app.matrix.VersionMatrix{Census,Tools,Pairs}``) through Maven,
then GRADES what they wrote. The classes exercise the real product -- its
manifest reader, its Tool Manager over an empty cache installing every offered
build from its pinned upstream URL, its probes, its command builder, its run
port -- and record one JSON row per combination they exercised. This script
derives the full list of combinations itself and gives every one a verdict:

``RUN-PASSED``   exercised here, every check passed, and its golden values
                 equal the reviewed golden (``R-TEST-07``)
``RUN-FAILED``   exercised here and a check failed, a golden differed or was
                 missing, or the combination is offered here and NO row was
                 recorded for it
``UNAVAILABLE``  the product does not offer it; ``matrix-data.json`` records
                 why and under which decision
``UNVERIFIED``   offered, but not exercised by this invocation: a platform no
                 runner here can execute, a part not requested, or the fixture
                 part refused because no fixture was named

The list is derived, never typed (gate item 1's own risk): from
``manifests/tools.json`` read here as plain JSON, and from the census the
product itself wrote (every record ITS reader returned, and what
``ArtefactManifest.select`` offers on every platform it names). The two
readings must agree record for record, every gap must have a recorded reason,
every recorded reason must cover a gap, every row the product wrote must be one
this script derived, and every version difference observed must be explained
by a recorded advisory. Any of those failing makes the matrix red.

The fixture
===========

The pair runs (``--part pairs``) need spectra, and take them as ONE named
input: ``COMETGUI_MATRIX_FIXTURE`` (or ``--fixture``), a directory whose
``*.mzML`` files are searched against its one ``*.fasta``. There is no default.
Without it the pair part is REFUSED: its rows are UNVERIFIED, the exit status
is 3, and nothing about it is green. ``D-006``'s CI fixture is OPEN; locally,
``COMETGUI_MATRIX_FIXTURE=scratch/fixture``.

Usage::

    python3 scripts/matrix/matrix.py run [--part tools|pairs|all] [--out DIR]
                                         [--fixture DIR] [--offline]
                                         [--no-local-309]
    python3 scripts/matrix/matrix.py grade --report DIR [--part ...] [--fixture DIR]
    python3 scripts/matrix/matrix.py write-goldens --report DIR --out DIR
    python3 scripts/matrix/matrix.py --self-test

Exit status:

==  ===========================================================================
0   every combination this invocation is responsible for RUN-PASSED, and the
    derivation found nothing unexplained
1   the matrix is red: see PROBLEMS in the report
2   misuse, or a data file refused
3   the fixture part was requested and refused (no fixture named); the rest
    of the report is complete, and this is never green
7   --self-test: a case did not behave as required
==  ===========================================================================

Standard library only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform as host_platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))

import toolmatrix  # noqa: E402  (scripts/toolmatrix.py: KNOWN_PLATFORMS, load)
import check_goldens  # noqa: E402

DATA_FILE = HERE / "matrix-data.json"
GOLDENS = ROOT / "cometgui-app" / "src" / "test" / "resources" / "goldens"
COMET_FIXTURES = ROOT / "cometgui-params-comet" / "src" / "test" / "resources" / "fixtures" / "comet"
DEFAULT_OUT = ROOT / "_build" / "version-matrix"
FIXTURE_ENV = "COMETGUI_MATRIX_FIXTURE"

PASSED, FAILED, UNAVAILABLE, UNVERIFIED = "RUN-PASSED", "RUN-FAILED", "UNAVAILABLE", "UNVERIFIED"
VERDICTS = (PASSED, FAILED, UNAVAILABLE, UNVERIFIED)

EXIT_GREEN, EXIT_RED, EXIT_MISUSE, EXIT_REFUSED, EXIT_SELF_TEST = 0, 1, 2, 3, 7

CLASSES = {
    "census": "org.cometgui.app.matrix.VersionMatrixCensus",
    "tools": "org.cometgui.app.matrix.VersionMatrixTools",
    "pairs": "org.cometgui.app.matrix.VersionMatrixPairs",
}

ADVISORY_KINDS = (
    "comet-defaults",
    "comet-schema",
    "percolator-capabilities",
    "percolator-synthetic-outputs",
    "pair-results-percolator",
    "pair-results-comet",
)


class Refused(Exception):
    """A data file or an input this script will not grade with."""


# ===========================================================================
# Inputs
# ===========================================================================

def load_data(path: Path = DATA_FILE) -> dict:
    """Read and validate ``matrix-data.json``."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise Refused(f"{path}: {error}") from None
    for key in ("platforms", "notOffered", "features", "local", "advisories"):
        if key not in data:
            raise Refused(f"{path}: no '{key}'")
    for name, entry in data["platforms"].items():
        for field in ("tier", "ciRunner", "ciWouldRun"):
            if field not in entry or not isinstance(entry[field], str):
                raise Refused(f"{path}: platform {name} has no string '{field}'")
    for index, entry in enumerate(data["notOffered"]):
        for field in ("tool", "version", "platform", "verdict", "decision", "reason"):
            if not str(entry.get(field, "")).strip():
                raise Refused(f"{path}: notOffered[{index}] has no '{field}'")
        if entry["verdict"] not in (UNAVAILABLE, UNVERIFIED):
            raise Refused(f"{path}: notOffered[{index}] verdict must be UNAVAILABLE or UNVERIFIED")
        if len(entry["reason"]) < 40:
            raise Refused(f"{path}: notOffered[{index}]'s reason is too short to be one")
    for index, entry in enumerate(data["advisories"]):
        for field in ("id", "kind", "versions", "text", "docs"):
            if not entry.get(field):
                raise Refused(f"{path}: advisories[{index}] has no '{field}'")
        if entry["kind"] not in ADVISORY_KINDS:
            raise Refused(f"{path}: advisories[{index}] kind {entry['kind']!r} is not one of {ADVISORY_KINDS}")
        if len(entry["versions"]) != 2:
            raise Refused(f"{path}: advisories[{index}] names {len(entry['versions'])} versions, not 2")
    return data


def raw_manifest_records(manifest_file: Path) -> list:
    """Every record of the manifest, read as plain JSON: the second, independent reading."""
    toolmatrix.load(manifest_file)  # the project's own validation; raises on a broken manifest
    document = json.loads(Path(manifest_file).read_text(encoding="utf-8"))
    return [
        (artefact["tool"], artefact["version"], f"{artefact['os']}-{artefact['arch']}")
        for artefact in document["artefacts"]
    ]


def fixture_digest(directory: Path) -> tuple:
    """The fixture's digest, as VersionMatrixPairs computes it: computed here independently."""
    files = sorted(
        (path for path in Path(directory).iterdir()
         if path.is_file() and (path.name.endswith(".mzML") or path.name.endswith(".fasta"))),
        key=lambda path: path.name.encode("utf-8"),
    )
    lines = "".join(f"{sha256_of(path)}  {path.name}\n" for path in files)
    return hashlib.sha256(lines.encode("utf-8")).hexdigest(), [path.name for path in files]


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def this_host() -> str:
    system = {"Linux": "linux", "Darwin": "macos", "Windows": "windows"}.get(host_platform.system(), "?")
    machine = host_platform.machine().lower()
    arch = {"x86_64": "x86-64", "amd64": "x86-64", "aarch64": "aarch64", "arm64": "aarch64"}.get(machine, machine)
    return f"{system}-{arch}"


def read_rows(report: Path) -> dict:
    rows = {}
    directory = Path(report) / "rows"
    if directory.is_dir():
        for file in sorted(directory.glob("*.json")):
            row = json.loads(file.read_text(encoding="utf-8"))
            rows[row["id"]] = row
    return rows


# ===========================================================================
# Grading
# ===========================================================================

def matches(entry_value: str, value: str) -> bool:
    return entry_value == "*" or entry_value == value


def version_key(text: str):
    return tuple(int(part) if part.isdigit() else part for part in re.split(r"[.-]", text))


def comet_defaults(version: str, platform: str):
    """name -> value of a committed -q fixture, comments stripped; None if absent."""
    fixture = COMET_FIXTURES / version / platform / "comet-q.params"
    if not fixture.is_file():
        return None
    values = {}
    for line in fixture.read_text(encoding="utf-8").splitlines():
        text = line.split("#", 1)[0].strip()
        if "=" in text and not text.startswith("["):
            name, value = text.split("=", 1)
            values[name.strip()] = value.strip()
    return values


def compare_golden(row: dict, platform: str, goldens: Path) -> list:
    """The golden problems of one row: [] when it equals its reviewed golden."""
    observed = row.get("observed", {})
    key = observed.get("goldenKey")
    if not key:
        return []
    digest = observed.get("fixtureDigest", "")
    path = Path(goldens) / key / f"fixture-{digest}.{platform}.json"
    if not path.is_file():
        return [f"no reviewed golden for {key} over fixture {digest} on {platform} "
                f"(expected {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}); "
                "R-TEST-07: a new golden is added with its review record, never assumed"]
    golden = json.loads(path.read_text(encoding="utf-8"))
    metrics = observed.get("metrics", {})
    tolerances = golden.get("tolerances", {})
    problems = []
    for name, expected in golden["expected"].items():
        if name not in metrics:
            problems.append(f"golden {key}: '{name}' expected {expected!r}, observed nothing")
            continue
        actual = metrics[name]
        tolerance = tolerances.get(name)
        if tolerance and isinstance(expected, (int, float)) and isinstance(actual, (int, float)):
            allowed = tolerance.get("absolute", 0)
            if "relative" in tolerance:
                allowed = max(allowed, abs(expected) * tolerance["relative"])
            if abs(actual - expected) > allowed:
                problems.append(f"golden {key}: '{name}' expected {expected!r} +/- {allowed}, observed {actual!r}")
        elif actual != expected:
            problems.append(f"golden {key}: '{name}' expected {expected!r}, observed {actual!r}")
    return problems


def failed_checks(row: dict) -> list:
    return [f"{check['name']} -- {check.get('detail', '')}" for check in row.get("checks", []) if not check.get("passed")]


def grade(census: dict, rows: dict, data: dict, raw_records: list, parts: set,
          fixture: dict, goldens: Path = GOLDENS, known_platforms=None) -> dict:
    """The matrix report: every derived combination with its verdict, and the problems."""
    problems = []
    report_rows = []
    host = census["host"]
    platforms = census["platforms"]

    # -- the two readings of the manifest must agree, record for record --------------------
    product_records = [(r["tool"], r["version"], r["platform"]) for r in census["manifestRecords"]]
    if sorted(product_records) != sorted(raw_records):
        only_raw = sorted(set(raw_records) - set(product_records))
        only_product = sorted(set(product_records) - set(raw_records))
        problems.append(
            f"the product's manifest reader returned {len(product_records)} record(s) and "
            f"manifests/tools.json holds {len(raw_records)}: only in the file {only_raw}, "
            f"only in the product {only_product}")
    known = toolmatrix.KNOWN_PLATFORMS if known_platforms is None else known_platforms
    missing_platforms = [p for p in known if p not in platforms]
    if missing_platforms:
        problems.append(f"the product names no platform {missing_platforms}, which the documentation renders")
    for p in platforms:
        if p not in data["platforms"]:
            problems.append(f"matrix-data.json says nothing about platform {p}: its tier and runner are unrecorded")
    for p in data["platforms"]:
        if p not in platforms:
            problems.append(f"matrix-data.json describes platform {p}, which the product does not name (stale)")

    # Releases per tool from the FILE, and the product's selections, keyed.
    releases = {}
    for tool, version, _ in raw_records:
        releases.setdefault(tool, set()).add(version)
    selections = {(s["platform"], s["tool"], s["version"]): s["records"] for s in census["selections"]}
    for p in platforms:
        for tool, versions in releases.items():
            for version in versions:
                if (p, tool, version) not in selections:
                    problems.append(f"the census has no selection for {tool} {version} on {p}")
    selected_records = set()
    for (p, tool, version), records in selections.items():
        for record in records:
            selected_records.add((tool, version, record["platform"]))
    for record in raw_records:
        if record not in selected_records:
            problems.append(f"manifest record {record} is offered on no platform: it has no matrix verdict")

    expected_ids = set()
    used_entries = set()

    def gap_entry(tool, version, p):
        for index, entry in enumerate(data["notOffered"]):
            if matches(entry["tool"], tool) and matches(entry["version"], version) and matches(entry["platform"], p):
                used_entries.add(index)
                return entry
        return None

    def platform_reason(p, extra=""):
        info = data["platforms"].get(p, {})
        runner = info.get("ciRunner") or "no runner exists"
        return (f"no {p} machine here; the CI matrix would run, on {runner}: {info.get('ciWouldRun', '?')}. "
                f"Tier: {info.get('tier', '?')}" + (f". {extra}" if extra else ""))

    def from_row(row_id, base, part):
        expected_ids.add(row_id)
        if part not in parts:
            base.update(verdict=UNVERIFIED, reason=f"not exercised by this invocation (--part did not include {part})")
            return base
        row = rows.get(row_id)
        if row is None:
            base.update(verdict=FAILED, reason="no verdict recorded: offered here and never exercised "
                                               "(no row was written for it)")
            return base
        reasons = failed_checks(row)
        reasons += compare_golden(row, base["platform"], goldens)
        base["evidence"] = {
            "checks": [c["name"] for c in row.get("checks", [])],
            "observed": {k: v for k, v in row.get("observed", {}).items()
                         if k not in ("schemaParameters",)},
        }
        if reasons or not row.get("passed"):
            base.update(verdict=FAILED, reason="; ".join(reasons) or "the row says it did not pass")
        else:
            base.update(verdict=PASSED, reason="every check passed" +
                        ("; equal to its reviewed golden" if row.get("observed", {}).get("goldenKey") else ""))
        return base

    offered = {}  # platform -> tool -> [versions]
    for p in platforms:
        for tool in sorted(releases):
            for version in sorted(releases[tool], key=version_key):
                records = selections.get((p, tool, version), [])
                row_id = f"offer:{tool}:{version}:{p}"
                base = {"id": row_id, "kind": "offer", "tools": {tool: version}, "platform": p,
                        "os": p.split("-", 1)[0], "arch": p.split("-", 1)[1]}
                if records:
                    offered.setdefault(p, {}).setdefault(tool, []).append(version)
                    translated = [r for r in records if r["translated"]]
                    base["artefacts"] = [r["platform"] for r in records]
                    if p == host:
                        report_rows.append(from_row(row_id, base, "tools"))
                    else:
                        extra = (f"served by the {translated[0]['platform']} artefact under Rosetta 2 (D-004)"
                                 if translated else "")
                        base.update(verdict=UNVERIFIED, reason=platform_reason(p, extra))
                        report_rows.append(base)
                else:
                    entry = gap_entry(tool, version, p)
                    if entry is None:
                        problems.append(f"{tool} {version} on {p} is not offered by the product and "
                                        "matrix-data.json records no reason: an unexplained gap")
                        base.update(verdict=UNAVAILABLE, reason="NO REASON RECORDED (the matrix is red)")
                    else:
                        base.update(verdict=entry["verdict"], reason=f"{entry['reason']} [{entry['decision']}]")
                    report_rows.append(base)
    for index, entry in enumerate(data["notOffered"]):
        if index not in used_entries:
            problems.append(f"matrix-data.json notOffered entry {entry['tool']} {entry['version']} on "
                            f"{entry['platform']} covers no gap: the product offers it, or it names nothing (stale)")

    # -- the host's offers must be what the Tool Manager listed --------------------------------
    listed = sorted(
        (o["tool"], o["version"]) for o in census["toolManager"].get(host, {}).get("offers", [])
        if o["origin"] == "MANAGED" and o["state"] != "UNAVAILABLE_ON_THIS_PLATFORM")
    derived = sorted((tool, v) for tool, versions in offered.get(host, {}).items() for v in versions)
    if listed != derived:
        problems.append(f"on {host} the Tool Manager lists {listed} and the manifest selection gives {derived}")

    # -- features that need a runner nothing here has ------------------------------------------
    for record in census["manifestRecords"]:
        for capability in record["capabilities"]:
            feature = data["features"].get(capability)
            if feature is None:
                continue
            row_id = f"{feature['rowPrefix']}:{record['tool']}-{record['version']}:{record['platform']}"
            report_rows.append({"id": row_id, "kind": "feature", "tools": {record["tool"]: record["version"]},
                                "platform": record["platform"], "os": record["platform"].split("-", 1)[0],
                                "arch": record["platform"].split("-", 1)[1], "verdict": feature["verdict"],
                                "reason": feature["reason"]})

    # -- pairs ----------------------------------------------------------------------------------
    for p in platforms:
        comets = offered.get(p, {}).get("comet", [])
        percolators = offered.get(p, {}).get("percolator", [])
        for c in comets:
            for q in percolators:
                row_id = f"pair:comet-{c}:percolator-{q}:{p}"
                base = {"id": row_id, "kind": "pair", "tools": {"comet": c, "percolator": q}, "platform": p,
                        "os": p.split("-", 1)[0], "arch": p.split("-", 1)[1]}
                if p != host:
                    base.update(verdict=UNVERIFIED, reason=platform_reason(p))
                    report_rows.append(base)
                elif "pairs" in parts and fixture.get("refused"):
                    expected_ids.add(row_id)
                    base.update(verdict=UNVERIFIED, reason=fixture["refused"])
                    report_rows.append(base)
                else:
                    report_rows.append(from_row(row_id, base, "pairs"))

    # -- local binaries -------------------------------------------------------------------------
    for entry in data["local"]:
        row_id = entry["id"]
        p = row_id.rsplit(":", 1)[1]
        tool, version = row_id.split(":")[1:3]
        base = {"id": row_id, "kind": "local binary, not a managed offer", "tools": {tool: version},
                "platform": p, "os": p.split("-", 1)[0], "arch": p.split("-", 1)[1]}
        if p != host:
            base.update(verdict=UNVERIFIED, reason=platform_reason(p, entry["reason"]))
        elif row_id in rows or ("tools" in parts and fixture.get("local309")):
            from_row(row_id, base, "tools")
            base["reason"] += f" -- {entry['reason']}"
        else:
            base.update(verdict=UNVERIFIED, reason=f"no local binary given here ({entry['wrapper']} absent): "
                                                   f"{entry['reason']}")
        expected_ids.add(row_id)
        report_rows.append(base)

    for row_id in rows:
        if row_id not in expected_ids:
            problems.append(f"the product exercised {row_id}, which this matrix did not derive from the manifest")

    # -- the fixture digest, computed twice -----------------------------------------------------
    if fixture.get("digest"):
        for row in rows.values():
            seen = row.get("observed", {}).get("fixtureDigest")
            if row["id"].startswith("pair:") and seen != fixture["digest"]:
                problems.append(f"{row['id']}: the run's fixture digest {seen} is not {fixture['digest']}, "
                                "computed here from the same directory")

    differences = observe_differences(rows, host, data, problems)
    return {"host": host, "rows": report_rows, "differences": differences, "problems": problems}


def observe_differences(rows: dict, host: str, data: dict, problems: list) -> list:
    """Every version difference the rows show, each matched to an advisory (or a problem)."""
    found = []

    def explain(kind, a, b, what):
        versions = sorted([a, b], key=version_key)
        for advisory in data["advisories"]:
            if advisory["kind"] == kind and sorted(advisory["versions"], key=version_key) == versions:
                found.append({"kind": kind, "versions": versions, "observed": what, "advisory": advisory["id"]})
                return
        found.append({"kind": kind, "versions": versions, "observed": what, "advisory": None})
        problems.append(f"an unexplained version difference ({kind}, {versions[0]} vs {versions[1]}): {what}. "
                        "Record an advisory in scripts/matrix/matrix-data.json and the documentation; "
                        "never special-case it")

    def evaluated(kind, a, b):
        return any(f["kind"] == kind and f["versions"] == sorted([a, b], key=version_key) for f in found)

    by_kind = {"comet": {}, "percolator": {}}
    for row in rows.values():
        parts = row["id"].split(":")
        if parts[0] in ("offer", "local") and parts[-1] == host and parts[1] in by_kind and row.get("passed"):
            by_kind[parts[1]][parts[2]] = row
    comets = sorted(by_kind["comet"], key=version_key)
    for a, b in zip(comets, comets[1:]):
        names_a = set(by_kind["comet"][a]["observed"].get("schemaParameters", []))
        names_b = set(by_kind["comet"][b]["observed"].get("schemaParameters", []))
        if names_a != names_b:
            explain("comet-schema", a, b, f"added {sorted(names_b - names_a)}, removed {sorted(names_a - names_b)}")
        defaults_a, defaults_b = comet_defaults(a, host), comet_defaults(b, host)
        if defaults_a is not None and defaults_b is not None:
            changed = sorted(n for n in set(defaults_a) | set(defaults_b) if defaults_a.get(n) != defaults_b.get(n))
            if changed:
                explain("comet-defaults", a, b, "default changed: " + ", ".join(
                    f"{n} {defaults_a.get(n)!r} -> {defaults_b.get(n)!r}" for n in changed))
    percolators = sorted(by_kind["percolator"], key=version_key)
    for i, a in enumerate(percolators):
        for b in percolators[i + 1:]:
            ra, rb = by_kind["percolator"][a]["observed"], by_kind["percolator"][b]["observed"]
            caps_a, caps_b = set(ra.get("capabilities", [])), set(rb.get("capabilities", []))
            if caps_a != caps_b:
                explain("percolator-capabilities", a, b,
                        f"only {a}: {sorted(caps_a - caps_b)}; only {b}: {sorted(caps_b - caps_a)}")
            ma, mb = ra.get("metrics", {}), rb.get("metrics", {})
            differing = sorted(k for k in set(ma) & set(mb) if k.endswith(".sha256") and ma[k] != mb[k])
            if differing:
                explain("percolator-synthetic-outputs", a, b, "outputs differ: " + ", ".join(differing))
    pairs = {}
    for row in rows.values():
        if row["id"].startswith("pair:") and row["id"].endswith(":" + host) and row.get("passed"):
            _, comet, percolator, _ = row["id"].split(":")
            pairs[(comet.split("-", 1)[1], percolator.split("-", 1)[1])] = row["observed"].get("metrics", {})
    for (c1, p1), m1 in sorted(pairs.items()):
        for (c2, p2), m2 in sorted(pairs.items()):
            differing = sorted(k for k in set(m1) & set(m2) if m1[k] != m2[k])
            if not differing:
                continue
            if c1 == c2 and version_key(p1) < version_key(p2) and not evaluated("pair-results-percolator", p1, p2):
                explain("pair-results-percolator", p1, p2, f"with Comet {c1}: " + ", ".join(differing))
            if p1 == p2 and version_key(c1) < version_key(c2) and not evaluated("pair-results-comet", c1, c2):
                explain("pair-results-comet", c1, c2, f"with Percolator {p1}: " + ", ".join(differing))
    return found


def summarise(report: dict, parts: set, fixture: dict) -> int:
    counts = {verdict: 0 for verdict in VERDICTS}
    for row in report["rows"]:
        counts[row["verdict"]] += 1
    report["summary"] = counts
    if report["problems"] or counts[FAILED]:
        return EXIT_RED
    if "pairs" in parts and fixture.get("refused"):
        return EXIT_REFUSED
    return EXIT_GREEN


def render(report: dict) -> str:
    lines = [
        "===============================================================================",
        " CometGUI version matrix -- PHASE-15 gate item 1",
        "===============================================================================",
        f" generated {report.get('generated', '?')}   host {report['host']}   parts {', '.join(report.get('parts', []))}",
        f" fixture   {report.get('fixture', {}).get('summary', '(none)')}",
        "",
    ]
    width = max(len(row["id"]) for row in report["rows"]) if report["rows"] else 10
    for row in report["rows"]:
        lines.append(f" {row['verdict']:<11} {row['id']:<{width}}  {row['reason']}")
    lines.append("")
    lines.append(" Version differences observed")
    for difference in report["differences"]:
        lines.append(f"   {difference['kind']} {difference['versions'][0]} vs {difference['versions'][1]}: "
                     f"{difference['observed']}  -> advisory {difference['advisory'] or 'NONE (red)'}")
    if not report["differences"]:
        lines.append("   (none evaluated)")
    lines.append("")
    if report["problems"]:
        lines.append(" PROBLEMS (the matrix is red)")
        lines.extend(f"   - {problem}" for problem in report["problems"])
    else:
        lines.append(" PROBLEMS: none")
    lines.append("")
    summary = report.get("summary", {})
    lines.append(" SUMMARY  " + "  ".join(f"{verdict} {summary.get(verdict, 0)}" for verdict in VERDICTS))
    lines.append(f" VERDICT  {report.get('verdict', '?')}")
    return "\n".join(lines) + "\n"


VERDICT_WORDS = {
    EXIT_GREEN: "GREEN -- every combination this invocation is responsible for RUN-PASSED",
    EXIT_RED: "RED -- see the problems and RUN-FAILED rows above",
    EXIT_REFUSED: "REFUSED -- the fixture part was requested and no fixture was named; not green",
}


def finish(report: dict, parts: set, fixture: dict, out: Path) -> int:
    status = summarise(report, parts, fixture)
    report["exit"] = status
    report["verdict"] = VERDICT_WORDS[status]
    out.mkdir(parents=True, exist_ok=True)
    (out / "matrix-report.json").write_text(json.dumps(report, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    text = render(report)
    (out / "matrix-report.txt").write_text(text, encoding="utf-8")
    sys.stdout.write(text)
    print(f"matrix: report {out / 'matrix-report.json'} and {out / 'matrix-report.txt'}")
    return status


# ===========================================================================
# Commands
# ===========================================================================

def resolve_fixture(args, parts: set) -> dict:
    named = args.fixture or os.environ.get(FIXTURE_ENV, "")
    fixture = {"env": FIXTURE_ENV}
    if "pairs" not in parts:
        fixture["summary"] = "not used (--part tools)"
        return fixture
    if not named:
        fixture["refused"] = (f"REFUSED: the fixture part needs its one named input, {FIXTURE_ENV} (a directory "
                              "of *.mzML and one *.fasta), and none was named. Nothing was searched; this is "
                              "not a pass. D-006's CI fixture is OPEN; locally, scratch/fixture")
        fixture["summary"] = f"REFUSED ({FIXTURE_ENV} not set)"
        return fixture
    directory = Path(named).resolve()
    if not directory.is_dir():
        raise Refused(f"{FIXTURE_ENV}={named} is not a directory")
    digest, files = fixture_digest(directory)
    fixture.update(directory=str(directory), digest=digest, files=files,
                   summary=f"{directory} digest {digest} ({len(files)} files: {', '.join(files)})")
    return fixture


def command_run(args) -> int:
    parts = parts_of(args.part)
    data = load_data()
    fixture = resolve_fixture(args, parts)
    out = Path(args.out).resolve()
    if out.exists():
        shutil.rmtree(out)
    (out / "rows").mkdir(parents=True)
    local = ROOT / data["local"][0]["wrapper"] if data["local"] else None
    selected = [CLASSES["census"]]
    if "tools" in parts:
        selected.append(CLASSES["tools"])
    if "pairs" in parts and not fixture.get("refused"):
        selected.append(CLASSES["pairs"])
    command = ["mvn", "-B", f"-Dmaven.repo.local={ROOT / '_build' / 'm2repo'}"]
    if args.offline:
        command.append("-o")
    command += ["-pl", "cometgui-app", "-am", "test",
                "-Dtest=" + ",".join(selected), "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Djacoco.skip=true", "-Dcometgui.matrix=true", f"-Dcometgui.matrix.report={out}"]
    if "pairs" in parts and fixture.get("directory"):
        command.append(f"-Dcometgui.matrix.fixture={fixture['directory']}")
    if "tools" in parts and local is not None and local.is_file() and not args.no_local_309:
        command.append(f"-Dcometgui.matrix.local309={local}")
        fixture["local309"] = str(local)
    print("matrix: " + " ".join(command), flush=True)
    started = time.monotonic()
    log = out / "maven.log"
    with open(log, "w", encoding="utf-8") as stream:
        status = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT).returncode
    elapsed = int(time.monotonic() - started)
    print(f"matrix: Maven exited {status} after {elapsed} s; log {log}", flush=True)
    census_file = out / "census.json"
    if not census_file.is_file():
        print(f"matrix: RED -- the product wrote no census ({census_file}); Maven exit {status}. "
              f"The tail of {log}:", file=sys.stderr)
        for line in log.read_text(encoding="utf-8", errors="replace").splitlines()[-40:]:
            print("  | " + line, file=sys.stderr)
        return EXIT_RED
    report = grade_report(out, parts, fixture, data)
    if status != 0 and not report["problems"] and not any(r["verdict"] == FAILED for r in report["rows"]):
        report["problems"].append(f"Maven exited {status} with no failed row: see {log}")
    report["mavenExit"] = status
    report["mavenSeconds"] = elapsed
    return finish(report, parts, fixture, out)


def grade_report(out: Path, parts: set, fixture: dict, data: dict) -> dict:
    census = json.loads((out / "census.json").read_text(encoding="utf-8"))
    rows = read_rows(out)
    raw = raw_manifest_records(ROOT / "manifests" / "tools.json")
    report = grade(census, rows, data, raw, parts, fixture)
    goldens = check_goldens.check(GOLDENS)
    if goldens:
        report["problems"].extend(f"golden review rule (R-TEST-07): {problem}" for problem in goldens)
    report.update(generated=time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()), parts=sorted(parts),
                  fixture={k: v for k, v in fixture.items() if k != "files"}, gitHead=git_head())
    return report


def command_grade(args) -> int:
    parts = parts_of(args.part)
    data = load_data()
    fixture = resolve_fixture(args, parts)
    out = Path(args.report).resolve()
    if args.local309:
        fixture["local309"] = args.local309
    report = grade_report(out, parts, fixture, data)
    return finish(report, parts, fixture, Path(args.out).resolve() if args.out else out)


def command_write_goldens(args) -> int:
    """Candidate goldens from a report's rows, for a person to review: never into the tree."""
    report, target = Path(args.report), Path(args.out)
    if target.resolve().is_relative_to(ROOT.resolve()) and not target.resolve().is_relative_to(ROOT / "_build"):
        print("write-goldens: refusing to write inside the repository; review the candidates, then copy them",
              file=sys.stderr)
        return EXIT_MISUSE
    for row in read_rows(report).values():
        observed = row.get("observed", {})
        if not observed.get("goldenKey") or not row.get("passed"):
            continue
        p = row["id"].rsplit(":", 1)[1]
        directory = target / observed["goldenKey"]
        directory.mkdir(parents=True, exist_ok=True)
        golden = {
            "key": observed["goldenKey"],
            "platform": p,
            "fixtureDigest": observed["fixtureDigest"],
            "fixture": "DESCRIBE THE FIXTURE",
            "comparison": "exact",
            "why": "DESCRIBE WHY EXACT",
            "expected": observed.get("metrics", {}),
            "tolerances": {},
        }
        path = directory / f"fixture-{observed['fixtureDigest']}.{p}.json"
        path.write_text(json.dumps(golden, indent=2) + "\n", encoding="utf-8")
        print(f"write-goldens: {path}")
    return EXIT_GREEN


def parts_of(part: str) -> set:
    return {"tools", "pairs"} if part == "all" else {part}


def git_head() -> str:
    try:
        return subprocess.run(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"], capture_output=True,
                              text=True, check=False).stdout.strip()
    except OSError:
        return "?"


# ===========================================================================
# Self-test: the grading, over synthetic census and rows (no Maven, no network)
# ===========================================================================

def self_test() -> int:
    import copy
    data = load_data()
    # A small world: two platforms, Comet and Percolator, one gap with a reason.
    base_data = copy.deepcopy(data)
    base_data["platforms"] = {p: data["platforms"].get(p, {"tier": "t", "ciRunner": "r", "ciWouldRun": "w"})
                              for p in ("linux-x86-64", "macos-x86-64")}
    base_data["notOffered"] = [{"tool": "comet", "version": "*", "platform": "macos-x86-64",
                                "verdict": "UNAVAILABLE", "decision": "D-011 option A",
                                "reason": "no x86-64 macOS Comet is published upstream, recorded for the self-test"},
                               {"tool": "percolator", "version": "2.0", "platform": "macos-x86-64",
                                "verdict": "UNAVAILABLE", "decision": "D-003",
                                "reason": "release 2.0 publishes no macOS x86-64 build, recorded for the self-test"}]
    base_data["features"] = {}
    base_data["local"] = []
    base_data["advisories"] = [{"id": "a", "kind": "percolator-synthetic-outputs", "versions": ["1.0", "2.0"],
                                "text": "t", "docs": "d"}]
    raw = [("comet", "9.0", "linux-x86-64"), ("percolator", "1.0", "linux-x86-64"),
           ("percolator", "2.0", "linux-x86-64"), ("percolator", "1.0", "macos-x86-64")]

    def census_of(records):
        selections = []
        for p in ("linux-x86-64", "macos-x86-64"):
            for tool, version in sorted({(t, v) for t, v, _ in records}):
                chosen = [{"platform": rp, "translated": False} for t, v, rp in records
                          if t == tool and v == version and rp == p]
                selections.append({"platform": p, "tool": tool, "version": version, "records": chosen})
        return {"host": "linux-x86-64", "platforms": ["linux-x86-64", "macos-x86-64"],
                "manifestRecords": [{"tool": t, "version": v, "platform": p, "capabilities": []}
                                    for t, v, p in records],
                "selections": selections,
                "toolManager": {"linux-x86-64": {"offers": [
                    {"tool": t, "version": v, "origin": "MANAGED", "state": "NOT_INSTALLED"}
                    for t, v, p in records if p == "linux-x86-64"]}}}

    def row(row_id, passed=True, metrics=None, key=None):
        observed = {"metrics": metrics or {}}
        if key:
            observed.update(goldenKey=key, fixtureDigest="d" * 64)
        return {"id": row_id, "passed": passed,
                "checks": [{"name": "a check", "passed": passed, **({} if passed else {"detail": "it broke"})}],
                "observed": observed}

    def clean_rows():
        return {
            "offer:comet:9.0:linux-x86-64": row("offer:comet:9.0:linux-x86-64"),
            "offer:percolator:1.0:linux-x86-64": row("offer:percolator:1.0:linux-x86-64",
                                                     metrics={"x.sha256": "a"}),
            "offer:percolator:2.0:linux-x86-64": row("offer:percolator:2.0:linux-x86-64",
                                                     metrics={"x.sha256": "b"}),
            "pair:comet-9.0:percolator-1.0:linux-x86-64": row("pair:comet-9.0:percolator-1.0:linux-x86-64"),
            "pair:comet-9.0:percolator-2.0:linux-x86-64": row("pair:comet-9.0:percolator-2.0:linux-x86-64"),
        }

    work = Path(tempfile.mkdtemp(prefix="matrix-selftest-"))
    goldens = work / "goldens"
    goldens.mkdir()
    both = {"tools", "pairs"}
    cases = []

    def case(name, expect_status, needle, census=None, rows=None, data_=None, raw_=None, parts=both,
             fixture=None):
        report = grade(census or census_of(raw), rows if rows is not None else clean_rows(), data_ or base_data,
                       raw_ or raw, parts, fixture or {"digest": None}, goldens,
                       known_platforms=("linux-x86-64", "macos-x86-64"))
        status = summarise(report, parts, fixture or {})
        text = render(report)
        ok = status == expect_status and (needle is None or needle in text)
        cases.append((name, ok, status, expect_status, needle))

    case("a clean world -> green", EXIT_GREEN, "RUN-PASSED  offer:comet:9.0:linux-x86-64")
    case("the macOS rows are UNVERIFIED and the Comet gap UNAVAILABLE with its decision", EXIT_GREEN,
         "UNAVAILABLE offer:comet:9.0:macos-x86-64")
    dropped = raw[:3]
    case("the matrix reader ignoring a manifest row -> red, naming it", EXIT_RED,
         "only in the product [('percolator', '1.0', 'macos-x86-64')]", raw_=dropped)
    rows = clean_rows()
    del rows["offer:percolator:2.0:linux-x86-64"]
    case("an offered combination with no row -> RUN-FAILED", EXIT_RED, "no verdict recorded", rows=rows)
    rows = clean_rows()
    rows["offer:percolator:1.0:linux-x86-64"] = row("offer:percolator:1.0:linux-x86-64", passed=False,
                                                    metrics={"x.sha256": "a"})
    case("a failed check -> RUN-FAILED with its words", EXIT_RED, "a check -- it broke", rows=rows)
    no_reason = copy.deepcopy(base_data)
    no_reason["notOffered"] = []
    case("a gap with no recorded reason -> red", EXIT_RED, "an unexplained gap", data_=no_reason)
    stale = copy.deepcopy(base_data)
    stale["notOffered"].append({"tool": "percolator", "version": "1.0", "platform": "linux-x86-64",
                                "verdict": "UNAVAILABLE", "decision": "x", "reason": "r" * 50})
    case("a recorded reason that covers no gap -> red", EXIT_RED, "covers no gap", data_=stale)
    rows = clean_rows()
    rows["offer:pdv:1:linux-x86-64"] = row("offer:pdv:1:linux-x86-64")
    case("a row the matrix did not derive -> red", EXIT_RED, "which this matrix did not derive", rows=rows)
    no_advisory = copy.deepcopy(base_data)
    no_advisory["advisories"] = []
    case("a version difference with no advisory -> red", EXIT_RED, "an unexplained version difference",
         data_=no_advisory)
    rows = clean_rows()
    rows["pair:comet-9.0:percolator-1.0:linux-x86-64"] = row(
        "pair:comet-9.0:percolator-1.0:linux-x86-64", metrics={"psms": 10}, key="comet-9.0_percolator-1.0")
    case("a pair with no reviewed golden -> RUN-FAILED", EXIT_RED, "no reviewed golden", rows=rows)
    golden_dir = goldens / "comet-9.0_percolator-1.0"
    golden_dir.mkdir()
    (golden_dir / f"fixture-{'d' * 64}.linux-x86-64.json").write_text(json.dumps(
        {"expected": {"psms": 11}, "tolerances": {}}), encoding="utf-8")
    case("a golden value differing -> RUN-FAILED naming expected and observed", EXIT_RED,
         "'psms' expected 11, observed 10", rows=rows)
    (golden_dir / f"fixture-{'d' * 64}.linux-x86-64.json").write_text(json.dumps(
        {"expected": {"psms": 10}, "tolerances": {}}), encoding="utf-8")
    case("the same golden equal -> green", EXIT_GREEN, "equal to its reviewed golden", rows=rows)
    case("the fixture part refused -> exit 3, the pairs UNVERIFIED, never green", EXIT_REFUSED,
         "UNVERIFIED  pair:comet-9.0:percolator-1.0:linux-x86-64",
         rows={k: v for k, v in clean_rows().items() if not k.startswith("pair:")},
         fixture={"refused": "REFUSED: no fixture"})
    case("--part tools: the pairs UNVERIFIED as not exercised, green", EXIT_GREEN,
         "not exercised by this invocation", parts={"tools"},
         rows={k: v for k, v in clean_rows().items() if not k.startswith("pair:")})
    rows = clean_rows()
    rows["pair:comet-9.0:percolator-1.0:linux-x86-64"]["observed"]["fixtureDigest"] = "e" * 64
    case("a run's fixture digest differing from the one computed here -> red", EXIT_RED,
         "is not " + "f" * 64, rows=rows, fixture={"digest": "f" * 64})
    shutil.rmtree(work, ignore_errors=True)

    failures = 0
    for name, ok, status, expected, needle in cases:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}  (exit {status}, required {expected}"
              + (f", words {needle!r}" if needle else "") + ")")
        failures += 0 if ok else 1
    if failures:
        print(f"matrix: self-test FAILED -- {failures}/{len(cases)} case(s) did not behave as required")
        return EXIT_SELF_TEST
    print(f"matrix: self-test OK -- {len(cases)}/{len(cases)} cases")
    return EXIT_GREEN


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="matrix.py", description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--self-test", action="store_true")
    sub = parser.add_subparsers(dest="command")
    run = sub.add_parser("run")
    run.add_argument("--part", choices=("tools", "pairs", "all"), default="all")
    run.add_argument("--out", default=str(DEFAULT_OUT))
    run.add_argument("--fixture", default="")
    run.add_argument("--offline", action="store_true")
    run.add_argument("--no-local-309", action="store_true")
    grade_parser = sub.add_parser("grade")
    grade_parser.add_argument("--report", required=True)
    grade_parser.add_argument("--part", choices=("tools", "pairs", "all"), default="all")
    grade_parser.add_argument("--fixture", default="")
    grade_parser.add_argument("--local309", default="")
    grade_parser.add_argument("--out", default="")
    write = sub.add_parser("write-goldens")
    write.add_argument("--report", required=True)
    write.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            return self_test()
        if args.command == "run":
            return command_run(args)
        if args.command == "grade":
            return command_grade(args)
        if args.command == "write-goldens":
            return command_write_goldens(args)
    except Refused as refused:
        print(f"matrix: REFUSED -- {refused}", file=sys.stderr)
        return EXIT_MISUSE
    parser.print_help()
    return EXIT_MISUSE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
