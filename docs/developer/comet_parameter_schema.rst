.. _dev-comet-parameter-schema:

======================
Comet parameter schema
======================

.. note::

   **Status: in progress, Phase 06.** Landed so far: the real-binary fixtures
   (unit 1); the schema model, the curated metadata file, the line-level
   reader, schema discovery, the version marker, drift detection and the
   schema provider (unit 2). The typed parser and writer, the structured value
   codecs, validation, presets, migration and the generator behind
   :doc:`../reference/comet_parameters_generated` (``R-DOC-04``) are still to
   come; where this page mentions them it describes intent, not the product.

What this page will cover
=========================

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
parameters for 2026.02.2); ``comet-p.params`` is the one written by ``comet
-p`` (the default file, 96). ``SHA256SUMS`` is ``sha256sum`` output for both.
A ``.gitattributes`` in ``fixtures/comet/`` sets ``-text`` so that Git never
converts their line endings on checkout -- ``core.autocrlf=true`` is the
default on a Windows runner and would otherwise change the bytes.

Tests locate fixtures through
``org.cometgui.params.comet.fixtures.CometFixtures`` (test sources) by
version, platform and mode rather than by path.

Today there is one set: **Comet 2026.02.2, linux/x86-64**.

.. list-table::
   :header-rows: 1
   :widths: 22 12 66

   * - File
     - Bytes
     - SHA-256
   * - ``comet-q.params``
     - 11 844
     - ``d15048709f485c09a840dcb2a384dc301b4ba4da4566c2ea053e9c17eae58f51``
   * - ``comet-p.params``
     - 10 214
     - ``b56c967959ae04e2e782411858b2f5be06863e572cf1bfdfeccc7a4b28fbb19c``

The manifest also names Comet 2026.02.2 for linux/aarch64, macos/x86-64,
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
* ``ParameterDumpFactsTest`` (every platform): the ``R-PARAM-01`` facts --
  ``-q`` declares 118 parameters, ``-p`` 96, ``-p`` declares nothing ``-q``
  does not, and the difference is exactly the 22 names the specification
  lists, typed into the test from the specification rather than derived from
  the fixtures; the ``-q`` file starts with the ``# comet_version`` marker and
  ends with the ``[COMET_ENZYME_INFO]`` table. Declarations are counted with a
  line rule (a name at column one, optional blanks, ``=``), not a parser.

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
   test must pass for the new version. ``ParameterDumpFactsTest`` asserts the
   2026.02.2 facts only; a new version's counts are a new fact, established by
   running it and checked against the specification, not copied from the
   file.

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
       ``ENZYME_REFERENCE`` and ``ION_SERIES_FLAG``. The codecs for the tuple,
       the enzyme table and the tolerance pair are not here.
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
documentation generator will read it with Python's standard library. That
reader accepts whole numbers only, so **every bound and default is a JSON
string**, written exactly as ``comet.params`` writes it (``"20.0"``,
``"0 0"``, ``"15.9949 M 0 3 -1 0 0 0.0"``). Every field is always present;
JSON ``null`` marks an absent value. A field the loader does not know is an
error, in every object.

Top level::

    {
      "schemaVersion": 1,
      "description": "...",
      "versions":    [ { "version", "marker", "parameterPages", "source",
                         "variableModTuple" } ],
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
help was written from. ``variableModTuple`` must be ``null`` until the
structured value types record the tuple's field layout there.

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
does not start at a curated version or ends before it starts; a tolerance-pair
member carrying the generic ``ordered_range`` rule (``R-PARAM-04``); a related
parameter that is unknown, the parameter itself, or named twice; a help or
source reference that is not ``https://``; an allow-list entry without a
reason, also modelled, or listed twice; and an enzyme-table section that does
not list exactly the ``ENZYME_REFERENCE`` parameters.

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

Two upstream defects are recorded in the help rather than hidden:

* ``spectral_library_ms_level`` is written by ``-q``, but Comet 2026.02.2's
  reader looks for ``speclib_ms_level`` and its search code for
  ``spectraL_library_ms_level``, so a value under the written name is ignored.
* ``add_U_selenocysteine`` is read, but the static mass for U is taken from
  ``add_U_user_amino_acid``, a name ``-q`` does not write, so a value under
  the written name has no effect.

The allow-list
--------------

``internal`` exists for parameters Comet declares that are hidden or internal
and deliberately not modelled, each with a reason. For Comet 2026.02.2 it is
**empty**: every one of the 118 parameters ``-q`` declares is modelled,
including the two above, because a user can set them.

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
real ``-q`` fixture of every Comet version ``manifests/tools.json`` names --
the enumeration unit 1's ``FixtureMatrixTest`` already requires fixtures for
-- and fails on any finding with the whole report. For 2026.02.2 it asserts
118 declared, 118 modelled, 0 allow-listed. It also proves the partial rule
against the real ``-p`` fixture: none of the 22 ``-q``-only parameters is
reported, and a parameter removed from the metadata still is.

To add a Comet version: capture its fixtures (*Fixtures*, above), add its
``versions`` record, and run the module's tests. Each finding is a decision
to make against that release's documentation and source -- a new parameter
to describe, a range to close with ``through``, a default to update -- never
a reason to widen the allow-list for a parameter a user could set.
