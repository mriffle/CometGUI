======================================================================
Small Thermo RAW files for a Windows RAW smoke test -- scouting report
======================================================================

:Date: 2026-11-12
:Scope: search and evaluate only; nothing here is project code or a decision
:Working directory: ``scratch/raw-scout/`` (gitignored; the files, scripts
   and search output named below were not committed -- only this report was)
:Status: Evidence. This document decides nothing, least of all licensing.

.. note::

   **Where this came from, and why it is kept.** The project owner asked for
   one agent, with no other job, to find out whether very small Thermo RAW
   files exist that still give real identifications. This is that agent's
   report, as written, kept at the owner's request because a list of such
   files is useful beyond this project. The main orchestrator re-derived the
   first row of the tier-1 table itself -- size, SHA-256, the PRIDE
   ``license`` field, PIN rows and both counts at q < 0.01 -- and it agrees;
   the other rows are the agent's own measurements. The owner then chose
   ``ph050213_18.raw`` as the project's RAW fixture (``DECISIONS.rst``,
   ``D-019``). Every count here comes from RAW files converted to mzML on
   Linux; none shows that Comet on Windows reads the RAW file itself.

   **Added 2026-11-12, after the report was written.** Phase 16 then tried
   the chosen file on a GitHub Windows runner, and Comet for Windows read it
   directly: 873 PSMs and 761 peptides at q < 0.01 with Comet 2026.02.2, and
   894 and 767 with 2026.03.0, about 3% under the converted-mzML figures for
   the same release because the two paths load slightly different spectra
   and assign charge differently. Two statements below are narrower than
   they read: the 906 / 787 for ``ph050213_18.raw`` is Comet 2026.02.2's
   figure (2026.03.0 gives 920 / 797 from the same mzML), and Comet did not
   search every scan of that file as 2+ and 3+ -- it searched 162 of them as
   1+.

.. contents:: Contents
   :depth: 2
   :local:

Verdict
=======

**Tier-1 files exist.** Nine Thermo ``.raw`` files under 10 MB (10\ :sup:`7`
bytes) were found on PRIDE for which a Comet search followed by Percolator
trains and reports hundreds of PSMs and peptides at 1% FDR. The best four come
from one project (``PXD002914``, a *Synechococcus* whole-cell lysate) and need
only a 1.08 MB, 2,513-protein FASTA.

**The catch is the licence.** Every clean tier-1 file is in a PRIDE project
whose ``license`` field reads ``EBI terms of use``, not CC0. The CC0 files that
were found (on MassIVE) are weaker: Percolator trains on them and reports PSMs
at 1% FDR, but **zero peptides** at 1% FDR. No file was found that is both
CC0 and a clean tier 1.

What was measured here is that the spectra are identifiable after conversion to
mzML on Linux. That Comet on Windows reads these RAW files natively was **not**
verified and cannot be on this host.

In this report ``MB`` means 10\ :sup:`6` bytes, as in
``docs/feasibility/fixture-candidates.rst``.

Ranked candidates
=================

Counting convention, so the numbers are not over-read: "PSMs at q < 0.01" is the
number of rows of Percolator's ``psms.target.txt`` with ``q-value < 0.01``,
exactly as ``scripts/feasibility/run_scientific_path.sh`` counts them. Ion-trap
MS2 scans carry no precursor charge, so Comet searches each as 2+ and 3+ and
writes up to five rows per assumption; "Comet loaded" therefore exceeds the MS2
count. The number of *distinct scans* behind the PSM count is given in the
per-candidate sections.

Tier 1 -- Percolator trains; PSMs and peptides at 1% FDR
--------------------------------------------------------

All numbers in this table were measured here, from files written by the tools.

.. list-table::
   :header-rows: 1
   :widths: 4 14 18 10 8 8 10 10 18

   * - No.
     - Project
     - File
     - Bytes
     - MS1
     - MS2
     - PIN rows (target / decoy)
     - PSMs / peptides at q < 0.01
     - FASTA used
   * - 1
     - ``PXD002914``
     - ``ph050213_18.raw``
     - 9,961,477
     - 8,558
     - 2,060
     - 18,120 (9,885 / 8,235)
     - **906 / 787**
     - ``UP000001422_84588``, 2,513 records, 1,080,427 bytes
   * - 2
     - ``PXD002914``
     - ``ph280213_35.raw``
     - 9,470,344
     - 3,006
     - 2,830
     - 22,715 (12,062 / 10,653)
     - **475 / 360**
     - as above
   * - 3
     - ``PXD002914``
     - ``ph280213_36.raw``
     - 8,796,695
     - 5,112
     - 2,562
     - 21,595 (11,449 / 10,146)
     - **509 / 467**
     - as above
   * - 4
     - ``PXD002914``
     - ``ph071212_DV_07.raw``
     - 9,419,062
     - 1,270
     - 2,609
     - 22,690 (11,860 / 10,830)
     - **420 / 348**
     - as above
   * - 5
     - ``PXD000211``
     - ``LCQ6524.RAW``
     - 9,844,673
     - 660
     - 1,980
     - 18,090 (9,413 / 8,677)
     - **571 / 415**
     - ``UP000006548_3702``, 27,368 records, 14,390,358 bytes
   * - 6
     - ``PXD000211``
     - ``LCQ6495.RAW``
     - 9,890,598
     - 680
     - 2,039
     - 18,015 (9,555 / 8,460)
     - **457 / 339**
     - as above
   * - 7
     - ``PXD000211``
     - ``LCQ6224.RAW``
     - 7,804,595
     - 588
     - 1,764
     - 14,540 (7,664 / 6,876)
     - **393 / 239**
     - as above
   * - 8
     - ``PXD000211``
     - ``LCQ6519.RAW``
     - 9,734,877
     - 658
     - 1,974
     - 17,935 (9,282 / 8,653)
     - **267 / 208**
     - as above
   * - 9
     - ``PXD000211``
     - ``LCQ6509.RAW``
     - 9,623,753
     - 652
     - 1,956
     - 17,385 (8,967 / 8,418)
     - **239 / 180**
     - as above

Percolator 3.09 on the same nine PIN files gave identical counts at q < 0.01.

Run as a pair (two PIN files given to one Percolator 3.07.1 run, the project's
multi-file model):

.. list-table::
   :header-rows: 1
   :widths: 40 20 20 20

   * - Pair
     - PIN rows
     - Target PSMs at q < 0.01
     - Target peptides at q < 0.01
   * - ``PXD002914``: ``ph050213_18`` + ``ph280213_35``
     - 40,835
     - **1,466**
     - **982**
   * - ``PXD000211``: ``LCQ6495`` + ``LCQ6524``
     - 36,105
     - **1,104**
     - **607**

For scale, the K562 mzML pair of ``docs/feasibility/scientific-path.rst`` gives
1,026 PSMs and 603 peptides.

Borderline -- CC0, Percolator trains, PSMs at 1% FDR but no peptides
---------------------------------------------------------------------

These two meet the letter of tier 1 at PSM level only. A smoke test that asserts
peptide-level identifications at 1% FDR would fail on them. Treat them as tier 2
for planning.

.. list-table::
   :header-rows: 1
   :widths: 4 16 18 10 8 8 12 12 12

   * - No.
     - Dataset
     - File
     - Bytes
     - MS1
     - MS2
     - PIN rows (target / decoy)
     - PSMs / peptides at q < 0.01
     - FASTA used
   * - 10
     - ``MSV000079277`` (``PXD003649``)
     - ``20100504_TAP19_07.RAW``
     - 9,137,813
     - 442
     - 2,652
     - 12,934 (6,811 / 6,123)
     - 95 / **0**
     - ``UP000002194_882``, 3,518 records, 1,808,448 bytes
   * - 11
     - ``MSV000080394`` (``PXD005560``)
     - ``HuHPXFS410a.RAW``
     - 9,975,570
     - 497
     - 2,485
     - 23,440 (12,445 / 10,995)
     - 135 / **0**
     - human ``UP000005640_9606``, 20,652 records, 13,731,881 bytes

Tier 2 -- some plausible PSMs, Percolator reports nothing at 1% FDR
--------------------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 18 24 10 8 14 26

   * - Source
     - File
     - Bytes
     - MS2
     - Rank-1 PIN rows (target / decoy)
     - Note
   * - ``MSV000079053`` (``PXD001860``), CC0
     - ``SalTyph_StatPhas_NanoMate_31Jan06_01.RAW``
     - 9,245,541
     - 492
     - 156 / 77
     - LTQ Orbitrap, 5-minute chip infusion of one fraction; a real excess of
       targets, too few to train
   * - ``PXD000129``, EBI terms of use
     - ``Dredd_0_C_1_G091215_0965_c_ymv.RAW``
     - 9,457,386
     - 474
     - 250 / 224
     - LTQ FT, *Drosophila* protein-interaction study (by the project
       title); Percolator's initial direction separated about 22 PSMs, final
       result 0

Evaluated and not usable
------------------------

.. list-table::
   :header-rows: 1
   :widths: 24 26 10 8 32

   * - Source
     - File
     - Bytes
     - MS2
     - Why
   * - ``PXD000211``
     - ``LCQ6860.RAW``
     - 5,596,446
     - 1,689
     - rank-1 998 target / 953 decoy: random. A different sample series of
       the same project; not every file in ``PXD000211`` is usable
   * - ``PXD000320``
     - ``QC_Shew_12_02_Run-07_11Sep12_Eagle_12-01-12.RAW``
     - 9,182,424
     - 1,272
     - 15,398 MS1 against 1,272 MS2: a failed QC run. The small files of this
       data-quality collection are small because they failed
   * - ``PXD000301``
     - ``peptide12323.RAW``
     - 6,810,493
     - 1,570
     - 2-D gel spot: one protein (S100-A9, four PSMs), everything else random
   * - ``PXD003285``
     - ``peptide12986.RAW``
     - 7,252,489
     - 1,454
     - gel slice of an affinity purification; rank-1 982 target / 1,090 decoy
   * - ``MSV000079277``
     - ``20091023_DVU_14.RAW``
     - 9,782,743
     - 3,282
     - rank-1 1,724 target / 1,711 decoy: random
   * - ``MSV000079053``
     - ``Ecoli_F1_04162012_Aragon.RAW``
     - 4,754,868
     - 117
     - a 7-minute fragment of a run
   * - ``fgcz/rawrr``
     - ``inst/extdata/sample.raw``
     - 1,531,188
     - 547
     - the first 0.78 minutes of a run; best Xcorr 0.5
   * - ``smith-chem-wisc/mzLib``
     - ``Test/FlashLFQ/TestData/sliced-raw.raw``
     - 689,578
     - 90
     - best Xcorr 0.48 against the human proteome
   * - ``compomics/ThermoRawFileParser`` (the same 1,504,354-byte file is in
       mzLib, ms_deisotope and tools-galaxyp)
     - ``small.RAW``
     - 1,504,354
     - 34
     - too few scans: 48 spectra, as the pwiz example
       ``fixture-candidates.rst`` rejected (candidate 6)
   * - ``compomics/ThermoRawFileParser``
     - ``small2.RAW``
     - 2,348,553
     - 49
     - too few scans (converted and counted, not searched)
   * - mzLib, MetaMorpheus, tools-galaxyp
     - ``sliced_ethcd.raw``, ``slicedMouse.raw``, ``sliced_glyco_hcd_ethcd.raw``,
       ``ScanDescriptionTestData.raw``, ``TestCompensationVoltageReading.raw``,
       ``really_small.raw``
     - 0.5--7.1 MB
     - 0--39
     - too few scans (converted and counted, not searched)

Tier 3 -- plausible on metadata only, not evaluated
----------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Source
     - What and why it is plausible
   * - ``PXD002914``
     - ``ph280213_37.raw`` (about 7.9 MB by the directory listing): the one
       remaining file of this project under 10 MB. The other 187 RAW files
       are 10 MB or more.
   * - ``PXD000211``
     - About 90 further ``LCQ6196``--``LCQ6245`` and ``LCQ6494``--``LCQ6541``
       files of 6--10 MB, the two run series all five tier-1 files came from
       (sizes from the directory listing; one or two sit just over 10 MB).
   * - ``MSV000079277``
     - ``20100504_TAP19_03.RAW`` (8,999,358), ``_10``, ``_05``, ``_04`` and
       three others between 7 and 10 MB that are not washes, gel bands or
       blanks by name (the other 997 files in that size range are). One of the
       two tried worked marginally, the other not at all.
   * - ``MSV000079138`` (``PXD002302``)
     - ``Sc_Ino80_TAP_L2_P4_Ti_201a.RAW`` (8,132,571 bytes): yeast INO80 TAP,
       one MudPIT step, LTQ. One download attempt; MassIVE answered 429.
   * - ``MSV000080128`` (``PXD004905``)
     - ``20090826_Ctherm_SWG_100ug_18hr_Bio1_Run1_LTQ3_01.RAW`` (4,694,683
       bytes): *C. thermocellum* lysate, first SCX step, LTQ XL. Queued
       behind the yeast file and never attempted.
   * - ``PNNL-Comp-Mass-Spec/Thermo-Raw-File-Reader`` and
       ``frallain/pymsfilereader``
     - ``Shew_246a_LCQa_15Oct04_Andro_0904-2_4-20.RAW``: a PNNL *Shewanella*
       LCQ run and by its name a real sample. **10,108,374 bytes -- 10.11 MB,
       over the limit by 1%, so it was not downloaded.** It is 9.64 MiB. If
       the owner's "10 MB" was meant loosely this is the first file to try.
   * - ``PXD001664``, ``PXD002873``, ``PXD001930``, ``PXD000012``
     - Hundreds of 4--9 MB LCQ/LTQ files. ``PXD001664`` and ``PXD002873``
       state 2-D gels in their protocols (one protein per file) and are
       organisms that were not matched to a reference proteome; the other two
       are presumed 2-D gel spots from their titles. The ``PXD000301`` result
       predicts failure; not tried.
   * - ``PXD001852``, ``PXD004971``, ``PXD004669``, ``PXD019538``,
       ``PXD027295``, ``PXD030549``
     - One to nine small files each among much larger ones; by their names the
       small ones are washes, last salt steps or stray runs. ``PXD010631``
       and ``PXD075203`` (CC0) hold many small files but are synthetic peptide
       standards and intact-mass measurements; ``PXD000162`` is annotated with
       MALDI instruments only.

Candidates 1 to 4 -- ``PXD002914``
===================================

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Field
     - Value
   * - Title
     - Low temperature adaptation in marine Synechococcus
   * - Publication
     - Varkey D *et al.*, ISME J. 2015, doi ``10.1038/ismej.2015.179``
   * - Submitter
     - Deepa Varkey / Ian Paulsen, Macquarie University
   * - Licence, as stated
     - ``EBI terms of use`` -- the ``license`` field of
       ``https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD002914``, read
       2026-11-12
   * - Submitted / public
     - 2015-09-15 / 2015-10-27; submission type ``PARTIAL``
   * - Sample (from the project's protocol text)
     - whole-cell lysate of *Synechococcus* WH8102 and BL107, 150 micrograms
       fractionated by 1-D SDS-PAGE, in-gel reduction, alkylation and tryptic
       digestion, each fraction run by nanoLC on a Velos Pro ion trap
   * - Instrument (read from the RAW file)
     - ``Velos Pro``; filter strings ``ITMS + c ESI Full ms`` and
       ``ITMS + c ESI d Full ms2 ...@cid35.00``: data-dependent, ion-trap MS1
       and MS2, centroided, 50-minute runs
   * - Download
     - ``https://ftp.pride.ebi.ac.uk/pride/data/archive/2015/10/PXD002914/ph050213_18.raw``
       and likewise ``ph280213_35.raw``, ``ph280213_36.raw``,
       ``ph071212_DV_07.raw``

.. list-table::
   :header-rows: 1
   :widths: 20 12 68

   * - File
     - Bytes
     - SHA-256 (computed here) and MD5
   * - ``ph050213_18.raw``
     - 9,961,477
     - ``b5ebbb2e24dcbdbeeb44ddff690c102f7901a43c5d004edc501dadf9c9e01c6c``;
       MD5 ``12a12262a3e28eaf265efb1a6c1a095d``
   * - ``ph280213_35.raw``
     - 9,470,344
     - ``75b9eb0db660bda39e38a82a31d2a1bef35aacdc27f2f41f78c67c360850d60a``;
       MD5 ``e1cfb9ad031f3380ff342ed8e74a8bae``
   * - ``ph280213_36.raw``
     - 8,796,695
     - ``c283a0ca7d5f2f6631abf5c2cc08be19f725b7bd6af7707fd00c70c68f25572c``;
       MD5 ``7221b1dc0083405cd7eb85265ce6c238``
   * - ``ph071212_DV_07.raw``
     - 9,419,062
     - ``858672c364532f0b76175fc56209a56290679da7202162fdf1173e0040201270``;
       MD5 ``2d3f99db7e7d68069c1af8d896a78e7c``

All four MD5 values equal the ones PRIDE publishes at
``https://www.ebi.ac.uk/pride/ws/archive/v3/files/checksum/PXD002914``. That
endpoint is new information relative to ``fixture-candidates.rst``, which
recorded that PRIDE publishes no checksum: the per-file ``checksum`` field is
still empty, but the per-project checksum listing returns an MD5 and a size for
every file.

.. list-table:: Measured results
   :header-rows: 1
   :widths: 32 17 17 17 17

   * - Quantity
     - ``ph050213_18``
     - ``ph280213_35``
     - ``ph280213_36``
     - ``ph071212_DV_07``
   * - Spectra (MS1 / MS2)
     - 10,618 (8,558 / 2,060)
     - 5,836 (3,006 / 2,830)
     - 7,674 (5,112 / 2,562)
     - 3,879 (1,270 / 2,609)
   * - mzML written by the converter, bytes
     - 47,227,367
     - 33,632,273
     - 38,030,657
     - 26,897,997
   * - Spectra with no peaks (converter warnings)
     - 1,041
     - 32
     - 45
     - 118
   * - Spectra Comet loaded (2+ and 3+ assumptions)
     - 3,624
     - 4,544
     - 4,320
     - 4,538
   * - PIN rows (target / decoy)
     - 18,120 (9,885 / 8,235)
     - 22,715 (12,062 / 10,653)
     - 21,595 (11,449 / 10,146)
     - 22,690 (11,860 / 10,830)
   * - Rank-1 PIN rows (target / decoy)
     - 2,378 / 1,246
     - 2,679 / 1,864
     - 2,607 / 1,712
     - 2,601 / 1,937
   * - Target PSMs at q < 0.01 (decoy PSMs at q < 0.01)
     - **906** (8)
     - **475** (3)
     - **509** (4)
     - **420** (3)
   * - Distinct scans behind those PSMs
     - 872
     - 466
     - 508
     - 402
   * - Target peptides at q < 0.01 (decoy)
     - **787** (7)
     - **360** (2)
     - **467** (3)
     - **348** (2)
   * - Percolator 3.09 on the same PIN
     - 906 / 787
     - 475 / 360
     - 509 / 467
     - 420 / 348

Among the q < 0.01 PSMs of ``ph050213_18`` there are 279 distinct first-listed
protein identifiers; the most frequent are RNA polymerase subunit ``RPOC2``,
phycoerythrin ``PHEB2`` and elongation factor G -- what a cyanobacterial
lysate should give.

Two things to know about these files:

* ``ph050213_18.raw`` contains **1,041 spectra with no peaks** (the converter
  warns ``Spectrum #N has no m/z data``); the other three have 32 to 118.
  Counted in the mzML: 1,032 of the 1,041 are empty MS1 scans and 9 are empty
  MS2 scans; the other files have 21 to 103 empty MS1 and 11 to 17 empty MS2
  scans. They did not affect the search here. Whether Comet's Windows RAW
  reader is equally indifferent is unverified.
  ``ph280213_35.raw`` and ``ph280213_36.raw`` are the cleanest pair;
  ``ph050213_18.raw`` identifies the most.
* The project holds two strains and does not say which file is which. All
  four files, from three acquisition series, were searched against WH8102
  only and all identify well. The BL107 proteome (``UP000004390``) returned
  no sequences from UniProt's REST stream and was not used.

Candidates 5 to 9 -- ``PXD000211``
===================================

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Field
     - Value
   * - Title
     - Arabidopsis nanodomain-delimited ABA signaling pathway regulates the
       anion channnel SLAH3 (spelling as in PRIDE)
   * - Publication
     - Demir F *et al.*, PNAS 2013, doi ``10.1073/pnas.1211667110``;
       dataset doi ``10.6019/PXD000211``
   * - Licence, as stated
     - ``EBI terms of use`` -- the ``license`` field of
       ``https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD000211``, read
       2026-11-12
   * - Submitted / public
     - 2013-04-11 / 2013-05-02; submission type ``COMPLETE``
   * - Sample (from the project description)
     - detergent-resistant membrane fractions of *Arabidopsis thaliana*
       mesophyll cells, in-solution and in-gel digested; carbamidomethyl
       cysteine fixed, per the submitters' own Mascot settings
   * - Instrument
     - PRIDE says ``LCQ Deca XP Plus``; the RAW file itself (``LCQ6524``)
       says ``LCQ Deca XP``, acquired 2008-02-13, RAW format version 50. Filter
       strings ``+ c ESI Full ms`` and ``+ c ESI d Full ms2 ...@cid35.00``:
       data-dependent, ion-trap MS1 and MS2, centroided, 55-minute runs
   * - Download
     - ``https://ftp.pride.ebi.ac.uk/pride/data/archive/2013/05/PXD000211/LCQ6524.RAW``
       and likewise for the others

.. list-table::
   :header-rows: 1
   :widths: 16 12 72

   * - File
     - Bytes
     - SHA-256 (computed here) and MD5
   * - ``LCQ6524.RAW``
     - 9,844,673
     - ``9aebf28db3a1c1de08dc35c68da2ca9fbe6a69b2ac1c44d4dcf14728e13fdadd``;
       MD5 ``946c4e8c3193307a41bc9518dd08085a``
   * - ``LCQ6495.RAW``
     - 9,890,598
     - ``dcbf8980312c42160d3645af42de26f4ad34721fea860306031d927b60fd367a``;
       MD5 ``0dd2d06fe6c9577126607d638daf9199``
   * - ``LCQ6224.RAW``
     - 7,804,595
     - ``a3ead88dca96f37de6188b2f3a72d48715f290aedb3f99656c302f9d8307e341``;
       MD5 ``bdaae663687ab0e47ca80d0255b644dc``
   * - ``LCQ6519.RAW``
     - 9,734,877
     - ``e83caaec28f710344dfe626072bac358d2b82d059d0615c874eb5a83ffb4bb7f``;
       MD5 ``35c1e1b6d3df2a49a85a5638a82ee8fc``
   * - ``LCQ6509.RAW``
     - 9,623,753
     - ``6d17c047cd8be5c757980a3bb2c5ae19700b2cd5f07013474eeb9b8d110c7d1d``;
       MD5 ``3db599a44f034e6fd27c2cb0b8cb26c0``

All five MD5 values equal PRIDE's at
``https://www.ebi.ac.uk/pride/ws/archive/v3/files/checksum/PXD000211``.

Distinct scans behind the q < 0.01 PSM counts: ``LCQ6524`` 548, ``LCQ6495`` 445,
``LCQ6224`` 379, ``LCQ6519`` 262, ``LCQ6509`` 230. The identified proteins are
what a plasma-membrane preparation should give (plasma-membrane ATPases
``PMA1``/``PMA2``, ABC transporter ``AB36G``, aquaporins).

Why these rank below ``PXD002914``: fewer identifications per file, an older
instrument writing an old RAW format version, and a FASTA thirteen times larger
(14.4 MB against 1.08 MB).

Candidates 10 and 11 -- the CC0 files, and why they are weaker
===============================================================

``MSV000079277`` / ``PXD003649`` -- ``20100504_TAP19_07.RAW``
--------------------------------------------------------------

* "Bacterial Interactomes: Desulfovibrio vulgaris_TAP_part 2_LTQ Data",
  principal investigator Gareth Butland; tandem affinity purifications from
  *Desulfovibrio vulgaris* Hildenborough.
* Licence, as stated on
  ``https://massive.ucsd.edu/ProteoSAFe/dataset.jsp?accession=MSV000079277``:
  "dataset license: CC0 1.0 Universal (CC0 1.0)".
* Instrument, from the file: ``LTQ XL``, data-dependent CID, 20-minute run.
* 9,137,813 bytes; SHA-256
  ``d0234b7114230c1a3d0153cce697a3eb9b53c7e223a4bad50c9f0d7de6aced15``.
* Result: 95 target PSMs at q < 0.01 (93 distinct scans, 76 distinct peptide
  sequences, 0 decoys), **0 peptides at q < 0.01** -- the best peptide q-value
  is 0.0121, just over the line; 102 peptides at q < 0.05. Percolator 3.09
  agrees (95 / 0). It is an affinity purification: a few proteins, seen often.
* A second file of the same dataset, ``20091023_DVU_14.RAW``, gave nothing.

``MSV000080394`` / ``PXD005560`` -- ``HuHPXFS410a.RAW``
--------------------------------------------------------

* "Identification of oxidative modifications of hemopexin and their
  physiological relevance", Ann Smith; purified hemopexin.
* Licence, as stated on
  ``https://massive.ucsd.edu/ProteoSAFe/dataset.jsp?accession=MSV000080394``:
  "dataset license: CC0 1.0 Universal (CC0 1.0)".
* Instrument, from the file: ``LTQ``. **Not data-dependent**: the filter
  strings are ``ITMS + c ESI Full ms2 ...@cid35.00`` and ``...@cid20.00``
  with no ``d`` -- by that reading a targeted method that fragments a fixed
  precursor list.
* 9,975,570 bytes; SHA-256
  ``3f10251cc6865bec6240a5c3d6287c8cbc9418f73c5338b378799a8c9e93303d``.
* Result: 135 target PSMs at q < 0.01, 134 of them hemopexin, covering only
  **3 distinct peptides**; 0 peptides at q < 0.01 or at q < 0.05. This fails
  the owner's "DDA of a real sample" criterion; it is listed only because it
  is CC0 and does make Percolator train.

MassIVE download, and a cost that matters for CI
-------------------------------------------------

The files were fetched over HTTPS as
``https://massive.ucsd.edu/ProteoSAFe/DownloadResultFile?file=f.<MSV>/<path>&forceDownload=true``.
The dataset pages give ``ftp://massive-ftp.ucsd.edu/v01/<MSV>/`` as the
download link; FTP does not connect from this host, so that route is
unverified. **MassIVE rate-limits hard**: five files came down, with ``429 Too
Many Requests`` answers in between, after which the HTTPS endpoint answered 429
to all six further attempts spread over about twenty minutes
(``api/massive-get2.log``, ``api/massive-get3.log``); the queue was then
stopped. A CI job fetching from MassIVE per run would be fragile. No per-file
checksum was looked for beyond the dataset page.

FASTA files used
================

All from UniProt reference proteomes, ``current_release`` (the release notes
read "UniProt Release 2026_04"), except the human one, which is the existing
``scratch/fixture/UP000005640_9606.fasta``. The UniProt licence was not re-read
by this scout; ``fixture-candidates.rst`` records it as CC BY 4.0 and records
that these URLs are not immutable.

.. list-table::
   :header-rows: 1
   :widths: 24 18 9 12 37

   * - File
     - Organism
     - Records
     - Bytes (plain / gz)
     - SHA-256 of the plain file
   * - ``UP000001422_84588.fasta``
     - *Synechococcus* WH8102 (UniProt now names it *Parasynechococcus
       marenigrum*)
     - 2,513
     - 1,080,427 / 512,556
     - ``b4c8aaf48778566e87b0517af6f861bf0b93b33a3ebd95fd071bab9a6dd1750d``
   * - ``UP000006548_3702.fasta``
     - *Arabidopsis thaliana*
     - 27,368
     - 14,390,358 / 7,773,158
     - ``4a70c4bd86371991e75bf8c12460353bc4585e59bd61ee42887c60ddd930e64e``
   * - ``UP000002194_882.fasta``
     - *D. vulgaris* Hildenborough
     - 3,518
     - 1,808,448 / 760,575
     - ``e97cd60243f1a4a4c900372b2bbfe2d85a3aaf4c4b6d23ede15e45399eff3b2c``
   * - ``UP000005640_9606.fasta``
     - human (existing file)
     - 20,652
     - 13,731,881
     - ``2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0``
       (as recorded in ``fixture-candidates.rst``)
   * - ``UP000001014_99287.fasta``
     - *Salmonella typhimurium* LT2
     - 4,533
     - 2,110,829 / 1,021,344
     - ``3616c65ec752350b58ea4a1bde164395dc52225a60583d47ae0cb02104e31e07``
   * - ``UP000000803_7227.fasta``
     - *Drosophila melanogaster*
     - 13,814
     - 9,058,091 / 4,969,187
     - ``4e0b1ddf833b28ac31259eb77e7f823e0bd60e78951d562e353b8a5027a60f7b``

Also fetched and used only for rejected candidates: ``UP000008186_211586``
(*Shewanella oneidensis*, 4,068 records), ``UP000002438_208964``
(*P. aeruginosa* PAO1, 5,563), ``UP000009136_9913`` (bovine, 22,715),
``UP000000625_83333`` (*E. coli* K-12, 4,403). Fetched and never used:
``UP000002311_559292`` (yeast, 6,066), ``UP000002145_203119``
(*C. thermocellum*, 3,105).

The conversion tool
===================

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Field
     - Value
   * - Tool
     - ThermoRawFileParser, self-contained Linux build (bundles its own .NET
       runtime; needs neither Mono nor a host .NET)
   * - URL
     - ``https://github.com/CompOmics/ThermoRawFileParser/releases/download/v.2.0.0-dev/ThermoRawFileParser-v.2.0.0-dev-linux.zip``
   * - Version
     - release tag ``v.2.0.0-dev``, published 2025-10-27; ``--version`` prints
       ``2.0.0.0``. Of the ten most recent releases it is the only one with a
       self-contained Linux asset; ``v1.4.5`` and earlier ship one small zip
       that needs Mono on Linux.
   * - Size / SHA-256
     - 48,627,521 bytes;
       ``19566762ce6759a93cee9aa4cef50de1e9ae2ad9078bd95826e8e733d4bb0d52``
       (equal to the digest GitHub's release API reports for the asset)
   * - Licence
     - Apache-2.0 (``LICENSE`` in the zip). The zip also ships
       ``THERMO_LICENSE`` (SHA-256
       ``580dab0a34729ef4c8e0213a4289a4e7d807cde027e91bb7548cef944a20f22f``),
       the Thermo RawFileReader licence, which among other things forbids
       redistributing the reader to others except with one's own software and
       forbids "distributing this Software with any software that is subject
       to the General Public License (GPL) or similar license in a manner that
       would create a combined work".
   * - Where
     - ``scratch/raw-scout/tools/trfp-2.0.0-dev-linux/``. An evaluation aid in
       scratch only; not added to the project.
   * - Host quirk
     - The host has no ICU. The binary aborts without
       ``DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1``, and with that alone every
       scan fails (``en-US is an invalid culture identifier``) and it writes an
       mzML with zero spectra and a non-zero exit code. It works with
       ``DOTNET_SYSTEM_GLOBALIZATION_PREDEFINED_CULTURES_ONLY=false`` as well.

The converter bundles ``ThermoFisher.CommonCore.RawFileReader.dll``, the same
Thermo library family the specification lists as Comet's Windows RAW
companion. That these files open with it is mild supporting evidence for the
Windows path, not proof.

Exact commands
==============

Everything is scripted in ``scratch/raw-scout/scripts/eval_raw.sh``, which
writes into ``scratch/raw-scout/eval/<name>/``. For candidate 1, from the
repository root::

   scratch/raw-scout/scripts/eval_raw.sh \
       scratch/raw-scout/data/PXD002914-ph050213_18/ph050213_18.raw \
       scratch/raw-scout/eval/PXD002914-ph050213_18 \
       $PWD/scratch/raw-scout/fasta/UP000001422_84588.fasta \
       "peptide_mass_tolerance_upper = 3.0" \
       "peptide_mass_tolerance_lower = -3.0" \
       "peptide_mass_units = 0" \
       "isotope_error = 0" \
       "fragment_bin_tol = 1.0005" \
       "fragment_bin_offset = 0.4" \
       "theoretical_fragment_ions = 1"

The other ten candidates used the same seven overrides and differ only in
the RAW file and the FASTA. What the script runs, in order::

   export DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1
   export DOTNET_SYSTEM_GLOBALIZATION_PREDEFINED_CULTURES_ONLY=false
   scratch/raw-scout/tools/trfp-2.0.0-dev-linux/ThermoRawFileParser \
       -i <file>.raw -b <out>/<base>.mzML -f 2 -m 0 -c <out>/<base>-metadata.json -l 2

   python3 -I scratch/raw-scout/scripts/mzml_summary.py <out>/<base>.mzML

   sed -e "s|^database_name = .*|database_name = <fasta>|" \
       scripts/feasibility/comet.params > <out>/comet.params
   # then each override replaces its line; the diff is kept as comet.params.diff
   ( cd <out> && scratch/upstream/comet.linux.exe -P<out>/comet.params <base>.mzML )

   scratch/percolator/3.07.1-linux-x86_64/usr/bin/percolator \
       -m <out>/percolator/psms.target.txt     -M <out>/percolator/psms.decoy.txt \
       -r <out>/percolator/peptides.target.txt -B <out>/percolator/peptides.decoy.txt \
       -w <out>/percolator/weights.txt <out>/<base>.pin

The binaries are the ones ``scientific-path.rst`` used: Comet 2026.02.2,
Percolator 3.07.1, and for the cross-check
``scratch/percolator/3.09/run-percolator-3.09.sh`` with the same flags.

Parameter changes, and why
--------------------------

Seven lines differ from ``scripts/feasibility/comet.params``, besides
``database_name``. All seven follow from one fact read out of each file's
filter strings: MS1 and MS2 are both low-resolution ion-trap scans.

.. list-table::
   :header-rows: 1
   :widths: 34 14 14 38

   * - Parameter
     - Project file
     - Used here
     - Why
   * - ``peptide_mass_tolerance_upper`` / ``_lower``
     - ``20.0`` / ``-20.0``
     - ``3.0`` / ``-3.0``
     - ion-trap precursor masses are good to about a dalton, not 20 ppm
   * - ``peptide_mass_units``
     - ``2`` (ppm)
     - ``0`` (amu)
     - as above
   * - ``isotope_error``
     - ``3``
     - ``0``
     - a 3 Da window already covers isotope mis-assignment
   * - ``fragment_bin_tol``
     - ``0.02``
     - ``1.0005``
     - Comet's documented low-resolution ion-trap setting
   * - ``fragment_bin_offset``
     - ``0.0``
     - ``0.4``
     - as above
   * - ``theoretical_fragment_ions``
     - ``0``
     - ``1``
     - as above

Unchanged and relevant: ``decoy_search = 1``, trypsin, two missed cleavages,
fixed ``add_C_cysteine = 57.021464``, variable methionine oxidation,
``num_output_lines = 5``, ``precursor_tolerance_type = 1``. The fixed cysteine
modification is stated in the protocols of ``PXD002914`` ("alkylation") and
``PXD000211``; it is an assumption for the two MassIVE files. No candidate uses
TMT, SILAC or another label. No tolerance was tuned to improve a result; the
3 Da window was the first value tried and the only one used on any tier-1 or
borderline file. The one file searched twice is the tier-2 *Salmonella* file,
which has Orbitrap MS1: a second run with the project's 20 ppm window
(``eval/MSV000079053-sal01-ppm``) did no better.

Percolator printed its mix-max warning (``not well behaved when # targets !=
# decoys``) on five of the nine tier-1 runs and on both borderline ones, as it
does on the project's K562 fixture; see "A caveat on the q-values" in
``scientific-path.rst``. ``-Y`` was not tried.

How the search was done
=======================

* **PRIDE.** The API has no cross-project file search by size, and
  ``/projects/{acc}/files/all`` streams too slowly for large projects (it timed
  out at 120 s on one). The workable route is
  ``/search/projects`` (instrument filter or keyword), then
  ``/projects/files-path/{acc}`` for the archive path, then the HTTPS directory
  listing on ``ftp.pride.ebi.ac.uk``, which shows a rounded size for every file
  in one request. ``scripts/pride_scan.py`` does this and caches everything.
  Exact sizes were then taken with HEAD before any download.
* Scanned: every project with instrument ``LTQ`` (410), ``LTQ Velos`` (50),
  ``LTQ XL`` (38), ``LTQ XL ETD`` (4), ``LCQ Classic`` (59),
  ``LCQ Deca XP Plus`` (19), ``LCQ Deca`` (8), ``LXQ`` (1), the first 150 of
  ``LTQ FT`` (capped), keyword searches ``LCQ`` (98) and ``"ion trap" LTQ``
  (83), and 179 ion-trap projects announced or re-announced since mid-2018,
  taken from ProteomeCentral. In all **792 projects, 580 with RAW files,
  42,345 RAW files**; 922 RAW files between 0.3 and 10 MB, in **19 projects**.
  Six of those projects were sampled by download.
* The reasoning behind the instrument choice: a modern Orbitrap stores several
  kilobytes per MS2 scan plus large MS1 scans, so a 10 MB file is a minute or
  two of acquisition. An ion-trap-only instrument stores a full 50-minute run
  with 2,000--3,000 MS2 scans in 8--10 MB. Every usable file found is of that
  kind.
* **MassIVE.** Its own dataset-query endpoint returned HTTP 500 to every form
  tried. ProteomeCentral's PROXI API
  (``https://proteomecentral.proteomexchange.org/api/proxi/v0.1/datasets``)
  lists MassIVE-hosted datasets with instrument; the GNPS dataset cache
  (``https://datasetcache.gnps2.org/datasette/database/filename``) holds every
  MassIVE file with its size and is queryable per dataset.
  ``scripts/massive_scan.py`` combined them: 598 MassIVE datasets seen, 139 on
  ion-trap-only instruments, **10 with a RAW file between 2 and 10 MB**; three
  of those were sampled by download.
* **Zenodo.** Two keyword queries restricted to records holding ``.raw`` files
  (242 and 219 records). Ten and nine had a small ``.raw``; none was
  bottom-up proteomics (metabolomics, native MS, intact protein, genotype
  files).
* **Open-source projects.** The file trees of thirteen repositories were listed
  for ``.raw`` blobs: ThermoRawFileParser, rawrr, mzLib, MetaMorpheus,
  tools-galaxyp, pymsfilereader, PNNL Thermo-Raw-File-Reader, ms_deisotope,
  alphapept, alpharaw, RawTools, thermofisherlsms/RawFileReader, UWPR/Comet.
  The last five hold none. Repository licences read: ThermoRawFileParser
  Apache-2.0, rawrr GPL-3, mzLib LGPL-3.0, MetaMorpheus MIT, tools-galaxyp
  MIT, pymsfilereader MIT, PNNL reader BSD-2-Clause. As
  ``fixture-candidates.rst`` says of Crux and OpenMS, those grants cover the
  software; none documents the provenance of its RAW files.

What led nowhere
================

* **Tool test data.** Every RAW file a tool ships is either a few dozen scans
  or a "sliced" fragment of a run: 5 to 90 MS2 scans, apart from rawrr's
  547, which are the dead volume at the start of a run. None identifies
  anything.
* **Small files inside otherwise large datasets.** In the three cases tried
  (``PXD000320``, ``PXD000129``, the PNNL *E. coli* file) the file was small
  because the run failed, was aborted, or was a low-yield control.
* **Gel-spot and gel-slice datasets of simple samples.** They supply hundreds
  of 5 MB LCQ/LTQ files, each holding one or a few proteins. ``PXD000301``
  (2-D gel spots) and ``PXD003285`` (slices of an affinity purification) both
  gave random results.
* **Recent PRIDE projects on ion traps** -- the population where CC0 is
  likely; their licences were not read one by one. Of the 152 with RAW files,
  only four have any RAW under 10 MB and none of those is a usable digest (one
  is intact-mass data, one an empty file, two are single small files among
  large ones). The smallest file in every other project of that set is 11 MB
  or more.
* **ISB's standard protein mix** (the 18-protein mix, LCQ and LTQ):
  ``regis-web.systemsbiology.net/PublicDatasets/`` now returns 404. Not
  pursued further.
* **PRIDE's facet endpoint** (``/facet/projects``) returns HTTP 500, so the
  exact instrument vocabulary had to be guessed; ``LTQ Velos Pro``,
  ``LCQ Fleet``, ``LCQ Deca XP`` and ``LCQ Advantage`` matched nothing.

What could not be verified
==========================

#. **The Windows path.** Nothing here shows that Comet on Windows opens these
   RAW files. Specific risks seen: RAW format version 50 from 2008
   (``PXD000211``); 1,041 empty spectra in ``ph050213_18.raw`` and a handful
   of empty MS2 scans in every ``PXD002914`` file; MS2 scans without a
   precursor charge in every candidate, so Comet must assign 2+/3+ itself as
   it did from mzML.
#. **Redistribution.** ``EBI terms of use`` is what PRIDE states for
   ``PXD002914`` and ``PXD000211``. What that permits is the owner's question;
   ``fixture-candidates.rst`` (candidate 3) records the earlier reading of
   those terms. The CC0 statements were read on the MassIVE dataset pages, not
   in a machine-readable field.
#. **Which strain each ``PXD002914`` file is.**
#. **Three MassIVE files were never obtained**: ``20100504_TAP19_03.RAW``
   (five attempts, all 429), the yeast INO80 file (one attempt, 429) and the
   *C. thermocellum* file (never attempted). The licence of the last two
   datasets was not read. A *C. thermocellum* lysate on an LTQ XL is the most
   promising untested lead for a file that is both CC0 and a clean tier 1,
   if its dataset page says CC0 (the four MassIVE dataset pages read here all
   did).
#. **The 10.11 MB PNNL Shewanella LCQ file** was not downloaded because it is
   over the stated limit.
#. **Coverage.** This was a scouting pass. Not searched: jPOST, iProX,
   PeptideAtlas, MassIVE datasets without a ProteomeXchange accession, PRIDE
   projects whose instrument is annotated only as an Orbitrap hybrid, and
   ``LTQ FT`` projects beyond the first 150. The PRIDE keyword scans for
   ``"LTQ XL"``, gel bands, BSA, teaching data and *E. coli*/yeast on LTQ were
   started and stopped before finishing, in favour of the ProteomeCentral
   list.
#. **One search per file.** No alternative parameters, no second seed.
   ``PXD000211`` shows that files of one project can differ from 571 PSMs to
   none, so any file not listed as measured must be measured
   before it is relied on.

Measured here versus read from metadata
=======================================

Measured here, from files written on this host: every byte count and SHA-256
or MD5 of a downloaded file; every MS1/MS2 count, instrument name and filter
string (parsed from the converted mzML or the converter's metadata JSON); every
PIN, PSM and peptide count; FASTA record counts and sizes.

Read from metadata and not independently checked: project titles, organisms,
sample protocols, publication references and licence statements (PRIDE API,
MassIVE dataset pages); sizes of files that were not downloaded (PRIDE
directory listings and HEAD, the GNPS dataset cache, GitHub tree listings);
everything in the tier-3 table.

Downloads made
==============

29 RAW files, none over 10,000,000 bytes, 185.0 MB in total, each in its own
directory under ``scratch/raw-scout/data/``; eleven FASTA files (29.3 MB
compressed); the converter zip (48.6 MB). Scripts are in
``scratch/raw-scout/scripts/``, API responses in ``scratch/raw-scout/api/``,
evaluation output in ``scratch/raw-scout/eval/``. No tracked file was created,
edited or deleted; nothing was installed on the host; no build, gate or
documentation build was run.
