===========================================================
Between Phases 12 and 13 -- the tier-1 repair, work log
===========================================================

:Brief: ``handoffs/FIX-P12-P13-BRIEF.rst`` (tier 1's; not edited)
:Agent: a single fix agent, dispatched directly by tier 1
:Started: 2026-10-13, from ``4b548c3`` on ``main``, ``git status`` clean
:Commits: ``a79f9f2`` (repair 1), ``37d2421`` (repair 2), and this log

Kept by the fix agent as the work ran. Every number below was read off a run
made in this session; every injection was made in **production** code (or in
the harness script itself, for repair 2), proved to have landed by the old
anchor being gone, and restored and checked with ``sha256sum -c``.

.. contents:: Contents
   :depth: 2
   :local:

Build economy, as followed
==========================

Neither ``scripts/build.sh`` nor the full ``scripts/verify-all-gates.sh`` nor
the full ``--only tests`` was run. ``scripts/dev-verify.sh --dry-run`` showed
that the change reaches the **whole** reactor (``cometgui-app`` and
``cometgui-archtests`` depend on every module), so it was not run either;
instead the affected modules were run directly, as listed per repair.
``--mutation`` was not used; PIT was run on the one changed class.

**Tests that read outside their module.** The new
``ToolManagerRefreshThreadUiTest`` reads ``manifests/tools.json`` (through the
shipped manifest on the class path and ``ArtefactMirror``) and
``scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar`` -- both
already read by ``LimelightFirstUseInstallTest`` and already checked by the
``tests`` preflight. No project document is read by any changed test.

Repair 1 -- the Tool Manager started processes on the JavaFX thread
===================================================================

What was wrong
--------------

``ToolManagerViewModel.refresh()`` called ``ToolManager.offers()`` and
``noManagedBuild()`` on the thread it was called on. ``ManagedToolManager.offers()``
runs the loadability check of every installed build through the process
service -- for the converter ``java -jar ... --version`` -- and ``refresh()``
is called on the JavaFX thread by ``CometGuiApplication.start``, by
``install()``, after a registration and on every terminal install report. The
root cause was the port's own contract: ``ToolManager``'s javadoc said
implementations were "safe to call from the JavaFX application thread: offers()
answers from what is already known", which stopped being true when the
loadability probe was added.

What changed (``a79f9f2``)
--------------------------

* ``ToolManagerViewModel.refresh()`` reads ``offers()`` and
  ``noManagedBuild()`` on the ``background`` executor it already took, and
  applies the answer through the ``uiThread`` executor. Rows are still matched
  by key, so a row still offered keeps its identity, its running install and
  its last progress report. A refresh supersedes an older one still reading:
  each is numbered (``AtomicLong``) and only the newest answer is applied, so
  an older read that finishes last cannot put a stale list back. A read that
  throws leaves the rows as they were and the summary says
  ``The tool list could not be read: ...`` (before, the exception would have
  reached the caller on the interface thread). The unavailable-host branch is
  unchanged and still synchronous (it reads no port).
* ``ToolManager`` (domain) and ``ManagedToolManager`` javadoc now say
  ``offers()`` starts processes and is not to be called on the JavaFX thread.
  Javadoc only.
* Tests adjusted to the asynchronous answer, each keeping its assertion:
  ``NoManagedBuildViewModelTest`` (its queued background executor now also
  receives the read: the test runs it, after asserting exactly one was
  queued); ``ProgressReachesTheInterfaceThreadTest`` (one more interface-thread
  round trip before reading rows; see *Fallout*);
  ``CometGuiApplicationStartupTest`` and ``AccessibleNameEnumerationUiTest``
  wait, bounded, for the Tool Manager summary to leave
  ``The tool list has not been read yet.`` (new helper
  ``RunningApplication.awaitToolListRead()``), so the accessible-name walk
  still walks the Tool Manager's rows rather than an unfilled section.

Every other caller, checked
---------------------------

* **Callers of** ``refresh()``: ``CometGuiApplication.start`` (interface
  thread); ``install()``; ``register()``'s completion (posted to the interface
  thread); ``onProgress`` on a terminal report; and ``toolManager::refresh``
  handed to ``PercolatorViewModel``, ``VisualisationViewModel`` and
  ``LimelightViewModel`` as their "an install or registration ended" callback.
  All of them now reach the port only through the background executor, from
  whatever thread they call on.
* **Callers of** ``offers()`` **in production**: ``ToolManagerViewModel``
  (fixed); ``ToolManagerPercolatorPort.offers()`` -- called only inside
  ``PercolatorViewModel.refresh()``'s ``background.execute``;
  ``WorkflowRunPort.select`` -- reached through ``RunViewModel.recheck()``'s
  ``background.execute`` and the run itself, on run threads;
  ``LimelightProvisioning`` and ``PdvProvisioning`` -- reached through
  ``LimelightViewModel`` / ``VisualisationViewModel``, whose ``refresh()`` and
  ``check()`` call the port inside ``background.execute``. **No other caller
  has the fault.**

Tests that prove it, with counts
--------------------------------

* ``ToolManagerRefreshThreadUiTest`` (``cometgui-app``, new, 1 test): the
  manager ``ToolManagerWiring`` composes over the shipped manifest and an empty
  cache, the pinned converter JAR installed into it through the view-model's
  own ``install``, the view-model built as the application builds it
  (``Platform::runLater``, ``ToolManagerWiring.installThreads()``) in a live
  scene; a recorder in front of the ``ProcessService`` notes every start's argv
  and ``Platform.isFxApplicationThread()``. It asserts that the refresh after
  the install **did** start ``java -jar <installed jar> --version`` (so it cannot
  pass by nothing running) and that **no** launch in the whole test was on the
  interface thread. Not timed.
* ``ToolManagerRefreshThreadTest`` (``cometgui-ui``, new, 6 tests), both
  executors queues: the port is read only on the background queue and the rows
  change only when the interface queue runs; an older answer arriving last is
  dropped, and one arriving first is dropped too; a row keeps its identity,
  ``INSTALLING`` state, handle and progress text across an off-thread refresh;
  an unreadable port leaves the rows and says why; a superseded failure says
  nothing.

Runs:

* ``mvn -B -o -pl cometgui-ui verify`` -- **787 tests, 0 failures**, all
  coverage checks met, SpotBugs clean (first run found
  ``THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`` in the new test; fixed in the
  test). Re-run after the last test edit: 787, 0 failures, BUILD SUCCESS.
* ``mvn -B -o -pl cometgui-ui,cometgui-app test`` (22 min) -- ui **787**, app
  **297, 0 failures, 1 skipped** (the opt-in ``UpstreamInstallUiTest``, skipped
  as before). First attempt stopped at a Checkstyle line length in the new
  test; fixed.
* ``mvn -B -o -pl cometgui-domain,cometgui-install,cometgui-ui,cometgui-app,cometgui-archtests
  test -Dtest='org.cometgui.archtests.**'`` -- ArchUnit **40, 0 failures**
  (``--only quality`` does not run ArchUnit; Phase 12 handoff).
* ``mvn -B -o -pl cometgui-domain,cometgui-install compile`` -- BUILD SUCCESS
  (javadoc-only change; Spotless and Checkstyle bound to ``validate`` ran).

Injections (production code), each landed, red, restored
--------------------------------------------------------

``ToolManagerViewModel.java`` copied and hashed first; each restore was a copy
back, ``touch`` (the incremental compiler keeps a class whose source mtime did
not move -- Phase 12) and ``sha256sum -c`` ``OK``. Style gates were skipped
(``-Dspotless.check.skip -Dcheckstyle.skip -Dspotbugs.skip``) for the injected
runs only.

#. **I1 -- the read put back on the interface thread**:
   ``background.execute(`` in ``refresh()`` replaced by
   ``((Executor) Runnable::run).execute(``. Old anchor count 0, one
   ``background.execute(`` left (the registration's); compiled class hash
   changed ``8b43d8a8...`` -> ``e986088d...``.
   ``ToolManagerRefreshThreadUiTest``: **1 run, 1 failure**::

       a process was started on the JavaFX application thread -- the window
       stalls for as long as it runs. Of 3 launches, these were on it ==>
       expected: <[]> but was: <[Launch[argv=[.../bin/java, -jar,
       .../limelight-converter/2.8.1/linux-x86-64/cometPercolator2LimelightXML.jar,
       --version], thread=JavaFX Application Thread, interfaceThread=true], ...]>

   ``ToolManagerRefreshThreadTest``: **6 run, 6 failed** (after the test's
   queue was made to say why rather than throw ``IndexOutOfBoundsException``,
   which it did on the first injected run)::

       nothing was queued on the background executor, so the work this test
       expected there ran somewhere else -- for a read of the port, on the
       thread that called refresh()

#. **I2 -- the superseded-answer check removed** from ``apply``. Anchor count
   0. ``ToolManagerRefreshThreadTest``: **6 run, 2 failed**
   (``anOlderReadThatFinishesLastIsDropped``: "the first refresh's answer
   arrived last and replaced the newer list with an older one ==> expected:
   <[percolator-3_07_1-1, pdv-2_7_0-1]> but was: <[percolator-3_07_1-1]>";
   ``anOlderReadArrivingFirstIsAlsoDropped``: "expected: <[]> but was:
   <[pdv-2_7_0-1]>").
#. **I3 -- the install gate's own control 4**, by hand: the progress
   listener's ``uiThread.execute`` hop removed. With the test as it stood,
   **2 run, 1 failed** -- only the mid-install test; see *Fallout*. With the
   test as committed, **2 run, 2 failed**, the terminal one with "must reach
   the pane's row list on the JavaFX application thread, but it arrived on
   "cometgui-install-1". ... The first change seen was to the row's progress."
   -- the text control 4 greps for.

Targeted PIT
------------

``mvn -B -o -pl cometgui-ui test-compile org.pitest:pitest-maven:mutationCoverage
-Dcometgui.mutation.skip=false -Dcometgui.pit.threads=16
-DtargetClasses='org.cometgui.ui.viewmodel.ToolManagerViewModel*'
-DtargetTests='org.cometgui.ui.viewmodel.*'`` (no POM change). The toolkit
tests cannot run under PIT here (no Monocle argument line in PIT's minion: 12
"failing without mutation"), hence the view-model test filter. **63
mutations, 60 killed (95 %)**; every mutation in the new code killed. Left:
two ``SURVIVED`` -- ``rows.clear()`` and ``noManagedBuild.clear()`` in the
unchanged unavailable-host branch, equivalent (an unavailable view-model never
holds a row or a gap) -- and one ``NO_COVERAGE``, ``summaryProperty``'s return,
covered only by the pane tests outside the filter. Population: one class file
(``ToolManagerViewModel.class``), one class in the report. The report was
copied out and ``cometgui-ui/target/pit-reports`` removed (Phase 08's trap).

.. _fixp12-fallout:

Fallout, and why a test was strengthened
----------------------------------------

``ProgressReachesTheInterfaceThreadTest.aTerminalReportRebuildsTheRowListOnTheInterfaceThread``
listened to the row **list** only. After the repair the list can no longer
change off the interface thread -- every refresh applies through ``uiThread``
-- so with the progress hop removed (``verify-install-gates.sh`` control 4)
that test stayed **green** (I3, first half): control 4's second assertion
would have gone red as a harness failure. The defect the control injects is
still real -- the terminal report then reaches the row's progress and
``installFinished`` off the interface thread -- so the test now watches the
row's progress as well as the list, grades the **first** change, still
requires the list to be rebuilt (a second latch), and names the first change
in its message after the text control 4 matches. The harness was not edited.

Repair 2 -- the tests preflight missed three prerequisites
==========================================================

What changed (``37d2421``)
--------------------------

``preflight()`` in ``scripts/verify-test-gates.sh`` now also stops with a
``FATAL`` naming what is missing and how to get it:

* **no** ``timeout`` **on** ``PATH``. ``ProjectXvfb`` (``cometgui-tools``)
  resolves ``timeout`` by walking ``PATH``, not by the literal
  ``/usr/bin/timeout``, so the preflight looks where the test looks -- the
  ``PATH`` the sandbox build inherits. On this host it resolves to
  ``/usr/bin/timeout``.
* **no** ``gcc`` **on** ``PATH``, likewise; and a ``gcc`` that is present but
  does not build the xkbcomp shim: the preflight compiles
  ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c`` with the tests' flags
  into ``_build/test-gate-preflight/`` and requires a non-empty ``.so``
  (exit 0 proves nothing).
* **the importer JAR** ``scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar``
  missing, or with a SHA-256 other than ``IMPORTER_SHA256`` read from
  ``FakeLimelightServerTest.java`` -- the test that needs it, read the way the
  large-fixture check reads ``LargeFixture.java``; the refill instruction
  names ``limelight_upload_spike.py --only artefacts``, which fetches and
  checks it against its own pin. A missing or reshaped pin is a
  ``HARNESS ERROR``.

The usage text names the new prerequisites. Nothing was installed. Cost:
about one second (the shim compile).

Each refusal, seen firing, without touching the host
----------------------------------------------------

All in the scratchpad: ``PATH`` directories of symbolic links to every program
in ``/usr/bin`` and ``/bin`` minus one; a copy of the tree (``scripts/``
copied, ``LargeFixture.java`` and ``FakeLimelightServerTest.java`` copied,
everything else the preflight reads symlinked).

.. list-table::
   :header-rows: 1
   :widths: 34 8 58

   * - Case
     - Exit
     - Output
   * - real tree
     - 0
     - ``PREFLIGHT: every precondition holds``
   * - ``PATH`` without ``timeout``
     - 1
     - ``FATAL: no timeout on PATH (...bin-no-timeout). The real-PDV tests
       (ProjectXvfb in cometgui-tools) cap the project-local Xvfb with it ...``
   * - ``PATH`` without ``gcc``
     - 1
     - ``FATAL: no gcc on PATH (...bin-no-gcc). The real-PDV tests ...
       compile scripts/feasibility/gui-spike/xkbcomp-path-shim.c with it ...``
   * - a ``gcc`` that exits 0 and writes nothing
     - 1
     - ``FATAL: gcc (.../bin-bad-gcc/gcc) is on PATH but did not build the
       xkbcomp path shim ...``
   * - control: the same link directory hiding nothing
     - 0
     - ``PREFLIGHT: every precondition holds``
   * - tree copy, no importer JAR
     - 1
     - ``FATAL: scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar
       does not exist. cometgui-tools' FakeLimelightServerTest runs the real
       Limelight importer from there ...``
   * - tree copy, JAR one byte longer
     - 1
     - ``FATAL: ... has SHA-256 f73be491..., not the 281d882a... pinned as
       IMPORTER_SHA256 in .../FakeLimelightServerTest.java ...``
   * - tree copy, pin file missing / pin renamed
     - 1
     - ``FATAL: HARNESS ERROR: ... is missing ...`` / ``... no 64-hex-digit
       IMPORTER_SHA256 could be read ...``
   * - control: tree copy with the pinned JAR
     - 0
     - ``PREFLIGHT: every precondition holds``

.. _fixp12-gates:

Gates
=====

*Pending at the time of writing; filled in when they finish.*

Escalated
=========

Nothing escalated beyond the report. Two notes for tier 1:

* ``docs/developer/results_model.rst`` says ``--preflight`` "runs only those
  checks" (the large fixture's); the preflight has checked more than that
  since Phase 05. Left as it is (tier 1's documentation, not this brief's).
* The brief names ``/usr/bin/timeout``; the tests look ``timeout`` up on
  ``PATH``, so the preflight does too (see repair 2).
