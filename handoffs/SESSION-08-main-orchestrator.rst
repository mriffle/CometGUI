==================================================
SESSION-08 -- main orchestrator record
==================================================

:Session: 08
:Tier: 1 -- main orchestrator
:Started: 2026-09-17
:Status: **CLOSED** 2026-09-18. Phase 05 restarted at unit 8 on the owner's
   instruction, ran units 8-11 and 13, and was stopped mid-unit-12 by the owner
:Authoritative state: ``STATUS.rst``, always. This file records *how the
   session went*, not where the project is.

.. contents:: Contents
   :depth: 2
   :local:

What this session did
=====================

#. **Restarted Phase 05 at unit 8** on the owner's instruction, fourteen days
   after it was stopped after unit 7. One fresh phase orchestrator, briefed by
   ``handoffs/PHASE-05-RESUMPTION-BRIEF.rst``, the only phase live in the tree.
#. **Cut the slowest stage of the build by 39%** after the owner said, twice in
   two days, that build wall clock was costing too much. See
   :ref:`status-pit-threads`.
#. **Built** ``scripts/dev-verify.sh``, the inner-loop check, and proved it.
#. **Stopped the phase on instruction** and wrote its handoff at tier 1,
   because the phase orchestrator was stopped before it could write its own.

The phase itself, in the orchestrator's hands, accepted units 8, 9, 10, 13 and
11 -- three of them after rework -- and reproduced and fixed the
cancel-mid-download defect it inherited. Unit 12 was unfinished at the stop.

Decisions taken at tier 1, with their reasoning
===============================================

* **Ship the PIT thread change; do not ship the thing I first built to
  "fix" it.** Raising PIT's threads cut ``gates`` from 1056s to 647s. A first
  pass concluded the mutation drift it exposed was thread-caused and pinned
  ``cometgui-process`` back to four threads; that conclusion was wrong and the
  pin came out. See below.
* **Do not change how the mutation gate counts.** ``scripts/build.sh`` scores
  the 80% gate on ``status='KILLED'`` alone while PIT counts ``TIMED_OUT`` as
  detected. Aligning them would be defensible and is **not an agent's call**.
  Recorded at ``status-mutation-drift`` so it is decided rather than
  discovered.
* **A granular runner is never a gate.** ``dev-verify.sh`` prints what it did
  not run, refuses to be selective when the root POM changed, and treats a
  Maven run that exits 0 having run zero tests as red.
* **Take the measurement window by asking the phase to hold**, rather than
  racing it. The phase confirmed it was holding and handed over its baseline as
  the "before" number.

What tier 1 got wrong, and it is the useful half
=================================================

**Four errors, all found by running something rather than reading it.**

#. **I concluded the mutation drift was caused by the thread count, on a
   control that was too clean.** A single-module run at four threads reproduced
   the baseline byte for byte, which looked like proof. A full reactor run with
   ``cometgui-process`` pinned back to four threads drifted anyway **and took
   ``cometgui-install`` with it**, 1294 vs 1295 -- exactly what the phase
   orchestrator had predicted and I had discounted. *The lesson: a control that
   removes the conditions under which the effect appears does not test for the
   effect.* The pin, and the rationale I had written into the POM, were removed
   rather than left standing on a falsified conclusion.
#. **My collision guard cried wolf, twice, and then missed the real thing.**
   ``build\.sh`` also matches ``docs-build.sh``; matching whole command lines
   matched stale shells that merely *mentioned* the path. Worse, it called the
   tree quiet at 01:26:08 while ``verify-all-gates.sh`` still had 173 seconds
   to run, **and a timing was taken across that overlap and had to be thrown
   away**. A suite that builds is a build. It scans argv token by token now.
#. **My module selector built an incomplete reactor.** ``-pl X -am -amd`` is
   not a closed set: it adds X's dependents but not *their* other dependencies,
   so Maven resolved those from a repository this build never writes to, and
   ``StagedJavaToolProbeTest`` died with ``NoClassDefFoundError`` -- **a test
   failure in code nobody had touched.**
#. **My test counter never counted.** Surefire writes
   ``tests/errors/skipped/failures``; the parser assumed
   ``tests/failures/errors``, matched nothing, and reported "0 tests" for a run
   of 3532 -- which would have fired the zero-tests-is-red branch on every
   healthy run. Fail-safe rather than fail-open, but wrong, and it would have
   taught its first user to distrust the tool.

And one that cost only seconds but is worth the line: **an illegal ``--`` inside
an XML comment** made ``pom.xml`` non-parseable and killed a build in 1.5
seconds. Every POM is parsed before Maven is invoked now.

What the next main orchestrator inherits
=========================================

* **Phase 05 is INCOMPLETE and UNSIGNED.** Units 1-11 and 13 accepted by the
  phase orchestrators; unit 12 unfinished, with an untracked 640-line draft of
  ``scripts/verify-install-gates.sh`` in the tree that **nobody has read, run
  or signed**. ``handoffs/PHASE-05-handoff.rst``.
* **Tier 1 has verified none of units 8-13 and has not re-run the exit gate.**
  The phase was stopped before it reported. Everything in that handoff's gate
  table is the phase's record, not a verification.
* **Three tier-1 debts**, listed at ``p05h2-debts``: the macOS runner for gate
  item 9, the ~88-minute phase sign-off, and the ``TIMED_OUT`` counting
  decision.
* **The nightly has failed every night since 2026-08-31** and its first failing
  stub still skips every later step. Untouched this session; Phase 01's
  residue.

The one method worth carrying forward
======================================

**Take the control before you take the measurement, and make the control run
under the conditions the effect needs.** Every real finding this session came
from a comparison that could have come out either way: 2895 mutations compared
status by status before and after; a decoy process proving a pattern fix; a
reactor whose test count had to match an independently produced 3532. Every
error came from a check that could only have come out one way -- a control run
in isolation, a parser that matched nothing, a guard that watched for the wrong
process.

The project's signature defect is a check that cannot go red. This session's
contribution is the reminder that **an agent's own instruments have that defect
too**, and that the way to find it is the same: make the thing you are
measuring with go wrong on purpose, and see whether it notices.
