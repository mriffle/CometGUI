====================================================================
PHASE-05 handoff -- Tool Registry and Installer (to a successor)
====================================================================

:Phase: 05
:Written: 2026-09-18
:Written by: **The main orchestrator (tier 1), not the phase orchestrator.**
   That matters and is not a formality -- see :ref:`p05h2-who`.
:Outcome: **INCOMPLETE. Units 1-11 and 13 accepted; unit 12 unfinished.**
   Stopped by owner instruction mid-unit-12, not because anything is wrong.
:Supersedes: the handoff of 2026-09-03 (units 1-7), in git history at
   ``874bb23``. Treat any copy of that you find as stale.
:Records: ``handoffs/PHASE-05-worklog.rst`` -- **this document is the map; the
   work log is the proof**, and for units 8-13 it is the *only* proof.

.. contents:: Contents
   :depth: 2
   :local:

.. _p05h2-who:

Read this first: who verified what
===================================

The two phase orchestrators that ran this phase signed off every unit they
accepted -- reading the diff, re-running the build and injecting a defect the
unit had not tried. Their evidence is in the work log, with the exact failure
text each injection produced.

**Tier 1 has independently verified none of units 8 through 13, and has not
re-run the phase exit gate.** The phase was stopped before it reported, so the
sign-off that normally follows never happened. Everything in
:ref:`p05h2-gates` below is therefore **the phase's own record**, faithfully
transcribed, and **not a tier-1 verification**. A successor inherits claims
backed by a work log, which is a good deal more than nothing and a good deal
less than a signed-off phase.

Do not promote any gate item on the strength of this document.

Where the phase stopped
=======================

The owner stopped the phase on **2026-09-18**, mid-unit-12, having already
asked for it to finish unit 12 first and then revised that to stopping
immediately. Both agents were stopped cleanly:

* the **phase orchestrator**, whose last action was waiting for unit 12's
  commit;
* the **unit 12 agent**, whose last words were *"I have every injection
  reconnoitred and biting. Now I'll write the harness."*

Nothing was lost and nothing was left half-committed.

What is in the tree, and what it is worth
------------------------------------------

Nothing. ``main`` is clean, and that is deliberate.

Unit 12's draft -- ``scripts/verify-install-gates.sh``, 640 lines -- is
preserved **under a tag and kept off** ``main``, exactly as the cancelled unit
8's work was preserved at ``phase05-unit8-cancelled``, so that unsigned work
does not accumulate in the tree::

    git show phase05-unit12-unfinished:scripts/verify-install-gates.sh

**It is unreviewed, unrun and unsigned.** It parses (``bash -n``) and contains
around sixty control/injection references, so it is substantially written rather
than a stub -- but nobody has read its diff, nobody has run it, and no control
in it has been seen to go red. **Treat it as a draft by an author who cannot be
asked questions**, in exactly the way your predecessor was told to treat the
``phase05-unit8-cancelled`` tag.

Two honest options, and the choice is the successor's:

#. **Finish it as unit 12**, which means reading it as if you had written
   nothing, running it, and injecting a defect into each control so you have
   seen each one bite. Its agent had reconnoitred the injections; that
   reconnaissance is not in the tree.
#. **Delete it and write unit 12 from the work log**, which is where the
   injections it should encode actually live, recorded with their failure text
   unit by unit. That is what the original brief said unit 12 should be
   assembled from.

What is NOT in the tree is any record of why it is shaped the way it is. That
died with its agent.

Units, and who accepted them
============================

.. list-table::
   :header-rows: 1
   :widths: 6 30 20 44

   * - #
     - Unit
     - State
     - Accepted at / note

   * - 1-7
     - Manifest, downloader, verification, extraction, atomic install,
       probes, tool adapters
     - **Accepted**
     - Two orchestrators, 2026-09-02/03. Five of seven sent back at least once.

   * - 8
     - Tool Manager runtime behind the domain port
     - **Accepted**
     - ``7bab20d``, after one round. Fixed the ``ToolOffer`` download-size gap
       and the cancel-mid-download defect.

   * - 9
     - Tool Manager UI section and wiring
     - **Accepted**
     - ``1f35c25``, after two rounds.

   * - 10
     - End-to-end install through the real UI, and the PDV
       cancel-and-restart
     - **Accepted**
     - ``437ba32``, no rework.

   * - 13
     - macOS Gatekeeper attempt
     - **Accepted as AUTHORED AND NEVER RUN**
     - ``ca33d0d``. **Gate item 9 remains NOT MET** and the phase said so
       plainly rather than letting an unrun check read as a pass.

   * - 11
     - Documentation, with the tables generated from ``manifests/tools.json``
     - **Accepted**
     - ``9a33bb4``, no rework.

   * - 12
     - ``scripts/verify-install-gates.sh``
     - **UNFINISHED**
     - Untracked draft in the tree. See above.

The cancellation defect your predecessor inherited **was reproduced and fixed**:
the reproduction is at ``68f182d`` and the fix landed inside unit 8.

.. _p05h2-gates:

The nine exit gate items -- THE PHASE'S RECORD, NOT A VERIFICATION
==================================================================

Re-read :ref:`p05h2-who` before using this table. Each row is what the phase
recorded; none has been re-run by tier 1.

.. list-table::
   :header-rows: 1
   :widths: 5 22 73

   * - #
     - Phase's record
     - What it rests on

   * - 1
     - Claimed met
     - Units 8, 9 and 10. Unit 10 drove the install through the real Tool
       Manager UI, which is what the item's wording demands.
   * - 2
     - Claimed met
     - Proved at the installer over all four artefact kinds in unit 3; the UI
       half by unit 10.
   * - 3
     - Met (unit 4)
     - 12 attacks x 4 multi-entry kinds; a real upstream artefact containing a
       traversing path installs safely and is rejected whole.
   * - 4
     - Met (unit 5)
     - Interruption in a real second JVM after each of the eight steps; two
       JVMs serialise with one observed to wait. **Cancellation within a step
       was the hole, and unit 8 closed it.**
   * - 5
     - Claimed met
     - Unit 6 at the probe; the offered-set half by unit 8.
   * - 6
     - Met (unit 7)
     - Thermo DLL capability graded both ways.
   * - 7
     - Claimed met
     - Unit 7 at the adapter; the port half by unit 8.
   * - 8
     - Claimed met
     - Manifest and selection in unit 2; the UI half by unit 9.
   * - 9
     - **NOT MET**
     - **No macOS binary has ever been executed anywhere in this project.**
       Unit 13 is authored and has never run. See :ref:`p05h2-debts`.

**Expected grade remains ``PARTIAL``**, on gate item 9 alone.

.. _p05h2-debts:

Tier 1's own debts, which are not the phase's to pay
=====================================================

#. **Gate item 9 needs a macOS runner.** Unit 13 is written and ``release.yml``
   already declares ``macos-latest``, so runners are reachable. Reaching one
   means pushing a branch and dispatching a workflow, which is tier 1's to do.
   **Its negative control is mandatory**: a ``curl`` download sets no
   ``com.apple.quarantine``, so the job must set the attribute itself and show
   that leaving it set produces a refusal. If the control does not bite, the
   result is "this check cannot go red" and is reported as such, never as a
   pass.
#. **The phase exit gate has never been re-run by tier 1.** That is the
   sign-off, and it is roughly 88 minutes of machine time:
   ``scripts/build.sh`` then ``scripts/verify-all-gates.sh``, in full, on a
   quiet tree.
#. **The ``TIMED_OUT`` counting question** (``STATUS.rst``,
   ``status-mutation-drift``). ``scripts/build.sh`` scores the 80% mutation
   gate on ``status='KILLED'`` alone while PIT counts ``TIMED_OUT`` as
   detected, so the gated number moves with machine load. **This is a
   gate-semantics decision and no agent should take it alone.**

Residue, named
==============

* **Unit 11's generator has no automated falsifiability harness.** Its five
  validation injections were demonstrated by hand and are reproducible from the
  recipe in the unit's report, but nothing re-runs them. Unit 11 called unit 12
  its natural home. A control over the generator must assert the **inner**
  diagnostic, not the outer ``ExtensionError`` sentence, which is identical for
  every rejection and would pass on a defect nobody injected.
* **A test gap in** ``cometgui-process``, **which is Phase 03's module, not
  this one's.** ``StreamPump.run:96`` is ``while (read >= 0)``; the surviving
  mutant makes it ``while (read > 0)``. Those differ **only when** ``read()``
  **returns 0** -- an ``InputStreamReader`` decoding a multi-byte character
  split across a read boundary. ``StreamPumpTest`` has eleven tests and none
  forces that, so the mutant is killed by accident of stream chunking or not at
  all. This phase's signature shape once more: a rule graded at one point on an
  axis it does not depend on.
* **Six test classes mutate global JVM state** through ``Locale.setDefault``.
  Safe today -- no parallel configuration anywhere -- but the moment anyone
  enables parallel execution they need ``@ResourceLock(Resources.LOCALE)``.

Decisions not to re-litigate
=============================

Unchanged from the 2026-09-03 handoff and still binding: the UI may not see the
installer; one JSON reader, one hasher, one process launcher, one redactor;
``NSIS_PAYLOAD`` is not implemented and must not be (``D-002`` option C);
``AtomicMoveNotSupportedException`` is re-thrown, never handled;
``scripts/ci/nightly-manifest-verify.sh`` stays a stub that exits non-zero;
``cometgui-tools``'s mutation switch stays ON; and **nothing is ever added to
the pinned survivor set in** ``scripts/verify-test-gates.sh`` **to make a build
pass.**

The first thing to do
=====================

#. Read ``handoffs/PHASE-05-worklog.rst`` in full. For units 8-13 it is the
   only evidence that exists, and it is what makes those sign-offs checkable
   rather than assertions from strangers.
#. Decide what to do with the ``phase05-unit12-unfinished`` draft -- finish it
   or replace it -- and record the decision before touching it. My own
   recommendation, and it is only that: **read it, then write unit 12 from the
   work log anyway.** The work log holds the injections unit 12 is supposed to
   encode, each with the failure text it actually produced; the draft holds an
   unknown author's idea of them, and you cannot ask it which injections it
   had reconnoitred.
#. Take a baseline on a quiet tree: ``bash scripts/build.sh`` then ``bash
   scripts/verify-all-gates.sh``. Note that PIT's thread count changed on
   2026-09-18, so the ``gates`` stage should now be around 647s rather than
   1056s; if it is not, say so, because nobody has confirmed that figure on a
   second machine.
#. Do **not** promote any gate item on the strength of this document.
