=====================================================
PHASE-12 work log -- Limelight Conversion and Upload
=====================================================

:Phase: 12
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 at
   ``6e5b764`` (brief ``handoffs/PHASE-12-BRIEF.rst``)
:Started: 2026-10-11

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``6e5b764`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*): tier 1
ran ``scripts/build.sh`` and the full ``scripts/verify-all-gates.sh`` at Phase
11's exit gate on this tree.

Facts established by the orchestrator before design (2026-10-11)
-----------------------------------------------------------------

* **The converter is already a managed tool.** ``manifests/tools.json``
  carries ``limelight-converter`` 2.8.1 (``JAR``, one artefact on all five
  platforms, SHA-256 ``843573396ce0...``, 2 762 075 bytes);
  ``ToolName.LIMELIGHT_CONVERTER`` exists; ``tools.limelight.LimelightConverterIdentity``
  starts the JAR with ``--version`` and reads ``cometPercolator2LimelightXML.jar
  v2.8.1`` (Phase 05). The pinned JAR is at
  ``scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar`` and
  ``scratch/scientific-path/tool/``. Nothing converts yet.
* **The converter's interface** (Phase 00, ``docs/feasibility/scientific-path.rst``,
  *Stage 3*): ``-c/--comet-params``, ``-p/--percolator-file``, ``-o/--out-file``
  (required); ``-d/--pepxml-directory``, ``-f/--fasta-file``, ``-q/--q-value``,
  ``--import-decoys``, ``--independent-decoy-prefix``, ``--open-mod``,
  ``-v/--verbose``, ``-h``, ``-V``. ``-d`` defaults to the Percolator file's
  directory and the converter derives each pepXML **name** from the
  Percolator PSM ids (no glob). It prints ``Error printing runtime
  information.`` on every run. It exits 0 with no arguments and on an unknown
  option (``D-007``).
* **The schema** is the JAR-root entry ``limelight-xml.xsd`` (65 905 bytes,
  SHA-256 ``e8c1b920...``, no target namespace), the one the converter
  validates against itself. Phase 00's validator accepted the real 12 797 113
  byte output (3897 ``psm``) and rejected three corruptions.
* **Decoys** (Phase 00, *R-LL-05 tested*): ``-X -Z`` without
  ``--import-decoys`` fails; ``--import-decoys`` without ``-Z`` exits 0 and
  imports zero decoys silently; ``--import-decoys`` cannot work with Comet's
  internal decoys (they are not FASTA entries). Phase 09 **never passes
  ``-Z``**, so no CometGUI run today has decoy XML output.
* **Phase 09's pieces this phase must reuse, not rebuild**:
  ``params.percolator.resolution`` (``DownstreamStage.LIMELIGHT_CONVERSION``,
  ``PercolatorResolver``, ``StageAvailability``, ``StageRemedy``
  ``REGISTER_LOCAL_BINARY`` / ``CONVERT_ON_SUPPORTED_PLATFORM``,
  ``ResolutionMessages``); the compatible rerun (``PercolatorRerun``, a
  derived run, ``run.json`` schema version 2 with ``derivedFrom``) and its
  view-model ``ui.viewmodel.percolator.PercolatorRerunViewModel``, shown in
  the Percolator section; the Limelight switch and status there
  (``UiIds.PERCOLATOR_LIMELIGHT``, ``PERCOLATOR_LIMELIGHT_STATUS``).
* **Declared and unused so far**: ``EngineStep.CONVERT_LIMELIGHT`` and
  ``UPLOAD_LIMELIGHT``; ``InputKind.LIMELIGHT_Q_CUTOFF``,
  ``LIMELIGHT_CONVERTER_OPTIONS``, ``LIMELIGHT_CONVERTER_TOOL``,
  ``LIMELIGHT_UPLOAD_TARGET``; ``WorkflowStage.LIMELIGHT_XML``,
  ``LIMELIGHT_UPLOAD``; ``SectionId.LIMELIGHT`` with a "this section arrives
  in phase 12" placeholder in ``SectionArrivals``; ``docs/limelight.rst`` a
  stub; ``docs/developer/traceability.rst`` rows for ``AC-LL-01..06``.
* **Secrets**: ``domain.secrets`` (``SecretRegistry``, ``SecretRedactor``,
  fourteen keywords including ``limelightkey``) and
  ``tools.process.ProcessRedactor`` (argv redacted element by element before
  display). STATUS records that Phase 12 will hold a real credential.
* **The specification does not name an upload mechanism** ("as required by
  the Limelight API"). CasanovoGUI (``scratch/casanovogui`` at ``480b301``)
  uploads by starting ``limelightSubmitImport.jar`` (``yeastrc/limelight-core``
  ``releases/latest``, unpinned) with ``--user-submit-import-key=<key>`` **on
  the command line**, and fetches both JARs on every call. Neither the
  unpinned fetch nor a key on argv is acceptable here.
* **No OS keychain on this host**: no ``secret-tool``, D-Bus or libsecret.
  Only Linux x86-64 can be executed; macOS and Windows are unverified.
* No test-jar is shared between modules; Phase 11 copied its test support
  (``ProjectXvfb``) into ``cometgui-app``.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P12-1 -- one of everything
    One process service (every converter and any upload-helper start goes
    through ``org.cometgui.tools.process``). One installer and manifest: the
    converter is obtained only through the Tool Manager port. One Percolator
    resolver and **one compatible-rerun path** -- Phase 09's
    ``PercolatorRerun`` and ``PercolatorRerunViewModel``; the Limelight
    section offers that action, it never builds a second. One results model.
    One hasher (``HashService``, MD5 and SHA-256 in one pass). One event log
    (``events.log``, new ``ProvenanceEventType`` values added additively, per
    tier 1's Phase 10 ruling that ``provenance.json`` is sealed). One redactor
    (``domain.secrets``).

P12-2 -- where things live
    ``org.cometgui.tools.limelight`` (``cometgui-tools``, already a PIT
    target): the converter command, output validation and schema access,
    readers of what the converter consumes, and the upload client if it runs
    in the JVM. ``org.cometgui.workflow.limelight``: provisioning,
    conversions, uploads, their events. ``ui.viewmodel.limelight`` and
    ``ui.controls.limelight``; the port's implementation in ``cometgui-app``.
    No new module edge without asking the orchestrator.

P12-3 -- the converter's exit status means nothing
    Every conversion is judged by its output: exists, non-empty, readable,
    parses, root element ``limelight_input``, and valid against
    ``limelight-xml.xsd`` read **from the installed, SHA-256-verified JAR**.
    The validator is proven to accept the real known-good output before it
    is trusted to reject. A conversion that fails any check leaves nothing at
    the final path looking complete.

P12-4 -- argument names
    Only from Phase 00's recorded help and a re-run of the pinned JAR's
    ``--help``; a test re-runs the real help and requires every option the
    builder can emit to appear in it.

P12-5 -- conversion is user-initiated
    Like PDV (Phase 11), conversion and upload are started by the scientist
    from the Limelight section on a finished run, not planned as engine steps;
    ``EngineStep.CONVERT_LIMELIGHT``/``UPLOAD_LIMELIGHT`` stay declared. A
    conversion writes under ``<run>/outputs/limelight/`` (a new file per
    conversion, never overwriting) with its log in
    ``<run>/logs/limelight-converter.log``, and records a
    ``limelight.converted`` event: converter version and JAR SHA-256, the
    argument array, the q cutoff and options, the SHA-256 of every input it
    read (``comet.params``, pout XML, each pepXML, FASTA) and the output's MD5
    and SHA-256. Flagged to tier 1 in the handoff.

P12-6 -- the Limelight q cutoff
    Its own value, default 0.01, always passed explicitly as ``-q``, never
    read from or written to the display filters (Phase 10's
    ``DisplayFiltersViewModel``). What ``-q`` changes in the output is
    established by execution, and a test proves the value reaches the file.

P12-7 -- decoys are decided at configuration time
    Decoy import is offered only when the **run's own Percolator output**
    contains decoy PSMs (read from its pout XML) -- which, with Phase 09
    never passing ``-Z`` and Comet's internal decoys absent from the FASTA,
    is never for a CometGUI run today. The control is disabled with that
    explanation, and a conversion request asking for decoy import against
    such a run cannot be made: it is refused before any process starts.

P12-8 -- the pepXML set belongs to the run
    ``-d`` is always the run's ``outputs/comet/``. Before launch, every pepXML
    name the converter will derive from the pout XML's PSM ids exists there
    and is one of the run's recorded Comet outputs with its recorded
    SHA-256; otherwise the conversion is refused naming the file.

P12-9 -- compatibility comes from the run
    Whether a run can convert is read from what **that run's** Percolator
    produced (its recorded execution and its pout XML), never from the
    current selection. Without XML: controls disabled, explanation shown
    before any attempt, and the action from Phase 09's resolution -- the
    compatible rerun labelled with the version resolution returns ("Rerun
    Percolator with 3.07.1 for Limelight", never a literal) when an
    XML-capable build is obtainable here, or local-binary registration with
    "no managed build exists for this platform" when not (``R-LL-02``).

P12-10 -- upload
    Decided after unit 1 (see below). Fixed now: explicit user action every
    time; the exact server and project shown, and confirmed, before anything
    is sent; never automatic; ``<run>/logs/limelight-upload.log``; server-side
    identifiers retained; a ``limelight.uploaded`` event.

P12-11 -- tests never reach a real Limelight server
    The ``D-007`` fake is mandatory, listens on loopback only, and validates
    what it receives against the converter's own XSD. A structural guard
    (decided after unit 1) makes an upload to a non-loopback address from a
    test fail. **Nothing in this phase -- spike included -- contacts a real
    Limelight server.**

P12-12 -- credentials
    Registered with ``SecretRegistry`` at the moment they enter the
    application; never on a process's argument array if any alternative
    exists; never in a file under a project; kept in the OS keychain where
    practical (secret passed on standard input, never argv), otherwise held
    in memory for the session only. Gate item 7 is proved by enumerating
    every file the run, the project and the application wrote (from the
    directories, not from a list) and searching each one's bytes for the
    seeded secret.

P12-13 -- forbidden, always
    Contacting a real Limelight server; an unpinned download; committing
    K562 data or anything derived from it (``D-006``). Analytics or any
    third-party contact found in the converter or uploader is reported to the
    orchestrator **at once** (``D-013`` precedent).

Design decisions from unit 1 (2026-10-11)
=========================================

P12-14 -- the upload is an in-product client (option B)
    ``org.cometgui.tools.limelight`` holds a ``java.net.http`` client of the
    submit-import protocol as ``limelight-core`` v8.3.1 (``61554282``)
    defines it: five XML-over-HTTP POSTs under ``<url>/d/rws/for-page/``
    (auth test, FASTA check only if a FASTA is sent -- it is not, initialise,
    upload file as one raw streamed body with its parameters Base64-encoded
    in a header for server program version >= 16, final submit), the wire
    names as JAXB writes them (``statusFailErrorMessage``, not the Java field
    name). The key never leaves the JVM and never reaches an argument array.
    The importer JAR is **not** a managed tool; no manifest row, no fifth
    ``ToolName``, no specification amendment. The committed wire schema
    ``scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd`` is
    the reference both the client's tests and the fake use.

P12-15 -- what the upload retains and shows
    The server's ``uploadKey`` (the import tracking id) and ``importerSubDir``
    are retained in the ``limelight.uploaded`` event; the project page URL is
    shown as ``<url>/d/pg/project/<id>``. A refusal is shown with the
    server's own flag and ``statusFailErrorMessage``. The project is an
    integer id typed by the user: **no endpoint lists projects for a submit
    key** (``ProjectsList_RestWebserviceController.java:117`` needs a
    logged-in session). The real server validates the XML later, in its
    background importer, so CometGUI validates before sending (P12-3) and the
    fake validates on receipt. The client sends the file's local path, as
    the importer does; whether to send it is unit 5's to argue (it is the
    user's path, not a secret).

P12-16 -- the one fake, and the test guard
    The ``D-007`` fake lives once in ``cometgui-tools`` test sources
    (``org.cometgui.tools.limelight.fake``), validating every request against
    the wire schema and every received Limelight XML against
    ``limelight-xml.xsd`` read from the pinned converter JAR. A module that
    needs it elsewhere (``cometgui-app`` GUI tests) carries a copy **and a
    test that fails if the copy's bytes differ from the original** (Phase 11
    copied ``ProjectXvfb``; the drift guard is new). No test-jar: it is a POM
    change with an unproven reactor interaction, and not needed. The guard
    against a real server: every test JVM that can upload routes HTTP(S)
    through a loopback sinkhole proxy (the spike caught every non-loopback
    attempt over http and https), paired with an architecture rule that
    production code never sets a proxy itself, so the guard cannot be
    bypassed by the product.

P12-17 -- credentials, as the spike found them
    A session store held in memory always works and is the default. Linux
    Secret Service through ``secret-tool`` with the secret on standard input
    where it is installed and a keyring is running (absent on this host, so
    proved only against a scripted stand-in); macOS ``security -i`` with
    commands on standard input (unverified); Windows session-only (the
    foreign-function route is not attempted this phase). Never ``cmdkey``,
    never ``security add-generic-password -w <secret>``.

Escalated from unit 1 (to tier 1, in the handoff): "show live upload **and
import** logs" cannot be met with a submit key -- import status is visible
only in the web application's logged-in session. The product shows the
upload log and the project page URL.

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
     - **Spike: how does an upload reach Limelight, and what must the fake
       be?** From ``yeastrc/limelight-core`` **source** (no real server is
       contacted): the submit-import protocol (endpoints, request and
       response bodies, chunking, how the user key travels, what identifiers
       come back, how a project is chosen, whether projects can be listed
       with a submit key); ``limelightSubmitImport.jar``'s options and
       whether its key can travel other than on argv; every network
       destination and any analytics in the importer **and** the converter.
       A throwaway loopback fake that validates what it receives against
       ``limelight-xml.xsd``, exercised by the **real** importer JAR (pinned
       by SHA-256 under ``scratch/``) if the protocol allows, and by a
       throwaway in-JVM client. Credential feasibility per platform. A
       costed recommendation: in-product client versus managed importer
       JAR. Evidence page and re-runnable script; no product code.
     - R-SEC-03/04 risk; D-007; prerequisite for 5, 7
     - Accepted, ``e13b46d``
   * - 2
     - **The converter adapter** (``tools.limelight``). The command from a
       typed request (P12-4) with the q cutoff always explicit; the output
       judge (P12-3) with the XSD read from the JAR; a pout XML reader
       (XML present, PSM ids, decoy PSM count); the pepXML-set check
       (P12-8). Real converter on the fixture outputs; exit 0 with a missing
       or empty file, no arguments and an unknown option each judged a
       failure; what ``-q`` changes established and pinned.
     - R-LL-03, R-LL-04, R-LL-05; gate 2
     -
   * - 3
     - **Conversion in the workflow** (``workflow.limelight``). Install on
       first use through the Tool Manager port (an empty cache through a
       loopback artefact server); prerequisites (``comet.params``, pepXML,
       pout XML, converter installed and verified, FASTA, writable output)
       each refused by name before launch; the run's q cutoff and options;
       the conversion, its log and its ``limelight.converted`` event
       (P12-5). A real CometGUI run (Comet then Percolator 3.07.1) converted,
       the output validated and its checksums equal to the test's own.
     - R-LL-01 (install), R-LL-04, R-LL-05; gate 1, 2, 5, 8 (model);
       AC-LL-01, 02
     -
   * - 4
     - **Compatibility and the compatible rerun** (P12-9). The run's own
       XML capability; the disabled state and its explanation; the offered
       action from Phase 09's resolution, both variants. A real run with
       3.09, the rerun with 3.07.1 through Phase 09's path, conversion of the
       derived run succeeding; Comet launches unchanged; provenance shows
       both Percolator executions with distinct versions, checksums and
       argv.
     - R-LL-01, R-LL-02; gate 3 (model), 4; AC-LL-03, 04
     -
   * - 5
     - **Upload core** (shape from unit 1). The client; the ``D-007`` fake,
       shared once; the loopback guard (P12-11); credential storage
       (P12-12) and redaction; the upload service, its log, identifiers and
       ``limelight.uploaded`` event. Against the fake: a valid upload
       accepted and its identifiers retained, a malformed one rejected by
       the fake's schema check and reported, a seeded credential absent from
       every file written.
     - R-SEC-03, R-SEC-04; gate 6, 7 (model); AC-LL-06
     -
   * - 6
     - **The Limelight section, conversion half.** View-model, pane, the
       app's port. Compatibility first: disabled controls and the
       explanation before any attempt, the offered action (Phase 09's rerun
       view-model) ; q cutoff (0.01, independent of the display filters);
       output path; decoy import disabled with its reason; independent decoy
       prefix; open-mod; resolved FASTA and pepXML directory read-only;
       Convert; validation shown. GUI tests: gate 3, 5, 8, and a real
       conversion and the compatible rerun through the interface.
     - R-LL-01..05; gate 1, 3, 4, 5, 8 (GUI); AC-LL-02..05
     -
   * - 7
     - **The Limelight section, upload half.** Server and project, the
       credential (masked, never echoed), the destination shown and
       confirmed before sending, live log, identifiers. GUI tests: gate 6
       (only on the explicit action, destination first, the fake) and gate
       7 (a seeded credential, after convert and upload through the
       interface, found in no file under the project, the run, the
       application's logs or any export).
     - R-SEC-03, R-SEC-04; gate 6, 7 (GUI); AC-LL-06
     -
   * - 8
     - **Documentation and traceability.** ``docs/limelight.rst``, a
       developer page, ``docs/citations.rst`` if the importer is used, stale
       statements, traceability for ``AC-LL-01..06``.
     - R-DOC; AC-LL-*
     -
   * - 9
     - **The phase harness** ``scripts/verify-limelight-gates.sh``,
       registered additively as ``limelight``: every gate item graded on a
       production injection; cost stated.
     - all eight gate items
     -

Unit sign-offs
==============

Unit 1 sign-off
---------------

**Accepted 2026-10-11.** Commit ``e13b46d`` (``docs/developer/limelight_upload_spike.rst``,
one toctree line, ``scripts/spikes/limelight_upload_spike.py`` and seven
files beside it). No product code.

* **Diff read.** Nine files; nothing under ``cometgui-*``; no POM, gate
  script or record touched; nothing under ``scratch/`` committed. No real
  Limelight host appears in the script.
* **Re-run by me**: ``. tools/env.sh; python3 scripts/spikes/limelight_upload_spike.py``
  -> ``93 of 93 checks passed in 57.0 s``, exit 0; the last checks show the
  key in only its own file and in the JFR recording of the argv run.
* **My injection** into the fake (a copy, run with ``--fake-source``): the
  received-XML validation ``v.validate(new StreamSource(f.toFile()))`` replaced
  by an empty statement, anchor count 0 after the edit -> exit 1, **13
  FAIL** lines, among them the in-JVM client no longer holding the server's
  refusal for ``missing-attribute.xml`` and ``truncated.xml``.
* **Analytics**: none in the importer v8.3.1 or the converter v2.8.1 (class
  scan and JFR socket events; the converter makes no socket I/O). Nothing to
  escalate as behaviour.
* ``docs`` and ``traceability`` run by the agent (PASS); ``docs-build.sh``
  re-run by me with this entry.
* Decisions P12-14..P12-17 follow from it; the "import logs" question is
  escalated.

Unit 2 sign-off
---------------

Pending.
