=================================================
Main orchestrator session handoff -- session 02
=================================================

:Session: 02 -- main orchestrator (tier 1)
:Dates: 2026-08-29 to 2026-08-30
:Ended: after Phase 01 sign-off and the closing of every open ``D-`` decision,
   before Phase 02 was dispatched
:Reason for stopping: the owner chose to run Phase 02 in a fresh session
:Phases signed off: 01 (``PARTIAL``)

This is a *session* handoff, not a phase handoff. Phase records live in
``handoffs/PHASE-01-worklog.rst`` and ``handoffs/PHASE-01-handoff.rst``. The
authoritative project state is ``STATUS.rst``; if this file and ``STATUS.rst``
ever disagree, ``STATUS.rst`` wins.

.. contents:: Contents
   :depth: 2
   :local:


Read this first
===============

**There are unpushed commits.** Check before anything else::

    git log --oneline origin/main..HEAD

At the moment this file was written there were five, all documentation and
decision records. **The owner pushes; this session did not have credentials.**
See :ref:`s02-push` -- do not assume that is still true, check.


What this session did
=====================

#. Read in ``ONBOARDING.rst``, ``STATUS.rst``, ``DECISIONS.rst``,
   ``phases/index.rst``, the session 01 handoff and the Phase 00 handoff.
#. **Put the two owner questions session 01 left standing** -- ``D-002`` option
   C and the publication half of ``D-008`` -- to the owner before dispatching
   anything, plus a third the phase raised: Phase 01's gate item 6 needs a
   remote that ``D-008`` was withholding.
#. Recorded those answers, amended ``specification.rst`` to revision 7 and
   re-scoped Phase 05, then **dispatched Phase 01 to one fresh phase
   orchestrator**, which decomposed it into eight work units.
#. **Re-ran all six gate items itself** and signed Phase 01 off ``PARTIAL``.
#. Took the remaining five ``D-`` decisions to the owner one at a time, and
   recorded each. Specification reached revision 9.

The three-tier model held throughout: no tier did the tier below's job.


Phase 01, and what sign-off caught
==================================

Signed off ``PARTIAL``. Items 1--5 pass; item 6's "on a pull request" half was
unmet because no remote existed at the time.

**Sign-off injected its own defects rather than re-running the phase's negative
controls.** That is the part worth carrying forward as practice: a harness that
only fails on the defect its author chose proves less than one that fails on a
defect it has never seen. Concretely, at sign-off the main orchestrator ran a
clean clone to ``11/11 stages OK``, broke a cross-reference, put a JavaFX field
in the domain, added an untested branchy class, and removed evidence from three
different places in the traceability map -- each was caught with a specific
diagnostic, and each gate returned to green when the defect was removed.

Two findings from that:

* **The vacuous pass is defended, and the defence works.** Removing
  ``cometgui-app`` from the archtests dependencies left the layering rules
  **passing 8/8 while checking nothing**; ``ClassImportCensusTest`` failed with
  *"no classes were imported from org.cometgui.app ... its rules check
  nothing"*. That is the failure mode ArchUnit invites, and it is closed.
* **One recorded surprise was wrong.** The Phase 01 handoff says a JavaFX
  layering violation in the domain "does not even compile", implying ArchUnit
  is a second line of defence. It compiles fine -- the Liberica Full JDK
  carries JavaFX -- so **ArchUnit is the first line of defence**, and the rule
  is genuinely load-bearing. Corrected in ``STATUS.rst``; the handoff itself is
  left as the phase wrote it.

**The phase reported a defect against itself**, which is the behaviour this
structure depends on: its own integration commit silently broke the
traceability self-test, and **nothing in the build ran that self-test**, so
gate item 5's falsifiability stopped being demonstrable while every other check
stayed green. ``scripts/verify-all-gates.sh`` exists because of it and belongs
in the nightly pipeline.


Decisions closed this session
=============================

**Every ``D-`` item is now answered** -- the first time since the project
started. Full reasoning is in ``DECISIONS.rst``; this is the index.

.. list-table::
   :header-rows: 1
   :widths: 10 90

   * - ID
     - Outcome
   * - ``D-002``
     - **Option C.** Percolator's binary comes from the portable ``noxml``
       archive on every tier-1 platform. Phase 05 does **not** implement NSIS
       or ``xar``/cpio payload extraction -- the most fragile code the
       installer was going to contain is unwritten rather than written.
   * - ``D-003``
     - Carry **3.07.1**, **3.09** and **3.06.5**. An intent, not a
       per-platform promise: ``R-PERC-01``'s artefact-plus-probe test decides
       what each machine is offered.
   * - ``D-005``
     - **Drive PDV properly, via a generated mzTab.** See :ref:`s02-pdv`.
   * - ``D-006``
     - No data redistribution; local fixture chosen; **the CI fixture set is
       deferred** to before Phase 14.
   * - ``D-007``
     - Local fake endpoint, always. Sandbox slot wired but unnamed.
   * - ``D-008``
     - Published at ``https://github.com/mriffle/CometGUI.git``. May move; will
       always be a GitHub repository the owner controls.
   * - ``D-009``
     - **Provisional.** The copyright line stays ``Copyright (C) 2026 The
       CometGUI authors.`` The institutional question is deferred.

Two deferrals have a named owner and moment, and must not quietly vanish:
``D-006``'s CI fixture set before Phase 14, and ``D-009``'s institutional
copyright question before release in Phase 16.


.. _s02-pdv:

The decision that changes the most work
=======================================

``D-005`` grew Phase 11 materially, and the reasoning matters more than the
outcome.

The specification had assumed a PDV ``db-gui`` control mode and, failing that,
offered only baseline open-in-PDV or a **PDV fork**. Inspecting
``Noble-Lab/CasanovoGUI`` showed both framings were wrong. That project drives
PDV in production from ``PdvLauncher.java`` and ``PdvController.java`` --
ephemeral loopback port, ``/ready`` polling, debounced
``/select?ref=<spectra_ref>`` -- but **every launch is** ``denovo-gui --mztab``
and its launcher has **no pepXML or mzID path at all**. The control server is
real and proven; mzTab is the only door into it. Casanovo emits mzTab natively,
Comet plus Percolator does not.

The owner chose to close that gap by generating the mzTab. This needs **no
fork** and puts **no upstream contribution on the critical path**, and Phase 11
may reuse CasanovoGUI's launcher and controller under ``D-001`` -- CometGUI
supplies the input and the results binding, not the machinery.

The cost is a real component with its own tests, governed by ``R-PDV-03``,
which encodes the owner's words -- *"accurate and true to the original
results"* -- as a falsifiable gate: values transcribed rather than recomputed,
missing fields left explicitly null rather than defaulted or invented,
modifications compared as parsed values, and export failing loudly rather than
emitting a partial file that looks complete.

**The landmine, named in advance.** ``spectra_ref`` must resolve to the
spectrum that actually produced the PSM. PDV numbers spectra by **1-based file
position** via ``msftbx`` while pepXML carries the **instrument scan number**,
and they diverge for any scan-range subset -- this is exactly why PDV exited 0
writing nothing during Phase 00. A test on a file where the two coincide proves
nothing. Phase 11 should **spike PDV's acceptance of a generated mzTab before
building the exporter out**.


State of the tree
=================

* **Product code exists now.** Twelve Maven modules, 61 Java files, 54 tests.
  The only real logic is ``BuildIdentity`` and two headless JavaFX probes --
  deliberately, so the gates measure something rather than an empty reactor.
  Nine of twelve modules hold only ``package-info.java`` and the build prints
  them as ``inert`` rather than letting an unevaluated rule read as passing.
* One documented build command: ``bash scripts/build.sh``, 11 stages. A clean
  clone with no ``tools/`` and no ``.venv`` reaches ``11/11 stages OK`` in
  about 150 seconds, bootstrapping its own JDK, Maven and font stack.
* ``scripts/verify-all-gates.sh`` -- 9 controls, 123 checks, ~5 minutes -- is
  the aggregate falsifiability harness. It is **not** in ``build.sh`` by
  design; it belongs in the nightly pipeline.
* Committed: the twelve modules, ``docs/`` (real Sphinx tree, 51 pages),
  ``.github/workflows/`` (three pipelines), ``LICENSE`` (verified byte-identical
  to the canonical FSF GPLv3 text), ``CONTRIBUTING.rst``, and the four
  documents tier 1 owns.
* Gitignored, by design: ``tools/``, ``.venv/``, ``_build/``, ``scratch/``.
* ``specification.rst`` is **revision 9**.


.. _s02-push:

Pushing
=======

The remote is ``https://github.com/mriffle/CometGUI.git`` and **the full
history is published**. Two constraints follow from ``D-008`` and are not
negotiable: the URL is kept in one place because the repository may move, and
**history is never force-pushed or rewritten** because it is published.

**This session had no GitHub credential** -- no ``gh``, no ``GH_TOKEN``, no SSH
key, no credential helper -- so ``git push`` fails here with ``could not read
Username``. The owner pushed from their own shell and said future sessions
would have permission. **Check, do not assume**::

    git ls-remote origin && git log --oneline origin/main..HEAD

Two things learned the hard way, worth not repeating:

* A Personal Access Token **cannot create or update anything under
  ``.github/workflows/`` without the ``workflow`` scope.** Phase 01 added three
  workflow files, so the first push was rejected. The error names identity
  nowhere, which sent this session chasing the wrong cause for several
  exchanges.
* **The owner works in a browser, so the ``!`` prefix does not work for them.**
  Give plain commands to type.

Also recorded so it is not rediscovered: the coding harness injects its own git
identity through ``GIT_CONFIG_*`` environment variables, which override
``.git/config``. That is why 57 of the published commits are authored by
``CometGUI spec <claude@ogdb.com>`` and 7 by ``Michael Riffle
<mriffle@uw.edu>``. It is a tooling artefact, not a real user. Changing it now
would mean rewriting published history, which ``D-008`` excludes; 37 commits
carry a ``Co-Authored-By: Claude`` trailer.


What the next session should do
===============================

**First:** check for unpushed commits, and check whether this session has push
credentials.

**Then, in priority order.**

#. **Close the two ``PARTIAL`` grades on the board.** Both were blocked only by
   the missing remote, and both are now ordinary phase work:

   * **Phase 00 item 8** -- run the seven-step checklist in
     ``docs/feasibility/windows-artefact.rst`` on a ``windows-latest`` runner.
     This would be **the first time any Windows binary in this project has been
     executed** rather than inferred from byte markers. Until it passes, every
     non-Linux capability verdict remains inference and the manifest must keep
     saying ``xml_capability: unverified-on-windows``.
   * **Phase 01 item 6** -- run the pull-request pipeline on a real pull
     request. The three workflow files exist and every step is proven locally;
     GitHub has simply never executed them. Note that Actions may need enabling
     on a fresh repository.

#. **Dispatch Phase 02** (``phases/PHASE-02-app-shell.rst``) to a fresh phase
   orchestrator. Nothing blocks it. Two obligations it must not drop:

   * **``D-001``'s attribution duty.** Any file derived from CasanovoGUI
     retains its copyright notices and records the derivation.
     ``CONTRIBUTING.rst`` says how -- a second Spotless file set and a second
     Checkstyle execution with their own header file, **extending** the header
     configuration and never relaxing or excluding it to make a derived file
     pass.
   * **The ``D-009`` placeholder stays a placeholder.** No agent substitutes a
     personal or institutional name.

   Phase 02 is also the first phase that will reuse CasanovoGUI source in
   earnest, so it is where the derivation machinery gets built and proved.


Traps this session verified, which will bite later phases
==========================================================

New this session, in addition to everything in the session 01 handoff and the
two phase handoffs.

* **Spotless cannot check licence headers on ``package-info.java``** --
  ``LicenseHeaderStep.unsupportedJvmFilesFilter()`` excludes it by name, and
  this repository has 53 of them, 87% of the tree. Checkstyle carries that
  obligation instead. Consequence for every later phase: ``mvn spotless:apply``
  will **not** add a header to a new ``package-info.java``; copy one from a
  sibling.
* **An unanchored ``tools/`` in ``.gitignore`` also matched
  ``org/cometgui/tools/``** inside three modules and silently dropped eight
  source files from ``git add``. It is now ``/tools/``. Any project whose
  source packages collide with a gitignored directory name has this bug and is
  never told about it.
* **A gate that is never run stops working without anyone noticing.** It
  happened inside Phase 01, to the phase orchestrator's own commit. That is the
  whole argument for ``scripts/verify-all-gates.sh`` being in the nightly
  pipeline.
* **JaCoCo, ArchUnit, PIT, SpotBugs, Spotless and a dependency scanner each
  offer a vacuous pass.** Each is defended and each defence is itself tested --
  a class census with a floor, a coverage-agent presence check,
  ``failWhenNoMutations`` left on, analysed-class counts, and a scanner canary
  that sends a known-vulnerable coordinate with every batch. The canary was
  verified live at sign-off: it found 7 advisories for ``log4j-core 2.14.1``.
* **``.mvn/jvm.config`` cannot contain comments** -- Maven passes every
  whitespace-separated token to the JVM.
* **The headless JavaFX recipe is Linux/amd64 only and knows it.** Its
  ``LD_LIBRARY_PATH`` names ``x86_64-linux-gnu``. The phase adding non-Linux
  runners owns splitting it into OS-activated profiles.


What this session got wrong
===========================

Recorded because a handoff that only lists successes teaches nothing.

* **Chased the wrong cause on the push failure.** When the owner reported a
  permission error, this session investigated git identity at length --
  including proposing a history rewrite -- before the actual error text arrived
  and showed it was a missing ``workflow`` token scope. The lesson is dull and
  general: **ask for the exact error text first.** Identity was never involved.
* **Answered a "walk me through the issues, one by one" request with all
  eleven at once**, which the owner had to correct. One at a time meant one at
  a time.
