#!/usr/bin/env python3
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
"""The determinism suite (Phase 15 unit 7; PHASE-15 exit gate item 3).

"Determinism repeats on a pinned fixture agree within the documented tolerance,
and the tolerance is justified in writing."

What this does
==============

``run`` drives two opt-in JUnit classes in ``cometgui-app``
(``org.cometgui.app.matrix.Determinism{Synthetic,Pairs}``) through Maven -- the
version matrix's runner, ``scripts/matrix/run-matrix.sh --part
determinism[-synthetic]`` -- and then GRADES what they left:

``synthetic``  every Percolator the product offers here, over CometGUI's own
               committed synthetic PIN, through the product's command builder
               (no spectra; what the nightly ``determinism`` job runs)
``fixture``    the whole Comet -> Percolator run through the product's run
               port over ``COMETGUI_MATRIX_FIXTURE``, every offered pair

Each part runs the configurations ``determinism-data.json`` lists: the SAME run
``repeats`` (N >= 3) times, each into a fresh directory, then with a setting
varied that should change no result (Comet's and Percolator's thread counts),
and a CONTROL that must change results (another seed), so that a comparison
that cannot see a difference is red rather than green.

Every artefact of every run is compared with the first repeat by the rule the
data gives it -- byte-identical, identical after removing NAMED fields, or
identical up to the order of rows Percolator's sort leaves tied -- and nothing
the data does not name may differ. The data and the "Determinism" section of
``docs/developer/testing.rst`` must state the same tolerance: an artefact, a
rule or a field in one and not the other is red (``check-doc``).

Usage::

    python3 scripts/matrix/determinism.py run [--part determinism|determinism-synthetic]
                                              [--out DIR] [--fixture DIR] [--offline]
    python3 scripts/matrix/determinism.py grade --report DIR [--part ...]
    python3 scripts/matrix/determinism.py check-doc [--doc FILE] [--data FILE]
    python3 scripts/matrix/determinism.py --self-test

Exit status: 0 green; 1 red; 2 misuse or a data file refused; 3 the fixture
part requested and refused (no fixture named; never green); 7 --self-test.

Standard library only.
"""

from __future__ import annotations

import argparse
import copy
import fnmatch
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DATA_FILE = HERE / "determinism-data.json"
DOC_FILE = ROOT / "docs" / "developer" / "testing.rst"
DEFAULT_OUT = ROOT / "_build" / "determinism"
FIXTURE_ENV = "COMETGUI_MATRIX_FIXTURE"

EXIT_GREEN, EXIT_RED, EXIT_MISUSE, EXIT_REFUSED, EXIT_SELF_TEST = 0, 1, 2, 3, 7

CLASSES = {
    "synthetic": "org.cometgui.app.matrix.DeterminismSynthetic",
    "fixture": "org.cometgui.app.matrix.DeterminismPairs",
}
PARTS = {"determinism": ("synthetic", "fixture"), "determinism-synthetic": ("synthetic",)}

COMPARE_KINDS = ("bytes", "text", "tsv", "pout", "json", "jsonl-unordered", "record")
SETTINGS = ("cometThreads", "percolatorThreads", "percolatorSeed")
ROLES = ("baseline", "repeat", "variant", "control")
DOC_START = ".. determinism-tolerance-table-start"
DOC_END = ".. determinism-tolerance-table-end"
DOC_SECTION = ".. _dev-testing-determinism:"


class Refused(Exception):
    """A data file or an input this script will not grade with."""


# ===========================================================================
# The data
# ===========================================================================

def load_data(path: Path = DATA_FILE) -> dict:
    """Read and validate ``determinism-data.json``."""
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise Refused(f"{path}: {error}") from None
    for key in ("repeats", "defaultPair", "configurations", "artefacts"):
        if key not in data:
            raise Refused(f"{path}: no '{key}'")
    if not isinstance(data["repeats"], int) or data["repeats"] < 3:
        raise Refused(f"{path}: repeats must be an integer of at least 3 (gate item 3 asks for repeats)")
    labels = set()
    for entry in data["configurations"]:
        for field in ("label", "parts", "scope", "cometThreads", "percolatorSeed", "percolatorThreads", "role",
                      "why"):
            if field not in entry:
                raise Refused(f"{path}: configuration {entry.get('label', '?')} has no '{field}'")
        if entry["role"] not in ROLES:
            raise Refused(f"{path}: configuration {entry['label']}: role {entry['role']!r} not in {ROLES}")
        if entry["label"] in labels:
            raise Refused(f"{path}: configuration {entry['label']} given twice")
        labels.add(entry["label"])
    for part in ("synthetic", "fixture"):
        roles = [c["role"] for c in data["configurations"] if part in c["parts"]]
        if roles.count("baseline") != 1:
            raise Refused(f"{path}: the {part} part needs exactly one baseline configuration")
        repeats = roles.count("baseline") + roles.count("repeat")
        if repeats < data["repeats"]:
            raise Refused(f"{path}: the {part} part repeats its baseline {repeats} time(s), "
                          f"fewer than repeats = {data['repeats']}")
        if "control" not in roles:
            raise Refused(f"{path}: the {part} part has no control: a comparison never shown to see a "
                          "difference is not shown to work")
    ids = set()
    for artefact in data["artefacts"]:
        for field in ("id", "part", "writer", "files", "compare", "why", "fields", "whenVaried"):
            if field not in artefact:
                raise Refused(f"{path}: artefact {artefact.get('id', '?')} has no '{field}'")
        if artefact["compare"] not in COMPARE_KINDS:
            raise Refused(f"{path}: artefact {artefact['id']}: compare {artefact['compare']!r} not in "
                          f"{COMPARE_KINDS}")
        key = (artefact["part"], artefact["id"])
        if key in ids:
            raise Refused(f"{path}: artefact {artefact['id']} given twice in part {artefact['part']}")
        ids.add(key)
        for setting in artefact["whenVaried"]:
            if setting not in SETTINGS:
                raise Refused(f"{path}: artefact {artefact['id']}: whenVaried {setting!r} not in {SETTINGS}")
        for field in all_fields(artefact):
            for need in ("name", "why", "source"):
                if not str(field.get(need, "")).strip():
                    raise Refused(f"{path}: artefact {artefact['id']}: a field has no '{need}'")
    for entry in data["configurations"]:
        for listed in entry.get("carries", []) + entry.get("changes", []):
            if not any(a["id"] == listed for a in data["artefacts"]):
                raise Refused(f"{path}: configuration {entry['label']} names no artefact {listed}")
    return data


def all_fields(artefact: dict) -> list:
    fields = list(artefact["fields"])
    for setting, extra in artefact["whenVaried"].items():
        fields.extend(extra)
    return fields


def plan_lines(data: dict, parts) -> list:
    """The tab-separated plan the JUnit classes run: part scope label cometThreads seed percolatorThreads."""
    lines = []
    for part in parts:
        for entry in data["configurations"]:
            if part in entry["parts"]:
                threads = entry["cometThreads"] if part == "fixture" else "-"
                lines.append("\t".join([part, entry["scope"], entry["label"], str(threads),
                                        str(entry["percolatorSeed"]), str(entry["percolatorThreads"])]))
    return lines


# ===========================================================================
# Normalisation
# ===========================================================================

def expand(pattern: str, run: Path) -> str:
    return pattern.replace("{run}", re.escape(str(run)))


def apply_text_fields(text: str, fields: list, run: Path) -> str:
    for field in fields:
        if "pattern" in field and "column" not in field:
            text = re.sub(expand(field["pattern"], run), field.get("replace", ""), text, flags=re.MULTILINE)
    return text


def digest_labels(run: Path) -> dict:
    """Every MD5 and SHA-256 of every file under the run's own directory -> a label naming the file."""
    labels = {}
    for path in sorted(p for p in run.rglob("*") if p.is_file()):
        data = path.read_bytes()
        name = path.relative_to(run).as_posix()
        labels[hashlib.sha256(data).hexdigest()] = f"<sha256 of {name}>"
        labels[hashlib.md5(data).hexdigest()] = f"<md5 of {name}>"  # noqa: S324 -- provenance records MD5
    return labels


def replace_digests(text: str, labels: dict) -> str:
    return re.sub(r"\b[0-9a-f]{64}\b|\b[0-9a-f]{32}\b", lambda m: labels.get(m.group(0), m.group(0)), text)


def flatten(value, path="") -> list:
    """path = value lines; a list of strings becomes one line, its items joined by spaces."""
    if isinstance(value, dict):
        lines = []
        for key in sorted(value):
            lines.extend(flatten(value[key], f"{path}.{key}" if path else str(key)))
        return lines
    if isinstance(value, list):
        if value and all(isinstance(item, str) for item in value):
            return [f"{path} = {' '.join(value)}"]
        lines = []
        for index, item in enumerate(value):
            lines.extend(flatten(item, f"{path}[{index}]"))
        return lines or [f"{path} = []"]
    return [f"{path} = {json.dumps(value)}"]


def drop_run_file_sizes(value, run: str) -> None:
    """Removes the size of every file record whose path lies under the run's own directory."""
    if isinstance(value, dict):
        path = value.get("path")
        if isinstance(path, str) and path.startswith(run + "/"):
            for key in ("sizeBytes", "size"):
                value.pop(key, None)
        for item in value.values():
            drop_run_file_sizes(item, run)
    elif isinstance(value, list):
        for item in value:
            drop_run_file_sizes(item, run)


def join_list_table_rows(text: str) -> str:
    """Each row of an reStructuredText list-table on one line, its cells joined by ' | '."""
    out = []
    for line in text.splitlines():
        if line.startswith("     - ") and out and out[-1].lstrip().startswith("* - "):
            out[-1] += " | " + line[len("     - "):]
        else:
            out.append(line)
    return "\n".join(out) + "\n"


def tie_key(cells: list, header: list, score_columns: list) -> tuple:
    """Percolator's sort keys as a table shows them: the score columns' texts and the scan number."""
    key = [cells[header.index(name)] if name in header and header.index(name) < len(cells) else ""
           for name in score_columns]
    psm = cells[0] if cells else ""
    scan = re.search(r"_(\d+)_\d+_\d+$", psm)
    key.append(scan.group(1) if scan else psm)
    return tuple(key)


def blocks_sorted(rows: list, keys: list) -> list:
    """Rows with every block of consecutive equal keys sorted: their order within a tie no longer counts."""
    out, start = [], 0
    for index in range(1, len(rows) + 1):
        if index == len(rows) or keys[index] != keys[start]:
            out.extend(sorted(rows[start:index]))
            start = index
    return out


def pout_elements(text: str) -> tuple:
    """(head, [psm blocks], middle, [peptide blocks], tail) of a pout XML, each block one string."""
    def split(section, tag):
        pattern = re.compile(rf"^ *<{tag} p:{tag}_id=.*?^ *</{tag}>\n", re.MULTILINE | re.DOTALL)
        blocks = pattern.findall(section)
        rest = pattern.sub(f"<{tag}/>\n", section)
        return blocks, rest
    psms, text = split(text, "psm")
    peptides, text = split(text, "peptide")
    return text, psms, peptides


def pout_key(block: str) -> tuple:
    def tag(name):
        found = re.search(rf"<{name}>([^<]*)</{name}>", block)
        return found.group(1) if found else ""
    first = re.search(r'(?:p:psm_id="|<psm_id>)([^"<]*)', block)
    scan = re.search(r"_(\d+)_\d+_\d+$", first.group(1)) if first else None
    return (tag("svm_score"), tag("q_value"), tag("pep"), tag("exp_mass"), scan.group(1) if scan else "")


def sorted_psm_ids(block: str) -> str:
    match = re.search(r"(<psm_ids>\n)(.*?)(\s*</psm_ids>)", block, re.DOTALL)
    if not match:
        return block
    ids = sorted(match.group(2).splitlines())
    return block[:match.start(2)] + "\n".join(ids) + block[match.end(2):]


# ===========================================================================
# One artefact of two runs
# ===========================================================================

class Side:
    """One run as the comparison sees it."""

    def __init__(self, label: str, run: Path, record: dict, digests: dict):
        self.label = label
        self.run = run
        self.record = record
        self.digests = digests


def normalised(artefact: dict, fields: list, side: Side, path: Path, tie_order: bool):
    """The comparable form of one file: a list of lines, or bytes for 'bytes'."""
    kind = artefact["compare"]
    if kind == "bytes":
        return path.read_bytes()
    text = path.read_text(encoding="utf-8", errors="replace")
    if any(f.get("digests") for f in fields):
        text = replace_digests(text, side.digests)
    if kind == "text":
        if any(f.get("listTableRows") for f in fields):
            text = join_list_table_rows(text)
        return apply_text_fields(text, fields, side.run).splitlines()
    if kind == "tsv":
        lines = text.splitlines()
        header = lines[0].split("\t") if lines else []
        rows = []
        for line in lines[1:]:
            cells = line.split("\t")
            for field in fields:
                if "column" in field and field["column"] in header:
                    index = header.index(field["column"])
                    if index < len(cells):
                        cells[index] = re.sub(expand(field["pattern"], side.run), field.get("replace", ""),
                                              cells[index])
            rows.append(cells)
        joined = ["\t".join(cells) for cells in rows]
        if tie_order:
            tie = next(f for f in fields if f.get("tieOrder"))
            joined = blocks_sorted(joined, [tie_key(cells, header, tie["tieOrder"]) for cells in rows])
        return [lines[0] if lines else ""] + joined
    if kind == "pout":
        text = apply_text_fields(text, fields, side.run)
        if tie_order:
            rest, psms, peptides = pout_elements(text)
            psms = blocks_sorted(psms, [pout_key(b) for b in psms])
            peptides = [sorted_psm_ids(b) for b in peptides]
            peptides = blocks_sorted(peptides, [pout_key(b) for b in peptides])
            return rest.splitlines() + "".join(psms).splitlines() + "".join(peptides).splitlines()
        return text.splitlines()
    if kind in ("json", "jsonl-unordered"):
        documents = ([json.loads(text)] if kind == "json"
                     else [json.loads(line) for line in text.splitlines() if line.strip()])
        out = []
        for document in documents:
            if any(f.get("runFileSizes") for f in fields):
                drop_run_file_sizes(document, str(side.run))
            lines = flatten(document)
            lines = [line for line in lines
                     if not any("path" in f and re.fullmatch(f["path"], line.split(" = ", 1)[0]) for f in fields)]
            lines = apply_text_fields("\n".join(lines), fields, side.run).splitlines()
            out.append("\n".join(lines))
        if kind == "jsonl-unordered":
            out = sorted(out)
        return "\n".join(out).splitlines()
    raise Refused(f"compare kind {kind} is not a file kind")


def content_digest(content) -> str:
    """The SHA-256 of a comparable form: the bytes, or the lines joined by newlines."""
    data = content if isinstance(content, bytes) else ("\n".join(content) + "\n").encode("utf-8")
    return hashlib.sha256(data).hexdigest()


def first_difference(a, b, artefact: dict, name: str) -> str:
    """Words naming the artefact, the file and where (line, row and column, or path) it differs."""
    if isinstance(a, bytes):
        index = next((i for i in range(min(len(a), len(b))) if a[i] != b[i]), min(len(a), len(b)))
        return f"{name}: bytes differ first at offset {index} ({len(a)} and {len(b)} bytes)"
    for index in range(max(len(a), len(b))):
        left = a[index] if index < len(a) else "<no line>"
        right = b[index] if index < len(b) else "<no line>"
        if left != right:
            where = describe(left, right, artefact, a[0] if a else "")
            return (f"{name}: line {index + 1}{where}: {shorten(left)!r} against {shorten(right)!r}"
                    f" ({len(a)} and {len(b)} lines)")
    return f"{name}: differs"


def describe(left: str, right: str, artefact: dict, header: str) -> str:
    kind = artefact["compare"]
    if kind == "tsv":
        names = header.split("\t")
        lc, rc = left.split("\t"), right.split("\t")
        for i in range(max(len(lc), len(rc))):
            if (lc[i] if i < len(lc) else None) != (rc[i] if i < len(rc) else None):
                column = names[i] if i < len(names) else f"#{i + 1}"
                row_id = lc[0] if lc else "?"
                return f", row {shorten(row_id, 60)!r}, column {column}"
    if kind in ("json", "jsonl-unordered") and " = " in left:
        return f", field {left.split(' = ', 1)[0]}"
    if kind in ("text", "pout"):
        la = dict(re.findall(r'([\w:]+)="([^"]*)"', left))
        ra = dict(re.findall(r'([\w:]+)="([^"]*)"', right))
        differing = sorted(k for k in set(la) | set(ra) if la.get(k) != ra.get(k))
        element = re.match(r"\s*<([\w:]+)", left)
        if differing and element:
            return f", element <{element.group(1)}> attribute {', '.join(differing)}"
        if element:
            return f", element <{element.group(1)}>"
    return ""


def shorten(text: str, limit: int = 160) -> str:
    return text if len(text) <= limit else text[:limit - 3] + "..."


def record_view(artefact: dict, fields: list, side: Side, tie_order: bool) -> list:
    """The record's values a 'record' artefact compares, flattened."""
    keys = artefact["keys"]
    view = {k: side.record.get(k) for k in keys}
    if tie_order and "product" in view and isinstance(view["product"], dict):
        view = copy.deepcopy(view)
        for table in view["product"].values():
            if isinstance(table, dict) and isinstance(table.get("selected"), list):
                table["selected"] = sorted(table["selected"])
    lines = flatten(view)
    return [line for line in lines
            if not any("path" in f and re.fullmatch(f["path"], line.split(" = ", 1)[0]) for f in fields)]


def compare_artefact(artefact: dict, base: Side, other: Side, varied: set, role: str) -> dict:
    """One artefact of two runs: identical within tolerance, or the words of where it differs."""
    fields = list(artefact["fields"])
    extra = [f for s in sorted(varied) for f in artefact["whenVaried"].get(s, [])]
    tie_order = any(f.get("tieOrder") for f in extra)
    result = {"artefact": artefact["id"], "files": 0, "verdict": "identical", "detail": ""}
    if artefact["compare"] == "record":
        a = record_view(artefact, fields, base, False)
        b = record_view(artefact, fields, other, False)
        result["files"] = 1
        if a != b:
            a2 = record_view(artefact, fields + extra, base, tie_order)
            b2 = record_view(artefact, fields + extra, other, tie_order)
            result["rawDiffers"] = True
            if a2 != b2:
                result.update(verdict="DIFFERS",
                              detail=first_difference(a2, b2, {"compare": "json"}, "the run's record"))
            else:
                result.update(verdict="within tolerance", detail=f"only {', '.join(f['name'] for f in extra)}")
        return result
    left = sorted(p for p in base.run.glob(artefact["files"]) if p.is_file())
    right = sorted(p for p in other.run.glob(artefact["files"]) if p.is_file())
    names_left = [p.relative_to(base.run).as_posix() for p in left]
    names_right = [p.relative_to(other.run).as_posix() for p in right]
    if not left:
        return dict(result, verdict="MISSING", detail=f"no file matches {artefact['files']} in {base.label}")
    if names_left != names_right:
        return dict(result, verdict="DIFFERS", detail=f"files {names_left} against {names_right}")
    result["files"] = len(left)
    result["digests"] = {}
    within = []
    raw_differs = False
    for lp, rp, name in zip(left, right, names_left):
        a = normalised(artefact, fields, base, lp, False)
        b = normalised(artefact, fields, other, rp, False)
        result["digests"][name] = [content_digest(a), content_digest(b)]
        if a == b:
            continue
        raw_differs = True
        if extra:
            a = normalised(artefact, fields + extra, base, lp, tie_order)
            b = normalised(artefact, fields + extra, other, rp, tie_order)
            if a == b:
                within.append(name)
                continue
        return dict(result, verdict="DIFFERS", detail=first_difference(a, b, artefact, name), rawDiffers=True)
    if within:
        result.update(verdict="within tolerance",
                      detail=f"{', '.join(within)}: only {', '.join(f['name'] for f in extra)}")
    result["rawDiffers"] = raw_differs
    return result


# ===========================================================================
# Grading
# ===========================================================================

def read_records(report: Path) -> list:
    directory = Path(report) / "records"
    return [json.loads(p.read_text(encoding="utf-8")) for p in sorted(directory.glob("*.json"))] \
        if directory.is_dir() else []


def side_of(report: Path, record: dict) -> Side:
    work = Path(report) / record["directory"]
    run = work / record["runDirectory"] if record.get("runDirectory") else work
    return Side(record["label"], run, record, digest_labels(run) if run.is_dir() else {})


def grade(report: Path, data: dict, parts) -> dict:
    """Every run compared with its baseline, artefact by artefact."""
    report = Path(report)
    records = read_records(report)
    problems = []
    groups = {}
    for record in records:
        groups.setdefault((record["part"], record["key"]), {})[record["label"]] = record
    configurations = {c["label"]: c for c in data["configurations"]}
    default = f"comet-{data['defaultPair']['comet']}_percolator-{data['defaultPair']['percolator']}"
    comparisons = []
    for part in parts:
        keys = sorted(k for p, k in groups if p == part)
        if not keys:
            problems.append(f"the {part} part left no run record: nothing ran, which is not a pass")
            continue
        if part == "fixture" and default not in keys:
            problems.append(f"the default pair {default} has no fixture run")
        expected_labels = [c["label"] for c in data["configurations"] if part in c["parts"]]
        baseline = next(c["label"] for c in data["configurations"] if part in c["parts"] and c["role"] == "baseline")
        artefacts = [a for a in data["artefacts"] if a["part"] == part]
        for key in keys:
            runs = groups[(part, key)]
            wanted = [label for label in expected_labels
                      if configurations[label]["scope"] == "every" or key == default or part == "synthetic"]
            for label in wanted:
                if label not in runs:
                    problems.append(f"{part} {key}: configuration {label} never ran (no record)")
            for label, record in runs.items():
                if label not in configurations or part not in configurations[label]["parts"]:
                    problems.append(f"{part} {key}: a run {label} the data does not plan")
                    continue
                expected = configurations[label]
                observed = record.get("configuration", {})
                want = {"cometThreads": str(expected["cometThreads"]) if part == "fixture" else "-",
                        "percolatorSeed": expected["percolatorSeed"],
                        "percolatorThreads": expected["percolatorThreads"]}
                if observed != want:
                    problems.append(f"{part} {key} {label}: ran with {observed}, the data says {want}")
                if not record.get("succeeded"):
                    problems.append(f"{part} {key} {label}: the run did not finish: {record.get('failure')}")
            if baseline not in runs or not runs[baseline].get("succeeded"):
                problems.append(f"{part} {key}: no successful baseline {baseline}; nothing compared")
                continue
            base = side_of(report, runs[baseline])
            base_configuration = configurations[baseline]
            # every file of the baseline must belong to a named artefact
            if base.run.is_dir():
                covered = set()
                for artefact in artefacts:
                    if artefact["compare"] != "record":
                        covered.update(p.relative_to(base.run).as_posix() for p in base.run.glob(artefact["files"]))
                stray = sorted(p.relative_to(base.run).as_posix() for p in base.run.rglob("*")
                               if p.is_file() and p.relative_to(base.run).as_posix() not in covered)
                for name in stray:
                    problems.append(f"{part} {key}: {name} is written by the run and no artefact of the data "
                                    "names it: its tolerance is undocumented")
            for label in wanted:
                if label == baseline or label not in runs or not runs[label].get("succeeded"):
                    continue
                configuration = configurations[label]
                varied = {s for s in SETTINGS
                          if str(configuration[s]) != str(base_configuration[s])
                          and not (s == "cometThreads" and part == "synthetic")}
                other = side_of(report, runs[label])
                results = [compare_artefact(a, base, other, varied, configuration["role"]) for a in artefacts]
                comparison = {"part": part, "key": key, "label": label, "role": configuration["role"],
                              "varied": sorted(varied), "results": results}
                comparisons.append(comparison)
                for result in results:
                    if configuration["role"] == "control" and result["artefact"] in configuration.get("changes", []):
                        if result["verdict"] != "DIFFERS":
                            problems.append(
                                f"{part} {key} {label}: the control changed the seed and {result['artefact']} "
                                "did not change -- a comparison that cannot see a seed change is not one")
                            result["verdict"] += " (CONTROL FAILED: must differ)"
                        else:
                            result["verdict"] = "differs, as the control requires"
                        continue
                    if result["verdict"] in ("DIFFERS", "MISSING"):
                        problems.append(f"{part} {key} {label} ({configuration['role']}): artefact "
                                        f"{result['artefact']} {result['verdict']} -- {result['detail']}")
                for carried in configuration.get("carries", []):
                    hit = next((r for r in results if r["artefact"] == carried), None)
                    if hit is not None and not hit.get("rawDiffers"):
                        problems.append(f"{part} {key} {label}: the varied setting {sorted(varied)} never reached "
                                        f"{carried}: the variation was not made, so its result says nothing")
    return {"comparisons": comparisons, "problems": problems, "parts": list(parts)}


def render(report: dict) -> str:
    lines = [
        "===============================================================================",
        " CometGUI determinism -- PHASE-15 gate item 3",
        "===============================================================================",
        f" generated {report.get('generated', '?')}   parts {', '.join(report.get('parts', []))}",
        f" fixture   {report.get('fixture', {}).get('summary', '(none)')}",
        f" repeats   N = {report.get('repeats', '?')}; tolerance: scripts/matrix/determinism-data.json, "
        "docs/developer/testing.rst 'Determinism'",
        "",
    ]
    for comparison in report["comparisons"]:
        lines.append(f" {comparison['part']} {comparison['key']}: {comparison['label']} ({comparison['role']}"
                     + (f", varies {', '.join(comparison['varied'])}" if comparison["varied"] else "")
                     + ") against the baseline")
        for result in comparison["results"]:
            detail = f"  {result['detail']}" if result["detail"] else ""
            lines.append(f"     {result['verdict']:<36} {result['artefact']} ({result['files']} file(s)){detail}")
    lines.append("")
    if report["problems"]:
        lines.append(" PROBLEMS (determinism is red)")
        lines.extend(f"   - {problem}" for problem in report["problems"])
    else:
        lines.append(" PROBLEMS: none")
    lines.append("")
    lines.append(f" VERDICT  {report.get('verdict', '?')}")
    return "\n".join(lines) + "\n"


VERDICT_WORDS = {
    EXIT_GREEN: "GREEN -- every repeat and variation agrees within the documented tolerance; every control differs",
    EXIT_RED: "RED -- see the problems above",
    EXIT_REFUSED: "REFUSED -- the fixture part was requested and no fixture was named; not green",
}


def finish(report: dict, out: Path, refused: bool) -> int:
    status = EXIT_RED if report["problems"] else (EXIT_REFUSED if refused else EXIT_GREEN)
    report["exit"] = status
    report["verdict"] = VERDICT_WORDS[status]
    out.mkdir(parents=True, exist_ok=True)
    (out / "determinism-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    text = render(report)
    (out / "determinism-report.txt").write_text(text, encoding="utf-8")
    sys.stdout.write(text)
    print(f"determinism: report {out / 'determinism-report.txt'}")
    return status


# ===========================================================================
# The document and the data must state the same tolerance
# ===========================================================================

def doc_table(doc: Path) -> dict:
    """artefact id -> (compare, [field names]) as the document's tolerance table states them."""
    text = Path(doc).read_text(encoding="utf-8")
    if DOC_START not in text or DOC_END not in text:
        raise Refused(f"{doc}: no determinism tolerance table between '{DOC_START}' and '{DOC_END}'")
    table = text.split(DOC_START, 1)[1].split(DOC_END, 1)[0]
    rows = re.split(r"^   \* - ", table, flags=re.MULTILINE)[1:]
    stated = {}
    for row in rows:
        cells = re.split(r"^     - ", row, flags=re.MULTILINE)
        cells = [" ".join(c.split()) for c in cells]
        if len(cells) < 4 or cells[0].lower().startswith("artefact"):
            continue
        ident = re.findall(r"``([^`]+)``", cells[0])
        compare = re.findall(r"``([^`]+)``", cells[2])
        names = sorted(re.findall(r"\*([^*]+)\*", cells[3]))
        if not ident or not compare:
            raise Refused(f"{doc}: a tolerance row without an ``id`` or a ``compare`` kind: {cells[0][:60]}")
        part_id = ident[0]
        if part_id in stated:
            raise Refused(f"{doc}: the tolerance table states {part_id} twice")
        stated[part_id] = (compare[0], names)
    return stated


def data_table(data: dict) -> dict:
    return {f"{a['part']}:{a['id']}": (a["compare"], sorted(f["name"] for f in all_fields(a)))
            for a in data["artefacts"]}


def check_doc(data: dict, doc: Path = DOC_FILE) -> list:
    """Problems where the document's tolerance and the data's differ (none when they agree)."""
    problems = []
    stated, held = doc_table(doc), data_table(data)
    for ident in sorted(set(stated) | set(held)):
        if ident not in stated:
            problems.append(f"{ident}: the data compares it ({held[ident][0]}) and the document states no "
                            "tolerance for it")
        elif ident not in held:
            problems.append(f"{ident}: the document states a tolerance for it and the data does not compare it")
        else:
            if stated[ident][0] != held[ident][0]:
                problems.append(f"{ident}: the document says ``{stated[ident][0]}``, the data compares "
                                f"{held[ident][0]}")
            if stated[ident][1] != held[ident][1]:
                only_doc = sorted(set(stated[ident][1]) - set(held[ident][1]))
                only_data = sorted(set(held[ident][1]) - set(stated[ident][1]))
                problems.append(f"{ident}: fields removed differ -- only in the document {only_doc}, only in the "
                                f"data {only_data}")
    text = Path(doc).read_text(encoding="utf-8")
    section = text.split(DOC_SECTION, 1)[-1].split(DOC_END, 1)[0] if DOC_SECTION in text else ""
    if not re.search(rf"\bN = {data['repeats']}\b", section):
        problems.append(f"the document's Determinism section does not state N = {data['repeats']}, the data's "
                        "repeats")
    return problems


# ===========================================================================
# Commands
# ===========================================================================

def resolve_fixture(named: str, parts) -> dict:
    fixture = {"env": FIXTURE_ENV}
    if "fixture" not in parts:
        fixture["summary"] = "not used (--part determinism-synthetic)"
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
    fixture.update(directory=str(directory), summary=str(directory))
    return fixture


def command_run(args) -> int:
    parts = PARTS[args.part]
    data = load_data()
    doc_problems = check_doc(data)
    fixture = resolve_fixture(args.fixture or os.environ.get(FIXTURE_ENV, ""), parts)
    out = Path(args.out).resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    running = [p for p in parts if not (p == "fixture" and fixture.get("refused"))]
    plan = out / "plan.tsv"
    plan.write_text("# part\tscope\tlabel\tcometThreads\tseed\tpercolatorThreads\n"
                    + "\n".join(plan_lines(data, running)) + "\n", encoding="utf-8")
    pair = f"{data['defaultPair']['comet']}/{data['defaultPair']['percolator']}"
    command = ["mvn", "-B", f"-Dmaven.repo.local={ROOT / '_build' / 'm2repo'}"]
    if args.offline:
        command.append("-o")
    command += ["-pl", "cometgui-app", "-am", "test",
                "-Dtest=" + ",".join(CLASSES[p] for p in running), "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Djacoco.skip=true", "-Dcometgui.matrix=true", f"-Dcometgui.matrix.report={out}",
                f"-Dcometgui.determinism.plan={plan}", f"-Dcometgui.determinism.defaultPair={pair}"]
    if fixture.get("directory"):
        command.append(f"-Dcometgui.matrix.fixture={fixture['directory']}")
    print("determinism: " + " ".join(command), flush=True)
    started = time.monotonic()
    log = out / "maven.log"
    with open(log, "w", encoding="utf-8") as stream:
        status = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=False).returncode
    elapsed = int(time.monotonic() - started)
    print(f"determinism: Maven exited {status} after {elapsed} s; log {log}", flush=True)
    report = grade(out, data, running)
    report["problems"][:0] = [f"document and data disagree: {p}" for p in doc_problems]
    if status != 0 and not report["problems"]:
        report["problems"].append(f"Maven exited {status}: see {log}")
    if fixture.get("refused"):
        report["refused"] = fixture["refused"]
    report.update(generated=time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()), mavenExit=status,
                  mavenSeconds=elapsed, fixture=fixture, repeats=data["repeats"], parts=list(parts))
    return finish(report, out, bool(fixture.get("refused")))


def command_grade(args) -> int:
    parts = PARTS[args.part]
    data = load_data()
    out = Path(args.report).resolve()
    report = grade(out, data, parts)
    report["problems"][:0] = [f"document and data disagree: {p}" for p in check_doc(data)]
    report.update(generated=time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()), repeats=data["repeats"],
                  fixture={"summary": "(graded from an existing report)"}, parts=list(parts))
    return finish(report, Path(args.out).resolve() if args.out else out, False)


def command_check_doc(args) -> int:
    data = load_data(Path(args.data))
    problems = check_doc(data, Path(args.doc))
    if problems:
        print("determinism: the document and the data state different tolerances (red):")
        for problem in problems:
            print(f"  - {problem}")
        return EXIT_RED
    print(f"determinism: OK -- the document and the data state the same tolerance for "
          f"{len(data['artefacts'])} artefact(s), N = {data['repeats']}")
    return EXIT_GREEN


# ===========================================================================
# Self-test: the comparison and the document check, on synthetic runs (no Maven)
# ===========================================================================

def self_test() -> int:
    data = load_data()
    cases = []
    work = Path(tempfile.mkdtemp(prefix="determinism-selftest-"))

    def case(name, ok, detail=""):
        cases.append((name, bool(ok), detail))

    # -- the document check ---------------------------------------------------------------------
    case("the committed document and data agree", not check_doc(data), "; ".join(check_doc(data)))
    doc_text = DOC_FILE.read_text(encoding="utf-8")
    first = data["artefacts"][0]

    def doc_case(name, mutate, needle):
        copy_doc = work / f"doc-{len(cases)}.rst"
        copy_doc.write_text(mutate(doc_text), encoding="utf-8")
        try:
            problems = check_doc(data, copy_doc)
        except Refused as refused:
            problems = [str(refused)]
        case(name, any(needle in p for p in problems), "; ".join(problems) or "no problem reported")

    def data_case(name, mutate, needle):
        changed = copy.deepcopy(data)
        mutate(changed)
        problems = check_doc(changed)
        case(name, any(needle in p for p in problems), "; ".join(problems) or "no problem reported")

    field_name = next(f["name"] for a in data["artefacts"] for f in all_fields(a))
    data_case("a field removed in the data and still in the document -> red",
              lambda d: [a.__setitem__("fields", [f for f in a["fields"] if f["name"] != field_name])
                         or [v.__setitem__(s, [f for f in fs if f["name"] != field_name])
                             for s, fs in a["whenVaried"].items() for v in [a["whenVaried"]]]
                         for a in d["artefacts"]],
              "only in the document")
    data_case("a field added to the data and not documented -> red",
              lambda d: d["artefacts"][0]["fields"].append(
                  {"name": "an undocumented field", "pattern": "x", "why": "w", "source": "s"}),
              "only in the data ['an undocumented field']")
    data_case("an artefact's compare kind changed in the data -> red",
              lambda d: d["artefacts"][0].__setitem__("compare", "text" if first["compare"] != "text" else "bytes"),
              f"{first['part']}:{first['id']}: the document says")
    data_case("an artefact compared and not documented -> red",
              lambda d: d["artefacts"].append(dict(copy.deepcopy(first), id="an-undocumented-artefact")),
              "the document states no tolerance")
    data_case("N in the data not the document's -> red",
              lambda d: d.__setitem__("repeats", d["repeats"] + 7), "does not state N")
    doc_case("an artefact documented and not compared -> red",
             lambda t: t.replace(DOC_END, "   * - ``fixture:a-ghost``\n     - nobody\n     - ``bytes``\n"
                                          "     - none\n     - none\n\n" + DOC_END, 1),
             "the data does not compare it")

    # -- the comparison, on synthetic runs ------------------------------------------------------
    run_a, run_b = work / "a" / "run", work / "b" / "run-longer-name"
    for run in (run_a, run_b):
        (run / "out").mkdir(parents=True)
    tsv = {"compare": "tsv", "id": "t", "files": "out/psms.tsv", "whenVaried": {
        "percolatorThreads": [{"name": "tie order", "tieOrder": ["score", "q-value"], "why": "w", "source": "s"}]},
        "fields": [{"name": "PSMId run directory", "column": "PSMId", "pattern": "^{run}/", "replace": "<run>/",
                    "why": "w", "source": "s"}]}

    def table(run, rows):
        (run / "out" / "psms.tsv").write_text("PSMId\tscore\tq-value\n" + "".join(
            f"{run}/{psm}\t{score}\t{q}\n" for psm, score, q in rows), encoding="utf-8")

    def compare(spec, varied=()):
        return compare_artefact(spec, Side("a", run_a, {}, {}), Side("b", run_b, {}, {}), set(varied), "repeat")

    rows = [("f_1_2_1", "1.5", "0.01"), ("f_7_2_1", "0.5", "0.2"), ("f_7_2_2", "0.5", "0.2")]
    table(run_a, rows)
    table(run_b, rows)
    case("the same rows under different run directories -> identical", compare(tsv)["verdict"] == "identical")
    table(run_b, [rows[0], rows[2], rows[1]])
    result = compare(tsv)
    case("tied rows swapped, nothing varied -> DIFFERS naming the row and column",
         result["verdict"] == "DIFFERS" and "column PSMId" in result["detail"], result["detail"])
    result = compare(tsv, {"percolatorThreads"})
    case("tied rows swapped under a varied thread count -> within tolerance",
         result["verdict"] == "within tolerance", result["detail"])
    table(run_b, [rows[0], rows[1], ("f_7_2_2", "0.5", "0.3")])
    result = compare(tsv, {"percolatorThreads"})
    case("a q-value changed under a varied thread count -> DIFFERS naming the column",
         result["verdict"] == "DIFFERS" and "column q-value" in result["detail"], result["detail"])
    table(run_b, [rows[1], rows[0], rows[2]])
    result = compare(tsv, {"percolatorThreads"})
    case("rows with different scores reordered under a varied thread count -> DIFFERS",
         result["verdict"] == "DIFFERS", result["detail"])
    text = {"compare": "text", "id": "x", "files": "out/a.xml", "whenVaried": {}, "fields": [
        {"name": "date", "pattern": '(<root date=")[^"]*"', "replace": r'\1<date>"', "why": "w", "source": "s"}]}
    (run_a / "out" / "a.xml").write_text('<root date="1" n="2"/>\n', encoding="utf-8")
    (run_b / "out" / "a.xml").write_text('<root date="9" n="2"/>\n', encoding="utf-8")
    case("a named field differs -> identical", compare(text)["verdict"] == "identical")
    (run_b / "out" / "a.xml").write_text('<root date="9" n="3"/>\n', encoding="utf-8")
    result = compare(text)
    case("an unnamed attribute differs -> DIFFERS naming the element and attribute",
         result["verdict"] == "DIFFERS" and "element <root> attribute n" in result["detail"], result["detail"])
    raw = {"compare": "bytes", "id": "w", "files": "out/w.txt", "whenVaried": {}, "fields": []}
    (run_a / "out" / "w.txt").write_bytes(b"0.1234\n")
    (run_b / "out" / "w.txt").write_bytes(b"0.1235\n")
    result = compare(raw)
    case("one byte of a byte-identical artefact -> DIFFERS at its offset",
         result["verdict"] == "DIFFERS" and "offset 5" in result["detail"], result["detail"])
    missing = dict(raw, files="out/none.txt")
    case("an artefact the baseline did not write -> MISSING", compare(missing)["verdict"] == "MISSING")
    shutil.rmtree(work, ignore_errors=True)

    # -- the grading: control, carried setting, stray file, a run that never ran ---------------
    graded = grade_self_test(data)
    for name, ok, detail in graded:
        case(name, ok, detail)

    failures = 0
    for name, ok, detail in cases:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}" + ("" if ok else f"  -- {detail}"))
        failures += 0 if ok else 1
    if failures:
        print(f"determinism: self-test FAILED -- {failures}/{len(cases)} case(s) did not behave as required")
        return EXIT_SELF_TEST
    print(f"determinism: self-test OK -- {len(cases)}/{len(cases)} cases")
    return EXIT_GREEN


def grade_self_test(data: dict) -> list:
    """A small world graded whole: a clean report green; each defect red with its own words."""
    work = Path(tempfile.mkdtemp(prefix="determinism-grade-"))
    world = {
        "repeats": 3, "defaultPair": {"comet": "1", "percolator": "2"},
        "configurations": [
            {"label": "repeat-1", "parts": ["synthetic"], "scope": "every", "cometThreads": "4",
             "percolatorSeed": 1, "percolatorThreads": 3, "role": "baseline", "why": "w"},
            {"label": "repeat-2", "parts": ["synthetic"], "scope": "every", "cometThreads": "4",
             "percolatorSeed": 1, "percolatorThreads": 3, "role": "repeat", "why": "w"},
            {"label": "repeat-3", "parts": ["synthetic"], "scope": "every", "cometThreads": "4",
             "percolatorSeed": 1, "percolatorThreads": 3, "role": "repeat", "why": "w"},
            {"label": "threads-1", "parts": ["synthetic"], "scope": "every", "cometThreads": "4",
             "percolatorSeed": 1, "percolatorThreads": 1, "role": "variant", "why": "w", "carries": ["argv"]},
            {"label": "seed-2", "parts": ["synthetic"], "scope": "every", "cometThreads": "4",
             "percolatorSeed": 2, "percolatorThreads": 3, "role": "control", "why": "w", "changes": ["weights"]},
        ],
        "artefacts": [
            {"id": "weights", "part": "synthetic", "writer": "w", "files": "weights.txt", "compare": "bytes",
             "why": "w", "fields": [], "whenVaried": {}},
            {"id": "argv", "part": "synthetic", "writer": "w", "files": "", "compare": "record", "keys": ["argv"],
             "why": "w", "fields": [], "whenVaried": {
                 "percolatorThreads": [{"name": "threads", "path": "argv", "why": "w", "source": "s"}],
                 "percolatorSeed": [{"name": "seed", "path": "argv", "why": "w", "source": "s"}]}},
        ],
    }

    def build(defect=None):
        report = work / f"r{len(results)}"
        shutil.rmtree(report, ignore_errors=True)
        for configuration in world["configurations"]:
            label = configuration["label"]
            if defect == "missing" and label == "repeat-3":
                continue
            directory = report / "runs" / "synthetic" / "percolator-2" / label
            directory.mkdir(parents=True)
            weights = b"0.5\n" if configuration["percolatorSeed"] == 1 else b"0.7\n"
            if defect == "repeat" and label == "repeat-2":
                weights = b"0.6\n"
            if defect == "control" and label == "seed-2":
                weights = b"0.5\n"
            (directory / "weights.txt").write_bytes(weights)
            if defect == "stray" and label == "repeat-1":
                (directory / "stray.log").write_text("x", encoding="utf-8")
            threads = configuration["percolatorThreads"] if defect != "uncarried" else 3
            record = {"part": "synthetic", "key": "percolator-2", "label": label,
                      "configuration": {"cometThreads": "-", "percolatorSeed": configuration["percolatorSeed"],
                                        "percolatorThreads": configuration["percolatorThreads"]},
                      "directory": f"runs/synthetic/percolator-2/{label}", "succeeded": True, "failure": "",
                      "argv": ["--seed", str(configuration["percolatorSeed"]), "--num-threads", str(threads)]}
            (report / "records").mkdir(exist_ok=True)
            (report / "records" / f"{label}.json").write_text(json.dumps(record), encoding="utf-8")
        return grade(report, world, ("synthetic",))["problems"]

    results = []

    def expect(name, defect, needle):
        problems = build(defect)
        ok = (not problems) if needle is None else any(needle in p for p in problems)
        results.append((name, ok, "; ".join(problems) or "no problem"))

    expect("a clean world of repeats, a variant and a control -> green", None, None)
    expect("a repeat whose weights differ -> red naming the artefact and offset", "repeat",
           "repeat-2 (repeat): artefact weights DIFFERS -- weights.txt: bytes differ first at offset 2")
    expect("a control whose results did not change -> red", "control", "did not change")
    expect("a variant whose varied setting never reached the tool -> red", "uncarried", "never reached argv")
    expect("a file no artefact names -> red", "stray", "stray.log is written by the run and no artefact")
    expect("a planned run with no record -> red", "missing", "configuration repeat-3 never ran")
    shutil.rmtree(work, ignore_errors=True)
    return results


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="determinism.py", description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--self-test", action="store_true")
    sub = parser.add_subparsers(dest="command")
    run = sub.add_parser("run")
    run.add_argument("--part", choices=tuple(PARTS), default="determinism")
    run.add_argument("--out", default=str(DEFAULT_OUT))
    run.add_argument("--fixture", default="")
    run.add_argument("--offline", action="store_true")
    grade_parser = sub.add_parser("grade")
    grade_parser.add_argument("--report", required=True)
    grade_parser.add_argument("--part", choices=tuple(PARTS), default="determinism")
    grade_parser.add_argument("--out", default="")
    doc = sub.add_parser("check-doc")
    doc.add_argument("--doc", default=str(DOC_FILE))
    doc.add_argument("--data", default=str(DATA_FILE))
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            return self_test()
        if args.command == "run":
            return command_run(args)
        if args.command == "grade":
            return command_grade(args)
        if args.command == "check-doc":
            return command_check_doc(args)
    except Refused as refused:
        print(f"determinism: REFUSED -- {refused}", file=sys.stderr)
        return EXIT_MISUSE
    parser.print_help()
    return EXIT_MISUSE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
