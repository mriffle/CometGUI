===============================================
PHASE-07 work log -- Comet Parameter Editor UI
===============================================

:Phase: 07
:Phase orchestrator: tier-2 phase orchestrator, dispatched by tier 1 session 10
   at ``c9b47ea`` (brief ``handoffs/PHASE-07-BRIEF.rst``)
:Started: 2026-10-05

Maintained by the phase orchestrator as the phase runs. One row per work unit.
A unit is not done until it carries a sign-off entry naming what was run and
what was observed -- "agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status`` clean at ``c9b47ea`` on ``main``. **No baseline build was
taken**, by the owner's build-economy rule (brief, *Build economy*). One
narrow check was run to learn the sign-off command's cost and to know the
module is green before anything changes:

* ``mvn -B -o -q -pl cometgui-ui -am verify -Dtest='org.cometgui.ui.**'
  -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.coverage.core.skip=true``
  -- rc 0 in 2 min 40 s; ``cometgui-ui`` 19 report files, 200 tests, 0
  errors, 0 failures, 0 skipped; ``cometgui-ui/target/site/jacoco/jacoco.xml``
  written. (``-Dcometgui.coverage.core.skip=true`` only because the filter
  runs no upstream test, so their core coverage rule would read zero; the
  ``cometgui-ui`` view-model rule is a different switch and stays on.)

Design decisions, made before the first dispatch
================================================

Each unit is briefed with these. A unit that finds one wrong reports it; it
does not quietly diverge.

P7-1 -- the model is the source of truth
    View-models hold a ``CometParameters`` and change it only through the
    model's own operations (``withText(name, text, USER)``, ``withValue``,
    ``resetToDefault``, ``withEnzymeTable``, ``withoutUnknown``, presets'
    ``applySelected``, ``SchemaMigration``). A refused edit's own message is
    what the field shows. Every finding shown anywhere comes from one
    model-layer ``ValidationReport``. No rule, number parsing, tuple splitting
    or residue test is written in ``org.cometgui.ui``. If the editor needs
    something the model does not offer, the gap is a change to
    ``cometgui-params-comet`` with its own tests and ``--only params``.

P7-2 -- everything is per release
    Field view-models are built from ``CuratedMetadata.parameter(name,
    version)`` for the session's selected release; choices, defaults, help,
    inline comments and the residue alphabet are that release's.
    2026.03.0 is the default, 2026.02.2 is offered; 2024.01.0 is a migration
    fixture and is never offered. The offered set is passed in by the
    composition root, not hard-coded in a view-model. Changing release
    migrates the current set (``SchemaMigration``) and puts its report up for
    review. Every version-dependent control has at least one test that runs
    on both releases.

P7-3 -- an unresolved ``NEEDS_ATTENTION`` blocks Run
    Represented in the model layer (unit 1), surfaced as an ERROR finding at
    its parameter and category in the same ``ValidationReport`` as every other
    finding, so it blocks Run, is attached to its field, appears in the
    summary and is keyboard reachable exactly as any error is.

P7-4 -- packages
    Toolkit-free view-models in ``org.cometgui.ui.viewmodel.params``; views
    in ``org.cometgui.ui.view.params``; custom controls in
    ``org.cometgui.ui.controls.params``. The view-model independence scan and
    the view-model coverage rule must reach the subpackage (strengthened, not
    narrowed, where they do not).

P7-5 -- stable identifiers
    Every control a test reaches has an id from ``UiIds``, pinned as a
    hand-typed literal in ``StableIdentifierPinTest``. GUI tests look controls
    up by hand-typed literals, never by calling ``UiIds``.

P7-6 -- Run
    The Run section gets a Run control whose disabled state and reason text
    come from a run-readiness view-model fed by the parameter session. The
    workflow engine is Phase 08's: the readiness also states, in text, that
    no run can start yet because the engine is not built, so the button is
    never a control that pretends. The parameter half of the readiness (its
    blocking flag and its reasons) is what gate item 6 asserts.

P7-7 -- workflow-enforced outputs
    Every model the editor adopts -- new, imported, raw-applied,
    preset-applied, migrated -- has ``withWorkflowEnforcedOutputs()``
    applied. ``output_pepxmlfile`` and ``output_percolatorfile`` are shown
    on and locked with ``WorkflowOutputs.stageNeeding`` as the reason. No
    stage can be disabled before Phases 11/12 (PDV, Limelight) exist and
    Percolator is a core stage, so in this phase every dependent stage is
    enabled and both stay locked; the lock reads which stages are enabled from
    an input so those phases can switch it.

P7-8 -- Settings
    ``STATUS.rst`` (*The Settings section*): no phase has produced an
    application-level preference citing an ``R-`` or ``AC-``, so the section
    is removed from navigation (unit 2).

P7-9 -- spectrum inputs
    Essentials shows spectrum inputs and the FASTA database. Spectra are run
    inputs, not parameters, and are never written into ``comet.params``. File
    choice goes through a file-chooser port with a test injection (GUI
    coverage list). Existence/readability is shown through the domain
    ``FileSystemAccess`` port; format and decoy checks are Phase 08's.

P7-10 -- where gate tests live
    Gate-item GUI tests drive the launched application in ``cometgui-app``
    through ``FxUiDriver``; component tests live in ``cometgui-ui``. Expected
    values are hand-typed or checked-in files, never computed by the code
    under test.

Sign-off procedure (every unit)
-------------------------------

Read the diff; run the module's tests (the targeted ``verify`` above, with
the test filter for the module the unit touched); targeted PIT on the
classes the unit changed (``mvn -B -o -pl <module> -am test-compile
org.pitest:pitest-maven:mutationCoverage -Dcometgui.mutation.skip=false
-DtargetClasses=<fqcn,...>``, never a POM change); at least two production
injections of my own, in a private scratchpad subdirectory, anchor matched
once, marker grepped back, restored and proved with ``sha256sum -c``,
reports deleted before each run; ``scripts/ci/docs-build.sh`` when ``.rst``
changed; ``verify-all-gates.sh --only NAME`` for every harness that reads
what changed (``params`` for ``cometgui-params-comet``; ``shell`` for the
application shell; ``quality`` for architecture rules; ``docs`` and
``traceability`` for documentation). At least one of my injections across
the phase makes a control version-blind.

Work units
==========

Run serially, one fresh agent each, in this order.

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items served
     - Sign-off: what was run, what was seen, date
   * - 1
     - **Model: migration review as a blocking validation state**
       (``cometgui-params-comet``). A model-layer type that holds a
       ``MigrationReport`` and which of its ``NEEDS_ATTENTION`` entries the
       scientist has resolved, and one model-layer validation entry point
       that returns a single ``ValidationReport`` containing every finding of
       ``CometValidator.standard()`` plus one ERROR finding per unresolved
       entry, attached to its parameter and category, naming the source
       value, the substituted value and the reason. Resolving one entry
       clears exactly its finding. Proved on a real 2026.02.2 file migrated
       to 2026.03.0 with a terminus outside 0-3 at a distance (the
       ``variable_mod01`` active-oxidation case) and on 2026.02.2 to
       2024.01.0 two-loss. Developer page updated. ``--only params``,
       ``docs``, ``traceability`` green.
     - R-PARAM-13; gate 6
     - **Accepted** 2026-10-05, ``cce4116``; :ref:`p07w-u1`.
   * - 2
     - **Settings removed from navigation** (P7-8). Nine sections; ids,
       pins, arrival notes, navigation and accessibility tests, documents
       that describe the section, and ``scripts/verify-shell-gates.sh``
       anchors updated. No count floor lowered without the measured reason
       recorded. ``--only shell`` and ``--only docs`` green.
     - STATUS decision; spec IA
     - **Accepted** 2026-10-05, ``b7a02be``; :ref:`p07w-u2`.
   * - 3
     - **Release default sets and editor session view-models.** First, in
       ``cometgui-params-comet``: each offered release's starting set is
       Comet's own ``-q`` output (the metadata curates no enzyme rows), so
       the module gains a main-resource copy of each release's ``-q`` file,
       byte-equal to the checked-in fixture, and a loader giving the parsed
       default set (``--only params``). Then the view-models
       (``org.cometgui.ui.viewmodel.params``):
       the session (model, selected release from an offered list, adopting
       a model with outputs enforced, release switch through migration with
       its review), per-parameter field view-models (display name, help,
       unit, release choices, text, origin, findings, refused-edit message,
       locked with reason), the Essentials curated task-ordered list and its
       equality to the metadata's ESSENTIALS set, Advanced grouping by the
       fourteen categories, reset of a field, a category and the whole set,
       the validation summary (entries pointing at fields), run readiness
       (P7-6), spectrum inputs and the file-chooser port (P7-9). An ArchUnit
       rule that ``org.cometgui.ui`` does not reach the parser's line reader,
       the value codecs, ``Numbers`` or ``java.util.regex``. Independence scan
       extended to the subpackage.
     - R-PARAM-03, -06, -08, R-CMT-01; AC-PAR-03, -09, -10; gates 1, 5, 6, 7
     - **Accepted after one rework** 2026-10-05, ``9c03e63``, ``00ad4ab``,
       ``adce760``; :ref:`p07w-u3`.
   * - 4
     - **Structured-value view-models**: variable-modification editor over
       all slots of the release's layout (summary text from the model, add,
       edit, reorder/assign slot, remove, common-modification presets,
       residue multi-select from the release's alphabet, N-/C-terminal
       choices, min/max counts, required/exclusive with explanation,
       neutral-loss editing, serialised value, ``max_variable_mods_in_peptide``
       and ``require_variable_mod`` alongside); enzyme selector, custom
       enzyme editor and second enzyme; static modification table; ion-series
       family; compound precursor tolerance (pair, units, type, isotope);
       two-value ranges.
     - R-PARAM-04, -09, -10, -13; AC-PAR-05; gate 2
     - **Accepted** 2026-10-05, ``afc533c``, ``74df799``; :ref:`p07w-u4`.
   * - 5
     - **Presets, search, Expert and migration-review view-models**: preset
       preview as a diff with per-row selection, apply all / apply subset /
       cancel (exactly the subset applied, cancel changes nothing),
       compatibility problems shown; global search over name, display name,
       help, category and aliases with the five filters; Expert canonical
       text, line diagnostics, diff versus preset/defaults and versus last
       saved, unknown-parameter list, validating apply with confirmation that
       leaves the model untouched on parse failure; the migration report as a
       reviewable diff with per-entry resolution.
     - R-PARAM-05, -07, -08, -13; AC-PAR-07, -08; gates 3, 4, 8
     -
   * - 6
     - **Views, part 1, with their gate tests**: the Comet Parameters editor
       (mode switch, typed generic controls, Essentials surface, Advanced
       categories, error summary with keyboard navigation, locked outputs),
       the Run control, composition-root wiring of metadata and offered
       releases; ``FxUiDriver`` gains text entry. GUI tests: gate 1
       (Essentials-only tryptic DDA, file equal to a checked-in expected
       canonical file), gate 5, gate 6, gate 7 (every parameter control
       named, validation in text), each version-dependent control on both
       releases.
     - AC-PAR-03, -04, -09, -10; gates 1, 5, 6, 7
     -
   * - 7
     - **Views, part 2, with their gate tests**: variable-modification
       editor, enzyme and static-modification editors, preset diff dialog,
       Expert pane, search field and filters, migration review pane. GUI
       tests: gate 2 (add, edit, reorder, remove; serialised tuple after
       each step), gate 3, gate 4, gate 8, and a migrated file with an
       unresolved ``NEEDS_ATTENTION`` blocking Run until resolved.
     - AC-PAR-05, -07, -08; gates 2, 3, 4, 8
     -
   * - 8
     - **Falsifiability harness** ``scripts/verify-param-ui-gates.sh``,
       registered additively in ``scripts/verify-all-gates.sh``: each of the
       eight gate items goes red on an injected production defect (at least
       one version-blind) graded on its own diagnostic, harness self-test,
       floor recorded.
     - all gate items
     -
   * - 9
     - **Documentation**: ``docs/comet_parameters.rst``,
       ``docs/variable_modifications.rst``,
       ``docs/comet_parameter_presets.rst`` written as built; developer
       documentation of the editor; ``docs/traceability-map.toml`` entries
       for AC-PAR-03, -04, -05, -07, -08, -09, -10 moved from planned to the
       tests that prove them.
     - R-DOC; all ACs above
     -

Sign-off entries
================

.. _p07w-u1:

Unit 1 -- migration review (``cce4116``)
----------------------------------------

Agent: fresh tier-3 agent. Built ``migration.MigrationReview`` (a
``MigrationResult`` plus acknowledged names; ``resolve(name)``;
``unresolved(model)``; ``validate(model)``), ``Rule.MIGRATION_NEEDS_ATTENTION``
(``migration.needs_attention``, fixed ERROR), 16 tests in
``MigrationReviewTest``, a ``ReportModelTest`` pin, and the developer page
(rule catalogue row; section *The review: an entry needing attention blocks a
run*). "Resolved" means acknowledged **or** the parameter's origin is ``USER``
in the validated model -- a constraint on units 3-5: only a value the
scientist set on *that* parameter may carry ``USER``.

What I ran (2026-10-05):

* Read the whole production diff (``MigrationReview``, ``Rule``, the
  ``ValidationReport`` javadoc). In scope; nothing outside the module and its
  page changed. The review calls ``CometValidator.standard()`` itself, so the
  editor gets one report and combines nothing.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` (unfiltered, coverage
  rules on): BUILD SUCCESS in 3m04s; module ``Tests run: 1932, Failures: 0,
  Errors: 0, Skipped: 0``; three "All coverage checks have been met".
* PIT, ``-DtargetClasses='org.cometgui.params.comet.migration.MigrationReview*'``:
  27 mutations, 27 KILLED.
* Injection 1 (production): the finding's category replaced by
  ``Optional.empty()`` -- red: ``MigrationReviewTest.blocks ... expected:
  <[... ERROR migration.needs_attention [variable_mod02] variable_mods]> but
  was: <[... [variable_mod02] -]>`` and ``bothInOrder``.
* Injection 2 (production, **version-blind**): ``requireTarget``'s release
  check made ``if (false)``, so a review of a migration to 2026.03.0 judges a
  set of another release -- red: ``MigrationReviewTest.anotherRelease:
  Expected java.lang.IllegalArgumentException to be thrown, but nothing was
  thrown.``
* Both restored; ``sha256sum -c``: ``MigrationReview.java: OK``. Anchors
  matched once; markers ``ORCH-INJ-1``/``-2`` grepped back before each run;
  surefire reports deleted before each run.
* ``bash scripts/verify-all-gates.sh --only params --only docs --only
  traceability``: rc 0, ``3 control(s) passed, 0 failed, in 512 seconds``;
  ``params: 109 controls in 453s`` (floor 109), ``docs: 1 injected
  cross-reference``, ``traceability: 8 injected defects caught``.

No test reads a file outside the module.

.. _p07w-u2:

Unit 2 -- Settings removed from navigation (``b7a02be``)
--------------------------------------------------------

Agent: fresh tier-3 agent. ``SectionId.SETTINGS`` and its arrival note
removed; nine sections, Tool Manager the one secondary section; five pinned
identifiers removed with the constant (``PINNED_IDENTIFIER_COUNT`` 133 ->
128, an exact count, not a floor); new tests ``settingsIsNotASection``,
``everySectionHasAnOwningPhase`` and ``SectionNavigationUiTest
.theApplicationHasNoSettingsSection``; ``AccessibleNameEnumerationUiTest``'s
floor **raised** 65 -> 68 (removal took 4 out of the derivation; the seven
always-present stepper arrows were added to it -- I read the derivation and
agree; the walk measures 161); ``docs/developer/architecture.rst`` gains *The
Settings section was removed*. Comment-only edits in
``cometgui-install``'s ``ManagedToolManager`` and in ``CometGuiApplication``
(they claimed the section exists). No shell-harness anchor needed a change.

What I ran (2026-10-05):

* Read the diff. Production changes are the enum constant, the note, and
  comments; test changes as above.
* ``mvn -B -o -pl cometgui-app -am verify -Dtest='org.cometgui.ui.**,org.cometgui.app.**'
  -Dsurefire.failIfNoSpecifiedTests=false -Dcometgui.coverage.core.skip=true``:
  BUILD SUCCESS, 4m09s; ``cometgui-ui`` 197 tests, 0 failures/errors/skips
  (200 before: four parameterised tests over ``SectionId`` lose a case, one
  test added); ``cometgui-app`` 121 tests, 0 failures, 1 skipped (the opt-in
  ``UpstreamInstallUiTest``).
* Injection (production, ``ShellView.buildNavigation``): a stale "Settings"
  toggle with id ``nav-settings`` and no pane. Red in ``cometgui-ui``:
  ``ShellViewTest.theNavigationSeparatesTheSecondarySections: eight primary
  entries, a separator, one secondary ==> expected: <10> but was: <11>``; red
  in ``cometgui-app`` (run alone): ``SectionNavigationUiTest
  .theApplicationHasNoSettingsSection: Expected java.lang.AssertionError to
  be thrown, but nothing was thrown.`` Restored, ``sha256sum -c``: OK.
* The agent's PIT (31/31 on ``SectionId*``, ``SectionArrivals*``,
  ``NavigationViewModel*``, with ``-DtargetTests`` limited to toolkit-free
  tests on the command line) -- not re-run: the production change is a
  removed enum constant and map entry, both killed by my injection's tests.
* ``bash scripts/verify-all-gates.sh --only shell``: rc 0, ``shell: 30
  controls in 201s`` (floor 30). ``bash scripts/ci/docs-build.sh``: PASSED.

No test reads a file outside its module.

.. _p07w-u3:

Unit 3 -- release default sets and session view-models (``9c03e63``, ``00ad4ab``, ``adce760``)
------------------------------------------------------------------------------------------------

Agent: fresh tier-3 agent (resumed once for the rework below).

* **Model** (``9c03e63``): ``parser.ReleaseDefaults`` loads each offered
  release's bundled ``comet -q`` file (``src/main/resources/.../parser/defaults/
  {2026.03.0,2026.02.2}/comet-q.params``, byte copies of the fixtures; a
  ``-text`` rule in ``.gitattributes``), origins ``COMET_DEFAULT``; refuses a
  release with none (2024.01.0 deliberately has none) and any parse
  diagnostic. Developer page section *Each release's starting set*.
* **View-models** (``00ad4ab``), ``org.cometgui.ui.viewmodel.params``:
  ``ParameterSession`` (one model; release from a caller-given list;
  ``adopt``/``adoptMigration`` always enforce outputs; ``selectRelease``
  migrates and keeps the ``MigrationReview``, and is refused while entries
  are unresolved; edits via ``withText(..., USER)``; an edit equal to the
  held value changes nothing, origin included, so re-committing text cannot
  resolve a migration entry), ``FieldViewModel`` (from ``parameter(name,
  version)``), Essentials (10 task-ordered groups, 57 parameters, held equal
  to the metadata's ESSENTIALS set for both releases), Advanced (14
  categories, 118 each), resets, ``ValidationSummaryViewModel``,
  ``RunReadinessViewModel``, ``SpectrumInputsViewModel`` with
  ``FileChooserPort`` and ``FileSystemAccess``, ``StageSwitches``. New
  ArchUnit rule ``UiThroughTheModelRule`` (no ``ParamsLineReader``,
  ``Numbers``, value codecs, ``ParameterValueCodec`` or ``java.util.regex`` in
  ``org.cometgui.ui``); ``UiIds``' one regex replaced by a character loop with
  new ``UiIdsTest`` pins; the view-model independence scan now reads
  subpackages.

**Rework (rejected design call).** The agent reported that a field holding a
refused edit did not block Run. I rejected that: the screen would show a value
that is not the one searched. ``adce760`` makes every pending refusal a
blocking reason (``Not applied -- <display name> (<name>), <category>:
<model's message>``), listed first in the summary with its focus target; an
accepted edit, reset, adoption or release change clears it. View-model state,
no model rule.

Accepted design calls, for later units: no "unit" field (the metadata has
none; units are parameters such as ``peptide_mass_units``); with a stage
switched off an output can be switched off but model validation still errors
(no stage can be switched off before Phases 11/12 -- deferred, see
*Deferred*).

What I ran (2026-10-05):

* Read the diffs; searched the view-models for parsing (``split``,
  ``parse``, ``Integer.``, ``BigDecimal``, ``matches``): one
  ``Integer.toString(source.decoySearch())`` to match a model enum's token
  against the release's choice tokens -- formatting, not a rule.
* ``mvn -B -o -pl cometgui-params-comet -am verify``: ``Tests run: 1941,
  Failures: 0``; three "All coverage checks have been met". PIT on
  ``ReleaseDefaults*``: 9/9 KILLED.
* ``mvn -B -o -pl cometgui-archtests -am verify -Dtest='org.cometgui.ui.**,org.cometgui.archtests.**'
  ...``: BUILD SUCCESS; archtests 29 tests, 0 failures. ``jacoco.xml``
  package ``org/cometgui/ui/viewmodel/params``: LINE 546/546, BRANCH
  145/145 before the rework (agent: 600/600, 159/159 after; the view-model
  rule passed in my post-rework run).
* Injection 3a (production): ``adopt`` without ``withWorkflowEnforcedOutputs()``
  -- red: ``ParameterSessionTest.editsKeepTheRelease expected: <1> but was:
  <0>``, ``imported`` (5 failures).
* Injection 3b (production, **version-blind**): ``selectRelease`` migrates to
  the current release instead of the target -- red:
  ``ParameterSessionTest.migrates expected: <2026.2.2> but was: <2026.3>``,
  ``needsAttentionBlocksUntilResolved expected: <[variable_mod01]> but was:
  <[]>``, ``editsKeepTheReview`` (6 failures). My first attempt at 3b was
  stopped by Spotless (line too long) -- no verdict; re-injected formatted.
* Injection 3c (production, after the rework): the per-field refusal
  listener removed -- red: ``SummaryAndReadinessTest.refusedEditBlocks
  expected: <true> but was: <false>``, ``adoptAndReleaseChangeUnblock``.
* All restored; ``sha256sum -c`` OK each time.
* After the rework: ``mvn -B -o -pl cometgui-ui -am verify -Dtest='org.cometgui.ui.**'
  ...``: ``Tests run: 508, Failures: 0, Errors: 0, Skipped: 0``, "All
  coverage checks have been met".
* ``bash scripts/verify-all-gates.sh --only params --only quality --only docs
  --only traceability``: ``4 control(s) passed, 0 failed, in 642 seconds``;
  params 109 controls, quality 42, docs 1, traceability 8.

No test reads a file outside its module.

.. _p07w-u4:

Unit 4 -- structured-value view-models (``afc533c``, ``74df799``)
-----------------------------------------------------------------

Agent: fresh tier-3 agent.

* **Model** (``afc533c``): ``value.VariableModSlots`` (the release's slots and
  parts from its layout; ``withPart`` reads one part's text and refuses with
  slot and part named; ``withResidue`` over the release's alphabet;
  ``unused()`` = ``0.0 X 0 3 -1 0 0 0.0``), ``VariableModPart``,
  ``VariableModChoice``, ``VariableModCodec.unwritable()``,
  ``ResidueAlphabet.letters()/terminalCodes()``, ``EnzymeDefinition.fromTexts``,
  ``EnzymeTable.nextNumber``, two-text ranges, ``schema.StaticModTarget``,
  and ``comet-modification-presets.json`` + ``ModificationPresets`` (six
  presets, each mass citing its Unimod record; offered where a release's
  slots can hold it, so the ``^`` Acetyl is 2026.03.0's only).
* **View-models** (``74df799``): ``VariableModsViewModel`` (summary from the
  model, serialised value, every part, add/edit/remove/move, presets, the
  ``R-PARAM-10`` pair beside the slots), ``EnzymesViewModel``,
  ``StaticModsViewModel``, ``IonSeriesViewModel``, ``ToleranceViewModel``,
  ``RangesViewModel``. A move to or from a slot with an unresolved migration
  entry is refused and *add* skips it; editing or removing that slot is the
  scientist's decision and resolves it.

For the owner's eye (recorded in the handoff): the presets' non-mass fields
(count 3; Phospho without the page's neutral loss; both Acetyl forms on
2026.03.0) are the agent's choices, each grounded in a Comet page example.

What I ran (2026-10-05):

* Read the diffs and the presets file (every mass and citation). Scanned the
  view-models for parsing: only ``Integer.toString`` of model codes to match
  choice tokens.
* ``mvn -B -o -pl cometgui-params-comet -am verify``: ``Tests run: 1988,
  Failures: 0``; three "All coverage checks have been met".
* ``mvn -B -o -pl cometgui-ui -am verify -Dtest='org.cometgui.ui.**' ...``:
  ``Tests run: 589, Failures: 0``; view-model coverage met.
* PIT on ``VariableModSlots*``, ``ModificationPreset*``, ``StaticModTarget*``,
  ``VariableModPart*``: 108/108 KILLED.
* Injection 4a (production, **version-blind**): ``ModificationPresets
  .offeredIn`` offers every preset to every release -- red:
  ``ModificationPresetsTest.offeredPerRelease expected: <[oxidation-m,
  phospho-sty, acetyl-protein-n-term, deamidation-nq, gln-pyro-glu]> but
  was: <[..., acetyl-protein-n-term-caret, ...]>``.
* Injection 4b (production): ``EnzymesViewModel.select``'s table check made
  ``if (false)`` -- red: ``StructuredEditorsTest.select expected:
  <EditOutcome[accepted=false, refusal=Optional[40. Elsewhere is not a row of
  this configuration's enzyme table, so it cannot be selected]]> but was:
  <EditOutcome[accepted=true, ...]>``.
* (Spotless/Checkstyle/SpotBugs skipped on the command line for the two
  injection runs only, so the tests were reached; reports deleted first.)
  Both restored; ``sha256sum -c`` OK.
* ``bash scripts/verify-all-gates.sh --only params --only quality --only docs
  --only traceability``: ``4 control(s) passed, 0 failed, in 647 seconds``;
  params 109, quality 42.

No test reads a file outside its module.

Rejections and rework
=====================

* Unit 3, 2026-10-05: design call "a pending refused edit does not block
  Run" rejected; reworked in ``adce760`` (see :ref:`p07w-u3`).

Deferred
========

* **Stage-dependent unlocking of workflow outputs.** In this phase every
  dependent stage is enabled (no stage can be switched off before Phases 11
  and 12), so both outputs are always locked. ``StageSwitches`` is the input
  those phases set; reconciling a switched-off stage with the model rule
  ``workflow_enforced.output_off`` is theirs.

Blockers escalated
==================

None yet.
