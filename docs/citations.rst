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
   2026-10-12. Comet, Percolator and the bundled Java runtime are not yet
   listed.

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
