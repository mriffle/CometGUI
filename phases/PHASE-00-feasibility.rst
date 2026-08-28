======================================================
PHASE-00: Feasibility, Legal and Upstream Verification
======================================================

:Phase: 00
:Status: NOT STARTED
:Depends on: none
:Blocked by decisions: D-001, D-002, D-004, D-005, D-006, D-007
:Delivers: Evidence only -- this phase owns no ``R-`` rule
:Contributes to: R-PLAT-02, R-PLAT-03, R-PARAM-01, R-PERC-01, R-SEC-01
:Proves: Nothing directly; unblocks everything

Purpose
-------
Establish that the product is buildable and legal before any product code is
written, and replace every dated assumption in the specification with a fact
re-verified today. Revision 2 of the specification did this once, on
2026-08-28; facts drift, and one of them (the PDV version) went stale between
the first and second draft of the same document on the same day.

This phase writes **no product code**. It writes scripts, evidence files and
decision records.

In scope
--------

* Re-verify every row of the specification's Verified Upstream Facts table
  against live upstream sources, and record the result with the date, the
  URL and the method.
* Install a project-local JDK and build tool under ``tools/`` and prove
  ``jpackage`` produces a runnable bundle on Linux; document what is needed
  for the Windows and macOS runners.
* Run a JavaFX startup smoke test on the pinned JDK/JavaFX pair, headless
  and headed.
* Spike TestFX (or the fallback robot) against that pair and record whether
  it works in CI conditions.
* Prove the scientific path end to end from a shell script, with no GUI:
  Comet 2026.02.2 -> pepXML + PIN -> Percolator -> PSM/peptide/weights, and
  on a platform where an XML-capable Percolator exists, -> Percolator XML ->
  Limelight converter -> Limelight XML.
* Prove the Percolator 3.09 path works for rescoring and produces no XML.
* Determine, per tier-1 platform, exactly which Percolator versions can be
  obtained and executed without administrative rights, and cost each option
  in D-002.
* Run the pinned Limelight converter JAR's help output and record its real
  argument names.
* Prove PDV CLI figure generation on a Comet pepXML plus spectrum file.
* Identify candidate test fixture data and its licence for D-006.

Out of scope
------------

* Any Java source belonging to the product.
* Any UI work.
* Choosing an answer to a D- decision on the owner's behalf.

Deliverables
------------

* ``docs/feasibility/upstream-facts.rst`` -- the verified table, dated, with
  method per row.
* ``docs/feasibility/toolchain.rst`` -- JDK/JavaFX/jpackage findings per
  platform.
* ``docs/feasibility/gui-automation-spike.rst`` -- TestFX verdict and the
  fallback plan.
* ``docs/feasibility/scientific-path.rst`` -- the scripted end-to-end proof,
  with commands and outputs.
* ``docs/feasibility/percolator-artefacts.rst`` -- per-platform artefact
  availability and the costed options for ``D-002``.
* ``scripts/feasibility/`` -- the scripts that produced all of the above,
  re-runnable.
* ``tools/`` -- project-local JDK and build tool, with a provenance
  manifest.
* Updated ``DECISIONS.rst`` with recommendations and costs for every
  decision this phase touches.

Exit gate
---------

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

1. Every row of the specification's fact table has been re-verified today,
   and any difference is either reflected in a specification amendment or
   recorded as a decision.
2. A scripted, non-GUI run produces a valid Limelight XML from real fixture
   spectra on at least one platform, and the exact commands are recorded.
3. A scripted run with Percolator 3.09 produces PSM, peptide and weights
   output and demonstrably no XML.
4. ``jpackage`` produces a launchable bundle on Linux from the pinned
   toolchain.
5. The GUI automation spike has a written verdict: TestFX works, or the
   named fallback does.
6. ``docs/feasibility/percolator-artefacts.rst`` states, per tier-1
   platform, whether an XML-capable Percolator can be installed without
   admin rights, and what each remedy costs.
7. ``D-001`` and ``D-002`` each have a written recommendation with
   evidence, ready for the owner.

Risks and notes
---------------

* ``D-001`` (CasanovoGUI licence) may stay open indefinitely. That blocks
  *derivation*, not the project: the specification's architecture is
  implementable from scratch. Do not stall waiting for it; do not copy code
  while waiting.
* The scientific proof may be impossible on this Linux host if the only XML-
  capable Percolator needs glibc 2.38. That is itself the finding -- record
  it and prove the path wherever it can be proven.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-00-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
