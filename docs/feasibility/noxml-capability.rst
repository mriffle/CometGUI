.. _feasibility-noxml-capability:

==========================================================================
Does the ``noxml`` Percolator build emit Percolator XML?
==========================================================================

:Phase: 00 -- Feasibility, Legal and Upstream Verification
:Work unit: 10
:Date: 2026-08-29
:Host: Debian 12, glibc 2.36, x86-64
:Serves: ``R-PERC-01``, ``R-PERC-02``, ``R-PERC-03``, ``R-PERC-10``,
         ``R-PERC-11``, ``R-TOOL-02``; bears on ``D-002``, ``D-003``, ``D-004``
:Reproduce with: ``python3 scripts/feasibility/noxml_sweep.py``

.. contents:: Contents
   :depth: 2
   :local:


The one-sentence answer
=======================

**Yes.** Upstream's ``noxml`` Percolator builds emit Percolator output ("pout")
XML that is byte-identical to the XML-capable twin's, that carries decoys under
``-Z``, and that the Limelight converter consumes to produce a schema-valid
Limelight XML file -- all of it executed here, not inferred. The ``noxml``
naming describes the pin-XML *reader*, not the pout-XML *writer*, and the
project's strategy has been resting on the wrong half of that distinction.

The finding is real but it is **not** a licence to change the selected version.
3.09 genuinely cannot write pout XML -- confirmed by execution -- so the ceiling
for a Limelight-enabled run is still 3.08.x. And 3.08 does not simplify the
platform story: it publishes no Linux portable archive at all, its two Linux
``.deb`` payloads need ``GLIBC_2.38``, and its only macOS artefact is an
**arm64** binary with a **macOS 15.0** floor, which reaches *fewer* Macs than
3.07.1's x86-64 build under Rosetta 2.


Why this unit exists
====================

``specification.rst``, *Percolator versions and artefact availability*, and
``D-002`` both rest on a claimed A/B: upstream builds each release twice, with
``-DXML_SUPPORT=ON`` and with the default ``OFF``, publishes the second under a
``noxml`` name, every portable archive is a ``noxml`` build, and therefore
every XML-capable artefact is an operating-system *package* whose payload must
be extracted. The NSIS extraction, the ``.pkg`` extraction and the Rosetta 2
requirement in ``D-004`` all descend from that one premise.

The premise is true about the *build*. It is false about the *capability the
Limelight stage needs*.


Method
======

What "can emit pout XML" means here
-----------------------------------

The Limelight converter's prerequisite is a file the converter can read: a
``<percolator_output>`` document in the ``percolator_out/15`` namespace, with
PSM and peptide q-values and PEPs, and -- for ``--import-decoys`` -- decoy
entries produced by Percolator's ``-Z``. That is the capability under test.
"Was built with ``XML_SUPPORT=ON``" is a *different* property, and this unit's
whole point is that the two come apart.

Executed versus inferred
------------------------

This host is Linux x86-64. Every claim below is labelled:

**executed**
    The binary was run here. Its path, size and version banner are given, and
    the verdict comes from the file it actually wrote and the element counts in
    it -- never from an exit status. ``ONBOARDING.rst`` is explicit that exit
    code 0 proves nothing, and this unit found a case where it would have lied:
    with too small a PIN, a fully capable Percolator aborts with "median decoy
    score <= score at 1% FDR" and writes nothing.

**inferred**
    The binary is a Windows PE or a macOS Mach-O and cannot run here. The
    verdict comes from a byte scan, and the discriminator used is named.

The discriminator that actually tracks the writer
-------------------------------------------------

Earlier units established that ``xmloutput``, ``decoy-xml-output``,
``pout.xml`` and ``percolator_out.xsd`` appear in *both* twins and therefore
separate nothing. That is not a flaw in those markers -- it is the finding:
both twins contain the writer.

What separates a build that has the pout writer from one that does not is the
writer's own literal output fragments, which cannot be present unless the code
that emits them is linked in:

.. list-table:: Markers used, and what each one actually tells you
   :header-rows: 1
   :widths: 34 20 46

   * - Byte marker
     - Tracks
     - Behaviour observed across the sweep
   * - ``<percolator_output``
     - pout **writer**
     - Present in every 3.05--3.08 artefact, both twins, all platforms.
       Absent from all four 3.09 artefacts.
   * - ``</percolator_output>``
     - pout **writer**
     - Identical pattern; used together as the decisive positive.
   * - ``/src/xml/percolator_out.xsd``
     - pout **writer**
     - The ``schemaLocation`` tail the writer emits. Same pattern.
   * - ``xmloutput``, ``decoy-xml-output``, ``pout.xml``
     - option parser
     - Present in both twins of 3.05--3.08; absent in 3.09. Do not separate
       the twins.
   * - ``xerces``
     - pin-XML **reader**
     - Thousands of hits in an ``XML_SUPPORT=ON`` build, **zero** in its
       ``noxml`` twin. This is the marker the ``noxml`` name predicts.
   * - ``percolator_in.xsd``, ``per-colator.com/percolator_in``
     - pin-XML **reader**
     - Present only in the ``XML_SUPPORT=ON`` build.
   * - ``Compiler flag XML_SUPPORT was off``
     - pin-XML **reader**
     - Present only in the ``noxml`` twin -- it is that build's own runtime
       refusal message, and it is emitted **only** on the ``--xml-in`` path.

The last three rows are the ones the project has been reading as "cannot emit
XML". They are correct about ``XML_SUPPORT`` and wrong about the writer.

The proof that ``XML_SUPPORT`` gates only the reader is the ``noxml`` build's
own diagnostic. Running ``percolator --xml-in`` on the 3.07.1 ``noxml`` Linux
binary prints::

    ERROR: Compiler flag XML_SUPPORT was off, you cannot use the -k flag for
    pin-format input files

while the same binary's ``-X`` path runs to completion. Both twins' ``--help``
list ``-X/--xmloutput``, ``-Z/--decoy-xml-output``, ``--xml-in`` and
``--stdinput-xml``; the two help texts are identical.


The sweep
=========

Thirty-one ``percolator`` binaries from twenty-nine published artefacts across
``rel-3-05`` .. ``rel-3-09``, both twins where upstream publishes twins, all
three tier-1 platforms. Only the ``percolator`` component is swept;
``percolator-converters`` and ``elude`` are different programs.

Every artefact was downloaded from its GitHub release, unpacked without root
using the signed-off extractors in ``scripts/feasibility/`` (``extract_deb.py``,
``extract_pkg.py``, ``extract_rpm.py``, ``extract_nsis.py``) or Python's
``zipfile``, and scanned. No installer was run and nothing was installed.

rel-3-09 -- no pout XML anywhere
--------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 26 10 10 24

   * - Artefact
     - Binary, size, sha256 (12)
     - Kind
     - pout?
     - Evidence
   * - ``percolator-v3-09-linux-amd64.deb``
     - ``usr/bin/percolator``, 1786248, ``1f067b5d438a``
     - ELF x86-64, ``GLIBC_2.38``
     - **no**
     - inferred -- writer literals absent; does not load here
   * - ``percolator-v3-09-linux-x86_64.rpm``
     - ``usr/bin/percolator``, 1599152, ``c31f613929f0``
     - ELF x86-64, ``GLIBC_2.14``
     - **no**
     - **executed**
   * - ``percolator-osx-portable.zip``
     - ``.../build/percolator/src/percolator``, 1782248, ``f07159a51dab``
     - Mach-O **arm64**, minos **15.0.0**
     - **no**
     - inferred -- writer literals absent
   * - ``percolator.exe``
     - ``percolator.exe``, 640512, ``1dd24985f010``
     - PE32+ x86-64
     - **no**
     - inferred -- writer literals absent

rel-3-08 -- both twins have the writer; the platform story does not improve
---------------------------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 26 10 10 24

   * - Artefact
     - Binary, size, sha256 (12)
     - Kind
     - pout?
     - Evidence
   * - ``percolator-v3-08-linux-amd64.deb`` (xml)
     - ``usr/bin/percolator``, 5015144, ``906a9181afb2``
     - ELF x86-64, ``GLIBC_2.38``
     - **yes**
     - inferred -- writer literals present; does not load here
   * - ``percolator-noxml-v3-08-linux-amd64.deb``
     - ``usr/bin/percolator``, 1921544, ``7ceb280ed526``
     - ELF x86-64, ``GLIBC_2.38``
     - **yes**
     - inferred -- writer literals present; does not load here
   * - ``percolator-noxml-osx-portable.zip``
     - ``.../percolator-noxml/src/percolator``, 1380952, ``02112ea81b88``
     - Mach-O **arm64**, minos **15.0.0**
     - **yes**
     - inferred -- writer literals present
   * - ``percolator-noxml-windows-portable.zip``
     - ``percolator.exe``, 720896, ``33139c905ba0``
     - PE32+ x86-64
     - **yes**
     - inferred -- writer literals present

**rel-3-08 publishes no Linux portable archive.** Its five assets are the
converters ``.deb``, two ``percolator`` ``.deb`` files, and the macOS and
Windows ``noxml`` portable zips. Both Linux ``.deb`` payloads demand
``GLIBC_2.38`` and ``GLIBCXX_3.4.32`` and fail at the dynamic loader on this
glibc 2.36 host.

rel-3-07-01 -- every artefact has the writer
--------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 26 10 10 24

   * - Artefact
     - Binary, size, sha256 (12)
     - Kind
     - pout?
     - Evidence
   * - ``percolator-v3-07-linux-amd64.deb`` (xml)
     - ``usr/bin/percolator``, 6242960, ``83f594c3abba``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- "Percolator version 3.07.1, Build Date Jun 20 2024
       13:21:20"
   * - ``percolator-noxml-v3-07-linux-amd64.deb``
     - ``usr/bin/percolator``, 2182688, ``9e140ade13e3``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- "Percolator version 3.07.1, Build Date Jun 20 2024
       13:20:18"
   * - ``percolator-noxml-ubuntu-portable.zip``
     - ``percolator``, 2538632, ``1ba38acf0952``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- same banner as the ``noxml`` ``.deb``
   * - ``percolator-v3-07-osx-x86_64.pkg`` (xml)
     - ``usr/local/bin/percolator``, 5066896, ``b00536476eb9``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred -- writer literals present
   * - ``percolator-noxml-v3-07-osx-x86_64.pkg``
     - ``usr/local/bin/percolator``, 1149936, ``052f8c7a6fc1``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred -- writer literals present
   * - ``percolator-noxml-osx-portable.zip``
     - ``.../percolator-noxml/src/percolator``, 1368048, ``a071eaba5609``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred -- writer literals present
   * - ``percolator-v3-07.exe`` (xml, NSIS)
     - ``INSTDIR/bin/percolator.exe``, 804864, ``044f3957e2f0``
     - PE32+ x86-64
     - **yes**
     - inferred -- writer literals present; imports ``xerces-c_3_1.dll``
   * - ``percolator-noxml-v3-07.exe`` (NSIS)
     - ``INSTDIR/bin/percolator.exe``, 707072, ``b9d9bbe82bc4``
     - PE32+ x86-64
     - **yes**
     - inferred -- writer literals present
   * - ``percolator-noxml-windows-portable.zip``
     - ``percolator.exe``, 707072, ``b9d9bbe82bc4``
     - PE32+ x86-64
     - **yes**
     - inferred -- writer literals present

The Windows portable zip's ``percolator.exe`` and the ``noxml`` NSIS
installer's ``percolator.exe`` are the **same file** -- sha256
``b9d9bbe82bc4a68d367a8cb00a0a22892b0b1cb516510fd0459d1df6805f059f``, 707072
bytes, both. The portable zip is that one binary and nothing else.

rel-3-06-05 and rel-3-05 -- same pattern, further back
-------------------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 26 10 10 24

   * - Artefact
     - Binary, size, sha256 (12)
     - Kind
     - pout?
     - Evidence
   * - ``percolator-v3-06-linux-amd64.deb`` (xml)
     - ``usr/bin/percolator``, 6201968, ``229b3a21a2ce``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- "Percolator version 3.06.5, Build Date Feb  8 2024
       10:03:24"
   * - ``percolator-noxml-v3-06-linux-amd64.deb``
     - ``usr/bin/percolator``, 2153960, ``2a9c30443ada``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- "... Feb  8 2024 10:02:23"
   * - ``percolator-noxml-linux-portable.zip``
     - ``percolator``, 2448768, ``1f1034b4f265``
     - ELF x86-64, ``GLIBC_2.14``
     - **yes**
     - **executed** -- "... Feb  8 2024 10:00:35"
   * - ``percolator-noxml-ubuntu-portable.zip``
     - ``percolator``, 2507600, ``32cd8a06ec40``
     - ELF x86-64, ``GLIBC_2.34``
     - **yes**
     - **executed** -- "... Feb  8 2024 10:02:23"
   * - ``percolator-v3-06-osx-x86_64.pkg`` (xml)
     - ``usr/local/bin/percolator``, 5130888, ``bb76a07687ea``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred
   * - ``percolator-noxml-v3-06-osx-x86_64.pkg``
     - ``usr/local/bin/percolator``, 1230192, ``9188e901332c``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred
   * - ``percolator-noxml-osx-portable.zip``
     - ``my_build/percolator-noxml/src/percolator``, 1471048, ``f6c627105bc2``
     - Mach-O x86-64, minos 12.7.0
     - **yes**
     - inferred
   * - ``percolator-v3-06.exe`` (xml, NSIS)
     - ``INSTDIR/bin/percolator.exe``, 861184, ``68e3d622b998``
     - PE32+ x86-64
     - **yes**
     - inferred
   * - ``percolator-noxml-v3-06.exe`` (NSIS)
     - ``INSTDIR/bin/percolator.exe``, 764416, ``7839dee577fc``
     - PE32+ x86-64
     - **yes**
     - inferred
   * - ``percolator-noxml-windows-portable.zip``
     - ``percolator.exe``, 764416, ``7839dee577fc``
     - PE32+ x86-64
     - **yes**
     - inferred -- same file as the NSIS payload again
   * - ``osx.zip`` (3.05, xml)
     - ``.../percolator-v3-05-osx-x86_64.pkg``, 5156708, ``e2497bdc1086``
     - Mach-O x86-64, minos 10.12.0
     - **yes**
     - inferred
   * - ``osx.zip`` (3.05, noxml)
     - ``.../percolator-noxml-v3-05-osx-x86_64.pkg``, 1467208, ``a823438d129a``
     - Mach-O x86-64, minos 10.12.0
     - **yes**
     - inferred
   * - ``win64.zip`` (3.05, xml)
     - ``.../percolator-v3-05.exe``, 929280, ``394a22a29c24``
     - PE32+ x86-64
     - **yes**
     - inferred
   * - ``win64.zip`` (3.05, noxml)
     - ``.../percolator-noxml-v3-05.exe``, 807936, ``c3d06f377db5``
     - PE32+ x86-64
     - **yes**
     - inferred

``rel-3-06-05``'s ``percolator-noxml-linux-portable.zip`` is the most portable
Linux Percolator in the whole sweep: it demands only ``GLIBC_2.14`` and
``GLIBCXX_3.4.21``, against ``GLIBC_2.34`` for every other 3.06/3.07 Linux
binary and ``GLIBC_2.38`` for both 3.08 ones.

``rel-3-05``'s ``osx.zip`` and ``win64.zip`` are **not** portable builds: each
is a zip *containing* the release's OS packages, which this sweep unpacks as a
second stage. ``rel-3-07`` (2024-05-31) publishes no assets at all, and
``rel-3-08-01`` has no GitHub release -- both confirm the specification.


Is the emitted XML usable, or merely well-formed?
=================================================

Byte-level equivalence of the two twins' output
-----------------------------------------------

Both 3.07.1 Linux binaries were run on the same input and their outputs
compared field by field, not just diffed.

Synthetic PIN, 400 PSMs (200 target / 200 decoy), generated by the sweep
script:

.. list-table::
   :header-rows: 1
   :widths: 30 18 18 34

   * - Measure
     - ``noxml`` build
     - ``xml`` build
     - Verdict
   * - ``-X`` output size
     - 143904 bytes
     - 143890 bytes
     - differs only by the recorded command line
   * - ``<psm>`` / ``<peptide>``
     - 200 / 200
     - 200 / 200
     - same
   * - ``-X -Z`` output size
     - 301598 bytes
     - 301584 bytes
     - differs only by the recorded command line
   * - ``-X -Z`` ``<psm>`` / ``<peptide>``
     - 400 / 400
     - 400 / 400
     - same
   * - ``-X -Z`` ``p:decoy="true"``
     - 400
     - 400
     - same
   * - all 400 ``<q_value>``, ``<pep>``, ``<svm_score>``
     - --
     - --
     - **element-for-element identical**
   * - whole file with ``<command_line>`` masked
     - 143682 bytes
     - 143682 bytes
     - **byte-identical**

Real Comet output, 3638 PSMs (the Phase 00 fixture run, produced by work unit
8 and used here read-only):

* ``noxml`` portable zip binary ``-X -Z`` -> 3017978 bytes; XML-capable
  ``.deb`` binary -> 3017976 bytes; **byte-identical with ``<command_line>``
  masked**.
* 3638 ``<psm>``, 3382 ``<peptide>``, 2954 ``p:decoy="true"`` from both.
* The ``-r``/``-m`` tab-delimited target and decoy files are byte-identical
  between the two builds (``cmp`` reports no difference).

**``-Z`` works on the ``noxml`` build.** That is the prerequisite for
Limelight's ``--import-decoys``, and it was executed, not inferred.

XSD validation -- and an upstream schema defect
-----------------------------------------------

Validation uses the JDK's own ``javax.xml.validation`` through
``scripts/feasibility/noxml-sweep/PoutXsdValidate.java``, run on Liberica JDK
25.0.4.1+1 at ``tools/liberica-jdk-25.0.4.1+1``. External DTD and schema
resolution are disabled; only local files are read.

Fourteen documents from six different binaries -- ``noxml`` and ``xml``, 3.06.5
and 3.07.1, ``.deb`` and portable-zip, with and without ``-Z`` -- were validated
against ``percolator_out.xsd`` as upstream ships it.

**All fourteen failed, including every document from the XML-capable build**,
with one error each::

    cvc-complex-type.3.1: Value '3' of attribute 'p:majorVersion' of element
    'percolator_output' is not valid with respect to the corresponding
    attribute use. Attribute 'p:majorVersion' has a fixed value of '2'.

The shipped schema declares ``<xsd:attribute ref="majorVersion" use="required"
fixed="2"/>``, a leftover from Percolator 2.x, while every 3.0x writer emits
``p:majorVersion="3"``. The same ``percolator_out.xsd`` (sha256
``21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865``) ships in
every ``.deb`` and ``.pkg`` from 3.05 to 3.08, both twins. The Windows NSIS
installers ship a CRLF variant of the same content
(``c4c664ea673817ded4616958b0682f401f940f40212246473e75835f3597bc1b``).

This is an upstream defect, it is **not** caused by the ``noxml`` build, and it
affects the XML-capable build identically. It matters because ``R-TOOL-02``
installs that schema next to the binary, and any product code that validates
Percolator output against it as shipped will reject correct output.

With that one ``fixed="2"`` constraint relaxed on a copy of the schema -- the
shipped file is never modified -- **all fourteen documents validate with zero
problems**, ``noxml`` and ``xml`` alike.

The validator is not a rubber stamp
-----------------------------------

A validator that never fails proves nothing, so the sweep also validates a
deliberately corrupted document, produced by injecting a bogus element and a
non-numeric q-value into a real output. It is **rejected by both schemas**:

Against the shipped schema, 4 problems::

    error at line  7 col 89: cvc-complex-type.3.1: ... 'p:majorVersion' has a
                             fixed value of '2'.
    error at line 15 col 21: cvc-complex-type.2.4.a: Invalid content was found
                             starting with element '{...:bogus_element}'.
    error at line 21 col 51: cvc-datatype-valid.1.2.3: 'NOT_A_NUMBER_...' is
                             not a valid value of union type 'probability_t'.
    error at line 21 col 51: cvc-type.3.1.3: The value 'NOT_A_NUMBER_...' of
                             element 'q_value' is not valid.

Against the relaxed schema, the three real errors remain and the spurious
``majorVersion`` one is gone. So the relaxed schema still rejects bad content;
it only stops rejecting the version attribute upstream's own writer emits.

End to end: the Limelight converter accepts ``noxml`` output
-------------------------------------------------------------

This is the question that decides everything, and it was executed.

Work unit 8 owns the scientific path and the Comet run; no Comet run was
performed here. Its Comet outputs
(``20100614_Velos1_TaGe_SA_K562_3.pin`` / ``.pep.xml``) and ``comet.params``
were copied read-only into ``scratch/u10/limelight/``, Percolator was run there
by both 3.07.1 twins, and ``cometPercolator2LimelightXML.jar`` v2.8.1 was run
on each result:

.. list-table::
   :header-rows: 1
   :widths: 34 33 33

   * - Step
     - From the ``noxml`` portable zip
     - From the XML-capable ``.deb``
   * - Percolator ``-X`` exit / size
     - 0 / 1683223 bytes
     - 0 / 1683225 bytes
   * - Converter exit
     - **0**
     - **0**
   * - Limelight XML size
     - **7678081 bytes**
     - 7678077 bytes
   * - Limelight XML, with ``conversion_date`` and ``arguments`` masked
     - 7677949 bytes
     - 7677949 bytes -- **byte-identical**
   * - Validated against ``limelight-xml.xsd``
     - **VALID**, 0 problems
     - **VALID**, 0 problems

The only differences between the two Limelight files are the conversion
timestamp and the recorded ``arguments`` string, which names the input path.

Work unit 8 reached the same conclusion independently on its own run
(``scratch/u8/run/ll/limelight-from-noxml.xml``, 12797010 bytes, against its
``limelight-noZ.xml`` at 12797004 bytes). That evidence is unit 8's to report;
it is named here only because two independent paths agreeing is worth knowing.


rel-3-09 in detail -- the number that still bounds everything
==============================================================

Executed. ``scratch/u10/extract/rel-3-09__percolator-v3-09-linux-x86_64.rpm/usr/bin/percolator``,
1599152 bytes, sha256 ``c31f613929f06ef0...``, banner **"Percolator version
3.09.0, Build Date May 21 2026 17:16:38"**. The binary hard-links
``libboost_filesystem.so.1.66.0`` and ``libboost_system.so.1.66.0``, which the
package does not ship; work unit 3 extracted those from CentOS 8 RPMs without
root, and the sweep puts them on ``LD_LIBRARY_PATH`` and records that it did.

* ``--help`` lists **51** long options. The only one containing "xml" is
  ``--pepxml-output`` (``-Q``), which writes a *rudimentary pepXML* file, a
  different format that the Limelight Comet+Percolator converter does not
  accept in place of pout XML.
* ``-X``, ``--xmloutput``, ``-Z``, ``--decoy-xml-output`` and ``--xml-in`` are
  each rejected: ``ERROR: the option -X is invalid.``
* No hidden option: of 656 option-shaped strings in the binary, exactly one
  contains "xml" -- ``pepxml-output``. The literals ``<percolator_output``,
  ``</percolator_output>``, ``percolator_out.xsd``, ``per-colator.com``,
  ``xmloutput``, ``decoy-xml-output``, ``pout.xml``, ``xml-in`` and ``xerces``
  all occur **zero** times.
* The same absence holds for the 3.09 ``.deb``, the macOS portable zip and the
  Windows ``percolator.exe`` (inferred; writer literals absent in all three).

**3.09 cannot emit pout XML, by any route.** The ceiling for a Limelight-enabled
run is 3.08.x, exactly as the specification says -- for a different reason than
the specification gives.


rel-3-08 in detail -- what changes and what does not
=====================================================

The question was whether a 3.08 portable archive can emit pout XML on all three
platforms, which would move "latest compatible" to 3.08.0 and largely retire
the extraction problem. The answer is **no, and the reason is availability, not
capability**.

.. list-table::
   :header-rows: 1
   :widths: 16 30 54

   * - Platform
     - 3.08 artefact
     - Consequence
   * - Linux
     - **no portable archive published**; two ``.deb`` files, both needing
       ``GLIBC_2.38``/``GLIBCXX_3.4.32``
     - Payload extraction is still required, and the glibc floor excludes
       RHEL 9, Ubuntu 22.04 and Debian 12. Extraction is root-free, so on a
       glibc >= 2.38 host 3.08 is reachable -- the ``noxml`` ``.deb`` is
       1921544 bytes against the XML build's 5015144 and carries the same
       writer.
   * - macOS
     - ``percolator-noxml-osx-portable.zip``, **arm64**, ``LC_BUILD_VERSION``
       minos **15.0.0**
     - No extraction and no Rosetta 2 -- but **no Intel Mac support at all**,
       and macOS 15.0 or newer only. 3.08 publishes **no** macOS x86-64
       artefact of any kind.
   * - Windows
     - ``percolator-noxml-windows-portable.zip``, PE32+ x86-64, 720896 bytes
     - No NSIS extraction. Ships the bare ``percolator.exe`` only -- no MSVC
       runtime DLLs, no XSDs.

The macOS row is the one that inverts ``D-004``'s story rather than resolving
it. 3.07.1's macOS binaries are x86-64 with a **macOS 12.7** floor: under
Rosetta 2 they run on every supported Apple-silicon Mac *and* natively on every
Intel Mac. 3.08's arm64/macOS-15.0 build runs on neither an Intel Mac nor an
Apple-silicon Mac still on macOS 13 or 14. Choosing 3.08 on macOS trades a
Rosetta 2 dependency for a narrower hardware and OS floor; it does not remove a
constraint.


What would break if the project switched to portable ``noxml`` artefacts
=========================================================================

XSD companion files (``R-TOOL-02``)
-----------------------------------

``R-TOOL-02`` requires the XSDs to be installed with the binary. Verified by
listing every extracted payload:

* Every ``.deb``, ``.pkg`` and NSIS ``.exe`` -- **including every ``noxml``
  twin** -- ships both ``share/xml/percolator/xml-pout-1-5/percolator_out.xsd``
  and ``xml-pin-1-3/percolator_in.xsd``.
* **No portable archive ships any XSD.** Every portable zip in the sweep --
  Linux, macOS and Windows, 3.06.5 through 3.09 -- contains exactly one member:
  the bare executable.

So switching to portable archives means the product must obtain the XSDs some
other way. Options, with costs:

#. Extract the XSDs from the matching ``noxml`` ``.deb``/``.pkg``/NSIS artefact
   and ship only those two files alongside the portable binary. *Cost:* a
   second download per platform; the extraction machinery already exists and is
   signed off. *Benefit:* the schemas provably match the release.
#. Vendor the two XSDs in the CometGUI repository, checksummed, per Percolator
   release. *Cost:* a redistribution question the owner must answer; the
   schemas are part of an Apache-2.0 project, but that is a ``D-001``-shaped
   call, not an agent's. *Benefit:* no extra download.
#. Do not install the XSDs at all. *Cost:* breaks ``R-TOOL-02`` as written.
   Recorded only for completeness; not recommended.

Whichever is chosen, the shipped ``percolator_out.xsd`` cannot be used
unmodified as a validation gate -- see the ``fixed="2"`` defect above. If the
product validates Percolator output, it must either carry a corrected schema
(and say so in provenance) or validate structurally rather than by XSD.

``--xml-in`` (pin-XML input) is not needed
------------------------------------------

Comet writes its Percolator input as **tab-delimited PIN**, not pin-XML. The
Comet binary's own parameter text says so::

    output_percolatorfile = 0   # 0=no, 1=yes  write Percolator pin file
    pinfile_protein_delimiter = # blank = default 'tab' delimiter between
                                # proteins; ... Percolator pin output only

and the Phase 00 fixture's Comet output
(``20100614_Velos1_TaGe_SA_K562_3.pin``, 3639 lines) has the TSV header
``SpecId<TAB>Label<TAB>ScanNr<TAB>...``. Percolator's own help calls pin-XML
"deprecated". **The product never needs ``--xml-in``**, which is the only thing
``XML_SUPPORT=OFF`` actually removes.

The capability probe in ``R-PERC-02``
-------------------------------------

This is the design consequence that matters most, because the current probe
gets it wrong.

* A probe that tests for ``-X`` in ``--help`` **does not discriminate**: both
  twins' help texts are identical and both list ``-X``. It would correctly
  reject 3.09 and tell you nothing about anything else.
* ``scripts/feasibility/probe_xml_capability.py`` (work unit 3, signed off)
  currently reports ``NOT XML-capable`` for
  ``rel-3-07-01/percolator-noxml-ubuntu-portable.zip``'s binary -- the same
  binary this unit executed, whose output the Limelight converter consumed into
  a schema-valid Limelight file. Its 3.09 verdict is correct. Its ``noxml``
  verdict answers "was this built with ``XML_SUPPORT=ON``", which is a real and
  useful question, but not the one ``R-PERC-02`` needs. That script is another
  unit's and was not modified here.

**Recommended probe.** Functional, not static. Implemented and demonstrated as
``python3 scripts/feasibility/noxml_sweep.py --probe BINARY``:

#. Write a tiny synthetic PIN -- 64 target and 64 decoy rows with separable
   features. It must not be smaller than about 20+20: at 8+8 a fully capable
   3.07.1 binary aborts with "median decoy score <= score at 1% FDR" and writes
   nothing, so an over-small fixture makes the probe report a false negative.
#. Run ``percolator -X <tmp> <pin>``. Require exit 0 **and** that the file
   exists **and** that it contains ``<percolator_output`` **and** the
   ``http://per-colator.com/percolator_out/`` namespace **and** exactly 64
   ``<psm>`` elements.
#. Run ``percolator -X <tmp> -Z <pin>``. Require the same, with 128 ``<psm>``
   elements and at least one ``p:decoy="true"``. This yields a *separate*
   capability flag, because Limelight's ``--import-decoys`` needs it and a
   future release could plausibly keep one and drop the other.
#. Record the version banner, the binary's size and its SHA-256 with the
   result, and put all of it in the provenance record.

Verified on every binary this host can execute:

.. list-table::
   :header-rows: 1
   :widths: 52 14 16 18

   * - Binary
     - ``pout_xml``
     - ``pout_xml_decoys``
     - Correct?
   * - 3.07.1 ``noxml`` ubuntu portable
     - ``True``
     - ``True``
     - yes
   * - 3.07.1 ``noxml`` ``.deb``
     - ``True``
     - ``True``
     - yes
   * - 3.07.1 XML ``.deb``
     - ``True``
     - ``True``
     - yes
   * - 3.06.5 ``noxml`` linux portable
     - ``True``
     - ``True``
     - yes
   * - 3.09 ``.rpm`` (loads with Boost 1.66)
     - ``False``
     - ``False``
     - yes
   * - 3.09 ``.deb`` (does not load here)
     - ``False``
     - ``False``
     - yes -- reported as "does not load", not as "not capable"

The probe is also the honest answer to ``R-PERC-01``: it is a *post-install
runtime probe on that platform*, which is what the rule already asks for. It
cannot be run at manifest-authoring time for a platform the build host is not;
that is a limitation of any functional probe and must be stated in the manifest
rather than papered over.

Windows: what the portable zip does not contain
-----------------------------------------------

``percolator-noxml-windows-portable.zip`` holds ``percolator.exe`` and nothing
else. That binary imports ``MSVCP140.dll``, ``VCRUNTIME140.dll``,
``VCRUNTIME140_1.dll`` and ``VCOMP140.DLL``, which the NSIS installer ships
next to it (``concrt140.dll``, ``msvcp140*.dll``, ``vcruntime140*.dll``,
``vcomp140.dll`` -- nine DLLs) and the zip does not. The 3.08 and 3.09 Windows
binaries import the same set; 3.09 adds ``bcrypt.dll`` (a system DLL).

So the Windows portable route removes the NSIS extraction but adds a
Visual C++ runtime dependency the product must satisfy -- by shipping the
redistributable DLLs, by requiring the VC++ redistributable, or by extracting
those DLLs from the ``noxml`` NSIS installer, which contains the *same*
``percolator.exe`` byte for byte.

Linux glibc floors
------------------

Highest required symbol version per binary, read from the ELF version-needs
strings:

.. list-table::
   :header-rows: 1
   :widths: 46 18 18 18

   * - Binary
     - GLIBC
     - GLIBCXX
     - Loads here (2.36)?
   * - 3.06.5 ``noxml-linux-portable.zip``
     - 2.14
     - 3.4.21
     - yes
   * - 3.06.5 ``.deb``, both twins
     - 2.34
     - 3.4.29
     - yes
   * - 3.06.5 ``noxml-ubuntu-portable.zip``
     - 2.34
     - 3.4.29
     - yes
   * - 3.07.1 ``.deb``, both twins
     - 2.34
     - 3.4.29
     - yes
   * - 3.07.1 ``noxml-ubuntu-portable.zip``
     - 2.34
     - 3.4.29
     - yes
   * - 3.08 ``.deb``, both twins
     - 2.38
     - 3.4.32
     - **no**
   * - 3.09 ``.deb``
     - 2.38
     - 3.4.32
     - **no**
   * - 3.09 ``.rpm``
     - 2.14
     - 3.4.21
     - yes, with Boost 1.66 supplied

3.07.1's portable Linux archive carries the same ``GLIBC_2.34`` floor as its
``.deb``, so on Linux the portable route changes the packaging and not the
reach.


Verdict
=======

.. _noxml-established:

(a) What is now established
---------------------------

Executed here, on named binaries:

#. The 3.07.1 and 3.06.5 ``noxml`` Linux builds -- ``.deb`` payload and
   portable archive alike -- write Percolator pout XML under ``-X``.
#. They write decoys under ``-Z``. ``p:decoy="true"`` count 400 of 400 on the
   synthetic PIN, 2954 on the real fixture.
#. Their output is **byte-identical** to the XML-capable twin's apart from the
   ``<command_line>`` element, on both a synthetic and a real 3638-PSM input,
   including every q-value, PEP and SVM score.
#. The Limelight converter v2.8.1 consumes ``noxml`` output and produces a
   7678081-byte Limelight XML file that **validates against Limelight's own
   XSD**, byte-identical to the one produced from the XML build's output once
   the timestamp and argument string are masked.
#. Percolator 3.09 cannot emit pout XML: 51 options, none of them ``-X``, no
   writer literals in the binary, every historical flag explicitly rejected.
#. The shipped ``percolator_out.xsd`` rejects **correct** Percolator 3.x output
   because of a ``fixed="2"`` ``majorVersion`` constraint. This is upstream's
   defect and affects both twins equally.
#. No portable archive ships the XSDs; every ``.deb``/``.pkg``/NSIS artefact
   does, ``noxml`` twins included.
#. ``XML_SUPPORT=OFF`` removes only the pin-XML *input* path, evidenced by the
   ``noxml`` build's own message on ``--xml-in``.
#. Comet writes tab-delimited PIN, so the product never needs pin-XML input.

(b) What remains inference
--------------------------

Everything about Windows and macOS. This host cannot execute a PE or a Mach-O
binary, and nothing in this unit changes that.

#. That the macOS and Windows ``noxml`` artefacts of 3.05--3.08 emit pout XML is
   **inferred** from the presence of the writer's own literal output fragments
   ``<percolator_output`` and ``</percolator_output>`` and of the ``-X``/``-Z``
   option strings, together with the absence of any Percolator-3.09-style
   removal. The inference is strong -- it is the same source tree, the same
   CMake flag, and the discriminator was validated against four executable
   binaries on each side -- but it is an inference.
#. That the 3.08 Linux binaries (both twins) emit pout XML is likewise
   inferred: they carry the writer literals but need ``GLIBC_2.38`` and do not
   load here.
#. That the 3.09 macOS and Windows binaries cannot emit pout XML is inferred
   from the same markers being absent; only the 3.09 Linux ``.rpm`` was
   executed.
#. macOS architecture and OS floors come from ``LC_BUILD_VERSION`` /
   ``LC_VERSION_MIN_MACOSX`` parsed from the Mach-O load commands, not from a
   Mac. The arm64-only, macOS-15.0 reading of 3.08's and 3.09's macOS builds
   has not been confirmed on hardware.
#. Whether Rosetta 2 actually runs the 3.07.1 macOS binary correctly is
   untested and untestable here.

(c) What this means for ``D-002``, ``D-003`` and ``D-004``
----------------------------------------------------------

These are owner decisions. What follows is evidence and costed options, not an
answer.

``D-002`` -- XML-capable Percolator artefact strategy
    The **decision** ("do not build from source; take 3.07.1's published
    artefacts") is untouched: 3.07.1 remains the newest release with a
    pout-XML-capable binary for all three tier-1 platforms, because 3.09 cannot
    and 3.08 publishes nothing usable for macOS Intel or for Linux below glibc
    2.38.

    The **verification text under it is wrong in one respect** and should be
    corrected: it says XML capability follows from the naming A/B, and that is
    false for the writer. Concretely, this line --

        "Windows: ... XML capability is **inferred** from the naming A/B and
        from size (1776 KB versus the ``noxml`` twin's 1193 KB, +49% ...)"

    -- infers the right conclusion from the wrong evidence. The size difference
    is Xerces-C, which serves the *reader*. Both Windows binaries carry the
    writer literals.

    The engineering consequence is a real option the owner now has:

    *Option A (status quo).* Extract ``.deb`` / ``.pkg`` / NSIS payloads for
    3.07.1 on all three platforms. *Cost:* three extractors, all already
    written and signed off; NSIS extraction verified by work unit 4. *Benefit:*
    XSDs and the Windows MSVC DLLs come with the payload; nothing new to
    decide.

    *Option B (portable ``noxml`` archives for 3.07.1).* A single zip per
    platform: Linux 2538632 bytes, macOS 1368048 bytes, Windows 707072 bytes.
    *Cost:* the XSDs must come from somewhere (see ``R-TOOL-02`` above); the
    Windows MSVC runtime DLLs must come from somewhere; the macOS binary is
    still x86-64 so Rosetta 2 is still required; the Linux glibc floor is
    unchanged at 2.34. *Benefit:* no NSIS extraction, no ``.pkg`` extraction,
    no ``ar``/cpio work on the download path -- ``zipfile`` only.

    *Option C (hybrid).* Portable archive for the binary, package payload for
    the two XSDs and, on Windows, the DLLs. *Cost:* two downloads per platform.
    *Benefit:* smallest primary download, provably matching schemas. It is the
    option this evidence most naturally supports, but it is still the owner's
    call.

``D-003`` -- managed version/platform coverage
    Widened, not settled. Every ``noxml`` artefact from 3.05 to 3.08 is now a
    *candidate* for the Limelight path rather than only for rescoring, which
    changes what the matrix in ``docs/platform_support.rst`` can offer. Two new
    facts belong in that matrix: 3.06.5's ``noxml-linux-portable.zip`` has a
    ``GLIBC_2.14`` floor, the lowest anywhere in the sweep; and 3.09's Linux
    ``.rpm`` needs Boost 1.66 shared objects it does not ship, which is a
    shipping question of its own.

``D-004`` -- macOS architecture policy
    The **decision** (Rosetta 2 for the Percolator stage) survives, because the
    macOS artefact for 3.07.1 is x86-64 whether it comes from the ``.pkg`` or
    the portable zip. But its stated **rationale** is now incomplete in a way
    the owner should see: 3.08 and 3.09 publish **arm64** macOS builds with a
    **macOS 15.0** floor. An arm64 Percolator therefore does exist upstream --
    just not one that both writes pout XML *and* runs on an Intel Mac or on
    macOS 13/14. The trade is:

    * 3.07.1 x86-64 + Rosetta 2: reaches Intel Macs and all Apple-silicon Macs
      from macOS 12.7 up; needs Rosetta 2 installed.
    * 3.08 arm64: no Rosetta; reaches Apple-silicon Macs on macOS 15.0+ only;
      **no Intel Mac support at all**.

    ``D-004``'s sentence "Ruling out source builds leaves no ``arm64``
    XML-capable Percolator" should be re-read as "leaves no arm64 pout-XML
    Percolator that also covers Intel Macs and macOS < 15".

(d) Recommended capability probe for ``R-PERC-02``
--------------------------------------------------

The functional probe described above, and nothing static. Restated as a rule:

    A Percolator binary is eligible for the Limelight path if and only if,
    **on the host it will run on**, ``percolator -X <file> <tiny.pin>`` exits 0
    and writes a ``<percolator_output>`` document in the
    ``percolator_out/15`` namespace with the expected ``<psm>`` count; and it
    is eligible for ``--import-decoys`` if and only if the same holds with
    ``-Z`` added and the document contains ``p:decoy="true"`` entries. Neither
    the artefact's name, nor its size, nor the presence of ``-X`` in
    ``--help``, nor the presence or absence of Xerces symbols shall be used to
    decide this.

Two subsidiary rules fall out of the evidence:

* The probe fixture must be large enough for cross-validation to converge
  (>= 20 targets and 20 decoys observed here; 64+64 recommended). A
  too-small fixture turns a capable binary into a false negative.
* "Does not load" and "loaded and cannot emit XML" are different results and
  must be reported differently. ``R-PERC-01``'s loadability probe and this
  capability probe are two gates, not one.


Confirm / contradict, item by item
==================================

Against ``specification.rst``, *Percolator versions and artefact availability*:

.. list-table::
   :header-rows: 1
   :widths: 44 14 42

   * - Specification statement
     - Finding
     - Evidence
   * - "Percolator 3.09 removed XML/XSD I/O"
     - **CONFIRM**
     - Executed: 3.09.0 rejects ``-X``, ``-Z``, ``--xml-in``; no writer
       literals in any 3.09 artefact.
   * - "the Limelight converter hard-requires Percolator XML"
     - **CONFIRM**
     - Converter's only Percolator input option is ``-p/--percolator-file
       <percolator output XML file>``; 3.09's ``--pepxml-output`` is a
       different format.
   * - "XML ... is the compile-time option ``XML_SUPPORT``, default ``OFF``"
     - **CONFIRM**
     - The ``noxml`` builds carry the literal "Compiler flag XML_SUPPORT was
       off"; the XML builds carry Xerces and do not.
   * - "Upstream's ``noxml`` artefacts are the default build; the XML-capable
       ones are ``-DXML_SUPPORT=ON`` builds"
     - **CONFIRM**
     - Xerces symbol counts: 10305 / 0 (3.07.1 Linux), 5997 / 0 (3.07.1
       macOS), 108 / 0 (3.07.1 Windows).
   * - '"latest compatible" means the newest release whose published artefacts
       include an XML-capable build for every tier-1 platform ... That is
       3.07.1'
     - **CONFIRM**, different reason
     - The conclusion holds. The reason is not that 3.08's portable archives
       lack the writer -- they have it -- but that 3.08 publishes no Linux
       portable archive, its ``.deb`` files need glibc 2.38, and its only
       macOS artefact is arm64/macOS-15.0.
   * - Table: ``rel-3-09`` Linux/Windows/macOS "no XML"
     - **CONFIRM**
     - Executed for the Linux ``.rpm``; inferred (writer literals absent) for
       the other three.
   * - Table: ``rel-3-08-01`` "no release published (tag only)"
     - **CONFIRM**
     - No ``rel-3-08-01`` entry in the release enumeration.
   * - Table: ``rel-3-08`` Windows/macOS "**no XML build published**"
     - **CONTRADICT** (as to capability)
     - Correct that no ``XML_SUPPORT=ON`` build is published. **Wrong** if read
       as "cannot emit pout XML": both 3.08 portable archives carry the writer
       literals, and their Linux sibling with the same literals is the same
       build configuration this unit executed at 3.07.1.
   * - Table: ``rel-3-07-01`` / ``rel-3-06-05`` rows marked "XML"
     - **CONFIRM**, incomplete
     - Those artefacts are XML-capable. The table omits that the ``noxml``
       twins and portable archives of the same releases also emit pout XML.
   * - "Every portable archive upstream publishes is a ``noxml`` build"
     - **CONFIRM**
     - True for every portable archive in the sweep.
   * - '"Portable" and "XML-capable" have not coexisted ... so the XML-capable
       artefacts are all operating-system *packages* ... **The installer must
       extract their payloads rather than run them**'
     - **CONTRADICT**
     - The premise is true and the conclusion does not follow. Portable
       archives emit pout XML. Payload extraction remains *necessary* only for
       the XSD companions and, on Windows, the MSVC runtime DLLs -- not for
       the capability.
   * - "3.07.1 is markedly *more* portable than 3.08.0 ... ``GLIBC_2.34`` ...
       3.08.0 build's ``GLIBC_2.38``"
     - **CONFIRM**
     - Measured: 2.34 / 3.4.29 versus 2.38 / 3.4.32; the 3.08 binaries fail to
       load on this glibc 2.36 host.
   * - "The macOS artefact is **x86-64 only**. On Apple silicon the Percolator
       stage runs under Rosetta 2"
     - **CONFIRM** for 3.07.1, **incomplete** overall
     - 3.07.1 macOS binaries are x86-64, minos 12.7.0. But 3.08 and 3.09
       publish **arm64** macOS builds (minos 15.0.0), so "Percolator has no
       arm64 macOS build" is not true of the project as a whole.
   * - "The XML builds ship XSD companion files ... which must be installed
       with the binary (``R-TOOL-02``)"
     - **CONFIRM**, incomplete
     - The ``noxml`` ``.deb``/``.pkg``/NSIS artefacts ship the **same** two
       XSDs. No portable archive ships either. And the shipped
       ``percolator_out.xsd`` rejects correct 3.x output.
   * - "3.07.1 predates ... I-splines ... and the PEP > 1.0 fix (#394)"
     - not re-tested
     - Out of this unit's scope; carried unchanged.
   * - "``R-PERC-02`` ... a probed capability set"
     - **CONFIRM** the rule, **CONTRADICT** any static implementation
     - ``--help`` is identical between the twins; the existing static probe
       misclassifies a proven-capable binary.


What remains UNVERIFIED
=======================

#. **No Windows or macOS binary was executed.** Every non-Linux verdict in this
   document is a byte-marker inference. A Windows runner and a Mac -- Intel and
   Apple silicon -- are needed before the manifest claims any of it.
#. **The 3.08 Linux binaries were never run** (glibc 2.38 unavailable here).
   Their capability is inferred from writer literals.
#. **Rosetta 2** has not been exercised on any Percolator binary.
#. **macOS floors** (arm64, minos 15.0.0 for 3.08/3.09; x86-64, minos 12.7.0
   for 3.06/3.07) are read from Mach-O load commands, not confirmed on
   hardware.
#. **The Limelight import itself** was not performed -- the converter's output
   was produced and schema-validated, but no Limelight server was contacted
   (``D-006``/``D-007`` territory, and out of scope here).
#. **``--import-decoys`` end to end** was not completed on this fixture: the
   converter rejected the ``-Z`` output because the fixture's FASTA does not
   contain the ``DECOY_`` entries Comet generated internally. It failed
   **identically, byte for byte in stderr, for both twins**, so it is not a
   ``noxml`` defect; but the successful ``--import-decoys`` path belongs to
   work unit 8, which has its own run.
#. **Whether pepXML from 3.09 could ever feed Limelight** was not investigated
   beyond establishing that the Comet+Percolator converter's Percolator input
   is pout XML. A different Limelight importer may exist; that is a Phase 12
   question.
#. **Releases below ``rel-3-05``** were not swept.
#. **Observation, not a finding:** ``extract_nsis.py`` unpacks the ``rel-3-05``
   installers into a directory whose name is the three bytes
   ``0x03 0x9f 0x80`` rather than ``INSTDIR`` -- the files are all present and
   correct, only the top-level directory name is mis-decoded, and only for
   ``rel-3-05``. 3.06.5 and 3.07.1 unpack to ``INSTDIR`` correctly. That script
   belongs to another work unit and was not modified.


Reproducing this
================

::

    python3 scripts/feasibility/noxml_sweep.py                # full sweep
    python3 scripts/feasibility/noxml_sweep.py --no-download  # offline re-run
    python3 scripts/feasibility/noxml_sweep.py --probe <binary>

The script downloads, extracts, scans, executes, validates against the XSD and
runs the negative control, and writes ``scratch/u10/sweep.json``. It exits
non-zero if the corrupted negative control ever validates, because a validator
that cannot fail proves nothing.

The XSD validator is ``scripts/feasibility/noxml-sweep/PoutXsdValidate.java``,
run in single-file source mode on the project-local JDK
(``tools/liberica-jdk-25.0.4.1+1``; ``. tools/env.sh``). It is also what
validated the Limelight output above.

Nothing in this unit installed anything on the host, used ``sudo`` or ``apt``,
ran an installer, or required root. Downloads live under ``scratch/u10/``,
which is gitignored.
