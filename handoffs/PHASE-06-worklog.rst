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

Not yet dispatched.

.. _p06-u2-signoff:

Unit 2
------

Not yet dispatched.

.. _p06-u3-signoff:

Unit 3
------

Not yet dispatched.

.. _p06-u4-signoff:

Unit 4
------

Not yet dispatched.

.. _p06-u5-signoff:

Unit 5
------

Not yet dispatched.

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

None yet.

Deferred
========

None yet.

Blockers escalated
==================

None yet. Reported upward, not blocking: the phase document's "eleven other
parameters" should read twelve (see *Starting state*).
