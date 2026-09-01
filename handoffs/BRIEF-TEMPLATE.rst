==========================================
PHASE-nn unit N brief -- <unit title>
==========================================

:Phase: nn
:Work unit: N of <total>
:Tier: 3 -- phase agent (one fresh agent, one unit)
:Dispatched by: <phase orchestrator, session>
:Date: <date>
:Depends on: <signed-off units this one builds on, or "nothing">
:Runs alongside: <units live in the tree at the same time, or "nothing">

.. note::

   **Filling this in.** This is the template a phase orchestrator uses to
   dispatch a phase agent. Replace every ``<placeholder>``; delete this note
   and any section that genuinely does not apply, rather than leaving it
   empty -- an empty section reads as "no constraints", which is rarely what
   is meant. Point at files by path so the agent reads the current version;
   do not paste their contents. Sections 5, 8 and 9 are standing text and
   should be carried over unchanged.

Read first, in this order
=========================

Read them properly, not by grep. The unit is small; the reading is what makes
it fit the rest of the project.

#. ``ONBOARDING.rst`` -- <all of it, or the named sections and why those>.
#. ``CONTRIBUTING.rst`` -- the environment, commit, gate, documentation and
   handoff conventions you are expected to follow.
#. ``phases/PHASE-nn-<name>.rst`` -- the phase you are inside: its scope, its
   deliverables and its exit gate.
#. ``DECISIONS.rst`` -- <the specific ``D-`` items that bear on this unit, and
   what each one settles or leaves open>.
#. ``specification.rst`` -- <the exact sections or ``R-`` rules this unit
   implements; name them, do not send the agent into a 150 000-word document
   unaided>.
#. <Prior art in the tree: the handoff of a phase this depends on, a
   signed-off unit whose interface you must fit, a worked example of the
   artefact you are producing.>

What you own
============

<One paragraph in plain language: what this unit is for, and why the project
needs it. Then the concrete scope -- the artefacts to produce and the
behaviour they must have. Be specific enough that "done" is not a matter of
opinion, and say what *good* looks like, not only what exists.>

Explicitly **not** in this unit:

* <Work that belongs to another unit or another phase. Name the owner so the
  agent reports it rather than doing it opportunistically.>
* <A tempting adjacent improvement that would collide with a concurrent
  agent.>

Files
=====

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - You own (create or edit)
     - Notes

   * - ``<path>``
     - <what it is; whether it exists yet>

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Do not touch
     - Why

   * - ``STATUS.rst``, ``DECISIONS.rst``, ``phases/*.rst``,
       ``handoffs/PHASE-*.rst``
     - Owned by the tiers above you. Report changes upward instead of making
       them.

   * - ``<path owned by a concurrent unit>``
     - <which unit owns it, and that the agent is live in the tree now>

   * - ``<path owned by an earlier signed-off unit>``
     - Signed off already; a change here invalidates that sign-off. Escalate
       if it must change.

Interfaces you must fit
=======================

<The contracts this unit cannot renegotiate: module and package names, type
and method signatures, file formats, on-disk layout, script names and their
exit-code conventions, configuration keys, the command that must keep working
after your change. Where an interface already exists in the tree, name the
file and let the agent read it. Where this unit *defines* an interface that a
later unit will consume, say so -- it changes how carefully it must be
designed.>

Hard rules
==========

Breaking one of these is a rejection at sign-off, regardless of how good the
rest of the work is.

* **Never weaken a gate**, a checksum verification, a validation rule or a
  coverage threshold to make something pass -- including the quiet forms:
  exclusions, skipped tests, loosened patterns, assertions pinned to whatever
  is produced today.
* **Exit code 0 proves nothing.** Verify that the output exists and is
  correct.
* **A test that asserts "did not throw" is not a test.** Prove the value.
* **All project documentation is reStructuredText** and must pass
  ``sphinx-build -n -W``. ``CLAUDE.md`` is the only Markdown file in the
  repository; do not add another.
* **Nothing is installed on the host** -- no ``sudo``, no ``apt``, no
  host-level ``pip``. Tools go under ``tools/``, Python into ``.venv``, Maven
  artefacts into ``_build/m2repo``.
* **Commit with an explicit pathspec after the message**; never ``git add
  -A``. Other agents are live in this tree.
* **The remote is** ``https://github.com/mriffle/CometGUI.git`` (``D-008``,
  decided 2026-08-30). **Never push and never open a pull request from a
  phase agent**; tier 1 holds both. Never force-push and never rewrite
  published history.
* **Do not answer a ``D-`` item.** Report it upward.

Acceptance conditions
=====================

You must **demonstrate** each of these, with the command and its actual
output. An asserted condition is not a met condition, and the phase
orchestrator re-runs every one of them before signing the unit off.

.. list-table::
   :header-rows: 1
   :widths: 6 50 44

   * - #
     - Condition
     - How it must be demonstrated

   * - 1
     - <The observable property, stated so that it can fail.>
     - <The exact command to run, and what its output must contain. For a
       gate, this includes showing it FAIL on the defect it exists to catch
       and then pass once the defect is removed -- against a sandbox copy,
       never the real tree.>

   * - <n>
     - ``git status --porcelain`` shows nothing outside the paths you own.
     - Run it and paste the output.

Decisions, blockers and surprises
=================================

If you hit an open ``D-`` item, a contradiction between documents, or a fact
about the world that the specification gets wrong: do every part of the unit
that does not depend on it, then report it with the evidence. Do not invent an
answer, do not quietly work around it, and do not widen your scope to fix it.

<Anything already known to be shaky here: an unresolved question this unit
will run into, a fact recently found to be stale, a decision the orchestrator
has already escalated.>

Commit your work
================

Run ``git status`` first, then commit with an explicit pathspec after the
message, with the phase identifier in the subject::

    git status --porcelain
    git commit -m "PHASE-nn: <what changed>" -- <path> <path>

Commit before you report. If you run out of context mid-unit, the commit is
what makes that cost part of a unit rather than all of it.

Report back
===========

Your report is read by the phase orchestrator, who will re-run everything you
claim. It must contain:

#. **What you created or changed**, by path.
#. **The exact commands you ran and their observed output** -- numbers,
   messages, file sizes. Not "it worked".
#. **Each acceptance condition** with the evidence that it holds.
#. **What you did not do**: conditions unmet, work deferred, anything you
   could not verify and why. An unverifiable item is not a passed item.
#. **Decisions and surprises** a later unit or phase needs to know.

**Report honestly rather than favourably.** A unit reported as working and
signed off on that basis is the one failure mode this structure cannot
absorb. A problem you name costs one round of rework; a problem you hide costs
the phase.
