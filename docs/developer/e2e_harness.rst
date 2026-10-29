.. _dev-e2e-harness:

==================
End-to-end harness
==================

This page is for a developer who has to run, extend or debug CometGUI's
end-to-end tier: the application driven the way a scientist drives it, with
real Comet, Percolator, PDV and the Limelight converter, and with every claim
the application makes checked by test-owned code reading the files on disk.
It was written by Phase 14 (``phases/PHASE-14-e2e.rst``); the design decisions
it cites as ``P14-n`` and the findings it cites as ``En`` are recorded in
``handoffs/PHASE-14-worklog.rst``.

Everything here was built and run on one Linux x86-64 host. Nothing has been
executed on macOS or Windows; see :ref:`dev-e2e-gaps`.

.. contents:: Contents
   :depth: 2
   :local:

.. _dev-e2e-tiers:

The two tiers, and why both
===========================

``specification.rst``, *End-to-end harness: two tiers*, makes both mandatory,
because an in-process robot and a packaged application are different things.

Tier A -- the assembled application, in-process
   The real ``CometGuiApplication`` runs inside the test JVM over a brand-new
   home and an empty tool cache, driven through the GUI driver. In-process
   access makes waiting on observable state and independent recomputation
   practical, so Tier A carries the detailed scenario assertions.
   Classes: ``org.cometgui.app.e2e.tiera.TierACanonicalScenarioTest`` and
   ``TierANoXmlScenarioTest``.

Tier B -- the packaged artefact, out of process
   The exact archive ``scripts/package/build-app-image.sh`` built is
   installed read-only outside the repository and its unmodified native
   launcher is started as a separate process, with a fresh home, no ``java``
   on ``PATH`` and ``JAVA_HOME`` unset. A test-only loopback bridge drives it.
   Tier B proves that *packaging* did not break the product: the bundled
   runtime starts, tools download into the installed application's home, a
   complete run succeeds, provenance is written, and the install tree is not
   written to. Classes: ``org.cometgui.app.e2e.tierb.TierBCanonicalScenarioTest``,
   ``TierBNoXmlScenarioTest``, ``TierBLocaleControlTest`` and
   ``BridgeEndToEndTest``.

Both tiers run **the same scenario code** (:ref:`dev-e2e-one-driver`), and
neither calls ``WorkflowEngine.run()``: every action enters through the
controls a user operates (``R-TEST-05``).

Where the sources live
----------------------

``cometgui-app/src/test/java/org/cometgui/app/e2e/`` (P14-1: the phase
document's ``src/test/e2e/`` is read as "the end-to-end test tree", not a new
source root, which would need a new module and changes to the Spotless,
Checkstyle and census file sets):

.. list-table::
   :header-rows: 1
   :widths: 18 82

   * - Package
     - What it holds

   * - ``support``
     - ``@EndToEnd`` and ``EndToEndExtension`` (the opt-in and the locale
       assertion), ``RunnerLocale``, ``FreshHome``.
   * - ``oracle``
     - The independent readers and counters (:ref:`dev-e2e-oracles`), each
       with an ordinary unit test against hand-computed values.
   * - ``scenario``
     - ``CanonicalScenario``, ``DownstreamSteps``, ``NoXmlScenario`` and their
       helpers: tier-neutral steps that touch no scene-graph node, no JavaFX
       thread and no product class.
   * - ``tiera``
     - Tier A's composition (``TierAApplication``) and its two scenario
       classes.
   * - ``tierb``
     - Tier B's composition (``TierBApplication``), the install and launch
       (``InstalledCometGui``, ``LaunchedCometGui``, ``PackagedDisplay``), the
       bridge client (``BridgeClient``, ``BridgedUiDriver``,
       ``BridgedChooserScript``, ``BridgeJar``) and its test classes.
   * - ``bridge``
     - The test-only Java agent that runs *inside* the packaged application
       (:ref:`dev-e2e-bridge`).

The wait primitive and the remotable driver interface are in
``org.cometgui.app.uidriver`` beside the existing drivers. There is no
``failure`` subpackage, although P14-1 named one: the failure-path tests live
in the modules whose behaviour they exercise (:ref:`dev-e2e-failure-paths`).

.. _dev-e2e-running:

Running it
==========

Prerequisites
-------------

Check them first; the preflight takes about two seconds and changes nothing::

    bash scripts/verify-e2e-gates.sh --preflight

It refuses to continue (exit 3) and names what is missing. What it needs:

* **A built tree**: ``tools/env.sh`` and a populated ``_build/m2repo``
  (``bash scripts/build.sh`` once). Every end-to-end Maven command runs
  offline (``-o``).
* **The** ``D-006`` **local fixture**, gitignored under ``scratch/fixture/``:
  the two Crux K562 mzML files and the UniProt human proteome. Fetch and
  verify it by SHA-256 with ``python3 scripts/feasibility/fetch_ephemeral_input.py``.
  This is the *local* fixture the owner delegated. It is **not** the CI
  fixture: that half of ``D-006`` is open, and no CI job may use the local one
  (:ref:`dev-e2e-ci`). ``RealSearch`` checks every file's SHA-256 before use;
  a missing or changed file fails the test, never skips it.
* **Other gitignored inputs**: ``scratch/percolator/3.09/run-percolator-3.09.sh``
  (Percolator 3.09 as a registered local binary, because there is no Linux
  managed build of 3.09; see :ref:`dev-version-capabilities`),
  ``scratch/phase05/artefacts/`` (the pinned Comet, Percolator and converter
  artefacts the ordinary failure-path tests and the smoke test read) and
  ``scratch/scientific-path/``.
* **The project-local display**: the X11 closure
  ``tools/x11-bookworm-20260829`` and the font stack
  ``tools/fontstack-bookworm-20260829``, plus the host's ``gcc`` to compile
  ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``. Both tiers draw real
  windows on a private Xvfb started through the process service
  (``ProjectXvfb``).
* **Network to GitHub release hosts.** The scenarios install the four tools
  the way a user does, from the URLs ``manifests/tools.json`` pins, on
  ``github.com``, whose downloads redirect to ``*.githubusercontent.com``.
  Tier A's JVM may reach exactly those hosts and loopback (its
  ``LoopbackGuard`` sinks every other connection and fails the test); Tier B's
  packaged application downloads them itself. No Limelight server is ever
  contacted: every upload goes to the ``D-007`` fake on loopback.
* **A non-root user.** The Tier B install is made read-only, and permission
  bits refuse root nothing.

The opt-in
----------

Every end-to-end class carries ``@EndToEnd`` (P14-2). It runs only when the
JVM has ``-Dcometgui.e2e=true``. Without it the class is **skipped with a
stated reason** naming the property's value, which surefire records as
skipped -- not run, not passed. ``scripts/build.sh`` therefore never runs an
end-to-end scenario. The oracle unit tests and the bridge's unit tests are
ordinary and always run.

The locale, asserted rather than inherited
-------------------------------------------

Run every end-to-end command under ``LC_ALL=C.UTF-8``. This project's build
host exports no locale at all, and in that state the JVM's
``sun.jnu.encoding`` is ``ANSI_X3.4-1968``: ``Path.of`` throws on any
non-ASCII path, so the space-and-accent paths the specification's failure list
names could not be exercised. A runner that silently skipped them would report
green while losing exactly the coverage a scientist's machine needs.

So the harness does not trust the environment it was given (P14-9):

* ``@EndToEnd`` asserts ``sun.jnu.encoding`` is UTF-8 **before anything else
  in the class**, and *fails* -- never skips -- naming the encodings and the
  remedy. Tier B makes the same assertion (``RunnerLocale``) over the values
  the *launched* packaged process reports through the bridge.
* ``-Dsun.jnu.encoding=UTF-8`` is **inert** -- the JVM fixes the value from the
  process locale before system properties apply -- so it is not a remedy and
  is refused in the packaged launcher's configuration. Starting the packaged
  application in a UTF-8 locale is Phase 16's launcher work.
* ``scripts/verify-e2e-gates.sh`` exports ``LC_ALL`` and ``LANG`` itself and
  shows both assertions failing with no locale (controls L and B). For its
  locale control it sets ``PYTHONCOERCECLOCALE=0``, because Python's PEP 538
  would otherwise coerce a C locale to ``C.UTF-8`` for its child processes.
* An end-to-end class must not build a ``Path`` in a static initialiser: that
  runs before the extension, and a non-ASCII path there fails with
  ``InvalidPathException`` instead of the locale diagnostic.

One class at a time
-------------------

Tier A, each scenario on its own::

    LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierACanonicalScenarioTest \
        -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true
    LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierANoXmlScenarioTest \
        -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true

Tier B needs the packaged archive first (:ref:`dev-e2e-packaging`); each class
installs it fresh and removes it afterwards::

    bash scripts/package/build-app-image.sh
    LC_ALL=C.UTF-8 mvn -B -o -pl cometgui-app -am test -Dtest=TierBCanonicalScenarioTest \
        -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.e2e=true

``TierBNoXmlScenarioTest``, ``TierBLocaleControlTest`` and
``BridgeEndToEndTest`` run the same way. The Tier B classes are
``@EnabledOnOs(LINUX)``: the Linux app-image is the only packaged artefact
built here.

The scenario steps are ordered test methods, each building on the last. Once a
step fails the remaining steps are skipped **naming the step that failed**,
rather than failing on the state it left. Read the first failure, not the
last.

Measured costs on the development host, from the unit sign-offs in the work
log: ``TierACanonicalScenarioTest`` 23 tests in about 73 s; the three Tier B
scenario classes together about 249 s; ``build-app-image.sh`` 24 s when the
reactor is already built. Every run downloads all four tools afresh from
GitHub, because every home is new.

The whole tier, graded
----------------------

``bash scripts/verify-e2e-gates.sh`` runs both tiers, the failure-path suite
and the injections, and grades all seven Phase 14 exit gate items; see
:ref:`dev-e2e-gate-harness`. It is also ``bash scripts/verify-all-gates.sh
--only e2e``. ``--help`` prints the full description of every control.

.. _dev-e2e-one-driver:

One driver, two tiers
=====================

Scenario logic is written once (P14-3), against
``org.cometgui.app.uidriver.RemotableUiDriver``: the part of ``FxUiDriver``
whose every argument and result is a ``String``, a ``boolean``, an ``int`` or a
``List<String>``. Controls are named by their stable identifiers
(``R-TEST-04``); actions are synthetic input, reads are scene-graph state on
the application thread; nothing calls a view-model or a controller.
``RemotableUiDriverShapeTest`` fails on a method with any other shape.

* **In Tier A** the implementation is one of the existing in-process drivers,
  ``TestFxUiDriver`` or ``RobotFxUiDriver``, which implement it through
  ``FxUiDriver``.
* **In Tier B** it is ``BridgedUiDriver``, which forwards each call over
  loopback to a ``RobotFxUiDriver`` running *inside* the packaged
  application. An ``AssertionError`` the far driver throws comes back with its
  message, so a remote failure reads as the in-process one would.

There is therefore no second implementation of what a click or a selection
means. File dialogs are answered through ``ChooserScript``: a
``ScriptedChooserScript`` over the composition root's file-chooser seam in
Tier A, a ``BridgedChooserScript`` over the bridge in Tier B.

**Adding a step.** Write it in ``e2e.scenario`` against ``RemotableUiDriver``
and ``ChooserScript`` only. If it needs a driver operation that does not
exist, add it to ``RemotableUiDriver`` under the rules in that interface's
Javadoc (implemented once in ``AbstractFxUiDriver``, proved against the real
application through both drivers -- ``RemotableDriverUiTest`` -- before
anything relies on it). Unit 2 added ``isSelected`` that way. Never give a
scenario a path constant: every path comes from ``ScenarioLayout``.

.. _dev-e2e-tier-a:

Tier A's composition and its seams
==================================

``TierAApplication`` builds the real ``CometGuiApplication`` with
``RunWiring.Setup.forThisApplication`` and a production
``ApplicationServices`` (P14-4). The Tool Manager, the installer, the process
service and the workflow are the product's own; no tool is staged or
mirrored and no product class is replaced. Exactly these are test-supplied:

#. **The environment.** ``FreshHome.services()`` is
   ``ApplicationServices.forThisHost()`` with an ``EnvironmentReader`` -- the
   product's own domain port -- that answers ``user.home``, ``HOME``,
   ``XDG_DATA_HOME`` and ``APPDATA`` with the fresh home and ``DISPLAY`` with
   the project Xvfb, and delegates everything else. This matters doubly here:
   ``cometgui-app``'s surefire sets the test JVM's own ``XDG_DATA_HOME`` to the
   font stack under ``tools/``, so an application left to read the process
   environment would put its tool cache there.
#. **The file chooser.** The composition root's
   ``Function<Supplier<Window>, FileChooserPort>`` is a ``ScriptedChooser``.
   The specification names "the file chooser abstraction and its test
   injection" as a GUI-test requirement.
#. **PDV's display environment** -- a third seam where P14-4 named two, and
   stated as such. The product's ``PdvDisplay.fromHost`` passes PDV only
   ``DISPLAY`` and ``XAUTHORITY``; this host has no system ``libX11``, so the
   JVM PDV runs on cannot load ``libawt_xawt.so``. Tier A supplies
   ``PdvDisplay.fromHost`` over the application's environment plus the project
   closures' library and fontconfig locations, through ``RunWiring.Setup``
   built from ``forThisApplication``'s own parts. A real Linux desktop has the
   library. See E6 in :ref:`dev-e2e-gaps`.

``FreshHome`` creates ``<parent>/<segment>/home/`` with ``.local/share`` and
``AppData/Roaming`` beneath it; the segment can carry a space and an accented
letter. It does **not** create the application data directory: on a fresh
home that does not exist until the application makes it.
``assertNoToolPresent()`` walks the whole home, by the test's own rule for
where the data directory is, and never asks the product's Tool Manager.

**Restart.** ``Application.launch`` runs once per JVM, so step 29 stops the
first application through its own ``stop()`` (which releases the project
lock), then builds a new ``CometGuiApplication`` over a new composition root
from the same home, in the same JVM. Tier B's restart is a new process.

.. _dev-e2e-scenarios:

The scenarios
=============

The canonical scenario
----------------------

``CanonicalScenario`` and ``DownstreamSteps`` follow ``specification.rst``,
*Canonical E2E scenario*, steps 1 to 30, with these departures, each recorded
in the work log:

* **Tools are installed in the Tool Manager, not on Run** (P14-5, E1). The
  product requires Comet and Percolator to be installed in the Tool Manager
  section before Run; the scenario installs them there from an empty cache and
  asserts the download, checksum and probe states. PDV and the converter do
  install automatically on first use, and the scenario asserts they were
  absent before.
* **The project is the default project** (P14-6, E2). The product has no
  project chooser; step 5 uses ``projects/default``, created by the first run,
  and "reopen" is the relaunched application reopening it.
* **Step 10 sets** ``num_output_lines = 1`` (E5). With Comet's default of 5,
  the pinned converter refuses this fixture's run.
* **An added boundary step**, ``inclusiveCutoff``. The specification's own
  cutoffs (0.01, 0.005, 0.02) hit no q-value in this run exactly, so ``<=``
  turned into ``<`` passes them; the step filters at a cutoff equal to a
  q-value present in the file, which is what catches that defect.
* **Tier B splits step 25** into "PDV launched" and "PDV drew". On this host
  only the first is proved (E6).

Numbers from the Tier A run with top-1 output (unit 3): merged PIN rows equal
the summed rows of its sources; 1201 target PSMs, 1053 at q <= 0.01;
Limelight XML about 3.5 MB, schema-valid, 1201 PSMs; 28 declared files
recomputed; the submit key absent from 269 files in 15 encodings.

The no-XML scenario
-------------------

``NoXmlScenario`` reuses the canonical steps and follows *Second E2E
scenario: Percolator without XML*: Percolator 3.09 registered through the UI
as a local binary, a run, standard viewing, the Limelight explanation shown
before any conversion is attempted, the compatible rerun installing 3.07.1 only
after the explicit action, Comet not rerun, XML, conversion, and both
Percolator executions in provenance with distinct versions, checksums and
commands. The specification's other branch -- no managed XML-capable build for
the platform, so the UI offers local-binary registration -- is Phase 12's
``LimelightRerunUiTest``, not this scenario.

.. _dev-e2e-oracles:

Independent oracles
===================

A number the application shows is compared with one the application did not
compute (P14-7). The classes in ``e2e.oracle`` -- ``CometParamsFile``,
``PinFile``, ``QValueCounts``, ``PercolatorWeights``, ``BestTargetPsm``,
``LimelightXml``, ``FigureImage``, ``DirectorySnapshot`` -- use the JDK alone
and import nothing from ``org.cometgui`` outside the test tree; the two test
helpers they build on, ``OwnDigests`` (a ``MessageDigest`` loop) and
``IndependentCounts``, import nothing from it either. Each is proved by an
ordinary unit test whose expected values were worked out by hand and typed in,
so a reader that agreed with the product on a wrong answer would still be
caught.

**"Comet was not rerun"** is proved from disk first: the first run's Comet
outputs unchanged by bytes, inode and mtime (``DirectorySnapshot``), and no new
pepXML or PIN anywhere under the project. Only then is it cross-checked
against the product's own records. The product's records alone are never the
proof. Control 3i of the gate harness shows the disk assertion catching a
rerun that merely touches an mtime.

.. _dev-e2e-waiting:

Waiting without fixed sleeps
============================

``org.cometgui.app.uidriver.Await`` is the one way an end-to-end or GUI test
waits for a fact no event announces (P14-8): it observes a value until a
condition holds or a bound passes, evaluating before the first pause and after
every pause, and fails naming what was awaited, the bound, how many times it
looked and **the last value it saw**. The bound is a deadlock detector, not a
timing assumption. ``RunningApplication`` waits through it. Where an event
exists (a listener, a latch, a future), use the event instead.

``Await.pause`` is the only timed pause permitted. The check::

    bash scripts/e2e/check-no-fixed-sleep.sh
    bash scripts/e2e/check-no-fixed-sleep.sh --self-test

reads the **compiled** classes of ``org.cometgui.app.e2e`` and
``org.cometgui.app.uidriver`` and every ``cometgui-app`` test class they
reference, with ``javap`` -- so a static import, a method reference or a helper
cannot hide a call -- and scans their sources too. It fails on
``Thread.sleep``, ``TimeUnit.sleep``, ``LockSupport.park*``, ``Object.wait``,
a timed ``Thread.join``, TestFX's sleeps and ``WaitForAsyncUtils``, and the
timed-delay equivalents (``Timer.schedule``, a scheduled executor,
``CompletableFuture.delayedExecutor``, ``PauseTransition``), permitting the
one call in ``Await.pause`` by class *and* method, never by file. It needs
compiled test classes (``mvn -B -o -pl cometgui-app test-compile``); missing
or stale ones are exit 2, not a pass. On the current tree it scans 153
classes in about 3 s. ``--self-test`` injects each form into a copy and
requires it rejected.

.. _dev-e2e-bridge:

The test bridge
===============

Tier B needs to drive a packaged application in another process. P14-11 chose
a **test-only Java agent attached at launch to the unmodified installed
image**, after measurement showed the jpackage launcher's JVM reads
``JAVA_TOOL_OPTIONS`` (it prints ``Picked up JAVA_TOOL_OPTIONS``;
``JDK_JAVA_OPTIONS`` is ignored by that launcher). No test-enabled image is
built, and nothing in the packaged artefact names the bridge.

``bash scripts/e2e/build-bridge.sh`` jars it from ``cometgui-app``'s compiled
**test** classes into ``_build/e2e/bridge/cometgui-e2e-bridge.jar``
(``BridgeJar`` builds the same jar from the classes the test JVM is running,
through ``--classes`` and ``--class-path``). It holds the agent, the
``uidriver`` package with ``RobotFxUiDriver`` (not the TestFX driver) and
JUnit's assertion classes, and nothing else; the script lists the jar
afterwards and fails if it holds a product class or TestFX. ``LaunchedCometGui``
starts the installed ``bin/CometGUI`` through the process service with::

    JAVA_TOOL_OPTIONS=-javaagent:<bridge jar>=port-file=<port file>

That variable is split on whitespace, so neither path may contain any; the
fresh home with its space and accent is not in it.

What the agent does, and refuses:

* **Inert in any other JVM.** The product starts child JVMs (the converter and
  PDV, from the bundled runtime's ``bin/java``), and an environment variable
  can reach them. The first thing ``BridgeAgent.premain`` does is decide from
  two system properties -- ``sun.java.command`` naming
  ``org.cometgui.app.bootstrap.CometGuiLauncher`` and ``jpackage.app-path``
  naming an executable called ``CometGUI`` -- whether this is the packaged
  launcher, and otherwise it returns: no transformer, thread, socket, file or
  output. ``BridgeEndToEndTest#childJvmIsUntouched`` proves it with the
  installed runtime's ``java -jar`` of the converter and the same
  ``JAVA_TOOL_OPTIONS``.
* **Loopback only.** ``BridgeServer`` binds ``127.0.0.1`` from its four bytes on
  an ephemeral port, re-checks after binding that the socket is on loopback,
  and drops any client whose peer is not.
* **A per-launch token.** 32 random bytes, written with the port and pid to an
  owner-only port file (moved into place atomically), compared in constant
  time; never in the environment, the command line or any output. A request
  without it, or with another, is refused and the connection closed.
* **Nothing but the driver.** A request is a ``RemotableUiDriver`` method,
  executed by one ``RobotFxUiDriver`` inside the application, or one of five
  ``bridge.`` operations: await the window, report the environment (encodings,
  ``java.home``, the variables a test asserts on), and three for file dialogs.
  No view-model, port, workflow or product class is reachable, which is how it
  cannot bypass parameter serialisation, validation or orchestration
  (``R-TEST-06``).
* **The chooser rewrite.** ``DialogsTransformer`` rewrites only the three
  method bodies of ``FxFileChooser``'s native-dialog object (the nested class
  implementing ``FxFileChooser$Dialogs``) to hand the configured
  ``FileChooser`` and owner window to ``BridgeDialogs``, which returns what the
  test queued. ``FxFileChooser`` itself -- titles, filters, suggested names,
  what a cancelled dialog means -- runs as shipped. It cannot rewrite
  ``javafx.stage.FileChooser``, because in the packaged runtime
  ``javafx.graphics`` belongs to the platform class loader, which cannot see
  the agent. Every rewrite and every failure is reported over the bridge, so a
  test requires the rewrite before relying on it. It uses the JDK's
  ``java.lang.classfile`` API, so the agent carries no bytecode library.

Tier B stops the application the way a user does: ``WM_DELETE_WINDOW`` sent
from outside by ``scripts/package/x11_windows.py``, then the process's own exit
with status 0.

.. _dev-e2e-no-bridge:

Proving the bridge never ships
------------------------------

``scripts/ci/release-no-test-bridge.sh`` (P14-12, ``AC-TST-12``; the release
pipeline's *Verify that no test bridge is present*) inspects a packaged
artefact::

    bash scripts/ci/release-no-test-bridge.sh              # the archive build-app-image.sh wrote
    bash scripts/ci/release-no-test-bridge.sh ARTEFACT     # a .tar.gz or an app-image
    bash scripts/ci/release-no-test-bridge.sh --self-test

It reads every JAR and nested JAR entry by entry, every manifest, every loose
class, the runtime's module image (with ``jimage``) and every text file, the
launcher configuration ``lib/app/*.cfg`` first. It fails on any class of
``org/cometgui/app/e2e/**`` or ``org/cometgui/app/uidriver/**``, any class
compiled from a ``*/src/test/java`` source of this repository, TestFX, Monocle,
JUnit and similar frameworks, the ``D-007`` fake; on a JAR whose manifest makes
it a Java agent or extends the boot class path; and on ``-javaagent``,
``-agentlib``, ``-agentpath``, ``-Xbootclasspath``, ``JAVA_TOOL_OPTIONS``, the
``cometgui.e2e`` flag or the bridge's names in any text. It reports how much it
inspected, and an artefact with no JAR, class, launcher configuration or
runtime module is exit 2: "found nothing" is never "looked at nothing". On the
current Linux archive it inspects 14 JARs, 1511 class entries, one launcher
configuration and 29 runtime modules holding 24 134 classes, in about 2 s
(about two minutes if it must build the archive first). It is a real step of
the release pipeline and is executed by the ``pipeline`` control.

Gate item 7 -- "the bridge-absence check fails when the bridge is deliberately
included in a build" -- is ``--self-test``: it builds nine bridge-included
variants and requires each rejected, and an empty image refused as
uninspected. The macOS and Windows installers are not unpacked yet; on those
hosts the script exits 1 saying so, because an artefact that has not been
inspected has not passed.

.. _dev-e2e-packaging:

Packaging
=========

P14-10. The configuration is committed; the script adds only what a build
knows (the version from the build identity, the input directory, the main JAR,
the module list, the destination):

.. list-table::
   :header-rows: 1
   :widths: 38 62

   * - File
     - What it is

   * - ``packaging/jpackage/common.args``
     - Every platform: name, main class
       ``org.cometgui.app.bootstrap.CometGuiLauncher``,
       ``--jlink-options "--strip-debug --no-man-pages --no-header-files"``
       and the two ``--enable-native-access`` options.
   * - ``packaging/jpackage/linux.args``
     - ``--type app-image``. Executed here.
   * - ``packaging/jpackage/macos.args``, ``windows.args``
     - ``dmg`` and ``msi`` configuration. **Never executed**: jpackage is not
       a cross-compiler and no such host exists here.
   * - ``packaging/runtime-modules.txt``
     - The runtime's root modules, each with its reason.

``bin/java`` **is kept.** Without ``--jlink-options`` jpackage strips native
commands and the bundled runtime has no ``java`` launcher
(:doc:`/feasibility/toolchain`); the product launches PDV and the converter
from ``java.home/bin/java``, so such an image installs, starts, and cannot run
either tool. Supplying the option replaces jpackage's defaults, so the value is
those defaults minus ``--strip-native-commands``.

**The runtime's modules** are ``java.se``, ``javafx.controls``,
``jdk.unsupported``, ``jdk.httpserver``, ``jdk.charsets``, ``jdk.localedata``
and ``jdk.zipfs`` (29 modules once jlink resolves them). The list covers the
two third-party JARs this runtime also runs, not only the application, and
deliberately leaves out ``jdk.jdi`` (and with it ``jdk.attach`` and
``jdk.jdwp.agent``). ``java.instrument`` is present through ``java.se``, which
is what lets Tier B attach its agent; it was not added for the tests.
``build-app-image.sh`` checks that every module ``jdeps`` finds for the
application is present, but **not** what the two tools need: a unit 7
injection that dropped ``java.logging`` passed the build and was caught only
by Tier B.

``-Dsun.jnu.encoding`` **is refused** in the launcher configuration: it is
inert (see the locale section above), and a UTF-8 launcher is Phase 16's.

The commands::

    bash scripts/package/build-app-image.sh
    bash scripts/package/install-archive.sh _build/package/linux/CometGUI-<version>-linux-x86_64.tar.gz DIR
    bash scripts/package/smoke-installed.sh DIR
    bash scripts/package/install-archive.sh --uninstall DIR

``build-app-image.sh`` writes the image under ``_build/package/linux/image/``
and the archive with its ``.sha256`` beside it (image about 169 MB, archive
about 61 MB), checking each stage's output -- byte-identical reactor JARs, no
test machinery, ``bin/java`` and the JavaFX modules present, the bundled
``java`` runs, the archive matches its checksum. ``--skip-maven`` reuses the
reactor's current outputs.

``install-archive.sh`` installs the way a user does, and stricter: it verifies
the checksum, extracts into a directory that must not exist and must be
**outside the repository** (where a path that works only in development would
go unnoticed), and makes the whole tree **read-only**, proving a probe write
is refused. It refuses to run as root.

``smoke-installed.sh`` checks the installed tree from outside, with no JDK on
``PATH`` and ``JAVA_HOME`` unset and no bridge: S1 the launch environment; S2
the runtime's ``bin/java`` runs with ``java.home`` inside the install and a
UTF-8 ``sun.jnu.encoding``; S3 the JavaFX modules; S4 that ``java`` runs the
pinned converter JAR; S5 the tree is read-only; S6 a viewable ``CometGUI``
window owned by the launcher on a private Xvfb; S7 ``/proc`` shows the
installed launcher and only the installed ``libjvm.so`` and glass library;
S8 a clean exit 0 on ``WM_DELETE_WINDOW``; S9 no output but Prism's two no-GLX
lines; S10 the install tree unchanged; S11 writes confined to the fresh home.
About 6 s. ``--self-test`` installs damaged copies and requires each caught by
its own check. Its header documents the exact launch environment this host
needs; Tier B uses the same one (``PackagedDisplay``). The library and
fontconfig variables in it stand in for a desktop's system libraries; a
scientist's machine needs none of them. Under a fresh ``XDG_DATA_HOME`` the
font stack needs a generated ``FONTCONFIG_FILE`` -- a test-environment detail
only.

.. _dev-e2e-failure-paths:

The failure-path map
====================

Gate item 4: "every failure-path scenario in the specification has a passing
test". ``scripts/e2e/failure-paths.toml`` lists the 25 scenarios of
``specification.rst``, *Failure-path scenarios*, in its words and order, each
with its status (``covered`` or ``partial``) and the tests that exercise it.
A test is a ``class#method`` id with a level (``gui-e2e``, ``gui``,
``integration``, ``unit+real``, ``unit``) and a run (``ordinary``, which
``scripts/build.sh`` runs, or ``e2e``). Most tests live in the module whose
behaviour they exercise -- ``cometgui-app``, ``cometgui-install``,
``cometgui-workflow`` and others -- and many predate Phase 14; units 8a and 8b
added the missing ones.

::

    bash scripts/e2e/check-failure-paths.sh
    bash scripts/e2e/check-failure-paths.sh --reports DIR... --scope all
    bash scripts/e2e/check-failure-paths.sh --self-test

The checker re-parses the specification's paragraph on every run, so a
scenario cannot be dropped, added or reworded in the map alone; requires every
mapped test to exist and not be ``@Disabled``; and, given surefire reports,
requires every mapped test in scope to have run and passed (a skip, a missing
report and a run of zero each fail). A ``partial`` scenario fails it unless
``--allow-partial`` is given. Today: 25 of 25 covered, 61 distinct tests in 12
test source trees, about 5 s for ``--self-test``.

**To add a test** for a scenario, add a ``[[scenario.test]]`` to that
scenario and run the checker; to change the list itself, the specification
changes first.

The suite found product defects that are recorded, not repaired, in this phase:
E9 to E15 below.

.. _dev-e2e-gate-harness:

The gate harness
================

``bash scripts/verify-e2e-gates.sh`` (P14-14; ``e2e`` in
``scripts/verify-all-gates.sh``, floor 48 graded checks) never works in the
working tree. It extracts ``git archive HEAD`` into ``_build/e2e-gate-sandbox``
-- so **uncommitted changes are not what it grades**, and it says so -- and
symlinks in the gitignored ``scratch/``, ``tools/`` and ``_build/m2repo``
(read offline, digested before and after). Every Maven run is started under a
child subreaper, so the real Comet, Percolator, PDV, Xvfb and CometGUI
processes the tests leave are found and ended; one that survives ``KILL`` is a
harness failure.

.. list-table::
   :header-rows: 1
   :widths: 10 14 76

   * - Control
     - Gate item
     - What it proves

   * - A
     - 1, 3
     - Both Tier A scenarios, once, under ``LC_ALL=C.UTF-8``: every ``@Test``
       method counted as **run** from that run's surefire XML against the
       source, none skipped, ``sun.jnu.encoding`` UTF-8, ``cometNotRerun``
       named. Its bytecode is the baseline every injection is compared with.
   * - L
     - 1
     - ``TierACanonicalScenarioTest`` with every locale variable removed fails
       before any step, naming the encoding.
   * - P
     - 2
     - ``build-app-image.sh`` in the sandbox; the archive's ``.sha256``
       verified before Tier B and after every control.
   * - B
     - 2, 3
     - The three Tier B classes against that archive, every method run and
       passed; ``TierBLocaleControlTest`` shows the launched-process locale
       check failing.
   * - 4
     - 4
     - Every ``ordinary`` test the map names, run fresh; the checker graded
       over exactly those reports plus A's and B's (``--scope all``); its
       ``--self-test``.
   * - 6
     - 6
     - ``check-no-fixed-sleep.sh`` on the sandbox's compiled tree, and its
       ``--self-test``.
   * - 7
     - 7
     - ``release-no-test-bridge.sh`` on the clean archive, and its
       ``--self-test``.
   * - 3i
     - 3
     - ``PercolatorRerun.copy`` touching the first run's file: the no-XML
       Tier A test red at ``cometNotRerun`` on the disk assertion, the step
       before it green.
   * - 5q
     - 5
     - ``QValueFilter.classify``'s ``<=`` made ``<``: red at
       ``inclusiveCutoff``, while step 21 at 0.01 stays green.
   * - 5p
     - 5
     - ``ManifestWriter.render`` leaving the searched FASTA out of
       ``provenance.json``: red at step 28.
   * - 5b
     - 5
     - ``LimelightUploadClient`` skipping the final submit while the screen
       still says submitted -- a bypass the product's own guards do not catch:
       red at step 27.
   * - H
     - --
     - The harness itself: a missing anchor, an injection that reaches the
       source but not the bytecode, a green graded as red, a red without its
       expected words, and a real run without the opt-in in which every test
       was skipped -- each must be reported as a HARNESS FAILURE.
   * - C
     - --
     - Every restoration reached the bytecode: a recompile identical to the
       baseline, every damaged source back to its pristine digest, the archive
       still verifying, the shared repository's jars unchanged.

Each injection goes into **production code**: its anchor must match exactly
once, the compiled form of the damaged class must differ from the baseline and
every other compiled file must be identical, and the red must be in the named
test **with the expected failure's own words** -- a red for another reason is a
harness failure, never a pass. Gate item 5's three injections are in the
canonical Tier A test; 5q was first shown in Tier B too (unit 7).

**Cost.** Each scenario class runs once in its clean form; the injections rerun
only the Tier A test they need, and there is no final clean rerun (control C
proves the restorations instead). The run that signed off unit 9 graded 48
controls in 1084 s. The harness prints each control's time and the total on
its ``SUMMARY`` line.

``--only IDS`` runs named controls plus the clean Tier A run every one needs;
``--self-test`` runs H; ``--preflight`` checks the host and stops. Exit 0 every
control bit; 1 a control failed; 2 misuse; 3 the environment is not ready; 4 a
harness failure.

.. _dev-e2e-ci:

CI, D-006 and D-007
===================

**No CI job runs the end-to-end tier** (P14-13). It needs the ``D-006`` local
fixture, and the CI fixture half of ``D-006`` is open; nothing may present the
local fixture as the CI fixture. The release steps whose real form needs the
CI fixture or Phase 16's packaged release artefact remain stubs that exit 70
and name what they wait for, with the local command to run meanwhile:
``scripts/ci/release-smoke.sh``, ``release-e2e-canonical.sh``,
``release-e2e-limelight.sh``, ``release-e2e-noxml.sh`` and
``nightly-gui-tests.sh``. Their owner is still PHASE-14; reassigning it is
tier 1's decision. ``release-package.sh`` is Phase 16's stub. They also need a
display on the runner, which nothing provisions yet. Run any of them to read
its text.

``release-no-test-bridge.sh`` is the exception: it is a real step today.

``D-007``: every upload in every test goes to the local fake on loopback
(``FakeLimelightServer``; see :ref:`dev-limelight-integration`). The nightly
sandbox slot, ``scripts/ci/nightly-limelight-sandbox.sh`` with its
``nightly.yml`` job, is wired and exits 70 while
``COMETGUI_LIMELIGHT_SANDBOX_URL`` is empty; there is no default URL. Naming a
sandbox is the owner's decision, and so is how its submit key reaches CI: the
workflow check forbids ``secrets.`` references.

.. _dev-e2e-gaps:

Known gaps and residue
======================

What the end-to-end tier does not prove, and what it found, stated plainly.
"Escalated" means reported to tier 1 for a decision or a later phase; nothing
below was changed in the product by Phase 14 except E3 and E4.

.. list-table::
   :header-rows: 1
   :widths: 10 90

   * - Item
     - Gap or finding

   * - Platforms
     - **Linux x86-64 only.** The macOS and Windows argfiles have never been
       executed; the macOS bundler is believed (unverified) to reject a
       version whose first number is zero. ``release-no-test-bridge.sh`` does
       not unpack macOS or Windows installers yet. ``AC-TST-07`` and
       ``AC-INS-01`` are therefore not met on those platforms.
   * - CI
     - No CI job runs either tier (:ref:`dev-e2e-ci`); it waits on the
       ``D-006`` CI fixture and Phase 16's packaged artefact.
   * - Smoke test
     - ``smoke-installed.sh`` and its ``--self-test`` are run by hand and by
       no harness or CI job; ``release-smoke.sh`` is a stub. Tier B repeats
       its launch-environment, runtime and install-tree checks.
   * - E1
     - Tools are not installed automatically on Run: the product needs Comet
       and Percolator installed in the Tool Manager first (canonical step 15,
       ``AC-INS-02``). Escalated.
   * - E2
     - No project chooser; step 5 uses the default project. Escalated.
   * - E3
     - Sections never learned of an install after a garbage collection (a
       listener on a weakly held wrapper). **Repaired** in unit 2b.
   * - E4
     - The converter could not be installed under a non-ASCII home (its probe
       ran with an empty environment). **Repaired**.
   * - E5
     - With Comet's default top 5 the pinned converter refuses this fixture's
       run (one spectrum reported twice with the same peptide). The scenario
       sets top 1. Whether the product should restrict or deduplicate is an
       open question. Escalated.
   * - E6
     - PDV drawing is **not proved in Tier B on this host**: the packaged
       application has no seam through which PDV can be given the project's
       X11 libraries, and the host has none. Tier B proves PDV is installed and
       launched from the installed runtime, failing only on ``libX11.so.6``.
       Tier A proves the drawing through its third seam.
   * - E7
     - When the packaged application's ``start`` fails, the process stays
       alive with no window. Escalated, not repaired.
   * - E8
     - Under a non-UTF-8 locale an accented ``XDG_DATA_HOME`` is dropped
       without a word and the tool cache goes under ``user.home``.
       ``TierBLocaleControlTest`` records it. Belongs with Phase 16's
       launcher locale repair.
   * - E9--E11
     - Found by the failure-path suite: a failed install never says why (E9);
       the pre-run path-length limit is not Comet's 511-byte limit (E10); the
       stepper shows Percolator "Running" after a Comet failure (E11).
       Escalated.
   * - E12
     - **Serious**: a run whose provenance could not be written was recorded
       SUCCEEDED. **Repaired** between Phases 14 and 15: such a run ends
       FAILED with the reason in the result, ``run.json`` and the event log,
       and the interface says so. Failure paths 17 and 18 map the tests
       (``SealFailureRunTest``, ``FileSizeLimitRunTest``,
       ``SealFailureUiTest``).
   * - E13--E15
     - A stale-lock takeover is never shown (E13); an interrupted run is
       labelled "running" (E14) and cannot be retried (E15). Escalated.
   * - GUI list
     - The end-to-end tier and the GUI suite together have not been audited
       item by item against the specification's *JavaFX GUI automation*
       list; the learned-weights chart it names does not exist
       (``WeightsPane`` draws none). ``AC-TST-05`` stays open on that.

See also :ref:`dev-testing` for the rest of the test suite and
:ref:`dev-traceability` for the evidence each acceptance criterion carries.
