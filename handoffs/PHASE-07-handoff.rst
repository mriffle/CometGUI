=====================================================
PHASE-07 handoff -- Comet Parameter Editor UI
=====================================================

:Phase: 07
:Written: 2026-10-05 by the Phase-07 orchestrator (tier 2)
:Outcome: **Ten work units accepted** (units 3, 6 and 7 after one rework round
   each). The phase orchestrator's verdict on the exit gate: items 1-8
   **met**, with the residue named under each. **Not yet signed off by tier
   1**, whose ``scripts/build.sh`` and full ``scripts/verify-all-gates.sh``
   run has not happened; nobody in this phase ran either, by the owner's
   build-economy rule.
:Records: ``handoffs/PHASE-07-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off there names the commands run, the
   numbers seen and the injections made into production code, with their
   failure text -- including the injections that did *not* bite at first.

.. contents:: Contents
   :depth: 2
   :local:

In one paragraph
================

The Comet Parameters section is a working, per-release parameter editor:
Essentials (a curated, task-ordered surface), Advanced (the fourteen
categories, every parameter), Expert (canonical raw text with highlighting,
line diagnostics, diffs and a validating apply that changes nothing on a parse
failure), search, presets with a reviewable diff, a variable-modification slot
editor, enzyme and static-modification tables, import and save, and a
migration review in which an unresolved ``NEEDS_ATTENTION`` entry blocks Run.
Every finding the editor shows comes from one model-layer
``ValidationReport``; no rule, parser or number reader lives in
``org.cometgui.ui`` (an ArchUnit rule now enforces the structural half). The
Settings section was removed from navigation. A new harness,
``scripts/verify-param-ui-gates.sh`` (``paramui``, 84 controls), proves every
gate item fails on a production defect, four of them version-blind.

What was built
==============

.. list-table::
   :header-rows: 1
   :widths: 8 30 62

   * - Unit
     - Commits
     - What
   * - 1
     - ``cce4116``
     - ``migration.MigrationReview`` and ``Rule.MIGRATION_NEEDS_ATTENTION``:
       one report holding every validator finding plus an ERROR per
       unresolved entry, at its parameter and category. Resolved =
       acknowledged, or the parameter set by the scientist (origin ``USER``).
   * - 2
     - ``b7a02be``
     - Settings removed from navigation (``STATUS.rst`` decision): nine
       sections; pins 133 -> 128 (exact count); accessibility floor raised
       65 -> 68.
   * - 3
     - ``9c03e63`` ``00ad4ab`` ``adce760``
     - ``parser.ReleaseDefaults`` (each offered release's bundled ``-q``
       file, byte-equal to the fixture); the toolkit-free session
       (``ParameterSession``), field view-models from ``parameter(name,
       version)``, Essentials/Advanced, resets, summary, run readiness,
       spectrum inputs and the file-chooser port; ArchUnit
       ``UiThroughTheModelRule``. Rework: a refused edit blocks Run.
   * - 4
     - ``afc533c`` ``74df799``
     - Model: ``VariableModSlots`` (set one tuple part from text under the
       release's layout), ``StaticModTarget``, two-text ranges, custom enzyme
       rows, ``comet-modification-presets.json`` (six Unimod-cited masses).
       View-models for slots, enzymes, static mods, tolerance, ranges.
   * - 5
     - ``562798c`` ``4c3ab49``
     - Model: ``ParamsHighlighting``. View-models for presets (diff, subset,
       cancel), search (with "why"), Expert (apply then confirm), import/save
       (``writeOnce``), migration review.
   * - 6
     - ``6edbaee`` ``7a484fb`` ``9871aab`` ``5e1cd8b`` ``fc68afa``
     - The JavaFX editor, the Run control, composition-root wiring
       (offered releases = manifest ∩ metadata ∩ bundled sets; build identity;
       file chooser), ``FxUiDriver`` text entry; gate tests 1, 2, 5, 6, 7.
       Rework: generated accessible names no longer pass for a control's own;
       ``ParameterSession`` publishes one consistent ``State``.
   * - 7
     - ``8993cfb`` ``6e3b67a`` ``26430e2``
     - Preset preview, Expert pane, search, import, migration review, enzyme
       table editor; gate tests 3, 4, 8 and the migration-blocks-Run test.
       Rework: gate tests start from a configuration a reset would change.
   * - 8
     - ``f393195``
     - ``scripts/verify-param-ui-gates.sh``, registered as ``paramui``.
   * - 9
     - ``2515166`` ``7276ebf`` ``38c494f``
     - User pages (``comet_parameters``, ``variable_modifications``,
       ``comet_parameter_presets``), the editor in ``architecture.rst``, 25
       traceability entries for AC-PAR-03/04/05/07/08/09/10; a GUI test of
       release-specific help.
   * - 10
     - ``8844706`` ``c16b1fd`` ``2691ff4``
     - The fragment "instrument" choice previewed before it changes anything
       (AC-PAR-08); a static-modification table; ``IonSeriesViewModel``
       removed; ``paramui`` floor 84.

Orchestrator commits: ``5eac118``, ``66673ad``, ``686e410`` (work log), and a
sign-off commit per unit (``b7aef5c``, ``4aea683``, ``296e35c``, ``b55b4da``,
``b7565e0``, ``516a96e``, ``a421760``, ``fa184e2``, ``4b186f1``, ``818f0f2``),
and this handoff.

**Changes outside the phase's own packages**, each reviewed at sign-off:
``cometgui-params-comet`` (units 1, 3, 4, 5: additions only, each with tests and
``--only params`` green at 109); ``cometgui-archtests`` (a new rule);
``cometgui-app/pom.xml`` (one Maven-filtered resource,
``build-identity.properties``); ``.gitattributes`` (``-text`` for the bundled
``-q`` files and the gate-1 expected file); Phase 02's ``AccessibleControls``
(generated names are marked; the fallback is kept);
``scripts/verify-all-gates.sh`` (``paramui`` added; nothing else changed).

Exit gate
=========

Every check below was run by me on the tree it names; the work log has the
detail and the failure text of each injection.

.. list-table::
   :header-rows: 1
   :widths: 5 12 83

   * - #
     - Verdict
     - Evidence
   * - 1
     - Met
     - ``EssentialsTrypticSearchUiTest``: a TMT tryptic DDA search set up
       through Essentials only and saved; the file is byte-equal to the
       checked-in ``essentials-tryptic-dda-2026.03.0.params`` (build
       ``0.0.0-guitest`` injected, so the header compares too). Red: decoy
       control always "no internal decoys" (``paramui`` 1), the save writing
       ``decoy_search = 0`` behind a correct screen (1b), a static-mod row
       writing to the next parameter (1c). **Residue:** the expected file holds
       a Linux path.
   * - 2
     - Met
     - ``VariableModificationEditorUiTest``: add, edit, reorder, remove, the
       serialised tuple asserted after each step, both drivers; ``^``/``$``
       offered for 2026.03.0 only. Red: Move up moving down (2a); every
       release offered ``TerminalCode.values()`` (2v, version-blind; my own
       injection 6a too).
   * - 3
     - Met
     - ``PresetPreviewUiTest`` and ``FragmentInstrumentPreviewUiTest``: the
       diff rows hand-typed; Cancel changes nothing from a non-default
       configuration; a subset applies exactly those rows with origin
       "Set by a preset". Red: Cancel applies all (3a), Apply selected
       applies all (3b), the instrument choice applying at once (3c), its
       preview not limited to fragment rows (3d). **Residue:** presets are
       applied from Essentials only; no user presets are offered.
   * - 4
     - Met
     - ``ExpertRawEditUiTest``: a malformed draft leaves every value, origin
       and canonical line of a non-default configuration unchanged and
       reports the line number and text; a valid edit changes nothing until
       confirmed; the ``^`` line is refused on 2026.02.2 and accepted on
       2026.03.0. Red: a failed apply resetting the configuration (4a -- green
       before unit 7's rework), apply without confirmation (4b), every draft
       parsed as the first offered release (4v, version-blind).
   * - 5
     - Met, with a deferral
     - ``WorkflowOutputsLockedUiTest``: mouse and keyboard on both levels,
       both outputs stay 1, the reason is on screen; a category reset keeps
       them. Red: input left enabled (5a), reason hidden (5b). **Residue:** no
       downstream stage can be switched off before Phases 11/12, so the
       outputs are always locked; ``StageSwitches`` is the input those phases
       set (work log, *Deferred*).
   * - 6
     - Met (parameter half)
     - ``CrossParameterValidationUiTest`` (reversed precursor window;
       required modification with no slot) and
       ``MigrationReviewBlocksRunUiTest``: Run disabled with the reason, the
       error at the field, the summary entry reached by Tab alone and Enter
       moving the focus to the field (focus owner asserted); fixing it lifts
       the parameters' block. Red: summary entry not moving focus (6a),
       readiness text forced to "do not block" (6b). **Note:** until Phase
       08 builds the workflow engine Run is *also* disabled by the engine
       reason, so what is proven is the parameters' half; an injection that
       only makes Run depend on the engine is equivalent today, and
       ``paramui`` control H reports it as a harness failure on purpose.
   * - 7
     - Met
     - ``ParameterControlsAccessibilityUiTest`` (both releases: 61 Essentials
       and 118 Advanced parameters; each control's name must contain its
       release display name; 17 names hand-typed; every control with an id
       must carry its own name, not the generated fallback) and
       ``AccessibleNameEnumerationUiTest``; validation state in text in a
       label and in accessible help. Red: a parameter control's own name
       removed (7a -- green before unit 6's rework), state left out of
       accessible help (7b), a static-mod mass field unnamed (7c), choices
       from the curated definition (7v, version-blind).
   * - 8
     - Met
     - ``ParameterSearchUiTest``: found by name, display name, help text and
       alias, each through that attribute only, with the "why" text asserted;
       activating a result focuses the field; release-specific help on both
       releases. Red: alias matching removed (8a), a result not focusing
       (8b), help taken from the curated definition (8v, version-blind, now
       also at GUI level).

**Harness runs on the final tree** (``818f0f2``'s code, ``2691ff4``):
``bash scripts/verify-all-gates.sh --only docs --only traceability --only
quality --only shell --only paramui`` -- ``5 control(s) passed, 0 failed, in
2203 seconds``: paramui 84 (floor 84), shell 30, quality 42, docs 1,
traceability 8. ``--only params`` last ran at unit 5 (109; no model change
since). ``--only tests``: see :ref:`p07h-tests`.

**Suites on the final tree:** ``mvn -B -o -pl cometgui-app -am verify
-Dtest='org.cometgui.ui.**,org.cometgui.app.**' -Dsurefire.failIfNoSpecifiedTests=false
-Dcometgui.coverage.core.skip=true`` -- ui 822 / 0 failures, view-model
coverage met; app 176 / 0 failures / 1 skipped (the opt-in
``UpstreamInstallUiTest``). ``cometgui-params-comet`` unfiltered verify: 2000
tests, 0 failures (unit 5; unchanged since).

.. _p07h-tests:

The tests harness
-----------------

``scripts/verify-test-gates.sh`` reads the view-model package this phase grew
(control 5 sizes its injection from it), so it was run at the end of the
phase, once.

* **First run: HARNESS ERROR** -- ``no PIT report for cometgui-domain in the
  sandbox or the working tree`` (control 6 sizes its injection from that
  report, which only ``scripts/build.sh`` writes; a unit's ``-am clean
  verify`` had removed it). Not a gate failure and not this phase's code.
* **Repair**: the report recreated exactly as ``build.sh``'s PIT stage writes
  it, for that module only -- ``mvn -B -o -Dcometgui.pit.threads=16 -pl
  cometgui-domain test-compile org.pitest:pitest-maven:mutationCoverage``
  (rc 0, 380 mutations, 379 killed). No harness, control or floor changed.
* **Second run: PASS** -- ``tests: 37 assertions in 3939s`` (floor 37);
  control 5 sized against the grown package and the view-model rule rejected
  it (``lines covered ratio is 0.66, but expected minimum is 0.80``).

No unit's tests read a file outside their module.

What is incomplete and why
==========================

* **Unverifiable here:** the native JavaFX file dialogs (never opened under
  the headless toolkit; everything ``FxFileChooser`` decides is tested
  through its dialog seam); any non-Linux host; the gate-1 expected file's
  Linux path.
* **Deferred, recorded:** stage-dependent unlocking of the workflow outputs
  (Phases 11/12); "diff versus last run" in Expert (no runs before Phase 08);
  user presets (no store yet); presets applied from Advanced/Expert; a
  "threads: automatic" switch (``num_threads = 0`` is Comet's own meaning,
  explained in help, not reimplemented in the UI).
* **Human activities:** ``AC-UX-01``..``06`` (UX validation) are owner
  scheduled; nothing here discharges them.

Decisions encountered
=====================

No ``D-`` item was hit. Judgement calls a later phase may revisit (each in the
work log):

* "Resolved" for a migration entry = acknowledged **or** set by the scientist
  on that parameter; a slot move to or from an unresolved slot is refused.
* An edit equal to the held value changes nothing, origin included.
* A refused edit blocks Run and saving (unit 3 rework).
* Saving is refused while anything blocks Run; a file is never overwritten.
* "Modified only" compares the value with the reset value, so the enforced
  ``output_percolatorfile = 1`` counts as modified.
* The common-modification presets' non-mass fields (count 3, Phospho without
  the page's neutral loss, both Acetyl forms on 2026.03.0) are the agent's
  choices grounded in Comet page examples -- **worth an owner glance**.
* JavaFX's combo-box skin list (id ``list-view``) is the one documented
  exclusion from "every control with an id carries its own name".

Surprises
=========

* **Three gate tests could not go red until reworked**, each found by an
  injection of mine into production code: gate 7 accepted the generated
  fallback name; gate 4 started from the defaults, which a reset reproduces;
  and (unit 6) "Run disabled only by the engine" is *equivalent* today. The
  lesson for Phase 14's GUI suite: a "nothing changed" assertion needs a
  non-default starting state, and an accessibility walk must tell an
  explicit name from a generated one.
* **Exceptions thrown in JavaFX listeners are swallowed** -- a listener
  reading a half-published session failed silently with every suite green
  until a GUI test recorded listener exceptions. ``ParameterSession`` now
  publishes one consistent ``State``.
* SpotBugs ``EI_EXPOSE_REP`` drove the composition: view-models are built in
  the composition root and passed in, never handed out by another
  view-model.
* ``pgrep -f`` matches the waiting shell's own command line; wait on a PID,
  and beware a zombie (``kill -0`` succeeds on one).

Escalated
=========

Nothing requires the owner. For tier 1: the gate-6 note (Run's engine half is
Phase 08's), the deferrals above, and the preset-shape judgement call.

First thing the next agent should do
====================================

**Tier 1:** on a quiet tree with ``scratch/phase05/artefacts``,
``scratch/phase06/artefacts`` and ``scratch/fixture`` present, run ``bash
scripts/build.sh`` and then ``bash scripts/verify-all-gates.sh`` in full; it
now includes ``paramui`` (about 28 minutes, 84 controls).

**Phase 08:** the Run control is ``org.cometgui.ui.controls.params.RunControl``
over ``RunReadinessViewModel``; the parameters' half is done -- add the
engine's half there, not a second readiness. Write the run's parameter file
through ``ParameterFilesViewModel``'s save path (``writeOnce``), and treat an
unresolved migration entry as the error it already is in the report.
