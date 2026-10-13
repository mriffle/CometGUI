=====================================================
PHASE-13 work log -- Provenance UI and Reports
=====================================================

:Phase: 13
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``6b4b0a9`` (brief ``handoffs/PHASE-13-BRIEF.rst``)
:Started: 2026-10-13

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``6b4b0a9`` on ``main``; no other agent live in the
tree (only ``<defunct>`` Java zombies, which PID 1 does not reap).
``bash scripts/verify-test-gates.sh --preflight``: "every precondition holds".
**No baseline build was taken**, by the owner's build-economy rule (brief,
*Build economy*).

Facts established by the orchestrator before design (2026-10-13)
-----------------------------------------------------------------

* **The model, both writers and the reader exist** (Phase 04,
  ``cometgui-provenance``): ``ProvenanceManifest`` and its records,
  ``ManifestWriter``/``ProvenanceReportWriter`` (both ``redactingWith`` a
  ``SecretRedactor``, ``render`` and ``writeTo``), ``ManifestReader``
  (``readFrom(Path)`` is ``Files.readString`` then ``parse`` -- the file is
  read whole and closed before parsing), ``ProvenanceEventLogReader.recover``
  (streams the log while parsing, then closes).
* **Every run already writes both documents.** ``RunExecution.finish``
  writes ``provenance/provenance.json`` and ``provenance/provenance.rst``
  from one ``ProvenanceManifest`` on every outcome (completed, failed,
  cancelled); a failure to write either is a ``recordingErrors`` entry, not
  an exception. ``RunLayout`` pins ``provenance.json``, ``provenance.rst``
  and ``events.log`` (Phase 04's escalation 6 is closed there).
* **Events written after the seal** (``export.written``, ``pdv.launched``,
  ``mztab.written``, six ``limelight.*``) are in ``events.log`` only, per tier
  1's Phase 10 ruling that ``provenance.json`` is sealed.
* **Finalisation** is ``AtomicDocumentWriter``: temp file beside the target,
  forced, then ``Durability.moveIntoPlace`` (``Files.move`` with
  ``ATOMIC_MOVE`` and ``REPLACE_EXISTING``, no retry), then a best-effort
  directory sync. ``Durability`` is the package-private seam; the order
  ``[syncFile, move, syncDirectory]`` is asserted as a literal.
  ``scripts/verify-provenance-gates.sh`` control 5 injects into
  ``FileSystemDurability``.
* **Parameter origin** is carried per parameter by ``CometParameters``
  (``ValueOrigin``: ``COMET_DEFAULT``, ``COMETGUI_DEFAULT``, ``PRESET``,
  ``USER``, ``IMPORTED``, ``WORKFLOW_ENFORCED``) but **is not recorded in
  provenance today**, and no preset identity reaches the workflow; the
  applied preset is known only to ``ui.viewmodel.params.PresetsViewModel``.
  The settings map is an open namespace with a closed key shape
  ``[a-z0-9]+(\.[a-z0-9-]+)+``, so recording it is additive and is **not**
  a schema change.
* **The UI**: ``SectionId.PROVENANCE`` exists with a "this section arrives in
  phase 13" placeholder in ``SectionArrivals``; ``cometgui-ui`` already
  depends on ``cometgui-provenance`` and ``cometgui-workflow``. Phase 12's
  pattern: ``ui.viewmodel.<x>`` + a port, ``ui.controls.<x>`` pane, the port
  implemented in ``cometgui-app``. ``ResultsPort.runs()`` lists only runs with
  results, so it cannot serve failed and cancelled runs.
* **No precedent for "Open file location"** anywhere in the product, and no
  ``xdg-open`` on this host.
* ``docs/provenance.rst`` is a Phase 01 stub; ``docs/developer/provenance_schema.rst``
  requires Phase 13 to keep it in sync with the UI.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P13-1 -- the Windows ``ATOMIC_MOVE`` question, settled by design (unit 1)
    *The orchestrator's call, reported to tier 1 as a change to Phase 04's
    code.* Two halves.

    **The viewer is never a holder.** It reads ``provenance.json`` whole
    (``ManifestReader.readFrom``: read, close, then parse), keeps no handle,
    no channel, no memory map and no ``WatchService`` on it or its
    directory, and a test proves on this platform that no descriptor for the
    file remains in ``/proc/self/fd`` after a load. The same for
    ``events.log``, which is append-only and never renamed over. Java's own
    NIO opens files on Windows with read, write **and delete** sharing, so
    even a reader caught mid-read does not block a replace -- a fact recorded
    as reasoned, not observed.

    **Finalisation retries a refused replace, briefly and boundedly**,
    because the holders the product does not control -- a virus scanner that
    opens a freshly closed file to scan it, a sync client -- are exactly what
    makes ``MoveFileEx`` refuse on Windows, and they let go within
    milliseconds. The precedent is ordinary practice for this exact failure
    (Go's ``cmd/go/internal/robustio`` and npm's ``graceful-fs`` both retry a
    Windows rename on access-denied and sharing-violation errors). Rules:

    * only the **rename** step is retried, and only when it throws
      ``AccessDeniedException`` or an ``IOException`` whose class is exactly
      ``FileSystemException`` (how the JDK reports ``ERROR_SHARING_VIOLATION``
      on Windows); never ``AtomicMoveNotSupportedException``,
      ``NoSuchFileException``, any other ``FileSystemException`` subclass,
      any other ``IOException``, a ``RuntimeException`` or an ``Error``;
    * a bounded number of attempts with a bounded total wait of about two
      seconds, the numbers named constants with their reasoning;
    * every attempt renames the **same** complete, forced temporary file, and
      a refused rename leaves the target untouched, so the whole-document
      guarantee and Phase 04's gate item 5 are unchanged: a reader sees the
      whole old document or the whole new one, and when the bound is
      exhausted the existing failure path runs (temporary file removed, last
      failure rethrown, target exactly as before);
    * interruption during a wait stops retrying at once, removes the
      temporary file, leaves the interrupt status set and is reported, never
      swallowed;
    * **the same code runs on every platform** -- no operating-system branch
      -- so it adds no new tier-B divergence and is fully exercised and
      mutation-tested here through the ``Durability`` seam and a
      substitutable waiter. On POSIX a refused rename is a permanent
      permission problem and costs at most the bound before the same failure
      is reported.

    **Unverified on Windows, and recorded so**: that Windows reports these
    holders with exactly these exception types; that they release within the
    bound; that a JDK reader does not block the replace. The residue becomes
    smaller rather than closed: in the worst case a finalise now fails
    *cleanly* (the engine records "could not write provenance.json" and the
    run keeps its ``events.log``), never torn.

P13-2 -- one read model, in ``org.cometgui.workflow.provenance``
    The one place that turns a run directory into what the Provenance
    section shows: loading (P13-1's closed read), the run's summary, tools,
    files, parameters, timeline, logs and warnings, the display form of a
    command, verification against disk, and the two exports. It reads the
    one model through Phase 04's reader and renders through Phase 04's
    writers; it re-implements no validation, no hashing (``HashService``,
    bypassing the cache), no redaction (``domain.secrets``), no command
    rendering (Phase 03's display rendering) and no RST generation. The
    view-model (``ui.viewmodel.provenance``) consumes it through a port the
    app implements; the pane (``ui.controls.provenance``) holds no logic. No
    new module edge without asking the orchestrator.

P13-3 -- the exports are re-rendered from the loaded model, never copied
    Export JSON is ``ManifestWriter`` over the ``ProvenanceManifest`` read
    from disk; export RST is ``ProvenanceReportWriter`` over **the same
    object**, both redacting with the session's redactor, both written with
    ``AtomicDocumentWriter`` to a path the user chose. Copying the files'
    bytes would export whatever is on disk, unredacted by today's rules and
    unchecked. A run with no sealed ``provenance.json`` has nothing to export
    and says so.

P13-4 -- parameter origin is recorded additively, in the settings map
    Unit 2 records, at run start, each Comet parameter's ``ValueOrigin``
    where it is not the Comet default, and the identity of any applied
    preset, as settings keys pinned as constants beside the existing ones
    and asserted against the key pattern. No schema change. The viewer then
    shows, per parameter, the recorded value, its origin, the release
    default and -- where a preset is recorded -- the preset's value, diffed.

P13-5 -- a run without a sealed record still opens
    A run whose ``provenance.json`` is absent (a crash, or still running)
    is shown from ``run.json`` and ``events.log`` alone, labelled as having
    no sealed record, with any event-log defects (torn tail, malformed line,
    sequence gap) shown as warnings. A record that fails to parse is shown as
    exactly that, with the reader's located message, never as an empty run.

P13-6 -- "Open file location" goes through the process service
    The platform's own file manager is started with an argument array
    through the one process service (Linux ``xdg-open <directory>``, macOS
    ``open -R <file>``, Windows ``explorer.exe /select,<file>``), never
    ``HostServices``, ``java.awt.Desktop`` or a shell. Only the Linux argv
    can be observed here, and only through a recording runner; macOS and
    Windows are unverified and recorded so. A missing launcher is a stated
    failure on screen, not a silent no-op.

P13-7 -- recompute from disk with a hasher the product does not own
    The product's "Verify against disk" uses the one ``HashService`` (cache
    bypassed). **The gate tests do not**: every digest they compare with the
    record comes from the test's own ``MessageDigest`` loop over the file,
    and every tool file is located in the tool cache by the test, not taken
    from the record's path alone. A test that compares the record with
    itself, or with a value the product computed, is rejected at sign-off.

P13-8 -- nothing slow on the JavaFX thread
    Loading, verifying, exporting and opening a location run on the
    background executor; the view-model publishes results to the FX thread.
    A GUI test asserts the thread, as the FIX-P12-P13 repair did for the Tool
    Manager.

Work units
==========

Serial, in this order; no two units are live at once (owner, 2026-08-31).

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items
     - Sign-off
   * - 1
     - **The Windows question, settled before any viewer code** (P13-1).
       The bounded rename retry in ``AtomicDocumentWriter`` through the
       ``Durability`` seam and a substitutable waiter: transient refusals
       then success; refusal until the bound, then the existing failure
       path (temporary removed, target byte-identical or absent, last
       failure rethrown); every non-retryable type not retried (one
       attempt); interruption during a wait; the success-path order literal
       unchanged; the concurrent-reader proof still green. A test that
       ``ManifestReader.readFrom`` and ``ProvenanceEventLogReader.recover``
       leave no descriptor for the file in ``/proc/self/fd``. The decision
       written into ``docs/developer/provenance_schema.rst`` (*Atomic
       finalisation*, *Platform divergence*).
     - R-PROV-05; Phase 04 gate 5 kept
     - Pending
   * - 2
     - **Parameter origin recorded** (P13-4): each non-default
       ``ValueOrigin`` and the applied preset identity as settings keys at
       run start, the constants pinned and pattern-checked, plumbing from
       the parameter editor through the run port if the preset identity is
       not already in the request; existing pinned provenance expectations
       updated by hand, never regenerated.
     - R-PROV (application provenance); spec *Provenance UI: Parameters*
     - Pending
   * - 3
     - **The read model** ``workflow.provenance`` (P13-2, P13-3, P13-5,
       P13-7): load a run (closed read), summary, tools (version, managed or
       local, path, MD5, SHA-256, capability badge), files (role, path,
       size, MD5, SHA-256, status), parameters (the exact archived
       ``comet.params``, the Percolator settings, origin, release default,
       preset value, diff), timeline (stages with command, time, duration,
       exit status), logs, warnings (advisories, compatibility workarounds,
       partial files, manifest/probe discrepancies, fallbacks, event-log
       defects); a command's display form; verify against disk; export JSON
       and RST from one loaded model. Hand-typed expectations; failed,
       cancelled, unsealed and unparsable records.
     - R-PROV-01..05; AC-PRV-06, 07; gate 4, 5, 6 (model)
     - Pending
   * - 4
     - **The gate on real runs** (P13-7): a real Comet + Percolator run of
       the pinned fixture, a real failed run and a real cancelled run,
       through the engine. Items 1 (independent recomputation of every
       listed input and output), 2 (every tool's version, path and both
       digests against the files in the tool cache), 3 (every argv, one
       per spectrum file, present and rendered for display), 4 (failed and
       cancelled open and show what happened, partial outputs marked), 5
       (the RST report's facts asserted against the JSON, both exported
       from one loaded model), 6 (a secret seeded through every entry path,
       Limelight key and URL credentials included, then every export and
       every file the run directory holds, enumerated from disk, grepped).
     - Gate 1-6; AC-PRV-01..09
     - Pending
   * - 5
     - **The Provenance section** (P13-6, P13-8): view-model, pane, the
       app's port and wiring; the run list including failed and cancelled
       runs; the seven parts the specification names; Copy MD5, Copy
       SHA-256, Copy command, Open file location, Export JSON, Export RST,
       Verify against disk. GUI tests: a real run through the interface
       then its record on screen (clipboard contents asserted as values),
       a failed and a cancelled run opened, the exports written and equal
       to the read model's, nothing slow on the FX thread.
     - Spec *Provenance UI*; AC-PRV-07; gate 3, 4 (GUI)
     - Pending
   * - 6
     - **Documentation and traceability**: ``docs/provenance.rst`` (the
       user page), ``docs/developer/provenance_schema.rst`` in sync with the
       UI and a developer section for the read model, stale statements
       (the arrival placeholder, any "Phase 13 will" text), traceability for
       ``AC-PRV-01..09``.
     - R-DOC; AC-PRV-*
     - Pending
   * - 7
     - **The phase harness**, registered additively in
       ``scripts/verify-all-gates.sh``: every gate item graded on a
       production injection, lean, cost stated.
     - Gate 1-6
     - Pending

Unit sign-offs
==============

(None yet.)
