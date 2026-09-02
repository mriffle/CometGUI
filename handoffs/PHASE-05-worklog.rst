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
     - ``R-TOOL-01``, ``R-TOOL-03``, ``R-PERC-12``, gate 8
     -

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
     - ``R-SEC-02``, ``D-008``, gate 1
     -

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
     -

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
     -

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

Rejections and rework
=====================

Units sent back, why, and what changed.

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
