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
# HOOK POINT FOR PHASE 01 UNIT 6 (R-DOC-03), NOT IMPLEMENTED HERE.
#
# Unit 6 adds the traceability report generator and wires it in here as:
#
#     def setup(app):
#         app.connect("builder-inited", <generate developer/traceability.rst>)
#         return {"parallel_read_safe": True}
#
# It must generate docs/developer/traceability.rst before Sphinx reads the
# source tree, and must fail the build (raise, so -W-style failure is not even
# needed) when an R- has no implementing phase or an AC- has no test and no
# human-sign-off mark.
#
# Until that hook exists there is a real gap, recorded here rather than hidden:
# docs/developer/traceability.rst is listed in the developer toctree and is
# gitignored, so unit 4 could only leave a placeholder in the working tree, not
# commit one. A fresh clone therefore has no such file and this build fails with
#     toctree contains reference to nonexisting document
#         'developer/traceability'
# until unit 6 lands the generator. Unit 6 closes it; nothing else should.
