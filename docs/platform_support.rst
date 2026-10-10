.. _user-platform-support:

================
Platform support
================

.. note::

   **Partly written.** The per-platform artefact table below is **Phase 05**'s
   (``phases/PHASE-05-tool-registry.rst``), which names this page in its
   deliverables and supplies the table the rest of the page is built on. The
   plain-language statement ``R-DOC-06`` requires -- which Percolator versions
   are available as managed installs on each platform, which support XML, and
   therefore where the Limelight path works out of the box -- is **Phase 16 --
   Documentation and Release Qualification**'s
   (``phases/PHASE-16-release.rst``), and is not written yet.

   ``R-DOC-06`` obligation, recorded here so it is not lost: this page shall
   state plainly, per platform, which Percolator versions are available as
   managed installs, which of them support XML, and therefore where the
   Limelight path works out of the box -- rather than leaving a user to discover
   it at conversion time. Phase 00's evidence for the artefact matrix is at
   :doc:`feasibility/percolator-artefacts` and
   :doc:`feasibility/noxml-capability`.

.. _platform-support-artefact-table:

The artefact table
==================

What follows is **what upstream publishes and what the installer pins**, read
from ``manifests/tools.json`` -- the same file the application reads at run
time. It is generated during the documentation build by
``scripts/toolmatrix.py`` rather than typed, so it cannot say something the
manifest does not; see :ref:`dev-tool-registry-generated-tables`.

Two things the table does not say, and a reader should not read into it.

**A declared capability is not a probed one.** The capability column is what
the manifest records *before* anything is installed, with the evidence behind
each claim: ``observed-by-execution`` where this project ran that binary on
that platform, ``inferred-from-artefact-bytes`` where the claim comes from
markers found inside the artefact, and ``unverified`` where it comes from
upstream's release notes alone. ``R-TOOL-07``'s probe runs the installed binary
on the host and its answer is the one the application acts on.

**Only ``linux-x86-64`` carries capabilities this project watched a binary
perform**, with one exception. No macOS binary has ever been executed anywhere
in this project, on any machine, so every macOS row is an inference. One
Windows row is not: Percolator 3.07.1's portable ``noxml`` binary was executed
once on a GitHub ``windows-latest`` runner, which is why that row's
``XML_OUTPUT`` reads ``observed-by-execution`` and names the run. One run on
one hosted image is not a clean end-user machine, and the row says what it saw
rather than what it implies.

**There is no Comet for an Intel Mac.** The table has no ``macos-x86-64``
Comet row, for any release, because the Comet developers have never published
one: both macOS files they publish are Apple-silicon (arm64) programs, whatever
their names suggest, and an Apple-silicon program cannot run on an Intel Mac
(``D-011``). On an Intel Mac the Tool Manager says so and offers to register a
Comet you have built yourself instead; see :ref:`tool-manager-no-managed-build`.
Percolator, PDV and the Limelight converter are unaffected there.

.. include:: /_generated/tool-platform-matrix.rsti

Where the numbers come from
---------------------------

Every URL, size, SHA-256 and licence behind the table is in the developer
documentation at :ref:`dev-tool-registry-provenance`, generated from the same
manifest in the same build.
