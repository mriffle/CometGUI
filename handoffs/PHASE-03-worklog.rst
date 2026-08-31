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


