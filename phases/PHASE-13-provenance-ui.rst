===================================
PHASE-13: Provenance UI and Reports
===================================

:Phase: 13
:Status: NOT STARTED
:Depends on: 04, 08, 09, 12
:Blocked by decisions: none
:Delivers: no rules owned -- presentation of the provenance model
:Contributes to: R-PROV-01..05
:Proves: AC-PRV-01..09

Purpose
-------
Make the provenance record readable and exportable, and prove it is complete
against the files on disk rather than against itself.

In scope
--------

* The Provenance tab: summary, tools, inputs and outputs, parameters,
  timeline, logs, warnings.
* Actions: copy MD5, copy SHA-256, copy command, open file location, export
  JSON, export RST.
* Parameter diffs and preset-origin display.
* Warning surfacing for advisories, manifest/probe discrepancies, partial
  files and fallbacks.
* The generated ``provenance.rst`` report, from the same model as the JSON.

Out of scope
------------

* Changing the provenance schema; phase 04 owns it.

Deliverables
------------

* Provenance UI section.
* ``docs/provenance.rst`` and the developer schema page kept in sync.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. For a completed real run, every input and output file listed has both
   digests, and an independent recomputation over the files on disk matches
   the manifest exactly.
2. Every tool used has its version, path and both digests, and they match
   the files in the tool cache.
3. The exact argument array for every process, including one per spectrum
   file, is present and displayable.
4. A failed run and a cancelled run each produce a provenance record that
   opens and shows what happened, with partial outputs marked.
5. JSON and RST exports are generated from the same model; a test asserts
   the RST report's facts against the JSON.
6. No secret appears in either export.

Risks and notes
---------------

* The temptation is to test the manifest against itself. The gate requires
  recomputation from disk.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-13-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-13-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
