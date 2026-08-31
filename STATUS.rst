==============
Project Status
==============

:Project: CometGUI -- Comet + Percolator desktop workflow
:Updated: 2026-08-30
:Updated by: Main orchestrator, session 03 (specification revision 10; Phase 00
   gate item 8 amended and strengthened; the Windows verification harness
   prepared and signed off; **Phase 02 run and signed off PASSED**)
:Current phase: 02 -- **PASSED**, signed off 2026-08-31. Phase 03 is next and
   unblocked. Phases 00 and 01 remain PARTIAL, both awaiting a push only.
:Overall: The repository, build and every quality gate exist and have each been
   seen to fail on a deliberate defect. Phases 00 and 01 stay PARTIAL for the
   same reason they always were, but the reason has changed shape: ``D-008``
   supplied a remote on 2026-08-30, and what now blocks both is simply that **no
   session has had a push credential**. The Windows verification harness is
   written, falsifiable and locally verified; GitHub has still executed nothing
   in this repository. **Phase 02 passed on 2026-08-31** -- the first phase to
   reach PASSED rather than PARTIAL -- after sign-off found and returned one
   real gap: the shell's "stable" identifiers were not pinned, and the whole
   build stayed green while one changed. See :ref:`status-residue-01` and
   :ref:`status-p02`.

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
     - IN PROGRESS
     - Dispatched 2026-08-31 (session 04) to a fresh phase orchestrator,
       concurrently with 04 on disjoint paths. Not yet signed off.
   * - 04
     - Hashing and provenance core
     - IN PROGRESS
     - Dispatched 2026-08-31 (session 04), concurrently with 03. The phase
       table says ``Depends on: 01, 03``; the ordering notes in
       ``phases/index.rst`` say 03 and 04 are independent after 01. The
       ordering note is the one being acted on. Not yet signed off.
   * - 05
     - Tool registry and installer
     - NOT STARTED
     - --
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
   disjoint. A ``flock`` around Maven invocations is the cheap fix and Phase 04
   adopted one.
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
is pushed at ``e97d863`` and ``windows-percolator-verification`` now exists on
the remote, so neither item is waiting on a credential any more. **Both are now
waiting on the pull request being opened**, because that is the trigger both
depend on. GitHub has executed exactly one workflow -- a scheduled nightly that
failed on a Phase-15 stub, by design -- and no pull-request workflow has run:

* **Phase 00 item 8** -- a ``windows-latest`` runner executes the checklist in
  ``docs/feasibility/windows-artefact.rst``, which is the first time any Windows
  binary in this project will have been run rather than inferred from byte
  markers. The workflow and its driver exist on branch
  ``windows-percolator-verification`` and are proved falsifiable
  (:ref:`status-residue-01`). Until it passes, every non-Linux capability claim
  stays inference and the manifest must keep saying so.
* **Phase 01 item 6** -- the four workflow files run on a real pull request.
  They exist and every step is proven locally -- 45 steps across 4 workflows,
  37 executed on this machine -- but GitHub has executed nothing in this
  repository at all: the Actions API reported ``total_count = 0`` runs on
  2026-08-30.

Both are re-verified on every change thereafter, which is why a runner was worth
more than one person spending fifteen minutes.

**2. Run Phase 03** (``phases/PHASE-03-process-service.rst``). Its dependencies,
Phases 01 and 02, are both signed off and no decision blocks it. Phase 02 leaves
it ``ProcessRunner`` and ``ToolCommand`` as ports ready to implement, and one
question it deliberately did not answer: **where the shared
``BoundedMessageLog`` lives** once the process service writes to the log the UI
reads. Phase 04 (provenance core) is independent of 03 after 01 and may run
alongside it.

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
accepted. ``main`` is at ``e97d863`` on the remote and
``windows-percolator-verification`` exists there. Session 04 was asked to push
and then stop, and did.

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
