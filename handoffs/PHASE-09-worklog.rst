=====================================================================
PHASE-09 work log -- Percolator Adapter and Version Capabilities
=====================================================================

:Phase: 09
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 session 10
   at ``66067ac`` (brief ``handoffs/PHASE-09-BRIEF.rst``)
:Started: 2026-10-07

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``66067ac`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*): tier 1
ran ``scripts/build.sh`` and the full ``scripts/verify-all-gates.sh`` at Phase
08's exit gate on this tree.

Facts established by the orchestrator before design (2026-10-07)
-----------------------------------------------------------------

* **Host:** Debian 12, ``GLIBC 2.36``.
* **The capability probe today establishes only** ``XML_OUTPUT`` and
  ``XML_DECOY_OUTPUT`` (``PercolatorCapabilityProbe``'s own Javadoc: the
  tab-separated, weights, seed and thread capabilities "are not probed and are
  therefore absent", recorded as residue for this phase). Under ``R-TOOL-08``
  a command builder reading probed capability could therefore pass nothing but
  ``-X``/``-Z``. Extending the one probe is this phase's first job.
* **Installed builds carry the probe's set**, written into the completion
  marker (``InstallationMarker.capabilities``) and surfaced by
  ``ManagedToolManager.offerFor`` as ``OBSERVED_BY_EXECUTION``; a build that
  is not installed carries the manifest's declared capabilities with their
  evidence value (``observed-by-execution`` for Linux 3.07.1 and 3.06.5,
  ``inferred-from-artefact-bytes`` for every macOS and most Windows rows).
* **Manifest Percolator rows** (``manifests/tools.json``): 3.07.1 and 3.06.5
  on linux, macos (x86-64) and windows; 3.09 on macos-aarch64 and windows
  only. **3.09 has no Linux row**, as ``D-003`` anticipated, and it stays that
  way: absent is honest.
* **3.09 does run on this host**, as an unmanaged binary: the upstream
  ``.rpm``'s executable (``c31f6139...``) with Boost 1.66 shared libraries
  extracted from CentOS 8.5 packages, through
  ``scratch/percolator/3.09/run-percolator-3.09.sh``. Under an empty
  environment (``env -i``) it prints ``Percolator version 3.09.0, Build Date
  May 21 2026 17:16:38``. That is how this phase executes 3.09 on Linux: as a
  **registered local binary**, which is the product's own route for a build
  the manifest cannot offer.
* **Option names, 3.07.1 against 3.09** (``--help``, both builds): 3.09 drops
  ``--xmloutput``, ``--decoy-xml-output``, ``--xml-in``, ``--stdinput-xml``
  and ``--no-schema-validation``, and adds ``--irls-pep``, ``--pava-pep`` and
  ``--rank-pep``. ``--results-psms``, ``--results-peptides``,
  ``--decoy-results-psms``, ``--decoy-results-peptides``, ``--weights``,
  ``--seed``, ``--num-threads``, ``--testFDR``, ``--trainFDR`` and
  ``--maxiter`` are in both. (Help text is evidence of a name, not of a
  capability -- ``R-PERC-02``.)
* **The weights artefact** names its own layout: three comment lines, then per
  cross-validation bin a header of feature names, a normalised-weights row and
  a raw-weights row (``scratch/percolator/capability-probe/p9_weights.txt``).
* **Percolator posts usage analytics by default.** The 3.07.1 binary carries
  ``GoogleAnalytics::postToAnalytics``; both releases offer
  ``--no-analytics``. No document in this repository mentions it. Escalated
  (see `Blockers escalated`_), not acted on: whether the product passes it is
  a product decision, and ``R-PERC-06`` forbids an unprobed option anyway.
* ``SectionId.PERCOLATOR`` exists in ``cometgui-ui`` with no content;
  ``EngineStep`` declares ``resolve-percolator``, ``run-percolator`` and
  ``parse-percolator`` with ``implementedInPhase`` 9; the provenance key
  ``percolator.seed`` is already pinned (``ProvenanceSchema.PERCOLATOR_SEED_SETTING``);
  the JVM locale is already recorded (``ApplicationRecord``).
* ``cometgui-ui`` depends on ``cometgui-params-percolator`` and
  ``cometgui-results`` but **not** on ``cometgui-tools``; ``cometgui-results``
  and ``cometgui-params-percolator`` depend on ``cometgui-domain`` alone.

Design decisions, made before the first dispatch
================================================

Every unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P9-1 -- one of everything
    One process launcher (``ProcessService`` behind ``StageRunner``; the
    engine's ``StepContext.invoke``; the probe's ``ToolRunner``), one hasher
    (``HashService``), one provenance writer (``ManifestWriter``,
    ``ProvenanceEventLog``, ``ProvenanceReportWriter``, assembled by
    ``ProvenanceLedger``), one redaction rule set
    (``org.cometgui.domain.secrets`` + ``ProcessRedactor``), one JSON reader
    and writer (``org.cometgui.provenance.json``), one atomic writer, one step
    graph and run store (``workflow.state``, ``workflow.storage``), **one
    capability probe** (``PercolatorCapabilityProbe``, extended, never
    copied), **one PIN reader** (``tools.comet``'s ``PinReader``/``PinHeader``),
    **one pout-XML reader** (``PoutDocument``), one local-binary registration
    (``LocalPercolatorRegistration`` behind ``ToolManager.registerLocalBinary``).
    Anything missing is added to that class's module with its own tests and
    that module's ``--only`` harness.

P9-2 -- capability, never version number
    No product code branches on a Percolator version number. The **only**
    permitted uses of a Percolator ``ToolVersion`` are: ordering candidates to
    find "newest"; the existing ``>= 3.05`` registration floor
    (``LocalPercolatorRegistration.MINIMUM_VERSION``); and display/recording.
    Every option emitted, every artefact expected, every stage-availability
    decision and every message reads a probed ``ToolCapability``. Advisories
    are manifest data (``ToolOffer.advisories``), never ``if (version ...)``.
    Every sign-off includes an injection that makes one of these decisions
    branch on the version number, and the tests must go red.

P9-3 -- where things live
    * ``org.cometgui.params.percolator`` (``cometgui-params-percolator``,
      domain-only, PIT-targeted, coverage gate already on): the Percolator
      settings model and its validation; which ``ToolCapability`` each setting
      needs; the downstream-stage requirement table; the *latest compatible*
      resolver and its result (selection, every skipped newer version with
      the capability it lacks, Limelight availability with the ``R-PERC-03``
      remedies); advisory rendering. Pure: no file, process or thread. Its
      POM gains ``<cometgui.mutation.skip>false</cometgui.mutation.skip>`` --
      the module's own switch, which ``scripts/build.sh`` *requires* once a
      critical package compiles a real class (the parent POM's documented
      mechanism). That is not a change to PIT's configuration, and nothing
      else in any POM changes.
    * ``org.cometgui.tools.percolator`` (PIT-targeted): the probe, the banner
      and version reading, the command builder, the pre-launch PIN check.
    * ``org.cometgui.results.parser`` (PIT-targeted): the PSM and peptide TSV
      parsers and the weights parser. **This is the parsers' one home**, so
      Phase 10's store and views build on them rather than writing a second
      set. ``cometgui-results`` gains the same module switch, for the same
      reason. ``org.cometgui.results.filtering`` gains only the display
      q-value filter *values* (defaults 0.01 and 0.01, ``[0, 1]``, inclusive)
      and their predicate -- needed for ``AC-RES-05`` and gate item 9;
      Phase 10 owns the store, the tables, the counts UI and export.
    * ``org.cometgui.workflow.steps``: the three Percolator step actions and
      the compatible-version rerun.
    * ``cometgui-ui``: the Percolator section's view-model and view;
      ``cometgui-app``: wiring.

P9-4 -- what the probe establishes
    The extended probe establishes ``PSM_TSV_OUTPUT``, ``PEPTIDE_TSV_OUTPUT``,
    ``DECOY_OUTPUT``, ``WEIGHTS_OUTPUT``, ``SEED_OPTION`` and
    ``THREAD_OPTION``, plus whatever new capability constants the settings
    need (P9-7), each **on its own observable** -- a file written and parsed
    with the expected rows, an option accepted on a run that completed -- so
    that one unsupported option can never hide or fake another. ``XML_OUTPUT``
    and ``XML_DECOY_OUTPUT`` stay functional exactly as today. A probe that
    could not exercise the binary still **throws** rather than returning an
    empty set. Completion markers written by the earlier probe hold only the
    two XML capabilities; such a marker must not make a build silently lose
    its tab-separated output: the unit defines how an older marker is
    re-probed and records it (``R-TOOL-07``).

P9-5 -- resolution (``R-PERC-02``, ``R-PERC-10``, ``R-PERC-03``)
    The resolver works over the Tool Manager's Percolator offers and the set
    of enabled downstream stages. A candidate is an offer that is
    ``INSTALLED`` (managed or registered local) or ``NOT_INSTALLED`` but
    installable here; offers that are ``UNAVAILABLE_ON_THIS_PLATFORM``,
    ``HOST_REQUIREMENTS_NOT_MET`` or ``FAILED`` are never selected (and are
    named when they are the reason). **A capability counts only when its
    evidence is observed** (``DeclaredCapability.isObserved()``): an
    installed build's probed set always is; a manifest row's may be (Linux
    3.07.1) or may not (macOS, inferred from bytes), and an unobserved claim
    makes the build ineligible for a stage needing it, with a message that
    says the claim is unobserved and that installing it will probe it
    (``R-TOOL-08``). The default is the highest-version candidate whose
    observed capabilities satisfy every enabled stage; managed before local
    at an equal version. The result names every newer candidate it skipped
    and the capability each lacks; with no candidate satisfying Limelight it
    returns the non-XML default and marks Limelight unavailable with the
    remedies (register a local XML-capable binary; convert on a supported
    platform). Re-resolving after the enabled stages change reports whether
    the default changed, from what to what, and why.

P9-6 -- the stage requirement table
    Data, in one place: Limelight conversion needs ``XML_OUTPUT``. The core
    rescoring stage's needs (tab-separated PSM and peptide output) are checked
    by the command builder at run time and refuse the run with a named
    reason; they are not resolution criteria, because a not-yet-installed
    build has no observed tab-separated capability to resolve on.

P9-7 -- settings and options
    ``testFDR`` and ``trainFDR`` (default 0.01 each, **separate types and
    fields from the display filters**, ``AC-RES-05``, with descriptions that
    say so, ``R-PERC-04``), the random seed (a fixed recorded default,
    ``R-PERC-05``), maximum iterations and thread count, each validated.
    **Every option the builder can emit maps to exactly one ``ToolCapability``
    that the probe establishes**; an option with no established capability is
    not emitted. Where that needs new capability constants (for example for
    ``--testFDR``/``--trainFDR`` and ``--maxiter``), they are added to
    ``ToolCapability`` in ``cometgui-domain`` with probe evidence, never
    passed unprobed. The effective seed is the value passed, or -- when the
    binary lacks ``SEED_OPTION`` -- recorded as not passed together with the
    reason; either way it is in provenance (gate item 7).

P9-8 -- the PIN check before launch
    ``R-DEC-04`` and the specification's *Input validation before Percolator*.
    In ``run-percolator``, before any process starts: the merged PIN exists,
    is non-empty, has the required header fields, parsable numeric features,
    and both target and decoy rows; a zero-decoy PIN fails the step with a
    message naming the decoy configuration and **no process is launched**
    (counted around the real ``ProcessService``). Built on ``tools.comet``'s
    PIN reader. The real zero-decoy case is Phase 08's: Comet 2026.02.2 with
    a fragment-ion (v4) index and ``decoy_search = 1`` searches to zero decoy
    rows; its real PIN is one fixture. Synthetic PINs only for named edge
    cases, each recorded as constructed.

P9-9 -- the run layout
    ``parameters/percolator-settings.json`` (written once and hashed,
    ``R-RUN-06``), ``outputs/percolator/`` for the raw artefacts, and one
    stream-tagged process-service log ``logs/percolator.log`` per invocation
    -- the same divergence from the specification's ``percolator.{stdout,
    stderr}.log`` that revision 14 adopted for Comet, escalated as an
    amendment, not built around. Raw outputs are made read-only once the step
    succeeds and their hashes recorded (``R-PERC-07``); derived files never
    go under ``outputs/percolator/``.

P9-10 -- provenance
    One tool record per Percolator invocation with its own argument array,
    version and binary SHA-256; one file record per input and output with a
    role; settings under pinned keys of shape ``[a-z0-9]+(\.[a-z0-9-]+)+``
    held in one place: ``percolator.seed`` (already pinned), and keys for the
    selected version, why it was selected, each skipped newer version with
    its missing capability (``R-PERC-10``), the advisories shown
    (``R-PERC-11``), the probed capability set, and the weights fallback
    warning (``R-PERC-08``).

P9-11 -- the compatible-version rerun
    A **new run**, because a version change is a configuration change
    (``R-RUN-06``; Phase 08's rule that only an unchanged configuration is
    retried). It records the run it derives from; its merged PIN is the
    original's, re-hashed and required equal to the original's recorded
    SHA-256 before use (``R-RUN-02``); Comet is not executed; only the
    Percolator steps and their downstream run. The original run directory is
    byte-identical afterwards, proved by a tree hash before and after. Any
    change to ``run.json`` is a documented format change honouring the
    schema-version policy (``R-RUN-04``) and
    ``docs/reference/project_format.rst``.

P9-12 -- the interface
    The Percolator section: the version selector over runnable offers with the
    resolved default marked; a capability and status badge; the
    Limelight-conversion toggle that is the "enabled downstream stage" input
    (Phase 12 owns the converter, not this switch's existence); a notice when
    toggling changes the default; the skip reason naming the version and the
    missing capability; advisories at selection time; Limelight shown
    unavailable with the remedies; Advanced settings for the selected
    version's supported options; the display filter defaults with the text
    that they change display and export only; the compatible-version rerun
    action, and local-binary registration where no managed XML-capable build
    exists. The interface reaches everything through ports, as Phase 08's
    ``RunEnginePort`` does; it never sees the installer or the process
    service.

P9-13 -- fixtures and platforms
    Percolator runs on a merged PIN produced by the **real Comet path**
    (Phase 08's fixture: the two K562 mzML, the UniProt proteome's first 1000
    records, Comet 2026.03.0), never on a hand-typed one. Synthetic PINs are
    allowed only where a gate names an edge case (two- and three-split
    weights, zero decoys) and are recorded as constructed. Real-binary tests
    are ``@EnabledOnOs(LINUX)``, verify each binary's SHA-256 first, and
    **fail rather than skip** when a fixture is missing, naming how to refill
    it. 3.07.1 and 3.09 are executed on Linux (3.09 as in the facts above);
    3.06.5 where cheap. Windows and macOS execution is recorded as
    unverified.

P9-14 -- harness
    ``scripts/verify-percolator-gates.sh``, registered additively in
    ``scripts/verify-all-gates.sh`` as ``percolator`` with a floor equal to
    its measured control count; every gate item graded by an injection into
    production code proved to have reached the bytecode, at least one
    version-blind. Kept lean: its cost is measured and stated.

Sign-off procedure (every unit)
-------------------------------

Read the diff (``git show --stat`` and the full diff); install the upstream
modules first (``mvn -B -o -pl <module> -am install -DskipTests`` or with
tests), so nothing compiles against a stale ``_build/m2repo``; run the affected
module's tests and **count** them from fresh surefire XML; targeted PIT on the
changed classes (``mvn -B -o -pl <module> -am test-compile
org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<fqcn,...>
-Dcometgui.pit.threads=16``), read from ``mutations.xml``; my own injections
into **production** code -- each proved to have landed because the old anchor
is gone and the compiled class changed, at least one making a decision branch
on the version number -- restored and verified with ``sha256sum -c``; a
per-module ``mvn install`` with tests through every module touched (the
harnesses run PIT and never ``verify``, so cannot see SpotBugs); the
class-population census for the module; ``scripts/ci/docs-build.sh`` if
``.rst`` changed; and ``bash scripts/verify-all-gates.sh --only NAME`` for
every harness that reads what changed (``docs`` and ``traceability`` for
documentation; ``quality`` for architecture rules; ``params`` if
``cometgui-params-comet`` is touched; ``install`` if ``cometgui-install`` or
the manifest is; ``paramui`` and ``shell`` if ``cometgui-ui`` is;
``workflow`` if ``cometgui-workflow`` or ``tools.comet`` is; ``provenance``
if ``cometgui-provenance`` is). Injection scripts live in a private scratch
subdirectory per unit. A targeted PIT run overwrites the module's
``mutations.xml``; before the ``tests`` harness runs at the end, the full
reports are regenerated as ``build.sh`` does (Phase 08's surprise).

Work units
==========

Serial, in this order; each builds on signed-off work. No two units run at
once. Each unit agent is given the brief's *Build economy* section verbatim.

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items
     - Sign-off

   * - 1
     - **Capability probe extended** (``tools.percolator``, ``domain.tools``
       if new constants are needed, ``cometgui-install`` only for the
       older-marker re-probe). P9-4 and P9-7's capability constants.
       Acceptance: the real 3.07.1 portable binary probes to the full set
       (both XML capabilities plus every new one); the real 3.09 (registered
       local, P9-13) probes to every new capability and **neither** XML
       capability; 3.06.5 probed and recorded; a fake binary that rejects
       one option loses exactly that capability and keeps the others; a
       binary that cannot be exercised throws; an older marker is re-probed
       per the rule the unit defines; version parsing from the banner for
       3.05 through 3.09 and future-looking strings (``3.10``, ``4.0``,
       ``3.09.1``).
     - R-PERC-02, R-PERC-06 (evidence half), R-TOOL-06..08; gates 2, 3
     - **Signed off 2026-10-07** (``62848df``; my integration repair
       ``c20f900``). Diff read: 24 files -- three new capabilities
       (``TEST_FDR_OPTION``, ``TRAIN_FDR_OPTION``, ``MAX_ITERATIONS_OPTION``);
       the one probe now makes eleven runs over the 64+64 fixture, one per
       capability, each judged on its own file or on a completed run
       (``ProbeArtefacts``: header by name, exact row count, target/decoy by
       the protein prefix); ``PercolatorOption`` maps each of 12 proved
       spellings to one capability; the completion marker records
       ``capabilityProbeGeneration`` 2 and an older marker verifies as
       ``CAPABILITIES_FROM_AN_EARLIER_PROBE`` (not installed: the next install
       re-probes, at the cost of a re-download). **Found by the agent and
       repaired by me:** ``cometgui-app``'s ``ToolManagerInstallUiTest`` and
       ``UpstreamInstallUiTest`` pinned the old two-capability row, so
       ``--only install`` was red on ``62848df``; updated to the eleven
       capabilities and twelve runs (``c20f900``). ``mvn -pl
       cometgui-install,cometgui-tools -am install`` with tests, rc 0, fresh
       XML: domain 1157, provenance 672 (2 skipped, pre-existing), process
       274, tools **509** (was 429), install **1038**, 0 failures, 0
       ``BugInstance`` in all five. App: ``ToolManagerInstallUiTest`` 3/3,
       ``UpstreamInstallUiTest`` 1 run + 1 network-gated skip. Real binaries
       (agent's output, re-run in my tools suite): 3.07.1 and 3.06.5 probe to
       all eleven; 3.09 (rpm binary + Boost 1.66, registered local) to the
       nine non-XML ones; about 5.5-5.9 s per probe. My PIT over
       ``PercolatorCapabilityProbe*``, ``ProbeArtefacts*``,
       ``PercolatorOption*``: **74/74 KILLED**. My injections, each landed
       (anchor gone), restored by ``sha256sum -c``: (1) **version-blind**:
       ``DECOY_OUTPUT`` granted only when ``version.isAtLeast(3.07)`` -- 2
       failures, ``PercolatorRealBinaryTest.theOldestManagedBinary:242
       ... expected <[..., DECOY_OUTPUT, ...]> but was <[...]>`` (3.06.5) and
       ``theOtherRealBinary:400``; (2) the target/decoy check in
       ``ProbeArtefacts.isResultTable`` disabled -- 5 failures, e.g.
       ``PercolatorCapabilityProbeTest.aDamagedPsmTable:769 a decoy among the
       targets``. ``--only install`` **PASS 95 controls in 306 s** on
       ``c20f900``; ``--only quality`` PASS. **Tests read outside their
       module** (all under ``scratch/``, which the ``tests`` sandbox links
       whole): ``scratch/percolator/3.09/run-percolator-3.09.sh``, its rpm
       binary and two Boost libraries, besides the existing
       ``scratch/phase05/artefacts`` zips and the 3.09 ``.deb`` payload.
       Limits noted: the fixture cannot tell a PSM table from a peptide table
       (all 128 peptides distinct); PIT reports in domain, tools and install
       are now partial and must be regenerated before ``--only tests``.

   * - 2
     - **Settings, resolution and advisories** (``params.percolator``).
       P9-5, P9-6, P9-7. Acceptance: Limelight enabled returns the newest
       candidate with observed ``XML_OUTPUT`` (3.07.1 against a set holding
       3.09), disabled returns the newest overall; the result names 3.09 and
       ``XML_OUTPUT`` as the reason; toggling reports the change from-and-to;
       a set with no observed XML-capable build yields a non-XML default and
       Limelight unavailable with both remedies; an inferred-only claim is
       not counted and is said to be unobserved; an unreleased future
       version with ``XML_OUTPUT`` (``3.10``) wins with no code change;
       settings defaults and ranges; ``testFDR``/``trainFDR`` separate from
       the display filters; advisory rendering. Expected values hand-typed.
     - R-PERC-02, 03, 04, 05, 10, 11; gates 3, 4 (logic); AC-RES-05
     - **Signed off 2026-10-07** (``aca45ea``). Diff read: 25 files, all in
       ``cometgui-params-percolator``; the POM gains only the module's own
       ``cometgui.mutation.skip=false`` and a corrected comment.
       ``PercolatorSettings`` (testFDR/trainFDR as their own ``BigDecimal``
       types, seed 1, maxiter 10, threads 3 -- Percolator's own defaults;
       ranges measured on all three binaries), ``PercolatorSetting`` mapping
       each to one capability, ``EffectiveSeed``; ``resolution``:
       ``DownstreamStage`` (Limelight needs ``XML_OUTPUT``),
       ``PercolatorResolver``, ``ResolutionChange``, ``ResolutionMessages``
       (all text in one place), ``AdvisoryRendering``. **Deviation accepted:**
       a managed ``INSTALLING`` offer is a candidate (the same build as the
       ``NOT_INSTALLED`` one; excluding it would make the default jump during
       an install). The brief's PIT command with ``-am`` fails on domain
       ("No mutations found"); run without ``-am`` after installing domain.
       ``mvn -pl cometgui-params-percolator -am install`` with tests rc 0: 54
       tests, 0 failures, 0 ``BugInstance``; JaCoCo line 409/409, branch
       169/171; census 17 compiled = 17 in ``jacoco.csv``. PIT, the module's
       full configured run: **145/145 KILLED** over 17 classes. My
       injections, landed and restored by ``sha256sum -c``: (1)
       **version-blind**: a stage available iff a candidate's version is
       below 3.09 -- 2 failures, ``PercolatorResolverTest.macosInferred`` and
       ``ResolutionChangeTest.switchedOnButUnavailable``; (2) the preference
       order not reversed (oldest first) -- 13 failures, e.g.
       ``futureWithXmlWins expected <3.10> but was <3.07.1>``. Note: the
       real Linux set (3.07.1 vs 3.09) cannot distinguish a version rule from
       a capability rule; only the future-version and inferred cases do, and
       they exist. ``--only quality`` PASS 42. No file read outside the
       module.

   * - 3
     - **Command builder and pre-launch PIN check** (``tools.percolator``).
       P9-7, P9-8. Acceptance: argv asserted element by element for a probed
       set with and without XML, weights, seed and threads; no option without
       its capability (each removed in turn); ``-X`` present only with
       ``XML_OUTPUT`` *and* an enabled stage needing it; the PIN check over a
       real merged PIN passes and over the real zero-decoy PIN fails naming
       the decoy configuration; missing header, non-numeric feature, empty
       file each refused with their own message; real 3.07.1 and 3.09 runs of
       the built command on a real merged PIN write exactly the expected
       artefacts (and 3.09 no XML).
     - R-PERC-06, R-DEC-04; gates 2, 5 (adapter half)
     - **Signed off 2026-10-07** (``f255fd9``). Diff read: 17 files in
       ``cometgui-tools``. ``PercolatorCommands.build(PercolatorRequest)``
       emits every option through one check (its capability in the probed
       set), never sees a version, refuses without ``PSM_TSV_OUTPUT`` or
       ``PEPTIDE_TSV_OUTPUT``, requests ``-X`` only when capable *and* needed,
       never ``-Z`` (Phase 00: the converter fails on ``-X -Z`` without
       ``--import-decoys``, which Comet's internal decoys cannot use -- Phase
       12 to confirm), reports each requested option not emitted with its
       reason; fixed artefact names (``psms.tsv``, ``peptides.tsv``,
       ``decoy-psms.tsv``, ``decoy-peptides.tsv``, ``weights.txt``,
       ``pout.xml``); environment ``LANG=C.UTF-8`` only.
       ``PercolatorPinCheck`` reuses ``CometPinValidator`` (new
       ``validateBeforePercolator``: ``validate``'s rules plus two narrow,
       measured prefix rules) -- no second PIN parser. Real runs on a merged
       PIN made by the real Comet 2026.03.0 path (6472 rows, 3285 targets,
       3187 decoys): 3.07.1 with XML 1.5 s, ``pout.xml`` 3285 psm; 3.09 0.6 s,
       no XML option, no ``.xml``; the real zero-decoy PIN (2026.02.2
       fragment index, ``decoy_search = 1``: 198 targets, 0 decoys)
       reproduced and refused. ``mvn -pl cometgui-tools -am install`` with
       tests rc 0, fresh XML: domain 1157, process 274, tools **581**, 0
       failures, 0 ``BugInstance``. My PIT over every ``tools.percolator``
       class plus ``CometPinValidator``/``PinReader``: **166 KILLED, 3
       TIMED_OUT** (row-reading loops), 0 survived. My injections, landed
       and restored by ``sha256sum -c``: (1) valued options emitted without
       their capability -- 7 failures, ``PercolatorCommandsTest.eachRemovedInTurn``;
       (2) ``pout.xml`` requested whether or not a stage needs it -- 3
       failures incl. the real ``PercolatorCommandRealBinaryTest.percolator3071WithoutLimelight``.
       ``--only workflow`` **110** (455 s) and ``--only quality`` 42, both
       PASS. **Tests read outside the module** (all under ``scratch/``):
       ``phase05/artefacts`` Comet 2026.03.0 and 2026.02.2 and the 3.07.1
       zip, ``fixture`` K562 mzML x2 and the UniProt FASTA,
       ``percolator/3.09/`` wrapper, binary and Boost libraries.

   * - 4
     - **Output parsers and display filter values** (``results.parser``,
       ``results.filtering``). Acceptance: PSM and peptide TSV from the real
       3.07.1, 3.09 and 3.06.5 runs parse to row counts checked against an
       independent count; missing/NaN q-values counted, not dropped; weights
       split count read from the artefact -- a real three-split file, a
       constructed two-split and a constructed four-split file, a malformed
       and a missing file refused with their own messages; feature names and
       per-split values hand-checked; display filter defaults 0.01/0.01,
       inclusive at exactly 0.01, values 0 and 1 accepted, below 0 and above
       1 refused, PSM independent of peptide.
     - R-PERC-08 (parse), R-PERC-09; gates 1 (parse), 8; AC-RES-05
     - **Signed off 2026-10-07** (``f2ef533``). Diff read: 49 files, all in
       ``cometgui-results``; POM gains only its mutation switch.
       ``ResultTableReader`` (columns by header name, streaming, unknown
       q-values counted as their own category with status ``MISSING`` /
       ``UNPARSABLE`` / ``OUT_OF_RANGE``, original text kept),
       ``WeightsReader`` (split count read from the file; every split must
       name the same features), ``PercolatorOutputException``;
       ``filtering``: ``PsmQValueFilter``, ``PeptideQValueFilter``,
       ``DisplayFilters`` (defaults 0.01, ``[0, 1]``, inclusive), separate
       from ``TestFdr``/``TrainFdr``. **Fixtures:** real 3.07.1, 3.06.5 and
       3.09 output over CometGUI's own ``SyntheticPin`` (not D-006 data),
       each SHA-256 pinned, provenance in ``real/PROVENANCE.txt``; two- and
       four-split weights and an unknown-q table constructed, recorded in
       ``constructed/CONSTRUCTED.txt``. Version differences: identical
       headers in all three; 3.09 differs from 3.07.1 only in
       ``posterior_error_prob`` (I-spline PEP). ``mvn -pl cometgui-results
       -am install`` with tests rc 0: results **167** tests, 0 failures, 0
       ``BugInstance``; census 22 compiled = 22 in ``jacoco.xml``. Module
       PIT (configured run): 181 = 177 KILLED + 1 TIMED_OUT, 2 SURVIVED + 1
       NO_COVERAGE -- all three in the close-after-refused-open path (a
       handle leak at worst; no row dropped, no comparison inverted, no
       refusal suppressed): accepted as argued. My injections, landed and
       restored by ``sha256sum -c``: (1) filter range check accepting up to
       10 -- 4 failures, ``QValueFilterTest.outOfRange``; (2) the
       same-features-per-split check disabled -- 2 failures,
       ``WeightsReaderTest.refused:69``. ``--only quality`` PASS 42. No file
       read outside the module. **Residue:** ``.gitattributes`` has no
       ``-text`` rule for the pinned fixtures, so a Windows checkout with
       ``core.autocrlf=true`` would fail their SHA-256 checks (tier 1's
       file); the synthetic fixture has no q <= 0.01 row, so the boundary is
       proved on the constructed table and at real values 0.0588235 and
       0.166667.

   * - 5
     - **The Percolator workflow steps** (``workflow.steps``, ``workflow.state``
       only if the plan target changes). ``resolve-percolator``,
       ``run-percolator``, ``parse-percolator``; P9-8, P9-9, P9-10.
       Acceptance: a real Comet run on the Phase 08 fixture followed by
       Percolator 3.07.1 with Limelight enabled writes PSM, peptide, weights
       and XML, all parsed and recorded, argv recorded with ``-X``; the same
       with 3.09 and Limelight disabled writes no XML and its recorded argv
       has no XML option; the real zero-decoy PIN fails ``run-percolator``
       with zero launches; ``percolator.seed`` in ``provenance.json`` of every
       run, including a failed one; the selection reason and skipped
       versions in provenance; raw outputs read-only after success and their
       hashes equal before and after parsing and provenance finalisation.
     - R-PERC-05, 07, 08, 10, 11; gates 1, 2, 4 (provenance), 5, 7, 9;
       AC-RES-06, AC-RES-07, AC-PRV-10
     - **Signed off 2026-10-08** (``88a5a8e``, ``2459a08``, ``3276e30``,
       ``5579fa2``). Diff read: 20 files, ``cometgui-workflow`` plus
       ``docs/reference/project_format.rst`` and ``provenance_format.rst``.
       ``PercolatorSelection``/``PercolatorChoice`` (``xmlNeeded()`` read from
       the stage table), ``SearchRequest.withPercolator``,
       ``CometWorkflow.planFor(mode, percolator)``; the three steps in
       ``PercolatorSteps`` (resolve re-hashes the binary; run checks the
       settings file's hash, then ``PercolatorPinCheck``, then one
       ``invoke`` with stage id ``percolator``, then every listed artefact
       present and non-empty, nothing unlisted in ``outputs/percolator``,
       outputs made read-only; parse through the one table, weights and pout
       readers); ``percolator-settings.json`` written once through the one
       JSON and atomic writer; settings keys in ``PercolatorProvenance``,
       fixed at prepare time so a failed run carries them. ``mvn -pl
       cometgui-workflow install`` with tests (upstream installed first):
       **645** tests, 0 failures, 0 ``BugInstance``. App run tests green
       (agent: RealRunUiTest 5, RealCancelUiTest 1, RunReadinessUiTest 2).
       Real runs (agent's figures; the tests re-ran in my suite): 3.07.1 with
       Limelight -- 3285 PSM rows = PIN targets, 3187 decoy PSM rows, 2482
       peptides, 3 weight splits, pout 3285 psm, ``-X`` in the recorded
       argv; 3.09 -- no ``-X`` in the recorded argv, no ``.xml``, and with
       Limelight on, ``not-emitted.01.option=-X`` with the ``XML_OUTPUT``
       reason; the real zero-decoy PIN refused by ``run-percolator`` with no
       launch; ``percolator.seed`` in every run including both failed ones.
       My PIT over ``org.cometgui.workflow.steps.Percolator*``: 99 = 55
       KILLED + 43 TIMED_OUT, 0 SURVIVED, 1 NO_COVERAGE (the DOS read-only
       branch, never run on Linux). My injections, landed and restored by
       ``sha256sum -c``: (1) outputs never made read-only -- 2 failures incl.
       ``RealPercolatorRunTest.gate9RawOutputsAreUnchangedAndReadOnly:677
       decoy-peptides.tsv is writable``; (2) the pre-launch PIN check
       bypassed -- 4 failures incl. ``RealPercolatorRunTest.gate5TheRealZeroDecoyPin``
       (Percolator launched and exited 1 instead of the decoy-configuration
       refusal). ``--only workflow`` **110** (464 s), ``--only quality`` 42,
       ``--only docs``, ``--only traceability`` 8 -- all PASS. **Residue:**
       ``finalise-provenance`` is ordered only after ``merge-pin`` when
       ``finalise-results`` (Phase 10) is unplanned, so the rerun preview
       reuses it after a Percolator change -- harmless today
       (``provenance.json`` is written at attempt end), pinned by a test,
       restored when Phase 10 plans ``finalise-results``; ``run.json`` does
       not record the Percolator half (no retry across a restart, as for
       Comet); 43 of 99 mutants in the steps time out rather than being
       killed (the engine's waits) -- relevant to the owner's ``TIMED_OUT``
       question, and ``workflow.steps`` is not a POM PIT target. **Tests read
       outside the module:** ``scratch/phase05/artefacts`` (Comet x2, 3.07.1
       zip), ``scratch/fixture`` (K562 x2, FASTA), ``scratch/percolator/3.09``.

   * - 6
     - **Compatible-version rerun** (``workflow.steps``, ``workflow.storage``,
       ``domain.run``; ``docs/reference/project_format.rst``). P9-11.
       Acceptance: after a real 3.09 run, the rerun with 3.07.1 produces a
       second run whose execution record has a different version, binary
       checksum and argument array; Comet launched zero times; the merged PIN
       re-hashed and equal; the original run directory's tree hash unchanged;
       a changed merged PIN refuses the rerun naming the file and both
       hashes; the original's raw Percolator outputs byte-identical.
     - R-RUN-02, R-RUN-06, the spec's *Stage reruns*; gates 6, 9
     -

   * - 7
     - **The Percolator section and wiring** (``cometgui-ui``,
       ``cometgui-app``). P9-12. Acceptance, driven through the real
       interface: toggling Limelight changes the selected default and shows
       the notice naming both versions; the skip reason names 3.09 and
       ``XML_OUTPUT``; a manifest with no XML-capable build shows Limelight
       unavailable with both remedies; advisories visible at selection;
       Advanced shows only options the selected build supports; the rerun
       action produces the second run; registration is offered where no
       managed XML-capable build exists; a real run uses the selected
       version.
     - R-PERC-01, 03, 04, 10, 11; gates 3, 4 (UI), 6 (UI)
     -

   * - 8
     - **Documentation and traceability.** ``docs/percolator.rst``,
       ``docs/reference/percolator_options.rst``,
       ``docs/developer/version_capabilities.rst`` written as built;
       ``docs/developer/workflow_engine.rst`` updated; the traceability map
       for AC-RES-05, 06, 07 and AC-PRV-10.
     - R-DOC; all items (description)
     -

   * - 9
     - **Falsifiability harness** ``scripts/verify-percolator-gates.sh``
       (P9-14), registered as ``percolator``.
     - all nine items
     -

Rejections and rework
=====================

None yet.

Deferred
========

None yet.

Blockers escalated
==================

#. **Percolator posts usage analytics by default** (``GoogleAnalytics::
   postToAnalytics`` in the 3.07.1 binary; ``--no-analytics`` in 3.07.1 and
   3.09). Every Percolator run CometGUI starts -- including every probe and
   every test -- may contact Google Analytics. Not acted on: passing
   ``--no-analytics`` is a product decision, and under ``R-PERC-06`` it would
   need its own probed capability. Recommendation for tier 1: decide whether
   the product passes it; if yes, it is one capability constant, one probe
   observable and one builder line.
