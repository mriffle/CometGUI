=====================================================
PHASE-11 work log -- PDV Integration
=====================================================

:Phase: 11
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``7507c57`` (brief ``handoffs/PHASE-11-BRIEF.rst``)
:Started: 2026-10-10

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``7507c57`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*): tier 1
ran ``scripts/build.sh`` and the full ``scripts/verify-all-gates.sh`` at Phase
10's exit gate on this tree.

Facts established by the orchestrator before design (2026-10-10)
-----------------------------------------------------------------

* ``org.cometgui.tools.pdv`` exists in ``cometgui-tools`` and holds only
  ``PdvJarIdentity`` (PDV's version read from its JAR manifest; PDV has no
  version option and cannot run headless) and ``package-info``.
  ``org.cometgui.tools.*`` is a PIT target in the parent POM, and
  ``cometgui-tools`` has the mutation gate switched on.
* ``cometgui-tools`` depends on ``cometgui-domain`` and ``cometgui-process``
  only. ``cometgui-results`` (``org.cometgui.results.parser`` -- the one
  Percolator table reader, ``SpectrumReference``) depends on
  ``cometgui-domain`` and ``cometgui-provenance``. A ``tools -> results``
  edge would create no cycle; ArchUnit's slice rule
  (``LayeringRulesTest.majorLayersAreFreeOfCycles``) grades it.
* pepXML reading today is validation only: ``tools.comet.CometPepXmlValidator``
  and ``PepXmlSummary``. No class reads ``search_hit`` contents.
* PDV 2.7.0 is in the manifest (``manifests/tools.json``, ``tool: "pdv"``) and
  installs through the Tool Manager (Phase 05 unit 10 proved cancel and
  restart through the UI). ``EngineStep.LAUNCH_PDV`` (``launch-pdv``,
  optional, ``implementedInPhase`` 11, input ``PDV_TOOL``) is declared with
  edges ``finalise-results -> launch-pdv`` and ``launch-pdv ->
  append-downstream-provenance``. ``SectionId.VISUALISATION`` exists with a
  "this section arrives in phase 11" placeholder in ``SectionArrivals``.
  ``docs/pdv.rst`` is a stub that still calls ``D-005`` open.
* ``ProvenanceEventType`` has ``run.started`` .. ``export.written``. Tier 1
  settled at Phase 10 that ``provenance.json`` is the run's sealed record and
  later events go to the run's ``events.log`` (``status-p10-signed``).
* Upstream: ``scratch/casanovogui`` is a clone of ``Noble-Lab/CasanovoGUI`` at
  ``480b3013e7f8fb51a2b8c58681043821e3e7f865`` (2026-08-29). Its
  ``org/casanovo/gui/core/PdvLauncher.java`` (339 lines) and
  ``PdvController.java`` (213 lines) carry **no per-file copyright notice**
  (as ``config/license/java-header-derived.txt`` already records for the
  whole repository). The launcher's download, unzip, GitHub-API and version
  logic duplicates CometGUI's installer; its ``launchDenovo`` uses
  ``ProcessBuilder`` directly. The controller holds the port, readiness,
  debounce and select machinery.
* PDV 2.7.0 unpacked: ``scratch/pdv/PDV-2.7.0/``; Phase 00's spike script
  ``scripts/feasibility/pdv_spike.sh`` starts the project-local Xvfb from
  ``tools/x11-bookworm-20260829/`` and is the worked example of a display.
* **Both fixture mzML files are scan-range subsets**, read by the orchestrator
  from the files themselves: ``scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML``
  801 spectra, ``index`` 0..800 = scans 11187..11987;
  ``..._K562_4.mzML`` 671 spectra, ``index`` 0..670 = scans 9938..10608. In
  each, 1-based file position and scan number differ for every spectrum.
  Phase 00 showed PDV's pepXML+mzML CLI path exits 0 writing nothing on
  ``_3`` (msftbx numbers by position); the MGF route worked.
* Real Comet and Percolator outputs on those files:
  ``scratch/scientific-path/comet/`` (pepXML, PIN per file) and
  ``scratch/scientific-path/percolator-3.07.1/`` and ``-3.09/``. ``D-006``:
  K562 data and anything derived from it stay under ``scratch/`` and are
  never committed.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P11-1 -- one of everything
    One process service: every PDV launch -- CLI figure and ``denovo-gui`` --
    goes through ``org.cometgui.tools.process`` (no ``ProcessBuilder``
    elsewhere; ``ProcessCreationRule``). One installer and manifest: PDV is
    obtained only through the Tool Manager port and ``manifests/tools.json``;
    **none of CasanovoGUI's download, unzip or GitHub-API code is brought
    in**. One results model and selection: Phase 10's ``RowKey`` /
    ``SpectrumReference`` / ``ProjectResultsPort``. One Percolator table
    reader (``results.parser.ResultTableReader``), one hasher
    (``HashService``), one provenance event log, one JSON writer.

P11-2 -- where things live
    ``org.cometgui.tools.pdv``: the CLI figure command, the ``denovo-gui``
    command, the controller and the mzTab exporter. Files derived from
    CasanovoGUI live under ``org.cometgui.tools.pdv.derived`` (a path with a
    ``/derived/`` segment, per ``CONTRIBUTING.rst``) and carry the derived
    header and the per-file derivation record naming the upstream path and
    commit ``480b3013e7f8fb51a2b8c58681043821e3e7f865``. pepXML search-hit
    reading extends ``org.cometgui.tools.comet``. If the exporter needs the
    Percolator reader, ``cometgui-tools`` may gain a dependency on
    ``cometgui-results`` -- recorded at the unit that adds it, ``quality``
    green after it. No other module edge without asking.

P11-3 -- what is derived and what is not
    Derived (``R-PDV-05``): the controller's machinery (instances keyed on the
    mzTab, an ephemeral loopback port, ``/ready`` polling, debounced
    ``/select``, shutdown) and the ``denovo-gui`` argument array. Rewritten on
    the way in, and the derivation record says "modified": process launch
    through the process service, polling on a scheduled executor with a
    deadline (no ``Thread.sleep`` loop), failures surfaced as an actionable
    error rather than swallowed (``R-PDV-04``). Not derived: anything that
    downloads, unpacks or chooses a version.

P11-4 -- provenance
    A PDV launch from a run, and the generated mzTab, are recorded in the
    run's ``events.log`` (tier 1's Phase 10 ruling): new
    ``ProvenanceEventType`` values, additively, carrying for a launch the
    JAR's path, SHA-256 (``HashService``), the version ``PdvJarIdentity``
    reads and the argument array; for an mzTab its path and SHA-256. Every
    launch goes through the one launcher, so "whenever it is launched" is
    structural.

P11-5 -- display and real-binary tests
    PDV needs a display. Tests use the project-local X11 stack under
    ``tools/x11-bookworm-20260829/`` (Xvfb started and stopped by the test
    support, as ``scripts/feasibility/pdv_spike.sh`` does), never the host's.
    Real-PDV tests are ``@EnabledOnOs(LINUX)`` like every other real-binary
    test, and fail loudly -- naming what is missing -- when the X11 stack or
    the PDV artefact is absent; they do not skip.

P11-6 -- PDV exits 0 having written nothing
    Every invocation is judged by its output files, under a timeout. A figure
    is proved by parsing it (PNG signature, IHDR, every chunk's CRC, decoded
    pixels not blank), never by existence or exit status.

P11-7 -- spectrum formats
    PDV 2.7.0 visualises MGF, mzML and mzXML (specification, *PDV*). Any
    other source format produces the specified error **before** PDV is
    launched. Whether subset mzML needs MGF re-encoding on the ``denovo-gui``
    path is the spike's question; on the CLI path Phase 00 already showed it
    does.

P11-8 -- the mzTab is a run artefact
    Written under the run directory (not ``exports/``), recorded with its
    checksum, never presented as a deliverable or an interchange format.
    Values are transcribed as the source's own text where the source is
    text; nothing is recomputed. A field mzTab requires and the source lacks
    is ``null``. Anything that cannot be represented faithfully fails the
    export, naming the PSM and the reason; no partial file is left looking
    complete.

P11-9 -- fidelity is graded independently
    The fidelity suite reads the Percolator tables, the pepXML and the
    spectrum file with **test-side readers** that share no code with the
    exporter, and is written by a different agent from the exporter's. A
    comparison against the exporter's own record is not a test.

P11-10 -- forbidden, always
    Screen-coordinate automation of PDV in production or tests; a PDV fork;
    committing K562 data or anything derived from it (``D-006``). Any
    analytics or telemetry found in PDV or anything it launches is reported
    to the orchestrator **at once** (escalated the same day, ``D-013``
    precedent).

P11-11 -- the spectrum route is MGF (from unit 1)
    PDV receives an MGF re-encoding of each source spectrum file and the
    mzTab carries the standard ``ms_run[n]:index=<0-based MGF ordinal>``.
    The subset-mzML route works only with ``ms_run[n]:scan=<1-based
    position>`` -- a reference that states a false scan number -- and is
    **rejected**. The MGF carries peaks at full, round-trip-exact precision
    (Phase 00's converter rounds; it is not reused) and each entry's
    ``TITLE`` is the mzML native id, so the drawn spectrum names itself.

P11-12 -- every PSM selectable (from unit 1; the orchestrator's call, flagged to tier 1)
    PDV keeps only the first mzTab row per exact ``spectra_ref``
    (``MztabImport.java:425``, ``putIfAbsent``): on ``_3``, 1404 of 2130
    target PSMs share a spectrum with an earlier row and could never be
    selected, which fails ``R-PDV-02`` ("any PSM"). Resolution: the PDV MGF
    holds **one entry per mzTab PSM row**, each an exact copy of the
    producing spectrum's peaks and native-id title, so every row has its own
    ``spectra_ref`` and every reference still resolves to the spectrum that
    produced its PSM. Unit 2 must prove by execution that PDV draws the
    right spectrum for PSMs that share one (duplicate titles included); if
    PDV cannot, the unit stops and the phase escalates.

P11-13 -- joining Percolator to pepXML (from unit 1)
    ``PSMId``'s rank is the hit's **1-based position** in its
    ``spectrum_query``, not ``hit_rank`` (tied hits share a ``hit_rank``;
    they differ for 81 ``_3`` and 99 ``_4`` PSMs). Join on (``-N`` base,
    scan, charge, position) and then verify the Percolator peptide against
    the hit; a mismatch fails the export naming the PSM.

P11-14 -- accessions come from Percolator (from unit 1)
    The results model shows Percolator's ``proteinIds``; the mzTab
    ``accession`` transcribes them. The pepXML drops ``DECOY_`` on
    alternative proteins where the PIN and Percolator keep it (31 ``_3``,
    12 ``_4`` PSMs) -- a documented divergence, not something to reconcile.

P11-15 -- the mzTab shape PDV requires (from unit 1)
    ``MTD`` lines, one ``PSH``, ``PSM`` lines; no blank or comment lines and
    no empty field (``null``). ``spectra_ref``, ``sequence``, ``charge``
    required. Modifications as bracketed signed masses transcribed from the
    pepXML text in an ``opt_`` ProForma column, fixed modifications written
    explicitly (PDV applies none); no C-terminal form exists, so a
    C-terminal modification fails the export by name. ``calc_mass_to_charge``
    omitted (``null`` there kills the import). ``ms_run[n]-location`` is the
    last ``ms_run[n]-*`` line and names the MGF passed to ``--spectrum``.

P11-16 -- asserting what PDV shows (from unit 1; flagged to tier 1)
    No endpoint reports the displayed spectrum: ``/select`` answers ``200
    selected`` once the row is found and a redraw queued, ``404 unknown
    ref``, ``400 missing ref``, ``503`` before ready; ``/shutdown`` stops
    only the control server, **not PDV** (lifecycle must also end the
    process). Gate item 5 is asserted through the control API (known 200,
    unknown 404, under a timeout) **and** a test-side observer that runs
    PDV's unmodified main class in the test JVM and reads which spectrum it
    drew by reflection -- no screen coordinates.

P11-17 -- PDV writes beside its inputs (from unit 1)
    ``<mztab>.db`` beside the mzTab, ``.mgf.cui`` beside each MGF, and files
    under ``~/.compomics`` and ``~/.java``. The mzTab and MGFs therefore live
    in a run-owned PDV directory, never beside the source spectra; tests
    give PDV a temporary ``HOME``.

Work units
==========

Serial, in this order; no two units are live at once (owner, 2026-08-31).
The order is the phase document's and binding: the spike, then the baseline
(gate items 1-4), then the exporter and fidelity (6-8) and the live control
path (5).

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items
     - Sign-off
   * - 1
     - **Spike: does PDV accept a CometGUI-shaped mzTab?** On the real K562
       ``_3`` run (Comet pepXML, Percolator 3.07.1 PSMs, the subset mzML),
       a throwaway generator writes an mzTab; PDV 2.7.0 ``denovo-gui
       --mztab --spectrum --port`` starts under the project-local Xvfb;
       ``/ready`` answers 200 by polling; ``/select`` with a known
       ``spectra_ref`` gets a response that distinguishes a found PSM from
       an unknown one. Establish from PDV's own source at ``v2.7.0``
       (``MztabImport``, ``PdvControlServer``, ``PDVMainClass``) and by
       execution: the columns PDV requires, the ``spectra_ref`` form it
       resolves and how (index, scan, position, title), how modifications
       are parsed, multiple ``ms_run`` handling, what ``/select`` returns,
       and whether the subset mzML works or needs MGF. Any network call PDV
       makes. Evidence page ``docs/developer/pdv_mztab_spike.rst`` and a
       re-runnable script; nothing in product code. **If PDV rejects the
       document, stop and escalate.**
     - D-005 risk; prerequisite for 5-8
     - see `Unit 1 sign-off`_
   * - 2
     - **CLI figure generation and the format gate.** In
       ``tools.pdv``: the CLI figure command as an argument array through
       the process service with a timeout; output judged by files; the
       supported-format check giving the specified error before launch;
       the mzML-to-MGF re-encoding if the spike and Phase 00 require it
       (peaks re-encoded, never altered, proved). Test support starting the
       project-local Xvfb. A real-PDV test on a real Comet pepXML plus its
       spectrum file asserting a valid annotated PNG from its content.
     - R-PDV-01 (first half), gate 2, 3; AC-VIS-02, 03
     -
   * - 3
     - **Install on first use, provenance on launch, the Visualisation
       section's first form.** "Open in PDV" from a run installs PDV
       through the Tool Manager port when absent -- cancellable, not
       blocking a search -- then launches; every launch records the JAR's
       SHA-256 and reported version in the run's ``events.log`` (P11-4).
       GUI test from an empty cache through a throttled loopback artefact
       server showing the search workflow usable during the download.
     - R-PDV-01 (managed), gate 1, 4; AC-VIS-01
     -
   * - 4
     - **Source readers for the exporter.** pepXML search-hit reader in
       ``tools.comet`` (spectrum, native id, scan, charge, rank, peptide,
       modifications with position and mass, proteins incl. alternatives,
       retention time and whatever precursor value the file carries, each
       as its source text); spectrum-file index reader (native id, 0-based
       index, 1-based position, scan, precursor m/z, RT) for the formats
       the spike says PDV resolves against. Real-fixture and constructed
       tests.
     - R-PDV-02, R-PDV-03 (inputs)
     -
   * - 5
     - **The mzTab exporter.** Joins the run's Percolator PSMs (the one
       reader) to their pepXML hits and spectra; writes the mzTab shape the
       spike proved; nulls where the source is silent; fails loudly naming
       the PSM on anything unrepresentable; writes atomically under the run
       directory and records its SHA-256 (P11-4, P11-8).
     - R-PDV-02, R-PDV-03
     -
   * - 6
     - **The fidelity suite** (different agent from unit 5). Test-side
       independent readers; every PSM exactly once and none invented,
       counted both sides; q-value, PEP, charge, m/z, RT, accessions
       against the Percolator and Comet files; modifications as parsed
       values with position and mass; ``spectra_ref`` resolving to the
       producing spectrum on a file where position and scan differ (shown
       first); an injected unrepresentable PSM failing export by name.
     - R-PDV-03, gate 6, 7, 8; AC-VIS-05
     -
   * - 7
     - **The live control path.** The derived controller and ``denovo-gui``
       launcher in ``tools.pdv.derived``; lifecycle in the process service
       (one instance per result set keyed on its mzTab, ephemeral loopback
       port, ``/ready`` polled, debounced select, shutdown on result-set
       close and application exit, actionable error when not ready);
       Results selection driving it. Real PDV end to end: select a known
       PSM and assert through the control API, under a timeout.
     - R-PDV-04, R-PDV-05, gate 5; AC-VIS-04
     -
   * - 8
     - **Documentation and traceability.** ``docs/pdv.rst`` (the mzTab is
       generated for PDV and is not an interchange artefact), developer
       pages, ``docs/citations.rst`` if needed, stale statements,
       traceability for AC-VIS-01..05.
     - R-DOC; AC-VIS-*
     -
   * - 9
     - **The phase harness** ``scripts/verify-pdv-gates.sh``, registered
       additively as ``pdv``: every gate item graded on a production
       injection; cost stated.
     - all eight gate items
     -

Unit sign-offs
==============

Unit 1 sign-off
---------------

**Accepted 2026-10-10 with conditions, which became P11-11..P11-17.**
Commit ``2da544c`` (``scripts/spikes/pdv_mztab_spike.py``,
``scripts/spikes/pdv_mztab_spike/PdvObserver.java``,
``docs/developer/pdv_mztab_spike.rst``, one toctree line). No product code.

* **Diff read.** Four files; nothing under ``cometgui-*``; no gate script,
  POM or record touched. K562 outputs only under ``scratch/pdv-mztab-spike/``.
* **Re-run by me**, the agent's output directory moved aside first:
  ``python3 scripts/spikes/pdv_mztab_spike.py`` -> ``all 289 checks passed
  across 11 experiments``, exit 0, 5 min 19 s; afterwards ``pgrep`` found no
  PDV or Xvfb left running. Seen in the log: honest native-id refs on the
  subset mzML answer ``200 selected`` and draw nothing (``No spectrum to
  display ... _cus_706``); a shared-spectrum PSM shows the first row's PSM
  instead of its own; unknown ref 404; R11 joined all 2130 ``_3`` target
  PSMs and drew rows 0 and 1106 correctly over MGF index refs; no
  non-loopback socket.
* **Source claims checked by me** in ``scratch/pdv-src`` (PDV ``v2.7.0``):
  ``MztabImport.java`` 270-280 splits on ``:index=`` for MGF and on
  ``:.*scan=`` for mzML; line 425 is the ``putIfAbsent`` on the verbatim
  ``spectra_ref``.
* **Analytics:** none reachable from PDV's code; a Google Analytics sender
  ships unused in ``lib/utilities-4.11.14.jar`` (no PDV class references
  it). Recorded for the handoff; nothing to escalate as a behaviour.
* ``docs`` and ``traceability`` run by the agent; ``docs-build.sh`` re-run by
  me with this entry.

Unit 2 sign-off
---------------

**Accepted 2026-10-10**, commit ``ff324f2`` (37 files: 20 main and 11 test
classes in ``cometgui-tools`` ``org.cometgui.tools.pdv`` and
``org.cometgui.tools.testing``; the spike's R12 experiment).

* **Diff read**, ``PdvFigures`` in full: format check, then an empty work
  directory, MGF, ids file, launch through ``ToolRunner`` over the process
  service with a timeout; judged only by the files (timeout, invalid PNG,
  a figure owned by no requested id, non-zero exit, ``NO_FIGURE`` on exit 0,
  ``error.txt`` lines). ``HOME`` and ``-Duser.home`` point into the work
  directory (P11-17). No POM, gate script or record changed.
* **Tests, run by me** after ``mvn -o -pl cometgui-domain,cometgui-process
  install -DskipTests``: ``mvn -o -pl cometgui-tools verify`` -> **703 tests,
  0 failures, 0 skipped**, BUILD SUCCESS (Checkstyle, SpotBugs, JaCoCo in
  ``verify``); ``PdvFiguresRealTest`` 2 tests ran in 5.1 s against real PDV
  under the project-local Xvfb.
* **My injections** (production code; each anchor asserted unique and gone,
  ``pdv`` tests only, 118 run each time; restored with ``git checkout`` and
  ``sha256sum -c`` clean):

  - J1 ``PngFigure``: the blank-figure floor ``nonWhite <
    MINIMUM_NON_WHITE_FRACTION * total`` -> ``nonWhite < 0``: 1 failure,
    ``PngFigureTest.floor`` "Expected InvalidFigureException ... nothing was
    thrown".
  - J2 ``PdvMgfWriter.writeOnePerRow`` writing each spectrum once
    (``LinkedHashSet``): 1 failure, ``PdvMgfWriterTest.onePerRow``.
  - J3 ``MzMlSpectrumReader`` position off by one: 9 failures, 7 errors,
    e.g. ``MzMlSpectrumReaderTest.identity``, ``theRealFixture``.

* **Targeted PIT, by me** (``-DtargetClasses=org.cometgui.tools.pdv.*``,
  pdv tests): 444 mutations, 434 KILLED, 3 TIMED_OUT, 7 SURVIVED -- the same
  seven the agent argued equivalent (two ``Inflater.end``/``close`` calls,
  three unreachable boundaries, the redundant external-entities setting, one
  in-memory ``close``). KILLED-only 97.7 %. **Population:** every compiled
  class is mutated except ``PdvFigure`` (record), ``PeakEncoding`` (enum),
  ``SpectrumVisitor`` (interface), ``package-info``.
* ``--only quality`` -> PASS (140 s).
* **Outside files the tests read** (brief, build economy 5):
  ``scratch/phase05/artefacts/v2.7.0__PDV-2.7.0.zip``, ``manifests/tools.json``,
  ``scratch/fixture/..._K562_3.mzML``,
  ``scratch/scientific-path/comet/..._K562_3.pep.xml``,
  ``tools/x11-bookworm-20260829/``, ``tools/fontstack-bookworm-20260829/``,
  ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``; and the host's
  ``gcc`` (compiles the shim, as the Phase 00 spikes do). The agent read
  the ``tests`` sandbox: ``scratch/`` and ``tools/`` are linked,
  ``scripts/`` and ``manifests/`` copied. ``--only tests`` at phase end.
* **Findings carried forward.** P11-12 proved: one MGF entry per PSM row --
  23 PSMs of seven shared spectra each drawn on their own row with
  bit-identical peaks. **PDV renames the k-th repeat of an MGF title to
  ``<title>_k``**, so P11-11's "the drawn spectrum names itself" holds
  exactly only for a spectrum's first row (R12 pins the rule). mzXML is
  refused, not re-encoded (``PdvSpectrumSupport``: 2.7.0 = mzML, MGF).
  32-bit peaks are written as the float's exact decimal expansion, because
  PDV parses doubles. With ``HOME`` moved, the desktop wiring must pass
  ``XAUTHORITY`` with ``DISPLAY`` (units 3 and 7). Stale:
  ``docs/developer/tool_adapters.rst`` (unit 8).
* **Note:** my targeted PIT overwrote ``cometgui-tools``' PIT report; the
  full reports are regenerated before ``--only tests`` (Phase 08's trap).

Unit 3 sign-off
---------------

**Accepted 2026-10-10**, commit ``a177e62`` (43 files: new
``org.cometgui.workflow.pdv`` -- ``PdvProvisioning``, ``PdvLaunches``,
``PsmFigures``; ``ProvenanceEventType.PDV_LAUNCHED``; ``ui.viewmodel.visualisation``
and ``ui.controls.visualisation``; app ``ProjectVisualisationPort``,
``PdvDisplay``, ``RunWiring``; archtests ``PdvLaunchPathRuleTest``). No POM
changed, no module edge added.

* **Diff read**, ``PdvLaunches`` in full: the per-run ``ProcessRunner`` refuses
  a command that does not launch the offered JAR, hashes the JAR with the
  one ``HashService``, reads its manifest version and refuses a mismatch,
  appends ``pdv.launched`` (version, JAR path, MD5, SHA-256, kind, redacted
  argv, working directory, redacted environment, ``launch.*`` context) to
  the run's ``events.log``, and only then starts the process. Unit 7's
  ``denovo-gui`` launcher must take this runner (``PdvLaunchKind.DENOVO_GUI``
  exists); construction of ``PdvFigures`` outside ``workflow.pdv`` is
  refused by ``PdvLaunchPathRuleTest``. Drawing for an executing run is
  refused (Phase 10's rule for exports). "Open run in PDV" is absent.
* **Tests, run by me** after ``mvn -o -pl cometgui-app -am install
  -DskipTests``: ``cometgui-workflow verify`` **760 tests, 0 failures**;
  ``cometgui-app`` ``PdvFirstUseUiTest`` (1 test, 40 s, real Comet,
  Percolator and PDV from an empty cache through a held loopback transfer)
  and ``ProjectVisualisationPortTest`` (7) -- 8 run, 0 failures.
* **My injections** (anchor asserted unique and gone; restored, ``sha256sum
  -c`` clean):

  - K1 ``PsmFigures.nativeIdOf`` matching the spectrum's 1-based
    **position** instead of its scan number (the Phase 00 landmine): 28
    ``workflow.pdv`` tests, 2 failures + 2 errors, e.g.
    ``PsmFiguresTest.anAmbiguousScanIsRefused`` "no spectrum there carries
    that scan number".
  - K2 ``PdvLaunches``' other-program refusal removed: 1 failure,
    ``PdvLaunchesTest.anotherProgramIsRefused``.
  - K3 ``VisualisationViewModel`` drawing without installing
    (``status.state() == INSTALLED`` -> ``!= null``): ``VisualisationViewModelTest``
    3 failures + 3 errors of 14; and **the gate-1 GUI test**
    ``PdvFirstUseUiTest`` -> "the PDV transfer did not reach 30000000
    bytes". **My first two K3 attempts did not land** -- ``if (true)`` did
    not compile and the second form was stopped by the ui coverage check
    before ``install``, so the app test ran against the old jar and stayed
    green; caught by hashing the installed jar (unchanged). The third
    attempt packaged the injected jar over the installed one (hash
    changed) and the test went red. Clean jar reinstalled afterwards.

* **Targeted PIT, by me**, ``-DtargetClasses=org.cometgui.workflow.pdv.*``:
  **68 mutations, 68 KILLED**; every compiled class mutated except the
  ``DrawnSpectrum`` record and ``package-info``.
* **Harnesses, run by me on ``a177e62``**, all PASS: ``quality`` 140 s,
  ``provenance`` 234 s, ``shell`` 401 s, ``results`` 566 s, ``workflow`` 477 s,
  ``percolator`` 713 s, ``install`` 340 s, ``paramui`` 1904 s.
* **Outside files the tests read** (``PdvFirstUseUiTest``):
  ``scratch/phase05/artefacts/`` (PDV, Comet, Percolator 3.07.1 artefacts),
  ``scratch/fixture/..._K562_3/_4.mzML`` and the FASTA,
  ``manifests/tools.json``, the X11 and font stacks under ``tools/``,
  ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``, the host's ``gcc``.
  ``cometgui-app`` carries its own copy of ``ProjectXvfb`` (test classes are
  not shared between modules).
* **For later units and tier 1:** ``org.cometgui.workflow.pdv`` and
  ``ui.viewmodel.visualisation`` are not POM PIT targets (a POM change;
  escalated, not made). ``EngineStep.LAUNCH_PDV`` untouched: launches are
  user-initiated through the port, not planned engine steps -- left for
  tier 1. No outcome event follows ``pdv.launched``. While the section
  installs PDV, the Tool Manager row refreshes only when the install ends.

Unit 4 sign-off
---------------

**Accepted 2026-10-10**, commit ``a3c690f`` (28 files, all ``cometgui-tools``:
``org.cometgui.tools.comet.PepXmlSearchHitReader``, ``PepXmlHitIndex``,
``PepXmlHitKey(startScan, assumedCharge, position)`` and the ``PepXml*``
records; constructed fixture ``comet-shapes.pep.xml``; test helpers
``RealCometSearch``/``K562Run`` extended). No existing production class
changed beyond a ``package-info`` comment; no POM.

* **Diff read**: StAX, hardened as the validator; values as ``PepXmlDecimal``
  (the file's text, exact ``BigDecimal`` alongside); hit position = 1-based
  place in the query (P11-13); 19 known elements only, unknown attributes
  refused on the four mass-defining elements; the index refuses two
  queries with one scan and charge.
* **Tests, run by me**: ``mvn -o -pl cometgui-tools verify`` -> **829
  tests, 0 failures** (703 before unit 4).
* **My injections** (126 ``PepXml*`` tests each; restored, ``sha256sum -c``
  clean): L1 hit position 0-based -> 19 failures + 6 errors; L2
  ``retention_time_sec`` dropped -> 7 failures + 1 error, e.g.
  ``PepXmlSearchHitReaderRealBinaryTest`` "expected Optional[4741.0] but was
  Optional.empty"; L3 the variable delta read from ``static`` -> 18 + 22.
* **Targeted PIT, by me** (``PepXml*`` classes and tests): 152 mutations on
  the new classes, 148 KILLED, 1 SURVIVED (``line > 0`` boundary, -1 or
  >= 1 only: equivalent), 1 NO_COVERAGE (``addSuppressed`` after a failed
  close); 2 further NO_COVERAGE in the pre-existing ``PepXmlSummary`` only
  because I narrowed the tests.
* ``--only quality`` PASS (141 s), ``--only percolator`` PASS (715 s; the
  shared ``RealCometSearch`` helper changed).
* **Outside files the tests read**: ``scratch/scientific-path/comet/..._{3,4}.pep.xml``,
  ``scratch/scientific-path/percolator-3.07.1/psms.{target,decoy}.txt``,
  ``scratch/phase05/artefacts/`` Comet 2026.02.2 and 2026.03.0,
  ``scratch/fixture/`` mzML and FASTA.
* **Facts for unit 5** (from Comet's source and real runs):
  ``mod_nterm_mass``/``mod_cterm_mass`` are whole terminal-group masses, not
  deltas -- a variable terminal mod on a static one sums to a value no
  declaration states (measured ``71.049690``); a residue may carry static and
  variable deltas together; values are printed un-normalised
  (``15.994900``); ``decoy_search = 2`` writes ``<base>.decoy.pep.xml`` with
  PSMIds shared with the target file, and ``RunLayout`` has no accessor for
  it; Comet writes ``DECOY_`` only on a decoy hit's first protein. Keeping
  1 000 000 joined PSMs costs about 1.4 GB of heap; streaming the same file
  without keeping fits 64 MB.

Unit 5 sign-off
---------------

**Accepted 2026-10-10**, commits ``bd25042`` and ``32f68ce``.
``org.cometgui.tools.pdv.PdvMzTabExporter`` (+ ``PsmHit``,
``PercolatorPeptide``, ``LongIntMap``, request/export/source/MGF records,
``PdvMzTabException`` with 19 reasons); ``org.cometgui.workflow.pdv.PdvMzTabs``
(``prepare`` -> ``<run>/pdv/mztab/``, reuse only when the last
``mztab.written`` event matches every source SHA-256); new
``ProvenanceEventType.MZTAB_WRITTEN``. **Module edge added:
``cometgui-tools`` -> ``cometgui-results``** (commented in the POM; P11-2).

* **Diff read** (the row-writing loop, accession, modification and
  terminal rules): every value is source text -- Percolator score, q-value,
  PEP and ``proteinIds``; pepXML peptide, charge, flanks,
  ``retention_time_sec``; the spectrum file's selected-ion m/z;
  ``calc_mass_to_charge`` omitted; ``null`` where no source states a value.
  N-terminal deltas only where exactly one N-terminal declaration exists and
  its mass text equals the hit's; every C-terminal modification refused.
  Written in a staging directory, read back (one line per row, references
  ``0..n-1``, MGF entry counts), then renamed; nothing at the final path on
  failure.
* **Tests, run by me** after ``mvn -o -pl cometgui-workflow -am install``:
  ``cometgui-tools verify`` **894, 0 failures** (``PdvMzTabExporterRealTest``
  7, ``PdvMzTabRealPdvTest`` 2 against real PDV); ``cometgui-workflow verify``
  **771, 0 failures**; ``cometgui-archtests`` 31, 0 failures (run directly:
  ``--only quality`` covers formatting, static analysis and the derived-file
  machinery, **not ArchUnit**, which only ``tests``/``build.sh`` run -- the
  brief's "quality for architecture rules" is imprecise; noted for tier 1).
* **My injections** (183 ``pdv`` tests each; restored, ``sha256sum -c``
  clean):

  - M1 the C-terminal refusal disabled: 1 failure,
    ``PdvMzTabExporterTest.refused`` "nothing was thrown".
  - M2 accession = first protein only: 2 failures, e.g.
    ``PdvMzTabExporterRealTest.accessions`` "expected
    sp|O60739|EIF1B_HUMAN;sp|P41567|EIF1_HUMAN but was sp|O60739|EIF1B_HUMAN".
  - M3 retention time always ``null``: 4 failures.

* **Targeted PIT, by me**: exporter classes 416 mutations -- 366 KILLED, 42
  TIMED_OUT, 8 NO_COVERAGE (the ``SOURCE_CHANGED``/``INCOMPLETE`` fallbacks),
  **0 SURVIVED**; ``PdvMzTabs`` 41/41 KILLED.
* **Harnesses, run by me on ``32f68ce``**, all PASS: ``quality`` 141 s,
  ``provenance`` 236 s, ``workflow`` 475 s, ``results`` 572 s,
  ``percolator`` 723 s.
* **Process hygiene.** PIT halts minions with ``Runtime.halt``, so real-PDV
  tests orphan PDV and Xvfb; the agent now launches both under the host's
  ``/usr/bin/timeout`` (PDV 300 s, Xvfb 600 s, test support only). Observed
  by me: 45 PDV processes after my PIT run, all gone within their caps; Xvfb
  gone within 10 minutes; 56 earlier Xvfb zombies remain ``<defunct>`` (PID
  1 does not reap, Phase 03). Halted minions also leave ``target/mzt*``
  directories until ``mvn clean``.
* **Outside files the tests read** (new to tools): ``scratch/fixture/..._4.mzML``,
  ``scratch/scientific-path/comet/..._4.pep.xml``; host ``/usr/bin/timeout``.
* **Measured by the agent:** 1 000 450 PSMs exported in 54-65 s, live heap
  peak 92 MB, completes at ``-Xmx128m``; output 278 MB mzTab and 1.78 GB MGF
  (one entry per PSM, P11-12). Real K562 ``_3`` + ``_4``: 3897 target + 2773
  decoy rows in, 6670 lines out.
* **For units 6 and 7:** with ``decoy_search = 2`` target and decoy rows
  share a PSMId -- key on (decoy flag, PSMId); stop a running PDV before
  ``prepare`` (a regeneration deletes the directory).

Unit 6 sign-off
---------------

**Accepted 2026-10-10**, commit ``a821c3f`` (tests only: 15 files in
``cometgui-tools`` ``org.cometgui.tools.pdv.fidelity`` and
``cometgui-workflow`` ``PdvMzTabsFidelityTest``). **No exporter defect found.**

* **Diff read; independence audited by me**: the seven readers
  (``MzMlFile``, ``MzTabFile``, ``PepXmlFile``, ``PercolatorTable``,
  ``MgfEntries``, ``Peptidoforms``, ``FidelityCheck``) import nothing from
  ``org.cometgui``; the only project types in the package are the exporter's
  entry points (``PdvMzTabExporter``, request, source, exception,
  ``SpectrumFileFormat``) and the test support that runs real tools.
  ``IndependenceGuardTest`` enforces it mechanically.
* **Tests, run by me** after ``mvn -o -pl cometgui-workflow -am install``:
  ``cometgui-tools verify`` **928, 0 failures** (fidelity: K562 17,
  constructed 6, real Comet 2, unrepresentable 5, real PDV 2, guard 2);
  ``cometgui-workflow verify`` **772, 0 failures** (re-run after the agent's
  SpotBugs fix, which it had not re-run in full).
* **Coverage of the gate items** (agent's figures, the tests re-run by me):
  gate 6 -- 6670 K562 rows identity-matched both ways, plus constructed (9),
  real Comet ``decoy_search = 2`` with N-terminal and static+variable
  modifications (13 216) and static TMT (4170) runs, and the run-level
  ``prepare`` path; gate 7 -- position != scan shown from the mzML for every
  producing spectrum, every row's ``spectra_ref`` resolved as PDV does and
  1 034 444 peak values compared bit for bit, wrong-reference forms (file
  position, scan, first row of a shared spectrum) shown to be caught; gate 8
  -- five unfaithful PSMs injected into copies of the real sources, each
  refused naming the PSMId, reason and file, nothing left at the final path.
* **My injections into the exporter**, graded on the 34 fidelity tests only
  (restored, ``sha256sum -c`` clean): N1 score and q-value columns swapped
  -> 4 failures, "search_engine_score[1] is "0.00112905", the source's score
  is "2.37114"" (my first N1 matched two anchors, was refused by my own
  guard, and its green run was discarded); N2 charge 3 written as 2 -> 4
  failures, ``K562FidelityTest.charge``; N3 flanks swapped -> 4 failures,
  ``K562FidelityTest.sequence``.
* ``--only quality`` PASS.
* **Outside files**: as units 2-5, plus the host's ``gcc`` and
  ``/usr/bin/timeout``; **and** ``PdvMzTabsFidelityTest`` compiles
  ``cometgui-tools/src/test/java/org/cometgui/tools/pdv/fidelity/*.java`` at
  test time (the ``tests`` sandbox copies every module's ``src``).
* **Not errors, recorded:** ``database``/``database_version`` are ``null``
  although the pepXML names the FASTA (``search_database``) -- nothing
  invented, left as is; the MGF's ``RTINSECONDS`` is the mzML scan time while
  the mzTab's ``retention_time`` is the pepXML's; MGF and mzXML sources and
  real Percolator output for the constructed searches were not exercised.

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
