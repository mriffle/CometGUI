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

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
