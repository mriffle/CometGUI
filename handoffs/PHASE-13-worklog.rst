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

Design decision from unit 2 (2026-10-13)
========================================

P13-4, amended -- parameter names go in settings VALUES, never in keys
    *The orchestrator's call.* Unit 2 checked every name in the bundled
    metadata (releases 2026.03.0, 2026.02.2, 2024.01.0, 118 names) before
    writing code and stopped: **39 Comet parameter names contain upper-case
    letters** (``use_A_ions`` ... ``use_Z_ions``, ``equal_I_and_L``,
    ``add_Cterm_peptide``, every ``add_<X>_<residue>``), so no per-parameter
    key can satisfy ``[a-z0-9]+(\.[a-z0-9-]+)+`` without a lossy mapping or a
    schema change. Chosen (option A): ``comet.origin.<origin word>`` = the
    parameter names with that origin, space-separated in schema order (five
    hand-typed words; ``COMET_DEFAULT`` absent); ``comet.preset.<nn>.<field>``
    for each applied preset's identity, and ``comet.preset.<nn>.applied.<mm>``
    = ``<name> = <value text>`` in ``comet.params`` line syntax. A test over
    every bundled schema pins what this relies on (no name holds whitespace or
    ``=``). Rejected: lower-casing the name into the key (relies on no two
    names colliding by case, which a future release could break) and an
    escape code (unreadable). Accepted limits, documented in the code: a
    value carried across a release change keeps its ``preset`` origin while
    the preset list is cleared; a retry of an unchanged configuration records
    the first preparation's origins. Origins never enter a fingerprint.

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
     - Accepted after one rework, ``eb256cb`` ``97e444d``
   * - 2
     - **Parameter origin recorded** (P13-4): each non-default
       ``ValueOrigin`` and the applied preset identity as settings keys at
       run start, the constants pinned and pattern-checked, plumbing from
       the parameter editor through the run port if the preset identity is
       not already in the request; existing pinned provenance expectations
       updated by hand, never regenerated.
     - R-PROV (application provenance); spec *Provenance UI: Parameters*
     - Accepted, ``fdb1cfc`` (after a stop for a design decision, P13-4
       amended)
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
     - Accepted after one rework, ``c330b0a`` ``b957f4a``
   * - 3b
     - **The warnings the read model found nobody writes** (added after unit
       3): ``R-TOOL-07``'s manifest/probe capability disagreement is not
       recorded anywhere (the probe silently wins in
       ``ManagedToolManager.probed``), and Comet's tool record carries no
       advisories (its identity is built with an empty warnings list), so the
       Provenance section's "manifest/probe discrepancies" and Comet
       advisories would always be empty. Record both as ``ToolRecord``
       warnings at run time, from the facts the installation already holds;
       no schema change. Tests through a real installation marker and a run's
       ``provenance.json``.
     - R-TOOL-07; spec *Provenance UI: Warnings*
     - Accepted after one rework, ``8007dc0`` ``8140fd0``
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

Unit 1 sign-off
---------------

**Accepted 2026-10-13 after one rework.** Commits ``eb256cb`` (retry,
``RetryWaiter`` seam, ``AtomicDocumentWriterRetryTest`` 22 tests,
``ReadersHoldNoDescriptorTest`` 10 tests, the developer page) and ``97e444d``
(rework).

* **Diff read.** Only ``Durability.moveIntoPlace`` is retried, only for
  ``AccessDeniedException`` or an exception whose class is exactly
  ``FileSystemException``; ``MOVE_ATTEMPTS`` 10, pauses 10, 20, 40, 80, 160,
  320, 500, 500, 500 ms (2130 ms) through a package-private ``RetryWaiter``
  (``Thread::sleep`` in production); exhaustion rethrows the last refusal with
  the earlier ones suppressed and the existing ``deleteTemporary`` path runs;
  an interrupt in a pause re-sets the status and throws
  ``InterruptedIOException`` (cause: the refusal). No operating-system
  branch. ``FileSystemDurability`` and ``Durability`` changed in Javadoc only;
  ``verify-provenance-gates.sh`` control 5's anchor untouched. Neither reader
  needed changing. The two cited precedents were checked against my own
  knowledge of both projects (robustio's two-second ``arbitraryTimeout``;
  graceful-fs's 60 s Windows rename retry on ``EACCES``/``EPERM``/``EBUSY``).
* **Rejected once.** ``mvn -pl cometgui-provenance -am install`` -- which the
  agent had not run -- failed SpotBugs with four findings in the two new test
  classes (a huge shared string constant, two ``throws Exception``, a
  ``RuntimeException`` rethrow). Fixed in the tests without any exclusion or
  suppression (``97e444d``).
* **Tests, run by me**: ``install`` BUILD SUCCESS -- domain 1180, provenance
  709 (0 failures, 2 skipped, pre-existing), ``BugInstance size is 0``, "All
  coverage checks have been met".
* **My injections** (production code, anchor matched once, restored and
  ``sha256sum -c`` OK, 52 tests run in the two writer classes each time):

  - O1 one attempt fewer (``attempt >= MOVE_ATTEMPTS - 1``): 3 failures, 1
    error, e.g. ``expected: <refusal #10> but was: <refusal #9>``.
  - O2 ``AccessDeniedException`` no longer retried: 6 failures, 2 errors,
    e.g. ``expected: <10> but was: <1>``.
  - O3 interrupt status not re-set: 3 failures, ``interrupt status
    swallowed``.
  - O4 earlier refusals not attached on exhaustion: 3 failures (the
    suppressed list compared as a literal).
  - O5 ``ManifestReader.readFrom`` opening and keeping a ``FileChannel``: 4 of
    10 ``ReadersHoldNoDescriptorTest`` tests red, ``expected: <0> but was:
    <1>``.

* **Targeted PIT, by me** (``-DtargetClasses=org.cometgui.provenance.io.*``):
  40 mutations, 39 KILLED, 1 TIMED_OUT (``attempt++`` to ``attempt--`` at
  line 382, a genuine endless loop), 0 SURVIVED, 0 NO_COVERAGE.
  ``RetryWaiter`` is an interface and yields none.
* **Harnesses, run by me**: ``provenance`` 24 controls PASS (239 s),
  ``docs`` PASS, ``traceability`` 8 PASS.
* **Files outside the module the tests read**: ``/proc/self/fd`` only.
* **Surprise, for the record**: two tests with one method name in different
  ``@Nested`` classes made surefire's XML report one test fewer than ran; the
  agent renamed them. Harnesses that count tests from that XML can
  under-count elsewhere -- not this phase's to fix; noted for tier 1.

Unit 2 sign-off
---------------

**Accepted 2026-10-13.** Commit ``fdb1cfc`` (21 files). The agent first
stopped before writing code, as briefed, on finding 39 upper-case parameter
names; I chose option A (P13-4, amended, above).

* **Diff read.** ``params.comet.presets.PresetApplication`` (identity plus the
  values it set; ``describes(model)`` true only while one of its values is in
  place with origin ``PRESET``); ``ParameterSession`` remembers applications
  (``adoptPreset``), cleared by a new configuration, import, migration or
  release change, kept by edits; ``RunEnginePort.start`` carries them;
  ``WorkflowRunPort`` -> ``SearchRequest.withPresets`` (old constructors
  kept); ``workflow.steps.CometParameterProvenance`` holds the keys and the
  hand-typed wire words; ``CometWorkflow.prepare`` merges its settings before
  the run is reserved. Presets and origins feed no input and no fingerprint.
  One hand-typed expectation updated by hand
  (``CometWorkflowTest.prepareRecordsTheRun``). No POM, no module edge.
* **Tests, run by me**: ``mvn -pl cometgui-params-comet,cometgui-workflow,
  cometgui-ui,cometgui-app -am install`` BUILD SUCCESS (36 min): workflow 929,
  ui 796, app 297 (1 skipped, the opt-in upstream test), params-comet 2123,
  every upstream module green, ``BugInstance size is 0`` in every module.
* **My injections** (anchor once, restored, ``sha256sum -c`` OK):

  - J1 preset origin word ``builtin`` for ``built-in``: 3 of 19 red
    (``wireWords``, ``aPresetApplied``, ``aPresetThenAnEdit``).
  - J2 applied values numbered from 00: 5 of 19 red, e.g.
    ``twoPresetsInOrder: expected: <num_threads = 8> but was: <null>``.
  - J3 ``describes`` ignoring whether the value is still the preset's: 1 of 6
    red, ``another preset's value ==> expected: <false> but was: <true>``.

* **Targeted PIT, by me**: ``CometParameterProvenance`` and ``SearchRequest``
  with the three workflow test classes: 17 KILLED, 1 NO_COVERAGE
  (``SearchRequest.withPercolator``, reached by tests I did not include; the
  agent's run with every test was 18/18). The agent's runs: params-comet
  8/8, ui 270/272 (two pre-existing ``generation++`` survivors in
  ``RunViewModel``), app not PIT-gated.
* **Harnesses, run by me**: ``workflow`` 110 PASS (511 s), ``quality`` 42
  PASS, ``paramui`` 87 PASS (2079 s). The agent ran ``docs`` and
  ``traceability`` (one line in ``docs/developer/workflow_engine.rst``).
* **Files outside the modules the tests read**: none new (the real-run test
  uses the existing ``scratch/`` fixtures).

Unit 3 sign-off
---------------

**Accepted 2026-10-13 after one rework.** Commits ``c330b0a`` (28 files: the
package ``org.cometgui.workflow.provenance`` -- ``ProvenanceReader``
``list``/``load``, ``LoadedRun`` with ``exportJson``/``exportRst``/``verifyAgainstDisk``,
``ProvenanceView`` with the seven parts, ``ViewBuilder``, ``ParametersBuilder``,
``DiskVerifier``, ``DisplayText``, ``ExportRenderer``; two role constants in
``RunResultFiles``) and ``b957f4a`` (rework, one test).

* **Diff read.** One reader (``ManifestReader.readFrom``, event-log
  ``recover``), one redactor (every displayed string through
  ``DisplayText``), one command rendering
  (``ProcessRedactor.redactedDisplayCommand``, already reachable -- no new
  module edge), one hasher (``CachingHashService.rehash``). Exports render the
  one loaded ``ProvenanceManifest`` through ``ManifestWriter`` and
  ``ProvenanceReportWriter`` and write atomically; an existing file is refused
  unless replacement is confirmed, the run's own ``provenance.json``/``.rst``
  always. No JavaFX, no POM, no schema change.
* **Rejected once.** My injection K1 -- ``DiskVerifier`` comparing SHA-256
  only -- left all 55 package tests green: a record whose MD5 alone is wrong
  would have verified as matching. Rework ``b957f4a`` adds
  ``eitherDigestAloneDiffers`` (MD5 wrong / SHA-256 wrong, for files, logs
  and an executable, hand-typed RFC 1321 / FIPS 180-2 vectors); K1 re-run:
  red, 1 of 56.
* **Tests, run by me**: ``mvn -pl cometgui-workflow install`` BUILD SUCCESS,
  987 tests (988 after the rework), ``BugInstance size is 0``.
* **My other injections** (anchor once, restored, ``sha256sum -c`` OK):

  - K2 tool executables left out of verification: 4 of 55 red
    (``10 files, 3 logs, 2 executables ==> expected: <15> but was: <13>``).
  - K3 a warning's message not redacted: ``SeededSecretTest.absentEverywhere``
    red, naming the leaked string.
  - K4 only FAILED (not PARTIAL/CANCELLED) files warned about: 3 red
    (``census``, ``failedRun``, ``cancelledRun``).

* **Targeted PIT, by me** (``org.cometgui.workflow.provenance.*`` with its
  tests and ``ProvenanceReaderOnEngineRunsTest``): 234 mutations, 231 KILLED,
  3 TIMED_OUT (negated loop conditions), 0 SURVIVED, 0 NO_COVERAGE; 13 of the
  16 compiled top-level classes carry mutations, the other three being
  ``package-info`` and two enums. **The package is not a POM PIT target**
  (the owner's build-time question, like ``workflow.pdv``/``.limelight``).
* **Harnesses, run by me**: ``workflow`` 110 PASS (518 s), ``quality`` 42 PASS.
* **Files outside the module the tests read**: ``/proc/self/fd`` only.
* **Found by the unit, acted on**: ``R-TOOL-07``'s discrepancy warning is
  written by nothing, and Comet's tool record carries no advisories -- unit
  3b added above. A file replaced between hashing and display could be shown
  with the other file's digest check (accepted; a second hasher would be
  worse).

Unit 3b sign-off
----------------

**Accepted 2026-10-13 after one rework.** Commits ``8007dc0`` (18 files) and
``8140fd0`` (one test). **A change to Phase 05's and Phase 08/09's code,
reported to tier 1.**

* **Diff read.** ``domain.tools.ToolOffer`` gains ``unconfirmedClaims``
  (only on an installed offer; never also a capability; old constructor
  kept); ``ManagedToolManager`` fills it from the shipped manifest record and
  the verified completion marker -- **no re-probe, no marker or format
  change**; ``workflow.steps.ToolWarnings`` is the one place the sentences
  and keys are made; Comet's tool records now carry advisories then
  discrepancies (they carried nothing), Percolator's advisories, discrepancies,
  then the R-PERC-08 warning; settings ``comet.advisory.<id>``,
  ``comet.probe-discrepancy.<nn>``, ``percolator.probe-discrepancy.<nn>``;
  read model ``WarningSource.MANIFEST_PROBE_DISCREPANCY``. Two hand-updated
  expectations (``ProvenanceWarningsTest.words``, ``census``). Docs: one
  clause in ``workflow_engine.rst``, one row in ``provenance_format.rst``.
* **Decision accepted (P13-9):** a discrepancy is one direction only -- the
  manifest claims a capability the install's probe did not find. A capability
  the probe found that the manifest does not list is the manifest being
  silent; counting it would put about ten warnings on every Percolator run.
* **Rejected once.** My injection L3 (``break`` after the first unconfirmed
  claim, so only one is ever reported) left all 28 ``ManagedToolManager*``
  tests green. Rework adds an install-path test with three claims, one
  confirmed; L3 re-run: red (1 of 29, ``and both it missed are carried, in
  manifest order``).
* **Tests, run by me**: ``mvn -pl cometgui-domain,cometgui-install,
  cometgui-workflow,cometgui-app -am install`` BUILD SUCCESS: domain 1198,
  install 1045 (2 skipped), workflow 1001, ui 796, app 299 (1 skipped), every
  module 0 failures, SpotBugs 0.
* **My other injections**: L1 Comet's discrepancies dropped from its tool
  record: 2 red (``cometWarnings``, ``cometAdvisoryAndDiscrepancy``); L2
  discrepancies before advisories: 3 red.
* **Targeted PIT, by me**: ``ToolWarnings`` and ``CometSelection`` 16/16
  KILLED; the agent's ``ManagedToolManager`` 61/61 and ``ToolOffer`` 35/35.
* **Harnesses, run by me**: ``install`` 95, ``workflow`` 110, ``percolator``
  109, ``quality`` 42, ``docs``, ``traceability`` 8 -- all PASS.
* **Files outside the modules the tests read**:
  ``scratch/phase05/artefacts/v2026.03.0__comet.linux.exe`` (already read by
  other app tests).
* **Carried into unit 4**: Comet's tool record is written with an empty
  capability set (``CometWorkflow`` passes ``Set.of()``). Nit, not acted on:
  ``ToolWarnings`` repeats ``AdvisoryRendering.forSelection``'s one-line loop
  rather than calling it.
