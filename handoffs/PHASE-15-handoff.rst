=====================================================================
PHASE-15 handoff -- Version Matrix, Performance and Hardening
=====================================================================

:Phase: 15
:Written: 2026-11-07 by the Phase-15 orchestrator (tier 2)
:Outcome: **All twelve work units accepted** (1-11 and 9a, added mid-phase);
   one defect found at sign-off and repaired by the orchestrator (unit 9),
   two small orchestrator repairs. Gate items 2, 5 and 6 met; items 1 and 3
   met on Linux x86-64 with the other platforms recorded unverified; item 4
   **partial** -- thresholds and baseline met on this shared host, the
   dedicated-runner half **unverifiable**. Recommended grade **PARTIAL**.
   **Not yet signed off by tier 1**; nobody in this phase ran
   ``scripts/build.sh`` or the full ``scripts/verify-all-gates.sh``.
:Records: ``handoffs/PHASE-15-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off names the commands run, the counts
   seen and the orchestrator's own production-code injections.
:Commits: ``c54ba43`` .. ``d54ba90`` and the commit adding this page, 36 in
   all on ``main``, not pushed.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

The product is now checked against every Comet/Percolator combination the
manifest offers on Linux, with real binaries, and every other combination is
named as unavailable or unverified; the mutation gate scores each critical
Java package on its own with ``TIMED_OUT`` counted as detected (``D-014``,
``D-015``), and no forbidden survivor remains; a determinism suite repeats a
pinned run and applies a per-artefact tolerance justified from the tools'
source (no numeric tolerance anywhere); nine performance measurements have
thresholds, each seen red, and a baseline for this host; the nightly manifest
job downloads every pinned artefact and fails on a changed, vanished or
superseded one; a flakiness policy is enforced by a check with nothing
quarantined. The nightly's Phase 15 stubs are replaced; what still needs
spectra in CI waits, honestly red, on ``D-006``.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 6 22 72

   * - Unit
     - Commits
     - What
   * - 1
     - ``a3f2ff5``
     - A failed install records its reason (step and cause, redacted) in
       ``ToolOffer.failureReason``, the Tool Manager row and the console log
       (``ManagedToolManager``, ``ArtefactInstaller``,
       ``InstallFailureListener``).
   * - 2
     - ``466c6f2``
     - ``PercolatorCapabilityProbe`` takes its workspace parent; tests own
       theirs. The ``/tmp`` leftovers came from the PIT stage (reproduced:
       64 before, 0 after).
   * - 3
     - ``d70206a``
     - ``scripts/build.sh`` grades per Java package (``pit_package_grading``),
       ``--gates-pit-root``; ``results.export.*`` added to PIT targets;
       ``tests`` control M (19 assertions, floor 68); control 5 sizes from its
       own sandbox; ``dev-verify.sh --mutation`` repaired.
   * - 4
     - ``eec0003``
     - Audit of all 76 undetected mutants; 2 forbidden killed, 1 real defect
       fixed (``ZipArchiveReader`` Zip64 bound), 17 more killed; the audit
       table in ``testing.rst``.
   * - 9a
     - ``69e1b45`` ``50c788d``
     - ``pdv`` controls 5b and 5e no longer grade by timing (test defects,
       reproduced and fixed).
   * - 5
     - ``de8f979``
     - ``scripts/ci/nightly-manifest-verify.sh`` + ``manifest_verify.py``
       (``R-TEST-08``), empty ``scripts/ci/manifest/not-adopted.json``, new
       control ``manifest`` (30).
   * - 6
     - ``ed5b985`` ``aab1aab`` ``8c77bb3``
     - ``scripts/matrix/`` (``run-matrix.sh``, ``matrix.py``,
       ``check_goldens.py``), opt-in ``cometgui-app/.../matrix/`` classes,
       goldens under ``cometgui-app/src/test/resources/goldens/`` with
       ``REVIEW.json`` (``R-TEST-07``, enforced in ``traceability.sh``).
   * - 7
     - ``83ae276``
     - ``--part determinism``, ``determinism.py``, ``determinism-data.json``,
       the tolerance table with source references in ``testing.rst``.
   * - 8
     - ``765f81f`` ``b8384ad``
     - ``scripts/perf/`` (``run-performance.sh``, ``performance.py``,
       ``performance-data.json``) and seven opt-in ``Performance*`` classes;
       runner profiles; ``NO BASELINE`` exit 4.
   * - 9
     - ``325472d`` ``0a7797c`` + ``b5fa31e``
     - ``scripts/ci/flakiness-check.sh``/``flakiness_check.py``, empty
       ``scripts/ci/quarantine.toml``, control ``flakiness`` (40), PR step;
       ``TestFxUiDriver.clickOn`` aimed on the application thread;
       ``ConsoleFloodUiTest`` measures live heap.
   * - 10
     - ``e0a85da`` ``0c42108`` ``88cd38d``
     - ``scripts/verify-hardening-gates.sh`` (control ``hardening``, 35);
       ``run-pipeline-locally.sh`` runs every step in a ``git archive HEAD``
       sandbox (``pipeline`` floor 24 -> 31).
   * - 11
     - ``998291d``
     - ``testing.rst`` completed, ``platform_support.rst``,
       ``traceability-map.toml``.

**POM change:** one -- ``org.cometgui.results.export.*`` added to PIT's
``targetClasses`` (approved). **Production code changed:** units 1, 2 (a
constructor parameter), 4 (one bounds condition). No threshold, floor,
timeout or survivor eased; floors raised: ``tests`` 49 -> 68, ``pipeline``
24 -> 31; new ``manifest`` 30, ``flakiness`` 40, ``hardening`` 35 --
**24 controls** now.

Exit gate
=========

All evidence Linux x86-64, from runs the orchestrator made itself.

.. list-table::
   :header-rows: 1
   :widths: 5 15 80

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met (Linux); others recorded
     - Matrix over ``scratch/fixture``: **RUN-PASSED 11, RUN-FAILED 0,
       UNAVAILABLE 14, UNVERIFIED 36** (Comet 2026.02.2/2026.03.0 x
       Percolator 3.06.5/3.07.1 full runs, each tool installed and probed,
       PDV and converter, a local 3.09). Every macOS/Windows/aarch64 row
       UNVERIFIED by name, Intel macOS Comet and Linux 3.09 UNAVAILABLE.
       Red on my ``WeightsReader`` injection ("'weights.splits' expected 3,
       observed 2").
   * - 2
     - Met
     - **47 packages, all >= 80%** under ``D-014``/``D-015`` (lowest
       ``tools.pdv.derived`` 89.7%); 0 forbidden survivors after unit 4;
       ``--only tests`` **68 in 5276 s**; my two grading injections red in
       control M.
   * - 3
     - Met (Linux)
     - Determinism over the fixture GREEN, N = 3, with thread-count
       variations and a seed control; no numeric tolerance, every removed
       field tied to a source line; red on my clock-seed injection in
       ``PercolatorRun``.
   * - 4
     - **Partial**
     - 16 thresholds PASS and Comet informational on this host (baseline in
       ``testing.rst`` with host and load); each threshold seen red (mine:
       hashing 112 vs 150 MiB/s, cancellation 5008 vs 250 ms). **The
       dedicated runner does not exist: that half is unverifiable**;
       unknown profiles give ``NO BASELINE`` (exit 4), never green.
   * - 5
     - Met
     - Real job: VERIFIED, 63 records and members, every newest release
       recorded; self-test 30 (checksum altered, gone upstream, new release,
       could-not-verify); my two injections red.
   * - 6
     - Met
     - Policy written; check OK, **register empty**, 279 release-critical
       classes computed; self-test 40; my injections red.

**Harnesses run on the last commit that changed what each reads:**
``hardening`` 35, ``pipeline`` 31 (working tree ``target/`` byte-identical
across it), ``workflows`` 23, ``docs`` 1, ``traceability`` 8, ``manifest``
30, ``flakiness`` 40, ``shell`` 30, ``install`` 95, ``paramui`` 87,
``percolator`` 109, ``results`` 100, ``pdv`` 107, ``limelight`` 130,
``provenanceui`` 96, ``e2e`` 50. **Older than the last change they read:**
``tests`` 68 and ``quality`` 42 at ``d70206a`` (units 4 and 9 changed
sources both copy; ``build.sh`` changed only in its workflows stage and usage
text). Not run this phase: ``license``, ``sbom``, ``depscan``,
``provenance``, ``params``, ``workflow`` (unit 11 ran ``sbom.sh`` and
``dependency-scan.sh`` directly: PASSED). Tier 1's exit-gate run covers
all of them.

What waits on D-006
===================

The real-data half of every new CI job. ``nightly-large-dataset.sh``,
``nightly-determinism-fixture.sh``, ``nightly-performance-fixture.sh`` and
``nightly-windows-raw.sh`` (also a Windows runner) exit 70 through
``stub-lib.sh --waits-for``; each suite takes the fixture as one named input,
``COMETGUI_MATRIX_FIXTURE``, and refuses without it. A CI fixture would need:
two or more DDA mzML files small enough for a 2-4 core runner, a FASTA with
target and decoy PIN rows from a normal tryptic search, a licence permitting
fetch-by-checksum, and a pinned URL. Goldens are keyed by fixture digest, so a
CI fixture adds goldens rather than replacing them. Nothing here commits or
names the local fixture as the CI fixture.

Mutation scores, before and after D-014/D-015
=============================================

Before (old ``build.sh``: KILLED only, per module): every module above 80% --
domain 99.4%, install 98.8%, params-comet 99.6%, params-percolator 100%,
process 89.7%, provenance 99.6%, results 94.0%, tools 94.0%, workflow 100%.
After (per Java package, ``TIMED_OUT`` detected): 47 packages, 47 at or above
80%, 0 below; lowest ``tools.pdv.derived`` 89.7%, ``tools.credentials``
91.8%, ``results.export`` 95.8%, ``tools.process`` 96.2%; 32 at 100%; 169
``TIMED_OUT`` mutants named. The owners' rules did not turn the gate red on
today's tree.

Injections used by the orchestrator
===================================

Each into the named code, anchor gone and ``sha256sum -c`` FAILED while in,
restored and OK: unit 1 -- no failing step reported; the failure label never
shown. Unit 2 -- the probe's workspace never deleted. Unit 3 -- a BELOW
package not counted; the EMPTY rule disabled. Unit 4 -- the Zip64 walk bound.
Unit 5 -- digests compared by prefix; releases sorted oldest first. Unit 6 --
``WeightsReader`` keeping two splits. Unit 7 -- the seed from the clock in
``PercolatorRun``. Unit 8 -- two wasted SHA-256 passes; no polite stop on
cancel. Unit 9 -- a retry on the same line as ``maven.test.failure.ignore``
(**passed the check: defect, repaired**), ``@Disabled`` on a real-binary
test, ``@DisabledOnOs(LINUX)``. Unit 10 -- the performance grader unable to
fail and the determinism grader calling differences tolerated (both caught by
``hardening``).

Incomplete, deferred, residue
=============================

* **Platform**: nothing ran on macOS or Windows; Comet ``-p``/``-q``
  fixtures are linux-x86-64 only.
* **Item 4's dedicated runner** (what it needs is in ``testing.rst``).
* **``org.cometgui.workflow.*`` not in PIT's targets** -- escalated below.
* **Product gaps found, not fixed** (product changes): the merged-PIN row
  count is not checked by the product (only the matrix caught a dropped row);
  PSM identifiers carry the absolute run path; keyboard navigation in the
  Results run chooser opens every run it passes; retained GUI heap creeps
  about 0.6 MiB per run opened; the probe's words for a non-zero exit omit the
  status (so a failed install's reason does too); a failed install's reason is
  lost on restart; ``CometCapabilityProbe`` leaves workspaces in ``/tmp`` under
  PIT, as the Percolator probe did; ``SecretToolCredentialStore`` does not
  cancel ``secret-tool`` when interrupted; ``ZipArchiveReader`` throws an
  unchecked exception on an extra-field length past the directory.
* **Open, deterministic**: ``KeyboardOnlyNavigationUiTest`` fails on a
  machine with Comet in the application-data folder.
* Percolator's thread count reorders fully tied rows; why its pre-sort order
  follows the thread count is not traced.
* ``check-doc`` compares field names, not the regular expressions behind
  them; the flakiness check's harness-retry detection is pattern-based.
* ``scripts/ci/integration-tests.sh``'s header comment is stale.

Decisions encountered
=====================

No ``D-`` item was answered. ``D-006``'s open half shaped every CI job above.
Design decisions P15-1 .. P15-5b are in the work log; tier 1 should confirm
**P15-1** (a "critical package" is the Java package of each mutated class,
not a ``targetClasses`` entry) and **P15-4** (goldens hold derived results from
the local fixture -- counts, digests, identifiers -- keyed by fixture digest).

Escalated (none answered here)
==============================

#. **``org.cometgui.workflow.*`` in PIT's targets** (``R-TEST-02``: command
   builders, stage invalidation). Measured at more than 2 h 45 min of PIT,
   unfinished; not added under the build-economy rule. A cost decision.
#. **AC-TST-02**: whether ``cometgui-workflow`` and ``cometgui-results`` are
   "core domain logic" under the 90/85 coverage rule; no record decides it.
#. **Six criteria keep a planned, phase 15 entry** (AC-TST-01, -02, -03, -10,
   -11, AC-RES-10) for their unmet halves; the generator fails
   ``PLAN-PHASE-PASSED`` if Phase 15 is recorded PASSED. Correct for PARTIAL;
   tier 1 to re-home or keep.
#. The product gaps listed above, for routing.
#. **Cost**: new controls ``manifest`` about 1 s, ``flakiness`` about 40 s,
   ``hardening`` about 10 min; ``pipeline`` grew to about 28-35 min (now in a
   sandbox, the real nightly steps included); ``build.sh`` grew only by the
   flakiness check (about 2 s) and ``results.export`` in PIT (no measurable
   change).

Surprises
=========

* The owner's per-package rule changed no verdict today; the lowest package
  sits at 89.7%.
* The ``pipeline`` control had silently started writing the working tree once
  the nightly steps became real (units 6-8); it crashed a build beside it
  before unit 10 sandboxed it.
* The old pipeline's no-test-bridge release step had been inspecting a
  leftover archive from 2026-10-23.
* ``build.sh --help`` had run its own usage text as commands since Phase 01.
* GitHub had in fact run ``pull-request.yml`` once (PR #1) and the nightly 68
  times; documents saying it had run nothing were stale.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/fixture``, ``scratch/phase05/artefacts``,
``scratch/phase06/artefacts`` and ``scratch/percolator/3.09`` are present and
the host has network to GitHub and ``api.github.com``; run
``bash scripts/build.sh`` and then the full ``bash scripts/verify-all-gates.sh``
(24 controls; ``hardening``, ``manifest`` and ``flakiness`` new) -- **one
after the other, never with a Maven build beside them** -- and the version
matrix with ``COMETGUI_MATRIX_FIXTURE=scratch/fixture``; then decide the
escalations above. Push after sign-off, then pause (the owner's instruction).
