====================================================================
PHASE-05 handoff -- Tool Registry and Installer (to a successor)
====================================================================

:Phase: 05
:Written: 2026-09-18 by tier 1; **updated 2026-10-01** by the fourth phase
   orchestrator after unit 12 -- see :ref:`p05h2-unit12`
:Written by: **The main orchestrator (tier 1), not the phase orchestrator.**
   That matters and is not a formality -- see :ref:`p05h2-who`.
:Outcome: **All fourteen units accepted by the phase orchestrators** (unit 12
   at ``534c5b6``, unit 14 at ``f097997`` on macOS run 36930720796, both
   2026-10-01). **Expected grade PARTIAL, on gate item 9 alone.** Still
   unsigned by tier 1: the exit gate is tier 1's to re-run. See
   :ref:`p05h2-final`.
:Supersedes: the handoff of 2026-09-03 (units 1-7), in git history at
   ``874bb23``. Treat any copy of that you find as stale.
:Records: ``handoffs/PHASE-05-worklog.rst`` -- **this document is the map; the
   work log is the proof**, and for units 8-13 it is the *only* proof.

.. contents:: Contents
   :depth: 2
   :local:

.. _p05h2-final:

Final report of the phase orchestrator, 2026-10-01
===================================================

**All fourteen work units are accepted.** Units 1-11 and 13 by earlier
orchestrators, unit 12 (``534c5b6``) and unit 14 (``f097997``) by me; every
sign-off is in ``handoffs/PHASE-05-worklog.rst`` with what was run and what was
seen. The work log is the proof; this section is the map.

.. list-table:: Units
   :header-rows: 1
   :widths: 6 44 50

   * - #
     - Unit
     - Accepted
   * - 1-7
     - Domain vocabulary, manifest, downloader, extraction, atomic install,
       probes, tool adapters
     - 2026-09-02/03, five of seven after rework
   * - 8
     - Tool Manager runtime behind the port
     - ``7bab20d``, one round
   * - 9
     - Tool Manager UI and wiring
     - ``1f35c25``, two rounds
   * - 10
     - End-to-end install through the UI; PDV cancel and restart
     - ``437ba32``, no rework
   * - 11
     - Documentation, tables generated from the manifest
     - ``9a33bb4``, no rework
   * - 12
     - ``scripts/verify-install-gates.sh``, registered in
       ``verify-all-gates.sh``
     - ``534c5b6``, no rework
   * - 13
     - macOS Gatekeeper job, authored
     - ``ca33d0d``; first run 36918810975 on 2026-10-01
   * - 14
     - macOS quarantine removal through ``/usr/bin/xattr``
     - ``f097997``, one round; accepted on macOS run 36930720796

.. list-table:: The nine exit gate items -- THE PHASE'S OWN CLAIMS, for tier 1 to re-run
   :header-rows: 1
   :widths: 5 15 80

   * - #
     - Phase's claim
     - Evidence, and the check that re-proves it
   * - 1
     - Met
     - Unit 10: four tools installed from an empty cache by pressing the Tool
       Manager's own controls (``ToolManagerInstallUiTest``), PDV cancelled
       mid-transfer and restarted (``PdvCancelAndRestartUiTest``).
       Falsified by ``verify-install-gates.sh`` controls 2, 3, 4, 5.
   * - 2
     - Met
     - Unit 3 at the installer, unit 10 through the UI with a recording
       process runner showing nothing launched. Controls 6 and 7.
   * - 3
     - Met
     - Unit 4: each attack per multi-entry kind, the real ``../`` upstream zip
       rejected whole. Controls 8 and 9 grade the absolute-path and XXE
       halves; **traversal, symlink and bomb have tests but no harness
       control**.
   * - 4
     - Met
     - Unit 5: interruption in a real second JVM after each of the eight
       steps; unit 8: cancellation inside a transfer. Control 10.
   * - 5
     - Met
     - Units 6 and 8: the ``R-PLAT-03`` diagnostic with alternatives; a tool
       failing loadability not offered. Controls 11, 12, 13.
   * - 6
     - Met
     - Unit 7: Thermo DLL capability graded both ways. Control 14 (new at
       unit 12).
   * - 7
     - Met
     - Unit 7: below-3.05 rejected, a valid binary registered. Control 15 (new
       at unit 12). The interface has no registration action (named residue).
   * - 8
     - Met
     - Units 2 and 9: selection from the manifest, offered set driven from it.
       Control 16.
   * - 9
     - **NOT MET**
     - Two macOS runs (36918810975, 36930720796): the Gatekeeper negative
       control **did not bite** on a hosted runner -- a quarantined binary ran
       -- so the verdict is ``INCONCLUSIVE``, and acceptance cannot be shown
       either. What run 36930720796 **does** show is ``R-PLAT-04``: the
       product's fix-up removes ``com.apple.quarantine`` and says so
       (``ATTRIBUTE CLEARED``). That is a precondition of item 9, not item 9.

**Expected grade: PARTIAL, on gate item 9 alone.** Item 9 is unverified
behaviour on a platform this project can reach only through a hosted runner
that does not enforce Gatekeeper as a clean end-user Mac does; no amount of
work inside this phase changes that.

**How to re-run the claims.** ``bash scripts/build.sh`` (11 stages; tier 1
measured 1136 s at ``49423fb``), then ``bash scripts/verify-all-gates.sh``
in full -- now with the ``install`` control at floor **88** (about four and a
half minutes) and ``workflows`` at **23**. Neither was run by me in full, by
the owner's build-economy instruction; every narrower check I ran is in the
work log.

**Residue, named.**

* Gate item 3's traversal, symlink and bomb attacks: tested, not in the
  harness.
* The ``[DOWNLOADING, FAILED]`` assertion does not catch unit 10's
  ``ArtefactVerifier`` neutering, contrary to the work log's unit-10 entry and
  the test's own comment; not a product hole (``R-SEC-02`` is stated three
  times). Escalated.
* ``scripts/dev-verify.sh --mutation`` fails on PIT 1.30.0's history flags.
  Escalated; tier 1 has it.
* Stale "no macOS binary has run" wording in ``scripts/verify-all-gates.sh``
  and ``STATUS.rst``; ``docs/developer/testing.rst`` lists neither the
  ``provenance`` nor the ``install`` control. Tier 1's files.
* Carried from earlier units and unchanged: the ``StreamPump`` zero-read gap
  (Phase 03), six ``Locale.setDefault`` test classes, no Refresh action, no
  local-binary registration action in the interface, a ``FAILED`` row carries
  no reason, ``offers()`` launches processes on the interface thread (353 ms
  measured), and the ``TIMED_OUT`` counting question, which is the owner's.

.. _p05h2-unit12:

Update of 2026-10-01: unit 12 accepted, and what is left
=========================================================

The owner resumed the phase at unit 12 on 2026-10-01 under
``handoffs/PHASE-05-UNIT12-BRIEF.rst``. One fresh unit agent wrote
``scripts/verify-install-gates.sh`` from the work log (the
``phase05-unit12-unfinished`` draft was read and not used as the starting
point; it is **1292 lines, not 640** as this document said below) and
registered it additively in ``scripts/verify-all-gates.sh``. Accepted at
``534c5b6`` with no rework; the evidence is the work log's
:ref:`p05-u12-signoff`, and the decision record before dispatch is
:ref:`p05-u12-draft-ruling`.

* ``bash scripts/verify-all-gates.sh --only install`` -> ``PASS install: 83
  controls in 248s``, items ``1,2,3,4,5,6,7,8``. **The harness costs about
  four minutes.** Each control builds one closed module set (``-pl X -am``).
* Floors in ``scripts/verify-all-gates.sh``: ``workflows`` **9 -> 23**
  (measured); ``install`` **new, 83**. None lowered.
* Items 6 and 7 are graded by two controls **new at unit 12** -- the record
  had no injection for either -- labelled so in the harness.
* **Item 9 remains NOT MET.** Control M only requires the gatekeeper driver
  to keep saying so.

**What is left, all of it tier 1's:** the macOS runner for gate item 9; the
phase exit-gate re-run (``scripts/build.sh`` then ``scripts/verify-all-gates.sh``
in full, now about four minutes longer than before); the ``TIMED_OUT``
counting question. **No baseline was taken in this session, by instruction**,
so tier 1's exit-gate run is the first full run since 2026-09-17.

**One correction to the record, escalated:** the ``[DOWNLOADING, FAILED]``
assertion unit 10 added does **not** catch its ``ArtefactVerifier``
neutering, as the work log claimed -- reproduced at unit 12 sign-off. Not a
product hole (``R-SEC-02`` is stated three times); a record and a test comment
that overstate. Control 7 grades the defect that assertion really does catch.

**Unit 14, added 2026-10-01 by owner order** (``handoffs/PHASE-05-UNIT14-BRIEF.rst``):
the first macOS run (36918810975) showed ``PlatformFixups`` removing nothing,
because the JDK's macOS attribute view prefixes ``user.`` to every name. The fix
-- ``/usr/bin/xattr`` through the process service, re-checked, failures named --
is signed off locally at ``f097997`` and **waits on tier 1's next macOS run**;
it is not accepted until that run reads ``ATTRIBUTE CLEARED``. Install floor now
88. See the work log's :ref:`p05-u14-signoff`.

The sections below are the 2026-09-18 text, unchanged except where this
update supersedes them.

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
     - **Accepted** (2026-10-01)
     - ``534c5b6``, no rework. 83 controls, 248 s. See :ref:`p05h2-unit12`.

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

* *Closed 2026-10-01:* unit 11's generator now has its harness -- control G
  of ``scripts/verify-install-gates.sh``, each of the five injections graded on
  its own inner diagnostic, one through the Sphinx hook.
* **Gate item 3's traversal, symlink and bomb attacks have no injection** in
  the harness; only the absolute-path and XXE halves are graded there.
* ``docs/developer/testing.rst``'s falsifiability section still says the
  aggregate "runs all ten" and names neither ``provenance`` nor ``install``.
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

*Superseded on 2026-10-01 for steps 2 and 3:* the draft was decided (step 2,
:ref:`p05-u12-draft-ruling`) and **no baseline is to be taken** (step 3) --
the owner's build-economy instruction. What remains is tier 1's exit-gate run
and the macOS runner, :ref:`p05h2-unit12`.

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
