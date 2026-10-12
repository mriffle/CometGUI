.. _user-limelight:

===============================
Limelight conversion and upload
===============================

The **Limelight** section turns a finished run into a *Limelight XML* file and,
when you ask, sends that file to a Limelight server, where your results can be
browsed, shared and compared in Limelight's web application. In one sentence:
**choose a run in the Results section, convert it in the Limelight section,
then -- only if you want to -- type the server, the project and your submit
key, check the destination CometGUI shows you, and press Send.**

Nothing is ever uploaded automatically. A run does not convert itself, a
conversion does not upload itself, and the upload sends nothing until you have
seen exactly where it is going and pressed **Send to Limelight**.

.. note::

   Written for Phase 12 (Limelight Conversion and Upload) on 2026-10-12,
   against the application as built, with the Limelight converter v2.8.1.
   **Everything on this page has been run on Linux (x86-64) only.** The
   converter, the compatible rerun and the upload have not been run by
   CometGUI on Windows or macOS, and nothing below should be read as a promise
   for those platforms until they have. **No test has ever contacted a real
   Limelight server**: every upload CometGUI has made was to a local stand-in
   that checks what it receives as Limelight's own code would (see `What has
   not been verified`_). How it is built is in
   :doc:`developer/limelight_integration`.

.. contents:: Contents
   :depth: 2
   :local:

Which runs can be converted
===========================

**A run can be converted only if its own Percolator wrote Percolator XML.**
The Limelight converter reads three things from a run: the Comet parameter
file, Comet's pepXML files and Percolator's results **in Percolator's XML
format** (its "pout XML"). It cannot read Percolator's tab-separated tables.

Percolator 3.09 removed the ability to write XML. So a run rescored by 3.09
cannot be converted, however good its results are. Percolator 3.07.1 can
write XML, and it is the version CometGUI chooses when you switch **Limelight
conversion** on in the Percolator section before the run (see
:doc:`percolator`). Percolator writes XML only when it is asked to, so a run
made with 3.07.1 but with Limelight conversion switched **off** has no XML
either.

**What decides is the run, not today's settings.** CometGUI reads what the
run's own Percolator recorded and whether its XML file is still there and
unchanged. Selecting or installing a different Percolator now does not make an
old 3.09 run convertible, and does not stop a 3.07.1 run with its XML from
being converted.

What you see when a run cannot be converted
-------------------------------------------

When the run chosen in Results cannot be converted, the section says so
**before you can try**: every conversion control is greyed out, and a
paragraph explains why in the run's own recorded words -- for example, that
its Percolator 3.09 did not write the Percolator XML the Limelight converter
requires. Below it, one action is offered.

**"Rerun Percolator with 3.07.1 for Limelight".** Offered when CometGUI can
obtain a Percolator that writes XML on this computer. The version in the label
is whichever version CometGUI's own Percolator choice selects for a
Limelight-enabled run -- 3.07.1 today; a future release that writes XML again
would be named instead without any change to CometGUI. Pressing it:

* installs that Percolator first if it is not installed yet (with progress
  and a **Cancel** button);
* creates a **new run** that rescores the original run's **merged PIN** -- the
  file Comet's results were combined into -- after checking it has not changed
  since the original run recorded its checksum;
* **does not run Comet again**;
* leaves the original run exactly as it was.

When the new run has finished it is chosen in the Results section as soon as
its results are listed there, and it can then be converted. The rerun uses the
Percolator section's current settings. It is the same rerun the Percolator
section offers (:doc:`percolator`, *Rerunning Percolator with a compatible
version*), not a second one.

**"Register a local Percolator binary for Limelight".** Offered instead when
no Percolator that writes XML can be obtained here. The explanation then says
plainly that no managed Percolator build that can write XML exists for this
platform (or, if one exists but cannot be used on this computer, why), and
**no rerun is offered**: CometGUI never offers a rerun with a version it
cannot get. Pressing it opens the Percolator section's registration, where you
can point CometGUI at an XML-capable Percolator you already have.

The section
===========

The Limelight section works on **the run chosen in the Results section**; it
keeps no selection of its own. From top to bottom it shows:

#. which run is chosen, and a **Check again** button;
#. *Can this run be converted?* -- the answer, and the action described above
   when the answer is no;
#. the Limelight converter's state: installed, installable, being installed
   (with progress and **Cancel**), or unavailable and why;
#. the converter settings (below);
#. every prerequisite that is not met, by name, then **Convert to Limelight
   XML**, the converter's live output and the result: the file, how many PSMs
   it holds, and its SHA-256;
#. the upload area (`Uploading to Limelight`_).

Installing the converter
------------------------

CometGUI installs the converter, ``cometPercolator2LimelightXML.jar``
v2.8.1, the first time you press **Convert** with it not installed -- through
the Tool Manager, with the same checksum verification as every other tool
(:doc:`tool_manager`) -- and then converts. The download is about 2.6 MB
(2,762,075 bytes). It can be cancelled; nothing is converted then. It runs on
the Java runtime CometGUI itself runs on, so there is nothing else to install.

Converter settings
==================

The settings are saved per run, as soon as you change one, in the run's
``results/limelight-settings.json``. They are used by that run's next
conversion and recorded with it.

**Limelight q-value cutoff** -- default **0.01**. This is the converter's own
setting and is **separate from the PSM and peptide q-value filters** of the
Results and Percolator sections: changing one never changes the other.
What it does is easy to misread, so precisely: **it drops no PSMs.** Every PSM
of the run is written to the Limelight XML whatever the cutoff. The value is
written into the file as the **default PSM q-value filter** Limelight's web
application starts from when it shows the data; you can change the filter
there later. (This was established by converting the same run with no cutoff
and with seven different ones: every file held all 3,897 PSMs, and only that
one value differed.) CometGUI accepts a number from 0 to 1.

**Output Limelight XML** -- leave it empty and each conversion writes a new
file ``outputs/limelight/limelight-<n>.xml`` in the run, numbered after any
already there. You may instead type an absolute path to a file that does not
exist yet, in a folder CometGUI can write to. **A conversion never overwrites
anything.**

**Import decoys** -- greyed out for every run CometGUI makes today, with the
reason beside it. The converter can import decoy matches only if Percolator
was run with its option to write decoys into the XML (``-Z``), and CometGUI
does not ask Percolator for that. In addition, Comet's own generated decoys
are not entries in your FASTA, which the converter's decoy import needs. A
decoy import that cannot work is refused when you set it, not discovered when
the conversion fails -- the converter itself would otherwise quietly import
nothing. The box becomes available only for a run whose Percolator XML
actually contains decoy matches.

**Independent-decoy prefix** -- optional. Proteins whose names begin with this
text are treated by Limelight as *independent decoys* for error estimation.
It is separate from the decoy prefix in the Comet parameters. It cannot be
blank or contain spaces.

**Open-modification mode** -- off by default. The converter then treats mass
differences as unlocalised modification masses (for open searches).

**FASTA and pepXML directory** -- shown, not edited. The FASTA is the one the
run recorded, and it must still be where it was and unchanged; the pepXML
directory is the ``outputs/comet/`` folder of the run that searched (for a
rerun, the original run's). CometGUI gives both to the converter explicitly,
rather than letting it guess.

How a conversion is judged
==========================

Before anything starts, CometGUI checks that the run has finished; that its
``comet.params``, its pepXML files, its Percolator XML and its FASTA exist and
are unchanged since the run recorded their checksums; that every pepXML file
the converter will read belongs to this run; that the converter is installed
and verified; and that the output can be written. Every check that fails is
listed by name and nothing starts.

**The converter's exit status is not trusted.** The converter reports success
(exit status 0) in situations where it has written nothing -- with no
arguments, with an option it does not know, or with an unusable q-value. So a
conversion is kept only when the file it wrote:

* exists, is not empty and can be read;
* is well-formed XML whose top element is ``limelight_input``;
* is valid against the Limelight XML schema **taken from inside the
  converter's own installed file**, the schema the converter itself checks
  against;
* carries the q-value cutoff you asked for.

A non-zero exit status, a timeout (30 minutes) or any failed check fails the
conversion. The converter writes to a temporary file first; a file that fails
is deleted, so **nothing is ever left at the output path looking like a
finished conversion**. A file that passes is moved into place, made
read-only, and its MD5 and SHA-256 are recorded.

One thing you may notice in the converter's output: it prints ``Error
printing runtime information.`` on every run, successful or not. It is
harmless and CometGUI ignores it.

Where things are written
========================

In the run's folder:

* ``outputs/limelight/limelight-<n>.xml`` -- each Limelight XML file, unless
  you named another path;
* ``logs/limelight-converter.log`` -- the first conversion's log, then
  ``limelight-converter.1.log``, ``.2.log`` ... (never overwritten);
* ``logs/limelight-upload.log`` -- the first upload's log, then
  ``limelight-upload.1.log`` ... ;
* ``results/limelight-settings.json`` -- the settings above;
* ``provenance/events.log`` -- the provenance event log, which records each
  conversion and each upload (below).

While a conversion runs, a temporary copy of the Percolator XML is kept in
``results/limelight/`` and deleted afterwards (see
:doc:`developer/limelight_integration` for why).

What the provenance record says
-------------------------------

A conversion or upload is started by you on a finished run, after the run's
``provenance.json`` is complete, so it is recorded as events in the run's
``provenance/events.log`` (format: :ref:`ref-provenance-format-event-types`).

* ``limelight.converting`` -- written **before** the converter starts: the
  converter's version and its file's MD5 and SHA-256; the exact argument list;
  the q-value cutoff and options; and the path, MD5 and SHA-256 of every file
  the converter reads.
* ``limelight.converted`` -- the same, plus the Limelight XML's path, size,
  MD5 and SHA-256, its PSM and peptide counts, the cutoff read back from it,
  and the log's path and checksums.
* ``limelight.unconverted`` -- a conversion that kept nothing: the named
  failure and its reason.
* ``limelight.uploading`` -- written before the first byte is sent: the
  server, the project id and its page, and the file's path, size, MD5 and
  SHA-256.
* ``limelight.uploaded`` -- the server's tracking id and importer directory,
  its program version, the bytes sent, and the upload log's path and
  checksums.
* ``limelight.unuploaded`` -- an upload that queued no import: the step it
  stopped at and the server's own flags and message, or why no answer came.

**No record holds your submit key**, in any form.

Uploading to Limelight
======================

The upload area appears below the conversion. It offers the converted files
of the chosen run, newest first. Only a file one of this run's conversions
recorded, and still unchanged, can be uploaded.

**Limelight server** -- the address of the Limelight web application as your
browser shows it, for example ``https://limelight.example.org/limelight``.
Only ``https`` is accepted, except for an address on this computer, because
the submit key travels inside every request; a Limelight server reachable only
by plain ``http`` cannot be used. An address with a user name, a password, a
``?`` query or a ``#`` fragment is refused.

**Project id** -- the number at the end of the project's address in Limelight
(``.../d/pg/project/<id>``). You type it rather than choose it from a list
because Limelight lists projects only to someone signed in to its web
application; a submit key cannot list them.

**Submit-import key** -- your Limelight submit-import key. The field never
shows what you type, and it is emptied the moment CometGUI takes the key. A
**Remember the key for this server** box keeps it after a successful upload
(see `Your submit key`_), and **Forget the kept key** removes it.

Upload, then Send
-----------------

**Upload...** checks everything and **sends nothing**. It shows you the
destination: the server address (as CometGUI will use it), the project id,
the project's page, the file, its size and its SHA-256.

**Send to Limelight** sends that file to that server and project -- exactly
what is shown, once. **Cancel** withdraws it, and so does changing the file,
the server, the project or the key, or choosing another run: what was shown
is then no longer what would be sent, so you must press **Upload...** again.
A second upload of the same file needs a second Upload and a second Send.

While the file is sent, the section shows a progress bar and the upload's live
log. **An upload cannot be stopped once it has been sent.** Each step has a
time limit (30 seconds to connect, 2 minutes per message, 6 hours for the
file), so it does end.

What comes back
---------------

When the server accepts the upload, it **queues an import** and returns:

* a **tracking id** (Limelight calls it ``uploadKey``),
* an **importer directory** name,
* and CometGUI shows the **project's page** in Limelight.

**The import itself runs later, on the server**, and its progress and outcome
are visible only in Limelight's web application -- a submit key cannot ask
for them, so CometGUI cannot show them. Limelight checks the file again when
it imports it. (CometGUI has already checked it against the converter's
schema before sending.)

If the server refuses -- a wrong key, a project it does not have or you
cannot upload to, a damaged file -- the section shows the step that was
refused and **the server's own words**, and nothing is queued.

Your submit key
===============

* **It is never written to a project file, a log, the provenance record or an
  export**, and it is never shown. CometGUI is tested for this by planting a
  recognisable key, converting and uploading through the interface, exporting,
  and then searching every file the run, the project and the application wrote
  for it, in every form.
* **It is never put on a command line.** The upload is made by CometGUI itself
  rather than by starting another program.
* **Where a remembered key is kept:**

  - **Linux**, when the desktop keyring (the Secret Service, through
    ``secret-tool``) is installed and answers: in the keyring. CometGUI hands
    the key to ``secret-tool`` on its standard input, never on its command
    line. This has been tested only against a scripted stand-in for
    ``secret-tool``, not against a real keyring.
  - **Linux without a keyring, macOS and Windows**: **for this session only**,
    in memory. It is forgotten when CometGUI closes. On macOS and Windows,
    storing it in the system keychain without exposing it on a command line
    has not been verified, so CometGUI does not try.

  The section says which applies on your computer, and why.

What is sent, and to whom
=========================

* The upload goes **only to the server you type**, and only after you press
  Send. Redirects are never followed, because a redirect would send your key
  to an address you were not shown.
* CometGUI sends the Limelight XML file, its name, size and SHA-256, your key
  and the project id. It does **not** send the file's location on your
  computer (Limelight's own upload program does).
* The converter makes no network connections; none were observed when it was
  run (:doc:`developer/limelight_upload_spike`).

When something goes wrong
=========================

**The conversion controls are greyed out.** Read *Can this run be converted?*:
the run's Percolator wrote no XML, or the run is still executing, or no run is
chosen in Results.

**"The conversion cannot start: ..."** Each line names a prerequisite and the
file. A file that "changed since the run recorded it" -- the FASTA, a pepXML,
``comet.params`` or the Percolator XML -- means the run's inputs or outputs
were edited or replaced after the run; CometGUI will not convert a run whose
files no longer match its record.

**"The converter's output was not kept ..."** The converter ran but its file
failed one of the checks in `How a conversion is judged`_. The message names
the check and quotes the converter's last lines; the full output is in the
conversion's log.

**The upload is refused with the server's words.** The message is Limelight's
own. A wrong key is the usual cause; the server's message quotes the first two
characters of the key you sent, which helps you see which key was used.

**"... no answer from the Limelight server ..."** The server could not be
reached, cut the connection, took longer than its time limit or answered with
something that was not Limelight's protocol. Nothing was queued.

What has not been verified
==========================

Stated plainly:

* **Windows and macOS**: no conversion, rerun or upload has been run there.
* **A real Limelight server**: no test contacts one. Uploads were tested
  against a local stand-in that speaks the upload protocol of Limelight
  v8.3.1 and checks every message and file as the real server's code does;
  Limelight's own upload program was also run against that stand-in and
  accepted. A real server of another version may differ.
* **The import on the server** after an upload: not observable with a submit
  key.
* **A real desktop keyring**: the Linux keyring store has been tested only
  against a stand-in for ``secret-tool``.
* **Decoy import** through CometGUI: it is never offered for a CometGUI run
  today (see `Converter settings`_); the converter's decoy import was run only
  outside the interface, on a separately prepared search over a FASTA that
  already contained decoy sequences, with Percolator's ``-Z``.
