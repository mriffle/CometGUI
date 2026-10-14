.. _dev-index:

=======================
Developer documentation
=======================

How CometGUI is built, tested and released. The requirements themselves live in
``specification.rst`` in the repository root, which is authoritative; these
pages describe the implementation that satisfies them.

.. note::

   **Three pages below are still stubs created by Phase 01**: :doc:`security`
   (Phase 12 added its credentials and upload section), :doc:`e2e_harness`
   and :doc:`releasing`. Each names the phase that owns its content. Every
   other page has been written by a phase that built what it describes. This
   index and the strict documentation build existed
   first on purpose: a documentation gate added at the end of a project
   measures nothing.

.. toctree::
   :maxdepth: 2
   :caption: Developer guide

   architecture
   workflow_engine
   comet_parameter_schema
   tool_adapters
   tool_registry
   version_capabilities
   results_model
   pdv_integration
   pdv_mztab_spike
   limelight_integration
   limelight_upload_spike
   provenance_schema
   provenance_ui
   security
   testing
   e2e_harness
   traceability
   releasing

.. _dev-feasibility-appendix:

Appendix: Phase 00 feasibility evidence
=======================================

The documents under :doc:`/feasibility/index` are **Phase 00 evidence, not
product documentation**. They record what was verified on 2026-08-29 --
upstream versions and checksums, per-platform Percolator artefacts, the
``noxml`` capability finding, the toolchain and ``jpackage`` results, the GUI
automation verdict, the scripted scientific path and the fixture shortlist --
together with the method used and the limits of each finding.

They are kept in the built tree deliberately. They are the reason several later
phases are scoped the way they are, and a claim in them that later turns out to
be wrong should be traceable to the evidence that produced it. Read them as
dated findings; where they disagree with ``specification.rst``, the
specification wins.

.. toctree::
   :maxdepth: 1
   :caption: Phase 00 evidence

   /feasibility/index
