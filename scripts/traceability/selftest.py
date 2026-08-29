"""Falsifiability harness: prove this gate can actually fail.

CONTRIBUTING.rst, *Gate conventions*: "A gate that has never been seen to fail
has not been shown to work. So a quality gate ships with a demonstration of its
own failure: inject the defect the gate exists to catch, show the narrowest
command that should catch it exiting non-zero with the expected diagnostic,
then show it passing once the defect is removed. Injure a copy, never the real
file. Make the harness itself falsifiable too: a control whose defect was not
actually injected must be reported as a harness failure, not a pass."

Every case below does exactly that, in a copy of the project under ``_build/``:

  1. copy the inputs and check that the copy validates clean (the control);
  2. inject one defect and check that the file really changed;
  3. run the same check and require the *specific* problem, not merely a
     non-zero exit;
  4. restore the copy and require it to validate clean again.

Run it through ``bash scripts/ci/traceability.sh --self-test``.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

from . import check
from .checks import (
    AC_NO_EVIDENCE,
    HUMAN_SET_MISMATCH,
    MAP_MISSING_ID,
    MAP_UNKNOWN_ID,
    R_MULTI_OWNER,
    R_UNOWNED,
    TEST_MISSING,
)
from .model import SourceError

# What a copy needs to be a checkable project. Deliberately not the whole tree:
# _build/, .venv/, tools/ and target/ are large and irrelevant here.
_COPY_FILES = ("specification.rst", "STATUS.rst", "DECISIONS.rst")
_COPY_TREES = ("phases", "docs", "scripts/ci", "scripts/traceability")
_IGNORE = shutil.ignore_patterns("_build", "__pycache__", "*.pyc")


class HarnessError(Exception):
    """The harness could not do its job -- never reported as a pass."""


# ---------------------------------------------------------------------------
# Copying
# ---------------------------------------------------------------------------


def copy_project(root, destination):
    """Copy just enough of ``root`` into ``destination`` to run the checker."""
    root, destination = Path(root), Path(destination)
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    for name in _COPY_FILES:
        shutil.copy2(root / name, destination / name)
    for name in _COPY_TREES:
        source = root / name
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(source, target, ignore=_IGNORE)
    for module in sorted(root.glob("cometgui-*")):
        tests = module / "src" / "test"
        if tests.is_dir():
            shutil.copytree(tests, destination / module.name / "src" / "test",
                            ignore=_IGNORE)
    return destination


# ---------------------------------------------------------------------------
# Injections. Each returns a one-line description of what it changed.
# ---------------------------------------------------------------------------


def _edit(path, transform):
    path = Path(path)
    before = path.read_text(encoding="utf-8")
    after, description = transform(before)
    if after == before:
        raise HarnessError(f"injection changed nothing in {path}")
    path.write_text(after, encoding="utf-8")
    return description


def _phase_file(root, wanted_field="Delivers"):
    """The first phase document whose field starts with a plain (non-range) id."""
    for path in sorted((root / "phases").glob("PHASE-*.rst")):
        for line in path.read_text(encoding="utf-8").split("\n"):
            if line.startswith(f":{wanted_field}:"):
                first = line.split(":", 2)[2].split(",")[0].strip()
                if re.fullmatch(r"R-[A-Z]+-\d+", first):
                    return path, first
    raise HarnessError("no phase document with a plain R- identifier in :Delivers:")


def inject_rule_unowned(root):
    path, identifier = _phase_file(root)

    def transform(text):
        line = next(l for l in text.split("\n") if l.startswith(":Delivers:"))
        rest = line.split(":", 2)[2].strip()
        remainder = rest[len(identifier):].lstrip(", ").strip()
        replacement = f":Delivers: {remainder}" if remainder else ":Delivers: nothing"
        return text.replace(line, replacement, 1), (
            f"removed {identifier} from {path.name}'s :Delivers: field"
        )

    return _edit(path, transform), identifier


def inject_rule_two_owners(root):
    owner_path, identifier = _phase_file(root)
    others = [
        p for p in sorted((root / "phases").glob("PHASE-*.rst")) if p != owner_path
    ]
    if not others:
        raise HarnessError("only one phase document; cannot create a second owner")
    victim = others[-1]

    def transform(text):
        line = next(l for l in text.split("\n") if l.startswith(":Delivers:"))
        return text.replace(line, f"{line}, {identifier}", 1), (
            f"added {identifier} to {victim.name}'s :Delivers: field, so two "
            "phases now own it"
        )

    return _edit(victim, transform), identifier


def _map(root):
    return root / "docs" / "traceability-map.toml"


def _first_criterion(root, kind):
    """The first criterion in the map whose evidence includes ``kind``."""
    text = _map(root).read_text(encoding="utf-8")
    for match in re.finditer(r'\[\[ac\."([A-Z-]+-\d+)"\.evidence\]\]\n(.*?)(?=\n\[|\Z)',
                             text, re.S):
        if f'kind = "{kind}"' in match.group(2):
            return match.group(1)
    raise HarnessError(f"no criterion in the map carries {kind!r} evidence")


def _strip_criterion(text, identifier):
    pattern = re.compile(
        r'\[\[ac\."%s"\.evidence\]\]\n.*?(?=\n\[|\Z)' % re.escape(identifier), re.S
    )
    stripped, count = pattern.subn("", text)
    if not count:
        raise HarnessError(f"{identifier} has no evidence blocks in the map")
    return re.sub(r"\n{3,}", "\n\n", stripped)


def inject_criterion_without_evidence(root):
    identifier = _first_criterion(root, "planned")

    def transform(text):
        stripped = _strip_criterion(text, identifier)
        stripped = stripped.rstrip("\n") + (
            f'\n\n[ac."{identifier}"]\nevidence = []\n'
        )
        return stripped, (
            f"emptied the evidence list for {identifier} -- the criterion is "
            "still mapped, but names no test and no human sign-off"
        )

    return _edit(_map(root), transform), identifier


def inject_missing_test(root):
    identifier = _first_criterion(root, "check")
    ghost = "org.cometgui.docs.TraceabilityReportTest"

    def transform(text):
        return text.rstrip("\n") + (
            f'\n\n[[ac."{identifier}".evidence]]\nkind = "test"\n'
            f'class = "{ghost}"\nmethod = "reportIsComplete"\n'
        ), (
            f"gave {identifier} a test entry naming {ghost}, which was never written"
        )

    return _edit(_map(root), transform), identifier


def inject_human_mark_missing(root):
    """Drop the human evidence from a criterion the specification marks |human|."""
    identifier = _first_criterion(root, "human")

    def transform(text):
        pattern = re.compile(
            r'\[\[ac\."%s"\.evidence\]\]\nkind = "human"\n.*?(?=\n\[|\Z)'
            % re.escape(identifier),
            re.S,
        )
        stripped, count = pattern.subn("", text)
        if not count:
            raise HarnessError(f"no human evidence block for {identifier}")
        return re.sub(r"\n{3,}", "\n\n", stripped), (
            f"removed the human-sign-off evidence for {identifier}, which "
            "specification.rst marks |human|"
        )

    return _edit(_map(root), transform), identifier


def inject_human_mark_invented(root):
    """Claim human sign-off for a criterion the specification does not mark."""
    identifier = _first_criterion(root, "planned")

    def transform(text):
        return text.rstrip("\n") + (
            f'\n\n[[ac."{identifier}".evidence]]\nkind = "human"\n'
            'reference = "somebody will look at it eventually"\n'
            'record = "DECISIONS.rst"\n'
        ), (
            f"claimed human sign-off for {identifier}, which specification.rst "
            "does not mark |human|"
        )

    return _edit(_map(root), transform), identifier


def inject_unknown_identifier(root):
    ghost = "AC-GHOST-01"

    def transform(text):
        return text.rstrip("\n") + (
            f'\n\n[[ac."{ghost}".evidence]]\nkind = "planned"\nphase = "16"\n'
        ), f"added {ghost}, which specification.rst does not define"

    return _edit(_map(root), transform), ghost


def inject_omitted_identifier(root):
    identifier = _first_criterion(root, "planned")

    def transform(text):
        return _strip_criterion(text, identifier), (
            f"deleted {identifier} from the map entirely, although "
            "specification.rst defines it"
        )

    return _edit(_map(root), transform), identifier


# ---------------------------------------------------------------------------
# The cases
# ---------------------------------------------------------------------------

CASES = (
    (
        "rule-with-no-owning-phase",
        "an R- rule that no phase :Delivers:",
        inject_rule_unowned,
        R_UNOWNED,
        "no implementing phase",
    ),
    (
        "rule-owned-by-two-phases",
        "an R- rule two phases claim to own (R-DOC-01 allows one)",
        inject_rule_two_owners,
        R_MULTI_OWNER,
        "R-DOC-01 allows exactly one owner",
    ),
    (
        "criterion-with-no-test-reference",
        "PHASE-01 exit gate item 5: an AC- given no test reference",
        inject_criterion_without_evidence,
        AC_NO_EVIDENCE,
        "no test reference and no human-sign-off mark",
    ),
    (
        "named-test-does-not-exist",
        "a map entry naming a test that was never written",
        inject_missing_test,
        TEST_MISSING,
        "does not exist under cometgui-*/src/test/java",
    ),
    (
        "human-mark-dropped",
        "a |human| criterion the map no longer marks for sign-off",
        inject_human_mark_missing,
        HUMAN_SET_MISMATCH,
        "offers no human-sign-off evidence",
    ),
    (
        "human-mark-invented",
        "human sign-off claimed for a criterion the specification does not mark",
        inject_human_mark_invented,
        HUMAN_SET_MISMATCH,
        "does not mark it |human|",
    ),
    (
        "map-names-unknown-identifier",
        "an identifier in the map that the specification does not define",
        inject_unknown_identifier,
        MAP_UNKNOWN_ID,
        "not defined in specification.rst",
    ),
    (
        "map-omits-defined-identifier",
        "a criterion the specification defines and the map leaves out",
        inject_omitted_identifier,
        MAP_MISSING_ID,
        "absent from traceability-map.toml",
    ),
)


def _validate_clean(root, label):
    try:
        _project, problems = check(root)
    except SourceError as error:
        raise HarnessError(f"{label}: sources did not parse: {error}") from None
    if problems:
        raise HarnessError(
            f"{label}: expected a clean map, got:\n"
            + "\n".join(f"    {problem}" for problem in problems)
        )


def run_case(root, work, name, description, injector, code, fragment, keep=False):
    """Run one case. Returns the copy's path. Raises HarnessError on any doubt."""
    copy = copy_project(root, Path(work) / name)
    print(f"\n--- case: {name} ---")
    print(f"    defect      {description}")
    print(f"    copy        {copy}")

    _validate_clean(copy, f"case {name}: control before injection")
    print("    control     the untouched copy validates clean")

    changed, identifier = injector(copy)
    print(f"    injected    {changed}")

    _project, problems = check(copy)
    if not problems:
        raise HarnessError(
            f"case {name}: THE GATE DID NOT FAIL. The defect was injected and "
            "the check still passed, so this check cannot be trusted."
        )
    matching = [
        problem
        for problem in problems
        if problem.code == code
        and problem.identifier == identifier
        and fragment in problem.message
    ]
    if not matching:
        raise HarnessError(
            f"case {name}: the check failed, but not on the injected defect.\n"
            f"    expected [{code}] {identifier} containing {fragment!r}\n"
            "    got:\n"
            + "\n".join(f"      {problem}" for problem in problems)
        )
    print(f"    diagnostic  {matching[0]}")
    print(f"    ({len(problems)} problem(s) reported in total)")

    if not keep:
        copy_project(root, copy)
        _validate_clean(copy, f"case {name}: control after removing the injection")
        print("    restored    the same copy validates clean once the defect is removed")
    return copy


def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="python -m traceability.selftest",
        description="Prove the traceability gate fails on each defect it exists to catch.",
    )
    parser.add_argument("--root", required=True, help="the project to copy from")
    parser.add_argument("--work", required=True, help="directory for the copies")
    parser.add_argument("--case", default=None, help="run only this case")
    parser.add_argument(
        "--keep",
        action="store_true",
        help="leave the injected copy in place (skips the restore step)",
    )
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    work = Path(args.work).resolve()
    work.mkdir(parents=True, exist_ok=True)

    cases = CASES
    if args.case:
        cases = tuple(case for case in CASES if case[0] == args.case)
        if not cases:
            print(f"selftest: no such case: {args.case}", file=sys.stderr)
            return 2

    print(f"selftest: project {root}")
    print(f"selftest: copies   {work}")
    print(f"selftest: {len(cases)} case(s); the working tree is never touched")

    last = None
    try:
        for name, description, injector, code, fragment in cases:
            last = run_case(root, work, name, description, injector, code, fragment,
                            keep=args.keep)
    except HarnessError as error:
        print(f"\nselftest: HARNESS FAILURE -- {error}", file=sys.stderr)
        return 4

    print(f"\nselftest: OK -- {len(cases)} case(s); each defect was injected, "
          "caught with the expected diagnostic, and cleared on restore.")
    if args.keep and last is not None:
        print(f"selftest: injected copy left at {last}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
