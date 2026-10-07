.. _dev-comet-parameter-schema:

======================
Comet parameter schema
======================

.. note::

   **Status: written as built, Phase 06.** The real-binary fixtures (unit 1);
   the schema model, the curated metadata file, the line-level reader, schema
   discovery, the version marker, drift detection and the schema provider
   (unit 2); the structured value types and their codecs (unit 3); the typed
   model with value origins, the parser, the canonical writer and write-once
   hashing (unit 4); validation, with the workflow-enforced outputs and the
   decoy source on the model (unit 5); presets, diffs, schema migration and a
   second, real Comet version to migrate from (unit 6); and the generated
   :doc:`../reference/comet_parameters_generated` (``R-DOC-04``, unit 7,
   :ref:`dev-comet-parameter-generated-reference`).

   **Comet 2026.03.0 intake, unit 1** (``handoffs/COMET-2026-03-worklog.rst``):
   the real 2026.03.0 fixtures, its version record, per-version
   :ref:`overrides <dev-comet-parameter-overrides>` beyond the default, drift
   for every captured and every installed release, and what each release does
   with the parameters that differ (:ref:`dev-comet-parameter-202603`).

   **Comet 2026.03.0 intake, unit 3**: the residue alphabet of the
   variable-modification tuple as per-release data
   (:ref:`dev-comet-parameter-residue-alphabet`), so ``^`` and ``$`` are legal
   for 2026.03.0 only (:ref:`dev-comet-parameter-202603-termini`); Phase 06
   gate items 1, 3, 4, 5 and 6 for 2026.03.0 as for 2026.02.2; and the real
   2026.03.0 binary reading the canonical file
   (:ref:`dev-comet-parameter-comet-reads`).

   **Comet 2026.03.0 intake, unit 4**: validation agreed with both real
   binaries -- version-scoped rule severities as data in each release's
   version record (:ref:`dev-comet-parameter-rule-severities`,
   :ref:`dev-comet-parameter-version-scoped-rules`), three new rules, and a
   corpus of 42 cases each release's binary was run on, replayed by a
   real-binary test (:ref:`dev-comet-parameter-validation-corpus`); the
   2026.03.0 help of ``variable_modNN`` and ``output_txtfile``
   (:ref:`dev-comet-parameter-202603-help`).

   **Comet 2026.03.0 intake, unit 5**: what a release does with values written
   for another, as data in its version record
   (:ref:`dev-comet-parameter-value-migrations`), and migration of the real
   2026.02.2 and 2024.01.0 files to 2026.03.0, accepted by the real 2026.03.0
   binary (:ref:`dev-comet-parameter-migration-202603`).

   **Phase 07, unit 1**: the migration review that blocks a run
   (:ref:`dev-comet-parameter-migration-review`). **Unit 3**: each offered
   release's starting set, bundled as its own ``-q`` output
   (:ref:`dev-comet-parameter-release-defaults`).

What this page covers
=====================

How the typed, versioned parameter schema is built from Comet's own ``-q``
output plus curated metadata, how it is parsed, written, validated and
migrated, and how the drift test catches a schema that no longer matches the
binary.

.. _dev-comet-parameter-fixtures:

Fixtures
========

Every test of the parameter model reads the parameter files Comet itself
writes, never a file typed to match what a parser expects (``R-TEST-01``).

What the files are
------------------

::

    cometgui-params-comet/src/test/resources/fixtures/comet/
        <version>/<os>-<arch>/comet-q.params
        <version>/<os>-<arch>/comet-p.params
        <version>/<os>-<arch>/SHA256SUMS

``<version>``, ``<os>`` and ``<arch>`` are spelled exactly as
``manifests/tools.json`` spells them. ``comet-q.params`` is the unmodified
``comet.params.new`` written by ``comet -q`` (the complete file, 118
parameters for 2026.02.2 and for 2026.03.0); ``comet-p.params`` is the one
written by ``comet -p`` (the default file, 96 for 2026.02.2, 95 for
2026.03.0). ``SHA256SUMS`` is ``sha256sum`` output for both.
A ``.gitattributes`` in ``fixtures/comet/`` sets ``-text`` so that Git never
converts their line endings on checkout -- ``core.autocrlf=true`` is the
default on a Windows runner and would otherwise change the bytes.

Tests locate fixtures through
``org.cometgui.params.comet.fixtures.CometFixtures`` (test sources) by
version, platform and mode rather than by path.

There are two sets, both **linux/x86-64**: Comet 2026.02.2 and Comet
2026.03.0.

.. list-table::
   :header-rows: 1
   :widths: 12 22 12 54

   * - Version
     - File
     - Bytes
     - SHA-256
   * - 2026.02.2
     - ``comet-q.params``
     - 11 844
     - ``d15048709f485c09a840dcb2a384dc301b4ba4da4566c2ea053e9c17eae58f51``
   * - 2026.02.2
     - ``comet-p.params``
     - 10 214
     - ``b56c967959ae04e2e782411858b2f5be06863e572cf1bfdfeccc7a4b28fbb19c``
   * - 2026.03.0
     - ``comet-q.params``
     - 12 551
     - ``4bbf39f943f011f16ea6f2380f396f05a06b85579496ece7d5265d3d7bac7aeb``
   * - 2026.03.0
     - ``comet-p.params``
     - 10 256
     - ``0b6abd4c7415a9289d858d3c8fc84031c31d62acf10f174a8123070ebd8fb8fc``

The 2026.03.0 set was captured before ``manifests/tools.json`` named that
release, because the registry cannot gain a version whose schema does not exist
yet. The intake's unit 2 added its rows, so the real-binary test now re-proves
these bytes from the binary too, reading its checksum from the manifest.

The manifest also names Comet 2026.02.2 for linux/aarch64, macos/x86-64,
macos/aarch64 and windows/x86-64, and Comet 2026.03.0 for linux/aarch64,
macos/aarch64 and windows/x86-64. **Their output has never been captured,
because those binaries have never been executed here**; only linux/x86-64
runs on this project's host. The fixture tests print each such row as ``NOT
CAPTURED`` rather than treating it as covered.

How they were produced
----------------------

Captured on 2026-10-01 from the pinned Linux binary, in a private scratch
directory under ``_build/``, from the repository root::

    $ mkdir -p _build/p06-u1/bin _build/p06-u1/q _build/p06-u1/p
    $ cp scratch/phase05/artefacts/v2026.02.2__comet.linux.exe _build/p06-u1/bin/comet
    $ chmod 700 _build/p06-u1/bin/comet
    $ sha256sum _build/p06-u1/bin/comet
    af515b6ed5a17efafff7277a6a9c73cee97e26d38f3c9b2a8da16adaa44e6d9e  _build/p06-u1/bin/comet
    $ B=$(realpath _build/p06-u1/bin/comet)
    $ (cd _build/p06-u1/q && "$B" -q)     # exit 0, prints the banner and
    $ (cd _build/p06-u1/p && "$B" -p)     # "Created:  comet.params.new"

The SHA-256 equals the ``sha256`` of the ``comet``/``linux``/``x86-64`` row of
``manifests/tools.json`` (URL
``https://github.com/UWPR/Comet/releases/download/v2026.02.2/comet.linux.exe``).
Each run used its own **empty** working directory, because Comet writes
``comet.params.new`` into the working directory under either option. The two
files were then copied, unmodified, to ``comet-q.params`` and
``comet-p.params``, and ``SHA256SUMS`` was written with ``sha256sum
comet-p.params comet-q.params``. The ``-q`` output is byte-identical to Phase
00's capture of 2026-08-29.

The first line of either file is ``# comet_version 2026.02 rev. 2
(6edec91)`` -- Comet's own spelling, not the manifest's ``2026.02.2``.

Comet 2026.03.0 was captured on 2026-10-04 the same way, from
``https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe``
(7 077 008 bytes; its SHA-256, below, equals the one the work package's brief
gives, which the orchestrator had checked against the digest the GitHub
releases API publishes for the asset), mirrored as
``scratch/phase05/artefacts/v2026.03.0__comet.linux.exe``::

    $ curl -sSfL -o <scratch>/comet.linux.exe \
        https://github.com/UWPR/Comet/releases/download/v2026.03.0/comet.linux.exe
    $ cp <scratch>/comet.linux.exe scratch/phase05/artefacts/v2026.03.0__comet.linux.exe
    $ mkdir -p _build/c2603-u1/bin _build/c2603-u1/q _build/c2603-u1/p
    $ cp scratch/phase05/artefacts/v2026.03.0__comet.linux.exe _build/c2603-u1/bin/comet
    $ chmod 700 _build/c2603-u1/bin/comet
    $ sha256sum _build/c2603-u1/bin/comet
    ad93b4cf60c2ed7afc2f41b8a3a05567c3938d999f17deb7b3676bc1fa91e7ed  _build/c2603-u1/bin/comet
    $ B=$(realpath _build/c2603-u1/bin/comet)
    $ (cd _build/c2603-u1/q && "$B" -q)     # exit 0, banner Comet version
    $ (cd _build/c2603-u1/p && "$B" -p)     # "2026.03 rev. 0 (fa08489)", then
                                            # "Created:  comet.params.new"
    $ F=cometgui-params-comet/src/test/resources/fixtures/comet/2026.03.0/linux-x86-64
    $ mkdir -p "$F"
    $ cp _build/c2603-u1/q/comet.params.new "$F/comet-q.params"
    $ cp _build/c2603-u1/p/comet.params.new "$F/comet-p.params"
    $ (cd "$F" && sha256sum comet-p.params comet-q.params > SHA256SUMS && cat SHA256SUMS)
    0b6abd4c7415a9289d858d3c8fc84031c31d62acf10f174a8123070ebd8fb8fc  comet-p.params
    4bbf39f943f011f16ea6f2380f396f05a06b85579496ece7d5265d3d7bac7aeb  comet-q.params

Both ``-q`` and ``-p`` start ``# comet_version 2026.03 rev. 0 (fa08489)``. The
``-q`` file declares the same 118 names as 2026.02.2's; ``-p`` leaves out 23 --
2026.02.2's 22 plus ``index_search_type``, which 2026.03.0 writes only for
``-q`` [V26Q]_. Against 2026.02.2's ``-q`` the whole-file difference is the
marker, ``decoy_search``'s inline comment, ``index_search_type``'s block
comment, inline comment and default (``1`` becomes ``-1``), and five new
comment lines above the variable modifications documenting ``^``, ``$``,
``-2`` and the terminus codes. Nothing else.

What the build re-proves on every run
-------------------------------------

All in ``cometgui-params-comet``'s test sources, package
``org.cometgui.params.comet.fixtures``:

* ``CometFixtureRealBinaryTest`` (Linux only): for every Comet version in the
  manifest with a linux/x86-64 row, stages the binary from the gitignored
  mirror ``scratch/phase05/artefacts/``, checks its SHA-256 against the
  manifest (read with ``JsonReader``), runs ``-q`` and ``-p`` through
  ``ProcessService`` in empty directories, and requires the written file to
  equal the fixture **byte for byte**. A missing mirror binary, a checksum
  mismatch, a non-zero exit or a missing ``comet.params.new`` each *fail* with
  a diagnostic; none of them skips. So a fixture edited by hand fails the
  build even if its ``SHA256SUMS`` is regenerated to match.
* ``FixtureMatrixTest`` (every platform): every distinct Comet ``version`` in
  ``manifests/tools.json`` has a ``linux-x86-64`` directory with both files
  and a ``SHA256SUMS`` that lists both and verifies. A manifest version with no
  fixtures fails the build.
* ``FixtureMatrixTest`` also verifies **every** fixture directory against its
  ``SHA256SUMS``, the manifest's or not, so a captured release whose rows have
  not landed yet is still checked byte for byte.
* ``ParameterDumpFactsTest`` (every platform): the ``R-PARAM-01`` facts --
  for 2026.02.2, ``-q`` declares 118 parameters, ``-p`` 96, ``-p`` declares
  nothing ``-q`` does not, and the difference is exactly the 22 names the
  specification lists, typed into the test from the specification rather than
  derived from the fixtures; for 2026.03.0, 118 and 95 and exactly 23 names,
  typed by hand from this project's own run of the binary, and ``-q``'s name
  set equal to 2026.02.2's. Each ``-q`` file starts with its version's
  ``# comet_version`` marker and ends with the ``[COMET_ENZYME_INFO]`` table.
  Declarations are counted with a line rule (a name at column one, optional
  blanks, ``=``), not a parser.

Adding a new Comet version
--------------------------

#. Add its rows to ``manifests/tools.json`` (the installer's work, not this
   page's). From that commit on, ``FixtureMatrixTest`` fails until step 4 is
   done -- by design.
#. Put the linux/x86-64 binary in the mirror as
   ``scratch/phase05/artefacts/<releaseTag>__<file name from its URL>`` and
   check ``sha256sum`` against the manifest row.
#. Run it with ``-q`` and with ``-p``, each in its own empty directory, exactly
   as above.
#. Copy each ``comet.params.new`` unmodified to
   ``fixtures/comet/<version>/linux-x86-64/comet-q.params`` and
   ``comet-p.params``, and write ``SHA256SUMS`` there with ``sha256sum
   comet-p.params comet-q.params``. Confirm with ``git diff --stat`` and
   ``git check-attr -a`` that Git sees them as ``text: unset`` and stores them
   unchanged.
#. Run ``mvn -B -o -pl cometgui-params-comet -am verify``. The real-binary
   test must pass for the new version. ``ParameterDumpFactsTest`` asserts each
   captured version's facts; a new version's counts are a new fact,
   established by running it and typed into the test, not copied from the
   file.
#. The drift test (:ref:`dev-comet-parameter-drift`) runs for the new fixture
   directory at once and fails until the metadata has a version record for
   it: a captured release can never sit unchecked, whether or not the
   manifest names it yet. The record's ``variableModTuple`` carries the
   release's field layout **and its residue alphabet**
   (:ref:`dev-comet-parameter-residue-alphabet`), each read from the
   release's own source and established by running it.
#. Give the release a row in ``ReleaseWriterGateTest`` (which fails for a
   fixture release without one), a round-trip entry point in
   ``VariableModRoundTripTest`` (which fails for an installed release without
   one) and a row in ``CometReadsCanonicalRealBinaryTest``, each with facts
   observed from the release, not copied from another.

Do not add fixtures for a platform whose binary was not executed. Its row
stays reported as never captured.

.. _dev-comet-parameter-schema-model:

The schema
==========

Two sources, which must agree (``R-PARAM-01`` to ``R-PARAM-03``):

* **Comet's own output.** ``comet -q`` writes the complete parameter file for
  its version. It says which parameters exist, their order, the default value
  text and Comet's own inline comment for each, and the default enzyme table.
* **Curated metadata.** One JSON file in the module says what the binary
  cannot: display names, categories, help, enum labels, bounds, relationships,
  visibility levels and version ranges (decision D6-1).

Package ``org.cometgui.params.comet.schema``:

.. list-table::
   :header-rows: 1
   :widths: 32 68

   * - Type
     - What it is
   * - ``ParameterDefinition``
     - The specification's *Parameter definition model*: name, display name,
       category, value kind, default for the version, min/max, labelled
       choices, visibility, short help, detailed-help reference, supported
       version range, serialisation rule and validator ids -- plus search
       aliases and related parameters, which ``R-DOC-04`` and *Global parameter
       search* need. Values are held as the text ``comet.params`` uses; typing
       them is the parser's and the codecs' job.
   * - ``ValueKind``
     - The structural kinds: ``INTEGER``, ``DECIMAL``, ``STRING``,
       ``BOOLEAN_FLAG``, ``INTEGER_ENUM``, ``STRING_ENUM``, ``FILE_PATH``,
       ``INTEGER_RANGE`` and ``DECIMAL_RANGE`` (two values on one line),
       ``DECIMAL_LIST``, ``TOLERANCE_PAIR_MEMBER``, ``VARIABLE_MOD_TUPLE``,
       ``ENZYME_REFERENCE`` and ``ION_SERIES_FLAG``. The codecs for the
       structured kinds are in ``org.cometgui.params.comet.value``
       (:ref:`dev-comet-parameter-structured`).
   * - ``VariableModField``, ``VariableModLayout``
     - What a tuple field can be, and one Comet version's tuple layout as the
       metadata records it (:ref:`dev-comet-parameter-tuple`).
   * - ``SerializationRule``
     - ``SINGLE_VALUE``, ``EMPTY_ALLOWED``, ``TWO_VALUES``, ``VALUE_LIST``,
       ``TUPLE``. The specification's *empty-valued parameter* is the rule
       ``EMPTY_ALLOWED`` rather than a kind, because it is orthogonal to the
       kind: three of the four empty-by-default parameters are also paths. An
       empty value is a value, written ``name =``.
   * - ``ParameterCategory``
     - The fourteen Advanced-mode groups of *Parameter editor levels*, in the
       specification's order.
   * - ``VisibilityLevel``
     - ``ESSENTIALS``, ``ADVANCED``, ``EXPERT``: the lowest level that shows a
       parameter.
   * - ``ValidatorId``
     - The closed vocabulary of named rules: ``choice``, ``path``,
       ``ordered_range``, ``signed_tolerance_pair``, ``enzyme_in_table``,
       ``variable_mod_tuple``, ``workflow_enforced``. The rules themselves are
       the validation package's.
   * - ``CuratedMetadata``, ``MetadataLoader``
     - The loaded metadata, and the only way to obtain a checked one.
   * - ``CometVersionMarker``
     - The ``# comet_version`` line, mapped to the manifest's version.
   * - ``SchemaDiscovery``, ``DiscoveredSchema``, ``DiscoveryMode``
     - A dump read into names, default texts, inline comments and enzyme rows,
       marked ``COMPLETE`` or ``PARTIAL_DISCOVERY``.
   * - ``SchemaDrift``, ``DriftReport``, ``DriftFinding``
     - Drift detection as a value.
   * - ``CometParameterSchemaProvider``, ``CometToolIdentity``,
       ``CometParameterSchema``, ``CometSchemaException``
     - Running a build for its dump, through the domain ``ProcessRunner`` port.

.. _dev-comet-parameter-metadata-format:

The metadata file
=================

``cometgui-params-comet/src/main/resources/org/cometgui/params/comet/schema/comet-parameters.json``.
Java reads it with the project's one JSON reader
(``org.cometgui.provenance.json.JsonReader``, decision D6-2); the
documentation generator (:ref:`dev-comet-parameter-generated-reference`)
reads it with Python's standard library. The
Java reader accepts whole numbers only, so **every bound and default is a JSON
string**, written exactly as ``comet.params`` writes it (``"20.0"``,
``"0 0"``, ``"15.9949 M 0 3 -1 0 0 0.0"``). Every field is always present;
JSON ``null`` marks an absent value. A field the loader does not know is an
error, in every object.

Top level::

    {
      "schemaVersion": 1,
      "description": "...",
      "versions":    [ { "version", "marker", "parameterPages", "source",
                         "variableModTuple": { "source",
                                               "fields": [ { "field", "kind", "pair" } ],
                                               "residueAlphabet": { "characters", "source" } },
                         "overrides": [ { "name", "source", ...any of "default",
                                          "choices", "inlineComment",
                                          "shortHelp", "helpUrl" } ],
                         "ruleSeverities": [ { "rule", "severity", "source" } ] } ],
      "categories":  [ { "id", "displayName" } ],         // exactly the fourteen
      "enzymeTable": { "header", "helpUrl", "rowFormat",
                       "senseChoices": [ { "value", "label" } ],
                       "referencedBy": [ "search_enzyme_number", ... ] },
      "internal":    [ { "name", "reason" } ],            // the allow-list
      "parameters":  [ { ...one object per parameter... } ]
    }

``versions`` records each Comet release the metadata was curated against: the
manifest's spelling (``2026.02.2``), the binary's marker
(``2026.02 rev. 2 (6edec91)``), and the upstream pages and source tree the
help was written from, and ``variableModTuple``: that release's
variable-modification tuple layout, in Comet's reading order -- see
:ref:`dev-comet-parameter-tuple`. In each ``fields`` entry ``field`` is a
``VariableModField`` constant, ``kind`` its ``VariableModField.Kind``
(``DECIMAL``, ``INTEGER`` or ``RESIDUES``) and ``pair`` a JSON boolean, the
only booleans in the file. ``residueAlphabet`` is the characters that release
accepts in the tuple's residue token, each once, with the ``https://``
reference to where the release shows them -- see
:ref:`dev-comet-parameter-residue-alphabet`.

``overrides`` lists what **that release** says differently about a parameter
(:ref:`dev-comet-parameter-overrides`); it is empty for a release that agrees
with every curated definition. ``ruleSeverities`` states, for each
version-scoped validation rule, what that release's binary does with what the
rule checks (:ref:`dev-comet-parameter-rule-severities`). Three records exist
(:ref:`dev-comet-parameter-202603`, :ref:`dev-comet-parameter-older-release`):

.. list-table::
   :header-rows: 1
   :widths: 14 26 60

   * - ``version``
     - ``marker``
     - What else it records
   * - ``2026.03.0``
     - ``2026.03 rev. 0 (fa08489)``
     - The default verified release from specification revision 12
       (``D-010``). The same tuple layout as 2026.02.2, read by
       ``Comet.cpp`` lines 556-625 at ``v2026.03.0`` [V26T]_; its residue
       alphabet adds ``^`` and ``$`` to ``A``-``Z``, ``n`` and ``c``
       [V26A]_ (:ref:`dev-comet-parameter-202603-termini`). Twenty overrides:
       ``index_search_type`` (default ``-1``, choices ``-1``/``0``/``1``,
       Comet's own 2026.03.0 inline comment, help and help page),
       ``decoy_search`` (Comet's own 2026.03.0 inline comment),
       ``spectral_library_ms_level`` and ``add_U_selenocysteine`` (inline
       comment, help and help reference: what 2026.03.0 really does with each),
       ``output_txtfile`` (inline comment and help naming 2026.03.0) and
       ``variable_mod01`` to ``variable_mod15`` (help naming ``^`` and ``$``,
       and the 2026.03 page) -- :ref:`dev-comet-parameter-202603-help`. Rule
       severities: a distance below -2 an **error**, ``index_search_type``
       without an ``.idx`` a **warning**.
   * - ``2026.02.2``
     - ``2026.02 rev. 2 (6edec91)``
     - Neutral loss and count both take a comma pair; residue alphabet
       ``A``-``Z``, ``n``, ``c`` [M1380]_. ``overrides`` empty: every curated
       definition is 2026.02.2's own. Rule severities: a distance below -2 a
       **warning**, ``index_search_type`` without an ``.idx`` **off**.
   * - ``2024.01.0``
     - ``2024.01 rev. 0 (f00df0c)``
     - The migration fixture's release, not offered to users. The neutral loss
       takes **one** value -- Comet 2024.01.0 reads it with ``%lf`` [V24T]_
       -- and the count takes ``min,max``; residue alphabet ``A``-``Z``,
       ``n``, ``c`` [V24A]_. Two default overrides:
       ``fragindex_num_spectrumpeaks = 100`` and
       ``fragindex_skipreadprecursors = 0`` [V24D]_, where 2026.02.2 writes
       ``150`` and ``1``. Rule severities as 2026.02.2's (it has no
       ``index_search_type``).

``CuratedMetadata.parametersFor(version)`` and ``parameter(name, version)``
give each definition **as that version has it** -- the version's override
applied where there is one -- so the parser's defaults,
``CometParameters.defaults``, ``resetToDefault``, the choice rule, the
canonical writer's inline comment and the drift test all use the version's
own facts. ``parameter(name)`` alone gives the curated definition, whatever
the version.

A parameter's ``versions`` range is a claim about the **curated** releases:
``from`` must be one of them, and the claim is checked against each curated
release's real ``-q`` output by the drift test. Releases between two curated
ones are not described -- the parser and the codecs refuse a version the
metadata has no record of -- so ``"from": "2026.02.2"`` on
``pinfile_protein_delimiter`` says that Comet 2024.01.0 does not declare it,
not that 2024.01.1 (which introduced it) does not have it.

One parameter::

    {
      "name": "isotope_error",
      "displayName": "Precursor isotope offsets",
      "category": "precursor_mass",          // a categories id
      "kind": "INTEGER_ENUM",                // a ValueKind constant
      "visibility": "ESSENTIALS",            // a VisibilityLevel constant
      "default": "2",                        // what comet -q writes
      "min": null, "max": null,              // strings, numeric kinds only
      "choices": [ { "value": "0", "label": "Off: monoisotopic mass only" }, ... ],
      "shortHelp": "...",                    // our words, not upstream's
      "inlineComment": "0=off, 1=0/1 (C13 error), ...",  // or null; see below
      "helpUrl": "https://uwpr.github.io/Comet/parameters/parameters_202602/isotope_error.html",
      "versions": { "from": "2026.02.2", "through": null },
      "serialization": "SINGLE_VALUE",       // a SerializationRule constant
      "validators": [ "choice" ],            // ValidatorId ids
      "aliases": [ "C13 error", "isotope", "monoisotopic peak" ],
      "related": [ "peptide_mass_tolerance_upper", "peptide_mass_tolerance_lower" ]
    }

What ``MetadataLoader`` refuses, naming the parameter (or section) and the
field: a field missing or unknown; a name Comet could not declare, or one
defined twice; a blank display name or help; an unknown category, kind,
visibility, serialisation or validator; a serialisation the kind does not
allow; an enumerated kind with fewer than two choices, or a non-enumerated
kind with any; a choice without a label, listed twice, or not whole for an
integer enum; a default that is not one of its choices, not ``0``/``1`` for a
flag, not a number of the right shape for a numeric kind, outside its own
``min``/``max``, empty where empty is not a value, or padded with white space;
bounds on a non-numeric kind, or ``min`` above ``max``; a version range that
does not start at a curated version or ends before it starts; an
``inlineComment`` that is not a string or ``null``, is blank, holds a line
break, or has white space at either end; an override that breaks any of the
rules under :ref:`dev-comet-parameter-overrides`; a tolerance-pair
member carrying the generic ``ordered_range`` rule (``R-PARAM-04``); a related
parameter that is unknown, the parameter itself, or named twice; a help or
source reference that is not ``https://``; an allow-list entry without a
reason, also modelled, or listed twice; and an enzyme-table section that does
not list exactly the ``ENZYME_REFERENCE`` parameters. For the tuple layout:
a ``variableModTuple`` that is not an object (``null`` included); a member,
field constant or kind it does not know; a ``pair`` that is not a boolean; a
field listed twice, declared with a kind that is not the field's, or given a
pair where no Comet release accepts one (only the count and the neutral loss
are pairable); a layout without the mass difference or the residues; a
parameter named ``variable_mod`` plus two digits that is not of kind
``VARIABLE_MOD_TUPLE``, or the other way round; a tuple default whose
field count differs from the layout of a version it claims, or whose residues
hold a character outside that version's alphabet. For the alphabet
(``variableModTuple.residueAlphabet``): missing, or not an object; a member
other than ``characters`` and ``source``; ``characters`` missing, not a
string, or empty; a character that is neither a letter ``A``-``Z`` nor one of
the four terminal codes; a character listed twice; and a ``source`` that is not
``https://``. ``ResidueAlphabetLoaderTest`` proves each on constructed
metadata.

``inlineComment`` is the comment the canonical writer puts after the value on
the parameter's own line (:ref:`dev-comet-parameter-canonical`), or ``null``
for none. The starting text is Comet's own ``-q`` comment for the parameter,
verbatim, for the 87 parameters that have one -- **each release's own**: where
a release's ``-q`` comment differs (2026.03.0's ``decoy_search``,
``index_search_type`` and ``add_U_selenocysteine``), that release's override
carries it. Curated away from it, because the ``-q`` text is wrong for the
release: for both 2026.02.2 and 2026.03.0, ``isotope_error`` adds the values 6
and 7, ``output_txtfile`` drops the "2=Crux-formatted" that the parameter code
treats as 1, and ``spectral_library_ms_level`` (which ``-q`` writes without a
comment) says what the release does with it; for 2026.02.2 only,
``add_U_selenocysteine`` says that Comet ignores it (these facts are below and
in :ref:`dev-comet-parameter-202603`). ``Comet202603CurationTest`` holds each
release's comments to its own ``-q`` file with exactly those deviations,
typed in: four for 2026.02.2, three for 2026.03.0. It must be one line because
it is written on one line, and unpadded because the reader trims it.

.. _dev-comet-parameter-overrides:

Per-version overrides
---------------------

Decision C-2 of the Comet 2026.03.0 intake: a fact that differs between Comet
releases is **data in that release's version record**, never an ``if
(version ...)`` in a codec or a rule. A parameter's object holds the curated
definition -- the facts of the release it was curated from, which every later
release inherits -- and a version record's ``overrides`` replaces, for that
release only, the fields that release says differently::

    {
      "name": "index_search_type",
      "source": "https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L939-L945",
      "default": "-1",
      "choices": [ { "value": "-1", "label": "Not set: ..." },
                   { "value": "0",  "label": "Peptide index (PI_DB)" },
                   { "value": "1",  "label": "Fragment-ion index (FI_DB)" } ],
      "inlineComment": "0=create peptide index, 1=create fragment ion index; ...",
      "shortHelp": "Used only when database_name names an .idx file ...",
      "helpUrl": "https://uwpr.github.io/Comet/parameters/parameters_202603/index_search_type.html"
    }

``name`` and ``source`` (the ``https://`` reference to where the release
shows the difference) are required; then **any** of the five fields that can
differ by release: ``default``, ``choices``, ``inlineComment``, ``shortHelp``
and ``helpUrl``. A field left out is inherited from the curated definition --
this is the one object in the file where an absent field means something --
and ``"inlineComment": null`` replaces the curated comment by none. Nothing
else can be overridden: a parameter's name, kind, category, bounds,
serialisation and validators are the same in every release it is modelled
for, and a release that changes one of them is a new parameter or a closed
version range.

Java holds an override as ``ParameterOverride`` and applies it with
``applyTo``; ``CometVersionRecord.overrides()`` maps parameter names to them,
and ``defaults()`` is the view of the overrides that replace a default. The
documentation generator applies the same fields the same way
(``definition_for`` in ``scripts/cometparams.py``).

What ``MetadataLoader`` refuses in an override, naming ``versions[i] override
for "<name>"`` and the field: a field that is not ``name``, ``source`` or one
of the five (``displayName`` and ``kind`` included, and ``version`` -- an
override has no version of its own; it belongs to the record it is in); an
override that replaces no field; a ``name`` that is not modelled, or whose
version range does not claim the record's version; a parameter overridden
twice; a ``source`` or ``helpUrl`` that is not ``https://``; **any replaced
field equal to the curated one** (an override records only a difference);
choices for a kind that is not enumerated, or choices that break the curated
choice rules (fewer than two, unlabelled or blank label, a value listed twice,
an integer-enum value that is not whole); a default that breaks its kind or
its curated bounds; **a release whose resulting default is not one of its
resulting choices** -- whether the default changed, the choices changed, or
both; an inline comment that is blank, holds a line break or is padded; blank
short help; and an ``overrides`` member that is not an array of objects.
``VersionOverridesLoaderTest`` proves each on constructed metadata, and
``VersionDefaultsLoaderTest`` the default-only cases that predate the other
four fields.

The format kept ``schemaVersion`` 1. A saved preset records the metadata
schema version it was made against, and bumping it would have orphaned every
user preset although no parameter changed; a reader that predates
``overrides`` refuses the document anyway, on the unknown field.

**Where later facts attach.** The version record is the place for anything
else a release changes. The residue alphabet of the variable-modification
tuple (2026.03.0's ``^`` and ``$``) is in that release's ``variableModTuple``,
beside the field layout it qualifies (:ref:`dev-comet-parameter-residue-alphabet`).
Version-keyed validation facts -- a rule's severity for that release -- are
the record's ``ruleSeverities``, keyed by the rule's stable identifier
(:ref:`dev-comet-parameter-rule-severities`), so that a rule reads the fact
from the version the model carries. What a release does with a value written
for another -- Comet 2026.02.2's ``index_search_type = 1``, which 2026.03.0
spells ``-1`` -- is the record's ``valueMigrations``
(:ref:`dev-comet-parameter-value-migrations`).

.. _dev-comet-parameter-rule-severities:

Rule severities
---------------

Decision C-2 again, for validation: where Comet releases judge a
configuration differently -- one refuses it, another warns, a third says
nothing -- the rule that checks it is **version-scoped**, and each release's
version record states its severity::

    "ruleSeverities": [
      { "rule": "index_search_type.ignored_without_idx", "severity": "WARNING",
        "source": "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1764-L1775" },
      { "rule": "variable_mod_tuple.distance_undocumented", "severity": "ERROR",
        "source": "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1436-L1450" }
    ]

``rule`` is the rule's stable identifier (``Rule.id()``), ``severity`` one of
``ERROR``, ``WARNING`` and ``OFF`` (the release has nothing to report, so the
rule finds nothing for it), and ``source`` the ``https://`` reference to the
release behaviour the level encodes. Java holds an entry as ``RuleSeverity``;
``CometVersionRecord.ruleSeverities()`` maps rule identifiers to them.

There is **no default and no inheritance**: every release states every
version-scoped rule, so a release added later cannot be judged by a severity
nobody chose for it. The member is required like every other (empty is
legal JSON, and then the validator refuses the record). What
``MetadataLoader`` refuses, naming ``versions[i] severity of rule "<id>"`` and
the field: a missing member or one that is not an array of objects; an entry
field other than the three; a ``rule`` that is blank or not an identifier
(lower-case words, a dot, lower-case words); a rule stated twice; a
``severity`` that is not one of the three constants; a ``source`` that is not
``https://``. The schema does not know the rules, so the validator, which
does, refuses the rest when it judges a model of that release
(``VersionSeverities``, an ``IllegalStateException`` naming the release): a
rule that does not exist, a rule whose severity is fixed, and a
version-scoped rule the record does not state. ``RuleSeveritiesLoaderTest``
and ``VersionScopedRulesTest`` prove each. The documentation generator
refuses the same malformed entries, and a release whose set of rules differs
from another's, and renders the table of severities on the generated
reference (:ref:`comet-rule-severities`).

Where the words come from
-------------------------

Help text, enum meanings and labels were written for this project from
Comet's own documentation for the release modelled, the 2026.02 page set at
``https://uwpr.github.io/Comet/parameters/parameters_202602/``, and from the
source at tag ``v2026.02.2`` (commit
``6edec914959a6fe2dfa898f8b395d775bb2d2751``) -- chiefly ``LoadParameters``
and ``PrintParams`` in ``Comet.cpp`` and the parameter handling in
``CometSearch/CometSearchManager.cpp``. Each parameter's ``helpUrl`` names its
page, or the source line where Comet publishes no page
(``compoundmods_file``, ``spectral_library_name``,
``spectral_library_ms_level``). Where the page and the source disagree the
source wins and the help says so: ``isotope_error`` accepts 0 to 7 although
the ``-q`` comment lists 0 to 5, and ``output_txtfile`` value 2 is treated as
1 by the 2026.02.2 parameter code.

Two upstream defects of 2026.02.2 are recorded in its help rather than hidden
(2026.03.0 fixed the names; what it does instead is in
:ref:`dev-comet-parameter-202603`, and its override says so):

* ``spectral_library_ms_level`` is written by ``-q``, but Comet 2026.02.2's
  reader looks for ``speclib_ms_level`` and its search code for
  ``spectraL_library_ms_level``, so a value under the written name is ignored.
* ``add_U_selenocysteine`` is read, but the static mass for U is taken from
  ``add_U_user_amino_acid``, a name ``-q`` does not write, so a value under
  the written name has no effect.

The allow-list
--------------

``internal`` exists for parameters Comet declares that are hidden or internal
and deliberately not modelled, each with a reason. For Comet 2026.02.2 and
2026.03.0 it is **empty**: every one of the 118 parameters ``-q`` declares is
modelled, including the two above, because a user can set them.

Visibility
----------

``ESSENTIALS`` holds what *Parameter editor levels* lists for Essentials: the
database, the precursor tolerance pair, units, tolerance type and isotope
handling, the fragment bin width and offset, the search enzyme, enzymatic
termini and missed cleavages, the static and variable modifications, the
decoy mode and prefix, the thread count, and the two outputs the workflow
requires. ``EXPERT`` holds parameters few searches need or that do nothing in
this release; everything else is ``ADVANCED``.

.. _dev-comet-parameter-discovery:

Discovery and the version marker
================================

The line reader
---------------

``org.cometgui.params.comet.parser.ParamsLineReader`` is the one place that
knows how a ``comet.params`` line is shaped. It classifies every line and
interprets nothing: the ``# comet_version`` marker, a comment (first non-blank
character ``#``), a blank line, a declaration ``name = value  # comment``
before the enzyme table (value and inline comment separated and trimmed; an
empty value kept as the empty string), the ``[COMET_ENZYME_INFO]`` header,
enzyme rows (kept verbatim), and anything else as malformed with its line
number and a reason. It keeps every line's exact text and whether the file
ended with a newline. It is stricter than Comet's own reader where Comet is
silently lenient -- a name with a space in it, a declaration after the enzyme
table, a second table header -- so that nothing imported is lost unreported.
The typed parser is built on it.

Discovery
---------

``SchemaDiscovery.discover(text, mode)`` turns a dump into names in order,
default texts, inline comments and enzyme rows. A malformed line, a name
declared twice, a first line that is not a marker this project can map, or a
missing enzyme table each fail, naming the line: a dump is Comet's own
output, so anything unexpected in it is reported, not tolerated. ``-q``
output is ``COMPLETE``; ``-p`` output is ``PARTIAL_DISCOVERY``
(``R-PARAM-02``).

The version marker
------------------

Comet 2026.02.2 writes ``# comet_version 2026.02 rev. 2 (6edec91)``: the
``comet_version`` macro ``"2026.02 rev. 2"`` from ``CometSearch/Common.h``,
then the first seven characters of the build's commit in parentheses. The
manifest, the release tag and ``ToolVersion`` call it ``2026.02.2``.
``CometVersionMarker`` maps release ``YYYY.NN`` and revision ``R`` to
``YYYY.NN.R`` and keeps the build hash separately; ``releaseTextFor`` maps
back. Comet's own check of a marker is lenient (any text containing
``2026.0``, ``2025.0`` or ``2024.0`` passes); this parser accepts only
``YYYY.NN rev. R`` with an optional ``(hash)``.

The provider
------------

``CometParameterSchemaProvider.schemaFor(comet)`` runs
``[executable, "-q"]`` when the build was probed to have
``COMPLETE_PARAMS_QUERY`` and ``[executable, "-p"]`` otherwise, through the
injected domain ``ProcessRunner`` (decision D6-3), in a fresh empty directory
of its own under a configured root, with an empty environment map, and reads
``comet.params.new`` from there before deleting the directory. It fails with
a ``CometSchemaException`` whose ``failure()`` is one of ``LAUNCH_FAILED``,
``TIMED_OUT`` (the process is cancelled), ``INTERRUPTED``, ``NON_ZERO_EXIT``,
``NO_FILE_WRITTEN``, ``UNREADABLE_DUMP`` or ``VERSION_MISMATCH`` (the dump's
marker names another version than the build's identity), with the command,
the directory and up to 200 lines of output.

.. _dev-comet-parameter-drift:

Drift detection
===============

``SchemaDrift.compare(discovered, metadata)`` returns a ``DriftReport``: the
version, the mode, the counts (declared, modelled, allow-listed) and every
finding, each naming the parameter. The rules:

``UNMODELLED``
    A declared parameter with no metadata for the dump's version and no
    allow-list entry. Reported from complete and partial dumps alike.
``NOT_DECLARED``
    A parameter the metadata claims for the version -- or allow-lists -- that
    a **complete** dump does not declare. **Never** reported from a
    ``PARTIAL_DISCOVERY`` dump: ``-p`` leaves out parameters the binary
    supports (22 of 118 for 2026.02.2), so absence from it proves nothing.
``DEFAULT_DIFFERS``
    The curated default differs from the binary's. Compared token by token;
    two tokens agree if their text is equal or both are numbers of equal value,
    so ``0.0`` against ``0.0000`` agrees and ``0.0`` against ``0.01`` does not.
``VERSION_RECORD``
    The metadata has no record of the dump's version, or records a different
    marker for it -- nothing curated has been checked against that build.

A version range with no upper end claims every later release, so a new Comet
version's ``-q`` output either confirms each claim or fails the build.

The drift test
--------------

``SchemaDriftFixtureTest`` (test sources, package
``org.cometgui.params.comet.schema``) runs the bundled metadata against the
real ``-q`` fixture of every Comet version that has a fixture directory under
``fixtures/comet/`` **and** every version ``manifests/tools.json`` names, and
fails on any finding with the whole report. So a manifest version without
fixtures fails (as ``FixtureMatrixTest`` also requires), and a captured
release the metadata has no record of fails on its ``VERSION_RECORD``
finding -- a release cannot be captured and then sit unchecked until its
manifest rows land. For 2026.02.2 and for 2026.03.0 it asserts 118 declared,
118 modelled, 0 allow-listed, no finding. It also proves the partial rule
against the real ``-p`` fixtures: for 2026.02.2 none of the 22 ``-q``-only
parameters is reported, and a parameter removed from the metadata still is;
for 2026.03.0, ``PARTIAL_DISCOVERY`` with 95 declared and modelled and none of
its 23 ``-q``-only parameters reported.

For 2026.03.0 specifically, the test shows the drift test going red on each
defect it exists to catch:

* an entry removed from the metadata (``index_search_type``):
  ``UNMODELLED: Comet 2026.03.0 declares index_search_type (line 14, ...)``,
  117 modelled;
* a parameter claimed for 2026.03.0 by its version range that 2026.03.0's
  ``-q`` does not write (a constructed ``ms1_mass_range`` from 2026.03.0):
  ``NOT_DECLARED``, while 2026.02.2's drift stays clean;
* an override claiming a parameter for 2026.03.0 whose range does not: the
  loader refuses the document (``is not modelled for Comet 2026.03.0``);
* the 2026.03.0 ``index_search_type`` override stripped: ``DEFAULT_DIFFERS``
  -- the metadata's default is "1" and Comet 2026.03.0 writes "-1";
* the real 2026.03.0 ``-q`` under a marker no record names (``2099.01 rev.
  0``), or under another build hash: ``VERSION_RECORD``.

It also runs against the **migration fixture's** real Comet 2024.01.0 output
(:ref:`dev-comet-parameter-older-release`): ``-q`` 109 declared, 109 modelled,
0 allow-listed, no finding; ``-p`` (``PARTIAL_DISCOVERY``) 87 and 87.
``OlderReleaseCurationTest`` proves that what is curated about 2024.01.0 is
checked rather than trusted: stripping the two default overrides makes the
drift test report exactly those two ``DEFAULT_DIFFERS``; claiming a
2026-only parameter for 2024.01.0 is reported ``NOT_DECLARED``; and dropping
2024.01.0 from a parameter it declares is reported ``UNMODELLED``.

To add a Comet version: capture its fixtures (*Fixtures*, above), add its
``versions`` record, and run the module's tests -- the drift test runs for it
from the moment its fixture directory exists. Each finding is a decision
to make against that release's documentation and source -- a new parameter
to describe, a range to close with ``through``, a default to update -- never
a reason to widen the allow-list for a parameter a user could set.

.. _dev-comet-parameter-202603:

Comet 2026.03.0
===============

Comet 2026.03.0 becomes the default verified release (``D-010``,
specification revision 12); 2026.02.2 stays supported. Its version record and
the reason for each override follow. Every fact was established by running
**both** real binaries on 2026-10-04, in a private scratch directory, each
probe a copy of that release's own ``-q`` file with ``database_name`` pointed
at a one-protein FASTA (UniProt P02769, bovine serum albumin), the
``spectral_library_name`` placeholder emptied, and one or two lines changed,
searched as ``comet -P<file> BSA3.mzML`` (a local bovine serum albumin run,
not a project fixture); the source lines cited are at the two tags.

``index_search_type``
---------------------

.. list-table::
   :header-rows: 1
   :widths: 10 45 45

   * - Value
     - Comet 2026.02.2
     - Comet 2026.03.0
   * - ``-1``
     - FASTA search: silent. ``database_name = bsa.fasta.idx`` (missing, so
       built on demand): a **fragment ion index** (the ``.idx`` header's
       ``IndexSearchType:`` line), silent.
     - FASTA: silent. Built on demand: fragment ion index, silent.
   * - ``0``
     - FASTA: silent. Built: **peptide index**.
     - FASTA: ``Warning - index_search_type = 0 is ignored: "bsa.fasta" is not
       an .idx file (plain FASTA search) ...``. Built: peptide index.
   * - ``1``
     - FASTA: silent. Built: fragment ion index.
     - FASTA: the same warning for ``1``. Built: fragment ion index, silent.
   * - ``99``
     - FASTA: silent. Built: fragment ion index, silent.
     - ``Warning - index_search_type = 99 is not -1, 0 or 1; using the default
       (-1, not set).`` in both cases; built: fragment ion index.

Every run exited 0. **Decision: ``-1`` is a legal choice for 2026.03.0 only.**
In 2026.02.2 it is accepted silently, but so is every integer: both places
that read the value test ``== 0`` and nothing else [V22I]_ [V22J]_, so ``-1``
builds exactly what ``1`` builds -- and what ``99`` builds -- and the release's
``-q`` comment and parameter page document ``0`` and ``1`` only. There it has
no meaning of its own, and offering it would invite a value that reads as
"not set" in a release where it is not. In 2026.03.0, ``-1`` is what ``-q``
writes [V26Q]_, the value any other one is coerced to [V26I]_, and the only
value that never warns [V26W]_. The 2026.03.0 override therefore carries
``default`` ``-1``, choices ``-1``/``0``/``1``, Comet's own 2026.03.0 inline
comment, new help and the 2026.03 parameter page; 2026.02.2 keeps choices
``0``/``1`` and the choice rule refuses ``-1`` there
(``Comet202603CurationTest``).
What migration does with a 2026.02.2 file's ``1`` (and ``0``), with the
runs against existing indexes, is in :ref:`dev-comet-parameter-migration-202603`.

``spectral_library_ms_level``
-----------------------------

* **2026.02.2:** every run of Comet's own ``-q`` file logs ``Warning - invalid
  parameter found: spectral_library_ms_level.  Parameter will be ignored.``:
  the reader registers ``speclib_ms_level`` [V22L]_, and the search manager
  then asks for ``spectraL_library_ms_level`` [V22M]_, so the value is ignored
  under every spelling. (``speclib_ms_level = 2`` draws no warning in 2026.02.2
  and no effect either.)
* **2026.03.0:** no warning; the reader registers the written name and the
  search manager reads it into the options [V26L]_ (``speclib_ms_level`` now
  draws the "invalid parameter" warning). But the value's **only** reader is
  the loader for a Thermo ``.raw`` spectral library [V26R]_, which prints
  ``Error - raw files as spectral libraries are not supported yet.`` and exits
  before it gets there. Run with ``spectral_library_name = lib.raw`` (an empty
  file) at level ``1`` and at level ``7``: both exit 1 with exactly that
  message -- in both releases. Level ``7`` with no library: silent, exit 0.

So 2026.03.0 reads the name, and nothing a command-line search does depends on
it. The 2026.03.0 override says so in its help and inline comment, cites the
loader as its help reference (Comet publishes no page for the parameter: the
2026.03 page set returns 404 for it), and keeps the choices ``1``/``2``/``3``.

``add_U_selenocysteine``
------------------------

* **2026.02.2:** with ``add_U_selenocysteine = 10.0`` the pepXML header has
  no ``aminoacid_modification`` for ``U``: the search manager takes the U
  static mass from ``add_U_user_amino_acid`` [V22U]_, and that name is not one
  the reader knows (``add_U_user_amino_acid = 10.0`` logs ``Warning - invalid
  parameter found: add_U_user_amino_acid``), so no ``comet.params`` can set it.
* **2026.03.0:** with ``add_U_selenocysteine = 10.0`` the header carries
  ``<aminoacid_modification aminoacid="U" massdiff="10.000000"
  mass="160.953633" variable="N"/>`` -- 150.953633 plus 10, so the mass table
  carries the modification [V26U]_.

The 2026.03.0 override restores Comet's own inline comment and plain help, and
points at the 2026.03 page, which documents the parameter; 2026.02.2's
definition still says that release ignores it.

``decoy_search``
----------------

Only Comet's own inline comment changed: 2026.03.0's ``-q`` adds ``; .idx
searches use the value the index was built with``. Both releases store the
decoy mode in an index's header and read it back from there [V22D]_ [V26D]_,
so this documents
existing behaviour rather than a change; the override carries the new comment
because the canonical file for a release takes that release's comments.

.. _dev-comet-parameter-202603-help:

``output_txtfile`` and ``variable_modNN``: help that names the release
------------------------------------------------------------------------

Two curated texts named the wrong release when 2026.03.0 inherited them; its
record now overrides both.

* ``output_txtfile``. The curated inline comment and help say that
  **2026.02.2** treats ``2`` (the "Crux-formatted" value ``-q`` offers) as
  ``1``. 2026.03.0 does the same: its parameter code tests ``== 0`` and writes
  the text file for any other value [V26O]_. Run on 2026-10-04 with 2026.03.0's
  own ``-q`` file (the base edits of :ref:`dev-comet-parameter-validation-corpus`)
  and ``output_txtfile`` ``1`` and then ``2``: both exit 0, silent, and write
  the same ``.txt`` body (26 lines, the same SHA-256). The 2026.03.0 override
  says ``0=no, 1=yes  write tab-delimited txt file (2026.03.0 treats 2 as 1)``,
  help naming 2026.03.0, and the 2026.03 page; the choices are unchanged, so
  ``2`` stays legal and the file reads back unchanged. ``ReleaseWriterGateTest``
  re-pinned the 2026.03.0 canonical SHA-256 for this one comment, and only for
  it: reversing that substitution in the new text gives the old pinned value.
* ``variable_mod01`` to ``variable_mod15``. The curated help says "n and c for
  termini". 2026.03.0's ``-q`` comment documents ``^`` and ``$`` and the
  position fields [V26M]_; its override (fifteen, one per slot) says ``n`` and
  ``c`` for any peptide terminus and ``^`` and ``$`` for the protein's, that
  2026.03.0 refuses a distance other than -1, -2 or 0 and up, and the terminus
  codes, and points at the 2026.03 page. ``Comet202603CurationTest`` holds
  both releases' texts.

.. _dev-comet-parameter-202603-termini:

``^`` and ``$`` in the residue token
------------------------------------

Comet 2026.03.0 adds two terminal codes to the residue field of
``variable_modNN``: ``^``, the protein N-terminus only, and ``$``, the protein
C-terminus only, "combined with residues in the same string" as ``n`` and
``c`` are [VM26]_. Its own ``-q`` output now says so above the slots (``^ =
protein N-terminus only, $ = protein C-terminus only (e.g. 42.010565 ^ 0 1 -1 0
0 0.0)``), and the 2026.03 page's generator validates ``n``, ``c``, ``^``,
``$`` and ``A``-``Z``, where the 2026.02 page's validated ``n``, ``c`` and
``A``-``Z`` [VM]_.

Both binaries were run on 2026-10-04 as in the probes above (each release's
own ``-q`` file, ``database_name`` the one-protein BSA FASTA, the spectral
library emptied, ``num_threads = 4``, one line changed, ``comet -Pp.params
-Nout BSA3.mzML``), and the pepXML header's modification elements and the
``<spectrum_query>`` part of the output compared:

.. list-table::
   :header-rows: 1
   :widths: 24 38 38

   * - ``variable_mod02 =``
     - Comet 2026.02.2
     - Comet 2026.03.0
   * - ``42.010565 ^ 0 1 -1 0 0 0.0``
     - exit 0, no message. Header: ``<aminoacid_modification aminoacid="^"
       massdiff="42.010565" mass="1000041.010565" ...>`` -- an *amino acid*
       named ``^``. The ``<spectrum_query>`` text is **byte-identical** (by
       SHA-256) to the run with slot 2 unused: the modification never applied.
     - exit 0, no message. Header: ``<terminal_modification terminus="N"
       massdiff="42.010565" ... protein_terminus="Y"/>``.
   * - ``-0.984016 $ 0 1 -1 0 0 0.0``
     - exit 0, no message; ``aminoacid="$"``; results again byte-identical to
       the unused slot.
     - exit 0; ``<terminal_modification terminus="C" ...
       protein_terminus="Y"/>``; one search hit in the pepXML carries the
       C-terminal modification (the protein's C-terminal peptide).
   * - ``42.010565 n 0 1 -1 0 0 0.0``
     - ``protein_terminus="N"``; 12 search hits with the N-terminal
       modification.
     - the same: ``protein_terminus="N"``, 12 search hits.
   * - ``n^``, ``$c``
     - --
     - ``n^``: the results are byte-identical to ``n``'s; ``$c``: identical to
       ``c``'s. ``n`` with ``^`` is just ``n`` [V26A]_.
   * - ``^M``, ``^$``, ``^^``, ``K^c$``
     - --
     - all exit 0. ``^M``: an ``M`` modification and a protein N-terminal
       one; ``^$``: both protein termini; ``^^``: the same results as ``^``
       (characters are sorted and de-duplicated [V26E]_); ``K^c$``: ``K``,
       the protein N-terminus and *any* C-terminus.

So Comet 2026.02.2 **accepts** ``^`` and ``$`` and silently does nothing with
them. Its search manager knows ``n`` and ``c`` only [M1380]_; every other
character of the token is a residue to be found in the protein sequence by
``strchr`` [S6735]_, and the FASTA loader keeps only ``A``-``Z`` in a sequence
[S902]_, so ``^`` and ``$`` can never match. A file with ``^`` written for
2026.02.2 would run, report no error, and search without the modification the
user asked for. **Decision:** this project refuses ``^`` and ``$`` for
2026.02.2 (and 2024.01.0, whose source has no code for them either [V24A]_),
as it did before, and accepts them for 2026.03.0. That is data, not code: each
release's ``variableModTuple.residueAlphabet`` lists what it accepts
(:ref:`dev-comet-parameter-residue-alphabet`).

**Combinations.** Comet reads the token with ``%31s`` [V26T]_ and does not
check its characters; it sorts and de-duplicates them [V26E]_, takes ``n`` over
``^`` and ``c`` over ``$`` [V26A]_, and rewrites ``n`` (or ``c``) at distance 0
from the protein N- (or C-) terminus to ``^`` (``$``) [V26N]_. Every
combination above ran. So the codec admits any combination of a release's
characters, repeats included, exactly as it always admitted ``nn`` or
``nKc``; whether a redundant combination deserves a warning is validation's
question (unit 4), not the reader's.

Citations, at tag ``v2026.03.0`` (commit
``fa08489b5d5311c69df0e1d6d188bea22b80c184``, ``git ls-remote``) and
``v2026.02.2``:

.. [VM26] https://uwpr.github.io/Comet/parameters/parameters_202603/variable_modXX.html
   -- the 2026.03 page: ``^`` and ``$`` "added with release 2026.03.0"; its
   generator's "Valid: n, c, ^, $, A-Z" (fetched 2026-10-04).
.. [V26A] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1538-L1563
   -- the terminal codes: ``n``/``c`` any peptide terminus, ``^``/``$`` the
   protein's only; "'n' together with '^' is just 'n'". The 2026.03.0 record's
   alphabet ``source``.
.. [V26E] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1514-L1521
   -- the token's characters sorted and de-duplicated.
.. [V26N] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1417-L1471
   -- ``n``/``c`` at distance 0 from the protein terminus rewritten to
   ``^``/``$``.
.. [V24A] https://github.com/UWPR/Comet/blob/v2024.01.0/CometSearch/CometSearchManager.cpp#L1460-L1477
   -- 2024.01.0's terminal codes: ``n`` and ``c`` only. The 2024.01.0
   record's alphabet ``source``.
.. [S6735] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L6735
   -- 2026.02.2: a residue character matched against the sequence with
   ``strchr``.
.. [S902] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L902-L915
   -- 2026.02.2's FASTA loader keeps ``A``-``Z`` (upper-casing ``a``-``z``).
.. [V26Q] https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L934-L946
   -- ``index_search_type = -1``, written only for ``-q`` (``iPrintParams ==
   2``).
.. [V26T] https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L556-L625
   -- the tuple reader: eight fields, a comma pair in the count and in the
   neutral loss, as at ``v2026.02.2``.
.. [V26I] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L811-L823
   -- a value other than -1, 0 or 1 is warned about and becomes -1.
.. [V26W] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1742-L1786
   -- the three warnings for a 0 or 1 that has no effect.
.. [V26L] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L512-L516
   -- ``spectral_library_ms_level`` read under its written name.
.. [V26R] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSpecLib.cpp#L195-L215
   -- the ``.raw`` library loader: exits before it reads the level.
.. [V26U] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L914-L915
   -- ``add_U_selenocysteine`` applied to U.
.. [V22I] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L219-L228
   -- building an index on demand: ``== 0`` a peptide index, anything else a
   fragment ion index.
.. [V22J] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1524
   -- the search type of an index about to be built: ``== 0`` or not.
.. [V22L] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L404
   -- the reader registers ``speclib_ms_level``.
.. [V22M] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L421-L425
   -- the search manager asks for ``spectraL_library_ms_level``.
.. [V22U] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L812-L813
   -- the U static mass taken from ``add_U_user_amino_acid``.
.. [V22D] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPeptideIndex.cpp#L1362-L1364
   -- an index's ``DecoySearch:`` header line read back (written at line 887).
.. [V26D] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1949-L1951
   -- the same in 2026.03.0 (written at line 1357).
.. [V26X] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1436-L1450
   -- an active slot with a distance below -2, or a distance of 0 or more and a
   terminus outside 0-3, refused: ``invalid term_distance/which_term``.
.. [V26C] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1402-L1414
   -- each active slot's maximum count capped at the per-peptide limit, and
   at 5 when a fragment-ion index is being built.
.. [V26G] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1475-L1512
   -- a slot identical to a lower one merged into it and switched off.
.. [V26P] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1577-L1603
   -- AScorePro with an active ``variable_mod10``-``15`` refused, and why.
.. [V26S] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1706-L1708
   -- "an index" decided by the database name's last four characters, ``.idx``.
.. [V26F] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1764-L1775
   -- ``index_search_type`` 0 or 1 with a database that is not an ``.idx``: a
   warning.
.. [V26Z] https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L707-L729
   -- an enzyme number with no row: ``is missing definition in params file``,
   exit 1.
.. [V26O] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L748-L754
   -- ``output_txtfile``: ``== 0`` or not.
.. [V26M] https://github.com/UWPR/Comet/blob/v2026.03.0/Comet.cpp#L996-L1003
   -- the ``-q`` comment above the slots: ``^``, ``$``, the distance and the
   terminus codes.
.. [V22G] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1315-L1352
   -- 2026.02.2's merge of identical slots: the same comparison, no
   protein-terminus rewrite before it.
.. [V22F] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L720-L724
   -- 2026.02.2 reads ``index_search_type`` with no check and no warning.
.. [V24G] https://github.com/UWPR/Comet/blob/v2024.01.0/CometSearch/CometSearch.cpp#L4754-L4758
   -- 2024.01.0, as 2026.02.2: any negative distance is "no constraint".
.. [P920] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPostAnalysis.cpp#L913-L921
   -- the peptide handed to AScorePro: each modified residue followed by its
   slot number in decimal digits.
.. [M3336] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L3330-L3360
   -- AScorePro's modification symbols: slot *n* is the one character
   ``'0' + n``.

.. _dev-comet-parameter-structured:

Structured values
=================

Package ``org.cometgui.params.comet.value`` holds a typed, immutable value for
every parameter kind that is not a scalar, and a codec that reads it from --
and writes it to -- the value text of one declaration: the text between ``=``
and ``#`` that ``ParamsLine.Declaration.value()`` holds. The typed parser and
writer call these codecs per line, and write what ``format`` returns byte for
byte; the validators read the typed values' fields.

A codec rejects only text it **cannot read** -- the wrong number of fields, a
token that is not a number, a comma pair where the version takes one value --
with a ``ValueSyntaxException`` naming what was being read (a parameter such
as ``variable_mod03``, or an enzyme row and its line) and the field. Text that
reads cleanly but means something illegal -- a lower bound above an upper, a
residue Comet does not know, an enzyme number missing from the table -- is the
validation package's to report, not a parse error.

Every fact below about Comet is cited to its own documentation for the
2026.02 release or to its source at tag ``v2026.02.2`` (commit
``6edec914959a6fe2dfa898f8b395d775bb2d2751``; ``git ls-remote`` shows the tag
pointing at that commit, and every line cited was read there). Where the two
disagree, the source wins and the disagreement is written down.

.. _dev-comet-parameter-numbers:

Numbers
-------

One rule for every codec (``Numbers``):

* A decimal is read into a ``BigDecimal``, which keeps every digit **and the
  scale written**, and is written back with ``toPlainString()``. So a mass
  survives exactly as the user gave it: Comet's own default ``15.9949`` is
  written ``15.9949``, and the precise ``15.994915`` that Phase 00's
  :doc:`../feasibility/scientific-path` edited it to is written ``15.994915``
  -- never rounded back, never padded to ``15.994915000``. ``0.0`` stays
  ``0.0``. Only the notation is canonical: a leading ``+`` is dropped and an
  exponent is written out (``1.5e2`` becomes ``150``).
* A whole number is read with ``Integer.parseInt`` and written with
  ``Integer.toString``; one too large for Comet's ``int`` is refused.
* What is accepted is what C's ``%lf`` and ``%d`` -- which Comet's reader
  uses -- read as a plain number, except ``inf`` and ``nan`` (no parameter
  means them) and exponents of more than three digits.
* Nothing consults the default locale, so the text is the same under a
  comma-decimal locale (``R-PARAM-11``). ``LocaleIndependenceTest`` writes
  every form below under ``Locale.ROOT``, ``Locale.GERMANY`` and
  ``Locale.FRANCE`` -- after proving that the locale under test really does
  format ``1.5`` as ``1,5`` -- requires identical text, and restores the
  default locale after each test. A comma decimal such as ``15,9949`` is
  never read as a number.

.. _dev-comet-parameter-tuple:

The variable-modification tuple
-------------------------------

``variable_mod01 = 15.9949 M 0 3 -1 0 0 0.0`` is not a scalar. Its fields, as
Comet 2026.02.2 reads them:

.. list-table::
   :header-rows: 1
   :widths: 4 18 44 34

   * - #
     - ``VariableModField``
     - Meaning
     - Source
   * - 1
     - ``MASS``
     - Mass difference, a decimal. A slot whose mass is zero is ignored --
       which is how ``comet -q`` writes slots 2 to 15, ``0.0 X 0 3 -1 0 0 0.0``.
     - Page [VM]_; read ``%lf`` [C589]_; zero means unused [M1368]_.
   * - 2
     - ``RESIDUES``
     - One token of residues; ``n`` for the N-terminus, ``c`` for the
       C-terminus, combinable (``nK``). From 2026.03.0 also ``^`` and ``$``,
       the protein N- and C-terminus only (:ref:`dev-comet-parameter-202603-termini`).
     - Page [VM]_; read ``%31s`` [C589]_; ``n`` and ``c`` [M1380]_; ``^``
       and ``$`` [VM26]_, [V26A]_.
   * - 3
     - ``BINARY_GROUP``
     - ``0`` a variable modification; any other integer a binary group, whose
       residues are all modified or all unmodified together (since 2015.02
       rev. 1).
     - Page [VM]_; any non-zero value is binary [M1398]_.
   * - 4
     - ``COUNT``
     - Maximum count per peptide, ``3``; or, since 2020.01 rev. 3, a
       ``min,max`` pair, ``2,4``.
     - Page [VM]_; the comma rule [C605]_.
   * - 5
     - ``TERMINAL_DISTANCE``
     - ``-1`` no constraint; ``-2`` anywhere except the peptide's C-terminal
       residue; ``0`` only the terminal residue; ``N`` the terminal residue
       through the next ``N``.
     - Page [VM]_; ``-2`` [S6874]_.
   * - 6
     - ``TERMINUS``
     - Which terminus the distance counts from: ``0`` protein N, ``1``
       protein C, ``2`` peptide N, ``3`` peptide C.
     - Page [VM]_; [S5375]_.
   * - 7
     - ``REQUIRED``
     - ``0`` not required; ``1`` required; ``-1`` exclusive -- at most one of
       the exclusive set in a peptide (since 2024.01.0).
     - Page [VM]_; ``> 0`` [M1401]_; ``== -1`` [S5881]_.
   * - 8
     - ``NEUTRAL_LOSS``
     - Fragment neutral loss, ``0.0`` for none; since 2025.01.0 a pair of two
       losses ``a,b`` with no space.
     - Page [VM]_; the comma rule [C600]_; zero means none [M1404]_,
       [S1770]_.

Comet requires **exactly eight** white-space separated fields and exits
otherwise [C576]_, which is why a space inside a pair (``97.976896,
79.966331``) is a ninth field and refused, not a pair. It reads a tuple under
any fourteen-character name starting ``variable_mod`` [C552]_, but reads
**only** ``variable_mod01`` to ``variable_mod15`` into the search
[M518]_ (``VMODS`` is 15 [K80]_): the ``-q`` comment "Up to 15 variable_mod
entries are supported for a standard search; manually add additional entries
as needed" [C971]_ promises more than 2026.02.2 delivers, and a
``variable_mod16`` would be read and then ignored.

The layout is data
~~~~~~~~~~~~~~~~~~

``R-PARAM-09`` says field count and order come from the version schema, not
from a hard-coded format string. So the table above is not in code: it is the
``versions[].variableModTuple`` object of the metadata file -- for 2026.02.2,
``MASS``, ``RESIDUES``, ``BINARY_GROUP``, ``COUNT`` (pair), ``TERMINAL_DISTANCE``,
``TERMINUS``, ``REQUIRED``, ``NEUTRAL_LOSS`` (pair), with its ``source``
pointing at the reading code [C552]_ -- loaded into a ``VariableModLayout`` on
the ``CometVersionRecord``. ``VariableModField`` says only what each field
*is* (its kind, and whether any release pairs it); which fields a release has,
in which order, and which accept a pair, is the layout's.

``VariableModCodec.forVersion(metadata, version)`` takes that layout and the
version's ``VARIABLE_MOD_TUPLE`` parameters as its slots. Parsing splits on
white space, requires as many fields as the layout lists, and reads each by
what the layout puts at that position; a comma pair is read only where the
layout accepts one, and then exactly two non-empty values. Formatting walks
the same layout and joins the fields with one space. The codec holds no field
count of its own, and no residue alphabet either
(:ref:`dev-comet-parameter-residue-alphabet`). A field a layout does not hold is given Comet's own default
when read -- the ``VarMods`` constructor [D269]_: group ``0``, count ``0``,
distance ``-1``, terminus ``0``, required ``0``, loss ``0.0`` -- and a value
that differs from that default in such a field is **refused** when written
under the layout, never silently dropped. ``VariableModCodecTest`` proves
this with CONSTRUCTED layouts: a seven-field one with no neutral loss reads
``15.9949 M 0 3 -1 0 1`` and refuses the eight-field form, one without a pair
on the count refuses ``2,4``, and a reordered three-field one writes
``STY 79.966331 97.976896,79.966331``.

The typed value
~~~~~~~~~~~~~~~

``VariableModification`` holds every field: the mass and the losses as
``BigDecimal`` (one, or two for the pair form), the residue token as written,
the binary group, an optional minimum and the maximum count, the terminal
distance, and the terminus and requirement **codes** as integers. The codes
stay integers because the source accepts values the page does not document:
any positive requirement acts as required [M1401]_ and only ``-1`` as
exclusive [S5881]_, and a terminus outside ``0``-``3`` matches none of the
search's branches [S5375]_. ``terminus()`` and ``requirement()`` map the
documented codes to enums and return empty otherwise, so the validators can
warn rather than the parser guess. The type holds a residue token whose every
character it can describe -- a letter ``A`` to ``Z`` or one of the four
``TerminalCode``\ s, ``n``, ``c``, ``^`` and ``$`` -- and refuses anything else
(``"K#" holds '#'; a residue token is letters A-Z and the terminal codes n, c,
^ and $``). Which of those a *release* accepts is its alphabet, which the
codec applies (:ref:`dev-comet-parameter-residue-alphabet`). Comet's reader
takes any token up to 31 characters [C589]_, so the length limit is left to
validation. ``nTerminus()`` and ``cTerminus()`` say which terminus a token
targets -- ``n`` over ``^`` and ``c`` over ``$``, as Comet resolves them
[V26A]_ -- and ``residueLetters()`` drops all four codes.
``effectiveNeutralLosses()`` drops zeros, as Comet does.

``summary()`` writes the value in words for the editor, as the specification
asks::

    Oxidation: +15.994915 on M; max 3 per peptide; optional
    +79.966331 on STY; 2 to 4 per peptide; required; neutral losses 97.976896 and 79.966331
    +28.0 on C-terminus, within 9 residues of the protein C-terminus; max 3 per peptide; optional
    Acetyl: +42.010565 on protein N-terminus; max 1 per peptide; optional
    +42.010565 on M and protein N-terminus; max 1 per peptide; optional
    unused (mass difference 0.0)

An undocumented code is named, not guessed (``requirement code 2``). ``n^``
reads "on N-terminus" and ``$c`` "on C-terminus", because Comet treats each as
the peptide code alone.

.. _dev-comet-parameter-residue-alphabet:

The residue alphabet is data
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Which characters a residue token may hold differs by release -- ``^`` and
``$`` are 2026.03.0's -- so, by decision C-2 of the 2026.03.0 intake, it is in
the version record, not in the code: ``versions[].variableModTuple
.residueAlphabet``, loaded into the ``VariableModLayout`` as a
``ResidueAlphabet``::

    "residueAlphabet": {
      "characters": "ABCDEFGHIJKLMNOPQRSTUVWXYZnc^$",
      "source": "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1538-L1563"
    }

.. list-table::
   :header-rows: 1
   :widths: 14 40 46

   * - Release
     - ``characters``
     - ``source``
   * - 2026.03.0
     - ``A``-``Z``, ``n``, ``c``, ``^``, ``$``
     - [V26A]_
   * - 2026.02.2
     - ``A``-``Z``, ``n``, ``c``
     - [M1380]_
   * - 2024.01.0
     - ``A``-``Z``, ``n``, ``c``
     - [V24A]_

``TerminalCode`` (``schema``) is the vocabulary: what ``n``, ``c``, ``^`` and
``$`` *mean* (``N-terminus``, ``C-terminus``, ``protein N-terminus``,
``protein C-terminus``), which is the same in every release that has them; the
alphabet is the law, which is not. The loader refuses an alphabet character
that is in neither ``A``-``Z`` nor the vocabulary, so every accepted character
can be put into words.

``VariableModCodec`` holds a token to the alphabet **both ways**. Reading a
character the release does not accept is a ``ValueSyntaxException`` naming the
slot, the field and the release::

    variable_mod07, field 2 (residues): "^" holds '^', which Comet 2026.02.2 does
    not accept in a residue token; its residue alphabet is A-Z, n (N-terminus), c
    (C-terminus)

and writing one -- a value made for 2026.03.0 put into a 2026.02.2 model -- is
refused with the same words and ``, so it cannot be written``; the canonical
writer then writes nothing. ``forVersion`` names the release as ``Comet
<version>``; a codec built directly from a layout says ``this Comet version``.
The parser reports the read refusal as ``UNREADABLE_VALUE`` with the line
number, and fails the parse (``R-PARAM-08``). The loader also holds each
curated tuple default to the alphabet of every release it claims.

``ResidueAlphabetTest`` and ``ResidueAlphabetLoaderTest`` cover the type and the
loader; ``VariableModCodecAlphabetTest`` shows the codec follows the data, with
CONSTRUCTED alphabets (``KM^`` accepts ``^K`` and refuses ``X``) as well as the
bundled ones; ``VariableModificationTest`` the meanings and the words.

The round trip, gate item 3
~~~~~~~~~~~~~~~~~~~~~~~~~~~

``VariableModRoundTripTest`` runs **17 forms x 15 slots = 255** dynamic tests
for 2026.02.2 (``everyFormInEverySlot``) and **26 forms x 15 slots = 390** for
2026.03.0 (``everyFormInEverySlotOfComet202603``): the same 17 plus the nine
``TupleForms.PROTEIN_TERMINI`` forms with ``^`` or ``$``. Those nine are then
**refused** in every slot of 2026.02.2 and of 2024.01.0, reading and writing,
with the diagnostic above: **2 x 9 x 15 = 270** more. Which releases accept
``^`` and ``$`` is typed in the test, not read from the metadata, so a record
that gave them to the wrong release fails it; and the releases with a round
trip are held to the manifest's. The run prints ``round trips completed
{2026.02.2=255, 2026.03.0=390} ... ^/$ refusals completed {2024.01.0=135,
2026.02.2=135}``. The 2026.02.2 entry point keeps its name and count because
``scripts/verify-param-gates.sh`` grades it by them.

The forms (``TupleForms``, test sources) are CONSTRUCTED test input: the
page's own examples [VM]_ -- one loss, two losses, required, ``nK``, ``n`` at
the protein N-terminus, ``c`` within 8 of the protein C-terminus, the
pyroglutamate cyclisation at the peptide N-terminus, binary groups 1 and 2 --
plus forms built from the page's field descriptions where it gives no
whole-line example (``min,max``, exclusive ``-1``, distance ``-2``, the peptide
C-terminus, all of them at once), ``comet -q``'s two defaults, and the
``15.994915`` edit. The protein-terminus forms are the 2026.03 page's own
examples [VM26]_ (``42.010565 ^ 0 1 -1 0 0 0.0``, ``15.994915 ^ 0 3 -1 0 0
0.0``) and forms built from its description and from the combinations the
real binary was seen to accept (:ref:`dev-comet-parameter-202603-termini`):
``$``, ``^M``, ``n^``, ``$c``, ``^$``, ``^^`` and ``^STY$`` with every other
field set. For each slot the bundled metadata gives the release, a line
``variable_modNN = <form>`` goes through ``ParamsLineReader``; the
declaration's value is parsed, compared field by field with the form's
expected value, formatted back to the identical text, and re-parsed to the
same value. Separately, all fifteen slots of the real ``-q`` fixture, and
every curated tuple default, read and write back unchanged.

.. _dev-comet-parameter-enzymes:

The enzyme table
----------------

``[COMET_ENZYME_INFO]`` is the last section of the file: Comet stops reading
parameters at that line [C541]_ and reads every following line as a row.
A row is ``number. name sense cut no-cut`` [EZ]_: the number, which the three
``ENZYME_REFERENCE`` parameters refer to; a name with no spaces; the sense,
``0`` to cleave N-terminal to (before) the cut residues and ``1`` C-terminal
to (after) them; the cut residues; and the flanking no-cut residues, ``-``
meaning none. A row whose cut and no-cut residues are both ``-`` means no
enzyme -- non-specific cleavage [M1246]_. Comet reads the number with
``"%d."`` [C657]_ and the row with ``"%lf %47s %d %19s %19s"`` [C662]_, so a
name of more than 47 characters or residues of more than 19 misparse; those
limits, the documented "numbers start at 0 and increase by 1" [EZ]_ (not
enforced by the source), and residue letters are the validation package's.

``EnzymeTableCodec`` reads rows into ``EnzymeDefinition`` values (number,
name, ``Sense``, cut and no-cut residues with "none" held as the empty string
and Comet's ``@`` kept as written) in an ``EnzymeTable``, which supports
lookup by number, adding a custom enzyme and removing one. **A table that
defines a number twice is refused**, naming the line: Comet's own loop reads
every row and, for a referenced number, the *last* match silently wins
[C654]_, so such a file does not mean what it appears to say.

What is written is **exactly the columns ``comet -q`` writes** [C1177]_: the
number and its full stop left-aligned in 4 characters, the name in 23, the
sense in 7, the cut residues in 12, then the no-cut residues with no trailing
space; a field as wide as its column or wider is followed by one space. Comet
does not need the padding -- ``sscanf`` takes any white space -- but matching
its own columns means the default table is written back **byte for byte**,
which is what the double round trip of the real ``-q`` file (gate item 1)
needs, and a custom row still lines up with Comet's. ``EnzymeTableCodecTest``
reads the real fixture's twelve rows, checks their typed values, writes them
back identical to the fixture's lines, adds a CONSTRUCTED ``12. Glu_C 1 DE P``
that survives a round trip, and refuses a duplicate number. The header line
and the blank line Comet writes after the rows are the file writer's.

.. _dev-comet-parameter-other-kinds:

The other structured kinds
--------------------------

Signed tolerance pair (``TolerancePair``)
    ``peptide_mass_tolerance_lower`` and ``_upper`` as one value, each read as
    one decimal [C454]_. The lower bound is normally negative -- the mass
    error is experimental minus theoretical [TL]_ -- and its sign and scale
    are kept as written. ``R-PARAM-04``'s own rule (``lower <= 0 <= upper``,
    a warning otherwise) is the validation package's; the pair is never given
    the generic ordering rule.

Two values on one line (``IntegerRange``, ``DecimalRange``)
    ``scan_range``, ``precursor_charge``, ``peptide_length_range`` (``%d %d``)
    and ``digest_mass_range``, ``clear_mz_range`` (``%lf %lf``) [C486]_
    [C308]_. Exactly two values are required where Comet would ignore a
    third; they are written separated by one space. They are called first
    and second, not minimum and maximum, because they are not always that
    (``precursor_charge = 0 2`` searches every charge).

Mass-offset list (``DecimalList``)
    ``mass_offsets``, zero or more decimals; empty is a value, and Comet's
    default. Values keep their order and scale. Comet 2026.02.2 drops
    negative values and sorts the rest [C494]_, and -- an upstream defect
    found by reading, not by running -- its loop advances to the next token
    only when the current one reads as a number, so a non-numeric token never
    ends it [C494]_. This codec refuses such a token.

Ion-series family (``IonSeries``, ``IonSeriesSelection``)
    ``use_A_ions`` .. ``use_Z1_ions`` as a set of series, plus ``use_NL_ions``
    (water and ammonia losses of b and y, a plain boolean in the metadata,
    not a series) as a separate flag, all read as integers by Comet [C407]_.
    Each is ``0`` or ``1``, which is all the documentation allows [IA]_; the
    family is read as a whole and written back in ``-q`` order. A test holds
    the enum equal to the metadata's ``ION_SERIES_FLAG`` parameters.

.. _dev-comet-parameter-editing:

Editing a structured value without parsing in the editor
--------------------------------------------------------

Phase 07, unit 4. The parameter editor's view-models may not split a tuple,
read a number or write a comma (decision P7-1; the ArchUnit rule
``UiThroughTheModelRule`` keeps the codecs and ``Numbers`` out of
``org.cometgui.ui``), so every structured control hands the model *texts*,
one per control, and the model reads them. What the model offers for that:

``VariableModSlots`` (``value``)
    The variable-modification slots of one release:
    ``VariableModSlots.forRelease(metadata, version)``. ``slots()`` are the
    release's ``VARIABLE_MOD_TUPLE`` parameters; ``parts()`` are one
    ``VariableModPart`` per field of the release's **layout**, in its order --
    ``MASS``, ``RESIDUES``, ``BINARY_GROUP``, ``MINIMUM_COUNT``,
    ``MAXIMUM_COUNT``, ``TERMINAL_DISTANCE``, ``TERMINUS``, ``REQUIRED``,
    ``NEUTRAL_LOSS``, ``SECOND_NEUTRAL_LOSS`` for 2026.03.0 and 2026.02.2 --
    where a field that accepts a comma pair is two parts and the optional half
    (the minimum count, the second loss) exists only where the layout accepts
    the pair: 2024.01.0, whose loss takes one value, has no
    ``SECOND_NEUTRAL_LOSS``. ``partText(value, part)`` is a part's text (empty
    for an absent optional half); ``withPart(slot, value, part, text)`` reads
    one part's text as the layout's field reads it and returns the changed
    value, every other part kept, an empty text clearing an optional half. It
    refuses, with a ``ValueSyntaxException`` naming the slot and the part, text
    that is not one value, not a number of the field's kind, a residue token
    with a character outside the release's alphabet, and a part the layout
    does not have::

        variable_mod04, maximum count per peptide: "2.5" is not a whole number
        variable_mod03, residues: "^M" holds '^', which Comet 2026.02.2 does not accept in a
        residue token; its residue alphabet is A-Z, n (N-terminus), c (C-terminus)

    ``withResidue(slot, value, character, selected)`` is the residue
    multi-select: it adds or clears one character of the release's alphabet
    (``ResidueAlphabet.letters()`` and ``terminalCodes()`` are what an editor
    offers -- ``n``, ``c``, ``^`` and ``$`` for 2026.03.0, ``n`` and ``c`` for
    2026.02.2), refuses a character the alphabet lacks and clearing the last
    one, and writes the token in one fixed order -- ``n``, ``^``, letters, ``c``,
    ``$`` -- which carries no meaning, since Comet sorts and de-duplicates the
    characters [V26E]_. ``choices(TERMINUS)`` and ``choices(REQUIRED)`` give each
    documented code with its words and, for the requirement, the page's
    explanation (``VariableModification.Requirement.explanation()``: "Only
    peptides that contain the modification are analysed."); ``explanation(part)``
    explains every part from the ``variable_modXX`` page [VM]_.
    ``unused()`` is what a slot holds when the editor removes its modification:
    the curated default of the first slot whose default has no mass difference,
    ``0.0 X 0 3 -1 0 0 0.0`` in both offered releases -- what ``-q`` writes for
    slots 2 to 15 and what the page gives as the value of a missing slot. It is
    **not** ``resetToDefault``: ``variable_mod01``'s default is an active
    oxidation. ``unwritable(value)`` says why the release cannot hold a value
    (``VariableModCodec.unwritable``, the refusal ``format`` makes, without
    writing). Whether a value that reads is *legal* -- a minimum above the
    maximum, a terminus outside 0-3 -- stays validation's question: a part
    accepts any text its field can hold, as the parser does.

Ranges (``model``, ``value``)
    ``CometParameters.rangeTexts(name)`` gives a two-value range's first and
    second text, scale kept; ``rangeValue(name, first, second)`` reads the two
    texts as the parameter's kind (``IntegerRange.parse(name, first, second)``,
    ``DecimalRange.parse(...)``) and returns the value for ``withValue``. A
    reversed pair is read, not judged: order is ``ordered_range``'s.

Custom enzymes (``value``)
    ``EnzymeDefinition.fromTexts(number, name, sense, cut, noCut)`` reads a
    custom row's texts -- the number a whole number 0 or more, the name one
    word, residues one token each, empty or ``-`` for none -- naming the field
    it refuses (``custom enzyme, number: "-1" is negative; enzyme numbers start
    at 0``). ``EnzymeTable.nextNumber()`` is one more than the highest number.
    A duplicate number is still the table's refusal.

Static-modification targets (``schema``)
    ``StaticModTarget.of(definition)`` reads Comet's naming convention so the
    editor does not: ``add_C_cysteine`` is the residue ``C``, "cysteine (C)";
    ``add_Nterm_peptide`` the "peptide N-terminus". Every ``static_mods``
    parameter of both offered releases has one: four termini and 26 residues.

Tests, expectations typed by hand: ``VariableModSlotsTest`` (every part set
on both offered releases, the refusals, the multi-select, ``^`` and ``$`` on
2026.03.0 only, a CONSTRUCTED reordered layout with and without pairs, and
2024.01.0's missing second loss), ``EditorInputsTest``, ``EditorFactsTest``
and ``RangeTextsTest``.

.. _dev-comet-parameter-model:

The typed model
===============

Package ``org.cometgui.params.comet.model``. ``CometParameters`` is the
parameter set of one Comet version: what the parser builds, the canonical
writer writes, and validation, presets and the editor read (``R-PARAM-03``).
It holds:

* exactly **one entry per parameter** the metadata models for the version, in
  the metadata's order -- which is the order ``comet -q`` writes. An entry
  (``ParameterEntry``) is the curated ``ParameterDefinition``, a typed
  ``ParameterValue`` and a ``ValueOrigin``;
* the enzyme table (unit 3's ``EnzymeTable``);
* the **unknown parameters** an imported file carried (``UnknownParameter``:
  name, value text as imported, inline comment, the comment lines above it,
  and its line), in file order;
* the **diagnostics** of the parse that produced it -- warnings only, since a
  parse with an error produces no model.

The imported comments of modelled parameters are not on the model -- two
models with the same values are equal whatever comments their files carried
-- but on the parse result (:ref:`dev-comet-parameter-parser`).

It is immutable. ``withValue(name, value, origin)``, ``withText(name, text,
origin)`` (text read as the parameter's kind), ``withOrigin(name, origin)``,
``resetToDefault(name)``, ``withEnzymeTable(table)`` and
``withoutUnknown(name)`` each return a new model and leave the old one as it
was. Lookup is by name: ``entry``, ``value``, ``origin``, ``definition`` and
``text`` (the value as written). ``tolerancePair()`` and ``ionSeries()`` give
unit 3's structured views over the two tolerance members and the eight
ion-series flags, which the model holds as one entry each.
``withWorkflowEnforcedOutputs()`` and ``withDecoySource(source, origin)`` are
validation's two model operations (:ref:`dev-comet-parameter-validation`).
``CometParameters.defaults(metadata, version, table)`` is every parameter at
its curated default; the metadata does not curate enzyme rows, so the table is
given. ``CometParameters.of(...)`` refuses a model with a parameter missing or
given twice, an entry the version does not model, a definition that is not the
metadata's, an unknown parameter that is modelled or named twice, or an error
diagnostic.

Values
------

One ``ParameterValue`` variant per family of kinds, fixed by the kind:

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Variant
     - Kinds
   * - ``Whole``
     - ``INTEGER``, ``INTEGER_ENUM``, ``ENZYME_REFERENCE``
   * - ``Decimal`` (``BigDecimal``, scale kept)
     - ``DECIMAL``, ``TOLERANCE_PAIR_MEMBER``
   * - ``Flag``
     - ``BOOLEAN_FLAG``, ``ION_SERIES_FLAG`` -- ``0`` or ``1``, nothing else
   * - ``Text`` (possibly empty)
     - ``STRING``, ``STRING_ENUM``, ``FILE_PATH``
   * - ``WholeRange``, ``DecimalPair``, ``Decimals``, ``Tuple``
     - ``INTEGER_RANGE``, ``DECIMAL_RANGE``, ``DECIMAL_LIST``,
       ``VARIABLE_MOD_TUPLE`` -- unit 3's types
       (:ref:`dev-comet-parameter-structured`)

``ParameterValueCodec`` is the one reader and writer of a value's text for a
version; the parser and the writer both call it. Its numbers go through unit
3's ``Numbers`` (made public for this, unchanged otherwise), so the whole file
has one number reader and one number writer. A ``Text`` may not hold ``#``
(Comet ends a value there), a line break, or white space at either end (the
reader trims), because it would not read back as written.

Origins
-------

``ValueOrigin`` is the specification's five: ``COMET_DEFAULT`` (the curated
default, what ``-q`` writes), ``PRESET``, ``USER``, ``IMPORTED`` and
``WORKFLOW_ENFORCED`` (``R-CMT-01``: the outputs the workflow forces on). The
origin is carried, compared by model equality, and never written: two models
that differ only in origins write the same bytes.

.. _dev-comet-parameter-parser:

The parser
==========

``org.cometgui.params.comet.parser.CometParamsParser`` is built for one
selected Comet version and turns a whole text into a ``ParseResult``: a model
and its warnings, or **no model** and every finding. It reads lines through
``ParamsLineReader`` and values through ``ParameterValueCodec``; nothing else
knows how a line or a value is shaped.

Parsing is **all or nothing**, which is what makes ``R-PARAM-08``'s "a failed
parse leaves the typed model untouched" true by construction: a caller that
applies a result only when it holds a model cannot half-apply one.
``ParseResult`` itself refuses a model alongside an error. Every finding is a
``Diagnostic`` with a severity fixed by its code, the lines it concerns, the
parameter where there is one, and a message naming both. Every finding is
reported, not only the first, ordered by line.

.. list-table::
   :header-rows: 1
   :widths: 30 12 58

   * - Code
     - Severity
     - When
   * - ``MALFORMED_LINE``
     - error
     - A line ``ParamsLineReader`` classifies as malformed; the message quotes
       it and gives the reader's reason.
   * - ``DUPLICATE_PARAMETER``
     - error
     - A name declared twice, known or unknown; both lines are named.
   * - ``DUPLICATE_VERSION_MARKER``
     - error
     - A second ``# comet_version`` line; both lines are named.
   * - ``UNREADABLE_VALUE``
     - error
     - A modelled parameter's value cannot be read as its kind (``num_threads
       = many``, a flag of ``2``, a seven-field tuple), or an unknown
       parameter could not be written back as it was read.
   * - ``ENZYME_TABLE_MISSING``
     - error
     - No ``[COMET_ENZYME_INFO]`` line.
   * - ``UNREADABLE_ENZYME_ROW``, ``DUPLICATE_ENZYME_NUMBER``
     - error
     - A row unit 3's codec cannot read; a number defined twice, both lines
       named (Comet would silently use the later row [C654]_).
   * - ``VERSION_MISMATCH``
     - warning
     - The marker names another Comet version than the selected one, or text
       that is not a version; the message names both. The same version from
       another build (another hash) is not a mismatch.
   * - ``VERSION_MARKER_MISSING``
     - warning
     - No marker; the message says the file's version is unknown and names the
       selected one.
   * - ``UNKNOWN_PARAMETER``, ``NOT_IN_VERSION``
     - warning
     - A name the metadata does not model at all, or models only for other
       versions. Kept as an ``UnknownParameter`` and written back
       (``R-PARAM-07``).

A version mismatch is a warning, not an error, because the file can be
represented: its parameters are read as the selected version's, and it will
be written for the selected version. ``R-PARAM-06`` asks that the user see it,
not that the import be refused.

Values, defaults and comments
-----------------------------

A modelled parameter the file declares takes its value from the file, origin
``IMPORTED``; one it does not declare takes the curated default, origin
``COMET_DEFAULT``. An **empty value is a value**: ``peff_obo =`` imports the
empty text, and ``decoy_prefix =`` imports the empty text rather than the
default ``DECOY_`` -- whether that is sensible is validation's question.
Parsing the real ``-p`` fixture gives 96 ``IMPORTED`` entries and exactly the
22 ``-q``-only parameters at ``COMET_DEFAULT``, every value equal to the
``-q`` model's.

The comment structure (``R-PARAM-05``)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The parser **preserves the whole comment structure of the imported file**, on
``ParseResult.comments()`` -- an immutable ``ImportedComments``, present
whether or not the parse succeeded:

* ``marker()``: the imported ``# comet_version`` line, verbatim;
* ``of(name)`` / ``parameters()``, for every modelled parameter the file
  declared, in file order: ``above()``, every comment and blank line (blank as
  an empty string) between the previous declaration and this one;
  ``inline()``, the trailing comment on its line; and ``continuation()``,
  indented comment lines directly under it -- Comet's own ``-q`` output
  continues ``sample_enzyme_number``'s comment on such a line;
* ``beforeEnzymeTable()`` and ``inEnzymeTable()``: the comment and blank lines
  between the last declaration and ``[COMET_ENZYME_INFO]``, and after it.

Lines are kept as written, minus a CRLF file's ``\r``. For the real ``-q``
fixture, ``of("search_enzyme_number").above()`` is ``["", "#", "# search
enzyme", "#"]``, and every one of the file's comment and blank lines lands in
exactly one of these places (``ImportedCommentsTest``). Unknown parameters
carry their own comments on ``UnknownParameter`` -- the whole-line comments
directly above, up to the nearest blank line, declaration or marker, and the
inline comment -- because the writer writes those back.

What the **canonical writer** emits is a different, smaller thing, as
``R-PARAM-05`` asks: the marker *regenerated* for the selected version, the
*curated* inline comments from the schema, and the imported comments of
unknown parameters only (:ref:`dev-comet-parameter-canonical`). The imported
comments of modelled parameters are kept for the editor and for diffs, not
re-emitted, so the canonical text depends on the model alone. A CRLF file
parses to the same model as its LF twin.

The duplicate policy
--------------------

**A parameter declared twice is an error, naming both lines.** Comet
2026.02.2 does not refuse it: its main loop reads every declaration in file
order and hands each to ``SetParam`` [C536]_, which erases an existing entry
of that name and inserts the new one [M1651]_; the enzyme numbers are copied
into locals on each declaration [C400]_. So **the last declaration silently
wins**, and a file with a duplicate does not mean what its first declaration
says. Taking the last would match Comet but drop the first value; taking the
first would misrepresent what Comet does. Refusing the file, with both line
numbers, drops nothing and lets the user say which one they meant. This is
the same choice unit 3 made for an enzyme number defined twice.

.. _dev-comet-parameter-highlighting:

Line classes for the Expert editor
----------------------------------

Phase 07, unit 5. Expert mode colours the raw text it shows, and the editor
may not read a ``comet.params`` line itself (decision P7-1; the architecture
rule ``UiThroughTheModelRule`` keeps ``org.cometgui.ui`` away from
``ParamsLineReader``). ``org.cometgui.params.comet.parser.ParamsHighlighting``
is the reader's classification made available to it:

* ``of(text)`` gives one ``Line`` per line of the text, in order, none
  dropped: its number, its text as read (without the ``\n``), its kind -- one
  per ``ParamsLine`` variant: ``VERSION_MARKER``, ``COMMENT``, ``BLANK``,
  ``DECLARATION``, ``ENZYME_HEADER``, ``ENZYME_ROW``, ``MALFORMED`` -- the
  spans of its parts, and for a malformed line the reader's own reason. A
  declaration has a ``NAME`` span, a ``VALUE`` span unless the value is empty,
  and an ``INLINE_COMMENT`` span from its ``#`` to the end of the line; every
  other kind but a blank line is one span over the whole line. Spans are
  offsets into the line and never cover a CRLF file's ``\r``.
* ``declaredRelease(text)`` is the release the text's first ``#
  comet_version`` line names, read by ``CometVersionMarker``; empty when there
  is no marker or it names no readable version. An import consults it to
  decide whether to offer a migration (the parser itself never migrates).

Nothing classifies a line a second way: the kind is the reader's variant, and
a declaration's spans are where the reader's own name, value and comment texts
lie in the line. ``ParamsHighlightingTest`` types every span by hand over
CONSTRUCTED lines, one of each kind (an indented declaration with a CRLF
ending among them), and classifies each offered release's bundled ``-q`` file:
118 declarations, twelve enzyme rows, nothing malformed.

.. _dev-comet-parameter-release-defaults:

Each release's starting set
===========================

Phase 07, unit 3. A new configuration in the editor starts from the selected
release's defaults, and ``CometParameters.defaults(metadata, version, table)``
cannot give them alone: the metadata curates every parameter's default but
**no enzyme rows** -- the ``[COMET_ENZYME_INFO]`` table is a file's content,
not the schema's. So the module bundles, for each release the editor offers,
that release's own ``comet -q`` output, and
``org.cometgui.params.comet.parser.ReleaseDefaults`` parses it::

    cometgui-params-comet/src/main/resources/org/cometgui/params/comet/parser/defaults/
        2026.03.0/comet-q.params
        2026.02.2/comet-q.params

Each file is a byte-for-byte copy of the fixture of the same release
(:ref:`dev-comet-parameter-fixtures`) -- Comet's own Apache-2.0 output, the
same bytes the real-binary test re-proves -- and Git keeps it unconverted
(a path rule in the root ``.gitattributes``; a ``.gitattributes`` beside the
files would be copied into the jar, since the directory is a resource root).

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Call
     - What it gives
   * - ``bundledReleases()``
     - ``2026.03.0`` and ``2026.02.2``, newest first.
   * - ``isBundled(release)``
     - Whether a release has a starting set.
   * - ``bundledFile(release)``
     - The file's bytes, exactly as Comet wrote them.
   * - ``load(metadata, release)``
     - The file parsed for that release by ``CometParamsParser``, with every
       origin ``COMET_DEFAULT`` (the parser marks a declared value
       ``IMPORTED``; nothing was imported here): 118 entries, Comet's own
       twelve enzyme rows, no unknown parameter and no diagnostic.

What it refuses: a release with no bundled file, with an
``IllegalArgumentException`` naming it and the releases that have one
("Comet 2024.01.0 has no bundled starting set; the releases with one are
[2026.03.0, 2026.02.2]"); and a bundled file that does not parse **cleanly**
as its own release -- any diagnostic at all, which includes a marker naming
another release and a parameter the metadata does not model -- with an
``IllegalStateException`` naming the release and every diagnostic. A
release's starting set cannot carry a warning.

**Comet 2024.01.0 has no starting set**, deliberately: it is the migration
fixture's release and is never offered to a user, so a starting set for it
would be dead weight. Adding a release to the editor means adding its file
here (copied from its fixture, never typed) and its row to
``ReleaseDefaultsTest``.

``ReleaseDefaultsTest`` proves, for both releases: the bundled bytes' size
and SHA-256, typed in from the fixture's ``SHA256SUMS`` (11 844 B
``d1504870...``, 12 551 B ``4bbf39f9...``), and equality with the fixture's
bytes; a clean parse; every value and value text equal to the fixture's
model, every origin ``COMET_DEFAULT``, the same enzyme table; and that the
set equals ``CometParameters.defaults`` over that enzyme table, so the
curated defaults and Comet's own file agree. The two releases' own values are
asserted by hand (``index_search_type`` ``-1`` for 2026.03.0, ``1`` for
2026.02.2). The refusals are asserted with their messages, the second on
CONSTRUCTED metadata that claims ``scan_range`` from 2026.03.0 only.

.. _dev-comet-parameter-canonical:

The canonical form
==================

``org.cometgui.params.comet.writer.CanonicalParamsWriter`` writes a model as
canonical text (*Canonical serialisation*, ``R-PARAM-11``): the same bytes for
the same model and build, under any default locale. Its constructor takes the
running build's ``BuildIdentity``. An annotated excerpt of the real ``-q``
fixture's model written by a build whose version is ``0.1.0-SNAPSHOT``::

    # comet_version 2026.02 rev. 2 (6edec91)                     (1)
    # Written by CometGUI 0.1.0-SNAPSHOT for Comet 2026.02.2. Canonical form, generated from the typed model.
    # Everything following the '#' symbol is treated as a comment.
    #
    database_name = /some/path/db.fasta                          (2)
    decoy_search = 0                       # 0=no (default), ...  (3)
    ...
    peff_obo =                             # path to PSI Mod or Unimod OBO file
    ...
    variable_mod02 = 0.0 X 0 3 -1 0 0 0.0
    ...
    add_Z_user_amino_acid = 0.0000         # added to Z - ...
                                                                 (4)
    #
    # COMET_ENZYME_INFO _must_ be at the end of this parameters file
    #
    [COMET_ENZYME_INFO]                                          (5)
    0.  Cut_everywhere         0      -           -
    1.  Trypsin                1      KR          P
    ...
    11. No_cut                 1      @           @

(The numbers in brackets are this page's annotations, not part of the file.)

1. **The version marker, regenerated** for the model's version from the
   metadata's record of it (``R-PARAM-05``), **always on line 1**. Comet
   2026.02.2 looks for a line starting ``# comet_version`` only in the first
   seven lines and exits without one ("The comet.params file is from version
   unknown") [C244]_, accepting any version text containing ``2026.0``,
   ``2025.0`` or ``2024.0`` [M1892]_. The **generated header** -- the CometGUI
   version from ``BuildIdentity`` and the target Comet version -- therefore
   comes *after* the marker. Header lines are comments; Comet's main loop cuts
   every line at ``#`` and ignores what has no ``=`` before it [C536]_. Seven
   comment lines before the marker were observed to make the real binary
   refuse the file; six were not.
2. **Every modelled parameter, in the metadata's (``-q``'s) order**, as ``name
   = value``. Each value is written by ``ParameterValueCodec``: decimals with
   ``BigDecimal.toPlainString()`` and the scale they were read with, whole
   numbers with ``Integer.toString``, flags as ``0``/``1`` -- no
   locale-sensitive formatter anywhere. An empty value is ``name =``.
3. **The curated inline comment** from the metadata, its ``#`` at column 40
   as in Comet's own output, or one space after a longer declaration. A
   parameter without one gets none; no line has trailing white space.
4. **Unknown parameters**, only if there are any, in a section after the
   modelled ones -- they must come before the table, where Comet stops reading
   parameters [C541]_. The section opens with a blank line, the line ``#
   Parameters CometGUI does not model for this Comet version, kept as imported
   (Comet 2026.02.2)`` and another blank line; then each unknown parameter's
   imported comment lines, verbatim, and ``name = value`` as imported with its
   inline comment. The blank line keeps the section's own comment from being
   read back as the first unknown parameter's.
5. **The enzyme table, last**, after a blank line and three comment lines,
   with unit 3's column layout. Every line ends ``\n``; the file ends with the
   last row's ``\n``.

A parameter that Comet ignores (``spectral_library_ms_level``) is still
written, because the model holds it and the metadata models it.

Byte stability
--------------

Gate item 1 is ``CanonicalWriterTest``: parse the real ``-q`` fixture and write
it -- text A, 10 656 bytes -- then parse A and write again, and again; all
three are byte-identical, and the test reports the first differing offset if
not. Parsing A gives a model equal to the fixture's with no finding. Every
declaration's value text in A equals the fixture's, character for character,
and the enzyme rows are the fixture's own lines. Of the 118 declaration
lines, the only ones that differ from Comet's are the four with curated
comments and ``minimum_intensity``, whose comment Comet itself writes one
column early; Comet's section comments are not reproduced, the header is
added.

**Every installed release.** ``ReleaseWriterGateTest`` runs gate items 1, 4
(the writer half) and 6 for each release with fixtures -- 2026.02.2 and
2026.03.0 -- from one table of per-release facts rather than a copy of the
test per release; a fixture release without a row fails it. For each: parse
the release's real ``-q`` fixture and write, then twice more, all three
byte-identical; the length and SHA-256 are the release's own, typed in --

.. list-table::
   :header-rows: 1
   :widths: 14 12 74

   * - Release
     - Bytes
     - SHA-256 of the canonical ``-q`` text
   * - 2026.02.2
     - 10 656
     - ``f381afe1d48749d49a0bb5e97a0375be7e691f3c6bfa2f740e96589b4502d62b``
   * - 2026.03.0
     - 10 725
     - ``3aecc834201d0e0cfb5a010b8e60391a3c599da70e42aa1b16abe720bd7b7444``

-- the re-parse has no finding and equals the first model, line 1 is the
release's marker, and all 118 declaration values equal the fixture's. (Of
2026.03.0's 118 declaration lines, the only ones that differ from Comet's own
``-q`` file are the three with curated comments -- ``spectral_library_ms_level``,
``isotope_error``, ``output_txtfile`` -- and ``minimum_intensity``, whose
comment Comet writes one column early; Comet's section comments are not
reproduced and the header is added, as for 2026.02.2.) Then the
release's twelve enzyme rows are written as Comet's own lines, each of the
three enzyme references set to ``42`` is refused with the message above, and
the CONSTRUCTED custom ``12. Glu_C`` survives a file round trip; then two
CONSTRUCTED unknown parameters survive two round trips, in a section naming
the release, and are reported as ``UNKNOWN_PARAMETER`` by both parses.
Finally the ``^``/``$`` slots: for 2026.03.0, a model with ``^``, ``$`` and
``^M`` in three slots writes them, reads them back and writes the same bytes
again; for 2026.02.2, the same text is refused by ``withText`` and by the
parser, and a ``^`` value made by the 2026.03.0 codec and put into a
2026.02.2 model is refused by the writer, each naming 2026.02.2.

Gate item 5 is ``CommaLocaleWriterTest``: under ``de-DE`` and ``fr-FR`` --
after showing that each really formats ``1.5`` as ``1,5`` with both
``String.format`` and ``NumberFormat`` -- the real fixture's model and a
CONSTRUCTED variant full of decimals parse and write to the same bytes as
under ``Locale.ROOT``, for 2026.02.2 and for 2026.03.0 (whose variant also
carries a ``$`` slot). The default locale is restored after every test.

Refusal: enzyme numbers absent from the table
---------------------------------------------

The writer **never emits an enzyme number absent from the table it writes**
(*Enzyme definitions*). For every parameter the metadata's
``enzymeTable.referencedBy`` names -- ``search_enzyme_number``,
``search_enzyme2_number``, ``sample_enzyme_number`` -- whose number is not a
row of the model's table, ``write`` throws ``ParamsWriteException`` naming the
parameter and the number, and nothing is written::

    search_enzyme_number = 42 names enzyme 42, which is not in the
    [COMET_ENZYME_INFO] table being written (its numbers are [0, 1, ..., 11]);
    the file is not written

Comet 2026.02.2 does **not** catch this, although it appears to. It keeps
each referenced number in a local [C400]_ and, after the table, exits with
"is missing definition" when the enzyme's name is ``"-"`` [C691]_. But the
names are those of an ``EnzymeInfo``, whose constructor sets the search and
sample names to ``""`` and the second enzyme's to ``"Cut_everywhere"``
[D345]_ -- never ``"-"`` -- so those checks cannot fire. Run against the real
binary, a ``-q`` file edited to ``search_enzyme_number = 42`` goes straight
past the parameter reader. The refusal is this project's only guard.

A custom enzyme survives a full file round trip: the real model with a
CONSTRUCTED row ``12. Glu_C 1 DE P`` added and ``search_enzyme_number = 12``
writes the row last in Comet's columns, parses back to the same row and
number, and writes the same bytes again.

.. _dev-comet-parameter-write-once:

Write once, hash what was written
=================================

``R-PARAM-12``: the exact file written to disk is the file recorded in
provenance and passed to Comet. ``CanonicalParamsWriter.writeOnce(model,
target, hashService)``:

#. produces the bytes first, so a refused model touches nothing;
#. writes them to ``target`` with ``CREATE_NEW`` -- a file that already exists
   is never overwritten (``FileAlreadyExistsException``, the file untouched);
#. hands ``target`` to the domain ``HashService`` port, which reads the
   **file on disk**;
#. returns ``WrittenParams``: the path, its ``FileHashes`` (MD5 and SHA-256)
   and its size on disk -- and no text, so nothing downstream can write the
   file again; it passes the path.

``WriteOnceTest`` writes the real fixture's model with the real
``StreamingHashService`` from ``cometgui-provenance`` and checks both digests
against ``MessageDigest`` over the bytes read back; a recording hash service
shows it is called once, after the whole file is on disk.

.. _dev-comet-parameter-validation:

Validation
==========

Package ``org.cometgui.params.comet.validation``. ``CometValidator.standard()
.validate(model)`` returns a ``ValidationReport``: every ``Finding``, each from
one ``Rule`` with a **stable identifier** and a **severity** -- fixed, or, for
a version-scoped rule, the one the model's release states
(:ref:`dev-comet-parameter-version-scoped-rules`) -- attached to
the responsible parameters (the first is the one to show it at) and their
category -- *Errors shall be attached to the responsible field and category*.
``hasErrors()`` is what Phase 08 blocks a run on; ``forParameter(name)`` and
``forCategory(category)`` are what Phase 07 shows at a control and a category
heading (``AC-PAR-10``). The report is in a stable order: per parameter in the
model's order, then the cross-field rules, then what the import left -- and,
for a migrated set validated through its review, last, one
``migration.needs_attention`` error per unresolved entry, in the migration
report's order (:ref:`dev-comet-parameter-migration-review`).

What validation checks is the **model**. It reads no file: whether the
database and spectra exist and are readable, output paths are writable, the
FASTA holds decoys (``R-DEC-02``) or the PIN holds targets and decoys
(``R-DEC-04``) needs the file system or data and is the workflow's check before
a run, built in Phase 08 (:ref:`dev-workflow-engine`). Paths are checked for
**form** only.

Every validator id is implemented
---------------------------------

Every ``ValidatorId`` has a rule (``FieldRule``), registered in
``CometValidator.standard()``; a validator built without one is **refused**
with an ``IllegalStateException`` naming the id, so an id the metadata names
can never go unchecked. For each parameter the validator applies its curated
``min``/``max`` (to every number the value holds: both numbers of a range,
every number of a list), then each rule its metadata ``validators`` names,
then the cross-field rules. ``ValidatorCoverageTest`` proves the standard
validator implements every id, that the bundled metadata names ``choice`` 18
times, ``variable_mod_tuple`` 15, ``path`` 5, ``ordered_range`` 5,
``enzyme_in_table`` 3, ``signed_tolerance_pair`` 2 and ``workflow_enforced``
2, and -- the part that matters -- that for **every (parameter, validator)
pair** the metadata declares, a CONSTRUCTED value that breaks it produces an
error of that validator's rule at that parameter. ``ChoiceAndBoundsTest`` does
the same for every enumerated parameter and every parameter with a bound,
generating its cases from the metadata.

Two metadata corrections came with the rules. ``scan_range`` now names
``ordered_range`` (it was the one two-value range without it).
``spectral_library_name`` is now ``EMPTY_ALLOWED``: Comet 2026.02.2 runs no
spectral-library search when the name is empty or names a file it cannot read
[M1961]_, so empty is a legitimate value, not a missing one; under the old
``SINGLE_VALUE`` the path rule would have made the normal "no library" case an
error. The drift test is unaffected (neither changes a name or a default).

.. _dev-comet-parameter-pair-rule:

The signed precursor tolerance pair (gate item 7)
-------------------------------------------------

``R-PARAM-04`` gives ``peptide_mass_tolerance_lower`` and ``_upper`` their own
rule, ``lower <= 0 <= upper``, and the generic ordering rule is **never**
applied to them (the loader refuses a pair member that names ``ordered_range``,
and the pair rule is its own class). The two rules disagree on most windows,
which is what lets a test tell them apart: the generic rule would pass
``-10 / 20``, ``5 / 20`` and ``-20 / -5`` silently and call ``20 / -20`` its
own ``ordered_range.reversed``.

.. list-table::
   :header-rows: 1
   :widths: 22 36 42

   * - Window
     - Verdict
     - Why
   * - ``-20.0 / 20.0``
     - clean
     - Comet's default; contains 0, symmetric.
   * - ``-10 / 20``, ``0 / 20``
     - warning ``signed_tolerance_pair.asymmetric``
     - Contains 0 but not symmetric about it: legal, and possibly deliberate.
   * - ``5 / 20``, ``-20 / -5``
     - warning ``signed_tolerance_pair.same_signed``
     - The window does not contain 0, so the exact theoretical mass is not
       searched: legal, and possibly deliberate.
   * - ``20 / -20``
     - error ``signed_tolerance_pair.reversed``
     - No mass can match; Comet negates both bounds [M572]_ and stops with
       "mass_tolerance_lower is greater than mass_tolerance_upper" [M1584]_.

Each finding names both parameters, the lower first, in the
``precursor_mass`` category, and the pair is reported once although both
members name the rule. **Units** (``peptide_mass_units``: amu, mmu, ppm) do
not enter the rule: each is a positive multiple of a mass difference, so the
signs of the bounds and their order are the same in every unit; a test
repeats the verdicts under all three. Out-of-range unit codes are the
``choice`` rule's (Comet would silently use amu [M584]_).

.. _dev-comet-parameter-version-scoped-rules:

Version-scoped rules
--------------------

Comet 2026.03.0 refuses or warns about configurations 2026.02.2 accepted in
silence, so some verdicts differ by release. By decision C-2 no rule asks which
version it judges. Two kinds of fact are data instead:

* **A severity.** A version-scoped ``Rule`` has no severity of its own
  (``Rule.fixedSeverity()`` is empty, ``isVersionScoped()`` true); each
  release's version record states it (:ref:`dev-comet-parameter-rule-severities`),
  and the finding carries it (``Finding.severity()``). ``Findings`` reads the
  record of the model's version once per validation (``VersionSeverities``)
  and records nothing for a rule the release states ``OFF``.
* **A release fact a rule reads.** The residue alphabet
  (:ref:`dev-comet-parameter-residue-alphabet`) is the law of
  ``variable_mod_tuple.residue_not_in_release``, and the merge model of
  ``variable_mods.ascorepro_slot_unsupported`` applies Comet 2026.03.0's
  protein-terminus rewrite only in a release whose alphabet has the
  protein-terminus code. Neither rule's severity differs, so neither is
  version-scoped.

.. list-table::
   :header-rows: 1
   :widths: 34 22 22 22

   * - Version-scoped rule
     - Comet 2026.03.0
     - Comet 2026.02.2
     - Comet 2024.01.0
   * - ``variable_mod_tuple.distance_undocumented``
     - **error**: the binary refuses it [V26X]_
     - warning: read as -1, silently [S5371]_
     - warning, as 2026.02.2 [V24G]_
   * - ``index_search_type.ignored_without_idx``
     - warning: the binary warns [V26F]_
     - off: the binary is silent [V22F]_
     - off: no such parameter

The rule catalogue
------------------

Severity is the rule's: **E** an error (blocks a run), **W** a warning, **V**
version-scoped (the release states it; the table above). Source names the
Comet fact a rule encodes, at tag ``v2026.02.2`` unless it names another; a
rule with none encodes this project's choice or the specification's.

.. list-table::
   :header-rows: 1
   :widths: 27 4 19 35 15

   * - Rule id
     - Sev.
     - Parameters
     - What it checks
     - Source
   * - ``choice.not_listed``
     - E
     - each ``choice`` parameter
     - The value is one of the curated choices. Comet replaces many
       out-of-range codes with a default without a word.
     - [M584]_, [M1125]_
   * - ``bounds.below_minimum``, ``bounds.above_maximum``
     - E
     - every parameter with a curated ``min``/``max``
     - Every number of the value is within the bounds; the message says which
       number (first, second, value *n*).
     - the metadata
   * - ``path.empty``
     - E
     - ``path`` parameters not ``EMPTY_ALLOWED`` (``database_name``)
     - Present.
     - [M1961]_
   * - ``path.nul_character``
     - E
     - every ``path`` parameter
     - No NUL, which ends a C string.
     - --
   * - ``path.too_long``
     - E
     - ``path`` parameters Comet reads whole
     - At most 4095 bytes: Comet copies the value into ``char
       szFile[SIZE_FILE]``, ``SIZE_FILE`` 4096.
     - [C342]_, [D20]_
   * - ``text.not_one_token``, ``text.too_long``
     - E
     - ``decoy_prefix``, ``pinfile_protein_delimiter``,
       ``protein_modslist_file``
     - Comet reads these with ``sscanf("%255s")``: only the first word, at
       most 255 bytes. The ``decoy_prefix`` page: "without spaces".
     - [C301]_, [C347]_, [DP]_
   * - ``ordered_range.reversed``
     - E
     - ``peptide_length_range``, ``digest_mass_range``, ``clear_mz_range``,
       ``scan_range``, ``precursor_charge``
     - First <= second. Comet silently ignores a reversed range and keeps its
       default. ``scan_range``: a second value of 0 means "to the last scan"
       and is exempt (``500 0`` is legal); Comet refuses an end below the
       start only when the end is not 0.
     - [M606]_, [M1009]_, [M1054]_, [M1093]_, [C768]_, [P383]_, [M304]_
   * - ``ordered_range.second_ignored``
     - W
     - ``precursor_charge``
     - A first value of 0 switches the range off ("0 as 1st entry ignores
       parameter"), so ``0 4``'s second value does nothing.
     - [C1054]_, [M1054]_
   * - ``signed_tolerance_pair.reversed``
     - E
     - ``peptide_mass_tolerance_lower``, ``_upper``
     - ``lower <= upper``. See above.
     - [M572]_, [M1584]_
   * - ``signed_tolerance_pair.same_signed``
     - W
     - the pair
     - The window contains 0 (``R-PARAM-04``).
     - specification
   * - ``signed_tolerance_pair.asymmetric``
     - W
     - the pair
     - The window is symmetric about 0 (``R-PARAM-04``).
     - specification
   * - ``enzyme_in_table.missing``
     - E
     - ``search_enzyme_number``, ``search_enzyme2_number``,
       ``sample_enzyme_number``
     - The number is a row of the model's table -- the writer's refusal, as a
       finding at the field before anything is written. Comet 2026.02.2's own
       "missing definition" checks can never fire, so it searches on with a
       default identity; 2026.03.0 refuses the file at parameter load.
     - [C691]_, [D345]_, [V26Z]_
   * - ``enzyme_table.row_unreadable``
     - E
     - the parameters that select the row
     - A selected row's name is at most 47 bytes and its residues at most 19:
       Comet reads selected rows with ``"%lf %47s %d %19s %19s"``.
     - [C654]_, [C662]_
   * - ``enzyme_table.unused_row_unreadable``
     - W
     - none (``digestion_enzymes``)
     - The same, for a row nothing selects: Comet reads only selected rows.
     - [C654]_
   * - ``enzyme_table.numbering``
     - W
     - none (``digestion_enzymes``)
     - Rows are numbered 0, 1, 2 ... in order, as Comet's page asks; the
       source does not enforce it.
     - [EZ]_
   * - ``variable_mod_tuple.residues_too_long``
     - E
     - any slot, active or not
     - The residue token is at most 31 characters (``%31s`` into
       ``MAX_VARMOD_AA`` 32); a longer one shifts every later field.
     - [C589]_, [D25]_
   * - ``variable_mod_tuple.residue_not_in_release``
     - E
     - any slot, active or not
     - Every character of the residue token is in the release's residue
       alphabet: ``^`` and ``$`` are 2026.03.0's. Comet 2026.02.2 runs them
       without a word and never applies them; the codec refuses them on read
       and write, and this reports a model built in code at the field. The
       message offers ``n`` (``c``) at distance 0 from terminus 0 (1) instead.
     - [V26A]_, [M1380]_
   * - ``variable_mod_tuple.count_negative``
     - E
     - an active slot
     - Counts are whole numbers 0 and up (the page's generator: ``\d+``).
     - [VM]_
   * - ``variable_mod_tuple.count_reversed``
     - E
     - an active slot
     - ``min <= max``: Comet places at most the maximum and rejects fewer than
       the minimum, so a reversed pair never applies.
     - [S5729]_, [S5844]_
   * - ``variable_mod_tuple.count_zero``
     - W
     - an active slot
     - A maximum of 0 places nothing.
     - [S5729]_
   * - ``variable_mod_tuple.distance_undocumented``
     - V
     - an active slot
     - Distance is -2, -1 or 0 and up. Comet 2026.02.2 treats other
       negatives as -1 (a warning); 2026.03.0 refuses them (an error).
     - [VM]_, [S5371]_, [S5454]_, [V26X]_
   * - ``variable_mod_tuple.terminus_undocumented``
     - E
     - an active slot with a distance of 0 or more
     - Terminus 0 to 3; with a distance Comet 2026.02.2 matches no other code,
       so the modification never applies, and 2026.03.0 refuses it. (With
       distance -1 or -2 neither release consults the terminus, and it is not
       checked.)
     - [S5375]_, [S5466]_, [V26X]_
   * - ``variable_mod_tuple.requirement_undocumented``
     - W
     - an active slot
     - Requirement 0, 1 or -1; Comet treats any positive value as 1 and any
       other negative as 0. The message says which.
     - [M1401]_, [S5881]_
   * - ``variable_mod_tuple.binary_group_negative``
     - W
     - an active slot
     - Binary groups are documented as non-zero, used as positive numbers;
       Comet treats any non-zero value as binary.
     - [VM]_, [M1398]_
   * - ``variable_mods.required_without_slot``
     - E
     - ``require_variable_mod``
     - ``require_variable_mod = 1`` needs an active slot: Comet then scores
       only modified peptides.
     - [M543]_, [S2933]_
   * - ``variable_mods.minimum_above_limit``
     - E
     - the slot, ``max_variable_mods_in_peptide``
     - A slot's minimum count is at most ``max_variable_mods_in_peptide``,
       which caps a peptide's modified residues across all slots.
     - [S5740]_, [S5844]_
   * - ``variable_mods.required_but_none_allowed``
     - E
     - ``max_variable_mods_in_peptide``, then ``require_variable_mod`` and
       each required slot
     - A limit of 0 while a modification is required: nothing can qualify.
     - [S5740]_, [M543]_, [M1401]_
   * - ``variable_mods.none_allowed``
     - W
     - ``max_variable_mods_in_peptide``, then the active slots
     - A limit of 0 with active, optional slots: they have no effect.
     - [S5740]_
   * - ``variable_mods.ascorepro_slot_unsupported``
     - E
     - the slot, ``print_ascorepro_score``
     - AScorePro on (``print_ascorepro_score`` not 0) while a slot above
       ``variable_mod09`` is active after Comet merges each slot identical to
       a lower one into it. One finding per such slot.
     - [V26P]_, [V26G]_, [P920]_, [M3336]_
   * - ``index_search_type.ignored_without_idx``
     - V
     - ``index_search_type``, ``database_name``
     - ``index_search_type`` 0 or 1 while ``database_name`` does not end in
       ``.idx`` (Comet's own test, case-sensitive): the value has no effect.
     - [V26S]_, [V26F]_
   * - ``workflow_enforced.output_off``
     - E
     - ``output_pepxmlfile``, ``output_percolatorfile``
     - The output is on; the message names the stage that reads it (PDV and
       the Limelight export; Percolator).
     - ``R-CMT-01``
   * - ``decoy.prefix_empty``
     - E
     - ``decoy_prefix``
     - Not empty: Percolator and the Limelight converter tell decoys from
       targets by it, and Comet prepends it to internal decoys.
     - ``R-DEC-01``, ``R-DEC-03``
   * - ``unknown_parameter.imported``
     - W
     - the unknown name (no category)
     - Kept and written back unless removed (``R-PARAM-07``).
     - [C536]_
   * - ``version.parameter_unavailable``
     - E
     - the name (no category)
     - A parameter the metadata models for other Comet versions only:
       blocked, not ignored -- Comet would log "invalid parameter" and drop it.
     - [C536]_
   * - ``import.diagnostic``
     - W
     - as the parse gave it
     - Every other warning of the parse that produced the model (version
       marker mismatched or missing, ``R-PARAM-06``), carried in unchanged.
     - --
   * - ``migration.needs_attention``
     - E
     - the entry's parameter (its category; none if the set does not model
       the name)
     - A schema migration could not keep the value with its meaning and put
       the target release's default in its place (``NEEDS_ATTENTION``), and
       the scientist has not resolved the entry. Reported only by
       ``MigrationReview.validate``, never by ``CometValidator``, because it
       needs the migration's report
       (:ref:`dev-comet-parameter-migration-review`).
     - ``R-PARAM-13``

A slot is **active** when its mass difference is not 0 [M1368]_; the meaning
rules apply to active slots only.

**AScorePro and the slot merge.** Comet hands AScorePro each modified residue
followed by its slot number as decimal digits [P920]_ and registers slot *n* as
the one character ``'0' + n`` [M3336]_, so a two-digit slot reads as two
one-digit ones. Before any check, Comet merges every active slot identical to
a lower active one into it and switches it off [V26G]_ (the same loop in
2026.02.2 [V22G]_); "identical" compares, as ``double``\ s and integers, the
mass difference, both neutral losses (a missing second one is 0), the binary
group, the maximum count **after** Comet caps it at
``max_variable_mods_in_peptide`` (or at the release's default when that is
negative and ignored) [V26C]_, the minimum count (0 when there is none), the
requirement (never ``-1``: exclusive slots are never merged), the distance and
the terminus -- not the residues. Comet 2026.03.0 first rewrites ``n`` (``c``)
at distance 0 from terminus 0 (1), in a token of nothing else and its
protein-terminus code, to ``^`` (``$``) with distance -1 and terminus 0
[V26N]_; ``AScoreProRule.activeAfterMerge`` applies that rewrite only where the
release's alphabet has the code. The rule then reports each surviving slot 10
to 15. It is an **error in both releases**: 2026.03.0 refuses the file
[V26P]_, and 2026.02.2 -- whose catalogue said nothing -- was seen to crash in
post-analysis (case ``ascore-crash``, :ref:`dev-comet-parameter-validation-corpus`). A slot's maximum above
``max_variable_mods_in_peptide`` is ordinary and not reported. A negative
``max_variable_mods_in_peptide`` is the bounds rule's error and caps nothing
in the ``variable_mods`` rules, because Comet ignores it and keeps its default
[M534]_. Each slot holds
one count form, one requirement code and one terminus, so "two tuple semantics
in one slot" cannot be expressed and needs no rule. No Comet source or page
makes any combination of binary group with another field illegal, so none is
invented.

Unknown and unavailable parameters are decided from the model's **current**
unknown parameters, with the parser's own test (does the metadata model the
name for any version), so ``withoutUnknown(name)`` clears the finding; the
parse's ``UNKNOWN_PARAMETER`` and ``NOT_IN_VERSION`` diagnostics are therefore
not repeated in the report.

Comet's real defaults
---------------------

``RealDefaultsTest``: the real ``-q`` file, parsed, has exactly **one**
finding -- ``workflow_enforced.output_off`` at ``output_percolatorfile``,
because Comet's default is ``0`` and ``R-CMT-01`` requires ``1``. With the
workflow's outputs enforced it has **none**, no error and no warning; the same
holds for the real ``-p`` file and for the schema defaults with the real
enzyme table.

The model operations
--------------------

Two operations live on ``CometParameters``, because they change values and
origins rather than judge them:

``withWorkflowEnforcedOutputs()``
    ``R-CMT-01``: every parameter the metadata marks ``workflow_enforced``
    (``output_pepxmlfile``, ``output_percolatorfile``) set on, origin
    ``WORKFLOW_ENFORCED`` -- even where it was already on, so the editor shows
    it as locked by the workflow. ``WorkflowOutputs.stageNeeding(name)`` gives
    the stage in words for the editor's "Required by CometGUI workflow" text.
    Whether a stage is enabled for a given run (``AC-PAR-09``) is Phase 07's
    and 08's; at model level every stage is assumed on.

``decoySource()``, ``withDecoySource(source, origin)``
    ``R-DEC-01``: ``DecoySource`` is ``FASTA_CONTAINS_DECOYS``
    (``decoy_search = 0``), ``COMET_INTERNAL_CONCATENATED`` (``1``) or
    ``COMET_INTERNAL_SEPARATE`` (``2``), mapped both ways; an undocumented
    ``decoy_search`` has no source (Comet would treat it as 0 [M1125]_) and is
    the ``choice`` rule's error.

The specification's *Comet validation* list
-------------------------------------------

.. list-table::
   :header-rows: 1
   :widths: 46 54

   * - Item
     - Where
   * - Database exists and is readable
     - Phase 08's pre-run check (``PreRunChecks``, file system). Here:
       ``database_name`` present, no NUL, fits Comet's buffer.
   * - Spectra exist and use a supported format
     - Phase 08's pre-run check: spectra are run inputs, not parameters; each
       must exist, be readable and carry a spectrum extension Comet reads on
       this platform.
   * - Precursor tolerance values and units are valid
     - Here: the pair rule, ``choice`` on ``peptide_mass_units`` and
       ``precursor_tolerance_type``.
   * - Numeric ranges are ordered (subject to ``R-PARAM-04``)
     - Here: ``ordered_range`` and the pair rule.
   * - Peptide-length ranges are valid
     - Here: ``ordered_range`` and the bounds 1 to 50.
   * - Selected enzyme numbers exist in the serialised table
     - Here: ``enzyme_in_table`` (and the writer's refusal).
   * - Variable-modification tuples are internally valid
     - Here: ``variable_mod_tuple``.
   * - Modification counts are consistent with version limits
     - Here: the tuple count rules, the 31-character residue limit, the
       ``R-PARAM-10`` rules and the bounds; the fifteen slots are the
       metadata's.
   * - ``output_pepxmlfile`` and ``output_percolatorfile`` are enabled
     - Here, at model level; locking the controls is Phase 07's; the pre-run
       check refuses a model without them (Phase 08).
   * - Selected index and search options are compatible
     - **Partly here**: ``index_search_type`` against the text of
       ``database_name`` (``index_search_type.ignored_without_idx``, 2026.03.0).
       The rest depends on whether ``database_name`` names an existing
       ``.idx`` and which type that file records (Comet reads only the first
       five variable modifications for a fragment-ion index [K77]_), so it needs the
       file system: assigned to Phase 08 by tier 1 on 2026-10-06 and built
       there as the index rules of :ref:`dev-comet-parameter-prerun-facts`.
   * - Decoy configuration satisfies *Target/decoy strategy*
     - Here: the decoy source (``R-DEC-01``) and the prefix. Phase 08 built
       the FASTA scan and its two blocks (``R-DEC-02``) and the PIN check
       (``R-DEC-04``); carrying the prefix to Percolator and the Limelight
       converter (``R-DEC-03``) arrives with those steps.
   * - Output paths are writable
     - Phase 08's pre-run check: the project's ``runs/`` directory must exist
       and be writable; every output is written inside the run directory.
   * - Imported unknown parameters are surfaced
     - Here (``unknown_parameter.imported``) and in the parse result (unit 4).
   * - Parameters unavailable in the selected version are blocked
     - Here (``version.parameter_unavailable``).


.. _dev-comet-parameter-prerun-facts:

Pre-run facts: the FASTA's decoys and an existing index
=======================================================

Phase 08, unit 3. Two checks need the file system: whether the FASTA already
holds decoys (``R-DEC-02``), and whether an existing ``.idx`` file Comet would
search agrees with the search (the specification's *selected index and search
options are compatible*, assigned to Phase 08 by tier 1 on 2026-10-06). Each is
split into a **reader**, which turns a file into a pure value, and **rules of
the one validator**, which judge that value against the model -- so Run
readiness and the workflow's validate step block on one report and there is no
second validator (design decisions P8-1, P8-7, P8-8, P8-16).

.. list-table::
   :header-rows: 1
   :widths: 34 26 40

   * - Class
     - Package
     - What it is
   * - ``FastaDecoyCensus``
     - ``org.cometgui.domain.params``
     - The prefix scanned for, the FASTA, its record count, its decoy count
       and the first decoy's accession.
   * - ``CometIndexDescription``
     - ``org.cometgui.domain.params``
     - An index header as typed values: format number and first line, the
       Comet that wrote it, the index type (``IndexMode.FRAGMENT_ION`` or
       ``PEPTIDE``), and every recorded option. Options a format does not
       record are empty.
   * - ``PreRunFacts``
     - ``org.cometgui.domain.params``
     - An optional census and an optional index description.
   * - ``FastaDecoyScanner``
     - ``org.cometgui.tools.comet``
     - Streams a FASTA (LF, CRLF or CR) into a census. Refuses, with a
       ``FastaScanException`` naming the file, a missing, unreadable or empty
       file and one whose first non-blank line does not begin with ``>``.
   * - ``CometIndexHeaderReader``
     - ``org.cometgui.tools.comet``
     - Reads at most the first 64 KiB of an ``.idx`` file into a description.
       Refuses, with a ``CometIndexHeaderException`` naming the file, anything
       that is not a versioned Comet index and any header line it cannot vouch
       for (an unknown key, a repeated one, a missing required one, a value
       that is not what Comet writes).
   * - ``FastaDecoyRule``, ``IndexCompatibilityRule``
     - ``org.cometgui.params.comet.validation``
     - The rules, reached through ``CometValidator.validate(model,
       PreRunFacts)``; ``validate(model)`` is ``validate(model,
       PreRunFacts.none())``.

**When to supply what.** A census whenever the FASTA has been scanned; it must
be taken for the model's own ``decoy_prefix`` (``R-DEC-03``), and one taken for
another prefix makes ``validate`` throw ``IllegalArgumentException`` rather
than judge with it. An index description exactly when Comet will read an
**existing** index -- ``database_name``, or ``-D``, names a file whose name
ends in ``.idx`` (Comet's own case-sensitive test [V26S]_) and that file exists.
An index Comet has yet to build has no description, and a plain FASTA search
has none.

The decoy rules
---------------

``decoy.none_anywhere`` (``decoy_search = 0`` and no decoy record) and
``decoy.double_decoys`` (``decoy_search`` 1 or 2 and any decoy record) are
errors at ``decoy_search`` and ``database_name``. Their messages, and the two
combinations that pass, are on the user page :doc:`/decoys`; the tests hold
the messages hand-typed against the censuses of the real ``D-006`` subset
(1000 records, no decoy) and of a target-decoy FASTA built from it (2000
records, 1000 decoys, the first ``DECOY_sp|A0A075B6H9|LV469_HUMAN``), which
``CometIndexRealBinaryTest`` counts with the real scanner. An undocumented
``decoy_search`` has no decoy source and is the choice rule's error only.

The index header, measured
--------------------------

Every fact below was established on 2026-10-06 by running the pinned
linux/x86-64 binaries (2026.03.0 SHA-256 ``ad93b4cf...``, 2026.02.2
``af515b6e...``, and the migration fixture 2024.01.0 ``2834f928...``) in a
private scratch directory, on the ``D-006`` subset (the UniProt proteome's first
1000 records, SHA-256 ``5005d961...``) and the LF copy of the K562 run. Each
index was built as the workflow builds one, in a directory holding the
release's own ``comet -q`` output with ``database_name = subset.fasta``,
``spectral_library_name`` empty and ``num_threads = 4``, and ``subset.fasta``, a
symbolic link to the subset::

    cd <index directory> && comet -Pcomet.params -i -Dsubset.fasta    # or -j

Comet wrote ``subset.fasta.idx`` there and nothing beside the FASTA, and
``InputDB:`` records the name it was given (``subset.fasta``), so the header
holds no machine path and two builds write the same bytes. What the file holds:

* **Line 1**: ``Comet index database v5.  Comet version 2026.03 rev. 0
  (fa08489)`` (2026.03.0) or ``... v4.  Comet version 2026.02 rev. 2
  (6edec91)`` (2026.02.2). Comet 2024.01.0 writes ``Comet peptide index.
  Comet version 2024.01 rev. 0 (f00df0c)``: no format number, and no empty
  line after its header [I24R]_. The reader refuses it as not a versioned
  index.
* **Then one ``Key: value`` line per option** [I26W]_ [I22W]_, in this order:
  ``IndexSearchType`` (``fragment ion index`` or ``peptide index``),
  ``InputDB``, ``MassRange``, ``LengthRange``, ``MassType``, ``DecoySearch``,
  ``DecoyPrefix`` (format 5 only), ``Enzyme``, ``Enzyme2``,
  ``NumEnzymeTermini``, ``AllowedMissedCleavage``, ``ClipNtermMethionine``
  (these three format 5 only), ``NumPeptides``, ``StaticMod`` (30 values:
  ``A``-``Z``, then the peptide N- and C-terminus and the protein N- and
  C-terminus), ``VariableMod`` (five slots, ``residues:mass:loss:loss2:max``,
  format 5 adding ``:distance:terminus``), ``ProteinModList``,
  ``RequireVariableMod`` (six integers: Comet's requirement flags, whose
  lowest bit is ``require_variable_mod``, then each slot's requirement) and
  ``MaxVariableModsInPeptide``.
* **Then an empty line** -- where the header ends. Both releases' readers stop
  there [I26E]_. After it come the protein names, each padded with NUL bytes,
  and the binary index. The headers measured 925 bytes (2026.03.0, ``-i``),
  920 (``-j``), 812 and 807 (2026.02.2); the whole files 7.8 MB.

Six captures are committed, header only, under
``cometgui-tools/src/test/resources/fixtures/comet-index/<release>/linux-x86-64/``
with their ``SHA256SUMS``: each release's ``-i`` and ``-j`` index of the subset
from its own defaults, and a ``-i`` index built with modifications Comet
rewrites, merges, caps and drops (``fragment-ion-mods.idx-header``: the edits
are listed in ``IndexHeaders.MODS_EDITS``). ``CometIndexRealBinaryTest``
rebuilds all six with both binaries on every Linux build and requires the same
bytes; ``CometIndexHeaderReaderTest`` reads each field by field.

.. _dev-comet-parameter-index-formats:

Which index formats a release reads
-----------------------------------

Each release searched each release's fragment-ion index, with ``-P`` the
release's ``-q`` file plus ``database_name = <the .idx>``,
``spectral_library_name`` empty, ``num_threads = 4``,
``output_percolatorfile = 1``, ``scan_range = 11000 11300``::

    comet -P<case.params> -N<dir>/out <K562_3, LF copy>

.. list-table::
   :header-rows: 1
   :widths: 18 41 41

   * - Searched by
     - Format 5 index (2026.03.0's)
     - Format 4 index (2026.02.2's)
   * - 2026.03.0
     - exit 0, PIN written
     - exit 1: ``Error - "<idx>" is not a v5 unified index file (v4 and older
       are intentionally not read: the protein-list layout changed for
       protein-terminal variable mods). Rebuild it from the FASTA with -i
       (FI_DB) or -j (PI_DB).``
   * - 2026.02.2
     - exit 1: ``Error - "<idx>" is not a v4 unified index file; rebuild it
       with -i or -j.``
     - exit 0, PIN written
   * - 2024.01.0
     - aborted (exit 134)
     - aborted (exit 134)

Given 2024.01.0's unversioned index, 2026.03.0 and 2026.02.2 each stopped with
the message in its row. So each release reads exactly its own format [I26V]_
[I22V]_, and 2024.01.0 none of the versioned ones.

That is a release fact, so by decision C-2 it is data, in each version
record::

    "indexFormats": {
      "readable": [ 5 ],
      "source": "https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1640-L1653"
    }

``readable`` is ``[5]`` for 2026.03.0, ``[4]`` for 2026.02.2 and ``[]`` for
2024.01.0. Java holds it as ``IndexFormats`` (``CometVersionRecord.indexFormats()``;
a record built in code without one reads no index, ``IndexFormats.unstated()``).
What ``MetadataLoader`` refuses, naming ``versions[i] indexFormats`` and the
field: a missing member or one that is not an object, a field other than
``readable`` and ``source``, a ``readable`` that is not an array, an element
that is not a whole number of 1 or more (or above the largest integer), a
format listed twice, and a ``source`` that is not ``https://``
(``IndexFormatsLoaderTest``). ``scripts/cometparams.py`` refuses the same
(``_validate_index_formats``), each with its own diagnostic, and its self-test
has a case for each (``index-formats-*`` in ``scripts/cometparams_selftest.py``).

``index.format_unreadable`` (error, at ``database_name``) reads the model's
release's record, never its version number::

    the index /project/index-cache/k1/subset.fasta.idx is in format v4 (written
    by Comet 2026.02 rev. 2 (6edec91)), and Comet 2026.03.0 reads index format
    v5, so it would stop before searching; rebuild the index from its FASTA
    with Comet 2026.03.0

An index a release cannot read is judged for nothing else.
``IndexCompatibilityRulesTest.formatByRelease`` judges the same two real
indexes for both releases, and ``IndexCompatibilityRealBinaryTest`` holds both
binaries to the metadata's ``readable`` lists, so a check that ignored the
release fails one test or the other.

What an index decides, measured
-------------------------------

Both releases' readers begin by discarding the parameter file's modifications:
"only the values baked into the .idx header are authoritative for an index
search" [I26H]_ [I22H]_, and the parse that follows restores every recorded
option. To see what that means for each option, each release built a peptide
index (``-j``) of the subset from its own defaults, then searched it with one
option changed (the search command above), and searched the plain FASTA with
the same change for comparison. The unchanged index search wrote 495 PIN rows.

.. list-table::
   :header-rows: 1
   :widths: 30 38 32

   * - Changed in the search
     - Both releases, index search
     - Decision
   * - ``decoy_search = 1``; ``search_enzyme_number = 3`` (Lys_C);
       ``num_enzyme_termini = 1``; ``allowed_missed_cleavage = 0``;
       ``clip_nterm_methionine = 1``; ``mass_type_parent = 0``;
       ``mass_type_fragment = 0``; ``add_C_cysteine = 0.0``;
       ``variable_mod01`` off; ``variable_mod02`` and ``variable_mod06`` set
       to ``79.966331 STY``; ``require_variable_mod = 1``;
       ``max_variable_mods_in_peptide = 1``; ``peptide_length_range = 3 50``
     - exit 0, no warning, **the same 495 rows** as the unchanged search. The
       same change to the FASTA search **changed** its rows.
     - Silently ignored: an **error**, ``index.contradicts_search``, one
       finding per option.
   * - ``search_enzyme2_number = 3``; ``digest_mass_range = 400.0 6000.0``
     - exit 0, no warning, the same rows. (On these scans the change does not
       alter a FASTA search either.)
     - The index cannot hold what the search asks for: an **error**.
   * - ``digest_mass_range = 600.0 1500.0``;
       ``peptide_length_range = 5 10``
     - exit 0, **fewer** rows: Comet clamps an index search's range inward
       only.
     - Applied, so not a contradiction: no finding. Only a range reaching
       outside the index's is one.
   * - ``index_search_type = 1`` against a peptide index (and ``0`` against a
       fragment-ion one)
     - exit 0, the same rows. 2026.03.0: ``Warning - index_search_type = 1 is
       ignored: "<idx>" is a peptide index and its own IndexSearchType:
       header line decides. Delete the file or rebuild it with -i to change
       the type.`` 2026.02.2: silent.
     - An **error** in both releases -- stricter than 2026.03.0's warning,
       because the search runs as a type the scientist did not ask for.
       ``-1`` (2026.03.0, not set) asks for nothing and is never a finding;
       2026.02.2 has no "not set", so its default ``1`` against a peptide
       index is a finding, fixed by setting ``0``.
   * - ``decoy_prefix = REV_``, on a fragment-ion index of the target-decoy
       FASTA built with ``DECOY_``
     - 2026.03.0: the same rows, decoys still labelled ``-1``: the index's
       ``DecoyPrefix:`` wins. 2026.02.2 (format 4 records no prefix): every
       row labelled a target -- the search's prefix is applied.
     - An **error** where the index records a prefix; nothing to compare where
       it does not.
   * - ``protein_modslist_file`` naming a list of 400 accessions, at build
       or search time, in all four combinations
     - Both releases wrote ``ProteinModList: 0`` even when built with the list,
       and all four searches wrote identical rows.
     - Read, **not compared**: nothing measured depends on it.

``IndexCompatibilityRealBinaryTest`` repeats every row of this table with both
binaries on every Linux build and asserts it, running no production class of
the module (as the corpus's binary half does, for PIT's sake).

How the comparison is made
--------------------------

``IndexCompatibilityRule`` compares what Comet would hold, not the text the
scientist wrote. The modifications capture shows Comet's own rewriting, and
the rule models each step from the source:

* identical slots are **merged** into the first (``variable_mod02`` ``W``
  into slot 1, written ``MW``, slot 2 written ``-`` with mass 0) -- the merge
  ``AScoreProRule.activeAfterMerge`` already models [V26G]_, now also
  recording which slots each survivor absorbed;
* each slot's count is **capped** at ``max_variable_mods_in_peptide`` and, for
  a fragment-ion index, at 5 (``FRAGINDEX_MAX_MODS_PER_MOD``) [I26C]_: a count
  of 7 is written 5;
* in a release whose residue alphabet has ``^``, ``n`` at distance 0 from the
  protein N-terminus is **rewritten** ``^`` with distance -1 [V26N]_
  (2026.02.2 keeps ``n``);
* only **five slots** are held [K77]_: ``variable_mod06`` is not in the index
  at all, so an active sixth slot is a contradiction even for an index built
  from the same settings -- Comet would search without it;
* masses are compared to the header's six decimals (a difference of at most
  0.0000005 is the same number); residues as sets of characters; a slot's
  requirement against ``RequireVariableMod:``, and ``require_variable_mod``
  against its lowest bit.

``IndexCompatibilityRulesTest.modsIndex`` validates the settings the
modifications capture was built from against that capture and finds exactly
one contradiction, ``variable_mod06``, for both releases.

What the rule cannot see, and says so: format 4 records no enzyme termini,
missed cleavages or methionine clipping [I22W]_, although its peptides were
digested with them and 2026.02.2 searches those peptides whatever the search
says (rows ``num_enzyme_termini``, ``allowed_missed_cleavage`` and
``clip_nterm_methionine`` above). ``index.option_unrecorded`` is a **warning**
naming them and the search's values. Format 4 records no terminal distance
either, but 2026.02.2 applies the search's, so it is not compared.

Known edges, recorded rather than guessed at: Comet 2026.02.2 writes ``U`` as
0 whatever ``add_U_selenocysteine`` says (it reads another name,
:ref:`dev-comet-parameter-202603`), so a non-zero value there is reported
against every 2026.02.2 index -- a value that release never applies anyway;
and Comet applies the fragment-ion cap of 5 *before* merging, the model after,
so two slots that differ only in counts above 5 merge in Comet and not in the
rule (the same edge as :ref:`dev-comet-parameter-validation-corpus`'s). Comet
2026.03.0 also warns, on its own, about ``decoy_search`` 1 or 2 against an
index whose protein list already holds the decoy prefix [I26G]_; that index's
decoys are the FASTA census's to catch.

.. [I26V] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1640-L1653
   -- 2026.03.0 refuses an index whose first line does not begin ``Comet
   index database v5``.
.. [I22V] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPeptideIndex.cpp#L1112-L1120
   -- 2026.02.2 refuses one that does not begin ``Comet index database v4``.
.. [I26H] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1613-L1640
   -- the header is authoritative: the parameter file's modifications are
   discarded before the header is parsed.
.. [I22H] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPeptideIndex.cpp#L1090-L1131
   -- the same in 2026.02.2.
.. [I26W] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1350-L1429
   -- the format-5 header writer.
.. [I22W] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPeptideIndex.cpp#L880-L943
   -- the format-4 header writer: no ``DecoyPrefix``, ``NumEnzymeTermini``,
   ``AllowedMissedCleavage`` or ``ClipNtermMethionine`` line.
.. [I26E] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L1661-L1665
   -- the reader stops at the empty line (2026.02.2: lines 1128-1132).
.. [I26C] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometSearchManager.cpp#L1402-L1414
   -- the count caps; ``FRAGINDEX_MAX_MODS_PER_MOD`` is 5
   (``CometSearch/core/Constants.h`` line 53, both tags).
.. [I26G] https://github.com/UWPR/Comet/blob/v2026.03.0/CometSearch/CometPeptideIndex.cpp#L484-L511
   -- the warning about internal decoys on a target-decoy index.
.. [I24R] https://github.com/UWPR/Comet/blob/v2024.01.0/CometSearch/CometFragmentIndex.cpp#L830-L855
   -- 2024.01.0's reader of its unversioned index.

.. _dev-comet-parameter-validation-corpus:

Validation agreed with the real binaries
========================================

Comet 2026.03.0 intake, unit 4. For every rule the release changed, a corpus
of parameter files was run through **both** real binaries, 2026.03.0 and
2026.02.2, and the validator, given each release, was made to agree with that
release's binary on every case. The corpus is
``cometgui-params-comet/src/test/resources/fixtures/comet-validation/corpus.json``;
the agreement is proved in two halves against the verdicts recorded there.
``ValidationCorpusTest`` (every platform, no binary) holds the **validator** to
each recorded finding; ``ValidationCorpusRealBinaryTest`` (Linux) re-runs both
**binaries** and holds each to its recorded exit code and lines. The
real-binary half runs no production class of the module -- it builds each
file by plain text edits of the ``-q`` fixture bytes -- because a test that
parsed or validated after 86 searches would be mapped by PIT to every parser
and validator mutant, re-run the searches for each, and time out; the params
gate scores a timeout as not killed.

The agreement criterion
-----------------------

Each run is classed **error** (the binary exits non-zero), **warning** (it
exits 0 and prints a ``Warning`` line beyond its release's control run) or
**clean**; the validator's report is classed **error** (``hasErrors()``),
**warning** (findings, none an error) or **clean**. Then:

* binary error: validator error;
* binary warning: validator warning -- or error, only with a recorded reason;
* binary clean: validator clean -- or a recorded deliberate stricter judgement
  (the setting is a silent no-op, never applies, or cannot be written).

The validator is **never** less severe than the binary. A case recorded
``same`` must have equal classes; one recorded ``stricter`` must be strictly
more severe and carry its reason (``S1``-``S8`` below). Both tests assert it
for every recorded verdict; the validator half also asserts the exact
findings, and the binary half the exact exit code and lines.

How each case was run
---------------------

Every case is the release's own ``comet -q`` fixture
(``fixtures/comet/<version>/linux-x86-64/comet-q.params``) with five **base
edits** and then the case's edits, each replacing the one line that declares
the parameter -- CONSTRUCTED input. The base edits: ``database_name`` the
first 1000 records of the UniProt human proteome ``UP000005640_9606.fasta``
(SHA-256 ``5005d961...558f`` for the subset); ``spectral_library_name``
empty (``-q``'s placeholder ``/some/path/speclib.file`` stops every search
with ``Error (5) - cannot read spectral library file``); ``scan_range = 11188
11192``; ``num_threads = 4``; ``output_percolatorfile = 1`` (so the base model
is clean: ``R-CMT-01``). Each run, in its own directory::

    comet -P<dir>/case.params -N<dir>/out <K562_3.mzML, LF copy>

with the pinned mirror binaries ``v2026.03.0__comet.linux.exe`` (SHA-256
``ad93b4cf...91e7ed``) and ``v2026.02.2__comet.linux.exe``. The spectra are
the Crux K562 run ``20100614_Velos1_TaGe_SA_K562_3.mzML`` (``D-006``; SHA-256
``cbd0c1b3...0bc7``), whose CRLF line endings break its index, so the run uses
an LF copy (SHA-256 ``a562f6e6...54da``). Neither file is committed; the test
reads both from ``scratch/fixture/`` and fails, naming the file, when one is
missing or changed. Refill with ``python3
scripts/feasibility/fetch_ephemeral_input.py``, which fetches by checksum.
``scripts/verify-test-gates.sh`` stops at once when ``scratch/fixture`` is
missing. Each search takes about 0.2 s; the whole corpus, both releases, runs
in about 5 s on eight threads.

Recorded per run: the exit code and every line holding ``Warning`` or
``Error``, from standard output and standard error, less the release's
**control** lines -- what its own base file prints anyway. Comet 2026.03.0's
control prints nothing; 2026.02.2's prints ``Warning - invalid parameter
found: spectral_library_ms_level.  Parameter will be ignored.`` on every run,
because its reader does not know the name its own ``-q`` writes
(:ref:`dev-comet-parameter-202603`). That warning is about the release's
template, not about any value a user chooses (Comet's own file draws it), so
the validator reports nothing for it, and the corpus judges each case by what
it adds to the control.

Which checks fire where: only the enzyme-definition errors fire at parameter
load (``comet -P<file> missing.mzML`` reaches them); the distance and terminus
errors, the AScorePro error and both ``index_search_type`` warnings fire only
once a search starts (with a missing input the run stops first at ``Error -
input file "missing.mzML" not found.``).

The corpus
----------

.. list-table::
   :header-rows: 1
   :widths: 16 26 29 29

   * - Case
     - Edit (CONSTRUCTED)
     - Comet 2026.03.0
     - Comet 2026.02.2
   * - ``control``
     - none (the base file)
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``res-protein-n``
     - ``variable_mod02 = 42.010565 ^ 0 1 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.residue_not_in_release``. **stricter [S1]**
   * - ``res-protein-c``
     - ``variable_mod02 = -0.984016 $ 0 1 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.residue_not_in_release``. **stricter [S1]**
   * - ``res-n-and-protein-n``
     - ``variable_mod02 = 42.010565 n^ 0 1 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.residue_not_in_release``. **stricter [S1]**
   * - ``res-mixed``
     - ``variable_mod02 = 42.010565 K^c$ 0 1 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.residue_not_in_release``. **stricter [S1]**
   * - ``res-protein-n-unused``
     - ``variable_mod02 = 0.0 ^ 0 3 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.residue_not_in_release``. **stricter [S2]**
   * - ``dist-minus3``
     - ``variable_mod01 = 15.9949 M 0 3 -3 0 0 0.0``
     - exit 1, error [M1]. Validator: error ``variable_mod_tuple.distance_undocumented``. **same**
     - exit 0, silent. Validator: warning ``variable_mod_tuple.distance_undocumented``. **stricter [S3]**
   * - ``dist-minus2``
     - ``variable_mod01 = 15.9949 M 0 3 -2 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``dist-minus3-unused``
     - ``variable_mod02 = 0.0 X 0 3 -3 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``term4-dist2``
     - ``variable_mod01 = 15.9949 M 0 3 2 4 0 0.0``
     - exit 1, error [M2]. Validator: error ``variable_mod_tuple.terminus_undocumented``. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.terminus_undocumented``. **stricter [S4]**
   * - ``termminus1-dist0``
     - ``variable_mod01 = 15.9949 M 0 3 0 -1 0 0.0``
     - exit 1, error [M3]. Validator: error ``variable_mod_tuple.terminus_undocumented``. **same**
     - exit 0, silent. Validator: error ``variable_mod_tuple.terminus_undocumented``. **stricter [S4]**
   * - ``term4-dist-minus1``
     - ``variable_mod01 = 15.9949 M 0 3 -1 4 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``term7-dist-minus2``
     - ``variable_mod01 = 15.9949 M 0 3 -2 7 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-slot10``
     - ``variable_mod10 = 79.966331 STY 0 3 -1 0 0 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-slot10-off``
     - ``variable_mod10 = 79.966331 STY 0 3 -1 0 0 0.0``; ``print_ascorepro_score = 0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-slot10-all``
     - ``variable_mod10 = 79.966331 STY 0 3 -1 0 0 0.0``; ``print_ascorepro_score = -1``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-slot15``
     - ``variable_mod15 = 79.966331 STY 0 3 -1 0 0 0.0``
     - exit 1, error [M5]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-slot09``
     - ``variable_mod09 = 79.966331 STY 0 3 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-merged``
     - ``variable_mod10 = 15.9949 M 0 3 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-merged-residues``
     - ``variable_mod10 = 15.9949 STY 0 3 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-merged-digits``
     - ``variable_mod10 = 15.99490 M 0 3 -1 0 0 0.00``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-count-differs``
     - ``variable_mod10 = 15.9949 M 0 2 -1 0 0 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-merged-by-cap``
     - ``variable_mod10 = 15.9949 M 0 7 -1 0 0 0.0``; ``max_variable_mods_in_peptide = 3``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ascore-cap-not-reached``
     - ``variable_mod10 = 15.9949 M 0 7 -1 0 0 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-exclusive``
     - ``variable_mod01 = 15.9949 M 0 3 -1 0 -1 0.0``; ``variable_mod10 = 15.9949 M 0 3 -1 0 -1 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-merged-unused-lower``
     - ``variable_mod01 = 0.0 M 0 3 -1 0 0 0.0``; ``variable_mod10 = 15.9949 M 0 3 -1 0 0 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-merged-rewrite``
     - ``variable_mod01 = 42.010565 n 0 1 0 0 0 0.0``; ``variable_mod10 = 42.010565 n 0 1 -1 0 0 0.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-crash``
     - ``scan_range = 11000 12500``; ``print_ascorepro_score = -1``; ``variable_mod01 = 0.0 M 0 3 -1 0 0 0.0``; ``variable_mod10 = 15.9949 M 0 3 -1 0 0 0.0``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 139 (signal 11, segmentation fault), no message. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
   * - ``ascore-neutral-loss-differs``
     - ``variable_mod10 = 15.9949 M 0 3 -1 0 0 63.998285``
     - exit 1, error [M4]. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``. **stricter [S5]**
   * - ``ascore-slot10-dist-invalid``
     - ``variable_mod10 = 79.966331 STY 0 3 -3 0 0 0.0``
     - exit 1, error [M6]. Validator: error ``variable_mod_tuple.distance_undocumented``; error ``variable_mods.ascorepro_slot_unsupported``. **same**
     - exit 0, silent. Validator: error ``variable_mods.ascorepro_slot_unsupported``; warning ``variable_mod_tuple.distance_undocumented``. **stricter [S5]**
   * - ``ist-1``
     - ``index_search_type = 1``
     - exit 0, warning [M7]. Validator: warning ``index_search_type.ignored_without_idx``. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ist-0``
     - ``index_search_type = 0``
     - exit 0, warning [M8]. Validator: warning ``index_search_type.ignored_without_idx``. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ist-minus1``
     - ``index_search_type = -1``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: error ``choice.not_listed``. **stricter [S6]**
   * - ``ist-99``
     - ``index_search_type = 99``
     - exit 0, warning [M9]. Validator: error ``choice.not_listed``. **stricter [S7]**
     - exit 0, silent. Validator: error ``choice.not_listed``. **stricter [S7]**
   * - ``ist-minus5``
     - ``index_search_type = -5``
     - exit 0, warning [M10]. Validator: error ``choice.not_listed``. **stricter [S7]**
     - exit 0, silent. Validator: error ``choice.not_listed``. **stricter [S7]**
   * - ``ist-1-idx-name``
     - ``index_search_type = 1``; ``database_name = ${DATABASE}.idx``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``ist-0-idx-name``
     - ``index_search_type = 0``; ``database_name = ${DATABASE}.idx``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``enz-search-99``
     - ``search_enzyme_number = 99``
     - exit 1, error [M11]. Validator: error ``enzyme_in_table.missing``. **same**
     - exit 0, silent. Validator: error ``enzyme_in_table.missing``. **stricter [S8]**
   * - ``enz-search2-99``
     - ``search_enzyme2_number = 99``
     - exit 1, error [M12]. Validator: error ``enzyme_in_table.missing``. **same**
     - exit 0, silent. Validator: error ``enzyme_in_table.missing``. **stricter [S8]**
   * - ``enz-sample-99``
     - ``sample_enzyme_number = 99``
     - exit 1, error [M13]. Validator: error ``enzyme_in_table.missing``. **same**
     - exit 0, silent. Validator: error ``enzyme_in_table.missing``. **stricter [S8]**
   * - ``selenocysteine``
     - ``add_U_selenocysteine = 10.0``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**
   * - ``speclib-level-2``
     - ``spectral_library_ms_level = 2``
     - exit 0, silent. Validator: clean. **same**
     - exit 0, silent. Validator: clean. **same**

The binaries' lines, verbatim (``${DATABASE}`` standing for the database path):

M1
    ``Error - variable_mod01 (M): invalid term_distance/which_term "-3 0"; term_distance must be -2, -1 or >= 0, and which_term 0-3 (0 = protein N, 1 = protein C, 2 = peptide N, 3 = peptide C).``
M2
    ``Error - variable_mod01 (M): invalid term_distance/which_term "2 4"; term_distance must be -2, -1 or >= 0, and which_term 0-3 (0 = protein N, 1 = protein C, 2 = peptide N, 3 = peptide C).``
M3
    ``Error - variable_mod01 (M): invalid term_distance/which_term "0 -1"; term_distance must be -2, -1 or >= 0, and which_term 0-3 (0 = protein N, 1 = protein C, 2 = peptide N, 3 = peptide C).``
M4
    ``Error - print_ascorepro_score is enabled but variable_mod10 is active; AScorePro localization is only supported for variable_mod01 through variable_mod09. Disable print_ascorepro_score or remove/renumber the higher-numbered variable mod(s).``
M5
    ``Error - print_ascorepro_score is enabled but variable_mod15 is active; AScorePro localization is only supported for variable_mod01 through variable_mod09. Disable print_ascorepro_score or remove/renumber the higher-numbered variable mod(s).``
M6
    ``Error - variable_mod10 (STY): invalid term_distance/which_term "-3 0"; term_distance must be -2, -1 or >= 0, and which_term 0-3 (0 = protein N, 1 = protein C, 2 = peptide N, 3 = peptide C).``
M7
    ``Warning - index_search_type = 1 is ignored: "${DATABASE}" is not an .idx file (plain FASTA search). It only selects the index type to auto-build when database_name names an .idx file that does not exist yet.``
M8
    ``Warning - index_search_type = 0 is ignored: "${DATABASE}" is not an .idx file (plain FASTA search). It only selects the index type to auto-build when database_name names an .idx file that does not exist yet.``
M9
    ``Warning - index_search_type = 99 is not -1, 0 or 1; using the default (-1, not set).``
M10
    ``Warning - index_search_type = -5 is not -1, 0 or 1; using the default (-1, not set).``
M11
    ``Error - search_enzyme_number 99 is missing definition in params file.``
M12
    ``Error - search_enzyme2_number 99 is missing definition in params file.``
M13
    ``Error - sample_enzyme_number 99 is missing definition in params file.``

Why the validator is stricter than the binary:

S1
    Comet 2026.02.2 runs ^ and $ without a word and never applies them: searches with and without such a slot gave byte-identical results (unit 3), and the codec refuses to write the token for this release, so the slot is reported where it is.
S2
    The slot is unused and Comet ignores it, but the token holds a character this release's residue alphabet does not have, so the file cannot be written; the slot is reported where it is.
S3
    Comet 2026.02.2 treats a distance below -2 as -1 without a word (scans 11000-12500: results byte-identical to -1); a value it silently reinterprets is a warning, as the catalogue had it.
S4
    With a distance of 0 or more Comet 2026.02.2 matches no terminus outside 0-3, so the modification never applies (scans 11000-12500: results byte-identical to the slot unused).
S5
    Comet 2026.02.2 hands AScorePro two-digit slot numbers it cannot read: the same kind of configuration crashed with signal 11 in post-analysis over scans 11000-12500 (case ascore-crash), and the 2026.03.0 release notes call it site corruption; five scans here gave AScorePro nothing to localise.
S6
    -1 is not a 2026.02.2 choice (unit 1): that release reads it as 1, as it reads every value but 0, and documents 0 and 1 only.
S7
    Comet replaces an out-of-range code by substitution (2026.03.0 with -1 and a warning, 2026.02.2 reads it as 1 silently); the choice rule refuses a value whose meaning Comet decides for the user.
S8
    Comet 2026.02.2 searches on with a default enzyme identity when the number names no row; the writer cannot write a reference to a missing row.

Cases the binaries cannot settle here
-------------------------------------

* **A database name ending in .idx.** ``ist-1-idx-name`` and
  ``ist-0-idx-name`` name an ``.idx`` that does not exist yet: both releases
  build the index and exit 0 silently, and the validator is clean. With an
  existing ``.idx``, 2026.03.0 warns when the value disagrees with the file's
  own type (``is a peptide index and its own IndexSearchType: header line
  decides``); that needs the file, and Phase 08's index rules make the
  contradiction an error (``index.contradicts_search``). The text-only rule fires
  only for a name that does not end in ``.idx``, which is exactly Comet's own
  test [V26S]_.
* **The merge when an index is built.** When Comet builds a fragment-ion
  index it also caps each slot's count at 5 before merging [V26C]_; whether it
  builds one depends on the file system, so the merge model applies the
  ``max_variable_mods_in_peptide`` cap only. Two slots that differ only in
  counts above 5 could then merge in Comet and not in the model.
* **The -i and -j index-building flags.** 2026.03.0 warns when an explicit
  ``index_search_type`` disagrees with ``-i``/``-j``; the workflow never
  passes them.
* **Windows and macOS binaries, and real-time search.** Not run; the corpus is
  the linux/x86-64 binaries'.
* **AScorePro corruption without a crash.** 2026.02.2 crashed (case
  ``ascore-crash``) when AScorePro localised the slot-10 modification; with a
  target of slot 1 and a phosphorylation in slot 10, scans 11000-12500 of the
  full proteome gave the same results as with the modification in slot 2 but
  for 18 E-values that differ slightly. The release notes' "corrupted
  modification sites" were not reproduced beyond the crash; the source shows
  the mechanism [P920]_ [M3336]_.

Catalogue changes for 2026.02.2, with their evidence
-----------------------------------------------------

The rule catalogue's behaviour for 2026.02.2 is unchanged except for one new
error: the new rule ``variable_mods.ascorepro_slot_unsupported`` **is an error
for 2026.02.2 as well**. The binary is silent on five scans, but over scans
11000-12500 with ``print_ascorepro_score = -1`` and only ``variable_mod10 =
15.9949 M 0 3 -1 0 0 0.0`` active it died with signal 11 in post-analysis on
every run -- three on the full proteome and each replay on the 1000-record
subset (``ascore-crash``, exit 139) -- where the same modification in
``variable_mod02`` completed. A warning would let a run start that the
binary cannot finish. The other new rule, ``residue_not_in_release``,
reports at the field what the codec already refused for 2026.02.2 on read and
write. ``index_search_type.ignored_without_idx`` is off for 2026.02.2, whose
binary says nothing, and ``distance_undocumented`` keeps its warning there.
``terminus_undocumented`` and ``enzyme_in_table.missing`` were already errors
for both releases: 2026.03.0's binary now agrees with each.

Nothing to validate: ``add_U_selenocysteine`` and the library MS level
-----------------------------------------------------------------------

Cases ``selenocysteine`` and ``speclib-level-2`` run clean in both releases,
and the validator is clean: neither binary refuses or warns about a value.
What each release does with them (2026.02.2 ignores both; 2026.03.0 applies the
U mass, and reads the level but uses it only for a ``.raw`` library it refuses)
is in their release-specific help (:ref:`dev-comet-parameter-202603`). A value
2026.02.2 ignores is a silent no-op the help states; no finding was added.

.. _dev-comet-parameter-presets:

Presets
=======

Package ``org.cometgui.params.comet.presets``. A preset is a **versioned
configuration delta, not a replacement file** (*Presets* in the
specification): a ``Preset`` names the Comet version and the metadata
``schemaVersion`` it was made against and lists only the parameters it sets,
each a ``PresetDelta`` -- the parameter and its value text in the
parameter's own ``comet.params`` syntax, exactly as it would follow ``name =``.
A preset changes nothing by itself.

The format
----------

Built-in presets are the JSON resource
``cometgui-params-comet/src/main/resources/org/cometgui/params/comet/schema/comet-presets.json``,
beside the metadata, read by ``PresetLoader`` with the project's one JSON
reader. A user's presets have the same form (``PresetJson.write``); keeping
them in a project file is a later phase's. Every field is always present,
``null`` where absent::

    {
      "presetFormat": 1,
      "description": "...",
      "presets": [
        {
          "id": "low-low",                       // [a-z0-9][a-z0-9-]*, unique
          "displayName": "...", "description": "...",
          "origin": "BUILT_IN",                  // or "USER"
          "cometVersion": "2026.02.2",           // a curated version, required
          "schemaVersion": 1,                    // the metadata's
          "source": "https://...",               // built-in: required; user: null
          "deltas": [ { "parameter": "fragment_bin_tol",
                        "value": "1.0005",
                        "citation": "https://... line 47: ..." } ]  // built-in: required
        }
      ]
    }

``PresetLoader`` refuses, naming the preset and the field: a missing, blank,
unreadable or uncurated ``cometVersion``; another ``schemaVersion``; a
parameter the metadata does not model at all, or does not model for the
preset's version; a value the parameter's codec cannot read for that version
(so a preset can never hold a value the model could not); an id that is not
one or is used twice; no deltas, or a parameter set twice; a built-in without
its ``source`` or a citation; a ``source`` or citation with no ``https://``
reference; and any field missing or unknown. ``PresetJson.write`` reads its
own output back and refuses to return a document that does not load as the
same presets -- the JSON writer passes every string through the project's
secret rules, so a value that looked like a credential would otherwise be
saved changed.

``Preset.fromModel(id, name, description, model, parameters)`` makes a user's
preset from a parameter set: the named parameters' current text, the set's
Comet version and schema, deltas in schema order.

.. _dev-comet-parameter-builtin-presets:

The built-in presets
--------------------

Comet's own documentation for the 2026.02 release publishes an example
parameter file for each of the conventional instrument-resolution patterns
[CPLL]_ [CPHL]_ [CPHH]_. The three built-in presets are those files' values,
and only for the parameters on which the three files differ from one another
-- eight, the same eight in each preset, so that applying any one of them
sets every instrument-resolution parameter:

.. list-table::
   :header-rows: 1
   :widths: 34 22 22 22

   * - Parameter
     - ``low-low``
     - ``high-low``
     - ``high-high``
   * - ``peptide_mass_tolerance_upper``
     - ``3.0``
     - ``20.0``
     - ``20.0``
   * - ``peptide_mass_tolerance_lower``
     - ``-3.0``
     - ``-20.0``
     - ``-20.0``
   * - ``peptide_mass_units``
     - ``0`` (amu)
     - ``2`` (ppm)
     - ``2`` (ppm)
   * - ``precursor_tolerance_type``
     - ``0`` (MH+)
     - ``1`` (precursor m/z)
     - ``1`` (precursor m/z)
   * - ``isotope_error``
     - ``0`` (off)
     - ``2`` (0, +1, +2)
     - ``2`` (0, +1, +2)
   * - ``fragment_bin_tol``
     - ``1.0005``
     - ``1.0005``
     - ``0.02``
   * - ``fragment_bin_offset``
     - ``0.4``
     - ``0.4``
     - ``0.0``
   * - ``theoretical_fragment_ions``
     - ``1``
     - ``1``
     - ``0``

``high-high`` is also exactly ``comet -q``'s defaults for 2026.02.2. Each delta
cites its example file, the line that declares it, and the file's SHA-256 as
fetched on 2026-10-02, plus the parameter's own page; the fragment values also
cite the pages' own recommendations -- "For ion trap data with a
fragment_bin_tol of 1.0005, it is recommended to set fragment_bin_offset to
0.4. For high-res MS/MS data, one might use a fragment_bin_tol of 0.02 and a
corresponding fragment_bin_offset of 0.0" [FBO]_; "For extremely coarse
fragment_bin_tol values such as the historical ~1 Da bins, a
theoretical_fragment_ions value of 1 is optimal ... ~0.02 for high-res MS/MS
spectra, a value of 0 is optimal" [TFI]_ -- and ``comet -q``'s own comment
block (lines 74-75 of the 2026.02.2 fixture: "ion trap ms/ms: 1.0005
tolerance, 0.4 offset (mono masses), theoretical_fragment_ions = 1" and "high
res ms/ms: 0.02 tolerance, 0.0 offset (mono masses), theoretical_fragment_ions
= 0, spectrum_batch_size = 15000"). ``spectrum_batch_size`` is not in any
preset: the three example files all keep ``15000``.

**The citations are checked, not trusted.** The three example files are
checked in unmodified under
``src/test/resources/fixtures/comet-presets/parameters_202602/`` with a
``SHA256SUMS`` (they are Comet's documentation, Apache-2.0, like the ``-q``
output). ``BuiltInPresetCitationTest`` requires every citation to name its
file, line, value and that file's SHA-256, the cited line to declare exactly
the preset's value, and each preset to set exactly the parameters on which
the three files differ.

Two findings from reading upstream rather than copying it:

* The **2024.01** page set's ``comet.params.low-low`` holds the high-resolution
  fragment settings (``0.02``, ``0.0``, ``0``), contradicting its own name,
  its ``-q`` comment and the ``fragment_bin_offset`` page; the 2026.02 file
  corrects it. The presets cite 2026.02.
* The 2026.02 example files are marked ``# comet_version 2026.02 rev. 0``;
  every value they give for the eight parameters reads under 2026.02.2's
  codecs, which the loader proves on every load.

**No project presets.** The specification asks for "a minimal set of clearly
named project presets". None is shipped: every candidate considered either
duplicates what the workflow already enforces (``R-CMT-01``'s outputs, the
decoy rules) or would set scientific values Comet's documentation does not
give. Fewer is better than invented; this is a decision for review, not an
omission.

.. _dev-comet-parameter-modification-presets:

Common-modification presets
---------------------------

Phase 07, unit 4. The variable-modification editor offers common
modifications in one step (``R-PARAM-09``, "common modification presets").
Their masses are scientific data, so they are not in the editor: they are
``comet-modification-presets.json`` beside the metadata, read by
``ModificationPresets`` with the preset file's rules (every field required,
no other allowed)::

    {"modificationPresetFormat": 1, "description": "...",
     "presets": [{"id": "oxidation-m", "name": "Oxidation", "description": "...",
                  "cometVersion": "2026.02.2",              // whose tuple syntax "tuple" is in
                  "tuple": "15.994915 M 0 3 -1 0 0 0.0",
                  "massSource": "https://www.unimod.org/...editid1=35 -- ... 15.994915 ...",
                  "formSource": "https://uwpr.github.io/Comet/..."}]}

A preset is refused, naming it and the field, when its id is not one or is
used twice, its ``cometVersion`` is not curated, its tuple does not read under
that release's codec, its mass difference is 0, a source cites no ``https://``
reference, or the mass source does not quote the mass exactly as the tuple
writes it. ``offeredIn(slots)`` is the presets a release's slots can hold
(``VariableModSlots.unwritable``), so the one written with ``^`` is offered
for 2026.03.0 and not for 2026.02.2 -- by the alphabet, not by a version test.

.. list-table::
   :header-rows: 1
   :widths: 24 30 46

   * - Preset
     - Tuple
     - Mass [UMx]_, form
   * - Oxidation (M)
     - ``15.994915 M 0 3 -1 0 0 0.0``
     - Unimod 35, 15.994915; the fields of ``-q``'s own ``variable_mod01``
       and the page's first example [VM]_
   * - Phospho (STY)
     - ``79.966331 STY 0 3 -1 0 0 0.0``
     - Unimod 21, 79.966331; the page's phospho example without its neutral
       loss
   * - Acetyl (protein N-term)
     - ``42.010565 n 0 1 0 0 0 0.0``
     - Unimod 1, 42.010565; ``n`` at distance 0 from the protein N-terminus,
       the page's "oxidation of protein N-terminus" form; both releases
   * - Acetyl (protein N-term, ``^``)
     - ``42.010565 ^ 0 1 -1 0 0 0.0``
     - Unimod 1; the 2026.03.0 ``-q`` comment's own example [V26M]_;
       2026.03.0 only
   * - Deamidation (NQ)
     - ``0.984016 NQ 0 3 -1 0 0 0.0``
     - Unimod 7, 0.984016
   * - Gln->pyro-Glu (N-term Q)
     - ``-17.026549 Q 0 1 0 2 0 0.0``
     - Unimod 28, -17.026549; the page's own pyroglutamate example

The Unimod records were fetched on 2026-10-05 and each file entry quotes the
record's monoisotopic delta and site. ``ModificationPresetsTest`` types the
six tuples, summaries and Unimod record numbers by hand, the per-release
offer, and one CONSTRUCTED breach per rule.

.. [UMx] https://www.unimod.org/modifications_view.php?editid1=35 (Oxidation),
   ``editid1=21`` (Phospho), ``editid1=1`` (Acetyl), ``editid1=7``
   (Deamidated), ``editid1=28`` (Gln->pyro-Glu); fetched 2026-10-05.

.. _dev-comet-parameter-diffs:

Diffs and applying a preset
---------------------------

A diff is a list of ``DiffRow``: *Parameter, Current, Preset* -- a kind
(``PARAMETER``, ``ENZYME_ROW``, ``UNKNOWN_PARAMETER``), a key, and the two
sides' text, either side empty where it does not have the thing. A row exists
only for a difference; texts are compared as the canonical writer writes them,
and an origin alone is never a difference.

``PresetDiff.of(model, preset)`` (``AC-PAR-08``, headless half):

#. runs the **compatibility check** of the preset against the model's Comet
   version -- one entry per delta, see below;
#. makes one ``PARAMETER`` row per delta the version can take whose text
   differs from the model's, **in schema order**. A delta whose value the
   model already has makes no row: ``high-high`` against the real ``-q``
   defaults has none, ``low-low`` has exactly the eight in the table above.

Nothing changes: the model is immutable. ``applyAll()`` or
``applySelected(names)`` return an ``AppliedPreset`` -- a **new** model in which
exactly the applied rows hold the preset's text with origin ``PRESET``, every
other parameter keeping its value and origin; the applied rows; the
**validation** of the new model (``CometValidator.standard()``), so a preset
that makes a configuration invalid is reported, not hidden; and the
compatibility check again. Selecting a parameter that is not a row of the
diff is refused, naming it and the rows; selecting none changes nothing.

``ParameterDiff.between(current, other)`` is the diff between two parameter
sets of **one** version -- Expert mode's "versus the selected preset or
defaults" and "versus the last saved": every modelled parameter that differs,
in schema order; then every enzyme-table row that differs or exists on one
side only, by number; then every unknown parameter that differs or exists on
one side only. Two sets of different versions are refused: that comparison is
a migration.

The compatibility check
-----------------------

A user's preset records the Comet version it was made for; applying it to a
set of another version runs ``VersionConversion`` (shared with migration,
below) on every delta, and ``CompatibilityReport`` holds **one entry per
delta**, in the preset's order, so nothing can go missing between a preset
and its diff:

``SAME``
    The target takes the value as the same text.
``CONVERTED``
    The value's syntax changed between the versions; it is rewritten for the
    target with the same meaning, reported in ``converted()``, and the row
    shows the target's text.
``NOT_IN_TARGET``
    The target version has no such parameter.
``NOT_CONVERTIBLE``
    The target has the parameter but cannot hold the value -- two neutral
    losses where the target's tuple takes one.

The last two are ``problems()``: never a row, never applied, and carried
into the ``AppliedPreset`` so the editor can show them. A user's preset made
on 2026.02.2 with ``pinfile_protein_delimiter``, a two-loss
``variable_mod02`` and ``num_threads``, applied to a real 2024.01.0 set,
reports the first two and diffs only ``num_threads`` (``CompatibilityTest``).
The built-in presets apply to a 2024.01.0 set with every delta ``SAME``.

.. _dev-comet-parameter-migration:

Schema migration
================

Package ``org.cometgui.params.comet.migration``.
``SchemaMigration.migrate(model, targetVersion)`` moves a parameter set from
its Comet version's schema to another's and returns a ``MigrationResult``: the
source model (unchanged), the new model, and a ``MigrationReport`` with **one
entry per parameter** of either version and per unknown parameter.
``SchemaMigration.migrateFile(metadata, text, target)`` reads a file as the
version its own ``# comet_version`` marker names and migrates that; a file
with no marker, an uncurated or unreadable one, or one that does not parse as
that version is refused with a ``MigrationException`` saying which.

**Migration is explicit** (``R-TOOL-09``): nothing in the parser, the writer
or the model calls it; a file of one version imported for another is read as
the selected version with ``R-PARAM-06``'s mismatch warning, never silently
migrated. The rules, per parameter:

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Outcome
     - When, and what the new model holds
   * - ``CARRIED``
     - Modelled in both versions and the target writes the same text: carried
       with its origin.
   * - ``RESHAPED``
     - Modelled in both; the value written in the target's syntax with the
       same meaning -- a variable-modification tuple re-laid-out for the
       target's field layout -- with its origin.
   * - ``CONVERTED``
     - Modelled in both; the value written as another that the target
       release gives the same meaning, as the target's version record states
       (:ref:`dev-comet-parameter-value-migrations`), with its origin. The
       explanation carries the record's reason and source.
   * - ``NOTED``
     - Modelled in both; carried with the same text and meaning, but the
       target's version record notes how that release treats it differently
       -- a warning the source release never gave. The explanation carries
       the notice and its source.
   * - ``ADDED``
     - New in the target: the target version's default, origin
       ``COMET_DEFAULT``.
   * - ``REMOVED_KEPT_AS_UNKNOWN``
     - Not a parameter of the target: kept as an unknown parameter with its
       value text and curated inline comment -- never dropped
       (``R-PARAM-07``) -- and therefore blocked by validation
       (``version.parameter_unavailable``) until the user removes it. Its
       ``line`` is ``SchemaMigration.NOT_FROM_A_FILE`` (0): it was declared
       on no line of any file.
   * - ``UNKNOWN_CARRIED``
     - An unknown parameter of the source the target does not model either:
       kept exactly as imported.
   * - ``UNKNOWN_ADOPTED``
     - An unknown parameter of the source the target models: read as the
       target's parameter, origin ``IMPORTED``.
   * - ``NEEDS_ATTENTION``
     - The target has the parameter but cannot hold the value with its
       meaning (two neutral losses into 2024.01.0's one-loss tuple; an
       adopted unknown whose text is not of the target's kind; a value the
       target's version record says has no equivalent there). The new model
       holds the target's default; the source value is in the report. Nothing
       is guessed.

Every outcome but ``CARRIED`` and ``UNKNOWN_CARRIED`` is a change
(``MigrationReport.changes()``), and ``MigrationReport.describe()`` writes a
headline and one line per change -- outcome, parameter, explanation -- which
is the reviewable diff the specification asks for (*Comet*: 2026.02.2 files
"import, and migrate to the default with a reviewable diff").

The enzyme table is the file's content, not the schema's, and is carried
unchanged (Comet 2024.01.0's own default table differs from 2026.02.2's in
rows 6, 8 and 9; a migrated file keeps whatever table it had). The new model
carries no parse diagnostics; the source keeps its own.

``VersionConversion`` is the one value rule migration and the preset
compatibility check share: read the text under the **source** version's codec,
write the typed value under the **target**'s. For every kind but the tuple the
two codecs are the same; a tuple's field layout is the version's, and the
target codec refuses a value it cannot hold -- a non-default value in a field
its tuple lacks, or a pair where it takes one value -- which is reported, not
dropped.

The two real Comet versions differ by nine parameters, all new in 2026.02.2,
and in no tuple field: their layouts have the same eight fields in the same
order and differ only in whether the neutral loss takes a pair. So with real
data every shared parameter is ``CARRIED``. ``RESHAPED`` and the
missing-field case are proved on a CONSTRUCTED third version (``2099.01.0``,
test input built on the bundled metadata in ``ConstructedVersions``) with a
reordered and a seven-field layout.

.. _dev-comet-parameter-value-migrations:

Value migrations: what a release does with another's values
-----------------------------------------------------------

Comet 2026.03.0 intake, unit 5. ``VersionConversion`` carries a value by its
typed meaning, which is right whenever the two releases read the same text
the same way. Where they do not -- the same text treated differently, or
different text with the same effect -- the **target** release's version
record says so, keyed by the release the value was written for (decision C-2:
data in the version record, never an ``if (version ...)``)::

    "valueMigrations": [
      { "from": "2026.02.2", "parameter": "index_search_type", "value": "1",
        "action": "CONVERT", "becomes": "-1",
        "reason": "Comet 2026.02.2 reads index_search_type only to ...",
        "source": "https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1524" },
      { "from": "2026.02.2", "rule": "variable_mod_tuple.distance_undocumented",
        "action": "CONVERT", "field": "TERMINAL_DISTANCE", "becomes": "-1",
        "reason": "...", "source": "https://..." },
      { "from": "2026.02.2", "rule": "variable_mod_tuple.terminus_undocumented",
        "action": "NEEDS_ATTENTION", "reason": "...", "source": "https://..." }
    ]

An entry is matched one of two ways:

* by ``parameter`` and ``value``: one choice of an enumerated parameter, as
  the source release writes it;
* by ``rule``: a validation rule's stable identifier whose finding the
  **source** model's own validation has on the parameter. The tuple conditions
  Comet 2026.03.0 refuses are exactly what two existing rules recognise --
  rules unit 4 agreed with both real binaries -- so the entry names the rule
  rather than restating its condition, and the condition stays in one place.

``action`` is one of:

``CONVERT``
    Write the value as ``becomes``, which means the same in the target:
    the whole value for an entry matched by value, or the text of one
    ``field`` of a variable-modification tuple for an entry matched by rule
    (that field replaced at its position in the source release's layout,
    then the tuple written for the target). Status ``CONVERTED``; migration
    outcome ``CONVERTED``.
``NEEDS_ATTENTION``
    The target has no equivalent. Status ``NOT_CONVERTIBLE``; migration
    outcome ``NEEDS_ATTENTION``, the target's default in the new model, the
    source value in the report.
``NOTICE``
    Carry the value unchanged -- it means the same -- and report how the
    target treats it. Status ``SAME`` with the notice in the explanation;
    migration outcome ``NOTED``.

Every entry carries ``reason`` (the words the user reads) and ``source`` (an
``https://`` reference to the release behaviour); each result names the
entries that decided it (``VersionConversion.Result.applied()``) and appends
each reason with its source to its explanation, so nothing an entry does is
silent. All matching entries apply: if any has no equivalent the value needs
attention; otherwise each conversion applies in the metadata's order, and
each notice is reported. Java holds an entry as ``ValueMigration``;
``CometVersionRecord.valueMigrations()`` lists them and
``valueMigrationsFrom(release)`` selects one source release's.

**Where they apply.** ``VersionConversion.convert(name, text)`` -- the preset
compatibility check -- applies the entries matched by value: a user's
2026.02.2 preset setting ``index_search_type = 1`` is ``CONVERTED`` to ``-1``
on a 2026.03.0 set. Entries matched by rule need the source model's findings,
so only ``SchemaMigration`` applies them: it validates the source model
(``CometValidator.standard()``), passes each parameter's rule identifiers to
``convert(name, text, rules)``, and refuses, with an
``IllegalStateException`` naming the record and the identifier, a target
record whose entries name a rule that does not exist.

**What ``MetadataLoader`` refuses**, naming ``versions[i] value migration k``
and the field: a missing ``valueMigrations`` member (it is required, as
``ruleSeverities`` is, so a release added later states its migrations, if
only as ``[]``) or one that is not an array of objects; a field other than
``from``, ``action``, ``reason``, ``source``, ``parameter``, ``value``,
``rule``, ``field`` and ``becomes``; a ``from`` that is not a curated release
or is the record's own; an ``action`` that is not one of the three; a blank
``reason``; a ``source`` that is not ``https://``; an entry matched both ways
or neither; ``becomes`` missing for ``CONVERT`` or given for anything else.
Matched by value: a ``field``; a parameter that is not modelled, not modelled
for both releases, or not enumerated; a ``value`` that is not one of the
source release's own choices (its override applied); a ``becomes`` that is
not one of the target's, or that repeats the value; a ``NOTICE`` value the
target does not offer. Matched by rule: a ``value``; an identifier that is
not one; for ``CONVERT``, a ``field`` that is not a ``VariableModField`` or
that either release's tuple layout lacks, and a ``becomes`` that is not one
value of the field's kind; a ``field`` on any other action. And any value or
rule matched twice for one source release. ``ValueMigrationsLoaderTest``
proves each on CONSTRUCTED metadata; ``ValueMigrationConversionTest`` proves
the conversions value by value, including a CONSTRUCTED target that combines
a conversion, a notice and a re-laid-out tuple.

The documentation generator does not render ``valueMigrations``: the
generated reference describes parameters, and what migration did to a
particular file is in that migration's report. The generator ignores the
member, and refuses nothing about it.

What the real migration proves
------------------------------

``RealMigrationTest``, both directions, with the expected parameters derived
from the two real dumps **and** typed by hand (each checking the other):

* **2024.01.0 to 2026.02.2** (via ``migrateFile`` on the real ``-q`` file):
  ``ADDED`` is exactly ``index_search_type``, ``compoundmods_file``,
  ``spectral_library_name``, ``spectral_library_ms_level``,
  ``protein_modslist_file``, ``print_ascorepro_score``,
  ``pinfile_protein_delimiter``, ``min_precursor_charge`` and
  ``percentage_base_peak``, each at 2026.02.2's default with origin
  ``COMET_DEFAULT``; nothing is removed; the 109 others are ``CARRIED`` with
  their text and origin (so ``fragindex_num_spectrumpeaks`` stays the file's
  ``100``); every tuple is the same typed value, and a CONSTRUCTED old-form
  edit (``79.966331 STY 0 2,4 -1 0 -1 97.976896``) is carried into the new
  layout. The result writes, re-parses with no diagnostic to the same values,
  and validates with exactly ``workflow_enforced.output_off`` (2024.01.0's
  ``-q`` also has ``output_percolatorfile = 0``) -- and with nothing once the
  workflow's outputs are enforced.
* **2026.02.2 to 2024.01.0**: the nine are ``REMOVED_KEPT_AS_UNKNOWN``, kept
  with their text, blocked by validation, written in the unknown-parameter
  section and read back by the 2024.01.0 parser; a two-loss tuple is
  ``NEEDS_ATTENTION``.

.. _dev-comet-parameter-migration-202603:

Migration to Comet 2026.03.0
----------------------------

Comet 2026.03.0 intake, unit 5. 2026.03.0 declares the same 118 parameters as
2026.02.2, so a 2026.02.2 file loses and gains nothing; what changes is how
2026.03.0 treats three values 2026.02.2 accepted. Each was established from
the source at both tags and by running the real binaries on 2026-10-04 --
2026.03.0, 2026.02.2 and 2024.01.0 (the pinned mirror binaries, SHA-256
``ad93b4cf...91e7ed``, ``af515b6e...6d9e`` and ``2834f928...2379``) -- in a
private scratch directory. Each run is that release's own ``-q`` fixture with
``database_name`` the 1000-record proteome subset, ``spectral_library_name``
empty, ``num_threads = 8``, ``output_txtfile = 1``, ``scan_range`` as stated,
and the case's edits (CONSTRUCTED input), searched as ``comet -P<case>
-N<out> <K562_3.mzML, LF copy>`` (the corpus inputs,
:ref:`dev-comet-parameter-validation-corpus`). Results are compared by the
SHA-256 of the ``.txt`` output without its first line (which names the run).

``index_search_type``
~~~~~~~~~~~~~~~~~~~~~

Every 2026.02.2 file Comet wrote holds ``index_search_type = 1``. Five scans
(11188-11192), each value in each situation; for an ``.idx`` the index is
built by the same release, and "built" is its ``IndexSearchType:`` header
line:

.. list-table::
   :header-rows: 1
   :widths: 22 39 39

   * - Situation
     - Comet 2026.02.2: ``-1``, ``0``, ``1``
     - Comet 2026.03.0: ``-1``, ``0``, ``1``
   * - FASTA database
     - all three silent, identical results (26 lines)
     - all three identical results (26 lines); ``-1`` silent; ``0`` and ``1``
       each warn ``Warning - index_search_type = N is ignored: "<db>" is not
       an .idx file (plain FASTA search). ...``
   * - ``database_name`` an ``.idx`` that does not exist
     - ``-1`` and ``1`` build a **fragment ion index**, ``0`` a **peptide
       index**; all silent
     - the same, all silent
   * - an existing fragment-ion index
     - the header decides; all three silent, identical results
     - the header decides, identical results; ``0`` warns ``... is a
       fragment ion index and its own IndexSearchType: header line decides
       ...``; ``-1`` and ``1`` silent
   * - an existing peptide index
     - the header decides; all three silent, identical results
     - the header decides, identical results; ``1`` warns ``... is a peptide
       index ...``; ``-1`` and ``0`` silent

The source says why: 2026.02.2 reads the value only where a named ``.idx``
does not exist yet, and tests ``== 0`` there (``CometSearchManager.cpp``
L1524 at ``v2026.02.2``: ``(iIndexSearchType == 0) ? PI_DB : FI_DB``), so
every value but 0 is a fragment-ion index; 2026.03.0 builds the same way, and
adds warnings for a value other than ``-1`` that it ignores (L1742-L1775 at
``v2026.03.0``) or coerces (L811-L822). So:

* **2026.02.2's** ``1`` **is written** ``-1`` **(** ``CONVERTED`` **).** In every
  situation above, 2026.02.2's ``1`` and 2026.03.0's ``-1`` do the same, and
  ``-1`` never warns; carried as ``1`` it would draw the "is ignored" warning
  on every FASTA search, about a value the user never chose (Comet's own
  ``-q`` wrote it). The conversion is in the report with its reason, never
  silent. Flagging it instead was rejected: the value has an exact
  equivalent, and a flag that holds the default would ask every user the
  same question with one right answer.
* **2026.02.2's** ``0`` **is carried with a notice (** ``NOTED`` **).** It means
  the same in both releases -- a peptide index for a named ``.idx`` that does
  not exist, nothing for a FASTA search -- and no other 2026.03.0 value means
  that. 2026.03.0 warns about it on a FASTA search and on an existing
  fragment-ion index; the report says so, and validation of the migrated set
  reports ``index_search_type.ignored_without_idx`` as a warning where the
  database is not an ``.idx``.
* **2024.01.0** has no ``index_search_type``: it is ``ADDED`` at 2026.03.0's
  default ``-1``.
* **Back to 2026.02.2**, 2026.03.0's ``-1`` is written ``1`` (2026.02.2's
  record): 2026.02.2 does not document ``-1`` (its validator refuses the
  choice), and ``1`` does exactly the same there. A 2026.02.2 file migrated to
  2026.03.0 and back is the same model.

Position fields 2026.03.0 refuses
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

2026.03.0 stops a search (``Error - variable_modNN (...): invalid
term_distance/which_term ...``) for an **active** slot with a terminal
distance below -2, or a distance of 0 or more with a terminus outside 0-3
(``CometSearchManager.cpp`` L1436-L1450); an unused slot (mass 0) is reset
before the check. Scans 11000-12500, ``variable_mod01`` changed:

.. list-table::
   :header-rows: 1
   :widths: 30 23 23 24

   * - ``variable_mod01``
     - Comet 2026.02.2
     - Comet 2024.01.0
     - Comet 2026.03.0
   * - ``15.9949 M 0 3 -1 0 0 0.0``
     - exit 0, 6985 lines (``878261f3aa3a70b5``)
     - exit 0, 6985 lines (``343bcf31720b5e6e``)
     - exit 0, 6985 lines
   * - the same with distance ``-3``, and ``-7``
     - exit 0, identical to ``-1``
     - exit 0, identical to ``-1``
     - exit 1, the error
   * - ``15.9949 M 0 3 2 4 0 0.0`` (terminus 4, distance 2)
     - exit 0, 6811 lines, identical to the slot unused; no modified peptide
     - exit 0, 6811 lines, no modified peptide (with every slot unused,
       2024.01.0 itself stops: ``Error in StorePeptides. stored twice``,
       exit 1, so the comparison is by the absence of modified peptides)
     - exit 1, the error
   * - ``15.9949 M 0 3 0 -1 0 0.0`` (terminus -1, distance 0)
     - exit 0, identical to the slot unused
     - exit 0, identical to terminus 4
     - exit 1, the error
   * - ``15.9949 M 0 3 -1 4 0 0.0`` (terminus 4, no distance)
     - exit 0, identical to ``-1 0``
     - exit 0, identical to ``-1 0``
     - exit 0, identical to ``-1 0``

Both older releases test ``iVarModTermDistance < 0`` for "no constraint"
(``CometSearch.cpp`` L5371-L5372 at ``v2026.02.2``, L4757-L4758 at
``v2024.01.0``) and compare the terminus with 0 to 3 and nothing else
(L5373-L5390, L4759-L4776). So:

* **A distance below -2 on an active slot is written -1 (** ``CONVERTED`` **)**,
  from either older release, matched by the rule
  ``variable_mod_tuple.distance_undocumented``. **One difference no 2026.03.0
  value can keep**, found in the source and confirmed by running: both older
  releases switch on their "one exclusive modification per peptide" check
  only when an active slot has a distance of exactly ``-1``
  (``bRareVarModPresent``, ``CometSearchManager.cpp`` L1318-L1322 at
  ``v2026.02.2``, L1399-L1403 at ``v2024.01.0``; its own comment says it means
  ``iRequireThisMod == -1``). With two exclusive slots (required ``-1``) at
  distance ``-3`` and no slot at ``-1``, 2026.02.2 gave 7265 result lines
  where the same slots at ``-1`` gave 7263 (2024.01.0 likewise); adding a
  third active slot at ``-1`` made ``-3`` and ``-1`` identical again.
  2026.03.0 switches the check on for any active slot, whatever the value
  (L1476-L1481 at ``v2026.03.0``). The reason in the record says so, so the
  report does.
* **A terminus outside 0-3 with a distance of 0 or more needs attention (**
  ``NEEDS_ATTENTION`` **)**, from either older release, matched by
  ``variable_mod_tuple.terminus_undocumented``: the modification was never
  applied, there is nothing in 2026.03.0 that means what the user wrote, and
  switching the slot off silently would hide that the file asked for
  something. The migrated set holds the slot's default, as for every
  ``NEEDS_ATTENTION``; the source value and the reason are in the report.
* An **unused** slot with such fields, and a terminus outside 0-3 with a
  negative distance, are ``CARRIED``: 2026.03.0 accepts both.

**AScorePro with an active slot 10-15** is an error for both releases in this
project (:ref:`dev-comet-parameter-validation-corpus`), so it is not a
migration question: there is nothing to convert to. It is carried, and the
migrated set's validation reports ``variable_mods.ascorepro_slot_unsupported``
as an error (``MigrationTo202603Test.Edits.ascoreProIsNotHidden``).

The real migrations
~~~~~~~~~~~~~~~~~~~

``MigrationTo202603Test`` migrates the four real fixtures with
``migrateFile``; the expected outcomes are derived from the real ``-q`` dumps
-- the names one release declares and the other does not, and the parameters
both declare whose own ``-q`` values differ -- **and** typed by hand, each
checking the other:

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Source
     - Report (118 entries each)
   * - 2026.02.2 ``-q``, 2026.02.2 ``-p``
     - ``CONVERTED`` 1 (``index_search_type``), ``CARRIED`` 117; nothing
       added, removed, reshaped, noted or needing attention
   * - 2024.01.0 ``-q``, 2024.01.0 ``-p``
     - ``ADDED`` 9 (the nine parameters 2024.01.0 lacks, each at 2026.03.0's
       default, ``index_search_type`` at ``-1``), ``CARRIED`` 109

The one change of a 2026.02.2 file, as ``MigrationReport.describe()`` writes
it::

    Comet 2026.02.2 -> 2026.03.0: 118 parameters, 1 changes, 0 needing attention
    CONVERTED index_search_type: index_search_type = 1 (Comet 2026.02.2) is written -1 for
    Comet 2026.03.0, which means the same there: Comet 2026.02.2 reads index_search_type only
    to choose the index it builds for a named .idx file that does not exist yet, and builds a
    fragment-ion index for every value but 0, so 1 (what its comet -q writes) is its ordinary
    setting. Comet 2026.03.0's -1 (not set, what its comet -q writes) does exactly the same and
    never warns; 1 there draws a warning on every FASTA search that the value is ignored.
    (https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1524)

(one line in the report; wrapped here). The migrated 2026.02.2 ``-q`` set holds
**exactly** 2026.03.0's own ``-q`` values, and its canonical file is byte for
byte the canonical file of 2026.03.0's own ``-q``. Each migrated set writes,
re-parses as 2026.03.0 with no diagnostic to the same values, and validates
with exactly one finding, ``workflow_enforced.output_off`` on
``output_percolatorfile`` (Comet's own files switch the PIN off) -- and with
none once the workflow's outputs are enforced: in particular no
``index_search_type`` warning, which the unmigrated value would draw. The
CONSTRUCTED edits -- ``index_search_type = 0``, a distance of ``-3`` and a
terminus of 4 in an active slot from either older release, the same in an
unused slot, AScorePro with slot 10 -- each give the outcome above, with
the record's reason in the explanation; migrating 2026.03.0 to itself changes
nothing, and 2024.01.0 to 2026.02.2 carries ``-3`` (2026.02.2's record states
no such entry): the entries are keyed by the source release.

Accepted by the real 2026.03.0 binary
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The four migrated canonical files are checked in, CometGUI's own output, at
``cometgui-params-comet/src/test/resources/fixtures/comet-migrated/2026.03.0/``
(``from-2026.02.2-q.params`` and so on, with ``SHA256SUMS``, ``-text`` in
``.gitattributes``). ``MigrationTo202603Test.Written`` requires migration and
the writer to produce exactly those bytes -- and writes what they produce to
``target/`` when they differ, for review -- and that each release's ``-p``
file migrates to the same bytes as its ``-q`` file (``-p`` omits only
parameters at their defaults). ``MigratedFileRealBinaryTest`` (Linux) runs
the pinned 2026.03.0 binary on those bytes through ``ProcessService`` and
**runs no production class of this module**, so PIT never maps a mutant to
its searches (the reason is in :ref:`dev-comet-parameter-validation-corpus`);
the byte-equality test is what ties the bytes it runs to production code.

* **Parameter load**, each file exactly as written: ``comet
  -P<file> missing.mzML`` exits 1 with one line, ``Error - input file
  "missing.mzML" not found.`` -- the same transcript as 2026.03.0's own
  ``-q`` file. Comet reads the whole file before it looks at the input.
* **A five-scan search**, each file with five explicit text edits, made in
  the test and stated there: ``database_name`` the proteome subset,
  ``spectral_library_name`` empty, ``scan_range = 11188 11192``,
  ``num_threads = 4``, ``output_txtfile = 1``. The migrated files hold the
  placeholders ``/some/path/db.fasta`` and ``/some/path/speclib.file``,
  because Comet's own ``-q`` writes them and migration carries what the file
  says (2024.01.0 has no library parameter, and migration adds 2026.03.0's
  default, the same placeholder); a search with either stops before it
  starts, and choosing the database and the library is the workflow's job,
  not migration's. Every file: exit 0, **no Warning or Error line**, and the
  same 26 result lines as 2026.03.0's own ``-q`` file with the same edits.
* **The negative control**: the migrated 2026.02.2 file with the one
  migrated line put back, ``index_search_type = 1``, exits 0 with the same
  results and exactly one line, ``Warning - index_search_type = 1 is ignored:
  "<db>" is not an .idx file (plain FASTA search). It only selects the index
  type to auto-build when database_name names an .idx file that does not
  exist yet.`` -- the warning migration spares every search.

.. _dev-comet-parameter-migration-review:

The review: an entry needing attention blocks a run
---------------------------------------------------

Phase 07, unit 1. A ``NEEDS_ATTENTION`` entry leaves the target's default in
the migrated set, and that default is not always harmless: Comet 2026.03.0's
``variable_mod01`` default is ``15.9949 M 0 3 -1 0 0 0.0``, an **active**
methionine oxidation, so a 2026.02.2 file whose ``variable_mod01`` had a
terminus Comet never applied would, unreviewed, search with a modification the
scientist never chose. ``R-PARAM-13``: "an entry needing the scientist's
attention blocks a run until resolved".

``MigrationReview`` (package ``org.cometgui.params.comet.migration``) holds a
``MigrationResult`` and the names of the ``NEEDS_ATTENTION`` entries the
scientist has acknowledged. It is immutable: ``MigrationReview.of(result)``
starts with none, and ``resolve(name)`` returns a new review. An entry is
**resolved** in exactly two ways:

* **acknowledged** -- ``resolve(name)``: the scientist accepts the value the
  set holds. It is recorded in the review and holds whatever the value later
  is;
* **set by the scientist** -- in the model being validated the parameter's
  origin is ``USER``. Migration gives every such parameter origin
  ``COMET_DEFAULT``, so ``USER`` there is a value entered after the migration.
  This is read from the model at each validation, not recorded: a reset to the
  default, a preset (``PRESET``) or any other origin leaves an unacknowledged
  entry unresolved again. A caller gives origin ``USER`` only to a value the
  scientist set on that parameter.

``resolve`` refuses, with an ``IllegalArgumentException`` naming it, a name
that is not a ``NEEDS_ATTENTION`` entry of the report ("``<name>`` is not an
entry of the migration from Comet ``<from>`` to Comet ``<to>`` that needs
attention, so there is nothing to resolve") and one already acknowledged
("``<name>`` has already been resolved in this migration's review"); the
constructor refuses an acknowledged set holding such a name.

``validate(model)`` is the **one** place a migration's review and validation
meet, and the editor shows its report and never combines two: every finding of
``CometValidator.standard().validate(model)``, in its order, then one
``migration.needs_attention`` error per unresolved entry, in the migration
report's order. Each is attached to the entry's parameter and that
parameter's category -- ``forParameter``, ``forCategory``, ``errors()`` and
``hasErrors()`` see it as any other error -- or, for a name the set does not
model, to the name alone with no category, as unknown names are. A model of
another release than the migration's target is refused. The message names the
parameter, the source release's value, the default migration put in its
place, the value the set holds now, and the entry's explanation; for the case
above::

    variable_mod01 needs your decision: migrating from Comet 2026.02.2 to Comet 2026.03.0
    could not keep its value 15.9949 M 0 3 2 4 0 0.0 and put Comet 2026.03.0's default
    15.9949 M 0 3 -1 0 0 0.0 in its place; the set now holds 15.9949 M 0 3 -1 0 0 0.0. Set a
    value, or accept this one, before running. Why: variable_mod01 = 15.9949 M 0 3 2 4 0 0.0
    (Comet 2026.02.2) has no equivalent in Comet 2026.03.0: With a terminal distance of 0 or
    more, ... or switch the slot off. (https://github.com/UWPR/Comet/blob/v2026.02.2/...);
    the migrated set holds Comet 2026.03.0's default instead, and the source value is kept
    only in this report

(one line; wrapped and shortened here). ``MigrationReviewTest`` proves it on
the real 2026.02.2 ``-q`` file with that one CONSTRUCTED edit, migrated to
2026.03.0: the review has exactly that entry; the set holds the active
default; the report is the validator's ``workflow_enforced.output_off`` error
followed by ``migration.needs_attention`` at ``variable_mod01`` in
``variable_mods``, with the whole message typed by hand; once the outputs are
enforced the entry is the only error; acknowledging it, or a ``USER`` value,
leaves exactly the validator's report, while a reset, a ``PRESET``,
``IMPORTED``, ``COMET_DEFAULT`` or ``WORKFLOW_ENFORCED`` value does not. With
two entries (``variable_mod01`` and ``variable_mod02``) resolving one clears
exactly its finding. The real 2026.02.2 file with a two-loss
``variable_mod02`` migrated to 2024.01.0 gives the validator's ten errors
(``workflow_enforced.output_off`` and nine ``version.parameter_unavailable``)
then the entry's; a migration with nothing needing attention (either older
real ``-q`` file to 2026.03.0) gives exactly the validator's report; and the
refusals are asserted with their messages.

**The variable-modification editor keeps that promise** (unit 4). Editing a
part of a slot, or removing its modification, is the scientist's decision on
that very slot and is written ``USER``. A move swaps two slots' values and
writes both ``USER``, so the editor **refuses a move to or from a slot whose
entry is unresolved** ("``variable_mod01`` needs your decision in the
migration review before a modification can be moved to or from it"), and an
added common modification skips an unused slot whose entry is unresolved:
either would otherwise resolve an entry with a value the scientist never set
on that parameter. ``VariableModsViewModelTest`` (*a migration under review*)
proves both on the real 2026.02.2 starting set migrated to 2026.03.0.

.. _dev-comet-parameter-older-release:

The migration fixture: Comet 2024.01.0
--------------------------------------

A real migration test needs a second Comet version's real output, and the
release matrix holds one. **Comet 2024.01.0** is the oldest release with
``-q`` (its release notes: "comet -q will generate a comet.params.new file
with a more complete list"), and predates the two-neutral-loss form
(2025.01.0), so its tuple layout differs from 2026.02.2's.

It is **not** in ``manifests/tools.json`` -- that is the product's install
matrix, and an older Comet is not offered to users -- and its fixtures sit
beside, not inside, the matrix's::

    cometgui-params-comet/src/test/resources/fixtures/comet-migration/
        .gitattributes                         (* -text, as fixtures/comet/)
        2024.01.0/linux-x86-64/comet-q.params  10 415 bytes
        2024.01.0/linux-x86-64/comet-p.params   8 746 bytes
        2024.01.0/linux-x86-64/SHA256SUMS

so ``FixtureMatrixTest`` neither counts nor requires them
(``MigrationFixtureRealBinaryTest.theOlderReleaseIsNotOffered`` proves the
version is not in the manifest and the matrix check still passes).

Capture record:

.. list-table::
   :widths: 22 78

   * - Release
     - ``v2024.01.0``, tag commit ``f00df0c79e00379dc65830ae8bc6eac2d7d2eb64``
       (``git ls-remote``), published 2024-05-31
   * - Artefact
     - ``https://github.com/UWPR/Comet/releases/download/v2024.01.0/comet.linux.exe``,
       6 889 456 bytes (the size the GitHub release API lists; upstream
       publishes no digest)
   * - SHA-256
     - ``2834f928594ae57a1fdc0f4a5591bfc0e2b5c91e3e9c33a5ab2e9f508a942379``
       -- two independent downloads on 2026-10-02 agreed; pinned in
       ``MigrationFixtures.ROW`` (test sources), the one place it is recorded
       in code
   * - Licence
     - Apache-2.0: ``LICENSE`` at the tag is the plain Apache License 2.0 text
       (10 173 bytes, SHA-256 ``a6cba85b...3ff9``); 2026.02.2's adds a
       copyright line and an embedded MIT section. Nothing in it bears on
       checking in the tool's own output.
   * - Mirror
     - ``scratch/phase06/artefacts/v2024.01.0__comet.linux.exe`` (gitignored)
   * - Captured
     - 2026-10-02, on linux/x86-64, through ``ProcessService``
   * - ``-q``
     - 10 415 bytes, SHA-256 ``005e4b9e2ead140a0fb64bb4e20057f2863959848f9adeb12c01ea7880049cab``;
       first line ``# comet_version 2024.01 rev. 0 (f00df0c)``; 109
       declarations
   * - ``-p``
     - 8 746 bytes, SHA-256 ``3dab8b034925c01eb2935a0400524de4021daea0d82c11580b4a533722cc052c``;
       87 declarations, all also in ``-q``, with the same values

The commands, from the repository root, with the toolchain sourced and the
module's test classes compiled::

    $ mkdir -p scratch/phase06/artefacts
    $ curl -sSL -o scratch/phase06/artefacts/v2024.01.0__comet.linux.exe \
        https://github.com/UWPR/Comet/releases/download/v2024.01.0/comet.linux.exe
    $ sha256sum scratch/phase06/artefacts/v2024.01.0__comet.linux.exe   # must be 2834f928...2379
    $ cp scratch/phase06/artefacts/v2024.01.0__comet.linux.exe _build/p06-u6/bin/comet
    $ chmod 700 _build/p06-u6/bin/comet
    $ java -cp cometgui-params-comet/target/test-classes:cometgui-process/target/cometgui-process-0.1.0-SNAPSHOT.jar:cometgui-domain/target/cometgui-domain-0.1.0-SNAPSHOT.jar \
        _build/p06-u6/Capture.java _build/p06-u6/bin/comet _build/p06-u6/cap1

where ``Capture.java`` is a single-file launcher that calls
``ParameterFileCapture.capture(binary, mode, emptyDirectory)`` -- the unit 1
helper that runs ``[binary, -q]`` or ``[binary, -p]`` through
``ProcessService`` in an empty directory with an empty environment, requires
exit 0, and returns ``comet.params.new`` -- once per mode. The two files were
copied unmodified to ``comet-q.params`` and ``comet-p.params``, and
``SHA256SUMS`` written with ``sha256sum comet-p.params comet-q.params``.

**To refill the mirror**, run the first three commands above.
``MigrationFixtureRealBinaryTest`` (Linux only, like the other real-binary
tests) stages the binary from the mirror, checks its SHA-256 against
``MigrationFixtures.ROW`` before running it, runs ``-q`` and ``-p`` through
``ProcessService``, and requires the fixtures to equal its output byte for
byte. A missing binary **fails** with these instructions, and a binary with
another SHA-256 is refused before it is run; neither skips. The staging is
unit 1's ``UpstreamMirror``, extended to take the mirror directory and what
pins the checksum, not copied.

What the metadata curates about 2024.01.0 -- and only what migration needs --
is its version record, its tuple layout, its two different defaults, and the
version ranges: the 109 parameters it declares claim it (``"from":
"2024.01.0"``), the nine it lacks do not. Help text, choices, bounds and
inline comments are 2026.02.2's and are not re-curated for 2024.01.0: for
example ``-q`` 2024.01.0's own comment on ``fragindex_skipreadprecursors``
("high mass cutoff for fragment ions") is an upstream copy error, and a
2024.01.0 canonical file is written with 2026.02.2's comments.

Citations for presets and migration
-----------------------------------

.. [CPLL] https://uwpr.github.io/Comet/parameters/parameters_202602/comet.params.low-low
   -- fetched 2026-10-02, 9 879 bytes, SHA-256 ``53c2782a...3c5b``.
.. [CPHL] https://uwpr.github.io/Comet/parameters/parameters_202602/comet.params.high-low
   -- fetched 2026-10-02, 9 879 bytes, SHA-256 ``d3addcf2...ed0e0d``.
.. [CPHH] https://uwpr.github.io/Comet/parameters/parameters_202602/comet.params.high-high
   -- fetched 2026-10-02, 9 879 bytes, SHA-256 ``112d8943...02f3db2``.
.. [FBO] https://uwpr.github.io/Comet/parameters/parameters_202602/fragment_bin_offset.html
.. [TFI] https://uwpr.github.io/Comet/parameters/parameters_202602/theoretical_fragment_ions.html
.. [V24T] https://github.com/UWPR/Comet/blob/v2024.01.0/Comet.cpp#L567-L601
   -- the 2024.01.0 tuple reader: ``"%lf %31s %d %511s %d %d %d %lf"``, a
   comma accepted only in the count.
.. [V24D] https://github.com/UWPR/Comet/blob/v2024.01.0/Comet.cpp#L1697-L1700
   -- what 2024.01.0's ``-q`` writes for the fragment-index parameters.

.. _dev-comet-parameter-generated-reference:

Generated reference
===================

:doc:`../reference/comet_parameters_generated` is ``R-DOC-04``'s page: "The
Comet parameter schema shall generate ``reference/comet_parameters_generated.rst``
so that user documentation and GUI metadata cannot silently diverge." It is
produced the way the traceability report and the tool tables are
(:ref:`dev-tool-registry-generated-tables`).

How it is produced
------------------

``scripts/cometparams.py`` (standard library only -- Read the Docs has no JDK,
decision D6-1) reads:

* **the metadata file** above -- the same bytes ``MetadataLoader`` reads; there
  is no second copy;
* **the built-in presets** beside it, for each parameter's preset effects;
* ``manifests/tools.json``, for the Comet versions CometGUI installs. The page
  documents those, and only those, as supported: today ``2026.02.2``, and
  ``2026.03.0`` from the commit that adds its manifest rows. The ``2024.01.0``
  record is curated for migration only and appears in an entry solely as the
  start of its version range, labelled as not installed;
* for each of those versions, **the real ``comet -q`` fixture**
  (:ref:`dev-comet-parameter-fixtures`), checked against its ``SHA256SUMS`` and
  its marker against the version record. It is the only complete list of what
  that Comet declares, and it holds Comet's default ``[COMET_ENZYME_INFO]``
  rows, which the metadata does not curate and which are the allowed values of
  the three enzyme-reference parameters.

It renders, per category in the metadata's order, one entry per parameter: the
Comet name, display name, category, type (the value kind, in words and as the
constant), the default for each installed version (the version record's
override where there is one), the allowed values or range (labelled choices,
bounds, the tuple layout and residue alphabet of that version, or a pointer
to the enzyme table),
the short help with its upstream reference, the serialisation rule and, for
each installed version, the **default line exactly as**
``CanonicalParamsWriter`` **writes it** for that version (``name = value``,
that version's inline comment with its ``#`` at column 40), version
availability,
related parameters as links, preset effects, the editor level, the validator
ids and the search aliases. Where the installed releases say different things
-- an override's choices or help -- *Allowed values* and *Description* are
rendered once per release, named for it (``Allowed values, Comet 2026.03.0``);
where they agree, once. Then the enzyme table and the internal allow-list.
The output is deterministic: no date, and every order comes from the inputs.

``docs/conf.py`` calls ``generate()`` from a ``builder-inited`` handler, after
deleting the previous build's fragment. The fragment is
``docs/_generated/comet-parameters.rsti`` (gitignored), pulled in by the page
with ``.. include::``. The build log carries one count line, for example::

    [cometparams] wrote _generated/comet-parameters.rsti: 118 parameter entries =
    118 modelled parameters, 0 internal, for Comet 2026.02.2; 3 preset(s);
    metadata sha256 <the metadata file's SHA-256>

What refuses a build
--------------------

Each of these fails the documentation build with the generator's own message,
naming the file, the parameter (or section) and the field:

* a release's residue alphabet missing, not an object, with a member other
  than ``characters`` and ``source``, with empty or missing characters, a
  character that is neither ``A``-``Z`` nor a terminal code (``TERMINAL_CODES``,
  which mirrors ``TerminalCode``), a character listed twice, or a source that
  is not ``https://`` -- checked for every version record, installed or not;
* a parameter missing any field of the metadata format -- each feeds a field of
  ``R-DOC-04`` or the default line -- or with a blank name, display name or
  help; a name given twice;
* an unknown category, value kind, serialisation rule or editor level, or a
  tuple field the generator has no words for (a constant the Java enums gain
  must be added to ``KINDS``, ``SERIALIZATIONS``, ``VISIBILITY`` or
  ``TUPLE_FIELDS`` too);
* a choice without a value or a label; an enumerated kind with fewer than two
  choices; a help reference that is not ``https://``; a version range that does
  not start (or end) at a curated version;
* **a parameter an installed Comet's ``comet -q`` declares that the metadata
  neither models nor allow-lists**, or a modelled parameter that output does
  not declare -- this is what makes removing a parameter's entry a build
  failure that names it;
* a related parameter that is not modelled (or is the parameter itself); an
  enzyme-table reference or a preset delta naming no modelled parameter; a
  preset for an uncurated version;
* any override the loader refuses (:ref:`dev-comet-parameter-overrides`): an
  unknown field, an unmodelled parameter or one the release's range does not
  claim, a second override of one parameter, a non-``https`` source or help
  reference, no replaced field, a replaced field equal to the curated one,
  choices on a kind that is not enumerated or without labels, a release left
  with a default outside its choices, a malformed inline comment, blank help
  -- checked for every version record, installed or not;
* an installed Comet version with no version record, a missing fixture, or a
  fixture whose SHA-256 or marker does not match;
* a rendered fragment without exactly one entry per modelled parameter
  (``check_coverage``).

``docs/conf.py`` adds two checks of its own that do not trust the generator: a
generator that returns without writing the fragment fails the build, and the
handler counts the entries (``.. _comet-param-<name>:`` labels) in what was
written against the names it reads from the metadata with its own
``json.load``.

On the Java side, ``GeneratedReferenceTest`` (in this module's tests) runs the
generator through ``ProcessService`` into a temporary directory and checks the
fragment against ``MetadataLoader``'s view: every modelled parameter has
exactly one entry, every default line is byte-for-byte the line the canonical
writer emits for that parameter in the default model, and every default enzyme
row is the writer's own.

The self-test
-------------

``scripts/cometparams_selftest.py``, run by ``scripts/ci/docs-build.sh
--self-test`` (and so by ``scripts/verify-all-gates.sh --only docs``), damages
**copies** -- never the real files -- and requires each damage to be refused
with its own diagnostic: a parameter's entry removed, each required field
removed in turn, an unknown category and kind, a choice without a label (absent
and blank), a related name that is not a parameter, a duplicated name, a
non-``https`` reference, a preset naming no parameter, an installed version
with no record (a constructed ``2099.01.0``: 2026.03.0 has one now), and
thirteen damaged overrides of the 2026.03.0 record -- an unknown field, an
unmodelled parameter, an override for a release its parameter's range does not
claim (``index_search_type`` for 2024.01.0), a second override, a non-``https``
source, an override that replaces nothing, a repeated curated inline comment
and choices, a default outside the release's choices and choices that drop its
default, choices on a decimal, a non-``https`` help reference and blank help;
and eight damaged residue alphabets of the 2026.03.0 record -- the alphabet
missing, an unknown member, empty characters, no characters, a character no
release can mean (``#``), a lower-case letter, a character listed twice and a
non-``https`` source; and seven damaged rule severities -- the list missing, an
unknown field, an unknown level (``FATAL``), a rule identifier that is not one,
a non-``https`` source, a rule stated twice, and a version-scoped rule one
release states and another does not: 55 generator cases in all. Before the
damages, a
**per-release control**
renders the clean copy for a manifest that also names 2026.03.0 and requires
``index_search_type``'s entry to carry ``1`` and ``0``/``1`` for 2026.02.2 and
``-1`` and ``-1``/``0``/``1`` for 2026.03.0, each with its own default line,
and ``variable_mod01``'s entry to state ``A``-``Z``, ``n``, ``c`` as
2026.02.2's residue alphabet and to add ``^`` and ``$`` for 2026.03.0 only:
a generator that ignored the overrides or the alphabets would fail it. Then, through the real hook in a project copy made by
``scripts/traceability/selftest.py``'s ``copy_project``: the clean copy builds
with the count line and one HTML section per parameter; a missing field and a
removed entry each fail the strict build; a generator that writes nothing (with
the previous fragment on disk) fails it; a generator that drops an entry after
its own check fails it on the hook's count; and the restored copy builds clean.

Every sandbox that runs the documentation build carries these inputs:
``scripts/ci/docs-build.sh --self-test`` builds a copy of ``docs/`` whose
``conf.py`` walks up to the real repository; ``scripts/traceability/selftest.py``
copies the metadata directory and each module's ``src/test`` (the fixtures);
``scripts/verify-install-gates.sh`` control G extracts ``git archive HEAD``.

Adding a field
--------------

#. Add it to the metadata format and to ``MetadataLoader`` (which refuses an
   unknown field), and document it under
   :ref:`dev-comet-parameter-metadata-format`.
#. Add it to ``REQUIRED_PARAMETER_FIELDS`` in ``scripts/cometparams.py`` with
   the ``R-DOC-04`` field it feeds, render it in ``render_entry``, and give any
   closed vocabulary its words, so an unknown value is refused rather than
   printed raw.
#. Run ``python3 scripts/cometparams.py --check`` and ``bash
   scripts/ci/docs-build.sh --self-test``; the self-test picks up the new
   required field automatically.

.. _dev-comet-parameter-falsifiability:

Falsifiability
==============

*A gate that has never been seen to fail has not been shown to work*
(``CONTRIBUTING.rst``, *Gate conventions*). ``bash
scripts/verify-param-gates.sh`` proves that each of the phase's nine exit gate
items fails on the defect it exists to catch. It is registered in
``scripts/verify-all-gates.sh`` as ``params`` and follows Phase 05's
``scripts/verify-install-gates.sh``:

* it extracts ``git archive HEAD`` into ``_build/param-gate-sandbox`` and
  damages only that; the working tree is never touched;
* the module's upstream modules are built once from the sandbox and installed
  into a private overlay repository (``_build/param-gate-m2``, every other
  entry a symlink into ``_build/m2repo``), so each control compiles and tests
  ``cometgui-params-comet`` alone, against the sandbox's own dependencies, and
  the shared repository's project jars are checked unchanged at the end;
* each injection's anchor must match exactly once; the damaged file must
  differ from its pristine copy; after the run its compiled form (the class
  and its inner classes, or the resource's copy under ``target/classes``) must
  differ from the clean baseline and everything else must be identical to it;
* each red is graded on the failing assertion's own words, and a final clean
  run over every selector must pass with the compiled module byte-identical to
  the baseline;
* control ``H`` requires the harness to refuse, as a harness error or failure,
  an unchanged file, a missing anchor, a metadata removal that removes
  nothing, an injection that reaches the source but not the bytecode, a green
  run graded as red, a red without its diagnostic, and a PIT report in which a
  graded package has no mutation.

.. list-table:: The controls (each from the record in ``handoffs/PHASE-06-worklog.rst``)
   :header-rows: 1
   :widths: 6 8 40 46

   * - Control
     - Gate item
     - Injected defect
     - Diagnostic required
   * - 1
     - 1
     - The writer skips every parameter whose value is empty.
     - ``textUnchanged``: the canonical text's SHA-256 is no longer
       ``f381afe1...d62b``; the second parse is not the first model.
   * - 2a
     - 2
     - ``scan_range`` removed from the sandbox's shipped metadata JSON.
     - ``UNMODELLED: Comet 2026.02.2 declares scan_range (line 114, ...)``;
       counts ``[118, 117, 0]``.
   * - 2b
     - 2
     - The drift test compares only the first token of a default.
     - ``defaultDiffersInAPartialDump``: ``findings 0 ==> expected: <1>``.
   * - 3a, 3b
     - 3
     - The second neutral loss dropped; a ``min,max`` count written
       ``max,min``.
     - The tuple round trip in all fifteen slots, naming the form, e.g.
       ``min,max count ==> expected: <... 2,4 ...> but was: <... 4,2 ...>``.
   * - 4a, 4b
     - 4
     - The enzyme table's duplicate-number invariant disabled; the writer's
       refusal of an enzyme number absent from the table disabled.
     - A custom enzyme reusing number 3 is accepted (the parser's own check
       stays green); ``refusesAnAbsentNumber``: no ``ParamsWriteException``.
   * - 5a, 5b
     - 5
     - ``Numbers.text``, and separately the model codec's decimal path, made
       locale-sensitive.
     - ``model 0 written under de_DE: the bytes differ first at offset 1076``
       (``20,0``), while the ``Locale.ROOT`` text test stays green.
   * - 6a, 6b
     - 6
     - The writer drops the unknown section; the parser warns about an unknown
       parameter and then drops it.
     - ``expected: <[ms1_mass_range, precursor_NL_ions]> but was: <[]>``.
   * - 7a, 7b, 7c
     - 7
     - The pair routed through the generic ordering rule; an asymmetric window
       made an error; the reversed-pair error unable to fire.
     - The generic rule's own guard (``... is of kind TOLERANCE_PAIR_MEMBER,
       not a two-value range``) in 14 of 15 tests; ``expected: <WARNING> but
       was: <ERROR>``; ``20.0001 / 20`` graded ``PAIR_SAME_SIGNED``.
   * - 8
     - 8
     - None of its own: ``scripts/cometparams_selftest.py`` is invoked in the
       sandbox (:ref:`dev-comet-parameter-generated-reference`).
     - Its OK line with at least 55 generator cases (27 when it shipped;
       raised to the measured count by the Comet 2026.03.0 intake's unit 6)
       and 4 hook defects, the removed entry named, the hook's count equal to
       the metadata's 118, and its two per-release controls
       (``index_search_type``'s and ``variable_mod01``'s entries state each
       release's own facts).
   * - 9
     - 9
     - PIT over the module; then every validation test class removed (source
       and compiled class) and PIT again.
     - ``parser``, ``writer`` and ``validation`` each, and the module, at
       least 80 % killed, scored as ``scripts/build.sh`` scores it; in the
       negative arm ``validation`` graded below 80 %.

**Item 9 per package.** ``scripts/build.sh`` grades the mutation score per
*module*. That is not what item 9 says, and the negative arm shows why it
matters: with every validation test removed, ``validation`` falls to
68/181 (37.5 %) while the module stays at 978/1102 (88.7 %) and PIT itself
exits 0 -- the module gate cannot see it; the per-package grade does.

**What the harness does not decide.** Item 9 also says "no surviving mutation
that suppresses a validation error or drops a parameter". That is a judgement
about each survivor, so the harness carries **no allow-list**: it prints every
mutant PIT did not kill in ``parser``, ``writer`` and ``validation`` -- class,
line, mutator and status, ``TIMED_OUT`` included and, as in
``scripts/build.sh``, not counted as killed -- and the reviewer reads that list
against the work log's argument for each one (``ParamsLineReader:137`` twice
and ``VariableModRules:178`` -- line 204 since the Comet 2026.03.0 intake --
equivalent; three ``ParamsLineReader`` timeouts). A new survivor does not fail
the harness unless it takes a package below 80 %.

.. _dev-comet-parameter-falsifiability-versions:

Version-blind controls (the Comet 2026.03.0 intake)
---------------------------------------------------

The intake made a set of facts **version-scoped**, each held as data in its
release's version record (decision C-2 in
``handoffs/COMET-2026-03-worklog.rst``): overrides of the curated metadata,
the residue alphabet, rule severities and value migrations. The defect such a
fact invites is not a wrong value but a value applied to the **wrong
release**, so each control below makes one of them version-blind -- in
production code, or in the shipped metadata where the metadata is what is
tested -- and, where it can, requires the release the defect does not touch to
**stay green**, so that the red is the version and nothing else. Each is graded
on its own diagnostic and proved in the bytecode like every other control.
"Recorded" means the injection is a unit sign-off's in the package's work log;
the others are new. Each runs one module compile and a narrow test selection,
7 to 12 seconds.

.. list-table:: The version-blind controls
   :header-rows: 1
   :widths: 6 8 40 46

   * - Control
     - Intake item
     - Injected defect
     - Diagnostic required
   * - v3a
     - 3 (recorded, unit 1)
     - ``CuratedMetadata`` applies the *first* version record's overrides
       (2026.03.0's) to every release.
     - ``minusOneIsNotA202602Choice: [] ==> expected: <1> but was: <0>``
       (``-1`` became a 2026.02.2 choice); 2026.02.2's
       ``index_search_type`` default ``expected: <1> but was: <-1>``.
   * - v3b
     - 3
     - No release's override is applied.
     - ``indexSearchTypeIsVersionScoped ... expected: <-1> but was: <1>``;
       2026.03.0's drift counts fail while 2026.02.2's stay green.
   * - v3c
     - 3
     - ``scan_range``'s version range in the shipped metadata ends at
       2026.02.2.
     - ``UNMODELLED: Comet 2026.03.0 declares scan_range (line 120, default
       "0 0"), which has no metadata ...`` and ``Comet 2026.03.0 COMPLETE:
       declared 118, modelled 117``; 2026.02.2's counts stay green.
   * - v4a
     - 4
     - The tuple codec gives every release 2026.03.0's residue alphabet.
     - ``VariableModCodecAlphabetTest.versionScoped: Expected
       ...ValueSyntaxException to be thrown, but nothing was thrown`` (2026.02.2
       reads ``^``); the release writer gate no longer refuses ``^``.
   * - v4b
     - 4
     - The reverse: every release gets 2024.01.0's alphabet.
     - ``"^" holds '^', which Comet 2026.03.0 does not accept in a residue
       token``, in the 2026.03.0 fifteen-slot round trip.
   * - v4c
     - 4 (gate item 1 for 2026.03.0)
     - The writer writes the *curated* inline comment, not the release's own.
     - ``Comet 2026.03.0 ==> expected: <c600c64f...fcf2e> but was: <...>`` --
       the pinned canonical text; one of the two releases fails (2026.02.2,
       which has no comment override, stays green).
   * - v5a
     - 5 (recorded, unit 4)
     - Every model is judged with the second version record's (2026.02.2's)
       severities.
     - ``ist-1, Comet 2026.03.0: ValidationReport[findings=[]] ==> expected:
       <[WARNING index_search_type.ignored_without_idx]> but was: <[]>``; a
       distance below -2 is no longer an error for 2026.03.0.
   * - v5b
     - 5
     - Validation reads the newest release's residue alphabet.
     - ``proteinNTerminus: expected exactly one finding:
       ValidationReport[findings=[]] ==> expected: <1> but was: <0>`` -- ``^``
       in a 2026.02.2 model built in code is no longer reported.
   * - v5c
     - 5 (recorded, unit 4)
     - AScorePro's ``-1`` ("localise all") suppresses the slot error
       (``ascore == 0`` became ``<= 0``).
     - ``slotsAboveNine ... print_ascorepro_score, -1]: [] ==> expected: <1>
       but was: <0>``.
   * - v5d
     - 5
     - AScorePro's merge of identical slots disabled.
     - ``mergedSlots ... [variable_mod10, 15.9949 M 0 3 -1 0 0 0.0] ==>
       expected: <[]> but was: <[Finding[rule=VARMODS_ASCOREPRO_SLOT, ...``.
   * - v5e
     - 5
     - AScorePro's protein-terminus rewrite applied whatever the release's
       alphabet, so 2026.02.2 merges as 2026.03.0 does.
     - ``proteinTerminusRewrite ... [variable_mod01, 42.010565 n 0 1 0 0 0
       0.0, variable_mod10, 42.010565 n 0 1 -1 0 0 0.0]: [] ==> expected: <1>
       but was: <0>``.
   * - v5f
     - 5
     - ``index_search_type.ignored_without_idx`` warns in every release.
     - ``silentForTheOlderRelease expected: <[]> but was:
       <[Finding[rule=INDEX_SEARCH_TYPE_IGNORED, severity=WARNING, ...``;
       2026.03.0's own warning stays green.
   * - v5g
     - 5 (data, not code)
     - One recorded **binary** verdict of the validation corpus made wrong:
       ``ist-1``'s 2026.03.0 warning line removed from ``corpus.json``.
     - ``ist-1, Comet 2026.03.0: Warning and Error lines ==> expected: <[]>
       but was: <[Warning - index_search_type = 1 is ignored: ...`` from the
       real binary; that case alone fails (``Tests run: 42, Failures: 1``).
       A test resource is not compiled, so the proof is its copy on the test
       class path: damaged in the run, pristine after the final clean run.
   * - v6a
     - 6 (recorded, unit 5)
     - Value migrations are applied whatever release they are *from*.
     - ``MigrationTo202603Test.sameRelease expected: <118> but was: <116>``.
   * - v6b
     - 6 (recorded, unit 5)
     - Migration hands conversion no source findings, so rule-keyed value
       migrations never apply.
     - ``distanceBelowMinusTwo 2026.02.2 ==> expected: <CONVERTED> but was:
       <CARRIED>``; ``terminusOutsideZeroToThree expected: <NEEDS_ATTENTION>
       but was: <CARRIED>``.

Control ``H`` also covers the new controls' plumbing: a metadata range edit
naming no parameter (``H8``), a corpus edit that would change nothing
(``H9``), and a test resource damaged in the source but not on the test class
path (``H10``) are each a harness error. A new control whose anchor has moved,
or whose injection does not compile, stops the run with exit 4 like any other
(both were shown on 2026-10-04 with a temporary copy of the script). Every
version-blind injection is laid out as ``google-java-format`` (AOSP) leaves
it, although the sandbox switches Spotless off.

**Mutation over the intake's changes** (gate item 8 of the intake, Phase 06
item 9 over the changed classes). PIT, no test excluded, over every production
class changed since ``d19b232`` -- 22 sources and their inner classes: **700 of
701 mutations killed**. The one survivor is ``VariableModRules:204``, the
boundary of ``requirementCode() > 0`` in the *wording* of the undocumented
requirement message: that line is reached only for a code that is neither 0 nor
1, so ``> 0`` and ``>= 0`` choose the same words (Phase 06's documented
equivalent, formerly line 178). It suppresses no error, drops no parameter and
touches no version-scoped fact. Every compiled class of the changed sources is
in the report except nine that carry no mutable code: three ``$1`` switch-map
classes, the enums ``RuleSeverity$Level`` and ``ValueMigration$Action``, and
the records ``MetadataLoader$Said``, ``VariableModLayout$Entry``,
``AScoreProRule$MergeKey`` and ``AScoreProRule$Slot``; every compiled class
of the module is in ``jacoco.xml`` but its nine ``package-info`` classes.
Module-wide, control
9 graded ``parser`` 96/101, ``writer`` 30/30, ``validation`` 251/252 and the
module 1360/1367 (``migration`` 84/84), and its negative arm put
``validation`` at 115/252 with the module still at 89.1 %.

Running it
----------

Run it as ``bash scripts/verify-param-gates.sh`` (about seven and a half
minutes, a third of it PIT), ``--only 2a,7c,v5g`` for named controls, or
``--self-test`` for control ``H`` alone. It needs the gitignored Comet
mirrors ``scratch/phase05/artefacts`` (2026.03.0 and 2026.02.2) and
``scratch/phase06/artefacts`` (2024.01.0) and the ``D-006`` inputs under
``scratch/fixture``, because the module's real-binary tests run them, and
refuses to start (exit 3) without them.

.. _dev-comet-parameter-comet-reads:

Comet reads what is written
===========================

``CometReadsCanonicalRealBinaryTest`` (Linux only, like the other real-binary
tests) runs each installed release's pinned binary -- 2026.02.2 and 2026.03.0
-- staged from the mirror and checked against the manifest's SHA-256, through
``ProcessService``, as ``comet -P<file> missing.mzML`` in an empty directory,
each with the canonical file written for it. Comet loads the ``-P`` file
**before** it looks at any input file [C820]_, and its parameter reader exits
with its own message for a missing or unrecognised marker [C244]_, for a file
without ``output_percolatorfile`` ("outdated params file") [C691]_, and for a
variable-modification tuple without eight fields [C576]_; it logs ``Warning -
invalid parameter found`` for every name it does not know [C536]_. Only after
all of that does it stop at ``Error - input file "missing.mzML" not found.``
[C820]_ (exit 1).

The test runs the release's own ``-q`` file first, as the control. The
control transcripts differ, and each is typed in the test as observed:

.. list-table::
   :header-rows: 1
   :widths: 14 86

   * - Release
     - Control transcript (exit 1 in both)
   * - 2026.02.2
     - standard output ``Warning - invalid parameter found:
       spectral_library_ms_level.  Parameter will be ignored.`` (``-q``
       writes it, the reader knows ``speclib_ms_level``); standard error
       ``Comet version 2026.02 rev. 2 (6edec91)`` and ``Error - input file
       "missing.mzML" not found.``
   * - 2026.03.0
     - standard output **empty** -- 2026.03.0 reads
       ``spectral_library_ms_level`` [V26L]_; standard error ``Comet version
       2026.03 rev. 0 (fa08489)`` and ``Error - input file "missing.mzML" not
       found.``

The canonical file of the same model, and a canonical file with a custom
enzyme and a paired-field tuple, must give exactly the same standard output,
standard error and exit code. For 2026.03.0, so must a canonical file with
``^``, ``$`` and ``^M$`` in three slots. A canonical file carrying one unknown
parameter must add exactly one warning naming it -- ``bogus_parameter``,
after 2026.02.2's own warning and as 2026.03.0's only one -- which shows the
comparison can see a difference.

What this proves: the real 2026.02.2 and 2026.03.0 parameter readers accept
the canonical file written for each -- marker, header, every parameter name,
every tuple's shape, the table, and for 2026.03.0 the protein-terminus codes
-- exactly as each accepts its own ``-q`` output. (That 2026.03.0 then
*searches* with ``^`` and ``$`` as meant was established by real searches,
:ref:`dev-comet-parameter-202603-termini`, not by this test.) What it does **not** prove:
that Comet reads each value as the model means it. Comet prints no parsed
values, so a value Comet's ``sscanf`` would read differently goes unseen
here; that is what the codecs' citations of Comet's reading code are for.

Citations
---------

Comet's documentation, 2026.02 page set, fetched 2026-10-02:

.. [VM] https://uwpr.github.io/Comet/parameters/parameters_202602/variable_modXX.html
.. [EZ] https://uwpr.github.io/Comet/parameters/parameters_202602/search_enzyme_number.html
.. [TL] https://uwpr.github.io/Comet/parameters/parameters_202602/peptide_mass_tolerance_lower.html
.. [IA] https://uwpr.github.io/Comet/parameters/parameters_202602/use_A_ions.html
.. [DP] https://uwpr.github.io/Comet/parameters/parameters_202602/decoy_prefix.html

Comet's source at tag ``v2026.02.2``, file and line (``C`` is ``Comet.cpp``,
``M`` ``CometSearch/CometSearchManager.cpp``, ``S``
``CometSearch/CometSearch.cpp``, ``D`` ``CometSearch/CometData.h``, ``K``
``CometSearch/core/Constants.h``):

.. [C244] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L242-L278
   -- the version check: ``# comet_version`` in the first seven lines, or
   exit.
.. [C308] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L308-L319
   -- ``parse_int_range`` and ``parse_double_range``.
.. [C400] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L400-L403
   -- the enzyme numbers copied into locals, per declaration.
.. [C407] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L407-L414
   -- the ion-series parameters, read with ``parse_int``.
.. [C454] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L454-L455
   -- the tolerance pair, read with ``parse_double``.
.. [C486] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L486-L492
   -- which parameters are ranges.
.. [C494] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L494-L514
   -- the ``mass_offsets`` handler.
.. [C536] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L536-L634
   -- the main loop: cut at ``#``, one ``SetParam`` per declaration, a
   warning for an unknown name.
.. [C541] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L541-L542
   -- parameters end at ``[COMET_ENZYME_INFO]``.
.. [C552] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L552-L621
   -- the tuple reader; line 552 its name rule.
.. [C576] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L576-L583
   -- exactly eight fields, or exit.
.. [C589] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L589-L597
   -- ``"%lf %31s %d %511s %d %d %d %s"``.
.. [C600] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L600-L603
   -- a comma in field 8 means two losses, ``"%lf,%lf"``.
.. [C605] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L605-L611
   -- a comma in field 4 means ``min,max``.
.. [C654] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L654-L688
   -- the enzyme loop.
.. [C657] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L657
   -- the row number, ``"%d."``.
.. [C662] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L662-L667
   -- the row, ``"%lf %47s %d %19s %19s"``.
.. [C691] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L691-L721
   -- "outdated params file" and the three "is missing definition" checks.
.. [C820] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L820-L890
   -- ``ProcessCmdLine``: ``LoadParameters`` (line 862) before the input
   files (line 880).
.. [C971] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L971-L973
   -- the ``-q`` comment above the slots.
.. [C1177] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L1177-L1190
   -- the default table ``-q`` writes.
.. [M518] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L518-L532
   -- only ``variable_mod01`` to ``variable_mod15`` are used.
.. [M1246] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1246-L1264
   -- cut and no-cut both ``-`` is no enzyme.
.. [M1368] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1368-L1369
   -- a zero mass leaves a slot unused.
.. [M1380] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1380-L1396
   -- ``n`` and ``c`` in the residue token.
.. [M1398] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1398-L1399
   -- any non-zero group is binary.
.. [M1401] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1401-L1402
   -- any positive requirement is required.
.. [M1404] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1404-L1405
   -- a zero first loss is none.
.. [M1651] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1651-L1660
   -- ``SetParam`` replaces an existing entry (every overload alike).
.. [M1892] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1892-L1904
   -- ``IsValidCometVersion``.
.. [S1770] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L1770
   -- a zero second loss is skipped.
.. [S5375] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5375-L5390
   -- the terminus codes 0 to 3.
.. [S5881] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5881
   -- only ``-1`` is exclusive.
.. [S6874] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L6874-L6875
   -- distance ``-2``.
.. [D269] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometData.h#L269-L284
   -- the ``VarMods`` defaults.
.. [D345] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometData.h#L345-L365
   -- ``EnzymeInfo``'s constructor: names ``""`` and ``"Cut_everywhere"``.
.. [K80] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/core/Constants.h#L80
   -- ``VMODS`` is 15.

Added with validation (unit 5), at the same tag; ``P`` is
``CometSearch/CometPreprocess.cpp``:

.. [C301] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L301-L306
   -- ``parse_string``: ``sscanf`` with ``%255s``, the first token only.
.. [C342] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L342-L345
   -- the four paths copied whole into ``char szFile[SIZE_FILE]``.
.. [C347] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L347-L352
   -- the parameters read with ``parse_string``.
.. [C768] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L768-L787
   -- ``scan_range`` taken per input file, each end independently.
.. [C1054] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L1054-L1055
   -- the ``-q`` comments of ``scan_range`` and ``precursor_charge``.
.. [M304] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L304-L316
   -- ``ValidateScanRange``: an end below the start is refused only when the end is not 0.
.. [M534] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L534-L538
   -- a negative ``max_variable_mods_in_peptide`` is ignored.
.. [M543] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L543-L549
   -- ``require_variable_mod``.
.. [M572] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L569-L576
   -- the tolerance pair read and both bounds negated.
.. [M584] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L584-L588
   -- ``peptide_mass_units`` outside 0 to 2 becomes 0.
.. [M606] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L606-L613
   -- ``clear_mz_range`` applied only when ordered.
.. [M1009] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1009-L1022
   -- ``peptide_length_range`` applied only when ordered.
.. [M1054] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1054-L1061
   -- ``precursor_charge`` applied only when the start is above 0 and ordered.
.. [M1093] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1093-L1100
   -- ``digest_mass_range`` applied only when ordered.
.. [M1125] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1125-L1127
   -- ``decoy_search`` outside 0 to 2 becomes 0.
.. [M1584] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1584-L1588
   -- "mass_tolerance_lower is greater than mass_tolerance_upper".
.. [M1961] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearchManager.cpp#L1961-L1972
   -- an empty or unreadable database or spectral library switches that search off.
.. [S2933] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L2933
   -- unmodified peptides scored only when no modification is required.
.. [S5371] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5369-L5372
   -- a negative distance counts as no constraint.
.. [S5454] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5452-L5461
   -- the same for terminal modifications.
.. [S5466] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5466-L5520
   -- terminal distances by terminus code 0 to 3.
.. [S5729] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5729-L5733
   -- at most the maximum count is placed.
.. [S5740] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5740-L5743
   -- the loops break above ``max_variable_mods_in_peptide``.
.. [S5844] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometSearch.cpp#L5844-L5845
   -- a count below the minimum is rejected (one of fifteen alike).
.. [D20] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometData.h#L20
   -- ``SIZE_FILE`` is 4096.
.. [D25] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometData.h#L25
   -- ``MAX_VARMOD_AA`` is 32.
.. [K77] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/core/Constants.h#L77
   -- ``FRAGINDEX_VMODS`` is 5.
.. [P383] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/CometPreprocess.cpp#L383-L387
   -- each ``scan_range`` end used when not 0.
