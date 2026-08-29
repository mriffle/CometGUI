.. _pa-percolator-artefacts:

==================================================================
Percolator artefact availability, extraction and host requirements
==================================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 3 of 9 -- Percolator artefact enumeration and payload extraction
:Serves exit gate items: 6, 7, 9 (and evidence for 3 and 10)
:Contributes to: ``R-PERC-01``, ``R-PERC-02``, ``R-PERC-03``, ``R-PERC-10``,
                 ``R-PERC-11``, ``R-TOOL-02``, ``R-PLAT-02``
:Verified on: 2026-08-29
:Host: Debian 12, x86-64, glibc 2.36 (``ldd (Debian GLIBC 2.36-9+deb12u14)``),
       Python 3.11.2. No ``dpkg``, ``rpm``, ``rpm2cpio``, ``cpio``, ``xar``,
       ``7z``, ``unzip``, ``bsdtar`` or ``file``; nothing was installed on the
       host and no command was run with ``sudo``.

.. contents:: Contents
   :depth: 2
   :local:

Scope
=====

This document answers, per tier-1 platform: which XML-capable Percolator
artefact the product uses, its exact URL and SHA-256, how its payload is
extracted without administrative rights, what host requirements that payload
imposes, and where the XSD companion files live inside it. It then re-derives
*latest compatible* from live upstream data rather than accepting the
specification's answer, and costs the options still open under ``D-003``.

Not in scope here, and owned elsewhere:

* The Windows NSIS ``.exe`` payload -- its extraction and its capability
  evidence belong to a separate work unit and to
  ``docs/feasibility/windows-artefact.rst``. This document names the Windows
  artefact and states plainly what is **not** established about it.
* Comet, PDV, the Limelight converter and CasanovoGUI.
* The scripted end-to-end scientific run (exit gate items 2 and 3). The runs
  recorded here are capability probes on synthetic input, not that proof.
* Answering ``D-003``. The options below carry costs; the choice is the
  owner's.

Summary: the per-platform answer
================================

.. list-table:: XML-capable Percolator artefact per tier-1 platform, as verified today
   :header-rows: 1
   :widths: 12 25 15 24 24

   * - Platform
     - Artefact (release ``rel-3-07-01``)
     - Container
     - Extraction without admin rights
     - Host requirement imposed
   * - Linux x86-64
     - ``percolator-v3-07-linux-amd64.deb``
     - ``ar`` + ``data.tar.gz``
     - **Verified here.** ``scripts/feasibility/extract_deb.py``; payload is a
       relative ``./usr`` tree
     - ``GLIBC_2.34``, ``GLIBCXX_3.4.29``, ``CXXABI_1.3.13``, ``GCC_3.0``,
       ``GOMP_4.0``. **Loads and runs on this glibc 2.36 host.**
   * - macOS
     - ``percolator-v3-07-osx-x86_64.pkg``
     - ``xar!`` + gzip + ``070707`` cpio
     - **Verified here.** ``scripts/feasibility/extract_pkg.py``; payload is a
       relative ``./usr/local`` tree
     - Mach-O 64-bit **x86-64 only**, ``LC_BUILD_VERSION`` minimum macOS
       **12.7**. Apple silicon needs Rosetta 2 (``D-004``). **Not executable
       on this Linux host.**
   * - Windows x64
     - ``percolator-v3-07.exe``
     - NSIS installer
     - **Not established by this work unit.** Owned by the Windows artefact
       work unit
     - **Not established.** No Windows runner was used and the binary has
       never been executed.

The one sentence that matters for the manifest: **Linux and macOS artefacts
are confirmed; the Windows artefact's XML capability is still an inference and
must not be recorded as verified.**

Reproducing everything in this document
=======================================

Three re-runnable, standard-library-only Python 3.11 scripts, plus one written
because the 3.09 investigation needed it::

   scripts/feasibility/enumerate_percolator_releases.py   # upstream enumeration + derivation
   scripts/feasibility/extract_deb.py                     # ar + tar, pure Python
   scripts/feasibility/extract_pkg.py                     # xar + cpio + Mach-O, pure Python
   scripts/feasibility/extract_rpm.py                     # rpm + cpio, pure Python
   scripts/feasibility/probe_xml_capability.py            # static XML-capability verdict

The extractors are pure Python because the shipping product performs this
extraction inside the JVM, so a shell-out to ``dpkg`` or ``xar`` would prove
nothing about the product -- and because none of those tools exist on this
host. Only the decompressors come from the standard library (``zlib``,
``gzip``, ``bz2``, ``lzma``), each of which has a JDK equivalent. The ``ar``,
``tar``, ``xar``, ``cpio``, ``rpm`` and Mach-O parsing is implemented in the
scripts.

Typical invocations::

   python3 scripts/feasibility/enumerate_percolator_releases.py
   python3 scripts/feasibility/extract_deb.py --control --dest OUT PKG.deb
   python3 scripts/feasibility/extract_pkg.py --identify --dest OUT PKG.pkg
   python3 scripts/feasibility/extract_rpm.py --requires --dest OUT PKG.rpm
   python3 scripts/feasibility/probe_xml_capability.py OUT/usr/bin/percolator

Downloads and extracted trees live under ``/workspace/scratch/percolator/``,
which is gitignored. No binary is committed.

.. _pa-derivation:

Deriving *latest compatible* from upstream data
===============================================

Exit gate item 7 requires this to be established from upstream data with the
evidence recorded, not assumed from the specification. It was re-derived
independently and the specification's answer was **confirmed**.

Source
------

``https://api.github.com/repos/percolator/percolator/releases?per_page=100``
returned 28 releases in a single page (28 < 100, so no pagination), cached
under ``/workspace/scratch/apicache/``. A second call to
``.../tags?per_page=100`` returned 50 tags. The script also implements a
rate-limit-free fallback -- the ``releases.atom`` feed plus one
``releases/expanded_assets/<tag>`` fetch per tag -- selectable with
``--source html``; the API path was the one actually used, and the JSON output
records which.

The fallback was exercised as a cross-check and independently returns the same
answer, from a different endpoint. It has two limitations that its output
declares rather than hides: the atom feed carries only the ten most recent
releases (enough here, since the answer lies inside them), and it carries no
release notes, so the "release notes say XML/XSD I/O was removed" rule cannot
fire. Under the fallback the strict derivation still returns 3.7.1, but the
deliberately optimistic one -- the variant that treats "unknown from naming" as
capable -- returns 3.9, because without the release notes ``rel-3-09``'s
unlabelled assets have nothing to rule them out. On the API path, where the
notes are available, both variants return 3.7.1. That divergence is the
argument for preferring the API path and for keeping the release-notes rule.

Rules, applied to data and not to version numbers
-------------------------------------------------

The classification is computed in
``scripts/feasibility/enumerate_percolator_releases.py``; no version number is
hard-coded anywhere in the derivation.

platform
   From the asset name, with macOS markers beating Windows markers beating
   Linux markers, so that ``percolator-v3-07-osx-x86_64.pkg`` classifies as
   macOS and not as Linux on its ``x86_64`` substring.

component
   ``converters`` when the name contains ``converters`` -- those are the
   ``sqt2pin``/``msgf2pin``/``tandem2pin`` family, not the ``percolator``
   binary. Only ``percolator``-component assets count towards coverage.

XML capability
   ``noxml`` in the asset name means no; release notes stating that XML/XSD
   I/O was removed mean no for every asset in that release; the presence in
   the same release of the ``noxml`` twin of exactly this asset name means
   yes, because that pairing *is* upstream's ``-DXML_SUPPORT=ON`` /
   ``OFF`` A/B; anything else is unknown from naming alone.

The rule
   The newest release publishing an XML-capable ``percolator`` binary for all
   three tier-1 platforms, under the project's two fixed constraints: the
   project does not build Percolator from source, and a version is a candidate
   only where upstream publishes a binary for that platform.

Result
------

.. list-table:: Derivation trace, newest first (from the script's output)
   :header-rows: 1
   :widths: 16 10 12 62

   * - Release
     - Version
     - Qualifies
     - Why
   * - ``rel-3-09``
     - 3.9
     - no
     - Release notes: "Removed XML/XSD I/O support ... (#399)". No XML-capable
       asset on any platform.
   * - ``rel-3-08``
     - 3.8
     - no
     - Linux ``.deb`` is XML-capable, but the only Windows and macOS assets are
       ``percolator-noxml-windows-portable.zip`` and
       ``percolator-noxml-osx-portable.zip``.
   * - ``rel-3-07-01``
     - 3.7.1
     - **yes**
     - ``percolator-v3-07-linux-amd64.deb``, ``percolator-v3-07.exe`` and
       ``percolator-v3-07-osx-x86_64.pkg``, each with a published ``noxml``
       twin.
   * - ``rel-3-07``
     - 3.7
     - no
     - Publishes zero assets. Superseded by ``rel-3-07-01``, whose release note
       is "Fixing the incorrect naming of the rel-3-07 release".
   * - ``rel-3-06-05``
     - 3.6.5
     - yes
     - Also qualifies, but is older.

**Computed answer: 3.7.1 (tag** ``rel-3-07-01``\ **, published 2024-06-20).
This matches the specification's 3.07.1.**

The derivation was run twice: strictly, and again treating "unknown from
naming" as capable. Both return 3.7.1, so the answer does not depend on how
the unlabelled assets are read.

Why nothing newer can qualify
-----------------------------

* ``rel-3-08-01`` (3.8.1) exists as a **tag only** -- commit ``febeef3463``
  appears in the tag list and no release object carries that tag, so it
  publishes no binary on any platform. Confirmed against both the releases and
  the tags endpoints.
* ``rel-3-09`` removed the XML code outright, in its own release notes and in
  its binaries: the extracted 3.09 Linux payload contains **no** XSD files at
  all, and its binary contains none of the XML option strings (see
  :ref:`pa-noxml-trap`).

Consequently the only way to a Percolator newer than 3.7.1 that emits XML is a
source build, which the project has ruled out.

.. _pa-noxml-trap:

How XML capability is actually decided -- and a trap in the current evidence
============================================================================

Upstream builds each pre-3.09 release twice from one source tree,
``-DXML_SUPPORT=ON`` and the default ``OFF``, and publishes the second under a
``noxml`` name. The option guards the Xerces-C/XSD code -- **not** the option
parser. Verified by byte-scanning both halves of the ``rel-3-07-01`` macOS
pair:

.. list-table:: String markers in the two halves of one A/B pair (3.07.1 macOS)
   :header-rows: 1
   :widths: 34 22 22 22

   * - Marker
     - XML build
     - ``noxml`` twin
     - Discriminates?
   * - ``xercesc``
     - 5996
     - 0
     - **yes -- positive**
   * - ``Compiler flag XML_SUPPORT was off``
     - 0
     - 1
     - **yes -- negative**
   * - ``xmloutput``
     - 1
     - 1
     - no
   * - ``decoy-xml-output``
     - 1
     - 1
     - no
   * - ``pout.xml``
     - 2
     - 2
     - no
   * - ``percolator_out.xsd``
     - 1
     - 1
     - no

The ``noxml`` binary's own diagnostic reads::

   ERROR: Compiler flag XML_SUPPORT was off, you cannot use the -k flag for
   pin-format input files

**Consequence for the existing record.** ``DECISIONS.rst`` records the macOS
half of ``D-002`` as evidenced by "the strings ``xerces``, ``xmloutput``,
``pout.xml``, ``decoy-xml-output``". Three of those four are present in the
``noxml`` build too, so only ``xerces`` was carrying the argument. The
conclusion is right and is independently confirmed above, but the evidence as
written is weaker than it appears, and anyone reasoning about the Windows
artefact from string presence should use ``xercesc`` or the ``XML_SUPPORT was
off`` diagnostic and nothing else.

``scripts/feasibility/probe_xml_capability.py`` implements exactly this and
was run against nine extracted binaries. Its verdicts agree with upstream's
naming in every case where a name exists, and resolve the cases where none
does:

.. list-table:: Static XML-capability verdicts (nine extracted binaries)
   :header-rows: 1
   :widths: 40 14 12 34

   * - Binary
     - ``xercesc``
     - ``XML_SUPPORT off``
     - Verdict
   * - 3.05 Linux ``percolator-v3-05-...deb``
     - 10680
     - 0
     - XML-capable
   * - 3.05 macOS ``percolator-v3-05-...pkg``
     - 1833
     - 0
     - XML-capable
   * - 3.07.1 Linux ``.deb``
     - 10304
     - 0
     - XML-capable
   * - 3.07.1 macOS ``.pkg``
     - 5996
     - 0
     - XML-capable
   * - 3.07.1 macOS ``noxml`` ``.pkg``
     - 0
     - 1
     - not XML-capable
   * - 3.07.1 Linux ``noxml-ubuntu-portable.zip``
     - 0
     - 1
     - not XML-capable
   * - 3.08.0 Linux ``.deb``
     - 796
     - 0
     - XML-capable
   * - 3.09 Linux ``.deb``
     - 0
     - 0
     - not XML-capable (no XML option strings at all)
   * - 3.09 macOS ``osx-portable.zip``
     - 0
     - 0
     - not XML-capable (no XML option strings at all)

This is static evidence. It is strong enough to contradict a claim; it is not
a substitute for running ``--help`` on the target platform, which is why the
Windows row of the summary table stays blank.

Linux x86-64 -- verified end to end
===================================

Artefact
--------

:Name: ``percolator-v3-07-linux-amd64.deb``
:URL: ``https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07-linux-amd64.deb``
:Size: 3 184 992 bytes (matches the size the release API reports)
:SHA-256: ``68cd3a4b60845d1399cc84e2e1acaef7044d89c46161009939bcb97af90d48c7``
:Control: ``Package: percolator``, ``Version: 3.07.1``, ``Architecture: amd64``,
          ``Installed-Size: 8492``

Extraction without administrative rights
----------------------------------------

The ``.deb`` is a Unix ``ar`` archive of three members::

   debian-binary                         4 bytes
   control.tar.gz                      857 bytes
   data.tar.gz                     3183942 bytes

``data.tar.gz`` inflates to 8 675 328 bytes of tar holding twelve entries, all
relative to ``./usr``. Nothing is absolute, nothing escapes the destination
(the extractor refuses members that would), and no maintainer script is run --
the ``control.tar.gz`` member is read for metadata only. Command::

   python3 scripts/feasibility/extract_deb.py \
       --dest /workspace/scratch/percolator/3.07.1-linux-x86_64 \
       /workspace/scratch/percolator/percolator-v3-07-linux-amd64.deb

Payload, with the file SHA-256 of each executable:

.. list-table:: Extracted 3.07.1 Linux payload
   :header-rows: 1
   :widths: 46 10 44

   * - Path inside the payload
     - Mode
     - SHA-256 / size
   * - ``./usr/bin/percolator``
     - 0755
     - ``83f594c3abbae40c14b78124a0895e679a03e22aa6b5b70f794ba62901cae48c``
   * - ``./usr/bin/qvality``
     - 0755
     - ``39a7c046fada80b794159029cb2dc895197a049f8168d78cf987d418e2290c14``
   * - ``./usr/bin/gtest_unit``
     - 0755
     - ``67f1d8a7e1946d78532e9df41cec0f41666987790cc6c4261e49a5ebd0863153``
   * - ``./usr/share/xml/percolator/xml-pout-1-5/percolator_out.xsd``
     - 0444
     - ``21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865``
       (10 388 bytes)
   * - ``./usr/share/xml/percolator/xml-pin-1-3/percolator_in.xsd``
     - 0444
     - ``fa50a550ea01c9109197ad2c8c9efdcdad448fddd81c5ddcf54f13f8af280f4f``
       (15 457 bytes)

Execution on this host
----------------------

The extracted binary was executed. Banner::

   Percolator version 3.07.1, Build Date Jun 20 2024 13:21:20
   Copyright (c) 2006-9 University of Washington. All rights reserved.

``percolator --help`` contains both XML options. The exact lines, verbatim::

    -X <filename>
    --xmloutput <filename>                 Path to xml-output (pout) file.

    -Z
    --decoy-xml-output                     Include decoys (PSMs, peptides and/or
                                           proteins) in the xml-output. Only
                                           available if -X is set.

and the usage line itself is ``percolator [-X pout.xml] [other options]
pin.tsv``.

The binary was then driven with ``-X`` on a synthetic 1000-row PIN (500 target,
500 decoy, two features) purely as a capability probe. It exited 0 and wrote a
740 259-byte ``pout.xml`` opening::

   <?xml version="1.0" encoding="UTF-8"?>
   <percolator_output
   xmlns="http://per-colator.com/percolator_out/15"
   ...
   p:majorVersion="3" p:minorVersion="07" p:percolator_version="Percolator version 3.07.1">

The emitted namespace ``http://per-colator.com/percolator_out/15`` is exactly
the ``targetNamespace`` of the ``percolator_out.xsd`` shipped in the same
payload. This is namespace agreement, **not** schema validation -- the payload
was not validated against the XSD, and no real spectra were involved. The
scientific proof remains a separate work unit's.

Host requirements
-----------------

``readelf -d`` gives five ``NEEDED`` entries and no Xerces among them --
``libstdc++.so.6``, ``libm.so.6``, ``libgomp.so.1``, ``libgcc_s.so.1``,
``libc.so.6`` -- so Xerces-C is statically linked, and the binary carries no
dependency the base system does not already provide.

Highest required symbol version per library, from ``readelf -V``:

.. list-table:: 3.07.1 Linux symbol-version floors
   :header-rows: 1
   :widths: 30 20 50

   * - Library
     - Highest required
     - Note
   * - ``libc.so.6``
     - ``GLIBC_2.34``
     - satisfied by Debian 12 (2.36), Ubuntu 22.04 (2.35), RHEL 9 (2.34)
   * - ``libstdc++.so.6``
     - ``GLIBCXX_3.4.29``
     - RHEL 9's system ``libstdc++`` provides exactly ``3.4.29`` -- no margin
   * - ``libstdc++.so.6``
     - ``CXXABI_1.3.13``
     - same library
   * - ``libgcc_s.so.1``
     - ``GCC_3.0``
     - trivially satisfied
   * - ``libgomp.so.1``
     - ``GOMP_4.0`` / ``OMP_3.0``
     - OpenMP runtime, present with GCC

The specification records ``GLIBC_2.34`` and is correct. It does not record
``GLIBCXX_3.4.29``, which is the tighter of the two on RHEL 9 and is what a
loadability probe should actually look at.

macOS -- extracted and identified, not executed
===============================================

Artefact
--------

:Name: ``percolator-v3-07-osx-x86_64.pkg``
:URL: ``https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07-osx-x86_64.pkg``
:Size: 2 122 306 bytes
:SHA-256: ``ad826425f932ab55651649981925c8a52fc9d19330e230c3710ebf9ce85ed4f1``

Extraction without administrative rights
----------------------------------------

Three nested containers, all handled in pure Python::

   xar!    version 1, 28-byte header, sha1 checksums
           TOC: 1276 bytes deflate -> 5611 bytes of XML
     Payload  2 117 417 bytes, gzip
       cpio   6 297 088 bytes, magic 070707 (old ASCII), 14 entries

The xar table of contents lists ``Distribution``, three ``Resources/`` files,
and the inner ``percolator-v3-07-osx-x86_64-Unspecified.pkg/`` holding ``Bom``,
``PackageInfo``, ``Scripts`` and ``Payload``. Only ``Payload`` is unpacked;
``Scripts`` is never run and ``installer(8)``, ``pkgutil`` and root are not
involved. Command::

   python3 scripts/feasibility/extract_pkg.py --identify \
       --dest /workspace/scratch/percolator/3.07.1-macos-x86_64 \
       /workspace/scratch/percolator/percolator-v3-07-osx-x86_64.pkg

Payload, relative to ``./usr/local``:

.. list-table:: Extracted 3.07.1 macOS payload
   :header-rows: 1
   :widths: 52 10 38

   * - Path inside the payload
     - Mode
     - SHA-256
   * - ``./usr/local/bin/percolator``
     - 0755
     - ``b00536476eb924282b8d76fae75244cfb7c1fe78ec9e25a372158f16bc6065bf``
   * - ``./usr/local/bin/qvality``
     - 0755
     - ``9a57b7a805e1a0809e67fd7693b187fe9a0a0380d19e96989d1544876d997bc3``
   * - ``./usr/local/bin/gtest_unit``
     - 0755
     - ``557679d16016c84b91e2590a7741fc33d9a65bcc3f68764388ca51880a92fadc``
   * - ``./usr/local/share/xml/percolator/xml-pout-1-5/percolator_out.xsd``
     - 0444
     - ``21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865``
   * - ``./usr/local/share/xml/percolator/xml-pin-1-3/percolator_in.xsd``
     - 0444
     - ``fa50a550ea01c9109197ad2c8c9efdcdad448fddd81c5ddcf54f13f8af280f4f``

Note the prefix differs from Linux: ``/usr/local/share`` on macOS against
``/usr/share`` on Linux. Anything that locates the XSDs by path must be
per-platform (``R-TOOL-02``).

Identification without ``file`` and without executing it
--------------------------------------------------------

``extract_pkg.py`` parses the Mach-O header itself. For
``./usr/local/bin/percolator``::

   format      : Mach-O 64-bit
   arch        : x86_64          (cputype 0x01000007, cpusubtype 3)
   filetype    : MH_EXECUTE
   platform    : macOS
   min_macos   : 12.7.0          (LC_BUILD_VERSION)
   sdk         : 13.1.0
   dylib       : /usr/lib/libcurl.4.dylib
   dylib       : /usr/lib/libc++.1.dylib
   dylib       : /usr/lib/libSystem.B.dylib

There is **one** architecture slice: this is not a universal binary. Its only
dynamic dependencies are macOS system libraries, so Xerces-C is statically
linked here too.

**This binary cannot be executed on this host.** It is a Mach-O image for
Darwin/x86-64 and this is Linux; nothing in this document should be read as
having run it. Its XML capability on macOS rests on the static verdict in
:ref:`pa-noxml-trap` (5996 ``xercesc`` occurrences, no ``XML_SUPPORT was off``
diagnostic) and on upstream's A/B naming, both of which are consistent, and on
the fact that the same release's Linux half -- built from the same tree with
the same option -- was executed and does emit XML. Running
``percolator --help`` on a Mac is still outstanding.

Two host requirements follow, both newer than the specification records:

* **x86-64 only.** On Apple silicon this stage runs under Rosetta 2, which the
  application must detect and explain (``D-004``).
* **Minimum macOS 12.7 (Monterey).** ``LC_BUILD_VERSION`` says so. On macOS 11
  or older this artefact will not launch at all, which is a manifest fact the
  specification does not yet carry.

Windows -- named only, deliberately unverified here
===================================================

.. warning::

   Nothing in this section is verified. This work unit did not download,
   extract or execute the Windows artefact, and no Windows runner was used.

The XML-capable Windows artefact of ``rel-3-07-01`` is
``percolator-v3-07.exe``, 1 818 841 bytes, at
``https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07.exe``.
Its ``noxml`` twin ``percolator-noxml-v3-07.exe`` is 1 222 439 bytes, so the
XML half is 49% larger -- the same direction and rough magnitude as the size
gap verified directly on the other two platforms (macOS: 2 122 306 against
999 831 bytes).

That is naming and size. It is **not** confirmation. As
:ref:`pa-noxml-trap` shows, even a string scan of the payload can mislead
unless it uses ``xercesc`` or the ``XML_SUPPORT was off`` diagnostic. Payload
extraction from the NSIS installer, ``percolator --help`` on Windows, and the
presence of ``-X`` and ``-Z`` there are the Windows work unit's to establish
(``docs/feasibility/windows-artefact.rst``, exit gate item 8). **The manifest
must not claim Windows XML capability until that unit reports it.**

Percolator 3.09 on Linux: no self-contained artefact exists
===========================================================

This was found while preparing the 3.09 material a later work unit needs, and
it contradicts an assumption in ``D-003``.

Neither upstream Linux 3.09 package can run as shipped, for different reasons.

.. list-table:: 3.09 Linux artefacts as published
   :header-rows: 1
   :widths: 30 20 50

   * - Artefact
     - Symbol floor
     - Why it does not run as shipped
   * - ``percolator-v3-09-linux-amd64.deb``
     - ``GLIBC_2.38``, ``GLIBCXX_3.4.32``
     - Two failures. The loader rejects it on any host below glibc 2.38, and
       it links ``libboost_filesystem.so.1.83.0``, which the package does not
       contain. Its ``control`` file declares **no** ``Depends:`` field at
       all, so ``dpkg -i`` would install it and it would then fail at run
       time.
   * - ``percolator-v3-09-linux-x86_64.rpm``
     - ``GLIBC_2.14``, ``GLIBCXX_3.4.21``
     - Much more portable, but links ``libboost_filesystem.so.1.66.0`` and
       ``libboost_system.so.1.66.0``, neither of which the package contains.
       The RPM at least declares both in ``REQUIRENAME``.

3.09 publishes **no Linux portable archive at all** -- ``rel-3-09``'s only
portable asset is ``percolator-osx-portable.zip``. ``D-003``'s note that 3.09
"is current and its portable archives need no payload extraction" is therefore
true on macOS and false on Linux.

The exact loader errors observed on this glibc 2.36 host::

   percolator: /lib/x86_64-linux-gnu/libstdc++.so.6: version `GLIBCXX_3.4.32'
       not found (required by .../3.09/...-from-deb/usr/bin/percolator)
   percolator: /lib/x86_64-linux-gnu/libc.so.6: version `GLIBC_2.38' not found
       (required by .../3.09/...-from-deb/usr/bin/percolator)

   percolator: error while loading shared libraries:
       libboost_filesystem.so.1.66.0: cannot open shared object file:
       No such file or directory

A loadability probe that only checks the exit status will see 1 in the first
case and 127 in the second and must treat both as "does not load"
(``R-PERC-01``).

The 3.09 payload contains **no XSD files whatsoever** -- confirming the XML
removal from the artefact rather than from the release notes alone. 3.08.0's
payload still contains both.

Making 3.09 runnable here, and what that implies
------------------------------------------------

The RPM binary was made to run by supplying the two Boost shared objects it
needs from the CentOS 8.5.2111 AppStream packages
``boost-filesystem-1.66.0-10.el8.x86_64.rpm``
(``42d08264afce5902a7feb069b4c996dce4326085db882e43e58f6ad02e1928d0``) and
``boost-system-1.66.0-10.el8.x86_64.rpm``
(``1c3a384d37f1e1fc52ba78fb904ebba34a919e7057dc3eaba821814676feba52``),
extracted with the same pure-Python RPM extractor and reached through
``LD_LIBRARY_PATH``. Nothing was installed on the host. Boost is under the
Boost Software License 1.0, shipped inside those packages.

That is an evidence-gathering measure, not a product decision. **Whether
CometGUI redistributes Boost, or restricts managed 3.09 on Linux to hosts that
already have it, is part of** ``D-003``.

With Boost supplied, ``percolator --help`` from the 3.09 RPM payload reports::

   Percolator version 3.09.0, Build Date May 21 2026 17:16:38
   ...
   Usage:
      percolator [other options] pin.tsv

Note the usage line has lost ``[-X pout.xml]``. The full 280-line help contains
no ``-X``, no ``-Z``, no ``--xmloutput``, no ``--decoy-xml-output`` and no
``--stdinput-xml``; the only match for "xml" anywhere in it is
``--pepxml-output``, which is a different feature. Passing ``-X`` gives::

   ERROR: the option -X is invalid.
   Please run "command --help."

The same synthetic PIN produced PSM and weights output and no XML file. That
is a capability probe, not exit gate item 3, which needs the real scientific
path and a peptide-level output check.

Assets left for the next work unit
==================================

All paths absolute, all under the gitignored scratch tree.

.. list-table:: Extracted trees left in place
   :header-rows: 1
   :widths: 46 54

   * - Path
     - Contents and state
   * - ``/workspace/scratch/percolator/3.07.1-linux-x86_64/``
     - 3.07.1 Linux payload: ``usr/bin/percolator``, ``qvality``,
       ``gtest_unit`` and both XSDs under
       ``usr/share/xml/percolator/``. **Runs on this host, emits XML.**
   * - ``/workspace/scratch/percolator/3.07.1-macos-x86_64/``
     - 3.07.1 macOS payload under ``usr/local/``. Not executable here.
   * - ``/workspace/scratch/percolator/3.08.0-linux-x86_64/``
     - 3.08.0 Linux payload. Both XSDs present; binary does not load here.
   * - ``/workspace/scratch/percolator/3.09/linux-x86_64-from-rpm/``
     - 3.09.0 Linux payload from the RPM. **Runs**, via the wrapper below.
   * - ``/workspace/scratch/percolator/3.09/deps/usr/lib64/``
     - ``libboost_filesystem.so.1.66.0``, ``libboost_system.so.1.66.0`` and
       their licences.
   * - ``/workspace/scratch/percolator/3.09/run-percolator-3.09.sh``
     - Wrapper that sets ``LD_LIBRARY_PATH`` and execs the 3.09 binary.
       Verified to print the 3.09.0 banner.
   * - ``/workspace/scratch/percolator/3.09/linux-x86_64-from-deb-DOES-NOT-LOAD/``
     - 3.09.0 Linux payload from the ``.deb``, kept as the evidence for the
       ``GLIBC_2.38`` finding. Named so it cannot be picked up by accident.
   * - ``/workspace/scratch/percolator/releases.json``
     - Full machine-readable enumeration and both derivations.
   * - ``/workspace/scratch/percolator/SHA256SUMS.txt``
     - Checksums of every artefact downloaded by this work unit.

Costed options for ``D-003``
============================

``D-003`` asks which Percolator version/platform pairs beyond 3.07.1 the
product offers as managed one-click installs. **This work unit does not answer
it.** The options below carry the costs the evidence above establishes.

One fact narrows the whole question: ``percolator_out.xsd`` and
``percolator_in.xsd`` are **byte-identical** across 3.05, 3.07.1 and 3.08.0
(``21204c89...`` and ``fa50a550...``). Schema- and adapter-level support across
the entire XML-capable range costs nothing extra; only *managed installation*
differs per pair.

Option 1 -- 3.07.1 only (the status quo after ``D-002``)
--------------------------------------------------------

*Cost:* no new engineering. Every user, including those who never touch
Limelight, runs a June 2024 build that predates the I-spline PEP default and
the fix for PEP values above 1.0 (#394).

*Hidden cost:* ``R-PERC-02`` requires the default to be *computed* across the
manifest. With one version in the manifest that computation is degenerate and
is never exercised by a real second candidate, so the resolution logic and the
``R-PERC-10`` "why not the newest" explanation ship untested against reality.

Option 2 -- add 3.09 where it is cheap: Windows and Apple silicon
-----------------------------------------------------------------

*Windows:* ``rel-3-09`` publishes a bare ``percolator.exe`` (640 512 bytes) --
no installer, no payload extraction, the cheapest managed pair in the matrix.
Unverified; the Windows work unit would have to confirm it.

*macOS:* ``percolator-osx-portable.zip`` (687 916 bytes,
``a52ab92e26b5397290a9b7284b46d396223396c0f4e619d69e7d66aed83412f5``) contains
a single Mach-O executable which this work unit identified as **arm64, minimum
macOS 15.0** -- not x86-64, not universal. So for 3.09 on macOS the
architecture story is exactly inverted from 3.07.1: native on Apple silicon
running macOS 15 or newer, and **nothing at all** for Intel Macs or for macOS
12--14.

*Cost:* two manifest entries, two loadability probes, no extraction code. The
UI must state plainly that 3.09 on macOS needs Apple silicon and macOS 15
(``R-PERC-01``, ``R-PERC-03``).

Option 3 -- add 3.09 on Linux as well
-------------------------------------

*Cost:* the Boost problem above. Three sub-options, in increasing cost:

a. **Offer 3.09 Linux only where Boost 1.83 is already present** (Ubuntu 24.04
   or newer, Debian 13 or newer, i.e. also glibc 2.38 or newer) using the
   ``.deb``, and mark it unsupported elsewhere. Cheapest, and honest, but the
   pair is unavailable on Debian 12, Ubuntu 22.04 and RHEL 9 -- which is most
   of the installed base this project targets.
b. **Ship the RPM payload plus two Boost shared objects** from a fixed,
   checksummed source, launched with a scoped ``LD_LIBRARY_PATH``. Proven to
   work on this host today. Costs an RPM payload extractor (already written:
   ``scripts/feasibility/extract_rpm.py``), a redistribution decision for
   Boost (BSL-1.0, permissive), and provenance entries for two more
   third-party artefacts.
c. **Do not offer managed 3.09 on Linux**; accept a locally registered binary.
   Zero engineering, but the zero-manual-install promise is not met for the
   newest Percolator on the platform where the project is most likely to run.

Option 4 -- add 3.08.0
----------------------

*Cost:* low effort, low value. It is XML-capable on Linux only, requires
``GLIBC_2.38``/``GLIBCXX_3.4.32`` -- excluding Debian 12, Ubuntu 22.04 and
RHEL 9 -- and still carries the PEP-above-1.0 defect that 3.08.1 fixed and
that has no published binary. It is strictly worse than 3.07.1 for the
Limelight path and strictly worse than 3.09 for everything else.

Option 5 -- a floor for local-binary registration only
------------------------------------------------------

Because the two XSDs are unchanged from 3.05 through 3.08.0, adapter and
schema support down to 3.05 is essentially free, and ``rel-3-05`` in fact
publishes XML-capable installers for all three platforms (see
:ref:`pa-rel305`). *Cost:* a documented floor and a handful of registry
entries; no managed installs, no CI beyond a parser test.

Whatever is chosen, the matrix belongs in ``docs/platform_support.rst`` as
managed / local-binary-only / unsupported per pair, and CI must not test pairs
the product does not offer.

.. _pa-rel305:

Findings that extend or correct the record
==========================================

.. list-table:: Differences between what was verified today and what is written down
   :header-rows: 1
   :widths: 32 68

   * - Item
     - Finding
   * - ``rel-3-05``'s archives
     - ``ubuntu.tar.gz``, ``osx.zip`` and ``win64.zip`` are **not** portable
       binary archives -- each is a bundle of OS installer packages
       (``.deb``, ``.pkg``, ``.exe``), including both halves of the XML/``noxml``
       A/B. Extracting ``percolator-v3-05-linux-amd64.deb`` and
       ``percolator-v3-05-osx-x86_64.pkg`` out of them yields XML-capable
       binaries with both XSDs. The specification's claim that "portable" and
       "XML-capable" have not coexisted since at least 3.06 survives -- 3.05
       simply had no portable artefacts to begin with.
   * - The ``noxml`` string trap
     - ``xmloutput``, ``decoy-xml-output``, ``pout.xml`` and
       ``percolator_out.xsd`` are present in ``noxml`` builds too. Only
       ``xercesc`` and the ``Compiler flag XML_SUPPORT was off`` diagnostic
       discriminate. ``D-002``'s recorded macOS evidence leans on three
       non-discriminating strings.
   * - 3.07.1 Linux floor
     - The specification records ``GLIBC_2.34`` and is right, but
       ``GLIBCXX_3.4.29`` is also required and is the tighter constraint on
       RHEL 9, which provides exactly that version.
   * - 3.07.1 macOS floor
     - Minimum macOS **12.7** (``LC_BUILD_VERSION``), not previously recorded.
       Single x86-64 slice, not universal.
   * - 3.09 on macOS
     - ``percolator-osx-portable.zip`` is **arm64-only, minimum macOS 15.0**.
       There is no 3.09 macOS build for Intel. Relevant to ``D-003`` and to
       ``D-004``'s framing.
   * - 3.09 on Linux
     - Neither the ``.deb`` nor the ``.rpm`` runs as shipped; both link Boost
       shared objects they do not contain, and the ``.deb`` additionally needs
       ``GLIBC_2.38``. The ``.deb`` declares no ``Depends:`` at all. 3.09
       publishes no Linux portable archive.
   * - 3.08 and 3.09 package contents
     - Both ``.deb``s ship a vendored copy of the Eigen headers under
       ``usr/include/eigen3`` (539 and 537 files against 3.07.1's 12). Harmless
       but it means payload size is not a proxy for anything.
   * - ``rel-3-08-01``
     - Confirmed to be a tag (``febeef3463``) with no release object and no
       published binary, as the specification states.
   * - XSD stability
     - ``percolator_out.xsd`` and ``percolator_in.xsd`` are byte-identical
       across 3.05, 3.07.1 and 3.08.0, and absent entirely from 3.09.

Artefacts downloaded, with checksums
====================================

Every file this work unit fetched. Also written to
``/workspace/scratch/percolator/SHA256SUMS.txt``.

.. list-table:: SHA-256 of every downloaded artefact
   :header-rows: 1
   :widths: 44 12 44

   * - Artefact
     - Bytes
     - SHA-256
   * - ``rel-3-07-01/percolator-v3-07-linux-amd64.deb``
     - 3 184 992
     - ``68cd3a4b60845d1399cc84e2e1acaef7044d89c46161009939bcb97af90d48c7``
   * - ``rel-3-07-01/percolator-v3-07-osx-x86_64.pkg``
     - 2 122 306
     - ``ad826425f932ab55651649981925c8a52fc9d19330e230c3710ebf9ce85ed4f1``
   * - ``rel-3-07-01/percolator-noxml-v3-07-osx-x86_64.pkg``
     - 999 831
     - ``7df4b831f50d357c3dc9596e16595117659e38b17f155ecc2cd8a43f26366c46``
   * - ``rel-3-07-01/percolator-noxml-ubuntu-portable.zip``
     - 946 303
     - ``4d0e94af851884ff8ab6a2223e73cf28ba3ced28f6af863d4a76d541009b9dd1``
   * - ``rel-3-08/percolator-v3-08-linux-amd64.deb``
     - 4 327 966
     - ``14320b7cea5062b83968c29fa779ccc421a5836012ffb3dfd8274e13a2dd5f7e``
   * - ``rel-3-09/percolator-v3-09-linux-amd64.deb``
     - 3 278 718
     - ``3488743548d607d468f5b1bdbc06e7d99d03af4f0bf00264a0a086e32d662cf1``
   * - ``rel-3-09/percolator-v3-09-linux-x86_64.rpm``
     - 2 227 716
     - ``45f083884853e5199e651e3a63c3d4d6b0c1629049342e46fd792a1fa5df4ba0``
   * - ``rel-3-09/percolator-osx-portable.zip``
     - 687 916
     - ``a52ab92e26b5397290a9b7284b46d396223396c0f4e619d69e7d66aed83412f5``
   * - ``rel-3-05/ubuntu.tar.gz``
     - 12 682 500
     - ``2ba745290bc927600cf3628fb41874e62bf69374d0dddd32a579353ba6b1ae73``
   * - ``rel-3-05/osx.zip``
     - 10 046 640
     - ``2c4b73ec979a705056b3b0cf69dd06a0a1512d22290c54540cde418ac9862c7b``
   * - ``rel-3-05/win64.zip``
     - 6 893 499
     - ``0a74e537746f679332de380d91605a90befc6fa231a0699adbcda8ac6bc28f5d``
   * - CentOS 8.5 ``boost-filesystem-1.66.0-10.el8.x86_64.rpm``
     - 50 224
     - ``42d08264afce5902a7feb069b4c996dce4326085db882e43e58f6ad02e1928d0``
   * - CentOS 8.5 ``boost-system-1.66.0-10.el8.x86_64.rpm``
     - 18 452
     - ``1c3a384d37f1e1fc52ba78fb904ebba34a919e7057dc3eaba821814676feba52``

Every Percolator size above equals the size the release API reports for that
asset. The two CentOS packages came from
``https://vault.centos.org/8.5.2111/AppStream/x86_64/os/Packages/``.

What is not verified
====================

Stated plainly, because an unverifiable item is not a passed item.

* **Windows.** Nothing. The artefact was not downloaded, extracted or run, by
  design. ``-X`` and ``-Z`` on Windows remain unconfirmed.
* **macOS execution.** The 3.07.1 macOS binary was identified but not run; no
  Mac was available. Its ``--help`` output, and therefore the direct presence
  of ``-X``/``-Z`` on macOS, is inferred from the A/B naming plus the static
  ``xercesc`` verdict plus the executed Linux half of the same release.
* **Rosetta 2.** Not testable here. ``D-004``'s premise is consistent with the
  single x86-64 slice found in the ``.pkg``, but nothing was executed under
  Rosetta.
* **Schema validation.** The ``pout.xml`` this work unit produced was not
  validated against ``percolator_out.xsd``; only the target namespace was
  compared.
* **Real scientific input.** Every execution here used a synthetic 1000-row
  PIN. Exit gate items 2 and 3 need real fixture spectra and are another work
  unit's.
* **3.09 Linux without externally supplied Boost.** 3.09 was made to run only
  by adding two Boost shared objects from CentOS 8. As published, upstream's
  3.09 Linux artefacts do not run on this host at all.
