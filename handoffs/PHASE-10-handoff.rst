=====================================================
PHASE-10 handoff -- Results Model and UI
=====================================================

:Phase: 10
:Written: 2026-10-09 by the Phase-10 orchestrator (tier 2)
:Outcome: **All twelve work units accepted** (units 1-11 and the ``D-013``
   unit tier 1 added; none sent back whole; four orchestrator repairs, each
   recorded). Every exit-gate item is met **on Linux x86-64**, the only
   platform any of it has executed on. Recommended grade **PARTIAL, on
   platform residue alone** (see `Incomplete, deferred, residue`_). **Not yet
   signed off by tier 1**, whose ``scripts/build.sh`` and full
   ``scripts/verify-all-gates.sh`` run has not happened; nobody in this phase
   ran either, by the owner's build-economy rule.
:Records: ``handoffs/PHASE-10-worklog.rst`` -- **this page is the map; the work
   log is the proof.** Every sign-off there names the commands run, the counts
   seen and the injections made into production code, with their failure text.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

The Results section is live. A run's PSM and peptide tables (target and,
where written, decoy) are opened through one store contract: in memory up to
100 000 rows, **disk-backed above it** (an off-heap mapped index over the
read-only raw file, external sorts, rows read back by offset through the one
reader). The interface binds a ``TableView`` to **one page of at most 200
rows**, never to every row. The PSM and peptide q-value filters are one shared
state (the Percolator section's too), default 0.01 and 0.01, inclusive,
refusing anything outside [0, 1]; changing one re-queries counts and the page
and launches nothing. All four counts (total, passing, failing, unknown
q-value) are always shown, and the unknown category is one rule on screen and
in the export. Exports are new files under the run's ``exports/`` with a JSON
sidecar (run ID, cutoff, category, counts before and rows after, version) and
an ``export.written`` event in the run's event log; raw files are only ever
read. The learned feature weights view shows per-split weights, mean signed,
mean absolute, population SD, sign consistency and rank, with the split count
read from the file. ``finalise-results`` is planned with Percolator and
``finalise-provenance`` is ordered after it again. A 1 000 000-row fixture,
generated first by a committed Python script, runs the store in a ``-Xmx64m``
child JVM where ``readAll`` dies of ``OutOfMemoryError``. Percolator is now
always passed ``--no-analytics`` where the probe observes it (``D-013``). A new
harness, ``scripts/verify-results-gates.sh`` (``results``, 100 controls,
about 9.5 minutes), proves every item fails on a production defect.

What was built, and where
=========================

.. list-table::
   :header-rows: 1
   :widths: 7 30 63

   * - Unit
     - Commits
     - What
   * - 1
     - ``e642695``
     - ``scripts/fixtures/large-results-fixture.py`` (stdlib Python,
       deterministic, ``--self-check``) writing ``scratch/phase10/large/``:
       1 000 000 PSM and 400 000 peptide rows, four source files, ten unknown
       q-value kinds, a manifest of generator-computed counts; test-side
       independent counter, locators with pinned SHA-256s for the fixture
       and the K562 outputs in ``scratch/scientific-path`` (``awk``-pinned
       counts).
   * - D-013
     - ``47c55c4`` ``c5973fd``
     - ``ToolCapability.NO_ANALYTICS_OPTION``, ``PercolatorOption.NO_ANALYTICS``;
       the probe observes it on its first run and passes it on every later
       one; the builder emits it whenever probed (a ``NotEmitted`` sentence
       otherwise, never a refusal); probe generation 2 -> 3; ``percolator``
       controls A1-A3, floor 94 -> 109.
   * - 2
     - ``91b7ad9`` (+ ``a3e2992``)
     - ``results.filtering.store``: ``ResultStore``, ``ResultQuery``,
       ``ResultPage``, ``ResultSort``, ``Category``, ``RowKey``,
       ``TableKind``, ``TextFilter``, the in-memory store;
       ``parser.SpectrumReference``; ``QValue`` extremes; the abstract
       ``ResultStoreContract``.
   * - 3
     - ``c4a0acc`` ``028b6de``
     - ``DiskResultStore``, ``DiskIndex``, ``SortFile``, ``RawIdentity``,
       ``IndexProblem``; ``ResultTableReader`` on ``Utf8Lines`` with byte
       offsets; ``QValueFilter.classify(Status, double)`` the one
       comparison; ``positionOf``; ``IN_MEMORY_ROW_LIMIT`` = 100 000; the
       child-JVM budget test.
   * - 4
     - ``7498a83``
     - ``parser.WeightsSummary``, ``FeatureWeights``, ``SignConsistency``.
   * - 5
     - ``aeb184c`` ``2c48ceb``
     - ``RunLayout`` ``results/``, ``results/index/``,
       ``results/view-state.json``, ``exports/``; ``workflow.storage``
       view state; ``ResultSteps.FinaliseResults``; the restored step order.
   * - 6
     - ``9965432`` ``f5d8088``
     - ``results.export`` (``ResultExporter``, sidecars, weights export);
       ``ProvenanceEventType.EXPORT_WRITTEN``; the module edge
       ``cometgui-results`` -> ``cometgui-provenance``.
   * - 7
     - ``1a9bd02`` ``68984c2`` ``58da46a`` (+ ``c7ae61e``)
     - ``ui.viewmodel.results``: ``DisplayFiltersViewModel`` (the one filter
       state), ``ResultsPort``, ``ResultTableViewModel`` (paged, selection
       by key), ``WeightsViewModel``, ``ResultsViewModel``.
   * - 8
     - ``6ff1946`` ``e284b60``
     - ``ui.controls.results.ResultsPane``, ``WeightsPane``; 34 ``UiIds``;
       app ``ProjectResultsPort``, ``SessionEngine.executingRuns``, one
       shared hasher; workflow ``RunResultFiles``; GUI gates 1, 2, 6.
   * - 9
     - ``f83558f`` ``c22aa45`` ``c9c2a50``
     - GUI gates 3, 4, 5, 7, 8; the real run's results in the section; a
       paging-focus defect fixed in ``ResultsPane``.
   * - 10
     - ``c42113c``
     - ``docs/results.rst``, ``docs/learned_feature_weights.rst``,
       ``docs/developer/results_model.rst``; stale statements; traceability
       for AC-RES-01..04, 08, 09, 10.
   * - 11
     - ``35961b3`` ``2f88c02`` (+ ``aea66af``)
     - ``scripts/verify-results-gates.sh``, registered as ``results`` (floor
       100).

Orchestrator commits: ``fe8e643`` (work log), a sign-off commit per unit, and
the four repairs: ``a3e2992`` (the store contract's paging loops hung on an
empty page instead of failing), ``c7ae61e`` (no test had exactly two spectrum
files, so "mandatory above two" stayed green), ``aea66af`` (control 8b's
diagnostic pinned to one table while the contract iterates in ``Set.of``
order), and unit 9's own product fix above.

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-domain`` (``ToolCapability.NO_ANALYTICS_OPTION``, ``RunLayout``
paths); ``cometgui-tools`` (probe and builder, ``D-013``); ``cometgui-install``
(probe generation 3); ``cometgui-provenance`` (``EXPORT_WRITTEN``);
``cometgui-workflow`` (``ResultSteps``, view state, ``RunResultFiles``, plans);
``cometgui-ui`` (``PercolatorViewModel`` delegates its filters, ``ShellView``,
``SectionArrivals``, ``UiIds``); ``cometgui-app`` (composition);
**``cometgui-results/pom.xml`` gained one dependency, ``cometgui-provenance``**
(P10-7; no cycle; ``quality`` green); ``scripts/verify-percolator-gates.sh``
and ``scripts/verify-all-gates.sh`` (additive). No PIT configuration changed.

Exit gate
=========

Every check below was run by me; the work log has each injection's failure
text. All evidence is Linux x86-64.

.. list-table::
   :header-rows: 1
   :widths: 5 13 82

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met
     - ``QValueFilterTest`` (defaults, range, inclusive), ``ResultStoreContract
       .inclusiveAtTheCutoff`` (both stores), ``DisplayFiltersViewModelTest``,
       ``ResultsFiltersUiTest`` (0.01 in both sections; 3/5/6 passing at
       0.0099999/0.01/0.0100001; 1.5, -0.1, abc, 0,01 refused on screen, no
       count changes; filters independent). Red: ``results`` 1a-1d; my unit
       8 injection (peptide field committing to the PSM filter).
   * - 2
     - Met
     - ``ResultsNoProcessUiTest``: the app's one ``ProcessRunner`` wrapped and
       recorded; eight filter changes each change the counts; launches
       before = after = none; every file under ``outputs/`` keeps its
       SHA-256. ``InputKind`` display filters are declared by no step
       (Phase 08). Red: ``results`` 2 ("the one process runner was called
       while the filters changed").
   * - 3
     - Met
     - ``ResultsCountsUiTest``: the real K562 3.07.1 tables (3897 target
       PSMs, 2985 peptides, decoys) at 0, 0.005, 0.01, 0.05 and 1, equal to
       ``awk`` pins and a test-side ``BigDecimal`` counter; also at the
       run's own smallest q-value (599 PSMs exactly on the cutoff) and the
       64-row tables' 17 rows at exactly 0.0588235. Model level: the store
       contract over 22 tables, both stores; the large fixture against the
       generator's manifest. Red: ``results`` 3a-3c. **Note:** the real
       first-1000-records search behind ``RealRunUiTest`` has no row at or
       below 0.01; the K562 Phase 00 outputs carry the gate.
   * - 4
     - Met
     - ``ResultsExportUiTest.theRawOutputsAreUntouched`` (after filter
       changes and eight exports through the buttons: size, mtime, SHA-256
       of every file under ``outputs/`` unchanged; new paths only under
       ``exports/``, ``results/``, ``events.log``),
       ``TableExportGateTest``, ``ResultStoreContract.rawFileUntouched``,
       ``FinaliseResultsTest`` and the real-run tests (raw outputs
       identical across ``finalise-results``). Phase 09 gate 9's export
       half is re-proved by the same tests. Red: ``results`` 4a, 4b.
   * - 5
     - Met
     - ``ResultsExportUiTest``: exports at 0.05 through the interface; the
       sidecar read back with test-side JSON: run ID, cutoff, four
       before-counts, rows written (1171 K562 passing PSMs = the ``awk``
       pin), the app's version; one ``export.written`` event. Model:
       ``TableExportGateTest`` (22 tables x 4 cutoffs x 4 categories). Red:
       ``results`` 5a, 5b; my unit 6 injection (rows written + 1).
   * - 6
     - Met (Linux; local budgets)
     - ``DiskStoreBudgetTest``: a child JVM at ``-Xmx64m`` opens the
       1 000 000-row fixture through the factory (disk chosen), counts at
       eight cutoffs equal the manifest, sorts, pages and a text filter each
       within budget (cold open 2.2 s of 8 s; peptide sort 2.7 s of 10 s);
       ``readAll`` in the same budget dies of ``OutOfMemoryError``.
       ``ResultsLargeFixtureUiTest``: the table never holds more than 200
       items (a listener records the maximum), counts equal the manifest,
       open 3.3 s, filter 0.7-1.0 s, heap growth 3 MB of 64 MB. Red:
       ``results`` 6a (child dies OUT_OF_MEMORY), 6b (items accumulate: 400
       of 200), 6c. Budgets were measured on this host; the specification's
       nightly thresholds are Phase 15's.
   * - 7
     - Met
     - ``WeightsSummaryIndependentTest`` (Python ``Fraction``/``decimal``
       values recorded in ``WEIGHTS-SUMMARY.txt``; K562 3.07.1 and 3.09,
       checked-in, 2- and 4-split constructed files),
       ``ResultsWeightsUiTest`` (every cell on screen against a test-side
       ``BigDecimal`` computation; sort by mean absolute gives rank order;
       weights export at full precision). My own Python: lnrSp rank 1, mean
       -0.336333, SD 0.079189. Red: ``results`` 7a-7e.
   * - 8
     - Met
     - ``ResultStoreContract.unknownKindsInTheirOwnCategory`` (both stores),
       ``TableExportGateTest``, ``ResultsExportUiTest`` (the unknown count on
       screen equals the independent count; the unknown category shows
       exactly those rows over three pages; its export holds exactly them
       and the sidecar's unknown count equals the screen's; a passing
       export holds none). Red: ``results`` 8a, 8b. The harness grades the
       shared predicate and the exporter, not the pane's label alone.

**Harness runs on the final tree** (each run by me, on the last commit that
changed what it reads): ``results`` **100** (562 s), ``percolator`` 109,
``workflow`` 110, ``paramui`` 87, ``shell`` 30, ``quality`` 42, ``provenance``
24, ``install`` 95, ``docs``, ``traceability`` 8 -- each PASS. ``--only
tests``: see `The deferred tests harness`_.

.. _p10h-tests:

The deferred tests harness
==========================

Before it, every module's PIT report was regenerated exactly as
``scripts/build.sh`` does (``mvn -B -o -Dcometgui.pit.threads=16
test-compile org.pitest:pitest-maven:mutationCoverage`` at the root; BUILD
SUCCESS), because the units' targeted PIT runs had overwritten several
reports (Phase 08's trap). Per module: domain 673 (672 killed), provenance
775/775, process 175 (172), tools 645/645, install 1412 (1401),
params-comet 1763 (1760), params-percolator 145/145, **results 943 (931) in
42 minutes 27 seconds**, workflow 242/242.

``bash scripts/verify-all-gates.sh --only tests``, run detached on
``66afdad`` (the last code commit) and watched to its exit line: **PASS, 37
assertions in 13 778 s (229 minutes 38 seconds)** -- up from 7490 s at Phase
09. The growth is the results module inside the sandbox's ``build.sh`` (its
tests roughly doubled and its PIT now takes about 42 minutes). Tier 1 must run
it detached; at nearly four hours it no longer fits any single watch window.

Every Phase 10 test that reads outside its module reads only under
``scratch/`` (``phase10/large``, ``scientific-path/percolator-3.07.1`` and
``-3.09``), which the ``tests`` sandbox links whole; none reads a project
document.

Incomplete, deferred, residue
=============================

* **Platform.** Every real-binary and GUI test ran on Linux x86-64 only; no
  Windows or macOS execution. POSIX-permission tests in the export suite
  would fail, not skip, on Windows or as root. Atomic replace and mapped-file
  behaviour on Windows are untested.
* **Budgets are local** (this host, warm cache); nightly thresholds are
  Phase 15's (AC-RES-10 stays partial in the traceability report).
* **Not built:** the optional weights bar chart (the pane would have to read
  numbers back from text, which ``UiThroughTheModelRule`` forbids); Comet
  scores in the PSM table and supporting-PSM counts in the peptide table
  (P10-9: not in Percolator's tables; a PIN join or a grouping rule is a
  later decision).
* **Export residue:** two CometGUI processes exporting the same table,
  cutoff and category of one run in the same millisecond could collide; a
  crash between the export's rename and its event leaves a file with no
  sidecar or event; exports are refused for an executing run (the engine
  holds ``events.log``).
* A run whose ``run.json`` cannot be read is left out of the Results list
  without a message.
* ``results.export`` is not a POM PIT target (targeted PIT: 97 mutations, 93
  killed); the store sits inside ``results.filtering.*`` on purpose (P10-4)
  and is gated.
* **``D-013`` residue:** the ``--help`` launches (install identity stage,
  local registration) run before the probe and carry no ``--no-analytics``;
  whether ``--help`` posts analytics is unmeasured.
* ``.gitattributes`` has no ``-text`` rule for the new fixtures under
  ``cometgui-results``, ``cometgui-ui`` and ``cometgui-app`` test resources.

Decisions encountered
=====================

No ``D-`` item was answered; ``D-013`` was implemented as the owner decided it,
on tier 1's instruction. Design decisions P10-1..P10-12 are in the work log.
Judgement calls a later phase may revisit: the threshold of 100 000 rows; a
zero weight mixed with one sign is ``MIXED``; the bias ``m0`` is listed and not
ranked; an export never overwrites (``-2``, ``-3`` ... appended); an export
holds rows verbatim in file order with text filter and sort not applied; a
damaged view-state file reads as the defaults with its refusal shown and is
never rewritten; a run with no attempts is listed as running.

Surprises
=========

* **The results module's PIT now takes about 42 minutes** (943 mutations,
  about 10 tests per mutation, many timeouts): the large-fixture, budget and
  export-gate tests cover the store, and PIT reruns them per mutant. This
  adds roughly 40 minutes to ``scripts/build.sh`` and to ``--only tests``.
* **Three tests could not go red until repaired**, each found by an injection
  at sign-off: a paging loop that hung instead of failing, a mandatory-column
  rule untested at exactly two files, and a harness diagnostic tied to
  ``Set.of`` iteration order.
* **Unit 8's large-fixture GUI test was flaky until unit 9 found the
  cause**: pressing Last disabled the focused button and focus fell into the
  table.
* The real K562 tables have no q-value exactly at 0, 0.005, 0.01, 0.05 or 1,
  so a ``<`` for ``<=`` slip is invisible there; the tests that catch it use
  the run's own q-values and constructed rows.

Escalated (none answered here)
==============================

#. **PIT cost**: the results module's mutation run grew to about 42 minutes.
   Reducing it without weakening the gate needs a POM-level choice (for
   example excluding a tagged set of slow, whole-fixture tests from PIT's
   test selection, which the brief forbids me to make). Tier 1 / owner.
#. **Add ``org.cometgui.results.export.*`` to PIT ``targetClasses``**
   (strengthening; a POM change).
#. **R-RES-01 "written to provenance when used to generate an export"** is
   met by the ``export.written`` event in the run's ``events.log``;
   ``provenance.json`` is not rewritten after the run. Confirm this reading,
   or say whether Phase 11's downstream-provenance step should fold export
   events into the report.
#. **``D-013``**: whether the ``--help`` launches should pass
   ``--no-analytics`` once a build is known to accept it.
#. **Fixture prerequisites**: the ``tests`` harness's preflight checks
   neither ``scratch/phase10/large/`` nor ``scratch/scientific-path/``;
   tests fail (not skip) without them. Tier 1's harness to extend if wanted.
#. **``.gitattributes``** for the new fixtures (above).
#. **Cost**: ``results`` adds about 9.5 minutes to the full gate run, and
   ``--only tests`` grew from about 125 to about 230 minutes (see
   `The deferred tests harness`_): the full gate run is now roughly six
   hours. This is the owner's build-economy concern, raised with numbers.

First thing the next agent should do
====================================

**Tier 1:** make sure ``scratch/phase05/artefacts``, ``scratch/fixture``,
``scratch/percolator/3.09`` and ``scratch/scientific-path/percolator-3.07.1``
are present, and regenerate the large fixture with ``python3
scripts/fixtures/large-results-fixture.py`` (about 15 s; its SHA-256s are
pinned, so a different file fails the tests). Then run ``bash
scripts/build.sh`` and the full ``bash scripts/verify-all-gates.sh`` (now 17
harnesses with ``results``, floor 100; ``percolator`` floor 109; ``tests``
now about 230 minutes -- run it detached). Then decide
the escalations above.

**Phase 11 (PDV):** the Results section's selected PSM is a ``RowKey`` with a
``SpectrumReference`` (source base, scan, charge) and the run's
spectrum-file mapping in ``ProjectResultsPort``; start from
``docs/developer/results_model.rst``.
