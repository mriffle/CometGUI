=====================================================
PHASE-09 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 09 -- Percolator Adapter and Version Capabilities
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-07
:Depends on: Phases 05 (PARTIAL, macOS Gatekeeper only) and 08 (PARTIAL,
   platform only), and the ``D-012`` repair (a new configuration starts with no
   spectral library), all signed off by tier 1 -- see ``STATUS.rst``. ``D-002``
   and ``D-003`` are decided; nothing blocks this phase.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-09-worklog.rst`` and ``handoffs/PHASE-09-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p09b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 3.7 hours (build 40 min, ``tests`` 95, the rest 91).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 08's exit gate on the tree you start from. Tier 1 runs them
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

#. ``ONBOARDING.rst`` -- all of it, and its warning: the single most important
   verified fact is that Percolator 3.09 removed XML I/O and Limelight needs
   Percolator XML.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-09-percolator.rst`` -- your scope and the nine exit-gate
   items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` (current revision) -- ``R-PERC-01``..``12``, the
   Percolator capability and artefact-availability sections (including the
   ``XML_OUTPUT`` probe and its 64+64 fixture rule, revision 11), and
   ``AC-RES-05``..``07``, ``AC-PRV-10``.
#. ``DECISIONS.rst`` -- ``D-002`` (option C: portable ``noxml`` archives,
   3.07.1 for XML), ``D-003`` (three managed versions), ``D-004`` (Rosetta 2
   for the Percolator stage on Apple silicon).
#. ``handoffs/PHASE-05-handoff.rst`` -- the manifest, installer, capability
   probes and the Windows XSD companions. Phase 05 owns probing; you consume
   probe results.
#. ``handoffs/PHASE-08-handoff.rst`` -- the workflow engine, step graph, run
   storage, the merged PIN you start from, and the declared-but-unbuilt
   Percolator steps the rerun preview already names.
#. ``handoffs/PHASE-03-handoff.rst`` and ``handoffs/PHASE-04-handoff.rst`` --
   the one process launcher, and hashing/provenance.

What this phase is, plainly
===========================

Run Percolator on the merged PIN from a Comet run, with whichever Percolator
the platform and the enabled stages call for, and never pass an option the
binary does not have. Choose the version by **capability**, never by version
number: with Limelight enabled, the newest build that can write XML; without
it, the newest overall; and say which newer version was skipped and why.

**The named trap.** Version-number branching passes on the versions you test
and fails on the one a user has. Every branch is on a probed capability.

Obligations from earlier phases
===============================

#. **One of everything.** One process launcher, one hashing and provenance
   writer, one redaction rule set, one step graph and run store (Phase 08),
   one capability probe (Phase 05). Extend them; never copy one.
#. **Zero-decoy PINs.** Phase 08 found that a 2026.02.2 fragment-ion index
   built with internal decoys searches to zero decoy rows. Gate item 5 is the
   guard: a zero-decoy PIN fails before Percolator launches. Use that real
   case as one of its fixtures if you can reproduce it; otherwise say why not.
#. **Real fixtures.** Percolator runs on a merged PIN produced by the real
   Comet path, not a hand-written one (a hand-typed fixture shaped to what the
   parser expects is a check that cannot go red). Synthetic PINs are fine
   where the gate names an edge case (two- and three-split weights, zero
   decoys), and each is recorded as constructed.
#. **Versions.** The managed versions are those in ``manifests/tools.json``
   (``D-003``). Executing 3.07.1 and 3.09 on Linux is required; other
   platforms are recorded as unverified, as for every phase so far.

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
  run is already about 3.7 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

The results model and UI, and filters (Phase 10); Limelight conversion and
upload (Phase 12) -- you only expose whether the stage is *available*; PDV
(Phase 11); ``D-011`` (Intel macOS, the owner's); the mutation-scoring
question in ``scripts/build.sh`` (the owner's); ``dev-verify.sh --mutation``
(tier 1); macOS and Windows execution (record as unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-09-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
