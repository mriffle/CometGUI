.. _dev-tool-adapters:

=============
Tool adapters
=============

.. note::

   **Stub page -- no content yet.** Content is owned by **Phase 03 -- Process Service** (``phases/PHASE-03-process-service.rst``), which names the process-service section of this page in its deliverables. Phases 08, 09, 11 and 12 add the sections for their own adapters as those adapters are built.

   Phase 01 created it so that the documentation tree builds strictly
   (``sphinx-build -n -W``) from the start and so that the page has a
   stable name to link to. It does not yet describe the product.

What this page will cover
=========================

The shape every tool adapter shares: argument arrays rather than shell
strings, streaming output, cancellation and descendant termination, log
archiving, and the fake-tool suite used to test all of it without the real
binaries.
