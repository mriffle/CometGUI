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
       progress, and the ``ToolRegistry``/``ToolInstaller`` ports the UI is
       allowed to see. *Accepts when:* version ordering is proved by a table
       including ``3.06.5 < 3.07.1 < 3.09`` and ``2026.02.2``; platform
       detection is proved from pinned ``os.name``/``os.arch`` triples for all
       five tier-1 pairs plus an unknown pair that must be rejected, not
       guessed; every enum constant is exercised; ``cometgui-domain`` still
       meets 90/85 and its mutation gate.
     - ``R-TOOL-01``, ``R-PLAT-02``, ``R-PERC-01``
     -

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

Rejections and rework
=====================

Units sent back, why, and what changed.

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
