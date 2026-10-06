=====================================================
PHASE-08 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 08 -- Workflow Engine and Comet Adapter
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-06
:Depends on: Phases 03, 04, 05 (each PARTIAL, platform residue only) and 06
   (PASSED, extended by the Comet 2026.03.0 intake); Phase 07 (PASSED) is the
   editor whose Run control you make real. No ``D-`` item blocks this phase.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-08-worklog.rst`` and ``handoffs/PHASE-08-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p08b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 2.5 hours.

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 07's exit gate on the tree you start from. Tier 1 runs them
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

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-08-workflow-comet.rst`` -- your scope (including the
   index-compatibility check tier 1 assigned on 2026-10-06) and the nine
   exit-gate items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` (current revision) -- ``R-CMT-01``..``08``,
   ``R-DEC-01``..``04``, ``R-RUN-01``..``06``, ``R-PARAM-13``, the workflow,
   storage and provenance sections, the stage-rerun list, and ``AC-WF-01``..``05``,
   ``AC-PRV-03``, ``AC-PRV-04``.
#. ``handoffs/PHASE-03-handoff.rst`` -- the process service: the **one**
   launcher, argument arrays, descendant termination. Comet runs through it.
#. ``handoffs/PHASE-04-handoff.rst`` -- hashing and the provenance record, and
   the one secret-redaction rule set. Do not build a second of either.
#. ``handoffs/PHASE-05-handoff.rst``, ``handoffs/PHASE-06-handoff.rst``,
   ``handoffs/COMET-2026-03-handoff.rst`` and ``handoffs/PHASE-07-handoff.rst``
   -- how a Comet binary is reached, the parameter model and its canonical
   writer, and the editor's Run readiness (today always disabled by "engine
   not built", which this phase replaces).
#. ``docs/feasibility/`` -- Phase 00 proved the scientific path end to end,
   and found the ``-N`` hazard this phase's gate item 1 exists for.

What this phase is, plainly
===========================

Make a real Comet search happen: a declared workflow of stages, one Comet
invocation per spectrum file into the run directory, validated pepXML and PIN
outputs merged correctly, the decoy rules enforced before anything runs, run
storage that cannot be changed after the fact, and provenance complete enough
to reproduce the run. The phase document says it decides the shape of every
run the product will ever record.

**The named trap.** ``-N`` accepts one input file. Passing several at once
appears to work and writes into the scientist's data directory.
``scratch/fixture/`` already holds a ``.pep.xml`` and ``.pin`` beside the
K562 mzML, written by Phase 00's feasibility run on 2026-08-29 -- leftovers,
not your output. Do not let a test mistake them for a result, and do not
delete them (they are part of Phase 00's evidence).

Obligations from earlier phases
===============================

#. **One of everything.** One process launcher (Phase 03), one hashing and
   provenance writer and one redaction rule set (Phase 04), one canonical
   parameter writer and one validator (Phase 06). If the workflow needs
   something one of them does not offer, extend it there, with its own tests
   and ``--only`` harness -- never a second copy in the workflow module.
#. **Release-scoped.** The run uses the release the scientist selected. The
   index-compatibility check reads the ``.idx`` header: 2026.03.0 reads only
   ``Comet index database v5``. A test that only ever uses the default
   release cannot see a version-blind check; one of your injections makes it
   version-blind.
#. **Run readiness is the model's.** Phase 07's ``NEEDS_ATTENTION`` and
   validation blocks stay in force; this phase removes only the "engine not
   built" reason, and gate items 4 and 5 add the decoy blocks.
#. **Real fixtures.** The two spectrum fixtures and the FASTA in
   ``scratch/fixture`` are fetched by checksum (``D-006``,
   ``scripts/feasibility/fetch_ephemeral_input.py``). A real search of the
   whole human proteome is slow: use a recorded, checksum-pinned subset where
   the gate does not need the whole, and say so. Searches run on a shared
   64-core machine; bound Comet's threads in tests.
#. **Read-only input.** Gate item 1 says "mounted read-only". There is no
   ``sudo``. A user-namespace bind mount (``unshare``) or a directory without
   write permission are both acceptable **if a test first proves that a write
   into it is refused**; record which you used and why. A read-only directory
   the test never tried to write to proves nothing.
#. **Cancellation** (gate item 7) terminates descendants through Phase 03's
   mechanism; prove it with the process tree, not with the exit code.

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
  run is already about 2.5 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

Percolator (Phase 09); results parsing beyond what validation needs (Phase
10); PDV, Limelight (11, 12); ``D-011`` (Intel macOS, the owner's); the
mutation-scoring question in ``scripts/build.sh`` (the owner's);
``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution (record as
unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-08-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
