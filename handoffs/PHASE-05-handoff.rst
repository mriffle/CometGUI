====================================================================
PHASE-05 handoff -- Tool Registry and Installer (to a successor)
====================================================================

:Phase: 05
:Written: 2026-09-02
:Outcome: **INCOMPLETE. Units 1-5 of 12 signed off; units 6-12 not started.**
   The phase is handed to a fresh phase orchestrator by owner decision, not
   because anything is wrong with it.
:Written by: the Phase-05 orchestrator (session 05), who will not be available
   to answer questions
:Records: ``handoffs/PHASE-05-worklog.rst`` -- the evidence. **This document is
   the map; the work log is the proof.** Every claim here is backed there by a
   command and the text it produced.

.. contents:: Contents
   :depth: 2
   :local:

Start here
==========

You are taking over a phase that is **five twelfths done and clean**. Nothing is
half-landed, nothing is landed-but-unsigned, and every unit that exists was
re-verified by me running its checks myself and injecting a defect its author
had not tried.

Do these four things, in this order, before you plan anything:

#. ``cd "$(git rev-parse --show-toplevel)"`` and ``. ./tools/env.sh``. **The
   checkout is not at ``/workspace``**; documents older than 2026-08-31 say it
   is and they are stale text, not a second tree.
#. ``git fetch`` and check where you are. **I am handing over at ``2f4511f``,
   which is two commits ahead of ``origin/main`` at ``38c17b5``** -- the unit 5
   sign-off and its mutation gate. Tier 1 pushes; you do not.
#. **Run ``bash scripts/verify-all-gates.sh``.** It takes ~50 minutes and it is
   the one number in this document that describes a tree which no longer exists.
   See :ref:`p05s-figures`.
#. Read ``handoffs/PHASE-05-worklog.rst`` in full. It is long. Read it anyway:
   it holds every injection with the exact failure text, and that is what makes
   the five sign-offs checkable rather than assertions you have to take from a
   stranger.

The one-paragraph version
=========================

This phase builds the thing that makes "the scientist installs nothing by hand"
true: a manifest of pinned upstream artefacts, a downloader, checksum
verification, safe extraction, an atomic install, a three-stage probe, and a
Tool Manager UI. **The download, verification, extraction and installation half
is built, gated and proven against real upstream artefacts. The probing, UI and
end-to-end half is not started.** The phase's expected grade is ``PARTIAL``,
because gate item 9 requires a macOS machine and none exists.

.. _p05s-tree:

The tree you are inheriting
============================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Thing
     - State

   * - ``git status --short``
     - empty

   * - ``HEAD``
     - ``2f4511f``, two ahead of ``origin/main``

   * - ``bash scripts/build.sh``
     - ``11/11 stages OK in 1300 seconds. BUILD OK``,
       ``tests=3140 failures=0 errors=0 skipped=3``

   * - Modules with code from this phase
     - ``cometgui-domain`` (``org.cometgui.domain.tools``, 24 classes) and
       ``cometgui-install`` (5 packages, 62 classes)

   * - ``cometgui-tools``
     - **still only ``package-info.java`` files, and its mutation switch is
       still off.** This is the single most likely way you break the build --
       see :ref:`p05s-traps`.

   * - Mutation-gated packages
     - **14** prefixes, up from 11. I added three; see :ref:`p05s-decisions`.

The three skips in the build are two of Phase 04's and one opt-in network test
declining without its flag **with a printed reason**. None is in this phase's
code, and this phase disabled nothing.

.. _p05s-units:

Units 1 to 5, and the defect I injected into each
==================================================

Every unit was sent back at least once except unit 5. **In every case the hole
was the same shape**: a rule asserted at one point on an axis it does not
actually depend on. If you read one thing about how to review this phase's work,
read that sentence again -- it predicted four of the five holes.

.. list-table::
   :header-rows: 1
   :widths: 4 17 12 67

   * - #
     - Unit
     - State
     - What I injected, and the text it produced

   * - 1
     - Domain tool vocabulary, ``org.cometgui.domain.tools``
     - **SIGNED OFF** ``42033ad``
     - Blank-note rejection skipped when evidence is ``UNVERIFIED``. **Survived
       108 tests.** After rework, 18 of 171 fail: ``every evidence value must
       reject a blank note ... expected: <[OBSERVED_BY_EXECUTION,
       INFERRED_FROM_ARTEFACT_BYTES, UNVERIFIED]> but was:
       <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES]>``

   * - 2
     - Manifest and strict reader, ``...install.registry``
     - **SIGNED OFF** ``eac6d5e``
     - Three. Inverting native-before-translated fails **three** tests (one
       before rework). Keying one-row-per-download on version not URL fails two.
       Keeping the translated row: ``pdv is a JAR, and a row marked
       TRANSLATED_ROSETTA_2 would tell a scientist that a Java program runs
       under Rosetta 2 ==> expected: <NATIVE> but was: <TRANSLATED_ROSETTA_2>``

   * - 3
     - Downloader and checksum decision, ``...download`` / ``...verify``
     - **SIGNED OFF** ``12d871e``
     - Progress on a **resumed** transfer reported from the resume point.
       **Survived 338 tests.** Now: ``expected: <1500000> but was: <16229>`` and
       ``monotone ACROSS the resume boundary ... expected: <true> but was:
       <false>``

   * - 4
     - Safe extraction, ``...archive``
     - **SIGNED OFF** ``00e6494``
     - ``hasDriveLetter`` returning false: **bit**, 8 failures, because the
       refused-path table already carried ``C:/x``. Then I deleted each of the
       five XXE guards in turn; all five fail, ``DISALLOW_DOCTYPE`` with 4 of 6.

   * - 5
     - Atomic install, marker, lock, ``...cache``
     - **SIGNED OFF** ``0ff3d72``
     - Made ``ToolCache`` trust the marker's own ``payloadEntryCount`` instead
       of counting the directory. **Bit**:
       ``ToolCacheTest.aLostFileIsCaughtByTheEntryCount:201 expected:
       <CONTENT_COUNT_MISMATCH> but was: <INSTALLED>``

   * - 6-12
     - Probes, adapters, local binary, UI, end-to-end, docs, harness
     - **NOT STARTED**
     - No agent dispatched. Nothing exists. See :ref:`p05s-remaining`.

.. _p05s-nowhere:

What I know that is written nowhere else
=========================================

This is the section I would most want if I were you, and the one that dies with
me if I do not write it.

How I actually reviewed, and why the injections that mattered all came from outside the brief
----------------------------------------------------------------------------------------------

My acceptance conditions were good enough that **every injection I chose from
inside them bit immediately** -- which tells you the agent read the brief, and
nothing else. The three injections that *survived* were all things I went
looking for **outside** the stated conditions, by asking a different question:
not "is this rule tested?" but **"what silent behaviour does this code have that
no acceptance condition names?"**

Progress reporting during a resume. A blank note under one evidence value. A
sort order that only matters when a tool has two builds for one platform. None
of those were in my own brief; all three were real.

So: **do not choose your injection from your own acceptance list.** Read the
production code for behaviour that is observable but unstated, and attack that.

The pattern that predicted four of five holes
----------------------------------------------

Every hole was **a rule graded at one point on an axis it does not depend on**.
Blank-note tested only with ``OBSERVED_BY_EXECUTION``. Progress tested only on
fresh downloads. Traversal narrowed to a leading ``..`` because every fixture
began with one. Duplicate-name asserted on one of four entry types.

When you review units 6-12, ask of each rejection: *which parameter is this
rule stated over, and which parameters is it silent about?* Then vary the silent
one.

Why I gated three packages after the fact rather than up front
---------------------------------------------------------------

I added ``org.cometgui.install.download.*`` after unit 3,
``org.cometgui.install.archive.*`` after unit 4 and
``org.cometgui.install.cache.*`` after unit 5. **I could not have chosen those
package names at decomposition time** -- I did not know what shape the code
would take, and gating a package that turned out to hold only value objects
would have been noise.

The first two each found real defects the moment they were switched on,
including five deletable XXE protections. The third found nothing, because that
unit had already pointed PIT at itself.

**Do the same for units 6-12.** ``org.cometgui.install.probe.*`` and
``org.cometgui.tools.*`` are *already* in the list, so those two are different:
they will bite the moment a class lands. Anything else new -- a UI view-model
package, for instance -- is yours to gate deliberately once you can see it.
Widening a gate never needs permission; narrowing one does.

Things I tried that did not work
---------------------------------

* **My first checksum verifier reported four mismatches that were my bug.** I
  keyed local artefact files by basename, and 3.06.5 and 3.07.1 publish their
  macOS and Windows portable zips under **the same file names**, so the map
  collided and compared one release's rows against another's files. Re-keyed by
  release tag, all 54 checksums matched. **A red result can be the harness's
  fault as easily as a green one can be a lie.**
* **I twice ran ``mvn -Dtest='SomeName'`` where the class did not exist**, got
  ``BUILD SUCCESS``, and briefly believed an injection had survived. A phase
  agent did the same thing independently on the same day. See
  :ref:`p05s-traps`.
* **I twice treated "no Maven process is running" as "the agent has stopped."**
  Once it cost a re-run; once I caught it only because a test file happened to
  be mid-edit in ``git status``.

Why the manifest lives where it does
-------------------------------------

``manifests/tools.json`` at the repository root, shipped into
``cometgui-install``'s jar by a ``<resource>`` element pointing at
``${maven.multiModuleProjectDirectory}/manifests``. **Not** a copy under
``src/main/resources``. Both the file and the jar's copy hash
``3f6707d3...``: one file, shipped once. A second copy is a second thing to
keep in step, and this project has paid for that twice.

Why the routine tests do not touch the network, and what I rejected
--------------------------------------------------------------------

Tests serve the **real artefact bytes** over a loopback HTTP server, from a
mirror populated by pinned URL with mandatory SHA-256. The stated limit is that
this proves download, verification, extraction and install against real bytes
over real HTTP, and **does not** prove upstream availability -- which is
``R-TEST-08`` and belongs to Phase 15.

I considered and **rejected** making the production HTTPS rule strict and giving
tests a permissive seam. That would run every transfer test through a seam
production does not use, which is the third of this project's nine shapes and
the exact defect I sent unit 2 back to fix. The loopback carve-out is narrower:
plain HTTP to a **127.0.0.0/8 or ``::1`` literal only**, never a name, because a
name resolves at connect time and a name-based check answers a different
question from the connection that follows. It is unreachable from product data
and its narrowness is itself graded.

Why unit 1 defines ports nobody implements
-------------------------------------------

``ToolManager``, ``InstallHandle``, ``InstallProgressListener`` and
``ToolProbe`` are declared with no implementation. That is this project's idiom,
not an oversight: ``Downloader``, ``HashService`` and ``ProcessRunner`` were all
declared phases before the phase that implemented them. The Javadoc on each says
which unit owns the implementation.

A version subtlety that will bite the UI
-----------------------------------------

``ToolVersion.equals`` is **numeric**, so ``3.07.1`` and ``3.7.1`` are one
version and ``3.09`` equals ``3.09.0``. The install cache therefore uses the
**normalised** directory name (``percolator/3.7.1/``), because text-named
directories would make one version into two. **But the marker and every
user-facing string keep upstream's spelling**, which is what a scientist can
look up. Unit 9 must render ``ToolVersion.text()`` and never the directory name.

What the phase deliberately did not do
---------------------------------------

* **``NSIS_PAYLOAD`` is not implemented and must not be.** ``D-002`` option C
  deleted it. ``ArtefactKind`` has exactly six constants and a test asserts so,
  with a Javadoc saying why, precisely so nobody "completes" the enum.
* **``scripts/ci/nightly-manifest-verify.sh`` is left as a stub that exits
  non-zero.** ``R-TEST-08`` is Phase 15's. A stub that exits non-zero is the
  correct state; making it exit 0 would be a gate weakening.
* **Nothing downloaded is committed.** The artefact mirror lives in
  ``scratch/``, which is gitignored.

.. _p05s-survey:

The upstream survey, which is the least recoverable thing here
===============================================================

On 2026-09-02 I fetched **24 artefacts by pinned URL, 145 MB**, and recorded
size, SHA-256 and MD5 for each. **Upstream had not moved**: every SHA-256 Phase
00 recorded on 2026-08-29 reproduced byte for byte and all four ``latest`` tags
were unchanged.

The raw tables are ``scratch/phase05/SURVEY.tsv`` and
``scratch/phase05/MANIFEST-INPUT.txt``. **Both are gitignored. If that directory
is gone, the survey must be re-run** -- ``scratch/phase05/fetch-survey.sh``
does it. What survives in git is ``manifests/tools.json``, whose **54**
checksums I re-derived from the bytes myself with zero mismatches.

Facts nobody knew before this phase
------------------------------------

#. **A genuine upstream artefact contains a path-traversal entry.**
   ``rel-3-06-05/percolator-noxml-osx-portable.zip`` has one member named
   ``../my_build/percolator-noxml/src/percolator``. Gate item 3 therefore has a
   **real** artefact behind it, which no security rule in this project has had.
#. **Percolator 3.09's Windows artefact is a bare ``percolator.exe``**, 640512
   bytes, not a zip. Code assuming "Percolator implies ZIP" is wrong.
#. **Percolator 3.09 has no Linux row at all.** No portable archive; the
   ``.deb`` needs ``GLIBC_2.38`` *and* ``libboost_filesystem.so.1.83.0`` which
   it does not ship. Both reproduced here. **Absent is the honest entry and a
   test asserts the absence is deliberate**, so nobody "fixes" it by adding one.
#. **The XSD pair is byte-identical across platforms and versions**, which is
   what makes Windows sourcing them from the Linux ``.deb`` safe.
#. **The two Comet macOS binaries are the same size (3998328) with different
   digests.** Closes Phase 00's open question 10. Do not deduplicate them.
#. **PDV is the only multi-entry archive the product installs**: 222 entries,
   115057606 bytes uncompressed, ratio **1.11**. The most expansive real
   artefact anywhere is the 3.09 ``.deb`` at ratio **4.046**. Those two numbers
   calibrate the decompression-bomb ceilings.

The capability probe, executed by me
-------------------------------------

Against the binary from ``rel-3-07-01/percolator-noxml-ubuntu-portable.zip``:

* **64 target + 64 decoy** with ``-X`` -> exit 0, 46601 bytes, **exactly 64**
  ``<psm``, correct root element and namespace.
* ``-X -Z`` -> exit 0, 96997 bytes, **128** ``<psm``, both decoy values.
* **8 + 8** -> exit **1**, ``median decoy score <= score at 1% FDR``. The
  documented false negative, reproduced.
* ``--help`` from the portable and the ``.deb`` ``noxml`` binaries is
  **byte-identical, 17928 characters**, both listing ``--xmloutput``.

**Two traps the specification does not state.** On the failing run the output
file **exists and is zero bytes**, so "the file exists" is not a sufficient
probe condition. And **``--help`` arrives on stderr** -- confirmed independently
on Windows -- so a probe reading stdout alone sees an empty string. Unit 7 is
bound by both.

Loader failures, executed by me
--------------------------------

Both ``R-PLAT-03`` shapes, on this host (Debian 12, glibc 2.36), from the 3.09
``.deb`` payload:

* as published: ``error while loading shared libraries:
  libboost_filesystem.so.1.83.0: cannot open shared object file``, exit 127;
* with a stub of that library on ``LD_LIBRARY_PATH``, exposing the layer
  beneath: ``libstdc++.so.6: version 'GLIBCXX_3.4.32' not found`` and
  ``libc.so.6: version 'GLIBC_2.38' not found``, exit 1.

``readelf -V`` floors: 3.06.5 ``GLIBC_2.14``, 3.07.1 ``GLIBC_2.34``, 3.09
``GLIBC_2.38``. **Unit 6 must write its classifier against those strings**, not
against invented ones.

Windows and macOS: exactly what is known
-----------------------------------------

A GitHub ``windows-latest`` runner executed Percolator 3.07.1's portable
``noxml`` binary -- the artefact the product ships -- on 2026-09-02, run
33644055780. It printed its banner, exited 0, wrote 148272 bytes holding **200
``<psm>`` and 200 ``<peptide>``** elements parsed rather than grepped, and
``--xml-in`` answered ``ERROR: Compiler flag XML_SUPPORT was off``, exit 1.

**What that licenses:** ``percolator 3.07.1 windows-x86-64 XML_OUTPUT`` is
``observed-by-execution``, and the functional probe's *detector* is proven to
work on Windows.

**What it does not:** ``XML_DECOY_OUTPUT`` on that row is still an inference --
the run never exercised ``-X -Z``, and 200 psm from 200 targets is the
target-only shape. The zip carries no Visual C++ runtime and **the runner's
image supplied one**, so a clean end-user machine is untested and
``requiredHostLibraries`` stands. Untested: standard (non-administrator) user,
consumer Windows 10/11 rather than Server 2025, Windows on ARM. **No macOS
binary has ever been executed anywhere in this project -- not on hardware, not
on a runner, not under emulation.**

There is a hand-typed allowlist in ``ShippedManifestTest`` of the **eight**
(row, capability) pairs anyone has watched run, plus a separate rule that no
macOS row may claim observation. **Adding a genuine new observation requires
editing that list**, which is the friction that forces someone to write down
what was run.

.. _p05s-figures:

Which figures to trust, one by one
===================================

.. list-table::
   :header-rows: 1
   :widths: 30 12 58

   * - Figure
     - Trust
     - Why

   * - ``build.sh`` 11/11 in 1300s, ``tests=3140``
     - **Yes**
     - Mine, on ``2f4511f``, quiet tree, ``git status`` empty.

   * - ``cometgui-install`` 100.0% line / 99.5% branch, 62/62 census,
       1087/1104 mutations
     - **Yes**
     - Same run.

   * - ``cometgui-domain`` 100.0%/100.0%, 49/49, 368/369
     - **Yes**
     - Same run.

   * - The 54 manifest checksums
     - **Yes**
     - Re-derived by me from the bytes, keyed by release tag after I found and
       fixed my own basename bug.

   * - Each unit's acceptance build
     - **Yes**
     - Each run after that unit's completion notification, ``git status``
       clean.

   * - ``verify-all-gates.sh`` -- 11 controls, 0 failed, 2926s
     - **NO -- re-take it**
     - Measured on ``96e7da4`` at phase start. Not because the tree was noisy
       -- it was quiet -- but because the **tree has moved**: three mutation
       gates, ~1400 tests, and PR #1 changed ``verify-all-gates.sh`` itself.

**Two measurements were taken on a noisy tree, and neither was used for
anything.** Unit 2's first sign-off build failed with ``package
org.cometgui.provenance.json does not exist`` because I started it while the
agent was live; discarded and re-run clean. My first unit 4 XXE attempt ran
while the agent was mid-edit *and* used a class-name pattern that matched
nothing; discarded and redone. Both are recorded in the work log.

.. _p05s-remaining:

The seven remaining units, as I would now scope them
=====================================================

This is **not** my decomposition from the start of the phase. I have changed it,
and the changes are the point.

.. list-table::
   :header-rows: 1
   :widths: 5 30 65

   * - #
     - Unit
     - What I would now say, and what changed

   * - 6
     - Loadability and identity probe, ``org.cometgui.install.probe``
     - **Package is already mutation-gated**, so it bites on the first class.
       Implements unit 5's ``ToolProbe`` seam. **Add ``minimumGlibcxx`` to
       ``MinimumHostRequirements`` and the manifest here** -- not earlier, where
       it would be a field nothing reads. The GLIBCXX line is reported *before*
       the GLIBC one, so a check knowing only glibc predicts "runnable" for a
       binary that fails on the C++ runtime. **Owns the exact-equality
       boundary**: a host with precisely ``GLIBC_2.34`` must be **offered**
       3.07.1, not refused. Write the classifier against the observed strings
       above. If it ever walks an import closure, note that **an import closure
       is not one file's imports** -- ``docs/feasibility/windows-artefact.rst``
       carries that correction.

   * - 7
     - Tool adapters and the functional capability probe, ``org.cometgui.tools``
     - **Flip ``cometgui-tools``'s mutation switch in the same commit as the
       first class** or the build fails. The synthetic PIN must be **64 target
       + 64 decoy**; 8+8 produces a false negative. **A zero-byte output file
       is not success**, and **``--help`` is on stderr**. Do not let the Windows
       result harden into a claim that the XML twin's capability is known there
       -- it is not. ``THERMO_RAW_WINDOWS`` is already gated on its companions
       as manifest **data** (``gatesCapability``), so that rule is a lookup,
       not a conditional to write.

   * - 8
     - Local binary registration
     - Small. ``ToolVersion`` requiring two-to-four numeric components is what
       makes the ">= 3.05" floor a numeric comparison. **I would now merge this
       into unit 7**: it is the same adapter code with a different source of
       binary, and splitting it buys nothing.

   * - 9
     - Tool Manager UI and wiring
     - **The hardest constraint in the phase**: ``LayeringRulesTest`` forbids
       ``org.cometgui.ui..`` from touching ``org.cometgui.install..``,
       ``org.cometgui.tools..``, ``java.net``, ``java.security``,
       ``java.util.zip`` and ``java.util.jar``, and ``cometgui-ui``'s POM
       declares neither installer module. Everything the UI renders must be in
       ``org.cometgui.domain.tools``, which is why unit 1 exists. Render
       ``ToolVersion.text()``, never the cache directory name. Go through
       ``select``, **not** ``artefacts()`` -- a platform-independent artefact is
       five rows in the raw list and one offer.

   * - 10
     - End-to-end install driven through the UI
     - Gate items 1 and 2. **I would now do this before unit 9's polish**, not
       after: it is the item most likely to reveal that the domain port is the
       wrong shape, and finding that after the UI is built is expensive. Test
       PDV's cancel-and-restart deliberately -- and note **cancellation deletes
       the partial**, so a cancelled 99 MB download restarts from zero; resume
       survives a *failure*, not a cancellation.

   * - 11
     - Documentation
     - ``docs/developer/tool_registry.rst``, ``docs/tool_manager.rst``, and the
       artefact table in ``docs/platform_support.rst``. **There is no
       ``docs/user/`` directory** -- the original brief said there was.
       ``R-DOC-06``'s final wording on ``platform_support`` is **Phase 16's**;
       this phase supplies the table only. Generate the matrix from
       ``manifests/tools.json`` so it cannot diverge. Record the loopback
       carve-out and the Windows-XSD oddity here, or someone will "tidy" them.

   * - 12
     - ``scripts/verify-install-gates.sh``
     - **Assembled from the injections in the work log, not invented.** They are
       all there with their exact failure text. Register it in
       ``verify-all-gates.sh`` **without lowering any floor** -- a run grading
       fewer controls than before is a failure even when green. Its controls
       must not inherit the process-check or ``-Dtest=`` traps below.

**On sequencing**: 6 and 7 are the critical path -- everything downstream needs
a working probe. 11 can be written any time after 7. 12 must be last.

**My estimate was 12 to 20 hours for units 6-12 and I still hold it**, with the
caveat that 9 and 10 are the least predictable work in the phase: headless
JavaFX under Monocle, and a live install driven through a UI. If it moves, it
moves there.

.. _p05s-gates:

The nine exit gate items
=========================

.. list-table::
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - State
     - Evidence, or what is missing

   * - 1
     - **NOT MET**
     - Units 6, 7, 9, 10. The **download** half works through product code:
       ``mvn -o -pl cometgui-install -Dcometgui.install.upstream=true
       -Dtest=UpstreamArtefactTest -Dsurefire.failIfNoSpecifiedTests=false
       test`` fetches the real 946303-byte artefact and verifies its pinned
       SHA-256 in **5.2 s**.

   * - 2
     - **PARTIAL**
     - Proved at the installer: a corrupted download is rejected and a probe
       stub that fails if entered is never reached, graded over all four
       artefact kinds and a corrupted companion. **The UI half is unit 10.**

   * - 3
     - **MET**
     - 12 attacks x 4 multi-entry kinds; the real traversing artefact installs
       safely **and** is rejected whole; bomb ceilings bite on ratio, total size
       and entry count **separately**; a bytecode scan proves no artefact kind
       can place a file except through the guard.

   * - 4
     - **MET**
     - Interruption in a real second JVM via ``Runtime.halt`` after each of the
       eight steps; a marker whose digest stopped matching makes the entry not
       installed; two JVMs serialise with one observed to wait and a control
       showing overlap without the lock.

   * - 5
     - **NOT MET**
     - Unit 6. The ``LoaderDiagnostic`` **type** exists and renders the three
       real messages; the classifier that produces one from stderr does not.

   * - 6
     - **NOT MET**
     - Unit 7, but the manifest data is already in place.

   * - 7
     - **NOT MET**
     - Unit 8.

   * - 8
     - **PARTIAL**
     - Manifest and selection done and tested against the shipped file. **UI
       half is unit 9.**

   * - 9
     - **CANNOT BE MET HERE**
     - No macOS machine exists. This is what makes the phase ``PARTIAL``. The
       quarantine code runs on Linux through the same
       ``UserDefinedFileAttributeView`` (tier A), but **that Gatekeeper then
       accepts the binary is not proved and is claimed nowhere.**

.. _p05s-decisions:

Decisions taken, which you should not re-litigate
==================================================

* **The UI may not see the installer.** Structural, enforced by ArchUnit. The
  whole Tool Manager vocabulary is ``org.cometgui.domain.tools``.
* **One JSON reader, one hasher, one process launcher, one redactor.**
  ``cometgui-install`` depends on ``cometgui-provenance`` for ``JsonReader``.
  Never write a second one of any of them.
* **For a ``ZIP``, the manifest names the member and the archive's own path
  never places a file** -- forced by the real traversing artefact. Stronger than
  sanitising a name, and **the traversal guard is still exercised** against that
  same artefact, so the design did not become the reason the guard is untested.
* **Windows takes the XSD pair from the Linux ``.deb``** (tier 1, 2026-09-02).
  The manifest record says why, because it looks like a mistake otherwise.
* **``AtomicMoveNotSupportedException`` is re-thrown, never handled.** A copy
  fallback is not atomic and would silently replace the guarantee ``R-TOOL-04``
  rests on. Other ``FileSystemException``\s become ``CACHE_CONTENDED`` with **no
  retry**. The Windows contention case is untested and remains residue.
* **Three mutation gates were added by me**, all ratified. Adding strengthens;
  the list is never narrowed.

.. _p05s-traps:

Traps, in full
==============

* **``mvn -Dtest='package.*'`` or a misspelled class name matches zero tests and
  exits 0.** With ``-Dsurefire.failIfNoSpecifiedTests=false`` there is no
  warning at all. It caught a phase agent and it caught me, on the same day.
  **Use explicit class names and read the ``Tests run:`` count, never the exit
  code.**
* **A mutation-critical package with its module's switch off fails the build**
  the moment a real class lands. ``org.cometgui.install.probe.*`` and
  ``org.cometgui.tools.*`` are in ``<targetClasses>``, and **``cometgui-tools``
  still has its switch off with only ``package-info`` files.** Unit 7 must flip
  it in the same commit as its first class.
* **The per-class coverage census fails the build.** A class whose test does not
  compile leaves the coverage sample rather than scoring low.
* **A raw NUL byte reaching a source file has now bitten three times** -- Phase
  03 through a heredoc, and twice in this phase, once through a shell heredoc
  and once through an agent's file-writing tool. It compiles, and it makes a
  test pass for the wrong reason. Git shows the file as binary, so a reviewer
  sees no diff at all.
* **A comment line moves the compiled class hash**, because javac records a
  ``LineNumberTable``. "The class hash moved" stays mandatory as proof an
  injection reached the bytecode, but **two parties' injected class hashes
  differing is not evidence they injected different defects.** Compare injected
  *source* hashes, or the failing assertion text.
* **``build.sh`` counts only ``status='KILLED'``** while PIT's console credits
  ``TIMED_OUT`` as a kill. The gate is stricter than the tool's own summary.
* **A commit is not a completion**, and **a process check is not a completion
  signal either.** ``pgrep -f`` matches its own invoking shell, so a naive check
  reports BUSY forever; match the JVM's real main class and **read the matched
  command lines, not the count**. And an agent between two commands has no
  process at all -- **the completion notification is the only signal that means
  finished.** I broke this twice.
* **Do not write anything the build reads while a build runs.** ``handoffs/``
  and ``STATUS.rst`` are among the documents the strict docs gate builds.
* **PIT's coverage minion does not use Surefire's class path.** A child JVM
  launched with ``System.getProperty("java.class.path")`` dies under PIT with
  ``ClassNotFoundException``, hanging the parent until timeout and failing the
  PIT stage with *"tests did not pass without mutation"*. Derive it from
  ``getProtectionDomain().getCodeSource()``. And **a mutant is invisible to a
  child JVM**, which loads unmutated classes from ``target/classes``.

.. _p05s-open:

Open threads, untidied
=======================

#. **An unexplained one-off failure in a security test.**
   ``XarTableOfContentsHardeningTest.everySettingIsForcedFromItsUnsafeValue``
   failed **once**, and its assertion message was lost to a truncated pipeline.
   It has not recurred in nine subsequent full-module runs and no test sets a
   JAXP system property. ``newDefaultInstance`` was pinned partly to remove
   ambient variation, but nobody claims that was the cause. **Nine green runs
   are not an explanation. If it reappears, that is the thread to pull.**
#. **``verify-all-gates.sh`` has not been re-run** since phase start.
#. **``scripts/verify-install-gates.sh`` does not exist** (unit 12), so this
   phase has no registered falsifiability harness yet -- the same debt Phase 03
   carried.
#. **A lock that nothing takes.** Tier 1 has ruled: ``build.sh`` gains a real
   ``flock`` **at Phase 05 sign-off**, with a control proving it serialises. Not
   before, because changing the build under a live phase is the hazard itself.
   Until then the protections are the serial rule and the two practices above.
#. **``InstallLock.close`` has a surviving mutant that PIT scores ``TIMED_OUT``**
   rather than killing, because covering tests block unboundedly.
#. **Three uncovered branches in ``cometgui-install``** are javac artefacts --
   two try-with-resources close paths and one synthetic default of an exhaustive
   enum switch. Not reachable from Java.

The first thing to do
=====================

``git fetch``, confirm you are at ``2f4511f``, then run
``bash scripts/verify-all-gates.sh`` on a quiet tree. It is the only figure in
this document that describes a tree which no longer exists, and everything else
here is either measured on the tree you are holding or marked as not.

Then read the work log. This document is what I concluded; that one is what
happened.
