===========================================================
Between Phases 14 and 15 -- the E12 repair, work log
===========================================================

:Brief: ``handoffs/FIX-P14-P15-BRIEF.rst`` (tier 1's; not edited)
:Agent: a single fix agent, dispatched directly by tier 1
:Started: 2026-10-29, from ``6813363`` on ``main``, ``git status`` clean
:Commits: ``88383ce`` (engine, ``run.json``, workflow tests), ``04a776b``
   (interface, GUI test, failure-path map, documents), ``23fd3b9`` (the
   settings block kept where the ``percolator`` gate injects), ``ac8f00e``
   (the reason's words pinned for PIT), ``cb00de5`` (three SpotBugs findings),
   ``4b17931`` (an intermittent Phase 14 test, repaired), and the commit that
   adds this log

Kept by the fix agent as the work ran. Every number below was read off a run
made in this session. Every injection was made in **production** code, proved
to have landed by its old anchor being gone (``grep -c`` = 0), and restored
and checked with ``sha256sum -c``.

.. contents:: Contents
   :depth: 2
   :local:

Build economy, as followed
==========================

Neither ``scripts/build.sh`` nor the full ``scripts/verify-all-gates.sh`` nor
``--only tests`` was run. ``scripts/dev-verify.sh --dry-run`` showed that the
change reaches the whole reactor, so the affected modules were run directly
(``-pl <module> -am`` with ``-Dtest`` limited to that module's packages, so the
upstream modules compile but their tests do not run). ``--mutation`` was not
used; PIT was run on the changed classes only. ``--only tests`` was replaced by
``bash scripts/verify-test-gates.sh --preflight``, which printed ``PREFLIGHT:
every precondition holds; no control was run.``

**Tests that read outside their module.** ``SealFailureRunTest``'s two
real-tool tests read ``scratch/phase05/artefacts`` (Comet 2026.03.0,
Percolator 3.07.1), ``scratch/fixture`` (both K562 mzML, the proteome) and
``manifests/tools.json``, through ``RealComet`` and ``RealPercolator``, exactly
as ``RealPercolatorRerunTest`` already does; the preflight checks all three.
Every other new or changed test reads nothing outside its module (stand-in
shell tools written into a temporary directory). No test reads a project
document.

**Which of them the** ``tests`` **harness selects.** None by name:
``verify-test-gates.sh`` names only ``LayeringRulesTest`` and
``ClassImportCensusTest``. Its sandbox build runs the whole suite, so every new
and changed test runs there, and the new test classes join its census.

What E12 was, and every path that finalises a run
=================================================

``RunExecution.finish`` derived the attempt's outcome from the step states,
then wrote ``provenance.json`` and ``provenance.rst``, catching a failure into
``finalisationErrors``, and recorded the outcome in ``run.json`` regardless. So
a run whose record could not be written was ``SUCCEEDED`` in the result, in
``run.json`` and on screen ("The run succeeded ... Its provenance could not be
finalised: ...").

**Paths found.** ``RunExecution.finish`` is the only code in the product that
writes ``provenance.json`` or ``provenance.rst`` for a run, or ends an attempt
in ``run.json`` (``grep`` for ``ManifestWriter``, ``ProvenanceReportWriter`` and
``withAttemptFinished`` over every ``src/main``). Everything that runs an
attempt reaches it through ``WorkflowEngine.start``:

* the ordinary run and its retry -- ``CometWorkflow.start`` and
  ``WorkflowRunPort.retry`` (``engine.start`` directly when reuse is refused);
* the Percolator compatible rerun -- ``PercolatorRerun.start``, reached from
  the Percolator section (``WorkflowRunPort``) and from the Limelight section's
  rerun (``SessionEngine.rerunCompatible`` through ``ProjectLimelightPort``).

**Limelight's conversion does not seal**: ``LimelightConversions`` appends
``limelight.converting`` / ``limelight.converted`` / ``limelight.unconverted``
to the run's ``events.log`` (``R-LL-04``: ``provenance.json`` is the sealed
record) and reads ``provenance.json``, never writes it, and refuses a run that
has none ("has no provenance record ..., so what it produced cannot be checked
and nothing of it is converted"). It needed no change.

What changed
============

The engine (``88383ce``, ``23fd3b9``)
-------------------------------------

``RunExecution.finish`` now, in order:

#. derives what the steps did (``outcome``) and builds the manifest with that
   status;
#. **seals**: writes ``provenance.json``; if that fails it writes no report (it
   would describe a record that is not on disk) and the seal has failed;
   otherwise writes ``provenance.rst``, whose failure also fails the seal;
#. **decides the outcome** (``ending``): ``FAILED`` and run state ``FAILED``
   when the seal failed, whatever the steps did; the manifest is rebuilt as
   failed, and a ``provenance.json`` already written as a success (the report
   failed after it) is rewritten as failed, so the record never contradicts the
   run;
#. records the end in ``run.json`` -- ``withAttemptFailed(reason, ended)`` when
   the seal failed, otherwise ``withAttemptFinished`` -- as far as the disk
   allows (a failure is a finalisation error, as before);
#. appends ``run.finished`` with ``status`` ``failed`` and the reason as its
   ``message``, as far as the disk allows.

The reason is in plain words: ``its provenance record <path> could not be
written: <the system's reason> (<file>)`` -- ``RunExecution.plainly`` gives an
``AccessDeniedException`` as ``permission was denied (<file>)``, any other
``FileSystemException`` as its own reason and file, anything else by its
message (``File too large``, ``No space left on device``). For the report:
``its provenance report <path> could not be written: ...``.

Nothing a step produced is touched; every step keeps the state it reached.
``RunResult`` gains ``provenanceFailure`` (an ``Optional<String>``; its
constructor refuses one with an outcome other than ``FAILED`` or a run state
other than ``FAILED``), with an 8-argument constructor kept for every existing
caller; the seal's failure is no longer also put in ``finalisationErrors``.

Decisions made inside the brief, for tier 1 to confirm:

* **A cancelled run whose seal fails ends** ``FAILED``, not ``CANCELLED`` --
  "a run whose provenance.json could not be finalised ... ends FAILED". Its
  reason is recorded as for any other.
* **The report counts as part of the seal.** E12 names both files; a run whose
  ``provenance.rst`` cannot be written is failed too, and its
  ``provenance.json`` is rewritten as failed.
* **The order** is the brief's: seal, then ``run.json``, then the event (the old
  code appended ``run.finished`` before writing ``run.json``).

``run.json`` schema version 3 (``88383ce``)
-------------------------------------------

The brief requires ``run.json`` to record ``FAILED`` **with that reason**, and
``run.json`` had nowhere to put one: its reader is strict, so a new member is
a new version (``R-RUN-04``). ``RunAttempt`` gains ``reason``
(``Optional<String>``; only a failed attempt may have one, never blank;
``failed(why, end)``), ``RunDescriptor`` gains ``withAttemptFailed`` and
``REASON_SCHEMA_VERSION = 3``. A run is written at version 3 **exactly when an
attempt records a reason**: version 2's members, ``derivedFrom`` ``null`` for an
own search, and ``reason`` (string or ``null``) last on every attempt. Every
other run is still written byte for byte as version 1 or 2. ``RunJson`` reads
1 to 3 and refuses a version-3 document whose attempts record no reason (not
the lowest version that expresses it). This changes one documented property:
a run's version was "decided by the identity alone ... for life"; it now rises
once, at the end of such an attempt, and never falls.
``docs/reference/project_format.rst`` says so, member by member. **For tier 1**:
``specification.rst`` revision 21 should say that ``run.json`` records the
reason (version 3); ``R-RUN-04``'s policy is unchanged (older builds refuse
version 3 before reading anything).

The interface (``04a776b``)
---------------------------

The three places that produced "Its provenance could not be finalised", each
replaced:

* ``RunViewModel.outcomeOf``: a run with a ``provenanceFailure`` reads "The run
  failed: <reason>. Run <id> in <dir>. What its tools wrote is kept in the run's
  directory." (after a step failure, the reason is appended). The stepper is
  told too: ``StageStepperViewModel.failStage(RESULTS, reason)`` -- the Results
  stage, where core provenance is finalised (``finalise-provenance`` projects
  there), is shown failed, so the derived run state is ``FAILED``, and the
  reason is carried in a new ``reasonProperty`` that any later state change
  clears. ``StageStepper`` writes it after the run state: "Run state: Failed.
  Its provenance record ... could not be written: ...".
* ``PercolatorRerunViewModel.outcomeOf`` and ``LimelightViewModel.finished``:
  "The Percolator rerun failed: <reason>. The new run is <description>. What
  Percolator wrote is kept in the new run's directory" (Limelight adds "and it
  is not offered for conversion"); as before, only a ``SUCCEEDED`` rerun is
  chosen in Results for conversion.
* Other finalisation errors (an event not appended, an end ``run.json`` could
  not take) now read "Part of its record could not be written: ...", because
  "its provenance could not be finalised" is no longer true of them.

Downstream follows ``run.json``'s outcome, which is now ``FAILED``: the Results
section lists the run with ", failed", exactly as any failed run with tables
(``ProjectResultsPort`` labels by the latest attempt's outcome); the Percolator
rerun and Limelight conversion refuse a run with no ``provenance.json``
(``RerunSource``, ``LimelightConversions``), as for any run without one; the
Provenance section lists it "failed, no sealed record" and opens it as Phase
13's unsealed run. No downstream code changed.

Tests that prove it, with counts
================================

New:

* ``SealFailureRunTest`` (``cometgui-workflow``, 3 tests, 38.6 s). The moment
  "after the last step, before the seal" is reached through the engine's own
  clock (``SealTrigger``, test code): the first time the engine reads the time
  after ``run.json`` records ``finalise-provenance`` succeeded, on the engine's
  thread, before ``finish`` is dispatched; no hook in the product.

  - ``anOrdinaryRunWhoseRecordCannotBeWritten`` -- real Comet 2026.03.0 over
    both K562 files and real Percolator 3.07.1; ``provenance/`` made read-only
    (a refused write proves it). FAILED, run state FAILED, the reason starting
    ``its provenance record <json> could not be written: permission was denied
    (<json>.tmp-``; every step ``SUCCEEDED``, no failures, no other
    finalisation error; ``run.json`` attempt FAILED with that exact reason, the
    file at ``"schemaVersion": 3``; the event log's last event ``run.finished``,
    ``failed``, its message the reason; no ``provenance.json`` or ``.rst``, only
    ``events.log`` left in ``provenance/``; every output the run hashed
    (``file.hashed``, direction output, at least 3) on disk with that SHA-256,
    and ``outputs/`` byte-, size- and mtime-identical to its snapshot at the
    seal; the product's ``ProvenanceReader`` lists it ``FAILED`` and unsealed
    and loads it ``NO_SEALED_RECORD``, summary status ``failed``, with the
    "no sealed provenance record" warning.
  - ``aPercolatorRerunWhoseRecordCannotBeWritten`` -- a sealed real search with
    Percolator 3.09, then the compatible rerun with 3.07.1 for Limelight
    (``PercolatorRerun.prepare`` / ``start``), the rerun's ``provenance/`` made
    read-only: the same assertions on the rerun, the source run's tree
    unchanged entry by entry, and the listing ``run-0001 completed sealed``,
    ``run-0002 failed unsealed``.
  - ``runJsonUnwritableToo`` (stand-in tools) -- the run's directory and its
    ``provenance/`` made read-only: FAILED with the reason; exactly one
    finalisation error, ``could not record the attempt's end:
    java.nio.file.AccessDeniedException: <run.json>.tmp-``; ``run.json``
    byte-identical to its content at the seal; ``run.finished`` failed with the
    reason. Reopened (``RealProject.open``), the attempt reads ``RUNNING`` and
    the reader lists ``run-0001 interrupted unsealed``, ``NO_SEALED_RECORD``,
    its summary status never ``completed``. **It shows interrupted**, no worse.

* ``FileSizeLimitRunTest#theRecordRefusedForWantOfRoom`` (new method, 1.9 s):
  a child JVM (``RunChild seal 12000``) runs a one-file stand-in search with no
  limit and lowers ``RLIMIT_FSIZE`` to 12 000 bytes only at the seal (the
  ``SealTrigger`` action runs ``/usr/bin/prlimit``; ``/proc/self/limits`` reads
  ``Max file size 12000 12000 bytes``). Measured at the seal: ``run.json``
  4 828 bytes, ``events.log`` 20 097 (an unlimited run's ``provenance.json`` is
  16 210). Outcome FAILED, reason ``its provenance record <json> could not be
  written: File too large``, no step failed, every step ``SUCCEEDED``;
  ``run.json`` attempt FAILED with that reason; the event log, already past the
  limit, cannot take ``stage.finished`` or ``run.finished``, and the result
  states both (``could not append ...: java.io.IOException: File too large``);
  no record, report or temporary file; the merged PIN kept. Not a real full
  disk, as the brief asks.

* ``SealFailureWordsTest`` (5 tests): the reason's last words for a refused
  permission, a file-system reason, a bare message and none.
* ``EngineEdgeTest``: ``anUnwritableManifestFailsTheRunWithTheReasonEverywhere``
  (a directory at ``provenance.json``'s path: FAILED, the reason in the result,
  ``run.json`` and ``run.finished``, no report written) and
  ``anUnwritableReportFailsTheRunAndTheRecordSaysSo`` (a directory at
  ``provenance.rst``'s path: FAILED naming the report, ``provenance.json``
  rewritten with status ``failed``).
* ``RunJsonTest`` +8 (the hand-built version-3 document written and read, a
  derived run at version 3, five damaged version-3 documents each refused
  naming the member); ``RunAttemptTest`` +2; ``RunDescriptorTest`` +1;
  ``StageStepperViewModelTest`` +1; ``StageStepperTest`` +1;
  ``RunViewModelTest#provenanceFailureStated``;
  ``PercolatorRerunViewModelTest#provenanceFailureStated``;
  ``LimelightViewModelTest#rerunWhoseRecordCannotBeWritten`` (the failed rerun's
  run not chosen, not offered for conversion).
* ``SealFailureUiTest`` (``cometgui-app``, GUI, 3 ordered tests, 12.6 s): a
  search run from the Run section with stand-in tools; while Percolator waits
  (its files written), ``provenance/`` is made read-only and Percolator then
  ends well. The outcome label starts "The run failed: its provenance record
  ... could not be written: permission was denied (" and contains no
  "succeeded"; Comet and Percolator "Succeeded", Results "Failed", the run
  state label "Run state: Failed. Its provenance record ..."; ``run.json``
  failed with the reason, ``schemaVersion`` 3; the Results section lists the
  run ending ", failed"; the Provenance section lists it ", failed, no sealed
  record", opens "No sealed record: ...", summary "Status: failed".

Module totals, after the change, each module's whole suite: ``cometgui-domain``
**1 230**, 0 failures; ``cometgui-workflow`` **1 031**, 0 failures (1 018
before; ``SealFailureWordsTest``'s 5 were added after it and run alone, 5/5);
``cometgui-ui`` **1 286**, 0 failures (4 min 31 s); ``cometgui-app`` **583**, 0
failures, 91 skipped (the opt-in end-to-end tier, as before; 26 min). The ``ui`` and
``app`` suites ran on the code of ``04a776b``; after ``cb00de5`` (SpotBugs) and
``4b17931`` the changed classes were run alone -- ``RunDescriptorTest`` 24,
``StageStepperTest``, ``StageStepperViewModelTest`` and ``RunViewModelTest``
68, ``SealFailureUiTest`` and ``AccessibleNameEnumerationUiTest`` 10,
``RealAwkwardPathRunTest`` 2, all passing -- and the ``quality``, ``shell`` and
``e2e`` gates were re-run on them (below).

Existing assertions changed, each and why
=========================================

The brief names the GUI failure-path tests as the one place an existing
assertion changes. **Unit 8b wrote no test that asserted this case either
way** (its map notes say "measured and not asserted", 8b-D1), so there was no
8b assertion to strengthen: the GUI-level proof is the new
``SealFailureUiTest``, and failure paths 17 and 18 map the new tests in place.
These existing assertions did change, and tier 1 should know of each:

* ``EngineEdgeTest.anUnwritableManifestOrReportIsReportedAndTheRunStillFinishes``
  **pinned the defect** (``assertEquals(SUCCEEDED, ...)`` for a run whose
  manifest and report could not be written, and ``SUCCEEDED`` in ``run.json``).
  Replaced by the two tests above, which assert ``FAILED``. A strengthening.
* ``RunViewModelTest.failureStated`` and ``PercolatorRerunViewModelTest.endings``
  asserted the words "Its provenance could not be finalised: provenance.rst /
  provenance.json could not be written". A seal failure no longer arrives as a
  finalisation error, so each now feeds a non-seal finalisation error and
  asserts "Part of its record could not be written: ..."; the seal case has its
  own new test in each class.
* ``RunJsonTest.newerRefusedFirst`` and ``olderRefused`` and
  ``RunStoreTest.newerRunJsonRefused``: version 3 is now read, so "a newer
  version is refused before anything is read" is proved at version **4**, and
  the messages say "reads version 3". The same checks, moved to the first
  version that is now unreadable; not weakened.

Injections, each into production code
=====================================

All three into ``cometgui-workflow/.../engine/RunExecution.java``, each by an
exact one-occurrence replacement, the old anchor's ``grep -c`` 0 after it,
restored from a copy and ``sha256sum -c`` ``OK``. Made first on ``88383ce`` and
**again on the final code** (``23fd3b9``), with the same results; the numbers
are the final run's. Each ran ``EngineEdgeTest``, ``SealFailureRunTest``,
``FileSizeLimitRunTest`` (15 tests) and, for 1 and 2, ``SealFailureUiTest``
(3).

#. **The outcome fixed before the seal again** (the old ordering): the seal's
   failure only appended to ``recordingErrors``, ``run.json`` ended with the
   steps' outcome. **Red: 6 of 15 workflow tests and 3 of 3 GUI tests** --
   "expected: <FAILED> but was: <SUCCEEDED>" (six times), the outcome label
   "The run succeeded: run ... Part of its record could not be written: its
   provenance record ... could not be written: permission was denied ...", the
   Results label "..., succeeded", the Provenance label "..., completed, no
   sealed record" -- E12 exactly.
#. **A failed seal recorded as SUCCEEDED in run.json only** (the result still
   FAILED): ``recordEnd`` writing ``withAttemptFinished(SUCCEEDED)`` when the
   seal failed. **Red: 5 of 15 and 3 of 3** -- "expected: <FAILED> but was:
   <SUCCEEDED>" (from ``run.json``), "expected: <Optional[its provenance report
   ... could not be written ...]> but was: <Optional.empty>", ``run.json``'s
   ``outcome`` "expected: <failed> but was: <succeeded>", the Results label
   "..., succeeded". ``runJsonUnwritableToo`` stays green, rightly: its
   ``run.json`` cannot be written at all.
#. **The rerun path left on the old ordering while the ordinary run is
   repaired**: ``sealFailure`` taken as empty when the run is derived
   (``runIdentity().isDerived()``). **Red in its own test only: 1 of 15** --
   ``SealFailureRunTest.aPercolatorRerunWhoseRecordCannotBeWritten``, "a run
   whose record could not be written (8 failures)", first "expected: <FAILED>
   but was: <SUCCEEDED>"; the ordinary-run, ``run.json`` and engine tests (14)
   green.

Mutation testing
================

Targeted PIT, the project's Maven settings as ``dev-verify.sh`` invokes them
(``-B -o``, ``-Dcometgui.pit.threads=32``), no POM change:

* ``cometgui-domain``, ``-DtargetClasses=RunAttempt,RunDescriptor``:
  **63 generated, 63 killed (100 %)**, line coverage of the mutated classes
  134/134, no survivors.
* ``cometgui-workflow``, ``-DtargetClasses=RunExecution,RunExecution$Seal,
  RunResult,RunJson``, all of the module's tests, ``-DmutationUnitSize=2`` (a
  command-line property like the threads; without it PIT made three units, ran
  them three at a time and had not finished after 85 minutes):
  **195 generated, 190 detected (97 %)** -- 98 killed, 92 timed out (mutants
  that stop a run ending, caught by each test's own run bound), line coverage
  689/706. **Five not detected, all in** ``RunExecution``:

  - ``finish`` line 553, ``outcome != FAILED`` negated (the rewrite of a
    ``provenance.json`` written as a success skipped): SURVIVED with one test
    run. Applied by hand it is red in
    ``EngineEdgeTest.anUnwritableReportFailsTheRunAndTheRecordSaysSo`` --
    "provenance.json says the run failed ==> expected: <FAILED> but was:
    <COMPLETED>" -- so PIT ran another test for it;
  - ``plainly``: two SURVIVED (line 707, the blank-message test negated, and
    the return replaced by ``""``) and two NO_COVERAGE (lines 703-704, the
    permission branch). Their callers are the real runs and the child JVM of
    ``FileSizeLimitRunTest``, where PIT's mutant is not loaded. Repaired in
    ``ac8f00e`` by ``SealFailureWordsTest`` (5 tests, every branch).

  Confirmed after it: ``-DtargetClasses=RunExecution``,
  ``-DtargetTests=org.cometgui.workflow.engine.*``: **115 generated, 115
  killed (100 %)**, no coverage gap.

Both runs rewrite their module's ``target/pit-reports``; ``build.sh``
regenerates every module's report, so tier 1's exit-gate run is unaffected.

Gates
=====

The brief's list, plus ``shell``, whose harness injects into
``StageStepper.java`` (``StageStepper`` changed). No other harness names a
changed production file: ``workflow``, ``percolator`` and ``provenanceui``
inject into ``RunExecution.java``, ``limelight`` into ``LimelightViewModel.java``
-- every anchor was checked to be still present, and the ``percolator`` one
(``Map<String, String> settings = ...`` in ``finish``, using ``outcome``) was
not until ``23fd3b9`` kept it. ``params`` and ``paramui`` read nothing changed.

First run, on ``ac8f00e``
(``verify-all-gates.sh --only workflow --only provenance --only provenanceui
--only percolator --only limelight --only results --only quality --only e2e
--only shell``):

.. list-table::
   :header-rows: 1
   :widths: 16 12 12 60

   * - Gate
     - Result
     - Controls
     - Note
   * - quality
     - **FAIL**
     - 40/42
     - The baseline SpotBugs run refused the tree: ``RunDescriptorTest`` ignored
       ``withAttemptFailed``'s return inside ``assertThrows``
       (``RV_RETURN_VALUE_IGNORED_INFERRED``). Root cause repaired in
       ``cb00de5``, with the two further findings a SpotBugs run over every
       touched module then showed (``StageStepper`` storing its view-model,
       ``EI_EXPOSE_REP2``; ``SealFailureUiTest`` writing static fields from a
       test method). Re-run below.
   * - shell
     - PASS
     - 30
     - 611 s
   * - provenance
     - PASS
     - 24
     - 241 s
   * - workflow
     - PASS
     - 110
     - 520 s
   * - percolator
     - PASS
     - 109
     - 827 s; its control "the seed recorded only on success" injects into
       ``finish`` and bit
   * - results
     - PASS
     - 100
     - 694 s
   * - limelight
     - PASS
     - 130
     - 1 166 s
   * - provenanceui
     - PASS
     - 96
     - 1 255 s
   * - e2e
     - PASS
     - 50
     - 2 234 s (1 266 s at Phase 14's exit). Control 4 graded **69** mapped
       test ids, 86 test cases, 25/25 scenarios; the new tests add about a
       minute of test time, the rest is this host's load.

Second run, on ``cb00de5`` (``--only quality --only shell --only e2e``), the
gates whose input changed after the first:

.. list-table::
   :header-rows: 1
   :widths: 16 12 12 60

   * - Gate
     - Result
     - Controls
     - Note
   * - quality
     - PASS
     - 42
     - 140 s
   * - shell
     - PASS
     - 30
     - 604 s
   * - e2e
     - **FAIL**
     - 10/11 run
     - 1 334 s. Tier A's ``pdv`` step waited its 15 minutes on "PDV 2.7.0 is
       being installed" -- the real download from GitHub, which this tier
       uses by design, never finished; nothing of the PDV install path
       changed here, and the same step passed in the first run and the next.
       Re-run.

Third run, on ``cb00de5`` (``--only e2e``): **FAIL**, 2 797 s. Tier A passed
(23/23); control 4's failure-path suite failed one test,
``RealAwkwardPathRunTest#longPaths`` (Phase 14 unit 8a's): "the spectrum
file's path is 3998 ==> expected: <true> but was: <false>". **Root cause**: it
builds the path from 255-byte directories under ``@TempDir`` and asserts at
least 4 000 bytes, but where the loop stops depends on the temporary
directory's own length, which JUnit draws at random -- one character shorter,
3 998. Repaired in ``4b17931`` (owner's fix-red-and-continue rule): one shorter
directory takes the path to exactly ``LINUX_PATH_MAX - 40`` = 4 055 bytes;
the assertion is unchanged; 2/2 after it.

Fourth run, on ``4b17931`` (``--only e2e``): **PASS, 50 controls, 1 575 s**.
Control 4: the failure-path suite ran 88 tests in 37 classes, 0 failed;
``check-failure-paths.sh --reports ... --scope all`` PASSED, 25/25
scenarios, all 69 mapped ids graded, 86 test cases ran, 86 passed.

**Harness cost.** No harness was added or changed. The ``e2e`` gate's control
4 now also runs the new mapped tests -- ``SealFailureRunTest`` (about 39 s),
``SealFailureUiTest`` (about 13 s), ``FileSizeLimitRunTest``'s new method
(about 2 s) and ``EngineEdgeTest``'s two -- about a minute.

``docs`` and ``traceability`` (``--only docs --only traceability``): PASS (1
and 8 injected defects caught) on the tree with this log's first draft, and
again on its final text, below.

``bash scripts/e2e/check-failure-paths.sh`` (no reports): "(a) 25 scenario(s)
in the specification, 25 in the map; (b) 69 distinct test id(s) checked ...: 0
not found or not a test; PASSED".

``--only tests`` was **not** run (the brief); ``bash
scripts/verify-test-gates.sh --preflight``: PASS.

Escalated
=========

* **run.json schema version 3** (above): a format change the brief's
  requirement implies; ``specification.rst`` revision 21 is tier 1's to write.
* **A cancelled run whose seal fails ends FAILED**; **the report is part of
  the seal** -- decisions inside the brief's words, for confirmation.
* **The disk-full case cannot leave the event in the log** under
  ``RLIMIT_FSIZE``: the event log is larger than ``provenance.json`` by the
  time of the seal, so a limit that refuses the record refuses the log too.
  The result states both refused appends; ``run.json`` carries the reason. A
  real full disk would refuse all three; the product writes each "as far as
  the disk allows".
* **E14 still holds** for the reopened ``run.json``-unwritable case: the run
  is listed *interrupted* (asserted), and its summary status word is E14's,
  asserted only to be not ``completed``.
* **Commit hygiene**: ``88383ce`` was committed with the two module
  directories as its pathspec after an exact ``git add`` of each file; it
  contains only those files (``git show --stat``), but the rule is exact paths
  and the later commits follow it.
