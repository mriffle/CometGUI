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
     - **Accepted 2026-10-22**, ``f8b80c2`` (agent 2), **with a workaround
       to be removed by unit 2b**: the test relaunches the application after
       the installs because of defect E3 (below). Diff read (17 files, test
       code only; composition = ``FreshHome.services()`` + the real
       ``CometGuiApplication`` over ``Setup.forThisApplication``; the
       loopback guard widened only to ``github.com|*.githubusercontent.com``,
       as ``UpstreamInstallUiTest`` does). Ran myself:
       ``TierACanonicalScenarioTest`` with ``-am`` under ``LC_ALL=C.UTF-8``,
       19 run, 0 failures, 73 s. My injection: ``PinMerger`` silently
       dropping the second file's first row -- red, 1 failure, 6 later steps
       skipped: "the summed row count of its sources ==> expected: <6657> but
       was: <6656>"; restored, ``sha256sum -c`` OK. Agent's injections (read,
       not re-run): results ``<=`` to ``<`` (red only on an added step at a
       cutoff equal to a q-value in the file -- the spec's own cutoffs 0.01,
       0.005, 0.02 hit no row exactly), a parameter written differently from
       the screen, Percolator skipped (caught by the product's own guard, so
       not a silent bypass). Numbers: merged PIN 6657 rows; 3934 target
       PSMs, 1024 at 0.01, 998 at 0.005; 3018 peptides, 599 at 0.01, 623 at
       0.02; downloads 9 875 971 bytes. Reads outside the module (opt-in
       class only): ``manifests/tools.json``, ``scratch/fixture`` (both mzML,
       the proteome), GitHub. ``paramui``/``provenanceui`` (driver gained
       ``isSelected``): agent's run PASS 87/96; mine deferred to after 2b,
       which changes product code those harnesses read.
   * - 2b
     - **Repair of E3**: the listener on the Tool Manager's rows held
       through a field; a test that fails on the old code; every other
       listener on a per-call wrapper audited; the relaunch workaround
       removed from unit 2's test.
     - E3; items 1, 2
     - **Accepted 2026-10-22**, ``64d5144`` (agent 2b). Diff read: the
       product change is one field in ``CometGuiApplication`` holding the
       ``rows()`` wrapper the listener is on; listener body unchanged; audit
       of all 146 ``addListener`` sites found no other instance. New
       ordinary test ``InstallReachesTheSectionsUiTest`` (forces a
       collection until a weak sentinel clears, installs through the Tool
       Manager from the loopback server). Ran myself: it plus Tier A, 1 + 18
       run, 0 failures. My injection: the listener's
       ``percolator.refresh()`` commented out -- red, "#run-engine did not
       reach the expected text within PT2M ... Percolator 3.07.1 ... is not
       installed"; restored, ``sha256sum -c`` OK. Agent showed both tests
       red with the fix reverted. Gates on this tree, by me: ``quality`` 42,
       ``paramui`` 87 (2366 s), ``provenanceui`` 96 (1298 s), PASS (these
       also cover unit 2's driver change). Reads outside the module:
       ``scratch/phase05/artefacts`` (as ``ToolManagerInstallUiTest``).
   * - 3
     - **Tier A canonical, steps 25--30**: PDV figure for a known PSM, real
       converter through the UI and XML validated, upload to the ``D-007``
       fake, provenance recomputed (MD5 and SHA-256 of every declared input
       and output, tool binaries and JARs on disk, versions, argv, no
       secret), close, relaunch, coherence.
     - items 1, 5; AC-TST-06
     - **Accepted 2026-10-23**, ``9b4a4a7`` (agent 3) plus my repair of E4,
       ``031748b``. Diff read (18 files: scenario ``DownstreamSteps``, three
       new oracles with hand-computed tests, ``FreshHome`` desktop
       variables, the Tier A class; ``FakeLimelightServer.close`` joins
       untimed in all four byte-identical copies). On ``9b4a4a7`` alone the
       test is red at step 26 (E4); with ``031748b``, run by me: 23 run, 0
       failures, 73 s. My injection: ``ManifestWriter.render`` skipping the
       ``.fasta`` file record -- red at step 28, "...UP000005640_9606.fasta:
       not declared as an input"; restored, ``sha256sum -c`` OK. Agent's
       injections (read): ``weights.txt`` dropped from the record (gate item
       5's dropped entry), one MD5 wrong, the upload's final submit skipped
       while the UI still says submitted (a silent bypass the product does
       not catch). Numbers with E5's top-1 setting: 1201 target PSMs, 1053 at
       0.01; Limelight XML 3 525 177 bytes, schema-valid, 1201 psm; 28
       declared files recomputed; key absent from 269 files in 15 encodings;
       q filters persisted in ``results/view-state.json`` and restored on
       relaunch. **Step 10 now also sets ``num_output_lines = 1``** (E5),
       which changed unit 2's counts. PDV in Tier A is given the project
       Xvfb's library variables through ``RunWiring.Setup`` built from
       ``forThisApplication``'s own parts (a third seam; see E6). Agent's
       gates on ``9b4a4a7``: ``paramui`` 87, ``limelight`` 130,
       ``provenanceui`` 96 PASS. Reads outside the module (opt-in only):
       ``manifests/tools.json``, ``scratch/fixture``,
       ``tools/x11-bookworm-20260829``, ``tools/fontstack-bookworm-20260829``,
       ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c`` (host ``gcc``),
       GitHub.
   * - 4
     - **Tier A no-XML scenario**: 3.09 registered through the UI, run,
       viewing, the Limelight explanation before any conversion, the
       compatible rerun installing 3.07.1 only after the action, Comet not
       rerun (P14-7), XML, conversion, both Percolator executions in
       provenance.
     - item 3; AC-TST-08
     - **Accepted 2026-10-23**, ``adec5c5`` ``3f96c12`` (agent 4). Diff read
       (5 files, test code only: ``NoXmlScenario`` reusing
       ``CanonicalScenario``'s steps, ``TierAApplication`` shared by both
       Tier A classes). Ran myself: ``TierANoXmlScenarioTest`` 15 and
       ``TierACanonicalScenarioTest`` 23, 0 failures;
       ``check-no-fixed-sleep.sh`` PASSED (109 classes). My injection:
       ``PercolatorRerun.copy`` touching the source file's mtime after
       copying -- red at step 5 on the disk check, "the first run's Comet
       trees, unchanged ... changed=[pin/merged.pin: mtime] ...
       [comet.params: mtime]"; restored, ``sha256sum -c`` OK. Agent's
       injections (read): Comet re-executed by the rerun (red on disk alone,
       the product's records agreeing), 3.07.1 installed eagerly or as a
       silent prefetch (red at step 3, the first cache walk after the
       explanation), ``-X`` passed to 3.09 (the run fails), a no-XML run
       judged convertible (red at step 3). 3.09 is a registered local binary
       (no Linux managed build); the "no managed build, offer registration"
       branch is Phase 12's ``LimelightRerunUiTest.noManagedBuild``. Reads
       outside the module (opt-in): as unit 3, plus
       ``scratch/percolator/3.09``.
   * - 5
     - **Packaging** (P14-10): the Linux app-image and archive, the install
       step, macOS and Windows configuration unexecuted, ``bin/java`` present,
       no JDK on ``PATH``.
     - item 2; AC-INS-01
     - **Accepted 2026-10-23**, ``a717cdc`` (agent 5). Diff read
       (``packaging/jpackage/{common,linux,macos,windows}.args``,
       ``packaging/runtime-modules.txt``, ``scripts/package/``:
       ``build-app-image.sh``, ``install-archive.sh``,
       ``smoke-installed.sh``, ``x11_windows.py``; no ``src/main``, POM or
       ``.rst``; ``release-package.sh`` untouched). ``--jlink-options``
       keeps ``bin/java``; native access for ALL-UNNAMED and
       javafx.graphics; no ``-Dsun.jnu.encoding`` (the build refuses it).
       Runtime: 29 modules (``java.se`` -- so ``java.instrument`` -- JavaFX
       base/graphics/controls, ``jdk.unsupported``, ``jdk.httpserver``,
       ``jdk.charsets``, ``jdk.localedata``, ``jdk.zipfs``), chosen for the
       app, the converter and PDV. Ran myself: build 24 s (incremental),
       install outside the repository read-only, ``smoke-installed.sh``
       S1-S11 all PASS (no java on ``PATH``, ``JAVA_HOME`` unset; runtime
       ``bin/java`` with ``sun.jnu.encoding=UTF-8``; the converter JAR's
       ``--version`` from the installed runtime; a viewable ``CometGUI``
       window owned by the installed launcher, ``/proc`` exe and libjvm
       inside the install; clean exit 0 on WM_DELETE_WINDOW; install tree
       unchanged, 264 entries; writes confined to the fresh home). My
       injection: a copy of the install without ``libglassgtk3.so`` -- S6-S9
       FAIL, "UnsatisfiedLinkError: no glassgtk3 in java.library.path".
       Agent showed each check failing on its own broken variant
       (``--self-test``). Sizes: image 169 MB, archive 61 MB. Unverified:
       macOS and Windows argfiles (never executed; macOS likely rejects
       version 0.1.0). Findings: E7; the font stack needs a generated
       ``FONTCONFIG_FILE`` under a fresh ``XDG_DATA_HOME`` (test
       environment only).
   * - 6
     - **The bridge and the release check** (P14-11, P14-12), with the
       check's red demonstration.
     - item 7; R-TEST-06; AC-TST-12
     - **Accepted 2026-10-23**, ``b20c6aa`` (agent 6). Diff read: the bridge
       (``e2e/bridge``: a ``-javaagent`` attached through
       ``JAVA_TOOL_OPTIONS`` to the **unmodified** installed image; binds
       IPv4 loopback and re-checks after binding, drops non-loopback
       clients; 32-byte token in an owner-only port file; every operation a
       ``RemotableUiDriver`` call on an in-process ``RobotFxUiDriver``; file
       choosers answered by rewriting only ``FxFileChooser``'s native-dialog
       object, so the product's chooser logic runs; inert in child JVMs),
       the client side (``e2e/tierb``), ``scripts/e2e/build-bridge.sh``, and
       ``scripts/ci/release-no-test-bridge.sh`` + ``no_test_bridge.py``
       replacing the Phase 01 stub. No ``src/main``, POM or ``packaging/``
       change. Ran myself: the bridge unit tests and
       ``BridgeEndToEndTest`` against the installed archive -- 57 run, 0
       failures (locale from the launched process, values read and set,
       chooser answered under a non-ASCII path, token and non-loopback
       refusals, close to exit 0, a second process over the same home).
       Release check on the clean archive PASS (14 JARs, 1511 class
       entries, 1 cfg, 29 modules / 24 134 classes inspected);
       ``--self-test`` 9 bridge-included variants rejected, an empty image
       refused as uninspected. **Gate item 7, my own variant**: the bridge's
       classes in ``lib/extras.zip`` -- FAILED, 22 findings, "the end-to-end
       harness, which holds the test bridge". Gates by me: ``workflows`` 23,
       ``pipeline`` 24 steps (1449 s; the check now a real step, exit 0),
       PASS; agent's ``quality`` 42 PASS. The check builds the archive if it
       is absent (cost then about 2 minutes, else 2 s).
   * - 6r
     - **Rework of units 1-4, found by agent 6**: SpotBugs runs on test
       classes at ``verify``; nine findings in units 1-4's code (null
       dereferences, a hard-coded ``/tmp``, ``throws Exception``) would have
       failed ``scripts/build.sh``. **My sign-offs ran ``test``, not
       ``verify``, and missed them**; the ``quality`` control does not
       grade test-code SpotBugs either.
     - build
     - **Repaired by me 2026-10-23**, ``6b77a54``: required non-null with a
       reason, ``java.io.tmpdir``, narrowed throws clauses; no exclusion.
       ``mvn -pl cometgui-app verify -DskipTests``: 9 findings, then BUILD
       SUCCESS; e2e support/oracle tests and both Tier A scenarios 91 run,
       0 failures. **Every later sign-off adds that ``verify -DskipTests``
       run.**
   * - 7
     - **Tier B**: both scenarios against the installed artefact on the
       project Xvfb, non-ASCII home, launched-process locale asserted, the
       converter and PDV started from the installed runtime, a real process
       restart.
     - items 2, 3; AC-TST-07
     - **Accepted 2026-10-23**, ``50b9921`` (agent 7). Diff read (12 files,
       test code only; the same tier-neutral scenario code as Tier A, step
       25 split into "launched" and "drew"). Ran myself on a rebuilt image:
       ``TierBCanonicalScenarioTest`` 24, ``TierBNoXmlScenarioTest`` 16,
       ``TierBLocaleControlTest`` 2 -- 0 failures, 249 s; ``verify
       -DskipTests`` BUILD SUCCESS; ``check-no-fixed-sleep.sh`` PASSED
       (153 classes); ``/home/agent`` left untouched. Tier B invariants: the
       launched process reports ``sun.jnu.encoding=UTF-8``, the installed
       runtime as ``java.home``, no ``JAVA_HOME``, no java on ``PATH``; all
       four tools downloaded into the fresh home by the packaged app; the
       install tree unchanged by bytes, mtime and inode; the converter ran on
       ``<install>/lib/runtime/bin/java``; a real process restart with the
       filters restored. **Step 25 partial (E6)**: PDV installed and
       launched from the installed runtime, fails only for ``libX11.so.6
       => not found`` -- drawing not proved on this host. Locale control:
       launched without a locale, the harness's assertion throws naming
       ``sun.jnu.encoding=ANSI_X3.4-1968``. My injection: results ``<=``
       to ``<`` in ``QValueFilter.classify``, built into the image -- red,
       "PSMs at q <= 0.00949668 ==> expected: <[1201, 1053, 148, 0]> but
       was: <[1201, 1044, 157, 0]>"; restored, ``sha256sum -c`` OK, image
       rebuilt. Agent's injections (read): ``java.logging`` dropped from the
       runtime (Tier B red at the converter/PDV, Tier A green, and
       ``build-app-image.sh`` still passed -- its checks cover the app's own
       needs only); ``JavaRuntime`` taking java from ``PATH`` (Tier B red,
       Tier A green). Incident: the locale control twice wrote a tool cache
       into ``/home/agent`` before the agent pinned ``user.home`` for that
       launch; removed and verified absent.
   * - 8
     - **Failure-path suite**: every scenario in the specification's list
       mapped to a named passing test, existing or new. Split: **8a** the
       map, its checker, gaps 2, 3, 6, 15, 20, 21; **8b** gaps 14, 17, 18, 24
       and 25 at GUI level.
     - item 4; AC-TST-09
     - **8a accepted 2026-10-23**, ``b140915`` (agent 8a) plus my
       ``d4e8bd7``. Diff read: ``scripts/e2e/failure-paths.toml`` (the 25
       scenarios in the specification's words and order),
       ``check-failure-paths.sh`` + ``failure_paths.py`` (re-parses
       ``specification.rst`` each run; ids must exist and not be
       ``@Disabled``; given reports, every mapped test must have run and
       passed); new tests ``NetworkUnavailableUiTest``,
       ``RunInputFailureUiTest`` (app), ``NetworkUnavailableTest``
       (install), ``RealAwkwardPathRunTest``, ``RunFailurePathTest``
       (workflow), ``StepFailureTest`` gaining a real deletion; **POM:
       ``cometgui-workflow`` surefire gets ``LANG``/``LC_ALL=C.UTF-8``**, as
       ``cometgui-process`` already has (needed by the real Unicode run under
       surefire and PIT; reported to tier 1). Ran myself with no locale
       exported: the six classes 3+2+3+2+2+9 run, 0 failures. Checker: red by
       default (4 partial, owned by 8b), green with ``--allow-partial``;
       ``--self-test`` PASSED. My injection: ``RunExecution.firstAbsent``
       treating a declared file as present when its directory exists -- red,
       "the step that ran the tool fails, naming the missing PIN ==>
       expected: <FAILED> but was: <SUCCEEDED>"; restored, ``sha256sum -c``
       OK. **Found by 8a, repaired by me (``d4e8bd7``)**: unit 6's
       ``DialogsTransformerTest`` errored with no UTF-8 locale (a non-ASCII
       file name in an ordinary test) -- now ASCII; 4/4 both ways. Product
       defects found: E9, E10, E11. Gates: the agent ran ``install`` 95,
       ``workflow`` 110, ``provenanceui`` 96, ``paramui`` 87, ``quality`` 42
       on ``b140915`` (PASS); mine run after 8b, which touches the same
       modules. Reads outside modules: ``scratch/phase05/artefacts``,
       ``scratch/fixture``, ``manifests/tools.json``.
       **8b accepted 2026-10-23**, ``6f9e3df`` ``a8faba4`` (agent 8b). Diff
       read (16 files, test code and the map; no ``src/main``, no POM):
       14 ``PercolatorCancellationTest``, ``PercolatorCancelUiTest``; 17
       ``UnwritableRunDirectoryTest`` (fails, not skips, as root); 18
       ``FileSizeLimitRunTest`` (a child JVM under RLIMIT_FSIZE 15000 --
       EFBIG, not ENOSPC; no free-space pre-check exists); 24
       ``InterruptedRunReopenedTest``, ``InterruptedRunReopenUiTest`` (a
       child JVM halts mid-Percolator); 25 ``LockedProjectUiTest`` (a second
       JVM holds the project). Map: no scenario partial. Ran myself with no
       locale exported: 4+2+1+1+1+1+2 run, 0 failures, 60 s;
       ``check-failure-paths.sh`` (no ``--allow-partial``) PASSED, 25/25
       scenarios, ids all found; ``--self-test`` PASSED. My injection:
       ``RunExecution`` ending a cancelled run-percolator as FAILED, not
       CANCELLED -- red, but by the test's 60 s run bound ("the cancelled run
       did not end within PT1M", the engine refusing the transition) rather
       than a state assertion; restored, ``sha256sum -c`` OK. Agent's
       injections (read): ten, each red, after the first cancel injection
       showed a watchdog could outlast the bound (repaired in ``a8faba4``).
       Defects: E12-E15. ``paramui`` control 2v failed once in the agent's
       run (85/87) and passed alone 3/3 and on a full rerun -- an
       intermittent in ``VariableModificationEditorUiTest``, untouched here.
       **Gates for 8a and 8b, by me on ``a8faba4``**: ``quality`` 42,
       ``install`` 95, ``paramui`` 87 (2248 s), ``workflow`` 110,
       ``provenanceui`` 96 (1252 s) -- all PASS.
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

E3 -- sections never learn of an install (found by unit 2, REPAIRED in 2b)
    ``CometGuiApplication.start`` adds its listener to
    ``toolManager.rows()``, a fresh unmodifiable wrapper per call that
    observes the backing list weakly; nothing keeps the wrapper, so after a
    garbage collection Run, Percolator, Visualisation and Limelight are never
    refreshed after an install (``ToolManagerViewModel.rows()``'s own
    documentation warns of it). Observed: Run still saying "Comet 2026.03.0
    is not installed" ten minutes after the Tool Manager showed it installed.
    A defect in approved work, so repaired in this phase (unit 2b) under the
    owner's fix-red-and-continue rule, and reported.

E4 -- the converter cannot be installed under a non-ASCII home (REPAIRED, ``031748b``)
    ``LimelightConverterIdentity`` probed ``java -jar <jar> --version`` with
    an empty environment, so the child JVM could not open a JAR under an
    accented path; the install failed with no diagnostic on screen. Repaired
    by the orchestrator: the probe passes ``LimelightConverter.ENVIRONMENT``
    (``LANG=C.UTF-8``), as a conversion already did. The identity test
    asserts the environment (red with ``Map.of()``, "expected:
    <{LANG=C.UTF-8}> but was: <{}>"); targeted PIT 7/7 killed;
    ``TierACanonicalScenarioTest`` 23/23 with it.
    Gates after it, by me: ``install`` 95 (408 s), ``limelight`` 130
    (1178 s), PASS. Note: targeted PIT rewrote ``cometgui-tools``'s PIT
    report; regenerate every module's report before ``--only tests``.

E5 -- Comet's default top 5 makes the pinned converter refuse the run (ESCALATED)
    With ``num_output_lines = 5`` on the ``D-006`` fixture and the whole
    proteome, Comet 2026.03.0 reports one spectrum twice with the same
    modified peptide (``k562_3`` scan 11483, ``k562_4`` scan 10245); both
    reach Percolator's XML and converter v2.8.1 refuses: "Got more than one
    entry ... for the same scan to the same reported peptide". The product
    reports the refusal faithfully; it cannot convert such a run. The
    scenario sets top 1 in the Advanced level as a documented workaround.
    Question for tier 1/owner: should the product restrict or deduplicate
    when Limelight is enabled (a product and scientific choice), or document
    it?

E6 -- PDV's display environment on a host without system X11 (RECORDED)
    ``PdvDisplay.fromHost`` passes PDV only ``DISPLAY`` and ``XAUTHORITY``;
    this host has no ``libX11``, so PDV needs the project closure's library
    path. Tier A supplies it through the ``RunWiring.Setup`` seam. A packaged
    application cannot be given that seam, so on this host Tier B can prove
    that PDV starts from the installed runtime but not that it draws. A real
    Linux desktop has ``libX11``; this is a testing gap of this host, not a
    product defect.

E7 -- a failed start leaves an invisible process (ESCALATED, not repaired)
    Found by unit 5: when the packaged application's ``start`` failed (no
    fonts under a fresh ``XDG_DATA_HOME``), the process stayed alive with no
    window, logging a ``RejectedExecutionException`` from
    ``PercolatorRerunViewModel.refresh``; the application's own non-daemon
    threads keep the JVM up. A scientist would see nothing and a second start
    could meet a held resource. Recommendation: the application exits (or
    shuts its executors down) when ``start`` throws. Outside this phase's
    scope; not repaired here.

E8 -- an unrepresentable XDG_DATA_HOME is dropped silently (RECORDED, Phase 16)
    Found by unit 7's locale control: under a non-UTF-8 locale an accented
    ``XDG_DATA_HOME`` arrives as U+FFFD, ``PlatformFileSystemAccess`` cannot
    use it and falls back to ``user.home`` -- the password-database home, not
    ``$HOME`` -- without a word, and the tool cache and default project go
    there. Belongs with the launcher locale repair (Phase 16); a warning at
    least is recommended.

E9 -- a failed install never says why (ESCALATED)
    Found by 8a: the installer's exception names the URL and the refused
    connection, but ``ManagedToolManager.runInstall`` swallows it; the row
    only says "the last install attempt did not succeed". Small, clear,
    user-visible; recommended for a product phase.

E10 -- the pre-run path-length limit is not Comet's (ESCALATED)
    Found by 8a, measured on Comet 2026.03.0: it refuses a database path of
    511 bytes or more while ``PathRule.FILE_NAME_LIMIT`` allows 4095, and it
    cuts ``-N`` and ``-P`` at 511 bytes, so a run with a longer output path
    exits 0 having written under a shortened name and then fails with a
    misleading "declared pepXML ... does not exist". Recommendation: a
    release-specific limit in the validator.

E11 -- the stepper shows Percolator "Running" after a Comet failure (ESCALATED)
    Found by 8a: after a run fails at run-comet the Percolator stage reads
    "Running" and stays so; ``StageProjection.combine`` draws a stage whose
    first step (resolve-percolator) succeeded and the rest never started as
    running.

E12 -- a run recorded SUCCEEDED with no provenance record (ESCALATED, serious)
    Found by 8b: if ``provenance/`` becomes unwritable mid-run, or the disk
    fills during finalisation, the run's outcome and ``run.json`` say
    SUCCEEDED while ``provenance.json`` and ``provenance.rst`` were never
    written; the outcome text only adds "Its provenance could not be
    finalised". Cause: ``RunExecution.finish`` fixes the outcome before
    writing the seal. A provenance-complete product should not call such a
    run a success. Recommended for repair before release.

E13 -- a stale-lock takeover is never shown (ESCALATED)
    ``ProjectLock.recoveryNotice()`` is produced and read by no product code;
    the interface takes over a dead CometGUI's lock silently.

E14 -- an interrupted run is labelled "running" (ESCALATED)
    Results reads "... running" and the Provenance summary "Status:
    running"; only the Provenance run list says "interrupted".

E15 -- an interrupted run cannot be retried (ESCALATED)
    No product code ends an interrupted attempt, so
    ``RunDescriptor.withNewAttempt`` refuses ("still running"); only a new
    run is offered. Cited from code by 8b, not asserted by a test.

E2 -- no project chooser
    See P14-6. Scenario step 5 has no control. Recommendation: amend the step
    to "the default project the interface creates", or route a project
    chooser to a product phase.
