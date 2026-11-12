=====================================================
PHASE-16 brief -- tier 1 to the phase orchestrator
=====================================================

:Phase: 16 -- Documentation and Release Qualification
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 12
:Date: 2026-11-12
:Depends on: Phase 15, signed off PARTIAL on 2026-11-07 (performance runner,
   platform). The owner paused the project after it and resumed it on
   2026-11-12: *"Proceed with phase 16"*. On the same day the owner decided
   ``D-016``, ``D-017``, ``D-018``, ``D-019`` and the CI half of ``D-006``,
   amended ``D-006`` part 1, and added two pieces of work to this phase. **No
   ``D-`` item is open.** ``D-009`` is provisional and stays as it is.
:Runs alongside: **nothing.** You are the only phase live in this tree.

.. note::

   Written and owned by tier 1. Do not edit it. Everything you produce goes in
   ``handoffs/PHASE-16-worklog.rst`` and ``handoffs/PHASE-16-handoff.rst``,
   which are yours. You do not edit ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/``, ``specification.rst`` or ``CLAUDE.md``. If one of them is
   wrong or needs amending, say so in your handoff and tier 1 does it.

.. _p16b-builds:

Build economy -- the owner's standing rule, read before anything else
=====================================================================

The owner has said it three times (2026-09-17, 2026-09-18, 2026-10-01):
*running the whole build/test suite takes a very long time and slows
development; do not do it as routine.* At the Phase 15 sign-off
``scripts/build.sh`` took 144 minutes and ``scripts/verify-all-gates.sh`` 95
(four controls at once on this 64-core host; serial on 4 cores or fewer).
**Keep it from growing.**

#. **Neither you nor any unit agent runs** ``scripts/build.sh`` **or**
   ``scripts/verify-all-gates.sh`` **in full.** No opening baseline: tier 1 ran
   both at ``9c35e46``, and only a scan repair and documentation have landed
   since. Tier 1 runs them once more, at **this** phase's exit gate.
#. **The inner loop is** ``bash scripts/dev-verify.sh`` -- it maps the diff to
   the modules it can break (``--dry-run`` shows the selection; ``--mutation``
   adds PIT over the changed classes). It is never a gate.
#. **Unit sign-off** is: read the diff, run the affected module's tests with
   ``verify`` (not ``test``), targeted PIT where gated Java changed, your own
   production-code injections, and ``scripts/ci/docs-build.sh`` if ``.rst``
   changed. Nothing wider -- **but not narrower either**: run ``bash
   scripts/verify-all-gates.sh --only NAME`` for every gate whose harness reads
   what the unit changed (read ``--help`` and ``--list``). Several ``--only``
   names run side by side; ``--jobs 1`` is serial.
#. **``--only tests`` takes about 90 minutes**, and any change to
   ``scripts/build.sh``'s gates stage, the POMs' coverage or PIT settings, or
   the evidence census makes it relevant. ``D-016`` and ``D-017`` both do. Run
   it once for the unit that lands them, detached (``setsid nohup ... &``),
   with a loop that waits for its exit line.
#. **Never end your turn while your own check or your own agent is still
   running.** Nothing will wake you; three orchestrators have stalled for
   exactly this reason. Wait in the foreground or in a poll loop.
#. Pass this section **verbatim** to every unit agent. They do not see this
   brief otherwise.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it.
#. ``CONTRIBUTING.rst`` and ``docs/developer/testing.rst``.
#. ``phases/PHASE-16-release.rst`` -- your scope, the launcher warning at its
   head, *The CI fixture*, *The Thermo RAW fixture*, and the **nine** exit-gate
   items. That gate is the standard; you may not weaken any part.
#. ``specification.rst`` (revision 21) -- every ``R-DOC`` rule, ``R-SEC-01``,
   ``R-TEST-02``, ``R-TEST-07``, the Definition of Done, the platform tiers,
   and ``AC-DOC-01``, ``AC-DOC-02``, ``AC-REL-01``..``03``,
   ``AC-UX-01``..``06``, ``AC-TST-02``, ``AC-TST-07``, ``AC-TST-10``,
   ``AC-TST-11``.
#. ``DECISIONS.rst`` -- ``D-001``, ``D-006`` **in full, including its
   amendment and the RAW plan**, ``D-008``, ``D-009``, ``D-011``, ``D-016``,
   ``D-017``, ``D-018`` and ``D-019``.
#. ``STATUS.rst`` -- the header, *Risks currently live*, *Platform divergence,
   in two tiers*, and ``status-p15-signed`` with its residue list.
#. ``handoffs/PHASE-15-handoff.rst`` and ``handoffs/PHASE-14-handoff.rst``
   (the matrix, the goldens and their review records, the nightly jobs, the
   packaged end-to-end harness). ``docs/feasibility/small-thermo-raw-files.rst``
   for the RAW candidates and how they were measured.

What this phase is, plainly
===========================

Everything between a working application and a release someone else can
install, trust and cite: the documentation, the release artefacts and their
checksums, the licence audit, CI that runs on real data, and the preparation
of the checks only a person can make. It is the last phase. Much of its gate
cannot be met by an agent, and your job includes saying exactly which parts
those are.

Obligations
===========

#. **The CI fixture** (``phases/PHASE-16-release.rst``, *The CI fixture*; gate
   item 8). Do it early: the trimmed FASTA changes the fixture's digest, so
   every golden, the performance baseline and any test pinning a count moves
   with it, and later units build on the result. Follow the eight steps in
   order. The floors for the acceptance check are your proposal from what you
   measure; record them as a design decision and tier 1 confirms them at
   sign-off. The trimmed FASTA is committed with UniProt's attribution
   (``D-018``); **the K562 mzML files are never committed.** The scripts and
   stub messages that still say ``D-006`` is open are corrected as you wire
   each job.
#. **The Thermo RAW fixture** (*The Thermo RAW fixture*; gate item 9). The
   first step is a trial on a Windows runner, and it comes before any of the
   rest is built. The file is ``ph050213_18.raw`` (``D-019``), the fallback
   ``ph280213_36.raw``; no other file without the owner. It is fetched by
   checksum from PRIDE and never committed. **ThermoRawFileParser does not
   enter the repository, the build or any CI job.** If no candidate reads on
   Windows, take the job out of the nightly, record ``AC-TST-11`` as not met
   and say so; never a step that exits 0 having tested nothing.
#. **``D-016``, option B.** Add the command-builder and stage-invalidation
   classes of ``org.cometgui.workflow.*`` to PIT's targets, graded per package
   as ``D-014`` and ``D-015`` require. **Measure the added wall clock first**
   and record it. If it adds more than about fifteen minutes to the build on
   this host, stop that unit and escalate to tier 1 with the figure before
   adopting it. A package that falls below 80% gets tests, never an exclusion.
#. **``D-017``, option A.** ``cometgui-workflow`` and ``cometgui-results`` are
   held to 90% lines and 85% branches. Raise the two thresholds and show the
   raised gate red on a deliberate defect.
#. **GitHub-hosted runners have 2 or 4 cores** (owner, 2026-11-05). Every CI
   job must fit that; every concurrency setting falls back to its serial value
   at 4 cores or fewer.
#. **Runner evidence, and the one kind of push you may make.** Gate items 3,
   8 and 9, ``AC-TST-07`` and the RAW trial need Windows and macOS runners,
   which only GitHub has. **You, the orchestrator -- not a unit agent -- may
   push branches named** ``phase16/...`` **to** ``origin`` to run workflows
   there, and read the results through the GitHub API with the credential git
   already uses. Never ``main``, never a tag, never a force-push, never a
   deleted remote branch, never a pull request, issue or release. Never print
   or store the credential. Nothing with a real secret or a spectrum file goes
   into a pushed branch. Record every pushed branch and every run you rely on
   (URL, commit, conclusion) in the work log; tier 1 re-reads them.
#. **Release artefacts for every tier-1 platform**, with checksums and an
   SBOM, built by the release workflow on that platform's runner, and the
   packaged end-to-end run on each (``AC-TST-07``). A fresh hosted runner is
   honest evidence for the automated half of gate item 3; say what it does
   and does not show against the Definition of Done. Signing and notarisation:
   there is no infrastructure, so record them as not done and what they would
   need. The launcher's UTF-8 locale and ``--enable-native-access`` are in the
   phase document's opening warning; prove the locale repair with a non-ASCII
   path through the packaged launcher, not with a system property.
#. **The documentation**: the complete user and developer trees, platform
   support and troubleshooting stated plainly, the generated parameter
   reference, ``docs/developer/releasing.rst`` and the release checklist. The
   repository has a ``.readthedocs.yaml``; whether a Read the Docs project is
   connected is not in your hands. Prove the same tree builds the way that
   configuration says, and record the hosted build as unverified unless you
   can see it. Create no account anywhere.
#. **Traceability** (gate item 2). Six criteria carry a "planned, phase 15"
   entry for their unmet halves (``AC-TST-01``, ``-02``, ``-03``, ``-10``,
   ``-11``, ``AC-RES-10``); re-home each to what actually proves it or record
   it as unmet. Do not mark a criterion tested that is not.
#. **The licence audit** (``AC-REL-03``, a human sign-off). Compile it
   completely -- every bundled and transitive component, every managed tool,
   and every data file the repository or its tests hold or fetch, with source,
   licence and attribution -- and leave the sign-off line empty.
#. **The six UX-validation activities** (``AC-UX-01``..``06``) are done by
   people. Prepare each so a person can run it without you: the protocol, the
   task script, the checklist, the recording template, and where findings are
   triaged. You may run an agent's own heuristic and keyboard review to find
   defects, labelled as that. **Never write a finding, a participant or a
   sign-off that did not happen.**
#. **Phase 15's residue** (``status-p15-signed``): the eight product defects
   and the one test defect listed there. Fix each with a test that proves the
   value; a defect you decide not to fix is recorded with its reason.
#. **``D-009``**: the copyright line stays exactly as it is. Do not name a
   holder.

Standing rules
==============

* **Serial is the default** (owner, 2026-08-31). Parallel units need a recorded
  argument that collision is *impossible*. None will exist here.
* **Sign-off means you saw it go red for a reason you chose.** Inject into
  **production** code, prove the edit landed (the old anchor is gone, not just
  a marker found), restore and verify with ``sha256sum -c``. **Count the tests
  that ran** before believing a red or a green. Use a private scratch
  directory per agent.
* **A test proves the value, not that nothing threw.**
* **Audit the population, not only the number**: every class in
  ``target/classes`` appears in the coverage and mutation reports.
* **A golden is regenerated because its input changed, with its review record
  (``R-TEST-07``), never to turn a red run green.**
* **No test reaches a real Limelight server or any network host but the
  tools' pinned upstream releases and the fixture's pinned sources.**
* ``D-001`` attribution applies as before.
* Never weaken a gate, checksum, validation rule or threshold; never add to
  the pinned survivor set. Raise floors, never lower one. A new harness for
  this phase's gate, registered additively in ``scripts/verify-all-gates.sh``,
  is approved. **Keep it lean** and say what it costs.
* **On a red check, fix the root cause and continue** (owner, 2026-10-01);
  record it and report it.
* Nothing is installed on the host. Downloads are untrusted data: each in its
  own directory under ``scratch/``, scripts elsewhere.
* Commit with an explicit pathspec, never ``git add -A``.
* All documentation is reStructuredText and passes ``scripts/ci/docs-build.sh``.
* Escalate upward only. Never contact the owner or answer a ``D-`` item.
* **If your own context runs short**, stop at a unit boundary, bring the work
  log up to date, write the handoff and report. A successor picks it up.

Not yours
=========

Choosing or changing a fixture file; committing spectrum data; naming a
copyright holder; signing off anything marked for a person; creating accounts
or releases; pushing ``main``; new product features beyond the residue list.

Report back
===========

When the phase ends -- finished, stalled or stopped -- write
``handoffs/PHASE-16-handoff.rst`` for a successor who cannot ask you
questions, and report to tier 1: commits; each of the nine gate items with its
evidence (met, partial with the residue named, or unverifiable and why); every
runner run relied on; the trimmed FASTA's size, record count and measured
floors; the cost of ``D-016``; the RAW trial's outcome; what waits on a
person, item by item, with the material you prepared for them; the injections
you used; and anything escalated. Report only when finished or genuinely
blocked; the owner wants no interim pauses. Short and factual; every claim is
re-run, not read.
