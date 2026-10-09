.. _ref-project-format:

==============
Project format
==============

This page is the **on-disk format** of a CometGUI project and of every run in
it: where each file lives, every member of ``project.json`` and ``run.json``
with its type and meaning, the lock file, and the rules a reader applies. It is
written so that someone who has never seen this repository can find a run's
inputs, outputs and logs, and parse its records, without reading the Java.

The provenance record inside each run has its own reference,
:doc:`provenance_format`.

**This page describes schema version 1** of ``project.json``,
``project.lock`` and a run's ``results/view-state.json``, and **schema
versions 1 and 2** of ``run.json`` -- version 2 is a *derived* run's (see
`A derived run`_). See :ref:`ref-project-format-versions`.

.. contents:: Contents
   :depth: 2
   :local:

The layout on disk
==================

A project holds mutable user intent and immutable run records
(``R-RUN-03``..``R-RUN-06``)::

    MyProject/
        project.json                 the project's identity (written once)
        project.lock                 who has the project open (R-RUN-05)
        presets/                     parameter presets (a later phase)
        index-cache/<key>/           cached Comet indexes (see `The index cache`_)
        runs/
            20260828T231500Z-run-0001/
                run.json             the run's identity and attempts
                parameters/
                    comet.params     the canonical parameter file (written once)
                    percolator-settings.json
                                     the Percolator settings (written once; a run
                                     with Percolator only -- see below)
                inputs/
                    pin/merged.pin   the merged PIN Percolator reads
                outputs/
                    comet/<base>.pep.xml
                    comet/<base>.pin
                    percolator/      Percolator's raw outputs, read-only once it
                                     succeeded (a run with Percolator only)
                logs/
                    comet-01.log     one log per Comet invocation (see below)
                    comet-02.log
                    percolator.log   the Percolator invocation's log
                provenance/
                    provenance.json  the provenance manifest
                    provenance.rst   the provenance report
                    events.log       the provenance event log
                results/             what CometGUI derives from the results (see below)
                    index/           the result stores' index files
                    view-state.json  the run's display-filter values
                exports/             filtered exports, new files, never overwritten

**Where a project is.** In Phase 08 the application has exactly one project
per session and no way to choose another: ``projects/default`` under the
application data directory -- ``%APPDATA%\CometGUI`` on Windows (else
``<home>\AppData\Roaming\CometGUI``), ``<home>/Library/Application
Support/CometGUI`` on macOS, and ``$XDG_DATA_HOME/cometgui`` (else
``<home>/.local/share/cometgui``) on Linux and anything else -- beside the
Tool Manager's cache and never next to the scientist's own files. It is created
(``project.json`` and ``runs/``) and locked the first time the Run section's
pre-run check finds a Comet to run, not when the window opens, and the lock is
held until the application stops (``ProjectSession`` in ``cometgui-app``). Only
the Linux location has ever been created; the Windows and macOS ones are
computed and tested as paths only.

**A run directory's name** is the run's creation time in UTC to the second,
``yyyyMMdd'T'HHmmss'Z'``, a hyphen, and the run id: ``20260828T231500Z-run-0001``.
The timestamp comes first so that a directory listing sorts runs by age; the
run id makes the name unique. A reader can rely on the name agreeing with the
``created`` and ``runId`` members of the ``run.json`` inside it: CometGUI
refuses a ``run.json`` that names another directory.

**Inputs are recorded, never copied** (``R-RUN-03``). Spectrum files and the
FASTA stay where the user keeps them; ``run.json`` records each one's canonical
path, size, modification time, MD5 and SHA-256.

**Comet's outputs** for spectrum file ``nn`` are named after its *base name*,
which Comet receives as ``-N<run>/outputs/comet/<base>``:

* the base name is the file name without its spectrum extension (``.mzML``,
  ``.mzXML``, ``.mgf``, ``.ms2``, ``.cms2``, ``.bms2``, ``.raw``, in any case);
  a file with none of those extensions keeps its whole name;
* base names are distinct ignoring case and Unicode normalisation, because the
  default file systems of macOS and Windows treat ``A.pin`` and ``a.pin`` as one
  file;
* a collision is resolved in input order: every file's own base name is
  reserved first, and a later file whose name is taken gets the smallest
  ``_2``, ``_3`` ... that is nobody's name. ``a.mzML``, ``A.mzXML``, ``a.mgf``
  become ``a``, ``A_2``, ``a_3``; ``a.mzML``, ``A.mgf``, ``a_2.mzML`` become
  ``a``, ``A_3``, ``a_2``;
* a name whose base would be empty, ``.`` or ``..``, or would contain ``/``,
  ``\`` or a control character, is refused: its outputs could land outside
  ``outputs/comet/``.

Each spectrum entry of ``run.json`` records the base name it was given.

The provenance event log is ``provenance/events.log``. Phase 04 left that name
to its callers; it is now a constant of the run layout.

Where the logs differ from the specification
--------------------------------------------

**This is a divergence from the specification, stated rather than hidden.**
The specification's *Project model* shows a Comet invocation's logs as
``logs/comet.<spectrum-basename>.{stdout,stderr}.log``. CometGUI writes one log
per invocation instead, ``logs/comet-<nn>.log``, where ``nn`` is the spectrum
file's 1-based position with at least two digits:

* the process service writes **one** timestamped log per process, each line
  tagged with the stream it came from, because two separate files lose the
  order in which a tool's two streams interleaved;
* a log is named after the invocation's stage identifier, which must match
  ``[A-Za-z0-9_-]{1,64}`` and so cannot carry an arbitrary spectrum file name;
* a retried invocation's log is ``comet-<nn>.1.log``, ``comet-<nn>.2.log`` ...,
  because the process service never overwrites a log.

``run.json`` maps each ``nn`` back to its spectrum file and base name (the
``stageId`` member of each spectrum entry). The divergence is design decision
P8-3 of the Phase 08 work log, escalated there as a proposed specification
amendment; until that amendment is made, this paragraph is the record of it.

Percolator's files
------------------

A run whose request carries a Percolator half (``SearchRequest.percolator``,
Phase 09) adds three things to its directory; a Comet-only run has none of
them.

``parameters/percolator-settings.json`` -- the Percolator scientific
settings the run executes with (``R-RUN-06``). It is written once, when the run
is prepared, by the one JSON writer through the one atomic writer; a second
write is refused with the file untouched, and ``run-percolator`` re-hashes it
before Percolator starts and refuses a file whose SHA-256 or size differs from
what was written, naming the file and both digests. It is canonical -- the same
settings always give the same bytes -- and its SHA-256 is the run's
``percolator-settings`` fingerprint input, so a changed setting reruns
Percolator and nothing upstream of it::

    {
      "schemaVersion": 1,
      "settings": {
        "test-fdr": "0.01",
        "train-fdr": "0.01",
        "random-seed": "1",
        "maximum-iterations": "10",
        "thread-count": "3"
      },
      "downstreamStages": [
        "limelight-conversion"
      ]
    }

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Member
     - Meaning

   * - ``schemaVersion``
     - ``1``, the first member. The same policy as ``run.json``'s applies to a
       change of format.
   * - ``settings``
     - One member per Percolator setting, in this fixed order, named by the
       setting's identifier: ``test-fdr`` and ``train-fdr`` (Percolator's
       learning thresholds, **not** the result display filters), the
       configured ``random-seed``, ``maximum-iterations`` and
       ``thread-count``. Each value is a string holding exactly the text that
       goes on Percolator's command line when the build accepts the option:
       plain decimal, the same in every locale. A setting the build cannot
       accept is still recorded here -- the file is the configuration, not the
       command -- and provenance says it was not passed and why.
   * - ``downstreamStages``
     - The identifiers of the enabled downstream stages, in declaration order;
       today only ``limelight-conversion``, or ``[]``. It is here because an
       enabled stage that needs pout XML changes the command (``-X``).

The document ends with one newline. Nothing else is in it: the build that runs
is a separate fingerprint input (its version and SHA-256), and what the build
was observed to accept is recorded in provenance, not here.

``outputs/percolator/`` -- the raw artefacts, with fixed names:
``psms.tsv``, ``peptides.tsv``, ``decoy-psms.tsv``, ``decoy-peptides.tsv``,
``weights.txt`` and ``pout.xml``, each present exactly when the command asked
for it (the decoy tables with ``DECOY_OUTPUT``, the weights with
``WEIGHTS_OUTPUT``, the pout XML only when the build has ``XML_OUTPUT`` *and* an
enabled stage needs it). Percolator runs with this directory as its working
directory. After it exits 0, every requested file must exist and hold bytes,
and **no other file may be in the directory** -- so a run that requested no
pout XML fails rather than keeping an ``.xml`` it did not ask for. Then every
file is made read-only (``R-PERC-07``): on a POSIX file system every write
permission bit is removed; elsewhere the DOS read-only attribute is set, which
has not run on Windows. Nothing derived is ever written here.

``logs/percolator.log`` -- the one stream-tagged log of the Percolator
invocation, whose stage identifier is ``percolator``; a retried invocation's is
``percolator.1.log``. This is the same divergence from the specification's
``percolator.{stdout,stderr}.log`` as Comet's logs above (design decision P9-9).

A derived run
-------------

A **derived run** is the compatible-version Percolator rerun (Phase 09, design
decision P9-11; the specification's *Stage reruns*): a new run that reruns
Percolator -- with another build, other settings or other downstream stages --
from the merged PIN an earlier run of the same project preserved, and runs no
Comet. It is always a new run, never an attempt of the earlier one, because a
different Percolator is a different configuration (``R-RUN-06``). Its directory
has the same layout as any run, with these differences:

* ``inputs/pin/merged.pin`` is a **byte copy** of the source run's merged PIN,
  taken only after the source's file was re-hashed and found equal to the
  SHA-256 the source's ``provenance.json`` recorded for it (``R-RUN-02``). The
  copy is re-hashed too, and must be equal again; otherwise the rerun is refused
  and the run directory removed, so a refused rerun leaves nothing behind.
* ``parameters/comet.params`` is a byte copy of the source's archived parameter
  file, checked the same way against the source's ``run.json``. It is there so
  that the run's ``parameters`` member names a file in the run, and its decoy
  configuration is the one the merged PIN is checked with.
* ``outputs/comet/`` is empty and ``logs/`` holds only ``percolator.log``: no
  Comet runs in a derived run.

**Why a copy, not a hard link.** A hard link would share the file's bytes and
permissions between the two runs, so changing either would change the other
and the source would no longer be provably untouched. A copy keeps the source's
directory byte-identical (a test holds every path, size, SHA-256, time and mode
of the source equal before and after a real rerun) and makes each run
self-contained, to be moved, archived or deleted alone. The cost is one more
file the size of the merged PIN.

A derived run's ``run.json`` is schema version 2: version 1 plus the
``derivedFrom`` member (`The derivation`_). Its Comet members -- ``cometRelease``,
``spectra``, ``fasta``, ``parameters``, ``indexMode`` and ``databaseDelivery``
-- are the source's, copied, because they describe the search whose merged PIN
it rescored. A derived run cannot itself be rerun: rerun its source instead.

.. _ref-project-format-derived:

What CometGUI derives: ``results/`` and ``exports/``
----------------------------------------------------

``outputs/`` holds what the tools wrote and **nothing CometGUI derives**
(``R-PERC-07``). Everything derived from a run's results has a directory of its
own (design decision P10-6), and neither directory is made when the run is
created: each is made by what first writes into it, so a run that never reaches
its results has neither.

``results/index/`` -- the result stores' index files, written by the
``finalise-results`` step (:ref:`dev-workflow-engine`) and by the Results
section when it opens a table. A table above the in-memory row limit is
indexed on disk, as ``<kind>.index`` and ``<kind>.sort-<column>-<direction>``
files named by table kind (``target-psms``, ``target-peptides``,
``decoy-psms``, ``decoy-peptides``); a smaller table is read into memory and
leaves nothing here. Each run has its own index directory, so two runs' stores
of one kind never share an index. An index records the size, modification time
and SHA-256 of the raw table it was built from, and is rebuilt whenever they no
longer match: it is a derived cache, never provenance, and deleting the
directory loses nothing. The files' byte layout and the checks that decide
whether one is reused are in :ref:`dev-results-model`.

``results/view-state.json`` -- the run's **view state**: the PSM and peptide
display q-value filters its results were last shown with (``R-RES-01``:
"filter values are stored in project/run view state"). It is not part of the
run's record: it is never in ``run.json`` or provenance, it may change at any
time, and changing it reruns nothing. It is written whole, by the one JSON
writer through the one atomic writer, touching nothing else in the run::

    {
      "schemaVersion": 1,
      "psmQValueFilter": "0.01",
      "peptideQValueFilter": "0.01"
    }

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Member
     - Meaning

   * - ``schemaVersion``
     - ``1``, the first member. The same policy as ``run.json``'s applies
       (:ref:`ref-project-format-versions`).
   * - ``psmQValueFilter``
     - The PSM display filter's cutoff, as a string holding the decimal
       exactly as it was set (``"0.050"`` stays ``"0.050"``): no binary
       floating point between the screen and the file. It must be a decimal
       number within ``[0, 1]``, both ends included, read by the PSM filter's
       own parser -- the one range rule.
   * - ``peptideQValueFilter``
     - The peptide display filter's cutoff, the same way.

The document ends with one newline; no other member is accepted. **What each
state of the file means:**

* **No file** -- nothing was saved; the filters are the defaults, 0.01 and
  0.01.
* **A version-1 file** -- its filters.
* **A file CometGUI cannot read** -- not UTF-8, not JSON, a missing or unknown
  member, a cutoff outside ``[0, 1]``, or another schema version: the run still
  opens, with the default filters, and the refusal (naming the file and the
  member, quoting no value) is given beside them for the interface to show.
  **The file is never rewritten**: reading changes nothing, and saving filters
  for that run is refused with the same message, so a view state written by a
  newer CometGUI is never overwritten by an older one and a damaged file stays
  for someone to look at. Removing the file is how filters are saved for that
  run again.

``exports/`` -- filtered exports of the run's tables and learned weights
(``R-PERC-07``: "derived filtered exports shall be new files under a distinct
directory"). Each export is a new file; none is ever overwritten. The formats
are below, in `Exports and their sidecars`_.

.. _ref-project-format-exports:

Exports and their sidecars
--------------------------

An export is two files in ``exports/`` and one line in the run's provenance
event log (``R-RES-04``, ``R-RES-01``, design decision P10-7). They are
written by ``org.cometgui.results.export.ResultExporter``.

**A filtered table** is named
``<table>_q<cutoff>_<category>_<yyyyMMdd'T'HHmmss.SSS'Z'>.tsv`` -- for example
``target-psms_q0.01_passing_20261009T120000.123Z.tsv`` -- where ``<table>`` is
``target-psms``, ``decoy-psms``, ``target-peptides`` or ``decoy-peptides``,
``<cutoff>`` is the filter's cutoff exactly as it was set, ``<category>`` is
``passing``, ``unknown-q-value``, ``failing`` or ``all``, and the time is UTC.
It holds Percolator's header line and then **exactly the rows of that
category** under that cutoff, **each row's bytes copied verbatim from the raw
table, its line terminator included, in the raw table's order**. Where a row
falls is decided by the one q-value predicate the results view uses, so a row
with a missing, unparsable or out-of-range q-value is in ``unknown-q-value``
(and ``all``) and nowhere else (``R-RES-02``). The view's text filter and sort
are view-only and are **not** applied; the sidecar says so. The raw table is
only ever read.

**The learned feature weights** are named
``learned-feature-weights_<time>.tsv``: UTF-8, ``\n`` line ends, tab-separated,
one row per feature in the artefact's order, the bias term ``m0`` included. For
an artefact of *n* splits the columns are ``feature``, ``bias`` (``true`` or
``false``), ``split_1_normalised`` .. ``split_<n>_normalised``,
``mean_signed``, ``mean_absolute``, ``standard_deviation``,
``sign_consistency`` (``all positive``, ``all negative``, ``mixed`` or ``all
zero``), ``rank`` (empty for the bias term, which is not ranked), and
``split_1_raw`` .. ``split_<n>_raw``. *n* is read from the artefact, never
assumed (``R-PERC-09``). Numbers are written by Java's ``Double.toString``:
the shortest decimal that reads back as the same ``double``, with a ``.``
decimal point whatever the locale, and ``E`` notation for very small or large
magnitudes (``1.0E-4``). The values are those of the weights view's table,
which is their one source.

**Never overwritten.** If the name, or its sidecar's, is taken by anything at
all, ``-2``, ``-3`` ... is added before ``.tsv`` until both are free. Every
export in one CometGUI process holds one lock while it chooses its name and
writes, so two exports never choose one name; two CometGUI *processes*
exporting the same table, cutoff and category of one run in the same
millisecond are the one case this cannot see.

**The sidecar** is ``<export>.json`` beside it, written by the one JSON writer
(every string value redacted, ``R-SEC-03``) through the one atomic writer. For
a filtered table, schema version 1 -- the constructed shuffled PSM table of the
tests, exported at 0.01, with the digests and two long texts abridged here::

    {
      "schemaVersion": 1,
      "export": "filtered-table",
      "runId": "run-0001",
      "cometguiVersion": "0.1.0",
      "created": "2026-10-09T12:00:00.123Z",
      "file": {
        "path": "exports/target-psms_q0.01_passing_20261009T120000.123Z.tsv",
        "size": 862,
        "md5": "97a8...",
        "sha256": "77d2..."
      },
      "source": {
        "table": "target-psms",
        "path": "outputs/percolator/psms.tsv",
        "size": 2096,
        "md5": "9ed7...",
        "sha256": "3754..."
      },
      "filter": {
        "name": "psm-q-value",
        "cutoff": "0.01",
        "rule": "a row passes when its q-value is known and q-value <= cutoff; the cutoff is inclusive"
      },
      "category": "passing",
      "unknownQValuePolicy": "a row whose q-value is missing, unparsable or outside [0, 1] is neither passing nor failing: ...",
      "countsBefore": {
        "total": 23,
        "passing": 9,
        "failing": 7,
        "unknownQValue": 7
      },
      "rowsWritten": 9,
      "rows": "the raw table's header line, then every row of the category written, ...",
      "textFilterApplied": false,
      "sortApplied": false
    }

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Member
     - Meaning

   * - ``schemaVersion``
     - ``1``.
   * - ``export``
     - ``filtered-table`` or ``learned-feature-weights``.
   * - ``runId``, ``cometguiVersion``
     - The run's ID and the version of CometGUI that wrote the export.
   * - ``created``
     - When the export was made, in the provenance timestamp form (UTC,
       milliseconds).
   * - ``file``
     - The export itself: its path relative to the run, its size in bytes, and
       its MD5 and SHA-256, computed by the one hasher after it was written.
   * - ``source``
     - The raw table (or weights artefact) it came from: for a table its
       ``table`` word; its path, relative to the run with ``/`` between names
       when it is inside the run, otherwise absolute; its size; its MD5 and
       SHA-256, in one pass, before it was read.
   * - ``filter``
     - Which filter (``psm-q-value`` or ``peptide-q-value``), its cutoff
       exactly as set, and the rule in words. Tables only.
   * - ``category``
     - The rows written. Tables only.
   * - ``unknownQValuePolicy``
     - The unknown-q-value rule in words. Tables only.
   * - ``countsBefore``
     - Where every row of the raw table falls under the filter: ``total``,
       ``passing``, ``failing``, ``unknownQValue``; they add up. Tables only.
   * - ``rowsWritten``
     - The rows in the export, its header excluded -- the count of
       ``category`` in ``countsBefore`` (``total`` for ``all``). Tables only.
   * - ``rows``, ``textFilterApplied``, ``sortApplied``
     - What the rows are, in words, and that the view's text filter and sort
       were not applied (both ``false``). Tables only.
   * - ``splitCount``, ``featureCount``, ``numbers``
     - Weights only: the splits, read from the artefact; the features, the
       bias term included; how numbers are written, in words.

**The event.** Each export appends one ``export.written`` event
(:ref:`ref-provenance-format-event-types`) to ``provenance/events.log``. Its
payload: ``run.id``; ``export.kind`` (the sidecar's ``export``); ``file.path``,
``file.md5`` and ``file.sha256`` (the export's); ``export.sidecar`` (the
sidecar's path); ``source.path``, ``source.md5`` and ``source.sha256``; for a
table ``export.table``, ``export.category``, ``filter.name``,
``filter.cutoff``, ``counts.total``, ``counts.passing``, ``counts.failing``,
``counts.unknown-q-value`` and ``rows.written``; for the weights
``weights.split-count`` and ``weights.feature-count``. This is where the filter
values used for a derived export are written to provenance (``R-RES-01``,
*Application provenance*). An export is made from a finished run, so the event
follows ``run.finished``; ``provenance.json`` is not rewritten.

**Order, and what a failure leaves.** The source is hashed; the export is
written to a temporary file in ``exports/`` and renamed into place; the export
is hashed and its sidecar written; the event is appended. A failure while the
export is written -- a table the reader refuses, or one whose size or
modification time changed while it was copied -- leaves no file under the
export's name and no temporary file. A failure in the last two steps removes
the export and its sidecar again, so **an export is never reported as made
unless its event was recorded**. A process killed between the rename and the
append can leave an export without a sidecar or event: such a file is
incomplete and is not an export of record. An export is for a run that is not
executing, because the run's engine holds the event log open while it runs.

The index cache
---------------

A search that builds a Comet index (``comet -i``, fragment-ion, or ``comet
-j``, peptide) builds it into the **project**, not the run, so that later runs
with the same database and options reuse it (``R-CMT-07``, design decision
P8-8). Comet writes an index *beside the database it is given*, so the build is
given a symbolic link to the FASTA inside the cache entry rather than the FASTA
itself, and nothing is ever written beside the user's file. Each entry is one
directory::

    index-cache/<key>/
        <fasta name>          a symbolic link to the FASTA; the build's -D names it
        <fasta name>.idx      the index Comet wrote beside that link; the search's -D names it
        index.complete        the completion marker, written last

**The key** is 64 lower-case hexadecimal characters: the SHA-256 of an ASCII
text, each line ending in ``\n``, that begins ``cometgui-index-key 1`` and then
records the FASTA's SHA-256 and file name, the index mode, the Comet release,
every search option the ``.idx`` header records, the ``fragindex_*`` options,
``equal_I_and_L`` and the canonical enzyme table (``IndexCacheKey``). A changed
FASTA or digestion setting therefore names a different entry.

**The marker** is UTF-8 text of exactly three lines, each ending in ``\n``::

    cometgui-index-cache 1
    sha256 <the index's SHA-256, 64 lower-case hexadecimal characters>
    size <the index's length in bytes>

An entry is **complete** only when ``index.complete`` is exactly that text and
the index exists. Comet writes an index's header before its body, so a build
that was cancelled or failed leaves a header that reads as valid over a
truncated body; the marker is therefore written -- atomically -- only after the
build exited zero and the index's header was read and judged. An entry without
a valid marker is incomplete, and a build into it first removes the index and
marker an earlier build left (and nothing else). **Before an index is reused**
it is re-hashed and must match the marker's SHA-256 and size, and its header is
judged again against the search; a changed index is refused, naming both
digests.

A run that used the cache records the key in its provenance settings
(``comet.index-cache-key``), ``databaseDelivery`` ``command-line`` in its
``run.json``, and the index as an input file of role ``comet-index``. The
application does not offer an index mode yet, so only the workflow's own tests
create entries today. The engine side is described in
:ref:`dev-workflow-engine`.

.. _ref-project-format-versions:

Schema versions
===============

``project.json``, ``run.json``, ``project.lock`` and
``results/view-state.json`` each begin with a ``schemaVersion`` member.
``project.json``, ``project.lock`` and ``view-state.json`` declare ``1``;
``run.json`` declares ``1``, or ``2`` for a derived run. The policy for any
other version (``R-RUN-04``) is the same for all four -- with one difference
in what a refusal means for ``view-state.json``, which is not part of the
run's record: the run opens with the default filters and the refusal is shown
beside them (`What CometGUI derives: results/ and exports/`_):

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Declared version
     - What CometGUI does

   * - ``1``
     - Reads the document.

   * - ``2`` (``run.json`` only)
     - Reads the document: a derived run's ``run.json``, version 1 plus the
       ``derivedFrom`` member.

   * - Lower than ``1``
     - Refuses it, with a message naming both versions and saying that no
       migration exists. Version 1 is the first published format, so nothing
       older was ever written.

   * - Higher than the newest it reads (``1``; ``2`` for ``run.json``)
     - Refuses it **before any other member is read**, with a message naming
       both versions. A newer CometGUI may have changed what a member *means*,
       not only added one, so a document read "as far as this build understands
       it" would be misread without anyone noticing.

In every case **the file is left byte-for-byte as it was**: refusal happens
while reading, and nothing is written.

**How version 2 of** ``run.json`` **relates to version 1.** A run is written at
the *lowest* version that can express it, decided by its identity alone, so a
run keeps its version for life. A run that executes its own search is version 1
-- byte for byte what Phase 08 wrote -- so every build that reads version 1
still reads every such run, and an older version-1 file needs no migration: it
*is* a version-2 run with no ``derivedFrom``. A derived run is version 2, not a
version-1 document with one more member, on purpose: a build that knows only
version 1 would otherwise read it (or, being strict, refuse it only as an
unknown member) as a Comet run whose Comet steps never ran, and a retry there
would execute Comet in a run that must never run it. Such a build refuses
version 2 before reading anything. The two versions are not mixed: a version-1
document carrying ``derivedFrom``, or a version-2 document without an object
there, is refused like any other malformed document.

Conventions a reader may rely on
================================

All four documents are written by the same JSON writer as the provenance
manifest and follow its byte-level conventions
(:ref:`ref-provenance-format`): UTF-8, ``\n`` line endings, two-space
indentation, one member per line, ``": "`` between a name and its value, one
trailing newline, ``{}`` and ``[]`` for empty containers, whole numbers only,
and an absent optional written as ``null``, never omitted. Members are written
in the order the tables below list them; the two open-ended maps
(``succeededSteps`` and ``inputDigests``) are sorted by key.

**Timestamps** are UTC with exactly three fractional digits,
``uuuu-MM-dd'T'HH:mm:ss.SSS'Z'`` -- for example ``2026-08-28T23:15:00.250Z`` --
truncated to the millisecond.

**Digests** are lower-case hexadecimal: 32 characters for MD5, 64 for SHA-256.
Upper case is refused, because nothing CometGUI writes produces it.

**The reader is strict**, and a reader built from this page should be too:

* the document must be UTF-8 and at most 64 MiB (``project.lock`` at most
  64 KiB);
* every member listed for an object must be present, and **no other member is
  accepted**. ``run.json`` is rewritten whenever an attempt changes, and a
  member this build did not understand would be silently dropped by the
  rewrite;
* a refusal names the file and the member -- for example
  ``"spectra[1].sha256"`` -- and the rule it broke, and **never quotes a value
  from the document**, which may hold anything.

``project.json``
================

The project's identity. Written once, when the project is created; a directory
that already holds one is refused.

.. code-block:: json

   {
     "schemaVersion": 1,
     "projectId": "project-beta",
     "created": "2026-08-28T23:00:00.000Z"
   }

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``schemaVersion``
     - number
     - Always ``1`` for this format.

   * - ``projectId``
     - string
     - The project's identifier: letters, digits, ``.``, ``-`` and ``_``,
       starting with a letter or digit, at most 64 characters. Every run of the
       project records it, as does the provenance manifest's ``run.projectId``.

   * - ``created``
     - timestamp
     - When the project was created.

**What later phases add.** Version 1 holds only what Phase 08 needs: an
identity runs can name. The specification's "mutable user intent" -- the
prospective parameter configuration, the chosen inputs and tools, the presets in
``presets/`` -- is added by the phases that build those editors, each as a
schema-version bump with a migration from version 1.

``run.json``
============

What a run *is*, written once when the run starts, and what has happened to it
since, only ever added to (``R-RUN-06``). The identity members come first, then
``attempts``. A derived run (schema version 2) has one more identity member,
``derivedFrom``, just before ``attempts`` (`The derivation`_).

.. code-block:: json

   {
     "schemaVersion": 1,
     "runId": "run-0001",
     "projectId": "project-beta",
     "created": "2026-08-28T23:15:00.250Z",
     "cometRelease": "2026.03.0",
     "spectra": [
       {
         "position": 1,
         "stageId": "comet-01",
         "base": "k562_3",
         "path": "/data/in/k562_3.mzML",
         "size": 2602922,
         "modified": "2026-08-01T10:00:00.000Z",
         "md5": "0123456789abcdef0123456789abcdef",
         "sha256": "a562f6e6b4c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5"
       }
     ],
     "fasta": {
       "path": "/data/db/sub.fasta",
       "size": 512000,
       "modified": "2026-07-30T08:00:00.000Z",
       "md5": "11111111111111111111111111111111",
       "sha256": "2222222222222222222222222222222222222222222222222222222222222222"
     },
     "parameters": {
       "path": "parameters/comet.params",
       "size": 12345,
       "md5": "33333333333333333333333333333333",
       "sha256": "4444444444444444444444444444444444444444444444444444444444444444"
     },
     "indexMode": "none",
     "databaseDelivery": "parameter-file",
     "attempts": [
       {
         "number": 1,
         "started": "2026-08-28T23:15:01.000Z",
         "ended": "2026-08-28T23:16:00.000Z",
         "outcome": "failed",
         "succeededSteps": {
           "hash-inputs": {
             "fingerprint": "5555555555555555555555555555555555555555555555555555555555555555",
             "inputDigests": {
               "fasta": "6666666666666666666666666666666666666666666666666666666666666666",
               "spectrum-files": "7777777777777777777777777777777777777777777777777777777777777777"
             }
           }
         }
       },
       {
         "number": 2,
         "started": "2026-08-28T23:17:00.000Z",
         "ended": null,
         "outcome": "running",
         "succeededSteps": {}
       }
     ]
   }

That is a run whose first attempt failed after hashing its inputs, and whose
retry is in progress. (The digests are illustrative.)

The root
--------

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``schemaVersion``
     - number
     - Always ``1`` for this format.

   * - ``runId``
     - string
     - The run's identifier, constrained as ``projectId`` is. With ``created``
       it names the run directory.

   * - ``projectId``
     - string
     - The project the run belongs to; equal to that project's
       ``project.json``.

   * - ``created``
     - timestamp
     - When the run was created. Its whole seconds are the run directory's
       timestamp.

   * - ``cometRelease``
     - string
     - The Comet release that executes the run, as its version text, for
       example ``2026.03.0``. Never blank.

   * - ``spectra``
     - array
     - The spectrum files, at least one, in the order the user gave them. See
       below.

   * - ``fasta``
     - object
     - The protein database, recorded as a spectrum file is (without
       ``position``, ``stageId`` and ``base``).

   * - ``parameters``
     - object
     - The canonical Comet parameter file archived in the run, the one passed
       to Comet with ``-P``. See below.

   * - ``indexMode``
     - string
     - ``none`` (Comet searches the FASTA), ``fragment-ion`` (a prebuilt index
       made with ``comet -i``) or ``peptide`` (``comet -j``).

   * - ``databaseDelivery``
     - string
     - How the database reached Comet (``R-CMT-04``): ``parameter-file``
       (``database_name`` in the parameter file) or ``command-line`` (``-D``,
       overriding it -- for example to search a cached index).

   * - ``derivedFrom``
     - object
     - **Version 2 only**, and there required: the run whose Comet results
       this derived run reuses. See `The derivation`_.

   * - ``attempts``
     - array
     - Every attempt to execute the run, oldest first; empty until the first
       one starts. See below.

A spectrum entry
----------------

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``position``
     - number
     - The file's 1-based position. Entries are in position order, 1, 2, 3 ...

   * - ``stageId``
     - string
     - ``comet-`` and the position with at least two digits: the identifier of
       this file's Comet invocation, and the name of its log,
       ``logs/<stageId>.log``. Derived from ``position``; a reader may check it
       and CometGUI does.

   * - ``base``
     - string
     - The output base name: Comet wrote ``outputs/comet/<base>.pep.xml`` and
       ``outputs/comet/<base>.pin`` for this file. It must be the name the rule
       in `The layout on disk`_ gives these inputs in this order.

   * - ``path``
     - string
     - The file's canonical path, absolute and normalised, when the run
       started.

   * - ``size``
     - number
     - The file's length in bytes.

   * - ``modified``
     - timestamp
     - The file's last-modified time.

   * - ``md5``, ``sha256``
     - string
     - The file's digests, computed by the hash service over the content the
       run used.

The parameter file
------------------

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``path``
     - string
     - The file's path **relative to the run directory**, with ``/`` on every
       platform; always under ``parameters/``. Relative because the file
       belongs to the run, so the record stays true when a project is moved.

   * - ``size``
     - number
     - Its length in bytes, as on disk when the run was recorded.

   * - ``md5``, ``sha256``
     - string
     - Its digests, computed over the file on disk after it was written -- the
       same values the provenance manifest carries.

The derivation
--------------

A derived run's ``derivedFrom`` (schema version 2)::

    "derivedFrom": {
      "runId": "run-0001",
      "created": "2026-08-28T22:00:00.125Z",
      "provenance": {
        "path": "provenance/provenance.json",
        "size": 40960,
        "md5": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
      },
      "mergedPin": {
        "path": "inputs/pin/merged.pin",
        "size": 1234567,
        "md5": "88888888888888888888888888888888",
        "sha256": "9999999999999999999999999999999999999999999999999999999999999999"
      }
    }

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``runId``
     - string
     - The source run's identifier, constrained as ``runId`` is. Never this
       run's own.

   * - ``created``
     - timestamp
     - The source run's ``created``. With ``runId`` it names the source's
       directory, ``runs/<yyyyMMdd'T'HHmmss'Z'>-<runId>/``.

   * - ``provenance``
     - object
     - The source's ``provenance.json`` as it was when this run was made:
       ``path`` (always ``provenance/provenance.json``, relative to the
       **source** run's directory), ``size``, ``md5`` and ``sha256``. A reader
       can tell from it whether the source has been retried since.

   * - ``mergedPin``
     - object
     - **This** run's copy of the source's merged PIN: ``path`` (always
       ``inputs/pin/merged.pin``, relative to this run's directory), ``size``,
       ``md5`` and ``sha256``. Its SHA-256 equals the one the source recorded
       for its merged PIN, because the copy is refused otherwise.

``derivedFrom`` is part of the identity: written once, never changed, never
added to or removed from a run afterwards. Every attempt of a derived run
re-hashes its two copies against ``mergedPin`` and ``parameters`` in
``validate-configuration``, before Percolator can start, and again in
``finalise-provenance``.

An attempt
----------

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``number``
     - number
     - The attempt's number: 1 for the first execution, 2 for the first retry,
       and so on, in order.

   * - ``started``
     - timestamp
     - When the attempt started.

   * - ``ended``
     - timestamp or ``null``
     - When it ended; ``null`` exactly while ``outcome`` is ``running``.

   * - ``outcome``
     - string
     - ``running``, ``succeeded``, ``failed`` or ``cancelled``. Only the latest
       attempt may be ``running``.

   * - ``succeededSteps``
     - object
     - The input fingerprint of every workflow step that **succeeded** in this
       attempt, keyed by the step's identifier (for example ``run-comet``). A
       step that failed, was cancelled, was skipped or was reused from an
       earlier attempt has no entry, so a later rerun preview is never told that
       something was produced when it was not. Each value is an object with
       ``fingerprint`` (the step's SHA-256 fingerprint) and ``inputDigests``
       (the digest of each of the step's inputs, keyed by input kind, for
       example ``comet-parameters``). Identifiers are lower-case letters and
       digits in hyphen-separated words.

A rerun preview compares against the fingerprints of every attempt merged,
the latest attempt's winning where two recorded the same step.

What may change, and what may not
---------------------------------

``run.json``'s identity -- every root member except ``attempts``, ``derivedFrom``
included -- is written once, when the run is recorded, and a second recording of the same run is
refused with the file untouched. After that the file is only ever replaced,
atomically (write a temporary file, force it to disk, rename it over the old
one), by a *successor*, which CometGUI checks against the file on disk:

* every identity member is unchanged -- no input, hash, parameter file,
  release, index mode or database mechanism is ever re-recorded;
* every existing attempt is still there, in order;
* an attempt that has ended is unchanged;
* the running attempt may end and may gain fingerprints, but its start does
  not change and a fingerprint it recorded is never changed or removed;
* new attempts may be appended: **a retry is a new attempt, never a rewrite.**

An update that breaks any of these is refused, naming the member, and the file
is left byte-for-byte as it was.

Everything under ``parameters/`` is written once as well: the canonical
``comet.params`` and ``percolator-settings.json`` are each created by a write
that refuses an existing file, and each is hashed as it lies on disk.

A ``running`` attempt found in a project that nobody had locked was
interrupted: the process that wrote it died. Ending it as ``failed`` is a
permitted successor, and is how it is recovered.

``project.lock``
================

The project lock (``R-RUN-05``): one CometGUI at a time per project, with a
stale-lock recovery that names the owner.

.. code-block:: json

   {
     "schemaVersion": 1,
     "pid": 4242,
     "host": "lab-pc",
     "started": "2026-10-06T09:00:00.125Z"
   }

.. list-table::
   :header-rows: 1
   :widths: 20 14 66

   * - Member
     - Type
     - Meaning

   * - ``schemaVersion``
     - number
     - Always ``1`` for this format.

   * - ``pid``
     - number
     - The operating-system process id of the CometGUI holding the project.
       Positive.

   * - ``host``
     - string
     - The name of the computer it runs on, or ``unknown-host`` when that
       computer's name could not be determined.

   * - ``started``
     - timestamp
     - When it took the lock.

**An empty file means "not held".** Releasing the lock empties the file and
only then releases it.

**How the lock is taken.** The file is created (or opened, if it exists) and an
operating-system lock is *tried* -- never waited for: a second CometGUI is
refused at once, not frozen. The locked region is a single byte at offset
2\ :sup:`30`, beyond any record, because Windows byte-range locks are
mandatory and locking the record itself would stop a second CometGUI from
reading whose lock it is. Within one CometGUI process, a second attempt on the
same project is refused before the file is touched.

**What a refusal says, and when a lock is stale.**

.. list-table::
   :header-rows: 1
   :widths: 40 60

   * - What CometGUI finds
     - What it does

   * - Another process holds the operating-system lock.
     - Refuses, naming the process, host and start time the file records
       ("is open in another CometGUI: process 4242 on host lab-pc, since
       ..."). The owner is live.

   * - The lock is free, and the file records a process **on another
       computer**.
     - Refuses, naming it. **A lock from another computer is never broken**,
       because this computer cannot tell whether that process is alive; the
       message says to close CometGUI there or delete the file by hand.

   * - The lock is free, and the file records a process on this computer that
       **is no longer running**.
     - The lock is **stale**: CometGUI takes it over and says whose it was
       ("recovered a stale lock ... left by process 4242 on host lab-pc, since
       ..., which is no longer running").

   * - The lock is free, and the file records a process on this computer that
       **is still running**.
     - Refuses, naming it. Most likely an unrelated program reused the process
       number; the message says to delete the file by hand if so.

   * - The file is not a record this build can read.
     - Refuses, naming the file. An unreadable lock is never broken
       silently.

**Written in place, not by rename.** Every other document is replaced
atomically; the lock record cannot be, because the operating-system lock
belongs to the file itself, and renaming a new file over it would leave the
holder locking a file nobody else opens. The record is written with one write
and forced to disk. For the same reason nothing else in a CometGUI process may
open ``project.lock``: on POSIX systems closing *any* descriptor of a file
releases every lock the process holds on it.

**Across computers** the operating-system lock is not trusted -- network file
systems do not propagate it reliably -- and the host check above is what
protects a project on a shared drive.

Where the code is
=================

The models are pure and live in ``org.cometgui.domain.project``
(``ProjectDescriptor``, ``ProjectLayout``, ``LockOwner``,
``SchemaVersionPolicy``) and ``org.cometgui.domain.run`` (``RunLayout``,
``OutputBaseNames``, ``RunDescriptor``, ``RunIdentity``, ``RunDerivation``,
``RunAttempt``).
Reading and writing them is ``org.cometgui.workflow.storage``
(``ProjectJson``, ``RunJson``, ``ProjectStore``, ``RunStore``,
``ProjectLock``), and so is the view state (``ViewStateJson``,
``ViewStateStore``, ``ViewStateReading``; tested against hand-typed documents
by ``ViewStateStoreTest``). The ``results/`` and ``exports/`` paths are
``RunLayout``'s. Exports are ``org.cometgui.results.export``
(``ResultExporter``; tested by ``TableExportGateTest``,
``TableExportBehaviourTest``, ``WeightsExportTest`` and ``LargeExportTest``). The index cache is ``org.cometgui.workflow.steps``
(``IndexCacheKey``, ``IndexCacheEntry``), and so are the Percolator files
(``PercolatorSettingsFile``, ``PercolatorDeclarations``), and so is the derived
run (``PercolatorRerun``, ``DerivedRun``, ``RerunSource``). The tests that hold the writers to
hand-typed documents byte for byte are ``RunJsonTest`` and ``ProjectJsonTest``, the
version-2 document included (the examples on this page
are abridged from them); the readers' tests parse hand-typed documents and
never ones the writer produced.
