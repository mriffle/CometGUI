=====================================================
PHASE-12 handoff -- Limelight Conversion and Upload
=====================================================

:Phase: 12
:Written: 2026-10-12 by the Phase-12 orchestrator (tier 2)
:Outcome: **All nine work units accepted** (one sent back once -- unit 2, for a
   second Percolator XML reader; one orchestrator repair). Every exit-gate item
   is met **on Linux x86-64**, the only platform any of it has executed on.
   Recommended grade **PARTIAL, on platform residue** (macOS and Windows never
   ran; the Linux keychain path was proved only against a scripted stand-in).
   **Not yet signed off by tier 1**, whose ``scripts/build.sh`` and full
   ``scripts/verify-all-gates.sh`` run has not happened; nobody in this phase
   ran either, by the owner's build-economy rule.
:Records: ``handoffs/PHASE-12-worklog.rst`` -- **this page is the map; the work
   log is the proof.** Every sign-off there names the commands run, the counts
   seen and the production-code injections made, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

A scientist can now send a finished run to Limelight. The Limelight section
reads the chosen run's **own** Percolator record first: a run whose Percolator
wrote no XML has every control disabled with the reason, and is offered the
action Phase 09's resolution computes -- "Rerun Percolator with 3.07.1 for
Limelight" (the label is the resolved version, never a literal), which runs
Phase 09's one compatible rerun from the merged PIN without Comet, or, where
no XML-capable build can be obtained, registering a local binary. A convertible
run is converted by the pinned converter (installed on first use through the
Tool Manager) with an explicit ``-q`` from its own Limelight q cutoff (0.01,
independent of the display filters), and the result is judged by the file --
exists, non-empty, well-formed, ``limelight_input``, valid against the XSD
inside the very JAR that ran -- never by the exit status. Decoy import is
refused at configuration time unless the run's pout XML holds decoy PSMs
(never, for a CometGUI run today). Upload is an in-product client of
limelight-core's submit-import protocol: Upload shows the exact destination
and sends nothing; only Send sends; the key is admitted to the redactor on
entry, travels only in the protocol's request bodies, and was found in no
file the application wrote. Tests reach only a loopback fake that validates
what it receives against the converter's XSD, behind a JVM-wide sinkhole
guard. New harness ``scripts/verify-limelight-gates.sh`` (``limelight``, 130
controls, about 19 minutes).

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 26 68

   * - Unit
     - Commits
     - What
   * - 1
     - ``e13b46d``
     - The spike (``docs/developer/limelight_upload_spike.rst``,
       ``scripts/spikes/limelight_upload_spike.py`` and its Java): the
       submit-import protocol from limelight-core v8.3.1 source, the importer
       JAR pinned under ``scratch/``, a loopback fake exercised by the real
       importer, no analytics in either JAR. Decisions P12-14..P12-17.
   * - 2
     - ``79e2aa7`` ``30b8439``
     - ``tools.limelight``: typed request and argv (``LimelightConverterCommand``),
       ``LimelightQValueCutoff``, ``LimelightConverter`` (staging file, judge,
       atomic move), ``LimelightSchema``, ``LimelightXmlJudge``,
       ``PepXmlSet``, ``PercolatorXmlPsms`` (a view over the one pout reader,
       ``tools.percolator.PoutDocument``, which gained PSM ids and the decoy
       count -- the rework).
   * - 3
     - ``0e38c4f``
     - ``workflow.limelight``: ``LimelightConversions`` (the one conversion
       path, ArchUnit-enforced), ``ConverterLaunch``, ``PoutForConverter``
       (P12-18), ``LimelightProvisioning``; ``workflow.storage.LimelightSettings*``;
       ``RunLayout`` paths; ``limelight.converting/converted/unconverted``.
   * - 4
     - ``879efc0``
     - ``params.percolator.resolution.CompatibilityAction``;
       ``LimelightCompatibility``; ``PercolatorRerun.compatibleChoice``.
   * - 5
     - ``41edbf4`` ``76dcfdd`` ``692b67b``
     - ``tools.limelight.LimelightUploadClient`` (+ ``LimelightServer``,
       ``SubmitImportWire``); ``tools.credentials``; ``domain.secrets``
       ``Credential``, ``CredentialStore``, ``SessionCredentialStore``,
       ``SessionSecrets``; ``workflow.limelight.LimelightUploads`` with a
       single-use confirmation; ``limelight.uploading/uploaded/unuploaded``;
       ``ProcessRunner.startWithInput``; the fake, the loopback guard and the
       drift test.
   * - 6
     - ``cfd605a`` ``8958735``
     - The Limelight section, conversion half (``LimelightViewModel``,
       ``LimelightPane``, ``ProjectLimelightPort``, ``ProjectRuns``,
       ``WorkflowRunPort.rerunFrom``, ``SessionEngine.rerunCompatible``).
   * - 7
     - ``23713c9`` ``81fd7c6``
     - The upload half (``LimelightUploadViewModel``, ``LimelightUploadPane``,
       ``ProjectLimelightUploadPort``); the fake and guard copied into
       ``cometgui-ui`` and ``cometgui-app``.
   * - 8
     - ``51d1ba7``
     - ``docs/limelight.rst``, ``docs/developer/limelight_integration.rst``,
       stale pages, ``citations.rst``, traceability for ``AC-LL-01..06``.
   * - 9
     - ``35413e5`` ``c203451``
     - ``scripts/verify-limelight-gates.sh``, registered as ``limelight``
       (floor 130); ``docs/developer/testing.rst``.

Orchestrator commits: the work log (``2736c7b`` and a sign-off commit per
unit), and one repair, ``48adabc``: the guard copied into ``cometgui-app``
would have stopped the opt-in ``UpstreamInstallUiTest``; that test now lets
``github.com|*.githubusercontent.com`` through, only when opted in, and
restores the guard afterwards.

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-domain`` (``RunLayout`` paths, ``domain.secrets`` credential types,
``ProcessRunner.startWithInput`` default method); ``cometgui-process``
(``ProcessService`` feeds standard input and absorbs a broken pipe);
``cometgui-provenance`` (six event types, additively);
``cometgui-params-percolator`` (``CompatibilityAction``, words in
``ResolutionMessages``, ``DownstreamStage.purpose()``); ``tools.percolator.PoutDocument``
(more facts from the same walk; the probe refuses nothing new);
``workflow.steps`` (``PercolatorRerun.compatibleChoice``,
``RunResultFiles.makeReadOnly``); ``cometgui-ui`` (``UiIds`` 514 -> 575,
``ShellView``, ``SectionArrivals``); ``cometgui-app`` (composition,
``ProjectRuns`` moved out of ``ProjectVisualisationPort``,
``WorkflowRunPort``, ``SessionEngine``); ``cometgui-archtests`` (conversion
path, upload path and no-proxy rules); test resources
``junit-platform.properties`` and the extension registration in four modules;
``scripts/verify-all-gates.sh`` (additive). **No POM changed; no PIT
configuration changed; no floor lowered** (the accessible-name walk's floor
rose 117 -> 174); no test removed or disabled.

Exit gate
=========

Every check below was run by me; the work log has each injection's failure
text. All evidence is Linux x86-64.

.. list-table::
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux)
     - ``RealLimelightConversionTest.gate1ARealConversion`` (real Comet
       2026.03.0, Percolator 3.07.1, converter 2.8.1 through the engine):
       valid against the XSD the test reads from the JAR, the test's own PSM
       count equal to the run's target rows, the event's MD5 and SHA-256 equal
       to the test's own; ``LimelightConversionUiTest.gate1`` the same through
       the interface from an empty tool cache. Checksums are recorded in the
       run's ``events.log`` (``limelight.converted``), per tier 1's Phase 10
       ruling that ``provenance.json`` is sealed (see `Decisions encountered`_).
       Red: ``limelight`` 1a-1c; my K1.
   * - 2
     - Met
     - ``LimelightConverterTest.exitZeroWithNoFile`` / ``exitZeroWithAnEmptyFile``
       (and through the workflow: one ``converting`` and one ``unconverted``
       event, nothing at the final path); the real converter's exit 0 with no
       arguments and with an unknown option, writing nothing, judged a
       failure. Red: ``limelight`` 2a-2c.
   * - 3
     - Met (Linux)
     - ``LimelightRerunUiTest.gate3`` (a 3.09 run: explanation shown and every
       control disabled before anything is attempted; the action text equals
       ``CompatibilityAction.forStage(...).label()``; no converter launched)
       and ``.noManagedBuild`` (no XML-capable build obtainable: registration
       offered, "no managed build exists for this platform", no rerun);
       ``LimelightCompatibilityTest``, ``CompatibilityActionTest`` (a
       hypothetical 3.08.2 and a future 3.10 change the label). Red:
       ``limelight`` 3a-3c; my N1, N2, Q1.
   * - 4
     - Met (Linux)
     - ``RealLimelightCompatibleRerunTest`` and ``LimelightRerunUiTest.gate4``:
       3.09 (registered local) run, the offered rerun with 3.07.1 through
       Phase 09's path, Comet launches unchanged, the merged PIN's SHA-256 as
       recorded, the derived run converted (3285 PSMs = its own target rows),
       both runs' ``provenance.json`` showing the two Percolator executions
       with different versions, SHA-256 and argv, the original's bytes
       unchanged. Red: ``limelight`` 4a-4c; my N3.
   * - 5
     - Met
     - ``LimelightConversionUiTest.gate5`` (0.01 shown; 0.05 reaches the next
       file's ``default_filter_value``; the Results filters stay 0.01 with
       unchanged counts, and changing them leaves the cutoff), the workflow's
       whole-run snapshot (changing the cutoff writes only
       ``limelight-settings.json``). ``-q`` sets only Limelight's default PSM
       q-value filter and drops no PSMs (unit 2, by execution). Red:
       ``limelight`` 5a-5c.
   * - 6
     - Met (Linux)
     - ``LimelightUploadUiTest.gate6``: the fake's request count is zero after
       the run, the conversion, Upload (destination shown, SHA-256 checked by
       the test) and Cancel; only Send sends; the fake validates the XML
       against the converter's XSD and the screen and ``limelight.uploaded``
       hold the fake's own tracking id. ``LoopbackGuard`` (JVM-wide sinkhole)
       plus ArchUnit rules against a product class choosing a proxy; the
       guard proved on TEST-NET, ``.invalid`` and the machine's own address.
       Red: ``limelight`` 6a-6f; my P1, P3, R1, R2.
   * - 7
     - Met (Linux)
     - ``LimelightUploadUiTest.gate7`` and ``LimelightUploadLeakTest``: a
       seeded ``CGSEED-`` key used for a refused and two successful uploads
       and two Results exports, then every file walked from the project, the
       tool cache, the test root and everything written under ``/tmp`` and the
       home directory since the start, searched in UTF-8, UTF-16LE/BE, four
       Base64 forms (three alignments) and URL-encoded -- no match; the
       window's text and the console clean; a control plants each form and
       finds it. Red: ``limelight`` 7a-7d.
   * - 8
     - Met
     - ``LimelightConversionsTest.gate8DecoyImportAgainstANoDecoyRun``
       (refused at ``saveSettings``, ``check`` and ``convert``, zero launches),
       ``LimelightConversionRequestTest``, ``LimelightConversionUiTest.aRealRunAndGate8``
       (the control disabled with its reason on a real run, unreachable by
       mouse or keyboard). Agrees with Phase 09: ``-Z`` is never passed, and
       Comet's internal decoys are not FASTA entries. Red: ``limelight``
       8a-8d; my L3, Q2.

**Harness runs on the final tree**, each by me on the last commit that changed
what it reads: ``limelight`` **130** (1130 s); ``quality`` 42, ``shell`` 30,
``results`` 100, ``pdv`` 107, ``percolator`` 109, ``paramui`` 87 (all on
``48adabc``); ``workflow`` 110, ``provenance`` 24 (unit 5); ``docs``,
``traceability`` 8 -- each PASS. ``--only tests``: see `The deferred tests
harness`_.

.. _p12h-tests:

The deferred tests harness
==========================

PENDING -- filled in when the run ends.

Incomplete, deferred, residue
=============================

* **Platform.** Linux x86-64 only. No conversion, rerun or upload ran on
  Windows or macOS. The Secret Service store is proved only against a
  scripted ``secret-tool`` (no keyring on this host); macOS and Windows keep
  the key for the session only (P12-17 named ``security -i`` for macOS; unit
  5 did not implement it, P12-21 records what was built).
* **No real Limelight server was contacted**, by rule (``D-007``). The real
  server validates the XML later, in its background importer; the fake
  validates on receipt.
* **Upload is not cancellable once sent** (``LimelightUploads.upload`` takes
  no cancellation; the screen says so).
* **The rerun uses the Percolator section's current settings**, as Phase
  09's does: nothing reads a run's archived ``percolator-settings.json``. A
  3.07.1 run whose pout XML was deleted still offers the rerun, which Phase 09
  then refuses as already run.
* The upload log's own redaction is a redundant second layer that no test
  can isolate (every line it receives is already redacted); the rest of the
  application redacts with ``SecretRedactor.patternsOnly()`` and does not
  know a typed key (no other writer receives it).
* ``LimelightProvisioning``/``LimelightConverterStatus`` mirror
  ``PdvProvisioning``/``PdvStatus`` (about 330 lines, both over the one Tool
  Manager port).
* Two generation-counter survivors are equivalent today; one (the upload
  view-model's files counter) could apply a stale file list if a run is
  deselected and reselected during one read.
* ``org.cometgui.workflow.limelight``, ``ui.viewmodel.limelight`` and
  ``tools.credentials``-adjacent app code are not POM PIT targets
  (``tools.*`` is); targeted PIT recorded per unit.
* The fake and guard live byte-identically in four modules
  (``FakeCopiesAreIdenticalTest`` grades drift); no test-jar.

Decisions encountered
=====================

No ``D-`` item was answered. Design decisions P12-1..P12-21 are in the work
log. Those tier 1 should confirm:

* **P12-5** -- conversion and upload are user-initiated from the Limelight
  section; ``EngineStep.CONVERT_LIMELIGHT``/``UPLOAD_LIMELIGHT`` stay declared
  and unplanned (like ``LAUNCH_PDV``); checksums are in ``events.log``
  rather than ``provenance.json`` (R-LL-04's and gate item 1's "recorded in
  provenance").
* **P12-14** -- the upload is an in-product client of the submit-import
  protocol, not the importer JAR: the key never reaches an argument array,
  and no managed tool was added.
* **P12-18** -- the converter reads a copy of the pout XML with the run's
  pepXML directory removed from its PSM ids, because CometGUI's absolute
  ``-N`` puts the absolute path in every id and the converter cannot find or
  match the pepXML otherwise. Both files are hashed into the events.
* **P12-19** -- plain http is refused except to this computer.
* **P12-20** -- the local file path is not sent; the server's wrong-key
  message quotes the key's first two characters and is shown as the server's
  words.

Surprises
=========

* **CometGUI's absolute Comet ``-N`` breaks the Limelight converter** (P12-18);
  Phase 00 used relative names and never saw it.
* **The converter's ``-q`` filters nothing** -- it sets Limelight's default
  PSM q-value filter; every output has all PSMs.
* **A run with decoy PSMs must import them**: ``-X -Z`` without
  ``--import-decoys`` fails (Phase 00), so the decoy rule runs both ways.
* **A submit key cannot list projects or see import progress**: the project
  is a typed id and import status lives only in Limelight's web application.
* **The real importer puts the key on its command line** (visible in
  ``/proc`` and in a JFR recording) and sends the user's absolute paths.
* **``--only quality`` does not run ArchUnit**; archtests were run directly
  at each sign-off. Restoring an injected file with ``mv`` keeps the old
  mtime and the incremental compiler keeps the injected class; ``-DskipTests``
  does not install ``cometgui-ui`` when the view-model coverage check fails,
  so an app test then runs the old jar -- hash the jar.
* **Analytics:** none in the converter or the importer (class scan and JFR).

Escalated (none answered here)
==============================

#. **Specification amendments wanted**: "show live upload **and import**
   logs" (import status is unreachable with a submit key); "server and project
   selection" (the project is typed); R-LL-04 / gate item 1 "recorded in
   provenance" (met in ``events.log``); R-SEC-03's keychain preference
   (macOS/Windows session-only); R-LL-01's "unless the PIN fails checksum"
   (Phase 09's rerun refuses instead of rerunning Comet).
#. **Confirm P12-5, P12-14, P12-18, P12-19, P12-20** (above).
#. **``ToolManagerViewModel.refresh()`` starts processes on the JavaFX
   thread** (pre-existing); with the converter installed, every refresh runs
   ``java -jar ... --version`` there.
#. **The opt-in ``UpstreamInstallUiTest``** now passes the guard for GitHub
   hosts only (repair ``48adabc``); it was not run opted in here. The
   nightly should run it.
#. **PIT targets**: add ``org.cometgui.workflow.limelight.*`` (a POM change).
#. **``EngineStep.CONVERT_LIMELIGHT``/``UPLOAD_LIMELIGHT``**: keep declared
   and unplanned, or remove (with ``LAUNCH_PDV``).
#. **Cost**: ``limelight`` adds about 19 minutes to the full gate run; the
   phase's real-binary and GUI tests add a few minutes to ``build.sh``
   outside PIT (see `The deferred tests harness`_ for ``tests``).

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts`` (Comet 2026.03.0,
Percolator 3.07.1, the converter JAR), ``scratch/percolator/3.09``,
``scratch/fixture``, ``scratch/scientific-path`` (``limelight``, ``corrupt``,
``decoy-chain``) and ``scratch/limelight-upload-spike/artefacts`` (the pinned
importer JAR, which ``FakeLimelightServerTest`` uses) are present, that the
host has ``gcc`` and ``/usr/bin/timeout``, then run ``bash scripts/build.sh``
and the full ``bash scripts/verify-all-gates.sh`` (now 19 harnesses with
``limelight``, floor 130). Then decide the escalations above.
