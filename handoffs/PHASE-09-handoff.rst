===================================================================
PHASE-09 handoff -- Percolator Adapter and Version Capabilities
===================================================================

:Phase: 09
:Written: 2026-10-08 by the Phase-09 orchestrator (tier 2)
:Outcome: **All nine work units accepted** (no unit sent back whole; four
   integration repairs made by the orchestrator, each recorded). Every exit-gate
   item is met **on Linux x86-64**, the only platform any of it has executed
   on; item 9 is met for every operation that exists today -- parsing,
   filtering, provenance finalisation and the compatible-version rerun -- and
   export does not exist until Phase 10. Recommended grade **PARTIAL**, on
   platform residue and item 9's export half (see :ref:`p09h-residue`). **Not
   yet signed off by tier 1**, whose ``scripts/build.sh`` and full
   ``scripts/verify-all-gates.sh`` run has not happened; nobody in this phase
   ran either, by the owner's build-economy rule.
:Records: ``handoffs/PHASE-09-worklog.rst`` -- **this page is the map; the work
   log is the proof.** Every sign-off there names the commands run, the counts
   seen and the injections made into production code, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

Run now includes Percolator. The one capability probe establishes every
Percolator capability by execution (eleven runs over the 64+64 synthetic PIN,
each capability on its own observable); the default build is *resolved* from
observed capabilities and the enabled downstream stages -- with Limelight on,
the newest build that can write XML (3.07.1 today), with it off, the newest
build (3.09 where it runs) -- and the skipped versions are named with the
capability they lack, on screen and in provenance. The command builder emits an
option only when the build's probed capability allows it, so 3.09 is never
handed ``-X``; a zero-decoy PIN is refused before any process starts; outputs
are parsed by the one parser set (``results.parser``, which Phase 10 builds
on), made read-only, and recorded with the effective seed. A compatible-version
rerun is a new *derived* run that reuses the original's merged PIN and never
runs Comet. A new harness, ``scripts/verify-percolator-gates.sh``
(``percolator``, 94 controls, about 10.5 minutes), proves every gate item fails
on a production defect, three of them version-blind.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 26 68

   * - Unit
     - Commits
     - What
   * - 1
     - ``62848df`` (+ ``c20f900``)
     - ``tools.percolator``: the probe extended (``PercolatorOption``,
       ``ProbeArtefacts``); ``domain.tools``: ``TEST_FDR_OPTION``,
       ``TRAIN_FDR_OPTION``, ``MAX_ITERATIONS_OPTION``; ``install.cache``:
       ``capabilityProbeGeneration`` 2 in the completion marker, an older
       marker verifies as ``CAPABILITIES_FROM_AN_EARLIER_PROBE``.
   * - 2
     - ``aca45ea``
     - ``params.percolator``: ``PercolatorSettings`` and validation
       (``TestFdr``/``TrainFdr`` their own types), ``PercolatorSetting``,
       ``EffectiveSeed``; ``resolution``: ``DownstreamStage``,
       ``PercolatorResolver``, ``PercolatorResolution``, ``ResolutionChange``,
       ``ResolutionMessages`` (all words in one place), ``AdvisoryRendering``.
       The module's own mutation switch on.
   * - 3
     - ``f255fd9``
     - ``tools.percolator``: ``PercolatorCommands``/``PercolatorRequest``/
       ``PercolatorCommand``/``PercolatorArtefact``, ``PercolatorPinCheck``
       over ``CometPinValidator.validateBeforePercolator`` (no second PIN
       parser).
   * - 4
     - ``f2ef533``
     - ``results.parser``: ``ResultTableReader``, ``WeightsReader``
       (split count from the file); ``results.filtering``: the display q-value
       filter values (0.01/0.01, ``[0, 1]``, inclusive). Real 3.06.5/3.07.1/3.09
       output over CometGUI's own synthetic PIN checked in with SHA-256 pins.
       The module's own mutation switch on.
   * - 5
     - ``88a5a8e`` ``2459a08`` ``3276e30`` ``5579fa2``
     - ``workflow.steps``: ``resolve-percolator``, ``run-percolator``,
       ``parse-percolator``; ``PercolatorSelection``, ``PercolatorChoice``,
       ``SearchRequest.withPercolator``; ``parameters/percolator-settings.json``
       written once; settings keys in ``PercolatorProvenance``; outputs made
       read-only.
   * - 6
     - ``6ff6a00``
     - ``PercolatorRerun`` and the derived run; ``Plan.covering(wanted,
       provided)``; ``run.json`` schema version 2 (= 1 plus ``derivedFrom``;
       ordinary runs still version 1); ``rerun.*`` provenance keys.
   * - 7
     - ``7b2614d`` ``21127d2`` ``7870cbe`` ``97881a9`` (+ ``ec85a0a``)
     - ``cometgui-ui``: the Percolator section (``viewmodel.percolator``,
       ``controls.percolator.PercolatorPane``, 34 pinned identifiers), the
       Percolator half of the one Run readiness; ``cometgui-app``: the ports,
       ``WorkflowRunPort`` running Percolator and the rerun.
   * - 8
     - ``72296dd``
     - ``docs/percolator.rst``, ``docs/reference/percolator_options.rst``,
       ``docs/developer/version_capabilities.rst``; stale statements corrected
       on six pages; the traceability map's AC-RES-05/06/07 and AC-PRV-10.
   * - 9
     - ``ce420f4`` (+ ``05abc88``, ``d701453``)
     - ``scripts/verify-percolator-gates.sh``, registered as ``percolator``
       (floor 94).

Orchestrator commits: ``7ace9f5`` (work log, P9-1..P9-14), one sign-off commit
per unit, and the four repairs: ``c20f900`` (two app install tests pinned the
old probe's capability row), ``ec85a0a`` (the keyboard walk reports pane and
focus together, so ``shell`` control 1b's focus diagnostic comes from the walk
itself), ``05abc88`` (the recorded skip reason asserted word for word),
``d701453`` (control 4 grades that stronger assertion).

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-domain`` (three capability constants, ``RunDerivation``,
``RunIdentity``/``RunLayout``); ``cometgui-install`` (the probe generation);
``tools.comet`` (``CometPinValidator.validateBeforePercolator``,
``PinReader``); ``workflow.state.Plan``; ``workflow.storage.RunJson``; two
POMs gained **only** their module's own ``cometgui.mutation.skip=false``
(``cometgui-params-percolator``, ``cometgui-results``), as ``scripts/build.sh``
requires once a PIT-critical package compiles a real class -- **tier 1 should
confirm this reading of the brief's "never change the POM's PIT
configuration"**; ``scripts/verify-all-gates.sh`` (additive). ``manifests/tools.json``
is unchanged (3.09 still has no Linux row).

Exit gate
=========

Every check below was run by me; the work log has each injection's failure
text. All real-binary evidence is Linux x86-64.

.. list-table::
   :header-rows: 1
   :widths: 5 14 81

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux)
     - ``RealPercolatorRunTest.gate1And2WithAnXmlCapableBuild``: Comet
       2026.03.0 on the two K562 mzML then real Percolator 3.07.1 through the
       engine; PSM 3285 rows = merged-PIN targets, decoy PSM 3187 = PIN
       decoys, 2482 peptides, weights 3 splits / 22 features, pout 3285 psm.
       Red: ``percolator`` 1a (a row dropped), 1b (artefact existence
       unchecked).
   * - 2
     - Met (Linux)
     - Asserted on the argv read back from ``provenance.json``: 3.07.1 with
       Limelight has ``-X .../pout.xml``; 3.09 (rpm binary + Boost 1.66,
       registered local) has none and writes no ``.xml``, with Limelight off
       and on (``not-emitted.01.option=-X``, ``XML_OUTPUT`` reason); 3.07.1
       with Limelight off has no ``-X``. Red: ``percolator`` 2a, 2b.
   * - 3
     - Met
     - ``PercolatorResolverTest``, ``ResolutionChangeTest``,
       ``PercolatorSectionUiTest.gate3`` (toggle notice "changed from 3.09
       (registered local binary) to 3.07.1 because Limelight conversion was
       switched on and needs XML_OUTPUT" and back), ``PercolatorUnavailableUiTest``
       (no observed XML-capable build: non-XML default, Limelight unavailable,
       both remedies). **Version-blind** red: ``percolator`` 3v, 3w, 3u -- the
       real 3.07.1/3.09 pair cannot tell a version rule from a capability
       rule; the future-version (3.10) and inferred-claim tests do.
   * - 4
     - Met
     - UI: ``PercolatorSectionUiTest.gate4TheSkipReasonAndTheAdvisories``;
       provenance: ``RealPercolatorRunTest.gate4TheSkippedVersionIsRecorded``
       (now word for word, ``05abc88``) -- the same sentence from
       ``ResolutionMessages``. Red: ``percolator`` 4.
   * - 5
     - Met (Linux)
     - ``RealPercolatorRunTest.gate5TheRealZeroDecoyPin``: the real zero-decoy
       PIN (Comet 2026.02.2, fragment-ion index, ``decoy_search = 1``: 198
       targets, 0 decoys) refused by ``run-percolator`` naming the decoy
       configuration, zero Percolator launches counted around the real
       process service; also refused at Comet validation in a full run.
       Red: ``percolator`` 5; my unit 5 injection.
   * - 6
     - Met (Linux)
     - ``RealPercolatorRerunTest`` and ``PercolatorRerunUiTest``: 3.09 run,
       then the rerun with 3.07.1 -- a second run whose execution record
       differs in version, binary SHA-256 and argv (``-X`` added); Comet
       launches unchanged; merged PIN equal to the recorded SHA-256; the
       original's Comet tool records and ``provenance.json`` bytes unchanged.
       Red: ``percolator`` 6a, 6b, 6c.
   * - 7
     - Met
     - ``RealPercolatorRunTest.gate7TheSeedOfEveryRun`` and the failed-run
       tests: ``percolator.seed`` in every run's ``provenance.json``,
       including runs refused before launch; the JVM locale recorded. Red:
       ``percolator`` 7; my unit 5 injection.
   * - 8
     - Met
     - ``WeightsReaderTest``: real three-split files (3.06.5, 3.07.1, 3.09) and
       constructed two- and four-split files parse with 3, 2 and 4. Red:
       ``percolator`` 8a, 8b.
   * - 9
     - **Partial**
     - Met for every operation that exists: SHA-256 of each raw output equal
       before and after parsing, filtering (``results.filtering``), provenance
       finalisation and the derived rerun, and each output read-only after
       success (``gate9RawOutputsAreUnchangedAndReadOnly``,
       ``rawFilesUntouched``, ``gate9TheOriginalIsUntouched``). Red:
       ``percolator`` 9a, 9b. **Export does not exist until Phase 10**, whose
       own gate item 4 must re-prove this for export.

**Harness runs on the final tree**: ``percolator`` **94** (631 s),
``workflow`` 110, ``install`` 95, ``paramui`` 87, ``shell`` 30, ``quality``
42, ``docs``, ``traceability`` 8 -- each PASS, on the last commit that changed
what it reads. ``--only tests`` **37** (7490 s): see :ref:`p09h-tests`.

.. _p09h-tests:

The deferred ``tests`` harness
==============================

Run by me at the end of the phase, on ``7bbeb2a`` (the last code commit),
after regenerating every module's PIT report exactly as ``scripts/build.sh``
does (``mvn test-compile org.pitest:pitest-maven:mutationCoverage`` at the
root, 28 min; domain 666 mutations, tools 637, params-percolator 145/145,
results 181, workflow 242/242, install 1412, params-comet 1763, provenance
775, process 175) -- because this phase's targeted PIT runs had left partial
reports, Phase 08's trap.

``bash scripts/verify-all-gates.sh --only tests``: **PASS, 37 assertions in
7490 s** (about 125 minutes, up from 95 at Phase 08 -- the new real-binary
Percolator tests and their PIT run inside its ``build.sh`` sandbox). **It no
longer fits one two-hour background job**: my first attempt was stopped by
that limit inside its last control, with every assertion so far passing; the
second ran detached (``setsid nohup``) and was watched to its exit. Tier 1
must run it the same way.

Every new test that reads outside its module reads only under ``scratch/``
(``phase05/artefacts``, ``fixture``, ``percolator/3.09``), which the ``tests``
sandbox links whole; the ``paramui`` sandbox carries no ``scratch/``, which is
why unit 7 made ``RunReadinessUiTest`` offer a never-run Percolator.

.. _p09h-residue:

Incomplete, deferred, residue
=============================

* **Platform.** Every real-binary test is ``@EnabledOnOs(LINUX)``. No Windows
  or macOS Percolator was executed by this phase; the DOS read-only branch of
  ``makeReadOnly`` has never run. 3.09 ran on Linux only as an unmanaged
  binary -- the upstream ``.rpm`` executable with Boost 1.66 libraries from
  CentOS 8.5 (``scratch/percolator/3.09``); the manifest still has no Linux
  3.09 row (``D-003``).
* **Item 9's export half** (Phase 10).
* **On a Mac before 3.07.1 is installed**, Limelight on resolves to 3.09 with
  Limelight unavailable, because 3.07.1's XML claim there is inferred from
  bytes (``R-TOOL-08``); installing 3.07.1 probes it. Documented on the user
  page.
* ``finalise-provenance`` is ordered only after ``merge-pin`` while
  ``finalise-results`` (Phase 10) is unplanned, so the rerun preview reuses it
  after a Percolator change -- harmless today; Phase 10 restores the order.
* Not built: the ``R-PERC-08`` stdout weights fallback (the run records the
  warning); the specification's train-subset, search-input and decoy-prefix
  Advanced settings; a duplicate guard on the rerun action; a stepper during
  a rerun; retry of a Percolator run across a restart.
* The view-model parses the integer settings with ``Integer.parseInt``
  (``PercolatorSettings`` has no text reader; range messages are still the
  model's).
* ``PercolatorCapabilityProbe``'s second-constructor Javadoc still says
  R-PERC-02 calls 8+8 insufficient (revision 11 reworded it).
* ``.gitattributes`` has no ``-text`` rule for ``cometgui-results``' pinned
  fixtures; a Windows checkout with ``core.autocrlf=true`` would fail their
  SHA-256 checks (tier 1's file).
* An older completion marker (generation 1) makes the build not installed:
  re-probing means a re-download.

Decisions encountered
=====================

No ``D-`` item was answered. Design decisions P9-1..P9-14 are in the work log.
Judgement calls a later phase may revisit: a managed ``INSTALLING`` offer is a
resolution candidate; ``-Z`` is never passed (Phase 00: the converter fails on
``-X -Z`` without ``--import-decoys``, which Comet's internal decoys cannot
use -- Phase 12 to confirm); a derived run is ``run.json`` schema version 2
and cannot itself be rerun; the seed default is 1, threads 3, maxiter 10
(Percolator's own); FDR 0 refused (``--trainFDR 0`` is a sentinel).

Surprises
=========

* **Percolator posts usage analytics by default** (``GoogleAnalytics::postToAnalytics``
  in the binary; ``--no-analytics`` exists in 3.07.1 and 3.09). Every
  Percolator run CometGUI starts -- probe and tests included -- may contact
  Google Analytics. Escalated, not acted on.
* **The real 3.07.1/3.09 pair cannot distinguish a version rule from a
  capability rule**; the proof that nothing branches on a version number
  rests on future-version and inferred-claim fixtures and the version-blind
  controls. Nothing enforces it statically.
* **TIMED_OUT mutants in ``cometgui-workflow``** (43 of 99 in the steps) became
  KILLED when PIT was restricted to the fast unit tests: they look like
  real-binary test classes' setup exceeding PIT's per-test timeout, not hangs
  -- bears on the owner's ``TIMED_OUT`` question.
* 3.09's banner reads ``3.09.0``, so a registered 3.09 is shown as 3.09.0 on
  screen and 3.09 in the parsed version.
* 3.09's tables differ from 3.07.1's only in ``posterior_error_prob``
  (I-spline PEP); the formats are identical.

Escalated (none answered here)
==============================

#. **``--no-analytics``**: whether the product passes it. If yes: one
   capability constant, one probe observable, one builder line.
#. **The two module mutation switches** (``cometgui-params-percolator``,
   ``cometgui-results``) -- confirm they are the documented mechanism and not
   "the POM's PIT configuration".
#. **Specification amendment**: the run layout's
   ``percolator.{stdout,stderr}.log`` is one stream-tagged
   ``logs/percolator.log``, as revision 14 adopted for Comet.
#. **Add ``org.cometgui.workflow.*`` to PIT targets** (carried from Phase 08;
   this phase's steps would join it) and the ``TIMED_OUT`` question above.
#. **``.gitattributes``** for the pinned fixtures (above).
#. **Cost**: ``percolator`` adds about 10.5 minutes to the full gate run; the
   new real-binary tests add roughly 2-3 minutes to ``build.sh``.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts``, ``scratch/fixture`` and
``scratch/percolator/3.09`` are present (the new real-binary tests fail, not
skip, without them), run ``bash scripts/build.sh``, then the full
``bash scripts/verify-all-gates.sh`` (now 16 harnesses with ``percolator``,
floor 94). Then decide the escalations above.

**Phase 10:** build on ``org.cometgui.results.parser`` (``ResultTableReader``,
``WeightsReader``) and ``results.filtering`` (the display filter values) --
do not write a second parser; plan ``finalise-results`` so
``finalise-provenance`` is again ordered after Percolator; re-prove gate item
9 for export. Read ``docs/developer/version_capabilities.rst`` and the
Percolator section of ``docs/developer/workflow_engine.rst``.
