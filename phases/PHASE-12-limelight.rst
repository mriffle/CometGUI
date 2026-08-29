=========================================
PHASE-12: Limelight Conversion and Upload
=========================================

:Phase: 12
:Status: NOT STARTED
:Depends on: 09, 10
:Blocked by decisions: D-002, D-007
:Delivers: R-LL-01..05, R-SEC-03, R-SEC-04
:Proves: AC-LL-01..06

Purpose
-------
Convert a compatible run to Limelight XML and upload it, with the
incompatibility boundary explained *before* the user tries, and with
credentials that never reach a log.

In scope
--------

* Managed converter JAR install, pinned to the tested version.
* Prerequisite validation: parameter file, pepXML, Percolator XML,
  converter, FASTA, writable output.
* Converter UI: Limelight q cutoff (0.01), output path, decoy import,
  independent decoy prefix, open-mod mode, resolved FASTA and pepXML
  directory.
* Multi-file pepXML directory handling and same-run verification.
* Disabled-with-explanation state and the explicit compatible-rerun action,
  including the no-managed-build variant that offers local-binary
  registration.
* Output validation beyond exit code: existence, non-emptiness, readability,
  structure, and any locally runnable schema validation.
* Upload UI with server and project selection, live logs and retained
  server-side identifiers.
* Credential storage in the OS keychain where practical, held separately
  from project files, redacted everywhere.

Out of scope
------------

* Uploading to a production Limelight server from any automated test.

Deliverables
------------

* ``org.cometgui.tools.limelight``.
* Limelight UI section.
* A local fake Limelight endpoint for tests, per ``D-007``.
* ``docs/limelight.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. A real conversion from the fixture run produces a Limelight XML that
   passes structural validation and is checksummed into provenance.
2. A converter exit code of 0 with a missing or empty XML fails the stage.
3. With a Percolator lacking XML, conversion controls are disabled with the
   explanation shown before any attempt, and the offered action matches
   what the platform can actually do.
4. The compatible rerun path yields a successful conversion without
   rerunning Comet, and provenance shows both Percolator executions.
5. The Limelight q cutoff defaults to 0.01 and is independent of the PSM
   and peptide display filters.
6. Upload runs only on an explicit user action, shows the destination
   first, and targets the fake or sandbox endpoint in tests.
7. A seeded credential appears in no log, no provenance record and no
   export; the test greps every artefact the run produced.
8. Selecting Limelight decoy import against a Percolator run that had no decoy
   output is prevented at configuration time, not at conversion time.

Risks and notes
---------------

* The converter's real argument names come from phase 00's recorded help
  output. Do not guess them.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-12-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-12-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
