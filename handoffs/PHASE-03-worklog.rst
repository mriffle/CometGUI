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
       ``ProcessRunner`` implementation), the ``RunningProcess``
       implementation, the two stream pumps, exit code and duration,
       cancellation with descendant termination
     - Needs unit 1 to have anything to launch. ``R-PROC-02``, ``R-PROC-04``.

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
     - Needs units 1, 2 and 4. Gate items 1, 2, 3, 4.

   * - 6
     - ``cometgui-archtests``: the ``R-PROC-02`` rule hardened, and a census
       assertion that the rule's subject set is not empty
     - Needs a real class in the process service package before the rule can
       be shown to permit the right thing. Gate item 5.

   * - 7
     - ``cometgui-process`` test sources: the mechanical no-fixed-sleep scan
       over this phase's own test sources
     - Scans units 1-5's tests, so it goes after them. Gate item 6.

   * - 8
     - ``docs/developer/tool_adapters.rst``
     - Describes the service as built, so it goes last.

Sign-offs
=========

