=====================================================
PHASE-07 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 07 -- Comet Parameter Editor UI
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-04
:Depends on: Phases 02 (PASSED) and 06 (PASSED), and the Comet 2026.03.0
   intake work package that extended 06 (signed off by tier 1; see
   ``STATUS.rst``). No ``D-`` item blocks this phase.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-07-worklog.rst`` and ``handoffs/PHASE-07-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p07b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take well over an hour and a half.

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at the intake's exit gate on the tree you start from. Tier 1 runs them
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
#. ``phases/PHASE-07-comet-param-ui.rst`` -- your scope, the ``Settings``
   note, and the eight exit-gate items. That gate is the standard; you may not
   weaken any part.
#. ``specification.rst`` (current revision) -- the parameter-interface
   sections, ``R-PARAM-03``..``R-PARAM-10``, ``R-CMT-01``, the GUI coverage
   list, the accessibility rules, and ``AC-PAR-03``..``05``, ``07``..``10``.
#. ``handoffs/PHASE-02-handoff.rst`` -- the application shell, navigation,
   and how GUI tests drive JavaFX headlessly (Monocle).
#. ``handoffs/PHASE-06-handoff.rst`` and then
   ``handoffs/COMET-2026-03-handoff.rst`` -- the model you build on, as it now
   is: two releases (2026.03.0 default, 2026.02.2 supported), per-release
   definitions, residue alphabet and rule severities, and the migration report.
#. ``docs/developer/comet_parameter_schema.rst``.

What this phase is, plainly
===========================

The screen where a scientist sets up a Comet search. The specification calls
it the central product-design effort: progressive disclosure (Essentials,
Advanced, Expert), structured editors for structured values (the fifteen
variable-modification slots, the enzyme table, tolerance pairs), and error
prevention over error reporting. The phase document's named trap is
generating controls mechanically from the schema and calling it done.
Essentials is a curated, task-ordered surface.

**The model is the source of truth.** No scientific logic, parsing or
validation in a controller or view model: the editor asks the model. If the
editor needs something the model does not offer, the gap is a change to
``cometgui-params-comet`` with its own tests and ``--only params`` -- never
a second rule set in the UI. Two rule sets that agree today are the defect
this project has already found once (``ONBOARDING.rst``, the duplicated
secret redaction).

Two obligations the intake handed you
=====================================

#. **Everything the editor offers is per release.** Bind to
   ``CuratedMetadata.parameter(name, version)``, the release's residue
   alphabet and its rule severities. ``^`` and ``$`` are offered for 2026.03.0
   and not for 2026.02.2. A GUI test that only ever runs on the default
   release cannot see a version-blind editor; at least one test per
   version-dependent control runs on both releases, and at least one of your
   injections makes a control version-blind.
#. **A migrated configuration is reviewed before it is used.** Show the
   ``MigrationReport`` (``CONVERTED``, ``NOTED``, ``NEEDS_ATTENTION``) as a
   reviewable diff. An unresolved ``NEEDS_ATTENTION`` entry is a **blocking**
   validation state: it blocks Run like any other error, is attached to its
   field, appears in the summary and is keyboard reachable (gate item 6). The
   reason is concrete: for ``variable_mod01`` the substituted default is an
   *active* methionine oxidation, so an unreviewed migrated file would search
   with a modification the scientist never chose.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a recorded
  argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (anchor matches once; marker
  grepped back), restore and verify with ``sha256sum -c``. Use a private
  scratchpad subdirectory. An injection stopped by Spotless or Checkstyle is
  no verdict; delete the reports before each run.
* **A GUI test proves the value, not that nothing threw**: assert the
  serialised tuple, the canonical file, the text of the error, the focus
  owner. An expected value computed by the code under test cannot fail.
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* Accessibility is a gate item: every control has an accessible name;
  validation is never colour-only.
* ``D-001``'s attribution duty applies to anything derived from
  ``Noble-Lab/CasanovoGUI``; the copyright line stays exactly ``Copyright (C)
  2026 The CometGUI authors.`` (``D-009``).
* Locale: number formatting uses ``Locale.ROOT``; tests that change the
  default locale restore it. Do not enable parallel test execution.
* Never weaken a gate, checksum, validation rule or threshold; never add to the
  pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors, never
  lower one. A new harness for this phase's gate, registered additively in
  ``scripts/verify-all-gates.sh``, is approved.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

Running a search, building an ``.idx``, the "selected index is compatible"
check (Phase 08 or later; no phase owns it yet); the Results UI (Phase 10);
the mutation-scoring question in ``scripts/build.sh`` (the owner's);
``dev-verify.sh --mutation`` (tier 1); the macOS Intel Comet question
(``D-011``, the owner's); Phase 05 and intake residue listed in their handoffs.

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-07-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
