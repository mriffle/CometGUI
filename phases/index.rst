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
     - NOT STARTED
   * - `01 <PHASE-01-build-skeleton.rst>`_
     - Repository, Build and Quality Skeleton
     - 00
     - D-008 (repository licence, for the LICENSE file only)
     - NOT STARTED
   * - `02 <PHASE-02-app-shell.rst>`_
     - Application Shell and Navigation
     - 01
     - D-001 (only if CasanovoGUI code is to be reused)
     - NOT STARTED
   * - `03 <PHASE-03-process-service.rst>`_
     - Process Service
     - 01, 02
     - --
     - NOT STARTED
   * - `04 <PHASE-04-provenance-core.rst>`_
     - Hashing and Provenance Core
     - 01, 03
     - --
     - NOT STARTED
   * - `05 <PHASE-05-tool-registry.rst>`_
     - Tool Registry and Installer
     - 01, 03, 04
     - D-002, D-003, D-004
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
     - D-002, D-003
     - NOT STARTED
   * - `10 <PHASE-10-results.rst>`_
     - Results Model and UI
     - 09
     - --
     - NOT STARTED
   * - `11 <PHASE-11-pdv.rst>`_
     - PDV Integration
     - 05, 10
     - D-005
     - NOT STARTED
   * - `12 <PHASE-12-limelight.rst>`_
     - Limelight Conversion and Upload
     - 09, 10
     - D-002, D-007
     - NOT STARTED
   * - `13 <PHASE-13-provenance-ui.rst>`_
     - Provenance UI and Reports
     - 04, 08, 09, 12
     - --
     - NOT STARTED
   * - `14 <PHASE-14-e2e.rst>`_
     - GUI Automation and Packaged End-to-End Harness
     - 07, 10, 11, 12, 13
     - D-006, D-007
     - NOT STARTED
   * - `15 <PHASE-15-hardening.rst>`_
     - Version Matrix, Performance and Hardening
     - 14
     - D-002, D-003
     - NOT STARTED
   * - `16 <PHASE-16-release.rst>`_
     - Documentation and Release Qualification
     - 15
     - D-001, D-004, D-006, D-008
     - NOT STARTED

Ordering notes
==============

The default order is numeric, and it is a dependency order rather than a
schedule. Where dependencies allow, phases may overlap:

* 03 (process service) and 04 (provenance core) are independent of each
  other after 01 and can run in parallel.
* 06 (parameter model) depends on 05 only for a real Comet binary to query;
  it is otherwise independent of 03, 04 and 05 and is the longest single
  piece of work in the project. Start it as early as a Comet binary exists.
* 07 (parameter UI) cannot start before 06 has a stable model, and it is the
  second-longest piece.
* 11 (PDV) is independent of 12 (Limelight) once 10 is done.
* 16 contains human-gated work (licensing, UX sessions) that must be
  scheduled far earlier than it is executed.

Phases 00-13 with their gates passed constitute a working,
provenance-complete application. Phases 14-16 are what make it a release.
