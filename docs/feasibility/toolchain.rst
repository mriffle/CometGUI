==========================================================
Toolchain, JavaFX and jpackage
==========================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 5 -- project-local toolchain and ``jpackage``
:Exit gate item: 4 -- "``jpackage`` produces a launchable bundle on Linux from
   the pinned toolchain"
:Date: 2026-08-29
:Host: Debian GNU/Linux 12 (bookworm), glibc 2.36, x86-64, 64 cores, 376 GB RAM

.. contents:: Contents
   :depth: 2
   :local:

Summary
=======

A project-local toolchain was installed under ``tools/`` with nothing placed on
the host: no ``apt``, no ``sudo``, no host-level ``pip``, no write outside
``/workspace``.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Question
     - Answer established on 2026-08-29
   * - JDK
     - **BellSoft Liberica JDK 25.0.4.1+1 "Full"** (LTS), installed at
       ``tools/liberica-jdk-25.0.4.1+1``. ``java.version`` = ``25.0.4.1``.
   * - JavaFX
     - **Bundled in that JDK** -- OpenJFX ``25.0.4+1``, present both as
       resolved modules in the runtime image and as ``jmods/javafx.*.jmod``.
       No separate OpenJFX SDK is fetched.
   * - Build tool
     - **Apache Maven 3.9.16**, installed at ``tools/apache-maven-3.9.16``.
       Recommended to Phase 01, which owns the final decision.
   * - ``jpackage`` on Linux
     - ``--type app-image`` **works** and the produced bundle was launched and
       proved to run on its own bundled runtime. ``--type deb`` fails for want
       of ``fakeroot``; ``--type rpm`` is not even offered because
       ``rpmbuild`` is absent. Neither may be installed on this host.
   * - Cross-platform packaging
     - ``jpackage`` is **not** a cross-compiler. Verified locally: the Linux
       JDK's ``jdk.jpackage`` module contains *only* Linux bundler classes.
       Windows and macOS bundles need Windows and macOS runners.

Gate item 4 is met on Linux for the ``app-image`` output type. It is **not**
met for ``deb``/``rpm`` on this host, and that is a host constraint rather than
a toolchain defect -- see `What jpackage needs on Linux, per output type`_.

Choices made by this work unit
==============================

These are engineering choices, recorded here with their reasons. None of them
is a ``D-`` decision; none was answered on the owner's behalf.

Why this JDK
------------

``jpackage`` was added in JDK 14 (incubating in 16, final in 16/17), so any
JDK from 17 upwards would do for packaging alone. Three further constraints
narrowed it to one:

#. ``specification.rst`` ("CasanovoGUI base") states the base targets
   **Java 23+ and JavaFX 25.x**, and that the exact JDK and JavaFX versions
   shall be pinned in the build and recorded in provenance.
#. The project promises tier-1 support for Linux x86-64, Windows x64 and
   macOS arm64, with best-effort macOS x86-64. The same JDK feature release
   must therefore exist, with ``jpackage`` and JavaFX, on all of them.
#. A current LTS is preferred over a feature release so that the pinned
   version keeps receiving updates across the project's life.

JDK 25 is the current LTS (``api.adoptium.net/v3/info/available_releases``
reports ``"most_recent_lts": 25`` on 2026-08-29). Within JDK 25, three vendors
were considered:

.. list-table::
   :header-rows: 1
   :widths: 22 34 44

   * - Candidate
     - JavaFX story
     - Verdict
   * - Eclipse Temurin 25.0.4.1+1
     - None. JavaFX must be fetched separately (Gluon SDK plus a matching
       ``jmods`` archive per platform, or Maven Central artefacts).
     - Rejected: two provenanced artefacts per platform instead of one, and
       the ``jmods`` archive is a separate download that Maven Central does
       not carry.
   * - Azul Zulu FX 25
     - Bundles JavaFX.
     - Viable, not chosen: Azul's builds that bundle JavaFX are a narrower
       matrix and BellSoft publishes a single "full" bundle for every
       platform this project targets, including Linux aarch64.
   * - **BellSoft Liberica JDK 25 "Full"**
     - Bundles OpenJFX 25.0.4+1 as resolved modules *and* as ``jmods``.
     - **Chosen.** One provenanced artefact per platform; ``jlink`` and
       ``jpackage`` can put JavaFX into the shipped runtime with no extra
       module path.

Liberica is a TCK-verified OpenJDK build (``readme.txt`` in the distribution:
"BellSoft Liberica is a build of OpenJDK that is verified to be compliant with
the Java SE specification using OpenJDK Technology Compatibility Kit test
suite"), licensed GPLv2 with the Classpath Exception like OpenJDK itself.

Why JavaFX comes from the JDK rather than a separate SDK
--------------------------------------------------------

**Decision: bundled.** The pinned JDK carries JavaFX; no separate OpenJFX SDK
or ``jmods`` archive is fetched.

Verified on this host after installation::

    $ cat $JAVA_HOME/lib/javafx.properties
    javafx.version=25.0.4+1
    javafx.runtime.version=25.0.4+1
    javafx.runtime.build=b01

    $ java --list-modules | grep javafx
    javafx.base@25.0.4.1
    javafx.controls@25.0.4.1
    javafx.fxml@25.0.4.1
    javafx.graphics@25.0.4.1
    javafx.media@25.0.4.1
    javafx.swing@25.0.4.1
    javafx.web@25.0.4.1

    $ ls $JAVA_HOME/jmods/javafx.*
    javafx.base.jmod  javafx.controls.jmod  javafx.fxml.jmod
    javafx.graphics.jmod  javafx.media.jmod  javafx.swing.jmod
    javafx.web.jmod

Note the version skew that this creates and that provenance must record
honestly: the *module* version is stamped ``25.0.4.1`` (the Liberica JDK
version), while the actual OpenJFX release is ``25.0.4+1``. Anything that
reports "the JavaFX version" must read ``lib/javafx.properties``, not the
module descriptor.

What the choice buys, and what it costs:

.. list-table::
   :header-rows: 1
   :widths: 20 40 40
   :class: longtable

   * - Aspect
     - Bundled JavaFX (chosen)
     - Separate OpenJFX SDK (rejected)
   * - Provenance
     - One artefact per platform to fetch, checksum and licence-audit.
     - Two per platform, and the ``jmods`` archive comes from a different
       host than the Maven artefacts.
   * - Phase 01's build
     - ``javac`` resolves ``javafx.*`` from the system modules, so no JavaFX
       dependency is needed in the POM at all. The build only compiles on a
       JavaFX-bundling JDK -- which is exactly the pinned toolchain, but it
       does mean "any JDK 25" is *not* enough.
     - POM declares ``org.openjfx:javafx-*`` with a platform classifier;
       builds on any JDK 25, but every developer and CI job must get the
       classifier right for its platform.
   * - ``jpackage``
     - ``--add-modules javafx.*`` is enough; ``jpackage``'s internal ``jlink``
       finds the ``jmods`` in the JDK. Proved below.
     - Needs ``--module-path`` pointing at a separate ``jmods`` directory, or
       the JavaFX jars shipped on the classpath in ``--input`` (which then
       are *not* in the runtime image and lose modular behaviour).
   * - Version control
     - JavaFX version is whatever BellSoft bundled. To move JavaFX you move
       the whole JDK.
     - JavaFX and the JDK move independently.

**Recommendation to Phase 01.** Compile against the bundled modules and do not
declare ``org.openjfx`` dependencies for the runtime; if a POM-declared
dependency is wanted for IDE or analysis reasons, declare it with
``<scope>provided</scope>`` and matching version ``25.0.4`` so it never reaches
the packaged image, because a second copy of ``javafx.*`` on the class path of
a runtime that already contains those modules is a split-package failure, not a
warning. This is Phase 01's decision to confirm; the constraint it must respect
is that the shipped runtime already contains JavaFX.

Why Maven
---------

**Recommendation: Apache Maven 3.9.16. Phase 01 owns the final decision** and
inherits whatever it chooses; this unit installs Maven so that later Phase 00
units and Phase 01 have a working build tool.

* Everything ``specification.rst`` names for the pull-request pipeline --
  JaCoCo coverage gates, ArchUnit, PIT mutation testing, SpotBugs-style static
  analysis, SBOM generation, dependency scanning -- has a mature, declarative
  Maven plugin. A declarative build is also easier to audit, which matters for
  a project whose deliverable includes a provenance record.
* Maven 4 is not yet generally available: the Apache archive's newest Maven 4
  directory on 2026-08-29 is ``4.0.0-rc-6``. Maven 3.9.16 is the newest
  stable release.
* The distribution is a 9 MB ``tar.gz`` with an Apache-published SHA-512, and
  this host has no ``unzip`` -- Gradle ships its distribution as a ``.zip``,
  which would have to be unpacked through Python.
* Maven is dependency-only at build time and is not redistributed with
  CometGUI, so its Apache-2.0 licence raises no distribution question.

Gradle's advantages -- incremental build speed, a programmable DSL -- weigh
less for a single desktop application than the plugin maturity and
auditability do. If Phase 01 disagrees, the switch costs this unit nothing:
only ``install-toolchain.sh`` and this document would change.

Verified, not assumed: Maven was made to build and run the spike on the pinned
JDK with ``--release 25`` -- see `Build-tool smoke test`_.

Provenance manifest
===================

``tools/`` is in ``.gitignore``. This table and
``scripts/feasibility/install-toolchain.sh`` are therefore the *only* committed
record of the toolchain, and between them they must be enough to reproduce it.

Every SHA-256 below was computed locally with ``sha256sum`` on the downloaded
file. Where upstream publishes its own checksum, the comparison is stated.

Installed on this host
----------------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - Field
     - Value
   * - Name
     - BellSoft Liberica JDK 25 Full (LTS)
   * - Version
     - ``25.0.4.1+1``; bundled OpenJFX ``25.0.4+1``
   * - File
     - ``bellsoft-jdk25.0.4.1+1-linux-amd64-full.tar.gz`` (407 472 475 bytes)
   * - URL
     - ``https://github.com/bell-sw/Liberica/releases/download/25.0.4.1%2B1/bellsoft-jdk25.0.4.1%2B1-linux-amd64-full.tar.gz``
   * - Date fetched
     - 2026-08-29
   * - SHA-256 (computed locally)
     - ``74de69863cfa8d58dd49992a97249ad041169ad01daa14a545ef9c7ef173cbd0``
   * - Published checksum
     - BellSoft's release API
       (``https://api.bell-sw.com/v1/liberica/releases``) publishes SHA-1 only:
       ``06b9069ceea8f3569546030b4663930193ffcfa6``. The locally computed SHA-1
       of the downloaded file **matches**. BellSoft publishes no SHA-256 for
       this artefact, so the pinned SHA-256 is ours.
   * - Licence
     - GPLv2 with the Classpath Exception. ``LICENSE`` in the distribution is
       GPL v2, June 1991; ``legal/java.base/ADDITIONAL_LICENSE_INFO`` carries
       the Classpath Exception text and ``legal/java.base/ASSEMBLY_EXCEPTION``
       the assembly exception. Per-module ``legal/`` directories exist for all
       seven ``javafx.*`` modules and for the ``jfx.incubator.*`` modules.
   * - Installed at
     - ``tools/liberica-jdk-25.0.4.1+1``

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - Field
     - Value
   * - Name
     - Apache Maven
   * - Version
     - ``3.9.16``
   * - File
     - ``apache-maven-3.9.16-bin.tar.gz`` (9 278 065 bytes)
   * - URL
     - ``https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz``
   * - Date fetched
     - 2026-08-29
   * - SHA-256 (computed locally)
     - ``80ffca22aed9e8b9713a232f3394fd81d7f20322df75efdb2b047dbd3e3a23bb``
   * - Published checksum
     - Apache publishes SHA-512 beside the artefact
       (``...-bin.tar.gz.sha512``):
       ``831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6``.
       The locally computed SHA-512 **matches**.
   * - Licence
     - Apache License 2.0 (``LICENSE`` and ``NOTICE`` in the distribution).
       Build-time only; not redistributed with CometGUI.
   * - Installed at
     - ``tools/apache-maven-3.9.16``

The same JDK on the other platforms
-----------------------------------

Not installed here, but fetched on 2026-08-29 and hashed locally so that
Phase 16's runners can pin the identical JDK release without re-deriving it.
Every SHA-1 below also matched the value BellSoft's API publishes for the same
file. All five archives unpack to a single top-level directory, named
``jdk-25.0.4.1-full`` except on macOS, where it is ``jdk-25.0.4.1-full.jdk``
with ``bin/`` directly inside it (no ``Contents/Home`` wrapper).

.. list-table::
   :header-rows: 1
   :widths: 20 46 34

   * - Platform
     - Archive and SHA-256 computed locally
     - Notes
   * - Linux x86-64
     - ``bellsoft-jdk25.0.4.1+1-linux-amd64-full.tar.gz``
       ``74de69863cfa8d58dd49992a97249ad041169ad01daa14a545ef9c7ef173cbd0``
     - Installed and proved here.
   * - Windows x64
     - ``bellsoft-jdk25.0.4.1+1-windows-amd64-full.zip``
       ``656ccc6c963925070df048c2d185fcce2903d3c5700c768d178ace72bee4d223``
     - Contains ``bin/jpackage.exe``, ``bin/jlink.exe`` and all seven
       ``jmods/javafx.*.jmod``. A ``.zip``; this host has no ``unzip``, so it
       was read with Python's ``zipfile``.
   * - macOS arm64
     - ``bellsoft-jdk25.0.4.1+1-macos-aarch64-full.tar.gz``
       ``18c273997a63db044401571bfeace4b3c57abaa76f12979f27f2af564a9682c0``
     - Contains ``bin/jpackage``, ``bin/jlink`` and the seven JavaFX jmods.
   * - macOS x86-64
     - ``bellsoft-jdk25.0.4.1+1-macos-amd64-full.tar.gz``
       ``f9db61e635175bad9f3475d18f299d34d09f7fcc4c1a5d366da62e2dc5a68b4a``
     - Tier-2 platform; artefact exists for the same release.
   * - Linux aarch64
     - ``bellsoft-jdk25.0.4.1+1-linux-aarch64-full.tar.gz``
       ``47e93cea18997caea7a8f220bd147c4c2c41ee2862c434e4a9a6d8e75240eab8``
     - Tier-3, unsupported in release 1; recorded because the artefact exists.

Reproducing the toolchain
=========================

``scripts/feasibility/install-toolchain.sh`` recreates ``tools/`` from nothing::

    rm -rf /workspace/tools
    bash /workspace/scripts/feasibility/install-toolchain.sh

It is idempotent: a second run re-verifies the archives, sees a matching stamp
file in each install directory and does no work. Downloads are cached under
``scratch/toolchain-cache`` (gitignored, and survives ``rm -rf tools``); set
``COMETGUI_TOOLCHAIN_NO_CACHE=1`` to force a fresh download, or
``COMETGUI_TOOLCHAIN_CACHE`` to move the cache.

Checksum handling, and the evidence that it works
-------------------------------------------------

The SHA-256 of every archive is verified **before** anything is unpacked, and a
mismatch is fatal. Three behaviours were tested rather than asserted, using a
copy of the script rooted in a throwaway directory:

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Test
     - Observed
   * - Cached archive corrupted, pin correct
     - ``maven: cached archive checksum mismatch, re-downloading`` --
       re-downloaded, verified, installed. A poisoned cache cannot be
       installed from.
   * - Pin deliberately set to ``deadbeef...``
     - ``[toolchain] FATAL: maven: SHA-256 MISMATCH ...`` printed to stderr
       with the expected and actual digests; exit status 1;
       ``tools/apache-maven-3.9.16`` **did not exist** afterwards and the bad
       archive was deleted from the cache.
   * - Clean run, then immediate re-run
     - Second run: ``cached archive verified`` and ``already installed ...
       (stamp matches)`` for both tools, then the same verification block.

Post-install verification (exit code 0 proves nothing)
------------------------------------------------------

The installer does not stop at unpacking. It asserts, and fails if any of these
is not true:

* ``java -XshowSettings:properties -version`` reports ``java.version`` exactly
  ``25.0.4.1``;
* ``javac``, ``jlink``, ``jpackage`` and ``jar`` exist and ``jpackage
  --version`` runs;
* all seven ``javafx.*`` modules are in ``java --list-modules`` **and** the
  corresponding ``jmods/javafx.*.jmod`` files exist -- so a JDK that is not a
  JavaFX-bundling build is rejected rather than silently accepted;
* ``mvn -v`` reports exactly ``3.9.16``.

Observed output of a from-scratch run::

    [toolchain] jdk: cached archive verified (sha256 74de6986...173cbd0)
    [toolchain] jdk: unpacking into /workspace/tools/liberica-jdk-25.0.4.1+1
    [toolchain] maven: unpacking into /workspace/tools/apache-maven-3.9.16
    [toolchain] wrote /workspace/tools/env.sh
    [toolchain] verifying installation
    [toolchain] java.version = 25.0.4.1
    [toolchain] jpackage --version = 25.0.4.1
    [toolchain] JavaFX modules present in runtime image and jmods: javafx.base
        javafx.controls javafx.fxml javafx.graphics javafx.media javafx.swing
        javafx.web
    [toolchain] Apache Maven 3.9.16

Using the toolchain
===================

The install directory is versioned and stable. Later work units and phases
should use these exact paths::

    JAVA_HOME=/workspace/tools/liberica-jdk-25.0.4.1+1
    MAVEN_HOME=/workspace/tools/apache-maven-3.9.16

Put it on ``PATH`` for the current shell, either explicitly::

    export JAVA_HOME=/workspace/tools/liberica-jdk-25.0.4.1+1
    export PATH="$JAVA_HOME/bin:/workspace/tools/apache-maven-3.9.16/bin:$PATH"

or through the file the installer generates (``tools/env.sh``, gitignored,
rewritten on every run)::

    . /workspace/tools/env.sh

Nothing is added to the host ``PATH`` permanently, and nothing is written to
``~/.m2``: pass ``-Dmaven.repo.local=/workspace/_build/m2repo`` to Maven so the
local repository stays inside the workspace.

Referencing JavaFX
------------------

JavaFX needs **no module path, no jars and no Maven dependency**: the seven
``javafx.*`` modules are part of the pinned runtime image, so ``javac`` and
``java`` resolve them as system modules. A JavaFX ``Application`` subclass can
be launched directly::

    $JAVA_HOME/bin/java MyFxApp.java

If a module path is wanted anyway, the loose jars are at
``$JAVA_HOME/lib/javafx*.jar`` and the ``jmods`` at ``$JAVA_HOME/jmods``, but
adding those to ``--module-path`` on top of the system modules duplicates them
and will fail. For packaging, name the modules with ``--add-modules`` and let
``jpackage``'s internal ``jlink`` take them from ``$JAVA_HOME/jmods``.

Two caveats for the GUI automation unit, neither of them resolved here:

* This host has no X display and no ``Xvfb``, and nothing may be installed on
  it. A headed run therefore needs something that does not exist here; a
  headless run needs Monocle
  (``-Dglass.platform=Monocle -Dmonocle.platform=Headless``). Whether the
  Liberica build ships a usable Monocle was **not** tested by this unit.
* ``java.vendor.version`` is ``null`` on this build. Anything identifying the
  runtime must use ``java.vendor`` (``BellSoft``) or the ``release`` file's
  ``IMPLEMENTOR``/``JAVA_RUNTIME_VERSION`` (``25.0.4.1+1-LTS``). This was found
  the hard way: the spike's first assertion looked for ``Liberica`` in
  ``java.vendor.version`` and failed.

The throwaway spike
===================

``scripts/feasibility/jpackage-spike/ToolchainProbe.java`` is a **throwaway
feasibility spike, not product code**. Phase 00 writes no product Java; this
class exists only so that the packaging proof is falsifiable. It prints, and
then asserts:

#. ``java.version`` is exactly the pinned ``25.0.4.1``;
#. ``java.vendor`` is ``BellSoft``;
#. ``java.home`` is the runtime *inside* a ``jpackage`` app-image -- that is,
   it ends ``lib/runtime`` in the app-image layout;
#. its own class was loaded from a jar inside that same app image, so the
   image is self-contained;
#. the JavaFX modules resolve from that bundled runtime.

On failure it prints ``PROBE RESULT        = FAIL`` with the reasons and exits
3. The assertion is not decorative: it rejected the first run of this proof.
Assertion 3 and 4 can be relaxed with ``-Dprobe.requireBundle=false`` for the
deliberate off-bundle runs.

Everything below is produced by ``scripts/feasibility/jpackage-proof.sh``,
which writes its logs to ``_build/jpackage-spike/logs/``.

The jpackage proof on Linux
===========================

Build the spike
---------------

::

    javac -d _build/jpackage-spike/classes \
        scripts/feasibility/jpackage-spike/ToolchainProbe.java
    jar --create --file _build/jpackage-spike/input/toolchain-probe.jar \
        --main-class ToolchainProbe -C _build/jpackage-spike/classes .

Produce the app-image
---------------------

::

    jpackage --type app-image \
        --name ToolchainProbe \
        --app-version 0.0.1 \
        --input _build/jpackage-spike/input \
        --main-jar toolchain-probe.jar \
        --main-class ToolchainProbe \
        --dest _build/jpackage-spike/dest \
        --add-modules java.base,java.logging,javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.swing,javafx.web \
        --verbose

Exact output::

    [00:42:06.207] Creating app package: ToolchainProbe in /workspace/_build/jpackage-spike/dest
    [00:42:12.383] Command [PID: -1]:
        jlink --output /workspace/_build/jpackage-spike/dest/ToolchainProbe/lib/runtime
        --add-modules javafx.base,javafx.web,javafx.controls,javafx.graphics,javafx.fxml,
        java.logging,javafx.swing,javafx.media,java.base --strip-native-commands
        --strip-debug --no-man-pages --no-header-files
    [00:42:12.383] Output:

    [00:42:12.383] Returned: 0

    [00:42:12.394] Using default package resource JavaApp.png [icon] (add ToolchainProbe.png to the resource-dir to customize).
    [00:42:12.410] Succeeded in building Linux Application Image package
    [exit code: 0]

(The ``jlink`` line is wrapped here for width; it is one line in the log.)

The bundle carries its own runtime
----------------------------------

::

    $ find _build/jpackage-spike/dest/ToolchainProbe -maxdepth 2 -mindepth 1 | sort
    .../ToolchainProbe/bin
    .../ToolchainProbe/bin/ToolchainProbe
    .../ToolchainProbe/lib
    .../ToolchainProbe/lib/ToolchainProbe.png
    .../ToolchainProbe/lib/app
    .../ToolchainProbe/lib/libapplauncher.so
    .../ToolchainProbe/lib/runtime

    $ ls -l .../ToolchainProbe/lib/runtime/lib/modules \
            .../ToolchainProbe/lib/runtime/lib/server/libjvm.so
    -rw-r--r-- 1 agent agent 75512633 .../lib/runtime/lib/modules
    -rw-r--r-- 1 agent agent 28850704 .../lib/runtime/lib/server/libjvm.so

    $ ls .../ToolchainProbe/lib/runtime/lib | grep -E '^lib(glass|javafx|prism|decora|fxplugins)'
    libdecora_sse.so
    libfxplugins.so
    libglass.so
    libglassgtk3.so
    libjavafx_font.so
    libjavafx_font_freetype.so
    libjavafx_font_pango.so
    libjavafx_iio.so
    libprism_common.so
    libprism_es2.so
    libprism_sw.so

    $ cat .../ToolchainProbe/lib/runtime/release
    JAVA_VERSION="25.0.4.1"
    MODULES="java.base java.datatransfer java.xml java.prefs java.desktop
    java.logging java.net.http java.scripting javafx.base jdk.unsupported
    javafx.graphics javafx.controls javafx.fxml javafx.media
    jdk.unsupported.desktop javafx.swing jdk.jsobject jdk.xml.dom javafx.web"

    $ du -sh .../ToolchainProbe
    284M	.../ToolchainProbe

Launch it -- the actual proof
-----------------------------

::

    $ _build/jpackage-spike/dest/ToolchainProbe/bin/ToolchainProbe
    === CometGUI toolchain probe (throwaway Phase 00 spike) ===
    java.version        = 25.0.4.1
    java.vendor         = BellSoft
    java.vendor.version = null
    java.runtime.name   = OpenJDK Runtime Environment
    java.vm.name        = OpenJDK 64-Bit Server VM
    java.home           = /workspace/_build/jpackage-spike/dest/ToolchainProbe/lib/runtime
    code source         = /workspace/_build/jpackage-spike/dest/ToolchainProbe/lib/app/toolchain-probe.jar
    app image root      = /workspace/_build/jpackage-spike/dest/ToolchainProbe
    bundled layout      = true
    self contained      = true
    env JAVA_HOME       = /workspace/tools/liberica-jdk-25.0.4.1+1
    env PATH            = /workspace/tools/liberica-jdk-25.0.4.1+1/bin:...
    javafx modules      = all present
    os                  = Linux amd64
    PROBE RESULT        = PASS
    === end probe ===
    [exit code: 0]

``java.home`` is the runtime *inside the bundle*, not
``tools/liberica-jdk-25.0.4.1+1``, and the code that ran came from
``lib/app/toolchain-probe.jar`` inside the same bundle.

Launch it with no JDK in the environment
----------------------------------------

To rule out any dependence on an external JDK, the same launcher was run with
an emptied environment -- no ``JAVA_HOME``, and a ``PATH`` with no JDK on it::

    $ env -i PATH=/usr/bin:/bin HOME=/home/agent \
        _build/jpackage-spike/dest/ToolchainProbe/bin/ToolchainProbe
    === CometGUI toolchain probe (throwaway Phase 00 spike) ===
    java.version        = 25.0.4.1
    java.vendor         = BellSoft
    java.home           = /workspace/_build/jpackage-spike/dest/ToolchainProbe/lib/runtime
    code source         = /workspace/_build/jpackage-spike/dest/ToolchainProbe/lib/app/toolchain-probe.jar
    app image root      = /workspace/_build/jpackage-spike/dest/ToolchainProbe
    bundled layout      = true
    self contained      = true
    env JAVA_HOME       = null
    env PATH            = /usr/bin:/bin
    javafx modules      = all present
    os                  = Linux amd64
    PROBE RESULT        = PASS
    === end probe ===
    [exit code: 0]

That is exit gate item 4 met on Linux for ``app-image``: the bundle exists, it
launches, and what it printed proves it ran the pinned runtime from inside
itself.

The bundled runtime has no ``java`` launcher by default
--------------------------------------------------------

``jpackage`` runs ``jlink`` with ``--strip-native-commands`` unless
``--jlink-options`` is supplied, so ``lib/runtime/bin`` does not exist in the
image above::

    $ ls .../ToolchainProbe/lib/runtime/bin
    ls: cannot access '.../lib/runtime/bin': No such file or directory

**This matters to the product, not just to the spike.** CometGUI has to run the
Limelight converter, which upstream distributes as a JAR. A shipped runtime
with no ``java`` binary cannot do that by launching a child process; the
options are to load the converter in-process, or to keep the launcher.
Supplying ``--jlink-options`` replaces ``jpackage``'s defaults and keeps it::

    $ jpackage --type app-image --name ToolchainProbeWithJava ... \
        --jlink-options "--strip-debug --no-man-pages --no-header-files"
    [exit code: 0]

    $ ls .../ToolchainProbeWithJava/lib/runtime/bin
    java
    jrunscript
    keytool

    $ .../ToolchainProbeWithJava/lib/runtime/bin/java -version
    openjdk version "25.0.4.1" 2026-08-18 LTS
    OpenJDK Runtime Environment (build 25.0.4.1+1-LTS)
    OpenJDK 64-Bit Server VM (build 25.0.4.1+1-LTS, mixed mode)

    $ .../ToolchainProbeWithJava/lib/runtime/bin/java --list-modules | grep javafx
    javafx.base@25.0.4.1
    javafx.controls@25.0.4.1
    javafx.fxml@25.0.4.1
    javafx.graphics@25.0.4.1
    javafx.media@25.0.4.1
    javafx.swing@25.0.4.1
    javafx.web@25.0.4.1

    $ du -sh .../ToolchainProbeWithJava
    284M	.../ToolchainProbeWithJava

Keeping the launchers costs nothing measurable (both images are 284 MB) and it
is what makes the Limelight converter runnable from the shipped product. Phase
01 and Phase 16 should decide this deliberately.

What jpackage needs on Linux, per output type
=============================================

The two output kinds have different requirements, and this host can satisfy
only one of them.

.. list-table::
   :header-rows: 1
   :widths: 14 30 28 28

   * - Output
     - External tools it needs
     - On this host
     - Result observed
   * - ``app-image``
     - None beyond the JDK itself. The runtime is built by the JDK's own
       ``jlink``; the launcher is a prebuilt ``libapplauncher.so`` shipped in
       the JDK.
     - Satisfied.
     - Built, launched, verified.
   * - ``deb``
     - ``fakeroot``, ``dpkg-deb`` and ``dpkg`` -- the tool names referenced by
       ``LinuxDebBundler`` and ``LinuxPackageArch$DebPackageArch`` in the
       JDK's ``jdk.jpackage`` module.
     - ``dpkg`` and ``dpkg-deb`` present; ``fakeroot`` **absent** and it may
       not be installed.
     - ``Bundler DEB Bundle skipped because of a configuration problem: Can
       not find fakeroot. Reason: Cannot run program "fakeroot": Exec failed,
       error: 2 (No such file or directory)`` / ``Advice to fix: Please
       install required packages``; exit 1.
   * - ``rpm``
     - ``rpmbuild`` -- referenced by ``LinuxRpmBundler`` and
       ``LinuxPackageArch$RpmPackageArch$RpmArchReader``.
     - Absent, and may not be installed.
     - ``Error: Invalid or unsupported type: [rpm]``; exit 1. Note the
       difference: the RPM bundler is not merely unusable, it is not offered
       at all, so a build script cannot distinguish "rpm unsupported" from a
       typo by the message alone.

The tool names were read out of the JDK itself (string constants in
``jdk.jpackage.jmod``), not from documentation. Oracle's *Packaging Overview*
for JDK 25 agrees: "For Red Hat Linux, the ``rpm-build`` package is required"
and "For Ubuntu Linux, the ``fakeroot`` package is required".

**Consequence for Phase 01 and Phase 16.** On this machine CometGUI can be
packaged as a self-contained Linux ``app-image`` and shipped as a ``tar.gz`` of
that directory -- which is exactly the packaging
``specification.rst`` lists first for Linux x86-64 ("``tar.gz`` + ``.deb``/
AppImage as infrastructure permits"). A ``.deb`` cannot be produced here. It
needs a Linux runner with ``fakeroot`` installed -- one ``apt`` package on a
runner the project controls, not on this host. Phase 16 should treat the
``.deb`` as an infrastructure-dependent extra and the ``app-image`` tarball as
the guaranteed Linux artefact. AppImage was not investigated by this unit.

jpackage is not a cross-compiler
================================

Verified locally rather than taken on trust. On this Linux JDK::

    $ jpackage --help
    ...
      --type -t <type>
              The type of package to create
              Valid values are: {"app-image", "rpm", "deb"}

Only the Linux types are offered, although the JDK 25 tool specification lists
``{"app-image", "exe", "msi", "rpm", "deb", "pkg", "dmg"}``. The reason is
visible inside the module: the Linux JDK's ``jdk.jpackage.jmod`` contains
``LinuxAppBundler``, ``LinuxDebBundler`` and ``LinuxRpmBundler`` and **no**
Windows or macOS bundler class at all. The Windows and macOS JDKs of the same
release, inspected the same way, each contain only their own:

.. list-table::
   :header-rows: 1
   :widths: 18 44 38

   * - JDK
     - Bundler classes in ``jdk.jpackage.jmod``
     - External tools named in those classes
   * - Linux x86-64
     - ``LinuxAppBundler``, ``LinuxDebBundler``, ``LinuxRpmBundler``
     - ``fakeroot``, ``dpkg-deb``, ``dpkg``, ``rpmbuild``
   * - Windows x64
     - ``WinAppBundler``, ``WinExeBundler``, ``WinMsiBundler``
     - WiX: ``candle``/``light`` (v3) or ``wix`` (v4/v5)
   * - macOS arm64
     - ``MacAppBundler``, ``MacDmgBundler``, ``MacPkgBundler``,
       plus ``AppImageSigner``, ``Codesign``, ``SigningIdentity``
     - ``/usr/bin/codesign``, ``/usr/bin/xcrun``, ``/usr/bin/security``,
       ``/usr/bin/hdiutil``, ``/usr/bin/pkgbuild``, ``/usr/bin/productbuild``

The JDK 25 ``jpackage`` tool specification states it outright: "Each format
must be built on the platform it runs on, there is no cross-platform support."
The *Packaging Overview* adds: "To package your application for multiple
platforms, you must run the packaging tool on each platform. If you want more
than one format for a platform, you must run the tool once for each format."

The Windows runner
------------------

Not verifiable on this host -- there is no Windows machine in this environment.
What follows is read out of the Windows JDK's own module plus Oracle's JDK 25
documentation, and is marked accordingly.

* **JDK**: ``bellsoft-jdk25.0.4.1+1-windows-amd64-full.zip``, SHA-256
  ``656ccc6c963925070df048c2d185fcce2903d3c5700c768d178ace72bee4d223``
  (computed locally). It contains ``bin/jpackage.exe``, ``bin/jlink.exe`` and
  the seven ``javafx.*`` jmods, so the JavaFX story is identical to Linux.
* **``app-image``**: needs nothing but the JDK, by the same reasoning as on
  Linux. This is the low-risk Windows artefact.
* **``msi`` and ``exe``**: need the **WiX Toolset** on ``PATH``. The bundler's
  own message is precise about what it looks for: "Can not find WiX tools. Was
  looking for WiX v3 ``light.exe`` and ``candle.exe`` or WiX v4/v5 ``wix.exe``
  and none was found", with the advice "Download WiX 3.0 or later from
  https://wixtoolset.org and add it to the PATH." The JDK 25 bundler therefore
  accepts either the v3 tools or the v4/v5 single ``wix.exe``; it also scans
  directories matching ``WiX Toolset v*``. ``exe`` is built by wrapping an
  ``msi``, so it has the same requirement.
* **Signing** is not required to produce an ``msi``. Windows SmartScreen
  reputation and any code-signing certificate question is a distribution
  matter, not a ``jpackage`` requirement, and is outside this unit's scope.
* **Companion files.** ``specification.rst`` records that Windows is the only
  platform on which Comet reads Thermo RAW, and only with ``CometWrapper.dll``
  and the ``ThermoFisher.*`` files beside the executable. Those are installed
  by the product at run time, not by ``jpackage``; noted so Phase 16 does not
  try to bake them into the bundle.

The macOS runner
----------------

Also not verifiable here. Same sourcing caveat.

* **JDK**: ``bellsoft-jdk25.0.4.1+1-macos-aarch64-full.tar.gz``, SHA-256
  ``18c273997a63db044401571bfeace4b3c57abaa76f12979f27f2af564a9682c0``;
  x86-64 equivalent ``f9db61e6...c5a68b4a``. Both carry ``bin/jpackage`` and
  the JavaFX jmods. The archive unpacks to ``jdk-25.0.4.1-full.jdk/`` with
  ``bin/`` directly inside -- there is no ``Contents/Home`` level, which a
  setup script must not assume.
* **``app-image``** produces a ``.app`` bundle and needs no extra tooling. On
  Apple silicon, macOS refuses to run unsigned arm64 binaries, so ``jpackage``
  ad-hoc signs the image; that is enough to run locally but not to distribute.
* **``dmg``** invokes ``/usr/bin/hdiutil``; **``pkg``** invokes
  ``/usr/bin/pkgbuild`` and ``/usr/bin/productbuild``. All three ship with
  macOS. Oracle adds that "Xcode command line tools are required when the
  ``--mac-sign`` option is used ... and when the ``--icon`` option is used to
  customize the DMG image" -- so an unsigned, default-icon ``dmg`` needs no
  Xcode, and everything the project actually wants to ship does.
* **Signing**: ``--mac-sign`` with ``--mac-signing-key-user-name`` or the
  explicit ``--mac-app-image-sign-identity`` / ``--mac-installer-sign-identity``
  pair, optionally ``--mac-signing-keychain`` and ``--mac-entitlements``.
  ``jpackage`` shells out to ``/usr/bin/codesign`` and reads identities through
  ``/usr/bin/security``. Distribution needs an Apple Developer Program
  membership: a *Developer ID Application* certificate for the ``.app`` and a
  *Developer ID Installer* certificate for a ``.pkg``. These are the same
  identities for both tier-1 and tier-2 macOS.
* **Notarisation is not done by jpackage.** The string ``notarytool`` does not
  appear anywhere in the macOS ``jdk.jpackage`` module. Notarising is a
  separate step after packaging -- submit the ``dmg``/``pkg`` with ``xcrun
  notarytool submit --wait`` and staple the ticket with ``xcrun stapler
  staple`` -- and it needs an Apple ID with an app-specific password or an App
  Store Connect API key held as a CI secret. Without it Gatekeeper will refuse
  a downloaded build on a user's machine.
* **Rosetta 2.** Unrelated to ``jpackage`` but relevant to the same runner:
  ``specification.rst`` records that the XML-capable Percolator artefact is
  x86-64 only, so that stage runs under Rosetta 2 on Apple silicon.

**Cost to Phase 16.** Three runners (Linux x86-64, Windows x64, macOS arm64,
plus optionally macOS x86-64) each running ``jpackage`` for their own formats;
one ``apt`` package on the Linux runner for ``.deb``; a WiX installation on the
Windows runner; and on macOS an Apple Developer Program membership plus two
certificates and a notarisation credential, all held as CI secrets. Only the
``app-image`` path is free of external requirements on every platform.

Build-tool smoke test
=====================

``mvn -v`` printing a version number is not evidence that the build tool works.
``scripts/feasibility/maven-smoke.sh`` builds the same spike through Maven with
``maven.compiler.release`` 25, forcing the local repository inside the
workspace, then runs the produced jar and checks its output::

    $ bash scripts/feasibility/maven-smoke.sh
    ...
    [INFO] Building jar: /workspace/_build/maven-smoke/target/toolchain-probe-0.0.1.jar
    [INFO] BUILD SUCCESS
    [INFO] Total time:  6.360 s

    == run the Maven-built executable jar ==
    java.version        = 25.0.4.1
    java.vendor         = BellSoft
    code source         = /workspace/_build/maven-smoke/target/toolchain-probe-0.0.1.jar
    javafx modules      = all present
    PROBE RESULT        = PASS

    OK: Apache Maven 3.9.16 built and ran the spike on JDK
    openjdk version "25.0.4.1" 2026-08-18 LTS

Maven resolved its plugins from Maven Central over the network into
``_build/m2repo``; nothing was written to ``~/.m2``. Maven 3.9.16 and
``maven-jar-plugin`` 3.4.2 run correctly on JDK 25 and ``--release 25``
compiles.

What this unit did not verify
=============================

Stated plainly, because an unverified item is not a passed item.

* **Windows and macOS packaging was not executed.** No Windows or macOS
  machine exists in this environment. The Windows and macOS sections above are
  derived from the JDK's own module contents (locally inspected, which is real
  evidence about *what the tool will look for*) and from Oracle's JDK 25
  documentation (which is not local evidence). No claim is made that an
  ``msi``, ``dmg`` or ``pkg`` has been built.
* **``.deb`` and ``.rpm`` were not produced**, only attempted. Whether the
  ``deb`` bundler succeeds once ``fakeroot`` exists is untested.
* **JavaFX rendering was not exercised.** This unit proved the modules resolve
  in the bundled runtime; it did not start a JavaFX ``Application``. That is
  work unit 7's job, headless and headed.
* **Monocle availability was not tested** in this Liberica build.
* **BellSoft publishes no SHA-256** for its artefacts. The SHA-1 it publishes
  matched for all five files, and the SHA-256 values pinned here are ours.
  SHA-1 is not a strong integrity guarantee on its own; the transport was
  HTTPS to ``github.com``.
* **The licence audit is not done.** GPLv2-with-Classpath-Exception is what
  the distribution's own files say, and the Classpath Exception is what makes
  bundling an OpenJDK runtime with an application ordinarily acceptable. The
  redistribution question for the product as a whole belongs to the licensing
  decisions in ``DECISIONS.rst`` and to Phase 16's licence audit, not here.
