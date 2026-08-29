"""Parsers for the four sources of truth behind the traceability report.

Nothing here invents information. Each parser reads one document and refuses to
guess:

``specification.rst``
    authoritative for which ``R-`` and ``AC-`` identifiers exist, and for which
    criteria are marked |human|. The human set is *derived* from the document,
    never hard-coded, so that adding or removing a human criterion in the
    specification is picked up rather than silently disagreed with.

``phases/PHASE-nn-*.rst``
    authoritative for ``R-`` ownership (``:Delivers:``), partial implementation
    (``:Contributes to:``) and which phase proves which ``AC-``
    (``:Proves:``). Range notation (``R-TOOL-01..09``) is expanded.

``STATUS.rst``
    authoritative for where the project actually is. Its *Phase board* decides
    whether a "planned in phase nn" mapping entry is still legitimate.

``docs/traceability-map.toml``
    the checked-in mapping file ``R-DOC-03`` allows, holding the ``AC-``
    evidence.

A parser that cannot find what it expects raises :class:`SourceError`. It never
returns an empty result and lets the caller conclude that all is well.
"""

from __future__ import annotations

import re
import tomllib
from pathlib import Path

from .model import (
    EVIDENCE_KINDS,
    EVIDENCE_SCHEMA,
    Criterion,
    Evidence,
    Phase,
    Project,
    SourceError,
)

# ---------------------------------------------------------------------------
# Identifier handling
# ---------------------------------------------------------------------------

# R-TOOL-01, AC-INS-10, and the range form R-TOOL-01..09 / AC-INS-01..10 that
# the phase documents use. The range is expanded to every identifier in it,
# keeping the zero padding of the first endpoint.
_ID_OR_RANGE = re.compile(r"\b((?:R|AC)-[A-Z]+)-(\d+)(?:\s*\.\.\s*(\d+))?\b")

# A definition-list term that *defines* an R- rule: the identifier alone on its
# line. A reference inside a table cell or a sentence never looks like this.
_RULE_TERM = re.compile(r"^(\s*)``(R-[A-Z]+-\d+)``\s*$")

_HUMAN_SUBSTITUTION = re.compile(r"^\.\.\s+\|human\|\s+replace::", re.MULTILINE)

_SECTION_TARGET = re.compile(r"^\.\. _[A-Za-z0-9_-]+:\s*$")

_HEADING_UNDERLINE = re.compile(r'^[=\-~^"\'`#*+_:.]{3,}\s*$')


def expand_identifiers(text, prefix=None):
    """Expand a phase field body into a de-duplicated list of identifiers.

    ``R-TOOL-01..09`` becomes the nine identifiers it stands for. ``prefix``
    filters the result to ``"R-"`` or ``"AC-"``.
    """
    found = []
    for match in _ID_OR_RANGE.finditer(text or ""):
        area, first, last = match.group(1), match.group(2), match.group(3)
        if last is None:
            found.append(f"{area}-{first}")
            continue
        width = len(first)
        start, end = int(first), int(last)
        if end < start:
            raise SourceError(
                f"traceability: descending identifier range {match.group(0)!r}"
            )
        for number in range(start, end + 1):
            found.append(f"{area}-{number:0{width}d}")
    if prefix is not None:
        found = [identifier for identifier in found if identifier.startswith(prefix)]
    seen = {}
    for identifier in found:
        seen.setdefault(identifier, None)
    return tuple(seen)


# ---------------------------------------------------------------------------
# specification.rst
# ---------------------------------------------------------------------------


def _list_table_rows(lines, first_cell_pattern):
    """Yield ``(first_cell_match, [cell_text, ...])`` for list-table rows.

    Written for the two-space-indented ``* - `` / ``  - `` form the project's
    documents use throughout. Continuation lines are joined with a space.
    """
    row_start = re.compile(r"^(\s*)\* - (.*)$")
    index = 0
    while index < len(lines):
        start = row_start.match(lines[index])
        if not start:
            index += 1
            continue
        indent = len(start.group(1))
        cell_marker = re.compile(r"^\s{%d}- (.*)$" % (indent + 2))
        cells = [start.group(2).strip()]
        index += 1
        while index < len(lines):
            line = lines[index]
            if row_start.match(line):
                break
            following = cell_marker.match(line)
            if following:
                cells.append(following.group(1).strip())
                index += 1
                continue
            if line.strip() and line.startswith(" " * (indent + 4)):
                cells[-1] = (cells[-1] + " " + line.strip()).strip()
                index += 1
                continue
            if not line.strip():
                index += 1
                continue
            break
        match = first_cell_pattern.match(cells[0])
        if match:
            yield match, cells


def parse_specification(path):
    """Return ``(rules, criteria)`` from ``specification.rst``."""
    path = Path(path)
    if not path.is_file():
        raise SourceError(f"traceability: no specification at {path}")
    text = path.read_text(encoding="utf-8")
    lines = text.split("\n")

    # -- R- rules: definition-list terms, in document order ------------------
    rules = []
    for number, line in enumerate(lines):
        term = _RULE_TERM.match(line)
        if not term:
            continue
        indent = len(term.group(1))
        body = None
        for candidate in lines[number + 1:]:
            if candidate.strip():
                body = candidate
                break
        if body is None or len(body) - len(body.lstrip()) <= indent:
            # An identifier alone on a line with no indented body under it is a
            # reference, not a definition.
            continue
        rules.append(term.group(2))
    duplicates = sorted({r for r in rules if rules.count(r) > 1})
    if duplicates:
        raise SourceError(
            "traceability: specification defines these R- rules more than once: "
            + ", ".join(duplicates)
        )
    if not rules:
        raise SourceError(
            f"traceability: found no R- rule definitions in {path}; the parser and "
            "the specification have diverged -- refusing to report an empty map"
        )

    # -- AC- criteria: the Acceptance Criteria section's list-tables ---------
    if not _HUMAN_SUBSTITUTION.search(text):
        raise SourceError(
            f"traceability: {path} no longer defines the |human| substitution. "
            "The human-sign-off set is derived from it, so without it every "
            "criterion would silently look automatable."
        )
    try:
        start = next(
            index
            for index, line in enumerate(lines)
            if line.strip() == ".. _spec-acceptance:"
        )
    except StopIteration:
        raise SourceError(
            f"traceability: {path} has no '.. _spec-acceptance:' target; the "
            "acceptance criteria section cannot be located"
        ) from None
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if _SECTION_TARGET.match(lines[index]):
            end = index
            break
    section = lines[start:end]

    criteria = []
    seen = set()
    pattern = re.compile(r"^``(AC-[A-Z]+-\d+)``$")
    for match, cells in _list_table_rows(section, pattern):
        identifier = match.group(1)
        if identifier in seen:
            raise SourceError(
                f"traceability: {path} defines {identifier} more than once"
            )
        seen.add(identifier)
        body = cells[1] if len(cells) > 1 else ""
        human = "|human|" in body
        text_only = body.replace("|human|", "").strip()
        text_only = re.sub(r"\s+", " ", text_only)
        criteria.append(Criterion(identifier, text_only, human))

    if not criteria:
        raise SourceError(
            f"traceability: found no AC- criteria in the acceptance section of {path}"
        )
    if not any(criterion.human for criterion in criteria):
        raise SourceError(
            f"traceability: no criterion in {path} is marked |human|. The "
            "specification marks eight; a parser that finds none has stopped "
            "reading the document correctly."
        )

    # Every AC- referenced anywhere in the specification must be defined in the
    # acceptance section. A criterion quoted in prose but never defined is a
    # hole in the map that would otherwise never be noticed.
    referenced = set(re.findall(r"``(AC-[A-Z]+-\d+)``", text))
    undefined = sorted(referenced - seen)
    if undefined:
        raise SourceError(
            "traceability: these AC- identifiers are referenced in "
            f"{path} but not defined in its acceptance criteria tables: "
            + ", ".join(undefined)
        )

    return tuple(rules), tuple(criteria)


# ---------------------------------------------------------------------------
# phases/PHASE-nn-*.rst
# ---------------------------------------------------------------------------

_FIELD = re.compile(r"^:([^:]+):\s*(.*)$")
_PHASE_TITLE = re.compile(r"^PHASE-(\d+):\s*(.+?)\s*$")


def _phase_fields(lines):
    """Read the RST field list at the top of a phase document."""
    fields = {}
    current = None
    seen_field = False
    for line in lines:
        if _HEADING_UNDERLINE.match(line):
            if seen_field:
                break
            current = None
            continue
        match = _FIELD.match(line)
        if match:
            current = match.group(1).strip()
            fields[current] = match.group(2).strip()
            seen_field = True
            continue
        if current is not None and line.startswith(" ") and line.strip():
            fields[current] = (fields[current] + " " + line.strip()).strip()
            continue
        if not line.strip():
            current = None
    return fields


def parse_phases(phases_dir):
    """Return the phase documents, ascending by number."""
    phases_dir = Path(phases_dir)
    if not phases_dir.is_dir():
        raise SourceError(f"traceability: no phases directory at {phases_dir}")
    found = sorted(phases_dir.glob("PHASE-*.rst"))
    if not found:
        raise SourceError(
            f"traceability: no PHASE-*.rst documents under {phases_dir}"
        )
    phases = []
    for path in found:
        lines = path.read_text(encoding="utf-8").split("\n")
        title = ""
        number = None
        for line in lines[:12]:
            heading = _PHASE_TITLE.match(line)
            if heading:
                number, title = heading.group(1), heading.group(2)
                break
        fields = _phase_fields(lines)
        if number is None:
            number = fields.get("Phase", "").strip()
        if not number:
            raise SourceError(
                f"traceability: cannot determine the phase number of {path}"
            )
        phases.append(
            Phase(
                number=number,
                title=title,
                path=path,
                delivers=expand_identifiers(fields.get("Delivers", ""), "R-"),
                contributes=expand_identifiers(fields.get("Contributes to", ""), "R-"),
                proves=expand_identifiers(fields.get("Proves", ""), "AC-"),
            )
        )
    numbers = [phase.number for phase in phases]
    duplicates = sorted({n for n in numbers if numbers.count(n) > 1})
    if duplicates:
        raise SourceError(
            "traceability: more than one phase document claims phase "
            + ", ".join(duplicates)
        )
    return tuple(sorted(phases, key=lambda phase: phase.number))


# ---------------------------------------------------------------------------
# STATUS.rst -- the phase board
# ---------------------------------------------------------------------------


def parse_phase_board(path):
    """Return ``{phase number: status}`` from the *Phase board* in ``STATUS.rst``.

    ``STATUS.rst`` is the only authoritative record of current state
    (``ONBOARDING.rst``, *Document map*), so it -- not a phase document's own
    ``:Status:`` field -- decides whether a phase has already passed.
    """
    path = Path(path)
    if not path.is_file():
        raise SourceError(f"traceability: no status document at {path}")
    lines = path.read_text(encoding="utf-8").split("\n")
    start = None
    for index, line in enumerate(lines[:-1]):
        if line.strip().lower() == "phase board" and _HEADING_UNDERLINE.match(
            lines[index + 1]
        ):
            start = index + 2
            break
    if start is None:
        raise SourceError(
            f"traceability: {path} has no 'Phase board' section. The board is "
            "how a 'planned in phase nn' mapping entry is checked against a "
            "phase that has already passed; without it that check is blind."
        )
    end = len(lines)
    for index in range(start, len(lines) - 1):
        if lines[index].strip() and _HEADING_UNDERLINE.match(lines[index + 1]):
            end = index
            break
    board = {}
    for match, cells in _list_table_rows(lines[start:end], re.compile(r"^(\d+)$")):
        if len(cells) < 3:
            raise SourceError(
                f"traceability: phase board row for phase {match.group(1)} in "
                f"{path} has {len(cells)} column(s); expected at least 3 "
                "(phase, title, status)"
            )
        board[match.group(1)] = cells[2].strip()
    if not board:
        raise SourceError(
            f"traceability: the 'Phase board' in {path} yielded no phase rows"
        )
    return board


# ---------------------------------------------------------------------------
# docs/traceability-map.toml
# ---------------------------------------------------------------------------


def parse_map(path):
    """Return ``{AC- identifier: [Evidence, ...]}`` and the raw invalid entries.

    Structural problems (an unknown kind, a missing or unexpected key) are
    returned as ``(identifier, message)`` pairs rather than raised, so that one
    typo does not hide the rest of the report's findings.
    """
    path = Path(path)
    if not path.is_file():
        raise SourceError(
            f"traceability: no mapping file at {path}. R-DOC-03 allows the "
            "AC- half of the map to be a checked-in file; this is that file."
        )
    with path.open("rb") as handle:
        try:
            data = tomllib.load(handle)
        except tomllib.TOMLDecodeError as error:
            raise SourceError(f"traceability: {path} is not valid TOML: {error}") from None

    unexpected = sorted(set(data) - {"meta", "ac"})
    if unexpected:
        raise SourceError(
            f"traceability: {path} has unexpected top-level table(s): "
            + ", ".join(unexpected)
        )
    entries = data.get("ac")
    if not isinstance(entries, dict) or not entries:
        raise SourceError(
            f"traceability: {path} has no [ac.*] entries; refusing to report a "
            "complete map from an empty mapping file"
        )

    mapping = {}
    invalid = []
    for identifier in sorted(entries):
        body = entries[identifier]
        if not isinstance(body, dict):
            invalid.append((identifier, "entry is not a table"))
            continue
        extra = sorted(set(body) - {"evidence"})
        if extra:
            invalid.append(
                (identifier, "unexpected key(s) " + ", ".join(repr(k) for k in extra))
            )
        raw = body.get("evidence", [])
        if not isinstance(raw, list):
            invalid.append((identifier, "'evidence' is not an array of tables"))
            raw = []
        evidence = []
        for position, item in enumerate(raw, start=1):
            if not isinstance(item, dict):
                invalid.append(
                    (identifier, f"evidence entry {position} is not a table")
                )
                continue
            kind = item.get("kind")
            if kind not in EVIDENCE_KINDS:
                invalid.append(
                    (
                        identifier,
                        f"evidence entry {position} has kind {kind!r}; expected one of "
                        + ", ".join(repr(k) for k in EVIDENCE_KINDS),
                    )
                )
                continue
            required, optional = EVIDENCE_SCHEMA[kind]
            keys = set(item) - {"kind"}
            missing = sorted(set(required) - keys)
            surplus = sorted(keys - set(required) - set(optional))
            if missing:
                invalid.append(
                    (
                        identifier,
                        f"evidence entry {position} (kind {kind!r}) is missing "
                        + ", ".join(repr(k) for k in missing),
                    )
                )
                continue
            if surplus:
                invalid.append(
                    (
                        identifier,
                        f"evidence entry {position} (kind {kind!r}) has unknown key(s) "
                        + ", ".join(repr(k) for k in surplus)
                        + " -- a mistyped key silently disables the check it names",
                    )
                )
                continue
            if any(not isinstance(item[key], str) or not item[key].strip()
                   for key in required):
                invalid.append(
                    (
                        identifier,
                        f"evidence entry {position} (kind {kind!r}) has an empty or "
                        "non-string required value",
                    )
                )
                continue
            evidence.append(Evidence(kind, dict(item)))
        mapping[identifier] = evidence
    return mapping, invalid


# ---------------------------------------------------------------------------
# The Java test index
# ---------------------------------------------------------------------------


def index_tests(root):
    """Map every fully-qualified test class under ``cometgui-*/src/test`` to its file."""
    root = Path(root)
    index = {}
    for module in sorted(root.glob("cometgui-*")):
        test_root = module / "src" / "test" / "java"
        if not test_root.is_dir():
            continue
        for source in sorted(test_root.rglob("*.java")):
            relative = source.relative_to(test_root).with_suffix("")
            index[".".join(relative.parts)] = source
    return index


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------


def find_project_root(start):
    """Walk up from ``start`` to the directory that holds the project documents.

    Used by ``docs/conf.py``, which may be running from the real tree, from a
    fresh extraction, or from a throwaway copy made by a gate script.
    """
    start = Path(start).resolve()
    for candidate in (start, *start.parents):
        if (candidate / "specification.rst").is_file() and (
            candidate / "phases"
        ).is_dir():
            return candidate
    raise SourceError(
        f"traceability: no project root above {start} (looked for a directory "
        "holding both specification.rst and phases/)"
    )


def load_project(root):
    """Parse every source and return the assembled :class:`Project`."""
    root = Path(root).resolve()
    rules, criteria = parse_specification(root / "specification.rst")
    phases = parse_phases(root / "phases")
    board = parse_phase_board(root / "STATUS.rst")
    map_path = root / "docs" / "traceability-map.toml"
    mapping, invalid = parse_map(map_path)
    project = Project(
        root=root,
        rules=rules,
        criteria=criteria,
        phases=phases,
        phase_status=board,
        mapping=mapping,
        map_path=map_path,
        tests=index_tests(root),
    )
    return project, invalid
