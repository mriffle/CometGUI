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
   third-party component or format records it here. The entries below were
   added by Phase 11 (PDV Integration) on 2026-10-11. Comet, Percolator, the
   Limelight converter and the bundled Java runtime are not yet listed.

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
