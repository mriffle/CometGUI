==================================================================
PHASE-05 unit-14 brief -- the macOS quarantine fix-up does nothing
==================================================================

:Phase: 05 -- Tool Registry and Installer
:Tier: 1 to the phase orchestrator
:Date: 2026-10-01
:Ordered by: the owner, 2026-10-01: *"fix the macOS quarantine bug as a new
   unit"*
:Unit: **14** (new; numbers 1-13 are unchanged)

.. note::

   Written and owned by tier 1. Do not edit. Everything in
   ``handoffs/PHASE-05-UNIT12-BRIEF.rst`` still applies, **in particular its
   build-economy section**: no full ``scripts/build.sh`` or
   ``scripts/verify-all-gates.sh`` run by you or your unit agent. Tier 1 runs
   both once, at the exit gate.

What the macOS runner showed
============================

Tier 1 dispatched ``macos-gatekeeper.yml`` on 2026-10-01 at ``49423fb``:
https://github.com/mriffle/CometGUI/actions/runs/36918810975 (``macos-latest``,
Apple silicon, Temurin 21.0.12.1). The first macOS execution in this project's
history. Two findings:

#. **The negative control did not bite.** The binary carrying
   ``com.apple.quarantine`` ran and printed Comet's banner. The script reported
   ``INCONCLUSIVE -- THIS CHECK CANNOT GO RED ON THIS MACHINE`` (exit 2), as
   designed. Gate item 9 stays NOT MET. **Not this unit's to solve.**
#. **The product's quarantine removal is a no-op on macOS** -- this unit. In
   the transcript (step 1, step 3):

   * ``/usr/bin/xattr -p`` reads ``com.apple.quarantine`` on the file before
     and **after** ``PlatformFixups`` ran.
   * The product's own Java view (``UserDefinedFileAttributeView``) reports
     ``probe.attrs.count=0`` -- it does not see the attribute at all -- and
     ``FixupReport.quarantineCleared()`` is empty, ``changedNothing=true``.

   So ``R-PLAT-04`` is **not delivered on the one platform it exists for**, and
   ``PlatformFixups``'s class comment asserts the premise that failed:
   *"Linux stores the attribute under the user. namespace while macOS stores it
   raw, so the code that lists and deletes it is the same code."* Shape 3 of
   the signature defect: proved through a stand-in production does not use.

   Tier 1 has not diagnosed *why* the JDK view hides it. Find out from the JDK
   sources for the macOS ``UserDefinedFileAttributeView`` implementation, not
   by guessing, and record what you find with a reference.

The transcript is saved at the run as artifact ``macos-gatekeeper-transcript``.

What unit 14 must deliver
=========================

#. **A fix in** ``PlatformFixups`` that removes ``com.apple.quarantine`` on
   macOS and reports truthfully what it removed. The approach is the unit's to
   choose and argue, within the standing decisions: one process launcher (if
   the fix runs ``/usr/bin/xattr``, it goes through the project's launcher and
   the ArchUnit layering rules, not a raw ``ProcessBuilder``); failures are
   reported, never swallowed; **files the fix cannot clear are reported as such**,
   so a silent no-op cannot recur.
#. **The class comment and its test corrected** so neither claims the Linux run
   proves macOS behaviour. The Linux test stays as what it is (proof of the
   Linux branch), labelled honestly.
#. **The macOS script grades the fix-up on its own.** Today
   ``scripts/ci/macos-gatekeeper-verify.sh`` treats everything after a
   non-biting control as uninterpretable -- true of Gatekeeper, **false of the
   attribute**, which ``xattr -p`` reads directly whatever Gatekeeper does. Add
   a verdict that fails (non-zero, distinct exit code, words in the transcript)
   when the attribute is still present after the product's fix-up, independent
   of the Gatekeeper control. Its ``--self-test`` must prove that branch bites.
   **That is a check that can go red on a hosted runner**, which is the point.
#. **A control in** ``scripts/verify-install-gates.sh`` for whatever part of
   the fix runs on Linux, and a floor raised to match. Never lowered.
#. **Stop at the edge of macOS.** When the branch is ready, commit it on
   ``main`` locally (do not push) and report to tier 1. **Tier 1 pushes a
   branch and dispatches the workflow**, then sends you the result. The unit is
   not accepted until the runner shows ``xattr`` no longer finds the attribute
   after the product's fix-up. If it does not, the unit goes back.

Gate item 9 itself (Gatekeeper accepts the binary) remains NOT MET whatever
this unit does; a hosted runner did not refuse a quarantined binary, so it
cannot show acceptance either. Do not report otherwise.

Verification economy for this unit: ``bash scripts/dev-verify.sh`` (with
``--mutation``, since ``cometgui-install`` is mutation-gated), the macOS
script's ``--self-test``, ``bash scripts/verify-all-gates.sh --only install``
and ``scripts/ci/docs-build.sh``. Nothing wider.
