.. _dev-version-capabilities:

====================
Version capabilities
====================

How CometGUI decides what a Percolator build can do, which build a run uses,
and what that build is passed -- **as built** in Phase 09. In one line: a
capability is *observed by running the build*, never read from its version
number or its help text; resolution and command construction read only
observed capabilities; and a version number only orders, names and records.

.. note::

   **State after Phase 09 -- Percolator Adapter and Version Capabilities**,
   written on 2026-10-08 against the tree as committed. The design decisions
   cited as ``P9-n`` are in ``handoffs/PHASE-09-worklog.rst``, with every work
   unit's sign-off. Every measured number below was produced on this
   project's Debian 12, x86-64 host; nothing in this page has run on Windows
   or macOS (see `What has run, and where`_). Amended in Phase 10 for
   ``D-013`` (decided 2026-10-08: CometGUI always passes ``--no-analytics``
   where the probe observes it). ``specification.rst``
   (*Percolator versions and artefact availability*, ``R-PERC-01``..\ ``12``)
   is the authority on what is required; where the build differs, this page
   says so.

The user's view of the same machinery is :doc:`../percolator`; the options
themselves are tabulated in :doc:`../reference/percolator_options`; the
Percolator workflow steps and the compatible-version rerun are in
:doc:`workflow_engine`.

.. contents:: Contents
   :depth: 2
   :local:

Where the code is
=================

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Package (module)
     - What it holds

   * - ``org.cometgui.domain.tools`` (``cometgui-domain``)
     - ``ToolCapability`` (twelve Percolator constants), ``CapabilityEvidence``
       and ``DeclaredCapability`` (a claim with its evidence), ``ToolOffer``
       (what the Tool Manager offers, with its advisories), ``ToolVersion``.

   * - ``org.cometgui.tools.percolator`` (``cometgui-tools``)
     - The one capability probe (``PercolatorCapabilityProbe``, with
       ``ProbeArtefacts`` and the ``SyntheticPin`` fixture); the banner and
       version reading (``PercolatorBanner``); local-binary registration
       (``LocalPercolatorRegistration``); ``PercolatorOption`` and
       ``PercolatorArtefact`` (spellings, capabilities and file names, once);
       the command builder (``PercolatorCommands``, ``PercolatorRequest``,
       ``PercolatorCommand``, ``NotEmitted``); the pre-launch PIN check
       (``PercolatorPinCheck``); the pout-XML reader (``PoutDocument``). Pure
       of the engine. A PIT target.

   * - ``org.cometgui.install.cache`` (``cometgui-install``)
     - The completion marker's ``capabilityProbeGeneration`` and the
       ``CAPABILITIES_FROM_AN_EARLIER_PROBE`` state (`The re-probe rule`_).

   * - ``org.cometgui.params.percolator`` (``cometgui-params-percolator``)
     - Domain-only, no file, process or thread. ``PercolatorSettings`` and
       ``EffectiveSeed``; ``.schema`` -- ``PercolatorSetting`` (each setting's
       one capability, label and description) and ``SettingsApplicability``;
       ``.validation`` -- ``TestFdr`` and ``TrainFdr``; ``.resolution`` --
       ``DownstreamStage``, ``PercolatorResolver``, ``PercolatorResolution``,
       ``SkippedVersion``, ``MissingCapability``, ``StageAvailability``,
       ``StageRemedy``, ``ResolutionChange``, ``ResolutionMessages``,
       ``AdvisoryRendering``. PIT and coverage gates on.

   * - ``org.cometgui.results.parser``, ``.filtering`` (``cometgui-results``)
     - The PSM/peptide table reader and the weights reader (the parsers' one
       home, which Phase 10 builds on); the display q-value filter *values*
       (``PsmQValueFilter``, ``PeptideQValueFilter``, ``DisplayFilters``).
       Phase 10 added the result store, the weights summary and export
       (:doc:`results_model`).

   * - ``org.cometgui.workflow.steps`` (``cometgui-workflow``)
     - ``PercolatorSelection``, ``PercolatorChoice``, ``PercolatorRun``,
       ``PercolatorSteps`` (the three step actions), ``PercolatorProvenance``
       (every provenance key), ``PercolatorSettingsFile``, and the derived
       run (``PercolatorRerun`` and friends). See :doc:`workflow_engine`.

   * - ``org.cometgui.ui.viewmodel.percolator``,
       ``org.cometgui.ui.controls.percolator`` (``cometgui-ui``)
     - The Percolator section: ``PercolatorViewModel``,
       ``PercolatorRerunViewModel``, their ports, and ``PercolatorPane``.
       Since Phase 10 the section's display filters are the interface's one
       ``DisplayFiltersViewModel``, shared with the Results section, to which
       ``PercolatorViewModel`` delegates.

   * - ``org.cometgui.app.config`` (``cometgui-app``)
     - Wiring: ``ToolManagerWiring`` gives the installer and registration the
       one probe; ``ToolManagerPercolatorPort``; ``WorkflowRunPort`` builds
       ``SearchRequest.withPercolator`` and the rerun.

The capability model
====================

Twelve Percolator capabilities
------------------------------

Each capability is one thing a run may ask of a build, and each is
established by its own probe run with its own observable (P9-4):

.. list-table::
   :header-rows: 1
   :widths: 26 26 48

   * - Capability
     - Probe run (options)
     - Observable that grants it
   * - ``XML_OUTPUT``
     - ``-X <file>``
     - The file parses as ``percolator_out/15`` and holds exactly 64
       ``<psm>`` elements. The exit code is deliberately not consulted: an
       aborted run exits 1 *and* leaves a zero-byte file.
   * - ``XML_DECOY_OUTPUT``
     - ``-X <file> -Z``
     - As above with 128 ``<psm>`` elements, both decoy values present.
   * - ``PSM_TSV_OUTPUT``
     - ``--results-psms <file>``
     - The file's header names all six result columns and exactly 64 rows
       follow, every one a target (by the fixture's protein prefix).
   * - ``PEPTIDE_TSV_OUTPUT``
     - ``--results-peptides <file>``
     - As above.
   * - ``DECOY_OUTPUT``
     - ``--decoy-results-psms <f1> --decoy-results-peptides <f2>``
     - Both files, 64 rows each, every row a decoy. One capability, so both
       are required.
   * - ``WEIGHTS_OUTPUT``
     - ``--weights <file>``
     - At least one cross-validation bin, each bin's header naming every
       fixture feature and its two rows that many finite numbers.
   * - ``SEED_OPTION``, ``THREAD_OPTION``, ``TEST_FDR_OPTION``,
       ``TRAIN_FDR_OPTION``, ``MAX_ITERATIONS_OPTION``
     - one run each: ``--seed 1``, ``--num-threads 3``, ``--testFDR 0.01``,
       ``--trainFDR 0.01``, ``--maxiter 10``
     - Accepted on a run that **completed**: exit 0 **and** the full
       64-row target peptide table on standard output. Each value is
       Percolator's documented default, so the option is the only difference
       from a default run.
   * - ``NO_ANALYTICS_OPTION``
     - ``--no-analytics``, alone -- the probe's **first** run
     - As for the five options above. Once observed, every later run of the
       probe carries ``--no-analytics`` too (see below).

**Why twelve runs, not one.** Real Percolator refuses an unknown option
outright -- banner, ``Exception caught``, exit 1, no output -- so in a combined
run one unsupported option would take every other capability down with it.
Separate runs make a build that rejects one option lose exactly that
capability (``PercolatorCapabilityProbeTest`` drives fake binaries that reject
one option each). The cost was measured at about 5.3 s for 3.06.5, 5.8 s for
3.07.1 and 3.8 s for 3.09 with eleven runs (2026-10-07), and 5.9 s, 6.4 s and
4.3 s with twelve (2026-10-08, the last including registration), paid once
per install or registration.

**No analytics, from the first run** (``D-013``). Percolator posts usage analytics to
Google on every run unless it is given ``--no-analytics``, and CometGUI always
gives it -- including on the probe's own runs. So the probe's first run is
``NO_ANALYTICS_OPTION``'s, and once that is observed every later run carries
``--no-analytics`` as its last option before the fixture
(``PercolatorCapabilityProbeTest.noAnalyticsIsObservedFirstAndThenAlwaysPassed``;
against the real 3.07.1 and 3.09, every launch is captured at the process
seam: ``PercolatorRealBinaryTest.noAnalyticsOnEveryLaunchOf3071`` and
``noAnalyticsOnEveryLaunchOf309``). A build that refuses it loses that one
capability and nothing else, and its later runs go without it
(``aBuildRefusingNoAnalytics``); the refused run cannot itself have posted
anything, because Percolator refuses an unknown option before doing any work.
It adds nothing the other runs' verdicts depend on: it is neither an output
nor a scoring parameter.

**The fixture** is ``SyntheticPin``: 64 target and 64 decoy rows, a fixed
seed, numbers formatted under ``Locale.ROOT``, its bytes pinned by
``SyntheticPinTest``. 64+64 is the size ``R-PERC-02`` (revision 11) records as
measured: 8+8 aborted 10 times in 50 seeds. **Known limit:** its 128 peptides
are all distinct, so the probe cannot tell a PSM table from a peptide table.

**What "no answer" is.** Every probe run must print Percolator's version
banner (on standard error). A run that does not -- a loader failure, a missing
shared library -- makes the probe **throw**, and a run that times out does
too; it never returns an empty or partial set (``R-TOOL-08``: an empty set is
positive evidence of absence). ``PercolatorRealBinaryTest.aLoaderFailureIsNotAnAbsence``
holds this against the real 3.09 ``.deb`` binary, which exits 127 for want of
``libboost_filesystem.so.1.83.0``.

**What it is not**: ``--help`` parsing. The ``noxml`` and ``XML_SUPPORT=ON``
builds of 3.07.1 print byte-identical help text
(:ref:`dev-tool-registry-functional-probe`,
``PercolatorRealBinaryTest.helpTextIsNotAProbe``).

Observed and inferred evidence
------------------------------

A capability reaches the product as a ``DeclaredCapability``: the capability
plus its ``CapabilityEvidence`` -- ``observed-by-execution``,
``inferred-from-artefact-bytes`` or ``unverified``. Only the first
``isObserved()``. Where each comes from:

* **An installed managed build** carries the probe's set, read from its
  completion marker, every capability ``observed-by-execution``
  (``ManagedToolManager``).
* **A registered local binary** carries the probe's set from its
  registration; there is no manifest row to inherit from.
* **A build not installed** carries the manifest's declaration. The manifest
  declares only ``XML_OUTPUT`` and ``XML_DECOY_OUTPUT`` for Percolator, with
  their evidence (``observed-by-execution`` for Linux 3.07.1 and 3.06.5 and
  Windows 3.07.1's ``XML_OUTPUT``; ``inferred-from-artefact-bytes`` for the
  rest; nothing for 3.09). The tab-separated, weights and option capabilities
  exist only after a probe.

``PercolatorResolver.observedCapabilities(offer)`` is the one place that
filters a claim set down to its observed members. Resolution, the command
builder (through ``PercolatorSelection``), the Advanced settings' "supported"
state and provenance's ``percolator.capabilities`` all read that set and
nothing else (``R-TOOL-08``). An unobserved claim is not silently dropped
either: the messages name it, its evidence, and -- for a build not installed
-- that installing it will probe it.

The re-probe rule
-----------------

Completion markers written before Phase 09 hold only the two XML
capabilities, and read naively they would make an installed 3.07.1 lose its
tab-separated output. So the marker records ``capabilityProbeGeneration``
(``InstallationMarker.CAPABILITY_PROBE_GENERATION``, now **3**; a marker
without the field is generation 1, Phase 09's probe wrote 2, and Phase 10
bumped it to 3 because the probe now also establishes
``NO_ANALYTICS_OPTION`` -- a believed generation-2 marker would leave every
installed build posting analytics), and ``ToolCache.verify`` reports a marker
from an older generation -- after every checksum has matched -- as
``CAPABILITIES_FROM_AN_EARLIER_PROBE``. That state is *not installed*: the
build is offered as installable, and the next install rebuilds the directory
and re-runs the probe, at the cost of a re-download (``R-TOOL-07``: the probe
wins). The rule names no tool and no version; bumping the constant is how a
later probe that establishes more makes every earlier record be re-confirmed.
A registered local binary is probed afresh at every registration and is not
persisted, so it needs no such rule.

Resolution: *latest compatible*
===============================

``PercolatorResolver.resolve(offers, enabledStages)`` is ``R-PERC-02``. Pure:
it reads the Tool Manager's offers and the enabled downstream stages and
returns a ``PercolatorResolution``.

**Candidates.** An offer that is ``INSTALLED`` (managed or local), or a
*managed* offer that is ``NOT_INSTALLED`` or ``INSTALLING``. ``INSTALLING``
counts because it is the same build as the ``NOT_INSTALLED`` offer a moment
earlier; excluding it would make the default jump away during the install and
back afterwards (a deviation from P9-5's wording, accepted at unit 2's
sign-off). ``UNAVAILABLE_ON_THIS_PLATFORM``, ``HOST_REQUIREMENTS_NOT_MET`` and
``FAILED`` offers are never selected; they are kept and named when they are
why a stage is unavailable.

**Preference order**, total: version, newest first; then managed before a
registered local binary of the same version (attributable to a pinned
artefact, ``R-PERC-01``); then ``INSTALLED`` before ``INSTALLING`` before
``NOT_INSTALLED``; then the order the Tool Manager gave, which is stable.

**The default** is the most preferred candidate satisfying the most enabled
stages. With one stage that is "the newest candidate satisfying every enabled
stage, or the newest candidate when none does" -- so with no observed
XML-capable build the default is the newest non-XML one and the stage is
reported unavailable, never "no Percolator". No candidate at all selects
nothing and says so in a sentence; it does not throw.

The stage requirement table
---------------------------

``DownstreamStage`` is the table, held in one place (P9-6). It has one row:
``LIMELIGHT_CONVERSION`` (``limelight-conversion``) needs ``XML_OUTPUT``,
because the Limelight converter reads pout XML, and offers two
``StageRemedy`` values in order: ``register-local-binary`` and
``convert-on-supported-platform`` (``R-PERC-03``).

Rescoring itself is **not** a resolution criterion. A build that is not
installed has no observed tab-separated capability to resolve on, so the
command builder checks ``PSM_TSV_OUTPUT`` and ``PEPTIDE_TSV_OUTPUT`` at run
time and refuses with a named reason. ``PercolatorChoice.xmlNeeded()`` reads
the same table, so "does this run need ``-X``" has one answer.

Skipped versions, availability and change notices
-------------------------------------------------

* **Skipped versions** (``R-PERC-10``): every candidate strictly newer than
  the selection, each a ``SkippedVersion`` with its ``MissingCapability``
  list (capability, stage, and the evidence of an unobserved claim) and its
  sentence. A candidate of *equal* version passed over is not "newer" and is
  not listed. Excluded offers are never "skipped"; they appear in the
  unavailability explanation instead.
* **Availability**: one ``StageAvailability`` per stage, enabled or not, with
  its explanation and remedies when unavailable -- so the interface can show
  Limelight unavailable before the scientist switches it on.
* **Change notices**: ``ResolutionChange.between(before, after)`` says whether
  the default changed, from what to what, and why (a stage switched on or off,
  or the available builds changed).
* **Messages in one place**: every sentence -- the selection reason, each
  skip, each unavailability, each change -- is built by
  ``ResolutionMessages``, so the interface and ``provenance.json`` say the same
  thing in the same words. A version is named by ``ToolVersion.text()`` as
  upstream wrote it, a local binary as ``3.09 (registered local binary)``, and
  a capability by its identifier.

**Advisories** (``R-PERC-11``) are manifest data carried on the offer
(``ToolOffer.advisories``). ``AdvisoryRendering`` shows them at selection
(``forSelection``) and records them under ``percolator.advisory.<id>``
(``forProvenance``); nothing decides in code which version has which
advisory.

Settings and the command
========================

**Settings.** ``PercolatorSettings`` holds ``testFdr`` and ``trainFdr`` as
their own types (``TestFdr``, ``TrainFdr``; ``BigDecimal`` in ``(0, 1]``,
never a display filter type -- ``AC-RES-05``), the random seed (1..20000,
default 1, ``R-PERC-05``), maximum iterations (1..1000, default 10) and thread
count (1..128, default 3). Each ``PercolatorSetting`` names its one required
capability; ``SettingsApplicability`` splits the settings for a build's
observed set, and ``EffectiveSeed`` is the configured seed plus whether it is
passed (``not-passed`` otherwise). The settings file a run archives is
described in :doc:`../reference/project_format`.

**The command builder.** ``PercolatorCommands.build(PercolatorRequest)`` is
pure and never sees a version: the request carries the executable, the merged
PIN, the output directory, the **probed** capability set, ``xmlNeeded`` and
the valued options' text. Every option comes from ``PercolatorOption`` and
goes through one test -- is ``option.capability()`` in the probed set? -- in
``Builder.accepts``. Then:

* no ``PSM_TSV_OUTPUT`` or ``PEPTIDE_TSV_OUTPUT``: ``PercolatorRefusedException``
  naming each missing capability, before anything runs;
* anything else requested and not supported is left out and reported as a
  ``NotEmitted`` (option and sentence), which provenance records under
  ``percolator.not-emitted.<nn>.*``; a missing weights file additionally
  produces the ``R-PERC-08`` warning;
* ``-X`` only when ``xmlNeeded`` *and* ``XML_OUTPUT``; ``-Z`` never;
* ``--no-analytics`` on every run whose build has ``NO_ANALYTICS_OPTION``,
  as the last option before the merged PIN -- not a setting, never turned off
  (``D-013``). A build without it is **not refused**: the option is left out
  and its ``NotEmitted`` sentence, recorded in provenance, says analytics
  could not be switched off for that build;
* the environment is exactly ``LANG=C.UTF-8`` and the working directory is
  the output directory.

The exact argument array, the file names and what is never passed are in
:doc:`../reference/percolator_options`. The *R-PERC-08* stdout fallback
parser the specification permits is not built: on a build without
``WEIGHTS_OUTPUT`` the run records the warning and has no weights.

**The pre-launch PIN check** (P9-8, ``R-DEC-04``). ``PercolatorPinCheck``
refuses a merged PIN that is missing, not a regular file or empty, then hands
it to the one PIN reader through ``CometPinValidator.validateBeforePercolator``
(required header fields, finite numeric features, both target and decoy rows,
labels not contradicting the decoy prefix). Every refusal says Percolator was
not started and names the file. It runs inside ``run-percolator`` before the
one launch, every time -- including in a derived run, which runs no Comet.

Capability, never version number (P9-2)
=======================================

No product code branches on a Percolator version number. The only permitted
uses of a Percolator ``ToolVersion`` are:

#. ordering candidates to find "newest" (``PercolatorResolver``'s
   preference order);
#. the ``>= 3.05`` registration floor
   (``LocalPercolatorRegistration.MINIMUM_VERSION``, a numeric comparison, so
   ``3.10`` is above ``3.05``);
#. display and recording (labels, messages, provenance).

Every option emitted, every artefact expected, every stage-availability
decision and every message reads a probed ``ToolCapability``.

**How that is held**, honestly: there is no static architecture rule for it.
It is held by tests that a version rule would fail and a capability rule
passes -- on the real Linux pair (3.07.1 and 3.09) the two rules agree, so the
discriminating tests are the synthetic ones: an unreleased ``3.10`` with
observed ``XML_OUTPUT`` wins with no code change
(``PercolatorResolverTest.futureWithXmlWins``), a ``3.10`` without it is
skipped (``futureWithoutXmlSkipped``), and a build whose XML is only inferred
is not counted (``macosInferred``, ``futureInferredSkipped``) -- and by the
sign-off rule that every unit's reviewer injects a version-number branch into
production code and watches tests go red (recorded per unit in the work log).

Which tests prove the exit gate
===============================

.. list-table::
   :header-rows: 1
   :widths: 6 34 60

   * - Gate
     - What
     - Tests (real binaries, Linux only, unless marked *unit* or *UI*)

   * - 1
     - A real run on the real merged PIN writes PSM, peptide and weights
       artefacts that parse
     - ``org.cometgui.workflow.steps.RealPercolatorRunTest.gate1And2WithAnXmlCapableBuild``;
       ``org.cometgui.results.parser.RealPercolatorOutputTest.everyRealTableParses``
       and ``realWeights``

   * - 2
     - XML-capable: XML produced and parsed; 3.09: no XML option in the
       recorded argument array, none expected
     - ``RealPercolatorRunTest.gate1And2WithAnXmlCapableBuild``,
       ``gate2ThreeNineWithoutLimelight``, ``gate2ThreeNineChosenWithLimelight``,
       ``anXmlCapableBuildWithoutLimelightWritesNoXml``;
       ``org.cometgui.tools.percolator.PercolatorCommandRealBinaryTest``
       (``percolator3071WithXml``, ``percolator309WithoutXml``,
       ``percolator3071WithoutLimelight``); *unit*:
       ``PercolatorCommandsTest`` (``fullSetWithXml``,
       ``capableButNotNeeded``, ``neededButNotCapable``, ``eachRemovedInTurn``)

   * - 3
     - Default resolved, not hard-coded; toggling re-evaluates and tells the
       user; no XML-capable build gives a non-XML default and Limelight
       unavailable
     - *unit*: ``org.cometgui.params.percolator.resolution.PercolatorResolverTest``
       (``limelightOn``, ``limelightOff``, ``only309``, ``macosInferred``,
       ``futureWithXmlWins``), ``ResolutionChangeTest`` (``switchedOn``,
       ``switchedOff``, ``switchedOnButUnavailable``); *UI*:
       ``org.cometgui.app.gui.PercolatorSectionUiTest.gate3TheSwitchMovesTheDefault``,
       ``PercolatorUnavailableUiTest.limelightUnavailable``; the real probe
       verdicts behind them: ``PercolatorRealBinaryTest``
       (``theRealBinaryIsFullyCapable``, ``theOldestManagedBinary``,
       ``percolator309HasNoXml``)

   * - 4
     - A skipped newer version is named with the missing capability, in the
       UI and in provenance
     - ``RealPercolatorRunTest.gate4TheSkippedVersionIsRecorded``; *unit*:
       ``PercolatorResolverTest.limelightOn``; *UI*:
       ``PercolatorSectionUiTest.gate4TheSkipReasonAndTheAdvisories``

   * - 5
     - A zero-decoy PIN fails before Percolator is launched
     - ``RealPercolatorRunTest.gate5TheRealZeroDecoyPin`` (the real Comet
       2026.02.2 fragment-index PIN: 198 targets, 0 decoys; launches counted
       around the real process service);
       ``PercolatorCommandRealBinaryTest.theRealZeroDecoyPinIsRefused``;
       *unit*: ``PercolatorPinCheckTest.zeroDecoys``,
       ``org.cometgui.workflow.steps.PercolatorStepTest.aPinWithoutDecoysIsRefusedBeforeLaunch``

   * - 6
     - The compatible-version rerun: a second execution record with another
       version, checksum and argument array; Comet untouched
     - ``org.cometgui.workflow.steps.RealPercolatorRerunTest.gate6TheSecondExecutionRecord``;
       *unit*: ``PercolatorRerunTest``; *UI*:
       ``org.cometgui.app.gui.PercolatorRerunUiTest.gate6TheRerun``

   * - 7
     - The effective seed in provenance for every run
     - ``RealPercolatorRunTest.gate7TheSeedOfEveryRun`` (four successful
       runs) and ``gate5TheRealZeroDecoyPin`` (two failed runs); *unit*:
       ``PercolatorStepTest.omissionsAreRecorded`` (``not-passed`` for a
       build without ``SEED_OPTION``)

   * - 8
     - The weights split count read from the artefact; two- and three-split
       files parse
     - *unit*: ``org.cometgui.results.parser.WeightsReaderTest`` (``twoSplits``,
       ``fourSplits``, and the refusals); ``RealPercolatorOutputTest.realWeights``
       (real three-split files)

   * - 9
     - Raw outputs byte-identical across filtering and export
     - ``RealPercolatorRunTest.gate9RawOutputsAreUnchangedAndReadOnly``;
       ``org.cometgui.results.filtering.RealOutputFilteringTest.rawFilesUntouched``;
       ``RealPercolatorRerunTest.gate9TheOriginalIsUntouched``. At Phase 09
       export did not exist; what those prove is parsing, filtering values,
       provenance finalisation and a derived run. Phase 10 added export and
       proves the raw outputs byte-identical across it (its gate item 4; see
       :ref:`dev-results-model`), and re-proved this item across
       ``finalise-results``.

The acceptance criteria's entries are in ``docs/traceability-map.toml`` and
the generated :doc:`traceability`. The falsifiability harness for these items
is ``scripts/verify-percolator-gates.sh`` (Phase 09 unit 9; ``percolator`` in
``scripts/verify-all-gates.sh``): it injects a defect per item into
production code and requires the tests above to go red with their own words.
Three of its controls are version-blind, and under each the real 3.07.1/3.09
pair stays green -- the measurement that only the future-version and
inferred-claim tests hold P9-2. See :ref:`dev-percolator-falsifiability`.

What has run, and where
=======================

**Only Linux x86-64.** Every real-binary test is ``@EnabledOnOs(LINUX)``, fails
rather than skips when a fixture is missing, and checks each binary's SHA-256
before use. What has executed:

* **3.07.1 and 3.06.5**: the managed Linux portable ``noxml`` binaries,
  probed to all twelve capabilities, and 3.07.1 run end to end on a merged PIN
  from the real Comet path (Comet 2026.03.0, the two ``D-006`` K562 mzML files,
  the proteome's first 1000 records: 3285 target and 3187 decoy PIN rows,
  3285 PSM rows, 2482 peptides, 3 weight splits of 22 features, a 3285-PSM pout
  XML).
* **3.09**: there is no installable Linux 3.09 (``D-003``), so it runs as a
  **registered local binary**: the upstream ``.rpm``'s executable with the
  ``libboost_filesystem`` and ``libboost_system`` 1.66 libraries from CentOS
  8.5 packages beside it, behind the wrapper
  ``scratch/percolator/3.09/run-percolator-3.09.sh`` (the registered file,
  and so the one whose SHA-256 provenance records). It prints ``Percolator
  version 3.09.0`` under an empty environment, probes to the ten non-XML
  capabilities, and rejects ``-X``. These fixtures are gitignored scratch; the
  tests that need them name how to rebuild them.
* **No Windows or macOS Percolator was executed by this phase.** The DOS
  read-only branch of ``PercolatorSteps.makeReadOnly`` has never run (it is
  the one ``NO_COVERAGE`` mutant in unit 5's PIT run); Rosetta 2, the Windows
  Visual C++ runtime and the ``LANG=C.UTF-8``-only environment on those
  platforms are unverified. Every non-Linux capability is the manifest's
  claim, carried with its evidence until an install there probes it.

Known limits, recorded rather than hidden:

* ``--no-analytics`` (``D-013``) is passed on every probe run after the first
  -- and on the first, which is its own -- and on every search and rerun,
  wherever the probe observed it; ``RealPercolatorRunTest.everyRealRunPassesNoAnalytics``
  and ``RealPercolatorRerunTest`` read it back from ``provenance.json``. Two
  Percolator invocations are outside that rule: the version read, which runs
  ``--help`` before any probe can say whether the build accepts the option,
  and the handful of direct runs inside ``PercolatorRealBinaryTest`` that test
  the binary rather than the product. Whether ``--help`` posts analytics has
  not been measured (the host has no system-call tracer).
* The probe's fixture cannot distinguish a PSM table from a peptide table.
* The PIT mutants in ``workflow.steps`` that time out (unit 5) appear to come
  from real-binary test setup exceeding PIT's per-test timeout rather than
  from hangs; ``workflow.steps`` is not a POM PIT target.
