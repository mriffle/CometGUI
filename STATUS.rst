==============
Project Status
==============

:Project: CometGUI -- Comet + Percolator desktop workflow
:Updated: 2026-09-01
:Updated by: Main orchestrator, session 05 (**Phase 03 signed off PARTIAL**;
   ``main`` published to the remote after GitHub push protection rejected the
   project's own seeded-secret decoy; **Phase 04 resumed, reworked once and
   signed off PARTIAL**)
:Current phase: 05 -- **IN PROGRESS**, dispatched 2026-09-02. Phase 04 is
   **PARTIAL**, signed off 2026-09-02 on tier 1's own re-run and seven of its
   own defect injections; Phase 03 **PARTIAL**, 2026-09-01. Phases 00 and 01
   remain PARTIAL, both awaiting a pull request. The per-class census debt is
   closed (:ref:`status-census-closed`).
:Overall: The repository, build and every quality gate exist and have each been
   seen to fail on a deliberate defect. Three phases are signed off -- 02
   PASSED, 03 PARTIAL, 00 and 01 PARTIAL -- and 04 is in progress. What holds
   Phases 00 and 01 at PARTIAL has narrowed twice: ``D-008`` supplied a remote
   on 2026-08-30, session 04 pushed, and session 05 published ``main`` in full.
   **What is left is one pull request**, which is the trigger both of their
   outstanding items depend on and which nobody has opened. The Windows
   verification harness is written, falsifiable and locally verified, and
   GitHub has now executed exactly one workflow -- a scheduled nightly that
   failed on a Phase-15 stub, by design. See :ref:`status-residue-01`,
   :ref:`status-p02` and :ref:`status-session-05`.

This file is the **only** authoritative record of where the project is. Update
it at every gate, every decision and every milestone. If it disagrees with
anything else, fix it here first.

Where we are
============

Phase 00 ran and was signed off **PARTIAL** on 2026-08-29. The scientific path
is proven end to end, the toolchain is installed and packaging works, and the
upstream facts were re-verified live -- one of them changed *during* the phase.
The residue is small and precisely named (see :ref:`status-p00`).

On 2026-08-29 the owner then took the two decisions Phase 00 had costed and
left open, and both change what later phases build. **``D-002`` option C:** the
product installs Percolator from the portable ``noxml`` archives, so Phase 05
never writes the NSIS or ``xar``/cpio payload extractors -- the most fragile
code the installer was going to contain is now unwritten rather than written.
**``D-008``, second half:** managed tool binaries are downloaded from upstream
by pinned URL and SHA-256, never redistributed. Specification revision 7 and
``phases/PHASE-05-tool-registry.rst`` carry the consequences.

**Phase 01 ran and was signed off ``PARTIAL`` on 2026-08-29** (see
:ref:`status-p01`). Eight work units, each signed off by the phase orchestrator
after it re-ran the checks itself. Five of six gate items pass on the main
orchestrator's own re-run; item 6's "on a pull request" half is unmet because
there is no remote, exactly as the owner directed before the phase started.

What the project now has that it did not: a twelve-module Maven build that a
clean clone takes from nothing to green with one command, the full GPLv3
``LICENSE``, a Sphinx tree that fails on a broken cross-reference, ArchUnit
layering rules, JaCoCo and PIT gates at the specification's own thresholds, a
traceability report that fails the documentation build, three CI pipelines, an
SBOM and a dependency scanner with a working canary. **Every one of those gates
has been seen to fail on a deliberate defect**, most of them injected by the
main orchestrator rather than by the phase.

What exists
-----------

.. list-table::
   :header-rows: 1
   :widths: 30 14 56

   * - Artefact
     - State
     - Notes
   * - ``specification.rst``
     - Revision 9
     - Revision 9 records ``D-003`` (three managed Percolator versions,
       ``R-PERC-12``); revision 8 recorded ``D-005`` (drive PDV through a
       generated mzTab, ``R-PDV-02``..``R-PDV-05``, ``AC-VIS-04``/``05``);
       revision 7 acted on the ``noxml`` discovery. Passes
       ``sphinx-build -n -W``.
   * - ``ONBOARDING.rst``
     - Complete
     - Read-first document for any orchestrating agent.
   * - ``phases/`` (00-16)
     - Complete
     - Scope, deliverables and exit gate per phase. 00 and 01 signed off
       PARTIAL, 05 re-scoped by ``D-002`` option C.
   * - ``DECISIONS.rst``
     - 0 open, 7 decided, 2 partial/provisional
     - ``D-001``, ``D-002`` (including option C), ``D-004`` and ``D-008``
       (all three parts), ``D-003``, ``D-005`` and ``D-007`` decided;
       ``D-009`` provisional and ``D-006`` partly decided. **No ``D-`` item is
       open.**
   * - ``handoffs/``
     - Phase 00 present
     - ``PHASE-00-worklog.rst`` (10 units, each with a sign-off entry) and
       ``PHASE-00-handoff.rst``.
   * - ``docs/feasibility/``
     - 10 documents
     - The phase's evidence. Builds clean under ``sphinx-build -n -W``
       (13 HTML pages) via ``scripts/feasibility/check-docs.sh``.
   * - ``scripts/feasibility/``
     - Re-runnable
     - Every claim above is reproducible from these scripts; the main
       orchestrator re-ran them at sign-off rather than reading the reports.
   * - ``tools/``
     - Installed, gitignored
     - Liberica JDK 25.0.4.1+1, Maven 3.9.16, OpenJFX Monocle 21.0.2, plus an
       X11 and font stack. Provenance (URL, SHA-256, licence) is recorded in
       the committed ``docs/feasibility/toolchain.rst``; rebuild with
       ``scripts/feasibility/install-toolchain.sh``.
   * - Product code
     - Skeleton only
     - Twelve Maven modules matching the specification's package structure,
       61 Java files, 54 tests. The only real logic is ``BuildIdentity`` and
       two headless JavaFX probes -- deliberately, so the coverage, mutation
       and architecture gates measure something rather than an empty reactor.
       Nine of twelve modules hold only ``package-info.java``; the build
       prints them as ``inert`` rather than letting an unevaluated rule read
       as a passing one.
   * - ``scripts/`` (product)
     - Re-runnable
     - ``build.sh`` (11 stages, the one documented command),
       ``verify-all-gates.sh`` (10 controls, 176 graded checks) and five gate
       harnesses. ``verify-all-gates.sh`` belongs in the nightly pipeline:
       it exists because a gate that is never run stopped working during
       this very phase.

Phase board
===========

.. list-table::
   :header-rows: 1
   :widths: 8 40 16 36

   * - Phase
     - Title
     - Status
     - Gate evidence
   * - 00
     - Feasibility, legal and upstream verification
     - PARTIAL
     - Signed off 2026-08-29. 9 of 10 gate items PASS on the main
       orchestrator's own re-run; item 8 passes only on its second branch.
       See :ref:`status-p00`.
   * - 01
     - Repository, build and quality skeleton
     - PARTIAL
     - Signed off 2026-08-29. Items 1-5 PASS on the main orchestrator's own
       re-run and its own defect injections; item 6 is half met -- no remote
       exists, so no pipeline has run on a pull request. See
       :ref:`status-p01`.
   * - 02
     - Application shell and navigation
     - PASSED
     - Signed off 2026-08-31. All five items PASS on the main orchestrator's
       own re-run and its own defect injections; item 2 only after the
       identifier-pinning repair sign-off required. See :ref:`status-p02`.
   * - 03
     - Process service
     - PARTIAL
     - Signed off 2026-09-01. **All six gate items PASS on the main
       orchestrator's own re-run and its own defect injections**, none of them
       the phase's negative controls. PARTIAL, not PASSED, because gate item 2
       carries no platform qualifier and descendant termination has never
       executed on Windows or macOS. See :ref:`status-p03`.
   * - 04
     - Hashing and provenance core
     - PARTIAL
     - Signed off 2026-09-02. **All seven gate items PASS on the main
       orchestrator's own re-run and its own seven defect injections**, none of
       them the phase's negative controls; item 6 only after a returned rework
       that closed a third blind spot in the secret corpus
       (:ref:`status-p04-occurrence`). PARTIAL, not PASSED, because gate items
       3, 4 and 5 rest on Windows branches that have never executed anywhere.
       See :ref:`status-p04`. *Historically: paused by the owner on 2026-08-31,
       part-built, to stop two phases sharing one tree.* Units **1-8 signed off** on the orchestrator's own
       injections; units **9 and 10 LANDED BUT NOT SIGNED OFF** -- committed
       green, but their diffs were not read, their gates not re-run and nothing
       injected into them, which the phase stated plainly rather than letting
       them read as done; units **11-13 not started**. Gate items 1-5 met on
       POSIX with evidence; item 6 partial (unit 11 missing); item 7 met for
       hashing and redaction but unverified module-wide. Resumes with a fresh
       orchestrator **after Phase 03 completes**, from
       ``handoffs/PHASE-04-handoff.rst`` (823 lines) and
       ``PHASE-04-worklog.rst`` (1060 lines). **Every headline number was taken
       from a tree another phase was changing and must be re-taken from a quiet
       one.** Expected grade on resumption is **PARTIAL**, not PASSED
       (:ref:`status-platform-divergence`).
   * - 05
     - Tool registry and installer
     - IN PROGRESS
     - **Dispatched 2026-09-02** with a fresh phase orchestrator, briefed by
       ``handoffs/PHASE-05-BRIEF.rst`` and told its expected grade is PARTIAL:
       gate item 9 is macOS-only and cannot be executed here. The first phase
       to reach the network. Dependencies 01, 03 and 04 are all signed off and
       no decision blocks it; the owner approved the network access on
       2026-09-02.
   * - 06
     - Comet parameter model
     - NOT STARTED
     - --
   * - 07
     - Comet parameter editor UI
     - NOT STARTED
     - --
   * - 08
     - Workflow engine and Comet adapter
     - NOT STARTED
     - --
   * - 09
     - Percolator adapter and version capabilities
     - NOT STARTED
     - --
   * - 10
     - Results model and UI
     - NOT STARTED
     - --
   * - 11
     - PDV integration
     - NOT STARTED
     - --
   * - 12
     - Limelight conversion and upload
     - NOT STARTED
     - --
   * - 13
     - Provenance UI and reports
     - NOT STARTED
     - --
   * - 14
     - GUI automation and packaged end-to-end harness
     - NOT STARTED
     - --
   * - 15
     - Version matrix, performance and hardening
     - NOT STARTED
     - --
   * - 16
     - Documentation and release qualification
     - NOT STARTED
     - --

Status values are ``NOT STARTED``, ``IN PROGRESS``, ``PARTIAL`` (with the
residue named), ``BLOCKED`` (with the decision or dependency named), or
``PASSED`` (with the date and the evidence the orchestrator verified).


.. _status-p00:

Phase 00 sign-off (2026-08-29)
==============================

:Outcome: **PARTIAL**
:Signed off by: Main orchestrator, by re-running every gate item itself
:Phase records: ``handoffs/PHASE-00-worklog.rst``, ``handoffs/PHASE-00-handoff.rst``

The phase orchestrator reported PARTIAL. The main orchestrator re-ran all ten
items rather than accepting that report; the table below records what the
re-run actually printed.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Result
     - What was run, and what it printed

   * - 1
     - PASS
     - ``.venv/bin/python scripts/feasibility/verify_upstream_facts.py
       --refresh`` -> ``CHANGED=1, CONFIRMED=17, UNVERIFIED=4``. The four
       unverified rows are artefact rows, covered by items 6-9. The one
       ``CHANGED`` row is CasanovoGUI's licence -- the script detecting a real
       drift, which is it working. Four differences from the specification are
       listed and are now recorded in specification revision 5.

   * - 2
     - PASS
     - ``bash scripts/feasibility/run_scientific_path.sh`` -> exit 0, and the
       output was checked rather than the exit code: a 12 797 113-byte
       Limelight XML with 3897 ``<psm>`` and 2985 ``<reported_peptide>``
       elements. The main orchestrator validated it with its **own**
       ``javax.xml.validation`` program (``warnings=0 errors=0 fatals=0,
       VERDICT: VALID``) and proved that validator falsifiable: a truncated
       copy gives ``fatals=1`` and a copy with one bogus attribute gives
       ``cvc-complex-type.3.2.2 ... 'bogusAttr' is not allowed``.

   * - 3
     - PASS
     - Same run, stage 11. Percolator 3.09 produced 3898-line PSM, 2986-line
       peptide and 12-line weights files, and ``find ... -name '*.xml' | wc
       -l`` -> ``0``. Its ``--help`` carries no XML flag at all.

   * - 4
     - PASS
     - ``env -i PATH=/usr/bin:/bin HOME=/tmp
       _build/jpackage-spike/dest/ToolchainProbe/bin/ToolchainProbe`` ->
       ``java.version = 25.0.4.1``, ``self contained = true``, ``javafx
       modules = all present``, ``PROBE RESULT = PASS`` with no JDK on PATH.

   * - 5
     - PASS
     - ``bash scripts/feasibility/javafx-smoke.sh`` -> 7/7 stages. TestFX
       4.0.18 works headless. Two stages are deliberate negative controls and
       both failed as required, so the harness is falsifiable rather than
       merely green.

   * - 6
     - PASS
     - ``sed -n '44,84p' docs/feasibility/percolator-artefacts.rst`` -> a
       per-platform table giving artefact, container, extraction-without-admin
       and host requirement. Linux and macOS cells say "Verified here"; the
       Windows cell says "Not established" in both columns.

   * - 7
     - PASS
     - ``python3 scripts/feasibility/enumerate_percolator_releases.py`` ->
       ``latest compatible = 3.7.1 (rel-3-07-01)`` under both a strict and an
       optimistic rule. Because that script reads a cache by default, the main
       orchestrator additionally queried the GitHub API directly at sign-off
       and confirmed the inputs live.

   * - 8
     - PASS -- second branch only
     - ``bash scripts/feasibility/windows-artefact.sh`` -> the NSIS payload
       extracts with no elevation, yielding a PE32+ x86-64 ``percolator.exe``
       importing 92 ``xercesc_3_1`` symbols plus both XSDs. **The binary was
       not executed on Windows, and no Windows runner exists here.** The gate
       item's second branch requires the blocking reason to be documented and
       the manifest not to claim the capability: both hold. The manifest value
       is ``xml_capability: unverified-on-windows`` and a grep of every
       committed document for *verified*, *confirmed*, *proven* or *tested*
       near a Windows XML claim returns nothing. New finding: both NSIS
       installers request ``requireAdministrator``, so extraction -- not
       installation -- is the only admin-free route.

   * - 9
     - PASS
     - ``extract_deb.py``, ``extract_pkg.py`` and the NSIS extractor were each
       run by the main orchestrator into a fresh directory. Each yielded its
       platform's binary plus ``percolator_in.xsd`` and
       ``percolator_out.xsd``. The freshly extracted Linux binary was then run
       (``-X`` -> 200 ``<psm>`` elements, exit 0). The NSIS extractor is
       cross-checked: its ``percolator.exe`` is byte-identical to the copy
       taken from the portable ZIP by unrelated code. Only the Linux binary
       was executed.

   * - 10
     - PASS
     - ``handoffs/PHASE-00-handoff.rst`` carries a written, evidenced
       recommendation for ``D-001`` and confirms ``D-002``'s outcome from
       recomputed data while escalating evidence that contradicts its stated
       rationale. Its ``D-001`` evidence was overtaken during sign-off -- see
       below.

Why PARTIAL rather than PASSED
------------------------------

#. **The Windows binary has never been executed, anywhere.** Gate item 8's
   first branch is unmet. This is honest inference, not a verified fact, and
   the manifest says so.
#. **No macOS execution.** The ``.pkg`` payload was extracted and inspected;
   Rosetta 2 (``D-004``) is untested because there is no Mac here.
#. **The ``noxml`` finding is recorded but not acted on.** Acting on it
   re-scopes Phase 05 and is ``D-002`` option C -- an owner decision.

What the main orchestrator caught that the phase did not
--------------------------------------------------------

**``Noble-Lab/CasanovoGUI`` published GPL-3.0 at 2026-08-29T01:56:35Z, while
Phase 00 was running.** The work unit checked before that commit and correctly
recorded ``license = null``; the sign-off re-check about an hour later found
the licence present, verified three ways (repository API, licence API blob sha
``f288702d...``, and the commit list). Both observations were right when made.
This is precisely the drift the phase exists to catch, and it is the reason
sign-off re-runs checks instead of reading reports. ``D-001`` is rewritten
accordingly.

.. _status-p01:

Phase 01 sign-off (2026-08-29)
==============================

:Outcome: **PARTIAL**
:Signed off by: Main orchestrator, by re-running every gate item itself
:Phase records: ``handoffs/PHASE-01-worklog.rst``, ``handoffs/PHASE-01-handoff.rst``

The phase orchestrator reported PARTIAL. The main orchestrator re-ran all six
items rather than accepting that report, and **injected its own defects rather
than re-running the phase's negative controls** -- a harness that only fails on
the defect its author chose proves less than one that fails on a defect it has
never seen. The table records what the re-run actually printed.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Result
     - What was run, and what it printed

   * - 1
     - PASS
     - ``git clone /workspace`` into a scratch directory -- verified to contain
       **no** ``tools/`` and **no** ``.venv`` -- then ``bash scripts/build.sh``:
       ``11/11 stages OK in 151 seconds. BUILD OK``, ``6 report file(s):
       tests=54 failures=0 errors=0 skipped=0``. It bootstrapped its own JDK,
       Maven and font stack under its own ``tools/``. ``~/.m2`` did not exist
       before and does not exist after. This also independently disproves any
       missing-source worry from the ``.gitignore`` trap below: a clone that
       builds cannot be missing files.

   * - 2
     - PASS
     - ``bash scripts/ci/docs-build.sh`` -> ``PASSED``, two strict builds.
       Falsified with my own defect: appending
       ``:ref:`a-label-the-main-orchestrator-invented-that-does-not-exist``` to
       ``docs/installation.rst`` gives exit 1, ``WARNING: undefined label``,
       ``warnings treated as errors``; restoring the file returns it to
       ``PASSED``.

   * - 3
     - PASS
     - My own violation -- a ``javafx.beans.property.SimpleStringProperty``
       field in ``org.cometgui.domain`` -- produced ``Architecture Violation
       ... was violated (3 times)`` naming the field, the constructor, the
       method and the line, and quoting the specification rationale in the rule
       text. Deleting it returns ``Tests run: 13, Failures: 0``. I then
       attacked the rule's own weak point: removing ``cometgui-app`` from the
       archtests dependencies left **the layering rules passing 8/8 while
       checking nothing**, and ``ClassImportCensusTest`` failed with *"no
       classes were imported from org.cometgui.app; that module is missing from
       the archtests class path and its rules check nothing"*. The vacuous pass
       is defended, and the defence works.

   * - 4
     - PASS
     - My own untested branchy class in ``org.cometgui.domain.build`` ->
       ``Rule violated for bundle cometgui-domain: lines covered ratio is 0.61,
       but expected minimum is 0.90``, and PIT fell to a mutation score of 5.
       Thresholds were checked against the specification rather than assumed:
       ``0.90`` line / ``0.85`` branch core, ``0.80`` line view-model,
       ``mutationThreshold 80``, and ``failWhenNoMutations`` left at its
       default -- none weakened. On a clean tree ``bash
       scripts/verify-test-gates.sh`` exits 0 with every control PASS,
       including two that check the harness itself refuses to grade a control
       whose defect never reached the sandbox.

   * - 5
     - PASS
     - Three defects of my own, each caught with its own diagnostic code:
       removing ``AC-DOC-02``'s evidence entirely -> ``[MAP-MISSING-ID]``;
       pointing it at a JUnit class that does not exist ->
       ``[TEST-MISSING]``; removing ``R-DOC-05`` from Phase 01's
       ``:Delivers:`` -> ``[R-UNOWNED] R-DOC-05: no implementing phase``. Each
       also fails the **documentation build**, which is what ``R-DOC-03``
       actually requires: ``sphinx.errors.ExtensionError: traceability: the
       traceability report is not complete``. ``bash
       scripts/ci/traceability.sh --self-test`` exits 0 over 8 injected
       defects and 48 unit tests.

   * - 6
     - **PARTIAL -- second half only**
     - ``bash scripts/ci/run-pipeline-locally.sh`` -> ``42 step(s) across 3
       workflow(s); 37 executed on this machine; 0 unexpected``, exit 0, and it
       prints its own limitation: ``Still unmet: 'on a pull request'. No remote
       exists (D-008)``. ``git remote -v`` is empty; none was created. Stub
       steps exit **70** naming their owning phase, so a later pipeline cannot
       read as green while doing nothing. I confirmed the checker parses the
       workflow files rather than a hardcoded list by adding a step naming a
       script that does not exist: ``check-workflows: FAILED ... names
       scripts/ci/this-script-does-not-exist.sh, which does not exist``.
       **GitHub has never executed these files, and cannot until ``D-008``
       says where CometGUI is published.**

Also verified at sign-off, beyond the gate
-------------------------------------------

* **The ``LICENSE`` is the real thing.** 674 lines, 35 149 bytes, sha256
  ``3972dc9744f6...``, and ``diff`` against the canonical
  ``https://www.gnu.org/licenses/gpl-3.0.txt`` reports **no differences**. The
  byte count also matches CasanovoGUI's own ``LICENSE`` blob. ``D-001``'s first
  obligation is met exactly, with no paraphrase.
* **The dependency scanner gives a real answer.** Its canary -- a known
  vulnerable ``log4j-core 2.14.1`` sent with every batch -- came back with 7
  advisories, so the endpoint demonstrably can find a vulnerability when there
  is one. A scanner that reports "no vulnerabilities" because it is broken is
  the failure mode, and it is defended.
* **The SBOM describes the real project**: 26 purls, XML and JSON agreeing,
  every build plugin carrying an exact pinned version.
* **The PIT target list covers every package the specification names as
  critical logic** -- q-value filters, command builders, capability rules,
  checksum and provenance code, stage invalidation -- not merely the packages
  that have code today.

What the main orchestrator caught that the phase did not
--------------------------------------------------------

**Handoff surprise 6 is wrong.** It states that "a JavaFX layering violation in
the domain does not even compile in this build", concluding the ArchUnit rule is
a second line of defence. It is not: ``javafx.scene.control.Label`` -- the very
class the handoff names -- compiles cleanly in ``cometgui-domain`` (``mvn
compile`` exit 0), because the Liberica Full JDK carries JavaFX in the JDK
itself. My ``SimpleStringProperty`` injection compiled too, and ArchUnit is what
caught both. The rule is the **first** line of defence and is genuinely
load-bearing. Left uncorrected, this would have told a later phase that the rule
is hard to exercise, which is the opposite of true.

Why PARTIAL rather than PASSED
------------------------------

One reason only: **no pipeline has ever run on a pull request, because there is
no git remote.** Everything else in the gate passes. This is the same blocker
that held Phase 00's item 8 open.

*Resolved the next day.* ``D-008`` was decided on 2026-08-30 and the remote
exists. This sign-off stands as recorded -- the item was genuinely unmet when
it was signed -- but it is now closable by running the pipeline on a pull
request, with no further decision needed.

What the phase found in its own work, and reported
---------------------------------------------------

Recorded because it is the behaviour this structure depends on. The phase
orchestrator discovered that **its own integration commit** (``f71ceb4``) broke
the traceability self-test: the self-test's sandbox copied ``scripts/ci`` and
``scripts/traceability`` but not ``scripts/``, so once ``AC-TST-02..04`` pointed
at ``scripts/verify-test-gates.sh`` the harness exited 4 -- and **nothing in the
build ran the self-test, so gate item 5's falsifiability silently stopped being
demonstrable while every other check stayed green.** It reported this rather
than quietly repairing it. I confirmed the repair (``_COPY_TREES`` now copies
``scripts`` whole) and that ``--self-test`` exits 0 today. The lesson is
generalised in ``scripts/verify-all-gates.sh``, which belongs in the nightly
pipeline: *a gate that is never run stops working without anyone noticing.*

Two other traps worth carrying forward: ``.gitignore``'s unanchored ``tools/``
also matched ``org/cometgui/tools/`` and silently dropped eight source files
from ``git add`` (now ``/tools/``); and **Spotless cannot check licence headers
on ``package-info.java`` at all** -- 53 files, 87% of the tree -- so Checkstyle
carries that obligation and ``mvn spotless:apply`` will not add a header to a
new ``package-info.java``.

.. _status-residue-01:

Phase 00 item 8 and Phase 01 item 6 -- preparation signed off (2026-08-30)
==========================================================================

:Outcome: **PREPARED, NEITHER ITEM CLOSED**
:Signed off by: Main orchestrator, session 03, by re-running the work itself
   and injecting its own defects
:Phase records: ``handoffs/PHASE-00-01-residue-worklog.rst``,
   ``handoffs/PHASE-00-01-residue-handoff.rst``
:Branch: ``windows-percolator-verification``, 13 commits, based on ``82609f0``

Both items were held open only by the absence of a remote. A remote exists, but
**this session has no push credential** -- no ``gh``, no token, no SSH key, no
credential helper; ``git push`` fails with ``could not read Username``. So the
work was prepared and verified locally and **neither gate item is closed**.
Closing them needs the owner to push and GitHub to execute something.

What is now true: the harness exists, is falsifiable, and refuses to report a
pass it has not earned. What is still not true: no Windows binary has been
executed, and GitHub has run no workflow in this repository at all -- the
Actions API reported ``total_count = 0`` on 2026-08-30.

.. list-table:: What the main orchestrator ran at sign-off
   :header-rows: 1
   :widths: 30 70

   * - Check
     - What was run, and what it printed

   * - The harness cannot go green while doing nothing
     - **My own defect, not one its author chose.** This project's recurring
       trap is a tool that exits 0 having done nothing, so I forced the Windows
       branch and replaced every Percolator launch with a process that exits 0,
       prints nothing and writes nothing, leaving download and extraction
       genuinely working. Verdict ``INCONCLUSIVE``, exit 2 -- never ``PASS``.
       The discriminating step reasoned correctly: *"the diagnostic is absent,
       but so is the 'Percolator version' banner in the 0 bytes captured. There
       is no positive proof the binary started, so the absence establishes
       nothing."* That is the dangerous false positive in this checklist --
       reading a missing ``XML_SUPPORT was off`` as proof of capability -- and
       it is closed.

   * - The workflow gate discovers files rather than listing names
     - I added a workflow the gate had never seen, carrying two defects of my
       own. It reported ``5 workflow file(s) discovered`` and failed both:
       ``uses 'actions/checkout@v5', which is not OWNER/REPO pinned to a full
       40-character commit SHA`` and ``names scripts/ci/mo-not-a-real-script.sh,
       which does not exist``. Removing my file returned ``PASSED`` with ``4
       workflow file(s) discovered``. It also refused my YAML flow-collection
       syntax rather than guessing at it.

   * - The path-traversal finding is real, and the fix generalises
     - Five traversal cases of my own -- none in the author's table -- all
       contained: two-space ``..``, dot-space-dot, a lowercase ``nul`` device,
       a space-dot-space shadow, and a plain four-level climb. Against the old
       extractor I confirmed the escape was genuine: its filter matched only
       exact ``".."``, so ``.. `` passed through, and Win32 strips the trailing
       space. The case that matters most here is ``percolator.exe `` resolving
       to ``percolator.exe`` -- silently replacing the very file whose SHA-256
       the checklist pins.

   * - One PIN generator, not two that can drift
     - ``windows-artefact.sh`` and the Windows driver both call
       ``make_synthetic_pin.py``. Generated twice: byte-identical, 32 466 bytes,
       sha256 ``6643ede4...``, matching the value pinned in both files.

   * - The aggregate gates still hold
     - ``bash scripts/build.sh`` -> ``11/11 stages OK in 88 seconds. BUILD OK``.
       ``bash scripts/verify-all-gates.sh`` -> ``9 control(s) passed, 0 failed,
       in 302 seconds``, ``Every gate was seen to reject its defect and accept
       the clean tree.`` ``run-pipeline-locally.sh`` -> ``45 step(s) across 4
       workflow(s); 37 executed on this machine; 0 unexpected``.

   * - Nothing was weakened to make this pass
     - ``docs/feasibility/windows-artefact.rst`` still carries
       ``xml_capability: unverified-on-windows``, still carries the sentence
       "The binary was not executed on Windows." (4 occurrences), and its
       central warning is unchanged. Its edits move in the honest direction:
       the claim that the extractor "runs on Windows unchanged" is **removed**
       and replaced with "it has still never been executed on Windows".
       ``run-pipeline-locally.sh`` still prints ``Still unmet: 'on a pull
       request'`` rather than quietly claiming item 6.

Gate item 8 amended, and strictly strengthened
-----------------------------------------------

Phase 00's work unit escalated ``E1``: gate item 8's own test, "``-X``
present", is satisfied by the ``noxml`` build it is meant to exclude -- both
twins accept ``-X`` and both write a ``percolator_out`` XML, executed on Linux
and reproduced on 2026-08-30. A gate an excluded build passes is not a gate.
Two further problems: an *absent* diagnostic proves nothing when the binary may
never have started, and ``D-002`` option C means the item interrogated an
artefact the product no longer ships.

The item now requires the binary to be **observed to start** (its own version
banner, not an exit code), requires ``--xml-in`` **not** to answer ``Compiler
flag XML_SUPPORT was off``, and requires the same observations of the portable
``noxml`` binary the product actually installs. **Every original requirement is
retained and each addition narrows what may count as a pass**; the
blocking-reason branch is untouched, and remains the branch the item rests on.

``E2`` is closed as an escalation -- the owner went further than it recommended
when taking ``D-002`` option C. One half of it survives as work, is cheap, and
is recorded below.

What the owner must do, and what nobody can yet know
-----------------------------------------------------

The push sequence is in :ref:`status-next-action`. Until a runner executes it,
these stay unknown: whether ``percolator.exe`` starts on Windows at all, its
real ``--help`` text, whether ``-X`` writes XML there, and whether ``--xml-in``
prints the diagnostic. Never answerable by this job at all: standard
(non-administrator) users, consumer Windows 10/11, Windows on ARM, and macOS.

A red Windows job is not automatically a defect. The driver exits 0 for PASS,
1 for a NEGATIVE finding about the binary, 2 for INCONCLUSIVE and 3 for a
harness failure, and the transcript names the value it observed in every case.

.. _status-p02:

Phase 02 sign-off (2026-08-31)
==============================

:Outcome: **PASSED**
:Signed off by: Main orchestrator, session 03, by re-running every gate item
   itself and injecting defects the phase had never seen
:Phase records: ``handoffs/PHASE-02-worklog.rst`` (twelve units),
   ``handoffs/PHASE-02-handoff.rst``

The phase orchestrator reported ``PASSED`` over eleven work units. The main
orchestrator re-ran all five gate items rather than accepting that report, and
**injected its own defects rather than re-running the phase's negative
controls**. Four items held. The fifth exposed a real gap, which was repaired
as a twelfth unit and then re-verified here; that gap is the most useful thing
this phase produced and is recorded in full below.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 10 85

   * - #
     - Result
     - The defect I injected, and what it printed

   * - 1
     - PASS
     - Made the arrow keys **skip** the Percolator section while leaving the
       mouse untouched -- a section genuinely unreachable by keyboard, which is
       the half of this item most likely to rot.
       ``everySectionIsReachableByKeyboardAlone`` failed on **both** drivers:
       ``#section-percolator showing, with percolator chosen ==> expected:
       <true> but was: <false>``. An earlier injection of mine --
       ``setFocusTraversable(false)`` -- correctly did **not** fail, because
       JavaFX honours an explicit ``requestFocus()`` regardless; that was my
       error, not a hole.

   * - 2
     - PASS
     - **after repair.** See :ref:`status-p02-identifier-gap`. As first delivered this
       item passed while proving less than it appeared to.

   * - 3
     - PASS
     - JavaFX as a **method-body local only** -- no field, no parameter, no
       return type -- in ``org.cometgui.domain.log.LogMessage``. It compiled
       (``mvn compile`` exit 0), confirming again that the Liberica Full JDK
       carries JavaFX and **ArchUnit is the only defence**. ArchUnit failed
       naming both call sites and their line numbers, and quoting the
       specification rationale in the rule text.

   * - 4
     - PASS
     - Added a real, visible ``Button`` to the header with a stable id and no
       accessible name: ``1 of 92 controls have none: Button with id
       #mo-unnamed-button under #shell-header``. The count moved 91 -> 92, so
       the enumeration is dynamic and catches **additions** rather than
       checking a fixed list.

   * - 5
     - PASS
     - The best of them. I retained every discarded message in a side list, so
       ``size()`` and ``discardedCount()`` stayed **exactly correct** -- a
       count-only test passes this defect. It failed on **retained heap**:
       ``retained heap grew by 222050704 bytes across 1000000 appends, which is
       over the documented bound of 33554432 bytes``. The gate measures memory,
       which is what the item actually requires.

.. _status-p02-identifier-gap:

What sign-off caught that the phase did not
--------------------------------------------

**The stable identifiers were not pinned, and the whole build went green while
one changed.** I renamed a section's identifier in *production* code --
``UiIds.sectionPane`` returning ``section-results-pane`` instead of
``section-results`` for ``RESULTS`` alone, a valid, stable, non-null id -- and
observed: ``SectionNavigationUiTest`` 4/0, ``KeyboardOnlyNavigationUiTest``
4/0, ``UiIdsTest`` 5/0, and ``bash scripts/build.sh`` -> ``11/11 stages OK.
BUILD OK``.

Two causes, both present. The GUI tests computed their expected identifier by
calling ``UiIds.sectionPane(section)`` -- **the same helper the production code
calls** -- so the assertion was self-referential and could not fail. And
``UiIdsTest`` pinned literal strings only as a *sample*: two of the ten
sections, neither of them ``RESULTS``.

This matters because Phase 02 delivers ``R-TEST-04``, whose entire content is
that identifiers are **stable**. A self-consistent test proves the control
exists and that navigation works; it proves nothing about stability, which is
precisely what Phase 07 and the Phase 14 GUI suite will depend on. Gate item 2
*as literally worded* passed, so this was returned as a repair unit rather than
a rejection.

**The repair** (unit 12) pins all 119 identifiers as hand-typed literals in a
new ``StableIdentifierPinTest``, with every derived form spelled out in full
rather than composed. ``git show --numstat`` is ``9/0``, ``688/0``, ``10/0`` --
**zero deleted lines**, so nothing was removed to accommodate it, and
``assertEquals(UiIds.`` appears **0** times, so the helper never sits on the
expected side.

**Re-verified with my own injections, not the phase's.** A derived form nobody
had tested -- the description suffix ``-description`` -> ``-desc`` -- failed
naming ``R-TEST-04`` and the phases that depend on it. More important, the
structural claim: I added a **new** enum constant ``MO_DIAGNOSTICS`` to
``SectionId`` and it failed with *"SECTION_PANE pins no identifier for:
MO_DIAGNOSTICS. A pinning table a new constant can bypass rebuilds the hole
this class exists to close."* The hole cannot be silently reopened one section
later.

The general lesson, which is why this is recorded at length: **an assertion
whose expected value is computed by the code under test cannot fail.** It
survived a phase of unit sign-offs -- including the phase orchestrator's own,
which were otherwise rigorous -- because everything it touched was green. It
took an injection into production code to find. This is the second such
finding in this project after Phase 01's vacuous ArchUnit pass, and the pattern
is the same: a check that has never been seen to go red is not yet a check.

``D-001`` and ``D-009``, verified independently
------------------------------------------------

* **The derivation records are truthful.** ``AtlantaFxThemes`` and
  ``ConsolePane`` each name their upstream file and commit
  ``480b3013e7f8fb51a2b8c58681043821e3e7f865``, which matches the actual
  ``HEAD`` of the CasanovoGUI clone.
* **The header configuration is extended, never relaxed.**
  ``checkstyle-derived.xml`` is a strict **superset** of ``checkstyle.xml`` --
  no module dropped, one ``Regexp`` module added to require the per-file
  derivation record -- and ``scripts/build.sh`` proves the ordinary and derived
  file sets are exhaustive and disjoint over every ``.java`` file on disk.
* **The collective attribution is correct, and I checked the fact it rests
  on.** Of the 83 Java files in the upstream tree, **none** carries a copyright
  notice, so there was no per-file notice to drop.
* **``D-009``'s placeholder is intact.** Every file reads ``Copyright (C) 2026
  The CometGUI authors.`` The only other copyright line in the tree is
  ``Copyright (C) the CasanovoGUI authors.`` in the four derived files, which
  is the attribution ``D-001`` requires rather than a substitution.

Residue carried forward
------------------------

Small, named, and none of it blocking.

* **The ``Settings`` section: owner assigned 2026-08-31, content deliberately
  left empty.** See :ref:`status-settings-decision`.
* ``UiIdsTest.noTwoIdentifiersCollide`` asserts a floor of 80 against a real
  surface of 119, and its ``allIdentifiers()`` walks ``displayOrder()`` rather
  than ``values()``. The new pin test covers the surface, so this is hardening,
  not a hole.
* ``consoleSeverityFilter`` derives from ``MessageSeverity.name()``, so
  renaming a **domain** enum renames a UI identifier. Now pinned, so it fails
  loudly instead of drifting.
* **Where the shared ``BoundedMessageLog`` lives** once the process service
  writes to the log the UI reads. Phase 02 deliberately did not decide this;
  Phase 03 inherits it.

.. _status-session-04:

Session 04 (2026-08-31): the checkout moved, and GitHub ran something at last
=============================================================================

Three things happened before any phase work, and all three are evidence rather
than housekeeping.

**1. The checkout moved off ``/workspace`` and the toolchain was stranded.**
``tools/env.sh`` had baked in ``/workspace/tools/...`` at install time, so
``mvn`` reported only ``The JAVA_HOME environment variable is not defined
correctly`` and nothing else. The virtualenv was broken the same way: 22
console scripts carried ``#!/workspace/.venv/bin/python3``, ``sphinx-build``
among them, which is the binary ``scripts/ci/docs-build.sh`` invokes by
absolute path -- so the documentation gate could not have run either.

The part worth carrying forward is **why nothing caught it**:
``scripts/build.sh`` re-bootstraps the toolchain only when ``tools/env.sh`` is
**missing**, never when it is merely **wrong**. A stale generated file is
therefore indistinguishable from a healthy one to every script that depends on
it. The generator now emits an ``env.sh`` that keeps the pinned directory names
but resolves the path to them at source time from the file's own location;
verified by generating it, copying it elsewhere and sourcing it there, where
``JAVA_HOME`` followed the file instead of staying behind. ``ONBOARDING.rst``
no longer asserts a fixed working directory, because that claim is exactly what
went stale.

**2. Both refs are pushed, and the token has ``workflow`` scope.** This session
is the first of four to hold a GitHub credential. ``main`` was pushed at
``e97d863`` -- deliberately as an explicit SHA rather than the branch tip, since
two phase orchestrators were committing to the same tree at the time -- and
``windows-percolator-verification`` was pushed and created. **The
``workflow``-scope warning recorded for session 03 did not materialise**: the
branch carries four files under ``.github/workflows/`` and the push was
accepted. Note that a ``git push --dry-run`` would NOT have proved this, since
that rejection is served by the remote only once the pack is actually sent.

**3. GitHub has now executed a workflow -- one -- and it failed by design.**
The Actions API had reported ``total_count = 0`` on 2026-08-30. It now reports
**1**: the ``nightly`` workflow, ``schedule`` event, head ``9115b1c`` on
``main``, 2026-08-31T09:21:27Z, conclusion **failure**. The failure is the
designed one and was confirmed from the runner's own log rather than inferred::

    NOT IMPLEMENTED -- nightly-version-matrix.sh
    This CI step is a deliberate stub installed by PHASE-01.
      owned by    PHASE-15
    ##[error]Process completed with exit code 70.

What that run proves, and it is not nothing: **the project-local toolchain
strategy works on a real GitHub runner.** ``scripts/ci/toolchain.sh``,
``fontstack.sh`` and ``python-env.sh`` all succeeded there -- a project-local
JDK, Maven, font stack and virtualenv provisioned on a machine nobody
configured, with no ``setup-java``. Every previous statement about those
scripts was a local one.

What it does **not** prove: Phase 01 item 6 needs the four workflows to run
**on a pull request**, and a scheduled nightly is not that. Phase 00 item 8
needs ``windows-percolator.yml``, which triggers only on ``pull_request``
(``workflow_dispatch`` is declared but GitHub offers the button only for a file
already on the default branch). **Both items still need the pull request to be
opened**, which is the one action this session was asked to stop short of.

.. _status-nightly-masking:

A new finding: the nightly's real step never runs on a real runner
------------------------------------------------------------------

``nightly.yml`` runs its steps in one job, in order, and GitHub aborts a job at
the first failing step. The order is four Phase-15 stubs (version matrix, large
dataset, determinism, performance), then a Phase-15 GUI stub, then
**Documentation link check** -- ``scripts/ci/nightly-linkcheck.sh``, which is
**a real step**, and which the file's own header calls out as one of only two
real steps it has. The first stub exits 70 at step 6, so the link check at step
11 is reported ``skipped`` and **has never executed and will not execute until
Phase 15 replaces the stubs above it.**

``scripts/ci/run-pipeline-locally.sh`` cannot see this, and that is the
instructive part: it runs each step **independently** and grades it against its
own classification, so it correctly reports the link check green while the real
runner never reaches it. The local harness is not wrong; it models a different
execution semantics than the thing it stands in for. This is the same family as
the two findings above it -- a check that is never *reached* is as inert as a
check that is never *red* -- and it was invisible until a real runner ran.

**Not fixed here, deliberately.** ``.github/workflows/nightly.yml`` is modified
on ``windows-percolator-verification``, which is now pushed and awaiting a pull
request; fixing it on ``main`` as well would produce a conflict on merge and fix
it twice. It belongs with Phase 01's residue, whoever lands it.

.. _status-one-phase-at-a-time:

Owner's decision, 2026-08-31: phases run one at a time
-------------------------------------------------------

**The owner ruled that once Phases 03 and 04 finish, only one phase runs at a
time.** Those two were allowed to complete concurrently; nothing after them
overlaps. ``ONBOARDING.rst`` and ``phases/index.rst`` are amended -- the tier-1
permission to run two orchestrators on disjoint files is withdrawn, and the
ordering notes now describe independence for *sequencing* only.

The evidence for it came from this very session, and the third item is the
argument. The first two are hazards with cheap workarounds
(:ref:`status-concurrent-maven`); the third is not fixable by briefing:

**Both live phases independently built a secret-redaction rule set.** Phase 03
wrote ``SecretNames`` and ``SecretValues`` in ``cometgui-process``; Phase 04
wrote ``SecretRedactor`` and ``SecretRegistry`` in ``cometgui-provenance``.
Those modules are **siblings** -- each depends on ``cometgui-domain``, neither
on the other -- so neither could *use* the other's implementation. The two
keyword lists diverged by one entry: Phase 03's carried ``signature`` and Phase
04's did not, so a value named ``...signature...`` would have been redacted in
the process log and **not** in the provenance record. Phase 04 confirmed the gap
was real -- ``REQUEST_SIGNATURE`` matched nothing in its twelve keywords. That is
the silent, security-relevant drift ``R-SEC-03`` exists to prevent, and two rule
sets falsify Phase 04's own gate wording, "driven by **one** rule set".

.. note::

   **Two corrections to this entry, both recorded because the first draft was
   unfair to Phase 03.**

   *One keyword, not two.* This entry first claimed the lists differed by
   ``signature`` **and** ``limelightkey``. Wrong: Phase 03's
   ``BUILT_IN_KEYWORDS`` holds thirteen entries and ``limelightkey`` appears
   only in a Javadoc example at ``SecretNames.java:145`` illustrating that
   caller-added names are normalised. The error came from grepping quoted
   strings out of a whole file, Javadoc included -- the same class of mistake
   this project keeps cataloguing, a measurement that did not measure what it
   claimed to. ``limelightkey`` was added to the merged set anyway, on tier 1's
   Phase 12 reasoning rather than Phase 03's.

   *Phase 03 saw it, and said so.* This entry first said neither orchestrator
   could see the other's work and that neither report was wrong "and neither
   could have been". Phase 03 could and did: it named its class
   ``ProcessRedactor`` rather than ``SecretRedactor`` **deliberately**, recorded
   that ``org.cometgui.provenance.redaction.SecretRedactor`` already existed and
   that ``cometgui-process`` depends on ``cometgui-domain`` alone so it could not
   use it, predicted the import collision a future workflow engine would hit
   holding both at once, and wrote that "merging the two -- most plausibly by
   moving the shared rules into ``cometgui-domain`` -- is an architectural
   decision, and it belongs to whoever owns both phases." That is the correct
   escalation and it names the exact resolution tier 1 then issued.

   **What actually failed was the escalation PATH, not the analysis.** The
   finding reached tier 1 as a Javadoc comment in a source file rather than as a
   message, so it was found by reading the working tree and could as easily have
   been missed. The rule that follows: **a cross-phase architectural finding is
   escalated upward as a report, not left as a comment in the code that
   contains it.** The one-at-a-time rule still stands -- serial phases would have
   made the second implementation unnecessary rather than merely well-labelled --
   but it stands on the cost of the duplication, not on any claim that the
   phases were blind to it.

**The general lesson: file-level disjointness is not design-level
disjointness.** Two agents can respect every path boundary and still build the
same thing twice, and no briefing about paths can prevent it, because neither
party can see that it is duplicating anything. If two phases look
parallelisable, that is a prompt to look for a shared abstraction between them,
not a licence to overlap them.

**The resolution, directed by tier 1 while both phases were still live:** the
shared rule set moves to ``cometgui-domain``, the only module both siblings
depend on. Phase 04 owns the move, because it owns the rule set and its tests
are far deeper -- a PEM private-key block rule, registry ordering pinned by
test, a seeded secret corpus, and short-carrier coverage added after a
length-conditioned leak got through. Phase 03 keeps ``ProcessRedactor``, whose
two insights are process-specific and must survive: redacting each argv element
**before** ``ToolCommand.displayString()`` escapes it (escaping turns a ``"``
inside a token into ``\"``, so a literal post-escape search misses it and the
secret prints in full), and returning the argument **by reference** when the
registry is empty so a 500 MB flood pays nothing for a feature no tool in the
workflow uses. The merged keyword list must be the considered **union**; a
rejection of ``signature`` or ``limelightkey`` must be argued in the worklog,
not made by omission.

**Landed 2026-08-31** at ``b0e7122``, in ``org.cometgui.domain.secrets``, and
verified by tier 1 rather than accepted on report: the package exists, the merged
list is fourteen keywords, and ``cometgui-domain`` moved from 152 mutations
across five packages to 204 across six, with the new package contributing
exactly the 52 that ``org.cometgui.provenance.redaction`` contributed before the
move -- so the suite followed the code rather than quietly ceasing to measure it.
One divergence survived the merge and was caught on inspection: the two classes
disagree on the marker string (``[REDACTED]`` against ``***REDACTED***``), which
would have printed one string in the console log and another in the provenance
record for the same secret. Phase 03 was directed to resolve it to a single
constant asserted by a test.

.. _status-concurrent-maven:

Running two phase orchestrators concurrently: one real hazard, and it was mine
------------------------------------------------------------------------------

Phases 03 and 04 ran concurrently on disjoint paths, which the ordering notes
permit. The file-level separation held. **Maven did not**, and the cause was the
dispatch, not the phases.

``scripts/build.sh`` line 217 runs ``mvn -B -Dmaven.repo.local=_build/m2repo
clean verify`` **at the repository root, in the working tree** -- and the
briefing for *both* orchestrators told each to run ``bash scripts/build.sh``
before writing code, on the strength of Phase 02's handoff advice. Two root
``clean verify`` runs at once means one deletes ``target/`` under the other
mid-build, and the error names the victim's own code rather than the collision.

Three things follow for any future session that runs phases in parallel:

#. **Do not tell two concurrent orchestrators to run ``scripts/build.sh``.**
   Have the second use ``mvn -o -pl <module> -am`` instead, or serialise the two
   opening builds explicitly. ``build.sh`` is written as the one documented
   command for a *single* worker in a *quiet* tree.
#. **``_build/m2repo`` is shared** by both phases and by the gate harnesses, so
   concurrent Maven writes there are a hazard even when the modules are
   disjoint. A ``flock`` around Maven invocations is the cheap fix, and Phase 04
   adopted one **in its own agents' command lines only**. *Corrected 2026-09-02:*
   nothing under ``scripts/`` has ever taken a lock -- ``build.sh`` runs
   ``mvn clean verify`` at the repository root unprotected -- so a reader of
   this paragraph would have concluded that builds here are serialised when they
   are not. Four collisions across three sessions say otherwise. The lock lands
   in ``build.sh`` with a control proving it serialises; see
   :ref:`status-lock-absent`.
#. **The gate harnesses are not the hazard.**
   ``verify-quality-gates.sh``, ``verify-test-gates.sh`` and
   ``verify-shell-gates.sh`` each declare "WHERE IT WORKS. Never in the working
   tree." and each extracts ``git archive HEAD`` into a sandbox under ``_build/``
   and builds the copy. None of them can remove a ``target/`` in the working
   tree. This was checked rather than assumed, because a phase orchestrator had
   attributed a collision to the baseline run; the baseline ran 17:27:37 to
   17:39:34 with zero failures and its result stands.

A shared-gate trap the same pair surfaced
------------------------------------------

``config/spotbugs/exclude.xml`` excludes ``NP_NULL_PARAM_DEREF``,
``NP_NULL_PARAM_DEREF_NONVIRTUAL`` and ``NP_NONNULL_PARAM_VIOLATION`` -- but
**not** ``NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS``. So any *instance* method
calling ``Objects.requireNonNull(arg, "name")``, together with the test proving
it rejects null, fails ``mvn verify``. That shape is everywhere in this codebase.

**The standing idiom is to fix it in test code**, with a helper that launders the
null through a method SpotBugs cannot see through (``deliberateNull()``), and
**not** to add the detector to the exclusion file. Phase 04 met this first,
refused the exclusion on the grounds that it would weaken a shared gate to make
its own code pass, and reported the decision rather than asking; tier 1 endorsed
it and made it binding on Phase 03 so the tree carries one convention.

.. _status-injection-must-land:

A sixth shape: the defect stopped working, not the assertion
-------------------------------------------------------------

Phase 04's unit 5 found the most useful harness lesson of the session, and it
applies to every falsifiability claim this project makes.

Two agents wrote their injection script to ``inject.py`` in the **same shared
scratchpad directory**. Two of unit 5's injections then executed the other
agent's script, changed nothing, and the test suite went green -- so the honest
report was *"I injected the defect and the tests still passed"*. That sentence is
**indistinguishable from "the gate is weak"** unless the edit is proven to have
landed. It was caught only because two defects that had previously *failed*
suddenly passed, which is the one signature that cannot be explained away.

This is the family's sixth shape and the first where the thing that quietly
stopped working was the **defect** rather than the **assertion**:

#. a rule that evaluates nothing (Phase 01);
#. an expected value computed by the code under test (Phase 02);
#. a property proved through a seam production need not use (Phase 04 unit 1);
#. an assertion too coarse to see a partial failure (Phase 04 unit 3);
#. an input set too narrow to see it (Phase 04 unit 3, again);
#. **an injection that never landed** (Phase 04 unit 5).

**The rule, now standing for every tier including tier 1.** An injection is
evidence only if the edited file is confirmed to have changed:

* assert the anchor text occurs **exactly once** before writing. Several classes
  here match a naive replace in more than one place, or in a **Javadoc example**
  rather than in code -- and a Javadoc-only hit changes no behaviour, so it
  produces a false "the gate is weak" verdict;
* print a marker and ``grep`` it back out of the target file before running
  anything;
* restore from a per-injection backup and verify with ``sha256sum -c``, not by
  eye and not by ``git diff`` alone;
* use a **per-agent scratchpad subdirectory**. Sibling subagents share one
  scratchpad root, so unique paths are not optional. The main orchestrator's own
  injection plan survived this only by having an unusual filename, which is luck
  rather than method;
* if a defect that previously failed suddenly passes, suspect the injection
  before suspecting the gate.

.. _status-class-census-gap:

A seventh shape, and a gap in the gate harness that lets it through
--------------------------------------------------------------------

The main orchestrator hypothesised that a coverage figure taken from a moving
tree could err **high** as well as low, because a class whose test does not
compile is often absent from the report altogether -- and an absent class does
not drag an average down, it leaves the sample. Phase 04 built the check and it
found a live instance **on the first run**::

    compiled classes (excluding package-info and inner): 37
    classes in jacoco.xml:                               36
    COMPILED BUT ABSENT FROM JACOCO:
      org.cometgui.provenance.manifest.ManifestReader

``ManifestReader`` was compiled and carrying **79 NO_COVERAGE mutations** while
the build reported *"All coverage checks have been met"*. The statement was true
of the sample and false of the code.

**Why this is the worst of the seven shapes, specifically for sign-off.** Every
earlier shape produced a check that *could not fail*. This one produces a **real
measurement over an incomplete population**. Re-running the gate -- which is what
a sign-off does -- reproduces the same clean figure, so it cannot be caught by
verification at all. Only auditing that the sample was **whole** catches it.

*The exclusion did not persist.* Checked at tier 1 rather than assumed: no
``ManifestReader`` entry in any ``pom.xml``, no exclusion in
``cometgui-provenance/pom.xml``, and both the class and its test present. It was
transient mid-flight scoping, not a gate weakened to make something pass.

**The harness gap, and the ruling.** ``scripts/build.sh`` already fails a
*module* that compiles real classes and produces no ``jacoco.xml``; that check
exists and works. It does **not** check the per-*class* case, which is the one
that bites when a single test is excluded or fails to compile. The two failure
modes are one line apart in the same stage and only one is covered.

#. **The check** belongs in ``scripts/build.sh``'s gates stage, beside the
   module-level check it completes, so it protects every build.
#. **A control proving it bites** belongs in ``scripts/verify-test-gates.sh``.
   Adding the check without a falsification control would repeat the Phase 01
   mistake this project was founded on.

**Owner and moment: tier 1, after Phases 03 and 04 land and before Phase 05 is
dispatched.** Not the phase that found it -- ``scripts/`` is shared and the tree
was still moving. Phase 04 hands over its fifteen-line census verbatim, with the
invocation and the output above, so the replacement is built from a script that
has actually caught something.

.. _status-platform-divergence:

Platform divergence, in two tiers -- and the grading rule it settles
---------------------------------------------------------------------

Asked to name every place its code takes a different path on a platform that has
never executed it, Phase 04 returned **five** rather than the three tier 1 had
identified, and sorted them into two tiers. The sorting is the useful part:
collapsing them would overstate the residue as much as omitting them would
understate it.

**The grading rule these establish**, and it applies to every remaining phase:

* *"We could not run this code on that platform"* is a **testing gap**. It does
  not cap a grade. Phase 02 passed in this Linux-only environment on exactly
  that basis -- its gate items had no platform-divergent path.
* *"There is **different code** on that platform and it has never run"* is
  **unverified behaviour**, and it does cap a grade.

**Tier A -- the divergent branch IS executed here, by a faithful stand-in.**
Not unexecuted code, and therefore not residue.

* *The hash cache's attribute source.* ``FileFingerprint.of`` asks whether the
  file system publishes the ``unix`` view; without it ``fileKey`` and ``ctime``
  are null, ``tamperEvident()`` is false, and the cache stores nothing. That
  branch runs here through a **zip file system**, and the stand-in was measured
  rather than assumed. Confirmed independently at tier 1::

      default FS views = [owner, dos, basic, posix, user, unix]  fileKey = (dev=33,ino=...)
      zipfs FS views   = [zip, basic]                            fileKey = null

  So the Windows-*shaped* algorithm is exercised and mutation-tested. What
  remains unverified is that Windows is the case it stands for.
* *Directory fsync*, exercised by substituting a ``Durability`` that throws. The
  Windows question is sharper than "does it work": opening a directory channel
  and **silently succeeding without forcing anything** has identical observable
  behaviour to failing, and the opposite durability guarantee.

**Tier B -- never executed in any form. This is the residue that caps the
grade.**

* **``ATOMIC_MOVE`` under contention, and this is the one that matters.** Gate
  item 5 is proved by a concurrent reader observing only whole documents, and
  that proof is POSIX-only. On Windows a rename over a file another process
  holds open can fail with ``AccessDeniedException`` -- and the Provenance UI of
  Phase 13, a virus scanner and a sync client are all exactly such processes.
  **If Windows cannot replace an open file the repair is a retry policy or a
  different finalisation strategy: a design change, not a test.** Routed into
  ``phases/PHASE-13-provenance-ui.rst`` so the viewer is not built first and
  discovered second.
* *Absolute-path validation.* ``Path.isAbsolute()`` is false for ``/var/...`` on
  Windows, so records valid here are rejected there. This is the actual reason
  behind the twenty ``@DisabledOnOs(WINDOWS)`` tests, and the repair is a second
  pinned document with Windows paths -- never a relaxation of the rule.
* *``Path.toRealPath()`` as the cache key*, which on Windows also folds case and
  8.3 short names.

**What was deliberately kept off the list**, which is as much a judgement as
what went on it: line endings, number formatting, digests, redaction, and JSON
and RST generation all run identical code everywhere, with locale-sensitive
paths pinned under Turkish, German and Thai-digit locales. Those are testing
gaps at worst, and admitting them would dilute the document.

.. _status-hash-cache-windows:

The hash cache is deliberately inert on Windows
------------------------------------------------

Phase 04 unit 5 keys the input-hash cache on a **fifth** attribute beyond
``R-PROV-02``'s four: the POSIX inode change time ``unix:ctime``. That is a
strengthening -- strictly more invalidation, never less -- and it is what makes
gate item 3's same-size, same-mtime, same-inode mutation **detected** rather than
merely survived, because user space cannot forge ``ctime``.

The consequence, which is a real product behaviour and not an implementation
detail: **on Windows neither file identity nor ``ctime`` is available, so the
cache stores nothing at all.** The design caches nothing rather than caching
something it cannot validate, which is the phase document's "if in doubt,
rehash" implemented literally, and it is the right call for a correctness
mechanism.

**For the owner and for Phase 15.** Every run therefore rehashes every input on
one of the three tier-1 platforms. For a multi-gigabyte mzML that is a
measurable cost, and it is the kind of thing a performance phase should meet as
a documented decision rather than as a surprise. It also compounds the standing
risk that no Windows binary in this project has ever been executed
(:ref:`status-session-04`), so the behaviour is currently reasoned rather than
observed.

Baseline gate run
-----------------

``bash scripts/verify-all-gates.sh`` at the start of the session, after the
toolchain repair: **10 controls passed, 0 failed, 718 seconds (11m58s)**, with
176 graded checks -- license 5, workflows 9, docs 1, traceability 8, sbom 8,
depscan 16, pipeline 24, quality 42, shell 30, tests 33. "Every gate was seen to
reject its defect and accept the clean tree." That is also the strongest
evidence the relocation repair is complete: the whole Maven, headless-JavaFX and
Sphinx stack runs at the new path.

.. _status-settings-decision:

The ``Settings`` section: owner assigned, content deliberately empty
====================================================================

Tier 1 owed a decision here before Phase 07, and the answer turned out to be a
scope *reduction* rather than the content list the question implied.

**What the specification actually says.** One sentence, at
``specification.rst`` line 1185: "Tool Manager and application Settings **may**
be secondary navigation or dialogs." That is permissive, not a requirement, and
it names no content. Searching the whole specification for anything required to
be user-configurable returns **one** hit -- ``AC-LL-05``, "the Limelight q
cutoff is separately configurable and defaults to 0.01" -- and that control
belongs to the Limelight and Results surfaces, not to an application-level
preferences pane. There is no required preference for the tool install root, the
cache or application-data directory, a proxy, a theme, or update checking; none
of those appears in the specification at all.

**The decision.**

#. **Phase 07 owns the ``Settings`` section**, as the next phase that builds UI
   and the deadline this question carried.
#. **Its default outcome is REMOVAL from navigation, not invention.** Phase 02
   built it honestly as an empty pane that says it is empty, pinned by a test.
   If, by Phase 07, no phase has produced an application-level, run-independent
   preference, Phase 07 removes the section rather than shipping a permanently
   empty one.
#. **An item may enter Settings only if it cites an ``R-`` rule or ``AC-``
   criterion requiring it to be configurable.** Today none does. This is the
   guard that keeps the section from becoming a junk drawer of invented
   preferences, which is the failure mode a nav section with no specified
   content invites.
#. **No second preferences mechanism.** If a later phase does earn a preference,
   it adds a group to this section; it does not build its own store.

**Why not simply fill it.** Inventing preferences would be an agent adding
unspecified product scope, which this project forbids -- the specification is
the authority on *what* to build, and disagreement is resolved by a recorded
amendment, never by a silent divergence. Two candidates were considered and
both were rejected on inspection: ``R-CMT-05``'s "configured limit derived from
the thread setting and available cores" is derived from a Comet parameter and
the host, not from a user preference; and ``R-PROC-03``'s "documented retention
policy" is documented, not configurable, and Phase 02 already implemented it as
a constant.

**For the owner.** If CometGUI *should* have a real Settings surface, that is a
specification amendment (revision 11) rather than something an agent may decide,
and it should be raised before Phase 07 rather than after. This entry records
the deliberate emptiness so that a later reader does not mistake it for an
oversight and quietly fill it.

.. _status-p03:

Phase 03 sign-off (2026-09-01)
==============================

:Outcome: **PARTIAL**
:Signed off by: Main orchestrator, session 04, by re-running every gate item
   itself and injecting six defects the phase had never tried
:Phase records: ``handoffs/PHASE-03-worklog.rst`` (8 units, 29 commits),
   ``handoffs/PHASE-03-handoff.rst``

The phase orchestrator reported eight signed-off units and recommended
``PARTIAL``. The main orchestrator re-ran all six gate items rather than
accepting that report, and **injected its own defects rather than re-running the
phase's negative controls**. All six held.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 8 87

   * - #
     - Result
     - The defect I injected, and what it printed

   * - 1
     - PASS
     - Drifted **one hand-typed scenario phrase** away from the specification's
       wording -- ``stdout/stderr interleaving`` to ``stdout and stderr
       interleaving`` -- leaving all eleven entries present, which the phase's
       own control (renaming a covering *method*) would not have caught. Two
       assertions failed independently, and the one that matters read
       ``specification.rst`` **from disk** and quoted the section back: *"these
       phrases are not in the specification's own list any more, so the map has
       drifted from the requirement it implements."*

   * - 2
     - PASS
     - **The opposite half of the phase's own control.** The phase emptied the
       descendant snapshot, which leaves the *child* alive. I deleted
       ``ordered.add(root)`` from ``ProcessTree.terminationOrder`` so
       descendants die and the **parent** survives. ``the parent (pid 1514001)
       was still alive 60s after the cancellation; isAlive() is true``. The test
       asserts both halves and names which process survived.

   * - 3
     - PASS
     - Made the log writer withhold the **last** line from disk while every
       in-memory counter stayed exactly correct -- a tail truncation, where the
       phase's control corrupted an interior ordinal. Heap growth was
       4 852 904 bytes against a 33 554 432 bound, so the *bounded heap* claim
       passed while the *complete log* claim failed on its own assertion:
       ``the file is a whole log: header first, footer last``. The three claims
       really are separable, as the test's own comment promises.

   * - 4
     - PASS
     - Unicode-normalised every argv element to **NFD** inside
       ``ProcessService`` -- invisible in a rendered diff, and distinct from the
       phase's encoding-environment control. ``expected: <... prot?ines ...> but
       was: <... prote?ines ...>``, the ``é`` decomposed. The assertion notes it
       compares *"the values the operating system actually delivered rather than
       the ones this test hoped for"*.

   * - 5
     - PASS
     - Used ``Runtime.getRuntime().exec(...)`` in ``cometgui-install`` rather
       than the phase's ``new ProcessBuilder``, exercising the rule's
       ``orShould`` clause that its own control never touched.
       ``Method <org.cometgui.install.MoRuntimeExecProbe.run()> calls method
       <java.lang.Runtime.exec([Ljava.lang.String;)>``.

   * - 6
     - PASS
     - A fixed sleep expressed as ``java.util.concurrent.TimeUnit.MILLISECONDS
       .sleep(50)`` inside a private helper, in a **different** test file from
       the phase's control -- testing whether the scan greps only the literal
       ``Thread.sleep``. It does not: ``StageLogFileTest.java:138: a pause
       through any receiver``. The scan also carries a hand-typed floor for the
       number of files it must read, so a scan that read nothing fails.

Why PARTIAL rather than PASSED
------------------------------

Gate item 2 carries **no platform qualifier**, and descendant termination is the
most platform-divergent thing in this project -- exit codes 143/137,
terminate-versus-kill, reparenting, and ``Process.destroy()`` closing pipes are
not the same on Windows or macOS, and none of it has ever executed there. By the
rule established for Phase 04 (:ref:`status-platform-divergence`), *"we could not
run this code there"* is a testing gap that does not cap a grade, but *"there is
different code there and it has never run"* is unverified behaviour and does.
Item 4 by contrast **is** explicitly scoped to "the reference platform", so it
caps nothing -- whoever wrote the gate scoped deliberately where they meant to.
The phase's handoff carries nine divergence entries written as what a Windows or
macOS twin must prove.

What the phase found that is worth more than the phase
-------------------------------------------------------

**The classic snapshot-after-destroy bug passed all 128 tests.**
``Process.destroy()`` only *queues* a SIGTERM, so a snapshot taken on the next
line still sees the child -- a test that passed only because a race is always
won on an idle machine, and that would have failed later on a loaded one. The
phase's adversarial unit 2b found it and closed it by asserting the order where
it is a fact rather than a race.

**Two harness findings it reported against itself**, neither of which it had to
disclose: an injection of its own that reached the source but not the compiled
class and reported green -- the eighth catalogued shape, and the reason every
tier-1 injection in this sign-off confirmed the ``.class`` hash moved -- and a
regression it caused in ``verify-test-gates.sh``, whose sandbox carried no
project documents. It diagnosed the harness rather than weakening its own test,
and escalated the shared-file edit instead of making it. Tier 1 then added
``specification.rst`` to ``build_sandbox`` in ``scripts/verify-test-gates.sh``
and recorded the general rule there: the sandbox carries what the build reads as
**input**, and a project document a test asserts against is an input; it does not
carry records or generated output. Phase 04's provenance report and Phase 15's
traceability work are the next two that will need a line there.

The aggregate suite, re-run by tier 1 on a quiet tree
-----------------------------------------------------

``bash scripts/verify-all-gates.sh`` -- **10 controls passed, 0 failed, in 1835
seconds (30m35s)**, with the ``tests`` control at **33 graded assertions**: the
floor recorded at the session-04 baseline **held**, so no control was silently
dropped or short-circuited. "Every gate was seen to reject its defect and accept
the clean tree."

*An earlier run of this suite reported ``pipeline`` FAILED, and it was the main
orchestrator's own doing rather than a regression.* Foreground
``scripts/ci/docs-build.sh`` runs -- checking edits to this very file -- raced the
suite's strict-Sphinx step over the shared ``docs/_build/html`` directory, so the
published HTML was missing when that step read it. Diagnosed by reading the step
log rather than the summary, then falsified by re-running the control in
isolation: ``1 control(s) passed, 0 failed, in 21 seconds``. Recorded because it
is the same shared-resource collision this session spent the day eliminating in
others, committed a third time by the tier enforcing it, and because "a gate
failed" is exactly the finding one is most tempted to accept without asking
which resource was shared.

Residue carried forward
------------------------

* **Descendant termination is unverified off Linux**, as above. Phase 15 owns
  the platform matrix.
* **The gate suite now costs ~29 minutes, up from 12**, because the 500 MB flood
  is paid twice. Nothing was weakened to achieve it; it is a configuration
  decision for the owner, and it is a real cost on every future sign-off.
* ``BoundedMessageLog`` did **not** move and the process service never sees one:
  it takes a ``RunMessageSink`` (append-only, one method) and a caller wires
  ``log::append``. The question Phase 02 left open is answered without
  publishing shared mutable state.

.. _status-session-05:

Session 05 (2026-09-01): ``main`` is published, and push protection bit on a decoy
==================================================================================

**``main`` is on the remote at** ``9abfe1b``. Ninety-one commits -- the whole of
Phase 03's sign-off, Phase 04's paused state and every session record -- had
existed only on this machine. They no longer do. Nothing was force-pushed and no
history was rewritten.

.. _status-push-protection:

The first external gate this project ever met, and it rejected the push
------------------------------------------------------------------------

The push was refused, and not by the ``workflow``-scope trap
(:ref:`status-next-action`) that every previous session had been warned about.
**GitHub push protection scanned the commits and found a credential**::

    —— GitLab Access Token ——
    - commit: fea6bd6  .../manifest/ProvenanceManifestTest.java:51
    - commit: fea6bd6  .../manifest/ToStringSecrecyTest.java:60
    - commit: 5b10a58  .../manifest/ToStringSecrecyTest.java:60
    - commit: 8f00cae  .../events/ProvenanceEventLogTest.java:190
    - commit: 8f00cae  .../events/ProvenanceEventTest.java:137

The string is ``glpat-Z1x9QeR7sVbN3mK0pLtY``, a hand-typed fixture from Phase
04's seeded-secret corpus whose own Javadoc says it is "shaped like a real
access token so that a substring search cannot match it by accident". It is
fabricated. It was written to be convincing, and it convinced a scanner.

**Why the obvious repair does not work, which is the part worth carrying
forward.** Editing the fixture cannot unblock the push: protection scans every
commit in the range rather than the tip, and the string is already in three
commits of unpushed history. The only mechanical removal is rewriting all
ninety-one commits -- and **every sign-off entry in both work logs, both
handoffs and this file names the commit it signed off by hash**. Rewriting
would have destroyed the project's evidence chain to hide a decoy. It was
refused on that ground, not on the published-history rule, since these commits
were not yet published. The owner allowlisted the detection as a false
positive, which is the correct disposition for a fabricated value, and the push
then succeeded unchanged.

**What the rest of the corpus shows, and it is a convention worth adopting.**
The same scan over the working tree finds three other provider-shaped fixtures
and only the GitLab one was rejected:

* ``AKIAIOSFODNN7EXAMPLE`` is **AWS's own published documentation example**,
  which scanners allowlist by design. Whoever chose it chose correctly.
* ``ghp_S3cr3tT0k3nExampleValue0123456789ab`` survives only by luck: GitHub's
  detector for its own tokens validates a checksum in the final characters, and
  this fixture fails it. A differently-typed fake would have been rejected.
* ``glpat-`` has no checksum in its detector, only a prefix and a length, so any
  plausible-looking fake matches.

The property a redaction corpus actually needs is **distinctiveness against an
accidental substring match**, not provider authenticity -- so adopting each
provider's published example value costs the corpus nothing it needs. Phase 04
has been asked to record that judgement in its handoff rather than to change
the fixtures, because changing them now would alter signed-off units without
unblocking anything.

**Two later phases inherit this.** Phase 12 will hold a **real** Limelight
credential, where the same scanner is a protection rather than an obstacle and
must not be routinely bypassed; and Phase 16 publishes the repository, where a
fork or a mirror re-push meets the same rejection because the string stays in
history. Neither is a defect today. Both are cheaper to know now than to
discover at release.

Baseline gate run
-----------------

``bash scripts/verify-all-gates.sh`` at the start of the session, on a quiet
tree: **10 controls passed, 0 failed, in 1702 seconds (28m22s)**, with the
``tests`` control at **33 graded assertions** -- the floor recorded at the
session-04 baseline held, so no control was silently dropped or
short-circuited.

Phase 04 dispatched
-------------------

One fresh phase orchestrator, briefed by
``handoffs/PHASE-04-RESUMPTION-BRIEF.rst`` and told **up front that its expected
grade is PARTIAL**, so that it documents for the evidence rather than writing
toward a verdict. It is the only phase live in the tree. Its first instruction
is to re-measure rather than to resume, and its second is that signing off units
9 and 10 -- landed, never read, never re-run, never injected into -- is the
first real work of the resumption.

Tier 1 holds two things away from it: ``scripts/build.sh`` and
``scripts/verify-test-gates.sh``, where the per-class population census lands
after this phase (:ref:`status-class-census-gap`), and pushing, which is not a
phase agent's to do. ``handoffs/BRIEF-TEMPLATE.rst`` still told phase agents
"there is no git remote and none may be created"; that has been untrue since
2026-08-30 and is corrected.

.. _status-p04:

Phase 04 sign-off (2026-09-02)
==============================

:Outcome: **PARTIAL**
:Signed off by: Main orchestrator, session 05, by re-running all seven gate
   items itself and injecting seven defects of its own -- none of them the
   phase's controls
:Phase records: ``handoffs/PHASE-04-worklog.rst``, ``handoffs/PHASE-04-handoff.rst``
:Head: ``87acc84``

The phase resumed from its paused state, re-measured on a quiet tree before
resuming any work, signed off units 9 and 10 properly, and built units 11, 12
and 13. Tier 1 then re-ran every gate item rather than accepting that report,
and **injected its own defects rather than re-running the phase's negative
controls**. Six held on the first pass; one did not, was returned as rework,
and now holds.

.. list-table:: Gate items, as re-run at sign-off
   :header-rows: 1
   :widths: 5 8 87

   * - #
     - Result
     - The defect I injected, and what it printed

   * - 1
     - PASS
     - Corrupted **only the zero-byte vector**, by digesting a single ``0`` byte
       when the stream yielded nothing -- so all thirteen non-empty vectors
       stayed exact and the phase's own control, which digests one byte less,
       cannot express it at all on an empty file. ``MD5 ==> expected:
       <d41d8cd98f00b204e9800998ecf8427e> but was:
       <93b885adfe0da089cdf634904fd59f71>``, caught independently in two test
       groups.

   * - 2
     - PASS
     - An extra full read of the file before the real one -- **correct digests,
       heap growth 273 896 bytes against a 4 194 304 bound**, and only "one
       pass" false. Distinct from the phase's heap control, which keeps every
       chunk. Six assertions failed, the first being ``open() calls ==>
       expected: <1> but was: <2>``, and the permanent in-suite negative control
       printed its expected ``heapGrowth=33570920`` alongside.

   * - 3
     - PASS
     - Left ``unix:ctime`` **present but frozen** to ``Instant.EPOCH``, so
       ``tamperEvident()`` stays true and the cache still serves -- the subtle
       form, where the fifth attribute exists but cannot witness anything. The
       phase's control instead made identity absent. ``both calls reached the
       hasher ==> expected: <2> but was: <1>``: a stale hash served for a
       mutated file.

   * - 4
     - PASS
     - Silently dropped the **good event following a defect**, so recovery still
       reports the defect and still returns a usable-looking history one record
       short -- a partial loss rather than the phase's clean-read control.
       ``expected: <[1, 2, 3, 4]> but was: <[1, 2, 3]>``. The test asserts
       recovered sequence numbers, not merely that something was recovered.

   * - 5
     - PASS
     - Kept ``ATOMIC_MOVE`` **and** ``REPLACE_EXISTING`` -- so every structural
       check of the call still passed, and ``FileSystemDurabilityTest`` stayed
       green 5/5 -- but emptied the target first, opening a zero-byte window.
       ``the reader saw a document of 0 bytes, which is neither of the two
       written``, observed 842 times. Proof by observation is the load-bearing
       part, exactly as the phase claimed.

   * - 6
     - PASS **after rework**
     - Two injections. The first found a real gap and was returned; see
       :ref:`status-p04-occurrence`. The second leaked the argv **in the RST
       report only**, leaving the JSON and the event log clean, to test whether
       the sweep really reads all three artefacts: ``provenance.rst=[0, 3, 5, 6,
       7, 8]`` against ``[0, 5, 6, 7, 8]`` for the other two. The sweep reports
       per artefact and per secret, which is the resolution needed to see it.

   * - 7
     - PASS
     - Read from ``mutations.xml`` rather than the console: ``cometgui-
       provenance`` **774 mutations, 771 KILLED, 3 TIMED_OUT, 0 SURVIVED, 0
       NO_COVERAGE**; the ``hashing`` package **70/70**; ``cometgui-domain``
       **204/204** with ``secrets`` **52/52**. The gate item names hashing and
       redaction specifically, and both are clean, as is the module as a whole.

.. _status-p04-occurrence:

What sign-off caught that the phase did not: occurrence count
--------------------------------------------------------------

Changing one word in the shared rule set -- ``KNOWN_TOKEN_SHAPES.matcher(...)
.replaceAll(...)`` to ``.replaceFirst(...)`` -- left **83 ``cometgui-domain``
tests and 22 provenance secrecy tests green, including the new whole-directory
artefact sweep**, while a token appearing twice in one string had its second
occurrence written out in clear. Proved by running the compiled redactor
directly rather than argued::

    TWICE -> used token [REDACTED] then retried with ghp_S3cr3t...0123456789ab
    SECOND OCCURRENCE SURVIVES: true

and, after restoring, ``[REDACTED]`` twice with ``SECOND OCCURRENCE SURVIVES:
false``. **The production code was correct**; what was missing was that no
carrier in the corpus contained its secret more than once.

This is catalogued shape 5 -- an input set too narrow to see the defect -- and
it is worth recording because of *where* it landed. ``SeededSecretCorpusTest``
documents two blind spots and argues explicitly that **carrier length is part of
this corpus's coverage, not an accident of how the examples were written**.
Occurrence *count* is the same kind of property, reasoned about in the same
file, and missed. It does not overturn gate item 6 -- the artefacts the sweep
generates genuinely carry no secret, because each is seeded once -- but it was
not acceptable residue with Phase 12 due to hold a real Limelight credential.

**The repair is more instructive than the finding, and this is the part to
carry forward.** The obvious fix -- add a two-occurrence carrier to the existing
tests -- **would have shipped a check that cannot fail.** ``redactText`` runs
the literal registry pass *first*, and ``SecretRegistry.redactIn`` uses
``String.replace``, which clears every occurrence; so through the fully loaded
corpus that pass silently repairs a broken pattern rule before the rule is ever
reached. The phase measured this before writing anything, and landed the new
carriers under ``patternsOnly()`` instead. Tier 1 confirmed both halves
independently: the ordering and the ``String.replace`` in the source, and the
defect going red on re-injection::

    SeededSecretCorpusTest.patternsAloneCoverWhatTheyClaim:600
      a pattern rule stopped covering its carrier: [corpus secret #9 survived
      the short token shape twice carrier with the pattern rules alone]
      ==> expected: <true> but was: <false>

A corollary the phase reported against its own earlier work, and which is worth
more than the fix: ``shortCarriersComeOutAsWritten`` proves less than it
appears to, for the same masking reason. The repeated carriers are built by
duplicating the short ones, so blind spots 2 and 3 cannot drift apart.

Two harness errors of my own, both instructive
-----------------------------------------------

Recorded because each produced a **misleading exit code in a different
direction**, and because the project's own documents warned about both.

* ``-Dtest=CachingHashServiceTest+FileFingerprintTest`` -- a ``+`` where
  surefire wants a ``,`` -- matched nothing, ran **zero tests**, and **exited
  0** with an injected defect live in the tree. Indistinguishable from "the gate
  is weak" except that the log carries no ``Tests run:`` line at all.
* Correcting the separator without ``-Dsurefire.failIfNoSpecifiedTests=false``
  then failed **upstream**: ``cometgui-domain`` matched no test, aborted, and
  ``cometgui-provenance`` was ``SKIPPED``. A red build that never ran the gate.

Both were caught by the standing protocol rather than by luck: confirm the
compiled ``.class`` hash moved, and read *why* a build is red or green rather
than *that* it is.

The numbers, from a quiet tree
-------------------------------

Taken by tier 1 after ``mvn -pl cometgui-domain install``, on a tree with no
agent in flight.

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - Measure
     - Value

   * - Tests
     - ``cometgui-provenance`` **669**, 0 failures, 2 skipped;
       ``cometgui-domain`` **362**, 0 failures

   * - Coverage, read from ``jacoco.xml``
     - **100.00% line (1827/1827) and 100.00% branch (623/623)** in
       ``cometgui-provenance``; 100.00%/100.00% in ``cometgui-domain``

   * - Mutation, read from ``mutations.xml``
     - provenance **774: 771 KILLED, 3 TIMED_OUT, 0 SURVIVED, 0 NO_COVERAGE**;
       domain **204/204**

   * - Class-population census
     - ``cometgui-provenance`` **37 compiled / 37 in jacoco.xml**;
       ``cometgui-domain`` **25 / 25**. ``ManifestReader`` is back in the
       sample. The seven classes PIT never mutated were **audited rather than
       skimmed**: an enum of constants, three interfaces, one functional
       interface, an exception with only a constructor, a constants holder, and
       one record whose validation delegates to ``ManifestChecks``. None is a
       class whose test failed to compile.

   * - Aggregate suite
     - ``bash scripts/verify-all-gates.sh`` -- **11 controls passed, 0 failed,
       in 1962 seconds (32m42s)**. Every pre-existing floor held (license 5,
       workflows 9, docs 1, traceability 8, sbom 8, depscan 16, pipeline 24,
       quality 42, shell 30, tests 33) and the new ``provenance`` control grades
       **24**. No floor was lowered.

   * - The generated report, under the strict docs gate
     - ``sphinx-build -n -W`` over
       ``cometgui-provenance/target/provenance-report-sample`` -- ``build
       succeeded``, and because exit 0 proves nothing: 25 422 bytes of HTML, 108
       rendered literals, **0** docutils ``problematic``/``system-message``
       spans, **0** stray double-backticks. Tier 1's own grep for every seeded
       secret over the generated directory returns nothing.

Nothing was weakened, checked rather than assumed
--------------------------------------------------

``git diff`` over ``pom.xml``, every module ``pom.xml``, ``config/`` and
``.mvn/`` across the whole phase is **empty**. Thresholds stand at 0.90 line,
0.85 branch, 0.80 class and mutation 80. No surefire exclusion was added; the
only ``<excludes>`` in the tree remain Phase 02's Spotless and Checkstyle
derived-file sets. **Zero unconditional** ``@Disabled`` in either module. The
rework touched no production code. No phase commit touched ``STATUS.rst``,
``DECISIONS.rst``, ``phases/``, ``scripts/build.sh`` or
``scripts/verify-test-gates.sh``.

The writer/reader separation unit 9 identified as load-bearing **survives**:
``ManifestReaderTest`` names ``ManifestWriter`` five times and calls it never --
twice for the ``FILE_NAME`` constant, which it pins to the literal
``provenance.json``, and three times in prose. No fixture in the reader's tests
is generated by the writer.

Why PARTIAL rather than PASSED
------------------------------

By the rule at :ref:`status-platform-divergence`: *"we could not run this code
there"* is a testing gap and does not cap a grade, but *"there is different code
there and it has never run"* is unverified behaviour and does. Gate items 3, 4
and 5 each rest on a branch that behaves differently on Windows and has never
executed anywhere:

* **``ATOMIC_MOVE`` under contention**, and it remains the one that matters.
  Item 5's proof -- a concurrent reader observing only whole documents -- is
  POSIX-only. On Windows a rename over a file another process holds open can
  fail with ``AccessDeniedException``. Already routed to
  ``phases/PHASE-13-provenance-ui.rst``, which must settle it **before**
  building a viewer that holds ``provenance.json`` open.
* **The hash cache is inert on Windows** (:ref:`status-hash-cache-windows`), so
  item 3 grades a different algorithm there. The Windows-shaped branch *is*
  exercised here through a zip file system, which is why this is the weaker of
  the two, but the stand-in is not the case itself.
* **Absolute-path validation and ``toRealPath``**, which are why twenty tests
  carry ``@DisabledOnOs(WINDOWS)``. The repair is a second pinned document with
  Windows paths, never a relaxation of the rule.

Items 1, 2, 6 and 7 are platform-neutral and clean. Phase 15 owns the platform
matrix.

Residue carried forward
------------------------

* **Windows behaviour for gate items 3, 4 and 5**, as above.
* **The per-class census is closed** (:ref:`status-census-closed`), on every
  build and with a control that proves it bites.
* **The sandbox line the phase escalated was NOT added, and the escalation was
  half a change.** ``ProvenanceFormatDocumentationTest`` does not read
  ``docs/reference/provenance_format.rst`` from disk: it holds a **hand-typed**
  copy of the member list, transcribed from that page. Copying the page into
  ``build_sandbox`` therefore changes nothing on its own -- the sandbox would
  carry a file no test opens, against its own documented rule that it holds only
  what the build reads as input. Making the drift check mechanical is a *test*
  change plus that line, and it is phase work rather than tier-1 infrastructure.
  **Owner: Phase 13**, the next phase to touch the provenance format. The
  phase's other finding stands and is worth keeping: a cross-directory
  ``.. include::`` is not an alternative, because ``docs-build.sh --self-test``
  copies only ``docs/``.
* **``build.sh --only gates`` grades undated evidence**
  (:ref:`status-only-gates-staleness`), and the dangerous direction is a stale
  *pass*. Not fixed; recorded with its repair named.
* **The artefact sweep's corpus still seeds each secret once**, so an
  occurrence-count defect in the *writers* would not be seen there. The domain
  corpus now covers the rule set, which is where that defect class lives; the
  phase judged a second copy of the property not worth the duplication and tier
  1 agrees, but it is recorded rather than forgotten.
* **``ExecutionRecord.status`` documents three values and enforces none**
  (Phase 08), and the event log's file name is pinned by no constant (Phase 13).
* The specification's "safely rendered command for display" has no member in the
  format; adding one is a schema v2 change and an owner-visible decision, not an
  agent's.

.. _status-census-closed:

The per-class census is closed, and closing it found two more defects
=====================================================================

Tier 1's debt from :ref:`status-class-census-gap`, discharged 2026-09-02, after
Phases 03 and 04 landed and before Phase 05 was dispatched -- the owner and
moment recorded when the gap was found.

**The check.** ``scripts/build.sh``'s gates stage now compares, per module, the
classes actually compiled into ``target/classes`` against the classes present in
``jacoco.xml``, and **fails the build** on any class that compiled and did not
reach the coverage sample. It sits directly beside the module-level check it
completes -- that check catches a module with no report at all, this one catches
the per-class case, and the two are one line apart in the same failure. On a
quiet tree, through the documented command::

    -- JaCoCo: every compiled class reached the coverage sample
       ok       cometgui-domain              25 compiled class(es), all 25 in the sample
       ok       cometgui-provenance          37 compiled class(es), all 37 in the sample
       ok       cometgui-process             15 compiled class(es), all 15 in the sample
       ok       cometgui-workflow            3 compiled class(es), all 3 in the sample
       ok       cometgui-ui                  13 compiled class(es), all 13 in the sample
       ok       cometgui-app                 8 compiled class(es), all 8 in the sample

The companion list -- compiled classes carrying no PIT mutations -- is printed
as a **prompt for a person and is deliberately not an assertion**, because an
interface, a constant enum, an exception with only a constructor or a
branchless record legitimately yields none. A rule there would fail honest code
and would be weakened within a week.

**The control, which is not optional.** ``scripts/verify-test-gates.sh``
control 8 injects the quiet form the project forbids by name -- a JaCoCo
``<excludes>`` added to shrink a report -- and requires two things in sequence:
that ``mvn verify`` **still exits 0 and still prints "All coverage checks have
been met"**, which is the green build the shortcut buys; and that the documented
build command then rejects it, naming the class. ``bash
scripts/verify-test-gates.sh`` now grades **37 assertions, 0 failed**, and the
floor in ``scripts/verify-all-gates.sh`` is raised 33 to 37 so the four new ones
cannot be silently removed.

.. _status-control-wrong-reason:

The control passed for the wrong reason first, and that is the ninth instance
-----------------------------------------------------------------------------

Written as a plain substring search for the class name, control 8's fourth
assertion passed against this line::

    [INFO] Running org.cometgui.domain.secrets.SecretRegistryTest

which surefire prints on **every** build. The assertion would have held with the
census naming nothing at all. It is the project's signature defect -- an
assertion too coarse to see what it claims to see -- committed by tier 1 inside
**the control whose only purpose is to prove a check can fail**, and it was
caught by reading the evidence line printed under the ``PASS`` rather than the
``PASS``. The assertion is now anchored to the census's own indented output
line, verified to match it and not to match the decoy. **A control needs its own
evidence read as carefully as the gate it controls.**

.. _status-only-gates-staleness:

A real gap this work exposed: ``--only gates`` reads evidence it never dates
-----------------------------------------------------------------------------

``scripts/build.sh --only gates`` deliberately **reads** what the main build
already produced -- surefire reports, ``jacoco.xml``, the ArchUnit results --
rather than re-running it. That is sound and documented. What it does not do is
check that what it reads describes the tree in front of it.

Observed live: a ``--only gates`` run on a clean tree reported
``LayeringRulesTest: tests=8 failures=1``, quoting a ``Runtime.exec`` violation
in ``org.cometgui.install.MoRuntimeExecProbe``. That class was **tier 1's own
injection during the Phase 03 sign-off**, reverted correctly sixteen hours
earlier; it is absent from the source tree and from history. The surefire XML on
disk was dated ``2026-09-01 19:08:49`` and had simply never been regenerated.
The full documented command is unaffected -- it runs ``mvn clean verify`` first
-- which is exactly why this had never been seen.

**The harmless direction is the one that showed up; the dangerous one is the
reverse.** A stale report can equally be *green*: if the last recorded run
passed and the code has broken since, ``--only gates`` prints ``ok``. That is a
real measurement, correctly read, describing a tree that no longer exists --
the same family as :ref:`status-class-census-gap`, and it survives a re-run for
the same reason. **Not fixed here**, because it is a second change to a shared
script and this entry is the record that it is known rather than a decision to
live with it. The repair is a freshness assertion: refuse to grade a report
older than the newest class file in the module it describes. It belongs with
whoever next edits ``build.sh``, and before ``--only gates`` is ever used to
sign anything off.

Tier 1 collided with itself, for the third time in this project
----------------------------------------------------------------

Recorded because the pattern is now three-for-three: **every concurrency
collision in this project has been committed by the tier enforcing the rule
against it.** Tier 1 started ``verify-test-gates.sh`` while a ``build.sh --only
gates`` run was still going -- ten minutes of overlap, two Maven-driven
harnesses in one tree sharing ``_build/m2repo``. It produced a transient
``MISSING  cometgui-install: 1 class(es) with code but no jacoco.xml`` that was
gone when the tree was quiet, and cost a re-run to disprove.

A second, smaller self-inflicted trap in the same hour: the chained "wait until
the other harness exits" loop used ``pgrep -f "scripts/verify-test-gates.sh"``
from a command whose **own command line contained that string**, so it matched
itself and would have waited forever. The project's standing warning about
``pkill -f`` matching your own shell has a patient twin.

What it costs, because it is not free
--------------------------------------

``bash scripts/verify-all-gates.sh`` now takes **2985 seconds (49m45s)**, up
from 1962 (32m42s) before this work and from 718 at the session-04 baseline. The
``tests`` control alone went 1370s to 2397s: control 8 runs a full ``mvn
verify`` **and** a full ``scripts/build.sh`` inside the sandbox, because
proving this particular defect requires demonstrating that the ordinary build
stays green before showing the census reject it. Nothing was weakened or
buffered to reduce it, and the two halves are not separable without losing the
point of the control.

That is now the third increase in this suite's cost recorded here, and it is a
standing budget question for the owner rather than an engineering one: it is
paid on every phase sign-off from here to Phase 16.

Final state, at the point Phase 05 is dispatched
-------------------------------------------------

``bash scripts/verify-all-gates.sh``: **11 controls passed, 0 failed**, with
every floor met or exceeded -- license 5, workflows 9, docs 1, traceability 8,
sbom 8, depscan 16, pipeline 24, quality 42, shell 30, **tests 37** and
provenance 24. "Every gate was seen to reject its defect and accept the clean
tree."

The build, at the point Phase 05 is dispatched
-----------------------------------------------

``bash scripts/build.sh`` on a quiet tree: **11/11 stages OK in 927 seconds,
BUILD OK**, ``106 report file(s): tests=1756 failures=0 errors=0 skipped=2``,
census clean in all six modules, and ArchUnit ``8 architecture rule(s) checked,
0 failures`` once the stale report was regenerated.

.. _status-p05-xsd:

Phase 05: where Windows gets the Percolator XSD companions (tier 1, 2026-09-02)
================================================================================

Phase 05 escalated this at decomposition, correctly, as engineering rather than
a ``D-`` item. ``R-TOOL-02`` requires both Percolator XSDs beside the binary.
``specification.rst`` names the ``noxml`` ``.deb`` for Linux and the ``.pkg``
for macOS and is **silent on Windows**, because ``D-002`` option C deleted NSIS
extraction and the NSIS installer is the only Windows artefact that carries
them.

**Decision: Windows fetches the two schemas from the Linux** ``noxml`` ``.deb``.
It redistributes nothing, reverses no owner decision, executes no installer and
changes no platform promise; it fills a silence rather than contradicting the
specification's text, so it needs no revision. Cost is one additional ~1.8 MB
download on a Windows install.

**What makes it safe, verified at tier 1 rather than accepted.** The two files
are byte-identical across artefact kinds *and* versions -- ``sha256`` prefixes
``21204c89…`` (``percolator_out.xsd``) and ``fa50a550…``
(``percolator_in.xsd``) from the 3.06.5 ``.deb``, the 3.07.1 ``.deb`` and the
3.07.1 ``.pkg``. The file Windows would have obtained from the NSIS installer
is the same file it obtains from the ``.deb``.

**And the stakes are lower than the requirement makes them sound.** Checked
directly: the shipped ``percolator_out.xsd`` declares ``majorVersion`` as
``use="required" fixed="2"`` while the 3.07.1 binary writes ``3``, so **the
schema cannot validate that binary's own output unmodified** -- the standing
risk recorded in *Risks currently live*. The XSDs are a provenance and
validation asset, not a runtime prerequisite: Phase 00 proved by execution that
XML output works without them.

**The documentation obligation this creates.** Downloading a Debian package on
a Windows machine is odd enough that a later reader will take it for a mistake
and "clean it up". Phase 05 records in the registry and in
``docs/developer/tool_registry.rst`` why it is done and that the schemas are
platform-independent.

.. _status-p05-upstream-facts:

Three upstream facts Phase 05 established by execution
=======================================================

All three verified independently at tier 1 from the fetched artefacts, because
two of them shape design rather than documentation.

* **A genuine upstream release contains a path-traversal entry.**
  ``rel-3-06-05/percolator-noxml-osx-portable.zip`` has exactly one member and
  it is named ``../my_build/percolator-noxml/src/percolator``. A correct
  ``R-SEC-05`` guard rejects that archive, and the obvious repair -- take the
  basename -- is precisely the weakening this project forbids. Phase 05's
  design resolves it without touching the guard: for ``ZIP`` artefacts **the
  manifest names the member and the destination, and the archive's own path
  never places a file.** That is strictly stronger than sanitising a path, and
  it gives gate item 3 a real upstream artefact rather than a synthetic
  fixture. The traversal guard stays and must still be exercised.
* **Percolator 3.09's Windows artefact is a bare ``percolator.exe``**, 640512
  bytes, not an archive. Any code assuming "Percolator implies ZIP" is wrong.
* **3.09 has no Linux row at all** -- no portable archive; its ``.deb`` needs
  ``GLIBC_2.38`` *and* ``libboost_filesystem.so.1.83.0``, which it does not
  ship. Absent is the honest manifest entry, exactly as ``D-003`` anticipated.

**Two probe assumptions that would otherwise have been re-invented**, both
found by executing rather than reading: on the too-small-fixture failure the
output file **exists and is zero bytes**, so "the file exists" is not a
sufficient probe condition; and Percolator's ``--help`` arrives on **stderr**,
so a probe reading stdout alone sees an empty string. Both are pinned by tests
rather than left in a work log.

**The ``--help`` probe is now disproved by measurement, not by argument.** The
portable ``noxml`` binary and the ``noxml`` ``.deb`` binary print
**byte-identical** help, 17928 characters each, both listing ``--xmloutput``
and ``--decoy-xml-output``. A text probe discriminates nothing.

.. _status-classhash-linenumbers:

Injection protocol, sharpened: a class hash moves on a comment (2026-09-02)
===========================================================================

Phase 05's unit 3 agent reported a figure it could not reconcile rather than
letting it pass: the same baseline class and the same one-line defect produced
injected class hashes of ``210d3e9a…`` for the phase orchestrator and
``a4a570b1…`` for the agent. It named the delta and guessed a different compile
invocation. The instinct was right and the cause was not.

**Measured at tier 1**, on two classes differing by nothing but one comment
line::

    A: 38282631c7a148e6a4ccee8cbaace25b
    B: c3d05ea60179187520be8139ba94f46e   <- one comment line added
    identical with -g:none? YES

``javac`` records a **LineNumberTable** in debug info, so inserting a comment
shifts every line below it and changes the compiled bytes. Stripping debug info
makes the two byte-identical, which proves the difference is line-number
metadata rather than behaviour. ``-Djacoco.skip=true`` is a red herring: JaCoCo
instruments at runtime through a javaagent and does not rewrite
``target/classes``.

**The rule, refined rather than replaced:**

* **"The compiled class hash MOVED" remains mandatory.** It is what proves an
  edit reached the bytecode, and it is the only defence against the eighth
  catalogued shape -- an injection that reaches the source and not the class.
  Nothing about that changes.
* **"Two agents' injected class hashes differ" is NOT evidence of different
  defects.** Line numbers alone move it, so a mismatch between two tiers'
  figures is usually formatting and must not be chased as a discrepancy.
* **The cross-agent comparison is the injected SOURCE hash, or the observed
  failure text** -- the first when two injections must provably be the same
  defect, the second when only the falsified property matters.

*Recorded also for how it surfaced.* The agent reported an unexplained number
instead of dropping it, which is the behaviour this three-tier structure depends
on and the one failure mode none of these gates can catch. It cost one message
to resolve and would have cost a phase to discover late.

.. _status-tenth-shape:

The tenth shape: a hole no coverage or mutation gate can reach (2026-09-02)
===========================================================================

Found by Phase 05's unit 1 agent, confirmed by the phase orchestrator on its own
re-run, and recorded here because it is the first shape in this catalogue that
**the project's strongest automated gates are structurally unable to find**.

**The hole.** A validation rule was missing a conjunct -- it should have refused
a record whose note was blank *and* whose evidence value was not
``UNVERIFIED``, and it tested only the first half. It sat under **100% line,
100% branch and 99.7% mutation coverage** and none of the three could have
found it.

**Why mutation testing is blind to it, which is the general point.** PIT mutates
**the expression that is there**. No mutation operator *adds* a conjunct, so no
mutant of the written rule ever produces ``note.isBlank() && evidence !=
UNVERIFIED``. The evidence that the gate was blind rather than merely quiet is
that the score was **368/369 before and after the repair** -- the number did not
move, because there was never a mutant to kill.

**Where this sits among the other nine.** Shapes 1-6 and 8 are checks that
cannot fail. Shape 7 -- a real measurement over an incomplete population -- is a
check answering the wrong question, and shape 9 is a check answering a question
about the wrong *time*. This one is different again: **the measurement is
correct, complete, current, and about code that was never written.** A gate can
only grade what exists.

**The defence, and it is not another gate.** Grade every rejection over the
axes the rule does *not* depend on, and inject by hand. The repair changed **ten
test classes and no production source**, which is the right shape when the
finding is that the rule was correct and the tests were thin. The orchestrator
then verified the audit was real rather than a patch of the one known defect, by
injecting a **second, unannounced** defect -- ``InstallProgress`` accepting a
negative byte count when the phase is ``FAILED`` or ``CANCELLED`` -- and
watching the newly added phase-axis test catch it too.

**The standing rule this makes explicit for every remaining phase:** a coverage
or mutation figure is evidence about the code that exists. It says nothing about
a condition nobody wrote. Only an adversarial reading of the *requirement*
against the *rule* finds those, which is why tier-by-tier injection is not
redundant with the gates and cannot be replaced by them.

*Related, from the same phase and the same day:* the orchestrator's own
injection into unit 3 -- progress on a **resumed** transfer reported from the
resume point rather than the absolute position, so the bar runs backwards on the
99 MB PDV download the phase document singles out -- **survived 338 tests**,
including 59 downloader tests. It was the twin of a hole the unit had already
found and closed in itself: cancellation had been graded over its axes, progress
over none. Sent back and closed, with the failure text
``expected: <1500000> but was: <16229>``.

.. _status-lock-absent:

The lock nothing takes, and the ruling on it (2026-09-02)
==========================================================

Phase 05 escalated that ``_build/cometgui-maven.lock`` existed while
``grep -rn "flock" scripts/`` returned nothing. Checked at tier 1 and it is
worse than the file: the file is gone (it survives only in a build archive) but
**this document told readers a lock was adopted**, while ``build.sh`` line 217
runs ``mvn clean verify`` at the repository root unprotected. Phase 04 did adopt
one -- in its agents' command lines, which vanished with those agents.

So the protection was **documented, believed and absent**: this project's
signature defect relocated into its own record. Four collisions across three
sessions prove it, and every one was committed by the tier enforcing the rule.

**Ruling, in two parts.** The false sentence is corrected now, because it is the
active harm. ``build.sh`` takes a real ``flock`` **at Phase 05 sign-off, with a
control proving it serialises** -- not mid-phase, because changing the build
under a live phase is the hazard itself, and a lock never seen to block is
exactly what we are complaining about. ``flock`` releases on process death, so
there is no stale-lock hazard.

*A fourth and fifth instance, both tier 1's, recorded because the shape is now
unmistakable.* A "wait until the other harness exits" loop used ``pgrep -f`` with
a pattern its **own command line contained**, so it matched itself and would have
waited forever; and a "is the tree busy" check did the same, reporting BUSY for
hours when only the harness's own shell wrappers matched. The project's warning
about ``pkill -f`` matching your own shell has both an impatient and a patient
twin. **A process check must exclude the checker.**

.. _status-windows-first-execution:

The first Windows execution in this project's history (2026-09-02)
===================================================================

Pull request #1 ran two workflows. **A ``windows-latest`` runner executed
Percolator**, which no machine in this project had ever done: every non-Linux
capability claim until today was inference from byte markers read on Linux.

**Two binaries were exercised and they must never be conflated.**

.. list-table::
   :header-rows: 1
   :widths: 22 39 39

   * - Observation
     - **A** -- NSIS installer payload (XML build), not shipped
     - **B** -- portable ``noxml`` build, **the artefact the product installs**

   * - sha256 / bytes
     - ``044f3957…4691e`` / 804 864
     - ``b9d9bbe8…5f059f`` / 707 072

   * - Started?
     - **No.** Exit ``3221225781`` (``0xC0000135``,
       ``STATUS_DLL_NOT_FOUND``), three invocations, 0 bytes captured each
       time. The loader failed before any application code ran.
     - **Yes.** ``Percolator version 3.07.1, Build Date Jun 20 2024 13:21:08``,
       in all three invocations.

   * - Wrote XML?
     - Nothing; the output file does not exist.
     - **exit 0, 148 272 bytes, 200 ``<psm>`` and 200 ``<peptide>``** parsed
       with an XML parser, against a 200-target/200-decoy PIN. The Linux twin
       produced 200/200 from the same input.

   * - ``--xml-in``
     - Diagnostic absent -- and **correctly refused as INCONCLUSIVE**, because
       the banner was absent from the same capture. Absence proves nothing
       without positive proof the binary started.
     - ``ERROR: Compiler flag XML_SUPPORT was off``, exit 1: a **positive
       observation of a negative capability**, and confirmation that the
       functional probe's discriminator works on Windows.

**Why the job is red, and why that is right.** The verdict is ``INCONCLUSIVE``
(exit 2), not ``NEGATIVE`` (exit 1) and not ``HARNESS FAILURE`` (exit 3). The
driver's exit taxonomy earned its keep: it declined to report "not XML-capable"
about a binary that never started, which is precisely the wrong-cause failure
this project keeps warning phases about.

**What is now observed, and the phrasing is load-bearing.** Percolator 3.07.1's
portable Windows binary is *observed to start and to write Percolator XML on a
GitHub ``windows-latest`` runner (Windows Server 2025, x86-64, administrator,
Visual-Studio-equipped image) on 2026-09-02*. Not *verified*, not *confirmed*,
not "runs on Windows" unqualified.

**What remains unverified, and one caveat is sharper than the transcript's
own.** The zip carries no Visual C++ runtime and the runner's image ships
VC++ 2022, so **the runner supplied it**. The transcript says this "cannot
settle" the question; the sharper reading is that it settles it *negatively* --
a clean end-user machine is still untested, and Phase 05's in-scope VC++ line is
**not** discharged. Also untested: standard-user (the runner was administrator),
consumer Windows 10/11 (this was Windows Server 2025), Windows on ARM, and
macOS entirely. One run, one image version.

**A committed document was disproved by the run**, and the correction is at
``docs/feasibility/windows-artefact.rst``: the payload's own
``xerces-c_3_1.dll`` imports 60 functions from ``MSVCR100.dll`` (Visual C++
2010), which the payload does not ship. The original analysis parsed one file's
imports rather than the transitive closure. **The artefact the product ships is
unaffected** -- its closure carries no xerces -- and the ``noxml`` NSIS
installer's payload is byte-identical to the portable zip's with a
self-contained closure.

.. _status-p01-item6:

Phase 01 item 6: the first half is met, on evidence
----------------------------------------------------

``pull-request.yml`` ran **on a pull request** -- run ``33644055679``, event
``pull_request``, PR #1, conclusion **success**, 1139 s, all 15 steps green,
``146 report file(s): tests=2682 failures=0 errors=0 skipped=1``.

The two **zero-second** steps were audited rather than assumed, because a
zero-second step is where a silent skip hides. Both can go red: the real-tool
integration step asserts that **no** integration test exists yet and fails the
moment one appears (*"PASSED (nothing to run, and that is asserted rather than
assumed)"*), and the dependency scan ran a live canary that found 7 advisories
for a known-vulnerable log4j coordinate. No pull-request step is a stub; the
stubs live in ``nightly.yml`` and exit 70 by design.

The item's second half -- "its failure modes are demonstrated, not assumed" --
is supplied by ``verify-quality-gates.sh`` and ``verify-test-gates.sh``, not by
this green run, and is unchanged.

.. _status-p00-item8-contradiction:

Phase 00 item 8 contradicts itself, and tier 1 will not quietly reword it
-------------------------------------------------------------------------

**This is an escalation to the owner, not a decision.** Item 8 requires the
binary's ``--xml-in`` to **not** answer ``Compiler flag XML_SUPPORT was off``,
and then, since ``D-002`` option C, requires "the same observations" of the
portable ``noxml`` build. But the ``noxml`` build is *defined* by printing
exactly that diagnostic -- item 8's own amendment note says so: *"only
``--xml-in`` -- which the ``noxml`` build refuses by name -- separates the
twins."*

Read literally, **the artefact the product ships can never satisfy item 8**, no
matter how well it behaves; it behaved exactly as the model predicts and a
strict reading still scores it a failure. That is a drafting fault introduced by
the amendment, not a shortfall in the work.

Rewording a gate so that something passes is the one move this project forbids
outright, so it is not done here on tier 1's own authority. The proposal, for
the owner: clause (iii) applies to the **XML** build only; for the ``noxml``
build the required observation is that it **does** print the diagnostic, which
is the positive control the phase already treats it as. Nothing that was proven
becomes unproven; an unsatisfiable clause becomes a satisfiable one that tests
the same fact.

The item's opening verb, "**confirmed** on a Windows runner", also uses a word
the project forbids elsewhere about Windows binaries.

.. _status-p05-findings:

Four findings from Phase 05 that outlive it
============================================

* **A redundant "correct" final value masks every wrong value before it.** The
  downloader ended each transfer by re-reporting the final byte count. Harmless
  and redundant -- and it meant a loop reporting wrong numbers throughout still
  ended on the right one, so any check of the end state passed. That is exactly
  why an injected resumed-progress defect survived 338 tests while
  ``lastByteCount()`` stayed correct. Removed rather than argued equivalent.
  **Phase 08's stage progress and Phase 13's provenance viewer are in its
  path.**
* **A fixture contains what the rule needs; real data contains what the world
  has.** Asserting an ordering rule against the *shipped* manifest rather than a
  fixture surfaced a defect nobody injected: PDV's 99 MB zip and the converter
  JAR offered **twice** on Apple silicon, the second labelled
  ``TRANSLATED_ROSETTA_2`` -- a false statement about a Java program.
* **The eleventh shape: a diagnostic that lies about the value it rejected.**
  The guard fires correctly, its arithmetic is mutation-killed, every
  behavioural test passes -- and the message reads ``400 kept plus 600 received
  is -200``, because the assertion stopped at the opening words. It generalises
  to the ``R-PLAT-03`` loader diagnostic, ``R-PERC-10``'s explanation and every
  provenance field a reviewer reads a year later.
* **Five security protections, none of them held in place.** All five XXE
  hardening calls on the parser that reads an attacker-controlled ``.pkg``
  table of contents could each be deleted with the whole suite green. Two were
  *unprovable as written* because they restate JDK defaults. The repair was a
  shape change, not more tests -- ``harden()`` now forces the safe state over a
  caller-supplied factory set to every unsafe value -- after which each of the
  five fails on its own. **A protection that cannot be observed to matter is
  indistinguishable from one that is absent.**

*And a third form of the same green-for-nothing trap:*
``mvn -Dtest='package.*'`` matches zero tests and **exits 0**. Three agents have
now been caught by a ``-Dtest`` expression that selected nothing -- a wrong
separator, a glob, and a missing ``surefire.`` prefix. **Read the ``Tests run:``
line, never the exit code.**

.. _status-injection-from-outside:

The strongest finding of Phase 05: where a useful injection comes from
=======================================================================

Phase 05's orchestrator injected a defect into every unit it signed off, and
reported the pattern rather than only the results:

  **Every injection chosen from inside its own acceptance conditions bit
  immediately. All three that survived came from outside them.**

The surviving three were found by asking a different question. Not *"is this
rule tested?"* — which an acceptance list already answers — but **"what silent
behaviour does this code have that no condition names?"** That is how a
resumed-transfer progress defect survived 338 tests, how a blank-note rejection
survived 108, and how five XXE protections turned out to be individually
deletable with the whole suite green.

**Why this matters beyond one phase.** An acceptance condition is a statement of
what the author already thought of. An injection drawn from that list tests the
implementation against the author's own imagination, and it will nearly always
fail loudly, which feels like rigour and is not. The defects that survive are
the ones nobody wrote a condition for — and the sign-off tier exists precisely
because it can read the *requirement* against the *code* rather than against the
author's list.

This is the practical companion to :ref:`status-tenth-shape`, which established
that coverage and mutation scores are silent about a condition nobody wrote.
Together they say the same thing from two directions: **the gates grade what
exists; a human adversary must supply what does not.**

*Recorded also because of how it was surfaced.* The phase reported the pattern
against itself, unprompted, while handing over — including that the one gate it
widened which found nothing did so because that unit had already pointed PIT at
its own package before being asked. A phase that reports the shape of its own
blind spots is worth more than one that reports a higher score.

.. _status-suite-was-red:

The gate suite was red for three days, and my own control was the reason
=========================================================================

Phase 05's successor orchestrator re-took ``verify-all-gates.sh`` as its first
act and found **10 controls passed, 1 failed** -- not the 11/0 on record. The
recorded figure was taken at ``96e7da4``, before Phase 05's units landed, and
nobody had re-run it since. Two independent faults, and the second is tier 1's.

**Fault 1 -- an assertion that had become false about correct code.**
``assert_pit_killed_everything`` demanded ``killed == generated`` on the clean
tree. Since 2026-09-02 ``cometgui-domain`` contains one **genuine equivalent
mutant** -- ``ToolVersion.compareTo:214``, ``ConditionalsBoundaryMutator``,
where ``index < width`` becomes ``index <= width`` and compares
``componentAt(width)`` as 0 against 0. Phase 05 unit 1's author argued it in the
code and declined the rewrite that would kill it; tier 1 agrees, because
chasing an equivalent mutant with a test is how a suite acquires assertions
that cannot fail. The assertion's own justification -- *"the clean tree does not
meet the gate"* -- was false: the gate is 80% and the module sits at 99.7%.
**A control that fails on correct code is broken, not strict.**

Replaced by something **stricter**: the survivor set must equal a hand-typed
list *exactly*. A new survivor fails; a survivor that moves class or line
fails; and **a listed survivor that is now killed also fails**, which is what
stops the list becoming a drawer that absorbs regressions. Adding an entry to
make a build pass is forbidden in the note above it.

.. _status-red-for-the-wrong-reason:

**Fault 2 -- my census control was failing for the wrong reason, and that is
why nobody noticed.** On 2026-09-02 the per-class census was landed here "with
a control proving it bites". Controls 7 and 8 damage a POM and require
``build.sh`` to reject it **at the census stage, with the census's own
diagnostic**. Instead the build died three stages earlier, twice over: the
sandbox carried no ``manifests/`` (Phase 05 ships ``manifests/tools.json`` as a
resource) and no ``scratch/phase05/artefacts`` (Phase 05's extraction suites
read the real upstream bytes and **fail rather than skip** without them --
deliberately, since "an extraction suite that stops reading the real containers
stops proving anything"). Thirteen tests failed in every sandbox build.

**The controls still went red, so nothing looked wrong.** That is the whole
lesson: **a control that fails for the wrong reason is worth no more than one
that passes for the wrong reason**, and it is harder to see, because red reads
as working. Tier 1 verified the pass on 2026-09-02 and never asked why the
failure was a failure -- the exact distinction it had spent the week demanding
of every tier below it.

Both repaired by carrying the two inputs into the sandbox, under the rule that
was already written there: *the sandbox carries what the build reads as input*.
A precondition now names an absent mirror directly rather than letting it
present as thirteen test failures. Re-run at tier 1: ``37 assertion(s) passed,
0 failed``, with control 8 quoting ``ABSENT   cometgui-domain: compiled, but
missing from jacoco.xml`` and naming ``org.cometgui.domain.secrets.SecretRegistry``
on the census's own line.

*Two smaller findings from the same report, both about believing a signal.* A
liveness check must exclude **defunct** processes as well as the checker: this
host carries 705 zombies, 345 of them named ``java``, so ``pgrep java`` matches
hundreds of processes that exited hours ago. And a background wrapper's
completion notification reports **the wrapper's** exit code, not the wrapped
command's -- the successor's notification said "exit code 0" while the suite
exited 1, and reading only the notification would have reported a green suite.

.. _status-restore-protocol:

The restore protocol has a hole, found by an agent falling into it
==================================================================

The injection protocol says: *restore with* ``git checkout --`` *and confirm
clean -- do not trust your own backup, since running an inject script twice
overwrites it with the injected version.* Phase 05's unit 6 agent followed that
and lost work, then reported it rather than quietly redoing it.

**The hole:** ``git checkout --`` restores the file to **HEAD**, so it is only
safe when everything you want to keep is already committed. The agent was
injecting into a fix that was still **uncommitted**, so the restore discarded
the defect *and the repair together*. It re-applied the fix and re-ran
everything.

**The protocol, corrected, and the order is the whole content:**

#. **Commit the work first.** An injection is a test of committed code; if the
   subject is uncommitted, commit it before injecting.
#. Snapshot the file separately, then inject.
#. Restore **from the snapshot**, not from ``HEAD``, when the working tree
   holds anything the commit does not.
#. Confirm the tree is clean *and* that the marker greps back out at zero.

Both halves of the original warning still stand -- an inject script run twice
overwrites its own backup, and ``git checkout --`` remains the right restore
for a committed subject. What was missing is that the two failure modes have
**opposite** remedies, so the rule cannot be stated as one instruction.

.. _status-wrong-key:

A recurring defect class: a rule keyed on the wrong attribute of the right idea
===============================================================================

Named by Phase 05's unit 6 agent, and hit three times in one module.

* Dedupe keyed on the version **text** rather than the version.
* A tool offered twice on Apple silicon, the second labelled ``TRANSLATED_ROSETTA_2``
  -- a false statement about a Java program.
* The ``R-PLAT-03`` alternatives list keyed on **version** where it meant
  **row**. Comet 2026.02.2 ships two rows on ``macos-aarch64`` -- the native
  build and the x86-64 build ``D-004`` says runs under Rosetta 2 -- so a failing
  native build **filtered out its own sibling** and told the user
  ``Alternatives: none known`` while a working managed build sat in the manifest.

**Why it hides.** The wrong key is always a *plausible* key: version, platform
and URL are each correct for some question. Every existing test passed all three
times, because no fixture varied the one axis where two rows share the chosen
attribute -- which is why both repairs were graded **against the shipped
manifest** rather than a fixture.

**The discipline.** Record in the code which key was chosen *and which were
rejected and why*. Here URL is the key; version is wrong because one version can
be two rows; platform is wrong in the other direction, because one platform
legitimately carries several versions and keying on it would delete every
alternative there is. One exception was left deliberately -- ``StagedToolProbe``
compares versions because it asks "is this the release the manifest pinned?" --
and was flagged upward rather than converted silently.

Phases 08 to 13 are full of the same shape: matching a PSM to a spectrum, a
provenance record to a run, a result row to a filter.

.. _status-red-symmetry:

A red result can be the harness's fault as easily as a green one can be a lie
=============================================================================

Phase 05's successor orchestrator injected a locale defect, the build went red,
and the red was **its own Checkstyle violation** -- a marker comment pushed a
line past 100 characters. Had it read that red as a result, it would have sent
an agent back for something it had not done.

Its own diagnosis is the useful part: *"I have been applying that rule to green
results and had not been applying it symmetrically."* This project has spent
weeks on green results that mean nothing. The mirror case is rarer and costs
someone else's time rather than your own -- a false accusation against a tier
that did its job. **Read why a build is red with the same suspicion you read why
it is green.**

A near-miss of the same family, caught before it did harm: a ``git diff A..B``
over a range that also spanned tier 1's commits made an agent look as though it
had edited files outside its lane. ``git show --stat <commit>`` answers "what
did this change"; a range answers a different question.

.. _status-document-commit-rule:

"Do not write what the build reads" was too blunt, and I broke it twice more
============================================================================

Tier 1 recorded that rule on 2026-09-02 after a phase agent caught it committing
``STATUS.rst`` inside a build window. It then did the same thing twice more, the
second time committing **specification.rst revision 11** about two minutes after
the Phase 05 orchestrator dispatched a live agent. The orchestrator caught it
and, instead of worrying, **checked**: five test files read ``specification.rst``
-- ``SpecificationScenarioCoverageTest``, ``ProvenanceFormatDocumentationTest``,
``FxUiDriver``, ``AccessibleNameEnumerationUiTest`` and a package doc -- and none
keys on the amended section. Confirmed independently at tier 1: the only
``R-PERC-02`` references in source are Javadoc, and the scenario test keys on
*"Component tests with fake executables"*, untouched. **No hazard
materialised.**

**The orchestrator's refinement is better than my rule and replaces it:**

  *A document-only commit into a live tree is cheap to make and cheap to check,
  and the check is the part that keeps getting skipped.*

So the rule is no longer "never write what the build reads" -- which is
unworkable, since ``STATUS.rst`` is the project's live record and holding every
entry until a phase ends is how findings get lost. It is:

#. Prefer a quiet tree.
#. If you commit a document into a live one, **identify what reads it and
   confirm the change cannot reach those assertions** -- by grep, before the
   next build, not after a red one.
#. If a build then goes red somewhere unrelated, **the document commit is the
   first hypothesis, not the agent.** That is :ref:`status-red-symmetry` applied
   to one's own change, and it is the half most easily skipped, because
   suspecting an agent is cheaper than suspecting yourself.

**Recorded three times over because the repetition is the finding.** An
absolute prohibition that is inconvenient enough gets quietly discarded rather
than followed; a cheap check attached to the inconvenient act survives. This
project has now produced the same lesson about locks, about process checks and
about document commits.

Open decisions
==============

**No decision is open.** ``D-001``..``D-005``, ``D-007`` and ``D-008`` are
closed in full; ``D-006`` and ``D-009`` are answered with a named, deliberate
deferral each. ``D-009`` is answered **provisionally** -- the wording stands, the
underlying question does not -- and ``D-006`` is **partly** answered: the data
is not redistributed and the local fixture is chosen, but the CI fixture set is
deliberately deferred. **No ``D-`` item has
ever been answered by an agent**, and none may be.

On 2026-08-29 the owner answered ``D-002`` **option C**, the ``D-008``
**tool-distribution** half, and directed that **Phase 01 run without a remote
and accept PARTIAL**. On 2026-08-30 the owner answered the last of ``D-008``:
**CometGUI is published at** ``https://github.com/mriffle/CometGUI.git``.

.. list-table::
   :header-rows: 1
   :widths: 12 46 42

   * - ID
     - Question
     - Blocks
   * - ``D-001``
     - **DECIDED**: derive from CasanovoGUI and release CometGUI under
       **GPL-3.0**. The copyleft commitment is accepted; ``R-SEC-01``'s
       no-copying constraint is lifted.
     - --
   * - ``D-002``
     - **DECIDED, including option C**: no source builds; 3.07.1; and the
       binary comes from the **portable ``noxml`` archive** on every tier-1
       platform, because the pout-XML writer is in every 3.05-3.08 artefact.
       Phase 05 does not implement NSIS or ``xar``/cpio payload extraction.
     - --
   * - ``D-003``
     - **DECIDED (2026-08-30)**: carry three -- **3.07.1** (default for
       Limelight runs), **3.09** (current, no Limelight) and **3.06.5**
       (reach; ``GLIBC_2.14``, the lowest floor in the release history). The
       set is an intent, not a per-platform promise: ``R-PERC-01``'s
       artefact-plus-probe test decides what each machine is actually offered,
       and 3.09 on Linux may end up unoffered.
     - --
   * - ``D-004``
     - **DECIDED**: Percolator runs under Rosetta 2 on Apple silicon.
     - --
   * - ``D-005``
     - **DECIDED (2026-08-30)**: drive PDV properly, as CasanovoGUI does.
       PDV's control server takes only mzTab, and Comet + Percolator does not
       produce it, so **Phase 11 builds an mzTab exporter** governed by a
       fidelity gate (``R-PDV-03``). No PDV fork and no upstream dependency.
     - --
   * - ``D-006``
     - **PARTLY DECIDED (2026-08-30)**: CometGUI redistributes no spectrum or
       FASTA data, which removes the licence risk in every candidate -- Phase
       00 found it lay in vendoring, never in use. The local fixture is the
       Crux K562 smoke-test pair plus the UniProt human proteome, fetched by
       checksum into gitignored ``scratch/``. **Still open: the trimmed-down
       DDA mzML set for CI**, which the owner deferred.
     - 14 (CI fixture only)
   * - ``D-007``
     - **DECIDED (2026-08-30)**: a local fake endpoint is the default and every
       run uses it, so the suite works offline and never depends on a third
       party being up. It validates uploads against the converter's **real**
       XSD, extracted from inside the JAR. A nightly sandbox slot is wired but
       has no endpoint named; it skips with a stated reason rather than passing
       silently. No test ever touches production.
     - --
   * - ``D-008``
     - **DECIDED, all three parts.** GPL-3.0; tool binaries downloaded from
       upstream by pinned URL and SHA-256, never redistributed; and published
       at ``https://github.com/mriffle/CometGUI.git`` (2026-08-30), which may
       move before release but will always be a GitHub repository the owner
       controls.
     - --
   * - ``D-009``
     - **PROVISIONAL (2026-08-30)**: the line stays ``Copyright (C) 2026 The
       CometGUI authors.`` on every source file. The institutional question --
       whether the University of Washington has a claim on work done there --
       is deferred, not answered. Phase 16 must put it back to the owner
       before release, while changing it is still cheap.
     - 16, before release only

Risks currently live
====================

#. **A scientist with a non-ASCII character in a directory name cannot run this
   product, and the obvious fix does not work.** Found by Phase 04 unit 7 on
   2026-08-31 and reproduced independently by the main orchestrator before being
   routed.

   Where no ``LANG``/``LC_ALL`` is exported, the JDK reports::

       file.encoding    = UTF-8
       native.encoding  = ANSI_X3.4-1968
       sun.jnu.encoding = ANSI_X3.4-1968

   ``sun.jnu.encoding`` is what the JDK uses to turn a Java string into a
   filesystem path, so::

       Path.of("/data/protéomique/x.mzML")
         -> java.nio.file.InvalidPathException: Malformed input or input
            contains unmappable characters: /data/prot?omique/x.mzML

   and the identical call under ``LANG=C.UTF-8`` returns the path unharmed. This
   happens **before any CometGUI code runs**, so no downstream handling recovers
   it. It sits squarely in the Definition of Done -- "a scientist on a clean
   supported computer ... chooses real spectra and a FASTA" -- and non-ASCII
   paths are named explicitly in the specification's own fake-executable list
   ("paths containing spaces and Unicode"). Phase 03's gate item 4 requires them
   to work.

   .. warning::

      **The obvious remedy is inert, and it fails silently.** Adding
      ``-Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8`` to the ``jpackage``
      java-options **does not work**. Measured::

          java -Dsun.jnu.encoding=UTF-8 -Dfile.encoding=UTF-8 ...
            sun.jnu.encoding = ANSI_X3.4-1968     <- flag ignored
            PATH FAILED      = InvalidPathException

      The JVM resolves ``sun.jnu.encoding`` from the process environment
      **before** system properties are applied, so the flag is accepted without
      error and changes nothing. ``-Dfile.encoding=UTF-8`` appears to work only
      because it was already UTF-8, which makes the pair look half-effective.
      Adopting it would produce a clean build, a green gate and a product that
      still cannot open an accented path -- **a remedy that cannot work,
      accepted because nothing goes red.** That is this project's signature
      defect relocated from a test into a fix.

   **What actually works, verified:** the process environment, set *before* the
   JVM starts -- ``LC_ALL=C.UTF-8`` or ``LANG=C.UTF-8``. The requirement is
   therefore "the launcher starts the JVM in a UTF-8 locale", which is a
   **launcher and packaging** problem and cannot be expressed through
   ``--java-options``.

   **Owners.** Phase 16 owns the packaged launcher and should carry this beside
   the ``--enable-native-access=ALL-UNNAMED`` item Phase 02 left it. Phase 14
   owns proving it: a CI runner that does not export a UTF-8 locale silently
   skips the coverage a user's machine needs, so the harness must assert the
   locale rather than inherit it. Phase 04 handled it honestly in-phase -- one
   path test aborts with a diagnostic naming the encoding rather than pretending
   to pass, which is the single skipped test in that module.



#. **Percolator 3.07.1 is the product's default, installed from the portable
   ``noxml`` archive** -- resolved 2026-08-29 (``D-002``, including option C).
   The owner ruled out source builds and required published binaries on all
   three tier-1 platforms; 3.07.1 (``rel-3-07-01``, 2024-06-20) is the newest
   release meeting both. What changed on 2026-08-29 is *which artefact*: the
   pout-XML writer is present in every published 3.05-3.08 build, both twins,
   all three platforms, so the product takes the portable zip and Phase 05
   never writes the NSIS or ``xar``/cpio extractors. Residual risk is now two
   named, bounded engineering items rather than a strategy question: **no
   portable archive ships the XSD companions** (fetch them from the matching
   ``noxml`` ``.deb`` or ``.pkg``), and **the Windows portable zip is the bare
   ``percolator.exe``** and needs a Visual C++ runtime the NSIS installer ships
   and the zip does not. The accepted trade is unchanged: 3.07.1 predates
   3.08's I-spline PEP default and the PEP-greater-than-1.0 fix, carried as
   advisories (``R-PERC-11``).

#. **A capability probe that greps ``--help`` is invalid, and this is not
   optional.** The XML and ``noxml`` twins print identical help text, both
   listing ``-X`` and ``-Z``. ``R-PERC-02`` needs a **functional** probe: run
   the binary over a synthetic PIN of at least 64 target and 64 decoy rows and
   inspect the file it writes. A smaller fixture makes a fully capable binary
   abort on "median decoy score <= score at 1% FDR" and produces a false
   negative. ``scripts/feasibility/probe_xml_capability.py`` is wrong for
   exactly this reason -- it reports "NOT XML-capable" for a binary whose XML
   the Limelight converter consumed -- and must not be copied into the product.

#. **Licensing is settled and is no longer a risk.** CasanovoGUI published
   GPL-3.0 mid-phase; the owner decided on 2026-08-29 that CometGUI is
   **GPL-3.0** and derives from it. Apache-2.0 dependencies (Comet,
   Percolator, the Limelight converter) are compatible in that direction, and
   the bundled Liberica JRE is GPLv2 **with the Classpath Exception**, which
   permits the combination. What remains is compliance work, not choice:
   the ``LICENSE`` file (Phase 01), derivation notices (Phase 02),
   ``docs/citations.rst`` attribution, and a source-availability mechanism
   (Phase 16, dependent on the open publication half of ``D-008``). One cheap
   follow-up: ask the CasanovoGUI authors to confirm the grant covers the
   repository's existing history, since it carries a merged outside
   contribution.
#. **Upstream drift.** PDV moved 2.6.0 -> 2.7.0 between the first and second
   drafts of the specification on the same day. Phase 00 re-verifies
   everything; phase 15 adds the CI job that catches it thereafter.
#. **Percolator's own XSD rejects Percolator's own output.** The shipped
   ``percolator_out.xsd`` fixes ``majorVersion`` at ``2``; the 3.07.1 binary
   writes ``3``. Verified at sign-off -- validating correct output against the
   shipped schema produces ``cvc-complex-type.3.1``. ``R-TOOL-02`` installs
   those XSDs, so any phase that validates against them will fail on good
   data.

#. **No Windows or macOS execution is possible in this environment.** Every
   non-Linux capability verdict in the project is currently inference from
   byte markers. This is the single largest gap in the evidence base and it
   will persist until either a remote enables Windows CI (``D-008``) or a
   person runs the 15-minute checklist in
   ``docs/feasibility/windows-artefact.rst``.

#. **Toolchain installed project-locally.** No longer a risk: Liberica JDK
   25.0.4.1+1, Maven 3.9.16 and OpenJFX Monocle live under ``tools/`` with
   checksum-pinned provenance in ``docs/feasibility/toolchain.rst``. Nothing
   was installed on the host. Two carried findings: **``jpackage`` strips the
   runtime's ``bin/java``** (verified -- the app image has no ``bin``
   directory at all), which matters because CometGUI must launch the Limelight
   converter JAR; and the pinned JDK ships no Monocle and this host has no
   fonts, so a ``Scene`` with any control dies on ``fontFactory is null``
   unless both are supplied.

.. _status-next-action:

Next action
===========

Two things are ready, and they are independent of each other.

**1. Close the two gate items that now need only a pull request.** Superseded in
part on 2026-08-31: the push has happened (:ref:`status-session-04`). ``main``
is pushed -- **at** ``9abfe1b`` **as of 2026-09-01**, session 05 having
published the remaining ninety-one commits (:ref:`status-session-05`) -- and
``windows-percolator-verification`` exists on the remote, so neither item is
waiting on a credential any more. **Both are now waiting on the pull request
being opened**, because that is the trigger both depend on. GitHub has executed exactly one workflow -- a scheduled nightly that
failed on a Phase-15 stub, by design -- and no pull-request workflow has run:

* **Phase 00 item 8** -- a ``windows-latest`` runner executes the checklist in
  ``docs/feasibility/windows-artefact.rst``, which is the first time any Windows
  binary in this project will have been run rather than inferred from byte
  markers. The workflow and its driver exist on branch
  ``windows-percolator-verification`` and are proved falsifiable
  (:ref:`status-residue-01`). Until it passes, every non-Linux capability claim
  stays inference and the manifest must keep saying so.
* **Phase 01 item 6** -- the pull-request pipeline runs on a real pull request.
  *Corrected 2026-09-02: this previously said "the four workflow files", which
  no pull request can ever satisfy.* Only two of the four declare
  ``pull_request``: ``pull-request.yml`` and ``windows-percolator.yml``.
  ``nightly.yml`` is ``schedule`` plus ``workflow_dispatch`` and ``release.yml``
  is ``workflow_dispatch`` only, so the item as written was unsatisfiable.
  They exist and every step is proven locally -- 45 steps across 4 workflows,
  37 executed on this machine -- but GitHub has executed nothing in this
  repository at all: the Actions API reported ``total_count = 0`` runs on
  2026-08-30.

Both are re-verified on every change thereafter, which is why a runner was worth
more than one person spending fifteen minutes.

*Superseded on 2026-09-01: what these two items wait on has narrowed again.*
Merging ``windows-percolator-verification`` into ``main`` rather than opening a
pull request would close **Phase 00 item 8** -- both workflows also declare
``workflow_dispatch``, and GitHub offers that button once the file is on the
default branch, which satisfies "confirmed on a Windows runner". It would
**not** close **Phase 01 item 6**, whose wording is "CI runs the pull-request
pipeline *on a pull request*": merging fires no ``pull_request`` event. The
route is the owner's to choose and is recorded here so the consequence is not
rediscovered. Note also that the merge is the push that first carries
``.github/workflows/`` to ``main``, so it is the one that tests ``workflow``
scope -- a clean ``git push --dry-run`` says nothing about that.

**2. Finish Phase 04, then close the per-class census debt before Phase 05.**
Phase 04 resumed on 2026-09-01 (:ref:`status-session-05`) and is the only phase
live in the tree. When it is signed off, tier 1 owns the two-part census work in
``scripts/build.sh`` and ``scripts/verify-test-gates.sh``
(:ref:`status-class-census-gap`) -- the check *and* a control proving it bites.
Phase 05 is dispatched after that, not before.

Three obligations now carry forward to every remaining phase:

* **``D-001``'s attribution duty**, and the machinery Phase 02 built for it. Any
  file derived from ``Noble-Lab/CasanovoGUI`` retains its copyright notices and
  records the derivation, and the derived file set is held to a **superset** of
  the ordinary Checkstyle rules. Never relax the header configuration or exclude
  a derived file from it to make one pass.
* **The copyright placeholder stays a placeholder.** Every Java file reads
  ``Copyright (C) 2026 The CometGUI authors.`` No agent substitutes a name;
  that is ``D-009``.
* **An assertion whose expected value is computed by the code under test cannot
  fail.** Phase 02's identifier gap (:ref:`status-p02-identifier-gap`) is the second
  instance of this shape in the project. Prove a check by making it go red.

**Pending on the branch, not on ``main``.** ``scripts/verify-all-gates.sh``
still prints that item 6 "needs a git remote, which ``D-008`` withholds", which
has been untrue since 2026-08-30. The correction is committed on
``windows-percolator-verification``, which is now **pushed** and lands when that
branch merges; it is recorded here so it is not fixed twice or mistaken for a
live blocker. The nightly step-ordering finding in
:ref:`status-nightly-masking` belongs to the same branch for the same reason.

For the owner
--------------

**Superseded 2026-08-31. The push is done; what is waiting is one button.**
Session 04 was the first to hold a working GitHub credential, and it had
``workflow`` scope, so the branch carrying four ``.github/workflows/`` files was
accepted. Session 04 was asked to push and then stop, and did. **Session 05
then published ``main`` in full**, at ``9abfe1b``: the push was refused once, by
GitHub push protection rather than by the scope trap below, and the owner
allowlisted the fabricated fixture that triggered it
(:ref:`status-push-protection`). ``windows-percolator-verification`` is
unchanged on the remote at ``38c066c``.

**What remains is to open the pull request** -- the trigger both outstanding
gate items depend on, and the only step nobody has taken::

    https://github.com/mriffle/CometGUI/compare/main...windows-percolator-verification?expand=1

Opening it runs ``windows-percolator.yml`` (Phase 00 item 8 -- the first time a
Windows binary in this project is executed rather than inferred from byte
markers) and ``pull-request.yml`` (Phase 01 item 6's unmet half). Expect the
Windows job to be able to say **no**: its driver exits 1 for a negative finding,
2 for inconclusive and 3 for a harness failure, and the transcript upload
carries ``if: always()`` precisely so a failing verification still returns
evidence. A red result there is a result, not a setback.

Runs appear at ``https://github.com/mriffle/CometGUI/actions``, which is no
longer empty: one scheduled nightly has run and failed on a Phase-15 stub, as
designed (:ref:`status-session-04`).

*Historical, retained so the sequence is auditable:* this section previously
asked the owner to run ``git push origin 82609f0:main`` from ``/workspace``.
Both halves are now stale -- ``82609f0`` is already an ancestor of
``origin/main``, and the checkout is no longer at ``/workspace``. Session 04
performed the pushes.

.. warning::

   **Resolved on 2026-08-31: the push succeeded and this trap did not fire.**
   Retained because the diagnosis cost session 02 several exchanges and the next
   token may differ. Note also that ``git push --dry-run`` does **not** test
   for it: the rejection is served by the remote only once the pack is actually
   sent, so a clean dry run says nothing about ``workflow`` scope.

   **The second push is the one that fails if the token lacks ``workflow``
   scope.** The whole deliverable lives under ``.github/workflows/``, and a
   Personal Access Token cannot create or update anything there without it. The
   rejection names the file and nothing else -- not the token, not the account,
   not the fix -- which is what sent session 02 chasing git identity for several
   exchanges. Remedy: a classic PAT needs the ``workflow`` box ticked; a
   fine-grained token needs *Repository permissions -> Workflows: Read and
   write*. SSH keys and ``gh`` credentials are unaffected, and
   ``git push origin 82609f0:main`` is unaffected -- no commit in it touches
   ``.github/``.

Nothing else is waiting. Every ``D-`` item is answered; two carry a deliberate
deferral, each with a named owner and moment:

* ``D-006``'s CI half -- the trimmed-down DDA mzML set -- belongs before Phase
  14. The local fixture is settled and does not substitute for it.
* ``D-009`` -- the copyright line stays as written, but whether an institution
  has a claim is deferred, not closed, and Phase 16 must raise it again before
  release while changing it is still cheap.

*Answered so far:* ``D-001`` (GPL-3.0, derived from CasanovoGUI, PDV treated as
GPL-3.0); ``D-002`` including option C (portable ``noxml`` archives; Phase 05
re-scoped); ``D-004`` (Rosetta 2); and ``D-008`` in full (GPL-3.0; tools
downloaded not redistributed; published at
``https://github.com/mriffle/CometGUI.git``).

Change log
==========

.. list-table::
   :header-rows: 1
   :widths: 14 20 66

   * - Date
     - Phase
     - Entry
   * - 2026-08-30
     - --
     - **``D-003`` decided, and with it every open decision is answered.**
       Three managed Percolator versions: **3.07.1** (the computed default for
       a Limelight-enabled run), **3.09** (current, for runs needing no
       Limelight) and **3.06.5** (reach -- its portable Linux build needs only
       ``GLIBC_2.14``, the lowest floor in the release history, so it runs on
       older institutional machines 3.07.1's ``GLIBC_2.34`` excludes).
       Recorded as an *intent*, not a per-platform promise: ``R-PERC-01``
       already forbids offering any pair without a verified artefact and a
       passed runtime probe, and Phase 00's evidence says **3.09 on Linux may
       end up unoffered** -- no portable archive is published, the ``.deb``
       needs ``GLIBC_2.38`` and the ``.rpm`` needs Boost libraries it does not
       ship. Absent is honest; a fabricated manifest entry is not. The matrix
       is populated from the functional probe and never from artefact names,
       because Phase 00 proved the names lie. Specification revision 9 adds
       ``R-PERC-12``; Phases 05, 09 and 15 are no longer blocked by any
       decision.
   * - 2026-08-30
     - --
     - **``D-005`` decided: CometGUI drives PDV properly, via a generated
       mzTab.** Inspecting ``Noble-Lab/CasanovoGUI`` settled it. That project
       drives PDV in production from ``PdvLauncher`` and ``PdvController`` --
       ephemeral loopback port, ``/ready`` polling, debounced
       ``/select?ref=<spectra_ref>`` -- but **every launch is** ``denovo-gui
       --mztab``, and its launcher has no pepXML or mzID path at all. The
       control server is real and proven; mzTab is the only door into it.
       Casanovo emits mzTab natively, Comet + Percolator does not, so the owner
       chose to **generate the mzTab**: *"If you need to make a mzTab converter
       for comet+percolator results, do it ... It is essential this is accurate
       and true to the original results."* This beats both routes revision 7
       offered -- **no PDV fork** to maintain, and **no upstream contribution
       on the critical path** -- and lets Phase 11 reuse CasanovoGUI's
       launcher and controller under ``D-001``. Specification revision 8 adds
       ``R-PDV-02``..``R-PDV-05`` and ``AC-VIS-04``/``05``, with fidelity as a
       falsifiable gate: values transcribed rather than recomputed, missing
       fields left explicitly null rather than defaulted, modifications
       compared as parsed values, and export failing loudly on anything it
       cannot represent. The named landmine is ``spectra_ref``: PDV numbers
       spectra by 1-based file position while pepXML carries the instrument
       scan number, so the test must use a file where the two **differ**.
   * - 2026-08-30
     - --
     - **``D-007`` decided: a local fake endpoint, and no test ever touches
       production.** The owner accepted the recommendation. Every run uses a
       local fake, so the suite works offline and never depends on a third
       party being up. What makes it credible rather than a stub is a Phase 00
       finding: the Limelight converter's XSD (65 905 bytes) lives **inside
       the distributed JAR** with no standalone copy, so the fake extracts that
       schema at build time and validates uploads against the **real** one --
       a fake that accepted anything would test nothing. A nightly sandbox slot
       is wired but has no endpoint named; it skips with a stated reason rather
       than passing silently, so supplying a URL later is configuration, not
       authorship. Two cautions Phase 12 inherits, both verified by execution:
       the converter's **exit status is unusable** (it exits 0 with no
       arguments and on unrecognised options), so success is judged from the
       output file; and Percolator's own XSD rejects Percolator's own output,
       which is the general warning that a schema must be proven to accept
       known-good output before it is used as a gate.
   * - 2026-08-30
     - --
     - **``D-006`` partly decided: no data redistribution, and a local fixture
       chosen.** The owner ruled out redistributing spectrum or FASTA data,
       which dissolves the licence question the decision turned on -- Phase 00
       had found the risk in every candidate lay in **vendoring**, never in
       use. The owner then delegated the local-testing fixture and explicitly
       deferred the CI set: *"eventually ... a trimmed down DDA set of mzML
       files ... but I don't want to decide that now."* Chosen under that
       delegation, and recorded rather than left implicit: the two Crux K562
       smoke-test mzML files (pinned by commit) plus the UniProt human
       reference proteome, **fetched by checksum into gitignored** ``scratch/``
       **and never committed** -- the moment one is committed this becomes a
       redistribution decision again. It is the strongest search of the six
       candidates, and the one Phase 00 already drove end to end to a 12.8 MB
       schema-valid Limelight XML. Conditions carried forward: the UniProt URL
       is not immutable, so a checksum mismatch must fail loudly; and these
       very files arrive CRLF-corrupted, on which Comet exits 249, so the
       ``<fileChecksum>`` guard is mandatory for any phase ingesting indexed
       mzML. **The CI fixture half of ``D-006`` stays open** and belongs before
       Phase 14.
   * - 2026-08-30
     - --
     - **History pushed, and ``D-009`` answered provisionally.** All 64 commits
       are on ``https://github.com/mriffle/CometGUI.git`` at ``9115b1c``. The
       push initially failed on a token scope, not on identity: a Personal
       Access Token may not create files under ``.github/workflows/`` without
       the ``workflow`` scope, and Phase 01 added three. Recorded because it
       will recur whenever the workflow files change and the message names
       identity nowhere. **``D-009``:** the owner directed *"for now, just say
       The CometGUI authors"*, so the copyright line is unchanged on all 61
       Java files -- but the institutional question behind it is deferred
       rather than settled, and Phase 16 owns raising it again before release.
       Noted for the record: the published history carries 57 commits authored
       by ``CometGUI spec <claude@ogdb.com>``, the coding harness's injected
       git identity, and 7 by ``Michael Riffle <mriffle@uw.edu>``. Rewriting
       that is now excluded by ``D-008``'s no-rewrite constraint; 37 of the
       commits carry a ``Co-Authored-By: Claude`` trailer.
   * - 2026-08-30
     - --
     - **``D-008`` decided in full: CometGUI is published at**
       ``https://github.com/mriffle/CometGUI.git``. The owner created the
       repository and recorded two qualifications -- it may move before
       release, and it will always be a GitHub repository under the owner's
       control. Two consequences bind every later phase: **the URL is kept in
       one place** rather than scattered through scripts and configuration, so
       a move costs an edit and not a day; and **history is never force-pushed
       or rewritten**, because it is now published. The prohibition on creating
       a remote is lifted, and the rule was corrected in ``CLAUDE.md``,
       ``ONBOARDING.rst`` and ``CONTRIBUTING.rst``, which all stated it
       absolutely. This unblocks the two gate items it had been holding open --
       phase 00 item 8 (no Windows binary has ever been executed anywhere in
       this project) and phase 01 item 6 (no pipeline has ever run on a pull
       request) -- and Read the Docs, which is ``AC-DOC-01``'s second clause.
       Closing them is now phase work, not a decision.
   * - 2026-08-29
     - 01
     - **Phase 01 run and signed off PARTIAL.** Three tiers as designed: one
       phase orchestrator, eight work units, each unit signed off by the tier
       above after re-running its checks. The main orchestrator re-ran all six
       gate items and **injected its own defects rather than re-running the
       phase's negative controls**; items 1-5 PASS, item 6 is half met because
       no git remote exists. Delivered: a twelve-module Maven build a clean
       clone takes to ``11/11 stages OK`` with one command; the full unmodified
       GPLv3 ``LICENSE`` (verified byte-identical to the FSF text); a strict
       Sphinx tree; ArchUnit layering rules with a class census that defeats
       the vacuous pass; JaCoCo and PIT at the specification's own thresholds;
       a traceability report that fails the documentation build; three CI
       pipelines whose stubs exit 70 rather than passing silently; an SBOM; and
       a dependency scanner with a working canary. Caught at sign-off: handoff
       surprise 6 is wrong -- a JavaFX violation in the domain **does** compile,
       so ArchUnit is the first line of defence, not the second. Reported by
       the phase against itself: its own integration commit silently disabled
       the traceability self-test, which nothing in the build ran --
       ``scripts/verify-all-gates.sh`` now exists so it cannot recur, and
       belongs in the nightly pipeline. ``D-009`` raised: no legal entity is
       named on the copyright line, and no agent may choose one.
   * - 2026-08-29
     - 01
     - **Session 02 opened. Two owner decisions taken, then Phase 01
       dispatched.** ``D-002`` **option C**: the product installs Percolator
       from the portable ``noxml`` archives on all three tier-1 platforms,
       because Phase 00 proved the pout-XML writer is present in every
       published 3.05-3.08 artefact and that ``XML_SUPPORT`` gates only the
       pin-XML reader the product never uses. Phase 05 therefore does **not**
       implement ``NSIS_PAYLOAD`` or ``.pkg`` extraction for the binary -- the
       most fragile code the installer was going to contain is unwritten rather
       than written. Two costs replace it and are recorded rather than left to
       be discovered: no portable archive ships the XSD companions, which must
       come from the matching ``noxml`` ``.deb`` or ``.pkg``; and the Windows
       portable zip is the bare ``percolator.exe``, needing a Visual C++
       runtime the NSIS installer ships and the zip does not, whose absence
       must be reported as a loader failure and never as "not XML-capable".
       ``D-008`` **second half**: managed tool binaries are **downloaded from
       upstream by pinned URL and SHA-256, never redistributed**, which keeps
       Apache-2.0 s4 obligations off the release artefacts and makes
       ``R-TEST-08``'s manifest job load-bearing. The owner also directed that
       **Phase 01 run without a remote and accept ``PARTIAL``** on gate item 6.
       Specification amended to revision 7; ``DECISIONS.rst``,
       ``phases/index.rst`` and ``phases/PHASE-05-tool-registry.rst`` updated.
       Phase 01 dispatched to a fresh phase orchestrator.
   * - 2026-08-28
     - --
     - Specification received (revision 1) and committed as the baseline.
   * - 2026-08-28
     - --
     - Deep review completed. Upstream facts verified against live sources;
       Percolator artefact finding recorded; specification revised to revision
       2 and converted to valid RST.
   * - 2026-08-28
     - --
     - Phases 00-16 defined with exit gates; ``ONBOARDING.rst``,
       ``STATUS.rst`` and ``DECISIONS.rst`` created. Project not yet started.
   * - 2026-08-29
     - --
     - Owner ruled out building Percolator from source and required published
       binaries on macOS, Windows and Linux. Verified that **3.07.1**
       (``rel-3-07-01``) is the newest release meeting that with XML: Linux
       build executed here, macOS payload extracted, Windows inferred and
       flagged for Phase 00. ``D-002`` and ``D-004`` decided; ``D-003``
       narrowed. Specification revision 4.
   * - 2026-08-29
     - --
     - Orchestration model made explicit: three tiers (main orchestrator ->
       phase orchestrator -> phase agent), each signing off the tier below by
       running the checks itself. Added ``handoffs/WORKLOG-TEMPLATE.rst``.
   * - 2026-08-29
     - --
     - **Owner decided the licensing question.** ``D-001`` DECIDED: derive from
       CasanovoGUI and release CometGUI under **GPL-3.0**, accepting the
       copyleft commitment; ``R-SEC-01``'s no-copying constraint is lifted and
       Phase 02 may reuse CasanovoGUI source with derivation notices. ``D-008``
       part decided: the licence is GPL-3.0, unblocking Phase 01's ``LICENSE``
       file; **where CometGUI is published, and whether tool binaries are
       redistributed, remain open -- there is still no remote and none may be
       created.** The owner also directed that **PDV be treated as GPL-3.0**,
       resolving its upstream ``LICENSE``/``pom.xml`` contradiction
       conservatively for now; Phase 16 must still get the real answer from
       upstream. Compatibility consequence recorded: Apache-2.0 (Comet,
       Percolator, Limelight converter) is one-way compatible into GPL-3.0, and
       the bundled Liberica JRE is GPLv2 **with the Classpath Exception**, so
       the dependency set raises no conflict. Specification amended to revision
       6.
   * - 2026-08-29
     - 00
     - **Phase 00 run and signed off PARTIAL.** Three tiers used as designed:
       one phase orchestrator, ten work units, each unit signed off by the tier
       above. The main orchestrator re-ran all ten gate items itself; 9 PASS,
       item 8 passes only on its second branch because no Windows runner
       exists. Proven by execution: the full Comet -> Percolator -> Limelight
       path (12.8 MB schema-valid Limelight XML, validated with an independent
       validator shown to be falsifiable), Percolator 3.09 rescoring with zero
       XML, a launchable ``jpackage`` bundle with no JDK on PATH, and TestFX
       headless with working negative controls. Discovered by execution: the
       ``noxml`` builds emit pout XML, which invalidates the help-text
       capability probe and may remove the need for installer payload
       extraction. Caught at sign-off: CasanovoGUI published GPL-3.0
       mid-phase, reframing ``D-001``. Specification amended to revision 5;
       ``D-001``, ``D-002``, ``D-003``, ``D-005``, ``D-006``, ``D-007`` and
       ``D-008`` updated with evidence and costed options. No ``D-`` item was
       answered by an agent.
   * - 2026-08-29
     - --
     - Owner directed that the product use the **latest compatible**
       Percolator rather than a pinned 3.08. Verified that this resolves to
       3.08.1 (``rel-3-08-01``), which has no published binary on any platform;
       that XML is an opt-in compile flag removed in 3.09; that the Limelight
       converter has no non-XML path; and that Bioconda cannot supply an
       XML-capable build. Specification revision 3 replaces pinned defaults
       with computed resolution (``R-PERC-02``, ``R-PERC-10``) and records the
       converter's verified interface (``R-LL-05``). ``D-002``'s recommendation
       changes to building from source.
