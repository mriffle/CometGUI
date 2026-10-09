.. _user-learned-feature-weights:

=======================
Learned feature weights
=======================

Percolator re-scores Comet's matches by training a linear support vector
machine (SVM) on your own target and decoy matches. What it learns is one
weight per feature -- per column of the merged PIN, such as Comet's ``XCorr``
or ``lnExpect`` -- in each of its cross-validation splits. CometGUI shows those
weights in a table titled **Learned feature weights (Percolator SVM)**, at the
bottom of the :doc:`results` section, and can export them.

.. note::

   Written for Phase 10 (Results Model and UI) on 2026-10-09, against the
   application as built and measured on Linux x86-64 only. The weights come
   from Percolator's weights file (``weights.txt`` in the run's
   ``outputs/percolator/``), which CometGUI asks every Percolator for when the
   build was seen to write one (:doc:`percolator`).

.. contents:: Contents
   :depth: 2
   :local:

What the numbers are, and are not
=================================

The view says this above the table, in these words:

    These are the coefficients Percolator's linear SVM learned for each
    feature, after Percolator's feature normalisation, in each of its
    cross-validation splits. They are not causal importances: a large weight
    says the feature helped separate target from decoy matches in this run's
    training, not that it makes a match correct. The rank is by mean absolute
    weight, 1 for the largest; the bias term m0 is listed and not ranked. This
    table is the source of truth, and its export keeps every value at full
    precision.

Two consequences are worth spelling out:

* **The weights are normalised.** Percolator rescales every feature before
  training, so weights of different features can be compared with one another
  within a run. Percolator also writes the weights on the original, unscaled
  features; CometGUI's export includes those (see `Exporting the weights`_),
  but every number in the table and every statistic is computed from the
  normalised ones.
* **The weights describe this run's training, not the biology.** Features that
  move together can share or trade weight between them, and a different
  search or a different random split can shift them. Read a weight as "the
  model leaned on this feature", not "this feature matters".

The table
=========

One row per feature, in the order the weights file lists them, then these
columns:

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Column
     - What it holds
   * - Feature
     - The feature's name as Percolator wrote it. The last row is ``m0``,
       labelled *(bias term, not ranked)*: the model's constant term rather
       than a feature of the match.
   * - Split 1, Split 2, ...
     - The normalised weight Percolator learned for the feature in each
       cross-validation split. **There is one column per split in the file**:
       CometGUI counts the splits in the file and never assumes how many there
       are (Percolator usually uses three).
   * - Mean signed
     - The average of the split weights, keeping their signs.
   * - Mean absolute
     - The average of the split weights' sizes, ignoring their signs.
   * - Standard deviation
     - How much the split weights differ from their mean: the *population*
       standard deviation (dividing by the number of splits, not one less), so
       a file with a single split shows 0 rather than nothing.
   * - Sign consistency
     - Whether the splits agree on the feature's direction, followed by how
       many splits were positive, negative and zero, for example *all
       positive (3 positive, 0 negative, 0 zero)*. See below.
   * - Rank
     - 1 for the feature with the largest mean absolute weight, 2 for the
       next, and so on. See below.

Sign consistency
----------------

Each split's weight is **positive** (above zero), **negative** (below zero) or
**zero**. A zero is neither sign, and ``-0.0000`` counts as zero. Then:

* **all positive** -- every split's weight is positive;
* **all negative** -- every split's weight is negative;
* **all zero** -- every split's weight is zero;
* **mixed** -- anything else.

So a feature with weights ``0``, ``0.2`` and ``0.3`` is **mixed**, not all
positive: one split learned no direction for it at all, and the splits did not
agree. The verdict is taken on the values exactly as the file holds them, and
Percolator writes four decimal places, so "zero" means a weight that Percolator
wrote as ``0.0000``.

Rank
----

Features are ranked by mean absolute weight, largest first. Features whose
mean absolute weights are exactly equal share a rank, and the next rank skips
accordingly (1, 2, 2, 4). The bias term ``m0`` has no rank: it does not take a
rank number and does not push any feature down, however large it is.

Sorting
-------

Each heading is a button: press it to sort ascending, again for descending,
and a third time to return to the file's order. Sorting is by the numbers, not
by the text shown; rows with equal values keep the file's order. When sorting
by rank, ``m0`` goes last either way, and sign consistency sorts in the order
all positive, all negative, mixed, all zero. Sorting by **Mean absolute**,
descending, gives the rank order.

How many decimals
-----------------

The table shows each split's weight with **4 decimal places** -- as many as
Percolator writes -- and each statistic (mean signed, mean absolute,
standard deviation) with **6**. Those are rounded for reading only: every
statistic is computed from the file's values at full precision, and the export
keeps the full precision.

No chart
--------

The specification allows a bar chart of the weights beside the table, as long
as the table stays the source of truth. **This release draws no chart.** The
table holds every value, can be read by a screen reader, sorts, and is what
the export writes.

Exporting the weights
=====================

**Export the learned feature weights**, under the results table, writes the
weights table to a new file in the run's ``exports/`` folder, named
``learned-feature-weights_<time>.tsv``, and never overwrites an existing
file. It is tab-separated text with one row per feature in the file's order,
``m0`` included, and these columns: ``feature``; ``bias`` (``true`` for
``m0``); ``split_1_normalised`` and so on, one per split; ``mean_signed``,
``mean_absolute``, ``standard_deviation``, ``sign_consistency`` and ``rank``
(empty for ``m0``); then ``split_1_raw`` and so on -- the weights on the
original, unscaled features, which the table does not show.

Numbers are written at **full precision** (the shortest decimal that reads
back to exactly the value CometGUI computed), with a ``.`` as the decimal point
whatever your computer's language, and in exponent form for very small or very
large values (``1.0E-4``).

As for a table export, a metadata file (the same name with ``.json`` added)
records the run, the weights file with its MD5 and SHA-256, the number of
splits and features, the CometGUI version and the time, and the run's
provenance record gains one entry for the export. The export is refused while
the run is still executing, and when the run has no weights. The exact
format is in :ref:`ref-project-format-exports`.

When there are no weights
=========================

If the run has no weights file -- the Percolator build used could not write
one -- the table is empty and says *"No learned feature weights: no run is
open, or the open run has no weights artefact."* CometGUI does not try to
recover the weights from Percolator's screen output instead. All three
Percolator versions CometGUI knows (3.06.5, 3.07.1 and 3.09) write a weights
file.
