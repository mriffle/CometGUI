.. _dev-provenance-ui:

==================
Provenance section
==================

How CometGUI shows a run's provenance record, exports it and checks it against
the disk -- **as built** in Phase 13. In one paragraph: one read model,
``org.cometgui.workflow.provenance``, turns a run directory into display text
and owns the three actions on a record (export JSON, export RST, verify
against disk); it reads with Phase 04's readers, hashes with the product's one
hasher with the cache bypassed, redacts with the session's redactor and renders
exports with Phase 04's writers, and keeps no file open. The section's
view-model reaches it only through a port the application implements, calls
that port only off the JavaFX thread, and lays out what it is given. *Open
file location* starts the platform's file manager through the one process
service with an argument array.

.. note::

   **State after Phase 13 -- Provenance UI and Reports**, written on
   2026-10-14 by work unit 6 against the code at ``55adc7f``. The user-facing
   description is :doc:`/provenance`; the model, the read model's rules, its
   warning sources and the settings keys Phase 13 added are in
   :doc:`provenance_schema` (:ref:`dev-provenance-schema-read-model`); the
   on-disk format is :doc:`../reference/provenance_format`. Design decisions
   cited as ``P13-n`` are recorded, with each work unit's sign-off, in
   ``handoffs/PHASE-13-worklog.rst``. **Everything here has run on Linux
   x86-64 only** (see `What has only run on Linux`_). The phase's exit-gate
   test on real runs is described in :ref:`dev-provenance-gate-test`.

.. contents:: Contents
   :depth: 2
   :local:

Packages and classes
====================

.. list-table::
   :header-rows: 1
   :widths: 22 30 48

   * - Module
     - Package
     - Classes
   * - ``cometgui-workflow``
     - ``workflow.provenance``
     - The read model (P13-2): ``ProvenanceReader`` (``list``, ``load``),
       ``LoadedRun`` (the record, ``exportJson``, ``exportRst``,
       ``verifyAgainstDisk``), ``ProvenanceView`` (the seven parts),
       ``RunListing``, ``RunCondition``, ``RecordState``, ``WarningSource``,
       ``DiskVerification``, ``DiskOutcome``, ``NothingToExportException``;
       package-private ``ViewBuilder``, ``ParametersBuilder``,
       ``DiskVerifier``, ``DisplayText``, ``ExportRenderer``. No JavaFX.
   * - ``cometgui-workflow``
     - ``workflow.steps``
     - What the run records for the section to show:
       ``CometParameterProvenance`` (origins and presets, P13-4) and
       ``ToolWarnings`` (advisories and manifest/probe discrepancies, P13-9).
   * - ``cometgui-provenance``
     - ``provenance.io``
     - ``AtomicDocumentWriter``'s bounded rename retry and its
       ``RetryWaiter`` seam (P13-1); see
       :ref:`dev-provenance-schema-rename-retry`.
   * - ``cometgui-ui``
     - ``ui.viewmodel.provenance``
     - ``ProvenancePort`` (what the section asks of the application),
       ``ProvenanceViewModel``, ``OpenedRecord`` (one opened run, which the
       actions act on), ``ProvenanceTexts`` (the read model's display text
       laid out as lines). Toolkit-free.
   * - ``cometgui-ui``
     - ``ui.controls.provenance``
     - ``ProvenancePane``: the run selector and *Read the runs again*, the
       seven parts, the buttons, and the clipboard. Holds no logic.
   * - ``cometgui-app``
     - ``app.config``
     - ``ProjectProvenancePort`` (the port over the session's project and
       ``ProvenanceReader``), ``FileManagerLauncher`` (P13-6), and
       ``RunWiring.provenance``, which composes both over the session's
       redactor and the product's hasher.

The read model
==============

Described with its rules in :ref:`dev-provenance-schema-read-model`: one of
everything, never a holder, a run without a sealed record still opens,
exports rendered from the one loaded model, verification with the cache
bypassed, and where each warning comes from. Everything below sits on top of
it and re-implements none of it.

The view-model and the pane
===========================

**Two threads (P13-8).** ``ProvenanceViewModel`` calls the port only on its
background executor -- listing, opening, exporting, verifying and opening a
location all block -- and applies every answer on the interface thread. The
file chooser, a modal dialog, is asked on the thread the action was called on,
as the parameter editor's is. ``ProvenanceSectionUiTest.noPortCallOnTheFxThread``
builds the section over the application's own port with a recorder in front
of it and presses every action.

**Stale answers are dropped.** Each listing and each opening is numbered; an
answer for a run that is no longer the chosen one, an older listing arriving
after a newer one, and the answer of an export, verification or location
started on a run that is no longer open are all discarded.

**Display text only.** Every published text is the read model's display text,
laid out by ``ProvenanceTexts``; nothing is parsed, hashed, compared or
redacted in the view-model. A copy returns exactly the read model's value --
the recorded digest, or the command's display form
(``ProcessRedactor.redactedDisplayCommand``, a bracketed array of quoted,
escaped arguments that is deliberately not a shell command) -- and the pane,
which alone can reach the system clipboard, puts it there.

**Partial files are said in words**: a file row that is not complete starts
with ``PARTIAL --``. The export and verify buttons are enabled only for an opened run whose record
is sealed and readable (``OpenedRecord.canExport``); verification has its own
*Cancel*, which the read model honours between files.

**The run list refreshes** when the section is built and whenever a run or a
Percolator rerun starts or ends (``CometGuiApplication``), and on *Read the
runs again*. The Limelight section's compatible rerun does not trigger it.

The pane added 35 identifiers to ``UiIds``, each with an accessible name; the
accessible-name walk's floor was raised from 174 to 209 to keep counting
them. The section's arrival note in ``SectionArrivals`` now says it is live.

The application's port
======================

``ProjectProvenancePort`` answers every call with ``ProvenanceReader``: the
runs are ``list`` of the session's project with the engine's executing runs (so
a run whose ``run.json`` says *running* and that nothing executes is
*interrupted*); a run is ``load``; and the exports and the verification are the
loaded run's own, so they act on **the record the section shows**, never one
read again later (P13-3). It needs no project lock: nothing it does writes
into the project.

``RunWiring.provenance`` builds the reader over ``SessionSecrets::redactor``
-- the session's redactor, not the pattern-only rule set -- so that a value the
session admitted as secret is redacted from every displayed text, copy and
export. ``ProvenanceSessionRedactionTest`` proves it through the production
composition with a value only the session's registry knows: visible before it
is admitted, absent after from every displayed text, every copy and both
exports.

Exports go to a file chosen through ``FileChooserPort.chooseExportTarget``,
suggested as ``provenance-<run id>.json`` or ``.rst``. The read model refuses
an existing target unless replacement is confirmed; the view-model then offers
*Replace the existing file*, which repeats the export with
``replaceExisting``.

Open file location (P13-6)
==========================

``FileManagerLauncher`` starts the platform's own file manager with an
argument array, through the one process service -- never ``HostServices``,
``java.awt.Desktop`` or a shell:

.. list-table::
   :header-rows: 1
   :widths: 18 34 48

   * - Platform
     - Argument array
     - Notes
   * - Linux
     - ``xdg-open <the file's folder>``
     - ``xdg-open`` has no "select this file" form. Observed only through a
       recording process runner; this host has no ``xdg-open``.
   * - macOS
     - ``open -R <file>``
     - Reveals the file in Finder. **Unverified**: never run on macOS.
   * - Windows
     - ``explorer.exe /select,<file>``
     - One argument. **Unverified**: never run on Windows, and how the JDK
       quotes that argument on Windows' command line has not been observed.

**A failure is stated, never a silent no-op.** An unrecognised operating
system, an application composed without a process service, a launcher that
cannot be started, a folder that no longer exists, and -- on Linux and macOS --
a launcher that exits non-zero within ``EXIT_BOUND`` (5 s; ``xdg-open`` says
"no method available" that way) are each an ``IOException`` the section shows.
On Windows only a launch that could not start is a failure, because
``explorer.exe`` returns 1 even when it did what was asked. A launcher still
running after the bound has handed the request on and is reported as started.
Every message passes the session's redactor; the path acted on is never put in
a message as it is.

Which tests prove the exit gate
===============================

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Gate item
     - Tests
   * - 1. Every listed input and output has both digests, and an independent
       recomputation matches
     - ``RealProvenanceGateTest`` ``ListedFiles`` (real run, own digests).
   * - 2. Every tool's version, path and both digests match the tool cache
     - ``RealProvenanceGateTest`` ``Tools`` (markers found by walking a cache
       the product's installer filled); ``ProvenanceSectionUiTest.aCompletedRun``
       (the tool rows' digests against ``OwnDigests``).
   * - 3. Every argument array present and displayable, one per spectrum file
     - ``RealProvenanceGateTest`` ``Commands``;
       ``ProvenanceSectionUiTest.aCompletedRun`` (Copy command read back from
       the clipboard).
   * - 4. A failed and a cancelled run open and show what happened, partial
       outputs marked
     - ``RealProvenanceGateTest`` ``FailedAndCancelled``;
       ``ProvenanceSectionUiTest.aFailedRun`` and ``aCancelledRun``; the
       unsealed and unreadable cases over constructed run directories in
       ``ProvenanceStatesTest``.
   * - 5. JSON and RST exports from the same model; the RST's facts asserted
       against the JSON
     - ``RealProvenanceGateTest`` ``Exports`` (the test's own JSON and RST
       readers); ``ProvenanceExportTest`` (one object, rendered not copied, the
       run's own documents never a target).
   * - 6. No secret in either export
     - ``RealProvenanceGateTest`` ``Secrets``; ``SeededSecretTest``;
       ``ProvenanceSessionRedactionTest``.

The independence rules -- own digests, own JSON and RST readers, tools located
through the cache's markers -- are in :ref:`dev-provenance-gate-test`. The
read model's own tests (``cometgui-workflow``, ``org.cometgui.workflow.provenance``)
assert hand-typed values written by ``CompleteRun`` and ``RunDirectories``,
never values the read model produced on an earlier run.

What has only run on Linux
==========================

Everything above. Two parts have code that differs by platform and has never
run anywhere but Linux:

* **Open file location's macOS and Windows argument arrays**, above.
* **The bounded rename retry's purpose.** The retry runs identically on every
  platform and is exercised and mutation-tested here through its seams, but
  what it exists for -- Windows refusing to replace ``provenance.json`` while a
  virus scanner or synchronisation client holds it -- has never been observed,
  nor that such holders release within the 2130 ms bound, nor that a JDK
  reader does not block the replace. The worst case is a clean failure, never a
  torn file; see :ref:`dev-provenance-schema-platform`.

Residue
=======

* **Replace is offered after any refused export**, including one aimed at the
  run's own ``provenance.json`` or ``provenance.rst``. Pressing it shows the
  read model's refusal again and nothing is written. A distinct exception type
  for "the run's own record" in the read model would let the view-model
  withhold the offer.
* **The run list does not refresh on a Limelight compatible rerun** (*Read the
  runs again* does).
* **A file replaced between hashing and display** could be shown with the
  other file's digest check. Accepted: a second hasher would be worse.
* **A run killed before sealing on a real engine is not proved end to end**;
  the reading of such a directory is proved over a constructed one
  (:ref:`dev-provenance-gate-test`).
* **Two ways a secret can still reach a run's files**, both found by unit 4
  and reported upward: a value typed as a Comet parameter is written verbatim
  into the archived ``comet.params``, which ``AC-PRV-04`` requires to be the
  exact executed file; and a Limelight server refusing a wrong submit key
  quotes the key's first two characters, which Phase 12's P12-20 passes
  through into the upload log and ``events.log`` -- a whole-secret sweep
  cannot see a two-character prefix.
