=========================================================================
The scientific path, end to end, with no GUI
=========================================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 8 of 10 -- the scientific path, end to end, with no GUI
:Date: 2026-08-29
:Serves: exit-gate items **2** and **3**
:Script: ``scripts/feasibility/run_scientific_path.sh``
:Parameter record: ``scripts/feasibility/comet.params``
:Status: Evidence. This document decides nothing.

.. contents:: Contents
   :depth: 2
   :local:

What this proves, and what it does not
======================================

Exit-gate item 2 asks for "a scripted, non-GUI run [that] produces a valid
Limelight XML from real fixture spectra on at least one platform, and the exact
commands are recorded". Exit-gate item 3 asks for "a scripted run with
Percolator 3.09 [that] produces PSM, peptide and weights output and demonstrably
no XML".

Both were achieved on this Linux host on 2026-08-29, from one re-runnable
script, on real high-resolution Orbitrap spectra. Every command, every count and
every failure below was observed, not inferred.

Four things found along the way matter more than the gate itself:

#. The mzML files as fetched are **broken indexed mzML** and Comet 2026.02.2
   refuses them outright. The cause is line endings; the repair is provable.
#. Comet's ``-N<name>`` is **silently ignored** with more than one input file --
   the documentation says "valid only with one input file", but no error is
   raised.
#. The Percolator ``-Z`` / Limelight ``--import-decoys`` relationship is
   **bidirectional and undocumented in that direction**: running Percolator with
   ``-Z`` and then omitting ``--import-decoys`` is a hard failure, and
   ``--import-decoys`` cannot work at all against Comet's *internal* decoys.
#. The ``percolator_out.xsd`` shipped inside the 3.07.1 Debian package
   **rejects the XML that the very same package's binary writes**.

Nothing here answers ``D-006``. The spectra and FASTA used are the Phase 00
ephemeral feasibility input described in ``fixture-candidates.rst``; they live
under ``scratch/`` and none of them, nor any run output, is committed.

Result at a glance
------------------

.. list-table::
   :header-rows: 1
   :widths: 46 54

   * - Question
     - Answer
   * - Comet 2026.02.2 searches the fixture?
     - Yes -- 1,335 MS2 spectra searched, 6,670 PIN rows
   * - Percolator 3.07.1 produces pout XML, PSM, peptide, weights?
     - Yes -- all five outputs, non-empty, cross-checked
   * - Limelight converter produces a Limelight XML?
     - Yes -- 12,797,113 bytes, 3,897 PSMs, 2,985 reported peptides
   * - Is that XML *valid*, not merely well-formed?
     - Yes -- schema-valid against ``limelight-xml.xsd``, with the
       validator first proven able to reject three deliberate corruptions
   * - Percolator 3.09 produces PSM, peptide, weights?
     - Yes -- identical q-values to 3.07.1 on the same PIN
   * - Does 3.09 produce XML?
     - No -- and it *rejects* ``-X``, ``--xmloutput`` and ``-Z`` with
       ``ERROR: the option -X is invalid.`` and exit status 1
   * - Does the converter accept XML from the 3.07.1 ``noxml`` build?
     - **Yes** -- and the resulting Limelight XML is byte-identical to the
       XML-capable build's apart from the provenance element

The host, the tools, and what was fetched
=========================================

Everything ran on the project host: Debian 12, glibc 2.36, 64 cores. Nothing was
installed on the host; no ``apt``, no ``sudo``, no host-level ``pip``.

.. list-table:: Binaries and artefacts used
   :header-rows: 1
   :widths: 26 74

   * - Tool
     - Location and identification
   * - Comet 2026.02.2
     - ``scratch/upstream/comet.linux.exe``; banner
       ``Comet version "2026.02 rev. 2 (6edec91)"``
   * - Percolator 3.07.1 (XML-capable)
     - ``scratch/percolator/3.07.1-linux-x86_64/usr/bin/percolator``; banner
       ``Percolator version 3.07.1, Build Date Jun 20 2024 13:21:20``
   * - Percolator 3.07.1 (``noxml`` build)
     - ``scratch/windows/linuxcontrol/noxml/usr/bin/percolator``, 2,182,688
       bytes, SHA-256
       ``9e140ade13e3994933503158699f81a924a1fe30592974d3e37eba91cddfadd3``;
       banner ``Percolator version 3.07.1, Build Date Jun 20 2024 13:20:18``
   * - Percolator 3.09.0
     - ``scratch/percolator/3.09/run-percolator-3.09.sh`` (a wrapper that
       supplies Boost through ``LD_LIBRARY_PATH``); banner
       ``Percolator version 3.09.0, Build Date May 21 2026 17:16:38``
   * - JDK
     - ``tools/liberica-jdk-25.0.4.1+1``; ``openjdk version "25.0.4.1" 2026-08-18 LTS``
   * - Percolator pout XSD
     - ``scratch/percolator/3.07.1-linux-x86_64/usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd``

Only one artefact was fetched by this work unit.

.. list-table:: Limelight converter, fetched 2026-08-29
   :header-rows: 1
   :widths: 20 80

   * - Field
     - Value
   * - File
     - ``cometPercolator2LimelightXML.jar``
   * - URL
     - ``https://github.com/yeastrc/limelight-import-comet-percolator/releases/download/v2.8.1/cometPercolator2LimelightXML.jar``
   * - Version
     - ``v2.8.1`` -- the release pinned by the specification; the JAR reports
       itself as ``version="v2.8.1"`` inside its own output
   * - Size
     - 2,762,075 bytes -- matches the size recorded in the specification
   * - SHA-256
     - ``843573396ce0654a0ac81582b378c496923e49dde71f40d750d890947774ece1``
   * - Licence
     - Apache-2.0 (upstream repository licence, as recorded in the
       specification; not independently re-read by this unit)
   * - Location
     - ``scratch/scientific-path/tool/`` -- gitignored, **not** committed, and
       **not** placed under ``tools/``: this is a Phase 00 probe, and where the
       product will keep the converter is part of ``D-003``

The JAR was downloaded through the release asset URL, which does not consume the
GitHub API rate limit. No ``api.github.com`` call was made by this unit.

Stage 0 -- the input, and a defect that blocks the whole chain
==============================================================

The ephemeral feasibility input is fetched by the script owned by work unit 6::

   python3 scripts/feasibility/fetch_ephemeral_input.py

It leaves, checksum-verified and content-verified:

.. list-table::
   :header-rows: 1
   :widths: 56 44

   * - File
     - Content
   * - ``scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML``
     - 801 spectra -- 73 MS1, **728 MS2**
   * - ``scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML``
     - 671 spectra -- 61 MS1, **610 MS2**
   * - ``scratch/fixture/UP000005640_9606.fasta``
     - 20,652 records, human canonical reference proteome

The mzML files are broken as fetched
------------------------------------

Running Comet directly on either file fails::

   $ scratch/upstream/comet.linux.exe \
       -Pcomet.params scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML

    Comet version "2026.02 rev. 2 (6edec91)"
    Search start:  2026/08/29, 01:08:41 AM
    - Input file: .../20100614_Velos1_TaGe_SA_K562_3.mzML
      - Load spectra:.../20100614_Velos1_TaGe_SA_K562_3.mzML(1) :
        parseOffset() 2: Syntax error parsing XML.

   exit status 249

The cause is line endings, and it is provable rather than guessable. Both files
are ``indexedmzML``: they carry an ``<indexList>`` of absolute byte offsets, an
``<indexListOffset>`` pointing at that list, and a ``<fileChecksum>`` which is
the SHA-1 of the file up to and including the ``<fileChecksum>`` open tag. They
are served from a git repository with **CRLF** line endings, and every one of
those offsets was computed on the **LF** form. Stripping the carriage returns
restores all three invariants exactly:

.. list-table:: mzML self-consistency, measured
   :header-rows: 1
   :widths: 34 22 22 22

   * - Check
     - File
     - As fetched
     - LF-normalised
   * - Bytes
     - ``..._3.mzML``
     - 11,098,328
     - 11,048,546
   * - ``<indexListOffset>`` equals the real offset of ``<indexList>``
     - ``..._3.mzML``
     - No (10,979,386 vs 11,028,357)
     - **Yes** (both 10,979,386)
   * - ``<fileChecksum>`` matches the recomputed SHA-1
     - ``..._3.mzML``
     - No
     - **Yes** (``b9e023af...``)
   * - Bytes
     - ``..._4.mzML``
     - 9,416,326
     - 9,374,600
   * - ``<indexListOffset>`` equals the real offset of ``<indexList>``
     - ``..._4.mzML``
     - No (9,316,728 vs 9,357,773)
     - **Yes** (both 9,316,728)
   * - ``<fileChecksum>`` matches the recomputed SHA-1
     - ``..._4.mzML``
     - No
     - **Yes** (``d2902832...``)

The file's *own* embedded checksum, written by the tool that produced it, agrees
with the LF form and disagrees with the CRLF form. That is conclusive: the CRLF
copy is a corrupted indexedmzML, and Comet is right to refuse it.

SHA-256 of the repaired files, for the record:

.. list-table::
   :header-rows: 1
   :widths: 44 56

   * - File
     - SHA-256 after CRLF to LF
   * - ``20100614_Velos1_TaGe_SA_K562_3.mzML``
     - ``a562f6e642b2880bece3134c43aa9581124788926bd1b9305c2db0bb506954da``
   * - ``20100614_Velos1_TaGe_SA_K562_4.mzML``
     - ``602aad75e18257feabdb94ece0fa82e19e5f17153eee4fb89875976920855c82``

``run_scientific_path.sh`` writes the repaired copies to
``scratch/scientific-path/mzml-lf/`` and **refuses to continue** unless both
invariants hold afterwards. The fetched files under ``scratch/fixture/`` are
never modified, and the checksums recorded by ``fetch_ephemeral_input.py`` --
which are checksums of the bytes upstream actually serves -- are not touched.

.. note::

   For a later phase: this is not a property of these two files alone. Any
   spectrum file a user obtains through a text-mode transfer can arrive with the
   same defect, and the failure mode Comet presents -- ``parseOffset() 2: Syntax
   error parsing XML`` with exit status 249 -- names neither the cause nor the
   remedy. The product should recognise it. Whether the product should *repair*
   such a file, or refuse it with a clear explanation, is a design question this
   unit does not answer.

Stage 1 -- Comet
================

Run layout, and why it is shaped that way
-----------------------------------------

Comet writes ``<input>.pep.xml`` and ``<input>.pin`` **beside each input file**.
``-N<name>`` overrides that base name but, per its own help text, is "valid only
with one input file". Meanwhile the Limelight converter's ``-d`` defaults to the
Percolator output file's own directory, and it looks there for pepXML files
named after the run.

Those two constraints together fix the layout. The run directory holds symlinks
to the spectrum files, so Comet's outputs land in the run directory; Percolator's
XML is written either into that same directory (so the converter's ``-d`` default
works) or elsewhere with ``-d`` given explicitly. Both were exercised; see
`The converter's -d default`_.

The commands
------------

::

   cd scratch/scientific-path/comet
   ln -s ../mzml-lf/20100614_Velos1_TaGe_SA_K562_3.mzML .
   ln -s ../mzml-lf/20100614_Velos1_TaGe_SA_K562_4.mzML .

   scratch/upstream/comet.linux.exe \
       -Pscratch/scientific-path/comet/comet.params \
       20100614_Velos1_TaGe_SA_K562_3.mzML \
       20100614_Velos1_TaGe_SA_K562_4.mzML

The parameter file was generated from the binary and then edited -- never
hand-written -- because parameter names have changed across Comet versions::

   scratch/upstream/comet.linux.exe -p     # writes comet.params.new

The result
----------

::

    Search start:  2026/08/29, 01:21:59 AM
    - Input file: 20100614_Velos1_TaGe_SA_K562_3.mzML
      - Load spectra: 728
        - Run stats: 1s (728 spectra, 1.95ms/spec, 512Hz, 790MB)

    Search start:  2026/08/29, 01:22:01 AM
    - Input file: 20100614_Velos1_TaGe_SA_K562_4.mzML
      - Load spectra: 607
        - Run stats: 1s (607 spectra, 2.40ms/spec, 417Hz, 790MB)

   exit status 0

.. list-table:: Comet output, measured
   :header-rows: 1
   :widths: 34 22 22 22

   * - Quantity
     - File 3
     - File 4
     - Total
   * - MS2 spectra in the mzML
     - 728
     - 610
     - 1,338
   * - Spectra Comet loaded
     - 728
     - **607**
     - 1,335
   * - pepXML bytes
     - 2,641,354
     - 2,214,530
     - --
   * - PIN bytes
     - 879,411
     - 731,519
     - --
   * - PIN rows (excluding header)
     - 3,638
     - 3,032
     - **6,670**
   * - PIN rows with ``Label = 1`` (target)
     - 2,130
     - 1,767
     - **3,897**
   * - PIN rows with ``Label = -1`` (decoy)
     - 1,508
     - 1,265
     - **2,773**

Comet loaded 607 of file 4's 610 MS2 spectra. Three spectra were dropped, almost
certainly by ``minimum_peaks = 10``; this was not chased further, and it is
listed under `What is unverified`_.

The PIN carries 28 columns:
``SpecId Label ScanNr ExpMass CalcMass lnrSp deltLCn deltCn lnExpect Xcorr Sp
IonFrac Mass PepLen Charge1..Charge6 enzN enzC enzInt lnNumSP dM absdM Peptide
Proteins``. ``decoy_prefix`` is the default ``DECOY_`` and appears verbatim in
the ``Proteins`` column, for example
``DECOY_sp|Q16600|ZN239_HUMAN``.

Per-scan structure, measured on file 3: 728 scans, 727 of them with five PIN
rows (``num_output_lines = 5``) and one with three; 677 scans carry both target
and decoy rows, 49 target-only, 2 decoy-only. This is a concatenated
target-decoy search, and it matters in `A caveat on the q-values`_.

``-N`` with two input files is silently ignored
-----------------------------------------------

The help says ``-N<name>   to specify an alternate output base name; valid only
with one input file``. What actually happens::

   $ comet.linux.exe -Pcomet.params -Nboth file3.mzML file4.mzML
   ... searches both files ...
   exit status 0

   $ ls
   20100614_Velos1_TaGe_SA_K562_3.pep.xml
   20100614_Velos1_TaGe_SA_K562_3.pin
   20100614_Velos1_TaGe_SA_K562_4.pep.xml
   20100614_Velos1_TaGe_SA_K562_4.pin
   # there is no both.pep.xml

**Finding.** ``-N`` is not rejected with more than one input file; it is
discarded without a word, and Comet exits 0 having written per-input names. A
product that passes ``-N`` and then looks for the named output will fail *later*,
somewhere else, with a confusing message. The product must not pass ``-N``
alongside multiple inputs, and must derive expected output paths from the input
paths in that case. The script asserts this behaviour so a future Comet that
starts erroring will be noticed.

Stage 2 -- Percolator 3.07.1
=============================

The command, with the real flag names
--------------------------------------

The three outputs the gate cares about -- PSMs, peptides and the learned feature
weights -- come from ``-m``, ``-r`` and ``-w``. The decoy counterparts are
``-M`` and ``-B``. ``-w/--weights`` is the one that is easy to miss.

::

   scratch/percolator/3.07.1-linux-x86_64/usr/bin/percolator \
       -X percolator-3.07.1/percolator.pout.xml \
       -m percolator-3.07.1/psms.target.txt     -M percolator-3.07.1/psms.decoy.txt \
       -r percolator-3.07.1/peptides.target.txt -B percolator-3.07.1/peptides.decoy.txt \
       -w percolator-3.07.1/weights.txt \
       comet/20100614_Velos1_TaGe_SA_K562_3.pin \
       comet/20100614_Velos1_TaGe_SA_K562_4.pin

.. list-table:: Percolator 3.07.1 flags used
   :header-rows: 1
   :widths: 30 70

   * - Flag
     - Meaning (from ``--help`` of this binary)
   * - ``-X``, ``--xmloutput``
     - Path to xml-output (pout) file
   * - ``-Z``, ``--decoy-xml-output``
     - Include decoys in the xml-output. "Only available if -X is set"
   * - ``-m``, ``--results-psms``
     - Tab-delimited PSM results to a file instead of stdout
   * - ``-M``, ``--decoy-results-psms``
     - Tab-delimited decoy PSM results
   * - ``-r``, ``--results-peptides``
     - Tab-delimited peptide results to a file instead of stdout
   * - ``-B``, ``--decoy-results-peptides``
     - Tab-delimited decoy peptide results
   * - ``-w``, ``--weights``
     - **Output final weights to the given file**

Multiple PIN files are accepted positionally
--------------------------------------------

The usage line reads ``percolator [-X pout.xml] [other options] pin.tsv`` --
singular. In fact 3.07.1 accepts several PIN files positionally and merges them::

   Reading file: 20100614_Velos1_TaGe_SA_K562_3.pin
   Reading file: 20100614_Velos1_TaGe_SA_K562_4.pin
   Reading tab-delimited input from datafile /tmp/.../converters-tmp.tcb
   Found 6670 PSMs

That is what the product's multi-file run model needs: no manual PIN
concatenation, and the PSM identifiers stay qualified by source file
(``20100614_Velos1_TaGe_SA_K562_3_11367_2_1`` -- file, scan, charge, rank), so
the converter can find the right pepXML for each. **This is not documented in the
usage line and should not be assumed for other versions**; 3.09 was observed to
accept two PIN files as well, but no other version was tested.

The result
----------

.. list-table:: Percolator 3.07.1 output, measured
   :header-rows: 1
   :widths: 46 18 36

   * - Output
     - Rows
     - At q < 0.01
   * - ``psms.target.txt``
     - 3,897
     - **1,026**
   * - ``psms.decoy.txt``
     - 2,773
     - 9
   * - ``peptides.target.txt``
     - 2,985
     - **603**
   * - ``peptides.decoy.txt``
     - 2,359
     - 5
   * - ``weights.txt``
     - 12 lines: 3 cross-validation bins, each a feature-name line plus a
       normalised and a raw weight line
     - --

The pout XML is 2,874,350 bytes and contains 3,897 ``<psm>`` and 2,985
``<peptide>`` elements -- exactly the target row counts, because this run had no
``-Z``. Its ``<process_info>`` block records the run::

   <pi_0_psms>0.676304</pi_0_psms>
   <pi_0_peptides>0.738004</pi_0_peptides>
   <psms_qlevel>1026</psms_qlevel>
   <peptides_qlevel>603</peptides_qlevel>

Real rows from ``psms.target.txt``, with real q-values::

   PSMId                                      score    q-value     posterior_error_prob  peptide                    proteinIds
   20100614_Velos1_TaGe_SA_K562_3_11367_2_1   2.37114  0.00112905  9.27998e-07           R.FNLTYVSHDGDDK.K          sp|P26639|SYTC_HUMAN
   20100614_Velos1_TaGe_SA_K562_3_11288_4_1   2.35707  0.00112905  9.96468e-07           K.LGHGLLSGEYSKPVPESGDGER.V sp|P45974|UBP5_HUMAN

The first three feature-weight lines of bin 1, so that "weights output" means
something concrete::

   lnrSp    deltLCn  deltCn   lnExpect  Xcorr   Sp       IonFrac  ...  dM       absdM     m0
   -0.412   0.0000   0.0356   -0.3003   0.1177  -0.0541  0.0533   ...  0.0129   -0.0620   -0.7605
   -0.3863  0.0000   0.1882   -0.0560   0.2181  -0.0004  0.3400   ...  14.1166  -68.3014  -0.4326

A caveat on the q-values
------------------------

Percolator's search-type detection got this input wrong, and said so::

   Separate target and decoy search inputs detected, using mix-max method.
   Train/test set contains 3897 positives and 2773 negatives, size ratio=1.40534 and pi0=1
   Warning: The mix-max procedure is not well behaved when # targets (3897) !=
   # decoys (2773). Consider using target-decoy competition (-Y flag).

The search *was* concatenated -- ``decoy_search = 1`` puts targets and decoys in
one database and the PIN's per-scan rows are a mixture of both. The
auto-detection nonetheless chose mix-max. Re-running with ``-Y``
(``--post-processing-tdc``) gives::

   Separate target and decoy search inputs detected, using target-decoy competition on Percolator scores.
   Selected best-scoring PSM per file+scan+expMass (target-decoy competition): 1213 target PSMs and 122 decoy PSMs.

.. list-table:: mix-max (default) versus target-decoy competition
   :header-rows: 1
   :widths: 40 30 30

   * - Quantity
     - Default (mix-max)
     - ``-Y`` (TDC)
   * - Target PSM rows reported
     - 3,897
     - 1,213
   * - Target PSMs at q < 0.01
     - 1,026
     - 1,058
   * - Target peptides at q < 0.01
     - 603
     - 596

The identification counts barely move, so nothing in this document depends on
the choice. But **a later phase must decide what the product does here**: it can
detect the "not well behaved" warning, or set ``-I concatenated`` / ``-Y``
explicitly when it knows Comet ran with ``decoy_search = 1``, which it always
does because it wrote the parameter file. Silently shipping a mis-detected
error model would be a scientific defect, not a cosmetic one. This is
recommended to the phase orchestrator as an item for a later phase, not decided
here.

Stage 3 -- the Limelight converter
==================================

The converter's real arguments, as observed
-------------------------------------------

``java -jar cometPercolator2LimelightXML.jar --help`` on the pinned v2.8.1 JAR
reports exactly the interface the specification records, with one addition::

   java -jar cometPercolator2LimelightXML.jar [-hvV] [--import-decoys]
   [--open-mod] -c=<cometParamsFile> [-d=<pepXMLDirectory>] [-f=<fastaFile>]
   [--independent-decoy-prefix=<independentDecoyPrefix>] -o=<outFile>
   -p=<percolatorFile> [-q=<qValueOverride>]

Every argument in the specification's table -- ``-c/--comet-params``,
``-p/--percolator-file``, ``-o/--out-file``, ``-d/--pepxml-directory``,
``-f/--fasta-file``, ``-q/--q-value``, ``--import-decoys``,
``--independent-decoy-prefix``, ``--open-mod``, ``-v/--verbose`` -- is present
with the documented meaning. The specification's table omits ``-h/--help`` and
``-V/--version``, which the JAR also accepts. The ``-d`` help text confirms the
default: "By default, this program expects the pepXML file(s) to be in the same
directory as the percolator file."

The command
-----------

::

   . tools/env.sh
   java -jar scratch/scientific-path/tool/cometPercolator2LimelightXML.jar \
       -c scratch/scientific-path/comet/comet.params \
       -p scratch/scientific-path/percolator-3.07.1/percolator.pout.xml \
       -d scratch/scientific-path/comet \
       -f scratch/fixture/UP000005640_9606.fasta \
       -o scratch/scientific-path/limelight/limelight.xml \
       -v

Output::

   Reading comet params into memory... Done.
   Reading Percolator XML data into memory... Done.
   Locating pepXML files using Percolator results... Done.
   Reading Comet pepXML data into memory... Done.
   Verifying all percolator results have comet results... Done.
   Writing out XML... Matching peptides to proteins... Done.
   Validating Limelight XML... Done.

   exit status 0

The converter runs in about four seconds and prints ``Error printing runtime
information.`` on standard output on every run, successful or not. It is benign
-- the exit status and the produced file are unaffected -- but a product that
treats any stdout content as a failure signal will get this wrong.

The converter's -d default
--------------------------

Two runs, to establish the behaviour rather than assume it.

**With the pout file in a directory holding no pepXML, and ``-d`` omitted** --
fails, with a message that names the file it wanted::

   Encountered error during conversion: Could not find file:
   .../percolator-3.07.1/20100614_Velos1_TaGe_SA_K562_4.pep.xml.
   May need to specify data directory with -d option.

   exit status 1

**With the pout file copied next to the pepXML files, and ``-d`` omitted** --
succeeds, and the output is byte-identical to the explicit-``-d`` run apart from
the ``<conversion_program>`` provenance element, which records the timestamp and
argument list.

So the converter derives each expected pepXML file name from the Percolator PSM
identifiers and looks for that exact name in the pepXML directory. It does not
glob, and the presence of the pout XML in the same directory does not confuse it.

.. _validity:

What "valid" means here, and how it was established
====================================================

Well-formed is not enough, so four independent things were checked.

1. A schema exists, and where it comes from
-------------------------------------------

The Limelight XML schema is **not** published as a standalone download from
``limelight-import-api`` or ``limelight-core``; no separate XSD artefact was
located. It ships *inside* the converter JAR, as the JAR-root entry
``limelight-xml.xsd``:

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Field
     - Value
   * - Entry
     - ``limelight-xml.xsd`` at the root of ``cometPercolator2LimelightXML.jar``
   * - Size
     - 65,905 bytes
   * - SHA-256
     - ``e8c1b92098db3b4364f06101b1def43675cd956ddf8758de25b692e3f698d242``
   * - Namespace
     - none (``elementFormDefault="qualified"``, no ``targetNamespace``)

That is the schema the converter itself validates against in its final
``Validating Limelight XML...`` step, so it is the right one to hold the output
to. Because it comes from the JAR, the product gets the schema for free wherever
it has the converter.

2. The validator, and proof that it can fail
---------------------------------------------

Validation is done by a small ``javax.xml.validation`` program run on the
project JDK (``java ValidateXml.java <schema.xsd> <document.xml>``), which
counts fatals, errors and warnings and exits non-zero if any fatal or error was
raised. **A validator that never fails proves nothing**, so before its verdict on
the real file was accepted it was pointed at three deliberately corrupted copies
of that same file.

.. list-table:: Deliberate-failure run
   :header-rows: 1
   :widths: 26 44 30

   * - Corruption
     - What the validator said
     - Verdict
   * - ``<not_in_the_schema/>`` injected before ``<search_program_info>``
     - ``ERROR line 3 col 25: cvc-complex-type.2.4.a: Invalid content was found
       starting with element 'not_in_the_schema'. One of '{search_comments,
       search_program_info}' is expected.``
     - INVALID, exit 1
   * - required attribute ``fasta_filename`` removed from the root element
     - ``ERROR line 2 col 18: cvc-complex-type.4: Attribute 'fasta_filename'
       must appear on element 'limelight_input'.``
     - INVALID, exit 1
   * - closing ``</limelight_input>`` removed
     - ``FATAL line 114132 col 1: XML document structures must start and end
       within the same entity.``
     - INVALID, exit 1
   * - **none -- the real converter output**
     - ``result: fatals=0 errors=0 warnings=0``
     - **VALID, exit 0**

The script performs all four every run, and fails loudly if any corrupted copy
is *accepted*. That inversion is the point: the harness proves its own
instrument before reporting a measurement.

3. The content is non-trivial
-----------------------------

.. list-table:: Limelight XML content
   :header-rows: 1
   :widths: 46 54

   * - Element
     - Count
   * - ``psm``
     - 3,897
   * - ``reported_peptide``
     - 2,985
   * - ``matched_protein``
     - 2,747 (2,753 ``matched_protein_label``)
   * - ``matched_protein_for_peptide``
     - 3,418
   * - ``peptide_modification``
     - 787 across 680 ``peptide_modifications`` blocks
   * - ``filterable_psm_annotation``
     - 42,867 -- 11 named annotations per PSM
   * - ``filterable_reported_peptide_annotation``
     - 11,940 -- 4 named annotations per reported peptide

The annotations carried per PSM are ``XCorr``, ``DeltaCN``, ``Sp Score``,
``Sp Rank``, ``E-Value``, ``Hit Rank`` and ``Mass Diff`` from Comet, and
``q-value``, ``p-value``, ``PEP`` and ``SVM Score`` from Percolator; per reported
peptide, the four Percolator ones. The file declares ``Comet`` version
``2026.02 rev. 2 (6edec91)`` and ``Percolator`` version
``Percolator version 3.07.1`` in ``<search_program_info>``, and its
``<conversion_program>`` element records ``cometPercolator2LimelightXML.jar``
version ``v2.8.1`` with the full argument list -- which is useful raw material
for the product's provenance record.

4. The content agrees with the Percolator output
------------------------------------------------

.. list-table:: Cross-check
   :header-rows: 1
   :widths: 44 28 28

   * - Quantity
     - Percolator
     - Limelight XML
   * - Target PSMs
     - 3,897
     - 3,897
   * - Target reported peptides
     - 2,985
     - 2,985
   * - PSMs at q < 0.01
     - 1,026
     - 1,026
   * - Peptides at q < 0.01
     - 603
     - 603

Every one of these is asserted by the script; a mismatch fails the run.

The Percolator pout XML does not match its own shipped XSD
===========================================================

The specification requires the installer to "install the XSD companion files
alongside the binary". Those XSDs do not accept the binary's output.

Validating the pout XML from 3.07.1 against
``usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd``, extracted from the
**same** ``percolator-v3-07-linux-amd64.deb``::

   ERROR line 7 col 89: cvc-complex-type.3.1: Value '3' of attribute
   'p:majorVersion' of element 'percolator_output' is not valid with respect to
   the corresponding attribute use. Attribute 'p:majorVersion' has a fixed value
   of '2'.

   result: fatals=0 errors=1 warnings=0
   VERDICT: INVALID

That is the *only* error -- everything else in the document validates. The XSD
declares::

   <xsd:attribute ref="majorVersion" use="required" fixed="2"/>

while the binary writes ``p:majorVersion="3" p:minorVersion="07"``. The same
single error occurs for the ``-Z`` output and for the ``noxml`` build's output.

**Consequences for the product.** Any feature that validates Percolator XML
against the shipped XSD before handing it to the converter will reject perfectly
good output. Either that validation must tolerate the ``majorVersion``
mismatch -- which is a documented, understood upstream defect, not a weakened
gate -- or the product must not schema-validate pout XML at all and should rely
on the converter's own parse. This is raised for the phase orchestrator; it is
not decided here. Note also that Percolator's ``-s/--no-schema-validation`` flag
concerns *input* validation, not output.

Gate item 3 -- Percolator 3.09
===============================

The command
-----------

::

   scratch/percolator/3.09/run-percolator-3.09.sh \
       -m percolator-3.09/psms.target.txt     -M percolator-3.09/psms.decoy.txt \
       -r percolator-3.09/peptides.target.txt -B percolator-3.09/peptides.decoy.txt \
       -w percolator-3.09/weights.txt \
       comet/20100614_Velos1_TaGe_SA_K562_3.pin \
       comet/20100614_Velos1_TaGe_SA_K562_4.pin

   exit status 0

The wrapper is required: the bare 3.09 binary needs Boost, which the wrapper
supplies through ``LD_LIBRARY_PATH``.

PSM, peptide and weights output, with real content
--------------------------------------------------

.. list-table:: Percolator 3.09 output, measured
   :header-rows: 1
   :widths: 40 16 20 24

   * - File
     - Bytes
     - Rows
     - At q < 0.01
   * - ``psms.target.txt``
     - 441,178
     - 3,897
     - **1,026**
   * - ``psms.decoy.txt``
     - 325,518
     - 2,773
     - 9
   * - ``peptides.target.txt``
     - 326,927
     - 2,985
     - **603**
   * - ``peptides.decoy.txt``
     - 266,430
     - 2,359
     - 5
   * - ``weights.txt``
     - 1,633
     - 12 lines
     - --

Real rows, with real q-values::

   PSMId                                      score    q-value     posterior_error_prob  peptide                    proteinIds
   20100614_Velos1_TaGe_SA_K562_3_11367_2_1   2.37114  0.00112905  1e-10                 R.FNLTYVSHDGDDK.K          sp|P26639|SYTC_HUMAN
   20100614_Velos1_TaGe_SA_K562_3_11288_4_1   2.35707  0.00112905  1e-10                 K.LGHGLLSGEYSKPVPESGDGER.V sp|P45974|UBP5_HUMAN

Demonstrably no XML
-------------------

Three separate demonstrations.

#. **No XML file is produced.** ``find percolator-3.09 -name '*.xml'`` returns
   nothing after a full successful run.
#. **The flags are gone from the interface.**
   ``run-percolator-3.09.sh --help | grep -cE 'xmloutput|decoy-xml-output'``
   returns ``0``. The usage line itself has changed from
   ``percolator [-X pout.xml] [other options] pin.tsv`` in 3.07.1 to
   ``percolator [other options] pin.tsv`` in 3.09.
#. **Passing them anyway is a hard error, not a silent ignore.** This is the
   behaviour the product's error handling depends on:

   .. list-table::
      :header-rows: 1
      :widths: 30 46 24

      * - Passed to 3.09
        - stderr
        - Exit status
      * - ``-X <file>``
        - ``ERROR: the option -X is invalid.`` followed by
          ``Please run "command --help."``, preceded by the usage block and
          prefixed ``Exception caught:``
        - 1
      * - ``--xmloutput <file>``
        - ``ERROR: the option --xmloutput is invalid.``
        - 1
      * - ``-Z``
        - ``ERROR: the option -Z is invalid.``
        - 1

   No file was created at the path given to ``-X``, and nothing was written to
   stdout. A caller therefore finds out immediately and unambiguously; there is
   no risk of a run appearing to succeed and producing no XML.

3.07.1 versus 3.09 on the same PIN
-----------------------------------

Both versions were given the identical pair of PIN files.

.. list-table::
   :header-rows: 1
   :widths: 44 28 28

   * - Quantity
     - 3.07.1
     - 3.09
   * - Target PSM rows
     - 3,897
     - 3,897
   * - Target PSMs at q < 0.01
     - **1,026**
     - **1,026**
   * - Target peptide rows
     - 2,985
     - 2,985
   * - Target peptides at q < 0.01
     - **603**
     - **603**
   * - ``weights.txt``
     - byte-identical between the two versions
     - byte-identical between the two versions

Comparing ``psms.target.txt`` column by column across all 3,897 rows: the
``PSMId``, ``score``, ``q-value``, ``peptide`` and ``proteinIds`` columns are
identical in value and in order. **Only ``posterior_error_prob`` differs**, in
3,870 of 3,897 rows, by at most 0.236 absolute. 3.09's stderr says why::

   Performing isotonic regression using I-Splines

The same holds for ``peptides.target.txt`` (1,551 of 2,985 rows differ, in the
same single column).

**Conclusion for a later phase.** For rescoring, 3.07.1 and 3.09 are
interchangeable on this input: identical SVM training, identical scores,
identical q-values, identical identification counts at 1% FDR. They differ only
in the posterior error probability estimator. The difference between the versions
that matters is XML, not science -- which is exactly what the *latest compatible*
policy in ``D-002`` assumes. Note this is one dataset and one seed (both default
to ``-S 1``); it is evidence, not a general proof.

The ``noxml`` question -- does the converter accept its XML?
=============================================================

Why this matters
----------------

Every XML-capable Percolator artefact is an OS package, so ``D-002``, ``D-003``
and ``D-004`` currently assume the installer must extract NSIS, ``.deb`` and
``.pkg`` payloads. The phase orchestrator established that the 3.07.1 Linux
**``noxml``** build nevertheless emits ``percolator_out`` XML under ``-X``. If
the Limelight converter accepts that XML, the payload-extraction burden may be
avoidable. Only this work unit could answer it, because answering it requires a
real Comet pepXML.

.. note::

   The per-release ``noxml`` capability sweep is work unit 10's, and owns
   ``docs/feasibility/noxml-capability.rst`` and
   ``scripts/feasibility/noxml_sweep.*``. This section contributes only the
   converter-acceptance answer and does not restate that unit's findings.

What was run
------------

::

   scratch/windows/linuxcontrol/noxml/usr/bin/percolator \
       -X percolator-noxml/percolator.pout.xml \
       -m percolator-noxml/psms.target.txt     -M percolator-noxml/psms.decoy.txt \
       -r percolator-noxml/peptides.target.txt -B percolator-noxml/peptides.decoy.txt \
       -w percolator-noxml/weights.txt \
       comet/20100614_Velos1_TaGe_SA_K562_3.pin \
       comet/20100614_Velos1_TaGe_SA_K562_4.pin

   java -jar cometPercolator2LimelightXML.jar \
       -c comet/comet.params \
       -p percolator-noxml/percolator.pout.xml \
       -d comet \
       -f scratch/fixture/UP000005640_9606.fasta \
       -o limelight/limelight-from-noxml.xml

The answer
----------

**Yes. The converter accepts XML produced by the ``noxml`` build, and the
resulting Limelight XML is identical to the one produced from the XML-capable
build's XML.**

.. list-table::
   :header-rows: 1
   :widths: 46 54

   * - Check
     - Result
   * - ``noxml`` build's ``--help`` mentions ``xmloutput`` / ``decoy-xml-output``
     - Yes, 2 occurrences -- despite the build name
   * - ``noxml`` build exits 0 under ``-X`` and writes a pout XML
     - Yes, 2,874,340 bytes in the recorded run (the size varies only with the
       length of the binary path recorded in ``<command_line>``)
   * - pout XML versus the XML-capable build's
     - Byte-identical apart from ``<command_line>``, which records the binary
       path
   * - Tab outputs (PSMs, decoy PSMs, peptides, decoy peptides, weights)
     - All five byte-identical to the XML-capable build's
   * - Converter exit status on the ``noxml`` XML
     - 0
   * - Resulting Limelight XML, schema-validated
     - VALID -- ``fatals=0 errors=0 warnings=0``
   * - Resulting Limelight XML versus the XML-capable run's
     - Identical apart from the ``<conversion_program>`` element, which records
       the timestamp and arguments

**What this does and does not establish.** It establishes that *this* ``noxml``
build, on Linux, produces converter-acceptable XML on this input. It does **not**
establish that the ``noxml`` artefacts published for macOS or Windows behave the
same way, nor that any other release's ``noxml`` build does. That is work unit
10's sweep. If the sweep agrees across platforms, ``D-002``, ``D-003`` and
``D-004`` should be revisited by the main orchestrator, because the premise that
XML capability requires the OS-package artefacts would be false. This unit
recommends that revisit; it does not make it.

``R-LL-05`` -- the ``-Z`` / ``--import-decoys`` chain, tested
==============================================================

``R-LL-05`` says ``--import-decoys`` is valid only if Percolator was run with
``-Z``, which Percolator's help says is "Only available if ``-X`` is set", and
warns that passing ``--import-decoys`` against a run with no decoy output "fails
late and obscurely". All four combinations were run. The requirement stands, but
**two of the four observed behaviours contradict the specification's account of
them**, and a fifth constraint that the specification does not mention turns out
to be the strictest of all.

.. list-table:: Observed behaviour, all four combinations
   :header-rows: 1
   :widths: 18 18 30 34

   * - Percolator
     - Converter
     - Outcome
     - Evidence
   * - ``-X`` only
     - no ``--import-decoys``
     - **Works.** This is the gate-item-2 path.
     - Valid Limelight XML, 3,897 PSMs, all targets
   * - ``-X -Z``
     - no ``--import-decoys``
     - **Hard failure.** Not documented anywhere.
     - ``Encountered error during conversion: Unable to find any comet results
       for reported peptide: TFGKGCQECR``, exit 1
   * - ``-X`` only
     - ``--import-decoys``
     - **Succeeds silently and imports nothing.** Not a late obscure failure.
     - exit 0, valid Limelight XML, ``is_decoy`` counts
       ``{'false': 3890}`` -- zero decoys
   * - ``-X -Z``
     - ``--import-decoys``
     - Works **only** if the decoy proteins are in the FASTA; see below
     - see the next two subsections
   * - ``-Z`` without ``-X``
     - --
     - **Silently ignored by Percolator.** Exit 0, no error, no XML.
     - 3.07.1 exits 0 and writes its peptide results to stdout as usual

``--import-decoys`` is incompatible with Comet's internal decoys
-----------------------------------------------------------------

With ``decoy_search = 1`` -- Comet's internal concatenated decoys, which is what
``fixture-candidates.rst`` recommends and what gate item 2 used -- the decoy
proteins exist only inside Comet. They are not in the FASTA, because Comet
generates them. The converter checks every protein name against the FASTA and
refuses::

   Encountered error during conversion: The following protein names were not
   found in FASTA: DECOY_sp|Q4G0U5|PCDP1_HUMAN, DECOY_sp|A0FGR8|ESYT2_HUMAN, ...

   exit status 1

Passing ``--independent-decoy-prefix DECOY_`` as well does **not** help; the same
error is raised.

What does work
--------------

Search an **externally concatenated target+decoy FASTA** with
``decoy_search = 0``. The script builds one (each record, then the same records
reversed and prefixed ``DECOY_``: 20,652 + 20,652 = 41,304 records) and repeats
the chain.

Two useful facts came out of that run:

* **Comet sets the PIN ``Label`` column from ``decoy_prefix`` even with
  ``decoy_search = 0``**, when the FASTA itself is concatenated: 2,133 target and
  1,505 decoy rows for file 3. Percolator therefore works unchanged.
* The full chain then succeeds:

  .. list-table::
     :header-rows: 1
     :widths: 50 50

     * - Quantity
       - Value
     * - Percolator target PSM rows / at q < 0.01
       - 3,890 / **1,060**
     * - Percolator target peptide rows / at q < 0.01
       - 2,974 / **625**
     * - Limelight XML
       - 22,428,635 bytes, **schema-valid**
     * - ``psm`` elements
       - 6,670 -- 3,890 with ``is_decoy="false"``, 2,780 with
         ``is_decoy="true"``
     * - ``reported_peptide`` elements
       - 5,307
     * - ``matched_protein`` elements
       - 4,684

What the product must therefore do
-----------------------------------

This is a stronger constraint than ``R-LL-05`` as written, and a later phase
needs it:

#. Limelight decoy import requires ``-Z``, which requires ``-X``. **And**
   ``-Z`` requires ``--import-decoys``: the two must be enabled or disabled
   together, because either one alone is wrong. With ``-Z`` and no
   ``--import-decoys`` the conversion fails outright; with ``--import-decoys``
   and no ``-Z`` it silently produces a decoy-free file, which is worse, because
   nothing tells the user.
#. Limelight decoy import additionally requires that the decoys be **real FASTA
   entries**, which means the search must use ``decoy_search = 0`` against a
   concatenated target+decoy database rather than ``decoy_search = 1``. The
   product either generates that database itself, or asks the user for one, or
   disables decoy import for internal-decoy searches with that explanation.
#. ``-Z`` without ``-X`` is silently ignored by Percolator 3.07.1, so the
   product cannot rely on Percolator to catch that mistake.

Recommended as an amendment to ``R-LL-05``, for the main orchestrator to decide;
this document does not amend the specification.

The parameter file that was used
================================

``scripts/feasibility/comet.params`` is committed as the reproducibility record.
It was produced by ``comet.linux.exe -p`` from the 2026.02.2 binary and then
edited -- never hand-written, because parameter names change across versions
(the single ``peptide_mass_tolerance`` is an upper/lower pair in this release).

SHA-256 of the committed file:
``da0da57e336343c559e6c6b930f77fe29fa3547738bc422c5b2ec7f7c61bb470``.
SHA-256 of the unmodified ``comet.params.new``:
``b56c967959ae04e2e782411858b2f5be06863e572cf1bfdfeccc7a4b28fbb19c``.

The complete diff against the 2026.02.2 defaults is five lines::

   --- comet.params.new (comet.linux.exe -p, unmodified)
   +++ scripts/feasibility/comet.params
   @@
   -database_name = /some/path/db.fasta
   -decoy_search = 0
   +database_name = /workspace/scratch/fixture/UP000005640_9606.fasta
   +decoy_search = 1
   @@
   -isotope_error = 2
   +isotope_error = 3
   @@
   -variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0
   +variable_mod01 = 15.994915 M 0 3 -1 0 0 0.0
   @@
   -output_percolatorfile = 0
   +output_percolatorfile = 1

Everything else the search needed was **already the default** in 2026.02.2, which
is worth recording because it is not true of older Comet releases:

.. list-table:: Settings that mattered, and where they came from
   :header-rows: 1
   :widths: 34 16 50

   * - Setting
     - Value used
     - Source
   * - ``peptide_mass_tolerance_upper`` / ``_lower``
     - ``20.0`` / ``-20.0``
     - default; ``peptide_mass_units = 2`` (ppm) is also the default
   * - ``isotope_error``
     - ``3``
     - **changed** from the default ``2``, per the fixture advice
   * - ``fragment_bin_tol``
     - ``0.02``
     - **default in 2026.02.2** -- this release already defaults to
       high-resolution MS2 settings
   * - ``fragment_bin_offset``
     - ``0.0``
     - default
   * - ``theoretical_fragment_ions``
     - ``0``
     - default
   * - ``search_enzyme_number``
     - ``1`` (Trypsin)
     - default
   * - ``num_enzyme_termini``
     - ``2`` (fully digested)
     - default
   * - ``allowed_missed_cleavage``
     - ``2``
     - default
   * - ``add_C_cysteine``
     - ``57.021464``
     - **default in 2026.02.2**
   * - ``variable_mod01``
     - ``15.994915 M 0 3 -1 0 0 0.0``
     - **changed** from the default ``15.9949`` for precision only
   * - ``use_B_ions`` / ``use_Y_ions``
     - ``1`` / ``1``
     - default; correct for HCD
   * - ``decoy_search``
     - ``1``
     - **changed** from ``0``; puts target and decoy rows in one PIN
   * - ``decoy_prefix``
     - ``DECOY_``
     - default; confirmed present in the PIN and pepXML output
   * - ``output_pepxmlfile``
     - ``1``
     - default
   * - ``output_percolatorfile``
     - ``1``
     - **changed** from ``0``
   * - ``num_output_lines``
     - ``5``
     - default; this is why the PIN has five rows per scan
   * - ``num_threads``
     - ``0`` (auto)
     - default

The yield -- 1,026 PSMs and 603 peptides at 1% FDR from 1,335 MS2 spectra, a 77%
PSM rate before FDR filtering and 21% peptide-level identification after it -- is
what a complex human lysate at high-resolution HCD should give, so the fixed
carbamidomethyl cysteine (which ``fixture-candidates.rst`` flags as not verified
from the data) does not need re-examining. The alternative search without it was
not run.

Reproducing all of this
=======================

One script does everything from the fixture fetch onwards::

   bash scripts/feasibility/run_scientific_path.sh

It takes about three minutes on this host, needs the network only for the
fixture and the converter JAR, and writes everything under
``scratch/scientific-path/`` (gitignored). ``--skip-fetch`` reuses an already
fetched fixture; ``RUN_ROOT`` moves the output directory.

The script fails loudly rather than trusting exit codes
--------------------------------------------------------

Comet exits 0 having found nothing if it is misconfigured, and the converter
exits 0 having imported no decoys, so exit status is checked *and* so is the
output. Every stage asserts that its output exists, is non-empty, and has
plausible content: PIN row counts on both labels, PSM and peptide counts, q-value
thresholds, weights rows, XML element counts, and cross-version equality where it
is expected. The Limelight element counts are asserted equal to the Percolator
row counts, so a converter that dropped half the data would fail the run.

The harness was tested for its ability to fail, not just to pass::

   $ RUN_ROOT=... COMET=/bin/true bash scripts/feasibility/run_scientific_path.sh --skip-fetch
   ...
   FAILED at [3. Comet 2026.02.2 on both mzML files (multi-file run model)]:
   Comet pepXML (20100614_Velos1_TaGe_SA_K562_3): no such file:
   .../comet/20100614_Velos1_TaGe_SA_K562_3.pep.xml

   exit status 1

A stage that exits 0 having produced nothing is caught. The schema-validation
step is inverted in the same way: it fails the run if the validator *accepts* any
of the three corrupted documents.

The final summary from the recorded run::

   run_scientific_path.sh -- 2026-08-29T01:23:11Z
   FASTA records                  20652
   PIN rows (both files)          6670 (target 3897, decoy 2773)
   3.07.1 target PSM rows         3897
   3.07.1 target PSMs q<0.01      1026
   3.07.1 target peptide rows     2985
   3.07.1 target peptides q<0.01  603
   3.09 target PSM rows           3897
   3.09 target PSMs q<0.01        1026
   3.09 target peptides q<0.01    603
   3.09 XML files written         0

Things that contradict, or go beyond, the specification
=======================================================

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Item
     - What was observed
   * - ``R-LL-05`` rationale
     - The specification says passing ``--import-decoys`` against a run with no
       decoy output "fails late and obscurely". With v2.8.1 it does not fail at
       all: it exits 0 and writes a valid Limelight XML containing zero decoys.
       The *requirement* still holds; the stated reason does not.
   * - ``R-LL-05`` scope
     - The reverse is also true and is not in the specification: running
       Percolator with ``-Z`` and then omitting ``--import-decoys`` is a hard
       conversion failure. And ``--import-decoys`` additionally requires the
       decoys to be FASTA entries, which rules out Comet's ``decoy_search = 1``.
   * - Percolator ``-Z`` precondition
     - Percolator's help says ``-Z`` is "Only available if ``-X`` is set". 3.07.1
       does not enforce it: ``-Z`` without ``-X`` exits 0 and is ignored.
   * - "Install the XSD companion files alongside the binary"
     - The shipped ``percolator_out.xsd`` rejects the shipped binary's own output
       on ``p:majorVersion`` (``fixed="2"``, binary writes ``3``).
   * - Converter argument table
     - Confirmed exactly as specified, plus ``-h/--help`` and ``-V/--version``
       which the table omits.
   * - Percolator invocation shape
     - The usage line implies a single PIN file; 3.07.1 and 3.09 both accept
       several positionally and merge them, which the multi-file run model needs.
   * - Comet ``-N``
     - Documented as "valid only with one input file"; in practice silently
       ignored with more, exit 0.
   * - Limelight XSD availability
     - No standalone published XSD was found in ``limelight-import-api`` or
       ``limelight-core``. The schema ships inside the converter JAR as
       ``limelight-xml.xsd``, which is where the product should get it.

What is unverified
==================

Stated plainly, because an unverifiable item is not a passed item.

#. **Linux only.** Everything here ran on Debian 12 x86-64. Nothing about
   macOS or Windows behaviour is established by this unit.
#. **One dataset, one search.** 1,335 MS2 spectra from two LTQ Orbitrap Velos
   HCD runs against a human canonical proteome. No other instrument, enzyme,
   modification set or organism was tried.
#. **The three missing spectra.** Comet loaded 607 of file 4's 610 MS2 spectra.
   The cause was not investigated; ``minimum_peaks = 10`` is the likely
   explanation but was not confirmed.
#. **The fixed carbamidomethyl assumption.** No protocol accompanies these
   files. The yield is good, so the assumption was not tested by re-running
   without it.
#. **The mix-max misdetection.** Percolator reports "Separate target and decoy
   search inputs detected" for what is a concatenated search. The effect on the
   identification counts is small (1,026 versus 1,058 PSMs at 1% FDR), but *why*
   the detection fails was not established -- the per-PSM ``ExpMass`` varying
   within a scan because of ``isotope_error`` is a hypothesis, not a finding.
#. **Converter licence.** Recorded as Apache-2.0 from the specification; the
   upstream ``LICENSE`` was not re-read by this unit.
#. **Limelight import itself.** A schema-valid Limelight XML was produced and
   independently validated. Whether a Limelight *server* accepts it on upload was
   not tested, and must not be, since the target server is ``D-007``.
#. **The ``noxml`` result is single-platform.** It holds for the Linux 3.07.1
   ``noxml`` build on this input. Generalising it across releases and platforms
   is work unit 10's sweep, and ``D-002``/``D-003``/``D-004`` should not be
   reopened on this section alone.
#. **PDV.** Out of this work unit's scope; not run.
#. **No performance claim.** Comet took 3 seconds wall clock, Percolator under 3
   seconds, the converter about 4 seconds, on a 64-core host with a small input.
   These are observations, not benchmarks.
