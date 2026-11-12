#!/usr/bin/env python3
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License, version 3, as published
# by the Free Software Foundation. It is distributed WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
# PARTICULAR PURPOSE. See the GNU General Public License for details.
#
# The full licence is the LICENSE file at the root of this repository. If it
# is missing, see <https://www.gnu.org/licenses/gpl-3.0.html>.
#
# SPDX-License-Identifier: GPL-3.0-only
"""The fixture acceptance check: is this FASTA fit to be the fixture's?

Phase 16 unit 2 (``phases/PHASE-16-release.rst``, *The CI fixture*, step 5;
exit gate item 8). The fixture is the two K562 spectrum files (``D-006``) and
one FASTA. This check takes a directory holding those three files, runs the
K562 pair against the FASTA **through the product's own workflow, for every
Comet and Percolator pair the product offers on this host**, and exits 0 only
if the results behave like a real search. It is how a trimmed FASTA is
accepted, and how an over-filtered one is refused.

What it runs
============

One Maven invocation of two opt-in JUnit classes in ``cometgui-app``:
``VersionMatrixCensus`` (what the product offers here) and
``FixtureAcceptancePairs``, which runs every pair through the run port the
version matrix's pair runs use (``VersionMatrixPairs.runOnce``) in two
configurations, serially:

``matrix``
    exactly the version matrix's pair run -- the release's defaults, Comet's
    top five results for each spectrum. The goldens, the determinism suite and
    the performance suite are taken from this run.
``top1``
    the same with ``num_output_lines = 1``, then the product's own Limelight
    conversion. One result per spectrum is what the end-to-end scenario sets
    before it converts: with the top five the pinned converter refuses the run
    whatever the FASTA (Phase 14, E5).

Every run's directory stays under the report directory, and every figure is
counted here again from its files -- the PIN's labels, the q-values of
Percolator's tables with ``decimal``, the weights, Percolator's own log, the
``psm`` elements of the Limelight XML -- and must agree with what the
product's readers counted.

The criteria, each reported by name with its value and its floor, per run
=========================================================================

``workflow-succeeded``        the attempt succeeded through the run port
``percolator-trained``        three cross-validation bins trained to their last
                              iteration with weights that are not all zero, and
                              Percolator did not warn that its positive samples
                              are too few to classify
``percolator-wrote-results``  the four tables and the weights hold rows, and
                              the product reads the counts this check counts
``psms-at-1pct``              target PSMs with q <= 0.01, against a floor
``peptides-at-1pct``          target peptides with q <= 0.01, against a floor
``decoy-psms``                decoy PSMs Percolator scored, against a floor
``psms-above-1pct``           target PSMs with q > 0.01, against a floor: PSMs
                              fall on both sides of the 1% line
``limelight-valid``           (``top1``) the product converted the run and the
                              file is valid against the converter's own schema

The floors are in ``scripts/fixtures/fixture-acceptance.json``, each with the
measurement and the reasoning beside it. No golden is read: a new FASTA has a
new fixture digest and no reviewed golden yet (``R-TEST-07``).

Reported, not graded
====================

Percolator warns that "the number of negative samples read is too small to
perform a correct classification" when it is given 105 decoy PSMs or fewer
(five for each of its 21 features). With one result per spectrum these 1,338
spectra give 120 against the whole proteome and 90 to 105 against every
subset measured, up to 8,698 of its 20,652 records, so no trimmed FASTA can
clear it. The check prints the warning wherever Percolator gave it and does
not fail on it; ``decoy-psms`` holds the count to a floor instead. The
reasoning and the measurements are in the floors file, under
``reportedNotGraded``.

Usage
=====

::

    check-fixture.sh DIR [--out DIR] [--offline] [--require-committed]
    check-fixture.sh --grade REPORT --fixture DIR      # grade an existing report again
    check-fixture.sh --self-test DIR                   # every control, the real one included
    check-fixture.sh --self-test-grading               # the controls that search nothing

``DIR`` holds the two K562 mzML files and exactly one ``*.fasta``. The report
is ``_build/fixture-acceptance/`` unless ``--out`` says otherwise.
``--require-committed`` also requires the FASTA to be the committed fixture's
bytes; without it any candidate is judged and the report says which it was.

``--self-test DIR`` proves the check can go red, on two FASTA files it cuts
from the one in ``DIR`` with the committed accession list, each in a directory
of its own and each put through the whole check:

* the OVER-FILTERED FASTA, the identified proteins only, which must be RED on
  ``psms-above-1pct`` in the ``matrix`` configuration for every pair, and on
  no criterion of another name;
* the background only, the identified proteins left out, which must be RED on
  ``psms-at-1pct`` and ``peptides-at-1pct`` in both configurations.

It also runs the controls that need no search (``--self-test-grading``).

Exit status
===========

==  ===========================================================================
0   every criterion met for every pair in both configurations
1   RED: a criterion failed, or a run has no verdict
2   misuse
3   REFUSED: no fixture was named, or a file is missing; nothing was searched
    and this is not a pass
7   a self-test control did not behave as required
==  ===========================================================================

Network: the product installs every offered Comet and Percolator and the
Limelight converter from their pinned upstream URLs (GitHub), as the version
matrix does; Maven's repository unless ``--offline``. Nothing runs
concurrently: the runs are serial on any machine, Comet with the matrix's
four threads. Standard library only.
"""

from __future__ import annotations

import argparse
import copy
import decimal
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent / "matrix"))
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(HERE))

import matrix  # noqa: E402  (scripts/matrix/matrix.py: the fixture digest, computed there independently)
import fixture_fasta  # noqa: E402

FLOORS_FILE = HERE / "fixture-acceptance.json"
DEFAULT_OUT = ROOT / "_build" / "fixture-acceptance"
CLASSES = ("org.cometgui.app.matrix.VersionMatrixCensus", "org.cometgui.app.matrix.FixtureAcceptancePairs")

# The two K562 files (DECISIONS.rst, D-006): the bytes the pinned crux-toolkit commit serves, and
# the same files with the carriage returns of that transit removed.
SPECTRA = {
    "20100614_Velos1_TaGe_SA_K562_3.mzML": (
        "cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7",
        "a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da"),
    "20100614_Velos1_TaGe_SA_K562_4.mzML": (
        "d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa",
        "602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82"),
}

EXIT_GREEN, EXIT_RED, EXIT_MISUSE, EXIT_REFUSED, EXIT_SELF_TEST = 0, 1, 2, 3, 7
TABLES = ("psms.tsv", "peptides.tsv", "decoy-psms.tsv", "decoy-peptides.tsv")
FLOORED = ("psms-at-1pct", "peptides-at-1pct", "decoy-psms", "psms-above-1pct")
CRITERIA = ("workflow-succeeded", "percolator-trained", "percolator-wrote-results") + FLOORED + (
    "limelight-valid",)
SAMPLE_WARNING = re.compile(r"number of (positive|negative) samples read is too small")
NEGATIVE_WARNING = "number of negative samples read is too small"


class Refused(Exception):
    """The fixture cannot be searched as named: nothing ran, and that is not a pass."""


# ===========================================================================
# The floors
# ===========================================================================

def load_floors(path: Path = FLOORS_FILE) -> dict:
    """Reads and validates the floors: every floored criterion, in every configuration."""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    for key in ("qValue", "configurations", "floors"):
        if key not in data:
            raise ValueError(f"{path}: no '{key}'")
    if decimal.Decimal(data["qValue"]) != decimal.Decimal("0.01"):
        raise ValueError(f"{path}: the criteria are named for 1%, and qValue is {data['qValue']}")
    for name, configuration in data["configurations"].items():
        for criterion in FLOORED:
            entry = data["floors"].get(name, {}).get(criterion)
            if entry is None:
                raise ValueError(f"{path}: no floor for {criterion} in {name}")
            if not isinstance(entry.get("floor"), int) or entry["floor"] < 1:
                raise ValueError(f"{path}: {name} {criterion}: a floor is a count of at least 1, so "
                                 f"that it can fail; this one is {entry.get('floor')!r}")
            if len(str(entry.get("why", ""))) < 40:
                raise ValueError(f"{path}: {name} {criterion}: no reasoning beside the floor")
            if not entry.get("measured"):
                raise ValueError(f"{path}: {name} {criterion}: no measurement beside the floor")
        if "converts" not in configuration or "cometEdits" not in configuration:
            raise ValueError(f"{path}: configuration {name} does not say what it edits and whether it converts")
    note = data.get("reportedNotGraded", {}).get("percolator-negative-sample-warning", {})
    if len(str(note.get("why", ""))) < 40 or not note.get("measured"):
        raise ValueError(f"{path}: the negative-sample warning is reported and not graded, and the file "
                         "must say why, with the measurement")
    return data


# ===========================================================================
# The fixture
# ===========================================================================

def read_fixture(named: str) -> dict:
    """The fixture as named, or Refused, naming what is missing."""
    if not named:
        raise Refused("no fixture was named: give the directory that holds the two K562 mzML files and "
                      "one FASTA")
    directory = Path(named).resolve()
    if not directory.is_dir():
        raise Refused(f"the fixture directory {directory} does not exist")
    spectra = {}
    for name, digests in SPECTRA.items():
        file = directory / name
        if not file.is_file():
            raise Refused(f"MISSING: {name} is not in {directory}. The spectra are the K562 pair "
                          "(DECISIONS.rst, D-006), fetched by checksum and never committed")
        digest = matrix.sha256_of(file)
        if digest not in digests:
            raise Refused(f"{file} is not the K562 file of that name: SHA-256 {digest}, expected "
                          f"{digests[0]} (as fetched) or {digests[1]} (carriage returns removed)")
        spectra[name] = digest
    others = sorted(p.name for p in directory.iterdir() if p.name.endswith(".mzML") and p.name not in SPECTRA)
    if others:
        raise Refused(f"{directory} holds other spectrum files, which the run would search too: {others}")
    fastas = sorted(p for p in directory.iterdir() if p.is_file() and p.name.endswith(".fasta"))
    if not fastas:
        raise Refused(f"MISSING: {directory} holds no *.fasta")
    if len(fastas) > 1:
        raise Refused(f"{directory} holds {len(fastas)} FASTA files and the fixture has one: "
                      f"{[p.name for p in fastas]}")
    fasta = fastas[0]
    if fasta.stat().st_size == 0:
        raise Refused(f"{fasta} is empty")
    digest, files = matrix.fixture_digest(directory)
    fasta_sha = matrix.sha256_of(fasta)
    committed = fixture_fasta.FIXTURE_FASTA
    is_committed = committed.is_file() and matrix.sha256_of(committed) == fasta_sha
    records = sum(1 for line in fasta.read_bytes().split(b"\n") if line.startswith(b">"))
    return {"directory": directory, "digest": digest, "files": files, "spectra": spectra,
            "fasta": fasta, "fastaSha256": fasta_sha, "fastaBytes": fasta.stat().st_size,
            "fastaRecords": records, "isCommitted": is_committed}


# ===========================================================================
# Counting a run again, from its files
# ===========================================================================

def pin_rows(pin: Path) -> tuple:
    """Rows, target rows and decoy rows of a PIN, from its Label column."""
    rows = targets = decoys = 0
    with open(pin, encoding="latin-1") as stream:
        next(stream)
        for line in stream:
            if not line.strip():
                continue
            rows += 1
            label = line.split("\t", 2)[1]
            targets += label == "1"
            decoys += label == "-1"
    return rows, targets, decoys


def table_counts(table: Path, q_value: decimal.Decimal) -> tuple:
    """Rows of a Percolator table, and those whose q-value is at or below the cut-off."""
    lines = table.read_text(encoding="utf-8").splitlines()
    if lines[0].split("\t")[:3] != ["PSMId", "score", "q-value"]:
        raise ValueError(f"{table}: not a Percolator table")
    rows = [line for line in lines[1:] if line]
    passing = sum(1 for line in rows if decimal.Decimal(line.split("\t")[2]) <= q_value)
    return len(rows), passing


def weights_bins(weights: Path) -> tuple:
    """How many cross-validation bins the weights file holds, and how many have a non-zero weight."""
    lines = [line for line in weights.read_text(encoding="utf-8").splitlines()
             if line and not line.startswith("#")]
    bins = nonzero = 0
    for index in range(0, len(lines) - 2, 3):
        names = lines[index].split("\t")
        normalised = lines[index + 1].split("\t")
        if len(names) != len(normalised):
            raise ValueError(f"{weights}: bin {bins + 1} has {len(names)} names and {len(normalised)} weights")
        bins += 1
        nonzero += any(float(value) != 0.0 for value in normalised[:-1])
    return bins, nonzero


def percolator_log(log: Path) -> dict:
    """What Percolator said about its own training."""
    text = log.read_text(encoding="utf-8", errors="replace")
    splits = {int(n) for n in re.findall(r"Split (\d+):\s*Selected feature", text)}
    iterations = [int(n) for n in re.findall(r"Iteration (\d+):\s*Estimated \d+ PSMs", text)]
    warnings = sorted({m.group(0) for m in SAMPLE_WARNING.finditer(text)})
    return {"splitsTrained": len(splits), "lastIteration": max(iterations) if iterations else 0,
            "sampleWarnings": warnings, "exitedZero": "stage percolator ended: exit code 0" in text}


def psm_elements(limelight: Path) -> int:
    """The ``psm`` elements of a Limelight XML file, counted as text: no parser shared with the product."""
    return len(re.findall(rb"<psm[\s>]", limelight.read_bytes()))


def measure(report: Path, row: dict, q_value: decimal.Decimal) -> dict:
    """One run's figures, counted here from the files its directory holds."""
    observed = row.get("observed", {})
    measured = {"row": row, "counted": {}, "problems": []}
    directory = observed.get("runDirectory")
    if not directory:
        return measured
    run = Path(report) / directory
    counted = measured["counted"]
    try:
        pins = sorted((run / "outputs" / "comet").glob("*.pin"))
        totals = [0, 0, 0]
        for pin in pins:
            for index, value in enumerate(pin_rows(pin)):
                totals[index] += value
        counted["pin.files"], counted["pin.rows"], counted["pin.targetRows"], counted["pin.decoyRows"] = (
            len(pins), *totals)
        merged = pin_rows(run / "inputs" / "pin" / "merged.pin")
        if list(merged) != totals:
            measured["problems"].append(f"the merged PIN holds {merged} rows/targets/decoys and the "
                                        f"per-file PINs {tuple(totals)}")
        for name in TABLES:
            rows, passing = table_counts(run / "outputs" / "percolator" / name, q_value)
            counted[name + ".rows"], counted[name + ".passingDefaultCutoff"] = rows, passing
        counted["weights.splits"], counted["weights.nonZeroBins"] = weights_bins(
            run / "outputs" / "percolator" / "weights.txt")
        counted["percolator"] = percolator_log(run / "logs" / "percolator.log")
        limelight = observed.get("limelight")
        if limelight and limelight.get("file"):
            file = Path(report) / limelight["file"]
            counted["limelight.bytes"] = file.stat().st_size
            counted["limelight.psmElements"] = psm_elements(file)
    except (OSError, ValueError, StopIteration, IndexError, decimal.InvalidOperation) as unreadable:
        measured["problems"].append(f"the run's files could not be counted: {unreadable}")
    return measured


# ===========================================================================
# Judging
# ===========================================================================

def expected_runs(census: dict, raw_records: list, floors: dict) -> tuple:
    """Every (pair, configuration) the check is responsible for on this host, and derivation problems."""
    host = census["host"]
    offered = {"comet": [], "percolator": []}
    for offer in census["toolManager"].get(host, {}).get("offers", []):
        if (offer["tool"] in offered and offer["origin"] == "MANAGED"
                and offer["state"] != "UNAVAILABLE_ON_THIS_PLATFORM"):
            offered[offer["tool"]].append(offer["version"])
    problems = []
    for tool, version, platform in raw_records:
        if platform == host and tool in offered and version not in offered[tool]:
            problems.append(f"manifests/tools.json holds {tool} {version} for {host} and the product's "
                            "Tool Manager does not offer it: that build has no acceptance run")
    for tool, versions in offered.items():
        if not versions:
            problems.append(f"the product offers no {tool} on {host}: nothing can be searched")
    runs = [(f"comet-{c}_percolator-{p}", configuration, f"acceptance:{configuration}:comet-{c}:percolator-{p}:{host}")
            for c in offered["comet"] for p in offered["percolator"] for configuration in floors["configurations"]]
    return runs, problems


def judge_run(pair: str, configuration: str, measured: dict, floors: dict, fixture_digest: str) -> list:
    """The criteria of one run: ``{criterion, verdict, value, floor, detail}``, in CRITERIA order."""
    results = []

    def add(criterion, ok, value, floor, detail=""):
        results.append({"pair": pair, "configuration": configuration, "criterion": criterion,
                        "verdict": "ok" if ok else "RED", "value": value, "floor": floor, "detail": detail})

    row = measured.get("row")
    if row is None:
        add("workflow-succeeded", False, "no row", "the attempt succeeds",
            "no verdict recorded: the pair is offered here and this run was never made")
        return results
    checks = {check["name"]: check for check in row.get("checks", [])}
    observed = row.get("observed", {})
    counted = measured.get("counted", {})
    product = observed.get("product", {})

    ran = next((check for name, check in checks.items() if name.startswith("the whole workflow ran")), None)
    digest_ok = observed.get("fixtureDigest") == fixture_digest
    edits = floors["configurations"][configuration]["cometEdits"]
    edits_ok = observed.get("cometEdits") == edits
    add("workflow-succeeded", bool(ran and ran.get("passed")) and digest_ok and edits_ok,
        "succeeded" if ran and ran.get("passed") else "failed", "the attempt succeeds",
        (ran or {}).get("detail", "") if not (ran and ran.get("passed")) else
        ("" if digest_ok else f"the run's fixture digest {observed.get('fixtureDigest')} is not "
                              f"{fixture_digest}, computed here from the same directory")
        + ("" if edits_ok else f"the run set {observed.get('cometEdits')} and the floors file says this "
                               f"configuration sets {edits}"))
    if not (ran and ran.get("passed")):
        return results
    if "psms.tsv.passingDefaultCutoff" not in counted or "percolator" not in counted:
        add("percolator-wrote-results", False, "unreadable", "counted from the run's files",
            "; ".join(measured.get("problems", [])) or "the run's files were not counted")
        return results

    log = counted.get("percolator", {})
    graded_warnings = [w for w in log.get("sampleWarnings", []) if w != NEGATIVE_WARNING]
    trained = (log.get("splitsTrained") == 3 and counted.get("weights.splits") == 3
               and counted.get("weights.nonZeroBins") == 3 and log.get("lastIteration", 0) >= 1
               and bool(log.get("exitedZero")) and not graded_warnings)
    add("percolator-trained", trained,
        f"{log.get('splitsTrained')} bins trained, {counted.get('weights.nonZeroBins')} of "
        f"{counted.get('weights.splits')} weight sets non-zero, last iteration {log.get('lastIteration')}"
        + (", positive-sample warning" if graded_warnings else ""),
        "3 bins, 3 of 3 non-zero, iteration >= 1, no positive-sample warning",
        "; ".join(f"Percolator: 'Warning : the {w}'" for w in graded_warnings))
    if NEGATIVE_WARNING in log.get("sampleWarnings", []):
        results.append({"pair": pair, "configuration": configuration, "criterion": "percolator-trained",
                        "verdict": "note", "value": f"{counted.get('decoy-psms.tsv.rows')} decoy PSMs",
                        "floor": "reported, not graded",
                        "detail": f"Percolator: 'Warning : the {NEGATIVE_WARNING} to perform a correct "
                                  "classification' (it warns at 105 or fewer)"})

    wrote = checks.get("Percolator wrote its tables and its weights, and the product reads them", {})
    disagreements = [f"{name}: the product read {product.get(name)!r}, counted here {counted.get(name)!r}"
                     for name in [t + s for t in TABLES for s in (".rows", ".passingDefaultCutoff")]
                     + ["weights.splits"] if product.get(name) != counted.get(name)]
    empty = [name for name in TABLES if counted.get(name + ".rows", 0) < 1]
    if counted.get("pin.targetRows") != counted.get("psms.tsv.rows"):
        disagreements.append(f"the PINs hold {counted.get('pin.targetRows')} target rows and psms.tsv "
                             f"{counted.get('psms.tsv.rows')}")
    if counted.get("pin.decoyRows") != counted.get("decoy-psms.tsv.rows"):
        disagreements.append(f"the PINs hold {counted.get('pin.decoyRows')} decoy rows and decoy-psms.tsv "
                             f"{counted.get('decoy-psms.tsv.rows')}")
    add("percolator-wrote-results",
        bool(wrote.get("passed")) and not disagreements and not empty and not measured.get("problems"),
        f"PIN {counted.get('pin.rows')} rows ({counted.get('pin.targetRows')} target / "
        f"{counted.get('pin.decoyRows')} decoy); tables {', '.join(str(counted.get(t + '.rows')) for t in TABLES)} rows",
        "every table holds rows; the product's counts equal this check's",
        "; ".join(([wrote.get("detail", "the product could not read them")] if not wrote.get("passed") else [])
                  + disagreements + [f"{name} holds no row" for name in empty] + measured.get("problems", [])))

    values = {
        "psms-at-1pct": counted["psms.tsv.passingDefaultCutoff"],
        "peptides-at-1pct": counted["peptides.tsv.passingDefaultCutoff"],
        "decoy-psms": counted["decoy-psms.tsv.rows"],
        "psms-above-1pct": counted["psms.tsv.rows"] - counted["psms.tsv.passingDefaultCutoff"],
    }
    for criterion in FLOORED:
        floor = floors["floors"][configuration][criterion]["floor"]
        add(criterion, values[criterion] >= floor, values[criterion], floor)

    if floors["configurations"][configuration]["converts"]:
        name = next((n for n in checks if n.startswith("the product's Limelight conversion")), None)
        if not observed.get("xmlCapable"):
            results.append({"pair": pair, "configuration": configuration, "criterion": "limelight-valid",
                            "verdict": "n/a", "value": "not converted", "floor": "valid against the schema",
                            "detail": "this Percolator build's probe observed no XML_OUTPUT, so the product "
                                      "offers no conversion of its runs"})
        else:
            check = checks.get(name, {}) if name else {}
            limelight = observed.get("limelight", {})
            here = counted.get("limelight.psmElements")
            ok = (bool(check.get("passed")) and limelight.get("validAgainstConverterSchema") is True
                  and here == counted["psms.tsv.rows"] and here == limelight.get("psms") and here > 0)
            add("limelight-valid", ok,
                f"valid, {here} psm elements, {counted.get('limelight.bytes')} bytes" if ok else "not valid",
                "valid against the converter's schema; one psm per target PSM row",
                "" if ok else (check.get("detail") or f"psm elements counted here {here}, the product "
                               f"{limelight.get('psms')}, target PSM rows {counted['psms.tsv.rows']}"))
    return results


def judge(runs: list, measured_by_id: dict, floors: dict, fixture_digest: str, problems: list) -> dict:
    """Every criterion of every expected run, and the verdict."""
    results = []
    for pair, configuration, row_id in runs:
        results += judge_run(pair, configuration, measured_by_id.get(row_id, {}), floors, fixture_digest)
    problems = list(problems)
    expected_ids = {row_id for _, _, row_id in runs}
    for row_id in measured_by_id:
        if row_id not in expected_ids:
            problems.append(f"the product ran {row_id}, which this check did not derive from the census")
    if not runs:
        problems.append("no run was expected, so nothing was searched: this is not a pass")
    converting = [name for name, c in floors["configurations"].items() if c["converts"]]
    if runs and converting and not any(r["criterion"] == "limelight-valid" and r["verdict"] == "ok"
                                       for r in results):
        problems.append("no run was converted to Limelight XML and validated")
    red = [r for r in results if r["verdict"] == "RED"]
    return {"results": results, "problems": problems, "red": red,
            "exit": EXIT_RED if (red or problems) else EXIT_GREEN}


# ===========================================================================
# Running and reporting
# ===========================================================================

def run_maven(fixture: dict, out: Path, offline: bool) -> tuple:
    command = ["mvn", "-B", f"-Dmaven.repo.local={ROOT / '_build' / 'm2repo'}"]
    if offline:
        command.append("-o")
    command += ["-pl", "cometgui-app", "-am", "test", "-Dtest=" + ",".join(CLASSES),
                "-Dsurefire.failIfNoSpecifiedTests=false", "-Djacoco.skip=true", "-Dcometgui.matrix=true",
                f"-Dcometgui.matrix.report={out}", f"-Dcometgui.matrix.fixture={fixture['directory']}"]
    print("fixture-acceptance: " + " ".join(command), flush=True)
    started = time.monotonic()
    log = out / "maven.log"
    with open(log, "w", encoding="utf-8") as stream:
        status = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT).returncode
    seconds = int(time.monotonic() - started)
    print(f"fixture-acceptance: Maven exited {status} after {seconds} s; log {log}", flush=True)
    return status, seconds


def grade(report: Path, fixture: dict, floors: dict) -> dict:
    census_file = report / "census.json"
    if not census_file.is_file():
        return {"results": [], "red": [], "exit": EXIT_RED,
                "problems": [f"the product wrote no census ({census_file}): nothing was searched"]}
    census = json.loads(census_file.read_text(encoding="utf-8"))
    raw = matrix.raw_manifest_records(ROOT / "manifests" / "tools.json")
    runs, problems = expected_runs(census, raw, floors)
    q_value = decimal.Decimal(floors["qValue"])
    measured = {}
    for file in sorted((report / "rows").glob("acceptance_*.json")) if (report / "rows").is_dir() else []:
        row = json.loads(file.read_text(encoding="utf-8"))
        measured[row["id"]] = measure(report, row, q_value)
    verdict = judge(runs, measured, floors, fixture["digest"], problems)
    verdict["host"] = census["host"]
    verdict["runs"] = len(runs)
    return verdict


def render(verdict: dict, fixture: dict, seconds) -> str:
    lines = [
        "===============================================================================",
        " CometGUI fixture acceptance -- PHASE-16, The CI fixture, step 5",
        "===============================================================================",
        f" fixture  {fixture['directory']}",
        f" digest   {fixture['digest']}",
        f" FASTA    {fixture['fasta'].name}: {fixture['fastaRecords']} records, {fixture['fastaBytes']} bytes, "
        f"SHA-256 {fixture['fastaSha256']}",
        "          " + ("the committed fixture FASTA" if fixture["isCommitted"] else
                        "NOT the committed fixture FASTA: a candidate, judged as one"),
        f" host     {verdict.get('host', '?')}; {verdict.get('runs', 0)} run(s) expected"
        + (f"; Maven {seconds} s" if seconds is not None else ""),
        "",
    ]
    width = max((len(r["criterion"]) for r in verdict["results"]), default=10)
    last = None
    for result in verdict["results"]:
        key = (result["pair"], result["configuration"])
        if key != last:
            lines.append(f" {result['pair']}  [{result['configuration']}]")
            last = key
        lines.append(f"   {result['verdict']:<4} {result['criterion']:<{width}}  {result['value']}"
                     f"  (floor: {result['floor']})" + (f"  -- {result['detail']}" if result["detail"] else ""))
    lines.append("")
    if verdict["problems"]:
        lines.append(" PROBLEMS")
        lines.extend(f"   - {problem}" for problem in verdict["problems"])
        lines.append("")
    red = verdict["red"]
    counts = {v: sum(1 for r in verdict["results"] if r["verdict"] == v) for v in ("ok", "RED", "n/a", "note")}
    lines.append(f" SUMMARY  ok {counts['ok']}  RED {counts['RED']}  n/a {counts['n/a']}  problems "
                 f"{len(verdict['problems'])}  notes (reported, not graded) {counts['note']}")
    if verdict["exit"] == EXIT_GREEN:
        lines.append(" VERDICT  GREEN -- every criterion met for every pair in both configurations")
    else:
        named = sorted({f"{r['criterion']} [{r['configuration']}]" for r in red})
        lines.append(" VERDICT  RED -- " + ("; ".join(named) if named else "see PROBLEMS"))
    return "\n".join(lines) + "\n"


def check(named: str, out: Path, offline: bool, require_committed: bool, report_only: bool = False) -> tuple:
    """Runs (or re-grades) the check: ``(exit, verdict, fixture)``."""
    floors = load_floors()
    fixture = read_fixture(named)
    if require_committed and not fixture["isCommitted"]:
        raise Refused(f"{fixture['fasta']} (SHA-256 {fixture['fastaSha256']}) is not the committed fixture "
                      f"FASTA {fixture_fasta.FIXTURE_FASTA.relative_to(ROOT)}, and --require-committed was given")
    seconds = None
    if report_only and not out.is_dir():
        raise Refused(f"the report directory {out} does not exist: there is nothing to grade")
    if not report_only:
        if out.exists():
            shutil.rmtree(out)
        out.mkdir(parents=True)
        status, seconds = run_maven(fixture, out, offline)
    verdict = grade(out, fixture, floors)
    if not report_only and status != 0 and verdict["exit"] == EXIT_GREEN:
        verdict["problems"].append(f"Maven exited {status} with no failed criterion: see {out / 'maven.log'}")
        verdict["exit"] = EXIT_RED
    text = render(verdict, fixture, seconds)
    (out / "fixture-acceptance.txt").write_text(text, encoding="utf-8")
    summary = {"exit": verdict["exit"], "fixtureDigest": fixture["digest"], "fastaSha256": fixture["fastaSha256"],
               "isCommitted": fixture["isCommitted"], "mavenSeconds": seconds, "problems": verdict["problems"],
               "results": verdict["results"]}
    (out / "fixture-acceptance.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    sys.stdout.write(text)
    print(f"fixture-acceptance: report {out / 'fixture-acceptance.txt'} and .json")
    return verdict["exit"], verdict, fixture


# ===========================================================================
# Self-test
# ===========================================================================

def synthetic(floors: dict, pairs=(("9.0", "1.0"),), host="linux-x86-64") -> tuple:
    """A census, rows and measurements in which every criterion is met exactly at its floor."""
    census = {"host": host, "toolManager": {host: {"offers": [
        {"tool": "comet", "version": c, "origin": "MANAGED", "state": "NOT_INSTALLED"} for c, _ in pairs] + [
        {"tool": "percolator", "version": p, "origin": "MANAGED", "state": "NOT_INSTALLED"} for _, p in pairs]}}}
    measured = {}
    for c, p in pairs:
        for configuration, settings in floors["configurations"].items():
            f = {k: floors["floors"][configuration][k]["floor"] for k in FLOORED}
            rows = {"psms.tsv.rows": f["psms-at-1pct"] + f["psms-above-1pct"],
                    "psms.tsv.passingDefaultCutoff": f["psms-at-1pct"],
                    "peptides.tsv.rows": f["peptides-at-1pct"] + 1,
                    "peptides.tsv.passingDefaultCutoff": f["peptides-at-1pct"],
                    "decoy-psms.tsv.rows": f["decoy-psms"], "decoy-psms.tsv.passingDefaultCutoff": 0,
                    "decoy-peptides.tsv.rows": f["decoy-psms"], "decoy-peptides.tsv.passingDefaultCutoff": 0,
                    "weights.splits": 3}
            counted = dict(rows)
            counted.update({"pin.rows": rows["psms.tsv.rows"] + f["decoy-psms"],
                            "pin.targetRows": rows["psms.tsv.rows"], "pin.decoyRows": f["decoy-psms"],
                            "weights.nonZeroBins": 3,
                            "percolator": {"splitsTrained": 3, "lastIteration": 10, "sampleWarnings": [],
                                           "exitedZero": True}})
            checks = [{"name": "the whole workflow ran through the product's run port and succeeded", "passed": True},
                      {"name": "Percolator wrote its tables and its weights, and the product reads them",
                       "passed": True}]
            observed = {"fixtureDigest": "d" * 64, "runDirectory": "runs/x", "product": rows, "xmlCapable": True,
                        "cometEdits": dict(settings["cometEdits"])}
            if settings["converts"]:
                checks.append({"name": "the product's Limelight conversion wrote a file", "passed": True})
                observed["limelight"] = {"psms": rows["psms.tsv.rows"], "validAgainstConverterSchema": True}
                counted["limelight.psmElements"] = rows["psms.tsv.rows"]
                counted["limelight.bytes"] = 1000
            row_id = f"acceptance:{configuration}:comet-{c}:percolator-{p}:{host}"
            measured[row_id] = {"row": {"id": row_id, "passed": True, "checks": checks, "observed": observed},
                                "counted": counted, "problems": []}
    raw = [("comet", c, host) for c, _ in pairs] + [("percolator", p, host) for _, p in pairs]
    return census, raw, measured


def self_test_grading() -> int:
    floors = load_floors()
    cases = []

    def case(name, expect_exit, needle, mutate=None, census_mutate=None):
        census, raw, measured = synthetic(floors)
        if census_mutate:
            census_mutate(census, raw)
        if mutate:
            mutate(measured)
        runs, problems = expected_runs(census, raw, floors)
        verdict = judge(runs, measured, floors, "d" * 64, problems)
        fixture = {"directory": Path("/synthetic"), "digest": "d" * 64, "fasta": Path("synthetic.fasta"),
                   "fastaRecords": 1, "fastaBytes": 1, "fastaSha256": "0" * 64, "isCommitted": False}
        verdict.update(host=census["host"], runs=len(runs))
        text = render(verdict, fixture, None)
        ok = verdict["exit"] == expect_exit and (needle is None or needle in text)
        cases.append((name, ok, verdict["exit"], expect_exit, needle))

    def each(configuration):
        def find(measured):
            return next(m for row_id, m in measured.items() if f":{configuration}:" in row_id)
        return find

    case("every criterion exactly at its floor -> green", EXIT_GREEN, "VERDICT  GREEN")
    for configuration in floors["configurations"]:
        find = each(configuration)
        for criterion, key in (("psms-at-1pct", "psms.tsv.passingDefaultCutoff"),
                               ("peptides-at-1pct", "peptides.tsv.passingDefaultCutoff"),
                               ("decoy-psms", "decoy-psms.tsv.rows")):
            def below(measured, key=key, find=find):
                run = find(measured)
                run["counted"][key] -= 1
                run["row"]["observed"]["product"][key] -= 1
                if key == "decoy-psms.tsv.rows":
                    run["counted"]["pin.decoyRows"] -= 1
                    run["counted"]["pin.rows"] -= 1
            case(f"{configuration}: {criterion} one below its floor -> red, by name", EXIT_RED,
                 f"VERDICT  RED -- {criterion} [{configuration}]", below)

        def no_failing(measured, find=find):
            run = find(measured)
            for holder in (run["counted"], run["row"]["observed"]["product"]):
                holder["psms.tsv.rows"] = holder["psms.tsv.passingDefaultCutoff"]
            run["counted"]["pin.targetRows"] = run["counted"]["psms.tsv.rows"]
            if "limelight.psmElements" in run["counted"]:
                run["counted"]["limelight.psmElements"] = run["counted"]["psms.tsv.rows"]
                run["row"]["observed"]["limelight"]["psms"] = run["counted"]["psms.tsv.rows"]
        case(f"{configuration}: every PSM passes 1% -> red on psms-above-1pct", EXIT_RED,
             f"VERDICT  RED -- psms-above-1pct [{configuration}]", no_failing)

        def warned(measured, find=find):
            find(measured)["counted"]["percolator"]["sampleWarnings"] = [NEGATIVE_WARNING]
        case(f"{configuration}: Percolator warns its negatives are too few -> reported by name, not graded",
             EXIT_GREEN, "note percolator-trained", warned)

        def few_positives(measured, find=find):
            find(measured)["counted"]["percolator"]["sampleWarnings"] = [
                "number of positive samples read is too small"]
        case(f"{configuration}: Percolator warns its positives are too few -> red on percolator-trained",
             EXIT_RED, f"VERDICT  RED -- percolator-trained [{configuration}]", few_positives)

        def other_edits(measured, find=find):
            find(measured)["row"]["observed"]["cometEdits"] = {"num_output_lines": "3"}
        case(f"{configuration}: a run configured otherwise than the floors file says -> red", EXIT_RED,
             f"workflow-succeeded [{configuration}]", other_edits)

        def two_bins(measured, find=find):
            find(measured)["counted"]["weights.nonZeroBins"] = 2
        case(f"{configuration}: a weight set that is all zero -> red on percolator-trained", EXIT_RED,
             f"percolator-trained [{configuration}]", two_bins)

        def disagree(measured, find=find):
            find(measured)["row"]["observed"]["product"]["psms.tsv.passingDefaultCutoff"] += 1
        case(f"{configuration}: the product's count differs from this check's -> red", EXIT_RED,
             f"percolator-wrote-results [{configuration}]", disagree)

        def failed(measured, find=find):
            run = find(measured)
            run["row"]["checks"][0].update(passed=False, detail="AssertionFailedError: the attempt: FAILED")
        case(f"{configuration}: the attempt failed -> red on workflow-succeeded", EXIT_RED,
             f"workflow-succeeded [{configuration}]", failed)

        def missing(measured, configuration=configuration):
            for row_id in [r for r in measured if f":{configuration}:" in r]:
                del measured[row_id]
        case(f"{configuration}: an expected run with no row -> red, no verdict recorded", EXIT_RED,
             "no verdict recorded", missing)

        def other_digest(measured, find=find):
            find(measured)["row"]["observed"]["fixtureDigest"] = "e" * 64
        case(f"{configuration}: a run over another fixture digest -> red", EXIT_RED, "is not " + "d" * 64,
             other_digest)

    converting = next(name for name, c in floors["configurations"].items() if c["converts"])

    def invalid(measured):
        run = each(converting)(measured)
        run["row"]["checks"][2].update(passed=False, detail="AssertionError: not valid against the schema")
        run["row"]["observed"]["limelight"] = {}
    case("the conversion did not validate -> red on limelight-valid", EXIT_RED,
         f"VERDICT  RED -- limelight-valid [{converting}]", invalid)

    def psm_count(measured):
        each(converting)(measured)["counted"]["limelight.psmElements"] -= 1
    case("the Limelight XML holds one psm too few -> red on limelight-valid", EXIT_RED,
         f"limelight-valid [{converting}]", psm_count)

    def no_xml(measured):
        for run in measured.values():
            run["row"]["observed"]["xmlCapable"] = False
    case("no Percolator here writes XML -> the conversion n/a for each, and red: none validated", EXIT_RED,
         "no run was converted to Limelight XML and validated", no_xml)

    case("no run at all -> red, never green", EXIT_RED, "no verdict recorded",
         lambda measured: measured.clear())

    def no_offers(census, raw):
        census["toolManager"][census["host"]]["offers"] = []
    case("the product offers nothing here -> red: nothing was searched", EXIT_RED,
         "nothing was searched", None, no_offers)

    def unoffered(census, raw):
        raw.append(("comet", "9.1", census["host"]))
    case("a manifest build for this host the product does not offer -> red, naming it", EXIT_RED,
         "comet 9.1", None, unoffered)

    def extra(measured):
        row_id = "acceptance:matrix:comet-8.0:percolator-1.0:linux-x86-64"
        measured[row_id] = copy.deepcopy(next(iter(measured.values())))
    case("a run the check did not derive -> red", EXIT_RED, "did not derive", extra)

    failures = 0
    for name, ok, status, expected, needle in cases:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}  (exit {status}, required {expected}"
              + (f", words {needle!r}" if needle else "") + ")")
        failures += 0 if ok else 1
    refusals = self_test_refusals()
    failures += refusals
    if failures:
        print(f"fixture-acceptance: self-test FAILED -- {failures} control(s) did not behave as required")
        return EXIT_SELF_TEST
    print(f"fixture-acceptance: grading self-test OK -- {len(cases)} graded cases and the refusals")
    return EXIT_GREEN


def self_test_refusals() -> int:
    """No fixture, a missing directory, a missing file: each refused by name, having searched nothing."""
    work = ROOT / "_build" / "fixture-acceptance-selftest" / "refusals"
    if work.exists():
        shutil.rmtree(work)
    empty = work / "empty"
    empty.mkdir(parents=True)
    no_fasta = work / "spectra-named-wrongly"
    no_fasta.mkdir()
    (no_fasta / "20100614_Velos1_TaGe_SA_K562_3.mzML").write_bytes(b"not the K562 file\n")
    failures = 0
    for name, named, needle in (
            ("no fixture named", "", "no fixture was named"),
            ("a directory that does not exist", str(work / "absent"), "does not exist"),
            ("a directory without the spectra", str(empty), "MISSING: 20100614_Velos1_TaGe_SA_K562_3.mzML"),
            ("a spectrum file that is not the K562 file", str(no_fasta), "is not the K562 file of that name")):
        try:
            read_fixture(named)
            ok, said = False, "(accepted)"
        except Refused as refused:
            ok, said = needle in str(refused), str(refused)
        print(f"  {'ok  ' if ok else 'FAIL'}  {name} -> REFUSED, exit {EXIT_REFUSED}, words {needle!r}"
              + ("" if ok else f"; it said: {said}"))
        failures += 0 if ok else 1
    return failures


def self_test(named: str, out: Path, offline: bool) -> int:
    """Every control: the ones that search nothing, then two real FASTA files that must be red."""
    status = self_test_grading()
    if status != EXIT_GREEN:
        return status
    floors = load_floors()
    fixture = read_fixture(named)
    kept, _ = fixture_fasta.read_list(fixture_fasta.FIXTURE_ACCESSIONS)
    records = fixture_fasta.read_records(fixture["fasta"])
    if out.exists():
        shutil.rmtree(out)
    configurations = list(floors["configurations"])
    controls = (
        ("over-filtered", "identified-proteins-only.fasta", fixture_fasta.IDENTIFIED,
         "the OVER-FILTERED FASTA: the identified proteins only",
         {("psms-above-1pct", "matrix")}, {"psms-above-1pct"}, True),
        ("background-only", "background-only.fasta", fixture_fasta.BACKGROUND,
         "the background only: the identified proteins left out",
         {(criterion, configuration) for criterion in ("psms-at-1pct", "peptides-at-1pct")
          for configuration in configurations},
         {"psms-at-1pct", "peptides-at-1pct", "percolator-trained", "workflow-succeeded", "limelight-valid"},
         False),
    )
    failures = []
    for name, file_name, role, words, required, allowed, must_search in controls:
        wanted = {accession for accession, kept_as in kept.items() if kept_as == role}
        data = fixture_fasta.subset(records, wanted)
        control = out / name / "fixture"
        control.mkdir(parents=True)
        for spectrum in SPECTRA:
            shutil.copyfile(fixture["directory"] / spectrum, control / spectrum)
        (control / file_name).write_bytes(data)
        print(f"fixture-acceptance: control '{name}' -- {words}: {len(wanted)} records, {len(data)} bytes, cut "
              f"from {fixture['fasta'].name} with the committed accession list, in {control}", flush=True)
        try:
            exit_status, verdict, _ = check(str(control), out / name / "report", offline, False)
        except Refused as refused:
            failures.append(f"{name}: the control was refused: {refused}")
            continue
        red = {(r["criterion"], r["configuration"]) for r in verdict["red"]}
        pairs = verdict.get("runs", 0) // len(configurations)
        searched = [r for r in verdict["results"]
                    if r["criterion"] == "workflow-succeeded" and r["verdict"] == "ok"]
        if exit_status != EXIT_RED:
            failures.append(f"{name}: the check exited {exit_status}, not {EXIT_RED}")
        if not required <= red:
            failures.append(f"{name}: it must be red on {sorted(required)} and was red on {sorted(red)}")
        unrelated = sorted(pair for pair in red if pair[0] not in allowed)
        if unrelated:
            failures.append(f"{name}: it is red on {unrelated}, which is not the reason this control exists")
        if verdict["problems"]:
            failures.append(f"{name}: it reported problems, so the red is not the criteria's alone: "
                            f"{verdict['problems']}")
        if pairs < 1 or not searched or (must_search and len(searched) != verdict.get("runs")):
            failures.append(f"{name}: {len(searched)} of {verdict.get('runs')} runs searched; a control must be "
                            "red because of what a search found, not because nothing ran")
        for criterion, configuration in sorted(required):
            hit = [r for r in verdict["red"] if (r["criterion"], r["configuration"]) == (criterion, configuration)]
            if must_search and len(hit) != pairs:
                failures.append(f"{name}: {criterion} [{configuration}] is red for {len(hit)} of {pairs} pairs, "
                                "not for every pair")
        print(f"fixture-acceptance: control '{name}' -- exit {exit_status}; {len(searched)} of "
              f"{verdict.get('runs')} runs searched; red on {sorted(red)}", flush=True)
    if failures:
        for failure in failures:
            print(f"fixture-acceptance: self-test FAILED -- {failure}")
        return EXIT_SELF_TEST
    print("fixture-acceptance: self-test OK -- the over-filtered FASTA (identified proteins only) was searched by "
          "every run and is RED on psms-above-1pct [matrix] for every pair, and on no criterion of another "
          "name; the background-only FASTA is RED on psms-at-1pct and peptides-at-1pct in both configurations")
    return EXIT_GREEN


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="check-fixture.sh", description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("fixture", nargs="?", default="")
    parser.add_argument("--out", default="")
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--require-committed", action="store_true")
    parser.add_argument("--grade", default="", metavar="REPORT")
    parser.add_argument("--fixture", dest="fixture_option", default="")
    parser.add_argument("--self-test", dest="self_test", default=None, metavar="DIR")
    parser.add_argument("--self-test-grading", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.self_test_grading:
            return self_test_grading()
        if args.self_test is not None:
            out = Path(args.out).resolve() if args.out else ROOT / "_build" / "fixture-acceptance-selftest" / "control"
            return self_test(args.self_test, out, args.offline)
        if args.grade:
            exit_status, _, _ = check(args.fixture_option or args.fixture, Path(args.grade).resolve(), args.offline,
                                      args.require_committed, report_only=True)
            return exit_status
        out = Path(args.out).resolve() if args.out else DEFAULT_OUT
        exit_status, _, _ = check(args.fixture or args.fixture_option, out, args.offline, args.require_committed)
        return exit_status
    except Refused as refused:
        print(f"fixture-acceptance: REFUSED -- {refused}. Nothing was searched; this is not a pass.",
              file=sys.stderr)
        return EXIT_REFUSED
    except (ValueError, matrix.Refused) as broken:
        print(f"fixture-acceptance: {broken}", file=sys.stderr)
        return EXIT_MISUSE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
