=====================================================================
PHASE-15 work log -- Version Matrix, Performance and Hardening
=====================================================================

:Phase: 15
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``fabbbcf`` (brief ``handoffs/PHASE-15-BRIEF.rst``)
:Started: 2026-11-05

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``fabbbcf`` on ``main``; no other phase live; no Maven,
Java or gate process running (``ps``). Host: 64 cores, load average 1.2.
``scratch/fixture`` (both K562 mzML, the UniProt proteome),
``scratch/phase05/artefacts`` (every pinned artefact of the manifest),
``scratch/phase06/artefacts`` and ``scratch/percolator/3.09`` (the ``.rpm``
binary with Boost 1.66) are present. **No baseline build was taken**, by the
owner's build-economy rule: tier 1 ran both on ``c9d037c``.

Facts established before design
-------------------------------

* **What the manifest offers on Linux x86-64**: Comet 2026.02.2 and
  2026.03.0; Percolator 3.07.1 and 3.06.5 (no Linux 3.09 row, ``D-003``);
  PDV 2.7.0; the Limelight converter 2.8.1. Everything else in
  ``manifests/tools.json`` is macOS or Windows (or Linux aarch64), which
  nothing here can execute.
* **The nightly** (``.github/workflows/nightly.yml``) has one job per check.
  Phase-15 stubs: ``nightly-version-matrix.sh``, ``nightly-large-dataset.sh``,
  ``nightly-determinism.sh``, ``nightly-performance.sh``,
  ``nightly-manifest-verify.sh``, ``nightly-windows-raw.sh``.
  ``nightly-gui-tests.sh`` is a PHASE-14 stub waiting on ``D-006``.
* **The mutation gate** (``scripts/build.sh``, ``stage_gates_pit``) scores
  each *module* on ``status='KILLED'`` alone. PIT's own
  ``mutationThreshold=80`` in ``pom.xml`` is module-wide and counts
  ``TIMED_OUT``. ``org.cometgui.results.export.*`` and
  ``org.cometgui.workflow.{engine,steps,storage}`` are not in
  ``targetClasses``.

Design decisions (orchestrator's, recorded before dispatch)
===========================================================

P15-1 -- what "a critical package" is under ``D-015``
   The graded unit is the **Java package** of each mutated class (inner
   classes folded into their outer class), for every class under a
   ``targetClasses`` prefix. A ``targetClasses`` entry is a prefix that can
   cover many Java packages (``org.cometgui.params.comet.*`` covers
   ``parser``, ``writer``, ``validation`` ...), and ``D-015`` was raised
   precisely because ``validation`` hid at 37.5% inside a module at 88.7%;
   grading per entry would let it hide again. Score =
   (``KILLED`` + ``TIMED_OUT``) / all mutations of the package; at or above
   80% passes. Every other status counts against (``D-014``).
   **"No mutations is a failure"** applies at two levels: a ``targetClasses``
   entry that yields no mutation anywhere in the reactor fails; and a Java
   package under a target that compiles classes with code (the census's
   ``real_classes_of`` sense) but carries no mutation fails -- **subject to
   measurement**: unit 3 measures how many such packages exist before the
   rule is switched on and reports them to me; if any is honest code PIT
   cannot mutate, I decide and record it, never the unit.
P15-2 -- the module-wide figure
   Printed, grades nothing in ``build.sh``. PIT's own ``mutationThreshold``
   stays: when every package is at or above 80% the module's weighted
   average is too, so it cannot fail a build the per-package rule passes,
   and removing it is not this phase's to do.
P15-3 -- ``org.cometgui.workflow.*``
   Carried as residue since Phase 08 (``R-TEST-02`` names command builders
   and stage invalidation; only ``workflow.state`` is gated). Unit 3
   measures the added PIT wall clock at 16 threads. Added if it is 15
   minutes or less; otherwise escalated to tier 1 with the number. Build
   economy is the owner's standing rule, and this is the one place the phase
   would grow the build.
P15-4 -- data, and ``D-006``
   Nothing here commits, or names as the CI fixture, the ``D-006`` local
   fixture. Every suite that needs spectra takes its fixture as **one named
   input** (an environment variable naming a directory, defaulting to
   nothing in CI and to ``scratch/fixture`` only in local scripts), refuses
   to run without it rather than passing, and in CI is a separate job that
   exits 70 through ``stub-lib.sh --waits-for "D-006 ..."``. Parts that need
   no spectra -- installing and probing each offered artefact, Comet ``-p``
   and ``-q``, Percolator over CometGUI's own committed synthetic PIN -- run
   for real in CI. Goldens may hold results derived from the local fixture
   (counts, ranges, identifiers, digests), never spectrum or FASTA content,
   and are keyed by tool pair **and** by the fixture's digest, so a ``D-006``
   CI fixture adds goldens rather than replacing them. Recorded for tier 1.
P15-5 -- cost
   New real-binary, matrix, determinism and performance suites are opt-in:
   not run by a plain ``mvn verify`` or ``scripts/build.sh``. They run from
   their nightly scripts and this phase's harness. ``scripts/build.sh`` may
   not grow by more than the PIT added under P15-3.
P15-5b -- network
   No test reaches a host other than the tools' pinned upstream release
   hosts. The manifest check's "a new upstream release appeared" half reads
   the GitHub releases listing of the five tool repositories
   (``api.github.com/repos/<owner>/<repo>/releases``), which is the upstream
   release host's own index; nothing else.

Work units
==========

Serial, in this order. Each agent is briefed with this unit, its acceptance
conditions, the files to read, and the brief's *Build economy* section
verbatim. No two units run at once.

.. list-table::
   :header-rows: 1
   :widths: 6 50 14 30

   * - #
     - Unit and acceptance conditions
     - Serves
     - Sign-off
   * - 1
     - **A failed install records its reason.** ``ManagedToolManager`` no
       longer discards the exception: the reason (the failing step and the
       cause's words) reaches the install's state, the Tool Manager section
       and the log, through the one redactor. Tests drive the real failure
       shapes: SHA-256 mismatch, ZIP extraction failure, layout/probe
       failure, and an upstream 404 (``D-008``'s "vanished" wording), each
       asserting the exact reason text in state, view-model and log, and
       that a secret in a cause never reaches either.
     - brief residue; ``R-SEC-03``; ``D-008``
     - `Unit 1 sign-off`_
   * - 2
     - **The Percolator probe's workspace is the test's.** The probe takes
       its workspace parent as a parameter (production default unchanged);
       ``theWorkspaceIsCleanedUp`` owns its own directory and counts only
       there; find and fix what leaves ``cometgui-percolator-probe-*``
       directories in ``/tmp`` after a build (named, reproduced, then
       fixed).
     - brief residue
     - `Unit 2 sign-off`_
   * - 3
     - **The mutation gate, scored as the owner decided.** ``build.sh``
       grades per Java package (P15-1), ``TIMED_OUT`` counted detected and
       listed by name, no-mutation failures, module-wide printed only;
       ``results.export.*`` added to ``targetClasses`` (and
       ``workflow.*`` per P15-3); ``verify-test-gates.sh`` gains controls
       proving a package below 80% inside a module above it goes red, and a
       ``TIMED_OUT`` mutant is not read as a miss; control 5 sizes from its
       own sandbox's ``cometgui-ui`` ``jacoco.xml`` with no second
       whole-sandbox build; ``dev-verify.sh --mutation`` repaired. Deliver
       per-package scores **before** (KILLED-only, module-wide as today) and
       **after** over one full reactor PIT run, and the list of packages
       below 80%. Does not write the tests that lift them (unit 4).
     - ``D-014``, ``D-015``, ``R-TEST-02``, gate item 2, ``AC-TST-03``
     - `Unit 3 sign-off`_
   * - 4
     - **Every critical package at 80% or more, by tests.** For each package
       unit 3 lists below 80%: tests that kill the survivors, never an
       exclusion, survivor pin or threshold change; and an audit of every
       remaining survivor in the critical packages against ``R-TEST-02``'s
       forbidden list (checksum, q-value comparison, required output,
       validation error, unsupported option, secret). Split into 4a, 4b ...
       by module if the list is long.
     - gate item 2, ``AC-TST-01``, ``AC-TST-03``
     - `Unit 4 sign-off`_
   * - 5
     - **The manifest verification job** (``R-TEST-08``).
       ``scripts/ci/nightly-manifest-verify.sh`` replaces its stub: every
       pinned artefact downloaded from its URL and checked by size, SHA-256
       and MD5 (and a ZIP member's digests where the manifest pins one);
       fails loudly, naming tool, version, platform, URL and both digests,
       when a checksum differs, when an artefact is gone upstream (404),
       and when a tool's upstream has a newer release than any the manifest
       records. ``--self-test`` proves each of the three red against a local
       loopback server or file override, never by touching upstream, and a
       clean run green.
     - ``R-TEST-08``, gate item 5, ``D-008``
     - `Unit 5 sign-off`_
   * - 6
     - **The version matrix.** Every combination the manifest offers on
       Linux x86-64 run with the real binaries: install and probe each
       artefact through the product; Comet ``-p``/``-q`` per version;
       Percolator per version over the committed synthetic PIN (PSM,
       peptide, weights; XML only where probed capable; no ``-X`` where not);
       the full Comet -> Percolator run per (Comet, Percolator) pair over the
       named fixture input (P15-4). Goldens under
       ``src/test/resources/goldens/comet-<v>_percolator-<v>/`` with
       ``R-TEST-07``'s review rule documented **and enforced by a check**
       (a golden whose content is not covered by a reviewed explanation
       fails). Every combination not offered or not runnable here (macOS,
       Windows, Intel Mac per ``D-011``, Linux 3.09, Thermo RAW) recorded by
       name as unavailable or unverified. Version differences recorded as
       advisories, not special-cased. Replaces ``nightly-version-matrix.sh``
       and ``nightly-large-dataset.sh`` per P15-4.
     - gate item 1, ``R-TEST-07``, ``AC-TST-10``, ``AC-TST-11``
     - `Unit 6 sign-off`_
   * - 7
     - **Determinism.** A pinned run repeated (same inputs, same seed) and
       compared; the tolerance written down and justified from what Comet
       and Percolator actually vary (threads, seeds, floating point, output
       order), from their source and from measurement -- not from what
       passed. Shown red on a production injection that breaks
       determinism. Replaces ``nightly-determinism.sh`` per P15-4.
     - gate item 3
     - `Unit 7 sign-off`_
   * - 8
     - **Performance.** The specification's suites (startup, large
       parameter file, large result tables and filtering within the heap
       budget -- ``AC-RES-10``, multi-gigabyte hashing, Comet and
       Percolator time, cancellation latency, project reopen), thresholds
       measured here with the host and its load recorded as the baseline,
       **each threshold shown red** on a production injection; the
       dedicated-runner half recorded as unverifiable with what such a
       runner needs. Replaces ``nightly-performance.sh``.
     - gate item 4, ``AC-RES-10``
     - `Unit 8 sign-off`_
   * - 9
     - **Flakiness policy.** Written in ``docs/developer/testing.rst``;
       enforced by a check (quarantine register with issue, owner,
       diagnostics, reason, fix plan; no unconditional retries; no
       release-critical test quarantined) with a self-test; ``pdv``
       control 5b put through it (root cause fixed if found); the
       nested-class surefire report overwrite audited in every harness of
       this phase.
     - gate item 6
     - `Unit 9 sign-off`_
   * - 10
     - **This phase's harness**, registered additively in
       ``verify-all-gates.sh`` (lean, its cost stated): one control per gate
       item from the injections recorded in units 1-9; the nightly and
       pipeline definitions consistent (``check-workflows``,
       ``run-pipeline-locally``), no stub left that this phase owns except
       those recorded as waiting on ``D-006`` or a runner.
     - gate items 1-6
     - `Unit 10 sign-off`_
   * - 11
     - **Documentation.** ``docs/developer/testing.rst`` completed (the
       "after Phase 01" framing replaced by the state now);
       ``docs/platform_support.rst`` matrix; traceability map for
       ``R-TEST-02/07/08``, ``AC-TST-01..04``, ``AC-TST-10``, ``AC-TST-11``,
       ``AC-RES-10``, ``AC-REL-01``.
     - deliverable 4, ``R-DOC-03``
     - `Unit 11 sign-off`_

Sign-offs
=========

Unit 1 sign-off
---------------

Pending.

Unit 2 sign-off
---------------

Pending.

Unit 3 sign-off
---------------

Pending.

Unit 4 sign-off
---------------

Pending.

Unit 5 sign-off
---------------

Pending.

Unit 6 sign-off
---------------

Pending.

Unit 7 sign-off
---------------

Pending.

Unit 8 sign-off
---------------

Pending.

Unit 9 sign-off
---------------

Pending.

Unit 10 sign-off
----------------

Pending.

Unit 11 sign-off
----------------

Pending.

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
