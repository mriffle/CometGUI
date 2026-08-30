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
     - PENDING

   * - 2
     - **Bounded console message model.** ``org.cometgui.domain.log`` with a
       documented cap, discard-oldest, stage tagging and thread safety; a
       flood test appending far more than the cap that asserts the retained
       size, that the *oldest* were the ones discarded, and that retained heap
       stays inside a documented bound.
     - ``R-PROC-03``, gate 5
     - PENDING

   * - 3
     - **Workflow stage and state model.** ``org.cometgui.workflow.state``
       with the specification's nine step states, the stage enumeration
       matching the stepper diagram, and run-state derivation; the module's
       mutation gate switched on because ``org.cometgui.workflow.state.*`` is
       a PIT target package.
     - stage stepper
     - PENDING

   * - 4
     - **Derived-file attribution machinery, and the first derived file.** A
       second Spotless file set and a second Checkstyle execution with their
       own header file, the first narrowed to exclude ``/derived/``; a
       required per-file derivation record; ``scripts/build.sh`` proving every
       ``.java`` file was inspected by exactly one of the two regimes; a
       negative control showing an unattributed derived file is rejected.
     - ``R-SEC-01``, ``D-001``
     - PENDING

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
