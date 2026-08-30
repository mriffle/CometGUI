==========================================
PHASE-02: Application Shell and Navigation
==========================================

:Phase: 02
:Status: NOT STARTED
:Depends on: 01
:Blocked by decisions: none -- D-001 DECIDED 2026-08-29: reuse permitted,
   with notices retained and the derivation recorded
:Delivers: R-PLAT-01, R-PROC-01, R-SEC-01, R-TEST-04
:Proves: Foundations for AC-TST-05

Purpose
-------
Build the JavaFX shell and the information architecture, with the MVVM boundary
and the injection seams that make everything after it testable without a
display. This phase deliberately contains no scientific behaviour: it is the
frame that later phases fill.

In scope
--------

* Application bootstrap, window, AtlantaFX styling, and the eight primary
  sections from the specification's information architecture as navigable,
  empty panes.
* The MVVM/presenter boundary and a domain layer that compiles and tests
  without JavaFX on the classpath at test time.
* Dependency injection seams for clock, environment reader, process runner,
  downloader, filesystem, run-ID source and hash service.
* The stage stepper component for the Run screen, driven by the workflow
  state enumeration, with no engine behind it yet.
* Stable ``fx:id``/test identifiers for every control the test suite will
  need, and accessible names on all of them.
* A console pane bound to a bounded, stage-taggable message model.
* Startup verification of the host baseline (64-bit OS, and on Linux the
  glibc version), reported to the user rather than discovered by a later
  crash.
* The ``FxUiDriver`` abstraction's interface, and the headless GUI test
  harness proving navigation.

Out of scope
------------

* Parameter editing, tool management, running anything.
* Any file I/O beyond application settings.

Deliverables
------------

* Runnable application shell with all primary sections reachable.
* ``FxUiDriver`` interface plus the implementation chosen in phase 00.
* GUI test navigating every section headlessly.
* ``docs/developer/architecture.rst`` describing the real layering as built.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. The application starts, and every primary section is reachable by mouse
   and by keyboard alone.
2. A headless GUI test navigates all sections and asserts each is present
   by stable identifier.
3. An ArchUnit test proves the domain module has no JavaFX dependency, and
   it fails if one is introduced.
4. Every control that exists has an accessible name; a test enumerates them
   and fails on a missing one.
5. The console pane discards oldest messages under a flood test without
   heap growth beyond its documented cap.

Risks and notes
---------------

* If ``D-001`` remains open, write the shell independently. Reading
  CasanovoGUI for design guidance is fine; copying is not (``R-SEC-01``).

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-02-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-02-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
