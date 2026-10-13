=====================================================
PHASE-13 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 13 -- Provenance UI and Reports
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-13
:Depends on: Phases 04, 08, 09 and 12 (all PARTIAL, platform), signed off;
   the between-phase repair FIX-P12-P13 signed off immediately before this.
   No ``D-`` item is open.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-13-worklog.rst`` and ``handoffs/PHASE-13-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p13b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 10 hours (build 135 min,
``tests`` about 312, the other eighteen about 152).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 12's exit gate, and the repair since touched only the Tool
   Manager and the tests preflight, re-gated by tier 1. Tier 1 runs them once
   more, at **this** phase's exit gate.
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
   ``workflow`` and ``tests`` own the provenance writer's controls -- read
   ``scripts/verify-all-gates.sh --help`` to see which harness grades what
   you touch).
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

**Two hours is not enough for** ``--only tests`` **any more** (about 312
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs. ``bash scripts/verify-test-gates.sh --preflight`` (one second)
tells you first whether the host has everything it needs.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-13-provenance-ui.rst`` -- your scope, the **important** box
   at its top, its *Risks and notes*, and the six exit-gate items. That gate
   is the standard; you may not weaken any part.
#. ``specification.rst`` (revision 19) -- ``R-PROV-01``..``05``,
   ``AC-PRV-01``..``09``, ``R-SEC-03``/``04`` (redaction) and the provenance
   sections.
#. ``STATUS.rst`` -- *Platform divergence, in two tiers* and *The hash cache is
   deliberately inert on Windows*.
#. ``handoffs/PHASE-04-handoff.rst`` (the provenance model, its writer, the
   one redactor, the RST generation and ``ATOMIC_MOVE``),
   ``docs/developer/provenance_schema.rst``, ``docs/provenance.rst``;
   ``handoffs/PHASE-08-handoff.rst`` (the run directory, failed and cancelled
   runs, partial outputs), ``handoffs/PHASE-09-handoff.rst`` (Percolator
   provenance and fallback warnings), ``handoffs/PHASE-11-handoff.rst`` and
   ``handoffs/PHASE-12-handoff.rst`` (PDV and Limelight provenance in
   ``events.log``, and the Limelight credentials that must never reach an
   export), ``handoffs/PHASE-05-handoff.rst`` (the tool cache and its
   digests).

What this phase is, plainly
===========================

Give the scientist a Provenance section that shows, for any run -- finished,
failed or cancelled -- exactly what was run on what: every tool with its
version, path and digests; every input and output file with both digests;
every command line, one per spectrum file; parameters with where they came
from (preset, changed from it); the timeline, logs and warnings. Let them copy
a digest or a command, open a file's folder, and export the record as JSON and
as a readable RST report -- both from the one model, neither ever carrying a
secret. And prove it all against the **files on disk**, never against the
record itself.

Obligations
===========

#. **Settle the Windows question first, as unit 1, before any viewer code.**
   The phase document's important box says why. This tree cannot run Windows,
   so settle it by design, not by measurement:

   * The viewer **never holds ``provenance.json`` open**: it reads the whole
     file and closes it before parsing, keeps no watcher or handle on it, and
     a test proves that on this platform (for example, no descriptor for the
     file remains in ``/proc/self/fd`` after a load).
   * Decide, and record in the work log and ``docs/developer/``, whether
     Phase 04's finalisation also needs a bounded retry on
     ``AccessDeniedException`` for holders the product does not control (a
     virus scanner, a sync client). If you add one, it is tested through an
     injected failure (Phase 04's ``Durability`` seam), it never weakens the
     whole-document guarantee or gate item 5 of Phase 04, and it is reported to
     tier 1 as a change to Phase 04's code. If you judge it needs the owner,
     escalate it; do not decide it silently.
   * Whatever is chosen stays **unverified on Windows** and is recorded so.

#. **One of everything**: one provenance model and writer (Phase 04's), one
   hashing service, one redactor, one RST generator. The JSON and RST exports
   come from the same model (gate item 5); the UI reads that model too. Do not
   change the provenance schema -- Phase 04 owns it; if the UI cannot show
   something without a schema change, escalate.
#. **Recompute from disk** (gate items 1 and 2): an independent hash of every
   listed input, output and tool file, compared with the record. A test that
   compares the record with itself, or with a value the same code computed,
   is the defect the phase document warns about.
#. **Real runs**: gate items 1--4 are proved on a real Comet + Percolator run
   of the pinned fixture, plus a real failed run and a real cancelled run --
   not on hand-built records alone.
#. **No secret in either export** (gate item 6): seed a secret through every
   path one could enter (the Limelight key and URL credentials included), then
   grep **every** export and every file the run directory holds -- enumerate
   them from disk, not from a list.
#. **Known residue, not yours unless it blocks you**: ``EngineStep``'s
   ``LAUNCH_PDV``, ``CONVERT_LIMELIGHT`` and ``UPLOAD_LIMELIGHT`` are declared
   and unused; PIT targets for ``workflow.pdv`` and ``workflow.limelight`` wait
   on the owner's build-time decision.

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
* **A test proves the value, not that nothing threw**: digests, argument
  arrays, row counts, the named file in the message.
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* **Nothing on the JavaFX thread starts a process or reads a large file** --
  the repair just before you fixed exactly that in the Tool Manager. Hashing
  for display, loading a record and exporting run on the background executor.
* Locale: number formatting uses ``Locale.ROOT``; do not enable parallel test
  execution.
* ``D-001`` attribution and the ``D-009`` copyright line apply as before.
* Never weaken a gate, checksum, validation rule or threshold; never add to the
  pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors, never
  lower one. A new harness for this phase's gate, registered additively in
  ``scripts/verify-all-gates.sh``, is approved. **Keep it lean**: the full gate
  run is already about 10 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

Changing the provenance schema (Phase 04's); packaged end-to-end GUI runs and
the nightly sandbox slot (Phase 14); performance (Phase 15); the
mutation-scoring and build-time questions (the owner's);
``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution (record as
unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-13-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the Windows decision and
its reasoning, the injections you used, and anything escalated. Report only
when finished or genuinely blocked; the owner wants no interim pauses. Short
and factual; every claim is re-run, not read.
