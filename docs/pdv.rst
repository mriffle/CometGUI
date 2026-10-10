.. _user-pdv:

======================
Viewing spectra in PDV
======================

The **Visualisation** section shows the spectrum behind a PSM, annotated, using
PDV (the Proteomics Data Viewer). In one sentence: **select a PSM in the
Results section, then either draw a picture of its spectrum, or open the whole
run in PDV's own window, which then follows whatever you select in Results.**

To make that second route work, CometGUI writes a file in a format called
**mzTab** for PDV. **That mzTab exists only so that PDV can display the run.
It is not one of your results, it is not meant to be shared or opened in other
software, and you should never use it in place of Percolator's own tables.**
See `The mzTab CometGUI writes for PDV`_.

.. note::

   Written for Phase 11 (PDV Integration) on 2026-10-11, against the
   application as built, with PDV 2.7.0. **Everything on this page has been
   run on Linux (x86-64) only.** PDV has not yet been driven by CometGUI on
   Windows or macOS, and nothing below should be read as a promise for those
   platforms until it has. How it is built is in
   :doc:`developer/pdv_integration`.

.. contents:: Contents
   :depth: 2
   :local:

The Visualisation section
=========================

The section has three parts.

**What is selected.** The run and the PSM come from the Results section --
the run it shows and the PSM row you last selected in its table. The
Visualisation section keeps no selection of its own: choosing another run or
row in Results is choosing it here. If Results shows a peptide table, the
section asks you to select a PSM in a PSM table.

**PDV's state.** One line says whether PDV is installed, can be installed,
is being installed (with a progress bar and a **Cancel** button), or cannot be
installed on this computer and why.

**Two ways to look at a spectrum**, described in the next sections:

* **Draw the selected PSM's spectrum** -- PDV draws one picture, which is
  shown in the section and saved in the run.
* **Open run in PDV** -- PDV's own window opens on the run; from then on,
  every PSM you select in the Results section is shown in it. **Close PDV**
  closes that window.

Both are refused, with the reason shown beside the button, while the run is
still executing: its results are not final, and every PDV launch is written to
the run's provenance event log, which the workflow engine is still writing.
Use them when the run has ended.

Installing PDV the first time you use it
========================================

CometGUI does not install PDV until you ask for it. The first time you press
either button with PDV not installed, the section starts the install through
the Tool Manager -- the same install the :doc:`tool_manager` offers, with the
same checksum verification -- and then draws or opens as you asked.

* The download is **about 99 MB** (103,407,417 bytes for PDV 2.7.0).
* You can **cancel** it with the section's **Cancel** button. Nothing is
  drawn or opened then, and the section says so.
* **A search never waits for PDV.** No part of a search needs it, and you can
  configure and run searches while PDV downloads. This is tested by holding
  the download half-way and running a complete search meanwhile.
* While PDV is installing, both buttons say so and wait; when the install
  finishes, the drawing or the window you asked for follows.

Only PDV 2.7.0 has been verified. A PDV version CometGUI has not verified is
given no spectrum format at all, and says so, rather than being assumed to
behave like 2.7.0.

Drawing one PSM's spectrum
==========================

**Draw the selected PSM's spectrum** runs PDV's command line on the run's own
Comet pepXML file and the spectrum file the PSM came from, and shows the
picture PDV draws: an annotated spectrum, 800 x 400 pixels. The message under
it names the PSM, the peptide PDV annotated, the spectrum (by its native id),
the spectrum file and where the picture was saved.

How the spectrum is found, and when it is refused instead of guessed:

* the PSM's identifier gives its spectrum file, scan number and charge;
* the scan number must belong to **exactly one** spectrum in that file, whose
  native id carries it as ``scan=<number>`` -- the form Thermo files have and
  Comet copies into the pepXML. No such spectrum, or more than one, is refused
  with a message naming the scan, because drawing a guess would be drawing
  some other spectrum.

Every picture is checked before it is shown: it must be a valid PNG and must
actually contain drawing, not a blank image. PDV is known to exit "successfully"
having written nothing, so success is decided by the picture, never by PDV's
exit status. A drawing that takes more than two minutes is stopped and
reported.

**Which peptide is annotated.** PDV's command line reads the hit from the
pepXML itself; the message names the peptide it annotated. That this is the
selected PSM's own peptide has been checked for a top-ranked target PSM. For a
PSM that is a lower-ranked hit of its spectrum, check the peptide named in the
message: which hit PDV's command line chooses then has not been verified.
**Open run in PDV** always shows exactly the PSM you selected.

The picture is saved in the run, under ``pdv/figures/`` (one numbered folder
per drawing), and is not removed when you draw another.

Opening a run in PDV's window
=============================

**Open run in PDV** opens PDV's own window on the run shown in the Results
section. It is the route to use for looking through many PSMs.

What happens when you press it:

#. If the run already has a PDV window, it is closed first.
#. CometGUI writes the run's mzTab for PDV (see below), or reuses the one it
   wrote before if none of the files it was made from has changed.
#. PDV is started on a free network port on **this computer only**
   (``127.0.0.1``), chosen fresh each time. The section says it is starting.
#. CometGUI asks PDV, several times a second, whether it has finished reading
   the run. When it has, the section says PDV is ready, and names the port.
   PDV is given five minutes; if it is not ready by then, or if it stops,
   it is ended and the section says why, quoting PDV's exit status and the last
   lines it wrote.

From then on, **each PSM you select in the Results section is shown in PDV's
window** -- from the target or the decoy PSM table, whichever Results shows.
A burst of selections (holding an arrow key, say) is collapsed to the last
one. A selection made before PDV is ready is kept, and sent when it is ready.
A line in the section reports what PDV answered: shown, or not shown and why.

**When the window is closed:**

* when you press **Close PDV**;
* **when the Results section changes run**, or shows no run: the window
  belongs to the run that was open, so it is closed rather than left showing a
  run you are no longer looking at;
* **when CometGUI exits**: every PDV window it opened is closed.

Closing a window ends PDV completely. (PDV's own "shut down" request stops
only the part of PDV that listens to CometGUI and leaves the window running,
so CometGUI also ends the PDV process itself, and waits for it to go.)

PDV's own table of PSMs is shown in its window as usual. Selecting a row there
does not change the selection in CometGUI.

Which spectrum files can be shown
=================================

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - Spectrum file
     - With PDV 2.7.0
   * - mzML
     - **Supported.** This is what CometGUI has been run on, with real Thermo
       data.
   * - MGF
     - **Supported** by the same code, tested on constructed files. No real
       Comet search of an MGF file has yet been viewed end to end.
   * - mzXML
     - **Refused**, with a message, before PDV is started (see below).
   * - MS2, CMS2, BMS2, Thermo RAW
     - **Refused**: PDV reads none of them.

A refused file gives a message of this form, before anything is launched:
*"The spectrum file <path> cannot be visualised by PDV 2.7.0: <why>. <what
this PDV can be given>"*. A file named as mzML or MGF whose content is not
mzML or MGF is refused the same way, naming what was found.

**Why mzXML is refused.** PDV's window mode cannot read mzXML at all. CometGUI
could convert it, but each converted spectrum must carry exactly the
identifier Comet recorded for it in the pepXML, and no mzXML has been searched
by Comet in this project to establish what that identifier is. A guess that
silently matched nothing, or matched the wrong spectrum, would be worse than a
refusal.

**Why PDV is given an MGF copy of your spectra.** CometGUI never hands PDV your
mzML directly. It writes an MGF copy for PDV, in the run's ``pdv/`` folder,
because PDV numbers the spectra of an mzML by their position in the file, not
by their scan number. For a file that holds only part of an acquisition --
scans 11187 to 11987, say -- position and scan number differ for every
spectrum, and PDV then either draws nothing while reporting success, or would
have to be given an identifier that states a false scan number. The MGF copy
avoids both. **No peak is altered:** every m/z and intensity is written so
that reading it back gives exactly the value in your file, bit for bit, and
the tests check exactly that. Your spectrum file itself is only read.

The mzTab CometGUI writes for PDV
=================================

**What it is for.** PDV's window can be controlled by another program only when
it is given an mzTab file, and Comet and Percolator do not write one. So
CometGUI writes one from the run's results, for PDV to read. That is its only
purpose.

**What it is not.** It is **not a scientific deliverable** and **not an
interchange format**: do not send it to collaborators, submit it to a
repository, or open it in other software as the run's results. Its shape is
dictated by what PDV 2.7.0 will accept -- in places that is not what the mzTab
standard intends -- and it holds one spectrum copy per PSM, which no other
program expects. Your results are Percolator's tables, and the filtered
exports the :doc:`results` section writes.

**Where it is and how it is recorded.** It is a **run artefact**:
``<run>/pdv/mztab/psms.mztab``, beside the MGF copies it refers to
(``ms_run-1.mgf``, ``ms_run-2.mgf``, ... one per spectrum file). Each time it is
written, the run's provenance event log gains an ``mztab.written`` record
with its MD5 and SHA-256 (see `What the provenance record says`_). It is
written again whenever any file it was made from has changed, and is otherwise
reused.

What it contains
----------------

One line for **every PSM in Percolator's target PSM table, then every PSM in
its decoy table**, each exactly once and in Percolator's order, and nothing
else. Each line holds:

* the peptide, its flanking residues, the charge and the retention time
  (seconds), from the Comet pepXML;
* the modifications, as signed masses on their residues, exactly as the
  pepXML writes them -- including Comet's fixed modifications, such as
  ``+57.021464`` on every cysteine, which Percolator's peptide does not show;
* the observed *m/z*, from the spectrum file (the precursor's selected-ion
  *m/z*);
* Percolator's score, q-value and posterior error probability, and its
  protein list (joined with ``;``);
* Percolator's PSMId, and whether the row came from the target or the decoy
  table;
* a reference to the PSM's own copy of its spectrum in the MGF.

**Values are copied, never recomputed.** Every number is the source file's own
text: a q-value of ``9.27998e-07`` in Percolator's table is ``9.27998e-07`` in
the mzTab. **Where no source states a value, the field says** ``null``
rather than holding something worked out or made up -- for example the
protein database's name and version, and the start and end positions in the
protein. The theoretical *m/z* column is left out altogether: no source states
it, and PDV 2.7.0 cannot read a file in which that column says ``null``.

**Accessions come from Percolator.** The mzTab lists the proteins Percolator
lists, which are the ones the Results section shows. (Comet's pepXML drops the
``DECOY_`` prefix on some alternative proteins that Percolator keeps; the two
are not merged.)

When it is refused instead of written
-------------------------------------

**If any PSM cannot be represented faithfully, no mzTab is written at all**,
and the message names the PSM, the files involved and the reason. A partial
file that looked complete is exactly what this rule prevents; on a refusal
nothing is left where the mzTab would be. The reasons include:

* a **C-terminal modification** -- PDV has no way to show one;
* an **N-terminal modification that cannot be stated as one known mass**:
  Comet records a modified N-terminus as the total mass of the terminal group,
  and when the search declared more than one N-terminal modification, which
  ones make up that total cannot be read back without calculating it -- so it
  is refused rather than calculated;
* a hit carrying a **sequence variant** (PEFF), which the mzTab cannot state;
* a Percolator row whose peptide or modifications **disagree** with the Comet
  hit it names, or that names **no hit** in the pepXML;
* a PSM whose **spectrum is missing** from the spectrum file, or is there more
  than once, or carries a different scan number;
* a value that is **not a number** where one must be, or text that would break
  the file (a tab, a line break, an empty protein name);
* a source file that **changed while it was being read**.

A run whose Percolator tables hold no PSM at all is also refused: PDV cannot
open an empty result set.

Things you will notice in PDV
=============================

* **Repeated spectrum titles get a suffix.** When one spectrum produced
  several PSMs, each PSM has its own copy of the spectrum (otherwise PDV would
  only ever show the first of them). PDV renames repeated titles: the first
  keeps the spectrum's native id, the next is shown as ``<native id>_1``, then
  ``_2``, and so on. The spectrum, its peaks and the PSM row are right; only
  the displayed title carries the suffix.
* **Peak labels use PDV's default tolerance** (0.05 Da in PDV 2.7.0). Comet's
  ``fragment_bin_tol`` is the width of the bins Comet scores in, not a
  tolerance for labelling peaks, so CometGUI does not turn it into one. The
  tolerance changes which peaks PDV labels, never which spectrum it draws.
* **PDV writes files of its own**, all inside the run's ``pdv/`` folder, never
  beside your spectrum files and never in your home folder:

  - ``psms.mztab.db`` beside the mzTab, and a ``.mgf.cui`` index beside each
    MGF copy;
  - PDV's preferences (``.compomics``) and a Java font cache (``.java``), in
    a ``home`` folder CometGUI gives PDV in place of your own -- under
    ``pdv/window/`` for the window and under each ``pdv/figures/`` drawing.

  These are working files. When the mzTab is written again, its folder is
  deleted and recreated, PDV's files with it.

What the provenance record says
===============================

Two kinds of record are added to the run's provenance event log,
``provenance/events.log``, after the run has finished. The format of each line
is in :ref:`ref-provenance-format-event-types`.

``pdv.launched`` -- **written before every start of PDV**, so that a launch
that then fails still leaves its record. It holds:

* the PDV version, as PDV's own JAR file reports it, and the JAR's path, MD5
  and SHA-256, computed at that moment;
* the kind of launch: ``cli-figure`` (a drawing) or ``denovo-gui`` (the
  window);
* the complete argument list PDV was started with, its working folder and the
  environment variables CometGUI set, with any credential removed;
* what the launch was for: for a drawing, the PSM, the spectrum, the spectrum
  file and the pepXML; for the window, the mzTab, its SHA-256 and its number
  of PSMs.

CometGUI refuses to start a PDV whose JAR reports a version other than the
one the Tool Manager installed. If the record cannot be written, PDV is not
started.

``mztab.written`` -- written each time the mzTab is written (not when it is
reused). It holds the mzTab's path, MD5 and SHA-256; each MGF copy's path,
MD5, SHA-256, number of entries and the spectrum file it was made from; every
source file read -- Percolator's target and decoy PSM tables, each pepXML and
each spectrum file -- with its path, MD5 and SHA-256; and the number of PSMs
read from Percolator's tables and written to the mzTab.

Privacy: does PDV contact the internet?
=======================================

**On the route CometGUI uses, no network contact was observed.** While PDV was
driven through every case CometGUI uses, each PDV process's network
connections were sampled every 50 milliseconds, and every host-name look-up it
made was logged. The only connections were on this computer (CometGUI talking
to PDV on ``127.0.0.1``); the only name looked up was this computer's own.

For completeness: one of the libraries PDV ships with (compomics-utilities)
contains a Google Analytics sender, used by other programs that share that
library. **No part of PDV refers to it**, so PDV cannot reach it on the route
CometGUI uses. PDV's window also has features of its own that do use the
network if you choose them -- online spectrum prediction (off by default),
looking up a spectrum identifier in public repositories, and links to contact
its developers. CometGUI never uses them.

These are observations on one Linux computer, not a guarantee about every
possible use of PDV; the evidence and its limits are in
:doc:`developer/pdv_mztab_spike`.

When something goes wrong
=========================

**"PDV is not installed" or the buttons wait.** If PDV is being installed, the
buttons say so and wait. If the install failed or was cancelled, press the
button again to retry, or see the :doc:`tool_manager`, which shows why it
failed. On a computer for which no PDV build is offered, the section says so.

**PDV fails to start, or nothing is drawn, with no display.** PDV is a desktop
program and cannot run without a screen to draw on: even its command line
opens a (hidden) window first. On Linux, CometGUI passes PDV the ``DISPLAY``
of the desktop CometGUI itself is running on, and the X authority file
(``XAUTHORITY``, or your own ``~/.Xauthority`` when that is not set), because
PDV runs with its own home folder. Running CometGUI over a remote connection
without a display forwarded, or from a session without a desktop, gives PDV no
display; the section then reports PDV's failure with the last lines it wrote.

**"PDV could not show run ...".** PDV did not become ready within five minutes,
or stopped. The message quotes PDV's exit status and its last lines. Very large
runs take PDV longer to read; the mzTab and MGF for one million PSMs are about
280 MB and 1.8 GB.

**The port.** PDV listens only on ``127.0.0.1``, on a port chosen fresh at
each start, so it is not reachable from other computers and there is no port
to configure. If another program takes the port in the moment between
CometGUI choosing it and PDV opening it, PDV is restarted on a new port, up
to three times in all, and every start is recorded. One thing CometGUI cannot
detect: an unrelated program on this computer that answers PDV's questions on
that very port would be believed.

**"... is executing".** Drawing and opening are refused while the run is
executing. Wait for it to end.

**"The mzTab for PDV was not written: PSM ..."** One PSM could not be
represented faithfully; see `When it is refused instead of written`_. The
message names the PSM and the reason. This is deliberate: CometGUI will not
open a run in PDV with PSMs silently missing.

**"The spectrum file ... cannot be visualised by PDV ..."** The run searched a
file format PDV cannot be given; see `Which spectrum files can be shown`_.

What has not been verified
==========================

Stated plainly:

* **Windows and macOS**: nothing on this page has been run there.
* **MGF spectrum files** from a real Comet search, end to end (the code is
  tested on constructed MGF files).
* For a drawing, **which peptide PDV's command line annotates for a PSM that is
  not the top hit** of its spectrum (see `Drawing one PSM's spectrum`_).
* **What PDV's window displays** is proved by a test that reads PDV's own
  state, not by looking at the screen, and PDV's control interface only says
  whether a selection was found, not what was drawn. The tests that prove the
  drawn spectrum is the selected PSM's run PDV's own unmodified program inside
  the test.
* **Very large runs in PDV's window**: the mzTab export has been measured at
  one million PSMs; PDV reading such a run, and CometGUI's lookup of each
  selection, have not been measured at that size.
