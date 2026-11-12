#!/usr/bin/env python3
"""trial.py -- can Comet for Windows open the project's Thermo RAW fixture?

PHASE-16, work unit 1 (decision P16-2 in handoffs/PHASE-16-worklog.rst).  A
bare-tools trial: no CometGUI code runs here.

THE QUESTION.  DECISIONS.rst D-019 chose ``ph050213_18.raw`` (PRIDE project
PXD002914) as the Windows RAW fixture on the strength of counts measured from a
copy CONVERTED TO mzML ON LINUX.  Nothing has ever shown that Comet on Windows
opens the RAW file itself.  This program finds out, on a GitHub
``windows-latest`` runner, before anything is built on the assumption.

WHAT IT DOES, in order.

 1. Reads ``manifests/tools.json`` and downloads, by the URL, size and SHA-256
    recorded there and nowhere else: ``comet.win64.exe`` of every Comet release
    the manifest offers for windows/x86-64, each release's runtime companions
    (the Thermo libraries) into one directory beside it under the names Comet
    loads them by, and the Windows Percolator 3.07.1 archive, from which
    ``percolator.exe`` is extracted and checked against its member pin.
    Percolator's only companion record is a pair of XSD files that are not a
    runtime prerequisite; it is not fetched.  The Visual C++ runtime both
    programs need is the host's to supply (the manifest's
    ``requiredHostLibraries``); a hosted runner image has it.
 2. Downloads the RAW file from PRIDE in binary mode, by the URL, size and
    SHA-256 in D-019.  Any mismatch stops the trial with exit 3.
 3. Verifies the committed FASTA,
    ``fixtures/synechococcus-wh8102/UP000001422_84588.fasta``, by size, SHA-256
    and record count, and copies it into the search directory.
 4. Writes the Comet parameter file exactly as the RAW search did
    (docs/feasibility/small-thermo-raw-files.rst, "Exact commands"):
    ``scripts/feasibility/comet.params`` with ``database_name`` replaced and
    D-019's seven ion-trap values, each replacing its whole line.  The eight
    changed lines are printed.  On the runner it also runs ``comet -p`` and
    prints every parameter whose value differs from what that release writes.
 5. Runs ``comet.win64.exe -P<params> ph050213_18.raw`` in a fresh directory
    and prints its stdout and stderr in full.
 6. Decides READ or NOT-READ from what is on disk, never from the exit code:
    a PIN with at least 1 000 data rows, targets and decoys both present (or,
    failing a PIN, a pepXML with at least 200 spectrum queries).  The search
    directory is created empty and is checked to hold no PIN before Comet
    starts, so a file left by an earlier run cannot be mistaken for this one's.
 7. Runs Percolator 3.07.1 over the PIN with the options the RAW search used
    (``-m -M -r -B -w``, no seed option, so Percolator's default seed) and
    counts the rows with q-value < 0.01 in the target PSM and target peptide
    tables -- the counting convention of the search report.
 8. Does all of that once per Comet release, each in its own directory.  If,
    and only if, a release cannot read ``ph050213_18.raw``, the same release
    then tries D-019's fallback, ``ph280213_36.raw``, and reports it labelled
    as the fallback.  No other file is ever tried.

WHAT IT PRINTS.  A transcript of every step (also written to
``<work-dir>/transcript.txt``), and at the end one block of lines that each
begin ``RAW-TRIAL|``:

    RAW-TRIAL|begin|...            the commit, the host, the mode
    RAW-TRIAL|result|comet=V|file=F|role=primary|read=READ|pin-rows=N|
        pin-targets=N|pin-decoys=N|percolator=TRAINED|psms-q01=N|
        peptides-q01=N|d019-psms=N|d019-peptides=N|diff-psms=+N|
        diff-peptides=+N                       (one line; wrapped here only)
    RAW-TRIAL|detail|...           banner, exit codes, spectra loaded, scans
    RAW-TRIAL|reference|...        what the Linux mzML search of the file gave
    RAW-TRIAL|params|...           the parameters that differ from ``comet -p``
    RAW-TRIAL|risk|...             one line per known risk (empty MS1 scans,
                                   empty MS2 scans, no precursor charge)
    RAW-TRIAL|comet-said|...       every line Comet printed that mentions a
                                   warning, an error, a charge or an empty scan
    RAW-TRIAL|verdict|exit=N|...   the conclusion and the exit status
    RAW-TRIAL|end

``grep '^RAW-TRIAL|'`` on the job log is the whole result; ``grep
'^RAW-TRIAL|result|'`` is one line per Comet release and file.  ``--check-only``
prints ``RAW-CHECK|`` lines instead and never a ``RAW-TRIAL|`` line.

A count that differs from D-019's is REPORTED, NOT FAILED.  D-019's 906 PSMs
and 787 peptides came from Comet 2026.02.2 reading a converted mzML on Linux;
the Windows RAW reader may legitimately load a different set of spectra, and
Comet 2026.03.0 gives 920 / 797 from that same mzML.  What a difference means
is the phase orchestrator's to decide, with the numbers in front of it.

EXIT STATUS.
    0  every Comet release read ``ph050213_18.raw``, and for each Percolator
       trained and wrote both target tables with a non-zero count at
       q < 0.01.  Also a clean ``--check-only``, which executes no tool and
       says so.
    1  at least one Comet release could not read ``ph050213_18.raw`` -- even
       if it then read the fallback.
    2  every release read the file, but for at least one Percolator did not
       train, wrote no table, or found nothing at q < 0.01.
    3  no verdict: a download failed its size or SHA-256, an artefact was
       unavailable, the manifest or the FASTA was not as pinned, or the
       harness itself failed.  Nothing was learned about Comet.
    4  refused: not a Windows host, and ``--check-only`` was not given.
    5  misuse.
It never exits 0 having searched nothing.

HOW TO RUN.
    bash scripts/spikes/windows-raw-trial/run.sh                # Windows only
    bash scripts/spikes/windows-raw-trial/run.sh --check-only   # anywhere
    bash scripts/spikes/windows-raw-trial/run.sh --check-only --work-dir DIR

``--check-only`` fetches and verifies every download (the fallback RAW file
included), verifies the FASTA and writes the parameter file; it executes
nothing.  Everything is written under ``--work-dir`` (default
``_build/windows-raw-trial/``, which git ignores).  About 28 MB is downloaded,
37 MB with ``--check-only``.
Standard library only, Python 3.9 or newer; nothing is installed.

WHAT MUST NOT HAPPEN HERE.  No RAW converter (ThermoRawFileParser or any
other) is downloaded or run: its Thermo licence forbids combining it with GPL
software, and the product reaches RAW only through Comet.  The RAW file, the
PIN and every other spectrum-derived file stay in the work directory and are
never committed or uploaded.

Processes are launched with an argument array, never a shell string: on the
runner ``shell: bash`` is Git Bash, whose path rewriting no logic here may
depend on (see scripts/ci/windows_percolator_verify.py, the prior art).
"""

from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import os
import platform
import re
import shutil
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from collections import Counter

EXIT_OK = 0
EXIT_NOT_READ = 1
EXIT_READ_NOT_SCORED = 2
EXIT_NO_VERDICT = 3
EXIT_REFUSED = 4
EXIT_MISUSE = 5

SELF = "trial.py"
IS_WINDOWS = os.name == "nt"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(SCRIPT_DIR)))
DEFAULT_WORK_DIR = os.path.join(PROJECT_ROOT, "_build", "windows-raw-trial")

MANIFEST_RELPATH = ("manifests", "tools.json")
PERCOLATOR_VERSION = "3.07.1"
MANIFEST_OS = "windows"
MANIFEST_ARCH = "x86-64"

# --------------------------------------------------------------------------
# Pins that manifests/tools.json does NOT hold.  None may be relaxed.
# --------------------------------------------------------------------------

# DECISIONS.rst, D-019: the directory, and for each file its size, SHA-256 and
# the counts at q < 0.01.  The remaining reference figures are the search
# report's (docs/feasibility/small-thermo-raw-files.rst, "Candidates 1 to 4"),
# all measured on Linux from the file converted to mzML, with Comet 2026.02.2.
PRIDE_DIRECTORY = "https://ftp.pride.ebi.ac.uk/pride/data/archive/2015/10/PXD002914/"

PRIMARY = {
    "name": "ph050213_18.raw",
    "role": "primary",
    "bytes": 9961477,
    "sha256": "b5ebbb2e24dcbdbeeb44ddff690c102f7901a43c5d004edc501dadf9c9e01c6c",
    "d019_psms": 906,
    "d019_peptides": 787,
    "ms2_scans": 2060,
    "ref_pin_rows": 18120,
    "ref_pin_targets": 9885,
    "ref_pin_decoys": 8235,
    "ref_spectra_loaded": 3624,
    "empty_ms1": "1032",
    "empty_ms2": "9",
}
FALLBACK = {
    "name": "ph280213_36.raw",
    "role": "fallback",
    "bytes": 8796695,
    "sha256": "c283a0ca7d5f2f6631abf5c2cc08be19f725b7bd6af7707fd00c70c68f25572c",
    "d019_psms": 509,
    "d019_peptides": 467,
    "ms2_scans": 2562,
    "ref_pin_rows": 21595,
    "ref_pin_targets": 11449,
    "ref_pin_decoys": 10146,
    "ref_spectra_loaded": 4320,
    "empty_ms1": "not recorded per file (the report gives 21 to 103)",
    "empty_ms2": "not recorded per file (the report gives 11 to 17)",
}

# NOT D-019 figures.  D-019's counts are Comet 2026.02.2's.  Phase 16 unit 1
# searched the same two converted mzML files on Linux with Comet 2026.03.0 and
# Percolator 3.07.1 on 2026-11-12, same parameters, and these are what it
# counted.  They are printed beside the D-019 expectation so that a difference
# between Comet releases is not read as a difference between the Windows RAW
# reader and the Linux conversion.
LINUX_MZML_SAME_RELEASE = {
    ("2026.02.2", "ph050213_18.raw"): (906, 787),
    ("2026.02.2", "ph280213_36.raw"): (509, 467),
    ("2026.03.0", "ph050213_18.raw"): (920, 797),
    ("2026.03.0", "ph280213_36.raw"): (517, 477),
}

# The organism's proteome, committed with its attribution (decision P16-7).
# The figures are the search report's ("FASTA files used") and
# fixtures/synechococcus-wh8102/ATTRIBUTION.txt's.
FASTA_RELPATH = ("fixtures", "synechococcus-wh8102", "UP000001422_84588.fasta")
FASTA_BYTES = 1080427
FASTA_SHA256 = "b4c8aaf48778566e87b0517af6f861bf0b93b33a3ebd95fd071bab9a6dd1750d"
FASTA_RECORDS = 2513

# The base parameter file the RAW search started from.  Its digest is taken
# over the text with line endings normalised to LF, because a Windows checkout
# may hand it over with CRLF; if the committed file is ever edited, this trial
# stops rather than search with parameters nobody measured.
BASE_PARAMS_RELPATH = ("scripts", "feasibility", "comet.params")
BASE_PARAMS_LF_SHA256 = "da0da57e336343c559e6c6b930f77fe29fa3547738bc422c5b2ec7f7c61bb470"

# D-019's seven values, in the order the search applied them.
D019_OVERRIDES = (
    ("peptide_mass_tolerance_upper", "3.0"),
    ("peptide_mass_tolerance_lower", "-3.0"),
    ("peptide_mass_units", "0"),
    ("isotope_error", "0"),
    ("fragment_bin_tol", "1.0005"),
    ("fragment_bin_offset", "0.4"),
    ("theoretical_fragment_ions", "1"),
)

Q_THRESHOLD = 0.01

# READ means the search produced a result set of a size only a real read can
# give: a thousand PIN rows is about 5% of what the Linux search of either
# file wrote (18 120 and 21 595), and 200 pepXML spectrum queries is the same
# bar at five result lines per query.
MIN_PIN_ROWS = 1000
MIN_SPECTRUM_QUERIES = 200

TIMEOUT_DOWNLOAD = 300
TIMEOUT_COMET_P = 180
TIMEOUT_COMET = 1200
TIMEOUT_PERCOLATOR = 900

# What a line of Comet's output must mention to be repeated in the result
# block.  Deliberately wide: the known risks are empty scans and missing
# precursor charges, and nobody knows what words Comet uses for either.
COMET_SAID = re.compile(
    r"warn|error|fail|cannot|could not|unable|exception|unhandled|invalid|"
    r"empty|no peaks|no spectra|zero|skip|charge|precursor|thermo|\.dll|"
    r"not loaded|out of date", re.IGNORECASE)

NTSTATUS = {
    0xC0000005: "STATUS_ACCESS_VIOLATION",
    0xC000007B: "STATUS_INVALID_IMAGE_FORMAT",
    0xC0000135: "STATUS_DLL_NOT_FOUND",
    0xC0000139: "STATUS_ENTRYPOINT_NOT_FOUND",
    0xC0000409: "STATUS_STACK_BUFFER_OVERRUN (fail-fast)",
    0xE0434352: "an unhandled .NET exception",
}


class NoVerdict(Exception):
    """The trial cannot reach a verdict; nothing was learned about Comet."""

    def __init__(self, summary, detail=None):
        Exception.__init__(self, summary)
        self.summary = summary
        self.detail = list(detail or [])


# --------------------------------------------------------------------------
# Transcript
# --------------------------------------------------------------------------

def ascii_safe(text):
    """ASCII only, LF only, no control character but tab and newline.

    Comet draws its progress with backspaces and Percolator prints a non-ASCII
    author name; a CI console is whatever code page the image picked.
    Escaping on the way in means the transcript cannot die of an encoding
    error half way through a result.  Nothing is lost: each such character
    becomes a backslash escape.
    """
    if isinstance(text, bytes):
        text = text.decode("utf-8", "replace")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[\x00-\x08\x0b-\x1f\x7f]",
                  lambda m: "\\x%02x" % ord(m.group()), text)
    return text.encode("ascii", "backslashreplace").decode("ascii")


class Transcript(object):
    """Writes as it goes and echoes to stdout, so the job log carries it."""

    def __init__(self, path=None, echo=True):
        self.path = path
        self.echo = echo
        self.handle = None
        if path:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            self.handle = open(path, "w", encoding="ascii",
                               errors="backslashreplace", newline="\n")

    def log(self, text=""):
        for line in ascii_safe(text).split("\n"):
            line = line.rstrip()
            if self.handle is not None:
                self.handle.write(line + "\n")
            if self.echo:
                sys.stdout.write(line + "\n")
        if self.handle is not None:
            self.handle.flush()
        if self.echo:
            sys.stdout.flush()

    def section(self, title):
        self.log()
        self.log("=" * 74)
        self.log(title)
        self.log("=" * 74)

    def kv(self, key, value):
        self.log("  %-30s %s" % (key + ":", value))

    def block(self, title, text):
        self.log("  %s" % title)
        body = ascii_safe(text)
        if body.strip("\n") == "":
            self.log("    | (empty -- nothing was captured)")
            return
        for line in body.rstrip("\n").split("\n"):
            self.log("    | " + line)

    def close(self):
        if self.handle is not None:
            self.handle.close()
            self.handle = None


# --------------------------------------------------------------------------
# Small helpers
# --------------------------------------------------------------------------

def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fmt_exit(code):
    """Decimal and hex: a crashed Windows process returns its NTSTATUS."""
    if code is None:
        return "none (it timed out or never started)"
    text = "%d (0x%08X)" % (code, code & 0xFFFFFFFF)
    name = NTSTATUS.get(code & 0xFFFFFFFF)
    if name:
        text += " -- %s" % name
    if code < 0 and not IS_WINDOWS:
        text += " -- killed by signal %d" % (-code,)
    return text


def version_key(version):
    return tuple(int(part) if part.isdigit() else part
                 for part in re.split(r"[.\-]", version))


def run_process(argv, cwd, timeout, t):
    """Launch with an ARGUMENT ARRAY and a timeout.  Never a shell."""
    rec = {"argv": [str(a) for a in argv], "cwd": str(cwd),
           "timeout_s": timeout, "timed_out": False, "launch_error": None,
           "exit_code": None, "stdout": "", "stderr": "", "seconds": 0.0}
    started = time.time()
    t.log("  launched (argument array, no shell):")
    for index, arg in enumerate(rec["argv"]):
        t.log("      argv[%d] = %r" % (index, arg))
    t.kv("working directory", rec["cwd"])
    try:
        proc = subprocess.Popen(rec["argv"], cwd=str(cwd),
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                stdin=subprocess.DEVNULL)
    except OSError as exc:
        rec["launch_error"] = "%s: %s" % (type(exc).__name__, exc)
        rec["seconds"] = time.time() - started
        t.kv("LAUNCH FAILED", rec["launch_error"])
        return rec
    try:
        out, err = proc.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        proc.kill()
        out, err = proc.communicate()
        rec["timed_out"] = True
    else:
        rec["exit_code"] = proc.returncode
    rec["seconds"] = time.time() - started
    rec["stdout"] = ascii_safe(out or b"")
    rec["stderr"] = ascii_safe(err or b"")
    t.kv("wall clock", "%.1f s (timeout %d s)" % (rec["seconds"], timeout))
    if rec["timed_out"]:
        t.kv("TIMED OUT", "killed after %d s; a timeout is not a pass" % timeout)
    t.kv("exit code", fmt_exit(rec["exit_code"]))
    t.block("stdout, in full (%d bytes):" % len(rec["stdout"]), rec["stdout"])
    t.block("stderr, in full (%d bytes):" % len(rec["stderr"]), rec["stderr"])
    return rec


def combined(rec):
    return rec["stdout"] + "\n" + rec["stderr"]


# --------------------------------------------------------------------------
# Downloads: the pin is a hard stop
# --------------------------------------------------------------------------

def fetch(pin, dest, t, attempts=5):
    """Download pin["url"] to dest in binary mode; require its size and SHA-256.

    A mismatch is never retried, never warned about and never continued past:
    a retry could turn a substitution into something that looks transient.
    Only a transport error, or a body shorter than the server's own
    Content-Length, is retried.
    """
    url, want_sha, want_bytes = pin["url"], pin["sha256"], pin["bytes"]
    t.log("  %s" % pin["label"])
    t.kv("    url", url)
    t.kv("    saved as", dest)
    if os.path.isfile(dest):
        have = sha256_file(dest)
        if have == want_sha and os.path.getsize(dest) == want_bytes:
            t.kv("    source", "already in the work directory")
            _report_pin(t, os.path.getsize(dest), want_bytes, have, want_sha)
            return
        t.kv("    stale copy", "sha256 %s -- downloading again" % have)
    data, last_error = None, None
    for attempt in range(1, attempts + 1):
        try:
            request = urllib.request.Request(
                url, headers={"User-Agent": "CometGUI-windows-raw-trial"})
            with urllib.request.urlopen(request, timeout=TIMEOUT_DOWNLOAD) as response:
                declared = response.headers.get("Content-Length")
                body = response.read()
            if declared is not None and int(declared) != len(body):
                raise IOError("short read: Content-Length %s, got %d bytes"
                              % (declared, len(body)))
            data = body
            break
        except (urllib.error.URLError, http.client.HTTPException, OSError,
                ValueError) as exc:
            last_error = "%s: %s" % (type(exc).__name__, exc)
            t.kv("    attempt %d/%d failed" % (attempt, attempts), last_error)
            if attempt < attempts:
                time.sleep(min(2 ** attempt, 30))
    if data is None:
        raise NoVerdict("download failed: %s" % url,
                        ["last error: %s" % last_error])
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as handle:
        handle.write(data)
    observed = hashlib.sha256(data).hexdigest()
    t.kv("    source", "network")
    _report_pin(t, len(data), want_bytes, observed, want_sha)
    if observed != want_sha or len(data) != want_bytes:
        raise NoVerdict(
            "PIN MISMATCH for %s -- HARD STOP" % pin["label"],
            ["url      %s" % url,
             "bytes    expected %d, observed %d" % (want_bytes, len(data)),
             "sha256   expected %s" % want_sha,
             "sha256   observed %s" % observed,
             "These are not the bytes the pin describes."])


def _report_pin(t, got_bytes, want_bytes, got_sha, want_sha):
    t.kv("    bytes", "%d (pinned %d) %s"
         % (got_bytes, want_bytes, "match" if got_bytes == want_bytes else "MISMATCH"))
    t.kv("    sha256", got_sha)
    t.kv("    sha256 pinned", "%s %s"
         % (want_sha, "match" if got_sha == want_sha else "MISMATCH"))


def copy_verified(src, dest, sha256, t=None):
    """Copy, then prove the copy is the pinned bytes."""
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copyfile(src, dest)
    observed = sha256_file(dest)
    if sha256 is not None and observed != sha256:
        raise NoVerdict("the copy at %s is not the pinned file" % dest,
                        ["expected %s" % sha256, "observed %s" % observed])
    if IS_WINDOWS:
        # A file marked as coming from the internet is one the .NET Framework
        # may refuse to load.  urllib does not write that mark; removing a
        # mark that is not there is harmless, and says so if one was.
        try:
            os.remove(dest + ":Zone.Identifier")
            if t is not None:
                t.kv("    note", "removed a Zone.Identifier mark from %s" % dest)
        except OSError:
            pass
    return observed


# --------------------------------------------------------------------------
# The manifest
# --------------------------------------------------------------------------

def _need(mapping, key, where):
    if key not in mapping or mapping[key] in (None, ""):
        raise NoVerdict("manifests/tools.json: %s has no %r" % (where, key))
    return mapping[key]


def load_manifest():
    path = os.path.join(PROJECT_ROOT, *MANIFEST_RELPATH)
    try:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
    except (OSError, ValueError) as exc:
        raise NoVerdict("cannot read %s" % path, ["%s: %s" % (type(exc).__name__, exc)])
    artefacts = document.get("artefacts")
    if not isinstance(artefacts, list):
        raise NoVerdict("manifests/tools.json has no artefacts list")
    return artefacts


def comet_releases(artefacts):
    """Every Comet release the manifest offers for windows/x86-64."""
    releases = []
    for row in artefacts:
        if (row.get("tool"), row.get("os"), row.get("arch")) != (
                "comet", MANIFEST_OS, MANIFEST_ARCH):
            continue
        version = _need(row, "version", "a Windows Comet row")
        where = "comet %s windows" % version
        if row.get("kind") != "BARE_EXECUTABLE":
            raise NoVerdict("%s: kind is %r, and this trial only understands a "
                            "bare executable" % (where, row.get("kind")))
        url = _need(row, "url", where)
        files = [{"label": "Comet %s: %s" % (version, os.path.basename(url)),
                  "url": url, "bytes": _need(row, "sizeBytes", where),
                  "sha256": _need(row, "sha256", where),
                  "name": os.path.basename(url), "companion": False}]
        skipped = []
        for companion in row.get("companions", []):
            cid = _need(companion, "id", where + " companion")
            if not companion.get("runtimePrerequisite"):
                skipped.append(cid)
                continue
            members = companion.get("members", [])
            if companion.get("kind") != "BARE_EXECUTABLE" or len(members) != 1:
                raise NoVerdict("%s: companion %s is not a single bare file; "
                                "this trial will not guess how to install it"
                                % (where, cid))
            member = members[0]
            name = os.path.basename(_need(member, "installedPath", cid))
            curl = _need(companion, "url", cid)
            if (member.get("sha256") != companion.get("sha256")
                    or member.get("sizeBytes") != companion.get("sizeBytes")
                    or os.path.basename(curl) != name):
                raise NoVerdict("%s: companion %s and its one member disagree "
                                "about name, size or SHA-256" % (where, cid))
            files.append({"label": "Comet %s companion: %s" % (version, name),
                          "url": curl, "bytes": _need(companion, "sizeBytes", cid),
                          "sha256": _need(companion, "sha256", cid),
                          "name": name, "companion": True,
                          "gates": companion.get("gatesCapability")})
        releases.append({
            "version": version, "files": files, "skipped": skipped,
            "host_libraries": row.get("minimumHostRequirements", {})
                                 .get("requiredHostLibraries", [])})
    releases.sort(key=lambda r: version_key(r["version"]))
    if not releases:
        raise NoVerdict("manifests/tools.json offers no Comet for %s/%s"
                        % (MANIFEST_OS, MANIFEST_ARCH))
    return releases


def percolator_pin(artefacts):
    rows = [row for row in artefacts
            if (row.get("tool"), row.get("version"), row.get("os"), row.get("arch"))
            == ("percolator", PERCOLATOR_VERSION, MANIFEST_OS, MANIFEST_ARCH)]
    if len(rows) != 1:
        raise NoVerdict("manifests/tools.json has %d rows for Percolator %s on "
                        "%s/%s; exactly one was expected"
                        % (len(rows), PERCOLATOR_VERSION, MANIFEST_OS, MANIFEST_ARCH))
    row = rows[0]
    where = "percolator %s windows" % PERCOLATOR_VERSION
    if row.get("kind") != "ZIP":
        raise NoVerdict("%s: kind is %r, expected ZIP" % (where, row.get("kind")))
    url = _need(row, "url", where)
    return {
        "label": "Percolator %s: %s" % (PERCOLATOR_VERSION, os.path.basename(url)),
        "url": url, "bytes": _need(row, "sizeBytes", where),
        "sha256": _need(row, "sha256", where), "name": os.path.basename(url),
        "member": _need(row, "member", where),
        "member_bytes": _need(row, "memberSizeBytes", where),
        "member_sha256": _need(row, "memberSha256", where),
        "installed_name": os.path.basename(_need(row, "installedPath", where)),
        "skipped": [c.get("id") for c in row.get("companions", [])
                    if not c.get("runtimePrerequisite")],
        "required": [c.get("id") for c in row.get("companions", [])
                     if c.get("runtimePrerequisite")],
        "host_libraries": row.get("minimumHostRequirements", {})
                             .get("requiredHostLibraries", [])}


def raw_pin(spec):
    return {"label": "RAW file (%s, D-019): %s" % (spec["role"], spec["name"]),
            "url": PRIDE_DIRECTORY + spec["name"], "bytes": spec["bytes"],
            "sha256": spec["sha256"], "name": spec["name"]}


# --------------------------------------------------------------------------
# Installing the tools into the work directory
# --------------------------------------------------------------------------

def install_comet(release, work_dir, t):
    """Download one release and lay it out side by side.  Returns the exe."""
    version = release["version"]
    download_dir = os.path.join(work_dir, "downloads", "comet-%s" % version)
    bin_dir = os.path.join(work_dir, "bin", "comet-%s" % version)
    if os.path.isdir(bin_dir):
        shutil.rmtree(bin_dir)
    for item in release["files"]:
        dest = os.path.join(download_dir, item["name"])
        fetch(item, dest, t)
        copy_verified(dest, os.path.join(bin_dir, item["name"]), item["sha256"], t)
    for cid in release["skipped"]:
        t.kv("  not fetched", "%s (the manifest says it is not a runtime "
                              "prerequisite)" % cid)
    t.kv("  installed side by side in", bin_dir)
    t.kv("  directory holds", ", ".join(sorted(os.listdir(bin_dir))))
    release["bin_dir"] = bin_dir
    release["exe"] = os.path.join(bin_dir, release["files"][0]["name"])
    return release["exe"]


def install_percolator(pin, work_dir, t):
    archive_path = os.path.join(work_dir, "downloads",
                                "percolator-%s" % PERCOLATOR_VERSION, pin["name"])
    fetch(pin, archive_path, t)
    if pin["required"]:
        raise NoVerdict("the manifest now names a runtime companion for "
                        "Percolator (%s) that this trial does not install"
                        % ", ".join(pin["required"]))
    try:
        with zipfile.ZipFile(archive_path) as archive:
            names = archive.namelist()
            if pin["member"] not in names:
                raise NoVerdict("no member %r in %s" % (pin["member"], pin["name"]),
                                ["members: %r" % names])
            # Written to a path this program chose: a member name is untrusted.
            blob = archive.read(pin["member"])
    except zipfile.BadZipFile as exc:
        raise NoVerdict("%s is not a readable zip: %s" % (pin["name"], exc))
    observed = hashlib.sha256(blob).hexdigest()
    t.kv("    member", "%s, %d bytes (pinned %d)"
         % (pin["member"], len(blob), pin["member_bytes"]))
    t.kv("    member sha256", observed)
    t.kv("    member sha256 pinned", "%s %s"
         % (pin["member_sha256"],
            "match" if observed == pin["member_sha256"] else "MISMATCH"))
    if observed != pin["member_sha256"] or len(blob) != pin["member_bytes"]:
        raise NoVerdict("PIN MISMATCH for the %s member of %s -- HARD STOP"
                        % (pin["member"], pin["name"]))
    bin_dir = os.path.join(work_dir, "bin", "percolator-%s" % PERCOLATOR_VERSION)
    if os.path.isdir(bin_dir):
        shutil.rmtree(bin_dir)
    os.makedirs(bin_dir)
    exe = os.path.join(bin_dir, pin["installed_name"])
    with open(exe, "wb") as handle:
        handle.write(blob)
    for cid in pin["skipped"]:
        t.kv("  not fetched", "%s (the manifest says it is not a runtime "
                              "prerequisite)" % cid)
    t.kv("  installed as", exe)
    return exe


# --------------------------------------------------------------------------
# The FASTA and the parameter file
# --------------------------------------------------------------------------

def verify_fasta(t):
    path = os.path.join(PROJECT_ROOT, *FASTA_RELPATH)
    if not os.path.isfile(path):
        raise NoVerdict("the committed FASTA is missing: %s" % path)
    with open(path, "rb") as handle:
        data = handle.read()
    observed = hashlib.sha256(data).hexdigest()
    records = sum(1 for line in data.split(b"\n") if line.startswith(b">"))
    carriage_returns = data.count(b"\r")
    t.kv("  FASTA", path)
    t.kv("    bytes", "%d (pinned %d)" % (len(data), FASTA_BYTES))
    t.kv("    sha256", observed)
    t.kv("    sha256 pinned", "%s %s" % (
        FASTA_SHA256, "match" if observed == FASTA_SHA256 else "MISMATCH"))
    t.kv("    records", "%d (pinned %d)" % (records, FASTA_RECORDS))
    t.kv("    carriage returns", carriage_returns)
    if (observed != FASTA_SHA256 or len(data) != FASTA_BYTES
            or records != FASTA_RECORDS):
        detail = []
        if carriage_returns:
            detail.append("It holds %d carriage returns: the checkout converted "
                          "its line endings.  .gitattributes must mark "
                          "fixtures/** as -text." % carriage_returns)
        raise NoVerdict("the committed FASTA is not the pinned file", detail)
    return path


def read_base_params():
    path = os.path.join(PROJECT_ROOT, *BASE_PARAMS_RELPATH)
    try:
        with open(path, "rb") as handle:
            data = handle.read()
    except OSError as exc:
        raise NoVerdict("cannot read %s: %s" % (path, exc))
    data = data.replace(b"\r\n", b"\n")
    observed = hashlib.sha256(data).hexdigest()
    if observed != BASE_PARAMS_LF_SHA256:
        raise NoVerdict(
            "%s is not the file the RAW search started from" % "/".join(BASE_PARAMS_RELPATH),
            ["sha256 (LF) expected %s" % BASE_PARAMS_LF_SHA256,
             "sha256 (LF) observed %s" % observed])
    return data.decode("utf-8")


def build_params(base_text, database_path):
    """The base file, database_name replaced, and each D-019 value replacing
    its whole line -- what scratch/raw-scout/scripts/eval_raw.sh did with sed.

    Returns (text, changes); changes is a list of (line number, old, new).
    """
    if re.search(r"[\s#]", database_path):
        raise NoVerdict(
            "the database path %r holds whitespace or '#', which a Comet "
            "parameter line cannot be trusted to carry; use --work-dir to "
            "choose a directory without either" % database_path)
    replacements = [("database_name", database_path)] + list(D019_OVERRIDES)
    lines = base_text.split("\n")
    changes = []
    for name, value in replacements:
        prefix = name + " = "
        hits = [i for i, line in enumerate(lines) if line.startswith(prefix)]
        if len(hits) != 1:
            raise NoVerdict("the base parameter file has %d lines starting %r; "
                            "exactly one was expected" % (len(hits), prefix))
        new = prefix + value
        changes.append((hits[0] + 1, lines[hits[0]], new))
        lines[hits[0]] = new
    return "\n".join(lines), changes


def masked_params_digest(text):
    """SHA-256 with the database path masked: equal on every machine."""
    masked = re.sub(r"(?m)^database_name = .*$", "database_name = <FASTA>", text)
    return hashlib.sha256(masked.encode("utf-8")).hexdigest()


def write_params(path, database_path, t):
    text, changes = build_params(read_base_params(), database_path)
    with open(path, "wb") as handle:
        handle.write(text.encode("utf-8"))
    t.kv("  parameter file", path)
    t.kv("    base", "/".join(BASE_PARAMS_RELPATH) + " (sha256 over LF text matches its pin)")
    t.kv("    lines changed", "%d (database_name and D-019's seven)" % len(changes))
    for number, old, new in changes:
        t.log("      line %3d  - %s" % (number, old))
        t.log("                + %s" % new)
    t.kv("    sha256, database masked", masked_params_digest(text))
    return text


def parse_params(text):
    """name -> value for every parameter line, and the enzyme table."""
    values, enzymes, in_enzymes = {}, [], False
    for line in text.replace("\r\n", "\n").split("\n"):
        if line.strip() == "[COMET_ENZYME_INFO]":
            in_enzymes = True
            continue
        if in_enzymes:
            if line.strip():
                enzymes.append(" ".join(line.split()))
            continue
        body = line.split("#", 1)[0]
        if "=" not in body:
            continue
        name, value = body.split("=", 1)
        values[name.strip()] = " ".join(value.split())
    return values, enzymes


def compare_params(ours_text, new_text):
    """Every way our file differs from what `comet -p` wrote."""
    ours, ours_enzymes = parse_params(ours_text)
    new, new_enzymes = parse_params(new_text)
    rows = []
    for name in sorted(set(ours) | set(new)):
        if name not in new:
            rows.append((name, ours[name], "(not written by comet -p)"))
        elif name not in ours:
            rows.append((name, "(absent from our file: Comet's default applies)",
                         new[name]))
        elif ours[name] != new[name]:
            rows.append((name, ours[name], new[name]))
    return rows, ours_enzymes == new_enzymes


# --------------------------------------------------------------------------
# Reading what the tools wrote
# --------------------------------------------------------------------------

SPEC_ID = re.compile(r"_(\d+)_(\d+)_(\d+)$")


def analyse_pin(path):
    info = {"path": path, "exists": os.path.isfile(path), "bytes": 0, "rows": 0,
            "targets": 0, "decoys": 0, "other_labels": 0, "scans": 0,
            "spectra": 0, "rank1_targets": 0, "rank1_decoys": 0,
            "charges": {}, "header_ok": False, "problem": None}
    if not info["exists"]:
        return info
    info["bytes"] = os.path.getsize(path)
    scans, spectra = set(), set()
    with open(path, "r", encoding="utf-8", errors="replace", newline=None) as handle:
        header = handle.readline().rstrip("\n").split("\t")
        try:
            id_col, label_col, scan_col = (header.index("SpecId"),
                                           header.index("Label"),
                                           header.index("ScanNr"))
        except ValueError:
            info["problem"] = "the first line is not a PIN header: %r" % header[:6]
            return info
        info["header_ok"] = True
        for line in handle:
            cols = line.rstrip("\n").split("\t")
            if len(cols) <= max(id_col, label_col, scan_col):
                continue
            info["rows"] += 1
            label = cols[label_col].strip()
            if label == "1":
                info["targets"] += 1
            elif label == "-1":
                info["decoys"] += 1
            else:
                info["other_labels"] += 1
            scans.add(cols[scan_col].strip())
            match = SPEC_ID.search(cols[id_col].strip())
            if match:
                spectra.add((match.group(1), match.group(2)))
                if match.group(3) == "1":
                    if label == "1":
                        info["rank1_targets"] += 1
                    elif label == "-1":
                        info["rank1_decoys"] += 1
    info["scans"] = len(scans)
    info["spectra"] = len(spectra)
    info["charges"] = dict(Counter(charge for _scan, charge in spectra))
    return info


def count_spectrum_queries(path):
    info = {"path": path, "exists": os.path.isfile(path), "bytes": 0, "queries": 0}
    if not info["exists"]:
        return info
    info["bytes"] = os.path.getsize(path)
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            info["queries"] += line.count("<spectrum_query ")
    return info


def count_table(path):
    """Rows with q-value < 0.01, as the search report counted them: the third
    column of the table Percolator wrote, compared as a number."""
    info = {"path": path, "exists": os.path.isfile(path), "bytes": 0, "rows": 0,
            "q01": 0, "unparsed": 0, "scans_q01": 0, "problem": None}
    if not info["exists"]:
        return info
    info["bytes"] = os.path.getsize(path)
    scans = set()
    with open(path, "r", encoding="utf-8", errors="replace", newline=None) as handle:
        header = handle.readline().rstrip("\n").split("\t")
        if len(header) < 3 or header[2] != "q-value":
            info["problem"] = "column 3 is not q-value: header %r" % header[:6]
            return info
        for line in handle:
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 3:
                continue
            info["rows"] += 1
            try:
                q_value = float(cols[2])
            except ValueError:
                info["unparsed"] += 1
                continue
            if q_value < Q_THRESHOLD:
                info["q01"] += 1
                match = SPEC_ID.search(cols[0])
                if match:
                    scans.add(match.group(1))
    info["scans_q01"] = len(scans)
    return info


def list_directory(path, t, title):
    t.log("  %s" % title)
    names = sorted(os.listdir(path))
    if not names:
        t.log("      (empty)")
    for name in names:
        full = os.path.join(path, name)
        if os.path.isdir(full):
            t.log("      %12s  %s/" % ("<dir>", name))
        else:
            t.log("      %12d  %s" % (os.path.getsize(full), name))
    return names


# --------------------------------------------------------------------------
# One search: Comet, the evidence, Percolator, the counts
# --------------------------------------------------------------------------

def comet_banner(rec):
    match = re.search(r'Comet version "([^"]+)"', combined(rec))
    return match.group(1) if match else None


def said_lines(rec):
    lines = []
    for line in combined(rec).split("\n"):
        text = " ".join(line.split())
        if text and COMET_SAID.search(text):
            lines.append(text)
    return lines


def run_search(t, comet_exe, percolator_exe, input_path, input_sha256,
               fasta_path, run_dir):
    """Search one file with one Comet.  Returns everything observed.

    run_dir is created empty.  Nothing in the returned record is taken from
    an exit code: every count is read from a file this function saw appear.
    """
    out = {"read": "NOT-READ", "read_reason": "", "percolator": "NOT-RUN",
           "percolator_reason": "", "comet": None, "perc": None,
           "pin": None, "pepxml": None, "tables": {}, "weights_bytes": 0,
           "banner": None, "loaded": None, "said": [], "params_text": None}
    if os.path.isdir(run_dir):
        shutil.rmtree(run_dir)
    os.makedirs(run_dir)
    input_name = os.path.basename(input_path)
    stem = os.path.splitext(input_name)[0]
    copy_verified(input_path, os.path.join(run_dir, input_name), input_sha256)
    fasta_copy = os.path.join(run_dir, os.path.basename(fasta_path))
    copy_verified(fasta_path, fasta_copy, FASTA_SHA256)
    params_path = os.path.join(run_dir, "comet.params")
    out["params_text"] = write_params(params_path, fasta_copy, t)

    before = list_directory(run_dir, t, "search directory before Comet:")
    stale = [name for name in before if name.lower().endswith((".pin", ".pep.xml"))]
    if stale:
        raise NoVerdict("the fresh search directory already holds %r" % stale)

    t.log()
    t.log("  -- Comet: %s" % input_name)
    rec = run_process([comet_exe, "-P" + params_path, input_name], run_dir,
                      TIMEOUT_COMET, t)
    out["comet"] = rec
    out["banner"] = comet_banner(rec)
    out["said"] = said_lines(rec)
    loaded = [int(n) for n in re.findall(r"Load spectra:\s*(\d+)", combined(rec))]
    out["loaded"] = sum(loaded) if loaded else None
    for name, text in (("comet.stdout.txt", rec["stdout"]),
                       ("comet.stderr.txt", rec["stderr"])):
        with open(os.path.join(run_dir, name), "w", encoding="ascii",
                  newline="\n") as handle:
            handle.write(text)
    after = list_directory(run_dir, t, "search directory after Comet:")

    def find_output(suffix):
        expected = stem + suffix
        if expected in after:
            return os.path.join(run_dir, expected)
        others = [n for n in after if n.lower().endswith(suffix)]
        if others:
            t.kv("  NOTE", "no %s; Comet wrote %r instead" % (expected, others))
            return os.path.join(run_dir, others[0])
        return os.path.join(run_dir, expected)

    pin = analyse_pin(find_output(".pin"))
    pepxml = count_spectrum_queries(find_output(".pep.xml"))
    out["pin"], out["pepxml"] = pin, pepxml
    t.kv("  Comet banner", out["banner"] or "NONE -- Comet did not print one")
    t.kv("  spectra Comet loaded", out["loaded"] if out["loaded"] is not None
         else "no 'Load spectra: <count>' line in its output")
    t.kv("  PIN", "%s: exists=%s, %d bytes" % (pin["path"], pin["exists"], pin["bytes"]))
    if pin["problem"]:
        t.kv("  PIN problem", pin["problem"])
    t.kv("  PIN data rows", "%d (target %d, decoy %d, other %d)"
         % (pin["rows"], pin["targets"], pin["decoys"], pin["other_labels"]))
    t.kv("  PIN distinct scans", pin["scans"])
    t.kv("  PIN scan/charge pairs", "%d, by charge: %s"
         % (pin["spectra"], fmt_charges(pin["charges"])))
    t.kv("  PIN rank-1 rows", "target %d, decoy %d"
         % (pin["rank1_targets"], pin["rank1_decoys"]))
    t.kv("  pepXML", "%s: exists=%s, %d bytes, %d spectrum queries"
         % (pepxml["path"], pepxml["exists"], pepxml["bytes"], pepxml["queries"]))

    pin_ok = (pin["header_ok"] and pin["rows"] >= MIN_PIN_ROWS
              and pin["targets"] > 0 and pin["decoys"] > 0)
    if pin_ok:
        out["read"] = "READ"
        out["read_reason"] = ("a PIN of %d data rows (%d target, %d decoy), "
                              "written by this run"
                              % (pin["rows"], pin["targets"], pin["decoys"]))
    elif pepxml["queries"] >= MIN_SPECTRUM_QUERIES:
        out["read"] = "READ"
        out["read_reason"] = ("a pepXML of %d spectrum queries, but no usable "
                              "PIN (%d rows)" % (pepxml["queries"], pin["rows"]))
    else:
        why = []
        if rec["launch_error"]:
            why.append("Comet could not be launched (%s)" % rec["launch_error"])
        elif rec["timed_out"]:
            why.append("Comet timed out after %d s" % rec["timeout_s"])
        elif out["banner"] is None:
            why.append("Comet printed no version banner and exited %s: it did "
                       "not start" % fmt_exit(rec["exit_code"]))
        else:
            why.append("Comet started (banner present) and exited %s"
                       % fmt_exit(rec["exit_code"]))
        why.append("PIN rows %d (need %d with targets and decoys), pepXML "
                   "spectrum queries %d (need %d)"
                   % (pin["rows"], MIN_PIN_ROWS, pepxml["queries"],
                      MIN_SPECTRUM_QUERIES))
        out["read_reason"] = "; ".join(why)
    t.kv("  READ VERDICT", "%s -- %s" % (out["read"], out["read_reason"]))

    if not pin_ok:
        out["percolator_reason"] = "there is no usable PIN to give it"
        t.kv("  Percolator", "not run: %s" % out["percolator_reason"])
        return out

    t.log()
    t.log("  -- Percolator %s over %s" % (PERCOLATOR_VERSION,
                                          os.path.basename(pin["path"])))
    perc_dir = os.path.join(run_dir, "percolator")
    os.makedirs(perc_dir)
    paths = {name: os.path.join(perc_dir, name + ".txt")
             for name in ("psms.target", "psms.decoy",
                          "peptides.target", "peptides.decoy")}
    weights = os.path.join(perc_dir, "weights.txt")
    prec = run_process([percolator_exe,
                        "-m", paths["psms.target"], "-M", paths["psms.decoy"],
                        "-r", paths["peptides.target"], "-B", paths["peptides.decoy"],
                        "-w", weights, pin["path"]], run_dir, TIMEOUT_PERCOLATOR, t)
    out["perc"] = prec
    list_directory(perc_dir, t, "what Percolator wrote:")
    for name, path in paths.items():
        out["tables"][name] = table = count_table(path)
        t.kv("  %s" % name, "exists=%s, %d bytes, %d rows, %d with q < %s%s"
             % (table["exists"], table["bytes"], table["rows"], table["q01"],
                Q_THRESHOLD, "; " + table["problem"] if table["problem"] else ""))
    out["weights_bytes"] = os.path.getsize(weights) if os.path.isfile(weights) else 0
    t.kv("  weights.txt", "%d bytes" % out["weights_bytes"])
    psms, peptides = out["tables"]["psms.target"], out["tables"]["peptides.target"]
    started = "Percolator version" in combined(prec)
    trained = (started and out["weights_bytes"] > 0
               and psms["rows"] > 0 and peptides["rows"] > 0
               and not psms["problem"] and not peptides["problem"])
    if not trained:
        out["percolator"] = "FAILED"
        out["percolator_reason"] = (
            "banner %s, exit %s, weights %d bytes, target PSM rows %d, target "
            "peptide rows %d" % ("present" if started else "ABSENT",
                                 fmt_exit(prec["exit_code"]), out["weights_bytes"],
                                 psms["rows"], peptides["rows"]))
    elif psms["q01"] == 0 or peptides["q01"] == 0:
        out["percolator"] = "TRAINED-NOTHING-AT-1PCT"
        out["percolator_reason"] = ("it trained and wrote both tables, but "
                                    "%d PSMs and %d peptides have q < %s"
                                    % (psms["q01"], peptides["q01"], Q_THRESHOLD))
    else:
        out["percolator"] = "TRAINED"
        out["percolator_reason"] = ("weights written, %d target PSM rows and %d "
                                    "target peptide rows"
                                    % (psms["rows"], peptides["rows"]))
    t.kv("  PERCOLATOR VERDICT", "%s -- %s" % (out["percolator"],
                                               out["percolator_reason"]))
    t.kv("  target PSMs, q < %s" % Q_THRESHOLD,
         "%d (from %d distinct scans)" % (psms["q01"], psms["scans_q01"]))
    t.kv("  target peptides, q < %s" % Q_THRESHOLD, peptides["q01"])
    return out


def fmt_charges(charges):
    if not charges:
        return "none"
    return ",".join("%s+:%d" % (charge, charges[charge])
                    for charge in sorted(charges, key=int))


def params_new(t, comet_exe, directory):
    """Run `comet -p` and return what it wrote, or None."""
    if os.path.isdir(directory):
        shutil.rmtree(directory)
    os.makedirs(directory)
    rec = run_process([comet_exe, "-p"], directory, TIMEOUT_COMET_P, t)
    path = os.path.join(directory, "comet.params.new")
    list_directory(directory, t, "what `comet -p` wrote:")
    if not os.path.isfile(path) or os.path.getsize(path) == 0:
        return rec, None
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        return rec, handle.read()


# --------------------------------------------------------------------------
# The host, and what the managed code will ask it for
# --------------------------------------------------------------------------

def assembly_references(path):
    """(runtime version, [(name, version)]) of a .NET assembly, or None.

    Reads the AssemblyRef table of the CLI metadata (ECMA-335, II.22.5 and
    II.24).  Comet for Windows is a mixed-mode program: it starts the .NET
    Framework to call Thermo's reader, and the reader names further assemblies
    that are resolved only when first used.  Which of them exist on the host is
    the most likely reason for a RAW file to fail to open, and Comet's own
    message for that does not name the missing library.
    """
    with open(path, "rb") as handle:
        data = handle.read()

    def u16(at):
        return struct.unpack_from("<H", data, at)[0]

    def u32(at):
        return struct.unpack_from("<I", data, at)[0]

    if data[:2] != b"MZ":
        return None
    pe = u32(0x3C)
    sections = pe + 24 + u16(pe + 20)

    def offset(rva):
        for i in range(u16(pe + 6)):
            base = sections + 40 * i
            vsize, vaddr, rsize, raw = struct.unpack_from("<IIII", data, base + 8)
            if vaddr <= rva < vaddr + max(vsize, rsize):
                return raw + rva - vaddr
        raise ValueError("RVA 0x%x is in no section" % rva)

    directories = pe + 24 + (112 if u16(pe + 24) == 0x20B else 96)
    cli_rva = u32(directories + 8 * 14)
    if cli_rva == 0:
        return None
    md = offset(u32(offset(cli_rva) + 8))
    if data[md:md + 4] != b"BSJB":
        raise ValueError("no metadata signature")
    vlen = u32(md + 12)
    runtime = data[md + 16:md + 16 + vlen].rstrip(b"\0").decode("ascii", "replace")
    at = md + 16 + vlen + 2
    stream_count = u16(at)
    at += 2
    streams = {}
    for _ in range(stream_count):
        off, size = struct.unpack_from("<II", data, at)
        end = data.index(b"\0", at + 8)
        streams[data[at + 8:end].decode("ascii")] = (md + off, size)
        at = md + ((end + 1 - md + 3) & ~3)
    tables = (streams.get("#~") or streams["#-"])[0]
    heaps = data[tables + 6]
    valid = struct.unpack_from("<Q", data, tables + 8)[0]
    at = tables + 24
    rows = [0] * 64
    for i in range(64):
        if valid >> i & 1:
            rows[i] = u32(at)
            at += 4
    s_ix = 4 if heaps & 1 else 2
    g_ix = 4 if heaps & 2 else 2
    b_ix = 4 if heaps & 4 else 2

    def ix(table):
        return 4 if rows[table] > 0xFFFF else 2

    def coded(members, bits):
        return 4 if max(rows[m] for m in members) >= (1 << (16 - bits)) else 2

    tdr = coded([2, 1, 0x1B], 2)
    hc = coded([4, 8, 0x17], 2)
    hca = coded([6, 4, 1, 2, 8, 9, 10, 0, 14, 0x17, 0x14, 0x11, 0x1A, 0x1B,
                 0x20, 0x23, 0x26, 0x27, 0x28, 0x2A, 0x2C, 0x2B], 5)
    hfm = coded([4, 8], 1)
    hds = coded([2, 6, 0x20], 2)
    mrp = coded([2, 1, 0x1A, 6, 0x1B], 3)
    hs = coded([0x14, 0x17], 1)
    mdr = coded([6, 10], 1)
    mf = coded([4, 6], 1)
    cat = coded([6, 10], 3)
    rs = coded([0, 0x1A, 0x23, 1], 2)
    size = {
        0x00: 2 + s_ix + 3 * g_ix, 0x01: rs + 2 * s_ix,
        0x02: 4 + 2 * s_ix + tdr + ix(4) + ix(6), 0x03: ix(4),
        0x04: 2 + s_ix + b_ix, 0x05: ix(6), 0x06: 8 + s_ix + b_ix + ix(8),
        0x07: ix(8), 0x08: 4 + s_ix, 0x09: ix(2) + tdr, 0x0A: mrp + s_ix + b_ix,
        0x0B: 2 + hc + b_ix, 0x0C: hca + cat + b_ix, 0x0D: hfm + b_ix,
        0x0E: 2 + hds + b_ix, 0x0F: 6 + ix(2), 0x10: 4 + ix(4), 0x11: b_ix,
        0x12: ix(2) + ix(0x14), 0x13: ix(0x14), 0x14: 2 + s_ix + tdr,
        0x15: ix(2) + ix(0x17), 0x16: ix(0x17), 0x17: 2 + s_ix + b_ix,
        0x18: 2 + ix(6) + hs, 0x19: ix(2) + 2 * mdr, 0x1A: s_ix, 0x1B: b_ix,
        0x1C: 2 + mf + s_ix + ix(0x1A), 0x1D: 4 + ix(4), 0x1E: 4, 0x1F: 4,
        0x20: 16 + b_ix + 2 * s_ix, 0x21: 4, 0x22: 12,
    }
    for table in range(0x23):
        at += rows[table] * size[table]
    strings = streams["#Strings"][0]
    references = []
    for _ in range(rows[0x23]):
        major, minor, build, revision = struct.unpack_from("<HHHH", data, at)
        name_at = at + 12 + b_ix
        index = u32(name_at) if s_ix == 4 else u16(name_at)
        end = data.index(b"\0", strings + index)
        references.append((data[strings + index:end].decode("utf-8", "replace"),
                           "%d.%d.%d.%d" % (major, minor, build, revision)))
        at += 12 + 2 * b_ix + 2 * s_ix
    return runtime, references


def global_assembly_cache(name):
    root = os.path.join(os.environ.get("WINDIR") or os.environ.get("SystemRoot")
                        or r"C:\Windows", "Microsoft.NET", "assembly")
    found = []
    for cache in ("GAC_MSIL", "GAC_64", "GAC_32"):
        directory = os.path.join(root, cache, name)
        if os.path.isdir(directory):
            found.extend("%s/%s" % (cache, v) for v in sorted(os.listdir(directory)))
    return found


def dependency_report(t, release):
    """Informational only: it never changes a verdict."""
    bin_dir = release["bin_dir"]
    t.log("  Comet %s -- .NET assemblies named by the files in %s"
          % (release["version"], bin_dir))
    present = {name.lower() for name in os.listdir(bin_dir)}
    unresolved = set()
    for name in sorted(os.listdir(bin_dir)):
        try:
            parsed = assembly_references(os.path.join(bin_dir, name))
        except (ValueError, KeyError, IndexError, struct.error) as exc:
            t.log("    %s: metadata not parsed (%s: %s)"
                  % (name, type(exc).__name__, exc))
            continue
        if parsed is None:
            t.log("    %s: not a .NET assembly" % name)
            continue
        runtime, references = parsed
        t.log("    %s: built for CLR %s, references:" % (name, runtime))
        for ref, version in references:
            beside = (ref + ".dll").lower() in present
            where = ["beside Comet" if beside
                     else "not beside Comet: the host must supply it"]
            if IS_WINDOWS:
                cached = global_assembly_cache(ref)
                where.append("in the GAC as %s" % ", ".join(cached) if cached
                             else "NOT in the GAC")
                if not beside and not cached:
                    unresolved.add("%s %s" % (ref, version))
            t.log("        %-42s %-14s %s" % (ref, version, "; ".join(where)))
    if IS_WINDOWS:
        if unresolved:
            t.log("    NOT FOUND beside Comet or in the GAC: %s"
                  % ", ".join(sorted(unresolved)))
            t.log("    Such a reference is resolved only if the code that needs "
                  "it runs.  If the RAW")
            t.log("    file does not open, these are the first thing to look at.")
        else:
            t.log("    every reference is beside Comet or in the GAC")
    else:
        t.log("    (not a Windows host: the global assembly cache was not consulted)")
    return sorted(unresolved)


def host_report(t, releases, percolator):
    t.kv("  platform", platform.platform())
    t.kv("  machine", platform.machine())
    t.kv("  python", "%s (%s)" % (sys.version.split()[0], sys.executable))
    t.kv("  project root", PROJECT_ROOT)
    for name in ("GITHUB_SHA", "GITHUB_REF", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT",
                 "ImageOS", "ImageVersion", "NUMBER_OF_PROCESSORS"):
        if os.environ.get(name):
            t.kv("  %s" % name, os.environ[name])
    if not IS_WINDOWS:
        t.kv("  Windows", "NO -- nothing below can be executed on this host")
        return
    try:
        import ctypes
        previous = ctypes.windll.kernel32.SetErrorMode(0x0001 | 0x0002 | 0x8000)
        t.kv("  error dialogs", "suppressed for child processes (SetErrorMode "
                                "was 0x%04X), so a loader failure is an exit "
                                "code and not a dialog nobody can dismiss" % previous)
    except Exception as exc:                    # pylint: disable=broad-except
        t.kv("  error dialogs", "NOT suppressed (%s: %s)" % (type(exc).__name__, exc))
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                            r"SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full") as key:
            release = winreg.QueryValueEx(key, "Release")[0]
            try:
                version = winreg.QueryValueEx(key, "Version")[0]
            except OSError:
                version = "?"
        t.kv("  .NET Framework 4", "version %s, release %s (528040 or more is "
                                   "4.8; netstandard 2.0 needs 4.7.2, 461808)"
             % (version, release))
    except Exception as exc:                    # pylint: disable=broad-except
        t.kv("  .NET Framework 4", "NOT FOUND in the registry (%s: %s)"
             % (type(exc).__name__, exc))
    system32 = os.path.join(os.environ.get("SystemRoot") or r"C:\Windows", "System32")
    wanted = set(percolator["host_libraries"])
    for release in releases:
        wanted.update(release["host_libraries"])
    # Comet 2026.02.2's row names none, but its import table is the same.
    wanted.update(["MSVCP140.dll", "VCRUNTIME140.dll", "VCRUNTIME140_1.dll",
                   "mscoree.dll"])
    for name in sorted(wanted, key=str.lower):
        t.kv("  %s" % name, "present in System32" if os.path.isfile(
            os.path.join(system32, name)) else "NOT in System32")


# --------------------------------------------------------------------------
# The result block
# --------------------------------------------------------------------------

def signed(value):
    return "%+d" % value


def result_lines(version, spec, out, param_rows, params_note):
    """The RAW-TRIAL| lines for one Comet release and one file."""
    head = "comet=%s|file=%s|role=%s" % (version, spec["name"], spec["role"])
    pin = out["pin"]

    def counted(name):
        table = out["tables"].get(name)
        if not table or not table["exists"] or table["problem"]:
            return None                 # no table is not the same as none at 1%
        return table["q01"]

    psms, peptides = counted("psms.target"), counted("peptides.target")
    fields = [head, "read=%s" % out["read"],
              "pin-rows=%d" % pin["rows"], "pin-targets=%d" % pin["targets"],
              "pin-decoys=%d" % pin["decoys"],
              "percolator=%s" % out["percolator"],
              "psms-q01=%s" % ("none" if psms is None else psms),
              "peptides-q01=%s" % ("none" if peptides is None else peptides),
              "d019-psms=%d" % spec["d019_psms"],
              "d019-peptides=%d" % spec["d019_peptides"],
              "diff-psms=%s" % ("none" if psms is None
                                else signed(psms - spec["d019_psms"])),
              "diff-peptides=%s" % ("none" if peptides is None
                                    else signed(peptides - spec["d019_peptides"]))]
    lines = ["RAW-TRIAL|result|" + "|".join(fields)]
    comet, perc = out["comet"], out["perc"]
    lines.append("RAW-TRIAL|detail|%s|comet-banner=%s|comet-exit=%s|"
                 "spectra-loaded=%s|pin-distinct-scans=%d|pin-scan-charge-pairs=%d|"
                 "pin-rank1-targets=%d|pin-rank1-decoys=%d|pepxml-spectrum-queries=%d|"
                 "percolator-exit=%s|scans-behind-psms-q01=%s"
                 % (head, out["banner"] or "NONE", fmt_exit(comet["exit_code"]),
                    "unknown" if out["loaded"] is None else out["loaded"],
                    pin["scans"], pin["spectra"], pin["rank1_targets"],
                    pin["rank1_decoys"], out["pepxml"]["queries"],
                    fmt_exit(perc["exit_code"]) if perc else "not-run",
                    out["tables"].get("psms.target", {}).get("scans_q01", "none")))
    lines.append("RAW-TRIAL|detail|%s|read-evidence=%s" % (head, out["read_reason"]))
    lines.append("RAW-TRIAL|detail|%s|percolator-evidence=%s"
                 % (head, out["percolator_reason"]))
    same = LINUX_MZML_SAME_RELEASE.get((version, spec["name"]))
    lines.append(
        "RAW-TRIAL|reference|%s|source=Linux, the file converted to mzML, Comet "
        "2026.02.2 (D-019 and small-thermo-raw-files.rst)|psms-q01=%d|"
        "peptides-q01=%d|pin-rows=%d|pin-targets=%d|pin-decoys=%d|"
        "spectra-loaded=%d|ms2-scans=%d"
        % (head, spec["d019_psms"], spec["d019_peptides"], spec["ref_pin_rows"],
           spec["ref_pin_targets"], spec["ref_pin_decoys"],
           spec["ref_spectra_loaded"], spec["ms2_scans"]))
    lines.append(
        "RAW-TRIAL|reference|%s|source=Linux, the same mzML, THIS Comet release "
        "(Phase 16 unit 1's own measurement, not a D-019 figure)|psms-q01=%s|"
        "peptides-q01=%s|diff-psms=%s|diff-peptides=%s"
        % (head, same[0] if same else "not-measured", same[1] if same else "not-measured",
           signed(psms - same[0]) if same and psms is not None else "none",
           signed(peptides - same[1]) if same and peptides is not None else "none"))
    if param_rows is None:
        lines.append("RAW-TRIAL|params|comet=%s|differs-from-comet-p=unknown|%s"
                     % (version, params_note))
    else:
        lines.append("RAW-TRIAL|params|comet=%s|differs-from-comet-p=%d|names=%s|%s"
                     % (version, len(param_rows),
                        ",".join(row[0] for row in param_rows), params_note))
    mentions = [line for line in out["said"]
                if re.search(r"empty|no peaks|no spectra|zero", line, re.IGNORECASE)]
    lines.append("RAW-TRIAL|risk|%s|risk=empty-ms1-scans|in-file=%s|"
                 "comet-lines-mentioning-empty-scans=%d|read=%s"
                 % (head, spec["empty_ms1"], len(mentions), out["read"]))
    lines.append("RAW-TRIAL|risk|%s|risk=empty-ms2-scans|in-file=%s|"
                 "ms2-scans-in-file=%d|pin-distinct-scans=%d|read=%s"
                 % (head, spec["empty_ms2"], spec["ms2_scans"], pin["scans"],
                    out["read"]))
    lines.append("RAW-TRIAL|risk|%s|risk=no-precursor-charge|"
                 "charges-comet-searched=%s|spectra-loaded=%s|"
                 "linux-mzml-spectra-loaded=%d|comet-lines-mentioning-charge=%d"
                 % (head, fmt_charges(pin["charges"]),
                    "unknown" if out["loaded"] is None else out["loaded"],
                    spec["ref_spectra_loaded"],
                    len([l for l in out["said"] if re.search("charge|precursor", l, re.I)])))
    if out["said"]:
        for line in out["said"]:
            lines.append("RAW-TRIAL|comet-said|%s|line=%s" % (head, line))
    else:
        lines.append("RAW-TRIAL|comet-said|%s|nothing: no line of Comet's output "
                     "mentions a warning, an error, a charge or an empty scan"
                     % head)
    return lines


# --------------------------------------------------------------------------
# The two modes
# --------------------------------------------------------------------------

def prepare(t, work_dir, check_only):
    """Everything that needs no Windows: pins, downloads, FASTA, parameters."""
    t.section("1. The pins in manifests/tools.json, and the downloads")
    artefacts = load_manifest()
    releases = comet_releases(artefacts)
    percolator = percolator_pin(artefacts)
    t.kv("  Comet releases for %s/%s" % (MANIFEST_OS, MANIFEST_ARCH),
         ", ".join(r["version"] for r in releases))
    t.kv("  Percolator", PERCOLATOR_VERSION)
    t.log()
    for release in releases:
        install_comet(release, work_dir, t)
        t.log()
    percolator["exe"] = install_percolator(percolator, work_dir, t)

    t.section("2. The RAW file, from PRIDE, by D-019's size and SHA-256")
    raw_dir = os.path.join(work_dir, "downloads", "raw")
    PRIMARY["path"] = os.path.join(raw_dir, PRIMARY["name"])
    fetch(raw_pin(PRIMARY), PRIMARY["path"], t)
    if check_only:
        t.log()
        t.log("  --check-only also fetches the fallback, to prove its pin can be "
              "met; a real run")
        t.log("  fetches it only if a Comet release cannot read the primary file.")
        FALLBACK["path"] = os.path.join(raw_dir, FALLBACK["name"])
        fetch(raw_pin(FALLBACK), FALLBACK["path"], t)

    t.section("3. The committed FASTA")
    fasta = verify_fasta(t)

    t.section("4. The host, and what the managed code will ask it for")
    host_report(t, releases, percolator)
    t.log()
    for release in releases:
        release["unresolved"] = dependency_report(t, release)
        t.log()
    return releases, percolator, fasta


def check_only(t, work_dir):
    releases, percolator, fasta = prepare(t, work_dir, True)
    t.section("5. The parameter file (--check-only: written, not used)")
    directory = os.path.join(work_dir, "check-only")
    if os.path.isdir(directory):
        shutil.rmtree(directory)
    os.makedirs(directory)
    fasta_copy = os.path.join(directory, os.path.basename(fasta))
    copy_verified(fasta, fasta_copy, FASTA_SHA256)
    text = write_params(os.path.join(directory, "comet.params"), fasta_copy, t)
    t.log()
    t.log("  What `comet -p` writes is known only by running Comet, which "
          "--check-only does not do;")
    t.log("  a real run prints every parameter that differs from it, per release.")

    t.section("CHECK-ONLY RESULT -- no tool was executed, nothing was searched")
    downloads = sum(len(r["files"]) for r in releases) + 3
    t.log("RAW-CHECK|mode=check-only|executed=nothing|searched=nothing|"
          "this-is-not-a-trial-result")
    t.log("RAW-CHECK|comet-releases=%s|percolator=%s"
          % (",".join(r["version"] for r in releases), PERCOLATOR_VERSION))
    t.log("RAW-CHECK|downloads-verified=%d|every-size-and-sha256-matched-its-pin=yes"
          % downloads)
    t.log("RAW-CHECK|raw=%s|sha256=%s" % (PRIMARY["name"], PRIMARY["sha256"]))
    t.log("RAW-CHECK|raw-fallback=%s|sha256=%s" % (FALLBACK["name"], FALLBACK["sha256"]))
    t.log("RAW-CHECK|fasta=%s|records=%d|sha256=%s"
          % ("/".join(FASTA_RELPATH), FASTA_RECORDS, FASTA_SHA256))
    t.log("RAW-CHECK|params-sha256-database-masked=%s" % masked_params_digest(text))
    t.log("RAW-CHECK|exit=0")
    return EXIT_OK


def trial(t, work_dir):
    releases, percolator, fasta = prepare(t, work_dir, False)
    block = []
    outcomes = []
    for number, release in enumerate(releases, start=5):
        version = release["version"]
        t.section("%d. Comet %s" % (number, version))
        release_dir = os.path.join(work_dir, "run", "comet-%s" % version)
        if os.path.isdir(release_dir):
            shutil.rmtree(release_dir)

        t.log("  -- comet -p: the parameter file this release writes")
        prec, new_text = params_new(t, release["exe"],
                                    os.path.join(release_dir, "params-new"))
        t.kv("  banner from -p", comet_banner(prec) or "NONE")

        t.log()
        t.log("  -- the search: %s (primary)" % PRIMARY["name"])
        out = run_search(t, release["exe"], percolator["exe"], PRIMARY["path"],
                         PRIMARY["sha256"], fasta,
                         os.path.join(release_dir, os.path.splitext(PRIMARY["name"])[0]))
        if new_text is None:
            param_rows, enzymes_same = None, None
            note = "comet -p wrote no comet.params.new (exit %s)" % fmt_exit(prec["exit_code"])
            t.kv("  parameters vs comet -p", note)
        else:
            param_rows, enzymes_same = compare_params(out["params_text"], new_text)
            note = "enzyme-table=%s" % ("same" if enzymes_same else "DIFFERENT")
            t.log()
            t.log("  every parameter whose value differs from this release's "
                  "`comet -p` (%d):" % len(param_rows))
            for name, ours, theirs in param_rows:
                t.log("      %-30s ours: %-34s comet -p: %s" % (name, ours, theirs))
            t.kv("  enzyme table", "same as comet -p" if enzymes_same
                 else "DIFFERS from comet -p")
        block.extend(result_lines(version, PRIMARY, out, param_rows, note))
        if release.get("unresolved"):
            block.append("RAW-TRIAL|detail|comet=%s|assemblies-not-beside-comet-"
                         "or-in-gac=%s" % (version, ",".join(release["unresolved"])))
        outcomes.append((version, out))

        if out["read"] != "READ":
            t.log()
            t.log("  -- Comet %s did not read the primary file: trying D-019's "
                  "fallback, %s" % (version, FALLBACK["name"]))
            try:
                if "path" not in FALLBACK:
                    FALLBACK["path"] = os.path.join(work_dir, "downloads", "raw",
                                                    FALLBACK["name"])
                    fetch(raw_pin(FALLBACK), FALLBACK["path"], t)
                fallback = run_search(
                    t, release["exe"], percolator["exe"], FALLBACK["path"],
                    FALLBACK["sha256"], fasta,
                    os.path.join(release_dir, os.path.splitext(FALLBACK["name"])[0]))
                block.extend(result_lines(version, FALLBACK, fallback,
                                          param_rows, note))
            except NoVerdict as exc:
                FALLBACK.pop("path", None)
                t.kv("  FALLBACK NOT TRIED", exc.summary)
                for line in exc.detail:
                    t.log("      %s" % line)
                block.append("RAW-TRIAL|result|comet=%s|file=%s|role=fallback|"
                             "read=NO-VERDICT|reason=%s"
                             % (version, FALLBACK["name"], exc.summary))

    if not outcomes:
        raise NoVerdict("no Comet release was run")
    not_read = [v for v, out in outcomes if out["read"] != "READ"]
    not_scored = [v for v, out in outcomes
                  if out["read"] == "READ" and out["percolator"] != "TRAINED"]
    if not_read:
        status = EXIT_NOT_READ
        conclusion = ("NOT READ: Comet %s could not read %s"
                      % (", ".join(not_read), PRIMARY["name"]))
    elif not_scored:
        status = EXIT_READ_NOT_SCORED
        conclusion = ("READ, NOT SCORED: every release read %s, but Percolator "
                      "did not give counts at q < %s for Comet %s"
                      % (PRIMARY["name"], Q_THRESHOLD, ", ".join(not_scored)))
    else:
        status = EXIT_OK
        conclusion = ("READ: Comet %s each read %s directly, and Percolator %s "
                      "trained and reported PSMs and peptides at q < %s for each"
                      % (", ".join(v for v, _ in outcomes), PRIMARY["name"],
                         PERCOLATOR_VERSION, Q_THRESHOLD))

    t.section("RESULT -- grep '^RAW-TRIAL|' for this block alone")
    t.log("RAW-TRIAL|begin|commit=%s|ref=%s|run=%s|host=%s|python=%s|percolator=%s"
          % (os.environ.get("GITHUB_SHA", "unknown"),
             os.environ.get("GITHUB_REF", "unknown"),
             os.environ.get("GITHUB_RUN_ID", "unknown"),
             platform.platform(), sys.version.split()[0], PERCOLATOR_VERSION))
    for line in block:
        t.log(line)
    t.log("RAW-TRIAL|verdict|exit=%d|releases=%s|primary-read-by=%s|"
          "primary-not-read-by=%s|%s"
          % (status, ",".join(v for v, _ in outcomes),
             ",".join(v for v, out in outcomes if out["read"] == "READ") or "none",
             ",".join(not_read) or "none", conclusion))
    t.log("RAW-TRIAL|end")
    return status


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

class Parser(argparse.ArgumentParser):
    def error(self, message):
        self.print_usage(sys.stderr)
        sys.stderr.write("%s: %s\n" % (SELF, message))
        raise SystemExit(EXIT_MISUSE)


def main(argv=None):
    parser = Parser(prog=SELF, description="Bare-tools trial: Comet for Windows "
                    "reads a Thermo RAW file directly. See the module docstring.")
    parser.add_argument("--check-only", action="store_true",
                        help="fetch and verify every download, verify the FASTA, "
                             "write the parameter file; execute nothing")
    parser.add_argument("--work-dir", default=DEFAULT_WORK_DIR,
                        help="where everything is written (default: %(default)s)")
    args = parser.parse_args(argv)
    work_dir = os.path.abspath(args.work_dir)

    if not args.check_only and not IS_WINDOWS:
        sys.stderr.write(
            "%s: REFUSED -- this is %s, not Windows.  Comet reads Thermo RAW "
            "files only on Windows, so nothing can be learned here.  Use "
            "--check-only to verify the downloads and the parameter file.\n"
            % (SELF, platform.system()))
        return EXIT_REFUSED

    os.makedirs(work_dir, exist_ok=True)
    t = Transcript(os.path.join(work_dir, "transcript.txt"))
    t.log("%s -- %s" % (SELF, "CHECK-ONLY: no tool is executed" if args.check_only
                        else "the Windows RAW trial"))
    t.kv("started (UTC)", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    t.kv("work directory", work_dir)
    try:
        status = check_only(t, work_dir) if args.check_only else trial(t, work_dir)
    except NoVerdict as exc:
        t.section("NO VERDICT -- nothing was learned about Comet")
        t.log("  %s" % exc.summary)
        for line in exc.detail:
            t.log("      %s" % line)
        prefix = "RAW-CHECK" if args.check_only else "RAW-TRIAL"
        t.log("%s|verdict|exit=%d|NO VERDICT: %s" % (prefix, EXIT_NO_VERDICT, exc.summary))
        status = EXIT_NO_VERDICT
    except Exception as exc:                    # pylint: disable=broad-except
        import traceback
        t.section("NO VERDICT -- the harness itself failed")
        t.log(traceback.format_exc())
        prefix = "RAW-CHECK" if args.check_only else "RAW-TRIAL"
        t.log("%s|verdict|exit=%d|NO VERDICT: harness failure, %s: %s"
              % (prefix, EXIT_NO_VERDICT, type(exc).__name__, exc))
        status = EXIT_NO_VERDICT
    t.kv("finished (UTC)", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    t.kv("exit status", status)
    t.close()
    return status


if __name__ == "__main__":
    sys.exit(main())
