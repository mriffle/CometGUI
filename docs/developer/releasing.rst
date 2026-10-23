.. _dev-releasing:

=========
Releasing
=========

.. note::

   **Stub page -- no content yet.** Content is owned by **Phase 16 -- Documentation and Release Qualification** (``phases/PHASE-16-release.rst``), which names this page in its deliverables.

   Phase 01 created it so that the documentation tree builds strictly
   (``sphinx-build -n -W``) from the start and so that the page has a
   stable name to link to. It does not yet describe the product.

What this page will cover
=========================

The release checklist: packaging per tier-1 platform, checksums, SBOM,
signing and notarisation where infrastructure permits, and the gates that must
be green before anything is published.

Phase 14 built the pieces this page will assemble, all Linux only so far: the
``jpackage`` configuration and the packaging, install and smoke scripts
(:ref:`dev-e2e-packaging`), the release check that no test bridge ships
(:ref:`dev-e2e-no-bridge`), and the release steps that remain stubs until
``D-006``'s CI fixture and Phase 16's ``release-package.sh`` exist
(:ref:`dev-e2e-ci`).
