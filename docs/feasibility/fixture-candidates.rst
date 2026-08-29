==========================================================
Fixture candidates for ``D-006`` -- a costed shortlist
==========================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 6 of 9 -- fixture candidates and ephemeral proof input
:Date: 2026-08-29
:Serves: ``D-006`` (test fixture data and licensing) -- **OPEN**
:Status: Evidence. This document decides nothing.

.. warning::

   **This document does not choose the project's fixture.** ``D-006`` -- which
   spectra and FASTA are CometGUI's fixtures, under what licence, vendored or
   fetched -- is an open owner decision and remains open after this document.
   What follows is a costed shortlist plus the evidence behind it, so that the
   owner can answer with the trade-offs visible. An agent that picked the
   dataset here would have made a serious error, not a shortcut
   (``ONBOARDING.rst``, *Decisions*).

   The last section describes a *separate* thing: one small, openly licensed
   input fetched into gitignored scratch space so that Phase 00 can demonstrate
   the scientific path at all. It is a feasibility input and nothing more.

.. contents:: Contents
   :depth: 2
   :local:

Scope
=====

Phase 00's in-scope bullet is "Identify candidate test fixture data and its
licence for ``D-006``", and exit-gate item 2 requires a scripted run that
produces valid Limelight XML "from real fixture spectra". Those are two
different obligations, and this unit keeps them apart:

* the **shortlist** below, which is what ``D-006`` is decided from; and
* the **ephemeral feasibility input**, fetched under ``scratch/`` (gitignored)
  so that work unit 8 can run Comet at all. It is not a candidate, not a
  recommendation, and not a fixture.

Nothing downloaded by this unit is committed. Committing a spectrum file or a
FASTA would pre-empt ``D-006``.

What ``D-006`` demands of a fixture
===================================

From ``DECISIONS.rst``, ``D-006``, verbatim constraints:

* small enough to run in CI;
* real enough to produce **both target and decoy PIN rows**;
* redistributable in a public repository **or** fetchable reproducibly by
  checksum;
* stable for the life of the goldens;
* **at least two spectrum files**, to exercise the multi-file run model.

Two of these deserve sharper wording than they usually get.

**"Publicly downloadable" is not "licensed for redistribution".** Anyone may
download PXD000001; that says nothing about the right to copy it into a public
git repository. Every licence claim below therefore carries the URL where it
was read, and where the licence is ambiguous this document says so rather than
rounding up to "it's public data".

**"Both target and decoy PIN rows" is not a property of the data.** Comet
generates decoys internally (``decoy_search``), so any real spectra searched
against a real database yields decoy rows. What the data has to supply is
enough genuine *target* identifications for Percolator to train on. A fixture
that produces five confident PSMs satisfies the letter of the constraint and
fails its purpose.

Method, and what "verified" means here
======================================

Everything in the tables was obtained today, 2026-08-29, from the source
named, using only the Python standard library. Specifically:

* **File sizes** are ``Content-Length`` from an HTTP ``HEAD``/``GET`` against
  the actual download URL, or ``fileSizeBytes`` from the PRIDE API -- not
  estimates, and not read off a web page. ``MB`` below means 10\ :sup:`6`
  bytes.
* **Spectrum counts** are from parsing the downloaded mzML with
  ``xml.etree.ElementTree`` and counting ``ms level`` cvParams per
  ``<spectrum>``. Where a file was not downloaded, this document says the
  counts are unverified rather than guessing them.
* **Acquisition details** (instrument, activation, MS2 analyser) are read out
  of the mzML itself -- ``instrumentConfiguration``, the activation cvParam,
  and the Thermo ``filter string`` where the converter preserved it.
* **Licences** are quoted from a file or API field that was actually fetched;
  the URL is given each time. Where the licence page could not be read (the
  PRIDE data-policy page is JavaScript-rendered and returns no text), that is
  recorded as a gap, not filled in from memory.

Candidate 1 -- Crux smoke-test spectra (upstream tool test data)
================================================================

The cheapest and licence-cleanest category is data that a tool in this
project's own dependency chain already ships as its test data. Crux is the
University of Washington toolkit from the same group as Comet, and it embeds
Comet; its smoke-test suite vendors two real Orbitrap runs.

Identity
--------

* Source: GitHub repository ``crux-toolkit/crux-toolkit``, directory
  ``test/smoke-tests/``. Not a ProteomeXchange accession -- there is no
  accession, which is itself a finding (see *Licence*).
* Pinned at commit ``fc6335cc817c8629aac07c27f2ab4584ba10930f`` (``master``
  head on 2026-08-23, read from
  ``https://github.com/crux-toolkit/crux-toolkit/commits/master.atom``).
* Organism: *Homo sapiens*, K562 cell line -- inferred from the file names.
  The repository documents no sample protocol.
* Instrument: **LTQ Orbitrap Velos**, from the mzML
  ``instrumentConfiguration``.
* Acquisition: data-dependent, **beam-type collision-induced dissociation
  (HCD)** at NCE 40, with **MS2 read out in the FT analyser** -- the preserved
  Thermo filter strings are of the form
  ``FTMS + c NSI d w Full ms2 615.79@hcd40.00 [100.00-1245.00]``. So
  high-resolution MS1 *and* high-resolution MS2. Centroided.

Files
-----

.. list-table::
   :header-rows: 1
   :widths: 40 12 12 10 26

   * - File
     - Bytes
     - MB
     - Format
     - Content (parsed)
   * - ``20100614_Velos1_TaGe_SA_K562_3.mzML``
     - 11,098,328
     - 11.10
     - mzML 1.1
     - 801 spectra: 73 MS1, **728 MS2**
   * - ``20100614_Velos1_TaGe_SA_K562_4.mzML``
     - 9,416,326
     - 9.42
     - mzML 1.1
     - 671 spectra: 61 MS1, **610 MS2**

Precursor charges present: 2+ (510 / 394), 3+ (186 / 184), 4+ (28 / 27), and a
handful of 5+ to 8+. Precursor *m/z* spans 311--1116 and 327--1096.

Download URLs
-------------

::

   https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/\
   fc6335cc817c8629aac07c27f2ab4584ba10930f/test/smoke-tests/\
   20100614_Velos1_TaGe_SA_K562_3.mzML

and the same with ``_4``. Pinning by **commit SHA** rather than by branch makes
the URL immutable: a later push to ``master`` cannot change what is served.
This was checked, not assumed -- the SHA-pinned URL and the ``master`` URL
return byte-identical content (same SHA-256). Suitable for fetch-by-checksum in
CI. ``raw.githubusercontent.com`` is not the GitHub REST API and does not
consume the 60-requests-per-hour API budget.

SHA-256:

* ``_3.mzML``:
  ``cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7``
* ``_4.mzML``:
  ``d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa``

Licence -- and the caveat that matters
--------------------------------------

The repository licence is **Apache-2.0**, read at
``https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/fc6335cc817c8629aac07c27f2ab4584ba10930f/license.txt``,
which begins:

   Copyright 2007-2013 University of Washington. Licensed under the Apache
   License, Version 2.0 (the "License"); you may not use this file except in
   compliance with the License.

``COPYRIGHT`` in the same tree reads "Crux is Copyright 2007-2016 University of
Washington".

**The caveat.** That grant is written by the University of Washington over
*Crux*. The two mzML files are third-party instrument data: the file names date
them to 14 June 2010 on a "Velos1" instrument with operator initials, and no
part of the repository documents where they came from or under what terms UW
redistributes them. So:

* using them, and fetching them by checksum, is not in any practical doubt --
  they are published by an Apache-2.0 project as its own test data;
* **vendoring them into a public CometGUI repository is a redistribution
  decision resting on UW's undocumented right to license that data**, which
  this unit could not establish.

That distinction is exactly what ``D-006`` is for, and it is why this candidate
is presented as a fetch-by-checksum option rather than a vendoring option.

Matching FASTA
--------------

None in the repository. ``test/smoke-tests/small-yeast.fasta`` (43,158 bytes,
56 *S. cerevisiae* ORFs) belongs to other tests and is both the wrong organism
and far too small a search space for stable Percolator training.

Pair instead with the UniProt human reference proteome -- see *FASTA options*
below. Combined CI cost: 20,514,654 bytes of spectra plus 7,752,225 bytes of
compressed FASTA = **28.27 MB per run**.

Fitness against ``D-006``
-------------------------

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Constraint
     - Assessment
   * - Two spectrum files
     - Met -- two independent runs, not two slices of one.
   * - Small enough for CI
     - Met -- 20.5 MB of spectra, 28.3 MB with the database.
   * - Expected Comet runtime
     - **Estimate, not a measurement**: 1,338 MS2 against 20,652 proteins with
       internal decoys should complete in well under a minute on this
       64-core host. Work unit 8 produces the measured figure.
   * - Target *and* decoy PIN rows
     - Met via ``decoy_search = 1``. Target yield should be high: complex human
       lysate, high-resolution HCD, tryptic.
   * - Redistributable
     - **Not established.** Apache-2.0 over the repository; no provenance or
       grant recorded for the data files themselves.
   * - Fetchable by checksum
     - Met, and immutably so when pinned by commit SHA.
   * - Stable for the life of the goldens
     - Good. Git objects are immutable; the risk is repository deletion or
       renaming, not content drift.

Candidate 2 -- OpenMS example data (upstream tool test data)
============================================================

The same category, a different tool, and a materially different scientific
profile.

Identity
--------

* Source: GitHub repository ``OpenMS/OpenMS``, directory
  ``share/OpenMS/examples/BSA/``, pinned at commit
  ``61f6fcc5b7b5dc8249c18e5d0dcd2f9ab2a3bfa1`` (``develop`` head on
  2026-08-28).
* Sample: a bovine serum albumin digest -- from the directory and file names;
  the repository documents no protocol.
* Instrument: **LTQ Orbitrap XL**, from the mzML ``instrumentConfiguration``.
  Processing history in the file shows ProteoWizard 1.6.0 plus OpenMS
  ``PeakPicker``/``FileFilter``/``FileMerger``, so these are re-processed, not
  raw conversions.
* Acquisition: **collision-induced dissociation**, centroided. The converter
  did not preserve Thermo filter strings, so the MS2 analyser is not stated in
  the file. It was determined by decoding the binary arrays: MS2 spectra carry
  ~30--110 peaks with ragged 0.6--1.5 Th spacing, which is
  **low-resolution ion-trap MS2**, not FT.

Files
-----

.. list-table::
   :header-rows: 1
   :widths: 26 14 10 12 38

   * - File
     - Bytes
     - MB
     - Format
     - Content (parsed)
   * - ``BSA1.mzML``
     - 13,642,066
     - 13.64
     - mzML 1.1
     - 1,684 spectra: 564 MS1, **1,120 MS2**
   * - ``BSA2.mzML``
     - 10,972,988
     - 10.97
     - mzML 1.1
     - 1,690 spectra: 524 MS1, **1,166 MS2**
   * - ``BSA3.mzML``
     - 10,475,965
     - 10.48
     - mzML 1.1
     - 1,438 spectra: 588 MS1, **850 MS2**

SHA-256: ``BSA1`` ``dc9ed61d595328d4ef2f1de47d21f41b83e2eae7c9145e1d9b88e910c8cec2f7``;
``BSA2`` ``b1a24b44fa71c0918c0078786b9618e84696334079dd9976fd927fff57c3156f``;
``BSA3`` ``b70c24e0130cdf46620715a4fcebd5fc5f23ff68d2943127edecebdc22b6e58c``.

URLs follow the same SHA-pinned ``raw.githubusercontent.com`` pattern as
candidate 1. Directory also contains ``BSA1_OMSSA.idXML`` and friends and an
``experimental_design.tsv``, which CometGUI would not use.

Licence
-------

**BSD-3-Clause.** Read at
``https://raw.githubusercontent.com/OpenMS/OpenMS/develop/LICENSE`` (also
present as ``LICENSE.md``, which is the canonical BSD-3-Clause text, and
``License.txt``, which is the same statement as ``LICENSE``). ``LICENSE`` says:

   This software is released under a three-clause BSD license

The same caveat as candidate 1 applies, and slightly more sharply: the grant is
worded over *software*, and the mzML files are data committed into that
repository with no separate statement. Provenance is undocumented, though a BSA
standard digest is the kind of run a tool group produces in-house, which makes
the position more comfortable than candidate 1's -- comfortable is not the same
as established.

Matching FASTA
--------------

None in the repository. A BSA fixture needs a bovine database, or a small
curated set containing P02769 (ALBU_BOVIN) plus a realistic background. Options
not verified by this unit: the UniProt *Bos taurus* reference proteome
(UP000009136), or a contaminants database such as cRAP. **cRAP could not be
checked**: ``https://ftp.thegpm.org/fasta/cRAP/crap.fasta`` fails TLS
verification with ``CERTIFICATE_VERIFY_FAILED ... Hostname mismatch``.

Fitness against ``D-006``
-------------------------

Three files is better than two for the multi-file model, and 3,136 MS2 spectra
is the largest target yield on offer per megabyte. Against that:

* **A single-protein sample is a weak search fixture.** Against a real bovine
  proteome the great majority of the 3,136 MS2 spectra have no correct answer,
  so the target PSM count comes from a few dozen BSA peptides seen repeatedly.
  Against a one-protein FASTA the decoy population is far too small for
  Percolator to model. Neither end of that trade is comfortable.
* **Low-resolution MS2** makes the search less discriminating than candidate 1
  or 3, and forces the ion-trap Comet fragment settings
  (``fragment_bin_tol = 1.0005``, ``fragment_bin_offset = 0.4``,
  ``theoretical_fragment_ions = 1``).
* Redistribution rights: same unresolved question as candidate 1.

Candidate 3 -- a PRIDE dataset under CC0 (PXD079076)
=====================================================

A genuinely different category: a ProteomeXchange submission whose licence is
stated explicitly, per dataset, by the repository itself.

The licence field is the point
------------------------------

PRIDE's public data-policy page could not be read directly -- it is a
JavaScript-rendered single-page application and returns no text to a plain
fetch. What *is* directly checkable, and better, is that **PRIDE's REST API
carries a per-project ``license`` field**:

.. list-table::
   :header-rows: 1
   :widths: 18 30 52

   * - Accession
     - ``license`` field
     - Read at
   * - ``PXD079076``
     - ``Creative Commons Public Domain (CC0)``
     - ``https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD079076``
   * - ``PXD063765``
     - ``Creative Commons Public Domain (CC0)``
     - ``https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD063765``
   * - ``PXD000001``
     - ``EBI terms of use``
     - ``https://www.ebi.ac.uk/pride/ws/archive/v3/projects/PXD000001``

That single field is the most useful licensing finding of this unit. It means
"a PRIDE dataset" is not a licence status: **PRIDE datasets differ from one
another, and the difference is machine-readable**. A CC0 dataset carries an
explicit public-domain dedication and may be redistributed; a dataset marked
"EBI terms of use" carries no such grant, and EMBL-EBI's own terms
(``https://www.ebi.ac.uk/about/terms-of-use/``) state that individual data
resources may impose their own terms and say nothing that amounts to a
redistribution licence. EMBL-EBI's licensing page
(``https://www.ebi.ac.uk/licencing/``) describes CC0 as the institute's
preferred direction across resources, which is consistent with the split above
but is a statement of direction, not of the status of any one dataset.

Identity
--------

* ``PXD079076`` -- "Dissecting early AXL signaling regulators and associated
  phenotypes in erlotinib-treated EGFR mutant lung cancer by phosphosite
  perturbations".
* Organism *Homo sapiens*; instrument recorded as **Q Exactive HF** (the
  submitted protocol text says Q Exactive HF-X); data-dependent acquisition,
  MS1 at resolution 60,000, top-15 HCD with MS2 also at resolution 60,000.
* Submitted 2026-05-30, public 2026-06-14, submission type ``COMPLETE``.

Files
-----

Fourteen mzML files. The two smallest usable ones:

.. list-table::
   :header-rows: 1
   :widths: 40 14 10 36

   * - File
     - Bytes
     - MB
     - Note
   * - ``20200128_JG_Erl_BR3_AC41.mzML``
     - 5,309,095
     - 5.31
     - smallest full-size run
   * - ``20200128_JG_Erl_BR2_AC41.mzML``
     - 5,404,425
     - 5.40
     - second smallest
   * - ``20200129_JG_ErlAF_BR3_AC42.mzML``
     - 180,192
     - 0.18
     - **do not use** -- almost certainly a failed or aborted run

Direct download, verified to return HTTP 200 with matching ``Content-Length``::

   https://ftp.pride.ebi.ac.uk/pride/data/archive/2026/06/PXD079076/\
   20200128_JG_Erl_BR3_AC41.mzML

PRIDE FTP paths are date-stamped by publication month and are stable in
practice: PXD000001's 2012 path still resolves today.

Two costs specific to this route
--------------------------------

* **PRIDE publishes no per-file checksum.** The API's ``checksum`` field is an
  empty string for every file inspected, in this dataset and in others. A
  fetch-by-checksum fixture over PRIDE therefore rests on a checksum the
  *project* records, computed once from a download the project trusts. That is
  workable -- it is what this unit's own fetch script does -- but it is weaker
  provenance than a repository that publishes digests.
* **These files were not downloaded or parsed by this unit.** MS2 counts are
  therefore unknown. Anyone adopting this candidate must parse them first.

Fitness against ``D-006``
-------------------------

* Licence: the strongest of the shortlist. CC0 permits vendoring into a public
  repository outright.
* Size: the smallest of the shortlist -- 10.71 MB for the pair.
* **The scientific caveat is real**: this is a phosphotyrosine-enriched sample.
  A plain tryptic search with no phospho variable modification will identify a
  small fraction of the spectra. Used as a fixture it wants
  ``variable_mod`` STY +79.966331, which is a heavier and slower search and a
  less typical one for a golden. ``PXD063765`` (also CC0, 45 mzML files at
  roughly 56 MB each) is a non-enriched alternative from the same route, at
  five times the download cost per file.

Candidate 4 -- PRIDE PXD000001, the canonical example (and why it fails)
========================================================================

Worth stating explicitly because it is the dataset everyone reaches for first.

* ``PXD000001`` -- "TMT spikes - Using R and Bioconductor for proteomics data
  analysis", *Erwinia carotovora* plus five spiked standards, LTQ Orbitrap
  Velos, HCD. DOI ``https://doi.org/10.6019/PXD000001``.
* It is the one candidate that **ships its own search database**:
  ``erwinia_carotovora.fasta``, 1,657,668 bytes, at a stable, immutable PRIDE
  FTP path. That is genuinely attractive -- one accession, one licence, spectra
  and database together.

It fails ``D-006`` on three counts:

.. list-table::
   :header-rows: 1
   :widths: 28 72

   * - Constraint
     - Why it fails
   * - Two spectrum files
     - **Fails.** The dataset contains one LC-MS/MS run.
   * - Small enough for CI
     - **Fails.** The mzML is 450,032,788 bytes (450 MB); the mzXML listed by
       the API is 243,031,280 bytes; the Thermo RAW is 220,475,548 bytes. The
       smallest peak list is ``PRIDE_Exp_Complete_Ac_22134.pride.mgf.gz`` at
       16,448,103 bytes, which is a PRIDE-generated derivative rather than the
       submitted data.
   * - Redistributable
     - **Not granted.** ``license`` is ``EBI terms of use``, not CC0 -- see
       candidate 3.

Keep it in the shortlist as the cautionary row: the most famous public
proteomics dataset in existence is *not* the licence-cleanest one, and being
famous and downloadable is not a grant.

Candidate 5 -- a project-generated subset of a CC0 dataset
==========================================================

Genuinely different in kind: rather than adopting somebody's files, derive
small ones.

The shape of it: a short, deterministic script keeps the first *N* MS2 scans
(and the MS1 scans they reference) from two runs of a CC0 PRIDE dataset and
writes two small mzML files -- of the order of 1--2 MB each for 300 MS2 scans.
Those are vendored in the repository.

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Aspect
     - Cost or benefit
   * - Licence
     - A derivative work: the licence follows the source, so this is only
       viable over a **CC0** source. Over candidate 1 or 2 it would inherit
       their unresolved redistribution question rather than solving it.
   * - CI cost
     - The lowest possible -- no network at all, a few MB in the repository.
   * - Stability
     - Perfect. The bytes are in git.
   * - New cost
     - The project acquires a slicing script that must itself be correct, and
       the goldens then depend on it. A subset also changes the search
       statistics: fewer spectra means fewer PSMs for Percolator, and a
       too-small subset can push Percolator below the point where it trains
       usefully.
   * - Repository weight
     - Binary-ish files in git forever, growing the clone for everyone.

Candidate 6 -- ProteoWizard example data (rejected, recorded for completeness)
==============================================================================

* Source: ``ProteoWizard/pwiz``, ``example_data/``. Licence **Apache-2.0**,
  read at ``https://raw.githubusercontent.com/ProteoWizard/pwiz/master/LICENSE``
  -- the cleanest and least ambiguous of any candidate here.
* ``tiny.pwiz.1.1.mzML`` is 25,072 bytes. ``small.pwiz.1.1.mzML`` is 5,103,183
  bytes and was parsed: **48 spectra, 14 MS1, 34 MS2**.
* **Rejected on scientific adequacy.** Thirty-four MS2 scans cannot produce a
  PSM population Percolator can train on, which fails the substance of
  ``D-006``'s "real enough to produce both target and decoy PIN rows". Recorded
  so that nobody re-discovers it as the obvious cheap answer.

FASTA options
=============

The database is a separate licensing question from the spectra, and it is the
one where the *stability* constraint bites hardest.

UniProt reference proteomes
---------------------------

* Human: ``UP000005640_9606.fasta.gz``, **7,752,225 bytes** compressed,
  **13,731,881 bytes** and **20,652 sequence records** decompressed. Served
  from
  ``https://ftp.uniprot.org/pub/databases/uniprot/current_release/knowledgebase/reference_proteomes/Eukaryota/UP000005640/UP000005640_9606.fasta.gz``.
* Licence: **CC BY 4.0**, with redistribution addressed explicitly. Read at
  ``https://ftp.uniprot.org/pub/databases/uniprot/previous_releases/LICENSE``:

   We have chosen to apply the Creative Commons Attribution 4.0 International
   (CC BY 4.0) License (https://creativecommons.org/licenses/by/4.0/) to all
   copyrightable parts of our databases.

   All databases and documents in the UniProt FTP directory may be copied and
   redistributed freely, without advance permission, provided that this
   copyright statement is reproduced with each copy.

  (``https://www.uniprot.org/help/license`` states the same thing but is
  JavaScript-rendered and returned no text to a plain fetch, so the FTP
  ``LICENSE`` file is the citation used here.)
* **The stability problem.** Per-proteome FASTA files exist only under
  ``current_release/``, which is replaced at every UniProt release -- currently
  ``2026_02 (10-Jun-2026)`` per
  ``https://ftp.uniprot.org/pub/databases/uniprot/current_release/relnotes.txt``.
  ``previous_releases/release-2025_01/knowledgebase/`` was listed directly and
  contains only whole-database tarballs
  (``knowledgebase2025_01.tar.gz``, ``uniprot_sprot-only2025_01.tar.gz``), **not
  per-proteome FASTA files**. There is therefore **no immutable UniProt URL for
  this file**, and a fetch-by-checksum fixture over it will break -- loudly, by
  design -- at the next UniProt release.

  CC BY 4.0 makes the clean answer available: **vendor the FASTA**, with the
  copyright statement reproduced, and the drift disappears. That is a genuine
  reason to treat the spectra and the database differently in ``D-006``.

In-repository FASTA files
-------------------------

``crux-toolkit`` ships ``test/smoke-tests/small-yeast.fasta`` (43,158 bytes,
56 records) under Apache-2.0. Immutable when pinned by commit SHA and trivially
small -- but 56 proteins is far too small a search space to yield a decoy
population Percolator can model. Suitable for a *parser* fixture, not a
*search* fixture. ``PXD000001``'s ``erwinia_carotovora.fasta`` (1,657,668
bytes) is at an immutable PRIDE path but carries that dataset's "EBI terms of
use" status.

Comparison
==========

.. list-table::
   :header-rows: 1
   :widths: 16 16 10 10 18 16 14

   * - Candidate
     - Licence (verified)
     - Files
     - MB
     - Redistributable?
     - Checksum-stable URL?
     - Search quality
   * - 1. Crux K562
     - Apache-2.0 over the repo; data provenance undocumented
     - 2
     - 20.51
     - Not established
     - Yes -- commit SHA
     - Best: complex human lysate, high-res HCD
   * - 2. OpenMS BSA
     - BSD-3-Clause over the repo; data provenance undocumented
     - 3
     - 35.09
     - Not established
     - Yes -- commit SHA
     - Weak: one protein, low-res MS2
   * - 3. PRIDE ``PXD079076``
     - CC0, per PRIDE API ``license``
     - 2 of 14
     - 10.71
     - **Yes**
     - Stable path; **no published checksum**
     - Good instrument, but pTyr-enriched
   * - 4. PRIDE ``PXD000001``
     - EBI terms of use, per PRIDE API ``license``
     - 1
     - 450.03
     - No grant
     - Stable path; no published checksum
     - Good, and ships its own FASTA
   * - 5. Project subset of a CC0 set
     - Inherited (CC0 only)
     - 2
     - ~2--4
     - Yes
     - N/A -- vendored
     - Reduced by construction
   * - 6. pwiz example data
     - Apache-2.0
     - 2
     - 5.13
     - Yes
     - Yes -- commit SHA
     - **Inadequate**: 34 MS2 scans

Cost per CI run
---------------

.. list-table::
   :header-rows: 1
   :widths: 22 22 22 34

   * - Candidate
     - Vendored in repo
     - Fetched per run
     - Main risk
   * - 1. Crux K562
     - 20.51 MB (rights unclear)
     - 28.27 MB with FASTA
     - Redistribution rights; repository rename or deletion
   * - 2. OpenMS BSA
     - 35.09 MB (rights unclear)
     - ~43 MB with a FASTA
     - As above, plus a weak search
   * - 3. ``PXD079076``
     - 10.71 MB (CC0, permitted)
     - 18.46 MB with FASTA
     - No published checksum; enriched sample
   * - 5. Project subset
     - ~2--4 MB
     - 0 -- fully offline
     - Slicer correctness; thin PSM population
   * - FASTA (UniProt human)
     - 13.73 MB plain / 7.75 MB gz
     - 7.75 MB
     - **URL not immutable** -- breaks at each UniProt release

Options for the owner
=====================

Framed as options with costs. ``D-006`` remains open; none of these is a
decision.

**If the owner wants the strongest licence position** -- CometGUI is to be
published and its fixtures must be redistributable without argument -- take
**candidate 3**, a CC0 PRIDE dataset, and vendor both the spectra and the
UniProt FASTA (CC BY 4.0, attribution reproduced). Cost: the specific dataset
found here is phosphopeptide-enriched, so either accept a phospho variable
modification in the golden search or spend a little more searching PRIDE for a
non-enriched CC0 dataset with two small mzML files. This unit's search was
shallow -- see *What could not be verified*.

**If the owner wants the best science per megabyte and will accept fetch-by-
checksum over vendoring** -- take **candidate 1**. A complex human lysate with
high-resolution HCD is the most representative of what a CometGUI user will
actually run, at 28 MB a run, from immutable commit-pinned URLs. Cost: the
project depends on a third party's repository, and the data's redistribution
status stays unresolved -- acceptable while the files are only ever fetched,
not shipped.

**If the owner wants CI to be fully offline and the goldens to be immovable** --
take **candidate 5** over a CC0 source. Cost: a slicing script the project now
owns and must keep correct, a smaller PSM population, and permanent repository
weight.

**If the owner wants one accession covering spectra and database together** --
that is **candidate 4**'s attraction, and it is the one to *reject* explicitly:
one run, 450 MB, and no redistribution grant. Recording the rejection is worth
more than leaving it to be rediscovered.

Whatever is chosen, two things follow from the evidence above and hold for all
options:

#. **Treat the database separately from the spectra.** UniProt is CC BY 4.0
   with explicit redistribution permission but has no immutable per-proteome
   URL. Vendoring the FASTA and fetching the spectra is a coherent split.
#. **Record the licence in ``docs/citations.rst``** as ``D-006``'s own
   recommendation says, and record the per-file SHA-256 in the project, because
   PRIDE does not publish one.

What could not be verified
==========================

Stated plainly, because an unverified item is not a passed item.

* **PRIDE's general data policy could not be read.**
  ``https://www.ebi.ac.uk/pride/markdownpage/datapolicy`` is a JavaScript
  single-page application; a plain fetch returns the shell only, and the
  application bundles contain no policy text. A web search summary asserts that
  PRIDE applies CC0 to datasets submitted from June 2018 and EBI terms of use
  before that -- **this document does not rely on that claim**. It relies
  instead on the per-project ``license`` field returned by the PRIDE API, which
  was fetched directly for three accessions and is consistent with it.
* **Redistribution rights over the Crux and OpenMS data files** could not be
  established. Both repositories carry a permissive licence over the project;
  neither documents the provenance of, or a separate grant for, the instrument
  data committed inside them.
* **cRAP contaminants** could not be checked: ``ftp.thegpm.org`` fails TLS
  hostname verification.
* **``PXD079076``'s mzML files were not downloaded or parsed**; their MS2 counts
  are unknown. Same for ``PXD063765``.
* **MassIVE was not surveyed properly.** ``massive.ucsd.edu`` is reachable and
  its ``QueryDatasets`` JSON endpoint works, but no MassIVE dataset was taken to
  the point of a verified per-file licence, so none is offered as a candidate.
  A fuller survey may well find a better CC0 dataset than ``PXD079076``.
* **Comet runtimes are estimates**, not measurements. Work unit 8 produces the
  measured numbers.
* **Comet's accepted input formats** (mzXML, mzML, Thermo RAW, mgf, ms2/cms2/
  bms2) were taken from this unit's brief and not re-verified against Comet
  2026.02.2. mzML is used regardless, as the format least likely to surprise.

Ephemeral feasibility input -- NOT the project fixture
======================================================

.. warning::

   Everything in this section is a **Phase 00 feasibility input**. It exists so
   that work unit 8 can demonstrate Comet -> pepXML + PIN -> Percolator ->
   Limelight XML on real data. **It is not the project's fixture, it is not a
   recommendation, and it does not answer ``D-006``,** which stays open and is
   the owner's to decide. It lives under ``scratch/`` (gitignored) and is never
   committed.

   The main orchestrator ruled explicitly that a small, openly available
   spectrum file plus a FASTA may be used this way provided it is recorded
   exactly and described plainly as a feasibility input. This section is that
   record.

How to obtain it
----------------

::

   python3 scripts/feasibility/fetch_ephemeral_input.py

The script is standard-library-only, re-runnable, and idempotent: an artefact
already present with the right checksum is verified and not fetched again.
``--force`` re-downloads; ``--dest DIR`` changes the destination. It exits 0
only when every artefact is present, checksum-verified **and** content-verified;
exit code 0 is not taken as proof of anything on its own.

What it verifies, beyond "the download worked":

* SHA-256 of every downloaded artefact, and of the decompressed FASTA;
* the byte size of every artefact;
* for each mzML, the file is parsed as XML and its MS1/MS2 spectra counted, with
  the MS2 count required to match;
* for the FASTA, the number of ``>`` records is counted and required to match.

A checksum mismatch deletes the offending file and fails the run. The checksum
is never relaxed to make the script pass.

What was fetched
----------------

Fetched and verified on 2026-08-29 into ``/workspace/scratch/fixture/``:

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Field
     - Value
   * - File
     - ``20100614_Velos1_TaGe_SA_K562_3.mzML``
   * - Path
     - ``/workspace/scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML``
   * - Size
     - 11,098,328 bytes (11.10 MB)
   * - SHA-256
     - ``cbd0c1b37fb990e6f44528278956306754145ff7318ec7f89fed3b4d3c9b0bc7``
   * - Content (parsed)
     - 801 spectra -- 73 MS1, **728 MS2**
   * - Source
     - ``crux-toolkit/crux-toolkit`` at commit
       ``fc6335cc817c8629aac07c27f2ab4584ba10930f``,
       ``test/smoke-tests/``, via ``raw.githubusercontent.com``
   * - Licence
     - Apache-2.0, read at
       ``https://raw.githubusercontent.com/crux-toolkit/crux-toolkit/fc6335cc817c8629aac07c27f2ab4584ba10930f/license.txt``
       -- with the provenance caveat recorded under candidate 1

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Field
     - Value
   * - File
     - ``20100614_Velos1_TaGe_SA_K562_4.mzML``
   * - Path
     - ``/workspace/scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML``
   * - Size
     - 9,416,326 bytes (9.42 MB)
   * - SHA-256
     - ``d30aa5af4b15e1c927616fce4dacfd68c6249e6cc2b7461913481c12d8408cfa``
   * - Content (parsed)
     - 671 spectra -- 61 MS1, **610 MS2**
   * - Source
     - as above
   * - Licence
     - as above

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Field
     - Value
   * - File
     - ``UP000005640_9606.fasta`` (from ``UP000005640_9606.fasta.gz``)
   * - Path
     - ``/workspace/scratch/fixture/UP000005640_9606.fasta``
   * - Size
     - 13,731,881 bytes plain; 7,752,225 bytes as downloaded (``.gz``)
   * - SHA-256
     - plain ``2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0``;
       gz ``cf49a88c4812dabbd934cb3e2e00b449e70375816e4d47cda7cc5b77b0754024``
   * - Content (parsed)
     - 20,652 sequence records -- UniProt human reference proteome, canonical
   * - Source
     - ``https://ftp.uniprot.org/pub/databases/uniprot/current_release/knowledgebase/reference_proteomes/Eukaryota/UP000005640/UP000005640_9606.fasta.gz``,
       UniProt release ``2026_02 (10-Jun-2026)``
   * - Licence
     - CC BY 4.0, read at
       ``https://ftp.uniprot.org/pub/databases/uniprot/previous_releases/LICENSE``

Total: 28.27 MB fetched, 34.25 MB on disk after decompression. Both downloads
completed in under two seconds on this host.

Known fragility of this input
-----------------------------

The two mzML URLs are pinned by commit SHA and are immutable. The UniProt URL
is **not**: it is served from ``current_release/``, and UniProt has no
immutable per-proteome path (see *FASTA options*). When UniProt publishes a
release after ``2026_02``, this script will fail with a checksum mismatch and
say so. That is the correct behaviour -- a search run against a silently
different database would be worse -- and re-establishing the recorded checksum
is a deliberate act, not a patch.

What work unit 8 needs
======================

Paths, exactly as the script leaves them::

   /workspace/scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML
   /workspace/scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML
   /workspace/scratch/fixture/UP000005640_9606.fasta

Two spectrum files, so the multi-file run model can be exercised for real.

Comet parameter advice, and where it comes from
-----------------------------------------------

Every recommendation below follows from something read out of the data, not
from habit. Wrong tolerances yield zero PSMs, so the reasoning is given.

.. list-table::
   :header-rows: 1
   :widths: 26 20 54

   * - Setting
     - Value
     - Why
   * - Database
     - the FASTA above
     - Human, 20,652 canonical entries. K562 is a human cell line, so target
       hits are genuine.
   * - ``decoy_search``
     - ``1``
     - No decoy database is supplied and none should be. Comet's internal
       decoys put target **and** decoy rows in one PIN with the ``Label``
       column set, which is what Percolator needs. ``2`` writes decoys
       separately and is not what this proof wants.
   * - ``decoy_prefix``
     - default (``DECOY_``)
     - Whatever it is, record it -- downstream tools key off it.
   * - Enzyme
     - trypsin, full digest, 2 missed cleavages
     - Standard tryptic lysate preparation. Semi-tryptic would multiply the
       search space for no gain here.
   * - Precursor tolerance
     - 20 ppm (``peptide_mass_units`` = ppm), symmetric
     - MS1 is Orbitrap FT: filter strings read ``FTMS + p NSI Full ms
       [300.00-1650.00]``. 20 ppm is generous for a Velos and safe against
       calibration drift; 10 ppm would also work.
   * - ``isotope_error``
     - on (``3``, or ``1`` at minimum)
     - Data-dependent Velos runs frequently select a non-monoisotopic
       precursor peak. Leaving this at 0 is the classic way to lose a third of
       the identifications.
   * - **Fragment settings**
     - ``fragment_bin_tol = 0.02``, ``fragment_bin_offset = 0.0``,
       ``theoretical_fragment_ions = 0``
     - **This is the setting most likely to be got wrong.** MS2 here is
       *high-resolution*, read out in the FT analyser -- the filter strings are
       ``FTMS + c NSI d w Full ms2 615.79@hcd40.00``. The low-resolution
       ion-trap defaults (``1.0005`` / ``0.4`` / ``1``) would still identify
       peptides but throw away the resolution.
   * - Ion series
     - b and y
     - HCD (beam-type CID) at NCE 40 on a Velos gives b/y ions.
   * - Fixed modification
     - C +57.021464 (carbamidomethyl)
     - Standard iodoacetamide alkylation. **Not verified from the data** -- no
       protocol accompanies these files. If the PSM yield is implausibly low,
       try the search without it before suspecting anything else.
   * - Variable modification
     - M +15.994915 (oxidation)
     - Ubiquitous; cheap; improves yield.
   * - Masses
     - monoisotopic, parent and fragment
     - High-resolution data throughout.
   * - Output
     - ``output_pepxmlfile = 1``, ``output_percolatorfile = 1``
     - The phase needs pepXML for PDV and a PIN for Percolator.
   * - Threads
     - default / auto
     - 64 cores available; the search is small enough that this hardly matters.

Two practical notes for unit 8:

#. **Generate the parameter file from the binary, then edit it.** Comet's
   parameter names have changed across versions -- the single
   ``peptide_mass_tolerance`` became an upper/lower pair in recent releases.
   Run Comet's own parameter-file generator with the 2026.02.2 binary in hand
   and edit the result, rather than writing a ``comet.params`` from a
   remembered template. This unit did not run Comet and cannot confirm the
   exact spelling of any parameter for that build.
#. **Check the PSM count before blaming Percolator.** 1,338 MS2 spectra across
   the two files. A complex human lysate at high-resolution HCD should identify
   a substantial fraction of them; a yield in the tens means the search is
   misconfigured -- look at the precursor tolerance, ``isotope_error`` and the
   fixed cysteine modification, in that order. Percolator needs a decent target
   and decoy population to train, and a misconfigured search starves it.

Where this leaves ``D-006``
===========================

Open. This document supplies the shortlist, the costs and the licence evidence
``D-006`` asks for, and deliberately stops there. The ephemeral input above is
a Phase 00 feasibility input and confers no status on the data it uses. Only
the owner closes ``D-006``, and only the main orchestrator writes
``DECISIONS.rst``.
