.. _ref-comet-parameters-generated:

=========================
Comet parameter reference
=========================

.. warning::

   **Placeholder -- this page is generated, and this is not the generated
   version.** ``R-DOC-04`` requires the Comet parameter schema itself to
   generate this page, so that the user documentation and the GUI metadata
   cannot silently diverge. The schema is built in **Phase 06 -- Comet
   Parameter Model** (``phases/PHASE-06-comet-param-model.rst``), which owns
   ``R-DOC-04``; Phase 01 committed this stub so that the documentation tree
   builds and so that the page has a stable name to link to.

   When Phase 06 lands, this file is replaced by generated content. Nothing
   written here by hand will survive.

What the generated page will contain
====================================

One entry per Comet parameter -- 118 of them for Comet 2026.02.2, several of
them structured tuples rather than scalars -- each giving, per ``R-DOC-04``:

* the Comet parameter name and the GUI display name;
* the category and the type;
* the default for the versioned schema;
* allowed values or range;
* a scientific description;
* the serialisation form written to ``comet.params``;
* version availability;
* related parameters;
* preset effects, where useful.

The generator is the same schema the editor UI reads, which is the point: a
parameter that changes in the schema changes here in the same build.
