========
CometGUI
========

A cross-platform desktop application for running a complete Comet ->
Percolator proteomics search workflow, installing every scientific tool it
needs by itself, and recording provenance strong enough to reproduce the run.

**Status: the build and quality skeleton exists; no application feature does
yet.** ``STATUS.rst`` is the authoritative record and the only place to read
for the current state.

Start here
==========

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - File
     - Read it for
   * - ``ONBOARDING.rst``
     - **Read first.** What this project is, how phases are run, and what
       "finished" means.
   * - ``STATUS.rst``
     - Where the project is right now. The only authoritative state record.
   * - ``DECISIONS.rst``
     - The decisions ``D-001``..``D-008``, which an implementing agent must
       never answer alone. Several are decided; the entry records which.
   * - ``specification.rst``
     - What to build: requirements (``R-``), acceptance criteria (``AC-``),
       architecture, testing strategy. Revision 7.
   * - ``phases/index.rst``
     - The sixteen implementation phases and their dependency order.
   * - ``handoffs/``
     - Per-phase work logs and handoffs: the work units, their sign-offs, and
       what actually happened.
   * - ``CONTRIBUTING.rst``
     - The working conventions: environment, the one build command, commits,
       gates, documentation, handoffs and the licence obligations that reach
       contributors.
   * - ``LICENSE``
     - The full GNU General Public License version 3. CometGUI is GPL-3.0
       (``D-001``); ``scripts/verify-license.sh`` checks the file is intact.

Conventions
===========

* All documentation is reStructuredText, built with
  ``sphinx-build -n -W`` (warnings are errors). ``CLAUDE.md`` is the sole
  Markdown file and contains only pointers.
* All tooling is installed project-locally under ``tools/`` and a project
  virtualenv. Nothing is installed on the host.
* Commit at every milestone, with an explicit pathspec. There is no git remote
  and adding one is an owner decision (``D-008``).
