=================================================================
PHASE-05 unit-12 brief -- tier 1 to the phase orchestrator
=================================================================

:Phase: 05 -- Tool Registry and Installer
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 09
:Date: 2026-10-01
:Runs alongside: **nothing.** You are the only phase live in this tree.
:Resumes at: **unit 12 of 13.** Units 1-11 and 13 are accepted and are not
   yours to revisit, except to read them.
:Expected grade: **PARTIAL**, on gate item 9 alone (macOS).

.. note::

   This document is written by tier 1 and owned by tier 1. Do not edit it.
   Everything you produce goes in ``handoffs/PHASE-05-worklog.rst`` and
   ``handoffs/PHASE-05-handoff.rst``, which are yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/index.rst`` or ``CLAUDE.md``.

Phase 05 was stopped mid-unit-12 on 2026-09-18 by owner instruction -- not
because anything was wrong. The owner restarted it on 2026-10-01. Your job is
**unit 12 only**, then a report. Tier 1 owns the macOS run (gate item 9) and
the phase exit-gate re-run.

.. _p05u12-builds:

Build economy -- read this before anything else
================================================

The owner has now said this **three times** (2026-09-17, 2026-09-18 and
2026-10-01, the last in the instruction that dispatched you): *running the whole
build/test suite takes a very long time; be less aggressive about doing this,
as it really slows down development.* ``scripts/build.sh`` is about 24 minutes
and ``scripts/verify-all-gates.sh`` about 65 more. This brief **supersedes**
every earlier instruction to the contrary, including the step *"take a baseline
on a quiet tree"* in ``handoffs/PHASE-05-handoff.rst`` -- **do not take it.**

#. **You do not run** ``scripts/build.sh`` **or** ``scripts/verify-all-gates.sh``
   **in full. At all.** Tier 1 runs them exactly once, at the phase exit gate,
   after you report. Nothing has touched Java or a POM since unit 11 was
   accepted at ``9a33bb4``, so there is nothing a baseline would tell you.
#. **Run what the change can break.** Unit 12 is a shell script plus an
   additive edit to ``scripts/verify-all-gates.sh``. Its checks are: the new
   script itself; the shell gate (``scripts/verify-shell-gates.sh`` or whatever
   lints shell scripts -- find it, do not guess); ``scripts/ci/docs-build.sh``
   for any ``.rst`` you touch; and, for the ``verify-all-gates.sh`` edit,
   ``bash scripts/verify-all-gates.sh --only NAME`` for **only** the gates you
   registered or whose floor you changed (``--list`` shows the names).
#. If unit 12 ends up touching Java or a POM -- it should not need to -- use
   ``bash scripts/dev-verify.sh`` (``--dry-run`` to see the selection,
   ``--mutation`` for incremental PIT). It is the inner-loop tool and is never
   a gate.
#. **Unit 12's own script must be cheap to run.** Each of its controls should
   build and test the *one* module its injection lands in, never the reactor
   and never through ``scripts/build.sh``. Use a closed module set (``-pl X
   -am``, and read why ``-am -amd`` is not closed in
   ``handoffs/SESSION-08-main-orchestrator.rst``). Record its total wall clock
   in the work log. A harness that costs another hour every time the gate suite
   runs is a defect in the deliverable, because it lands in
   ``verify-all-gates.sh`` and every future sign-off pays for it.
#. A failed run is re-run **only for the part that failed**, after a fix.

Saving time is never a reason to skip the evidence below: every control must be
seen to go red for a reason you chose. It is a reason to make each red cheap.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- *Roles*, *Sign-off*, *What parallel agents actually
   share*.
#. ``CONTRIBUTING.rst`` -- conventions.
#. ``handoffs/PHASE-05-handoff.rst`` -- the 2026-09-18 handoff, written at tier
   1 for you. Its *The first thing to do* is replaced by this brief where they
   differ (the baseline step, above).
#. ``handoffs/PHASE-05-worklog.rst`` -- **the source of unit 12.** Every
   injection unit 12 should encode is recorded there, unit by unit, with the
   failure text it actually produced. Read units 1-11 and 13 sign-off entries
   in full.
#. ``handoffs/PHASE-05-BRIEF.rst`` -- the original brief; its definition of
   unit 12 ("assembled from the injections") is still in force.
#. ``scripts/verify-all-gates.sh`` and one existing sibling harness, e.g.
   ``scripts/verify-provenance-gates.sh`` -- unit 12 must match their shape,
   control numbering and floor conventions.

What unit 12 is
===============

``scripts/verify-install-gates.sh``: a falsifiability harness for this phase's
gates, assembled from the injections in the work log, registered additively in
``scripts/verify-all-gates.sh`` with a floor equal to the number of controls it
grades. Each control injects a defect into **production** code, runs the
narrowest check that should catch it, asserts it went red **with the expected
diagnostic** (not merely non-zero), and restores from a checksummed backup.

Items the work log has already routed to unit 12, which you must handle:

* **Unit 11's documentation-table generator** has five hand-demonstrated
  injections and no automated harness. Encode them. Each control asserts the
  **inner** diagnostic, not the outer ``ExtensionError`` sentence, which is
  identical for every rejection and would pass on a defect nobody injected.
* **Do not build a control from neutering** ``ArtefactVerifier``'s **SHA-256
  comparison expecting a red** from the old tests -- see the unit-10 sign-off
  entry. Grade the phase-sequence assertion ``[DOWNLOADING, FAILED]`` that
  closed it, or argue why not.
* **A disconnected UI control costs ten minutes to detect** with a no-op
  handler (``TERMINAL_TIMEOUT_SECONDS`` is 600). Use
  ``install.setDisable(true)``, which fails immediately.
* **The stale** ``workflows`` **floor**: ``scripts/verify-all-gates.sh`` has
  ``GATE_FLOOR=9`` for a harness that now grades 23. Raising it to the true
  count is in scope of the approved additive edit. Raising is fine; lowering
  any floor is a rejection.

The draft at tag ``phase05-unit12-unfinished``
==============================================

640 lines, unreviewed, unrun, unsigned, by an agent who cannot be asked why it
is shaped as it is. Tier 1's direction: **read it, then write unit 12 from the
work log.** You may lift pieces of it that you have read and understood, but
every control you keep is held to the same standard as one you wrote --
seen red for a reason you chose. Record the decision in the work log before the
unit agent starts. Do not merge or check out the tag onto ``main``.

Standing rules
==============

* **One unit agent at a time.** You spawn one fresh phase agent for unit 12;
  if it needs rework, it goes back to that agent or a fresh one, serially.
* **Signing off unit 12** means you read the diff, run the harness yourself,
  and inject at least one defect the unit agent did not try -- including one
  into the **harness**: make a control's injection silently fail to land and
  confirm the harness reports that, not a pass. An injection is only evidence
  if the edit is proven to have landed (anchor matches exactly once; marker
  grepped back; restore verified with ``sha256sum -c``). Use a private
  scratchpad subdirectory.
* Never weaken a gate, a checksum, a validation rule or a threshold. Never add
  to the pinned survivor set in ``scripts/verify-test-gates.sh``.
* Commit with an explicit pathspec, never ``git add -A``. Do not push. Never
  force-push.
* All documentation is reStructuredText and must pass
  ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner, answer a ``D-`` item, or edit
  ``STATUS.rst``/``DECISIONS.rst``. The ``TIMED_OUT`` counting question
  (``status-mutation-drift``) is the owner's; do not touch how
  ``scripts/build.sh`` counts mutations.

Not yours
=========

Gate item 9 / the macOS runner (tier 1 dispatches it); the phase exit-gate
re-run (tier 1); the nightly's step ordering (Phase 01 residue); the
``StreamPump`` ``read > 0`` test gap (Phase 03's module); the ``Locale``
test classes.

Report back
===========

When unit 12 is accepted -- or if it stalls -- update
``handoffs/PHASE-05-handoff.rst`` for a successor and report to tier 1: the
commit, the controls (each with the diagnostic it asserts), the harness's wall
clock, the injection you added at sign-off and what it produced, and anything
escalated. Keep the report short and factual; every claim will be re-checked
rather than read. Escalate a blocker as soon as you hit it.
