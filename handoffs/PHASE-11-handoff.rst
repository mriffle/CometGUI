=====================================================
PHASE-11 handoff -- PDV Integration
=====================================================

:Phase: 11
:Written: 2026-10-11 by the Phase-11 orchestrator (tier 2)
:Outcome: **All nine work units accepted** (none sent back; one orchestrator
   repair). Every exit-gate item is met **on Linux x86-64**, the only platform
   any of it has executed on; item 5's "displayed spectrum" half rests on a
   test-side observer because PDV's control API cannot report what it shows
   (see `Exit gate`_). Recommended grade **PARTIAL, on platform residue
   alone**. **Not yet signed off by tier 1**, whose ``scripts/build.sh`` and
   full ``scripts/verify-all-gates.sh`` run has not happened; nobody in this
   phase ran either, by the owner's build-economy rule.
:Records: ``handoffs/PHASE-11-worklog.rst`` -- **this page is the map; the work
   log is the proof.** Every sign-off there names the commands run, the counts
   seen and the production-code injections made, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

A scientist can now look at a spectrum. The Visualisation section installs PDV
2.7.0 through the Tool Manager on first use -- cancellable, never holding up a
search -- and either draws the selected PSM's annotated spectrum with PDV's
command-line figure mode, or opens a live PDV window (``denovo-gui`` on an
ephemeral loopback port) that follows every PSM selected in the Results table.
PDV's control server only accepts mzTab, so the substance of the phase is an
**mzTab exporter** that transcribes the run's Percolator and Comet values and
refuses, by name, anything it cannot represent faithfully; an **independent
fidelity suite** (its own readers, sharing no code with the exporter) proves it
against the source files, including ``spectra_ref`` on spectrum files where
1-based position and scan number differ for every spectrum. Every PDV launch
records the JAR's SHA-256 and manifest version in the run's ``events.log``
before the process starts; every mzTab write records its checksums. The
controller and the ``denovo-gui`` command are derived from
``Noble-Lab/CasanovoGUI`` with the derivation recorded (``D-001``). New harness
``scripts/verify-pdv-gates.sh`` (``pdv``, 107 controls, about 14 minutes).

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 26 68

   * - Unit
     - Commits
     - What
   * - 1
     - ``2da544c``
     - The spike: PDV 2.7.0 ``denovo-gui`` accepts a Comet + Percolator mzTab
       (``scripts/spikes/pdv_mztab_spike.py``, ``PdvObserver.java``;
       ``docs/developer/pdv_mztab_spike.rst``). Its conditions became design
       decisions P11-11..P11-17.
   * - 2
     - ``ff324f2``
     - ``tools.pdv``: mzML/MGF readers, ``PdvMgfWriter`` (exact peaks; one
       entry per spectrum or per PSM row), ``PdvSpectrumSupport`` (format
       check before launch), ``PdvFigures`` + ``PngFigure`` (CLI figure,
       judged by content); test support ``ProjectXvfb``, ``RealPdv``.
   * - 3
     - ``a177e62``
     - ``workflow.pdv``: ``PdvProvisioning``, ``PdvLaunches`` (the one launch
       path: hash, identify, record ``pdv.launched``, then start),
       ``PsmFigures``; ``ProvenanceEventType.PDV_LAUNCHED``; the Visualisation
       section; ``PdvLaunchPathRuleTest``.
   * - 4
     - ``a3c690f``
     - ``tools.comet``: ``PepXmlSearchHitReader``, ``PepXmlHitIndex`` (join on
       scan, charge, 1-based hit position).
   * - 5
     - ``bd25042`` ``32f68ce``
     - ``tools.pdv.PdvMzTabExporter`` (+ ``PsmHit``, ``PercolatorPeptide``,
       19 refusal reasons, staging + read-back + rename);
       ``workflow.pdv.PdvMzTabs`` (``<run>/pdv/mztab/``, reuse by source
       SHA-256s); ``ProvenanceEventType.MZTAB_WRITTEN``. **Module edge
       ``cometgui-tools`` -> ``cometgui-results``** (the one Percolator
       reader).
   * - 6
     - ``a821c3f``
     - The fidelity suite ``tools.pdv.fidelity`` (tests only) and
       ``workflow.pdv.PdvMzTabsFidelityTest``.
   * - 7
     - ``309fa97`` ``cb53c88`` ``1ec639d``
     - **Derived** ``tools.pdv.derived.PdvController`` and
       ``DenovoGuiCommand``; ``PdvMzTabRefs``; ``workflow.pdv.PdvWindows``;
       Open/Close PDV and selection in the Visualisation section; the app's
       ``stop()`` closes every window.
   * - 8
     - ``5c6c717``
     - ``docs/pdv.rst``, ``docs/developer/pdv_integration.rst``, stale pages,
       ``citations.rst``, traceability for AC-VIS-01..05.
   * - 9
     - ``2a7ffe5``
     - ``scripts/verify-pdv-gates.sh``, registered as ``pdv`` (floor 107).

Orchestrator commits: ``d288509`` (work log), a sign-off commit per unit, and
the one repair ``1f25b47`` (the Visualisation section's arrival note said
"being filled by phase 11").

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-provenance`` (two event types); ``cometgui-tools/pom.xml`` (the
``cometgui-results`` dependency, commented); ``cometgui-ui`` (``UiIds``,
``ShellView``, ``SectionArrivals``, ``ResultTableViewModel.anchorRowProperty``);
``cometgui-app`` (composition, ``PdvDisplay``, ``stop()``); ``cometgui-archtests``
(``PdvLaunchPathRuleTest``, two rules); ``scripts/verify-all-gates.sh``
(additive). **No PIT configuration changed**; no floor lowered; no test
removed or disabled.

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
     - ``PdvFirstUseUiTest``: the managed Tool Manager over an empty cache
       fetches the real 103 407 417-byte PDV from a loopback server held at
       30 000 000 bytes; **while held, a second real Comet + Percolator search
       runs to success through the Run section**, the transfer checked still
       part-way before and after; then the install completes with the pinned
       SHA-256 and the figure is drawn. Red: my K3 ("the PDV transfer did not
       reach 30000000 bytes"); ``pdv`` 1a, 1b (interface frozen).
   * - 2
     - Met (Linux)
     - ``PdvFiguresRealTest``: the real K562 Comet pepXML and the mzML Comet
       searched (re-encoded to MGF with bit-identical peaks, because PDV's
       pepXML+mzML path writes nothing on a scan-range subset) give an
       800x400 PNG proved by parsing (signature, every CRC, inflated pixels,
       >= 1 % non-white, >= 300 pixels in each of PDV's ion colours, empty
       ``error.txt``); an absent id is ``NO_FIGURE``, not a pass. Red: my J1;
       ``pdv`` 2a-2c (two against real PDV).
   * - 3
     - Met
     - ``PdvSpectrumSupport`` refuses every format but mzML and MGF for PDV
       2.7.0 with a message naming the file, its format, the PDV version and
       what is supported, **before** anything launches (recording process
       runner). Red: ``pdv`` 3a, 3b. **Note:** mzXML is refused (the
       specification's baseline text lists it; escalated).
   * - 4
     - Met (Linux)
     - Every launch goes through ``PdvLaunches``: ``pdv.launched`` in the
       run's ``events.log`` with the manifest version, MD5 and SHA-256 of the
       JAR, argv and environment, written **before** the start (a failed
       start keeps its record); ``PdvFirstUseUiTest`` and
       ``PdvLiveWindowUiTest`` compare the SHA-256 with their own hash and the
       version with the manifest they read; ``PdvLaunchPathRuleTest`` forbids
       another launch path. Red: my K2; ``pdv`` 4a-4d. In ``events.log``, not
       ``provenance.json``, per tier 1's Phase 10 ruling.
   * - 5
     - Met (Linux), on the basis in P11-16
     - ``PdvLiveWindowUiTest``: real run through the UI, Open installs PDV,
       ``denovo-gui`` on an ephemeral port, ``/ready`` reached by polling, two
       Results selections each answered ``200 selected``, an unknown ref 404,
       Close and the application's ``stop()`` leave no process and a refused
       port. **PDV's API cannot report what it displays**, so the "changes
       the displayed spectrum" half is ``PdvControllerRealPdvTest``: PDV's
       unmodified main class under a test-side reflective observer, driven by
       the derived controller over HTTP -- five selections (two sharing one
       spectrum, one oxidised) each draw exactly that PSM's row, peptide,
       charge, modifications and the producing spectrum's peaks (SHA-256
       over every peak). No screen coordinates. Red: my Q1, Q2; ``pdv``
       5a-5e (5b red only in the observer test -- PDV still answers 200).
   * - 6
     - Met
     - The fidelity suite reads Percolator, pepXML, mzML, mzTab and MGF with
       its own readers (mechanical independence guard): every PSM exactly
       once and none invented, by (decoy, PSMId) both ways, counts both
       sides; score, q-value, PEP, charge, observed m/z, retention time,
       accessions as source text; modifications as (position, delta) values
       against the pepXML and Percolator's peptide; ``null`` where no source
       supplies a value. Data: K562 ``_3`` + ``_4`` (6670 rows), constructed,
       and two real Comet searches with terminal and static+variable
       modifications. Red: my N1-N3, M2, M3; ``pdv`` 6a-6e.
   * - 7
     - Met
     - Position != scan shown from the mzML files (both K562 files are
       scan-range subsets); every row's ``spectra_ref`` resolved as PDV does
       and the entry's 1 034 444 peak values compared bit for bit with the
       producing spectrum found independently; wrong references by scan, by
       position and to a shared spectrum's first row are caught. Red: ``pdv``
       7a-7c, each with the exporter's own read-back off.
   * - 8
     - Met
     - Into copies of the real sources: no pepXML hit, a C-terminal
       modification, a residue and a modification mismatch, a missing
       spectrum -- each fails the export naming the PSMId, the reason and the
       file, with nothing at the final path. Red: my M1; ``pdv`` 8a-8c.

**Harness runs on the final tree** (run by me; ``pdv`` on ``2a7ffe5``; the
last commit touching code is ``1f25b47``): ``pdv`` **107** (821 s); per-unit
runs, each on the last commit that changed what it reads: ``quality``,
``provenance``, ``shell``, ``results``, ``workflow``, ``percolator``,
``install``, ``paramui``, ``docs``, ``traceability`` -- each PASS (worklog).
``--only tests``: see `The deferred tests harness`_.

.. _p11h-tests:

The deferred tests harness
==========================

Before it, every module's PIT report was regenerated exactly as
``scripts/build.sh`` does (``mvn -B -o -Dcometgui.pit.threads=16 test-compile
org.pitest:pitest-maven:mutationCoverage`` at the root; exit 0 in 5169 s),
because the units' targeted PIT runs had overwritten several reports (Phase
08's trap); a stray ``cometgui-ui`` report from a unit's targeted run, which
``build.sh`` never produces, was removed. KILLED-only per module: domain 669/673,
provenance 772/775, process 160/175 (91.4 %), **tools 1714/1832 (93.6 %; 645 at
Phase 10)**, install 1398/1415, params-comet 1768/1775, params-percolator
145/145, results 888/943 (94.2 %), workflow 242/242.

``bash scripts/verify-all-gates.sh --only tests``, run detached on ``39e7fdd``
(code unchanged since ``1f25b47``) and watched to its exit line: **PASS, 37
assertions in 16 221 s (270 minutes)** -- up from 13 778 s at Phase 10, the
growth being this phase's tests (real PDV, real Comet, the fidelity suite)
inside the sandbox's ``build.sh`` and its PIT. ``verify-test-gates.sh
--preflight`` held before it.

Tests that read outside their module read only under ``scratch/``
(``phase05/artefacts``, ``fixture``, ``scientific-path``), ``tools/`` (X11 and
font stacks), ``manifests/tools.json``, ``scripts/feasibility/gui-spike/xkbcomp-path-shim.c``
and, at test time, ``cometgui-tools``' own fidelity test sources; none reads a
project document.

Incomplete, deferred, residue
=============================

* **Platform.** Linux x86-64 only. Nothing ran on Windows or macOS (PDV is
  Java, but the display, the process tree and paths are platform-specific).
* **The display claim of item 5** rests on an in-JVM observer, not on the
  production window: PDV 2.7.0 has no endpoint that reports what it draws.
* **mzXML** is refused (no re-encoder; PDV's ``denovo-gui`` cannot read it).
* **MGF size**: one MGF entry per PSM (P11-12) -- 1.78 GB of MGF for a
  1 000 000-PSM result set (export 54-65 s, heap peak 92 MB).
* The bind-race relaunch is proven against a fake only; a foreign server
  answering ``/ready`` on the reserved port would be trusted; the
  ``spectra_ref`` index's memory at 1M PSMs is unmeasured.
* ``database``/``database_version`` are ``null`` though the pepXML names the
  FASTA; the MGF's ``RTINSECONDS`` is the mzML scan time while the mzTab's
  retention time is the pepXML's.
* ``org.cometgui.workflow.pdv`` and ``ui.viewmodel.visualisation`` are not POM
  PIT targets (targeted PIT: 68/68, 41/41, 34/34 on workflow).
* The derived controller's timing code scores 69 % KILLED-only (89 % with
  timeouts) under targeted PIT -- the owner's open ``TIMED_OUT`` question.
* Real-PDV tests need the host's ``gcc`` (the xkbcomp shim) and
  ``/usr/bin/timeout``; ``verify-test-gates.sh``'s preflight checks neither
  (``verify-pdv-gates.sh``'s does). PIT-halted minions orphan PDV and Xvfb
  until their caps (300 s / 600 s) and leave ``target/mzt*``; this container
  accumulates ``<defunct>`` Xvfb zombies (PID 1 does not reap).
  ``cometgui-app``'s copy of ``ProjectXvfb`` has no ``timeout`` cap.
* ``EngineStep.LAUNCH_PDV`` is declared and unused (launches are
  user-initiated).
* Stale, not this phase's to rewrite: ``docs/developer/index.rst`` (calls
  most pages Phase 01 stubs) and ``docs/developer/testing.rst``'s "not tested
  yet" table.

Decisions encountered
=====================

No ``D-`` item was answered. Design decisions P11-1..P11-17 are in the work
log. Two are judgement calls tier 1 should confirm:

* **P11-12** -- PDV keeps only the first mzTab row per exact ``spectra_ref``
  (``MztabImport.java:425``), so 1404 of 2130 ``_3`` target PSMs could never
  be selected. The PDV MGF therefore holds one exact copy of the producing
  spectrum per PSM row, giving every row its own reference; proven to draw
  the right PSM and spectrum for shared spectra. PDV shows repeated titles as
  ``<title>_k``.
* **P11-16** -- item 5's display half asserted through an in-JVM observer.

Others: the MGF route rather than subset mzML with ``scan=<position>``
(P11-11, rejected because it states a false scan number); accessions from
Percolator (P11-14); N-terminal deltas transcribed only when exactly one
declaration matches, every C-terminal modification refused; ``--tol``
omitted (Comet's ``fragment_bin_tol`` is not an annotation tolerance); the
mzTab and PDV's side files live under ``<run>/pdv/``.

Surprises
=========

* **PDV selects only the first row per ``spectra_ref``** (P11-12 above).
* **``/shutdown`` stops only PDV's control server**; the window keeps
  running, so the lifecycle also ends the process tree.
* **PDV's subset-mzML route resolves ``scan=`` against the 1-based file
  position** (msftbx), and ``index=`` crashes the import -- only a false
  scan number would have worked; hence MGF.
* **Percolator's PSMId rank is the hit's position in the pepXML query**, not
  ``hit_rank`` (tied hits share one; 81 ``_3`` and 99 ``_4`` PSMs).
* **The pepXML drops ``DECOY_`` on alternative proteins** where Percolator
  keeps it.
* **Comet's ``mod_nterm_mass``/``mod_cterm_mass`` are whole terminal-group
  masses**, not deltas.
* **``--only quality`` does not run ArchUnit** (it grades formatting, static
  analysis and the derived-file machinery); the brief's "quality for
  architecture rules" is imprecise. Archtests ran directly at each sign-off.
* **Two of my injections first failed to land** (an unreachable-code compile
  error, then a coverage check stopping ``install``); caught by hashing the
  installed jar. Hash the jar, not the source.
* **Analytics:** none observed from PDV (only loopback sockets); a Google
  Analytics sender ships unused inside ``lib/utilities-4.11.14.jar`` and no PDV
  class references it. Nothing to escalate as behaviour; recorded.

Escalated (none answered here)
==============================

#. **Specification text the build contradicts** (amendment wanted): the
   baseline integration lists mzXML (refused); "Open run in PDV ... using the
   preserved Comet pepXML" (the window reads the generated mzTab and MGF
   copies; the CLI figure path does use the pepXML); the Open control is in
   the Visualisation section driven by the Results selection, not in "the
   Results UI"; "JAR checksum and reported version shall appear in
   provenance" is met in ``events.log``.
#. **Confirm P11-12 and P11-16** (above).
#. **PIT targets**: add ``org.cometgui.workflow.pdv.*`` (a POM change).
#. **``EngineStep.LAUNCH_PDV``**: keep declared-and-unused, or remove.
#. **The ``tests`` preflight** should check host ``gcc`` and
   ``/usr/bin/timeout`` (or the X11 shim should be prebuilt under ``tools/``).
#. **Cost**: ``pdv`` adds about 14 minutes to the full gate run; the
   phase's tests add about a minute of real-PDV runtime to ``build.sh``
   outside PIT, and PIT reruns them per mutant.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts`` (PDV, Comet, Percolator),
``scratch/fixture``, ``scratch/scientific-path`` (``comet``,
``percolator-3.07.1``) and ``scratch/phase10/large`` are present, that the host
has ``gcc`` and ``/usr/bin/timeout``, then run ``bash scripts/build.sh`` and the
full ``bash scripts/verify-all-gates.sh`` (now 18 harnesses with ``pdv``, floor
107). Then decide the escalations above.

**Phase 12 (Limelight):** nothing here constrains it, except that every
optional downstream step so far is user-initiated and recorded as an event in
``events.log``; ``docs/developer/pdv_integration.rst`` shows the pattern.
