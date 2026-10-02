=======================================================
PHASE-06 handoff -- Comet Parameter Model
=======================================================

:Phase: 06
:Written: 2026-10-02 by the Phase-06 orchestrator (tier 2)
:Outcome: **All eight work units accepted** (one after a rework round). The
   phase orchestrator's verdict on the exit gate: items 1-8 **met**, item 9
   **met**, with its qualitative half resting on three survivors argued
   equivalent in the work log. **Not yet signed off by tier 1**, whose
   exit-gate run (``scripts/build.sh``, then ``scripts/verify-all-gates.sh`` in
   full) has not happened; nobody in this phase ran either, by the owner's
   build-economy rule.
:Records: ``handoffs/PHASE-06-worklog.rst`` -- **this page is the map; the
   work log is the proof.** Every sign-off there names the commands run, the
   numbers seen, and two or more defects injected into production code with
   their exact failure text.

.. contents:: Contents
   :depth: 2
   :local:

What was built
==============

All in ``cometgui-params-comet`` (``org.cometgui.params.comet``), plus a
documentation generator and a gate harness under ``scripts/``.

.. list-table::
   :header-rows: 1
   :widths: 22 12 66

   * - Package / file
     - Unit
     - What it is
   * - ``src/test/resources/fixtures/comet/2026.02.2/linux-x86-64/``
     - 1
     - The real ``comet -q`` and ``comet -p`` output of the pinned binary, with
       ``SHA256SUMS``; a real-binary test re-proves the bytes on every Linux
       build, and the release matrix (``manifests/tools.json``) is read so a
       new Comet version without fixtures fails the build.
   * - ``schema``
     - 2, 3, 6
     - ``ParameterDefinition`` and its kinds, categories, visibility, validator
       ids; ``MetadataLoader`` over the one ``JsonReader``; the curated
       metadata ``comet-parameters.json`` (**118 of 118 parameters modelled;
       the internal allow-list is empty**); ``CometVersionMarker``
       (``2026.02 rev. 2 (6edec91)`` <-> ``2026.02.2``); discovery from
       ``-q``/``-p`` with ``PARTIAL_DISCOVERY``; drift as a value;
       ``CometParameterSchemaProvider`` over the domain ``ProcessRunner``; the
       variable-modification layout as per-version data.
   * - ``parser``
     - 2, 4
     - ``ParamsLineReader`` (the one place that knows a line's shape),
       ``CometParamsParser`` -> ``ParseResult`` (model, diagnostics,
       ``ImportedComments``). All-or-nothing: an error yields no model.
   * - ``value``
     - 3
     - Typed tuple, enzyme table, tolerance pair, ranges, decimal list, ion
       series; codecs driven by the version's layout; ``Numbers`` (the one
       number reader/writer, ``BigDecimal``, ``Locale.ROOT``).
   * - ``model``
     - 4, 5
     - ``CometParameters`` (immutable; five value origins), ``DecoySource``,
       workflow-enforced outputs.
   * - ``writer``
     - 4
     - ``CanonicalParamsWriter``: deterministic, marker on line 1, generated
       header, curated inline comments, unknowns preserved, refuses an
       enzyme number absent from the table, ``writeOnce`` hashes the file on
       disk through ``HashService`` (``R-PARAM-12``).
   * - ``validation``
     - 5
     - 33 rules with stable ids and fixed severities; ``R-PARAM-04``'s pair
       rule separate from the generic ordering rule; ``R-PARAM-10``,
       ``R-CMT-01``, ``R-DEC-01`` (model level), version-unavailable
       parameters blocked.
   * - ``presets``, ``migration``
     - 6
     - Low-low / high-low / high-high presets (values equal to Comet's own
       2026.02 example files), diffs, apply-selected with origin ``PRESET``,
       cross-version compatibility reports; migration between schema versions
       against a **real Comet 2024.01.0** fixture (binary not in the release
       matrix; kept in gitignored ``scratch/phase06/artefacts/``).
   * - ``scripts/cometparams.py`` + ``docs/conf.py`` hook
     - 7
     - Generates ``reference/comet_parameters_generated.rst`` from the
       metadata during the documentation build (``R-DOC-04``); refuses a bad
       input and fails the build; self-test of 27 + 4 cases inside
       ``docs-build.sh --self-test``.
   * - ``scripts/verify-param-gates.sh``
     - 8
     - The falsifiability harness, registered as ``params`` in
       ``verify-all-gates.sh`` with floor 68.
   * - ``docs/developer/comet_parameter_schema.rst``
     - all
     - The developer page, written as built, with every Comet fact cited to
       the source at tag ``v2026.02.2``.

**Architecture change:** a new **main** edge ``cometgui-params-comet ->
cometgui-provenance`` (for the one ``JsonReader``; the same edge
``cometgui-install`` has), and a **test-scope** edge to ``cometgui-process``.
``docs/developer/architecture.rst``'s row says so. ArchUnit is green.

Measured on the final tree (unit 6/7 sign-offs, no product change since):
module 979 tests, 0 failures, 0 skipped; 136 compiled classes, all 136 in
``jacoco.xml``; PIT 1095/1102 killed under ``build.sh``'s scoring.

Gate items
==========

Each item's evidence is the phase orchestrator's own run, listed by sign-off in
the work log. ``bash scripts/verify-all-gates.sh --only params`` (my run,
2026-10-02, 326 s) re-proves every item 1-9 fails on its defect and passes
clean.

.. list-table::
   :header-rows: 1
   :widths: 5 12 83

   * - #
     - Verdict
     - Evidence (and the injection that makes it go red)
   * - 1
     - Met
     - ``CanonicalWriterTest`` *gate item 1*: parse/write of the real ``-q``
       fixture -> 10 656 bytes, SHA-256 ``f381afe1...d62b``, identical on the
       second and third round trip. Red: writer skipping empty-valued
       parameters -> ``expected: <f381afe1...> but was: <185bce30...>``
       (unit 4 sign-off; harness control 1).
   * - 2
     - Met
     - 118 declared / 118 modelled / 0 allow-listed
       (``SchemaDriftFixtureTest``). Red: ``scan_range`` removed from the
       metadata -> ``UNMODELLED: Comet 2026.02.2 declares scan_range ...``
       (harness 2a); drift reduced to first token (harness 2b).
   * - 3
     - Met
     - 17 forms x 15 slots = 255 round trips (``VariableModRoundTripTest``).
       Red: ``max,min`` written -> 30 failures (unit 3 sign-off; harness 3b);
       second neutral loss dropped (3a).
   * - 4
     - Met
     - Real table round-trips byte for byte; custom ``12. Glu_C 1 DE P``
       survives; writer refuses ``search_enzyme_number = 42``. Red: refusal
       disabled -> ``Expected ...ParamsWriteException to be thrown, but nothing
       was thrown.`` (harness 4b); duplicate-number check disabled (4a).
   * - 5
     - Met
     - ``CommaLocaleWriterTest`` under ``de-DE`` and ``fr-FR`` (locale proven
       to write ``1,5`` first; restored). Red: locale-sensitive formatting ->
       ``the bytes differ first at offset 1076 ... "= 20,0"`` (harness 5a, 5b).
   * - 6
     - Met
     - Unknown parameters keep value and comments through two round trips and
       are reported as ``UNKNOWN_PARAMETER``. Red: writer / parser drop them ->
       ``expected: <[ms1_mass_range, precursor_NL_ions]> but was: <[]>``
       (harness 6a, 6b).
   * - 7
     - Met
     - ``TolerancePairRuleTest``: ``-20/20`` clean; ``-10/20``, ``5/20``,
       ``-20/-5`` warnings; ``20/-20`` error; the generic rule never fires for
       the pair. Red: reversed check unable to fire -> ``expected:
       <PAIR_REVERSED> but was: <PAIR_SAME_SIGNED>`` (unit 5 sign-off; 7c);
       pair routed through the generic rule (7a); asymmetric made an error (7b).
   * - 8
     - Met
     - ``docs-build.sh`` PASSED; build log ``118 parameter entries = 118
       modelled parameters``; 118 entry anchors in the HTML. Red: the hook
       swallowing a rejection -> ``docs-build.sh --self-test`` rc 4, graded on
       the hook's own message (unit 7 sign-off; harness control 8).
   * - 9
     - Met
     - PIT, ``build.sh`` scoring: ``parser`` 96/101 (95.0 %), ``writer``
       30/30, ``validation`` 180/181 (99.4 %); module 1095/1102. Survivors:
       ``ParamsLineReader:137`` x2 and ``VariableModRules:178`` -- each read and
       argued equivalent in the work log (unreachable for the boundary that
       distinguishes them); **none suppresses a validation error or drops a
       parameter** (a model missing a parameter cannot even be constructed --
       unit 6 sign-off). Red: validation tests removed -> ``validation is
       graded BELOW the 80% threshold: 68/181 = 37.5%`` (harness 9). The
       harness covers the numeric half; the equivalence judgements are the
       work log's, for tier 1 to re-read.

What is incomplete and why
==========================

* **Only ``linux``/``x86-64`` Comet output has ever been captured or run.**
  The manifest's four other 2026.02.2 rows are named ``NOT CAPTURED`` by the
  fixture tests. Whether ``-q`` writes different bytes on Windows or macOS is
  unknown.
* **Per-version curation of 2024.01.0 is partial**: version record, tuple
  layout, two default overrides and version ranges only -- not help, choices,
  bounds or inline comments. It exists for migration tests; it is not a
  supported Comet.
* **No project presets**: only Comet's three instrument-resolution presets.
* **Filesystem-dependent validation is not here**: database/spectra existence,
  the FASTA decoy scan (``R-DEC-02``), the PIN check (``R-DEC-04``), and "selected
  index and search options are compatible" (needs the ``.idx`` header). The
  last is **assigned by no phase document** -- escalated.
* **Not run by this phase, by instruction:** ``scripts/build.sh``,
  ``scripts/verify-all-gates.sh`` in full, ``--only tests``, ``--only quality``.
  ``--only install`` was run once (unit 7, because ``docs/conf.py`` changed):
  88 controls in 282 s.

Decisions encountered
=====================

No ``D-`` item was hit. Engineering decisions D6-1..D6-6 are in the work log;
judgement calls a later phase could revisit:

* **Duplicate declarations are a parse error** (Comet silently keeps the last).
* A missing ``# comet_version`` marker and a version mismatch are **warnings**
  naming both versions; a missing enzyme table is an error.
* The canonical form does **not** reproduce Comet's section comments; the parse
  result keeps every imported comment (``ImportedComments``).
* ``spectral_library_name`` is ``EMPTY_ALLOWED`` (empty = no library search).
* Decimals keep their digits and scale exactly (``15.9949`` stays,
  ``15.994915`` stays).
* The documentation generator also reads the real ``-q`` fixture (to name a
  missing entry and to take Comet's enzyme rows).

Surprises
=========

* ``-p`` lacks **twelve** non-slot parameters, not eleven as the phase document
  says; the specification's list is right.
* ``isotope_error`` accepts **0-7** (source and 2026.02 docs), not 0-5 as the
  ``-q`` comment says.
* Comet 2026.02.2 **ignores** ``spectral_library_ms_level`` (reads
  ``speclib_ms_level``) and ``add_U_selenocysteine``; it **reads**
  ``ms1_mass_range`` and ``precursor_NL_ions``, which ``-q`` does not write
  (they arrive as reported unknowns).
* Comet's undefined-enzyme check **never fires** (``CometData.h`` 345-365): the
  writer's refusal and the validator are the only guards.
* Comet looks for the marker only in the **first seven lines** (confirmed on
  the real binary).
* **Comet v2026.03.0 was released upstream on 2026-10-01** (new residue codes
  ``^``/``$``, ``index_search_type`` changes, undefined enzyme becomes an
  error). The release matrix does not have it; adding it will make the fixture
  matrix test fail until its fixtures are captured, then drift will list the
  differences.
* **``scripts/build.sh``'s 80 % mutation gate is module-wide** and passes a
  module whose ``validation`` package is 37.5 % mutation-covered (module
  88.7 %). Gate item 9 is per package; only ``verify-param-gates.sh`` grades it
  so. Escalated -- a gate-semantics question for tier 1, not edited here.
* ``GeneratedReferenceTest`` makes Python a test-time dependency of the module
  build (the PR workflow sets up ``.venv`` before Maven).
* Tier 1 committed ``STATUS.rst`` inside unit 1's build window; one docs build
  failed and then passed unchanged.

First thing the next agent should do
====================================

**Tier 1:** run the exit gate on a quiet tree -- ``bash scripts/build.sh``,
then ``bash scripts/verify-all-gates.sh`` in full (now with ``params``, about
5.5 minutes more). Before it, make sure ``scratch/phase05/artefacts/`` **and**
``scratch/phase06/artefacts/`` exist (``verify-test-gates.sh``'s precondition
checks only the first; the real-binary tests fail rather than skip without
either). If the ``tests`` control's sandbox build fails in ``cometgui-ui``
(``ProgressReachesTheInterfaceThreadTest`` timing out, seen once by the unit 2
agent under load), that is not this phase's module.

**Phase 07:** read ``docs/developer/comet_parameter_schema.rst`` first; bind
to ``CometParameters``, ``ValidationReport`` (findings carry parameter names
and category), ``PresetDiff``/apply-selected, and ``ParseResult`` for the raw
editor (``R-PARAM-08``: a failed parse yields no model).
