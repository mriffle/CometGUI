#!/usr/bin/env python3
"""Fetch the Phase 00 *ephemeral feasibility input* and verify it.

THIS SCRIPT DOES NOT CHOOSE THE PROJECT'S TEST FIXTURE.

``D-006`` -- which spectra and FASTA are CometGUI's fixtures, under what
licence, vendored or fetched -- is an OPEN OWNER DECISION.  What this script
downloads is a small, openly available input used once, by Phase 00, to
demonstrate that the Comet -> Percolator -> Limelight path runs at all.  The
costed shortlist the owner actually decides from is
``docs/feasibility/fixture-candidates.rst``.

Everything is written under ``scratch/fixture/`` (gitignored).  Nothing this
script downloads may be committed: committing spectra or a FASTA would pre-empt
``D-006``.

What it fetches
---------------

* Two mzML files vendored in the Crux toolkit's own smoke-test suite, pinned by
  git commit SHA so the URL is immutable:
  ``20100614_Velos1_TaGe_SA_K562_3.mzML`` and ``..._4.mzML``.  Human K562
  lysate, LTQ Orbitrap Velos, data-dependent HCD with MS2 read out in the FT
  analyser.  Crux is Apache-2.0 (``license.txt`` in that repository).
* The UniProt human reference proteome ``UP000005640_9606.fasta.gz``, CC BY 4.0
  (``https://ftp.uniprot.org/pub/databases/uniprot/previous_releases/LICENSE``),
  decompressed to a plain FASTA for Comet.

Verification
------------

Exit code 0 proves nothing, so this script does not stop at "the download
succeeded".  For every artefact it verifies the SHA-256 against the value
recorded here; for each mzML it then parses the XML and counts MS1 and MS2
spectra and requires the expected MS2 count; for the FASTA it counts ``>``
records and requires the expected number.  Any mismatch is a hard failure --
the check is never relaxed to make the run pass, and a file whose checksum is
wrong is deleted rather than left behind to be mistaken for a good one.

Re-running is cheap: an artefact already present with the right checksum is
verified and not downloaded again.  ``--force`` re-downloads regardless.

Usage
-----

    python3 scripts/feasibility/fetch_ephemeral_input.py
    python3 scripts/feasibility/fetch_ephemeral_input.py --dest /tmp/elsewhere
    python3 scripts/feasibility/fetch_ephemeral_input.py --force

Exit status
-----------

    0   every artefact present, checksum verified, content verified
    1   a download, checksum or content check failed
    2   misuse (bad arguments, unwritable destination)

Standard library only.  Nothing is installed on the host.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import os
import shutil
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from typing import Optional

# --------------------------------------------------------------------------
# Pinned sources.  Every URL here is immutable or is checked byte for byte.
# --------------------------------------------------------------------------

# crux-toolkit master HEAD on 2026-08-23, read from
# https://github.com/crux-toolkit/crux-toolkit/commits/master.atom.  Pinning by
# commit SHA rather than by branch makes the raw.githubusercontent.com URL
# immutable: a later push to master cannot change what this fetches.
CRUX_COMMIT = "fc6335cc817c8629aac07c27f2ab4584ba10930f"
CRUX_RAW = (
    "https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/"
    f"{CRUX_COMMIT}/test/smoke-tests/"
)

# UniProt serves the per-proteome FASTA only from current_release/, which is
# replaced at every UniProt release (2026_02, 10-Jun-2026, at the time of
# writing).  previous_releases/ keeps whole-database tarballs, not per-proteome
# FASTA files, so there is no immutable URL for this file.  The checksum below
# therefore also acts as a release detector: if UniProt has rolled a release,
# this script fails loudly and says so rather than silently searching against a
# different database.  See "Ephemeral feasibility input" in
# docs/feasibility/fixture-candidates.rst.
UNIPROT_FASTA_URL = (
    "https://ftp.uniprot.org/pub/databases/uniprot/current_release/"
    "knowledgebase/reference_proteomes/Eukaryota/UP000005640/"
    "UP000005640_9606.fasta.gz"
)
UNIPROT_RELEASE = "2026_02 (10-Jun-2026)"

USER_AGENT = "CometGUI-Phase00-feasibility/1.0 (+scripts/feasibility)"
TIMEOUT = 120
RETRIES = 3


class Artefact:
    """One downloaded file and everything that must be true about it."""

    def __init__(
        self,
        name: str,
        url: str,
        sha256: str,
        size: int,
        kind: str,
        licence: str,
        licence_url: str,
        expect_ms2: Optional[int] = None,
    ) -> None:
        self.name = name
        self.url = url
        self.sha256 = sha256
        self.size = size
        self.kind = kind  # "mzml" | "fasta-gz"
        self.licence = licence
        self.licence_url = licence_url
        self.expect_ms2 = expect_ms2


ARTEFACTS = [
    Artefact(
        name="20100614_Velos1_TaGe_SA_K562_3.mzML",
        url=CRUX_RAW + "20100614_Velos1_TaGe_SA_K562_3.mzML",
        sha256="cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7",
        size=11098328,
        kind="mzml",
        licence="Apache-2.0 (crux-toolkit repository licence)",
        licence_url=(
            "https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/"
            f"{CRUX_COMMIT}/license.txt"
        ),
        expect_ms2=728,
    ),
    Artefact(
        name="20100614_Velos1_TaGe_SA_K562_4.mzML",
        url=CRUX_RAW + "20100614_Velos1_TaGe_SA_K562_4.mzML",
        sha256="d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa",
        size=9416326,
        kind="mzml",
        licence="Apache-2.0 (crux-toolkit repository licence)",
        licence_url=(
            "https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/"
            f"{CRUX_COMMIT}/license.txt"
        ),
        expect_ms2=610,
    ),
    Artefact(
        name="UP000005640_9606.fasta.gz",
        url=UNIPROT_FASTA_URL,
        sha256="cf49a88c4812dabbd934cb3e2e00b449e70375816e4d47cda7cc5b77b0754024",
        size=7752225,
        kind="fasta-gz",
        licence="CC BY 4.0 (UniProt)",
        licence_url=(
            "https://ftp.uniprot.org/pub/databases/uniprot/previous_releases/LICENSE"
        ),
    ),
]

# The decompressed FASTA Comet is actually given.
FASTA_NAME = "UP000005640_9606.fasta"
FASTA_SHA256 = "2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0"
FASTA_SIZE = 13731881
FASTA_ENTRIES = 20652

MZML_NS = "{http://psi.hupo.org/ms/mzml}"


# --------------------------------------------------------------------------
# helpers
# --------------------------------------------------------------------------


def log(msg: str = "") -> None:
    print(msg, flush=True)


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: str) -> None:
    """Download to a temporary file in the destination directory, then move it.

    Downloading straight onto the final path would leave a truncated file
    behind if the transfer died halfway, and the next run would then verify a
    partial file.  The rename is the last step, so the final path only ever
    holds a complete transfer.
    """
    directory = os.path.dirname(os.path.abspath(dest))
    last_error = None
    for attempt in range(1, RETRIES + 1):
        fd, tmp = tempfile.mkstemp(dir=directory, prefix=".download-")
        os.close(fd)
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            started = time.time()
            with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                with open(tmp, "wb") as out:
                    shutil.copyfileobj(response, out, length=1 << 20)
            elapsed = time.time() - started
            size = os.path.getsize(tmp)
            # mkstemp creates 0600; the artefacts are ordinary read-only data
            # that later work units read back, so widen to the usual 0644.
            os.chmod(tmp, 0o644)
            os.replace(tmp, dest)
            log(f"      downloaded {size} bytes in {elapsed:.1f}s")
            return
        except (urllib.error.URLError, OSError, TimeoutError) as exc:  # noqa: PERF203
            last_error = exc
            if os.path.exists(tmp):
                os.unlink(tmp)
            if attempt < RETRIES:
                wait = 2 * attempt
                log(f"      attempt {attempt} failed ({exc!r}); retrying in {wait}s")
                time.sleep(wait)
    raise RuntimeError(f"download failed after {RETRIES} attempts: {url}: {last_error!r}")


def count_mzml_spectra(path: str) -> "tuple[int, int, int]":
    """Return (total spectra, MS1 count, MS2 count) by parsing the mzML.

    This is the check that distinguishes "the download succeeded" from "the
    file is a real, parseable spectrum file that contains actual MS2 scans".
    """
    total = ms1 = ms2 = 0
    for _event, element in ET.iterparse(path, events=("end",)):
        if element.tag != MZML_NS + "spectrum":
            continue
        total += 1
        level = None
        for param in element.findall(MZML_NS + "cvParam"):
            if param.get("name") == "ms level":
                level = param.get("value")
                break
        if level == "1":
            ms1 += 1
        elif level == "2":
            ms2 += 1
        element.clear()
    return total, ms1, ms2


def count_fasta_entries(path: str) -> int:
    entries = 0
    with open(path, "rb") as fh:
        for line in fh:
            if line.startswith(b">"):
                entries += 1
    return entries


def fail(message: str) -> None:
    log("")
    log("FAILED: " + message)


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------


def repo_root() -> str:
    return os.path.dirname(os.path.dirname(os.path.abspath(os.path.dirname(__file__))))


def main(argv: "list[str]") -> int:
    default_dest = os.path.join(repo_root(), "scratch", "fixture")
    parser = argparse.ArgumentParser(
        description=(
            "Fetch and verify the Phase 00 ephemeral feasibility input. "
            "This is NOT the project's test fixture: D-006 is open and is the "
            "owner's to decide."
        )
    )
    parser.add_argument(
        "--dest",
        default=default_dest,
        help=f"destination directory (default: {default_dest})",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="re-download even when a verified copy is already present",
    )
    args = parser.parse_args(argv)

    dest = os.path.abspath(args.dest)
    try:
        os.makedirs(dest, exist_ok=True)
    except OSError as exc:
        fail(f"cannot create destination {dest}: {exc}")
        return 2
    if not os.access(dest, os.W_OK):
        fail(f"destination is not writable: {dest}")
        return 2

    log("CometGUI Phase 00 -- ephemeral feasibility input")
    log("=" * 62)
    log("")
    log("This is a Phase 00 feasibility input, NOT the project's fixture.")
    log("D-006 (fixture data and licensing) is OPEN and is the owner's to")
    log("decide; see docs/feasibility/fixture-candidates.rst.")
    log("")
    log(f"destination: {dest}")
    log(f"UniProt release pinned by checksum: {UNIPROT_RELEASE}")
    log("")

    problems = []

    for artefact in ARTEFACTS:
        path = os.path.join(dest, artefact.name)
        log(f"[{artefact.name}]")
        log(f"   url:     {artefact.url}")
        log(f"   licence: {artefact.licence}")
        log(f"            {artefact.licence_url}")

        have = os.path.exists(path) and not args.force
        if have:
            actual = sha256_of(path)
            if actual == artefact.sha256:
                log("   present with the expected checksum; not re-downloading")
            else:
                log("   present but checksum differs; re-downloading")
                have = False
        if not have:
            try:
                download(artefact.url, path)
            except RuntimeError as exc:
                problems.append(f"{artefact.name}: {exc}")
                log(f"   ERROR: {exc}")
                log("")
                continue

        size = os.path.getsize(path)
        actual = sha256_of(path)
        log(f"   size:    {size} bytes (expected {artefact.size})")
        log(f"   sha256:  {actual}")
        if size != artefact.size:
            problems.append(
                f"{artefact.name}: size {size} != expected {artefact.size}"
            )
        if actual != artefact.sha256:
            problems.append(
                f"{artefact.name}: sha256 mismatch\n"
                f"      expected {artefact.sha256}\n"
                f"      actual   {actual}\n"
                f"      The checksum is not relaxed to make this pass. For the\n"
                f"      UniProt FASTA a mismatch most likely means UniProt has\n"
                f"      published a release newer than {UNIPROT_RELEASE}; the\n"
                f"      recorded value must then be re-established deliberately\n"
                f"      and recorded in docs/feasibility/fixture-candidates.rst,\n"
                f"      not patched silently."
            )
            # Do not leave a file that failed verification lying around where a
            # later step might pick it up.
            os.unlink(path)
            log("   ERROR: checksum mismatch; the file has been deleted")
            log("")
            continue
        log("   checksum verified")

        if artefact.kind == "mzml":
            total, ms1, ms2 = count_mzml_spectra(path)
            log(f"   parsed:  {total} spectra -- {ms1} MS1, {ms2} MS2")
            if ms2 != artefact.expect_ms2:
                problems.append(
                    f"{artefact.name}: {ms2} MS2 spectra, expected "
                    f"{artefact.expect_ms2}"
                )
            elif ms2 == 0:
                problems.append(f"{artefact.name}: contains no MS2 spectra")
            else:
                log("   MS2 scan count verified")
        log("")

    # The FASTA Comet is given must be plain text, so decompress it.
    gz_path = os.path.join(dest, "UP000005640_9606.fasta.gz")
    fasta_path = os.path.join(dest, FASTA_NAME)
    if os.path.exists(gz_path):
        log(f"[{FASTA_NAME}]")
        needs_write = args.force or not os.path.exists(fasta_path)
        if not needs_write and sha256_of(fasta_path) != FASTA_SHA256:
            needs_write = True
        if needs_write:
            with gzip.open(gz_path, "rb") as src, open(fasta_path, "wb") as out:
                shutil.copyfileobj(src, out, length=1 << 20)
            log("   decompressed from UP000005640_9606.fasta.gz")
        else:
            log("   present with the expected checksum; not re-decompressing")
        size = os.path.getsize(fasta_path)
        actual = sha256_of(fasta_path)
        log(f"   size:    {size} bytes (expected {FASTA_SIZE})")
        log(f"   sha256:  {actual}")
        if actual != FASTA_SHA256:
            problems.append(
                f"{FASTA_NAME}: sha256 mismatch\n"
                f"      expected {FASTA_SHA256}\n"
                f"      actual   {actual}"
            )
        else:
            log("   checksum verified")
        entries = count_fasta_entries(fasta_path)
        log(f"   parsed:  {entries} sequence records (expected {FASTA_ENTRIES})")
        if entries != FASTA_ENTRIES:
            problems.append(
                f"{FASTA_NAME}: {entries} records, expected {FASTA_ENTRIES}"
            )
        log("")

    log("-" * 62)
    if problems:
        fail(f"{len(problems)} check(s) did not pass:")
        for problem in problems:
            log(f"   * {problem}")
        log("")
        log("Nothing here is weakened to make the run green. Fix the cause.")
        return 1

    log("OK -- every artefact downloaded, checksum verified and content verified.")
    log("")
    log("Files for the Phase 00 scientific-path proof:")
    log(f"   spectra 1: {os.path.join(dest, ARTEFACTS[0].name)}")
    log(f"   spectra 2: {os.path.join(dest, ARTEFACTS[1].name)}")
    log(f"   database:  {fasta_path}")
    log("")
    log("Comet advice for this data (see fixture-candidates.rst for the whole")
    log("set): trypsin, 2 missed cleavages; precursor 20 ppm with isotope_error")
    log("on; HIGH-resolution HCD MS2 read out in the Orbitrap, so")
    log("fragment_bin_tol 0.02 / fragment_bin_offset 0.0 /")
    log("theoretical_fragment_ions 0; decoy_search 1 so the PIN carries both")
    log("target and decoy rows; output_pepxmlfile 1 and output_percolatorfile 1.")
    log("")
    log("REMINDER: none of these files may be committed. D-006 is open.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
