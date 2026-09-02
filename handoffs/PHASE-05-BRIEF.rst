================================================================
PHASE-05 brief -- tier 1 to the phase orchestrator
================================================================

:Phase: 05 -- Tool Registry and Installer
:Tier: 2 -- phase orchestrator (one fresh agent, one phase)
:Dispatched by: Main orchestrator, session 05
:Date: 2026-09-02
:Depends on: 01 (PARTIAL), 03 (PARTIAL), 04 (PARTIAL) -- all signed off
:Runs alongside: **nothing.** You are the only phase live in this tree.
:Expected grade: **PARTIAL** -- read :ref:`p05b-grade` before you plan anything.

.. note::

   Written by tier 1 and owned by tier 1. Do not edit it. Your records are
   ``handoffs/PHASE-05-worklog.rst`` and ``handoffs/PHASE-05-handoff.rst``.

This is the largest and riskiest phase the project has reached, and the first
one that touches the network. It is where "the scientist installs nothing by
hand" either becomes true or becomes a promise the product cannot keep.

Read first, in this order
=========================

#. ``ONBOARDING.rst`` -- all of it. Especially *Roles*, *Sign-off*, *Why phases
   run one at a time*, and *What parallel agents actually share*.
#. ``CONTRIBUTING.rst`` -- environment, commit, gate, documentation and handoff
   conventions.
#. ``phases/PHASE-05-tool-registry.rst`` -- your phase: scope, deliverables and
   **nine** exit-gate items. Read *Risks and notes* twice.
#. ``DECISIONS.rst`` -- ``D-002`` including **option C**, ``D-003``, ``D-004``
   and ``D-008``. All four are decided and all four bind you. You may not
   reopen any of them, and you may not answer any ``D-`` item yourself.
#. ``specification.rst`` -- only the sections your phase names:
   ``R-TOOL-01``..``09``, ``R-PLAT-02``..``05``, ``R-SEC-02``, ``R-SEC-05``,
   ``R-SEC-06``, and *Percolator versions and artefact availability*. It is
   160 KB; do not read it whole.
#. ``handoffs/PHASE-00-handoff.rst`` and ``docs/feasibility/`` -- the upstream
   facts were established there by execution, not by reading. In particular
   ``docs/feasibility/windows-artefact.rst``, whose central caveat is still
   live.
#. ``handoffs/PHASE-03-handoff.rst`` -- the process service is how you launch
   anything. Read *Surprises a later phase must know*.
#. ``handoffs/PHASE-04-handoff.rst`` -- hashing and provenance. You record tool
   artefact provenance; do not build a second way to hash or redact.
#. ``STATUS.rst`` -- the Phase 04 sign-off, *Risks currently live*, and
   :ref:`status-census-closed`.

What is already decided, and is not yours to revisit
====================================================

Four decisions shape this phase more than the specification text does.

* **``D-002`` option C.** Percolator's binary comes from the portable ``noxml``
  **zip** on every tier-1 platform. **You do not implement NSIS payload
  extraction, and you do not implement ``xar``/cpio extraction for the
  Percolator binary.** That work was deleted by an owner decision. ``DEB`` and
  ``PKG`` payload handling survives for **one** purpose only: fetching the two
  XSD companions that no portable archive ships. **Installers are never
  executed.**
* **``D-003``: three managed Percolator versions** -- 3.07.1 (default for
  Limelight runs), 3.09 (current, no Limelight) and 3.06.5 (reach). That set is
  what the project *attempts*; the artefact-plus-probe test decides what each
  platform actually gets. **Expect 3.09 on Linux to be difficult or absent.**
  Absent is honest. A fabricated manifest entry is not.
* **``D-004``:** the macOS Percolator payload is x86-64, so that stage runs
  under Rosetta 2 on Apple silicon. Detect it, verify before the stage runs,
  and explain if absent.
* **``D-008``:** managed binaries are **downloaded from upstream by pinned URL
  and SHA-256, never redistributed.** The project holds no copy to fall back
  on, so a vanished or re-tagged upstream artefact is an **availability**
  failure naming the URL and the expected checksum -- not a corrupt download,
  and not a probe failure.

The three things most likely to go wrong
========================================

**1. A capability probe that greps ``--help`` is invalid, and this is not
negotiable.** The XML and ``noxml`` Percolator twins print **identical** help
text, both listing ``-X`` and ``-Z``. ``R-PERC-02`` needs a **functional**
probe: run the binary over a synthetic PIN of at least **64 target and 64 decoy
rows** and inspect the file it writes. A smaller fixture makes a fully capable
binary abort on "median decoy score <= score at 1% FDR" and produces a false
negative. ``scripts/feasibility/probe_xml_capability.py`` is wrong for exactly
this reason -- it reports "NOT XML-capable" for a binary whose XML the Limelight
converter consumed -- and **must not be copied into the product.**

**2. The manifest must never assert a capability the project has not
observed.** *Updated 2026-09-02: no longer true of Windows.* A ``windows-latest``
runner has now executed Percolator 3.07.1's portable ``noxml`` binary, which
started and wrote XML; the XML installer payload failed to load. No macOS binary
has ever been executed, and no Windows binary has run on a clean end-user
machine, a standard-user account, consumer Windows or ARM. Every non-Linux row in the specification's artefact table is a
byte-marker inference. Build the manifest, the extraction and the probe against
the Linux path, mark other platforms' capability as **probed at runtime**, and
let the manifest say ``unverified-on-windows`` where that is the truth. The
words *verified*, *confirmed*, *proven* and *tested* are not used of a binary
nobody has run.

**3. A failure must name the right cause.** Two specific traps the feasibility
work already paid for:

* the Windows portable zip is the bare ``percolator.exe`` and needs a Visual
  C++ runtime it does not carry (``MSVCP140.dll``, ``VCRUNTIME140.dll``,
  ``VCRUNTIME140_1.dll``, ``VCOMP140.DLL``). Its absence is an ``R-PLAT-03``
  **loader** failure naming the DLL -- **never** "not XML-capable";
* the shipped ``percolator_out.xsd`` fixes ``majorVersion`` at ``2`` while the
  3.07.1 binary writes ``3``, so **the XSDs cannot serve unmodified as a
  validation gate**. They are a provenance and validation asset, not a runtime
  prerequisite -- Phase 00 proved by execution that XML output works without
  them. Record that distinction in the registry rather than leaving it implicit.

.. _p05b-grade:

Expected grade: PARTIAL. Document for the evidence
==================================================

Gate item 9 -- *"On macOS, a freshly installed managed tool executes without a
Gatekeeper refusal"* -- **cannot be executed in this environment.** There is no
macOS machine. Several other items carry Windows-divergent behaviour that has
never run anywhere.

The grading rule is in ``STATUS.rst`` under *Platform divergence, in two
tiers*: *"we could not run this code on that platform"* is a **testing gap** and
does not cap a grade; *"there is different code on that platform and it has
never run"* is **unverified behaviour** and does. Sort your divergences into
those two tiers honestly, as Phase 04 did, and expect ``PARTIAL``.

That is not a lower bar. **Every item that can be met here must be met here,
with evidence.** What it changes is what you write down. Never mark an item
passed with a caveat in prose -- ``ONBOARDING.rst`` forbids it.

Network access, and the limits on it
====================================

You may reach the network. It is the point of the phase. Constraints:

* **Every download is by pinned URL and pinned SHA-256**, verified **before any
  execution**, with MD5 recorded alongside. No unpinned fetch, ever.
* **Nothing is redistributed and nothing is committed.** Downloaded artefacts
  live in gitignored working directories. Never add a binary to the repository.
* **PDV is roughly 99 MB.** Test cancellation and restart deliberately, as the
  phase document requires -- not as an afterthought once it works.
* **Record provenance for every artefact you fetch** -- URL, version, date,
  SHA-256, licence -- as the project has done for its own toolchain.
* If upstream has moved or a checksum no longer matches, that is a **finding to
  report**, not a checksum to update. Never relax a checksum to make a download
  pass. Escalate it to me with the evidence.

Two build rules that will bite this phase specifically
======================================================

Both are new since the last phase and both are about *your* modules.

* **``org.cometgui.install.registry.*``, ``org.cometgui.install.verify.*``,
  ``org.cometgui.install.probe.*`` and ``org.cometgui.tools.*`` are
  mutation-critical packages** in ``pom.xml``. ``cometgui-install`` and
  ``cometgui-tools`` today hold **only ``package-info.java`` and zero tests.**
  The moment you land real classes there, ``scripts/build.sh`` fails the build
  unless that module's mutation gate is switched on. Switch it on deliberately
  and early; do not discover it at the end. **Turning a gate off to make a
  build pass is a rejection.**
* **The per-class coverage census now fails the build**
  (:ref:`status-census-closed`): every class compiled into ``target/classes``
  must appear in that module's ``jacoco.xml``. A class whose test does not
  compile does not score low -- it leaves the sample, and the build now stops.
  This is a help, not an obstacle: it is the check that catches the measurement
  error that re-running a gate cannot.

Files
=====

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - You own
     - Notes

   * - ``cometgui-install/**``, ``cometgui-tools/**``
     - Your modules. Registry, download, verify, archive, probe.

   * - The tool artefact manifest
     - ``manifests/tools.json`` or equivalent. No such directory exists yet.

   * - The Tool Manager section in ``cometgui-ui`` / ``cometgui-app``
     - Fit Phase 02's shell. Do not restructure navigation. **No scientific
       logic, hashing, download or parsing code in a JavaFX controller** --
       that is an architecture rule with a live ArchUnit test behind it.

   * - ``docs/developer/tool_registry.rst``, ``docs/user/tool_manager.rst``,
       ``docs/platform_support.rst``
     - Your documentation deliverables.

   * - ``handoffs/PHASE-05-worklog.rst``, ``handoffs/PHASE-05-handoff.rst``
     - Yours. The worklog is written as you go, not at the end.

.. list-table::
   :header-rows: 1
   :widths: 42 58

   * - Do not touch
     - Why

   * - ``STATUS.rst``, ``DECISIONS.rst``, ``phases/*.rst``, ``ONBOARDING.rst``,
       ``CLAUDE.md``, ``specification.rst``
     - Tier 1 owns them. Report upward.

   * - ``scripts/build.sh``, ``scripts/verify-test-gates.sh``,
       ``scripts/verify-all-gates.sh``
     - Tier 1's. Escalate anything you need there. You **may** add a new
       ``scripts/verify-install-gates.sh`` of your own; see below.

   * - ``cometgui-process/**``, ``cometgui-provenance/**``,
       ``cometgui-domain/**``
     - Phases 03 and 04, signed off. Launch processes **only** through the
       process service; hash **only** through the provenance hasher; redact
       **only** through ``org.cometgui.domain.secrets``. Do not build a second
       one of any of those. Two phases already built the same redaction rule
       set twice and the copies diverged within hours.

Your falsifiability harness
---------------------------

Phases 01, 02 and 04 each ship one under ``scripts/``, registered as a control
in ``scripts/verify-all-gates.sh``; Phase 03 does not, and that is recorded
debt. **Build ``scripts/verify-install-gates.sh``**, assembled from the
injections in your own work log rather than invented, and register it. It must
be seen to bite. **Never lower a floor** in ``verify-all-gates.sh`` -- a run
grading fewer controls than before is a failure even when green. Note the suite
already costs ~50 minutes; keep your controls proportionate and say what they
cost.

Hard rules
==========

* **Never weaken a gate, a checksum, a validation rule or a coverage
  threshold** to make something pass -- including the quiet forms: exclusions,
  disabled tests, loosened patterns, a switched-off mutation gate, a relaxed
  checksum.
* **Exit code 0 proves nothing.** Two tools in this project's own dependency
  chain exit 0 while doing nothing useful. Verify the output exists and is
  correct.
* **A test that asserts "did not throw" is not a test.** Prove the value.
* **An expected value computed by the code under test cannot fail.**
* Documentation is reStructuredText and must pass ``sphinx-build -n -W``. The
  gate is **global**: one short title underline fails the build for everybody.
  Run ``bash scripts/ci/docs-build.sh`` and **gate the commit on it**.
* Nothing installs on the host: no ``sudo``, no ``apt``, no host-level pip.
* **Commit by exact pathspec.** Never ``git add -A``.
* **Do not push and do not open a pull request.** Tier 1 holds both.
* **Do not answer a ``D-`` item.** Report it upward with a recommendation and
  the cost of each option.

Work units run serially
=======================

**Serial is the default.** Two agents at once needs a positive, recorded
argument that collision is *impossible*, written into the work log before they
start. "They touch different files" is **not** that argument and has been proven
false: agents also share the Maven working tree, ``_build/m2repo``, the
scratchpad root, formatter invocations, ``docs/_build/html``, the global docs
gate and the git index -- and two phase-local ``flock`` files do not serialise
against each other. Tier 1 committed this same collision again on 2026-09-02 and
paid for it in re-runs; it is easy to do and expensive to detect.

The injection protocol
======================

An injection is evidence only if it landed. Assert the anchor occurs **exactly
once**; confirm the **compiled ``.class``** changed, not only the ``.java``;
grep a marker back out; restore with ``git checkout --`` and confirm clean --
never trust your own backup, since running an inject script twice overwrites it
with the injected version; work in a **private scratchpad subdirectory**; read
**why** a build is red or green, never merely that it is; and **if a defect that
previously failed suddenly passes, suspect the injection before the gate**.

Two live cautions from tier 1's own last session: ``-Dtest=A+B`` is not a valid
separator and silently runs **zero** tests while exiting 0; and
``mvn -pl <module>`` without ``-Dsurefire.failIfNoSpecifiedTests=false`` aborts
in an **upstream** module, skipping the one you meant to test, which looks like
a failure of your gate.

Nine shapes of a check that cannot fail
=======================================

Expect a tenth.

#. A rule that evaluates nothing.
#. An expected value computed by the code under test.
#. A property proved through a seam production need not use.
#. An assertion too coarse to see a partial failure.
#. An input set too narrow to see it.
#. An injection that never landed.
#. **A real measurement over an incomplete population** -- the worst, because
   re-running reproduces the clean figure. Now checked on every build.
#. An injection that reached the source but not the compiled class.
#. **Evidence read without being dated.** ``build.sh --only gates`` grades
   whatever reports are on disk; a stale one can be green for code that has
   since broken. Recorded, not yet fixed -- do not use ``--only gates`` to
   prove anything.

Report back
===========

Report to me at these moments, not only at the end:

#. **After decomposition**, with the work units and their acceptance
   conditions, before you spawn anything.
#. **When the first real download and probe works end to end**, with the
   observed output -- that is the phase's central risk retired or not.
#. **At the end**, with each of the nine gate items carrying the command that
   produces its evidence and the evidence itself.

I will re-run every gate item myself and inject my own defects -- never your
negative controls. Report honestly rather than favourably: a unit reported as
working and signed off on that basis is the one failure mode this structure
cannot absorb.

Escalate upward, to me, never to the owner.
