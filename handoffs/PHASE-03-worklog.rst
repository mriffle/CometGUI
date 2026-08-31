=================================================
PHASE-03 work log -- Process Service
=================================================

:Phase: 03
:Phase orchestrator: Phase-03 orchestrator subagent (session 03)
:Started: 2026-08-31

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Baseline observed before any work started
=========================================

``git status`` clean at ``e97d863``.

**The first ``bash scripts/build.sh`` of this session FAILED**, and the failure
was not the tree. ``cometgui-ui`` died in test discovery with
``java.lang.NoClassDefFoundError: org/cometgui/domain/run/StageTag`` while
resolving ``org.cometgui.ui.controls.ConsolePaneTest``, with
``cometgui-domain`` reporting ``Tests run: 256, Failures: 0`` in the same run
and ``cometgui-domain/target/classes/org/cometgui/domain/run/StageTag.class``
present on disk afterwards. ``ps`` showed a second Maven reactor running
``clean verify`` against the same ``-Dmaven.multiModuleProjectDirectory``, plus
a third against ``_build/shell-gate-sandbox``: the main orchestrator's
``verify-all-gates.sh`` baseline was live in the same checkout. Two reactors
running ``clean`` in one tree delete each other's ``target/classes`` mid-build.

Re-run alone once that reactor exited, the same command printed
``11/11 stages OK in 185 seconds. BUILD OK`` with
``45 report file(s): tests=700 failures=0 errors=0 skipped=0``. **That green
run is the tree every unit below starts from.**

That is recorded here because it is a **standing hazard for the rest of this
project**, not a one-off: phase orchestrators are run concurrently in one
checkout, and ``scripts/build.sh`` runs ``clean verify`` from the repository
root. See :ref:`p03-hazard-concurrent-maven`.

.. _p03-hazard-concurrent-maven:

Standing hazard: two Maven reactors in one checkout
---------------------------------------------------

A ``NoClassDefFoundError`` naming a class that exists on disk, or a
``package org.cometgui.x does not exist`` naming your own module, is almost
always a concurrent ``clean``, not a defect. The rules this phase worked under:

#. Never start a root ``mvn`` while another reactor is live -- check
   ``ps aux | grep classworlds.Launcher`` first.
#. Prefer ``mvn -o -pl cometgui-process -am ...`` **without** ``clean`` for
   inner-loop work.
#. ``-am`` is mandatory on every ``-pl`` invocation, because ``build.sh`` runs
   ``clean verify`` and never ``install``, so ``_build/m2repo`` holds stale
   ``org.cometgui`` snapshots (Phase 02 surprise 9).

**The mechanism, established by tier 1 after the fact**, is narrower than "a
gate harness did it": ``scripts/build.sh`` line 217 runs ``mvn ... clean
verify`` **at the repository root, in the working tree**, and both live phase
orchestrators were told to run it before starting. The three Maven gate
harnesses extract ``git archive HEAD`` into a sandbox under ``_build/`` and
build the copy, so none of them can delete a ``target/`` in the working tree;
the 10/10 baseline stands. This phase ran ``build.sh`` once more, to confirm the
tree, and not again while Phase 04 was live.

.. _p03-shared-lock:

**A residual hazard neither phase's lock actually covers, and it is worth
fixing before a third phase runs concurrently.** Phase 04 serialises its Maven
invocations with a ``flock`` on ``p04-maven.lock``, and this phase uses
``_build/cometgui-maven.lock``. **Two different lock files do not serialise two
phases against each other** -- a lock only excludes processes contending for the
same file. And the exposure is real rather than theoretical: Phase 04 builds
``-pl cometgui-provenance -am`` and this phase builds ``-pl cometgui-process
-am``, and *both* of those resolve ``cometgui-domain`` from the reactor and
write ``cometgui-domain/target/``. ``_build/m2repo`` is shared as well. **A
single agreed lock path is needed**; this is escalated to the main
orchestrator rather than decided here, because it binds another phase.

.. _p03-null-idiom:

The null-rejection idiom, arrived at twice independently
---------------------------------------------------------

``config/spotbugs/exclude.xml`` excludes ``NP_NULL_PARAM_DEREF``,
``NP_NULL_PARAM_DEREF_NONVIRTUAL`` and ``NP_NONNULL_PARAM_VIOLATION`` in test
sources -- but **not** ``NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS``. So an
instance method that calls ``Objects.requireNonNull(arg, "name")``, together
with the test proving it rejects null, fails ``mvn verify``. Both live phases
met it. Both refused to add the detector to the shared exclusion file, which
would be weakening a shared gate to make one's own code pass, and both took the
same test-side fix: launder the null through a generic method SpotBugs cannot
see through::

    private static <T> T opaqueNull() {
        return null;
    }

Phase 04 named it ``deliberateNull``. Same signature, same technique, different
word; this phase's name was already committed and signed off in code a live
agent was editing when the divergence came to light. ``config/spotbugs/`` has
not been touched by this phase -- ``git log --name-only`` over the phase's range
lists nothing under ``config`` or ``scripts``.

Baseline supplied by the main orchestrator
------------------------------------------

``bash scripts/verify-all-gates.sh`` -- run by the main orchestrator, not by
this phase -- **10 controls passed, 0 failed, in 718 seconds**: license 5,
workflows 9, docs 1, traceability 8, sbom 8, depscan 16, pipeline 24, quality
42, shell 30, tests 33 graded checks. Every existing gate was seen to reject its
own defect at the new checkout path. It is re-run at the end of this phase.

.. _p03-item5-scope:

What gate item 5 actually asks of this phase
--------------------------------------------

The ``R-PROC-02`` ArchUnit rule **already exists**, shipped by Phase 01 as
``LayeringRulesTest.processCreationIsConfinedToTheProcessService()``, and
``verify-test-gates.sh`` control 2 already injects a ``ProcessBuilder`` outside
the process service and watches it bite. This phase does not re-implement it.

What is new, and what can rot silently, is this: **the rule's protected package
is empty today.** ``org.cometgui.tools.process`` holds one
``package-info.java``, so "nobody outside the process service constructs a
process" is true because nobody anywhere does. Two things become falsifiable
only once this phase puts a real ``ProcessBuilder`` inside that package, and
neither is proved by anything that exists now:

#. The rule must still **fail** for a use outside the package -- re-proved by
   injection *after* the implementation lands, not before.
#. The new classes must actually be **inside** ``ProductClasses.all()``. If
   ``cometgui-process`` were missing from the archtests class path, the rule
   would scan a tree that does not contain this phase's code and report green
   having checked nothing. That is precisely the Phase 01 failure shape -- a
   rule reporting 8/8 while evaluating nothing -- and this is where it would
   recur. Unit 6 therefore asserts named process-service classes are present in
   the import and that the module's census count moved off its
   package-info-only floor.

``cometgui-app``'s ``FfmGlibcVersionSource`` reads glibc through
``java.lang.foreign`` **specifically to avoid a subprocess**, because it
predates this phase. It is not to be "fixed" to use the new service.

Decisions taken by the orchestrator before decomposing
======================================================

These are engineering choices, not ``D-`` items. They are recorded here
because later phases inherit them.

.. _p03-decision-log-ownership:

Where the shared ``BoundedMessageLog`` lives -- the decision Phase 02 deferred
------------------------------------------------------------------------------

**Decision: it does not move, and the process service never sees one.**

Phase 02 left ``org.cometgui.domain.log.BoundedMessageLog`` injected into
``CometGuiApplication`` rather than held by ``ApplicationServices``, because a
composition root that hands out a mutable shared object is publishing mutable
state -- SpotBugs' ``EI_EXPOSE_REP``, and SpotBugs is right. It deferred the
question of what happens when the process service must append to the same log
the UI reads.

The answer taken here is to **narrow the reference rather than move the
object**. The process service declares its own append-only sink type in
``org.cometgui.tools.process`` and accepts that; a caller wires
``boundedMessageLog::append``. Consequences, which are the rationale:

* **Nothing new is published.** ``ApplicationServices`` is unchanged, so the
  ``EI_EXPOSE_REP`` finding is never created. What crosses the boundary is a
  method reference, not the log.
* **The capability is one-directional.** The process service can append. It
  cannot read the console, cannot ``clear()`` it, cannot learn its capacity or
  its discard count. A tool adapter that could empty the user's console is a
  capability nobody asked for.
* **The direction of dependency stays legal.** The sink names only
  ``org.cometgui.domain.log.LogMessage``; the process service keeps its single
  ``cometgui-domain`` edge and gains no dependency on the UI or on the
  composition root.
* **Thread safety is already paid for.** ``BoundedMessageLog`` synchronises
  every method body on a private monitor, which is exactly what makes
  ``log::append`` safe to call from two pump threads while the FX thread
  paints. This phase does not re-implement it and does not weaken it.
* **It defers nothing to a later phase.** The wiring point -- workflow engine
  to process service -- is Phase 08's, and Phase 08 receives a service whose
  constructor states its requirement in its type.

The rejected alternative was to give ``ApplicationServices`` a
``BoundedMessageLog`` accessor. It is one line, and it makes every holder of
the services object a potential writer to, and clearer of, the console.

``cometgui-domain`` is a shared file set in this session and **was not
touched**: this decision is implementable without it, which is part of why it
was chosen.

Redaction is applied where ``R-SEC-03`` names it, and only there
-----------------------------------------------------------------

``R-SEC-03`` requires secrets to be redacted from "command display, process
environment capture and exported reports". This phase's scope line is "a safely
rendered display command with redaction applied". So redaction is applied to
the rendered command and to the captured environment unconditionally, and to
output lines **only when a secret value has actually been registered** -- which
is never for Comet, Percolator or PDV, and is the case Phase 12's Limelight
upload will need. A per-line scan that costs nothing when the registry is empty
can be left switched on honestly; one that costs 500 MB of scanning per run
could not.

The fakes are one program, not a matrix of shell scripts
---------------------------------------------------------

The specification asks for "small test executables". A ``.sh``/``.cmd`` pair
per scenario is two implementations of every fake, and the Windows half would
never be executed on this machine -- an untested half is worse than none. The
fakes are therefore **one Java program**, ``FakeTool``, in
``cometgui-process/src/test/resources/fakes/``, compiled once per test run and
launched as a real external process through the project JDK's own ``java``
binary. One implementation, genuinely cross-platform, and it can spawn real
descendants.

The mutation gate switches ON in this module, because the POM already said so
-----------------------------------------------------------------------------

``pom.xml``'s PIT ``<targetClasses>`` already contains
``org.cometgui.tools.*`` -- written by Phase 01 with the comment "command
builders, version and capability rules, secret redaction in the process
service". ``scripts/build.sh``'s drift guard re-derives those prefixes **from
the POM** and fails any module that compiles a class under one of them without
setting ``<cometgui.mutation.skip>false</cometgui.mutation.skip>``. So the
moment this phase writes its first class, ``cometgui-process`` falls under
``R-TEST-02``'s **80% mutation threshold**, and this phase switches the gate on
in its own module POM rather than discovering it at the end. The JaCoCo
90/85 gate does *not* apply: ``COVERAGE_GATED_PREFIXES`` is domain, params and
provenance, which matches the specification's "adapters covered by real
integration tests rather than artificial line counts".

Consequence for the design: everything that can be pure logic is pure logic in
its own small class -- line splitting, redaction, log-line rendering, the
outcome value -- because a mutation in a thread-timing path is much harder to
kill than a mutation in a function.

Units are run serially
----------------------

Phase 04 is live in this same checkout and the main orchestrator re-runs the
gates in it. Every unit here runs Maven, and :ref:`p03-hazard-concurrent-maven`
is what happens when two reactors overlap. The parallelism this phase could
have had is not worth the false failures it would produce.

Decomposition
=============

.. list-table:: Order, ownership and dependency
   :header-rows: 1
   :widths: 6 42 52

   * - Unit
     - What it owns
     - Why it is here and not earlier

   * - 1
     - ``cometgui-process/src/test/resources/fakes/FakeTool.java``;
       ``src/test/java/.../fakes/`` (the compile-and-launch helper and the
       fakes' own self-test)
     - Every later unit's tests need a real external process to drive. It
       depends on no production code, so it goes first.

   * - 2
     - ``org.cometgui.tools.process`` core: ``ProcessService`` (the
       ``ProcessRunner`` implementation), ``StartedProcess``, the two stream
       pumps, the ``LineSplitter``, decoding, exit code, duration, the
       explicit working directory and environment
     - Needs unit 1 to have anything to launch. ``R-PROC-02``, ``R-PROC-04``.

   * - 2b
     - The adversarial cancellation proofs: a hanging process with a child,
       liveness of **both** after cancellation, and escalation from terminate
       to kill
     - **Split out of unit 2 after unit 1's sign-off**, because gate item 2 is
       one of the two the main orchestrator named as the shortcut a tired agent
       takes, and it deserves a fresh agent whose whole job is to break it. Its
       implementation is unit 2's; its proof is not. Gate item 2.

   * - 3
     - ``org.cometgui.tools.process`` redaction: the secret registry and the
       redactor, plus the redacted command and environment rendering
     - Pure logic, but unit 4 composes it, so it must be signed off first.
       ``R-SEC-03``.

   * - 4
     - ``org.cometgui.tools.process`` stage layer: the append-only message
       sink, the per-stage log file writer, the composed stage runner, the
       optional per-stage timeout, the outcome value
     - Needs units 2 and 3. This is the file-writing half of ``R-PROC-03``.

   * - 5
     - The specification's fake-executable scenario suite: every scenario in
       *Component tests with fake executables*, plus the 500 MB flood and the
       spaces/Unicode paths
     - Needs units 1, 2 and 4. Gate items 1, 3, 4.

   * - 6
     - ``cometgui-archtests``: proof that the existing ``R-PROC-02`` rule now
       has a real subject, still rejects a use outside the package, and is
       evaluated against a class set that actually contains this phase's code
     - The rule already exists; it becomes falsifiable only once unit 2's
       ``ProcessBuilder`` is inside the package. See :ref:`p03-item5-scope`.
       Gate item 5.

   * - 7
     - ``cometgui-process`` test sources: the mechanical no-fixed-sleep scan
       over this phase's own test sources
     - Scans units 1-5's tests, so it goes after them. Gate item 6.

   * - 8
     - ``docs/developer/tool_adapters.rst``
     - Describes the service as built, so it goes last.

Sign-offs
=========

Unit 1 -- the fakes, their harness and their self-test
======================================================

:Agent: fresh phase agent, spawned 2026-08-31
:Commit: ``46129db``, plus the orchestrator's repairs in the sign-off commit
:Outcome: **ACCEPTED after two repairs made by the orchestrator**

**What I ran.** I read the whole diff -- four files, 1,679 lines -- then
``. ./tools/env.sh && mvn -o -B -pl cometgui-process -am test``, which printed
``Tests run: 27, Failures: 0, Errors: 0, Skipped: 1`` for
``FakeToolSelfTest`` and ``Tests run: 256, Failures: 0`` for
``cometgui-domain`` in the same reactor. I ran the four acceptance greps
myself:

* ``grep -rn "Thread\.sleep\|TimeUnit\.[A-Z_]*\.sleep\|LockSupport\.park"
  cometgui-process/src/test`` -- no output.
* ``grep -rn "new ProcessBuilder" cometgui-process/src/test/java`` -- two hits,
  both in ``FakeToolSelfTest.java`` (lines 668 and 692), which is the one file
  the brief permits it in. ``FakeTool.java`` itself has one, in
  ``hang-with-child``, which is the whole point of that scenario.
* ``cometgui-process/target/fake-tools/classes/fakes/FakeTool.class`` -- present,
  13,620 bytes.

**Repair 1: the skipped test, which was gate item 4 quietly going missing.**
The agent reported ``Skipped: 1`` and named the cause honestly rather than
deleting the test: this container has no UTF-8 locale (``locale -a`` offers
``C``, ``C.utf8``, ``POSIX``; ``LANG`` and ``LC_ALL`` are unset), so a JVM
started here reports ``sun.jnu.encoding=ANSI_X3.4-1968`` and cannot represent a
non-ASCII file name at all -- ``Path.of("café")`` throws
``InvalidPathException`` before any product code runs. **An unverifiable gate
item is not a passed gate item**, so I did not accept the skip. I added
``<environmentVariables><LANG>C.UTF-8</LANG><LC_ALL>C.UTF-8</LC_ALL>`` to the
surefire configuration in ``cometgui-process/pom.xml`` -- this module's own POM,
not the shared root one -- with the reasoning written into the POM. The run is
now ``Tests run: 27, Failures: 0, Errors: 0, Skipped: 0``. The assertion is
unchanged: it still creates a real directory whose name is not ASCII and runs a
real process out of it. What changed is that the JVM can spell the name.

**Repair 2: a test whose name claimed more than it checked -- found by
injection.** I injected two defects of my own, neither of them one the agent
had used.

*Injection B, caught.* ``hang-with-child`` reports ``child + (child.pid() + 1)``
instead of the real pid::

    FakeToolSelfTest.hangWithChildStartsARealDescendant:332
      child 88705 is not among the parent's descendants [88704]
      ==> expected: <true> but was: <false>

*Injection A, NOT caught.* ``delayed-output`` announced the file **before**
creating it -- the exact reordering its ``@DisplayName`` ("creates the file
before it announces it") claims to forbid. The suite stayed green,
``Tests run: 27, Failures: 0``. The test read the process's output only after
exit, by which time both events had happened in either order, so ordering was
asserted by the display name and by nothing else. This is the project's
recurring shape in a new disguise: not an expectation computed by the code under
test, but an expectation that the test never actually evaluated.

The repair makes the ordering a fact rather than a comment. ``delayed-output``
now prints ``created <file> <bytes>`` where ``<bytes>`` is read back **out of
the file** with ``Files.size``, so the line cannot be printed before the file
exists; the self-test pins the literal ``created late.txt 4``. Re-applying
injection A now fails deterministically::

    FakeToolSelfTest.delayedOutputAnnouncesTheFileOnlyAfterCreatingIt:216
      expected: <0> but was: <1>

(the fake dies on ``NoSuchFileException``). Reverted, and green again at
``Tests run: 27, Failures: 0, Errors: 0, Skipped: 0``.

**Findings from this unit that later units must honour**, all verified by the
agent against this machine and all recorded because they change a design:

#. **Kill descendants first, then the parent.** PID 1 in this container is not
   an init that reaps orphans. If the parent is killed first, its child is
   reparented to PID 1 and, once killed, becomes a **permanent zombie**:
   ``/proc/<pid>`` still exists, so ``ProcessHandle.isAlive()`` stays ``true``
   for ever and ``onExit()`` never completes. Gate item 2 asserts on liveness,
   so with the wrong order that assertion can never pass here -- and it would
   look like the process service's bug. Unit 2 terminates descendants first,
   while their parent is alive to reap them.
#. **``Process.destroy()`` closes the process's streams on Linux**;
   ``ProcessHandle.destroy()`` sends the same ``SIGTERM`` and leaves them alone.
   Unit 2 therefore cancels through the handle, so that output already written
   by the tool is still drained rather than lost to a closed pipe.
#. **SpotBugs analyses test sources in this build** (``includeTests`` is on).
   ``throws Exception`` on a test helper fails it; narrow it.

**Scope.** ``git status --short`` showed only unit 1's four files; the
``cometgui-provenance`` changes in the tree at the time belong to the Phase 04
agent and were never staged by this phase.

Unit 2 -- the core process service
==================================

:Agent: fresh phase agent, spawned 2026-08-31
:Commit: ``3797a86``, plus the orchestrator's watchdog repair in the sign-off
   commit
:Outcome: **ACCEPTED**

**What was built.** Six main classes in ``org.cometgui.tools.process`` --
``ProcessService`` (the one ``ProcessBuilder`` in the product),
``StartedProcess``, ``ProcessTree``, ``StreamPump``, ``LineSplitter``,
``GuardedListener`` -- and six test classes. 3,639 lines.

**What I ran.** I read the whole diff, then, myself:

* ``mvn -o -B -pl cometgui-process -am verify`` -- **BUILD SUCCESS**,
  ``Tests run: 118, Failures: 0, Errors: 0, Skipped: 0`` in
  ``cometgui-process`` and ``Tests run: 256, Failures: 0`` in
  ``cometgui-domain``; ``BugInstance size is 0`` for both modules;
  ``You have 0 Checkstyle violations`` from all four executions.
* ``mvn -o -B -pl cometgui-process org.pitest:pitest-maven:mutationCoverage``
  -- **``Generated 95 mutations Killed 91 (96%)``**, line coverage
  ``227/241 (94%)``, against ``R-TEST-02``'s 80% threshold. The module's own
  POM now carries ``<cometgui.mutation.skip>false</cometgui.mutation.skip>``;
  nothing was narrowed, excluded or lowered.
* ``grep -rn "Thread\.sleep\|TimeUnit\.[A-Z_]*\.sleep\|LockSupport\.park"
  cometgui-process/src`` -- no output.
* ``grep -rn "new ProcessBuilder" cometgui-process/src/main`` -- exactly one
  hit, ``ProcessService.java:152``.

**Three defects I injected into the production code, none of them one the agent
had used.**

*Injection 1 -- the constructed environment of* ``R-PROC-04``. Deleting
``builder.environment().clear()`` so the JVM's environment is inherited. Four
tests failed, each naming the real value that leaked in::

    ProcessServiceTest.anEmptyEnvironmentIsTrulyEmpty
      expected: <[... env PATH -absent-, env HOME -absent-, env LANG -absent-,
                  envcount 0]>
      but was:  <[... env PATH /mnt/10TBdrive/.../bin:..., env HOME /home/agent,
                  env LANG C.UTF-8, envcount 43]>

*Injection 2 -- the heap bound of* ``R-PROC-03``. Raising
``LineSplitter.DEFAULT_MAXIMUM_LINE_LENGTH`` to ``Integer.MAX_VALUE``, which is
the change someone would actually make to stop a long line being split::

    LineSplitterTest.theDefaultCapIs65536 expected: <65536> but was: <2147483647>
    ProcessServiceTest.longLinesAreSplitAtTheCap expected: <20> but was: <5>

*Injection 3 -- the decoder.* ``CodingErrorAction.REPLACE`` to ``REPORT``::

    ProcessServiceTest.malformedBytesBecomeTheReplacementCharacter
      expected: <[A<U+FFFD>(B]>
      but was:  <[[cometgui] standard output could not be read:
                  java.nio.charset.MalformedInputException: Input length = 1]>

The third also shows the pump's contract holding: a read failure becomes a
visible line rather than silence.

An earlier form of injection 2 -- ``if (false && ...)`` -- was rejected by
Checkstyle's ``SimplifyBooleanExpression`` before any test ran. Worth knowing:
the quality gate constrains what an injection may look like, and an injection
that cannot compile has proved nothing.

**The agent found the recurring project defect in its own test, and said so.**
Its first "the exit is the last event, exactly once" test stayed green with
``joinUninterruptibly(standardOutputPump)`` deleted -- the assertion held only
because the pumps happened to finish first. PIT agreed: both join-removal
mutants survived. It added two tests that park a pump inside the listener and
then watch a negative window for an early ``onExit``; each injected join
deletion now fails one of them deterministically. **Anything that later
simplifies** ``awaitCompletionAndNotify`` **must re-run that injection.**

.. _p03-pit-orphans:

Repair: PIT leaks hanging fakes, and would have on every full build
-------------------------------------------------------------------

PIT runs the suite once per mutation in a minion JVM it kills at its own
timeout. A mutant that breaks cancellation makes a hanging scenario block; the
minion is killed mid-test, the test's ``finally`` never runs, and the fake is
reparented to PID 1, which in this container reaps nothing. The agent's two runs
left eight such processes; mine left four. ``scripts/build.sh`` runs that goal in
its ``gates`` stage, so every full build would add more.

I fixed it inside the fake rather than in the build script, which is not this
phase's file. ``FakeTool``'s hanging scenarios now wait ``WATCHDOG_SECONDS =
300`` and then ``Runtime.getRuntime().halt(71)``.

**Why that cannot make a cancellation test pass by accident**, which is the only
reason a watchdog would be the wrong answer here: 300 seconds is one to two
orders of magnitude longer than any timeout in this phase, and 71 is a code no
signal produces -- a cancelled process exits 143 (``SIGTERM``) or 137
(``SIGKILL``), and the cancellation tests assert those numbers exactly, so a
test that only passed because the watchdog fired would see 71 and fail.
``Runtime.halt`` rather than ``System.exit`` because ``hang-ignoring-term``
deliberately installs a shutdown hook that never returns.

.. _p03-watchdog-observed:

**Observed, not assumed.** My own PIT run left four orphaned fake JVMs. I
sampled ``ps -eo pid,ppid,args | awk '$2==1 && /fakes.FakeTool/'`` every 30
seconds and killed nothing::

    19:04:51 orphaned FakeTool JVMs: 4
    19:05:21 orphaned FakeTool JVMs: 4
    19:05:51 orphaned FakeTool JVMs: 4
    19:06:21 orphaned FakeTool JVMs: 4
    19:06:51 orphaned FakeTool JVMs: 3
    19:07:21 orphaned FakeTool JVMs: 0

PIT started at about 19:01, which puts the transition where a 300-second
watchdog puts it. The machine cleans itself now.

**Findings from this unit that later phases must know:**

#. **With an empty environment the child JVM's** ``sun.jnu.encoding`` **falls
   back to ASCII**, so a non-ASCII environment *value* passed to a tool would be
   corrupted at the child's end. This phase keeps environment values ASCII and
   flags it rather than papering over it.
#. **The cancellation exit codes 143 and 137 are Linux-specific.** A Windows or
   macOS run will need different literals, and no machine here can check that.
#. ``ProcessService.closeStandardInput`` is package-private and static purely so
   it can be proved: "the pipe was closed" is otherwise unobservable, and PIT's
   ``VoidMethodCallMutator`` deletes an unobservable call for free.

**Four surviving mutations**, all argued as equivalent and all in the same
shapes: the ``if (interrupted) Thread.currentThread().interrupt()`` re-assert in
the uninterruptible-wait idiom (nothing interrupts a daemon completion thread);
``pump.join()`` removal inside a ``while (pump.isAlive())`` loop, which is more
CPU and identical behaviour; ``depthBelow``'s ``<`` to ``<=``, reachable only
with a cycle in the parent chain and returning the same value either way; and
``read >= 0`` to ``read > 0`` where ``InputStreamReader.read(char[8192])``
cannot return 0. The score is 96% with all four counted as failures, so none of
them is load-bearing for the gate.

Unit 2b -- the adversarial proof of gate item 2
===============================================

:Agent: fresh phase agent, spawned 2026-08-31, whose whole job was to break
   cancellation
:Commit: ``5d4ff81``, plus the orchestrator's two repairs in the sign-off commit
:Outcome: **ACCEPTED. It found a real hole and closed it.**

**What was built.** ``ProcessCancellationTest`` -- 1,126 lines, twelve tests in
five nested groups -- and two methods on the shared ``RecordingListener``. No
production code changed.

**What I ran myself:** ``mvn -o -B -pl cometgui-process -am verify`` --
**BUILD SUCCESS**, ``Tests run: 130, Failures: 0, Errors: 0, Skipped: 0``
(118 before this unit), ``BugInstance size is 0``, ``0 Checkstyle violations``
from every execution; and ``org.pitest:pitest-maven:mutationCoverage`` --
``Generated 95 mutations Killed 88 (93%)``.

.. _p03-pit-variance:

**PIT's score varies between runs and the worklog must say so.** I measured 96%
at the unit 2 sign-off and 93% here, on the same 95 mutations; the agent
independently reproduced 93% on the pre-change sources and found the survivor
set byte-identical between its two runs. The difference is three mutants in the
uninterruptible-wait idiom that PIT classifies as timed-out (killed) or
survived depending on machine load. **The honest number is a floor of 93%
against a threshold of 80%**, not a point value, and a later phase should not
treat 96% as a regression baseline.

The hole: the classic bug went undetected by everything
--------------------------------------------------------

The agent injected the snapshot-after-destroy defect -- taking
``descendants()`` **after** the parent has been destroyed, which is the exact
bug ``ProcessTree``'s whole design exists to avoid -- and **all 128 tests
passed, including the brand-new real-process gate item 2 test.**

The reason is worth recording, because it is not carelessness: ``destroy()``
only queues a ``SIGTERM``. The snapshot on the next line still sees the child
and still kills it. The bug is a race this machine wins every time, and a test
that waited for the race to be lost would be a test synchronised by a sleep.

The repair asserts the order where it is a **fact rather than a race**: it drives
``StartedProcess.requestCancellation`` against a ``Process`` whose handle records
what was asked of it, and requires the recorded sequence. Re-injected, it now
fails with::

    expected: <[descendants, destroy:200, destroy:100]>
    but was:  <[destroy:100, descendants, destroy:200, destroy:100]>

**This is a fifth shape of the project's signature defect and it deserves its own
name: a property that is true only because a race is always won.** Not an empty
rule, not an expectation computed by the subject, not a seam the production path
need not use, not an assertion too coarse to see a partial failure -- a real
assertion, on the real subject, that the machine happens to satisfy for a reason
unrelated to the code being right.

Injections the agent ran, with what happened
---------------------------------------------

* **Reverse the termination order.** Six ``ProcessTreeTest`` failures -- but
  *every real-process test still passed*, because both signals land microseconds
  apart. Honest reporting of a partial result; the new order tests now catch it
  too.
* **``destroyAll`` made a no-op.** Ten failures across three classes, including
  ``afterTheGraceItIsKilled expected: <[out:hanging, out:terminating, exit:137]>
  but was: <[out:hanging, exit:137]>``.
* **``ProcessHandle.destroy()`` replaced by ``Process.destroy()``**, the choice
  unit 2 documented. Five failures, and one consequence nobody had written down:
  **the exit code is misreported as 1**. Closing the read end gives the tool
  ``EPIPE``, it dies of an ``IOException`` before the signal lands, and a
  cancelled run would be shown to the user as a tool crash. It also delivered 82
  lines where the correct implementation delivers 1,296.

My own injections
------------------

*Injection D -- never collect descendants at all*, so only the parent is killed.
This is gate item 2's central property. Twelve failures, and the real-process
test failed on **liveness**::

    ProcessCancellationTest.awaitExit:231
      the child (pid 325499) was still alive 60s after the cancellation;
      isAlive() is true

*Injection E -- the fourth defect shape, on tier 1's warning.* I made
``StreamPump`` drop exactly one line in the middle of a stream, and then --
the harder case -- **replace line 42 with a repeat of line 41, so the line count
is unchanged.** A count-preserving corruption is invisible to an assertion on
size plus a sampled element, which is what ``onExitComesOnceAndLast`` had. It
now asserts the whole ordered sequence, built by the test from the format
``FakeToolSelfTest`` pins with hand-typed literals::

    ProcessServiceTest.onExitComesOnceAndLast
      expected: <[... out 41, out 42, out 43 ...]>
      but was:  <[... out 41, out 41, out 43 ...]>

The two flood tests caught it independently and already had the right shape::

    flood line 42 is not what the fake wrote and it is not the last line
    delivered, so the output was corrupted rather than truncated:
    expected "0000000042 0123456789..." but got "0000000041 0123456789..."

Repairs I made
---------------

#. **Gate item 2's failure was the single word "Timeout".**
   ``handle.onExit().get(n, SECONDS)`` throws a ``TimeoutException`` carrying no
   message, so the one gate item tier 1 said he would personally re-inject
   reported nothing about *what* survived. An ``awaitExit(handle, what)`` helper
   now fails with the sentence above, naming the process and its pid. Proved by
   re-running injection D.
#. **The interleave assertion was too coarse**, as above.
#. **The null-rejection helper is now named** ``deliberateNull``, matching Phase
   04, per :ref:`p03-null-idiom`. Same technique, one name across both phases.

The redaction collision, and where the shared rule set ended up
================================================================

Recorded here because it changed a unit mid-flight and because the lesson is
about escalation, not about redaction.

``R-SEC-03`` was being implemented **twice, in parallel, by two live phases**.
Phase 04 had ``SecretRedactor`` and ``SecretRegistry`` in
``cometgui-provenance``; this phase's unit 3 was building ``SecretNames`` and
``SecretValues`` in ``cometgui-process``. The two modules are **siblings** --
each depends on ``cometgui-domain``, neither on the other -- so neither
implementation could consume the other, and the lists had already diverged by
one keyword. A variable named ``REQUEST_SIGNATURE`` would have been redacted in
the process log and **not** in the provenance record: exactly the silent,
security-relevant drift ``R-SEC-03`` exists to prevent.

The main orchestrator moved the shared rules into ``org.cometgui.domain.secrets``
(commit ``b0e7122``) and gave Phase 04 ownership, because its tests there are
much deeper -- a PEM private-key rule, a seeded secret corpus, registry ordering
pinned by test. This phase consumes them and keeps only ``ProcessRedactor``,
whose two ideas are process-specific and were explicitly ruled out of the merge:

* **Redact each argv element BEFORE** ``ToolCommand.displayString()`` escapes
  it. Escaping turns a ``"`` inside a token into ``\"``, and a literal
  post-escape search then misses it and prints the secret in full.
* **Return the argument by reference when the registry is empty**, with a test
  asserting reference identity rather than equality, so a 500 MB flood pays
  nothing for a feature no tool in this workflow uses.

**One marker, decided here:** the domain's ``REDACTION_MARKER`` wins and this
phase's ``***REDACTED***`` is deleted. Two markers would put one string in the
console log and another in the provenance record for the same secret in the same
run; one marker is worth more than the extra distinctiveness, and changing the
shared constant would churn Phase 04's deeper suite for a cosmetic gain. The
marker is asserted in a Phase 03 test as a **hand-typed literal**, not as a
reference to the constant, so a later change to it breaks a test visibly instead
of silently agreeing with itself.

**The lesson, which is not about secrets.** Unit 3's agent found this itself: it
named its class ``ProcessRedactor`` rather than ``SecretRedactor`` deliberately,
recorded that Phase 04's class existed and was unreachable across the sibling
boundary, predicted the exact collision, and wrote that merging the two "is an
architectural decision, and it belongs to whoever owns both phases". That is the
correct analysis and the correct escalation target -- **but it was written in a
Javadoc comment, and the main orchestrator found it by reading the tree rather
than by being told.** A cross-phase finding has to travel as a message, not as a
comment in the file that contains the problem. This phase carried the same fault
one level up: the finding never reached tier 1 through me either.

Unit 3 -- the process side of R-SEC-03
======================================

:Agent: fresh phase agent, spawned 2026-08-31, re-scoped mid-flight
:Commit: ``cdec906``
:Outcome: **ACCEPTED**

**What was built.** ``ProcessRedactor`` -- 181 lines holding **no rules of its
own** -- plus ``ProcessRedactorTest``, ``SecretRedactionPropertyTest`` and the
``SecretScan`` test helper. The rules come from ``org.cometgui.domain.secrets``.
The agent's first implementation, with its own ``SecretNames`` and
``SecretValues`` and 32 tests, was **deleted rather than committed** when the
shared rule set landed.

**What I ran myself:**

* ``flock _build/cometgui-maven.lock mvn -o -B -pl cometgui-process -am verify``
  -- **BUILD SUCCESS**, ``Tests run: 159, Failures: 0, Errors: 0, Skipped: 0``
  in ``cometgui-process`` (130 before) and ``Tests run: 359, Failures: 0`` in
  ``cometgui-domain``; ``BugInstance size is 0``; ``0 Checkstyle violations``.
* ``... -am test-compile org.pitest:pitest-maven:mutationCoverage`` --
  ``cometgui-process``: ``Generated 103 mutations Killed 97 (94%)``, line
  coverage ``241/255 (95%)``. Every ``ProcessRedactor`` mutant is killed; the
  survivors are unit 2's already-argued equivalents.

.. _p03-pit-command:

**The PIT command in the acceptance conditions no longer works, and this will
hit every later phase that adds a package to** ``cometgui-domain``.
``mvn ... -pl cometgui-process org.pitest:pitest-maven:mutationCoverage`` now
fails with *"8 tests did not pass without mutation"*, because ``_build/m2repo``
holds a ``cometgui-domain`` snapshot that predates ``b0e7122`` and contains
**zero** ``domain/secrets`` classes -- ``jar tf | grep -c domain/secrets`` is 0.
``-am`` alone does not fix it: a goal-only invocation still resolves the sibling
from the repository. The working form prefixes a lifecycle phase::

    flock _build/cometgui-maven.lock mvn -o -B -pl cometgui-process -am \
        test-compile org.pitest:pitest-maven:mutationCoverage

This is Phase 02's surprise 9 in a sharper form: ``scripts/build.sh`` runs
``clean verify`` and never ``install``, so the local repository is permanently
behind the reactor.

**My injections into the production code.**

*Injection F -- redact after escaping instead of before*, which is
``ProcessRedactor``'s headline claim::

    ProcessRedactorTest.redactedBeforeEscaping
      expected: <["/opt/tool", "--api-token", "[REDACTED]"]>
      but was:  <["/opt/tool", "--api-token", "ab\"cd\\efgh"]>

The escaped form is exactly the leak the ordering prevents: the token's ``"``
became ``\"`` and a literal search no longer matched it.

*Injection G -- stop redacting the captured environment altogether.* Eight
failures, including the property test::

    SecretRedactionPropertyTest.theEnvironmentLeaksNothing
      expected: <{... UPLOAD_TARGET=...?key=[REDACTED],
                  LIMELIGHT_API_TOKEN=[REDACTED]}>
      but was:  <{... UPLOAD_TARGET=...?key=7a3f9c2e8b4d6a1f0c5e7b9d3a2f8c4e6b1d0a5f,
                  LIMELIGHT_API_TOKEN=7a3f9c2e8b4d6a1f0c5e7b9d3a2f8c4e6b1d0a5f}>

**The agent's own "cheapness" test was passing by accident, and PIT found it.**
``assertSame`` on an ordinary Comet line passed whether the empty-registry
short-circuit was there or not, because the shared rules *also* return their
argument by reference when no rule fires. The mutant ``size() > 0`` to
``size() >= 0`` survived and said so. The test now uses a line the rules **would**
have rewritten (``Authorization: Bearer abc123def456ghi789``) and asserts both
sides. That is the same shape as unit 2b's finding -- a real assertion, on the
real subject, true for a reason unrelated to the code being right.

**The sliding-window scanner, and the proof it fires.** Tier 1 warned that
absence of the whole secret is not absence of the secret: Phase 04 shipped a
sweep asserting ``contains(wholeSecret)`` that passed while a private key leaked
with one character rewritten. ``SecretScan`` slides a 4-character window over the
secret and reports every surviving fragment, and
``theScannerSeesALeakThatTheNaiveAssertionMisses`` **builds that exact failure**
-- a 40-character secret with its last character rewritten -- and requires the
naive assertion to pass while the scan reports 36 of 37 windows. A middle rewrite
leaves 33. Both hand-typed. ``survivingFragments`` refuses a secret too short to
have a window, so a scan can never be vacuously clean.

**The marker is pinned as a hand-typed literal**, not as a reference to the
constant it checks::

    assertEquals("[REDACTED]", SecretRedactor.REDACTION_MARKER, ...)

**A stated gap this phase accepted, which Phase 12 must read.** With **no**
registered secret value, ``redact(String)`` returns the line by reference and
skips the *pattern* rules as well, so a bearer token printed by a tool that was
never given a credential reaches the console log unredacted. The trade is in the
Javadoc and pinned by a test rather than hidden. It does not extend to the two
renderings ``R-SEC-03`` actually names: the display command and the environment
get the full rule set **unconditionally**, because each is produced once per
stage rather than once per line. Phase 12 registers its Limelight token and the
per-line path switches on.

**Also for Phase 12:** ``--api-token`` is **not** in the shared
``SECRET_BEARING_LONG_FLAGS`` (``--auth-token``, ``--access-token``,
``--session-token``, ``--api-key`` are). The registry covers it, but the real
Limelight flag should be checked against that list.
