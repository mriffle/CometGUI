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

.. _platform-support-version-matrix:

What has been run, and where: the version matrix
================================================

The table above says what upstream publishes. This one says what has actually
been **run**: the result of the version matrix (Phase 15), last run on
**2026-11-06** on Linux x86-64, the only kind of machine this project has. Every
combination is one of four things:

* **run** -- installed from its upstream download by CometGUI's own Tool
  Manager into an empty tool cache, checked by running it, and passed;
* **unavailable** -- CometGUI does not offer it there, for the recorded reason;
* **unverified** -- CometGUI offers it, but no machine of that kind has run it
  yet. Unverified is not the same as working;
* (a combination that was run and failed would say so; none did.)

.. list-table:: Version matrix, 2026-11-06
   :header-rows: 1
   :widths: 16 21 21 21 21

   * - Platform
     - Comet 2026.02.2, 2026.03.0
     - Percolator 3.07.1, 3.06.5
     - Percolator 3.09
     - PDV 2.7.0, Limelight converter 2.8.1
   * - Linux x86-64
     - **run** (both): installed, ``-p``/``-q`` exactly as recorded, every
       Comet with every Percolator searched end to end over the local test
       data
     - **run** (both): installed, probed able to write XML for Limelight, run
       over CometGUI's own test input and every Comet search
     - **unavailable** -- no Linux download CometGUI can install (``D-003``).
       A 3.09 you install yourself and register **was run** and works,
       without XML
     - **run** (both): installed and identified; their own real runs are in
       the PDV and Limelight checks
   * - macOS Apple silicon
     - unverified
     - unverified (runs under Rosetta 2, ``D-004``)
     - unverified
     - unverified
   * - macOS Intel
     - **unavailable** -- no Intel Comet exists (``D-011``); register one you
       built
     - unverified
     - **unavailable** -- 3.09 for macOS is Apple silicon only
     - unverified
   * - Windows x64
     - unverified; reading Thermo RAW files is unverified too
     - unverified
     - unverified
     - unverified
   * - Linux ARM (aarch64)
     - unverified (not supported in release 1)
     - **unavailable** -- no ARM Linux Percolator is published
     - **unavailable**
     - unverified (not supported in release 1)

The full list -- one row per tool, version and platform, 61 rows, with the
reason for each -- is the matrix's own report, which the developer
documentation explains at :ref:`dev-testing-version-matrix`. Where the
versions behave differently (different defaults, a different number of
results from the same data), the differences are listed at
:ref:`dev-version-matrix-advisories`.
