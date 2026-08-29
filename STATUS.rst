==============
Project Status
==============

:Project: CometGUI -- Comet + Percolator desktop workflow
:Updated: 2026-08-29
:Updated by: Main orchestrator (Phase 00 signed off; D-001 decided, D-008 part-decided)
:Current phase: 00 -- PARTIAL, signed off; 01 ready and no longer licence-blocked
:Overall: Feasibility established. No product code exists yet, by design --
   Phase 00 writes none. Phase 01 is ready to start.

This file is the **only** authoritative record of where the project is. Update
it at every gate, every decision and every milestone. If it disagrees with
anything else, fix it here first.

Where we are
============

Phase 00 ran and was signed off **PARTIAL** on 2026-08-29. The scientific path
is proven end to end, the toolchain is installed and packaging works, and the
upstream facts were re-verified live -- one of them changed *during* the phase.
The residue is small and precisely named (see :ref:`status-p00`). Nothing that
Phase 01 needs is blocked.

What exists
-----------

.. list-table::
   :header-rows: 1
   :widths: 30 14 56

   * - Artefact
     - State
     - Notes
   * - ``specification.rst``
     - Revision 5
     - Revision 5 records Phase 00's verification findings, including the
       ``noxml`` discovery, without yet rewriting the artefact strategy --
       that is ``D-002`` option C, an owner decision. Passes
       ``sphinx-build -n -W``.
   * - ``ONBOARDING.rst``
     - Complete
     - Read-first document for any orchestrating agent.
   * - ``phases/`` (00-16)
     - Complete
     - Scope, deliverables and exit gate per phase. None started.
   * - ``DECISIONS.rst``
     - 6 open, 2 decided
     - ``D-002`` and ``D-004`` decided. ``D-001`` changed materially on
       2026-08-29 and is now coupled to ``D-008``.
   * - ``handoffs/``
     - Phase 00 present
     - ``PHASE-00-worklog.rst`` (10 units, each with a sign-off entry) and
       ``PHASE-00-handoff.rst``.
   * - ``docs/feasibility/``
     - 10 documents
     - The phase's evidence. Builds clean under ``sphinx-build -n -W``
       (13 HTML pages) via ``scripts/feasibility/check-docs.sh``.
   * - ``scripts/feasibility/``
     - Re-runnable
     - Every claim above is reproducible from these scripts; the main
       orchestrator re-ran them at sign-off rather than reading the reports.
   * - ``tools/``
     - Installed, gitignored
     - Liberica JDK 25.0.4.1+1, Maven 3.9.16, OpenJFX Monocle 21.0.2, plus an
       X11 and font stack. Provenance (URL, SHA-256, licence) is recorded in
       the committed ``docs/feasibility/toolchain.rst``; rebuild with
       ``scripts/feasibility/install-toolchain.sh``.
   * - Product code
     - None
     - Correct: Phase 00 writes none. Phase 01 creates the build skeleton.

Phase board
===========

.. list-table::
   :header-rows: 1
   :widths: 8 40 16 36

   * - Phase
     - Title
     - Status
     - Gate evidence
   * - 00
     - Feasibility, legal and upstream verification
     - PARTIAL
     - Signed off 2026-08-29. 9 of 10 gate items PASS on the main
       orchestrator's own re-run; item 8 passes only on its second branch.
       See :ref:`status-p00`.
   * - 01
     - Repository, build and quality skeleton
     - NOT STARTED
     - --
   * - 02
     - Application shell and navigation
     - NOT STARTED
     - --
   * - 03
     - Process service
     - NOT STARTED
     - --
   * - 04
     - Hashing and provenance core
     - NOT STARTED
     - --
   * - 05
     - Tool registry and installer
     - NOT STARTED
     - --
   * - 06
     - Comet parameter model
     - NOT STARTED
     - --
   * - 07
     - Comet parameter editor UI
     - NOT STARTED
     - --
   * - 08
     - Workflow engine and Comet adapter
     - NOT STARTED
     - --
   * - 09
     - Percolator adapter and version capabilities
     - NOT STARTED
     - --
   * - 10
     - Results model and UI
     - NOT STARTED
     - --
   * - 11
     - PDV integration
     - NOT STARTED
     - --
   * - 12
     - Limelight conversion and upload
     - NOT STARTED
     - --
   * - 13
     - Provenance UI and reports
     - NOT STARTED
     - --
   * - 14
     - GUI automation and packaged end-to-end harness
     - NOT STARTED
     - --
   * - 15
     - Version matrix, performance and hardening
     - NOT STARTED
     - --
   * - 16
     - Documentation and release qualification
     - NOT STARTED
     - --

Status values are ``NOT STARTED``, ``IN PROGRESS``, ``PARTIAL`` (with the
residue named), ``BLOCKED`` (with the decision or dependency named), or
``PASSED`` (with the date and the evidence the orchestrator verified).


.. _status-p00:

Phase 00 sign-off (2026-08-29)
==============================

:Outcome: **PARTIAL**
:Signed off by: Main orchestrator, by re-running every gate item itself
:Phase records: ``handoffs/PHASE-00-worklog.rst``, ``handoffs/PHASE-00-handoff.rst``

The phase orchestrator reported PARTIAL. The main orchestrator re-ran all ten
items rather than accepting that report; the table below records what the
re-run actually printed.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Result
     - What was run, and what it printed

   * - 1
     - PASS
     - ``.venv/bin/python scripts/feasibility/verify_upstream_facts.py
       --refresh`` -> ``CHANGED=1, CONFIRMED=17, UNVERIFIED=4``. The four
       unverified rows are artefact rows, covered by items 6-9. The one
       ``CHANGED`` row is CasanovoGUI's licence -- the script detecting a real
       drift, which is it working. Four differences from the specification are
       listed and are now recorded in specification revision 5.

   * - 2
     - PASS
     - ``bash scripts/feasibility/run_scientific_path.sh`` -> exit 0, and the
       output was checked rather than the exit code: a 12 797 113-byte
       Limelight XML with 3897 ``<psm>`` and 2985 ``<reported_peptide>``
       elements. The main orchestrator validated it with its **own**
       ``javax.xml.validation`` program (``warnings=0 errors=0 fatals=0,
       VERDICT: VALID``) and proved that validator falsifiable: a truncated
       copy gives ``fatals=1`` and a copy with one bogus attribute gives
       ``cvc-complex-type.3.2.2 ... 'bogusAttr' is not allowed``.

   * - 3
     - PASS
     - Same run, stage 11. Percolator 3.09 produced 3898-line PSM, 2986-line
       peptide and 12-line weights files, and ``find ... -name '*.xml' | wc
       -l`` -> ``0``. Its ``--help`` carries no XML flag at all.

   * - 4
     - PASS
     - ``env -i PATH=/usr/bin:/bin HOME=/tmp
       _build/jpackage-spike/dest/ToolchainProbe/bin/ToolchainProbe`` ->
       ``java.version = 25.0.4.1``, ``self contained = true``, ``javafx
       modules = all present``, ``PROBE RESULT = PASS`` with no JDK on PATH.

   * - 5
     - PASS
     - ``bash scripts/feasibility/javafx-smoke.sh`` -> 7/7 stages. TestFX
       4.0.18 works headless. Two stages are deliberate negative controls and
       both failed as required, so the harness is falsifiable rather than
       merely green.

   * - 6
     - PASS
     - ``sed -n '44,84p' docs/feasibility/percolator-artefacts.rst`` -> a
       per-platform table giving artefact, container, extraction-without-admin
       and host requirement. Linux and macOS cells say "Verified here"; the
       Windows cell says "Not established" in both columns.

   * - 7
     - PASS
     - ``python3 scripts/feasibility/enumerate_percolator_releases.py`` ->
       ``latest compatible = 3.7.1 (rel-3-07-01)`` under both a strict and an
       optimistic rule. Because that script reads a cache by default, the main
       orchestrator additionally queried the GitHub API directly at sign-off
       and confirmed the inputs live.

   * - 8
     - PASS -- second branch only
     - ``bash scripts/feasibility/windows-artefact.sh`` -> the NSIS payload
       extracts with no elevation, yielding a PE32+ x86-64 ``percolator.exe``
       importing 92 ``xercesc_3_1`` symbols plus both XSDs. **The binary was
       not executed on Windows, and no Windows runner exists here.** The gate
       item's second branch requires the blocking reason to be documented and
       the manifest not to claim the capability: both hold. The manifest value
       is ``xml_capability: unverified-on-windows`` and a grep of every
       committed document for *verified*, *confirmed*, *proven* or *tested*
       near a Windows XML claim returns nothing. New finding: both NSIS
       installers request ``requireAdministrator``, so extraction -- not
       installation -- is the only admin-free route.

   * - 9
     - PASS
     - ``extract_deb.py``, ``extract_pkg.py`` and the NSIS extractor were each
       run by the main orchestrator into a fresh directory. Each yielded its
       platform's binary plus ``percolator_in.xsd`` and
       ``percolator_out.xsd``. The freshly extracted Linux binary was then run
       (``-X`` -> 200 ``<psm>`` elements, exit 0). The NSIS extractor is
       cross-checked: its ``percolator.exe`` is byte-identical to the copy
       taken from the portable ZIP by unrelated code. Only the Linux binary
       was executed.

   * - 10
     - PASS
     - ``handoffs/PHASE-00-handoff.rst`` carries a written, evidenced
       recommendation for ``D-001`` and confirms ``D-002``'s outcome from
       recomputed data while escalating evidence that contradicts its stated
       rationale. Its ``D-001`` evidence was overtaken during sign-off -- see
       below.

Why PARTIAL rather than PASSED
------------------------------

#. **The Windows binary has never been executed, anywhere.** Gate item 8's
   first branch is unmet. This is honest inference, not a verified fact, and
   the manifest says so.
#. **No macOS execution.** The ``.pkg`` payload was extracted and inspected;
   Rosetta 2 (``D-004``) is untested because there is no Mac here.
#. **The ``noxml`` finding is recorded but not acted on.** Acting on it
   re-scopes Phase 05 and is ``D-002`` option C -- an owner decision.

What the main orchestrator caught that the phase did not
--------------------------------------------------------

**``Noble-Lab/CasanovoGUI`` published GPL-3.0 at 2026-08-29T01:56:35Z, while
Phase 00 was running.** The work unit checked before that commit and correctly
recorded ``license = null``; the sign-off re-check about an hour later found
the licence present, verified three ways (repository API, licence API blob sha
``f288702d...``, and the commit list). Both observations were right when made.
This is precisely the drift the phase exists to catch, and it is the reason
sign-off re-runs checks instead of reading reports. ``D-001`` is rewritten
accordingly.

Open decisions
==============

``D-001``, ``D-002`` and ``D-004`` are decided; ``D-008`` is half decided.
Phase 00 supplied evidence and costed options for every item; **none was
answered by an agent.** On 2026-08-29 the owner decided the licensing question:
**CometGUI is GPL-3.0**, derived from CasanovoGUI, and **PDV is to be treated
as GPL-3.0**. Four items remain open: ``D-003``, ``D-005``, ``D-006``,
``D-007``, plus the publication half of ``D-008``.

.. list-table::
   :header-rows: 1
   :widths: 12 46 42

   * - ID
     - Question
     - Blocks
   * - ``D-001``
     - **DECIDED**: derive from CasanovoGUI and release CometGUI under
       **GPL-3.0**. The copyleft commitment is accepted; ``R-SEC-01``'s
       no-copying constraint is lifted.
     - --
   * - ``D-002``
     - **DECIDED**: no source builds; use 3.07.1, the newest release with
       XML-capable binaries on all three platforms.
     - --
   * - ``D-003``
     - **Widened** by the ``noxml`` finding: every ``noxml`` artefact 3.05-3.08
       may be a Limelight candidate, and 3.06.5 has the lowest glibc floor
       found (``GLIBC_2.14``).
     - 05, 09, 15
   * - ``D-004``
     - **DECIDED**: Percolator runs under Rosetta 2 on Apple silicon.
     - --
   * - ``D-005``
     - PDV baseline only, or enhanced control mode?
     - 11
   * - ``D-006``
     - Which fixture data, under which licence?
     - 00, 14, 16
   * - ``D-007``
     - Which Limelight endpoint do tests target?
     - 12, 14
   * - ``D-008``
     - **PART DECIDED**: the licence is **GPL-3.0**, so Phase 01 is unblocked.
       Still open: where CometGUI is published (**still no remote -- do not
       create one**) and whether tool binaries are redistributed or downloaded.
     - 16; and gate item 8 of phase 00, whose cheapest fix needs a remote

Risks currently live
====================

#. **Percolator 3.07.1 is the product's XML-capable default** -- resolved
   2026-08-29 (``D-002``). The owner ruled out source builds and required
   published binaries on all three tier-1 platforms; 3.07.1
   (``rel-3-07-01``, 2024-06-20) is the newest release meeting both. The Linux
   build was extracted and executed here (``-X/--xmloutput`` present, needs
   only ``GLIBC_2.34``); the macOS payload was extracted and inspected
   (Mach-O binary plus the XSDs, x86-64 only). **The Windows artefact's XML
   capability is inferred, not verified** -- Phase 00 must confirm it on a
   Windows runner before the manifest claims it. Residual risk is now
   engineering, not strategy: payload extraction for ``.deb``, ``.pkg`` and
   NSIS; XSD companion installation; Rosetta 2 on Apple silicon (``D-004``).
   The accepted trade is that 3.07.1 predates 3.08's I-spline PEP default and
   the PEP-greater-than-1.0 fix, carried as advisories (``R-PERC-11``).
#. **The ``noxml`` Percolator builds emit XML.** Verified by execution
   2026-08-29 and re-verified at sign-off: the 3.07.1 Linux ``noxml`` binary
   given ``-X`` writes a ``percolator_out/15`` document differing from the
   XML-capable build's in two lines, both inside ``<command_line>``.
   ``XML_SUPPORT`` gates the pin-XML *input* path, which the product never
   uses. Two consequences. First, the NSIS and ``xar``/cpio extraction paths
   may be unnecessary -- ``D-002`` option C, an owner decision, so nothing has
   been re-scoped. Second, and not optional: **a capability probe that greps
   ``--help`` for ``-X`` is invalid**, because the twins' help text is
   identical. ``R-PERC-02`` needs a functional probe, and
   ``scripts/feasibility/probe_xml_capability.py`` is currently wrong.

#. **Licensing is settled and is no longer a risk.** CasanovoGUI published
   GPL-3.0 mid-phase; the owner decided on 2026-08-29 that CometGUI is
   **GPL-3.0** and derives from it. Apache-2.0 dependencies (Comet,
   Percolator, the Limelight converter) are compatible in that direction, and
   the bundled Liberica JRE is GPLv2 **with the Classpath Exception**, which
   permits the combination. What remains is compliance work, not choice:
   the ``LICENSE`` file (Phase 01), derivation notices (Phase 02),
   ``docs/citations.rst`` attribution, and a source-availability mechanism
   (Phase 16, dependent on the open publication half of ``D-008``). One cheap
   follow-up: ask the CasanovoGUI authors to confirm the grant covers the
   repository's existing history, since it carries a merged outside
   contribution.
#. **Upstream drift.** PDV moved 2.6.0 -> 2.7.0 between the first and second
   drafts of the specification on the same day. Phase 00 re-verifies
   everything; phase 15 adds the CI job that catches it thereafter.
#. **Percolator's own XSD rejects Percolator's own output.** The shipped
   ``percolator_out.xsd`` fixes ``majorVersion`` at ``2``; the 3.07.1 binary
   writes ``3``. Verified at sign-off -- validating correct output against the
   shipped schema produces ``cvc-complex-type.3.1``. ``R-TOOL-02`` installs
   those XSDs, so any phase that validates against them will fail on good
   data.

#. **No Windows or macOS execution is possible in this environment.** Every
   non-Linux capability verdict in the project is currently inference from
   byte markers. This is the single largest gap in the evidence base and it
   will persist until either a remote enables Windows CI (``D-008``) or a
   person runs the 15-minute checklist in
   ``docs/feasibility/windows-artefact.rst``.

#. **Toolchain installed project-locally.** No longer a risk: Liberica JDK
   25.0.4.1+1, Maven 3.9.16 and OpenJFX Monocle live under ``tools/`` with
   checksum-pinned provenance in ``docs/feasibility/toolchain.rst``. Nothing
   was installed on the host. Two carried findings: **``jpackage`` strips the
   runtime's ``bin/java``** (verified -- the app image has no ``bin``
   directory at all), which matters because CometGUI must launch the Limelight
   converter JAR; and the pinned JDK ships no Monocle and this host has no
   fonts, so a ``Scene`` with any control dies on ``fontFactory is null``
   unless both are supplied.

Next action
===========

**Run Phase 01** (``phases/PHASE-01-build-skeleton.rst``). It is ready and no
longer blocked in any part: its only dependency, Phase 00, is signed off; the
toolchain it needs is installed and provenanced; and its ``LICENSE`` file is
now determined -- the full, unmodified GNU General Public License version 3
text at the repository root. Phase 01 must place the real GPLv3 text, not a
paraphrase or a summary.

For the owner, in priority order
--------------------------------

#. **``D-002`` option C -- act on the ``noxml`` finding, or not.** If the
   portable ``noxml`` archives can feed Limelight, Phase 05 need not implement
   NSIS or ``xar``/cpio extraction at all. Deciding before Phase 05 starts
   saves building the most fragile code in the installer; deciding after wastes
   it. **This is now the only decision with a deadline.**
#. **The publication half of ``D-008``.** Where CometGUI is published, and
   whether tool binaries are redistributed or downloaded from upstream. The
   remote question is what keeps Phase 00's gate item 8 open: a free GitHub
   Actions ``windows-latest`` runner would close it permanently and re-run on
   every change.

*Answered on 2026-08-29:* ``D-001`` and the licence half of ``D-008`` --
CometGUI is GPL-3.0, derived from CasanovoGUI, with PDV treated as GPL-3.0.
Neither open item blocks starting Phase 01.

Change log
==========

.. list-table::
   :header-rows: 1
   :widths: 14 20 66

   * - Date
     - Phase
     - Entry
   * - 2026-08-28
     - --
     - Specification received (revision 1) and committed as the baseline.
   * - 2026-08-28
     - --
     - Deep review completed. Upstream facts verified against live sources;
       Percolator artefact finding recorded; specification revised to revision
       2 and converted to valid RST.
   * - 2026-08-28
     - --
     - Phases 00-16 defined with exit gates; ``ONBOARDING.rst``,
       ``STATUS.rst`` and ``DECISIONS.rst`` created. Project not yet started.
   * - 2026-08-29
     - --
     - Owner ruled out building Percolator from source and required published
       binaries on macOS, Windows and Linux. Verified that **3.07.1**
       (``rel-3-07-01``) is the newest release meeting that with XML: Linux
       build executed here, macOS payload extracted, Windows inferred and
       flagged for Phase 00. ``D-002`` and ``D-004`` decided; ``D-003``
       narrowed. Specification revision 4.
   * - 2026-08-29
     - --
     - Orchestration model made explicit: three tiers (main orchestrator ->
       phase orchestrator -> phase agent), each signing off the tier below by
       running the checks itself. Added ``handoffs/WORKLOG-TEMPLATE.rst``.
   * - 2026-08-29
     - --
     - **Owner decided the licensing question.** ``D-001`` DECIDED: derive from
       CasanovoGUI and release CometGUI under **GPL-3.0**, accepting the
       copyleft commitment; ``R-SEC-01``'s no-copying constraint is lifted and
       Phase 02 may reuse CasanovoGUI source with derivation notices. ``D-008``
       part decided: the licence is GPL-3.0, unblocking Phase 01's ``LICENSE``
       file; **where CometGUI is published, and whether tool binaries are
       redistributed, remain open -- there is still no remote and none may be
       created.** The owner also directed that **PDV be treated as GPL-3.0**,
       resolving its upstream ``LICENSE``/``pom.xml`` contradiction
       conservatively for now; Phase 16 must still get the real answer from
       upstream. Compatibility consequence recorded: Apache-2.0 (Comet,
       Percolator, Limelight converter) is one-way compatible into GPL-3.0, and
       the bundled Liberica JRE is GPLv2 **with the Classpath Exception**, so
       the dependency set raises no conflict. Specification amended to revision
       6.
   * - 2026-08-29
     - 00
     - **Phase 00 run and signed off PARTIAL.** Three tiers used as designed:
       one phase orchestrator, ten work units, each unit signed off by the tier
       above. The main orchestrator re-ran all ten gate items itself; 9 PASS,
       item 8 passes only on its second branch because no Windows runner
       exists. Proven by execution: the full Comet -> Percolator -> Limelight
       path (12.8 MB schema-valid Limelight XML, validated with an independent
       validator shown to be falsifiable), Percolator 3.09 rescoring with zero
       XML, a launchable ``jpackage`` bundle with no JDK on PATH, and TestFX
       headless with working negative controls. Discovered by execution: the
       ``noxml`` builds emit pout XML, which invalidates the help-text
       capability probe and may remove the need for installer payload
       extraction. Caught at sign-off: CasanovoGUI published GPL-3.0
       mid-phase, reframing ``D-001``. Specification amended to revision 5;
       ``D-001``, ``D-002``, ``D-003``, ``D-005``, ``D-006``, ``D-007`` and
       ``D-008`` updated with evidence and costed options. No ``D-`` item was
       answered by an agent.
   * - 2026-08-29
     - --
     - Owner directed that the product use the **latest compatible**
       Percolator rather than a pinned 3.08. Verified that this resolves to
       3.08.1 (``rel-3-08-01``), which has no published binary on any platform;
       that XML is an opt-in compile flag removed in 3.09; that the Limelight
       converter has no non-XML path; and that Bioconda cannot supply an
       XML-capable build. Specification revision 3 replaces pinned defaults
       with computed resolution (``R-PERC-02``, ``R-PERC-10``) and records the
       converter's verified interface (``R-LL-05``). ``D-002``'s recommendation
       changes to building from source.
