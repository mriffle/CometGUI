=========================================================
PHASE-08 handoff -- Workflow Engine and Comet Adapter
=========================================================

:Phase: 08
:Written: 2026-10-07 by the Phase-08 orchestrator (tier 2)
:Outcome: **All ten work units accepted** (units 1-9 and 7b; unit 3 after one
   rework round, unit 7 after one fixture repair). The phase orchestrator's
   verdict on the exit gate: items 1-9 and the index-compatibility check
   **met on Linux x86-64**, the only platform any of it has executed on.
   Recommended grade **PARTIAL, on platform residue alone** (see
   :ref:`p08h-platform`), the same shape as Phases 03-05. **Not yet signed off
   by tier 1**, whose ``scripts/build.sh`` and full
   ``scripts/verify-all-gates.sh`` run has not happened; nobody in this phase
   ran either, by the owner's build-economy rule.
:Records: ``handoffs/PHASE-08-worklog.rst`` -- **this page is the map; the work
   log is the proof.** Every sign-off there names the commands run, the numbers
   seen and the injections made into production code, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

A scientist can now press Run and a real Comet search happens: one Comet
invocation per spectrum file with ``-N`` into the run directory, the decoy
configuration and any existing ``.idx`` judged by the one validator before
anything launches, pepXML and PIN validated per file, the PINs merged under one
header, every argument array, file and hash recorded in provenance, Cancel
terminating the process tree, and a rerun preview computed from a declared
seventeen-step graph and per-step input fingerprints. Runs are stored in a
versioned, write-once project format behind a project lock. A new harness,
``scripts/verify-workflow-gates.sh`` (``workflow``, 110 controls, about 7.5
minutes), proves every gate item fails on a production defect, two of them
version-blind.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 8 30 62

   * - Unit
     - Commits
     - What
   * - 1
     - ``ea174c5``
     - ``workflow.state``: ``EngineStep`` (17 steps, PREPARATION/RESULT),
       ``StepGraph`` (26 edges), ``Plan``, ``StepInputs``/``Fingerprints``,
       ``RerunPreview``, ``StageProjection``, ``RunState.deriveFrom(Plan, ...)``.
   * - 2
     - ``6643bf4`` ``0da7414``
     - ``domain.project``, ``domain.run`` (layout, ``-N`` base names,
       descriptors, schema policy), ``workflow.storage`` (``project.json``,
       ``run.json``, ``ProjectLock``); ``CanonicalTimestamp.parse`` in
       provenance; ``docs/reference/project_format.rst``.
   * - 3
     - ``c4d7be4`` ``097b2ac`` ``028ff7e`` ``46443dc``
     - ``domain.params`` facts; ``FastaDecoyScanner``,
       ``CometIndexHeaderReader``; ``FastaDecoyRule``,
       ``IndexCompatibilityRule``, ``CometValidator.validate(model, facts)``;
       ``indexFormats`` per release as metadata data; ``docs/decoys.rst``.
   * - 4
     - ``60117e0``
     - ``tools.comet``: per-file and index commands, pepXML/PIN validation,
       R-DEC-04, ``PinMerger``.
   * - 5
     - ``4e50009`` ``db52ab1``
     - ``workflow.engine``: plan execution, states and event log, bounded
       concurrency, cancellation, retry and re-hash revalidation, provenance
       finalisation on every outcome.
   * - 6
     - ``e77e388`` ``e84df85`` ``ee04df9`` ``5c49aba`` ``926f5b6``
       ``a987bef`` ``82d0a52``
     - ``workflow.steps``: ``CometWorkflow`` (check, prepare, start, preview),
       the step actions, the index cache; the real-binary gate tests.
   * - 7
     - ``6eba9c8`` ``963980d`` ``5848d86`` ``f83d7c5`` ``9a0f20c``
     - The Run section: ``RunEnginePort``, ``RunViewModel``, the engine's half
       of ``RunReadinessViewModel`` (``ENGINE_NOT_BUILT`` removed),
       ``RunControl``, ``RunWiring``/``WorkflowRunPort``/``ProjectSession``;
       GUI tests through the real binary; ``paramui`` control H7.
   * - 7b
     - ``e1595e1``
     - Every set path-valued parameter must name a readable file before Comet
       starts (the release default ``spectral_library_name`` placeholder made
       every real run fail at Comet).
   * - 8
     - ``2f27d33``
     - ``docs/developer/workflow_engine.rst``, the architecture page, stale
       statements, traceability for AC-WF-01..05, AC-PRV-03, AC-PRV-04.
   * - 9
     - ``42b368b``
     - ``scripts/verify-workflow-gates.sh``, registered as ``workflow``.

Orchestrator commits: ``42ecfa9`` (work log and design decisions), a sign-off
commit per unit (``792e1bd``, ``ba1a73c``, ``b1a7186``, ``c43a0b1``,
``30314c1``, ``eba423d``, ``8ebae94``, ``68929b5``, ``7e98942``, ``5182d07``),
``003b8f2`` (``paramui`` floor 84 -> 87), and this handoff.

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-provenance`` (``CanonicalTimestamp.parse``); ``cometgui-params-comet``
(the decoy and index rules, ``indexFormats``, ``DecoySource.meaning()``;
``--only params`` 109 after each); ``scripts/cometparams.py`` and its self-test
(``indexFormats``); ``scripts/verify-param-ui-gates.sh`` (control H7);
``scripts/verify-all-gates.sh`` (``workflow`` registered; ``paramui`` floor
87; Phase 08 in the coverage loop, ``--list`` and banner). **No POM and no
module edge changed.**

Exit gate
=========

Every check was run by me; the work log has each injection's failure text.

.. list-table::
   :header-rows: 1
   :widths: 5 12 83

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux)
     - ``RealCometRunTest.gate1TwoFilesFromAReadOnlyDirectory``: the input
       directory first refuses a write (``AccessDeniedException``, else the
       test fails), then two pepXML and two PIN in ``outputs/comet`` with
       distinct ``-N`` bases, exactly two launches, one input each. Read-only
       by removing write permission, not ``unshare`` (P8-5). Red: ``-N``
       dropped (unit 4 sign-off; ``workflow`` 1a), every file on one command
       line (unit 6; 1b).
   * - 2
     - Met (Linux)
     - ``gate2NothingWrittenOutsideTheRunDirectory``: the whole test root
       snapshotted (path, size, mtime, SHA-256); the input tree identical; the
       only changed path outside the run directory is ``project/runs``' own
       mtime. Red: ``workflow`` 2 (base placed beside the run).
   * - 3
     - Met
     - ``gate3MergedPinHasOneHeaderAndTheSummedRows`` (3554 + 2918 = 6472,
       one header) and ``RealMergeMismatchTest`` (merge step FAILED naming
       both files). Red: set comparison (my unit 4 injection; ``workflow``
       3a), a header per input (3b).
   * - 4
     - Met
     - ``RealDecoyBlockTest.gate4...``: readiness and ``prepare`` give the
       hand-typed ``decoy.none_anywhere`` message; no run directory; zero
       launches counted around the real ``ProcessService``. On screen:
       ``RunReadinessUiTest``. Red: ``workflow`` 4a, 4b; my unit 3 and 6
       injections.
   * - 5
     - Met
     - ``gate5DoubleDecoysBlockBeforeCometStarts`` for ``decoy_search`` 1
       and 2, and the run's own validate step. Red: ``workflow`` 5.
   * - 6
     - Met (preview over the declared graph)
     - ``RerunPreviewTest``: the five *Stage reruns* scenarios with
       hand-typed step sets; ``gate6RerunPreviewAfterARealRun`` after a real
       run; the preview on screen (``RealRunUiTest``). **Residue:** scenarios
       (b), (c), (e) name Percolator and Limelight steps that are declared but
       not built until Phases 09-12; proven on the graph, not by executing
       them. Red: ``workflow`` 6a-6c.
   * - 7
     - Met (POSIX)
     - Fake tool with a child: both pids dead after cancel, exit 143 not the
       watchdog's 71, negative control alive (``CancellationTest``); the real
       Comet mid-search on the whole proteome with one thread
       (``RealCancellationTest``); logs and ``provenance.json`` parse,
       cancelled, outputs partial; through the interface
       (``RealCancelUiTest``). Red: ``workflow`` 7 (graded on the fake; see
       *Residue*).
   * - 8
     - Met
     - ``RealChangedInputTest`` (spectrum and FASTA): reuse refused naming the
       file and both SHA-256s, re-hash bypassing the cache (proved with a
       primed stale entry). Red: ``workflow`` 8a, 8b.
   * - 9
     - Met
     - ``gate9ArgvPerFileAndArchivedParamsHash``, read back with
       ``ManifestReader``: two tool records whose argvs differ only at ``-N``
       and the input; the ``comet-params`` hash = SHA-256 of the ``-P`` file =
       ``writeOnce``'s hash = an independent hash. Red: ``workflow`` 9a, 9b.
   * - Index
     - Met (Linux, both releases)
     - ``RealIndexTest``: the index built into the project cache (never
       beside the read-only FASTA) and reused; a v4 index refused for
       2026.03.0 before any launch and accepted for 2026.02.2, in one test.
       Red, **version-blind**: ``workflow`` Iv and Iw.

**Harness runs on the final tree**: ``--only workflow`` 110 controls
in 449 s (floor 110); ``--only docs --only traceability --only quality``
pass (docs 1, traceability 8, quality 42); ``--only tests`` **37 assertions
passed, 0 failed, in 5727 s** -- on its second run; the first failed control 6
for the reason under *Surprises* (a PIT report my own sign-off had narrowed),
repaired without touching the harness. Run by the units after their last
change to what each harness reads (recorded in their sign-offs, **not re-run
by me**): ``params`` 109, ``install`` 95, ``provenance`` 24, ``shell`` 30,
``paramui`` 87 (floor raised from 84).

**Suites**: workflow 608 tests (was 133), tools 429 (was 232), domain 1139
(was 965), params-comet 2094, ui 876 (was 822), app 190 (was 176), 0
failures; 0 ``BugInstance`` in every module (``mvn -pl cometgui-app -am
install``).

.. _p08h-platform:

What has only ever run on Linux
===============================

Every real-binary test is ``@EnabledOnOs(LINUX)``; only linux/x86-64 Comet
has executed in this project. Code that takes a different path elsewhere and
has never run there:

#. **The index cache's symbolic link** (``CometIndexCommand``): on a file
   system without symbolic links it fails with an ``IOException`` naming the
   link (tested through zipfs only). Windows is the open question.
#. **Read-only input by permission bits** (P8-5): the gate-1 proof uses POSIX
   permissions.
#. **Cancellation's exit codes and descendant termination**: Phase 03's
   residue, inherited.
#. **Absolute-path rules** in the run descriptors (Phase 04's residue).

Incomplete, deferred, residue
=============================

* **Mutation gating of the engine**: ``org.cometgui.workflow.engine``,
  ``.steps`` and ``.storage`` are **not** in the POM's PIT ``targetClasses``
  (the brief forbade changing it). Every sign-off ran PIT on them by
  ``-DtargetClasses``: engine 258 = 212 KILLED + 46 TIMED_OUT (82% under
  KILLED-only scoring), steps 227 = 205 + 22, storage 181/181, 0 survived in
  each. Escalated.
* ``workflow`` does not grade the real-binary cancellation test (a broken
  cancel lets Comet finish the whole proteome); item 7 is graded on the fake.
* A Phase 08 run cannot be retried across an application restart (the
  prepared run is in memory); index mode is not offered in the interface;
  ``CometSelection.artefactIdentity`` is empty.
* ``CanonicalParamsWriter.writeOnce`` is not atomic (plain ``CREATE_NEW``);
  every use re-hashes the archived file before trusting it.
* PIN ``SpecId`` carries the absolute ``-N`` path (Phase 09 must know).
* Comet writes ``<base>.decoy.pep.xml`` for ``decoy_search = 2``; validated.

Decisions encountered
=====================

No ``D-`` item was hit. Design decisions P8-1..P8-16 are in the work log;
judgement calls a later phase may revisit: PREPARATION steps never invalidate
downstream; a changed configuration is always a new run (R-RUN-06), only an
unchanged one is retried; ``index_search_type`` contradicting the index's own
type is an ERROR (stricter than either binary); ``run.json`` refuses unknown
members; a project is ``<app data>/projects/default``, locked lazily.

Surprises
=========

* **``-N`` with several inputs** re-measured on 2026.03.0: still silently
  ignored; Comet writes beside the input.
* **``comet -i`` writes the ``.idx`` beside the database**; a symbolic link
  in the cache directory contains it.
* **The release default ``spectral_library_name = /some/path/speclib.file``
  makes every real run fail at Comet** (exit 1); now refused before Comet. An
  empty library file is refused by 2026.03.0 only for some extensions (none
  and ``.msp`` search normally).
* **2026.02.2's fragment-ion v4 index built with ``decoy_search = 1``
  searches to zero decoy rows**; R-DEC-04 catches it after the search.
* **The harnesses run PIT, never ``verify``**, so a unit green on every
  harness was still red on SpotBugs (unit 3). Every later brief required a
  per-module ``install``.
* **A targeted PIT run overwrites the module's ``mutations.xml``**, and
  ``verify-test-gates.sh`` control 6 sizes its injection from the working
  tree's ``cometgui-domain`` report: my own ``-DtargetClasses=domain.params.*``
  run left a 44-mutation report, control 6 injected 24 survivors into a
  678-mutation module and the gate (correctly) stayed green -- **the
  ``tests`` harness's first run failed for that reason**. Repaired by
  regenerating the full report exactly as ``build.sh`` does (653 mutations);
  nothing in the harness changed. Tier 1: run ``build.sh`` before ``--only
  tests`` as usual and this cannot recur.
* ``verify-param-ui-gates.sh`` once failed its final clean re-run on Phase
  07's ``VariableModificationEditorUiTest`` (``bounds are not visible in
  Scene``) and passed on the next run: flaky under that harness.

Escalated (none answered here)
==============================

#. **Add ``org.cometgui.workflow.*`` to PIT ``targetClasses``** (R-TEST-02
   names stage invalidation and command builders; only ``workflow.state`` is
   gated today). Under ``build.sh``'s KILLED-only scoring the engine would
   read about 82% because of its timeouts -- the ``TIMED_OUT`` question,
   which is the owner's.
#. **Specification amendment (P8-3)**: the run layout's
   ``logs/comet.<spectrum-basename>.{stdout,stderr}.log`` is one stream-tagged
   ``logs/comet-<nn>.log`` per invocation (Phase 03's design; a stage id must
   match ``[A-Za-z0-9_-]{1,64}``); ``run.json`` and provenance map ``nn`` to
   the file.
#. **Product decision**: a new configuration starts with Comet's placeholder
   spectral library, so every first run is blocked until the scientist
   clears it; and ``spectral_library_name``'s curated ``shortHelp`` ("a file
   Comet cannot read means no spectral-library search") is false on
   2026.03.0. Recommendation: a per-release default override to empty,
   through the existing metadata mechanism, with the Phase 07 gate-1
   expected file re-pinned.
#. **A pre-run rule for 2026.02.2 fragment-ion indexes with internal
   decoys** (zero decoy rows), or leave it to R-DEC-04.
#. **Phase 02's ``KeyboardOnlyNavigationUiTest``** assumes no tab stop but
   navigation; on a machine whose application-data folder has a Comet
   installed, Run becomes one and the test fails.
#. **The ``tests`` harness's cost**: ``--only tests`` took 5727 s (95
   minutes), up from 3939 s at Phase 07: its sandbox runs
   ``scripts/build.sh``, which now runs this phase's real-binary tests
   (workflow steps about 64 s, the app's real-run GUI tests about 45 s) and
   their PIT. The new ``workflow`` harness adds about 7.5 minutes to the full
   run.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts``, ``scratch/phase06/artefacts``
and ``scratch/fixture`` are present, run ``bash scripts/build.sh`` and then
``bash scripts/verify-all-gates.sh`` in full (now with ``workflow``, 110
controls, about 7.5 minutes; ``paramui`` floor 87). Then decide the
escalations above.

**Phase 09:** read ``docs/developer/workflow_engine.rst``. Percolator's steps
are already declared (``RUN_PERCOLATOR``, ``PARSE_PERCOLATOR``,
``FINALISE_RESULTS``) with their inputs and edges, and ``implementedInPhase``
is the one value to change; start from the preserved ``inputs/pin/merged.pin``
and carry the one decoy prefix (R-DEC-03). The engine's ``StepAction`` and
``StepContext.invoke`` are how a tool runs; never a second launcher.
