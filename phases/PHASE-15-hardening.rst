===================================================
PHASE-15: Version Matrix, Performance and Hardening
===================================================

:Phase: 15
:Status: NOT STARTED
:Depends on: 14
:Blocked by decisions: none -- D-002 and D-003 both DECIDED
:Delivers: R-TEST-02, R-TEST-07, R-TEST-08
:Proves: AC-TST-01..04, AC-TST-10, AC-TST-11, AC-RES-10, AC-REL-01

Purpose
-------
Take the product from "works on the reference fixture" to "known to work across
the versions, platforms and data scales it claims", and keep it that way.

In scope
--------

* The Percolator version matrix, restricted to combinations the manifest
  provides and whose loadability probe passes.
* The Comet nightly matrix, including determinism repeats and, where
  infrastructure permits, the Windows Thermo RAW smoke test.
* Larger real-data regression with tolerant scientific oracles and version-
  keyed goldens.
* Performance suites with thresholds on dedicated runners.
* Mutation testing raised to the gate across all critical packages.
* Chaos and negative testing beyond the failure-path suite.
* The manifest verification job that fails when an upstream artefact
  changes, disappears or is superseded.
* Dependency scanning, SBOM validation and the flakiness policy enforced.

Out of scope
------------

* New product features.

Deliverables
------------

* Nightly and matrix CI pipelines, populated rather than stubbed.
* ``src/test/resources/goldens/`` keyed by tool pair, with the review rule
  documented.
* Performance baselines and thresholds.
* ``docs/developer/testing.rst`` completed.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. Every version/platform combination the product offers has a passing
   matrix run, and every combination it does not offer is explicitly
   recorded as unavailable rather than untested.
2. The mutation gate passes at >= 80% in the critical packages with none of
   the forbidden survivors.
3. Determinism repeats on a pinned fixture agree within the documented
   tolerance, and the tolerance is justified in writing.
4. Performance thresholds pass on the dedicated runner and are recorded as
   a baseline.
5. The manifest verification job demonstrably fails when a checksum is
   altered.
6. No release-critical test is quarantined.

Risks and notes
---------------

* The matrix will surface version-specific behaviour differences that look
  like bugs in CometGUI and are not. Record advisories rather than special-
  casing silently.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-15-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-15-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
