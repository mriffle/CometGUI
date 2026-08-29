"""CometGUI traceability report generator (``R-DOC-03``).

``R-DOC-03`` requires a report mapping ``R-``/``AC-`` identifiers to phases and
to test names, generated from a checked-in mapping file and built by
documentation CI, where *an identifier with no implementing phase, or an* ``AC-``
*with no test and no human-sign-off mark, is a documentation build failure*.

Two entry points:

* :func:`check` -- validate and write nothing. ``scripts/ci/traceability.sh``
  and the ``--check`` flag use this.
* :func:`generate` -- validate and write ``docs/developer/traceability.rst``.
  ``docs/conf.py`` calls this from a ``builder-inited`` handler, so an
  incomplete map fails the documentation build itself rather than being
  discovered later.

Standard library only, Python 3.11+ (``tomllib``). Adding a dependency here
would add one to Read the Docs, which installs ``docs/requirements.txt``.
"""

from __future__ import annotations

from pathlib import Path

from .checks import counts, status_of, validate
from .model import (
    Criterion,
    Evidence,
    Phase,
    Problem,
    Project,
    SourceError,
    TraceabilityError,
    ValidationFailure,
)
from .report import render
from .sources import find_project_root, load_project

__all__ = [
    "Criterion",
    "Evidence",
    "Phase",
    "Problem",
    "Project",
    "SourceError",
    "TraceabilityError",
    "ValidationFailure",
    "check",
    "counts",
    "default_output",
    "find_project_root",
    "generate",
    "load_project",
    "render",
    "status_of",
    "validate",
]

#: Where the report lands inside a documentation source tree.
REPORT_RELATIVE_PATH = Path("developer") / "traceability.rst"


def default_output(root):
    """The generated page's path for a project rooted at ``root``."""
    return Path(root) / "docs" / REPORT_RELATIVE_PATH


def check(root):
    """Parse and validate. Returns ``(project, problems)``; writes nothing."""
    project, invalid = load_project(root)
    return project, validate(project, invalid)


def generate(root, out=None):
    """Validate, then write the report. Raises on any problem.

    ``out`` defaults to ``<root>/docs/developer/traceability.rst``. The
    documentation build passes the *source tree it is actually reading*, which
    is not always the real ``docs/`` -- a gate script may be building a copy.
    """
    project, problems = check(root)
    if problems:
        raise ValidationFailure(problems)
    target = Path(out) if out is not None else default_output(root)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(render(project), encoding="utf-8")
    return target, project
