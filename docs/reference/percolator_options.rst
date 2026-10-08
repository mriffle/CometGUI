.. _ref-percolator-options:

==================
Percolator options
==================

Every Percolator command-line option CometGUI may pass, the capability each
one needs, when it is passed, where its value comes from, and the files it
names. Then what is never passed, and what each Percolator version was
measured to accept.

.. note::

   Written for Phase 09 on 2026-10-08 and hand-checked against the code. This
   page is **hand-written**; the tables below restate three enums that are the
   single source of truth -- ``PercolatorOption`` (spelling and capability),
   ``PercolatorArtefact`` (file names) and ``PercolatorSetting`` (the
   Advanced settings), with the argument order fixed by
   ``PercolatorCommands`` -- and ``PercolatorCommandsTest`` holds the code to
   the full argument array element by element. Where this page and the code
   disagree, the code is what runs; report the difference.

The one rule behind every row: **an option is passed only if the capability
probe watched the selected build accept it** (``R-PERC-06``). The command
builder never sees a version number; 3.09 gets no ``-X`` because its probe did
not observe ``XML_OUTPUT``, not because it is 3.09. How the probe works is on
:doc:`../developer/version_capabilities`.

.. contents:: Contents
   :depth: 2
   :local:

The options CometGUI may pass
=============================

.. list-table::
   :header-rows: 1
   :widths: 21 19 30 30

   * - Option and value
     - Capability it needs
     - When it is passed
     - Value: source and default
   * - ``--results-psms <file>``
     - ``PSM_TSV_OUTPUT``
     - Always. A build without the capability is **refused** before launch,
       naming it: the target table is what the run is for.
     - ``outputs/percolator/psms.tsv`` in the run directory.
   * - ``--results-peptides <file>``
     - ``PEPTIDE_TSV_OUTPUT``
     - Always; refused as above without it.
     - ``outputs/percolator/peptides.tsv``.
   * - ``--decoy-results-psms <file>``
     - ``DECOY_OUTPUT``
     - When the build has the capability; otherwise left out and recorded as
       not passed.
     - ``outputs/percolator/decoy-psms.tsv``.
   * - ``--decoy-results-peptides <file>``
     - ``DECOY_OUTPUT`` (the same one: the probe requires both tables from one
       run)
     - As above, together with ``--decoy-results-psms``.
     - ``outputs/percolator/decoy-peptides.tsv``.
   * - ``--weights <file>``
     - ``WEIGHTS_OUTPUT``
     - When the build has the capability (``R-PERC-08``: always requested
       when supported). Without it, left out, and the run records the
       ``percolator.weights-warning`` provenance warning.
     - ``outputs/percolator/weights.txt``.
   * - ``-X <file>``
     - ``XML_OUTPUT``
     - Only when an **enabled** downstream stage needs pout XML (today:
       Limelight conversion) **and** the build has the capability. Needed but
       not capable: left out and recorded. Capable but not needed: not passed,
       and nothing is recorded as missing.
     - ``outputs/percolator/pout.xml``.
   * - ``--seed <n>``
     - ``SEED_OPTION``
     - Always requested; passed when the build has the capability. Not
       passed: provenance records the seed as ``not-passed``.
     - Advanced setting *Random seed*. Default ``1``; 1 to 20000.
   * - ``--num-threads <n>``
     - ``THREAD_OPTION``
     - Always requested; passed when capable.
     - Advanced setting *Thread count*. Default ``3``; 1 to 128.
   * - ``--testFDR <x>``
     - ``TEST_FDR_OPTION``
     - Always requested; passed when capable.
     - Advanced setting ``testFDR``. Default ``0.01``; greater than 0, at most
       1. **Not** the PSM display filter (``R-PERC-04``).
   * - ``--trainFDR <x>``
     - ``TRAIN_FDR_OPTION``
     - Always requested; passed when capable.
     - Advanced setting ``trainFDR``. Default ``0.01``; greater than 0, at
       most 1. Not a display filter.
   * - ``--maxiter <n>``
     - ``MAX_ITERATIONS_OPTION``
     - Always requested; passed when capable.
     - Advanced setting *Maximum iterations*. Default ``10``; 1 to 1000.
   * - *(the merged PIN)*
     - --
     - Always, as the last argument.
     - ``inputs/pin/merged.pin`` in the run directory.

The defaults of the five valued options are Percolator's own documented
defaults, made explicit so every run records them. Values are written as plain
decimal in every locale (``0.01``, never ``0,01`` or ``1E-2``); the command
builder refuses anything else. Where CometGUI's range is narrower than
Percolator's, it is on purpose: Percolator accepts ``--maxiter 0`` (which
trains nothing) and an FDR of ``0`` (for ``--trainFDR`` a sentinel meaning "use
testFDR"), and CometGUI refuses both so that every recorded value is the value
that was used.

Order, working directory and environment
----------------------------------------

The argument array is, in this order: the executable; the two target tables;
the two decoy tables; the weights file; ``-X`` and its file; ``--seed``,
``--num-threads``, ``--testFDR``, ``--trainFDR``, ``--maxiter``, each with its
value; the merged PIN. Each option and its value are two elements, and every
path is absolute. With every capability present and Limelight conversion
switched on, a run's array is::

    <percolator>
      --results-psms           <run>/outputs/percolator/psms.tsv
      --results-peptides       <run>/outputs/percolator/peptides.tsv
      --decoy-results-psms     <run>/outputs/percolator/decoy-psms.tsv
      --decoy-results-peptides <run>/outputs/percolator/decoy-peptides.tsv
      --weights                <run>/outputs/percolator/weights.txt
      -X                       <run>/outputs/percolator/pout.xml
      --seed 1  --num-threads 3  --testFDR 0.01  --trainFDR 0.01  --maxiter 10
      <run>/inputs/pin/merged.pin

Percolator runs with ``outputs/percolator/`` as its working directory and an
environment of exactly ``LANG=C.UTF-8`` -- constructed, never inherited -- so
that the C library's decimal separator is a point whatever the user's shell
says. The exact array of every run is recorded in its provenance
(``tools[].argv``).

What a user can influence
-------------------------

The five Advanced settings (their values; whether each is passed is the
build's capability), the Limelight conversion switch (whether ``-X`` is
wanted), and which build runs. Nothing else: the file names, the order and the
environment are fixed.

The output files
================

.. list-table::
   :header-rows: 1
   :widths: 24 22 54

   * - File (in ``outputs/percolator/``)
     - Requested by
     - Contents
   * - ``psms.tsv``
     - ``--results-psms``
     - Target PSMs, tab-separated, one row per target PIN row.
   * - ``peptides.tsv``
     - ``--results-peptides``
     - Target peptides, tab-separated.
   * - ``decoy-psms.tsv``
     - ``--decoy-results-psms``
     - Decoy PSMs, tab-separated.
   * - ``decoy-peptides.tsv``
     - ``--decoy-results-peptides``
     - Decoy peptides, tab-separated.
   * - ``weights.txt``
     - ``--weights``
     - The learned feature weights: comment lines, then for each
       cross-validation split a header of feature names, a normalised-weights
       row and a raw-weights row. Not one table, hence not ``.tsv``. The number
       of splits is read from the file (``R-PERC-09``).
   * - ``pout.xml``
     - ``-X``
     - Pout XML, targets only, in the ``percolator_out/15`` namespace.

A file is present exactly when its option was passed. After Percolator exits
0, every requested file must exist and hold bytes and **no other file** may be
in the directory -- so a run that did not ask for XML fails rather than keeping
an ``.xml`` it did not request. Then every file is made read-only
(``R-PERC-07``). Percolator's standard output and error are not files it is
asked to write: they go to the run's one stream-tagged ``logs/percolator.log``
(see :doc:`project_format`).

What is never passed
====================

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Option
     - Why not
   * - ``-Z`` (decoys in the pout XML)
     - Phase 00 ran every combination through the Limelight converter
       (:doc:`../feasibility/scientific-path`): ``-X`` alone converts; ``-X
       -Z`` without the converter's ``--import-decoys`` fails; and
       ``--import-decoys`` cannot work with Comet's internal decoys, which is
       the product's configuration. Decoy results are kept in the decoy tables
       instead. The probe still establishes ``XML_DECOY_OUTPUT``, so if Phase 12
       finds a configuration that needs decoys in the XML, the option already
       has a capability to be checked against.
   * - ``--no-analytics``
     - Percolator posts usage analytics by default, and 3.07.1 and 3.09 both
       offer this option. Whether CometGUI should pass it is a product
       decision that has been **escalated, not decided** (Phase 09 work log,
       *Blockers escalated*). Under ``R-PERC-06`` it would in any case need a
       probed capability of its own first, and it has none today.
   * - ``--xml-in`` and the other XML *input* options (``--stdinput-xml``,
       ``--no-schema-validation``)
     - ``--xml-in`` is the pin-XML *reader*, which is what a ``noxml`` build
       lacks, and the product never needs it or its companions: Comet writes
       a tab-separated PIN, which Percolator reads directly
       (``specification.rst``, *Percolator versions and artefact
       availability*). 3.09's help text no longer lists any of the three.
   * - Any other Percolator option
     - No probed capability exists for it, so it is never passed. Adding one is
       one ``PercolatorOption`` constant, one probe run with its own observable
       and one line in the command builder -- never an option passed on trust.

Per-version facts, as measured
==============================

**This table records what was observed; it is not a rule the product
follows.** What a build may be passed is whatever the probe watched *that
build* accept on *that machine*, recorded with the install or registration. No
code reads this table, and no version number implies a capability.

All three were run on this project's Debian 12 (x86-64, ``GLIBC 2.36``) host
on 2026-10-07: 3.06.5 and 3.07.1 as the managed Linux portable ``noxml``
binaries, 3.09 as the upstream ``.rpm``'s binary with Boost 1.66 libraries
beside it, registered as a local binary (Linux has no installable 3.09;
``D-003``).

.. list-table::
   :header-rows: 1
   :widths: 30 14 14 14 28

   * - Option (capability)
     - 3.06.5
     - 3.07.1
     - 3.09
     - Evidence
   * - ``-X`` (``XML_OUTPUT``)
     - accepted
     - accepted
     - **rejected**
     - ``PercolatorRealBinaryTest`` (``theOldestManagedBinary``,
       ``theRealBinaryIsFullyCapable``, ``percolator309HasNoXml``)
   * - ``-X -Z`` (``XML_DECOY_OUTPUT``)
     - accepted
     - accepted
     - **rejected**
     - as above
   * - ``--results-psms``, ``--results-peptides``
     - accepted
     - accepted
     - accepted
     - as above
   * - ``--decoy-results-psms`` with ``--decoy-results-peptides``
     - accepted
     - accepted
     - accepted
     - as above
   * - ``--weights``
     - accepted
     - accepted
     - accepted
     - as above
   * - ``--seed``, ``--num-threads``, ``--testFDR``, ``--trainFDR``,
       ``--maxiter``
     - accepted
     - accepted
     - accepted
     - as above

"Accepted" means the probe's own observable held, not that the option was
listed in help text. The probe took about 5.3 s for 3.06.5, 5.8 s for 3.07.1
and 3.8 s for 3.09, whose two XML runs fail at once.

Further facts measured the same day, recorded for whoever touches the options
next:

* **Value ranges are the same on all three**: ``--seed`` requires an integer
  from 1 to 20000; ``--maxiter`` from 0 to 1000; ``--num-threads`` from 1 to
  128; ``--testFDR`` and ``--trainFDR`` a float from 0 to 1. Each refused the
  same out-of-range values with the same message.
* **Help text, 3.07.1 against 3.09** (evidence of names only, never of a
  capability -- ``R-PERC-02``): 3.09 drops ``--xmloutput``,
  ``--decoy-xml-output``, ``--xml-in``, ``--stdinput-xml`` and
  ``--no-schema-validation``, and adds ``--irls-pep``, ``--pava-pep`` and
  ``--rank-pep``. Every option CometGUI passes is listed by both, except
  ``-X``, the short form of ``--xmloutput``.
* **Output headers** of the PSM and peptide tables are identical across the
  three versions; between 3.07.1 and 3.09 only ``posterior_error_prob``
  differs on the same input (3.09's I-spline PEP). Measured by
  ``RealPercolatorOutputTest`` over CometGUI's own synthetic PIN.
* **No Windows or macOS Percolator has been run by this phase.** Every
  non-Linux capability in the tool list is the manifest's claim, carried with
  its evidence until an install on that machine probes it.

Could this page be generated?
-----------------------------

The first table and the output-file table could be: every column but the
prose is a field of ``PercolatorOption``, ``PercolatorArtefact`` or
``PercolatorSetting``. They are hand-written today; ``PercolatorCommandsTest``
pins the code's side of them. The per-version table cannot be generated from
code, because no code holds it -- by design.
