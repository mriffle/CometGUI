#!/usr/bin/env python3
#
# CometGUI -- Comet to Percolator proteomics search workflow with provenance.
# Copyright (C) 2026 The CometGUI authors.
# SPDX-License-Identifier: GPL-3.0-only
#
# failure_paths.py -- the checker behind scripts/e2e/check-failure-paths.sh
# (Phase 14 exit-gate item 4: "Every failure-path scenario in the
# specification has a passing test").
#
# Three checks, each of which can only pass by observation:
#
#   (a) the map's scenarios are EXACTLY the specification's: the paragraph
#       under "Failure-path scenarios" is parsed out of specification.rst on
#       every run, and a scenario missing, added, reworded or moved is a
#       failure;
#   (b) every mapped test exists in the source tree: its class file is found,
#       any nested class is declared in it, and the method is declared with a
#       JUnit test annotation and without @Disabled;
#   (c) given surefire report directories, every mapped test in scope RAN and
#       PASSED -- counted from the reports, so a run of zero is a failure, a
#       skip is a failure, and a missing report is a failure.
#
# Exit status: 0 every check passed; 1 a check failed (each finding named);
# 2 misuse (bad arguments, an unreadable file, a map that is not TOML).

import argparse
import os
import re
import sys
import tomllib
import xml.etree.ElementTree as ElementTree

HEADING = "Failure-path scenarios"
LEAD = "Separate end-to-end and integration tests shall exercise:"
STATUSES = ("covered", "partial")
LEVELS = ("gui-e2e", "gui", "integration", "unit+real", "unit")
RUNS = ("ordinary", "e2e")
TEST_ID = re.compile(r"^((?:[a-z_][a-z0-9_]*\.)+)([A-Z][A-Za-z0-9_]*)((?:\$[A-Z][A-Za-z0-9_]*)*)#([a-z_][A-Za-z0-9_]*)$")
TEST_ANNOTATION = re.compile(r"@(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b")
DISABLED = re.compile(r"@Disabled\w*\b")


class Misuse(Exception):
    """Exit status 2."""


def collapse(text):
    return " ".join(text.split())


# ------------------------------------------------------------------ (a) ------


def spec_scenarios(spec_text):
    """The scenarios of the specification's paragraph, in its order, or raise ValueError."""
    lines = spec_text.splitlines()
    found = [
        index
        for index, line in enumerate(lines[:-1])
        if line.strip() == HEADING
        and lines[index + 1].strip()
        and set(lines[index + 1].strip()) <= set("=-~^\"'`#*+")
        and len(lines[index + 1].strip()) >= len(HEADING)
    ]
    if len(found) != 1:
        raise ValueError(
            'the specification has %d section(s) titled "%s"; exactly one is required' % (len(found), HEADING)
        )
    cursor = found[0] + 2
    while cursor < len(lines) and not lines[cursor].strip():
        cursor += 1
    paragraph = []
    while cursor < len(lines) and lines[cursor].strip():
        paragraph.append(lines[cursor])
        cursor += 1
    text = collapse(" ".join(paragraph))
    if not text.startswith(LEAD):
        raise ValueError(
            'the "%s" paragraph no longer begins "%s"; it begins: %s' % (HEADING, LEAD, text[:120])
        )
    body = text[len(LEAD):].strip()
    if not body.endswith("."):
        raise ValueError('the "%s" list does not end with a full stop' % HEADING)
    items = [item.strip() for item in body[:-1].split(";")]
    if items and items[-1].startswith("and "):
        items[-1] = items[-1][len("and "):]
    if not items or any(not item for item in items):
        raise ValueError('the "%s" list has an empty item: %r' % (HEADING, items))
    return items


def check_scenarios(spec_items, scenarios, findings):
    mapped = [collapse(str(scenario.get("text", ""))) for scenario in scenarios]
    numbers = [scenario.get("number") for scenario in scenarios]
    expected_numbers = list(range(1, len(scenarios) + 1))
    if numbers != expected_numbers:
        findings.append("the map's scenario numbers are %s, not 1..%d in order" % (numbers, len(scenarios)))
    if mapped == spec_items:
        return
    for item in spec_items:
        if item not in mapped:
            findings.append('scenario missing from the map: "%s"' % item)
    for item in mapped:
        if item not in spec_items:
            findings.append('scenario in the map but not in the specification (added or reworded): "%s"' % item)
    if sorted(mapped) == sorted(spec_items):
        for position, (want, have) in enumerate(zip(spec_items, mapped), start=1):
            if want != have:
                findings.append(
                    'scenario %d is out of order: the specification has "%s", the map "%s"' % (position, want, have)
                )
    if len(mapped) != len(spec_items):
        findings.append("the map holds %d scenario(s); the specification lists %d" % (len(mapped), len(spec_items)))


# ------------------------------------------------------------------ (b) ------


def strip_java(source):
    """Source with comments, strings, text blocks and char literals blanked, line structure kept."""
    out = []
    index = 0
    length = len(source)
    while index < length:
        char = source[index]
        two = source[index:index + 2]
        if two == "//":
            end = source.find("\n", index)
            end = length if end < 0 else end
            index = end
        elif two == "/*":
            end = source.find("*/", index + 2)
            end = length if end < 0 else end + 2
            out.append(re.sub(r"[^\n]", " ", source[index:end]))
            index = end
        elif source.startswith('"""', index):
            end = source.find('"""', index + 3)
            end = length if end < 0 else end + 3
            out.append('""' + re.sub(r"[^\n]", " ", source[index + 2:end - 2]) + '""')
            index = end
        elif char in "\"'":
            cursor = index + 1
            while cursor < length and source[cursor] != char:
                cursor += 2 if source[cursor] == "\\" else 1
            out.append(char + char)
            index = cursor + 1
        else:
            out.append(char)
            index += 1
    return "".join(out)


def source_files(root):
    found = {}
    for module in sorted(os.listdir(root)):
        tests = os.path.join(root, module, "src", "test", "java")
        if os.path.isdir(tests):
            found[module] = tests
    return found


def check_test_exists(test_id, roots, cache, findings):
    match = TEST_ID.match(test_id)
    if not match:
        findings.append("%s: not a test id of the form package.Class#method (Outer$Inner#method for a nested class)" % test_id)
        return
    package, outer, nested, method = match.group(1)[:-1], match.group(2), match.group(3), match.group(4)
    relative = os.path.join(*package.split("."), outer + ".java")
    files = [os.path.join(tests, relative) for tests in roots.values() if os.path.isfile(os.path.join(tests, relative))]
    if len(files) != 1:
        findings.append("%s: %d source file(s) for %s.%s under */src/test/java" % (test_id, len(files), package, outer))
        return
    if files[0] not in cache:
        with open(files[0], encoding="utf-8") as handle:
            cache[files[0]] = strip_java(handle.read())
    code = cache[files[0]]
    for inner in [name for name in nested.split("$") if name]:
        if not re.search(r"\bclass\s+%s\b" % re.escape(inner), code):
            findings.append("%s: no nested class %s is declared in %s" % (test_id, inner, files[0]))
            return
    declarations = list(re.finditer(r"\bvoid\s+%s\s*\(" % re.escape(method), code))
    if not declarations:
        findings.append("%s: no method %s is declared in %s" % (test_id, method, files[0]))
        return
    annotated = [names for names in (annotations_before(code, d.start()) for d in declarations)
                 if any(TEST_ANNOTATION.fullmatch("@" + name) for name in names)]
    if not annotated:
        findings.append("%s: %s is declared but is not a test (no JUnit test annotation)" % (test_id, method))
    elif all(any(DISABLED.fullmatch("@" + name) for name in names) for names in annotated):
        findings.append("%s: the test is @Disabled" % test_id)


MODIFIERS = {"public", "protected", "private", "static", "final", "synchronized", "abstract", "default", "strictfp"}


def annotations_before(code, position):
    """The simple names of the annotations on the member declared at position, nearest last."""
    names = []
    cursor = position
    while True:
        while cursor > 0 and code[cursor - 1].isspace():
            cursor -= 1
        if cursor > 0 and code[cursor - 1] == ")":
            depth = 0
            scan = cursor - 1
            while scan >= 0:
                if code[scan] == ")":
                    depth += 1
                elif code[scan] == "(":
                    depth -= 1
                    if depth == 0:
                        break
                scan -= 1
            if scan < 0:
                return names
            cursor = scan
            while cursor > 0 and code[cursor - 1].isspace():
                cursor -= 1
        word_end = cursor
        while cursor > 0 and (code[cursor - 1].isalnum() or code[cursor - 1] in "_."):
            cursor -= 1
        word = code[cursor:word_end]
        if not word:
            return names
        if cursor > 0 and code[cursor - 1] == "@":
            names.append(word.split(".")[-1])
            cursor -= 1
        elif word in MODIFIERS:
            continue
        else:
            return names


# ------------------------------------------------------------------ (c) ------


def report_index(directories):
    """Every TEST-*.xml under the directories, by the class its file name names."""
    index = {}
    for directory in directories:
        if not os.path.isdir(directory):
            raise Misuse("no report directory %s" % directory)
        for parent, _, names in os.walk(directory):
            for name in names:
                if name.startswith("TEST-") and name.endswith(".xml"):
                    index.setdefault(name[len("TEST-"):-len(".xml")], []).append(os.path.join(parent, name))
    return index


def grade(test_id, index):
    """(ran, passed, problems) for one test id, counted from the reports."""
    match = TEST_ID.match(test_id)
    outer = match.group(1) + match.group(2)
    method = match.group(4)
    files = index.get(outer, [])
    if not files:
        return 0, 0, ["no surefire report for %s" % outer]
    ran = passed = 0
    problems = []
    for path in files:
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as unreadable:
            problems.append("%s cannot be parsed: %s" % (path, unreadable))
            continue
        for case in root.iter("testcase"):
            name = case.get("name", "")
            if name != method and not name.startswith(method + "("):
                continue
            if case.find("skipped") is not None:
                problems.append("%s was skipped (%s)" % (name, os.path.basename(path)))
                continue
            ran += 1
            if case.find("failure") is not None or case.find("error") is not None:
                problems.append("%s failed (%s)" % (name, os.path.basename(path)))
            else:
                passed += 1
    if ran == 0 and not problems:
        problems.append("no test case %s ran in %s" % (method, ", ".join(os.path.basename(f) for f in files)))
    return ran, passed, problems


# ------------------------------------------------------------------ main -----


def load_map(path):
    try:
        with open(path, "rb") as handle:
            document = tomllib.load(handle)
    except (OSError, tomllib.TOMLDecodeError) as unreadable:
        raise Misuse("the map %s cannot be read: %s" % (path, unreadable))
    scenarios = document.get("scenario")
    if not isinstance(scenarios, list) or not scenarios:
        raise Misuse("the map %s holds no [[scenario]]" % path)
    return scenarios


def check(spec, map_path, source_root, reports, scope, allow_partial):
    findings = []
    notes = []
    try:
        with open(spec, encoding="utf-8") as handle:
            spec_text = handle.read()
    except OSError as unreadable:
        raise Misuse("the specification %s cannot be read: %s" % (spec, unreadable))
    scenarios = load_map(map_path)
    try:
        spec_items = spec_scenarios(spec_text)
    except ValueError as moved:
        findings.append(str(moved))
        spec_items = []
    if spec_items:
        check_scenarios(spec_items, scenarios, findings)
    print("(a) %d scenario(s) in the specification, %d in the map" % (len(spec_items), len(scenarios)))

    roots = source_files(source_root)
    if not roots:
        raise Misuse("no */src/test/java under %s" % source_root)
    cache = {}
    ids = []
    for scenario in scenarios:
        number = scenario.get("number")
        status = scenario.get("status")
        tests = scenario.get("test", [])
        if status not in STATUSES:
            findings.append("scenario %s: status %r is none of %s" % (number, status, STATUSES))
        if not str(scenario.get("note", "")).strip():
            findings.append("scenario %s: no note" % number)
        if status == "covered" and not tests:
            findings.append("scenario %s is marked covered and maps no test" % number)
        if status == "partial":
            message = "scenario %s is partial: %s" % (number, scenario.get("note", ""))
            (notes if allow_partial else findings).append(message)
        for test in tests:
            test_id = str(test.get("id", ""))
            if test.get("level") not in LEVELS:
                findings.append("%s: level %r is none of %s" % (test_id, test.get("level"), LEVELS))
            if test.get("run") not in RUNS:
                findings.append("%s: run %r is none of %s" % (test_id, test.get("run"), RUNS))
            ids.append((number, status, test_id, test.get("run")))
    distinct = sorted({test_id for _, _, test_id, _ in ids})
    before = len(findings)
    for test_id in distinct:
        check_test_exists(test_id, roots, cache, findings)
    print("(b) %d distinct test id(s) checked against %d test source tree(s): %d not found or not a test"
          % (len(distinct), len(roots), len(findings) - before))

    if reports:
        index = report_index(reports)
        if not index:
            findings.append("no TEST-*.xml at all under %s: a run of zero is not a pass" % ", ".join(reports))
        graded = {}
        for number, status, test_id, run in ids:
            if scope != "all" and run != scope:
                continue
            if test_id not in graded:
                graded[test_id] = grade(test_id, index)
        for test_id in sorted(graded):
            ran, passed, problems = graded[test_id]
            print("    %-118s ran %d, passed %d" % (test_id, ran, passed))
            for problem in problems:
                findings.append("%s: %s" % (test_id, problem))
        for scenario in scenarios:
            in_scope = [t for t in scenario.get("test", []) if scope == "all" or t.get("run") == scope]
            if scenario.get("status") == "covered" and not in_scope:
                findings.append("scenario %s has no test in scope %s" % (scenario.get("number"), scope))
        not_graded = sorted({test_id for _, _, test_id, run in ids if scope != "all" and run != scope})
        for test_id in not_graded:
            notes.append("not graded in scope %s (needs the %s run): %s" % (scope, "e2e" if scope == "ordinary" else "ordinary", test_id))
        total_ran = sum(r for r, _, _ in graded.values())
        total_passed = sum(p for _, p, _ in graded.values())
        print("(c) scope %s: %d test id(s) graded, %d test case(s) ran, %d passed" % (scope, len(graded), total_ran, total_passed))
        if not graded:
            findings.append("no mapped test is in scope %s: nothing was graded" % scope)
    else:
        print("(c) not run: no --reports given")

    for note in notes:
        print("    NOTE: " + note)
    for finding in findings:
        print("    FAIL: " + finding)
    return 1 if findings else 0


# ------------------------------------------------------------- self-test -----


def _capture(function, *arguments):
    import contextlib
    import io

    buffer = io.StringIO()
    with contextlib.redirect_stdout(buffer):
        try:
            status = function(*arguments)
        except Misuse as misuse:
            print("MISUSE: %s" % misuse)
            status = 2
    return status, buffer.getvalue()


def _replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise AssertionError("control %s: its anchor matched %d time(s), not once: %r" % (label, count, old[:80]))
    damaged = text.replace(old, new)
    if damaged == text:
        raise AssertionError("control %s: the injection changed nothing" % label)
    return damaged


def _write_reports(directory, scenarios, scope, mutate=None):
    os.makedirs(directory, exist_ok=True)
    by_class = {}
    for scenario in scenarios:
        for test in scenario.get("test", []):
            if scope != "all" and test.get("run") != scope:
                continue
            match = TEST_ID.match(test["id"])
            outer = match.group(1) + match.group(2)
            by_class.setdefault(outer, [])
            if match.group(4) not in by_class[outer]:
                by_class[outer].append(match.group(4))
    for outer, methods in by_class.items():
        cases = []
        for method in methods:
            body = ""
            name = method + "()"
            if mutate is not None:
                name, body = mutate(outer, method, name, body)
            cases.append('  <testcase name="%s" classname="%s" time="0.1">%s</testcase>' % (name, outer, body))
        with open(os.path.join(directory, "TEST-%s.xml" % outer), "w", encoding="utf-8") as handle:
            handle.write('<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="%d">\n%s\n</testsuite>\n'
                         % (outer, len(cases), "\n".join(cases)))
    return by_class


def self_test(spec, map_path, source_root, workdir):
    import shutil

    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(workdir)
    with open(map_path, encoding="utf-8") as handle:
        real_map = handle.read()
    with open(spec, encoding="utf-8") as handle:
        real_spec = handle.read()
    scenarios = load_map(map_path)
    failures = []
    controls = 0

    def control(label, expected, map_text=real_map, spec_text=real_spec, reports=None, allow_partial=True,
                scope="ordinary", want_status=1):
        nonlocal controls
        controls += 1
        here = os.path.join(workdir, label)
        os.makedirs(here, exist_ok=True)
        damaged_map = os.path.join(here, "failure-paths.toml")
        damaged_spec = os.path.join(here, "specification.rst")
        with open(damaged_map, "w", encoding="utf-8") as handle:
            handle.write(map_text)
        with open(damaged_spec, "w", encoding="utf-8") as handle:
            handle.write(spec_text)
        status, output = _capture(check, damaged_spec, damaged_map, source_root, reports or [], scope, allow_partial)
        if status != want_status:
            failures.append("%s: exit %d, wanted %d\n%s" % (label, status, want_status, output))
            print("  HARNESS FAILURE: %s exited %d, wanted %d" % (label, status, want_status))
        elif expected and expected not in output:
            failures.append("%s: rejected, but without the diagnostic %r\n%s" % (label, expected, output))
            print("  HARNESS FAILURE: %s rejected without its diagnostic: %s" % (label, expected))
        else:
            print("  %-34s %s" % (label, "accepted, as it must be" if want_status == 0 else "rejected: " + expected))

    try:
        block_7 = real_map[real_map.index("[[scenario]]\nnumber = 7\n"):real_map.index("[[scenario]]\nnumber = 8\n")]
        removed = _replace_once(real_map, block_7, "", "M1")
        for number in range(8, 26):
            removed = _replace_once(removed, "number = %d\n" % number, "number = %d\n" % (number - 1), "M1")
        control("M1-scenario-removed", 'scenario missing from the map: "a FASTA with no decoys', removed)
        control("M2-scenario-reworded", 'not in the specification (added or reworded): "Comet exits nonzero"',
                _replace_once(real_map, 'text = "Comet exits non-zero"', 'text = "Comet exits nonzero"', "M2"))
        control("M3-scenario-added", 'not in the specification (added or reworded): "the moon falls"',
                real_map + '\n[[scenario]]\nnumber = 26\ntext = "the moon falls"\nstatus = "partial"\nnote = "x"\n')
        swapped = _replace_once(real_map, 'text = "Percolator exits non-zero"', 'text = "@@NINE@@"', "M4")
        swapped = _replace_once(swapped, 'text = "malformed Percolator output"', 'text = "Percolator exits non-zero"', "M4")
        swapped = _replace_once(swapped, 'text = "@@NINE@@"', 'text = "malformed Percolator output"', "M4")
        control("M4-scenarios-swapped", "scenario 9 is out of order", swapped)
        control("M5-spec-gained-a-scenario", 'scenario missing from the map: "a cosmic ray"', real_map,
                _replace_once(real_spec, "reopened; and a second CometGUI", "reopened; a cosmic ray; and a second CometGUI", "M5"))
        control("M6-spec-heading-renamed", 'section(s) titled "Failure-path scenarios"', real_map,
                _replace_once(real_spec, "\nFailure-path scenarios\n", "\nFailure path scenarios\n", "M6"))
        control("M7-class-does-not-exist", "0 source file(s) for org.cometgui.workflow.steps.NoSuchFailureTest",
                _replace_once(real_map, '"org.cometgui.workflow.steps.PercolatorStepTest#aNonZeroExitFails"',
                              '"org.cometgui.workflow.steps.NoSuchFailureTest#aNonZeroExitFails"', "M7"))
        control("M8-method-does-not-exist", "no method aNonZeroExitFailsQuietly is declared",
                _replace_once(real_map, '"org.cometgui.workflow.steps.PercolatorStepTest#aNonZeroExitFails"',
                              '"org.cometgui.workflow.steps.PercolatorStepTest#aNonZeroExitFailsQuietly"', "M8"))
        control("M9-a-helper-not-a-test", "assertNoCometAlive is declared but is not a test",
                _replace_once(real_map, '"org.cometgui.app.gui.RealRunUiTest#aFailureIsStated"',
                              '"org.cometgui.app.gui.RealRunUiTest#assertNoCometAlive"', "M9"))
        control("M10-nested-class-missing", "no nested class ThroughTheMoon",
                _replace_once(real_map, "AwkwardPathTest$ThroughTheService#theWorkingDirectory",
                              "AwkwardPathTest$ThroughTheMoon#theWorkingDirectory", "M10"))
        block_9 = real_map[real_map.index("[[scenario]]\nnumber = 9\n"):real_map.index("[[scenario]]\nnumber = 10\n")]
        control("M11-covered-with-no-test", "scenario 9 is marked covered and maps no test",
                _replace_once(real_map, block_9, block_9[:block_9.index("  [[scenario.test]]")], "M11"))
        block_14 = real_map[real_map.index("[[scenario]]\nnumber = 14\n"):real_map.index("[[scenario]]\nnumber = 15\n")]
        partial_14 = _replace_once(
            real_map, block_14, _replace_once(block_14, 'status = "covered"', 'status = "partial"', "M12"), "M12")
        control("M12-partial-not-allowed", "scenario 14 is partial", partial_14, allow_partial=False)
        control("M12-partial-allowed-when-asked", "", partial_14, allow_partial=True, want_status=0)
        control("M13-malformed-id", "not a test id of the form",
                _replace_once(real_map, '"org.cometgui.workflow.steps.PercolatorStepTest#aNonZeroExitFails"',
                              '"PercolatorStepTest.aNonZeroExitFails"', "M13"))

        passing = os.path.join(workdir, "reports-passing")
        classes = _write_reports(passing, scenarios, "ordinary")
        if not classes:
            raise AssertionError("the real map holds no ordinary test to write a report for")
        control("R0-every-report-passing", "", reports=[passing], want_status=0)
        target = ("org.cometgui.workflow.steps.PercolatorStepTest", "aNonZeroExitFails")

        def damaging(kind):
            def mutate(outer, method, name, body):
                if (outer, method) != target:
                    return name, body
                if kind == "skipped":
                    return name, '<skipped message="disabled"/>'
                if kind == "failed":
                    return name, '<failure message="expected: &lt;1&gt;"/>'
                if kind == "error":
                    return name, '<error message="boom"/>'
                if kind == "renamed":
                    return "aNonZeroExitFailsElsewhere()", body
                return name, body
            return mutate

        for kind, expected in (("skipped", "aNonZeroExitFails() was skipped"),
                               ("failed", "aNonZeroExitFails() failed"),
                               ("error", "aNonZeroExitFails() failed"),
                               ("renamed", "no test case aNonZeroExitFails ran")):
            directory = os.path.join(workdir, "reports-" + kind)
            _write_reports(directory, scenarios, "ordinary", damaging(kind))
            control("R-%s" % kind, "org.cometgui.workflow.steps.PercolatorStepTest#aNonZeroExitFails: " + expected,
                    reports=[directory])
        missing = os.path.join(workdir, "reports-missing")
        _write_reports(missing, scenarios, "ordinary")
        os.remove(os.path.join(missing, "TEST-org.cometgui.workflow.steps.PercolatorStepTest.xml"))
        control("R-report-missing", "no surefire report for org.cometgui.workflow.steps.PercolatorStepTest",
                reports=[missing])
        empty = os.path.join(workdir, "reports-empty")
        os.makedirs(empty)
        control("R-no-reports-at-all", "no TEST-*.xml at all", reports=[empty])
        control("R-e2e-scope-needs-e2e-reports", "no surefire report for org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest",
                reports=[passing], scope="all")
    except AssertionError as broken:
        failures.append(str(broken))
        print("  HARNESS FAILURE: %s" % broken)

    print("self-test: %d control(s), %d harness failure(s)" % (controls, len(failures)))
    if failures:
        return 1
    print("the real tree:")
    status, output = _capture(check, spec, map_path, source_root, [], "ordinary", True)
    print(output, end="")
    return status


def main(argv):
    parser = argparse.ArgumentParser(description="Check the failure-path map (Phase 14 gate item 4).")
    parser.add_argument("--spec", required=True)
    parser.add_argument("--map", required=True)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--reports", nargs="*", default=[])
    parser.add_argument("--scope", choices=("ordinary", "e2e", "all"), default="all")
    parser.add_argument("--allow-partial", action="store_true")
    parser.add_argument("--self-test", metavar="WORKDIR",
                        help="damage copies under WORKDIR, require each rejected, then check the real tree")
    options = parser.parse_args(argv)
    if options.self_test:
        status = self_test(options.spec, options.map, options.source_root, options.self_test)
        print("check-failure-paths --self-test: %s" % ("PASSED" if status == 0 else "FAILED"))
        return status
    try:
        status = check(options.spec, options.map, options.source_root, options.reports, options.scope, options.allow_partial)
    except Misuse as misuse:
        print("check-failure-paths: %s" % misuse, file=sys.stderr)
        return 2
    print("check-failure-paths: %s" % ("PASSED" if status == 0 else "FAILED"))
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
