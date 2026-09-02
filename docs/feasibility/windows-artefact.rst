.. _windows-artefact:

=================================================================
The Windows Percolator artefact -- evidence, and its exact limits
=================================================================

:Phase: 00 (feasibility), work unit 4
:Artefact under examination: ``percolator-v3-07.exe``, release ``rel-3-07-01``
:Host used: Linux x86-64
:Date of the evidence: 2026-08-29
:Reproduced by: ``scripts/feasibility/windows-artefact.sh``

.. contents:: Contents
   :depth: 2
   :local:

.. warning::

   **The binary was not executed on Windows.** This host is Linux and no
   Windows runner was available. Everything below is static evidence obtained
   by decompressing the installer and reading the bytes, plus an executed
   control run of the *Linux* builds of the same release. No statement here
   establishes that ``percolator.exe`` starts, accepts ``-X``, or writes
   Percolator XML on Windows. The words *verified*, *confirmed*, *proven* and
   *tested* are not used of the Windows binary anywhere in this document, and
   the tool manifest must not claim the capability -- see
   :ref:`windows-artefact-manifest`.

What was actually done
======================

#. Both halves of the release's Windows A/B pair were downloaded and their
   SHA-256 recorded: the XML-capable installer ``percolator-v3-07.exe`` and its
   ``noxml`` twin ``percolator-noxml-v3-07.exe``.
#. Both NSIS payloads were decompressed on Linux with a pure-Python extractor
   written for this unit. Nothing was installed on the host: no ``7z``, no
   ``p7zip``, no ``cabextract``, no ``wine``, no ``apt``, no ``sudo``. No tool
   was fetched into ``tools/``, so this unit adds no row to the toolchain
   provenance manifest.
#. The extractor was checked against an independent route rather than trusted.
#. Both extracted ``percolator.exe`` files were parsed as PE images -- header,
   sections and import table -- by a small PE reader written for this unit,
   because this host has no ``file`` command.
#. A strings A/B was run between the two Windows binaries.
#. The *same* A/B was run against the Linux twins of the same release, which
   **can** be executed here. That control is what tells us how much the
   Windows static evidence is worth, and it produced the most important finding
   in this document.

Summary of findings
===================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Finding
     - Status
   * - The NSIS payload can be extracted on Linux without admin rights and
       without installing anything
     - Demonstrated. 22 distinct payload files, including
       ``percolator.exe``, ``xerces-c_3_1.dll`` and both XSDs.
   * - The extractor produces the true bytes
     - Demonstrated independently: the ``noxml`` payload's ``percolator.exe``
       is byte-identical to the one in
       ``percolator-noxml-windows-portable.zip``, extracted by Python's
       ``zipfile``, which shares no code with the NSIS extractor.
   * - ``percolator.exe`` is a 64-bit Windows console program
     - Read from its PE header: PE32+, machine ``0x8664``, subsystem 3.
   * - The XML build is linked against Xerces-C and the ``noxml`` build is not
     - Read from the PE import table: 92 ``xercesc_3_1`` symbols from
       ``xerces-c_3_1.dll`` in one, no such import in the other.
   * - Both XSD companions are in the payload
     - Demonstrated -- and, unexpectedly, in *both* payloads.
   * - ``-X/--xmloutput`` appears in the binary's help text
     - True of **both** the XML and the ``noxml`` Windows binary, so this
       string alone discriminates nothing. See
       :ref:`windows-artefact-control`.
   * - The NSIS installer can be run silently without administrative rights
     - **No.** Its manifest requests ``requireAdministrator``.
   * - ``percolator.exe`` runs on Windows and emits Percolator XML
     - **Not established.** The binary was not executed on Windows.

How the payload was extracted
=============================

The format
----------

``percolator-v3-07.exe`` is a Nullsoft installer: a Win32 stub followed by a
28-byte *firstheader* and a compressed data section. The firstheader carries
the signature ``0xDEADBEEF`` followed by the ASCII ``NullsoftInst``, the
uncompressed header length and the length of everything that follows.

Measured on this artefact:

.. list-table::
   :header-rows: 1
   :widths: 34 33 33

   * - Field
     - ``percolator-v3-07.exe``
     - ``percolator-noxml-v3-07.exe``
   * - File size
     - 1 818 841 B
     - 1 222 439 B
   * - SHA-256
     - ``a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348``
     - ``1e97ea31d1a9ccd4450b2da083d0aa81599350067ed5d17844b479fe59118bba``
   * - PE stub size
     - 72 704 B
     - 72 704 B
   * - Firstheader offset
     - ``0x11c00``
     - ``0x11c00``
   * - ``length_of_header``
     - 340 646 B
     - 340 548 B
   * - ``length_of_all_following_data``
     - 1 746 137 B
     - 1 149 735 B
   * - Compression
     - LZMA, per-block (not solid)
     - LZMA, per-block (not solid)
   * - String table
     - Unicode (UTF-16LE)
     - Unicode (UTF-16LE)
   * - Instructions in the script
     - 797
     - 793
   * - NSIS version (stub manifest)
     - 3.10
     - 3.10

The block immediately after the firstheader begins ``80 11 01 40`` -- an int32
whose high bit marks the block compressed -- followed by the LZMA property
bytes ``5d 00 00 80 00`` (``lc=3, lp=0, pb=2``, 8 MiB dictionary). Because the
archive is not solid, each file record carries its own length prefix and its
own LZMA stream, and file offsets are counted from the first byte after the
header block rather than from ``blocks[NB_DATA].offset``, which is zero here.

The extractor
-------------

``scripts/feasibility/extract_nsis.py`` uses only the Python standard library
(``lzma``, ``zlib``, ``bz2``). It locates the firstheader, decompresses the
header, parses the eight ``block_header`` entries, reads the instruction array
and the Unicode string table, and walks the instructions: opcode 20
(``EW_EXTRACTFILE``) names a file and points at its data record, opcode 11 with
its second parameter set is ``SetOutPath``. ``$_OUTDIR``, which ``File /r``
emits for each subdirectory, is resolved against the enclosing ``SetOutPath``,
so the reported paths are the real install paths rather than raw variables.

It handles solid and per-block archives and the three NSIS compressors, and it
refuses to write outside its output directory.

Proving the extractor rather than trusting it
---------------------------------------------

Exit code 0 proves nothing, and neither does a plausible-looking file list. The
check used here is an independent second route to the same bytes: the same
release also publishes ``percolator-noxml-windows-portable.zip``, a plain ZIP
containing one file, ``percolator.exe``. Extracted with Python's ``zipfile`` --
no shared code with the NSIS extractor -- it is byte-identical to the
``percolator.exe`` recovered from the ``noxml`` installer's payload::

    b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f
        scratch/windows/noxml/INSTDIR/bin/percolator.exe      (from NSIS)
    b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f
        scratch/windows/portable/rel-3-07-01/percolator.exe   (from ZIP)

The NSIS extraction therefore produces the true payload bytes. That is a fact
about the extractor, not about Windows.

The payload
===========

Both installers place their product files under ``$INSTDIR`` (default
``$PROGRAMFILES\percolator-v3-07``, read from the installer's string table).
Files whose path begins ``$PLUGINSDIR`` are the installer's own NSIS plugins,
unpacked to a temporary directory at install time and not part of the product.

Sizes are uncompressed bytes. "Same" compares SHA-256 between the two builds.

.. list-table:: Distinct payload files, XML build against ``noxml`` twin
   :header-rows: 1
   :widths: 52 14 14 20

   * - Install path
     - XML
     - ``noxml``
     - Bytes
   * - ``$INSTDIR\bin\concrt140.dll``
     - 317888
     - 317888
     - same
   * - ``$INSTDIR\bin\gtest_unit.exe``
     - 604672
     - 604672
     - differ
   * - ``$INSTDIR\bin\msvcp140.dll``
     - 567328
     - 567328
     - same
   * - ``$INSTDIR\bin\msvcp140_1.dll``
     - 25016
     - 25016
     - same
   * - ``$INSTDIR\bin\msvcp140_2.dll``
     - 186928
     - 186928
     - same
   * - ``$INSTDIR\bin\msvcp140_codecvt_ids.dll``
     - 21536
     - 21536
     - same
   * - ``$INSTDIR\bin\percolator.exe``
     - 804864
     - 707072
     - differ
   * - ``$INSTDIR\bin\qvality.exe``
     - 132608
     - 132608
     - differ
   * - ``$INSTDIR\bin\vcomp140.dll``
     - 181808
     - 181808
     - same
   * - ``$INSTDIR\bin\vcruntime140.dll``
     - 98336
     - 98336
     - same
   * - ``$INSTDIR\bin\vcruntime140_1.dll``
     - 38448
     - 38448
     - same
   * - ``$INSTDIR\bin\xerces-c_3_1.dll``
     - 2528768
     - absent
     - n/a
   * - ``$INSTDIR\share\xml\percolator\xml-pin-1-3\percolator_in.xsd``
     - 15782
     - 15782
     - same
   * - ``$INSTDIR\share\xml\percolator\xml-pout-1-5\percolator_out.xsd``
     - 10674
     - 10674
     - same
   * - ``$PLUGINSDIR\InstallOptions.dll``
     - 15872
     - 15872
     - same
   * - ``$PLUGINSDIR\NSIS.InstallOptions.ini``
     - 667
     - 697
     - differ
   * - ``$PLUGINSDIR\StartMenu.dll``
     - 7680
     - 7680
     - same
   * - ``$PLUGINSDIR\System.dll``
     - 12288
     - 12288
     - same
   * - ``$PLUGINSDIR\UserInfo.dll``
     - 4096
     - 4096
     - same
   * - ``$PLUGINSDIR\ioSpecial.ini``
     - 211
     - 211
     - same
   * - ``$PLUGINSDIR\modern-header.bmp``
     - 34254
     - 34254
     - same
   * - ``$PLUGINSDIR\modern-wizard.bmp``
     - 26494
     - 26494
     - same

The one structural difference in the product files is ``xerces-c_3_1.dll``,
shipped only by the XML build.

Checksums of the files that matter
----------------------------------

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - File
     - SHA-256
   * - XML ``percolator.exe``
     - ``044f3957e2f05a38d13d8c77136f24435827d8563850b8808b5ad52e6aa4691e``
   * - ``noxml`` ``percolator.exe``
     - ``b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f``
   * - ``xerces-c_3_1.dll`` (XML build only)
     - ``164c9a4fd8198468c089caba1cf5411aa4a54312e0eaa32d9089b48a8e0fbf73``
   * - ``percolator_in.xsd`` (both builds)
     - ``fc3c95e02950af3c44ae0c830c3ecf8005a543358eb7311f94c12dab4a216b87``
   * - ``percolator_out.xsd`` (both builds)
     - ``c4c664ea673817ded4616958b0682f401f940f40212246473e75835f3597bc1b``

The XSD companions
------------------

Exit gate item 9 asks that NSIS payload extraction yield "a runnable binary
plus its XSD companions". Both XSDs are present, at::

    $INSTDIR\share\xml\percolator\xml-pin-1-3\percolator_in.xsd    15782 B
    $INSTDIR\share\xml\percolator\xml-pout-1-5\percolator_out.xsd  10674 B

Two qualifications, both material:

* They are present in the ``noxml`` payload as well, byte-identical. The XSDs
  are therefore **not** a discriminator between an XML-capable and a ``noxml``
  build, contrary to what their presence might suggest.
* "Runnable" is not shown. The binary was not executed on Windows.

A third point matters for a later phase. On Linux the XML build looks for
``percolator_in.xsd`` at a compiled-in absolute path, and running it with
``--xml-in`` when the file is missing from that path does not fail cleanly --
it segmentation-faults (exit 139) after printing ``unable to load``. The
Windows XML binary contains the path fragment ``share/xml\percolator/`` (mixed
separators, present only in the XML build), so the same lookup exists there.
Whatever the product does with these files, it must put them where the binary
expects them; a missing XSD is not a graceful degradation.

PE facts, read from the bytes
=============================

``file`` does not exist on this host, so ``scripts/feasibility/pe_info.py``
parses the headers directly.

The two ``percolator.exe`` binaries
-----------------------------------

.. list-table::
   :header-rows: 1
   :widths: 28 36 36

   * - Field
     - XML build
     - ``noxml`` build
   * - Size
     - 804 864 B
     - 707 072 B
   * - Format
     - PE32+
     - PE32+
   * - Machine
     - ``0x8664`` x86-64 (AMD64)
     - ``0x8664`` x86-64 (AMD64)
   * - Subsystem
     - 3, Windows console
     - 3, Windows console
   * - Link timestamp
     - 2024-06-20 13:22:48 UTC
     - 2024-06-20 13:21:38 UTC
   * - Linker version
     - 14.29 (MSVC 2019 toolset)
     - 14.29 (MSVC 2019 toolset)
   * - Image base
     - ``0x140000000``
     - ``0x140000000``
   * - Characteristics
     - ``EXECUTABLE_IMAGE``, ``LARGE_ADDRESS_AWARE``
     - ``EXECUTABLE_IMAGE``, ``LARGE_ADDRESS_AWARE``
   * - DLL characteristics
     - ASLR, DEP, high-entropy VA, TS-aware
     - ASLR, DEP, high-entropy VA, TS-aware
   * - ``.text`` virtual size
     - 619 407 B
     - 555 119 B

The link timestamps are 70 seconds apart, consistent with two configurations
built back to back from one source tree in one CI job.

The installer stub itself is a different animal: PE32, machine ``0x014c``
(i386, 32-bit), subsystem 2 (GUI), link timestamp 2024-03-30 16:55:15 UTC --
that is the NSIS 3.10 ``exehead``, not the Percolator build.

Imports: the structural discriminator
-------------------------------------

Parsed from the import directory. The XML build imports 18 DLLs, the ``noxml``
build 17. The two lists are identical except for one entry:

.. list-table::
   :header-rows: 1
   :widths: 40 20 20 20

   * - Imported DLL
     - XML build
     - ``noxml`` build
     - Shipped in payload
   * - ``xerces-c_3_1.dll``
     - **92 symbols**
     - not imported
     - XML build only
   * - ``KERNEL32.dll``, ``ADVAPI32.dll``, ``WS2_32.dll``
     - imported
     - imported
     - no (Windows system DLLs)
   * - ``MSVCP140.dll``, ``VCRUNTIME140.dll``, ``VCRUNTIME140_1.dll``,
       ``VCOMP140.DLL``
     - imported
     - imported
     - yes
   * - eleven ``api-ms-win-crt-*-l1-1-0.dll``
     - imported
     - imported
     - no (Universal CRT, part of Windows 10 and later)

The 92 Xerces symbols are the C++-mangled ``xercesc_3_1`` namespace and include
``XMLReaderFactory::createXMLReader``, ``XMLGrammarPoolImpl::retrieveGrammar``,
``XMLGrammarPoolImpl::serializeGrammars`` and the ``DefaultHandler`` SAX2
callbacks -- an XML *parser* and grammar pool, not an XML writer.

What this tells a later phase about the Windows install: ``percolator.exe`` and
the DLLs in ``$INSTDIR\bin`` must stay in one directory.

.. warning::

   **Corrected 2026-09-02, by running it on Windows.** This paragraph used to
   say that "a separate Visual C++ redistributable should not be needed" and
   that "the remaining imports are Windows components". **Both are false**, and
   the first Windows execution in this project's history is what exposed them.

   On a ``windows-latest`` runner the payload ``percolator.exe`` failed to load
   three times with exit ``3221225781`` (``0xC0000135``,
   ``STATUS_DLL_NOT_FOUND``): the process was created, the loader could not
   resolve its imports, and no application code ran. The cause is one level
   deeper than this analysis looked. The payload's own
   ``xerces-c_3_1.dll`` imports 60 functions from **``MSVCR100.dll``, the
   Visual C++ 2010 runtime**, which the 22-file payload does not contain and
   which GitHub's image does not ship. The original analysis parsed only
   ``percolator.exe``'s imports and never walked the transitive closure.

   **This does not affect the artefact the product installs.** ``D-002`` option
   C ships the portable ``noxml`` build, whose closure carries no xerces and
   therefore no ``MSVCR100`` dependency; it started and wrote XML on that same
   runner. The ``noxml`` NSIS installer's payload binary is byte-identical to
   the portable zip's and its closure is self-contained.

   A reminder of the general shape: **an import closure is not the imports of
   one file.**

Strings: the A/B, quoted exactly
================================

``strings -a -n 4`` over each extracted ``percolator.exe``. Counts are matching
lines.

.. list-table::
   :header-rows: 1
   :widths: 46 27 27

   * - Token
     - XML build
     - ``noxml`` build
   * - ``xmloutput``
     - 1
     - 1
   * - ``decoy-xml-output``
     - 1
     - 1
   * - ``pout.xml``
     - 2
     - 2
   * - ``percolator_out.xsd``
     - 1
     - 1
   * - ``xerces`` / ``Xerces``
     - 107
     - 0
   * - ``percolator_in.xsd``
     - 1
     - 0
   * - ``xml-pin-1-3``
     - 1
     - 0
   * - ``Compiler flag XML_SUPPORT was off``
     - 0
     - 1

The option strings, present in both
-----------------------------------

These lines appear, in this order and with this wording, in **both** Windows
binaries. Line numbers are into the strings output of the XML build::

    9937:   percolator [-X pout.xml] [other options] pin.tsv
    9943:pout.xml is where the output will be written (ensure to have read
    9944:and write access on the file).
    9945:filename
    9946:Path to xml-output (pout) file.
    9947:xmloutput
    9954:Include decoys (PSMs, peptides and/or proteins) in the xml-output. Only available if -X is set.
    9955:decoy-xml-output

The corresponding block in the ``noxml`` binary is character-for-character the
same; only the recorded build time differs (``13:22:20`` against ``13:21:08``).

**Therefore a ``strings`` hit on ``--xmloutput``, ``-X``, ``pout.xml`` or
``decoy-xml-output`` says nothing about whether a binary is an
``XML_SUPPORT=ON`` build.** That is the inference the artefact's capability
previously rested on, and it does not hold.

The strings that do discriminate
--------------------------------

Present only in the XML build::

    10447:percolator_in.xsd
    10448:xml-pin-1-3/
    10449:http://per-colator.com/percolator_in/
    10451:ERROR: xml schema error
    10453:ERROR: caught xercesc::DOMException=
     9914:share/xml\percolator/
    (plus 107 lines of xercesc source-file and symbol names)

Present only in the ``noxml`` build::

    9353:ERROR: Compiler flag XML_SUPPORT was off, you cannot use the -k flag for pin-format input files

Present in both, and worth noting because it shows where the pout XML writer
lives::

    9354:writeXML_PSMs
    9355:  <psms>
    9357:writeXML_Peptides
    9360:writeXML_Proteins
    9368:xsi:schemaLocation="

.. _windows-artefact-control:

The executed control: what these markers actually mean
======================================================

Static markers are only worth what a calibration says they are worth. The same
release publishes Linux builds of the same two configurations, and those
**can** be executed on this host. They were, and the result changes the
reading of the Windows evidence.

Artefacts: ``percolator-v3-07-linux-amd64.deb``
(``68cd3a4b...``) and ``percolator-noxml-v3-07-linux-amd64.deb``
(``ea630bbc...``), extracted with ``ar`` and ``tar``, giving
``percolator`` ``83f594c3...`` (XML, 6 242 960 B) and ``9e140ade...``
(``noxml``, 2 182 688 B). Both report ``Percolator version 3.07.1``. Input: a
400-PSM synthetic PIN generated by the driver script.

.. list-table:: Executed on Linux, both builds of ``rel-3-07-01``
   :header-rows: 1
   :widths: 40 30 30

   * - Test
     - XML build
     - ``noxml`` build
   * - ``--help`` lists ``-X, --xmloutput``
     - yes
     - **yes**
   * - ``--help`` lists ``--decoy-xml-output``
     - yes
     - **yes**
   * - ``-X out.xml`` on a valid PIN
     - exit 0, 143 729 B written
     - **exit 0, 143 733 B written**
   * - ``<psm>`` elements in that output
     - 200
     - **200**
   * - ``--xml-in`` (pin-XML *input*)
     - attempts it; needs the XSD; segfaults (139) when it is absent
     - refuses: ``Compiler flag XML_SUPPORT was off``
   * - ``xercesc`` symbols in the binary
     - present (statically linked)
     - absent

The two XML outputs differ in exactly one element, ``<command_line>``. Both
declare ``xmlns="http://per-colator.com/percolator_out/15"`` and
``xsi:schemaLocation`` pointing at ``percolator_out.xsd``, and both contain 200
``<psm>`` and 200 ``<peptide>`` elements.

Two conclusions follow, and they pull in opposite directions.

**First, the discriminators are real.** Xerces linkage,
``percolator_in.xsd``/``xml-pin-1-3`` and the ``XML_SUPPORT was off``
diagnostic separate the two configurations perfectly on Linux, where the
separation can be checked by running them. The same three markers separate the
two Windows binaries the same way. The Windows artefact named
``percolator-v3-07.exe`` is, on this evidence, an ``XML_SUPPORT=ON`` build --
a much stronger statement than "its name and size suggest it".

**Second, on Linux the capability the project needs does not depend on that
build option at all.** ``XML_SUPPORT`` gates the pin-XML *input* path
(``-k/--xml-in``, ``--stdinput-xml``) and its Xerces/XSD validation. The
Percolator XML *output* that the Limelight converter consumes is written by
``writeXML_PSMs``/``writeXML_Peptides``, which is compiled into both builds and
which the ``noxml`` build executed successfully here. Those writer strings are
also in the Windows ``noxml`` binary.

This bears directly on ``D-002`` and is escalated, not decided, in
:ref:`windows-artefact-escalation`.

.. _windows-artefact-proves:

What this evidence proves, and what it does not
===============================================

Established
-----------

* The NSIS payload of ``percolator-v3-07.exe`` can be extracted on a
  non-Windows host, without administrative rights and without installing
  anything, by a script in this repository.
* The extracted bytes are the true payload bytes (independent-route check).
* The payload contains ``percolator.exe``: PE32+, x86-64, Windows console
  subsystem, linked 2024-06-20, importing 92 symbols from
  ``xerces-c_3_1.dll``, which the payload also ships.
* The payload contains ``percolator_in.xsd`` and ``percolator_out.xsd``.
* Of the two Windows binaries in the release's A/B pair, only
  ``percolator-v3-07.exe``'s carries the Xerces linkage, the
  ``percolator_in.xsd``/``xml-pin-1-3`` strings and the ``share/xml\percolator/``
  path fragment; only the ``noxml`` one carries the ``XML_SUPPORT was off``
  diagnostic. On Linux, executed, those markers correspond exactly to
  ``-DXML_SUPPORT=ON`` and to the default build.
* The NSIS installer's manifest requests ``requireAdministrator``.

Not established
---------------

* **That ``percolator.exe`` runs on Windows at all.** The binary was not
  executed on Windows.
* That its ``--help`` prints ``-X``/``--xmloutput`` on Windows. The strings are
  in the image; whether the program reaches the code that prints them is a
  different question, and one the ``noxml`` twin shows a strings hit cannot
  answer.
* That ``-X`` produces a Percolator XML file on Windows, or that such a file
  would validate against ``percolator_out.xsd``, or that the Limelight
  converter would accept it.
* That the shipped MSVC runtime DLLs are sufficient on a clean Windows machine.
* That the payload, once extracted by the product, works from a per-user
  directory with no registry entries and no ``PATH`` change.

Every one of those needs the binary to run on Windows. None of them is
inferable from the bytes.

Status against exit gate item 8
-------------------------------

Gate item 8 reads: the Windows artefact "is confirmed on a Windows runner --
payload obtained without admin rights, ``-X`` present -- OR the blocking reason
is documented precisely and the manifest does not claim it."

The first branch is **not met**: there is no Windows runner. This document is
the second branch. The blocking reason is that the only host available to this
phase runs Linux, that no Windows machine or CI runner is reachable from it,
that creating a git remote to reach one is decision ``D-008`` and not an
agent's to take, and that installing an emulator on the host is forbidden by
the project's environment rules. :ref:`windows-artefact-cost` prices the ways
out. :ref:`windows-artefact-manifest` gives wording that does not claim the
capability.

One further observation the gate's authors should see: **half of the test in
gate item 8 -- "``-X`` present" -- would pass on the ``noxml`` binary too**, on
Linux where it was actually executed. If the intent is to establish
``XML_SUPPORT=ON``, the discriminating test is ``--xml-in``, which the
``noxml`` build refuses by name. If the intent is to establish that the binary
can write the XML the Limelight converter needs, the test is running ``-X`` on
a real PIN and inspecting the file. "``-X`` present in ``--help``" establishes
neither.

.. _windows-artefact-mechanism:

The extraction mechanism the product will use (Phase 05 input)
==============================================================

The installer cannot be run
---------------------------

The NSIS stub's embedded manifest, identical in both artefacts, contains::

    <requestedExecutionLevel level="requireAdministrator" uiAccess="false"/>

An executable with that manifest triggers a UAC elevation prompt on launch.
A standard user without administrator credentials cannot start it at all, and
the prompt appears **before** any NSIS switch is interpreted. Therefore:

* ``percolator-v3-07.exe /S`` (silent) does not avoid elevation.
* ``percolator-v3-07.exe /S /D=C:\Users\me\CometGUI\percolator`` does not
  either. ``/D=`` only sets ``$INSTDIR``; it does not change the manifest.
* Even with elevation the script does more than copy files: its string table
  shows registry writes under ``SYSTEM\CurrentControlSet\Control\Session
  Manager\Environment``, a ``PATH`` modification with an all-users or
  current-user choice, Start Menu shortcut creation and an uninstaller
  registration. None of that is wanted, and the all-users branch is what a
  silent default is likely to select.

So the ``/S`` ``/D=`` route is unusable for CometGUI, and the reason is a
manifest, not a preference.

What the product should do instead
----------------------------------

Extract the payload itself, exactly as this unit did. The mechanism is
implementable in the JVM with no native helper and no external tool:

#. Read the file, find ``0xDEADBEEF`` + ``NullsoftInst``, parse the 28-byte
   firstheader.
#. Read the header block: an int32 length prefix whose top bit means
   compressed, then an LZMA stream beginning with 5 property bytes.
   ``java.util.zip.Inflater`` covers the zlib case; LZMA needs a decoder --
   either the public-domain XZ-for-Java library or a vendored LZMA1 decoder.
   Percolator's Windows artefacts for 3.06 and 3.07 both use LZMA.
#. Walk the instruction array for opcode 20, resolving names through the
   UTF-16 string table.
#. Write ``bin\`` and ``share\`` into the product's own per-user tool
   directory. Nothing else in the payload is needed: the ``$PLUGINSDIR``
   entries are the installer's UI plugins.

Properties worth recording for Phase 05:

* No administrative rights, no registry write, no ``PATH`` change, no
  elevation prompt, no temporary directory outside the product's own.
* Deterministic: the payload files' SHA-256 can be checked against a
  recorded manifest, which the project needs for provenance anyway.
* ``percolator.exe`` and the MSVC DLLs must be unpacked into the same
  directory; the XSDs must go where the binary looks for them.
* The same approach reads the 3.06 artefact, which has the same structure --
  useful if the version policy ever selects it.

The alternative, for completeness: a bundled extractor binary (7-Zip's
``7za``) would work but adds a redistributed third-party executable per
platform, with its own licence and provenance obligations, to avoid roughly
200 lines of Java. The parser is the better trade.

.. _windows-artefact-cost:

What it would take to execute the binary on Windows
===================================================

Four realistic options. None was taken; each is priced.

.. list-table::
   :header-rows: 1
   :widths: 22 20 29 29

   * - Option
     - Cost
     - Proves
     - Does not prove
   * - GitHub Actions ``windows-latest``
     - Money: none in practice (a public repository is free; a private one
       bills Windows at 2x, and the job is ~3 minutes). Effort: ~1 hour to
       write the workflow. **Blocked**: there is no git remote and creating
       one is ``D-008``, an owner decision.
     - The binary starts on Windows Server 2022; the real ``--help`` text;
       ``-X`` on a real PIN; ``--xml-in`` behaviour, which is the
       discriminating test. Repeatable on every change.
     - Behaviour on consumer Windows 10/11; behaviour for a standard,
       non-administrator user (the hosted runner is an administrator);
       Windows on ARM.
   * - A developer's own Windows machine, one-page checklist
     - Money: none. Effort: ~15 minutes of one person's time, once. No
       decision needed.
     - Everything the CI runner proves, on a real desktop Windows, and --
       if run from a standard user account -- the non-administrator case,
       which is the one the product actually needs.
     - Repeatability: it is a one-off unless the transcript is recorded.
       Nothing about other Windows versions.
   * - A project-local wine under ``tools/``
     - Effort: high and uncertain -- see the assessment below. Money: none.
     - At most that the PE image loads and its imports resolve against
       wine's DLLs plus the shipped MSVC runtime. A useful smoke test.
     - **Nothing about Windows.** Wine is a reimplementation; a pass is not
       evidence that Windows behaves the same, and a failure may be wine's.
       This is the weakest option and was explicitly not attempted.
   * - A cloud Windows VM
     - Money: well under one US dollar for an hour (a small Azure or AWS
       Windows instance, licence included). Effort: ~1 hour including
       account setup. Needs cloud credentials the project does not have;
       obtaining them is an owner decision.
     - Everything the CI runner proves, plus full control of the account
       type, so the true non-administrator path can be exercised.
     - Nothing about the diversity of end-user machines.

On a project-local wine specifically
------------------------------------

Asked honestly: wine does not publish a self-contained relocatable build in
the way a JDK or a Maven distribution does. WineHQ ships distribution packages
(``.deb``, ``.rpm``) whose contents assume system paths, and a source build
needs a large set of development headers and 32-bit multilib support that
cannot be installed here without root. Unpacking a ``.deb`` into ``tools/``
and pointing ``WINEPREFIX`` and ``LD_LIBRARY_PATH`` at it is not obviously
impossible for a console-only program -- ``percolator.exe`` needs no graphics
-- but it depends on the host's glibc and on a set of shared libraries the
package does not carry, and the first run creates a prefix that itself needs
wine's own tooling. Estimate: the better part of a day, with a real chance of
ending with nothing. Against that, what a success would prove is limited, for
the reason in the table. It is not recommended, and it was not attempted.

Recommended: the checklist for a Windows machine
------------------------------------------------

The cheapest option that produces real evidence is fifteen minutes on any
Windows machine, ideally from a standard user account:

#. Download ``percolator-v3-07.exe`` from
   ``https://github.com/percolator/percolator/releases/download/rel-3-07-01/percolator-v3-07.exe``
   and check its SHA-256 is
   ``a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348``
   (``certutil -hashfile percolator-v3-07.exe SHA256``).
#. **Do not run it.** Extract the payload instead. The extractor in this
   repository is pure Python and runs on Windows unchanged::

       python scripts\feasibility\extract_nsis.py percolator-v3-07.exe -o out

   Confirm ``out\INSTDIR\bin\percolator.exe`` has SHA-256
   ``044f3957e2f05a38d13d8c77136f24435827d8563850b8808b5ad52e6aa4691e``, which
   pins the Windows test to the same bytes examined here.
#. ``out\INSTDIR\bin\percolator.exe --help`` -- capture the whole output.
#. Generate the same 400-PSM synthetic PIN (the generator is inside
   ``scripts/feasibility/windows-artefact.sh``) and run
   ``percolator.exe -X pout.xml test.pin``. Record the exit code, whether
   ``pout.xml`` exists, its size, and its first ten lines.
#. ``percolator.exe --xml-in test.pin`` -- record whether the message
   ``Compiler flag XML_SUPPORT was off`` appears. It must **not**, for an
   ``XML_SUPPORT=ON`` build. This is the discriminating test.
#. Record whether every step ran without an elevation prompt and whether the
   account used was an administrator.
#. Return the transcript. It replaces this document's central caveat with a
   fact, either way.

.. _windows-artefact-manifest:

What the tool manifest may say
==============================

Given what was and was not established, the manifest may record the artefact's
identity, its checksums and how its payload is obtained. It may not record an
XML capability for Windows.

Safe wording, verbatim:

.. code-block:: text

    tool: percolator
    version: 3.07.1
    platform: windows-x86_64
    source_artefact: percolator-v3-07.exe
    source_release: rel-3-07-01 (2024-06-20)
    source_sha256: a9860e02a7e78b9bc069438e6564eb20e90bb46244aa628d567e4b69fe1ea348
    obtained_by: NSIS payload extraction (no installation, no elevation)
    payload_binary: bin/percolator.exe
    payload_binary_sha256: 044f3957e2f05a38d13d8c77136f24435827d8563850b8808b5ad52e6aa4691e
    payload_schemas:
      - share/xml/percolator/xml-pin-1-3/percolator_in.xsd
      - share/xml/percolator/xml-pout-1-5/percolator_out.xsd
    xml_capability: unverified-on-windows
    xml_capability_basis: >
      Static evidence only, obtained on Linux 2026-08-29: the payload binary
      imports 92 symbols from xerces-c_3_1.dll and carries the
      percolator_in.xsd / xml-pin-1-3 strings, which on the Linux builds of the
      same release correspond exactly to -DXML_SUPPORT=ON. The binary was not
      executed on Windows. Its help text and its XSD companions are identical
      to the noxml twin's and discriminate nothing.

Rules this wording follows, and which any rewording must keep:

* The value of ``xml_capability`` is ``unverified-on-windows``. Not
  ``true``, not ``xml``, not ``supported``.
* The basis field says what was observed and on which host, and contains the
  sentence "The binary was not executed on Windows."
* Nothing in the manifest is phrased as *verified*, *confirmed*, *proven* or
  *tested* in respect of the Windows binary.

Wording that must not be used, for the record: "XML-capable", "verified
XML support", "``-X`` confirmed", "tested on Windows", or any capability field
whose value would let a downstream feature switch itself on without a probe.

The product does not depend on the manifest answering this
----------------------------------------------------------

Worth stating plainly, because it bounds the damage. The specification already
requires the product to resolve Percolator from *probed* capability rather than
from a version number or a static table. A manifest that records
``unverified-on-windows`` is therefore not a missing feature: the running
application probes the binary it actually has, on the machine it is actually
on, and enables or disables the Limelight path from that. The unverified item
is the *project's* claim, not the *product's* behaviour.

.. _windows-artefact-escalation:

Escalations
===========

Two items for the phase orchestrator. Neither is this unit's to decide.

E1 -- gate item 8's test does not discriminate
----------------------------------------------

"``-X`` present" is satisfied by the ``noxml`` build, executed, on Linux. If
gate item 8 is meant to establish an ``XML_SUPPORT=ON`` build, its test should
be ``--xml-in`` (which the ``noxml`` build refuses by name) or an actual ``-X``
run inspected for output. This is a wording problem in the gate, and gates are
not this unit's to change. Evidence: :ref:`windows-artefact-control`.

E2 -- executed evidence that bears on ``D-002``
-----------------------------------------------

``D-002`` rests on the premise that only an XML-capable build can emit the
Percolator XML the Limelight converter needs, and that this forces the choice
of 3.07.1 and of OS-package artefacts on all three platforms. On this host, on
2026-08-29, the ``noxml`` Linux build of 3.07.1 wrote a 143 733-byte
``percolator_out`` XML file with 200 ``<psm>`` and 200 ``<peptide>`` elements,
identical to the XML build's output but for the ``<command_line>`` element, at
exit 0. What ``XML_SUPPORT`` gates is the pin-XML *input* path, not the pout
XML *output* path.

If that holds generally, the consequences are large and entirely in
``D-002``'s territory:

* ``percolator-noxml-windows-portable.zip`` -- a plain ZIP, no installer, no
  elevation, extractable by ``java.util.zip`` -- would become a candidate for
  the Windows Limelight path. It exists for ``rel-3-07-01``
  (``percolator.exe``, 707 072 B) and for ``rel-3-08``
  (``percolator.exe``, 720 896 B, SHA-256 ``33139c90...``), and both contain
  the ``writeXML_PSMs`` writer strings.
* The same argument would apply to ``percolator-noxml-osx-portable.zip``,
  which would remove the ``.pkg`` and possibly change the Rosetta 2 position
  in ``D-004``.
* "Latest compatible" might move from 3.07.1 to 3.08, which would also retire
  the accepted trade in ``D-002`` about the I-splines PEP regressor and the
  PEP-over-1.0 fix.

What is **not** established, and must be before any of that is acted on:

* Whether a ``noxml`` build's ``-X`` output validates against
  ``percolator_out.xsd`` -- not checked; this host has no XSD validator and
  no JDK yet.
* Whether the Limelight converter accepts it -- not checked; no JVM yet.
* Whether the Windows and macOS ``noxml`` builds behave as the Linux one does
  -- not checked; neither was executed.
* Whether 3.09, which deleted the XML code rather than compiling it out,
  behaves differently again. Its release notes say the I/O was removed, and
  its binaries carry neither marker.

Recommendation, for the orchestrator to weigh rather than to adopt: keep
``D-002`` as decided for now -- 3.07.1, XML artefacts -- because it is the
conservative choice and nothing here contradicts it; and raise a separate,
narrowly scoped question once a JVM exists, namely whether a ``noxml``
build's ``-X`` output passes ``percolator_out.xsd`` validation and the
Limelight converter. That question is cheap to answer and could simplify three
platforms at once. It is an owner decision because it changes what ``D-002``
decided.

Reproducing all of this
=======================

::

    scripts/feasibility/windows-artefact.sh --clean

Downloads six artefacts (about 8 MB), checks every SHA-256, extracts both NSIS
payloads, cross-checks the extractor against the portable ZIP, parses both PE
headers and import tables, runs the strings A/B, and runs the executed Linux
control. Everything lands under ``scratch/`` (gitignored) with a transcript in
``scratch/windows/evidence.log``. The script fails, rather than warning, if any
checksum is wrong or any expected output is missing.

The pieces are usable on their own:

.. list-table::
   :header-rows: 1
   :widths: 44 56

   * - Script
     - What it does
   * - ``scripts/feasibility/extract_nsis.py``
     - Extracts any NSIS installer's payload. Standard library only; runs on
       Windows and macOS unchanged. ``--list`` inspects without writing.
   * - ``scripts/feasibility/pe_info.py``
     - Reports PE header, sections and import table. ``--imports`` lists the
       imported symbols.
   * - ``scripts/feasibility/windows-artefact.sh``
     - The whole chain above, from nothing but a network connection.

Nothing in this document was produced by a tool installed on the host, and no
tool was fetched into ``tools/`` for it.
