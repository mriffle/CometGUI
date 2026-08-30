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
     - PENDING

   * - 6
     - **Views and controls.** The shell, the eight section panes, the
       console pane (second derived file) and the stage stepper control;
       stable identifiers for every control a test needs and an accessible
       name on every control that exists.
     - ``R-TEST-04``, gates 1, 4
     - PENDING

   * - 7
     - **Application bootstrap.** ``CometGuiApplication``, AtlantaFX applied,
       the composition root wiring every seam, and the host-baseline result
       reported at startup rather than discovered by a later crash.
     - ``R-PLAT-01``, ``R-PROC-01``
     - PENDING

   * - 8
     - **FxUiDriver and the headless GUI suite.** The driver abstraction with
       its TestFX implementation; a test that reaches every section by mouse
       and a test that reaches every section by keyboard alone; a test that
       enumerates every control and fails on a missing accessible name; a
       pane-level console flood test.
     - ``R-TEST-04``, ``AC-TST-05``, gates 1, 2, 4, 5
     - PENDING

   * - 9
     - **``docs/developer/architecture.rst``** describing the layering as
       built, and the traceability map updated to name the new evidence.
     - ``R-DOC-03``
     - PENDING

   * - 10
     - **``scripts/verify-shell-gates.sh``**: for each of the five exit-gate
       items, inject the defect it exists to catch, show the narrowest
       command failing with the expected diagnostic, then show it passing
       once removed; wired into ``scripts/verify-all-gates.sh``.
     - all five gate items
     - PENDING

Rejections and rework
=====================

None yet.

Deferred
========

Nothing yet.

Blockers escalated
==================

None yet.
