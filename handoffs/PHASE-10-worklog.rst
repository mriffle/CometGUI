=====================================================
PHASE-10 work log -- Results Model and UI
=====================================================

:Phase: 10
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``4f66dcd`` (brief ``handoffs/PHASE-10-BRIEF.rst``)
:Started: 2026-10-08

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``4f66dcd`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*): tier 1
ran ``scripts/build.sh`` and the full ``scripts/verify-all-gates.sh`` at Phase
09's exit gate on this tree.

Facts established by the orchestrator before design (2026-10-08)
-----------------------------------------------------------------

* ``cometgui-results`` holds ``org.cometgui.results.parser``
  (``ResultTableReader`` -- streaming, ``open``/``next``/``forEach``, and
  ``readAll`` which builds an in-memory ``ResultTable`` of every row;
  ``WeightsReader`` -> ``LearnedWeights``; ``QValue`` with ``KNOWN``,
  ``MISSING``, ``UNPARSABLE``, ``OUT_OF_RANGE``) and
  ``org.cometgui.results.filtering`` (``QValueFilter.classify`` -- the one
  ``q <= cutoff`` comparison -- ``PsmQValueFilter``, ``PeptideQValueFilter``,
  ``DisplayFilters``, ``FilterCounts``, ``FilterTally``, ``Visibility``).
  ``org.cometgui.results.export`` is an empty package. The module depends on
  ``cometgui-domain`` only.
* The parent POM's PIT ``targetClasses`` include
  ``org.cometgui.results.filtering.*`` and ``org.cometgui.results.parser.*``
  (PIT's ``*`` matches sub-packages) but **not** ``results.export``. The
  brief forbids changing the POM's PIT configuration.
* The one display-filter state in the interface today is
  ``PercolatorViewModel.displayFiltersProperty()`` (Phase 09 unit 7).
* ``EngineStep.FINALISE_RESULTS`` is declared (``implementedInPhase`` 10) with
  edges ``parse-percolator -> finalise-results`` (required) and
  ``finalise-results -> finalise-provenance`` (if planned); it is not planned,
  so ``finalise-provenance`` is ordered only after ``merge-pin``.
* ``InputKind`` has ``psm-display-filter`` and ``peptide-display-filter``,
  declared by no step (a test pins it).
* Real Percolator outputs available: the Phase 09 checked-in tables over
  CometGUI's synthetic 64+64 PIN (3.06.5, 3.07.1, 3.09; 64 rows each), and the
  Phase 00 real run on the ``D-006`` K562 fixture,
  ``scratch/scientific-path/percolator-{3.07.1,3.09}/`` (3897 target PSMs,
  2985 target peptides, 1026 PSMs and 603 peptides at q < 0.01 per Phase 00's
  summary; 3 weight splits of 22 features). ``D-006``: CometGUI does not
  redistribute spectrum or FASTA data, so K562-derived output stays in
  ``scratch/`` and is never committed (as Phase 09's P9-13 did).
* Comet's ``SpecId`` -- and so Percolator's ``PSMId`` -- begins with the
  ``-N`` base verbatim (an absolute path in a CometGUI run; Phase 08), then
  ``_<scan>_<charge>_<rank>``.
* ``ProvenanceEventLog.openAppend`` exists (``cometgui-provenance``);
  ``HashService`` is a domain port.
* No SQLite, H2 or other storage library is in the build; the only runtime
  third-party dependencies are JavaFX and AtlantaFX.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P10-1 -- one of everything
    One table reader (``ResultTableReader``) and one weights reader
    (``WeightsReader``), **extended in place**; one q-value predicate,
    ``QValueFilter.classify`` -- no other product code compares a q-value with
    a cutoff, the store, the export and the interface all call it; one filter
    value type (``DisplayFilters``); **one display-filter state in the
    interface**, shared by the Percolator and Results sections; one process
    service; one hasher (``HashService``); one JSON writer and one atomic
    writer (``org.cometgui.provenance.json``, ``.io``); one provenance event
    log; one step graph and run store. Anything missing is added to the
    owning class's module with its own tests. Every sign-off injects into the
    one predicate and requires the new code's tests to go red too.

P10-2 -- the large fixture comes first, and is independent
    A committed, deterministic, stdlib-only Python generator
    (``scripts/fixtures/large-results-fixture.py``) writes a Percolator-shaped
    PSM table (1 000 000 rows, several source files, multi-protein rows) and
    peptide table into ``scratch/phase10/large/`` with a manifest of SHA-256s
    and **expected counts computed by the generator from what it assigned**
    (Python ``decimal``), including every unknown-q-value kind. It is
    recorded as constructed. Being Python, it shares no code with the Java
    under test. Tests pin the fixture's SHA-256 and **fail, never skip**, when
    it is absent, naming the command that makes it.

P10-3 -- independent counts
    Gate items 3 and 7 compare against numbers not produced by the code under
    test: the generator's manifest; numbers pinned from ``awk`` with the
    command recorded beside them; or a deliberately minimal counter in test
    code (``split`` + ``BigDecimal``) that uses no ``org.cometgui.results``
    class. Real fixtures first: the checked-in Phase 09 tables and the
    ``scratch/scientific-path`` K562 outputs (SHA-256 pinned; fail not skip).

P10-4 -- the store
    ``org.cometgui.results.filtering.store`` (inside the existing PIT target,
    so the POM is unchanged and the store is mutation-gated by
    ``scripts/build.sh``). One ``ResultStore`` contract with two
    implementations: in memory at or below a documented row threshold,
    **disk-backed above it**. The disk-backed store keeps no row object for
    every row: a binary index file (row byte offset into the raw file, the
    q-value's status and value, score, PEP) memory-mapped off the heap,
    rows read back by offset from the raw file and parsed by the one reader;
    the raw file is only ever opened for reading. Queries -- q-value filter,
    category, text, sort, offset and limit -- return a ``ResultPage`` of at
    most ``limit`` rows plus the counts. Both implementations pass one
    contract test suite. No third-party storage library is added (no new
    licence, SBOM or native-library surface); the specification's SQLite is
    an example.

P10-5 -- what a filter shows, and the unknown category (``R-RES-02``)
    Every row is in exactly one ``Visibility``: ``PASSES``, ``FAILS`` or
    ``UNKNOWN_Q_VALUE``. A table's view selects a **category**: *passing*
    (the default), *unknown q-value*, *failing* or *all*. Counts always show
    total, passing, failing and unknown. Export writes exactly the rows of
    the category selected, under the same predicate, and its metadata records
    all four counts and which category was written -- so the unknown policy
    is one rule in the UI and the export.

P10-6 -- the run's derived files
    Nothing derived is ever written under ``outputs/``. A run gains
    ``results/`` (the store's index files, and ``view-state.json`` holding
    the run's display-filter values, schema-versioned, written atomically)
    and ``exports/`` (exported files, never overwritten). The paths are
    ``RunLayout``'s. ``finalise-results`` is planned whenever Percolator is:
    it builds and checks the store's indexes over the raw tables, records
    counts at the default filters in its ``stage.finished`` details, and
    ``finalise-provenance`` is ordered after it again (gate 9 of Phase 09
    re-proved: raw outputs byte-identical across it).

P10-7 -- export (``R-RES-04``, ``R-RES-01``)
    ``org.cometgui.results.export``: a filtered table export is a new file in
    ``exports/`` holding Percolator's header and the selected rows **verbatim
    in file order** (text filter and sort are view-only and not applied,
    which the metadata says), plus a JSON sidecar: run ID, table, source
    file path and SHA-256, the cutoff applied, category, the four counts
    before and the row count after, the CometGUI version, the time. The
    weights summary exports the same way. Each export appends one event to
    the run's provenance event log naming the file and the filter values.
    ``cometgui-results`` gains a dependency on ``cometgui-provenance`` (for
    the one JSON writer, atomic writer and event log; ``provenance`` depends
    on ``domain`` alone, so no cycle) -- recorded in the handoff.

P10-8 -- learned feature weights (``R-PERC-08``/``09``, ``AC-RES-08``/``09``)
    A summary computed from ``LearnedWeights`` in ``results.parser``: per
    feature, the **normalised** weight of each split (the split count read
    from the file), mean signed, mean absolute, standard deviation
    (population, divide by *n*, so one split gives 0), sign consistency
    (``all positive``, ``all negative``, ``mixed``, ``all zero``; zero is
    neither sign) and rank by mean absolute weight (1 = largest; ties share
    the rank, competition ranking; order then by file order). The bias
    ``m0`` is listed, labelled as the bias term, and **not ranked**. The
    sortable table is the source of truth; a chart, if any, is secondary.

P10-9 -- the interface
    ``org.cometgui.ui.viewmodel.results`` and ``controls.results``. The
    display-filter state is extracted from ``PercolatorViewModel`` into one
    view-model both sections hold (its existing API delegates; its tests stay
    green unchanged). Table views are bound to a **page** (at most the page
    size, never every row) fetched on a background executor and applied on
    the JavaFX thread; selection is held by a stable row key and survives
    filter and sort changes; sort, text filter, category, column visibility
    and copy (selected rows as tab-separated text). PSM table: PSMId, source
    file (always shown when the run has several spectrum files), scan,
    charge, peptide, proteins, score, q-value, PEP; peptide table: peptide,
    proteins, score, q-value, PEP; target or decoy table selectable where
    the decoy table exists. Not shown, recorded as deferred: Comet scores
    (not in Percolator's tables; a PIN join is a later feature) and
    supporting-PSM counts (not derivable from Percolator's tables without
    grouping choices that could change their meaning). The source file is
    the run's spectrum input whose ``-N`` base begins the ``PSMId``.

P10-10 -- the process service is the witness (gate 2)
    "Changing a filter launches no process" is asserted by counting calls on
    the application's one ``ProcessRunner`` through the real composition
    root, in a GUI test that changes both filters and sees the counts
    change.

P10-11 -- budgets (gate 6)
    The budgets are documented in ``docs/developer/results_model.rst`` and
    measured on this host. The heap budget is enforced by running the large
    fixture's load, filters, sorts and page reads in a **child JVM with a
    fixed ``-Xmx``**, with a negative control in the same budget
    (``ResultTableReader.readAll``) that must fail with
    ``OutOfMemoryError`` -- so the fixture provably crosses what the heap can
    hold. The GUI half asserts the table's items never exceed the page size
    on the large fixture.

P10-12 -- harness
    ``scripts/verify-results-gates.sh``, registered additively as
    ``results``: one or more production-code injections per gate item, in a
    git-archive sandbox, graded on the failing assertion's own words, with
    the usual harness self-controls. Lean: target under ten minutes.

Work units
==========

Run serially, in this order. Each unit's agent is fresh and is given the
brief's *Build economy* section verbatim.

.. list-table::
   :header-rows: 1
   :widths: 5 40 17 38

   * - #
     - Unit and acceptance conditions
     - Rules served
     - Sign-off: what was run, what was seen, date

   * - 1
     - **Large performance fixture and independent counts.** The generator
       of P10-2 with a self-check (a second, separate pass that re-counts
       the written files and must agree with the manifest; a second run
       byte-identical); q-values written as Percolator writes them (at most
       six significant digits) with exact ``0``, ``0.005``, ``0.01`` and
       ``1`` present, and every unknown kind (empty, ``NaN``, ``nan``,
       ``inf``, ``0,01``, out of range) present in known numbers. In
       ``cometgui-results`` tests: a locator for the fixture (pinned
       SHA-256, fail-not-skip), a locator for the ``scratch/scientific-path``
       real outputs (pinned SHA-256s, ``awk``-pinned counts at 0, 0.005,
       0.01, 0.05 and 1 with the command recorded), and the minimal
       independent counter of P10-3, itself checked against both.
       ``CONSTRUCTED`` record for the fixture.
     - R-RES-03 (fixture first); gates 3, 6
     - **Signed off 2026-10-08** (``e642695``). Diff read: 12 files, no
       production code -- the generator (559 lines, stdlib only), six test
       helpers/tests in ``org.cometgui.results.testing``, ``CONSTRUCTED.txt``
       and ``real-k562/PROVENANCE.txt`` (numbers and SHA-256s only, no
       data). I ran ``python3 scripts/fixtures/large-results-fixture.py
       --self-check``: 1 000 000 PSM rows (147 155 274 bytes, SHA-256
       ``4f7aaecd...``) and 400 000 peptide rows (``bdda5589...``); 1478 and
       594 unknown q-values in ten kinds; 0 double-vs-decimal disagreements;
       at q <= 0.01 219 777 PSMs and 88 111 peptides pass; second generation
       byte-identical; 21 s. My own ``awk`` over ``psms.tsv`` (numeric
       q-values only): 998 522 known, 219 777 at 0.01, 175 162 at 0.005,
       5003 at 0, 1478 unknown -- equal. My ``awk`` over the K562 outputs:
       3.07.1 PSMs 3897/982/1026, peptides 2985/567/603, 3.09 decoy PSMs
       2773/4/9 -- equal to the pins. ``mvn -o -pl cometgui-results test``:
       **197 tests, 0 failures** (new: ``LargeFixtureTest`` 6,
       ``RealK562Test`` 14, ``IndependentCounterTest`` 10). Injections
       (into the unit's test-side oracle, since the unit has no production
       code), restored by ``sha256sum -c``: (1) the independent counter's
       ``<= 0`` made ``< 0`` -- 10 failures (``counterEqualsManifest`` x2,
       ``IndependentCounterTest`` x8); (2) the K562 pin at 0.01 made 1027
       -- ``RealK562Test.counterEqualsAwk`` and ``phase00Summary`` red,
       "expected passing=1027 but was 1026". **Files read outside the
       module**: ``scratch/phase10/large/`` and
       ``scratch/scientific-path/percolator-{3.07.1,3.09}/`` (the ``tests``
       sandbox links ``scratch/`` whole); tier 1 must regenerate the first
       with the command above before ``build.sh``.

   * - D-013
     - **``--no-analytics``** (added by tier 1 on the owner's decision of
       2026-10-08, run next, before unit 2). One capability for the switch,
       observed by the one probe on its own run and passed on every later
       probe run once observed; one ``PercolatorOption``; the builder emits
       it whenever the probed set holds it -- capability-gated, never
       version-gated; the probe-generation constant bumped so installed
       builds are re-probed. Acceptance: (1) the real 3.07.1 and 3.09 probe
       observe it; (2) every Percolator invocation's recorded argv --
       read back from ``provenance.json`` and from the probe's launches --
       carries it; (3) a build whose probe does not observe it gets no such
       argument and is not refused; (4) ``docs/percolator.rst`` and
       ``docs/developer/version_capabilities.rst`` say so; (5) my injections
       go red; (6) ``--only percolator``, ``workflow``, ``install`` green,
       floors only rise.
     - D-013; R-PERC-06
     - **Signed off 2026-10-08** (``47c55c4``, ``c5973fd``). Diff read: 28
       files + harness -- ``ToolCapability.NO_ANALYTICS_OPTION``,
       ``PercolatorOption.NO_ANALYTICS``; the probe's first run is
       ``[exe, --no-analytics, pin]`` and, once observed, every later run
       carries it (12 runs); ``Builder.noAnalytics()`` emits it last before
       the PIN when the probed set holds it, else a ``NotEmitted`` sentence
       (run not refused); ``CAPABILITY_PROBE_GENERATION`` 2 -> 3; docs on
       five pages; ``percolator`` controls A1-A3, floor 94 -> 109. I ran:
       ``mvn -o -pl cometgui-tools -am install -DskipTests``, then the four
       Percolator test classes -- **115 tests, 0 failures** (incl. the real
       3.07.1/3.09 ``PercolatorRealBinaryTest`` 11, 49 s);
       ``-pl cometgui-params-percolator,cometgui-ui test`` -- 54 and 587, 0
       failures (the domain enum grew). My injections, restored by
       ``sha256sum -c``: (A) the probe passing ``--no-analytics`` on later
       runs even when not observed -- 4 red (``aBuildRefusingNoAnalytics``,
       ``aNoAnalyticsRunMustExitZero``, ``theArgumentArrays``,
       ``oneRejectedOptionLosesExactlyItsCapability[13]``); (B) the
       builder's gate for the switch keyed on ``XML_OUTPUT`` instead of its
       own capability -- 5 red (``noAnalyticsWheneverProbed``,
       ``withoutNoAnalyticsTheRunIsNotRefused``, ``neededButNotCapable``,
       ``eachRemovedInTurn`` x2). Agent's PIT: 64/64 killed on the changed
       classes. ``verify-all-gates.sh --only percolator --only workflow
       --only install``: **3 passed** -- percolator 109 (699 s), workflow
       110 (466 s), install 95 (315 s). **Residue, escalated:** the
       ``--help`` launches (install identity stage, local registration) run
       before the probe and carry no ``--no-analytics``; whether ``--help``
       posts analytics is unmeasured (no strace on the host, nothing may be
       installed). Hand-typed eleven-capability test inputs in ui, app and
       params-percolator are unchanged (they run no Percolator).

   * - 2
     - **The store contract and the in-memory store.**
       ``results.filtering.store``: ``ResultStore``, ``ResultQuery``
       (filter, category, text, sort, offset, limit), ``ResultPage``, the
       stable row key; the in-memory implementation; the spectrum reference
       (source base, scan, charge, rank) read from a ``PSMId`` in the parser
       package; one contract test suite over the real fixtures with counts
       equal to the independent ones at 0, 0.005, 0.01 and 1, inclusive at
       the boundary, every unknown kind in its own category; raw files'
       SHA-256 unchanged. Any parser extension in place, its tests green.
     - R-RES-01, R-RES-02; gates 1, 3, 8
     - **Signed off 2026-10-08** (``91b7ad9``; my repair ``a3e2992``). Diff
       read: 24 files -- ``filtering.store``: ``ResultStore`` (file, kind,
       header, rowCount, ``counts``, ``query``, ``row(RowKey)``), ``RowKey``
       (line number), ``TableKind`` (refuses the other table's filter),
       ``Category``, ``ResultSort`` (ten columns; missing last both ways;
       ties by file order), ``ResultQuery`` (default page 200, maximum
       5000), ``ResultPage``, ``TextFilter``, ``InMemoryResultStore``,
       ``ResultStores``; ``parser.SpectrumReference`` (from the right);
       ``QValue``: ``1e-400`` known and above 0 (fails cutoff 0),
       ``1e400``/``-1e-400`` OUT_OF_RANGE. The store classifies only through
       ``QValueFilter.classify``. I ran ``mvn -o -pl cometgui-results -am
       install -DskipTests`` then ``-pl cometgui-results verify``: **652
       tests, 0 failures** (``InMemoryResultStoreTest`` 386 via the abstract
       ``ResultStoreContract`` over 22 tables incl. a constructed shuffled
       one, ``StoreValuesTest`` 22, ``SpectrumReferenceTest`` 30,
       ``QValueExtremesTest`` 14, ``InMemoryLargeFixtureTest`` 3), 0
       SpotBugs, 0 Checkstyle. My injections, restored by ``sha256sum
       -c``: (1) ``QValueFilter.classify`` ``<=`` -> ``<`` -- 96 of 408
       red, e.g. ``categoriesPartition PASSING expected [2, 3, 4, 9, 10, 13,
       14] but was [2, 3, 4, 9, 13, 14]``; (2) paging off by one
       (``matching > offset``) -- **the suite hung for 29 minutes** (a
       paging loop with no exit on an empty page); I killed it and
       repaired the contract (``a3e2992``: an empty page before the end
       fails); re-run: 231 of 408 red, "only the last page is short"; (3)
       sort ties broken in reverse -- 62 red. Agent's PIT on the new and
       changed classes: 214 mutations, 211 killed, 2 timed out, 1 survived
       (equivalent: a loop bound in ``TextFilter.contains``). I ran
       ``--only percolator --only quality``: **percolator 109 (698 s),
       quality 42 (142 s)**. Large fixture in memory: open 1.9 s, about
       670 MB heap -- the threshold belongs well below a million rows.
       Files read outside the module: as unit 1.

   * - 3
     - **The disk-backed store and the threshold.** Off-heap index
       (P10-4), rows read back through the one reader, sorting every
       column (external sort for text keys), text filter, threshold
       documented and switched on by the factory; the same contract suite
       passes for it; the child-JVM heap and time budget test of P10-11 with
       its ``OutOfMemoryError`` negative control; index rebuilt when the raw
       file's size or SHA-256 differs from the index's record.
     - R-RES-03; gates 3, 6, 8
     - **Signed off 2026-10-08** (``c4a0acc``, ``028b6de``). Diff read: 27
       files -- ``DiskResultStore`` with ``DiskIndex`` (96-byte header:
       magic, version, kind, the raw table's size, mtime and SHA-256 through
       the injected ``HashService``, row count, CRC-32C of header and body;
       56 bytes a row), ``SortFile`` (external merge sort, permutation
       checked on load), ``RawIdentity``, ``IndexProblem`` (14 reasons),
       mapped off-heap through an FFM ``Arena``; rows read back by position
       and checked against their index record; ``ResultTableReader`` on a
       new strict ``Utf8Lines`` with byte offsets (its 30 tests unchanged);
       ``QValueFilter.classify(Status, double)`` now the one comparison;
       ``ResultStore.positionOf``; ``ResultStores.IN_MEMORY_ROW_LIMIT`` =
       **100 000** (about 67 MB in memory). I ran ``-am install`` then
       ``-pl cometgui-results verify``: **1215 tests, 0 failures**, 0
       SpotBugs (contract: 444 on the disk store, 447 in memory;
       ``DiskLargeFixtureTest`` 4 in 31 s; ``DiskStoreBudgetTest`` 2 in
       12 s -- a child JVM at ``-Xmx64m`` opens the 1 000 000-row fixture
       through the factory, counts at all eight cutoffs equal the manifest,
       pages by score and peptide, a text filter, each within budget, and
       ``readAll`` in the same 64 MB dies of ``OutOfMemoryError``). My
       injections, restored and ``sha256sum -c`` OK: (a) the sort file's
       every-row-once check removed -- ``DiskIndexInvalidationTest.sortFiles``
       red ("a row twice"); (b) the disk count loop skipping the last row --
       173 failures + 45 errors of 444, e.g. ``total=2359 ... but was
       total=2358``; (c) the index's raw-mtime comparison made vacuous --
       ``rawTimeChanged`` red, ``RAW_TIME_CHANGED`` expected, ``built=false``
       found (Checkstyle and Spotless had to be skipped for the injected
       form to compile, the tests ran). Agent's PIT: 646 mutations, 593
       killed, 24 timed out, 22 survived (each argued equivalent or
       performance-only), 7 no coverage; ``QValueFilter`` 33/33. I ran
       ``--only percolator --only quality``: **109 (701 s), 42 (140 s)**.
       New reads outside the module: ``/proc/self/{fd,maps}`` (Linux) and a
       child JVM from ``java.home``. Residue: in-memory compares scans as
       ``double``, disk as ``long`` (differ above 2^53 only); every disk
       open hashes the raw table (0.5 s at 147 MB); Windows untested.

   * - 4
     - **Learned-feature-weights summary** (P10-8) in ``results.parser``,
       with values, ranks and sign consistency checked against numbers
       computed independently (recorded how) for the real three-split files
       and the constructed two- and four-split files.
     - R-PERC-09, AC-RES-08/09; gate 7
     -

   * - 5
     - **``finalise-results`` and the run's derived files** (P10-6):
       ``RunLayout`` paths; the view-state document and its store
       (``workflow.storage``); the step action, planned with Percolator;
       ``finalise-provenance`` ordered after it; rerun-preview expectations
       updated with hand-typed step sets; raw outputs byte-identical across
       the step; ``docs/reference/project_format.rst`` updated.
     - R-RES-01 (view state), R-RES-03, R-RUN-01; Phase 09 gate 9
     -

   * - 6
     - **Export** (P10-7): table and weights export with sidecar, provenance
       event, distinct directory, never overwriting; raw files byte-identical
       (SHA-256 before/after) after filtering and export; unknown-category
       rows exported exactly as the view classifies them.
     - R-RES-04, R-RES-01, R-RES-02, R-PERC-07; gates 4, 5, 8
     -

   * - 7
     - **Results view-models** (P10-9): the shared display-filter
       view-model (Percolator section delegating, its tests green
       unchanged); the paged table view-model; the weights view-model; the
       Results view-model (run choice, export actions) and its ports;
       view-model coverage >= 80%.
     - R-RES-01..04, R-PERC-09
     -

   * - 8
     - **Results and weights panes, wiring, and the first GUI gates**: the
       JavaFX panes (accessible names, pinned identifiers), the app's
       ports and composition, ``SectionArrivals`` updated; GUI tests for
       gate 1 (defaults, inclusive, out-of-range refused on screen), gate 2
       (P10-10) and gate 6 (the large fixture, items never above the page
       size, within budget).
     - R-RES-01, R-RES-03; gates 1, 2, 6
     -

   * - 9
     - **The remaining GUI gates and the real run**: displayed counts
       against independent counts at 0, 0.005, 0.01 and 1 (gate 3); raw
       files byte-identical after filtering and export through the
       interface (gate 4); the export's metadata (gate 5); the weights table
       against independent values (gate 7); the unknown category identical
       on screen and in the export (gate 8); a real Comet + Percolator run
       through the interface whose results appear in the Results section.
     - gates 3, 4, 5, 7, 8
     -

   * - 10
     - **Documentation**: ``docs/results.rst``,
       ``docs/learned_feature_weights.rst``,
       ``docs/developer/results_model.rst`` (budgets, threshold, store,
       export format); stale statements corrected (workflow engine,
       version capabilities' gate-9 note, project format); traceability map
       entries for AC-RES-01..04, 08..10.
     - R-DOC; all AC-RES of the phase
     -

   * - 11
     - **The falsifiability harness** (P10-12), registered as ``results``.
     - every gate item
     -

Rejections and rework
=====================

* **Unit 2** -- not sent back; one orchestrator repair (``a3e2992``): the
  contract's paging loops had no exit when a page came back empty before the
  end, so an injected off-by-one hung the suite instead of turning it red.

Deferred
========

* Comet scores in the PSM table and supporting-PSM counts in the peptide
  table (P10-9).
* Protein-level results (out of scope for release 1).

Blockers escalated
==================

None yet.
