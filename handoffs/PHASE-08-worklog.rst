======================================================
PHASE-08 work log -- Workflow Engine and Comet Adapter
======================================================

:Phase: 08
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 session 10
   at ``b5ffad5`` (brief ``handoffs/PHASE-08-BRIEF.rst``)
:Started: 2026-10-06

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``b5ffad5`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*). One narrow
check: ``mvn -B -o -q -pl cometgui-workflow -am test
-Dtest='org.cometgui.workflow.**' -Dsurefire.failIfNoSpecifiedTests=false`` --
rc 0; 3 fresh surefire reports, **133 tests, 0 failures, 0 errors, 0
skipped** (``RunStateTest``, ``StepStateTest``, ``WorkflowStageTest``).
``scripts/ci/docs-build.sh`` -- rc 0 in 17 s.

Facts established by the orchestrator before design (2026-10-06)
-----------------------------------------------------------------

Measured on this host with the pinned binaries staged from
``scratch/phase05/artefacts`` (2026.03.0 ``ad93b4cf...``), the two K562 mzML
LF-repaired into private copies (SHA-256 ``a562f6e6...`` and ``602aad75...``,
the values ``docs/feasibility/scientific-path.rst`` records) and the first
1000 records of the UniProt proteome, in a scratch directory:

* ``comet -P<params> -N<rundir>/out/k562_3 <readonly-dir>/k562_3.mzML`` with
  ``num_threads = 4``, ``decoy_search = 1``: rc 0 in **1.4 s**; it wrote
  ``k562_3.pep.xml`` (2 602 922 B) and ``k562_3.pin`` (1 195 947 B) into the
  run directory, and ``ls -la`` of the input directory was identical before
  and after. The input directory had first refused ``touch``
  (``Permission denied``).
* **``comet -i`` writes ``<database>.idx`` beside the database.** With the
  FASTA in the read-only directory it failed, rc 1: ``Error - cannot open
  index file .../in/sub.fasta.idx to write``. With ``-D`` naming a **symbolic
  link** to the FASTA inside a writable cache directory it wrote
  ``cache/sub.fasta.idx`` there and nothing beside the FASTA. ``-j`` writes
  the same file name, overwriting a ``-i`` index.
* **The ``.idx`` header is plain text and self-describing**: line 1 ``Comet
  index database v5.  Comet version 2026.03 rev. 0 (fa08489)`` (2026.02.2
  writes ``v4.``), then ``IndexSearchType: fragment ion index`` / ``peptide
  index``, ``InputDB:``, ``MassRange:``, ``LengthRange:``, ``MassType:``,
  ``DecoySearch:``, ``DecoyPrefix:``, ``Enzyme:``, ``Enzyme2:``,
  ``NumEnzymeTermini:``, ``AllowedMissedCleavage:``, ``ClipNtermMethionine:``,
  ``NumPeptides:``, ``StaticMod:``, ``VariableMod:``, ``ProteinModList:``,
  ``RequireVariableMod:``, ``MaxVariableModsInPeptide:``, then protein names.
* 2026.03.0 searching a 2026.02.2 index: rc 1, ``Error - "c22/sub.fasta.idx"
  is not a v5 unified index file (v4 and older are intentionally not read ...)``.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P8-1 -- one of everything
    One process launcher (``ProcessService`` behind ``StageRunner``), one
    hasher (``HashService``: ``StreamingHashService``/``CachingHashService``),
    one provenance writer (``ManifestWriter``, ``ProvenanceEventLog``,
    ``ProvenanceReportWriter``), one redaction rule set
    (``org.cometgui.domain.secrets`` + ``ProcessRedactor``), one JSON reader
    and writer (``org.cometgui.provenance.json``), one atomic writer
    (``AtomicDocumentWriter``), one canonical parameter writer
    (``CanonicalParamsWriter.writeOnce``) and one validator
    (``CometValidator``). Anything missing is added **to that class's module,
    with its own tests and that module's ``--only`` harness**, never copied
    into the workflow. Test helpers (staging a real binary, fixtures) may be
    written per module, as ``UpstreamMirror``'s Javadoc records is this
    project's convention.

P8-2 -- where things live
    * ``org.cometgui.workflow.state`` (PIT-targeted): the declared engine DAG,
      per-step input fingerprints, invalidation and the rerun preview, the
      mapping of engine steps onto the stepper's ``WorkflowStage`` -- pure.
    * ``org.cometgui.workflow.engine``: the executor.
    * ``org.cometgui.workflow.steps``: the concrete steps.
    * ``org.cometgui.workflow.storage``: ``project.json``/``run.json`` reading
      and writing through the one JSON reader/writer and atomic writer, the
      project lock.
    * ``org.cometgui.domain.project`` and ``org.cometgui.domain.run``
      (PIT-targeted): the pure models -- descriptors, schema-version policy,
      the run-directory layout. The domain depends on nothing.
    * ``org.cometgui.tools.comet`` (PIT-targeted): the command builder, output
      base names, pepXML/PIN validation, PIN merge, the FASTA decoy scan, the
      ``.idx`` self-description reader.
    * ``org.cometgui.params.comet.validation`` (PIT-targeted): the decoy and
      index-compatibility **rules**, over file-system facts passed in as data.
      Release differences are data in ``comet-parameters.json`` (COMET-2026-03
      design rule C-2), never ``if (version ...)``.
    * ``workflow.engine``, ``workflow.steps`` and ``workflow.storage`` are
      **not** in the POM's PIT ``targetClasses`` and the brief forbids
      changing that configuration; every unit's sign-off runs PIT on them by
      ``-DtargetClasses`` all the same, and adding them is escalated to tier 1.

P8-3 -- the run directory
    The specification's *Project model* layout:
    ``runs/<UTC yyyyMMdd'T'HHmmss'Z'>-<id>/`` holding ``run.json``,
    ``parameters/comet.params``, ``inputs/pin/merged.pin``,
    ``outputs/comet/<base>.{pep.xml,pin}``, ``logs/``,
    ``provenance/{provenance.json,provenance.rst}`` and the event log. One
    deliberate divergence, escalated as a proposed specification amendment
    rather than built around: the process service writes **one** timestamped,
    stream-tagged log per invocation (Phase 03's design; it preserves the
    interleaving two files would lose), and a stage identifier must match
    ``[A-Za-z0-9_-]{1,64}``. So a Comet invocation's log is
    ``logs/comet-<nn>.log`` (``nn`` the input's 1-based position, two or more
    digits), not ``comet.<base>.{stdout,stderr}.log``; ``run.json`` and
    provenance map each ``nn`` to its spectrum file and base name.

P8-4 -- ``-N`` base names
    One Comet invocation per spectrum file, **always** with ``-N`` naming
    ``<run>/outputs/comet/<base>``. ``<base>`` is the spectrum file name minus
    its spectrum extension (``.mzML``, ``.mzXML``, ``.mgf``, ``.ms2``,
    ``.cms2``, ``.bms2``, ``.raw``, matched case-insensitively). Bases must be
    distinct case-insensitively; a collision is resolved deterministically in
    input order by appending ``_2``, ``_3`` ... and recorded. Never more than
    one input file on a Comet command line: the builder refuses it.

P8-5 -- the read-only input directory
    A directory whose write permission has been removed
    (``PosixFilePermissions``), **after a test step has proved that creating a
    file in it is refused** (``AccessDeniedException``). If the write
    succeeds -- running as root, a file system ignoring modes -- the test
    **fails**, naming why; it never passes or skips. No ``unshare``: a user
    namespace adds a platform dependency and proves nothing a refused write
    does not. Permissions are restored in cleanup so ``@TempDir`` can delete.

P8-6 -- real fixtures
    The ``D-006`` local fixture by checksum: the two K562 mzML from
    ``scratch/fixture`` held to their fetched SHA-256s, LF-repaired into
    private copies held to ``a562f6e6...``/``602aad75...``; the database is
    the UniProt proteome's **first 1000 records**, the same subset and
    SHA-256 the validation corpus records
    (``cometgui-params-comet/src/test/resources/fixtures/comet-validation/corpus.json``,
    ``subsetSha256``), unless a gate needs more and says why. Comet threads in
    tests: ``num_threads = 4``; per-file concurrency in tests at most 2. A
    missing or changed input **fails**; nothing skips. ``scratch/fixture``'s
    leftover ``.pep.xml``/``.pin`` (Phase 00) are never read as results and
    never deleted. Real-binary tests are ``@EnabledOnOs(LINUX)`` like the
    existing ones (only linux/x86-64 has ever been executed).

P8-7 -- decoys
    The decoy prefix is the model's ``decoy_prefix`` -- one project-level
    value (``R-DEC-03``). The FASTA scan streams the file and counts records
    whose accession (first whitespace-delimited token after ``>``) begins
    with the prefix, and the total. ``R-DEC-02``'s two blocks are **rules of
    the one validator**, fed the scan as data: "no decoys anywhere"
    (``decoy_search = 0`` and zero decoy records) and "double decoys"
    (``decoy_search != 0`` and any decoy record). Each is an ERROR whose text
    names the decoy configuration (``decoy_search`` value and meaning, the
    prefix, the FASTA and the count). Because they are report errors, Run
    readiness and the engine's validate step both block on them with no
    second rule.

P8-8 -- index mode
    Index construction (``-i`` fragment-ion, ``-j`` peptide) is its own step,
    cached in the **project** under ``index-cache/<key>/``, keyed by the
    FASTA's SHA-256, the mode, the Comet release and the parameters the
    ``.idx`` header records. Comet is run with ``-D`` naming a symbolic link
    to the FASTA **inside** that cache directory, so the ``.idx`` lands there
    (measured above). The search then passes ``-D<cache>/<fasta>.idx`` -- the
    one ordinary use of ``-D`` -- and the run records that mechanism. The
    compatibility check (assigned by tier 1) reads an existing ``.idx``'s
    header and refuses **before Comet starts** an index the selected release
    cannot read (format versions per release are metadata data: 2026.03.0
    reads ``v5`` only) or one whose recorded options contradict the search.

P8-9 -- what a Phase 08 run is
    The declared DAG is the specification's whole *Canonical workflow DAG*,
    Percolator and downstream included, so invalidation and the rerun preview
    are computed over the real graph. A run executes a **plan**: the
    sub-graph up to a target step. In this phase the target is "merge PIN"
    plus finalising provenance; Percolator and later steps are declared, not
    implemented, and are recorded as not planned -- **never** as succeeded or
    skipped. The run's state is derived over the planned steps only.

P8-10 -- fingerprints and the rerun preview
    Each step declares its inputs; its fingerprint is a SHA-256 over its
    identity, the hashes of its input files, the subset of settings it reads
    (for Comet: the canonical parameter file's bytes; for Percolator: its
    settings; for Limelight conversion: the q cutoff ...), the tool identity
    (version and binary SHA-256) and its upstream steps' fingerprints. The
    preview is every step whose fingerprint differs from the recorded run, and
    every step downstream of one. PSM/peptide display filters are inputs of
    no step. Gate item 6's expected sets are hand-typed from the
    specification's *Stage reruns* paragraph.

P8-11 -- storage
    ``project.json`` and ``run.json`` carry ``schemaVersion`` 1. Older: refuse
    with a message naming both versions (there is no older one to migrate;
    the policy is defined and tested). Newer: refuse **before** reading any
    other member, never modifying the file. ``project.lock`` is created
    ``CREATE_NEW`` and held with a ``FileChannel`` lock, recording pid, host
    and start time; a lock whose process is gone on this host is stale and
    recoverable, and the message names the owning process. A run's
    ``parameters/`` and ``run.json`` identity are written once; a second
    write is refused; a retry is a new attempt record, never a rewrite.

P8-12 -- provenance
    Every Comet invocation is a ``ToolRecord`` whose ``stageId`` is its
    ``comet-<nn>`` and whose ``ExecutionRecord`` carries **its own** argument
    array; every file a ``FileRecord`` (inputs: spectra, FASTA, the archived
    ``comet.params``; outputs: each ``pep.xml``/``pin``, ``merged.pin``,
    ``partial`` when the stage did not finish). The ``-P`` path **is** the
    archived file ``writeOnce`` wrote and hashed (``R-PARAM-12``); the record's
    hash is that hash and must equal a re-hash after the run. State
    transitions are ``stage.started``/``stage.finished`` events with the
    state; the PIN merge is recorded (inputs, row counts, output hash) in its
    ``stage.finished`` payload. Settings keys are pinned constants of shape
    ``[a-z0-9]+(\.[a-z0-9-]+)+``.

P8-13 -- cancellation
    Through ``RunningStage.requestCancellation`` only (Phase 03's descendant
    termination). Proved twice: a fake Comet that starts a child, asserted on
    the process tree (both pids dead, neither by the fake's watchdog code
    71); and the real binary cancelled mid-search, asserted dead by pid.
    Afterwards the stage logs and the event log parse, ``provenance.json`` is
    finalised with status ``cancelled`` and outputs present are ``partial``.

P8-14 -- prerequisite revalidation
    Before any recorded file is reused (retry from a failed step, a rerun
    that keeps Comet's output), it is **re-hashed** -- never served from the
    cache -- and compared with the recorded SHA-256. A mismatch refuses reuse
    with a message naming the file, its role and both hashes, and the plan
    offered includes the producing step.

P8-15 -- concurrency
    Per-file invocations run concurrently, bounded by
    ``max(1, min(files, cores / threads))`` (``num_threads = 0`` means all
    cores, so 1), with an explicit cap. The parameter file, the output names
    and the provenance order (input order, not completion order) do not
    depend on it. The first failure fails the stage: in-flight invocations
    are cancelled, pending ones not started, every log kept.

P8-16 -- Run in the interface
    Phase 07's ``RunReadinessViewModel`` gains the engine's half; the
    ``ENGINE_NOT_BUILT`` reason is removed and replaced by the engine's own
    reasons (Comet not installed, inputs missing, and the validator's report
    with file-system facts, which carries the decoy and index blocks). No
    second readiness. Scanning a FASTA happens off the JavaFX thread.

Sign-off procedure (every unit)
-------------------------------

Read the diff (``git show --stat`` and the full diff); run the affected
module's tests and **count** them from fresh surefire XML; targeted PIT on the
changed classes with ``mvn -B -o -pl <module> -am test-compile
org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>``
(``-Dcometgui.pit.threads=16``), read from ``mutations.xml``; at least two
injections of my own into **production** code, each proved to have landed
because the old anchor is gone, each restored and verified with ``sha256sum
-c`` and the compiled class re-checked; the class-population census for the
module; ``scripts/ci/docs-build.sh`` if ``.rst`` changed; and ``bash
scripts/verify-all-gates.sh --only NAME`` for every harness that reads what
changed (``docs`` and ``traceability`` for documentation, ``quality`` for
architecture rules, ``params`` whenever ``cometgui-params-comet`` is touched,
``paramui`` whenever ``cometgui-ui`` is). Injection scripts live in a private
scratch subdirectory per unit. **Added after unit 3:** a per-module ``mvn
install`` (with tests) through every module the unit touched, because the
harnesses run PIT and never ``verify``, and so cannot see SpotBugs.

Work units
==========

Serial, in this order; each builds on signed-off work. No two units run at
once.

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items
     - Sign-off

   * - 1
     - **Declared DAG, fingerprints, invalidation, rerun preview**
       (``workflow.state``). The seventeen canonical steps declared with their
       dependency edges and inputs; each mapped onto a stepper
       ``WorkflowStage``; a per-step input fingerprint (P8-10); a plan to a
       target step (P8-9); the rerun preview. Acceptance: for each scenario of
       the specification's stage-rerun list, a test with a hand-typed
       expected step set (display filter -> nothing; Percolator parameters ->
       Percolator and downstream conversion, not Comet; XML-capable
       Percolator -> Percolator from the preserved merged PIN then
       conversion; Comet parameters -> Comet and everything downstream;
       Limelight q cutoff -> conversion and upload only); a cycle or an
       undeclared input is rejected; PIT on the new classes.
     - R-RUN-01; gate 6; AC-WF-04
     - **Signed off 2026-10-06** (``ea174c5``). Diff read (30 files,
       ``workflow.state`` only; ``RunState`` refactored onto one precedence,
       old tests untouched). Tests: 13 fresh reports, **328 run, 0
       failures** (was 133). PIT ``-DtargetClasses=org.cometgui.workflow.state.*``:
       **234/234 KILLED**; census 20 compiled, 16 mutated, the 4 unmutated
       are ``RerunDecision``, ``StepKind``, ``ValueType`` (constant enums) and
       ``StepFingerprint`` (record). My injections, each landed (anchor gone,
       class hash changed) and restored by ``sha256sum -c``: (1) ``run-percolator``
       no longer declaring ``PERCOLATOR_SETTINGS`` -- 4 failures incl.
       ``RerunPreviewTest.assertSets:107 re-executed ==> expected: <[...,
       RUN_PERCOLATOR]> but was: <[]>``; (2) the ``merge-pin -> run-percolator``
       edge deleted -- 10 failures incl. scenario (d) ``but was: <[RUN_COMET,
       VALIDATE_COMET_OUTPUTS, MERGE_PIN, FINALISE_PROVENANCE, ...]>``.
       Accepted design call: PREPARATION steps (validate, resolve x2, hash
       inputs) run when needed and never invalidate downstream -- without it
       scenario (b) would rerun Comet. Recorded fingerprints must be those of
       steps that SUCCEEDED (units 2, 5 honour it).

   * - 2
     - **Project and run storage** (``domain.project``, ``domain.run``,
       ``workflow.storage``; ``docs/reference/project_format.rst``). The
       P8-3 layout as a path model; ``project.json``/``run.json`` with schema
       versions (P8-11); input records with canonical path, size, timestamps,
       MD5 and SHA-256 (``R-RUN-03``, no copying); the lock with stale
       recovery naming the owner; write-once run parameters. Acceptance:
       older/newer schema refused with the stated messages and the file
       byte-identical afterwards; a second instance refused while a live
       process holds the lock, a dead owner's lock recovered and named; a
       second write of a run's parameters refused; round trip pinned by
       hand-typed documents, not by the writer.
     - R-RUN-03..06
     - **Signed off 2026-10-06** (``6643bf4`` adds the reading half of
       ``CanonicalTimestamp`` to ``cometgui-provenance`` rather than a third
       private copy; ``0da7414`` the unit). Diff read: 58 files inside the
       brief's paths. Tests, fresh XML: domain **1132** (was 965), provenance
       **672** (2 pre-existing skips), workflow **445** (was 328), 0
       failures. PIT: ``domain.project.*,domain.run.*`` 234 = 231 KILLED + 3
       TIMED_OUT (genuine infinite loops), 0 survived; ``workflow.storage.*``
       181/181. My injections, landed and restored by ``sha256sum -c``: (1)
       ``SchemaVersionPolicy.judge`` accepting ``current + 1`` --
       ``SchemaVersionPolicyTest.newerRefused:60 Expected
       UnsupportedSchemaVersionException to be thrown, but nothing was
       thrown`` and ``judges:36``; (2) ``ProjectLock.isAlive`` always false
       (a live owner taken for stale) -- ``ProjectLockTest.productionAcquire``
       and ``liveness`` fail. Agent's own gates: ``--only quality`` 42,
       ``--only docs``/``traceability``, ``--only provenance`` 24 -- all
       pass. **Carried forward:** ``CanonicalParamsWriter.writeOnce`` uses
       plain ``CREATE_NEW``, so a crash mid-write leaves a truncated
       ``comet.params`` that ``RunStore`` checks only by existence -- unit 6
       re-hashes it before use (P8-14); ``JsonWriter`` redacts string values,
       so a path matching a secret pattern is stored redacted in
       ``run.json`` (the store returns the re-read record for that reason).

   * - 3
     - **Pre-run facts and rules.** In ``tools.comet``: the streaming FASTA
       decoy scan and the ``.idx`` self-description reader. In
       ``cometgui-params-comet``: the decoy blocks (P8-7) and the
       index-compatibility rules (P8-8) in the one validator, release facts as
       metadata data; ``docs/decoys.rst``. Acceptance: no-decoy FASTA +
       ``decoy_search = 0`` and decoy FASTA + ``decoy_search`` 1 or 2 are
       ERRORs naming the configuration; real ``.idx`` files built by both
       binaries (v4, v5; ``-i`` and ``-j``) read and judged per release; a
       v4 index refused for 2026.03.0, accepted for 2026.02.2; a header
       contradicting the search refused; ``--only params`` green with its
       floor.
     - R-DEC-01..03, R-PARAM-13, the index check; gates 4, 5 (rule half)
     - **Signed off 2026-10-06 after one rework round** (``c4d7be4``,
       ``097b2ac``; my repair ``028ff7e``; rework ``46443dc``). Fact types in
       ``domain.params``; ``FastaDecoyScanner`` and ``CometIndexHeaderReader``
       in ``tools.comet``; ``FastaDecoyRule``, ``IndexCompatibilityRule`` and
       ``CometValidator.validate(model, PreRunFacts)`` in params-comet;
       readable index formats are data (``indexFormats``: 2026.03.0 ``[5]``,
       2026.02.2 ``[4]``, 2024.01.0 ``[]``, each measured on the real
       binaries). Tests, fresh XML: domain 1139, tools 318 (was 232),
       params-comet 2093 (was 1995), process 274, 0 failures. PIT:
       ``domain.params.*`` 44/44; tools readers 123/123; params-comet new
       rules + ``IndexFormats`` + ``CometValidator`` 180/180. My injections,
       landed and restored: (1) **version-blind** -- format judged ``< 4``
       for every release: ``IndexCompatibilityRulesTest.formatByRelease:105
       expected: <INDEX_FORMAT_UNREADABLE> but was: <INDEX_OPTION_UNRECORDED>``;
       (2) "no decoys anywhere" only for an empty FASTA:
       ``FastaDecoyRulesTest.noDecoysAnywhere:67 expected exactly one
       finding: ValidationReport[findings=[]]``. **Rejected first:** my
       ``install`` found SpotBugs failing the build -- 1 finding in
       cometgui-tools tests (repaired by me, ``028ff7e``) and 7 in
       params-comet tests (returned; fixed at root, no exclusion,
       ``46443dc``). The agent's gates (``--only params`` 109) run PIT, not
       ``verify``, so cannot see SpotBugs; from unit 4 every brief requires a
       per-module ``install``. After rework: ``mvn -pl cometgui-workflow -am
       install`` rc 0, 0 ``BugInstance`` in all eight modules; ``--only
       params`` 109 (agent, after the rework commit). Judgement call
       accepted: ``index_search_type`` contradicting the index's type is an
       ERROR, stricter than either binary. Residue for unit 8:
       ``docs/comet_parameters.rst`` still says decoy counting "arrives with
       the workflow".

   * - 4
     - **Comet adapter** (``tools.comet``). Per-file ``ToolCommand``: binary,
       ``-P``, ``-D`` only when overriding (P8-8), ``-N`` (P8-4), one input;
       working directory inside the run; constructed environment with
       ``LANG``. Index-build command. Output validation per file: pepXML
       well-formed with its expected root and spectrum queries; PIN header,
       feature columns, rows, target/decoy counts (``R-DEC-04``). PIN merge:
       one header, summed rows, feature-column check naming both files, a
       merge record (inputs, row counts, output hash). Acceptance: argv
       asserted element by element; two inputs refused; merge of real PINs
       has exactly one header and the summed rows; a mismatch names the
       files; zero decoy rows fails naming the decoy configuration.
     - R-CMT-03, 04, 06, 07 (command), R-DEC-04; gate 3; AC-WF-01 (merge)
     - **Signed off 2026-10-06** (``60117e0``, 24 files in ``tools.comet``).
       ``mvn -pl cometgui-tools -am install`` with tests: rc 0, 0
       ``BugInstance``; tools **429 tests** from 29 fresh reports (was 318), 0
       failures. PIT over the 12 new classes: 168 = 165 KILLED + 3 TIMED_OUT
       (read: genuine infinite loops), 0 survived. Real facts: per-file PIN
       rows 3554 (k562_3) + 2918 (k562_4) = **6472** merged under one header;
       ``SpecId`` carries the absolute ``-N`` path (Phase 09 must know);
       ``-N`` with two inputs re-measured on 2026.03.0 -- still ignored, Comet
       writes beside the input; Comet runs with an empty environment, ``LANG``
       set anyway. My injections, landed and restored: (1) feature columns
       compared as sets -- ``PinMergerTest.swappedColumns:175`` and
       ``CometAdapterRealBinaryTest.swappedRealColumns:442 Expected
       CometOutputException to be thrown, but nothing was thrown``; (2) ``-N``
       dropped from the per-file command -- 5 failures, including the real
       search against the read-only input directory
       (``CometAdapterRealBinaryTest.search:171``) and
       ``parameterFileCommands:76``. Agent's gates: ``--only quality`` 42,
       ``--only install`` 95 (floor). Left to unit 6: validating the extra
       ``.decoy.pep.xml`` Comet writes for ``decoy_search = 2``.

   * - 5
     - **Engine core** (``workflow.engine``). Executes a plan over the
       declared DAG with observable step-state transitions, each written to
       the event log; bounded concurrency (P8-15); cancellation (P8-13);
       partial outputs; retry from a failed step; prerequisite revalidation
       (P8-14); provenance finalised atomically on success, failure and
       cancellation. Tested with fake executables, including one that
       starts a child. Acceptance: process-tree cancellation proof; a
       changed input refuses reuse naming the file; the event log and
       ``provenance.json`` parse after cancel and failure; concurrency bound
       observed, not assumed.
     - R-CMT-05, R-RUN-02, cancellation; gates 7 (fake), 8
     -

   * - 6
     - **Comet steps and the real run** (``workflow.steps``). Validate
       (validator + file-system facts; blocks before Comet), serialise
       (``writeOnce`` into ``parameters/``), hash inputs, index (cached,
       keyed, recorded), per-file Comet, validate outputs, merge, finalise.
       Real-binary tests: two spectrum files from a proven read-only
       directory (gate 1); input-tree snapshot before/after (gate 2); merged
       PIN (gate 3); both decoy blocks with no Comet process started (gates
       4, 5); cancellation of the real binary (gate 7); changed input (gate
       8); distinct argv per file and the archived ``comet.params`` hash
       equal to the executed file (gate 9); a version-scoped index refusal.
     - R-CMT-01..08, R-DEC-02, R-RUN-01..06; gates 1-9; AC-WF-01..05,
       AC-PRV-03, AC-PRV-04
     -

   * - 7
     - **Run in the application** (``cometgui-app``, ``cometgui-ui``). The
       process runner and console sink wired into ``ApplicationServices``;
       ``ENGINE_NOT_BUILT`` replaced by the engine's reasons (P8-16); Run and
       Cancel drive the engine off the JavaFX thread; the stepper follows the
       engine; the rerun preview shown before a rerun starts. ``paramui``
       control H revisited (its injection stops being equivalent).
       Acceptance: GUI tests -- a decoy block's text on screen with Run
       disabled; Run enabled when ready; a run through the interface with a
       fake Comet moving the stepper; ``--only paramui`` green.
     - R-RUN-01 (UI), AC-WF-03 (UI)
     -

   * - 8
     - **Documentation and traceability.**
       ``docs/developer/workflow_engine.rst`` as built; the architecture page;
       traceability entries for AC-WF-01..05, AC-PRV-03 and AC-PRV-04 naming
       the real tests. Acceptance: ``docs``, ``traceability`` green.
     - R-DOC; AC traceability
     -

   * - 9
     - **Falsifiability harness** ``scripts/verify-workflow-gates.sh``,
       registered additively as ``workflow`` in ``verify-all-gates.sh``:
       each gate item and the index check injected into production code in
       a ``git archive`` sandbox, proved to reach the bytecode, graded on the
       failing assertion's words; at least one version-blind control on the
       index check; ``-N`` with several inputs; harness self-controls. Cost
       stated.
     - gates 1-9
     -

Rejections and rework
=====================

* **Unit 3, round 1** -- SpotBugs failed ``cometgui-tools`` (1) and
  ``cometgui-params-comet`` (7) on the unit's test code; every gate the unit
  ran was green because none of them runs ``verify``. One repaired by me, seven
  returned and fixed at the root. See the unit 3 sign-off.

Files read outside their module by tests
========================================

Recorded the day a unit lands, for ``--only tests``' sandbox.

* Unit 1: none.
* Unit 2: no path outside the module; ``RunStoreTest`` loads
  ``cometgui-params-comet``'s committed main resources from the class path.
* Unit 3: ``scratch/phase05/artefacts/v2026.03.0__comet.linux.exe`` and
  ``v2026.02.2__comet.linux.exe`` (tools and params-comet tests),
  ``scratch/fixture/UP000005640_9606.fasta`` (both),
  ``scratch/fixture/20100614_Velos1_TaGe_SA_K562_3.mzML`` and
  ``manifests/tools.json`` (params-comet). All were already in the
  ``tests`` sandbox's precondition set; nothing under ``docs/``.
* Unit 4: ``scratch/phase05/artefacts/v2026.03.0__comet.linux.exe``, both
  K562 mzML and the proteome under ``scratch/fixture`` (tools tests). Already
  in the sandbox's precondition set.

Deferred
========

None yet.

Blockers escalated
==================

None yet.
