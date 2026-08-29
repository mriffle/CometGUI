=====================================
PHASE-05: Tool Registry and Installer
=====================================

:Phase: 05
:Status: NOT STARTED
:Depends on: 01, 03, 04
:Blocked by decisions: D-002, D-003, D-004
:Delivers: R-TOOL-01..09, R-PLAT-02, R-PLAT-03, R-PLAT-04, R-PLAT-05, R-SEC-02, R-SEC-05, R-SEC-06
:Contributes to: R-PERC-01
:Proves: AC-INS-01..10

Purpose
-------
The zero-manual-install promise, and the place where the project's largest
technical risk lands. This phase must handle the artefact kinds that actually
exist -- bare executables with companion DLLs, JARs, portable zips, and
(depending on ``D-002``) operating-system package payloads -- and must never
present a tool as available when it cannot be obtained or cannot run.

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
* ``project-built`` as a first-class manifest artefact source, since the
  latest compatible Percolator has no upstream binary and must be built and
  published by the project (``D-002``); such artefacts carry their upstream
  licence, source tag and build provenance.
* Local Percolator binary registration with a >= 3.05 check and capability
  probe.
* The Tool Manager UI showing installed, available, unavailable-on-this-
  platform and local tools with their capabilities and advisories.
* Tool artefact provenance records.
* Cross-process install locking.

Out of scope
------------

* Running a scientific workflow with the tools (phases 08, 09).
* Deciding D-002; this phase implements whichever strategy the owner chose.

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

Every item is verified by the orchestrator, independently of the phase
agent's report. An item that cannot be verified has not passed.

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

* This phase is where ``D-002`` becomes concrete. If the decision is still
  open when the phase starts, build the manifest, the artefact-kind
  extractors and the probe against the Linux path and leave the other
  platforms' records absent -- absent is honest; a fabricated URL is not.
* PDV is a ~99 MB download. Test the cancellation and restart path
  deliberately.

Handoff
-------

Before finishing -- whether the phase passed, stalled or was abandoned --
write ``handoffs/PHASE-05-handoff.rst`` covering: what was built and where;
which gate items pass and the evidence for each; what is incomplete and why;
decisions encountered; surprises a later phase must know about; and the
first thing the next agent should do.
