"""Command line entry point: ``python -m traceability`` with ``scripts/`` on the path.

    python -m traceability --check              validate; write nothing
    python -m traceability                      validate and write the report
    python -m traceability --out PATH           write somewhere else

Exit status:
    0  validated (and, unless ``--check``, wrote a non-empty report)
    1  the map did not validate -- every problem is printed
    2  a source document is missing or is not in the shape this tool parses
    3  the report was written but is missing or empty (exit code proves nothing)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import check, generate
from .checks import STATUS_AUTOMATED, STATUS_HUMAN, STATUS_PARTIAL, STATUS_PLANNED, counts
from .model import SourceError, ValidationFailure
from .sources import find_project_root


def _summary(project):
    tally = counts(project)
    return "\n".join(
        [
            f"traceability: {tally['rules']} R- rules, "
            f"{tally['criteria']} AC- criteria, {tally['phases']} phase documents",
            f"traceability: criteria by evidence -- "
            f"{tally[STATUS_AUTOMATED]} {STATUS_AUTOMATED}, "
            f"{tally[STATUS_PARTIAL]} {STATUS_PARTIAL}, "
            f"{tally[STATUS_PLANNED]} {STATUS_PLANNED}, "
            f"{tally[STATUS_HUMAN]} {STATUS_HUMAN}",
            f"traceability: evidence names {tally['named_tests']} JUnit test(s) and "
            f"{tally['named_checks']} automated check file(s); "
            f"{tally['test_classes_in_tree']} test class(es) exist under "
            "cometgui-*/src/test/java",
        ]
    )


def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="python -m traceability",
        description="Generate or check the CometGUI traceability report (R-DOC-03).",
    )
    parser.add_argument(
        "--root",
        default=None,
        help="project root (default: the directory above scripts/ holding "
        "specification.rst and phases/)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        dest="check_only",
        help="validate only; write nothing and exit non-zero on any problem",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="where to write the report (default: <root>/docs/developer/traceability.rst)",
    )
    parser.add_argument(
        "--quiet", action="store_true", help="print problems and errors only"
    )
    args = parser.parse_args(argv)

    try:
        root = Path(args.root).resolve() if args.root else find_project_root(
            Path(__file__).resolve().parent
        )
    except SourceError as error:
        print(error, file=sys.stderr)
        return 2

    try:
        if args.check_only:
            project, problems = check(root)
            if problems:
                print(ValidationFailure(problems).render(), file=sys.stderr)
                return 1
            if not args.quiet:
                print(f"traceability: root {root}")
                print(_summary(project))
                print("traceability: map is complete -- no problems found.")
            return 0

        target, project = generate(root, args.out)
    except SourceError as error:
        print(error, file=sys.stderr)
        return 2
    except ValidationFailure as error:
        print(error.render(), file=sys.stderr)
        return 1

    # Exit code 0 proves nothing: confirm the page exists and has content.
    if not target.is_file() or target.stat().st_size == 0:
        print(
            f"traceability: wrote nothing useful to {target}",
            file=sys.stderr,
        )
        return 3
    if not args.quiet:
        print(f"traceability: root {root}")
        print(_summary(project))
        print(
            f"traceability: wrote {target} ({target.stat().st_size} bytes, "
            f"{len(target.read_text(encoding='utf-8').splitlines())} lines)"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
