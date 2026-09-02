===================================
PHASE-07: Comet Parameter Editor UI
===================================

:Phase: 07
:Status: NOT STARTED
:Depends on: 02, 06
:Blocked by decisions: none
:Delivers: no rules owned -- UI half of the parameter rules
:Contributes to: R-PARAM-03..10, R-CMT-01
:Proves: AC-PAR-03, AC-PAR-04, AC-PAR-05, AC-PAR-07, AC-PAR-08, AC-PAR-09, AC-PAR-10

.. note::

   **This phase also owns the ``Settings`` navigation section**, assigned by
   tier 1 on 2026-08-31. Its default outcome is **removal, not invention**: the
   specification mentions application Settings once, permissively
   ("Tool Manager and application Settings *may* be secondary navigation or
   dialogs"), and requires nothing to be user-configurable at application level.
   Phase 02 built it honestly as an empty pane that says it is empty, pinned by
   a test. If no phase before this one has produced an application-level,
   run-independent preference that cites an ``R-`` rule or ``AC-`` criterion,
   **remove the section from navigation** rather than shipping a permanently
   empty one or filling it with invented preferences. See ``STATUS.rst``,
   *The Settings section: owner assigned, content deliberately empty*.

Purpose
-------
The central product-design effort. The specification is explicit that this is
not a form-building task: it is progressive disclosure, structured editors for
structured values, and error prevention over error reporting.

In scope
--------

* Essentials mode covering the specification's control list, sufficient for
  a normal tryptic DDA search with no raw editing.
* Advanced mode grouped by scientific concept, covering every user-relevant
  parameter.
* Expert mode: canonical raw text, highlighting, line diagnostics, diffs
  against preset and last-run, unknown-parameter list, and a validating
  apply that leaves the model untouched on parse failure.
* The variable-modification editor with all fifteen slots, human-readable
  summaries, presets, residue multi-select, terminus choices, count
  controls, neutral-loss editing and serialised-value inspection.
* Enzyme selector plus custom enzyme editor plus second enzyme.
* Static modification table.
* Ion-series controls, compound tolerance controls, range controls, file
  pickers.
* Global parameter search with the specified filters.
* Reset at field, category and configuration level; preset application with
  a reviewable diff.
* Workflow-enforced outputs shown as locked with their reason.
* Validation surfacing: field-attached, summarised, keyboard reachable,
  never colour-only.

Out of scope
------------

* Changing the parameter model's semantics; the model from phase 06 is the
  source of truth.

Deliverables
------------

* ``org.cometgui.ui`` parameter editor views, view models and custom
  controls.
* GUI tests for every item in the specification's GUI coverage list that
  concerns parameters.
* ``docs/comet_parameters.rst``, ``docs/variable_modifications.rst``,
  ``docs/comet_parameter_presets.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. A GUI test configures a complete tryptic DDA search using Essentials
   only, and the generated parameter file matches an expected canonical
   file exactly.
2. A GUI test adds, edits, reorders and removes a variable modification and
   asserts the serialised tuple after each step.
3. Applying a preset shows a diff, applying a subset applies exactly that
   subset, and cancelling changes nothing.
4. A raw Expert edit that fails to parse leaves the typed model unchanged
   and reports the offending line.
5. Disabling a workflow-required output is impossible while the dependent
   stage is enabled, and the reason is shown.
6. An invalid cross-parameter configuration blocks Run, attaches the error
   to the field, appears in the summary and is reachable by keyboard.
7. Every parameter control has an accessible name, and validation state is
   conveyed in text.
8. Parameter search finds a parameter by name, by display name, by help
   text and by alias.

Risks and notes
---------------

* The temptation is to generate controls mechanically from the schema and
  call it done. Essentials in particular is a curated, task-ordered surface,
  not a filtered dump.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-07-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-07-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
