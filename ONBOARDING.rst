.. _onboarding:

###############################################################
CometGUI -- Orchestrator Onboarding
###############################################################

:Audience: Any main/orchestrating agent picking up this project
:Read first: yes -- before any other document, and before any code
:Last updated: 2026-08-31

If you read nothing else, read this page and then ``STATUS.rst``.

.. contents:: Contents
   :depth: 2
   :local:

What this project is
====================

**CometGUI** is a cross-platform JavaFX desktop application that runs a
complete Comet -> Percolator proteomics search workflow -- installing every
scientific tool it needs by itself -- and produces a provenance record strong
enough to reproduce the run later.

The user is a mass-spectrometry scientist who should never have to install
Comet, Percolator, PDV, the Limelight converter, or a Java runtime by hand, and
should never have to hand-edit a ``comet.params`` file.

Three things make this harder than "shell out to a binary":

#. **The parameter space.** Comet 2026.02.2 exposes 118 parameters, several of
   which are structured tuples (fifteen variable-modification slots, an enzyme
   table, signed tolerance pairs). The central product-design effort is a
   typed, versioned, validated editor for these -- not a form.
#. **Version-dependent capability.** Percolator 3.09 removed XML I/O; the
   Limelight converter consumes Percolator XML. Which downstream features work
   depends on the *probed capability* of the exact binary in use, per platform.
   See the warning below.
#. **Provenance and proof.** Every input, output, binary, argument array and
   checksum is recorded, and the test suite must independently prove the
   application's claims rather than assert that nothing threw.

The full requirements live in ``specification.rst`` (revision 10). It is the
authority on *what* to build. This document is the authority on *how the work
is run*.

.. warning::

   **The single most important verified fact.** The product uses the *latest
   compatible* Percolator, computed from probed capability -- subject to two
   fixed constraints: **the project does not build Percolator from source**,
   and a version is offered only where upstream publishes a binary for that
   platform. Percolator 3.09 removed XML/XSD I/O and the Limelight converter
   hard-requires Percolator XML, so for a Limelight-enabled run resolution
   returns **3.07.1** -- the newest release publishing XML-capable binaries for
   Linux, macOS and Windows alike (``D-002``, decided). Every XML-capable
   artefact is an OS package, so the installer extracts payloads rather than
   running installers; the macOS one is x86-64, so that stage runs under
   Rosetta 2 on Apple silicon (``D-004``). Newer Percolator versions stay fully
   usable for rescoring and results -- they simply cannot feed Limelight. Do
   not let any phase hard-code a version, or build a UI, manifest or test that
   assumes a particular version implies XML. See ``specification.rst``,
   *Percolator versions and artefact availability*.

Document map
============

.. list-table::
   :header-rows: 1
   :widths: 26 20 54

   * - Document
     - Authoritative for
     - Notes
   * - ``ONBOARDING.rst``
     - How the project is run
     - This file. Stable; changes rarely.
   * - ``STATUS.rst``
     - Where the project is *now*
     - The only place current state lives. Update it at every gate.
   * - ``DECISIONS.rst``
     - Owner/team decisions ``D-nnn``
     - Open decisions block the phases that name them.
   * - ``specification.rst``
     - What to build (``R-`` rules, ``AC-`` criteria)
     - Revision 10. Amend by revision, never silently.
   * - ``phases/index.rst``
     - The phase list and dependency order
     - Summary table; the phase files hold the detail.
   * - ``phases/PHASE-nn-*.rst``
     - One phase's scope, deliverables and exit gate
     - What a phase agent is given.
   * - ``handoffs/PHASE-nn-worklog.rst``
     - A phase's work units and their sign-offs
     - Written by the phase orchestrator as it goes.
   * - ``handoffs/PHASE-nn-handoff.rst``
     - What actually happened in a phase
     - Written by the phase orchestrator, verified by the main orchestrator.

Nothing else is authoritative. If code and specification disagree, the
specification wins or the specification gets a recorded amendment -- never a
silent divergence.

Roles
=====

Work runs in **three tiers**. Each tier spawns the next, and each tier signs
off the tier below it before that work is considered done. No tier does the
tier below's job.

::

    Main orchestrator          one per project, top-level agent
        |  spawns one fresh subagent per phase
        v
    Phase orchestrator         one fresh subagent per phase (00-16)
        |  spawns one fresh subagent per work unit
        v
    Phase agent                one fresh subagent per work unit

Two reasons for the shape. **Context**: a phase is far too large for one
agent's context, so the phase orchestrator holds the phase-level picture while
short-lived workers burn context on individual units. **Verification**: every
piece of work is checked by someone who did not write it -- units by the phase
orchestrator, the phase gate by the main orchestrator.

Tier 1 -- main orchestrator
---------------------------

You, if you are the top-level agent in the session.

* Reads this file, ``STATUS.rst``, ``DECISIONS.rst`` and ``phases/index.rst``
  at the start of every session.
* Selects the next ready phase or phases and **spawns one fresh phase
  orchestrator subagent for each**. Does not implement phase work, and does not
  spawn phase agents directly.
* May run two phase orchestrators concurrently where the dependency graph
  allows it (see the ordering notes in ``phases/index.rst``), provided they do
  not touch the same files.
* **Signs off each phase** (:ref:`sign-off`) by independently re-running the
  exit gate. The phase orchestrator's report is a claim to be checked, not
  evidence.
* Owns ``STATUS.rst``, ``DECISIONS.rst`` and ``phases/index.rst``. No other
  tier writes them.
* Is the only channel to the owner. Escalations arrive from phase
  orchestrators; the main orchestrator decides what reaches the owner and in
  what form.
* Commits at every milestone.

Tier 2 -- phase orchestrator
----------------------------

One fresh subagent per phase, spawned by the main orchestrator. It owns exactly
one phase, start to finish.

* Reads, in this order: this file; its own ``phases/PHASE-nn-*.rst``; the
  specification sections that phase names; and the handoffs of the phases it
  depends on.
* **Decomposes the phase into work units** small enough for one agent to finish
  with context to spare -- typically a coherent module, parser, adapter, UI
  section or test suite. Records the decomposition in the phase work log
  before starting.
* **Spawns one fresh phase agent per work unit.** It orchestrates; it does not
  implement the units itself. Small integration work, wiring and repairs are
  its own to do.
* **Signs off every work unit before moving on** (:ref:`sign-off`). Unsigned
  work does not accumulate: a unit is accepted, sent back for rework, or
  explicitly recorded as deferred with a reason.
* May run several phase agents concurrently when their units do not touch the
  same files; serialises them when they do.
* Maintains ``handoffs/PHASE-nn-worklog.rst`` as it goes, and writes
  ``handoffs/PHASE-nn-handoff.rst`` before finishing -- whether the phase
  passed, stalled or was abandoned.
* Verifies the phase's exit gate itself, and reports to the main orchestrator
  with the evidence, knowing it will be re-checked.
* Escalates blockers and decisions **upward only**. It never contacts the
  owner, never answers a ``D-`` item, never edits ``STATUS.rst`` or
  ``DECISIONS.rst``, and never weakens a gate.

Tier 3 -- phase agent
---------------------

One fresh subagent per work unit, spawned by the phase orchestrator.

* Is given: this file, its phase document, the specific work unit, the
  acceptance conditions for that unit, and the interfaces it must fit.
* Implements exactly that unit, with its tests. Work belonging to another unit
  or another phase is reported, not done opportunistically.
* Commits its own work with an explicit pathspec.
* Reports what it built, what it ran, what it saw, and what it left undone.
  It reports honestly rather than favourably: a unit reported as working and
  signed off on that basis is the one failure mode this structure cannot
  absorb.
* Does not write ``STATUS.rst``, ``DECISIONS.rst``, phase documents or
  handoffs.

An agent at any tier running out of context mid-task is expected, not
exceptional. That is what the work log, the handoffs and frequent commits are
for: a dead agent should cost one work unit, not a phase.

.. _sign-off:

Sign-off
========

Sign-off is the load-bearing part of this structure. It happens twice: the
phase orchestrator signs off each work unit, and the main orchestrator signs
off each phase.

**A sign-off means you ran it yourself.** Reading an agent's summary and
agreeing with it is not a sign-off. Concretely, to sign off you must:

#. Read the actual diff, not the description of it.
#. Run the tests, the build and the relevant gate checks yourself, and read the
   output -- including what scrolled past.
#. Check the work against the phase document and the ``R-`` rules it claims to
   deliver, not against what the agent said it was doing.
#. Confirm the work is inside its scope, and that nothing outside it was
   quietly changed.
#. Confirm no gate, checksum, validation rule or threshold was weakened to make
   something pass. If one was, that is a rejection regardless of how green the
   build is.

Record the sign-off where the tier writes: work units in
``handoffs/PHASE-nn-worklog.rst``, phases in ``STATUS.rst``. A sign-off entry
names what was run and what was observed. "Agent reported success" is not an
entry.

If you cannot verify something -- a macOS-only behaviour, a check needing
hardware you do not have -- say so explicitly and mark it unverified. An
unverifiable item is not a passed item, and a phase resting on one is
``PARTIAL``, not ``PASSED``.

How to run a phase
==================

For the main orchestrator
-------------------------

#. **Confirm readiness.** Read the phase's ``Depends on`` and ``Blocked by
   decisions`` fields. If a dependency phase has not passed its gate, or a
   named ``D-`` decision is still open, do not start it: pick a phase that is
   ready, or escalate the decision.
#. **Spawn a fresh phase orchestrator.** Brief it with: the read order above,
   its phase file path, the instruction to decompose the phase into work units
   and run them through phase agents, the sign-off duty, and the rule that it
   may not weaken the gate. Point at the files rather than pasting their
   contents, so it reads the current version.
#. **Stay out of the detail.** Answer questions about intent; re-read the
   specification yourself rather than guessing; do not start implementing.
#. **Sign off the phase.** When the phase orchestrator reports, re-run every
   gate check (:ref:`sign-off`).
#. **Record and commit.** Update ``STATUS.rst`` with the outcome, the date and
   the evidence you saw; file any new decisions; commit with the phase ID in
   the subject.

For the phase orchestrator
--------------------------

#. **Read in.** This file, your phase document, the specification sections it
   names, and the handoffs you depend on.
#. **Decompose and log.** Write the work units into
   ``handoffs/PHASE-nn-worklog.rst`` with, for each, its acceptance conditions
   and the ``R-`` rules it serves. Sequence them so that later units build on
   signed-off work.
#. **Run one unit at a time** -- or several in parallel where they cannot
   collide. Spawn a fresh agent per unit; do not carry one agent across
   several units.
#. **Sign off each unit** before starting anything that depends on it. Rework
   and re-sign as needed; record rejections, they are useful history.
#. **Verify the gate**, write the handoff, and report upward with the evidence.

If a gate cannot be met
-----------------------

Stop. Do not lower the bar. The options, in order of preference:

#. Fix the work so the gate passes.
#. Split the phase, if part of it is genuinely blocked -- the main orchestrator
   records the split in ``phases/index.rst`` and ``STATUS.rst``.
#. Escalate to the owner, through the main orchestrator, with a specific
   question and the evidence.

Never mark a phase ``PASSED`` with a caveat in prose. Either it passed, or it
is ``BLOCKED``/``PARTIAL`` with the residue named in ``STATUS.rst``.

Decisions
=========

Decisions ``D-001``..``D-008`` in ``DECISIONS.rst`` are questions an agent
**must not answer on its own**: licensing, redistribution, which platforms the
product promises, whose data is used as a fixture, which server a test uploads
to. Each names the phase it blocks.

When a phase agent hits one, it reports it to its phase orchestrator. When a
phase orchestrator hits one:

* Do every part of the phase that does not depend on it.
* Record the blocker in the phase work log and the handoff.
* Escalate to the main orchestrator with a concrete recommendation and the cost
  of each option -- not an open question.

The main orchestrator then records it in ``STATUS.rst``, marks the phase
``PARTIAL``, and decides what goes to the owner. Only the main orchestrator
writes ``DECISIONS.rst``, and only the owner closes a ``D-`` item.

An agent inventing an answer to a ``D-`` item (adding a licence, picking a
public dataset, pointing a test at a real server) is a serious error, not a
shortcut.

Working conventions
===================

Environment
-----------

**The checkout is not at a fixed path, and no document should say it is.** It
was created at ``/workspace`` and moved on 2026-08-31; documents written before
that date still say ``/workspace`` and are stale rather than describing a second
checkout. Derive the root instead::

    cd "$(git rev-parse --show-toplevel)"

As of 2026-08-28 the machine has 64 cores, 376 GB RAM, ~7.3 TB free, ``git``,
``python3`` and ``node``, and network access to GitHub and PyPI. It has **no**
JDK, Maven, Gradle or Docker.

* Everything the project needs is installed **project-locally** -- a JDK and
  build tool under ``tools/<name>-<version>/``, Python tooling in a project
  virtualenv. Nothing goes on the host PATH, nothing uses ``sudo`` or ``apt``.
  Source the toolchain with ``. tools/env.sh`` in every shell.
* **A relocated checkout strands anything that recorded an absolute path.** The
  2026-08-31 move broke ``tools/env.sh`` and 22 virtualenv console scripts, and
  the only symptom Maven offered was ``The JAVA_HOME environment variable is not
  defined correctly``. ``scripts/build.sh`` does not catch this: its toolchain
  stage re-bootstraps only when ``tools/env.sh`` is *missing*, never when it is
  merely wrong. Both are repaired and the generator now resolves the path at
  source time, so the failure should not recur -- but treat that error message
  as "a path moved", not "the JDK is broken".
* Record every fetched tool's URL, version, date, SHA-256 and licence in the
  project's own environment manifest. The project is intended for publication;
  an unprovenanced toolchain is not reproducible.
* Exit code 0 proves nothing. Check that the output exists and has the expected
  content. Two tools in this project's own dependency chain exit 0 while doing
  nothing useful.

Repository
----------

* Git is initialised here and history is the durability record. The remote is
  ``https://github.com/mriffle/CometGUI.git`` (``D-008``, decided 2026-08-30).
  It may move before release, so keep the URL in one place rather than
  scattering it. **Never force-push and never rewrite published history.**
* Commit at every milestone: a passed gate, a landed repair, an amended phase
  document. Do not batch.
* Commit with an explicit pathspec (``git commit -- path/...``). Never
  ``git add -A``: another agent may be live in the same tree, and a broad add
  sweeps its half-finished work into your commit. Check ``git status`` first.

Documentation
-------------

* All project documentation is reStructuredText, built by Sphinx with
  ``sphinx-build -n -W`` (warnings are errors). ``specification.rst`` passes
  this gate today; keep it that way.
* The only Markdown file in the repository is ``CLAUDE.md``, which exists
  because the coding harness reads it, and which contains nothing but pointers.

Quality rules that apply to every phase
---------------------------------------

* No scientific logic, hashing, download or parsing code in JavaFX
  controllers.
* Processes are launched with argument arrays, never shell strings, and only
  through the process service.
* No secret ever reaches a log, a provenance record or an export.
* A test that asserts "did not throw" is not a test. Prove the value.
* Never weaken a gate, a checksum verification, a validation rule or a coverage
  threshold to make something pass.

The finished condition
======================

The project is finished when **all three** hold.

#. **Every phase has passed its gate.** Phases 00-16 in ``phases/index.rst``,
   each verified independently by the orchestrator and recorded in
   ``STATUS.rst``.
#. **Every acceptance criterion in ``specification.rst`` is met.** Each
   ``AC-`` criterion either passes its named automated test, or -- for the ones
   marked ``[human]`` (licensing, licence audit, and the six UX-validation
   activities) -- carries a recorded human sign-off in ``DECISIONS.rst``. The
   traceability report shows no ``R-`` without an implementing phase and no
   ``AC-`` without a test or a sign-off.
#. **The Definition of Done holds.** A scientist on a clean supported computer
   installs only CometGUI; chooses real spectra and a FASTA; configures a valid
   search through the parameter interface; selects a supported Percolator;
   runs the real workflow; inspects 1% PSM and peptide results and changes
   those filters independently; inspects learned feature weights; views spectra
   in PDV; produces and uploads Limelight XML where the platform permits; and
   reads a provenance record with exact versions, commands and MD5 plus SHA-256
   for every input and output -- and automated tests drive both the assembled
   and the packaged GUI through that same workflow and independently prove it.

Partial credit is possible and should be stated honestly: phases 00-13 with
their gates passed are a working, provenance-complete application; phases 14-16
are what make it a *release*. Do not report the project as done while any
``AC-`` is unmet, and do not quietly drop one.
