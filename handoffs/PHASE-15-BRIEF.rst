=====================================================
PHASE-15 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 15 -- Version Matrix, Performance and Hardening
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 11
:Date: 2026-11-05
:Depends on: Phase 14, signed off PARTIAL (platform), then the E12 repair
   (``status-fix-p14-p15``) and the build-time repair
   (``status-fix-build-time``), both signed off. The owner paused the project
   after the E12 repair and resumed it on 2026-11-05: *"Resume Phase 15"*.
   ``D-014`` and ``D-015`` (how the mutation gate scores) were decided the
   same day. **``D-006``'s CI-fixture half is OPEN** -- see obligation 1.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-15-worklog.rst`` and ``handoffs/PHASE-15-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``.

.. _p15b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* Since the build-time repair,
``scripts/build.sh`` takes about 156 minutes and ``scripts/verify-all-gates.sh``
about 91 (four controls at once on this 64-core host; serial on 4 cores or
fewer). It was 11 hours at Phase 14. **Keep it from growing back.**

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both on the tree you start from (``c9d037c``, plus documentation since).
   Tier 1 runs them once more, at **this** phase's exit gate.
#. **The inner loop is** ``bash scripts/dev-verify.sh`` -- it maps the diff to
   the modules it can break (``--dry-run`` shows the selection). It is never a
   gate.
#. **Its** ``--mutation`` **flag is broken** and must not be used until a unit
   of this phase repairs it (obligation 3). Until then, for mutation evidence
   at unit sign-off, run PIT on the **one** module, restricted to the classes
   the unit changed, e.g. ``mvn -pl <module> -am test-compile
   org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>``, with
   the project's Maven settings (copy how ``scripts/dev-verify.sh`` invokes
   Maven).
#. **Unit sign-off** is: read the diff, run the affected module's tests with
   ``verify`` (not ``test`` -- Phase 14 missed nine SpotBugs findings that
   way), targeted PIT as above, your own production-code injections, and
   ``scripts/ci/docs-build.sh`` if ``.rst`` changed. Nothing wider -- **but not
   narrower either**: run ``bash scripts/verify-all-gates.sh --only NAME`` for
   every gate whose harness reads what the unit changed (read
   ``scripts/verify-all-gates.sh --help`` and ``--list``). Several ``--only``
   names run side by side; ``--jobs 1`` is serial.
#. **``--only tests`` now takes about 90 minutes**, and any change to
   ``scripts/build.sh``'s gates stage, the POMs' coverage or PIT settings, or
   the evidence census makes it relevant. If a unit touches those, run it
   before that unit is signed off -- detached (``setsid nohup ... &``), with a
   loop that waits for its exit line. **Never end your turn while your own
   check is still running**; nothing will wake you, and two orchestrators have
   stalled for exactly this reason.
#. Pass this section **verbatim** to every unit agent. They do not see this
   brief otherwise.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst`` and ``docs/developer/testing.rst`` (which this phase
   completes).
#. ``phases/PHASE-15-hardening.rst`` -- your scope, its *Risks and notes*,
   and the six exit-gate items. That gate is the standard; you may not weaken
   any part.
#. ``specification.rst`` (revision 21) -- ``R-TEST-02``, ``R-TEST-07``,
   ``R-TEST-08``, ``AC-TST-01``..``04``, ``AC-TST-10``, ``AC-TST-11``,
   ``AC-RES-10``, ``AC-REL-01``, the version and platform matrix, and every
   ``R-`` rule on managed-tool manifests.
#. ``DECISIONS.rst`` -- ``D-002``, ``D-003`` (the managed Percolator
   versions), ``D-006`` in full (the local fixture and the half that is still
   open), ``D-008``, ``D-010`` and ``D-011`` (Comet versions and the Intel
   Mac), ``D-013``, and **``D-014`` and ``D-015``**, which this phase
   implements.
#. ``STATUS.rst`` -- *Risks currently live*; *Platform divergence, in two
   tiers*; the mutation drift section (``status-mutation-drift``); and the two
   repair sections, ``status-fix-p14-p15`` and ``status-fix-build-time``,
   including their residue.
#. ``handoffs/PHASE-14-handoff.rst``, and the handoffs of Phases 05, 06, 08
   and 09 (manifest, Comet parameters, the Comet and Percolator runs and
   probes). ``handoffs/FIX-BUILD-TIME-worklog.rst`` for how the gate suite now
   runs and what its controls share.

What this phase is, plainly
===========================

Take the product from "works on the reference fixture" to "known to work
across every Comet and Percolator version and platform it offers, at the data
sizes it claims", and make the checks that keep it so run on their own: a
populated nightly and matrix CI, a manifest check that goes red when upstream
changes a file, a mutation gate graded per critical package, performance
baselines, determinism with a justified tolerance, and a flakiness policy with
nothing release-critical quarantined.

Obligations
===========

#. **``D-006``'s CI-fixture half is OPEN, and you must not answer it.** The
   local fixture (the two Crux K562 mzML files and the UniProt human proteome,
   fetched by checksum into gitignored ``scratch/``) is for this host. Do
   **not** commit it, present it as the CI fixture, or wire a CI job that
   depends on it as though it were. A nightly or matrix job that needs real
   data is written so that its fixture is one named input, and recorded as
   waiting on ``D-006`` -- with what it would need (size, licence, where it
   would live). Escalate to tier 1 if anything else turns on the choice.
#. **GitHub-hosted runners have 2 or 4 cores** (owner, 2026-11-05). Every CI
   job must fit that; every concurrency setting falls back to today's serial
   value at 4 cores or fewer, as ``cometgui.pit.threads`` and
   ``verify-all-gates.sh``'s job count already do.
#. **``D-014`` and ``D-015``: the mutation gate, scored as the owner
   decided.** In ``scripts/build.sh``: ``TIMED_OUT`` counts as detected, and
   is listed by name; every critical package in the PIT ``targetClasses`` list
   is graded on its own at 80% or more; a critical package with no mutations
   is a failure. Packages that fall below 80% when graded separately get
   **tests**, never a lower threshold, an exclusion or a survivor. The
   ``tests`` harness's mutation controls must prove both rules bite: a package
   below 80% inside a module above it goes red, and a ``TIMED_OUT`` mutant is
   no longer read as a miss. ``org.cometgui.results.export.*``, left out of
   PIT's targets at Phase 09, is added. Changing the PIT ``targetClasses``
   list in the parent POM, **only to add critical packages**, is approved.
   Repair ``scripts/dev-verify.sh --mutation`` in the same unit or the next.
#. **No dedicated performance runner exists.** Item 4 says thresholds pass
   *on the dedicated runner*. Build the suites and thresholds, measure them
   here, record the baseline, the host and its load; then record the
   dedicated-runner half as **unverifiable** and say what such a runner would
   need. Do not quietly treat this shared host as the dedicated runner. A
   threshold set so loosely it cannot fail is a defect; show each one red.
#. **The matrix runs real binaries.** Every combination the manifest offers
   and the probe can load on Linux x86-64 is run here; every other
   combination (macOS, Windows, Intel Mac per ``D-011``, Thermo RAW) is
   recorded as **unavailable or unverified**, by name, never as passed.
   Version-specific differences are recorded as advisories, not special-cased
   (the phase document's risk note).
#. **The manifest verification job** downloads each pinned artefact and
   checks it. It must be shown red with a checksum altered, and with an
   artefact missing upstream (simulated, not by touching upstream).
#. **Residue that is now this phase's:**

   * **A failed tool install records no reason.** ``ManagedToolManager``
     discards the exception, which is why tier 1 could not name the cause of
     an ``e2e`` red at the build-time sign-off. Hardening, not a feature:
     the reason reaches the install's state, the Tool Manager section and the
     log, redacted as every other log is. Test it with the real failure
     shapes (digest mismatch, extraction, probe).
   * ``PercolatorCapabilityProbeTest.theWorkspaceIsCleanedUp`` counts every
     ``cometgui-percolator-probe-*`` directory in the shared ``/tmp``, so a
     concurrent probe moves it; builds leave such directories behind. Make
     the probe's workspace a parameter the test owns, and find what leaves
     them.
   * ``tests`` control 5 sizes from the working tree's ``cometgui-ui``
     ``jacoco.xml``. Make it read its own sandbox's, without adding a second
     whole-sandbox build.
   * The nightly pipeline is red by the owner's decision until this phase
     replaces its stubs. Replace them; none may exit 0 having run nothing.
   * Known, not yours unless it blocks you: ``pdv`` control 5b is
     timing-flaky (apply the flakiness policy to it); surefire reports of
     nested test classes overwrite each other under the outer class's name
     (audit any harness of yours for it).

#. **Determinism** (item 3): repeat a pinned run, compare, and justify the
   tolerance in writing from what Comet and Percolator actually vary (seeds,
   threads, floating point), not from what happened to pass.
#. **Flakiness policy** (item 6): written down, enforced by a check, and no
   release-critical test quarantined. Quarantine is never a fix for a test
   that fails because of the product.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a recorded
  argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (the old anchor is gone, not just
  a marker found -- a marker can match elsewhere), restore and verify with
  ``sha256sum -c``. **Count the tests that ran** before believing a red or a
  green.
* **A test proves the value, not that nothing threw**: digests, argument
  arrays, row counts, the named file in the message.
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* **Nothing on the JavaFX thread starts a process or reads a large file.**
* **No test reaches a real Limelight server or any network host but the
  tools' pinned upstream releases.**
* Locale: number formatting uses ``Locale.ROOT``; do not enable parallel test
  execution inside a module.
* ``D-001`` attribution and the ``D-009`` copyright line apply as before.
* Never weaken a gate, checksum, validation rule or threshold; never add to
  the pinned survivor set in ``scripts/verify-test-gates.sh``. Raise floors,
  never lower one. A new harness for this phase's gate, registered additively
  in ``scripts/verify-all-gates.sh`` (with ``gate_locks`` if it writes what
  another control reads), is approved. **Keep it lean** and say what it costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.

Not yours
=========

The CI fixture choice (``D-006``, the owner's); release signing, the
launcher's locale repair and release qualification (Phase 16); new product
features; macOS and Windows execution (build the configuration where you can,
record execution as unverified).

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-15-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits, each gate item with its evidence
(met, partial with residue named, or unverifiable), what waits on ``D-006``,
the matrix (run, unavailable, unverified), the mutation scores per package
before and after ``D-014``/``D-015``, the injections you used, and anything
escalated. Report only when finished or genuinely blocked; the owner wants no
interim pauses. Short and factual; every claim is re-run, not read.
