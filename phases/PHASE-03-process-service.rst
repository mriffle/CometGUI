=========================
PHASE-03: Process Service
=========================

:Phase: 03
:Status: NOT STARTED
:Depends on: 01, 02
:Blocked by decisions: none
:Delivers: R-PROC-02, R-PROC-03, R-PROC-04
:Proves: Foundations for AC-WF-05

Purpose
-------
One place where processes are created, observed, cancelled and logged. Every
later adapter depends on this being correct under adversarial conditions,
because a scientific tool that hangs, floods stdout, spawns children or dies
half-way is the normal case, not the exception.

In scope
--------

* Argv-only process launch with explicit working directory and constructed
  environment.
* Independent, non-blocking stdout and stderr pumps with timestamped events,
  streamed to per-stage log files as they arrive.
* Bounded in-memory console buffering with a documented retention policy.
* Cancellation, including best-effort termination of descendant processes on
  each tier-1 platform.
* Exit code, duration, and a safely rendered display command with redaction
  applied.
* Optional per-stage timeouts, off by default.
* The fake-executable test suite from the specification (interleaving, exit
  codes, children, hangs, floods, missing outputs, malformed outputs,
  partial files, delayed output, spaces and Unicode in paths).

Out of scope
------------

* Knowing anything about Comet, Percolator, PDV or the converter.

Deliverables
------------

* ``org.cometgui.tools.process`` implementation and its ``ProcessRunner``
  port.
* ``src/test/resources/fakes/`` -- the fake tool scripts, cross-platform.
* ``docs/developer/tool_adapters.rst`` process-service section.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. Every fake-executable scenario in the specification has a passing test.
2. A hanging process with a child is cancelled and neither process
   survives; the test asserts on process liveness, not on the absence of an
   exception.
3. A fake emitting 500 MB of stdout completes with bounded heap and a
   complete on-disk log.
4. Paths containing spaces and non-ASCII characters work on the reference
   platform.
5. An ArchUnit rule confines ``ProcessBuilder`` to this package and fails
   when it is used elsewhere.
6. No test uses a fixed sleep to synchronise.

Risks and notes
---------------

* Descendant termination differs sharply across platforms; use
  ``ProcessHandle`` descendants and verify per platform rather than
  assuming.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-03-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-03-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
