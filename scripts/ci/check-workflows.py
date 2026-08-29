#!/usr/bin/env python3
"""Prove that the CI workflow files and the scripts they invoke cannot drift.

A workflow file is the one part of a build that nobody runs while developing.
It rots quietly: a script gets renamed, a step gets commented out "for now", a
`|| true` appears, and the pipeline stays green while checking less than it
did.  This is the check against that, and it is deliberately paranoid.

What it verifies
----------------
1. **Every ``run:`` names a script that exists and is executable.**  The command
   must be a single ``bash scripts/...`` invocation: no pipes, no ``&&``, no
   ``|| true``, no ``set +e``, no inline multi-line shell.  If a step cannot be
   expressed as one script, the logic belongs in a script.
2. **The pull-request pipeline covers the specification's required set.**  Each
   required item is a clause quoted verbatim from ``specification.rst``; the
   checker asserts the clause is STILL THERE (so an amended specification fails
   this check rather than silently diverging) and that a step implements it.
3. **No stub in the pull-request pipeline.**  A ``scripts/ci/stub-lib.sh`` step
   always exits 70, so one in the pull-request pipeline would make every pull
   request permanently red and teach everyone to ignore the pipeline.
4. **Every stage of ``scripts/build.sh`` is covered by the pull-request
   pipeline**, derived from the two files rather than from a list kept by hand:
   the stage ids come out of ``build.sh``'s STAGES array, and each workflow
   step's script is read to see which stage it runs.  Add a stage to the local
   gate without adding it to CI and this fails.
5. **The release pipeline cannot publish anything.**  No ``secrets.``, no
   ``git push``, no ``git remote``, no write permission, no action other than
   the pinned checkout.  D-008 is open and this repository has no remote; a
   release workflow that could push is a way for that decision to be made by
   accident.
6. **Actions are pinned to a commit SHA**, for the same reason build plugins
   are pinned to a version.
7. **Windows and macOS matrix entries are present where the specification
   requires them**, and are the only place non-Linux runners appear.

The YAML parser
---------------
PyYAML is NOT used, and must not be added: the project virtualenv is what Read
the Docs installs from ``docs/requirements.txt``, and a documentation
environment has no business growing a YAML parser so that a build script can
lint a workflow.  Instead this file contains a parser for the small, explicit
subset of YAML these three workflows are written in:

    block mappings, block sequences, plain and quoted scalars, comments on
    their own line.

and it REFUSES anything outside that subset -- flow collections (``[a, b]``,
``{a: b}``), anchors, aliases, tags, multi-line scalars (``|``, ``>``), tabs,
and trailing comments.  That refusal is the honest part: the parser never
guesses.  If a workflow is ever written with a construct it does not
understand, this check fails loudly and someone either simplifies the workflow
or extends the parser deliberately.

As a second, independent guard against a parser that silently skips something,
the raw text is scanned for ``run:``, ``uses:`` and ``continue-on-error`` and
the counts are required to match what the parser produced.

The parser was cross-checked once, on 2026-08-29, against PyYAML 6.0.3 in a
THROWAWAY virtualenv under ``_build/`` (created, used and deleted; nothing was
added to ``.venv`` or to ``docs/requirements.txt``).  All three workflow files
parsed to structures identical to ``yaml.safe_load``'s, with one documented
difference: **this parser does not type scalars.**  ``timeout-minutes: 90`` is
the string ``"90"`` here and the integer ``90`` in PyYAML, and ``fail-fast:
false`` is ``"false"`` rather than ``False``.  Nothing in this file compares a
scalar to a number or a boolean, so that difference does not affect any check;
it is written down because a future check that did compare one would be wrong.

WHAT THIS DOES NOT VERIFY.  It is not a GitHub Actions schema validator: it
does not know whether ``runs-on: ubuntu-latest`` is a real runner label, that
``timeout-minutes`` accepts an integer, or that an expression like
``${{ matrix.os }}`` resolves.  It has never seen GitHub execute these files,
because this repository has no remote (D-008).  It checks the correspondence
between the workflows, the scripts and the specification -- which is the part
that rots -- and says nothing about the rest.

Usage
-----
    python3 scripts/ci/check-workflows.py
    python3 scripts/ci/check-workflows.py --root DIR
    python3 scripts/ci/check-workflows.py --list-steps pull-request.yml
    python3 scripts/ci/check-workflows.py --self-test

``--list-steps`` prints one tab-separated ``job<TAB>runner<TAB>name<TAB>command``
row per step; ``scripts/ci/run-pipeline-locally.sh`` uses it to run the
workflows on this machine, so the transcript is produced from the workflow
files themselves rather than from a hand-written copy of them.

``--self-test`` copies the workflows and scripts under ``_build/``, damages the
copy nine ways -- renaming a script, removing its executable bit, deleting a
required step, adding ``continue-on-error``, adding ``|| true``, putting a stub
in the pull-request pipeline, adding a secret to the release pipeline, adding a
``git push``, and writing a construct the parser refuses -- and requires each
to be rejected.  The working tree is never touched.

Exit status
-----------
0  the workflows, the scripts and the specification agree
1  they do not; every problem is printed
2  misuse or a broken environment
3  a workflow file could not be parsed within the supported subset
4  --self-test only: a damaged copy was ACCEPTED, so this check is not
   falsifiable and must not be trusted
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

EXIT_OK, EXIT_PROBLEMS, EXIT_MISUSE, EXIT_PARSE, EXIT_UNFALSIFIABLE = 0, 1, 2, 3, 4

WORKFLOWS = ("pull-request.yml", "nightly.yml", "release.yml")


class ParseError(Exception):
    pass


# ==========================================================================
# A strict, refusing parser for the YAML subset these workflows use
# ==========================================================================

KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_.\-]*)\s*:(?:\s+(.*))?$")
FORBIDDEN_SCALAR_STARTS = "|>&*!{["


def _scalar(raw: str, lineno: int):
    value = raw.strip()
    if not value:
        return None
    if value[0] in FORBIDDEN_SCALAR_STARTS:
        raise ParseError(
            f"line {lineno}: value starts with {value[0]!r}. Flow collections, anchors, "
            f"aliases, tags and multi-line scalars are outside the subset this parser "
            f"accepts, and it will not guess. Rewrite the workflow, or extend the parser."
        )
    if value[0] == '"' and value.endswith('"') and len(value) >= 2:
        return value[1:-1].replace('\\"', '"')
    if value[0] == "'" and value.endswith("'") and len(value) >= 2:
        return value[1:-1].replace("''", "'")
    if " #" in value:
        raise ParseError(
            f"line {lineno}: {value!r} looks like it has a trailing comment. Real YAML "
            f"would strip it and this parser would not, so the two would disagree about "
            f"the value. Put the comment on its own line."
        )
    return value


def _logical_lines(text: str) -> list[tuple[int, int, str, bool]]:
    """(lineno, indent, content, is_sequence_item). Sequence items are expanded
    so that `- key: value` becomes a marker plus a line indented two further."""
    out: list[tuple[int, int, str, bool]] = []
    for lineno, raw in enumerate(text.splitlines(), start=1):
        if "\t" in raw:
            raise ParseError(f"line {lineno}: a tab. YAML forbids tabs for indentation.")
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped == "---":
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        if stripped.startswith("- ") or stripped == "-":
            out.append((lineno, indent, "", True))
            rest = stripped[2:] if stripped.startswith("- ") else ""
            if rest.strip():
                out.append((lineno, indent + 2, rest.strip(), False))
        else:
            out.append((lineno, indent, stripped, False))
    return out


class _Parser:
    def __init__(self, lines):
        self.lines = lines
        self.i = 0

    def at(self):
        return self.lines[self.i] if self.i < len(self.lines) else None

    def parse(self, indent):
        node = self.at()
        if node is None:
            return None
        lineno, node_indent, content, is_seq = node
        if node_indent != indent:
            raise ParseError(f"line {lineno}: indent {node_indent}, expected {indent}")
        return self.sequence(indent) if is_seq else self.mapping(indent)

    def sequence(self, indent):
        items = []
        while True:
            node = self.at()
            if node is None or node[1] != indent or not node[3]:
                break
            self.i += 1
            nxt = self.at()
            if nxt is None or nxt[1] <= indent:
                items.append(None)
                continue
            # A plain scalar item -- `- main`, `- ubuntu-latest`. Everything
            # else at a deeper indent is a nested block.
            if not nxt[3] and not KEY_RE.match(nxt[2]):
                items.append(_scalar(nxt[2], nxt[0]))
                self.i += 1
                continue
            items.append(self.parse(nxt[1]))
        return items

    def mapping(self, indent):
        result = {}
        while True:
            node = self.at()
            if node is None or node[1] != indent or node[3]:
                break
            lineno, _, content, _ = node
            match = KEY_RE.match(content)
            if not match:
                raise ParseError(
                    f"line {lineno}: {content!r} is neither `key: value` nor `key:`; this "
                    f"parser accepts only block mappings and block sequences."
                )
            key, inline = match.group(1), match.group(2)
            self.i += 1
            if inline is not None and inline.strip():
                if inline.strip().startswith("#"):
                    raise ParseError(f"line {lineno}: a trailing comment on {key!r}")
                result[key] = _scalar(inline, lineno)
                continue
            nxt = self.at()
            if nxt is not None and nxt[1] > indent:
                result[key] = self.parse(nxt[1])
            else:
                result[key] = None
        return result


def parse_yaml_subset(text: str):
    lines = _logical_lines(text)
    if not lines:
        raise ParseError("the file has no content")
    parser = _Parser(lines)
    value = parser.parse(lines[0][1])
    if parser.i != len(lines):
        lineno = parser.lines[parser.i][0]
        raise ParseError(f"line {lineno}: trailing content the parser could not attach to anything")
    return value


# ==========================================================================
# The project's own facts, read from the files rather than restated
# ==========================================================================

def normalise(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace("``", "")).strip()


def build_sh_stages(root: Path) -> tuple[list[str], dict[str, set[str]]]:
    """(stage ids in order, {stage id: set of scripts/ci/*.sh it invokes})."""
    build_sh = root / "scripts" / "build.sh"
    if not build_sh.is_file():
        raise SystemExit(f"check-workflows: no {build_sh}")
    text = build_sh.read_text(encoding="utf-8")
    block = re.search(r"readonly STAGES=\((.*?)\n\)", text, re.S)
    if not block:
        raise SystemExit("check-workflows: could not find the STAGES array in scripts/build.sh")
    ids = re.findall(r'^\s*"([a-z]+):', block.group(1), re.M)
    invokes: dict[str, set[str]] = {}
    for stage in ids:
        body = re.search(rf"^stage_{stage}\(\)\s*\{{(.*?)^\}}", text, re.S | re.M)
        invokes[stage] = set(re.findall(r"scripts/ci/([A-Za-z0-9_-]+\.sh)", body.group(1) if body else ""))
    return ids, invokes


# Each entry: (clause quoted verbatim from specification.rst, script that
# implements it, why).  The clause text is CHECKED against the specification on
# every run, so an amendment to the specification breaks this table instead of
# silently outdating it.
PR_REQUIRED = [
    ("Compile on the supported JDK", "maven-verify.sh",
     "the compiler runs inside mvn verify on the pinned Liberica JDK"),
    ("formatting and style check", "maven-verify.sh",
     "Spotless and Checkstyle are bound to validate"),
    ("static analysis", "maven-verify.sh",
     "SpotBugs is bound to verify"),
    ("fast JUnit tests", "maven-verify.sh",
     "Surefire runs the unit tests inside mvn verify"),
    ("JaCoCo coverage gate", "test-gates.sh",
     "the JaCoCo check executions run in mvn verify; this step proves they measured something"),
    ("ArchUnit tests", "test-gates.sh",
     "the ArchUnit rules are tests in cometgui-archtests; this step proves the import was not empty"),
    ("PIT mutation tests for critical packages", "test-gates.sh",
     "PIT is deliberately not part of mvn verify and is run here"),
    ("small real-tool integration tests on Linux", "integration-tests.sh",
     "there are none yet, and the step says so and asserts it"),
    ("Sphinx documentation build with warnings as errors", "docs-build.sh",
     "R-DOC-05's exact command line, plus the project documents outside docs/"),
    ("traceability report generation", "traceability.sh",
     "R-DOC-03"),
    ("dependency and security scanning", "dependency-scan.sh",
     "OSV, with a canary so an unreachable or lying endpoint fails"),
    ("SBOM generation validation", "sbom.sh",
     "CycloneDX, validated against the POMs rather than by exit code"),
]

# Steps the specification's sentence does not name but the pipeline cannot work
# without.  Named here so that removing one is a deliberate act.
PR_ALSO_REQUIRED = [
    ("toolchain.sh", "the pinned JDK and Maven, project-locally, instead of a setup-java action"),
    ("fontstack.sh", "PHASE-00: a Scene with any Control dies with 'fontFactory is null' without it"),
    ("python-env.sh", "the virtualenv the documentation gate needs"),
    ("artefacts.sh", "exit code 0 proves nothing; this checks the jars and test reports exist"),
    ("format-evidence.sh", "proves Spotless, Checkstyle and SpotBugs inspected the code rather than nothing"),
    ("check-workflows.sh", "this check itself, so the workflows are verified by the pipeline they define"),
]

NIGHTLY_REQUIRED = [
    ("the broader Comet and Percolator version matrix", "nightly-version-matrix.sh"),
    ("a larger real dataset", "nightly-large-dataset.sh"),
    ("determinism comparisons", "nightly-determinism.sh"),
    ("performance metrics", "nightly-performance.sh"),
    ("the headless and native GUI test suites", "nightly-gui-tests.sh"),
    ("the Windows RAW search test", "nightly-windows-raw.sh"),
    ("a documentation link check", "nightly-linkcheck.sh"),
    ("verification that every managed tool URL and checksum in the manifest is still reachable and unchanged",
     "nightly-manifest-verify.sh"),
]

RELEASE_REQUIRED = [
    ("build the native packaged application with its bundled runtime", "release-package.sh"),
    ("compute release checksums", "release-checksums.sh"),
    ("run the clean-home packaged smoke test", "release-smoke.sh"),
    ("run Tier B canonical E2E on that exact artefact", "release-e2e-canonical.sh"),
    ("run the XML-capable full Limelight E2E", "release-e2e-limelight.sh"),
    ("run the no-XML compatibility E2E", "release-e2e-noxml.sh"),
    ("verify the tool download manifest", "nightly-manifest-verify.sh"),
    ("verify the strict documentation build", "docs-build.sh"),
    ("generate and publish the SBOM", "sbom.sh"),
    ("verify that no test bridge is present", "release-no-test-bridge.sh"),
    ("sign and notarise where infrastructure permits", "release-sign-notarise.sh"),
    ("publish only if every gate passes", "release-publish.sh"),
]

FORBIDDEN_IN_RUN = [
    ("|| true", "swallows a failure"),
    ("|| :", "swallows a failure"),
    ("; true", "swallows a failure"),
    ("set +e", "disables failure propagation"),
    ("git push", "this repository has no remote and none may be created (D-008)"),
    ("git remote", "this repository has no remote and none may be created (D-008)"),
    ("|", "a pipeline hides the exit status of everything but the last command"),
    ("&&", "chained commands belong in a script"),
    (">", "redirection belongs in a script"),
]

RELEASE_FORBIDDEN_TOKENS = [
    ("secrets.", "a release workflow with a credential can publish; D-008 is open"),
    ("GITHUB_TOKEN", "same"),
    ("gh release", "same"),
    ("permissions: write", "same"),
    ("contents: write", "same"),
    ("id-token", "same"),
]

ACTION_SHA_RE = re.compile(r"^actions/checkout@[0-9a-f]{40}$")


# ==========================================================================
# The check
# ==========================================================================

def collect_steps(document, path: Path):
    """[(job, runs_on, step name, run command or None, uses or None)]"""
    jobs = document.get("jobs")
    if not isinstance(jobs, dict) or not jobs:
        raise ParseError(f"{path.name}: no jobs")
    rows = []
    for job_id, job in jobs.items():
        if not isinstance(job, dict):
            raise ParseError(f"{path.name}: job {job_id} is not a mapping")
        runs_on = job.get("runs-on")
        matrix = ((job.get("strategy") or {}).get("matrix") or {}) if isinstance(job.get("strategy"), dict) else {}
        runners = matrix.get("os") if isinstance(matrix.get("os"), list) else None
        label = ", ".join(runners) if runners else str(runs_on)
        steps = job.get("steps")
        if not isinstance(steps, list) or not steps:
            raise ParseError(f"{path.name}: job {job_id} has no steps")
        for step in steps:
            if not isinstance(step, dict):
                raise ParseError(f"{path.name}: job {job_id} has a step that is not a mapping")
            rows.append((job_id, label, step.get("name"), step.get("run"), step.get("uses"), step))
    return rows


def check(root: Path, verbose: bool = True) -> int:
    problems: list[str] = []
    workflow_dir = root / ".github" / "workflows"
    spec = root / "specification.rst"
    spec_text = normalise(spec.read_text(encoding="utf-8")) if spec.is_file() else ""
    if not spec_text:
        problems.append("specification.rst is missing or empty; the required-step tables cannot be checked")

    def say(*args):
        if verbose:
            print(*args)

    documents, all_steps = {}, {}
    for name in WORKFLOWS:
        path = workflow_dir / name
        if not path.is_file():
            problems.append(f"{name}: missing from .github/workflows/")
            continue
        text = path.read_text(encoding="utf-8")
        try:
            documents[name] = parse_yaml_subset(text)
            all_steps[name] = collect_steps(documents[name], path)
        except ParseError as exc:
            print(f"check-workflows: {name}: {exc}", file=sys.stderr)
            return EXIT_PARSE

        # Independent guard: the parser must not have skipped anything.
        # A literal space, not \s: \s also matches the newline, which would
        # count the `run:` of the `defaults:` block as a step and make these
        # counts disagree for a reason that has nothing to do with drift.
        raw_runs = len(re.findall(r"^[ ]+run: ", text, re.M))
        raw_uses = len(re.findall(r"^[ ]+uses: ", text, re.M))
        parsed_runs = sum(1 for r in all_steps[name] if r[3] is not None)
        parsed_uses = sum(1 for r in all_steps[name] if r[4] is not None)
        if (raw_runs, raw_uses) != (parsed_runs, parsed_uses):
            problems.append(
                f"{name}: the parser found {parsed_runs} run: and {parsed_uses} uses: keys but the "
                f"raw text has {raw_runs} and {raw_uses}. Something was skipped; this check is blind."
            )
        if "continue-on-error" in text:
            problems.append(f"{name}: contains continue-on-error. A check that cannot fail the "
                            f"pipeline is not a check.")
        say(f"check-workflows: {name}: {len(all_steps[name])} step(s), "
            f"{parsed_runs} run + {parsed_uses} uses (raw text agrees)")

    if not documents:
        for problem in problems:
            print(f"  * {problem}", file=sys.stderr)
        return EXIT_PROBLEMS

    # --- every run: is a single, existing, executable script ---------------
    scripts_used: dict[str, set[str]] = {name: set() for name in documents}
    for name, rows in all_steps.items():
        for job, _runner, step_name, run, uses, step in rows:
            if not step_name:
                problems.append(f"{name}/{job}: a step has no name")
            for key in step:
                if key not in ("name", "run", "uses", "with", "env", "working-directory"):
                    problems.append(f"{name}/{job}/{step_name}: unexpected step key {key!r}")
            if uses is not None:
                if not ACTION_SHA_RE.match(str(uses)):
                    problems.append(
                        f"{name}/{job}/{step_name}: uses {uses!r}. Only actions/checkout pinned to a "
                        f"40-character commit SHA is allowed: a tag is mutable, and any other action "
                        f"would mean CI and the local build are not the same build."
                    )
                continue
            if run is None:
                problems.append(f"{name}/{job}/{step_name}: neither run: nor uses:")
                continue
            command = str(run).strip()
            if "\n" in command:
                problems.append(f"{name}/{job}/{step_name}: multi-line run block; put the logic in a script")
                continue
            for token, why in FORBIDDEN_IN_RUN:
                if token in command:
                    problems.append(f"{name}/{job}/{step_name}: run contains {token!r} -- {why}")
            parts = command.split()
            if len(parts) < 2 or parts[0] != "bash" or not parts[1].startswith("scripts/"):
                problems.append(
                    f"{name}/{job}/{step_name}: run is {command!r}; every step must be a single "
                    f"`bash scripts/...` invocation a person can run locally"
                )
                continue
            script = parts[1]
            target = root / script
            if not target.is_file():
                problems.append(f"{name}/{job}/{step_name}: names {script}, which does not exist")
                continue
            if not os.access(target, os.X_OK):
                problems.append(f"{name}/{job}/{step_name}: {script} is not executable")
            scripts_used[name].add(script)

    # --- required steps, cross-checked against specification.rst -----------
    def require(workflow: str, table, label: str):
        for row in table:
            clause, script = row[0], row[1]
            if spec_text and normalise(clause).lower() not in spec_text.lower():
                problems.append(
                    f"{label}: specification.rst no longer contains the clause "
                    f"{clause!r}. The specification was amended; this table and the workflow "
                    f"must be brought back into line deliberately."
                )
            if f"scripts/ci/{script}" not in scripts_used.get(workflow, set()):
                problems.append(
                    f"{label}: the specification requires {clause!r} but no step in {workflow} "
                    f"runs scripts/ci/{script}"
                )

    require("pull-request.yml", PR_REQUIRED, "pull-request pipeline")
    require("nightly.yml", NIGHTLY_REQUIRED, "nightly pipeline")
    require("release.yml", RELEASE_REQUIRED, "release pipeline")

    for script, why in PR_ALSO_REQUIRED:
        if f"scripts/ci/{script}" not in scripts_used.get("pull-request.yml", set()):
            problems.append(f"pull-request pipeline: no step runs scripts/ci/{script} ({why})")

    # --- stubs are for nightly and release only ----------------------------
    def is_stub(script: str) -> bool:
        path = root / script
        return path.is_file() and "stub-lib.sh" in path.read_text(encoding="utf-8")

    for script in sorted(scripts_used.get("pull-request.yml", set())):
        if is_stub(script):
            problems.append(
                f"pull-request pipeline: {script} is a stub (it exits 70 always). A stub in the "
                f"pull-request pipeline makes every pull request red for a reason nobody can fix."
            )

    # --- every build.sh stage is covered by the pull-request pipeline ------
    stage_ids, stage_scripts = build_sh_stages(root)
    covered: set[str] = set()
    for script in scripts_used.get("pull-request.yml", set()):
        path = root / script
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        for match in re.finditer(r"--only\s+([a-z,]+)", text):
            covered.update(match.group(1).split(","))
        for stage, invoked in stage_scripts.items():
            if Path(script).name in invoked:
                covered.add(stage)
    missing_stages = [stage for stage in stage_ids if stage not in covered]
    if missing_stages:
        problems.append(
            f"pull-request pipeline: scripts/build.sh has stage(s) {missing_stages} that no step "
            f"runs. The local gate and CI would then be checking different things."
        )
    say(f"check-workflows: scripts/build.sh has {len(stage_ids)} stage(s); the pull-request "
        f"pipeline covers {sorted(covered & set(stage_ids))}")

    # --- the release pipeline must not be able to publish ------------------
    release_path = workflow_dir / "release.yml"
    if release_path.is_file():
        release_text = release_path.read_text(encoding="utf-8")
        body = "\n".join(line for line in release_text.splitlines() if not line.lstrip().startswith("#"))
        for token, why in RELEASE_FORBIDDEN_TOKENS:
            if token in body:
                problems.append(f"release.yml: contains {token!r} -- {why}")
        permissions = (documents.get("release.yml") or {}).get("permissions")
        if permissions != {"contents": "read"}:
            problems.append(f"release.yml: permissions is {permissions!r}, expected {{contents: read}}")
        say("check-workflows: release.yml has no secret, no push, no remote and read-only permissions")

    # --- the non-Linux runners are named where the specification wants them
    nightly_runners = {row[1] for row in all_steps.get("nightly.yml", [])}
    release_runners = {row[1] for row in all_steps.get("release.yml", [])}
    if not any("windows" in r for r in nightly_runners):
        problems.append("nightly.yml: the specification requires the Windows RAW search test; "
                        "no job runs on a Windows runner")
    if not all(any(o in r for r in release_runners) for o in ("ubuntu", "windows", "macos")):
        problems.append("release.yml: the specification says 'for each tier-1 platform'; the matrix "
                        f"is {sorted(release_runners)}")
    pr_runners = {row[1] for row in all_steps.get("pull-request.yml", [])}
    if any(("windows" in r or "macos" in r) for r in pr_runners):
        problems.append("pull-request.yml: the specification scopes the pull-request integration "
                        "tests to Linux; a non-Linux runner here has never been verified")
    say(f"check-workflows: runners -- pull-request {sorted(pr_runners)}, "
        f"nightly {sorted(nightly_runners)}, release {sorted(release_runners)}")

    if problems:
        print("\ncheck-workflows: FAILED", file=sys.stderr)
        for problem in problems:
            print(f"  * {problem}", file=sys.stderr)
        return EXIT_PROBLEMS

    say("\ncheck-workflows: PASSED -- every step names a script that exists and is executable, "
        "every clause\ncheck-workflows: the specification requires has a step, and every "
        "scripts/build.sh stage is covered.")
    return EXIT_OK


def list_steps(root: Path, workflow: str) -> int:
    path = root / ".github" / "workflows" / workflow
    if not path.is_file():
        print(f"check-workflows: no {path}", file=sys.stderr)
        return EXIT_MISUSE
    try:
        rows = collect_steps(parse_yaml_subset(path.read_text(encoding="utf-8")), path)
    except ParseError as exc:
        print(f"check-workflows: {workflow}: {exc}", file=sys.stderr)
        return EXIT_PARSE
    for job, runner, name, run, uses, _step in rows:
        kind = "run" if run is not None else "uses"
        value = run if run is not None else uses
        print(f"{job}\t{runner}\t{name}\t{kind}\t{value}")
    return EXIT_OK


# ==========================================================================
# --self-test
# ==========================================================================

def self_test(root: Path) -> int:
    work = root / "_build" / "workflow-selftest"
    if work.exists():
        shutil.rmtree(work)
    tree = work / "tree"
    tree.mkdir(parents=True)
    for item in (".github", "scripts"):
        shutil.copytree(root / item, tree / item, symlinks=True)
    shutil.copy2(root / "specification.rst", tree / "specification.rst")

    print(f"=== check-workflows --self-test: damaged copies under {tree} ===")
    print("The working tree is never touched.\n")

    pr = tree / ".github" / "workflows" / "pull-request.yml"
    rel = tree / ".github" / "workflows" / "release.yml"
    pristine_pr = pr.read_text(encoding="utf-8")
    pristine_rel = rel.read_text(encoding="utf-8")

    results = []

    def case(label, damage, undo, expected):
        damage()
        code = check(tree, verbose=False)
        undo()
        ok = code == expected
        results.append(ok)
        print(f"  {'ok  ' if ok else 'FAIL'} {label:<34} exit {code} (expected {expected})")

    def restore():
        pr.write_text(pristine_pr, encoding="utf-8")
        rel.write_text(pristine_rel, encoding="utf-8")

    target = tree / "scripts" / "ci" / "traceability.sh"
    case("renamed script",
         lambda: target.rename(target.with_name("traceability-RENAMED.sh")),
         lambda: target.with_name("traceability-RENAMED.sh").rename(target),
         EXIT_PROBLEMS)

    case("script not executable",
         lambda: target.chmod(0o644),
         lambda: target.chmod(0o755),
         EXIT_PROBLEMS)

    case("required step deleted",
         lambda: pr.write_text(
             pristine_pr.replace(
                 "      - name: Dependency and security scanning (OSV)\n"
                 "        run: bash scripts/ci/dependency-scan.sh\n", ""),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("continue-on-error added",
         lambda: pr.write_text(
             pristine_pr.replace("        run: bash scripts/ci/test-gates.sh",
                                 "        continue-on-error: true\n        run: bash scripts/ci/test-gates.sh"),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("|| true added to a run",
         lambda: pr.write_text(
             pristine_pr.replace("run: bash scripts/ci/sbom.sh",
                                 "run: bash scripts/ci/sbom.sh || true"),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("stub put in the PR pipeline",
         lambda: pr.write_text(
             pristine_pr.replace("run: bash scripts/ci/integration-tests.sh",
                                 "run: bash scripts/ci/nightly-determinism.sh"),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("secret added to release.yml",
         lambda: rel.write_text(
             pristine_rel.replace("      - name: Compute release checksums",
                                  "      - name: Compute release checksums\n"
                                  "        env:\n"
                                  "          TOKEN: ${{ secrets.PUBLISH_TOKEN }}"),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("git push added to release.yml",
         lambda: rel.write_text(
             pristine_rel.replace("run: bash scripts/ci/release-publish.sh",
                                  "run: git push origin main"),
             encoding="utf-8"),
         restore, EXIT_PROBLEMS)

    case("YAML outside the parser's subset",
         lambda: pr.write_text(
             pristine_pr.replace("        run: bash scripts/ci/sbom.sh",
                                 "        run: |\n          bash scripts/ci/sbom.sh\n          echo done"),
             encoding="utf-8"),
         restore, EXIT_PARSE)

    control = check(tree, verbose=False)
    control_ok = control == EXIT_OK
    results.append(control_ok)
    print(f"  {'ok  ' if control_ok else 'FAIL'} {'control-undamaged-copy':<34} exit {control} (expected 0)")

    if all(results):
        print(f"\ncheck-workflows: self-test OK -- {len(results) - 1} damaged copies rejected, "
              f"the undamaged one accepted.")
        return EXIT_OK
    print("\ncheck-workflows: SELF-TEST FAILED -- a damaged copy was accepted, or the undamaged "
          "one was rejected.", file=sys.stderr)
    return EXIT_UNFALSIFIABLE


def main(argv: list[str]) -> int:
    here = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", default=str(here.parent.parent))
    parser.add_argument("--list-steps", default=None, metavar="WORKFLOW")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    if args.list_steps:
        return list_steps(root, args.list_steps)
    if args.self_test:
        return self_test(root)
    return check(root)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
