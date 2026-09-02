=============================================================
PHASE-02 handoff -- Application Shell and Navigation
=============================================================

:Phase: 02
:Agent finished: 2026-08-31
:Outcome: **PASSED**
:Phase orchestrator: Phase-02 orchestrator subagent (session 03)
:Records: ``handoffs/PHASE-02-worklog.rst`` -- eleven work units, each with a
   sign-off entry naming what I ran, what I saw, and the defect I injected
   myself to see the check go red

In one line: **all five exit gate items pass, each with a demonstration of its
own failure that anyone can re-run in one command** --
``bash scripts/verify-shell-gates.sh``, 30 controls in 160 seconds -- and the
``D-001`` derivation machinery is built, proved, and carrying two real files
reused from ``Noble-Lab/CasanovoGUI``.

**One gap was found after this phase first reported**, by the main orchestrator,
by injecting a rename into production code: the stable identifiers were asserted
against values computed from the same helper that produced them, so a rename was
invisible. It is repaired and the repair is proved; :ref:`p02-identifier-gap` is
the first thing to read here.

Read :ref:`p02-surprises` before starting Phase 03. Three entries there will
otherwise cost a later phase real time, and one of them is a decision Phase 03
has to make on its first day.

.. contents:: Contents
   :depth: 2
   :local:

What was built
==============

A running JavaFX application with ten navigable sections, an MVVM boundary that
needs no toolkit to test, seven injection seams, a bounded console, a stage
stepper, a headless GUI suite driven through two independent robots, and the
build machinery that makes a file derived from CasanovoGUI keep its attribution
or fail the build.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Where
     - What it is

   * - ``org.cometgui.domain.ports``
     - The seven ``R-PROC-01`` seams. ``java.time.Clock`` is the clock -- there
       is deliberately no wrapper. ``EnvironmentReader``, ``FileSystemAccess``
       and ``RunIdSource`` have real implementations in the composition root;
       ``ProcessRunner``, ``HashService`` and ``Downloader`` are interfaces
       only, and phases 03, 04 and 05 own them. ``ToolCommand`` is a validated
       value type whose ``displayString()`` can never be pasted into a shell.

   * - ``org.cometgui.domain.platform``
     - ``R-PLAT-01``. ``GlibcVersion`` parses and compares; ``HostBaselineVerifier``
       returns one of five named outcomes with a sentence naming the host's own
       value and the required one. The glibc floor is an argument, never a
       constant.

   * - ``org.cometgui.domain.log``
     - ``R-PROC-03``'s in-memory half. ``BoundedMessageLog`` retains the newest
       10,000 messages, discards the oldest, reports how many it discarded, and
       is thread safe because phase 03 will append from reader threads while the
       FX thread paints.

   * - ``org.cometgui.workflow.state``
     - The specification's nine step states, the eight user-facing stepper
       stages with their edges, and the run state derived from them. Not the
       seventeen-step engine DAG, which is phase 08's.

   * - ``org.cometgui.ui.viewmodel``
     - The MVVM boundary: navigation over ten sections, the console with stage
       and severity filters, the stage stepper, the host-baseline banner. No
       ``javafx.scene``, no ``javafx.stage``, no ``javafx.application``, no
       ``Platform.runLater`` -- so every one of its 156 tests runs without a
       toolkit.

   * - ``org.cometgui.ui.view`` and ``.controls``
     - The shell, ten section panes, the stage stepper control, and 119 stable
       identifiers in one class. Every ``Control`` in the running application
       carries an explicit accessible name.

   * - ``org.cometgui.ui.controls.derived.ConsolePane``
     - **Derived** from CasanovoGUI's ``ConsoleView.java``: its coalescing
       flush and its document trimming, with the retained model moved to the
       domain and stage/severity filters added.

   * - ``org.cometgui.app.config.derived.AtlantaFxThemes``
     - **Derived** from CasanovoGUI's ``Themes.java``: the theme set and the
       "theming is optional" contract, with the mutable static state removed.

   * - ``org.cometgui.app.config`` and ``.bootstrap``
     - The composition root -- the only place in the product that calls
       ``System.getenv`` or ``System.getProperty`` -- and the ``Application``
       subclass that verifies the host baseline before it shows the window.
       The glibc probe is a ``java.lang.foreign`` downcall to
       ``gnu_get_libc_version``, not a subprocess.

   * - ``cometgui-app/src/test/java/.../uidriver`` and ``.../gui``
     - ``FxUiDriver`` with two implementations -- TestFX 4.0.18 and a
       TestFX-free ``javafx.scene.robot.Robot`` -- and the four tests that carry
       exit gate items 1, 2, 4 and 5. Both drivers run on every build.

   * - ``config/license/java-header-derived.txt``,
       ``config/checkstyle/checkstyle-derived.xml``, ``pom.xml``
     - ``D-001`` obligation 2 and ``R-SEC-01``, enforced by the build: a second
       Spotless execution and a second Checkstyle execution over
       ``**/derived/**``, with their own header and a required per-file
       derivation record.

   * - ``scripts/build.sh`` (``stage_format``)
     - The census that makes the split honest: the two file sets must be
       exhaustive and disjoint over every ``.java`` file on disk, and
       ``checkstyle-derived.xml`` must stay a superset of ``checkstyle.xml``.

   * - ``scripts/verify-shell-gates.sh``
     - **New.** Proves each of this phase's five gate items fails on the defect
       it exists to catch. 30 controls, 159 seconds.

   * - ``scripts/verify-test-gates.sh``
     - **Repaired.** Its coverage and mutation injections are now sized from
       the module's own reports rather than from constants.

   * - ``docs/developer/architecture.rst``
     - 1,154 lines describing the layering as built, including what this phase
       deliberately did not build.

The exit gate
=============

Every command below was run by me on the final tree. Run them from
``/workspace``, after ``bash scripts/build.sh`` has populated ``_build/``.

.. list-table::
   :header-rows: 1
   :widths: 5 10 85

   * - Item
     - Result
     - Command run, what it printed, and the defect that makes it go red

   * - 1
     - PASS
     - **Starts.** I ran the real application myself, outside any test:
       ``org.cometgui.app.bootstrap.CometGuiLauncher`` on a class path of the
       twelve ``target/classes`` directories plus ``atlantafx-base-2.1.0.jar``,
       under the Monocle headless recipe. It ran to the 25-second timeout with
       no output and no exception. In-test, ``CometGuiApplicationStartupTest``
       asserts ``stage.isShowing()``, ``getTitle()`` = ``CometGUI``, the root's
       id ``shell-root``, and the user-agent stylesheet
       ``/atlantafx/base/theme/primer-light.css``.
       **Mouse:** ``SectionNavigationUiTest`` clicks every ``#nav-<id>`` with a
       robot, through both drivers. **Keyboard alone:**
       ``KeyboardOnlyNavigationUiTest`` reaches every section with key presses
       only -- no ``clickOn`` on its code path.
       *Seen to fail:* making every entry select ``RUN`` gives ``the header's
       echo of the selected section ==> expected: <Comet Parameters> but was:
       <Run>``; making the arrow keys step two sections gives ``#section-comet-
       parameters showing, with comet-parameters chosen ==> expected: <true>
       but was: <false>``. Both are controls 1a and 1b of
       ``scripts/verify-shell-gates.sh``.

   * - 2
     - PASS
     - ``SectionNavigationUiTest`` navigates all ten sections in the launched
       application and asserts each pane by its stable identifier, with the
       other nine not showing. No pixel coordinate and no CSS ancestry appears
       anywhere in the suite (``R-TEST-04``).
       *Seen to fail:* ``SectionPane.setId(null)`` gives ``no node with the
       stable identifier #section-run exists in the running application``;
       leaving the ids but showing every pane gives ``#section-comet-parameters
       should not be showing while run is selected``. Controls 2a and 2b.
       **Repaired after the phase first reported**: the identifiers were
       *present* but not *stable* -- see :ref:`p02-identifier-gap`.
       ``StableIdentifierPinTest`` now pins all 119 as hand-typed literals.

   * - 3
     - PASS
     - ``LayeringRulesTest.domainDoesNotDependOnJavaFx``, checked against an
       import whose size and per-module composition ``ClassImportCensusTest``
       asserts first. ``bash scripts/build.sh`` prints ``ok 8 architecture
       rule(s) checked, 0 failures`` and ``ok 104 classes imported from
       org.cometgui``.
       *Seen to fail:* ``scripts/verify-test-gates.sh`` control 1 injects a
       ``javafx.scene.control.Label`` into the domain and requires ``Architecture
       Violation ... Rule 'no classes that reside in a package
       'org.cometgui.domain..' should depend on ... 'javafx..'' was violated``.
       **This rule is the first line of defence, not the second**: I compiled a
       domain class importing ``javafx.scene.control.Label`` with the module's
       own settings (``javac --release 25``, no dependencies) and it **exits 0**.
       The Liberica *Full* JDK supplies JavaFX as system modules, so the Maven
       graph cannot forbid it. Phase 01's handoff said the opposite; it was
       wrong, and ``docs/developer/architecture.rst`` now records the
       experiment.

   * - 4
     - PASS
     - ``AccessibleNameEnumerationUiTest`` walks the whole scene graph of the
       launched application after ``applyCss()`` and ``layout()``, finds **91
       Controls**, and requires a non-blank ``getAccessibleText()`` on every
       one -- including the ``ScrollPane`` and two ``ScrollBar``\ s a
       ``TextArea``'s skin builds. It asserts a floor on the count too, so a
       walk that found three controls could not pass.
       *Seen to fail:* removing the accessible name from the stage-name labels
       gives ``8 of 91 controls have none: Label with id #stage-inputs-name
       under #stage-inputs; ...``, naming every one. Control 4a.

   * - 5
     - PASS
     - ``ConsoleFloodUiTest`` floods the running application's console with
       **250,000** messages from a producer thread, then refreshes on the FX
       thread. Asserted: the model holds exactly 10,000; ``discardedCount()``
       is 240,001; the retained window is the contiguous sequence ``flood line
       00240000``--``00249999``, so the **oldest** were the ones dropped; the
       document holds exactly 5,000 lines; the summary reads ``240,001 earlier
       lines discarded. Showing the newest 5,000 of 10,000 matching lines.``;
       250,000 refresh requests coalesced into **5** flushes; and retained heap
       growth was **8,595,680 bytes** against a documented 20 MB bound.
       *Seen to fail:* removing the rendered-document window gives ``the
       document must hold exactly its cap of lines, not 10000 ==> expected:
       <5000> but was: <10000>``; removing the eviction from
       ``BoundedMessageLog.append`` gives ``retained heap growth over the flood
       was 37779624 bytes (36 MB), over the documented bound of 20971520 bytes
       (20 MB)``. Controls 5a and 5b.

**Falsifiability, aggregated.** ``bash scripts/verify-all-gates.sh`` exits 0
with **10 control(s) passed, 0 failed, in 687 seconds (11m27s)** -- 176 graded
checks -- and its summary now reports coverage per phase: ``PHASE-01 ...
1,2,3,4,5,6`` and ``PHASE-02 ... 1,2,4,5``, with item 3 delegated to the
``tests`` control and the delegation itself asserted.

``D-001`` and ``R-SEC-01``: what was built and what proves it
=============================================================

``R-SEC-01`` was amended in specification revision 10 while this phase was
reading in: reuse is permitted, and every derived file must retain its upstream
notices and record its derivation, **enforced by the build rather than by
review**.

The convention is mechanical: **a file is derived if and only if its path
contains a ``/derived/`` segment.** Two files are, today:

* ``org.cometgui.app.config.derived.AtlantaFxThemes``, from
  ``src/main/java/org/casanovo/gui/ui/Themes.java``
* ``org.cometgui.ui.controls.derived.ConsolePane``, from
  ``src/main/java/org/casanovo/gui/ui/ConsoleView.java``

both at ``Noble-Lab/CasanovoGUI`` commit
``480b3013e7f8fb51a2b8c58681043821e3e7f865``, plus a ``package-info.java`` in
each derived package.

**A verified fact the attribution rests on:** no CasanovoGUI source file carries
a per-file copyright notice. Every one of its 83 ``.java`` files begins with its
``package`` statement, and ``grep -rl Copyright --include='*.java' src`` in a
clone matches nothing. The derived header therefore attributes collectively --
"Copyright (C) the CasanovoGUI authors" -- and says in the header itself that
upstream carries no per-file notice, so a reader cannot mistake it for a notice
that was dropped.

The machinery, and why each half exists:

* ``config/license/java-header-derived.txt`` -- the fixed block. It keeps the
  ``D-009`` line ``Copyright (C) 2026 The CometGUI authors.`` verbatim and adds
  the upstream attribution.
* A **second Spotless execution**, ``spotless-check-derived``, over exactly the
  paths the first one excludes, carrying google-java-format as well as the
  derived header. It is an execution and not a ``<formats><format>`` block
  because a generic ``<format>`` cannot run google-java-format -- derived files
  would have kept a header check and silently lost the formatter.
* A **second Checkstyle execution**, ``checkstyle-check-derived``, against
  ``config/checkstyle/checkstyle-derived.xml``, which contains every module of
  ``checkstyle.xml`` plus a ``Regexp`` rule requiring the per-file derivation
  record. Neither rule set has a suppression filter of any kind.
* **The census in** ``scripts/build.sh`` -- the part that makes the exclusion
  honest. It requires the two Spotless indexes and the two Checkstyle result
  files together to cover every ``.java`` file on disk, exactly once, per
  module, and it fails with ``UNCHECKED``, ``OVERLAP``, ``PHANTOM`` or
  ``MISMATCH`` rather than a count. It also requires
  ``checkstyle-derived.xml`` to stay a superset of ``checkstyle.xml``.

**The negative controls**, in ``scripts/verify-quality-gates.sh`` (controls
6-12, part of its 42): a derived file with the ordinary header; a derived file
with no derivation record; a **non**-derived file claiming a derivation it did
not make; a badly formatted derived file, which proves google-java-format still
runs on that set; a derived file that neither file set matches, which a green
Maven build checks with nothing and the census catches; and a module deleted
from the derived rule set.

I injected two of my own at sign-off. Deleting the derivation record from the
**real** ``AtlantaFxThemes.java`` gives ``Required pattern 'the derivation
record ...' missing in file. [Regexp]``. And deleting the whole
``checkstyle-check-derived`` execution from ``pom.xml`` -- the move someone
would actually make to get a stubborn derived file through -- leaves
``mvn clean validate`` **green, exit 0**, and the census catches it:
``UNCHECKED cometgui-app: Checkstyle inspected NEITHER file set for:
.../derived/AtlantaFxThemes.java``, then ``FATAL: 2 file-set census check(s)
failed``.

``D-009`` is untouched. Every ``.java`` file in the repository, derived or not,
carries exactly ``Copyright (C) 2026 The CometGUI authors.``

.. _p02-identifier-gap:

The identifier gap, found at sign-off by injection
==================================================

**This is the most useful thing in this handoff for the phases that follow, and
it was not found by the phase that wrote the code.** It was found by the main
orchestrator at sign-off, by injection, after this phase had reported.

**The injection.** ``UiIds.sectionPane(SectionId)`` was made to return
``"section-results-pane"`` for ``RESULTS`` only -- a valid, stable, non-null
identifier, simply a different one. Nothing noticed:
``SectionNavigationUiTest`` 4/0, ``KeyboardOnlyNavigationUiTest`` passed,
``UiIdsTest`` 5/0, ``bash scripts/build.sh`` ``11/11 stages OK. BUILD OK``. I
reproduced it independently on ``PERCOLATOR``, with
``AccessibleNameEnumerationUiTest`` green as well.

**Why nothing noticed**, and both halves mattered:

#. **The GUI assertions were self-referential.** They computed the identifier
   they expected by calling ``UiIds.sectionPane(section)`` -- the helper under
   test -- so a rename moved the expectation and the value together. Such a
   test proves the identifier *exists* and that navigation *works*. It cannot
   prove *stability*, and stability is the entire content of ``R-TEST-04``.
#. **``UiIdsTest`` pinned literals only as a sample** -- two sections out of
   ten, two stages out of eight. Eight sections, including ``RESULTS``, could
   be renamed freely.

**The repair** (work unit 12, commit ``5569f6b``):
``cometgui-ui/src/test/java/org/cometgui/ui/controls/StableIdentifierPinTest.java``
writes out **all 119 identifiers as hand-typed literals** -- 22 constants, ten
sections times five, eight stages times three, eight console stage filters,
seven stepper arrows, two branches times two, four severity filters. Nothing on
the expected side is produced by calling ``UiIds``, ``SectionId.id()``,
``WorkflowStage.id()`` or ``MessageSeverity.name()``; every derived form is
spelled out in full, so ``section-run-heading`` is typed rather than computed.

It also fails on an **addition**, which is what stops the hole being rebuilt one
enum constant later: each table's key set must equal the full ``values()`` of
its enumeration, and ``UiIds``'s ``public static final String`` fields are
enumerated **reflectively** and each required to be pinned. So a new section, a
new stage, a new severity or a new constant fails the build until someone writes
its literal down.

**Evidence it bites.** The main orchestrator's exact injection now gives
``expected: <section-results> but was: <section-results-pane>`` and ``BUILD
FAILURE`` -- while ``UiIdsTest`` in the same run is still ``Tests run: 5,
Failures: 0``, which is the clearest possible statement of what was missing. I
added two more against tables and sections nobody had used: renaming the
``TOOL_MANAGER`` navigation entry, and renaming every stage state label's
suffix. The unit's own agent proved the other three modes -- an unpinned enum
constant, an unpinned ``UiIds`` constant, and a collision.

**What a later phase must take from this.** Adding a control means adding its
literal to ``StableIdentifierPinTest``; the build will insist. And changing an
identifier is now a deliberate, reviewable act rather than an invisible one --
which is the point, because Phase 07's parameter-editor tests and Phase 14's GUI
suite will look controls up by these strings.

**And the general lesson, which is not about identifiers at all:** an assertion
whose expected value is computed by the code under test cannot fail. This one
survived a phase's worth of sign-offs, including mine, because everything it
touched was green. It took an injection to find. Inject into the *production*
code, not only into the harness.

.. _p02-incomplete:

What is incomplete, and why
===========================

#. **The ``Settings`` section has no owning phase.** It is one of the two
   secondary sections the specification's Information Architecture allows
   ("Tool Manager and application Settings may be secondary navigation or
   dialogs"), but nothing in ``phases/index.rst``, the phase documents or
   ``specification.rst`` claims it. Rather than guess a phase number, the pane
   says so in text and a test pins that string. **This is escalated to the main
   orchestrator**; see :ref:`p02-escalations`.

#. **Three of the seven injection seams have no implementation.**
   ``ProcessRunner`` (phase 03), ``HashService`` (phase 04) and ``Downloader``
   (phase 05) are interfaces only. The composition root exposes each as an
   ``Optional`` **and** as a ``require*`` accessor that throws naming the owning
   phase -- deliberately not a fake, because a no-op hash service would let
   phase 04 build a provenance record out of fiction and stay green.

#. **The glibc probe's non-Linux branches are unexercised.** The
   ``java.lang.foreign`` downcall to ``gnu_get_libc_version`` returns ``2.36``
   on this host, and the symbol-absent, symbol-null and lookup-throws paths are
   driven through an injected ``SymbolLookup``. What no machine here can show is
   a real musl, macOS or Windows host.

#. **No non-Linux machine has run any of this.** The headless recipe is
   Linux/amd64 and its ``LD_LIBRARY_PATH`` names ``x86_64-linux-gnu``. TestFX,
   Monocle and the FFM probe are unverified on Windows and macOS, exactly as
   Phase 00 recorded.

#. **``R-TEST-06``'s release check is not written.** Nothing yet verifies that
   the shipped artefact contains no ``org.cometgui.app.uidriver`` class. The
   drivers are in test sources, so they cannot reach ``cometgui-app.jar``
   today, but the *check* belongs to phase 16.

#. **The section panes are empty on purpose.** Each names the phase that fills
   it. That is the phase's scope, not a shortfall -- but a later phase must not
   mistake a pane that exists for a feature that works.

Decisions encountered
=====================

No ``D-`` item was answered by this phase. Judgement calls that were mine, and
that a later phase may overrule:

AtlantaFX is referenced only from ``cometgui-app``
    ``LayeringRulesTest.UI_MAY_DEPEND_ON`` allows ``org.cometgui.ui..`` the JDK,
    JavaFX and the CometGUI modules and nothing else. Keeping the theme in the
    bootstrap module meant the phase added **no exemption to an architecture
    rule**. A later phase that wants a theme picker in Settings will have to
    either widen that allowlist deliberately or pass the theme in as data.

The mutation gate is on in ``cometgui-workflow``; the coverage gate is not
    ``org.cometgui.workflow.state.*`` is a PIT target package, so the drift
    guard requires it. The module-wide 90/85 coverage rule was **deliberately
    not** switched on, because it would bind phase 08's engine to a number this
    phase has no authority to choose. Measured coverage there is 100% today.

A failed optional downstream stage does not fail the run
    ``RunState`` derives ``FAILED`` and ``CANCELLED`` from the **core** stages
    only, so a PDV or Limelight failure leaves a completed search reported as
    ``SUCCEEDED`` with that stage still ``FAILED``. Documented and tested both
    ways.

The console's two caps are reported separately
    The model retains 10,000 messages; the rendered document holds 5,000 lines.
    The summary states both, because "12,431 earlier lines discarded" and
    "showing the newest 5,000 of 10,000" are different facts and collapsing them
    would misreport what the user is looking at.

``assertj-core`` was removed, not allowlisted
    TestFX drags in ``org.assertj:assertj-core:3.13.2``, which carries
    ``CVE-2026-24400``; the supply-chain stage failed the build on it,
    correctly. It is excluded on both TestFX coordinates.
    ``scripts/ci/security/allowlist.json`` is still ``"entries": []``.

.. _p02-escalations:

Escalated to the main orchestrator
==================================

#. **The ``Settings`` section has no owning phase.** Either a phase document
   should claim it, or ``phases/index.rst`` should say plainly that application
   settings are out of scope for release 1. Cost of leaving it: a navigable
   section whose pane can never stop saying "no phase claims this".

#. **``STATUS.rst`` records "9 controls" and "9 control(s) passed" in two
   places.** There are now ten. ``STATUS.rst`` is tier 1's file and I have not
   touched it. (``docs/developer/testing.rst`` said the same thing and I
   corrected it, in ``928c896``.)

#. **``ONBOARDING.rst`` still calls ``specification.rst`` "revision 7" at lines
   43 and 87, and ``CLAUDE.md`` still says "revision 2".** It is revision 10.
   Reported by Phase 01 as well; both files are tier 1's.

#. **Every phase document still says ``:Status: NOT STARTED``**, including this
   one.

.. _p02-surprises:

Surprises
=========

**0. An assertion whose expected value is computed by the code under test
cannot fail.** The whole GUI suite asserted identifiers it obtained by calling
``UiIds`` itself, and every one of them was green while a control's identifier
was renamed underneath it. Found at sign-off by injection, after the phase had
reported; repaired in unit 12. :ref:`p02-identifier-gap` has the detail, and it
is the first thing to read in this document.

**1. An injection that reaches the file but not the behaviour is a harness
failure, not a pass -- and it happened to me.** Proving gate item 4, I removed
the accessible name from ``StageStepper``'s stage-*state* labels and the test
still passed. The gate was fine; ``showStage()`` legitimately re-assigns that
text from the view-model a few lines later, so my injection was overwritten. I
re-injected at a single-assignment site and it failed correctly, naming all
eight controls. ``scripts/verify-shell-gates.sh`` control 4b now makes exactly
that ineffective injection on purpose and requires the harness to say ``HARNESS
FAILURE -- the check PASSED with the defect present``. **Any control whose
defect is assigned in more than one place needs every assignment considered.**

**2. A falsifiability harness can be outgrown, silently, by the code it
protects.** ``scripts/verify-test-gates.sh`` was written when
``cometgui-domain`` had 35 covered lines and 22 mutations. By the end of this
phase it had 301 and 152, so an injected class with ten uncovered lines left the
module at 0.97 against a 0.90 gate, and weakening one test class left the
mutation score at 96% against a threshold of 80. **The gates were perfectly
healthy; the controls no longer reached them**, and the harness correctly
reported that as a failure. Unit 11 re-sized every injection from the module's
own reports at run time, so it scales with the tree. **Run
``bash scripts/verify-all-gates.sh`` at the START of a phase as well as the
end** -- I ran it late and lost time to it.

**3. Maven merges plugin configuration into executions, and the surprise is
which half leaks.** Adding a second Spotless execution for derived files, the
child's ``<includes>`` wins outright -- lists are not appended when the child
declares the element -- but the parent's ``<excludes>`` is **inherited**,
because the execution declares none. That exclusion is ``**/derived/**``, so
without ``combine.self="override"`` the derived execution would have included
the derived files and then excluded every one of them, **formatting and
header-checking nothing while exiting 0**. Observed in
``mvn help:effective-pom``, not guessed. The same trap will bite any future
second execution of a plugin whose managed configuration carries a list.

**4. Spotless's incremental index is per-module, not per-execution.** Two
Spotless executions in one module both write ``target/spotless-index``, and the
second overwrites the first -- destroying the evidence ``scripts/build.sh``
reads. ``<upToDateChecking><indexFile>`` fixes it. Checkstyle has the same
problem with ``<outputFile>`` and ``<cacheFile>``.

**5. ``java.lang.foreign`` reads the glibc version with no subprocess.**
``Linker.nativeLinker().defaultLookup().find("gnu_get_libc_version")`` works on
this host and returns ``2.36``. That matters because ``R-PROC-02`` confines
``ProcessBuilder`` to the process service, which does not exist until phase 03,
so a ``ldd --version`` probe was not available. It needs
``--enable-native-access=ALL-UNNAMED`` to avoid a JDK 25 restricted-method
warning; **phase 16 must put that in the ``jpackage`` java-options**.

**6. ``Clipboard.getSystemClipboard()`` works under Monocle Headless.** Verified
by a throwaway probe: a string round-tripped. It is still isolated in a one-line
method and no test depends on it.

**7. A ``git archive`` sandbox has no ``tools/``.** It is gitignored, so every
JavaFX test in a sandbox fails with ``the project-local font stack is missing
... Run: bash scripts/fetch-fontstack.sh`` until ``/workspace/tools`` is
symlinked in. That failure is the font-stack check working, and mistaking it for
an injected defect would make a whole harness report false positives.

**8. Surefire's ``tests`` attribute under-counts JUnit 5 ``@Nested`` classes.**
A report file can declare ``tests="25"`` while containing 28 ``<testcase>``
elements. ``scripts/build.sh``'s artefacts stage counts the attribute, so its
total is a slight undercount. Harmless today; worth knowing before anyone treats
that number as exact.

**9. ``_build/m2repo`` holds stale ``org.cometgui`` snapshots.**
``scripts/build.sh`` runs ``clean verify`` and never ``install``, so the sibling
jars in the local repository predate this phase. **Always pass ``-am`` to a
``mvn -pl <module>`` command**: without it you compile against an API that no
longer exists, and the error names your own code.

First thing the next agent should do
====================================

**Run ``bash scripts/build.sh`` and then ``bash scripts/verify-all-gates.sh``,
in that order, before writing a line of code.** The first takes under three
minutes and tells you the tree is intact; the second takes twelve and tells you
every gate you are about to be judged by still bites on this machine. Surprise 2
above is why the second one is not optional at the start of a phase.

Then read ``docs/developer/architecture.rst``. It is the map of what exists,
what is deliberately empty, and which phase owns each empty thing.

**Phase 03 specifically** inherits three things from this phase on its first
day:

* ``org.cometgui.domain.ports.ProcessRunner``, ``ProcessListener``,
  ``RunningProcess`` and ``ToolCommand`` are waiting to be implemented. The
  argument-array validation is already done and tested; do not re-litigate it.
* ``org.cometgui.domain.log.BoundedMessageLog`` is the in-memory half of
  ``R-PROC-03``. The file-writing half is yours.
* **A decision this phase deliberately did not make:** where the shared message
  log lives. It is currently injected into ``CometGuiApplication`` rather than
  held by ``ApplicationServices``, because SpotBugs is right that a composition
  root handing out mutable shared state is ``EI_EXPOSE_REP``. When the process
  service needs to write into the same log the UI reads, phase 03 decides how.
