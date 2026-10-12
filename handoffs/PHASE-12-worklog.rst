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

Design decision from unit 3 (2026-10-11)
========================================

P12-18 -- the converter reads a copy of the pout XML without the pepXML directory in its ids
    *The orchestrator's call; flagged to tier 1.*
    Every CometGUI run gives Comet an **absolute** ``-N <run>/outputs/comet/<base>``,
    so Comet's PIN ``SpecId`` and Percolator's ``psm_id`` carry that absolute
    path (Phase 00 ran with relative names, which is why nobody saw it).
    Observed by unit 3 with the real tools: with ``-d <run>/outputs/comet``
    the converter looks for ``<dir>/<absolute id>.pep.xml``; with ``-d /`` it
    finds the file and then fails "Unable to find any comet results",
    because it keys Comet's side by file name and Percolator's by the whole
    id. With the directory removed from the ids it converts 1838/1838 PSMs.
    So ``workflow.limelight.PoutForConverter`` requires every id with a
    separator to be exactly ``<pepXML dir>/<name>``, writes a copy with that
    directory string removed under ``<run>/results/limelight/``, reads it back
    through the one pout reader (same ids in order, same decoy count),
    records **both** files' MD5 and SHA-256 in the events (roles
    ``percolator-xml`` and ``percolator-xml-for-converter``), and deletes the
    copy afterwards. The Limelight XML carries no PSM ids, so its content is
    unaffected; its ``conversion_program`` names the copy's path. Rejected
    alternative: a relative Comet ``-N``, which changes Phase 08's run model
    and Phase 11's ``SpectrumReference`` and still leaves existing runs
    needing the copy. Limits: the removal is global (the copy's
    ``<command_line>`` loses the directory too); a run directory containing
    an XML-special character is refused.

Design decisions from unit 5 (2026-10-12)
=========================================

P12-19 -- plain http only to this computer
    The submit key travels inside every request body, so the client refuses
    an ``http://`` server that is not loopback; ``https`` is required
    elsewhere. An http-only Limelight instance is therefore unreachable;
    relaxing it is a one-line change and the owner's to ask for. Flagged to
    tier 1.

P12-20 -- what the client sends and keeps
    The local file path is **not** sent (the real importer sends absolute
    and canonical paths; the server stores them only as nullable tracking
    columns, and they disclose the user's name and directory layout);
    ``submitterSameMachine="false"``. Redirects are never followed. The real
    server's wrong-key message quotes the key's first two characters and is
    passed through as the server's words; the full key is always redacted.

P12-21 -- the process service accepts standard input
    ``ProcessRunner.startWithInput`` (a default method on the port) and
    ``ProcessService`` absorbing a broken pipe on standard input, so the
    Secret Service store passes the secret on stdin. Credentials:
    ``domain.secrets.SessionSecrets.admit`` is the only way to make a
    ``Credential`` and registers it with the redactor first;
    ``CredentialStore`` port in ``domain.secrets``; ``SessionCredentialStore``
    the default; ``tools.credentials.SecretToolCredentialStore`` on Linux
    where ``secret-tool``, a session bus and a clean probe exist (proved only
    against a scripted stand-in); macOS and Windows session-only.

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
     - Accepted after one rework, ``79e2aa7`` ``30b8439``
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
     - Accepted, ``0e38c4f``
   * - 4
     - **Compatibility and the compatible rerun** (P12-9). The run's own
       XML capability; the disabled state and its explanation; the offered
       action from Phase 09's resolution, both variants. A real run with
       3.09, the rerun with 3.07.1 through Phase 09's path, conversion of the
       derived run succeeding; Comet launches unchanged; provenance shows
       both Percolator executions with distinct versions, checksums and
       argv.
     - R-LL-01, R-LL-02; gate 3 (model), 4; AC-LL-03, 04
     - Accepted, ``879efc0``
   * - 5
     - **Upload core** (shape from unit 1). The client; the ``D-007`` fake,
       shared once; the loopback guard (P12-11); credential storage
       (P12-12) and redaction; the upload service, its log, identifiers and
       ``limelight.uploaded`` event. Against the fake: a valid upload
       accepted and its identifiers retained, a malformed one rejected by
       the fake's schema check and reported, a seeded credential absent from
       every file written.
     - R-SEC-03, R-SEC-04; gate 6, 7 (model); AC-LL-06
     - Accepted, ``41edbf4`` ``76dcfdd`` ``692b67b``
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
     - Accepted, ``cfd605a`` ``8958735``
   * - 7
     - **The Limelight section, upload half.** Server and project, the
       credential (masked, never echoed), the destination shown and
       confirmed before sending, live log, identifiers. GUI tests: gate 6
       (only on the explicit action, destination first, the fake) and gate
       7 (a seeded credential, after convert and upload through the
       interface, found in no file under the project, the run, the
       application's logs or any export).
     - R-SEC-03, R-SEC-04; gate 6, 7 (GUI); AC-LL-06
     - Accepted, ``23713c9`` ``81fd7c6`` (+ repair ``48adabc``)
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

**Accepted 2026-10-11 after one rework.** Commits ``79e2aa7`` (11 main and 10
test classes in ``org.cometgui.tools.limelight``) and ``30b8439`` (rework).

* **Rework, sent back by me:** the first commit added ``PercolatorXmlPsms``,
  a second StAX walker of pout XML beside ``tools.percolator.PoutDocument``
  (the parser package-info names that "its one reader"; P12-1). Now
  ``PoutDocument``'s one walk also records PSM ids, the decoy-PSM count,
  id-less PSMs and a DOCTYPE; ``PercolatorXmlPsms`` is a view that adds the
  adapter's refusals; the probe refuses nothing new (``nothingNewIsRefused``).
* **Diff read**, ``LimelightConverter`` in full: refuses an existing output;
  schema from the JAR that runs; the converter writes ``<output>.partial``;
  timeout, non-zero exit, the judge (exists, non-empty, readable,
  well-formed, root ``limelight_input``, XSD) and the q cutoff read back from
  the file each fail and delete the staging file; only then an atomic move.
  No POM, gate script or record touched; no module edge added.
* **Accepted addition to P12-7:** a run **with** decoy PSMs is refused
  without ``--import-decoys`` (re-observed by the agent on
  ``concat.pout.xml``: "Unable to find any comet results for reported
  peptide"), matching Phase 00.
* **What ``-q`` does** (agent, by execution over eleven values): it sets only
  ``default_filter_value`` of Percolator's PSM ``q-value`` annotation; it
  filters nothing (3897 PSMs at every value); no ``-q`` equals ``-q 0.01``;
  NaN/Infinity/text exit 0 writing nothing. ``theCutoffReachesTheFile``
  reads the value back as text.
* **Tests, run by me:** ``mvn -B -o -pl cometgui-tools verify`` -> **1068
  tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
* **My injections** (production code, anchor unique and gone, ``limelight``
  tests, 104 run each time, restored and ``sha256sum -c`` OK):

  - K1 ``LimelightXmlJudge`` root check reduced to ``local.isEmpty()``: 1
    failure, ``LimelightXmlJudgeTest.wrongRoot`` (4 assertions). (My first
    form, ``false && (...)``, was stopped by Checkstyle and never reached the
    tests -- recorded as an injection that did not land.)
  - K2 ``PepXmlSet.PSM_ID`` with one ``_\d+`` group dropped: 7 failures, 9
    errors (``PepXmlSetTest.theNameRule``, ``aChangedPepXmlIsRefused``, six
    real-converter tests).
  - K3 the "decoys present but not imported" refusal made unreachable: 1
    failure, ``LimelightConversionRequestTest.decoysWithoutImportAreRefused``.

* **Targeted PIT, by me** (``limelight.*`` and ``PoutDocument*``, tests
  restricted to the same): 185 mutations, 182 KILLED; the three others are
  ``PoutDocument``'s pre-existing peptide count (2 SURVIVED, 1 NO_COVERAGE),
  which only the probe tests outside my filter exercise -- the agent's run
  including them killed 185/185. **Population:** every compiled
  ``tools.limelight`` class mutated except ``package-info``.
* ``--only quality`` -> PASS (140 s), run by me; archtests 33/33 by the agent.
* **Files outside the module the tests read** (all under ``scratch/``,
  checked by SHA-256): ``phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar``;
  ``scientific-path/comet/`` (``comet.params``, both pepXML),
  ``scientific-path/percolator-3.07.1/percolator.pout.xml``,
  ``scientific-path/limelight/limelight.xml``, ``scientific-path/corrupt/*.xml``,
  ``scientific-path/decoy-chain/`` (``withZ.pout.xml``, ``concat.pout.xml``,
  ``concat_target_decoy.fasta``, ``comet/``); ``fixture/UP000005640_9606.fasta``.
  The root is located by a ``manifests`` directory. No project document is
  read. ``--only tests`` at phase end.
* **Trap recorded** (agent): restoring an injected file with ``mv`` keeps
  the backup's older mtime and the incremental compiler keeps the injected
  class; restore by copy and ``touch``, and check the compiled class.

Unit 3 sign-off
---------------

**Accepted 2026-10-11.** Commit ``0e38c4f`` (30 files: ``org.cometgui.workflow.limelight``
-- ``LimelightConversions`` the one path, ``ConverterLaunch``,
``PoutForConverter``, ``LimelightProvisioning``/``LimelightConverterStatus``,
readiness and refusal types; ``workflow.storage.LimelightSettings*``;
``RunLayout`` limelight paths; three ``ProvenanceEventType`` values;
``RunResultFiles.makeReadOnly``; ``cometgui-app`` ``LimelightFirstUseInstallTest``;
archtest ``LimelightConversionPathRuleTest``).

* **Diff read**: ``ConverterLaunch`` (event ``limelight.converting`` before
  the start, the process service's ``StageRunner`` writing the log through
  ``ProcessRedactor``), ``PoutForConverter`` in full, the prerequisite
  checks. No POM, gate script or record touched; no module edge added.
  Additive enum values only. P12-18 accepted (above).
* **Deviations accepted:** settings live in ``<run>/results/limelight-settings.json``,
  separate from ``view-state.json``; the log is ``logs/limelight-converter.log``
  for the first conversion and ``limelight-converter.<n>.log`` after (the
  ``StageRunner`` never appends), each event naming its own log;
  ``LimelightProvisioning``/``LimelightConverterStatus`` mirror
  ``PdvProvisioning``/``PdvStatus`` (about 330 lines, both over the one
  Tool Manager port -- recorded as residue, not a second installer).
* **Tests, run by me**: ``mvn -B -o -pl cometgui-workflow verify`` -> **878
  tests, 0 failures, 0 skipped** (``RealLimelightConversionTest`` 2 in 19 s:
  real Comet 2026.03.0, Percolator 3.07.1, the real converter twice;
  ``LimelightConversionsTest`` 36); ``cometgui-domain`` 1175 and
  ``cometgui-provenance`` 677 (2 skipped, pre-existing), 0 failures;
  ``cometgui-app`` ``LimelightFirstUseInstallTest`` + ``ToolManagerWiringTest``
  15, 0 failures; ``cometgui-archtests`` 35, 0 failures.
* **My injections** (production code, anchor unique and gone, the 65
  ``workflow.limelight`` tests, restored and ``sha256sum -c`` OK):

  - L1 the converter's reported-version check made unreachable: 1 failure,
    ``aConverterThatIsNotWhatItWasOffered``. (First form called a method
    ``ToolVersion`` lacks; compile error, did not land -- redone.)
  - L3 ``saveSettings``' decoy refusal made unreachable: 1 failure,
    ``gate8DecoyImportAgainstANoDecoyRun``.
  - L2 ``PoutForConverter``'s read-back decoy-count comparison disabled:
    **green** (65 run). The comparison is a defensive re-check that a correct
    prefix removal cannot violate; the id comparison beside it and the
    real test carry the copy. Recorded as residue, not a hole in a gate item.

* **Targeted PIT, by me** (``workflow.limelight.*``,
  ``storage.LimelightSettings*``): 256 mutations, 244 KILLED, 5 TIMED_OUT
  (cancellation and loop mutants that hang), 6 NO_COVERAGE, 1 SURVIVED
  (``pepXmls`` returning an already-empty map -- equivalent). KILLED-only
  95.3 %. Population: every class mutated except ``LimelightPrerequisite``
  (enum) and ``package-info``.
* **Harnesses, run by me**: ``quality`` PASS (141 s), ``provenance`` PASS
  (235 s), ``workflow`` PASS (493 s).
* **Files outside the module the tests read**: ``scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar``
  (workflow and app), ``scratch/scientific-path/limelight/limelight.xml``,
  and everything ``RealPercolatorRunTest`` reads (Comet 2026.03.0 from
  ``scratch/phase05/artefacts``, ``scratch/fixture``, ``scratch/percolator/3.07.1-linux-x86_64``);
  ``manifests/``. No project document.

Unit 4 sign-off
---------------

**Accepted 2026-10-11.** Commit ``879efc0`` (14 files:
``params.percolator.resolution.CompatibilityAction`` with its words in
``ResolutionMessages`` and ``DownstreamStage.purpose()``;
``workflow.limelight.LimelightCompatibility`` and
``LimelightConversions.compatibility``; one additive entry point
``PercolatorRerun.compatibleChoice``).

* **Diff read.** Compatibility is read from the run's ``provenance.json``
  (its Percolator record and the pout XML file record) and Phase 09's
  recorded not-emitted reason; ``check()`` carries the same object. The
  action is ``PercolatorResolver.resolve(offers, {LIMELIGHT_CONVERSION})``:
  available -> the rerun labelled from the resolved build; otherwise
  registration, opening "No managed Percolator build that can write
  XML_OUTPUT exists for this platform" when no managed offer here claims
  it. ``compatibleChoice`` hands Phase 09's ``preview``/``prepare``/``start``
  a ``PercolatorChoice``; no second rerun. No POM, gate script or record
  touched; no module edge.
* **Tests, run by me**: ``cometgui-params-percolator`` install (with tests)
  -> **68, 0 failures**; ``cometgui-workflow`` limelight, rerun and real
  tests -> **120, 0 failures, 0 skipped**, including
  ``RealLimelightCompatibleRerunTest`` (4, 23 s: 3.09 registered local ->
  not convertible, the 3.07.1 rerun offered, conversion of the original
  refused with no launch, the derived run converted to 3285 PSMs = its own
  target rows, Comet launches 2 -> 2, merged PIN SHA-256 and the original's
  ``provenance.json`` unchanged, the two executions differing in version,
  SHA-256 and argv) and ``RealPercolatorRerunTest`` (6).
* **My injections** (production code, anchor unique and gone, restored and
  ``sha256sum -c`` OK):

  - N1 ``CompatibilityAction.forStage`` offering the rerun whenever any
    build is selected (not when the stage is available): 4 failures,
    ``noManagedBuildForThisPlatform``, ``aLocalClaimIsNotAManagedBuild``,
    ``anInferredManagedClaim``, ``aFailedManagedBuild``.
  - N2 the label built from the literal ``3.07.1``: 3 failures,
    ``whateverResolutionReturns`` (3.08.2), ``aFutureBuild``,
    ``aRegisteredLocalBinary``.
  - N3 ``LimelightConversions.convert`` no longer refusing an unready run:
    17 failures in ``LimelightConversionsTest``, 1 in
    ``LimelightCompatibilityTest.noXmlCapability``, and
    ``RealLimelightCompatibleRerunTest`` red.

* **Targeted PIT, by me** (``CompatibilityAction*``, ``ResolutionMessages*``,
  ``DownstreamStage*``): **59/59 KILLED**. The agent's workflow run: 194, 191
  killed, the three others unit 3's already-recorded ones.
* **Harnesses, run by me**: ``quality`` 42 controls PASS (141 s);
  ``workflow`` 110 PASS (496 s); ``percolator`` 109 PASS (736 s).
* **Files outside the modules the tests read** (``scratch/``, SHA-checked):
  ``phase05/artefacts`` (converter JAR, Comet 2026.03.0,
  ``rel-3-07-01__percolator-noxml-ubuntu-portable.zip``), ``fixture``,
  ``percolator/3.09``; ``manifests/``. No project document.
* **Residue recorded**: the rerun's settings are passed by the caller (no
  reader of a run's archived ``percolator-settings.json`` exists); a 3.07.1
  run whose pout XML was deleted still offers the rerun, which Phase 09 then
  refuses as already run; installing an uninstalled resolved build is the
  interface's (unit 6).

Unit 5 sign-off
---------------

**Accepted 2026-10-12.** Commits ``41edbf4`` (55 files), ``76dcfdd``,
``692b67b``. Client ``tools.limelight.LimelightUploadClient`` with
``LimelightServer``, ``SubmitImportWire``, typed outcomes;
``tools.credentials``; ``domain.secrets`` ``Credential``, ``CredentialStore``,
``SessionCredentialStore``, ``SessionSecrets``; ``workflow.limelight.LimelightUploads``
with ``LimelightDestination`` -> single-use ``LimelightUploadConfirmation``;
three event types ``limelight.uploading``/``uploaded``/``unuploaded``; the
fake ``FakeLimelightServer``, ``LoopbackGuard`` (auto-registered JUnit
extension: the test JVM's http(s) proxy is a loopback sinkhole, loopback
exempt, and a test fails if the sinkhole caught anything) and
``FakeCopiesAreIdenticalTest`` in ``cometgui-tools`` with a byte-identical
copy in ``cometgui-workflow``; archtests forbidding a product class to set a
proxy or choose its own, and to use the client outside the upload service.

* **Diff read**: the key is read (``reveal()``) only in the client's request
  bodies/upload header and in the upload service's redactor; no URL, argv
  or ``toString`` carries it; ``.proxy(ProxySelector.getDefault())``. No POM
  (no test-jar), gate script or record touched; no module edge.
  ``junit-platform.properties`` turns on extension auto-registration for the
  two modules' tests.
* **Tests, run by me** (``install`` with tests, nine modules): domain 1180,
  provenance 677 (2 skipped, pre-existing), process 281, tools and the
  other modules through workflow (917) -- **0 failures** in every module;
  archtests **40, 0 failures**.
* **My injections** (production code, anchor unique and gone, restored and
  ``sha256sum -c`` OK):

  - P1 ``LimelightServer`` accepting plain http to any host: 1 failure,
    ``LimelightServerTest.plainHttpOnlyToLoopback`` (146 limelight tests run).
  - P3 the client ignoring the initialise step's refusal: 1 failure,
    ``LimelightUploadClientTest.laterStepsAnswerForThemselves``.
  - P2 ``UploadLog`` writing lines without its own redaction: **green** (15
    upload tests). Every line it receives is already redacted (the outcome
    is redacted before it is logged; the client's progress lines carry no
    secret), so this layer is redundant and untestable from outside.
    Recorded as residue; the leak test's walk of every file still proves
    the gate-7 property.

* **Targeted PIT, by me**: tools (credentials, server, client, wire) 186,
  175 KILLED, 4 TIMED_OUT, 4 SURVIVED (a describe branch, ``Arrays.fill``
  zeroing, a cancellation after a ``secret-tool`` timeout, the stderr cap
  boundary), 3 NO_COVERAGE (interrupt and ``onError`` paths); workflow
  (``LimelightUpload*``, ``LimelightDestination*``) 80, 76 KILLED, 3
  SURVIVED (a progress boundary, ``Writer.flush``/``close``), 1
  NO_COVERAGE. The same as the agent's.
* **Harnesses, run by me**: ``quality`` 42 PASS (141 s), ``provenance`` 24
  PASS (235 s), ``workflow`` 110 PASS (503 s).
* **Files outside the modules the tests read**: tools --
  ``scratch/phase05/artefacts/v2.8.1__cometPercolator2LimelightXML.jar``,
  ``scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar``
  (the real importer against the fake), ``scratch/scientific-path/limelight/limelight.xml``,
  ``scratch/scientific-path/corrupt/*.xml``,
  ``scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd``,
  other modules' ``src/test`` (the drift test), ``/bin/bash`` and
  ``/bin/sleep``; workflow -- the converter JAR, ``scratch/scientific-path``,
  ``cometgui-tools/src/test`` (drift). **The ``tests`` sandbox must carry
  ``scratch/limelight-upload-spike/`` and every module's ``src/test``** --
  checked at the phase-end ``--only tests`` run.

Unit 6 sign-off
---------------

**Accepted 2026-10-12.** Commits ``cfd605a`` (32 files) and ``8958735``.
``ui.viewmodel.limelight`` (``LimelightViewModel``, ``LimelightPort`` and four
records), ``ui.controls.limelight.LimelightPane``, ``cometgui-app``
``ProjectLimelightPort``, ``ProjectRuns`` (moved out of
``ProjectVisualisationPort``), ``RunWiring.limelight``,
``WorkflowRunPort.rerunFrom`` split out of the Percolator section's rerun and
``SessionEngine.rerunCompatible`` (``compatibleChoice`` then ``rerunFrom`` --
still one rerun path); 31 ``UiIds`` (pins 514 -> 545); the accessible-name
walk's floor 117 -> 147 (raised, not lowered); ``SectionArrivals``.

* **Diff read**: the view-model publishes compatibility first and enables
  the settings only when the run's own compatibility is convertible and the
  run has ended; decoy import additionally only when
  ``decoyImportUnavailable`` is empty; the action text is
  ``CompatibilityAction.label()``. The register action calls the existing
  ``PercolatorViewModel.register()`` (the file chooser and Tool Manager
  registration). Test support lives in ``src/test``. No POM, gate script or
  record touched; no module edge (the cutoff crosses the port as text,
  because ``cometgui-ui`` may not depend on ``org.cometgui.tools``).
* **Tests, run by me** (``install`` of ``cometgui-ui`` and ``cometgui-app``
  with tests, 20 min 39 s): ui **735, 0 failures, 0 skipped**; app **279, 0
  failures, 1 skipped** (``UpstreamInstallUiTest``, skipped before this
  unit). Among them ``LimelightConversionUiTest`` 3 (43 s) and
  ``LimelightRerunUiTest`` 4 (42 s), real Comet, Percolator 3.07.1/3.09 and
  the converter.
* **My injections** (``LimelightViewModel``; anchor unique and gone; ui jar
  reinstalled with the injected class -- its hash changed -- before the app
  test; restored, ``sha256sum -c`` OK, reinstalled):

  - Q1 the conversion controls enabled for any run with a readiness answer,
    compatible or not: ``LimelightPaneTest.incompatible`` and
    ``LimelightRerunUiTest.gate3`` red. (Two earlier forms did not land: one
    crashed with a NullPointerException, and in both the ui install was
    stopped by the view-model coverage check, so the app test ran the old
    jar and stayed green -- found by comparing the jar's hash. The install
    now skips ``cometgui.coverage.*`` for the injected build only.)
  - Q2 decoy import enabled whenever the settings are:
    ``LimelightPaneTest.convertible`` and ``LimelightConversionUiTest.aRealRunAndGate8`` red.

* **Targeted PIT, by me** (``ui.viewmodel.limelight.*``, its tests): **396
  mutations, 388 KILLED** -- the agent's eight survivors (seven
  generation-counter increment/decrement mutants, equivalent because the
  counters are only compared for equality; one ``publish()`` after a refused
  install). ``ProjectLimelightPort`` (agent): 34, 31 killed, the rest reached
  only by GUI tests. The PIT run leaves a ``cometgui-ui/target/pit-reports``
  that ``build.sh`` never produces: removed before the phase-end ``tests``
  run.
* **Harnesses**: the agent ran ``quality`` 42, ``shell`` 30, ``paramui`` 87,
  ``results`` 100, ``pdv`` 107, ``percolator`` 109 on ``8958735``, all PASS.
  **I re-run them at unit 7's sign-off**, which changes the same modules, so
  that my run covers the final tree once rather than twice.
* **Files outside the modules the tests read**: ``scratch/fixture``,
  ``scratch/phase05/artefacts`` (Comet 2026.03.0, Percolator 3.07.1 zip and
  ``.deb``, converter JAR), ``scratch/percolator/3.09``,
  ``manifests/tools.json`` (class path), the project JDK under ``tools/``.
* **Residue and escalations**: the rerun uses the Percolator section's
  current settings (as Phase 09's does; no reader of a run's archived
  settings); **``ToolManagerViewModel.refresh()`` starts processes on the
  JavaFX thread** -- pre-existing, and once the converter is installed every
  refresh runs ``java -jar ... --version`` there (escalated, not this
  phase's code); ``ProjectLimelightPort``'s fallback to the requested cutoff
  when the file carries none is unreachable (the judge refuses such a file).

Unit 7 sign-off
---------------

**Accepted 2026-10-12**, with one orchestrator repair. Commits ``23713c9``
(39 files) and ``81fd7c6``; repair ``48adabc``.
``ui.viewmodel.limelight.LimelightUploadViewModel`` with its own
``LimelightUploadPort`` and two records; ``ui.controls.limelight.LimelightUploadPane``
in unit 6's slot through ``ShellView``; ``cometgui-app``
``ProjectLimelightUploadPort`` over ``LimelightUploads`` with one
``SessionSecrets`` from the composition root and ``CredentialStores.forThisComputer``
chosen once, off the FX thread; 30 ``UiIds`` (545 -> 575); the accessible-name
floor 147 -> 174; byte-identical copies of the fake, guard, wire XSD and
extension registration in ``cometgui-ui`` and ``cometgui-app``.

* **Diff read**: Upload calls ``prepare`` only and shows server, project,
  page, file, size and SHA-256; only Send calls the port's ``send``, which
  confirms and refuses a second send; any change withdraws the destination;
  the key is in an unbound ``PasswordField``, handed over once, admitted, and
  the field emptied; the view-model hears only "non-empty". Upload is not
  cancellable once sent (``LimelightUploads.upload`` takes no cancellation;
  the screen says so). Test seams in ``src/test`` (``ParameterEditorApp``
  hides the session bus; the UI driver types ``:``). No POM, gate script or
  record touched; no module edge.
* **Repair ``48adabc`` (mine):** the guard copied into ``cometgui-app`` would
  stop the opt-in ``UpstreamInstallUiTest`` (``-Dcometgui.install.upstream=true``,
  which downloads from GitHub). That test now adds
  ``github.com|*.githubusercontent.com`` to ``http.nonProxyHosts`` while it
  runs, only when opted in, and restores the guard's value. Checked with
  ``ProxySelector`` on the project JDK: GitHub hosts ``DIRECT``;
  ``limelight.yeastrc.org`` and ``notgithub.com`` to the sinkhole. Not run
  opted in (it downloads 123 MB from GitHub); tier 1's nightly runs it.
* **Tests, run by me** (``install`` of ``cometgui-ui`` and ``cometgui-app``
  with tests, after the repair, 22 min 38 s): ui **781, 0 failures, 0
  skipped**; app **296, 0 failures, 1 skipped** (``UpstreamInstallUiTest``'s
  opt-in test). ``LimelightUploadUiTest`` 4 (59 s), ``ProjectLimelightUploadPortTest``
  11, ``LimelightUploadViewModelTest`` 38, ``LimelightUploadPaneTest`` 6;
  ``FakeCopiesAreIdenticalTest`` (tools) 2, 0 failures.
* **Gate 7 walk** (agent's run, reported): seeded ``CGSEED-`` + 48 random
  characters; after a refused and two successful uploads (one with the kept
  key) and two Results exports: project 15 dirs/32 files, tool cache 10/3,
  the test's temp root 3/5, and every file written since the start under
  ``/tmp`` (2) and the home directory (0) -- no match in any form, the
  window's >1000 texts and the console clean; the control finds every form
  planted.
* **My injections** (restored, ``sha256sum -c`` OK; the ui jar reinstalled
  with the injected class, its hash changed):

  - R1 a changed server no longer withdrawing the shown destination:
    ``LimelightUploadViewModelTest.aChangeWithdrawsTheDestination`` red
    (the GUI test changes the project, not the server, so it stays green).
  - R2 the port's single-use check always passing: ``ProjectLimelightUploadPortTest.prepareThenSend``
    red. (First form stopped by Checkstyle; did not land.)

* **Targeted PIT, by me** (``LimelightUpload*`` view-model, its tests):
  **308, 301 KILLED**, the seven generation-counter survivors the agent
  argued (one, the files counter, could apply a stale list if a run is
  deselected and reselected during one read -- recorded as residue).
  ``ProjectLimelightUploadPort`` 36/36 (agent).
* **Harnesses, run by me on ``48adabc``** (covering units 6 and 7 and the
  repair): ``quality`` 42 (141 s), ``shell`` 30 (512 s), ``results`` 100
  (656 s), ``pdv`` 107 (888 s), ``percolator`` 109 (802 s), ``paramui`` 87
  (2083 s) -- **all PASS**. (The agent saw ``paramui`` fail its baseline once
  in ``VariableModificationEditorUiTest``, the flaky test STATUS already
  lists; passed on its re-run and on mine.)
* **Files outside the modules the tests read**: every module's ``src/test``
  (the drift test, ui and app); ``scratch/fixture``, ``scratch/phase05/artefacts``,
  ``scratch/scientific-path/limelight/limelight.xml``, ``manifests/``; the
  gate-7 walk reads the JVM temp directory and the home directory, files
  written since the class started only.
* **Residue and escalations**: no cancel once sent; the rest of the
  application redacts with ``SecretRedactor.patternsOnly()`` and does not
  know a typed key (no other writer receives it today); the files-counter
  equivalent-mutant caveat above.

Unit 8 sign-off
---------------

Pending.
