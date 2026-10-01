=======================================================
PHASE-06 brief -- tier 1 to the phase orchestrator
=======================================================

:Phase: 06 -- Comet Parameter Model
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 09
:Date: 2026-10-01
:Depends on: Phases 01 and 05, both signed off (see ``STATUS.rst``). No ``D-``
   item is open and none blocks this phase.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-06-worklog.rst`` and ``handoffs/PHASE-06-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/index.rst`` or ``CLAUDE.md``.

.. _p06b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* ``scripts/build.sh`` is about 19
minutes and ``scripts/verify-all-gates.sh`` about 65 more.

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at Phase 05's exit gate on the tree you start from. Tier 1 runs them
   once more, at **this** phase's exit gate.
#. **The inner loop is** ``bash scripts/dev-verify.sh`` -- it maps the diff to
   the modules it can break (``--dry-run`` shows the selection). It is never a
   gate.
#. **Its** ``--mutation`` **flag is broken** and must not be used: pitest-maven
   1.30.0 removed built-in incremental history (``History has been enabled but
   no history plugin has been installed/activated``). For mutation evidence at
   unit sign-off, run PIT on the **one** module, restricted to the classes the
   unit changed, e.g. ``mvn -pl <module> -am test-compile
   org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>``, with
   the project's Maven settings (read how ``scripts/dev-verify.sh`` invokes
   Maven and copy that). Never add a PIT plugin or change the POM's PIT
   configuration to make this faster.
#. **Unit sign-off** is: read the diff, run the affected module's tests,
   targeted PIT as above, your own production-code injections, and
   ``scripts/ci/docs-build.sh`` if ``.rst`` changed. Nothing wider -- **but
   not narrower either**: run ``bash scripts/verify-all-gates.sh --only NAME``
   for every gate whose harness reads what the unit changed. Phase 05 unit 11
   made ``docs/conf.py`` read ``manifests/tools.json`` and nobody re-ran
   ``--only traceability``, whose sandbox copy lacked ``manifests/``; it went
   red at the exit gate, two units later. If a unit touches ``docs/conf.py``,
   anything the documentation build reads, or a generated page (you will:
   ``reference/comet_parameters_generated.rst``), run ``--only docs --only
   traceability`` at sign-off.
#. Pass this section **verbatim** to every unit agent. They do not see this
   brief otherwise.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it: *Roles*, *Sign-off*, *Why phases run one at
   a time*, *What parallel agents actually share*.
#. ``CONTRIBUTING.rst``.
#. ``phases/PHASE-06-comet-param-model.rst`` -- your scope and the nine
   exit-gate items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` revision 11 -- ``R-PARAM-01``..``R-PARAM-12``,
   ``R-DOC-04``, ``R-TEST-01`` and ``AC-PAR-01``, ``02``, ``06``, ``11``.
#. ``handoffs/PHASE-05-handoff.rst`` -- what you inherit: the Tool Manager, the
   installed-tool port, and how a real Comet binary is reached. Phase 06
   depends on Phase 05 *only* for a real Comet binary to query.
#. ``handoffs/PHASE-03-handoff.rst`` -- the process service. Running
   ``comet -q`` / ``comet -p`` goes through it; there is **one** process
   launcher in this project.
#. ``docs/feasibility/`` -- Phase 00 established the ``-q`` versus ``-p``
   facts this phase rests on. Re-verify them; do not inherit them.

What this phase is, plainly
===========================

The typed model of Comet's parameter file: discover the schema, parse, write
back deterministically, validate, presets and migration. It is the scientific
core and the phase document says it is "the part most likely to be built
plausibly and wrongly". The named trap: building the schema from ``comet -p``
instead of ``-q`` silently loses ten variable-modification slots and eleven
other parameters.

**Fixtures are real output, never hand-written.** The ``comet -p`` and
``comet -q`` fixtures are captured from the real binaries at the versions in
``manifests/tools.json``, with how they were captured recorded so a successor
can reproduce them. A fixture typed by hand to match what the parser expects
is shape 2 of the signature defect: the expected value produced by the thing
under test.

Plan the units yourself, record them in the work log **before** the first
dispatch, and run them **serially**.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a recorded
  argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (anchor matches once; marker
  grepped back), restore and verify with ``sha256sum -c``. Use a private
  scratchpad subdirectory.
* **Audit the population, not only the number**: every class in
  ``target/classes`` must appear in the coverage and mutation reports.
* Locale: anything that formats numbers uses ``Locale.ROOT`` (gate item 5).
  Tests that change the default locale must restore it; six existing classes
  already do this and are escalated -- do not enable parallel test execution.
* Never weaken a gate, checksum, validation rule or threshold; never add to the
  pinned survivor set in ``scripts/verify-test-gates.sh``. Additive
  registration of a ``scripts/verify-param-gates.sh`` harness (or similar) in
  ``scripts/verify-all-gates.sh`` is approved; raise floors, never lower one.
* Commit with an explicit pathspec, never ``git add -A``. **Do not push.**
  Never force-push.
* All documentation is reStructuredText and must pass
  ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item. The
  ``TIMED_OUT`` mutation-counting question is the owner's; do not touch how
  ``scripts/build.sh`` counts.

Not yours
=========

Any JavaFX control (Phase 07); running a search (Phase 08); the nightly's step
ordering (Phase 01 residue); repairing ``dev-verify.sh --mutation`` (tier 1);
Phase 05 residue listed in its handoff.

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-06-handoff.rst`` for a successor who cannot ask you questions,
and report to tier 1: commits, each gate item with its evidence (met, partial
with residue named, or unverifiable), the injections you used, and anything
escalated. Short and factual; every claim is re-run, not read. Escalate a
blocker the day you hit it.
