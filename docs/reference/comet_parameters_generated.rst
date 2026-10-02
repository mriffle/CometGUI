.. _ref-comet-parameters-generated:

=========================
Comet parameter reference
=========================

One entry for every Comet parameter CometGUI models, grouped by the categories
of the Advanced editor. Each entry gives the Comet name and the name the
application shows, the category and type, the default for the Comet release
CometGUI installs, the allowed values or range, a description with a link to
Comet's own documentation, the exact ``name = value`` line written to
``comet.params``, which Comet releases declare it, related parameters, and what
each built-in preset sets it to (``R-DOC-04``).

Everything below the introduction is **generated during the documentation
build** by ``scripts/cometparams.py`` from the same metadata file the
application reads, so this page and the parameter editor cannot say different
things. A parameter missing from that metadata, or an entry missing a field,
fails the documentation build rather than producing a shorter page. How it is
made: :ref:`dev-comet-parameter-generated-reference`.

.. include:: /_generated/comet-parameters.rsti
