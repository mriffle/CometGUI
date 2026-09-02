==================================================================
PHASE-05 handoff -- Tool Registry and Installer (mid-phase)
==================================================================

:Phase: 05
:Written: 2026-09-02
:Outcome so far: **four of twelve units signed off; the phase is INCOMPLETE and
   is handed over mid-flight**, at tier 1's direction, while the record can be
   written from a quiet tree rather than from exhaustion
:Phase orchestrator: Phase-05 orchestrator subagent (session 05)
:Records: ``handoffs/PHASE-05-worklog.rst`` -- the decomposition, every unit's
   sign-off, every injection with its failure text, and the findings this
   document only summarises
:Tree this describes: ``00e6494``, ``git status --short`` empty

In one line: **the download, verification and extraction half of the installer
is built, gated and proven against real upstream artefacts; the probe, install,
UI and end-to-end half is not started.**

.. contents:: Contents
   :depth: 2
   :local:

.. _p05h-numbers:

Every number here, and the tree it came from
=============================================

Phase 04's handoff was written from a moving tree and every headline figure had
to be re-taken. So: **each figure below was measured by the phase orchestrator,
personally, on a quiet tree with no agent live and nothing uncommitted**, and the
commit is named. Anything I did not measure that way is marked.

.. list-table::
   :header-rows: 1
   :widths: 40 18 42

   * - Measurement
     - Tree
     - Value

   * - ``bash scripts/build.sh``
     - ``00e6494``
     - ``11/11 stages OK in 1085 seconds. BUILD OK``;
       ``tests=2962 failures=0 errors=0 skipped=3``

   * - ``bash scripts/verify-all-gates.sh``
     - ``96e7da4`` (phase start)
     - 11 controls passed, 0 failed, 2926s. **Not re-run since; must be
       re-taken before sign-off.**

   * - ``cometgui-domain`` coverage / census / mutation
     - ``00e6494``
     - line 100.0% (832/832), branch 100.0% (350/350); 49 compiled, all 49 in
       the sample; 368/369 = 99.7%

   * - ``cometgui-install`` coverage / census / mutation
     - ``00e6494``
     - line 100.0% (1831/1831), branch 99.4% (746/750); 44 compiled, all 44 in
       the sample; 848/863 = 98.2%

   * - Critical package prefixes in ``pom.xml``
     - ``00e6494``
     - **13**, up from 11 at phase start

   * - Reactor tests
     - ``96e7da4`` then ``00e6494``
     - 1756 -> **2962**

The three skips are: two in ``cometgui-provenance`` from Phase 04, and the
opt-in upstream download test declining without its flag **with a printed
reason**. None is in this phase's new code.

.. _p05h-units:

Unit state, one line each
==========================

.. list-table::
   :header-rows: 1
   :widths: 5 20 12 63

   * - #
     - Unit
     - State
     - The defect I injected, and what it produced

   * - 1
     - Domain tool vocabulary
     - **SIGNED OFF** ``42033ad``
     - ``DeclaredCapability``'s blank-note rejection skipped when evidence is
       ``UNVERIFIED``. **Survived 108 tests.** After rework it fails 18 of 171:
       ``every evidence value must reject a blank note ... expected:
       <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES, UNVERIFIED]> but
       was: <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES]>``

   * - 2
     - Manifest and strict reader
     - **SIGNED OFF** ``eac6d5e``
     - Three of mine. Inverting native-before-translated fails **three** tests
       (one before the rework). Keying the one-row-per-download rule on version
       rather than URL fails two. Keeping the translated row fails with ``pdv is
       a JAR, and a row marked TRANSLATED_ROSETTA_2 would tell a scientist that
       a Java program runs under Rosetta 2 ==> expected: <NATIVE> but was:
       <TRANSLATED_ROSETTA_2>``

   * - 3
     - Downloader and checksum decision
     - **SIGNED OFF** ``12d871e``
     - Progress on a **resumed** transfer reported from the resume point.
       **Survived 338 tests.** Now fails: ``expected: <1500000> but was:
       <16229>`` and ``monotone ACROSS the resume boundary ... expected: <true>
       but was: <false>``

   * - 4
     - Safe extraction (``R-SEC-05``)
     - **SIGNED OFF** ``00e6494``
     - ``hasDriveLetter`` returning false: **bit**, 8 failures, because the
       refused-path table already carried ``C:/x``. Then I deleted each of the
       five XXE guards in turn myself; all five fail, ``DISALLOW_DOCTYPE`` with
       4 of 6.

   * - 5-12
     - Install, probes, adapters, local binary, UI, end-to-end, docs, harness
     - **NOT STARTED**
     - No agent was dispatched. Nothing is landed-but-unsigned anywhere.

**Nothing is in the landed-but-unsigned state.** Every unit above was
re-verified by the orchestrator running the checks itself, not by reading a
report, and every one was sent back at least once.

.. _p05h-survey:

The upstream survey -- the least recoverable thing in this document
====================================================================

Twenty-four artefacts fetched by pinned URL on 2026-09-02, 145 MB, each with
size, SHA-256 and MD5. The raw table is ``scratch/phase05/SURVEY.tsv`` and the
assembled one ``scratch/phase05/MANIFEST-INPUT.txt`` -- **both gitignored, so if
that directory is lost the survey must be re-run.** The values that survive in
git are in ``manifests/tools.json``, and I re-derived **all 54** of them from the
bytes with **zero mismatches**.

**Upstream had not moved**: every SHA-256 Phase 00 recorded on 2026-08-29
reproduced byte for byte, and all four ``latest`` tags were unchanged.

Facts that were not known before this phase
--------------------------------------------

#. **A genuine upstream artefact contains a path-traversal entry.**
   ``rel-3-06-05/percolator-noxml-osx-portable.zip`` has one member, named
   ``../my_build/percolator-noxml/src/percolator``. Gate item 3 therefore has a
   **real** artefact behind it, which no security rule in this project has had.
#. **Percolator 3.09's Windows artefact is a bare ``percolator.exe``**, 640512
   bytes, **not a zip**. Any code assuming "Percolator implies ZIP" is wrong.
#. **Percolator 3.09 has no Linux row at all** -- no portable archive; the
   ``.deb`` needs ``GLIBC_2.38`` *and* ``libboost_filesystem.so.1.83.0`` which it
   does not ship. Both failures were reproduced here. Absent is the honest
   entry.
#. **The XSD pair is byte-identical across platforms and versions**
   (``21204c89...`` and ``fa50a550...`` from the 3.07.1 ``.deb``, the 3.07.1
   ``.pkg``, the 3.06.5 ``.deb`` and the 3.06.5 ``.pkg``). That is what makes
   the Windows sourcing decision safe.
#. **The two Comet macOS binaries are exactly the same size** (3998328) and have
   **different** SHA-256 values, closing Phase 00's open question 10. Do not
   deduplicate them.
#. **PDV is the only multi-entry archive the product installs**: 222 entries,
   115057606 bytes uncompressed from 103407417, ratio **1.11**. The most
   expansive real artefact anywhere is the 3.09 ``.deb`` at ratio **4.046**.
   Those two numbers are what the decompression-bomb ceilings are calibrated
   against.

The capability probe, executed
-------------------------------

Against the binary from ``rel-3-07-01/percolator-noxml-ubuntu-portable.zip``:

* **64 target + 64 decoy**, ``-X`` -> exit 0, 46601 bytes, **exactly 64**
  ``<psm``, correct root element and the
  ``http://per-colator.com/percolator_out/`` namespace.
* ``-X -Z`` -> exit 0, 96997 bytes, **128** ``<psm``, both ``p:decoy="true"``
  and ``"false"``.
* **8 target + 8 decoy** -> exit **1**, ``median decoy score <= score at 1%
  FDR`` -- the documented false negative, reproduced.
* ``--help`` from the portable and the ``.deb`` ``noxml`` binaries is
  **byte-identical, 17928 characters**, both listing ``--xmloutput`` and
  ``--decoy-xml-output``. **A text probe discriminates nothing.**

**Two traps the specification's wording does not cover.** On the failing 8+8 run
the output file **exists and is zero bytes**, so "the file exists" is not a
sufficient probe condition. And ``--help`` arrives on **stderr** -- confirmed
independently on Windows -- so a probe reading stdout alone sees an empty
string. Units 6 and 7 are bound by both.

Loader failures, executed
--------------------------

Both ``R-PLAT-03`` shapes were produced on this host (Debian 12, glibc 2.36)
from the 3.09 ``.deb`` payload, so the classifier is written against observed
text:

* as published -- ``error while loading shared libraries:
  libboost_filesystem.so.1.83.0: cannot open shared object file``, exit 127;
* with a stub of that library on ``LD_LIBRARY_PATH``, exposing the layer
  beneath -- ``libstdc++.so.6: version 'GLIBCXX_3.4.32' not found`` and
  ``libc.so.6: version 'GLIBC_2.38' not found``, exit 1.

``readelf -V`` floors: 3.06.5 portable ``GLIBC_2.14``, 3.07.1 portable
``GLIBC_2.34``, 3.09 ``.deb`` ``GLIBC_2.38``.

.. _p05h-windows:

Windows, as of 2026-09-02: one thing observed, most things still not
---------------------------------------------------------------------

A GitHub ``windows-latest`` runner executed Percolator 3.07.1's portable
``noxml`` binary -- **the artefact the product ships** -- on 2026-09-02, run
33644055780. It printed its banner, exited 0, and wrote 148272 bytes holding
**200 ``<psm>`` and 200 ``<peptide>``** elements, parsed rather than grepped, and
``--xml-in`` answered ``ERROR: Compiler flag XML_SUPPORT was off``, exit 1 --
which is a positive observation of a *negative* capability and confirms the
functional probe's discriminator works there.

**What that licenses, and nothing more.** ``percolator 3.07.1 windows-x86-64
XML_OUTPUT`` is now ``observed-by-execution``. ``XML_DECOY_OUTPUT`` on that row
is **deliberately still an inference**: the run exercised ``-X`` and
``--xml-in``, not ``-X -Z``, and 200 psm from 200 targets is the target-only
shape.

**What is not discharged.** The zip carries no Visual C++ runtime and the
runner's image supplies one, so **a clean end-user machine remains untested** and
``requiredHostLibraries`` stands. Also untested: a standard (non-administrator)
user, consumer Windows 10/11 rather than Server 2025, and Windows on ARM. **No
macOS binary has been executed anywhere in this project, still.**

.. _p05h-decisions:

Decisions taken, so a fresh agent does not re-litigate them
============================================================

**The Tool Manager UI may not reference the installer.** ``LayeringRulesTest``
restricts ``org.cometgui.ui..`` to java/javax/javafx/ui/domain/workflow/results/
provenance/params and forbids ``java.net``, ``java.security``, ``java.util.zip``
and ``java.util.jar``; ``cometgui-ui``'s POM declares neither installer module.
So the whole Tool Manager vocabulary lives in ``org.cometgui.domain.tools`` and
reaches the installer through a domain port. **This is why unit 1 exists**, and
unit 9 is built on it.

**One JSON reader, one hasher, one launcher, one redactor.**
``cometgui-install`` depends on ``cometgui-provenance`` and uses its
``JsonReader``; checksums go through the existing ``HashService``; processes
through ``ProcessService``; redaction through ``org.cometgui.domain.secrets``.
No second implementation of any of them.

**The manifest exists once**, at ``manifests/tools.json``, shipped into the jar
by a ``<resource>`` pointing at the repository-root directory. Both copies hash
``3f6707d3...``: one file, shipped once.

**For a ``ZIP``, the manifest names the member and the archive's own path never
places a file.** Forced by the real traversing artefact. It is **stronger** than
sanitising a name, not weaker -- and the traversal guard is still exercised
against that same artefact, so the design did not become the reason the guard is
never tested.

**Capability evidence is a field, and observation is an allowlist.** Every claim
carries how it was established. ``ShippedManifestTest`` pins the **eight**
(row, capability) pairs anyone has watched run, by hand, and a separate rule
forbids any macOS row from claiming observation. An allowlist rather than a
"must carry a note" rule, because a fabricated claim can carry a fabricated
note -- that would grade prose rather than truth.

**Windows takes the XSD pair from the Linux ``.deb``** (tier 1, 2026-09-02),
because the schemas are platform-independent and byte-identical and ``D-002``
option C deleted the only Windows source. **The manifest record says why**, so it
does not become somebody's cleanup.

**Routine tests serve real artefact bytes over loopback HTTP**, from a mirror
populated by pinned URL with mandatory SHA-256. **Stated limit:** that proves
download, verification, extraction and install against real bytes over real
HTTP; it does **not** prove upstream availability, which is ``R-TEST-08`` and
Phase 15's. ``scripts/ci/nightly-manifest-verify.sh`` remains a stub that exits
non-zero, which is correct.

**Two mutation gates were widened by me**, both ratified by tier 1. Adding a
package strengthens the gate and the POM's own comment sanctions it; the list is
never narrowed.

* ``org.cometgui.install.download.*`` -- because the availability-versus-corrupt
  distinction is a ``D-008`` product requirement. Found 9 survivors.
* ``org.cometgui.install.archive.*`` -- because ``R-SEC-05`` is this phase's
  security rule and unit 4 reported that its 8550 lines carried **no mutation
  evidence at all**. Found 62. See :ref:`the XXE finding <p05-xxe>` in the work log.

.. _p05h-gates:

The nine exit gate items
=========================

.. list-table::
   :header-rows: 1
   :widths: 5 14 81

   * - #
     - State
     - Evidence, or what is missing

   * - 1
     - **NOT MET**
     - Needs units 5, 9 and 10. The **download** half is proven through product
       code: ``mvn -o -pl cometgui-install -Dcometgui.install.upstream=true
       -Dtest=UpstreamArtefactTest -Dsurefire.failIfNoSpecifiedTests=false
       test`` fetches the real 946303-byte Percolator artefact through the real
       redirect and verifies the pinned SHA-256 in **5.2 s**. Nothing is driven
       through the UI yet.

   * - 2
     - **PARTIAL**
     - Checksum rejection is built and gated (``org.cometgui.install.verify``,
       mutation-critical); an MD5 match with a SHA-256 mismatch is a rejection.
       **The "no process was launched" assertion belongs to unit 10 and does not
       exist.**

   * - 3
     - **MET, pending my re-run at sign-off**
     - ``AttackMatrixTest``: 12 attacks x 4 multi-entry kinds, plus the real
       traversing artefact installing safely **and** being rejected whole.
       Bomb ceilings proved to bite on ratio, total size and entry count
       **separately**. Structural proof that no kind can bypass the guard.

   * - 4
     - **NOT MET**
     - Unit 5. Nothing about completion markers or interrupted installs exists.

   * - 5
     - **NOT MET**
     - Unit 6. The ``LoaderDiagnostic`` **type** exists and renders the three
       real messages; the classifier that produces one from stderr does not.

   * - 6
     - **NOT MET**
     - Unit 7. The manifest already gates ``THERMO_RAW_WINDOWS`` on its three
       companions as **data** (``gatesCapability``), so the rule is data-driven
       and not a conditional to be written.

   * - 7
     - **NOT MET**
     - Unit 8.

   * - 8
     - **PARTIAL**
     - The manifest and selection are done and tested against the shipped file:
       3.09 has no Linux row, Rosetta selection works, one row per download.
       **The UI half is unit 9.**

   * - 9
     - **CANNOT BE MET HERE**
     - No macOS machine exists. This is the item that makes the phase
       ``PARTIAL`` by ``ONBOARDING.rst``'s rule, exactly as tier 1's brief
       predicted.

.. _p05h-next:

What I would do next, in this order
====================================

#. **Re-run ``bash scripts/verify-all-gates.sh``.** The only figure in this
   document not taken on the current tree is from ``96e7da4``. It costs ~50
   minutes and must not overlap any Maven run.
#. **Unit 5, the atomic install**, and give it three things this phase learned:
   ``ArchiveMember.hashes()`` is **recorded and compared by nobody** today --
   a value with no check, which unit 4 flagged itself and which
   ``ArtefactVerifier.verify(Path, FileHashes, long, URI)`` already has the
   right signature to close; the **atomic move has no fallback** since unit 3
   removed an unreachable ``AtomicMoveNotSupportedException`` catch, whose
   reasoning covers the cross-filesystem case and **not** Windows contention
   (``AccessDeniedException`` on a file another process holds open); and unit 4
   does **not** clean the destination after a rejection, so the install must
   stage into a directory it discards.
#. **Unit 6**, adding ``minimumGlibcxx`` to ``MinimumHostRequirements`` and the
   manifest -- **in the unit that first reads it**, not before. The GLIBCXX line
   is reported *before* the GLIBC one, so a check knowing only glibc predicts
   "runnable" for a binary that fails on the C++ runtime. It also owns the
   exact-equality boundary: a host with precisely ``GLIBC_2.34`` must be
   **offered** 3.07.1.
#. **Unit 7**, the functional capability probe, held to the two traps above --
   a zero-byte output file is not success, and ``--help`` is on stderr.
#. Then 8, 9, 10, 11, 12 in order. **Unit 12 must be assembled from the
   injections recorded in the work log, not invented**, and registered in
   ``scripts/verify-all-gates.sh`` without lowering any floor.

.. _p05h-traps:

Traps that would cost hours
============================

* **``mvn -Dtest='package.*'`` silently matches zero tests and exits 0.** Both a
  phase agent and I believed an injection had survived on that basis. **Explicit
  class names only.** With ``-Dsurefire.failIfNoSpecifiedTests=false`` there is
  no warning at all.
* **A mutation-critical package with no gate switched on fails the build** the
  moment a real class lands. ``org.cometgui.install.probe.*`` and
  ``org.cometgui.tools.*`` are in ``<targetClasses>`` and **``cometgui-tools``
  still has its switch off with only ``package-info`` files**. Unit 7 must flip
  it in the same commit as its first class.
* **The per-class census fails the build.** A class whose test does not compile
  leaves the coverage sample rather than scoring low.
* **A raw NUL byte reaching a source file** has now bitten **three** times in
  this project -- Phase 03 through a heredoc, and twice in this phase, once
  through a shell heredoc and once through an agent's file-writing tool. It
  compiles, and it makes a test pass for the wrong reason. A sweep over every
  ``.java`` file currently finds none.
* **A comment line moves the compiled class hash**, because javac records a
  ``LineNumberTable``. "The class hash moved" stays mandatory; **two parties'
  injected class hashes differing is not evidence they injected different
  defects.** Compare injected *source* hashes, or the failing assertion text.
* **``build.sh`` counts only ``status='KILLED'``**, while PIT's console credits
  ``TIMED_OUT`` as a kill. The gate is stricter than the tool's own summary and
  the two figures always differ by the timeout count.
* **A commit is not a completion.** I started a build while an agent was still
  live and got ``package org.cometgui.provenance.json does not exist``, which
  would not reproduce -- the signature symptom of this project's collision, now
  four instances across three sessions. Wait for the completion notification.
  And **do not write anything the build reads** while one runs: ``handoffs/``
  and ``STATUS.rst`` are among the documents the docs gate builds.

* **A process check must exclude the checker, and a process check is not a
  completion signal.** Two different errors that look alike, and this project
  has now made both several times.

  The first is mechanical: ``pgrep -f <pattern>`` matches **its own invoking
  shell**, because the wrapper's command line contains the pattern. Tier 1's
  "wait until the harness exits" loop therefore waited forever on itself, and
  its "is the tree busy" check reported BUSY for hours while the tree was in
  fact quiet, so document edits were held back for nothing. Exclude the checker
  -- match on the JVM's real main class, as in ``pgrep -f
  org.codehaus.plexus.classworlds.launcher.Launcher``, and even then read the
  matched command lines rather than the count.

  The second is a reasoning error and is the more expensive: **"no build process
  is running" does not mean "the agent has stopped."** I used that proxy twice.
  The first time I started a build into a live agent and paid a re-run; the
  second time I caught it only because a test file happened to be mid-edit in
  ``git status``. An agent between two commands has no process at all. **The
  completion notification is the only signal that means finished**, and unit
  12's harness must not inherit the cheaper one.

.. _p05h-open:

Open threads, not tidied away
==============================

#. **An unexplained one-off failure in a security test.**
   ``XarTableOfContentsHardeningTest.everySettingIsForcedFromItsUnsafeValue``
   failed **once**, and its assertion message was lost to a truncated pipeline.
   It has not recurred in nine subsequent full-module runs and no test sets a
   JAXP system property. ``newDefaultInstance`` was pinned partly to remove
   ambient variation, but **nobody claims that was the cause**. Nine green runs
   are not an explanation. **If it reappears, that is the thread to pull.**
#. **``verify-all-gates.sh`` has not been re-run** since the phase started, and
   this phase has added two mutation-gated packages and ~1200 tests.
#. **``scripts/verify-install-gates.sh`` does not exist** (unit 12), so this
   phase has no falsifiability harness registered yet -- the same debt Phase 03
   carried.
#. **A lock that nothing takes.** Tier 1 has ruled: the record is corrected now,
   and ``build.sh`` takes a real ``flock`` **at Phase 05 sign-off**, with a
   control proving it serialises. Until then the protections are the serial rule
   and the two practices above.
#. **``ArchiveMember.hashes()`` is compared by nobody in production.** Unit 5.
#. **Three uncovered branches in ``cometgui-install``** are javac artefacts --
   two try-with-resources close paths and one synthetic default of an exhaustive
   enum switch. Not reachable from Java.

The first thing the next agent should do
=========================================

Read ``handoffs/PHASE-05-worklog.rst`` -- not this document -- for anything you
intend to rely on. This is the map; the work log is the evidence, and it carries
every injection with the exact text it produced, which is what makes the four
sign-offs checkable rather than assertions.

Then run ``bash scripts/verify-all-gates.sh`` on a quiet tree, because it is the
one figure here that describes a tree that no longer exists.
