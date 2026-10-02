===================================================
PHASE-06 work log -- Comet Parameter Model
===================================================

:Phase: 06
:Phase orchestrator: Phase-06 orchestrator subagent (tier 2, dispatched by
   tier 1 session 09 under ``handoffs/PHASE-06-BRIEF.rst``)
:Started: 2026-10-01

Maintained by the phase orchestrator as the phase runs. A unit is not done
until it carries a sign-off entry naming what was run and what was observed --
"agent reported success" is not a sign-off.

.. contents:: Contents
   :depth: 1
   :local:

Starting state
==============

``git status --short`` clean at ``54a4512`` on ``main``. **No baseline build
was taken**, by the owner's build-economy rule (brief, *Build economy*): tier 1
ran ``scripts/build.sh`` and ``scripts/verify-all-gates.sh`` at Phase 05's exit
gate on this tree and runs them again at this phase's exit gate. Nobody in this
phase runs either in full.

``cometgui-params-comet`` holds seven ``package-info.java`` files and nothing
else: ``org.cometgui.params.comet`` and its ``schema``, ``parser``, ``writer``,
``validation``, ``presets`` and ``migration`` subpackages. Its POM already sets
``<cometgui.coverage.core.skip>false</cometgui.coverage.core.skip>`` (inert
until classes land) and does **not** switch the mutation gate on. Its only
dependency is ``cometgui-domain``. ``docs/reference/comet_parameters_generated.rst``
and ``docs/developer/comet_parameter_schema.rst`` are Phase 01 stubs.

Narrow timing measured here: ``mvn -B -o -q -pl cometgui-params-comet -am
test`` took **9 s** wall clock at ``54a4512``.

The ``-q`` / ``-p`` facts, re-verified rather than inherited
------------------------------------------------------------

Run by me on 2026-10-01 in a private scratch directory, against the mirror copy
``scratch/phase05/artefacts/v2026.02.2__comet.linux.exe``:

* ``sha256sum`` -> ``af515b6e...a44e6d9e``, equal to ``manifests/tools.json``'s
  ``sha256`` for the ``linux``/``x86-64`` Comet row.
* ``comet -q`` and ``comet -p``, each in an empty directory: both exit 0, both
  print ``Comet version "2026.02 rev. 2 (6edec91)"`` and ``Created:
  comet.params.new``. ``-q`` wrote 11 844 bytes, ``-p`` 10 214.
* Lines matching ``^[A-Za-z0-9_]* *=``: **118** for ``-q``, **96** for ``-p``.
  The name sets differ by exactly 22 names, all present in ``-q`` and absent
  from ``-p``, none the other way: ``variable_mod06``..``variable_mod15``,
  ``compoundmods_file``, ``mass_type_fragment``, ``mass_type_parent``,
  ``num_results``, ``peff_format``, ``peff_obo``,
  ``pinfile_protein_delimiter``, ``print_ascorepro_score``,
  ``print_expect_score``, ``protein_modslist_file``,
  ``spectral_library_ms_level``, ``spectral_library_name``.
* Today's ``-q`` output is byte-identical (``cmp``) to Phase 00's capture of
  2026-08-29 in ``scratch/upstream/pdump/q.params``.
* The first line of ``-q`` output is ``# comet_version 2026.02 rev. 2
  (6edec91)`` -- **not** ``2026.02.2``. Mapping between the marker and the
  manifest's version string is real work (unit 2).

**One correction to the record, reported upward:** the phase document and the
brief say ``-p`` "silently loses ten variable-modification slots and eleven
other parameters". It is **twelve** other parameters (22 - 10), which is what
``R-PARAM-01``'s own list enumerates. The specification is right; the phase
document's count is off by one. Not edited here (tier 1's file).

.. _p06-decisions:

Engineering decisions taken before decomposing
==============================================

Engineering choices, not ``D-`` items; recorded with the constraint that forced
each, so a later phase can tell whether the constraint still holds.

.. _p06-d-metadata:

D6-1. The curated metadata is ONE data file, read by Java and by the docs build
    ``R-DOC-04`` wants ``reference/comet_parameters_generated.rst`` generated
    *during the documentation build*, and ``CONTRIBUTING.rst`` says generated
    pages are produced there. Read the Docs builds with Python 3.11 and no JDK
    (``.readthedocs.yaml``), so the generator cannot run Java. The metadata is
    therefore a JSON resource inside the module --
    ``cometgui-params-comet/src/main/resources/org/cometgui/params/comet/schema/``
    -- read by the Java schema and, through a ``builder-inited`` hook in
    ``docs/conf.py`` exactly like Phase 05's ``toolmatrix``, by a standard-library
    Python generator under ``scripts/``. There is no second copy to diverge.

D6-2. Java reads that JSON with the project's one JSON reader
    ``org.cometgui.provenance.json.JsonReader``. Phase 05 established "one JSON
    reader, one hasher, one process launcher, one redactor". That needs a new
    **main** dependency edge ``cometgui-params-comet -> cometgui-provenance`` --
    the same edge ``cometgui-install`` already has. Provenance depends only on
    domain, so no cycle is possible. **Recorded here and in the handoff as an
    architectural change; ``docs/developer/architecture.rst``'s module table is
    updated by the unit that adds the edge.**

D6-3. The schema provider runs Comet through the domain ``ProcessRunner`` port
    ``CometParameterSchemaProvider`` (the specification's name) lives in
    ``org.cometgui.params.comet.schema`` and takes an injected
    ``org.cometgui.domain.ports.ProcessRunner``. No main edge to
    ``cometgui-process`` or ``cometgui-tools``; the one process launcher stays
    the one process launcher. Tests that run the real binary use the real
    ``org.cometgui.tools.process.ProcessService``, through a **test-scope**
    dependency on ``cometgui-process`` (which depends only on domain, so this
    cannot form a cycle).

D6-4. Fixtures are captured from the real binary and proved equal to it on every build
    ``cometgui-params-comet/src/test/resources/fixtures/comet/<version>/<os>-<arch>/``
    holds the bytes of ``comet.params.new`` as written by ``comet -q`` and by
    ``comet -p``, plus a ``SHA256SUMS``. A real-binary test runs the pinned
    binary (from the gitignored mirror, SHA-256 checked against
    ``manifests/tools.json`` first) through ``ProcessService`` and requires
    byte equality with the fixture, so a fixture typed or edited by hand fails
    the build. Comet versions are enumerated from ``manifests/tools.json``, so
    adding a Comet version without fixtures fails the build too. Only
    ``linux``/``x86-64`` can be executed here; other platforms' rows are named
    as unexecuted, never as passing.

D6-5. The mutation switch goes on with the first real class
    ``scripts/build.sh`` fails a module that compiles classes in a critical
    package (``org.cometgui.params.comet.*`` is already in the POM's
    ``targetClasses``) while its POM leaves ``cometgui.mutation.skip`` true. The
    first unit to add a class sets it false. Nothing changes in the parent
    POM's PIT configuration.

D6-6. Units run serially, one fresh agent each
    Owner's standing rule. No argument for parallelism exists here and none is
    attempted.

How every unit is signed off
============================

Per ``ONBOARDING.rst`` *Sign-off* and the brief's *Build economy*, for each
unit I:

#. read the whole diff (``git show``), and ``git status`` for strays;
#. run ``mvn -B -o -pl cometgui-params-comet -am verify`` (tests, Spotless,
   Checkstyle, SpotBugs, JaCoCo's module gate) and read the surefire counts;
#. run ``cometgui-archtests`` against the changed tree when a class, package or
   dependency edge was added;
#. run PIT on the one module, restricted to the classes the unit changed, and
   read ``mutations.xml`` -- never ``dev-verify.sh --mutation``;
#. inject at least one defect into **production** code that the agent did not
   try, in a private scratch directory, proving the edit landed (anchor matched
   once, marker grepped back, **compiled class hash changed**), deleting the
   surefire reports first so a Spotless/Checkstyle stop cannot leave a stale
   green report, then restore and ``sha256sum -c``;
#. run ``scripts/ci/docs-build.sh`` when ``.rst`` changed, and ``bash
   scripts/verify-all-gates.sh --only NAME`` for every gate whose inputs the
   unit changed -- always ``--only docs --only traceability`` when anything the
   documentation build reads, or a generated page, changed.

Work units
==========

.. list-table::
   :header-rows: 1
   :widths: 5 45 18 32

   * - #
     - Unit and acceptance conditions
     - Rules / gate items
     - Sign-off

   * - 1
     - **Real fixtures and their proof.** ``comet -q`` and ``comet -p`` output
       for every Comet version in ``manifests/tools.json`` (today one:
       2026.02.2), captured from the real binary, checked in under D6-4's
       layout with ``SHA256SUMS``. Real-binary tests through ``ProcessService``:
       fixture bytes equal fresh output; 118 vs 96 names and the exact 22-name
       difference re-derived; a manifest Comet version with no fixture fails;
       the mirror missing **fails, never skips**. Capture procedure recorded in
       ``docs/developer/comet_parameter_schema.rst`` (a *Fixtures* section) so a
       successor can reproduce it. Test-scope edge to ``cometgui-process``.
     - ``R-TEST-01``, ``R-PARAM-01``; enables gate 1-3
     - :ref:`p06-u1-signoff`

   * - 2
     - **Schema, curated metadata, discovery and drift.** The definition model
       (spec *Parameter definition model*: name, display name, category, value
       type, default, min/max, choices, visibility, help, version range,
       serialisation rule, validators). The JSON metadata (D6-1) for all 118
       parameters of 2026.02.2 or an explicit internal allow-list with a reason
       per entry. Loading via ``JsonReader`` (D6-2). Discovery from dump text:
       ``-q`` complete; ``-p`` fallback marked ``PARTIAL_DISCOVERY``, where a
       curated parameter absent from a partial dump is NOT reported removed.
       ``CometParameterSchemaProvider`` over the ``ProcessRunner`` port (D6-3).
       The **drift test** against the real ``-q`` fixture: fails on an
       unmodelled parameter, on a metadata entry the binary no longer reports,
       and on a curated default that differs from the binary's. Version marker
       ``2026.02 rev. 2 (6edec91)`` <-> ``2026.02.2``. Mutation switch on (D6-5).
     - ``R-PARAM-01``, ``02``, ``03``; gate 2; ``AC-PAR-01``, ``AC-PAR-02``
     - :ref:`p06-u2-signoff`

   * - 3
     - **Structured value types and their codecs.** Variable-modification tuple
       with **field layout taken from the version schema**, not a format
       string: mass, residues/terminus tokens, binary group, max or
       ``min,max`` count, terminal distance, terminus type, required /
       exclusive (``-1``), zero, one or two (``a,b``) neutral losses -- each
       fact cited to Comet's own source or documentation at tag ``v2026.02.2``.
       Enzyme table (``number. name sense cut no-cut``, custom enzymes),
       signed tolerance pair, two-value range, ion-series family. Parsing and
       formatting with ``Locale.ROOT``. Every tuple form round-trips in all
       fifteen slots.
     - ``R-PARAM-09`` (model half), ``R-PARAM-11``; gate 3, half of 4
     - :ref:`p06-u3-signoff`

   * - 4
     - **Parser, model, canonical writer.** Parameter set with value origin
       (default, preset, user, imported, workflow-enforced). Parser: every
       structural kind, comment structure, the ``# comet_version`` marker and
       ``R-PARAM-06``'s mismatch warning naming both versions, empty values,
       duplicates (a stated policy), malformed lines as located diagnostics,
       unknown parameters preserved and reported. Writer: deterministic,
       ``Locale.ROOT``, generated header naming the CometGUI and Comet versions,
       regenerated version marker, curated inline comments, unknown parameters
       round-tripped, **refuses an enzyme number absent from the table it
       writes**, writes once and hashes what it wrote through the
       ``HashService`` port (``R-PARAM-12``). Byte-stable double round trip of
       the real ``-q`` fixture; byte-identical output under a comma-decimal
       locale with the default locale restored.
     - ``R-PARAM-05``, ``06``, ``07``, ``08`` (model half), ``11``, ``12``;
       gate 1, 4, 5, 6; ``AC-PAR-06``, ``AC-PAR-11``
     - :ref:`p06-u4-signoff`

   * - 5
     - **Validation.** Per-field (type, range, enum, path form) and cross-field
       validators with field-attached diagnostics. ``R-PARAM-04``'s own rule for
       the precursor pair (``lower <= 0 <= upper``, warning not error for a
       deliberate asymmetric or same-signed window) **not** the generic
       ordering rule, and the generic rule still applied to the other ranges.
       Enzyme numbers exist in the table; tuple validity and version limits;
       ``R-PARAM-10``'s ``max_variable_mods_in_peptide`` /
       ``require_variable_mod`` cross-validation; ``R-CMT-01``'s enforced
       ``output_pepxmlfile`` / ``output_percolatorfile``; the model-level
       decoy rules (``R-DEC-01`` mode mapping, prefix present); a parameter
       unavailable in the selected version blocked, not ignored.
     - ``R-PARAM-04``, ``10``, ``R-CMT-01`` (model), ``R-DEC-01`` (model);
       gate 7
     - :ref:`p06-u5-signoff`

   * - 6
     - **Presets and migration.** Presets as versioned deltas recording the
       Comet version and schema they target; the low-low / high-low /
       high-high patterns; diff computation (current vs preset) with
       apply-all and apply-selected, nothing changed before apply; a
       compatibility check across versions. Migration of a parameter set
       between two schema versions, reporting added, removed and re-shaped
       parameters. Any second Comet version used as a fixture is captured from
       a real upstream binary with its SHA-256 recorded -- never hand-written.
     - In-scope items *Presets*, *Schema migration*
     - :ref:`p06-u6-signoff`

   * - 7
     - **Generated reference and developer page.** The Python generator
       (D6-1) behind ``reference/comet_parameters_generated.rst``, hooked into
       ``docs/conf.py``, covering every modelled parameter with ``R-DOC-04``'s
       fields, refusing (build failure) a metadata file it cannot render
       completely, with its own ``--check``/self-test. ``docs/developer/comet_parameter_schema.rst``
       written as built. ``docs/traceability-map.toml``: ``AC-PAR-01``,
       ``02``, ``06``, ``11`` moved from ``planned`` to named tests.
       ``--only docs`` and ``--only traceability`` green, including their
       sandbox copies (the Phase 05 unit 11 trap).
     - ``R-DOC-04``; gate 8
     - :ref:`p06-u7-signoff`

   * - 8
     - **Falsifiability harness and the mutation gate.**
       ``scripts/verify-param-gates.sh``: for each gate item 1-9 at least one
       injection into production code in a sandbox, each proved to have reached
       the bytecode, each graded on its own diagnostic, plus a harness-error
       control for an injection that did not land. Registered additively in
       ``scripts/verify-all-gates.sh`` with its measured floor. PIT >= 80 %
       reported **per package** for parser, writer and validation, and every
       surviving mutant in those packages read and either killed or argued
       equivalent -- none that suppresses a validation error or drops a
       parameter.
     - gate 9 and the falsifiability of 1-8; ``R-TEST-02`` for this module
     - :ref:`p06-u8-signoff`

Sign-off entries
================

.. _p06-u1-signoff:

Unit 1
------

**ACCEPTED 2026-10-01 at ``8218876``, no rework.** One fresh agent; 19 paths,
all inside its brief. No main class (as briefed), so the mutation switch is
still off and the JaCoCo gate still inert.

What I ran and saw:

* Read the whole diff: ``pom.xml`` (``cometgui-provenance`` compile scope,
  ``cometgui-process`` test scope, nothing else), thirteen test classes under
  ``org.cometgui.params.comet.fixtures``, the fixtures, a nested
  ``fixtures/comet/.gitattributes`` with ``* -text`` (the agent's addition, so a
  Windows ``core.autocrlf`` checkout cannot change the fixture bytes -- kept),
  the architecture-table row, and the new *Fixtures* section of
  ``docs/developer/comet_parameter_schema.rst``.
* **Fixtures against my own independent capture** (taken before dispatch, see
  *Starting state*): ``git show HEAD:<fixture> | cmp -`` -> identical for both
  ``comet-q.params`` and ``comet-p.params``; ``sha256sum -c SHA256SUMS`` -> both
  OK.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS`` in
  2m40s (the ``-am`` closure now includes provenance and process, hence slower
  than the 9 s measured at the start). Surefire XML: 34 tests, 0 failures, 0
  skipped, six classes; both real-binary dynamic tests executed.
* **Injection 1** (``DeclarationLines``, anchor ``^`` removed so a name inside
  a comment counts): class hash ``9fad581f`` -> ``15966eb2``; 5 failures,
  including ``completeDeclares118`` -- ``COMPLETE declares a name twice, so a
  count of lines is not a count of names ==> expected: <122> but was: <120>``.
  Restored, ``sha256sum -c`` OK.
* **Injection 2** (``UpstreamMirror.stage``'s checksum comparison made
  vacuous). My first form ``if (false && ...)`` was **stopped by Checkstyle**
  (``SimplifyBooleanExpression``) before any test ran, and my harness reported
  ``BYTECODE UNCHANGED`` rather than a pass -- the trap the brief names.
  Re-injected as ``!actual.equals(actual)``: class ``2299be4d`` ->
  ``3455e016``; ``UpstreamMirrorTest.aChecksumMismatchFails`` -- ``Expected
  java.lang.AssertionError to be thrown, but nothing was thrown.`` Restored,
  ``sha256sum -c`` OK.
* ``bash scripts/verify-all-gates.sh --only docs --only traceability`` ->
  ``2 control(s) passed, 0 failed, in 32 seconds``.
* **Not run here, deliberately:** ``--only tests``. Its sandbox copies module
  ``src`` and links ``scratch/`` and copies ``manifests/`` (read in
  ``scripts/verify-test-gates.sh``), so the new tests have what they need; I
  run it at unit 2's sign-off, where the mutation switch flips and the first
  main classes land, which is what that harness's census controls grade.

Noted: **tier 1 committed** ``51d8db1`` and ``72d5101`` (``nightly.yml``,
``STATUS.rst``) **while unit 1 was building**. The agent saw one
``docs-build.sh`` run exit 3 (12 pages missing after "build succeeded") that
passed unchanged on re-run; probably that overlap, unconfirmed. Reported upward.

.. _p06-u2-signoff:

Unit 2
------

**ACCEPTED 2026-10-02 at ``e53ecb5``, no rework.** One fresh agent; 46 files,
all inside its brief. The agent finished without delivering its report; tier 1
pointed this out and I fetched the report by message. I worked from the commit
and diff in the meantime.

What was built: ``schema/`` (30 classes -- definition model, 14 value kinds, 14
categories, ``MetadataLoader`` over ``JsonReader``, ``CometVersionMarker``,
discovery, drift as a value, ``CometParameterSchemaProvider`` over the domain
``ProcessRunner``), ``parser/`` (``ParamsLineReader``, line classification
only), ``comet-parameters.json`` (118 parameters modelled, **empty** internal
allow-list), the mutation switch on.

What I ran and saw:

* Read the diff and the JSON. Spot-checked enum labels against upstream:
  ``isotope_error`` carries values 6 and 7, which the binary's own ``-q``
  comment (0-5) and the specification's example omit; Comet's 2026.02 page
  says "Valid values are 0 through 7" -- the metadata is right.
  ``num_enzyme_termini`` 8/9 match the ``-q`` comment. Kinds: 37 decimal,
  17 integer enum, 15 tuple, 14 integer, 9 boolean, 7 ion-series, 5 path,
  3 enzyme reference, 3 integer range, 2 pair members, 2 decimal ranges,
  2 strings, 1 string enum, 1 decimal list. Visibility 57/46/15.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``;
  module ``Tests run: 217, Failures: 0, Errors: 0, Skipped: 0``; JaCoCo
  "All coverage checks have been met", LINE 901/906, BRANCH 315/317.
* **Population audit:** 42 compiled classes (no ``package-info``) and 42
  classes in ``jacoco.xml``. PIT mutated 21; the 9 top-level classes it did
  not (``ParamsLine``, ``Choice``, ``CometVersionRecord``,
  ``DiscoveredParameter``, ``DiscoveryMode``, ``DriftFinding``,
  ``InternalParameter``, ``MalformedDumpException``, ``VisibilityLevel``) were
  read: records, enums and an exception whose only statements are
  ``Objects.requireNonNull`` calls, which PIT's default mutators do not mutate.
  Nothing with logic is missing.
* **PIT, the module alone, with the POM's own ``targetClasses``** (tier 1's
  instruction): ``mvn -B -o -Dmaven.repo.local=_build/m2repo -pl
  cometgui-params-comet test-compile org.pitest:pitest-maven:mutationCoverage``
  -> exit 0 in 3m04s; scored as ``scripts/build.sh`` scores it
  (``status='KILLED'`` over ``mutations.xml``): **310/316 = 98.1 %** (4
  ``TIMED_OUT``, 2 ``SURVIVED``, both ``ParamsLineReader:137`` boundary
  mutants argued equivalent in a comment there; I agree -- a line starting with
  ``#`` never reaches that branch). With ``-am`` PIT fails in
  ``cometgui-domain`` ("No mutations found") when ``-DtargetClasses`` is
  narrowed, so the module-alone form is the one later units use; it resolves
  upstream modules from ``_build/m2repo`` jars of 2026-09-18 (the agent checked
  that nothing it uses changed since).
* **The switch goes red:** with ``MetadataLoaderTest``, ``SchemaDriftTest``,
  ``SchemaDriftFixtureTest``, ``BundledMetadataTest``,
  ``CometParameterSchemaProviderTest`` and ``ParamsLineReaderTest`` moved aside
  (four alone left 264/316 = 83.5 %, still green), PIT exits 1 with
  ``Mutation score of 78 is below threshold of 80`` and the ``build.sh``
  scoring reads **244/316 = 77.2 %**. Restored; ``sha256sum -c`` all OK;
  ``git status`` clean.
* **Injection 1** (``SchemaDrift.sameValue`` compares only the first token of
  a default). My first form ``index < 1`` went red **for the wrong reason** --
  21 ``ArrayIndexOutOfBounds`` errors on empty defaults -- so I did not count
  it. Re-injected as ``index < Math.min(1, left.length)``: class
  ``756c4f92`` -> ``452ece0d``; exactly one failure,
  ``SchemaDriftTest.defaultDiffersInAPartialDump`` -- ``PARTIAL_DISCOVERY:
  declared 10, modelled 9, allow-listed 1, findings 0 ==> expected: <1> but
  was: <0>``. **Thin:** one constructed test is all that guards a later-token
  default (``variable_mod01``'s ``15.9949 M 0 3 -1 0 0 0.0``); noted for unit
  8's harness.
* **Injection 2** (``MetadataLoader`` duplicate-name check never matches):
  class ``760e5840`` -> ``14138057``;
  ``MetadataLoaderTest.aDuplicateNameIsRejected`` -- ``Expected
  ...InvalidMetadataException to be thrown, but nothing was thrown.``
* ArchUnit: ``mvn -B -o -pl cometgui-archtests -am test
  -Dtest='org.cometgui.archtests.**' -Dsurefire.failIfNoSpecifiedTests=false``
  -> ``Tests run: 21, Failures: 0``, ``BUILD SUCCESS``.
* Docs: the agent's ``--only docs --only traceability`` ran green (2 controls,
  71 s); I re-run both on this sign-off commit.
* **``--only tests`` not run, by tier 1's instruction** (2026-10-02: it took
  2977 s at the Phase 05 exit gate; tier 1 runs it once, at this phase's exit
  gate). I had started it and stopped it on that instruction; it works in
  ``_build/test-gate-sandbox`` and the tree was untouched. **For tier 1's
  exit-gate run:** the unit 2 agent ran it once, in a loaded window, and its
  control 7 failed with ``cometgui-ui``'s
  ``ProgressReachesTheInterfaceThreadTest.aTerminalReportRebuildsTheRowListOnTheInterfaceThread``
  timing out after 30 s inside the sandbox ``build.sh`` while
  ``cometgui-params-comet`` built SUCCESS there. Unexplained, unreproduced,
  and not in this phase's module.

Findings recorded by the agent that later units inherit: Comet 2026.02.2's
reader ignores ``spectral_library_ms_level`` (it looks for
``speclib_ms_level``) and ``add_U_selenocysteine``; where documentation and
source disagree the source at the tag wins; every number in the JSON is a
string (``JsonReader`` rejects fractions); inline comments are not yet in the
metadata (unit 4's).

.. _p06-u3-signoff:

Unit 3
------

**ACCEPTED 2026-10-02 at ``4e48146``, no rework.** One fresh agent; 32 paths,
inside its brief (new package ``org.cometgui.params.comet.value``;
``VariableModField``/``VariableModLayout`` in ``schema``; the loader now reads
and checks ``versions[].variableModTuple`` instead of requiring ``null``).

What I ran and saw:

* Read the diff and the layout added to the JSON (eight fields, pairs allowed
  on ``COUNT`` and ``NEUTRAL_LOSS``). **Checked the cited source myself**:
  ``Comet.cpp`` at tag ``v2026.02.2`` (raw GitHub, 1208 lines), lines 552-621
  read the tuple with ``sscanf(szParamVal, "%lf %31s %d %511s %d %d %d %s"``,
  split the neutral loss on ``,`` into ``dNeutralLoss``/``dNeutralLoss2``, and
  the count into min/max when it holds a ``,`` -- exactly the layout encoded.
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``
  (7m35s on this machine); module ``Tests run: 580, Failures: 0, Errors: 0,
  Skipped: 0``; JaCoCo LINE 1424/1430, BRANCH 567/571; 66 compiled classes,
  66 in ``jacoco.xml``.
* PIT, module alone, POM ``targetClasses`` (1m32s): **542/548 = 98.9 %**
  killed (``build.sh`` scoring). Non-kills are unit 2's: the two
  ``ParamsLineReader:137`` equivalents, three ``ParamsLineReader`` timeouts
  (lines 78, 87) and ``CometParameterSchemaProvider$Collector:289``. Every
  class unit 3 added was mutated; the unmutated set is unit 2's nine
  null-check-only types, unchanged.
* **Injection 1** (``VariableModCodec`` writes ``max,min`` instead of
  ``min,max``): class ``6f0b9922`` -> ``0de97de6``; 30 round-trip failures,
  e.g. ``min,max count ==> expected: <79.966331 STY 0 2,4 -1 0 0 0.0> but was:
  <79.966331 STY 0 4,2 -1 0 0 0.0>``. Restored, ``sha256sum -c`` OK.
* **Injection 2** (``TolerancePair.parse`` takes the absolute value of the
  lower bound -- the sign of a normally-negative tolerance lost): class
  ``990322d6`` -> ``8ae84a76``; ``StructuredValuesTest.readsTheRealDefaults``
  -- ``expected: <-20.0> but was: <20.0>`` -- and ``keepsSignAndScale``.
  Restored, ``sha256sum -c`` OK.
* ``bash scripts/verify-all-gates.sh --only docs --only traceability`` ->
  ``2 control(s) passed, 0 failed, in 47 seconds``. ``git status`` clean.

Inherited by later units, from the agent's report: decimals are written with
``BigDecimal.toPlainString()`` (digits and scale kept exactly, so
``15.9949`` stays and ``15.994915`` stays; ``+`` and exponents normalised);
the enzyme table is written with Comet's own column widths 4/23/7/12, so the
default table is byte-identical; a duplicate enzyme number is refused (Comet
would silently use the later row); only ``variable_mod01``..``15`` exist even
though ``-q``'s comment invites more (``VMODS = 15``). Two parameters Comet
2026.02.2 **reads but ``-q`` does not write** -- ``ms1_mass_range`` and
``precursor_NL_ions`` -- are not in the metadata, so an imported file using
them meets the unknown-parameter path (preserved and reported, ``R-PARAM-07``);
recorded for the handoff, not a gate item. Upstream: ``mass_offsets``' reading
loop appears never to advance past a non-numeric token (read, not run).

.. _p06-u4-signoff:

Unit 4
------

**ACCEPTED 2026-10-02 at ``de95807``, after one round of rework.** First
commit ``14e43da`` (33 files: new ``model`` package -- ``CometParameters``,
``ParameterEntry``, sealed ``ParameterValue``, ``ValueOrigin``,
``UnknownParameter``, ``Diagnostic``; ``CometParamsParser``/``ParseResult``;
``CanonicalParamsWriter``/``WrittenParams``; a curated ``inlineComment`` on all
118 parameters, 87 of them Comet's own ``-q`` text; ``value/Numbers`` made
public, visibility only -- I read that diff). Rework ``de95807`` (7 files).

**Rejection, round 1** (recorded under *Rejections and rework*): the agent's
report said imported comments on modelled parameters were not kept. ``R-PARAM-05``
says "The parser shall preserve the file's comment structure: the leading
``# comet_version`` marker line, block comments between parameters, and inline
trailing comments." Sent back; the rework added ``parser/ImportedComments`` on
``ParseResult`` (marker, per-parameter block comments, inline comment,
continuation lines, the lines around the enzyme table), left the canonical
writer and the model's equality untouched, and pinned the canonical text's
SHA-256 (``f381afe1...d62b``, 10 656 bytes) in ``CanonicalWriterTest.textUnchanged``
so the rework provably did not move it.

What I ran and saw, on ``de95807``:

* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``
  (2m58s); module ``Tests run: 690, Failures: 0, Errors: 0, Skipped: 0``;
  JaCoCo LINE 2039/2046, BRANCH 771/777; **93 compiled classes, 93 in
  ``jacoco.xml``**.
* PIT, module alone, POM ``targetClasses``: **722/728 = 99.2 %** killed
  (``build.sh`` scoring); per package ``model`` 89/89, ``writer`` 30/30,
  ``value`` 200/200, ``schema`` 307/308, ``parser`` 96/101. All six non-kills
  are unit 2's, already accepted (``ParamsLineReader:137`` x2 equivalent;
  ``ParamsLineReader:78``/``87`` and the provider's ``Collector:289``
  ``TIMED_OUT``). **No survivor in ``model``, ``parser`` (unit 4's code) or
  ``writer``.**
* **Injection 1** (the writer silently skips every parameter whose value text
  is empty -- ``peff_obo``, ``mass_offsets``... dropped): class ``2a55b941`` ->
  ``61be8a33``; 4 failures in *gate item 1*, e.g. ``textUnchanged`` --
  ``expected: <f381afe1...d62b> but was: <185bce30...f38a>`` -- plus
  ``valuesAsComet``, ``secondParseIsTheSameModel``, ``inlineComments``.
  Restored, ``sha256sum -c`` OK.
* **Injection 2** (the parser stamps declared values ``COMET_DEFAULT`` instead
  of ``IMPORTED``): ``CometParamsParser$Run.class`` ``55f17fd9`` ->
  ``50209b96``; 6 failures, e.g. ``realDefaultsFile`` -- the default-origin set
  expected as the 22 ``-q``-only names, was every one of the 118 -- and
  ``everyParameterImported``, ``emptyIsAValue``, ``customEnzymeSurvives``.
  Restored, ``sha256sum -c`` OK.
* ``CometReadsCanonicalRealBinaryTest`` executed (surefire: 1 test, 0
  failures): the real binary given the canonical file behaves exactly as given
  its own ``-q`` file (same warning, same "input file not found", exit 1). The
  agent states plainly what that does **not** prove -- that Comet interprets
  each value as the model means it.
* ``--only docs --only traceability`` -> ``2 control(s) passed, 0 failed, in 36
  seconds``. ``git status`` clean.

Inherited from the agent's findings, all cited to the source at the tag:
Comet looks for the marker only in the first seven lines (the writer's header
goes after it; confirmed on the real binary: 7 comment lines before the marker
refused, 6 accepted); Comet keeps the last of two duplicate declarations --
**our policy is to refuse the file, naming both lines (a choice, open to
review)**; Comet's undefined-enzyme checks never fire (the writer's refusal is
the only guard); Comet reads only the first token of a text value; the
canonical form does not reproduce Comet's section comments (the parse result
keeps them).

.. _p06-u5-signoff:

Unit 5
------

**ACCEPTED 2026-10-02 at ``55aa0b7``, no rework.** One fresh agent; 35 files:
new ``validation`` package (``ValidationReport``/``Finding``/``Rule`` -- 33
rules, each with a stable id and fixed severity -- ``CometValidator`` and the
rule classes), ``model/DecoySource`` and three model operations
(``decoySource``, ``withDecoySource``, ``withWorkflowEnforcedOutputs``), and two
metadata edits I read and accept: ``scan_range`` gains ``ordered_range`` (it
was the one two-value range without it), and ``spectral_library_name`` becomes
``EMPTY_ALLOWED`` (the source treats an empty name as "no library search";
cited ``CometSearchManager.cpp`` L1961-1972).

What I ran and saw:

* Read ``TolerancePairRule`` in full: reversed (``lower > upper``) is an
  error and returns; a window not containing 0 is a ``same_signed`` warning;
  otherwise a non-symmetric window is an ``asymmetric`` warning. The generic
  ``OrderedRangeRule`` is not applied to the pair (the agent's own injection
  (a) routes it there and 14 tests fail).
* ``mvn -B -o -pl cometgui-params-comet -am verify`` -> ``BUILD SUCCESS``;
  module ``Tests run: 882, Failures: 0, Errors: 0, Skipped: 0``; **113
  compiled classes, 113 in ``jacoco.xml``**.
* PIT, module alone: **910/917** killed (``build.sh`` scoring); ``validation``
  180/181, ``model`` 97/97, ``writer`` 30/30, ``value`` 200/200, ``schema``
  307/308, ``parser`` 96/101. The one new non-kill,
  ``VariableModRules:178`` (boundary on ``requirementCode() > 0``), I read: it
  is reached only for codes outside {-1, 0, 1}, where ``> 0`` and ``>= 0``
  agree, and it only chooses a warning's wording -- equivalent, suppresses
  nothing. The rest are unit 2's accepted six.
* **Injection 1** (the reversed-pair error can never fire:
  ``compareTo(...) > 0`` -> ``> 1``): class ``7354fc6d`` -> ``0e913fcb``; 7
  failures, e.g. ``TolerancePairRuleTest.boundaries`` -- ``expected:
  <PAIR_REVERSED> but was: <PAIR_SAME_SIGNED>`` for ``20.0001 / 20`` -- and
  ``ValidatorCoverageTest.everyDeclarationChecked`` [9], [10]. Restored,
  ``sha256sum -c`` OK.
* **Injection 2** (``DecoySource.COMET_INTERNAL_CONCATENATED`` mapped to
  ``decoy_search = 2``, the separate mode): class ``54cbb092`` ->
  ``e38345b3``; ``DecoyTextAndPathTest.onTheModel`` -- ``expected:
  <Optional[COMET_INTERNAL_SEPARATE]> but was:
  <Optional[COMET_INTERNAL_CONCATENATED]>`` -- and ``mapping``. Restored,
  ``sha256sum -c`` OK.
* ``--only docs --only traceability`` -> ``2 control(s) passed, 0 failed, in 38
  seconds``. ``git status`` clean.

Acceptance condition 4 as briefed ("the real ``-q`` defaults validate with no
errors") conflicts with ``R-CMT-01``: Comet's default
``output_percolatorfile = 0`` **is** an error for this workflow. The agent
reports exactly that one error on the raw file and **0 errors, 0 warnings**
after ``withWorkflowEnforcedOutputs()``. That is the right reading; the brief
was loose.

**Unassigned specification item, escalated:** *Comet validation* lists
"selected index and search options are compatible". It depends on whether
``database_name`` names an existing ``.idx`` and what that index records (a
fragment index uses only the first five variable modifications,
``Constants.h`` L77) -- it needs the filesystem. Not implemented here; the
developer page records it as Phase 08's, but **no phase document assigns it**.

.. _p06-u6-signoff:

Unit 6
------

Not yet dispatched.

.. _p06-u7-signoff:

Unit 7
------

Not yet dispatched.

.. _p06-u8-signoff:

Unit 8
------

Not yet dispatched.

Rejections and rework
=====================

* **Unit 4, round 1 (``14e43da``)** -- sent back on ``R-PARAM-05``: the parser
  discarded block and inline comments of modelled parameters. Reworked at
  ``de95807`` without moving the canonical output (its SHA-256 is now pinned by
  a test). Accepted.

Deferred
========

None yet.

Blockers escalated
==================

None yet. Reported upward, not blocking:

* the phase document's "eleven other parameters" should read twelve (see
  *Starting state*);
* tier 1 committed ``STATUS.rst`` and ``nightly.yml`` inside unit 1's build
  window; one documentation build in that window failed and then passed
  unchanged (unit 1 sign-off);
* for tier 1's exit-gate ``--only tests`` run: one sandbox failure seen by the
  unit 2 agent in ``cometgui-ui`` (unit 2 sign-off);
* the specification's "selected index and search options are compatible"
  validation item needs the filesystem and no phase document assigns it
  (unit 5 sign-off).
