=====================================================
PHASE-14 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 14 -- GUI Automation and Packaged End-to-End Harness
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 10
:Date: 2026-10-22
:Depends on: Phases 07, 10, 11, 12 and 13 (07 PASSED; the rest PARTIAL,
   platform), all signed off; Phase 13 immediately before this, after which
   the owner paused the project and resumed it on 2026-10-22. ``D-007`` is
   decided. **``D-006``'s CI-fixture half is OPEN** -- see the first
   obligation.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-14-worklog.rst`` and ``handoffs/PHASE-14-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p14b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` plus
``scripts/verify-all-gates.sh`` now take about 11 hours (build 130 min,
``tests`` about 309, the other nineteen about 223).

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 13's exit gate on the tree you start from. Tier 1 runs them once
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
   ``paramui`` and ``provenanceui`` for GUI tests in ``cometgui-app`` -- read
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

**Two hours is not enough for** ``--only tests`` **any more** (about 309
minutes). If you run it, run it detached (``setsid nohup ... &``) and watch it
with a loop that waits for its exit line -- and still never end your turn
while it runs. ``bash scripts/verify-test-gates.sh --preflight`` (one second)
tells you first whether the host has everything it needs.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-14-e2e.rst`` -- your scope, the **important** box at its top
   (assert the runner's locale), its *Risks and notes*, and the seven exit-gate
   items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` (revision 20) -- ``R-TEST-03``..``06``,
   ``AC-TST-05``..``09``, ``AC-TST-12``, ``AC-INS-01``, the canonical and
   no-XML scenarios, and *Failure-path scenarios*: that list, not your
   reading of it, is item 4.
#. ``DECISIONS.rst`` -- ``D-006`` in full (the local fixture, its conditions,
   and the half that is still open) and ``D-007`` (the fake Limelight
   endpoint).
#. ``STATUS.rst`` -- *Risks currently live*, items 1 (non-ASCII paths: the
   ``jpackage`` java-option remedy is inert) and the toolchain item
   (``jpackage`` strips the runtime's ``bin/java``); *Platform divergence, in
   two tiers*.
#. ``handoffs/PHASE-00-handoff.rst`` and ``docs/feasibility/`` (the
   ``jpackage`` spike and the headless GUI stack), ``handoffs/PHASE-02-handoff.rst``
   and ``handoffs/PHASE-07-handoff.rst`` (``FxUiDriver``, ``UiIds``, TestFX
   and Monocle), and the handoffs of Phases 09--13 for the stages the
   scenarios cross (compatible rerun, results and filters, PDV, Limelight,
   provenance). ``cometgui-app``'s ``RealProvenanceGateTest`` and
   ``RunningApplication`` are the nearest existing whole-application tests.

What this phase is, plainly
===========================

Prove the product's claims by using it as a scientist does. Tier A: start the
assembled application with an empty home and tool cache, and drive the
canonical scenario through the interface -- install the tools, set up the
search, run Comet and Percolator, filter the results, look at a spectrum,
convert and upload to the fake Limelight, check the provenance against the
files on disk, restart the application and find it all again -- recomputing
counts and hashes independently. Tier B: do the same against the **exact
packaged artefact** ``jpackage`` produces, as an installed user would have it.
Then the no-XML scenario in both tiers, the specification's failure paths,
and a release check that no test bridge ships.

Obligations
===========

#. **``D-006``'s CI-fixture half is OPEN, and you must not answer it.** Use the
   local fixture ``D-006`` chose (the two Crux K562 mzML files and the UniProt
   human proteome), fetched by checksum into gitignored ``scratch/``, never
   committed, with its CRLF guard. Do **not** present it as the CI fixture,
   commit it, or wire a CI job that depends on it as though it were. Where an
   item needs CI, record it as waiting on ``D-006`` and say what the CI job
   would need; escalate to tier 1 if anything else turns on the choice.
#. **Packaging traps known before you start** -- budget for them, as the phase
   document says:

   * ``jpackage`` strips the runtime's ``bin/java``, but the product launches
     two JARs (the Limelight converter and PDV). Prove both start from the
     installed artefact, with no JDK on ``PATH``.
   * A packaged launcher whose process has no UTF-8 locale cannot open a
     non-ASCII path, and ``-Dsun.jnu.encoding`` in the java-options is inert
     (*Risks currently live*, item 1). The end-to-end harness checks
     ``sun.jnu.encoding`` and fails loudly when it is not UTF-8; Tier B runs a
     non-ASCII project path through the real launcher. The launcher repair
     itself is a Phase 16 obligation -- if Tier B cannot pass without it,
     escalate rather than work around it.
   * Paths that differ once installed (the tool cache, the working directory,
     resources inside the image).

#. **One of everything.** One ``FxUiDriver``, extended over the whole
   application; one process service; the product's own installer for tools;
   the ``D-007`` fake for Limelight -- **no test reaches a real Limelight
   server or any network host but the tools' pinned GitHub releases**. If a
   test needs a seam, add it through the UI command path; add no product
   feature to make a test easier.
#. **A test bridge, if Tier B uses one, is test-only and loopback-only**, and
   gate item 7's release check must fail when the bridge is deliberately built
   in -- shown red, not assumed.
#. **Independent oracles.** Counts, digests and the "Comet was not rerun"
   assertion are computed by the test from files on disk and process records,
   never read back from the product's own summary.
#. **Gate item 5 is three injections into production code**, each making the
   canonical test fail on its own assertion: an inverted q-value comparison, a
   dropped provenance entry, a bypassed downstream stage.
#. **No fixed sleeps** (item 6): wait on observable state with a bound. Prove
   it with a check that fails on a ``Thread.sleep`` or equivalent in the
   end-to-end sources.
#. **Known residue, not yours unless it blocks you**: the Limelight
   wrong-key refusal quotes two characters of the key into the logs; surefire
   reports of nested test classes overwrite each other under the outer
   class's name (audit any harness of yours for it); ``pdv`` control 5b is
   timing-flaky; ``EngineStep``'s ``LAUNCH_PDV``, ``CONVERT_LIMELIGHT`` and
   ``UPLOAD_LIMELIGHT`` are unused.

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
* **Nothing on the JavaFX thread starts a process or reads a large file.**
* Locale: number formatting uses ``Locale.ROOT``; do not enable parallel test
  execution.
* ``D-001`` attribution and the ``D-009`` copyright line apply as before.
* Never weaken a gate, checksum, validation rule or threshold; never add to the
  pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors, never
  lower one. A new harness for this phase's gate, registered additively in
  ``scripts/verify-all-gates.sh``, is approved. **Keep it lean**: the full gate
  run is already about 11 hours, and an end-to-end scenario is slow by
  nature -- say what your harness costs, and prefer running each scenario
  once per harness run over once per control.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

The CI fixture choice (``D-006``, the owner's); the launcher's locale repair
and release signing (Phase 16); the version matrix, performance and nightly
hardening (Phase 15); the mutation-scoring and build-time questions (the
owner's); ``dev-verify.sh --mutation`` (tier 1); macOS and Windows packaging
and execution (build the configuration where you can, record execution as
unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-14-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), what waits on ``D-006``,
the packaging problems found and how each was resolved, the injections you
used, and anything escalated. Report only when finished or genuinely blocked;
the owner wants no interim pauses. Short and factual; every claim is re-run,
not read.
