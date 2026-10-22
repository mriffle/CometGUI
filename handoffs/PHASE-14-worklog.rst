===================================================================
PHASE-14 work log -- GUI Automation and Packaged End-to-End Harness
===================================================================

:Phase: 14
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``0aaeb47`` (brief ``handoffs/PHASE-14-BRIEF.rst``)
:Started: 2026-10-22

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``0aaeb47`` on ``main``; no other phase live.
``bash scripts/verify-test-gates.sh --preflight``: "every precondition
holds". ``scratch/fixture`` (both K562 mzML, the UniProt proteome),
``scratch/phase05/artefacts`` and ``scratch/percolator/3.09`` are present.
**No baseline build was taken**, by the owner's build-economy rule.
The host exports **no locale at all** (``LANG`` and ``LC_ALL`` empty,
``LC_CTYPE="POSIX"``) -- exactly the runner the phase document's important box
warns about.

Facts established by the orchestrator before design (2026-10-22)
-----------------------------------------------------------------

* **The real composition root has the seams a test needs, and no more.**
  ``CometGuiApplication(ApplicationServices, BoundedMessageLog,
  Supplier<BuildIdentity>, Function<Supplier<Window>, FileChooserPort>,
  RunWiring.Setup)``; production passes ``RunWiring.Setup.forThisApplication``.
  ``ApplicationServices`` takes an ``EnvironmentReader`` (a domain port), and
  ``PlatformFileSystemAccess`` derives the application data directory from it
  (``$XDG_DATA_HOME/cometgui``, else ``user.home``). ``FxFileChooser`` is the
  native-dialog port; the specification names "the file chooser abstraction
  and its test injection" as a GUI-test requirement.
* **``cometgui-app``'s surefire sets ``XDG_DATA_HOME`` to the font stack's
  ``usr/share``** (for fontconfig), so a test that let the product read the
  process environment would put the application data inside ``tools/``.
* **The product does not install Comet or Percolator on Run.**
  ``WorkflowRunPort.select`` answers "Comet X is not installed ... install it
  in the Tool Manager section", and ``choiceOf`` requires an installed
  Percolator offer. PDV and the Limelight converter **do** install
  automatically on first use. The canonical scenario's step 15 ("Click Run ...
  the GUI downloads required tools automatically") and ``AC-INS-02`` ("A first
  real run installs the required scientific tools automatically") are
  therefore not what the product does. See P14-5 and *Blockers escalated*.
* **The product has no project chooser.** ``ProjectSession``: one project per
  session, ``projects/default`` under the application data directory, created
  and locked by the first pre-run check that finds a Comet. Scenario step 5
  ("create or open a test project through the UI") has no control to drive.
  See P14-6.
* **Percolator 3.09 has no Linux x86-64 managed artefact** in
  ``manifests/tools.json`` (macOS aarch64 and Windows only). Phase 12's
  no-XML test registers the upstream ``.rpm``'s binary as a local binary
  (``scratch/percolator/3.09/run-percolator-3.09.sh``) -- the specification's
  own fallback (``R-LL-02``: "where no such managed build exists ... the UI
  offers local-binary registration instead").
* **Headed JavaFX works on this host** through the project-local Xvfb and GTK3
  closure (``tools/x11-bookworm-20260829``; Phase 00,
  ``docs/feasibility/gui-automation-spike.rst``), and ``ProjectXvfb`` already
  starts it through the process service for PDV. A packaged application, which
  has no Monocle, can only show a window that way.
* **``jpackage`` strips ``bin/java`` unless ``--jlink-options`` is given**
  (Phase 00, ``docs/feasibility/toolchain.rst``); ``JavaRuntime`` launches the
  converter and PDV from ``java.home/bin/java``. The ``deb`` bundler needs
  ``fakeroot``, absent here: the Linux artefact this host can build is an
  ``app-image`` shipped as ``tar.gz``.
* **The release pipeline's Phase 14 steps are stubs** (``release-smoke``,
  ``release-e2e-canonical``, ``-limelight``, ``-noxml``,
  ``release-no-test-bridge``), and ``scripts/ci/run-pipeline-locally.sh``
  (the ``pipeline`` control) requires a stub to exit 70 and **every other step
  to exit 0** -- so making a step real makes the ``pipeline`` control execute
  it. ``release-package.sh`` is Phase 16's stub and stays one.
* **Existing GUI-test machinery** (``cometgui-app`` test tree): ``FxUiDriver``
  with ``TestFxUiDriver`` and ``RobotFxUiDriver`` (``javafx.scene.robot``
  only), ``RunningApplication`` (``launchedByMain``, ``showing(Stage)``),
  ``RealSearch`` (the ``D-006`` local fixture by SHA-256, LF repair),
  ``RealPercolators``, ``IndependentCounts``, ``OwnDigests``,
  ``IndependentJson``/``IndependentRst``, ``SecretSweep``, ``ProjectXvfb``,
  ``FakeLimelightServer`` (``D-007``), ``ScriptedChooser``.
  ``RunningApplication.pause`` is a ``Thread.sleep`` poll interval.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P14-1 -- where the end-to-end sources live
    ``cometgui-app/src/test/java/org/cometgui/app/e2e/`` and subpackages
    (``support``, ``oracle``, ``scenario``, ``tiera``, ``tierb``, ``bridge``,
    ``failure``). The phase document's ``src/test/e2e/`` is read as "the
    end-to-end test tree", not as a new source root: a new root would need
    a new Maven module or build-helper plus changes to the Spotless and
    Checkstyle file sets and to ``build.sh``'s census, and the app module
    already carries the headless JavaFX setup. Reported to tier 1.

P14-2 -- opt-in, and never a silent pass
    End-to-end classes run only under ``-Dcometgui.e2e=true``; otherwise they
    are skipped **with a stated reason** (as ``UpstreamInstallUiTest`` is).
    The harness that grades them counts tests *run*, not reported, from the
    surefire reports of the run it started. No POM change unless a unit proves
    one necessary, and then it is reported.

P14-3 -- one driver
    Scenario logic is written **once**, tier-neutral, against a remotable
    subset of ``FxUiDriver`` (operations whose arguments and results are
    strings, booleans and lists of strings). ``FxUiDriver`` extends that
    subset, so both in-process drivers implement it; Tier B's bridge client
    implements it by forwarding each call to a ``RobotFxUiDriver`` running
    inside the packaged application. No second implementation of UI
    semantics.

P14-4 -- Tier A's composition
    The real ``CometGuiApplication`` with ``RunWiring.Setup.forThisApplication``
    and a production ``ApplicationServices`` in which exactly two things are
    test-supplied: the ``EnvironmentReader`` (reporting a fresh ``user.home``
    and ``XDG_DATA_HOME``) and the file-chooser factory. Real Tool Manager,
    real installer fetching from the shipped manifest's GitHub URLs (the only
    network host any test reaches), real process service, real workflow.
    **Relaunch**: the first application is stopped through its own
    ``stop()`` (releasing the project lock), then a new ``CometGuiApplication``
    over a new composition root, built from the same home, in the same JVM
    (``Application.launch`` runs once per JVM). Tier B's restart is a new
    process.

P14-5 -- tool installation in the scenario
    Comet and Percolator are installed **through the Tool Manager section's
    own controls** from an empty cache, and the scenario asserts the
    download, checksum and probe states there; PDV and the converter install
    automatically on first use and the scenario asserts that they were absent
    before. Escalated as E1; the product is **not** changed in this phase.

P14-6 -- the project
    The scenario's project is the product's default project, created by the
    interface's first run; "reopen" is the relaunched application reopening
    it, and the second-instance failure path uses its lock. Escalated as E2.

P14-7 -- independent oracles
    Counts, digests and the "Comet was not rerun" assertion come from
    test-owned code reading files on disk: an own ``MessageDigest`` loop, own
    readers of ``comet.params``, PIN, Percolator TSV, weights and the
    provenance JSON. An existing helper is reused only after the unit confirms
    it calls no product code for the fact it checks. "Comet was not rerun"
    is proved from disk (the first run's Comet outputs unchanged by bytes,
    inode and mtime; no new pepXML or PIN anywhere under the project) and is
    then cross-checked against the product's records -- never from the
    product's records alone.

P14-8 -- waiting
    One bounded primitive polls an observable condition against a deadline
    and fails with the last value it observed. No ``Thread.sleep``,
    ``TimeUnit.sleep``, ``LockSupport.park*``, ``Object.wait`` or TestFX
    sleep anywhere in the end-to-end sources (and in the driver code they
    run) **except inside that primitive's poll**. Proved by a check over the
    compiled bytecode and the sources whose ``--self-test`` injects each form
    into a copy and must see it rejected (gate item 6).

P14-9 -- locale
    The harness exports ``LC_ALL=C.UTF-8`` **and** every end-to-end class
    asserts ``sun.jnu.encoding`` is UTF-8 -- in its own JVM for Tier A, as
    reported by the launched process for Tier B -- failing, never skipping,
    with the encoding named. The launcher repair is Phase 16's; if Tier B
    cannot pass without it, that is escalated, not worked around.

P14-10 -- packaging
    Linux: a ``jpackage`` ``app-image`` (and its ``tar.gz``) with
    ``--jlink-options`` that keep ``bin/java``, ``CometGuiLauncher`` as the
    main class, the native-access java-options the app POM documents, and
    **not** the inert ``-Dsun.jnu.encoding``. macOS and Windows configuration
    is written and recorded as never executed. "Installed" means the archive
    extracted into a fresh directory outside the build tree and made
    read-only, launched with no ``java`` on ``PATH`` and ``JAVA_HOME`` unset.
    ``release-package.sh`` stays Phase 16's.

P14-11 -- the bridge
    A test-only Java agent, built from test classes, attached at launch to
    the **unmodified** installed artefact, if measurement shows that works on
    the packaged runtime; otherwise a separately built test-enabled image, and
    the release artefact is never the one carrying it. Binds 127.0.0.1 only,
    refuses anything else, requires a per-launch token, and does nothing in a
    JVM that is not the CometGUI launcher (child JVMs inherit the
    environment). It drives the interface through the in-process
    ``FxUiDriver``; it answers a file chooser by replacing only the native
    dialog, so the product's own chooser logic still runs.

P14-12 -- the release check (gate item 7)
    Inspects a packaged artefact -- every JAR and class in the image, the
    runtime's module list and the launcher configuration -- and fails on the
    bridge's classes, its enabling flag or agent option, and other test-only
    machinery. Its ``--self-test`` builds bridge-included variants and must
    see each one rejected.

P14-13 -- CI and ``D-006``
    No CI job depends on the local fixture. A release step whose real form
    needs the CI fixture (``D-006``) or Phase 16's packaged artefact stays a
    stub whose text names what it waits for; the ``D-007`` nightly sandbox
    slot is wired and skips with a stated reason.

P14-14 -- the harness
    ``scripts/verify-e2e-gates.sh``, registered additively as ``e2e``. Each
    scenario runs once per harness run where it can; gate item 5's three
    injections each rerun the canonical Tier A test. Its cost is measured
    and stated.

.. _p14w-economy:

Build economy -- passed verbatim to every unit agent
====================================================

The brief's section *Build economy -- the owner's standing rule, read before
anything else* (``handoffs/PHASE-14-BRIEF.rst``) is pasted, unedited, into
every unit's dispatch.

Work units
==========

Serial, in this order. No two run at once; no argument that any two cannot
collide exists, so none is offered.

.. list-table::
   :header-rows: 1
   :widths: 5 45 15 35

   * - #
     - Unit and acceptance conditions
     - Serves
     - Sign-off
   * - 1
     - **Foundations.** The ``e2e`` package tree (P14-1), the opt-in
       (P14-2), the locale assertion (P14-9), a fresh home (with a space and
       a non-ASCII segment available), the one wait primitive (P14-8, with
       ``RunningApplication`` routed through it), the independent oracles
       (P14-7) with their own unit tests against hand-computed values, the
       remotable driver subset (P14-3), and the no-fixed-sleep check with a
       ``--self-test`` that sees each injected form go red.
     - item 6; R-TEST-05
     - **Accepted 2026-10-22**, ``85f0f19`` (agent 1). Diff read (32 files,
       test code and ``scripts/e2e/`` only; no ``src/main``, POM or ``.rst``).
       Ran: the unit's tests plus ``RemotableDriverUiTest`` and
       ``SectionNavigationUiTest`` -- 75 run, 0 failures, 2 skipped (the
       canary without the property). Canary with ``-Dcometgui.e2e=true``:
       under ``LC_ALL=C.UTF-8`` 2/2 pass; with no locale exported the class
       fails, naming ``sun.jnu.encoding=ANSI_X3.4-1968``.
       ``check-no-fixed-sleep.sh`` PASSED (47 classes, 361 methods, 34
       sources); ``--self-test`` 14/14 caught. My injections: ``<=`` to ``<``
       in ``IndependentCounts`` (``QValueCountsTest`` red, "a strict < would
       give 2 ==> expected: <5>"); an ``Object.wait(5)`` in
       ``PinFile.read`` (check exit 1, found in bytecode and source); both
       restored, ``sha256sum -c`` OK. Gates on this tree, run by me:
       ``quality`` 42, ``paramui`` 87 (2228 s), ``provenanceui`` 96 (1490 s),
       all PASS. No test reads outside ``cometgui-app``. Change to shared
       code: ``AbstractFxUiDriver.onFxThread`` rethrows an ``AssertionError``
       unwrapped. Not proved on the real app: ``rowsOf`` for a list (unit 3).
   * - 2
     - **Tier A canonical, steps 1--24**: fresh home and empty cache through
       install, parameters, run, ``comet.params``, ``-N``, nothing beside the
       inputs, PIN merge, Percolator artefacts, independent q-value counts at
       0.01, 0.005 (PSM only changes) and 0.02 (peptides), learned weights.
       Tier-neutral scenario steps (P14-3).
     - items 1, 5; R-TEST-03, -05; AC-TST-06
     - pending
   * - 3
     - **Tier A canonical, steps 25--30**: PDV figure for a known PSM, real
       converter through the UI and XML validated, upload to the ``D-007``
       fake, provenance recomputed (MD5 and SHA-256 of every declared input
       and output, tool binaries and JARs on disk, versions, argv, no
       secret), close, relaunch, coherence.
     - items 1, 5; AC-TST-06
     - pending
   * - 4
     - **Tier A no-XML scenario**: 3.09 registered through the UI, run,
       viewing, the Limelight explanation before any conversion, the
       compatible rerun installing 3.07.1 only after the action, Comet not
       rerun (P14-7), XML, conversion, both Percolator executions in
       provenance.
     - item 3; AC-TST-08
     - pending
   * - 5
     - **Packaging** (P14-10): the Linux app-image and archive, the install
       step, macOS and Windows configuration unexecuted, ``bin/java`` present,
       no JDK on ``PATH``.
     - item 2; AC-INS-01
     - pending
   * - 6
     - **The bridge and the release check** (P14-11, P14-12), with the
       check's red demonstration.
     - item 7; R-TEST-06; AC-TST-12
     - pending
   * - 7
     - **Tier B**: both scenarios against the installed artefact on the
       project Xvfb, non-ASCII home, launched-process locale asserted, the
       converter and PDV started from the installed runtime, a real process
       restart.
     - items 2, 3; AC-TST-07
     - pending
   * - 8
     - **Failure-path suite**: every scenario in the specification's list
       mapped to a named passing test, existing or new.
     - item 4; AC-TST-09
     - pending
   * - 9
     - **The harness and CI wiring** (P14-13, P14-14), including gate item
       5's three injections as controls.
     - items 5--7
     - pending
   * - 10
     - **Documentation**: ``docs/developer/e2e_harness.rst``, the testing
       page, traceability for the ``AC-`` and ``R-`` items this phase proves.
     - R-DOC-03
     - pending

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

Recorded here as they arise and carried into the handoff; reported to tier 1
at the end of the phase (the brief asks for no interim pauses).

E1 -- automatic installation on Run
    See P14-5. The specification (canonical step 15, ``AC-INS-02``) says the
    first run installs the required tools automatically; the product requires
    Comet and Percolator to be installed in the Tool Manager first.
    Recommendation: a product change in a later phase (Run offering to
    install the resolved, uninstalled managed Comet and Percolator, with the
    same states the Tool Manager shows), or a specification amendment if the
    explicit install is the intended design. Not changed here: it is a product
    feature outside this phase's scope.

E2 -- no project chooser
    See P14-6. Scenario step 5 has no control. Recommendation: amend the step
    to "the default project the interface creates", or route a project
    chooser to a product phase.
