==========================================================
Between Phases 14 and 15 -- a tier-1 repair brief (E12)
==========================================================

:Tier: a single fix agent, dispatched directly by tier 1 (no phase
   orchestrator)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-29
:Starts from: Phase 14 signed off PARTIAL (``STATUS.rst``,
   ``status-p14-signed``), at ``f17b0be``. No ``D-`` item is open.
:Runs alongside: **nothing.**
:Owner's instruction (2026-10-29): *"fix the E12 bug and then proceed to
   phase 15"*.

.. note::

   Written and owned by tier 1. Do not edit it. Record your work in
   ``handoffs/FIX-P14-P15-worklog.rst``, which is yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``specification.rst`` or
   ``CLAUDE.md``.

.. _fixp14-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 11 hours (build 142 min, ``tests`` about 320, the other twenty about 190).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 14's exit gate on the tree you start from. Tier 1 runs them
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

**Two hours is not enough for** ``--only tests`` **any more** (about 320
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs.

The repair: E12
===============

Read ``ONBOARDING.rst`` and ``CONTRIBUTING.rst`` first, then E12 in
``handoffs/PHASE-14-worklog.rst`` (and the failure-path tests of its unit 8b
that found it), ``handoffs/PHASE-04-handoff.rst`` (the provenance writer and
the run states) and ``handoffs/PHASE-13-handoff.rst`` (the bounded rename
retry, ``R-PROV-05``).

**The defect.** If ``provenance/`` becomes unwritable mid-run, or the disk
fills during finalisation, the run's outcome and ``run.json`` say SUCCEEDED
although ``provenance.json`` and ``provenance.rst`` were never written; the
outcome text only adds "Its provenance could not be finalised". Cause, as
found: ``RunExecution.finish`` fixes the outcome before it writes the seal.

**What the repaired product does -- tier 1's decision, to be written into
the specification as revision 21 at sign-off:**

#. **A run whose** ``provenance.json`` **could not be finalised is never
   SUCCEEDED.** It ends ``FAILED``, and its failure reason names the cause in
   plain words: that the provenance record could not be written, which file,
   and the underlying error (e.g. no space left on the device). The work its
   tools did stays on disk, untouched; nothing is deleted.
#. **The outcome is decided after the seal**, in every path that finalises a
   run: the ordinary run, the Percolator compatible rerun, and anything else
   that calls the same finalisation (Limelight's conversion run, if it
   seals). Find every one; say in the work log what you found.
#. **What is written, in order.** The seal is attempted first. If it
   fails, ``run.json`` records ``FAILED`` with that reason, and
   ``events.log`` gets an event saying so, each as far as the disk allows.
   **If** ``run.json`` **itself cannot be written, a reopened project must
   still not show the run as SUCCEEDED**: it is no worse than an interrupted
   run. Prove which it shows.
#. **Downstream follows the state.** Results, Visualisation, Limelight and
   the Percolator rerun offer for a SUCCEEDED run only; a run that failed
   this way is offered what any FAILED run is offered, no more. The
   Provenance section opens it as a failed run with partial provenance
   (Phase 13 already handles a run with no ``provenance.json``; prove it
   still does here).
#. **The interface says it.** The stepper and the run's outcome text show
   the run failed, with the reason; no surface anywhere says "succeeded" for
   it. Check every place the three view models' "Its provenance could not be
   finalised" text is produced and replace it with the failed outcome.

**Prove it** with tests that assert values, not "nothing threw":

* in ``cometgui-workflow``, against the real Comet and Percolator where the
  existing tests already do: a run whose ``provenance/`` is made unwritable
  before the seal ends FAILED, ``run.json`` reads FAILED with the reason,
  ``events.log`` holds the event, and every tool output is still on disk with
  its digest unchanged;
* the disk-full case through the same file-size limit Phase 14 uses
  (``RLIMIT_FSIZE``), not a real full disk;
* ``run.json`` unwritable too: what a reopened project shows, by the
  product's own reader;
* at GUI level, the failure-path tests 8b wrote for this case now assert
  FAILED, never SUCCEEDED. **This is a strengthening of those tests; it is
  the one place an existing assertion changes.** Say each one you change and
  why;
* ``scripts/e2e/failure-paths.toml`` and ``check-failure-paths.sh`` still
  grade every scenario, with this one's mapped tests changed in place.

**Your own injections, each into production code, each seen red and
restored with** ``sha256sum -c``: the outcome fixed before the seal again
(the old ordering); a failed seal recorded as SUCCEEDED in ``run.json`` only;
and the Limelight or rerun path left on the old ordering while the ordinary
run is repaired -- red in its own test.

Gates for this repair: read your diff for anything weakened; run each
affected module's tests with counts; targeted PIT on the changed classes;
then ``bash scripts/verify-all-gates.sh --only workflow --only provenance
--only provenanceui --only percolator --only limelight --only results --only
quality --only e2e --only docs --only traceability``, and any other gate
whose harness reads what you changed. Do **not** run ``--only tests`` in
full; run ``bash scripts/verify-test-gates.sh --preflight`` instead, and say
whether any test you changed or added is one the ``tests`` harness selects.

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
  run is already about 11 hours; say what your harness costs.
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
