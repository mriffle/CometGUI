=============================================================
PHASE-04 resumption brief -- tier 1 to the phase orchestrator
=============================================================

:Phase: 04 -- Hashing and Provenance Core
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 05
:Date: 2026-09-01
:Depends on: Phase 01 (PARTIAL, signed off), Phase 03 (PARTIAL, signed off)
:Runs alongside: **nothing.** You are the only phase live in this tree.
:Expected grade: **PARTIAL** -- read :ref:`p04b-grade` before you plan anything.

.. note::

   This document is written by tier 1 and owned by tier 1. Do not edit it.
   Everything you produce goes in ``handoffs/PHASE-04-worklog.rst`` and
   ``handoffs/PHASE-04-handoff.rst``, which are yours.

Phase 04 was **paused part-built** by the owner on 2026-08-31, not failed and
not abandoned. Your job is to finish it and to make its numbers mean something.

Read first, in this order
=========================

Read them properly, not by grep.

#. ``ONBOARDING.rst`` -- all of it. In particular *Roles*, *Sign-off*, *Why
   phases run one at a time*, and *What parallel agents actually share*.
#. ``CONTRIBUTING.rst`` -- environment, commit, gate, documentation and handoff
   conventions.
#. ``phases/PHASE-04-provenance-core.rst`` -- your phase: scope, deliverables
   and the seven exit-gate items. That gate is the standard you are held to and
   you may not weaken any part of it.
#. ``handoffs/PHASE-04-handoff.rst`` (823 lines) -- what the paused phase
   built, what it measured, and what it warns you about. **Every headline
   number in it was taken from a tree another phase was changing.** Its section
   *The first thing the next agent should do* is your starting sequence.
#. ``handoffs/PHASE-04-worklog.rst`` (1060 lines) -- the thirteen work units,
   every sign-off entry, the exact defect injected for each and the exact
   failure text it produced, and the class-population census verbatim.
#. ``handoffs/PHASE-03-handoff.rst`` -- the process service, the sibling module
   that shares ``cometgui-domain`` with you. Read *Surprises a later phase must
   know* and *Escalated to the main orchestrator*; item 4 of the latter is
   yours to answer (see :ref:`p04b-carried`).
#. ``STATUS.rst`` -- the Phase 03 sign-off, *Platform divergence, in two
   tiers*, *A seventh shape*, and *A sixth shape*.
#. ``specification.rst`` -- only the sections your phase names. It is 160 KB;
   do not read it whole. ``R-PROV-01``..``R-PROV-05`` and ``R-SEC-03`` are the
   rules you deliver.
#. ``DECISIONS.rst`` -- no ``D-`` item blocks you. None may be answered by any
   agent, including you.

What is already done, and what is not
=====================================

.. list-table::
   :header-rows: 1
   :widths: 6 30 18 46

   * - #
     - Unit
     - State
     - What that means for you

   * - 1-8
     - Hasher, atomic writer, secret rule set, manifest records, hash cache,
       2 GB proof, canonical JSON writer, event log
     - **Signed off**
     - The previous phase orchestrator read each diff, re-ran each gate and
       injected a defect into each. Do not re-do them. Do re-measure them.

   * - 9
     - Strict JSON reader and round-trip suite (``9639b77``)
     - **LANDED, NOT SIGNED OFF**
     - Its diff was never read, its gate never re-run, nothing injected.
       ~5400 lines.

   * - 10
     - ``provenance.rst`` report (``9317abc``)
     - **LANDED, NOT SIGNED OFF**
     - Same. ~3600 lines. Its sample report has never been through Sphinx by
       anyone but the agent that wrote it.

   * - 11
     - Seeded-secret grep over generated artefacts
     - **NOT STARTED**
     - Gate item 6's missing half. The item is ``PARTIAL`` until this exists.

   * - 12
     - Documentation
     - **NOT STARTED**
     - ``docs/reference/provenance_format.rst`` and
       ``docs/developer/provenance_schema.rst`` are still Phase 01 stubs.

   * - 13
     - Gate enablement and falsifiability
     - **Partly done**
     - The mutation switch is on. The final clean run and
       ``scripts/verify-all-gates.sh`` were never run.

Your order of work, and it is not negotiable
=============================================

**1. Re-measure before you resume.** The first thing you do is take the
numbers, not continue the work. Nothing in the handoff is evidence for a gate.
The sequence is in ``handoffs/PHASE-04-handoff.rst`` under *The first thing the
next agent should do* and it starts with ``mvn -pl cometgui-domain install``,
which is not optional -- PIT resolves from ``_build/m2repo``, not the reactor.
Run the class-population census from the work log over ``cometgui-provenance``
and over ``cometgui-domain`` and **read its output before you read any
percentage**. A percentage over an incomplete population is worse than no
percentage. Report the census output to me in your first status message.

**2. Sign off units 9 and 10 properly, or send them back.** This is the first
real work of the resumption and it is the part nobody has done. Signing off
means all three of: you read the diff; you re-ran the gate yourself; you
injected a defect the unit never tried, watched it fail, and reverted it. Two
specific obligations the handoff names:

* run ``sphinx-build -n -W`` over unit 10's generated sample report. It has
  never been run by anyone but its author, and the RST half of gate item 6
  rests on it;
* re-check the nine PIT survivors listed in the handoff's *Open PIT survivors*.
  All were reported killed or diagnosed; **none has been confirmed.** Two are
  argued to be equivalent mutants whose fix is to delete code rather than write
  a test -- check that argument rather than accepting it.

Unit 9 left five design questions for whoever signs it off. Answer them with
the diff in front of you, or record why each is deferred. Do not answer them
from the handoff's summary of them.

**3. Then units 11, 12 and 13, in that order.** Not before. Unit 11 is gate
item 6's missing half and needs unit 10 to exist and be trusted.

Work units run serially. This is the owner's rule
=================================================

**Serial is the default.** Running two phase agents at once requires a
positive, recorded argument that collision is *impossible*, written into the
work log *before* they start. **"They touch different files" is not that
argument and has been proven false in this very phase.** Two agents in one
checkout also share the Maven working tree, ``_build/m2repo``, the scratchpad
root, formatter invocations, ``docs/_build/html``, the global documentation
gate, and the git index -- and two phase-local ``flock`` files do not serialise
against each other.

The consequence that matters is not friction, it is measurement: **a coverage
or mutation number taken while an agent is mid-landing can read HIGH**, because
a class whose test will not compile leaves the sample rather than scoring zero.
That is how this phase once reported "All coverage checks have been met" over a
class carrying 79 uncovered mutations.

If in doubt, serialise. The cost of a serial run is time. The cost of a
collision is the owner's money and a defect that looks like someone's code.

The injection protocol
======================

An injection is evidence only if the edit is proven to have landed. Every one
of these was paid for once already.

* Assert the anchor text occurs **exactly once** before writing. Several
  classes here match a naive replace in more than one place, or in a **Javadoc
  example** rather than in code -- and a Javadoc-only hit changes no behaviour,
  so it produces a false "the gate is weak" verdict.
* Confirm the **compiled class** changed, not only the source. An injection
  that reaches ``.java`` and not ``.class`` reports green; that is the eighth
  catalogued shape and Phase 03 found it in its own work.
* ``grep`` a marker back out of the target file before running anything.
* Restore with ``git checkout --`` and confirm the tree is clean. **Do not
  trust your own backup**: running an inject script twice overwrites the backup
  with the injected version.
* Work in a **private scratchpad subdirectory**. Sibling agents share one root,
  and one agent overwriting another's ``inject.py`` is how two defects in this
  phase silently stopped existing.
* A revert that preserves mtime does not rebuild. ``touch`` the reverted file
  and re-run; ``shutil.copy2`` made a clean tree look broken here once.
* **Read why a build is red, never only that it is.** An ArchUnit injection
  once "failed" because ``-DfailIfNoSpecifiedTests`` needs the ``surefire.``
  prefix, so an upstream module aborted before the rule ever ran.
* **If a defect that previously failed suddenly passes, suspect the injection
  before the gate.**

.. _p04b-grade:

Expected grade: PARTIAL. Document for the evidence, not for a verdict
=====================================================================

I am telling you this up front so that you write the record honestly rather
than writing toward ``PASSED``.

The grading rule is in ``STATUS.rst`` under *Platform divergence, in two
tiers*: *"we could not run this code on that platform"* is a **testing gap**
and does not cap a grade; *"there is different code on that platform and it has
never run"* is **unverified behaviour** and does. This phase has the second
kind, in tier B of its own divergence list -- ``ATOMIC_MOVE`` under contention,
absolute-path validation, and ``toRealPath`` as a cache key. Gate item 5's
promise is proved on POSIX and unproven on Windows.

So the honest outcome is ``PARTIAL`` with the residue named precisely. That is
not a lower bar and it does not licence a weaker gate: **every item that can be
met here must be met here, with evidence.** What it changes is what you write
down. A phase that reaches ``PARTIAL`` with seven items evidenced and the
residue named exactly is worth more to this project than one that reaches
``PASSED`` with a caveat in prose, and ``ONBOARDING.rst`` forbids the second
outright.

If you find you can meet an item I expect to be residue, say so with the
evidence and I will grade it on what you show me, not on this paragraph.

Files
=====

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - You own
     - Notes

   * - ``cometgui-provenance/**``
     - Your module, main and test.

   * - ``cometgui-domain/src/**/org/cometgui/domain/secrets/**``
     - The one shared secret rule set. ``cometgui-process`` also depends on it;
       a change here affects Phase 03's signed-off work, so change it only with
       a reason and say so in the work log.

   * - ``docs/reference/provenance_format.rst``,
       ``docs/developer/provenance_schema.rst``
     - Unit 12. Still Phase 01 stubs.

   * - ``handoffs/PHASE-04-worklog.rst``,
       ``handoffs/PHASE-04-handoff.rst``
     - Yours to write. The worklog is written as you go, not at the end.

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - Do not touch
     - Why

   * - ``STATUS.rst``, ``DECISIONS.rst``, ``phases/*.rst``, ``ONBOARDING.rst``,
       ``CLAUDE.md``, ``specification.rst``
     - Tier 1 owns them. Report upward instead of editing.

   * - ``scripts/build.sh``, ``scripts/verify-test-gates.sh``
     - **Tier 1 is implementing the per-class population census in these two,
       immediately after this phase lands.** Editing them would collide with
       that work. Report anything you need there to me.

   * - ``cometgui-process/**``
     - Phase 03, signed off. A change invalidates that sign-off.

   * - ``org.cometgui.provenance.redaction``
     - **It no longer exists and must not be recreated.** The rule set moved to
       ``org.cometgui.domain.secrets`` because two phases built it twice and
       the keyword lists had already diverged within hours.

One deliberate addition to unit 13, and it is droppable
--------------------------------------------------------

Phases 01 and 02 each ship a falsifiability harness under ``scripts/``,
registered as a control in ``scripts/verify-all-gates.sh``. Phase 03 has none
and escalated that as debt before Phase 08 depends on it. If Phase 04 also ends
without one, the debt doubles.

**If unit 13 has room once gate items 1-7 are evidenced**, assemble
``scripts/verify-provenance-gates.sh`` from the injections already recorded in
your work log -- they each carry the exact command, the file and the exact
failure text, so this is assembly from a record, not invention -- and register
it as a control in ``scripts/verify-all-gates.sh``. It must be seen to bite.
**Never lower a floor in that script**: a run that grades fewer controls than
before is a failure even when it is green.

**If it threatens the phase, escalate it to me and drop it.** Do not silently
omit it, and do not let it displace unit 11, which is a gate item.

.. _p04b-carried:

Carried in from elsewhere, already routed -- do not re-derive
=============================================================

These are settled. Reading them again and re-deciding them costs money and
changes nothing.

* **The ``sun.jnu.encoding`` defect.** An accented directory name breaks the
  product before any CometGUI code runs, and the obvious
  ``-Dsun.jnu.encoding=UTF-8`` fix is **inert** -- measured twice,
  independently. Routed to Phases 14 and 16. Your two ``Assumptions.abort``
  encoding sites **stay exactly as they are**; both orchestrators agreed. Do
  not try to fix it and do not delete the skips.
* **``ATOMIC_MOVE`` under contention on Windows.** Routed to Phase 13, which
  must settle it before building a viewer that holds ``provenance.json`` open.
  Name it in your residue; do not attempt a Windows retry policy here.
* **The class-population census.** Tier 1 owns putting it into ``build.sh``
  with a control in ``verify-test-gates.sh``, after this phase lands. **Run it
  by hand in the meantime** -- it is fifteen lines in your work log.

**One item from Phase 03 is genuinely yours to answer.** Phase 03's escalation
4: ``CachingHashServiceTest``'s ``awaitSettled`` is a fixed sleep in your
module, waiting out the file system's one-second mtime granularity. Phase 03's
mechanical no-fixed-sleep scan does not cover ``cometgui-provenance``. Either
justify it in the code with the reason it cannot be an event, or remove it.
Answer it explicitly in the handoff either way.

Traps that cost hours
=====================

* ``mvn -pl <module>`` **always** needs ``-am``.
* ``mvn -pl cometgui-domain install`` **before any PIT run**, always, and
  especially after any cross-module change. A stale jar in ``_build/m2repo``
  produces ``NO_COVERAGE`` readings that are classloading failures in costume,
  and they are indistinguishable from missing tests. The diagnostic is
  ``jar tf _build/m2repo/org/cometgui/cometgui-domain/*/cometgui-domain-*.jar``.
* Scope the formatter with ``-DspotlessFiles=``. And **check the file count it
  reports, never its exit code**: a scope that matches nothing formats nothing
  and exits 0.
* **Commit by exact file path.** Never ``git add -A``, never a directory
  pathspec.
* **The documentation gate is global and strict.** A title underline one
  character short, or an inline literal opened with two backticks and closed
  with one, fails the build for everybody. It bit this phase twice already. Run
  ``bash scripts/ci/docs-build.sh`` **and gate the commit on it** -- running the
  check and committing on the next line is how both slipped through.
* ``scripts/verify-all-gates.sh`` now takes about **30 minutes**. Run it in the
  background, and **do not run ``docs-build.sh`` or any Maven command while it
  is running** -- they share ``docs/_build/html`` and ``_build/``, and the
  collision reports as a gate failure. Tier 1 made exactly that mistake once
  and spent time diagnosing its own collision as a regression.
* **Never ``pkill -f``** with a pattern that could match your own shell.
* A clean ``git show`` is not proof a commit contains reviewable text: a raw
  NUL byte in a source file makes git classify the whole file as binary and
  print no diff at all.

Hard rules
==========

Breaking one is a rejection at sign-off however green the build is.

* **Never weaken a gate, a checksum verification, a validation rule or a
  coverage threshold to make something pass** -- including the quiet forms:
  exclusions, disabled tests, loosened patterns, assertions pinned to whatever
  the code produces today.
* **Exit code 0 proves nothing.** Verify the output exists and is correct.
* **A test that asserts "did not throw" is not a test.** Prove the value.
* **An expected value computed by the code under test cannot fail.** Preserve
  the separation unit 9 found: ``ManifestWriterTest`` pins the bytes and
  ``ManifestReaderTest`` pins the values read from those bytes, both typed from
  the format rather than captured from the code. A future agent "simplifying"
  the reader tests to generate their fixtures with the writer would remove the
  only thing standing between this project and a symmetric, unanimous, wrong
  provenance record.
* All project documentation is reStructuredText and must pass
  ``sphinx-build -n -W``.
* Nothing installs on the host. No ``sudo``, no ``apt``, no host-level pip.
* **Do not answer a ``D-`` item.** Report it upward.
* **Do not push, and do not open a pull request.** Tier 1 holds that and it is
  waiting on the owner.

The eight shapes of a check that cannot fail
============================================

Every one was found in this project, five of them on a single day, and this
phase found four of them itself. Expect a ninth.

#. A rule that evaluates nothing.
#. An expected value computed by the code under test.
#. A property proved through a seam production need not use.
#. An assertion too coarse to see a partial failure.
#. An input set too narrow to see it.
#. An injection that never landed.
#. **A real measurement over an incomplete population** -- the worst, because
   re-running the gate reproduces the same clean figure, so verification cannot
   catch it. Only auditing that the sample was whole catches it.
#. An injection that reached the source but not the compiled class.

Report back
===========

Report to me at these moments, not only at the end:

#. **After the re-measurement**, with the census output and the headline
   numbers from the quiet tree. Before you resume any building.
#. **When units 9 and 10 are signed off or sent back**, with the defect you
   injected into each and the exact failure text.
#. **At the end**, with the seven gate items each carrying the command that
   produces its evidence and the evidence itself.

Your report is a claim I will re-check. I will re-run every gate item myself
and inject my own defects -- never your negative controls. Report honestly
rather than favourably: a unit reported as working and signed off on that basis
is the one failure mode this structure cannot absorb.

Escalate upward, to me, and never to the owner. Bring a recommendation and the
cost of each option, not an open question.
