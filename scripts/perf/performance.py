#!/usr/bin/env python3
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only (see LICENSE at the repository root)
"""The performance suite (Phase 15 unit 8; PHASE-15 exit gate item 4; AC-RES-10).

"Performance thresholds pass on the dedicated runner and are recorded as a
baseline."  The specification, *Performance and resource tests*: "Measure
separately: application startup; parsing a large Comet parameter file;
rendering and filtering large PSM and peptide result sets; hashing multi-
gigabyte files; Comet execution time (informational); Percolator execution
time; peak GUI heap usage for large result tables; cancellation latency; and
project reopen time.  Thresholds shall be based on stable dedicated nightly
runners, not noisy shared pull-request runners."

What this does
==============

``run`` writes a plan (which measurements, how many repeats, the sizes of what
is generated) and drives the opt-in JUnit classes in ``cometgui-app`` that
measure through the product's own code paths
(``org.cometgui.app.performance.*``, ``org.cometgui.app.gui.Performance*``,
``org.cometgui.app.matrix.Performance*``) through Maven, sampling the host's
load while they run.  Each class writes one record per measurement: every
sample, the evidence, and named checks proving the measured work was the real
work.  This script then GRADES the records against ``performance-data.json``:

``PASS``         the median is within the threshold of this runner's profile,
                 and every check of the measured work held
``RED``          a check failed, a planned record is missing, or the median is
                 over (or, for a throughput, under) the threshold -- the line
                 names the measurement, the threshold and the observed value
``INFO``         informational, never graded on its value (Comet's execution
                 time, by the specification); its checks are still graded
``NO-BASELINE``  measured and reported, but this runner has no recorded
                 profile, so there is nothing to grade it against
``REFUSED``      a fixture measurement, and no fixture was named

Runner profiles
===============

Thresholds are keyed by a RUNNER PROFILE: a named host fingerprint (CPU model,
CPUs visible, memory, OS and architecture) with its thresholds and the baseline
they were derived from.  The profile is detected from the host, or named with
``--profile``; a named profile must also match the host.  On a host no profile
matches -- every GitHub-hosted runner today -- every measurement still RUNS and
is reported, and the verdict is ``NO BASELINE FOR THIS RUNNER PROFILE`` (exit
4): never a silent pass.  ``propose`` derives a candidate profile from a full
run by the documented rule, for review.

THE ONE PROFILE RECORDED TODAY IS A SHARED HOST, NOT THE DEDICATED RUNNER the
gate item names.  See docs/developer/testing.rst, "Performance".

The fixture
===========

Comet's and Percolator's times over real spectra and Comet's cancellation need
spectra, and take them as the version matrix's ONE named input,
``COMETGUI_MATRIX_FIXTURE`` (or ``--fixture``).  There is no default.  Without
it those measurements are REFUSED (exit 3, never green).  ``D-006``'s CI fixture
is OPEN; locally, ``scratch/fixture``.  Everything else generates its own data
at run time into the work directory and deletes it: a large parameter file, a
multi-gigabyte file of pseudo-random bytes, Phase 10's synthetic large results
fixture, a project of stand-in runs.

Usage::

    python3 scripts/perf/performance.py run [--part all|no-spectra|fixture] [--quick]
                                            [--only ID,...] [--profile NAME] [--fixture DIR]
                                            [--out DIR] [--work DIR] [--offline]
    python3 scripts/perf/performance.py grade --report DIR [--profile NAME]
    python3 scripts/perf/performance.py propose --report DIR --name NAME
    python3 scripts/perf/performance.py check-doc
    python3 scripts/perf/performance.py --self-test

Exit status:

==  ===========================================================================
0   every planned measurement PASSED (or is INFO) on a recorded profile
1   RED: a threshold exceeded, a check failed, or a record missing
2   misuse, or the data file refused
3   the fixture part was requested and refused (no fixture named)
4   NO BASELINE FOR THIS RUNNER PROFILE: measured and reported, not graded
5   check-doc: the document and the data disagree
7   --self-test: a case did not behave as required
==  ===========================================================================

Standard library only.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import platform
import shutil
import statistics
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
DATA_FILE = HERE / "performance-data.json"
DOC_FILE = ROOT / "docs" / "developer" / "testing.rst"
DEFAULT_OUT = ROOT / "_build" / "performance"
FIXTURE_ENV = "COMETGUI_MATRIX_FIXTURE"
LARGE_FIXTURE = ROOT / "scratch" / "phase10" / "large"
LARGE_GENERATOR = ROOT / "scripts" / "fixtures" / "large-results-fixture.py"
LARGE_PSMS_SHA256 = "4f7aaecd0164bc22be51de1c60dfed3e70ddf65a4283f38a1912c76ece8cfcf4"
DOC_START = ".. performance-baseline-table-start"
DOC_END = ".. performance-baseline-table-end"

PASS, RED, INFO, NO_BASELINE, REFUSED = "PASS", "RED", "INFO", "NO-BASELINE", "REFUSED"
EXIT_GREEN, EXIT_RED, EXIT_MISUSE, EXIT_REFUSED, EXIT_NO_BASELINE, EXIT_DOC, EXIT_SELF_TEST = 0, 1, 2, 3, 4, 5, 7


class Refused(Exception):
    """Misuse, or a data file that cannot be trusted."""


# ===========================================================================
# The data
# ===========================================================================

def load_data(path: Path = DATA_FILE, strict: bool = True) -> dict:
    """The data, refused if a rule is broken. Not strict (a run, a grading), the record of each
    threshold's red is not required -- a threshold is shown red BY running against an injection --
    but check-doc, in the traceability gate on every pull request, is strict."""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    problems = [p for p in validate_data(data) if strict or "never been shown red" not in p]
    if problems:
        raise Refused(f"{path} refused:\n  " + "\n  ".join(problems))
    return data


def validate_data(data: dict) -> list:
    """Every rule the data must keep; each broken rule is one line."""
    problems = []
    ids = [m.get("id") for m in data.get("measurements", [])]
    if len(set(ids)) != len(ids):
        problems.append("a measurement identifier is repeated")
    if not ids:
        problems.append("no measurement is defined")
    for m in data.get("measurements", []):
        for key in ("id", "spec", "what", "unit", "better", "part", "class", "repeats", "ceiling"):
            if key not in m:
                problems.append(f"measurement {m.get('id')} has no {key}")
        if m.get("better") not in ("lower", "higher"):
            problems.append(f"measurement {m.get('id')}: better must be lower or higher")
        if m.get("part") not in ("no-spectra", "fixture"):
            problems.append(f"measurement {m.get('id')}: part must be no-spectra or fixture")
        for mode in ("full", "quick"):
            if int(m.get("repeats", {}).get(mode, 0)) < 1:
                problems.append(f"measurement {m.get('id')} repeats at least once in {mode} mode")
    for name, profile in data.get("profiles", {}).items():
        for key in ("description", "dedicated", "match", "thresholds", "baseline"):
            if key not in profile:
                problems.append(f"profile {name} has no {key}")
        thresholds = profile.get("thresholds", {})
        for m in data.get("measurements", []):
            if m.get("informational"):
                if m["id"] in thresholds:
                    problems.append(f"profile {name}: {m['id']} is informational and may have no threshold")
                continue
            t = thresholds.get(m["id"])
            if t is None:
                problems.append(f"profile {name}: {m['id']} has no threshold")
                continue
            if not isinstance(t.get("value"), (int, float)):
                problems.append(f"profile {name}: {m['id']}'s threshold has no numeric value")
                continue
            if len(str(t.get("rationale", "")).strip()) < 40:
                problems.append(f"profile {name}: {m['id']}'s threshold has no written rationale")
            ceiling = m.get("ceiling", {}).get("value")
            if isinstance(ceiling, (int, float)):
                looser = t["value"] > ceiling if m["better"] == "lower" else t["value"] < ceiling
                if looser:
                    problems.append(f"profile {name}: {m['id']}'s threshold {t['value']} {m['unit']} is looser "
                                    f"than the level a user would see as a regression, {ceiling} {m['unit']}")
            shown = t.get("shownRed")
            if (not shown or not shown.get("injection") or not shown.get("observed")
                    or "PENDING" in (str(shown.get("injection")) + str(shown.get("observed")))):
                problems.append(f"profile {name}: {m['id']}'s threshold has never been shown red "
                                "(shownRed: the injection and the observed value)")
            base = profile.get("baseline", {}).get("measurements", {}).get(m["id"])
            if base is None:
                problems.append(f"profile {name}: {m['id']} has no baseline")
            elif isinstance(base.get("median"), (int, float)):
                beyond = (base["median"] > t["value"]) if m["better"] == "lower" else (base["median"] < t["value"])
                if beyond:
                    problems.append(f"profile {name}: {m['id']}'s own baseline median is beyond its threshold")
        for key in thresholds:
            if key not in ids:
                problems.append(f"profile {name}: a threshold for an unknown measurement {key}")
    return problems


def measurement(data: dict, mid: str) -> dict:
    for m in data["measurements"]:
        if m["id"] == mid:
            return m
    raise Refused(f"no measurement {mid}")


# ===========================================================================
# The host
# ===========================================================================

def read_text(path: str) -> str:
    try:
        return Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def cpu_model() -> str:
    for line in read_text("/proc/cpuinfo").splitlines():
        if line.startswith("model name"):
            return line.split(":", 1)[1].strip()
    return platform.processor() or "?"


def cpus_visible() -> int:
    try:
        count = len(os.sched_getaffinity(0))
    except AttributeError:
        count = os.cpu_count() or 0
    quota = read_text("/sys/fs/cgroup/cpu.max").split()
    if len(quota) == 2 and quota[0] != "max":
        count = min(count, max(1, int(int(quota[0]) / int(quota[1]))))
    return count


def memory_gib() -> float:
    total = 0
    for line in read_text("/proc/meminfo").splitlines():
        if line.startswith("MemTotal:"):
            total = int(line.split()[1]) * 1024
    limit = read_text("/sys/fs/cgroup/memory.max").strip()
    if limit.isdigit() and total:
        total = min(total, int(limit))
    return round(total / (1 << 30), 1)


def java_version() -> str:
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java"
    if not java.is_file():
        return "?"
    result = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=False)
    lines = result.stderr.strip().splitlines()
    return " / ".join(lines[:2]) if lines else "?"


def host() -> dict:
    return {
        "cpuModel": cpu_model(),
        "cpus": cpus_visible(),
        "memoryGiB": memory_gib(),
        "os": platform.system(),
        "arch": platform.machine(),
        "kernel": platform.release(),
        "governor": read_text("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").strip() or "?",
        "jdk": java_version(),
        "github": bool(os.environ.get("GITHUB_ACTIONS")),
    }


def mismatches(match: dict, fingerprint: dict) -> list:
    out = []
    for key in ("cpuModel", "cpus", "os", "arch"):
        if key in match and match[key] != fingerprint.get(key):
            out.append(f"{key} {fingerprint.get(key)!r}, the profile's {match[key]!r}")
    if "memoryGiB" in match:
        low, high = match["memoryGiB"]
        if not low <= fingerprint.get("memoryGiB", 0) <= high:
            out.append(f"memoryGiB {fingerprint.get('memoryGiB')}, the profile's {low}..{high}")
    return out


def choose_profile(data: dict, fingerprint: dict, named: str) -> tuple:
    """(name, profile, why): a profile that matches the host, or (None, None, why not)."""
    profiles = data.get("profiles", {})
    if named:
        if named not in profiles:
            return None, None, (f"no runner profile is named {named!r}; recorded profiles: "
                                f"{', '.join(sorted(profiles)) or 'none'}")
        wrong = mismatches(profiles[named]["match"], fingerprint)
        if wrong:
            return None, None, f"profile {named!r} does not match this host: " + "; ".join(wrong)
        return named, profiles[named], f"profile {named!r}, named and matching this host"
    reasons = []
    for name, profile in sorted(profiles.items()):
        wrong = mismatches(profile["match"], fingerprint)
        if not wrong:
            return name, profile, f"profile {name!r}, detected from this host"
        reasons.append(f"{name}: " + "; ".join(wrong))
    return None, None, "no recorded profile matches this host (" + " | ".join(reasons or ["none recorded"]) + ")"


class LoadSampler:
    """This container's CPU use (cgroup cpu.stat) and the host's load average, every ten seconds."""

    def __init__(self):
        self.samples = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._loop, daemon=True)

    @staticmethod
    def now() -> dict:
        usage = None
        for line in read_text("/sys/fs/cgroup/cpu.stat").splitlines():
            if line.startswith("usage_usec "):
                usage = int(line.split()[1])
        load = read_text("/proc/loadavg").split()
        return {"t": time.time(), "cgroupUsageUsec": usage,
                "loadavg1": float(load[0]) if load else None, "loadavg5": float(load[1]) if len(load) > 1 else None}

    def _loop(self):
        while not self._stop.wait(10):
            self.samples.append(self.now())

    def start(self):
        self.samples.append(self.now())
        self._thread.start()

    def stop(self) -> dict:
        self._stop.set()
        self._thread.join(timeout=15)
        self.samples.append(self.now())
        return summarise_load(self.samples)


def summarise_load(samples: list) -> dict:
    first, last = samples[0], samples[-1]
    wall = max(last["t"] - first["t"], 1e-9)
    busy = None
    if first.get("cgroupUsageUsec") is not None and last.get("cgroupUsageUsec") is not None:
        busy = round((last["cgroupUsageUsec"] - first["cgroupUsageUsec"]) / 1e6 / wall, 2)
    loads = [s["loadavg1"] for s in samples if s.get("loadavg1") is not None]
    return {
        "wallSeconds": round(wall, 1),
        "before": {"loadavg1": first.get("loadavg1"), "loadavg5": first.get("loadavg5"),
                   "cgroupUsageUsec": first.get("cgroupUsageUsec")},
        "after": {"loadavg1": last.get("loadavg1"), "loadavg5": last.get("loadavg5"),
                  "cgroupUsageUsec": last.get("cgroupUsageUsec")},
        "containerCpusBusyMean": busy,
        "loadavg1Max": max(loads) if loads else None,
        "loadavg1Mean": round(statistics.mean(loads), 2) if loads else None,
        "samples": len(samples),
    }


# ===========================================================================
# Grading
# ===========================================================================

def stats(samples: list) -> dict:
    ordered = sorted(samples)
    median = statistics.median(ordered)
    return {"n": len(ordered), "median": round(median, 3), "min": round(ordered[0], 3),
            "max": round(ordered[-1], 3),
            "spreadPercent": round(100.0 * (ordered[-1] - ordered[0]) / median, 1) if median else None}


def fmt(value, unit: str) -> str:
    if value is None:
        return "?"
    if abs(value) >= 100:
        text = f"{value:.0f}"
    elif abs(value) >= 10:
        text = f"{value:.1f}"
    else:
        text = f"{value:.2f}"
    return f"{text} {unit}"


def grade(data: dict, records: dict, planned: list, fingerprint: dict, named_profile: str,
          refused: dict) -> dict:
    """The verdict of every planned measurement, and of the run."""
    name, profile, why = choose_profile(data, fingerprint, named_profile)
    rows, problems = [], []
    for mid in planned:
        m = measurement(data, mid)
        row = {"id": mid, "spec": m["spec"], "what": m["what"], "unit": m["unit"], "better": m["better"]}
        threshold = profile["thresholds"].get(mid) if profile else None
        row["threshold"] = threshold["value"] if threshold else None
        if mid in refused:
            row.update(verdict=REFUSED, detail=refused[mid])
            rows.append(row)
            continue
        record = records.get(mid)
        if record is None:
            row.update(verdict=RED, detail="no record was written: the measuring class did not finish "
                                           "(see the Maven log)")
            problems.append(f"RED {mid}: no record")
            rows.append(row)
            continue
        samples = [s for s in record.get("samples", []) if isinstance(s, (int, float))]
        if not samples:
            row.update(verdict=RED, detail="no sample was taken")
            problems.append(f"RED {mid}: no sample")
            rows.append(row)
            continue
        row.update(stats(samples))
        row["samples"] = samples
        failed = [f"{c['name']} ({c['detail']})" for c in record.get("checks", []) if not c.get("passed")]
        failed += [f for f in record.get("failures", []) if not any(f.startswith(c.split(" (")[0]) for c in failed)]
        if not record.get("checks"):
            failed.append("the record holds no check of the measured work")
        if failed:
            row.update(verdict=RED, detail="a check of the measured work failed: " + "; ".join(failed))
            problems.append(f"RED {mid}: " + "; ".join(failed))
            rows.append(row)
            continue
        observed = row["median"]
        if m.get("informational"):
            row.update(verdict=INFO, detail="informational: the specification gives it no threshold")
        elif profile is None:
            row.update(verdict=NO_BASELINE, detail="measured; no recorded profile to grade it against")
        else:
            over = observed > threshold["value"] if m["better"] == "lower" else observed < threshold["value"]
            if over:
                word = "over" if m["better"] == "lower" else "under"
                detail = (f"{m['what']}: median {fmt(observed, m['unit'])} over N={row['n']} is {word} its "
                          f"threshold {fmt(threshold['value'], m['unit'])} on runner profile {name}")
                row.update(verdict=RED, detail=detail)
                problems.append(f"RED {mid} -- {detail}")
            else:
                row.update(verdict=PASS, detail=f"within {fmt(threshold['value'], m['unit'])}")
        rows.append(row)
    verdicts = [r["verdict"] for r in rows]
    if RED in verdicts:
        status, summary = EXIT_RED, "RED"
    elif profile is None:
        status, summary = EXIT_NO_BASELINE, "NO BASELINE FOR THIS RUNNER PROFILE"
    elif REFUSED in verdicts:
        status, summary = EXIT_REFUSED, "REFUSED (the fixture part)"
    else:
        status, summary = EXIT_GREEN, "GREEN"
    return {"profile": name, "profileWhy": why, "dedicated": bool(profile and profile.get("dedicated")),
            "rows": rows, "problems": problems, "status": status, "summary": summary}


def render(report: dict) -> str:
    out = []
    out.append("CometGUI performance suite (PHASE-15 exit gate item 4; specification, Performance and "
               "resource tests)")
    out.append(f"generated {report.get('generated', '?')}   git {report.get('gitHead', '?')}   "
               f"mode {report.get('mode', '?')}   part {report.get('part', '?')}")
    h = report.get("host", {})
    out.append(f"host      {h.get('cpuModel')}; {h.get('cpus')} CPUs visible; {h.get('memoryGiB')} GiB; "
               f"{h.get('os')} {h.get('arch')} {h.get('kernel')}; governor {h.get('governor')}")
    out.append(f"jdk       {h.get('jdk')}")
    load = report.get("load") or {}
    if load:
        out.append(f"load      loadavg1 {load['before']['loadavg1']} before, {load['after']['loadavg1']} after, "
                   f"max {load.get('loadavg1Max')}, mean {load.get('loadavg1Mean')}; this container's CPUs busy "
                   f"(cgroup cpu.stat) mean {load.get('containerCpusBusyMean')} over {load.get('wallSeconds')} s")
    out.append(f"profile   {report['grading']['profileWhy']}")
    if report["grading"]["profile"] and not report["grading"]["dedicated"]:
        out.append("          THIS PROFILE IS A SHARED HOST, NOT THE DEDICATED RUNNER gate item 4 names: a PASS "
                   "here is evidence")
        out.append("          about this host, not the dedicated-runner verdict (docs/developer/testing.rst, "
                   "Performance).")
    if report.get("only"):
        out.append(f"PARTIAL   --only {','.join(report['only'])}: only these were measured and graded")
    out.append("")
    out.append(f"{'verdict':<12} {'measurement':<26} {'N':>3} {'median':>12} {'min':>10} {'max':>10} "
               f"{'threshold':>12}")
    for r in report["grading"]["rows"]:
        out.append(f"{r['verdict']:<12} {r['id']:<26} {r.get('n', ''):>3} {fmt(r.get('median'), r['unit']):>12} "
                   f"{fmt(r.get('min'), ''):>10} {fmt(r.get('max'), ''):>10} "
                   f"{(fmt(r['threshold'], r['unit']) if r.get('threshold') is not None else '-'):>12}")
        out.append(f"{'':<12} ({r['spec']}) {r['detail']}")
    out.append("")
    for problem in report["grading"]["problems"]:
        out.append(problem)
    out.append(f"performance: {report['grading']['summary']} -- "
               f"{sum(1 for r in report['grading']['rows'] if r['verdict'] == PASS)} PASS, "
               f"{sum(1 for r in report['grading']['rows'] if r['verdict'] == RED)} RED, "
               f"{sum(1 for r in report['grading']['rows'] if r['verdict'] == INFO)} INFO, "
               f"{sum(1 for r in report['grading']['rows'] if r['verdict'] == NO_BASELINE)} NO-BASELINE, "
               f"{sum(1 for r in report['grading']['rows'] if r['verdict'] == REFUSED)} REFUSED")
    if report["grading"]["status"] == EXIT_NO_BASELINE:
        out.append("NO BASELINE FOR THIS RUNNER PROFILE: every measurement above ran and is reported, and none "
                   "is graded. This is not a pass. Record a profile for this runner (performance.py propose) "
                   "from full runs on it, review it, and commit it to scripts/perf/performance-data.json.")
    return "\n".join(out) + "\n"


def read_records(report_dir: Path) -> dict:
    records = {}
    directory = Path(report_dir) / "measurements"
    if directory.is_dir():
        for file in sorted(directory.glob("*.json")):
            record = json.loads(file.read_text(encoding="utf-8"))
            records[record["id"]] = record
    return records


def git_head() -> str:
    try:
        return subprocess.run(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"], capture_output=True,
                              text=True, check=False).stdout.strip() or "?"
    except OSError:
        return "?"


# ===========================================================================
# Running
# ===========================================================================

def planned_ids(data: dict, part: str, only: list) -> list:
    ids = [m["id"] for m in data["measurements"] if part == "all" or m["part"] == part]
    if only:
        unknown = [i for i in only if i not in [m["id"] for m in data["measurements"]]]
        if unknown:
            raise Refused(f"--only names no measurement: {', '.join(unknown)}")
        ids = [i for i in ids if i in only]
        if not ids:
            raise Refused("--only selects nothing in this part")
    return ids


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def large_fixture(work: Path) -> Path:
    """Phase 10's large results fixture: the local copy if it is intact, else generated into the work dir."""
    manifest = LARGE_FIXTURE / "manifest.json"
    if manifest.is_file() and (LARGE_FIXTURE / "psms.tsv").is_file():
        meta = json.loads(manifest.read_text(encoding="utf-8"))
        if (meta["tables"]["psms"]["sha256"] == LARGE_PSMS_SHA256
                and sha256_of(LARGE_FIXTURE / "psms.tsv") == LARGE_PSMS_SHA256
                and sha256_of(LARGE_FIXTURE / "peptides.tsv") == meta["tables"]["peptides"]["sha256"]):
            return LARGE_FIXTURE
    target = work / "large-results"
    print(f"performance: generating the large results fixture into {target} (about 20 s)", flush=True)
    subprocess.run([sys.executable, "-B", str(LARGE_GENERATOR), "--out", str(target)], check=True)
    return target


def write_plan(data: dict, ids: list, mode: str, work: Path, plan: Path) -> None:
    lines = ["# written by scripts/perf/performance.py; one line per planned measurement and parameter"]
    for mid in ids:
        lines.append(f"measure\t{mid}\t{measurement(data, mid)['repeats'][mode]}")
    for name, value in sorted(data["parameters"].items()):
        lines.append(f"param\t{name}\t{value}")
    if any(measurement(data, i)["class"].endswith("PerformanceResults") for i in ids):
        directory = large_fixture(work)
        meta = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        lines.append(f"param\tresults.fixture\t{directory}")
        lines.append(f"param\tresults.peptides.sha256\t{meta['tables']['peptides']['sha256']}")
        for table in ("psms", "peptides"):
            for count in meta["tables"][table]["counts"]:
                lines.append(f"param\tresults.{table}.counts.{count['cutoff']}\t"
                             f"{count['total']},{count['passing']},{count['failing']},{count['unknown']}")
    plan.write_text("\n".join(lines) + "\n", encoding="utf-8")


def command_run(args) -> int:
    data = load_data(strict=False)
    mode = "quick" if args.quick else "full"
    only = [i for i in (args.only or "").split(",") if i]
    ids = planned_ids(data, args.part, only)
    named = args.fixture or os.environ.get(FIXTURE_ENV, "")
    refused = {}
    fixture_dir = None
    if any(measurement(data, i)["part"] == "fixture" for i in ids):
        if not named:
            for i in ids:
                if measurement(data, i)["part"] == "fixture":
                    refused[i] = (f"REFUSED: needs its one named input, {FIXTURE_ENV} (a directory of *.mzML and "
                                  "one *.fasta), and none was named. Nothing was searched; this is not a pass. "
                                  "D-006's CI fixture is OPEN; locally, scratch/fixture")
        else:
            fixture_dir = Path(named).resolve()
            if not fixture_dir.is_dir():
                raise Refused(f"{FIXTURE_ENV}={named} is not a directory")
    out = Path(args.out).resolve()
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    work = Path(args.work).resolve() if args.work else out / "work"
    if work.exists() and any(work.iterdir()):
        raise Refused(f"the work directory {work} is not empty")
    work.mkdir(parents=True, exist_ok=True)
    measured = [i for i in ids if i not in refused]
    fingerprint = host()
    report = {"generated": time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()), "gitHead": git_head(),
              "mode": mode, "part": args.part, "only": only, "host": fingerprint, "planned": ids,
              "fixture": str(fixture_dir) if fixture_dir else ("REFUSED" if refused else "not used")}
    sampler = LoadSampler()
    sampler.start()
    status = 0
    try:
        if measured:
            plan = out / "plan.tsv"
            write_plan(data, measured, mode, work, plan)
            classes = sorted({measurement(data, i)["class"] for i in measured})
            command = ["mvn", "-B", f"-Dmaven.repo.local={ROOT / '_build' / 'm2repo'}"]
            if args.offline:
                command.append("-o")
            command += ["-pl", "cometgui-app", "-am", "test", "-Dtest=" + ",".join(classes),
                        "-Dsurefire.failIfNoSpecifiedTests=false", "-Djacoco.skip=true", "-Dcometgui.perf=true",
                        f"-Dcometgui.perf.report={out}", f"-Dcometgui.perf.plan={plan}",
                        f"-Dcometgui.perf.work={work}"]
            if fixture_dir:
                command.append(f"-Dcometgui.matrix.fixture={fixture_dir}")
            print("performance: " + " ".join(command), flush=True)
            started = time.monotonic()
            log = out / "maven.log"
            with open(log, "w", encoding="utf-8") as stream:
                status = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT).returncode
            report["mavenExit"] = status
            report["mavenSeconds"] = int(time.monotonic() - started)
            print(f"performance: Maven exited {status} after {report['mavenSeconds']} s; log {log}", flush=True)
    finally:
        report["load"] = sampler.stop()
        if not args.keep_work:
            shutil.rmtree(work / "large-results", ignore_errors=True)
            for child in list(work.iterdir()) if work.is_dir() else []:
                shutil.rmtree(child, ignore_errors=True)
    records = read_records(out)
    report["grading"] = grade(data, records, ids, fingerprint, args.profile, refused)
    if status != 0 and report["grading"]["status"] == EXIT_GREEN:
        report["grading"]["problems"].append(f"Maven exited {status} with every record green: see {out / 'maven.log'}")
        report["grading"]["status"], report["grading"]["summary"] = EXIT_RED, "RED"
    return finish(report, out)


def finish(report: dict, out: Path) -> int:
    text = render(report)
    (out / "performance-report.txt").write_text(text, encoding="utf-8")
    (out / "performance-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    sys.stdout.write(text)
    print(f"performance: report {out / 'performance-report.txt'}")
    return report["grading"]["status"]


def command_grade(args) -> int:
    data = load_data(strict=False)
    report_dir = Path(args.report).resolve()
    previous = json.loads((report_dir / "performance-report.json").read_text(encoding="utf-8"))
    refused = {r["id"]: r["detail"] for r in previous["grading"]["rows"] if r["verdict"] == REFUSED}
    previous["grading"] = grade(data, read_records(report_dir), previous["planned"], previous["host"],
                                args.profile, refused)
    return finish(previous, report_dir)


# ===========================================================================
# Proposing a profile, by the documented rule
# ===========================================================================

def proposed_threshold(m: dict, s: dict) -> float:
    """The rule (docs/developer/testing.rst, Performance): twice the median, and at least one and a half
    times the worst sample, for a time; the reverse for a throughput -- then never looser than the level a
    user would see as a regression."""
    ceiling = m["ceiling"]["value"]
    if m["better"] == "lower":
        value = max(2.0 * s["median"], 1.5 * s["max"])
        return min(value, ceiling)
    value = min(0.6 * s["median"], 0.8 * s["min"])
    return max(value, ceiling)


def command_propose(args) -> int:
    data = load_data(strict=False)
    report = json.loads((Path(args.report) / "performance-report.json").read_text(encoding="utf-8"))
    if report.get("mode") != "full" or report.get("only"):
        raise Refused("a profile is proposed only from a full run of every measurement (no --quick, no --only)")
    thresholds, baseline = {}, {}
    for row in report["grading"]["rows"]:
        if "median" not in row:
            continue
        m = measurement(data, row["id"])
        s = {k: row[k] for k in ("n", "median", "min", "max", "spreadPercent")}
        baseline[row["id"]] = s
        if not m.get("informational"):
            thresholds[row["id"]] = {"value": round(proposed_threshold(m, s), 1),
                                     "rationale": "PROPOSED by the rule; review and write why",
                                     "shownRed": None}
    h = report["host"]
    profile = {args.name: {
        "description": f"PROPOSED from {report['generated']} at {report['gitHead']}; review before committing",
        "dedicated": False,
        "match": {"cpuModel": h["cpuModel"], "cpus": h["cpus"], "os": h["os"], "arch": h["arch"],
                  "memoryGiB": [round(h["memoryGiB"] * 0.98, 1), round(h["memoryGiB"] * 1.02, 1)]},
        "thresholds": thresholds,
        "baseline": {"recorded": report["generated"], "commit": report["gitHead"], "host": h,
                     "load": report.get("load"), "measurements": baseline}}}
    print(json.dumps(profile, indent=2))
    return EXIT_GREEN


# ===========================================================================
# The document and the data
# ===========================================================================

def doc_table(data: dict) -> str:
    """The baseline table docs/developer/testing.rst must carry, rendered from the data."""
    lines = []
    for name, profile in sorted(data["profiles"].items()):
        base = profile["baseline"]
        lines.append(f".. list-table:: Runner profile ``{name}`` -- baseline {base['recorded']} at "
                     f"``{base['commit']}`` (machine-readable: ``scripts/perf/performance-data.json``)")
        lines.append("   :header-rows: 1")
        lines.append("   :widths: 5 18 4 9 14 28 22")
        lines.append("")
        lines.append("   * - Spec")
        lines.append("     - Measurement")
        lines.append("     - N")
        lines.append("     - Median")
        lines.append("     - Range")
        lines.append("     - Threshold, and why")
        lines.append("     - Shown red by")
        for m in data["measurements"]:
            b = base["measurements"].get(m["id"], {})
            t = profile["thresholds"].get(m["id"])
            lines.append(f"   * - ({m['spec']})")
            lines.append(f"     - ``{m['id']}``: {m['what']}")
            lines.append(f"     - {b.get('n', '-')}")
            lines.append(f"     - {fmt(b.get('median'), m['unit'])}")
            lines.append(f"     - {fmt(b.get('min'), m['unit'])} to {fmt(b.get('max'), m['unit'])}")
            if t:
                word = "at most" if m["better"] == "lower" else "at least"
                lines.append(f"     - {word} {fmt(t['value'], m['unit'])}: {t['rationale']}")
                lines.append(f"     - {t['shownRed']['injection']} (observed {t['shownRed']['observed']})")
            else:
                lines.append(f"     - informational: {m['ceiling']['why']}")
                lines.append("     - --")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def command_check_doc(doc: Path = DOC_FILE, data: dict = None) -> int:
    data = data or load_data()
    text = Path(doc).read_text(encoding="utf-8")
    if DOC_START not in text or DOC_END not in text:
        print(f"performance: check-doc RED -- {doc} has no {DOC_START} ... {DOC_END} block", file=sys.stderr)
        return EXIT_DOC
    written = text.split(DOC_START, 1)[1].split(DOC_END, 1)[0].strip("\n") + "\n"
    expected = doc_table(data)
    if written != expected:
        import difflib
        diff = "".join(difflib.unified_diff(written.splitlines(True), expected.splitlines(True),
                                            "the document", "the data", n=1))
        print(f"performance: check-doc RED -- the baseline table in {doc} is not the data's:\n{diff}",
              file=sys.stderr)
        return EXIT_DOC
    count = sum(len(p["thresholds"]) for p in data["profiles"].values())
    print(f"performance: OK -- the document states the data's {count} threshold(s) and baseline for "
          f"{len(data['profiles'])} profile(s)")
    return EXIT_GREEN


# ===========================================================================
# Self-test: the grading and the data rules, over synthetic records (no Maven)
# ===========================================================================

def self_test() -> int:
    data = load_data()
    profile_name = sorted(data["profiles"])[0]
    profile = data["profiles"][profile_name]
    fingerprint = {**profile["baseline"]["host"]}
    ids = [m["id"] for m in data["measurements"]]
    graded = [m["id"] for m in data["measurements"] if not m.get("informational")]

    def records(**changes):
        out = {}
        for m in data["measurements"]:
            median = profile["baseline"]["measurements"][m["id"]]["median"]
            out[m["id"]] = {"id": m["id"], "samples": [median] * 3, "failures": [],
                            "checks": [{"name": "the work was real", "passed": True, "detail": "ok"}]}
        for mid, change in changes.items():
            change(out, mid)
        return out

    def over(out, mid):
        m = measurement(data, mid)
        t = profile["thresholds"][mid]["value"]
        out[mid]["samples"] = [t * (1.25 if m["better"] == "lower" else 0.75)] * 3

    results = []

    def case(name, status, needle, recs, fp=fingerprint, named="", refused=None, planned=None):
        g = grade(data, recs, planned or ids, fp, named, refused or {})
        text = render({"grading": g, "host": fp})
        ok = g["status"] == status and (needle in text)
        results.append(ok)
        print(f"  {'ok  ' if ok else 'FAIL'} {name:<62} exit {g['status']} (expected {status})")
        if not ok:
            print("       " + text.replace("\n", "\n       ")[:3000])

    print("performance: self-test")
    case("every measurement at its baseline median: green", EXIT_GREEN, "performance: GREEN", records())
    for mid in graded:
        m = measurement(data, mid)
        word = "over" if m["better"] == "lower" else "under"
        case(f"{mid} beyond its threshold: red naming it", EXIT_RED,
             f"RED {mid} -- {m['what']}: median", records(**{mid: over}))
        if not f"is {word} its threshold" in render({"grading": grade(data, records(**{mid: over}), ids,
                                                                      fingerprint, "", {}), "host": fingerprint}):
            results.append(False)
            print(f"  FAIL {mid}: the red does not say '{word} its threshold'")
    info = [m["id"] for m in data["measurements"] if m.get("informational")]
    for mid in info:
        def huge(out, i):
            out[i]["samples"] = [1e9] * 3
        case(f"{mid} is informational: any value is INFO, never red", EXIT_GREEN, "INFO", records(**{mid: huge}))

    def failed_check(out, mid):
        out[mid]["checks"].append({"name": "the counts shown", "passed": False, "detail": "expected <1> but was <2>"})
    case("a failed check of the measured work: red, named", EXIT_RED, "the counts shown",
         records(**{ids[0]: failed_check}))

    def missing(out, mid):
        del out[mid]
    case("a planned measurement with no record: red", EXIT_RED, f"RED {ids[-1]}: no record",
         records(**{ids[-1]: missing}))

    def no_checks(out, mid):
        out[mid]["checks"] = []
    case("a record with no check: red", EXIT_RED, "holds no check", records(**{ids[1]: no_checks}))
    case("an unknown profile name: NO BASELINE, exit 4, not green", EXIT_NO_BASELINE,
         "NO BASELINE FOR THIS RUNNER PROFILE", records(), named="github-ubuntu-latest")
    runner = dict(fingerprint, cpuModel="AMD EPYC 7763 64-Core Processor", cpus=4, memoryGiB=15.6)
    case("a GitHub-hosted runner's host: NO BASELINE, exit 4, not green", EXIT_NO_BASELINE,
         "no recorded profile matches this host", records(), fp=runner)
    case("... and it is still red when a check fails", EXIT_RED, "the counts shown",
         records(**{ids[0]: failed_check}), fp=runner)
    case("a matching profile named: green", EXIT_GREEN, "named and matching", records(), named=profile_name)
    case("a profile named on a host it does not match: NO BASELINE", EXIT_NO_BASELINE,
         "does not match this host", records(), fp=runner, named=profile_name)
    fixture_ids = [m["id"] for m in data["measurements"] if m["part"] == "fixture"]
    refused = {i: "REFUSED: needs COMETGUI_MATRIX_FIXTURE" for i in fixture_ids}
    recs = records()
    for i in fixture_ids:
        recs.pop(i)
    case("the fixture part refused: exit 3, never green", EXIT_REFUSED, "REFUSED", recs, refused=refused)

    def data_case(name, mutate, needle):
        broken = copy.deepcopy(data)
        mutate(broken)
        problems = validate_data(broken)
        ok = any(needle in p for p in problems)
        results.append(ok)
        print(f"  {'ok  ' if ok else 'FAIL'} data: {name:<56} {'refused' if problems else 'ACCEPTED'}")
        if not ok:
            print("       " + "; ".join(problems))

    first = graded[0]
    data_case("a threshold looser than its ceiling", lambda d: d["profiles"][profile_name]["thresholds"][first]
              .update(value=(1e12 if measurement(d, first)["better"] == "lower" else 0)), "looser than")
    data_case("a threshold never shown red", lambda d: d["profiles"][profile_name]["thresholds"][first]
              .update(shownRed=None), "never been shown red")
    data_case("a threshold with no rationale", lambda d: d["profiles"][profile_name]["thresholds"][first]
              .update(rationale="ok"), "no written rationale")
    data_case("a graded measurement with no threshold", lambda d: d["profiles"][profile_name]["thresholds"]
              .pop(first), "has no threshold")
    if info:
        data_case("a threshold on an informational measurement", lambda d: d["profiles"][profile_name]
                  ["thresholds"].update({info[0]: {"value": 1}}), "informational")
    data_case("a baseline median beyond its own threshold",
              lambda d: d["profiles"][profile_name]["baseline"]["measurements"][first].update(
                  median=(1e12 if measurement(d, first)["better"] == "lower" else -1)), "beyond its threshold")

    with tempfile.TemporaryDirectory() as scratch:
        doc = Path(scratch) / "testing.rst"
        doc.write_text("x\n" + DOC_START + "\n\n" + doc_table(data) + "\n" + DOC_END + "\n", encoding="utf-8")
        ok = command_check_doc(doc, data) == EXIT_GREEN
        broken = copy.deepcopy(data)
        broken["profiles"][profile_name]["thresholds"][first]["value"] += 1
        ok_red = command_check_doc(doc, broken) == EXIT_DOC
        results.extend([ok, ok_red])
        print(f"  {'ok  ' if ok else 'FAIL'} check-doc: the rendered table green")
        print(f"  {'ok  ' if ok_red else 'FAIL'} check-doc: a threshold changed in the data alone, red")
    if all(results):
        print(f"performance: self-test OK -- {len(results)} case(s)")
        return EXIT_GREEN
    print(f"performance: SELF-TEST FAILED -- {results.count(False)} of {len(results)} case(s)", file=sys.stderr)
    return EXIT_SELF_TEST


def main(argv) -> int:
    parser = argparse.ArgumentParser(prog="performance.py", description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--self-test", action="store_true")
    sub = parser.add_subparsers(dest="command")
    run = sub.add_parser("run")
    run.add_argument("--part", choices=("all", "no-spectra", "fixture"), default="all")
    run.add_argument("--quick", action="store_true", help="fewer repeats; the same sizes and thresholds")
    run.add_argument("--only", default="", help="comma-separated measurement identifiers")
    run.add_argument("--profile", default="", help="assert this runner profile (it must match the host)")
    run.add_argument("--fixture", default="")
    run.add_argument("--out", default=str(DEFAULT_OUT))
    run.add_argument("--work", default="", help="where generated files go (default OUT/work); emptied after")
    run.add_argument("--keep-work", action="store_true")
    run.add_argument("--offline", action="store_true")
    grade_parser = sub.add_parser("grade")
    grade_parser.add_argument("--report", required=True)
    grade_parser.add_argument("--profile", default="")
    propose = sub.add_parser("propose")
    propose.add_argument("--report", required=True)
    propose.add_argument("--name", required=True)
    sub.add_parser("check-doc")
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            return self_test()
        if args.command == "run":
            return command_run(args)
        if args.command == "grade":
            return command_grade(args)
        if args.command == "propose":
            return command_propose(args)
        if args.command == "check-doc":
            return command_check_doc()
    except Refused as refused:
        print(f"performance: REFUSED -- {refused}", file=sys.stderr)
        return EXIT_MISUSE
    parser.print_help()
    return EXIT_MISUSE


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
