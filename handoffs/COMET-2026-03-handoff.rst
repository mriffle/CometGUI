=====================================================
COMET-2026-03 handoff -- Comet 2026.03.0 intake
=====================================================

:Work package: Comet 2026.03.0 intake (``handoffs/COMET-2026-03-BRIEF.rst``)
:Written: 2026-10-04 by the work-package orchestrator (tier 2)
:Outcome: **All six units accepted** (unit 4 after one rework round). Exit-gate
   items 2-8 **met**; item 1 **partial**: met for four of the five platforms
   the manifest covered, and **cannot** be met for the fifth because upstream
   publishes no x86-64 macOS Comet (see :ref:`c2603h-gate`). Not yet signed off
   by tier 1, whose ``scripts/build.sh`` and full
   ``scripts/verify-all-gates.sh`` run has not happened; nobody in this package
   ran either.
:Records: ``handoffs/COMET-2026-03-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off there names the commands run, the
   numbers seen and the injections made, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

What was built
==============

.. list-table::
   :header-rows: 1
   :widths: 8 30 62

   * - Unit
     - Commits
     - What
   * - 1
     - ``72f0ed5`` ``65e97ce`` ``88ed18b`` ``f375c87``
     - Real 2026.03.0 ``-q``/``-p`` fixtures; a 2026.03.0 version record;
       per-release ``overrides`` (default, choices, inline comment, help, help
       reference) as data; drift per version over fixtures and manifest.
   * - 2
     - ``bc10bb9`` ``149bc5c`` ``667c080``
     - ``manifests/tools.json`` rows for 2026.03.0 (linux x86-64, linux
       aarch64, macos aarch64, windows x86-64 with companions); default by
       newest-first; real upstream install; install harness 88 -> 95.
   * - 3
     - ``6159488`` ``6aadcf1``
     - Residue alphabet per release (``^``/``$`` for 2026.03.0 only); Phase 06
       gate items 1, 3, 4, 5, 6 for 2026.03.0; the real 2026.03.0 binary reads
       the canonical file.
   * - 4
     - ``c1b89f7`` ``f08ff50`` ``543fb08`` ``5a25dc1``
     - Per-release ``ruleSeverities``; new rules (residue not in release,
       AScorePro with slots 10-15, ``index_search_type`` without ``.idx``); a
       42-case corpus judged by both real binaries.
   * - 5
     - ``7f9e1b1`` ``92f3ea6`` ``876dc91``
     - Per-release ``valueMigrations``; real migrations of 2026.02.2 and
       2024.01.0 to 2026.03.0; migrated files accepted by the real binary.
   * - 6
     - ``5a2dffb`` ``0ba27b4`` ``f3b3973``
     - Fifteen version-blind controls and H8-H10 in
       ``scripts/verify-param-gates.sh``; params floor 68 -> 109.

Orchestrator commits: ``38b6216`` (work log), ``d5ed3e4`` and ``58d32e3``
(unit 1 sign-off; the second repairs a strict-build failure the first
introduced in the work log), ``58ceddc``, ``8a4b937``, ``b56dbd4``,
``4df3dc0``, ``fbe67e7`` (sign-offs), and this handoff.

The single design rule (work log C-2): **every 2026.03.0 difference is data in
that release's version record** in
``cometgui-params-comet/src/main/resources/org/cometgui/params/comet/schema/comet-parameters.json``
(``overrides``, ``variableModTuple.residueAlphabet``, ``ruleSeverities``,
``valueMigrations``), loaded and refused-when-malformed by ``MetadataLoader``
and by ``scripts/cometparams.py``. There is no ``if (version ...)`` in a codec,
rule or migration.

.. _c2603h-gate:

Exit gate
=========

.. list-table::
   :header-rows: 1
   :widths: 5 12 83

   * - #
     - Verdict
     - Evidence (my own runs, recorded in the work log)
   * - 1
     - **Partial**
     - Rows for linux x86-64, linux aarch64, macos aarch64 and windows x86-64,
       each size/SHA-256/MD5 equal to my own independent download; 2026.03.0
       the default, 2026.02.2 still offered. Real install from the upstream
       URL (``-Dcometgui.install.upstream=true``): SHA-256 ``ad93b4cf...``,
       7 077 008 B, probe identity ``2026.03.0`` through the real
       ``ProcessService``. Corrupted 2026.03.0 pin refused before anything
       runs. ``--only install``: 95 controls (floor 95, was 88), re-run on the
       final tree. **Residue:** no macos x86-64 row -- ``comet.macos.exe`` is
       ARM64 (Mach-O CPU type ``0x0100000C``) in 2026.03.0 *and* 2026.02.2, so
       upstream publishes no x86-64 macOS Comet; macOS and Windows rows are
       unexecuted.
   * - 2
     - Met
     - Fixtures captured from the binary, byte-equal to my independent capture,
       ``SHA256SUMS`` verify; recipe in
       ``docs/developer/comet_parameter_schema.rst``; the manifest-driven
       real-binary test re-proves them; 2026.02.2 and 2024.01.0 fixtures
       unchanged.
   * - 3
     - Met
     - 2026.03.0 ``-q`` 118 declared / 118 modelled / 0 allow-listed; ``-p``
       95, ``PARTIAL_DISCOVERY``. Drift goes red on an entry removed and on a
       parameter claimed for a release whose ``-q`` lacks it (params controls
       ``v3b``, ``v3c``).
   * - 4
     - Met
     - 2026.03.0 canonical round trip 10 725 B, SHA-256 ``3aecc834...`` at unit
       3 (re-pinned at unit 4 when 2026.03.0's own ``output_txtfile`` comment
       was added; ``ReleaseWriterGateTest``); 26 forms x 15 slots for
       2026.03.0, ``^``/``$`` forms refused in every slot of 2026.02.2 and
       2024.01.0; enzyme table, comma locale, unknown parameters; the real
       binary reads the canonical file.
   * - 5
     - Met
     - 42-case corpus, both binaries, commands and output in
       ``fixtures/comet-validation/corpus.json`` and the developer page;
       validator agrees on every case under a stated criterion; cases the
       binary cannot settle are listed (existing ``.idx``, index-build count
       cap, ``-i``/``-j``, Windows/macOS, real-time).
   * - 6
     - Met
     - 2026.02.2 ``-q``/``-p``: CONVERTED 1 (``index_search_type = 1`` ->
       ``-1``, reason in the report), CARRIED 117; 2024.01.0: ADDED 9, CARRIED
       109. My own run: the real 2026.03.0 binary searches all four migrated
       files, rc 0, no Warning or Error.
   * - 7
     - Met
     - Generated reference covers 2026.02.2 and 2026.03.0 (count line ``118
       parameter entries = 118 modelled parameters, 0 internal, for Comet
       2026.02.2, 2026.03.0``); ``--only docs --only traceability --only
       params`` pass on the final tree.
   * - 8
     - Met
     - PIT over the 22 production classes changed since ``d19b232``, no test
       excluded: 700/701; the survivor ``VariableModRules:204`` is Phase 06's
       documented equivalent. Control 9: parser 96/101, writer 30/30,
       validation 251/252, module 1360/1367. No survivor makes a
       version-scoped rule version-blind.

Final harness runs on ``fbe67e7``: ``--only params`` 109 controls (446 s);
``--only install`` 95 controls (259 s); ``--only docs --only traceability`` 2
passed; ``--only tests``: see :ref:`c2603h-tests`.

.. _c2603h-tests:

The tests harness
-----------------

``scripts/verify-test-gates.sh``'s precondition changed twice (units 1 and 4:
it now also requires ``scratch/phase06/artefacts`` and ``scratch/fixture``).
``--only tests`` costs about 50 minutes and builds every module, so it was run
**once**, on the final tree, rather than per unit.

* **First run, at ``fbe67e7``: FAIL** -- ``FATAL: 6 test-gate control(s)
  failed`` in 1057 s. Every sandbox build died in
  ``ValidationCorpusTest.everyCaseIsDocumented`` (unit 4), which reads
  ``docs/developer/comet_parameter_schema.rst``; the harness sandbox carries
  only the documents tests read, by name, and did not carry that one. Controls
  5, 7, 8 and H were red for that reason, not their own.
* **Repair** (mine, additive, following the harness's own written rule that
  "project documents that tests assert against are inputs"): the sandbox now
  copies that one page. No control, floor or diagnostic changed; no other test
  reads anything under ``docs/`` (searched).
* **Second run: PASS** -- ``tests: 37 assertions in 3052s`` (floor 37).

Also by the build-economy rule: unit sign-offs never ran ``--only tests``, so
this defect surfaced only here; tier 1's full run would have hit it too.

Proposed specification text (tier 1 amends by revision)
=======================================================

All established by executing the 2026.03.0 binary (``sha256 ad93b4cf...``) on
2026-10-04.

* **Upstream facts, Comet parameter dump.** "``-p`` emits 95 parameters; ``-q``
  emits 118 (2026.03.0). ``-q``'s names are the same 118 as 2026.02.2's. ``-p``
  omits the 22 that 2026.02.2's ``-p`` omits **and** ``index_search_type``,
  which 2026.03.0's ``-p`` no longer writes. ``-q`` writes
  ``index_search_type = -1`` (not set), where 2026.02.2 wrote ``1``."
* **R-PARAM-01.** "The verified difference is 118 parameters versus 95 for
  2026.03.0 (96 for 2026.02.2): ``-p`` omits ``variable_mod06``--``15``, both
  ``mass_type_*`` parameters, ``num_results``, the PEFF parameters, the
  spectral-library parameters, ``pinfile_protein_delimiter``,
  ``print_expect_score``, ``print_ascorepro_score``, ``compoundmods_file``,
  ``protein_modslist_file`` and, from 2026.03.0, ``index_search_type``."
* **Variable-modification tuple.** "In 2026.03.0 the residue field also
  accepts ``^`` (protein N-terminus only) and ``$`` (protein C-terminus only).
  2026.02.2 accepts them silently and they never apply; CometGUI refuses them
  for that release."
* **Comet artefacts.** "``comet.macos.exe`` and ``comet.aarch64.macos.exe`` are
  both ARM64 Mach-O executables (2026.02.2 and 2026.03.0); upstream publishes
  no x86-64 macOS Comet." This contradicts the specification's artefact list,
  ``D-004``'s premise and the existing 2026.02.2 ``macos``/``x86-64`` manifest
  row -- see *Escalated*.
* **Comet validation.** "In 2026.03.0, an undefined ``search_enzyme_number``,
  ``search_enzyme2_number`` or ``sample_enzyme_number`` stops Comet at
  parameter load; a terminal distance below -2, a terminus outside 0-3 with a
  distance, and ``print_ascorepro_score`` non-zero with an active, unmerged
  ``variable_mod10``--``15`` stop it when the search starts. 2026.02.2 crashes
  (signal 11) on the last."
* **``.idx`` format v5.** "2026.03.0 rejects an index whose first line does not
  begin ``Comet index database v5``" -- input to the unowned "selected index is
  compatible" check; nothing was built for it.

Workflow files that name a Comet version
========================================

Only ``.github/workflows/macos-gatekeeper.yml`` (a comment, line 38, "Comet
2026.02.2 publishes a native aarch64 macOS build"). Its driver selects the
binary through ``ArtefactManifest.select``, so it now picks 2026.03.0
``comet.aarch64.macos.exe``. ``scripts/ci/macos-gatekeeper-verify.sh`` holds
2026.02.2 banner strings only as self-test inputs for banner classification,
which is version-independent. ``nightly.yml``, ``pull-request.yml``,
``release.yml`` and ``windows-percolator.yml`` name none.

Escalated (none answered here)
==============================

#. **No x86-64 macOS Comet exists upstream; the 2026.02.2 ``macos``/``x86-64``
   row describes an ARM64 binary.** Left unchanged. Touches ``D-004`` and the
   platform promise; an Intel Mac is now offered 2026.02.2 only, from a row
   whose architecture is wrong.
#. **``R-TOOL-02`` names ``CometWrapper.dll`` as a required companion, but
   ``comet.win64.exe`` references neither wrapper** (import table, .NET
   references, ``Comet.vcxproj``). Kept as required (it can only withhold
   ``THERMO_RAW_WINDOWS``, never claim it falsely); a specification question.
   ``CometWrapperCore.dll`` and ``Ijwhost.dll`` are not companions.
#. **The 2026.02.2 Windows row declares no Visual C++ runtime**, though its
   binary imports the same four DLLs the 2026.03.0 row now declares.
#. **AScorePro with slots 10-15 is an error for 2026.02.2 too** -- beyond the
   brief, on evidence: 2026.02.2 segfaults (rc 139), reproduced by me.
#. **Migration judgement calls** (unit 5 residue): a distance below -2 is
   converted to -1 although an exclusive-modification edge case differs
   (measured 7265 vs 7263 result lines); NEEDS_ATTENTION substitutes the
   slot's default, which for ``variable_mod01`` is an active methionine
   oxidation -- Phase 07/08 should make an unresolved NEEDS_ATTENTION block a
   run.

Corrections to the record
=========================

* The brief's "``phases/PHASE-06-comet-param-model.rst`` says 'eleven other
  parameters'" is stale: since ``2decaa9`` it says "twelve", which is right.
* My own starting probe of AScorePro was wrong (a duplicate slot that Comet
  merged); corrected in the work log the same day.
* Unit 4's first report claimed ``--only params`` green; my run showed control
  9 red (TIMED_OUT under PIT). Reworked at the root; see the work log.

Not done, by instruction
========================

``scripts/build.sh`` and the full ``scripts/verify-all-gates.sh`` (tier 1's,
at this exit gate); any macOS or Windows execution; ``.github/workflows/*``;
``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``specification.rst``.
``dev-verify.sh --mutation`` was never used.

First thing the next agent should do
====================================

**Tier 1:** on a quiet tree with ``scratch/phase05/artefacts`` (now also
holding ``v2026.03.0__*``), ``scratch/phase06/artefacts`` and
``scratch/fixture`` present, run ``bash scripts/build.sh`` and then ``bash
scripts/verify-all-gates.sh`` in full. ``params`` now costs about 7.5 minutes
(109 controls) and ``install`` about 4.5 (95). Then decide the escalations
above, and amend the specification from the proposed text.

**Phase 07:** bind the editor to the release's own definitions
(``CuratedMetadata.parameter(name, version)``), its residue alphabet and its
rule severities; show ``MigrationReport`` entries (``CONVERTED``, ``NOTED``,
``NEEDS_ATTENTION``) as a reviewable diff before a migrated set is used.
