#!/usr/bin/env python3
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only
"""The flakiness policy, enforced (PHASE-15 exit gate item 6).

``specification.rst``, *Flakiness policy*: "A flaky test is a defect. Do not
hide instability with unconditional retries. A temporarily quarantined flaky
test must have an issue and owner, captured diagnostics, a stated reason and a
fix plan. Release-critical scientific end-to-end tests may not remain
quarantined at release."  The policy in words is in
``docs/developer/testing.rst``, *Flakiness policy*; this is the check that
holds the tree to it.  Standard library only (``tomllib``: Python 3.11).

What is red, each with its own words (``KIND`` in brackets):

* ``[RETRY]`` an unconditional retry or an ignored failure anywhere a test run
  is configured: surefire's or failsafe's ``rerunFailingTestsCount`` (element,
  property or ``-D``), ``testFailureIgnore`` / ``maven.test.failure.ignore``,
  in any POM, ``.mvn/`` file, script or workflow;
* ``[RETRY]`` a JUnit retry mechanism in test code: ``@RetryingTest``,
  ``@RepeatedIfExceptionsTest``, ``@Flaky``, the junit-pioneer or
  rerunner-jupiter dependency, or any implementation of
  ``TestExecutionExceptionHandler`` or ``InvocationInterceptor`` (the two
  extension points a retry or a swallowed failure is built from);
* ``[HARNESS-RETRY]`` a loop in a script that runs a test runner again until
  it passes: ``until``/``while !`` on a runner, a runner followed by
  ``&& break``/``&& exit 0``/``&& return 0``/``|| continue``, an ``if
  <runner>; then ... break`` (or ``exit 0``/``return 0``, on the same line or
  after it), and -- in or out of a loop -- a runner whose ``||`` runs a runner
  again (``mvn test || mvn test``); ``scripts/build.sh`` and ``dev-verify.sh``
  are runners; and in
  Python a loop around a runner subprocess that breaks or returns on a zero
  exit status;
* ``[UNREGISTERED]`` any test-disabling construct that is neither one of the
  recognised legitimate conditions below nor covered by an entry of the
  quarantine register: ``@Disabled``, ``@DisabledIf``/``@EnabledIf``,
  ``@Disabled``/``@EnabledIf{SystemProperty,EnvironmentVariable}`` with an
  unrecognised property, ``@Tag`` with an unrecognised value, a new
  ``ExecutionCondition``, a surefire/failsafe exclusion (``excludes``,
  ``excludesFile``, ``groups``, ``excludedGroups``, ``skip``, ``skipTests``,
  ``-Dtest=!``, ``-DexcludedGroups``), an unrecognised assumption;
* ``[PLATFORM]`` a platform condition that turns a test off on Linux, the
  platform every pipeline runs on -- quarantine by another name;
* ``[OPT-IN]`` an opt-in property no harness sets and no document tells a
  person to set: a test nothing runs;
* ``[TIMING-ASSUMPTION]`` an assumption whose condition reads the clock or a
  duration: a test that skips itself when it is slow;
* ``[REGISTER]`` a quarantine entry missing any of its required fields
  (``test``, ``issue``, ``owner``, ``diagnostics``, ``reason``, ``fix_plan``,
  ``since``), holding a placeholder, dated in the future, pointing at
  diagnostics that are not in the repository, naming no test, or covering no
  disabling construct (stale);
* ``[RELEASE-CRITICAL]`` a quarantine entry naming a release-critical test
  (computed, never listed by hand -- see ``release_critical``);
* ``[AMBIGUOUS-ID]`` a failure-path map id naming a nested class's method
  whose name another class of the same test class also uses: the grader reads
  the outer class's surefire report by method name, so it could read the
  other one's result.

Usage::

    python3 scripts/ci/flakiness_check.py [--root DIR]
    python3 scripts/ci/flakiness_check.py --list-release-critical [--root DIR]
    python3 scripts/ci/flakiness_check.py --self-test

Exit status: 0 the tree keeps the policy; 1 it does not (every problem
printed); 2 misuse, or a scan that found nothing to scan (never green);
7 ``--self-test`` only: a case did not behave as required.
"""

from __future__ import annotations

import argparse
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

REGISTER = "scripts/ci/quarantine.toml"
FAILURE_PATHS = "scripts/e2e/failure-paths.toml"
TRACEABILITY = "docs/traceability-map.toml"

# --------------------------------------------------------------------------
# The recognised legitimate conditions.  Each is named, with why it is not a
# quarantine; anything not here needs a register entry.
# --------------------------------------------------------------------------

#: Opt-in system properties.  A test behind one is not quarantined because
#: something real runs it: each property names the files that set it, and the
#: check requires every one to exist and to contain ``-D<property>=``.  A
#: "document" runner is a person following that document (stated, not hidden).
OPT_INS = {
    "cometgui.e2e": {
        "runners": ["scripts/verify-e2e-gates.sh", "scripts/ci/release-e2e-canonical.sh"],
        "why": "the Tier A/B end-to-end scenarios and the failure-path GUI tests: real tools, "
               "the D-006 fixture; run by the e2e gate harness and the release pipeline",
    },
    "cometgui.matrix": {
        "runners": ["scripts/matrix/matrix.py", "scripts/matrix/determinism.py"],
        "why": "the version matrix and determinism suites (gate items 1 and 3)",
    },
    "cometgui.perf": {
        "runners": ["scripts/perf/performance.py"],
        "why": "the performance suites (gate item 4)",
    },
    "cometgui.limelight.sandbox.url": {
        "runners": ["scripts/ci/nightly-limelight-sandbox.sh"],
        "why": "the Limelight sandbox upload, nightly, only where a sandbox URL is configured",
    },
    "cometgui.install.upstream": {
        "runners": ["docs/developer/limelight_integration.rst"],
        "why": "downloads from the tools' real GitHub release URLs; run by hand as that document "
               "says. The nightly manifest verification (R-TEST-08) checks the same artefacts "
               "automatically",
    },
}

#: The one custom ExecutionCondition, and the property it reads.
EXECUTION_CONDITIONS = {
    "EndToEndExtension": "cometgui.e2e",
}

#: Tags.  A tag excludes nothing by itself (exclusions are red on their own);
#: one is recognised because something selects by it.
TAGS = {
    "e2e": "EndToEndExtension.TAG: the end-to-end scenarios, enabled by -Dcometgui.e2e=true",
}

#: Assumptions, by file and a fragment of their condition.  Each skips a test
#: for a reason that is about the machine, never about timing, and says so in
#: its message.
ASSUMPTIONS = [
    ("app/e2e/tiera/TierACanonicalScenarioTest.java", "failedStep == null",
     "a scenario step after one that failed: the failed step already fails the class"),
    ("app/e2e/tiera/TierANoXmlScenarioTest.java", "failedStep == null",
     "a scenario step after one that failed: the failed step already fails the class"),
    ("app/e2e/tierb/TierBCanonicalScenarioTest.java", "failedStep == null",
     "a scenario step after one that failed: the failed step already fails the class"),
    ("app/e2e/tierb/TierBNoXmlScenarioTest.java", "failedStep == null",
     "a scenario step after one that failed: the failed step already fails the class"),
    ("process/fakes/FakeToolSelfTest.java", "thisJvmCanNameNonAsciiFiles()",
     "a JVM whose file-name encoding cannot spell a non-ASCII name (surefire sets C.UTF-8)"),
    ("manifest/ManifestRoundTripTest.java", "sun.jnu.encoding",
     "a JVM whose file-name encoding cannot spell a non-ASCII path"),
    ("manifest/ManifestWriterTest.java", "sun.jnu.encoding",
     "a JVM whose file-name encoding cannot spell a non-ASCII path"),
    ("percolator/PercolatorCapabilityProbeTest.java", "Files.isWritable(",
     "a user who can write a read-only directory (root), so the failure cannot be made"),
]

#: The platform every pipeline runs on.
PIPELINE_OS = "LINUX"

#: Network transport retries are not test retries, and are allowed where they
#: are: manifest_verify.py retries a transfer (never a verdict) three times,
#: printed; curl --retry fetches an artefact.  Neither is matched by the
#: harness-retry rules, which look for a TEST RUNNER being run again.
TEST_RUNNER = re.compile(
    r"(?<![\w-])(mvn|mvnw|run_maven|run_mvn|maven_run|surefire|pytest|gradle|gradlew)(?![\w-])"
    r"|scripts/verify-[a-z0-9-]+\.sh|scripts/build\.sh|dev-verify\.sh|run-pipeline-locally\.sh"
    r"|run-matrix\.sh|run-performance\.sh")

#: What a loop body does to stop repeating once the run it guards succeeded:
#: leave the loop, or leave the script or function with success.
LOOP_EXIT = r"\b(break|exit\s+0|return\s+0)\b"

PLACEHOLDER = re.compile(r"\b(TODO|TBD|FIXME|XXX|placeholder|n/?a|none|unknown)\b|^\s*[-?.]*\s*$",
                         re.IGNORECASE)

REQUIRED_FIELDS = ("test", "issue", "owner", "diagnostics", "reason", "fix_plan", "since")

DISABLING = ("Disabled", "DisabledIf", "EnabledIf",
             "DisabledIfSystemProperty", "EnabledIfSystemProperty",
             "DisabledIfSystemProperties", "EnabledIfSystemProperties",
             "DisabledIfEnvironmentVariable", "EnabledIfEnvironmentVariable",
             "DisabledIfEnvironmentVariables", "EnabledIfEnvironmentVariables",
             "DisabledOnOs", "EnabledOnOs", "DisabledOnJre", "EnabledOnJre",
             "DisabledForJreRange", "EnabledForJreRange",
             "DisabledInNativeImage", "EnabledInNativeImage", "Tag", "Tags")
RETRY_ANNOTATIONS = ("RetryingTest", "RepeatedIfExceptionsTest", "Flaky", "Retry",
                     "RetryOnFailure", "Ignore")
ANNOTATION = re.compile(
    r"@(?:[a-z][\w]*\.)*(" + "|".join(sorted(DISABLING + RETRY_ANNOTATIONS, key=len, reverse=True))
    + r")\b")
TIMING = re.compile(r"Duration|currentTimeMillis|nanoTime|Instant\.now|Clock\b|elapsed|timeout|"
                    r"Timeout|toMillis|toSeconds|getSeconds|deadline", re.IGNORECASE)


class Misuse(Exception):
    """A scan that cannot be trusted: nothing found, or an input unreadable."""


@dataclass
class Problem:
    kind: str
    where: str
    message: str

    def __str__(self) -> str:
        return f"RED [{self.kind}] {self.where}: {self.message}"


@dataclass
class Census:
    test_files: int = 0
    poms: int = 0
    scripts: int = 0
    accounted: dict = field(default_factory=dict)
    release_critical: dict = field(default_factory=dict)
    register_entries: int = 0

    def count(self, what: str) -> None:
        self.accounted[what] = self.accounted.get(what, 0) + 1


# ---------------------------------------------------------------- the tree --

def tracked(root: Path) -> list[str]:
    """Every file git knows or would add (tracked and untracked, not ignored)."""
    if (root / ".git").exists():
        out = subprocess.run(["git", "-C", str(root), "ls-files", "-co", "--exclude-standard"],
                             capture_output=True, text=True, check=True).stdout
        return sorted(p for p in out.splitlines() if (root / p).is_file())
    # a self-test copy: no repository, every file is the tree
    return sorted(str(p.relative_to(root)) for p in root.rglob("*") if p.is_file())


_LEXEME = re.compile(r'//[^\n]*|/\*.*?(?:\*/|\Z)|"""(?:.|\n)*?(?:"""|\Z)|"(?:\\.|[^"\\\n])*"?'
                     r"|'(?:\\.|[^'\\\n])*'?", re.S)


def _blank(match: re.Match) -> str:
    text = match.group(0)
    if text.startswith(("//", "/*")):
        return re.sub(r"[^\n]", " ", text)
    quote = 3 if text.startswith('"""') else 1
    inner = text[quote:-quote] if len(text) >= 2 * quote and text.endswith(text[0] * quote) else text[quote:]
    tail = text[len(text[:quote]) + len(inner):]
    return text[:quote] + re.sub(r"[^\n]", " ", inner) + tail


def strip_java(source: str) -> str:
    """The source with comments and string/char literals blanked, positions kept."""
    return _LEXEME.sub(_blank, source)


def balanced(code: str, start: int) -> int:
    """Index just past the parenthesis group opening at ``start``."""
    depth = 0
    for i in range(start, len(code)):
        if code[i] == "(":
            depth += 1
        elif code[i] == ")":
            depth -= 1
            if depth == 0:
                return i + 1
    return len(code)


@dataclass
class JavaFile:
    path: str
    source: str
    code: str
    package: str
    spans: list  # (start, end, simple name)
    constants: dict

    def classes_at(self, pos: int) -> list[str]:
        return [name for start, end, name in self.spans if start <= pos < end]

    def line(self, pos: int) -> int:
        return self.source.count("\n", 0, pos) + 1


_PARSED: dict[str, tuple] = {}


def parse_java(path: str, source: str) -> JavaFile:
    cached = _PARSED.get(source)
    if cached is None:
        code = strip_java(source)
        pkg = re.search(r"^\s*package\s+([\w.]+)\s*;", code, re.M)
        closing, stack = {}, []
        for brace in re.finditer(r"[{}]", code):
            if brace.group(0) == "{":
                stack.append(brace.start())
            elif stack:
                closing[stack.pop()] = brace.end()
        spans = []
        for match in re.finditer(r"\b(?:class|interface|enum|record)\s+([A-Z]\w*)", code):
            if code[max(0, match.start() - 1)] == ".":  # Foo.class
                continue
            brace = code.find("{", match.end())
            if brace >= 0:
                spans.append((match.start(), closing.get(brace, len(code)), match.group(1)))
        constants = {m.group(1): m.group(2) for m in re.finditer(
            r"static\s+final\s+String\s+(\w+)\s*=\s*\"([^\"\n]*)\"\s*;", source)}
        cached = (code, pkg.group(1) if pkg else "", spans, constants)
        _PARSED[source] = cached
    code, package, spans, constants = cached
    return JavaFile(path, source, code, package, spans, constants)


def declaration_after(code: str, pos: int) -> tuple[str, str]:
    """('class', name) or ('method', name) of the declaration an annotation at pos annotates."""
    i, n = pos, len(code)
    while i < n:
        while i < n and code[i].isspace():
            i += 1
        if code.startswith("@interface", i):
            name = re.match(r"@interface\s+(\w+)", code[i:])
            return "class", name.group(1) if name else "?"
        if i < n and code[i] == "@":
            m = re.match(r"@[\w.]+\s*", code[i:])
            i += m.end() if m else 1
            if i < n and code[i] == "(":
                i = balanced(code, i)
            continue
        break
    names = []
    while i < n:
        m = re.match(r"\s*([A-Za-z_][\w]*|<[^;{()]*?>|\[\]|[.,?])", code[i:])
        if not m:
            break
        token = m.group(1)
        i += m.end()
        if code.startswith("@interface", i - len(token)) or re.match(r"\s*@interface\b", code[i:]):
            name = re.match(r"\s*@interface\s+(\w+)", code[i:])
            if name:
                return "class", name.group(1)
        if token in ("class", "interface", "enum", "record"):
            name = re.match(r"\s*(\w+)", code[i:])
            return "class", name.group(1) if name else "?"
        if re.match(r"[A-Za-z_]", token):
            names.append(token)
    rest = code[i:i + 1]
    if rest == "(" and names:
        return "method", names[-1]
    return "field", names[-1] if names else "?"


# ----------------------------------------------------- the release-critical --

def release_critical(root: Path, files: list[str], java: dict[str, JavaFile]) -> dict[str, set]:
    """Every release-critical test CLASS (binary name of the outer class), by source.

    Computed from the tree, never listed by hand:

    * ``failure-path map`` -- every class a ``[[scenario.test]]`` id names
      (AC-TST-09);
    * ``end-to-end`` -- every class under ``app/e2e/tiera`` or ``tierb``, and
      every class meta-annotated ``@EndToEnd`` (AC-TST-06..08);
    * ``real binary`` -- every test class whose name says it runs a real tool
      (``Real...``, ``...RealBinary...``, ``...RealPdv...``, ``...RealTest``);
    * ``gate harness`` -- every test class a ``scripts/verify-*-gates.sh``
      harness, the e2e, matrix or performance scripts name;
    * ``traceability`` -- every class ``docs/traceability-map.toml`` gives as
      a requirement's or criterion's test evidence (every AC- must pass its
      named test for the project to be finished).
    """
    by_simple: dict[str, set] = {}
    for jf in java.values():
        if "/src/test/java/" not in jf.path:
            continue
        for start, end, name in jf.spans:
            if not jf.classes_at(start)[:-1]:  # top-level only
                by_simple.setdefault(name, set()).add(f"{jf.package}.{name}")
    found: dict[str, set] = {k: set() for k in
                             ("failure-path map", "end-to-end", "real binary", "gate harness",
                              "traceability")}
    fp = root / FAILURE_PATHS
    if fp.is_file():
        with fp.open("rb") as handle:
            doc = tomllib.load(handle)
        for scenario in doc.get("scenario", []):
            for test in scenario.get("test", []):
                found["failure-path map"].add(test["id"].split("#")[0].split("$")[0])
    for jf in java.values():
        if "/src/test/java/" not in jf.path:
            continue
        outer = os.path.basename(jf.path)[:-5]
        binary = f"{jf.package}.{outer}"
        if "/app/e2e/tiera/" in jf.path or "/app/e2e/tierb/" in jf.path:
            if outer.endswith("Test"):
                found["end-to-end"].add(binary)
        if re.search(r"@EndToEnd\b", jf.code):
            found["end-to-end"].add(binary)
        if re.search(r"^Real[A-Z]|RealBinary|RealPdv|Real(Test|UiTest)$", outer) and \
                re.search(r"@(Test|ParameterizedTest)\b", jf.code):
            found["real binary"].add(binary)
    harness = [f for f in files if re.match(r"scripts/(verify-[a-z0-9-]+-gates\.sh|e2e/|matrix/|perf/)", f)]
    for path in harness:
        text = (root / path).read_text(encoding="utf-8", errors="replace")
        for token in set(re.findall(r"\b([A-Z][A-Za-z0-9]*)\b", text)):
            for binary in by_simple.get(token, ()):
                found["gate harness"].add(binary)
    tm = root / TRACEABILITY
    if tm.is_file():
        with tm.open("rb") as handle:
            doc = tomllib.load(handle)

        def walk(node):
            if isinstance(node, dict):
                if node.get("kind") == "test" and isinstance(node.get("class"), str):
                    found["traceability"].add(node["class"].split("#")[0].split("$")[0])
                for value in node.values():
                    walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)
        walk(doc)
    return found


# ------------------------------------------------------------- the check --

def check(root: Path) -> tuple[list[Problem], Census]:
    root = root.resolve()
    files = tracked(root)
    census = Census()
    problems: list[Problem] = []
    java_paths = [f for f in files if f.endswith(".java") and "/src/" in f]
    test_paths = [f for f in java_paths if "/src/test/java/" in f]
    poms = [f for f in files if f == "pom.xml" or f.endswith("/pom.xml")]
    scripts = [f for f in files if f.startswith("scripts/") and f.endswith((".sh", ".py"))]
    workflows = [f for f in files if f.startswith(".github/workflows/") and f.endswith((".yml", ".yaml"))]
    census.test_files, census.poms, census.scripts = len(test_paths), len(poms), len(scripts)
    if not test_paths or not poms or not scripts:
        raise Misuse(f"found {len(test_paths)} test source file(s), {len(poms)} POM(s) and "
                     f"{len(scripts)} script(s) under {root}: a scan of nothing is not a pass")
    java = {p: parse_java(p, (root / p).read_text(encoding="utf-8", errors="replace"))
            for p in test_paths}
    constants_by_class = {}
    for jf in java.values():
        constants_by_class.setdefault(os.path.basename(jf.path)[:-5], {}).update(jf.constants)

    # ---- retries and ignored failures in configuration ----------------------
    retry_tokens = re.compile(r"rerunFailingTestsCount|testFailureIgnore|maven\.test\.failure\.ignore"
                              r"|junit-pioneer|rerunner-jupiter|io\.github\.artsok")
    config = poms + [f for f in files if f.startswith(".mvn/")] + scripts + workflows
    for path in config:
        if path == "scripts/ci/flakiness_check.py":
            continue
        text = (root / path).read_text(encoding="utf-8", errors="replace")
        grader = grades_every_testcase(path, text)
        for number, line in enumerate(text.splitlines(), 1):
            if path.endswith(".xml"):
                line = re.sub(r"<!--.*?-->", "", line)
            elif line.lstrip().startswith("#"):
                continue
            # Every token on the line is graded, not only the first: a permitted
            # maven.test.failure.ignore in a grading harness must not carry a retry
            # past the check on the same line (found at the orchestrator's sign-off).
            for hit in retry_tokens.finditer(line):
                if hit.group(0) == "maven.test.failure.ignore" and grader:
                    census.count("maven.test.failure.ignore in a harness that grades each testcase")
                    continue
                problems.append(Problem("RETRY", f"{path}:{number}",
                                        f"'{hit.group(0)}' -- an unconditional retry or an ignored "
                                        "failure hides instability; a flaky test is a defect to fix"))

    # ---- exclusions in the POMs ------------------------------------------------
    register, register_problems = read_register(root)
    problems += register_problems
    census.register_entries = len(register)
    used: set[int] = set()
    for path in poms:
        try:
            tree = ET.parse(root / path)
        except ET.ParseError as error:
            raise Misuse(f"{path} cannot be parsed: {error}")
        for element in tree.iter():
            tag = element.tag.split("}")[-1]
            if tag == "properties":
                for prop in element:
                    name = prop.tag.split("}")[-1]
                    if name in ("skipTests", "maven.test.skip", "surefire.excludesFile", "groups",
                                "excludedGroups", "surefire.excludedGroups", "surefire.groups",
                                "failsafe.excludedGroups", "test"):
                        problems.append(Problem("UNREGISTERED", path,
                                                f"the property <{name}> selects or skips tests for every "
                                                "build -- a quarantine with no register entry"))
            if tag != "plugin":
                continue
            artifact = next((c.text for c in element if c.tag.split("}")[-1] == "artifactId"), "")
            if artifact not in ("maven-surefire-plugin", "maven-failsafe-plugin"):
                continue
            for conf in element.iter():
                ctag = conf.tag.split("}")[-1]
                if ctag in ("excludes", "excludesFile", "groups", "excludedGroups", "skip", "skipTests",
                            "skipExec", "test"):
                    names = sorted({m for c in conf.iter() for m in
                                    re.findall(r"([A-Z]\w*)(?:\.java|\.class)?\s*$", (c.text or "").strip())})
                    covered = bool(names)
                    for n in names:
                        hits = {i for i, e in enumerate(register) if isinstance(e.get("test"), str)
                                and e["test"].split("#")[0].split("$")[0].endswith("." + n)}
                        covered = covered and bool(hits)
                        if covered:
                            used.update(hits)
                    if covered:
                        census.count("POM exclusion, registered")
                        continue
                    problems.append(Problem("UNREGISTERED", path,
                                            f"{artifact} <{ctag}> {' '.join(names) or (conf.text or '').strip()!r}"
                                            " excludes or skips tests with no quarantine register entry"))

    # ---- exclusions and harness retries in scripts -----------------------------
    for path in scripts + workflows:
        if path == "scripts/ci/flakiness_check.py":
            continue
        text = (root / path).read_text(encoding="utf-8", errors="replace")
        lines = text.splitlines()
        for number, line in enumerate(lines, 1):
            body = line.split("#", 1)[0] if path.endswith((".sh", ".yml", ".yaml")) else line
            if path.endswith(".py") and body.lstrip().startswith("#"):
                continue
            if re.search(r"-Dtest=['\"]?[^ '\"]*!|-D(surefire\.|failsafe\.)?excludedGroups=|"
                         r"-D(surefire\.|failsafe\.)?groups=|-Dsurefire\.excludesFile=|--exclude-tag", body):
                problems.append(Problem("UNREGISTERED", f"{path}:{number}",
                                        "a test selection that excludes tests or groups -- quarantine "
                                        "with no register entry"))
        problems += harness_retries(path, lines)

    # ---- the Java test sources --------------------------------------------------
    for path, jf in java.items():
        for impl in re.finditer(r"\bimplements\s+([^{]+)\{", jf.code):
            ifaces = impl.group(1)
            if re.search(r"\b(TestExecutionExceptionHandler|InvocationInterceptor|"
                         r"LifecycleMethodExecutionExceptionHandler)\b", ifaces):
                problems.append(Problem("RETRY", f"{path}:{jf.line(impl.start())}",
                                        "an extension that intercepts a test's execution or its "
                                        "exception -- how a retry or a swallowed failure is built"))
            if re.search(r"\bExecutionCondition\b", ifaces):
                owner = (jf.classes_at(impl.start()) or ["?"])[-1]
                if owner in EXECUTION_CONDITIONS:
                    census.count(f"ExecutionCondition {owner} ({EXECUTION_CONDITIONS[owner]})")
                else:
                    problems.append(Problem("UNREGISTERED", f"{path}:{jf.line(impl.start())}",
                                            f"{owner} is a new ExecutionCondition: a way of disabling "
                                            "tests the policy does not recognise"))
        for match in ANNOTATION.finditer(jf.code):
            name = match.group(1)
            after = match.end()
            text = ""
            j = after
            while j < len(jf.code) and jf.code[j] in " \t\n":
                j += 1
            if j < len(jf.code) and jf.code[j] == "(":
                end = balanced(jf.code, j)
                text = jf.source[j:end]
                after = end
            kind, target = declaration_after(jf.code, after)
            chain = jf.classes_at(match.start())
            if kind == "class":
                chain = chain + [target] if target not in chain[-1:] else chain
            outer_binary = f"{jf.package}.{chain[0]}" if chain else f"{jf.package}.?"
            binary = f"{jf.package}." + "$".join(chain) if chain else outer_binary
            test_id = binary + (f"#{target}" if kind == "method" else "")
            where = f"{path}:{jf.line(match.start())}"
            if kind == "field":
                continue  # an annotation on a field or a parameter is not a test condition
            if name in RETRY_ANNOTATIONS:
                problems.append(Problem("RETRY" if name != "Ignore" else "UNREGISTERED", where,
                                        f"@{name} on {test_id}: " + (
                                            "a retry hides instability" if name != "Ignore" else
                                            "a JUnit 4 ignore, which this JUnit 5 build would not "
                                            "even run")))
                continue
            verdict = classify(name, text, jf, constants_by_class, root, test_id)
            if verdict is None:
                census.count(f"@{name}")
                continue
            kind_, message = verdict
            if covered_by(register, test_id, used):
                census.count(f"@{name}, registered")
                continue
            problems.append(Problem(kind_, where, f"@{name}{compact(text)} on {test_id}: {message}"))
        for match in re.finditer(r"\b(assumeTrue|assumeFalse|assumingThat|Assumptions\s*\.\s*abort)\s*\(",
                                 jf.code):
            start = match.end() - 1
            end = balanced(jf.code, start)
            condition = jf.source[start:end]
            where = f"{path}:{jf.line(match.start())}"
            chain = jf.classes_at(match.start())
            test_id = f"{jf.package}." + "$".join(chain)
            if TIMING.search(strip_java(condition)):
                problems.append(Problem("TIMING-ASSUMPTION", where,
                                        f"{match.group(1)}{compact(condition)}: a test that skips itself "
                                        "on timing hides exactly the instability the policy forbids hiding"))
                continue
            known = [a for a in ASSUMPTIONS if path.endswith(a[0]) and a[1] in condition]
            if known:
                census.count("assumption, recognised")
            elif covered_by(register, test_id, used):
                census.count("assumption, registered")
            else:
                problems.append(Problem("UNREGISTERED", where,
                                        f"{match.group(1)}{compact(condition)}: an assumption the policy "
                                        "does not recognise skips a test silently"))

    # ---- opt-in properties are run by something -----------------------------------
    for prop, spec in OPT_INS.items():
        for runner in spec["runners"]:
            path = root / runner
            if not path.is_file():
                problems.append(Problem("OPT-IN", runner, f"names a runner of -D{prop}=... that does not "
                                        "exist: the tests behind that property would run nowhere"))
            elif f"-D{prop}=" not in path.read_text(encoding="utf-8", errors="replace"):
                problems.append(Problem("OPT-IN", runner, f"no longer sets -D{prop}=...: the tests behind "
                                        "that property would run nowhere"))

    # ---- the register -----------------------------------------------------------
    critical = release_critical(root, files, java)
    census.release_critical = {k: len(v) for k, v in critical.items()}
    all_critical = set().union(*critical.values())
    if not all_critical:
        raise Misuse("no release-critical test was found (no failure-path map, harness or "
                     "traceability map?): the check would pass any quarantine")
    known_classes = {f"{jf.package}.{os.path.basename(p)[:-5]}" for p, jf in java.items()}
    for index, entry in enumerate(register):
        test = entry.get("test")
        if not isinstance(test, str):
            continue
        outer = test.split("#")[0].split("$")[0]
        sources = sorted(k for k, v in critical.items() if outer in v)
        if sources:
            problems.append(Problem("RELEASE-CRITICAL", REGISTER,
                                    f"{test} is release-critical ({', '.join(sources)}) and may not be "
                                    "quarantined: fix it at its cause"))
        if outer not in known_classes:
            problems.append(Problem("REGISTER", REGISTER, f"{test} names no test class in the tree (stale)"))
        elif index not in used:
            problems.append(Problem("REGISTER", REGISTER,
                                    f"{test} is registered but nothing it covers needs it (stale): an entry "
                                    "that quarantines nothing is a place for the next one to hide"))

    # ---- the failure-path map's nested ids are unambiguous -----------------------
    problems += ambiguous_ids(root, java)
    return problems, census


def grades_every_testcase(path: str, text: str) -> bool:
    """A falsifiability harness: its dirty runs are MEANT to fail, and it reads each named test's
    outcome from the surefire XML (``assert_testcase``) after proving every named test ran
    (``verify_classes_ran``).  There, ``-Dmaven.test.failure.ignore=true`` lets Maven finish so the
    failures can be graded one by one; nothing is ignored.  Recognised there and nowhere else."""
    return bool(re.fullmatch(r"scripts/verify-[a-z0-9-]+-gates\.sh", path)) and \
        re.search(r"^assert_testcase\(\)\s*\{", text, re.M) is not None and \
        re.search(r"^verify_classes_ran\(\)\s*\{", text, re.M) is not None


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text)[:160]


def covered_by(register: list[dict], test_id: str, used: set | None = None) -> bool:
    """Whether an entry covers the test id; the entries that do are added to ``used``."""
    hit = False
    for index, entry in enumerate(register):
        test = entry.get("test")
        if not isinstance(test, str):
            continue
        if test_id == test or test_id.startswith(test + "$") or test_id.startswith(test + "#"):
            hit = True
            if used is not None:
                used.add(index)
    return hit


def resolve(value: str, jf: JavaFile, constants_by_class: dict) -> str | None:
    value = value.strip()
    literal = re.fullmatch(r"\"([^\"]*)\"", value)
    if literal:
        return literal.group(1)
    qualified = re.fullmatch(r"(?:[\w.]*\.)?([A-Z]\w*)\.([A-Z_][A-Z0-9_]*)", value)
    if qualified:
        return constants_by_class.get(qualified.group(1), {}).get(qualified.group(2))
    if re.fullmatch(r"[A-Z_][A-Z0-9_]*", value):
        return jf.constants.get(value)
    return None


def classify(name: str, text: str, jf: JavaFile, constants_by_class: dict, root: Path,
             test_id: str) -> tuple[str, str] | None:
    """None if the annotation is a recognised legitimate condition, else (kind, why)."""
    if name in ("EnabledOnOs", "DisabledOnOs"):
        systems = set(re.findall(r"\bOS\.([A-Z_]+)\b", text)) | set(re.findall(r"\b(LINUX|MAC|WINDOWS|"
                                                                               r"AIX|FREEBSD|OPENBSD|"
                                                                               r"SOLARIS|OTHER)\b", text))
        if not systems:
            return "UNREGISTERED", "a platform condition naming no operating system the check can read"
        if name == "EnabledOnOs" and PIPELINE_OS not in systems:
            return "PLATFORM", f"enabled only on {sorted(systems)}, so off on {PIPELINE_OS}, the platform " \
                               "every pipeline runs -- quarantine by another name"
        if name == "DisabledOnOs" and PIPELINE_OS in systems:
            return "PLATFORM", f"disabled on {PIPELINE_OS}, the platform every pipeline runs -- " \
                               "quarantine by another name"
        return None
    if name == "EnabledIfSystemProperty":
        named = re.search(r"named\s*=\s*([^,)]+)", text)
        prop = resolve(named.group(1), jf, constants_by_class) if named else None
        matches = re.search(r"matches\s*=\s*\"([^\"]*)\"", text)
        if prop in OPT_INS and matches and matches.group(1) in ("true", ".+"):
            return None
        return "UNREGISTERED", f"an opt-in property {prop!r} the policy does not recognise (recognised: " \
                               f"{', '.join(sorted(OPT_INS))}); a test nothing runs is a quarantine"
    if name in ("Tag", "Tags"):
        values = [resolve(v, jf, constants_by_class) for v in re.findall(r"(\"[^\"]*\"|[\w.]+)", text)
                  if v != "Tag"]
        values = [v for v in values if v is not None] or [None]
        if all(v in TAGS for v in values):
            return None
        return "UNREGISTERED", f"a tag {values} the policy does not recognise (recognised: {sorted(TAGS)})"
    return "UNREGISTERED", "disables a test with no quarantine register entry -- issue, owner, " \
                           "diagnostics, reason and fix plan, or fix it"


def harness_retries(path: str, lines: list[str]) -> list[Problem]:
    problems = []
    if path.endswith((".sh", ".yml", ".yaml")):
        depth = 0
        for number, raw in enumerate(lines, 1):
            line = raw.split("#", 1)[0] if not raw.lstrip().startswith("#") else ""
            if not line.strip():
                continue
            if re.match(r"\s*(until|while\s+!)\b", line) and TEST_RUNNER.search(line):
                problems.append(Problem("HARNESS-RETRY", f"{path}:{number}",
                                        "a loop that runs a test runner until it passes"))
            if re.search(r"\b(for|while|until)\b.*\bdo\b|^\s*(for|while|until)\b", line):
                depth += 1
            if re.search(r"\bdone\b", line):
                depth = max(0, depth - 1)
            # A runner retried on its own failure, in or out of a loop:
            # "mvn test || mvn test".  The command after "||" must itself BE a
            # runner, not a message that names one ("|| die 'no mvn'").
            ran, _, otherwise = line.partition("||")
            if otherwise and TEST_RUNNER.search(ran) and \
                    re.match(r"\s*(\w+=\S*\s+)*(bash\s+|sh\s+|\./)?(" + TEST_RUNNER.pattern + r")",
                             otherwise):
                problems.append(Problem("HARNESS-RETRY", f"{path}:{number}",
                                        "a test runner run again when it fails"))
            if depth and TEST_RUNNER.search(line):
                if re.search(r"&&\s*" + LOOP_EXIT + r"|\|\|\s*continue\b", line):
                    problems.append(Problem("HARNESS-RETRY", f"{path}:{number}",
                                            "a test runner whose success ends the loop that repeats it"))
                elif re.search(r"\bif\b.*;\s*then\b", line):
                    # The body starts after "then" on this line ("if mvn test;
                    # then break; fi") and runs on until fi, else or elif.
                    rest = re.split(r";\s*then\b", line, maxsplit=1)[1]
                    for follow in [rest] + lines[number:number + 6]:
                        body = re.split(r"\b(fi|else|elif)\b", follow, maxsplit=1)[0]
                        if re.search(LOOP_EXIT, body):
                            problems.append(Problem("HARNESS-RETRY", f"{path}:{number}",
                                                    "a test runner whose success breaks the loop "
                                                    "that repeats it"))
                            break
                        if body != follow:
                            break
    elif path.endswith(".py"):
        for number, raw in enumerate(lines, 1):
            header = re.match(r"(\s*)(for|while)\b.*:\s*$", raw)
            if not header:
                continue
            indent = len(header.group(1))
            body = []
            for follow in lines[number:]:
                if follow.strip() and (len(follow) - len(follow.lstrip())) <= indent:
                    break
                body.append(follow)
            block = "\n".join(body)
            if re.search(r"subprocess\.(run|call|check_call|Popen)", block) and \
                    re.search(r"[\"']mvn[\"']|" + TEST_RUNNER.pattern, block) and \
                    re.search(r"\b(break|return)\b", block) and re.search(r"returncode\s*==\s*0|"
                                                                          r"returncode\s*!=\s*0|"
                                                                          r"not\s+\w+\.returncode", block):
                problems.append(Problem("HARNESS-RETRY", f"{path}:{number}",
                                        "a loop that runs a test runner again until its exit status is 0"))
    return problems


def read_register(root: Path) -> tuple[list[dict], list[Problem]]:
    path = root / REGISTER
    if not path.is_file():
        raise Misuse(f"no quarantine register at {REGISTER}: an absent register is not an empty one")
    try:
        with path.open("rb") as handle:
            doc = tomllib.load(handle)
    except tomllib.TOMLDecodeError as error:
        raise Misuse(f"{REGISTER} cannot be parsed: {error}")
    entries = doc.get("quarantine", [])
    problems = []
    if set(doc) - {"quarantine", "schema"}:
        problems.append(Problem("REGISTER", REGISTER, f"unknown top-level key(s) {sorted(set(doc) - {'quarantine', 'schema'})}"))
    if not isinstance(entries, list):
        return [], [Problem("REGISTER", REGISTER, "'quarantine' must be an array of tables")]
    today = datetime.date.today()
    for index, entry in enumerate(entries, 1):
        label = f"{REGISTER} entry {index} ({entry.get('test', '?')})"
        for name in REQUIRED_FIELDS:
            if name not in entry:
                problems.append(Problem("REGISTER", label, f"has no '{name}': a quarantine needs an issue, an "
                                        "owner, captured diagnostics, a stated reason, a fix plan and a date"))
        for name in sorted(set(entry) - set(REQUIRED_FIELDS)):
            problems.append(Problem("REGISTER", label, f"has an unknown field '{name}'"))
        for name in ("test", "issue", "owner", "diagnostics", "reason", "fix_plan"):
            value = entry.get(name)
            if name in entry and (not isinstance(value, str) or PLACEHOLDER.search(value)
                                  or len(value.strip()) < (20 if name in ("reason", "fix_plan") else 3)):
                problems.append(Problem("REGISTER", label, f"'{name}' is empty, too short or a placeholder"))
        since = entry.get("since")
        if "since" in entry and (not isinstance(since, datetime.date) or isinstance(since, datetime.datetime)):
            problems.append(Problem("REGISTER", label, "'since' must be a date (YYYY-MM-DD)"))
        elif isinstance(since, datetime.date) and since > today:
            problems.append(Problem("REGISTER", label, f"'since' {since} is in the future"))
        issue = entry.get("issue")
        if isinstance(issue, str) and not (re.fullmatch(r"https://\S+/\d+", issue)
                                           or (root / issue.split("#")[0]).is_file()):
            problems.append(Problem("REGISTER", label, f"'issue' {issue!r} is neither an issue URL nor a "
                                    "file in the repository"))
        diagnostics = entry.get("diagnostics")
        if isinstance(diagnostics, str):
            target = root / diagnostics
            if os.path.isabs(diagnostics) or ".." in Path(diagnostics).parts or \
                    diagnostics.startswith(("scratch/", "_build/", "/tmp")) or \
                    not target.is_file() or target.stat().st_size == 0:
                problems.append(Problem("REGISTER", label, f"'diagnostics' {diagnostics!r} is not a non-empty "
                                        "file committed in the repository: captured diagnostics are kept"))
        test = entry.get("test")
        if isinstance(test, str) and not re.fullmatch(r"[a-z][\w.]*\.[A-Z]\w*(\$[A-Z]\w*)*(#\w+)?", test):
            problems.append(Problem("REGISTER", label, f"'test' {test!r} is not package.Class[$Nested][#method]"))
    return [e for e in entries if isinstance(e, dict)], problems


def ambiguous_ids(root: Path, java: dict[str, JavaFile]) -> list[Problem]:
    path = root / FAILURE_PATHS
    if not path.is_file():
        return []
    with path.open("rb") as handle:
        doc = tomllib.load(handle)
    by_binary = {}
    for p, jf in java.items():
        by_binary[f"{jf.package}.{os.path.basename(p)[:-5]}"] = jf
    problems = []
    for scenario in doc.get("scenario", []):
        for test in scenario.get("test", []):
            ident = test.get("id", "")
            if "$" not in ident or "#" not in ident:
                continue
            outer, method = ident.split("$")[0], ident.split("#")[1]
            jf = by_binary.get(outer)
            if jf is None:
                continue
            owners = set()
            for m in re.finditer(r"\bvoid\s+" + re.escape(method) + r"\s*\(", jf.code):
                owners.add("$".join(jf.classes_at(m.start())))
            if len(owners) > 1:
                problems.append(Problem("AMBIGUOUS-ID", FAILURE_PATHS,
                                        f"{ident}: {method} is declared in {sorted(owners)}; the grader "
                                        "reads the outer class's surefire report by method name and could "
                                        "read another class's result"))
    return problems


# --------------------------------------------------------------- reporting --

def report(root: Path) -> int:
    try:
        problems, census = check(root)
    except Misuse as error:
        print(f"flakiness-check: REFUSED -- {error}")
        return 2
    print(f"flakiness-check: scanned {census.test_files} test source file(s), {census.poms} POM(s), "
          f"{census.scripts} script(s), and every workflow")
    rc = census.release_critical
    print(f"flakiness-check: release-critical classes -- " +
          ", ".join(f"{k} {v}" for k, v in rc.items()))
    print("flakiness-check: test conditions accounted for -- " +
          (", ".join(f"{k} x{v}" for k, v in sorted(census.accounted.items())) or "none"))
    print(f"flakiness-check: quarantine register {REGISTER}: {census.register_entries} entr"
          f"{'y' if census.register_entries == 1 else 'ies'}")
    for problem in problems:
        print(problem)
    if problems:
        kinds = sorted({p.kind for p in problems})
        print(f"flakiness-check: FAILED -- {len(problems)} problem(s): {', '.join(kinds)}. "
              "See docs/developer/testing.rst, 'Flakiness policy'.")
        return 1
    print("flakiness-check: OK -- no retry, no unregistered quarantine, no release-critical test "
          "quarantined")
    return 0


def list_release_critical(root: Path) -> int:
    files = tracked(root.resolve())
    java = {p: parse_java(p, (root / p).read_text(encoding="utf-8", errors="replace"))
            for p in files if p.endswith(".java") and "/src/test/java/" in p}
    found = release_critical(root.resolve(), files, java)
    every = {}
    for source, classes in found.items():
        for c in classes:
            every.setdefault(c, []).append(source)
    for c in sorted(every):
        print(f"{c}  ({', '.join(every[c])})")
    print(f"{len(every)} release-critical test class(es)")
    return 0


# --------------------------------------------------------------- self-test --

#: A test the policy may quarantine: ordinary, named by no harness, no map, no
#: traceability entry, no real tool.  The self-test fails loudly if that ever
#: stops being true of it, rather than quietly testing nothing.
ORDINARY = "cometgui-domain/src/test/java/org/cometgui/domain/project/ProjectIdTest.java"
ORDINARY_ID = "org.cometgui.domain.project.ProjectIdTest"
FAILURE_PATH_TEST = "cometgui-app/src/test/java/org/cometgui/app/gui/ToolManagerInstallUiTest.java"
FAILURE_PATH_ID = "org.cometgui.app.gui.ToolManagerInstallUiTest"
TIER_A = "cometgui-app/src/test/java/org/cometgui/app/e2e/tiera/TierACanonicalScenarioTest.java"
TIER_A_ID = "org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest"
GRADED = "cometgui-app/src/test/java/org/cometgui/app/gui/ConsoleFloodUiTest.java"
GRADED_ID = "org.cometgui.app.gui.ConsoleFloodUiTest"
DIAGNOSTICS = "scripts/ci/quarantine/ProjectIdTest-example.log"


def _edit(root: Path, rel: str, old: str, new: str, count: int = 1) -> None:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise AssertionError(f"injection anchor not found in {rel}: {old[:60]!r}")
    path.write_text(text.replace(old, new, count), encoding="utf-8")


def _annotate_first_test(root: Path, rel: str, annotation: str) -> None:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    match = re.search(r"\n(\s*)@Test\b", text)
    if not match:
        raise AssertionError(f"no @Test in {rel}")
    path.write_text(text[:match.start()] + f"\n{match.group(1)}{annotation}" + text[match.start():],
                    encoding="utf-8")


def _annotate_class(root: Path, rel: str, annotation: str) -> None:
    path = root / rel
    text = path.read_text(encoding="utf-8")
    match = re.search(r"\n((?:public\s+|final\s+)*class\s+)", text)
    if not match:
        raise AssertionError(f"no class declaration in {rel}")
    path.write_text(text[:match.start()] + f"\n{annotation}" + text[match.start():], encoding="utf-8")


def _register(root: Path, test: str, **override) -> None:
    fields = {
        "test": test,
        "issue": "https://github.com/mriffle/CometGUI/issues/1",
        "owner": "the Phase 15 orchestrator",
        "diagnostics": DIAGNOSTICS,
        "reason": "fails one run in twenty with a timeout in the reader thread",
        "fix_plan": "make the test wait on the reader's completion instead of its start",
        "since": "2026-11-01",
    }
    fields.update(override)
    lines = ["", "[[quarantine]]"]
    for key, value in fields.items():
        if value is None:
            continue
        lines.append(f"{key} = {value}" if key == "since" else f"{key} = \"{value}\"")
    diag = root / DIAGNOSTICS
    diag.parent.mkdir(parents=True, exist_ok=True)
    diag.write_text("[ERROR] Tests run: 3, Failures: 1 -- the captured run\n", encoding="utf-8")
    with (root / REGISTER).open("a", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def _pom_rerun(root: Path) -> None:
    _edit(root, "pom.xml", "<argLine>@{cometgui.jacocoArgLine}",
          "<rerunFailingTestsCount>2</rerunFailingTestsCount>\n            <argLine>@{cometgui.jacocoArgLine}")


def cases():
    """(name, injection, expected kinds -> substrings). An empty expectation means green."""
    return [
        ("the clean tree", lambda r: None, {}),
        ("rerunFailingTestsCount in the root POM's surefire configuration", _pom_rerun,
         {"RETRY": "'rerunFailingTestsCount'"}),
        ("surefire.rerunFailingTestsCount as a POM property",
         lambda r: _edit(r, "pom.xml", "<properties>", "<properties>\n    <surefire.rerunFailingTestsCount>3"
                         "</surefire.rerunFailingTestsCount>"),
         {"RETRY": "'rerunFailingTestsCount'"}),
        ("-Dsurefire.rerunFailingTestsCount in .mvn/maven.config",
         lambda r: (r / ".mvn/maven.config").open("a").write("-Dsurefire.rerunFailingTestsCount=2\n"),
         {"RETRY": ".mvn/maven.config"}),
        ("a rerun on the same line as a grading harness's permitted maven.test.failure.ignore",
         lambda r: _edit(r, "scripts/verify-pdv-gates.sh", "-Dmaven.test.failure.ignore=true \\",
                         "-Dmaven.test.failure.ignore=true -Dsurefire.rerunFailingTestsCount=2 \\"),
         {"RETRY": "'rerunFailingTestsCount'"}),
        ("testFailureIgnore in a module POM",
         lambda r: _edit(r, "cometgui-ui/pom.xml", "<reuseForks>false</reuseForks>",
                         "<reuseForks>false</reuseForks>\n          <testFailureIgnore>true</testFailureIgnore>"),
         {"RETRY": "'testFailureIgnore'"}),
        ("a JUnit retry annotation (@RetryingTest)",
         lambda r: _annotate_first_test(r, ORDINARY, "@org.junitpioneer.jupiter.RetryingTest(3)"),
         {"RETRY": "@RetryingTest on " + ORDINARY_ID}),
        ("an extension that intercepts a test's exception",
         lambda r: (r / "cometgui-domain/src/test/java/org/cometgui/domain/project/Swallow.java").write_text(
             "package org.cometgui.domain.project;\nimport org.junit.jupiter.api.extension.*;\n"
             "final class Swallow implements TestExecutionExceptionHandler {\n"
             "  public void handleTestExecutionException(ExtensionContext c, Throwable t) {}\n}\n"),
         {"RETRY": "Swallow.java"}),
        ("a harness that re-runs Maven until it passes (until)",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -", "\nuntil mvn -o -B test; do :; done\nset -"),
         {"HARNESS-RETRY": "until it passes"}),
        ("a harness that breaks its loop when the test run succeeds",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -",
                         "\nfor attempt in 1 2 3; do\n    mvn -o -B test && break\ndone\nset -"),
         {"HARNESS-RETRY": "ends the loop"}),
        ("a harness that runs Maven again when it fails (||)",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -", "\nmvn -o -B test || mvn -o -B test\nset -"),
         {"HARNESS-RETRY": "run again when it fails"}),
        ("a harness that re-runs the build when it fails (scripts/build.sh ||)",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -",
                         "\nbash scripts/build.sh || bash scripts/build.sh\nset -"),
         {"HARNESS-RETRY": "run again when it fails"}),
        ("a harness whose one-line if breaks its loop when the test run succeeds",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -",
                         "\nfor attempt in 1 2 3; do\n    if mvn -o -B test; then break; fi\ndone\nset -"),
         {"HARNESS-RETRY": "breaks the loop"}),
        ("a harness that exits 0 from its loop when the test run succeeds",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -",
                         "\nfor attempt in 1 2 3; do\n    mvn -o -B test && exit 0\ndone\nset -"),
         {"HARNESS-RETRY": "ends the loop"}),
        ("a Python harness that loops on Maven until its exit status is 0",
         lambda r: _edit(r, "scripts/perf/performance.py", "\nimport ",
                         "\ndef _again(cmd):\n    for attempt in range(3):\n"
                         "        done = subprocess.run([\"mvn\", \"test\"])\n"
                         "        if done.returncode == 0:\n            break\n\nimport "),
         {"HARNESS-RETRY": "exit status is 0"}),
        ("an unregistered @Disabled on a test method",
         lambda r: _annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled(\"flaky\")"),
         {"UNREGISTERED": "@Disabled(\"flaky\") on " + ORDINARY_ID}),
        ("an unregistered @Disabled on a test class",
         lambda r: _annotate_class(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
         {"UNREGISTERED": "@Disabled on " + ORDINARY_ID + ":"}),
        ("a platform condition that turns a test off on Linux",
         lambda r: _annotate_first_test(r, ORDINARY, "@DisabledOnOs(OS.LINUX)"),
         {"PLATFORM": "disabled on LINUX"}),
        ("an opt-in property nothing recognises",
         lambda r: _annotate_first_test(r, ORDINARY,
                                        "@EnabledIfSystemProperty(named = \"cometgui.someday\", matches = \"true\")"),
         {"UNREGISTERED": "'cometgui.someday'"}),
        ("a quarantine tag",
         lambda r: _annotate_first_test(r, ORDINARY, "@Tag(\"quarantine\")"),
         {"UNREGISTERED": "['quarantine']"}),
        ("a surefire exclusion in a module POM",
         lambda r: _edit(r, "cometgui-ui/pom.xml", "<reuseForks>false</reuseForks>",
                         "<reuseForks>false</reuseForks>\n          <excludes><exclude>**/SectionIdTest.java"
                         "</exclude></excludes>"),
         {"UNREGISTERED": "<excludes> 'SectionIdTest'"}),
        ("excluded groups on a script's Maven command line",
         lambda r: _edit(r, "scripts/verify-shell-gates.sh", "\nset -", "\nX=\"-DexcludedGroups=slow\"\nset -"),
         {"UNREGISTERED": "excludes tests or groups"}),
        ("an assumption that skips on timing",
         lambda r: _edit(r, ORDINARY, "class ProjectIdTest {",
                         "class ProjectIdTest {\n    void slow(java.time.Duration took) {\n"
                         "        org.junit.jupiter.api.Assumptions.assumeTrue(took.toMillis() < 500);\n    }\n"),
         {"TIMING-ASSUMPTION": "skips itself on timing"}),
        ("an assumption the policy does not recognise",
         lambda r: _edit(r, ORDINARY, "class ProjectIdTest {",
                         "class ProjectIdTest {\n    void maybe(boolean ok) {\n"
                         "        org.junit.jupiter.api.Assumptions.assumeTrue(ok);\n    }\n"),
         {"UNREGISTERED": "does not recognise skips a test"}),
        ("an opt-in whose harness no longer sets it",
         lambda r: _edit(r, "scripts/perf/performance.py", "\"-Dcometgui.perf=true\",", "", 1),
         {"OPT-IN": "no longer sets -Dcometgui.perf"}),
        ("a quarantined test with every field: accepted",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled(\"flaky\")"),
                    _register(r, ORDINARY_ID)), {}),
        ("a register entry with no owner",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, owner=None)), {"REGISTER": "has no 'owner'"}),
        ("a register entry with no fix plan",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, fix_plan=None)), {"REGISTER": "has no 'fix_plan'"}),
        ("a register entry whose reason is a placeholder",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, reason="TODO: find out why it fails")),
         {"REGISTER": "'reason' is empty, too short or a placeholder"}),
        ("a register entry whose diagnostics were never captured",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, diagnostics="scripts/ci/quarantine/missing.log")),
         {"REGISTER": "'diagnostics'"}),
        ("a register entry dated in the future",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, since="2999-01-01")), {"REGISTER": "in the future"}),
        ("a register entry with no issue",
         lambda r: (_annotate_first_test(r, ORDINARY, "@org.junit.jupiter.api.Disabled"),
                    _register(r, ORDINARY_ID, issue=None)), {"REGISTER": "has no 'issue'"}),
        ("a register entry that quarantines nothing (stale)",
         lambda r: _register(r, ORDINARY_ID), {"REGISTER": "nothing it covers needs it (stale)"}),
        ("a register entry for a test whose only condition is a recognised one (stale)",
         lambda r: _register(r, "org.cometgui.app.e2e.oracle.DirectorySnapshotTest"),
         {"REGISTER": "nothing it covers needs it (stale)"}),
        ("a failure-path test quarantined",
         lambda r: (_annotate_class(r, FAILURE_PATH_TEST, "@org.junit.jupiter.api.Disabled"),
                    _register(r, FAILURE_PATH_ID)), {"RELEASE-CRITICAL": "failure-path map"}),
        ("a Tier A scenario quarantined",
         lambda r: (_annotate_class(r, TIER_A, "@org.junit.jupiter.api.Disabled"),
                    _register(r, TIER_A_ID)), {"RELEASE-CRITICAL": "end-to-end"}),
        ("a test a gate harness grades quarantined",
         lambda r: (_annotate_class(r, GRADED, "@org.junit.jupiter.api.Disabled"),
                    _register(r, GRADED_ID)), {"RELEASE-CRITICAL": "gate harness"}),
        ("a failure-path id whose method name another nested class shares",
         lambda r: _edit(r, "cometgui-install/src/test/java/org/cometgui/install/download/HttpDownloaderTest.java",
                         "class HttpDownloaderTest {",
                         "class HttpDownloaderTest {\n    @Nested\n    class Shadow {\n        @Test\n"
                         "        void aRefusedConnectionIsANetworkFailure() {}\n    }\n"),
         {"AMBIGUOUS-ID": "aRefusedConnectionIsANetworkFailure"}),
        ("a register that is missing (never read as empty)",
         lambda r: (r / REGISTER).unlink(), {"MISUSE": "no quarantine register"}),
        ("a tree with no test sources (a scan of nothing)",
         lambda r: [p.unlink() for p in r.rglob("*.java")], {"MISUSE": "a scan of nothing"}),
    ]


def _snapshot(root: Path) -> dict:
    """Every file's size and modification time: enough to tell whether an injection wrote."""
    seen = {}
    for parent, _, names in os.walk(root):
        for name in names:
            stat = os.stat(os.path.join(parent, name))
            seen[os.path.join(parent, name)] = (stat.st_size, stat.st_mtime_ns)
    return seen


def _run_case(pristine: Path, work: Path, injection) -> tuple[list[Problem], str | None]:
    if work.exists():
        shutil.rmtree(work)
    shutil.copytree(pristine, work, symlinks=True)
    before = _snapshot(work)
    injection(work)
    changed = before != _snapshot(work)
    try:
        problems, _ = check(work)
        return problems, ("changed" if changed else "unchanged")
    except Misuse as error:
        return [Problem("MISUSE", str(work), str(error))], ("changed" if changed else "unchanged")


def grade(name: str, expected: dict, problems: list[Problem], changed: str) -> str | None:
    """None when the case behaved as required, else what was wrong."""
    if expected and changed != "changed":
        return "the injection changed nothing, so the case tested nothing"
    kinds = {p.kind for p in problems}
    if not expected:
        return None if not problems else "expected green, got: " + "; ".join(map(str, problems[:3]))
    if kinds != set(expected):
        return f"expected exactly {sorted(expected)}, got {sorted(kinds) or 'green'}: " + \
            "; ".join(map(str, problems[:3]))
    for kind, words in expected.items():
        if not any(p.kind == kind and words in str(p) for p in problems):
            return f"red {kind}, but without its words {words!r}: " + "; ".join(map(str, problems[:3]))
    return None


def self_test(root: Path) -> int:
    root = root.resolve()
    failures = 0
    with tempfile.TemporaryDirectory(prefix="flakiness-selftest-") as tmp:
        pristine = Path(tmp) / "pristine"
        pristine.mkdir()
        wanted = re.compile(r"(^|/)pom\.xml$|^\.mvn/|/src/test/java/.*\.java$|^scripts/|^\.github/workflows/|"
                            r"^docs/traceability-map\.toml$|^docs/developer/limelight_integration\.rst$")
        for rel in tracked(root):
            if wanted.search(rel) and "__pycache__" not in rel:
                target = pristine / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(root / rel, target)
        crit = release_critical(pristine, tracked(pristine),
                                {p: parse_java(p, (pristine / p).read_text(encoding="utf-8"))
                                 for p in tracked(pristine) if p.endswith(".java")})
        every = set().union(*crit.values())
        if ORDINARY_ID in every or FAILURE_PATH_ID not in crit["failure-path map"] or \
                TIER_A_ID not in crit["end-to-end"] or GRADED_ID not in crit["gate harness"]:
            print("flakiness-check: self-test HARNESS FAILURE -- the specimens no longer have the roles "
                  "the cases assume (ordinary / failure-path / Tier A / graded)")
            return 7
        work = Path(tmp) / "work"
        all_cases = cases()
        for number, (name, injection, expected) in enumerate(all_cases, 1):
            problems, changed = _run_case(pristine, work, injection)
            wrong = grade(name, expected, problems, changed)
            verdict = "ok  " if wrong is None else "FAIL"
            shown = sorted(expected) or ["green"]
            print(f"  {verdict} {number:2d} {name} -> {', '.join(shown)}")
            if wrong:
                failures += 1
                print(f"        {wrong}")
        # Controls on the grader itself: an un-injected case, and a red graded
        # against another case's words, must both be reported as wrong.
        clean, _ = _run_case(pristine, work, lambda r: None)
        h1 = grade("H1", {"RETRY": "'rerunFailingTestsCount'"}, clean, "changed")
        print(f"  {'ok  ' if h1 else 'FAIL'} H1 a case whose defect was not injected: "
              f"{'reported as a HARNESS FAILURE' if h1 else 'NOT reported'}")
        failures += 0 if h1 else 1
        red, changed = _run_case(pristine, work, _pom_rerun)
        h2 = grade("H2", {"RETRY": "'testFailureIgnore'"}, red, changed)
        print(f"  {'ok  ' if h2 else 'FAIL'} H2 a red with another defect's words: "
              f"{'reported as a HARNESS FAILURE' if h2 else 'NOT reported'}")
        failures += 0 if h2 else 1
        unchanged = grade("H3", {"RETRY": "x"}, [], "unchanged")
        print(f"  {'ok  ' if unchanged else 'FAIL'} H3 an injection that changed no file: "
              f"{'reported as a HARNESS FAILURE' if unchanged else 'NOT reported'}")
        failures += 0 if unchanged else 1
        # the command line itself: exit 0 on the clean copy, 1 on a red one
        clean_rc = subprocess.run([sys.executable, "-I", "-B", __file__, "--root", str(pristine)],
                                  capture_output=True, text=True).returncode
        _run_case(pristine, work, _pom_rerun)
        red_rc = subprocess.run([sys.executable, "-I", "-B", __file__, "--root", str(work)],
                                capture_output=True, text=True).returncode
        cli_ok = clean_rc == 0 and red_rc == 1
        print(f"  {'ok  ' if cli_ok else 'FAIL'} C1 the command exits 0 on the clean copy ({clean_rc}) "
              f"and 1 on a red one ({red_rc})")
        failures += 0 if cli_ok else 1
        total = len(all_cases) + 4
    if failures:
        print(f"flakiness-check: self-test FAILED -- {failures} of {total} case(s) did not behave as required")
        return 7
    print(f"flakiness-check: self-test OK -- {total}/{total} cases ({len(all_cases) - 2} injected defects "
          f"each red with its own words, the clean tree and a fully registered quarantine green, two "
          f"refusals, three grader controls and the exit status)")
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--list-release-critical", action="store_true")
    args = parser.parse_args(argv)
    root = Path(args.root)
    if args.self_test:
        return self_test(root)
    if args.list_release_critical:
        return list_release_critical(root)
    return report(root)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
