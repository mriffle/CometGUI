=================================================================
PHASE-05 resumption brief -- tier 1 to the phase orchestrator
=================================================================

:Phase: 05 -- Tool Registry and Installer
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 07
:Date: 2026-09-17
:Depends on: Phases 01, 03 and 04 -- all ``PARTIAL``, all signed off. Nothing
   blocks you and no ``D-`` item is open.
:Runs alongside: **nothing.** You are the only phase live in this tree.
:Resumes at: **unit 8 of 13.** Units 1-7 are signed off and are not yours to
   revisit except where this brief says so.
:Expected grade: **PARTIAL** -- read :ref:`p05r-grade` before you plan anything.

.. note::

   This document is written by tier 1 and owned by tier 1. Do not edit it.
   Everything you produce goes in ``handoffs/PHASE-05-worklog.rst`` and
   ``handoffs/PHASE-05-handoff.rst``, which are yours. You do not edit
   ``STATUS.rst``, ``DECISIONS.rst``, ``phases/index.rst`` or ``CLAUDE.md``.

Phase 05 was **stopped after unit 7 on 2026-09-03 by owner instruction** -- not
failed, not abandoned, and not because anything was wrong. The owner restarted
it on 2026-09-17. Your job is to finish it from unit 8 and to make its numbers
mean something.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it. In particular *Roles*, *Sign-off*, *Why
   phases run one at a time*, and *What parallel agents actually share*.
#. ``CONTRIBUTING.rst`` -- environment, commit, gate, documentation and handoff
   conventions.
#. ``phases/PHASE-05-tool-registry.rst`` -- your phase: scope, deliverables and
   the nine exit-gate items. That gate is the standard you are held to and you
   may not weaken any part of it.
#. ``handoffs/PHASE-05-handoff.rst`` (652 lines) -- written on 2026-09-03 by
   the second Phase-05 orchestrator **for you**, and it expects to be read in
   full. Its section *The first thing to do* is your starting sequence and this
   brief does not replace it. Note its own warning: a revision of it at
   ``be5dd72`` says "units 1-5 signed off, 6-12 not started" and is stale.
#. ``handoffs/PHASE-05-worklog.rst`` (2877 lines) -- the proof behind the
   handoff's claims: every unit, every sign-off, the exact defect injected and
   the exact failure text it produced. The handoff is the map; this is what
   happened.
#. ``handoffs/PHASE-05-BRIEF.rst`` -- the original dispatch brief of
   2026-09-02. Still in force where this document is silent.
#. ``specification.rst`` **revision 11**, the ``R-TOOL-``, ``R-PERC-``,
   ``R-PLAT-`` and ``R-SEC-`` rules your gate items name.

Where the phase actually stands
===============================

**Units 1-7 signed off. Nothing landed-but-unsigned.** Each was signed off by
an orchestrator that read the diff, re-ran the build itself and injected a
defect the unit had not tried; five of the seven were sent back at least once.
The tree is at ``0630d6d``'s Java state -- the last commit touching any
``.java`` or POM is ``9e38f3b``, and **nothing since 2026-09-03 has touched
Java, a POM or the Maven build**; the four commits after it changed
``STATUS.rst``, ``phases/PHASE-00-feasibility.rst``, a CI workflow and two CI
scripts. So the handoff's figures describe this tree. That is a reason to
expect your baseline to reproduce them, **not** a reason to skip taking it.

Gate items as inherited: **3, 4, 6 MET**; **5 and 7 met at the probe/adapter
but need unit 8 to reach the port**; **2 and 8 PARTIAL**, proved at the
installer and awaiting the UI; **1 NOT MET** (units 8, 9, 10); **9 NOT MET**
and it is why the phase is ``PARTIAL``. The handoff's table at ``p05h-gates``
is authoritative on the detail and you re-verify it rather than inheriting it.

Your order of work, and it is not negotiable
============================================

#. **Decide the ``phase05-unit8-cancelled`` tag** (``be2edfc``, 24 files, ~5000
   insertions, **unverified by anyone**). Your predecessor's recommendation --
   which tier 1 endorses -- is to **read it, not build on it**. It touched
   ``cometgui-domain``, which is mutation-critical with a hand-typed survivor
   set pinned in ``scripts/verify-test-gates.sh``. Record the decision in the
   work log before unit 8 is planned.
#. **Reproduce the cancellation defect** described at ``p05h-cancel-defect``
   *before* you plan unit 8: cancelling an install mid-download reports
   ``FAILED``, not ``CANCELLED``, reproduced three times against the real PDV
   artefact at 4 MB. **It is reported, not verified** -- your predecessor did
   not confirm the diagnosis and neither has tier 1. It changes what unit 8
   must do, and if it is real it lands on unit 10's gate item 1. If it does not
   reproduce, that is a finding too and it is reported, not quietly dropped.
#. **Take a baseline on a quiet tree**: ``bash scripts/build.sh`` then ``bash
   scripts/verify-all-gates.sh``. This is the one figure in the handoff that
   describes a tree nobody has confirmed green end to end. Take it **once**,
   at the start, and record it in the work log. Read :ref:`p05r-builds` before
   you reach for either script again.
#. **Then the units, serially, in this order: 8, 9, 10, 13, 11, 12.** The order
   is your predecessor's and its reasoning is in the handoff. Do not renumber
   them: references in the committed work log would silently go wrong, and a
   silently renumbered unit is drift this project has already paid for.

Four standing tier-1 directions
===============================

These are decided. Carry them out; do not re-open them.

#. **The ``ToolOffer`` download-size gap is fixed in unit 8**, not deferred.
   As the port stands the Tool Manager cannot tell a scientist that PDV is a
   103 407 417-byte download, on the one transfer the phase document singles
   out for cancellation testing. ``ArtefactRecord.sizeBytes`` already exists.
#. **The cancellation defect is fixed or explicitly routed in unit 8**, with
   the fix graded *inside* a transfer and not only at step boundaries. The
   reported fix is about ten lines in ``InstallPipeline.runNextStep``; grade
   the behaviour, not the line count.
#. **Additive registration in ``scripts/verify-all-gates.sh`` is approved** for
   unit 12's ``scripts/verify-install-gates.sh``: register the control, never
   lower a floor. Raising your own is expected.
#. **Never add an entry to the pinned survivor set** in
   ``scripts/verify-test-gates.sh`` to make a build pass. If control 0 fails,
   kill the new mutant with a test or argue equivalence in the production code.
   ``cometgui-tools``'s mutation switch is **ON**; switching it back off is a
   rejection.

Work units run serially. This is the owner's rule
=================================================

The owner set this on 2026-08-31 and it has not moved. **Serial is the
default.** Running two phase agents at once requires a positive argument that
collision is *impossible*, recorded in the work log before they start. "They
touch different files" is not that argument and has already been shown false in
this project. Your predecessor offered no such argument for units 8-13 because
none exists: they share ``cometgui-domain``, the Maven working tree,
``_build/m2repo``, the docs gate and the git index. If in doubt, serialise.

The injection protocol
======================

A unit is signed off when **you** have read its diff, re-run its checks, and
seen its gate go **red for a reason you chose** -- not when its agent reports
success. Inject into **production** code, never into a test, then revert from a
checksummed backup and verify with ``sha256sum -c`` rather than by eye.

**An injection is only evidence if the edit is proven to have landed.** Assert
the anchor matches exactly once before writing -- a naive replace can hit a
Javadoc example instead of code, changing no behaviour and yielding a false
"the gate is weak" verdict -- and grep a marker back out of the file
afterwards. Use a private scratchpad subdirectory: sibling agents share one
scratchpad root, and two agents writing ``inject.py`` to the same path is a
real incident in this project's history. **If a defect that previously failed
suddenly passes, suspect the injection before you suspect the gate.**

**Audit the population, not only the number.** Compare the classes actually
compiled into ``target/classes`` against those present in ``jacoco.xml`` and
``mutations.xml``. A class missing from a report does not drag an average down
-- it leaves the sample, and re-running the gate reproduces the same clean
figure.

.. _p05r-builds:

Build economy -- the owner's rule of 2026-09-17
===============================================

``scripts/build.sh`` takes about 23 minutes, roughly 17 of them PIT, and that
cost grows with every module this phase fills with real code. The owner's
instruction: **do not re-run the whole build as insurance.** Take the baseline
once as step 3 above, run the full build where Java or build configuration
changed -- which unit work usually does -- and for a documentation-only or
CI-script-only change run the check that can actually go red for it
(``scripts/ci/docs-build.sh`` for the ``-n -W`` gate, a script's own
``--self-test``). Project rule 6 -- exit code 0 proves nothing -- is satisfied
by verifying *the thing that changed*, not by re-running everything.

.. _p05r-grade:

Expected grade: PARTIAL. Document for the evidence, not for a verdict
=====================================================================

Gate item 9 needs macOS, and **no macOS binary has ever been executed anywhere
in this project**. Unit 13 is the approved attempt; until it runs with a
negative control that **bites**, item 9 stays unmet and must not be reported
otherwise. A phase resting on an unverifiable item is ``PARTIAL``, not
``PASSED``, and reporting ``PARTIAL`` with precise residue is worth more here
than a ``PASSED`` that has to be walked back.

Unit 13 needs tier 1, and you stop at its edge
==============================================

Unit 13 runs on a GitHub macOS runner. **Tier 1 pushes the branch and
dispatches the workflow**, exactly as it did for the Windows job. Author the
unit, prove what you can locally, then **escalate upward with the branch ready
and say what you need run**. Do not push a branch or open a pull request
yourself. Its mandatory negative control: a ``curl`` download sets no
``com.apple.quarantine``, so the job must set the attribute itself and show
that leaving it set produces a refusal. **If the control does not bite, the
result is "this check cannot go red" and is reported as such, never as a pass.**

Hard rules
==========

* **Never weaken an exit gate, a checksum verification, a validation rule or a
  coverage threshold to make something pass.** That is a rejection regardless
  of how green the build is.
* **Commit with an explicit pathspec. Never** ``git add -A``.
* **Never force-push and never rewrite published history.** ``main`` is
  published.
* All documentation is reStructuredText and must pass ``sphinx-build -n -W``
  via ``scripts/ci/docs-build.sh``.
* **Escalate upward only.** You never contact the owner, never answer a ``D-``
  item, never edit ``STATUS.rst`` or ``DECISIONS.rst``.
* ``NSIS_PAYLOAD`` is not implemented and must not be (``D-002`` option C).
  ``scripts/ci/nightly-manifest-verify.sh`` stays a stub that exits non-zero --
  it is Phase 15's, and making it exit 0 would be a gate weakening.
* The copyright line stays ``Copyright (C) 2026 The CometGUI authors.``
  (``D-009``). Files derived from ``Noble-Lab/CasanovoGUI`` keep their notices
  and are held to a superset of the Checkstyle rules (``D-001``).

What you are hunting
====================

This project's most valuable recurring finding is a check that is green because
it **cannot fail**, not because the code is right. Ten shapes are on record in
``STATUS.rst``; the ones this phase keeps paying for are **a rule graded at one
point on an axis it does not depend on** -- four times, including the
cancellation defect, which was graded at step boundaries but never *inside* a
transfer -- and **a check that is never reached**, which is as inert as one that
is never red. When you sign off a unit the question is never "is it green?" but
**"have I seen this go red for a reason I chose?"**

Not yours
=========

* The **nightly pipeline's step ordering** (``status-nightly-masking``) is
  Phase 01 residue and belongs to whoever lands it. Do not fix it here.
* The **six test classes calling** ``Locale.setDefault`` are escalated and sit
  with tier 1 until someone proposes enabling parallel execution. Safe today.
* **Promoting Phase 00 to ``PASSED``** is a tier-1 sign-off action.

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-05-handoff.rst`` for a successor who cannot ask you questions,
and report to tier 1 with the evidence, **knowing every claim will be
re-checked rather than read**. Tier 1 re-runs the nine gate items itself and
injects its own defects rather than re-running yours. Say plainly which items
are met, which are partial with the residue named, and which are unverifiable
here. Escalate a blocker the day you hit it, not at the end.
