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
     - **Signed off 2026-10-08** (``7498a83``). Diff read: three new classes
       in ``results.parser`` (``WeightsSummary``, ``FeatureWeights``,
       ``SignConsistency``), no existing class changed; tests and the
       recorded independent computation ``WEIGHTS-SUMMARY.txt`` (Python
       ``Fraction``/``decimal``, script and command in the file; K562
       statistics only, no per-split data). Zeros mixed with one sign are
       ``MIXED`` (the agent's documented reading of "zero is neither
       sign"). My own Python (``statistics.pstdev``) over the K562 3.07.1
       weights: lnrSp rank 1, mean -0.336333, SD 0.079189; lnExpect 2;
       Xcorr 0.1139 -- equal to the pins. ``-pl cometgui-results verify``:
       **1232 tests, 0 failures**, 0 SpotBugs. My injections, restored by
       ``sha256sum -c``: (a) mean absolute summing signed values -- 11 of
       39 red; (b) ranks counting equal values as greater (ties broken) --
       8 red. Agent's PIT: 63/63 killed. ``--only quality`` by the agent
       (42); ``percolator`` not needed (no existing class changed).

   * - 5
     - **``finalise-results`` and the run's derived files** (P10-6):
       ``RunLayout`` paths; the view-state document and its store
       (``workflow.storage``); the step action, planned with Percolator;
       ``finalise-provenance`` ordered after it; rerun-preview expectations
       updated with hand-typed step sets; raw outputs byte-identical across
       the step; ``docs/reference/project_format.rst`` updated.
     - R-RES-01 (view state), R-RES-03, R-RUN-01; Phase 09 gate 9
     - **Signed off 2026-10-09** (``aeb184c``, ``2c48ceb``). Diff read: 27
       files -- ``RunLayout``: ``results/``, ``results/index/``,
       ``results/view-state.json``, ``exports/``; ``workflow.storage``
       ``ViewStateJson``/``ViewStateStore``/``ViewStateReading``
       (``{"schemaVersion": 1, "psmQValueFilter": ..., "peptideQValueFilter":
       ...}`` through the one JSON reader and atomic writer, cutoffs parsed
       by the filters' own ``parse``; a refused file reads as the defaults
       plus the refusal text and is never overwritten);
       ``ResultSteps.FinaliseResults``, planned with Percolator in a search
       and in the derived rerun, opening each table through
       ``ResultStores.open`` with ``results/index/`` and recording rows and
       the four counts at 0.01 per table plus the weights' split and feature
       counts; ``finalise-provenance`` after it again; app GUI pins of the
       preview updated; ``project_format.rst``, ``workflow_engine.rst``.
       ``ResultStores.open(..., limit)`` made public (visibility only). I
       ran ``-am install`` then ``-pl cometgui-domain,cometgui-workflow
       verify``: **1172 and 730 tests, 0 failures**, 0 SpotBugs; app
       ``PercolatorRerunUiTest, RealRunUiTest, RealCancelUiTest,
       WorkflowRunPortTest``: 21, 0 failures. My injections, restored by
       ``sha256sum -c``: (a) the index directory at the run root instead of
       ``results/index/`` -- ``RunLayoutTest.derivedFiles`` red and
       ``FinaliseResultsTest.aTableAboveTheLimitIsIndexedUnderResults``
       errors (``NoSuchFile .../results``); (b) the view state accepting
       schema version 2 -- ``ViewStateStoreTest.newer`` red. Agent's
       injections include the dropped ``finalise-results ->
       finalise-provenance`` edge (6 red) and the one predicate (3 red);
       its PIT: workflow 45 mutations, 44 killed (1 NO_COVERAGE in Phase 09
       code); ``RunLayout`` 36/36. I ran ``--only workflow --only percolator
       --only quality --only docs --only traceability``: **5 passed** --
       workflow 110 (470 s), percolator 109 (719 s), quality 42, docs,
       traceability 8; and ``--only quality`` once more for unit 4's added
       classes (42, 139 s). **Known weakness, recorded:** on the real K562
       first-1000-records search no row reaches q <= 0.01 (smallest target
       PSM q 0.018648), so the real-run count comparison cannot tell 0.01
       from a smaller cutoff; the hand-typed table in
       ``FinaliseResultsTest`` does. The step's ``store`` detail repeats
       the factory's ``rows > limit`` rule (display only).

   * - 6
     - **Export** (P10-7): table and weights export with sidecar, provenance
       event, distinct directory, never overwriting; raw files byte-identical
       (SHA-256 before/after) after filtering and export; unknown-category
       rows exported exactly as the view classifies them.
     - R-RES-04, R-RES-01, R-RES-02, R-PERC-07; gates 4, 5, 8
     - **Signed off 2026-10-09** (``9965432``, ``f5d8088``). Diff read: 22
       files -- ``cometgui-results`` -> ``cometgui-provenance`` edge (POM,
       commented); ``ProvenanceEventType.EXPORT_WRITTEN``
       (``export.written``, additive); ``results.export``:
       ``ResultExporter.exportTable(table, kind, filter, category)`` and
       ``exportWeights(summary)``, rows copied byte for byte from a
       read-only channel in file order, classified only by
       ``QValueFilter.classify``; written by the one atomic writer under
       ``exports/``, never overwritten (``-2``, ``-3`` ... appended); JSON
       sidecar (run ID, source path/MD5/SHA-256, filter name and cutoff,
       category, the four counts before, rows written, text filter and sort
       not applied, version, time, the export's own hashes); one event per
       export in ``events.log`` (``provenance.json`` is not rewritten); a
       failed sidecar or event removes the export. I ran ``-am install``
       then ``-pl cometgui-results verify``: **1310 tests, 0 failures**, 0
       SpotBugs (``TableExportGateTest`` 45 over 22 tables x 4 cutoffs x 4
       categories, about 60-100 s; large fixture exported at 0.01: 219 777
       rows, and in a ``-Xmx32m`` child JVM); the agent's
       ``cometgui-provenance verify`` 674/0. My injections, restored by
       ``sha256sum -c``: (a) the sidecar's ``rowsWritten`` one too many --
       22 red, e.g. "decoy-peptides.tsv at 0, PASSING expected 0 but was
       1"; (b) the provenance event never recorded -- 3 failures + 17
       errors in ``TableExportBehaviourTest``/``WeightsExportTest``. The
       agent's seven (unknown rows in the passing file, sidecar cutoff from
       the default, overwrite, raw opened for writing, counts swapped, last
       byte dropped, the one predicate) were each red. Agent's PIT on
       ``results.export.*`` (not a POM target): 97 mutations, 93 killed, 4
       survivors argued performance-only. I ran ``--only quality
       --only provenance --only docs --only traceability --only
       percolator``: **5 passed** -- provenance 24 (234 s), percolator 109
       (721 s), quality 42, docs, traceability 8. Residue: two processes
       exporting the same name in one millisecond; a crash between rename
       and event; exports only for runs not executing (the engine holds
       ``events.log``); POSIX-dependent tests; R-RES-01's "written to
       provenance" is met by the ``events.log`` event -- tier 1 to confirm.

   * - 7
     - **Results view-models** (P10-9): the shared display-filter
       view-model (Percolator section delegating, its tests green
       unchanged); the paged table view-model; the weights view-model; the
       Results view-model (run choice, export actions) and its ports;
       view-model coverage >= 80%.
     - R-RES-01..04, R-PERC-09
     - **Signed off 2026-10-09** (``1a9bd02``, ``68984c2``, ``58da46a``; my
       repair ``c7ae61e``). Diff read: 40 files in
       ``ui.viewmodel.results`` -- ``DisplayFiltersViewModel`` (the one
       filter state; ``PercolatorViewModel`` delegates, its 28 tests
       unchanged and green; a six-argument constructor takes the shared
       instance), ``ResultsPort`` (+ ``ResultsRun``, ``OpenedResults``),
       ``ResultTableViewModel`` (pages of at most 200 rows built on the
       background executor, stale answers dropped, cells as written, all
       four counts, category, sort cycle, text filter on Enter, selection
       as a set of ``RowKey`` with an anchor that follows its page via
       ``positionOf`` or is shown "selected but hidden", copy as TSV,
       source-file column mandatory with several spectrum files),
       ``WeightsViewModel`` (the specified title and description; 4/6
       decimals; sort on numbers; bias unranked), ``ResultsViewModel``
       (runs, open/close, saved view state applied and saved on change,
       exports refused while executing). I ran ``-am install`` then ``-pl
       cometgui-ui verify``: **652 tests, 0 failures**, "All coverage
       checks have been met", 0 SpotBugs. My injections, restored by
       ``sha256sum -c``: (a) the source-file column mandatory only above
       **two** files -- **stayed green** (tests used one file and three);
       I added a two-file case (``c7ae61e``), which goes red on it
       ("expected hideable=false but was hideable=true") and is green on
       the real code; (b) a table opened on category ALL instead of
       PASSING -- 16 red, e.g. ``ResultTableScratchFixturesTest.realK562``
       "expected 0 but was 3897". The agent's seven (every row as one page,
       no re-query on a filter change, selection by index, a second filter
       state in the Percolator section, export while executing, a saved
       state re-saved on apply, a refused filter applied) were each red.
       Agent's PIT: 592 mutations, 589 killed; new package coverage 99.2%
       lines, 92.7% branches. I ran ``--only quality --only shell --only
       percolator``: **3 passed** -- quality 42, shell 30 (353 s),
       percolator 109 (723 s). Files read outside the module:
       ``scratch/scientific-path/percolator-3.07.1/`` and
       ``scratch/phase10/large/psms.tsv`` (fail, not skip); small fixtures
       copied into ui test resources with SHA-256 pins (``FIXTURES.txt``).

   * - 8
     - **Results and weights panes, wiring, and the first GUI gates**: the
       JavaFX panes (accessible names, pinned identifiers), the app's
       ports and composition, ``SectionArrivals`` updated; GUI tests for
       gate 1 (defaults, inclusive, out-of-range refused on screen), gate 2
       (P10-10) and gate 6 (the large fixture, items never above the page
       size, within budget).
     - R-RES-01, R-RES-03; gates 1, 2, 6
     - **Signed off 2026-10-09** (``6ff1946``, ``e284b60``). Diff read: 32
       files -- ``ui.controls.results.ResultsPane`` (a ``TableView`` whose
       items are the current page only, ``setAll`` per page; TableView
       sorting off, headings are buttons calling the view-model's store
       sort; filter fields on the shared ``DisplayFiltersViewModel``;
       counts, category, text filter on Enter, paging, column switches,
       copy, exports) and ``WeightsPane`` (bar chart **not built**: the
       pane would have to read numbers back from text); 34 ``UiIds``;
       ``SectionArrivals``; ``ShellView``; app ``ProjectResultsPort``
       (unlocked reads of ``runs/*/run.json``, locked writes, exports
       refuse an executing run, ``-N`` base -> spectrum file name, a
       derived run uses its source's bases), ``SessionEngine.executingRuns``
       tracked by ``WorkflowRunPort``, one ``CachingHashService`` shared by
       engine and port, refresh at start and at run start/end; workflow
       ``RunResultFiles``. Pins raised exactly: identifiers 433 -> 493,
       accessible-name floor 68 -> 117. I ran ``-am install`` then ``-pl
       cometgui-app verify`` on the final commit (the agent had not):
       **230 tests, 0 failures, 1 skipped** (opt-in upstream install), 0
       SpotBugs, 12 m 53 s. The agent's ui verify: 654, coverage met. My
       injections, restored by ``sha256sum -c``: (a) the peptide field
       committing to the PSM filter -- ``ResultsFiltersUiTest``
       ``outOfRangeAndNonNumbersAreRefused`` and ``theFiltersAreIndependent``
       red; (b) the port's spectrum-name map empty --
       ``ProjectResultsPortTest`` ``open``, ``derivedRun``, ``sharedNames``
       and one more red. The agent's six (table items accumulating -- "the
       results table holds 400 items, more than one page of 200"; a filter
       listener launching ``/bin/true`` through the one runner -- gate 2
       red; a second filter state; an accessible name removed; export of an
       executing run; the one predicate) were each red. Gate 6 measured
       (``ResultsLargeFixtureUiTest``, about 22 s): open 3.3 s, filter
       0.7-1.0 s, sorts 1.3/3.3 s, page 0.4-0.6 s, heap growth 3 MB of a
       64 MB budget, most items ever held 200. I ran ``--only quality
       --only shell --only paramui --only percolator --only workflow``:
       **5 passed** -- quality 42, shell 30 (383 s), paramui 87 (1881 s),
       workflow 110 (469 s), percolator 109 (731 s). Files read outside the
       module: ``scratch/phase10/large/psms.tsv``. Residue: a run whose
       ``run.json`` cannot be read is left out of the list silently; the
       copy test asserts the message, not the clipboard.

   * - 9
     - **The remaining GUI gates and the real run**: displayed counts
       against independent counts at 0, 0.005, 0.01 and 1 (gate 3); raw
       files byte-identical after filtering and export through the
       interface (gate 4); the export's metadata (gate 5); the weights table
       against independent values (gate 7); the unknown category identical
       on screen and in the export (gate 8); a real Comet + Percolator run
       through the interface whose results appear in the Results section.
     - gates 3, 4, 5, 7, 8
     - **Signed off 2026-10-09** (``f83558f``, ``c22aa45``, ``c9c2a50``).
       Diff read: app GUI tests ``ResultsCountsUiTest`` (gate 3: the real
       K562 3.07.1 tables in a constructed run, four tables at 0, 0.005,
       0.01, 0.05 and 1 equal to the ``awk`` pins and a test-side
       ``BigDecimal`` counter; also at the run's own smallest q-value
       0.00112905, where 599 target PSMs sit exactly on the cutoff, and the
       64-row tables' 17 rows at exactly 0.0588235), ``ResultsExportUiTest``
       (gates 4, 5, 8), ``ResultsWeightsUiTest`` (gate 7), ``RealRunUiTest
       .theResultsAppear`` (a real Comet + Percolator run listed and opened
       without a restart, counts equal the counter on its raw ``psms.tsv``,
       ``finalise-results`` recorded); helpers ``IndependentCounts``,
       ``TestJson`` (test-side JSON), ``K562Outputs``. **Product defect
       found and fixed** (``c9c2a50``): pressing Last disabled the focused
       paging button and focus fell into the table (unit 8's large-fixture
       test failed 2 of 3 runs); ``ResultsPane`` now hands focus to the
       enabled neighbour first, asserted red-then-green. I ran ``-am
       install`` then the eight Results GUI classes and ``RealRunUiTest``:
       **32 tests, 0 failures** (counts 4 in 76 s, export 6 in 33 s, weights
       5 in 20 s, large fixture 1 in 23 s, real run 6 in 53 s); the agent's
       ``cometgui-app verify`` on ``c9c2a50``: 246, 0 failures. My
       injections, restored by ``sha256sum -c`` and reinstalled: (a) weights
       statistics displayed at 4 decimals instead of 6 -- 4 of 5 red, "cells
       that differ from the independent values ... lnrSp: mean signed"; (b)
       the export dropping the header line -- 5 of 6 red, "the export is the
       header and exactly the counter's rows of the category, verbatim".
       The agent's twelve (passing label bound to failing; the one
       predicate -- red only through the on-the-cutoff cases, the pinned
       K562 cutoffs alone cannot see it; export beside the raw table; raw
       mtime touched; sidecar cutoff from the default; wrong version;
       weights sorted by mean signed; n-1 SD; three split headings; an
       unknown row dropped from export and from the view; no refresh at run
       end) were each red. I ran ``--only quality --only shell --only
       paramui --only percolator --only workflow``: **5 passed** (60 m 02 s;
       paramui 87 in 1882 s, percolator 109 in 729 s). Files read outside
       the module: ``scratch/scientific-path/percolator-3.07.1/``.

   * - 10
     - **Documentation**: ``docs/results.rst``,
       ``docs/learned_feature_weights.rst``,
       ``docs/developer/results_model.rst`` (budgets, threshold, store,
       export format); stale statements corrected (workflow engine,
       version capabilities' gate-9 note, project format); traceability map
       entries for AC-RES-01..04, 08..10.
     - R-DOC; all AC-RES of the phase
     - **Signed off 2026-10-09** (``c42113c``). Diff read: 8 files --
       ``docs/results.rst`` (320 lines), ``docs/learned_feature_weights.rst``,
       ``docs/developer/results_model.rst`` (821 lines: code map, one of
       everything, store contract, threshold, index and sort-file layouts,
       budgets with measured numbers, finalise-results, export, fixtures and
       how to regenerate them, the exit-gate test table, Linux only, known
       limits); stale statements corrected in ``percolator.rst``,
       ``version_capabilities.rst`` (gate-9 export note), ``architecture.rst``
       (the ``results -> provenance`` edge); traceability: AC-RES-01..04, 08,
       09 now ``test`` evidence (GUI, view-model, store and unit tests named
       by class and method), AC-RES-10 keeps Phase 15's planned entry and
       gains the budget tests (reported partial), AC-RES-05's note corrected.
       I read the user page and spot-checked the facts against the code. I
       ran ``scripts/ci/docs-build.sh``: PASSED. My injections: (a) a
       traceability entry naming ``ResultsFilterzUiTest`` -- the docs build
       fails, "the traceability report is not complete" (R-DOC-03); (b) an
       undefined ``:ref:`` in ``results.rst`` -- "undefined label",
       docs-build FAILED; both restored by ``sha256sum -c``. ``--only docs
       --only traceability``: **2 passed** (docs, traceability 8). Code vs
       work log, reported by the agent and correct: the disk index stores
       length, scan and charge as well (56 bytes a row); unit 8's GUI timings
       are 3.3 s in the sign-off and 3.4 s in the test's Javadoc.
       ``docs/developer/testing.rst`` waits for unit 11's harness.

   * - 11
     - **The falsifiability harness** (P10-12), registered as ``results``.
     - every gate item
     - **Signed off 2026-10-09** (``35961b3``, ``2f88c02``; my repair
       ``aea66af``). Diff read: ``scripts/verify-results-gates.sh`` -- 22
       controls over items 1-8 (1a-1d, 2, 3a-3c, 4a-4b, 5a-5b, 6a-6c,
       7a-7e, 8a-8b; recorded and new marked), each injected into production
       code of a ``git archive`` sandbox, proved to reach the bytecode, graded
       on the failing testcase's own words with green siblings, plus
       self-controls H1-H6; registration in ``verify-all-gates.sh``
       **additive only** (``results``, phase 10, items 1-8, floor 100; no
       other entry or floor touched); ``testing.rst`` and
       ``results_model.rst``; a test-only message added to
       ``ResultTableViewModelTest.paging``. I ran ``verify-all-gates.sh
       --only results --only docs --only traceability``: docs and
       traceability passed, **results FAILED on one check** -- control 8b's
       diagnostic pattern named ``psms-unknown-q.tsv``, but the contract
       iterates its constructed tables in ``Set.of`` order (salted per JVM)
       and my run reached ``psms-shuffled.tsv`` first, a correct red the
       pattern did not accept. Repaired (``aea66af``): either table, each
       with its own count, still exactly the empty spelling gone. Re-run
       ``--only results``: **100 controls in 562 s**, 8b matched on the
       shuffled table. The harness's H controls each reported a HARNESS
       ERROR or failure as required. Cost: about 9.5 minutes added to the
       full gate run. Not graded by it (each seen red at unit 9): the
       export, weights and K562 counts GUI tests and the real run.

Rejections and rework
=====================

* **Unit 2** -- not sent back; one orchestrator repair (``a3e2992``): the
  contract's paging loops had no exit when a page came back empty before the
  end, so an injected off-by-one hung the suite instead of turning it red.
* **Unit 7** -- not sent back; one orchestrator repair (``c7ae61e``): no test
  had exactly two spectrum files, so a "mandatory above two" rule stayed
  green.
* **Unit 11** -- not sent back; one orchestrator repair (``aea66af``): control
  8b's diagnostic was pinned to one constructed table while the contract
  reaches its tables in ``Set.of`` order, so a correct red failed the
  control on my run.

Deferred
========

* Comet scores in the PSM table and supporting-PSM counts in the peptide
  table (P10-9).
* Protein-level results (out of scope for release 1).
* The optional learned-weights bar chart (unit 8): the view-model hands the
  pane text, and drawing from it would mean reading numbers in the view.

Phase-end checks
================

* PIT reports regenerated as ``scripts/build.sh`` does (root
  ``test-compile org.pitest:pitest-maven:mutationCoverage``, 16 threads):
  BUILD SUCCESS; results 943 mutations, 931 killed, **42 m 27 s**.
* ``verify-all-gates.sh --only tests`` on ``66afdad``, detached and watched
  to its exit: **PASS, 37 assertions in 13 778 s** (229 m 38 s; Phase 09:
  7490 s).

Blockers escalated
==================

No blocker. Escalations for tier 1 are listed in
``handoffs/PHASE-10-handoff.rst`` (PIT and gate-run cost, results.export as a
PIT target, R-RES-01's provenance reading, ``D-013``'s ``--help`` launches,
fixture prerequisites, ``.gitattributes``).
