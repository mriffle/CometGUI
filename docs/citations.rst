.. _user-citations:

=========================
Citations and attribution
=========================

.. note::

   **Partial page.** No phase document names this page individually, so
   completing it falls to Phase 16's *complete user documentation tree*
   deliverable (``phases/PHASE-16-release.rst``); the specification names it as
   the home of third-party attribution, and the licence audit that feeds it is
   ``AC-REL-03``, a human sign-off. Until then, a phase that comes to rely on a
   third-party component or format records it here. The PDV, CasanovoGUI and
   mzTab entries were added by Phase 11 (PDV Integration) on 2026-10-11, and
   the Limelight entries by Phase 12 (Limelight Conversion and Upload) on
   2026-10-12, and the UniProt entry by Phase 16 (Documentation and Release
   Qualification) on 2026-11-12. Comet, Percolator and the bundled Java
   runtime are not yet listed.

How to cite CometGUI and, more importantly, the tools behind it when results
produced with this application are published -- and what CometGUI takes from
other projects.

.. contents:: Contents
   :depth: 1
   :local:

PDV
===

PDV (the Proteomics Data Viewer) draws CometGUI's annotated spectra and runs
the live spectrum window (:doc:`pdv`). If you publish a figure PDV drew, cite
it as its authors ask, in the README of their repository at tag ``v2.7.0``:

   Li K, Vaudel M, Zhang B, Ren Y, Wen B. PDV: an integrative proteomics data
   viewer. *Bioinformatics* **35** (7), 1249--1251 (2019).
   ``doi:10.1093/bioinformatics/bty770``; free full text at
   https://pmc.ncbi.nlm.nih.gov/articles/PMC6821182/

   The DOI is given as text rather than as a link because the publisher's
   site answers automated requests with 403 Forbidden, so the nightly link
   check could never confirm it; the PubMed Central copy is the same article
   and is checked.

* **Project:** https://github.com/wenbostar/PDV -- version 2.7.0, tag
  ``v2.7.0`` (commit ``aaf38f971ea3edf9aa6ed1490448fd92cb2ca1fd``).
* **How CometGUI obtains it:** downloaded from the project's own release at
  install time, by pinned URL and SHA-256 (``manifests/tools.json``), and
  never redistributed by CometGUI (``D-008``). CometGUI runs it unmodified; it
  is not forked.
* **Licence: treated as GPL-3.0, by owner direction (2026-08-29).** PDV's
  licensing is self-contradictory upstream: its ``LICENSE`` file at ``v2.7.0``
  is the GPL-3.0 text, while its ``pom.xml`` at the same tag declares *The
  Apache Software License, Version 2.0*. The owner directed that the project
  proceed on the GPL-3.0 reading, the more constraining of the two; with
  CometGUI itself GPL-3.0 it raises no compatibility question. This does not
  resolve the contradiction: Phase 16 asks the PDV authors which licence
  governs and records the answer (``DECISIONS.rst``, ``D-008``). The Tool
  Manager shows the same advisory beside PDV.
* **What ships inside PDV's download** is PDV's own business, not CometGUI's:
  its ``lib/`` folder holds 183 library JARs under their own licences, which
  CometGUI neither redistributes nor audits here. One of them,
  compomics-utilities, contains an analytics sender that PDV never references
  (:doc:`pdv`, *Privacy*).

CasanovoGUI
===========

CometGUI is GPL-3.0 because it derives from CasanovoGUI (``D-001``).

* **Project:** https://github.com/Noble-Lab/CasanovoGUI, GPL-3.0. Every derived
  file is taken from commit ``480b3013e7f8fb51a2b8c58681043821e3e7f865``.
* **Attribution is collective**, because upstream's source files carry no
  per-file copyright notice: each derived file keeps the derived licence
  header, which credits *the CasanovoGUI authors*, and names its own upstream
  file and commit in a derivation record.
* **The derived files**, each under a ``/derived/`` path (the list, with what
  was kept, is in :doc:`developer/architecture`):

  - ``AtlantaFxThemes`` (from ``Themes.java``) and ``ConsolePane`` (from
    ``ConsoleView.java``), since Phase 02;
  - ``PdvController`` (from ``PdvController.java``) and ``DenovoGuiCommand``
    (from ``PdvLauncher.java``'s ``denovo-gui`` argument array only), since
    Phase 11: the control of a live PDV window over its loopback port,
    reused as ``R-PDV-05`` expects, and modified as
    :doc:`developer/pdv_integration` describes.

Limelight
=========

Limelight is the web application CometGUI's Limelight section converts runs
for and uploads them to (:doc:`limelight`). If you publish results you shared
or analysed in Limelight, cite it as its authors ask, in the README of
``yeastrc/limelight-core`` at tag ``v8.3.1``:

   Riffle M, Zelter A, Jaschob D, Hoopmann MR, Faivre DA, Moritz RL, Davis TN,
   MacCoss MJ, Isoherranen N. Limelight: An Open, Web-Based Tool for
   Visualizing, Sharing, and Analyzing Mass Spectrometry Data from DDA
   Pipelines. *J Proteome Res.* **24** (4), 1895--1906 (2025).
   ``doi:10.1021/acs.jproteome.4c00968``; PubMed ID ``40036265``.

The Limelight converter
-----------------------

``limelight-import-comet-percolator`` converts a Comet + Percolator run to
Limelight XML.

* **Project:** https://github.com/yeastrc/limelight-import-comet-percolator --
  version 2.8.1, tag ``v2.8.1``, release asset
  ``cometPercolator2LimelightXML.jar``.
* **How CometGUI obtains it:** downloaded from the project's own release at
  install time, by pinned URL and SHA-256 (``manifests/tools.json``), and
  never redistributed by CometGUI (``D-008``). CometGUI runs it unmodified.
  It also reads, at run time, the Limelight XML schema (``limelight-xml.xsd``)
  the converter carries inside its JAR, to validate the converter's output.
* **Licence: Apache-2.0**, from the upstream ``LICENSE`` at tag ``v2.8.1``.

The upload protocol
-------------------

CometGUI uploads to a Limelight server with its own client of the
*submit-import* protocol defined by ``limelight-core``: the endpoints, the XML
messages and their field names, and how the file and its parameters travel.
**No code from limelight-core is part of the application**: the protocol was
read from the project's source and the client was written for CometGUI
(:doc:`developer/limelight_integration`). The test suite's local stand-in
server reproduces the real server's refusal flags and messages, taken from that
source, so that tests see what a real refusal looks like.

* **Project:** https://github.com/yeastrc/limelight-core -- tag ``v8.3.1``
  (commit ``61554282dc77ae48abdb3a382a8f0dce3a2f0be6``), the source of the
  wire format.
* **Licence: Apache-2.0** (``limelight-core`` ``LICENSE`` at that tag).
* **Its importer program,** ``limelightSubmitImport.jar`` v8.3.1, is **not**
  used by CometGUI. It was downloaded once, pinned by SHA-256, for the Phase
  12 spike (:doc:`developer/limelight_upload_spike`), and is run only by a
  test, against CometGUI's local stand-in server, to show the stand-in
  accepts what the real program sends. It is never redistributed.
* The schema of the protocol's messages that CometGUI's tests check against,
  ``submit-import-wire-v16.xsd``, was generated by JAXB from that program's
  own classes.

The mzTab format
================

The file CometGUI writes for PDV's window follows the shape of mzTab 1.0.0 as
far as PDV 2.7.0 accepts it. It is generated only for PDV and is not offered as
an mzTab interchange file (:doc:`pdv`). The format is the HUPO Proteomics
Standards Initiative's:

   Griss J, Jones AR, Sachsenberg T, Walzer M, Gatto L, Hartler J, *et al.* The
   mzTab data exchange format: communicating mass-spectrometry-based
   proteomics and metabolomics experimental results to a wider audience.
   *Molecular & Cellular Proteomics* **13** (10), 2765--2775 (2014).
   https://doi.org/10.1074/mcp.O113.036681

Its controlled-vocabulary terms (``MS:...``, ``UO:...``) are from the PSI-MS
and Unit ontologies.

UniProt
=======

Two protein sequence databases from UniProt are kept in the CometGUI source
repository, as test data: one whole proteome and a subset of another. Neither
is part of the installed application, and CometGUI never chooses a database
for you: you always supply your own FASTA.

   The UniProt Consortium. UniProt: the Universal Protein Knowledgebase in
   2025. *Nucleic Acids Research* **53** (D1), D609--D617 (2025).
   ``doi:10.1093/nar/gkae1010``

**The** *Synechococcus* **proteome.**

* **What:** the reference proteome of *Synechococcus* sp. strain WH8102, which
  UniProt now names *Parasynechococcus marenigrum* (strain WH8102) -- proteome
  ``UP000001422``, taxonomy identifier 84588 -- whole and unaltered: 2,513
  records, 1,080,427 bytes, SHA-256
  ``b4c8aaf48778566e87b0517af6f861bf0b93b33a3ebd95fd071bab9a6dd1750d``.
* **Where:** ``fixtures/synechococcus-wh8102/UP000001422_84588.fasta``, with
  ``ATTRIBUTION.txt`` beside it, which reproduces UniProt's copyright statement
  as its terms require.
* **From:** UniProt release 2026_04 (04-Nov-2026), downloaded on 2026-11-12
  from
  ``https://ftp.uniprot.org/pub/databases/uniprot/current_release/knowledgebase/reference_proteomes/Bacteria/UP000001422/UP000001422_84588.fasta.gz``.
  That address serves a different file after each UniProt release, which is
  why the file is committed rather than downloaded when needed.
* **Licence:** `Creative Commons Attribution 4.0 International (CC BY 4.0)
  <https://creativecommons.org/licenses/by/4.0/>`__, which UniProt applies to
  all copyrightable parts of its databases (https://www.uniprot.org/help/license).
* **What it is for:** it is the database for the test that searches a Thermo
  RAW file on Windows. That file, ``ph050213_18.raw`` of PRIDE project
  ``PXD002914``, is downloaded for the test and is not in the repository
  (``DECISIONS.rst``, ``D-019``).

**A subset of the human proteome.**

* **What:** 3,698 of the 20,652 records of the human reference proteome --
  proteome ``UP000005640``, taxonomy identifier 9606, canonical sequences:
  2,539,164 bytes, SHA-256
  ``bd252b62568d88881e0d13bcdfaa6b9db96666af0ba08fc51863fcd7bdec1b34``. **It is
  not the proteome.** Each record it holds is UniProt's, unaltered and in
  UniProt's order.
* **Where:**
  ``fixtures/human-k562-subset/uniprot-human-2026_02-k562-subset.fasta``, with
  ``ATTRIBUTION.txt`` beside it, which reproduces UniProt's copyright statement
  and states the changes made, and ``accessions.tsv``, which lists the records
  kept.
* **From:** UniProt release 2026_02 (10-Jun-2026): the file
  ``UP000005640_9606.fasta.gz`` (SHA-256
  ``cf49a88c4812dabbd934cb3e2e00b449e70375816e4d47cda7cc5b77b0754024``),
  downloaded on 2026-08-29 from UniProt's ``current_release`` directory and
  found again, the same bytes, on 2026-11-12 in EMBL-EBI's reference-proteome
  directory for the Quest for Orthologs, whose README says it is based on
  that release.
* **Changes made:** a subset. 698 records are the proteins identified at a
  q-value of 0.01 or less when the test's two spectrum files are searched
  against the whole proteome; 3,000 more are a background chosen by a fixed
  rule over the accession. :doc:`developer/test_fixtures` gives both rules
  and what was measured to choose them.
* **Licence:** `Creative Commons Attribution 4.0 International (CC BY 4.0)
  <https://creativecommons.org/licenses/by/4.0/>`__, as above.
* **What it is for:** it is the database CometGUI's tests search two human
  K562 spectrum files against. Those files, from the Crux toolkit's smoke
  tests, are downloaded for the tests and are not in the repository
  (``DECISIONS.rst``, ``D-006`` and ``D-018``). It was chosen to contain what
  those two files identify, so it is of no use for any other search.
