.. _dev-pdv-mztab-spike:

============================================================
PDV ``denovo-gui`` and a Comet + Percolator mzTab: the spike
============================================================

:Phase: 11 -- PDV Integration
:Work unit: 1 of 9 -- the spike the phase document makes the binding first step
:Date: 2026-10-10
:Script: ``scripts/spikes/pdv_mztab_spike.py`` (observer:
         ``scripts/spikes/pdv_mztab_spike/PdvObserver.java``)
:Status: Evidence. Throwaway code; nothing here is product code. It decides
         nothing that belongs to a ``D-`` item.

.. contents:: Contents
   :depth: 2
   :local:

Verdict
=======

**ACCEPTED WITH CONDITIONS.** PDV 2.7.0's ``denovo-gui`` mode, unmodified,
imports an mzTab generated from Comet + Percolator 3.07.1 results, reaches
``/ready`` by polling, and on ``/select`` draws the PSM's own spectrum with
the right peptide, charge and modification masses. This was shown on the real
K562 ``_3`` run, on a spectrum file where 1-based file position and scan
number differ for every spectrum, for 13 hand-picked PSMs and for all 2,130
K562 ``_3`` target PSMs in one document. The route stands. The conditions are
real, and three of them decide how later units are built:

#. **The spectrum file must be re-encoded as MGF, or the** ``spectra_ref``
   **must say** ``scan=<1-based position>``. With a subset mzML and the honest
   ``spectra_ref`` (the instrument scan number), PDV answers ``200 selected``
   and draws **nothing**. See `Which spectra_ref lands on the right spectrum`_.
#. **One spectrum, one selectable PSM.** PDV keeps the *first* row for each
   verbatim ``spectra_ref`` (``putIfAbsent``). In K562 ``_3`` 668 of the 726
   spectra with a target PSM carry more than one, so **1,404 of 2,130 target
   PSMs cannot be reached through** ``/select``. ``R-PDV-02`` ("display any
   PSM in the result set") cannot be met through this API as it stands. See
   `Duplicate spectra_ref`_.
#. **The control API cannot report what is displayed.** ``200 selected``
   means "row found and a render queued", not "spectrum drawn". Gate item 5
   ("selecting a known PSM changes PDV's displayed spectrum -- asserted
   through the control API") cannot be asserted literally through the API.
   See `The control API`_.

**Analytics:** none observed or reachable from the ``denovo-gui`` path. A
Google Analytics sender is *shipped* inside a bundled library but is never
referenced by PDV's own code. See `Network and analytics`_.

What was run
============

Inputs (K562, ``D-006``: on disk under ``scratch/``, never committed, and
nothing derived from them is committed):

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Input
     - Facts the script re-established
   * - ``scratch/scientific-path/comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml``
       (and ``_4``)
     - Comet 2026.02.2 pepXML; 728 ``spectrum_query`` elements in ``_3``
   * - ``scratch/scientific-path/percolator-3.07.1/psms.target.txt``
     - 2,130 ``_3`` and 1,767 ``_4`` target PSMs
   * - ``scratch/scientific-path/mzml-lf/..._3.mzML`` (the LF copy Comet
       searched; SHA-256 ``a562f6e6...54da``)
     - 801 spectra, 728 MS2, index 0..800 = scans 11187..11987; position
       differs from scan for **all 801**
   * - ``..._4.mzML`` (SHA-256 ``602aad75...5c82``)
     - 671 spectra, 610 MS2, scans 9938..10608; position differs for all 671
   * - ``scratch/fixture/..._3.mzML``
     - the as-fetched copy; differs from the LF copy only by 49,782 CRLF line
       endings (byte-identical after ``\r\n`` -> ``\n``)
   * - ``scratch/pdv/PDV-2.7.0.zip``
     - SHA-256 ``58f95a56...7982``, 103,407,417 bytes -- matches all five
       ``manifests/tools.json`` rows; all 217 unpacked files match their zip
       entries; JAR SHA-256 ``fc876310...7908``
   * - PDV source, ``github.com/wenbostar/PDV`` tag ``v2.7.0``
     - commit ``aaf38f971ea3edf9aa6ed1490448fd92cb2ca1fd``, cloned read-only
       into ``scratch/pdv-src``. The shipped ``MztabImport.class`` line-number
       table maps lines 273, 279, 295, 399, 405, 425 and 518 to the same
       statements as the tagged source, and the string constants of
       ``MztabImport`` and ``PdvControlServer`` match it, so the source quotes
       below describe the shipped JAR.

Command::

   . tools/env.sh
   python3 scripts/spikes/pdv_mztab_spike.py            # all 11 experiments
   python3 scripts/spikes/pdv_mztab_spike.py --only R5-mgf-index

It verifies the artefact, reads the sources with its own readers, writes one
throwaway mzTab per experiment, starts the project-local Xvfb from
``tools/x11-bookworm-20260829/`` exactly as ``scripts/feasibility/pdv_spike.sh``
does, and runs PDV once per experiment under a hard timeout. Every experiment
has a written expectation; any deviation is a ``FAIL`` and the script exits 1.
It finishes by checking that no PDV or Xvfb process it started is still alive.
Outputs go to ``scratch/pdv-mztab-spike/`` (mzTabs, MGFs, per-run stdout and
stderr, ``results.json``). Run on 2026-10-10: **289 checks passed across 11
experiments, exit 0**, about four minutes.

The production-shaped launch (experiment R1) is exactly::

   java -Duser.home=<isolated> -jar scratch/pdv/PDV-2.7.0/PDV-2.7.0.jar \
       denovo-gui --mztab <run>/R1-jar-mgf.mztab \
       --spectrum <run>/spectra/20100614_Velos1_TaGe_SA_K562_3.mgf \
       --port <ephemeral>

Knowing what PDV drew, without the screen
-----------------------------------------

No control endpoint reports the displayed spectrum, and screen-coordinate
automation is forbidden. So the experiments other than R1 launch PDV through
``PdvObserver``, a test-side class that calls PDV's **own unmodified**
``PDVGUI.gui.PDVMainClass.main`` with the same ``denovo-gui`` arguments in the
same JVM, listens for the ``border`` property change PDV fires inside
``updateSpectrum``, and reads by reflection what PDV handed its plot:
``SpectrumMainPanel.currentSpectrum`` (title, file, precursor m/z, every
peak) and ``spectrumIdentificationAssumption`` (peptide, charge, each
modification's site and the mass PDV's PTM factory holds for it), plus the
selected row key and the panel's border title. Nothing in PDV is patched,
subclassed or clicked.

Each drawn spectrum was then compared with the mzML spectrum the PSM came
from, read independently: precursor m/z, peak count, total intensity and base
peak m/z; and the drawn peptide with the pepXML hit: sequence, charge,
modification sites and masses (exact), and PDV's theoretical m/z against
``(calc_neutral_pep_mass + z * 1.007276) / z`` within 0.002.

The PSMs used
-------------

From K562 ``_3``, chosen by rule from Percolator's file order: unmodified
charge 2; unmodified charge 3; charge 4; charge 5; one ``M[15.9949]``; two
``M[15.9949]``; a multi-protein PSM; a cysteine PSM (Comet's static
``+57.021464``, which Percolator's peptide string does not show); cysteine plus
oxidation; Phase 00's reference spectrum, scan 11892 (index 705, position
706); both target PSMs of one spectrum; and the lowest-scoring target. Three
``_4`` PSMs for the multi-file runs. R11 used every ``_3`` target PSM.

The required mzTab shape
========================

What ``MztabImport`` actually requires is far narrower than mzTab 1.0, and in
places contradicts it. Quoted from
``src/main/java/PDVGUI/fileimport/MztabImport.java`` at ``v2.7.0``.

**File layout: ``MTD`` lines, one ``PSH`` line, ``PSM`` lines -- nothing
else.** The header is simply the first line not starting ``MTD``
(``getParameters``, lines 689-728); every later line not starting ``MTD`` or
``PSH`` is parsed as a PSM (line 264)::

   if(!line.startsWith("MTD") && !line.startsWith("PSH")){

So a protein (``PRH``/``PRT``), peptide (``PEH``/``PEP``), ``COM`` or blank
line before or among the PSMs breaks the import. Every ``MTD`` line needs a key
and a value (``line.split("\t")[2]``, line 730).

**Columns are found by exact header name** (lines 694-726): ``spectra_ref``,
``sequence``, ``charge``, ``modifications``, ``calc_mass_to_charge``, and any
header containing ``proforma``. An absent one silently defaults to column 0.
Every other column is stored verbatim as display text in a SQLite column named
after the header, so header names must be unique.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Column
     - How PDV uses it
   * - ``spectra_ref``
     - **Required.** The spectrum locator (next section) and, verbatim, the
       ``/select`` key.
   * - ``sequence``
     - **Required.** Used for annotation only if no ``proforma`` column holds
       a value.
   * - ``charge``
     - **Required**, numeric: ``(int) Double.parseDouble(...)`` (line 295).
   * - a column whose header contains ``proforma``
     - If present and not ``null``, *replaces* ``sequence`` as the source of
       the peptide and its modifications (lines 289-294). The spike used
       ``opt_global_cv_MS:1003169_proforma_peptidoform_sequence``, keeping
       ``sequence`` the plain mzTab sequence.
   * - ``modifications``
     - **Display only** whenever the peptide carries bracketed mods (line
       341). The standard ``5-UNIMOD:35`` / ``CHEMMOD`` form is never parsed
       for annotation.
   * - ``calc_mass_to_charge``
     - Optional, but if present every value must parse as a double or be
       ``nan``: ``Double.valueOf(values[mzIndex])`` (line 409). The mzTab
       ``null`` **kills the import** (R9, below). If absent, PDV computes the
       theoretical m/z itself (line 412). The spike omitted the column.
   * - everything else (``PSM_ID``, ``accession``, scores, RT,
       ``exp_mass_to_charge``, ``opt_*``)
     - Opaque display text. ``exp_mass_to_charge`` and ``retention_time``
       are **not** used to draw; the precursor comes from the spectrum file.

**No empty fields.** Java's ``String.split("\t")`` drops trailing empty
strings, and lines 448-452 then read ``values[index]`` for every header column,
so a row whose last fields are empty throws. Write ``null``.

**Modifications: bracketed signed masses on the residue.** ``get_modification``
(lines 526-593) turns ``M[+15.994915]`` into ``"+15.994915 of M@4"``. The name
matches no PTM-factory entry, so ``registerableNtermDelta`` (lines 635-646) --
despite its name, applied to *any* residue -- parses the stem as a double and
``addModification`` registers a PTM with exactly that mass. Observed in R5::

   border  "NH2-GGHM<+15.994915 of M>DDGGYSMNFNMSSSR-COOH"
   peptide GGHMDDGGYSMNFNMSSSR, charge 3,
           modifications [{"name": "+15.994915 of M", "site": 4, "mass": 15.994915}]

Consequences, all observed:

* **Static modifications must be written explicitly.** ``openDenovoFromCli``
  never applies fixed mods to a sequence; every cysteine needs
  ``C[+57.021464]``. With it, PDV's theoretical m/z matched Comet's
  ``calc_neutral_pep_mass`` for every cysteine PSM tested.
* **Use the pepXML's mass text, not Percolator's.** Percolator writes
  ``M[15.9949]``; the pepXML ``mod_aminoacid_mass`` carries
  ``variable="15.994915"`` / ``static="57.021464"``. The drawn masses equal
  the pepXML values exactly.
* A *named* mod (``M[Oxidation]``) would be resolved against PDV's PTM factory
  and drawn with the factory's mass, not the source's. Masses avoid that.
* **There is no C-terminal form.** A ``-`` after the residues is rejected
  (``Unexpected character``, line 564). N-terminal ``[+mass]-PEPTIDE`` is
  accepted (line 539-551) but was not exercised: this search has no terminal
  mods. An exporter must fail loudly on a C-terminal mod.

**``ms_run[n]-location`` must be the last ``ms_run[n]-*`` line.** Line 261
maps *every* MTD key that starts ``ms_run`` to the text after the last ``/``
of its value, keyed on the part before the first ``-``::

   spectrumFileMap.put(values[1].split("-")[0],
       values[2].split("/")[values[2].split("/").length-1]);

so a following ``ms_run[1]-format`` overwrites the file name. R10 wrote
location first: PDV answered ``200 selected`` and drew nothing, border
``No spectrum to display for [MS, MS:1000768, Thermo nativeID format, ]_cus_181``.
The **basename** of the location must equal the basename of the ``--spectrum``
file (PDV keys loaded files by ``sf.getName()``, ``PDVMainClass.java:514``);
two spectrum files with the same basename in different directories collide.

The shape the spike wrote, which every passing experiment used::

   MTD  mzTab-version                1.0.0
   MTD  mzTab-mode / mzTab-type / description / psm_search_engine_score[1..3]
   MTD  ms_run[1]-format             [MS, MS:1001062, Mascot MGF format, ]
   MTD  ms_run[1]-id_format          [MS, MS:1000774, multiple peak list nativeID format, ]
   MTD  ms_run[1]-location           file:///<abs>/20100614_Velos1_TaGe_SA_K562_3.mgf
   PSH  sequence PSM_ID accession unique database database_version search_engine
        search_engine_score[1] [2] [3] modifications retention_time charge
        exp_mass_to_charge uri spectra_ref pre post start end
        opt_global_cv_MS:1003169_proforma_peptidoform_sequence opt_global_percolator_PSMId
   PSM  FNLTYVSHDGDDK 1 sp|P26639|SYTC_HUMAN null null null
        [MS, MS:1002251, Comet, ]|[MS, MS:1001490, Percolator, ]
        2.37114 0.00112905 9.27998e-07 null 4797.0 2 755.841003417969 null
        ms_run[1]:index=163 R K null null FNLTYVSHDGDDK
        20100614_Velos1_TaGe_SA_K562_3_11367_2_1

(tab-separated; one row per PSM; scores, RT and m/z are the source's text;
multiple accessions were joined with ``;`` in one row, which PDV accepts as
text.)

Which ``spectra_ref`` lands on the right spectrum
=================================================

How PDV resolves it
-------------------

``MztabImport.java`` lines 272-281 parse the reference by spectrum *type*,
which ``openDenovoFromCli`` takes from the **first** ``--spectrum`` file's
extension alone (``PDVMainClass.java:466``: ``.mgf`` -> ``mgf``, anything
else -> ``mzml``)::

   if (Objects.equals(spectrumFileType, "mgf")){
       spectrumTitle = values[spectrumTitleIndex].split(":index=")[1];
       ...
   } else {
       // mzML
       spectrumTitle = values[spectrumTitleIndex].split(":.*scan=")[1];

and line 399 then requires the remainder to be an integer::

   currentMatch.setSpectrumNumber(Integer.valueOf(spectrumTitle));

At display time (``PDVMainClass.java``):

* **MGF** (line 4800): ``spectrumFactory.getSpectrumTitle(spectrumFileName,
  Integer.parseInt(spectrumKey)+1)`` -- the 0-based ``index=`` is the
  spectrum's **ordinal in the MGF**, which is the mzTab 1.0 meaning of
  ``index=`` for MGF.
* **mzML** (line 4728): ``currentScans.getScanByNum(scanNumber)`` -- msftbx's
  scan "number", which Phase 00 showed is the **1-based position in the
  file**, not the instrument scan. Line 4773 builds the precursor from
  ``precursorInfo.getMzTarget()``.

What was observed
-----------------

.. list-table::
   :header-rows: 1
   :widths: 9 36 55

   * - Run
     - ``spectra_ref`` form, spectrum file
     - Observed
   * - R2
     - ``ms_run[1]:controllerType=0 controllerNumber=1 scan=11892`` -- the
       pepXML ``spectrumNativeID``; subset mzML
     - Import and ``/ready`` fine; **every** ``/select`` -> ``200 selected``;
       **no spectrum drawn** for any of the 13 (border ``No spectrum to
       display for ..._cus_11367``). ``getScanByNum(11367)`` is null.
   * - R3
     - ``ms_run[1]:index=705`` (mzML 0-based index); subset mzML
     - **Import dies**: ``ArrayIndexOutOfBoundsException: Index 1 out of
       bounds for length 1 at MztabImport.parseMztabFile(MztabImport.java:279)``.
       ``/ready`` answers ``503 loading`` for ever (45 s observed);
       ``/select`` -> ``503 not ready``. PDV stays running.
   * - R4
     - ``ms_run[1]:scan=706`` (``scan=`` carrying the **1-based position**);
       subset mzML
     - **Right spectrum, every PSM.** Scan 11892's PSM drew position 706:
       precursor 504.230010986328, 136 peaks, total intensity and base peak
       equal to the mzML's scan 11892 (see the precursor caveat below).
   * - R5
     - ``ms_run[1]:index=640`` (ordinal in the MGF of MS2 spectra); MGF
     - **Right spectrum, every PSM**, and the drawn spectrum's title is the
       mzML native id itself, e.g. ``controllerType=0 controllerNumber=1
       scan=11460``.
   * - R8
     - as R4, on the as-fetched **CRLF** fixture mzML
     - Right spectrum, every PSM: msftbx is indifferent to the CRLF damage
       that makes Comet exit 249.

**The verdict on the subset mzML:** ``denovo-gui`` *can* use it, but only with
``scan=<1-based position>``, a ``spectra_ref`` whose text contradicts the
mzTab meaning of ``scan=`` and which PDV shows verbatim in its table.
The honest form draws nothing while ``/select`` says ``selected``. MGF with
``index=<ordinal>`` is standard-conformant and self-checking (the drawn title
*is* the native id), so **MGF re-encoding is recommended, not strictly
needed**. That choice is for the phase orchestrator; both are proven here.

**Precursor caveat on the mzML route.** msftbx's ``getMzTarget()`` is the
**isolation window target** (MS:1000827), not the **selected ion m/z**
(MS:1000744). For scan 11288 PDV's precursor was 572.034118652344 while the
selected ion is 571.783325195313; 4 of the 13 spectra differ this way. Peaks,
intensities and base peak are unaffected, and the MGF route uses the selected
ion (``PEPMASS``). It affects only how PDV labels and annotates the precursor
peak.

Phase 00's converter writes ``PEPMASS`` to 6 and peaks to 5 (m/z) and 3
(intensity) decimals, so the MGF route compared within 2e-5 m/z and a 1e-5
relative intensity sum. A product MGF writer that must "never alter" peaks
should write full precision.

Duplicate ``spectra_ref``
-------------------------

Line 425 indexes the ``/select`` key with ``putIfAbsent``::

   pdvMainClass.spectraRefToSpectrumIndex.putIfAbsent(values[spectrumTitleIndex], String.valueOf(lineCount));

Percolator 3.07.1 keeps every charge state and up to five hits per spectrum,
so in K562 ``_3`` 2,130 target PSMs share 726 distinct spectra and 668 of
those spectra carry more than one. Observed in R5: ``..._11214_2_4`` shares
``ms_run[1]:index=24`` with ``..._11214_2_1``; ``/select`` answers ``200
selected`` and PDV draws row 2 (``SSHYDELLAAEAR``), never this PSM's row 11.
In R11, **1,404 of 2,130** target PSMs are unreachable this way. Writing two
textually different references that parse to the same spectrum would work
around it, and would be exactly the kind of invented identifier ``R-PDV-03``
forbids; this is a question for the orchestrator, not an exporter detail.

Multiple spectrum files
=======================

``--spectrum f1,f2`` with ``ms_run[1]``/``ms_run[2]`` works for both
formats. R6 (two MGFs) and R7 (two subset mzMLs, ``scan=<position>``): all 16
PSMs, 13 from ``_3`` and 3 from ``_4``, were drawn on their own spectrum from
the right file. Limits from the source: the format is decided by the first
file only, so **mixed MGF/mzML runs are unsupported**; and **mzXML is not
supported by** ``denovo-gui`` at all -- a non-``.mgf`` first file is opened
with ``MZMLFile`` (``PDVMainClass.java:507``). mzXML was not executed.

The control API
===============

From ``src/main/java/PDVGUI/gui/utils/PdvControlServer.java``, bound to
``InetAddress.getLoopbackAddress()`` (line 46), every response a short
``text`` body:

.. list-table::
   :header-rows: 1
   :widths: 30 34 36

   * - Request
     - Source (line)
     - Observed
   * - ``GET /ready`` before the port binds
     - --
     - connection refused (the server starts on its own thread after the
       window, ``PDVMainClass.java:454``)
   * - ``GET /ready`` while importing
     - ``503 loading`` (66)
     - ``503 loading``
   * - ``GET /ready`` once imported
     - ``200 ready`` (66); requires ``loadComplete`` and a non-empty table
       (``PDVMainClass.java:578-583``)
     - ``200 ready`` after 2.0-2.8 s; 2,130 rows: 2.8 s
   * - ``GET|POST /select?ref=<known>``
     - ``200 selected`` (83)
     - ``200 selected``; ``POST`` (CasanovoGUI's verb) also accepted
   * - ``/select?ref=<unknown>``
     - ``404 unknown ref`` (85)
     - ``404 unknown ref``; nothing redrawn
   * - ``/select`` with no ``ref``
     - ``400 missing ref`` (78)
     - ``400 missing ref``
   * - ``/select`` before ready
     - ``503 not ready`` (87)
     - ``503 not ready``
   * - ``GET /shutdown``
     - ``200 bye`` then ``server.stop(0)`` (96-97)
     - ``200 bye``; port then refuses; **the PDV process and its window keep
       running**

What ``200 selected`` proves, and what it does not
--------------------------------------------------

``selectBySpectraRef`` (``PDVMainClass.java:593-639``) looks the verbatim
string up, finds the row's page, queues the selection with
``SwingUtilities.invokeLater`` and **returns** ``SELECT_OK`` **before
anything is drawn** (line 638). Whether a spectrum is then found is decided
later, on the EDT, and a miss only changes the panel border
(``"No spectrum to display for "``, line 4873). R2 and R10 are the proof:
``200 selected`` for every PSM and no spectrum drawn.

* ``200`` vs ``404`` reliably distinguishes "a row has this exact
  ``spectra_ref``" from "no row does".
* **No endpoint reports what is displayed.** There are three endpoints and
  none returns state beyond ready/not ready.
* ``/shutdown`` does not exit PDV. Lifecycle (``R-PDV-04``) must terminate the
  process itself; PDV exited with status 143 to ``SIGTERM`` in every run.

So for gate item 5, the API can assert "PDV started on an ephemeral port,
``/ready`` reached by polling, ``/select`` of a known PSM answered ``200``
and of an unknown one ``404``" -- and no more. Proving the *displayed*
spectrum changed needs something outside the API: this spike's in-JVM
observer is one way that uses no screen coordinates. Which evidence the gate
accepts is the orchestrator's call.

Network and analytics
=====================

**Nothing observed; nothing reachable from this path.**

* **Runtime.** Every PDV process's own sockets (``/proc/<pid>/fd`` matched to
  ``/proc/<pid>/net/{tcp,tcp6,udp,udp6}``) were sampled every 50 ms for the
  whole of every run. The only sockets ever seen were the loopback control
  server and the probe's own loopback connections (R1:
  ``::ffff:127.0.0.1:41547`` listening and one ``127.0.0.1`` peer). In the
  observer runs a logging ``InetAddressResolverProvider`` recorded every
  host-name lookup: exactly one per run, of this machine's own host name, by
  log4j (``org.apache.logging.log4j.core.util.NetUtils.getLocalHostname``).
  No other name was resolved.
* **Shipped code.** Every class in ``PDV-2.7.0.jar`` and its 183 library jars
  was scanned. ``lib/utilities-4.11.14.jar`` (compomics-utilities) contains a
  Google Analytics sender -- ``com.compomics.util.Util.sendGAUpdate`` posting
  to ``google-analytics.com/collect`` -- called only from
  ``com.compomics.software.CompomicsWrapper$3`` with PeptideShaker and
  SearchGUI tracking ids ``UA-36198780-1`` / ``-2``. **No PDV class references
  ``CompomicsWrapper``, ``sendGAUpdate`` or any ``com/compomics/software``
  class**, so this code is shipped but not reachable from PDV.
* **User-triggered network code in PDV** (none on the ``denovo-gui`` path
  unless the user asks): Koina spectrum prediction
  (``https://koina.wilhelmlab.org/v2/models/``), off by default
  (``SpectrumMainPanel.autoPredictEnabled = false``) and enabled only from its
  dialog; USI look-up against ProteomeCentral and PRIDE PROXI from the
  single-peptide window; and "contact the developers" links.

Files PDV writes
================

Observed in every run: ``<mztab>.db`` (a SQLite file) **next to the mzTab**,
plus ``.db-journal`` when the import dies; ``<file>.mgf.cui`` (an index)
**next to each MGF**; ``~/.compomics/userpreferences.cup`` and a Java font
cache under ``~/.java``. Nothing next to an mzML. The run directory must be
writable, and these files are run artefacts the product should expect.

Facts for the units that follow
===============================

Source-side findings the exporter's readers (units 4-6) must handle, counted
over every K562 target PSM:

* **Percolator's PSMId rank is not pepXML's** ``hit_rank``. Comet gives tied
  hits the same ``hit_rank`` (scan 11262 charge 3 has two hits at rank 3)
  while its PIN numbers hits 1..n in file order, so ``..._11262_3_5`` is the
  5th ``search_hit`` whose ``hit_rank`` is 4. **81** of 2,130 ``_3`` and
  **99** of 1,767 ``_4`` PSMs differ. Join by ordinal, then verify the peptide
  and modifications, as the spike does.
* **Protein lists disagree between Comet's PIN and its pepXML.** The pepXML
  ``alternative_protein`` drops the ``DECOY_`` prefix the PIN (and so
  Percolator) keeps: ``..._11911_2_1`` is ``sp|P24752|THIL_HUMAN,
  DECOY_sp|Q9NVE4|CCD87_HUMAN`` in Percolator and ``..., sp|Q9NVE4|CCD87_HUMAN``
  in pepXML. **31** ``_3`` and **12** ``_4`` PSMs. Which source is
  authoritative for accessions is a fidelity decision for unit 5.
* Percolator's peptide string omits Comet's static cysteine modification;
  the pepXML records it (``static="57.021464"``).

What is UNVERIFIED
==================

#. **The observer is not the product launch.** Display evidence comes from
   PDV's own main class run inside the observer's JVM; R1, the plain
   ``java -jar`` launch, was judged by the control API only. The code path is
   the same, but they are two launches.
#. **mzXML** in ``denovo-gui``, and mixed MGF + mzML: established from source
   only, not executed.
#. **N-terminal modifications** through the bracket form: from source only;
   this search has none.
#. **A full-run mzML** (position == scan): not available; the claim that
   ``scan=<native scan>`` then works is a prediction from the mechanism.
#. **The msftbx position rule** is inferred from Phase 00's probe and this
   spike's results on two contiguous subset files; a file with gaps in its
   index was not tested.
#. **Network silence** was observed on this host, for these runs, sampling
   every 50 ms: a connection shorter than one sample interval could escape the
   socket sampler, though not the name-lookup logger unless it used a literal
   IP address. The static scan establishes reachability from PDV's own
   classes, not every possible reflective path in 183 libraries.
#. **Rendering itself** -- that the plot pixels show the annotated spectrum --
   was not checked; the evidence is what PDV handed to its plot.
#. Windows and macOS: nothing executed.
#. The page-switch path of ``selectBySpectraRef`` was exercised once (row
   1106 of 2,130, page 2); the last page was not separately selected because
   its last row shares its ``spectra_ref`` with an earlier row.
