===================================================================
PHASE-16 handoff -- Documentation and Release Qualification (PAUSE)
===================================================================

:Phase: 16
:Written: 2026-11-12 by the Phase-16 orchestrator (tier 2)
:Outcome: **PAUSED by the owner after work unit 2**, not finished. The
   owner, relayed by tier 1: *"I want to pause phase 16 after it finishes its
   current unit's work. I will continue it later."* Two of sixteen work units
   are accepted; fourteen have not started. **No exit-gate item is met.**
   This is a pause handoff, not a phase-end handoff: it says what exists, what
   was seen, and where to pick up.
:Records: ``handoffs/PHASE-16-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Each sign-off there names the commands run, the
   counts seen and the orchestrator's own injections.
:Commits: ``c15acef`` .. the commit adding this page, on ``main``, **not
   pushed** (``origin/main`` is ``d303e21``). Nobody in this phase ran
   ``scripts/build.sh`` or the full ``scripts/verify-all-gates.sh``.

.. important::

   **State on 2026-11-14 at 01:20 (the rest of this page is the pause
   record of 2026-11-12 and is out of date).** Units 1, 2, 3, 3b, 4 and 4c
   are accepted. **Unit 5a's agent has reported PARTIAL**: the tools and
   params-comet tests are moved onto a cut of the committed FASTA
   (``e88e440``, ``6a2f7fc``, ``1984599``, ``581a659``); the workflow and
   app tests are not, pending a ruling on two findings. The orchestrator's
   injections on what landed are done and red; its reactor ``verify`` and
   gate run are in flight or next (``_build/p16-orch/u5a/``). **The owner
   pauses the phase when 5a is finished**; nothing after it has started.
   What a successor does first is in ``handoffs/PHASE-16-worklog.rst``,
   *Current state -- a checkpoint kept true*.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

Comet for Windows opens the project's Thermo RAW fixture: a bare-tools trial
on a GitHub Windows runner read ``ph050213_18.raw`` directly with both Comet
releases and carried it through Percolator. A FASTA trimmed to suit the K562
spectra exists, is committed with UniProt's attribution, is reproducible byte
for byte from a committed accession list, and has an acceptance check that is
green on it and red on the identified proteins alone. Nothing uses that FASTA
yet. Nothing else in the phase has started.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 20 74

   * - Unit
     - Commits
     - What
   * - 1
     - ``5980502`` ``6dae9f5``
     - ``scripts/spikes/windows-raw-trial/`` -- ``trial.py`` (standard library
       only; downloads Comet for Windows with its Thermo companions and
       Percolator 3.07.1 by the manifest's pins, the RAW file from PRIDE by
       ``D-019``'s digest, searches it and counts at q < 0.01; ``--check-only``
       runs anywhere), ``run.sh``, and ``workflow.yml``, a template kept
       outside ``.github/workflows/``. ``fixtures/synechococcus-wh8102/`` --
       the WH8102 proteome (2,513 records, 1,080,427 bytes, UniProt release
       2026_04) with ``ATTRIBUTION.txt``. ``.gitattributes``: ``fixtures/**
       -text``. ``docs/citations.rst``: a UniProt section.
   * - 2
     - ``d2b57e2`` ``d8d985a`` ``41411c4`` ``18b7a26`` ``13f7b07``
     - ``fixtures/human-k562-subset/`` -- the trimmed FASTA
       (``uniprot-human-2026_02-k562-subset.fasta``), ``accessions.tsv`` (the
       list and the pinned digest), ``ATTRIBUTION.txt``.
       ``scripts/fixtures/`` -- ``fixture-fasta.sh`` and ``fixture_fasta.py``
       (``describe``, ``verify``, ``build``, ``identify``);
       ``check-fixture.sh``, ``fixture_acceptance.py`` and
       ``fixture-acceptance.json`` (the acceptance check and its floors).
       ``cometgui-app`` test code: the opt-in ``FixtureAcceptancePairs`` and
       a ``runOnce`` overload in ``VersionMatrixPairs``.
       ``docs/developer/test_fixtures.rst`` -- the fixture, the sizing series,
       the floors, how to rebuild, and the list of what switching to the new
       FASTA touches.

**No product code changed. No POM, threshold, floor of an existing control,
workflow file or golden changed.** No test reads the new FASTA.

Units accepted, and the evidence
================================

Unit 1 -- the RAW trial
-----------------------

Run https://github.com/mriffle/CometGUI/actions/runs/43150059739 on
``Windows-2025Server-10.0.26100``, green in 59 s.

.. list-table::
   :header-rows: 1
   :widths: 18 12 24 14 14 18

   * - Comet
     - Read
     - PIN rows (target / decoy)
     - PSMs, q < 0.01
     - Peptides, q < 0.01
     - Linux mzML search, same release
   * - 2026.02.2
     - READ
     - 17,655 (9,674 / 7,981)
     - 873
     - 761
     - 906 / 787
   * - 2026.03.0
     - READ
     - 17,655 (9,674 / 7,981)
     - 894
     - 767
     - 920 / 797

The fallback file was not needed. Read directly, Comet loads 3,531 spectra
against 3,624 from the converted mzML and assigns charge differently, so the
counts are about 3% lower. Mine: ``--check-only`` green on 11 pinned
downloads; two pin injections (the manifest's Comet digest, the RAW file's
digest in ``trial.py``), each exit 3 naming the file; both restored.

Unit 2 -- the trimmed FASTA
---------------------------

3,698 records -- the 698 proteins Percolator names for a target PSM or peptide
at q <= 0.01 in any of eight whole-proteome runs, and 3,000 others ranked by
the SHA-256 of the accession -- 2,539,164 bytes, SHA-256
``bd252b62568d88881e0d13bcdfaa6b9db96666af0ba08fc51863fcd7bdec1b34``, cut from
``UP000005640_9606.fasta`` of UniProt release 2026_02 (20,652 records,
13,731,881 bytes, SHA-256 ``2329a517...d5a0``). Mine, at ``13f7b07``:

* rebuilt byte for byte (``fixture-fasta.sh verify --source
  scratch/fixture/UP000005640_9606.fasta``);
* the acceptance check green, 60 criteria over four pairs in two
  configurations, 131 s;
* its self-test red on the identified proteins alone, on ``psms-above-1pct``
  with five results per spectrum for every pair and on nothing else;
* the version matrix over the present fixture unchanged (11 / 0 / 14 / 36);
* a row dropped in ``PinMerger``: the check red on
  ``percolator-wrote-results`` in all eight runs;
* a record removed from the committed FASTA: **the check passed it**, which
  was a defect -- sent back, fixed, and then seen refused (exit 3);
* ``verify -DskipTests`` over the app module and its dependencies, the
  flakiness check, and the controls ``hardening``, ``docs``, ``traceability``
  and ``flakiness``: all green.

The proposed floors
-------------------

For tier 1 to confirm. Each is 95% of the smallest of the four pairs' counts
**against the whole proteome** (two thirds for the two small counts of the
one-result run); none is set from the trimmed file or from a failing run. The
committed file's counts and the identified-only file's are in the work log's
table under *Unit 2*.

.. list-table::
   :header-rows: 1
   :widths: 30 14 14 42

   * - Criterion
     - Five results per spectrum
     - One result per spectrum
     - What it catches
   * - Target PSMs at q <= 0.01
     - 957
     - 998
     - A FASTA that lost the identified proteins
   * - Target peptides at q <= 0.01
     - 562
     - 589
     - The same
   * - Decoy PSMs
     - 2644
     - 80
     - Decoys not searched, or a PIN that lost rows
   * - Target PSMs at q > 0.01
     - **2691**
     - 93
     - With five results, **over-filtering**: the committed file gives 2,784
       to 2,795, the identified proteins alone 2,580 to 2,605

The exact criteria beside them: the workflow succeeded, Percolator trained
three bins and wrote non-zero weights, its tables hold the PIN's rows and the
product's readers count what the check counts, and the one-result run's
Limelight XML is valid against the converter's schema. Linux x86-64 only.

Gate items
==========

.. list-table::
   :header-rows: 1
   :widths: 6 16 78

   * - Item
     - Result
     - State at the pause
   * - 1
     - Not started
     - The documentation units (12, 13) have not run. The strict build passes
       on the tree as it is (``scripts/ci/docs-build.sh``, 59 and 96 pages).
   * - 2
     - Not started
     - Unit 16. The six "planned, phase 15" entries stand.
   * - 3
     - Not started
     - Units 8 to 10.
   * - 4
     - Not started
     - Units 8, 9 and 14. Two data files are committed, each with its
       attribution, and wait to be entered in the licence audit.
   * - 5
     - Not assessed
     - ``D-001`` is decided; the audit that records it is unit 14.
   * - 6
     - Not started
     - Unit 15. Nothing was prepared and no finding exists.
   * - 7
     - Not started
     - Units 9 and 16.
   * - 8
     - **Partial**
     - The trimmed FASTA exists and its acceptance check passes and has been
       seen to fail on an over-filtered FASTA. **Not done:** no CI job uses
       it, no golden is regenerated, the jobs that name ``D-006`` are still
       stubs that exit 70 (units 5 and 7).
   * - 9
     - **Partial**
     - Step 1 only: Comet on a Windows runner reads the file. The smoke test
       through the product and its golden do not exist;
       ``nightly-windows-raw.sh`` is still a stub (unit 11).

What is incomplete and why
==========================

Everything from unit 3 on, because the owner paused the phase. The work log's
table has each unit's acceptance conditions; none has an agent, a branch or a
file in the tree. In order (decision P16-3): 3 and 4, Phase 15's residue; 5,
the switch to one fixture and the goldens; 6, ``D-016`` and ``D-017``; 7, the
fixture in CI; 8 to 10, the launcher, packaging and the three runners; 11,
the RAW smoke test through the product; 12 to 16, the documentation, the
licence audit, the UX preparation, traceability and the phase's own harness.

**Nothing was left half-done.** The tree is clean, no agent or check is
running, and no runner job is in flight.

Pushed branches and runner runs
===============================

One branch, one run. Nothing else was pushed; ``main`` was not pushed.

* ``phase16/raw-trial-1`` at ``25abc52``: ``main``'s ``6dae9f5`` plus one
  commit that copies ``scripts/spikes/windows-raw-trial/workflow.yml`` to
  ``.github/workflows/phase16-raw-trial.yml`` (decision P16-1). It stays on
  ``origin``; a local branch of the same name exists.
* Run ``43150059739``, workflow ``phase16-raw-trial``, job ``130956861567``:
  **success**, 2026-11-12 03:43 to 03:44 UTC. The lines of its log that begin
  ``RAW-TRIAL|`` are the result.

State on disk outside git
=========================

All under ignored directories. A resume needs the first group; the rest is
evidence and can be deleted.

Needed:

* ``scratch/fixture/`` -- the two K562 mzML files and the whole proteome
  (``UP000005640_9606.fasta``, release 2026_02). The proteome is the source
  ``fixture-fasta.sh verify`` rebuilds from, and every existing test still
  reads this directory until unit 5.
* ``scratch/phase05/artefacts``, ``scratch/phase06/artefacts``,
  ``scratch/percolator/3.09``, ``scratch/scientific-path``,
  ``scratch/limelight-upload-spike/artefacts`` -- as the Phase 14 and 15
  handoffs list them; unchanged.
* ``_build/m2repo`` -- Maven's repository; the checks above ran offline.
* ``scratch/p16/orch/build-economy.txt`` -- the brief's *Build economy*
  section, extracted with ``sed`` so it can be handed to every unit agent
  verbatim. It can be made again from the brief in one line.

Evidence, not needed:

* ``scratch/p16/orch/`` -- ``ghapi.py`` (read-only Actions queries; it takes
  the credential from ``git credential fill`` and never prints or stores it),
  ``wait.sh``, ``raw-trial-1.log`` (the trial's job log), the injection
  transcripts.
* ``scratch/p16/u1/`` (1.2 GB) and ``scratch/p16/u2/`` (1.1 GB) -- the unit
  agents' working files: downloads, the sizing series' reports, the QfO
  archive member.
* ``_build/p16-orch/`` -- my runs of the acceptance check, its self-test, the
  matrix, the injections and the four controls; ``_build/p16u2/`` -- the unit
  2 agent's Maven logs.
* ``scratch/raw-scout/`` -- the RAW search agent's files, from before the
  phase.

Decisions encountered
=====================

No ``D-`` item was answered. Design decisions P16-1 to P16-7 are in the work
log; tier 1 should look at **P16-1** (a runner is reached by a branch whose
one extra commit adds a ``push`` trigger, because no workflow has one and the
phase may neither open a pull request nor dispatch) and **P16-7** (committed
fixture data lives under ``fixtures/<name>/`` with a plain-text attribution
file, and ``fixtures/**`` is ``-text``).

Escalated (none answered here)
==============================

#. **The floors** above.
#. **Percolator warns that its negative samples are too few** in every
   one-result-per-spectrum run against every trimmed FASTA measured (90 to
   105 decoy PSMs; the warning stops above 105; the whole proteome gives
   120). It still trains, and every run converts to valid Limelight XML. The
   check reports the warning and does not grade it. This follows from
   trimming the FASTA at all, and whether it is acceptable is not an agent's
   to decide.
#. **Unit 5 is larger than planned.** The inventory names 129 files. Three
   things in it change the plan: most real-binary tests search the
   proteome's first 1,000 records, not the whole file; four tests and one
   control need a search long enough to cancel, which the subset no longer
   gives; and many tests read Phase 00's outputs under
   ``scratch/scientific-path``, which a runner does not have. The resuming
   orchestrator should split unit 5 before dispatching it.
#. **Documents found wrong** that this tier may not edit: the work log's
   *Blockers escalated* and the end of *Unit 1* list them (``D-019``'s
   figure is one release's; ``D-018`` on whether the proteome's bytes can
   still be fetched; the phase document's prediction of what over-filtering
   does; ``testing.rst`` on the matrix's "Limelight enabled"; the manifest's
   Windows row for Comet 2026.02.2; ``CONTRIBUTING.rst``; ``ONBOARDING.rst``).

Surprises
=========

* **The first run on Windows was green.** The Thermo reader references five
  assemblies that are neither beside Comet nor in the GAC, and reads the file
  without them.
* **Over-filtering does not starve Percolator.** With the identified proteins
  alone it trains, has as many decoy rows as against the whole proteome, and
  reports *more* PSMs at 1%. What shrinks is the rejected side, and that is
  the one criterion the check can be red on.
* **A check written this phase could not go red** in the phase's own
  signature way: ``--require-committed`` vouched for whatever the checkout
  held. Found by altering the real file, not a copy handed to the check.
* **The proteome's 2026_02 bytes are still downloadable**, from EMBL-EBI's
  QfO copies, although UniProt's ``current_release/`` moved to 2026_04.
* **A gate run's log must not be written under** ``scratch/``: the run
  fingerprints that directory and fails itself. Write it under ``_build/``.
* **A foreground command here is moved to the background after about three
  minutes.** An orchestrator waits in calls shorter than that, or on a
  detached command with an exit file.

First thing the next agent should do
====================================

**Tier 1:** record the pause; decide the floors and the Percolator warning.
Nothing here needs a full build.

**The resuming phase orchestrator:** read the brief and the work log; confirm
``git status`` is clean and ``scratch/fixture`` is present; then dispatch
**unit 3** (Phase 15's residue, results and workflow) exactly as the work
log's row states it. Units 3, 4 and 6 do not depend on the floors; units 5
and 7 do, so have tier 1's answer before unit 5, and split unit 5 first.
