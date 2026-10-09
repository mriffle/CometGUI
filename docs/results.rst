.. _user-results:

=======
Results
=======

The **Results** section shows what Percolator found in a run: its PSM and
peptide tables, filtered by q-value, with counts you can check, and the
learned feature weights. In one sentence: **the two q-value filters only
change what you see and export -- they never rerun anything and never change
Percolator's files -- and a row whose q-value cannot be read is always counted
and shown on its own, never quietly dropped or kept.**

.. note::

   Written for Phase 10 (Results Model and UI) on 2026-10-09, against the
   application as built. Every time and size quoted below was measured on one
   Linux x86-64 computer; CometGUI's results have not been opened on Windows
   or macOS yet. The learned feature weights, shown at the bottom of the same
   section, have a page of their own: :doc:`learned_feature_weights`. The
   exact formats of the files mentioned here are in
   :doc:`reference/project_format`.

.. contents:: Contents
   :depth: 2
   :local:

Choosing what to look at
========================

**Run.** The section lists every run of the project that has at least one
Percolator result table, newest first, and shows the newest. Each entry names
the run, when it was created and how it ended (or "executing now"). Choose
another run to see its results; **Read the runs again** reads the list afresh.
The list is also read when CometGUI starts and when a run starts or ends, so a
run that has just finished appears without a restart.

**Table.** Each run offers up to four tables: **Target PSMs** (shown first),
**Target peptides**, and -- when Percolator wrote them -- **Decoy PSMs** and
**Decoy peptides**. The PSM filter applies to both PSM tables, the peptide
filter to both peptide tables.

Protein-level results are not part of CometGUI's first release.

The two q-value filters
=======================

At the top of the section are the **PSM q-value filter** and the **peptide
q-value filter**:

* A PSM is **passing** when its q-value is **at or below** the PSM filter; a
  peptide when its q-value is at or below the peptide filter. A q-value equal
  to the filter passes: at 0.01, a row with q-value ``0.01`` is passing.
* Both are **0.01** by default.
* Each may be any number **from 0 to 1**, both included, written with a ``.``
  as the decimal point. A value is applied when you press Enter or leave the
  field. Anything else -- ``1.5``, ``-0.1``, ``0,01``, ``abc`` -- is refused
  with a message beside the fields, what you typed stays there for you to
  correct, and the filter keeps its previous value.
* The two are **independent**: changing one never changes the other.
* They are the same two filters the :doc:`percolator` section shows. Change
  them in either section and both show the new value.

**What changing a filter does**: the counts and the table are recalculated
from the files Percolator already wrote. It **does not run Percolator or any
other program**, does not change any of Percolator's files, and does not
create a new run. The one thing it writes is the run's saved filter values
(see `The filters a run remembers`_).

**What the filters are not**: they are not Percolator's ``testFDR`` and
``trainFDR``. Those two, under *Advanced settings* in the Percolator section,
are part of how Percolator learns and take effect only when Percolator runs;
see :doc:`percolator`.

The counts, and rows with an unknown q-value
============================================

Above the table, four numbers describe the whole table under its filter,
whatever else you have chosen to show:

* **Total rows** -- every row in Percolator's file;
* **Passing** -- rows whose q-value is at or below the filter;
* **Failing** -- rows whose q-value is above it;
* **Unknown q-value** -- rows whose q-value CometGUI cannot compare with the
  filter.

The three always add up to the total. A sentence beneath them says the same
in words, for example *"3897 target PSMs in total. At a q-value cutoff of 0.01
(a q-value equal to the cutoff passing): 1026 passing, 2871 failing, and 0
with an unknown q-value, which neither pass nor fail."*

**An unknown q-value** is one of these:

* an empty field;
* text that is not a plain number: ``NaN``, ``nan``, ``-nan``, ``inf``,
  ``Infinity``, a number written with a comma such as ``0,01``, or any other
  text;
* a number below 0 or above 1, such as ``1.5`` or ``-0.1``, which no q-value
  can be.

Numbers in exponent form, such as ``1e-3`` or ``9.27998e-07``, are ordinary
numbers and are compared like any other. Percolator does not normally write
any of the unknown kinds; CometGUI handles them because a file that contains
one must not quietly change your counts. Such a row is **never silently
dropped and never silently included**: it is not passing and not failing, it
is counted under *Unknown q-value*, and you can show it. Exports follow exactly
the same rule.

Which rows the table shows
==========================

**Rows shown** chooses one of four categories:

* **Passing rows** (the default) -- what most people mean by "the results at
  1%";
* **Rows with an unknown q-value**;
* **Failing rows**;
* **All rows**.

Sorting
-------

Each column heading is a button. Press it once to sort ascending, again for
descending, and a third time to return to **file order** -- the order
Percolator wrote the rows, which is the default. The sort covers the whole
table, not only the page you are looking at.

* Numbers (score, q-value, PEP, scan, charge) sort as numbers; the q-value
  sorts by its value, never by its text.
* Text (PSMId, source file, peptide, proteins) sorts character by character,
  upper case before lower case, the same on every computer whatever its
  language. Proteins sort by the first protein, then the next.
* A row with no value in that column -- an unknown q-value, a score that is
  not a number, a PSMId whose scan cannot be read -- goes **last in both
  directions**.
* Rows with equal values stay in file order, in both directions.

On a very large table the first sort by a column takes a few seconds while
CometGUI builds its order; sorting by that column again is quick.

Text filter
-----------

**Text filter** keeps only the rows whose PSMId, peptide or any of whose
proteins contains the text you type, ignoring upper and lower case. It is
applied when you press **Enter**, not as you type, because on a table of a
million rows each new text means one pass over the whole file (about 1.5
seconds). Scores and q-values are not searched as text; use the q-value filter
and sorting for those. A note beside the field says whether what you have
typed has been applied yet. Clear the field and press Enter to remove it.

The four counts above are not affected by the text filter: they always
describe the whole table.

Pages
-----

The table shows **200 rows at a time**. **First**, **Previous**, **Next** and
**Last** move between pages, and a line beside them says where you are, for
example *"Rows 201 to 400 of 1026 (page 2 of 6)"*. Changing the filter, the
category, the sort or the text filter returns you to the first page -- unless
you have a row selected, in which case you are taken to the page that holds it
(see below).

Columns
=======

**The PSM tables** have these columns:

.. list-table::
   :header-rows: 1
   :widths: 18 82

   * - Column
     - What it holds
   * - PSMId
     - Percolator's identifier for the match, exactly as written. Comet
       builds it from the spectrum file, scan number, charge and rank.
   * - Source file
     - The spectrum file the match came from, read from the PSMId and named
       as you chose it (its full path, if two of the run's files have the
       same name).
   * - Scan
     - The scan number, read from the PSMId.
   * - Charge
     - The precursor charge, read from the PSMId.
   * - Peptide
     - The peptide, as Percolator wrote it.
   * - Proteins
     - Every protein Percolator lists for the match, separated by commas.
   * - Score
     - Percolator's score.
   * - q-value
     - The q-value.
   * - PEP
     - The posterior error probability.

**The peptide tables** have the last five: peptide, proteins, score, q-value
and PEP.

Score, q-value and PEP are shown **exactly as Percolator wrote them** -- never
rounded or rewritten -- so an unknown q-value is shown as it is in the file.
If a PSMId is not in the shape Comet writes, its source file, scan and charge
are left empty rather than guessed.

**Hiding columns.** The **Columns** check boxes below the table show or hide
each column. Your choice is kept when you switch to another table or run
while CometGUI is open; it is not saved with the run. One column cannot be
hidden: **when a run searched more than one spectrum file, the source-file
column is always shown**, because without it two matches to scan 1234 from
different files could not be told apart. Trying to hide it says why.

**Not shown.** Comet's own scores are not in Percolator's tables, and the
number of PSMs supporting each peptide cannot be worked out from Percolator's
tables without choices that could change what it means; neither is shown in
this release.

Selecting and copying rows
==========================

Click a row to select it; hold Ctrl (Cmd on a Mac) or Shift to select several.
**Your selection belongs to the rows, not to their positions**: it stays the
same rows when you change a filter, the category, the sort, the text filter or
the page.

After such a change, if the row you selected last is still among the rows
shown, the table moves to the page that holds it, so it stays in view. If it
is not -- say a stricter filter now fails it -- the first page is shown, the
row stays selected but hidden, and a line under the table says so; loosen the
filter again and the table returns to it.

**Copy selected rows** (or Ctrl+C, Cmd+C on a Mac, in the table) puts the
selected rows **shown on the current page** on the clipboard as tab-separated
text, ready to paste into a spreadsheet: a first line of the visible column
headings, then each row's visible cells exactly as shown. Selected rows on
other pages, or hidden by the filter, are not copied, and the message after
copying says how many were left out.

Exporting
=========

**Export this table** writes the rows you are looking at to a new file: the
table shown, the category chosen in *Rows shown*, under that table's current
q-value filter.

* The file begins with Percolator's own header line, followed by **exactly the
  rows of that category, each copied byte for byte from Percolator's file, in
  Percolator's order.** A row's category in the export is decided by the same
  rule as on screen, so the unknown q-value rows are in an export of *Rows
  with an unknown q-value* (and of *All rows*), and in no other.
* **The text filter and the sort are not applied**: they are ways of looking
  at a table, not part of the result. The metadata says so.
* The file goes into the run's own ``exports/`` folder, named after the
  table, the cutoff, the category and the time, for example
  ``target-psms_q0.01_passing_20261009T120000.123Z.tsv``. **An existing file
  is never overwritten**; a second export with the same name gets ``-2``,
  ``-3`` and so on.
* Beside it is a metadata file with the same name and ``.json`` added. It
  records the run's ID, the table and the Percolator file it came from (with
  its MD5 and SHA-256), the filter and its cutoff exactly as you set it, the
  category, the four counts before filtering and the number of rows written,
  that the text filter and sort were not applied, the version of CometGUI, the
  time, and the export's own MD5 and SHA-256.
* The run's provenance record gains one entry naming the export and the filter
  values used (see :doc:`provenance`).
* The message under the buttons names the file and the number of rows
  written.

**Export the learned feature weights** does the same for the weights table;
see :doc:`learned_feature_weights`.

**Percolator's files are never changed.** Exporting, like filtering, only
reads them; everything an export writes goes under ``exports/``.

**An export of a run that is still executing is refused**, with a message
saying to export when the run has ended: the run's provenance record is still
being written, and every export is recorded in it. One export is written at a
time; the buttons wait until the previous one has finished.

The full description of both files is in :ref:`ref-project-format-exports`.

The filters a run remembers
===========================

Each run remembers the q-value filters its results were last shown with.
When you open a run, its saved filters are restored, and a line under the
filters says so; a run that never saved any opens at 0.01 and 0.01. While the
run is open, each change to either filter -- in this section or in the
Percolator section -- is saved for it.

This is a note of how you last looked at the run, kept in the run's
``results/view-state.json``. It is not part of the run's record: changing it
reruns nothing and does not change the run's provenance. If the file cannot be
read -- damaged, or written by a newer CometGUI -- the run opens at the
defaults, the message says why, and CometGUI does not overwrite the file.

Very large results
==================

A run can produce hundreds of thousands of PSMs, and CometGUI never holds a
whole table in memory to show it:

* A table of up to 100 000 rows is read into memory when the run is opened.
* A larger table is **indexed on disk**, in the run's ``results/index/``
  folder: a compact record of where each row is in Percolator's file and of
  its q-value, score and PEP. Rows are read back from Percolator's file a page
  at a time. The index is built at the end of the run (the
  ``finalise-results`` step), so opening the run later is quick; if
  Percolator's file is ever different from what the index recorded, the index
  is rebuilt rather than trusted.
* The table on screen only ever holds one page of 200 rows.

What to expect, measured on a synthetic table of one million PSMs on a fast
Linux computer: opening it the first time takes a few seconds (it is read
once in full, checksummed and indexed); a filter change about a second; the
first sort by a number column about 1.5 seconds and by a text column about
3.5 seconds; a page move under a second; a new text filter about 1.5 seconds.
CometGUI's memory use barely changes while doing any of it. Slower disks and
computers will take longer.

Deleting ``results/index/`` loses nothing: it is rebuilt the next time the
run is opened.
