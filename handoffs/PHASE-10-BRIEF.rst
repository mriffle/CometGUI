=====================================================
PHASE-10 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 10 -- Results Model and UI
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-08
:Depends on: Phase 09 (PARTIAL: platform, and gate item 9's export half,
   which is yours to re-prove). No ``D-`` item blocks this phase; ``D-013``
   (Percolator's analytics) is open and is the owner's.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-10-worklog.rst`` and ``handoffs/PHASE-10-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p10b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 4.7 hours (build 53 min, ``tests`` 123, the rest about 100).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 09's exit gate on the tree you start from. Tier 1 runs them
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

**Two hours is not enough for** ``--only tests`` **any more** (about 123
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-10-results.rst`` -- your scope and the eight exit-gate
   items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` (current revision) -- ``R-RES-01``..``04``,
   ``R-PERC-09``, ``AC-RES-01``..``04``, ``08``..``10``, and the results,
   export and performance sections.
#. ``handoffs/PHASE-09-handoff.rst`` -- especially its note for Phase 10 --
   then ``docs/developer/version_capabilities.rst`` and the Percolator section
   of ``docs/developer/workflow_engine.rst``.
#. ``handoffs/PHASE-08-handoff.rst`` (step graph, run store) and
   ``handoffs/PHASE-07-handoff.rst`` (the UI shell, accessibility, headless
   GUI tests).

What this phase is, plainly
===========================

Show the scientist their results: PSM and peptide tables with independent
1% q-value filters, counts that match the raw Percolator files exactly, the
learned feature weights, and exports that record what was filtered and how --
without ever changing the raw files, and without loading a large result set
into the interface all at once.

**The named trap.** Binding an ``ObservableList`` of every PSM. Build the
large performance fixture **first**, before any UI, and bind the UI to a paged
model.

**You do not start from nothing -- and must not start again.** Phase 09 built,
inside your own packages, ``org.cometgui.results.parser``
(``ResultTableReader``, ``WeightsReader``, ``QValue`` and the row/column
types) and ``org.cometgui.results.filtering`` (``PsmQValueFilter``,
``PeptideQValueFilter``, ``DisplayFilters``, counts). They are signed off and
tested. **Extend them; never write a second parser or a second filter.** If
one cannot meet a requirement as it stands -- for example, if
``ResultTable`` holds every row in memory and so cannot back the large fixture
-- change it in place, keep its existing tests green, and record the change.
Two q-value rules that agree today are the defect this project has already
found once (the duplicated secret redaction in ``ONBOARDING.rst``).

Obligations from earlier phases
===============================

#. **One of everything**: one parser set, one filter set, one process service
   (gate item 2 asserts a filter change launches **no** process -- through
   that service), one hashing and provenance writer, one step graph and run
   store.
#. **Restore the step order.** Phase 09 noted ``finalise-results`` is
   unplanned, so ``finalise-provenance`` is not ordered after Percolator;
   plan ``finalise-results`` and restore that order.
#. **Gate item 9 of Phase 09** -- raw Percolator outputs byte-identical after
   any filtering **or export** -- is re-proved here for export; your own gate
   item 4 is the same claim.
#. **Independent counts.** Gate items 3 and 7 compare against values computed
   independently from the raw files: a test that computes the expected value
   with the code under test cannot fail. Compute them another way (a separate
   simple reader in the test, a script, or a pinned number with how it was
   obtained).
#. **Real fixtures first.** The real Percolator outputs from Phase 09's runs
   are the primary fixtures; the large performance fixture is generated, its
   generator committed, and recorded as constructed.

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
  run is already about 4.7 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

Protein-level results (out of scope for release 1); PDV (Phase 11); Limelight
(Phase 12); the provenance UI (Phase 13); ``D-011`` and ``D-013`` (the
owner's); the mutation-scoring question in ``scripts/build.sh`` (the owner's);
``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution (record as
unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-10-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
