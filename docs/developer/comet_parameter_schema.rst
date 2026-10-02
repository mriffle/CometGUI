.. _dev-comet-parameter-schema:

======================
Comet parameter schema
======================

.. note::

   **Status: in progress, Phase 06.** Landed so far: the real-binary fixtures
   (unit 1); the schema model, the curated metadata file, the line-level
   reader, schema discovery, the version marker, drift detection and the
   schema provider (unit 2); the structured value types and their codecs --
   the variable-modification tuple with its layout taken from the metadata, the
   enzyme table, the tolerance pair, the ranges, the mass-offset list and the
   ion-series family (unit 3). The typed parser and writer, validation,
   presets, migration and the generator behind
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
                         "variableModTuple": { "source",
                                               "fields": [ { "field", "kind", "pair" } ] } } ],
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
not list exactly the ``ENZYME_REFERENCE`` parameters. For the tuple layout:
a ``variableModTuple`` that is not an object (``null`` included); a member,
field constant or kind it does not know; a ``pair`` that is not a boolean; a
field listed twice, declared with a kind that is not the field's, or given a
pair where no Comet release accepts one (only the count and the neutral loss
are pairable); a layout without the mass difference or the residues; a
parameter named ``variable_mod`` plus two digits that is not of kind
``VARIABLE_MOD_TUPLE``, or the other way round; and a tuple default whose
field count differs from the layout of a version it claims.

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

Citations
---------

Comet's documentation, 2026.02 page set, fetched 2026-10-02:

.. [VM] https://uwpr.github.io/Comet/parameters/parameters_202602/variable_modXX.html
.. [EZ] https://uwpr.github.io/Comet/parameters/parameters_202602/search_enzyme_number.html
.. [TL] https://uwpr.github.io/Comet/parameters/parameters_202602/peptide_mass_tolerance_lower.html
.. [IA] https://uwpr.github.io/Comet/parameters/parameters_202602/use_A_ions.html

Comet's source at tag ``v2026.02.2``, file and line (``C`` is ``Comet.cpp``,
``M`` ``CometSearch/CometSearchManager.cpp``, ``S``
``CometSearch/CometSearch.cpp``, ``D`` ``CometSearch/CometData.h``, ``K``
``CometSearch/core/Constants.h``):

.. [C308] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L308-L319
   -- ``parse_int_range`` and ``parse_double_range``.
.. [C407] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L407-L414
   -- the ion-series parameters, read with ``parse_int``.
.. [C454] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L454-L455
   -- the tolerance pair, read with ``parse_double``.
.. [C486] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L486-L492
   -- which parameters are ranges.
.. [C494] https://github.com/UWPR/Comet/blob/v2026.02.2/Comet.cpp#L494-L514
   -- the ``mass_offsets`` handler.
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
.. [K80] https://github.com/UWPR/Comet/blob/v2026.02.2/CometSearch/core/Constants.h#L80
   -- ``VMODS`` is 15.
