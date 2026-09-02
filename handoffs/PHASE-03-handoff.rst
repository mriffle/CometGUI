=============================================
PHASE-03 handoff -- Process Service
=============================================

:Phase: 03
:Agent finished: 2026-09-01
:Outcome: **PARTIAL** -- five of six gate items pass outright; gate item 2
   passes on the reference platform and carries no platform qualifier, and
   descendant termination has never executed on Windows or macOS
:Phase orchestrator: Phase-03 orchestrator subagent (session 03)
:Records: ``handoffs/PHASE-03-worklog.rst`` -- eight work units, each with a
   sign-off entry naming what I ran, what I saw, and the defect I injected into
   **production code** myself to watch the check go red

In one line: **the process service is built, every gate item has been seen to
fail on a defect I chose, and the one thing stopping this being a PASS is that
process trees are the most platform-divergent thing in this project and only one
of the three tier-1 platforms exists here.**

Two units found holes that the tests written for them did not catch, and both
are repaired: cancellation's snapshot ordering was **true only because a race is
always won**, and an assertion on a size plus a sampled element **could not see
a count-preserving corruption**. A third near-miss was mine: an injection that
reached the source but not the compiled class reported a green build, and I was
minutes from recording a hole that does not exist.

.. contents:: Contents
   :depth: 2
   :local:

Why PARTIAL and not PASSED
==========================

The rule, from ``ONBOARDING.rst``: "An unverifiable item is not a passed item,
and a phase resting on one is ``PARTIAL``, not ``PASSED``." The distinction that
decides it is not how many tests are skipped -- **nothing in this phase is
skipped** -- but whether there is *different code* on a platform that has never
run it.

* "We could not run this code on that platform" is a **testing gap**.
* "There is different code on that platform and it has never run" is
  **unverified behaviour**.

**Gate item 2 is the second kind.** It reads, without qualification: "A hanging
process with a child is cancelled and neither process survives." The phase
document's own risk note says why that matters: "Descendant termination differs
sharply across platforms; use ``ProcessHandle`` descendants and **verify per
platform** rather than assuming." Signal delivery, orphan reparenting and the
distinction between a polite terminate and a kill are not the same on Windows,
and none of it has executed there.

Compare gate item 4, which the phase document scopes deliberately -- "work **on
the reference platform**". That one does not cap anything, and it passes.

:ref:`p03-platform` is the list, with what a twin must prove for each entry. It
is a specification for Phase 15, not a discovery left for it to make.

The exit gate, item by item
===========================

Every command below was run by me on the final tree, on a quiet checkout with
``git status --short`` empty. Where I say a check was seen to fail, I injected
that defect into **production** code, watched it fail, reverted it, and
confirmed the revert -- from unit 4 onward by hashing the **compiled class**,
not the source, for the reason in :ref:`the stale-class trap <p03-stale-class>`.

.. list-table::
   :header-rows: 1
   :widths: 5 9 86

   * - Item
     - Result
     - What was run, what it printed, and the defect that makes it go red

   * - 1
     - PASS
     - **Every fake-executable scenario in the specification has a passing test,
       driven through the service.** The eleven scenario phrases are read back
       out of ``specification.rst`` on disk; each covering test is resolved
       reflectively and required to carry ``@Test`` and not ``@Disabled``; each
       method **body** is extracted by a brace matcher and must contain the fake
       scenario's name as a quoted literal; and the covering file must contain
       ``.start(`` and must **not** contain ``new ProcessBuilder`` -- which is
       what excludes ``FakeToolSelfTest``, because proving the *fake* behaves is
       not proving the *service* handles it. The audit found **six genuinely
       uncovered scenarios** and they were added.
       *Seen to fail:* renaming one covering method gives ``no such method in
       org.cometgui.tools.process.FakeScenarioSuiteTest or any of its nested
       classes``.
       *Stated limit:* it cannot detect a scenario added to the specification
       and omitted from the hand-typed list. Nothing can detect an omission from
       a hand-typed list, and its Javadoc says so.

   * - 2
     - PASS **on the reference platform only**
     - **A hanging process with a child is cancelled and neither survives, and
       the assertion is on liveness.** Before cancelling, the test proves it is
       not vacuous: the announced child exists, is running, and is genuinely
       among the parent's ``descendants()``. After cancelling, both
       ``onExit()`` futures must complete within a failure bound and both
       handles must then be dead. The exit code is asserted as **143** and
       explicitly **not 71** -- 71 is the fake's watchdog code, which is what
       this test would see if nothing had killed the process. A **negative
       control** launches the same scenario, cancels nothing, and requires both
       processes to still be alive when a two-second window closes.
       *Seen to fail:* emptying the descendant snapshot so only the parent is
       killed gives ``the child (pid 325499) was still alive 60s after the
       cancellation; isAlive() is true``, plus eleven other failures.
       **Platform residue:** see :ref:`p03-platform`. Exit codes 143 and 137 are
       POSIX; a Windows terminate is understood to be indistinguishable from a
       kill; there is no reparenting and no zombie there.

   * - 3
     - PASS
     - **500 MB of stdout, bounded heap, a complete on-disk log.** Measured and
       printed by the test: ``bytes=524288000 lines=5190971 logBytes=700781582
       floodMillis=11633 heapBefore=8544056 heapAfter=10522240 growth=1978184
       limit=33554432 consoleSize=10000 consoleDiscarded=5180972``. Heap grew
       **1.98 MB against a hand-typed 32 MiB bound**; an unbounded console would
       retain roughly **1.3 GB**, forty times the bound. The line count comes
       from three independent derivations that must agree -- a hand-typed
       literal, arithmetic the test does itself, and the count the fake writes
       on the other stream -- and **every ordinal must equal its own position**.
       *Seen to fail:* rewriting one interior line's ordinal from 3,000,000 to
       2,999,999 -- **count-preserving, console untouched, at a position the
       test does not pin by hand** -- gives ``the ordinals are not a contiguous
       0..5190970 run: position 3000000 should be "0003000000 ..." but was
       "0002999999 ..."``.
       *Cost:* 13.7 s in ``verify``; **+1m53s (27%) in PIT**, measured. Reported
       rather than buffered away; see :ref:`p03-escalations`.

   * - 4
     - PASS
     - **Paths with spaces and non-ASCII characters, on the reference
       platform**, which is how the phase document scopes it.
       ``"café über 日本語 αβγ"`` in the working directory, an argument, an
       output file read back, the stage log directory and file, and the class
       path the fake is launched from.
       *Seen to fail:* I removed the ``<environmentVariables>`` block from
       ``cometgui-process/pom.xml`` and the item fails **loudly** rather than
       silently skipping -- ``sun.jnu.encoding is "ANSI_X3.4-1968", so this JVM
       cannot represent a non-ASCII file name and PHASE-03 exit gate item 4
       cannot be tested at all``, naming the POM block to restore -- plus four
       ``InvalidPathException`` errors on the paths themselves.

   * - 5
     - PASS
     - **An ArchUnit rule confines ``ProcessBuilder`` to the process service and
       fails when it is used elsewhere.** The rule is built **once**, as
       ``ProcessCreationRule.CONFINED_TO_THE_PROCESS_SERVICE``, and both the
       test that grades the product and the test that grades illegal fixtures
       check that same object. ``cometgui-archtests`` prints ``21 tests, 0
       failures``; the census records ``org.cometgui.tools.process 17``, up from
       **1** -- a lone ``package-info`` -- before this phase.
       *Seen to fail:* I wrote ``new ProcessBuilder("percolator", "--help")``
       into ``cometgui-install``'s main sources, confirmed the file by
       ``sha256sum``, and got ``Architecture Violation ... was violated (2
       times): Method <org.cometgui.install.OrchestratorInjection
       .runPercolatorDirectly()> calls constructor <java.lang.ProcessBuilder
       .<init>([Ljava.lang.String;)>``.

   * - 6
     - PASS
     - **No test in this phase uses a fixed sleep to synchronise**, checked
       mechanically rather than by review. ``grep -rn
       "Thread\.sleep\|TimeUnit\.[A-Z_]*\.sleep\|LockSupport\.park"
       cometgui-process/src`` returns nothing, and ``NoFixedSleepScanTest``
       scans every ``.java`` file under this phase's test roots for six named
       forms. **The scan is not exempt from itself.**
       *Seen to fail, against a test its author did not write:* a
       ``Thread.sleep(250)`` inside a **private helper** -- not a ``@Test`` body
       -- of unit 2's ``GuardedListenerTest`` gives
       ``.../GuardedListenerTest.java:181: Thread pause: Thread.sleep(250);``.
       *Seen to fail when it reads nothing:* pointing the scan root at a
       directory that does not exist fires **four independent guards**,
       including ``only 1 files were read, and this phase has at least 20``.

The numbers, from one clean end-to-end run
===========================================

``bash scripts/build.sh`` on a quiet tree, nothing else building:
**``11/11 stages OK in 976 seconds. BUILD OK``**, with
``104 report file(s): tests=1741 failures=0 errors=0 skipped=2``.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Measure
     - Value

   * - ``cometgui-process`` tests
     - **275, 0 failures, 0 errors, 0 skipped**

   * - The two skips in the whole build
     - Both in ``cometgui-provenance``, from Phase 04's paused state. **None in
       this phase.**

   * - Coverage (diagnostic; the specification gives adapters no numeric gate)
     - line **96.6%** (455/471), branch **95.5%** (86/90)

   * - Mutation, as ``scripts/build.sh`` counts it
     - **156/175 = 89.1%** against ``R-TEST-02``'s 80%

   * - Mutation, as PIT's own report counts it
     - **168/175 = 96%**

   * - SpotBugs
     - ``126 class(es) analysed, 0 findings``

   * - Spotless and Checkstyle census
     - ``41 ordinary + 0 derived = 41 file(s) on disk``, both tools

   * - ArchUnit import
     - 179 classes; ``org.cometgui.tools.process`` **17**; 8 rules, 0 failures

``bash scripts/verify-all-gates.sh`` on the same quiet tree:
**``10 control(s) passed, 0 failed, in 1767 seconds (29m27s)``**, with the
``tests`` control at **33 graded assertions** -- the same count as tier 1's
baseline, so the harness floor did not drop.

.. warning::

   **That suite now takes 29m27s where the baseline took 11m58s, and this phase
   is why.** ``verify-test-gates.sh`` control 7 runs ``scripts/build.sh`` inside
   its sandbox, so the 500 MB flood and its PIT cost are paid there as well as in
   the ordinary build. Nothing was weakened to reduce it; see
   :ref:`p03-escalations`.

**One control failed on my first attempt, and the cause was mine.**
``SpecificationScenarioCoverageTest`` reads the eleven scenario phrases back out
of ``specification.rst`` **on disk**, so a hand-typed list cannot silently drift
from the requirement it implements. But ``verify-test-gates.sh`` does not build
its sandbox from ``git archive``: it hand-copies ``pom.xml``, ``.mvn``,
``config``, ``scripts`` and each module's ``pom.xml`` and ``src``, and no project
document. The test failed there, and its failure drowned the expected
diagnostics of two other controls, so one root cause reported as several
failures.

I diagnosed the harness rather than adjusting the test until the harness was
happy, made the minimum safe change inside my own ownership -- a **visible
abort**, never a silent pass, and only in a tree missing every marker of a real
checkout -- and escalated the shared-file edit with the reasoning instead of
making it. Tier 1 added ``specification.rst`` to the sandbox (``e1d750f``) and
recorded the rule: **the sandbox carries what the build reads as input, and a
project document a test asserts against is an input; it does not carry records
or generated output.** The abort branch could then never fire, and a check that
can never fire is not a check, so it was deleted (``c6c9ef4``).

**The two mutation figures differ and both are honest.**
``scripts/build.sh`` counts only ``status='KILLED'``; PIT's own score also
counts ``TIMED_OUT``. The build's number is the stricter one and is the gate.
Separately, **PIT's score varies between runs on the same 175 mutations** -- I
measured 96%, 93% and 89.1% at different moments -- because mutants in the
uninterruptible-wait idiom are classified as timed-out or survived depending on
machine load. **Treat 89.1% as the floor, not 96% as a baseline.**

.. _p03-population:

The population was audited, not just the score
-----------------------------------------------

Tier 1's seventh defect shape is a *real measurement over an incomplete
population*: a class whose test does not compile is absent from the report
rather than scored low, so the average goes **up** and re-running the gate
reproduces the same clean figure. It cannot be caught by verification, only by
auditing that the sample was whole. I audited this module::

    compiled classes (no package-info, no inner): 15
    classes in jacoco.xml:                        15
    COMPILED BUT ABSENT FROM JACOCO: none
    classes PIT mutated:                          14
    COMPILED BUT NEVER MUTATED: org.cometgui.tools.process.RunMessageSink

``RunMessageSink`` is a functional interface with no method body; there is
nothing to mutate. The population is whole.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 32 68

   * - Where
     - What it is

   * - ``ProcessService``
     - The ``ProcessRunner`` implementation and **the only ``ProcessBuilder`` in
       the product**. Argument array only; the working directory checked here so
       the diagnostic names the *directory*; the environment cleared and rebuilt
       from the ``ToolCommand`` so nothing is inherited; standard input closed
       at once; the streams never merged. Covariant return of ``StartedProcess``.

   * - ``StartedProcess`` and ``ProcessTree``
     - Exit code, pid, and start/end/duration from the injected ``Clock``.
       ``onExit`` exactly once and only after **both** pumps are joined.
       Cancellation snapshots descendants **before** destroying anything, kills
       them deepest-first with the parent last, and escalates after a grace --
       all through ``ProcessHandle``, never ``Process.destroy()``.

   * - ``StreamPump`` and ``LineSplitter``
     - One daemon thread per stream, nothing accumulated, a decoder that
       **replaces** malformed input, and a 65,536-character line cap so a tool
       writing hundreds of megabytes without a newline cannot exhaust the heap.
       ``\n``, ``\r\n`` and a lone ``\r`` are all terminators, including a pair
       split across two reads.

   * - ``GuardedListener``
     - A listener that throws cannot kill a pump or lose the rest of a run's log.

   * - ``ProcessRedactor``
     - ``R-SEC-03``'s process side over the **shared** rule set in
       ``org.cometgui.domain.secrets``. Holds no rules of its own.

   * - ``StageRunner``, ``RunningStage``, ``StageOutcome``, ``RunMessageSink``,
       and package-private ``StageRecorder``, ``StageLogFile``,
       ``StageLogFormat``, ``ToolStream``
     - The stage layer: per-stage log files written **as they arrive**, the
       append-only console sink, the optional timeout, and one immutable outcome.

   * - ``cometgui-process/src/test/resources/fakes/FakeTool.java``
     - One Java program with sixteen scenarios, launched through the JDK's own
       ``java`` binary. One implementation, genuinely cross-platform, and it can
       spawn real descendants.

   * - ``cometgui-archtests``
     - ``ProcessCreationRule`` (the rule, once), ``ProcessCreationRuleTest``
       (six violating fixtures, one benign outsider, one positive control), and
       a census that fails if the process package is back on its
       package-info-only floor.

   * - ``docs/developer/tool_adapters.rst``
     - The process-service section, written as built, including the
       platform-divergence table.

   * - ``cometgui-process/pom.xml``
     - Two additions, both this module's own: the PIT mutation gate switched on,
       and surefire forked with ``LANG``/``LC_ALL=C.UTF-8`` without which gate
       item 4 is untestable.

.. _p03-decision:

The decision Phase 02 deferred, and how it was settled
=======================================================

**Where the shared ``BoundedMessageLog`` lives, once the process service writes
to the log the UI reads.**

**Decision: it does not move, and the process service never sees one.**

The process service accepts ``RunMessageSink`` -- a one-method, append-only
functional interface taking a ``LogMessage``. A caller wires
``boundedMessageLog::append``. The rationale, which is the decision:

* **Nothing new is published.** ``ApplicationServices`` is unchanged, so the
  ``EI_EXPOSE_REP`` finding SpotBugs was right about is never created. What
  crosses the boundary is a method reference, not the log.
* **The capability is one-directional.** The process service can append. It
  cannot read the console, cannot ``clear()`` it, cannot learn its capacity or
  discard count. A tool adapter that could empty the user's console is a
  capability nobody asked for.
* **The dependency direction stays legal.** The sink names only
  ``org.cometgui.domain.log.LogMessage``; the process service keeps its single
  ``cometgui-domain`` edge.
* **Thread safety is already paid for.** ``BoundedMessageLog`` synchronises
  every method body on a private monitor, which is exactly what makes
  ``log::append`` safe from two pump threads while the FX thread paints. This
  phase did not re-implement it and did not weaken it.
* **It defers nothing.** The wiring point -- workflow engine to process service
  -- is Phase 08's, and Phase 08 receives a service whose constructor states its
  requirement in its type.

The rejected alternative was a ``BoundedMessageLog`` accessor on
``ApplicationServices``. One line, and it makes every holder of the services
object a potential writer to, and clearer of, the console.

**``cometgui-domain`` and ``cometgui-app`` were not touched.** That this
decision is implementable without either is part of why it was chosen.

.. _p03-holes:

Two holes found by injection, and a third that was my own harness
==================================================================

**1. A property true only because a race is always won.** Unit 2b injected the
classic snapshot-after-destroy bug -- taking ``descendants()`` *after* the parent
is destroyed, which is the exact thing ``ProcessTree``'s design exists to avoid
-- and **all 128 tests passed, including the brand-new real-process gate item 2
test.** ``destroy()`` only queues a ``SIGTERM``, so the snapshot on the next line
still sees the child and still kills it. A test that waited for the race to be
lost would be a test synchronised by a sleep. The repair asserts the order where
it is a **fact rather than a race**, against a ``Process`` whose handle records
what was asked of it. Re-injected, it fails with ``expected: <[descendants,
destroy:200, destroy:100]> but was: <[destroy:100, descendants, destroy:200,
destroy:100]>``.

**2. An assertion too coarse to see a partial failure.** ``onExitComesOnceAndLast``
asserted a size, one interior element and two counts. I replaced line 42 of a
stream with a repeat of line 41 -- **count-preserving** -- and it stayed green.
It now asserts the whole ordered sequence, built by the test from a format
pinned elsewhere with hand-typed literals, and the injection gives
``expected: <[... out 41, out 42, out 43 ...]> but was: <[... out 41, out 41,
out 43 ...]>``.

.. _p03-stale-class:

**3. An injection that reached the source and not the compiled class, which
would have produced a FALSE FINDING.** Deleting the one line that puts a tool's
output into the console sink reported ``Tests run: 239, Failures: 0`` and BUILD
SUCCESS. I was minutes from writing "the console wiring is untested" into the
work log as a hole in the phase. **It is not a hole.** The defect was in the
source -- I read it back -- and absent from the class the tests ran. I caught it
only by hashing the compiled artefact::

    class sha before the injected run: 8b952b15a99d4c3e...
    class sha after  the injected run: 8b952b15a99d4c3e...   <- unchanged
    class sha after re-injecting:      640d1c8e30ee922c...   <- 9 failures

I could not reproduce the skip on demand and **do not claim a mechanism**. A
concurrent agent was building in the same tree at that moment.

**This is the most dangerous form of the trap yet seen in this project.** Every
earlier shape produced a check that could not fail, which leaves an honest
record. This one produces a **false report of a defect that does not exist** --
after which somebody "fixes" working code. The defence is cheap and absolute:
**hash the ``.class``, not the source.**

.. _p03-platform:

Platform divergence: what has only ever run on Linux
=====================================================

Everything was built and measured on Linux/amd64, glibc 2.36, Liberica JDK
25.0.4.1. The full table with reasoning is in the work log and in
``docs/developer/tool_adapters.rst``; in brief, and each phrased as what a twin
must **prove**:

#. **Terminate versus kill.** Whether ``ProcessHandle.destroy()`` and
   ``destroyForcibly()`` are distinguishable at all on Windows. If they are not,
   the termination grace and the whole escalation are dead code there.
#. **Exit codes 143 and 137** are POSIX ``128+signal``. What a cancelled and a
   killed process report elsewhere, and whether either can be told from an
   ordinary tool failure -- a cancelled run that looks like a crash misreports
   the run to the user.
#. **Orphan reparenting.** Descendants-first ordering exists because this
   container's PID 1 does not reap, leaving a permanent zombie whose
   ``isAlive()`` is true for ever. Windows has neither reparenting nor zombies.
#. **The descendant snapshot** itself, and whether it sees a grandchild.
#. **``Process.destroy()`` closing the streams** is an OpenJDK Unix detail, and
   it is the entire reason cancellation goes through the handle.
#. **The cleared environment.** ``R-PROC-04``'s constructed environment is clean
   on Linux (measured ``envcount 0``); many Windows programs will not start
   without ``SystemRoot``. **The entry most likely to need a product change.**
#. **Non-ASCII paths**, which work here only because this module forks its tests
   with a UTF-8 locale.
#. **The shutdown-hook fake**, which probably cannot express "ignores a polite
   terminate" where terminate cannot be caught.
#. **Long paths**, unbounded here and bounded by ``MAX_PATH`` there.

Not on the list, deliberately: the line splitter, the decoder, the bounded log,
redaction and the argument-array rendering are pure logic with no platform
branch. Those are ordinary testing gaps.

.. _p03-surprises:

Surprises a later phase must know
=================================

**0. Hash the compiled class, not the source.** See
:ref:`the stale-class trap <p03-stale-class>`.

**1. ``R-PROC-04``'s cleared environment leaves a JAVA tool unable to decode its
own argv and working directory.** With no ``LANG``, a child JVM reports
``sun.jnu.encoding=ANSI_X3.4-1968`` and a non-ASCII path arrives as replacement
characters -- not only environment *values*, but **arguments and ``user.dir``**.
The service delivers the correct bytes and a native tool such as Comet is
unaffected, because it never decodes them. The fix is the one
``ProcessService``'s Javadoc prescribes: **the caller names ``LANG`` in the
``ToolCommand``**. **Phase 08 and Phase 16 need this.** It is pinned by a test.

**2. ``-Dsun.jnu.encoding=UTF-8`` does not work.** The JVM resolves it from the
OS locale before system properties apply. It must be the environment. Measured
independently by this phase and by tier 1.

**3. PIT strands hanging fake processes on every full build.** A mutant that
breaks cancellation makes a hanging scenario block; PIT kills the minion at its
own timeout, the test's ``finally`` never runs, and the fake is reparented to a
PID 1 that reaps nothing. Twelve accumulated in one session. ``FakeTool``'s
hanging scenarios now halt themselves after 300 s with exit code **71** -- a
code no signal produces, two orders of magnitude beyond any test's bound, and
one the cancellation tests assert against, so it cannot make a cancellation pass
by accident. Observed working: four orphans went to zero with nothing killed by
hand.

**4. A scoped formatter that scopes to nothing formats nothing and exits 0.**
``-DspotlessFiles=".*/archtests/.*[.]java"`` matches no path, because the
directory is ``cometgui-archtests``. **Check the file count Spotless reports,
never the exit code.**

**5. Building ``-pl cometgui-archtests`` without ``-am`` reproduced the Phase 01
vacuous-rule failure by accident.** ``LayeringRulesTest`` reported **8/8 green**
while ``R-PROC-02`` was graded against a stale jar whose process package held
only ``package-info``. The new census assertions are what caught it.

**6. PIT needs a lifecycle phase in front of the goal.**
``mvn -pl cometgui-process org.pitest:pitest-maven:mutationCoverage`` resolves a
stale ``cometgui-domain`` from ``_build/m2repo`` and fails with "tests did not
pass without mutation". Prefix ``test-compile``. This bites **any** phase that
adds a package to ``cometgui-domain``, because ``build.sh`` runs ``clean verify``
and never ``install``.

**7. PIT inherits surefire's ``<environmentVariables>``** via
``parseSurefireConfig``, which defaults to true. **Gate item 4 survives the
mutation run only because of that default.**

**8. A raw NUL byte reached a test source file and git classified the whole
21 KB file as binary.** ``git show`` printed ``Bin 0 -> 21852 bytes`` instead of
a diff, so a reviewer reading that commit would have seen **no test file at
all**. It compiled and passed. Repaired. **A clean ``git show`` is not proof a
commit contains reviewable text.**

**9. Two phase-local ``flock`` files do not serialise two phases.** Both live
phases locked, on different paths, and both built ``cometgui-domain`` through
``-am``. Moot now that work is serial, but it is the shape of the mistake.

.. _p03-escalations:

Escalated to the main orchestrator
==================================

#. **The grade.** I recommend ``PARTIAL`` for the reason at the top. If tier 1
   judges that "verify per platform" is Phase 15's obligation rather than this
   phase's, item 2 is a clean PASS and so is the phase; that is a call above me.

#. **There is no falsifiability harness for this phase.** Phases 01 and 02 each
   have one under ``scripts/``, registered as a control in
   ``verify-all-gates.sh``. ``scripts/`` is on my escalate-before-editing list
   and I had no channel to ask mid-flight, so I did not create one. **Every
   injection in the work log is recorded with its exact command, the file, and
   the exact failure text**, so a ``scripts/verify-process-gates.sh`` can be
   assembled from that record rather than invented. I recommend it before Phase
   08 depends on this service.

#. **The 500 MB test costs PIT +1m53s (27%)**, measured with and without. It
   costs ``verify`` 13.7 s. ``scripts/build.sh``'s ``gates`` stage now takes 692
   seconds. Nothing was buffered or weakened to reduce it; if that is too much
   for every build, the remedy is a build configuration decision, not a change
   to the service.

#. **A fixed sleep in ``cometgui-provenance``.** ``CachingHashServiceTest``'s
   ``awaitSettled`` waits out the file system's one-second mtime granularity.
   That is a genuine fixed delay used to make a cache test deterministic, and
   there may be no event to wait on instead -- but it is Phase 04's to justify,
   and this phase's scan does not cover it. ``cometgui-app`` has two more from
   Phase 02, both backoffs inside deadline-bounded polling loops.

#. **``ProductClasses.TOOLS_ADAPTERS``'s comment was corrected, not its
   constant.** Its Javadoc claimed the pattern excluded the process service;
   ``org.cometgui.tools..`` never did. Narrowing it would have been a weakening.

What is incomplete, and why
===========================

#. **No non-Linux machine has run any of this.** :ref:`p03-platform`.
#. **No real tool has been launched through this service.** Every test drives a
   fake. That is this phase's scope -- "knowing anything about Comet,
   Percolator, PDV or the converter" is explicitly out of scope -- and the
   specification's real-tool tests belong to phases 08 onward.
#. **``ProcessRunner`` is still not wired into ``ApplicationServices``.**
   ``requireProcessRunner()`` still throws naming phase 03. That is deliberate:
   the wiring point is Phase 08's, and this phase did not touch
   ``cometgui-app``.
#. **Four surviving mutations are argued as equivalent** rather than killed: the
   interrupt re-assert in the uninterruptible-wait idiom, a ``join`` removal
   inside a liveness loop, a ``<``/``<=`` reachable only with a cycle in a
   parent chain, and a ``read >= 0`` where the reader cannot return 0. The score
   counts all of them as failures and still clears the threshold.
#. **Descendant termination is proved one level deep.** ``hang-with-child``
   creates one child. Deeper trees are covered only synthetically, in
   ``ProcessTreeTest``, against hand-built handles.

First thing the next agent should do
====================================

**Read** ``docs/developer/tool_adapters.rst``. It is the process service as
built, including what has only ever run on Linux.

**Then, if you are Phase 08** -- the workflow engine, which is this service's
first real customer:

* You hold **one** object, ``StageRunner``, constructed with a
  ``ProcessRunner``, a ``Clock``, a ``ProcessRedactor``, a ``RunMessageSink``
  and a log directory. ``start(stage, command)`` has no timeout;
  ``start(stage, command, timeout)`` has one. You get back a ``RunningStage``
  you can cancel from the UI thread and await from a worker thread.
* **Wire the console as ``boundedMessageLog::append``**, and wire the process
  runner into ``ApplicationServices`` when you do. See :ref:`p03-decision`
  before choosing anything else.
* **Put ``LANG`` in every ``ToolCommand``** whose paths might not be ASCII, and
  put ``PATH``, ``HOME`` and ``TMPDIR`` in any command whose tool needs them.
  The environment is constructed, not inherited, and that is deliberate --
  surprise 1 above is what happens if you forget.
* A stage that is re-run gets ``comet.1.log``, not a truncated ``comet.log``.
  The outcome tells you which file was written.

**And whoever grades this phase:** the injections are all in the work log with
their exact failure text. Re-run them. Hash the compiled class.
