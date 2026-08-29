=========================================================
Upstream facts, re-verified 2026-08-29
=========================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 2 -- upstream fact re-verification, live
:Verification date: 2026-08-29
:Verified against: ``specification.rst`` revision 2, whose fact table is dated
                   2026-08-28
:Produced by: ``scripts/feasibility/verify_upstream_facts.py``
:Machine-readable result: ``scratch/upstream/facts-2026-08-29.json``
                          (gitignored; regenerate with the script)

Why this document exists
========================

The specification's *Verified Upstream Facts* table was checked once, on
2026-08-28, and one of its rows -- the PDV release -- went stale between two
drafts of the same document on the same day. Phase 00's exit gate therefore
requires every row to be re-checked against the live upstream source, with the
date, the URL and the method recorded per row.

Every row below was re-observed on 2026-08-29. No row was carried over from the
specification without a fresh check; where this work unit deliberately did not
re-check something, the row says so and names the work unit that owns it.

.. warning::

   This document reports what was observed. It does **not** amend
   ``specification.rst``: a phase agent may not edit the specification. The
   :ref:`Differences from specification.rst <uf-differences>` section below is
   the escalation surface for the phase orchestrator.

How the checks were run
=======================

``scripts/feasibility/verify_upstream_facts.py`` is Python 3 standard library
only, runs on the host's ``/usr/bin/python3`` (3.11.2), and needs no
virtualenv::

   python3 scripts/feasibility/verify_upstream_facts.py
   python3 scripts/feasibility/verify_upstream_facts.py --refresh          # ignore the cache
   python3 scripts/feasibility/verify_upstream_facts.py --refresh-github   # GitHub only

It writes ``scratch/upstream/facts-2026-08-29.json`` and a summary table to
stdout. Each fact in the JSON carries an ``id``, ``subject``,
``specification_claim``, ``url``, ``method``, ``observed`` and ``verdict``,
plus the HTTP provenance (live or cache, and when).

Three properties matter for re-running it.

**It is cache-aware.** Unauthenticated GitHub allows 60 requests per hour for
the whole host, shared with the other Phase 00 work units. Every HTTP response
is written to ``scratch/apicache/``, so a re-run costs no quota. The run of
2026-08-29 00:41 UTC was made with ``--refresh``, so every value in this
document was fetched live, not replayed: it used 8 GitHub API requests and left
52 of 60.

**It has a rate-limit fallback.** If the GitHub releases API answers HTTP 403
with a rate-limit message, the script falls back to the repository's
``releases.atom`` feed, which is not rate-limited, and records which route it
used in the ``provenance`` of every fact derived from it. The fallback was
exercised on 2026-08-29 against ``percolator/percolator`` and ``wenbostar/PDV``
and independently returned ``rel-3-09`` / 2026-05-21T17:25:43Z and ``v2.7.0`` /
2026-08-14T19:55:27Z, matching the API. No row in this document needed the
fallback.

**Exit code 0 proves nothing.** The script says so, and the observed values --
not its exit status -- are the evidence. Spot-check any row from its ``url``.

Host used for the executed checks
---------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Property
     - Value
   * - Platform
     - ``Linux-6.8.0-137-generic-x86_64-with-glibc2.36`` (Debian 12)
   * - glibc
     - 2.36
   * - Python
     - 3.11.2 (``/usr/bin/python3``)
   * - Available binutils
     - ``readelf``, ``strings``. ``file`` is **not** installed on this host, so
       file typing was done with ``readelf -h`` rather than ``file``.

Verdict summary
===============

22 rows, matching the 22 rows of the specification's table.

.. list-table::
   :header-rows: 1
   :widths: 20 12 68

   * - Verdict
     - Count
     - Meaning
   * - ``CONFIRMED``
     - 18
     - Today's observation matches what the specification asserts.
   * - ``CHANGED``
     - 0
     - Today's observation contradicts the specification. None found.
   * - ``UNVERIFIED``
     - 4
     - Not established by this work unit. All four are artefact
       download/extraction rows owned by another Phase 00 unit; see
       `Rows deliberately not re-checked here`_.

Three rows are ``CONFIRMED`` but carry a wording or completeness difference
that the phase orchestrator should see. They are set out in full under
:ref:`Differences from specification.rst <uf-differences>`.

The verified table
==================

Comet
-----

.. list-table::
   :header-rows: 1
   :widths: 16 34 34 16

   * - Subject
     - Finding today (2026-08-29)
     - Source URL and method
     - Verdict vs specification
   * - Comet current release
     - ``v2026.02.2``, ``published_at`` 2026-08-11T21:57:33Z, not a
       prerelease. It is the newest of 19 releases; nothing newer has been
       published.
     - ``https://api.github.com/repos/UWPR/Comet/releases?per_page=100`` --
       GitHub releases API, one request, newest by ``published_at``.
     - CONFIRMED
   * - Comet artefacts
     - Nine assets, no archive: ``comet.linux.exe`` (7 014 400 B),
       ``comet.aarch64.linux.exe`` (6 482 904 B), ``comet.macos.exe``
       (3 998 328 B), ``comet.aarch64.macos.exe`` (3 998 328 B),
       ``comet.win64.exe`` (3 452 416 B), ``CometWrapper.dll`` (4 411 392 B),
       ``ThermoFisher.CommonCore.Data.dll`` (406 016 B),
       ``ThermoFisher.CommonCore.RawFileReader.dll`` (650 752 B) and
       ``README.md`` (2 852 B). All eight assets the specification names are
       present; ``README.md`` is a ninth it does not list.
     - Same request as above -- the ``assets`` array of the ``v2026.02.2``
       release.
     - CONFIRMED, with one addition (:ref:`D1 <uf-d1>`)
   * - Comet binary linkage
     - ``comet.linux.exe`` (SHA-256
       ``af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e``,
       7 014 400 B) is ELF64, ``Type: EXEC``, x86-64. ``readelf -d`` reports
       *"There is no dynamic section in this file"* -- zero ``NEEDED`` entries,
       so statically linked. ``readelf -V`` shows no ``GLIBC_*`` symbol
       version requirements at all. Executed on this glibc 2.36 host and
       printed ``Comet version "2026.02 rev. 2 (6edec91)"``.
     - Downloaded from
       ``https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe``;
       ``readelf -d``, ``readelf -h``, ``readelf -V``; then executed.
     - CONFIRMED
   * - Comet parameter dump
     - ``comet -p`` emits **96** parameters; ``comet -q`` emits **118**.
       ``-q`` adds exactly 22 and removes none:
       ``variable_mod06``--``variable_mod15``, ``compoundmods_file``,
       ``mass_type_fragment``, ``mass_type_parent``, ``num_results``,
       ``peff_format``, ``peff_obo``, ``pinfile_protein_delimiter``,
       ``print_ascorepro_score``, ``print_expect_score``,
       ``protein_modslist_file``, ``spectral_library_ms_level``,
       ``spectral_library_name``. This is the specification's list exactly.
     - Executed ``comet.linux.exe -p`` and ``comet.linux.exe -q`` in separate
       scratch directories; counted ``name =`` lines above the
       ``[COMET_ENZYME_INFO]`` table. The only non-parameter lines in either
       file are that enzyme table and comments.
     - CONFIRMED
   * - Comet CLI
     - Nine options: ``-p``, ``-q``, ``-P<params>``, ``-N<name>``,
       ``-D<dbase>``, ``-F<num>``, ``-L<num>``, ``-i``, ``-j``. ``-N`` is
       documented "valid only with one input file" and ``-L`` "is required if
       ``-F`` is used". Input formats, verbatim: *"mzXML, mzML, Thermo raw,
       mgf, and ms2 variants (cms2, bms2, ms2)"*.
     - Executed ``comet.linux.exe`` with no arguments and parsed the usage
       block; the full text is in the JSON under ``comet-cli``.
     - CONFIRMED

Percolator
----------

.. list-table::
   :header-rows: 1
   :widths: 16 34 34 16

   * - Subject
     - Finding today (2026-08-29)
     - Source URL and method
     - Verdict vs specification
   * - Percolator current release
     - ``rel-3-09``, ``published_at`` 2026-05-21T17:25:43Z. Newest of 28
       releases; nothing newer.
     - ``https://api.github.com/repos/percolator/percolator/releases?per_page=100``
       -- GitHub releases API, one request. Independently corroborated by
       ``https://github.com/percolator/percolator/releases.atom``.
     - CONFIRMED
   * - Percolator XML removal
     - The ``rel-3-09`` notes contain the bullet *"Removed XML/XSD I/O
       support, which was incompatible with modern C++ toolchains. (#399)"*.
       The published body is hard-wrapped at ~80 columns and the break falls
       inside this sentence, so the bytes read ``...modern C++
       toolchains\n. (#399)``.
     - Same request -- the ``body`` field of the ``rel-3-09`` release.
       Compared both byte-exactly and with whitespace collapsed.
     - CONFIRMED in substance (:ref:`D2 <uf-d2>`)
   * - Percolator 3.08 XML artefacts
     - ``rel-3-08`` (2025-05-28) has **exactly five** assets:
       ``percolator-v3-08-linux-amd64.deb`` (4 327 966 B),
       ``percolator-noxml-v3-08-linux-amd64.deb`` (3 276 062 B),
       ``percolator-noxml-osx-portable.zip`` (531 661 B),
       ``percolator-noxml-windows-portable.zip`` (336 866 B) and
       ``percolator-converters-v3-08-linux-amd64.deb`` (6 578 548 B). The only
       XML-named ``percolator`` artefact is the Linux ``.deb``; the macOS and
       Windows archives are explicitly ``percolator-noxml-*``. The fifth asset
       is the separate *converters* package (``sqt2pin``/``msgf2pin``/
       ``tandem2pin``), not percolator itself.
     - Same request -- the full ``assets`` array of ``rel-3-08``.
     - CONFIRMED
   * - Newest XML-capable Percolator with binaries on all three tier-1
       platforms
     - **rel-3-07-01, published 2024-06-20**, publishing
       ``percolator-v3-07-linux-amd64.deb``,
       ``percolator-v3-07-osx-x86_64.pkg`` and ``percolator-v3-07.exe``. The
       two newer releases fail the test for different reasons: ``rel-3-08``
       publishes an XML build for Linux only, and ``rel-3-09`` publishes no
       XML build at all (the code is gone, so the ``noxml`` naming stopped and
       its ``percolator.exe`` / ``percolator-osx-portable.zip`` must not be
       read as XML-capable).
     - Same request -- every release's asset list, classified by upstream's
       ``percolator-*`` versus ``percolator-noxml-*`` naming A/B, with releases
       at or after ``rel-3-09`` treated as XML-incapable regardless of naming.
     - CONFIRMED
   * - Percolator XML on Windows/macOS
     - The newest XML-named builds for those two platforms remain
       ``percolator-v3-07.exe`` (1 818 841 B) and
       ``percolator-v3-07-osx-x86_64.pkg`` (2 122 306 B), both from
       ``rel-3-07-01`` (2024-06-20). Nothing newer exists on either platform.
     - Same request -- release asset lists.
     - CONFIRMED
   * - Newest XML-capable Percolator overall
     - ``rel-3-08-01`` is present in the tags API (commit
       ``febeef346327ff3adaf6712c7b8b250499aecc63``, dated 2025-07-08T08:32:01Z,
       subject *"Fixing a PEP>1.0 bug (#394)"*) and **absent** from the
       releases API, whose newest four tags are ``rel-3-09``, ``rel-3-08``,
       ``rel-3-07-01``, ``rel-3-07``. It therefore publishes no binary on any
       platform.
     - ``https://api.github.com/repos/percolator/percolator/tags?per_page=100``
       (50 tags) cross-checked against the releases API, plus
       ``.../commits/febeef34...`` for the tag date.
     - CONFIRMED
   * - Percolator XML is a build option
     - At ``rel-3-08`` and ``rel-3-08-01`` the byte-identical 4 917-byte
       ``CMakeLists.txt`` contains, verbatim, ``option(XML_SUPPORT "Choose to
       support xml input (slower compilation)." OFF)``, with 11 ``XML_SUPPORT``
       occurrences and 9 Xerces mentions each. At ``rel-3-09`` the file is
       3 541 bytes and contains **zero** occurrences of ``XML_SUPPORT`` and
       **zero** of ``xerces``.
     - ``https://raw.githubusercontent.com/percolator/percolator/<tag>/CMakeLists.txt``
       for each of the three tags -- ``raw.githubusercontent.com`` is not
       rate-limited.
     - CONFIRMED
   * - Bioconda Percolator
     - Latest ``bioconda::percolator`` is **3.9**, offered for ``linux-64`` and
       ``linux-aarch64`` only -- no macOS, no Windows, across all seven
       versions ever published. ``meta.yaml`` carries ``skip: True  # [osx]``
       verbatim. ``build.sh`` states: *"The XSD/Xerces-C based converters
       (sqt2pin, msgf2pin, tandem2pin) are intentionally not built ... Skipping
       it keeps this package free of the xerces-c / xsd dependency."*
     - ``https://api.anaconda.org/package/bioconda/percolator`` (not
       rate-limited) for platforms, plus the raw ``meta.yaml`` and ``build.sh``
       from ``bioconda-recipes``.
     - CONFIRMED

PDV, the Limelight converter and CasanovoGUI
--------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 16 34 34 16

   * - Subject
     - Finding today (2026-08-29)
     - Source URL and method
     - Verdict vs specification
   * - PDV current release
     - ``v2.7.0``, ``published_at`` 2026-08-14T19:55:27Z, single asset
       ``PDV-2.7.0.zip``, 103 407 417 B (98.6 MiB). The previous release is
       ``v2.6.0`` (2026-07-06), so 2.6.0 is indeed one behind. 37 releases in
       total; nothing newer than 2.7.0 today.
     - ``https://api.github.com/repos/wenbostar/PDV/releases?per_page=100``.
       Independently corroborated by
       ``https://github.com/wenbostar/PDV/releases.atom``.
     - CONFIRMED
   * - Limelight converter
     - ``yeastrc/limelight-import-comet-percolator``: licence field
       ``Apache-2.0`` (``Apache License 2.0``); newest of 20 releases is
       ``v2.8.1``, ``published_at`` 2025-08-19T21:41:30Z, with the single asset
       ``cometPercolator2LimelightXML.jar`` (2 762 075 B). Repository last
       pushed 2025-09-18.
     - ``https://api.github.com/repos/yeastrc/limelight-import-comet-percolator``
       for the licence field, and its ``/releases`` endpoint for the release.
     - CONFIRMED
   * - Limelight converter input
     - The README still opens with *"Requires that the Percolator output be
       represented as XML (see -X option in Percolator). Also requires that
       comet output be present as pepXML files."*, and documents ``-p,
       --percolator-file`` as *"Full path to percolator output XML file"*. A
       search of the whole README for ``tab-delimited``, ``tab separated`` or
       ``tsv`` returns **no matches**: no tab-delimited input path exists. The
       repository's last push (2025-09-18) predates ``rel-3-09`` (2026-05-21).
     - ``https://raw.githubusercontent.com/yeastrc/limelight-import-comet-percolator/master/README.md``,
       fetched and searched.
     - CONFIRMED
   * - Converter arguments
     - The README's command-line block documents ``-c/--comet-params``,
       ``-f/--fasta-file``, ``-p/--percolator-file``, ``-d/--pepxml-directory``,
       ``-q/--q-value``, ``-o/--out-file``, ``--import-decoys``,
       ``--independent-decoy-prefix``, ``--open-mod``, ``-v/--verbose``, and
       also ``-h/--help`` and ``-V/--version``. ``--import-decoys`` is
       documented as *"percolator must be run with -Z to output decoys"*.
     - Same README fetch. The converter's **executed** ``--help`` is unit 9's
       to run -- it needs a JDK, which does not exist on this host yet -- so
       this row quotes the help text the README embeds, not a live invocation.
     - CONFIRMED, with two options unlisted (:ref:`D3 <uf-d3>`)
   * - CasanovoGUI licence
     - ``Noble-Lab/CasanovoGUI``: repository API ``license`` field is
       ``null``; the recursive git tree of the default branch ``main`` has 106
       entries, is not truncated, and contains **no** file matching
       ``LICENSE``/``LICENCE``/``COPYING``/``NOTICE``/``UNLICENSE`` at any
       depth; top level is ``.gitattributes``, ``.github/``, ``.gitignore``,
       ``.run/``, ``README.md``, ``docs/``, ``packaging/``, ``pom.xml``,
       ``src/``. Language Java, created 2026-06-08, last pushed
       2026-08-21T07:27:28Z, public, not archived, not a fork.
     - ``https://api.github.com/repos/Noble-Lab/CasanovoGUI`` and
       ``https://api.github.com/repos/Noble-Lab/CasanovoGUI/git/trees/main?recursive=1``.
     - CONFIRMED

Rows deliberately not re-checked here
=====================================

Four rows of the specification's table describe downloading, extracting and
executing the Percolator artefacts. Another Phase 00 work unit owns each of
them, and re-doing the work here would duplicate a large download and collide
on files in the shared scratch tree. They are recorded as ``UNVERIFIED`` **by
this unit** -- which is not the same as unverified by the phase.

.. list-table::
   :header-rows: 1
   :widths: 26 20 54

   * - Specification row
     - Owned by
     - What this unit did establish
   * - Percolator 3.08 Linux binary
     - unit 3 -- ``docs/feasibility/percolator-artefacts.rst``
     - From release metadata only: ``percolator-v3-08-linux-amd64.deb`` exists
       and is 4 327 966 B. The glibc 2.38 / GLIBCXX 3.4.32 claim is unit 3's to
       prove.
   * - 3.07.1 Linux build, executed
     - unit 3 -- ``docs/feasibility/percolator-artefacts.rst``
     - From release metadata only: ``percolator-v3-07-linux-amd64.deb``
       (3 184 992 B) is published by ``rel-3-07-01``.
   * - 3.07.1 macOS build, extracted
     - unit 3 -- ``docs/feasibility/percolator-artefacts.rst``
     - From release metadata only: ``percolator-v3-07-osx-x86_64.pkg``
       (2 122 306 B) is published by ``rel-3-07-01``.
   * - 3.07.1 Windows build, inferred
     - unit 4 -- ``docs/feasibility/windows-artefact.rst``
     - The two asset sizes the specification's size argument rests on:
       ``percolator-v3-07.exe`` 1 818 841 B (1 776.2 KiB) against
       ``percolator-noxml-v3-07.exe`` 1 222 439 B (1 193.8 KiB), i.e. **+48.8
       %**. The specification's "1776 KB against 1193 KB (+49%)" is arithmetically
       correct. The NSIS payload itself was not opened here.

.. _uf-differences:

Differences from specification.rst
==================================

Every place today's finding contradicts or exceeds the specification's wording
is listed here, with the two side by side. **Three differences were found.
None of them changes a decision, a version choice or a capability claim** --
they are a missing asset, a quotation artefact and an incomplete option list.
No row was found to contradict the specification on substance.

This unit may not edit ``specification.rst``; these are for the phase
orchestrator to escalate.

.. _uf-d1:

D1 -- Comet artefacts: the release also publishes ``README.md``
---------------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 14 43 43

   * - Field
     - specification.rst (2026-08-28)
     - Observed 2026-08-29
   * - Wording
     - "Standalone executables, no archive: ``comet.linux.exe``,
       ``comet.aarch64.linux.exe``, ``comet.macos.exe``,
       ``comet.aarch64.macos.exe``, ``comet.win64.exe``, plus
       ``CometWrapper.dll``, ``ThermoFisher.CommonCore.Data.dll`` and
       ``ThermoFisher.CommonCore.RawFileReader.dll`` companions for Thermo RAW
       on Windows."
     - All eight of those assets are present. The release carries a **ninth**
       asset, ``README.md`` (2 852 B), which the specification does not
       mention.
   * - Severity
     - --
     - Cosmetic. Nothing in the product downloads ``README.md``. It matters
       only if a later phase asserts "the release has exactly eight assets" as
       a manifest invariant, which would then fail.

A second observation from the same asset list, not a contradiction because the
specification says nothing about it: ``comet.macos.exe`` and
``comet.aarch64.macos.exe`` are **both exactly 3 998 328 bytes**. Two
differently-named macOS assets of identical size are worth a second look by
whichever phase writes the download manifest -- they may be the same universal
binary published twice, or a packaging slip upstream. Neither was downloaded
here (no macOS host), so this is an observation, not a finding.

.. _uf-d2:

D2 -- Percolator XML removal: "verbatim" quote is a reflow
----------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 14 43 43

   * - Field
     - specification.rst (2026-08-28)
     - Observed 2026-08-29
   * - Wording
     - "Confirmed **verbatim** in the ``rel-3-09`` release notes: *'Removed
       XML/XSD I/O support, which was incompatible with modern C++
       toolchains. (#399)'*"
     - The sentence is present and says exactly that, but the published body
       is hard-wrapped at ~80 columns and the wrap falls mid-sentence: the
       actual bytes are ``* Removed XML/XSD I/O support, which was
       incompatible with modern C++ toolchains\n. (#399)``. A byte-exact
       search for the specification's string fails; a whitespace-collapsed
       search succeeds.
   * - Severity
     - --
     - Cosmetic, but it is the reason an automated re-verification of this row
       must normalise whitespace. The script does, and records both results
       (``exact_byte_match_against_specification_quote`` false,
       ``whitespace_normalised_match`` true). The specification's claim is
       true in substance; only the word "verbatim" overstates it.

.. _uf-d3:

D3 -- Converter arguments: two documented options are not listed
-----------------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 14 43 43

   * - Field
     - specification.rst (2026-08-28)
     - Observed 2026-08-29
   * - Wording
     - "``-c/--comet-params``, ``-f/--fasta-file``, ``-p/--percolator-file``,
       ``-d/--pepxml-directory``, ``-q/--q-value``, ``-o/--out-file``,
       ``--import-decoys`` (which *requires* Percolator to have been run with
       ``-Z``), ``--independent-decoy-prefix``, ``--open-mod``, ``-v``."
     - Every one of those is documented and unchanged. The README's option
       block also documents ``-h, --help`` and ``-V, --version``, which the
       specification's list omits. (``-v`` in the specification is the
       README's ``-v, --verbose``.)
   * - Severity
     - --
     - Cosmetic. ``--help`` and ``--version`` are picocli defaults. Worth
       noting only because unit 9, which runs the JAR's real ``--help``, should
       expect to see twelve options rather than the ten listed.

Nothing else differed
---------------------

Beyond D1--D3, **no difference from the specification was found**. Every
version number, date, asset name, asset size, parameter count, option name,
licence field, platform list and quoted string that this unit could reach
matched the specification's table.

In particular, and against expectation given the PDV row's history: the PDV
row is **still correct today**. ``v2.7.0`` published 2026-08-14 remains the
newest of 37 releases, with ``PDV-2.7.0.zip`` at 103 407 417 B (98.6 MiB), and
``v2.6.0`` (2026-07-06) is still exactly one release behind.

Bearing on the Percolator decision
==================================

This unit did not re-derive *latest compatible* -- that is unit 3's work, and
it needs the artefacts executed, not just listed. What the release metadata
alone establishes today, independently of the specification:

#. ``rel-3-09`` (2026-05-21) is current and its own release notes say XML/XSD
   I/O was removed; its ``CMakeLists.txt`` no longer mentions ``XML_SUPPORT``
   or Xerces at all.
#. ``rel-3-08`` publishes an XML-named build for Linux only, out of exactly
   five assets.
#. ``rel-3-08-01`` is a tag with no release, so it ships no binary anywhere.
#. ``rel-3-07-01`` (2024-06-20) is the newest release publishing an XML-named
   build for Linux **and** macOS **and** Windows.
#. The Limelight converter still hard-requires Percolator XML and offers no
   tab-delimited path.

Those five observations are consistent with the specification's resolution of
**3.07.1** for a Limelight-enabled run (``D-002``). They do not by themselves
prove it: naming is evidence of XML capability, not proof, and the proof is the
executed ``--help`` showing ``-X/--xmloutput``, which units 3 and 4 own. This
document records the metadata half of that argument only.

Reproducing this document
=========================

::

   python3 scripts/feasibility/verify_upstream_facts.py --refresh
   bash scripts/feasibility/check-docs.sh docs/feasibility/upstream-facts.rst

The first command re-fetches everything live (8 GitHub API requests) and
rewrites ``scratch/upstream/facts-2026-08-29.json``. Drop ``--refresh`` to run
entirely from ``scratch/apicache/`` at no quota cost. The second builds this
file with ``sphinx-build -n -W``.
