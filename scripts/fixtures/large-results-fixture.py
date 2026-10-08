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
"""Write Phase 10's large, synthetic, Percolator-shaped results fixture.

Phase 10 unit 1, design decision P10-2 in ``handoffs/PHASE-10-worklog.rst``.
``R-RES-03``: "A performance fixture large enough to cross the threshold shall
exist before the results UI is built, not after." This is that fixture.

What it writes, into ``--out`` (default ``scratch/phase10/large/``, which is
gitignored -- the generated data is never committed)::

    psms.tsv        a Percolator target PSM table, 1 000 000 rows by default
    peptides.tsv    a Percolator target peptide table, 400 000 rows by default
    manifest.json   the arguments, each file's SHA-256, size and row count, and
                    the EXPECTED COUNTS at eight cutoffs, computed here from the
                    values this generator assigned, with Python ``decimal``

Both tables have Percolator's header
``PSMId score q-value posterior_error_prob peptide proteinIds``, rows ordered
by descending score as Percolator orders them, and ``proteinIds`` continuing to
the end of the row (one tab-separated field per protein; a share of rows carry
two to four). Every ``PSMId`` has Comet's ``SpecId`` shape, ``<-N base>_<scan>_
<charge>_<rank>``, where the base is an absolute-looking path to one of four
source files -- one with digits and underscores of its own, one with a space --
so multi-file source columns are exercised. Protein accessions (``fx|FX...``)
and peptides are synthetic; nothing here is derived from UniProt or any real
dataset (``D-006``).

Q-values are written as Percolator writes them: at most six significant digits,
C ``%g`` form, so values below 1e-4 take the exponent form (``9.27998e-07``).
Because every known q-value and every cutoff is a short decimal, a comparison
of ``double``s and an exact decimal comparison agree on every row; the
self-check proves it. The exact boundary values ``0``, ``0.005``, ``0.01`` and
``1`` are present in known numbers, with neighbours one unit in the sixth
significant digit either side of ``0.005`` and ``0.01``; and every kind of
unknown q-value is present in known numbers (``R-RES-02``): empty, ``NaN``,
``nan``, ``-nan``, ``inf``, ``-inf``, ``Infinity``, a comma decimal ``0,01``,
and the out-of-range ``1.5`` and ``-0.1``. The manifest records, per kind, the
category the specification's policy puts it in -- ``MISSING`` (empty),
``UNPARSABLE`` (not a decimal number) or ``OUT_OF_RANGE`` (a decimal outside
[0, 1]) -- as decided by :func:`policy_category` here, not by any Java.

Counts are inclusive at the boundary: a row passes a cutoff when its known
q-value ``<= cutoff``; a known q-value above it fails; an unknown one is in
neither and counted on its own.

Determinism: the only source of randomness is ``random.Random(seed).random()``,
whose sequence for an integer seed Python guarantees to be reproducible across
versions; every integer choice, sample and shuffle is derived from it here
rather than from ``randrange``/``shuffle``/``sample``. Numbers are formatted
with ``%`` (never locale-aware), files are written as ASCII with ``\\n`` line
endings, and the manifest holds no time or host detail, so the same arguments
give byte-identical files on any host.

Usage, from the repository root::

    python3 scripts/fixtures/large-results-fixture.py
    python3 scripts/fixtures/large-results-fixture.py --self-check

``--self-check`` writes nothing into ``--out``. It re-reads the written files
with a second, deliberately separate pass (split on tab, ``decimal`` compare),
requires every number it counts to equal the manifest's, then generates again
into a sibling directory and requires the bytes to be identical. It prints what
it checked, with the numbers.

Standard library only. Exit status: 0 written / every check agreed; 1 a
self-check disagreement (each one named); 2 misuse.
"""

from __future__ import annotations

import argparse
import bisect
import hashlib
import json
import re
import shutil
import sys
import time
from decimal import Decimal
from pathlib import Path
from random import Random

GENERATOR = "scripts/fixtures/large-results-fixture.py"
GENERATOR_VERSION = "1.0.0"
DEFAULT_SEED = 20261008
DEFAULT_PSM_ROWS = 1_000_000
DEFAULT_PEPTIDE_ROWS = 400_000
DEFAULT_OUT = "scratch/phase10/large"

HEADER = ["PSMId", "score", "q-value", "posterior_error_prob", "peptide", "proteinIds"]

CUTOFFS = ["0", "0.001", "0.005", "0.01", "0.05", "0.1", "0.5", "1"]

SOURCES = [
    "/fixture/runs/run-01/outputs/comet/sample_A",
    "/fixture/runs/run-01/outputs/comet/sample_B",
    "/fixture/runs/run-01/outputs/comet/2026_10_08_sample_C_2_1",
    "/fixture/runs/run-01/outputs/comet/sample D",
]

# Every unknown kind: (label, text as written). The counts per table are fixed
# and distinct (primes), so a swapped kind or a swapped table shows.
UNKNOWN_KINDS = [
    ("empty", ""),
    ("NaN", "NaN"),
    ("nan", "nan"),
    ("-nan", "-nan"),
    ("inf", "inf"),
    ("-inf", "-inf"),
    ("Infinity", "Infinity"),
    ("comma-decimal", "0,01"),
    ("above-one", "1.5"),
    ("negative", "-0.1"),
]
UNKNOWN_ROWS = {
    "psms": [211, 113, 127, 131, 137, 139, 149, 151, 157, 163],
    "peptides": [41, 43, 47, 53, 59, 61, 67, 71, 73, 79],
}

# Known q-values placed deliberately, as text exactly as written.
DELIBERATE_KNOWN = {
    "psms": [
        ("0", 5003),
        ("0.005", 307),
        ("0.01", 401),
        ("1", 1009),
        ("0.00499999", 53),
        ("0.00500001", 59),
        ("0.00999999", 61),
        ("0.0100001", 67),
    ],
    "peptides": [
        ("0", 2003),
        ("0.005", 103),
        ("0.01", 211),
        ("1", 307),
        ("0.00499999", 17),
        ("0.00500001", 19),
        ("0.00999999", 23),
        ("0.0100001", 29),
    ],
}

AMINO_ACIDS = "ACDEFGHIKLMNPQRSTVWY"
PROTEIN_POOL = 20_000

# A decimal number in Locale.ROOT form -- the specification's notion of a
# parsable q-value. Anything else is UNPARSABLE.
DECIMAL = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?")


def policy_category(text: str) -> str:
    """The specification's R-RES-02 category of a q-value field, decided here."""
    if text == "":
        return "MISSING"
    if not DECIMAL.fullmatch(text):
        return "UNPARSABLE"
    value = Decimal(text)
    if value < 0 or value > 1:
        return "OUT_OF_RANGE"
    return "KNOWN"


def g6(value: float) -> str:
    """C %g with six significant digits: how Percolator writes a double."""
    return "%.6g" % value


class Draw:
    """Every random choice, from Random.random() alone (reproducible by contract)."""

    def __init__(self, seed: int) -> None:
        self._random = Random(seed).random

    def unit(self) -> float:
        return self._random()

    def below(self, n: int) -> int:
        return int(self._random() * n)

    def permutation(self, n: int) -> list[int]:
        values = list(range(n))
        rnd = self._random
        for i in range(n - 1, 0, -1):
            j = int(rnd() * (i + 1))
            values[i], values[j] = values[j], values[i]
        return values


def make_peptides(draw: Draw, count: int) -> list[tuple[str, str]]:
    """Unique synthetic peptides, flanked as Percolator writes them, each with its proteins."""
    seen: set[str] = set()
    pool: list[tuple[str, str]] = []
    while len(pool) < count:
        length = 7 + draw.below(19)
        core = "".join(AMINO_ACIDS[draw.below(20)] for _ in range(length))
        if "M" in core and draw.unit() < 0.3:
            core = core.replace("M", "M[15.9949]", 1)
        if core in seen:
            continue
        seen.add(core)
        before = "-" if draw.unit() < 0.02 else ("K" if draw.unit() < 0.5 else "R")
        after = "-" if draw.unit() < 0.02 else AMINO_ACIDS[draw.below(20)]
        r = draw.unit()
        proteins = 1 if r < 0.82 else 2 if r < 0.92 else 3 if r < 0.97 else 4
        ids = []
        for _ in range(proteins):
            n = 1 + draw.below(PROTEIN_POOL)
            ids.append("fx|FX%07d|SYN%07d_FIXTURE" % (n, n))
        pool.append((before + "." + core + "." + after, "\t".join(ids)))
    return pool


def assign_q_values(draw: Draw, table: str, rows: int) -> tuple[list[str], list[Decimal], list[str]]:
    """The q-value text of every row, in file order, and the sorted known values.

    Known q-values ascend with file position, as Percolator's do; unknown ones
    are placed at drawn positions among them.
    """
    unknown_texts: list[str] = []
    for (label, text), n in zip(UNKNOWN_KINDS, UNKNOWN_ROWS[table]):
        unknown_texts.extend([text] * n)
    deliberate: list[str] = []
    for text, n in DELIBERATE_KNOWN[table]:
        deliberate.extend([text] * n)
    drawn = rows - len(unknown_texts) - len(deliberate)
    if drawn < 1:
        raise SystemExit(
            "%s: %d rows cannot hold the %d deliberate and %d unknown rows"
            % (table, rows, len(deliberate), len(unknown_texts))
        )
    known = deliberate + [g6(draw.unit() ** 3) for _ in range(drawn)]
    keyed = sorted((Decimal(text), text) for text in known)
    known_sorted_texts = [text for _, text in keyed]
    known_sorted_values = [value for value, _ in keyed]

    # Unknown rows go to distinct drawn positions; known rows fill the rest in order.
    positions: set[int] = set()
    unknown_at: list[int] = []
    while len(unknown_at) < len(unknown_texts):
        p = draw.below(rows)
        if p not in positions:
            positions.add(p)
            unknown_at.append(p)
    q_texts: list[str | None] = [None] * rows
    for p, text in zip(unknown_at, unknown_texts):
        q_texts[p] = text
    it = iter(known_sorted_texts)
    for i in range(rows):
        if q_texts[i] is None:
            q_texts[i] = next(it)
    return q_texts, known_sorted_values, unknown_texts  # type: ignore[return-value]


def write_table(
    draw: Draw, table: str, rows: int, path: Path, peptides: list[tuple[str, str]], one_per_peptide: bool
) -> dict:
    q_texts, known_values, unknown_texts = assign_q_values(draw, table, rows)
    keys = draw.permutation(rows)
    peptide_order = draw.permutation(len(peptides)) if one_per_peptide else None
    by_source = [0] * len(SOURCES)
    protein_rows = {1: 0, 2: 0, 3: 0, 4: 0}
    lines = ["\t".join(HEADER)]
    append = lines.append
    for i in range(rows):
        key = keys[i]
        source = key % len(SOURCES)
        by_source[source] += 1
        scan = key // len(SOURCES) + 1
        charge = 2 + (key // 7) % 3
        rank = 2 if draw.unit() < 0.05 else 1
        score = g6(6.0 - 9.0 * ((i + 0.5) / rows) ** 0.7)
        q = q_texts[i]
        if policy_category(q) == "KNOWN":
            pep = g6(min(1.0, float(Decimal(q)) * 4.0 + draw.unit() * 1e-6))
        else:
            pep = g6(draw.unit())
        if one_per_peptide:
            peptide, proteins = peptides[peptide_order[i]]  # type: ignore[index]
        else:
            peptide, proteins = peptides[draw.below(len(peptides))]
        protein_rows[proteins.count("\t") + 1] += 1
        append(
            "%s_%d_%d_%d\t%s\t%s\t%s\t%s\t%s"
            % (SOURCES[source], scan, charge, rank, score, q, pep, peptide, proteins)
        )
    data = ("\n".join(lines) + "\n").encode("ascii")
    path.write_bytes(data)

    counts = []
    unknown = len(unknown_texts)
    for cutoff in CUTOFFS:
        passing = bisect.bisect_right(known_values, Decimal(cutoff))
        counts.append(
            {
                "cutoff": cutoff,
                "total": rows,
                "passing": passing,
                "failing": len(known_values) - passing,
                "unknown": unknown,
            }
        )
    kinds = []
    by_category = {"MISSING": 0, "UNPARSABLE": 0, "OUT_OF_RANGE": 0}
    for (label, text), n in zip(UNKNOWN_KINDS, UNKNOWN_ROWS[table]):
        category = policy_category(text)
        if category == "KNOWN":
            raise SystemExit("internal: unknown kind %r classifies as KNOWN" % text)
        by_category[category] += n
        kinds.append({"label": label, "text": text, "category": category, "rows": n})
    exact = {}
    for value in ("0", "0.005", "0.01", "1"):
        d = Decimal(value)
        exact[value] = bisect.bisect_right(known_values, d) - bisect.bisect_left(known_values, d)
    return {
        "file": path.name,
        "sha256": hashlib.sha256(data).hexdigest(),
        "bytes": len(data),
        "rows": rows,
        "header": HEADER,
        "known_rows": len(known_values),
        "exponent_form_rows": sum(1 for t in q_texts if "e" in t and policy_category(t) == "KNOWN"),
        "exact_value_rows": exact,
        "rows_by_source": {SOURCES[s]: by_source[s] for s in range(len(SOURCES))},
        "rows_by_protein_count": {str(k): v for k, v in protein_rows.items()},
        "unknown_by_kind": kinds,
        "unknown_by_category": by_category,
        "counts": counts,
    }


def generate(out: Path, psm_rows: int, peptide_rows: int, seed: int) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    draw = Draw(seed)
    peptides = make_peptides(draw, peptide_rows)
    psms = write_table(draw, "psms", psm_rows, out / "psms.tsv", peptides, one_per_peptide=False)
    peps = write_table(draw, "peptides", peptide_rows, out / "peptides.tsv", peptides, one_per_peptide=True)
    manifest = {
        "generator": GENERATOR,
        "generator_version": GENERATOR_VERSION,
        "synthetic": True,
        "arguments": {"psm_rows": psm_rows, "peptide_rows": peptide_rows, "seed": seed},
        "cutoffs": CUTOFFS,
        "comparison": "a known q-value passes when q <= cutoff (inclusive), compared with Python decimal",
        "sources": SOURCES,
        "tables": {"psms": psms, "peptides": peps},
    }
    text = json.dumps(manifest, indent=2, ensure_ascii=True) + "\n"
    (out / "manifest.json").write_bytes(text.encode("ascii"))
    return manifest


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


# ---------------------------------------------------------------- self-check
# A second pass that shares nothing with the generation code above but the
# file names: it reads what is on disk, line by line, and counts again.

_NUMBER = re.compile(r"^[+-]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][+-]?[0-9]+)?$")


def _recount(path: Path, cutoffs: list[str]) -> dict:
    limits = [Decimal(c) for c in cutoffs]
    floats = [float(c) for c in cutoffs]
    passing = [0] * len(limits)
    failing = [0] * len(limits)
    disagreements = 0
    total = 0
    unknown_by_text: dict[str, int] = {}
    category: dict[str, str] = {}
    exponent = 0
    exact = {"0": 0, "0.005": 0, "0.01": 0, "1": 0}
    exact_d = {k: Decimal(k) for k in exact}
    sources: dict[str, int] = {}
    protein_rows: dict[str, int] = {}
    bad_rows = 0
    with path.open("r", encoding="ascii", newline="") as stream:
        header = stream.readline().rstrip("\n").split("\t")
        for line in stream:
            if not line.endswith("\n"):
                bad_rows += 1
            fields = line[:-1].split("\t")
            total += 1
            if len(fields) < 6:
                bad_rows += 1
                continue
            base = fields[0].rsplit("_", 3)[0]
            sources[base] = sources.get(base, 0) + 1
            n = str(len(fields) - 5)
            protein_rows[n] = protein_rows.get(n, 0) + 1
            q = fields[2]
            why = None
            if q == "":
                why = "MISSING"
            elif not _NUMBER.match(q):
                why = "UNPARSABLE"
            else:
                d = Decimal(q)
                if d < 0 or d > 1:
                    why = "OUT_OF_RANGE"
            if why is not None:
                unknown_by_text[q] = unknown_by_text.get(q, 0) + 1
                category[q] = why
                continue
            if "e" in q or "E" in q:
                exponent += 1
            for k, v in exact_d.items():
                if d == v:
                    exact[k] += 1
            f = float(q)
            for i, limit in enumerate(limits):
                if d <= limit:
                    passing[i] += 1
                else:
                    failing[i] += 1
                if (d <= limit) != (f <= floats[i]):
                    disagreements += 1
    return {
        "header": header,
        "total": total,
        "passing": passing,
        "failing": failing,
        "unknown_by_text": unknown_by_text,
        "category": category,
        "exponent": exponent,
        "exact": exact,
        "sources": sources,
        "protein_rows": protein_rows,
        "bad_rows": bad_rows,
        "float_decimal_disagreements": disagreements,
    }


def self_check(out: Path) -> int:
    problems: list[str] = []

    def expect(what: str, seen, wanted) -> None:
        if seen != wanted:
            problems.append("%s: counted %r, manifest says %r" % (what, seen, wanted))

    manifest_path = out / "manifest.json"
    if not manifest_path.is_file():
        print("SELF-CHECK FAILED: %s does not exist; generate it first with python3 %s" % (manifest_path, GENERATOR))
        return 1
    manifest = json.loads(manifest_path.read_text(encoding="ascii"))
    cutoffs = manifest["cutoffs"]
    print("self-check of %s (generator %s, arguments %s)" % (out, manifest["generator_version"], manifest["arguments"]))
    for table, entry in manifest["tables"].items():
        path = out / entry["file"]
        if not path.is_file():
            problems.append("%s is missing" % path)
            continue
        measured = sha256_of(path)
        expect("%s SHA-256" % entry["file"], measured, entry["sha256"])
        expect("%s size in bytes" % entry["file"], path.stat().st_size, entry["bytes"])
        r = _recount(path, cutoffs)
        expect("%s header" % entry["file"], r["header"], HEADER)
        expect("%s data rows" % entry["file"], r["total"], entry["rows"])
        expect("%s malformed rows" % entry["file"], r["bad_rows"], 0)
        unknown = sum(r["unknown_by_text"].values())
        for i, c in enumerate(entry["counts"]):
            expect("%s cutoff %s" % (entry["file"], c["cutoff"]),
                   {"total": r["total"], "passing": r["passing"][i], "failing": r["failing"][i], "unknown": unknown},
                   {k: c[k] for k in ("total", "passing", "failing", "unknown")})
        for kind in entry["unknown_by_kind"]:
            expect("%s unknown kind %s rows" % (entry["file"], kind["label"]),
                   r["unknown_by_text"].get(kind["text"], 0), kind["rows"])
            expect("%s unknown kind %s category" % (entry["file"], kind["label"]),
                   r["category"].get(kind["text"]), kind["category"])
        expect("%s unknown q-value spellings" % entry["file"],
               sorted(r["unknown_by_text"]), sorted(k["text"] for k in entry["unknown_by_kind"]))
        expect("%s exponent-form q-values" % entry["file"], r["exponent"], entry["exponent_form_rows"])
        expect("%s exact boundary values" % entry["file"], r["exact"], entry["exact_value_rows"])
        expect("%s rows by source" % entry["file"], r["sources"], entry["rows_by_source"])
        expect("%s rows by protein count" % entry["file"],
               {k: v for k, v in r["protein_rows"].items() if v}, {k: v for k, v in entry["rows_by_protein_count"].items() if v})
        expect("%s rows where double and decimal comparison disagree" % entry["file"], r["float_decimal_disagreements"], 0)
        print("  %s: %d rows, %d bytes, SHA-256 %s (manifest: %s)"
              % (entry["file"], r["total"], path.stat().st_size, measured,
                 "same" if measured == entry["sha256"] else entry["sha256"]))
        print("    unknown %d by kind %s" % (unknown, {k["label"]: r["unknown_by_text"].get(k["text"], 0) for k in entry["unknown_by_kind"]}))
        print("    exact boundary rows %s; exponent-form q-values %d; sources %d; rows by protein count %s"
              % (r["exact"], r["exponent"], len(r["sources"]), r["protein_rows"]))
        print("    double-vs-decimal disagreements over %d known rows x %d cutoffs: %d"
              % (r["total"] - unknown, len(cutoffs), r["float_decimal_disagreements"]))
        for i, c in enumerate(cutoffs):
            print("    q <= %-6s passing %7d  failing %7d  unknown %4d  total %7d"
                  % (c, r["passing"][i], r["failing"][i], unknown, r["total"]))

    regen = out.parent / (out.name + ".selfcheck-regen")
    if regen.exists():
        shutil.rmtree(regen)
    args = manifest["arguments"]
    started = time.monotonic()
    generate(regen, args["psm_rows"], args["peptide_rows"], args["seed"])
    elapsed = time.monotonic() - started
    for name in ("psms.tsv", "peptides.tsv", "manifest.json"):
        first, second = sha256_of(out / name), sha256_of(regen / name)
        if first != second:
            problems.append("a second generation of %s is %s; the file on disk is %s" % (name, second, first))
        print("  second generation %-13s %s %s" % (name, second, "identical" if first == second else "DIFFERENT"))
    print("  second generation took %.1f s" % elapsed)
    shutil.rmtree(regen)

    if problems:
        print("SELF-CHECK FAILED: %d disagreement(s)" % len(problems))
        for p in problems:
            print("  - " + p)
        return 1
    print("SELF-CHECK PASSED: every count above equals the manifest, and a second generation is byte-identical")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out", default=None, help="output directory (default %s under the repository root)" % DEFAULT_OUT)
    parser.add_argument("--psm-rows", type=int, default=DEFAULT_PSM_ROWS)
    parser.add_argument("--peptide-rows", type=int, default=DEFAULT_PEPTIDE_ROWS)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--self-check", action="store_true", help="check an existing output directory; write nothing into it")
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parent.parent.parent
    out = Path(args.out) if args.out else root / DEFAULT_OUT
    if args.self_check:
        return self_check(out)
    if args.psm_rows < 1 or args.peptide_rows < 1:
        parser.error("row counts must be positive")
    started = time.monotonic()
    manifest = generate(out, args.psm_rows, args.peptide_rows, args.seed)
    elapsed = time.monotonic() - started
    print("wrote %s in %.1f s" % (out, elapsed))
    for entry in manifest["tables"].values():
        print("  %-13s %8d rows %11d bytes  SHA-256 %s" % (entry["file"], entry["rows"], entry["bytes"], entry["sha256"]))
    print("  %-13s %8s      %11d bytes  SHA-256 %s" % ("manifest.json", "", (out / "manifest.json").stat().st_size, sha256_of(out / "manifest.json")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
