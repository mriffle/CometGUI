.. _dev-testing:

=======
Testing
=======

.. note::

   **Stub page -- no content yet.** Content is completed by **Phase 15 -- Version Matrix, Performance and Hardening** (``phases/PHASE-15-hardening.rst``), which names this page in its deliverables. Phase 01 builds the gates it will describe -- JUnit Jupiter, JaCoCo, ArchUnit, PIT and the strict documentation build.

   Phase 01 created it so that the documentation tree builds strictly
   (``sphinx-build -n -W``) from the start and so that the page has a
   stable name to link to. It does not yet describe the product.

What this page will cover
=========================

How to run the suites, what each gate enforces and at what threshold, the
headless JavaFX recipe, and the flakiness policy: a flaky test is a defect,
and a test that asserts *did not throw* is not a test.
