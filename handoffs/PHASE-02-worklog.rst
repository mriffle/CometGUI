=========================================================
PHASE-02 work log -- Application Shell and Navigation
=========================================================

:Phase: 02
:Phase orchestrator: Phase-02 orchestrator subagent (session 03)
:Started: 2026-08-30

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Baseline observed before any work started
=========================================

``git status`` clean at ``82609f0``; ``bash scripts/build.sh`` printed
``11/11 stages OK in 88 seconds. BUILD OK`` with
``6 report file(s): tests=54 failures=0 errors=0 skipped=0``. That is the
tree every unit below starts from.

Decisions taken by the orchestrator before decomposing
======================================================

These are engineering choices, not ``D-`` items. They are recorded here
because several later phases inherit them.

Reuse of CasanovoGUI is real, and it is exactly three files
    ``R-SEC-01`` was amended in specification revision 10 (2026-08-30) while
    this phase was reading in: reuse is permitted and carries an attribution
    obligation enforced by the build. ``Noble-Lab/CasanovoGUI`` was cloned
    read-only into ``scratch/casanovogui`` (gitignored) at commit
    ``480b3013e7f8fb51a2b8c58681043821e3e7f865``, 2026-08-29. **No upstream
    file carries a per-file copyright notice** -- ``grep -rl Copyright
    --include='*.java' src`` matches nothing, and every source file begins
    with ``package``. The derived header therefore attributes to *the
    CasanovoGUI authors* collectively and names the upstream path and commit,
    which is the strongest honest form available.

Derived files are selected by path, not by judgement
    Any Java file whose path contains ``/derived/`` is a derived file. That
    makes the Spotless file set and the second Checkstyle execution
    mechanically exact rather than a matter of someone remembering.

AtlantaFX is referenced only from ``cometgui-app``
    ``LayeringRulesTest.UI_MAY_DEPEND_ON`` allows ``org.cometgui.ui..`` to
    depend on the JDK, JavaFX and the CometGUI domain modules and on nothing
    else. Putting the theme in the bootstrap module leaves that allowlist
    untouched, so the phase adds no exemption to an architecture rule.

glibc is probed through the FFM API, not a subprocess
    ``R-PROC-02`` confines ``ProcessBuilder`` to the process service, which
    Phase 03 owns, so a ``ldd --version`` probe is not available to this
    phase. ``Linker.nativeLinker().defaultLookup().find("gnu_get_libc_version")``
    is, and the orchestrator verified it on this host before writing the unit:
    it printed ``symbol present: true`` and ``glibc version: 2.36``. The
    adapter lives in ``cometgui-app`` (no coverage or mutation gate, because
    its non-Linux branches cannot be exercised here); the parsing and
    comparison logic lives in ``cometgui-domain`` under the full 90/85 +
    PIT-80 gates.

Decomposition
=============

Ten work units. **They are almost entirely serial**, and that is deliberate:
a second phase orchestrator is live in this same tree, every unit runs Maven,
and two Maven reactors sharing ``_build/m2repo`` and the modules' ``target/``
directories corrupt each other. Only the last two units, which run different
tools, are run together.

.. list-table:: Order and file ownership
   :header-rows: 1
   :widths: 8 46 46

   * - Unit
     - What it owns
     - Why it cannot move earlier

   * - 1
     - ``cometgui-domain`` ``org.cometgui.domain.ports``,
       ``.platform``, ``.run``
     - Nothing depends on it; it is the base every later unit imports.

   * - 2
     - ``cometgui-domain`` ``org.cometgui.domain.log``
     - Needs unit 1's ``StageTag``.

   * - 3
     - ``cometgui-workflow`` ``org.cometgui.workflow.state``,
       ``cometgui-workflow/pom.xml``
     - Needs unit 1's ``StageTag`` for ``WorkflowStage`` to implement.

   * - 4
     - ``pom.xml``, ``config/license/java-header-derived.txt``,
       ``config/checkstyle/checkstyle-derived.xml``,
       ``scripts/verify-derivation-gate.sh``, ``scripts/build.sh`` format
       stage, ``cometgui-app`` theme support (first derived file)
     - The machinery must exist and be proved before any other derived file
       is written.

   * - 5
     - ``cometgui-ui`` ``org.cometgui.ui.viewmodel``
     - Needs units 1--3.

   * - 6
     - ``cometgui-ui`` ``org.cometgui.ui.view``, ``.controls`` (including the
       second derived file)
     - Needs unit 5 and unit 4's machinery.

   * - 7
     - ``cometgui-app`` ``org.cometgui.app.bootstrap``, ``.config``,
       ``cometgui-app/pom.xml``
     - Needs the shell to exist before it can start it.

   * - 8
     - ``cometgui-ui`` and ``cometgui-app`` test sources: ``FxUiDriver`` and
       the headless GUI suite
     - Needs a running application to drive.

   * - 9
     - ``docs/developer/architecture.rst``, ``docs/traceability-map.toml``
     - Describes the layering *as built*, so it goes last.

   * - 10
     - ``scripts/verify-shell-gates.sh``, ``scripts/verify-all-gates.sh``
     - Proves each of the five gate items fails on an injected defect, so
       every gate must exist first.

Paths no unit may touch: ``.github/workflows/``, ``scripts/ci/`` and
``docs/feasibility/`` are owned by a concurrently running phase orchestrator.
``STATUS.rst``, ``DECISIONS.rst``, ``specification.rst`` and ``phases/`` are
tier 1's.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 4 30 14 52

   * - #
     - Unit and acceptance conditions
     - Rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Injection seams and host baseline.** Seven injectable seams as
       interfaces in ``org.cometgui.domain.ports`` (clock via
       ``java.time.Clock``, ``EnvironmentReader``, ``ProcessRunner``,
       ``Downloader``, ``FileSystemAccess``, ``RunIdSource``,
       ``HashService``) with the minimum supporting value types;
       ``org.cometgui.domain.platform`` carrying ``GlibcVersion`` (parse,
       compare) and ``HostBaselineVerifier`` producing a report with distinct
       outcomes and actionable messages; ``org.cometgui.domain.run.StageTag``.
       Accepted only with cometgui-domain still at >= 0.90 line / 0.85 branch
       and PIT >= 80%, no JavaFX and no ``ProcessBuilder``.
     - ``R-PROC-01``, ``R-PLAT-01``
     - **SIGNED OFF 2026-08-30**, commits ``f34db9d`` and ``9835ae1``. I read
       both diffs in full. ``git show --name-only`` over the two commits lists
       nothing outside ``cometgui-domain/``, and
       ``git log 57964e7..HEAD -- pom.xml config/ scripts/ .mvn/`` is empty, so
       no gate, threshold or exclusion was touched. I ran
       ``mvn -B -pl cometgui-domain -am verify``: ``BUILD SUCCESS``,
       ``0 Checkstyle violations``, ``BugInstance size is 0``,
       ``All coverage checks have been met``. Reading the reports myself:
       194 tests, 0 failures, 0 errors, 0 skipped across eight classes;
       ``jacoco.xml`` final counters ``LINE missed=0 covered=237`` and
       ``BRANCH missed=0 covered=124`` -- 100% against the 0.90/0.85 gate. I
       ran PIT myself and counted ``mutations.xml`` rather than trusting the
       exit code: ``total=129 killed=129 survived=0``, 100% against the 80%
       threshold. **Falsification of my own**, in a ``git archive`` sandbox
       under ``_build/``: inverting one character in
       ``HostBaselineVerifier`` (``!sixtyFourBit.get()`` to
       ``sixtyFourBit.get()``) turned 11 tests red across three nested
       classes, naming ``theBlockingArchitectureOutranksTheBlockingGlibc``,
       ``aKnownThirtyTwoBitArchitectureBlocks`` and both warning cases; the
       sandbox was deleted afterwards and the working tree never carried the
       defect. No ``assertDoesNotThrow`` and no bare ``assertNotNull`` appears
       in the unit's tests. Every new ``.java`` file carries the exact D-009
       header line. ``bash scripts/build.sh``: ``11/11 stages OK in 99
       seconds. BUILD OK``.

   * - 2
     - **Bounded console message model.** ``org.cometgui.domain.log`` with a
       documented cap, discard-oldest, stage tagging and thread safety; a
       flood test appending far more than the cap that asserts the retained
       size, that the *oldest* were the ones discarded, and that retained heap
       stays inside a documented bound.
     - ``R-PROC-03``, gate 5
     - **SIGNED OFF 2026-08-30**, commit ``b75ee48``. Diff read in full; nine
       files, all under ``cometgui-domain/src``, nothing else touched and no
       file from unit 1 modified. ``mvn -B -pl cometgui-domain -am verify``:
       ``0 Checkstyle violations``, ``BugInstance size is 0``,
       ``All coverage checks have been met``, and my own count over the
       surefire XML gives ``tests=256 failures=0 errors=0 skipped=0``.
       ``jacoco.xml`` bundle totals ``LINE missed=0 covered=301`` and
       ``BRANCH missed=0 covered=144``. PIT counted by me from
       ``mutations.xml``: ``total=152 killed=152 survived=0``, 100%. The flood
       test's own printed line, from the surefire report of my run:
       ``flood: appended=1000000 capacity=10000 size=10000 discarded=990000
       heapBefore=5116176 heapAfter=7238864 growth=2122688 limit=33554432
       elapsedMillis=497`` -- a real measurement, 16x inside its documented
       bound. **Falsification of my own**, distinct from the agent's and run in
       a ``git archive`` sandbox: changing the discard test from
       ``messages.size() == capacity`` to ``messages.size() > capacity`` -- a
       single character, leaving discarding switched on -- fails five
       assertions in the flood test, including
       ``size is exactly the capacity ==> expected: <10000> but was: <10001>``
       and ``discarded is appended minus capacity ==> expected: <990000> but
       was: <989999>``. The cap is therefore proved exact rather than
       approximate. ``bash scripts/build.sh``: ``11/11 stages OK in 100
       seconds. BUILD OK``. Judgement call I accepted: the agent replaced
       ``System.gc()`` with ``ManagementFactory.getMemoryMXBean().gc()``
       because SpotBugs rejects ``DM_GC`` at ``threshold=Low``; that is a fix
       in the code rather than the forbidden exclusion, and the measurement
       itself is unchanged.

   * - 3
     - **Workflow stage and state model.** ``org.cometgui.workflow.state``
       with the specification's nine step states, the stage enumeration
       matching the stepper diagram, and run-state derivation; the module's
       mutation gate switched on because ``org.cometgui.workflow.state.*`` is
       a PIT target package.
     - stage stepper
     - **SIGNED OFF 2026-08-30**, commit ``f981b71``. Diff read in full: ten
       paths, all under ``cometgui-workflow/``, and
       ``git log 57964e7..HEAD -- pom.xml config/ scripts/ .mvn/`` is still
       empty, so the parent POM's thresholds and PIT ``<targetClasses>`` were
       not touched. The module POM change is a gate being switched **on**
       (``<cometgui.mutation.skip>false</cometgui.mutation.skip>``), which is
       what the drift guard requires once ``org.cometgui.workflow.state`` has
       classes. ``mvn -B -pl cometgui-workflow -am verify``:
       ``Tests run: 134, Failures: 0, Errors: 0, Skipped: 0``,
       ``BugInstance size is 0``, ``BUILD SUCCESS``. ``jacoco.xml`` bundle
       totals ``LINE missed=0 covered=115`` and ``BRANCH missed=0 covered=48``.
       PIT counted by me from ``mutations.xml``: ``total=43 killed=43
       survived=0``. ``bash scripts/build.sh --only gates`` prints
       ``ok  cometgui-workflow  43/43 mutations killed = 100.0%`` and
       ``ok  every module with critical-package code has its mutation gate
       on`` -- neither ``OFF`` nor ``MISSING``, which is the drift guard
       confirming the switch is real. **Falsification of my own**, distinct
       from the agent's six, in a ``git archive`` sandbox: making
       ``WorkflowStage.isCore()`` return true for ``PDV`` -- one added clause,
       reclassifying an optional stage as core -- produced errors or failures
       in 45 tests across seven nested classes, including the exact-message
       assertion ``expected: <... missing: comet> but was: <... missing: comet,
       pdv>``. The core/downstream distinction the run-state derivation rests
       on is therefore load-bearing and tested. ``bash scripts/build.sh``:
       ``11/11 stages OK in 108 seconds. BUILD OK``. Decision I accepted and
       am recording: a ``FAILED`` optional downstream stage does **not** make
       the run ``FAILED``; it is documented in ``RunState``'s Javadoc and
       tested both ways.

   * - 4
     - **Derived-file attribution machinery, and the first derived file.** A
       second Spotless file set and a second Checkstyle execution with their
       own header file, the first narrowed to exclude ``/derived/``; a
       required per-file derivation record; ``scripts/build.sh`` proving every
       ``.java`` file was inspected by exactly one of the two regimes; a
       negative control showing an unattributed derived file is rejected.
     - ``R-SEC-01``, ``D-001``
     - **SIGNED OFF 2026-08-30**, commits ``7517d3d``, ``33f7b3a``,
       ``5d9904f``, ``4d6674f``, plus my own ``6c1fdd7``. I read all four
       diffs. ``git diff --name-only`` over the range lists eleven files and
       nothing under ``.github/``, ``scripts/ci/``, ``docs/feasibility/``,
       ``STATUS.rst``, ``DECISIONS.rst``, ``specification.rst``, ``phases/``
       or ``handoffs/``. **The ordinary header check was not deleted, relaxed
       or suppressed**: ``config/license/java-header.txt`` and the ``Header``
       module in ``checkstyle.xml`` are byte-for-byte unchanged, the ordinary
       Spotless and Checkstyle executions still run over every non-derived
       file, and the derived paths they now exclude are covered by a second
       Spotless execution (``spotless-check-derived``, carrying
       google-java-format, so formatting was not quietly dropped) and a second
       Checkstyle execution (``checkstyle-check-derived``) against a rule set
       that is a **superset** of the ordinary one. The D-009 line
       ``Copyright (C) 2026 The CometGUI authors.`` appears verbatim in both
       header files -- I grepped for the exact line and got 1 in each.
       ``bash scripts/build.sh``: ``11/11 stages OK in 112 seconds. BUILD OK``,
       and the new census prints
       ``ok cometgui-app  Spotless 6 ordinary + 2 derived = 8 file(s) on disk``
       and the same for Checkstyle, over all twelve modules.
       ``bash scripts/verify-quality-gates.sh``:
       ``SUMMARY: 42 control(s) passed, 0 failed, in 120 seconds`` and
       ``Every gate rejected its defect and accepted the clean tree.``
       ``bash scripts/ci/docs-build.sh``: ``PASSED``.
       **Two falsifications of my own**, in ``git archive`` sandboxes and
       distinct from the agent's seven controls. (a) Deleting the derivation
       record from the **real** derived file
       (``AtlantaFxThemes.java``) and running
       ``mvn -pl cometgui-app checkstyle:check@checkstyle-check-derived``
       gives ``BUILD FAILURE`` and
       ``AtlantaFxThemes.java:1: Required pattern 'the derivation record ...'
       missing in file. [Regexp]``. (b) Deleting the whole
       ``checkstyle-check-derived`` execution from ``pom.xml`` -- the exact
       move someone would make to get a stubborn derived file through --
       leaves ``mvn clean validate`` **green, exit 0**, and the census catches
       it: ``UNCHECKED cometgui-app: Checkstyle inspected NEITHER file set
       for: .../derived/AtlantaFxThemes.java .../derived/package-info.java``,
       ``MISMATCH cometgui-app: 8 .java source(s) on disk, 6 ordinary + 0
       derived = 6 seen by Checkstyle``, ``FATAL: 2 file-set census check(s)
       failed``. That is the "excluded from set 1 and missed by set 2" hole
       closed and demonstrated.
       **A defect of my own, found and fixed by this sign-off**: my edit to
       ``checkstyle.xml``'s GROUP 0 comment introduced a literal ``--`` inside
       an XML comment, which is illegal and made Checkstyle fail to load the
       rule set. The census reported ``FATAL: 24 file-set census check(s)
       failed`` and Maven printed ``MojoExecutionException``; fixed in
       ``6c1fdd7`` and both rule sets now parse
       (``xml.dom.minidom.parse`` on each). Recorded because the project's own
       rule sets deliberately use single hyphens in prose for this reason.
       **Accepted with a caveat, reported upward**: a bare
       ``mvn checkstyle:check`` from the command line now fails on derived
       files, because the include/exclude split lives on the executions and
       the CLI invocation uses the plugin-level configuration. ``mvn verify``
       and ``bash scripts/build.sh`` are unaffected. The alternative -- moving
       the excludes into ``<pluginManagement>`` -- would make the derived
       execution inherit them and check **nothing** while exiting 0, which is
       strictly worse. ``CONTRIBUTING.rst`` documents ``mvn -pl <module>
       validate`` as the command to use instead.

   * - 5
     - **View-models.** ``org.cometgui.ui.viewmodel``: navigation over the
       eight primary sections, the console view-model with a stage filter,
       and the stage stepper view-model. >= 0.80 line coverage on the
       package.
     - ``R-PROC-01``
     - **SIGNED OFF 2026-08-30**, commit ``9a2e9d9``. Diff read; every path is
       under ``cometgui-ui/src/``, and no gate configuration changed.
       ``mvn -B -pl cometgui-ui -am verify``:
       ``Tests run: 156, Failures: 0, Errors: 0, Skipped: 0``,
       ``BugInstance size is 0``, ``All coverage checks have been met``,
       ``BUILD SUCCESS``. I read the coverage the rule actually measures --
       the ``<package name="org/cometgui/ui/viewmodel">`` element of
       ``jacoco.xml``, not the bundle -- and it is ``LINE missed=0
       covered=175`` and ``BRANCH missed=0 covered=40``, so 100% against the
       live 0.80 line gate that had been inert until this unit.
       ``bash scripts/build.sh``: ``11/11 stages OK in 123 seconds. BUILD
       OK``, with ``ok cometgui-ui line 100.0% (176/176) branch 100.0%
       (40/40) [view-model >=80% line]`` and Spotless and Checkstyle each
       reporting ``24 ordinary + 0 derived = 24 file(s) on disk``.
       **Falsification of my own**, distinct from the agent's three: flipping
       ``CONSOLE`` from primary to secondary in ``SectionId`` -- one boolean,
       quietly demoting a section the Information Architecture names -- fails
       three assertions, ``expected: <8> but was: <7>``,
       ``expected: <[RUN, ... PROVENANCE, CONSOLE]> but was: <[RUN, ...
       PROVENANCE]>`` and ``expected: <[TOOL_MANAGER, SETTINGS]> but was:
       <[CONSOLE, TOOL_MANAGER, SETTINGS]>``. The eight primary sections are
       therefore pinned by test, not by intent. Contracts the view unit
       inherits, recorded here because unit 6 must honour them: the ten stable
       ids are ``run comet-parameters percolator results visualisation
       limelight provenance console`` then ``tool-manager settings``;
       navigation does **not** wrap and ``selectNext``/``selectPrevious``
       return ``false`` at the ends; every published property is read-only, so
       a view listens and calls ``select(...)`` rather than binding
       bidirectionally, and must filter the ``null`` a cleared selection model
       reports; ``ConsoleViewModel.refresh()`` is the only thing that moves
       messages into the view and the view must call it on the FX thread.

   * - 6
     - **Views and controls.** The shell, the eight section panes, the
       console pane (second derived file) and the stage stepper control;
       stable identifiers for every control a test needs and an accessible
       name on every control that exists.
     - ``R-TEST-04``, gates 1, 4
     - **SIGNED OFF 2026-08-30**, commits ``40a696a`` and ``0d42ef5``. Diff
       read: seventeen files, every one under ``cometgui-ui/src/``, no gate
       configuration touched. **The second derived file landed in the derived
       file set, and I checked that rather than believing it**:
       ``mvn -pl cometgui-ui checkstyle:check@checkstyle-check-derived``
       reports ``0 Checkstyle violations``,
       ``spotless:check@spotless-check-derived`` reports
       ``Spotless.Java is keeping 2 files clean``, and ``scripts/build.sh``'s
       census prints ``ok cometgui-ui Spotless 37 ordinary + 2 derived = 39
       file(s) on disk`` and the same for Checkstyle -- so ``ConsolePane`` and
       its ``package-info`` are checked by the derived regime and not by the
       ordinary one. Their header is byte-identical to
       ``config/license/java-header-derived.txt`` and each carries the
       per-file record naming
       ``src/main/java/org/casanovo/gui/ui/ConsoleView.java`` at commit
       ``480b3013e7f8fb51a2b8c58681043821e3e7f865``.
       ``bash scripts/build.sh``: ``11/11 stages OK in 137 seconds. BUILD
       OK``, ``34 report file(s): tests=623 failures=0 errors=0 skipped=0``,
       ``ok 8 architecture rule(s) checked, 0 failures``,
       ``ok cometgui-ui 65 class(es) analysed, 0 findings``. The view-model
       package is still at ``LINE missed=0 covered=175`` /
       ``BRANCH missed=0 covered=40``, read from ``jacoco.xml`` -- this unit
       did not dilute the gated package.
       **Two falsifications of my own**, in a ``git archive`` sandbox.
       (a) Skipping ``SectionId.CONSOLE`` when the content area is built --
       one section quietly absent -- turns all 13 ``ShellViewTest`` tests into
       errors. (b) The subtler one: changing
       ``pane.getValue().setVisible(isSelected)`` to ``setVisible(true)``, so
       every section renders at once instead of only the selected one, gives
       four named failures including
       ``theContentAreaShowsExactlyTheSelectedPane ... visibility of
       comet-parameters ==> expected: <false> but was: <true>`` and
       ``arrowKeysWalkThroughEverySectionIncludingTheSecondaryOnes ...
       visibility of run ==> expected: <false> but was: <true>``. The tests
       are about the rendered scene graph, not about construction.
       **Escalated upward, not decided here**: the ``Settings`` section has no
       owning phase anywhere in ``phases/index.rst`` or
       ``specification.rst``. The agent declined to guess a number and wrote a
       note saying so, pinned by a test. See *Blockers escalated* below.
       **Recorded for later phases**: ``#navigation`` is a ``VBox`` and is
       deliberately NOT focus-traversable -- only the selected entry is, a
       roving tab stop -- so a test must assert traversability on the selected
       entry, never on the container; and
       ``mvn -pl <module> spotless:apply@spotless-check-derived`` does apply
       the derived header, which is a useful thing the next agent to add a
       derived file should know.

   * - 7
     - **Application bootstrap.** ``CometGuiApplication``, AtlantaFX applied,
       the composition root wiring every seam, and the host-baseline result
       reported at startup rather than discovered by a later crash.
     - ``R-PLAT-01``, ``R-PROC-01``
     - **SIGNED OFF 2026-08-30**, commit ``b7a3b8f``. Diff read; every path is
       under ``cometgui-app/``, and the POM diff adds only the headless test
       recipe -- no ``skip``, no ``exclude``, no ``failIf`` appears anywhere in
       it (I grepped the added lines for those words and got nothing).
       ``bash scripts/build.sh``: ``11/11 stages OK in 155 seconds. BUILD OK``,
       ``42 report file(s): tests=677 failures=0 errors=0 skipped=0``,
       ``ok cometgui-app 28 class(es) analysed, 0 findings``,
       ``ok 106 classes imported from org.cometgui`` (up from 75 -- the
       ArchUnit census is growing with the product, not standing still), and
       ``ok 8 architecture rule(s) checked, 0 failures``. The derived-file
       count for ``cometgui-app`` is still ``25 ordinary + 2 derived = 27``,
       so this unit added no derived file and did not disturb the two that
       exist. The FFM probe's own evidence file,
       ``cometgui-app/target/glibc-probe.txt``, reads
       ``symbol gnu_get_libc_version text 2.36 parsed 2.36.0`` -- a real
       native call, no subprocess, matching what I measured by hand before the
       unit was written.
       **I started the application myself**, outside any test: built a class
       path from the twelve ``target/classes`` directories plus
       ``atlantafx-base-2.1.0.jar``, and ran
       ``org.cometgui.app.bootstrap.CometGuiLauncher`` under the Monocle
       headless recipe with the project-local font stack. It ran for the full
       25-second timeout with **no output on stdout or stderr and no
       exception** -- the JVM had to be killed by the timeout, which is what a
       showing window does. On its own that is only "it did not crash"; it is
       recorded because it is independent of the test harness, and the scene
       contents are asserted by the smoke test.
       **Falsification of my own**: raising ``STARTUP_GLIBC_FLOOR`` from
       ``2.14.0`` to ``9.99.0`` -- so this host's real 2.36 no longer meets it
       -- fails six tests across the two bootstrap test classes, with
       ``Cannot continue: This host has glibc 2.36, but the selected tools
       require glibc 9.99.0 or newer. Select tool versions built for an older
       glibc, or run CometGUI on a distribution with glibc 9.99.0 or newer.``
       That is R-PLAT-01 verified at startup against a real probe, and the
       diagnostic names the host's version and the required version as
       ``R-PLAT-03`` asks.
       **Note for a sandboxed re-run**: ``git archive`` does not carry
       ``tools/`` (it is gitignored), so a sandbox must symlink
       ``/workspace/tools`` before the JavaFX tests can run; without it they
       fail loudly with ``the project-local font stack is missing ... Run:
       bash scripts/fetch-fontstack.sh``, which is the designed behaviour, not
       a defect.
       **Accepted judgement calls, recorded**: the three seams with no
       implementation yet (``ProcessRunner``, ``HashService``, ``Downloader``)
       are modelled as ``Optional`` accessors plus ``require*`` forms that
       throw naming the owning phase, rather than as fakes or null fields --
       a no-op hash service would let Phase 04 build a provenance record out
       of fiction and stay green. The shared ``BoundedMessageLog`` is injected
       into ``CometGuiApplication`` rather than held by the composition root,
       because SpotBugs is right that a composition root handing out mutable
       shared state is ``EI_EXPOSE_REP``; **Phase 03 must decide where the
       shared log lives** when the process service needs to write to it.

   * - 8
     - **FxUiDriver and the headless GUI suite.** The driver abstraction with
       its TestFX implementation; a test that reaches every section by mouse
       and a test that reaches every section by keyboard alone; a test that
       enumerates every control and fails on a missing accessible name; a
       pane-level console flood test.
     - ``R-TEST-04``, ``AC-TST-05``, gates 1, 2, 4, 5
     - **SIGNED OFF 2026-08-30**, commits ``ca4b22f``, ``fa05644``,
       ``23f10e0``, ``bd8adc3``. Diff read: thirteen files, all under
       ``cometgui-app/src/test/java`` except the two POMs, whose only change
       is the TestFX declaration (test scope in both places) and the AssertJ
       exclusion. I ran the four gate tests myself:
       ``Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`` across
       ``SectionNavigationUiTest`` (4), ``KeyboardOnlyNavigationUiTest`` (4),
       ``AccessibleNameEnumerationUiTest`` (3) and ``ConsoleFloodUiTest`` (5).
       ``bash scripts/build.sh``: ``11/11 stages OK in 173 seconds. BUILD OK``
       with ``46 report file(s): tests=693 failures=0 errors=0 skipped=0``.
       **Four falsifications of my own**, each different from the agent's and
       each run in a ``git archive`` sandbox with ``tools/`` symlinked:
       *gate 1, mouse* -- making every navigation entry select ``RUN``
       (``entry.setOnAction(event -> select(SectionId.RUN))``) fails with
       ``the header's echo of the selected section ==> expected: <Comet
       Parameters> but was: <Run>`` and ``#section-run should not be showing
       while comet-parameters is selected``;
       *gate 1, keyboard* -- making Down move two sections at a time fails
       three tests with ``the walk starts with the focus on the first
       section's entry ==> expected: <nav-run> but was: <nav-percolator>``;
       *gate 2* -- ``SectionPane.setId(null)``, so no pane carries its stable
       identifier, fails the navigation walk under both drivers;
       *gate 4* -- removing the accessible name from the stage-NAME labels
       fails with ``8 of 91 controls have none: Label with id
       #stage-inputs-name under #stage-inputs; ...`` naming every one;
       *gate 5* -- replacing the console's document window with ``int from =
       0`` fails with ``the document must hold exactly its cap of lines, not
       10000 ==> expected: <5000> but was: <10000>`` and with the summary
       string changing to ``Showing 10,000 matching lines.``.
       **A failed injection of my own, recorded because it matters.** My first
       gate-4 attempt nulled the accessible text of the stage *state* labels
       at their construction site and the test still passed -- not a hole in
       the gate, but an ineffective injection: ``StageStepper.showStage()``
       legitimately re-sets that text from the view-model a few lines later.
       I re-injected at a single-assignment site and it failed as it should.
       An injection that does not reach the code is a harness failure, not a
       pass, and this is why the project's harnesses carry
       ``assert_injected``.
       **Judgement calls accepted.** The ``assertj-core`` exclusion is a
       removal, not an acceptance: ``scripts/ci/security/allowlist.json`` is
       still ``"entries": []``, so CVE-2026-24400 was taken out of the
       dependency graph rather than allowlisted, which is the stronger of the
       two options. ``MemoryMXBean.gc()`` in place of ``System.gc()`` is the
       same fix-in-code that unit 2 made for the same SpotBugs rule.
       **For the licence audit**: ``org.testfx:testfx-core`` and
       ``testfx-junit5`` 4.0.18 declare **EUPL 1.1**; test scope, in
       ``cometgui-app`` only, never redistributed.
       **A regression this sign-off found, which is NOT unit 8's**: running
       ``bash scripts/verify-all-gates.sh`` for the first time since Phase 02
       began gives ``8 control(s) passed, 1 failed`` -- the ``tests`` control
       (``scripts/verify-test-gates.sh``) reports ``24 assertion(s) passed, 8
       failed``. Its ArchUnit controls 1-3 still pass, so **gate item 3's
       falsifiability is intact**; what has broken is Phase 01's
       coverage/mutation injections, because Phase 02 grew the tree they were
       sized against. ``cometgui-domain`` now has 301 covered lines, so an
       injected class with ten uncovered lines leaves the ratio at 0.97 and
       the 0.90 gate correctly does not fire; PIT now generates 152 mutations
       there, so weakening one test class leaves the score at 96%. The gates
       are unchanged and still correct -- **the controls no longer reach
       them**. That is exactly the drift Phase 01's own handoff warned about,
       and it is mine to repair: see unit 11. It should have been caught
       earlier: I ran the aggregate harness at unit 8 rather than after unit
       1, and I am recording that as my own process lapse.

   * - 9
     - **``docs/developer/architecture.rst``** describing the layering as
       built, and the traceability map updated to name the new evidence.
     - ``R-DOC-03``
     - **SIGNED OFF 2026-08-30**, commits ``2c62be7`` and ``420e8cf``. The
       page is 1,154 lines over eleven top-level sections, and I read it. It
       describes the tree as built rather than the specification's intent, and
       it is honest where the two differ. ``bash scripts/ci/docs-build.sh``
       run by me: ``build 1 OK -- 51 HTML page(s)``,
       ``build 2 OK -- 39 HTML page(s)``, ``PASSED``, and
       ``docs/_build/html/developer/architecture.html`` is 133,884 bytes on
       disk -- the page exists, not merely the exit code.
       **I re-verified the page's load-bearing claim myself** rather than
       taking it: a class in ``org.cometgui.domain`` importing
       ``javafx.scene.control.Label``, compiled with the module's own settings
       (``javac --release 25``, no dependencies, no ``--add-modules``), exits
       **0** and produces ``Probe.class``. The Liberica *Full* JDK supplies
       ``javafx.*`` as system modules, so the Maven graph cannot forbid it and
       ArchUnit really is the only thing that refuses it. Phase 01's handoff
       surprise 6 says the opposite, and this page corrects it.
       The page also carries the two corrections I asked for after my own
       sign-offs -- why the bundle figure (176/176) and the gated package
       figure (175/175) differ and which one to read, and the
       double-assignment property of accessible text that made one of my
       injections ineffective.
       ``scripts/feasibility/check-docs.sh`` reports two ``unknown document``
       warnings on this page. I checked rather than accepted the explanation:
       that harness builds one file in a throwaway tree with no siblings, so
       an inter-document ``:doc:`` reference cannot resolve there by
       construction, and both targets do resolve in the real gate -- the
       rendered HTML carries ``href="testing.html"`` and
       ``href="../feasibility/gui-automation-spike.html"``. No cross-reference
       was removed to make a check pass.

   * - 10
     - **``scripts/verify-shell-gates.sh``**: for each of the five exit-gate
       items, inject the defect it exists to catch, show the narrowest
       command failing with the expected diagnostic, then show it passing
       once removed; wired into ``scripts/verify-all-gates.sh``.
     - all five gate items
     - **SIGNED OFF 2026-08-31**, commit ``7c09e89``, plus my own ``928c896``.
       Diff read: two files, the new 837-line harness and the ``shell``
       registration in ``verify-all-gates.sh``; nothing else, and no ``.java``,
       POM, ``config/`` or gate threshold anywhere in the range. The script is
       mode ``100755``, which matters because ``verify-all-gates.sh`` refuses
       to run at all if a sub-harness is not executable.
       ``bash scripts/verify-shell-gates.sh`` run by me: **exit 0**,
       ``SUMMARY: 30 control(s) passed, 0 failed, in 159 seconds``,
       ``Every gate rejected its defect and accepted the clean tree.``, over
       eleven controls -- a clean baseline, two for gate item 1 (mouse and
       keyboard), two for item 2, the enforced delegation for item 3, one for
       item 4, two for item 5, and two on the harness itself.
       ``bash scripts/verify-all-gates.sh`` run by me end to end: **exit 0**,
       ``10 control(s) passed, 0 failed, in 699 seconds (11m39s)``, with
       ``PASS shell 02:1,2,4,5 30 158s``. Its summary now reports coverage per
       phase: ``PHASE-01 ... 1,2,3,4,5,6`` and ``PHASE-02 ... 1,2,4,5``, and
       states in the output why item 3 is delegated rather than injected
       twice.
       **The control I most wanted to see, and did**: control 4b makes the
       exact ineffective injection I made by hand during unit 8's sign-off --
       removing the accessible name at the site ``showStage()`` re-assigns --
       and requires the harness to print ``HARNESS FAILURE -- the check PASSED
       with the defect present. Either the gate is dead or the injection never
       reached the running code``. Control H does the same for a defect that
       never reached the sandbox at all, in three forms (missing file,
       unmodified file, vanished anchor). A harness that cannot tell "the gate
       did not bite" from "the injection did not land" is a harness that
       reports the second as the first, and this one is tested not to.
       **Why I did not build a further meta-check of my own**: the two
       strongest ones already exist and I watched both. The aggregate caught a
       genuinely broken sub-harness earlier the same day -- the ``tests``
       control reporting ``24 assertion(s) passed, 8 failed`` -- and the agent
       reported its own first run failing ``29 passed, 1 failed`` on a
       mis-transcribed expected diagnostic, which it corrected rather than
       dropped.
       My own commit ``928c896`` corrects ``docs/developer/testing.rst``,
       which still said "all nine falsifiability harnesses" and omitted
       ``shell`` from the ``--only`` list.
       **Escalated**: ``STATUS.rst`` also records "9 controls" in two places.
       That file is tier 1's and I have not touched it.

   * - 11
     - **Repair ``scripts/verify-test-gates.sh``.** Added after unit 8's
       sign-off, when the first full run of ``scripts/verify-all-gates.sh``
       since the phase began reported ``8 control(s) passed, 1 failed``. Phase
       02 grew ``cometgui-domain`` from 35 covered lines to 301 and from 22
       mutations to 152, so Phase 01's coverage and mutation injections are no
       longer large enough to move either ratio past its threshold, and three
       hard-coded diagnostic strings no longer match. Accepted only when every
       injection is sized from the module as it is, not from a constant; when
       no threshold, rule or exclusion has changed; and when
       ``verify-all-gates.sh`` reports every control passing.
     - gate 3, ``R-TEST-02``, ``AC-TST-02``--``AC-TST-04``
     - **SIGNED OFF 2026-08-30**, commit ``b891ec5``, plus my own ``ca27d6f``.
       Diff read: two files, ``scripts/verify-test-gates.sh`` and the ``tests``
       control's floor and floor-history comment in
       ``scripts/verify-all-gates.sh``. **Nothing was weakened, and I checked
       the ways it could have been**: ``git diff`` over the range touches no
       POM, no ``config/``, no ``.java`` and no ``.rst``; the coverage rule is
       still 0.90 line / 0.85 branch, the view-model rule still 0.80, the PIT
       threshold still 80; no control, assertion or guard was deleted; the
       assertion count went **up**, 32 to 33, and ``GATE_FLOOR`` was raised to
       match rather than left behind. The injections are now sized from the
       module's own reports each run instead of from constants, which is the
       repair that stops this recurring.
       ``bash scripts/verify-all-gates.sh`` run by me end to end: **exit 0**,
       ``9 control(s) passed, 0 failed, in 535 seconds (8m55s)``, with
       ``PASS tests 3, 4 33 366s``. The ``tests`` harness itself reports
       ``SUMMARY: 33 assertion(s) passed, 0 failed`` and ``Every gate rejected
       its defect and accepted the clean tree.``
       I read the gate diagnostics out of ``_build/test-gate-logs/`` rather
       than trusting the summary, and they carry real numbers:
       ``Rule violated for bundle cometgui-domain: lines covered ratio is
       0.81, but expected minimum is 0.90``;
       ``Rule violated for package org.cometgui.ui.viewmodel: lines covered
       ratio is 0.66, but expected minimum is 0.80``;
       ``Generated 231 mutations Killed 152 (66%)`` with
       ``Mutation score of 66 is below threshold of 80``; and the clean
       baseline at ``Generated 152 mutations Killed 152 (100%)``.
       **Why I am satisfied the repaired harness would still notice a gate
       that stopped biting**: that is precisely the assertion form it uses --
       "the gate exited 0 with the defect present" -- and I watched it fire
       for real earlier today, when the un-repaired controls reported
       ``24 assertion(s) passed, 8 failed`` against gates that were themselves
       perfectly healthy. A harness that can be seen distinguishing "the gate
       did not fire" from "the gate fired" is the thing being asked for, and
       it was seen doing exactly that.
       My own commit ``ca27d6f`` corrects the one-line prose in
       ``verify-all-gates.sh`` that still described control 6 as injecting "a
       weakened test suite"; it now injects a covered class whose test asserts
       nothing.

   * - 12
     - **Pin every stable identifier as a literal, exhaustively.** Added after
       the phase reported, when the **main orchestrator found the gap at its
       own sign-off by injection**: renaming one section's pane identifier in
       ``UiIds`` left the whole build green, because the GUI tests compute
       their expected identifier by calling the same helper they are checking,
       and ``UiIdsTest`` pinned literals for only two of the ten sections.
       ``R-TEST-04`` turns on the word *stable*, and a self-referential
       assertion cannot see a rename. Accepted only when every identifier the
       shell exposes is pinned as a hand-written literal, when adding a
       section, stage or severity fails until its literal is pinned, when no
       pinned literal is produced by calling ``UiIds``, and when a rename is
       demonstrated failing and naming the identifier.
     - ``R-TEST-04``, gate 2
     - **SIGNED OFF 2026-08-31**, commit ``5569f6b``. **I reproduced the gap
       myself before briefing the unit**, on a section neither the main
       orchestrator nor the agent used: renaming ``sectionPane(PERCOLATOR)`` to
       ``section-percolator-pane`` left ``UiIdsTest`` 5/0,
       ``SectionNavigationUiTest`` 4/0, ``KeyboardOnlyNavigationUiTest`` 4/0,
       ``AccessibleNameEnumerationUiTest`` 3/0 and ``BUILD SUCCESS``. The cause
       is exactly as reported: the expected value was produced by calling the
       helper under test, and ``UiIdsTest`` pinned literals for two sections
       out of ten.
       Diff read. ``git show --numstat`` is ``9/0``, ``688/0``, ``10/0`` --
       **zero deleted lines in the whole commit**, so no existing assertion
       could have been removed, and ``UiIdsTest``'s five tests still run. The
       nine lines added to ``UiIds.java`` are Javadoc only: filtering the diff
       to non-comment added lines returns nothing, so no identifier value, no
       method body and no signature changed. Nothing outside
       ``cometgui-ui/src/{main,test}/java/org/cometgui/ui/controls/`` was
       touched.
       **I checked the independence claim rather than accepting it**: every
       ``UiIds.*`` call in the new class is on the *actual* side of an
       assertion or inside failure-message text; the expected side is a
       hand-typed literal in one of seven ``Map.ofEntries`` tables, with each
       derived form spelled out in full (``section-run-heading``, not
       ``pane + "-heading"``). 118 distinct identifier literals appear in the
       file.
       **Three falsifications of my own**, in a ``git archive`` sandbox with
       ``tools/`` symlinked, deliberately using tables and sections the agent
       did not: renaming the ``TOOL_MANAGER`` **navigation entry** to
       ``nav-tools`` gives ``the stable identifier of the section TOOL_MANAGER
       navigation entry (UiIds.navigationEntry) is no longer the pinned
       nav-tool-manager ... expected: <nav-tool-manager> but was: <nav-tools>``;
       renaming every stage **state** label suffix from ``-state`` to
       ``-status`` gives the same shape naming ``stage INPUTS state label``.
       And, most directly, **re-injecting the main orchestrator's exact case**
       -- ``sectionPane(RESULTS)`` returning ``section-results-pane`` -- now
       gives ``expected: <section-results> but was: <section-results-pane>``
       and ``BUILD FAILURE``, while ``UiIdsTest`` in the same run is still
       ``Tests run: 5, Failures: 0``. That pair is the proof: the old suite
       genuinely cannot see the rename, and the new one does.
       On the final tree: ``bash scripts/build.sh`` gives ``11/11 stages OK in
       174 seconds. BUILD OK`` with ``45 report file(s): tests=700 failures=0
       errors=0 skipped=0``, and ``bash scripts/verify-all-gates.sh`` exits 0
       with ``10 control(s) passed, 0 failed, in 698 seconds (11m38s)``.
       **Four observations the agent reported and did not fix, which I accept
       as correctly out of its scope** and which the next phase should weigh:
       ``UiIdsTest.noTwoIdentifiersCollide`` asserts a floor of 80 against a
       real surface of 119; ``UiIdsTest.allIdentifiers()`` walks
       ``SectionId.displayOrder()`` rather than ``values()``, so a section
       dropped from the display order would leave that collision check
       silently; ``StageStepper`` draws the arrow into a stage from
       ``predecessors().get(0)`` only, while the pin compares every
       predecessor, so a second edge will fail the pin and force a deliberate
       decision about what the stepper draws; and
       ``consoleSeverityFilter`` derives from ``MessageSeverity.name()``, so
       renaming a **domain** enum constant renames a UI control identifier --
       now pinned, but a real coupling worth knowing about.

Rejections and rework
=====================

No unit was sent back. Two units were **repaired by me after their own agent had
finished**, and both are recorded in the sign-off entries above rather than
hidden: unit 4's machinery needed ``config/checkstyle/checkstyle.xml``'s GROUP 0
comment brought into step (commit ``6c1fdd7``), and my own edit there introduced
a literal ``--`` inside an XML comment, which is illegal and stopped Checkstyle
loading the rule set at all. The census reported ``FATAL: 24 file-set census
check(s) failed`` and I fixed it in the same commit. It is recorded because the
project's rule sets deliberately use single hyphens in prose, and the next
person to edit one will hit the same thing.

One unit was **added mid-phase**: unit 11, after the first full run of
``scripts/verify-all-gates.sh`` since the phase began reported a failing
control.

Deferred
========

* **The ``Settings`` section has no owning phase.** Escalated; see below. The
  pane exists, is reachable, is named and is identified, and says in text that
  no phase claims it.
* **``R-TEST-06``'s release check** -- proving no ``FxUiDriver`` class is in a
  published artefact -- is phase 16's. The drivers are in test sources, so they
  cannot reach ``cometgui-app.jar`` today, but nothing yet *checks* that.
* **The module-wide coverage gate in ``cometgui-workflow``** was deliberately
  left off: it binds phase 08's engine to a number this phase has no authority
  to choose. Measured coverage there is 100% today.
* **Non-Linux verification.** The headless recipe, TestFX, Monocle and the FFM
  glibc probe are unverified on Windows and macOS. Same blocker as Phase 01's
  runner matrix.

Blockers escalated
==================

Four items, all reported to the main orchestrator in this phase's report and in
``handoffs/PHASE-02-handoff.rst``. None blocked any work.

#. **The ``Settings`` section has no owning phase** -- nothing in
   ``phases/index.rst``, the phase documents or ``specification.rst`` claims it.
   The unit that found it declined to guess a phase number and wrote a note
   saying so, pinned by a test. Tier 1 decides whether a phase claims it or the
   index says application settings are out of scope for release 1.
#. **``STATUS.rst`` records "9 controls" in two places**; there are now ten.
   ``STATUS.rst`` is tier 1's file and was not touched.
   ``docs/developer/testing.rst`` said the same thing and I corrected it.
#. **``ONBOARDING.rst`` still calls the specification "revision 7"** (lines 43
   and 87) and ``CLAUDE.md`` still says "revision 2". It is revision 10.
#. **Every phase document still says ``:Status: NOT STARTED``**, including this
   one.
