=====================================================
PHASE-13 handoff -- Provenance UI and Reports
=====================================================

:Phase: 13
:Written: 2026-10-16 by the Phase-13 orchestrator (tier 2)
:Outcome: **All eight work units accepted** (1-7 and 3b; five sent back once
   each at sign-off, three stopped before code for a design ruling). Every
   exit-gate item is met **on Linux x86-64**, the only platform any of it has
   executed on. Recommended grade **PARTIAL, on platform residue** (the
   Windows half of P13-1 and the macOS/Windows argv of Open file location
   have never run). **Not yet signed off by tier 1**; nobody in this phase ran
   ``scripts/build.sh`` or the full ``scripts/verify-all-gates.sh``.
:Records: ``handoffs/PHASE-13-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off there names the commands run, the
   counts seen and the production-code injections made, with their failure
   text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

A scientist can now open the **Provenance** section, pick any run of the
project -- completed, failed, cancelled, running, interrupted, unsealed or
unreadable -- and read its record in seven parts (summary, tools, inputs and
outputs, parameters with origins and presets, timeline, logs, warnings); copy
any MD5, SHA-256 or command; show any file in the file manager; export the
record as JSON and as the RST report; and verify every listed file against the
disk. Before any of that, the Windows ``ATOMIC_MOVE`` question was settled by
design (P13-1): the viewer never holds the record open, and finalisation
retries a refused rename briefly and boundedly in code that runs identically
everywhere. The gate is proved on **real** runs of the pinned fixture with
tools installed by the product's own installer, against the files on disk,
with the test's own hasher and its own JSON and RST readers.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 26 68

   * - Unit
     - Commits
     - What
   * - 1
     - ``eb256cb`` ``97e444d``
     - ``provenance.io``: bounded retry of a refused rename in
       ``AtomicDocumentWriter`` (10 attempts, 2130 ms, only
       ``AccessDeniedException`` or an exact ``FileSystemException``), the
       ``RetryWaiter`` seam; ``ReadersHoldNoDescriptorTest`` (``/proc/self/fd``).
   * - 2
     - ``fdb1cfc``
     - Parameter origins and applied presets recorded as settings
       (``comet.origin.<word>``, ``comet.preset.<nn>.*``), names in values
       (P13-4 amended); ``PresetApplication``; plumbing UI -> app -> workflow.
   * - 3
     - ``c330b0a`` ``b957f4a``
     - ``org.cometgui.workflow.provenance``: ``ProvenanceReader`` (list,
       load), ``LoadedRun`` (exports, verify), ``ProvenanceView`` and its
       builders, ``DiskVerifier``, ``DisplayText``.
   * - 3b
     - ``8007dc0`` ``8140fd0``
     - ``R-TOOL-07`` manifest/probe discrepancies and Comet advisories
       recorded as tool warnings (``ToolOffer.unconfirmedClaims``,
       ``ToolWarnings``); ``WarningSource.MANIFEST_PROBE_DISCREPANCY``.
   * - 4
     - ``0821f8e`` ``aac5827`` ``2f97f34`` ``ae4b06f``
     - ``cometgui-app`` ``RealProvenanceGateTest`` (17 tests, ~26 s) over
       ``GateWorld``; Comet's capabilities, both tools' artefact identity and
       release tags recorded (``ToolOffer.artefactIdentity``/``releaseTag``).
   * - 5
     - ``842a654`` ``efa58d5`` ``1364904`` ``278fa4c``
     - ``ui.viewmodel.provenance``, ``ui.controls.provenance``;
       ``ProjectProvenancePort``, ``FileManagerLauncher``,
       ``RunWiring.provenance``; 35 ``UiIds``; GUI tests.
   * - 6
     - ``fcb499e``
     - ``docs/provenance.rst``, ``docs/developer/provenance_ui.rst``, the
       schema page's read-model section, ``testing.rst``, traceability for
       ``AC-PRV-01..09`` (46 automated / 7 partial / 19 planned).
   * - 7
     - ``04742c9``
     - ``scripts/verify-provenance-ui-gates.sh``, registered as
       ``provenanceui`` (floor 96, about 21 minutes).

Orchestrator commits: the work log and one sign-off commit per unit. **Changes
to earlier phases' code, each reviewed at sign-off and reported here:** Phase
04 (``AtomicDocumentWriter`` retry); Phase 05 (``ToolOffer`` gains
``unconfirmedClaims``, ``artefactIdentity``, ``releaseTag``;
``ManagedToolManager`` fills them; ``InstallationMarker.artefactIdentity()``);
Phase 07 (``ParameterSession`` remembers applied presets;
``RunEnginePort.start`` carries them; ``FileChooserPort.chooseExportTarget``);
Phase 08/09 (``SearchRequest.withPresets``, ``CometSelection`` gains
advisories, claims, capabilities, identity and tag; ``CometWorkflow`` records
them -- **replacing the old ``"v" + release`` release-tag guess**, which also
tagged registered local binaries; Percolator's record gains identity, tag and
discrepancies); ``RunResultFiles`` two role constants; ``ShellView``,
``SectionArrivals``. **No POM changed, no PIT configuration changed, no
module edge added, no floor lowered** (accessible-name floor raised 174 ->
209). **The provenance schema is unchanged**: every new fact is a settings key
or an existing member.

Exit gate
=========

All evidence Linux x86-64. Every check below was run by me; the work log has
each injection's failure text.

.. list-table::
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux)
     - ``RealProvenanceGateTest`` gate 1: a real two-file K562 Comet +
       Percolator 3.07.1 run through the product's run port; all 28 listed
       files and 6 logs recomputed with the test's own ``MessageDigest`` loop
       equal the record, and all 10 files under ``outputs/`` at seal are
       listed. Red: my M3 (database recorded as an input nowhere);
       ``provenanceui`` 1a, 1b (the product's hasher wrong in the MD5 alone --
       the gate's digests are its own).
   * - 2
     - Met (Linux)
     - Gate 2, one test per tool (Comet, Percolator, converter): version,
       path inside the cache, both digests against the test's own hash and
       the install marker the test finds itself, capabilities, artefact
       identity and release tag against the marker. Red: unit 4's I6-I8;
       ``provenanceui`` 2a-2c.
   * - 3
     - Met (Linux)
     - Gate 3: one Comet execution per spectrum file with its own ``-N``,
       Percolator's argv names the merged PIN, every displayed command equal
       to the argv element by element; the GUI's Copy command read back from
       the clipboard. Red: ``provenanceui`` 3a.
   * - 4
     - Met (Linux)
     - Gate 4: a real failed run (damaged spectrum file, Comet exit 134) and
       a real cancelled run open through the read model and the section,
       showing status, the failing/cancelled stage and every partial output
       marked ``PARTIAL`` in words. Red: unit 4's I5; my K4;
       ``provenanceui`` 4a, 4b. **Not proved on a real engine:** a run killed
       before sealing (nothing can crash one honestly); the reading of such a
       directory is proved over a constructed one (``ProvenanceStatesTest``).
   * - 5
     - Met
     - Gate 5: both exports come from one loaded ``ProvenanceManifest``
       through Phase 04's two writers, are byte-identical to the run's own
       documents, and every fact the test's **independent** JSON reader finds
       is asserted in its place in the RST by the test's own RST reader. Red:
       my M4 (RST printing a log's MD5 as SHA-256 -- caught while the byte
       comparison correctly stays green); ``provenanceui`` 5a, 5b (an export
       copying bytes).
   * - 6
     - Met (Linux)
     - Gate 6: five seeds through every entry path the product has (Limelight
       key in a refused and a submitted upload to the ``D-007`` fake; a
       password in a Limelight URL; a URL password and an ``api_key=`` in a
       preset name; a URL password in a preset name over 256 characters),
       then every file under the project and both exports swept by Phase 12's
       ``SecretSweep`` (UTF-8, UTF-16, three Base64 alignments, URL-encoded),
       with a control planting every form. ``ProvenanceSessionRedactionTest``
       proves the section and exports redact with the **session's** redactor.
       Red: my M1 (JSON redaction skipped above 200 characters, after the
       rework), N3; ``provenanceui`` 6a-6d. **Two known ways a secret reaches a
       file remain** (see *Escalated*).

**Design obligations.** P13-1: ``provenanceui`` d1-d3 (retry removed, one
attempt fewer, a reader holding a descriptor); P13-7's product half
(``Verify`` comparing SHA-256 only): d4; P13-8 (a port call on the FX thread):
d5.

**Harness runs, by me, on the last commit that changed what each reads**:
``provenanceui`` **96** (1238 s, ``04742c9``), ``provenance`` 24,
``workflow`` 110, ``percolator`` 109, ``install`` 95, ``quality`` 42,
``shell`` 30, ``paramui`` 87, ``docs``, ``traceability`` 8 -- each PASS. The
agents also ran ``results`` 100 and ``pdv`` 107 after unit 5 (``pdv`` passed on
its second run; see *Surprises*). ``--only tests``: see `The deferred tests
harness`_.

.. _p13h-tests:

The deferred tests harness
==========================

Before it, every module's PIT report was regenerated exactly as
``scripts/build.sh`` does (``mvn -B -o -Dcometgui.pit.threads=16 test-compile
org.pitest:pitest-maven:mutationCoverage`` at the root; BUILD SUCCESS,
13:03-14:37 UTC on 2026-10-16), because the units' targeted PIT runs had
overwritten several reports (Phase 08's trap); the stray ``cometgui-ui`` and
``cometgui-app`` reports, which ``build.sh`` never produces, were removed.
Totals per module: domain 707 (703 KILLED), provenance 795 (792), process 185
(168), tools 2169 (2040), install 1418 (1401), params-comet 1783 (1776),
params-percolator 176/176, results 943 (888), workflow 242/242.

``bash scripts/verify-test-gates.sh --preflight``: every precondition holds.
Then ``bash scripts/verify-all-gates.sh --only tests``, detached on
``c77b338`` (code unchanged since ``04742c9``) and watched to its exit line:
**PASS, 37 assertions in 18 644 s (310m44s)**, against 18 961 s at Phase 12.

Tests that read outside their module read only: ``/proc/self/fd`` (units 1,
3); under ``scratch/``: ``fixture/`` (both K562 mzML, the UniProt proteome)
and ``phase05/artefacts/`` (``v2026.03.0__comet.linux.exe``,
``rel-3-07-01__percolator-noxml-ubuntu-portable.zip``,
``rel-3-07-01__percolator-noxml-v3-07-linux-amd64.deb``,
``v2.8.1__cometPercolator2LimelightXML.jar``) -- all already read by earlier
app tests. None reads a project document.

Incomplete, deferred, residue
=============================

* **Platform.** Linux x86-64 only. The P13-1 retry runs identical code
  everywhere and is mutation-tested here, but **unverified on Windows**: that
  a scanner's or sync client's hold arrives as ``AccessDeniedException`` or a
  bare ``FileSystemException``; that holders release within 2130 ms; that a
  JDK reader does not block the replace. The worst case is now a clean
  failure ("could not write provenance.json", ``events.log`` kept), never a
  torn file. Open file location's macOS (``open -R``) and Windows
  (``explorer.exe /select,``) argv have never run.
* A run killed before sealing is proved only over a constructed directory.
* Replace is offered after any refused export, including one aimed at the
  run's own record (pressing it shows the refusal; nothing is written). The
  run list does not refresh on a Limelight compatible rerun ("Read the runs
  again" does).
* ``org.cometgui.workflow.provenance`` and ``ui.viewmodel.provenance`` are not
  POM PIT targets (targeted PIT: 231/234 with 3 timeouts; 312/312) -- the
  owner's open build-time question, like ``workflow.pdv``/``.limelight``.
* The ``provenanceui`` harness costs about 21 minutes (brief asked "well
  under 20"); the real-run world is rebuilt per nested class.
* Stale, wider than this phase: ``docs/index.rst``'s "every page is a stub"
  warning, ``testing.rst``'s Phase 01-era tables and stale counts, the
  2026-08-29 header of ``traceability-map.toml``.
* Known residue not ours: ``EngineStep``'s ``LAUNCH_PDV``,
  ``CONVERT_LIMELIGHT``, ``UPLOAD_LIMELIGHT`` declared and unused.

Decisions encountered
=====================

No ``D-`` item was hit or answered. Design decisions P13-1..P13-10 are in the
work log. Those tier 1 should confirm:

* **P13-1** -- the Windows question settled by a bounded retry (a change to
  Phase 04's code) plus "the viewer is never a holder". Judged not to need the
  owner: it narrows a failure mode without changing any guarantee.
* **P13-4 amended** -- 39 Comet parameter names contain upper-case letters,
  so parameter names live in settings **values**, not keys.
* **P13-9** -- a manifest/probe discrepancy is one direction only (claimed,
  not found).
* **P13-10** -- the real-run gate lives in ``cometgui-app``, and records
  Comet's capabilities and both tools' artefact identity and release tag.

Surprises
=========

* **``R-TOOL-07``'s discrepancy warning was written by nothing**, and Comet's
  tool record carried no advisories, no capabilities, no artefact identity and
  a guessed release tag -- found by building the read model, repaired in 3b
  and 4.
* **39 of 118 Comet parameter names contain upper-case letters**
  (``add_C_cysteine``, ``use_A_ions`` ...), so no settings key can name one.
* **Surefire writes every ``@Nested`` class's report under the outer class's
  name**, so a selection naming several nested classes keeps only the last
  one's results, and two tests with one method name in different nested
  classes under-count. The ``provenanceui`` harness guards against it; older
  harnesses selecting several nested classes of one class may read
  overwritten reports (Phase 12's ``CompatibilityActionTest$Rerun``/``$NoRerun``).
* ``pdv`` control 5b failed once after unit 5 because the real-PDV observer
  logged a partial ``row=0`` line first and the expected-text regex missed;
  it passed alone and on a full rerun. A flake in Phase 11's harness.
* Five units' first drafts passed every check the agent ran and still had a
  gap one production injection exposed (SpotBugs in tests; MD5-only
  verification; one unconfirmed claim only; short seed carriers only; the
  session redactor's wiring). All repaired with tests.

Escalated (none answered here)
==============================

#. **A secret typed as a Comet parameter value** is written verbatim into the
   archived ``comet.params``, which ``AC-PRV-04`` requires to be the exact
   executed file -- a genuine tension with ``R-SEC-03``. Recommendation: keep
   ``AC-PRV-04`` and document it (done on the user page); the alternative,
   redacting the archived file, makes it no longer the executed file.
#. **The Limelight server's wrong-key refusal quotes the key's first two
   characters**, which reaches the upload log and ``events.log`` (Phase 12's
   P12-20 passes the server's words through). Recommendation: redact a
   quoted key prefix in that one message.
#. **Confirm P13-1, P13-4 (amended), P13-9, P13-10** and the changes to
   Phases 04, 05, 07, 08 and 09 code listed above.
#. **PIT targets**: ``org.cometgui.workflow.provenance.*`` (a POM change),
   with ``workflow.pdv``/``.limelight``.
#. **The surefire nested-report overwrite** in older harnesses (above).
#. **Cost**: ``provenanceui`` adds about 21 minutes to the full gate run; the
   phase's tests add about 1.5 minutes of real runs to the app module
   (``RealProvenanceGateTest`` ~26 s, ``ProvenanceSectionUiTest`` ~45 s).

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts`` and ``scratch/fixture``
are present and the host has ``gcc`` and ``/usr/bin/timeout``, then run
``bash scripts/build.sh`` and the full ``bash scripts/verify-all-gates.sh``
(now 20 harnesses with ``provenanceui``, floor 96), and decide the
escalations above.
