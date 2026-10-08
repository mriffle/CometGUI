.. _user-percolator:

==========
Percolator
==========

Percolator re-scores Comet's matches: it learns, from the target and decoy
matches of your own search, which matches are trustworthy, and gives each one
a q-value. CometGUI runs it on the merged result file Comet writes for all your
spectrum files together (the *merged PIN*).

This page explains which Percolator CometGUI uses, why, and what each setting
in the **Percolator** section does. In one sentence: **CometGUI picks the
newest Percolator that can do everything the run needs, tells you when that is
not the newest one and why, and records the choice.**

.. note::

   Written for Phase 09 (Percolator Adapter and Version Capabilities) on
   2026-10-08, against the application as built. Everything described here as
   measured was measured on Linux x86-64. No Percolator has been run by
   CometGUI on Windows or macOS yet, so on those platforms this page describes
   what the application is built to do. The results tables and the learned
   weights view are not built yet (:doc:`results`,
   :doc:`learned_feature_weights`), and neither is Limelight conversion
   (:doc:`limelight`); this page says only what the Percolator section itself
   does.

.. contents:: Contents
   :depth: 2
   :local:

Which Percolator runs
=====================

Why the version matters
-----------------------

Percolator versions differ in one way that matters to CometGUI. **Percolator
3.09 can no longer write Percolator XML** (its "pout XML" output), and the
Limelight converter needs exactly that file. Every other output CometGUI uses
-- the PSM and peptide tables, the decoy tables and the learned feature
weights -- comes from 3.06.5, 3.07.1 and 3.09 alike. So:

* if you do **not** want Limelight conversion, the newest Percolator is the
  best choice, and CometGUI uses it;
* if you **do**, CometGUI must use a Percolator that writes XML, and today the
  newest release that publishes such a build for Linux, Windows and macOS
  alike is **3.07.1** (``D-002``).

The default: "latest compatible"
--------------------------------

CometGUI does not have a built-in favourite version. Each time the list of
Percolator builds changes, or you switch Limelight conversion on or off, it
works the default out again: **the newest Percolator on this computer that has
been seen to do everything the switched-on stages need.** "Seen" is meant
literally -- CometGUI runs every Percolator it installs or registers on a small
test input and records what it actually does (see `What CometGUI checks before
it trusts a build`_). A claim it has not seen does not count.

What that gives today:

.. list-table::
   :header-rows: 1
   :widths: 30 35 35

   * - Situation
     - Limelight conversion off
     - Limelight conversion on
   * - Linux, managed builds only
     - 3.07.1 -- 3.09 publishes no Linux build CometGUI can install
       (``D-003``)
     - 3.07.1
   * - Linux, with 3.09 registered as your own binary
     - **3.09**, the newest
     - **3.07.1**, and CometGUI says it passed over 3.09 because 3.09 cannot
       write XML
   * - Windows (not yet run by CometGUI)
     - 3.09 (it must be installed from the :doc:`tool_manager` before it can
       run)
     - 3.07.1

**On a Mac** (also not yet run by CometGUI), 3.07.1's ability to write XML is,
until you install it, an inference from the downloaded file rather than
something anyone has watched it do, so it does not count yet. Until 3.07.1 is
installed, switching Limelight conversion on reports conversion as unavailable
and says that installing 3.07.1 will check it. This is deliberate: CometGUI
does not promise what it has not seen. (Upstream's 3.09 for macOS is for Apple
silicon only, and needs macOS 15.)

If a future Percolator release can write XML again, it becomes the default for
Limelight runs as soon as it is added to CometGUI's tool list and installed. No
part of CometGUI needs to change for that.

The Limelight switch, and the notice it gives
---------------------------------------------

The **Limelight conversion** check box in the Percolator section is how you
tell CometGUI whether this run needs Percolator XML. It is off by default.
Switching it changes the default Percolator when it has to, and a notice under
it says exactly what happened, for example::

    The default Percolator changed from 3.09 (registered local binary) to
    3.07.1 because Limelight conversion was switched on and needs XML_OUTPUT.

If nothing changed, the notice says that too. ``XML_OUTPUT`` is the name of
the capability "writes Percolator XML" (the full list is on
:doc:`reference/percolator_options`).

The switch only decides which Percolator runs and whether it is asked for XML.
The conversion itself is a later part of CometGUI (:doc:`limelight`).

When a newer version was passed over
------------------------------------

When the default is not the newest Percolator on this computer, the section
lists every newer build it passed over and the reason, naming the version and
what it lacks::

    Using Percolator 3.07.1 rather than 3.09 (registered local binary)
    because 3.09 (registered local binary) lacks XML_OUTPUT, which Limelight
    conversion needs (the Limelight converter reads the Percolator XML that
    XML_OUTPUT writes).

The same sentence is written into the run's provenance record, so the answer
to "why did this run use an older Percolator?" is still there a year later.

Choosing a different version
----------------------------

The version list offers every Percolator that is installed or registered here,
with the default marked "the resolved default". You may choose another one.
Your choice is kept until you press **Use the default**, choose the default
again, or the build you chose disappears -- and the run's provenance records
that the build was your choice, not the default.

A managed build that is not installed is named under the list as installable
from the Tool Manager section; it cannot be chosen for running until it is
installed. If the default itself is not installed yet, **Run** says so and is
blocked until you install it.

If you choose 3.09 with Limelight conversion switched on, the run still works:
CometGUI does not ask 3.09 for XML it cannot write, and records that the XML
was not requested and why. That run cannot be converted for Limelight; see
`Rerunning Percolator with a compatible version`_.

Advisories
----------

Some versions come with caveats, shown in the section whenever that version is
selected and written into the run's provenance. They are part of CometGUI's
tool list, word for word. For example:

* **3.07.1** predates two changes: 3.08 switched the default way of
  computing posterior error probabilities (PEP) to I-splines, and 3.08.1 and
  3.09 fixed PEP values above 1.0 (upstream issue #394). So a 3.07.1 run
  computes PEP the older way, and a PEP above 1.0 can appear in its output.
  This is the price of XML today: the only alternatives are a version that
  cannot write XML at all, or building Percolator from source, which this
  project does not do.
* **3.06.5** is two release lines behind and is carried because it runs on
  older Linux systems; it is not a recommended default, and its handling of
  peptide protein identifiers is known to differ from later releases.
* Platform caveats, such as the Windows build needing a Visual C++ runtime, or
  the x86-64 macOS build needing Rosetta 2 on Apple silicon.

When Limelight conversion is unavailable
========================================

If no Percolator on this computer has been seen to write XML, the section says
**Limelight conversion is unavailable**, explains why in one paragraph, and
offers the two documented remedies:

#. **Register a local Percolator binary** that can write Percolator XML (see
   below). CometGUI checks it and offers Limelight conversion if it does.
#. **Convert on a computer whose platform has an XML-capable Percolator**,
   rerunning Percolator there from this run's merged PIN.

The default then falls back to the newest Percolator that *can* run here, so
you still get your rescored results; only the conversion is unavailable.

Registering your own Percolator
===============================

**Register a local Percolator binary...** in the Percolator section lets you
point CometGUI at a Percolator already on your computer. CometGUI runs it to
read its version, refuses anything older than 3.05 or anything that is not
Percolator, records its checksums, and runs the same capability check as for a
managed install. The result appears in the version list as a "registered local
binary", with an advisory that CometGUI did not download it and cannot vouch
for where it came from.

This is also the only way to use **3.09 on Linux**. Upstream publishes no
Linux build of 3.09 that CometGUI can install: its ``.deb`` needs a newer C
library than most current Linux systems have, and its ``.rpm`` needs Boost
libraries it does not ship. This was decided and recorded (``D-003``): an
absent entry is honest; a fabricated one is not.

A registration lasts until CometGUI exits; it is not yet remembered between
sessions (:ref:`tool-manager-limits`).

What CometGUI checks before it trusts a build
=============================================

Every Percolator is checked by running it, once, when it is installed or
registered -- never by reading its version number or its help text. The check
runs the build eleven times on a small synthetic input of 64 target and 64
decoy rows, once per capability, and keeps a capability only if the build
visibly did it: the file it was asked to write exists with the expected rows,
or the run with that one option completed and produced its results. One
unsupported option therefore cannot hide another. It takes a few seconds per
build.

On Linux this check found that 3.06.5 and 3.07.1 can do all eleven things
CometGUI asks of a Percolator, and that 3.09 can do all of them except the two
XML ones. A build installed by an earlier version of CometGUI, whose check
knew about fewer capabilities, is shown as not installed until it is installed
again, so that it is checked again rather than believed.

The settings
============

Two filters, and two learning thresholds that are not filters
-------------------------------------------------------------

Two pairs of numbers in this section both default to 0.01, and they are
easily confused. They are different things.

**The PSM and peptide q-value filters** (on the section's main page) decide
which results you *see and export*: a PSM is shown when its q-value is at or
below the PSM filter, and a peptide when its q-value is at or below the
peptide filter. Each is 0.01 by default, may be anything from 0 to 1, and is
set independently of the other. Changing them **never reruns Percolator or
any other tool** and never changes Percolator's output files. (The results
tables they apply to are built in a later phase; see :doc:`results`.)

**testFDR and trainFDR** (under **Advanced settings**) are part of
Percolator's own learning: ``trainFDR`` decides which matches Percolator
trains on, and ``testFDR`` the false discovery rate at which it chooses its
best model and reports its results. Changing them changes what Percolator
computes, so they take effect only when Percolator runs. Their descriptions on
screen say they are not the display filters (``R-PERC-04``).

Advanced settings
-----------------

.. list-table::
   :header-rows: 1
   :widths: 22 14 64

   * - Setting
     - Default
     - What it does
   * - ``testFDR``
     - 0.01
     - Learning threshold; see above. Greater than 0 and at most 1.
   * - ``trainFDR``
     - 0.01
     - Learning threshold; see above. Greater than 0 and at most 1.
   * - Random seed
     - 1
     - Decides how Percolator splits your matches for cross-validation. Fixed
       by default, and always recorded, so a run can be reproduced
       (``R-PERC-05``). From 1 to 20000.
   * - Maximum iterations
     - 10
     - The most training iterations Percolator runs. From 1 to 1000.
   * - Thread count
     - 3
     - How many threads Percolator trains with. Changes how long a run
       takes, not its results. From 1 to 128.

The defaults are Percolator's own, written down so that every run records
them. A value CometGUI cannot accept is refused beside its field, the field
keeps what you typed, and **Run** stays blocked until it is corrected.

A setting the selected build was not seen to accept is marked "Not supported
by this build" and is not passed to it; the run's provenance records that it
was not passed and why. If the seed in particular is not passed, provenance
records ``not-passed`` as the seed that ran, never a seed that was not used.
On the three versions CometGUI manages, every setting is accepted.

Rerunning Percolator with a compatible version
==============================================

If a run used a Percolator that cannot write XML -- 3.09, say -- and you then
want to convert it for Limelight, you do not need to search again. Switch
Limelight conversion on, so that the section selects an XML-capable build,
and press **Rerun Percolator** (under the settings). CometGUI shows what the
rerun will do before it starts, and then:

* creates a **new run**, which records which run it came from;
* reuses that run's merged PIN -- after checking that the file has not changed
  since the original run recorded its checksum; if it has, the rerun is
  refused, naming the file and both checksums;
* runs **Percolator only**. Comet is not run again; the new run's record
  refers to the original run's Comet search instead;
* leaves the **original run exactly as it was**, byte for byte.

The same button reruns Percolator with other settings or another build in
general; it is refused when nothing that Percolator depends on has changed. It
works from the last run of the current session. A rerun cannot itself be
rerun; rerun the original instead.

What is kept, and what is recorded
==================================

**Percolator's own output files are never changed.** After a successful run
they are made read-only in the run's ``outputs/percolator/`` directory:
``psms.tsv`` and ``peptides.tsv`` (the target results), ``decoy-psms.tsv`` and
``decoy-peptides.tsv``, ``weights.txt`` (the learned feature weights), and
``pout.xml`` only when XML was needed and the build can write it. Filtering
and export read them and write elsewhere (``R-PERC-07``).

Before Percolator starts, the merged PIN is checked: it must exist, have the
columns Percolator needs, contain readable numbers, and contain **both target
and decoy rows**. A merged PIN with no decoys stops the run with a message
naming your decoy settings, and Percolator is not started (``R-DEC-04``; see
also :doc:`decoys`).

The run's provenance record (:doc:`provenance`) holds, for Percolator:

* the version, whether it was managed or your own binary, its SHA-256, and the
  capabilities its check observed;
* whether it was the default or your choice, why the default was what it was,
  and each newer version passed over with what it lacked;
* the advisories shown for it;
* the exact command it ran, and anything requested but not passed, with why;
* the settings, including the seed that actually ran;
* every output file with its MD5 and SHA-256.

The exact keys are listed in :ref:`ref-provenance-format-percolator-settings`;
the options CometGUI passes are on :doc:`reference/percolator_options`.
