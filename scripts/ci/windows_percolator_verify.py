#!/usr/bin/env python3
"""windows_percolator_verify.py -- drive the Windows Percolator checklist.

PHASE-00 residue, work unit U3.  This is the whole of the logic behind
``scripts/ci/windows-percolator-verify.sh``; the shell file next to it is a
five-line wrapper that finds a Python and execs this.

WHY THE LOGIC LIVES IN PYTHON.  The entry point has to be a single
``bash scripts/...`` invocation, because that is what this project's CI
contract requires and what ``scripts/ci/check-workflows.py`` enforces.  But on
a GitHub ``windows-latest`` runner ``shell: bash`` is Git Bash, an MSYS
environment that rewrites arguments that look like POSIX paths, has its own
quoting rules and its own idea of what a path is.  So no *logic* may depend on
Git Bash behaving like bash.  Everything here launches processes with an
ARGUMENT ARRAY -- never a shell string -- which is also this project's rule for
launching processes, and every argument is built with ``os.path``.

WHAT IT DOES.  The seven steps of the checklist in
``docs/feasibility/windows-artefact.rst``, section "Recommended: the checklist
for a Windows machine", in order, each writing its evidence to a transcript;
then a section 8 that is explicitly NOT part of that checklist (see below).

WHAT IT MAY NOT CLAIM.  Until a Windows run has actually passed, nothing in
this project says the Windows binary is *verified*, *confirmed*, *proven* or
*tested*.  This program reports what it OBSERVED on the machine it ran on.
That is a different claim, and it is the only one made here.

FALSIFIABILITY -- read this before changing step 5.  Step 5 tests for the
ABSENCE of the string ``Compiler flag XML_SUPPORT was off``.  A binary that
never started, that was not found, that died on a missing DLL, or whose output
was not captured ALSO produces an absence, and would sail through a naive
check.  An absence is evidence only when there is positive proof the program
ran and reached the code path in question.  So step 5 requires, in the SAME
captured output, the version banner (it started) and ``Reading pin-xml input
from datafile`` (it reached the pin-XML input path) BEFORE the absence is
allowed to mean anything.  Without those markers the verdict is INCONCLUSIVE,
never PASS.  Section 8 adds a second guard: it runs the same test on the
``noxml`` build, where the diagnostic MUST appear, which is the positive
control proving the detector can see that string on this host at all.

HONEST NEGATIVES.  The checklist ends "Return the transcript.  It replaces
this document's central caveat with a fact, EITHER WAY."  A job engineered to
go green fails that sentence.  The verdict block distinguishes a HARNESS
FAILURE (nothing was learned about the binary) from a NEGATIVE (the binary ran
and contradicted the project's inference) from INCONCLUSIVE (it did not run far
enough for the test to mean anything) from PASS, and only PASS exits 0.

EXIT CODE 0 PROVES NOTHING.  Every assertion in the transcript names the value
it observed.  No step is reported as passed because a command returned 0.

Usage::

    windows_percolator_verify.py                 # the real run; Windows only
    windows_percolator_verify.py --check-only    # platform-independent steps
    windows_percolator_verify.py --self-test     # damage the harness on purpose
    windows_percolator_verify.py --help

Exit status:
    0  PASS -- every checklist assertion held, on Windows, and each named the
       value it observed.  Also the exit status of a successful --check-only
       (which is NOT a pass: no Windows binary was executed) and of a
       --self-test in which every damaged case was rejected.
    1  NEGATIVE -- the binary ran and the evidence contradicts an inference
       this project currently relies on.  This is a real finding, not an
       error; it is meant to be loud.
    2  INCONCLUSIVE -- the binary did not run far enough for the test to mean
       anything.  Nothing is established either way.
    3  HARNESS FAILURE -- download, checksum, extraction, Python, or the PIN
       generator failed.  Nothing was learned about the binary.
    4  REFUSED -- this is not a Windows host and --check-only was not given.
       Nothing about Windows can be inferred here.
    5  MISUSE -- bad arguments, or a broken environment.
    6  SELF-TEST FAILED -- a damaged case was accepted, or a control case was
       rejected.  The harness cannot be trusted to report a negative.

Standard library only, Python 3.8+, no third-party import and no pip: it has
to run on a bare Windows runner with nothing installed.  Nothing is written
outside ``_build/windows-verify/`` and nothing is installed anywhere.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from datetime import datetime, timezone

# --------------------------------------------------------------------------
# Exit statuses.  Documented in the module docstring and in the wrapper.
# --------------------------------------------------------------------------
EXIT_PASS = 0
EXIT_NEGATIVE = 1
EXIT_INCONCLUSIVE = 2
EXIT_HARNESS_FAILURE = 3
EXIT_REFUSED = 4
EXIT_MISUSE = 5
EXIT_SELF_TEST_FAILED = 6

VERDICT_EXIT = {
    "PASS": EXIT_PASS,
    "NEGATIVE": EXIT_NEGATIVE,
    "INCONCLUSIVE": EXIT_INCONCLUSIVE,
    "HARNESS FAILURE": EXIT_HARNESS_FAILURE,
    "CHECK-ONLY": EXIT_PASS,
}

# --------------------------------------------------------------------------
# Pinned identities.  NONE OF THESE MAY BE RELAXED to make a run pass.  Each
# was measured on Linux on 2026-08-29/30 and is recorded in
# docs/feasibility/windows-artefact.rst; they are what tie a Windows run to the
# same bytes the Linux-side evidence was gathered from.  A mismatch is a HARD
# STOP, never a warning.
# --------------------------------------------------------------------------
INSTALLER_URL = ("https://github.com/percolator/percolator/releases/download/"
                 "rel-3-07-01/percolator-v3-07.exe")
INSTALLER_SHA256 = "a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348"
INSTALLER_BYTES = 1818841

PAYLOAD_EXE_SHA256 = "044f3957e2f05a38d13d8c77136f24435827d8563850b8808b5ad52e6aa4691e"
PAYLOAD_EXE_BYTES = 804864
PAYLOAD_UNIQUE_FILES = 22
XSD_IN_RELPATH = "share/xml/percolator/xml-pin-1-3/percolator_in.xsd"
XSD_IN_SHA256 = "fc3c95e02950af3c44ae0c830c3ecf8005a543358eb7311f94c12dab4a216b87"
XSD_OUT_RELPATH = "share/xml/percolator/xml-pout-1-5/percolator_out.xsd"
XSD_OUT_SHA256 = "c4c664ea673817ded4616958b0682f401f940f40212246473e75835f3597bc1b"

PIN_SHA256 = "6643ede48534fcd28c90a1d4e53781e47ba39b0523e9f907ea8e1a63b15af61e"
PIN_ROWS = 400

# Section 8 -- D-002 option C, the artefact the product actually ships.
PORTABLE_URL = ("https://github.com/percolator/percolator/releases/download/"
                "rel-3-07-01/percolator-noxml-windows-portable.zip")
PORTABLE_SHA256 = "1510c2cfc8ce05822ac46e53954c7e6e5fa42305789fa94aad2f73657a0f94a2"
PORTABLE_BYTES = 329022
PORTABLE_EXE_NAME = "percolator.exe"
PORTABLE_EXE_SHA256 = "b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f"
PORTABLE_EXE_BYTES = 707072

# What the Linux builds of the SAME release did with the SAME 400-PSM PIN.
# These are the reference values a Windows observation is compared against.
LINUX_XML_X_BYTES = 143729
LINUX_NOXML_X_BYTES = 143733
LINUX_PSM_COUNT = 200
LINUX_XML_HELP_EXIT = 0
LINUX_BANNER = "Percolator version 3.07.1, Build Date Jun 20 2024 13:21:20"

# --------------------------------------------------------------------------
# The markers step 5's falsifiability rests on.  Quoted from real captured
# Linux output of this exact release; see self_test() for the fixtures.
# --------------------------------------------------------------------------
MARKER_BANNER = "Percolator version"          # the binary started
MARKER_ISSUED = "Issued command:"             # it parsed its command line
MARKER_PIN_XML = "Reading pin-xml input from datafile"   # it reached the path
MARKER_XML_OFF = "Compiler flag XML_SUPPORT was off"     # the diagnostic
MARKER_XMLOUTPUT = "--xmloutput"              # the option in --help

TIMEOUT_DOWNLOAD = 300
TIMEOUT_EXTRACT = 300
TIMEOUT_PIN = 120
TIMEOUT_HELP = 120
TIMEOUT_RUN = 300
TIMEOUT_WHOAMI = 60

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))))
WORK_DIR = os.path.join(PROJECT_ROOT, "_build", "windows-verify")

SELF = "windows_percolator_verify.py"


class HarnessFailure(Exception):
    """Something in the harness failed; nothing was learned about the binary."""

    def __init__(self, summary, detail=None):
        Exception.__init__(self, summary)
        self.summary = summary
        self.detail = list(detail or [])


# --------------------------------------------------------------------------
# Transcript
# --------------------------------------------------------------------------

def ascii_safe(text):
    """Everything written to the transcript is ASCII, always.

    Percolator prints a non-ASCII author name in its banner, and a Windows
    console in CI is whatever code page the image happened to pick.  Escaping
    on the way in means the transcript can never die of a UnicodeEncodeError
    half way through recording a result, and the file is byte-identical
    whatever the console is.  Nothing is lost: non-ASCII becomes a \\xNN
    escape.  CRLF is normalised to LF for the same reason.
    """
    if isinstance(text, bytes):
        text = text.decode("utf-8", "replace")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    return text.encode("ascii", "backslashreplace").decode("ascii")


class Transcript(object):
    """Accumulates the transcript, writes it as it goes, echoes it to stdout.

    Written incrementally rather than at the end: if this program dies in the
    middle of a step, the evidence gathered up to that point is already on
    disk and already in the job log.  Echoing to stdout is deliberate -- the
    job log then carries the transcript even if the artifact upload does not
    happen.
    """

    def __init__(self, path=None, echo=True):
        self.path = path
        self.echo = echo
        self.lines = []
        self.handle = None
        if path:
            parent = os.path.dirname(path)
            if parent and not os.path.isdir(parent):
                os.makedirs(parent)
            self.handle = open(path, "w", encoding="ascii",
                               errors="backslashreplace", newline="\n")

    def log(self, text=""):
        for line in ascii_safe(text).split("\n"):
            line = line.rstrip()
            self.lines.append(line)
            if self.handle is not None:
                self.handle.write(line + "\n")
            if self.echo:
                sys.stdout.write(line + "\n")
        if self.handle is not None:
            self.handle.flush()
        if self.echo:
            sys.stdout.flush()

    def rule(self, char="-"):
        self.log(char * 74)

    def section(self, title):
        self.log()
        self.rule("=")
        self.log(title)
        self.rule("=")

    def kv(self, key, value):
        self.log("  %-28s %s" % (key + ":", value))

    def block(self, title, text):
        self.log("  %s" % title)
        body = ascii_safe(text)
        if body == "":
            self.log("    | (empty -- nothing was captured)")
            return
        for line in body.split("\n"):
            self.log("    | " + line)

    def close(self):
        if self.handle is not None:
            self.handle.close()
            self.handle = None


# --------------------------------------------------------------------------
# Findings and assertions
# --------------------------------------------------------------------------

class Finding(object):
    """One step's conclusion.  status is OK, NEGATIVE or INCONCLUSIVE."""

    def __init__(self, step, status, reasons):
        self.step = step
        self.status = status
        self.reasons = list(reasons)

    def downgrade(self, status, reason):
        self.status = status
        self.reasons.append(reason)


class Assertions(object):
    """The verdict block's rows.  Every row names the value it observed."""

    def __init__(self):
        self.rows = []

    def add(self, status, step, label, observed, expected=None):
        self.rows.append((status, step, label, str(observed),
                          None if expected is None else str(expected)))

    def render(self, t):
        for status, step, label, observed, expected in self.rows:
            t.log("  [%-12s] %-8s %-30s observed %s"
                  % (status, step, label, observed))
            if expected is not None:
                t.log("  %-14s %-8s %-30s expected %s" % ("", "", "", expected))


# --------------------------------------------------------------------------
# Small helpers
# --------------------------------------------------------------------------

def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while True:
            chunk = handle.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def fmt_exit(code):
    """Exit codes in decimal AND hex.

    A crashing Windows process returns its NTSTATUS as an exit code:
    3221225477 is 0xC0000005 (access violation) and 3221225781 is 0xC0000135
    (a DLL was not found).  Nobody recognises those in decimal.  A negative
    value on a POSIX host is a signal.
    """
    if code is None:
        return "none (no exit status: it timed out or never started)"
    text = "%d (0x%08X)" % (code, code & 0xFFFFFFFF)
    if code < 0:
        text += " -- killed by signal %d" % (-code,)
    return text


def run_process(argv, cwd, timeout, label, t):
    """Launch a process with an ARGUMENT ARRAY and a timeout.  Never a shell.

    A timeout is recorded as a timeout and never as a pass.  A failure to
    launch at all is recorded as such: on Windows that is how a missing DLL or
    a missing file shows up, and it must not be confused with a clean run that
    printed nothing.
    """
    rec = {
        "label": label,
        "argv": [str(a) for a in argv],
        "cwd": str(cwd),
        "timeout_s": timeout,
        "timed_out": False,
        "launch_error": None,
        "exit_code": None,
        "stdout": "",
        "stderr": "",
        "combined": "",
        "seconds": 0.0,
    }
    started = time.time()
    try:
        proc = subprocess.Popen(rec["argv"], cwd=str(cwd),
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                stdin=subprocess.DEVNULL)
    except OSError as exc:
        rec["launch_error"] = "%s: %s" % (type(exc).__name__, exc)
        rec["seconds"] = time.time() - started
        _log_process(rec, t)
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
    rec["combined"] = rec["stdout"] + ("\n" if rec["stdout"] and rec["stderr"]
                                       else "") + rec["stderr"]
    _log_process(rec, t)
    return rec


def _log_process(rec, t):
    t.log("  launched (argument array, no shell):")
    for index, arg in enumerate(rec["argv"]):
        t.log("      argv[%d] = %r" % (index, arg))
    t.kv("working directory", rec["cwd"])
    t.kv("timeout", "%s s" % rec["timeout_s"])
    t.kv("wall clock", "%.2f s" % rec["seconds"])
    if rec["launch_error"]:
        t.kv("LAUNCH FAILED", rec["launch_error"])
        t.kv("exit code", "none -- the process never started")
        return
    if rec["timed_out"]:
        t.kv("TIMED OUT", "killed after %s s; this is a timeout, not a pass"
             % rec["timeout_s"])
    t.kv("exit code", fmt_exit(rec["exit_code"]))
    t.kv("stdout bytes", len(rec["stdout"]))
    t.kv("stderr bytes", len(rec["stderr"]))
    t.block("stdout, verbatim:", rec["stdout"])
    t.block("stderr, verbatim:", rec["stderr"])


def markers_of(rec):
    """Which positive markers the captured output carries."""
    text = rec.get("combined", "")
    return {
        "banner": MARKER_BANNER in text,
        "issued_command": MARKER_ISSUED in text,
        "reached_pin_xml": MARKER_PIN_XML in text,
        "xml_support_off": MARKER_XML_OFF in text,
        "xmloutput_option": MARKER_XMLOUTPUT in text,
    }


def log_markers(t, marks):
    t.log("  positive markers found in the captured output:")
    for key in ("banner", "issued_command", "reached_pin_xml",
                "xmloutput_option", "xml_support_off"):
        t.log("      %-18s %s" % (key, "yes" if marks[key] else "no"))


# --------------------------------------------------------------------------
# Step 1 -- download, with a checksum that is a hard stop
# --------------------------------------------------------------------------

def fetch(url, dest, expected_sha256, expected_bytes, t, attempts=4,
          allow_cache=True, timeout=TIMEOUT_DOWNLOAD):
    """Download url to dest and require expected_sha256.

    A CHECKSUM MISMATCH IS A HARD STOP.  It is never retried (a retry could
    turn a real substitution into a transient-looking blip and eventually let
    something through), never warned about, and never continued past.  Only a
    genuine transport error, or a body whose length disagrees with the
    server's own Content-Length, is retried.
    """
    record = {"url": url, "dest": dest, "expected_sha256": expected_sha256}

    if allow_cache and os.path.isfile(dest):
        cached = sha256_file(dest)
        if cached == expected_sha256:
            record["source"] = "cache"
            record["sha256"] = cached
            record["bytes"] = os.path.getsize(dest)
            t.kv("source", "reused the cached copy at %s" % dest)
            t.kv("bytes", record["bytes"])
            t.kv("sha256 observed", cached)
            t.kv("sha256 expected", expected_sha256)
            t.kv("match", "yes")
            return record
        t.kv("cache", "%s exists but hashes %s -- re-downloading" % (dest, cached))

    last_error = None
    data = None
    for attempt in range(1, attempts + 1):
        t.kv("attempt %d/%d" % (attempt, attempts), "GET %s" % url)
        try:
            request = urllib.request.Request(
                url, headers={"User-Agent": "CometGUI-windows-percolator-verify"})
            with urllib.request.urlopen(request, timeout=timeout) as response:
                declared = response.headers.get("Content-Length")
                body = response.read()
            if declared is not None and int(declared) != len(body):
                raise IOError("short read: Content-Length %s, got %d bytes"
                              % (declared, len(body)))
            data = body
            break
        except (urllib.error.URLError, IOError, OSError) as exc:
            last_error = "%s: %s" % (type(exc).__name__, exc)
            t.kv("transient failure", last_error)
            if attempt < attempts:
                delay = 2 ** attempt
                t.kv("retrying in", "%d s" % delay)
                time.sleep(delay)

    if data is None:
        raise HarnessFailure(
            "download of %s failed after %d attempts" % (url, attempts),
            ["last error: %s" % last_error,
             "Nothing was learned about the binary."])

    parent = os.path.dirname(dest)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent)
    with open(dest, "wb") as handle:
        handle.write(data)

    observed = hashlib.sha256(data).hexdigest()
    record["source"] = "network"
    record["sha256"] = observed
    record["bytes"] = len(data)
    t.kv("bytes downloaded", len(data))
    t.kv("bytes expected", expected_bytes if expected_bytes else "(not pinned)")
    t.kv("sha256 observed", observed)
    t.kv("sha256 expected", expected_sha256)
    t.kv("match", "yes" if observed == expected_sha256 else "NO")

    if observed != expected_sha256:
        raise HarnessFailure(
            "SHA-256 mismatch for %s -- HARD STOP" % os.path.basename(dest),
            ["url      %s" % url,
             "expected %s" % expected_sha256,
             "observed %s" % observed,
             "bytes    %d" % len(data),
             "These are not the bytes the recorded evidence was gathered "
             "from.  Nothing was learned about the binary."])
    if expected_bytes is not None and len(data) != expected_bytes:
        raise HarnessFailure(
            "byte count mismatch for %s" % os.path.basename(dest),
            ["expected %d bytes, observed %d" % (expected_bytes, len(data))])
    return record


# --------------------------------------------------------------------------
# Analysers.  Pure functions of what was observed: that is what makes the
# harness testable without a Windows machine.  See self_test().
# --------------------------------------------------------------------------

def analyse_help(rec):
    """Step 3: percolator.exe --help."""
    marks = markers_of(rec)
    if rec["launch_error"]:
        return Finding("step 3", "INCONCLUSIVE",
                       ["the binary could not be launched at all (%s); nothing "
                        "about its behaviour follows" % rec["launch_error"]])
    if rec["timed_out"]:
        return Finding("step 3", "INCONCLUSIVE",
                       ["--help timed out after %s s; a timeout is not a pass"
                        % rec["timeout_s"]])
    if not marks["banner"]:
        return Finding("step 3", "INCONCLUSIVE",
                       ["no %r banner in the %d bytes captured: the binary did "
                        "not start, or its output was not captured, so the "
                        "absence of anything else means nothing"
                        % (MARKER_BANNER, len(rec["combined"]))])
    reasons = []
    status = "OK"
    if rec["exit_code"] != LINUX_XML_HELP_EXIT:
        status = "NEGATIVE"
        reasons.append("--help exited %s where the Linux build of the same "
                       "release exits %d" % (fmt_exit(rec["exit_code"]),
                                             LINUX_XML_HELP_EXIT))
    if not marks["xmloutput_option"]:
        status = "NEGATIVE"
        reasons.append("the binary started (banner present) but its --help "
                       "output does not contain %r" % MARKER_XMLOUTPUT)
    if status == "OK":
        reasons.append("the banner is present, --help exited %s and the output "
                       "contains %r" % (fmt_exit(rec["exit_code"]),
                                        MARKER_XMLOUTPUT))
    return Finding("step 3", status, reasons)


def analyse_x_run(rec, pout, step="step 4", reference_bytes=LINUX_XML_X_BYTES):
    """Step 4 (and section 8's equivalent): percolator.exe -X pout.xml test.pin.

    pout is a dict: exists, size, psm_count.
    """
    marks = markers_of(rec)
    if rec["launch_error"]:
        return Finding(step, "INCONCLUSIVE",
                       ["the binary could not be launched at all (%s)"
                        % rec["launch_error"]])
    if rec["timed_out"]:
        return Finding(step, "INCONCLUSIVE",
                       ["-X timed out after %s s; a timeout is not a pass"
                        % rec["timeout_s"]])
    if not marks["banner"]:
        return Finding(step, "INCONCLUSIVE",
                       ["no %r banner in the %d bytes captured: the binary did "
                        "not start, or its output was not captured"
                        % (MARKER_BANNER, len(rec["combined"]))])
    reasons = []
    status = "OK"
    if rec["exit_code"] != 0:
        status = "NEGATIVE"
        reasons.append("the binary started but -X exited %s; on Linux the same "
                       "release exits 0" % fmt_exit(rec["exit_code"]))
    if not pout["exists"]:
        status = "NEGATIVE"
        reasons.append("the binary started but wrote no output file at %s"
                       % pout["path"])
    elif pout["size"] == 0:
        status = "NEGATIVE"
        reasons.append("the binary started but the output file at %s is 0 bytes"
                       % pout["path"])
    elif pout["psm_count"] != LINUX_PSM_COUNT:
        status = "NEGATIVE"
        reasons.append("the output file has %d '<psm ' elements; the Linux "
                       "build of the same release, given this exact input, "
                       "wrote %d" % (pout["psm_count"], LINUX_PSM_COUNT))
    if status == "OK":
        reasons.append("exit 0, %d bytes written, %d '<psm ' elements (the "
                       "Linux twin wrote %d bytes and %d elements from the "
                       "same input; the byte count legitimately differs "
                       "because <command_line> carries this machine's paths)"
                       % (pout["size"], pout["psm_count"], reference_bytes,
                          LINUX_PSM_COUNT))
    return Finding(step, status, reasons)


def analyse_xml_in(rec):
    """Step 5, THE DISCRIMINATING TEST.  Absence is only evidence with proof.

    An XML_SUPPORT=ON build must NOT print the diagnostic.  But so does a
    binary that never ran.  The order of the tests below is the whole point:
    the diagnostic first (its presence proves the binary ran AND refused, so it
    is a NEGATIVE whatever else is missing), then the positive markers, and
    only then is the absence allowed to count.
    """
    marks = markers_of(rec)
    if marks["xml_support_off"]:
        return Finding("step 5", "NEGATIVE",
                       ["the output contains %r.  That string is emitted only "
                        "by a build compiled without XML_SUPPORT.  This "
                        "contradicts the project's inference that "
                        "percolator-v3-07.exe is an XML_SUPPORT=ON build."
                        % MARKER_XML_OFF])
    if rec["launch_error"]:
        return Finding("step 5", "INCONCLUSIVE",
                       ["the binary could not be launched at all (%s).  The "
                        "diagnostic is absent because nothing ran, which "
                        "establishes nothing" % rec["launch_error"]])
    if rec["timed_out"]:
        return Finding("step 5", "INCONCLUSIVE",
                       ["--xml-in timed out after %s s and the diagnostic had "
                        "not appeared.  A timeout is not a pass"
                        % rec["timeout_s"]])
    if not marks["banner"]:
        return Finding("step 5", "INCONCLUSIVE",
                       ["the diagnostic is absent, but so is the %r banner in "
                        "the %d bytes captured.  There is no positive proof "
                        "the binary started, so the absence establishes "
                        "nothing" % (MARKER_BANNER, len(rec["combined"]))])
    if not marks["reached_pin_xml"]:
        return Finding("step 5", "INCONCLUSIVE",
                       ["the binary started (banner present) but the output "
                        "does not contain %r, so there is no proof it reached "
                        "the pin-XML input path where the diagnostic would be "
                        "emitted.  The absence establishes nothing"
                        % MARKER_PIN_XML])
    return Finding("step 5", "OK",
                   ["the binary started (%r present), reached the pin-XML "
                    "input path (%r present), and did NOT print %r"
                    % (MARKER_BANNER, MARKER_PIN_XML, MARKER_XML_OFF)])


def analyse_xml_in_control(rec):
    """Section 8's inverted test: on the noxml build the diagnostic MUST appear.

    This is the positive control for step 5's detector.  It is reported, and it
    can downgrade step 5, but on its own it never fails the job.
    """
    marks = markers_of(rec)
    if marks["xml_support_off"]:
        return Finding("section 8", "OK",
                       ["the noxml build printed %r, which is the positive "
                        "control: the step 5 detector can see that string on "
                        "this host" % MARKER_XML_OFF])
    if rec["launch_error"] or rec["timed_out"] or not marks["banner"]:
        return Finding("section 8", "INCONCLUSIVE",
                       ["the noxml binary did not run far enough to serve as a "
                        "positive control (launch_error=%s, timed_out=%s, "
                        "banner=%s), so step 5's detector could not be "
                        "exercised against a known-positive on this host"
                        % (rec["launch_error"], rec["timed_out"],
                           marks["banner"])])
    return Finding("section 8", "NEGATIVE",
                   ["the noxml build RAN (banner present) and did NOT print "
                    "%r.  Either this is not the noxml build, or the detector "
                    "cannot see that string on this host" % MARKER_XML_OFF])


# --------------------------------------------------------------------------
# The run
# --------------------------------------------------------------------------

def read_pout(path):
    info = {"path": path, "exists": os.path.isfile(path), "size": 0,
            "psm_count": 0, "head": ""}
    if not info["exists"]:
        return info
    with open(path, "rb") as handle:
        data = handle.read()
    info["size"] = len(data)
    info["psm_count"] = data.count(b"<psm ")
    text = data.decode("utf-8", "replace")
    info["head"] = "\n".join(text.split("\n")[:10])
    return info


def host_block(t):
    t.section("host and environment -- which machine produced this transcript")
    t.kv("utc timestamp",
         datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
    t.kv("platform", platform.platform())
    t.kv("system / release", "%s / %s" % (platform.system(), platform.release()))
    t.kv("version", platform.version())
    t.kv("machine", platform.machine())
    t.kv("processor", platform.processor() or "(not reported)")
    t.kv("node", platform.node())
    t.kv("os.name / sys.platform", "%s / %s" % (os.name, sys.platform))
    t.kv("python version", sys.version.replace("\n", " "))
    t.kv("python executable", sys.executable)
    t.kv("python implementation", platform.python_implementation())
    t.kv("process cwd at start", os.getcwd())
    t.kv("project root", PROJECT_ROOT)
    t.kv("work directory", WORK_DIR)
    t.kv("argv", repr(sys.argv))
    for name in ("CI", "GITHUB_ACTIONS", "GITHUB_WORKFLOW", "GITHUB_RUN_ID",
                 "GITHUB_SHA", "RUNNER_OS", "RUNNER_ARCH", "RUNNER_NAME",
                 "ImageOS", "ImageVersion"):
        if os.environ.get(name):
            t.kv("env %s" % name, os.environ[name])


def step1_download(t, assertions):
    t.section("step 1 -- download percolator-v3-07.exe and require its SHA-256")
    t.log("A mismatch here is a HARD STOP.  It is never a warning and never")
    t.log("continued past: different bytes mean the recorded evidence does not")
    t.log("describe what is about to be run.")
    t.log()
    dest = os.path.join(WORK_DIR, "percolator-v3-07.exe")
    record = fetch(INSTALLER_URL, dest, INSTALLER_SHA256, INSTALLER_BYTES, t)
    assertions.add("PASS", "step 1", "installer sha256", record["sha256"],
                   INSTALLER_SHA256)
    assertions.add("PASS", "step 1", "installer bytes", record["bytes"],
                   INSTALLER_BYTES)
    return dest


def step2_extract(t, assertions, installer):
    t.section("step 2 -- extract the NSIS payload; DO NOT run the installer")
    t.log("The installer is NOT executed here, and must never be.  Its manifest")
    t.log("requests requireAdministrator, so /S does not avoid the elevation")
    t.log("prompt, and running it would also write registry keys under Session")
    t.log("Manager\\Environment and modify PATH.  The payload is decompressed")
    t.log("instead, by scripts/feasibility/extract_nsis.py.")
    t.log()
    extractor = os.path.join(PROJECT_ROOT, "scripts", "feasibility",
                             "extract_nsis.py")
    if not os.path.isfile(extractor):
        raise HarnessFailure("no extractor at %s" % extractor)
    outdir = os.path.join(WORK_DIR, "extract")
    manifest_path = os.path.join(WORK_DIR, "extract-manifest.json")
    rec = run_process([sys.executable, extractor, installer, "-o", outdir,
                       "--json", manifest_path], WORK_DIR, TIMEOUT_EXTRACT,
                      "extract_nsis.py", t)
    if rec["timed_out"] or rec["launch_error"] or rec["exit_code"] != 0:
        raise HarnessFailure(
            "extract_nsis.py did not complete (exit %s, timed_out=%s, "
            "launch_error=%s)" % (fmt_exit(rec["exit_code"]), rec["timed_out"],
                                  rec["launch_error"]),
            ["Nothing was learned about the binary."])
    if not os.path.isfile(manifest_path):
        raise HarnessFailure("extract_nsis.py exited 0 but wrote no manifest at "
                             "%s -- exit code 0 proves nothing" % manifest_path)
    with open(manifest_path, encoding="utf-8") as handle:
        manifest = json.load(handle)

    unique = manifest.get("unique_file_count")
    instructions = len(manifest.get("files", []))
    t.kv("extract instructions", instructions)
    t.kv("distinct payload files", "%s (expected %d)"
         % (unique, PAYLOAD_UNIQUE_FILES))
    if unique != PAYLOAD_UNIQUE_FILES:
        raise HarnessFailure(
            "the payload has %s distinct files, expected %d"
            % (unique, PAYLOAD_UNIQUE_FILES),
            ["The installer's own SHA-256 matched, so the extractor's reading "
             "of it has changed.  Nothing was learned about the binary."])
    assertions.add("PASS", "step 2", "distinct payload files", unique,
                   PAYLOAD_UNIQUE_FILES)

    exe = os.path.join(outdir, "INSTDIR", "bin", "percolator.exe")
    if not os.path.isfile(exe):
        raise HarnessFailure("no payload binary at %s" % exe)
    observed = sha256_file(exe)
    size = os.path.getsize(exe)
    t.kv("payload binary", exe)
    t.kv("bytes", "%d (expected %d)" % (size, PAYLOAD_EXE_BYTES))
    t.kv("sha256 observed", observed)
    t.kv("sha256 expected", PAYLOAD_EXE_SHA256)
    t.kv("match", "yes" if observed == PAYLOAD_EXE_SHA256 else "NO")
    if observed != PAYLOAD_EXE_SHA256:
        raise HarnessFailure(
            "payload percolator.exe SHA-256 mismatch -- HARD STOP",
            ["expected %s" % PAYLOAD_EXE_SHA256,
             "observed %s" % observed,
             "This run would not be pinned to the bytes the Linux-side "
             "evidence was gathered from.  Nothing was learned."])
    assertions.add("PASS", "step 2", "payload exe sha256", observed,
                   PAYLOAD_EXE_SHA256)
    assertions.add("PASS", "step 2", "payload exe bytes", size,
                   PAYLOAD_EXE_BYTES)

    t.log("  the two XSD companions:")
    for relpath, want in ((XSD_IN_RELPATH, XSD_IN_SHA256),
                          (XSD_OUT_RELPATH, XSD_OUT_SHA256)):
        path = os.path.join(outdir, "INSTDIR", *relpath.split("/"))
        if not os.path.isfile(path):
            raise HarnessFailure("missing XSD %s" % path)
        got = sha256_file(path)
        t.log("      %s" % path)
        t.log("        install path INSTDIR/%s" % relpath)
        t.log("        bytes %d" % os.path.getsize(path))
        t.log("        sha256 observed %s" % got)
        t.log("        sha256 expected %s" % want)
        t.log("        match %s" % ("yes" if got == want else "NO"))
        if got != want:
            raise HarnessFailure("XSD SHA-256 mismatch for %s" % relpath,
                                 ["expected %s" % want, "observed %s" % got])
        assertions.add("PASS", "step 2", os.path.basename(relpath), got, want)
    return exe, os.path.join(outdir, "INSTDIR")


def generate_pin(t, assertions):
    t.log("  generating the %d-PSM synthetic PIN with the one generator both "
          "platforms call" % PIN_ROWS)
    generator = os.path.join(PROJECT_ROOT, "scripts", "feasibility",
                             "make_synthetic_pin.py")
    if not os.path.isfile(generator):
        raise HarnessFailure("no PIN generator at %s" % generator)
    pin = os.path.join(WORK_DIR, "test.pin")
    rec = run_process([sys.executable, generator, pin, "--print-sha256",
                       "--expect-sha256", PIN_SHA256],
                      WORK_DIR, TIMEOUT_PIN, "make_synthetic_pin.py", t)
    if rec["timed_out"] or rec["launch_error"] or rec["exit_code"] != 0:
        raise HarnessFailure(
            "make_synthetic_pin.py did not produce the pinned input (exit %s)"
            % fmt_exit(rec["exit_code"]),
            ["Without the same input the Windows/Linux comparison is "
             "meaningless.  Nothing was learned about the binary."])
    if not os.path.isfile(pin):
        raise HarnessFailure("the PIN generator exited 0 but wrote no %s" % pin)
    observed = sha256_file(pin)
    matches = observed == PIN_SHA256
    t.kv("pin path", pin)
    t.kv("pin bytes", os.path.getsize(pin))
    t.kv("pin sha256 observed", observed)
    t.kv("pin sha256 expected", PIN_SHA256)
    t.log("  pin_matches_pinned_linux_input: %s" % ("yes" if matches else "NO"))
    if not matches:
        raise HarnessFailure(
            "the generated PIN is not the pinned Linux input -- HARD STOP",
            ["expected %s" % PIN_SHA256, "observed %s" % observed,
             "Every Percolator number recorded on the Linux side was measured "
             "with the pinned input; comparing against a different one proves "
             "nothing."])
    assertions.add("PASS", "step 4", "pin sha256", observed, PIN_SHA256)
    return pin


def log_pout(t, pout, reference_bytes):
    t.kv("output file", pout["path"])
    t.kv("exists", "yes" if pout["exists"] else "NO")
    t.kv("bytes", "%d (the Linux twin wrote %d from this input)"
         % (pout["size"], reference_bytes))
    t.kv("'<psm ' elements", "%d (the Linux twin wrote %d)"
         % (pout["psm_count"], LINUX_PSM_COUNT))
    t.block("first ten lines:", pout["head"])


def step6_privilege(t, assertions, launched):
    t.section("step 6 -- elevation and the account this ran under")
    is_admin = None
    detail = ""
    if os.name == "nt":
        try:
            import ctypes
            is_admin = bool(ctypes.windll.shell32.IsUserAnAdmin())
            detail = "ctypes.windll.shell32.IsUserAnAdmin()"
        except Exception as exc:                      # pragma: no cover
            detail = "IsUserAnAdmin() raised %s: %s" % (type(exc).__name__, exc)
    else:
        try:
            is_admin = os.geteuid() == 0
            detail = "os.geteuid() == 0 (this is not Windows; recorded for "
            detail += "completeness only)"
        except AttributeError:                        # pragma: no cover
            detail = "no geteuid on this platform"
    t.kv("account is administrator", "unknown" if is_admin is None
         else ("YES" if is_admin else "no"))
    t.kv("determined by", detail)
    if is_admin:
        t.log("  A hosted GitHub runner runs as an administrator.  Recording")
        t.log("  that honestly matters: it means THE STANDARD-USER CASE IS")
        t.log("  STILL UNTESTED, and the standard-user case is the one the")
        t.log("  product actually needs.")
    if os.name == "nt":
        run_process(["whoami", "/groups"], WORK_DIR, TIMEOUT_WHOAMI,
                    "whoami /groups", t)
        run_process(["whoami"], WORK_DIR, TIMEOUT_WHOAMI, "whoami", t)
    t.log()
    t.log("  every process the checklist steps launched, in order (section")
    t.log("  8 comes after this step and lists its own):")
    if not launched:
        t.log("      (none -- no Windows binary was executed in this mode)")
    for index, rec in enumerate(launched):
        if rec["launch_error"]:
            outcome = "LAUNCH FAILED: %s" % rec["launch_error"]
        elif rec["timed_out"]:
            outcome = "TIMED OUT after %s s" % rec["timeout_s"]
        else:
            outcome = "exit %s" % fmt_exit(rec["exit_code"])
        t.log("      %d. %s" % (index + 1, " ".join(repr(a) for a in rec["argv"])))
        t.log("         cwd %s" % rec["cwd"])
        t.log("         %s" % outcome)
    t.log()
    t.kv("installer executed", "NO -- percolator-v3-07.exe was never run")
    t.kv("elevation prompt observed", "no")
    t.log("  Justification, so that 'no' is a fact and not an assumption: the")
    t.log("  only executable with a requireAdministrator manifest in this")
    t.log("  material is the NSIS installer, and it was not executed -- the")
    t.log("  payload was decompressed by a Python script instead.  Every")
    t.log("  process listed above is either a Python interpreter or a payload")
    t.log("  console binary, none of which carries an elevation manifest.  A")
    t.log("  non-interactive runner cannot answer a UAC prompt, so a step that")
    t.log("  needed elevation would have failed rather than silently waited.")
    assertions.add("RECORDED", "step 6", "is administrator",
                   "unknown" if is_admin is None else ("yes" if is_admin else "no"))
    assertions.add("RECORDED", "step 6", "installer executed", "no")
    return is_admin


def section8(t, assertions, pin, check_only, findings, step5_finding):
    t.section("section 8, beyond the checklist: the artefact the product "
              "actually ships (D-002 option C)")
    t.log("This is NOT part of the seven-step checklist, which is the gate and")
    t.log("is unchanged above.  The owner took D-002 option C on 2026-08-29:")
    t.log("Percolator's binary now comes from the PORTABLE noxml ZIP on every")
    t.log("tier-1 platform, so the checklist above exercises an artefact the")
    t.log("product no longer ships.  While a Windows machine is available,")
    t.log("this section exercises the binary the product WILL ship.")
    t.log()
    t.log("This section reports its own verdict and does NOT gate the job's")
    t.log("exit status, with one exception: if the noxml binary RUNS and yet")
    t.log("writes no Percolator XML, or writes empty XML, that contradicts the")
    t.log("premise D-002 option C was decided on and the job fails loudly.")
    t.log()

    zip_path = os.path.join(WORK_DIR, "percolator-noxml-windows-portable.zip")
    record = fetch(PORTABLE_URL, zip_path, PORTABLE_SHA256, PORTABLE_BYTES, t)
    assertions.add("PASS", "sect 8", "portable zip sha256", record["sha256"],
                   PORTABLE_SHA256)

    portable_dir = os.path.join(WORK_DIR, "portable")
    if not os.path.isdir(portable_dir):
        os.makedirs(portable_dir)
    with zipfile.ZipFile(zip_path) as archive:
        names = archive.namelist()
        t.kv("zip members", repr(names))
        member = None
        for name in names:
            if os.path.basename(name.replace("\\", "/")) == PORTABLE_EXE_NAME:
                member = name
                break
        if member is None:
            raise HarnessFailure("no %s in %s" % (PORTABLE_EXE_NAME, zip_path))
        # Written to a path this program chose, never to a path the archive
        # chose: a zip member name is untrusted input.
        blob = archive.read(member)
    portable_exe = os.path.join(portable_dir, PORTABLE_EXE_NAME)
    with open(portable_exe, "wb") as handle:
        handle.write(blob)
    observed = hashlib.sha256(blob).hexdigest()
    t.kv("extracted with", "the standard library's zipfile -- no installer, "
                           "no elevation")
    t.kv("payload", portable_exe)
    t.kv("bytes", "%d (expected %d)" % (len(blob), PORTABLE_EXE_BYTES))
    t.kv("sha256 observed", observed)
    t.kv("sha256 expected", PORTABLE_EXE_SHA256)
    t.kv("match", "yes" if observed == PORTABLE_EXE_SHA256 else "NO")
    if observed != PORTABLE_EXE_SHA256:
        raise HarnessFailure(
            "portable percolator.exe SHA-256 mismatch -- HARD STOP",
            ["expected %s" % PORTABLE_EXE_SHA256, "observed %s" % observed])
    assertions.add("PASS", "sect 8", "portable exe sha256", observed,
                   PORTABLE_EXE_SHA256)
    t.log()
    t.log("  NOTE: nothing was copied next to this binary.  The MSVC runtime")
    t.log("  DLLs from the NSIS payload were NOT placed beside it, because the")
    t.log("  open question about this artefact is whether the ZIP alone is")
    t.log("  enough.  A missing-DLL failure here is the expected failure mode")
    t.log("  and is information, not a harness fault.")

    if check_only:
        t.log()
        t.log("  --check-only: the noxml binary was NOT executed.")
        assertions.add("SKIPPED", "sect 8", "noxml execution",
                       "not executed (--check-only)")
        return None, []

    section_status = "OK"
    section_reasons = []
    records = []

    t.log()
    t.log("  8a. percolator.exe --help (the portable noxml build)")
    help_rec = run_process([portable_exe, "--help"], portable_dir,
                           TIMEOUT_HELP, "noxml --help", t)
    records.append(help_rec)
    log_markers(t, markers_of(help_rec))
    if help_rec["launch_error"]:
        t.kv("note", "the portable build could not be launched at all; on "
                     "Windows a missing DLL usually shows up instead as exit "
                     "3221225781 (0xC0000135, STATUS_DLL_NOT_FOUND)")
    elif help_rec["exit_code"] != 0:
        t.kv("note", "the portable build exited %s; 3221225781 (0xC0000135) is "
                     "STATUS_DLL_NOT_FOUND, which is the expected failure mode "
                     "for a ZIP that carries no Visual C++ runtime"
             % fmt_exit(help_rec["exit_code"]))

    t.log()
    t.log("  8b. percolator.exe -X pout-noxml.xml test.pin (same 400-PSM PIN)")
    pout_path = os.path.join(WORK_DIR, "pout-noxml.xml")
    if os.path.isfile(pout_path):
        os.remove(pout_path)
    x_rec = run_process([portable_exe, "-X", pout_path, pin], portable_dir,
                        TIMEOUT_RUN, "noxml -X", t)
    records.append(x_rec)
    pout = read_pout(pout_path)
    log_pout(t, pout, LINUX_NOXML_X_BYTES)
    x_finding = analyse_x_run(x_rec, pout, step="sect 8",
                              reference_bytes=LINUX_NOXML_X_BYTES)
    marks = markers_of(x_rec)
    ran = (not x_rec["launch_error"] and not x_rec["timed_out"]
           and marks["banner"])
    gating = None
    if ran and (not pout["exists"] or pout["size"] == 0
                or pout["psm_count"] == 0):
        section_status = "NEGATIVE"
        section_reasons.append(
            "THE NOXML BINARY RAN AND WROTE NO USABLE PERCOLATOR XML: "
            "exists=%s, %d bytes, %d '<psm ' elements, exit %s.  D-002 option "
            "C rests on this artefact being able to write the XML the "
            "Limelight path consumes."
            % (pout["exists"], pout["size"], pout["psm_count"],
               fmt_exit(x_rec["exit_code"])))
        gating = "NEGATIVE"
    elif not ran:
        section_status = "INCONCLUSIVE"
        section_reasons.append(
            "the portable noxml binary did not run (launch_error=%s, "
            "timed_out=%s, banner=%s).  A missing Visual C++ runtime is the "
            "expected failure mode for this artefact."
            % (x_rec["launch_error"], x_rec["timed_out"], marks["banner"]))
    elif x_finding.status != "OK":
        section_status = "DIVERGENT"
        section_reasons.extend(x_finding.reasons)
    else:
        section_reasons.extend(x_finding.reasons)

    t.log()
    t.log("  8c. percolator.exe --xml-in test.pin -- the POSITIVE CONTROL")
    t.log("      Here the diagnostic SHOULD appear: this is the noxml build.")
    t.log("      If step 5 reports it absent on the XML build and it is absent")
    t.log("      here too, the detector is broken and no result is claimed.")
    xmlin_rec = run_process([portable_exe, "--xml-in", pin], portable_dir,
                            TIMEOUT_RUN, "noxml --xml-in", t)
    records.append(xmlin_rec)
    log_markers(t, markers_of(xmlin_rec))
    control = analyse_xml_in_control(xmlin_rec)
    t.kv("positive control", control.status)
    for reason in control.reasons:
        t.log("      %s" % reason)

    if control.status == "NEGATIVE" and step5_finding.status == "OK":
        step5_finding.downgrade(
            "INCONCLUSIVE",
            "THE DETECTOR COULD NOT BE SHOWN TO WORK ON THIS HOST: the noxml "
            "build ran and did not print %r either, so step 5's absence result "
            "is not evidence.  This is reported as INCONCLUSIVE rather than as "
            "a result." % MARKER_XML_OFF)
    elif control.status == "INCONCLUSIVE":
        t.log("      The positive control could not be obtained on this host, "
              "so step 5's absence result rests on its own positive markers "
              "alone.")

    t.log()
    t.kv("section 8 verdict", section_status)
    for reason in section_reasons:
        t.log("      %s" % reason)
    t.log("  A hosted runner is NOT a clean machine: GitHub's windows-latest")
    t.log("  image ships Visual Studio and its redistributables.  This section")
    t.log("  therefore cannot settle, either way, whether the portable ZIP")
    t.log("  needs a Visual C++ runtime it does not carry.")
    assertions.add(section_status if section_status != "OK" else "PASS",
                   "sect 8", "noxml -X output",
                   "exists=%s, %d bytes, %d '<psm '" % (pout["exists"],
                                                        pout["size"],
                                                        pout["psm_count"]))
    assertions.add(control.status if control.status != "OK" else "PASS",
                   "sect 8", "XML_SUPPORT positive control",
                   "diagnostic %s" % ("present" if markers_of(xmlin_rec)
                                      ["xml_support_off"] else "ABSENT"))
    record_finding(findings, "section 8  the portable noxml build",
                   Finding("section 8", section_status, section_reasons),
                   gating=False)
    return gating, records


def record_finding(findings, label, finding, gating=True):
    """Keep the Finding object itself, not a snapshot of its status.

    Section 8 can DOWNGRADE step 5 after step 5 has already been recorded (see
    analyse_xml_in_control).  Storing a copy of the status here would freeze
    the pre-downgrade answer and report a result the harness had already
    decided it could not claim.
    """
    findings.append({"label": label, "finding": finding, "gating": gating})


def verdict_block(t, verdict, assertions, findings, notes):
    t.section("VERDICT")
    t.log("verdict:   %s" % verdict)
    t.log("exit code: %d" % VERDICT_EXIT[verdict])
    t.log()
    t.log("What each verdict means:")
    t.log("  HARNESS FAILURE  download, checksum, extraction, Python or the PIN")
    t.log("                   generator failed.  NOTHING WAS LEARNED ABOUT THE")
    t.log("                   BINARY.                                 exit %d"
          % EXIT_HARNESS_FAILURE)
    t.log("  NEGATIVE         the binary ran and the evidence CONTRADICTS an")
    t.log("                   inference this project relies on.       exit %d"
          % EXIT_NEGATIVE)
    t.log("  INCONCLUSIVE     the binary did not run far enough for the test to")
    t.log("                   mean anything.                          exit %d"
          % EXIT_INCONCLUSIVE)
    t.log("  PASS             every checklist assertion held, each naming the")
    t.log("                   value it observed.                      exit %d"
          % EXIT_PASS)
    t.log("  CHECK-ONLY       the platform-independent steps ran; NO WINDOWS")
    t.log("                   BINARY WAS EXECUTED.  This is not a pass. exit %d"
          % EXIT_PASS)
    t.log()
    t.log("Assertions, each naming the value observed (exit code 0 proves")
    t.log("nothing; no step below is 'ok' merely because a command returned 0):")
    assertions.render(t)
    t.log()
    t.log("Findings by step:")
    if not findings:
        t.log("  (none -- no step that produces a finding was executed)")
    for entry in findings:
        finding = entry["finding"]
        t.log("  %-52s %s%s" % (entry["label"], finding.status,
                                "" if entry["gating"] else "   (does not gate)"))
        for reason in finding.reasons:
            for line in _wrap(reason, 66):
                t.log("      %s" % line)
    if notes:
        t.log()
        t.log("Notes:")
        for note in notes:
            for line in _wrap(note, 70):
                t.log("  %s" % line)
    t.log()
    t.log("This transcript records what was OBSERVED on the machine named in")
    t.log("the host block above.  It does not, on its own, make the Windows")
    t.log("binary verified, confirmed, proven or tested.")


def _wrap(text, width):
    words = str(text).split()
    lines = []
    current = ""
    for word in words:
        if current and len(current) + 1 + len(word) > width:
            lines.append(current)
            current = word
        else:
            current = (current + " " + word) if current else word
    if current:
        lines.append(current)
    return lines or [""]


def run(check_only, t):
    assertions = Assertions()
    findings = []
    notes = []
    launched = []

    t.log("%s -- the seven-step Windows Percolator checklist, automated." % SELF)
    t.log("Source of the checklist: docs/feasibility/windows-artefact.rst,")
    t.log("section 'Recommended: the checklist for a Windows machine'.")
    t.log("Captured process output is escaped to ASCII and CRLF-normalised;")
    t.log("nothing else about it is altered.")
    host_block(t)

    if check_only:
        t.log()
        t.log("  MODE: --check-only.  Every platform-independent step runs")
        t.log("  (download, checksums, extraction, payload checksums, PIN")
        t.log("  generation, portable ZIP).  NO WINDOWS BINARY IS EXECUTED,")
        t.log("  and nothing about Windows is inferred.")

    installer = step1_download(t, assertions)
    exe, instdir = step2_extract(t, assertions, installer)

    step5_finding = None

    # ---- step 3
    t.section("step 3 -- percolator.exe --help, captured whole")
    if check_only:
        t.log("  SKIPPED: --check-only, and this host is %s." % platform.system())
        assertions.add("SKIPPED", "step 3", "--help", "not executed")
    else:
        rec = run_process([exe, "--help"], instdir, TIMEOUT_HELP,
                          "percolator.exe --help", t)
        launched.append(rec)
        log_markers(t, markers_of(rec))
        t.kv("first line", (rec["combined"].split("\n") or [""])[0])
        t.kv("Linux reference first line", LINUX_BANNER)
        finding = analyse_help(rec)
        record_finding(findings, "step 3  --help", finding)
        assertions.add(finding.status if finding.status != "OK" else "PASS",
                       "step 3", "--help exit code", fmt_exit(rec["exit_code"]),
                       "0")
        assertions.add(finding.status if finding.status != "OK" else "PASS",
                       "step 3", "--help contains --xmloutput",
                       "yes" if markers_of(rec)["xmloutput_option"] else "NO")

    # ---- step 4
    t.section("step 4 -- generate the pinned PIN, then percolator.exe -X")
    pin = generate_pin(t, assertions)
    if check_only:
        t.log("  -X SKIPPED: --check-only, and this host is %s."
              % platform.system())
        assertions.add("SKIPPED", "step 4", "-X run", "not executed")
    else:
        pout_path = os.path.join(WORK_DIR, "pout.xml")
        if os.path.isfile(pout_path):
            os.remove(pout_path)
        rec = run_process([exe, "-X", pout_path, pin], WORK_DIR, TIMEOUT_RUN,
                          "percolator.exe -X", t)
        launched.append(rec)
        pout = read_pout(pout_path)
        log_pout(t, pout, LINUX_XML_X_BYTES)
        finding = analyse_x_run(rec, pout)
        record_finding(findings, "step 4  -X pout.xml test.pin", finding)
        assertions.add(finding.status if finding.status != "OK" else "PASS",
                       "step 4", "-X exit code", fmt_exit(rec["exit_code"]), "0")
        assertions.add(finding.status if finding.status != "OK" else "PASS",
                       "step 4", "-X output",
                       "exists=%s, %d bytes, %d '<psm '"
                       % (pout["exists"], pout["size"], pout["psm_count"]),
                       "exists=True, %d '<psm '" % LINUX_PSM_COUNT)

    # ---- step 5
    t.section("step 5 -- THE DISCRIMINATING TEST: percolator.exe --xml-in")
    t.log("An XML_SUPPORT=ON build must NOT print %r." % MARKER_XML_OFF)
    t.log("But nor does a binary that never started.  So the absence counts")
    t.log("only when the SAME captured output proves the binary started (the")
    t.log("version banner) and reached the pin-XML input path.  Without those,")
    t.log("the verdict is INCONCLUSIVE, not PASS.")
    t.log("The compiled-in XSD path does not exist on this machine, so this")
    t.log("step is EXPECTED to fail or crash; on Linux the XML build")
    t.log("segmentation-faults (exit 139) after 'unable to load'.  That is")
    t.log("fine: the test is what it printed, not whether it survived.")
    t.log()
    if check_only:
        t.log("  SKIPPED: --check-only, and this host is %s." % platform.system())
        assertions.add("SKIPPED", "step 5", "--xml-in", "not executed")
        step5_finding = Finding("step 5", "SKIPPED", ["--check-only"])
    else:
        t.kv("working directory used", instdir)
        t.log("  (the extracted INSTDIR, so that if the XSD lookup is relative")
        t.log("   it can resolve against share\\xml\\percolator\\...)")
        rec = run_process([exe, "--xml-in", pin], instdir, TIMEOUT_RUN,
                          "percolator.exe --xml-in", t)
        launched.append(rec)
        log_markers(t, markers_of(rec))
        t.kv("'%s' present" % MARKER_XML_OFF,
             "YES" if markers_of(rec)["xml_support_off"] else "no")
        step5_finding = analyse_xml_in(rec)

    if not check_only:
        record_finding(findings, "step 5  --xml-in (discriminating test)",
                       step5_finding)

    # ---- step 6
    step6_privilege(t, assertions, launched)
    notes.append("The hosted-runner account type is recorded in step 6.  If it "
                 "is an administrator, the standard-user case -- the one the "
                 "product actually needs -- remains untested.")

    # ---- section 8
    gating, _ = section8(t, assertions, pin, check_only, findings,
                         step5_finding or Finding("step 5", "SKIPPED", []))

    if not check_only:
        # Added AFTER section 8, because section 8's positive control can
        # downgrade step 5 and the assertion must name the final answer.
        assertions.add("PASS" if step5_finding.status == "OK"
                       else step5_finding.status,
                       "step 5", "XML_SUPPORT diagnostic",
                       "absent, and the captured output shows the binary "
                       "started and reached the pin-XML path"
                       if step5_finding.status == "OK"
                       else "see the step 5 finding below")

    # ---- step 7
    t.section("step 7 -- the transcript")
    t.kv("transcript written to", t.path)
    t.kv("transcript lines so far", len(t.lines))
    t.log("  The same text is on stdout, so the job log carries it even if the")
    t.log("  artifact upload does not happen.")

    if check_only:
        verdict = "CHECK-ONLY"
        notes.append("--check-only: no Windows binary was executed.  This run "
                     "exercises the harness (download, checksums, extraction, "
                     "payload checksums, PIN generation, portable ZIP) and "
                     "establishes NOTHING about Windows.")
    else:
        statuses = [entry["finding"].status for entry in findings
                    if entry["gating"]]
        if gating == "NEGATIVE" or "NEGATIVE" in statuses:
            verdict = "NEGATIVE"
        elif "INCONCLUSIVE" in statuses:
            verdict = "INCONCLUSIVE"
        else:
            verdict = "PASS"
    verdict_block(t, verdict, assertions, findings, notes)
    return VERDICT_EXIT[verdict]


# --------------------------------------------------------------------------
# Self-test: prove the harness can fail
# --------------------------------------------------------------------------

# Verbatim excerpts of REAL captured output from the Linux builds of
# rel-3-07-01, run on 2026-08-30 with the pinned 400-PSM PIN.  The accented
# author line is dropped so these fixtures stay ASCII; nothing else is edited.
FIXTURE_XML_XMLIN = """\
Percolator version 3.07.1, Build Date Jun 20 2024 13:21:20
Copyright (c) 2006-9 University of Washington. All rights reserved.
Issued command:
./xml/usr/bin/percolator --xml-in test.pin
Started Sun Aug 30 15:30:44 2026
Hyperparameters: selectionFdr=0.01, Cpos=0, Cneg=0, maxNiter=10
Reading pin-xml input from datafile test.pin
XML parser warning at :0:0
  warning: unable to open primary document entity '/usr/share/xml/percolator/xml-pin-1-3/percolator_in.xsd'
/usr/share/xml/percolator/xml-pin-1-3/percolator_in.xsd: error: unable to load
"""

FIXTURE_NOXML_XMLIN = """\
Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18
Copyright (c) 2006-9 University of Washington. All rights reserved.
Issued command:
./noxml/usr/bin/percolator --xml-in test.pin
Started Sun Aug 30 15:30:46 2026
Hyperparameters: selectionFdr=0.01, Cpos=0, Cneg=0, maxNiter=10
Reading pin-xml input from datafile test.pin
ERROR: Compiler flag XML_SUPPORT was off, you cannot use the -k flag for pin-format input files
ERROR: Failed to read in file, check if the correct file-format was used.
"""

FIXTURE_HELP = """\
Percolator version 3.07.1, Build Date Jun 20 2024 13:21:20
Usage:
   percolator [-X pout.xml] [other options] pin.tsv

Options:
 -X <filename>
 --xmloutput <filename>                 Path to xml-output (pout) file.
"""


def _rec(combined, exit_code=0, timed_out=False, launch_error=None):
    return {"combined": combined, "exit_code": exit_code,
            "timed_out": timed_out, "launch_error": launch_error,
            "timeout_s": TIMEOUT_RUN, "argv": ["percolator.exe"], "cwd": "."}


def _pout(exists=True, size=143729, psm=200, path="pout.xml"):
    return {"exists": exists, "size": size, "psm_count": psm, "path": path,
            "head": ""}


def self_test():
    """Damage the harness on purpose and require the right verdict.

    Every case here is one this harness must NOT get wrong: an absence that is
    not evidence, a checksum that does not match, a timeout, a binary that
    never started.  Each prints one line.  A damaged case that is ACCEPTED, or
    a control case that is REJECTED, fails the whole self-test -- because a
    harness that cannot report a negative is worse than no harness.

    Needs no network and no Windows machine: the download cases use file://
    URLs against files this function writes into a sandbox.
    """
    sandbox = os.path.join(WORK_DIR, "selftest")
    if not os.path.isdir(sandbox):
        os.makedirs(sandbox)
    quiet = Transcript(path=None, echo=False)
    results = []

    print("=== %s --self-test ===" % SELF)
    print("Damaged cases must be REJECTED; control cases must be ACCEPTED.")
    print("Sandbox: %s   (the working tree is not touched)" % sandbox)
    print()

    def case(label, got, want, detail=""):
        ok = got == want
        results.append(ok)
        print("  %s %-46s got %-14s want %-14s%s"
              % ("ok  " if ok else "FAIL", label, got, want,
                 ("  [%s]" % detail) if detail else ""))
        return ok

    # ---- group A: the checksum is a hard stop -------------------------------
    good = b"percolator-installer-stand-in\n"
    good_path = os.path.join(sandbox, "good.bin")
    with open(good_path, "wb") as handle:
        handle.write(good)
    good_sha = hashlib.sha256(good).hexdigest()
    from urllib.request import pathname2url
    good_url = "file:" + pathname2url(os.path.abspath(good_path))

    dest = os.path.join(sandbox, "fetched.bin")
    try:
        if os.path.isfile(dest):
            os.remove(dest)
        fetch(good_url, dest, good_sha, len(good), quiet, attempts=1,
              allow_cache=False)
        case("A1 control: correct sha256 is accepted", "ACCEPTED", "ACCEPTED")
    except HarnessFailure as exc:
        case("A1 control: correct sha256 is accepted", "REJECTED", "ACCEPTED",
             str(exc))

    wrong_sha = "0" * 64
    try:
        if os.path.isfile(dest):
            os.remove(dest)
        fetch(good_url, dest, wrong_sha, len(good), quiet, attempts=1,
              allow_cache=False)
        case("A2 wrong expected sha256", "ACCEPTED", "HARNESS FAILURE")
    except HarnessFailure as exc:
        text = "\n".join([exc.summary] + exc.detail)
        named = wrong_sha in text and good_sha in text
        case("A2 wrong expected sha256", "HARNESS FAILURE", "HARNESS FAILURE")
        case("A2 names both hashes in the message",
             "yes" if named else "NO", "yes")
        print("       message: %s" % exc.summary)
        for line in exc.detail[:4]:
            print("                %s" % line)

    corrupt = bytearray(good)
    corrupt[0] ^= 0xFF
    corrupt_path = os.path.join(sandbox, "corrupt.bin")
    with open(corrupt_path, "wb") as handle:
        handle.write(bytes(corrupt))
    corrupt_url = "file:" + pathname2url(os.path.abspath(corrupt_path))
    try:
        if os.path.isfile(dest):
            os.remove(dest)
        fetch(corrupt_url, dest, good_sha, len(good), quiet, attempts=1,
              allow_cache=False)
        case("A3 corrupted download, correct expectation", "ACCEPTED",
             "HARNESS FAILURE")
    except HarnessFailure as exc:
        text = "\n".join([exc.summary] + exc.detail)
        actual = hashlib.sha256(bytes(corrupt)).hexdigest()
        named = good_sha in text and actual in text
        case("A3 corrupted download, correct expectation", "HARNESS FAILURE",
             "HARNESS FAILURE")
        case("A3 names both hashes in the message",
             "yes" if named else "NO", "yes")

    try:
        if os.path.isfile(dest):
            os.remove(dest)
        fetch("file:" + pathname2url(os.path.join(sandbox, "does-not-exist")),
              dest, good_sha, None, quiet, attempts=1, allow_cache=False)
        case("A4 unreachable URL", "ACCEPTED", "HARNESS FAILURE")
    except HarnessFailure:
        case("A4 unreachable URL", "HARNESS FAILURE", "HARNESS FAILURE")

    # ---- group B: step 5, the absence test ---------------------------------
    print()
    case("B1 real noxml output (diagnostic present)",
         analyse_xml_in(_rec(FIXTURE_NOXML_XMLIN, exit_code=1)).status,
         "NEGATIVE")
    case("B2 diagnostic present but no banner",
         analyse_xml_in(_rec("ERROR: " + MARKER_XML_OFF + "\n",
                             exit_code=1)).status,
         "NEGATIVE")
    case("B3 empty output, exit 0",
         analyse_xml_in(_rec("", exit_code=0)).status, "INCONCLUSIVE")
    case("B4 truncated: banner only, never reached pin-xml",
         analyse_xml_in(_rec(LINUX_BANNER + "\n", exit_code=0)).status,
         "INCONCLUSIVE")
    case("B5 binary never launched",
         analyse_xml_in(_rec("", exit_code=None,
                             launch_error="FileNotFoundError")).status,
         "INCONCLUSIVE")
    case("B6 timed out with perfect markers",
         analyse_xml_in(_rec(FIXTURE_XML_XMLIN, exit_code=None,
                             timed_out=True)).status,
         "INCONCLUSIVE")
    case("B7 crashed with 0xC0000005, no markers",
         analyse_xml_in(_rec("", exit_code=3221225477)).status, "INCONCLUSIVE")
    case("B8 control: real XML output, markers present, no diagnostic",
         analyse_xml_in(_rec(FIXTURE_XML_XMLIN, exit_code=139)).status, "OK")

    # ---- group C: step 4, the -X run ---------------------------------------
    print()
    case("C1 control: 200 psm at exit 0",
         analyse_x_run(_rec(LINUX_BANNER + "\n", 0), _pout()).status, "OK")
    case("C2 ran but wrote no file",
         analyse_x_run(_rec(LINUX_BANNER + "\n", 0),
                       _pout(exists=False, size=0, psm=0)).status, "NEGATIVE")
    case("C3 ran but wrote an empty file",
         analyse_x_run(_rec(LINUX_BANNER + "\n", 0),
                       _pout(size=0, psm=0)).status, "NEGATIVE")
    case("C4 ran, non-zero exit",
         analyse_x_run(_rec(LINUX_BANNER + "\n", 1), _pout()).status,
         "NEGATIVE")
    case("C5 ran but wrote 0 psm elements",
         analyse_x_run(_rec(LINUX_BANNER + "\n", 0), _pout(psm=0)).status,
         "NEGATIVE")
    case("C6 never started, no file",
         analyse_x_run(_rec("", 3221225781),
                       _pout(exists=False, size=0, psm=0)).status,
         "INCONCLUSIVE")
    case("C7 timed out",
         analyse_x_run(_rec(LINUX_BANNER + "\n", None, timed_out=True),
                       _pout()).status, "INCONCLUSIVE")

    # ---- group D: step 3, --help -------------------------------------------
    print()
    case("D1 control: real help output at exit 0",
         analyse_help(_rec(FIXTURE_HELP, 0)).status, "OK")
    case("D2 empty output at exit 0",
         analyse_help(_rec("", 0)).status, "INCONCLUSIVE")
    case("D3 ran but no --xmloutput in the help",
         analyse_help(_rec(LINUX_BANNER + "\nOptions:\n -h\n", 0)).status,
         "NEGATIVE")
    case("D4 ran, help exited non-zero",
         analyse_help(_rec(FIXTURE_HELP, 1)).status, "NEGATIVE")
    case("D5 DLL missing (0xC0000135), nothing captured",
         analyse_help(_rec("", 3221225781)).status, "INCONCLUSIVE")

    # ---- group E: section 8's positive control, and the downgrade ----------
    print()
    case("E1 control: noxml prints the diagnostic",
         analyse_xml_in_control(_rec(FIXTURE_NOXML_XMLIN, 1)).status, "OK")
    case("E2 noxml ran but printed no diagnostic",
         analyse_xml_in_control(_rec(FIXTURE_XML_XMLIN, 139)).status,
         "NEGATIVE")
    case("E3 noxml never started",
         analyse_xml_in_control(_rec("", 3221225781)).status, "INCONCLUSIVE")

    step5 = analyse_xml_in(_rec(FIXTURE_XML_XMLIN, exit_code=139))
    control = analyse_xml_in_control(_rec(FIXTURE_XML_XMLIN, 139))
    if control.status == "NEGATIVE" and step5.status == "OK":
        step5.downgrade("INCONCLUSIVE", "detector unproven on this host")
    case("E4 step 5 OK is DOWNGRADED when the control fails",
         step5.status, "INCONCLUSIVE")

    # ---- group F: exit-code mapping ----------------------------------------
    print()
    case("F1 NEGATIVE maps to a non-zero exit",
         VERDICT_EXIT["NEGATIVE"], EXIT_NEGATIVE)
    case("F2 INCONCLUSIVE maps to a non-zero exit",
         VERDICT_EXIT["INCONCLUSIVE"], EXIT_INCONCLUSIVE)
    case("F3 HARNESS FAILURE maps to a non-zero exit",
         VERDICT_EXIT["HARNESS FAILURE"], EXIT_HARNESS_FAILURE)
    case("F4 only PASS maps to 0", VERDICT_EXIT["PASS"], EXIT_PASS)

    failed = results.count(False)
    print()
    print("  %d case(s), %d failed." % (len(results), failed))
    if failed:
        print("  SELF-TEST FAILED: a damaged case was accepted or a control")
        print("  case was rejected.  This harness cannot be trusted to report")
        print("  a negative.")
        return EXIT_SELF_TEST_FAILED
    print("  Every damaged case was rejected and every control accepted.")
    return EXIT_PASS


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------

class _Parser(argparse.ArgumentParser):
    def error(self, message):
        sys.stderr.write("%s: %s\n" % (SELF, message))
        sys.exit(EXIT_MISUSE)


def main(argv=None):
    parser = _Parser(
        prog=SELF,
        description="Run the Windows Percolator verification checklist.",
        epilog="Exit: 0 PASS (or a clean --check-only / --self-test), "
               "1 NEGATIVE, 2 INCONCLUSIVE, 3 HARNESS FAILURE, "
               "4 REFUSED (not a Windows host), 5 MISUSE, "
               "6 SELF-TEST FAILED.")
    parser.add_argument("--check-only", action="store_true",
                        help="run only the platform-independent steps "
                             "(download, checksums, extraction, payload "
                             "checksums, PIN generation, portable ZIP) and "
                             "exit 0. NO WINDOWS BINARY IS EXECUTED and "
                             "nothing about Windows is established.")
    parser.add_argument("--self-test", action="store_true",
                        help="damage the harness on purpose and check that the "
                             "right verdict comes out; needs no network and no "
                             "Windows machine")
    args = parser.parse_args(argv)

    if args.self_test and args.check_only:
        sys.stderr.write("%s: --self-test and --check-only are exclusive\n" % SELF)
        return EXIT_MISUSE
    if args.self_test:
        if not os.path.isdir(WORK_DIR):
            os.makedirs(WORK_DIR)
        return self_test()

    if os.name != "nt" and not args.check_only:
        message = [
            "%s: REFUSED -- this is not a Windows host." % SELF,
            "  os.name=%r sys.platform=%r platform=%s"
            % (os.name, sys.platform, platform.platform()),
            "  Nothing about Windows can be inferred here, and this script will",
            "  not pretend otherwise.  Run it on a Windows machine or a",
            "  windows-latest runner.",
            "  To exercise the platform-independent half of the harness on this",
            "  machine, use:  bash scripts/ci/windows-percolator-verify.sh "
            "--check-only",
            "  Exit status %d = REFUSED." % EXIT_REFUSED,
        ]
        # stderr only, once: a doubled message reads like a bug, and a CI job
        # log carries both streams anyway.
        sys.stderr.write("\n".join(message) + "\n")
        return EXIT_REFUSED

    if not os.path.isdir(WORK_DIR):
        os.makedirs(WORK_DIR)
    t = Transcript(os.path.join(WORK_DIR, "transcript.txt"))
    try:
        try:
            return run(args.check_only, t)
        except HarnessFailure as exc:
            t.section("VERDICT")
            t.log("verdict:   HARNESS FAILURE")
            t.log("exit code: %d" % EXIT_HARNESS_FAILURE)
            t.log()
            t.log("  %s" % exc.summary)
            for line in exc.detail:
                t.log("      %s" % line)
            t.log()
            t.log("  A HARNESS FAILURE means the download, checksum,")
            t.log("  extraction, Python or PIN generation failed.  NOTHING WAS")
            t.log("  LEARNED ABOUT THE BINARY -- this is not a negative result")
            t.log("  about Percolator and must not be read as one.")
            return EXIT_HARNESS_FAILURE
        except Exception as exc:                       # pragma: no cover
            t.section("VERDICT")
            t.log("verdict:   HARNESS FAILURE")
            t.log("exit code: %d" % EXIT_HARNESS_FAILURE)
            t.log("  unhandled %s: %s" % (type(exc).__name__, exc))
            import traceback
            t.log(traceback.format_exc())
            t.log("  NOTHING WAS LEARNED ABOUT THE BINARY.")
            return EXIT_HARNESS_FAILURE
    finally:
        t.close()


if __name__ == "__main__":
    sys.exit(main())
