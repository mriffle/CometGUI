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
                                               "fields": [ { "field", "kind", "pair" } ] },
                         "defaults": [ { "name", "default", "source" } ] } ],
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
only booleans in the file.

``defaults`` lists the parameters whose default **that release** writes
differently from the parameter's curated ``default``, each with the
``https://`` source line that writes it; it is empty for most releases. Two
records exist (:ref:`dev-comet-parameter-older-release`):

.. list-table::
   :header-rows: 1
   :widths: 14 26 60

   * - ``version``
     - ``marker``
     - What else it records
   * - ``2026.02.2``
     - ``2026.02 rev. 2 (6edec91)``
     - The release matrix's version. Neutral loss and count both take a
       comma pair. ``defaults`` empty: every curated default is its own.
   * - ``2024.01.0``
     - ``2024.01 rev. 0 (f00df0c)``
     - The migration fixture's release, not offered to users. The neutral loss
       takes **one** value -- Comet 2024.01.0 reads it with ``%lf`` [V24T]_
       -- and the count takes ``min,max``. ``defaults``:
       ``fragindex_num_spectrumpeaks = 100`` and
       ``fragindex_skipreadprecursors = 0`` [V24D]_, where 2026.02.2 writes
       ``150`` and ``1``.

``CuratedMetadata.parametersFor(version)`` and ``parameter(name, version)``
give each definition **with that version's default** -- the override where
there is one -- so the parser's defaults, ``CometParameters.defaults``,
``resetToDefault`` and the drift test all use the version's own value.
``parameter(name)`` alone gives the curated definition, whatever the version.

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
break, or has white space at either end; a version's default override that
names a parameter which is not modelled, or not modelled for that version, or
that is overridden twice, has no ``https://`` source, repeats the curated
default, or breaks any rule a curated default must keep; a tolerance-pair
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
``VARIABLE_MOD_TUPLE``, or the other way round; and a tuple default whose
field count differs from the layout of a version it claims.

``inlineComment`` is the comment the canonical writer puts after the value on
the parameter's own line (:ref:`dev-comet-parameter-canonical`), or ``null``
for none. The starting text is Comet's own ``-q`` comment for the parameter,
verbatim, for the 87 parameters that have one. Four are curated away from it
because the ``-q`` text is wrong for 2026.02.2: ``isotope_error`` adds the
values 6 and 7, ``output_txtfile`` drops the "2=Crux-formatted" that the
parameter code treats as 1, and ``spectral_library_ms_level`` (which ``-q``
writes without a comment) and ``add_U_selenocysteine`` say that Comet ignores
them (both facts are below). It must be one line because it is written on one
line, and unpadded because the reader trims it.

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

It also runs against the **migration fixture's** real Comet 2024.01.0 output
(:ref:`dev-comet-parameter-older-release`): ``-q`` 109 declared, 109 modelled,
0 allow-listed, no finding; ``-p`` (``PARTIAL_DISCOVERY``) 87 and 87.
``OlderReleaseCurationTest`` proves that what is curated about 2024.01.0 is
checked rather than trusted: stripping the two default overrides makes the
drift test report exactly those two ``DEFAULT_DIFFERS``; claiming a
2026-only parameter for 2024.01.0 is reported ``NOT_DECLARED``; and dropping
2024.01.0 from a parameter it declares is reported ``UNMODELLED``.

To add a Comet version: capture its fixtures (*Fixtures*, above), add its
``versions`` record, and run the module's tests. Each finding is a decision
to make against that release's documentation and source -- a new parameter
to describe, a range to close with ``through``, a default to update -- never
a reason to widen the allow-list for a parameter a user could set.

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
       C-terminus, combinable (``nK``).
     - Page [VM]_; read ``%31s`` [C589]_; ``n`` and ``c`` [M1380]_.
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
count of its own. A field a layout does not hold is given Comet's own default
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
warn rather than the parser guess. The residue token must be letters ``A`` to
``Z`` with ``n`` and ``c`` -- the alphabet the page's own generator enforces
([VM]_, "Valid: n, c, A-Z"); Comet's reader takes any token up to 31
characters [C589]_, so the length limit is left to validation.
``effectiveNeutralLosses()`` drops zeros, as Comet does.

``summary()`` writes the value in words for the editor, as the specification
asks::

    Oxidation: +15.994915 on M; max 3 per peptide; optional
    +79.966331 on STY; 2 to 4 per peptide; required; neutral losses 97.976896 and 79.966331
    +28.0 on C-terminus, within 9 residues of the protein C-terminus; max 3 per peptide; optional
    unused (mass difference 0.0)

An undocumented code is named, not guessed (``requirement code 2``).

The round trip, gate item 3
~~~~~~~~~~~~~~~~~~~~~~~~~~~

``VariableModRoundTripTest`` runs **17 forms x 15 slots = 255** dynamic tests.
The forms (``TupleForms``, test sources) are CONSTRUCTED test input: the
page's own examples [VM]_ -- one loss, two losses, required, ``nK``, ``n`` at
the protein N-terminus, ``c`` within 8 of the protein C-terminus, the
pyroglutamate cyclisation at the peptide N-terminus, binary groups 1 and 2 --
plus forms built from the page's field descriptions where it gives no
whole-line example (``min,max``, exclusive ``-1``, distance ``-2``, the peptide
C-terminus, all of them at once), ``comet -q``'s two defaults, and the
``15.994915`` edit. For each slot the bundled metadata gives 2026.02.2, a line
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

Gate item 5 is ``CommaLocaleWriterTest``: under ``de-DE`` and ``fr-FR`` --
after showing that each really formats ``1.5`` as ``1,5`` with both
``String.format`` and ``NumberFormat`` -- the real fixture's model and a
CONSTRUCTED variant full of decimals parse and write to the same bytes as
under ``Locale.ROOT``. The default locale is restored after every test.

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
one ``Rule`` with a **stable identifier** and a **fixed severity**, attached to
the responsible parameters (the first is the one to show it at) and their
category -- *Errors shall be attached to the responsible field and category*.
``hasErrors()`` is what Phase 08 blocks a run on; ``forParameter(name)`` and
``forCategory(category)`` are what Phase 07 shows at a control and a category
heading (``AC-PAR-10``). The report is in a stable order: per parameter in the
model's order, then the cross-field rules, then what the import left.

What validation checks is the **model**. It reads no file: whether the
database and spectra exist and are readable, output paths are writable, the
FASTA holds decoys (``R-DEC-02``) or the PIN holds targets and decoys
(``R-DEC-04``) needs the file system or data and is the workflow's check before
a run (Phase 08). Paths are checked for **form** only.

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

The rule catalogue
------------------

Severity is the rule's: **E** an error (blocks a run), **W** a warning. Source
names the Comet fact a rule encodes, at tag ``v2026.02.2``; a rule with none
encodes this project's choice or the specification's.

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
       finding at the field before anything is written. Comet's own
       "missing definition" checks can never fire.
     - [C691]_, [D345]_
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
     - W
     - an active slot
     - Distance is -2, -1 or 0 and up; Comet treats other negatives as -1.
     - [VM]_, [S5371]_, [S5454]_
   * - ``variable_mod_tuple.terminus_undocumented``
     - E
     - an active slot with a distance of 0 or more
     - Terminus 0 to 3; with a distance Comet matches no other code, so the
       modification never applies. (With distance -1 or -2 the terminus is
       not consulted and is not checked.)
     - [S5375]_, [S5466]_
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

A slot is **active** when its mass difference is not 0 [M1368]_; the meaning
rules apply to active slots only. A slot's maximum above
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
     - Phase 08 (file system). Here: ``database_name`` present, no NUL, fits
       Comet's buffer.
   * - Spectra exist and use a supported format
     - Phase 08: spectra are run inputs, not parameters.
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
     - Here, at model level; locking the controls is Phase 07's, blocking a
       run Phase 08's.
   * - Selected index and search options are compatible
     - **Not here.** It depends on whether ``database_name`` names an existing
       ``.idx`` and which type that file records (Comet reads only the first
       five variable modifications for a fragment-ion index [K77]_), so it needs the
       file system: Phase 08. Not yet assigned in any phase document;
       reported upward.
   * - Decoy configuration satisfies *Target/decoy strategy*
     - Here: the decoy source (``R-DEC-01``) and the prefix. Phase 08: the
       FASTA scan (``R-DEC-02``), the PIN check (``R-DEC-04``) and carrying the
       prefix to Percolator and Limelight (``R-DEC-03``).
   * - Output paths are writable
     - Phase 08 (file system).
   * - Imported unknown parameters are surfaced
     - Here (``unknown_parameter.imported``) and in the parse result (unit 4).
   * - Parameters unavailable in the selected version are blocked
     - Here (``version.parameter_unavailable``).


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
       adopted unknown whose text is not of the target's kind). The new model
       holds the target's default; the source value is in the report. Nothing
       is guessed.

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
  documents those, and only those, as supported: today ``2026.02.2``. The
  ``2024.01.0`` record is curated for migration only and appears in an entry
  solely as the start of its version range, labelled as not installed;
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
bounds, the tuple layout of that version, or a pointer to the enzyme table),
the short help with its upstream reference, the serialisation rule and the
**default line exactly as** ``CanonicalParamsWriter`` **writes it** (``name =
value``, the inline comment's ``#`` at column 40), version availability,
related parameters as links, preset effects, the editor level, the validator
ids and the search aliases. Then the enzyme table and the internal allow-list.
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
* a related parameter that is not modelled (or is the parameter itself); a
  version default override, an enzyme-table reference or a preset delta naming
  no modelled parameter; a preset for an uncurated version;
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
non-``https`` reference, a preset naming no parameter and an installed version
with no record. Then, through the real hook in a project copy made by
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
     - Its OK line with at least 27 generator cases and 4 hook defects, the
       removed entry named, and the hook's count equal to the metadata's 118.
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
and ``VariableModRules:178``, equivalent; three ``ParamsLineReader``
timeouts). A new survivor does not fail the harness unless it takes a package
below 80 %.

Run it as ``bash scripts/verify-param-gates.sh`` (about five and a half
minutes, two thirds of it PIT), ``--only 2a,7c`` for named controls, or
``--self-test`` for control ``H`` alone. It needs the gitignored Comet
mirrors ``scratch/phase05/artefacts`` and ``scratch/phase06/artefacts``,
because PIT runs the module's real-binary tests, and refuses to start (exit 3)
without them.

.. _dev-comet-parameter-comet-reads:

Comet reads what is written
===========================

``CometReadsCanonicalRealBinaryTest`` (Linux only, like the other real-binary
tests) runs the pinned 2026.02.2 binary, staged from the mirror and checked
against the manifest's SHA-256, through ``ProcessService``, as ``comet
-P<file> missing.mzML`` in an empty directory. Comet loads the ``-P`` file
**before** it looks at any input file [C820]_, and its parameter reader exits
with its own message for a missing or unrecognised marker [C244]_, for a file
without ``output_percolatorfile`` ("outdated params file") [C691]_, and for a
variable-modification tuple without eight fields [C576]_; it logs ``Warning -
invalid parameter found`` for every name it does not know [C536]_. Only after
all of that does it stop at ``Error - input file "missing.mzML" not found.``
[C820]_ (exit 1).

The test runs Comet's own ``-q`` file first, as the control: one warning,
for ``spectral_library_ms_level`` (``-q`` writes it, the reader knows
``speclib_ms_level``), then the input-file error. The canonical file of the
same model, and a canonical file with a custom enzyme and a paired-field tuple,
must give exactly the same standard output, standard error and exit code. A
canonical file carrying one unknown parameter must add exactly one warning
naming it, which shows the comparison can see a difference.

What this proves: the real 2026.02.2 parameter reader accepts the canonical
file -- marker, header, every parameter name, every tuple's shape, the table
-- exactly as it accepts its own ``-q`` output. What it does **not** prove:
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
