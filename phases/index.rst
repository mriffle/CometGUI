==============================
CometGUI Implementation Phases
==============================

Read ``../ONBOARDING.rst`` first, then ``../STATUS.rst`` for current state.
This file lists the phases and their dependency order; each phase file is
authoritative for its own scope and exit gate.

.. list-table:: Phases
   :header-rows: 1
   :widths: 8 34 14 20 24

   * - Phase
     - Title
     - Depends on
     - Decisions
     - Status
   * - `00 <PHASE-00-feasibility.rst>`_
     - Feasibility, Legal and Upstream Verification
     - none
     - D-001, D-002, D-004, D-005, D-006, D-007
     - PARTIAL (signed off 2026-08-29)
   * - `01 <PHASE-01-build-skeleton.rst>`_
     - Repository, Build and Quality Skeleton
     - 00 (met)
     - none outstanding; ``D-008`` capped the outcome at PARTIAL, and is now
       decided -- item 6 is closable
     - PARTIAL (signed off 2026-08-29)
   * - `02 <PHASE-02-app-shell.rst>`_
     - Application Shell and Navigation
     - 01 (met)
     - D-001 DECIDED -- reuse permitted; notices retained and derivation
       recorded, enforced by a Checkstyle superset. ``D-009`` placeholder kept
     - PASSED (signed off 2026-08-31)
   * - `03 <PHASE-03-process-service.rst>`_
     - Process Service
     - 01, 02 (both met)
     - --
     - IN PROGRESS (dispatched 2026-08-31)
   * - `04 <PHASE-04-provenance-core.rst>`_
     - Hashing and Provenance Core
     - 01 (03 not required -- see Ordering notes)
     - --
     - IN PROGRESS (dispatched 2026-08-31, the last phase run concurrently)
   * - `05 <PHASE-05-tool-registry.rst>`_
     - Tool Registry and Installer
     - 01, 03, 04
     - none open. D-002 (option C), D-003 and D-004 all DECIDED
     - NOT STARTED
   * - `06 <PHASE-06-comet-param-model.rst>`_
     - Comet Parameter Model
     - 01, 05
     - --
     - NOT STARTED
   * - `07 <PHASE-07-comet-param-ui.rst>`_
     - Comet Parameter Editor UI
     - 02, 06
     - --
     - NOT STARTED
   * - `08 <PHASE-08-workflow-comet.rst>`_
     - Workflow Engine and Comet Adapter
     - 03, 04, 05, 06
     - --
     - NOT STARTED
   * - `09 <PHASE-09-percolator.rst>`_
     - Percolator Adapter and Version Capabilities
     - 05, 08
     - none open; D-002 and D-003 both DECIDED
     - NOT STARTED
   * - `10 <PHASE-10-results.rst>`_
     - Results Model and UI
     - 09
     - --
     - NOT STARTED
   * - `11 <PHASE-11-pdv.rst>`_
     - PDV Integration and mzTab Export
     - 05, 10
     - D-005 DECIDED -- enhanced control mode via a generated mzTab
     - NOT STARTED
   * - `12 <PHASE-12-limelight.rst>`_
     - Limelight Conversion and Upload
     - 09, 10
     - none open; D-002 and D-007 both DECIDED
     - NOT STARTED
   * - `13 <PHASE-13-provenance-ui.rst>`_
     - Provenance UI and Reports
     - 04, 08, 09, 12
     - --
     - NOT STARTED
   * - `14 <PHASE-14-e2e.rst>`_
     - GUI Automation and Packaged End-to-End Harness
     - 07, 10, 11, 12, 13
     - D-006's CI-fixture half only; D-007 DECIDED
     - NOT STARTED
   * - `15 <PHASE-15-hardening.rst>`_
     - Version Matrix, Performance and Hardening
     - 14
     - none open; D-002 and D-003 both DECIDED
     - NOT STARTED
   * - `16 <PHASE-16-release.rst>`_
     - Documentation and Release Qualification
     - 15
     - D-006, D-009. D-001, D-004 and D-008 DECIDED
     - NOT STARTED

Ordering notes
==============

The default order is numeric, and it is a dependency order rather than a
schedule.

.. important::

   **Phases run ONE AT A TIME** (owner's decision, 2026-08-31). The notes below
   describe which phases are *independent*, which still matters for ordering and
   for spotting shared abstractions -- but independence is **no longer a licence
   to overlap them**. Phases 03 and 04 were the last pair run concurrently, and
   they independently built the same secret-redaction rule set in two sibling
   modules without either being able to see the other. See *Why phases run one
   at a time* in ``../ONBOARDING.rst``.

Independence, for ordering purposes only:

* 03 (process service) and 04 (provenance core) are independent of each
  other after 01. **Run 03, sign it off, then run 04.**
* 06 (parameter model) depends on 05 only for a real Comet binary to query;
  it is otherwise independent of 03, 04 and 05 and is the longest single
  piece of work in the project. Start it as early as a Comet binary exists.
* 07 (parameter UI) cannot start before 06 has a stable model, and it is the
  second-longest piece.
* 11 (PDV) is independent of 12 (Limelight) once 10 is done -- independent in
  the dependency sense, still run one after the other. It grew
  materially on 2026-08-30: ``D-005`` added an **mzTab exporter** with its own
  fidelity suite, because PDV's control server accepts only mzTab. Treat it as
  two pieces -- the baseline integration, and the exporter -- and spike PDV's
  acceptance of a generated mzTab before building the second out.
* 16 contains human-gated work (licensing, UX sessions) that must be
  scheduled far earlier than it is executed.

Phase 00's residue, for the phases that inherit it
==================================================

Phase 00 was signed off ``PARTIAL``. Four items travel forward. None stops
Phase 01 from running, but the fourth caps the grade it can reach.

* **No Windows or macOS binary has ever been executed.** Every non-Linux
  capability verdict in the project is inference from byte markers. Phases 05,
  09 and 15 must treat platform capability as *probed at runtime*, never as
  read from a table. Closing this needs either a remote for Windows CI
  (``D-008``) or fifteen minutes from a person with a Windows machine, using
  the checklist in ``docs/feasibility/windows-artefact.rst``.
* **The ``noxml`` finding re-scoped Phase 05 -- settled 2026-08-29.** The owner
  took ``D-002`` **option C**: Percolator's binary comes from the portable
  ``noxml`` zip on every tier-1 platform, and the NSIS and ``xar``/cpio payload
  extractors Phase 05 was going to build are **not built**. Two costs replaced
  them and are now in Phase 05's scope: the XSD companions must be fetched
  separately, because no portable archive ships them, and the Windows portable
  zip needs a Visual C++ runtime it does not carry. Specification revision 7
  and ``phases/PHASE-05-tool-registry.rst`` carry the detail.
* **The capability probe must be functional, not textual.** ``--help`` is
  identical between the XML and ``noxml`` builds, so ``R-PERC-02`` cannot be
  satisfied by string matching. Phase 09 owns the rule; Phase 05 owns the
  post-install probe that first applies it.
* **The remote exists as of 2026-08-30** --
  ``https://github.com/mriffle/CometGUI.git`` (``D-008``). This was the residue
  Phases 00 and 01 shared: it left Phase 00's gate item 8 open and capped Phase
  01 at ``PARTIAL`` on gate item 6. **Both are now ordinary phase work.** Phase
  01 proved every pipeline step locally -- 42 steps across 3 workflows then,
  45 across 4 since the Windows Percolator verification workflow was added on
  2026-08-30 -- and named the Windows and macOS matrix entries so that turning
  them on is configuration rather than authorship. Two standing constraints
  follow: the repository may move, so its URL lives in one place; and its
  history is published, so it is never force-pushed or rewritten.

Phases 00-13 with their gates passed constitute a working,
provenance-complete application. Phases 14-16 are what make it a release.
