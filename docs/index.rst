.. _cometgui-docs:

======================
CometGUI documentation
======================

CometGUI is a cross-platform desktop application that runs a complete
Comet |rarr| Percolator proteomics search workflow, installs every scientific
tool it needs by itself, and records a provenance trail strong enough to
reproduce a run later.

.. |rarr| unicode:: U+2192

.. warning::

   **This tree is a skeleton, not the product documentation.** Phase 01 created
   every page below as a stub so that the strict documentation build
   (``sphinx-build -n -W``) is in place before there is anything to document.
   Each page names the phase that owns its content. Phase 16 completes the
   tree; until then, ``specification.rst`` in the repository root is the
   authority on what CometGUI does.

User guide
==========

.. toctree::
   :maxdepth: 2
   :caption: User guide

   installation
   getting_started
   workflow
   comet_parameters
   comet_parameter_presets
   variable_modifications
   decoys
   percolator
   results
   learned_feature_weights
   pdv
   limelight
   provenance
   tool_manager
   platform_support
   troubleshooting
   faq
   citations
   release_notes

Reference
=========

Generated and hand-written reference material. The Comet parameter reference is
generated from the parameter schema (``R-DOC-04``) so that the documentation and
the GUI cannot silently diverge.

.. toctree::
   :maxdepth: 1
   :caption: Reference

   reference/comet_parameters_generated
   reference/percolator_options
   reference/project_format
   reference/provenance_format
   reference/command_examples

Developer documentation
=======================

.. toctree::
   :maxdepth: 2
   :caption: Developer

   developer/index
