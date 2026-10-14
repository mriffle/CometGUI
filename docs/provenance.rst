.. _user-provenance:

==========
Provenance
==========

Every run CometGUI makes keeps a **provenance record**: a written account of
exactly what was done, with what, to which files, and what came out. It names
the version and the checksums of every program that ran, the exact command
each one was started with, every input and output file with two checksums
(MD5 and SHA-256), the exact Comet parameter file that was used, the
Percolator settings, how long each step took and how it ended, and every
warning the run raised. Its purpose is that you -- or a reviewer, or you in
three years' time -- can say precisely how a result was produced, check that
the files on disk are still the ones the run used, and reproduce the run.

The **Provenance** section shows that record for any run of the project, lets
you copy any checksum or command out of it, shows any listed file in your
computer's file manager, exports the record as JSON or as a readable report,
and checks the record against the files on disk. In one sentence: **choose a
run, read what it did, and press Verify against disk to confirm that every
file it lists is still exactly what it recorded.**

.. note::

   Written for Phase 13 (Provenance UI and Reports) on 2026-10-14, against the
   application as built. **Everything on this page has been run on Linux
   (x86-64) only.** Two things in particular have never run on macOS or
   Windows; they are described in `What has not been verified`_. The exact
   layout of the files is in :doc:`reference/provenance_format`, and how the
   section is built is in :doc:`developer/provenance_ui`.

.. contents:: Contents
   :depth: 2
   :local:

What the record is, and where it lives
======================================

The record is written into the run's own folder, under ``provenance/``, as the
run goes. Three files make it up::

    MyProject/runs/20260828T231500Z-run-0001/
        run.json             the run's identity and its attempts
        parameters/
            comet.params     the exact Comet parameter file the run used
            percolator-settings.json
                             the Percolator settings (runs with Percolator)
        logs/                one log per program the run started
        provenance/
            provenance.json  the sealed record (machine-readable)
            provenance.rst   the same record as a readable report
            events.log       the running history, one event per line

``provenance.json``
    The complete record, written once, when the run ends -- whether it
    completed, failed or was cancelled. It is **sealed**: nothing changes it
    afterwards. It is written so that it is never seen half-written: either the
    whole document is there or none of it is.

``provenance.rst``
    The same record as a report a person can read (reStructuredText, which is
    plain text with light formatting). It is generated from exactly the same
    information as ``provenance.json`` at the same moment, never written
    separately, so the two cannot disagree.

``events.log``
    The history, written one line at a time while the run happens, so that a
    run that is stopped by a crash or a power cut still leaves an account of
    what it had done. Things that happen to a run **after** it has ended -- an
    export of its results, a spectrum drawn in PDV, a Limelight conversion or
    upload -- are added here, because the sealed record is not changed.

The **archived parameter files** in ``parameters/`` and the **logs** in
``logs/`` are part of the record too: the record lists each with its
checksums, so a changed or missing one can be found (see **Verify against
disk** below).

**Secrets are kept out.** A Limelight submit key, a password in a web address
and anything else CometGUI recognises as a credential are replaced by
``[REDACTED]`` before anything is written (see :doc:`limelight`). Two cases
are known in which something secret can still reach a file:

* **Whatever you type as a Comet parameter value** is written into
  ``parameters/comet.params`` exactly as typed, because that file must be the
  exact file Comet ran. Do not type a password into a parameter.
* **A Limelight server that refuses a wrong submit key** may quote the key's
  first two characters in its answer; CometGUI writes the server's words into
  the upload log and ``events.log`` as they came.

The Provenance section
======================

Choosing a run
--------------

At the top is the list of **every run in the project**, whatever became of
it. Each entry shows the run's identifier, when it was created, what happened
to it and whether it has a sealed record, for example *completed, sealed
record*. The words you may see:

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Shown
     - Meaning
   * - completed
     - The run finished successfully.
   * - failed
     - A step of the run failed.
   * - cancelled
     - You cancelled the run.
   * - running
     - The run is being carried out now, in this session.
   * - interrupted
     - The run's records say it was running, but nothing is running it now:
       CometGUI or the computer stopped while it ran. These are often the runs
       most worth opening.
   * - not started
     - The run was prepared but never started.
   * - run.json unreadable
     - The run's ``run.json`` is missing or damaged. The run is listed by its
       folder name, with the reason, and still opens.

The list is read again by itself whenever a run or a Percolator rerun starts
or ends. **Read the runs again** reads it on demand -- for example after the
Limelight section's compatible rerun has created a new run, which does not
refresh the list by itself.

When a run is chosen, a line under the list says what kind of record it has:
a **sealed record** that was read and checked, **no sealed record**, or a
**record unreadable** (see `Runs without a sealed record`_). Then come the
seven parts below.

Summary
-------

The run at a glance: its identifier and project, its status, when it started
and ended and how long it took, each program and version it used, how many
input and output files it lists, and the computer it ran on -- the CometGUI
version and build, the operating system, the Java version, the language and
number-format settings, and the time zone. (Language settings are recorded
because they can change how numbers are written into a parameter file.)
All times are in UTC.

Tools
-----

One entry for each time a program was run. A two-file search therefore shows
Comet twice -- once per spectrum file, as ``comet-01`` and ``comet-02`` -- and
Percolator once. Each entry gives:

* the program's **name and version**, and the upstream **release tag** for a
  program CometGUI installed;
* whether it is **managed** (installed by CometGUI's Tool Manager, see
  :doc:`tool_manager`) or **local** (a program you registered yourself);
* the **path** of the program that ran, and its **MD5** and **SHA-256**;
* for a managed program, the **artefact** it was installed from: the download
  address and that download's own SHA-256;
* its **capabilities** -- what the program was found able to do when it was
  checked, such as writing Percolator XML. These are what the program was
  observed to do, never assumed from its version;
* any **warnings** recorded against it.

Inputs and outputs
------------------

One entry for every file the run read or wrote: whether it was an **input**
or an **output**, its **role** (``spectrum``, ``fasta``, ``comet-params``,
``pepxml``, ``pin``, ``merged-pin``, ``percolator-psms`` and so on), its path,
size and modification time, its **MD5** and **SHA-256**, and its **status**.

A file that one step writes and a later step reads appears twice, once as an
output and once as an input, because both facts are recorded.

**A file that is not complete is marked in words**, at the start of its
entry: ``PARTIAL -- output pepxml``, with its status explained -- *incomplete:
the stage that wrote it did not complete*. Such a file is what a failed or
cancelled step left behind. It is listed, with its checksums, so that you know
it exists and cannot mistake it for a finished result.

Parameters
----------

* **The exact Comet parameter file** the run used, as text, read-only -- the
  very file Comet was given -- with the line above it saying where it is, the
  SHA-256 the run recorded for it, and whether the file on disk **still
  matches** that checksum. If the file has changed or gone, the line says so
  and a warning is shown. (A file larger than 1 MiB, or one that is not text,
  is not shown; the line says why.)
* **The Percolator settings**: whether the archived settings file still
  matches its checksum, and every Percolator setting the run recorded -- the
  seed it actually ran with, the false discovery rates, the version and why it
  was chosen. See :doc:`percolator`.
* **Each Comet parameter**, one line each: its value; its **origin** -- where
  the value came from; the **release default** (Comet's own default for that
  release); and, if a preset set it, the **preset's value**. Wherever the value
  is not the release default, or not what the preset set, the line says
  *(differs)*. The origins are:

  .. list-table::
     :header-rows: 1
     :widths: 24 76

     * - Origin
       - The value is ...
     * - ``comet-default``
       - Comet's own default for the release.
     * - ``cometgui-default``
       - CometGUI's own starting value, where it differs from Comet's.
     * - ``preset``
       - set by a preset you applied (see :doc:`comet_parameter_presets`).
     * - ``user``
       - one you typed or chose.
     * - ``imported``
       - read from a parameter file you imported.
     * - ``workflow-enforced``
       - fixed by CometGUI because the workflow requires it.

  A run made before CometGUI recorded origins shows the origin as *not
  recorded*.
* **The presets applied**, each with its name, whether it is built in or your
  own, the Comet version it was written for, and every value it set. A preset
  is listed only while at least one of the values it set is still in place.
* **Notes**: anything the section could not show and why, and anything the
  parameter reader reported about the file.

Timeline
--------

Two lists. The first is each program's **execution**, in order: the stage, how
it ended and its **exit status**, when it started and ended and how long it
took, the **command** -- the exact list of arguments it was started with --
its working folder, and any environment variables CometGUI set for it. The
second is every event in ``events.log``, in order, including those added after
the run ended.

Logs
----

Each archived log -- the screen output of each program the run started --
with its path and its MD5 and SHA-256. **Open file location** shows the
selected log in your file manager, from where you can open it.

Warnings
--------

Everything about the run you should know, **grouped by where it came from**,
each group headed by its name and how many it holds. If the list is empty,
nothing was found to warn about.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Group
     - What it means
   * - run outcome
     - The run failed or was cancelled; any output it left is marked partial.
   * - step failure
     - The message a failed or cancelled step left, naming the step.
   * - partial file
     - A listed file that is not complete (see `Inputs and outputs`_).
   * - version advisory
     - A notice about the exact Comet or Percolator version used, as shown
       when it was chosen.
   * - manifest/probe discrepancy
     - CometGUI's list of tool downloads says a program can do something,
       and when it was installed and checked it could not. CometGUI believes
       the check, not the list, and records the disagreement here.
   * - skipped version
     - A newer Percolator that was passed over for this run, and why -- for
       example because it cannot write the Percolator XML Limelight needs.
   * - option not emitted
     - A Percolator option the run asked for and did not pass, because the
       version used was not found to accept it, and why.
   * - weights fallback
     - Percolator was not asked to write its learned feature weights, and why
       (see :doc:`learned_feature_weights`).
   * - tool warning
     - Anything else recorded against a program -- for example, that one of
       its logs could not be checksummed.
   * - warning event
     - A warning the run wrote into ``events.log`` as it went.
   * - limelight failure
     - A Limelight conversion or upload of this run that did not succeed,
       with the reason recorded.
   * - parameter file changed
     - An archived parameter file no longer matches the checksum the run
       recorded, or is gone.
   * - no sealed record, record unreadable, run.json unreadable,
       event log unreadable, event log defect
     - Problems with the record itself; see `Runs without a sealed record`_.

The actions
===========

**Copy MD5** and **Copy SHA-256**
    Under **Tools** and under **Inputs and outputs**. Copy the selected
    entry's recorded checksum to the clipboard, exactly as recorded, and say
    what was copied. They copy what the record says -- not a checksum worked
    out now. To compare with the disk, use **Verify against disk**.

**Copy command**
    Under **Timeline**. Copies the selected execution's command, exactly as
    shown: the recorded list of arguments written as a bracketed list with
    every argument in double quotes -- ``["/path/to/comet", "-P/.../comet.params",
    ...]`` -- so that an argument containing a space is visibly one argument,
    and with any secret replaced by ``[REDACTED]``. It is for reading,
    comparing and recording. It is **not** a command to paste into a terminal:
    it will not run there, by design.

**Open file location**
    Under **Tools**, **Inputs and outputs** and **Logs**. Asks your computer's
    own file manager to show the selected file. On Linux the file's **folder**
    is opened (the Linux mechanism, ``xdg-open``, cannot select a file within
    it); on macOS Finder is asked to reveal the file; on Windows Explorer is
    asked to open the folder with the file selected. If no file manager can be
    started, or the folder no longer exists, the section says so -- it never
    silently does nothing.

**Export provenance JSON** and **Export provenance RST**
    Write the chosen run's record to a file you choose, as JSON or as the
    readable report. The suggested name is ``provenance-<run id>.json`` or
    ``.rst``. Each export is **written afresh from the record that was read**
    when you chose the run -- the JSON and the report from the same record --
    and with today's secret rules applied. Nothing is copied byte for byte, so
    an export never carries a secret CometGUI recognises -- including one it
    learned was a secret only after the run.

    **An export never replaces a file without asking.** If the file you chose
    already exists, nothing is written and the section says so; press
    **Replace the existing file** to write over it, or export again and choose
    another name. The run's own ``provenance.json`` and ``provenance.rst`` are
    **never** replaced by an export, whatever you press: the sealed record
    stays exactly as the run wrote it.

**Verify against disk**
    Works out the MD5 and SHA-256 of every file the record lists **again,
    from the disk** -- every input and output, every archived log and every
    program -- and compares each with the record. Nothing remembered from
    earlier is used. Each file is reported as one of:

    * **matches** -- both checksums equal the recorded ones;
    * **differs** -- the file is there and at least one checksum is not the
      recorded one (both are shown, recorded and found);
    * **missing** -- there is no file at the recorded path;
    * **unreadable** -- something is there and could not be read.

    The result starts with one sentence -- every file matches, or how many
    match, differ, are missing and are unreadable -- followed by one line per
    file, the outcome first. Large spectrum files take time to read; **Cancel
    the verification** stops before the next file, and the sentence then says
    the verification was cancelled and not every file was checked.

What the actions do not do
--------------------------

* **They never change the run's record.** No action writes to
  ``provenance.json``, ``provenance.rst``, ``events.log`` or any other file of
  the run. An export writes only the one file you chose, and is not itself
  recorded in ``events.log``.
* **An export holds the sealed record only.** Events added to ``events.log``
  after the run ended -- results exports, PDV, Limelight -- are shown in the
  Timeline but are not part of either export. ``events.log`` itself is in the
  run's ``provenance/`` folder.
* **Verify against disk checks what the sealed record lists.** The Limelight
  converter, and files written after the run ended, are recorded only in
  ``events.log`` and are not verified by it.
* **A run without a sealed record has nothing to export or verify.** The
  export and verify buttons are unavailable for it, and the section says why.

Runs without a sealed record
============================

A run opens in the Provenance section **whatever state it is in** -- it is
never shown as an empty run.

**Failed and cancelled runs** have a sealed record like any other: CometGUI
writes ``provenance.json`` however a run ends. The Summary shows *failed* or
*cancelled*; the Timeline shows which step ended that way and with what exit
status; every output a step had begun is listed with its checksums and marked
``PARTIAL``; and the Warnings say what happened.

**No sealed record.** A run that is still running, or that was stopped by a
crash before it could write ``provenance.json``, has none. The section says so
and shows what the run folder still holds: the run's identity and attempts
from ``run.json``, and its history from ``events.log``. There is nothing to
export or verify.

**Record unreadable.** If ``provenance.json`` exists but cannot be read -- it
was edited, damaged or truncated by something outside CometGUI -- the section
says *record unreadable*, quotes what the reader found wrong and where, and
shows the run from ``run.json`` and ``events.log``. The damaged file is never
treated as an empty record.

**A damaged event log** is shown with what could be recovered, plus a warning
naming the damage: a **torn last line** (the usual mark of a crash), a
**malformed line** (the file was altered or corrupted), or a **gap** in the
event numbers (events are missing). These are told apart on purpose: "the run
was killed" and "this file has been changed" are different findings.

What has not been verified
==========================

**Everything on this page has been run on Linux (x86-64) only.** Two
behaviours are known to be different elsewhere and have never run there:

* **Open file location on macOS and Windows.** CometGUI starts ``open -R
  <file>`` on macOS and ``explorer.exe /select,<file>`` on Windows. Neither
  has been run on those systems. On Linux the command has been checked only as
  far as CometGUI building the right ``xdg-open`` command; no test opens a real
  file manager.
* **Writing the sealed record on Windows while another program holds it.** On
  Windows, replacing a file that another program has open -- typically a virus
  scanner or a file-synchronisation client inspecting a just-written file --
  can be refused. CometGUI retries for about two seconds. If the other program
  holds the file for longer, the run ends with its record **not written**
  rather than half-written: the run reports that ``provenance.json`` could not
  be written, its ``events.log`` is complete, and the Provenance section shows
  it as having no sealed record (or, for a run that was tried again, the
  previous attempt's whole record). CometGUI's own Provenance section reads the
  record and lets go of it at once, so it never causes this itself. None of
  this has been observed on a Windows computer.

Where nothing above says otherwise, the rest of the section runs the same code
on every platform, but has still only been run on Linux.
