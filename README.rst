========
CometGUI
========

A cross-platform desktop application for running a complete Comet ->
Percolator proteomics search workflow, installing every scientific tool it
needs by itself, and recording provenance strong enough to reproduce the run.

**Status: planning complete, implementation not started.** See ``STATUS.rst``.

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
     - The eight open decisions an implementing agent must not make alone.
   * - ``specification.rst``
     - What to build: requirements (``R-``), acceptance criteria (``AC-``),
       architecture, testing strategy. Revision 2.
   * - ``phases/index.rst``
     - The sixteen implementation phases and their dependency order.
   * - ``handoffs/``
     - What actually happened in each phase.

Conventions
===========

* All documentation is reStructuredText, built with
  ``sphinx-build -n -W`` (warnings are errors). ``CLAUDE.md`` is the sole
  Markdown file and contains only pointers.
* All tooling is installed project-locally under ``tools/`` and a project
  virtualenv. Nothing is installed on the host.
* Commit at every milestone, with an explicit pathspec. There is no git remote
  and adding one is an owner decision (``D-008``).
