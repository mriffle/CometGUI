"""Data model and error types for the CometGUI traceability report.

The report exists because of ``R-DOC-03``: an identifier with no implementing
phase, or an ``AC-`` with no test and no human-sign-off mark, is a
documentation build failure. Everything here is shaped by that sentence -- the
model carries enough to *prove* a mapping is complete, not merely to render it.

Standard library only. The project virtualenv holds Sphinx and nothing else,
and Read the Docs installs ``docs/requirements.txt``; a traceability report
that needed a dependency would be a report that stops running.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

# The four kinds of evidence a mapping entry may offer for an AC- criterion.
#
#   test     a named JUnit test in cometgui-*/src/test -- verified to exist
#   check    a named automated check that is not a JUnit test (a gate script)
#            -- verified to exist and to still contain the marker named
#   human    a criterion the specification marks |human|; cross-checked against
#            the specification in both directions
#   planned  the criterion's phase has not run yet -- verified against the
#            phase's :Proves: field and against the phase board in STATUS.rst
EVIDENCE_KINDS = ("test", "check", "human", "planned")

# Required and optional keys per kind. Unknown keys are rejected: a typo such as
# `clas = "..."` would otherwise silently disable the verification that entry
# exists to provide, which is exactly the rot this report is here to catch.
EVIDENCE_SCHEMA = {
    "test": (("class",), ("method", "note")),
    "check": (("command", "file", "contains"), ("note",)),
    "human": (("reference", "record"), ("note",)),
    "planned": (("phase",), ("note",)),
}


class TraceabilityError(Exception):
    """Base class for every failure this package reports."""


class SourceError(TraceabilityError):
    """A source document is missing, or is not in the shape this tool parses.

    Raised rather than reported as a :class:`Problem` because a source that
    cannot be read produces no findings at all -- reporting "no problems" would
    be the worst possible answer.
    """


class ValidationFailure(TraceabilityError):
    """The traceability map or the phase documents did not validate."""

    def __init__(self, problems):
        self.problems = list(problems)
        super().__init__(self.render())

    def render(self) -> str:
        lines = [
            f"traceability: {len(self.problems)} problem(s) found; "
            "the traceability report is not complete and the documentation "
            "build must fail (R-DOC-03).",
            "",
        ]
        lines.extend(f"  {problem}" for problem in self.problems)
        return "\n".join(lines)


@dataclass(frozen=True, order=True)
class Problem:
    """One reason the traceability map cannot be trusted.

    ``code`` is stable and greppable on purpose: the falsifiability
    demonstrations and the unit tests assert on it, so renaming one is a
    visible change rather than a silently weakened check.
    """

    identifier: str
    code: str
    message: str

    def __str__(self) -> str:
        return f"[{self.code}] {self.identifier}: {self.message}"


@dataclass(frozen=True)
class Evidence:
    """One evidence entry for one acceptance criterion."""

    kind: str
    data: dict

    def get(self, key, default=None):
        return self.data.get(key, default)

    @property
    def note(self):
        return self.data.get("note")

    def summary(self) -> str:
        """A one-line human description, used in the generated table."""
        if self.kind == "test":
            method = self.data.get("method")
            name = self.data["class"]
            return f"{name}#{method}" if method else name
        if self.kind == "check":
            return self.data["command"]
        if self.kind == "human":
            return self.data["reference"]
        if self.kind == "planned":
            return f"phase {self.data['phase']}"
        return self.kind


@dataclass(frozen=True)
class Criterion:
    """An ``AC-`` acceptance criterion as the specification defines it."""

    identifier: str
    text: str
    human: bool


@dataclass(frozen=True)
class Phase:
    """One ``phases/PHASE-nn-*.rst`` document, reduced to its traceability fields."""

    number: str
    title: str
    path: Path
    delivers: tuple
    contributes: tuple
    proves: tuple


@dataclass
class Project:
    """Everything the checker and the renderer need, already parsed."""

    root: Path
    rules: tuple                      # R- identifiers, specification order
    criteria: tuple                   # Criterion, specification order
    phases: tuple                     # Phase, ascending number
    phase_status: dict                # phase number -> STATUS.rst phase board status
    mapping: dict                     # AC- identifier -> list[Evidence]
    map_path: Path
    tests: dict = field(default_factory=dict)   # fully-qualified test class -> Path

    @property
    def rule_set(self):
        return set(self.rules)

    @property
    def criterion_set(self):
        return {criterion.identifier for criterion in self.criteria}

    def phase(self, number):
        for candidate in self.phases:
            if candidate.number == number:
                return candidate
        return None

    def spec_human(self):
        return {c.identifier for c in self.criteria if c.human}

    def map_human(self):
        return {
            identifier
            for identifier, evidence in self.mapping.items()
            if any(item.kind == "human" for item in evidence)
        }
