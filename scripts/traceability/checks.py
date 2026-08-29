"""The validation rules. Every finding here is fatal.

``R-DOC-03``: *"An identifier with no implementing phase, or an ``AC-`` with no
test and no human-sign-off mark, is a documentation build failure."*

There is deliberately no warning tier. A warning is where an incomplete map
would come to live permanently, and the one thing this report exists to prevent
is a traceability map that has quietly stopped being true.
"""

from __future__ import annotations

import re

from .model import Problem

# Codes are part of the interface: the unit tests and the falsifiability
# demonstrations assert on them.
R_UNOWNED = "R-UNOWNED"
R_MULTI_OWNER = "R-MULTI-OWNER"
AC_NO_EVIDENCE = "AC-NO-EVIDENCE"
TEST_MISSING = "TEST-MISSING"
CHECK_MISSING = "CHECK-MISSING"
HUMAN_SET_MISMATCH = "HUMAN-SET-MISMATCH"
MAP_UNKNOWN_ID = "MAP-UNKNOWN-ID"
MAP_MISSING_ID = "MAP-MISSING-ID"
PLAN_UNKNOWN_PHASE = "PLAN-UNKNOWN-PHASE"
PLAN_NOT_PROVEN = "PLAN-NOT-PROVEN"
PLAN_PHASE_PASSED = "PLAN-PHASE-PASSED"
PHASE_UNKNOWN_ID = "PHASE-UNKNOWN-ID"
EVIDENCE_INVALID = "EVIDENCE-INVALID"


def _owners(project):
    owners = {}
    for phase in project.phases:
        for identifier in phase.delivers:
            owners.setdefault(identifier, []).append(phase)
    return owners


def check_rules(project):
    """``R-DOC-01``: exactly one owning phase per ``R-`` rule."""
    problems = []
    owners = _owners(project)
    for identifier in project.rules:
        owning = owners.get(identifier, [])
        if not owning:
            problems.append(
                Problem(
                    identifier,
                    R_UNOWNED,
                    "no implementing phase. R-DOC-01 requires exactly one owning "
                    "phase, named in that phase's :Delivers: field; no phase "
                    "document in phases/ names this rule.",
                )
            )
        elif len(owning) > 1:
            named = ", ".join(f"phase {phase.number}" for phase in owning)
            problems.append(
                Problem(
                    identifier,
                    R_MULTI_OWNER,
                    f"owned by {len(owning)} phases ({named}). R-DOC-01 allows "
                    "exactly one owner; a phase implementing part of a rule it "
                    "does not own belongs under :Contributes to:.",
                )
            )
    return problems


def check_phase_identifiers(project):
    """Phase documents may only name identifiers the specification defines."""
    problems = []
    rules, criteria = project.rule_set, project.criterion_set
    for phase in project.phases:
        for field_name, identifiers, known in (
            (":Delivers:", phase.delivers, rules),
            (":Contributes to:", phase.contributes, rules),
            (":Proves:", phase.proves, criteria),
        ):
            for identifier in identifiers:
                if identifier not in known:
                    problems.append(
                        Problem(
                            identifier,
                            PHASE_UNKNOWN_ID,
                            f"named in {field_name} of {phase.path.name} but not "
                            "defined in specification.rst.",
                        )
                    )
    return problems


def check_map_coverage(project, invalid_entries):
    """The map must name every ``AC-`` the specification defines, and no others."""
    problems = []
    for identifier, message in invalid_entries:
        problems.append(Problem(identifier, EVIDENCE_INVALID, message + "."))
    criteria = project.criterion_set
    for identifier in sorted(set(project.mapping) - criteria):
        problems.append(
            Problem(
                identifier,
                MAP_UNKNOWN_ID,
                f"named in {project.map_path.name} but not defined in "
                "specification.rst. Either the specification dropped it or the "
                "map has a typo; both make the report untrue.",
            )
        )
    for identifier in sorted(criteria - set(project.mapping)):
        problems.append(
            Problem(
                identifier,
                MAP_MISSING_ID,
                f"defined in specification.rst but absent from "
                f"{project.map_path.name}. Every acceptance criterion must be "
                "mapped; an unmapped one has no test reference and no "
                "human-sign-off mark.",
            )
        )
    return problems


def check_evidence(project):
    """Every criterion needs evidence, and every evidence entry must verify."""
    problems = []
    for criterion in project.criteria:
        evidence = project.mapping.get(criterion.identifier)
        if evidence is None:
            continue  # already reported by check_map_coverage
        if not evidence:
            problems.append(
                Problem(
                    criterion.identifier,
                    AC_NO_EVIDENCE,
                    "no test reference and no human-sign-off mark. R-DOC-02 "
                    "requires every AC- to name at least one automated test or "
                    "be explicitly marked as requiring human sign-off, and "
                    "R-DOC-03 makes the omission a documentation build failure.",
                )
            )
            continue
        for item in evidence:
            problems.extend(_check_one(project, criterion, item))
    return problems


def _check_one(project, criterion, item):
    if item.kind == "test":
        return _check_test(project, criterion, item)
    if item.kind == "check":
        return _check_gate(project, criterion, item)
    if item.kind == "planned":
        return _check_planned(project, criterion, item)
    if item.kind == "human":
        return _check_human_record(project, criterion, item)
    return []


def _check_test(project, criterion, item):
    """A named test must actually exist in the source tree."""
    class_name = item.data["class"]
    source = project.tests.get(class_name)
    if source is None:
        return [
            Problem(
                criterion.identifier,
                TEST_MISSING,
                f"names the test class {class_name}, which does not exist under "
                "cometgui-*/src/test/java. A map entry naming a test that was "
                "renamed or never written is how this report rots.",
            )
        ]
    method = item.data.get("method")
    if method:
        text = source.read_text(encoding="utf-8")
        if not re.search(r"\b%s\s*\(" % re.escape(method), text):
            return [
                Problem(
                    criterion.identifier,
                    TEST_MISSING,
                    f"names the test method {class_name}#{method}; the class "
                    f"exists at {source.relative_to(project.root)} but declares "
                    "no such method.",
                )
            ]
    return []


def _check_gate(project, criterion, item):
    """A named automated check must exist and still contain the marker named."""
    relative = item.data["file"]
    target = project.root / relative
    if not target.is_file():
        return [
            Problem(
                criterion.identifier,
                CHECK_MISSING,
                f"names the automated check {item.data['command']!r}, whose file "
                f"{relative} does not exist.",
            )
        ]
    marker = item.data["contains"]
    if marker not in target.read_text(encoding="utf-8", errors="replace"):
        return [
            Problem(
                criterion.identifier,
                CHECK_MISSING,
                f"names the automated check {item.data['command']!r}; "
                f"{relative} exists but no longer contains {marker!r}, so the "
                "check the map claims is not the check the file performs.",
            )
        ]
    return []


def _check_planned(project, criterion, item):
    """"Planned in phase nn" is checked, not taken on trust."""
    number = item.data["phase"]
    phase = project.phase(number)
    if phase is None:
        return [
            Problem(
                criterion.identifier,
                PLAN_UNKNOWN_PHASE,
                f"is planned in phase {number}, but there is no "
                f"phases/PHASE-{number}-*.rst document.",
            )
        ]
    problems = []
    if criterion.identifier not in phase.proves:
        problems.append(
            Problem(
                criterion.identifier,
                PLAN_NOT_PROVEN,
                f"is planned in phase {number}, but the :Proves: field of "
                f"{phase.path.name} does not claim it. Either the map or the "
                "phase document is wrong.",
            )
        )
    status = project.phase_status.get(number, "")
    if status.upper().startswith("PASSED"):
        problems.append(
            Problem(
                criterion.identifier,
                PLAN_PHASE_PASSED,
                f"is planned in phase {number}, which the STATUS.rst phase "
                f"board already records as {status!r}. A criterion cannot still "
                "be waiting for a phase that has passed its gate.",
            )
        )
    return problems


def _check_human_record(project, criterion, item):
    record = item.data["record"]
    if not (project.root / record).is_file():
        return [
            Problem(
                criterion.identifier,
                CHECK_MISSING,
                f"records its human sign-off in {record}, which does not exist.",
            )
        ]
    return []


def check_human_set(project):
    """The map's human-sign-off set must equal the specification's |human| set."""
    problems = []
    spec = project.spec_human()
    claimed = project.map_human()
    for identifier in sorted(claimed - spec):
        problems.append(
            Problem(
                identifier,
                HUMAN_SET_MISMATCH,
                f"is mapped as requiring human sign-off in "
                f"{project.map_path.name}, but specification.rst does not mark "
                "it |human|. Human sign-off is the specification's call, not "
                "the map's -- this is how an automatable criterion escapes "
                "being tested.",
            )
        )
    for identifier in sorted(spec - claimed):
        problems.append(
            Problem(
                identifier,
                HUMAN_SET_MISMATCH,
                f"is marked |human| in specification.rst, but "
                f"{project.map_path.name} offers no human-sign-off evidence for "
                "it.",
            )
        )
    return problems


def validate(project, invalid_entries=()):
    """Run every rule and return the findings, ordered by identifier then code."""
    problems = []
    problems.extend(check_rules(project))
    problems.extend(check_phase_identifiers(project))
    problems.extend(check_map_coverage(project, invalid_entries))
    problems.extend(check_evidence(project))
    problems.extend(check_human_set(project))
    return sorted(problems)


# ---------------------------------------------------------------------------
# Derived status, shared by the renderer and the summary counts
# ---------------------------------------------------------------------------

STATUS_HUMAN = "human sign-off"
STATUS_AUTOMATED = "automated"
STATUS_PARTIAL = "partial"
STATUS_PLANNED = "planned"

STATUS_ORDER = (STATUS_AUTOMATED, STATUS_PARTIAL, STATUS_PLANNED, STATUS_HUMAN)


def status_of(evidence):
    """Classify one criterion from its evidence entries.

    ``partial`` is the honest answer for a criterion that has an automated
    check in the tree today *and* a later phase that finishes the job -- the
    documentation build is strict now, but Read the Docs is not yet building
    anything, for example.
    """
    kinds = {item.kind for item in evidence}
    if "human" in kinds:
        return STATUS_HUMAN
    automated = bool(kinds & {"test", "check"})
    planned = "planned" in kinds
    if automated and planned:
        return STATUS_PARTIAL
    if automated:
        return STATUS_AUTOMATED
    return STATUS_PLANNED


def counts(project):
    """The summary numbers the generated page and the CI wrapper both report."""
    tally = {status: 0 for status in STATUS_ORDER}
    named_tests = set()
    named_checks = set()
    for criterion in project.criteria:
        evidence = project.mapping.get(criterion.identifier, [])
        tally[status_of(evidence)] += 1
        for item in evidence:
            if item.kind == "test":
                named_tests.add(item.summary())
            elif item.kind == "check":
                named_checks.add(item.data["file"])
    return {
        "rules": len(project.rules),
        "criteria": len(project.criteria),
        "phases": len(project.phases),
        "test_classes_in_tree": len(project.tests),
        "named_tests": len(named_tests),
        "named_checks": len(named_checks),
        **tally,
    }
