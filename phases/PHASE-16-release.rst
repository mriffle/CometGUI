=================================================
PHASE-16: Documentation and Release Qualification
=================================================

:Phase: 16
:Status: NOT STARTED
:Depends on: 15
:Blocked by decisions: D-001, D-004, D-006, D-008
:Delivers: R-DOC-01, R-DOC-02, R-DOC-06
:Contributes to: R-DOC-03, R-DOC-04, R-DOC-05, R-SEC-01
:Proves: AC-DOC-01, AC-DOC-02, AC-REL-02, AC-REL-03, AC-UX-01..06, AC-TST-07

Purpose
-------
Everything that stands between a working application and a release someone else
can install, trust and cite -- including the human activities no agent can
discharge.

In scope
--------

* The complete user documentation tree, including the platform-support and
  troubleshooting pages that state plainly where the Limelight path works
  out of the box.
* The complete developer documentation tree, including traceability.
* The generated parameter reference, built in CI.
* Read the Docs configuration, building successfully.
* Release packaging for every tier-1 platform, with checksums, SBOM, and
  signing or notarisation where infrastructure permits.
* Clean-machine acceptance on each tier-1 platform.
* Licence audit of every bundled and transitive component, including any
  project-built tool binaries.
* Scheduling and recording the six human UX-validation activities and the
  defects they produce.

Out of scope
------------

* Shipping while any AC- criterion is unmet.

Deliverables
------------

* Complete ``docs/`` tree and a published Read the Docs build.
* Release artefacts for each tier-1 platform with checksums and SBOM.
* ``docs/developer/releasing.rst`` and a release checklist.
* Recorded UX session findings, triaged as defects.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. The strict documentation build and the link check both pass, and Read
   the Docs builds the same tree.
2. The traceability report shows every ``R-`` implemented by a phase and
   every ``AC-`` either tested or carrying a recorded human sign-off.
3. A clean machine per tier-1 platform installs only CometGUI and completes
   the Definition of Done workflow.
4. Release artefacts carry published checksums and an SBOM, and the licence
   audit is complete and recorded.
5. ``D-001`` is resolved and recorded before any public redistribution.
6. All six UX-validation activities are complete, with findings triaged.
7. No release-critical test is quarantined and every release gate in the
   pipeline passes.

Risks and notes
---------------

* The human-gated criteria are the long pole. Start scheduling them during
  phase 07, not here.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-16-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
