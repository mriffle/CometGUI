.. _dev-limelight-integration:

=====================
Limelight integration
=====================

How CometGUI converts a finished run to Limelight XML and uploads it to a
Limelight server -- **as built** in Phase 12. In one paragraph: the converter
(``limelight-import-comet-percolator`` v2.8.1) is installed only by the Tool
Manager and started only through
``org.cometgui.workflow.limelight.LimelightConversions``, which checks every
prerequisite by name, writes a ``limelight.converting`` event before the
process starts, ignores the converter's exit status as evidence of success and
keeps the output only when it validates against the schema inside the
installed JAR. Whether a run can convert is read from that run's own record;
when it cannot, the offered action is computed from Phase 09's resolver. The
upload is an in-JVM client of ``limelight-core``'s submit-import protocol,
reachable only through ``LimelightUploads``, which makes the destination a
value the interface must show and confirm before anything is sent. The submit
key enters the application once, through ``SessionSecrets.admit``, and never
reaches an argument array, a URL, a log or a record.

.. note::

   **State after Phase 12 -- Limelight Conversion and Upload**, written on
   2026-10-12 by work unit 8 against the code at ``c5a8cdd``. The user-facing
   description is :doc:`/limelight`; the spike that decided the upload's shape,
   with ``limelight-core``'s source quoted, is :doc:`limelight_upload_spike`.
   Design decisions cited as ``P12-n`` are recorded, with each work unit's
   sign-off, in ``handoffs/PHASE-12-worklog.rst``. **Everything here has run
   on Linux x86-64 only** (see `What has only run on Linux`_), and **no test
   has contacted a real Limelight server**. The phase's falsifiability harness
   (work unit 9) is not described here; it did not exist when this page was
   written.

.. contents:: Contents
   :depth: 2
   :local:

Packages and classes
====================

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Package (module)
     - What it holds

   * - ``org.cometgui.tools.limelight`` (``cometgui-tools``)
     - The converter adapter. ``LimelightConverterIdentity`` (Phase 05: starts
       the JAR with ``--version``). ``PercolatorXmlPsms`` -- a strict view of
       what the converter will read from a pout XML (PSM ids, decoy PSM
       count), over ``tools.percolator.PoutDocument``, the one pout reader,
       whose single walk Phase 12 extended to record PSM ids, decoy PSMs,
       id-less PSMs and a DOCTYPE. ``PepXmlSet`` -- every pepXML name the
       converter will derive from the PSM ids, each proved the run's own.
       ``LimelightConversionRequest`` (typed; cannot be built with a decoy
       choice its pout XML contradicts), ``LimelightQValueCutoff``,
       ``LimelightConverterCommand`` (the argument array),
       ``LimelightConverter`` (runs it and keeps the output only if judged
       valid), ``LimelightXmlJudge`` with ``LimelightXmlSummary``,
       ``LimelightSchema`` (the XSD read from the JAR), ``LimelightConversion``
       and ``LimelightConversionException``. The upload client:
       ``LimelightUploadClient``, ``LimelightServer`` (the checked, normalised
       address), ``SubmitImportWire`` (the protocol's XML, hardened),
       ``LimelightUploadRequest``, ``LimelightUploadStep``,
       ``LimelightUploadOutcome``, ``LimelightUploadListener``. A PIT target.

   * - ``org.cometgui.tools.credentials`` (``cometgui-tools``)
     - ``CredentialStores.forThisComputer`` -- chooses the store and says why;
       ``SecretToolCredentialStore`` -- the Linux Secret Service through
       ``secret-tool``, the secret on standard input.

   * - ``org.cometgui.domain.secrets`` (``cometgui-domain``)
     - Added beside the Phase 04 redactor: ``Credential`` (prints nothing of
       itself), ``SessionSecrets`` (``admit`` is the only way to make a
       ``Credential``, and registers the value with the session's redactor
       first), the ``CredentialStore`` port and ``SessionCredentialStore``
       (in memory, always available).

   * - ``org.cometgui.domain.ports`` (``cometgui-domain``), ``org.cometgui.tools.process`` (``cometgui-process``)
     - ``ProcessRunner.startWithInput`` (a default method) and
       ``ProcessService`` absorbing a broken pipe on standard input (P12-21),
       so a secret can be handed to a process without an argument.

   * - ``org.cometgui.domain.run`` (``cometgui-domain``)
     - ``RunLayout``: ``outputs/limelight/``, ``results/limelight/`` (the
       converter's pout copy, while it runs) and
       ``results/limelight-settings.json``.

   * - ``org.cometgui.params.percolator.resolution``
       (``cometgui-params-percolator``)
     - ``CompatibilityAction`` (with ``Kind`` ``RERUN_PERCOLATOR`` and
       ``REGISTER_LOCAL_BINARY``), its sentences in ``ResolutionMessages``,
       and ``DownstreamStage.purpose()`` ("Limelight", for the label). See
       :ref:`dev-version-capabilities-compatibility-action`.

   * - ``org.cometgui.workflow.limelight`` (``cometgui-workflow``)
     - ``LimelightProvisioning`` and ``LimelightConverterStatus`` -- the
       converter through the one ``ToolManager`` port; ``LimelightConverterInstallation``.
       ``LimelightConversions`` -- **the one conversion path** (``check``,
       ``convert``, ``compatibility``, ``decoyImportUnavailable``,
       ``saveSettings``), with ``LimelightReadiness``, ``LimelightRefusal``,
       ``LimelightPrerequisite``, ``LimelightConversionRefusedException``,
       ``LimelightConverted`` and ``LimelightCompatibility``.
       ``ConverterLaunch`` -- the runner that records before it starts.
       ``PoutForConverter`` (P12-18). ``LimelightUploads`` -- **the one
       upload path** -- with ``LimelightDestination``,
       ``LimelightUploadConfirmation`` (single use), ``LimelightUploadAttempt``,
       ``LimelightUploadWatcher`` and ``LimelightUploadRefusedException``.

   * - ``org.cometgui.workflow.storage`` (``cometgui-workflow``)
     - ``LimelightSettings``, ``LimelightSettingsJson`` (schema version 1) and
       ``LimelightSettingsStore``: a run's cutoff and options, in their own
       file, apart from the display filters' ``view-state.json``.

   * - ``org.cometgui.workflow.steps`` (``cometgui-workflow``)
     - ``PercolatorRerun.compatibleChoice`` -- one additive entry point that
       hands Phase 09's ``preview``/``prepare``/``start`` a
       ``PercolatorChoice``; no second rerun. ``RunResultFiles.makeReadOnly``
       applied to each kept Limelight XML.

   * - ``org.cometgui.provenance.events`` (``cometgui-provenance``)
     - Six ``ProvenanceEventType`` values, added additively (`The events`_).

   * - ``org.cometgui.ui.viewmodel.limelight``,
       ``org.cometgui.ui.controls.limelight`` (``cometgui-ui``)
     - ``LimelightViewModel`` behind ``LimelightPort`` (records
       ``LimelightOptions``, ``LimelightRunState``, ``LimelightResult``,
       ``LimelightRerun``); ``LimelightUploadViewModel`` behind
       ``LimelightUploadPort`` (``LimelightUploadPreview``,
       ``LimelightUploaded``); ``LimelightPane`` and ``LimelightUploadPane``,
       which ``ShellView`` places in the pane's upload slot. Toolkit-free
       view-models that use the Results section's one run selection. The
       cutoff crosses the port as text, because ``cometgui-ui`` may not depend
       on ``org.cometgui.tools``.

   * - ``org.cometgui.app.config`` (``cometgui-app``)
     - ``ProjectLimelightPort`` (over the Tool Manager, the session's project,
       the session engine and ``LimelightConversions``),
       ``ProjectLimelightUploadPort`` (over ``LimelightUploads``, with one
       ``SessionSecrets`` from the composition root and the store chosen once
       by ``CredentialStores.forThisComputer``, off the JavaFX thread),
       ``ProjectRuns`` (moved out of ``ProjectVisualisationPort`` to be
       shared), ``RunWiring.limelight``, ``WorkflowRunPort.rerunFrom`` (split
       out of the Percolator section's rerun) and
       ``SessionEngine.rerunCompatible`` (``compatibleChoice`` then
       ``rerunFrom`` -- still one rerun path).

**No module edge was added.** **What Phase 12 did not use.**
``EngineStep.CONVERT_LIMELIGHT`` and ``UPLOAD_LIMELIGHT`` (declared since
Phase 08) are still declared and are never planned: conversion and upload are
started by the scientist from a finished run, as PDV is (P12-5;
:doc:`pdv_integration`). The ``InputKind`` values ``LIMELIGHT_Q_CUTOFF``,
``LIMELIGHT_CONVERTER_OPTIONS``, ``LIMELIGHT_CONVERTER_TOOL`` and
``LIMELIGHT_UPLOAD_TARGET`` are likewise unused. The importer JAR
``limelightSubmitImport.jar`` is **not** a managed tool: no manifest row and
no ``ToolName`` (P12-14).

The one conversion path
=======================

``LimelightConversions.check`` answers "can this run convert now, and if not,
why?" -- every unmet ``LimelightPrerequisite`` at once, in order: the run not
executing; ``comet.params`` present and equal to the SHA-256 the run
recorded; the Percolator XML present, recorded complete in
``provenance.json`` and unchanged; the recorded Comet pepXML present and
unchanged, and every pepXML the converter will derive from the PSM ids among
them (``R-LL-03``); the converter installed and verified; the FASTA where the
run recorded it and unchanged; a new output in a writable directory; and the
decoy choice agreeing with the run's own Percolator output (P12-7). It reads
and hashes, and starts nothing. ``convert`` asks the same question first.

``convert``, in order:

#. The pout XML is given as it is, or -- when its PSM ids carry a directory,
   as every CometGUI run's do -- as the verified copy of `The pout copy
   (P12-18)`_.
#. ``PepXmlSet`` proves every pepXML the converter will open is the run's
   own. ``-d`` is the ``outputs/comet/`` of the run that searched (the source
   run, for a Percolator rerun: ``LimelightConversions.searchedRun``).
#. The installed converter is started with ``--version`` and must report the
   version the Tool Manager offered; its JAR is hashed with the one hasher.
#. ``LimelightConversionRequest`` is built and ``LimelightConverter`` runs it
   on the Java runtime CometGUI itself runs on
   (``JavaRuntime.ofThisApplication()``), through a ``ConverterLaunch``, as a
   logged stage under a 30-minute timeout, in the JAR's directory, with an
   environment of exactly ``LANG=C.UTF-8``. ``-d``, ``-f`` and ``-q`` are
   **always** passed, so the converter infers nothing; argument order is
   fixed (``-c -p -d -f -o -q``, then switches); the free-text prefix is
   attached with ``=``.
#. The output is judged (`Judging the output`_), hashed (MD5 and SHA-256, one
   pass), made read-only and recorded, or deleted and recorded as a failure.

**Where.** Unless a path is named, ``<run>/outputs/limelight/limelight-<n>.xml``,
``n`` one more than any used before; a named path must be absolute, must not
exist and must be in a writable directory. The converter writes
``<output>.partial`` and only a judged file is moved into place atomically.
Logs are ``logs/limelight-converter.log``, then ``limelight-converter.1.log``
... (the process service's ``StageRunner`` never appends), each event naming
its own log.

**What keeps it the only path.** ``LimelightConversionPathRuleTest`` in
``cometgui-archtests``: only ``org.cometgui.workflow.limelight`` may construct
``LimelightConverter``. Graded against the product and against a fixture
(``ConstructsLimelightConverter``) that the same rule object must reject.
Elsewhere, the converter could be handed the application's own process service
and convert a run with nothing checked and nothing recorded. Process creation
remains confined to the process service (``ProcessCreationRule``).

Judging the output
------------------

``R-LL-04`` asks that exit status be "necessary but not sufficient". The
pinned v2.8.1 converter makes it worth less than that: it exits **0** with no
arguments, on an unknown option and on ``-q NaN``, each time printing its usage
and writing nothing, and prints ``Error printing runtime information.`` on
every run. So a timeout or a non-zero exit fails; exit 0 is never the reason a
file passes. ``LimelightXmlJudge`` reads the file: exists and regular; not
empty; readable; well-formed with no DOCTYPE (hardened StAX); document element
``limelight_input`` in no namespace; valid against ``limelight-xml.xsd``.
``LimelightSchema`` reads that XSD from the JAR the conversion ran (the JAR-root
entry, 65,905 bytes, SHA-256 ``e8c1b920...`` in v2.8.1, no target namespace --
the schema the converter validates its own output against), with secure
processing on and every external access refused. The converter then must
have written the cutoff asked for. The judge was proved against the real
known-good output (Phase 00's ``limelight.xml`` and a fresh conversion) before
it was trusted to reject.

What ``-q`` does
----------------

Established by running the pinned converter on the K562 fixture with no
``-q`` and with ``-q`` 0.01, 0.05, 0, 1, 0.001, -0.5 and 2
(``LimelightQValueCutoff``'s Javadoc):

* it changes exactly one value: ``default_filter_value`` of the Percolator
  search program's PSM-level ``filterable_psm_annotation_type name="q-value"``;
* **it filters nothing** -- 3,897 ``psm`` and 2,985 ``reported_peptide`` at
  every value; the reported-peptide default stays 0.01;
* no ``-q`` equals ``-q 0.01``; the converter takes any number (-0.5 and 2 were
  written as given) and rejects ``NaN``, ``Infinity`` and text with exit 0 and
  no file.

So the [0, 1] range is the product's (``LimelightQValueCutoff``); the cutoff
is always passed explicitly (P12-6); it is never read from or written to the
display filters; and ``LimelightConverter`` checks it was written. The judge
reads the value back (``output.q-cutoff``).
``LimelightConverterRealTest.theCutoffReachesTheFile`` pins it against the
real JAR.

The decoy chain
---------------

``R-LL-05``, as Phase 00 and unit 2 observed it with the real tools:
Limelight's ``--import-decoys`` needs Percolator's ``-Z``, which needs ``-X``;
``--import-decoys`` against a run without ``-Z`` exits 0 and silently imports
nothing; a ``-Z`` run converted **without** ``--import-decoys`` fails ("Unable
to find any comet results for reported peptide"); and decoy import cannot work
with Comet's internal decoys, which are not FASTA entries. Phase 09 never
passes ``-Z``, so **no CometGUI run today has decoy PSMs in its pout XML**,
and decoy import is never offered.

The rule is one rule, in ``LimelightConversionRequest``: the decoy choice must
agree with the decoy PSMs the pout XML actually holds, in both directions, or
the request cannot be constructed. ``decoyImportUnavailable`` is the same rule
for the control the interface disables before anyone asks, and
``LimelightConversions.saveSettings`` refuses to save a decoy import the run
cannot honour -- at configuration time (gate item 8). The real decoy import
is exercised only by ``LimelightConverterRealTest.aRealDecoyImport``, on
Phase 00's ``decoy-chain`` run over a concatenated target-decoy FASTA.

The pout copy (P12-18)
----------------------

Every CometGUI run passes Comet an **absolute** ``-N <run>/outputs/comet/<base>``,
so the PIN ``SpecId`` and Percolator's ``psm_id`` carry that absolute path
(Phase 00 used relative names, which hid it). The v2.8.1 converter cannot read
such ids: it opens ``new File(<-d>, <id's file part> + ".pep.xml")``, which
concatenates an absolute child onto the directory; with ``-d /`` it finds the
file and then fails "Unable to find any comet results", because it keys
Comet's side by file name and Percolator's by the whole id.

So ``PoutForConverter`` requires every id with a separator to be exactly
``<pepXML dir>/<name>`` (otherwise it refuses, naming the id), writes a copy
under ``<run>/results/limelight/`` with that directory string removed, reads
the copy back through the one pout reader (same ids in order, same decoy
count), records **both** files' MD5 and SHA-256 (roles ``percolator-xml`` and
``percolator-xml-for-converter``) and deletes the copy afterwards. The
Limelight XML carries no PSM ids, so its content is unaffected; its
``conversion_program`` names the copy's path. Limits: the removal is global
(the copy's ``<command_line>`` loses the directory too), and a run directory
containing an XML-special character is refused. The rejected alternative, a
relative Comet ``-N``, would change Phase 08's run model and Phase 11's
``SpectrumReference`` and still leave existing runs needing the copy.

Compatibility and the offered action
====================================

``LimelightConversions.compatibility`` (and ``check``, which carries the same
``LimelightCompatibility``) reads **that run's** Percolator record in
``provenance.json`` and its pout XML file record -- never the Percolator
selected now (P12-9). The explanation reuses Phase 09's recorded
``percolator.not-emitted`` reason for ``-X`` when there is one, otherwise the
recorded capabilities. When the pout XML is the reason, ``action`` is
``CompatibilityAction.forStage(offers, LIMELIGHT_CONVERSION)``:
``PercolatorResolver.resolve`` over the Tool Manager's current offers with
only that stage enabled. Available -> ``RERUN_PERCOLATOR``, labelled by
``ResolutionMessages.rerunLabel`` from the resolved build ("Rerun Percolator
with 3.07.1 for Limelight" today; a test resolves a hypothetical 3.08.2 and an
unreleased 3.10 to prove the label is never a literal). Not available ->
``REGISTER_LOCAL_BINARY`` and no rerun, opening with "No managed Percolator
build that can write XML_OUTPUT exists for this platform" when no managed
offer here claims the capability (``R-LL-02``), or with the resolver's reason
when one claims it but is unusable or unobserved.

The rerun is Phase 09's: ``SessionEngine.rerunCompatible`` calls
``PercolatorRerun.compatibleChoice`` and then ``WorkflowRunPort.rerunFrom``,
the same rerun the Percolator section starts. It reuses the merged PIN after
checking its recorded SHA-256, launches no Comet, and leaves the original run
byte for byte unchanged; the derived run records ``derivedFrom``. The
registration action calls the Percolator section's existing
``PercolatorViewModel.register()``.

The upload
==========

The protocol client (P12-14, P12-15, P12-19, P12-20)
-----------------------------------------------------

``LimelightUploadClient`` speaks the submit-import protocol of
``limelight-core`` v8.3.1 (commit ``61554282``) over ``java.net.http``, in the
JVM: authentication test (checks key and project, learns the server's program
version), initialise (returns ``uploadKey``, the import tracking id), the file
as one raw streamed body with its parameters in a header -- Base64 for a
server at program version 16 or later, raw single-line XML for an older one --
and final submit (returns ``importerSubDir``). No FASTA is sent, so the FASTA
check is never called. The wire names are JAXB's (``statusFailErrorMessage``,
not the Java field name); ``SubmitImportWire`` writes and reads them, and the
committed ``scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd``
is what the tests and the fake validate against.

* **The key** is in each request body's ``userSubmitImportProgramKey`` and
  in the file's parameters header -- nowhere else: never a URL, never an
  argument array (nothing here starts a process), never an exception message,
  an outcome or a ``toString``.
* **The address** (``LimelightServer``): an absolute http(s) URL with a host;
  no user-info, query or fragment; **plain http only to loopback**, because the
  key is in every body (an http-only Limelight is therefore unreachable;
  relaxing that is the owner's call). Scheme and host lower-cased, trailing
  slashes removed. The project page is ``<address>/d/pg/project/<id>``.
* **Not sent**: the file's absolute and canonical local paths, which the
  real importer sends; ``submitterSameMachine="false"``.
* **Answers**: HTTP 200, or 400 with an XML body, is the protocol's answer;
  ``statusSuccess="true"`` proceeds, anything else is ``Refused`` with the
  server's flags and message as sent. Any other status, **a redirect (never
  followed)**, an empty or malformed body, a refused or cut connection or a
  timeout is ``Failed``. Timeouts: connect 30 s, each XML call 2 min, the
  file call 6 h.
* **Proxies**: the client is built with ``ProxySelector.getDefault()``
  explicitly, so a system proxy is honoured and the test guard below catches
  everything.

There is **no endpoint that lists projects for a submit key**
(``ProjectsList_RestWebserviceController.java:117`` needs a logged-in session),
so the project is an integer the scientist types; and the server's import log
is not reachable with a submit key either, so the product shows the upload
log and the project page, not the import's progress. The real server
validates the XML later, in its background importer; CometGUI validates
before sending (only a judged, recorded conversion can be a destination).

The one upload path
-------------------

``LimelightUploads.destination`` checks the server, the project id, that the
run has ended, that the file is one of the run's own conversions (named by a
``limelight.converted`` event) and that its SHA-256, hashed now, is still the
recorded one; it sends and writes nothing. The ``LimelightDestination`` it
returns is what the interface shows; only ``LimelightDestination.confirm()``
makes a ``LimelightUploadConfirmation``, and ``upload`` uses it up, checking
again that the run is not executing and the file unchanged before any
connection. One confirmation, one upload. ``upload`` takes no cancellation:
stopping part-way would leave no record of how it ended.

In the interface, Upload calls ``prepare`` only; Send calls the port's
``send``; any change to the file, server, project or key, or another run,
withdraws the shown destination; ``ProjectLimelightUploadPort`` refuses a
second send of one confirmation.

``LimelightUploadRulesTest`` (four rules, each graded against the product and
a committed fixture with the same rule object): only
``org.cometgui.workflow.limelight`` calls the client; only the service and the
interface (``org.cometgui.ui``, ``org.cometgui.app``) start an upload --
nothing in the engine can; no product class calls ``System.setProperty``,
``ProxySelector.setDefault`` or ``ProxySelector.of``, uses ``Proxy.NO_PROXY``,
constructs a ``Proxy`` or subclasses ``ProxySelector``; and the one class that
gives an HTTP client a proxy selector is the upload client, giving it the
default.

Credentials (P12-12, P12-17, P12-21)
------------------------------------

The key is typed into an unbound ``PasswordField``, handed to the view-model
once when the field is committed, admitted at once through
``SessionSecrets.admit`` -- which registers it with the session's redactor
before it returns a ``Credential`` -- and the field is emptied; the
view-model hears only "the field is non-empty". It is used for the next
upload and dropped, unless "Remember the key" is ticked and the server queued
the import; then the port keeps it per server in the store
``CredentialStores.forThisComputer`` chose:

* **Linux**: ``SecretToolCredentialStore`` when ``secret-tool`` is on
  ``PATH``, ``DBUS_SESSION_BUS_ADDRESS`` is set and a probe lookup answers.
  ``store`` reads the secret from standard input (``startWithInput``); the
  argument array holds the label and the attributes ``service cometgui`` and
  ``account <server URL>``. ``lookup``'s output goes to the store's own
  listener, which admits it first, and to no stage runner or log. **Proved
  only against a scripted stand-in**
  (``cometgui-tools/src/test/resources/org/cometgui/tools/credentials/secret-tool-stand-in.sh``);
  this host has no Secret Service. Otherwise the session store, with the
  reason shown.
* **macOS and Windows**: the session store. ``security -i`` (commands on
  standard input), which P12-17 named for macOS, is **not** implemented:
  quoting a secret inside that command language could not be tested without a
  Mac. ``cmdkey`` would put the secret on argv; the Credential Manager API
  needs untested native access.

``LimelightUploads`` also adds the key to the redactor it writes with, so a
server message that quotes it is written ``[REDACTED]``
(``LimelightUploadsTest.theServersWordsAreRedacted``). The real server's
wrong-key message quotes the key's first two characters; that is passed
through as the server's words.

The events
==========

Six ``ProvenanceEventType`` values, appended to the run's
``provenance/events.log`` after ``provenance.json`` is sealed (tier 1's Phase
10 ruling; P12-5). Each carries ``run.id``. Payload keys are constants of
``LimelightConversions`` and ``LimelightUploads``; every value also passes the
event log's own redaction. The full list is in
:ref:`ref-provenance-format-event-types`.

.. list-table::
   :header-rows: 1
   :widths: 24 76

   * - Type
     - When, and what it adds

   * - ``limelight.converting``
     - Before the converter process starts. ``tool`` (``limelight-converter``),
       ``tool.version`` (as the converter reports it),
       ``converter.jar.path``/``.md5``/``.sha256``, ``converter.java``,
       ``argv.count`` and ``argv.0`` .. (after ``SecretRedactor.redactArgv``),
       ``process.working-directory``, ``process.environment``,
       ``limelight.q-cutoff``, ``limelight.import-decoys``,
       ``limelight.independent-decoy-prefix`` (when set),
       ``limelight.open-mod``, ``input.count`` and per input
       ``input.<n>.role`` (``comet-params``, ``percolator-xml``,
       ``percolator-xml-for-converter``, ``pepxml``, ``fasta``), ``.path``,
       ``.md5``, ``.sha256``; ``output.path``.

   * - ``limelight.converted``
     - The above plus ``output.size``, ``output.md5``, ``output.sha256``,
       ``output.psms``, ``output.reported-peptides``, ``output.q-cutoff`` (read
       back from the file), ``process.exit-code``, ``log.path``, ``log.md5``,
       ``log.sha256``.

   * - ``limelight.unconverted``
     - The ``converting`` keys plus ``limelight.failure`` (a lower-case
       hyphenated name; ``could-not-run`` when the process could not start),
       ``message``, and the log's three keys when there is a log. No output
       checksum.

   * - ``limelight.uploading``
     - Before the first byte. ``limelight.server``,
       ``limelight.project-id``, ``limelight.project-page``,
       ``upload.file.path``, ``.size``, ``.md5``, ``.sha256``.

   * - ``limelight.uploaded``
     - The above plus ``limelight.upload-key`` (the tracking id),
       ``limelight.importer-sub-dir``, ``limelight.server-program-version``,
       ``upload.bytes-sent``, and the upload log's ``log.path``, ``log.md5``,
       ``log.sha256``.

   * - ``limelight.unuploaded``
     - The ``uploading`` keys plus ``upload.step``, ``upload.failure``
       (``refused-by-server`` with ``upload.http-status``,
       ``limelight.server-flags`` and ``limelight.server-message``; or
       ``no-answer`` with ``message``), and the log's three keys.

**The key is in none of them.** The upload log is
``logs/limelight-upload.log``, then ``limelight-upload.1.log`` ..., every line
through the one redactor and, live, to a ``LimelightUploadWatcher``.

That the conversion is "checksummed into provenance" (gate item 1,
``R-LL-04``) is met by these events, not by ``provenance.json``: P12-5,
flagged to tier 1.

The fake, the guard and the drift test
======================================

**The fake** (``D-007``; P12-16) is
``org.cometgui.tools.limelight.fake.FakeLimelightServer`` in
``cometgui-tools``' test sources: the server side of the v8.3.1 protocol on
``127.0.0.1``, an ephemeral port, refusing non-loopback peers, writing nothing
to disk. Every request, parameters header and answer is validated against the
wire schema; every received Limelight XML is checked for its root and
validated against ``limelight-xml.xsd`` read from the pinned converter JAR,
both held to their SHA-256. It recomputes the SHA-256 of what it receives and
refuses a wrong key or project with the server's own flags and messages (copied
from ``limelight-core``'s submit-key access check). Unlike the real server it
refuses an invalid file **at upload** (HTTP 400,
``limelightXMLFileFailsInitialParse``), so a test can see it. It is credible
because the **real** importer JAR, pinned by SHA-256 under
``scratch/limelight-upload-spike/``, uploads to it and is accepted, and is
refused on a corrupt file (``FakeLimelightServerTest``). It can be told to
refuse every key, refuse the project, cut the connection mid-file, or claim
another server version.

**The guard** (P12-11, P12-16) is ``LoopbackGuard``, a JUnit Jupiter extension
registered through ``META-INF/services/org.junit.jupiter.api.extension.Extension``
with ``junit.jupiter.extensions.autodetection.enabled=true`` in
``junit-platform.properties`` -- no POM change. Constructed before the first
test, it starts a loopback **sinkhole** and makes it the whole JVM's
``http.proxyHost``/``https.proxyHost`` with loopback-only
``http.nonProxyHosts``; the JDK's default ``ProxySelector`` reads those per
connection, and the upload client uses exactly that selector. Plain http to a
real host lands in the sinkhole as ``POST http://host/...``, https as
``CONNECT host:443``, a name before it is resolved; the sinkhole answers 403.
After each test, anything caught that the test did not claim fails it
(``LoopbackGuardTest``). The architecture rule above is the other half: the
product cannot route around the guard. The one opt-in test that downloads from
GitHub, ``UpstreamInstallUiTest`` with ``-Dcometgui.install.upstream=true``,
adds ``github.com|*.githubusercontent.com`` to ``http.nonProxyHosts`` while it
runs and restores it (orchestrator repair ``48adabc``).

**Four modules carry it byte-identically.** No test-jar (a POM change with an
unproven reactor interaction): ``cometgui-workflow``, ``cometgui-ui`` and
``cometgui-app`` each carry copies of six files from ``cometgui-tools`` at the
same paths -- ``FakeLimelightServer.java``, ``LoopbackGuard.java``,
``FakeCopiesAreIdenticalTest.java``, ``submit-import-wire-v16.xsd``, the
extension registration and ``junit-platform.properties``.
``FakeCopiesAreIdenticalTest`` (``everyCopyIsTheOriginal``, with
``theComparisonCanFail`` as its control), wherever it runs, finds every module
holding the package and compares each copied file's bytes with the original,
so a copy edited anywhere fails the build in every module that has one. **To
change the fake, change it in** ``cometgui-tools`` **and copy all six files to
the other three modules.** The test reads other modules' ``src/test``, which
the ``tests`` harness's sandbox must carry.

Test support and evidence
=========================

**Files the tests read outside their module** (relevant to the ``tests``
harness sandbox): ``scratch/phase05/artefacts/`` (the converter JAR, Comet
2026.03.0, the Percolator 3.07.1 zip and ``.deb``),
``scratch/limelight-upload-spike/artefacts/v8.3.1__limelightSubmitImport.jar``,
``scratch/scientific-path/`` (``comet/``, ``percolator-3.07.1/``,
``limelight/limelight.xml``, ``corrupt/``, ``decoy-chain/``),
``scratch/fixture/``, ``scratch/percolator/3.07.1-linux-x86_64`` and
``scratch/percolator/3.09``; ``manifests/tools.json``;
``scripts/spikes/limelight_upload_spike/submit-import-wire-v16.xsd``; every
module's ``src/test`` (the drift test); ``/bin/bash`` and ``/bin/sleep`` (the
``secret-tool`` stand-in); the project JDK under ``tools/``. K562 data and
everything derived from it stay under ``scratch/`` or the build directory and
are never committed (``D-006``).

**Gate item 7** is proved by walking directories, not lists:
``LimelightUploadLeakTest`` (workflow) and ``LimelightUploadUiTest.gate7``
(the launched application: after a refused and two successful uploads and two
Results exports, every file under the project, the tool cache and the test's
temporary root, and every file written since the class started under the JVM
temporary directory and the home directory, plus the window's texts and the
console) are searched for a seeded ``CGSEED-`` key in every form the control
test (``theSearchFindsEveryForm``, ``theSearchFindsAPlantedCredential``)
shows the search finds.

**Where the acceptance evidence is.** :doc:`traceability` maps ``AC-LL-01`` to
``LimelightFirstUseInstallTest`` and ``LimelightConversionUiTest.gate1``;
``AC-LL-02`` to ``RealLimelightConversionTest``,
``LimelightConverterRealTest.aRealConversion`` and
``LimelightConversionUiTest.gate1``; ``AC-LL-03`` to
``LimelightCompatibilityTest``, ``RealLimelightCompatibleRerunTest`` and
``LimelightRerunUiTest.gate3``; ``AC-LL-04`` to
``RealLimelightCompatibleRerunTest``, ``CompatibilityActionTest`` and
``LimelightRerunUiTest``; ``AC-LL-05`` to ``LimelightConversionsTest``,
``LimelightSettingsTest`` and ``LimelightConversionUiTest``; ``AC-LL-06`` to
``LimelightUploadLeakTest`` and ``LimelightUploadUiTest.gate7``.

What has only run on Linux
==========================

Every real-tool and GUI test of this phase is ``@EnabledOnOs(LINUX)``.
Specifically **unverified**:

* **Windows and macOS**: no conversion, rerun, upload or credential store has
  run there. The converter is a JAR and should run wherever CometGUI's Java
  runs; that is untested. On macOS, a Limelight-enabled run needs the x86-64
  Percolator 3.07.1 under Rosetta 2 on Apple silicon (``D-004``); untested.
* **A real Limelight server**: never contacted, by design (P12-11, P12-13).
  The client is proved against the fake and the fake against the real
  importer; a server of another version, a proxy in front of a real server,
  and a real server's TLS are untested.
* **A real Secret Service keyring**: only the stand-in.
* **Decoy import through the product**: never offered for a CometGUI run;
  the converter's decoy path is tested only on Phase 00's ``decoy-chain`` run.
* **Very large files**: the 1 GB server default limit and multi-GB uploads
  were not exercised; the client streams the file and has a 6-hour file-call
  limit.

Residue
=======

Recorded in the work log's sign-offs and still true of the code:

* ``LimelightProvisioning``/``LimelightConverterStatus`` mirror
  ``PdvProvisioning``/``PdvStatus`` (about 330 lines, both over the one Tool
  Manager port) -- duplication, not a second installer.
* Two defensive layers no test can make fail from outside:
  ``PoutForConverter``'s read-back decoy-count comparison (the id comparison
  beside it carries the check) and the upload log's own redaction (every line
  reaches it already redacted).
* The rerun uses the Percolator section's **current** settings: no reader of
  a run's archived ``percolator-settings.json`` exists. A 3.07.1 run whose
  pout XML was deleted still offers the rerun, which Phase 09 then refuses as
  already run.
* An upload cannot be cancelled once sent.
* The rest of the application redacts with ``SecretRedactor.patternsOnly()``
  and does not know a typed key; no other writer receives one today.
* ``ProjectLimelightPort``'s fallback to the requested cutoff when a file
  carries none is unreachable (the judge refuses such a file).
* ``ToolManagerViewModel.refresh()`` starts processes on the JavaFX thread
  (pre-existing); once the converter is installed, every refresh runs
  ``java -jar ... --version`` there. Escalated, not this phase's code.
* PIT survivors argued equivalent or unreachable: the view-models'
  generation-counter increments (one, the upload files counter, could apply a
  stale list if a run is deselected and reselected during one read),
  ``Arrays.fill`` zeroing, a ``secret-tool`` timeout's cancellation, a
  progress boundary and ``Writer.flush``/``close``.
* ``EngineStep.CONVERT_LIMELIGHT``/``UPLOAD_LIMELIGHT`` and the four
  ``InputKind`` values stay declared and unused; whether to remove them is
  tier 1's.
