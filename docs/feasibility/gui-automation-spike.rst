==========================================================
GUI automation spike: JavaFX startup, TestFX and fallback
==========================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 7 -- JavaFX startup smoke test and GUI automation spike
:Exit gate item: 5 -- "The GUI automation spike has a written verdict: TestFX
   works, or the named fallback does"
:Date: 2026-08-29
:Host: Debian GNU/Linux 12 (bookworm), glibc 2.36, x86-64, no X display
:Pinned pair: BellSoft Liberica JDK 25.0.4.1+1 (LTS) / OpenJFX 25.0.4+1

.. contents:: Contents
   :depth: 2
   :local:

Verdict
=======

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Question
     - Answer established on 2026-08-29
   * - Does a real JavaFX ``Application`` start on the pinned pair, headless?
     - **Yes**, but not out of the box. It needs Monocle, which this JDK does
       not contain, plus a font stack, which this host does not have. Both are
       supplied project-locally. Fourteen assertions inside
       ``Application.start()`` pass, including a rendered pixel.
   * - Is Monocle present in this JDK?
     - **No.** ``javafx.graphics`` in Liberica 25.0.4.1+1 contains exactly two
       Glass platform packages, ``com.sun.glass.ui.gtk`` and
       ``com.sun.glass.ui.delegate``. There is no
       ``com.sun.glass.ui.monocle`` and no ``libglass_monocle.so``.
   * - Was a headed run possible?
     - **Yes.** A project-local Xvfb plus the GTK3 dependency closure was
       extracted from the Debian 12 archive, and JavaFX ran on its own
       ``GtkApplication`` Glass platform against it. This needed one
       ``LD_PRELOAD`` shim; see `Headed: how it was actually done`_.
   * - Does TestFX work on JavaFX 25?
     - **Yes.** TestFX 4.0.18 with ``org.testfx:openjfx-monocle:21.0.2``
       drives the pinned pair, headless and headed. It needs eight
       ``--add-exports`` / ``--add-opens`` options that are not obvious, and
       one forked JVM per test class. See `TestFX verdict`_.
   * - Is there a working fallback?
     - **Yes**, and it is proved rather than proposed:
       ``ToolkitFallbackTest`` -- plain JUnit 5, ``Platform.startup()``,
       ``Platform.runLater`` + ``FutureTask``, and in-process
       ``javafx.scene.robot.Robot`` for synthetic input. No TestFX on its
       code path at all.
   * - Do the tests genuinely fail when the assertion is wrong?
     - **Yes**, demonstrated four times (two smoke, two TestFX), and the
       negative controls are re-run by the scripts on every run.

**Recommendation for Phase 14: build on TestFX 4.0.18, keep the fallback
harness alongside it.** The detail is in `What Phase 14 should build on`_.

What was actually run
=====================

Two re-runnable scripts produce every result quoted here.

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - Script
     - What it proves
   * - ``scripts/feasibility/javafx-smoke.sh``
     - Bootstraps Monocle and the font stack into ``tools/`` with pinned
       SHA-256 verification, asserts the JDK ships no Monocle, compiles the
       spike, runs the headless startup proof, runs a negative control that
       must fail, runs the TestFX and fallback suites through Maven, and runs
       a second negative control with the TestFX assertion deliberately
       broken. Seven stages, all of which must pass.
   * - ``scripts/feasibility/javafx-headed-xvfb.sh``
     - Fetches and verifies the 139-package Xvfb + GTK3 closure, builds the
       ``xkbcomp`` path shim, starts a project-local X server, and reruns the
       startup proof and both test suites headed. Six stages.

Sources, all labelled as throwaway spikes in their header comments:

* ``scripts/feasibility/gui-spike/src/main/java/cometgui/spike/SmokeApp.java``
  -- the tiny scene graph under test: ``TextField#input``, ``Button#go``,
  ``Label#output``; pressing the button sets the label to the field's text
  upper-cased with a ``=`` prefix.
* ``scripts/feasibility/gui-spike/src/main/java/cometgui/spike/HeadlessSmoke.java``
  -- a real ``Application`` whose ``start()`` makes fourteen assertions.
* ``scripts/feasibility/gui-spike/src/test/java/cometgui/spike/TestFxSpikeTest.java``
  -- the TestFX spike.
* ``scripts/feasibility/gui-spike/src/test/java/cometgui/spike/ToolkitFallbackTest.java``
  -- the fallback spike.
* ``scripts/feasibility/gui-spike/pom.xml`` -- the throwaway build. It is
  under ``scripts/feasibility/`` deliberately: Phase 01 owns the real build,
  and nothing here belongs at the repository root.
* ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``,
  ``scripts/feasibility/gui-spike/deb-closure.py``,
  ``scripts/feasibility/gui-spike/headed-x11-closure.tsv`` -- the headed
  half's plumbing and its provenance manifest.

Nothing was installed on the host: no ``apt``, no ``sudo``, no host-level
``pip``, no write outside ``/workspace``, nothing in ``~/.m2``. Everything
fetched is under ``tools/`` and everything built is under ``_build/``, both
gitignored -- which is why the provenance tables below are in this committed
document.

The display situation
=====================

Recorded by stage 0 of ``javafx-smoke.sh`` on 2026-08-29::

    DISPLAY         = []
    /tmp/.X11-unix  = absent
    Xvfb on PATH    = absent
    xvfb-run        = absent
    libX11.so.6     =

There is no X display, no X server, and no X client library. The default
Glass platform therefore cannot even load its native library::

    $ java Probe.java        # no glass.platform override
    Exception in thread "main" java.lang.RuntimeException:
      java.lang.UnsatisfiedLinkError:
      /workspace/tools/liberica-jdk-25.0.4.1+1/lib/libglass.so:
      libX11.so.6: cannot open shared object file: No such file or directory
        at javafx.graphics/com.sun.javafx.tk.quantum.QuantumToolkit.startup(QuantumToolkit.java:294)

Headless is not a preference on this host; it is the only way in without
fetching an X stack.

Finding 1: the pinned JDK contains no Monocle
=============================================

Work unit 5 left this open ("Whether the Liberica build ships a usable Monocle
was **not** tested by this unit"). It does not.

Listing the runtime image directly::

    $ jimage list "$JAVA_HOME/lib/modules" | grep -oE 'com/sun/glass/ui/[a-z0-9]+/' | sort -u
    com/sun/glass/ui/delegate/
    com/sun/glass/ui/gtk/

    $ jimage list "$JAVA_HOME/lib/modules" | grep -ci monocle
    0

    $ ls "$JAVA_HOME"/lib | grep -iE 'glass|monocle'
    libglass.so
    libglassgtk3.so

So ``javafx.graphics`` has a GTK platform and nothing else -- no Monocle
classes and no ``libglass_monocle.so``. Asking for Monocle anyway fails
exactly as you would expect::

    $ java -Dglass.platform=Monocle -Dmonocle.platform=Headless ... Probe
    java.lang.ClassNotFoundException: com.sun.glass.ui.monocle.MonoclePlatformFactory
        at javafx.graphics/com.sun.glass.ui.PlatformFactory.getPlatformFactory(PlatformFactory.java:42)
        at javafx.graphics/com.sun.glass.ui.Application.run(Application.java:143)
        at javafx.graphics/com.sun.javafx.tk.quantum.QuantumToolkit.startup(QuantumToolkit.java:284)

``javafx-smoke.sh`` stage 1 re-derives this on every run and **fails loudly if
a future JDK does contain Monocle**, so the finding cannot go quietly stale.

Monocle therefore comes from ``org.testfx:openjfx-monocle`` on Maven Central.
The newest release is **21.0.2** (2024-02-16); despite being built from
JavaFX 21 sources it works unmodified against JavaFX 25.0.4+1.

It must be injected with ``--patch-module``, not put on the class path.
``javafx.graphics`` is a named system module and its Glass platform lookup is
``Class.forName("com.sun.glass.ui." + platform.toLowerCase() + "." + platform
+ "PlatformFactory")`` executed from inside that module, so a class-path copy
in the unnamed module is invisible to it. The working option is::

    --patch-module javafx.graphics=/path/to/openjfx-monocle-21.0.2.jar

The jar contains 199 entries, all ``.class`` files under
``com/sun/glass/ui/monocle/`` -- no ``module-info``, no native library. The
headless platform (``HeadlessPlatform``, ``HeadlessScreen``) is pure Java,
which is why it works with no ``libglass_monocle.so`` anywhere on this host.

.. note::

   ``org.testfx:openjfx-monocle:21.0.2`` is published with
   ``<packaging>pom</packaging>`` even though a jar exists at the coordinate.
   The spike fetches it with ``maven-dependency-plugin``'s ``copy`` goal and an
   explicit ``<type>jar</type>``; a plain ``<dependency>`` would also keep it
   on the test class path, which is exactly where it must not be.

Finding 2: JavaFX needs a font stack this host does not have
============================================================

With Monocle patched in, the toolkit started -- and then the first ``Control``
in the scene killed it::

    java.lang.NullPointerException: Cannot invoke
      "com.sun.javafx.font.FontFactory.isPlatformFont(String)" because "fontFactory" is null
        at javafx.graphics/com.sun.javafx.font.PrismFontLoader.loadFont(PrismFontLoader.java:210)
        at javafx.graphics/javafx.scene.text.Font.getDefault(Font.java:109)
        at javafx.graphics/javafx.scene.CssStyleHelper.<clinit>(CssStyleHelper.java:1671)
        at javafx.graphics/javafx.scene.Node.reapplyCss(Node.java:9967)
        ...
        at javafx.graphics/javafx.scene.Scene.<init>(Scene.java:245)

This is not avoidable by writing a simpler UI: putting *any* node into a
``Scene`` initialises ``CssStyleHelper``, which calls ``Font.getDefault()``.
On Linux JavaFX resolves fonts through FreeType, and lays out glyphs through
Pango. This host has neither, nor any font files at all::

    $ ldd $JAVA_HOME/lib/libjavafx_font_freetype.so | grep 'not found'
        libfreetype.so.6 => not found
    $ ldd $JAVA_HOME/lib/libjavafx_font_pango.so | grep 'not found'
        libpangoft2-1.0.so.0 => not found
        libpango-1.0.so.0 => not found
        libfontconfig.so.1 => not found
        libgobject-2.0.so.0 => not found
        libglib-2.0.so.0 => not found
        libfreetype.so.6 => not found
    $ ls /usr/share/fonts
    ls: cannot access '/usr/share/fonts': No such file or directory

Fixed with fourteen Debian 12 packages extracted project-locally by work unit
3's ``extract_deb.py`` -- no ``dpkg``, no maintainer scripts, no root -- and
three environment variables::

    LD_LIBRARY_PATH=tools/fontstack-bookworm-20260829/root/usr/lib/x86_64-linux-gnu
    FONTCONFIG_PATH=tools/fontstack-bookworm-20260829/root/etc/fonts
    XDG_DATA_HOME=tools/fontstack-bookworm-20260829/root/usr/share

``XDG_DATA_HOME`` is the load-bearing one: Debian's ``fonts.conf`` includes
``<dir prefix="xdg">fonts</dir>``, so pointing it at the extracted
``usr/share`` is what makes the DejaVu fonts visible without touching
``/usr/share/fonts``. ``XDG_CACHE_HOME`` is redirected into ``_build/`` so
fontconfig's cache does not land in the user's home directory.

**This matters beyond the spike.** A CI runner or a jpackage-produced bundle
running on a minimal Linux image will hit the same ``fontFactory is null`` the
moment it builds a scene. See `What a CI runner needs`_.

The headless startup proof
==========================

``HeadlessSmoke`` is a real ``javafx.application.Application`` launched through
``Application.launch()``, so the whole toolkit-startup path runs. Every value
below is read *from inside* ``Application.start()`` and compared against a
pinned expectation; the process exits 1 if any comparison fails. Verbatim
output of ``javafx-smoke.sh`` stage 4::

    == JavaFX headless startup evidence ==
      [PASS] fxThreadName           actual=JavaFX Application Thread expected=JavaFX Application Thread
      [PASS] isFxApplicationThread  Platform.isFxApplicationThread()=true
      [PASS] toolkitClass           actual=com.sun.javafx.tk.quantum.QuantumToolkit expected=com.sun.javafx.tk.quantum.QuantumToolkit
      [PASS] javafx.runtime.version actual=25.0.4+1 expected=25.0.4+1
      [PASS] java.version           actual=25.0.4.1 expected=25.0.4.1
      [PASS] java.vendor            actual=BellSoft expected=BellSoft
      [PASS] glassApplication       actual=com.sun.glass.ui.monocle.MonocleApplication expected=com.sun.glass.ui.monocle.MonocleApplication
      [PASS] primaryScreen          javafx.stage.Screen primary bounds = 1280x800
      [PASS] buttonLaidOut          Button#go width = 70.0
      [PASS] fontMetrics            width of "MMMM" at 100pt = 345.1171875 (expected 150 < w < 900)
      [PASS] snapshotPixel(2,2)     actual=ff204080 expected=ff204080
      [PASS] snapshotSize           snapshot = 320x200
      [PASS] eventHandlerResult     actual==COMET expected==COMET
      [PASS] pressCount             actual=1 expected=1

    HEADLESS SMOKE: PASS -- all checks satisfied

Why these fourteen and not "it did not throw":

* ``fxThreadName`` and ``isFxApplicationThread`` -- the FX Application Thread
  really exists and ``start()`` is on it.
* ``toolkitClass`` -- ``com.sun.javafx.tk.Toolkit.getToolkit()`` is the real
  ``QuantumToolkit``, not a stub.
* ``javafx.runtime.version`` -- read as a system property *at runtime*; it is
  set by the toolkit, so ``25.0.4+1`` proves the pinned OpenJFX is the one that
  initialised. ``java.vendor.version`` is ``null`` on this build (work unit 5's
  finding), so identification uses ``java.version`` and ``java.vendor``.
* ``glassApplication`` -- ``com.sun.glass.ui.Application.GetApplication()`` is
  Monocle's, which is the whole point of the patch-module.
* ``primaryScreen``, ``buttonLaidOut`` -- a ``Stage`` was shown and a CSS and
  layout pass ran; the ``Button`` has a real width.
* ``fontMetrics`` -- ``"MMMM"`` at 100 pt measures 345.1171875 px. A null font
  factory throws long before this; a stub one measures zero.
* ``snapshotPixel(2,2)`` -- ``Scene.snapshot()`` rasterised the scene through
  the Prism software pipeline and pixel (2,2) is exactly ``0xff204080``, the
  colour set on the root by ``-fx-background-color: #204080``. **This is the
  strongest single check: nothing short of a working renderer produces it.**
* ``eventHandlerResult`` and ``pressCount`` -- a real ``ActionEvent`` reached a
  real handler and changed scene-graph state.

Negative control (``javafx-smoke.sh`` stage 5): the same binary, run with one
expectation deliberately falsified::

    $ java ... -Dsmoke.expect.fxVersion=0.0.0-deliberately-wrong ... HeadlessSmoke
      [FAIL] javafx.runtime.version actual=25.0.4+1 expected=0.0.0-deliberately-wrong
    HEADLESS SMOKE: FAIL -- 1 check(s) failed:
        javafx.runtime.version: actual=25.0.4+1 expected=0.0.0-deliberately-wrong
    exit status 1

The ``-Dsmoke.expect.*`` overrides exist only for that demonstration and for
the one legitimately platform-dependent value (the Glass class differs headed).
Every default is the pinned value; overriding one to make a real failure pass
would be weakening a gate.

TestFX verdict
==============

**TestFX 4.0.18 works against JDK 25.0.4.1+1 / JavaFX 25.0.4+1**, headless via
Monocle and headed via GTK. This was the project's named risk and it did not
materialise -- but it needs configuration that is easy to get wrong, so the
detail below is the useful part of this document.

Artefacts used
--------------

.. list-table::
   :header-rows: 1
   :widths: 34 12 16 38

   * - Artefact
     - Version
     - Size (bytes)
     - Note
   * - ``org.testfx:testfx-core``
     - 4.0.18
     - 213,327
     - Latest release; ``maven-metadata.xml`` ``lastUpdated`` 2024-02-11.
       SHA-256 ``26fe893a12f206fe66b59e08d2804ee4ab2de7e4df43d543e7889b5f112f7f95``
   * - ``org.testfx:testfx-junit5``
     - 4.0.18
     - 17,912
     - SHA-256 ``6965916303b7b411b0ec47684cfcb212b11ce0c36e6dc7c6cf04855de7fe1511``
   * - ``org.testfx:openjfx-monocle``
     - 21.0.2
     - 257,923
     - Latest release; ``lastUpdated`` 2024-02-16. SHA-256
       ``3d0b0c186a9f495aa4e3d058c612b2a9cf44a97ffbcecd75d441aed8263fac50``
   * - ``org.junit.jupiter:junit-jupiter``
     - 5.14.4
     - 6,365 (aggregator)
     - Latest release on Maven Central on 2026-08-29
   * - ``org.hamcrest:hamcrest``
     - 2.1
     - 123,103
     - Pulled in by ``testfx-core`` at runtime scope
   * - ``org.assertj:assertj-core``
     - 3.13.2
     - 4,478,210
     - Pulled in by ``testfx-core`` at runtime scope
   * - ``org.osgi:org.osgi.core``
     - 6.0.0
     - 475,256
     - Pulled in by ``testfx-core`` at runtime scope

Everything resolved from ``https://repo1.maven.org`` into
``_build/m2repo`` (forced with ``-Dmaven.repo.local``); nothing was written to
``~/.m2``.

Licences, for the record and **not** as an answer to any ``D-`` item:
``testfx-core`` and ``testfx-junit5`` declare **EUPL 1.1** in their POMs;
``openjfx-monocle`` declares **GPL v2 with the Classpath Exception**. Both are
test-time only and are not redistributed with CometGUI, but EUPL 1.1 on a test
dependency is worth the licence audit's attention rather than being assumed
harmless.

The JVM options TestFX needs on JDK 25
--------------------------------------

Nine options, in the surefire ``argLine``. Six of them exist purely because
TestFX 4.0.18 reaches into JavaFX internals reflectively, and each was found by
watching it fail::

    --patch-module javafx.graphics=<openjfx-monocle-21.0.2.jar>
    --add-exports  javafx.graphics/com.sun.glass.ui=ALL-UNNAMED
    --add-opens    javafx.graphics/com.sun.glass.ui=ALL-UNNAMED
    --add-exports  javafx.graphics/com.sun.glass.ui.monocle=ALL-UNNAMED
    --add-opens    javafx.graphics/com.sun.glass.ui.monocle=ALL-UNNAMED
    --add-exports  javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
    --add-opens    javafx.graphics/com.sun.javafx.application=ALL-UNNAMED
    --add-exports  javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED
    --enable-native-access=javafx.graphics

Without the ``com.sun.glass.ui.monocle`` pair::

    java.lang.IllegalAccessException: class org.testfx.toolkit.impl.ApplicationLauncherImpl
      cannot access class com.sun.glass.ui.monocle.MonoclePlatformFactory (in module
      javafx.graphics) because module javafx.graphics does not export
      com.sun.glass.ui.monocle to unnamed module @36fc695d
        at org.testfx.toolkit.impl.ApplicationLauncherImpl.assignMonoclePlatform(ApplicationLauncherImpl.java:55)
        at org.testfx.toolkit.impl.ApplicationLauncherImpl.initMonocleHeadless(ApplicationLauncherImpl.java:39)

Without the ``com.sun.javafx.application`` pair::

    java.lang.reflect.InaccessibleObjectException: Unable to make field private static
      java.util.Map com.sun.javafx.application.ParametersImpl.params accessible: module
      javafx.graphics does not "opens com.sun.javafx.application" to unnamed module
        at org.testfx.toolkit.impl.ToolkitServiceImpl.cleanupParameters(ToolkitServiceImpl.java:177)

Note that ``--add-exports`` against a system module is rejected by ``javac``
when ``--release`` is in force, so the spike POM uses
``maven.compiler.source``/``target`` rather than ``maven.compiler.release``.

One forked JVM per test class is mandatory
------------------------------------------

With surefire's default (one reused fork for all test classes), the TestFX
interaction test **fails** when ``ToolkitFallbackTest`` has run first in the
same JVM::

    [INFO] Tests run: 2, Failures: 0, Errors: 0 -- in cometgui.spike.ToolkitFallbackTest
    [ERROR] Tests run: 2, Failures: 1, Errors: 0 -- in cometgui.spike.TestFxSpikeTest
    TestFxSpikeTest.typingAndClickingProducesTheUpperCasedValue:53
      the button handler did not produce the expected label value
      ==> expected: <=COMET> but was: <->

The click stops reaching the ``Button``; the label keeps its initial ``-``.
Run alone, the same test passes three times out of three. The cause is that
``ToolkitFallbackTest`` calls ``Platform.startup()``, sets
``setImplicitExit(false)`` and owns its own ``Stage``s, while TestFX wants to
own the primary stage and the Monocle window stack. The fix is
``<reuseForks>false</reuseForks>``, recorded in the spike POM with that
explanation. **This is a real trap for Phase 14**: it looks like a flaky
TestFX click, and the tempting "fix" is to weaken the assertion.

The TestFX test and its output
------------------------------

The test types into the field, clicks the button, and asserts the exact value
the handler produces -- not that nothing threw::

    clickOn("#input");
    write("comet");
    assertEquals("comet", lookup("#input").queryTextInputControl().getText(), ...);
    clickOn("#go");
    assertEquals("=COMET", lookup("#output").queryAs(Label.class).getText(), ...);
    assertEquals(1, app.getPressCount(), ...);

Passing run, headless (``javafx-smoke.sh`` stage 6)::

    [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in cometgui.spike.ToolkitFallbackTest
    [INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in cometgui.spike.TestFxSpikeTest
    [INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
    [INFO] BUILD SUCCESS

Deliberate-failure run (``javafx-smoke.sh`` stage 7). The script copies the
spike to ``_build/gui-spike/negative``, changes the one expected string, and
requires the build to fail with that exact message::

    53:  assertEquals("=NOT-WHAT-THE-HANDLER-PRODUCES", output.getText(),
    [ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0 -- in cometgui.spike.TestFxSpikeTest
    org.opentest4j.AssertionFailedError: the button handler did not produce the expected
      label value ==> expected: <=NOT-WHAT-THE-HANDLER-PRODUCES> but was: <=COMET>
    [INFO] BUILD FAILURE

The assertion bites, and it bites for the right reason: the robot's click did
run the handler, which produced ``=COMET``.

The fallback, proved not proposed
=================================

Even though TestFX works, the fallback was spiked, because a verdict that rests
on one library that has historically lagged JavaFX is a thin verdict.

**The named fallback is:** plain JUnit 5 plus the JavaFX toolkit driven
directly -- ``Platform.startup()`` once per JVM, work marshalled onto the FX
Application Thread with ``Platform.runLater`` and a ``FutureTask``, assertions
on real scene-graph state, and ``javafx.scene.robot.Robot`` (in-process, JavaFX
11+) for synthetic input. It is
``scripts/feasibility/gui-spike/src/test/java/cometgui/spike/ToolkitFallbackTest.java``
and it has no TestFX on its code path::

    $ javap -p -c -classpath .../test-classes cometgui.spike.ToolkitFallbackTest | grep -c testfx
    0

It proves two levels:

``firingTheHandlerProducesTheUpperCasedValue``
    Controller level, no synthetic input: build the scene on the FX thread,
    show a ``Stage``, apply CSS and lay out, set the field's text, fire the
    ``Button``'s ``ActionEvent``, and assert the label reads ``=PERCOLATOR``
    and the press count is 1.

``theRobotClicksAndTypesForReal``
    Synthetic input: construct a ``javafx.scene.robot.Robot``, move the pointer
    to ``TextField#input``'s real screen coordinates via ``localToScreen``,
    click, type ``c o m e t`` with ``Robot.keyType``, assert the field reads
    ``comet``; then move to ``Button#go``, click, and assert the label reads
    ``=COMET``.

Both pass headless under Monocle and headed under GTK -- the ``Tests run: 2,
Failures: 0`` line for ``ToolkitFallbackTest`` in both Maven runs above. So
``javafx.scene.robot.Robot`` works under Monocle Headless, which is the fact
that makes this a real fallback rather than a controller-only workaround.

Headed: how it was actually done
================================

The unit's stretch goal was a headed run without touching the host. It
succeeded, and the route is worth recording because a CI runner will face the
same problems.

#. **Dependency closure.** ``scripts/feasibility/gui-spike/deb-closure.py``
   reads Debian 12's own ``Packages`` index and computes the closure of
   ``xvfb x11-xkb-utils xkb-data libgtk-3-0 libxtst6``, minus packages already
   in this host image. Result: **139 packages, 86.4 MB**, pinned in
   ``scripts/feasibility/gui-spike/headed-x11-closure.tsv`` with each
   package's version, URL, SHA-256 from the archive index, size and Debian
   ``License:`` field. Re-running ``deb-closure.py`` on 2026-08-29 reproduced
   that manifest byte-for-byte on name, version and SHA-256.
#. **Extraction.** Each ``.deb`` is verified against the manifest's SHA-256 and
   unpacked with work unit 3's ``extract_deb.py`` into
   ``tools/x11-bookworm-20260829/root``. No ``dpkg``, no maintainer scripts, no
   root. Afterwards ``ldd`` reports zero unresolved libraries for both ``Xvfb``
   and ``libgtk-3.so.0``.
#. **The one thing that could not be relocated.** Xvfb compiles its keymap by
   running ``xkbcomp`` at the absolute path baked in at build time,
   ``/usr/bin/xkbcomp``. Without it the server dies::

       sh: 1: /usr/bin/xkbcomp: not found
       XKB: Failed to compile keymap
       Fatal server error: Failed to activate virtual core keyboard: 2

   ``/usr/bin`` is not writable, and writing there would be a host install.
   User namespaces are refused on this host (``unshare: unshare failed:
   Operation not permitted``), so a bind mount is unavailable. The X server
   launches the compiler with ``execl("/bin/sh", "sh", "-c", cmd)``, so a
   twenty-line ``LD_PRELOAD`` shim that rewrites ``/usr/bin/xkbcomp`` inside
   ``cmd`` solves it and touches nothing outside the Xvfb process tree:
   ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``. It is a spike
   workaround for this host, not a product technique -- a CI runner installs
   ``x11-xkb-utils`` and needs none of it.
#. **Result.** ``Xvfb :99 -screen 0 1280x800x24 -nolisten tcp -xkbdir ...``
   comes up; ``DISPLAY=:99`` and the same ``HeadlessSmoke`` binary, with no
   Monocle and no ``glass.platform`` override, gives::

       ES2 Prism: Error - GLX extension is not supported
           GLX version 1.3 or higher is required
       == JavaFX headless startup evidence ==
         [PASS] fxThreadName           actual=JavaFX Application Thread ...
         [PASS] toolkitClass           actual=com.sun.javafx.tk.quantum.QuantumToolkit ...
         [PASS] javafx.runtime.version actual=25.0.4+1 ...
         [PASS] glassApplication       actual=com.sun.glass.ui.gtk.GtkApplication ...
         [PASS] primaryScreen          javafx.stage.Screen primary bounds = 1280x800
         [PASS] fontMetrics            width of "MMMM" at 100pt = 345.1171875 ...
         [PASS] snapshotPixel(2,2)     actual=ff204080 expected=ff204080
         [PASS] eventHandlerResult     actual==COMET expected==COMET
       HEADLESS SMOKE: PASS -- all checks satisfied

   All fourteen checks pass on the **GTK** Glass platform. The GLX message is
   expected and harmless: Xvfb offers no GLX 1.3, so Prism falls back from the
   ``es2`` hardware pipeline to the software one, exactly as it does headless.
#. **TestFX headed.** ``mvn -Pheaded test`` (no Monocle, ``testfx.headless``
   false) gives ``Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`` and
   ``BUILD SUCCESS``. So both suites pass on both platforms.
#. **Negative control, headed.** The same harness with
   ``-Dsmoke.expect.pixel=ffdeadbe`` exits 1 with ``[FAIL] snapshotPixel(2,2)
   actual=ff204080 expected=ffdeadbe``.

What a CI runner needs
======================

Distilled from the above, per platform, for whoever writes the pipeline.

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - Platform
     - Requirement
   * - Linux, headless
     - The pinned JDK; ``org.testfx:openjfx-monocle`` on the module patch
       path; and a real font stack -- ``libfreetype6``, ``libfontconfig1``,
       ``fontconfig-config``, ``libpangoft2-1.0-0`` (with ``libpango``,
       ``libglib2.0``, ``libharfbuzz``, ``libfribidi``, ``libthai``,
       ``libdatrie``, ``libgraphite2``) and at least one font package such as
       ``fonts-dejavu-core``. Without fonts, every scene-building test dies on
       ``fontFactory is null``. No X server, no ``DISPLAY``, no Xvfb needed.
   * - Linux, headed
     - As above plus ``xvfb``, ``x11-xkb-utils``, ``xkb-data`` and the GTK3
       runtime, and ``DISPLAY`` pointing at the Xvfb instance. On a runner
       where these are installed normally, ``xvfb-run`` replaces the whole
       shim story. Expect Prism to use the software pipeline: Xvfb has no
       GLX 1.3.
   * - Windows / macOS
     - **Unverified by this unit** -- no such runner exists here. Monocle
       Headless is platform-neutral Java and both platforms have system fonts,
       so the headless path is expected to work with only the
       ``--patch-module`` and ``--add-exports`` options, but expected is not
       verified. See `Unverified`_.

What Phase 14 should build on
=============================

**Build on:**

* **TestFX 4.0.18 + ``org.testfx:openjfx-monocle:21.0.2``**, with the nine JVM
  options listed above. It works on the pinned pair today, headless and headed,
  and it gives you ``clickOn``/``write``/``lookup`` for free.
* **``<reuseForks>false</reuseForks>``** in surefire, or an equivalent
  isolation. Non-negotiable; see the trap above.
* **The fallback harness as a first-class citizen**, not a contingency. TestFX
  4.0.18 dates from February 2024 and its Monocle shim from the same month;
  the next JavaFX that breaks it will break it in CI, on a Friday. A test suite
  that already has a working ``Platform.runLater`` + ``javafx.scene.robot.Robot``
  harness can move the affected tests across in an afternoon.
* **Assertions on computed values.** The pattern that made this spike
  falsifiable -- assert the label's exact text, the button's width, the
  snapshot's pixel -- is the pattern for Phase 14. ``Scene.snapshot()`` plus a
  pixel assertion is a cheap, honest way to prove something actually rendered.
* **Fonts as an explicit dependency of the test environment**, documented in
  the CI setup, not discovered later.

**Avoid:**

* Adding an ``org.openjfx:javafx-*`` Maven dependency. JavaFX is in the pinned
  JDK as system modules; a second copy duplicates the packages (work unit 5's
  warning, confirmed here from the other direction).
* Putting the Monocle jar on the class path. It must be patched into
  ``javafx.graphics`` or the platform lookup cannot see it.
* ``maven.compiler.release`` in any module that needs ``--add-exports`` against
  a JDK system module.
* Trusting a green TestFX run that shares a JVM with a hand-driven toolkit
  test.
* Assuming ``java.vendor.version`` identifies the runtime -- it is ``null``
  here.
* The ``LD_PRELOAD`` xkbcomp shim. It is a spike workaround for a host where
  nothing may be installed; a CI runner installs the package.

Provenance of everything fetched
================================

``tools/`` is gitignored, so this table is the only record. Every file was
re-verified by SHA-256 on 2026-08-29 by the scripts that use it, and the
scripts fail rather than continue on a mismatch.

Monocle
-------

.. list-table::
   :header-rows: 1
   :widths: 20 80

   * - Field
     - Value
   * - Artefact
     - ``org.testfx:openjfx-monocle:21.0.2`` (jar)
   * - URL
     - ``https://repo1.maven.org/maven2/org/testfx/openjfx-monocle/21.0.2/openjfx-monocle-21.0.2.jar``
   * - Date fetched
     - 2026-08-29
   * - Size
     - 257,923 bytes
   * - SHA-256
     - ``3d0b0c186a9f495aa4e3d058c612b2a9cf44a97ffbcecd75d441aed8263fac50``
   * - Licence
     - GNU General Public License v2 with the Classpath Exception (declared in
       the artefact's POM; it is OpenJFX code). Test-time only; not
       redistributed with CometGUI.
   * - Installed at
     - ``tools/openjfx-monocle-21.0.2/``

Font stack (Debian 12 "bookworm", ``main``, fetched 2026-08-29)
---------------------------------------------------------------

All from ``https://deb.debian.org/debian/pool/main/<pool>/<file>``. Total
6,471,036 bytes of ``.deb``, expanding to 13,863,368 bytes under
``tools/fontstack-bookworm-20260829/root``. Licence column is the Debian
``copyright`` file's ``License:`` field.

.. list-table::
   :header-rows: 1
   :widths: 34 20 34 12

   * - File
     - Pool
     - SHA-256
     - Licence
   * - ``libfreetype6_2.12.1+dfsg-5+deb12u4_amd64.deb``
     - ``f/freetype``
     - ``8043e479f73f29992d652e3f9dfe8b17f9780c7ea6330afe379ec5f9f188ac44``
     - FTL
   * - ``libpng16-16_1.6.39-2+deb12u5_amd64.deb``
     - ``libp/libpng1.6``
     - ``a56d64bfaa9da12aafb83347909e62e6fd5fd251e6b34c194065911a30359978``
     - libpng
   * - ``libfontconfig1_2.14.1-4_amd64.deb``
     - ``f/fontconfig``
     - ``16ee38d374e064f534116dc442b086ef26f9831f1c0af7e5fb4fe4512e700649``
     - fontconfig (MIT-style)
   * - ``fontconfig-config_2.14.1-4_amd64.deb``
     - ``f/fontconfig``
     - ``281c66e46b95f045a0282a6c7a03b33de0e9a08d016897a759aaf4a04adfddbe``
     - fontconfig (MIT-style)
   * - ``fonts-dejavu-core_2.37-6_all.deb``
     - ``f/fonts-dejavu``
     - ``8892669e51aab4dc56682c8e39d8ddb7d70fad83c369344e1e240bf3ca22bb76``
     - bitstream-vera
   * - ``libglib2.0-0_2.74.6-2+deb12u9_amd64.deb``
     - ``g/glib2.0``
     - ``7ff85197685d89e150e342b29a59aab1beee400050ba7da73de81cd999ffee5a``
     - LGPL-2+ / LGPL-2.1+ and others
   * - ``libharfbuzz0b_6.0.0+dfsg-3_amd64.deb``
     - ``h/harfbuzz``
     - ``bfce132b7ee67b9c2d2166075b1936a25c8cc6866b6a049f99b8e94baa916e71``
     - MIT
   * - ``libgraphite2-3_1.3.14-1+deb12u1_amd64.deb``
     - ``g/graphite2``
     - ``c19a7f6ba9298db7eef041ae27b08985f2c02009e418063f8bccdb5bc5e858dc``
     - LGPL-2.1+ or MPL-1.1 or GPL-2+
   * - ``libpango-1.0-0_1.50.12+ds-1_amd64.deb``
     - ``p/pango1.0``
     - ``851720de07441ae6bb6a7f51fc0f2edb4db7aa6f25b5bf1bf7b72dcab8947b7f``
     - LGPL-2+ and LGPL-2.1+
   * - ``libpangoft2-1.0-0_1.50.12+ds-1_amd64.deb``
     - ``p/pango1.0``
     - ``78da3f494109f6e7a39c4626aaae7571c600c5854cecda0bc0c902224986a63b``
     - LGPL-2+ and LGPL-2.1+
   * - ``libfribidi0_1.0.8-2.1_amd64.deb``
     - ``f/fribidi``
     - ``87fce56627aab7b2968501d370aa3ed6d1c792119efa765e71a690bdfe570e62``
     - LGPL-2.1+
   * - ``libthai0_0.1.29-1_amd64.deb``
     - ``libt/libthai``
     - ``37cd66bef851ea0e4af807797ba3ad14d43226f7c4954c1d0a19478e11815bae``
     - LGPL-2.1+
   * - ``libthai-data_0.1.29-1_all.deb``
     - ``libt/libthai``
     - ``eed65a75269411e47d7b393d82bc30471da5c499e9f311abbfd8c54ca1a42d9e``
     - LGPL-2.1+
   * - ``libdatrie1_0.2.13-2+b1_amd64.deb``
     - ``libd/libdatrie``
     - ``f021f193384929989b2dfd19f606a8cebe54b5f209fe387fc40683e810e01ebe``
     - LGPL-2.1+

Xvfb + GTK3 closure (headed run only)
-------------------------------------

139 packages, 90,572,188 bytes of ``.deb``, expanding to about 331 MB under
``tools/x11-bookworm-20260829/root``. Listing them all here would drown the
document, so the full manifest -- package, version, URL, SHA-256, size and
Debian ``License:`` field for each -- is committed as
``scripts/feasibility/gui-spike/headed-x11-closure.tsv`` and is what
``javafx-headed-xvfb.sh`` verifies against. It is regenerated from the archive
index by::

    python3 scripts/feasibility/gui-spike/deb-closure.py \
        xvfb x11-xkb-utils xkb-data libgtk-3-0 libxtst6

All 139 are from Debian 12 ``main``, so all are DFSG-free; the licence field is
recorded per package in the manifest. 61 of them carry a pre-machine-readable
Debian ``copyright`` file with no ``License:`` field, and the manifest says so
rather than guessing. **None of this is redistributed with CometGUI** -- it
exists only so a headless CI-like host can run a headed test -- but it is not
a licence audit either, and it is not a ``D-`` answer.

How to re-run all of this
=========================

::

    bash scripts/feasibility/javafx-smoke.sh          # headless, ~2 min incl. downloads
    bash scripts/feasibility/javafx-headed-xvfb.sh    # headed, needs the above first

Both are idempotent, verify every download against a pinned SHA-256, and check
output content rather than exit status. Both print a per-stage summary and exit
non-zero if any stage did not produce the expected evidence. The document check
for this file is::

    bash scripts/feasibility/check-docs.sh docs/feasibility/gui-automation-spike.rst

Unverified
==========

Honest list of what this unit did **not** establish. None of these is a passed
item.

#. **Windows and macOS.** No runner exists here. Whether Monocle Headless,
   TestFX 4.0.18 or ``javafx.scene.robot.Robot`` behave the same on those
   platforms is untested. The headless path is *expected* to be simpler there
   (system fonts exist), but that is an expectation.
#. **The ``es2`` hardware Prism pipeline.** Everything above ran on the
   software rasteriser -- forced with ``-Dprism.order=sw`` headless, and fallen
   back to automatically headed because Xvfb offers no GLX 1.3. Nothing here
   says JavaFX works on a real GPU pipeline, and nothing here would catch a
   rendering bug that only appears there.
#. **A real windowing environment.** Xvfb is a real X server but there is no
   window manager, no compositor and no user. Focus, stacking, native file
   dialogs, drag-and-drop, screen DPI scaling and multi-monitor behaviour are
   all untested.
#. **``javafx.web`` and ``javafx.media``.** The spike touched
   ``javafx.controls`` and ``javafx.graphics`` only. WebView in particular
   drags in a large native stack that this host may well not satisfy; if any
   part of CometGUI plans to embed one, that is a separate spike.
#. **Long-running stability.** Each test JVM lived a couple of seconds. Nothing
   here says the Monocle headless toolkit survives a long suite; the
   ``reuseForks`` finding is a hint that toolkit lifecycle in a shared JVM is
   fragile.
#. **TestFX beyond click and type.** ``clickOn``, ``write``, ``lookup``,
   ``moveTo`` and ``point`` were exercised. Drag-and-drop, right-click menus,
   ``TableView`` cell interaction, ``FXMLLoader``-driven scenes and TestFX's
   matchers were not.
#. **Whether TestFX 4.0.18 is really the newest.** It is the newest on Maven
   Central: ``testfx-core`` ``maven-metadata.xml`` lists 4.0.18 as ``latest``
   and ``release`` with ``lastUpdated`` 2024-02-11, and ``openjfx-monocle``
   lists 21.0.2 with ``lastUpdated`` 2024-02-16. No newer release was published
   elsewhere as far as this unit checked, but only Maven Central was checked.
#. **Licence audit.** The licences above are recorded, not audited. EUPL 1.1 on
   TestFX and GPLv2+CE on Monocle are flagged for whoever owns the licence
   audit; this unit does not answer ``D-`` items.
#. **jpackage-bundled behaviour.** Work unit 5 proved a ``jpackage`` app-image
   launches. Whether a bundled image finds fonts on a minimal target machine
   was not tested here, and finding 2 says that is a real risk worth a
   deliberate check before release.
