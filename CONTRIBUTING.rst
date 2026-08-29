========================
Contributing to CometGUI
========================

:Audience: Anyone -- agent or human -- about to change a file in this
   repository
:Authority: ``ONBOARDING.rst``. This page is the working summary; where the
   two overlap, ``ONBOARDING.rst`` wins
:Last updated: 2026-08-29

CometGUI is built by a three-tier chain of agents against a written
specification, with every quality gate wired up before there is code to hide
behind it. That only works if everyone follows the same few conventions, so
this page collects them: how the environment is set up, how work is committed,
what a gate is for, how documentation is written, how work is handed over, and
what the licence obliges you to do.

It deliberately does **not** restate how the project is run. That is
``ONBOARDING.rst``'s job, and two documents describing the same process in
different words will drift. Where this page needs one of those rules it names
the section of ``ONBOARDING.rst`` that owns it.

.. contents:: Contents
   :depth: 2
   :local:

Before you change anything
==========================

Read, in this order:

#. ``ONBOARDING.rst`` -- what the project is, the three tiers, sign-off, and
   the finished condition.
#. ``STATUS.rst`` -- where the project actually is. It is the only
   authoritative record of current state; nothing else is.
#. The phase document you are working inside, ``phases/PHASE-nn-*.rst``, and
   the handoffs of the phases it depends on.
#. ``DECISIONS.rst`` -- at least the ``D-`` items your phase names. Some are
   decided, some are open, and an open one is a hard stop, not a judgement
   call (see `Decisions you must not make`_).

``specification.rst`` is the authority on *what* to build. If code and
specification disagree, the specification wins or it gets a recorded
amendment with a new revision number -- never a silent divergence.

Environment
===========

Everything this project needs is installed **project-locally**. Nothing goes
on the host, and that is not a style preference: the project is intended for
publication, and a build that depends on what happens to be installed on one
machine is not reproducible.

* **No ``sudo``, no ``apt``, no host-level ``pip``, nothing added to the host
  ``PATH``.**
* **Toolchain** lives under ``tools/<name>-<version>/`` -- currently a
  Liberica Full JDK, Apache Maven, the Monocle headless JavaFX platform, and
  the font and X11 stacks the GUI tests need. Put it on your ``PATH`` for a
  shell with::

      . tools/env.sh

  which exports ``JAVA_HOME``, ``MAVEN_HOME`` and a prefixed ``PATH`` and
  nothing else. It is generated; do not hand-edit it.
* **Python tooling** lives in the project virtualenv at ``.venv``. Call it by
  path (``.venv/bin/sphinx-build``, ``.venv/bin/python``) rather than relying
  on an activated shell, so scripts work the same way under CI and under an
  agent.
* **Maven writes nothing to ``~/.m2``.** ``.mvn/maven.config`` pins
  ``-Dmaven.repo.local=_build/m2repo`` so the dependency cache is inside the
  repository's ignored build directory, for *every* ``mvn`` invocation -- an
  ad-hoc ``mvn test``, an IDE, CI -- and not only for the build script. The
  path is relative, so run ``mvn`` from the repository root. If you ever find
  yourself passing that flag by hand, the configuration file is missing or has
  been edited -- fix that instead.
* **Build output** goes under ``_build/`` or a module's ``target/``. Both are
  ignored by ``.gitignore``, together with ``.venv/``, ``tools/`` and
  ``scratch/``. Nothing generated is committed.
* **Record the provenance of every tool you fetch**: URL, version, date,
  SHA-256 and licence, in the project's environment manifest. An unprovenanced
  toolchain is not reproducible, and this project's own installer holds its
  downloads to exactly that standard.

The one documented build command
================================

::

    bash scripts/build.sh

That is the command. It bootstraps ``tools/`` and ``.venv`` if they are
missing, then runs the whole local gate -- compile, format and static
analysis, tests, coverage, architecture tests and the strict documentation
build -- using only project-local tools, from a clean ``_build/``.

Phase 01 gate item 1 is exactly this: *a clean checkout builds and tests green
with one documented command*. So if the way you build differs from the way CI
builds, or a contributor needs a private incantation to get a green run, that
is a defect in ``scripts/build.sh``, not a personal workaround. Fix the
script.

Narrower commands are fine while you iterate -- a single module's ``mvn
verify``, ``scripts/ci/docs-build.sh`` for documentation alone,
``scripts/feasibility/check-docs.sh FILE.rst`` for one page. They are
shortcuts, not substitutes: run the documented command before you report work
finished.

Commit conventions
==================

Git history is this project's durability record. An agent running out of
context mid-task is expected rather than exceptional, and frequent commits are
what make that cost one work unit instead of a phase.

**Commit at every milestone, and do not batch.** A landed unit, a passed gate,
a repair, an amended document -- each is its own commit, made when it happens.

**Check ``git status`` first, then commit with an explicit pathspec, after the
message**::

    git status --porcelain
    git commit -m "PHASE-01: <what changed>" -- LICENSE CONTRIBUTING.rst

**Never ``git add -A``, ``git add .`` or ``git commit -a``.** Several agents
may be live in this tree at once. A broad add sweeps another agent's
half-finished work into your commit, and the first anyone knows about it is
when a reviewer reads a diff that spans two units. Name your paths.

**Put the phase identifier in the subject** -- ``PHASE-01: ...`` -- or the
decision identifier when you are recording one -- ``D-001 DECIDED: ...``.
Subjects are read as a phase timeline.

**There is no git remote, and none may be created.** Where CometGUI is
published is the still-open half of ``D-008``: an owner decision, coupled to
the GPL-3.0 source-availability obligation, and not something an agent may
settle by running ``git remote add``. Do not create one, and do not add a
push step to a script or a workflow that assumes one. The cost is known and
recorded: Phase 00 gate item 8 and Phase 01 gate item 6 (*CI runs on a pull
request*) stay unmet until the owner decides, and both phases record that
openly rather than pretending otherwise.

Gate conventions
================

A gate is a check that is supposed to stop bad work reaching the next phase.
Everything below follows from taking that literally.

**Never weaken a gate to make something pass.** Not a threshold, not a
checksum verification, not a validation rule, not a coverage or mutation
target. Not "temporarily". This includes the quiet forms: adding an exclusion,
loosening a regular expression, marking a test skipped, catching an exception
the gate was there to surface, or pinning an assertion to the wrong value that
happens to be produced today. Weakening a gate is a rejection at sign-off no
matter how green the build is.

**If a gate cannot be met, stop.** The options, in order, are in
``ONBOARDING.rst`` under *If a gate cannot be met*: fix the work; split the
phase; or escalate through the tier above you with the evidence. Never mark
something ``PASSED`` with a caveat in prose -- it is ``PARTIAL`` or
``BLOCKED`` with the residue named.

**Exit code 0 proves nothing.** Check that the output exists and is correct.
Two tools in this project's own dependency chain exit 0 while doing nothing
useful, and this is precisely how a green build comes to mean nothing. Scripts
in this repository are expected to verify their own output -- for example
``scripts/feasibility/check-docs.sh`` exits 3 when ``sphinx-build`` succeeds
but writes no HTML.

**A test that asserts "did not throw" is not a test.** Prove the value: the
parsed field, the written file, the computed checksum, the rejected input,
the exact error message.

**A gate that has never been seen to fail has not been shown to work.** So a
quality gate ships with a demonstration of its own failure: inject the defect
the gate exists to catch, show the narrowest command that should catch it
exiting non-zero with the expected diagnostic, then show it passing once the
defect is removed. Two worked examples live in the tree already --
``scripts/verify-license.sh`` run against a deliberately truncated copy under
``_build/``, and Phase 01's falsifiability harness, which does this for every
gate the phase installs. Injure a copy, never the real file.

**Numeric targets come from the specification, not from taste.** The mutation
score gate (``R-TEST-02``) is >= 80% over the critical packages with no
surviving mutation that can disable a checksum, invert a q-value comparison,
drop an output, suppress a validation error, pass an unsupported option or
leak a secret; coverage starts at >= 90% line and >= 85% branch on core
domain, parameter and provenance logic. Any lower threshold must be documented
with the untested risk, in the same commit that lowers it.

Documentation conventions
=========================

* **All project documentation is reStructuredText**, and it must build under
  ``sphinx-build -n -W`` -- nitpicky, warnings as errors. Broken internal
  cross-references are build failures (``R-DOC-05``).
* **``CLAUDE.md`` is the only Markdown file in the repository.** It exists
  because the coding harness reads it and it contains nothing but pointers.
  Do not add a second Markdown file, and do not move content into it.
* **Check a page before you commit it.** ``scripts/ci/docs-build.sh`` is the
  gate and covers both the published tree and the repository-root, ``phases/``
  and ``handoffs/`` documents. ``scripts/feasibility/check-docs.sh FILE.rst``
  is the quick single-file check while writing; it builds a throwaway tree
  under ``_build/`` that shares no configuration with ``docs/conf.py``, which
  makes it a useful independent cross-check but not the gate.
* **Generated pages stay generated.** The Comet parameter reference
  (``R-DOC-04``) and the traceability report (``R-DOC-03``) are produced
  during the documentation build so that they cannot silently diverge from the
  code. Fix the generator or its input, never the generated page.
* **Amend the specification by revision.** A change to ``specification.rst``
  bumps its revision and adds a revision-history entry saying what changed and
  why.
* Third-party attribution belongs in ``docs/citations.rst``; see
  `Licence obligations`_.

Handoff conventions
===================

Work runs in three tiers -- main orchestrator, phase orchestrator, phase agent
-- and each tier signs off the tier below it. ``ONBOARDING.rst`` sections
*Roles* and *Sign-off* define this; what follows is only how it lands on disk.

Who writes what
---------------

.. list-table::
   :header-rows: 1
   :widths: 34 22 44

   * - File
     - Written by
     - When

   * - ``STATUS.rst``, ``DECISIONS.rst``, ``phases/index.rst``
     - Main orchestrator only
     - At every phase gate and every decision. No other tier writes these.

   * - ``phases/PHASE-nn-*.rst``
     - Main orchestrator
     - When a phase is created, split or amended.

   * - ``handoffs/PHASE-nn-worklog.rst``
     - Phase orchestrator
     - Decomposition before the phase starts; a sign-off entry as each unit
       lands.

   * - ``handoffs/PHASE-nn-handoff.rst``
     - Phase orchestrator
     - Before the phase finishes, whether it passed, stalled or was abandoned.

   * - Code, tests, scripts and the documents a unit owns
     - Phase agent
     - As the unit is built, committed by the agent itself.

A phase agent that finds itself editing ``STATUS.rst``, ``DECISIONS.rst``, a
phase document or a handoff has left its lane. Report the change upward
instead.

Templates
---------

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Template
     - Used for

   * - ``handoffs/BRIEF-TEMPLATE.rst``
     - The brief a phase orchestrator fills in to dispatch a phase agent: read
       order, the unit, the files it owns and must not touch, the interfaces
       it must fit, and the acceptance conditions it must demonstrate.

   * - ``handoffs/WORKLOG-TEMPLATE.rst``
     - ``handoffs/PHASE-nn-worklog.rst`` -- the work units of one phase and
       their sign-offs.

   * - ``handoffs/TEMPLATE.rst``
     - ``handoffs/PHASE-nn-handoff.rst`` -- what actually happened in a phase.

``handoffs/PHASE-00-worklog.rst`` and ``handoffs/PHASE-00-handoff.rst`` are
worked examples of the filled-in forms. Read one before writing your first.

What a work log entry contains
------------------------------

One row per work unit, written **before** the unit is dispatched: the unit and
its acceptance conditions, the ``R-`` rules and gate items it serves, and --
once it lands -- its sign-off entry. Rejections and rework are recorded too;
they are the evidence that sign-off is real, not an embarrassment.

What a sign-off entry contains
------------------------------

A sign-off means **you ran it yourself**. Reading an agent's summary and
agreeing with it is not a sign-off, and "agent reported success" is not an
entry. To sign off you must read the actual diff, run the tests and gate
checks yourself and read the output, check the work against the phase document
and the rules it claims to deliver, confirm nothing outside its scope was
quietly changed, and confirm no gate was weakened.

The entry then records: what you ran (the commands), what you observed (actual
output -- numbers, messages, file sizes), the date, and anything you found
that the agent did not. If something could not be verified -- a macOS-only
behaviour, a check needing hardware nobody has -- say so explicitly and mark
it unverified. An unverifiable item is not a passed item.

What a phase agent's report contains
------------------------------------

What you built, the commands you ran and their observed output, what you left
undone, and any decision or surprise the next agent needs. **Report honestly
rather than favourably.** A unit reported as working and signed off on that
basis is the one failure mode this structure cannot absorb -- and the tier
above re-runs everything you claim, so a favourable report buys nothing and
costs the phase its credibility.

Decisions you must not make
===========================

``DECISIONS.rst`` holds ``D-001``..``D-008``: licensing, redistribution,
platform promises, whose data is a fixture, which server a test uploads to,
where the project is published. An agent must never answer one of these alone.
Inventing an answer -- adding a licence, picking a public dataset, pointing a
test at a real server, creating a git remote -- is a serious error, not a
shortcut.

When you hit one: do every part of your work that does not depend on it,
record the blocker where your tier writes, and escalate upward with a concrete
recommendation and the cost of each option -- not an open question. Only the
main orchestrator writes ``DECISIONS.rst``, and only the owner closes a
``D-`` item.

Licence obligations
===================

**CometGUI is GPL-3.0** (``D-001``, decided 2026-08-29), because it derives
from ``Noble-Lab/CasanovoGUI``, which is GPL-3.0. That is a strong-copyleft
commitment the owner accepted deliberately, and it creates obligations that
reach ordinary contributions:

* **``LICENSE`` at the repository root carries the full, unmodified GPL-3.0
  text.** Not a summary, not an SPDX stub. Run ``bash
  scripts/verify-license.sh`` if you have any reason to think it changed; it
  checks the file by size, by SHA-256, by git blob sha -- the same blob sha
  ``DECISIONS.rst`` records for CasanovoGUI's own ``LICENSE`` -- and by
  structure.
* **Contributions are GPL-3.0.** Do not add code under an incompatible licence
  or paste in code whose provenance you cannot state.
* **Anything derived from CasanovoGUI keeps its copyright notices and records
  the derivation** (``D-001`` obligation 2, which Phase 02 owns). Whether
  source files also carry a per-file GPL header, and in what form, is not
  settled by this page: Phase 01's Spotless configuration may enforce a header
  template and Phase 02 owns the derivation notices. Do not invent a header
  format ahead of them -- ask the tier above you.
* **Third-party attribution goes in ``docs/citations.rst``** -- Comet
  (Apache-2.0 with an embedded MIT section), Percolator, the Limelight
  converter, PDV (treated as GPL-3.0 by owner direction) and the bundled
  Liberica JRE (GPLv2 **with** the Classpath Exception, which is what makes
  the combination legitimate).
* **Tool binaries are downloaded, not redistributed** (``D-008``). Comet,
  Percolator, PDV and the Limelight converter are fetched from upstream at
  install time by pinned URL and SHA-256. No release artefact of this project
  contains one, so do not add a step that bundles one.
* **Source availability** for installer recipients is a GPL-3.0 obligation
  Phase 16 owns, and it depends on the open publication half of ``D-008``.

Standing quality rules
======================

These apply to every phase and every change, and are enforced by the
architecture tests as well as by review:

* No scientific logic, hashing, download or parsing code in JavaFX
  controllers.
* Processes are launched with argument arrays, never shell strings, and only
  through the process service.
* No secret ever reaches a log, a provenance record or an export.
* A test that asserts "did not throw" is not a test.
* Never weaken a gate, a checksum verification, a validation rule or a
  coverage threshold to make something pass.

Before you report a unit done
=============================

#. ``bash scripts/build.sh`` is green from a clean ``_build/``.
#. Every acceptance condition of your unit has been **demonstrated**, with the
   command and its actual output, not asserted.
#. Any gate you added has been seen to fail on the defect it exists to catch.
#. Every document you touched passes the strict documentation build.
#. ``git status --porcelain`` shows nothing outside the paths you own.
#. Your work is committed with an explicit pathspec and a phase-tagged
   subject.
#. Your report says what you ran, what you saw, and what you left undone.
