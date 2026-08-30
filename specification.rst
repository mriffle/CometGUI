.. _cometgui-specification:

##############################################################################
CometGUI: Comet + Percolator Desktop Workflow -- Implementation Specification
##############################################################################

:Status: Implementation-ready design specification
:Revision: 9
:Revision date: 2026-08-30
:Supersedes: Revision 8, 2026-08-30
:Target application: Cross-platform Java desktop application
:Primary source base: Noble-Lab CasanovoGUI (GPL-3.0). Derivation approved 2026-08-29 (``D-001``)
:Licence: **GPL-3.0** -- decided 2026-08-29 (``D-001``, ``D-008``)
:Primary search engine: Comet
:Primary post-processor: Percolator
:Visualisation: PDV
:Downstream integration: Limelight
:Documentation format: reStructuredText / Sphinx / Read the Docs

.. contents:: Contents
   :depth: 2
   :local:

.. _spec-revision-history:

Revision History
================

.. list-table::
   :header-rows: 1
   :widths: 8 14 78

   * - Rev
     - Date
     - Summary
   * - 9
     - 2026-08-30
     - **``D-003`` decided: three managed Percolator versions.** 3.07.1 (the
       computed default for a Limelight-enabled run), 3.09 (current, for runs
       that do not need Limelight) and 3.06.5 (reach -- its portable Linux
       build needs only ``GLIBC_2.14``, the lowest floor in the release
       history). The set is an intent, not a per-platform promise:
       ``R-PERC-01`` still requires a verified artefact **and** a passed
       runtime probe before any pair is offered, and Phase 00's findings mean
       3.09 on Linux may end up unoffered -- it publishes no portable archive,
       its ``.deb`` needs ``GLIBC_2.38``, and its ``.rpm`` needs Boost
       libraries it does not ship. With this, **no ``D-`` decision remains
       open.**
   * - 8
     - 2026-08-30
     - **``D-005`` decided: CometGUI drives PDV exactly as CasanovoGUI does,
       via an mzTab bridge.** This document previously assumed a hypothetical
       ``db-gui`` control mode and, failing that, offered only baseline
       open-in-PDV or a PDV fork. Inspection of ``Noble-Lab/CasanovoGUI``
       establishes that PDV's control server is real, reachable and already
       driven in production -- but **only** through ``denovo-gui --mztab``.
       Their ``PdvLauncher`` has no pepXML or mzID path whatsoever. The owner
       therefore chose a third route the earlier revisions did not contain:
       **generate mzTab from Comet + Percolator results and reuse PDV's
       existing, unmodified control server.** No fork, no upstream dependency,
       and the launcher/controller machinery is reusable because ``D-001``
       makes CometGUI a GPL-3.0 derivative of CasanovoGUI. The cost is a real
       component -- an mzTab exporter with its own tests -- governed by a
       fidelity requirement the owner stated directly: it must be *"accurate
       and true to the original results"*. That is written into ``R-PDV-03``
       as a falsifiable rule rather than an aspiration.
   * - 7
     - 2026-08-29
     - **Percolator artefact strategy decided (``D-002`` option C), and the
       managed-tool distribution model decided (``D-008``, second half).** The
       owner acted on Phase 00's ``noxml`` finding. The premise this document
       carried from revision 1 -- that every portable archive is a ``noxml``
       build and therefore every XML-capable artefact is an operating-system
       package whose payload must be extracted -- is **withdrawn**. It is true
       about the *build* and false about the *capability the Limelight stage
       needs*: ``XML_SUPPORT`` gates the pin-XML **reader** (``--xml-in``),
       which Comet never produces and the product never uses, while the
       pout-XML **writer** is present in every published 3.05--3.08 artefact,
       both twins, all three tier-1 platforms. The product therefore obtains
       Percolator from the **portable ``noxml`` archives**, and phase 05 does
       **not** implement NSIS or ``xar``/cpio payload extraction. Two costs are
       carried explicitly rather than discovered later: no portable archive
       ships the XSD companion files ``R-TOOL-02`` requires, and the Windows
       portable zip contains ``percolator.exe`` alone without the nine Visual
       C++ runtime DLLs the NSIS installer places beside it. Separately, the
       owner decided that managed tool binaries are **downloaded from upstream
       by pinned URL and checksum, not redistributed** with CometGUI, which
       keeps Apache-2.0 s4 notice obligations off the release artefacts and
       makes ``R-TEST-08``'s manifest-verification job load-bearing rather than
       advisory.
   * - 6
     - 2026-08-29
     - **Licensing decided.** ``Noble-Lab/CasanovoGUI`` published GPL-3.0 on
       2026-08-29, and the owner decided that CometGUI **derives from it and is
       released under GPL-3.0** (``D-001``). The derivation gate throughout this
       document is therefore met: the Executive Summary's "shall be derived from
       the CasanovoGUI code base" is now authorised rather than conditional, and
       ``R-SEC-01``'s prohibition on copying CasanovoGUI source is lifted,
       replaced by an obligation to retain its copyright notices and record the
       derivation. Consequences recorded rather than assumed: Apache-2.0 (Comet,
       Percolator, the Limelight converter) is one-way compatible **into**
       GPL-3.0, so those dependencies raise no conflict -- GPLv2 would have,
       which is why the version matters; none of the three ships a ``NOTICE``
       file, so Apache-2.0 s4(d) does not bite; Comet's ``LICENSE`` is
       Apache-2.0 plus an embedded MIT section (Gygi Lab, 2022), also
       compatible; and the bundled Liberica JRE is GPLv2 **with the Classpath
       Exception**, which exists to permit exactly this combination. **PDV is to
       be treated as GPL-3.0** by owner direction, resolving its upstream
       ``LICENSE``/``pom.xml`` contradiction conservatively -- the assumption
       constrains the project more than the Apache reading would, so it cannot
       create a violation, but Phase 16 must still obtain the real answer
       upstream. New obligations for the implementing phases: the full
       unmodified GPLv3 text at the repository root (phase 01), derivation
       notices (phase 02), third-party attribution in ``docs/citations.rst``,
       and a source-availability mechanism for installer recipients (phase 16),
       which depends on the still-open publication half of ``D-008``.
   * - 5
     - 2026-08-29
     - **Phase 00 verification amendment.** Recorded, without yet rewriting the
       Percolator artefact strategy, the findings the phase established by
       execution. (a) The ``noxml`` Percolator builds **do** emit
       ``percolator_out`` XML: the 3.07.1 Linux ``noxml`` binary given ``-X``
       writes a document differing from the XML-capable build's in two lines,
       both inside ``<command_line>``. ``XML_SUPPORT`` gates the pin-XML
       **input** path (``--xml-in``), not the pout-XML **output** path, and
       Comet writes tab-delimited PIN, so the product never needs the input
       path. The premise "portable archive = ``noxml`` = no XML" that underlies
       :ref:`spec-percolator-artefacts` is therefore wrong for XML output; the
       *choice* of 3.07.1 is unaffected and was re-derived independently from
       live release data. Acting on this -- which would make NSIS and
       ``xar``/cpio extraction optional -- re-scopes Phase 05 and is
       ``D-002`` option C, an owner decision, so the strategy sections are
       left standing and flagged rather than rewritten. (b) ``--help`` text is
       **identical** between the twins, so a help-text capability probe is
       invalid; ``R-PERC-02`` requires a functional probe. (c) Percolator's own
       shipped ``percolator_out.xsd`` fixes ``majorVersion`` at ``2`` while the
       3.07.1 binary writes ``3``, so validating correct output against the
       shipped schema **fails**; this bears on ``R-TOOL-02``. (d) Corrections to
       the verified-facts table: the Comet release publishes a **ninth** asset
       (``README.md``); ``comet.macos.exe`` and ``comet.aarch64.macos.exe`` are
       byte-for-byte the same size (3 998 328 B), which a download manifest
       must not treat as a coincidence; the ``rel-3-09`` XML-removal quote is a
       **reflow**, the published bytes breaking between ``toolchains`` and
       ``. (#399)``; the Limelight converter has **twelve** options, not ten
       (``-h/--help`` and ``-V/--version`` were omitted). (e) ``R-LL-05``'s
       rationale is wrong in both directions: ``-Z`` without
       ``--import-decoys`` is a hard converter failure, ``--import-decoys``
       over a decoy-free pout succeeds silently importing none, and
       ``--import-decoys`` is incompatible with ``decoy_search = 1`` plus a
       target-only FASTA -- it requires an externally concatenated
       target+decoy FASTA. (f) ``Noble-Lab/CasanovoGUI`` published **GPL-3.0**
       at ``2026-08-29T01:56:35Z``, during this phase; the fact table's
       "no licence" row is superseded and ``D-001`` is reframed as a copyleft
       question coupled to ``D-008``. Evidence for every item is under
       ``docs/feasibility/`` and re-runnable from ``scripts/feasibility/``.
   * - 1
     - 2026-08-28
     - Initial implementation-ready specification.
   * - 4
     - 2026-08-29
     - Owner ruled out building Percolator from source and required a version
       with published binaries on all three tier-1 platforms. Verified that
       this is **3.07.1** (``rel-3-07-01``, 2024-06-20), the newest release
       publishing XML-capable artefacts for Linux, macOS and Windows.
       Confirmed by execution on Linux and by payload extraction on macOS;
       the Windows artefact is inferred from the same naming and size pattern
       and must be verified on a Windows runner in Phase 00. ``D-002``
       resolved; ``D-004`` forced to Rosetta 2. Added NSIS and xar/cpio
       payload extraction, the XSD companion files that ship with XML builds,
       and version advisories for what 3.07.1 lacks.
   * - 3
     - 2026-08-29
     - Percolator version policy rewritten around *latest compatible* rather
       than a pinned 3.08.0. Verified that the newest XML-capable Percolator is
       the ``rel-3-08-01`` **tag** (3.08.1, 2025-07-08), which has no published
       binaries on any platform, and that XML is a compile-time option
       (``XML_SUPPORT``, default OFF) removed outright in 3.09. Verified that
       the Limelight converter still hard-requires Percolator XML and recorded
       its real command-line arguments and its ``-Z`` decoy dependency.
       Verified that Bioconda's Percolator builds are explicitly XML-free and
       skip macOS, closing that option. ``D-002``'s recommendation changes
       accordingly: build 3.08.1 from source with ``XML_SUPPORT=ON``.
   * - 2
     - 2026-08-28
     - Upstream release facts verified against live sources (see
       :ref:`spec-verified-facts`). Percolator artefact strategy rewritten
       after discovering that no XML-capable Percolator 3.08 build is
       published for Windows or macOS. Added: supported-platform matrix with
       a glibc floor and macOS quarantine handling; Comet invocation and
       output-containment rules; multi-input-file run model; a normative
       target/decoy strategy; locale-independent serialisation; input-hash
       caching; project schema versioning and single-instance locking;
       requirement identifiers and traceability; sharpened two-tier
       end-to-end test model. Converted the document to valid
       reStructuredText so it builds under the project's own strict Sphinx
       gate. Implementation sequence replaced by a pointer to ``phases/``.

.. _spec-verified-facts:

Verified Upstream Facts
=======================

Every external claim in this specification was verified against the live
upstream source on **2026-08-28**. These facts are *dated inputs*, not
constants: they shall be represented in version/capability metadata and
re-verified at the start of implementation (see ``phases/PHASE-00``) and before
each release.

.. list-table:: Upstream facts, verified 2026-08-28
   :header-rows: 1
   :widths: 26 44 30

   * - Subject
     - Verified finding
     - Source / method
   * - Comet current release
     - ``v2026.02.2``, published 2026-08-11.
     - GitHub releases API, ``UWPR/Comet``
   * - Comet artefacts
     - Standalone executables, no archive: ``comet.linux.exe``,
       ``comet.aarch64.linux.exe``, ``comet.macos.exe``,
       ``comet.aarch64.macos.exe``, ``comet.win64.exe``, plus
       ``CometWrapper.dll``, ``ThermoFisher.CommonCore.Data.dll`` and
       ``ThermoFisher.CommonCore.RawFileReader.dll`` companions for Thermo RAW
       on Windows.
     - Release asset list
   * - Comet binary linkage
     - ``comet.linux.exe`` is statically linked (no ``NEEDED`` entries) and
       runs on a glibc 2.36 host.
     - ``readelf -d``; executed
   * - Comet parameter dump
     - ``-p`` emits 96 parameters; ``-q`` emits 118. ``-q`` adds
       ``variable_mod06``--``variable_mod15``, ``mass_type_parent``,
       ``mass_type_fragment``, ``num_results``, ``peff_format``, ``peff_obo``,
       ``pinfile_protein_delimiter``, ``print_expect_score``,
       ``print_ascorepro_score``, ``spectral_library_name``,
       ``spectral_library_ms_level``, ``compoundmods_file``,
       ``protein_modslist_file``.
     - Executed ``comet -p`` / ``comet -q``
   * - Comet CLI
     - ``-P<params>``, ``-N<name>`` (*valid only with one input file*),
       ``-D<dbase>``, ``-F``/``-L`` scan range, ``-i`` fragment-ion index,
       ``-j`` peptide index. Inputs: mzXML, mzML, Thermo RAW, mgf, ms2/cms2/bms2.
     - Executed ``comet`` with no arguments
   * - Percolator current release
     - ``rel-3-09``, published 2026-05-21.
     - GitHub releases API, ``percolator/percolator``
   * - Percolator XML removal
     - Confirmed verbatim in the ``rel-3-09`` release notes: *"Removed
       XML/XSD I/O support, which was incompatible with modern C++
       toolchains. (#399)"*
     - Release notes
   * - **Percolator 3.08 XML artefacts**
     - **The only XML-capable published 3.08 artefact is
       ``percolator-v3-08-linux-amd64.deb`` (Linux x86-64).** The macOS and
       Windows portable archives are explicitly ``percolator-noxml-*``. The
       release has exactly five assets; no XML-capable Windows or macOS build
       exists.
     - Full asset list for ``rel-3-08``
   * - Percolator 3.08 Linux binary
     - The ``.deb`` is a plain ``ar`` archive (``debian-binary``,
       ``control.tar.gz``, ``data.tar.gz``) and extracts without root;
       ``./usr/bin/percolator`` is 5.0 MB, reports package version 3.08.0.
       It is dynamically linked and **requires ``GLIBC_2.38`` and
       ``GLIBCXX_3.4.32``**; it fails to load on a glibc 2.36 host with a
       loader error, not a tool error.
     - Downloaded, extracted, executed
   * - **Newest XML-capable Percolator with binaries on all three tier-1
       platforms**
     - **3.07.1 (``rel-3-07-01``, 2024-06-20)**, publishing
       ``percolator-v3-07-linux-amd64.deb``, ``percolator-v3-07-osx-x86_64.pkg``
       and ``percolator-v3-07.exe``. Each release publishes an XML build and a
       ``noxml`` twin of the same artefact, so the naming is an explicit A/B.
     - Release asset lists for every release from ``rel-3-05`` onward
   * - 3.07.1 Linux build, executed
     - Extracted from the ``.deb`` without root and **run**: prints
       "Percolator version 3.07.1, Build Date Jun 20 2024", and its help lists
       ``-X, --xmloutput <filename>`` and ``-Z, --decoy-xml-output`` ("Only
       available if -X is set"). Its highest required symbol version is
       ``GLIBC_2.34``, so it runs on RHEL 9, Ubuntu 22.04 and Debian 12 --
       unlike the 3.08.0 build.
     - Downloaded, extracted, executed, ``readelf -V``
   * - 3.07.1 macOS build, extracted
     - The ``.pkg`` is a ``xar!`` archive whose payload is gzip-compressed
       ``070707`` cpio, extractable without root. It contains
       ``./usr/local/bin/percolator`` (64-bit Mach-O) plus
       ``./usr/local/share/xml/percolator/xml-pout-1-5/percolator_out.xsd`` and
       ``xml-pin-1-3/percolator_in.xsd``, and the strings ``xerces``,
       ``xmloutput``, ``pout.xml`` and ``decoy-xml-output``. It is
       **x86-64 only**.
     - Downloaded, xar TOC parsed, payload extracted and inspected
   * - 3.07.1 Windows build, inferred
     - ``percolator-v3-07.exe`` is a valid NSIS installer (firstheader
       ``0xdeadbeef``, header 340646 B, data 1746137 B). Its payload was not
       decompressed here, so XML capability is **inferred** from the naming
       A/B and from size: the XML build is 1776 KB against the ``noxml``
       twin's 1193 KB (+49%), the same pattern as macOS (2072 vs 976 KB) and
       Linux (3110 vs 1809 KB), where XML capability was verified directly.
       Phase 00 must confirm it on a Windows runner.
     - Downloaded, NSIS firstheader parsed
   * - Newest XML-capable Percolator overall
     - ``rel-3-08-01`` (3.08.1, tagged 2025-07-08, fixing a PEP>1.0 bug) is a
       tag with no GitHub release and therefore **no binary on any platform**;
       3.08.0's only XML artefact is Linux-only and needs glibc 2.38. Both are
       out of scope: the product does not build Percolator from source.
     - Tag list, tag commit, ``CMakeLists.txt`` at each tag
   * - Percolator XML is a build option
     - ``option(XML_SUPPORT "Choose to support xml input (slower
       compilation)." OFF)`` -- XML is opt-in at compile time and pulls in
       Xerces-C and XSD. Upstream's ``noxml`` artefacts are simply the default
       build; the XML-capable ones are ``-DXML_SUPPORT=ON`` builds. In 3.09 the
       option and its code are gone entirely.
     - ``CMakeLists.txt`` at ``rel-3-08``, ``rel-3-08-01``, ``rel-3-09``
   * - Bioconda Percolator
     - Provides 3.9 for ``linux-64`` and ``linux-aarch64`` only, skips macOS
       (``skip: True  # [osx]``), and its build script states that the XSD/
       Xerces path is deliberately not built. **Not a source of XML-capable
       binaries.**
     - Anaconda API and the Bioconda recipe
   * - Limelight converter input
     - Still hard-requires Percolator XML: *"Requires that the Percolator
       output be represented as XML (see -X option in Percolator)"*, and
       ``-p, --percolator-file`` is documented as "Full path to percolator
       output XML file". No tab-delimited path exists; the repository's last
       substantive change predates the 3.09 removal.
     - Converter README and its ``--help`` output
   * - Converter arguments
     - ``-c/--comet-params``, ``-f/--fasta-file``, ``-p/--percolator-file``,
       ``-d/--pepxml-directory``, ``-q/--q-value``, ``-o/--out-file``,
       ``--import-decoys`` (which *requires* Percolator to have been run with
       ``-Z``), ``--independent-decoy-prefix``, ``--open-mod``, ``-v``.
     - Converter ``--help`` output
   * - Percolator XML on Windows/macOS
     - The newest XML-capable published builds for those platforms are
       ``percolator-v3-07.exe`` and ``percolator-v3-07-osx-x86_64.pkg``
       (installers, macOS x86-64 only), from ``rel-3-07-01`` (2024-06-20).
     - Release asset lists
   * - PDV current release
     - ``v2.7.0``, published 2026-08-14 (``PDV-2.7.0.zip``, ~99 MB). 2.6.0 is
       one release behind.
     - GitHub releases API, ``wenbostar/PDV``
   * - Limelight converter
     - ``yeastrc/limelight-import-comet-percolator``, Apache-2.0, newest
       release ``v2.8.1`` (2025-08-19), single asset
       ``cometPercolator2LimelightXML.jar``.
     - GitHub API
   * - CasanovoGUI licence
     - ``Noble-Lab/CasanovoGUI`` (Java, last pushed 2026-08-21) still
       publishes **no licence**: the GitHub licence field is null and no
       licence file is detected.
     - GitHub repository API

.. warning::

   Taken together, the Percolator rows invalidate the assumption, held
   throughout Revision 1, that selecting "Percolator 3.08.0" delivers the
   complete Comet -> Percolator -> Limelight workflow on every supported
   platform. It does not: 3.08 publishes no XML-capable build for Windows or
   macOS. **The newest release that does publish XML-capable binaries for all
   three tier-1 platforms is 3.07.1**, and it is the product's default for a
   Limelight-enabled run. See :ref:`spec-percolator-artefacts` and ``D-002``.

Executive Summary
=================

CometGUI shall be a cross-platform desktop application for configuring and
running a complete Comet -> Percolator proteomics workflow without requiring
the user to install Java, Comet, Percolator, PDV, the Limelight converter, or
any other command-line dependency manually.

The application shall be derived from the CasanovoGUI code base and shall
preserve the strongest parts of that application: a modern JavaFX desktop
shell, bundled Java runtime, managed tool installation, live process output,
PDV integration patterns, Limelight upload patterns, and cross-platform
packaging. The Casanovo-specific workflow layer shall be replaced by a general
scientific workflow/tool-adapter architecture suitable for Comet and
Percolator. Derivation is gated on ``D-001`` (CasanovoGUI licensing).

The primary difficulty in this project is not invoking Comet. It is creating a
safe, understandable, version-aware graphical editor for Comet's large and
interdependent parameter space -- 118 parameters in the verified current
release, several of them structured tuples rather than scalars. The application
shall therefore *not* expose ``comet.params`` as a flat collection of text
boxes. It shall maintain a typed, versioned Comet parameter model and present
parameters using progressive disclosure, domain-oriented groups, dedicated
controls for structured values, version-matched help, cross-parameter
validation, presets, search, reset/diff operations, and an Expert raw-parameter
view that round-trips without silently dropping unknown settings.

The second difficulty is **obtaining an XML-capable Percolator**. The product
uses the *latest compatible* Percolator, resolved from probed capability rather
than pinned in code, subject to two constraints the project has fixed: the
product does not build Percolator from source, and a version is only offered
where upstream publishes a binary for the platform. Percolator 3.09 removed
XML/XSD I/O and the Limelight converter hard-requires Percolator XML, so for a
Limelight-enabled run resolution returns **3.07.1** -- the newest release
publishing XML-capable binaries for Linux, macOS and Windows alike. Newer
versions remain fully selectable for rescoring and result viewing; they simply
cannot feed Limelight (:ref:`spec-percolator-artefacts`, ``D-002``).

Percolator support shall be capability-driven rather than version-number
driven, for versions 3.05 and newer, including user-registered local binaries.
The UI shall explain when Limelight conversion is unavailable for the selected
Percolator and shall offer an explicit rerun of the Percolator stage with a
compatible version, reusing the Comet PIN and without rerunning Comet.

Percolator result filtering in the GUI shall default to PSM q-value <= 0.01 and
peptide q-value <= 0.01. These are *result-view/export filters*, independent
from Percolator's ``--trainFDR`` and ``--testFDR`` learning options, which
default to their conventional 0.01 values and are shown under Advanced
Percolator settings. Changing a GUI result filter shall never rerun Percolator
or mutate the original Percolator result files.

Percolator learned model coefficients shall be captured and displayed in a
"Learned feature weights" view. The UI may use "parameter importances" as a
navigation label to match user expectations, but explanatory text shall make
clear that these are learned normalised SVM feature weights, not causal
importance values. The view shall show each cross-validation split, summary
statistics, ranking by mean absolute coefficient, coefficient sign, and
cross-split consistency.

Every workflow run shall produce a provenance record containing every tool
name/version, exact executable checksum, exact command argument array,
timestamps, exit status, all generated parameter files, and MD5 plus SHA-256
checksums for every input and output file. SHA-256 is the integrity/trust
mechanism; MD5 is recorded because it is an explicit product requirement and
because downstream proteomics repositories use it. Provenance shall be viewable
in the GUI and exportable as machine-readable JSON and a human-readable RST
report.

Testing is a first-class deliverable. Unit testing alone is insufficient. The
project shall include a GUI-driven end-to-end harness that starts from a clean
temporary user environment, drives the same controls that a user drives,
generates real Comet parameters from those controls, obtains real tool
binaries, executes real Comet and Percolator processes on real spectra and
FASTA fixtures, verifies real output, verifies q-value filtering and learned
weights, exercises PDV and Limelight conversion, independently recomputes
provenance hashes, closes and reopens the project, and exercises meaningful
failure cases. The actual packaged application shall be tested on supported
operating systems before release.

Normative Language
==================

The words **shall**, **must**, and **required** describe mandatory behaviour.
The word **should** describes behaviour expected unless there is a documented
technical reason to deviate. The word **may** describes optional behaviour.

.. _spec-requirement-ids:

Requirement Identifiers and Traceability
========================================

Rules that a phase must implement and a test must prove carry a stable
identifier.

``R-<AREA>-<nn>``
    A normative implementation rule. Areas: ``PLAT`` (platform), ``TOOL``
    (tool registry/installation), ``PROC`` (process execution), ``PARAM``
    (Comet parameters), ``CMT`` (Comet adapter), ``PERC`` (Percolator),
    ``DEC`` (target/decoy), ``RUN`` (workflow/run storage), ``RES``
    (results), ``PDV``, ``LL`` (Limelight), ``PROV`` (provenance), ``SEC``
    (security), ``TEST`` (testing), ``DOC`` (documentation).

``AC-<AREA>-<nn>``
    A release acceptance criterion (:ref:`spec-acceptance`).

``D-<nnn>``
    A decision the owner or the project team must make; recorded in
    ``DECISIONS.rst``. A ``D`` item that is still open blocks any exit gate
    that names it.

Requirements:

``R-DOC-01``
    Every ``R-`` rule shall have exactly one **owning** phase, named in that
    phase's ``:Delivers:`` field. A phase that implements part of a rule it
    does not own -- typically the UI half of a rule whose model half lives
    elsewhere -- shall name it under ``:Contributes to:``. Every phase document
    shall also list the ``AC-`` criteria it proves.

``R-DOC-02``
    Every ``AC-`` criterion shall name at least one automated test that proves
    it, or shall be explicitly marked as requiring human sign-off.

``R-DOC-03``
    A traceability report mapping ``R-``/``AC-`` identifiers to phases and to
    test names shall be generated from source annotations or a checked-in
    mapping file, and shall be built by documentation CI. An identifier with no
    implementing phase, or an ``AC-`` with no test and no human-sign-off mark,
    is a documentation build failure.

Project Goals
=============

#. Make a high-quality Comet + Percolator workflow accessible without manual
   command-line tool installation.
#. Preserve the scientific flexibility of Comet rather than hiding its useful
   parameters.
#. Make configuration safer than editing ``comet.params`` manually.
#. Support reproducible reruns with exact tool and parameter provenance.
#. Allow Percolator versions >= 3.05 to be selected and managed explicitly.
#. Make version-dependent capabilities visible instead of failing late.
#. Provide first-class PSM and peptide result exploration with independent
   q-value filtering.
#. Make Percolator learned SVM weights inspectable.
#. Provide PDV spectrum visualisation.
#. Convert compatible Comet + Percolator results to Limelight XML and upload
   them to Limelight.
#. Provide comprehensive, automated scientific and GUI testing.
#. Package the application so a user can install CometGUI and run it on a clean
   supported system without manually installing its scientific tools.
#. Document all user and developer-facing behaviour in RST for Sphinx and Read
   the Docs.

Non-Goals
=========

The first production release is not required to:

* Reimplement Comet scoring.
* Reimplement Percolator machine learning or FDR estimation.
* Perform protein inference or report protein-level FDR. Percolator's
  protein-level options shall not be exposed in release 1; protein-level
  analysis is Limelight's role downstream. This exclusion shall be stated in
  the UI where a user might expect protein results.
* Expose Comet spectral-library search (``spectral_library_name``,
  ``spectral_library_ms_level``) or PEFF search (``peff_format``, ``peff_obo``)
  as supported workflows in release 1. These parameters shall still be modelled
  by the schema, round-tripped, and editable in Expert mode, but they are
  untested paths and shall be labelled as such.
* Invent a new PSM visualisation engine when PDV already supports Comet pepXML.
* Modify Percolator output q-values in place.
* Pretend that newer Percolator versions support XML when they do not.
* Silently translate incompatible Percolator output into a made-up XML format.
* Reproduce all possible raw command-line flags as equal-priority controls on
  the primary screen.
* Guarantee byte-identical floating-point scores across every operating system
  and every tool version when upstream tools do not guarantee this.
* Automate an external GUI such as PDV with screen-coordinate clicking in
  production code.
* Convert vendor raw formats other than through Comet's own supported readers.

.. _spec-platforms:

Supported Platforms
===================

Revision 1 named release targets only inside the release-pipeline section. The
supported matrix is normative and appears here once.

.. list-table:: Supported platform matrix
   :header-rows: 1
   :widths: 20 16 16 48

   * - Platform
     - Tier
     - Packaging
     - Notes
   * - Linux x86-64
     - 1 -- fully supported
     - ``tar.gz`` + ``.deb``/AppImage as infrastructure permits
     - Reference platform for CI, real-tool tests and the canonical E2E.
   * - Windows x64
     - 1 -- fully supported
     - MSI/EXE via ``jpackage``
     - Only platform on which Comet reads Thermo RAW, and only with the
       ``CometWrapper.dll`` and ``ThermoFisher.*`` companion files installed
       beside the executable.
   * - macOS arm64
     - 1 -- fully supported
     - ``.dmg``/``.pkg`` via ``jpackage``
     - Native Comet ``aarch64`` build exists. The XML-capable Percolator
       artefact is x86-64 only, so that stage runs under Rosetta 2, which the
       application detects and explains (``D-004``).
   * - macOS x86-64
     - 2 -- best effort
     - ``.dmg``
     - Built and smoke-tested if CI runners permit; not release-blocking.
   * - Linux aarch64
     - 3 -- unsupported in release 1
     - not packaged
     - Comet publishes an ``aarch64`` build; Percolator does not. Local-binary
       registration only.

Requirements:

``R-PLAT-01``
    The application shall declare a minimum host baseline and shall verify it
    at startup: a 64-bit OS, and on Linux a glibc version sufficient for the
    tools the user selects.

``R-PLAT-02``
    Because managed tool binaries have their own, *higher*, system-library
    requirements than the JVM does, tool compatibility shall be established by
    executing the installed binary (:ref:`spec-runtime-probe`), never by
    assuming that a successful download implies a runnable tool.

``R-PLAT-03``
    A dynamic-loader failure (missing ``GLIBC_*``/``GLIBCXX_*`` symbol version,
    missing shared object, wrong architecture, macOS quarantine refusal) shall
    be detected and reported as a distinct, actionable diagnostic naming the
    host's version, the required version, and the available alternatives. It
    shall never be surfaced as an opaque non-zero exit.

``R-PLAT-04``
    On macOS, every file extracted or downloaded into the tool cache that will
    be executed shall have its ``com.apple.quarantine`` extended attribute
    cleared, or shall be launched by a mechanism not subject to quarantine
    refusal. This shall be covered by a macOS-only integration test; without
    it, managed tool execution fails on a clean Mac with a Gatekeeper dialog
    the application cannot dismiss.

``R-PLAT-05``
    Downloaded executables shall be made executable (POSIX permission bits) as
    part of the atomic install, since Comet and Percolator artefacts include
    bare executables and archive-preserved modes cannot be relied on.

Research-Derived Constraints and Release Assumptions
====================================================

These assumptions are dated (:ref:`spec-verified-facts`) and shall be
represented in version/capability metadata rather than scattered hard-coded
conditionals.

CasanovoGUI base
----------------

The existing CasanovoGUI application provides an appropriate JavaFX desktop
foundation, cross-platform packaging, a bundled Java runtime, managed
first-run installation of scientific software, live process output, PDV launch
support, and Limelight-related integration patterns.

However, ``Noble-Lab/CasanovoGUI`` still exposes **no licence** -- verified
2026-08-28, with the repository last pushed 2026-08-21. A public GitHub
repository is not a grant of redistribution rights.

``R-SEC-01``
    No CasanovoGUI source shall be copied into the CometGUI repository until
    ``D-001`` is resolved by an explicit licence added upstream or written
    permission from the copyright holders, recorded in ``DECISIONS.rst``. Until
    then, CasanovoGUI may be *read* for design guidance, and CometGUI code
    shall be written independently. This is a release gate and an early
    implementation gate, not a documentation nicety.

The base targets Java 23+ and JavaFX 25.x and uses AtlantaFX. The new project
should initially preserve that stack so the work focuses on the workflow and
parameter editor rather than a GUI-framework migration. The exact JDK and
JavaFX versions shall be pinned in the build and recorded in provenance.

Comet
-----

The default verified Comet version for the initial implementation shall be
``2026.02.2``. Tool metadata shall not assume this remains latest forever. New
Comet releases shall enter the managed registry only after automated
compatibility and regression tests pass.

Comet ships **standalone executables**, not archives, one per platform, plus
Windows-only companion DLLs for Thermo RAW reading. The installer must
therefore treat "bare executable plus companion files" as a first-class
artefact kind (:ref:`spec-tool-registry`).

Comet configuration is versioned, so the schema used by the GUI shall be tied
to the selected Comet version.

``R-PARAM-01``
    Schema discovery shall use ``comet -q``, not ``comet -p``. The verified
    difference is 118 parameters versus 96: ``-p`` omits
    ``variable_mod06``--``variable_mod15``, both ``mass_type_*`` parameters,
    ``num_results``, the PEFF parameters, the spectral-library parameters,
    ``pinfile_protein_delimiter``, ``print_expect_score``,
    ``print_ascorepro_score``, ``compoundmods_file`` and
    ``protein_modslist_file``. A GUI built from ``-p`` would silently offer 5
    variable-modification slots where Comet supports 15.

``R-PARAM-02``
    If a selected Comet binary does not support ``-q``, the schema provider
    shall fall back to ``-p`` and shall mark the resulting schema
    ``PARTIAL_DISCOVERY``; parameters known to curated metadata but absent from
    a partial dump shall not be reported as removed by drift detection.

.. _spec-percolator-artefacts:

Percolator versions and artefact availability
---------------------------------------------

The product shall use the **latest compatible** Percolator, not a pinned
version. This section establishes what "latest compatible" resolves to and why
it is not simply "the newest release".

Percolator 3.09 (2026-05-21) removed XML/XSD I/O, and the Limelight converter
hard-requires Percolator XML. XML has never been unconditional: it is the
compile-time option ``XML_SUPPORT``, default ``OFF``, pulling in Xerces-C and
XSD. Upstream's ``noxml`` artefacts are the default build; the ones named
without ``noxml`` are ``-DXML_SUPPORT=ON`` builds. In 3.09 the option and its
code are gone.

.. important::

   **``XML_SUPPORT`` gates the pin-XML reader, not the pout-XML writer.** This
   was established by execution in phase 00 and it corrects a premise this
   document carried through revision 5. Running the 3.07.1 Linux ``noxml``
   binary as ``percolator -X out.xml pin`` exits 0 and writes a
   ``percolator_out/15`` document that differs from the ``XML_SUPPORT=ON``
   twin's output in two lines, both inside ``<command_line>``; the Limelight
   converter consumed that document and produced a schema-valid Limelight file.
   What ``XML_SUPPORT=OFF`` actually removes is ``--xml-in``, the deprecated
   pin-XML *input* path -- and Comet writes tab-delimited PIN, so **the product
   never needs it**. Two consequences run through the rest of this
   specification. Artefact selection is no longer restricted to operating-system
   packages (see below). And **a capability probe may not be textual**: both
   twins print identical ``--help`` listing ``-X`` and ``-Z``, so the probe
   required by ``R-PERC-02`` must be functional.

Two project constraints then bound the search. **The product does not build
Percolator from source**, and **a version is offered only where upstream
publishes a binary for that platform**. So "latest compatible" means: the
newest release whose published artefacts include a build that can *write pout
XML* on every tier-1 platform.

That is **3.07.1** (``rel-3-07-01``, 2024-06-20) -- not because 3.08 lacks the
writer, but because 3.08 does not publish a usable artefact set: it ships no
Linux portable archive at all, both its Linux ``.deb`` payloads demand
``GLIBC_2.38``, and its only macOS artefact is **arm64 with a macOS 15.0
floor**, reaching fewer Macs than 3.07.1's x86-64 build under Rosetta 2. 3.09
genuinely cannot write pout XML -- verified by execution, ``-X`` rejected with
exit 1 -- so the ceiling for a Limelight-enabled run is 3.08.x and the choice
within it is 3.07.1.

.. list-table:: Published Percolator artefacts, and which can write pout XML
   :header-rows: 1
   :widths: 12 30 29 29

   * - Release
     - Linux x86-64
     - Windows x64
     - macOS
   * - ``rel-3-09``
     - ``.deb``, ``.rpm`` -- **no writer**
     - ``percolator.exe`` -- **no writer**
     - ``percolator-osx-portable.zip`` -- **no writer**, arm64, minos 15.0
   * - ``rel-3-08-01``
     - **no release published** (tag only)
     - **no release published**
     - **no release published**
   * - ``rel-3-08``
     - **no portable archive**; two ``.deb`` files, both ``GLIBC_2.38``
     - ``percolator-noxml-windows-portable.zip`` -- writer present
     - ``percolator-noxml-osx-portable.zip`` -- writer present, **arm64**,
       minos 15.0
   * - ``rel-3-07-01``
     - ``percolator-noxml-ubuntu-portable.zip`` -- writer present,
       ``GLIBC_2.34``, **executed**
     - ``percolator-noxml-windows-portable.zip`` -- writer present, PE32+
       x86-64
     - ``percolator-noxml-osx-portable.zip`` -- writer present, Mach-O
       **x86-64**, minos 12.7
   * - ``rel-3-06-05``
     - ``percolator-noxml-linux-portable.zip`` -- writer present,
       ``GLIBC_2.14`` (the lowest floor found anywhere)
     - ``percolator-noxml-windows-portable.zip`` -- writer present
     - ``percolator-noxml-osx-portable.zip`` -- writer present, x86-64

Every ``.deb``, ``.rpm``, ``.pkg`` and NSIS ``.exe`` in 3.05--3.08 also carries
the writer, in both twins. They are not needed for the writer; two of them are
needed for the XSDs, below.

Consequences that the implementation must respect:

* **The product obtains Percolator from the portable ``noxml`` archives**
  (``D-002`` option C, decided 2026-08-29). A portable zip needs no
  administrative rights, no installer execution and no payload extraction on
  any platform. Phase 05 therefore implements ``ZIP`` extraction and does
  **not** implement ``NSIS_PAYLOAD`` or ``PKG_PAYLOAD``. The ``.deb``, ``.pkg``
  and NSIS extractors written in phase 00 are retained as feasibility evidence
  and for the XSD problem below, not as product code.
* **No portable archive ships an XSD.** Every portable zip upstream publishes,
  3.06.5 through 3.09 and on all three platforms, contains exactly one member:
  the bare executable. ``R-TOOL-02`` requires the XSD companions to be
  installed with the binary, so they must come from the matching ``noxml``
  ``.deb`` or ``.pkg`` -- both of which do ship
  ``share/xml/percolator/xml-pout-1-5/percolator_out.xsd`` and
  ``xml-pin-1-3/percolator_in.xsd``. This is a second, small download per
  platform, using the extraction code phase 00 already proved; it is not a
  reason to reinstate package-payload installs. Vendoring the two XSDs in the
  repository instead is a redistribution question and would need an owner
  decision, which has not been taken.
* **The Windows portable zip is the bare executable and needs a Visual C++
  runtime.** ``percolator.exe`` imports ``MSVCP140.dll``,
  ``VCRUNTIME140.dll``, ``VCRUNTIME140_1.dll`` and ``VCOMP140.DLL``; the NSIS
  installer ships nine such DLLs beside it and the zip ships none. The product
  must satisfy this dependency explicitly -- the Windows registry entry
  declares the runtime as a companion requirement, the loadability probe
  (``R-PLAT-03``) must report a missing runtime as a *loader* failure with the
  named DLL rather than as "not capable", and the manifest must not offer the
  Windows artefact as installable until that path is settled. The 3.07.1 NSIS
  installer's ``percolator.exe`` is byte-identical to the portable zip's
  (sha256 ``b9d9bbe82bc4...f059f``, 707072 bytes), so extracting the DLLs from
  it remains available as the fallback.
* 3.07.1 is markedly *more* portable than 3.08.0, not less: its Linux binary's
  highest required symbol version is ``GLIBC_2.34`` in both the portable
  archive and the ``.deb``, so it runs on RHEL 9, Ubuntu 22.04 and Debian 12,
  where the 3.08.0 build's ``GLIBC_2.38`` requirement fails at the dynamic
  loader.
* The macOS 3.07.1 artefact is **x86-64 only**. On Apple silicon the Percolator
  stage runs under Rosetta 2, which the application must detect and explain
  (``D-004``). Moving to 3.08 does not avoid this: its macOS build is arm64
  with a macOS 15.0 floor, which trades a Rosetta dependency for a narrower
  hardware and OS reach.
* **Capability is probed, never read from this table.** No Windows or macOS
  Percolator binary has been executed anywhere in this project; every non-Linux
  row above is a byte-marker inference and the manifest must say so. The rows
  are a statement of what upstream publishes, not a grant of capability.
* 3.07.1 predates 3.08's change of default PEP regressor to I-splines and
  predates the fix for PEP values exceeding 1.0 (#394, fixed in 3.08.1 and
  3.09). These are version advisories to carry in the registry
  (``R-PERC-11``), not reasons to change the selection: the alternatives are
  a version that cannot emit XML at all, or a source build the project has
  ruled out.
* Newer Percolator versions are not excluded from the product. They remain
  selectable for rescoring, result viewing and learned weights; they are
  simply not eligible to feed Limelight.

``R-PERC-12``
    **The managed version set.** The manifest shall carry Percolator 3.07.1,
    3.09 and 3.06.5 (``D-003``, decided 2026-08-30): the computed default for a
    Limelight-enabled run, the current release for runs that do not need
    Limelight, and the lowest-glibc build for reach on older hosts. This names
    what the project *attempts* to offer. What it actually offers on a given
    machine is decided by ``R-PERC-01``'s artefact-plus-probe test, so a
    version may legitimately be absent on a platform -- 3.09 on Linux
    especially, which publishes no portable archive, whose ``.deb`` requires
    ``GLIBC_2.38`` and whose ``.rpm`` needs Boost libraries it does not ship.
    Absent is honest; a fabricated entry is not. Adding a version to this set
    is a manifest change plus a probe result, never a code change.

``R-PERC-01``
    The application shall not present a Percolator version/platform
    combination as a one-click managed install unless a verified artefact for
    that combination exists in the manifest and its post-install runtime probe
    has passed on that platform. The UI must not promise a downloadable build
    that does not exist or cannot run.

``R-PERC-02``
    **Latest compatible.** The default selected Percolator version shall be
    computed, never hard-coded. It is the highest version in the manifest that
    (a) has a published upstream artefact for the host platform -- the project
    does not build Percolator from source -- (b) passed its loadability probe,
    and (c) has a probed capability set satisfying every *enabled* downstream
    stage. A version number shall never appear in code as a default;
    ``3.07.1`` appears in this document only as the value that resolution
    returns today, for a run with Limelight enabled.

    **The capability set shall be established functionally.** The ``noxml`` and
    ``XML_SUPPORT=ON`` builds print identical ``--help``, both listing ``-X``
    and ``-Z``, so a text probe cannot discriminate and shall not be used. The
    ``XML_OUTPUT`` capability is proved by running the binary over a small
    synthetic PIN with ``-X`` and requiring the file to exist, to carry the
    ``percolator_out/15`` namespace and to contain the expected ``<psm>``
    count. The fixture shall be large enough that a capable binary does not
    abort on "median decoy score <= score at 1% FDR" -- 64 target and 64 decoy
    rows is proven sufficient, 8 and 8 is not, and an over-small fixture makes
    the probe report a false negative. A binary that fails to load is reported
    as a loader failure (``R-PLAT-03``), never as "not capable".

    The rule is evaluated against the enabled stages, so it has more than one
    answer at a time. With Limelight disabled, the newest verified Percolator
    wins even though it cannot emit XML; with Limelight enabled, the newest
    XML-capable one wins. Changing which downstream stages are enabled shall
    therefore re-evaluate the default and tell the user it changed.

``R-PERC-11``
    Each registry entry shall carry version advisories, shown at selection time
    and recorded in provenance. 3.07.1 shall carry at least: it predates the
    change of default PEP regressor to I-splines (3.08), and it predates the
    fix for PEP values exceeding 1.0 (#394). A user selecting it for the
    Limelight path shall be able to see what they are trading away.

``R-PERC-10``
    When resolution selects a version that is not the newest one the manifest
    knows about, the UI and the run's provenance shall record *why* -- naming
    the newer version and the capability it lacks. "Using 3.07.1 rather than
    3.09 because 3.09 cannot emit the XML the Limelight stage needs" is
    information the user needs at configuration time, and a reviewer needs a
    year later.

``R-PERC-03``
    When no XML-capable Percolator is available for the host platform, the
    Limelight stage shall be shown as unavailable with a specific explanation
    and the documented remedies (register a local XML-capable binary; run the
    conversion on a supported platform), rather than being silently absent or
    failing at conversion time.

``D-002`` is resolved in two steps. The owner ruled out source builds and
required published binaries on all three tier-1 platforms (2026-08-29), which
selects 3.07.1; the owner then took **option C** (2026-08-29), acting on phase
00's finding that the pout-XML writer is present in every 3.05--3.08 artefact,
which selects the *portable* form of those binaries. What remains is
engineering, recorded here so no phase rediscovers it:

#. Install Percolator from the platform's ``noxml`` **portable zip**. Do not
   run installers and do not extract package payloads for the binary.
#. Obtain the two XSD companion files from the matching ``noxml`` ``.deb``
   (Linux) or ``.pkg`` (macOS) -- no portable archive ships them -- and install
   them atomically with the binary (``R-TOOL-02``).
#. Satisfy the Windows Visual C++ runtime dependency the portable zip does not
   carry, and report its absence as a loader failure naming the DLL.
#. Detect and explain Rosetta 2 on Apple silicon.
#. Carry 3.07.1's advisories in the registry.
#. Probe capability functionally on the host after install. Never infer it from
   an artefact name, a file size, a byte marker or ``--help`` text.

If a future Percolator release restores XML output, or the Limelight converter
gains a tab-delimited input path, resolution picks the newer version up
automatically once the manifest records it -- which is the point of computing
the default rather than pinning it.

Whichever is chosen, the capability model, the manifest and the UI messaging
described in this specification are unchanged; only the manifest contents and
the release pipeline change. Support for versions 3.05 and newer remains
capability-driven, and older point releases may carry advisories -- for
example 3.06 behaviour around peptide protein IDs -- which is a reason to test
version-specific outputs rather than treating all 3.x releases as
interchangeable.

Limelight converter
-------------------

The required ``limelight-import-comet-percolator`` converter (Apache-2.0,
release ``v2.8.1``, asset ``cometPercolator2LimelightXML.jar``) consumes the
Comet parameter file, Comet pepXML, **Percolator XML**, optionally the FASTA,
and one optional q-value override. Its verified interface is:

.. list-table:: Converter arguments, verified 2026-08-29
   :header-rows: 1
   :widths: 34 66

   * - Argument
     - Meaning
   * - ``-c, --comet-params``
     - Path to the Comet parameter file. Required.
   * - ``-p, --percolator-file``
     - Path to the Percolator output **XML** file. Required.
   * - ``-o, --out-file``
     - Path for the generated Limelight XML. Required.
   * - ``-d, --pepxml-directory``
     - Directory holding the pepXML files. Defaults to the Percolator file's
       own directory, which the run layout must satisfy or override.
   * - ``-f, --fasta-file``
     - FASTA used in the experiment; falls back to the Comet params file.
   * - ``-q, --q-value``
     - The single converter q-value override.
   * - ``--import-decoys``
     - Import decoys. **Requires that Percolator was run with ``-Z``.**
   * - ``--independent-decoy-prefix``
     - Treat hits to proteins with this prefix as independent decoys.
   * - ``--open-mod``
     - Treat mass diffs as unlocalised modification masses.
   * - ``-v, --verbose``
     - Full stack traces on error.

``R-LL-05``
    ``--import-decoys`` is only valid if the Percolator execution that produced
    the XML was run with decoy output enabled -- and Percolator's own help
    states that ``-Z, --decoy-xml-output`` is "Only available if -X is set", so
    the chain is: Limelight decoy import requires ``-Z``, which requires
    ``-X``. The application shall either
    enable Percolator's decoy output whenever Limelight decoy import is
    selected, or shall disable the decoy-import control with that explanation.
    It shall not pass ``--import-decoys`` against a Percolator run that had no
    decoy output, which fails late and obscurely.

Because the converter has one q-value override, the GUI shall not pretend that
its independent PSM and peptide display filters map one-to-one onto Limelight
conversion. Limelight conversion shall have its own explicitly labelled
**Limelight q-value cutoff**, default 0.01.

The converter is a JAR and runs on the bundled Java runtime, so it has no
native-artefact problem -- it is the *input* it demands that constrains the
product. The converter has had no substantive change since before Percolator
removed XML, and offers no tab-delimited path, so there is no version of it
that unblocks 3.09. Phase 00 shall re-run the pinned JAR's help output and
confirm the table above rather than trusting it.

PDV
---

PDV supports Comet pepXML with MGF, mzML and mzXML spectrum files and shall be
the supported annotated-spectrum viewer. The initial managed version shall be
**PDV 2.7.0** (2026-08-14); Revision 1 named 2.6.0, which was superseded
before implementation began. The download is ~99 MB, which is large enough that
installation must be cancellable, resumable-or-restartable, and must not block
the first search.

PDV's documented external control server is specific to its ``denovo-gui``
mode as used by CasanovoGUI. A corresponding database-search control mode is
not documented. CometGUI shall therefore distinguish two integration levels:

baseline
    Managed PDV installation plus reliable opening/batch visualisation of Comet
    pepXML and source spectra using documented PDV database-search support.

enhanced
    Exact row-to-spectrum selection from CometGUI through a generalised PDV
    database-search launch/control mode, preferably contributed upstream.

The product shall not rely on brittle screen-coordinate automation of PDV.
``D-005`` selects baseline-only or baseline-plus-enhanced for release 1.

UX Design Methodology
=====================

The Comet parameter editor shall be designed using explicit human-computer
interaction methods rather than by mechanically exposing the underlying text
file.

User classes
------------

At minimum, design and usability work shall consider these user classes.

Routine proteomics user
    Wants a correct search with familiar parameters, common modifications,
    tryptic digestion, instrument-appropriate mass tolerances, and clear
    results. This user should not need to understand every Comet internal
    option.

Advanced search-method developer
    Intentionally changes less common fragmentation, indexing, enzyme,
    modification, spectral-processing, decoy and output settings. This user
    requires access to the complete parameter space and exact serialisation.

Workflow administrator / reproducibility reviewer
    Primarily cares about versions, tool installation, provenance, exact
    commands, checksums, logs, compatibility, and the ability to reproduce a
    prior run.

Primary user tasks
------------------

The UI shall be optimised around actual tasks, including: choose spectra and a
sequence database; start from an instrument/search preset; define precursor and
fragment tolerances; define digestion; define static and variable
modifications; configure decoy behaviour; review advanced Comet settings when
needed; choose a Percolator version; run the complete workflow; diagnose
failure from a specific stage; filter PSMs and peptides by q-value; inspect
learned Percolator feature weights; inspect a PSM in PDV; convert and upload a
result to Limelight; inspect and export exact provenance; and reopen a
historical run and know exactly what happened.

Design principles
-----------------

Progressive disclosure
    Common, high-impact parameters are shown first. Advanced and Expert
    settings remain available without overwhelming the default workflow.

Recognition rather than recall
    Users see units, valid ranges, descriptions, current defaults, preset
    origin and common choices. They do not need to memorise Comet's numeric
    enum encodings.

Error prevention
    Invalid combinations are blocked before a run where possible. The UI
    explains why a value is invalid and points to the responsible field.

User control and reversibility
    Every parameter category and individual parameter can be reset. Applying a
    preset shows a diff before changing the configuration. Raw results are not
    destructively changed by result filters.

Visibility of system state
    Tool downloads, validation, hashing, Comet, Percolator, conversion, upload
    and finalisation each have explicit states and progress indicators.

Consistency
    Similar parameter types use similar controls. Units and serialised values
    are represented consistently across categories.

Version transparency
    When behaviour is unavailable because of a selected tool version, the UI
    says so at configuration time rather than allowing a late cryptic process
    failure.

Inline help
    Each parameter has concise help and an action to open version-matched
    documentation. Help shall describe the scientific meaning, not merely
    restate the parameter name.

Accessibility
    Every interactive control requires an accessible label; validation errors
    must be conveyed in text, not by colour alone; keyboard navigation and
    visible focus shall be tested; custom JavaFX controls shall expose
    appropriate accessibility attributes.

UX validation activities
------------------------

Before release, the parameter UI shall undergo domain/task analysis with at
least one experienced Comet user; a heuristic evaluation against standard
usability heuristics; a cognitive walkthrough of the primary workflow; at least
one usability test with a routine proteomics user who has not implemented the
GUI; a usability test with an advanced Comet user using imported or custom
parameters; and a keyboard-only accessibility review. Issues found in these
sessions shall be tracked like software defects.

.. note::

   These six activities require human participants and cannot be discharged by
   an implementing agent. They are recorded as ``AC-UX-01``--``AC-UX-06`` and
   are owner-scheduled; see ``phases/PHASE-16`` and ``STATUS.rst``.

Information Architecture
========================

The primary application window should use a stable left navigation rather than
proliferating modal dialogs. Recommended primary sections:

Run
    Inputs, workflow summary, selected tool versions, high-level parameter
    summary, validation, and Run/Cancel controls.

Comet Parameters
    Typed parameter editor with Essentials, Advanced and Expert modes.

Percolator
    Version selection, result-filter defaults, advanced learning options, and
    version capability/advisory information.

Results
    Run summary, PSM table, peptide table, learned feature weights, export.

Visualisation
    PDV status, selected spectrum/PSM context, and Open in PDV actions.

Limelight
    Converter compatibility, converter parameters, generated Limelight XML,
    upload configuration, upload log/status.

Provenance
    Tool versions/checksums, file hashes, exact commands, parameter files, run
    timeline, environment, warnings, export.

Console
    A persistent or collapsible live console that can filter messages by
    workflow stage.

Tool Manager and application Settings may be secondary navigation or dialogs.

The primary Run screen should present the workflow as a stage stepper::

    Inputs -> Validate -> Comet -> Percolator -> Results

with optional downstream stages visibly attached::

    Results -> PDV
    Results -> Limelight XML -> Limelight Upload

Software Architecture
=====================

Architectural style
-------------------

The project shall use a layered ports/adapters architecture with a UI-facing
MVVM or Presenter boundary. JavaFX controllers must not contain scientific
process logic, file hashing logic, download logic or output parsing logic.

The workflow engine and domain logic shall be usable from tests without
launching JavaFX. The JavaFX layer shall translate user actions into domain
commands and observe state.

Recommended package structure::

    org.cometgui.app          bootstrap/ config/
    org.cometgui.domain       project/ run/ tools/ params/ results/ provenance/
    org.cometgui.workflow     engine/ steps/ state/
    org.cometgui.tools        api/ comet/ percolator/ pdv/ limelight/ process/
    org.cometgui.install      registry/ download/ verify/ archive/ probe/
    org.cometgui.params.comet schema/ parser/ writer/ validation/ presets/ migration/
    org.cometgui.params.percolator  schema/ validation/
    org.cometgui.results      parser/ filtering/ export/
    org.cometgui.provenance   hashing/ manifest/ events/ report/
    org.cometgui.ui           view/ viewmodel/ controls/ dialogs/

Key interfaces
--------------

Exact Java names may change, but these responsibilities shall exist.

.. code-block:: java

    public interface ToolAdapter {
        ToolIdentity identify(Path executable) throws ToolException;
        Set<ToolCapability> probeCapabilities(Path executable) throws ToolException;
        ToolCommand buildCommand(ToolExecutionRequest request);
        ToolExecutionResult validateOutputs(ToolExecutionContext context);
    }

    public interface ProcessRunner {
        RunningProcess start(ToolCommand command, ProcessListener listener)
            throws IOException;
    }

    public interface HashService {
        FileHashes hash(Path path) throws IOException;
    }

    public interface ProvenanceRecorder {
        void record(ProvenanceEvent event);
        ProvenanceManifest finalizeManifest(RunOutcome outcome);
    }

    public interface CometParameterSchemaProvider {
        CometParameterSchema schemaFor(CometToolIdentity comet);
    }

``R-PROC-01``
    The clock, environment reader, process runner, downloader, filesystem
    abstraction where useful, run-ID source and hash service shall be
    injectable. Deterministic tests depend on this.

Workflow state model
--------------------

Each workflow step shall use explicit states: ``NOT_STARTED``, ``VALIDATING``,
``READY``, ``RUNNING``, ``SUCCEEDED``, ``FAILED``, ``CANCEL_REQUESTED``,
``CANCELLED``, ``SKIPPED``. The overall run state shall be derived from step
states. State changes shall be observable by the UI and written to provenance.

Process execution
-----------------

``R-PROC-02``
    Processes shall be started using argument arrays, never by constructing a
    single shell command string. An ArchUnit rule shall confine
    ``ProcessBuilder`` construction to the process service.

The process service shall stream stdout and stderr independently; timestamp
emitted lines and events; never block the JavaFX application thread; preserve
full logs to disk; expose exit code and duration; support cancellation;
attempt to terminate descendant processes when cancelling; time out only where
a stage-specific timeout is explicitly configured; and redact secrets before
provenance or log display.

``R-PROC-03``
    Log capture shall be bounded in memory. Process output shall be written to
    the run's log files as it arrives, and the in-memory console buffer shall
    be capped with a documented retention policy, so that a tool emitting
    hundreds of megabytes of output cannot exhaust the heap.

``R-PROC-04``
    Every launched process shall be started with an explicit working directory
    inside the run directory, and with an explicitly constructed environment.
    Inherited environment variables that are known to change tool behaviour
    shall be recorded in provenance.

.. _spec-tool-registry:

Tool Installation and Version Management
========================================

Zero-manual-install requirement
-------------------------------

A supported user workflow must begin from a clean machine on which only the
CometGUI installer has been installed or extracted. The application shall
bundle its own Java runtime in native release packages.

The application shall install scientific tools into an application-private
cache, for example::

    ~/.comet-gui/
        tools/
            comet/2026.02.2/{linux-x64,windows-x64,macos-arm64}/
            percolator/{3.07.1,3.09.0}/<platform>/
            pdv/2.7.0/
            limelight-converter/2.8.1/
        cache/downloads/
        cache/hashes/

Exact platform names may differ. Tool installs shall not require root or
administrative privileges after the application itself is installed.

Managed artefact manifest
-------------------------

Tool locations shall come from a versioned, release-bundled (and ideally
signed) manifest, not ad hoc URL construction spread through the code.

Each managed artefact record shall contain at least: tool name; upstream
version; release tag or commit when known; operating system; architecture;
download URL; **artefact kind**; expected executable or JAR path; expected
SHA-256; expected MD5 when available; licence metadata; required companion
files; known capabilities; known advisories; minimum host requirements; and
minimum compatible CometGUI version.

``R-TOOL-01``
    Artefact kind shall be an explicit enumeration covering at least
    ``BARE_EXECUTABLE`` (Comet), ``ZIP`` (Percolator, all three platforms),
    ``TAR_GZ``, ``JAR`` (PDV, Limelight converter) and ``DEB_PAYLOAD``
    (``ar`` + ``data.tar.*``, used for the Percolator XSD companions on Linux)
    and ``PKG_PAYLOAD`` (``xar!`` + gzip + ``070707`` cpio, the same on macOS).
    The extractor for each kind shall be selected from this field, never
    inferred from the URL suffix alone.

    Since ``D-002`` option C the Percolator **binary** comes from a portable
    zip on every tier-1 platform, so ``NSIS_PAYLOAD`` is **not required** and
    shall not be implemented. ``DEB_PAYLOAD`` and ``PKG_PAYLOAD`` survive only
    to fetch the XSD companion files, which no portable archive ships.

``R-TOOL-02``
    Companion files shall be modelled as part of the artefact record and
    installed atomically with the primary executable. Comet's Thermo RAW
    support requires ``CometWrapper.dll``, ``ThermoFisher.CommonCore.Data.dll``
    and ``ThermoFisher.CommonCore.RawFileReader.dll`` beside
    ``comet.win64.exe``; a Comet install missing them shall not advertise the
    ``THERMO_RAW_WINDOWS`` capability. Percolator's XML builds ship
    ``share/xml/percolator/xml-pout-1-5/percolator_out.xsd`` and
    ``xml-pin-1-3/percolator_in.xsd`` -- but **the portable archive the product
    installs the binary from ships neither**, so they are a separately sourced
    companion pair taken from the matching ``noxml`` ``.deb`` or ``.pkg``. An
    install missing them shall not advertise ``XML_OUTPUT`` until a probe
    proves XML output works without them; phase 00 established by execution
    that it does, so the XSDs are a provenance and validation asset rather than
    a runtime prerequisite, and that distinction shall be recorded in the
    registry rather than left implicit. Note also that the shipped
    ``percolator_out.xsd`` fixes ``majorVersion`` at ``2`` while the 3.07.1
    binary writes ``3``: it cannot be used unmodified as a validation gate.

``R-TOOL-03``
    Manifest records shall carry ``minimumHostRequirements`` (for example a
    minimum glibc version) so the UI can explain in advance that an artefact
    will not run here, instead of only discovering it at probe time.

``R-SEC-02``
    SHA-256 verification is mandatory before an executable is launched. MD5
    shall also be computed and recorded for provenance but shall never be the
    security trust mechanism.

Installation shall be atomic:

#. Download to a temporary file.
#. Verify expected SHA-256.
#. Extract into a temporary directory with path-traversal and unsafe-symlink
   protections, using the extractor for the declared artefact kind.
#. Verify the expected executable and companion layout.
#. Apply platform fix-ups: executable permission bits, macOS quarantine
   removal.
#. Probe version, runtime loadability and capabilities.
#. Rename or move atomically into the final cache directory.
#. Record installation metadata.

``R-TOOL-04``
    Interrupted installations shall be safely discarded or resumed and shall
    never leave a tool that appears installed but is incomplete. A tool
    directory shall be considered installed only when a completion marker
    written last is present and its recorded checksums match.

``R-TOOL-05``
    Concurrent installation of the same artefact by two CometGUI processes
    shall be serialised by a lock file or shall be made idempotent; a partially
    written cache entry shall never be observed as complete.

Percolator installation modes
-----------------------------

The Tool Manager shall show all supported verified Percolator versions >= 3.05
that are available **and runnable** for the user's platform. The user shall
have three installation modes:

Managed verified version
    Downloaded and verified by CometGUI from the curated artefact manifest.

Registered local binary
    The user selects a local executable. CometGUI probes it, verifies it is
    Percolator >= 3.05, computes checksums, probes capabilities, and records it
    as unmanaged/local. This is the documented remedy wherever a managed
    XML-capable build is unavailable.

Developer/custom artefact
    Optional expert mode. A custom URL or local archive may be registered only
    if an expected SHA-256 is supplied. Clearly marked unsupported/unverified
    unless the compatibility suite has been run.

.. _spec-runtime-probe:

Capability and runtime probing
------------------------------

Version strings alone shall not determine behaviour. Each registered tool shall
have a capability set. Example Percolator capabilities: ``XML_OUTPUT``,
``PSM_TSV_OUTPUT``, ``PEPTIDE_TSV_OUTPUT``, ``DECOY_OUTPUT``,
``WEIGHTS_OUTPUT``, ``THREAD_OPTION``, ``SEED_OPTION``. Example Comet
capabilities: ``PEPXML_OUTPUT``, ``PIN_OUTPUT``, ``COMPLETE_PARAMS_QUERY``
(``-q``), ``THERMO_RAW_WINDOWS``, ``FRAGMENT_ION_INDEX`` (``-i``),
``PEPTIDE_INDEX`` (``-j``), ``SCAN_RANGE`` (``-F``/``-L``),
``OUTPUT_BASENAME`` (``-N``).

``R-TOOL-06``
    Probing shall proceed in three ordered stages, each with a distinct
    failure state: **loadability** (the binary starts at all -- distinguishing
    loader/link/architecture/quarantine failures per ``R-PLAT-03``);
    **identity** (a parsed version string); **capability** (help-text
    inspection and, where cheap and unambiguous, a small smoke run). A tool
    that fails loadability shall never be offered for selection.

``R-TOOL-07``
    Capability claims taken from the manifest shall be confirmed by probe on
    first install and shall be re-confirmed if the recorded executable checksum
    changes. Where manifest and probe disagree, the probe wins and the
    discrepancy is recorded as a warning in provenance.

``R-TOOL-08``
    Unknown local binaries shall be probed conservatively: absent positive
    evidence of a capability, the capability is absent.

Version pinning
---------------

``R-TOOL-09``
    Existing projects shall pin exact tool versions and artefact checksums.
    Application updates shall never silently change the scientific tools used
    when rerunning a historical run. If a pinned artefact is missing from the
    cache at rerun time, the application shall offer to reinstall exactly that
    artefact, and shall refuse to substitute a different version without an
    explicit user action recorded in provenance.

Comet Parameter Model and Editor
================================

Core rule
---------

``R-PARAM-03``
    The Comet parameter editor shall be schema-driven and typed. The UI shall
    not be the source of truth. The parameter model shall be serialisable to
    canonical ``comet.params`` text, parsable from existing parameter files,
    diffable and version-aware.

Parameter definition model
--------------------------

A parameter definition should contain fields equivalent to:

.. code-block:: java

    public record ParameterDefinition<T>(
        String name,
        String displayName,
        ParameterCategory category,
        ParameterValueType valueType,
        T defaultValue,
        Optional<T> minValue,
        Optional<T> maxValue,
        List<Choice<T>> choices,
        VisibilityLevel visibility,
        String shortHelp,
        String detailedHelpRef,
        VersionRange supportedVersions,
        SerializationRule serialization,
        List<ValidatorId> validators
    ) {}

A parameter value in a project or run shall also remember its origin -- Comet
default, application preset, user changed, imported from file, or
workflow-enforced -- and the UI shall be able to show it.

Structured parameter kinds
--------------------------

The verified 2026.02.2 parameter file is not a flat list of scalars. The schema
shall model at least these structural kinds, because each needs its own parser,
writer, validator and control:

Scalar
    ``allowed_missed_cleavage = 2``, with an inline ``#`` comment after the
    value.

Signed tolerance pair
    ``peptide_mass_tolerance_lower = -20.0`` with
    ``peptide_mass_tolerance_upper = 20.0``. The lower bound is **normally
    negative**.

    ``R-PARAM-04``
        The generic "lower <= upper" range validator shall not be applied to
        the precursor tolerance pair. Its rule is ``lower <= 0 <= upper`` with
        a warning, not an error, if the user chooses an asymmetric or
        same-signed window deliberately.

Two-value range on one line
    ``peptide_length_range = 5 50``.

Variable-modification tuple
    ``variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0`` -- fifteen slots
    (``variable_mod01``--``variable_mod15``) in 2026.02.2, whose field count
    and meaning are version-dependent.

Enzyme table
    A trailing ``[COMET_ENZYME_INFO]`` block of numbered rows, verified as
    ``number. name sense cut-residues no-cut-residues``, referenced by
    ``search_enzyme_number``, ``search_enzyme2_number`` and
    ``sample_enzyme_number``.

Bit-like family of booleans
    ``use_A_ions`` .. ``use_Z1_ions``, presented as an ion-series control.

Empty-valued parameter
    ``pinfile_protein_delimiter =`` and ``peff_obo =`` have empty values by
    default; empty is meaningful and shall round-trip as empty, not as absent.

Free-path parameter
    ``database_name``, ``spectral_library_name``, ``compoundmods_file``,
    ``protein_modslist_file``.

``R-PARAM-05``
    The parser shall preserve the file's comment structure: the leading
    ``# comet_version <version>`` marker line, block comments between
    parameters, and inline trailing comments. The canonical writer shall
    regenerate the version marker for the *selected* Comet version and shall
    emit curated inline comments from the schema; imported comments that
    cannot be re-derived shall be preserved for unknown parameters.

``R-PARAM-06``
    The parsed ``# comet_version`` marker shall be compared with the selected
    binary's reported version on import; a mismatch shall produce a visible
    compatibility warning naming both versions.

Schema discovery and drift detection
------------------------------------

For supported Comet binaries that expose complete parameter generation, the
build and application shall use that output to verify supported names and
defaults (``R-PARAM-01``). Curated schema metadata is still required: the
binary output alone does not provide scientific descriptions, value semantics,
relationships, enum labels, recommended groupings or structured-control
behaviour.

CI shall contain a schema-drift test that installs each Comet version in the
release matrix, asks each binary for its complete parameter set, parses names
and generated defaults, compares them with the checked-in schema metadata, and
fails if a supported binary introduces a parameter with no metadata (unless
explicitly allow-listed as hidden/internal) or if the GUI claims a parameter
that the verified binary no longer recognises.

``R-PARAM-07``
    Imported unknown parameters shall never be silently discarded. They shall
    be preserved in Expert mode with a warning and round-tripped on write
    unless the user explicitly removes them.

Parameter editor levels
-----------------------

Essentials
~~~~~~~~~~

Essentials shall contain the common workflow-defining controls: spectrum
inputs; FASTA database; search/acquisition preset; precursor mass tolerance
lower/upper and units; precursor tolerance type and isotope handling; fragment
mass tolerance (``fragment_bin_tol``/``fragment_bin_offset``, presented in
instrument terms); search enzyme; number of enzymatic termini; allowed missed
cleavages; static modifications; variable modifications; target/decoy strategy
and decoy prefix; thread count or automatic CPU selection; and a concise output
summary showing that pepXML and PIN are required.

Advanced
~~~~~~~~

Advanced mode shall group parameters by scientific concept rather than file
order. At minimum: Database and PEFF; CPU and execution; Precursor mass and
isotope handling; Digestion and enzymes (including the second enzyme);
Fragment-ion scoring; Fragment-ion and peptide-index search options;
Spectrum/scan/charge filters; Spectral pre-processing; Search ranges and
peptide constraints; Output options; MS1/real-time-search options where
supported; Static modifications; Variable modifications;
Miscellaneous/version-specific options.

Expert
~~~~~~

Expert mode shall provide the canonical raw ``comet.params`` text; syntax
highlighting; line-level validation diagnostics where possible; a diff versus
the selected preset or defaults; a diff versus the last saved or run
configuration; the list of unknown imported parameters; a round-trip action
from raw text into the typed model; and explicit confirmation before a raw edit
changes the typed configuration.

``R-PARAM-08``
    The raw editor must not become a second unsynchronised source of truth.
    Apply must parse and validate into the typed model, and a failed parse must
    leave the typed model untouched.

Global parameter search
-----------------------

The parameter editor shall include a search field matching parameter name,
display name, help text, category, and common aliases such as "precursor
tolerance", "missed cleavage" or "oxidation". Additional filters should
include: Modified only; Errors only; Warnings only; Expert parameters;
Unsupported/imported parameters.

Typed control requirements
--------------------------

Boolean parameters
    Check boxes or toggles. The UI may show the serialised 0/1 value in help
    but shall not require users to type it.

Enums
    Descriptive combo boxes or radio groups. The serialised integer or token
    shall be shown in advanced help -- for example ``isotope_error`` values
    ``0``--``5`` map to distinct C13 offset sets, and ``num_enzyme_termini``
    uses ``1``, ``2``, ``8`` and ``9``, which no user should be expected to
    recall.

Numeric parameters
    Validated numeric fields or spinners appropriate to range and precision.
    Scientific notation shall be supported where Comet supports it.

Ranges
    Paired controls with one semantic label; validate lower <= upper except
    where ``R-PARAM-04`` applies.

Mass tolerances
    Compound controls containing value or values, unit and tolerance
    semantics.

File parameters
    A path field plus chooser, with existence/readability checks; do not
    truncate the full path in accessible text or tooltips.

Ion families
    Named checkboxes for the A/B/C/X/Y/Z and Z+1 series and neutral-loss
    behaviour instead of numeric flags.

Static modifications
    A residue/terminus-oriented table with modification mass, name where
    user-supplied, and reset/default state.

Enzyme definitions
    An enzyme selector for known entries, plus a dedicated editor for custom
    enzyme definitions, plus a second-enzyme selector. The selected enzyme
    numbers and the serialised ``[COMET_ENZYME_INFO]`` table must stay
    consistent; the writer shall never emit an enzyme number absent from the
    table it writes.

Variable modification editor
----------------------------

Variable modifications are sufficiently structured that they require a
purpose-built editor. A free-form text field is not acceptable as the primary
control.

``R-PARAM-09``
    The editor shall present all variable-modification slots supported by the
    selected version -- fifteen in 2026.02.2 -- and shall model every field of
    the version's tuple syntax: mass delta; residue or terminus selector token;
    binary/group behaviour; maximum (and where applicable minimum)
    modification count; distance from a terminus; terminus type;
    required/exclusive semantics; and optional neutral-loss values. Field
    count and order shall come from the version schema, not from a hard-coded
    format string.

The UI should display a human-readable summary such as::

    Oxidation: +15.994915 on M; max 3 per peptide; optional

rather than forcing users to decode ``15.9949 M 0 3 -1 0 0 0.0``.

Required features: add; remove; reorder or assign slot where order matters;
common modification presets; residue multi-select; N-/C-terminal choices;
explicit max/min count controls; required/exclusive behaviour with explanation;
neutral-loss editor; validation of illegal residue/terminus/count combinations;
"show serialised value" for expert inspection; and round-trip tests for every
supported tuple form.

``R-PARAM-10``
    ``max_variable_mods_in_peptide`` and ``require_variable_mod`` shall be
    presented next to the slot editor, because they change the meaning of every
    slot, and shall be cross-validated against the configured slots.

Presets
-------

Presets shall be versioned configuration deltas, not opaque replacement files.
Initial presets should include Comet's conventional instrument-resolution
patterns -- low-low, high-low, high-high -- as appropriate to the selected
version, plus a minimal set of clearly named project presets.

Applying a preset shall show a reviewable diff::

    Parameter                    Current        Preset
    ----------------------------------------------------
    fragment_bin_tol             0.02           1.0005
    mass_type_fragment           monoisotopic   monoisotopic

The user can apply all or selected changes. User-defined presets shall record
the Comet version and schema they were created against; applying them to a
different version shall run a compatibility check.

Workflow-enforced Comet outputs
-------------------------------

``R-CMT-01``
    The workflow requires Comet artefacts consumed by downstream stages, so the
    application shall force ``output_pepxmlfile = 1`` (for PDV and Limelight)
    and ``output_percolatorfile = 1`` (the PIN for Percolator). The verified
    default for ``output_percolatorfile`` is ``0``, so this is a real change
    the application makes on the user's behalf and must show as such.

These fields shall appear in the output section as locked/on with text such as
"Required by CometGUI workflow". Exact option names shall come from the
selected version's schema. Users may enable additional Comet outputs but
cannot disable artefacts required by an enabled downstream stage.

.. _spec-decoy:

Target/decoy strategy
---------------------

The interaction between Comet's decoy generation, the FASTA contents,
Percolator's expectations and the Limelight converter's decoy handling is the
single most likely cause of a run that "succeeds" and is scientifically
meaningless. Revision 1 mentioned it only as one validation bullet; it is
specified here.

Verified defaults: ``decoy_search = 0`` (no internal decoys), ``decoy_prefix =
DECOY_``. Comet's ``decoy_search`` accepts ``0`` (none), ``1`` (internal decoys
concatenated into the same output) and ``2`` (internal decoys reported
separately).

``R-DEC-01``
    The project shall model the decoy source explicitly as one of
    ``FASTA_CONTAINS_DECOYS``, ``COMET_INTERNAL_CONCATENATED``
    (``decoy_search = 1``) or ``COMET_INTERNAL_SEPARATE``
    (``decoy_search = 2``), and shall present it as a single Essentials control
    rather than as a bare numeric parameter.

``R-DEC-02``
    Before Comet runs, the application shall detect whether the selected FASTA
    already contains decoy entries by scanning accessions for the configured
    decoy prefix, and shall report the count. Both "no decoys anywhere" and
    "decoys in the FASTA *and* ``decoy_search != 0``" shall block the run with
    an explanatory error, because the first yields a Percolator run with no
    negative examples and the second double-counts decoys.

``R-DEC-03``
    The decoy prefix shall be a single project-level value that drives Comet's
    ``decoy_prefix``, the Percolator decoy-pattern option where the selected
    version supports it, and the Limelight converter's decoy prefix. Where a
    stage's prefix is deliberately overridden, the override shall be explicit
    in the UI and recorded in provenance.

``R-DEC-04``
    After Comet runs and before Percolator starts, the PIN shall be checked to
    contain both target and decoy rows in the quantity the configured mode
    requires; a PIN with zero decoy rows shall fail the stage with a specific
    message naming the decoy configuration, not a generic Percolator error.

Comet validation
----------------

Validation shall occur per-field and across fields. At minimum: database exists
and is readable; spectra exist and use a supported format (mzML, mzXML, mgf,
ms2/cms2/bms2, and Thermo RAW only on Windows with the companion DLLs
present); precursor tolerance values and units are valid; numeric ranges are
ordered (subject to ``R-PARAM-04``); peptide-length ranges are valid; the
selected enzyme numbers exist in the serialised enzyme table; variable
modification tuples are internally valid; modification counts are consistent
with version limits; ``output_pepxmlfile`` and ``output_percolatorfile`` are
enabled; selected index and search options are compatible; decoy configuration
satisfies :ref:`spec-decoy`; output paths are writable; imported unknown
parameters are surfaced; and parameters unavailable in the selected Comet
version are blocked rather than ignored.

Errors shall be attached to the responsible field and category, summarised at
the top of the editor, and reachable by keyboard.

Canonical serialisation
-----------------------

The Comet parameter writer shall be deterministic for a given model: stable
ordering; stable numeric formatting; stable newline convention within a
platform-independent canonical artefact; an explicit generated header
containing the CometGUI version and target Comet version; and no hidden
mutation at process-launch time.

``R-PARAM-11``
    All numeric formatting and parsing in serialisation shall use
    ``Locale.ROOT``. A JVM running under a locale that uses a decimal comma
    would otherwise write ``fragment_bin_tol = 0,02``, which Comet parses as a
    different value or rejects. A test shall run the writer under at least one
    comma-decimal locale and assert byte-identical output.

``R-PARAM-12``
    The exact file written to disk shall be the exact file recorded in
    provenance and passed to Comet. The writer shall write once, hash what it
    wrote, and pass that path; it shall not regenerate the file for the
    process invocation.

Comet Invocation and the Multi-File Run Model
=============================================

Revision 1 did not state how Comet is invoked, where its outputs land, or
whether a run may contain more than one spectrum file. All three are decided
here, because Comet's verified CLI constrains them.

Spectrum-file batches
---------------------

``R-CMT-02``
    A run shall accept **one or more** spectrum files. Real DDA experiments are
    searched as a set of fractions or replicates, and Percolator is normally
    trained on the pooled PIN, so a one-file-per-run design would make the
    product unusable for its actual audience.

``R-CMT-03``
    Comet shall be invoked **once per spectrum file**, with ``-N`` naming an
    output base path inside the run directory. The verified CLI restricts
    ``-N`` to a single input file, and without ``-N`` Comet writes
    ``<input-basename>.pep.xml`` and ``<input-basename>.pin`` **beside the
    input spectra**. Writing into the user's data directory -- which may be
    read-only, shared, or a network mount -- is not acceptable, and it makes
    two runs of the same data overwrite each other's outputs.

``R-CMT-04``
    The canonical parameter file shall be passed with ``-P`` from the run's
    ``parameters/`` directory, and the database with ``-D`` only when it must
    override the serialised ``database_name``; the run shall record which
    mechanism was used. Per-file invocations shall differ only in the input
    path and the ``-N`` base name, and every argument array shall be recorded
    in provenance separately.

``R-CMT-05``
    Per-file Comet invocations may run concurrently, bounded by a configured
    limit derived from the thread setting and available cores. Concurrency
    shall not change the parameter file, the output layout or the recorded
    provenance, and a failure in one file shall fail the stage with the
    per-file logs retained.

``R-CMT-06``
    The PIN files produced per spectrum file shall be merged into one PIN for
    Percolator, preserving exactly one header, validating that all files share
    the same feature columns in the same order, and recording the merge (inputs,
    row counts, output hash) as a provenance event. A feature-column mismatch
    shall fail the stage with a message naming the differing files.

Index modes
-----------

``R-CMT-07``
    Comet's fragment-ion index (``-i``) and peptide index (``-j``) build a
    ``.idx`` file from the FASTA. Where the GUI exposes an index mode, index
    construction shall be a distinct, cached, provenance-recorded workflow step
    keyed by FASTA hash plus the digestion parameters that affect the index, so
    that repeated runs reuse it and a changed FASTA or digestion setting
    invalidates it. An ``.idx`` file is self-describing about which mode built
    it, and the application shall read that rather than assume.

Output containment
------------------

``R-CMT-08``
    All tool outputs, logs and generated parameter files shall be written
    inside the run directory. Nothing shall be written next to the user's
    input spectra or FASTA, and no input file shall ever be modified. A test
    shall run the full workflow with the input directory mounted read-only.

Percolator Configuration
========================

Standard Percolator UI
----------------------

The standard Percolator screen shall expose the version selector; a
capability/status badge; the PSM result q-value filter (default 0.01); the
peptide result q-value filter (default 0.01); a concise explanation that these
filters change display and export only; and an Advanced settings disclosure.

Advanced Percolator settings
----------------------------

Advanced settings shall expose, where supported by the selected version:
``testFDR`` (default 0.01); ``trainFDR`` (default 0.01); random seed; maximum
iterations; thread count; train-subset options where relevant; search-input and
target-decoy behaviour where relevant; decoy-prefix behaviour where relevant;
and any additional supported options explicitly chosen for the product schema.

``R-PERC-04``
    The GUI shall not equate ``testFDR`` with the PSM result display filter.
    Their descriptions shall state the difference explicitly.

``R-PERC-05``
    The random seed shall have a fixed, recorded default rather than being left
    implicit, and its effective value shall always be written to provenance,
    so that a rerun of an archived run is reproducible.

``R-PERC-06``
    Command construction shall be capability-driven per version. The
    application shall never pass an option the probed version does not
    advertise -- in particular it shall not pass any XML output option to a
    version lacking ``XML_OUTPUT``, which is the specific failure mode
    introduced by the 3.09 removal.

Percolator outputs
------------------

For a normal run the adapter shall request and preserve, as available: target
PSM TSV; target peptide TSV; optional decoy PSM TSV; optional decoy peptide
TSV; the learned weights file; XML output when the selected version supports it
and an enabled downstream stage needs it; and stdout and stderr logs.

``R-PERC-07``
    Raw outputs shall be immutable after successful completion. Derived
    filtered exports shall be new files under a distinct directory.

Input validation before Percolator
----------------------------------

Before invoking Percolator, CometGUI shall validate the merged PIN: the file
exists and is non-empty; required header fields exist; both target and decoy
labels are present when the configured mode requires them (``R-DEC-04``);
feature columns contain parsable numeric values where required; and the
protein/decoy prefix behaviour is not obviously inconsistent with the selected
configuration. This validation should produce a useful GUI error before
Percolator emits a more obscure one.

Q-value result filters
----------------------

The Results model shall keep the original q-values read from Percolator. A
filter is a view predicate::

    PSM visible     := psm.q_value <= psm_filter
    Peptide visible := peptide.q_value <= peptide_filter

``R-RES-01``
    Defaults are 0.01 and 0.01, independently. The valid range is [0, 1]. The
    boundary is inclusive. Changing a value updates counts and table contents
    without rerunning any tool. The UI shows total records and records passing
    the current filter. Raw Percolator files remain unchanged. Filter values
    are stored in project/run view state, and are written to provenance when
    used to generate an export, with exported filtered files identifying the
    cutoff in their metadata.

``R-RES-02``
    Rows whose q-value is missing or unparsable shall be counted and shown as
    a distinct "unfiltered/unknown" category rather than being silently dropped
    or silently included; the policy shall be identical in the UI and in export.

Learned feature weights
-----------------------

``R-PERC-08``
    The application shall always request a Percolator weights artefact when
    the selected version supports it. If a stable weights file is unavailable
    for a version, a version-specific parser may fall back to the documented
    stdout block, but the file is preferred and the fallback shall be recorded
    as a provenance warning.

The UI shall call this view **Learned feature weights (Percolator SVM)**. A
navigation shortcut may say **Parameter importances**, but the description must
state that the values are coefficients learned after Percolator's feature
normalisation and cross-validation, and are not causal importances.

For every feature show: feature name; the weight from each cross-validation
split; mean signed weight; mean absolute weight; standard deviation; a sign
consistency indicator; and rank by mean absolute weight. The table shall be
sortable. A bar chart may show mean absolute magnitude with signed direction
available in text; the table remains the accessibility and export source of
truth. The weights artefact shall be checksummed and listed in provenance.

``R-PERC-09``
    The number of cross-validation splits shall be read from the artefact, not
    assumed to be three.

Workflow Engine
===============

Canonical workflow DAG
----------------------

A normal run shall use these steps:

#. Validate project inputs and configuration.
#. Resolve, install and probe Comet.
#. Resolve, install and probe Percolator.
#. Serialise the canonical Comet parameter file.
#. Hash immutable inputs.
#. Build or reuse the Comet index, if an index mode is selected.
#. Run Comet once per spectrum file.
#. Validate Comet pepXML and PIN outputs per file.
#. Merge PIN files.
#. Run Percolator.
#. Validate and parse Percolator outputs and learned weights.
#. Finalise core result indexes and summaries.
#. Hash outputs and finalise core provenance.
#. Optional: launch or use PDV.
#. Optional: run the Limelight converter.
#. Optional: upload to Limelight.
#. Append downstream provenance events and refresh the report.

Hashing may execute concurrently with tool installation when safe, but a tool
must never begin reading an input that CometGUI is mutating, and the input
fingerprint recorded for a run must correspond to the file contents actually
used.

Stage reruns
------------

The workflow shall understand stage dependencies. Changing only the PSM or
peptide display filters requires no scientific rerun. Changing Percolator
parameters reruns Percolator and downstream conversion, not Comet. Choosing an
XML-capable Percolator for Limelight after running a version without XML reruns
Percolator from the preserved merged PIN, then conversion; Comet is reused.
Changing Comet parameters invalidates Comet and everything downstream. Changing
only the Limelight q cutoff invalidates only conversion and upload.

``R-RUN-01``
    Stage invalidation shall be computed from a declared dependency graph and
    an input fingerprint per stage, not from ad hoc conditionals, and the UI
    shall preview exactly which stages will rerun before execution starts.

Cancellation and recovery
-------------------------

Cancelling shall terminate the active stage and its process descendants where
possible, mark outputs as partial, and preserve logs and provenance. A failed
or cancelled run shall be reopenable, and the user shall be able to retry from
the failed stage while prerequisites remain valid.

``R-RUN-02``
    CometGUI shall not reuse a prerequisite whose checksum no longer matches
    the recorded manifest; it shall say which file changed and offer to rerun
    the producing stage.

Project and Run Storage
=======================

Project model
-------------

A project contains mutable user intent and one or more immutable run records::

    MyProject/
        project.json
        project.lock
        presets/
        runs/
            20260828T231500Z-<id>/
                run.json
                parameters/
                    comet.params
                    percolator-settings.json
                inputs/
                    pin/merged.pin
                outputs/
                    comet/<spectrum-basename>.{pep.xml,pin}
                    percolator/
                    limelight/
                logs/
                    comet.<spectrum-basename>.{stdout,stderr}.log
                    percolator.{stdout,stderr}.log
                    limelight-converter.log
                    limelight-upload.log
                provenance/
                    provenance.json
                    provenance.rst

``R-RUN-03``
    Large input spectra and FASTA files shall not be copied into the project by
    default. The project shall store canonical path, size, timestamps, MD5 and
    SHA-256. A "portable project" function may later copy or hard-link inputs
    explicitly.

``R-RUN-04``
    ``project.json`` and ``run.json`` shall each carry a schema version, and
    the application shall define and test its policy for opening older
    versions (migrate, or refuse with a clear message) and newer ones (refuse
    without data loss, never partially parse).

``R-RUN-05``
    A project opened by one CometGUI instance shall be locked against
    concurrent modification by another instance on the same machine, with a
    stale-lock recovery path that identifies the owning process.

Run immutability
----------------

``R-RUN-06``
    Once a run starts, its serialised Comet and Percolator scientific
    parameters are immutable. Editing the project creates a new prospective
    configuration or a new run/retry record, so the GUI can never display a
    parameter state that differs from what was executed.

Results Model and UI
====================

Result indexing
---------------

Percolator outputs shall be parsed into a queryable local result model. The
parser layer shall be independent of the UI and version-aware.

``R-RES-03``
    The results architecture shall not assume the result set fits in heap. A
    disk-backed indexed representation (for example SQLite) shall be used above
    a documented row-count threshold, and the UI shall bind to a paged,
    query-backed model rather than to an ``ObservableList`` holding every PSM.
    A performance fixture large enough to cross the threshold shall exist
    before the results UI is built, not after.

PSM table
---------

Columns, where available: spectrum/native identifier; source file; scan or
index; precursor charge; peptide/peptidoform; protein IDs; Percolator score;
q-value; PEP; useful Comet scores or features carried through; and
decoy/target state where appropriate. With multi-file runs the source file
column is mandatory, not optional.

Peptide table
-------------

Columns, where available: peptide/peptidoform; proteins; Percolator score;
q-value; PEP; and the number of supporting PSMs where derivable without
changing scientific meaning.

Result tables shall support sorting, text filtering, column visibility, copy
and export, and stable selection across filter changes.

``R-RES-04``
    Export shall produce a new file that records, in an accompanying metadata
    block or sidecar, the run ID, the filter values applied, the row count
    before and after filtering, and the CometGUI version.

PDV Integration
===============

Managed installation
--------------------

PDV shall be managed by the same Tool Registry as Comet and Percolator, with
PDV 2.7.0 as the initial verified version. Its JAR checksum and reported
version shall appear in provenance whenever PDV is launched from a run. Because
the download is approximately 99 MB, PDV installation shall be deferred until
first use, cancellable, and shall not be a prerequisite for a search.

Baseline integration
--------------------

The baseline production integration shall use documented PDV functionality for
Comet pepXML plus MGF/mzML/mzXML. The Comet pepXML produced by the search shall
be preserved even though the primary result tables come from Percolator,
because it is the native search identification data PDV understands.

The Results UI shall provide: Open run in PDV; Open the selected
source/spectrum context in PDV where a robust documented mapping exists; and a
clear error when the source spectrum format cannot be visualised by the
selected PDV version.

Exact-selection integration via mzTab
--------------------------------------

**Decided 2026-08-30 (``D-005``).** CometGUI drives PDV the way CasanovoGUI
does: a PDV window per result set, held open on a loopback control port, told
which spectrum to display when the user selects a PSM.

The mechanism is PDV's existing de novo external-control mode, used unmodified::

    java -jar PDV.jar denovo-gui \
        --mztab <generated>.mztab \
        --spectrum run.mzML \
        --port <ephemeral-port> \
        [--hide-psm-table]

with ``/ready``, ``/select?ref=<spectra_ref>`` and ``/shutdown`` on
``127.0.0.1``. This is verified to exist and to work: ``Noble-Lab/CasanovoGUI``
drives it in production from ``PdvLauncher`` and ``PdvController``, reserving an
ephemeral port, polling readiness, and sending a debounced ``/select`` when the
user clicks a peptide.

**The gap, and how it is closed.** That door opens only to mzTab. CasanovoGUI
passes through it because Casanovo emits mzTab natively; Comet plus Percolator
does not, and no ``db-gui`` equivalent exists upstream. CometGUI therefore
**generates the mzTab itself** from its own results. Two consequences follow,
and both are improvements on the alternatives this document previously listed:
**no PDV fork is required**, so there is no divergent binary to checksum,
maintain and upstream; and **no upstream contribution is on the critical path**,
so release 1 does not depend on a third party's schedule.

``R-PDV-02``
    CometGUI shall generate an mzTab document from the Comet pepXML and the
    Percolator results of a completed run, sufficient for PDV's ``denovo-gui``
    mode to display any PSM in the result set. The generated file is a run
    artefact: it is recorded in provenance with its checksum like any other
    output, and it is never presented to the user as a scientific deliverable
    or as an interchange format for third parties.

``R-PDV-03``
    **Fidelity.** The generated mzTab shall be *accurate and true to the
    original results*. This is a gate, not an aspiration, and shall be proved
    by comparison against the source rather than by the exporter's own
    accounting:

    * **Completeness and uniqueness.** Every PSM the results model holds shall
      appear exactly once, and the mzTab shall contain no PSM that the source
      does not. Counts shall be asserted on both sides.
    * **Values are carried, never recomputed.** Peptide sequence, charge,
      observed *m/z*, retention time, protein accessions, and Percolator's
      q-value and PEP shall be transcribed from the source. Where mzTab
      requires a value the source does not supply, the field shall be left
      explicitly null rather than derived, defaulted or invented.
    * **Modifications shall survive exactly**, including position and mass, and
      round-trip comparison shall be by parsed modification rather than by
      string equality.
    * **``spectra_ref`` shall resolve to the spectrum that actually produced
      the PSM.** This is the rule most likely to fail silently: Phase 00
      established that PDV, through ``msftbx``, numbers spectra by 1-based file
      position while pepXML carries the instrument scan number, and the two
      diverge for any scan-range subset. A test shall prove the identity holds
      on a file where the two orderings differ, not merely on one where they
      coincide.
    * **No silent loss.** If any PSM cannot be represented faithfully, export
      shall fail loudly, naming the PSM and the reason. A partial mzTab
      presented as complete is the failure mode this rule exists to prevent.

``R-PDV-04``
    PDV lifecycle shall be owned by the process service (``R-PROC-02``): one
    PDV instance per result set keyed on its mzTab, an ephemeral loopback port
    obtained without a fixed default, readiness established by polling
    ``/ready`` rather than by sleeping, selection requests debounced, and every
    instance shut down when its result set closes or the application exits. A
    PDV that fails to become ready shall surface an actionable error rather
    than leaving the UI waiting.

``R-PDV-05``
    Reuse of ``Noble-Lab/CasanovoGUI``'s ``PdvLauncher`` and ``PdvController``
    is permitted and expected (``D-001``), subject to that decision's
    obligations: retain the upstream copyright notices and record the
    derivation. What CometGUI supplies is the mzTab input and the results-model
    binding; the port, readiness and selection machinery need not be reinvented.

PDV testing
-----------

``R-PDV-01``
    Automated tests shall not rely on whether a PDV window appears. At least
    one test shall invoke PDV's deterministic command-line figure generation on
    a known Comet pepXML plus spectrum file and assert a valid, non-empty
    output figure. End-to-end tests shall additionally start PDV on an
    ephemeral port, prove ``/ready`` is reached, select a **known** PSM through
    ``/select``, and assert the response rather than assuming it. Two Phase 00
    findings make the shape of these tests non-negotiable: PDV's CLI extends
    ``javax.swing.JFrame`` and therefore needs a display, so figure generation
    is not headless; and PDV **exits 0 having written nothing** on indexed
    mzML, so every test shall assert on output files and never on exit status,
    under a timeout -- ``-rt 6`` was observed running 600 seconds writing
    nothing.

Limelight Conversion and Upload
===============================

Converter management
--------------------

The ``limelight-import-comet-percolator`` JAR shall be installed and versioned
through the Tool Registry, pinned per CometGUI release (initially ``v2.8.1``,
``cometPercolator2LimelightXML.jar``) rather than silently tracking latest.

Conversion prerequisites
------------------------

The Limelight tab shall validate that the canonical Comet parameter file
exists; Comet pepXML exists; Percolator XML exists; the selected converter is
installed and verified; the FASTA path is available when needed; and the output
directory is writable.

``R-LL-01``
    If the selected Percolator lacks ``XML_OUTPUT``, conversion controls shall
    be disabled with an explanation and an explicit action, labelled with the
    version resolution actually returns for a Limelight-enabled run -- today
    ``Rerun Percolator with 3.07.1 for Limelight``, never a literal baked into
    the message. The action shall preserve the original Percolator
    run and create a distinct Percolator-stage execution and provenance record.
    It shall reuse the merged PIN and shall not rerun Comet unless the PIN
    fails checksum or prerequisite validation.

``R-LL-02``
    Where no XML-capable Percolator is available for the host platform at all
    (:ref:`spec-percolator-artefacts`), the action offered shall be to register
    a local XML-capable binary, and the message shall say plainly that no
    managed build exists for this platform. The UI shall never offer a rerun
    with a version it cannot obtain.

Converter UI
------------

The standard converter UI shall expose the Limelight q-value cutoff (default
0.01); the output Limelight XML path; whether to import decoys; an optional
independent decoy prefix; open-mod mode; the resolved FASTA; and the resolved
pepXML directory. Advanced values that can be inferred reliably should be shown
read-only by default and be editable only when necessary. The converter's
single q-value option is separate from the GUI's independent PSM and peptide
result filters.

``R-LL-03``
    With multi-file runs, the converter's expectations about the pepXML
    directory shall be satisfied by the run's own ``outputs/comet/`` layout,
    and the adapter shall verify that every pepXML the converter will consume
    belongs to the same run.

Conversion validation
---------------------

``R-LL-04``
    A successful process exit is necessary but not sufficient. CometGUI shall
    also validate that the Limelight XML exists, is non-empty, is readable, has
    the expected top-level XML structure, and passes any locally runnable
    converter or schema validation. The output shall be checksummed and
    recorded in provenance.

Upload
------

The upload UI shall provide server and project selection as required by the
Limelight API, show live upload and import logs, and retain the final
server-side identifier or URL metadata when available.

``R-SEC-03``
    Credentials, tokens and passwords shall never be written to provenance or
    ordinary logs. OS credential/keychain storage is preferred. At minimum,
    secrets shall be held separately from project files and redacted from
    command display, process environment capture and exported reports.

``R-SEC-04``
    Upload is the only outward-facing action in the product. It shall require
    an explicit user action every time, shall show the exact destination server
    and project before sending, and shall never be triggered automatically as
    part of a run.

Provenance and Reproducibility
==============================

Provenance is a primary feature, not an afterthought.

Hash requirements
-----------------

For every regular input and output file used or created by a run, record the
canonical path at time of run; role/type; byte size; modification timestamp;
MD5; and SHA-256.

``R-PROV-01``
    Files shall be hashed by streaming chunks, not by reading whole files into
    memory. Output files shall be hashed only after the producing process has
    closed them. Partial files from failed or cancelled stages may be hashed
    but shall be marked ``partial``.

``R-PROV-02``
    An input-hash cache keyed by canonical path, size, modification time and,
    where available, file identity may be used to avoid rehashing multi-
    gigabyte spectrum files on every run. The cache shall be revalidated on
    every use, shall be invalidated by any attribute change, and shall be
    bypassable. A run's recorded hash shall always be a hash of the content the
    tools actually read; when in doubt, rehash.

``R-PROV-03``
    Both MD5 and SHA-256 shall be computed in a single pass over the file.

Tool provenance
---------------

For each tool invocation record: logical tool name; reported version; release
tag or commit when known; executable or JAR path; its MD5 and SHA-256; upstream
or managed artefact identity; managed versus local status; probed capabilities;
the exact argument array; a safely rendered command for display; working
directory; environment variables added or overridden; start and end timestamps;
duration; exit code; stdout and stderr log paths and checksums;
cancellation/failure state; and warnings or advisories active for that version.

Application provenance
----------------------

Record the CometGUI version; build identifier or git commit; operating system
and version; architecture; JVM/runtime version; locale and time zone; project
and run IDs; the generated Comet parameter file hash and a full archived copy;
Percolator scientific settings including the effective random seed;
result-view q filters when used for a derived export; Limelight conversion
parameters; and PDV launch and version when used. Do not record secrets.

``R-PROV-04``
    The recorded locale shall be the JVM default locale in effect during the
    run, precisely because locale can affect serialisation (``R-PARAM-11``).

Provenance event model
----------------------

``R-PROV-05``
    Provenance shall be written incrementally as appendable events, or as
    atomically updated state, so that a crash still leaves useful history. The
    final ``provenance.json`` shall carry a schema version, and finalisation
    shall be atomic (write-temp-then-rename).

The human-readable ``provenance.rst`` report shall be generated from the same
machine-readable model, never maintained independently.

Provenance UI
-------------

The Provenance tab shall contain:

Summary
    Run ID, status, start and end, tool versions, input and output counts.

Tools
    Name, version, managed or local, binary path, MD5, SHA-256, capability
    badge.

Inputs/outputs
    File role, path, size, MD5, SHA-256, status.

Parameters
    The exact Comet file, Percolator settings, preset and origin information,
    and diffs where useful.

Timeline
    Ordered workflow stages with command, time, duration and exit status.

Logs
    Actions to open the archived logs.

Warnings
    Version advisories, compatibility workarounds, partial-file notices,
    manifest/probe discrepancies.

Actions shall include Copy MD5, Copy SHA-256, Copy command, Open file location,
Export provenance JSON and Export provenance RST.

Supply-Chain and Application Security
=====================================

At minimum: use HTTPS for managed downloads; verify SHA-256 before executing
downloaded artefacts; prefer signed upstream releases or a signed CometGUI
artefact manifest; guard ZIP, TAR and package-payload extraction against
``../`` traversal, absolute paths and unsafe symlinks; never execute a tool
from an unverified temporary download; record provenance for the exact artefact
executed; keep tool caches user-writable but application-scoped; do not put
credentials in command-line arguments where a safer mechanism exists; redact
known secret environment variables; generate an SBOM at release time; run
dependency vulnerability scanning in CI; pin build-plugin versions; sign native
installers where infrastructure permits; publish release checksums; and audit
licences for the CasanovoGUI source, Comet, Percolator, PDV, the converter, the
Java runtime and all bundled and transitive components before redistribution.

``R-SEC-05``
    Archive and package-payload extraction shall be implemented once, in one
    class, with the traversal, absolute-path, symlink and decompression-bomb
    checks applied uniformly to every artefact kind, including ``.deb`` and
    ``.pkg`` payloads if ``D-002`` selects that strategy.

``R-SEC-06``
    Any project-built tool binaries published under ``D-002`` shall be built
    from a pinned upstream tag by a reproducible CI job, shall carry their
    upstream licence and build provenance, and shall be checksummed in the
    manifest exactly like third-party artefacts.

Testing Strategy
================

Testing philosophy
------------------

The test suite must be able to catch real defects. Tests that only assert that
a method returns non-null, that a window opens, or that an exception does not
occur are insufficient for scientific and provenance-critical code.

The suite shall distinguish:

Fast unit tests
    Run on every local and CI build; no network, no native scientific tools.

Component/integration tests
    Exercise filesystem, process, parser and install boundaries with controlled
    fixtures and fake processes.

Real-tool integration tests
    Execute real pinned Comet, Percolator, converter and PDV binaries on small
    real fixtures.

GUI tests
    Drive JavaFX controls and verify UI state and validation.

Packaged end-to-end tests
    Start the actual packaged application in a clean environment and drive a
    complete real workflow.

Nightly/scientific regression tests
    Larger real data, broader version matrices, determinism and performance.

Release acceptance tests
    Test the exact installer and package artefacts that will be published.

JUnit and Java test practices
-----------------------------

Use JUnit Jupiter. Apply: Arrange/Act/Assert structure; one scientifically
meaningful behaviour per test where practical; descriptive names stating
condition and expected outcome; parameterised tests for boundaries and version
matrices; dynamic tests where a schema defines a large set of invariant checks;
temporary directories for filesystem tests; no dependence on developer
home-directory state; fixed seeds for randomised tests with the seed printed on
failure; explicit timeouts for processes and deadlock-prone code; waiting on
observable state rather than arbitrary sleeps; resource cleanup in test
extensions; retention of logs and diagnostic artefacts on failure; and serial
or resource-locked execution for scientific end-to-end tests while pure unit
tests parallelise.

Unit tests: Comet parameter system
----------------------------------

At minimum: parse and serialise every supported scalar type; canonical
parse -> write -> parse equivalence; comment and unknown-parameter preservation;
malformed lines; duplicate-parameter policy; enum mappings; numeric boundaries;
mass tolerance units; the signed precursor tolerance pair (``R-PARAM-04``);
two-value ranges; enzyme table parsing and serialisation; custom enzyme
serialisation; second-enzyme consistency; static modification controls; every
field of every variable-modification tuple across all fifteen slots; min/max
counts; terminal modifications; required/exclusive modifications; one and two
neutral-loss values; invalid combinations; range validation; workflow-enforced
outputs; decoy cross-validation; version-introduced and version-removed
parameters; presets and preset diffs; resetting a field, a category and all
fields; deterministic serialisation including under a comma-decimal locale;
schema migration; Expert raw round-trip; and unknown imported parameters not
silently lost.

``R-TEST-01``
    The parameter round-trip suite shall include the *actual* files emitted by
    ``comet -p`` and ``comet -q`` for every Comet version in the release
    matrix, checked in as fixtures, and shall assert byte-stable canonical
    output.

Unit tests: Percolator
----------------------

At minimum: version parsing from 3.05 through current and future-looking
strings; capability mapping; command construction per version; PSM and peptide
TSV parsing; q-value parsing and missing/NaN policy; the inclusive boundary at
exactly 0.01; filter values 0 and 1; rejection of values below 0 or above 1;
PSM filter independent of peptide filter; train/test FDR independent of result
filters; weights-file parsing; split summary statistics with a
non-hard-coded split count; sign consistency; ranking by mean absolute weight;
malformed and missing weights files; ``XML_OUTPUT`` absent for 3.09; present
for a verified XML-capable build; and advisory rendering.

Unit tests: provenance
----------------------

At minimum: known MD5 and SHA-256 vectors; single-pass dual hashing; streaming
large files; zero-byte files; hash-cache validation and invalidation;
file-changed-during-hash policy; deterministic manifest serialisation where
required; argument-array preservation; environment capture; secret redaction;
no token or password in JSON or RST output; partial, failed and cancelled
states; atomic finalisation; manifest reopen and parse; schema-version
migration; and all mandatory files represented.

Unit tests: installation and security
-------------------------------------

At minimum: artefact selection by OS, architecture and version; checksum match;
checksum mismatch blocks execution; truncated archive; missing expected
executable; missing companion file suppresses the dependent capability;
interrupted install recovery; valid cache hit; corrupt cache rejected; ZIP/TAR
path traversal rejected; absolute archive path rejected; unsafe symlink
rejected; decompression bomb rejected; ``.deb`` payload extraction where
``D-002`` selects it; local binary version probe; local Percolator below 3.05
rejected; local binary checksum recorded; loadability failure produces the
distinct diagnostic required by ``R-PLAT-03``; and capability probe failure
produces an actionable state.

Architecture tests
------------------

Use ArchUnit or equivalent: ``ui`` may depend on domain and application APIs
but domain must not depend on JavaFX; tool adapters must not depend on UI
classes; provenance and hashing must not depend on UI; the parameter parser and
writer must not depend on JavaFX; no cyclic dependencies between major layers;
and process creation is centralised in the process service rather than
scattered ``new ProcessBuilder`` calls (``R-PROC-02``).

Mutation testing
----------------

Use PIT or equivalent on critical pure logic: Comet parameter parsers and
writers; validators; q-value filters; command builders; version and capability
rules; checksum and provenance code; secret redaction; and stage invalidation
rules.

``R-TEST-02``
    The gate is >= 80% mutation score in those packages, with **no** surviving
    mutation that can disable checksum verification, invert a q-value
    comparison, drop a required output, suppress a validation error, pass an
    unsupported option to a tool, or leak a secret.

Coverage
--------

Use JaCoCo; coverage is a diagnostic and a gate, not the goal. Initial gates:
core domain, parameter and provenance logic >= 90% line and >= 85% branch;
UI-independent view-model and presenter logic >= 80% line; adapters covered by
real integration tests rather than artificial line counts; JavaFX rendering
glue has no numeric target but its primary user flows must be GUI-tested. Any
lower threshold must be documented with the untested risk.

Component tests with fake executables
-------------------------------------

Create small test executables that behave like scientific tools so the workflow
engine can deterministically test: stdout/stderr interleaving; exit 0 with
outputs; non-zero exit; child process creation; a hanging process and
cancellation; huge stdout/stderr volume; missing output despite exit 0;
malformed output; a partial file followed by failure; delayed output creation;
and paths containing spaces and Unicode. These fakes complement, never replace,
real-tool tests.

Real-tool integration tests
---------------------------

The test repository shall include or fetch a small licence-compatible real
proteomics fixture: a small FASTA suitable for target/decoy behaviour; a small
mzML and/or MGF with enough real spectra to produce both target and decoy PIN
rows; a stable Comet parameter preset; and expected invariants with
version-pinned golden data. Fixture licensing is ``D-006``.

Real-tool tests shall: generate ``comet.params`` with the production writer;
run the pinned real Comet binary; verify pepXML and PIN existence and
parseability; verify the merge of multiple PIN files; verify target and decoy
rows; run real Percolator; verify PSM, peptide and weights output; verify XML
for XML-capable versions; run the real Limelight converter for the
XML-capable workflow; verify a valid non-empty Limelight XML; and run PDV CLI
figure generation for at least one selected spectrum, verifying a non-empty
annotated figure.

``R-TEST-03``
    The multi-file path shall be a first-class fixture: at least two spectrum
    files searched in one run, exercising per-file ``-N`` invocation, output
    containment, PIN merge, and the source-file column in results.

JavaFX GUI automation
---------------------

TestFX is a candidate because it provides a JavaFX robot and JUnit integration,
but its compatibility with the selected JDK and JavaFX versions shall be proven
in an early spike rather than assumed. If it cannot operate reliably in CI, the
project shall retain the same test semantics behind a small ``FxUiDriver``
abstraction and use a compatible robot or accessibility automation mechanism.

``R-TEST-04``
    Controls required by automated tests shall have stable semantic
    identifiers (``fx:id`` or a dedicated stable test/accessibility ID). Tests
    shall not locate important controls by pixel coordinates or brittle CSS
    ancestry.

GUI tests shall cover at least: navigation to all primary sections; the file
chooser abstraction and its test injection; Comet Essentials fields; Advanced
category expansion; parameter search; reset of field and category; preset
preview, diff and apply; variable modification add, edit and remove; inline
validation and the error summary; Expert raw editor apply and reject;
Percolator version selection and capability messaging; both default q filters
equal to 0.01; independent q filter editing; Run button enable/disable rules;
run progress and the state stepper; cancellation; result tables; the learned
weights table and chart data; Limelight disabled for a Percolator without XML;
the Limelight compatible-rerun action; the Provenance tab and checksum copying;
keyboard focus traversal for the critical workflow; and accessible names for
primary controls.

End-to-end harness: two tiers
-----------------------------

Revision 1 asked for a JavaFX robot driving "the packaged application", which
is only partly coherent: a robot runs in-process, and a packaged application is
a separate process with its own runtime. The requirement is therefore split
into two tiers, both mandatory.

Tier A -- assembled-application GUI E2E
    Runs the real application module in-process with a fresh temporary home and
    tool cache, driven through ``FxUiDriver``. It exercises the complete
    workflow with real tools and real fixtures. This tier owns the detailed
    scenario assertions below, because in-process access makes observable-state
    waiting and independent recomputation practical.

Tier B -- packaged-artefact E2E
    Launches the exact packaged installer output as an external process with a
    fresh home, and drives it either through an external UI automation driver
    or through a test-only loopback control bridge. Its job is to prove that
    *packaging* did not break the product: bundled runtime starts, tool
    download works from the installed layout, a complete run succeeds, and
    provenance is written. It runs the canonical scenario and the no-XML
    scenario, and may assert a smaller set of invariants than Tier A.

``R-TEST-05``
    Neither tier may call ``WorkflowEngine.run()`` directly and be described as
    an end-to-end test. Both shall enter through the same commands the UI
    issues.

``R-TEST-06``
    A test-only control bridge, if used, shall be built only into a
    test-enabled build, shall trigger the same UI actions and commands as real
    controls, and shall never bypass parameter serialisation, validation or
    workflow orchestration. A release-pipeline check shall verify by inspecting
    the shipped artefact that the bridge classes and its enabling flag are
    absent from production builds.

Canonical E2E scenario
~~~~~~~~~~~~~~~~~~~~~~

The principal end-to-end test shall:

#. Create a brand-new temporary user home and application data directory.
#. Ensure no Comet, Percolator, PDV or converter is present in that cache.
#. Launch CometGUI (Tier A: the assembled application; Tier B: the packaged
   artefact).
#. Verify the application reaches a ready state.
#. Create or open a test project through the UI.
#. Choose **two** real spectrum fixtures through the same control users use.
#. Choose the real FASTA fixture.
#. Select Comet 2026.02.2.
#. Select the platform's default XML-capable Percolator, and assert that the
   selection was resolved from the manifest rather than hard-coded.
#. Change at least one precursor or fragment parameter from its preset value.
#. Add or edit at least one variable modification using the structured editor.
#. Verify the GUI parameter summary reflects those changes.
#. Confirm the default PSM q filter is 0.01 and the peptide q filter is 0.01.
#. Click Run.
#. Verify the GUI downloads required tools automatically and that download,
   checksum and probe states become successful.
#. Wait on observable workflow state, never a fixed sleep.
#. Verify the generated ``comet.params`` contains exactly the GUI-selected
   values, and that ``output_pepxmlfile`` and ``output_percolatorfile`` were
   forced on.
#. Verify Comet was invoked once per spectrum file with distinct ``-N`` base
   names, that no file was written next to the input spectra, and that both
   pepXML and PIN outputs parse.
#. Verify the merged PIN has one header and the summed row count of its
   sources.
#. Verify Percolator exits successfully and that PSM, peptide, weights and XML
   artefacts parse.
#. Independently compute the number of PSMs with q <= 0.01 from the raw
   Percolator file and compare with the GUI count; repeat for peptides.
#. Change the PSM filter to 0.005 through the UI and verify only the PSM count
   changes, according to independent calculation.
#. Change the peptide filter to 0.02 and verify the peptide count
   independently.
#. Open Learned feature weights and compare displayed values and ranking with
   the weights artefact.
#. Invoke the PDV integration and verify a successful visualisation or CLI
   figure output for a known PSM.
#. Run the real Limelight converter through the UI and validate the resulting
   XML.
#. Exercise Limelight upload against a controlled local fake endpoint or an
   official sandbox endpoint; never upload CI data to a production server.
#. Open the Provenance view; independently recompute MD5 and SHA-256 for every
   declared input and output and compare with the manifest; verify tool binary
   and JAR checksums match the files on disk; verify exact tool versions and
   argument arrays are present; and verify no secret appears.
#. Close CometGUI, relaunch it, reopen the project, and verify that results,
   the selected run, tool versions, q-filter state and provenance remain
   coherent.

This test shall be designed to fail if a GUI control stops affecting the
parameter file, a downstream stage is bypassed, filtering uses ``<`` instead of
``<=``, an output is omitted from provenance, Comet writes beside its inputs,
or tool installation stops being automatic.

Second E2E scenario: Percolator without XML
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A second end-to-end test shall select Percolator 3.09; run Comet and Percolator
successfully; verify standard PSM, peptide and weights viewing; verify that the
Limelight tab explains the XML incompatibility *before* conversion is
attempted; verify that the one-click compatible rerun installs and uses an
XML-capable version only after the explicit action, and that where no such
managed build exists for the platform the UI offers local-binary registration
instead (``R-LL-02``); verify Comet is not rerun; verify the new Percolator
execution produces XML; verify Limelight conversion then succeeds; and verify
that provenance contains both Percolator executions with distinct versions,
checksums and commands.

Failure-path scenarios
~~~~~~~~~~~~~~~~~~~~~~

Separate end-to-end and integration tests shall exercise: download checksum
mismatch; network unavailable on first install; network unavailable after tools
are cached; a downloaded binary that cannot load on this host (``R-PLAT-03``);
Comet exits non-zero; Comet exits zero but the PIN is missing; a FASTA with no
decoys and ``decoy_search = 0``; a PIN containing no decoy rows; Percolator
exits non-zero; malformed Percolator output; converter exits non-zero;
converter returns zero but the XML is missing or empty; the user cancels Comet;
the user cancels Percolator; a source input deleted after selection; an input
checksum changed before a rerun; the output or project directory becoming
unwritable; insufficient disk space where it can be simulated safely; paths
containing spaces; paths containing Unicode; long paths on supported platforms;
a corrupted cached tool; an interrupted tool install; a stale or partial run
reopened; and a second CometGUI instance opening a locked project.

Scientific regression fixtures and oracles
------------------------------------------

Do not use one oracle type for all tests.

Exact invariants
    Format, required columns, process exit, version, command arguments,
    generated parameters, provenance hashes, filter arithmetic, known selected
    fixture identifiers.

Version-pinned golden expectations
    Expected selected PSM identifiers, stable counts and ranges, known
    parameter file text, known weights parser output for a specific pinned tool
    pair.

Tolerant scientific metrics
    For larger real datasets, compare PSM counts, rank agreement and
    performance within justified tolerance rather than requiring byte-identical
    floating-point output across platforms.

Goldens shall be keyed by the scientific tool pair::

    src/test/resources/goldens/
        comet-2026.02.2_percolator-3.07.1/
        comet-2026.02.2_percolator-3.09.0/

``R-TEST-07``
    Updating a golden shall require a reviewed written explanation of why the
    scientific change is expected, recorded alongside the golden.

Version matrix tests
--------------------

CI and nightly testing shall include representative Percolator versions --
3.05.x, a 3.06.x with its advisory check, the latest compatible XML-capable
version (3.07.1 today), 3.09 and the newest verified version after a registry
update -- **restricted to the
version/platform combinations that the manifest actually provides and that pass
their loadability probe**. For each, test the available PSM, peptide and
weights outputs; test XML for XML-capable versions; and for 3.09 and later
verify that the application neither requests a removed XML option nor enables
Limelight conversion from non-existent XML.

Comet nightly tests should include the current default Comet; at least one
prior supported version if offered; direct FASTA search; index-search mode if
exposed; repeated-run determinism on a pinned fixture; a Windows Thermo RAW
smoke test where infrastructure permits; an mzML or mzXML cross-platform
baseline; and a larger real dataset for result-count, rank and performance
drift.

Performance and resource tests
------------------------------

Measure separately: application startup; parsing a large Comet parameter file;
rendering and filtering large PSM and peptide result sets; hashing multi-
gigabyte files; Comet execution time (informational); Percolator execution
time; peak GUI heap usage for large result tables; cancellation latency; and
project reopen time. Thresholds shall be based on stable dedicated nightly
runners, not noisy shared pull-request runners.

Flakiness policy
----------------

A flaky test is a defect. Do not hide instability with unconditional retries. A
temporarily quarantined flaky test must have an issue and owner, captured
diagnostics, a stated reason and a fix plan. Release-critical scientific
end-to-end tests may not remain quarantined at release.

CI and Release Pipeline
=======================

Pull-request pipeline
---------------------

Compile on the supported JDK; formatting and style check; static analysis; fast
JUnit tests; JaCoCo coverage gate; ArchUnit tests; PIT mutation tests for
critical packages (per PR or on a required merge gate, depending on runtime);
small real-tool integration tests on Linux; Sphinx documentation build with
warnings as errors; traceability report generation (``R-DOC-03``); dependency
and security scanning; and SBOM generation validation.

Nightly pipeline
----------------

Add: the broader Comet and Percolator version matrix; a larger real dataset;
determinism comparisons; performance metrics; the headless and native GUI test
suites; the Windows RAW search test; a documentation link check; and
verification that every managed tool URL and checksum in the manifest is still
reachable and unchanged.

``R-TEST-08``
    The manifest verification job shall fail loudly when an upstream artefact
    disappears, changes checksum, or a new upstream release appears -- the
    condition that silently invalidated the PDV 2.6.0 pin in this
    specification between drafting and verification.

Release pipeline
----------------

For each tier-1 platform: build the native packaged application with its
bundled runtime; produce the installer or archive; compute release checksums;
run the clean-home packaged smoke test; run Tier B canonical E2E on that exact
artefact; run the XML-capable full Limelight E2E; run the no-XML compatibility
E2E; verify the tool download manifest; verify the strict documentation build;
generate and publish the SBOM; verify that no test bridge is present
(``R-TEST-06``); sign and notarise where infrastructure permits; and publish
only if every gate passes.

Documentation
=============

All project documentation shall be authored in reStructuredText and built with
Sphinx for Read the Docs. Avoid a second Markdown documentation system. Where a
hosting or tooling platform requires a Markdown file (for example an
agent-instruction file consumed by a coding tool), it shall be a pointer of a
few lines to the RST documents, never a place where substantive content lives.

Recommended documentation tree::

    README.rst
    .readthedocs.yaml
    docs/
        conf.py  index.rst  installation.rst  getting_started.rst
        workflow.rst  comet_parameters.rst  comet_parameter_presets.rst
        variable_modifications.rst  decoys.rst  percolator.rst  results.rst
        learned_feature_weights.rst  pdv.rst  limelight.rst  provenance.rst
        tool_manager.rst  platform_support.rst  troubleshooting.rst
        faq.rst  citations.rst  release_notes.rst
        developer/
            index.rst  architecture.rst  workflow_engine.rst
            comet_parameter_schema.rst  tool_adapters.rst  tool_registry.rst
            version_capabilities.rst  results_model.rst  provenance_schema.rst
            security.rst  testing.rst  e2e_harness.rst  traceability.rst
            releasing.rst
        reference/
            comet_parameters_generated.rst  percolator_options.rst
            project_format.rst  provenance_format.rst  command_examples.rst

``R-DOC-04``
    The Comet parameter schema shall generate
    ``reference/comet_parameters_generated.rst`` so that user documentation and
    GUI metadata cannot silently diverge. For every parameter the generated
    page shall include the Comet parameter name, GUI display name, category,
    type, default for the versioned schema, allowed values or range, scientific
    description, serialisation form, version availability, related parameters,
    and preset effects where useful.

``R-DOC-05``
    Documentation CI shall run ``sphinx-build -n -W -b html docs docs/_build/html``.
    A scheduled or release job shall also run link checking. Broken internal
    cross-references are build failures.

``R-DOC-06``
    ``platform_support.rst`` and ``troubleshooting.rst`` shall state plainly,
    per platform, which Percolator versions are available as managed installs,
    which support XML, and therefore where the Limelight path works
    out of the box -- rather than leaving users to discover it at conversion
    time.

Implementation Sequence
=======================

The implementation is organised as gated phases. The authoritative definitions
live in ``phases/``; ``ONBOARDING.rst`` explains how an orchestrating agent
runs them and ``STATUS.rst`` records where the project currently is. This
section is a summary only and must not be treated as a substitute for the phase
documents.

.. list-table:: Phase overview
   :header-rows: 1
   :widths: 12 40 48

   * - Phase
     - Title
     - Purpose
   * - 00
     - Feasibility, legal and upstream verification
     - Re-verify upstream facts; resolve licensing; prove the toolchain and the
       scripted end-to-end scientific path; settle open decisions. No product
       code.
   * - 01
     - Repository, build and quality skeleton
     - Multi-module build, pinned toolchain, test/coverage/mutation/architecture
       infrastructure, docs build, CI.
   * - 02
     - Application shell and navigation
     - JavaFX shell, information architecture, MVVM boundary, dependency
       injection, headless testability.
   * - 03
     - Process service
     - Argv-only execution, streaming, cancellation, descendant termination,
       log archiving, fake-tool suite.
   * - 04
     - Hashing and provenance core
     - Single-pass MD5+SHA-256, hash cache, event model, schema-versioned JSON,
       atomic finalisation, redaction, RST report.
   * - 05
     - Tool registry and installer
     - Manifest, download, verification, safe extraction of every artefact
       kind, atomic install, three-stage probing, Tool Manager UI.
   * - 06
     - Comet parameter model
     - Schema from ``-q`` plus curated metadata, parser and writer, validators,
       presets, migration, drift test, generated reference.
   * - 07
     - Comet parameter editor UI
     - Essentials, Advanced and Expert modes, structured editors, search,
       diffs, validation surfacing, accessibility.
   * - 08
     - Workflow engine and Comet adapter
     - DAG and states, per-file invocation, output containment, decoy
       validation, PIN merge, run storage and immutability.
   * - 09
     - Percolator adapter and versions
     - Capability-driven command building, outputs and weights, advisories,
       compatible-version rerun.
   * - 10
     - Results model and UI
     - Parsers, disk-backed indexing, tables, independent filters, weights
       view, export.
   * - 11
     - PDV integration
     - Managed install, open-in-PDV, CLI figure test, optional enhanced control
       mode.
   * - 12
     - Limelight conversion and upload
     - Converter adapter, cutoff and decoy handling, XML validation, upload,
       credential handling.
   * - 13
     - Provenance UI and reports
     - Provenance tab, actions, JSON and RST export, diffs, timeline.
   * - 14
     - GUI automation and packaged E2E
     - ``FxUiDriver``, Tier A and Tier B harnesses, packaging, failure paths,
       bridge-absence check.
   * - 15
     - Matrix, performance and hardening
     - Version matrix, nightly suites, determinism, performance thresholds,
       chaos and security testing.
   * - 16
     - Documentation and release qualification
     - Complete user and developer documentation, generated reference, Read the
       Docs, packaging on all tier-1 platforms, SBOM, signing, licence review,
       human UX validation.

.. _spec-acceptance:

Acceptance Criteria
===================

A release is not complete unless every applicable criterion is met. Criteria
marked |human| require human sign-off and cannot be discharged by an automated
test.

.. |human| replace:: **[human]**

Installation and tool management
--------------------------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-INS-01``
     - On each tier-1 OS, a user can install or extract CometGUI without
       installing Java separately.
   * - ``AC-INS-02``
     - A first real run installs the required scientific tools automatically.
   * - ``AC-INS-03``
     - Downloaded executables and JARs are SHA-256 verified before execution.
   * - ``AC-INS-04``
     - Managed tool MD5 and SHA-256 are shown in provenance.
   * - ``AC-INS-05``
     - Corrupt or checksum-mismatched tools are never executed.
   * - ``AC-INS-06``
     - Percolator 3.05+ local binaries can be registered and probed.
   * - ``AC-INS-07``
     - Existing runs pin exact scientific tool versions and artefact
       checksums.
   * - ``AC-INS-08``
     - A tool artefact that cannot load on the host produces the diagnostic
       required by ``R-PLAT-03``, and that tool is not offered for selection.
   * - ``AC-INS-09``
     - On macOS, managed tools execute on a clean machine without a Gatekeeper
       prompt the application cannot handle.
   * - ``AC-INS-10``
     - The Tool Manager never offers a version/platform combination for which
       no verified artefact exists.

Comet parameters
----------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-PAR-01``
     - Comet 2026.02.2 is fully represented by the supported parameter schema
       as discovered by ``-q``, subject only to documented internal exclusions.
   * - ``AC-PAR-02``
     - Schema drift CI detects unmodelled supported parameters.
   * - ``AC-PAR-03``
     - Essentials mode can configure a normal tryptic DDA search without raw
       parameter editing.
   * - ``AC-PAR-04``
     - Advanced mode exposes all supported user-relevant parameters.
   * - ``AC-PAR-05``
     - Variable modifications use a structured editor covering all fifteen
       slots and every tuple field.
   * - ``AC-PAR-06``
     - Imported unknown parameters are never silently dropped.
   * - ``AC-PAR-07``
     - Expert raw mode round-trips through the typed model.
   * - ``AC-PAR-08``
     - Preset application shows a diff before changing anything.
   * - ``AC-PAR-09``
     - Required pepXML and PIN outputs cannot be disabled while a dependent
       stage is enabled.
   * - ``AC-PAR-10``
     - Invalid cross-parameter configurations block Run with actionable,
       field-attached errors.
   * - ``AC-PAR-11``
     - Canonical serialisation is byte-identical under a comma-decimal locale.

Workflow and decoys
-------------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-WF-01``
     - A run accepts multiple spectrum files, invokes Comet once per file, and
       merges the PINs correctly.
   * - ``AC-WF-02``
     - No file is written next to the user's inputs; a run succeeds with the
       input directory read-only.
   * - ``AC-WF-03``
     - A decoy configuration that would yield no negative examples, or double
       decoys, blocks the run with a specific explanation.
   * - ``AC-WF-04``
     - The rerun preview names exactly the stages that will re-execute.
   * - ``AC-WF-05``
     - Cancellation terminates the stage and its descendants and leaves usable
       logs and provenance.

Percolator and results
----------------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-RES-01``
     - The default PSM result q cutoff is 0.01.
   * - ``AC-RES-02``
     - The default peptide result q cutoff is 0.01.
   * - ``AC-RES-03``
     - The two filters are independent.
   * - ``AC-RES-04``
     - Changing a filter never reruns Percolator or mutates raw output.
   * - ``AC-RES-05``
     - ``trainFDR`` and ``testFDR`` are represented separately from the display
       filters.
   * - ``AC-RES-06``
     - An XML-capable Percolator produces PSM, peptide, weights and XML in the
       verified workflow.
   * - ``AC-RES-07``
     - 3.09 runs normal rescoring without the GUI attempting removed XML I/O.
   * - ``AC-RES-08``
     - Learned Percolator weights are viewable and exportable, with the split
       count read from the artefact.
   * - ``AC-RES-09``
     - Displayed weight summary values match the underlying artefact.
   * - ``AC-RES-10``
     - Result tables remain responsive on the large performance fixture within
       the documented heap budget.

PDV and Limelight
-----------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-VIS-01``
     - The current verified PDV installs automatically on first use.
   * - ``AC-VIS-02``
     - A known Comet pepXML plus spectrum file can be visualised.
   * - ``AC-VIS-03``
     - The PDV CLI automated test produces a valid annotated spectrum artefact.
   * - ``AC-VIS-04``
     - Selecting a PSM in the results table makes the running PDV window
       display that spectrum, proved through the loopback control server
       rather than by observing a window.
   * - ``AC-VIS-05``
     - The generated mzTab is proved accurate and true to the Comet and
       Percolator results it came from, by comparison against the source
       (``R-PDV-03``).
   * - ``AC-LL-01``
     - The Limelight converter installs automatically.
   * - ``AC-LL-02``
     - Limelight conversion succeeds for the XML-capable workflow fixture.
   * - ``AC-LL-03``
     - Limelight controls are disabled and explained for a Percolator without
       XML, before conversion is attempted.
   * - ``AC-LL-04``
     - The explicit compatible rerun reuses Comet output and enables
       conversion; where no managed XML-capable build exists for the platform,
       local-binary registration is offered instead.
   * - ``AC-LL-05``
     - The Limelight q cutoff is separately configurable and defaults to 0.01.
   * - ``AC-LL-06``
     - Credentials never appear in provenance, logs or exports.

Provenance
----------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-PRV-01``
     - Every input and output file that exists has both MD5 and SHA-256.
   * - ``AC-PRV-02``
     - Exact tool versions and tool artefact hashes are recorded.
   * - ``AC-PRV-03``
     - Exact command argument arrays are recorded, per spectrum file.
   * - ``AC-PRV-04``
     - The exact generated Comet parameter file is archived and hashed, and is
       the file that was executed.
   * - ``AC-PRV-05``
     - Start, end, duration and exit code are recorded for every process.
   * - ``AC-PRV-06``
     - Failed and cancelled runs retain useful provenance.
   * - ``AC-PRV-07``
     - Provenance is viewable in the GUI and exports to JSON and RST.
   * - ``AC-PRV-08``
     - Independent end-to-end hash recomputation matches the manifest.
   * - ``AC-PRV-09``
     - Secret redaction tests pass.
   * - ``AC-PRV-10``
     - The effective Percolator seed and the JVM locale are recorded.

Testing, documentation and release
----------------------------------

.. list-table::
   :header-rows: 1
   :widths: 16 84

   * - ID
     - Criterion
   * - ``AC-TST-01``
     - Meaningful JUnit tests cover critical domain logic.
   * - ``AC-TST-02``
     - Coverage gates pass.
   * - ``AC-TST-03``
     - The critical-package mutation score gate passes with no forbidden
       surviving mutation.
   * - ``AC-TST-04``
     - Architecture rules pass.
   * - ``AC-TST-05``
     - The JavaFX GUI automation suite passes.
   * - ``AC-TST-06``
     - Tier A canonical E2E passes.
   * - ``AC-TST-07``
     - Tier B packaged E2E passes on every tier-1 platform.
   * - ``AC-TST-08``
     - The no-XML compatibility E2E passes.
   * - ``AC-TST-09``
     - The failure-path suite passes.
   * - ``AC-TST-10``
     - The nightly real-data regression suite is healthy.
   * - ``AC-TST-11``
     - The Windows Thermo RAW smoke test is healthy when that feature ships.
   * - ``AC-TST-12``
     - No test bridge is present in any published artefact.
   * - ``AC-DOC-01``
     - Sphinx builds with warnings as errors, and Read the Docs builds
       successfully.
   * - ``AC-DOC-02``
     - The traceability report shows every ``R-`` implemented and every ``AC-``
       tested or marked for human sign-off.
   * - ``AC-REL-01``
     - SBOM, security and dependency checks pass.
   * - ``AC-REL-02``
     - The CasanovoGUI derivative-source licensing question (``D-001``) is
       resolved before any public redistribution. |human|
   * - ``AC-REL-03``
     - Licence audit of all bundled and transitive components is complete.
       |human|
   * - ``AC-UX-01``
     - Domain and task analysis with an experienced Comet user is complete.
       |human|
   * - ``AC-UX-02``
     - Heuristic evaluation is complete and its defects are triaged. |human|
   * - ``AC-UX-03``
     - A cognitive walkthrough of the primary workflow is complete. |human|
   * - ``AC-UX-04``
     - A usability test with a routine proteomics user is complete. |human|
   * - ``AC-UX-05``
     - A usability test with an advanced Comet user is complete. |human|
   * - ``AC-UX-06``
     - A keyboard-only and accessibility review is complete. |human|

.. _spec-decisions:

Key Risks and Required Decisions
================================

Open decisions are tracked in ``DECISIONS.rst``; each is referenced by the
phase whose exit gate it blocks.

``D-001`` CasanovoGUI source licensing
--------------------------------------

The highest-priority non-technical gate, and still open as of 2026-08-28: the
repository publishes no licence. A public repository is not permission to
redistribute a derivative. Either an explicit licence is added upstream or
written permission is obtained and recorded. Until then, no CasanovoGUI code
may be copied (``R-SEC-01``); the architecture in this specification is
implementable independently, so this decision gates *derivation*, not the
project.

``D-002`` XML-capable Percolator artefact strategy
---------------------------------------------------

**Resolved 2026-08-29.** The project does not build Percolator from source and
offers only versions upstream publishes binaries for. Under those constraints
the latest compatible version -- newest with XML-capable builds on Linux, macOS
and Windows -- is **3.07.1**, verified by executing the Linux build and
extracting the macOS payload; the Windows artefact is inferred and must be
confirmed on a Windows runner in Phase 00. The residual risk is no longer
"which strategy" but three concrete engineering tasks: payload extraction for
three package formats, XSD companion installation, and Rosetta 2 handling on
Apple silicon. The accepted trade is that 3.07.1 predates 3.08's I-spline PEP
default and the PEP-greater-than-1.0 fix; those are carried as version
advisories (``R-PERC-11``).

``D-003`` Percolator historical binary availability
----------------------------------------------------

Related but broader: supporting "every Percolator >= 3.05" is feasible at the
adapter and schema level, while managed one-click installation depends on a
usable binary per version and platform. The release process must either publish
verified project-built artefacts where legally permitted, or describe
unsupported managed combinations and rely on local binaries.

``D-004`` macOS architecture policy
-----------------------------------

Comet publishes native ``aarch64`` and ``x86-64`` macOS builds; Percolator's
XML-capable macOS artefacts are ``x86-64`` only. Decide whether release 1
requires Rosetta 2 for the Percolator stage on Apple silicon, ships a
project-built ``arm64`` Percolator, or scopes the Limelight path off macOS.

``D-005`` PDV database-search remote control
---------------------------------------------

PDV has the needed file-format support but its documented external control path
is de novo specific. If precise click-a-row-to-update-PDV behaviour is required
for release 1, budget an upstream PDV feature or a small maintained fork. Do
not hide this dependency behind fragile GUI automation.

``D-006`` Test fixture data and licensing
------------------------------------------

The real-tool and nightly suites need spectra and a FASTA that may be
redistributed in a public repository or fetched reproducibly. Choose the
source, record its licence, and decide whether fixtures are vendored or fetched
by checksum.

``D-007`` Limelight test endpoint
----------------------------------

Upload tests must not target a production Limelight server. Decide between a
controlled local fake endpoint, a sandbox instance, or both.

``D-008`` CometGUI's own licence and distribution
--------------------------------------------------

Two thirds decided. The **licence is GPL-3.0** (2026-08-29, with ``D-001``).
Managed tool binaries are **downloaded from upstream by pinned URL and
SHA-256, not redistributed** with CometGUI (2026-08-29): the release artefacts
therefore carry no Comet, Percolator, PDV or converter binary, Apache-2.0 s4
notice obligations do not attach to them, and ``R-TEST-08``'s job -- failing
loudly when an upstream artefact disappears or changes checksum -- becomes
load-bearing rather than advisory, because an upstream deletion breaks
installation for every new user. **Still open:** where CometGUI is published,
which the GPL-3.0 source-availability obligation depends on. There is no git
remote and none may be created until that is answered.

Comet parameter completeness
----------------------------

Comet evolves; a hand-written static form will rot. Versioned metadata plus
binary-derived parameter names plus schema-drift CI is what keeps the GUI
credible. The verified 96-versus-118 gap between ``-p`` and ``-q`` shows how
easily a plausible-looking implementation silently loses two thirds of the
variable-modification slots.

Scientific golden tests
-----------------------

Exact output can change legitimately with tool versions. Goldens must be keyed
by tool versions and reviewed when changed. Workflow correctness, provenance,
filter arithmetic, parameter generation and format invariants can be tested far
more strictly than floating-point scores.

Large result scalability
------------------------

Do not assume result tables fit in memory. Establish the performance fixture
early and adopt disk-backed indexing before the UI architecture couples itself
to an ``ObservableList`` of every PSM.

Upstream drift
--------------

Every version in this document was correct on 2026-08-28 and some were already
stale relative to Revision 1 written the same day. The manifest verification
job (``R-TEST-08``) exists so that drift is discovered by CI rather than by a
user.

Definition of Done
==================

The project is done when a scientist on a clean supported computer can install
only CometGUI, choose real spectra and a FASTA, configure a scientifically
valid Comet search through a comprehensible parameter interface, select a
supported Percolator version, execute the real workflow, inspect 1% PSM and
peptide results, change those filters independently, inspect learned Percolator
feature weights, inspect spectra in PDV, produce and upload compatible
Limelight XML where the platform permits, and inspect a provenance record
containing exact versions, commands and MD5 plus SHA-256 hashes for every input
and output -- and when automated tests drive both the assembled and the
packaged GUI through the same workflow and independently prove that those
claims are true, with every ``AC-`` criterion either passing its named test or
carrying a recorded human sign-off.
