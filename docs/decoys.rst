.. _user-decoys:

======
Decoys
======

Percolator learns to tell right identifications from wrong ones by comparing
the matches to real (*target*) proteins with matches to *decoy* proteins --
sequences that cannot be in the sample, usually the target sequences reversed.
A search with no decoys gives Percolator nothing to learn from; a search with
decoys counted twice gives it the wrong picture. Either way the run would
finish and its numbers would mean nothing, so CometGUI checks the decoy
configuration **before Comet starts** and refuses to run one that cannot work.

.. contents:: On this page
   :local:
   :depth: 1

Where the decoys come from
==========================

You choose this once, on the Essentials tab, as one setting in words
(``R-DEC-01``). It is what Comet's ``decoy_search`` parameter means:

.. list-table::
   :header-rows: 1
   :widths: 34 14 52

   * - Choice
     - ``decoy_search``
     - What happens
   * - **Decoys are already in my FASTA**
     - ``0``
     - Comet makes no decoys. Your FASTA must already hold a decoy entry for
       each target, its name beginning with the decoy prefix.
   * - **Comet makes decoys, reported together with the targets**
     - ``1``
     - Comet adds a reversed decoy for every target peptide; targets and
       decoys compete, and each spectrum gets one best match. Your FASTA must
       hold **targets only**. This is the usual choice for Percolator.
   * - **Comet makes decoys, reported separately**
     - ``2``
     - As above, but targets and decoys are searched and reported separately.
       Your FASTA must hold **targets only**.

The decoy prefix
================

A decoy protein is recognised by the start of its name: the **decoy prefix**,
``DECOY_`` unless you change it. There is one prefix for the whole project
(``R-DEC-03``): it is what Comet puts in front of the decoys it makes, what
CometGUI looks for in your FASTA, what Percolator uses to tell decoys from
targets, and what the Limelight converter is given. The prefix is
case-sensitive: ``DECOY_`` and ``decoy_`` are different prefixes.

How CometGUI checks your FASTA
==============================

Before every run, CometGUI reads your FASTA from start to end (it does not load
it into memory, so a large proteome is fine) and counts its entries and how
many of them are decoys (``R-DEC-02``):

* an **entry** is a line that begins with ``>``;
* its **name** (accession) is the first word after the ``>``, for example
  ``sp|P02769|ALBU_BOVIN`` in ``>sp|P02769|ALBU_BOVIN Albumin OS=Bos taurus``;
* it is a **decoy** if that name begins with the decoy prefix.

Lines may end the Windows or the Unix way. A file that does not exist, cannot
be read, holds no entry, or does not begin with a ``>`` line is refused with a
message naming it.

Two combinations work, and two are refused:

.. list-table::
   :header-rows: 1
   :widths: 30 25 45

   * - Your FASTA
     - Where the decoys come from
     - Result
   * - holds decoys
     - already in my FASTA (``0``)
     - Runs.
   * - targets only
     - Comet makes them (``1`` or ``2``)
     - Runs.
   * - targets only
     - already in my FASTA (``0``)
     - **Refused: no decoys anywhere.**
   * - holds decoys
     - Comet makes them (``1`` or ``2``)
     - **Refused: decoys twice.**

The two refusals
================

Each is an error of CometGUI's parameter check, attached to the **Internal decoy
search** setting and to the database, and a configuration with an error is not
run. The Run section shows the message as the reason the workflow cannot start
the search, under ``The workflow engine cannot start this search:``, and Run
stays disabled; no run is created and Comet is not started. The messages name the setting, its meaning, the prefix, the file and
the count. With a FASTA of 1000
target proteins at ``/data/subset.fasta``:

**No decoys anywhere** (rule ``decoy.none_anywhere``)::

    decoy_search = 0 (no internal decoys) and /data/subset.fasta holds no entry
    whose accession begins with DECOY_ (0 of 1000 records): Percolator would
    have no negative examples; set decoy_search to 1 or 2 so that Comet makes
    decoys, or choose a FASTA whose decoys begin with DECOY_

To fix it, let Comet make the decoys, or choose a FASTA that already holds
them. If your FASTA does hold decoys under another prefix (``REV_``, say), set
the decoy prefix to that.

**Decoys twice** (rule ``decoy.double_decoys``), with a FASTA that holds 1000
targets and 1000 decoys::

    decoy_search = 1 (Comet's internal decoys, concatenated) and
    /data/target-decoy.fasta already holds 1000 entries whose accession begins
    with DECOY_ (1000 of 2000 records; the first is
    DECOY_sp|A0A075B6H9|LV469_HUMAN): Comet would make decoys of those decoys
    too, so decoys would be counted twice; set decoy_search to 0 to use the
    FASTA's own decoys, or choose a FASTA of targets only

With ``decoy_search = 2`` the meaning reads ``(Comet's internal decoys,
reported separately)``; the rest is the same.

Searching an existing index
===========================

If you search a Comet index (an ``.idx`` file) rather than the FASTA itself,
Comet uses the decoy setting the index was **built** with and ignores what the
search says. Indexes written by Comet 2026.03.0 also record the decoy prefix
and use that. CometGUI reads the index's own description before the run and
refuses one whose decoy setting or prefix disagrees with yours, for example::

    the index /project/index-cache/k1/subset.fasta.idx records "DecoySearch: 0",
    and Comet searches an existing index with what it records, so
    decoy_search = 1 would be silently ignored; set decoy_search to 0, or
    rebuild the index from its FASTA with this search's settings

The same check covers the enzyme, the modifications and the other options an
index records, and an index written by a Comet release that cannot read it
(Comet 2026.03.0 reads only indexes it wrote itself, format 5). The details
are on the developer page :doc:`developer/comet_parameter_schema`.

After Comet runs
================

Comet writes one result file for Percolator (a PIN file) per spectrum file.
Before those are merged for Percolator, the workflow checks each one for both
target and decoy rows (``R-DEC-04``). A PIN file with no decoy rows -- or with
no target rows, or no rows at all -- stops the run at the step that validates
Comet's outputs, with a message naming the decoy configuration rather than a
Percolator error later. For example::

    the PIN file <run>/outputs/comet/k562_3.pin holds 1807 target rows and no
    decoy row (Label -1), so Percolator would have no negative examples; the
    decoy configuration was decoy_search = 1 (Comet's internal decoys,
    concatenated), decoy_prefix = "DECOY_"

The checks before Comet starts make this rare, but not impossible: an index
built by Comet 2026.02.2 in fragment-ion mode with ``decoy_search = 1`` was
measured to search to no decoy rows at all, and it is this check that stops
such a run. With ``decoy_search = 2`` Comet also writes a separate decoy
pepXML file for each spectrum file; it is checked too, and the PIN file still
holds both targets and decoys.

The merged PIN is checked once more immediately before Percolator starts --
also when Percolator is rerun from an earlier run's merged PIN without
searching again -- and a merged PIN with no decoy rows stops the run without
starting Percolator (:doc:`percolator`).

How the run itself works is on the developer page
:doc:`developer/workflow_engine`.
