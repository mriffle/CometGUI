=========
Decisions
=========

:Updated: 2026-08-30 (D-008 fully decided -- publication location)

Decisions an implementing agent **must not make on its own**. Each names what
it blocks, the options with their costs, and a recommendation. ``D-009`` was
raised by Phase 01 on 2026-08-29; the numbering is open-ended, and a phase that
uncovers a question only the owner can answer gets a new entry rather than a
guess. When the owner
answers, record the answer, the date and the reasoning in the entry, set its
status to ``DECIDED``, and update ``STATUS.rst``.

An agent that invents an answer here -- adds a licence, picks a dataset, points
a test at a real server -- has made an error, not a shortcut.

Status values: ``OPEN``, ``DECIDED``, ``SUPERSEDED``.

----

D-001 -- CasanovoGUI source reuse
=================================

:Status: **DECIDED 2026-08-29**
:Raised: 2026-08-28
:Blocks: nothing further; phases 01, 02 and 16 implement it
:Owner: Project owner, with the CasanovoGUI copyright holders
:Decided by: Project owner, 2026-08-29, in session with the main orchestrator

**Question.** May CometGUI be derived from ``Noble-Lab/CasanovoGUI``, and at
what cost to CometGUI's own licensing?

.. important::

   **Upstream published a licence during Phase 00.** ``Noble-Lab/CasanovoGUI``
   added **GPL-3.0** in commit *"Add GNU General Public License v3.0"*, authored
   ``2026-08-29T01:56:35Z``. The Phase 00 work unit checked the repository
   *before* that commit and correctly recorded ``license = null``; the main
   orchestrator's independent re-check at sign-off, roughly an hour later,
   found the licence present. Both observations were accurate when made.

**Facts, verified by the main orchestrator on 2026-08-29 at sign-off.**

* ``GET /repos/Noble-Lab/CasanovoGUI`` returns
  ``license.spdx_id = "GPL-3.0"``.
* ``GET /repos/Noble-Lab/CasanovoGUI/license`` returns a ``LICENSE`` blob,
  35 149 bytes, sha ``f288702d2fa16d3cdf0035b15a9fcbc552cd88e7``, whose first
  lines are the canonical *GNU GENERAL PUBLIC LICENSE, Version 3, 29 June
  2007*.
* The commit list shows ``2026-08-29T01:56:35Z  Add GNU General Public License
  v3.0`` as the most recent commit, the one before it dated 2026-08-21.

**What this changes.** The question is no longer *"may we derive at all?"* --
GPL-3.0 grants that. It is now *"do we accept GPL-3.0 on CometGUI?"*, because
GPL-3.0 is strong copyleft: a work derived from CasanovoGUI must itself be
distributed under GPL-3.0. **This converts ``D-001`` from a permission problem
into a licence-choice problem, and couples it tightly to ``D-008``.** It must
be answered together with ``D-008``, not before it.

**Options.**

#. **Derive, and license CometGUI under GPL-3.0.** Now legally available and
   costs no negotiation. *Cost:* CometGUI is GPL-3.0 for good; institutional
   or commercial redistributors who need a permissive licence are excluded,
   and any future proprietary packaging is foreclosed.
#. **Write CometGUI independently and choose its licence freely.** The
   architecture is implementable from scratch, and Phase 00 lowered this cost
   measurably: the toolchain, packaging and GUI-automation patterns
   (Liberica Full JDK 25 + ``jpackage`` app-image + TestFX with an injected
   Monocle) were all established here without reference to CasanovoGUI.
   *Cost:* the shell, packaging and installer patterns -- roughly a phase of
   work, not a project.
#. **Ask upstream for an additional permissive licence** (dual-licensing).
   *Cost:* an email and goodwill, and upstream may decline; GPL-3.0 remains
   the fallback.

**DECISION (2026-08-29). Option 1: derive from CasanovoGUI, and release
CometGUI under GPL-3.0.** The owner accepted the copyleft commitment. The
derivation prohibition in ``R-SEC-01`` is lifted: Phase 02 may reuse
CasanovoGUI source, subject to the obligations below.

**Obligations this creates, which the implementing phases must honour.**

#. **``LICENSE``** at the repository root carries the full, unmodified GNU
   General Public License version 3 text. Phase 01 places it; it is no longer
   blocked. Do not paraphrase or truncate it.
#. **Attribution to CasanovoGUI is required, not optional.** Any file derived
   from ``Noble-Lab/CasanovoGUI`` retains its copyright notices and records the
   derivation. Phase 02 owns this.
#. **Source availability.** Every recipient of a CometGUI installer must be
   able to obtain the corresponding source. Phase 16 owns the mechanism, which
   depends on the still-open publication half of ``D-008``.
#. **Third-party attribution still applies.** Apache-2.0 is compatible with
   GPL-3.0 in this direction, so Comet, Percolator and the Limelight converter
   pose no compatibility problem; but if their binaries are *redistributed*
   rather than downloaded from upstream, Apache-2.0 s4 requires notices and a
   licence copy. None of the three ships a ``NOTICE`` file, which lightens
   this. Comet's ``LICENSE`` is Apache-2.0 plus an embedded MIT section (Gygi
   Lab, 2022), also GPL-3.0-compatible. Record all of it in
   ``docs/citations.rst``.
#. **The bundled JRE is unaffected.** Liberica is GPLv2 **with the Classpath
   Exception**, which exists precisely to permit this combination. GPLv2 alone
   would have been incompatible with the Apache-2.0 dependencies; GPL-3.0 is
   not.

**Still to confirm with upstream, and cheap.** Ask the CasanovoGUI authors to
confirm the grant covers the repository's **existing history** and not only
commits after ``2026-08-29T01:56:35Z``. The repository carries a merged
outside contribution, so the party adding the ``LICENSE`` file should confirm
the grant reaches prior contributions. This is a tidying action, not a blocker;
proceed while it is outstanding.

**``AC-REL-02``.** This entry is the recorded human sign-off for the licensing
criterion, per ``ONBOARDING.rst``'s finished condition. Phase 16's licence
audit still runs; it now verifies compliance rather than choosing a licence.

**Note on the fact table.** ``specification.rst``'s verified-facts row
"CasanovoGUI licence" and Phase 00's ``docs/feasibility/upstream-facts.rst``
both record the pre-commit ``license = null`` state. The fact-verification
script re-run at sign-off reports this row as ``CHANGED``, which is the script
working correctly.

----

D-002 -- XML-capable Percolator artefact strategy
=================================================

:Status: **DECIDED 2026-08-29**
:Raised: 2026-08-28
:Blocks: nothing further; phases 05, 09, 12, 15, 16 implement it
:Owner: Project owner, with the engineering team

**Decision.** Do not build Percolator from source. Use the most recent version
that publishes binaries for macOS, Windows and Linux *and* satisfies the XML
requirement. That version is **3.07.1** (``rel-3-07-01``, 2024-06-20).

**Verification of the decision (2026-08-29).**

* Linux: ``percolator-v3-07-linux-amd64.deb`` extracted without root and
  **executed** -- reports "Percolator version 3.07.1, Build Date Jun 20 2024";
  help lists ``-X, --xmloutput`` and ``-Z, --decoy-xml-output``. Highest
  required symbol version ``GLIBC_2.34``, so it runs on RHEL 9, Ubuntu 22.04
  and Debian 12 -- strictly better than 3.08.0's ``GLIBC_2.38``.
* macOS: ``percolator-v3-07-osx-x86_64.pkg`` is ``xar!`` + gzip + ``070707``
  cpio, extracted without root. Contains ``./usr/local/bin/percolator``
  (64-bit Mach-O), ``percolator_out.xsd`` and ``percolator_in.xsd``, and the
  strings ``xerces``, ``xmloutput``, ``pout.xml``, ``decoy-xml-output``.
  **x86-64 only** -- see ``D-004``.
* Windows: ``percolator-v3-07.exe`` is a valid NSIS installer (firstheader
  ``0xdeadbeef``). Its payload was not decompressed here, so XML capability is
  **inferred** from the naming A/B and from size (1776 KB versus the ``noxml``
  twin's 1193 KB, +49%, matching the pattern verified directly on the other
  two platforms). **Phase 00 must confirm this on a Windows runner** before
  the manifest claims it.

**Accepted trade.** 3.07.1 predates 3.08's change of default PEP regressor to
I-splines and predates the fix for PEP values exceeding 1.0 (#394, fixed in
3.08.1 and 3.09). Carried as version advisories (``R-PERC-11``) rather than
hidden. The alternatives were a version that cannot emit XML at all, or a
source build that has been ruled out.

**What remains engineering, not decision.** Payload extraction for three
package formats (``.deb`` verified, ``.pkg`` verified, NSIS to be established
in Phase 00); installing the XSD companions with the binary; detecting and
explaining Rosetta 2 on Apple silicon.

**Phase 00 correction, 2026-08-29 -- the decision stands, its rationale does
not.** Re-deriving *latest compatible* from live upstream data returns 3.7.1
(``rel-3-07-01``) under both a strict and an optimistic rule, so the chosen
version is right. But the reasoning recorded above -- that the ``noxml``
artefacts cannot produce the XML the Limelight converter needs -- is **false
for XML output**, verified by execution:

* The 3.07.1 Linux ``noxml`` build run as ``percolator -X out.xml in.pin``
  exits 0 and writes a well-formed ``percolator_out/15`` document whose bytes
  differ from the XML-capable build's output in **two lines only**, both inside
  ``<command_line>``. Confirmed independently by the main orchestrator at
  sign-off: 200 ``<psm>`` elements from each build, ``diff`` reports 2 differing
  lines.
* ``XML_SUPPORT`` gates the **pin-XML input** path, not the pout-XML output
  path. The ``noxml`` build's own message says so: ``--xml-in`` fails with
  *"ERROR: Compiler flag XML_SUPPORT was off, you cannot use the -k flag for
  pin-format input files"*. Comet writes tab-delimited PIN, so the product
  never needs that input path.
* Consequently ``--help`` text is **identical** between the twins -- both
  advertise ``--xmloutput`` and ``--decoy-xml-output``. A capability probe that
  greps help output discriminates nothing;
  ``scripts/feasibility/probe_xml_capability.py`` currently returns *"NOT
  XML-capable"* for a binary whose XML the Limelight converter consumed.
  ``R-PERC-02`` needs a **functional** probe: run the binary with ``-X`` on a
  tiny PIN and inspect the file it writes.

What does **not** change: 3.09 genuinely cannot emit XML (executed -- no XML
flags in help, no file written), so the ceiling remains 3.08.x; and 3.08's
``GLIBC_2.38`` floor and arm64-only macOS build keep **3.07.1 the right
answer**.

**The open question this raises,** costed for the owner:

* **A -- amend the rationale only.** *Cost:* a documentation edit. The
  artefact set, the manifest and Phase 05's scope are unchanged.
* **B -- also execute the Windows check.** A developer with a Windows machine
  runs the seven-step checklist in ``docs/feasibility/windows-artefact.rst``
  against ``percolator.exe`` sha256 ``044f3957...``; *cost:* ~15 minutes of one
  person, and it closes gate item 8's first branch. A GitHub Actions
  ``windows-latest`` runner is free and repeatable but is **blocked by
  ``D-008``**, there being no remote. A cloud Windows VM is under $1/hour plus
  credentials the project does not hold. Wine was assessed and **not
  attempted**: it would prove nothing about Windows.
* **C -- act on the ``noxml`` finding and re-scope Phase 05.** If the portable
  ``noxml`` archives can feed Limelight, the NSIS and ``xar``/cpio extraction
  paths become optional, and the Windows portable ZIP needs no installer
  handling at all. *Cost:* re-scoping Phase 05, plus one execution each on
  Windows and macOS to raise those halves above inference. *Benefit:* deletes
  the most fragile code the installer was going to contain.

**This is not an agent's call.** Option C changes what the product builds.

DECISION on the open question, 2026-08-29: **option C**
--------------------------------------------------------

:Decided by: Project owner, 2026-08-29, in session with the main orchestrator

**The product obtains Percolator from the portable ``noxml`` archives.** The
selected version is unchanged -- 3.07.1 remains the answer *latest compatible*
returns for a Limelight-enabled run -- but the artefact the installer fetches
changes from the operating-system package to the portable zip, on all three
tier-1 platforms.

**Why.** The premise that made package-payload extraction necessary is false.
``XML_SUPPORT`` gates the pin-XML **reader** (``--xml-in``), which Comet never
produces and the product never uses; the pout-XML **writer** is present in
every published 3.05--3.08 artefact, both twins, all three platforms, proven by
execution on Linux and by writer-literal byte markers elsewhere. A portable zip
needs no administrative rights, no installer execution and no payload
extraction anywhere.

**What this deletes.** Phase 05 does **not** implement ``NSIS_PAYLOAD`` and
does not extract the macOS ``.pkg`` for the binary. That was the most fragile
code the installer was going to contain, and it is now unwritten rather than
written and maintained.

**What this costs, carried explicitly rather than discovered in phase 05.**

#. **No portable archive ships an XSD.** Every portable zip upstream
   publishes -- Linux, macOS and Windows, 3.06.5 through 3.09 -- contains
   exactly one member: the bare executable. ``R-TOOL-02`` requires the XSD
   companions, so they come from the matching ``noxml`` ``.deb`` (Linux) or
   ``.pkg`` (macOS) as a second small download, using extraction code phase 00
   already proved and signed off. ``DEB_PAYLOAD`` and ``PKG_PAYLOAD`` therefore
   survive in ``R-TOOL-01``; ``NSIS_PAYLOAD`` does not. Vendoring the two XSDs
   in the repository would remove that download but is a redistribution
   question, which is **not decided** and which no agent may settle.
#. **The Windows portable zip is the bare ``percolator.exe`` and needs a
   Visual C++ runtime** -- ``MSVCP140.dll``, ``VCRUNTIME140.dll``,
   ``VCRUNTIME140_1.dll``, ``VCOMP140.DLL``. The NSIS installer ships nine such
   DLLs beside the binary; the zip ships none. Phase 05 must declare the
   runtime as a companion requirement and report its absence as a **loader**
   failure naming the DLL (``R-PLAT-03``), never as "not XML-capable". The NSIS
   installer's ``percolator.exe`` is byte-identical to the zip's (sha256
   ``b9d9bbe82bc4...f059f``, 707072 bytes), so extracting the DLLs from it is
   the fallback if shipping or requiring the redistributable proves
   unacceptable.
#. **The inference gap is unchanged.** No Windows or macOS Percolator binary
   has been executed anywhere in this project. Option C does not close that and
   was not chosen as if it did; it removes code, not uncertainty. Phases 05, 09
   and 15 must treat capability as probed on the host at runtime.

**Consequential edits made by the main orchestrator on taking this decision:**
``specification.rst`` revision 7 (the artefact section rewritten, ``R-TOOL-01``,
``R-TOOL-02`` and ``R-PERC-02`` amended), ``phases/PHASE-05-tool-registry.rst``
re-scoped, ``phases/index.rst`` and ``STATUS.rst`` updated.

----

Original analysis, retained
---------------------------

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

:Status: OPEN -- narrowed by ``D-002``
:Raised: 2026-08-28 (carried from specification revision 1)
:Blocks: Phases 05, 09, 15
:Owner: Project owner

**Question.** Beyond 3.07.1, which Percolator version and platform pairs does
the product offer as managed one-click installs?

**Settled by ``D-002``.** 3.07.1 is offered on all three tier-1 platforms as
the XML-capable default. That is the pair set the Limelight path needs.

**Still open.** Which *additional* versions to carry for users who do not need
Limelight -- 3.09 is the obvious candidate, since it is current -- and whether
to carry any version below 3.07.1 at all. (The reason once given for 3.09,
that "its portable archives need no payload extraction", is now both corrected
and moot: it publishes no Linux portable archive, and since ``D-002`` option C
*no* Percolator artefact needs payload extraction for its binary.) Adapter- and schema-level support for >= 3.05 is
feasible; managed installation depends on a usable binary existing per pair,
and several pairs have no published artefact.

**Recommendation.** Publish the matrix explicitly in
``docs/platform_support.rst``: for each pair, managed / local-binary-only /
unsupported. The UI must not offer what the manifest does not contain
(``R-PERC-01``), and CI must not test pairs the product does not offer.

**Phase 00, 2026-08-29 -- this decision is *widened*, not narrowed.** The
``noxml`` builds do emit pout XML, and ``D-002`` option C is now decided on
that basis, so every ``noxml`` artefact from 3.05 to 3.08 **is** a Limelight
candidate rather than a possible one -- and 3.06.5's
``percolator-noxml-linux-portable.zip`` carries the lowest glibc floor found
anywhere in the release history (``GLIBC_2.14``), which makes it the obvious
candidate for users on older Linux distributions that 3.07.1's ``GLIBC_2.34``
excludes. That is a widening of the matrix this decision must set, not a
change to the default: ``R-PERC-02`` still computes the default, and 3.07.1
still wins for a Limelight-enabled run on a current host. Two corrections to
this entry's own text, both verified:

* **3.09 publishes no Linux portable archive at all**, and its ``.deb``/
  ``.rpm`` need Boost shared libraries they do not ship -- the extracted 3.09
  binary fails to load here with ``libboost_filesystem.so.1.66.0: cannot open
  shared object file``. "Its portable archives need no payload extraction" is
  false on Linux.
* **3.09's macOS build is arm64-only, minimum macOS 15.0**, so it reaches
  *fewer* Macs than 3.07.1 x86-64 under Rosetta 2.

Populate the matrix from the functional capability probe, never from artefact
names.

----

D-004 -- macOS architecture policy
==================================

:Status: **DECIDED 2026-08-29**, as a consequence of ``D-002``
:Raised: 2026-08-28
:Blocks: nothing further; phases 05 and 16 implement it
:Owner: Project owner

**Decision.** The Percolator stage runs under **Rosetta 2** on Apple silicon.
Ruling out source builds leaves no ``arm64`` XML-capable Percolator: the only
XML-capable macOS artefact for 3.07.1 is ``osx-x86_64``. Comet still runs
natively (it publishes an ``aarch64`` macOS build), so only the Percolator
stage is translated. The application shall detect Apple silicon, verify Rosetta
2 is present before the stage runs, and explain the requirement rather than
failing with an exec-format error.

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

**Phase 00, 2026-08-29 -- the fact is now sharper.** PDV 2.7.0 **does** ship a
control server (``PDVGUI/gui/utils/PdvControlServer``, offering ``/ready``,
``/select`` and ``/shutdown`` on ``127.0.0.1``), but it is reachable only via
``java -jar PDV.jar denovo-gui --mztab <f> --spectrum <f>``, and
``spectra_ref`` resolution exists only in ``MztabImport``. Comet plus
Percolator never produces mzTab, so **there is no path from CometGUI's results
to that server today**. The upstream contribution is therefore *cheaper than
assumed* -- it extends an existing mechanism rather than inventing one -- but
it remains on a third party's schedule. Recommendation unchanged: baseline for
release 1.

Two operational findings Phase 11 must plan around, both verified: PDV's CLI
is **not headless** (it constructs a ``JFrame`` and throws
``HeadlessException``), and it **exits 0 having written nothing** when given
the same indexed mzML Comet searched -- msftbx numbers spectra by file
position while pepXML refers to them by scan number. MGF is the working route.
``-rt 6`` hangs; impose a timeout.

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

**Phase 00, 2026-08-29.** Six costed candidates with real sizes, parsed MS2
counts and licence URLs are in ``docs/feasibility/fixture-candidates.rst``.
The finding that matters: **PRIDE exposes a machine-readable per-project
``license`` field**, so "a PRIDE dataset" is not by itself a licence status --
each candidate must be checked individually. The spectra used to prove the
scientific path in this phase were an **ephemeral feasibility input**: they are
not committed, not vendored, and explicitly **not** the project's chosen
fixture. Choosing the fixture remains the owner's.

Phase 00 also produced a defence this decision should carry: **the fetched
mzML were CRLF-corrupted in transit and Comet exited 249** on them. The
corruption was proven against the files' own ``<fileChecksum>`` element. Any
fixture-fetching code must verify the checksum and fetch in binary mode.

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

**Phase 00, 2026-08-29.** No server was contacted. One enabling finding: the
Limelight converter's XSD (65 905 bytes) lives **inside the distributed JAR**
and there is no standalone copy, so a local fake endpoint can validate what it
receives by extracting the schema from the JAR at build time. A caution for
Phase 12: **the converter's exit status is unusable** -- it exits 0 with no
arguments at all and exits 0 on unrecognised options. Success must be
determined from the output file, never from the exit code.

----

D-008 -- CometGUI licence and distribution
==========================================

:Status: **DECIDED** -- licence and tool distribution 2026-08-29, publication
   location 2026-08-30. All three parts are now answered.
:Raised: 2026-08-28
:Blocks: Phase 16, and Windows CI for phase 00's gate item 8, and gate item 6
   of phase 01. **Does not block Phase 01 from running** -- see the note under
   the open half.
:Owner: Project owner

**DECIDED -- the licence.** CometGUI is released under **GPL-3.0**, following
from ``D-001``'s decision to derive from CasanovoGUI. Phase 01 places the full
GPLv3 text at the repository root and is unblocked for that deliverable.

**DECIDED 2026-08-29 -- tool binaries are downloaded, not redistributed.**
Comet, Percolator, PDV and the Limelight converter are fetched from upstream at
install time by **pinned URL and SHA-256**, as the specification's tool-registry
design already assumed. CometGUI's release artefacts contain none of them.

*What this settles.* CometGUI does not become an Apache-2.0 redistributor of
Comet, Percolator or the converter, so s4 notice obligations do not attach to
its release artefacts; PDV's unresolved upstream ``LICENSE``/``pom.xml``
contradiction cannot become a distribution problem, only a documentation one;
and the release artefacts stay small.

*What this costs, and it is not free.* Installation requires network access,
and **an upstream deletion or a re-tagged asset breaks installation for every
new user**. ``R-TEST-08``'s nightly manifest-verification job is therefore
load-bearing rather than advisory: it is the only thing that detects the
breakage before a user does. Phase 05 must also make the failure legible --
a vanished artefact is reported as an upstream availability failure with the
URL and expected checksum, never as a corrupt download or a probe failure.
Phase 16 should record a documented recovery path for a user whose artefact has
disappeared upstream, since the project holds no copy.

*Note on ``D-002`` option C.* The two decisions were taken together and are
consistent: the portable zips chosen there are downloaded by checksum like
everything else, and no artefact this project ships contains a third-party
binary.

**DECIDED 2026-08-30 -- CometGUI is published on GitHub, in a repository the
owner controls.** The repository is ``https://github.com/mriffle/CometGUI.git``.
The owner recorded two qualifications: it **may move before release**, and it
will **always be a GitHub repository under the owner's control**.

*What this settles.* The GPL-3.0 source-availability obligation has a mechanism:
recipients of an installer can obtain the corresponding source from the public
repository (Phase 16 still implements and documents the link). The prohibition
on creating a remote is **lifted**.

*What it unblocks, and this is the whole point of answering it.* GitHub Actions
runners become available, which is the only repeatable way to close the two gate
items this decision has been holding open:

* **Phase 00 gate item 8** -- no Windows binary has ever been executed anywhere
  in this project. A ``windows-latest`` runner executes the checklist in
  ``docs/feasibility/windows-artefact.rst`` and re-runs it on every change.
* **Phase 01 gate item 6** -- no pipeline has ever run on a pull request. The
  three workflow files exist and every step is proven locally; they have simply
  never been executed by GitHub.

Read the Docs also becomes buildable, which is ``AC-DOC-01``'s second clause.

*Constraints that follow, binding on every phase.* Because the location may
change, **the URL is kept in one place rather than scattered** through scripts,
POMs and configuration. Because the history is now published, **it is never
force-pushed and never rewritten**. Neither is a matter of taste: a moved
repository with the URL hard-coded in twenty files is a day of work, and a
rewritten public history breaks every clone.

**Question.** Under what licence is CometGUI released, where is it published,
and are project-built tool binaries distributed alongside it?

**Constraints.** Constrained by ``D-001`` (derivation) and ``D-002``
(redistributed binaries). *Superseded:* while this was open there was no git
remote and no agent could add one. Both halves are now answered above.

**Recommendation, as it stood.** Decide the licence early enough for phase 01
to place the file; defer publication until ``D-001`` is resolved and the
repository has been re-read for anything that should not be public.

**Phase 00, 2026-08-29 -- this decision then blocked something concrete.** Two
couplings, both now resolved:

* **``D-001`` and ``D-008`` are now one question.** CasanovoGUI became GPL-3.0
  today. Deriving from it commits CometGUI to GPL-3.0; declining that commits
  the project to writing the shell independently. Answer them together.
* **The cheapest repeatable route to Phase 00's one unmet gate item is Windows
  CI**, and there is nowhere to run it, because there is no remote and
  creating one is this decision. A free GitHub Actions ``windows-latest``
  runner would close gate item 8 permanently and would re-run on every change;
  without a remote, the only alternatives are a person with a Windows machine
  (~15 minutes, one-off, not repeatable) or a paid cloud VM.

**PDV, by owner direction 2026-08-29: treat it as GPL-3.0.** Its licensing is
self-contradictory upstream -- ``LICENSE`` at v2.7.0 is GPL-3.0 while its
``pom.xml`` declares Apache-2.0. The owner directed that the project proceed on
the assumption that **GPL-3.0 governs**, which is the conservative reading and
is now harmless: with CometGUI itself GPL-3.0, a GPL-3.0 PDV raises no
compatibility question in either direction.

Two things this assumption does **not** do, and which Phase 16 must still
close. It does not resolve the upstream contradiction -- ask the PDV authors
which licence governs, and record the answer. And it does not licence
redistribution of PDV under Apache terms: if the project ever needs the
permissive reading (for example to relicense or to redistribute PDV inside a
non-GPL artefact), that requires the upstream answer, not this assumption. Note
the assumption is directionally safe: assuming GPL-3.0 constrains the project
*more* than the Apache reading would, so acting on it cannot create a
violation.

----

D-009 -- The copyright holder named in every source file
=========================================================

:Status: OPEN
:Raised: 2026-08-29, by Phase 01, at the main orchestrator's sign-off
:Blocks: nothing yet; Phase 16 cannot complete the licence audit without it,
   and it must be settled **before any public redistribution**
:Owner: Project owner

**Question.** Whose name goes on the copyright line of every CometGUI source
file?

**What exists today.** Phase 01 placed a GPL-3.0 header on all 61 Java files,
reading ``Copyright (C) 2026 The CometGUI authors.`` That is a deliberate
placeholder, not an answer: no legal entity is named anywhere in the project.
The phase orchestrator correctly declined to substitute a name and escalated
it, which is the behaviour ``ONBOARDING.rst`` asks for.

**Why an agent may not answer it.** A copyright line asserts who owns the work.
It is a legal claim about a real person or institution, it interacts with
``D-001`` (the project is a GPL-3.0 derivative of ``Noble-Lab/CasanovoGUI``,
whose own notices must be retained in any derived file) and with ``D-008``
(source availability follows from where the work is published), and it cannot
be inferred from anything in the repository.

**Options.** Named individual author(s); an institution or laboratory; or a
project-collective form such as "The CometGUI authors" made real by a
``CONTRIBUTORS`` file that lists who that is. The third is the cheapest and is
what the placeholder already assumes -- but it is only honest once the file
exists and is accurate.

**Recommendation.** Decide it with the ``D-008`` publication question, since
both are answered by the same fact: who is publishing this and on whose behalf.
There is no deadline before Phase 16, and no phase is blocked meanwhile --
Phase 02 must simply keep the placeholder rather than inventing a name, and
must retain CasanovoGUI's own notices in anything derived from it.
