===================================================================
PHASE-14 handoff -- GUI Automation and Packaged End-to-End Harness
===================================================================

:Phase: 14
:Written: 2026-10-23 by the Phase-14 orchestrator (tier 2)
:Outcome: **All work units accepted** (1, 2, 2b, 3-7, 8a, 8b, 9, 10; one
   rework unit 6r). Every exit-gate item is met **on Linux x86-64**, the
   reference platform and the only one anything here has executed on.
   Recommended grade **PARTIAL**: platform residue (no macOS or Windows
   packaging or execution), Tier B step 25 drawing not provable on this host
   (E6), and two specification steps the product does not do as written (E1,
   E2). **Not yet signed off by tier 1**; nobody in this phase ran
   ``scripts/build.sh`` or the full ``scripts/verify-all-gates.sh``.
:Records: ``handoffs/PHASE-14-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off there names the commands run, the
   counts seen and the production-code injections made, with their failure
   text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

The product is now driven end to end the way a scientist drives it, twice:
**Tier A** runs the real application in-process over a fresh home with a
space and an accent in its path, installs the tools from their pinned GitHub
releases through the interface, searches the ``D-006`` local fixture with
real Comet and Percolator, filters, draws a PDV figure, converts and uploads
to the ``D-007`` fake, verifies provenance against the disk with its own
digests, closes and relaunches; **Tier B** does the same against the exact
``jpackage`` archive, installed read-only outside the repository and launched
with no Java on ``PATH``, through a test-only loopback bridge attached to the
unmodified image. The no-XML scenario runs in both tiers. All 25 failure-path
scenarios are mapped to passing tests by a checker that re-reads the
specification. One harness, ``e2e``, grades all seven gate items, including
three production injections and its own self-controls. Building it found
fifteen product issues; two were repaired here (E3, E4), the rest are
escalated.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 7 25 68

   * - Unit
     - Commits
     - What
   * - 1
     - ``85f0f19``
     - ``cometgui-app/src/test/java/org/cometgui/app/e2e/`` (``support``:
       ``@EndToEnd`` opt-in, ``RunnerLocale``, ``FreshHome``; ``oracle``:
       params, PIN, q-value counts, weights, directory snapshot);
       ``uidriver/RemotableUiDriver`` and ``Await``;
       ``scripts/e2e/check-no-fixed-sleep.sh``.
   * - 2, 2b
     - ``f8b80c2`` ``64d5144``
     - Tier-neutral ``e2e/scenario/CanonicalScenario`` steps 1-24 and
       ``tiera/TierACanonicalScenarioTest``; **product repair E3** (one field
       in ``CometGuiApplication``) with ``InstallReachesTheSectionsUiTest``.
   * - 3
     - ``9b4a4a7`` ``031748b``
     - Steps 25-30 (``DownstreamSteps``), oracles ``BestTargetPsm``,
       ``LimelightXml``, ``FigureImage``; **product repair E4** (orchestrator)
       in ``LimelightConverterIdentity``.
   * - 4
     - ``adec5c5`` ``3f96c12``
     - ``scenario/NoXmlScenario``, ``tiera/TierANoXmlScenarioTest``,
       ``TierAApplication``.
   * - 5
     - ``a717cdc``
     - ``packaging/jpackage/{common,linux,macos,windows}.args``,
       ``packaging/runtime-modules.txt``; ``scripts/package/``
       (``build-app-image.sh``, ``install-archive.sh``,
       ``smoke-installed.sh``, ``x11_windows.py``).
   * - 6, 6r
     - ``b20c6aa`` ``6b77a54``
     - ``e2e/bridge`` (Java agent), ``e2e/tierb`` client side,
       ``scripts/e2e/build-bridge.sh``; ``scripts/ci/release-no-test-bridge.sh``
       + ``no_test_bridge.py`` replacing the Phase 01 stub. 6r: SpotBugs
       findings in units 1-4's test code repaired (orchestrator).
   * - 7
     - ``50b9921``
     - ``tierb/TierBCanonicalScenarioTest``, ``TierBNoXmlScenarioTest``,
       ``TierBLocaleControlTest``, ``TierBApplication``, ``PdvOnThisHost``.
   * - 8a, 8b
     - ``b140915`` ``d4e8bd7`` ``6f9e3df`` ``a8faba4``
     - ``scripts/e2e/failure-paths.toml``, ``check-failure-paths.sh``,
       ``failure_paths.py``; new failure-path tests in ``cometgui-install``,
       ``cometgui-workflow`` and ``cometgui-app`` (network, missing PIN,
       deleted input, Unicode and long paths, Percolator cancel, unwritable
       directory, file-size limit, interrupted run, second instance).
       **POM**: ``cometgui-workflow`` surefire gets ``LANG``/``LC_ALL=C.UTF-8``
       as ``cometgui-process`` already has.
   * - 9
     - ``0bda6c5`` ``81ac797`` ``76b18c9`` ``5f016e7``
     - ``scripts/verify-e2e-gates.sh``, registered additively as ``e2e``
       (floor **50**); ``stub-lib.sh`` optional arguments; the release and
       nightly GUI stubs re-worded (owner kept PHASE-14); the ``D-007``
       nightly slot (``nightly-limelight-sandbox.sh``, ``nightly.yml``);
       ``LimelightSandboxUploadTest``.
   * - 10
     - ``114383e``
     - ``docs/developer/e2e_harness.rst``, ``testing.rst``, ``index.rst``,
       ``releasing.rst``, ``traceability-map.toml``.

**Product changes**: E3 (``CometGuiApplication``, a held wrapper) and E4
(``LimelightConverterIdentity``, an environment). **POM change**: one,
``cometgui-workflow/pom.xml`` (surefire locale). **No PIT configuration
changed, no floor lowered, no survivor pinned, no exclusion added.** New
harness ``e2e``; ``verify-all-gates.sh`` changed only by that registration.

Exit gate
=========

All evidence Linux x86-64, on runs I made myself; the work log has each
injection's failure text.

.. list-table::
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux), with residue
     - ``TierACanonicalScenarioTest`` 23/23 from an empty home and cache
       through provenance verification and relaunch; ``e2e`` control A.
       Relaunch is a new composition root in the same JVM (P14-4); Tier B's
       is a real process restart. **Residue**: Comet and Percolator are
       installed through the Tool Manager, not by Run (E1); the project is the
       product's default one (E2); step 10 sets top 1 for the converter (E5).
   * - 2
     - Met (Linux), step 25 partial
     - ``TierBCanonicalScenarioTest`` 24/24 against the archive installed
       read-only outside the repository, no java on ``PATH``, launched
       process reporting ``sun.jnu.encoding=UTF-8``; tools downloaded into
       the fresh home, install tree unchanged, the converter run on the
       installed ``bin/java``; ``smoke-installed.sh`` S1-S11 and its
       self-test in ``e2e`` control P. **Step 25**: PDV installs and launches
       from the installed runtime but cannot draw on this host (no system
       ``libX11``; E6). My injection: results ``<=`` to ``<`` built into
       the image -- red at the boundary step.
   * - 3
     - Met (Linux)
     - ``TierANoXmlScenarioTest`` 15/15 and ``TierBNoXmlScenarioTest`` 16/16,
       each with ``cometNotRerun`` proved from disk (bytes, inode, mtime) and
       cross-checked with the product's records; ``e2e`` control 3i (the
       rerun touching Comet's outputs) red on the disk assertion. 3.09 is a
       registered local binary (no Linux managed build).
   * - 4
     - Met (Linux)
     - ``check-failure-paths.sh``: 25/25 scenarios equal to the
       specification's paragraph, 61 test ids, every mapped test run fresh and
       passed (``e2e`` control 4); ``--self-test`` 22 controls. Limits stated
       in the map: 18 simulated with RLIMIT_FSIZE (EFBIG, not ENOSPC); 21 is
       Linux limits only.
   * - 5
     - Met
     - ``e2e`` controls 5q (``QValueFilter`` ``<=`` to ``<``, red at the
       boundary step -- the specification's own cutoffs hit no row exactly,
       so the scenario adds one at a q-value in the file), 5p (the FASTA
       dropped from ``provenance.json``, red at step 28), 5b (the upload's
       final submit skipped while the screen says submitted, red at step 27).
       Each proved in the bytecode and graded on its own words.
   * - 6
     - Met
     - ``check-no-fixed-sleep.sh`` over compiled classes and sources (153
       classes), ``--self-test`` 14 controls; my ``Object.wait`` injection
       caught. The only timed wait is ``Await``'s poll.
   * - 7
     - Met
     - ``release-no-test-bridge.sh``: clean archive PASS (14 JARs, 1511
       classes, 29 modules inspected); ``--self-test`` 9 bridge-included
       variants rejected; my variant (bridge classes in ``lib/extras.zip``)
       rejected with 22 findings.

**Harness runs, by me, on the last commit that changed what each reads**:
``e2e`` 48 (1084 s, ``76b18c9``) and control P after the floor rose to 50
(``5f016e7``); ``pipeline`` 25 steps, ``workflows`` 23 (``76b18c9``);
``workflow`` 110, ``install`` 95, ``quality`` 42, ``paramui`` 87,
``provenanceui`` 96 (``a8faba4``); ``limelight`` 130 (``031748b``); ``docs``,
``traceability`` (``114383e``). ``--only tests``: see `The deferred tests
harness`_.

.. _p14h-tests:

The deferred tests harness
==========================

To be completed at the end of the phase: every module's PIT report
regenerated exactly as ``scripts/build.sh`` writes it (targeted PIT runs
overwrote some), then ``bash scripts/verify-all-gates.sh --only tests``
detached and watched to its exit line. Result recorded here and in the work
log.

Tests of this phase that read outside their module (all already read by
earlier tests, or opt-in): ``manifests/tools.json``; ``scratch/fixture``;
``scratch/phase05/artefacts``; ``scratch/percolator/3.09``;
``scratch/scientific-path/limelight/limelight.xml``
(``LimelightSandboxUploadTest``); ``tools/x11-bookworm-20260829``,
``tools/fontstack-bookworm-20260829``,
``scripts/feasibility/gui-spike/xkbcomp-path-shim.c`` (opt-in classes);
``_build/package/linux`` and ``scripts/package/*`` (opt-in Tier B). None reads
a project document.

What waits on D-006
===================

The whole CI half. The end-to-end tier searches the ``D-006`` **local**
fixture (two K562 mzML files, the UniProt human proteome, fetched by checksum
into gitignored ``scratch/``); it was never committed and is not presented as
the CI fixture. Therefore ``release-e2e-canonical.sh``,
``release-e2e-limelight.sh``, ``release-e2e-noxml.sh``, ``release-smoke.sh``
and ``nightly-gui-tests.sh`` stay **stubs** (exit 70, owner PHASE-14), each
saying what it waits for: ``D-006``'s CI fixture, Phase 16's
``release-package.sh`` (still a stub), and a display on the runner. A CI job
would need: the CI fixture fetched by checksum, the release archive installed
outside the checkout, Xvfb and the font stack, network to the tools' GitHub
release hosts, and the ``D-007`` fake on loopback. ``release-no-test-bridge.sh``
needs no fixture and is a real release step now.

Packaging problems found, and how each was resolved
===================================================

#. **``jpackage`` strips ``bin/java``** -- kept with ``--jlink-options``;
   smoke S2/S4 prove the installed runtime runs the converter JAR; Tier B
   ran the converter and launched PDV on it.
#. **Runtime modules**: jdeps on the app alone misses what the converter and
   PDV need; ``packaging/runtime-modules.txt`` lists 29 with reasons. A
   dropped ``java.logging`` passed ``build-app-image.sh`` and failed only in
   Tier B (residue: the build's own check covers the app's needs only).
#. **Native-access warning** at every start -- ``--enable-native-access``
   for ``javafx.graphics`` as well as ALL-UNNAMED.
#. **Locale**: ``-Dsun.jnu.encoding`` is inert and is refused by the build;
   the harness exports ``LC_ALL=C.UTF-8`` and asserts the launched process's
   encoding; with no locale the assertion fails loudly
   (``TierBLocaleControlTest``). The launcher repair is Phase 16's; under a
   non-UTF-8 locale an accented ``XDG_DATA_HOME`` is silently dropped (E8).
#. **Non-ASCII install paths**: the converter's identity probe ran with an
   empty environment and could not open its JAR under an accented home --
   repaired (E4).
#. **Fonts under a fresh ``XDG_DATA_HOME``** on this host -- test
   environment only (a generated ``FONTCONFIG_FILE``).
#. **A failed start leaves an invisible process** (E7) -- escalated.
#. **Install-layout paths**: none differed once installed; the install tree
   was unchanged after every Tier B launch; nothing was written to the
   launch directory.

Injections used
===============

Mine, each into production code, each restored and verified with
``sha256sum -c``: ``<=`` to ``<`` in ``IndependentCounts`` (unit 1, test
oracle); ``Object.wait`` in ``PinFile`` (sleep check); ``PinMerger`` dropping
a row (Tier A step 19); the listener's ``percolator.refresh()`` removed (E3
test); an empty environment in ``LimelightConverterIdentity`` (E4 test); the
FASTA dropped by ``ManifestWriter`` (step 28); ``PercolatorRerun.copy``
touching its source (no-XML step 5); ``libglassgtk3.so`` removed from an
install (smoke S6-S9); bridge classes in ``lib/extras.zip`` (release check);
``QValueFilter`` ``<=`` to ``<`` in the packaged image (Tier B);
``RunExecution.firstAbsent`` fooled by a directory (failure path 6); a
cancelled run-percolator ended FAILED (failure path 14, red by the run
bound); a harness copy expecting the wrong words for 5b (HARNESS FAILURE).
The agents' injections are in the work log.

Incomplete, deferred, residue
=============================

* **Platform**: macOS and Windows packaging argfiles never executed (macOS
  likely rejects version ``0.1.0``); AC-TST-07 and AC-INS-01 are Linux only.
* **Tier B step 25** (PDV drawing) unprovable on this host (E6).
* **AC-INS-01 and AC-TST-05 keep a "planned, phase 14" traceability
  entry**: their remainder (macOS/Windows; AC-TST-05's GUI list not audited
  item by item, and the learned-weights *chart* it names does not exist) has
  no later owner, and the generator rejects a criterion planned for a PASSED
  phase.
* The relaunch in Tier A is in-process (P14-4).
* ``scripts/e2e/`` sleep check and harness are Linux only.
* An intermittent in ``paramui`` control 2v (``VariableModificationEditorUiTest``),
  seen once, passing alone 3/3 -- not this phase's code.

Decisions encountered
=====================

No ``D-`` item was answered. ``D-006``'s CI half shaped every CI decision
above. ``D-007``'s nightly slot is wired and red while unconfigured; the
submit key has no route into CI because ``check-workflows.py`` forbids
``secrets.`` (an owner decision). Design decisions P14-1..P14-14 are in the
work log; tier 1 should confirm P14-1 (``src/test/e2e/`` read as the
``e2e`` package tree), P14-4 (in-process relaunch), P14-11 (the bridge
attached by ``JAVA_TOOL_OPTIONS`` to the unmodified image).

Escalated (none answered here)
==============================

#. **E1** -- Run does not install Comet and Percolator (canonical step 15,
   ``AC-INS-02``): a product change or a specification amendment.
#. **E2** -- no project chooser (step 5).
#. **E5** -- Comet's default top 5 makes converter v2.8.1 refuse the run:
   should the product restrict or deduplicate when Limelight is on?
#. **E12 (serious)** -- a run recorded SUCCEEDED with no provenance record
   when finalisation cannot write it.
#. **E7, E9, E10, E11, E13, E14, E15** -- a failed start's invisible process;
   install failures that never say why; Comet's real 511-byte path limit; the
   stepper's stale "Running"; a silent stale-lock takeover; interrupted runs
   labelled "running" and not retryable.
#. **E8** -- the silent ``XDG_DATA_HOME`` fallback, with Phase 16's launcher.
#. **Ownership** of the five Phase 14 stubs that wait on ``D-006`` and
   Phase 16 (kept PHASE-14; unit 9 had moved them to 16/15), and of
   AC-INS-01/AC-TST-05's remainder.
#. **POM change** in ``cometgui-workflow`` (surefire locale) and the two
   product repairs (E3, E4) -- confirm.
#. **Cost**: ``e2e`` about 20 minutes (Tier A and B each once, three item-5
   reruns, the failure-path tests, the smoke and its self-test).

Surprises
=========

* The specification's own cutoffs (0.01, 0.005, 0.02) hit no q-value exactly
  on this fixture, so ``<`` and ``<=`` give the same counts there; the
  scenario adds a step at a cutoff equal to a q-value in the file.
* My sign-offs ran ``mvn test``, not ``verify``, and missed nine SpotBugs
  findings in test code (unit 6r); later sign-offs add ``verify
  -DskipTests``. The ``quality`` control does not grade test-code SpotBugs.
* An ordinary test with a non-ASCII file name passes under the harness's
  UTF-8 locale and errors on this host's default -- run ordinary tests with
  no locale exported, as ``build.sh`` does.
* Python's PEP 538 coerces a C locale to C.UTF-8 for its children; the
  harness's locale control sets ``PYTHONCOERCECLOCALE=0``.
* ``JDK_JAVA_OPTIONS`` is ignored by the ``jpackage`` launcher;
  ``JAVA_TOOL_OPTIONS`` reaches it. ``javafx.graphics`` loads through the
  platform class loader, so the bridge rewrites ``FxFileChooser``'s dialog
  object, not ``FileChooser``.
* PID 1 here never reaps orphans: a zombie answers ``kill -0`` and
  ``ProcessHandle.isAlive``.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/fixture``, ``scratch/phase05/artefacts``,
``scratch/percolator/3.09``, ``scratch/scientific-path`` and
``scratch/limelight-upload-spike/artefacts`` are present, the host has
``gcc``, ``/usr/bin/timeout``, ``/usr/bin/prlimit`` and network to GitHub,
then run ``bash scripts/build.sh`` and the full
``bash scripts/verify-all-gates.sh`` (now 21 harnesses with ``e2e``, floor
50), and decide the escalations above.
