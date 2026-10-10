=====================================================
PHASE-11 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 11 -- PDV Integration
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-10
:Depends on: Phases 05 (PARTIAL, macOS Gatekeeper) and 10 (PARTIAL,
   platform), both signed off. ``D-005`` (enhanced PDV via a generated
   mzTab) and ``D-001`` (CasanovoGUI derivation) are decided. No ``D-`` item
   is open.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-11-worklog.rst`` and ``handoffs/PHASE-11-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p11b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 8 hours (build 102 min, ``tests`` about 235, the rest about 140).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 10's exit gate on the tree you start from. Tier 1 runs them
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

**Two hours is not enough for** ``--only tests`` **any more** (about 235
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-11-pdv.rst`` -- your scope, its *Risks and notes*, and the
   eight exit-gate items. That gate is the standard; you may not weaken any
   part.
#. ``specification.rst`` (current revision) -- ``R-PDV-01``..``05``,
   ``AC-VIS-01``..``05``, and the PDV and mzTab sections.
#. ``DECISIONS.rst`` -- ``D-005`` (why mzTab) and ``D-001`` (what deriving
   from ``Noble-Lab/CasanovoGUI`` obliges: retained notices, recorded
   derivation, the derived-file Checkstyle superset).
#. ``handoffs/PHASE-10-handoff.rst`` (the results model and selection you
   bind to), ``handoffs/PHASE-05-handoff.rst`` (managed installs -- PDV is one),
   ``handoffs/PHASE-03-handoff.rst`` (the one process service; PDV's
   lifecycle lives there) and ``handoffs/PHASE-08-handoff.rst`` (run storage
   and provenance).
#. ``docs/feasibility/`` -- Phase 00's PDV findings.

What this phase is, plainly
===========================

Let the scientist look at a spectrum: install PDV on first use without
blocking a search, open a run in it, and have selecting a PSM in CometGUI's
results move PDV to that spectrum. PDV's control server only accepts mzTab, so
the substance of the phase is an **mzTab exporter proved faithful to the
source files** -- every PSM once, nothing invented, values transcribed not
recomputed, modifications compared as parsed values, and ``spectra_ref``
resolving to the right spectrum on a file where file position and scan number
differ.

**Order of work, from the phase document, and binding:** (1) **spike first**
-- prove on a real run that PDV's ``MztabImport`` accepts an mzTab CometGUI
generates, before building the exporter out; if it does not, stop and escalate
with the evidence; (2) bank the baseline -- install on demand, open-in-PDV,
CLI figure generation (gate items 1-4); (3) then the exporter and its fidelity
suite (items 6-8) and the live control path (item 5).

Obligations
===========

#. **One of everything**: one process service (PDV's lifecycle, its port, its
   shutdown), one installer and manifest (PDV is a managed tool with a pinned
   SHA-256), one results model and selection (Phase 10's), one hashing and
   provenance writer.
#. **Reuse, do not reinvent** ``PdvLauncher`` and ``PdvController`` from
   ``Noble-Lab/CasanovoGUI`` (``R-PDV-05``), keeping upstream copyright
   notices exactly and recording the derivation per ``D-001``. The derived-file
   Checkstyle superset applies; never exclude a derived file from it. Any
   CometGUI-authored file says ``Copyright (C) 2026 The CometGUI authors.``
   (``D-009``).
#. **Fidelity is against the source.** Every gate-6 comparison reads the
   Percolator and Comet files independently; a check against the exporter's
   own accounting cannot fail. Gate 7 needs a spectrum file where 1-based
   position and scan number **differ** -- build or choose one, record how,
   and show they differ before using it.
#. **PDV exits 0 having written nothing**, and its CLI is not headless: judge
   every invocation by its output files, impose a timeout, and use the
   project's own X11 stack under ``tools/`` (Phase 00) for a display. Never
   install anything on the host.
#. **No screen-coordinate automation**, in production or tests: the control
   API is the only way to assert what PDV shows.
#. **Analytics.** If PDV or anything it launches reports usage to a third
   party, escalate it the day you find it (``D-013`` set the project's
   stance for Percolator).

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
  run is already about 8 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

Limelight (Phase 12); the provenance UI (Phase 13); packaged end-to-end GUI
runs (Phase 14); the mutation-scoring and build-time questions (the owner's);
``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution (record as
unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-11-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
