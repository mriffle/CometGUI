=========================================================
PHASE-05 work log -- Tool Registry and Installer
=========================================================

:Phase: 05
:Phase orchestrator: Phase-05 orchestrator subagent (session 05)
:Started: 2026-09-02

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Baseline observed before any work started
=========================================

``git status --short`` clean at ``96e7da4``. The checkout is at
``/mnt/10TBdrive/home/mriffle/work/comet-gui``; every document that says
``/workspace`` is stale text, not a second tree.

Tier 1's measurements on this tree, taken 2026-09-02 and not re-measured here
because the tree had not changed: ``bash scripts/build.sh`` -> ``11/11 stages
OK in 927s``, ``tests=1756 failures=0 skipped=2``, ``BUILD OK``;
``bash scripts/verify-all-gates.sh`` -> 11 controls passed, 0 failed, 2985s.
This orchestrator started its own ``verify-all-gates.sh`` run at 04:28 UTC on
2026-09-02 as an independent baseline; its result is recorded below.

``cometgui-install`` at that commit holds six ``package-info.java`` files
(``org.cometgui.install`` and its ``registry``, ``download``, ``verify``,
``archive`` and ``probe`` subpackages) and **no tests**. ``cometgui-tools``
holds six (``org.cometgui.tools`` and its ``api``, ``comet``, ``percolator``,
``pdv`` and ``limelight`` subpackages) and **no tests**. Neither module sets
``<cometgui.mutation.skip>false</cometgui.mutation.skip>``, and both must set
it in the unit that lands their first real class -- see
:ref:`p05-build-rules`.

.. _p05-decisions:

Engineering decisions taken by the orchestrator before decomposing
==================================================================

These are engineering choices, not ``D-`` items. Later phases inherit them.
Each is recorded with the constraint that forced it, so that a later phase can
see whether the constraint still holds rather than re-deriving the choice.

.. _p05-ui-cannot-see-the-installer:

The Tool Manager UI may not reference the installer, and this shapes the phase
    ``LayeringRulesTest.uiDependsOnlyOnDomainAndApplicationApis`` restricts
    ``org.cometgui.ui..`` to ``java..``, ``javax..``, ``javafx..``,
    ``org.cometgui.ui..``, ``org.cometgui.domain..``,
    ``org.cometgui.workflow..``, ``org.cometgui.results..``,
    ``org.cometgui.provenance..`` and ``org.cometgui.params..``. **Neither
    ``org.cometgui.install..`` nor ``org.cometgui.tools..`` is on that list**,
    and ``cometgui-ui``'s POM declares neither module. A second rule forbids
    ``org.cometgui.ui..`` from depending on ``java.net..``,
    ``java.security..``, ``java.util.zip..`` and ``java.util.jar..``.

    So the Tool Manager's whole vocabulary -- tool identity, version, platform,
    artefact kind, capability, install state, probe outcome, progress,
    advisories -- lives in ``org.cometgui.domain.tools``, and the UI reaches
    the installer only through a port declared there. This is not a workaround:
    it is the same seam ``ProcessRunner`` and ``HashService`` already use, and
    it is what lets the Tool Manager be tested with no installer at all.

One JSON reader, one hasher, one process launcher, one redactor
    ``cometgui-install`` takes a dependency on ``cometgui-provenance`` and uses
    ``org.cometgui.provenance.json.JsonReader`` for the manifest. Writing a
    second JSON parser is exactly the duplication that produced two divergent
    secret-redaction rule sets on 2026-08-31. For the same reason: checksums
    come from ``org.cometgui.provenance.hashing.StreamingHashService`` through
    the ``HashService`` port and nowhere else; every process is launched
    through ``ProcessService``/``StageRunner``; every redaction is
    ``org.cometgui.domain.secrets``. No cycle is created --
    ``cometgui-provenance`` depends only on ``cometgui-domain``.

The manifest exists once, at ``manifests/tools.json``
    The file is the authoritative copy and is shipped into
    ``cometgui-install``'s jar by a ``<resource>`` entry pointing at
    ``${maven.multiModuleProjectDirectory}/manifests``. A copy under
    ``src/main/resources`` would be a second file to keep in step, and the
    project has already paid for that shape twice.

For a ``ZIP`` artefact the manifest names the member; the archive's own path is never used
    Forced by a real upstream artefact, not by taste. See
    :ref:`the traversal finding <p05-traversal-upstream>`. The extractor writes the declared member to
    the declared destination and never derives an output path from an archive
    entry name. That is **stronger** than sanitising the name, not weaker: for
    a single-member portable archive no attacker-controlled string reaches the
    file system at all. Multi-entry kinds (``TAR_GZ``, ``DEB_PAYLOAD``,
    ``PKG_PAYLOAD``) are extracted wholesale and carry the full ``R-SEC-05``
    guard set, which is where the traversal, absolute-path, symlink and
    decompression-bomb tests live.

Capability is a field with an evidence value, never a bare claim
    Every capability in the manifest carries how it was established:
    ``observed-on-linux-x86-64`` where this project executed the binary,
    ``unverified-on-windows`` or ``unverified-on-macos`` where it did not.
    The words *verified*, *confirmed*, *proven* and *tested* are not used of a
    binary nobody has run, and a test enforces that mechanically over the
    manifest text. ``R-TOOL-07``'s probe still overrides the manifest on the
    host; the field exists so the UI can say what it knows before the probe
    runs, and so a fabricated row is visible as one.

Artefact bytes for routine tests come from a gitignored mirror, served over loopback
    Populating the mirror is a download by pinned URL with mandatory SHA-256
    verification; the bytes are the real upstream artefacts and the probes run
    the real binaries. What a loopback server does **not** prove is that
    upstream is still reachable, and that is deliberate: ``R-TEST-08``'s
    nightly manifest verification is Phase 15's (``scripts/ci/
    nightly-manifest-verify.sh`` is its stub today). A separate opt-in test
    fetches every manifest artefact from its real URL; its command and cost are
    recorded with the gate evidence. **Stated limit:** the routine suite proves
    the download, verification, extraction, install and probe path against real
    artefact bytes over real HTTP; it does not prove upstream availability.

Nothing downloaded is committed
    The mirror lives under ``scratch/`` and the tool cache under a temporary
    directory or ``_build/``; both are gitignored. No binary enters git.

.. _p05-u0:

Unit 0 -- the upstream artefact survey, done by the orchestrator
================================================================

Integration work, done before decomposing, because every later unit needs the
pinned table and because this is where the phase's central risk sits. Run
2026-09-02 from the repository root; the script is
``scratch/phase05/fetch-survey.sh`` (gitignored working file) and the raw
result is ``scratch/phase05/SURVEY.tsv``.

**Upstream has not moved.** Every asset named in ``specification.rst`` and in
``docs/feasibility/`` is present at the size the release API reports, and every
SHA-256 that Phase 00 recorded on 2026-08-29 was reproduced byte for byte
today: ``comet.linux.exe`` ``af515b6e...``,
``rel-3-07-01/percolator-noxml-ubuntu-portable.zip`` ``4d0e94af...``,
``rel-3-07-01/percolator-noxml-v3-07-osx-x86_64.pkg`` ``7df4b831...``,
``rel-3-09/percolator-osx-portable.zip`` ``a52ab92e...``,
``rel-3-09/percolator-v3-09-linux-amd64.deb`` ``34887435...``,
``rel-3-09/percolator-v3-09-linux-x86_64.rpm`` ``45f08388...``. The four
``latest`` releases are unchanged: Comet ``v2026.02.2``, Percolator
``rel-3-09``, PDV ``v2.7.0``, converter ``v2.8.1``.

Twenty-four artefacts were fetched, 145 MB in total. The full URL / size /
SHA-256 / MD5 table is the input to unit 2 and is reproduced in
``docs/developer/tool_registry.rst`` as this phase's provenance record.

What the survey established that was not known before
-----------------------------------------------------

.. _p05-traversal-upstream:

**1. A genuine upstream artefact contains a path-traversal entry.**
``rel-3-06-05/percolator-noxml-osx-portable.zip`` holds exactly one member,
named::

    ../my_build/percolator-noxml/src/percolator

A correct ``R-SEC-05`` traversal guard rejects that archive, so under a naive
design Percolator 3.06.5 would be uninstallable on macOS -- and the obvious
repair, taking the basename, is precisely the weakening this project forbids.
The design in :ref:`p05-decisions` resolves it without touching the guard: for
``ZIP`` the manifest names the member and the destination, and the archive's
own path is never used to place a file. The 3.07.1 and 3.09 macOS zips make
the same point more mildly, their single member being
``Users/runner/work/percolator/percolator/build/percolator-noxml/src/percolator``.

**2. Percolator 3.09's Windows artefact is a bare ``percolator.exe``, not a
zip.** ``rel-3-09`` publishes four assets and none of them is a Windows
portable archive; the Windows artefact is the executable itself, 640512 bytes.
So the manifest needs ``BARE_EXECUTABLE`` for that row, and any code that
assumes "Percolator implies ZIP" is wrong.

**3. Percolator 3.09 has no Linux row at all.** ``rel-3-09`` publishes no Linux
portable archive; its ``.deb`` needs ``GLIBC_2.38`` **and**
``libboost_filesystem.so.1.83.0``, which it does not ship. Absent is the
honest entry, exactly as ``D-003`` warned.

**4. The XSD companion pair is byte-identical across platforms and across the
two versions that ship it.** ``percolator_out.xsd``
``21204c89234b3b255fc05009ac6b956195573fce79020863f472ab64fd986865`` and
``percolator_in.xsd``
``fa50a550ea01c9109197ad2c8c9efdcdad448fddd81c5ddcf54f13f8af280f4f`` come out
identical from the 3.07.1 ``.deb``, the 3.07.1 ``.pkg``, the 3.06.5 ``.deb``
and the 3.06.5 ``.pkg``. Linux takes them from the ``.deb`` and macOS from the
``.pkg``, as ``specification.rst`` prescribes. **Windows has no prescribed
source** -- ``D-002`` option C deleted NSIS extraction -- and this is escalated
in :ref:`p05-escalations` rather than settled here.

.. _p05-central-risk:

The central risk, retired on Linux: the functional capability probe
-------------------------------------------------------------------

Executed by the orchestrator on 2026-09-02 against the binary extracted from
``rel-3-07-01/percolator-noxml-ubuntu-portable.zip`` (member ``percolator``,
2538632 bytes, sha256 ``1ba38acf0952...``).

.. list-table::
   :header-rows: 1
   :widths: 28 72

   * - Run
     - Observed

   * - ``percolator -X out.xml pin`` with **64 target + 64 decoy** rows
     - exit **0**; ``out-64.xml`` 46601 bytes; **exactly 64** ``<psm``
       elements; ``<percolator_output`` present; namespace
       ``http://per-colator.com/percolator_out/`` present.

   * - ``percolator -X out.xml -Z pin``, same fixture
     - exit **0**; 96997 bytes; **128** ``<psm`` elements; both
       ``p:decoy="true"`` and ``p:decoy="false"`` present.

   * - ``percolator -X out.xml pin`` with **8 target + 8 decoy** rows
     - exit **1**; ``Exception caught: Error: median decoy score <= score at
       1% FDR. Cannot rescale scores to merge cross validation bins, try
       lowering --trainFDR.`` -- the documented false negative, reproduced.

   * - ``--help`` from the portable ``noxml`` binary and from the ``noxml``
       ``.deb`` binary
     - **byte-identical, 17928 characters each**, both listing
       ``--xmloutput`` and ``--decoy-xml-output``, both exiting 0. A text probe
       discriminates nothing. Note also that the help text arrives on
       **stderr**, so a probe reading stdout alone sees an empty string.

**A trap the specification's wording does not cover.** On the 8+8 run the
output file **exists and is zero bytes**. "The file exists" is therefore not a
sufficient condition; the probe must require the root element, the namespace
and the exact ``<psm>`` count, as ``R-PERC-02`` says.

A real ``R-PLAT-03`` loader failure, executed
----------------------------------------------

Both shapes were produced on this host (Debian 12, glibc 2.36) from the
Percolator 3.09 ``.deb`` payload, so the classifier is written against observed
text rather than invented text:

* as published -- ``percolator: error while loading shared libraries:
  libboost_filesystem.so.1.83.0: cannot open shared object file: No such file
  or directory``, exit **127**;
* with a stub ``libboost_filesystem.so.1.83.0`` on ``LD_LIBRARY_PATH``, which
  gets past the missing object and exposes the symbol-version failure beneath
  it -- ``/lib/x86_64-linux-gnu/libstdc++.so.6: version `GLIBCXX_3.4.32' not
  found`` and ``/lib/x86_64-linux-gnu/libc.so.6: version `GLIBC_2.38' not
  found``, exit **1**.

``readelf -V`` confirms the floors the manifest will declare as
``minimumHostRequirements``: 3.06.5 portable ``GLIBC_2.14``, 3.07.1 portable
``GLIBC_2.34``, 3.09 ``.deb`` ``GLIBC_2.38``.

.. _p05-build-rules:

Two build rules that bind specific units
========================================

* **Mutation gates.** ``org.cometgui.install.registry.*``,
  ``org.cometgui.install.verify.*``, ``org.cometgui.install.probe.*`` and
  ``org.cometgui.tools.*`` are in ``<targetClasses>`` in ``pom.xml``.
  ``scripts/build.sh`` fails the build when a module compiles a class under one
  of those prefixes while its POM does not set
  ``<cometgui.mutation.skip>false</cometgui.mutation.skip>``. **Unit 2 flips it
  for ``cometgui-install``; unit 7 flips it for ``cometgui-tools``**, in the
  same commit as the first class. Switching it back off is a rejection.
* **The per-class coverage census.** Every class compiled into
  ``target/classes`` must appear in that module's ``jacoco.xml``. A class whose
  test does not compile leaves the sample rather than scoring low, and the
  build now stops. Every unit ends with ``bash scripts/build.sh`` green, and
  the census line is read, not skimmed.
* ``org.cometgui.domain.tools`` lands in ``cometgui-domain``, which is gated at
  **90% line / 85% branch over the whole module** and is mutation-critical.
  Unit 1's classes are held to that.

.. _p05-download-facts:

Download facts established by the orchestrator before unit 3
=============================================================

Measured on 2026-09-02 against the real upstream host, so that unit 3 designs
the resume path from what the server does rather than from what HTTP permits.

**A release download is a redirect to a signed URL that expires.**
``https://github.com/<owner>/<repo>/releases/download/<tag>/<file>`` answers
``302`` with ``content-length: 0`` and redirects to
``release-assets.githubusercontent.com`` with a signature carrying an expiry
about an hour out. Two consequences: the client **must follow redirects**
(Java's ``HttpClient`` does **not** by default -- its default is
``Redirect.NEVER``, and a downloader that forgets this silently writes a
zero-byte file), and a resume attempted later must **re-request the original
``github.com`` URL** to obtain a fresh signature rather than reusing a stored
redirect target.

**Range requests work.** The asset host answers ``accept-ranges: bytes`` and a
ranged request returns ``206`` with ``content-range: bytes 0-99/946303`` and an
``etag``. Resume is genuinely implementable and is not a fiction.

**``If-Range`` is ignored, and this is the trap.** Sent a deliberately stale
validator -- ``If-Range: "0xDEADBEEFDEADBEE"`` against a real ETag of
``"0x8DC9130344F5BFC"`` -- the server still answers **206 with the partial
range**, not ``200`` with the whole body. Confirmed twice: through the redirect
and again directly against the signed URL with no redirect in the picture, and
the 206 response even carries the real ETag.

So the standard mechanism for "tell me if the file changed under my partial
download" **does not work here**. If upstream re-tags an asset between the
first attempt and the resume, a client relying on ``If-Range`` splices bytes
from two different files and produces a corrupt result that no HTTP status
reveals. The rules that follow, and unit 3 is held to them:

#. Record the total length and the ``ETag`` seen on the first attempt, and
   **discard the partial file and restart** if either differs on the resume.
   That is a cheap check, but it is advisory only, because the server is under
   no obligation to keep an ETag stable.
#. **The mandatory SHA-256 verification is the sole integrity authority**
   (``R-SEC-02``), and it is what actually catches a spliced download.
#. **A resumed download that fails its checksum discards the partial file and
   restarts from zero** -- it never resumes again. Resuming a second time
   splices the same corruption back in and fails identically, which reads as an
   upstream fault when it is the client's own.

.. _p05-eleventh-shape:

An eleventh shape: a diagnostic that lies about the value it rejected
======================================================================

Catalogued at tier 1's direction, because the project has not met this one
before and will meet it again. It is a **variant of the fourth shape** -- an
assertion too coarse to see a partial failure -- but it lives somewhere nobody
looks: inside an error message, on a path where **the rule itself is working
correctly**.

``DownloadReport``'s constructor rejects a report whose parts do not add up. The
guard was right, its own arithmetic was mutation-killed, and the rejection fired
exactly when it should. What survived was the addition **inside the message**::

    "... " + resumedFromBytes + " kept plus " + bytesTransferred
           + " received is " + (resumedFromBytes + bytesTransferred)

Mutate that second addition and the class still rejects everything it should,
still throws, still passes every behavioural test -- and reports *"400 kept plus
600 received is -200"*. The assertion did not see it because it stopped at
``startsWith("a report must account for every byte")``.

**Why it is worth its own entry.** Every instinct this project has built says to
check *that* a rule fired. A diagnostic is the part a human reads at three in the
morning, months later, to find out *why*; a message that misstates the value it
rejected sends that person to the wrong place with the authority of the system
behind it. And the usual defences are all absent here: coverage sees the line
executed, mutation testing needs the message pinned to notice, and a test that
greps the opening words passes.

**The defence** is the one applied here: pin the **whole** message, with the
computed value **hand-typed** rather than recomputed -- because an expected value
computed by the code under test cannot fail, which is the second shape and would
have reintroduced the hole in the fix.

**It generalises past error messages** to anything the product tells a human and
nothing else consumes: the ``R-PLAT-03`` loader diagnostic that names a required
and an available version, ``R-PERC-10``'s explanation of why an older Percolator
was chosen, ``R-PERC-11``'s advisories, and every provenance field a reviewer
reads a year later. Units 6, 7 and 9 own three of those, and their briefs say so.

.. _p05-project-findings:

Two findings tier 1 has taken as project-level
===============================================

Recorded here in the form later phases inherit them.

**A redundant "correct" final value masks every wrong value before it.**
``HttpDownloader`` ended every transfer with ``listener.onProgress(size,
declaredTotal)``, duplicating what the loop's last iteration had already
reported. It looked harmless. It was the reason my resumed-progress injection
survived: with every mid-transfer report wrong, the final report still landed on
the right number, so ``lastByteCount()`` stayed correct and the endpoint
assertions passed. **Removing it made the last report the loop's own work**, and
my re-injection then failed with four assertions where it had failed with three.

This is not a downloader detail. It applies to anything that reports progress
toward a known total, which puts **Phase 08's stage progress and Phase 13's
provenance viewer** directly in its path: a final "100%" that is written rather
than reached will hide every wrong percentage before it.

**A fixture contains what the rule needs; real data contains what the world
has.** The one-row-per-download defect was found by asserting an ordering rule
against the **shipped** ``manifests/tools.json`` instead of against a fixture --
and nobody injected it. The fixture had been built to exercise ordering, so it
contained exactly two comparable rows and nothing awkward. The real manifest
contains a 99 MB file carried on five platforms because the specification
requires an operating system and an architecture in every record, and on Apple
silicon two of those five rows are both runnable.

The rule belongs on the fixture, where it can be stated cleanly. **The
product's own instance of the rule belongs on the real data**, because that is
where the awkward cases live. Doing only the first is the third shape -- a
property proved through a seam production need not use.

.. _p05-lock-ruling:

The lock: tier 1's ruling
==========================

The escalation in :ref:`p05-stale-lock` was answered on 2026-09-02, and the
finding was worse than the file. ``_build/cometgui-maven.lock`` is gone,
surviving only in ``_build/archive-before-p05-unit2/``, and nothing under
``scripts/`` or ``.github/`` ever referenced it -- but ``STATUS.rst`` line 1032
told readers *"a flock around Maven invocations is the cheap fix and Phase 04
adopted one"*, while ``build.sh`` line 217 runs ``mvn clean verify`` at the
repository root with **no lock at all**. Phase 04 adopted one in its agents' own
command lines, which vanished when those agents did.

**A protection that is documented, believed and absent** -- the signature defect
relocated into the authoritative record. Tier 1's ruling, both parts its own to
execute: the record is corrected **now**, because the false sentence is the
active harm; ``build.sh`` takes a real ``flock`` **at Phase 05 sign-off, not
during it**, with a control proving it actually serialises, because changing the
build under a live phase is the precise hazard being discussed.

Until then the operative protections are the serial rule and the pair of
practices these four collisions produced: **do not write anything the build
reads, and do not assume an agent has finished until it says so.**

Work units
==========

Run **serially**. No positive argument exists that any two of these cannot
collide -- they share ``cometgui-domain``, the Maven working tree,
``_build/m2repo``, the docs gate and the git index -- so none is offered.

.. list-table::
   :header-rows: 1
   :widths: 4 30 20 46

   * - #
     - Unit and acceptance conditions
     - Rules and gate items served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Domain tool vocabulary.** ``org.cometgui.domain.tools``: tool
       identity, comparable tool version, host platform (OS + architecture,
       detected through a port over system properties), artefact kind
       enumeration, capability, capability-evidence, install state, probe
       stage and probe-failure kind, loader-failure diagnostic, install
       progress, and the ``ToolManager`` port the UI is allowed to
       see (this row originally said ``ToolRegistry``/``ToolInstaller``; the
       unit brief said ``ToolManager``, the agent followed the brief, and the
       row is corrected here rather than in the code). *Accepts when:* version ordering is proved by a table
       including ``3.06.5 < 3.07.1 < 3.09`` and ``2026.02.2``; platform
       detection is proved from pinned ``os.name``/``os.arch`` triples for all
       five tier-1 pairs plus an unknown pair that must be rejected, not
       guessed; every enum constant is exercised; ``cometgui-domain`` still
       meets 90/85 and its mutation gate.
     - ``R-TOOL-01``, ``R-TOOL-03``, ``R-TOOL-06``, ``R-TOOL-08``,
       ``R-PLAT-02``, ``R-PLAT-03``, ``R-PERC-01``, ``R-PERC-11``
     - **ACCEPTED 2026-09-02** after one rework -- see :ref:`p05-u1-signoff`.
       ``ea686d6`` reproduced exactly when I re-ran it, but an injection of my
       own **survived 108 tests**; ``42033ad`` widened ten test classes and both
       that injection and a second, unannounced one now fail. Build 11/11 in
       910s, domain 100.0% line and branch, 49/49 in the census, 368/369
       mutations. Ten test classes changed, no production source.

   * - 2
     - **The artefact manifest and its reader.** ``manifests/tools.json``
       populated from unit 0's table, plus
       ``org.cometgui.install.registry.*``: record model with every field
       ``R-TOOL-01``/``R-TOOL-03`` names, strict reader over
       ``JsonReader``, and selection by tool, version and host platform.
       Flips ``cometgui-install``'s mutation switch. *Accepts when:* every
       record carries tool, version, release tag, OS, architecture, URL,
       kind, expected member, expected installed path, SHA-256, MD5, licence,
       companions, capabilities with evidence, advisories,
       ``minimumHostRequirements`` and minimum CometGUI version; a record
       missing any of them is **rejected with a message naming the field**;
       selection returns empty for 3.09 on Linux and a test asserts that;
       a test rejects the words verified/confirmed/proven/tested applied to a
       platform with ``unverified-*`` evidence; a hand-typed expected record
       is compared field by field, not by size.
     - ``R-TOOL-01``, ``R-TOOL-02``, ``R-TOOL-03``, ``R-SEC-02``,
       ``R-PERC-11``, ``R-PERC-12``, ``D-008``, gate 8
     - **ACCEPTED 2026-09-02** after one rework -- see :ref:`p05-u2-signoff`.
       All 54 checksums re-derived by me from the bytes, 0 mismatches; the
       honesty properties hold; three injections of mine all bit, one of them
       in three places where it bit in one before the rework. Build 11/11 in
       984s, cometgui-install 100.0% line and branch, 8/8 in the census,
       189/189 mutations. The rework found a real defect: selection offered
       one 99 MB download twice, the second marked as running under Rosetta 2.

   * - 3
     - **Downloader.** ``org.cometgui.install.download``: an
       ``org.cometgui.domain.ports.Downloader`` over ``java.net.http`` --
       HTTPS only, cross-host redirect following, progress, cancellation,
       resume-or-clean-restart, timeouts, download to a temporary file, and an
       upstream-availability failure that names the URL and the expected
       checksum. *Accepts when:* against a loopback server, progress is
       monotone and ends at the declared total; cancellation mid-stream leaves
       **no** destination file and reports cancelled rather than failed; a
       resumed transfer produces bytes identical to an unresumed one and a
       test proves the ``Range`` request was actually made; a server that
       rejects ``Range`` causes a clean restart, proved by the byte count
       actually transferred; ``http://`` is refused; 404 is reported as
       availability with the URL; the 946 KB real Percolator artefact is
       fetched from its real URL and its SHA-256 matches unit 0's.
     - ``R-SEC-02``, ``R-TOOL-04``, ``D-008``, gate 1
     - **ACCEPTED 2026-09-02** after three rounds -- see :ref:`p05-u3-signoff`.
       The real 946 KB Percolator artefact is fetched from its real URL through
       product code and verified against the pinned SHA-256 in 5.2s. Build
       11/11 in 1039s, 384/387 mutations = 99.2% over a gate I widened, 27/27
       in the census. My injection survived round one and now fails with four
       assertions where it once failed with none.

   * - 4
     - **Extraction, once, for every kind (``R-SEC-05``).**
       ``org.cometgui.install.archive``: one class covering
       ``BARE_EXECUTABLE``, ``ZIP``, ``TAR_GZ``, ``JAR``, ``DEB_PAYLOAD``
       (``ar`` + ``data.tar.*``) and ``PKG_PAYLOAD`` (``xar!`` + gzip +
       ``070707`` cpio), with traversal, absolute-path, unsafe-symlink and
       decompression-bomb checks applied uniformly. *Accepts when:* each of
       the four attacks has its **own** test **per multi-entry kind**, each
       asserting the specific rejection reason and that nothing was written
       outside the destination; the real 3.06.5 macOS zip with its ``../``
       member is a test case; the real 3.07.1 Linux zip yields a binary whose
       SHA-256 equals unit 0's; the real ``.deb`` and ``.pkg`` each yield both
       XSDs at ``21204c89...``/``fa50a550...``; a truncated archive is
       rejected; the bomb guard is proved to bite on ratio, on absolute size
       and on entry count separately.
     - ``R-SEC-05``, ``R-TOOL-01``, ``R-TOOL-02``, gate 3
     - **ACCEPTED 2026-09-02** after two rounds -- see :ref:`p05-u4-signoff`.
       Gate item 3 has a real upstream artefact behind it. Gating the package
       found five XXE guards that could each be deleted with the suite green,
       and three live product defects including a copy loop that spun.

   * - 5
     - **Atomic install, marker, recovery, lock, fix-ups.** The eight-step
       pipeline from ``specification.rst``; tool cache layout; a completion
       marker written last carrying the recorded checksums; discard-or-resume
       of an interrupted install; a cross-process lock; executable bits; the
       macOS quarantine step. *Accepts when:* an install interrupted after
       each of the eight steps in turn never reports itself installed, proved
       by a test that enumerates the steps rather than sampling one; a
       marker whose recorded checksum no longer matches the file makes the
       entry not-installed; two **separate JVMs** installing the same artefact
       serialise and neither observes a partial entry; the installed binary is
       executable and actually runs.
     - ``R-TOOL-04``, ``R-TOOL-05``, ``R-PLAT-04``, ``R-PLAT-05``, gates 2, 4
     - **ACCEPTED 2026-09-02**, no rework -- see :ref:`p05-u5-signoff`. My
       injection bit. Build 11/11 in 1300s, 62/62 in the census, 1087/1104
       mutations over 14 gated prefixes.

   * - 6
     - **Loadability and identity probes, and the ``R-PLAT-03``
       diagnostic.** ``org.cometgui.install.probe``: stage 1 loadability with
       a classifier for missing shared object, missing symbol version, wrong
       architecture, macOS quarantine and missing Windows DLL; stage 2
       identity by parsing the version banner; the advance check of
       ``minimumHostRequirements`` against the host through the existing
       ``GlibcVersion``/``HostBaselineVerifier``. *Accepts when:* the
       diagnostic names the required version, the host's version and the
       available alternatives; the classifier is tested against the **verbatim
       strings recorded in** :ref:`p05-central-risk`; the 3.09 ``.deb``
       payload is **executed** here and produces the loader diagnostic, not a
       capability verdict; a tool failing loadability is not offered for
       selection, asserted on the offered set.
     - ``R-PLAT-02``, ``R-PLAT-03``, ``R-TOOL-06``, gate 5
     -

   * - 7
     - **Tool adapters and the functional capability probe.**
       ``org.cometgui.tools.{api,comet,percolator,pdv,limelight}``: the
       synthetic PIN generator (64 target + 64 decoy, deterministic without
       ``java.util.Random`` defaults), the ``XML_OUTPUT`` and
       ``XML_DECOY_OUTPUT`` probes, Comet's capability set and its
       ``THERMO_RAW_WINDOWS`` companion rule, JAR identity for PDV and the
       converter. Flips ``cometgui-tools``'s mutation switch. *Accepts when:*
       the probe returns ``XML_OUTPUT`` true for the 3.07.1 portable binary --
       the exact binary ``scripts/feasibility/probe_xml_capability.py`` gets
       wrong -- with 64 ``<psm>`` and the namespace asserted; a **negative
       control** shows an 8+8 fixture producing the recorded false negative,
       which is why 64+64 is the size; a zero-byte output file is not accepted
       as success; a Comet install without the three Thermo DLLs does not
       advertise ``THERMO_RAW_WINDOWS`` and one with them does.
     - ``R-PERC-02``, ``R-TOOL-02``, ``R-TOOL-06``, ``R-TOOL-07``, gate 6
     -

   * - 8
     - **Local binary registration.** Probe an arbitrary path, require
       Percolator >= 3.05, compute and record checksums, probe capabilities
       conservatively, register as unmanaged. *Accepts when:* a binary
       reporting 3.04 is rejected with a message naming the version found and
       the minimum required; the real 3.07.1 binary is registered with the
       SHA-256 from unit 0 and its probed capability set; an unreadable or
       non-Percolator file is rejected distinctly from a too-old one; absent
       positive evidence a capability is absent (``R-TOOL-08``).
     - ``R-TOOL-08``, ``R-PERC-01``, gate 7
     -

   * - 9
     - **Tool Manager UI section and wiring.** A view-model and view under
       ``SectionId.TOOL_MANAGER``, using only ``org.cometgui.domain.tools``;
       wiring in ``ApplicationServices``. *Accepts when:* installed,
       available, unavailable-on-this-platform and local tools each render
       with capabilities and advisories; the offered set is exactly the
       manifest's selection for the host and a test drives that from the
       manifest, not from a literal; progress and cancellation are exposed;
       Phase 02's stable-identifier pinning still passes; no
       ``java.net``/``java.security``/``java.util.zip`` reference enters
       ``org.cometgui.ui``.
     - ``R-PERC-01``, ``R-PERC-03``, ``R-PERC-11``, gates 5, 8
     -

   * - 10
     - **The end-to-end install, driven through the UI.** From an empty tool
       cache, install Comet, an XML-capable Percolator, PDV and the converter
       and probe each, driven through the Tool Manager rather than a test
       helper; plus the deliberate cancellation and restart of the 99 MB PDV
       download; plus the opt-in real-upstream variant. *Accepts when:* the
       four tools reach ``INSTALLED`` with probed capability sets; a corrupted
       artefact is rejected and a recording ``ProcessRunner`` proves **no
       process was launched**; the PDV download is cancelled mid-transfer and
       restarted to completion, with the byte counts of both attempts
       recorded.
     - gates 1, 2
     -

   * - 11
     - **Documentation.** ``docs/developer/tool_registry.rst`` (design, the
       artefact provenance table from unit 0, the stated limits),
       ``docs/tool_manager.rst``, and the per-platform artefact table in
       ``docs/platform_support.rst``. **There is no ``docs/user/``
       directory**: the brief names ``docs/user/tool_manager.rst`` but the
       tree has ``docs/tool_manager.rst``, and creating a second page would
       be the drift this project keeps paying for. ``R-DOC-06``'s final
       wording on ``platform_support`` stays **Phase 16's**, as that page's
       own note records; this phase supplies the table it is built on.
       *Accepts when:* the platform matrix is generated from
       ``manifests/tools.json`` rather than typed, so it cannot diverge;
       ``bash scripts/ci/docs-build.sh`` is green.
     - ``R-DOC-06``, ``R-PERC-12``
     -

   * - 12
     - **``scripts/verify-install-gates.sh``**, assembled from the injections
       recorded in this log rather than invented, and registered as a control
       in ``scripts/verify-all-gates.sh``. *Accepts when:* every control is
       seen to bite; a control whose defect did not land is reported as a
       **harness failure**, not a pass; the aggregate suite grades at least as
       many controls as before; the added cost is measured and stated.
     - every gate item
     -

.. _p05-injection-protocol:

The injection protocol, as this phase now runs it
==================================================

Sharpened by tier 1 on 2026-09-02, after unit 3's agent reported a figure it
could not explain instead of letting it pass. Recorded here because every
remaining unit of this phase is held to it.

**What happened.** The same baseline class ``904baa02...``, the same one-line
defect, and two different injected class hashes: ``210d3e9a...`` from me and
``a4a570b1...`` from the agent. The agent guessed a different compile
invocation and said so rather than papering over it.

**The measured cause**, which tier 1 established by compiling two classes
differing by nothing but a comment line: ``javac`` records a
``LineNumberTable`` in its debug info, so **a comment moves every line below it
and therefore moves the class hash**. Compiled with ``-g:none`` the two classes
are byte-identical, which proves the difference is line-number metadata rather
than behaviour. ``-Djacoco.skip=true`` is a red herring -- JaCoCo instruments at
run time through a javaagent and never rewrites ``target/classes``.

**The three rules that follow:**

#. **"The compiled class hash moved" stays mandatory.** It is what proves an
   edit reached the bytecode, and it is the only defence against the eighth
   shape -- an injection that reached the source but not the compiled class.
   Nothing about that changes.
#. **Two parties' injected class hashes differing is NOT evidence that they
   injected different defects.** Line numbers alone move it, and chasing such a
   mismatch is chasing formatting.
#. **To compare two injections, compare the injected SOURCE hashes** -- or, if
   all that matters is that the same property was falsified, **compare the
   assertion text that failed.**

**And the behaviour worth keeping.** The agent reported a number it could not
explain rather than quietly dropping it. That is the behaviour this structure
depends on: a phase in which agents suppress inconvenient figures is the one
failure mode none of these gates can catch, because every gate reads what it is
given.

.. _p05-u1-signoff:

Unit 1 sign-off
===============

**What I ran myself, on the committed tree at ``ea686d6``, with nothing else
building.** Not read from the agent's report; every figure below is from my own
run.

``bash scripts/build.sh`` -> ``11/11 stages OK in 968 seconds``, ``BUILD OK``,
``127 report file(s): tests=2182 failures=0 errors=0 skipped=2``. The four
lines that matter::

    ok       cometgui-domain   line 100.0% (832/832)  branch 100.0% (350/350)
    ok       cometgui-domain   49 compiled class(es), all 49 in the sample
    ok       cometgui-domain   368/369 mutations killed = 99.7%
    ok       8 architecture rule(s) checked, 0 failures

The census figure is the one worth reading twice: ``cometgui-domain`` held 25
compiled classes before this unit and holds 49 now, and all 49 are in the
coverage sample. The two skips are Phase 04's and pre-date this unit.

I read the diff rather than the description of it: 46 files, 6847 insertions,
every one under ``cometgui-domain/src/main/java/org/cometgui/domain/tools/`` or
its test twin. Nothing outside the unit's paths. No gate, threshold, exclusion
or rule was touched.

Two tests I checked specifically for the shapes this project keeps finding, and
both are sound. ``ToolVersionTest.Ordering`` sorts a jumbled list with the
method under test and compares it to a **hand-typed** ascending literal, so the
expected value is not computed by the code under test.
``ProbeFailureKindTest`` pins every kind's stage in a hand-typed
``@CsvSource`` table; its last test does compare two methods of the class under
test to each other, which alone could not fail, but it sits on top of the
pinned table and is a consistency check rather than the only check.

.. _p05-u1-injection:

The defect I injected, which the unit did not catch
---------------------------------------------------

In ``DeclaredCapability``'s compact constructor I replaced the single anchor
``if (note.isBlank()) {`` with ``if (note.isBlank() && evidence !=
CapabilityEvidence.UNVERIFIED) {`` -- the plausible-sounding "an unverified
capability has no provenance to state, so let its note be empty".

The injection **landed**, checked rather than assumed: the anchor occurred
exactly once; the source hash went ``b8586159...`` to ``39fd3ac8...``; the
**compiled class** went ``8835fcb3...`` to ``bd619d13...``; and ``javap -c``
showed ``UNVERIFIED`` in the constructor's bytecode.

``mvn -pl cometgui-domain -Dtest='DeclaredCapabilityTest,ToolOfferTest,
ToolCapabilityTest,CapabilityEvidenceTest' test`` then exited **0**. The
surefire reports prove those four classes really ran -- all stamped 07:01:44,
while ``ToolVersionTest`` still carried 06:40:14 from the earlier build, so the
selection worked and nothing was silently skipped::

    DeclaredCapabilityTest  tests="10" failures="0"
    ToolOfferTest           tests="17" failures="0"
    ToolCapabilityTest      tests="66" failures="0"
    CapabilityEvidenceTest  tests="15" failures="0"

**108 tests passed with the blank-note rejection switched off.**

The cause is the project's fifth shape, an input set too narrow to see the
defect. ``aBlankNoteIsRejected`` is a ``@ValueSource`` over five blank strings
and every one is built with ``OBSERVED_BY_EXECUTION``: the blank axis is
covered thoroughly and the **evidence axis is not covered at all**.

It matters more than a thin test usually would. ``UNVERIFIED`` is the evidence
value every Windows and every macOS capability row in this phase's manifest
carries, because no Windows or macOS binary has ever been executed anywhere in
this project, and the note is the only field recording *why* such a row is
unverified. With the injection in place the manifest could carry ``XML_OUTPUT``
/ ``UNVERIFIED`` / an empty note -- an unverified claim with no provenance at
all, which is exactly the failure ``phases/PHASE-05-tool-registry.rst`` names
as this phase's second most likely.

Restored with ``git checkout --``; the source hash returned to ``b8586159...``,
the marker greps back out at zero occurrences, and ``git status --porcelain``
is empty.

Unit 1 accepted, after one rework
---------------------------------

Rework committed as ``42033ad``, on top of my sign-off record and leaving
``ea686d6`` untouched. **Ten test classes widened; no production source
changed** -- the rule was right and the tests were thin, which is the correct
shape of this repair.

**What I ran myself**, on ``42033ad`` with nothing else building.

*The same injection, again.* Anchor still unique. Source ``b8586159...`` to
``31b47c54...`` (my wording differs from the agent's, so the hash does too);
**compiled class** ``8835fcb3...`` to ``1b4c7018...``; ``UNVERIFIED`` present in
the constructor bytecode. The four-class command that previously exited 0 with
108 passes now exits **1** with ``Tests run: 171, Failures: 18``, and
``DeclaredCapabilityTest`` goes from 10 tests to 25 with 18 failing. It fails
for the right reason, not by coincidence::

    DeclaredCapabilityTest.theEvidenceAxisIsCoveredWhole:188 every evidence
    value must reject a blank note; a value missing here is a capability that
    can be claimed with no provenance ==> expected: <[OBSERVED_BY_EXECUTION,
    INFERRED_FROM_ARTEFACT_BYTES, UNVERIFIED]> but was:
    <[OBSERVED_BY_EXECUTION, INFERRED_FROM_ARTEFACT_BYTES]>

*A second, different injection, to test whether the audit was real or
cosmetic.* Repairing only the defect that was found is the cheap response, so I
injected into a different widened class without warning: ``InstallProgress``
accepting a negative ``bytesTransferred`` when the phase is ``FAILED`` or
``CANCELLED`` -- "a failed transfer has no meaningful byte count". Compiled
class ``1cc980d1...`` to ``ea70743e...``. ``InstallProgressTest`` exits **1**,
``Tests run: 40, Failures: 2``, from
``aNegativeByteCountIsRejectedInEveryPhase`` -- the phase-axis test the audit
added. **The audit was real.**

Both injections reverted with ``git checkout --``; both source hashes back to
baseline, both markers grep back out at zero, ``git status --porcelain`` empty.

*The build, my own run,* 07:35:25 to 07:50:35::

    11/11 stages OK in 910 seconds.  BUILD OK
    127 report file(s): tests=2334 failures=0 errors=0 skipped=2
    ok  cometgui-domain   line 100.0% (832/832)  branch 100.0% (350/350)
    ok  cometgui-domain   49 compiled class(es), all 49 in the sample
    ok  cometgui-domain   368/369 mutations killed = 99.7%
    ok  cometgui-domain   154 class(es) analysed, 0 findings
    ok  8 architecture rule(s) checked, 0 failures

Reactor tests 2182 to 2334; ``cometgui-domain`` 788 to 940. Coverage and the
census are unchanged, which is what a test-only change should do.

.. _p05-tenth-shape:

A tenth shape: an added conjunct is invisible to coverage *and* to mutation
---------------------------------------------------------------------------

The phase brief says "nine shapes of a check that cannot fail -- expect a
tenth". This is the tenth, and it was the agent that named it.

The hole in :ref:`p05-u1-injection` sat under **100% line coverage, 100% branch
coverage and a 99.7% mutation score**, and none of the three could have found
it. Coverage cannot: every line and every branch of the original rule was
executed. Mutation cannot either, and this is the part worth keeping: **PIT
mutates the expression that is there; it never adds a conjunct.** There is no
mutation operator that turns ``note.isBlank()`` into ``note.isBlank() &&
evidence != UNVERIFIED``, because that is not a mutation of the expression, it
is a different expression. The mutation score was identical before and after
the repair -- 368/369 both times -- which is the evidence that the gate was
blind rather than merely quiet.

So a rule can be fully covered, fully mutation-tested, and still be switched
off for one value of a parameter it was never varied over. The defence is not a
stronger automated gate; it is **grading every rejection over the axes the rule
does not depend on**, which is what the rework did, plus a human injection of
the plausible-sounding extra condition. Unit 12 carries this as a control, and
it is the reason a hand-written injection is worth doing even on a module that
reports perfect numbers.

Carried forward from unit 1
---------------------------

* **``ProbeFailureKind.stage()`` is single-valued**, and ``TIMED_OUT`` and
  ``EXECUTION_FAILED`` fall to ``LOADABILITY`` under the agent's rule: an
  ambiguous kind takes the earliest stage, because the safe direction is "we
  did not establish that it starts" and never "we established that it cannot do
  this". ``CAPABILITY_ABSENT`` is consequently the only kind whose stage is
  ``CAPABILITY``, which is what makes this phase's named trap checkable.
  **Units 6 and 7 are bound by this**; if either needs a stage-varying kind,
  ``stage()`` moves onto the occurrence record rather than becoming a guess.
* **``MinimumHostRequirements`` has no satisfaction rule and its test does not
  give it one.** The assertions near lines 60 to 67 of
  ``MinimumHostRequirementsTest`` exercise ``GlibcVersion.isAtLeast``, which is
  Phase 02's class. **Unit 6 owns "is this host satisfied", and must test the
  exact-equality boundary**: a host with precisely ``GLIBC_2.34`` must be
  offered Percolator 3.07.1, not refused it.
* **The wire-format ids unit 2 must match**, pinned by hand-typed tests in unit
  1 rather than derived from ``name()``: tools ``comet``, ``percolator``,
  ``pdv``, ``limelight-converter``; platforms ``linux-x86-64``,
  ``macos-aarch64``, ``windows-x86-64`` and so on; artefact kinds and
  capabilities as the uppercase specification tokens; evidence
  ``observed-by-execution``, ``inferred-from-artefact-bytes``, ``unverified``.
* **``ToolVersion`` requires two to four numeric components**, so a bare ``3``
  is rejected and ``3.05`` equals ``3.5``. That is what makes ``R-TOOL-08``'s
  minimum-3.05 floor a numeric comparison rather than a string test.

.. _p05-u2-signoff:

Unit 2 sign-off
===============

**What I ran myself, on ``fd24fa7``.** Every figure below is from my own run.

Fifty-four independent checksum checks, nothing disagreed
----------------------------------------------------------

I re-derived every checksum in ``manifests/tools.json`` from the bytes in
``scratch/phase05/artefacts/``: **39** artefact, zip-member and
companion-archive checks and **15** companion-member checks -- the XSD pairs
extracted from all four payloads, both ``.deb`` and both ``.pkg``, and the three
Comet Windows DLLs -- with **0 mismatches**.

The first version of my checker reported four mismatches, and they were **my
bug, not the manifest's**: I keyed the local files by basename, and 3.06.5 and
3.07.1 publish their macOS and Windows portable zips under the *same* file
names, so the dictionary collided and compared the 3.06.5 rows against the
3.07.1 files. Re-keyed by release tag, everything matched. Recorded because it
is the same lesson in the other direction: **a red result can be the harness's
fault as easily as a green one can be a lie**, and "exit code 0 proves nothing"
has a mirror image.

The honesty properties hold
---------------------------

* ``observed-by-execution`` appears on **linux-x86-64 only** -- seven claims --
  and nowhere else. Every other row is ``inferred-from-artefact-bytes`` or
  ``unverified``. Zero dishonest claims.
* **No 3.09 row claims any XML capability**, and there is **no Percolator 3.09
  Linux row at all**, which is the honest absence ``D-003`` and ``R-PERC-12``
  require.
* Every evidence note names who ran it and what was observed. I checked one
  against its source rather than accepting it: the 3.06.5 note attributes the
  64-plus-64 fixture to Phase 00's sweep, and
  ``scripts/feasibility/noxml_sweep.py`` line 485 does set ``n_target = 64`` on
  the ``--probe`` path. The note is accurate, and it credits Phase 00 rather
  than implying unit 0 did that execution.
* Advisories are present and mandated by ``R-PERC-11``, not decorative: 3.07.1
  carries both the I-splines and the PEP-above-one items, 3.06.5 carries
  two-lines-old and the peptide-protein-id behaviour, 3.09 carries that it
  writes no pout XML, macOS carries Rosetta 2, Windows carries the Visual C++
  runtime, and PDV carries the upstream licence contradiction.
* Comet's ``linux-x86-64`` row claims only the three capabilities Phase 00
  established **by execution** and not the further options its usage text
  advertises. That conservatism is ``R-TOOL-08`` applied correctly, and it is
  the opposite of the textual-probe error this phase exists to avoid.

The defect I injected
---------------------

I inverted the second sort key of ``ArtefactManifest.OFFER_ORDER`` so that a
translated artefact sorts before a native one. Compiled class ``21ef9ea9...``
to ``88de51f5...``. It **bit**: ``ArtefactManifestTest.selectionIsOrdered``
failed against a hand-typed expected list, on a fixture that deliberately gives
one version both an arm64 and an x86-64 macOS row so the key is observable at
all. Reverted; marker greps back out at zero; tree clean.

The build, my own run
---------------------

::

    11/11 stages OK in 992 seconds.  BUILD OK
    136 report file(s): tests=2483 failures=0 errors=0 skipped=2
    ok  cometgui-install  line 100.0% (560/560)  branch 100.0% (168/168)
    ok  cometgui-install  8 compiled class(es), all 8 in the sample
    ok  cometgui-install  187/187 mutations killed = 100.0%
    ok  cometgui-install  30 class(es) analysed, 0 findings
    ok  every module with critical-package code has its mutation gate on

``cometgui-install`` printed ``inert -- no classes with code yet`` before this
unit and now carries a real, whole population.

Sent back for two small additions
---------------------------------

#. **The ordering rule is tested; the product's own instance of it is not.**
   ``ShippedManifestTest`` covers Apple silicon for Percolator, where no version
   publishes both a native and a translated macOS build, so the
   native-before-translated key is never observable there. **Comet** is the tool
   that does have both on ``macos-aarch64``, and ``D-004`` says in as many words
   that Comet runs natively while only the Percolator stage is translated. The
   manifest satisfies that today and nothing asserts it against the shipped
   data -- the third shape, a property proved through a seam production need not
   use, in its mildest form.
#. **A Javadoc promising an ordering key the code deliberately refuses to
   have.** ``select``'s Javadoc says "then by platform identifier"; the comment
   above ``OFFER_ORDER`` explains at length, and correctly, that a third key
   would be a comparison no input can reach. A reader who trusts the Javadoc
   will "fix" the comparator to match it, which puts the unreachable branch
   back.

Also asked: the agent reported ``tests=2482`` and ``(559/559)`` where my run of
the same commit reports ``tests=2483`` and ``(560/560)``. One test and one line,
in the safe direction, but an unexplained delta between a reported figure and
the committed tree is the "evidence read without being dated" shape and is worth
one sentence rather than a shrug.

Unit 2 accepted, after one rework
----------------------------------

Reworked in ``eac6d5e``, on top of the agent's own ``c4bb680``. **The manifest
itself is byte-identical to what I checked** -- ``git diff fd24fa7 eac6d5e --
manifests/tools.json`` is empty -- so the 54 checksum verifications above stand
for the accepted tree, and ``manifests/tools.json`` and the copy inside the jar
are both sha256
``3f6707d3b13686750914a5cfbb20bab11ae29acdd079577028cbe12ef0aaffcf``: one file,
shipped once, confirmed by hashing both.

**My build, on the accepted tree**::

    11/11 stages OK in 984 seconds.  BUILD OK
    136 report file(s): tests=2485 failures=0 errors=0 skipped=2
    ok  cometgui-install  line 100.0% (567/567)  branch 100.0% (172/172)
    ok  cometgui-install  8 compiled class(es), all 8 in the sample
    ok  cometgui-install  189/189 mutations killed = 100.0%
    ok  cometgui-install  30 class(es) analysed, 0 findings
    ok  every module with critical-package code has its mutation gate on

Three injections, all of them mine
-----------------------------------

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Defect injected
     - What went red

   * - **A.** Invert the second sort key, so translated sorts before native.
     - **Three** failures: the fixture ordering test, ``ShippedManifest
       Test.cometIsOfferedNativelyOnAppleSilicon`` and
       ``aPlatformIndependentDownloadIsOfferedOnce``. Before the rework this
       same injection failed **one** test, which is the measurable difference
       the rework made.

   * - **B.** Key the one-row-per-download rule on the **version** instead of
       the download URL -- the mistake the agent said it nearly made.
     - **Two** failures: the fixture ordering test and the Comet real-data
       test. The agent predicted exactly one. It is in the safe direction, but
       recorded because **a prediction about one's own harness is a claim to
       check, not to believe.**

   * - **C.** Keep the **last** row for each download instead of the first, so
       the translated row survives where a native one exists. Mine, chosen
       before reading the agent's suggestion.
     - ``aPlatformIndependentDownloadIsOfferedOnce``, with the product
       consequence in the message: *"pdv is a JAR, and a row marked
       TRANSLATED_ROSETTA_2 would tell a scientist that a Java program runs
       under Rosetta 2 ==> expected: <NATIVE> but was: <TRANSLATED_ROSETTA_2>"*.
       So the test checks **which** row survives, not merely how many.

All three reverted; markers grep back out at zero; ``git status --porcelain``
empty.

Two real defects the agent found in its own work
-------------------------------------------------

Both are worth the phase's memory, and the second is the more interesting.

#. **The duplicate check was keyed on the version's spelling.** It used
   ``ToolVersion.text()``, so a manifest carrying both ``3.09`` and ``3.09.0``
   for one tool and platform was accepted, and ``select(host, tool,
   parse("3.09"))`` returned **both** rows -- because ``ToolVersion.equals``
   compares numerically and calls them one version. The check that exists to
   stop one release being offered twice would have missed exactly the case it
   was for. Found by re-reading, not by a failing test.

#. **Selection offered the same download twice, and this one was surfaced by
   the review item I asked for.** PDV's zip and the converter's JAR are one
   file each, carried on all five platforms because the specification requires
   an operating system and an architecture in every record. On
   ``macos-aarch64`` both the ``macos-aarch64`` row and the ``macos-x86-64``
   row are runnable, so the Tool Manager would have shown one **99 MB**
   download twice and marked the second one as running under Rosetta 2 -- *a
   false statement about a Java program*, not merely a repeated row.

   The fix keys on the **URL**, not the version, and that distinction is the
   whole point: Comet's two macOS builds are two different files with two
   different digests, verified so in my own checksum run, and they remain two
   genuine offers with the native one first; two rows naming one file were
   never a choice.

   **The lesson is the one the review was testing.** I asked for the ordering
   rule to be asserted against the shipped manifest rather than only against a
   fixture, because the rule was proved through a seam production need not use
   -- the third of the project's nine shapes. Writing that assertion is what
   made the duplicate visible. A fixture built to exercise a rule contains
   exactly what the rule needs and nothing awkward; the real data contains a
   99 MB file carried on five platforms.

Carried forward from unit 2
----------------------------

* **``MinimumHostRequirements`` has no field for a GLIBCXX floor, and unit 6
  needs one.** 3.07.1 requires ``GLIBCXX_3.4.29`` and the 3.09 payload requires
  ``GLIBCXX_3.4.32`` -- and in :ref:`the loader failure I executed
  <p05-central-risk>` the **GLIBCXX** line is reported *before* the ``GLIBC``
  one, so an advance check that knows only about glibc would predict "runnable"
  for a binary that fails on the C++ runtime. **Decision: unit 6 adds
  ``minimumGlibcxx`` to ``MinimumHostRequirements`` and to the manifest**, in
  the unit that first reads it, rather than landing a field nothing uses.
* **Payload entries carry a ``./`` prefix** in the ``.deb`` tar and the ``.pkg``
  cpio that the manifest's member paths omit. **Unit 4 must normalise**, and
  must not do it by trimming whatever the archive happens to start with.
* ``bcrypt.dll`` is deliberately **omitted** from the 3.09 Windows row's
  required libraries: it is a system DLL, not a redistributable.
* Companions carry ``id``, ``runtimePrerequisite``, ``gatesCapability``,
  ``note`` and a uniform ``members`` list; ``memberMd5`` and companion-member
  ``md5`` exist so every digest pair goes through the existing ``FileHashes``.
* The reader loads ``/tools.json`` **from the classpath**, never from a
  relative path that would only work with the repository root as the working
  directory.
* **A platform-independent artefact is still five rows in ``artefacts()``.**
  ``select`` now hides the duplication, but the raw list does not, because the
  specification requires an operating system and an architecture in **every**
  record and PDV's zip and the converter's JAR are one file each. **Units 9 and
  11 will see five rows where a user should see one tool**, and must go through
  ``select`` rather than ``artefacts()`` when they mean "what is on offer here".
  Removing the duplication at source would be a specification amendment, which
  is not this phase's to make.

.. _p05-stale-lock:

A lock file that nothing takes
-------------------------------

While diagnosing :ref:`my own collision <p05-my-own-collision>` I found
``_build/cometgui-maven.lock``, left by an earlier phase. **Nothing in
``scripts/`` references it** -- ``grep -rn "cometgui-maven.lock\|flock\|LOCK"
scripts/*.sh scripts/ci/*.sh`` returns nothing. It is a lock that is enforced by
no one, and a reader who saw it could reasonably conclude that builds in this
tree are serialised. That is the project's signature defect relocated from a
test into a piece of process safety: a mechanism that cannot do the thing its
existence implies.

``scripts/`` is tier 1's, so this is **escalated, not fixed**: either delete the
file, or make ``scripts/build.sh`` actually take it. The unit 2 agent had moved
it, with the rest of ``_build/`` except ``m2repo``, into
``_build/archive-before-p05-unit2/``; that move is not what let my collision
happen, because nothing was reading the file in the first place.

.. _p05-my-own-collision:

I committed the project's most-repeated mistake, and it is the fourth instance
-------------------------------------------------------------------------------

**My first sign-off build of ``fd24fa7`` failed**, in stage 4, with
``package org.cometgui.provenance.json does not exist`` across every import in
``ArtefactManifestReader``. The reactor summary showed ``CometGUI ::
Provenance ... SUCCESS`` three modules earlier, so a dependency that had just
built successfully was not on the next module's compile classpath.

It did not reproduce. ``-pl cometgui-install -am clean compile`` succeeded; a
full-lifecycle ``clean verify`` over the first six modules succeeded; a second
``bash scripts/build.sh`` on a quiet tree succeeded in 992 seconds. The module
POM is correct and the effective POM carries ``cometgui-provenance`` at compile
scope.

**The cause was me.** I started that build while the unit 2 agent was still live
in the tree. It had committed, and I treated the commit as the finish -- but a
commit is not a completion, and the agent was still running: a message sent
afterwards was accepted for delivery "at its next tool round", which is what
proved it. Two Maven processes sharing ``_build/m2repo`` and every module's
``target/`` is exactly the collision ``ONBOARDING.rst`` documents, and its
signature symptom is exactly what I saw -- **a transient failure naming the
victim's own code rather than the collision.**

``STATUS.rst`` records that every concurrency collision in this project has been
committed by the tier enforcing the rule against it, three times, all by tier 1.
This is the fourth and the first by tier 2. It cost one re-run and about
seventeen minutes.

**The rule this phase now follows: wait for the agent's completion
notification, not for its commit, before running Maven.** A commit is a
durability checkpoint; it says nothing about whether the agent has stopped.

.. _p05-u3-signoff:

Unit 3 sign-off
===============

Committed as ``d88e001`` and ``f38f44a``: ``org.cometgui.install.download``
(the ``Downloader`` port over ``java.net.http``, with the redirect, range,
cancellation and truncation logic) and ``org.cometgui.install.verify`` (the
``R-SEC-02`` decision, and the composition that implements the
restart-after-a-failed-resume rule).

The HTTPS rule's loopback carve-out: accepted, with the reasoning
------------------------------------------------------------------

The unit brief said "HTTPS only". What was built accepts https anywhere, plus
plain http to a **127.0.0.0/8 or ``::1`` literal only**. The agent reported the
deviation rather than burying it, which is the behaviour this structure needs.
**I accept it**, having checked all three supporting claims rather than taking
them:

* ``config/checkstyle/checkstyle.xml`` line 188 bans ``sun, com.sun,
  jdk.internal`` through ``IllegalImport``, and the rule set covers test
  sources, so ``com.sun.net.httpserver`` is genuinely unusable and the
  raw-socket loopback server was not a preference.
* The carve-out is **literal-only and anchored** -- ``LOOPBACK_LITERAL`` is
  applied with ``.matches()``, not ``.find()``, so ``127.0.0.1.example.com``
  cannot slip through -- and names are refused for the right reason: a name
  resolves at connect time, so a name-based check answers a different question
  from the connection that follows.
* It is unreachable from product data: ``ArtefactValues.REQUIRED_SCHEME`` is
  ``https`` for every manifest record and ``ShippedManifestTest.everyUrlIsHttps``
  asserts it against the shipped file.

And ``DownloadRequestTest.refusedUrls`` grades the narrowness itself, which is
what makes this a carve-out rather than a hole: ``localhost``, ``LOCALHOST``,
``localhost.localdomain``, the boundary literals ``126.255.255.255`` and
``128.0.0.1``, credentials over **both** schemes including over loopback,
no-host, other schemes and non-absolute URIs.

**Why the alternatives are worse.** A strict production rule plus a test-only
permissive seam would run every transfer test through a seam production does not
use -- the third shape, and the very defect unit 2 was sent back to fix. An
HTTPS loopback server needs either a new dependency or a committed private key,
and a committed private key is strictly worse. The property ``R-SEC-02``
protects is integrity against an intermediary, and a plain-http connection to a
loopback **literal** has no intermediary because it has no path off the machine.
Unit 11 records this in ``docs/developer/tool_registry.rst``.

The defect I injected, which the unit did not catch
----------------------------------------------------

In ``HttpDownloader`` I replaced the single anchor ``listener.onProgress(offset
+ transferred, declaredTotal);`` with ``listener.onProgress(transferred,
declaredTotal);`` -- **progress reported from the resume point rather than the
absolute position.**

It landed -- anchor unique, compiled class ``904baa02...`` to ``210d3e9a...`` --
and the module test run **exited 0**: ``HttpDownloaderTest`` ran **59 tests**
and the module ran ``Tests run: 338, Failures: 0, Errors: 0, Skipped: 1``, all
green with the defect in place.

On a resumed transfer the listener would report the offset, jump **backwards**
to a small number, then climb again, and every percentage computed against
``declaredTotal`` would be wrong for the resumed portion. That is the 99 MB PDV
download the phase document singles out for deliberate cancellation and restart
testing: a progress bar that runs backwards on the one transfer big enough to be
resumed.

**It is the twin of a hole the unit had already found and fixed in itself.**
``f38f44a`` exists because the added conjunct ``isCancelled() && declaredTotal
>= 0`` -- "only stop when we know how much is left", which would make every
chunked download uncancellable -- survived the unit's three original
cancellation tests, all of which used a server declaring a length. The unit then
graded cancellation over the no-declared-length and resumed axes. It did not do
the same for progress: the progress assertions are thorough on the
fresh-download axis and absent on the resumed one.

Unit 3 accepted, after three rounds
------------------------------------

**My build, on the accepted tree** (``12d871e``)::

    11/11 stages OK in 1039 seconds.  BUILD OK
    146 report file(s): tests=2682 failures=0 errors=0 skipped=3
    ok  cometgui-install  line 100.0% (1023/1023)  branch 99.1% (348/351)
    ok  cometgui-install  27 compiled class(es), all 27 in the sample
    ok  cometgui-install  384/387 mutations killed = 99.2%
    ok  cometgui-install  85 class(es) analysed, 0 findings
    12 critical package prefix(es) read from pom.xml
    ok  8 architecture rule(s) checked, 0 failures

**And the milestone, run by me**::

    mvn -o -pl cometgui-install -Dcometgui.install.upstream=true \
        -Dtest=UpstreamArtefactTest -Dsurefire.failIfNoSpecifiedTests=false test
    Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   --  5.2 s wall clock

That is the real 946303-byte Percolator artefact, fetched from its real GitHub
URL through the real redirect to the signed asset host, and verified against the
pinned SHA-256 -- **through product code, not through a spike.** The third skip
in the build is this test declining to run without its opt-in flag, and the
reason is printed rather than silent; an always-running companion pins the URL,
the size and both digests against the shipped ``manifests/tools.json``, so the
gate is not vacuous when the flag is absent.

My injection, re-run after the survivor work, now fails with **more** assertions
than before -- 4 and 3 where it was 3 and 2 -- which is the concrete
confirmation of the ``copy:503`` decision below.

Where I was wrong, and the agent corrected me
----------------------------------------------

Recorded because a sign-off that only records the agent's errors is not an
honest record.

I read ``DownloadReport``'s surviving mutant as landing on the guard ``if
(resumedFromBytes + bytesTransferred != fileSizeBytes)`` and told the agent it
was therefore *"suppress a validation error"*, named in ``R-TEST-02``'s absolute
clause. **It was not the guard.** Line 70 is the guard and line 71 is the
``throw``; javac attributes a whole string concatenation to the statement it
begins, so the surviving addition was the one at line 77, **inside the
diagnostic message**. I checked the source myself after the agent said so, and
it is right.

The guard's own addition had been killed all along -- which is why my
"``(0, 1000, 1000)`` should have thrown" reasoning did not play out. What the
mutant actually corrupted was a message that could have read *"400 kept plus 600
received is -200"* and shipped, because the assertion stopped at
``startsWith("a report must account for every byte")``. That is the **fourth**
shape, an assertion too coarse to see a partial failure, living inside an error
message -- a smaller fault than the one I alleged, and a real one. It is killed
by pinning the whole message with the sum **hand-typed as a fourth
``CsvSource`` column** rather than recomputed.

The instruction that produced this was still the right one: I told the agent to
find out *why* it survived before writing the test, because the answer might be
more interesting than the mutant. It was, and it pointed somewhere other than
where I expected.

An accounting fact worth keeping
---------------------------------

PIT's own console credits a ``TIMED_OUT`` mutant as a kill; ``scripts/build.sh``
line 923 counts ``status='KILLED'`` **only**. So the console said ``Killed 385``
where the gate says ``384/387``, from identical results. **The gate is stricter
than the tool's own summary**, and already implements the principle that a
timeout is not a kill. The two figures will always differ by the timeout count,
and a reader comparing them should not go looking for flakiness.

The nine survivors: seven killed, two argued, one timeout explained
--------------------------------------------------------------------

* **Three ``PartialDownload::discard`` removals** -- the rule that a partial file
  of unknown or stale provenance is thrown away rather than appended to. All
  three are invisible on the happy path, because a completed download moves the
  partial away and a restart truncates it. Each is now asserted on a path where
  the attempt does **not** complete, which is the case that matters: the bytes
  left behind are what a later resuming attempt would find and trust.
* **``close:242``** -- killed by closing the downloader and requiring the next
  fetch to fail naming ``closed`` **and to reach no server**. ``HttpDownloader``
  owns an ``HttpClient`` and an ``HttpClient`` owns threads; unit 5 holds one for
  the length of an install.
* **``nextChunk``'s ``onDisk < declaredTotal``** -- killed by a 206 that lies:
  its content-range promises 100 bytes in total, its ``Content-Length`` promises
  60 more, and it sends 50, so the file ends holding exactly the declared total
  and the transfer still failed. Nothing is short, so "truncated" is the wrong
  word and the checksum decides. This also covered the module's last reachable
  uncovered branch.
* **``copy:503`` removed rather than argued, and I accept the argument.** The
  final ``onProgress(size, declaredTotal)`` was a duplicate -- but the reason to
  delete it is that **it masked endpoint defects**: with it present, a loop
  reporting entirely wrong numbers still ended on the right one. That is not
  hypothetical, it is exactly how my own injection kept the last report correct
  while every mid-transfer report was wrong. My re-injection failing with four
  assertions instead of three is the measurement that the removal strengthened
  the tests rather than weakening them. **A line that can only hide a defect is
  worth less than the assertion it weakens.**
* **Two ``declaredTotal >= 0`` versus ``> 0`` boundaries, argued equivalent** --
  they differ only at a declared length of exactly zero, where the second
  operand becomes "a byte count < 0", which nothing satisfies. The argument is
  in the code, and a new test **reaches the boundary value**, serving
  ``Content-Length: 0``, so the claim rests on a demonstrated-reachable value
  rather than on an assertion that the value is unreachable.
* **The ``TIMED_OUT`` mutant** is a negated conditional on the 416 guard that
  makes the restart unconditional and the loop infinite. Unreachable in correct
  code -- every ``continue`` is guarded by ``point.isPresent()`` and empties
  ``point`` first, so the loop turns at most twice -- and that invariant is now
  written above the loop. The agent deliberately did **not** add a restart
  counter to convert the timeout into an assertion, because in correct code the
  watchdog's ``throw`` would be a branch nothing can test. I accept that: it is
  the same reasoning behind its other deletions, and ``build.sh`` does not credit
  the timeout as a kill in any case.

Three uncovered branches remain, all compiler artefacts: two are javac's
try-with-resources close path and one is the synthetic default of an exhaustive
enum switch. Neither is reachable from Java.

Measured facts recorded for units 5 and 10
-------------------------------------------

All observed by the unit, none of them in the work log before:

* ``HttpDownloader`` is ``AutoCloseable`` and owns an ``HttpClient`` with
  threads. **Close it.**
* **Cancellation deletes the partial file**, so a cancelled 99 MB download
  restarts from zero. Resume survives a *failure*, not a cancellation.
* A truncation or network failure **propagates** out of ``VerifiedDownloader``;
  retrying it is the installer's job. Only a *checksum* failure after a resume
  is retried internally, once.
* **``HttpRequest.timeout`` does not abort a streaming body once the headers
  have arrived** -- verified with a two-second trickle under a 500 ms request
  timeout. That is what makes a 60-second response timeout safe on a 99 MB
  download rather than a guarantee of failure.
* A body shorter than a declared ``Content-Length`` surfaces from the JDK as
  ``java.io.IOException: fixed content-length: N, bytes received: M``, and
  ``java.net.ConnectException`` for a refused loopback connection carries a
  **null** message.
* **The atomic move now has no fallback.** The unit removed the
  ``AtomicMoveNotSupportedException`` catch as unreachable, correctly, because
  the partial file is always a sibling of the destination and the move is
  therefore always a same-directory rename. **Unit 5 must know that this
  reasoning covers the cross-filesystem case and not the Windows-contention
  case**: ``AccessDeniedException`` on a file another process holds open is a
  different exception, and ``STATUS.rst``'s *Platform divergence, in two tiers*
  records ``ATOMIC_MOVE`` under contention as live tier-B residue.

Decided by me: ``org.cometgui.install.download.*`` joins the mutation targets
-----------------------------------------------------------------------------

The package now holds the **availability-versus-corrupt classification** that
``D-008`` turns into a product requirement: a vanished artefact must be reported
as an upstream availability failure naming the URL and the expected checksum,
never as a corrupt download. A mutation that collapsed that distinction would
violate the decision, and nothing would currently stop it.

The root POM's own comment sanctions the addition -- *"A later phase adds its
packages here and flips its own module's switch; it does not narrow this list"*
-- and adding a package **strengthens** the gate rather than weakening it, which
is the only direction this project allows. I make the change myself rather than
handing it to the unit, so that only one party is editing the reactor at a time.

.. _p05-u4-signoff:

Unit 4 sign-off
===============

**Accepted 2026-09-02** at ``00e6494``, after two rounds. My own build on the
accepted tree, nothing else live::

    11/11 stages OK in 1085 seconds.  BUILD OK
    160 report file(s): tests=2962 failures=0 errors=0 skipped=3
    ok  cometgui-install  line 100.0% (1831/1831)  branch 99.4% (746/750)
    ok  cometgui-install  44 compiled class(es), all 44 in the sample
    ok  cometgui-install  848/863 mutations killed = 98.2%
    ok  cometgui-install  146 class(es) analysed, 0 findings
    13 critical package prefix(es) read from pom.xml
    ok  8 architecture rule(s) checked, 0 failures

What I verified rather than read
---------------------------------

237 archive tests green on my own run. The traversal pair is the shape tier 1's
standing direction 1 demands: the **real** ``rel-3-06-05`` macOS zip installs in
named-member mode with digest ``f6c62710...`` **and** is rejected whole, with the
entry named, and a snapshot of the destination's *parent* proves nothing escaped
-- asserted by walking the tree, not by trusting the exception.

``GuardBypassStructureTest`` is the strongest structural check in this phase. It
parses each compiled class's constant pool and requires that **only**
``ExtractionGuard`` can create, write, link, copy or delete a file, with two
anti-vacuity guards (the scan must have found the guard, and must have inspected
at least fifteen classes), plus a separate test requiring the scan to find the
guard's *own* ``newOutputStream``, ``createSymbolicLink`` and
``createDirectories`` -- so a scanner that saw nothing at all fails.

My injection: ``hasDriveLetter`` returning false. **Bit** -- 8 failures, because
the refused-path table already carried ``C:/x`` with a **forward** slash, the one
drive-letter form no other check catches.

Then I gated the package, and it changed the unit
--------------------------------------------------

``org.cometgui.install.archive.*`` was **not** in ``<targetClasses>``, so 8550
lines of the phase's security-critical code carried **no mutation evidence at
all**. The agent reported that residue itself rather than leaving it to be
discovered. I added the package -- the same decision as for the downloader, with
more force, because ``R-SEC-05`` is this phase's security rule and gate item 3
is the gate over it.

First run: **799/864 = 92.4%**, 60 survivors and 5 timeouts. Above the 80%
threshold, so the build was green; it is ``R-TEST-02``'s **absolute clause** that
was not satisfied.

.. _p05-xxe:

**The one that mattered: five XXE guards that nothing tested.** Every one of
``PkgPayloadReader.parse``'s hardening calls -- ``FEATURE_SECURE_PROCESSING``,
``DISALLOW_DOCTYPE``, ``setXIncludeAware``, ``setExpandEntityReferences``,
``setNamespaceAware`` -- could be **deleted with the whole suite still green**.
The xar table of contents is XML that arrived over the network inside a
downloaded ``.pkg``: attacker-controlled input by construction, on the one
parser in this phase that reads hostile XML. The protections were present and
unproven, which is a rule that has never been seen to work.

Two of them were **unprovable as written**, because ``setXIncludeAware(false)``
and ``setNamespaceAware(false)`` restate JDK defaults and a test inspecting a
fresh factory passes without them. The repair changed the shape: ``harden()``
now **forces** the safe state over a factory the caller supplies, and the test
hands it one set to every unsafe value. **I deleted each of the five in turn
myself** and every one failed::

    setFeature(FEATURE_SECURE_PROCESSING)  exit=1  Tests run: 6, Failures: 1
    setFeature(DISALLOW_DOCTYPE)           exit=1  Tests run: 6, Failures: 4
    setXIncludeAware(false)                exit=1  Tests run: 6, Failures: 1
    setExpandEntityReferences(false)       exit=1  Tests run: 6, Failures: 1
    setNamespaceAware(false)               exit=1  Tests run: 6, Failures: 1

Behaviourally, an external entity, a ``SYSTEM`` entity pointing at a local file
(with the file's contents asserted to appear nowhere) and a ten-level
billion-laughs are each refused at the ``DOCTYPE``, artefact named, message
under 500 characters so the refusal is not the bomb going off in the log.

**Ratified, and it goes beyond what I asked:** ``parse`` now uses
``DocumentBuilderFactory.newDefaultInstance`` rather than ``newInstance``,
because ``newInstance`` consults a system property and the class path, so a
dependency could change which parser reads this attacker-controlled document
while the hardening is written against the built-in one's behaviour. That is the
right call and I would have asked for it had I thought of it.

Three product defects, not test gaps
-------------------------------------

The residue was not only thin tests. Three were live faults:

* **A container answering a read with zero bytes made the copy loop spin.** The
  loop tested ``read >= 0``. This is precisely the "mutant that makes extraction
  hang on a 99 MB archive" I asked about, and it was real rather than a test
  artefact. It now refuses, proved against a stream that returns 0 forever.
* **A gzip payload that is not one escaped as a bare ``EOFException`` naming no
  artefact.** Both payload readers now refuse by name.
* **Four readers' ``close()`` calls were unobserved**, so an install could run
  out of file handles. Proved by *naming*, not counting: the test resolves every
  ``/proc/self/fd`` entry and asserts none points inside its own archive
  directory, because a descriptor count drifts for unrelated reasons.

Final archive figure **464/476 = 97.5%**, from 62 non-killed to 12; module
**848/863 = 98.2%**. The 8 remaining survivors and 4 timeouts are each argued
beside the code, and three of the timeouts are mutants that break
``InputStream``'s contract and hang **inside the JDK's inflater** rather than in
anything this project owns.

.. _p05-open-thread:

An open thread, not tidied away
--------------------------------

``everySettingIsForcedFromItsUnsafeValue`` **failed once**, in a single run, and
its assertion message was lost to a truncated pipeline. It has not recurred in
nine subsequent full-module runs, and no test anywhere sets a JAXP system
property. The agent pinned ``newDefaultInstance`` partly to remove ambient
variation but explicitly does **not** claim that was the cause.

**Nine green runs are not an explanation.** This is an unexplained one-off in a
security test, it is recorded as open, and if it reappears that is the thread to
pull.

.. _p05-u5-signoff:

Unit 5 sign-off
===============

**Accepted 2026-09-02** at ``0ff3d72``. ``org.cometgui.install.cache``: 19
production classes, 178 tests. My own build, on a quiet tree, after gating the
new package::

    11/11 stages OK in 1300 seconds.  BUILD OK
    172 report file(s): tests=3140 failures=0 errors=0 skipped=3
    ok  cometgui-install  line 100.0% (2568/2568)  branch 99.5% (956/960)
    ok  cometgui-install  62 compiled class(es), all 62 in the sample
    ok  cometgui-install  1087/1104 mutations killed = 98.4%
    14 critical package prefix(es) read from pom.xml
    ok  8 architecture rule(s) checked, 0 failures

**The defect I injected** was aimed at whether the unit had repeated the very
gap it was sent to close. It had not: ``ArchiveMember.hashes()`` was a recorded
value nothing compared, and the marker's ``payloadEntryCount`` is the same
shape -- so I made ``ToolCache`` trust the marker's own count instead of
counting the directory. Compiled class ``cfa4e9dd...`` to ``3afb20ac...``. It
**bit**: ``ToolCacheTest.aLostFileIsCaughtByTheEntryCount:201 expected:
<CONTENT_COUNT_MISMATCH> but was: <INSTALLED>``. Reverted, hash restored, tree
clean.

**This is the first unit that needed no rework**, and the reason is worth
recording: it is the first whose agent pointed PIT at its own package before
being asked, found 239/241, and reported the two non-kills with arguments. The
three gates I widened after units 3 and 4 each found real defects; this one
found nothing, because the unit had already looked.

What it built that later units depend on
-----------------------------------------

* **``InstallStep`` is an enumeration the pipeline is driven by**, and a step
  added without an action **stops the installer** rather than being skipped.
  The eight names are pinned hand-typed, so a ninth fails that test at once.
* **The marker is written last, after the atomic move**, and carries the length
  and both digests of every file the manifest names, plus
  ``payloadEntryCount``. ``ToolCache.verify`` re-hashes on every read.
* **Interruption is proved in a real second JVM with ``Runtime.halt``** after
  each of the eight steps in turn -- no ``finally``, no unwinding, lock released
  by the OS. Steps 1-6 leave ``NOT_PRESENT``, step 7 ``NO_MARKER``, only step 8
  ``INSTALLED``.
* **The lock is a JVM-wide permit plus a ``FileLock``**, because a ``FileLock``
  is held by the *process* and a second attempt inside one JVM throws rather
  than waiting. Two real JVMs serialise, one is observed to wait, **and a
  control shows the same harness observing an overlap when the lock is left
  out** -- without which the first test could pass vacuously.
* **``ToolProbe`` is a declared, unimplemented seam.** Units 6 and 7 implement
  it. It is also how gate item 2 is served here: a probe stub that fails the
  test if entered proves a corrupted download never reaches execution.

Decisions the unit took, which I ratify
----------------------------------------

* **``AtomicMoveNotSupportedException`` is re-thrown, never handled.** A copy
  fallback is not atomic and would silently replace the guarantee ``R-TOOL-04``
  rests on. Every other ``FileSystemException`` becomes ``CACHE_CONTENDED``
  with **no retry**, quoting the file system's own reason and naming the likely
  holders. Tested on Linux with a read-only parent. **The Windows contention
  case is still untested and the residue is unchanged.**
* **The cache directory uses the normalised version** (``percolator/3.7.1/``),
  because ``ToolVersion.equals`` is numeric and text-named directories would
  make ``3.09`` and ``3.09.0`` two directories for one version. **The marker
  and every user-facing string keep upstream's spelling.** Unit 9 must render
  ``text()``, never the directory name.
* **Step 2 re-hashes the file** rather than trusting the digest
  ``VerifiedArtefact`` reports -- found by mutation testing, because a source
  that lies otherwise passes step 2.

Two findings for any later unit that spawns a JVM
--------------------------------------------------

* **PIT's coverage minion does not use Surefire's class path.** A child JVM
  launched with ``System.getProperty("java.class.path")`` gets the minion's own
  under PIT, dies with ``ClassNotFoundException``, and the parent test hangs
  until timeout -- which failed ``build.sh``'s PIT stage with *"tests did not
  pass without mutation"*. Derive the class path from
  ``getProtectionDomain().getCodeSource()``. And **a mutant is invisible to a
  child JVM**, which loads unmutated classes from ``target/classes``.
* **A mutant that blocks scores ``TIMED_OUT``, not a kill**, and ``build.sh``
  counts only ``KILLED``. One such survivor remains in ``InstallLock.close``.

Rejections and rework
=====================

Units sent back, why, and what changed.

* **Unit 3, sent back 2026-09-02**: progress on a **resumed** transfer was
  reported from the resume point rather than the absolute position, and 338
  tests passed with the defect in place. The twin of a hole the unit had
  already found and fixed in itself for cancellation. See
  :ref:`p05-u3-signoff`.
* **Unit 2, sent back 2026-09-02** for two small additions: a shipped-manifest
  assertion that Comet on Apple silicon is offered its native build before the
  translated one, which is ``D-004``'s own sentence and was proved only on a
  fixture; and the deletion of a Javadoc clause promising an ordering key the
  code deliberately refuses to have. Neither is a defect in the manifest, whose
  54 checksums and honesty properties I verified independently.
* **Unit 1, sent back 2026-09-02** for one round: the narrow-axis validation
  hole in :ref:`p05-u1-injection`, plus an audit of every other rejection test
  in the unit for the same shape, plus proof by re-injecting the same defect
  and watching it go red. The first submission was otherwise sound and was not
  rejected as a whole.

Deferred
========

Anything explicitly not done in this phase, with the reason and where it goes.

.. _p05-escalations:

Blockers escalated
==================

#. **Where Windows gets the Percolator XSD companion pair.** ``R-TOOL-02``
   requires the two XSDs beside the binary. ``specification.rst`` names the
   ``.deb`` for Linux and the ``.pkg`` for macOS and is silent on Windows,
   because ``D-002`` option C deleted NSIS payload extraction -- and the NSIS
   installer is the only Windows artefact that carries them. Unit 0 established
   that both files are **byte-identical across platforms and versions**, so the
   options are: (a) Windows fetches them from the Linux ``noxml`` ``.deb`` (one
   extra 1.8 MB download, no new extractor, no redistribution, and the schemas
   provably match the release); (b) Windows installs without them, which is
   functional -- Phase 00 proved XML output works without the XSDs -- but
   leaves ``R-TOOL-02`` unmet on one platform; (c) reinstate NSIS extraction,
   which an owner decision deleted and which no agent may reverse. **Reported
   to tier 1 with (a) recommended.** Not a ``D-`` item: nothing is
   redistributed and no platform promise changes.

   **ANSWERED by tier 1, 2026-09-02: option (a).** Windows fetches the two
   schemas from the Linux ``noxml`` ``.deb``. Tier 1 re-verified the
   byte-identity itself across the 3.06.5 ``.deb``, the 3.07.1 ``.deb`` and
   the 3.07.1 ``.pkg`` before answering, and recorded the reasoning: the
   choice fills a silence in ``specification.rst`` rather than contradicting
   its text, redistributes nothing, reverses no owner decision and executes no
   installer, and the byte-identity evidence is what makes it safe -- the file
   Windows would have got from the NSIS installer **is** the file it gets from
   the ``.deb``.

   Tier 1 also confirmed independently that the shipped ``percolator_out.xsd``
   declares ``majorVersion`` as ``use="required" fixed="2"`` while the 3.07.1
   binary writes ``3``, so **that schema cannot validate that binary's own
   output unmodified**. The XSDs are therefore a provenance and validation
   asset, not a runtime prerequisite and not, as shipped, a working validation
   gate. **Because taking a Debian package's payload onto a Windows machine
   looks like a mistake to a later reader, unit 2 records the reason in the
   registry itself and unit 11 records it in
   ``docs/developer/tool_registry.rst``.** An undocumented oddity becomes
   somebody's cleanup.

.. _p05-tier1-directions:

Standing directions from tier 1, carried into the units
=======================================================

Given on dispatch of unit 1, 2026-09-02, and repeated in the unit briefs that
own them.

#. **The manifest-names-the-member design must not become the reason the
   traversal guard is never exercised.** Both hold at once: for ``ZIP`` the
   archive's own path never places a file, **and** the guard that rejects
   ``../my_build/percolator-noxml/src/percolator`` is still exercised, against
   that real upstream artefact. Tier 1 has said this is the part of the phase
   it is most likely to try to break at sign-off.
#. **"The output file exists" is not a probe condition**, because the 8+8
   failure leaves a zero-byte file; and **``--help`` arrives on stderr**, so a
   probe reading stdout alone sees an empty string. Both are pinned as facts
   in unit 7's tests, not merely recorded here -- they are exactly the
   assumptions a later agent re-introduces.
#. **The ``R-PLAT-03`` classifier is written against observed text**,
   including the two-layer case in :ref:`p05-central-risk` where stubbing
   ``libboost_filesystem`` exposes ``GLIBCXX_3.4.32`` and ``GLIBC_2.38``
   beneath the missing-object failure. A classifier built from invented
   strings is a rule that has never seen its subject.
#. ``scripts/ci/nightly-manifest-verify.sh`` **stays a stub.** It is Phase
   15's, and a stub that exits non-zero is the correct state.
#. The opt-in real-URL fetch command and its cost are handed to tier 1 with
   the closing report; tier 1 will run it once at sign-off.

.. _p05-orchestrator-2:

Phase orchestrator handover, and the re-scope of units 6 to 12
===============================================================

:Taken over: 2026-09-02 at ``be5dd72``
:By: the second Phase-05 phase orchestrator (session 06)
:From: ``handoffs/PHASE-05-handoff.rst``

Units 1 to 5 are signed off, nothing is landed-but-unsigned, and
``git status --porcelain`` was empty when I arrived. What follows is what I
re-measured myself, what I changed about the plan, and why.

What I re-measured before planning anything
--------------------------------------------

**``origin/main`` is at** ``be5dd72``\ **, the same commit as ``HEAD``.** The
handoff says it is handing over two commits ahead of ``origin/main`` at
``38c17b5``; tier 1 has pushed since, and that sentence is stale. Nothing is
unpublished.

**The gitignored artefact mirror survived the handover**, which was not
certain: ``scratch/phase05/artefacts`` holds 24 files, 185 MB. I re-derived
**32 artefact and companion rows** -- every ``url``/``sha256``/``sizeBytes``
triple in ``manifests/tools.json``, artefacts and companions alike -- from
those bytes: **0 mismatches, 0 missing**. So units 6 and 7 have real upstream
binaries to probe without re-running ``fetch-survey.sh``.

.. _p05-scope-change:

What I changed about units 6 to 12, and why
--------------------------------------------

The handoff re-scoped units 6-12 and I inherit that re-scope, with two
changes. Both are recorded rather than made silently, because a renumbered or
repurposed unit that nobody wrote down is the drift this project keeps paying
for.

**Change 1: old unit 8 is absorbed into unit 7.** The handoff recommends this
and I agree: local binary registration is the same adapter code reading a
different source of binary, and splitting it buys a second agent's context and
nothing else.

**Change 2, which is mine: unit 8's number is reused for work nobody owned.**
``org.cometgui.domain.tools.ToolManager`` -- the port the Tool Manager UI is
allowed to see, and the only one it has -- **has no implementation and no unit
that produces one.** The handoff's unit 9 is "a view-model and view ... plus
wiring in ``ApplicationServices``", and the composition behind the port is
implied by the word *wiring*. It is not wiring. It is: manifest ``select``
per host; the host-requirement filter; ``ToolCache.verify`` for install state;
``InstallPipeline`` started on a background thread behind an ``InstallHandle``;
failure mapped to ``ToolInstallState`` and ``LoaderDiagnostic``; and
``registerLocalBinary``. That is a work unit, and it is the one gate items 1,
2, 5, 7 and 8 actually run through.

**It goes before the UI, which is the handoff's own recommendation applied one
unit earlier.** The handoff says to move the end-to-end install ahead of the
UI polish "because that is the item most likely to reveal the domain port is
the wrong shape, and discovering that after the UI is built is expensive". A
``ToolManager`` implementation exercised from a JUnit harness reveals a wrong
port shape *without* a UI existing at all, which is cheaper still -- and gate
item 1's wording, "driven through the Tool Manager UI, not from a test
helper", still forces the through-the-UI unit afterwards. So the order is:
port runtime (8), then UI (9), then the install driven through the UI (10).

.. _p05-tools-seam:

An engineering decision the two critical-path units both need
--------------------------------------------------------------

``cometgui-tools`` depends on ``cometgui-domain`` and ``cometgui-process``, and
on nothing else. ``ToolProbe`` lives in ``org.cometgui.install.cache`` and
takes an ``ArtefactRecord``, which lives in ``org.cometgui.install.registry``.
**So the functional capability probe cannot implement ``ToolProbe`` from the
module it belongs in**, and two units would otherwise discover that separately
and answer it differently.

Decided by me, before dispatching either: ``org.cometgui.install.probe``
declares a narrow ``CapabilityProber`` port stated entirely in **domain**
vocabulary -- tool, version, host platform, the executable's path, out to a
``Set<ToolCapability>``. ``cometgui-tools`` implements it; a composing
``ToolProbe`` in ``org.cometgui.install.probe`` runs loadability, then
identity, then delegates capability to it. **No module's dependency list
changes**, the layering rules are untouched, and the seam is the same shape as
``Downloader`` and ``HashService``. If a unit finds this wrong, it reports it
rather than adding a dependency.

Units 6 to 12, as this orchestrator will run them
--------------------------------------------------

The rows above, written by my predecessor, are left exactly as they were: they
are the record of what was planned, and overwriting them would destroy the only
evidence that the plan changed. This table is what I will actually dispatch.
Where the two differ, the difference is explained in :ref:`p05-scope-change`.

**Serially.** No positive argument exists that any two of these cannot collide
-- they share ``cometgui-domain``, the Maven working tree, ``_build/m2repo``,
the docs gate and the git index -- so none is offered, and none will be.

.. list-table::
   :header-rows: 1
   :widths: 4 34 20 42

   * - #
     - Unit, and what changed from the predecessor's row
     - Rules and gate items
     - Sign-off

   * - 6
     - **Loadability and identity probes, and the ``R-PLAT-03``
       diagnostic.** ``org.cometgui.install.probe``. Unchanged in substance.
       Adds ``minimumGlibcxx`` to ``MinimumHostRequirements`` and the manifest,
       here rather than earlier, and owns the host-satisfaction rule including
       the exact-equality boundary. **Also declares ``CapabilityProber``**, the
       port unit 7 implements -- see :ref:`p05-tools-seam`.
     - ``R-PLAT-02``, ``R-PLAT-03``, ``R-TOOL-06``, gate 5
     -

   * - 7
     - **Tool adapters, the functional capability probe, and local binary
       registration.** ``org.cometgui.tools.*``. **Absorbs the old unit 8**:
       same adapter code, different source of binary. Flips
       ``cometgui-tools``'s mutation switch in the same commit as its first
       class, and re-runs ``scripts/verify-test-gates.sh`` afterwards, because
       that harness writes a control class into the package this unit fills.
     - ``R-PERC-02``, ``R-TOOL-02``, ``R-TOOL-06``, ``R-TOOL-07``,
       ``R-TOOL-08``, ``R-PERC-01``, gates 6, 7
     -

   * - 8
     - **The Tool Manager runtime behind the port.** *Scope changed.* This
       number was "local binary registration"; that is now unit 7's. It is
       reused for the implementation of ``org.cometgui.domain.tools.ToolManager``,
       which **has no implementation, no test double and no unit producing
       one**, and which is what gate items 1, 2, 5, 7 and 8 actually run
       through. Exercised end to end from JUnit with **no UI**, so a wrong port
       shape is found before the UI is built.
     - ``R-TOOL-04``..``R-TOOL-08``, ``R-PERC-01``, gates 1, 2, 5, 7, 8
     -

   * - 9
     - **Tool Manager UI section and wiring.** Unchanged, but now built on a
       port that has been run.
     - ``R-PERC-01``, ``R-PERC-03``, ``R-PERC-11``, gates 5, 8
     -

   * - 10
     - **The end-to-end install driven through the UI**, plus the deliberate
       PDV cancellation and restart, plus the opt-in real-upstream variant.
       Gate item 1's wording is literal -- "driven through the Tool Manager
       UI, not from a test helper" -- which is why unit 8 does not discharge
       it.
     - gates 1, 2
     -

   * - 11
     - **Documentation**: ``docs/developer/tool_registry.rst``,
       ``docs/tool_manager.rst``, and **only the artefact table** in
       ``docs/platform_support.rst``. Checked against the stubs themselves:
       ``docs/developer/tool_adapters.rst``'s per-tool sections belong to
       phases 08, 09, 11 and 12, and ``docs/developer/version_capabilities.rst``
       belongs to Phase 09. Neither is this phase's.
     - ``R-DOC-06``, ``R-PERC-12``
     -

   * - 12
     - **``scripts/verify-install-gates.sh``**, assembled from the injections
       in this log rather than invented, in
       ``scripts/verify-provenance-gates.sh``'s shape. Registration in
       ``verify-all-gates.sh`` is escalated, not assumed.
     - every gate item
     -

.. _p05-zombie-processes:

A mechanical cause for the process-check trap nobody had named
---------------------------------------------------------------

``STATUS.rst`` records, under *The lock nothing takes, and the ruling on it*,
that a "wait until the other harness exits" loop and an "is the tree busy"
check each used ``pgrep
-f`` with a pattern their own command line contained, so both matched
themselves -- the impatient and the patient forms of the same mistake, both
tier 1's, and the rule drawn from them is that **a process check must exclude
the checker.**

There is a second cause, and it is worse because excluding the checker does not
fix it. On this host right now::

    $ ps -eo stat | grep -c Z
    705
    $ ps -eo stat,comm | awk '$1 ~ /Z/ {print $2}' | sort | uniq -c | sort -rn | head -4
        345 java
        176 cat
         93 bash
         52 sleep

**705 zombie processes, 345 of them named ``java``.** A zombie stays in the
process table until its parent reaps it, and these parents are dead shells from
sessions that ended hours and days ago. So ``pgrep java``, ``pgrep -f mvn`` and
every ``ps | grep`` liveness check in this tree matches hundreds of processes
that **exited long ago** -- and reports BUSY for ever, correctly excluding the
checker and still being wrong.

The rule that follows: **a liveness check must exclude defunct processes as
well as the checker** -- match on a live attribute such as a non-zero RSS, or
on the JVM's real main class read from a still-open ``/proc/<pid>/cmdline``, and
read the matched lines rather than the count. And the standing conclusion is
unchanged and is the one to rely on: **the completion notification is the only
signal that means finished.**

I found this while waiting for the gate suite, by checking that the suite was
still alive without matching my own command line.

.. _p05-gate-retake:

The aggregate suite, re-taken -- and it is red
===============================================

``bash scripts/verify-all-gates.sh``, run by me at ``be5dd72`` on a quiet tree
with nothing else building, 2026-09-02::

    10 control(s) passed, 1 failed, in 1504 seconds (25m04s).
    tests: exited 1; a gate did not bite, or the harness could not run.
    verify-all-gates.sh: 1 control(s) failed.

The recorded figure this replaces -- **11 controls, 0 failed, 2926s**, measured
at ``96e7da4`` -- is now known to have been stale by movement rather than by
noise, exactly as the handoff warned. Every other control passed at or above
its floor: license 5, workflows 19, docs 1, traceability 8, sbom 8, depscan 16,
pipeline 24, quality 42, shell 30, provenance 24. **No floor was lowered and
none needs to be.**

``scripts/verify-test-gates.sh`` reports **33 assertions passed, 4 failed**.
The four are two independent faults, and the second is the serious one.

.. _p05-control0:

Fault 1: an argued equivalent mutant makes CONTROL 0 red
---------------------------------------------------------

::

    FAIL  the baseline mutation run really mutated something, and killed all of
          it: the clean tree left survivors -- 368/369 killed (99%)

Read out of ``mutations.xml`` rather than the console, the survivor is
``org.cometgui.domain.tools.ToolVersion.compareTo`` line 214,
``ConditionalsBoundaryMutator`` -- ``index < width`` becoming ``index <=
width``.

**It is a genuine equivalent mutant and no test can kill it.** The extra
iteration compares ``componentAt(width)`` on both sides; ``componentAt``
returns 0 at or past the end of the component list, so it is 0 against 0 for
any two versions, the difference is 0, and the loop falls through to the same
``return 0``. Unit 1's author wrote that argument into a comment above the loop
and declined the alternative implementation that would kill it, because that
one is correct only by way of an invariant established in another method. I
agree and I am not asking for it to be reversed.

``assert_pit_killed_everything`` requires ``killed == generated`` **and**
``percent == 100``, and its own comment gives the reason: *"a run that left
survivors means the clean tree does not meet the gate, which would make every
dirty run below meaningless."* **That sentence is now false.** The clean tree
does meet the gate -- 99.7% against a floor of 80 -- so the runs below are not
meaningless. The assertion has become stronger than the property it defends,
and legitimate growth turned it red.

**Phase 04 signed off with ``cometgui-domain`` at 204/204. Unit 1 took it to
368/369**, a figure the unit 1, 2, 3, 4 and 5 sign-offs and ``STATUS.rst`` all
record as accepted. So this control has been failing since ``42033ad``, and
nobody saw it because the aggregate suite had not been re-run since phase start
-- which is open thread 2 in the handoff. This is what was behind it.

.. _p05-sandbox-manifest:

Fault 2: this phase broke the sandbox, and two controls now fail for the wrong reason
--------------------------------------------------------------------------------------

::

    FAIL  the documented build command rejects unmeasured coverage: failed, but
          without the expected diagnostic 'MISSING  cometgui-domain: ...'
    FAIL  the documented build command rejects the incomplete population:
          failed, but without the expected diagnostic 'ABSENT   cometgui-domain: ...'
    FAIL  and it names the class that left the sample: nothing matches
          '^ +org\.cometgui\.domain\.secrets\.SecretRegistry$'

Controls 7 and 8 damage ``cometgui-domain/pom.xml`` in the sandbox and require
``bash scripts/build.sh`` to reject it **at the census stage, with the census's
own diagnostic**. The build does fail. It fails **three stages earlier**, in
``build``, and the reason is ours::

    ShippedManifestTest.percolator309HasNoLinuxRow:525 ? InvalidArtefactManifest
    the tool artefact manifest is missing from the classpath at "/tools.json"

``Tests run: 806, Failures: 3, Errors: 23`` in ``cometgui-install``; control 8's
log carries the same message 52 times.

**The cause.** Unit 2 ships ``manifests/tools.json`` into
``cometgui-install``'s jar through a ``<resource>`` element pointing at
``${maven.multiModuleProjectDirectory}/manifests`` -- one file, shipped once,
which is the right design and I am not reversing it. But
``scripts/verify-test-gates.sh`` builds its sandbox from a **deliberately
minimal** copy: ``.mvn``, ``config``, ``scripts``, ``specification.rst``, each
module's ``pom.xml`` and each module's ``src``. There is no ``manifests/``
directory in ``_build/test-gate-sandbox``, confirmed by ``ls``. So in the
sandbox that resource directory does not exist, ``/tools.json`` is not on the
classpath, and **26 ``cometgui-install`` tests fail in every sandbox build.**

**Why this is the serious one.** It is
``STATUS.rst``'s catalogued *a control that fails for the wrong reason*,
happening for real, and it lands on the check tier 1 closed on 2026-09-02
together with "a control proving it bites". The census itself still runs and
still bites in the real build; what is broken is **the proof that it does**. A
control that goes red whatever you do to it is worth no more than one that goes
green whatever you do to it, and this one has been red since unit 2 landed.

**The repair is one line, and the harness's own rule prescribes it.** Above the
copy loop it says: *"the sandbox carries files the build reads as INPUT ... add
them the same way, with the reason, rather than widening this to a blanket
copy."* ``manifests/tools.json`` is a file the build reads as input -- a module
POM names it as a ``<resource>``. Copying ``manifests/`` beside ``config/``
restores both controls to grading what they mean to grade, lowers no floor, and
widens nothing. **``scripts/verify-test-gates.sh`` is tier 1's file, so this is
escalated, not fixed.**

.. _p05-exit-code-again:

And the trap caught me too
---------------------------

I ran the suite in the background as
``bash scripts/verify-all-gates.sh > log 2>&1; echo "EXIT=$?"``. The harness
reported the task as **"completed (exit code 0)"** -- the exit code of my
wrapper, not of the suite, which exited 1. Had I taken the completion
notification at face value I would have reported a green suite and moved on.

**Exit code 0 proves nothing, and that applies to the exit code a wrapper
reports as much as to the one a tool returns.** The suite's own summary line is
the evidence; the notification is not. Recorded because it is the fourth
distinct form this trap has taken in this project, after the ``+`` separator,
the ``package.*`` glob and the missing ``surefire.`` prefix.

.. _p05-tier1-answers:

Tier 1's answers, and both faults repaired at ``90d87fa``
==========================================================

Both faults in :ref:`p05-gate-retake` were repaired by tier 1 within hours of
being reported, and all four questions were answered. Recorded here with what I
checked myself, because "tier 1 says it is fixed" is not evidence any more than
"the agent reported success" is.

**What I verified rather than took.** ``git show 90d87fa -- scripts/verify-test-gates.sh``,
read as a diff:

* ``assert_pit_killed_everything`` no longer demands ``killed == generated``.
  It now requires the SURVIVOR SET to equal a hand-typed list exactly --
  ``org.cometgui.domain.tools.ToolVersion:214:ConditionalsBoundaryMutator``,
  one entry. **This is stricter than what it replaced, in every direction that
  matters**: a new survivor fails, a survivor that moves class or line fails,
  and a listed survivor that is now *killed* fails too, so the list cannot
  become a drawer that absorbs regressions. The ``generated > 0`` anti-vacuity
  check is untouched. Nothing was lowered.
* The sandbox gains ``cp -r manifests`` and a ``ln -s scratch``, each under the
  rule already written above the copy loop -- the sandbox carries what the
  build reads as INPUT -- and each with its reason in the file. A precondition
  in ``main`` now ``die``\ s with an actionable message when
  ``scratch/phase05/artefacts`` is absent, instead of letting it surface as 13
  test failures inside a sandbox build.

Tier 1 re-ran it: **37 assertions passed, 0 failed**, with control 8 quoting
the census diagnostic and naming the class on the census's own line.

**A standing rule this creates for every unit of this phase.** ``cometgui-domain``
is mutation-critical and units 6 and 8 both touch it. **Adding an entry to that
survivor list to make a build pass is a weakening and is forbidden.** The only
honest reason to add one is a *new* equivalent mutant argued in the production
code first and escalated. A unit that finds itself wanting to edit that list
reports it to me instead.

**One observation I am keeping rather than escalating.** The sandbox now
symlinks the real ``scratch/`` rather than copying it -- correct, since it is
146 MB and read-only by convention -- but a sandbox test that ever *wrote*
there would corrupt the working tree's mirror. I re-derived the mirror's 32
artefact and companion checksums before any of this and found 0 mismatches, so
I have a baseline; I will re-derive them at the phase's closing measurement and
say whether they still hold. Separately, ``pit_report_of`` falls back to the
**working tree's** ``mutations.xml`` when the sandbox has none, which would
grade the wrong tree -- it cannot bite here, because the baseline PIT run
always precedes the assertion, but it is the ninth shape sitting one missing
file away.

The four answers, which bind the remaining units
-------------------------------------------------

#. **The re-scope is approved as proposed**, including unit 8's reuse for the
   ``ToolManager`` runtime ahead of the UI. Order 6, 7, 8, 9, 10, 11, 12,
   serial.
#. **The macOS attempt on gate item 9 is approved**, as a **late unit after
   unit 10**, so it does not displace the units other gate items depend on.
   **The negative control is mandatory**: if leaving ``com.apple.quarantine``
   set does not produce a refusal, the result is "this check cannot go red" and
   is reported as such, never as a pass. It is **unit 13**, and it runs between
   units 10 and 11. Its number is higher than its position deliberately --
   renumbering 11 and 12 would invalidate references already committed in this
   log, and a silently renumbered unit is the drift this project keeps paying
   for.
#. **Unit 12 may make the additive edit** registering
   ``scripts/verify-install-gates.sh`` in ``scripts/verify-all-gates.sh``.
   Additive only; never lower a floor; raising its own floor is expected.
#. **``R-SEC-06`` is recorded plainly as vacuously satisfied**, because
   ``D-002`` option C means the project builds no tool binaries, so the rule
   has no subject in this phase. It is not delivered work and will not be
   written up as any.

**And one instruction that changes unit 8's scope.** The ``ToolOffer`` gap --
a record whose Javadoc says *"everything a scientist is shown about a tool is
expressible here, or it is not shown"*, carrying no download size, so the Tool
Manager cannot tell a user that PDV is 99 MB -- is to be **fixed in unit 8, not
deferred**. That is the port being the wrong shape, found before the UI rather
than after, which is what unit 8 exists for.

.. _p05-u6-signoff:

Unit 6 sign-off: sent back for one round
=========================================

**What I ran myself**, on the committed tree at ``9fd1ea8``, with nothing else
building and ``git status --porcelain`` empty. Every figure below is from my own
run, not from the agent's report.

``bash scripts/build.sh``, with the exit status captured **into** the log rather
than reported by a wrapper -- see :ref:`p05-exit-code-again` for why that
matters::

    185 report file(s): tests=3280 failures=0 errors=0 skipped=3
    11/11 stages OK in 1302 seconds.  BUILD OK
    === build.sh EXIT STATUS: 0 ===

    ok  cometgui-domain   line 100.0% (834/834)  branch 100.0% (352/352)
    ok  cometgui-domain   49 compiled class(es), all 49 in the sample
    ok  cometgui-domain   369/370 mutations killed = 99.7%
    ok  cometgui-install  line 100.0% (3101/3101)  branch 99.6% (1117/1121)
    ok  cometgui-install  78 compiled class(es), all 78 in the sample
    ok  cometgui-install  1285/1302 mutations killed = 98.6%
    ok  8 architecture rule(s) checked, 0 failures
    14 critical package prefix(es) read from pom.xml
    ok  every module with critical-package code has its mutation gate on

**Read out of ``mutations.xml`` rather than the console**, because the two
disagree by the timeout count by design: ``cometgui-domain`` has **370
mutations, 369 killed, and exactly one survivor** --
``org.cometgui.domain.tools.ToolVersion:214:ConditionalsBoundaryMutator``, which
is precisely the entry ``scripts/verify-test-gates.sh`` now pins. Nothing added,
nothing moved, nothing newly killed, so that harness's hand-typed list still
matches. ``cometgui-install`` has 1302 mutations and 17 non-kills, and I
enumerated all 17: **not one is in ``org.cometgui.install.probe``.** They are
the pre-existing ``archive``, ``download`` and ``cache`` residue from units 3,
4 and 5.

**Nothing was weakened, checked rather than assumed.** ``git diff 95daeef..9fd1ea8``
over ``pom.xml``, every module POM, ``config/``, ``.mvn/``, ``scripts/``,
``STATUS.rst``, ``DECISIONS.rst``, ``phases/``, ``ONBOARDING.rst``,
``CLAUDE.md``, ``specification.rst`` and ``handoffs/`` is **empty**. No
``@Disabled``, no ``<exclude``, no ``assumeTrue`` was introduced -- the only
matches for those words in the diff are prose in Javadoc and ``@DisplayName``
saying the tests do *not* skip.

**One file, shipped once**, verified both ways: ``manifests/tools.json``,
``cometgui-install/target/classes/tools.json`` and the member ``tools.json``
**inside the built jar** all hash
``fafe9d3299b2f30dc40fd6d8057178178531e2739484f81ed75d9b120ac02af0``.

.. _p05-u6-injection:

The defect I injected, which the unit did not catch
----------------------------------------------------

I did not choose it from the unit's acceptance conditions. Every candidate I
drew from that list was already graded, and several were graded better than the
list asked -- the manifest-versus-banner disagreement, the alternatives filter,
the timeout cancellation and the "unknown C library leaves the C++ refusal
standing" case all have their own tests, and none of them was a listed
condition. So I asked the question :ref:`status-injection-from-outside` says to
ask: **what silent behaviour does this code have that no condition names?**

``ProbeGatedOffers.decide`` declares ``throws IOException``, and
``LoadabilityCheck.refusalFor`` is documented as throwing "if the binary cannot
be reached at all". **No test anywhere makes it throw.** So I made an
unreachable binary *offered* instead of refused, which is the plausible-sounding
reading -- "a binary we could not reach is not one we have judged"::

    Optional<LoaderDiagnostic> refusal;
    try {
        refusal = refusalFor(record, check);
    } catch (IOException unreachableBinary) {
        refusal = Optional.empty();
    }

The injection **landed**, checked rather than assumed: the anchor occurred
exactly once; the source hash went ``616a373b...`` to ``9bc032c4...``; the
**compiled class** went ``1c8f3663...`` to ``a3a17301...``; and ``javap -c``
shows the ``java/io/IOException`` catch in the bytecode.

``mvn -o -pl cometgui-install test`` then reported::

    Tests run: 944, Failures: 0, Errors: 0, Skipped: 1
    BUILD SUCCESS
    === mvn EXIT STATUS: 0 ===

**944 tests passed with a tool that could not be started being offered to the
user.** That is ``R-TOOL-06``'s last sentence -- *"A tool that fails loadability
shall never be offered for selection"* -- and the second half of gate item 5,
switched off for one way of failing.

**It is this phase's own pattern again**: a rule graded at one point on an axis
it does not depend on. The offered-set rule is graded thoroughly over *what the
probe answers* and not at all over *whether the probe could answer*. The unit
graded the axis it was told about.

Restored with ``git checkout --``; source hash back to ``616a373b...``, the
marker greps back out at zero occurrences, ``git status --porcelain`` empty.

What the rework must do
------------------------

#. **Decide what an unreachable binary means, deliberately, and write the reason
   down.** My steer, which the unit may argue against with evidence: the safe
   direction is the one ``ProbeFailureKind.stage()`` already takes for an
   ambiguous kind -- *"we did not establish that it starts"*, never *"we
   established that it can be offered"*. Today the exception propagates out of
   ``decide`` and the whole offer list fails, which is at least not "offer it";
   whether it should instead become a ``Refusal`` carrying a diagnostic is the
   unit's call. **"Offered" is not among the available answers.**
#. **Grade it.** A test in which ``LoadabilityCheck`` throws.
#. **Audit the unit for the same shape** -- every seam this unit declares with a
   ``throws`` clause, and every path where a stage *cannot run* rather than
   returning a verdict, including ``StagedToolProbe``'s and
   ``HostCxxRuntime.highestGlibcxxIn``'s. Repairing only the defect I found is
   the cheap response; unit 1 was sent back with the same instruction and the
   audit found a second hole.
#. **Prove it by re-injecting** the same defect and showing it go red, with the
   ``Tests run:`` line.

The unit is otherwise the strongest first submission this phase has had, and
none of the rest of it is in question.

.. _p05-u6-rework:

Unit 6 rework: the audit was real, and one defect I found by reading
=====================================================================

**Reworked at ``704ce1c``.** The fix is the shape tier 1 ruled from the
specification: ``ProbeGatedOffers.decide`` no longer declares ``throws
IOException``, an unreachable binary becomes **one** ``Refusal`` carrying its
own diagnostic and its own alternatives, and every other candidate is still
decided. The reasoning is written into the class against the requirement text.
The agent's commit touched **seven files, all under ``install/probe/``** -- I
checked ``git show --stat 704ce1c`` rather than the range, because the range
``9fd1ea8..704ce1c`` also contains my own sign-off commit and tier 1's session
record, and reading the range would have shown two ``handoffs/`` files as though
the agent had written them. It had not.

**My own build on ``704ce1c``**, quiet tree, exit status captured inside the
log::

    185 report file(s): tests=3294 failures=0 errors=0 skipped=3
    11/11 stages OK in 1227 seconds.  BUILD OK
    === build.sh EXIT STATUS: 0 ===

    ok  cometgui-domain   line 100.0% (834/834)  branch 100.0% (352/352)
    ok  cometgui-domain   49 compiled class(es), all 49 in the sample
    ok  cometgui-domain   369/370 mutations killed = 99.7%
    ok  cometgui-install  line 100.0% (3113/3113)  branch 99.6% (1117/1121)
    ok  cometgui-install  78 compiled class(es), all 78 in the sample
    ok  cometgui-install  1288/1305 mutations killed = 98.6%
    ok  8 architecture rule(s) checked, 0 failures

Read out of ``mutations.xml``: ``cometgui-domain``'s single survivor is still
exactly ``ToolVersion:214:ConditionalsBoundaryMutator``, unmoved, so the pinned
list still matches. **``org.cometgui.install.probe``: 199 mutations, 199
killed, no survivors and no timeouts.**

**One unexplained delta, recorded rather than shrugged at.** The agent reported
``1289/1305``; my run of the same commit reports ``1288/1305``, and the build's
own line says ``1288``. One mutation, in the safe direction. The likely cause is
a mutant landing on the ``KILLED``/``TIMED_OUT`` boundary under different load,
and ``build.sh`` counts only ``KILLED`` -- but nobody has demonstrated that, and
the probe package is ``199/199`` in both runs. This is the same shape as the
``tests=2482`` versus ``2483`` delta in unit 2, and it is worth one sentence
rather than nothing.

The second, unannounced injection: the audit was real
------------------------------------------------------

I injected a defect the agent was not told about, again from outside its
acceptance conditions and this time aimed at the rework itself: in
``unreachableRefusal`` I replaced ``alternatives.apply(record)`` with
``List.of()`` -- the plausible-sounding *"a binary we could not reach tells us
nothing about what to try instead"*. It attacks precisely the ``R-PLAT-03``
clause tier 1 ruled from, on the path the rework created, and it leaves the
identical call in ``advanceRefusal`` untouched so only the new path changes.

It **landed**: anchor unique, source ``4fa7bcd0...`` to ``da9b8ad2...``,
compiled class ``21874adc...`` to ``8077f024...``, and ``javap -c`` shows the
``Function.apply`` call sites drop from **2 to 1**.

It **bit**: ``Tests run: 958, Failures: 3``, ``BUILD FAILURE``, exit 1. Three
tests, each pinning the **whole** message hand-typed, and one of them on a real
staged entry::

    expected: <... Alternatives: percolator 3.06.5 linux-x86-64.>
    but was:  <... Alternatives: none known -- registering a local binary is
               the documented remedy.>

So the rework graded the requirement, not merely the control flow. Restored from
the snapshot taken before injecting -- source hash back to ``4fa7bcd0...``,
marker greps back out at zero, ``git status --porcelain`` empty.

.. _p05-u6-alternatives-defect:

What I found by reading, which no injection would have shown me
-----------------------------------------------------------------

``ManifestAlternatives.forArtefact`` excludes candidates with::

    .filter(candidate -> !candidate.version().equals(record.version()))

The intent is right -- do not offer the build that just failed as somewhere to
go instead -- but it is keyed on the **version** where it means the **row**, and
this manifest has a case where those differ. Checked against the shipped file
rather than argued: on ``macos-aarch64`` Comet 2026.02.2 has **two rows with two
different files**, ``comet.aarch64.macos.exe`` (native) and ``comet.macos.exe``
(x86-64, translated). They survive ``select`` as two genuine offers, because
unit 2 keyed the one-row-per-download rule on the **URL** and these are two
URLs; ``ShippedManifestTest.cometIsOfferedNativelyOnAppleSilicon`` exists
precisely because both are offered, native first.

**So if Comet's native build fails to load on an Apple silicon Mac, the x86-64
build is filtered out of the alternatives because it shares a version, and the
user is told "Alternatives: none known -- registering a local binary is the
documented remedy."** That statement is false: there is a managed build in the
manifest that ``D-004`` says runs there under Rosetta 2. ``R-PLAT-03`` requires
the available alternatives to be named, and this is the one manifest case where
the alternative is a sibling row rather than another version.

**It is unit 2's lesson, one unit later.** A fixture built to exercise "another
version of the same tool" contains exactly that and nothing awkward; the real
manifest contains one tool with two builds of one version on one host. The four
existing alternatives tests are all sound and none of them can see this, because
none of them varies the axis where two rows share a version.

The repair is to exclude the **row** rather than the version -- by download URL,
which is the key unit 2 already established for exactly this reason, or by
platform. It is a one-line change plus a test on the shipped manifest, and I am
sending it back rather than making it myself, because a production rule with a
test is unit work and because the agent should see the shape.

**Unit 6 is not accepted yet.** Everything else about it stands: the rework is
correct, the audit was real and is proved so by a defect it was not told about,
and this is the last item.
