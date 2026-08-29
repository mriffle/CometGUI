"""Tests for the traceability generator, one per way the report can go wrong.

Every test builds a small synthetic project on disk -- its own specification,
phase documents, phase board, mapping file and Java test tree -- injects
exactly one defect, and asserts on the *specific* problem code and message that
defect must produce. "Did not throw" is not an assertion here, and neither is
"some problem was reported": a checker that reports the wrong reason is a
checker that will be believed when it is wrong.

The last class runs the real repository through the same code, because a
generator that only works on its own fixtures proves nothing about the project
it gates.
"""

from __future__ import annotations

import io
import contextlib
import tempfile
import unittest
from pathlib import Path

from traceability import check, generate, render
from traceability import __main__ as cli
from traceability.checks import (
    AC_NO_EVIDENCE,
    CHECK_MISSING,
    EVIDENCE_INVALID,
    HUMAN_SET_MISMATCH,
    MAP_MISSING_ID,
    MAP_UNKNOWN_ID,
    PHASE_UNKNOWN_ID,
    PLAN_NOT_PROVEN,
    PLAN_PHASE_PASSED,
    PLAN_UNKNOWN_PHASE,
    R_MULTI_OWNER,
    R_UNOWNED,
    STATUS_AUTOMATED,
    STATUS_HUMAN,
    STATUS_PARTIAL,
    STATUS_PLANNED,
    TEST_MISSING,
    counts,
    status_of,
)
from traceability.model import SourceError, ValidationFailure
from traceability.sources import (
    expand_identifiers,
    find_project_root,
    parse_phase_board,
    parse_specification,
)


# ---------------------------------------------------------------------------
# The synthetic project
# ---------------------------------------------------------------------------


def _rule(text, char="="):
    return char * len(text)


class Fixture:
    """A minimal but complete CometGUI-shaped project, written to a directory.

    Defaults are valid: :meth:`problems` on an unmodified fixture must return
    nothing. Each test mutates one attribute and asserts what breaks.
    """

    def __init__(self):
        self.rules = ["R-ALPHA-01", "R-ALPHA-02", "R-BETA-01"]
        # (identifier, criterion text, is human sign-off)
        self.criteria = [
            ("AC-ALPHA-01", "A measurable thing happens.", False),
            ("AC-ALPHA-02", "A person reviews the thing.", True),
            ("AC-ALPHA-03", "A gate script rejects the bad thing.", False),
            ("AC-BETA-01", "A later thing happens.", False),
        ]
        self.human_substitution = ".. |human| replace:: **[human]**"
        self.phases = {
            "01": {
                "title": "Alpha phase",
                "delivers": "R-ALPHA-01, R-ALPHA-02",
                "contributes": "R-BETA-01",
                "proves": "AC-ALPHA-01, AC-ALPHA-02, AC-ALPHA-03",
            },
            "02": {
                "title": "Beta phase",
                "delivers": "R-BETA-01",
                "contributes": "",
                "proves": "AC-BETA-01",
            },
        }
        self.board = {"01": "IN PROGRESS", "02": "NOT STARTED"}
        self.board_heading = "Phase board"
        self.mapping = {
            "AC-ALPHA-01": [
                {
                    "kind": "test",
                    "class": "org.cometgui.demo.DemoTest",
                    "method": "provesTheThing",
                }
            ],
            "AC-ALPHA-02": [
                {
                    "kind": "human",
                    "reference": "DECISIONS.rst -- the reviewer signs here",
                    "record": "DECISIONS.rst",
                }
            ],
            "AC-ALPHA-03": [
                {
                    "kind": "check",
                    "command": "bash scripts/ci/demo-gate.sh",
                    "file": "scripts/ci/demo-gate.sh",
                    "contains": "REJECTS-THE-BAD-THING",
                }
            ],
            "AC-BETA-01": [{"kind": "planned", "phase": "02"}],
        }
        self.test_classes = {
            "cometgui-demo/src/test/java/org/cometgui/demo/DemoTest.java": (
                "package org.cometgui.demo;\n"
                "\n"
                "class DemoTest {\n"
                "    void provesTheThing() {\n"
                "        assertEquals(1, 1);\n"
                "    }\n"
                "}\n"
            )
        }
        self.check_files = {
            "scripts/ci/demo-gate.sh": "#!/bin/sh\n# REJECTS-THE-BAD-THING\nexit 0\n"
        }
        self.write_specification = True
        self.write_status = True

    # -- rendering the documents -------------------------------------------

    def specification(self):
        title = "Synthetic specification"
        lines = [_rule(title), title, _rule(title), "", "Rules", "-----", ""]
        for identifier in self.rules:
            lines += [f"``{identifier}``", f"    Synthetic rule {identifier}.", ""]
        lines += [
            ".. _spec-acceptance:",
            "",
            "Acceptance Criteria",
            "===================",
            "",
        ]
        if self.human_substitution:
            lines += [self.human_substitution, ""]
        lines += [
            ".. list-table::",
            "   :header-rows: 1",
            "   :widths: 16 84",
            "",
            "   * - ID",
            "     - Criterion",
        ]
        for identifier, text, human in self.criteria:
            lines.append(f"   * - ``{identifier}``")
            lines.append(f"     - {text}" + (" |human|" if human else ""))
        lines += ["", ".. _spec-decisions:", "", "Decisions", "=========", "", "None.", ""]
        return "\n".join(lines)

    def phase_document(self, number):
        body = self.phases[number]
        title = f"PHASE-{number}: {body['title']}"
        lines = [_rule(title), title, _rule(title), "", f":Phase: {number}"]
        lines.append(f":Delivers: {body['delivers'] or 'no rules owned'}")
        if body["contributes"]:
            lines.append(f":Contributes to: {body['contributes']}")
        lines.append(f":Proves: {body['proves'] or 'nothing'}")
        lines += ["", "Purpose", "-------", "", "Synthetic.", ""]
        return "\n".join(lines)

    def status(self):
        title = "Synthetic status"
        lines = [
            _rule(title),
            title,
            _rule(title),
            "",
            self.board_heading,
            _rule(self.board_heading),
            "",
            ".. list-table::",
            "   :header-rows: 1",
            "   :widths: 8 40 16 36",
            "",
            "   * - Phase",
            "     - Title",
            "     - Status",
            "     - Gate evidence",
        ]
        for number, state in self.board.items():
            lines += [
                f"   * - {number}",
                f"     - {self.phases.get(number, {}).get('title', 'Unknown')}",
                f"     - {state}",
                "     - --",
            ]
        lines += ["", "Notes", "=====", "", "None.", ""]
        return "\n".join(lines)

    def mapping_toml(self):
        lines = ["[meta]", 'owner = "synthetic fixture"', ""]
        for identifier, entries in self.mapping.items():
            for entry in entries:
                lines.append(f'[[ac."{identifier}".evidence]]')
                for key, value in entry.items():
                    rendered = str(value).replace("\\", "\\\\").replace('"', '\\"')
                    lines.append(f'{key} = "{rendered}"')
                lines.append("")
            if not entries:
                lines += [f'[ac."{identifier}"]', "evidence = []", ""]
        return "\n".join(lines)

    # -- writing and checking ----------------------------------------------

    def write(self, root):
        root = Path(root)
        (root / "phases").mkdir(parents=True, exist_ok=True)
        (root / "docs").mkdir(parents=True, exist_ok=True)
        if self.write_specification:
            (root / "specification.rst").write_text(self.specification(), encoding="utf-8")
        if self.write_status:
            (root / "STATUS.rst").write_text(self.status(), encoding="utf-8")
        (root / "DECISIONS.rst").write_text("Synthetic decisions.\n", encoding="utf-8")
        for number in self.phases:
            name = self.phases[number]["title"].lower().replace(" ", "-")
            (root / "phases" / f"PHASE-{number}-{name}.rst").write_text(
                self.phase_document(number), encoding="utf-8"
            )
        (root / "docs" / "traceability-map.toml").write_text(
            self.mapping_toml(), encoding="utf-8"
        )
        for relative, content in self.test_classes.items():
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        for relative, content in self.check_files.items():
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
        return root


class FixtureCase(unittest.TestCase):
    """Base class: a fresh temporary project per test."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.fixture = Fixture()

    def run_check(self):
        self.fixture.write(self.root)
        _project, problems = check(self.root)
        return problems

    def only_problem(self, code):
        """Assert exactly one problem, with this code, and return it."""
        problems = self.run_check()
        self.assertEqual(
            [problem.code for problem in problems],
            [code],
            msg="expected exactly one %s; got %s"
            % (code, [str(problem) for problem in problems]),
        )
        return problems[0]


# ---------------------------------------------------------------------------
# The baseline: a correct map produces nothing
# ---------------------------------------------------------------------------


class CleanFixtureTests(FixtureCase):
    def test_a_correct_map_reports_no_problems(self):
        self.assertEqual([str(p) for p in self.run_check()], [])

    def test_counts_classify_every_criterion(self):
        self.fixture.write(self.root)
        project, _ = check(self.root)
        tally = counts(project)
        self.assertEqual(tally["rules"], 3)
        self.assertEqual(tally["criteria"], 4)
        self.assertEqual(tally[STATUS_AUTOMATED], 2)  # one test, one check
        self.assertEqual(tally[STATUS_HUMAN], 1)
        self.assertEqual(tally[STATUS_PLANNED], 1)
        self.assertEqual(tally[STATUS_PARTIAL], 0)
        self.assertEqual(tally["named_tests"], 1)
        self.assertEqual(tally["test_classes_in_tree"], 1)

    def test_partial_is_reported_when_a_check_and_a_plan_coexist(self):
        self.fixture.mapping["AC-BETA-01"] = [
            {
                "kind": "check",
                "command": "bash scripts/ci/demo-gate.sh",
                "file": "scripts/ci/demo-gate.sh",
                "contains": "REJECTS-THE-BAD-THING",
            },
            {"kind": "planned", "phase": "02"},
        ]
        self.assertEqual([str(p) for p in self.run_check()], [])
        project, _ = check(self.root)
        self.assertEqual(
            status_of(project.mapping["AC-BETA-01"]), STATUS_PARTIAL
        )


# ---------------------------------------------------------------------------
# Failure mode 1 and 2 -- R- ownership (R-DOC-01)
# ---------------------------------------------------------------------------


class RuleOwnershipTests(FixtureCase):
    def test_rule_with_no_owning_phase_fails(self):
        self.fixture.phases["02"]["delivers"] = ""
        problem = self.only_problem(R_UNOWNED)
        self.assertEqual(problem.identifier, "R-BETA-01")
        self.assertIn("no implementing phase", problem.message)
        self.assertIn("R-DOC-01", problem.message)

    def test_rule_owned_by_two_phases_fails(self):
        self.fixture.phases["02"]["delivers"] = "R-BETA-01, R-ALPHA-01"
        problem = self.only_problem(R_MULTI_OWNER)
        self.assertEqual(problem.identifier, "R-ALPHA-01")
        self.assertIn("owned by 2 phases", problem.message)
        self.assertIn("phase 01", problem.message)
        self.assertIn("phase 02", problem.message)

    def test_a_contributing_phase_is_not_an_owner(self):
        # Phase 01 contributes to R-BETA-01 without owning it: that is exactly
        # what R-DOC-01 permits, and it must not be reported as a second owner.
        self.assertEqual([str(p) for p in self.run_check()], [])


# ---------------------------------------------------------------------------
# Failure mode 3 -- the one PHASE-01 exit gate item 5 names
# ---------------------------------------------------------------------------


class MissingEvidenceTests(FixtureCase):
    def test_criterion_with_no_test_reference_fails(self):
        """PHASE-01 exit gate item 5: no test reference means no build."""
        self.fixture.mapping["AC-BETA-01"] = []
        problem = self.only_problem(AC_NO_EVIDENCE)
        self.assertEqual(problem.identifier, "AC-BETA-01")
        self.assertIn("no test reference and no human-sign-off mark", problem.message)
        self.assertIn("R-DOC-02", problem.message)
        self.assertIn("R-DOC-03", problem.message)

    def test_no_evidence_also_fails_the_generator(self):
        self.fixture.mapping["AC-BETA-01"] = []
        self.fixture.write(self.root)
        target = self.root / "docs" / "developer" / "traceability.rst"
        with self.assertRaises(ValidationFailure) as raised:
            generate(self.root, target)
        self.assertIn(AC_NO_EVIDENCE, raised.exception.render())
        self.assertFalse(
            target.exists(),
            "a rejected map must not leave a report behind for Sphinx to read",
        )


# ---------------------------------------------------------------------------
# Failure mode 4 -- a named test that is not there
# ---------------------------------------------------------------------------


class NamedTestTests(FixtureCase):
    def test_missing_test_class_fails(self):
        self.fixture.mapping["AC-ALPHA-01"][0]["class"] = "org.cometgui.demo.GoneTest"
        problem = self.only_problem(TEST_MISSING)
        self.assertEqual(problem.identifier, "AC-ALPHA-01")
        self.assertIn("org.cometgui.demo.GoneTest", problem.message)
        self.assertIn("cometgui-*/src/test/java", problem.message)

    def test_missing_test_method_fails_even_when_the_class_exists(self):
        self.fixture.mapping["AC-ALPHA-01"][0]["method"] = "renamedAwayLastWeek"
        problem = self.only_problem(TEST_MISSING)
        self.assertIn(
            "org.cometgui.demo.DemoTest#renamedAwayLastWeek", problem.message
        )
        self.assertIn("declares no such method", problem.message)

    def test_a_test_outside_src_test_does_not_count(self):
        self.fixture.test_classes = {
            "cometgui-demo/src/main/java/org/cometgui/demo/DemoTest.java": "class DemoTest {}\n"
        }
        problem = self.only_problem(TEST_MISSING)
        self.assertIn("org.cometgui.demo.DemoTest", problem.message)

    def test_missing_check_file_fails(self):
        self.fixture.check_files = {}
        problem = self.only_problem(CHECK_MISSING)
        self.assertEqual(problem.identifier, "AC-ALPHA-03")
        self.assertIn("scripts/ci/demo-gate.sh", problem.message)

    def test_check_file_that_lost_its_marker_fails(self):
        self.fixture.check_files["scripts/ci/demo-gate.sh"] = "#!/bin/sh\nexit 0\n"
        problem = self.only_problem(CHECK_MISSING)
        self.assertIn("REJECTS-THE-BAD-THING", problem.message)
        self.assertIn("no longer contains", problem.message)


# ---------------------------------------------------------------------------
# Failure mode 5 -- the human-sign-off set
# ---------------------------------------------------------------------------


class HumanSignOffTests(FixtureCase):
    def test_claiming_human_sign_off_the_specification_does_not_mark_fails(self):
        self.fixture.mapping["AC-BETA-01"] = [
            {
                "kind": "human",
                "reference": "someone will look at it",
                "record": "DECISIONS.rst",
            }
        ]
        problem = self.only_problem(HUMAN_SET_MISMATCH)
        self.assertEqual(problem.identifier, "AC-BETA-01")
        self.assertIn("does not mark it |human|", problem.message)

    def test_omitting_a_human_criterion_the_specification_marks_fails(self):
        self.fixture.mapping["AC-ALPHA-02"] = [{"kind": "planned", "phase": "01"}]
        problems = self.run_check()
        codes = sorted({problem.code for problem in problems})
        self.assertEqual(codes, [HUMAN_SET_MISMATCH])
        self.assertIn("offers no human-sign-off evidence", problems[0].message)

    def test_the_human_set_comes_from_the_specification_not_the_code(self):
        # Move the mark to a different criterion: the map must follow it.
        self.fixture.criteria = [
            ("AC-ALPHA-01", "A measurable thing happens.", True),
            ("AC-ALPHA-02", "A person reviews the thing.", False),
            ("AC-ALPHA-03", "A gate script rejects the bad thing.", False),
            ("AC-BETA-01", "A later thing happens.", False),
        ]
        problems = self.run_check()
        self.assertEqual(
            sorted((p.identifier, p.code) for p in problems),
            [
                ("AC-ALPHA-01", HUMAN_SET_MISMATCH),
                ("AC-ALPHA-02", HUMAN_SET_MISMATCH),
            ],
        )

    def test_a_specification_without_the_substitution_is_refused(self):
        self.fixture.human_substitution = ""
        self.fixture.write(self.root)
        with self.assertRaises(SourceError) as raised:
            check(self.root)
        self.assertIn("|human| substitution", str(raised.exception))


# ---------------------------------------------------------------------------
# Failure mode 6 -- identifiers the specification does not define, or omits
# ---------------------------------------------------------------------------


class MapCoverageTests(FixtureCase):
    def test_map_naming_an_unknown_identifier_fails(self):
        self.fixture.mapping["AC-GHOST-99"] = [{"kind": "planned", "phase": "02"}]
        problems = self.run_check()
        codes = [p.code for p in problems]
        self.assertIn(MAP_UNKNOWN_ID, codes)
        unknown = next(p for p in problems if p.code == MAP_UNKNOWN_ID)
        self.assertEqual(unknown.identifier, "AC-GHOST-99")
        self.assertIn("not defined in specification.rst", unknown.message)

    def test_map_omitting_a_defined_identifier_fails(self):
        del self.fixture.mapping["AC-BETA-01"]
        problem = self.only_problem(MAP_MISSING_ID)
        self.assertEqual(problem.identifier, "AC-BETA-01")
        self.assertIn("absent from traceability-map.toml", problem.message)

    def test_a_phase_naming_an_unknown_identifier_fails(self):
        self.fixture.phases["02"]["proves"] = "AC-BETA-01, AC-NOPE-01"
        problem = self.only_problem(PHASE_UNKNOWN_ID)
        self.assertEqual(problem.identifier, "AC-NOPE-01")
        self.assertIn(":Proves:", problem.message)
        self.assertIn("PHASE-02-beta-phase.rst", problem.message)


# ---------------------------------------------------------------------------
# "Planned" is checked, not trusted
# ---------------------------------------------------------------------------


class PlannedEvidenceTests(FixtureCase):
    def test_planned_in_a_phase_that_does_not_exist_fails(self):
        self.fixture.mapping["AC-BETA-01"] = [{"kind": "planned", "phase": "77"}]
        problem = self.only_problem(PLAN_UNKNOWN_PHASE)
        self.assertIn("phases/PHASE-77-*.rst", problem.message)

    def test_planned_in_a_phase_that_does_not_claim_it_fails(self):
        self.fixture.mapping["AC-BETA-01"] = [{"kind": "planned", "phase": "01"}]
        problem = self.only_problem(PLAN_NOT_PROVEN)
        self.assertIn(":Proves:", problem.message)
        self.assertIn("PHASE-01-alpha-phase.rst", problem.message)

    def test_planned_in_a_phase_that_already_passed_fails(self):
        self.fixture.board["02"] = "PASSED 2026-08-29"
        problem = self.only_problem(PLAN_PHASE_PASSED)
        self.assertEqual(problem.identifier, "AC-BETA-01")
        self.assertIn("already records as 'PASSED 2026-08-29'", problem.message)

    def test_a_status_document_without_a_phase_board_is_refused(self):
        self.fixture.board_heading = "Where we are"
        self.fixture.write(self.root)
        with self.assertRaises(SourceError) as raised:
            check(self.root)
        self.assertIn("no 'Phase board' section", str(raised.exception))


# ---------------------------------------------------------------------------
# Malformed mapping entries
# ---------------------------------------------------------------------------


class MalformedEntryTests(FixtureCase):
    def test_unknown_evidence_kind_fails(self):
        self.fixture.mapping["AC-BETA-01"] = [{"kind": "vibes", "phase": "02"}]
        problems = self.run_check()
        codes = sorted({p.code for p in problems})
        self.assertEqual(codes, [AC_NO_EVIDENCE, EVIDENCE_INVALID])
        invalid = next(p for p in problems if p.code == EVIDENCE_INVALID)
        self.assertIn("has kind 'vibes'", invalid.message)

    def test_a_mistyped_key_is_rejected_rather_than_ignored(self):
        self.fixture.mapping["AC-ALPHA-01"] = [
            {"kind": "test", "class": "org.cometgui.demo.DemoTest", "methd": "typo"}
        ]
        problems = self.run_check()
        invalid = next(p for p in problems if p.code == EVIDENCE_INVALID)
        self.assertIn("'methd'", invalid.message)
        self.assertIn("silently disables the check", invalid.message)

    def test_a_missing_required_key_is_rejected(self):
        self.fixture.mapping["AC-BETA-01"] = [{"kind": "planned"}]
        problems = self.run_check()
        invalid = next(p for p in problems if p.code == EVIDENCE_INVALID)
        self.assertIn("missing 'phase'", invalid.message)

    def test_a_map_with_no_entries_is_refused(self):
        self.fixture.mapping = {}
        self.fixture.write(self.root)
        with self.assertRaises(SourceError) as raised:
            check(self.root)
        self.assertIn("no [ac.*] entries", str(raised.exception))

    def test_a_missing_specification_is_refused(self):
        self.fixture.write_specification = False
        self.fixture.write(self.root)
        with self.assertRaises(SourceError) as raised:
            check(self.root)
        self.assertIn("no specification at", str(raised.exception))


# ---------------------------------------------------------------------------
# Range notation, rendering and the command line
# ---------------------------------------------------------------------------


class RangeExpansionTests(unittest.TestCase):
    def test_ranges_expand_to_every_identifier(self):
        self.assertEqual(
            expand_identifiers("R-TOOL-01..09, R-PLAT-02", "R-"),
            (
                "R-TOOL-01",
                "R-TOOL-02",
                "R-TOOL-03",
                "R-TOOL-04",
                "R-TOOL-05",
                "R-TOOL-06",
                "R-TOOL-07",
                "R-TOOL-08",
                "R-TOOL-09",
                "R-PLAT-02",
            ),
        )

    def test_ranges_keep_zero_padding_and_cross_ten(self):
        self.assertEqual(
            expand_identifiers("AC-INS-08..11", "AC-"),
            ("AC-INS-08", "AC-INS-09", "AC-INS-10", "AC-INS-11"),
        )

    def test_the_prefix_filter_separates_rules_from_criteria(self):
        text = "Foundations for AC-TST-02, AC-DOC-01 and R-DOC-03"
        self.assertEqual(expand_identifiers(text, "AC-"), ("AC-TST-02", "AC-DOC-01"))
        self.assertEqual(expand_identifiers(text, "R-"), ("R-DOC-03",))

    def test_prose_without_identifiers_expands_to_nothing(self):
        self.assertEqual(
            expand_identifiers("Evidence only -- this phase owns no ``R-`` rule", "R-"),
            (),
        )

    def test_a_descending_range_is_refused(self):
        with self.assertRaises(SourceError):
            expand_identifiers("R-TOOL-09..01", "R-")


class RenderTests(FixtureCase):
    def test_the_report_has_a_row_for_every_identifier(self):
        self.fixture.write(self.root)
        project, problems = check(self.root)
        self.assertEqual(problems, [])
        page = render(project)
        for identifier in self.fixture.rules:
            self.assertIn(f"   * - ``{identifier}``", page)
        for identifier, _text, _human in self.fixture.criteria:
            self.assertIn(f"   * - ``{identifier}``", page)
        self.assertIn(".. _dev-traceability:", page)
        self.assertIn("This page is generated", page)
        self.assertIn("01 -- Alpha phase", page)

    def test_rendering_is_deterministic(self):
        self.fixture.write(self.root)
        project, _ = check(self.root)
        self.assertEqual(render(project), render(project))

    def test_generate_writes_the_page_where_it_is_told(self):
        self.fixture.write(self.root)
        target = self.root / "elsewhere" / "traceability.rst"
        written, _project = generate(self.root, target)
        self.assertEqual(written, target)
        self.assertTrue(target.is_file())
        self.assertGreater(target.stat().st_size, 0)
        self.assertIn("Traceability report", target.read_text(encoding="utf-8"))


class CommandLineTests(FixtureCase):
    def _run(self, argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = cli.main(argv)
        return status, out.getvalue(), err.getvalue()

    def test_check_mode_passes_and_writes_nothing(self):
        self.fixture.write(self.root)
        status, out, _err = self._run(["--root", str(self.root), "--check"])
        self.assertEqual(status, 0)
        self.assertIn("3 R- rules, 4 AC- criteria", out)
        self.assertIn("map is complete", out)
        self.assertFalse((self.root / "docs" / "developer").exists())

    def test_check_mode_reports_the_problem_and_exits_one(self):
        self.fixture.mapping["AC-BETA-01"] = []
        self.fixture.write(self.root)
        status, _out, err = self._run(["--root", str(self.root), "--check"])
        self.assertEqual(status, 1)
        self.assertIn(AC_NO_EVIDENCE, err)
        self.assertIn("no test reference and no human-sign-off mark", err)

    def test_a_broken_source_exits_two(self):
        self.fixture.write_status = False
        self.fixture.write(self.root)
        status, _out, err = self._run(["--root", str(self.root), "--check"])
        self.assertEqual(status, 2)
        self.assertIn("no status document at", err)

    def test_generate_mode_writes_and_reports_the_size(self):
        self.fixture.write(self.root)
        status, out, _err = self._run(["--root", str(self.root)])
        self.assertEqual(status, 0)
        page = self.root / "docs" / "developer" / "traceability.rst"
        self.assertTrue(page.is_file())
        self.assertIn(f"wrote {page}", out)


# ---------------------------------------------------------------------------
# The real repository
# ---------------------------------------------------------------------------


class RealProjectTests(unittest.TestCase):
    """The fixtures prove the checker works; this proves it works *here*."""

    @classmethod
    def setUpClass(cls):
        cls.root = find_project_root(Path(__file__).resolve().parent)
        cls.project, cls.problems = check(cls.root)

    def test_the_real_map_validates(self):
        self.assertEqual([str(problem) for problem in self.problems], [])

    def test_the_human_set_is_the_eight_the_specification_marks(self):
        self.assertEqual(
            sorted(self.project.spec_human()),
            [
                "AC-REL-02",
                "AC-REL-03",
                "AC-UX-01",
                "AC-UX-02",
                "AC-UX-03",
                "AC-UX-04",
                "AC-UX-05",
                "AC-UX-06",
            ],
        )

    def test_every_rule_has_exactly_one_owner(self):
        owners = {}
        for phase in self.project.phases:
            for identifier in phase.delivers:
                owners.setdefault(identifier, []).append(phase.number)
        for identifier in self.project.rules:
            self.assertEqual(
                len(owners.get(identifier, [])),
                1,
                msg=f"{identifier} is owned by {owners.get(identifier, [])}",
            )

    def test_the_phase_board_covers_every_phase_document(self):
        for phase in self.project.phases:
            self.assertIn(
                phase.number,
                self.project.phase_status,
                msg=f"phase {phase.number} has a document but no phase board row",
            )

    def test_the_real_specification_still_parses_as_expected(self):
        rules, criteria = parse_specification(self.root / "specification.rst")
        self.assertGreater(len(rules), 50)
        self.assertGreater(len(criteria), 50)
        self.assertEqual(len(set(rules)), len(rules))
        self.assertIn("R-DOC-03", rules)

    def test_the_real_phase_board_parses(self):
        board = parse_phase_board(self.root / "STATUS.rst")
        self.assertIn("01", board)
        self.assertTrue(all(value for value in board.values()))

    def test_the_real_report_renders_a_row_per_identifier(self):
        page = render(self.project)
        rule_rows = page.count("\n   * - ``R-")
        criterion_rows = page.count("\n   * - ``AC-")
        self.assertEqual(rule_rows, len(self.project.rules))
        self.assertEqual(criterion_rows, len(self.project.criteria))


if __name__ == "__main__":
    unittest.main()
