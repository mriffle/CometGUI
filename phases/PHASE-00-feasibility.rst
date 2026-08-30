======================================================
PHASE-00: Feasibility, Legal and Upstream Verification
======================================================

:Phase: 00
:Status: PARTIAL -- signed off 2026-08-29 by the main orchestrator (see ../STATUS.rst)
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
* Re-derive *latest compatible* rather than accepting the specification's
  answer: enumerate Percolator releases, establish the newest one publishing
  an XML-capable binary for **all three** tier-1 platforms, and confirm it
  against the converter's actual input requirement. The project does not build
  Percolator from source, so a version without a published binary is not a
  candidate.
* **Confirm the Windows artefact.** ``percolator-v3-07.exe`` is an NSIS
  installer whose XML capability is currently inferred from naming and size,
  not verified. On a Windows runner: obtain the payload without administrative
  rights, run ``percolator --help``, and confirm ``-X/--xmloutput`` and
  ``-Z/--decoy-xml-output`` are present. Establish and document the extraction
  mechanism the product will use.
* Establish payload extraction for the other two package formats against the
  real artefacts: ``ar`` + ``data.tar.gz`` for the ``.deb``, ``xar!`` + gzip +
  ``070707`` cpio for the ``.pkg``, including the XSD companion files.
* On Apple silicon, confirm the x86-64 Percolator runs under Rosetta 2 and
  determine how the application detects its absence.
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

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

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
   platform, which XML-capable Percolator artefact is used, how its payload is
   extracted without admin rights, and what its host requirements are.
7. The latest compatible Percolator version is established from upstream data
   with the evidence recorded, not assumed from the specification.
8. The Windows Percolator artefact is confirmed on a Windows runner -- payload
   obtained without admin rights; the binary **observed to start**, evidenced
   by its own version banner rather than by an exit code; and ``--xml-in``
   **not** answering ``Compiler flag XML_SUPPORT was off`` -- or the blocking
   reason is documented precisely and the manifest does not claim it. Because
   ``D-002`` option C ships the portable ``noxml`` archive, the same
   observations are additionally required of **that** binary, which is the one
   the product actually installs.

   .. note::

      **Amended 2026-08-30 by the main orchestrator; strictly stronger than
      the original, and deliberately so.** As first written this item asked
      for "the Windows XML-capable artefact ... ``-X`` present", which fails
      to discriminate in two ways that Phase 00's own evidence exposed and
      that its work unit escalated as ``E1``.

      First, ``-X`` is **not** a discriminating test. Both halves of the
      3.07.1 A/B pair accept ``-X`` and both write a ``percolator_out`` XML;
      that was executed on Linux during Phase 00 and reproduced on 2026-08-30.
      What ``XML_SUPPORT`` gates is the pin-XML *input* path, so only
      ``--xml-in`` -- which the ``noxml`` build refuses **by name** --
      separates the twins. An item satisfied by the build it is meant to rule
      out is not a gate.

      Second, an *absent* diagnostic proves nothing on its own: a binary that
      never started also prints no diagnostic. The item therefore now requires
      positive evidence that the process ran, which is why the banner is named
      explicitly.

      Third, the item asked only about the XML-capable installer, but the
      owner took ``D-002`` **option C** later the same day and the product now
      installs the portable ``noxml`` binary. A gate that interrogates an
      artefact the product does not ship would pass while saying nothing about
      the software users receive, so the shipped artefact is added rather than
      substituted.

      Nothing here lowers the bar: every original requirement is retained, and
      each addition narrows what may count as a pass. The blocking-reason
      branch is unchanged, and remains the branch this item currently rests
      on -- no Windows binary has been executed as of this amendment.
9. Payload extraction is demonstrated for ``.deb``, ``.pkg`` and the NSIS
   ``.exe``, each yielding a runnable binary plus its XSD companions.
10. ``D-001`` has a written recommendation with evidence, ready for the owner,
    and ``D-002``'s decided outcome is confirmed rather than assumed -- or the
    evidence that contradicts it is escalated.

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

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-00-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-00-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
