=====================================
PHASE-05: Tool Registry and Installer
=====================================

:Phase: 05
:Status: NOT STARTED
:Depends on: 01, 03, 04
:Blocked by decisions: none. D-002, D-003 and D-004 are all DECIDED --
   see In scope.
:Delivers: R-TOOL-01..09, R-PLAT-02, R-PLAT-03, R-PLAT-04, R-PLAT-05, R-SEC-02, R-SEC-05, R-SEC-06
:Contributes to: R-PERC-01
:Proves: AC-INS-01..10

Purpose
-------
The zero-manual-install promise, and the place where the project's largest
technical risk lands. This phase must handle the artefact kinds that actually
exist -- bare executables with companion DLLs, JARs and portable zips -- and
must never present a tool as available when it cannot be obtained or cannot
run.

``D-002`` **option C was decided on 2026-08-29 and this phase is re-scoped by
it.** Percolator's binary comes from the portable ``noxml`` zip on every
tier-1 platform, so the NSIS and ``xar``/cpio payload extraction this phase was
going to build **is not built**. Read ``D-002`` in ``DECISIONS.rst`` and the
amended *Percolator versions and artefact availability* section of
``specification.rst`` (revision 7) before decomposing the phase; the two costs
that replaced the deleted work -- separately sourced XSDs, and the Windows
Visual C++ runtime -- are in scope and are named below.

In scope
--------

* The versioned artefact manifest with every field from the specification,
  including artefact kind, companion files and minimum host requirements.
* Downloader with progress, cancellation and resumption or clean restart.
* Mandatory SHA-256 verification before any execution; MD5 recorded.
* One extraction implementation covering every artefact kind, with
  traversal, absolute-path, symlink and decompression-bomb protection
  applied uniformly.
* Atomic install with a completion marker, platform fix-ups (executable
  bits, macOS quarantine removal) and interrupted-install recovery.
* Three-stage probing: loadability, identity, capability -- with distinct
  failure states and the actionable diagnostic for loader failures.
* Managed installs for Comet, Percolator, PDV 2.7.0 and the Limelight
  converter JAR.
* **Three managed Percolator versions** (``D-003``, decided 2026-08-30):
  3.07.1 (default for Limelight runs), 3.09 (current, no Limelight) and
  3.06.5 (reach -- ``GLIBC_2.14``, the lowest floor in the release history).
  That set is what the project *attempts* to offer; ``R-PERC-01``'s
  artefact-plus-probe test decides what each platform actually gets. **Expect
  3.09 on Linux to be difficult or absent**: it publishes no portable archive,
  its ``.deb`` needs ``GLIBC_2.38`` and its ``.rpm`` needs Boost libraries it
  does not ship. Absent is honest; a fabricated manifest entry is not.
* Percolator installed from the platform's portable ``noxml`` zip -- kind
  ``ZIP`` -- on Linux, macOS and Windows. **``NSIS_PAYLOAD`` is not
  implemented.** ``DEB_PAYLOAD`` (``ar`` + ``data.tar.gz``) and ``PKG_PAYLOAD``
  (``xar!`` + gzip + ``070707`` cpio) survive **only** to fetch the XSD
  companions, below. Installers are never executed.
* The two XSD companion files, which **no portable archive ships**: fetched
  from the matching ``noxml`` ``.deb`` (Linux) or ``.pkg`` (macOS) as a second
  small download and installed atomically with the binary. Phase 00 proved by
  execution that XML output works without them, so they are a provenance and
  validation asset, not a runtime prerequisite -- record that distinction in
  the registry rather than leaving it implicit, and note that the shipped
  ``percolator_out.xsd`` fixes ``majorVersion`` at ``2`` while the binary
  writes ``3``, so it cannot serve unmodified as a validation gate.
* The Windows Visual C++ runtime dependency the portable zip does not carry
  (``MSVCP140.dll``, ``VCRUNTIME140.dll``, ``VCRUNTIME140_1.dll``,
  ``VCOMP140.DLL``): declared as a companion requirement, with its absence
  reported as an ``R-PLAT-03`` **loader** failure naming the DLL -- never as
  "not XML-capable". The ``noxml`` NSIS installer holds the same
  ``percolator.exe`` byte for byte and nine such DLLs, and is the documented
  fallback source.
* Rosetta 2 detection on Apple silicon, since the selected macOS Percolator
  is x86-64 (``D-004``): verify before the stage runs and explain if absent.
* Managed tool binaries are **downloaded from upstream by pinned URL and
  SHA-256, never redistributed** (``D-008``, decided 2026-08-29). A vanished or
  re-tagged upstream artefact must be reported as an upstream *availability*
  failure naming the URL and the expected checksum -- not as a corrupt download
  and not as a probe failure -- because the project holds no copy to fall back
  on.
* Local Percolator binary registration with a >= 3.05 check and capability
  probe.
* The Tool Manager UI showing installed, available, unavailable-on-this-
  platform and local tools with their capabilities and advisories.
* Tool artefact provenance records.
* Cross-process install locking.

Out of scope
------------

* Running a scientific workflow with the tools (phases 08, 09).
* Deciding D-002; it is decided, and this phase implements it.
* NSIS payload extraction, and macOS ``.pkg`` extraction for the Percolator
  *binary*. Deleted by ``D-002`` option C. Do not reinstate either without a
  new owner decision.

Deliverables
------------

* ``org.cometgui.install`` with registry, download, verify, archive and
  probe subpackages.
* ``manifests/tools.json`` (or equivalent) with the verified artefacts for
  every supported platform.
* Tool Manager UI section.
* ``docs/developer/tool_registry.rst``, ``docs/user/tool_manager.rst``,
  ``docs/platform_support.rst``.

Exit gate
---------

The phase orchestrator verifies every item, and the main orchestrator then
re-runs them to sign the phase off. Neither accepts a report in place of
running the check. An item that cannot be verified has not passed.

1. From an empty cache, the application installs Comet, an XML-capable
   Percolator where one exists for the platform, PDV and the converter, and
   probes each successfully -- driven through the Tool Manager UI, not from
   a test helper.
2. A corrupted download is rejected and the tool is never executed; the
   test asserts no process was launched.
3. Archive traversal, absolute-path, symlink and bomb attacks are each
   rejected by a specific test.
4. An interrupted install leaves no directory that reports itself
   installed.
5. A binary that cannot load on the host produces the ``R-PLAT-03``
   diagnostic naming the required and available versions, and the tool is
   not offered for selection.
6. A Comet install missing its Thermo companion DLLs does not advertise
   ``THERMO_RAW_WINDOWS``.
7. A local Percolator below 3.05 is rejected with a clear message; a valid
   local binary is registered, checksummed and probed.
8. The Tool Manager offers no version/platform combination absent from the
   manifest.
9. On macOS, a freshly installed managed tool executes without a Gatekeeper
   refusal.

Risks and notes
---------------

* ``D-002`` is decided, so the artefact strategy is fixed; what is *not* fixed
  is capability. **No Windows or macOS Percolator binary has ever been executed
  anywhere in this project.** Every non-Linux row in the specification's
  artefact table is a byte-marker inference. Build the manifest, the extractors
  and the probe against the Linux path, mark the other platforms' capability as
  probed-at-runtime, and never let the manifest assert a capability the project
  has not observed -- absent or unverified is honest; a fabricated claim is
  not.
* The capability probe must be **functional**, not textual: both Percolator
  twins print identical ``--help`` listing ``-X``. Run the binary over a
  synthetic PIN of at least 64 target and 64 decoy rows and inspect the file it
  writes. ``scripts/feasibility/probe_xml_capability.py`` is wrong for exactly
  this reason and must not be copied into the product.
* PDV is a ~99 MB download. Test the cancellation and restart path
  deliberately.

Handoff
-------

The **phase orchestrator** owns both records for this phase.

``handoffs/PHASE-05-worklog.rst`` is written as the phase runs: the work units,
their acceptance conditions, which agent did each, and the sign-off entry for
each -- what was run and what was observed.

``handoffs/PHASE-05-handoff.rst`` is written before finishing, whether the phase
passed, stalled or was abandoned: what was built and where; which gate items
pass and the evidence for each; what is incomplete and why; decisions
encountered; surprises a later phase must know about; and the first thing the
next agent should do.
