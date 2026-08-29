# Sphinx configuration for the CometGUI documentation tree.
#
# Owned by Phase 01. Deliberately minimal: the project virtualenv holds Sphinx
# and its own dependencies and nothing else, and every extension or theme added
# here becomes a dependency Read the Docs must install as well. Anything added
# must also be pinned in docs/requirements.txt.
#
# The documentation gate is scripts/ci/docs-build.sh, which runs exactly
#     sphinx-build -n -W -b html docs docs/_build/html
# as required by R-DOC-05, plus a second strict build over the project
# documents that live outside this tree (README.rst, ONBOARDING.rst,
# STATUS.rst, DECISIONS.rst, specification.rst, CONTRIBUTING.rst, phases/ and
# handoffs/).

# -- Project information -----------------------------------------------------

project = "CometGUI"
author = "The CometGUI project"
copyright = "2026, The CometGUI project"

# Single source of truth for the version is the Maven build; until a release
# exists there is nothing honest to put here but the pre-release marker.
version = "0.1.0-SNAPSHOT"
release = "0.1.0-SNAPSHOT"

# -- General configuration ---------------------------------------------------

extensions = []

root_doc = "index"
source_suffix = {".rst": "restructuredtext"}
templates_path = []

exclude_patterns = [
    # The HTML output lands inside the source tree (docs/_build/html), because
    # R-DOC-05 fixes that exact command line.
    "_build",
    "requirements.txt",
    "Thumbs.db",
    ".DS_Store",
]

# Nitpicky mode is passed on the command line as -n by the documentation gate.
# It is also set here because Read the Docs has no configuration key for it:
# .readthedocs.yaml can supply -W (fail_on_warning) but not -n, and R-DOC-05
# requires both. Setting it here makes the hosted build as strict as the local
# gate rather than less strict.
nitpicky = True

# No suppress_warnings and no nitpick_ignore. Warnings are errors under -W and
# a warning that cannot be fixed honestly is a finding to report, never
# something to silence here.

# -- HTML output -------------------------------------------------------------

# Stock Sphinx theme: shipped with Sphinx itself, so it adds no dependency.
html_theme = "alabaster"
html_static_path = []
html_title = "CometGUI documentation"


# -- Extension points --------------------------------------------------------
#
# R-DOC-03: the traceability report is generated here, during the documentation
# build, so that an incomplete map is a documentation build failure rather than
# something noticed later. The generator lives in scripts/traceability (standard
# library only, so it adds nothing to docs/requirements.txt) and is also
# runnable on its own as `bash scripts/ci/traceability.sh`.
#
# The gap this closes: developer/traceability.rst is gitignored and generated,
# so a fresh clone has no such file and the developer toctree would fail with
#     toctree contains reference to nonexisting document 'developer/traceability'
# The builder-inited handler below writes the page before Sphinx reads the
# source tree, which is why a fresh extraction of HEAD now builds.

import sys
from pathlib import Path

from sphinx.errors import ExtensionError
from sphinx.util import logging as sphinx_logging

_LOGGER = sphinx_logging.getLogger(__name__)
_CONF_DIR = Path(__file__).resolve().parent


def _project_root(start):
    """The directory holding specification.rst and phases/, at or above ``start``.

    Walked rather than assumed to be ``_CONF_DIR.parent``: this configuration is
    also read from copies of the documentation tree -- scripts/ci/docs-build.sh
    makes one under _build/ for its self-test -- and the generator's inputs live
    in the real repository either way.
    """
    for candidate in (start, *start.parents):
        if (candidate / "specification.rst").is_file() and (candidate / "phases").is_dir():
            return candidate
    return None


def _generate_traceability_report(app):
    """builder-inited handler: write developer/traceability.rst or fail the build."""
    root = _project_root(_CONF_DIR)
    if root is None:
        raise ExtensionError(
            f"traceability: no project root at or above {_CONF_DIR} (looked for a "
            "directory holding both specification.rst and phases/). The "
            "traceability report required by R-DOC-03 cannot be generated."
        )
    scripts_dir = str(root / "scripts")
    if scripts_dir not in sys.path:
        sys.path.insert(0, scripts_dir)
    try:
        import traceability
    except ImportError as error:  # pragma: no cover - environment failure
        raise ExtensionError(
            f"traceability: cannot import the report generator from {scripts_dir}: "
            f"{error}"
        ) from None

    target = Path(app.srcdir) / traceability.REPORT_RELATIVE_PATH
    try:
        written, project = traceability.generate(root, target)
    except traceability.TraceabilityError as error:
        raise ExtensionError(
            "traceability: the traceability report is not complete, so the "
            "documentation build fails (R-DOC-03).\n\n"
            f"{error}\n\n"
            "Fix docs/traceability-map.toml, or the phase document it disagrees "
            "with, and run `bash scripts/ci/traceability.sh`."
        ) from None

    # Exit code 0 proves nothing, and neither does "no exception": check the page.
    if not written.is_file() or written.stat().st_size == 0:
        raise ExtensionError(
            f"traceability: the generator reported success but {written} is missing "
            "or empty."
        )
    tally = traceability.counts(project)
    _LOGGER.info(
        "[traceability] wrote %s: %d R- rules, %d AC- criteria "
        "(%d automated, %d partial, %d planned, %d human sign-off)",
        written.relative_to(Path(app.srcdir)),
        tally["rules"],
        tally["criteria"],
        tally["automated"],
        tally["partial"],
        tally["planned"],
        tally["human sign-off"],
    )


def setup(app):
    app.connect("builder-inited", _generate_traceability_report)
    return {
        "version": "1.0",
        "parallel_read_safe": True,
        "parallel_write_safe": True,
    }
