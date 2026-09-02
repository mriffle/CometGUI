.. _dev-architecture:

============
Architecture
============

This page describes the layering CometGUI **has**, not the layering it is
supposed to have. ``specification.rst`` is the authority on what is required;
where the two differ, this page says so and names the difference. Where
something is deferred, it names the phase that owns it.

.. note::

   **State after Phase 02 -- Application Shell and Navigation**, written on
   2026-08-30 against the tree as committed, and amended the same day once the
   GUI test harness landed (`The GUI test harness`_). Phase 02 builds the frame
   and deliberately contains no scientific behaviour: seven of the twelve Maven
   modules still hold nothing but ``package-info.java``, and
   `What Phase 02 deliberately did not build`_ collects what a later phase must
   not assume exists.

   Every measured number quoted below was produced by running the thing --
   either by the Phase 02 orchestrator at a work-unit sign-off, recorded in
   ``handoffs/PHASE-02-worklog.rst``, or, where it is marked as such, by the
   agent that wrote this page. None was copied from another document.

.. contents:: Contents
   :depth: 2
   :local:

The module graph as it exists
=============================

Twelve Maven modules, listed in ``pom.xml`` in build order. The dependency
edges below were read out of the module POMs, not from the package structure:
an artefact name and a package name do not always agree here, and one pair
deliberately does not.

.. list-table:: The twelve modules, their packages and their edges
   :header-rows: 1
   :widths: 22 22 28 28

   * - Module
     - Root package
     - Depends on (module scope)
     - State after Phase 02

   * - ``cometgui-domain``
     - ``org.cometgui.domain``
     - **nothing**
     - Real: ``ports``, ``platform``, ``log``, ``run``, plus Phase 01's
       ``build``. Its ``project``, ``params``, ``provenance``, ``results`` and
       ``tools`` subpackages are empty.

   * - ``cometgui-process``
     - ``org.cometgui.tools.process``
     - ``domain``
     - **Empty.** Phase 03 fills it with the process service.

   * - ``cometgui-provenance``
     - ``org.cometgui.provenance``
     - ``domain``
     - **Empty.** Phase 04.

   * - ``cometgui-tools``
     - ``org.cometgui.tools`` (minus ``.process``)
     - ``domain``, ``process``
     - **Empty.** Phases 08, 09, 11 and 12 fill the adapter subpackages.

   * - ``cometgui-install``
     - ``org.cometgui.install``
     - ``domain``, ``process``
     - **Empty.** Phase 05.

   * - ``cometgui-params-comet``
     - ``org.cometgui.params.comet``
     - ``domain``
     - **Empty.** Phase 06.

   * - ``cometgui-params-percolator``
     - ``org.cometgui.params.percolator``
     - ``domain``
     - **Empty.** Phase 09.

   * - ``cometgui-results``
     - ``org.cometgui.results``
     - ``domain``
     - **Empty.** Phase 10.

   * - ``cometgui-workflow``
     - ``org.cometgui.workflow``
     - ``domain``, ``tools``, ``provenance``, ``results``,
       ``params-comet``, ``params-percolator``
     - Partly real: ``state`` is built; ``engine`` and ``steps`` are empty and
       belong to Phase 08.

   * - ``cometgui-ui``
     - ``org.cometgui.ui``
     - ``domain``, ``workflow``, ``results``, ``provenance``,
       ``params-comet``, ``params-percolator``
     - Real: ``viewmodel``, ``view``, ``controls``. ``dialogs`` is empty.

   * - ``cometgui-app``
     - ``org.cometgui.app``
     - all ten above, plus ``io.github.mkpaz:atlantafx-base`` 2.1.0
     - Real: ``bootstrap``, ``config``. The only module with a ``main``.

   * - ``cometgui-archtests``
     - ``org.cometgui.archtests``
     - all eleven, at **test** scope, plus ``archunit-junit5`` 1.5.0
     - Real. No main sources at all; it exists to hold the rules.

Two things in that table are easy to misread.

**``cometgui-process`` publishes** ``org.cometgui.tools.process``, not
``org.cometgui.process``. That is the specification's package layout, and it
matters to the architecture tests: ``ProductClasses.MODULE_PACKAGES`` names
``org.cometgui.tools`` and ``org.cometgui.tools.process`` separately, and
``ClassImportCensusTest.belongsTo`` attributes a class to the *longest*
matching module package, so a class in the process service does not count
towards the adapter module's census.

**``cometgui-app`` depends on everything.** It is the composition root, and the
only place a concrete implementation of a port is chosen. AtlantaFX is
referenced from this module and from nowhere else, which is a deliberate
consequence of the layering rule below: the UI's allowlist does not include
``atlantafx..``, so putting the theme in ``cometgui-ui`` would have required an
exemption on an architecture rule, and this phase added none.

The shape, with the table above as the authority::

    tier 4    cometgui-app            the only entry point; depends on all ten
                 |                    product modules, plus atlantafx-base
                 v
    tier 3    cometgui-ui  ------->  cometgui-workflow
                 |                          |
                 |                          v
    tier 2       |                   cometgui-tools      cometgui-install
                 |                          |                    |
                 |                          v                    v
    tier 1       |                   cometgui-process  <---------+
                 |                          |
                 |    cometgui-provenance   cometgui-results
                 |    cometgui-params-comet cometgui-params-percolator
                 |                          |
                 v                          v
    tier 0    cometgui-domain         depends on nothing at all

              cometgui-archtests      outside the graph: no main sources, and a
                                      TEST-scope dependency on all eleven

The tiers are a reading aid, not a declared concept; nothing in the build
enforces "tier". What the build enforces is the eight rules below.

The layering rules and what enforces them
=========================================

Why ArchUnit is the *first* line of defence
-------------------------------------------

An earlier handoff got this backwards, so it is worth stating flatly.

**A JavaFX dependency in** ``cometgui-domain`` **compiles.** ``cometgui-domain``
has no Maven dependencies at all, and it does not need one: the pinned BellSoft
Liberica JDK 25.0.4.1+1 is the *Full* variant, which carries JavaFX 25.0.4+1
inside the JDK image as resolved system modules. There is deliberately no
``org.openjfx:javafx-*`` dependency anywhere in this build, and the root POM
says why -- a Maven JavaFX dependency would put a second copy of the ``javafx.*``
packages on the class path. The consequence is that nothing stops a domain
class importing ``javafx.scene.control.Label``: the Maven graph cannot forbid
what the JDK already supplies.

Verified directly by the agent that wrote this page, outside Maven, with the
same compiler settings ``cometgui-domain`` uses (``--release 25``, no
dependencies, no ``--add-modules``)::

    $ cat org/cometgui/domain/probe/Probe.java
    package org.cometgui.domain.probe;
    import javafx.scene.control.Label;
    public final class Probe {
        private Probe() {}
        public static String kind() { return Label.class.getName(); }
    }
    $ javac --release 25 -d out org/cometgui/domain/probe/Probe.java
    $ echo $?
    0

So ArchUnit is not a second opinion confirming what the build already refuses.
It is the **only** thing that refuses it, and phase 02 gate item 3 -- "an
ArchUnit test proves the domain module has no JavaFX dependency, and it fails if
one is introduced" -- rests on it entirely.

The eight rules
---------------

All eight live in
``cometgui-archtests/src/test/java/org/cometgui/archtests/LayeringRulesTest.java``,
one JUnit test each, every one checked against the single class import in
``ProductClasses``. They are the specification's *Architecture tests* list.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Rule
     - What it forbids

   * - the domain does not depend on JavaFX
     - No class in ``org.cometgui.domain..`` may depend on ``javafx..`` or
       ``com.sun.javafx..``.

   * - the UI depends only on the domain and the application APIs
     - ``org.cometgui.ui..`` may depend **only** on ``java..``, ``javax..``,
       ``javafx..``, its own package, and ``org.cometgui.domain..``,
       ``.workflow..``, ``.results..``, ``.provenance..``, ``.params..``.
       Not the tool adapters, not the installer, and not AtlantaFX.

   * - tool adapters do not depend on UI classes
     - ``org.cometgui.tools..`` may not reach ``org.cometgui.ui..`` or
       ``javafx..``.

   * - provenance and hashing do not depend on the UI
     - ``org.cometgui.provenance..`` may not reach ``org.cometgui.ui..`` or
       ``javafx..``.

   * - the parameter parser and writer do not depend on JavaFX
     - ``org.cometgui.params..`` may not reach ``javafx..`` or
       ``com.sun.javafx..``.

   * - the major layers have no dependency cycles
     - ``slices().matching("org.cometgui.(*)..")`` must be free of cycles. The
       slice is the **second** package segment, so the process service falls
       inside the ``tools`` slice rather than forming one of its own.

   * - process creation is confined to the process service (``R-PROC-02``)
     - No class outside ``org.cometgui.tools.process..`` may depend on
       ``ProcessBuilder`` or call ``Runtime.exec``.

   * - the UI contains no hashing, download or archive-extraction logic
     - ``org.cometgui.ui..`` may not reach ``java.security..``, ``java.net..``,
       ``java.util.zip..`` or ``java.util.jar..``.

The last rule's own source comment is honest about its limits, and this page
repeats the limit rather than papering over it: *"no scientific logic"* and
*"no parsing logic"* are **not** expressible as dependency rules. A hand-written
q-value comparison or a ``split(",")`` loop inside a controller uses nothing but
``java.lang`` and ``java.util``. Those halves of the specification's rule remain
a review obligation, constrained indirectly by the UI allowlist -- a controller
that cannot reach the results or install packages has nothing to parse.

A rule set that imports nothing passes everything
--------------------------------------------------

Every ArchUnit rule is a statement about the classes ArchUnit was given. Given
none, ``noClasses().that()...`` has nothing to reject and every rule is green.
A misconfigured ``@AnalyzeClasses`` package, a module missing from the test
class path, or an ArchUnit that cannot read Java 25 class files all produce a
suite that checked nothing and said so in no way at all.

``ClassImportCensusTest`` is the defence, and it runs against the same import:

* a floor of ``MINIMUM_IMPORTED_CLASSES = 50`` -- deliberately below today's
  number, so a later phase need not edit it, and far above zero;
* every one of the eleven product module packages must contribute at least one
  class, which is the guarantee that actually holds as the product grows;
* three named classes must be present, so the import is of the product rather
  than of stubs;
* a rule built over ``org.cometgui.nosuchlayer..`` must **fail**, which proves
  ``archRule.failOnEmptyShould`` is in force rather than assuming it. The
  setting is written explicitly in
  ``cometgui-archtests/src/test/resources/archunit.properties`` for that reason;
* the census is written to ``target/archunit-import.txt``, which
  ``scripts/build.sh`` reads back.

Measured at the sign-off of work unit 7 on 2026-08-30:
``ok 106 classes imported from org.cometgui`` and
``ok 8 architecture rule(s) checked, 0 failures``. The import was 55 classes
after Phase 01 and 75 partway through Phase 02, so the census is growing with
the product rather than standing still -- which is the number to watch, because
a *shrinking* census with a green suite is exactly the failure it exists to
catch.

The MVVM boundary as built
==========================

``org.cometgui.ui.viewmodel`` -- no toolkit
--------------------------------------------

Five classes and one package-private helper:

* ``SectionId`` -- the specification's Information Architecture as a type: the
  eight primary sections (Run, Comet Parameters, Percolator, Results,
  Visualisation, Limelight, Provenance, Console) and the two the specification
  allows to be secondary (Tool Manager, Settings), told apart by
  ``isPrimary()``.
* ``NavigationViewModel`` -- the section list and which one is selected, plus
  ``selectNext()`` / ``selectPrevious()``.
* ``ConsoleViewModel`` -- a stage filter and a minimum-severity filter over the
  domain's bounded message log.
* ``StageStepperViewModel`` -- a ``StepState`` per ``WorkflowStage`` and the
  ``RunState`` derived from them.
* ``HostBaselineViewModel`` -- the startup banner: three levels from the
  domain's five outcomes.
* ``NonNullProperty`` -- package-private; a ``ReadOnlyObjectWrapper`` that
  rejects ``null`` at ``set`` and *refuses* ``bind``, because a bound JavaFX
  property is read straight from its binding and would walk past the null check.

**What this package must not contain**, and why the reason is mechanical rather
than stylistic: ``javafx.scene``, ``javafx.stage``, ``javafx.application``,
``javafx.fxml`` and ``Platform.runLater(``. ``Platform.runLater`` throws
``IllegalStateException`` when no toolkit has been started, so a single call
would turn every test of this package into a test that needs a display -- and
the whole reason the layer exists is that it does not.

JavaFX **properties and observable collections** (``javafx.beans``,
``javafx.collections``) are deliberately *not* forbidden. They need neither a
started toolkit nor a display, and they are how a view-model publishes state.
The line is drawn at the scene graph, the stage, the application class and the
FX application thread.

``ViewModelIndependenceTest`` enforces this over the whole package by scanning
the source text, and -- because a scan that read the wrong directory would pass
over anything -- it separately asserts that it read exactly the six files it
expects and that each contains ``package org.cometgui.ui.viewmodel;``.

``org.cometgui.ui.view`` and ``.controls``
-------------------------------------------

``view`` holds ``ShellView`` (header, left navigation, content area),
``SectionPane`` (one per section: heading, description, arrival note) and
``SectionArrivals`` (package-private -- the "this section arrives in phase NN"
text, with the phase numbers read from ``phases/index.rst``).

``controls`` holds ``UiIds``, ``AccessibleControls``, ``StageStepper`` and, under
``controls/derived/``, ``ConsolePane``.

``UiIds`` lives in ``controls`` rather than ``view`` on purpose: both packages
set identifiers, and putting it here makes the dependency run one way --
``view`` composes ``controls`` -- instead of making the two point at each other.

How a view observes a view-model
--------------------------------

Every published property is **read-only**. ``NavigationViewModel`` exposes
``selectedSectionProperty()`` as a ``ReadOnlyObjectProperty``; ``select(...)``,
``selectNext()`` and ``selectPrevious()`` are the only ways it changes.

So the wiring is two explicit halves, **never** ``bindBidirectional``:

#. a listener on the view-model property that moves the interface
   (``ShellView`` calls ``showSelection()``);
#. a control action that calls ``navigation.select(section)``.

That is not a formality. **A JavaFX selection model reports** ``null`` **when
its selection is cleared**, and a bidirectional binding would push that ``null``
into a view-model that has no such state. Filtering it out at the one call site
is a line of code; recovering from a ``null`` selected section everywhere it is
read is not. ``NonNullProperty`` makes the attempt fail loudly at the setter
rather than much later at the reader.

Two further consequences a later phase inherits:

* Navigation does **not** wrap. ``selectNext()`` at the last section and
  ``selectPrevious()`` at the first do nothing and return ``false``, which is
  what a list control does and what lets a screen reader announce the boundary.
* ``ConsoleViewModel.refresh()`` is the only thing that moves messages into the
  view, and the view must call it on the FX application thread. The view-model
  does no marshalling of its own, and the console pane's coalescing is what
  makes that affordable (see `The console and its bound in memory (R-PROC-03)`_).

The coverage rule on this package, and what it measured
-------------------------------------------------------

The specification's *Testing Strategy / Coverage* gives "UI-independent
view-model and presenter logic" a target of its own: **>= 80% line**. The parent
POM's ``coverage-check-viewmodel`` JaCoCo rule enforces it as a ``PACKAGE`` rule
matching ``org.cometgui.ui.viewmodel*`` and nothing else, and
``cometgui.coverage.viewmodel.skip=false`` in ``cometgui-ui`` switches it on.
The JavaFX rendering glue in ``view`` and ``controls`` has **no** numeric target,
because the specification gives it none.

That asymmetry is a live hazard and is named here so nobody trips over it: a
class moved into ``viewmodel`` to escape the rendering layer's lack of a target,
or moved out of it to escape this one, is a weakening of a gate.

The rule was inert until work unit 5 landed. At its sign-off on 2026-08-30
``scripts/build.sh`` printed
``ok cometgui-ui line 100.0% (176/176) branch 100.0% (40/40) [view-model >=80%
line]``, and the phase orchestrator separately read the
``<package name="org/cometgui/ui/viewmodel">`` element of ``jacoco.xml`` --
``LINE missed=0 covered=175``, ``BRANCH missed=0 covered=40``.

**The two line counts differ because they measure different report elements,
not because either is wrong.** ``scripts/build.sh`` reports the ``cometgui-ui``
**bundle**; the gate's own rule is ``PACKAGE``-scoped on
``org.cometgui.ui.viewmodel``. Both are 100%, and it is the package figure the
rule is evaluated against. Anyone reading a coverage number for this gate should
read the package element, because a bundle figure would go on looking healthy
while the gated package rotted underneath it.

Work unit 6 added seventeen files of views and controls and left those package
counters unchanged, which is the evidence that the views did not dilute the
gated package.

The injection seams (``R-PROC-01``)
===================================

``R-PROC-01`` names seven seams: clock, environment reader, process runner,
downloader, filesystem abstraction, run-ID source and hash service. All seven
exist. An eighth was added, for a reason given below.

.. list-table::
   :header-rows: 1
   :widths: 26 32 42

   * - Seam
     - Where the interface lives
     - Implementation today

   * - clock
     - ``java.time.Clock`` -- **no project interface**
     - ``Clock.systemUTC()``. There is deliberately no ``ClockPort``: the JDK
       already makes the clock injectable and fixable, and a wrapper would be a
       second thing to fake.

   * - environment reader
     - ``org.cometgui.domain.ports.EnvironmentReader``
     - ``org.cometgui.app.config.SystemEnvironmentReader``. Outside it, nothing
       in the product calls ``System.getenv`` or ``System.getProperty``.

   * - filesystem
     - ``org.cometgui.domain.ports.FileSystemAccess``
     - ``org.cometgui.app.config.PlatformFileSystemAccess``. Deliberately the
       smallest set of questions the shell and the baseline check actually ask,
       plus the application data directory.

   * - run-ID source
     - ``org.cometgui.domain.ports.RunIdSource``
     - ``org.cometgui.app.config.ClockRunIdSource``.

   * - process runner
     - ``org.cometgui.domain.ports.ProcessRunner``
     - **Interface only. Phase 03.**

   * - hash service
     - ``org.cometgui.domain.ports.HashService``
     - **Interface only. Phase 04.**

   * - downloader
     - ``org.cometgui.domain.ports.Downloader``
     - **Interface only. Phase 05.**

   * - glibc version (the eighth)
     - ``org.cometgui.domain.platform.GlibcVersionSource``
     - ``org.cometgui.app.config.FfmGlibcVersionSource``. Not named by
       ``R-PROC-01``, but ``R-PLAT-01``'s check reads a native symbol, and a
       check that could only be tested on a glibc host would not be a check.

The package also holds two validated value types that are *not* seams:
``ToolCommand`` (argument array, absolute working directory, explicit
environment -- ``R-PROC-02`` and ``R-PROC-04`` as a type, with no
"command line" accessor a caller could paste into a shell) and ``FileHashes``
(MD5 **and** SHA-256, both required, so the type cannot represent a file that
was hashed only one way). Alongside them sit the callback interfaces
``ProcessListener``, ``RunningProcess`` and ``DownloadProgressListener``.

How the composition root models the three that do not exist
------------------------------------------------------------

``org.cometgui.app.config.ApplicationServices`` is the one place an
implementation is chosen. Two tempting shortcuts are deliberately not taken with
the three missing seams:

* **No fake.** A no-op ``HashService`` returning a constant would let Phase 04
  build a provenance record out of fiction and stay green.
* **No null field.** A seam that is silently ``null`` fails at a call site far
  from the cause, in a ``NullPointerException`` that names nothing.

Instead the absence is modelled twice and both forms are tested.
``processRunner()``, ``hashService()`` and ``downloader()`` return
``Optional``, so a caller that can work without one must say so at compile
time; ``requireProcessRunner()``, ``requireHashService()`` and
``requireDownloader()`` are for callers that cannot, and throw
``IllegalStateException`` naming the port and the phase --
``the process runner is not wired yet: org.cometgui.domain.ports.ProcessRunner
is delivered by phase 03``. When a phase lands one, it is passed to the
constructor, the ``Optional`` becomes present, the ``require`` accessor stops
throwing, and no caller has to be found and rewritten.

**The run message log is not published from here, and that is a decision Phase
03 inherits.** A composition root handing out a mutable ``BoundedMessageLog`` is
publishing shared mutable state, which SpotBugs reports as ``EI_EXPOSE_REP`` and
is right about. It is therefore *injected* -- ``CometGuiApplication`` takes one
and passes it to the console -- which is the same sharing with the ownership
stated at the call site. When Phase 03's process service needs to write to that
same log, whoever wires it up decides where the log lives; this class
deliberately does not decide it in advance.

The host baseline (``R-PLAT-01``) as built
==========================================

The split
---------

``R-PLAT-01`` asks for a declared minimum host baseline, verified at startup: a
64-bit OS, and on Linux a glibc version sufficient for the tools the user
selects. The work is split across two modules on purpose.

**Pure comparison logic, in** ``org.cometgui.domain.platform``:
``GlibcVersion`` (parse and compare -- ``2.36``, ``2.3.4`` and
``2.31-0ubuntu9.9`` are all accepted, the packaging suffix is kept for
diagnostics but takes no part in comparison, and an absent third component is
zero), ``GlibcVersionSource`` (interface only), ``HostBaselineOutcome`` (five
outcomes, each declaring whether it is blocking), ``HostBaselineReport`` (an
outcome plus the complete sentence shown to the user) and
``HostBaselineVerifier``.

The message is part of the value rather than something a controller composes,
because ``R-PLAT-03`` requires the diagnostic to name the host's value *and* the
required value, and both are known there and nowhere else. Two of the five
outcomes block (``NOT_64_BIT``, ``GLIBC_TOO_OLD``); two are warnings
(``ARCHITECTURE_UNDETERMINED``, ``GLIBC_UNDETERMINED``), because missing
evidence is not negative evidence and ``R-PLAT-02``'s runtime probe settles tool
compatibility by execution anyway.

**The native probe, in** ``org.cometgui.app.config.FfmGlibcVersionSource``: it
calls ``gnu_get_libc_version()`` through ``java.lang.foreign``.

Why the probe is not a subprocess
---------------------------------

The obvious probe is ``ldd --version``. It was not available to this phase:
``R-PROC-02`` confines ``ProcessBuilder`` to ``org.cometgui.tools.process``, the
ArchUnit rule above enforces it, and the process service is Phase 03's. A
``ProcessBuilder`` in ``cometgui-app`` would have failed the architecture suite.

Reading the symbol directly is also the better answer: it is the version of the
C library this process is *linked against*, which is the number that decides
whether a managed tool loads, where ``ldd`` reports whatever ``ldd`` is found on
``PATH``.

``detect()`` returns ``Optional.empty()`` and never throws. Four things make it
empty -- the symbol is absent (musl, macOS, Windows), the lookup or call fails,
the function returns a null pointer, or the string will not parse -- and the
domain turns empty into ``GLIBC_UNDETERMINED``, a warning. An implementation
that threw would convert every non-glibc host into a startup crash. The
``catch`` is on ``Throwable`` rather than ``Exception``, because
``MethodHandle.invokeExact`` is declared to throw it and a denied or failed
foreign call can arrive as an ``Error``.

``--enable-native-access`` is passed to surefire by ``cometgui-app/pom.xml``.
Phase 16's ``jpackage`` configuration must pass it to the packaged application:
the warning is cosmetic on JDK 25 and becomes an error in a later JDK.

The glibc floor is a parameter, not a constant
-----------------------------------------------

``HostBaselineVerifier.verify(GlibcVersion requiredGlibcVersion)`` takes the
floor as an argument, and its documentation forbids it to grow a hard-coded one.
Comet 2026.02.2 and Percolator 3.07.1 are built on different distributions and
do not require the same version, and the only trustworthy statement about a
binary's requirement comes from running it -- that is ``R-PLAT-02``, and Phase
05's runtime probe owns it. A wrong constant here would either block a host that
works or admit one that does not, and both are worse than the probe.

At startup no tool has been selected yet, so the only question that can honestly
be asked is whether the host is below the floor of *everything the product could
ever offer*. That is ``CometGuiApplication.STARTUP_GLIBC_FLOOR``, **2.14** --
which ``specification.rst`` records as the requirement of Percolator 3.06.5's
portable Linux build and the lowest floor found anywhere. A host that passes it
may still be too old for the tools the user goes on to choose.

A blocking outcome is reported and the application **starts anyway**: there is
no ``Platform.exit()`` and no ``System.exit()`` on that path. A user whose
machine is unsupported should be able to read the diagnostic, open the tool
manager and copy the message. The phase that owns running a workflow owns
refusing to start one. Blocking and warning are distinguished in *text* --
``bannerText()`` begins ``Cannot continue:`` or ``Warning:``, followed by the
diagnostic -- not by colour, which is the specification's Accessibility
principle applied.

What is and is not exercised here
----------------------------------

**Only the glibc-present path is exercised on this machine.** The build host is
Debian bookworm on x86-64 and its ``gnu_get_libc_version`` answers ``2.36``;
this project's environment contains no musl, macOS or Windows host, and none of
the packaging or GUI work has ever run on a non-Linux machine.

The other branches are not left to a comment. ``FfmGlibcVersionSource`` takes a
``SymbolLookup`` as a constructor parameter, so a test drives "the symbol is
absent" with a lookup that finds nothing, "the lookup failed" with one that
throws, and "the symbol is null" with ``MemorySegment.NULL``; and the parsing
step is package-private so an unparseable version can be exercised without a
native call. One branch is untestable here and is marked in the source as a
guard: a real ``gnu_get_libc_version`` returning a null pointer, which no glibc
does.

The domain half is fully exercised, because every outcome is reachable through
the two seams. At the unit 1 sign-off on 2026-08-30 ``cometgui-domain`` measured
``LINE missed=0 covered=237`` and ``BRANCH missed=0 covered=124`` against the
0.90/0.85 gate, with PIT ``total=129 killed=129 survived=0`` against the 80%
threshold. Raising ``STARTUP_GLIBC_FLOOR`` to ``9.99.0`` in a sandbox failed six
bootstrap tests with the real diagnostic, host version and required version
named -- which is ``R-PLAT-01`` verified against a real probe rather than
against a fake.

The FFM probe writes its own evidence file. At the unit 7 sign-off
``cometgui-app/target/glibc-probe.txt`` read
``symbol gnu_get_libc_version text 2.36 parsed 2.36.0``.

The console and its bound in memory (``R-PROC-03``)
===================================================

``R-PROC-03`` requires the in-memory console buffer to be capped with a
documented retention policy, so that a tool emitting hundreds of megabytes
cannot exhaust the heap. As built there are **two** caps, in two layers, and
they are reported to the user separately.

``BoundedMessageLog`` -- the model's cap
-----------------------------------------

In ``org.cometgui.domain.log``, with ``LogMessage`` and ``MessageSeverity``.

:Retention policy: the newest *N* messages are retained, where *N* is the
   capacity fixed at construction; appending the *N+1*'th discards the
   **oldest**. Nothing else is ever discarded.
:Capacity: ``DEFAULT_CAPACITY = 10_000`` -- roughly a terminal's scrollback,
   enough for a whole ordinary Comet or Percolator run, and a few megabytes at a
   typical line length.
:Discarding is not silent: ``discardedCount()`` is a ``long`` (a tool that
   overflows 10,000 lines can overflow an ``int``'s worth too, and a count that
   wraps negative is worse than none) and ``clear()`` resets it, because a
   console the user has just emptied has nothing missing to report.
:Thread safety: every method body runs inside ``synchronized (lock)`` on a
   **private** monitor, so no caller can deadlock the log by locking the log.
   The process service will append from a tool's stdout and stderr reader
   threads while the FX thread reads; an unsynchronised ``ArrayDeque`` under two
   appenders can lose an element or throw at the reader.

This is only half of ``R-PROC-03``. The other half -- every line reaching the
run's log files on disk as it arrives -- belongs to Phase 03, and it is what
makes discarding acceptable: nothing is lost, it is simply no longer in memory.

Measured by the flood test at the unit 2 sign-off on 2026-08-30, printed by the
test itself::

    flood: appended=1000000 capacity=10000 size=10000 discarded=990000
    heapBefore=5116176 heapAfter=7238864 growth=2122688 limit=33554432
    elapsedMillis=497

A real measurement, 16x inside its documented bound, and the cap is proved
*exact* rather than approximate: changing one assertion from
``size() == capacity`` to ``size() > capacity`` in a sandbox failed five
assertions, including ``expected: <10000> but was: <10001>``.

``ConsolePane`` -- the document's cap
--------------------------------------

The rendered document has its own, separate limit:
``DEFAULT_MAX_RENDERED_LINES = 5_000``. A ``TextArea`` holding every line a tool
ever wrote would be exactly the unbounded buffer the rule forbids, whatever the
model behind it did.

**The two truncations are reported separately, because they mean different
things.** The summary line reads, for example::

    12,431 earlier lines discarded. Showing the newest 5,000 of 7,600
    matching lines.

The first sentence is output the application **no longer has**; the second is
output it has but is not drawing. Both counts are grouped in ``Locale.ROOT``, so
the sentence is byte-identical on every machine.

The pane also carries the coalescing flush kept from upstream:
``requestRefresh()`` may be called from any thread and once per line, and the
first caller after a flush schedules exactly one ``Platform.runLater``; every
caller until it runs is absorbed. ``refreshNow()`` throws
``IllegalStateException`` off the FX thread rather than corrupting the scene
graph quietly. The pane counts its flushes, because a test that cannot count
them cannot tell coalescing from luck.

Stable identifiers and accessibility
====================================

One class holds every identifier
--------------------------------

``org.cometgui.ui.controls.UiIds`` holds every string this interface passes to
``setId(...)``. ``R-TEST-04`` requires stable semantic identifiers and forbids
locating controls by pixel coordinates or brittle CSS ancestry, so a test does
``scene.lookup("#" + UiIds.CONSOLE_OUTPUT)``.

The identifier is a contract between a view that sets it and a test that looks
it up, and those live in different source trees written by different agents. Ten
views each spelling ``"section-comet-parameters"`` from memory is a contract
that drifts silently -- the view compiles, the test compiles, and the lookup
returns ``null`` at run time.

**Derived, not repeated.** Anything with an identity in the model builds its
identifier from that model's own stable identifier rather than from a second
copy of the string::

    sectionPane(section)        ->  "section-"  + SectionId.id()
    navigationEntry(section)    ->  "nav-"      + SectionId.id()
    stepperStage(stage)         ->  "stage-"    + StageTag.id()
    stepperArrow(from, to)      ->  "stage-arrow-" + from.id() + "-" + to.id()
    consoleStageFilter(stage)   ->  "console-stage-filter-" + StageTag.id()
    consoleSeverityFilter(sev)  ->  "console-severity-filter-" + lowercased name

Renaming a section is then a change in one enum, and every view and every test
follows it. ``SectionId.id()`` is used *verbatim* as the ``fx:id``; it is
deliberately neither ``name()``, which would leak Java naming into markup, nor
``title()``, which changes whenever the wording does. ``VISUALISATION`` keeps
the specification's British spelling for the same reason -- the identifier is a
contract, so it is copied rather than spelled from memory.

Every control carries an explicit accessible name
--------------------------------------------------

Gate item 4 is "every control that exists has an accessible name; a test
enumerates them and fails on a missing one", from the specification's
Accessibility principle.

``AccessibleControls.named(control, text)`` is the only way a control here gets
one. It calls ``setAccessibleText`` and **rejects a blank name**. Letting a
``Labeled`` fall back to its own text was rejected deliberately: that fallback
covers a ``Label`` and a ``Button`` and nothing else, so it would be silent
exactly where it matters -- a text field, an icon-only action, a toggle whose
meaning is its position in a group.

**The controls nobody wrote.** A ``TextArea`` is one control in the source and
four in the scene graph: its skin builds a ``ScrollPane``, which builds two
``ScrollBar``\ s, and none of them exists until CSS is applied and the skin is
built. So ``named(...)`` also watches that control's children, recursively and
permanently, and gives anything that later appears underneath a **named** control
a generated name -- ``scroll bar within console-output``.

That scoping is the whole design. **Only descendants of an explicitly named
control are ever generated for.** A control a view forgot to name is a child of
a layout pane, not a descendant of a named control, so it is never reached and
fails the enumeration exactly as it should. A sweep over the whole scene would
have made the gate unfailable, which is worse than not having it.

**A control whose accessible text is assigned in more than one place needs every
assignment considered.** ``StageStepper`` names each stage-state label twice:
once at construction through ``named(...)``, and again in ``showStage()``, which
rewrites the text from the view-model whenever the state changes. Both
assignments are correct and both are wanted -- the second is what keeps a screen
reader hearing the *state* rather than the stage name twice -- but the pattern
has a consequence the Phase 02 orchestrator found by injecting a defect at the
sign-off of work unit 8: nulling the accessible text at the construction site
alone left the enumeration **green**, because ``showStage()`` put it back a few
lines later. That was an ineffective injection rather than a hole in the gate,
and re-injecting at a single-assignment site failed it as it should. The general
point is worth writing down: an injection that does not reach the code is a
harness failure, never a pass, which is why this project's harnesses carry
``assert_injected``. The enumeration test is what makes multiple assignment safe
in the first place -- it reads the *final* state of every control in the scene,
so it cannot be fooled by which of several assignments ran last.

Why a keyboard test must assert on the selected entry
------------------------------------------------------

The left navigation is a **roving tab stop**, and this trips people up.

* ``#navigation`` is a ``VBox`` and is deliberately **not** focus traversable.
* Exactly one navigation entry is focus traversable at a time: the **selected**
  one. ``ShellView.showSelection()`` sets ``setFocusTraversable(isSelected)`` on
  every entry.
* Tab therefore moves *into* and *out of* the navigation in one press each
  rather than ten; arrow keys move within it. Up and Left go back, Down and
  Right go forward.
* The arrow handler is an event **filter** on the container, so it runs before
  the focused button's own behaviour and cannot be beaten to the key by JavaFX's
  directional focus traversal. Keys are consumed whether or not the selection
  moved, so arrows only ever mean "move the selection" and Tab only ever means
  "leave the navigation".

**Consequence for tests: assert traversability on the selected navigation entry,
never on the container.** A test asserting ``#navigation`` is traversable is
asserting the opposite of the design and will fail.

Mnemonics are deliberately not the mechanism. They are a shortcut for a user who
already knows the interface, not a way to reach a control, and a gate item
resting on them would be proved by a test that never moved focus.

All ten section panes are built and attached from the moment the shell exists;
the selection decides which one is *visible and managed*, not which one exists.
That is what lets a test look a section up by identifier without navigating to
it, and what lets the accessible-name enumeration see every control in the shell
in one pass rather than only the selected section's. Unmanaged children are
excluded from layout, so the nine hidden panes cost a construction and nothing
per frame.

The derived-file regime (``D-001``, ``R-SEC-01``)
=================================================

``D-001`` decided that CometGUI derives from ``Noble-Lab/CasanovoGUI`` and is
GPL-3.0; ``R-SEC-01`` (amended in specification revision 10) requires every
derived file to retain upstream's notices and record its derivation, enforced by
the build rather than by review. ``CONTRIBUTING.rst``, *Files derived from
CasanovoGUI*, is the working instruction -- what to do when you add one. This
section says only how it is wired and what exists today.

The convention and the four executions
---------------------------------------

**A file is derived if and only if its path contains a** ``/derived/``
**segment.** Mechanical on purpose: which files carry an upstream notice must
not depend on anyone remembering.

Two headers, and two of every check:

* ``config/license/java-header.txt`` and
  ``config/license/java-header-derived.txt``;
* Spotless executions ``spotless-check`` (excluding ``**/derived/**``) and
  ``spotless-check-derived`` (including only those, with the same
  google-java-format step and its own ``indexFile``);
* Checkstyle executions ``checkstyle-check`` (excluding the same paths) and
  ``checkstyle-check-derived`` (with its own ``outputFile`` and ``cacheFile``);
* rule sets ``config/checkstyle/checkstyle.xml`` and
  ``config/checkstyle/checkstyle-derived.xml``, the second a **superset** of the
  first.

Phase 02 **extended** these checks; it did not relax them. The ordinary header
file and the ordinary ``Header`` module are byte-for-byte unchanged, and the
paths the ordinary executions now exclude are covered by the second pair. Each
rule set rejects the *other's* header, so nobody can claim a derivation they did
not make and nobody can omit one they did.

The one rule the derived set adds is the **per-file derivation record**, a
``Regexp`` module requiring, in the file's documentation comment::

    * Derived from Noble-Lab/CasanovoGUI <upstream path> at commit
    * <40-hex commit sha>, GPL-3.0, modified.

It cannot live in the header, because Spotless inserts one fixed block and
Checkstyle's ``Header`` module compares the first N lines literally. It applies
to ``package-info.java`` too: a ``TreeWalker`` check cannot be exempted for one
file name without a suppression filter, and this rule set has none.

The census that proves the two sets are exhaustive and disjoint
----------------------------------------------------------------

Excluding a file from a gate is legitimate only while something else covers it,
so ``scripts/build.sh`` proves after every build that:

#. the two sets together are **exactly** the ``.java`` files on disk, per
   module. This is the hole that matters -- a file excluded from set 1 whose
   path set 2's include pattern misses would be checked by nothing at all, and
   the build would stay green and say so;
#. the two sets are **disjoint**;
#. ``checkstyle-derived.xml`` still contains every module ``checkstyle.xml``
   does.

It is a shell-level check needing no Maven run, so it catches drift on the build
that introduces it. ``bash scripts/verify-quality-gates.sh`` proves it fails on
the defect: at the unit 4 sign-off, **deleting the whole**
``checkstyle-check-derived`` **execution left** ``mvn clean validate`` **green
and exit 0**, and the census caught it with
``UNCHECKED cometgui-app: Checkstyle inspected NEITHER file set for: ...`` and
``FATAL: 2 file-set census check(s) failed``. That run reported
``SUMMARY: 42 control(s) passed, 0 failed, in 120 seconds``.

The derived files that exist today
-----------------------------------

Two source files and their two ``package-info.java`` companions, all four under
a ``/derived/`` path, all four from ``Noble-Lab/CasanovoGUI`` at commit
``480b3013e7f8fb51a2b8c58681043821e3e7f865``:

.. list-table::
   :header-rows: 1
   :widths: 42 34 24

   * - File in this repository
     - Upstream file
     - What was kept

   * - ``cometgui-app/src/main/java/org/cometgui/app/config/derived/AtlantaFxThemes.java``
     - ``src/main/java/org/casanovo/gui/ui/Themes.java``
     - The seven AtlantaFX themes, the light/dark distinction, and the contract
       that theming is optional and its absence is not an error.

   * - ``cometgui-ui/src/main/java/org/cometgui/ui/controls/derived/ConsolePane.java``
     - ``src/main/java/org/casanovo/gui/ui/ConsoleView.java``
     - The coalescing flush -- one scheduled ``runLater`` per pulse however many
       lines arrive -- and the trimming cap at 5,000 lines.

Their ``package-info.java`` files carry the record as a statement about the
material the package holds; the surrounding prose says plainly that the
``package-info`` itself is new writing.

**The attribution is collective, and that is a verified fact rather than a
convenience.** ``Noble-Lab/CasanovoGUI`` carries **no per-file copyright
notice**: the phase orchestrator cloned it read-only and
``grep -rl Copyright --include='*.java' src`` matched nothing -- every upstream
source file begins with its ``package`` statement. The derived header therefore
attributes to *the CasanovoGUI authors* collectively and names the upstream path
and commit per file, which is the strongest honest form available. No notice was
dropped in copying. The line ``Copyright (C) 2026 The CometGUI authors.``
(``D-009``) stays on a derived file as on every other file; upstream is
attributed alongside it, not instead of it.

Two operational facts worth knowing before adding a third derived file:
``mvn spotless:apply`` applies only the **ordinary** header, so a derived file's
header is copied in by hand (``mvn -pl <module> spotless:apply@spotless-check-derived``
does apply the derived one); and a bare ``mvn checkstyle:check`` from the command
line now fails on derived files, because the include/exclude split lives on the
executions while the CLI invocation uses the plugin-level configuration.
``mvn -pl <module> validate`` runs all four executions and is the command to use.

The headless JavaFX recipe
==========================

The GUI tests run with no display, and the recipe is two independent pieces
carried in ``cometgui-ui/pom.xml`` and ``cometgui-app/pom.xml`` -- duplicated
deliberately, because the Monocle jar is fetched into each module's own
``target/`` and the ``--patch-module`` path is module-relative.

**An injected Monocle.** The Liberica Full JDK's ``javafx.graphics`` contains no
Monocle at all, so ``-Dglass.platform=Monocle`` alone fails trying to load
``libglass.so``. ``org.testfx:openjfx-monocle`` supplies a headless Glass
platform; it is fetched by ``maven-dependency-plugin`` and injected with
``--patch-module javafx.graphics=...`` plus the ``--add-exports`` and
``--add-opens`` pairs the platform factory needs reflectively, then
``-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw``.

**A real font stack.** A ``Scene`` containing any ``Control`` initialises CSS on
its first ``Node``, which calls ``Font.getDefault()``; with no fonts that fails
with ``fontFactory is null`` and every GUI test dies before its first assertion.
The stack is fetched into ``tools/fontstack-bookworm-20260829/`` and surefire
exports ``LD_LIBRARY_PATH``, ``FONTCONFIG_PATH`` and ``XDG_DATA_HOME`` at it.

**This is the Linux/amd64 recipe and nothing else.** Nothing here has been
executed on Windows or macOS. Phase 00 established it, Phase 14 owns proving it
elsewhere, and Phase 00's evidence is
:doc:`/feasibility/gui-automation-spike`. :doc:`testing` carries the same recipe
from the test suite's side, including the two ways each half fails without
naming its cause.

A note for anyone re-running the suite in a sandbox: ``git archive`` does not
carry ``tools/`` (it is gitignored), so a sandbox must symlink it before the
JavaFX tests can run. Without it they fail loudly with ``the project-local font
stack is missing ... Run: bash scripts/fetch-fontstack.sh``, which is the
designed behaviour and not a defect.

The GUI test harness
====================

Everything in this section lives in ``cometgui-app/src/test/java`` and in no
other source root. That is ``R-TEST-06`` taken literally: test-only machinery
must never reach a shipped artefact, and the cheapest way to keep that true is
for the machinery to live where it cannot be packaged. Nothing here is compiled
into ``cometgui-app.jar``.

The ``FxUiDriver`` abstraction, and why there are two of them
-------------------------------------------------------------

``org.cometgui.app.uidriver`` holds ``FxUiDriver`` (the interface),
``AbstractFxUiDriver`` (package-private: thread marshalling and every read of
scene-graph state, so the two implementations differ *only* in how input is
made), ``TestFxUiDriver``, ``RobotFxUiDriver`` and ``RunningApplication``.

The specification's *JavaFX GUI automation* clause is why the interface exists:
TestFX's compatibility "shall be proven in an early spike rather than assumed.
If it cannot operate reliably in CI, the project shall retain the same test
semantics behind a small ``FxUiDriver`` abstraction and use a compatible robot
or accessibility automation mechanism." Phase 00's spike found TestFX 4.0.18
does work on the pinned JDK/JavaFX pair and recommended keeping the fallback as
a first-class citizen.

So there are two implementations, and **both are exercised on every build**:

* ``TestFxUiDriver`` -- on TestFX 4.0.18, using ``FxRobot`` and nothing else.
  TestFX's own ``FxToolkit`` does *not* start the application, which is what
  keeps the two drivers comparable.
* ``RobotFxUiDriver`` -- on ``javafx.scene.robot.Robot`` alone, finding nodes
  with ``Scene.lookup`` and computing a pointer target from the node's own
  screen bounds. **There is no TestFX on this class's code path.**

``SectionNavigationUiTest`` and ``KeyboardOnlyNavigationUiTest`` are
``@ParameterizedTest``\ s over ``Stream.of(new TestFxUiDriver(application), new
RobotFxUiDriver(application))``, so the same walk runs through both. That is
what makes the abstraction worth having rather than a layer of indirection: a
fallback that had never been run would not be a fallback, and the day TestFX
breaks against a new JavaFX the alternative is already proved rather than
proposed.

No TestFX type appears in ``FxUiDriver``. The interface offers no method taking
a pixel coordinate and none taking a CSS selector beyond an identifier, because
``R-TEST-04`` forbids exactly those; a test says
``clickOn(UiIds.navigationEntry(SectionId.RESULTS))``. Nor is there any method
that reaches into a view-model or fires an ``ActionEvent``: input is synthetic,
the way a user's is, which is what makes "reachable by mouse and by keyboard
alone" mean anything. *Reading* is deliberately different -- assertions read
scene-graph state directly, because a test that could only read through the
robot it typed with would be asserting its own echo.

``RunningApplication.launchedByMain()`` starts the real product through
``CometGuiLauncher.main`` on a background thread and then waits on two
observable facts -- whether the toolkit accepts work, and whether a showing
stage exists -- never on a sleep. Nothing is hand-built: no test constructs a
``ShellView``, and the composition root is the real one.
``RunningApplication.showing(stage)`` wraps a stage a test started itself, which
is what the console flood test needs in order to supply the log it floods.
``Application.launch`` may be called at most once per JVM, so
``reuseForks=false`` in ``cometgui-app/pom.xml`` gives each GUI test class its
own JVM and its own application.

The four tests that carry the exit gate
----------------------------------------

``org.cometgui.app.gui``, one class per gate item, each driving the launched
application:

* ``SectionNavigationUiTest`` -- **items 1 (by mouse) and 2**: a synthetic click
  on each of the ten navigation entries, then that section's pane asserted
  showing *by its stable identifier* while the other nine are not. Both drivers.
* ``KeyboardOnlyNavigationUiTest`` -- **item 1 (by keyboard alone)**: no
  ``clickOn`` is called anywhere in the class. Tab into the navigation, then
  every section reached with arrow keys, reading back the focus owner's
  identifier, which entries are traversable and which pane is showing. Both
  drivers. Method order is declared, because the first test is about a *fresh*
  application.
* ``AccessibleNameEnumerationUiTest`` -- **item 4**: walks the whole scene graph
  after ``applyCss()`` and ``layout()`` and requires a non-blank accessible name
  on every ``Control`` found. Not a list of identifiers to check, which would
  pass on the one day it matters. It also asserts a *floor* on how many controls
  were seen -- derived at 65, with this build's walk finding **91** -- so a walk
  that found three and named all three cannot pass.
* ``ConsoleFloodUiTest`` -- **item 5**: the console *pane* inside the running
  application, flooded from a producer thread through the same path the process
  service will use. It asserts the retained model equals the log's capacity, the
  document is within ``DEFAULT_MAX_RENDERED_LINES``, the messages that are gone
  are the *oldest* (by a sequence number carried in each line, not by counting),
  the summary line states the discard in text, and the retained heap growth is
  inside a documented bound. No sleep, no timeout, no retry: the producer thread
  is joined and ``refreshNow()`` is a barrier.

Measured at the unit 8 sign-off on 2026-08-30:
``Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`` across the four classes,
and ``bash scripts/build.sh`` reporting ``11/11 stages OK in 173 seconds. BUILD
OK`` with ``46 report file(s): tests=693 failures=0 errors=0 skipped=0``.

TestFX: EUPL 1.1, and the AssertJ it drags in
----------------------------------------------

TestFX 4.0.18 is pinned in the parent POM (``testfx.version``) and declared, at
**test** scope, in ``cometgui-app`` and nowhere else. The scope is repeated at
the declaration site as well as being managed, deliberately: a managed scope is
easy to lose sight of, and a robot library at compile scope would be shipped
inside the application.

Two facts a reader needs.

**``testfx-core`` and ``testfx-junit5`` declare EUPL 1.1** in their POMs. That
is not GPL-3.0, and it is recorded here and in the phase handoff so the licence
audit is told rather than left to discover it. Nothing about it is redistributed
with CometGUI -- test scope, one module, no release artefact.

**Its transitive ``org.assertj:assertj-core`` 3.13.2 carries
GHSA-rqfh-9r24-8c9r / CVE-2026-24400** (XML external entity processing in
``isXmlEqualTo``), and ``scripts/ci/dependency-scan.py`` genuinely failed the
supply-chain stage on it -- correctly. It is **excluded** on both TestFX
coordinates, which *removes the component from the graph* rather than accepting
it. That distinction is the point: an allowlist entry would accept a known
advisory for no benefit, and ``scripts/ci/security/allowlist.json`` is still
``"entries": []``, which is its correct state. The exclusion is safe because
AssertJ is used by exactly one part of TestFX -- ``javap`` over the 4.0.18 jar
shows references to ``org/assertj`` only from ``org/testfx/assertions/**``, and
nothing here imports that package; if that ever stops being true the tests fail
with ``NoClassDefFoundError``, loudly, which is the right failure.
``org.hamcrest:hamcrest`` and ``org.osgi:org.osgi.core`` arrive the same way and
are **not** excluded: neither carries an advisory, and both are part of the
library proper.

What Phase 02 deliberately did not build
========================================

Stated plainly, so a later phase does not assume it exists.

.. list-table::
   :header-rows: 1
   :widths: 40 16 44

   * - Not built
     - Owning phase
     - Note

   * - Any scientific behaviour at all
     - 03--13
     - Seven modules hold only ``package-info.java``. The shell is the frame;
       each empty section pane says in text which phase fills it, so an empty
       section can be told from a broken one without reading the source.

   * - The process service, and any ``ProcessBuilder``
     - 03
     - ``ProcessRunner`` is an interface with no implementation. The
       host-baseline probe uses ``java.lang.foreign`` precisely because a
       subprocess was not available to this phase.

   * - Hashing, and any real ``FileHashes``
     - 04
     - ``HashService`` is an interface. No fake was supplied, on purpose.

   * - Downloading, the tool registry and the runtime capability probe
     - 05
     - ``Downloader`` is an interface. The per-tool glibc floor
       (``R-PLAT-02``) comes from executing the installed binary, which is
       Phase 05's; ``STARTUP_GLIBC_FLOOR`` is not a substitute for it.

   * - The workflow engine
     - 08
     - ``org.cometgui.workflow.state`` models the **stepper's** eight stages,
       which is *not* the specification's seventeen-step canonical workflow
       DAG. The two must not be confused, and neither is derived from the other
       here; Phase 08 declares the mapping.

   * - File I/O beyond application settings, and settings persistence itself
     - later
     - ``FileSystemAccess`` answers only the questions the shell and the
       baseline check ask. ``ApplicationServices.forThisHost()`` does no I/O and
       creates no directory.

   * - Any content in the ten section panes
     - 03, 05, 07--13
     - ``SectionArrivals`` names the phase per section, read from
       ``phases/index.rst``.

   * - Blocking a run on a blocking host outcome
     - the phase that owns running a workflow
     - Startup reports and continues; it does not exit the JVM.

   * - Windows and macOS, anywhere
     - 14, 15
     - No non-Linux machine has run any of this. The Monocle and font-stack
       recipe, the FFM probe's non-glibc branches, and packaging are all
       unverified off Linux/amd64.

Two loose ends this phase surfaced and did **not** decide, recorded here so they
are not rediscovered:

**The Settings section has no owning phase.** Neither ``phases/index.rst`` nor
``specification.rst`` claims it. ``SectionId.SETTINGS`` exists, is reachable and
carries an accessible name and a stable identifier, and its pane says so in
text. It was escalated rather than guessed at.

**Phase 01's two ``JavaFxAvailability`` scaffolding classes are gone.** They
existed to prove that ``cometgui-ui`` and ``cometgui-app`` compile against the
JavaFX modules bundled in the pinned JDK, and their own Javadoc said Phase 02
would replace them. It did: ``org.cometgui.ui.view.ShellView`` and
``org.cometgui.app.bootstrap.CometGuiApplication`` are the real proof, and they
run rather than merely compile. Deleting the scaffolding meant editing
``ClassImportCensusTest.namedProductClassesArePresent``, which asserted both by
name; it now names those two real classes instead, which makes the census a
stronger statement than it was -- an import that reaches the product's shell and
its ``Application`` subclass cannot be an import of stubs.
