==========================================
PHASE-02: Application Shell and Navigation
==========================================

:Phase: 02
:Status: NOT STARTED
:Depends on: 01
:Blocked by decisions: D-001 (only if CasanovoGUI code is to be reused)
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

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

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

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-02-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
