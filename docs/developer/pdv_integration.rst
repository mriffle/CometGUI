.. _dev-pdv-integration:

===============
PDV integration
===============

How CometGUI installs, launches and drives PDV 2.7.0, and how it writes the
mzTab PDV's ``denovo-gui`` mode is given -- **as built** in Phase 11. In one
paragraph: PDV is installed only by the Tool Manager; it is started only
through ``org.cometgui.workflow.pdv.PdvLaunches``, which writes a
``pdv.launched`` event before every start; it draws figures from its command
line (``PdvFigures``) and runs live windows through a controller derived from
Noble-Lab/CasanovoGUI (``tools.pdv.derived``); and the window reads an mzTab
plus one MGF entry per PSM that ``PdvMzTabExporter`` transcribes from
Percolator's tables, Comet's pepXML and the spectrum files, refusing by name
anything it cannot represent faithfully. A fidelity suite that shares no code
with the exporter grades the mzTab against those sources.

.. note::

   **State after Phase 11 -- PDV Integration**, written on 2026-10-11 by
   work unit 8 against the code at that date. The user-facing description is
   :doc:`/pdv`; the spike that decided the route, with PDV's source quoted, is
   :doc:`pdv_mztab_spike`. The design decisions cited as ``P11-n`` are
   recorded, with each work unit's sign-off, in
   ``handoffs/PHASE-11-worklog.rst``. **Everything here has run on Linux
   x86-64 only** (see `What has only run on Linux`_).

.. contents:: Contents
   :depth: 2
   :local:

Packages and classes
====================

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Package (module)
     - What it holds

   * - ``org.cometgui.tools.pdv`` (``cometgui-tools``)
     - ``PdvJarIdentity`` (the version from the JAR manifest; PDV has no
       version option and cannot run headless). ``PdvSpectrumSupport``,
       ``SpectrumFileFormat`` and ``UnsupportedSpectrumFileException`` -- the
       format gate, decided before anything launches. The source readers
       ``SourceSpectra``, ``MzMlSpectrumReader``, ``MgfSpectrumReader``
       (``SourceSpectrum``, ``PeakList``, ``SpectrumVisitor``).
       ``PdvMgfWriter`` -- the exact MGF re-encoding. ``PdvFigures``,
       ``PdvFigureRequest``, ``PdvFigure``, ``PngFigure``,
       ``PdvFigureException`` -- the CLI figure command and its judge.
       ``PdvMzTabExporter`` with ``PdvMzTabRequest``, ``PdvMzTabSource``,
       ``PdvMzTabExport``, ``PdvMzTabMgf``, ``PsmHit``, ``PercolatorPeptide``,
       ``LongIntMap``, ``RetentionTime`` and ``PdvMzTabException`` -- the
       mzTab. ``PdvMzTabRefs`` -- (decoy flag, ``PSMId``) to ``spectra_ref``,
       read from the exporter's mzTab. ``PdvWindowState``,
       ``PdvSelectOutcome``, ``PdvControlAnswer``, ``PdvWindowShutdown`` --
       what the controller reports.

   * - ``org.cometgui.tools.pdv.derived`` (``cometgui-tools``)
     - ``PdvController`` and ``DenovoGuiCommand``, derived from CasanovoGUI
       (see `Derived from CasanovoGUI`_).

   * - ``org.cometgui.tools.comet`` (``cometgui-tools``)
     - Since Phase 11, the pepXML search-hit reader the exporter uses:
       ``PepXmlSearchHitReader`` (StAX, hardened as ``CometPepXmlValidator``),
       ``PepXmlVisitor``, ``PepXmlHitIndex``, ``PepXmlHitKey(startScan,
       assumedCharge, position)`` and the ``PepXml*`` records. Every value is
       a ``PepXmlDecimal`` -- the file's text with its exact ``BigDecimal``.

   * - ``org.cometgui.workflow.pdv`` (``cometgui-workflow``)
     - ``PdvProvisioning`` and ``PdvStatus`` -- PDV through the one
       ``ToolManager`` port. ``PdvLaunches`` and ``PdvLaunchKind`` -- **the one
       launch path**. ``PsmFigures`` (``PsmFigureRequest``, ``DrawnSpectrum``,
       ``SpectrumNotDrawnException``) -- one PSM's figure. ``PdvMzTabs``
       (``PdvMzTab``) -- a run's mzTab, prepared or reused, and its
       ``mztab.written`` event. ``PdvWindows`` (``PdvWindowRequest``,
       ``PdvWindowStatus``, ``PdvSelection``) -- the live window per run.
       ``PdvInstallation`` -- an installed PDV.

   * - ``org.cometgui.ui.viewmodel.visualisation``,
       ``org.cometgui.ui.controls.visualisation`` (``cometgui-ui``)
     - ``VisualisationViewModel`` and its ``VisualisationPort``;
       ``VisualisationPane``. Toolkit-free view-model: it uses the Results
       section's one selection (``chosenRunProperty``, ``anchorRowProperty``),
       keeps none of its own, and closes the run's window when that run stops
       being the Results section's.

   * - ``org.cometgui.app.config`` (``cometgui-app``)
     - ``ProjectVisualisationPort`` (the port over the Tool Manager, the
       session's project, ``PsmFigures`` and ``PdvWindows``; refuses an
       executing run whatever the caller checked), ``PdvDisplay`` (``DISPLAY``
       and ``XAUTHORITY`` for PDV), and ``RunWiring``, which builds
       ``PdvLaunches`` over the one process service, hasher and secret
       redactor. ``CometGuiApplication.stop()`` closes every window.

   * - ``org.cometgui.provenance.events`` (``cometgui-provenance``)
     - Two new ``ProvenanceEventType`` values, added additively:
       ``PDV_LAUNCHED`` (``pdv.launched``) and ``MZTAB_WRITTEN``
       (``mztab.written``).

**One module edge was added**: ``cometgui-tools`` depends on
``cometgui-results`` (unit 5, P11-2), so that the exporter reads Percolator's
tables through the one reader, ``org.cometgui.results.parser.ResultTableReader``,
and parses ``PSMId`` with the one ``SpectrumReference``. ArchUnit's cycle rule
grades it; the edge creates no cycle.

**What Phase 11 did not use.** ``EngineStep.LAUNCH_PDV`` (``launch-pdv``,
declared since Phase 08 with ``implementedInPhase`` 11) is still declared and
is never planned: PDV is launched by the scientist through the Visualisation
section, from a finished run, not as a step of the run. Whether to remove the
declaration is left to tier 1.

Derived from CasanovoGUI
========================

``R-PDV-05`` permits and expects reuse of CasanovoGUI's ``PdvLauncher`` and
``PdvController``, under ``D-001``'s obligations. Two files were derived, both
from ``Noble-Lab/CasanovoGUI`` at commit
``480b3013e7f8fb51a2b8c58681043821e3e7f865`` (GPL-3.0), and both live under a
``/derived/`` path so the derived-file regime of :doc:`architecture` applies
(the derived header, the per-file derivation record, the derived Spotless and
Checkstyle file sets). Upstream carries no per-file copyright notice, so none
was dropped.

.. list-table::
   :header-rows: 1
   :widths: 30 26 44

   * - File in this repository
     - Upstream file
     - Kept, and changed

   * - ``cometgui-tools/src/main/java/org/cometgui/tools/pdv/derived/PdvController.java``
     - ``src/main/java/org/casanovo/gui/core/PdvController.java``
     - **Kept**: one instance per mzTab (a map keyed on the mzTab's absolute
       path, a new registration replacing the old); an ephemeral loopback port
       reserved by binding port 0; ``/ready`` polled until 200, process death
       or a five-minute cap; the 60 ms debounce, a burst collapsing to the
       last selection; ``POST /select?ref=`` URL-encoded as UTF-8; the
       loopback address and request timeouts. **Changed**: launch through the
       ``ProcessRunner`` the caller gives (never ``ProcessBuilder``); no
       failure swallowed -- ``FAILED`` with an actionable message (exit status
       and PDV's last lines, or the deadline and its last answer); polling on
       a scheduled executor with a deadline instead of a ``Thread.sleep``
       loop; the bind race handled by relaunching on a fresh port, at most
       three launches; a selection made before ready kept (the latest) and
       sent when ready; shutdown sends ``/shutdown`` **and** ends the process
       tree, then waits, bounded (upstream had no shutdown). Upstream's
       ``launchAndRegister``, which also downloaded PDV, is not here.

   * - ``cometgui-tools/src/main/java/org/cometgui/tools/pdv/derived/DenovoGuiCommand.java``
     - ``src/main/java/org/casanovo/gui/core/PdvLauncher.java``
       (``launchDenovo``'s argument array only)
     - **Kept**: the argument array ``java -jar <jar> denovo-gui --mztab
       <file> --spectrum <files joined by ","> [--tol <v> --tol-unit <u>]
       [--port <n>] [--hide-psm-table]``, absolute paths. **Changed**: it
       builds a ``ToolCommand`` and starts nothing; ``--port`` is required;
       ``--tol`` is optional and the product omits it (Comet's
       ``fragment_bin_tol`` is a scoring bin width, not an annotation
       tolerance, so PDV's default of 0.05 Da is used rather than an invented
       value); ``HOME`` and ``-Duser.home`` moved into a caller-owned
       directory and ``LANG`` fixed (P11-17); the application's own Java
       runtime; a spectrum path containing ``,`` and two spectra with one file
       name refused before launch.

   * - ``cometgui-tools/src/main/java/org/cometgui/tools/pdv/derived/package-info.java``
     - (new writing)
     - Carries the derivation record (for ``PdvController.java``) because the
       Checkstyle rule admits no exception for a file name; its text says it
       is new writing.

**Not brought in, by design (P11-1, P11-3):** anything of upstream's
``PdvLauncher`` that downloads, unpacks, queries the GitHub API or chooses a
version. PDV is obtained only through the Tool Manager and
``manifests/tools.json``. The derived classes' own documentation comments list
what was kept and changed in more detail.

Tests of derived code (``PdvControllerTest``, ``DenovoGuiCommandTest``,
``PdvControllerRealPdvTest``) are not derived material and live in
``org.cometgui.tools.pdv`` with the ordinary header.

The one launch path
===================

Every PDV process is started through a ``ProcessRunner`` from
``PdvLaunches.forRun(run, runId, pdv, kind, context)``. Each ``start`` through
it, in this order:

#. refuses a command that does not launch that installed PDV JAR;
#. hashes the JAR with the one ``HashService`` (MD5 and SHA-256, one pass) and
   reads the version from its manifest (``PdvJarIdentity``), refusing a JAR
   whose version is not the one the Tool Manager offered;
#. appends one ``pdv.launched`` event to the run's ``events.log`` and closes
   the log;
#. only then hands the command to the process service.

A failure in steps 1-3 starts nothing; a failure after step 4 leaves the
record, because PDV *was* launched. "Recorded whenever PDV is launched" is
therefore structural. The payload: ``run.id``, ``tool`` (``pdv``),
``tool.version``, ``pdv.jar.path``, ``pdv.jar.md5``, ``pdv.jar.sha256``,
``pdv.launch`` (``cli-figure`` or ``denovo-gui``), ``argv.count`` and
``argv.0`` .. ``argv.n-1`` after ``SecretRedactor.redactArgv``,
``process.working-directory``, ``process.environment`` (the variables the
command sets, sorted, redacted) and ``launch.<key>`` for the caller's context
(a figure: ``psm-id``, ``native-id``, ``spectrum-file``, ``pepxml``; a
window: the mzTab, its SHA-256 and its PSM count).

**What keeps it the only path.** ``PdvLaunchPathRuleTest`` in
``cometgui-archtests`` holds two ArchUnit rules, each graded against the
product and against a fixture class that must be rejected by the same rule
object:

* no class outside ``org.cometgui.workflow.pdv..`` calls the
  ``PdvFigures(ProcessRunner, Duration)`` constructor;
* no class outside ``org.cometgui.workflow.pdv..`` calls
  ``PdvController.open(Path, ProcessRunner, IntFunction, Listener)``.

Either, elsewhere, could hand PDV the application's own process service and
launch it with no record. The process service remains the only creator of
processes (``ProcessCreationRule``). Note that ``--only quality`` does **not**
run ArchUnit; ``cometgui-archtests`` runs under ``tests`` and
``scripts/build.sh``.

The CLI figure path
===================

``PdvFigures`` (P11-6, P11-7): the format gate first
(``PdvSpectrumSupport.check``; an unsupported source throws
``UnsupportedSpectrumFileException`` and nothing launches), then in an empty
work directory ``home/``, ``spectra.mgf`` (``PdvMgfWriter.writeEverySpectrum``),
``spectrum-ids.txt`` and ``figures/``, then::

    java -Duser.home=<home> -jar <PDV jar> -r <pepXML> -rt 2 -s <MGF> -st 1
         -i <ids file> -k s -o <figures> -ft png

through the process service under a timeout (``DEFAULT_TIMEOUT``, two
minutes), with an environment of exactly ``LANG=C.UTF-8``, ``HOME`` and the
display variables. The outcome is read from the files: every ``.png`` is
parsed by ``PngFigure`` (signature, ``IHDR``, every chunk's CRC, decoded
pixels above a non-white floor) and attributed to the requested id its name
ends with; ``error.txt`` is read. Every failure is named
(``PdvFigureException.Failure``: ``TIMED_OUT``, ``EXITED_NON_ZERO``,
``NO_FIGURE`` -- exit 0 and no figure is a failure, never a pass --
``INVALID_FIGURE``, ``PDV_REPORTED_ERRORS``).

``PsmFigures`` maps the PSM to its spectrum: ``SpectrumReference`` gives the
``-N`` base, scan and charge; the base names one of the run's spectrum files;
the scan must be carried as ``scan=<n>`` by exactly one spectrum's native id,
or the drawing is refused naming the scan. The work directory is a new
numbered directory under ``<run>/pdv/figures/``. PDV's CLI chooses the hit it
annotates from the pepXML; that it is the selected PSM's peptide is asserted
for a rank-1 target PSM only (``PdvFiguresRealTest``,
``PdvFirstUseUiTest``).

The live window
===============

``PdvWindows`` owns one window per run (``R-PDV-04``):

#. ``open`` refuses a run the engine is executing; shuts the run's existing
   window down **before** preparing the mzTab, because a regenerated export
   deletes the directory the running PDV reads and writes its ``.db`` into;
   calls ``PdvMzTabs.prepare``; reads the ``spectra_ref`` index
   (``PdvMzTabRefs``); and launches the derived controller with
   ``DenovoGuiCommand`` through ``PdvLaunches.forRun(..., DENOVO_GUI, ...)``.
   Working directory and home are ``<run>/pdv/window/`` and
   ``<run>/pdv/window/home/``; ``--tol`` is omitted and
   ``--hide-psm-table`` is off.
#. ``select(run, decoy, psmId)`` looks the PSM up by (decoy flag, ``PSMId``) --
   with ``decoy_search = 2`` a target and a decoy row can share a ``PSMId`` --
   and the controller sends the verbatim reference, debounced. The answer is a
   ``PdvSelection``: ``SELECTED`` (``200 selected``), ``UNKNOWN_TO_PDV``
   (``404``), ``REFUSED_BY_PDV`` (``400``/``503``/``500``), ``NOT_IN_MZTAB``
   (nothing sent), ``NOT_OPEN``, ``SUPERSEDED``, or no answer.
#. ``close(run)`` and ``close()`` shut down: ``/shutdown``, then
   ``RunningProcess.requestCancellation()`` on the process tree, then a
   bounded wait. The view-model calls ``close(run)`` when the Results
   section's run changes or clears; ``CometGuiApplication.stop()`` calls
   ``closeAllWindows()``.

Window states are ``STARTING``, ``READY``, ``FAILED`` (with the reason) and
``CLOSED``. ``PdvController.Timing.DEFAULT``: poll every 250 ms, ready
deadline 5 min (upstream's cap), debounce 60 ms, 5 s per request, 15 s for an
exit, 3 launches. A window that misses the deadline is stopped and reported.

**What the control API can and cannot prove** (P11-16; the spike's evidence
is in :doc:`pdv_mztab_spike`): ``/select`` answers ``200 selected`` when a row
with that exact ``spectra_ref`` exists and a redraw has been *queued* --
before anything is drawn; ``404 unknown ref``, ``400 missing ref``, ``503``
before ready. No endpoint reports what is displayed, and ``/shutdown`` stops
only the control server, not PDV. So gate item 5 is asserted through the API
(known ``200``, unknown ``404``, under a timeout) **and** by a test-side
observer, ``PdvDisplayObserver``, that runs PDV's own unmodified main class
and reads what it drew by reflection. No screen coordinates are used anywhere,
in production or tests (P11-10).

The mzTab
=========

``PdvMzTabs.prepare`` writes ``<run>/pdv/mztab/`` -- ``psms.mztab`` and
``ms_run-<n>.mgf`` per spectrum file with PSMs -- from the run's Percolator
target (and, when present, decoy) PSM tables; for each spectrum file in input
order the pepXML ``outputs/comet/<base>.pep.xml`` of the run that searched it
(the run itself, or the source run of a Percolator rerun) and
``<base>.decoy.pep.xml`` when it exists; and the spectrum file, which must
pass the format gate. It is **reused** only when the last ``mztab.written``
event records the same ``FORMAT_VERSION``, the same sources in the same order,
roles, paths and SHA-256s, and outputs still at their recorded paths with their
recorded SHA-256s; otherwise the directory is deleted and written again. The
sources are hashed again afterwards, and an export whose sources changed
meanwhile is deleted and refused.

The shape PDV requires (P11-15)
-------------------------------

``MztabImport`` at ``v2.7.0`` reads far less than mzTab 1.0 and contradicts it
in places, so the document is: ``MTD`` lines, one ``PSH``, ``PSM`` lines; no
blank or comment lines; no empty field (``null`` instead). ``MTD`` carries
``mzTab-version 1.0.0``, mode ``Summary``, type ``Identification``, a
``description`` stating the document is for PDV only and is not a deliverable
or interchange file, ``software[n]`` per Comet version plus Percolator,
``psm_search_engine_score[1..3]`` (Percolator score, q-value, PEP),
``fixed_mod``/``variable_mod`` from the pepXML's declarations (``CHEMMOD`` with
the signed ``massdiff`` text, site and position), ``colunit-psm`` for
retention time in seconds, and per ``ms_run`` its ``format`` (MGF),
``id_format`` and -- **last**, because PDV keys every ``ms_run[n]-*`` line to
the text after its last ``/`` -- ``location``, the MGF given to
``--spectrum``.

Column by column
----------------

One ``PSM`` line per Percolator row, target table then decoy table, each in
file order. "Text" means the source's own characters, copied.

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Column
     - Source
   * - ``sequence``
     - pepXML ``search_hit/@peptide`` (residues only)
   * - ``PSM_ID``
     - the row's 1-based ordinal in the mzTab
   * - ``accession``
     - Percolator ``proteinIds``, every protein in order, joined by ``;``
       (P11-14); ``null`` when Percolator lists none
   * - ``unique``, ``database``, ``database_version``, ``uri``, ``start``,
       ``end``
     - ``null``: no source states them (the pepXML's ``search_database`` names
       a file, not a database name and version, and is not reinterpreted)
   * - ``search_engine``
     - ``[MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]``
   * - ``search_engine_score[1]``, ``[2]``, ``[3]``
     - Percolator ``score``, ``q-value``, ``posterior_error_prob``, text
   * - ``modifications``
     - from the pepXML hit: ``<position>-CHEMMOD:<signed delta>``, comma-
       separated, position 0 for the N-terminus; ``null`` when none
   * - ``retention_time``
     - pepXML ``retention_time_sec`` text (seconds); ``null`` when absent --
       never taken from the spectrum file
   * - ``charge``
     - pepXML ``assumed_charge``
   * - ``exp_mass_to_charge``
     - the producing spectrum's selected-ion *m/z* text, from the spectrum file
   * - ``spectra_ref``
     - ``ms_run[n]:index=<k>``, ``k`` the 0-based ordinal of this row's own
       MGF entry (P11-11, P11-12)
   * - ``pre``, ``post``
     - pepXML ``peptide_prev_aa``, ``peptide_next_aa``
   * - ``opt_global_cv_MS:1003169_proforma_peptidoform_sequence``
     - the peptide with every modification as a bracketed signed mass:
       ``C[+57.021464]``, ``C[+57.021464][+15.994915]``,
       ``[+42.010565]-PEPTIDE``. PDV annotates from this column. Static
       deltas are written explicitly because PDV applies no fixed
       modification; the masses are the pepXML's text with a sign added
   * - ``opt_global_cv_MS:1002217_decoy_peptide``
     - ``1`` for a decoy-table row, ``0`` for a target
   * - ``opt_global_percolator_PSMId``
     - Percolator ``PSMId``, verbatim
   * - ``opt_global_percolator_score``, ``_q_value``,
       ``_posterior_error_prob``
     - the same three Percolator values again, under unambiguous names
   * - ``calc_mass_to_charge``
     - **omitted**: no source states it, and PDV's import dies on ``null``
       there (spike R9); PDV computes the theoretical *m/z* itself

Joining the sources (P11-13, P11-14)
------------------------------------

A Percolator row is joined to **exactly one** pepXML hit and **exactly one**
spectrum:

* its ``PSMId`` ``<base>_<scan>_<charge>_<rank>`` names the source by its
  absolute ``-N`` base, and the hit by (``start_scan``, ``assumed_charge``,
  **1-based position of the hit in its** ``spectrum_query``) -- in the
  ``.decoy.pep.xml`` for a decoy-table row when the search wrote decoys
  separately. **The rank is the position, not** ``hit_rank``: Comet gives tied
  hits one ``hit_rank`` while its PIN numbers them 1..n (81 of 2,130 ``_3`` and
  99 of 1,767 ``_4`` K562 target PSMs differ);
* the Percolator peptide is then checked against that hit as values --
  residues, flanks and modifications -- and a mismatch refuses the export;
* the query's ``spectrumNativeID`` names the spectrum, which must be exactly
  one MS2 spectrum of the file and, if its native id carries a scan number,
  must carry the PSM's.

**Accessions come from Percolator** (P11-14): the Results section shows
Percolator's ``proteinIds``, so the mzTab transcribes them. The pepXML drops
``DECOY_`` on alternative proteins where the PIN and Percolator keep it (31
``_3`` and 12 ``_4`` K562 PSMs); that divergence is documented, not
reconciled.

**Terminal modifications.** Comet writes ``mod_nterm_mass`` and
``mod_cterm_mass`` as the mass of the whole modified terminal group, never a
delta. A delta is transcribed only when the search declared **exactly one**
N-terminal modification and the hit's ``mod_nterm_mass`` text equals that
declaration's ``mass`` text; the delta is then the declaration's ``massdiff``
text. Two or more N-terminal declarations make a matching total no proof, so
the hit is refused. **Every C-terminal modification is refused**: PDV's
peptide parser has no C-terminal form.

One MGF entry per row (P11-11, P11-12)
--------------------------------------

PDV numbers mzML spectra by 1-based file position through msftbx, while the
pepXML carries the instrument scan; they differ for every spectrum of a
scan-range subset, and the honest ``scan=`` reference then draws nothing. And
PDV keeps only the first mzTab row per verbatim ``spectra_ref``
(``MztabImport.java:425``, ``putIfAbsent``): in K562 ``_3``, 1,404 of 2,130
target PSMs share a spectrum with an earlier row. So each ``ms_run`` gets its
own MGF with **one entry per PSM row**, each an exact copy of the producing
spectrum (``PdvMgfWriter.writeOnePerRow``) titled with its native id, and the
row's reference is that entry's ordinal. Peaks are written to round-trip
exactly: a 64-bit value as its shortest round-trip decimal, a 32-bit value as
the float's exact decimal expansion (PDV parses doubles); NaN and infinity are
refused. PDV renames the *k*-th repeat of a title ``<title>_k``.

Refusals
--------

``PdvMzTabException`` names the PSM (where there is one), the source files and
the reason; on any refusal **nothing exists at the final path** -- the whole
document is written in a hidden staging directory beside it, read back
(one line per row, references ``0..n-1`` per ``ms_run``, each MGF exactly
``n`` entries), and only then renamed into place.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - ``Reason``
     - When
   * - ``NO_PSMS``
     - the Percolator tables hold no PSM (PDV cannot open an empty set)
   * - ``NO_SPECTRUM_REFERENCE``
     - a ``PSMId`` is not Comet's ``<base>_<scan>_<charge>_<rank>``
   * - ``UNKNOWN_BASE``
     - a ``PSMId``'s base names none of the run's Comet outputs
   * - ``DUPLICATE_PSM``
     - two Percolator rows name the same pepXML hit
   * - ``NO_PEPXML_HIT``
     - no hit at the ``PSMId``'s scan, charge and position
   * - ``AMBIGUOUS_QUERY``
     - two ``spectrum_query`` elements share the scan and charge
   * - ``PEPTIDE_MISMATCH``
     - Percolator's peptide is not the hit's: residues, flanks or
       modifications
   * - ``UNREPRESENTABLE_MODIFICATION``
     - a C-terminal modification, or a terminal mass no single declaration
       states
   * - ``SEQUENCE_VARIANT``
     - the hit carries a PEFF sequence variant
   * - ``NO_NATIVE_ID``
     - the query has no ``spectrumNativeID``
   * - ``NO_SPECTRUM`` / ``AMBIGUOUS_SPECTRUM``
     - no MS2 spectrum, or more than one, has that native id
   * - ``SPECTRUM_MISMATCH``
     - the spectrum's native id carries another scan number
   * - ``UNREPRESENTABLE_SPECTRUM``
     - the spectrum cannot be written to an MGF exactly
   * - ``NOT_A_DECIMAL``
     - a score, q-value or PEP is not a decimal number as written
   * - ``UNREPRESENTABLE_TEXT``
     - a value would be empty, hold a tab or line break, or read as ``null``
       (including a protein containing ``;``, and an unusable Comet version)
   * - ``UNREADABLE_SOURCE``
     - a source is missing, unreadable or refused by its reader
   * - ``SOURCE_CHANGED``
     - a source changed while it was being exported
   * - ``INCOMPLETE``
     - the written document does not hold exactly the PSMs read (the
       exporter's own read-back)

Memory and size, measured by unit 5 on 2026-10-10 (1,000,450 PSMs over
200,200 spectra, an MGF source): 54-65 s, live heap peak 92 MB, completes at
``-Xmx128m``; output 278 MB mzTab and 1.78 GB MGF -- the MGF grows with PSMs,
not spectra. The exporter keeps about 40 bytes per PSM in primitive arrays.
``PdvMzTabRefs`` holds the whole reference index in memory and has **not**
been measured at that size.

The fidelity suite (P11-9)
==========================

``R-PDV-03`` is a gate, so the mzTab is graded by comparison with the
sources, never with the exporter's own record. The suite,
``cometgui-tools/src/test/java/org/cometgui/tools/pdv/fidelity/``, was written
by a different agent from the exporter, and **its readers share no code with
the exporter**: ``MzMlFile``, ``MzTabFile``, ``PepXmlFile``,
``PercolatorTable``, ``MgfEntries``, ``Peptidoforms`` and ``FidelityCheck``
import nothing from ``org.cometgui``. The only project types the package
touches are the exporter's entry points and the test support that runs real
tools. ``IndependenceGuardTest`` enforces this mechanically and proves it can
fail on each forbidden form.

.. list-table::
   :header-rows: 1
   :widths: 28 72

   * - Test
     - What it proves
   * - ``K562FidelityTest``
     - On the real K562 ``_3`` + ``_4`` run (Comet 2026.02.2 pepXML,
       Percolator 3.07.1 target and decoy tables, two subset mzML; 6,670
       rows): every PSM exactly once and none invented, counted both sides;
       score, q-value, PEP, charge, observed *m/z*, retention time, accessions,
       sequence and flanks as source text; modifications as parsed (position,
       delta) values against the pepXML and against Percolator's peptide;
       Comet's static ``+57.021464`` on every cysteine; ``null`` where no
       source speaks; **position differs from scan for every producing
       spectrum** (shown first), every ``spectra_ref`` resolved as PDV
       resolves it to the producing spectrum with peaks compared bit for bit;
       and that a reference by file position, by scan, or to the first row of
       a shared spectrum would be caught.
   * - ``ConstructedFidelityTest``
     - What K562 lacks: ``decoy_search = 2`` with shared ``PSMId`` values, an
       N-terminal modification, a residue with a static and a variable delta,
       ``15.994900``, several proteins, tied hits (position not
       ``hit_rank``), a query without a retention time (``null``), 32-bit
       peaks.
   * - ``RealCometFidelityTest``
     - The real Comet 2026.02.2 under settings K562 did not use: separate
       decoys with an N-terminal acetyl and a static+variable cysteine; and
       static terminal labels. Percolator is not run; its tables are written
       from the PIN's own columns, synthetic in their numbers only, which the
       suite compares as text.
   * - ``UnrepresentableTest``
     - Gate item 8: five unfaithful PSMs injected into **copies** of the real
       sources (a row with no hit, a C-terminal modification, a peptide and a
       modification disagreement, a missing spectrum), each refusing the export
       naming the PSM, reason and file, with nothing left at the final path;
       each injection shown to have landed, and the uninjected copies shown
       to export.
   * - ``RealPdvFidelityTest``
     - The real PDV 2.7.0 accepts the K562 export, reaches ``/ready`` and
       selects by its own ``spectra_ref`` every row of the most-shared
       spectrum, decoy rows and second-``ms_run`` rows; an unknown reference
       is unknown to it.
   * - ``PdvMzTabsFidelityTest`` (``cometgui-workflow``)
     - The run-level path: a finished run laid out from the K562 sources,
       ``PdvMzTabs.prepare``, then the same ``FidelityCheck`` -- **compiled at
       test time from** ``cometgui-tools``' **test sources**, not copied, so
       there is one suite.

Unit 6 also injected three defects into the exporter (score and q-value
swapped, a charge written wrongly, flanks swapped); each was caught by this
suite alone. Not exercised by the suite: MGF and mzXML *source* files, and real
Percolator output for the constructed searches.

Test support
============

**A display.** PDV cannot run headless (its CLI extends ``JFrame``;
``-Djava.awt.headless=true`` makes it worse). ``ProjectXvfb`` starts the
project-local Xvfb from ``tools/x11-bookworm-20260829/`` with the fonts from
``tools/fontstack-bookworm-20260829/`` -- never the host's X -- through the
process service, letting Xvfb choose its display (``-displayfd``) so PIT's
parallel JVMs cannot collide. Xvfb runs ``/usr/bin/xkbcomp`` by a compiled-in
path, so the ``xkbcomp`` path shim
``scripts/feasibility/gui-spike/xkbcomp-path-shim.c`` is compiled with the
**host's** ``gcc`` into the build directory. Anything missing **fails** the
test, naming it; real-PDV tests never skip (P11-5). ``cometgui-app`` has its
own copy of ``ProjectXvfb``, because test classes are not shared between
modules.

**PIT and orphans.** PIT ends a minion whose mutant timed out with
``Runtime.halt``, so no ``@AfterAll`` or shutdown hook runs; two targeted PIT
runs once left 60 Xvfb servers behind. So the ``cometgui-tools`` test support
runs Xvfb and PDV under the host's ``timeout`` (coreutils, found on the
``PATH``): Xvfb 10 minutes (``ProjectXvfb.LIFETIME``), PDV 5 minutes
(``PdvMzTabRealPdvTest``, ``PdvControllerRealPdvTest``); ``PdvLiveWindowUiTest``
puts its PDV under ``timeout`` too. **The** ``cometgui-app`` **copy of**
``ProjectXvfb`` **has no such cap.** Halted minions also leave ``target/mzt*``
directories until ``mvn clean``. On the development host PID 1 does not reap,
so ended Xvfb processes remain as ``<defunct>`` zombies (57 at unit 7's
sign-off); they hold no resources but a process-table slot.

**PDV's home.** Tests give PDV a temporary ``HOME`` (P11-17), as the product
does, so nothing is written to the developer's ``~/.compomics`` or ``~/.java``.

**Files the tests read outside their module** (relevant to the ``tests``
harness sandbox): ``scratch/phase05/artefacts/`` (the PDV 2.7.0 zip, Comet,
Percolator 3.07.1), ``scratch/fixture/`` (the K562 ``_3``/``_4`` mzML and the
FASTA), ``scratch/scientific-path/comet/`` and
``scratch/scientific-path/percolator-3.07.1/``, ``manifests/tools.json``, the
X11 and font stacks under ``tools/``, the shim source under ``scripts/``, the
host's ``gcc`` and ``timeout``, and -- for ``PdvMzTabsFidelityTest`` --
``cometgui-tools/src/test/java/org/cometgui/tools/pdv/fidelity/``. K562 data
and everything derived from it stay under ``scratch/`` or the build directory
and are never committed (``D-006``).

**Where the acceptance evidence is.** :doc:`traceability` maps ``AC-VIS-01``
to ``PdvFirstUseUiTest``; ``AC-VIS-02`` and ``AC-VIS-03`` to
``PdvFiguresRealTest`` (and the first-use test's figure); ``AC-VIS-04`` to
``PdvLiveWindowUiTest`` and ``PdvControllerRealPdvTest``; ``AC-VIS-05`` to the
fidelity suite and ``PdvMzTabsFidelityTest``.

What has only run on Linux
==========================

Every real-PDV test is ``@EnabledOnOs(LINUX)``, and the project-local X11
stack is Linux-only. Specifically **unverified**:

* **Windows and macOS**: PDV has never been launched by CometGUI there.
  ``PdvDisplay`` passes no display variables on those platforms, on the
  assumption that PDV reaches the desktop without them; that is untested.
* ``XAUTHORITY`` handling on a real Linux desktop (the tests use the
  project-local Xvfb, which needs no cookie).
* **MGF and mzXML source files** end to end (mzXML is refused by design; MGF
  is tested on constructed files only).
* **What the CLI annotates for a PSM that is not rank 1**.
* **The bind-race relaunch** against real PDV (proved against a fake only),
  and a foreign server answering ``/ready`` on the reserved port, which would
  be trusted.
* **The displayed spectrum** is proved by the in-JVM observer, not by the GUI
  test of the product launch; the two run PDV's same main class, by two
  launches.
* **Scale in the window**: ``PdvMzTabRefs`` and PDV's own import at a million
  PSMs.
