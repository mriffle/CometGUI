========================================================
PHASE-14: GUI Automation and Packaged End-to-End Harness
========================================================

:Phase: 14
:Status: NOT STARTED
:Depends on: 07, 10, 11, 12, 13
:Blocked by decisions: D-006, D-007
:Delivers: R-TEST-03, R-TEST-05, R-TEST-06
:Contributes to: R-TEST-04
:Proves: AC-TST-05..09, AC-TST-12, AC-INS-01

Purpose
-------
Prove, by driving the product the way a user drives it, that the claims are
true -- in two tiers, because an in-process robot and a packaged application
are different things and revision 1 conflated them.

In scope
--------

* ``FxUiDriver`` completed over the whole application.
* Tier A: assembled-application GUI end-to-end with a fresh temporary home
  and tool cache, running the canonical scenario in full, including
  independent recomputation of counts and hashes.
* Packaging with ``jpackage`` for the tier-1 platforms.
* Tier B: packaged-artefact end-to-end against the exact installer output,
  via an external driver or a test-only loopback bridge.
* The no-XML second scenario in both tiers.
* The failure-path suite from the specification.
* The release check proving no test bridge is present in a published
  artefact.

Out of scope
------------

* Adding product features to make a test easier. If a test needs a seam, add
  the seam through the UI command path.

Deliverables
------------

* ``src/test/e2e/`` with both tiers and the failure-path suite.
* Packaging configuration per platform.
* ``docs/developer/e2e_harness.rst``.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. The canonical scenario passes in Tier A, end to end, from an empty tool
   cache through provenance verification and application restart.
2. The canonical scenario passes in Tier B against the packaged artefact on
   the reference platform.
3. The no-XML scenario passes in both tiers, including the assertion that
   Comet was not rerun.
4. Every failure-path scenario in the specification has a passing test.
5. Deliberately inverting a q-value comparison, dropping a provenance
   entry, or bypassing a downstream stage each cause the canonical test to
   fail -- demonstrated, not assumed.
6. No test uses a fixed sleep; all waits are on observable state.
7. The bridge-absence check fails when the bridge is deliberately included
   in a build.

Risks and notes
---------------

* Tier B is where packaging bugs live: a bundled runtime that cannot find
  JavaFX, a tool cache path that differs when installed, a working directory
  assumption. Budget for it.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-14-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
