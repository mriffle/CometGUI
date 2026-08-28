.. _onboarding:

###############################################################
CometGUI -- Orchestrator Onboarding
###############################################################

:Audience: Any main/orchestrating agent picking up this project
:Read first: yes -- before any other document, and before any code
:Last updated: 2026-08-28

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

The full requirements live in ``specification.rst`` (revision 2). It is the
authority on *what* to build. This document is the authority on *how the work
is run*.

.. warning::

   **The single most important verified fact.** No XML-capable Percolator 3.08
   build is published for Windows or macOS; every portable archive upstream
   ships is a ``noxml`` build; and the one XML-capable 3.08 artefact is a Linux
   ``.deb`` whose binary needs glibc 2.38 (so it will not run on Ubuntu 22.04,
   Debian 12 or RHEL 9). The Limelight path therefore is **not** obtainable
   from upstream artefacts on most platforms until decision ``D-002`` is made
   and executed. Do not let any phase build a UI, a manifest or a test that
   assumes "select 3.08 and XML appears". See ``specification.rst``,
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
     - Revision 2. Amend by revision, never silently.
   * - ``phases/index.rst``
     - The phase list and dependency order
     - Summary table; the phase files hold the detail.
   * - ``phases/PHASE-nn-*.rst``
     - One phase's scope, deliverables and exit gate
     - What a phase agent is given.
   * - ``handoffs/``
     - What actually happened in each phase
     - Written by the phase agent, verified by the orchestrator.

Nothing else is authoritative. If code and specification disagree, the
specification wins or the specification gets a recorded amendment -- never a
silent divergence.

Roles
=====

Orchestrator (you, if you are the main agent)
---------------------------------------------

* Reads this file, ``STATUS.rst``, ``DECISIONS.rst`` and ``phases/index.rst``
  at the start of every session.
* Chooses the next phase, briefs a **fresh subagent** for it, and stays out of
  the implementation detail.
* **Independently verifies the exit gate.** Never accept a phase agent's
  self-report as evidence. Run the gate commands yourself and read the output.
* Owns ``STATUS.rst``, ``DECISIONS.rst`` and ``phases/index.rst``.
* Is the only channel to the owner. Phase agents do not ask the owner
  questions; they record blockers and the orchestrator escalates.
* Commits at every milestone.

Phase agent (one fresh subagent per phase)
-------------------------------------------

* Is given, in this order: ``ONBOARDING.rst`` (this file), its own
  ``phases/PHASE-nn-*.rst``, and the specification sections its phase names.
* Implements only its phase's scope. Work belonging to another phase is noted
  in the handoff, not done opportunistically.
* Writes ``handoffs/PHASE-nn-handoff.rst`` before finishing, whether it
  succeeded or not.
* Commits its own work with an explicit pathspec.

A phase agent that runs out of context mid-phase is expected. That is why
handoffs and frequent commits exist: a dead agent should cost one commit's
worth of work, not a session's.

How to run a phase
==================

#. **Confirm readiness.** Read the phase's ``Depends on`` and ``Blocked by
   decisions`` fields. If a dependency phase has not passed its gate, or a
   named ``D-`` decision is still open, do not start; pick a phase that is
   ready, or escalate the decision to the owner.
#. **Brief a fresh subagent.** Give it the read order above, its phase file
   path, the gate it must pass, and the rule that it may not weaken the gate.
   Do not paste the phase content into the brief -- point at the file so it
   reads the current version.
#. **Let it work.** Do not micro-manage; do answer questions about intent, and
   re-read the specification yourself rather than guessing.
#. **Verify the gate independently.** Run every gate check. Read failures in
   full. A gate item you cannot verify is a gate item that has not passed.
#. **Record.** Update ``STATUS.rst`` with the outcome, the evidence you saw,
   and the date. File any new decisions in ``DECISIONS.rst``.
#. **Commit.** One commit per milestone, with the phase ID in the subject.

If a gate cannot be met
-----------------------

Stop. Do not lower the bar. The options, in order of preference, are:

#. Fix the work so the gate passes.
#. Split the phase, if part of it is genuinely blocked -- record the split in
   ``phases/index.rst`` and ``STATUS.rst``.
#. Escalate to the owner with a specific question and the evidence.

Never mark a phase ``PASSED`` with a caveat in prose. Either it passed or it is
``BLOCKED``/``PARTIAL`` with the residue named in ``STATUS.rst``.

Decisions
=========

Decisions ``D-001``..``D-008`` in ``DECISIONS.rst`` are questions an agent
**must not answer on its own**: licensing, redistribution, which platforms the
product promises, whose data is used as a fixture, which server a test uploads
to. Each names the phase it blocks.

When you hit one:

* Do every part of the phase that does not depend on it.
* Record the blocker in ``STATUS.rst`` and mark the phase ``PARTIAL``.
* Escalate with a concrete recommendation and the cost of each option -- not an
  open question.

An agent inventing an answer to a ``D-`` item (adding a licence, picking a
public dataset, pointing a test at a real server) is a serious error, not a
shortcut.

Working conventions
===================

Environment
-----------

The working directory is ``/workspace``. As of 2026-08-28 the machine has 64
cores, 376 GB RAM, ~7.3 TB free, ``git``, ``python3`` and ``node``, and network
access to GitHub and PyPI. It has **no** JDK, Maven, Gradle or Docker.

* Everything the project needs is installed **project-locally** -- a JDK and
  build tool under ``tools/<name>-<version>/``, Python tooling in a project
  virtualenv. Nothing goes on the host PATH, nothing uses ``sudo`` or ``apt``.
* Record every fetched tool's URL, version, date, SHA-256 and licence in the
  project's own environment manifest. The project is intended for publication;
  an unprovenanced toolchain is not reproducible.
* Exit code 0 proves nothing. Check that the output exists and has the expected
  content. Two tools in this project's own dependency chain exit 0 while doing
  nothing useful.

Repository
----------

* Git is initialised here and history is the durability record. **There is no
  remote, and adding one is an owner decision (``D-008``) -- do not create
  one.**
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
