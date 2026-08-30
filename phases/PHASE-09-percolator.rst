=====================================================
PHASE-09: Percolator Adapter and Version Capabilities
=====================================================

:Phase: 09
:Status: NOT STARTED
:Depends on: 05, 08
:Blocked by decisions: none -- D-002 and D-003 both DECIDED
:Delivers: R-PERC-01..12
:Proves: AC-RES-05, AC-RES-06, AC-RES-07, AC-PRV-10

Purpose
-------
Run Percolator correctly for whichever version and capability set is actually
present, and never ask a binary for something it does not have. This is where
the 3.09 XML removal stops being a footnote and becomes command construction.

In scope
--------

* Version parsing and the capability model, driven by probe results from
  phase 05.
* Per-version command construction that emits only advertised options.
* *Latest compatible* resolution (``R-PERC-02``): the highest manifest
  version with a verified artefact for this platform, a passing loadability
  probe, and a capability set satisfying the enabled downstream stages. Never
  hard-coded, and re-evaluated when the enabled stages change.
* Recording why a newer version was not selected (``R-PERC-10``), in the UI
  and in provenance.
* PIN validation before invocation, including the decoy-row check.
* Output handling: PSM and peptide TSV, optional decoy TSVs, weights, XML
  when capable and needed, logs.
* Effective random seed defaulting and recording.
* Advisory model for known version defects, including 3.07.1's: it predates
  3.08's I-spline PEP default and the fix for PEP values exceeding 1.0
  (``R-PERC-11``). Advisories are shown at selection time and recorded in
  provenance.
* The compatible-version rerun action: preserve the original run, create a
  distinct execution record, reuse the merged PIN, do not rerun Comet -- and
  offer local-binary registration instead where no managed XML-capable build
  exists.
* Raw output immutability.

Out of scope
------------

* The results UI and filters (phase 10).
* Limelight conversion (phase 12).

Deliverables
------------

* ``org.cometgui.tools.percolator`` and ``org.cometgui.params.percolator``.
* ``docs/percolator.rst``, ``docs/reference/percolator_options.rst``,
  ``docs/developer/version_capabilities.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. A real Percolator run on the merged PIN from phase 08 produces PSM,
   peptide and weights artefacts that parse.
2. With an XML-capable version, XML is produced and parses; with 3.09, no
   XML option is passed and none is expected -- asserted on the recorded
   argument array, not only on the outcome.
3. The default version is resolved, not hard-coded: with Limelight enabled
   the resolver returns the newest XML-capable entry, with Limelight disabled
   the newest entry overall, and toggling the stage re-evaluates it and tells
   the user. A manifest with no XML-capable build for the platform yields a
   non-XML default and a Limelight stage marked unavailable.
4. When the resolver skips a newer version, the reason it gives names that
   version and the missing capability, in the UI and in provenance.
5. A PIN with zero decoy rows fails the stage before Percolator is
   launched.
6. The compatible-version rerun produces a second execution record with a
   different version, checksum and argument array, and the recorded Comet
   stage is untouched.
7. The effective seed appears in provenance for every run.
8. Weights parsing reads the split count from the artefact; a two-split and
   a three-split fixture both parse.
9. Raw Percolator outputs are byte-identical before and after any filtering
   or export operation.

Risks and notes
---------------

* Version-number-driven branching will pass on the versions you test and
  fail on the one a user has. Branch on capability.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-09-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-09-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
