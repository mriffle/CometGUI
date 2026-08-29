==========================================================================
PHASE-00 handoff -- Feasibility, Legal and Upstream Verification
==========================================================================

:Phase: 00
:Agent finished: 2026-08-29
:Outcome: PARTIAL
:Phase orchestrator: Phase-00 orchestrator subagent

The residue is named in :ref:`p00-incomplete`. In one line: **everything the
gate asks for was done except executing the Windows binary on Windows, which
gate item 8 explicitly permits documenting instead** -- and the phase turned up
evidence that the specification's model of Percolator XML capability is wrong
in a way that makes the Windows question much less important than it was.

What was built
==============

Phase 00 writes no product code. It produced evidence, scripts and a
project-local toolchain, in ten work units run by ten fresh phase agents. The
work log ``handoffs/PHASE-00-worklog.rst`` carries the decomposition and a
sign-off entry per unit naming what the orchestrator ran and what it saw.

Documents, all under ``docs/feasibility/`` and all clean under
``sphinx-build -n -W``:

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Document
     - What it establishes
   * - ``index.rst``
     - Landing page for the evidence set; Sphinx provenance.
   * - ``upstream-facts.rst``
     - Every row of the specification's fact table re-checked live on
       2026-08-29, with URL, method and verdict, plus a "Differences from
       specification.rst" section.
   * - ``percolator-artefacts.rst``
     - Per tier-1 platform: artefact, payload extraction without admin
       rights, host requirements; and *latest compatible* derived from
       upstream data.
   * - ``windows-artefact.rst``
     - The NSIS payload, the A/B against the ``noxml`` twin, and a precise
       statement of the limits of that evidence.
   * - ``noxml-capability.rst``
     - The per-release, per-platform sweep of whether a build can emit
       Percolator ``pout`` XML. Created mid-phase; see :ref:`p00-surprises`.
   * - ``toolchain.rst``
     - JDK, JavaFX, Maven and ``jpackage`` findings, and the provenance
       manifest (``tools/`` is gitignored, so this document is the record).
   * - ``gui-automation-spike.rst``
     - The TestFX verdict, headless and headed, and the proved fallback.
   * - ``scientific-path.rst``
     - Comet -> pepXML + PIN -> Percolator -> PSM/peptide/weights -> pout XML
       -> Limelight XML, with real numbers at every stage.
   * - ``fixture-candidates.rst``
     - Costed shortlist for ``D-006`` and the ephemeral feasibility input.
   * - ``pdv-converter-spike.rst``
     - PDV CLI figure generation and the converter's real interface.

Scripts, all under ``scripts/feasibility/`` and all re-runnable:
``check-docs.sh``, ``verify_upstream_facts.py``,
``enumerate_percolator_releases.py``, ``extract_deb.py``, ``extract_pkg.py``,
``extract_rpm.py``, ``extract_nsis.py``, ``pe_info.py``,
``windows-artefact.sh``, ``probe_xml_capability.py``, ``noxml_sweep.py``,
``install-toolchain.sh``, ``jpackage-proof.sh``, ``maven-smoke.sh``,
``javafx-smoke.sh``, ``javafx-headed-xvfb.sh``, ``fetch_ephemeral_input.py``,
``run_scientific_path.sh``, ``comet.params``, and the ``jpackage-spike/``,
``gui-spike/`` and ``noxml-sweep/`` spike sources.

Toolchain, project-local under ``tools/`` (gitignored, recreated by
``install-toolchain.sh``): BellSoft Liberica JDK 25.0.4.1+1 Full (bundling
OpenJFX 25.0.4+1) and Apache Maven 3.9.16. Nothing was installed on the host.

Gate items
==========

Every command below was run by the phase orchestrator, not read from an
agent's report. Run them from ``/workspace``.

.. list-table::
   :header-rows: 1
   :widths: 5 9 86

   * - Item
     - Result
     - Command to re-run, and the one-line observation it produces

   * - 1
     - PASS
     - ``python3 scripts/feasibility/verify_upstream_facts.py`` -- 22 facts,
       18 CONFIRMED, 0 CHANGED, 4 UNVERIFIED (the four artefact rows, owned by
       units 3 and 4 and covered by items 6-9). Three differences from the
       specification are recorded and escalated below; **making the
       specification amendment is the main orchestrator's act, not mine.**

   * - 2
     - PASS
     - ``bash scripts/feasibility/run_scientific_path.sh`` -- exit 0;
       12797113-byte Limelight XML with 3897 ``psm`` and 2985
       ``reported_peptide``, schema-valid against ``limelight-xml.xsd``
       (``errors=0 fatals=0``), and my own independently written validator
       rejects a truncated and a tampered copy.

   * - 3
     - PASS
     - Same script, section 11 -- Percolator 3.09 yields 3897 PSM rows, 1026
       at q<0.01, 603 peptides, an 11-row weights file, and **0 XML files**;
       given ``-X``/``--xmloutput``/``-Z`` it exits 1 with "is invalid".

   * - 4
     - PASS
     - ``bash scripts/feasibility/install-toolchain.sh && bash
       scripts/feasibility/jpackage-proof.sh``, then
       ``env -i PATH=/usr/bin:/bin
       _build/jpackage-spike/dest/ToolchainProbe/bin/ToolchainProbe`` --
       prints ``java.version = 25.0.4.1``, ``java.home =
       .../ToolchainProbe/lib/runtime``, ``self contained = true``,
       ``PROBE RESULT = PASS`` with no JDK on PATH.

   * - 5
     - PASS
     - ``bash scripts/feasibility/javafx-smoke.sh`` (and
       ``javafx-headed-xvfb.sh``) -- 7/7 and 6/6 stages PASS; TestFX 4.0.18
       works headless *and* headed; ``Scene.snapshot()`` pixel (2,2) =
       ``ff204080`` proves rendering; both deliberate-failure controls fail as
       required.

   * - 6
     - PASS
     - ``sed -n '44,82p' docs/feasibility/percolator-artefacts.rst`` -- a
       per-tier-1-platform table of artefact, extraction mechanism without
       admin rights, and host requirements, with the Windows capability cell
       left explicitly unverified.

   * - 7
     - PASS
     - ``python3 scripts/feasibility/enumerate_percolator_releases.py`` --
       computes *latest compatible* from 28 releases and returns **3.7.1
       (rel-3-07-01)** under both a strict and an optimistic classification.
       Derived, not assumed; it agrees with the specification.

   * - 8
     - PASS (second branch only)
     - ``bash scripts/feasibility/windows-artefact.sh`` -- the payload
       extracts without admin rights and yields a PE32+ x86-64
       ``percolator.exe`` importing 92 ``xercesc_3_1`` symbols plus both XSDs.
       **The binary was not executed on Windows.** The gate's first branch is
       NOT met; the second is: the blocking reason is documented precisely and
       the prescribed manifest value is ``xml_capability:
       unverified-on-windows``. I grepped every feasibility document for
       verified/confirmed/proven/tested near a Windows XML claim and found
       none.

   * - 9
     - PASS (extraction; runnability executed on Linux only)
     - ``bash scripts/feasibility/windows-artefact.sh`` plus
       ``python3 scripts/feasibility/extract_deb.py`` /
       ``extract_pkg.py`` -- all three payloads extract without root, each
       yielding the platform's correct executable format plus
       ``percolator_out.xsd`` and ``percolator_in.xsd``. Only the Linux binary
       was **executed** (it runs, and emits XML); the Mach-O and PE binaries
       were identified by parsing their headers, never run. Cross-check that
       the NSIS extractor is correct: its ``noxml`` output is byte-identical
       (sha256 ``b9d9bbe8...f059f``) to the same file taken from the portable
       ZIP by an unrelated code path.

   * - 10
     - PASS
     - ``sed -n '/^Decisions encountered/,/^Surprises/p'
       handoffs/PHASE-00-handoff.rst`` -- ``D-001`` has a written
       recommendation with evidence re-verified today; ``D-002``'s outcome is
       confirmed from recomputed upstream data **and** the evidence that
       contradicts its stated rationale is escalated below.

.. _p00-incomplete:

What is incomplete and why
==========================

#. **The Windows binary has never been executed.** Not on Windows, not under
   emulation. Gate item 8's first branch is unmet and cannot be met from a
   Linux host. Everything else about the artefact is established: the payload
   extracts without administrative rights, ``percolator.exe`` is PE32+
   x86-64, it imports 92 Xerces symbols the ``noxml`` twin does not, and both
   XSDs ship with it. What is missing is that it starts, that its ``--help``
   prints ``-X`` and that ``-X`` writes XML **there**. Cost to close it is in
   :ref:`p00-decisions` under ``D-002``.

#. **No macOS execution, and Rosetta 2 is untested.** ``D-004`` is decided on
   the basis that the 3.07.1 macOS artefact is x86-64 only, which was
   re-confirmed by parsing the Mach-O header (``cputype 0x1000007``). That the
   binary runs under Rosetta 2 remains unverified, as does how the application
   detects Rosetta's absence.

#. **The specification amendment is not made.** A phase agent may not edit
   ``specification.rst``. Three fact-table differences and a much larger
   correction to the Percolator artefact section are escalated below for the
   main orchestrator to action.

#. **One dataset, one search configuration, one platform.** The scientific
   path was proven once, on Linux, with one ephemeral input and one set of
   Comet parameters. It is a feasibility proof, not a test matrix.

.. _p00-decisions:

Decisions encountered
=====================

Drafted for the main orchestrator, which owns ``DECISIONS.rst``. **No ``D-``
item was answered by this phase.**

``D-001`` -- CasanovoGUI source reuse (OPEN, re-verified today)
---------------------------------------------------------------

**Evidence, 2026-08-29.** ``GET /repos/Noble-Lab/CasanovoGUI`` returns
``license = null``; a recursive tree of ``main`` (106 entries, not truncated)
contains no ``LICENSE``, ``COPYING`` or ``NOTICE`` at any depth; last pushed
2026-08-21. Unchanged from 2026-08-28. A public repository is not a grant of
redistribution rights.

**Recommendation.** Unchanged and now better supported: ask upstream for an
explicit licence (cost: an email and goodwill; best outcome for downstream
users), and proceed independently meanwhile. Phase 00 confirms the cost of
independence is lower than feared -- the toolchain, packaging and GUI-automation
patterns this phase established (Liberica Full + jpackage app-image + TestFX
with an injected Monocle) were all derived from scratch here without reference
to CasanovoGUI. Do not stall; do not copy code while it is open.

``D-002`` -- XML-capable Percolator artefact strategy (DECIDED; rationale needs correcting)
-------------------------------------------------------------------------------------------

**The decision survives; the reasoning recorded under it does not.** 3.07.1 is
still the right answer, and *latest compatible* recomputed from live upstream
data returns exactly that. But ``DECISIONS.rst`` justifies the Windows and
macOS rows from strings and file size, and Phase 00 shows those markers track
the pin-XML **reader**, not the pout-XML **writer**. See
:ref:`p00-surprises`. Options, costed:

* **A -- amend the rationale, keep the decision.** Cost: a documentation edit.
  Leaves the manifest honest and the artefact set unchanged.
* **B -- also run the Windows check.** A developer with a Windows machine
  follows the seven-step checklist in ``windows-artefact.rst`` (pinned to
  ``percolator.exe`` sha256 ``044f3957...``); cost ~15 minutes of one person,
  no decision required, and it closes gate item 8's first branch. A GitHub
  Actions ``windows-latest`` runner is free and repeatable but is **blocked by
  D-008**, since there is no remote. A cloud Windows VM costs under $1/hour
  plus credentials the project does not have. A project-local wine was assessed
  and **not attempted**: no self-contained relocatable build exists, and it
  would prove nothing about Windows anyway.
* **C -- act on the ``noxml`` finding.** If portable ``noxml`` archives can
  feed Limelight, the NSIS and ``.pkg`` extraction paths become optional. Cost:
  a re-scoping of Phase 05, and the Windows and macOS halves still need one
  execution each to be more than inference.

``D-003`` -- managed Percolator version/platform coverage (OPEN, widened)
-------------------------------------------------------------------------

Phase 00 *widens* rather than narrows this. Every ``noxml`` artefact from 3.05
to 3.08 now appears to be a Limelight candidate, and 3.06.5's
``noxml-linux-portable.zip`` has the lowest glibc floor found anywhere
(``GLIBC_2.14``). Two corrections to the entry's own text: 3.09 publishes **no
Linux portable archive at all**, and its ``.deb``/``.rpm`` need Boost libraries
they do not ship, so "its portable archives need no payload extraction" is
false on Linux; and the 3.09 macOS build is **arm64-only, minimum macOS 15.0**,
so it reaches *fewer* Macs than 3.07.1 x86-64 under Rosetta.
**Recommendation:** publish the matrix explicitly per the existing entry, but
populate it from the functional capability probe below rather than from
artefact names.

``D-005`` -- PDV integration level (OPEN)
------------------------------------------

Evidence in ``docs/feasibility/pdv-converter-spike.rst``, and the fact in
``DECISIONS.rst`` is now sharper rather than merely re-confirmed. PDV 2.7.0
**does** ship a control server (``PDVGUI/gui/utils/PdvControlServer``, with
``/ready``, ``/select`` and ``/shutdown`` on ``127.0.0.1``), but it is reachable
only through ``java -jar PDV.jar denovo-gui --mztab <f> --spectrum <f>``, and
``spectra_ref`` resolution exists only in ``MztabImport``. Comet plus Percolator
never produces mzTab, so **there is no path from CometGUI's results to that
server today**.

**Recommendation:** ship the baseline (open-in-PDV plus CLI figure generation)
for release 1 and propose the enhancement upstream. Costs: the baseline is
bounded and was proved working in this phase; an upstream contribution is
*cheaper than previously assumed*, because the mechanism exists and would be
extended rather than invented -- but its schedule belongs to a third party; a
maintained fork costs a permanent maintenance burden and, given the licence
contradiction below, needs a legal answer first. Do not substitute
screen-coordinate automation.

``D-006`` -- test fixture data and licensing (OPEN; shortlist delivered)
------------------------------------------------------------------------

The costed shortlist is ``docs/feasibility/fixture-candidates.rst`` -- six
candidates with real file names, real sizes, parsed MS2 counts and a licence
URL each. The decisive finding is that **PRIDE exposes a machine-readable
per-project ``license`` field**, so "a PRIDE dataset" is not a licence status:
``PXD079076`` is CC0, ``PXD000001`` is "EBI terms of use". The best search
quality comes from the Crux smoke-test spectra (Apache-2.0 over the *project*,
with the instrument data's own provenance undocumented -- a real caveat for
redistribution); the cleanest licence comes from the CC0 PRIDE set, whose
pTyr enrichment makes a plain tryptic search yield little.

**The ephemeral input used to prove the scientific path is NOT the project's
fixture** and is not in git: the two Crux K562 mzML files and a UniProt human
FASTA (CC BY 4.0), fetched by checksum into ``scratch/fixture/``. Recorded in
full in the document. Choosing the fixture remains the owner's.

``D-007`` -- Limelight test endpoint (OPEN)
--------------------------------------------

Not exercised: no automated test contacted any Limelight server, by design.
**Recommendation unchanged and now cheaper than it looked:** build the local
fake as the default and add a sandbox nightly. Phase 00 establishes that the
converter's output is schema-valid against the ``limelight-xml.xsd`` shipped
*inside* the converter JAR (65905 bytes) -- there is **no standalone XSD** in
``limelight-import-api`` or ``limelight-core``, so a fake endpoint can validate
uploads locally using that embedded schema, at the cost of extracting it from
the JAR at build time.

``D-008`` -- licence and distribution (OPEN, untouched)
--------------------------------------------------------

No remote was created. It is worth recording that ``D-008`` now blocks
something concrete: the cheapest repeatable way to close gate item 8 is a
Windows CI runner, and there is nowhere to run CI.

.. _p00-surprises:

Surprises
=========

**1. The ``noxml`` Percolator builds emit Percolator XML. This is the phase's
biggest finding and it invalidates the specification's model.** The whole
artefact strategy rests on the claim that upstream publishes an XML-capable
build and a ``noxml`` twin, that every portable archive is a ``noxml`` build,
and that XML-capable artefacts are therefore all OS packages needing payload
extraction. The orchestrator verified personally that the 3.07.1 Linux
``noxml`` binary, run as ``percolator -X out.xml pin``, exits 0 and writes
``percolator_out/15`` XML **byte-identical to the XML-capable build's apart
from the ``<command_line>`` element**. Two units then confirmed independently
that the Limelight converter accepts that XML and produces a schema-valid
Limelight file. ``XML_SUPPORT`` gates the pin-XML **input** path
(``--xml-in``), not the pout-XML **output** path -- and Comet writes TSV PIN,
so the product never needs the input path. Consequences: the NSIS and ``.pkg``
extraction machinery may be unnecessary; portable ZIPs may serve Windows and
macOS with no administrative rights at all; and the specification's
"Percolator versions and artefact availability" section needs a substantial
amendment. What is *not* changed: **3.09 genuinely cannot emit XML** (executed;
``-X`` rejected with exit 1), so the ceiling for the Limelight path is still
3.08.x, and 3.08's availability problems (glibc 2.38, arm64-only macOS) still
make 3.07.1 the right choice.

**2. A capability probe that greps ``--help`` for ``-X`` cannot work.** The
``noxml`` and XML builds have *identical* help text -- ``-X, --xmloutput`` and
``-Z, --decoy-xml-output`` appear in both. Gate item 8's own suggested test
("confirm ``-X`` is present") would therefore pass on a build regardless. This
directly shapes ``R-PERC-02``: the probe must be **functional** -- run ``-X``
and ``-X -Z`` on a small PIN and require the file to exist, to contain the
``percolator_out/15`` namespace and to carry the expected ``<psm>`` count.
``scripts/feasibility/probe_xml_capability.py`` currently reports "NOT
XML-capable" for a binary whose XML the Limelight converter consumed; Phase 09
must not inherit that logic.

**3. Percolator's own XSD rejects its own output.** The
``percolator_out.xsd`` shipped inside the 3.07.1 package declares
``majorVersion fixed="2"`` while the binary writes ``3``. Every other
constraint validates. ``R-TOOL-02`` requires installing those XSDs with the
binary; a phase that validates against them will fail on correct output.

**4. The fetched mzML files were CRLF-corrupted and Comet failed on them.**
Comet exits **249** with ``parseOffset() 2: Syntax error parsing XML``. Proven
by the files' own ``<fileChecksum>`` and ``<indexListOffset>``, which match
only the LF form. The scientific-path script repairs a copy and refuses to
proceed unless both invariants hold. Any phase that ingests indexed mzML needs
this defence.

**5. ``R-LL-05``'s rationale is wrong in both directions.**
``--import-decoys`` over a decoy-free pout does *not* fail obscurely -- it
exits 0 and writes a valid file with zero decoys. Conversely ``-Z`` *without*
``--import-decoys`` is a hard failure. And ``--import-decoys`` is
**incompatible with Comet's ``decoy_search = 1``**, because the converter
requires the decoys to be real FASTA entries; it works only with an externally
concatenated target+decoy FASTA and ``decoy_search = 0``. Also, Percolator
3.07.1 *silently ignores* ``-Z`` without ``-X`` despite its help saying "Only
available if -X is set".

**6. Two Comet CLI behaviours are silent rather than rejecting.** ``-N`` with
more than one input file is silently ignored (exit 0), not rejected. And
Percolator accepts multiple PIN files positionally and merges them, despite a
singular usage line -- which is exactly what the multi-file run model needs.

**7. The GitHub releases atom feed lists tags that have no release.**
``releases.atom`` shows ``rel-3-08-01``, but ``GET
/releases/tags/rel-3-08-01`` returns **404**. The specification's claim holds,
but the rate-limit-free fallback must never be used alone to enumerate
releases -- Phase 15's drift-detection job would draw the wrong conclusion.

**8. The pinned JDK ships no Monocle, and this host has no fonts.** JavaFX 25
in Liberica Full contains only the GTK Glass platform; Monocle must be injected
from ``org.testfx:openjfx-monocle:21.0.2`` with ``--patch-module``, not put on
the class path. Separately, any ``Scene`` containing a control dies with
``fontFactory is null`` without freetype/fontconfig/pango and actual font
files. **A CI runner will hit both.** Fonts are an explicit CI dependency, and
Phase 16 should check whether a ``jpackage`` bundle finds fonts on a minimal
target machine.

**9. ``jpackage`` strips the bundled runtime's own ``java`` launcher.**
CometGUI must launch the Limelight converter JAR as a child process, and a
shipped runtime with no ``bin/java`` cannot. Passing
``--jlink-options "--strip-debug --no-man-pages --no-header-files"`` keeps it at
no measurable size cost. Also, ``deb`` output fails here for want of
``fakeroot`` and ``rpm`` is not offered at all -- neither may be installed on
this host.

**10. Three differences from the specification's fact table**, none material
but all worth an amendment: the Comet release publishes a ninth asset
(``README.md``); the ``rel-3-09`` XML-removal quote the specification presents
as verbatim is a reflow, the published bytes breaking mid-sentence between
``toolchains`` and ``. (#399)``; and the converter also documents ``-h/--help``
and ``-V/--version``, so twelve options, not ten. Separately: ``comet.macos.exe``
and ``comet.aarch64.macos.exe`` are **exactly the same size** (3998328 bytes),
which whoever writes the download manifest should look at.

**11. PDV's command-line mode is not headless, and it lies about success.**
``PDVCLI.PDVCLIMainClass`` extends ``javax.swing.JFrame``, so it throws
``HeadlessException`` before it parses an argument; ``-Djava.awt.headless=true``
makes it worse, not better. Figure generation therefore needs a display on the
user's machine -- fine on a desktop, a real constraint for CI and for any
headless workflow. Worse: **given the pepXML and the very mzML Comet searched,
PDV exits 0 and writes no figure at all.** The cause was traced to
``msftbx`` numbering spectra by 1-based file position while pepXML carries the
instrument scan number, which diverge for any scan-range subset. The working
route was via MGF, whose ``TITLE`` carries the pepXML ``spectrumNativeID``.
Phase 11 must verify PDV's output files rather than its exit status, and must
impose a timeout: ``-rt 6`` (TIC) ran 600 seconds writing nothing and had to be
killed.

**12. The Limelight converter's exit status is not a success signal either.**
It exits 0 with no arguments at all, and exits 0 on an unrecognised option; it
returns 1 only when required options are present and the files behind them are
missing. Phase 12 must check the output file, not the return code.

**13. PDV's licence is self-contradictory upstream.** ``LICENSE`` at tag
``v2.7.0`` is **GPL-3.0** (674 lines, sha256 ``8ceb4b9e...``) while its
``pom.xml`` declares **Apache-2.0**. The product installs and launches PDV, so
this needs an answer before redistribution -- it bears on ``D-008`` and on the
licence audit, and it is not a question an agent may settle.

First thing the next agent should do
====================================

**Take the ``noxml`` finding to the owner before Phase 05 designs the
installer.** Concretely: read ``docs/feasibility/noxml-capability.rst``, then
decide whether ``specification.rst``'s "Percolator versions and artefact
availability" section is amended to say that pout-XML output is available from
*every* published 3.05-3.08 artefact including the portable ``noxml`` archives.
That answer determines whether Phase 05 must implement NSIS and ``xar``/cpio
payload extraction at all, and it is much cheaper to settle now than after the
installer is written.
