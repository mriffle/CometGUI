"""Render the validated traceability map as the reStructuredText report page.

The page is generated during the documentation build (``R-DOC-03``) and is
gitignored. It is written to be read, not merely to exist: someone asking "who
owns R-PERC-04?" or "what proves AC-LL-03, and when?" should get the answer
here without opening a phase document.

Everything rendered is deterministic -- no timestamps, no host paths -- so that
two builds of the same commit produce byte-identical output.
"""

from __future__ import annotations

from .checks import (
    STATUS_AUTOMATED,
    STATUS_HUMAN,
    STATUS_PARTIAL,
    STATUS_PLANNED,
    counts,
    status_of,
)

GENERATOR = "scripts/traceability"
CHECK_COMMAND = "bash scripts/ci/traceability.sh"


def _title(text, char):
    return [text, char * len(text), ""]


def _overlined(text, char):
    rule = char * len(text)
    return [rule, text, rule, ""]


def _cell(lines, text):
    """First cell of a list-table row."""
    lines.append(f"   * - {text}")


def _more(lines, text):
    lines.append(f"     - {text}")


def _block(lines, entries):
    """A following cell holding a line block, one line per entry."""
    if not entries:
        _more(lines, "--")
        return
    _more(lines, f"| {entries[0]}")
    for entry in entries[1:]:
        lines.append(f"       | {entry}")


def _evidence_lines(evidence):
    rendered = []
    for item in evidence:
        if item.kind == "test":
            rendered.append(f"test: ``{item.summary()}``")
        elif item.kind == "check":
            rendered.append(f"check: ``{item.summary()}``")
        elif item.kind == "human":
            rendered.append(f"human sign-off: {item.summary()}")
        elif item.kind == "planned":
            rendered.append(f"planned: {item.summary()}")
        if item.note:
            rendered.append(f"  {item.note}")
    return rendered


def render(project):
    """Return the complete page as a string."""
    tally = counts(project)
    owners = {}
    contributors = {}
    for phase in project.phases:
        for identifier in phase.delivers:
            owners[identifier] = phase
        for identifier in phase.contributes:
            contributors.setdefault(identifier, []).append(phase)

    out = [".. _dev-traceability:", ""]
    out += _overlined("Traceability report", "=")

    out += [
        ".. warning::",
        "",
        "   **This page is generated. Hand edits will not survive.**",
        "",
        f"   It is written by ``{GENERATOR}`` during every documentation build,",
        "   from ``specification.rst`` (which identifiers exist and which need",
        "   human sign-off), the ``phases/PHASE-nn-*.rst`` documents (which phase",
        "   owns which rule and proves which criterion), the *Phase board* in",
        "   ``STATUS.rst`` (what has already run) and the checked-in mapping file",
        "   ``docs/traceability-map.toml`` (the evidence for each criterion).",
        "",
        "   To change what appears here, change one of those. To check the map",
        f"   without building the documentation, run ``{CHECK_COMMAND}``.",
        "",
        "``R-DOC-03`` makes this report a gate rather than a summary: an ``R-`` with",
        "no implementing phase, or an ``AC-`` with no test reference and no",
        "human-sign-off mark, fails the documentation build. So does a mapping entry",
        "naming a test that does not exist, a criterion the specification does not",
        "define, or a human-sign-off claim the specification does not make.",
        "",
    ]

    # -- Summary -------------------------------------------------------------
    out += _title("Summary", "=")
    out += [
        ".. list-table::",
        "   :header-rows: 1",
        "   :widths: 62 12",
        "",
        "   * - Measure",
        "     - Count",
    ]
    rows = [
        ("Requirement rules (``R-``) defined by the specification", tally["rules"]),
        ("Rules with exactly one owning phase", tally["rules"]),
        ("Acceptance criteria (``AC-``) defined by the specification", tally["criteria"]),
        (f"Criteria whose evidence is automated and complete today ({STATUS_AUTOMATED})",
         tally[STATUS_AUTOMATED]),
        (f"Criteria with an automated check today, finished by a later phase ({STATUS_PARTIAL})",
         tally[STATUS_PARTIAL]),
        (f"Criteria waiting on a phase that has not run ({STATUS_PLANNED})",
         tally[STATUS_PLANNED]),
        (f"Criteria requiring human sign-off ({STATUS_HUMAN})", tally[STATUS_HUMAN]),
        ("Distinct JUnit tests named as evidence", tally["named_tests"]),
        ("Distinct automated check files named as evidence", tally["named_checks"]),
        ("Test classes present under ``cometgui-*/src/test/java``",
         tally["test_classes_in_tree"]),
        ("Phase documents read", tally["phases"]),
    ]
    for label, value in rows:
        _cell(out, label)
        _more(out, str(value))
    out.append("")
    out += [
        "Every rule has exactly one owning phase and every criterion has evidence;",
        "that is what makes this build pass. It is *not* a claim that the product",
        "is finished. ``planned`` means the criterion's owning phase has not run",
        "yet and the entry names the phase that will run it -- checked against that",
        "phase's ``:Proves:`` field and against the phase board, so it cannot point",
        "at a phase that never claimed the criterion or has already passed.",
        "",
    ]

    # -- Phases --------------------------------------------------------------
    out += _title("Phases", "=")
    out += [
        ".. list-table::",
        "   :header-rows: 1",
        "   :widths: 8 40 18 12 12",
        "",
        "   * - Phase",
        "     - Title",
        "     - Status",
        "     - Rules owned",
        "     - Criteria proved",
    ]
    for phase in project.phases:
        _cell(out, phase.number)
        _more(out, phase.title or "--")
        _more(out, project.phase_status.get(phase.number, "not on the phase board"))
        _more(out, str(len(phase.delivers)))
        _more(out, str(len(phase.proves)))
    out.append("")

    # -- Rules ---------------------------------------------------------------
    out += _title("Requirement rules", "=")
    out += [
        "Every ``R-`` rule in ``specification.rst``, with the phase that owns it",
        "(``:Delivers:``) and any phase implementing part of it (``:Contributes",
        "to:``). ``R-DOC-01`` requires exactly one owner.",
        "",
        ".. list-table::",
        "   :header-rows: 1",
        "   :widths: 18 46 36",
        "",
        "   * - Rule",
        "     - Owning phase",
        "     - Also implemented in",
    ]
    for identifier in project.rules:
        owner = owners.get(identifier)
        also = contributors.get(identifier, [])
        _cell(out, f"``{identifier}``")
        _more(out, f"{owner.number} -- {owner.title}" if owner else "**none**")
        _more(out, ", ".join(phase.number for phase in also) if also else "--")
    out.append("")

    # -- Criteria ------------------------------------------------------------
    out += _title("Acceptance criteria", "=")
    out += [
        "Every ``AC-`` criterion in ``specification.rst``, its evidence, and the",
        "phases whose ``:Proves:`` field claims it. Criterion text is reproduced",
        "from the specification, which remains authoritative.",
        "",
        ".. list-table::",
        "   :header-rows: 1",
        "   :widths: 12 40 14 34",
        "",
        "   * - Criterion",
        "     - What it requires",
        "     - Status",
        "     - Evidence",
    ]
    proving = {}
    for phase in project.phases:
        for identifier in phase.proves:
            proving.setdefault(identifier, []).append(phase.number)
    for criterion in project.criteria:
        evidence = project.mapping.get(criterion.identifier, [])
        phases = proving.get(criterion.identifier, [])
        _cell(out, f"``{criterion.identifier}``")
        _more(out, criterion.text or "--")
        _more(out, status_of(evidence))
        entries = _evidence_lines(evidence)
        if phases:
            entries.append("proving phases: " + ", ".join(phases))
        _block(out, entries)
    out.append("")

    # -- How to change it ----------------------------------------------------
    out += _title("Changing the map", "=")
    out += [
        "``docs/traceability-map.toml`` holds one entry per acceptance criterion::",
        "",
        '    [[ac."AC-DOC-02".evidence]]',
        '    kind = "check"',
        '    command = "bash scripts/ci/traceability.sh"',
        '    file = "scripts/ci/traceability.sh"',
        '    contains = "--check"',
        "",
        "Four kinds of evidence are accepted, and each is verified rather than",
        "believed:",
        "",
        "``test``",
        "    a JUnit test, named by fully-qualified ``class`` and optionally",
        "    ``method``. The class must exist under ``cometgui-*/src/test/java``",
        "    and the method must be declared in it.",
        "",
        "``check``",
        "    an automated check that is not a JUnit test -- a gate script. The",
        "    ``file`` must exist and must still contain the ``contains`` marker,",
        "    so that renaming the check inside the script is caught.",
        "",
        "``human``",
        "    a criterion the specification marks ``[human]``. The map's human set",
        "    is compared with the specification's in both directions: claiming",
        "    human sign-off for a criterion the specification does not mark is a",
        "    failure, and so is omitting one it does.",
        "",
        "``planned``",
        "    the criterion's phase has not run. The named phase must exist, its",
        "    ``:Proves:`` field must claim the criterion, and the ``STATUS.rst``",
        "    phase board must not already record that phase as ``PASSED``.",
        "",
        f"Run ``{CHECK_COMMAND}`` after editing. It validates the map, runs the",
        "generator's own unit tests, and writes nothing into the documentation",
        "tree.",
        "",
    ]

    return "\n".join(out).rstrip("\n") + "\n"
