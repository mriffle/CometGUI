==========================================================
Between Phases 12 and 13 -- a tier-1 repair brief
==========================================================

:Tier: a single fix agent, dispatched directly by tier 1 (no phase
   orchestrator)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-13
:Starts from: Phase 12 signed off PARTIAL (``STATUS.rst``,
   ``status-p12-signed``). No ``D-`` item is open.
:Runs alongside: **nothing.**

.. note::

   Written and owned by tier 1. Do not edit it. Record your work in
   ``handoffs/FIX-P12-P13-worklog.rst``, which is yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``specification.rst`` or
   ``CLAUDE.md``.

.. _fixp12-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 10 hours (build 135 min, ``tests`` about 310, the other eighteen about 150).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 12's exit gate on the tree you start from. Tier 1 runs them
   once more, at **this** phase's exit gate.
#. **The inner loop is** ``bash scripts/dev-verify.sh`` -- it maps the diff to
   the modules it can break (``--dry-run`` shows the selection). It is never a
   gate.
#. **Its** ``--mutation`` **flag is broken** and must not be used. For mutation
   evidence at unit sign-off, run PIT on the **one** module, restricted to the
   classes the unit changed, e.g. ``mvn -pl <module> -am test-compile
   org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>``, with
   the project's Maven settings (copy how ``scripts/dev-verify.sh`` invokes
   Maven). Never add a PIT plugin or change the POM's PIT configuration.
#. **Unit sign-off** is: read the diff, run the affected module's tests,
   targeted PIT as above, your own production-code injections, and
   ``scripts/ci/docs-build.sh`` if ``.rst`` changed. Nothing wider -- **but not
   narrower either**: run ``bash scripts/verify-all-gates.sh --only NAME`` for
   every gate whose harness reads what the unit changed (``docs`` and
   ``traceability`` for any documentation; ``quality`` for architecture rules;
   ``params`` if ``cometgui-params-comet`` is touched at all).
#. **``--only tests`` is the one deferred harness, and deferring it bit the
   intake**: a unit added a test that read a document the harness sandbox did
   not carry, and six controls went red at the end. If a unit's tests read any
   file outside their module, say so in the work log the day it lands, and run
   ``--only tests`` at the end of the phase *before* you report -- in the
   foreground, or in the background with a loop that waits for it to exit.
   **Never end your turn while your own check is still running**; nothing will
   wake you, and the intake orchestrator stalled for exactly this reason.
#. Pass this section **verbatim** to every unit agent. They do not see this
   brief otherwise.

**Two hours is not enough for** ``--only tests`` **any more** (about 310
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs.

The two repairs
===============

Read ``ONBOARDING.rst`` and ``CONTRIBUTING.rst`` first, then
``handoffs/PHASE-12-handoff.rst`` (where both were found) and
``handoffs/PHASE-05-handoff.rst`` (the Tool Manager and its executors).

#. **The Tool Manager starts processes on the interface thread.**
   ``ToolManagerViewModel.refresh()`` (``cometgui-ui``) is called on the
   JavaFX application thread and reads ``ToolManager.offers()``, which runs
   each installed tool's version check -- for the Limelight converter, a
   ``java -jar ... --version`` -- so the window can stall on every refresh.
   The class already takes a ``background`` executor and a ``uiThread``
   executor; use them. Reading the offers happens off the interface thread,
   and the rows are updated on it. Keep what ``refresh()`` promises: a row
   whose key is still offered keeps its identity, its running install and
   its progress. **Prove it with a test that fails if any process is started
   on the interface thread during a refresh**, graded through the one process
   service, not by timing; check every other caller of ``refresh()`` and of
   ``offers()`` for the same fault and say what you found. Your own
   injection: put the read back on the interface thread and show that test
   go red.
#. **The ``tests`` preflight misses three prerequisites.**
   ``scripts/verify-test-gates.sh --preflight`` must also refuse to start,
   with a FATAL naming what is missing and how to get it, when the host has
   no ``gcc`` or no ``/usr/bin/timeout`` (the real-PDV tests compile an X11
   shim and cap processes with them -- see ``handoffs/PHASE-11-handoff.rst``)
   or when ``scratch/limelight-upload-spike/artefacts`` lacks the pinned
   importer JAR (verify its SHA-256 against the one place it is pinned, as
   the large-fixture check does). Show each new refusal firing, without
   touching the host: a ``PATH`` that hides the tool, a copy of the tree
   missing the JAR. Never install anything.

Gates for this repair: read the diff for anything weakened; run the affected
module's tests with counts; targeted PIT on the changed classes; then
``bash scripts/verify-all-gates.sh --only shell --only install --only
quality --only docs --only traceability``, and any other gate whose harness
reads what you changed. Do **not** run ``--only tests`` in full; run
``bash scripts/verify-test-gates.sh --preflight`` instead.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a recorded
  argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (the old anchor is gone, not just
  a marker found -- a marker can match elsewhere), restore and verify with
  ``sha256sum -c``. **Count the tests that ran** before believing a red or a
  green: tier 1's own injections at Phase 07 first ran zero tests against a
  stale local repository and would have read as nothing.
* **A test proves the value, not that nothing threw**: row counts, file
  hashes, argument arrays, the named file in the message.
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* Locale: number formatting uses ``Locale.ROOT``; do not enable parallel test
  execution.
* ``D-001`` attribution and the ``D-009`` copyright line apply as before.
* Never weaken a gate, checksum, validation rule or threshold; never add to the
  pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors, never
  lower one. A new harness for this phase's gate, registered additively in
  ``scripts/verify-all-gates.sh``, is approved. **Keep it lean**: the full gate
  run is already about 10 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Report back
===========

Report to tier 1 when finished or genuinely blocked: commits, what each
repair changed, the tests that prove it with counts, your injections with
their failure text, the gates you ran and their results, and anything
escalated. Short and factual.
