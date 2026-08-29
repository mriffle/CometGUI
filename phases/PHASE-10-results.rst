==============================
PHASE-10: Results Model and UI
==============================

:Phase: 10
:Status: NOT STARTED
:Depends on: 09
:Blocked by decisions: none
:Delivers: R-RES-01, R-RES-02, R-RES-03, R-RES-04
:Contributes to: R-PERC-09
:Proves: AC-RES-01..04, AC-RES-08, AC-RES-09, AC-RES-10

Purpose
-------
Turn Percolator output into something a scientist can explore, at real data
scale, with filters that are honest view predicates and never a rerun.

In scope
--------

* Version-aware, UI-independent parsers for PSM, peptide and weights
  artefacts.
* A results store that switches to disk-backed indexing above a documented
  row threshold, with a paged query-backed UI model.
* PSM and peptide tables with the specified columns, including a mandatory
  source-file column for multi-file runs.
* Independent PSM and peptide q filters, inclusive, range-checked, with
  total and passing counts.
* Explicit handling of missing or unparsable q-values as a distinct
  category.
* Sorting, text filtering, column visibility, copy, and selection stable
  across filter changes.
* Export producing new files with run ID, filter values, row counts and
  CometGUI version recorded.
* The learned feature weights view: per-split weights, mean signed, mean
  absolute, standard deviation, sign consistency, rank; sortable table as
  the source of truth, optional chart.
* A large performance fixture, created before the UI is built.

Out of scope
------------

* Protein-level results; explicitly out of scope for release 1.

Deliverables
------------

* ``org.cometgui.results`` with parser, filtering and export subpackages.
* Results and weights UI sections.
* The large performance fixture and its generator.
* ``docs/results.rst``, ``docs/learned_feature_weights.rst``,
  ``docs/developer/results_model.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. Both default filters are 0.01, independent, inclusive at exactly 0.01,
   and reject values outside [0, 1].
2. Changing a filter launches no process; a test asserts the process
   service was not called.
3. Displayed counts match counts computed independently from the raw
   Percolator file, for several cutoffs including 0, 0.005, 0.01 and 1.
4. Raw Percolator files are byte-identical after filtering and export.
5. An export carries the run ID, the applied cutoff and the before/after
   row counts.
6. The large fixture loads and filters within the documented time and heap
   budget, with the UI bound to a paged model rather than to a list of
   every row.
7. Weights values, ranking and sign consistency match values computed
   independently from the weights artefact.
8. Rows with missing q-values are counted and shown as their own category,
   identically in UI and export.

Risks and notes
---------------

* Binding an ``ObservableList`` of every PSM early is the mistake this phase
  is ordered to prevent. Build the fixture first.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-10-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-10-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
