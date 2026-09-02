=================================================
Main orchestrator session handoff -- session 03
=================================================

:Session: 03 -- main orchestrator (tier 1)
:Dates: 2026-08-30 to 2026-08-31
:Ended: after Phase 02 was signed off ``PASSED``, before Phase 03 was
   dispatched
:Reason for stopping: the owner chose to run Phase 03 in a fresh session
:Phases signed off: 02 (``PASSED``)

This is a *session* handoff, not a phase handoff. Phase records live in
``handoffs/PHASE-02-worklog.rst`` and ``handoffs/PHASE-02-handoff.rst``. The
authoritative project state is ``STATUS.rst``; if this file and ``STATUS.rst``
ever disagree, ``STATUS.rst`` wins.

.. contents:: Contents
   :depth: 2
   :local:


Read this first
===============

**There are unpushed commits, and this session could not push either.** Check
before anything else::

    git log --oneline origin/main..HEAD

At the moment this file was written there were 71. This is now the **third
consecutive session with no GitHub credential** -- no ``gh``, no ``GH_TOKEN``,
no SSH key, no credential helper; ``git push`` fails with ``could not read
Username``. The owner pushes by hand. **Check, do not assume**; it may be true
for you and it may not.

Two gate items are blocked on that push and on nothing else. See
:ref:`s03-push`.


What this session did
=====================

#. Read in ``ONBOARDING.rst``, ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/index.rst``, the session 02 handoff and ``PHASE-02-app-shell.rst``.
#. Found and repaired a **contradiction inside the specification** before
   dispatching anything -- see :ref:`s03-rsec01`. Specification revision 10.
#. Dispatched **two phase orchestrators concurrently** on disjoint file sets:
   Phase 02, and one for the Phase 00 / Phase 01 residue. Both completed.
#. **Signed off Phase 02 ``PASSED``** after re-running all five gate items and
   injecting its own defects. Four held; the fifth exposed a real gap that was
   returned as a repair unit and re-verified. See :ref:`s03-p02`.
#. **Amended and strengthened Phase 00 gate item 8**, which its own work unit
   had escalated as ``E1`` and which no session had acted on.
#. Signed off the Windows verification harness as **prepared, not closed**.

The three-tier model held. No tier did the tier below's job, and the main
orchestrator wrote no phase code.


.. _s03-rsec01:

The specification contradicted itself, and it mattered that day
================================================================

``R-SEC-01``'s normative text still read *"No CasanovoGUI source shall be
copied into the CometGUI repository until ``D-001`` is resolved"*, and the
paragraph above it still asserted as present fact that upstream *"still exposes
no licence -- verified 2026-08-28"*. Both were overtaken on 2026-08-29 when
CasanovoGUI published GPL-3.0 and the owner decided to derive from it.
Revision 6's change-log entry recorded the lift; **nobody amended the rule
text**, so the document contradicted both ``DECISIONS.rst`` and its own change
log for four revisions.

It was found while the Phase 02 orchestrator was reading the file. Phase 02
delivers ``R-SEC-01`` and is the first phase to reuse CasanovoGUI source in
earnest, so an agent grepping the rule rather than the change log would have
concluded reuse was forbidden and written the shell from scratch -- a phase of
wasted work, and the derivation machinery would never have been built.

**The general lesson for tier 1:** a decision is not recorded until the
*normative* text says so. A change-log entry is a note about a change, not the
change. When you close a ``D-`` item, amend every rule it touches in the same
commit.


.. _s03-p02:

Phase 02, and what sign-off caught
===================================

Signed off ``PASSED`` -- the first phase in this project to reach it rather
than ``PARTIAL``. Twelve work units. The full evidence table is
:ref:`status-p02`; this is what is worth carrying forward.

**Four gate items held against defects they had never seen.** The most
instructive was item 5: the console's flood test was attacked by retaining
every discarded message in a side list, so ``size()`` and ``discardedCount()``
stayed *exactly correct*. A count-only test passes that defect. It failed on
**retained heap** -- 222,050,704 bytes against a 33,554,432 bound -- because
the gate measures memory, which is what the item actually requires.

**Item 2 did not hold, and this is the finding to carry.** Renaming one
section's identifier in *production* code -- ``section-results`` to
``section-results-pane`` -- left the **entire build green**: every GUI test,
``UiIdsTest``, and ``build.sh`` at ``11/11 stages OK``. Two causes: the GUI
tests computed their expected identifier by calling the same helper the
production code calls, so the assertion was self-referential and could not
fail; and the literal-pinning test covered two of ten sections.

Repaired as unit 12 -- 119 identifiers pinned as hand-typed literals, adding a
constant fails until it is pinned -- and re-verified with injections the phase
had not used, including a brand-new enum constant.

.. important::

   **An assertion whose expected value is computed by the code under test
   cannot fail.** It survived a full phase of unit sign-offs, all of them
   otherwise rigorous, because everything it touched was green. Only an
   injection into production code found it.

   This is the **second** instance of this shape in the project. Phase 01's was
   an ArchUnit rule that passed 8/8 while checking nothing after a module left
   the classpath. Expect a third. When you sign off a gate, the question is not
   "is it green?" but "have I seen this go red for a reason I chose?"


The two PARTIAL grades, and why they are still PARTIAL
=======================================================

Both were held open by the missing remote. ``D-008`` supplied one on
2026-08-30, so both became ordinary phase work -- and both are now done and
verified locally, and **still not closed**, because no session has been able to
push and GitHub has executed nothing in this repository at all (Actions API:
``total_count = 0``).

A phase orchestrator built, on branch ``windows-percolator-verification``:

* ``.github/workflows/windows-percolator.yml`` and
  ``scripts/ci/windows-percolator-verify.sh`` -- the seven-step checklist in
  ``docs/feasibility/windows-artefact.rst``, automated for a ``windows-latest``
  runner, uploading its transcript with ``if: always()`` so a *failing* run is
  not the one whose evidence is lost.
* A single synthetic-PIN generator shared by both platforms, byte-identical.
* A genuine security fix in ``extract_nsis.py``: its containment filter matched
  only exact ``".."``, so on Win32 -- which strips trailing dots and spaces --
  a payload member named ``percolator.exe`` with a trailing space would have
  **silently replaced the file whose SHA-256 the checklist pins**.

Sign-off confirmed the harness cannot go green while doing nothing: forcing the
Windows path and making every Percolator launch exit 0 with no output and no
files produced ``INCONCLUSIVE``, exit 2. Its discriminating step reasons
correctly that a missing ``Compiler flag XML_SUPPORT was off`` proves nothing
without positive evidence the binary started.

**Gate item 8 was amended and strictly strengthened** (see :ref:`status-p02`'s
neighbouring section and ``phases/PHASE-00-feasibility.rst``). Its original
test, "``-X`` present", is satisfied by the ``noxml`` build it exists to
exclude -- both twins write ``percolator_out`` XML, executed on Linux. It now
requires the binary to be *observed to start*, requires ``--xml-in`` not to
answer the diagnostic, and extends the same observations to the portable
``noxml`` binary ``D-002`` option C actually ships. Every original requirement
was retained.


.. _s03-push:

Pushing
=======

Push ``main`` at ``82609f0`` first, then the branch. That commit is the
branch's base and a verified-green tree; ``main`` has since advanced, and
holding the first-ever pipeline run to a verified commit keeps an unrelated
failure from being mistaken for a pipeline failure::

    cd /workspace
    git push origin 82609f0:main
    git push origin windows-percolator-verification

Then create the pull request at
``https://github.com/mriffle/CometGUI/compare/main...windows-percolator-verification?expand=1``
and watch ``https://github.com/mriffle/CometGUI/actions``.

.. warning::

   **The second push is the one that fails if the token lacks ``workflow``
   scope.** The whole deliverable lives under ``.github/workflows/``. The
   rejection names the file and nothing else -- not the token, not the account,
   not the fix -- which is what sent session 02 chasing git identity for
   several exchanges. Classic PAT: tick ``workflow``. Fine-grained:
   *Repository permissions -> Workflows: Read and write*.

Actions is already enabled; it has simply never run. A **red** Windows job is
not automatically a defect: the driver exits 0 PASS, 1 NEGATIVE (a real finding
about the binary, to escalate), 2 INCONCLUSIVE, 3 HARNESS FAILURE.


What the next session should do
===============================

**First:** check for unpushed commits and whether you have push credentials.

**Then dispatch Phase 03** (``phases/PHASE-03-process-service.rst``) to a fresh
phase orchestrator. Its dependencies, 01 and 02, are both signed off and no
``D-`` item blocks it.

Phase 03 is the one place processes are created, observed, cancelled and
logged, and every later tool adapter depends on it being correct under
adversarial conditions. Four things it must not lose:

* **It inherits an undecided question from Phase 02:** where the shared
  ``BoundedMessageLog`` lives once the process service writes to the log the UI
  reads. Phase 02 deliberately did not answer it.
* **Phase 02 left it two ports ready to implement**, ``ProcessRunner`` and
  ``ToolCommand``, in ``org.cometgui.domain.ports``.
* Its gate forbids **fixed sleeps for synchronisation**, and requires
  cancellation to be proved by asserting **process liveness** rather than the
  absence of an exception. Both are exactly the shortcuts a tired agent takes.
* An ArchUnit rule must confine ``ProcessBuilder`` to that package. The rule
  already exists and Phase 02 is already held to it.

**Phase 04** (provenance core) is independent of 03 after 01 and may run
concurrently, on disjoint files. Two concurrent phase orchestrators worked
cleanly this session; brief each one with the paths the other owns.

Open, and tier 1's to settle
-----------------------------

* **The ``Settings`` section has no owning phase.** It appears once in the
  specification -- "Tool Manager and application Settings may be secondary
  navigation or dialogs" -- and in no phase document. Phase 02 built it as an
  empty pane that says so in text, pinned by a test. Give it an owner and
  content **before Phase 07**, which is the next big UI phase.
* ``D-006``'s CI fixture set, before Phase 14, and ``D-009``'s institutional
  copyright question, before release in Phase 16. Both are deliberate deferrals
  with a named moment; neither may quietly vanish.


Traps this session verified
============================

New this session, in addition to everything in the session 01 and 02 handoffs.

* **A concurrently running phase orchestrator makes ``git checkout`` of another
  branch fail** with "already checked out at ...". That refusal is protecting a
  live agent. Use ``git worktree add``, which is how the residue branch was
  built without disturbing Phase 02.
* **``sphinx -n -W`` fails on a duplicate label across documents.** Writing a
  ``STATUS.rst`` section using a label the phase handoff had already defined
  broke the docs gate. The gate caught it; namespace tier-1 labels
  (``status-``) to avoid it.
* **``verify-all-gates.sh`` now takes about 12 minutes**, past the 10-minute
  foreground command limit. Run it in the background and wait on its exit line.
* **Injecting a defect that is not actually a defect proves nothing.** Setting
  ``focusTraversable(false)`` on a navigation entry did *not* fail the
  keyboard-navigation test, and that was correct: JavaFX honours an explicit
  ``requestFocus()`` regardless. The real defect -- making the arrow keys skip a
  section -- failed on both drivers. Before concluding a gate has a hole,
  confirm your injection breaks the thing the gate actually claims.
* **A CVE fixed by removing a dependency is not a weakened gate.** TestFX drags
  in a vulnerable ``assertj-core``; Phase 02 **excluded** it rather than
  allowlisting the advisory, with ``javap`` evidence that nothing on the code
  path references it. ``scripts/ci/security/allowlist.json`` is still
  ``"entries": []``, which is the correct state. Check which of the two any
  future "supply-chain fix" actually is.

Pending on the branch, not on ``main``
---------------------------------------

``scripts/verify-all-gates.sh`` still prints that Phase 01 item 6 "needs a git
remote, which ``D-008`` withholds", untrue since 2026-08-30. The correction is
committed on ``windows-percolator-verification`` and lands when that branch
merges. Recorded so it is not fixed twice, and not mistaken for a live blocker.


What this session got wrong
===========================

Recorded because a handoff that only lists successes teaches nothing.

* **Nearly reported a gate hole that was not one.** The
  ``focusTraversable(false)`` injection above passed, and the first reading was
  that gate item 1 had a gap. It did not; the injection was wrong. Checking why
  a defect *should* fail before believing a green result is the cheaper order.
* **Wrote a ``STATUS.rst`` label without checking what the phase handoff had
  already defined**, breaking the docs gate. Trivial to fix, and it would have
  been trivial to avoid by grepping first.
* **Did not run the aggregate harness before dispatching.** The Phase 02
  orchestrator made the same observation about its own phase and stated the
  rule plainly: run ``scripts/verify-all-gates.sh`` at the **start** of a
  phase, not in the middle. A baseline you did not take is one you cannot
  compare against.
