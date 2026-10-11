=====================================================
PHASE-12 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 12 -- Limelight Conversion and Upload
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-11
:Depends on: Phases 09 and 10 (both PARTIAL, platform), signed off; Phase 11
   (PARTIAL, platform) signed off immediately before this. ``D-002`` and
   ``D-007`` are decided. No ``D-`` item is open.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-12-worklog.rst`` and ``handoffs/PHASE-12-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p12b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 9 hours (build 120 min, ``tests`` about 270, the other seventeen about 140).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 11's exit gate on the tree you start from. Tier 1 runs them
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

**Two hours is not enough for** ``--only tests`` **any more** (about 270
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-12-limelight.rst`` -- your scope, its *Risks and notes*, and
   the eight exit-gate items. That gate is the standard; you may not weaken
   any part.
#. ``specification.rst`` (revision 18) -- ``R-LL-01``..``05``, ``R-SEC-03``,
   ``R-SEC-04``, ``AC-LL-01``..``06`` and the Limelight sections.
#. ``DECISIONS.rst`` -- ``D-002`` (XML-capable Percolator) and ``D-007`` (the
   fake endpoint, which validates against the converter's own XSD, and the
   two cautions it carries forward).
#. ``handoffs/PHASE-09-handoff.rst`` (Percolator capability resolution, the
   compatible rerun, why ``-Z`` is never passed and decoys stay in the decoy
   tables), ``handoffs/PHASE-10-handoff.rst`` (results and filters),
   ``handoffs/PHASE-11-handoff.rst`` (the newest pattern for a
   user-initiated downstream step recorded in ``events.log``;
   ``docs/developer/pdv_integration.rst``), ``handoffs/PHASE-05-handoff.rst``
   (managed installs -- the converter is one) and
   ``handoffs/PHASE-03-handoff.rst`` (the one process service).
#. ``docs/feasibility/scientific-path.rst`` -- Phase 00 ran the converter on
   the real fixture with every decoy combination; its recorded help output is
   the only source for argument names.

What this phase is, plainly
===========================

Let the scientist send a finished run to Limelight: convert it with the
pinned converter, check the XML for real, and upload it to a server the user
chooses -- only when they ask, showing where it goes first, with their
credentials never written anywhere a log, provenance record or export could
carry them. When the run's Percolator could not write XML, say so **before**
the user tries, and offer the compatible rerun that already exists (Phase 09)
-- never a rerun of Comet.

Obligations
===========

#. **One of everything**: one process service, one installer and manifest
   (the converter is a managed tool with a pinned SHA-256), one Percolator
   resolver and compatible-rerun path (Phase 09's), one results model, one
   hashing and provenance writer, one redactor.
#. **The converter's exit status means nothing** (``D-007``, measured in
   Phase 00): it exits 0 with no arguments and on unknown options. Judge
   every run by its output file -- existence, size, parse, schema -- and
   prove the schema accepts known-good output before trusting it to reject.
#. **Argument names come from Phase 00's recorded help output** and a re-run
   of the pinned JAR's help, never from memory.
#. **Tests never reach a real Limelight server.** The fake of ``D-007`` is
   mandatory and validates what it receives against the converter's
   XSD. Any upload code path that could reach a configured production server
   from a test is a defect.
#. **Credentials**: OS keychain where practical, held apart from project
   files, redacted everywhere through the one redactor. Gate item 7 greps
   **every** artefact the run produced for a seeded secret -- enumerate them
   from the run directory, not from a list.
#. **Decoys**: item 8's prevention happens at configuration time, from what
   the run's Percolator actually produced, and must agree with Phase 09's
   finding that ``--import-decoys`` cannot work with Comet's internal decoys.
#. **Analytics and network**: if the converter or uploader reports usage to
   a third party or contacts anything other than the chosen server,
   escalate it the day you find it (``D-013`` set the project's stance).
#. **Known residue, not yours unless it blocks you**: ``verify-test-gates.sh``'s
   preflight does not check for the host ``gcc`` and ``/usr/bin/timeout`` that
   the real-PDV tests need; ``EngineStep.LAUNCH_PDV`` is declared and unused.

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
  run is already about 9 hours; say what your harness costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

The provenance UI (Phase 13); packaged end-to-end GUI runs and the nightly
sandbox slot (Phase 14); the mutation-scoring and build-time questions (the
owner's); ``dev-verify.sh --mutation`` (tier 1); macOS and Windows execution
(record as unverified); naming a real sandbox server (the owner's).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-12-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), the injections you used,
and anything escalated. Report only when finished or genuinely blocked; the
owner wants no interim pauses. Short and factual; every claim is re-run, not
read.
