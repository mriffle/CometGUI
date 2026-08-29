.. _dev-architecture:

============
Architecture
============

.. note::

   **Stub page -- no content yet.** Content is owned by **Phase 02 -- Application Shell and Navigation** (``phases/PHASE-02-app-shell.rst``), which names this page in its deliverables. Its deliverable is worded as *describing the real layering as built*, so it is written against the code rather than ahead of it.

   Phase 01 created it so that the documentation tree builds strictly
   (``sphinx-build -n -W``) from the start and so that the page has a
   stable name to link to. It does not yet describe the product.

What this page will cover
=========================

The module layout, the MVVM boundary, dependency injection and the rules
that the ArchUnit suite enforces: no scientific logic, hashing, download or
parsing code in JavaFX controllers, and process creation only through the
process service.
