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
"""Select the fixture FASTA from the UniProt human reference proteome.

Phase 16 unit 2 (``phases/PHASE-16-release.rst``, *The CI fixture*, steps 3, 4
and 6; ``DECISIONS.rst``, ``D-006`` and ``D-018``). The fixture's FASTA is a
subset of the proteome: the proteins behind the confident identifications of
the two K562 spectrum files, and a background of others. This script is the
whole of how that subset is chosen and written, so that anyone holding the
source file gets the same bytes. ``docs/developer/test_fixtures.rst`` says why
each choice was made and what was measured.

The source
==========

One file, pinned here by SHA-256: ``UP000005640_9606.fasta``, the UniProt
human reference proteome (canonical sequences) of UniProt release 2026_02,
decompressed. Every command that reads the source refuses any other bytes.

The two rules
=============

**Identified.** A protein is *identified* when Percolator names it for a
target PSM whose q-value is at or below 0.01 in ``psms.tsv``, or for a target
peptide whose q-value is at or below 0.01 in ``peptides.tsv``, in ANY run of an
acceptance report made over the source (``scripts/fixtures/check-fixture.sh``
writes one: every Comet and Percolator pair the product offers, in both of the
check's configurations). Every protein the row names is taken -- a peptide
shared by several proteins keeps all of them -- except names carrying Comet's
decoy prefix, which are not in the source.

**Background.** Every other record of the source is ranked by the SHA-256 of
its accession (the ASCII text between the bars of its header, for example
``P04637``), as lower-case hexadecimal, ascending. The background is the first
N of that ranking. Nothing is random, nothing depends on the order of the
file, and a larger N keeps every record a smaller N kept.

The subset is written in the source's order, each record copied byte for byte.

Commands
========

::

    fixture_fasta.py identify --report DIR --source FASTA --background N --out LIST
    fixture_fasta.py build    --source FASTA --accessions LIST --out FASTA
    fixture_fasta.py verify   --source FASTA [--accessions LIST] [--fasta FASTA]
    fixture_fasta.py describe [--accessions LIST] [--fasta FASTA]

``identify`` reads the search results and writes the accession list, with the
size, record count and SHA-256 of the subset it defines. ``build`` writes the
subset from the source and a list, and refuses to leave a file whose digest is
not the one the list records. ``verify`` rebuilds in memory and compares with
the committed file, byte for byte. ``describe`` needs no source: it checks the
committed file against the committed list (digest, size, the accessions and
their order) and prints what it is.

Exit status: 0 done and verified; 1 a check failed; 2 misuse, or an input was
refused. Standard library only.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent

SOURCE_NAME = "UP000005640_9606.fasta"
SOURCE_SHA256 = "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0"
SOURCE_BYTES = 13731881
SOURCE_RECORDS = 20652
SOURCE_RELEASE = "UniProt release 2026_02 (10-Jun-2026)"

FIXTURE_DIRECTORY = ROOT / "fixtures" / "human-k562-subset"
FIXTURE_FASTA = FIXTURE_DIRECTORY / "uniprot-human-2026_02-k562-subset.fasta"
FIXTURE_ACCESSIONS = FIXTURE_DIRECTORY / "accessions.tsv"

IDENTIFIED, BACKGROUND = "identified", "background"
Q_VALUE = "0.01"
DECOY_PREFIX = "DECOY_"
TABLES = ("psms.tsv", "peptides.tsv")
HEADER = re.compile(rb"^>(sp|tr)\|([A-Z0-9]+)\|(\S+)")

IDENTIFIED_RULE = (
    "named by Percolator for a target PSM with q-value <= {q} in psms.tsv or a target peptide with "
    "q-value <= {q} in peptides.tsv, in any run of an acceptance report over the source; every "
    "protein a row names is kept; names with the decoy prefix {prefix} are not in the source"
)
BACKGROUND_RULE = (
    "the records not identified, ranked by the SHA-256 of the accession (lower-case hexadecimal, "
    "ascending); the first {n} are kept"
)


class Refused(Exception):
    """An input this script will not work from."""


# ===========================================================================
# FASTA records
# ===========================================================================

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_records(path: Path) -> list:
    """The records of a UniProt FASTA, in order: ``(accession, name, bytes)``.

    A record is its header line and every line up to the next header, bytes
    untouched. Refuses a file that does not begin with a header, a header that
    is not UniProt's ``>db|ACCESSION|NAME``, a carriage return, and an
    accession that occurs twice.
    """
    data = Path(path).read_bytes()
    if b"\r" in data:
        raise Refused(f"{path} holds a carriage return: it is not the file UniProt serves")
    if not data.startswith(b">"):
        raise Refused(f"{path} does not begin with a FASTA header")
    starts = [0]
    position = data.find(b"\n>")
    while position != -1:
        starts.append(position + 1)
        position = data.find(b"\n>", position + 1)
    records, seen = [], set()
    for index, start in enumerate(starts):
        end = starts[index + 1] if index + 1 < len(starts) else len(data)
        record = data[start:end]
        match = HEADER.match(record)
        if not match:
            raise Refused(f"{path}: record {index + 1} has no UniProt header: {record[:60]!r}")
        accession = match.group(2).decode("ascii")
        if accession in seen:
            raise Refused(f"{path}: accession {accession} occurs twice")
        seen.add(accession)
        name = (match.group(1) + b"|" + match.group(2) + b"|" + match.group(3)).decode("ascii")
        records.append((accession, name, record))
    if b"".join(record for _, _, record in records) != data:
        raise Refused(f"{path}: the records do not add up to the file")
    return records


def read_source(path: Path) -> list:
    """The pinned source's records; refuses any other bytes."""
    path = Path(path)
    if not path.is_file():
        raise Refused(f"the source FASTA {path} does not exist")
    digest = sha256_bytes(path.read_bytes())
    if digest != SOURCE_SHA256:
        raise Refused(
            f"{path} is not the source: its SHA-256 is {digest}, and the source is {SOURCE_NAME} of "
            f"{SOURCE_RELEASE}, SHA-256 {SOURCE_SHA256}. A different UniProt release gives a "
            "different subset; the checksum is not relaxed")
    records = read_records(path)
    if len(records) != SOURCE_RECORDS:
        raise Refused(f"{path} holds {len(records)} records, not {SOURCE_RECORDS}")
    return records


def subset(records: list, wanted: set) -> bytes:
    """The wanted records, in the order they were read, each byte for byte."""
    present = {accession for accession, _, _ in records}
    missing = sorted(set(wanted) - present)
    if missing:
        raise Refused(f"{len(missing)} wanted accession(s) are not in the FASTA read, the first "
                      f"{missing[:5]}")
    return b"".join(record for accession, _, record in records if accession in wanted)


def background_ranking(records: list, identified: set) -> list:
    """The accessions that are not identified, in the background rule's order."""
    others = [accession for accession, _, _ in records if accession not in identified]
    return sorted(others, key=lambda accession: hashlib.sha256(accession.encode("ascii")).hexdigest())


# ===========================================================================
# The accession list
# ===========================================================================

def write_list(path: Path, records: list, kept: dict, meta: dict) -> None:
    lines = [
        "# The accessions of the fixture FASTA, and what each record was kept as.",
        "# Written by scripts/fixtures/fixture_fasta.py identify -- regenerate it, never edit it.",
        "# The rules and the measurements behind them: docs/developer/test_fixtures.rst.",
    ]
    lines += [f"# {key}\t{value}" for key, value in meta.items()]
    lines.append("# columns\taccession\tkept-as")
    lines += [f"{accession}\t{kept[accession]}" for accession, _, _ in records if accession in kept]
    Path(path).write_bytes(("\n".join(lines) + "\n").encode("ascii"))


def read_list(path: Path) -> tuple:
    """``(kept, meta)``: accession -> identified/background in file order, and the header's values."""
    path = Path(path)
    if not path.is_file():
        raise Refused(f"the accession list {path} does not exist")
    kept, meta = {}, {}
    for number, line in enumerate(path.read_text(encoding="ascii").splitlines(), start=1):
        if line.startswith("#"):
            fields = line[1:].strip().split("\t")
            if len(fields) == 2:
                meta[fields[0]] = fields[1]
            continue
        fields = line.split("\t")
        if len(fields) != 2 or fields[1] not in (IDENTIFIED, BACKGROUND):
            raise Refused(f"{path}:{number}: not 'accession<TAB>{IDENTIFIED}|{BACKGROUND}': {line!r}")
        if fields[0] in kept:
            raise Refused(f"{path}:{number}: accession {fields[0]} is listed twice")
        kept[fields[0]] = fields[1]
    if not kept:
        raise Refused(f"{path} lists no accession")
    for key in ("source-sha256", "subset-sha256", "subset-bytes", "subset-records"):
        if key not in meta:
            raise Refused(f"{path} does not record '{key}'")
    if meta["source-sha256"] != SOURCE_SHA256:
        raise Refused(f"{path} was made from a source with SHA-256 {meta['source-sha256']}, and this "
                      f"script pins {SOURCE_SHA256}")
    return kept, meta


# ===========================================================================
# The identified proteins, from an acceptance report
# ===========================================================================

def report_runs(report: Path) -> list:
    """Every run of an acceptance report: ``(row, run directory)``; refuses a report with none."""
    report = Path(report)
    rows = sorted((report / "rows").glob("acceptance_*.json")) if (report / "rows").is_dir() else []
    if not rows:
        raise Refused(f"{report} holds no acceptance row: run scripts/fixtures/check-fixture.sh over "
                      "a directory holding the source FASTA first")
    runs = []
    for file in rows:
        row = json.loads(file.read_text(encoding="utf-8"))
        observed = row.get("observed", {})
        directory = observed.get("runDirectory")
        if not directory:
            raise Refused(f"{file.name}: the run did not finish, so it names no proteins")
        runs.append((row, report / directory))
    return runs


def identified_names(report: Path) -> tuple:
    """Protein names behind the confident identifications of every run: ``(names, per run)``."""
    names, per_run = set(), []
    for row, directory in report_runs(report):
        files = row["observed"].get("fixtureFiles", {})
        fastas = {name: digest for name, digest in files.items() if name.endswith(".fasta")}
        if list(fastas.values()) != [SOURCE_SHA256]:
            raise Refused(f"{row['id']} searched {fastas}, not the source FASTA ({SOURCE_SHA256}): the "
                          "identified proteins are taken from a search of the source alone")
        found = set()
        for table in TABLES:
            found |= confident_names(directory / "outputs" / "percolator" / table)
        per_run.append((row["id"], len(found)))
        names |= found
    return names, per_run


def confident_names(table: Path) -> set:
    """The protein names of a Percolator table's rows at or below the q-value, decoys excluded."""
    if not table.is_file():
        raise Refused(f"{table} does not exist")
    lines = table.read_text(encoding="utf-8").splitlines()
    header = lines[0].split("\t")
    if header[:6] != ["PSMId", "score", "q-value", "posterior_error_prob", "peptide", "proteinIds"]:
        raise Refused(f"{table}: not a Percolator table: {header}")
    names = set()
    for line in lines[1:]:
        if not line:
            continue
        fields = line.split("\t")
        if float(fields[2]) <= float(Q_VALUE):
            names.update(name for name in fields[5:] if name and not name.startswith(DECOY_PREFIX))
    return names


# ===========================================================================
# Commands
# ===========================================================================

def command_identify(args) -> int:
    records = read_source(args.source)
    by_name = {name: accession for accession, name, _ in records}
    names, per_run = identified_names(args.report)
    unknown = sorted(name for name in names if name not in by_name)
    if unknown:
        raise Refused(f"{len(unknown)} identified protein name(s) are not in the source, the first "
                      f"{unknown[:5]}")
    identified = {by_name[name] for name in names}
    ranking = background_ranking(records, identified)
    if not 0 <= args.background <= len(ranking):
        raise Refused(f"--background {args.background}: the source has {len(ranking)} records that "
                      "are not identified")
    kept = {accession: IDENTIFIED for accession in identified}
    kept.update({accession: BACKGROUND for accession in ranking[:args.background]})
    data = subset(records, set(kept))
    meta = {
        "source-name": SOURCE_NAME,
        "source-release": SOURCE_RELEASE,
        "source-sha256": SOURCE_SHA256,
        "source-bytes": SOURCE_BYTES,
        "source-records": SOURCE_RECORDS,
        "identified-rule": IDENTIFIED_RULE.format(q=Q_VALUE, prefix=DECOY_PREFIX),
        "identified-runs": len(per_run),
        "identified-records": len(identified),
        "background-rule": BACKGROUND_RULE.format(n=args.background),
        "background-records": args.background,
        "subset-records": len(kept),
        "subset-bytes": len(data),
        "subset-sha256": sha256_bytes(data),
    }
    write_list(args.out, records, kept, meta)
    for row_id, count in per_run:
        print(f"identify: {row_id}: {count} protein(s) behind its PSMs and peptides at q <= {Q_VALUE}")
    print(f"identify: {len(identified)} identified in the union of {len(per_run)} run(s); "
          f"{args.background} background; {len(kept)} of {SOURCE_RECORDS} records kept")
    print(f"identify: the subset is {len(data)} bytes, SHA-256 {meta['subset-sha256']}")
    print(f"identify: wrote {args.out}")
    return 0


def built(source: Path, accessions: Path) -> tuple:
    """The subset the list defines, built from the source and checked against the list."""
    kept, meta = read_list(accessions)
    records = read_source(source)
    data = subset(records, set(kept))
    problems = check_against_list(data, kept, meta)
    ranking = background_ranking(records, {a for a, role in kept.items() if role == IDENTIFIED})
    background = {a for a, role in kept.items() if role == BACKGROUND}
    if set(ranking[:len(background)]) != background:
        problems.append("the background accessions are not the first "
                        f"{len(background)} of the rule's ranking over the source")
    return data, kept, meta, problems


def check_against_list(data: bytes, kept: dict, meta: dict) -> list:
    problems = []
    digest = sha256_bytes(data)
    if digest != meta["subset-sha256"]:
        problems.append(f"SHA-256 {digest}, and the list records {meta['subset-sha256']}")
    if str(len(data)) != meta["subset-bytes"]:
        problems.append(f"{len(data)} bytes, and the list records {meta['subset-bytes']}")
    if str(len(kept)) != meta["subset-records"]:
        problems.append(f"the list holds {len(kept)} accessions and records {meta['subset-records']}")
    return problems


def command_build(args) -> int:
    data, kept, meta, problems = built(args.source, args.accessions)
    if problems:
        for problem in problems:
            print(f"build: FAILED -- {problem}", file=sys.stderr)
        return 1
    Path(args.out).write_bytes(data)
    print(f"build: wrote {args.out}: {len(kept)} of {SOURCE_RECORDS} records, {len(data)} bytes, "
          f"SHA-256 {sha256_bytes(data)} -- as the list records")
    return 0


def command_verify(args) -> int:
    data, kept, meta, problems = built(args.source, args.accessions)
    committed = Path(args.fasta)
    if not committed.is_file():
        problems.append(f"{committed} does not exist")
    elif committed.read_bytes() != data:
        problems.append(f"{committed} is not the bytes the source and the list give "
                        f"(SHA-256 {sha256_bytes(committed.read_bytes())})")
    if problems:
        for problem in problems:
            print(f"verify: FAILED -- {problem}", file=sys.stderr)
        return 1
    roles = list(kept.values())
    print(f"verify: OK -- {committed} is byte for byte what {args.source} and {args.accessions} give: "
          f"{len(kept)} of {SOURCE_RECORDS} records ({roles.count(IDENTIFIED)} identified, "
          f"{roles.count(BACKGROUND)} background), {len(data)} bytes, SHA-256 {sha256_bytes(data)}")
    return 0


def describe(fasta: Path, accessions: Path) -> tuple:
    """Checks a fixture FASTA against its list without the source: ``(facts, problems)``."""
    kept, meta = read_list(accessions)
    records = read_records(fasta)
    data = b"".join(record for _, _, record in records)
    problems = check_against_list(data, kept, meta)
    if [accession for accession, _, _ in records] != list(kept):
        problems.append("the FASTA's accessions, in order, are not the list's")
    roles = list(kept.values())
    facts = {
        "records": len(records),
        "identified": roles.count(IDENTIFIED),
        "background": roles.count(BACKGROUND),
        "bytes": len(data),
        "sha256": sha256_bytes(data),
        "source": f"{meta.get('source-name')} of {meta.get('source-release')}, "
                  f"SHA-256 {meta['source-sha256']}, {meta.get('source-records')} records",
    }
    return facts, problems


def command_describe(args) -> int:
    facts, problems = describe(Path(args.fasta), Path(args.accessions))
    if problems:
        for problem in problems:
            print(f"describe: FAILED -- {args.fasta}: {problem}", file=sys.stderr)
        return 1
    print(f"describe: OK -- {args.fasta}: {facts['records']} records ({facts['identified']} identified, "
          f"{facts['background']} background), {facts['bytes']} bytes, SHA-256 {facts['sha256']}; "
          f"a subset of {facts['source']}")
    return 0


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="fixture_fasta.py", description=__doc__.split("\n\n", 1)[0])
    sub = parser.add_subparsers(dest="command")
    identify = sub.add_parser("identify")
    identify.add_argument("--report", required=True)
    identify.add_argument("--source", required=True)
    identify.add_argument("--background", required=True, type=int)
    identify.add_argument("--out", required=True)
    build = sub.add_parser("build")
    build.add_argument("--source", required=True)
    build.add_argument("--accessions", default=str(FIXTURE_ACCESSIONS))
    build.add_argument("--out", required=True)
    verify = sub.add_parser("verify")
    verify.add_argument("--source", required=True)
    verify.add_argument("--accessions", default=str(FIXTURE_ACCESSIONS))
    verify.add_argument("--fasta", default=str(FIXTURE_FASTA))
    described = sub.add_parser("describe")
    described.add_argument("--accessions", default=str(FIXTURE_ACCESSIONS))
    described.add_argument("--fasta", default=str(FIXTURE_FASTA))
    args = parser.parse_args(argv)
    commands = {"identify": command_identify, "build": command_build, "verify": command_verify,
                "describe": command_describe}
    if args.command not in commands:
        parser.print_help()
        return 2
    try:
        return commands[args.command](args)
    except Refused as refused:
        print(f"fixture_fasta.py: REFUSED -- {refused}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
