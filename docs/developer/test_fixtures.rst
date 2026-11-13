.. _dev-test-fixtures:

=============
Test fixtures
=============

This page is for a developer who has to run, change or rebuild the real data
CometGUI's tests search: two spectrum files and one protein database. It was
written by Phase 16 (``phases/PHASE-16-release.rst``, *The CI fixture*), work
unit 2, on 2026-11-12, when the database stopped being the whole human
proteome and became a subset cut to suit the spectra. It records what that
subset is, how it was chosen, what was measured to size it, the check that
decides whether a candidate is fit, and everything in the repository that the
change of database touches.

Everything here was measured on one Linux x86-64 host. Nothing has been run on
macOS or Windows; see :ref:`dev-test-fixtures-limits`.

.. contents:: Contents
   :depth: 2
   :local:

.. _dev-test-fixtures-what:

What the fixture is
===================

Three files, decided by the owner (``DECISIONS.rst``, ``D-006`` and
``D-018``).

.. list-table::
   :header-rows: 1
   :widths: 34 12 54

   * - File
     - Kept
     - What it is
   * - ``20100614_Velos1_TaGe_SA_K562_3.mzML``
     - fetched
     - Human K562 lysate, LTQ Orbitrap Velos, data-dependent HCD; 728 MS2
       spectra. From ``crux-toolkit``'s smoke tests at commit ``fc6335cc``,
       SHA-256 ``cbd0c1b3...0bc7`` as served. Never committed: the terms it
       is redistributed under have not been established (``D-006``,
       amendment to part 1).
   * - ``20100614_Velos1_TaGe_SA_K562_4.mzML``
     - fetched
     - The same, 610 MS2 spectra, SHA-256 ``d30aa5af...8cfa`` as served. A
       second file, so that the multi-file run model is exercised.
   * - ``fixtures/human-k562-subset/uniprot-human-2026_02-k562-subset.fasta``
     - committed
     - 3,698 of the 20,652 records of the UniProt human reference proteome,
       release 2026_02: 2,539,164 bytes, SHA-256
       ``bd252b62568d88881e0d13bcdfaa6b9db96666af0ba08fc51863fcd7bdec1b34``.
       ``ATTRIBUTION.txt`` beside it carries UniProt's licence statement
       (CC BY 4.0) and the changes made; ``accessions.tsv`` lists every kept
       accession.

Both spectrum files arrive from that address with carriage returns added in
transit, which breaks their index; every reader in the tests searches a copy
with the carriage returns removed (``D-006``). The fixture's digest -- the one
the reviewed goldens are keyed by, :ref:`dev-testing-version-matrix` -- over
the two files as served and this FASTA is
``1d530d5bc575bb240eb25c0b1d03622f28295cc8173904467264602908194f26``.

**Why the FASTA is a subset.** The owner, 2026-11-12: *"We shouldn't use a
FASTA for the whole proteome, it's unnecessarily large. Add to phase 16 a step
to get the FASTA and suitably filter it for our data, so that our CI will
still run successfully, but not with an overly large FASTA."* The whole
proteome is 13,731,881 bytes and is served only from an address whose content
changes at every UniProt release, so it can be neither committed sensibly nor
fetched by checksum for long. The subset is 18% of its size, is committed, and
Comet searches it in 2.6 s a file where the whole proteome takes 9.3 s (four
threads, this host).

**Only some tests read it yet.** The real-binary tests of ``cometgui-tools``
and ``cometgui-params-comet`` search a cut of it since 2026-11-13
(:ref:`dev-test-fixtures-moved`). Every other test still reads
``scratch/fixture/UP000005640_9606.fasta``; :ref:`dev-test-fixtures-touches`
is the list of them, as it stood before anything was moved.

.. warning::

   The subset is a test fixture and nothing else. It was chosen to contain the
   proteins these two spectrum files identify, so a search of them against it
   is circular by construction, and a search of any other spectra against it
   would be wrong.

.. _dev-test-fixtures-source:

The source and its release
==========================

The source is the file the local fixture has used since Phase 00:
``UP000005640_9606.fasta``, the UniProt human reference proteome, canonical
sequences.

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Fact
     - Value
   * - As served
     - ``UP000005640_9606.fasta.gz``, 7,752,225 bytes, SHA-256
       ``cf49a88c4812dabbd934cb3e2e00b449e70375816e4d47cda7cc5b77b0754024``
   * - Decompressed
     - 13,731,881 bytes, 20,652 records (20,329 Swiss-Prot, 323 TrEMBL), no
       carriage return, SHA-256
       ``2329a517bec9bd7269f9ce3b9252d8b959ae98bc41405a945c2d6134b284d5a0``
   * - UniProt release
     - 2026_02 (10-Jun-2026)
   * - Downloaded
     - 2026-08-29, by ``scripts/feasibility/fetch_ephemeral_input.py``, from
       UniProt's ``current_release/`` directory

**How the release is known.** The download address no longer proves it: since
04-Nov-2026 it serves release 2026_04, a different file of 7,740,211 bytes.
Three things do.

#. The fetcher pinned the checksum on 2026-08-29 and recorded that
   ``current_release/relnotes.txt`` announced 2026_02 that day
   (``docs/feasibility/fixture-candidates.rst``).
#. The gzip header of the served file is dated 2026-04-16 07:26:06 UTC, eight
   weeks before 2026_02 was published; the file UniProt serves for 2026_04 is
   dated 2026-09-01, nine weeks before its release.
#. **The same bytes are still served, labelled.** On 2026-11-12 the file
   ``https://ftp.ebi.ac.uk/pub/databases/reference_proteomes/QfO/Eukaryota/UP000005640_9606.fasta.gz``
   was downloaded and has the same SHA-256, ``cf49a88c...4024``. It is in
   EMBL-EBI's reference-proteome directory for the Quest for Orthologs, whose
   README says "Based on UniProt Release 2026_02", and its ``Last-Modified``
   is 16 Apr 2026 07:26:06 GMT -- the gzip header's own date.

The third settles it: the source is release 2026_02.

**Whether the source can still be fetched.** Yes, on 2026-11-12, from two
places at EMBL-EBI, and from nowhere at UniProt.

* The QfO address above. That directory is replaced once a year (its README),
  so the address is not permanent.
* The archive
  ``https://ftp.ebi.ac.uk/pub/databases/reference_proteomes/previous_releases/qfo_release-2026_02/QfO_release_2026_02.tar.gz``
  (2,358,805,957 bytes). Its member ``Eukaryota/UP000005640_9606.fasta.gz``
  was extracted on 2026-11-12 and is the same bytes, SHA-256
  ``cf49a88c...4024``. An archive of a past release is the nearest thing to a
  fixed address the source has; getting one 7.8 MB file out of it means
  downloading 2.4 GB.
* UniProt itself keeps no per-proteome file for a past release:
  ``previous_releases/release-2026_02/`` holds only whole-database archives
  of 1.6 GB and more.

This is why the subset is committed (``D-018``) rather than rebuilt from a
download.

.. _dev-test-fixtures-selection:

How the records were chosen
===========================

Two rules, both in ``scripts/fixtures/fixture_fasta.py`` and nowhere else.

**Identified: 698 records.** A protein is *identified* when Percolator names
it for a target PSM with a q-value of 0.01 or less in ``psms.tsv``, or for a
target peptide with a q-value of 0.01 or less in ``peptides.tsv``, in **any**
of eight runs of the K562 pair against the whole source through the product:
each of the four Comet and Percolator pairs the product offers on Linux x86-64
(Comet 2026.02.2 and 2026.03.0, Percolator 3.06.5 and 3.07.1), in each of the
two configurations of :ref:`dev-test-fixtures-check`. The details that matter:

* *Both tables, 1%.* 0.01 is the product's default filter at both levels, and
  "at or below" is how it compares. Taking the PSM table and the peptide table
  keeps every protein either level calls confident.
* *The union over every pair and both configurations*, so that the file suits
  each of them. One run names between 620 and 667 proteins; the union is 698.
* *A shared peptide keeps every protein it is listed for.* Percolator's
  ``proteinIds`` column holds all of them and all are taken, so the protein
  list of a confident peptide is the same against the subset as against the
  source.
* *Decoys are skipped.* Names carrying Comet's ``DECOY_`` prefix are made by
  Comet from the FASTA and are not records of it.

**Background: 3,000 records.** Every other record of the source is ranked by
the SHA-256 of its accession -- the text between the bars of its header, for
example ``P04637`` -- as lower-case hexadecimal, ascending, and the first
3,000 are kept. The rule uses no random number generator and does not depend
on the order of the file, so anyone can apply it again; and a larger
background keeps every record a smaller one kept, which is what makes the
sizes below comparable.

The subset is written in the source's order, each record copied byte for
byte. ``fixtures/human-k562-subset/accessions.tsv`` lists every kept
accession, in that order, with which of the two it was kept as, and records in
its header the source's digest and the size, record count and digest of the
subset it defines.

.. _dev-test-fixtures-sizing:

How large the background is, and why
====================================

A series of backgrounds was cut with the same rule and each searched by all
four pairs in both configurations: 72 runs of the product's workflow. Ranges
are over the four pairs; a single number means all four agree. "At 1%" is a
q-value of 0.01 or less; "above 1%" is the target PSMs that a 1% filter
rejects. Comet's time is the mean of its two per-file runs, four threads.

**Five results per spectrum** -- the version matrix's configuration:

.. list-table::
   :header-rows: 1
   :widths: 11 9 12 17 12 12 11 9 7

   * - Background
     - Records
     - Bytes
     - PIN rows (target / decoy)
     - PSMs at 1%
     - PSMs above 1%
     - Peptides at 1%
     - Decoy PSMs (at 1%)
     - Comet, s
   * - 0
     - 698
     - 563,574
     - 6,541 (3,729 / 2,812)
     - 1124--1149
     - 2580--2605
     - 658--672
     - 2,812 (11)
     - 1.4
   * - 250
     - 948
     - 721,870
     - 6,559 (3,787 / 2,772)
     - 1124--1151
     - 2636--2663
     - 666--677
     - 2,772 (10--11)
     - 1.5
   * - 500
     - 1,198
     - 897,295
     - 6,570 (3,821 / 2,749)
     - 1126--1132
     - 2689--2695
     - 663--672
     - 2,749 (10)
     - 1.6
   * - 1,000
     - 1,698
     - 1,218,700
     - 6,597 (3,849 / 2,748)
     - 1105--1116
     - 2733--2744
     - 652--659
     - 2,748 (10)
     - 1.8
   * - 2,000
     - 2,698
     - 1,882,627
     - 6,620 (3,850 / 2,770)
     - 1102--1125
     - 2725--2748
     - 648--664
     - 2,770 (10)
     - 2.2
   * - **3,000**
     - **3,698**
     - **2,539,164**
     - 6,631 (3,887 / 2,744)
     - 1092--1103
     - 2784--2795
     - 646--654
     - 2,744 (10)
     - 2.6
   * - 4,000
     - 4,698
     - 3,173,131
     - 6,634 (3,884 / 2,750)
     - 1090--1120
     - 2764--2794
     - 651--663
     - 2,750 (10)
     - 3.0
   * - 8,000
     - 8,698
     - 5,836,692
     - 6,661 (3,906 / 2,755)
     - 1082--1099
     - 2807--2824
     - 641--650
     - 2,755 (10)
     - 4.6
   * - whole proteome
     - 20,652
     - 13,731,881
     - 6,670 (3,886 / 2,784)
     - 1008--1053
     - 2833--2878
     - 592--602
     - 2,784 (9)
     - 9.3

**One result per spectrum** -- the configuration that is converted to
Limelight XML:

.. list-table::
   :header-rows: 1
   :widths: 13 19 12 13 12 13 18

   * - Background
     - PIN rows (target / decoy)
     - PSMs at 1%
     - PSMs above 1%
     - Peptides at 1%
     - Decoy PSMs (at 1%)
     - Percolator's negative-sample warning
   * - 0
     - 1,327 (1,237 / 90)
     - 1119--1124
     - 113--118
     - 653--661
     - 90 (10)
     - yes
   * - 250
     - 1,328 (1,232 / 96)
     - 1119--1130
     - 102--113
     - 653--659
     - 96 (10)
     - yes
   * - 500
     - 1,329 (1,224 / 105)
     - 1106--1114
     - 110--118
     - 648--653
     - 105 (10)
     - yes
   * - 1,000
     - 1,330 (1,227 / 103)
     - 1095--1105
     - 122--132
     - 641--647
     - 103 (9--10)
     - yes
   * - 2,000
     - 1,332 (1,234 / 98)
     - 1090--1108
     - 126--144
     - 641--651
     - 98 (9--10)
     - yes
   * - **3,000**
     - 1,333 (1,236 / 97)
     - 1087--1092
     - 144--149
     - 637--644
     - 97 (9)
     - yes
   * - 4,000
     - 1,334 (1,239 / 95)
     - 1079--1099
     - 140--160
     - 638--641
     - 95 (9)
     - yes
   * - 8,000
     - 1,335 (1,233 / 102)
     - 1076--1090
     - 143--157
     - 632--639
     - 102 (9)
     - yes
   * - whole proteome
     - 1,335 (1,215 / 120)
     - 1051--1075
     - 140--164
     - 620--623
     - 120 (9)
     - no

**Whether Percolator trained normally.** In all 72 runs it exited 0, trained
its three cross-validation bins to the tenth iteration and wrote three sets of
weights that are not all zero, and the product converted every
one-result-per-spectrum run to Limelight XML that is valid against the
converter's schema. The one difference is the warning in the last column,
which is the subject of :ref:`dev-test-fixtures-warning`.

What the identified proteins alone actually do
----------------------------------------------

The phase document expects a FASTA holding only the identified proteins to
leave Percolator "too few decoys and poor matches to train on" and a result
set in which everything passes 1%. Measured, it is milder than that and
different in kind:

* **Percolator still trains, and nothing fails.** Every run succeeds and
  converts.
* **Decoys do not disappear.** With five results per spectrum there are as
  many decoy rows as against the whole proteome (2,812 against 2,784). With
  one result per spectrum there are fewer, 90 against 120 -- but so there are
  at every size measured: the count wanders between 90 and 105 with no trend
  until the whole proteome is searched.
* **What changes is which PSMs pass.** Take proteins out of the FASTA and a
  weak but true match has fewer wrong candidates to be outscored by, so it
  passes; the identified proteins alone report 1124--1149 PSMs at 1% where
  the whole proteome reports 1008--1053, and the PSMs the filter rejects fall
  from 2833--2878 to 2580--2605. With one result per spectrum the rejected
  side falls from 140--164 to 113--118. The search is not broken; it is
  flattering, and it gives the filters less to reject.

That is the effect the size has to undo, and it recedes slowly: each doubling
of the background returns part of the rejected side, and no subset in the
series returns all of it.

Why 3,000 and not 2,000
-----------------------

3,000 is the smallest background in the series at which the PSMs rejected at
1%, with one result per spectrum, lie inside the whole proteome's own range
for every pair: 144, 146, 149 and 149, against 140 to 164. At 2,000 two of
the four pairs fall below that range (133 and 126; the others give 144 and
141). At 4,000 and 8,000 all four lie inside it again (140--160 and 143--157),
so 3,000 is not one lucky size. With five results per spectrum the rejected
side at 3,000 is 2784--2795, 98% of the whole proteome's smallest, and 93 PSMs
above the floor that catches over-filtering; at 2,000 the margin over that
floor is 34 and at 1,000 it is 42.

The acceptance check passes from a background of 1,000 (1,698 records,
1,218,700 bytes). Had size been the only consideration that file would do;
3,000 was chosen for the margin, and costs 1.3 MB more. Anyone who weighs it
the other way changes one number:
:ref:`dev-test-fixtures-rebuild`.

.. _dev-test-fixtures-warning:

Percolator's warning about too few negatives
--------------------------------------------

With one result per spectrum, Percolator prints, for every subset in the
series::

    Warning : the number of negative samples read is too small to perform a correct classification.

It prints this when it is given 105 decoy PSMs or fewer -- five for each of the
21 features of Comet's PIN (``src/Scores.cpp`` at Percolator's tag
``rel-3-07-01``) -- and then trains as usual. These 1,338 spectra give 120
decoy PSMs against the whole proteome, 15 above that line, and 90 to 105
against every subset up to 8,698 of the proteome's 20,652 records. **No
trimmed FASTA measured clears the warning**, and the series gives no reason to
expect one short of the whole proteome to.

So the warning cannot be an acceptance criterion: it would refuse every subset
and accept only the file the owner decided not to carry. The check prints it
wherever Percolator gave it, by name, and grades the decoy count against a
floor instead. What the warning costs was measured: the committed file's
one-result-per-spectrum runs report 1087--1092 PSMs and 637--644 peptides at
1%, against 1051--1075 and 620--623 for the whole proteome.

This is a consequence of the owner's decision that an agent can measure and
cannot accept on the owner's behalf. **It is put to tier 1 with the floors**
(``handoffs/PHASE-16-worklog.rst``, P16-4).

.. _dev-test-fixtures-check:

The acceptance check
====================

``scripts/fixtures/check-fixture.sh`` takes a directory holding the two K562
files and one FASTA and exits 0 only if a search of them behaves like a real
one, for every Comet and Percolator pair the product offers on the host. It is
how a candidate FASTA is accepted and how an over-filtered one is refused
(PHASE-16 exit gate item 8).

What it runs
------------

One Maven invocation of two opt-in classes of ``cometgui-app``:
``VersionMatrixCensus``, which writes what the product's Tool Manager offers
here, and ``FixtureAcceptancePairs``. The second installs every offered Comet
and Percolator and the Limelight converter from their pinned upstream
addresses into an empty cache, as the version matrix does, and runs each pair
through ``VersionMatrixPairs.runOnce`` -- the one way the matrix, the
determinism suite and the performance suite run the workflow through the
product's run port -- twice:

``matrix``
   exactly the matrix's pair run: the release's defaults with the fixture's
   FASTA, ``decoy_search = 1``, four threads, Percolator's defaults. Comet
   reports its top five results for each spectrum. The goldens are taken from
   this run.
``top1``
   the same with ``num_output_lines = 1``, followed by the product's own
   Limelight conversion (``RunWiring.limelight``, the port the Limelight
   section uses). One result per spectrum is what the end-to-end scenario sets
   before it converts: with five, Comet can report one spectrum twice with the
   same modified peptide and converter v2.8.1 refuses the run whatever the
   FASTA (``handoffs/PHASE-14-worklog.rst``, E5). This unit saw that again:
   against the whole proteome all four pairs' five-result runs were refused,
   at scan 11483.

Each run has its own fresh project under the report directory and is left
there. The JUnit class asserts only what is exact and applies no floor. The
grading is ``scripts/fixtures/fixture_acceptance.py``'s, which derives the
runs it expects from the census and ``manifests/tools.json``, counts every
figure again from the files -- the PIN's labels, the q-values with
``decimal``, the weights, Percolator's own log, the ``psm`` elements of the
Limelight XML -- and requires its counts to equal the product's.

It reads no golden. A FASTA that has just been made has a new fixture digest
and no reviewed golden (``R-TEST-07``), and the check has to be able to judge
it first.

The criteria and the floors
---------------------------

Every criterion is reported by name, with its value and its floor, for every
pair in each configuration.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Criterion
     - Met when
   * - ``workflow-succeeded``
     - the attempt succeeded through the run port, over the fixture digest
       the check computed itself, configured as the floors file says
   * - ``percolator-trained``
     - Percolator exited 0, trained three cross-validation bins, reached an
       iteration, wrote three weight sets that are not all zero, and did not
       warn that its *positive* samples are too few
   * - ``percolator-wrote-results``
     - the four tables and the weights hold rows; the PIN's target and decoy
       rows are the tables' rows; the product's readers count what the check
       counts
   * - ``psms-at-1pct``
     - target PSMs with q <= 0.01 reach the floor
   * - ``peptides-at-1pct``
     - target peptides with q <= 0.01 reach the floor
   * - ``decoy-psms``
     - the decoy PSMs Percolator scored reach the floor
   * - ``psms-above-1pct``
     - target PSMs with q > 0.01 reach the floor: PSMs fall on both sides of
       the 1% line
   * - ``limelight-valid``
     - (``top1``) the product converted the run; the file is valid against
       the schema inside the installed converter JAR, validated by the test
       with the JDK's validator; it holds one ``psm`` element for each target
       PSM row. A Percolator build whose probe observed no XML output is
       reported "n/a", and the check is red if no run at all was converted.

The floors are in ``scripts/fixtures/fixture-acceptance.json``, each with the
rule it follows, the counts it was set from for every pair and the reasoning.
They are a **proposal**: the phase orchestrator records them and tier 1
confirms them. Every floor is derived from the whole-proteome search -- the
real one -- and never from a run that was failing.

.. list-table::
   :header-rows: 1
   :widths: 9 17 7 15 15 15 22

   * - Run
     - Criterion
     - Floor
     - Whole proteome
     - Committed file
     - Identified only
     - Rule
   * - ``matrix``
     - ``psms-at-1pct``
     - 957
     - 1008, 1036, 1013, 1053
     - 1099, 1101, 1092, 1103
     - 1149, 1129, 1144, 1124
     - 95% of the smallest whole-proteome count
   * - ``matrix``
     - ``peptides-at-1pct``
     - 562
     - 599, 602, 592, 598
     - 648, 653, 646, 654
     - 668, 672, 670, 658
     - 95% of the smallest
   * - ``matrix``
     - ``decoy-psms``
     - 2644
     - 2784
     - 2744
     - 2812
     - 95% of the whole-proteome count
   * - ``matrix``
     - ``psms-above-1pct``
     - **2691**
     - 2878, 2850, 2873, 2833
     - 2788, 2786, 2795, 2784
     - **2580, 2600, 2585, 2605**
     - 95% of the smallest
   * - ``top1``
     - ``psms-at-1pct``
     - 998
     - 1051, 1059, 1065, 1075
     - 1087, 1090, 1092, 1087
     - 1119, 1124, 1119, 1124
     - 95% of the smallest
   * - ``top1``
     - ``peptides-at-1pct``
     - 589
     - 621, 620, 623, 623
     - 640, 637, 644, 640
     - 653, 660, 654, 661
     - 95% of the smallest
   * - ``top1``
     - ``decoy-psms``
     - 80
     - 120
     - 97
     - 90
     - two thirds of the whole-proteome count
   * - ``top1``
     - ``psms-above-1pct``
     - 93
     - 164, 156, 150, 140
     - 149, 146, 144, 149
     - 118, 113, 118, 113
     - two thirds of the smallest

Counts are listed in the order Comet 2026.02.2 with Percolator 3.06.5 and
3.07.1, then Comet 2026.03.0 with the same two. Why each floor is where it is:

* **The identifications (four floors).** A subset that keeps the proteins the
  spectra come from must identify what the real search identifies, to within
  a twentieth. A subset finds *more*, so these floors do not catch
  over-filtering; they catch a FASTA that has lost the identified proteins.
  The background alone, with those 698 records left out, gives 0 PSMs and 0
  peptides at 1% in every run, and Percolator 3.06.5 exits 1 on it with one
  result per spectrum.
* ``psms-above-1pct`` **with five results per spectrum is the floor that
  catches over-filtering.** It measures the effect described above directly.
  95% of what the real search rejects is red for the identified proteins
  alone, by 86 to 111 PSMs; red for 250 background records; red for one pair
  of four at 500; green from 1,000.
* ``decoy-psms`` **with five results per spectrum** hardly depends on the
  FASTA (2,744 to 2,812 at every size). A count far below the real search's
  would mean decoys were not searched or the PIN lost rows.
* **The two small counts of the one-result run** have the wider allowance of
  two thirds. A count near 150 differs by up to 24 between the four pairs on
  one FASTA, where a count near 2,800 differs by 45 at most; and no subset
  reaches 95% of the whole proteome's 120 decoy PSMs. Neither is relied on to
  catch over-filtering: the identified proteins alone pass both.

A floor is never lowered to make a run pass. A red run means the FASTA, the
tools or the product changed, and that is what has to be explained.

How to run it
-------------

The check needs a directory holding the three files. Until the fixture is
fetched by one script (a later unit), make one from the local fixture::

    mkdir -p _build/fixture
    cp scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML \
       scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML \
       fixtures/human-k562-subset/uniprot-human-2026_02-k562-subset.fasta \
       _build/fixture/

    bash scripts/fixtures/check-fixture.sh _build/fixture --offline

It prints one block for each pair in each configuration and ends::

     SUMMARY  ok 60  RED 0  n/a 0  problems 0  notes (reported, not graded) 4
     VERDICT  GREEN -- every criterion met for every pair in both configurations

The report is ``_build/fixture-acceptance/fixture-acceptance.txt`` and
``.json``, beside every run's directory. ``--out DIR`` puts it elsewhere;
``--require-committed`` also refuses a FASTA that is not the committed
fixture's (below); ``--grade REPORT --fixture DIR`` grades an existing report
again without searching. ``--offline`` is for a machine whose ``_build/m2repo``
already holds the build's dependencies; the tools are downloaded from GitHub
either way.

**What it decides about the FASTA's identity.** Without
``--require-committed`` it judges any candidate and says in its report whether
the FASTA is the committed one. That is deliberate: the check exists to judge
a file *before* it is committed, and its own controls are candidates. A job
that must run on the committed file and no other passes the flag.

"The committed fixture FASTA" means one thing: a file whose SHA-256 is the
digest the committed accession list pins (``subset-sha256`` in
``accessions.tsv``, read by ``fixture_fasta.py``'s own reader). It never means
"the same bytes as the file in this checkout", because a checkout that rewrote
the file, or a commit that changed the FASTA without its list, would then
vouch for itself. With the flag the check is refused, exit 3, before anything
is searched, when the FASTA given differs from the pin, and also when the
committed file in the checkout differs from it; each refusal names the file
and both digests. Without the flag the report says ``NOT the committed fixture
FASTA`` for any other bytes and adds a ``WARNING`` line when the checkout's
own file is not its pin.

**How long it takes.** About 130 s on this host for the committed file (134
and 129 s in two runs: eight runs of the workflow and four conversions; Comet
2.6 s a file). The runs are serial on any machine
-- nothing in the check runs concurrently, so it needs no fallback at four
cores or fewer -- and Comet uses the matrix's four threads.

Exit status: 0 green; 1 red; 2 misuse; 3 refused -- no fixture named, the
directory or a spectrum file missing, a spectrum file that is not the K562
file of that name, no FASTA or more than one, or with ``--require-committed``
a FASTA or a checkout that is not the pinned bytes -- having searched nothing;
7 a self-test control did not behave as required.

What its red looks like
-----------------------

``bash scripts/fixtures/check-fixture.sh --self-test _build/fixture --offline``
proves the check can fail. It first runs 44 cases that search nothing
(``--self-test-grading`` runs these alone, in a second): 32 graded over
constructed measurements -- each floor one below, a failed attempt, a missing
run, a count the product reads differently; 4 refusals of a fixture; and 8 of
the committed FASTA's identity, over copies of the committed file and list
under ``_build/`` -- a candidate that differs from the pin, a checkout whose
file differs from its pin, that file or the list missing, each refused under
``--require-committed`` and never called the committed fixture FASTA. Then it
cuts two FASTA files from the one in the directory with the committed accession
list, puts each in a directory of its own with the spectra, and runs the whole
check on each:

* **The over-filtered FASTA** -- the 698 identified proteins and nothing else,
  563,574 bytes. It must be red on ``psms-above-1pct`` in the ``matrix``
  configuration for every pair, and on no criterion of another name, with
  every run having searched::

       comet-2026.03.0_percolator-3.07.1  [matrix]
         ok   psms-at-1pct              1124  (floor: 957)
         ok   peptides-at-1pct          658  (floor: 562)
         ok   decoy-psms                2812  (floor: 2644)
         RED  psms-above-1pct           2605  (floor: 2691)
       ...
       SUMMARY  ok 56  RED 4  n/a 0  problems 0  notes (reported, not graded) 4
       VERDICT  RED -- psms-above-1pct [matrix]

* **The background only** -- the 3,000 background records with the identified
  proteins left out. It must be red on ``psms-at-1pct`` and
  ``peptides-at-1pct`` in both configurations (0 against floors of 957, 562,
  998 and 589).

The self-test took about 220 s here (222 and 221 s in two runs) and ends
``self-test OK`` only when both were red for those reasons.

.. _dev-test-fixtures-rebuild:

Rebuilding the file
===================

``scripts/fixtures/fixture-fasta.sh`` wraps ``fixture_fasta.py``. Every
command that reads the source refuses any bytes but the pinned ones.

Check the committed file against its list, with no source::

    bash scripts/fixtures/fixture-fasta.sh describe

Rebuild it from the source and the committed list and compare byte for byte::

    bash scripts/fixtures/fixture-fasta.sh verify --source scratch/fixture/UP000005640_9606.fasta

``verify`` also checks that the list's background accessions are exactly the
first of the rule's ranking over the source, so the list cannot drift from the
rule. ``build --source FASTA --out FILE`` writes the file instead of comparing
it, and refuses to leave one whose digest is not the list's.

Make the list again from search results -- for a different background size,
or after the product offers a new Comet or Percolator::

    mkdir -p _build/fixture-whole
    cp scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML \
       scratch/fixture/20100614_Velos1_TaGe_SA_K562_4.mzML \
       scratch/fixture/UP000005640_9606.fasta _build/fixture-whole/
    bash scripts/fixtures/check-fixture.sh _build/fixture-whole --offline \
        --out _build/fixture-acceptance-whole
    bash scripts/fixtures/fixture-fasta.sh identify \
        --report _build/fixture-acceptance-whole \
        --source scratch/fixture/UP000005640_9606.fasta \
        --background 3000 --out _build/accessions.tsv

``identify`` refuses a report whose runs searched anything but the source.
With the tools of 2026-11-12 the list it writes is the committed one, byte for
byte. A different list is a different FASTA: it has to pass the acceptance
check, its attribution file and this page have to be brought up to date, and
every golden keyed by the fixture's digest has to be reviewed again
(``R-TEST-07``).

.. _dev-test-fixtures-moved:

What has been moved, and what moving it found
=============================================

Phase 16, unit 5a, 2026-11-13. This section is the record the unit was asked
to keep of how it chose what the moved tests search. Everything was measured
on the one Linux x86-64 host, with the two pinned Comet releases and
Percolator 3.07.1 run directly, four Comet threads.

**Moved.** In ``cometgui-tools``: ``CometIndexRealBinaryTest``,
``CometAdapterRealBinaryTest``, ``CometIndexHeaderReaderTest``,
``PepXmlSearchHitReaderRealBinaryTest``, ``RealCometFidelityTest``,
``PercolatorCommandRealBinaryTest`` and ``IndependenceGuardTest``, through
``RealCometSearch`` and a new helper ``FixtureFasta``; the six committed index
headers and their two ``SHA256SUMS``. In ``cometgui-params-comet``: the
validation corpus, ``ValidationCorpusRealBinaryTest``,
``MigratedFileRealBinaryTest``, ``IndexCompatibilityRealBinaryTest`` and the
typed values in ``IndexDescriptions`` and ``FastaDecoyRulesTest``. In
``cometgui-domain``: one typed value in ``PreRunFactValuesTest``. The 42
verdicts of the validation corpus were run again on both Comet releases
against the new database and none changed.

**Not moved.** Everything behind ``RealComet.subset`` (``cometgui-workflow``)
and ``RealSearch.subset`` (``cometgui-app``). They still cut the whole
proteome's first 1,000 records. The reason is the second finding below.

**What the moved tests search.** Not the whole committed file and not its
first records: the first 500 records of the committed file, in its order and
byte for byte, that ``accessions.tsv`` marks ``identified`` -- 392,463 bytes,
SHA-256 ``c12a0261c8ae410cbaf7d5060e23a53c7d9a6d7f628d28422f102a09d978088e``.
Each helper holds the committed file to the digest, size and record count the
accession list's header pins, and the cut to that digest.

.. warning::

   That cut holds only proteins these spectra identify. A search of it is the
   over-filtered search the acceptance check exists to refuse. It was chosen
   for tests that prove files, counts and records, never a rate of
   identification, and it is one agent's choice under a ruling that asked for
   a cut "by measured test time"; the phase orchestrator and tier 1 have not
   confirmed it.

**Why not a simpler cut.** The ruling was that the modules' ``verify`` must
not grow and that no test's bound may be lowered. One test,
``RealCometFidelityTest``, requires more than 3,000 PSMs from a search with
two static labels. Measured:

.. list-table::
   :header-rows: 1
   :widths: 24 13 15 16 16 16

   * - What is searched
     - Records
     - Comet, s a file (2026.03.0)
     - Percolator 3.07.1, s, five results
     - PSMs at 1%
     - PSMs of the labelled search
   * - whole proteome's first 1,000 (before)
     - 1,000
     - 1.37, 1.26
     - 1.4--1.5
     - 0
     - 4,170
   * - committed file, whole
     - 3,698
     - 2.58, 2.60
     - 1.8--1.9
     - 1,097--1,133
     - 5,346
   * - committed file, first 250
     - 250
     - 1.07, 0.87
     - 1.0--3.2
     - 0
     - **2,439**
   * - first 450
     - 450
     - 1.18, 0.99
     - **1.3--13.7**
     - 136--159
     - 3,147
   * - first 500
     - 500
     - 1.17, 0.99
     - **5.0**
     - 162
     - 3,263
   * - first 1,000
     - 1,000
     - 1.41, 1.29
     - **22.2**
     - 231
     - 4,175
   * - first 1,500
     - 1,500
     - **1.78, 1.49**
     - 1.6--2.3
     - 429--463
     - 4,633
   * - first 1,000 marked ``background``
     - 1,000
     - 1.52, 1.35
     - **exits 1**
     - 0
     - 4,082
   * - first 500 marked ``identified`` (chosen)
     - 500
     - 1.29, 1.07
     - 1.7--1.9
     - 796--832
     - 3,562

Comet's two figures are the K562_3 and K562_4 files, the mean of three runs
(of one run for 450, 500 and 1,500 records). A Percolator range is over
random seeds 1, 2, 3, 4, 7 and 42 and both releases' PINs where more than one
was run; 22.2 s was measured twice. Bold marks what rules a row out: the
bound not cleared, Percolator slow or erratic, Percolator failing, or Comet
slower than before. No cut of the file's first records passes all three.

With one result per spectrum Percolator 3.07.1 is slower and less steady on
every input measured that identifies anything. Over six runs each (seeds 1, 2
and 7, both releases' PINs): 2.5 to 3.5 s on the chosen cut with one run of
13.0 s, 2.8 to 10.2 s on the whole committed file, 1.8 to 12.9 s on the first
1,500 records, against 1.4 to 2.8 s before. The first 1,500 records also hold
``P38159``, the subject of the second finding below.

**The affected classes' time.** Run alone, before and after, once each:

.. list-table::
   :header-rows: 1
   :widths: 30 20 20 30

   * - Module
     - Before, s
     - After, s
     - Tests, failures
   * - ``cometgui-tools`` (7 classes)
     - 65.9
     - 61.3
     - 93, 0
   * - ``cometgui-params-comet`` (6 classes)
     - 15.3
     - 12.2
     - 185, 0
   * - ``cometgui-domain`` (1 class)
     - 0.12
     - 0.13
     - 7, 0

**First finding: the helper that makes a target-decoy FASTA split records at
every** ``>``. The committed file holds ``DNA dC->dU-editing enzyme
APOBEC-3H`` (``Q6NTF7``), which that split turned into two records, one with
no sequence. The three copies of the helper now split where a line begins
with ``>``. On the old input both give the same bytes.

**Second finding: the Limelight converter refuses a five-result run of any
FASTA that holds** ``P38159`` **or** ``Q96E39``. They are records 1,313 and
2,548 of the committed file. At scan 11483 of ``K562_3`` Comet reports
``R.GGHM[15.9949]DDGGYSMNFNMSSSR.G`` twice, and converter v2.8.1 stops with
"Got more than one entry ... for the same scan to the same reported peptide".
This is the refusal described under :ref:`dev-test-fixtures-check`. Three
workflow tests -- ``RealLimelightConversionTest``,
``RealLimelightCompatibleRerunTest`` and
``RealIdentifiersTest.theEarlierRunIsStillConverted`` -- convert five-result
runs, and they pass today only because the whole proteome's first 1,000
records hold neither protein. On the chosen cut all three were refused. The
unit did not change those tests to one result per spectrum and did not pick
a cut that leaves the two proteins out; it stopped, left the workflow and the
application as they were, and put the question to the phase orchestrator.
The application's Limelight tests search the same input and were not run on
the cut.

On the cut, the workflow classes that needed no change of a pinned value ran
in 111.2 s against 107.8 s before (one run each), so "does not grow" is shown
for the two moved modules and not for the workflow.

.. _dev-test-fixtures-touches:

What the change of FASTA touches
================================

Measured before anything was changed, at commit ``33c1108``, and left for the
unit that moves the tests. Nothing below has been edited.

How it was searched
-------------------

Two passes over the tracked files.

*First, every file that names the fixture.* ::

    git grep -l -E 'UP000005640|scratch/fixture|2329a517|cf49a88c|5005d961|8b704dcb|13731881|13,731,881|7752225|7,752,225|20652|20,652|20 652|scientific-path|COMETGUI_MATRIX_FIXTURE|cometgui\.matrix\.fixture'

The patterns are the FASTA's name, the fixture directory, the SHA-256 of the
FASTA, of its ``.gz`` and of its first 1,000 records, the fixture digest the
goldens are keyed by, the FASTA's two sizes and its record count as they are
variously written, the directory of Phase 00's search outputs, and the version
matrix's named input. At ``33c1108`` it lists **129 files**: 104 outside
``handoffs/`` and 25 handoffs.

*Second, every test that reaches the fixture only through a helper class*,
and so holds none of those strings itself: the uses of each helper named
below were listed with ``git grep -l`` on its class and method names, and each
user read for the numbers it pins.

*Third, a net for what both passes miss:* ``git grep -l -i k562`` lists 195
files. The 67 that neither pass had found were read where they match. Most use
``k562_3.mzML`` as an invented file name in constructed data, or mention the
fixture in a comment; the few that quote a number from a search are in the
last table.

In the tables, **moves** says what has to change when the FASTA does:

``path``
   the file names the FASTA's location or file name
``digest``
   it pins a SHA-256 that will differ
``counts``
   it pins numbers that came from a search and will differ
``duration``
   it relies on the search lasting long enough to be interrupted
``text``
   it describes the present fixture in words or quotes its numbers
``no``
   it does not move

Where the FASTA is named: the seams
-----------------------------------

Change these and every test behind them follows.

.. list-table::
   :header-rows: 1
   :widths: 40 44 16

   * - File
     - What it pins
     - Moves
   * - ``cometgui-app/src/test/java/org/cometgui/app/testing/RealSearch.java``
     - ``scratch/fixture/UP000005640_9606.fasta``; its SHA-256
       ``2329a517...``; ``SUBSET_SHA256`` ``5005d961...``, the digest of *the
       proteome's first 1,000 records*, which ``subset()`` cuts
     - path, digest
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/testing/RealCometSearch.java``
     - the same three, and the first-1,000 subset it searches
     - path, digest
   * - ``cometgui-workflow/src/test/java/org/cometgui/workflow/steps/RealComet.java``
     - the same three; ``proteome()`` and ``subset()``
     - path, digest
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/comet/CometIndexRealBinaryTest.java``
     - the same three; a census of 1,000 records and no decoy, and of 2,000
       with 1,000 decoys
     - path, digest
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/comet/CometAdapterRealBinaryTest.java``
     - cuts the first 1,000 records; 1,807 target rows; 728 and 607 spectrum
       queries (the spectra's, not the FASTA's)
     - digest, counts
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/limelight/LimelightFixtures.java``
     - the FASTA's path and SHA-256, given to the converter with Phase 00's
       outputs (below)
     - path, digest
   * - ``cometgui-params-comet/src/test/resources/fixtures/comet-validation/corpus.json``
     - ``database.file``, its SHA-256, ``records: 1000`` and ``subsetSha256``
     - path, digest
   * - ``cometgui-params-comet/src/test/java/org/cometgui/params/comet/validation/ValidationCorpusRealBinaryTest.java``
     - reads the corpus's FASTA and holds it to those digests; 42 cases, two
       Comet releases, over scans 11188--11192
     - path; the verdicts must be run again
   * - ``cometgui-params-comet/src/test/java/org/cometgui/params/comet/migration/MigratedFileRealBinaryTest.java``
     - the same inputs, held to the corpus's digests
     - path, digest
   * - ``cometgui-app/src/test/java/org/cometgui/app/e2e/tiera/TierAApplication.java``,
       ``.../e2e/tierb/TierBApplication.java``
     - stage the whole proteome under the name ``UP000005640_9606.fasta``;
       that name is in a message a gate greps for (below)
     - path
   * - ``cometgui-app/src/test/java/org/cometgui/app/matrix/VersionMatrixPairs.java``,
       ``DeterminismPairs.java``, ``PerformanceFixture.java``,
       ``MatrixSupport.java``
     - no path: the fixture is the named input. Three refusal messages say
       "D-006's CI fixture is OPEN; locally, scratch/fixture"
     - text

Tests behind the seams
----------------------

None of these names the FASTA. Each searches it, or its first 1,000 records,
through a helper above.

.. list-table::
   :header-rows: 1
   :widths: 26 52 22

   * - Helper
     - Tests, and what they pin
     - Moves
   * - ``RealSearch.proteome`` -- **the whole proteome**
     - ``TierACanonicalScenarioTest``, ``TierANoXmlScenarioTest``,
       ``TierBCanonicalScenarioTest``, ``TierBNoXmlScenarioTest``,
       ``TierBLocaleControlTest`` (through the two application classes):
       counts are recomputed by the scenario's oracles, not typed.
       ``RealCancelUiTest``, ``ProvenanceSectionUiTest``,
       ``RealProvenanceGateTest`` (its cancelled run): cancel Comet once it
       has printed "Load spectra:"
     - the tiers: none typed, run again. The three cancelling tests:
       **duration**
   * - ``RealSearch.subset`` -- the first 1,000 records
     - ``GateWorld`` and ``RealProvenanceGateTest`` (record lists naming
       ``subset.fasta``); ``RealRunUiTest`` (merged PIN 6,472 rows = 3,554 +
       2,918; 3,285 target rows); ``LimelightConversionUiTest``,
       ``LimelightRerunUiTest``, ``LimelightUploadUiTest``,
       ``NetworkUnavailableUiTest``, ``PdvFirstUseUiTest``,
       ``PdvLiveWindowUiTest``, ``PercolatorRerunUiTest``,
       ``ProvenanceSectionUiTest``
     - digest; counts in ``RealRunUiTest``
   * - ``RealComet.subset``
     - ``RealCometRunTest`` (3,554, 2,918, 6,472), ``RealStepTest`` (6,472;
       1,807 and 118 target rows), ``RealDecoyBlockTest`` (messages quoting
       "0 of 1000 records", "1000 of 2000 records" and the proteome's first
       record, ``DECOY_sp|A0A075B6H9|LV469_HUMAN``, which is **not** in the
       subset), ``RealIndexTest``, ``RealIndexCacheTest``,
       ``RealChangedInputTest``, ``RealMergeMismatchTest``,
       ``RealPathParameterTest``, ``RealAwkwardPathRunTest``,
       ``RealPercolatorRunTest``, ``RealPercolatorRerunTest``,
       ``RealLimelightConversionTest``,
       ``RealLimelightCompatibleRerunTest``, ``SealFailureRunTest``
     - digest; counts and text where given
   * - ``RealComet.proteome`` -- the whole proteome
     - ``RealCancellationTest``: Comet at one thread, cancelled after "Load
       spectra: 728" (about 31 s for one file against the whole proteome)
     - **duration**
   * - ``RealCometSearch``
     - ``PepXmlSearchHitReaderRealBinaryTest`` (more than 100 queries),
       ``RealCometFidelityTest`` (more than 3,000 PSMs; more than 1,000 decoy
       rows sharing a target's identifier), ``PercolatorCommandRealBinaryTest``,
       ``IndependenceGuardTest``
     - digest; the bounds must be checked
   * - ``CometIndexRealBinaryTest``'s subset and ``IndexHeaders``
     - the index headers both Comet releases write for the first-1,000
       subset, **committed** under
       ``cometgui-tools/src/test/resources/fixtures/comet-index/`` (six
       header files and two ``SHA256SUMS``, which name the fixture nowhere),
       and their values typed out in ``IndexHeaders`` (tools and workflow),
       ``CometIndexHeaderReaderTest``, ``IndexDescriptions`` and
       ``PreRunFactValuesTest`` (``NumPeptides: 129327``; 81,849)
     - digest and counts, if the subset changes
   * - the validation corpus and ``IndexDescriptions``
     - ``ValidationCorpusTest``, ``IndexCompatibilityRealBinaryTest``,
       ``IndexCompatibilityRulesTest``, ``FastaDecoyRulesTest``: descriptions
       of the same subset's index
     - only if the subset changes

**The first 1,000 records.** Most real-binary tests do not search the whole
proteome: they cut its first 1,000 records and pin that cut's digest
(``5005d961...``). The first 1,000 records of the committed subset are other
records -- its first is ``sp|O95757|HS74L_HUMAN`` -- so whichever way those
tests are moved, that digest and every count taken from that search change.

**The tests that need a long search.** Four tests and one performance
measurement cancel Comet in mid-search, and they use the whole proteome
because it takes long enough. Against the committed subset Comet finishes a
file in 2.6 s at four threads. Each has to be shown still to catch Comet
searching.

Phase 00's search outputs
-------------------------

``scripts/feasibility/run_scientific_path.sh`` searched the K562 pair against
the whole proteome with Comet 2026.02.2 on 2026-08-29 and left its outputs
under ``scratch/scientific-path/``. They are derived from spectra and are not
committed. Many tests read them, each file held to a SHA-256, and pin its
counts: 6,670 PIN rows (3,897 target, 2,773 decoy), 1,026 PSMs and 603 of
2,985 peptides at 1%.

.. list-table::
   :header-rows: 1
   :widths: 40 44 16

   * - File
     - What it pins
     - Moves
   * - ``scripts/feasibility/run_scientific_path.sh``,
       ``scripts/feasibility/comet.params``
     - reads ``${FIXTURE_DIR}/UP000005640_9606.fasta``, requires 20,652
       records; the parameter file names ``/workspace/scratch/fixture/...``
     - path, counts, if run again
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/testing/K562Run.java``
     - the two pepXML files and Percolator's target and decoy tables, by
       digest. Read by ``K562FidelityTest``, ``RealPdvFidelityTest``,
       ``UnrepresentableTest``, ``IndependenceGuardTest``,
       ``PdvMzTabExporterRealTest``, ``PdvMzTabRealPdvTest``,
       ``PdvControllerRealPdvTest``, ``PdvFiguresRealTest``,
       ``PepXmlSearchHitReaderRealFileTest`` (728, 3,638, 1,956, 489, 1,454
       and 607, 3,032, 1,766, 326, 1,268), ``MzMlSpectrumReaderTest`` and
       ``PdvMgfWriterTest`` (spectra only)
     - digest, counts
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/limelight/LimelightFixtures.java``
     - twelve paths under ``scratch/scientific-path``. Read by
       ``LimelightConverterRealTest`` (3,897 PSMs), ``LimelightXmlJudgeTest``
       (3,897; 7,794 and 7,795 errors), ``PercolatorXmlPsmsTest`` (3,897;
       2,130 and 1,767; 2,773 and 2,780; 6,670), ``PepXmlSetTest``,
       ``LimelightConverterTest``, ``LimelightUploadClientTest``,
       ``LimelightSandboxUploadTest``, ``FakeLimelightServerTest``
     - digest, counts
   * - ``cometgui-workflow/src/test/java/org/cometgui/workflow/limelight/LimelightFixtures.java``,
       ``LimelightUploadsTest.java``
     - Phase 00's ``limelight.xml`` by digest, which the stand-in converter
       of that package writes; one corrupted copy
     - digest
   * - ``cometgui-workflow/src/test/java/org/cometgui/workflow/pdv/PdvMzTabsFidelityTest.java``
     - the pepXML files and tables by digest; "13340 compared", "6670
       compared"
     - digest, counts
   * - ``cometgui-results/src/test/java/org/cometgui/results/testing/RealK562.java``,
       ``RealK562Test.java``
     - ten Percolator outputs by digest, and their counts at five cut-offs.
       Read by ``ResultTableReaderOffsetsTest`` (3,897),
       ``WeightsSummaryIndependentTest``, ``WeightsExportTest``,
       ``ExportTables``, ``ResultStoreContract``
     - digest, counts
   * - ``cometgui-results/src/test/resources/org/cometgui/results/real-k562/PROVENANCE.txt``,
       ``.../parser/WEIGHTS-SUMMARY.txt``
     - the same digests and counts, in words; names the proteome and its
       20 652 records
     - text
   * - ``cometgui-ui/src/test/java/org/cometgui/ui/viewmodel/results/ResultsFixtures.java``
     - three of those outputs. Read by ``ResultTableScratchFixturesTest``,
       ``WeightsViewModelTest``
     - digest, counts
   * - ``cometgui-app/src/test/java/org/cometgui/app/testing/K562Outputs.java``
     - the same outputs and their counts at five cut-offs (3,897 / 982 /
       1,026 / 1,171; 2,773; 2,985 / 567 / 603). Read by
       ``ResultsCountsUiTest``, ``ResultsExportUiTest`` (2,326),
       ``ResultsWeightsUiTest``
     - digest, counts
   * - ``cometgui-app/src/test/java/org/cometgui/app/config/ProjectLimelightUploadPortTest.java``
     - Phase 00's ``limelight.xml``
     - digest
   * - ``cometgui-app/src/test/resources/org/cometgui/app/testing/results/FIXTURES.txt``,
       ``cometgui-ui/src/test/resources/org/cometgui/ui/viewmodel/results/FIXTURES.txt``
     - name the directory; the files they list are Percolator's output over
       CometGUI's synthetic PIN, not K562
     - no

These move **only if the Phase 00 outputs are made again** against the
subset; left alone, they still describe a search of the whole proteome. But a
CI runner has no ``scratch/scientific-path``, and its content cannot be
committed, so a job that runs these tests has to make it -- and then it is
made from whatever FASTA the job has. That choice belongs to the unit that
moves the tests.

Goldens, baselines and data
---------------------------

.. list-table::
   :header-rows: 1
   :widths: 40 44 16

   * - File
     - What it pins
     - Moves
   * - ``cometgui-app/src/test/resources/goldens/comet-*_percolator-*/fixture-8b704dcb...linux-x86-64.json``
       (four)
     - every value of a pair run over fixture digest ``8b704dcb...``; the
       text names ``scratch/fixture`` and the proteome
     - all of it: the new digest is ``1d530d5b...``, so four new goldens
   * - ``cometgui-app/src/test/resources/goldens/comet-*_percolator-*/REVIEW.json``
       (four)
     - the review record of each golden, by the golden's SHA-256
     - a new record for each (``R-TEST-07``)
   * - ``cometgui-app/src/test/resources/goldens/percolator-*/`` (three)
     - goldens over the synthetic PIN, digest ``a6ab6336...``
     - no
   * - ``scripts/perf/performance-data.json``
     - for this host's profile, the thresholds of ``percolator-fixture``
       (4.5 s, from a median of 2.14 s) and ``cancel-comet`` (500 ms, from
       94.1 ms), and the recorded baseline; ``comet-fixture`` is
       informational
     - counts; ``cancel-comet``: **duration**
   * - ``scripts/matrix/determinism-data.json``
     - the runs and the tolerance; no value from a search
     - no
   * - ``scripts/matrix/matrix-data.json``
     - advisories for the differences between versions' pair results
     - check: the differing keys may change
   * - ``docs/traceability-map.toml``
     - two commands written with ``COMETGUI_MATRIX_FIXTURE=<fixture dir>``;
       notes saying the runs were over the local fixture
     - text

Gate harnesses
--------------

Each lists the scratch files it needs and stops when one is missing.

.. list-table::
   :header-rows: 1
   :widths: 34 50 16

   * - File
     - What it names
     - Moves
   * - ``scripts/verify-e2e-gates.sh``
     - the three fixture files (lines 202--204); the expected diagnostic
       ``UP000005640_9606\.fasta: not declared as an input`` (line 1534)
     - path, text
   * - ``scripts/verify-limelight-gates.sh``
     - the three fixture files and the Phase 00 pepXML files
     - path
   * - ``scripts/verify-pdv-gates.sh``
     - the same; expected diagnostics quoting Phase 00's scores for PSM
       ``..._K562_3_11367_2_1`` (``0.00112905``, ``2.37114``,
       ``9.27998e-07``) and ``..._11460_3_2``
     - path; counts if Phase 00's outputs are made again
   * - ``scripts/verify-param-gates.sh``
     - ``CORPUS_SPECTRA`` and ``CORPUS_PROTEOME``
     - path
   * - ``scripts/verify-percolator-gates.sh``,
       ``scripts/verify-provenance-ui-gates.sh``
     - the three fixture files; the second also greps for ``input fasta
       subset.fasta``
     - path
   * - ``scripts/verify-test-gates.sh``, ``scripts/verify-workflow-gates.sh``
     - require ``scratch/fixture`` to exist; the first also requires Phase
       00's Percolator outputs
     - path
   * - ``scripts/verify-results-gates.sh``
     - Phase 00's Percolator outputs
     - only if they are made again
   * - ``scripts/verify-hardening-gates.sh``
     - the text of a CI stub it injects into
     - text

CI steps and the suites' own messages
-------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 40 44 16

   * - File
     - What it says
     - Moves
   * - ``scripts/ci/nightly-large-dataset.sh``,
       ``nightly-determinism-fixture.sh``,
       ``nightly-performance-fixture.sh``, ``nightly-gui-tests.sh``,
       ``nightly-windows-raw.sh``, ``release-e2e-canonical.sh``,
       ``release-e2e-limelight.sh``, ``release-e2e-noxml.sh``
     - stubs that exit 70 saying ``D-006``'s CI fixture is open and
       ``scratch/fixture`` is not it
     - text, then real work (a later unit)
   * - ``.github/workflows/nightly.yml``
     - three comments saying no fixture exists on a runner
     - text
   * - ``scripts/matrix/matrix.py``, ``determinism.py``, ``run-matrix.sh``,
       ``scripts/perf/performance.py``, ``run-performance.sh``
     - refusal messages and usage naming ``scratch/fixture`` as the local
       fixture
     - text

Documents
---------

.. list-table::
   :header-rows: 1
   :widths: 40 44 16

   * - File
     - What it says
     - Moves
   * - ``docs/developer/testing.rst``
     - commands with ``COMETGUI_MATRIX_FIXTURE=scratch/fixture``; the
       fixture described as the K562 pair and the human proteome; numbers
       from present runs throughout
     - text
   * - ``docs/developer/e2e_harness.rst``,
       ``limelight_integration.rst``, ``pdv_integration.rst``,
       ``results_model.rst``, ``workflow_engine.rst``,
       ``version_capabilities.rst``, ``comet_parameter_schema.rst``
     - name ``scratch/fixture``, the proteome or its first 1,000 records,
       and quote counts (1,201 and 1,053; 3,285 and 3,187; the subset's
       digest)
     - text
   * - ``docs/developer/limelight_upload_spike.rst``,
       ``pdv_mztab_spike.rst``, ``docs/reference/percolator_options.rst``
     - cite Phase 00's outputs
     - no: dated findings
   * - ``docs/feasibility/fixture-candidates.rst``,
       ``scientific-path.rst``, ``small-thermo-raw-files.rst``,
       ``pdv-converter-spike.rst``; ``phases/PHASE-00-feasibility.rst``
     - Phase 00 evidence: the fetch, the source's digests, the first search
     - no: dated evidence
   * - ``scripts/feasibility/fetch_ephemeral_input.py``,
       ``pdv_spike.sh``; ``scripts/spikes/limelight_upload_spike.py``,
       ``pdv_mztab_spike.py``
     - the fetcher pins the source; the spikes read Phase 00's outputs
     - no, unless the fetcher is to fetch the source again: its address now
       serves release 2026_04
   * - ``docs/decoys.rst``
     - a user-facing example of a message, quoting "1807 target rows" from
       the search of the first 1,000 records
     - text, if the example is to stay a real one
   * - ``docs/developer/tool_registry.rst``, ``manifests/tools.json``
     - the evidence notes of the Comet 2026.03.0 intake (2026-10-04): PIN
       rows and spectrum queries of two partial searches against the whole
       proteome
     - no: dated evidence
   * - ``cometgui-tools/src/test/java/org/cometgui/tools/comet/PinText.java``
     - the 23 feature values of one real PIN row (scan 11188), used as
       constructed text
     - no
   * - ``DECISIONS.rst``, ``STATUS.rst``
     - ``D-006``'s table names ``UP000005640_9606.fasta``, 20 652 records
     - tier 1's
   * - 25 files under ``handoffs/``
     - the record of earlier phases
     - no: history
   * - six classes under ``cometgui-tools/src/main/java``
       (``CometSearchCommands``, ``PinHeader``, ``LimelightConverterCommand``,
       ``LimelightQValueCutoff``, ``PepXmlSet``, ``PercolatorCommands``)
     - comments citing ``docs/feasibility/scientific-path.rst`` as evidence
     - no

.. _dev-test-fixtures-limits:

What this page does not establish
=================================

* **Linux x86-64 only.** Every count here is from one host. The floors have
  never been met, or missed, on a macOS or Windows runner, and Comet's and
  Percolator's counts can differ between platforms; the margins were chosen
  with that in mind and have not been tested against it.
* **The floors are a proposal** until tier 1 confirms them, and so is the
  decision to report Percolator's negative-sample warning without failing on
  it.
* **The series is one sample at each size.** The background rule is fixed, so
  each size is one particular set of records; another rule would give other
  counts at the same size.
* **The list of what the change touches can miss a file that names nothing.**
  It was made by searching for the fixture's names, digests and sizes and for
  the helpers that read it. A committed file *derived* from the fixture that
  quotes none of those is found only by reading: one such group was, the
  index headers under ``fixtures/comet-index``. There may be another.
* **Most of the tree has not been moved onto the subset.** Two modules'
  real-binary tests read it (:ref:`dev-test-fixtures-moved`); no workflow or
  application test, golden, baseline or CI job does yet.
