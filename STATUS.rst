==============
Project Status
==============

:Project: CometGUI -- Comet + Percolator desktop workflow
:Updated: 2026-08-30
:Updated by: Main orchestrator, session 02 (D-002 option C and all of D-008
   decided; Phase 01 run and signed off PARTIAL; D-009 raised)
:Current phase: 01 -- PARTIAL, signed off. Phase 02 is next and unblocked.
:Overall: The repository, build and every quality gate now exist and have each
   been seen to fail on a deliberate defect. Phases 00 and 01 are both PARTIAL
   for the same reason -- no git remote -- which ``D-008`` resolved on
   2026-08-30. Both residues are now closable by phase work.

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
     - Revision 7
     - Revision 7 acts on the ``noxml`` discovery: the artefact section is
       rewritten around portable archives, and ``R-TOOL-01``, ``R-TOOL-02``,
       ``R-PERC-02`` and the ``D-008`` entry are amended. Revision 6 decided
       licensing; revision 5 recorded Phase 00's findings without acting on
       them. Passes ``sphinx-build -n -W``.
   * - ``ONBOARDING.rst``
     - Complete
     - Read-first document for any orchestrating agent.
   * - ``phases/`` (00-16)
     - Complete
     - Scope, deliverables and exit gate per phase. 00 and 01 signed off
       PARTIAL, 05 re-scoped by ``D-002`` option C.
   * - ``DECISIONS.rst``
     - 2 open, 5 decided, 2 partial/provisional
     - ``D-001``, ``D-002`` (including option C), ``D-004`` and ``D-008``
       (all three parts) and ``D-007`` decided; ``D-009`` provisional and
       ``D-006`` partly decided. ``D-003`` and ``D-005`` remain open.
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
       ``verify-all-gates.sh`` (9 controls, 123 checks) and four gate
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
     - NOT STARTED
     - --
   * - 03
     - Process service
     - NOT STARTED
     - --
   * - 04
     - Hashing and provenance core
     - NOT STARTED
     - --
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

Open decisions
==============

Two items are open: ``D-003`` and ``D-005``. ``D-007`` and ``D-008`` are closed
in full. ``D-009`` is answered **provisionally** -- the wording stands, the
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
     - Which *additional* Percolator versions to carry beyond 3.07.1, for
       users who do not need Limelight. Widened by the ``noxml`` finding:
       3.06.5's portable Linux archive has the lowest glibc floor found
       anywhere (``GLIBC_2.14``) and would reach hosts 3.07.1 cannot.
     - 05, 09, 15
   * - ``D-004``
     - **DECIDED**: Percolator runs under Rosetta 2 on Apple silicon.
     - --
   * - ``D-005``
     - PDV baseline only, or enhanced control mode?
     - 11
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

Next action
===========

Two things are now ready, and they are independent of each other.

**1. Close the two gate items ``D-008`` was holding open.** The remote exists as
of 2026-08-30, so both residues are ordinary phase work rather than blocked
items:

* **Phase 00 item 8** -- a ``windows-latest`` runner executes the checklist in
  ``docs/feasibility/windows-artefact.rst``, which is the first time any Windows
  binary in this project will have been run rather than inferred from byte
  markers. Until it passes, every non-Linux capability claim stays inference and
  the manifest must keep saying so.
* **Phase 01 item 6** -- the three workflow files run on a real pull request.
  They exist and every step is proven locally; GitHub has simply never executed
  them.

Both are re-verified on every change thereafter, which is why a runner was worth
more than one person spending fifteen minutes.

**2. Run Phase 02** (``phases/PHASE-02-app-shell.rst``). Its dependency, Phase
01, is signed off; nothing blocks it. Two obligations it inherits and must not
drop:

* **``D-001``'s attribution duty.** Any file derived from
  ``Noble-Lab/CasanovoGUI`` retains its copyright notices and records the
  derivation. ``CONTRIBUTING.rst`` says how: a second Spotless file set and a
  second Checkstyle execution with their own header file -- **extending** the
  header configuration, never relaxing or excluding it to make a derived file
  pass.
* **The copyright placeholder stays a placeholder.** Every Java file reads
  ``Copyright (C) 2026 The CometGUI authors.`` No agent substitutes a name;
  that is ``D-009``.

For the owner
--------------

Two items are genuinely open, and each blocks a phase that has not started:
``D-003`` (which additional Percolator versions to carry) and ``D-005`` (how
deep the PDV integration goes).

Two more are answered but not closed, and both are deferrals the owner made
deliberately rather than gaps:

* ``D-006``'s remaining half -- the trimmed-down DDA mzML set for CI -- belongs
  before Phase 14. The local fixture is settled and does not substitute for it.
* ``D-009`` -- the copyright line stays as written for now, but the
  institutional question behind it is deferred, not closed, and Phase 16 must
  raise it again before release, while changing it is still cheap.

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
