=========
Decisions
=========

:Updated: 2026-08-28

Decisions an implementing agent **must not make on its own**. Each names what
it blocks, the options with their costs, and a recommendation. When the owner
answers, record the answer, the date and the reasoning in the entry, set its
status to ``DECIDED``, and update ``STATUS.rst``.

An agent that invents an answer here -- adds a licence, picks a dataset, points
a test at a real server -- has made an error, not a shortcut.

Status values: ``OPEN``, ``DECIDED``, ``SUPERSEDED``.

----

D-001 -- CasanovoGUI source reuse
=================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phase 02 (derivation), Phase 16 (redistribution), ``AC-REL-02``
:Owner: Project owner, with the CasanovoGUI copyright holders

**Question.** May CometGUI be derived from ``Noble-Lab/CasanovoGUI``?

**Facts.** Verified 2026-08-28: the repository is public, active (last pushed
2026-08-21), Java, and publishes **no licence** -- GitHub reports no licence
and no licence file is detected. A public repository is not a grant of
redistribution rights; absent a licence, default copyright applies.

**Options.**

#. Obtain an explicit licence upstream (ask the authors to add one). Best
   outcome; costs only time and goodwill.
#. Obtain written permission for this derivative and record it. Adequate, but
   weaker for downstream users of CometGUI.
#. Write CometGUI independently, using CasanovoGUI only as design reference.
   The specification's architecture is implementable from scratch; the cost is
   the shell, packaging and installer patterns, which are perhaps a phase of
   work, not a project.

**Recommendation.** Ask upstream now (option 1), and proceed on option 3 in the
meantime. Do not let this block the project, and do not copy code while it is
open (``R-SEC-01``).

----

D-002 -- XML-capable Percolator artefact strategy
=================================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phases 05, 09, 12, 15, 16; the product's Limelight promise
:Owner: Project owner, with the engineering team

**Question.** The product's policy is to use the **latest compatible**
Percolator. How does CometGUI obtain that version -- one that can emit the XML
the Limelight converter requires -- on each tier-1 platform, without
administrative rights?

**Facts.** Verified 2026-08-28 from the upstream release assets:

* **The latest compatible version is 3.08.1** (tag ``rel-3-08-01``,
  2025-07-08), whose one commit fixes a PEP-greater-than-1.0 bug. It is a tag
  with no GitHub release: **no published binary exists for it on any
  platform**.
* Percolator 3.09 removed XML/XSD I/O (stated verbatim in its release notes),
  so no version newer than 3.08.1 can serve the Limelight path.
* XML has always been opt-in at build time:
  ``option(XML_SUPPORT ... OFF)``, pulling in Xerces-C and XSD. Upstream's
  ``noxml`` artefacts are just the default build. The option and its code are
  gone in 3.09.
* The Limelight converter still hard-requires Percolator XML -- its README and
  ``--help`` both say so -- and has had no substantive change since before the
  removal. There is no converter version that unblocks 3.09.
* Bioconda is not a way out: its Percolator package is deliberately built
  without the XSD/Xerces path and skips macOS entirely.
* ``rel-3-08`` publishes exactly five assets. The only XML-capable one is
  ``percolator-v3-08-linux-amd64.deb``. The macOS and Windows portable archives
  are ``percolator-noxml-*``.
* Every portable archive Percolator publishes, in every release inspected, is a
  ``noxml`` build. XML-capable builds are OS packages (``.deb``, ``.rpm``,
  ``.pkg``, installer ``.exe``).
* The 3.08 ``.deb`` extracts without root -- confirmed by extracting it -- but
  its binary requires ``GLIBC_2.38`` and ``GLIBCXX_3.4.32`` and fails to load
  on a glibc 2.36 host. That excludes Ubuntu 22.04, Debian 12 and RHEL 9.
* The newest XML-capable Windows and macOS builds are from ``rel-3-07-01``
  (2024): an installer ``.exe`` and an x86-64 ``.pkg``.

**Options.**

#. **Build it (``rel-3-08-01`` with ``-DXML_SUPPORT=ON``).** Percolator is
   Apache-2.0, so redistribution is permitted. Build in CometGUI CI for each
   tier-1 platform, statically linked or against an old glibc, publish as
   CometGUI release artefacts with checksums, register as ``project-built``.
   *Cost:* a Xerces-C/XSD build on three platforms plus maintenance.
   *Benefit:* the **only** option that delivers the latest compatible version
   at all; it also covers Windows and macOS and removes the glibc floor.
#. **Package-payload extraction.** Extract the upstream ``.deb``/``.rpm``/
   ``.pkg`` payloads in-process. *Cost:* modest for ``.deb`` (verified feasible
   in pure JDK code); harder for ``.pkg``; Windows' XML-capable artefact is an
   installer, so it does not solve Windows. *Ceiling:* 3.08.0 -- one release
   behind latest compatible, carrying the PEP-greater-than-1.0 defect -- and it
   inherits the glibc 2.38 floor.
#. **Older XML-capable version where nothing else exists.** 3.07.1 on Windows
   and macOS. *Cost:* installer extraction, macOS x86-64 only, and a two-year-
   old Percolator on two platforms.
#. **Platform-scoped feature.** Limelight conversion Linux-first, documented as
   such; local-binary registration elsewhere. *Cost:* the zero-manual-install
   promise is not met for Limelight on two platforms.

**Recommendation.** **Option 1**, because the instruction to use the latest
compatible Percolator cannot be satisfied any other way -- 3.08.1 has no binary
to download on any platform, so the choice is to build it or to ship something
older. Option 4 remains the honest interim state for platforms where the build
is not yet done: the UI says the Limelight path is unavailable there rather
than pretending. Options 2 and 3 are worth taking only as a stop-gap on a
single platform, and both mean shipping a Percolator with a known invalid-PEP
defect. Do not build the manifest or the UI around "3.08 means XML"; version
selection is computed from probed capability (``R-PERC-02``).

----

D-003 -- Managed Percolator version/platform coverage
=====================================================

:Status: OPEN
:Raised: 2026-08-28 (carried from specification revision 1)
:Blocks: Phases 05, 09, 15
:Owner: Project owner

**Question.** Which Percolator version and platform pairs does the product
offer as managed one-click installs, given ``D-002``?

**Facts.** Adapter- and schema-level support for >= 3.05 is feasible. Managed
installation depends on a usable binary existing per pair. Some pairs have no
published artefact at all.

**Recommendation.** Publish the matrix explicitly in
``docs/platform_support.rst``: for each pair, managed / local-binary-only /
unsupported. The UI must not offer what the manifest does not contain
(``R-PERC-01``), and CI must not test pairs the product does not offer.

----

D-004 -- macOS architecture policy
==================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phases 05, 16
:Owner: Project owner

**Question.** On Apple silicon, how does the Percolator stage run?

**Facts.** Comet publishes native ``aarch64`` and ``x86-64`` macOS builds.
Percolator's XML-capable macOS artefacts are ``x86-64`` only; its 3.09 portable
macOS archive is not XML-capable.

**Options.** Require Rosetta 2 for the Percolator stage; ship a project-built
``arm64`` Percolator (a subset of ``D-002`` option 1); or scope the Limelight
path off macOS for release 1.

**Recommendation.** Decide together with ``D-002``; they share an answer.

----

D-005 -- PDV integration level
==============================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phase 11
:Owner: Project owner

**Question.** Does release 1 need exact click-a-row-to-update-PDV selection?

**Facts.** PDV supports the needed file formats, but its documented external
control server is de novo specific. A generalised database-search control mode
does not exist upstream.

**Options.** Baseline only (open-in-PDV plus CLI figure generation); or
baseline plus an upstream contribution or a small maintained fork.

**Recommendation.** Ship baseline for release 1 and propose the enhancement
upstream. Do not substitute screen-coordinate automation.

----

D-006 -- Test fixture data and licensing
========================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phases 00, 14, 16
:Owner: Project owner

**Question.** Which spectra and FASTA are the project's fixtures, under what
licence, vendored or fetched?

**Constraints.** Fixtures must be small enough to run in CI, real enough to
produce both target and decoy PIN rows, redistributable in a public repository
or fetchable reproducibly by checksum, and stable for the life of the goldens.
At least two spectrum files are needed to exercise the multi-file run model.

**Recommendation.** Prefer a small, explicitly licensed public dataset,
fetched by checksum rather than vendored, with the licence recorded in
``docs/citations.rst``.

----

D-007 -- Limelight test endpoint
================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phases 12, 14
:Owner: Project owner

**Question.** What do upload tests talk to?

**Constraint.** No automated test uploads to a production Limelight server.

**Options.** A local fake endpoint implementing the upload API; an official
sandbox instance; or both -- the fake for every run, the sandbox nightly.

**Recommendation.** Both, with the fake as the default so the suite works
offline.

----

D-008 -- CometGUI licence and distribution
==========================================

:Status: OPEN
:Raised: 2026-08-28
:Blocks: Phase 01 (``LICENSE`` file), Phase 16
:Owner: Project owner

**Question.** Under what licence is CometGUI released, where is it published,
and are project-built tool binaries distributed alongside it?

**Constraints.** Constrained by ``D-001`` (derivation) and ``D-002``
(redistributed binaries). There is currently **no git remote**, and creating
one is part of this decision -- an agent must not add one.

**Recommendation.** Decide the licence early enough for phase 01 to place the
file; defer publication until ``D-001`` is resolved and the private-content
scan of the repository has been re-run.
