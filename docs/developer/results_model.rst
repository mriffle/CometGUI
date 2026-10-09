.. _dev-results-model:

=============
Results model
=============

How CometGUI reads Percolator's result tables and weights, decides which rows
a q-value filter passes, holds a table of any size without holding its rows,
shows it a page at a time and exports it -- **as built** in Phase 10. In one
line: there is one table reader, one q-value predicate and one display-filter
state; a store answers page-sized queries over a raw table it only ever reads,
in memory up to 100 000 rows and from an off-heap index above that; and
filtering and exporting never launch a process or write under ``outputs/``.

.. note::

   **State after Phase 10 -- Results Model and UI**, written on 2026-10-09
   (work unit 10) against the tree at ``af70dd3``. The design decisions cited
   as ``P10-n`` are in ``handoffs/PHASE-10-worklog.rst``, with every work
   unit's sign-off. Every measured number below was produced on this
   project's Debian 12, x86-64 host (64 cores, JDK 25); nothing on this page
   has run on Windows or macOS (see `What has run, and where`_). The phase's
   falsifiability harness (work unit 11, design decision P10-12) is
   ``scripts/verify-results-gates.sh``; see `Which tests prove the exit
   gate`_. ``specification.rst`` (*Q-value result
   filters*, *Learned feature weights*, *Result indexing*, ``R-RES-01``..\
   ``04``, ``R-PERC-08``/``09``) is the authority on what is required; where
   the build differs, this page says so.

The user's view of the same machinery is :doc:`../results` and
:doc:`../learned_feature_weights`. The on-disk formats -- ``results/``,
``view-state.json``, ``exports/`` and the export sidecars -- are specified in
:doc:`../reference/project_format`, and the ``finalise-results`` step in
:doc:`workflow_engine`; this page links to them rather than repeating them.

.. contents:: Contents
   :depth: 2
   :local:

Where the code is
=================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Package (module)
     - What it holds

   * - ``org.cometgui.results.parser`` (``cometgui-results``)
     - The one table reader, ``ResultTableReader`` (streaming
       ``open``/``next``/``forEach``, ``readAll`` for a table known to be
       small, and each row's byte offset and length for the disk index, over
       the strict ``Utf8Lines``); ``ResultRow``, ``ResultTableHeader``,
       ``ResultColumn``; ``QValue`` and ``DecimalText``;
       ``SpectrumReference`` (source base, scan, charge and rank read from a
       ``PSMId``, from the right); the one weights reader, ``WeightsReader``
       -> ``LearnedWeights``; the weights summary, ``WeightsSummary``,
       ``FeatureWeights``, ``SignConsistency``. A PIT target.

   * - ``org.cometgui.results.filtering`` (``cometgui-results``)
     - The display filters: ``QValueFilter`` (sealed; its ``classify`` is the
       one comparison of a q-value with a cutoff), ``PsmQValueFilter``,
       ``PeptideQValueFilter``, ``DisplayFilters``, ``Visibility``,
       ``FilterCounts``, ``FilterTally``. A PIT target.

   * - ``org.cometgui.results.filtering.store`` (``cometgui-results``)
     - The store (P10-4): ``ResultStore``, ``ResultStores`` (the factory and
       the threshold), ``ResultQuery``, ``ResultPage``, ``ResultSort``,
       ``TextFilter``, ``Category``, ``TableKind``, ``RowKey``;
       ``InMemoryResultStore``; ``DiskResultStore`` with ``DiskIndex``,
       ``SortFile``, ``RawIdentity``, ``IndexProblem``,
       ``ResultIndexException``, ``CheckedFiles``. Inside the
       ``results.filtering`` PIT target, so mutation-gated with no POM change.

   * - ``org.cometgui.results.export`` (``cometgui-results``)
     - Export (P10-7): ``ResultExporter``, ``TableExport``, ``WeightsExport``,
       ``WeightsTable``, ``ExportSidecar``, ``ExportFiles``, ``ForwardCopier``,
       ``ExportVocabulary``. **Not** a POM PIT target (the brief forbade
       changing the POM's PIT configuration); unit 6 ran PIT on it by hand.

   * - ``org.cometgui.domain.run`` (``cometgui-domain``)
     - ``RunLayout``'s ``results/``, ``results/index/``,
       ``results/view-state.json`` and ``exports/`` paths (P10-6).

   * - ``org.cometgui.workflow.storage``, ``.steps`` (``cometgui-workflow``)
     - The view state: ``ViewStateJson``, ``ViewStateStore``,
       ``ViewStateReading``. The step: ``ResultSteps.FinaliseResults``.
       ``RunResultFiles`` -- where a run's four tables and weights are, for
       readers outside the package.

   * - ``org.cometgui.ui.viewmodel.results`` (``cometgui-ui``)
     - ``DisplayFiltersViewModel`` (the interface's one filter state),
       ``ResultTableViewModel`` (one table as a page), ``WeightsViewModel``,
       ``ResultsViewModel`` (run choice, view state, exports), the port
       ``ResultsPort`` with ``ResultsRun`` and ``OpenedResults``, and the
       values they publish (``TablePage``, ``TableCounts``,
       ``ResultRowView``, ``ResultsColumn``, ``ColumnState``,
       ``CopyOutcome``, ``WeightsRowView``, ``WeightsColumn``,
       ``WeightsSort``). Coverage-gated with the rest of ``cometgui-ui``.

   * - ``org.cometgui.ui.controls.results`` (``cometgui-ui``)
     - ``ResultsPane`` and ``WeightsPane``: thin views. Neither reads a
       number, compares a q-value, parses or hashes.

   * - ``org.cometgui.app.config`` (``cometgui-app``)
     - ``ProjectResultsPort``, the ``ResultsPort`` over the session's
       project; its wiring, with the session's one ``CachingHashService``
       shared with the engine and the runs the engine is executing
       (``SessionEngine.executingRuns``).

**Module edge added.** ``cometgui-results`` now depends on
``cometgui-provenance`` (for the one JSON writer, the one atomic writer and the
one provenance event log, P10-7). ``cometgui-provenance`` depends on
``cometgui-domain`` alone, so the edge makes no cycle; the edge is commented in
``cometgui-results/pom.xml``. No storage library was added: the only runtime
third-party dependencies are still JavaFX and AtlantaFX, and the
specification's "for example SQLite" is met by a file format of the project's
own (`The disk store`_).

One of everything (P10-1)
=========================

Each of these exists once, and every unit's sign-off injected a defect into it
and required the new code's tests to go red as well:

* **One table reader** (``ResultTableReader``) and **one weights reader**
  (``WeightsReader``), extended in place. The in-memory store, the disk index,
  the read-back of a single row, the export's classification, the
  ``finalise-results`` step and the factory's row count all parse through it.
* **One q-value predicate**: ``QValueFilter.classify(QValue.Status, double)``.
  ``classify(QValue)`` and ``classify(ResultRow)`` delegate to it; the
  in-memory store, the disk store (which keeps a status and a ``double`` per
  row in its index), the export and every count call it. No other product code
  compares a q-value with a cutoff.
* **One filter value type** (``DisplayFilters``) and **one display-filter
  state in the interface** (``DisplayFiltersViewModel``), built once by the
  composition root and held by both the Percolator section
  (``PercolatorViewModel`` delegates to it; its Phase 09 tests are unchanged
  and green) and the Results section.
* **One hasher** (the ``HashService`` port; the session's
  ``CachingHashService`` in the application), **one JSON writer**, **one
  atomic writer** and **one provenance event log**, all from
  ``cometgui-provenance``.
* **One step graph and run store**: ``finalise-results`` is a step of the
  existing graph, and the view state is a file of the existing run layout.

This is a review and sign-off rule, not an architecture test: a hand-written
``q <= cutoff`` uses nothing an ArchUnit rule can see.

Q-values and the predicate
==========================

``QValue.of(text)`` keeps the field verbatim and gives it one status:

.. list-table::
   :header-rows: 1
   :widths: 18 82

   * - Status
     - When
   * - ``KNOWN``
     - A decimal number in ``Locale.ROOT`` form -- ``DecimalText``'s
       ``[+-]?(digits[.digits]|.digits)([eE][+-]?digits)?`` -- within ``[0,
       1]``. A positive decimal too small for a ``double`` (``1e-400``) is
       known and held as ``Double.MIN_VALUE``, so it fails a cutoff of 0 and
       sorts after an exact 0.
   * - ``MISSING``
     - The field is empty.
   * - ``UNPARSABLE``
     - Not such a decimal: ``NaN``, ``nan``, ``-nan``, ``inf``, ``-inf``,
       ``Infinity``, ``0,01``, any other text.
   * - ``OUT_OF_RANGE``
     - A decimal outside ``[0, 1]``, including one too large for a
       ``double`` (``1e400``) and a negative one too small for it
       (``-1e-400``).

``classify`` returns ``PASSES`` for a known value ``<=`` the cutoff,
``FAILS`` for one above it and ``UNKNOWN_Q_VALUE`` for every other status
(``R-RES-02``). Given ``KNOWN`` with a value outside ``[0, 1]`` it throws: no
``QValue`` produces that pair, and classifying it would silently include or
exclude a row. The comparison is between ``double``\ s -- the row's value as
``Double.parseDouble`` reads it and the cutoff's nearest ``double`` -- which
agrees with exact decimal comparison for any two decimals a ``double`` can
tell apart, far beyond the six significant digits Percolator writes; the large
fixture's generator checks this for every one of its 1 400 000 rows
(0 disagreements).

**The cutoff** is a ``BigDecimal`` held as typed (``0.050`` stays ``0.050``
in the view state and the export), parsed by ``PsmQValueFilter.parse`` /
``PeptideQValueFilter.parse`` and range-checked to ``[0, 1]`` inclusive;
the default is ``QValueFilter.DEFAULT_CUTOFF`` = 0.01 for both. A cutoff is
never a ``TestFdr`` or ``TrainFdr`` and has no conversion to one
(``AC-RES-05``).

The store
=========

The contract
------------

``ResultStore`` is one Percolator table, opened once and then asked for one
page at a time. Its Javadoc states the contract and ``ResultStoreContract``
(an abstract test class, run by ``InMemoryResultStoreTest`` and
``DiskResultStoreTest``, 447 and 444 tests at unit 3) tests it, over 22 real
and constructed tables:

* every row of the file is in the store, read by the one reader; a row with an
  unknown q-value is kept;
* where a row falls is decided by ``QValueFilter.classify`` and nothing else;
* ``counts(filter)`` -- total, passing, failing, unknown -- is over the whole
  table, whatever the category, text and page;
* a query's pages, read in turn, are exactly its matching rows in its order:
  none twice, none missed;
* the raw file is never written, moved or locked, and is byte-identical after
  opening, any number of queries and closing;
* queries are safe from any number of threads; after ``close`` every method
  throws ``IllegalStateException``.

**A query** (``ResultQuery``) is a filter, a ``Category`` (``PASSING`` -- the
default --, ``UNKNOWN_Q_VALUE``, ``FAILING``, ``ALL``; P10-5), a text, a
``ResultSort``, an offset and a limit. The limit is at most
``MAX_PAGE_SIZE`` = 5000, so no query can return every row of a large table;
``DEFAULT_PAGE_SIZE`` = 200 is the interface's page. ``ResultPage`` is the
page, the number of matching rows and the counts. ``positionOf(key, query)``
is a row's position among a query's matches, which is how the interface keeps
a selected row in view after a change.

**Which filter.** ``TableKind`` (``TARGET_PSMS``, ``DECOY_PSMS``,
``TARGET_PEPTIDES``, ``DECOY_PEPTIDES``) decides which filter applies, and
``TableKind.check`` refuses the other one, so the PSM and peptide filters
cannot be crossed by a wiring mistake (``AC-RES-03``).

**Order** (``ResultSort``): ten columns -- file order, ``PSMId``, source file,
scan, charge, peptide, proteins, score, q-value, PEP. Text by
``String.compareTo`` (UTF-16 code unit, no locale), proteins as a list; numbers
numerically, ``-0`` equal to ``0``, the q-value by value; **a row with no value
last in both directions**; **ties by file order ascending in both
directions**, so descending is not ascending reversed.

**Text** (``TextFilter``): a row matches when its ``PSMId``, peptide or any
protein contains the text, compared as ``String.regionMatches(true, ...)``
compares -- case-insensitive per character, no locale. Scores and q-values
are never matched as text.

**Identity** (``RowKey``): a row's line number in the raw file, the header
being line 1. It does not change with filter, category, text or sort, nor
between two stores opened on one file.

The threshold
-------------

``ResultStores.open(file, kind, indexDirectory, hasher)`` counts the table's
rows with the one reader, stopping at ``IN_MEMORY_ROW_LIMIT + 1``, and opens
the **in-memory store at or below** ``IN_MEMORY_ROW_LIMIT`` = **100 000** rows
and the **disk store above it**. Deciding costs at most a parse of 100 001
rows: about 0.1 s, 0.3 s in a fresh JVM.

Why 100 000 (measured 2026-10-08): the in-memory store holds every row as
parsed objects, about 670 bytes of heap a row -- the 1 000 000-row large
fixture took about 670 MB and 1.9 s to open in memory -- and a run opens up to
four tables at once. At 100 000 rows a table costs about 67 MB and 0.2 s, so
four stay near 270 MB, within a desktop JVM's default heap. The real K562
search's largest table has 3897 rows. Above the limit the disk store's costs
are small beside what the heap would hold, and its heap stays flat
(`Budgets`_).

``ResultStores.open(..., limit)`` takes another limit; product code passes the
constant, and ``FinaliseResultsTest`` passes a low one so that a small table
takes the disk path. ``inMemory`` and ``onDisk`` open one kind regardless of
size, for tests.

The in-memory store
-------------------

``InMemoryResultStore`` reads every row with ``readAll`` and answers queries
from the list: classification through ``classify``, sorting by
``ResultSort``'s comparator, the text filter through ``TextFilter``.

The disk store
--------------

``DiskResultStore`` keeps no object per row. Opening it hashes the raw table
through the injected ``HashService`` (``RawIdentity``: size, modification time
in nanoseconds, SHA-256; the size and time are read again after hashing, and a
change is ``RAW_CHANGED_WHILE_INDEXING``), then looks for its index in the
index directory and checks it; an index that is absent or fails any check is
rebuilt by one pass of the one reader, written to a temporary file and moved
into place. The index directory must not be the raw table's own directory
(refused), and a run's is ``results/index/``. The index and every sort file are
memory-mapped read-only through an FFM ``Arena``, off the heap.

A query walks the mapped order, classifies each row from its indexed status
and value through ``classify(QValue.Status, double)``, and reads back **only
the page's rows**, by offset, from the raw table -- opened once, read-only,
read by position -- parsing each with the one reader. Every row read back is
checked against its index record (q-value status and value, score, PEP); a
mismatch, or a raw table whose size or modification time has changed since
opening, is a ``RAW_CHANGED_SINCE_OPENED`` refusal rather than a wrong row. A
text filter is one bit per row, made by one streaming pass of the reader
through ``TextFilter``; the last 8 texts' bit sets and the last 64 filters'
counts are kept.

**The index file** ``<kind>.index`` (``DiskIndex``; ``<kind>`` is
``target-psms``, ``decoy-psms``, ``target-peptides`` or ``decoy-peptides``),
big-endian:

.. list-table::
   :header-rows: 1
   :widths: 14 10 76

   * - Offset
     - Bytes
     - Header (96 bytes)
   * - 0
     - 8
     - magic ``CGRESIDX``
   * - 8
     - 4
     - format version, 1
   * - 12
     - 4
     - record size, 56
   * - 16
     - 4
     - table kind (``TableKind`` ordinal)
   * - 24
     - 8
     - raw table size in bytes
   * - 32
     - 8
     - raw table modification time, nanoseconds since the epoch
   * - 40
     - 32
     - raw table SHA-256
   * - 72
     - 8
     - rows
   * - 80
     - 4
     - CRC-32C of the body
   * - 84
     - 4
     - CRC-32C of header bytes 0..83

Bytes 20..23 and 88..95 are zero. The body is one 56-byte record per row in
file order (row *i* is line *i* + 2): byte offset of the row in the raw table
(8), its length without the line terminator (4), the q-value status (1, the
``QValue.Status`` ordinal), three zero bytes, then the q-value, score and PEP
(8 each, ``NaN`` when not known or not a decimal number), the scan from the
``PSMId`` (8, -1 when not in ``SpecId`` shape), the charge (4, likewise) and
four zero bytes. Text is not indexed: it is read back from the raw table. At
most ``Integer.MAX_VALUE`` rows (``TOO_MANY_ROWS``).

**A sort file** ``<kind>.sort-<column>-<direction>`` (``SortFile``; for
example ``target-psms.sort-score-descending``) is a permutation: a 72-byte
header -- magic ``CGRESPRM``, format version 1, table kind, column, direction,
rows, the raw table's SHA-256, CRC-32C of the body and of header bytes 0..67
-- then one big-endian ``int`` per position, the row index at that position.
A column's ascending and descending files are built together on the first use
of either, by an external merge sort: keys arrive in file order, are sorted in
chunks of at most ``CHUNK_BYTES`` = 8 MiB of estimated key bytes, spilled to
run files in the index directory, and merged; the descending file is derived
from the ascending one (groups of equal values reversed, missing values still
last). File order needs no file.

**When an index or sort file is not used.** Each check failing is an
``IndexProblem``, and the file is rebuilt, never trusted:

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - ``IndexProblem``
     - Meaning
   * - ``ABSENT``, ``TRUNCATED``, ``NOT_AN_INDEX``
     - No file; a length that disagrees with the header's row count; wrong
       magic.
   * - ``HEADER_DAMAGED``, ``BODY_DAMAGED``
     - A CRC-32C does not match.
   * - ``OTHER_FORMAT``, ``OTHER_TABLE``
     - Another format version or record size; another table kind or sort.
   * - ``RAW_SIZE_CHANGED``, ``RAW_TIME_CHANGED``, ``RAW_CONTENT_CHANGED``
     - The raw table's size, modification time or SHA-256 differs from the
       index's record (a sort file records the SHA-256 only).
   * - ``NOT_A_PERMUTATION``
     - A sort file does not hold every row exactly once; the last check made.
   * - ``RAW_CHANGED_WHILE_INDEXING``, ``RAW_CHANGED_SINCE_OPENED``,
       ``TOO_MANY_ROWS``
     - The cases where the store **refuses** with a ``ResultIndexException``
       rather than rebuilding -- together with a freshly built file that
       fails its own checks.

A rebuilt index deletes the table's sort files with it. ``DiskResultStore``'s
``indexReport`` and ``sortReport`` say whether a file was reused or built, and
why. The index files are a derived cache, not provenance: they are not
declared outputs of any step, and deleting ``results/index/`` loses nothing.

Budgets
-------

**The model's budget** (``DiskStoreBudgetTest``, unit 3, P10-11). The heap is
enforced, not estimated: ``BudgetProbe`` runs in a **child JVM at
``-Xmx64m``**, opens the 1 000 000-row large fixture through
``ResultStores.open`` (so the threshold picks the store), checks the counts at
all eight manifest cutoffs, reads three pages by score and three by peptide
and applies a text filter, timing each. The **negative control** runs
``ResultTableReader.readAll`` over the same file in the same 64 MB and must die
of ``OutOfMemoryError`` -- so the fixture provably crosses what the budget can
hold.

.. list-table::
   :header-rows: 1
   :widths: 46 18 18

   * - Step, in the ``-Xmx64m`` child
     - Budget
     - Measured
   * - Cold open (hash, one indexing pass, map)
     - 8 s
     - 2.2 s
   * - Warm open (hash, index check, map)
     - 3 s
     - 0.7 s
   * - Counts at all eight cutoffs
     - 1 s
     - 0.1 s
   * - Three pages by score, sort files built on first use
     - 3 s
     - 0.7 s
   * - Three pages by peptide, external merge sort on first use
     - 10 s
     - 2.7 s
   * - A new text filter's first page and match count
     - 6 s
     - 1.5 s

Measured 2026-10-08, warm page cache. The disk store completes the same run at
``-Xmx16m``; ``readAll`` fails at 64, 256 and 512 MB and completes only at
1 GB. Each budget is three to four times its measurement.

**The interface's budget** (``ResultsLargeFixtureUiTest``, unit 8). The large
fixture as a constructed run's target PSM table, through the real composition
root, driven by the robot, timed from the first event to the answer on screen;
the table's items are asserted never to exceed one page (200) and the used
heap, measured after a forced collection before opening and after every
action, to grow by less than 64 MB.

.. list-table::
   :header-rows: 1
   :widths: 46 18 18

   * - Action, on screen
     - Budget
     - Measured
   * - Open the run (cold: hash, index, map)
     - 15 s
     - 3.4 s
   * - A filter change (mostly typing the cutoff)
     - 4 s
     - 0.7--1.0 s
   * - Sort by score (first use; reversed)
     - 6 s
     - 1.3 s; 0.3 s
   * - Sort by peptide
     - 15 s
     - 3.4 s
   * - A page move
     - 2.5 s
     - 0.4--0.6 s
   * - Heap growth across the whole sequence
     - 64 MB
     - 3 MB

Measured 2026-10-09, headless Monocle; the most items the table ever held was
200. For comparison, the table in memory would cost about 670 MB and a list of
every passing row about 150 MB. (Unit 8's sign-off recorded one run at 3.3 s
to open and 3.3 s for the peptide sort; the figures above are the test's own
Javadoc.)

**These are local gates on one host**, set from measurement with margin.
The specification's *Performance and resource tests* require thresholds from
stable dedicated nightly runners; those, and ``AC-RES-10`` as a whole, are
Phase 15's (``scripts/ci/nightly-performance.sh`` and
``nightly-large-dataset.sh`` are still stubs).

``finalise-results``
====================

Planned whenever Percolator is, in a search and in a derived run, after
``parse-percolator`` and before ``finalise-provenance`` (P10-6). It opens each
table the run's command produced through ``ResultStores.open`` with the run's
``results/index/`` -- so a table above the threshold has its disk index built
and checked during the run, and the Results section later reuses it -- and
records, in its ``stage.finished`` details, the cutoffs (the defaults) and per
table the rows, the store (``memory`` or ``disk``) and the four counts, plus
the weights' split and feature counts. It only reads ``outputs/``. Details,
keys and tests: :doc:`workflow_engine`.

The interface
=============

**Pages, never every row** (P10-9, ``R-RES-03``). ``ResultTableViewModel``
holds one ``TablePage`` -- at most ``PAGE_SIZE`` = 200 rows -- fetched by a
query whose limit is the page size. The store is asked only on the background
executor and every answer is applied on the JavaFX executor; each request
carries a generation number and an answer to an older one is dropped.
``ResultsPane``'s ``TableView`` items are that page, replaced with ``setAll``;
its columns are made unsortable (a ``TableView`` would sort the 200 rows it
holds and call a million-row table sorted) and each heading is a button
calling ``sortBy``: ascending, descending, file order.

**What re-queries.** A change of the table's own filter (the other filter is
ignored), category or sort, or an applied text filter (applied on Enter,
never per keystroke: a new text costs the disk store a pass) asks the store
for counts and one page. Paging asks for one page. Column visibility and
selection ask for nothing.

**Selection** is a set of ``RowKey``\ s with an anchor (the most recently
selected). After a filter, category, text or sort change the selection is kept
whole; if the new query matches the anchor the view moves to the page holding
it (``positionOf``), otherwise it shows the first page and says the anchor is
selected but hidden. ``ResultsPane`` re-selects the page's selected rows
whenever the page or the selection changes. **Copy** is the selected rows of
the current page as tab-separated text, header of the shown columns' headings,
cells as displayed; a selected row not on the page is not copied and the
outcome counts it.

**Columns** (``ResultsColumn``): PSM tables ``PSMId``, source file, scan,
charge, peptide, proteins, score, q-value, PEP; peptide tables the last five.
Cells are the raw text, never reformatted. The source file is the run's
spectrum input whose ``-N`` base begins the ``PSMId`` (its file name, or its
path when two inputs share a name; the base itself when no input matches; a
derived run maps its source run's bases). **The source-file column cannot be
hidden when the run has more than one spectrum file** (``R-RES``, *PSM
table*). Comet scores and supporting-PSM counts are not shown (P10-9,
deferred).

**Run choice, view state and export** are ``ResultsViewModel``'s: it lists
runs through the port (newest first, the newest opened by default; the
application refreshes at start and at each run's start and end), opens a
run's stores and closes the previous run's, applies the run's saved view state
to the shared filters, and on each later filter change saves it through the
port -- the only write a filter change causes. A refused ``view-state.json``
is never written. Exports are refused while the run is executing.

``ProjectResultsPort`` (``cometgui-app``) lists a run when ``run.json`` reads
and at least one of the four tables is under ``outputs/percolator/``
(``RunResultFiles``); reads take no project lock, writes (view state, export)
take it. **It is given no process service**: a filter change reaches it at
most as ``saveViewState``. Gate 2 is proved through the real composition root
with a recording ``ProcessRunner`` in front of the real process service
(P10-10, ``ResultsNoProcessUiTest``).

**Weights** (P10-8, ``R-PERC-08``/``09``). ``WeightsSummary.of(LearnedWeights)``
computes every value once, over the normalised weights, with the split count
read from the file: mean signed and mean absolute as ``double`` sums in split
order divided by *n*; the **population** standard deviation (divide by *n*);
``SignConsistency`` with zero (``-0.0`` included) as neither sign, so zeros
mixed with one sign are ``MIXED``; competition ranking by mean absolute weight
under exact ``double`` equality, ties listed in file order; the bias ``m0``
listed and unranked. ``WeightsViewModel`` only formats (4 decimals per split,
6 per statistic, ``Locale.ROOT``) and sorts on the summary's numbers, the bias
last when sorting by rank. ``WeightsPane`` draws **no chart**: a chart would
need numbers, and the view is handed text; recorded as deferred.

Export
======

``ResultExporter`` (P10-7, ``R-RES-04``, ``R-RES-01``, ``R-PERC-07``) writes a
filtered table or the weights as a new file in ``exports/`` with a JSON
sidecar and one ``export.written`` event in ``provenance/events.log``. The
file names, the sidecar's members, the event's payload keys and the ordering
and failure rules are specified in :ref:`ref-project-format-exports`; the event
type is in :ref:`ref-provenance-format-event-types`. The internals, briefly:

* **Rows are bytes, not re-serialised rows.** The table is streamed twice --
  once to hash it (MD5 and SHA-256 in one pass), once to copy it --
  ``ForwardCopier`` copying each selected row's bytes, terminator included,
  from a read-only channel in file order; a row's category is
  ``QValueFilter.classify`` of the row the one reader parsed. A table whose
  size or modification time changes during the copy is refused.
* **Written once, through the one atomic writer**: a temporary file in
  ``exports/``, forced, renamed; ``ExportFiles`` chooses a name no file holds
  (``-2``, ``-3`` ... appended) under one process-wide lock held for the whole
  export.
* **Recorded or removed**: if hashing the export, writing the sidecar or
  appending the event fails, the export and its sidecar are deleted -- an
  export is never reported as made unless its event was recorded.
* **Weights**: ``WeightsTable`` writes the summary with ``Double.toString``;
  the export refuses an artefact whose content changed since it was
  summarised.
* **Only for a run not executing**: the engine holds the run's event log open
  while it runs; both the view-model and the port refuse.

The fixtures
============

**The large fixture** (P10-2, unit 1) is synthetic and generated, never
committed: ``scripts/fixtures/large-results-fixture.py`` -- standard library
only, deterministic (seed 20261008), so it shares no code with the Java under
test -- writes ``scratch/phase10/large/psms.tsv`` (1 000 000 rows, four source
bases including one with digits and underscores and one with a space, 1-4
proteins a row), ``peptides.tsv`` (400 000 rows) and ``manifest.json`` (each
file's SHA-256, size and row count, and the **expected counts at eight
cutoffs computed by the generator from what it assigned**, in Python
``decimal``). Exact ``0``, ``0.005``, ``0.01`` and ``1`` occur in known
numbers, with neighbours one unit in the sixth significant digit either side
of 0.005 and 0.01, and every unknown kind: 1478 unknown q-values in
``psms.tsv`` and 594 in ``peptides.tsv``, over ten kinds. To make it, from
the repository root::

    python3 scripts/fixtures/large-results-fixture.py
    python3 scripts/fixtures/large-results-fixture.py --self-check

(about 15 s; ``--self-check`` re-counts the written files with a separate
pass, requires the manifest's numbers, and requires a second generation to be
byte-identical). The tests pin the result -- ``psms.tsv``
``4f7aaecd0164bc22be51de1c60dfed3e70ddf65a4283f38a1912c76ece8cfcf4``,
``peptides.tsv``
``bdda5589063d55c9c210f8eba7cd64f1489b95e8fa51fe4a9dccea9255e01e44``,
``manifest.json``
``4298cc2b05b3d86108a9dadf4ae832284a5aff1ca046f49b5c89d2a6ec832d85``
(``org.cometgui.results.testing.LargeFixture``) -- and **fail, never skip**,
naming the command, when a file is absent or differs. Its record is
``cometgui-results/src/test/resources/org/cometgui/results/large-fixture/CONSTRUCTED.txt``.

**The real fixtures.** Phase 00's K562 search (Comet 2026.02.2 over the two
``D-006`` K562 mzML files against the full human reference proteome, then
Percolator 3.07.1 and 3.09) left its Percolator outputs in
``scratch/scientific-path/percolator-{3.07.1,3.09}/``: 3897 target PSMs, 2773
decoy PSMs, 2985 target peptides, 2359 decoy peptides, 3 weight splits of 22
features; 1026 target PSMs and 603 target peptides at q <= 0.01. ``D-006``:
CometGUI does not redistribute spectrum or FASTA data, so this output **stays
in** ``scratch/`` **and is never committed**; the tests hold it to SHA-256s
pinned in ``org.cometgui.results.testing.RealK562`` (and the app's
``K562Outputs``), with the provenance, the digests and the ``awk``-pinned
counts in ``cometgui-results/src/test/resources/org/cometgui/results/real-k562/PROVENANCE.txt``,
and fail, never skip, without it. The checked-in 64-row tables Percolator
3.06.5, 3.07.1 and 3.09 wrote over CometGUI's own synthetic 64 + 64 PIN (Phase
09) and a handful of constructed tables (a shuffled table with every unknown
kind, a two-, four- and one-split weights file, a weights file with ties) are
in the test resources, with their own records.

**Independent counts** (P10-3). Gate items 3 and 7 compare against numbers the
code under test did not produce: the generator's manifest; ``awk`` counts
pinned with their command; and deliberately minimal counters in test code
(``split`` and ``BigDecimal``, no ``org.cometgui.results`` class) --
``IndependentCounter`` (results), ``IndependentCounts`` (ui and app),
``IndependentTableCount`` (workflow) -- each itself checked against the
manifest and the ``awk`` pins. The weights are recomputed in ``BigDecimal``
in ``ResultsWeightsUiTest`` and in Python ``fractions`` in
``cometgui-results/src/test/resources/org/cometgui/results/parser/WEIGHTS-SUMMARY.txt``.

**Files the tests read outside their module**: ``scratch/phase10/large/``
(results, app) and ``scratch/scientific-path/percolator-{3.07.1,3.09}/``
(results, ui, app). Before ``scripts/build.sh`` or ``--only tests`` on a fresh
checkout, generate the first with the command above; the second comes from
Phase 00's scripted run (:doc:`../feasibility/scientific-path`).

Which tests prove the exit gate
===============================

.. list-table::
   :header-rows: 1
   :widths: 6 30 64

   * - Gate
     - What
     - Tests (*unit* unless marked *UI* -- the launched application, driven
       headless -- or *real binaries, Linux only*)

   * - 1
     - Both default filters are 0.01, independent, inclusive at exactly
       0.01, and reject values outside [0, 1]
     - ``org.cometgui.results.filtering.QValueFilterTest`` (nested
       ``Defaults.defaults``, ``Boundary.atDefault``, ``Range.outOfRange``,
       ``Independence.independent``);
       ``org.cometgui.results.filtering.store.ResultStoreContract``
       ``inclusiveAtTheCutoff`` and ``psmAndPeptideFiltersIndependent`` (run
       by ``InMemoryResultStoreTest`` and ``DiskResultStoreTest``);
       ``org.cometgui.ui.viewmodel.results.DisplayFiltersViewModelTest``
       (``defaults``, ``bothEndsAndIndependence``, ``refused``,
       ``sharedWithThePercolatorSection``);
       ``org.cometgui.workflow.storage.ViewStateStoreTest``
       (``outOfRange``, ``boundaries``); *UI*:
       ``org.cometgui.app.gui.ResultsFiltersUiTest`` (``defaults``,
       ``inclusiveAtTheCutoff``, ``outOfRangeAndNonNumbersAreRefused``,
       ``theFiltersAreIndependent``)

   * - 2
     - Changing a filter launches no process
     - *UI*: ``org.cometgui.app.gui.ResultsNoProcessUiTest.changingTheFiltersLaunchesNoProcess``
       (the application's one ``ProcessRunner`` records nothing while both
       filters change and the counts change)

   * - 3
     - Displayed counts match counts computed independently from the raw
       file at 0, 0.005, 0.01 and 1
     - ``ResultStoreContract.countsEqualIndependentCounts`` and
       ``gateCutoffsCovered``;
       ``org.cometgui.results.filtering.store.DiskLargeFixtureTest.countsEqualManifest``,
       ``InMemoryLargeFixtureTest.countsEqualManifest``;
       ``org.cometgui.results.testing.LargeFixtureTest.counterEqualsManifest``,
       ``RealK562Test``;
       ``org.cometgui.ui.viewmodel.results.ResultTableViewModelTest`` (nested
       ``Counts.independentCounts``);
       ``org.cometgui.workflow.steps.FinaliseResultsTest``; *UI*:
       ``org.cometgui.app.gui.ResultsCountsUiTest`` (``theK562RunAtThePinnedCutoffs``,
       ``theK562RunOnItsOwnQValues``, ``theSyntheticRun``); *real binaries,
       Linux only*: ``org.cometgui.app.gui.RealRunUiTest.theResultsAppear``

   * - 4
     - Raw Percolator files byte-identical after filtering and export
     - ``ResultStoreContract.rawFileUntouched``;
       ``org.cometgui.results.filtering.store.DiskIndexInvalidationTest.rawTableUntouched``;
       ``org.cometgui.results.export.TableExportBehaviourTest.aSourceInsideTheRunIsRelativeAndOnlyRead``;
       ``TableExportGateTest.everyCategoryAtEveryCutoff``; *UI*:
       ``org.cometgui.app.gui.ResultsExportUiTest.theRawOutputsAreUntouched``,
       ``ResultsNoProcessUiTest.changingTheFiltersLaunchesNoProcess``

   * - 5
     - An export carries the run ID, the applied cutoff and the before/after
       row counts
     - ``org.cometgui.results.export.TableExportGateTest.everyCategoryAtEveryCutoff``
       (22 tables x 4 cutoffs x 4 categories),
       ``TableExportBehaviourTest.oneEventPerExport``; *UI*:
       ``ResultsExportUiTest`` (``aPeptideExportAtANonDefaultCutoff``,
       ``realExportsAtANonDefaultCutoff``)

   * - 6
     - The large fixture loads and filters within the documented time and
       heap budget, the UI bound to a paged model
     - ``org.cometgui.results.filtering.store.DiskStoreBudgetTest``
       (``largeFixtureWithinBudget``, ``readAllDoesNotFit``);
       ``ResultStoresTest.switchesAboveTheLimit``;
       ``org.cometgui.results.export.LargeExportTest.everyRowInASmallHeap``;
       ``ResultTableViewModelTest`` (nested ``Page.pageBound``); *UI*:
       ``org.cometgui.app.gui.ResultsLargeFixtureUiTest.theLargeFixture``

   * - 7
     - Weights values, ranking and sign consistency match values computed
       independently from the artefact
     - ``org.cometgui.results.parser.WeightsSummaryIndependentTest``
       (``checkedInFiles``, ``realK562``);
       ``org.cometgui.ui.viewmodel.results.WeightsViewModelTest.realK562``;
       ``org.cometgui.results.export.WeightsExportTest.valuesEqualTheSummary``;
       *UI*: ``org.cometgui.app.gui.ResultsWeightsUiTest``
       (``theTwoIndependentComputationsAgree``, ``theK562WeightsOnScreen``,
       ``sortingByMeanAbsoluteGivesTheRankOrder``, ``twoAndFourSplits``,
       ``theExportHoldsTheIndependentValues``)

   * - 8
     - Rows with missing q-values counted and shown as their own category,
       identically in UI and export
     - ``ResultStoreContract.unknownKindsInTheirOwnCategory``;
       ``TableExportGateTest.theUnknownExportHoldsEveryUnknownKind``;
       ``LargeExportTest.unknownRowsByKind``; ``ResultTableViewModelTest``
       (nested ``Counts.unknownCategory``); *UI*:
       ``ResultsExportUiTest`` (``theUnknownCategoryOnScreenAndInTheExport``,
       ``theUnknownCategoryOverSeveralPages``)

The acceptance criteria's entries are in ``docs/traceability-map.toml`` and the
generated :doc:`traceability`. Each work unit's sign-off in
``handoffs/PHASE-10-worklog.rst`` records the production-code injections that
turned these tests red.

``bash scripts/verify-results-gates.sh`` (registered in
``scripts/verify-all-gates.sh`` as ``results``) repeats twenty-two of those
injections, and new ones, at least one per gate item, in a ``git archive``
sandbox: each proved to have reached the compiled class and graded on the
failing assertion's own words in the named test above, with a sibling test
required to stay green where one can show it. Its controls, their
diagnostics and what it leaves out are listed in
:ref:`dev-results-falsifiability`.

What has run, and where
=======================

**Only Linux x86-64.** Everything on this page -- the unit tests, the GUI
tests (headless Monocle), the child-JVM budget test and the real run -- has
executed on this project's Debian 12 host and nowhere else.

* **Real binaries**: ``RealRunUiTest.theResultsAppear`` (``@EnabledOnOs(LINUX)``)
  runs the real Comet and Percolator 3.07.1 through the interface on the two
  ``D-006`` K562 files against the proteome's first 1000 records; the run is
  listed and opened without a restart, its counts equal the independent
  counter's on its raw ``psms.tsv``, and ``finalise-results`` is recorded.
  Every other Phase 10 test reads Percolator output that already exists.
* **Real data**: the Phase 00 K562 outputs, as above.
* **Linux-specific test machinery**: ``OpenFiles`` reads ``/proc/self/fd`` and
  ``/proc/self/maps`` to prove that files are closed and unmapped (it reports
  itself unavailable elsewhere); ``FinaliseResultsTest`` uses a POSIX shell
  stand-in for Percolator (``@EnabledOnOs({LINUX, MAC})``); several export
  tests depend on POSIX permissions. The disk store's memory mapping and its
  replacement of index files have never run on Windows, which restricts
  deleting or replacing a file while it is mapped; that path is untested.

Known limits
============

Recorded rather than hidden:

* **The real CometGUI run cannot tell 0.01 from a smaller cutoff**: on the
  first-1000-records K562 search no row reaches q <= 0.01 (smallest target PSM
  q-value 0.018648). The constructed and Phase 00 tables, which do have rows on
  and around the cutoffs, are what distinguish them; and the pinned K562
  cutoffs alone do not catch a ``<=`` made ``<`` (no K562 q-value equals a
  pinned cutoff) -- ``ResultsCountsUiTest.theK562RunOnItsOwnQValues``, at the
  run's own smallest q-value 0.00112905 where 599 target PSMs sit exactly on
  the cutoff, does.
* The in-memory store compares scans as ``double``, the disk store as ``long``;
  they differ only above 2\ :sup:`53`.
* Every disk-store open hashes the raw table (about 0.5 s at 147 MB), unless
  the application's caching hasher can answer from its cache.
* ``finalise-results``' ``store`` detail repeats the factory's ``rows > limit``
  rule for display; the factory is the only place that decides.
* Two CometGUI **processes** exporting the same table, cutoff and category of
  one run in the same millisecond could choose one name; a process killed
  between an export's rename and its event leaves an export without a sidecar
  or event (incomplete, not an export of record).
* Exports are possible only for runs that are not executing.
* ``R-RES-01``'s "written to provenance when used to generate an export" is met
  by the ``export.written`` event in ``events.log``; ``provenance.json`` is not
  rewritten after the run (unit 6; for tier 1 to confirm).
* A run whose ``run.json`` cannot be read is left out of the run list
  silently.
* The copy test asserts the outcome message, not the system clipboard's
  contents.
* Not built, by decision: Comet scores in the PSM table and supporting-PSM
  counts in the peptide table (P10-9); the weights bar chart (unit 8);
  protein-level results (out of scope for release 1); the ``R-PERC-08``
  stdout fallback for a build without a weights file.
* The phase's falsifiability harness, ``scripts/verify-results-gates.sh``
  (P10-12, unit 11), grades items 4, 5, 7 and 8 on the unit tests, not on the
  GUI export and weights tests, and grades the budgets' time limits only as
  far as the factory's choice of store (see
  :ref:`dev-results-falsifiability`).
