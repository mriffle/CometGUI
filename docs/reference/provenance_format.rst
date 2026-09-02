.. _ref-provenance-format:

=================
Provenance format
=================

This page is the **on-disk format** of a CometGUI run's provenance record: the
key order, every member, the conventions that make the document checkable, and
the rules a reader must apply. It is written so that someone who has never seen
this repository can write a ``provenance.json`` parser from it without reading
the Java.

The model behind the format -- why duration is derived, how the hash cache
decides, what a torn event log yields -- is
:doc:`../developer/provenance_schema`. The reading of a record in the
application itself is :doc:`../provenance`.

**This page describes schema version 1.** The version is a compatibility
statement rather than a decoration; see :ref:`ref-provenance-format-version`.

.. note::

   **The member list on this page is checked against the writer by a test.**
   ``ProvenanceFormatDocumentationTest`` renders a fully populated manifest,
   parses it back and compares the set of member names it finds against a
   hand-typed list, with the count asserted separately so that an empty walk
   cannot pass. When that test fails, this page is stale, and the page and the
   test are updated together.

.. contents:: Contents
   :depth: 2
   :local:

What a run leaves behind
========================

Three artefacts, in the run's own directory.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Artefact
     - What it is

   * - ``provenance.json``
     - The machine-readable manifest. Written once, atomically, when the run is
       finalised. This page is its format.

   * - ``provenance.rst``
     - The human-readable report, generated from the same model as the
       manifest, never maintained independently. It carries the same facts in
       reStructuredText; it is not a second source of truth and is not parsed
       by anything.

   * - the event log
     - One JSON object per line, appended while the run happens. This is what a
       run that crashed leaves instead of a manifest; see
       :ref:`ref-provenance-format-event-log`.

The name ``provenance.json`` is fixed, and so is ``provenance.rst``. **The
event log's file name is not fixed by this format**: the log is opened on a
path its caller chooses. The tests and the run-directory secret sweep use
``events.log``.

The document
============

``provenance.json`` is a single JSON object. Its six members are written in a
fixed order -- identity, then environment, then configuration, then what was
done, then what was touched -- and ``schemaVersion`` is always first, so that a
reader knows on its first member whether it understands the rest.

.. code-block:: json

   {
     "schemaVersion": 1,
     "run": {
       "runId": "run-20260831-101500",
       "projectId": "project-beta",
       "status": "running",
       "start": "2026-08-31T10:15:00.000Z",
       "end": null,
       "durationMillis": null
     },
     "application": {
       "cometGuiVersion": "0.1.0-SNAPSHOT",
       "buildIdentifier": "9f8c1d2e4b7a",
       "osName": "Windows 11",
       "osVersion": "10.0",
       "architecture": "aarch64",
       "jvmVersion": "25.0.4.1",
       "locale": "und",
       "formatLocale": "tr-TR",
       "zoneId": "UTC"
     },
     "settings": {},
     "tools": [],
     "files": []
   }

That is a real document and not an abbreviation: it is the manifest of a run
that has started and not finished, and it is the state of the file for the
whole length of a run. Note ``"end": null`` with ``"durationMillis": null``
beside it, the empty object for ``settings`` and the empty arrays for ``tools``
and ``files``.

Conventions a reader may rely on
================================

Every one of these is a property of the format, not of a particular writer, and
every one of them is pinned by a test that compares a whole document against a
hand-typed literal.

Bytes and layout
----------------

* **UTF-8**, always, on every platform. The file is read strictly: a byte
  sequence that is not UTF-8 is an error, never a replacement character.
* **The line terminator is** ``\n``, **including on Windows.** A document whose
  bytes depend on the machine that wrote it is not a provenance record: it
  would hash differently for the same run.
* **Exactly one trailing newline**, after the closing brace and nothing else.
  That makes the file a well-formed POSIX text file, so ``diff``, ``wc -l`` and
  every line-oriented tool behave.
* **Two spaces of indentation per level.** One member or element per line, a
  ``,`` at the end of the preceding line, and ``": "`` -- colon, one space --
  between a name and its value.
* **An empty object is** ``{}`` **and an empty array is** ``[]``, on one line.
* Nesting is at most five levels deep. A reader is entitled to bound its own
  recursion; this one refuses anything deeper than 64.

Order
-----

* **Object members are written in the order this page lists them**, not in the
  order a map iterated. Two runs that differ only in their data produce
  documents that differ only in that data, which is what makes two provenance
  records diffable.
* **The two open-ended maps are sorted by key**: ``settings`` and each
  execution's ``environment``. The ordering is ``String``'s natural,
  code-point ordering -- not a locale-sensitive collation, which would sort
  differently in Sweden than in Germany.
* **Arrays keep their order.** An argument array reordered is a different
  command, and a list of warnings reordered is a different story about the run.
  ``capabilities`` is the one array whose order is also ascending, because the
  model holds it as a sorted set.

Values
------

* **Every number is a whole number**, rendered with ``Long.toString``: ASCII
  digits, an ASCII minus sign, no grouping, no fraction, no exponent, no
  leading ``+`` and no leading zero. Every quantity in a manifest is a byte
  count, an exit code, a millisecond count or a schema version.
* **An absent optional is written as** ``null``, **never omitted.** Every
  document of a given schema version therefore carries exactly the same set of
  keys, and the two conditions mean different things: a key that is *absent* is
  a schema disagreement worth failing on, while a key that is ``null`` is this
  run saying it has no such value.
* **Non-ASCII text is emitted as itself.** Everything at or above ``U+0020``
  is written literally and encoded as UTF-8, including accented characters,
  ``µ`` and emoji. Only the double quote, the backslash and the control
  characters are escaped: the five with short forms as ``\b``, ``\f``, ``\n``,
  ``\r`` and ``\t``, and
  everything else below ``U+0020`` as ``\u00XX`` with lower-case hexadecimal
  digits. A path containing ``é`` therefore reads as that path, which is what
  lets a scientist check a record against their own disk.
* **A digest is lower-case hexadecimal**: 32 characters for MD5, 64 for
  SHA-256.

Timestamps
----------

Every timestamp in the format -- in the manifest and in the event log alike --
is a string in exactly this form::

    uuuu-MM-dd'T'HH:mm:ss.SSS'Z'

* **UTC, always**, and the trailing ``Z`` is a literal. The zone the run
  actually happened in is not lost: it is recorded once, as
  ``application.zoneId``, which is where a reader that wants local wall-clock
  time gets it.
* **Exactly three fractional digits, always.** ``.000`` is written for an
  instant with no fractional part. The width never varies, so a reader may
  treat the field as fixed width.
* **Truncated towards the past, not rounded.** An instant of
  ``09:14:00.250999999Z`` is recorded as ``09:14:00.250Z``. A round trip
  through this format is lossy below one millisecond, and anything comparing a
  parsed instant against an original must compare at millisecond precision.
* ``uuuu`` is the proleptic year, which is what ISO-8601 means; the format
  carries four-digit years only.

Locales and time zones
----------------------

* A locale is its **BCP 47 language tag**, as ``Locale.toLanguageTag()`` writes
  it: ``en-US``, ``de-DE``, ``tr-TR``. The root locale is ``und``.
* A time zone is its **zone id**: ``Europe/Berlin``, ``UTC``.

Neither is a display name. A display name is itself locale-dependent, so a
German JVM would record ``Vereinigte Staaten`` where an English one recorded
``United States`` -- and the field that exists to explain a locale-dependent
difference would be the one field that had it.

.. _ref-provenance-format-version:

The schema version
==================

``schemaVersion`` is the first member of the document, and version 1 is the
first published format.

**What obliges a bump.** The number is bumped whenever a member is removed,
renamed, re-typed, or given a new meaning. Adding an optional member that an
older reader can ignore does **not** require a bump; nothing else escapes one.
Every constant of this format -- the settings-key pattern, the pinned settings
keys, the wire names below -- is part of the on-disk contract, so changing one
is a format change rather than a refactoring.

**What a reader must do with it.**

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - The document says
     - What a reader does

   * - a **higher** version than it implements
     - **Refuse the document outright.** Not "read the members I recognise": a
       newer writer may have changed what a member *means* rather than merely
       added one, and a half-understood provenance record is worse than an
       unreadable one, because it is wrong without saying so.

   * - a **lower** version
     - **Refuse it until a migration exists**, and then migrate explicitly, so
       that the members a later version added have declared values rather than
       silently absent ones. There is no migration today: version 1 is the
       first published format, so nothing below it was ever written and a
       version below 1 is a corrupt or invented document.

   * - the **same** version
     - Read it, applying every rule on this page.

**Unknown members are ignored; missing known members are an error.** That is
the other half of the same policy -- an older reader has to be able to ignore
the optional member a later version added. Because every member this format
defines is always present, a mistyped key is caught as the *known* key it
displaced rather than passing unnoticed as an unknown extra.

Field reference
===============

In the tables below, **Null?** says whether the member may be JSON ``null``. It
is never absent.

The root object
---------------

.. list-table::
   :header-rows: 1
   :widths: 20 14 10 56

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``schemaVersion``
     - number
     - no
     - The format version this document was written against. Always the first
       member. Fits in a signed 32-bit integer and is at least 1.

   * - ``run``
     - object
     - no
     - The run's identity, state and timing.

   * - ``application``
     - object
     - no
     - The application, machine and JVM the run happened on.

   * - ``settings``
     - object
     - no
     - The scientific and export settings in effect. May be ``{}``.

   * - ``tools``
     - array
     - no
     - Every tool invocation the run made, in the order they were made. May be
       ``[]``.

   * - ``files``
     - array
     - no
     - Every input and output file the run read or wrote. May be ``[]``.

``run``
-------

.. list-table::
   :header-rows: 1
   :widths: 20 14 10 56

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``runId``
     - string
     - no
     - The run identifier, which also names the run's directory on disk. Starts
       with a letter or a digit and contains only letters, digits, ``.``, ``-``
       and ``_``; at most 64 characters.

   * - ``projectId``
     - string
     - no
     - The project the run belongs to. Never blank.

   * - ``status``
     - string
     - no
     - One of the :ref:`status values <ref-provenance-format-status>`.

   * - ``start``
     - string
     - no
     - When the run began.

   * - ``end``
     - string
     - **yes**
     - When it finished. ``null`` while the run is still in progress, and for a
       run that was interrupted and never wrote its end. Never before
       ``start``.

   * - ``durationMillis``
     - number
     - **yes**
     - How long the run took, in milliseconds. ``null`` exactly when ``end`` is
       ``null``. See :ref:`ref-provenance-format-duration`.

``application``
---------------

.. list-table::
   :header-rows: 1
   :widths: 22 12 10 56

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``cometGuiVersion``
     - string
     - no
     - The version of CometGUI that produced the run. Never blank.

   * - ``buildIdentifier``
     - string
     - no
     - The build identifier or git commit that version was built from.

   * - ``osName``
     - string
     - no
     - The operating system name, from the JVM's ``os.name``.

   * - ``osVersion``
     - string
     - no
     - The operating system version, from ``os.version``.

   * - ``architecture``
     - string
     - no
     - The CPU architecture, from ``os.arch``.

   * - ``jvmVersion``
     - string
     - no
     - The Java runtime version, from ``java.version``.

   * - ``locale``
     - string
     - no
     - The JVM default locale in effect during the run, as a BCP 47 language
       tag. Required by ``R-PROV-04``, because locale reaches serialisation.

   * - ``formatLocale``
     - string
     - no
     - The ``Locale.Category.FORMAT`` default in effect during the run. This is
       the one that actually governs number formatting, and therefore the one
       that decides whether a written parameter file used ``0.0200`` or
       ``0,0200``. It is a separate member because a run that set the two apart
       must be representable.

   * - ``zoneId``
     - string
     - no
     - The JVM default time zone in effect during the run.

``settings``
------------

An object of string to string, **sorted by key**, holding the scientific and
export settings in effect. It may be empty.

The map is an open namespace: later stages record Percolator's scientific
settings, the Limelight conversion parameters and the result-view q filters
used for a derived export here. So the format fixes the *shape* of a key rather
than a list of names.

**Every key matches** ``[a-z0-9]+(\.[a-z0-9-]+)+``. Dotted, lower case, at
least two segments; the first segment is a bare namespace (``percolator``,
``comet``, ``limelight``) and every later segment may contain a hyphen so that
a key can carry a scientific parameter name that already has one. There is no
underscore and no upper case anywhere. ``PERCOLATOR_SEED``,
``percolator_seed`` and ``Percolator.Seed`` are all rejected.

**Values are strings**, whatever the underlying quantity is. A seed of 9001 is
``"9001"``.

One key is pinned by this format:

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Key
     - Meaning

   * - ``percolator.seed``
     - The **effective** Percolator random seed: the seed Percolator actually
       ran with, whether the user chose it, a preset supplied it or the
       application generated it. Percolator's cross-validation is seeded, so
       two runs that differ only in this number produce different q-values, and
       a run whose seed is not recorded cannot be reproduced (``AC-PRV-10``).

``tools[]``
-----------

Each element describes one tool as it existed for one run: which binary, which
version, what it could do, and what it did.

.. list-table::
   :header-rows: 1
   :widths: 22 16 10 52

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``name``
     - string
     - no
     - The logical tool name -- ``comet``, ``percolator``. Never blank.

   * - ``version``
     - string
     - no
     - The version the tool itself reported when probed. Never blank.

   * - ``releaseTag``
     - string
     - **yes**
     - The upstream release tag or commit. ``null`` when the binary does not
       reveal one. Never an empty string: an empty tag is an absent tag
       recorded as present.

   * - ``executablePath``
     - string
     - no
     - The absolute path of the executable or JAR that was run.

   * - ``md5``
     - string
     - no
     - MD5 of that executable or JAR.

   * - ``sha256``
     - string
     - no
     - SHA-256 of it.

   * - ``managed``
     - boolean
     - no
     - ``true`` if the application installed and owns this binary, ``false`` if
       the user pointed at one already on the machine.

   * - ``artefactIdentity``
     - string
     - **yes**
     - The upstream or managed artefact this binary came from. ``null`` for a
       local binary the application did not install and therefore cannot
       attribute.

   * - ``capabilities``
     - array of strings
     - no
     - The capabilities that were **probed** on this binary, in ascending
       order. May be ``[]``. These are probed, never inferred from the version:
       what a Percolator build can do is a property of that build, and the
       mapping from version to capability is neither monotonic nor the same on
       every platform.

   * - ``stageId``
     - string
     - **yes**
     - The workflow stage that ran this tool. ``null`` for an invocation
       belonging to no stage -- a capability probe, a version query.

   * - ``execution``
     - object
     - no
     - What happened when it was run; see below.

   * - ``warnings``
     - array of strings
     - no
     - The advisories that were active for this version at the time of the run,
       in the order they were raised. May be ``[]``. The order is information:
       the first advisory is the one that mattered most when it was raised.

``tools[].execution``
---------------------

.. list-table::
   :header-rows: 1
   :widths: 24 16 10 50

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``argv``
     - array of strings
     - no
     - The exact argument array the process was launched with, executable
       first, in order. At least one element, none blank. **Redacted
       positionally** as well as textually; see
       :ref:`ref-provenance-format-redaction`.

   * - ``workingDirectory``
     - string
     - no
     - The absolute directory the process ran in.

   * - ``environment``
     - object
     - no
     - The environment variables that were set for the process, string to
       string, **sorted by key**. May be ``{}``. A variable name never contains
       ``=``. Keys are run data, not schema.

   * - ``start``
     - string
     - no
     - When the process was started.

   * - ``end``
     - string
     - no
     - When it was observed to have finished. Never before ``start``, and never
       ``null``: an execution is only recorded once it has one.

   * - ``durationMillis``
     - number
     - no
     - How long the process ran, in milliseconds.

   * - ``exitCode``
     - number
     - no
     - The process's exit status. Fits in a signed 32-bit integer and is
       **not** range-checked otherwise: on some platforms a signalled process
       reports a negative code or one above 128.

   * - ``stdout``
     - object
     - **yes**
     - The archived standard-output log; ``null`` if none was captured. A
       process killed before it opened its output files has none, and the
       format does not invent a path for a file that does not exist.

   * - ``stderr``
     - object
     - **yes**
     - The archived standard-error log, on the same terms.

   * - ``status``
     - string
     - no
     - How the process ended: ``completed``, ``failed`` or ``cancelled``.

``stdout`` and ``stderr``
-------------------------

When present, each is an object of three members.

.. list-table::
   :header-rows: 1
   :widths: 20 14 10 56

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``path``
     - string
     - no
     - The absolute path of the archived log file.

   * - ``md5``
     - string
     - no
     - MD5 of that file's contents.

   * - ``sha256``
     - string
     - no
     - SHA-256 of it.

The checksum is the half that makes the log evidence. A path alone says where a
file was, which is worth nothing once anyone can edit it; a scientist re-reading
a run can hash the archived log and see that it is the output the run recorded
and not a file that has since been trimmed, rotated or reconstructed.

Archived logs are deliberately **not** members of ``files``: a log is not an
input or an output of the scientific workflow, and putting it in the file list
would inflate the input and output counts a summary reports.

``files[]``
-----------

Each element is one file the run read or wrote. The six facts the specification
requires for every input and output file are all here, plus the two that make
them interpretable: which side of the run the file was on, and whether it is
whole.

.. list-table::
   :header-rows: 1
   :widths: 20 14 10 56

   * - Member
     - Type
     - Null?
     - Meaning

   * - ``direction``
     - string
     - no
     - ``input`` or ``output``. An input's hash proves what the tools read; an
       output's proves what they produced.

   * - ``role``
     - string
     - no
     - What the file is to the run -- ``spectra``, ``fasta``, ``pepxml`` and so
       on. Free text rather than a closed vocabulary, because a role is added
       by whichever stage produces it. Never blank.

   * - ``path``
     - string
     - no
     - The canonical, absolute path the file had at the time of the run.

   * - ``sizeBytes``
     - number
     - no
     - The file's length in bytes. Never negative; zero is legal. May exceed
       the range of a 32-bit integer, so a reader must hold it in a 64-bit
       type.

   * - ``modifiedAt``
     - string
     - no
     - The file's last-modified timestamp at the time of the run.

   * - ``md5``
     - string
     - no
     - MD5 of the content that was actually read or written.

   * - ``sha256``
     - string
     - no
     - SHA-256 of it. Both digests are always present, for every file
       (``AC-PRV-01``).

   * - ``status``
     - string
     - no
     - One of the :ref:`status values <ref-provenance-format-status>`.
       ``partial`` is the marking ``R-PROV-01`` requires for a file left behind
       by a stage that did not finish.

The size and the modification timestamp sit beside the digests because they are
what the input-hash cache is keyed on. Keeping them in the manifest lets a
later verification tell "this file changed" from "this file was hashed
wrongly", which the digests alone cannot distinguish.

A ``partial`` file is worth reading carefully. A truncated output recorded like
any other is worse than no record at all: its hash is real, it verifies, and it
describes a file that is not a result.

.. _ref-provenance-format-status:

Status values
-------------

The same five tokens are used by ``run.status``, ``execution.status`` and
``files[].status``. They are exact, lower-case, and matched without trimming or
case folding: a document containing ``Completed`` was not written by this
application.

.. list-table::
   :header-rows: 1
   :widths: 18 82

   * - Token
     - Meaning

   * - ``running``
     - Still in progress; nothing about it is final yet.

   * - ``completed``
     - Finished successfully, and the artefact is whole.

   * - ``partial``
     - The artefact exists but is not whole.

   * - ``failed``
     - Ended in an error: a non-zero exit, an unreadable input, a rejected
       parameter.

   * - ``cancelled``
     - Stopped because the user asked it to stop, which is not the same thing
       as a failure.

``execution.status`` is **written** as one of the last three only -- an
execution is recorded once it has ended. The model documents that restriction
and does not enforce it, and the reader accepts any of the five there, so a
reader of this format should not assume a document can never carry another
token in that position.

``direction`` has its own two-token vocabulary, ``input`` and ``output``, on
the same exact-match terms, and that one is enforced.

.. _ref-provenance-format-duration:

``durationMillis``, which no record carries
--------------------------------------------

``durationMillis`` appears twice in the document: in ``run`` and in every
``execution``, immediately after ``end`` so that it reads beside the two
numbers it comes from. ``AC-PRV-05`` requires that start, end, duration and
exit code are recorded for every process, and the file *is* the record -- a
duration that exists only as a method on a model the reader has to reconstruct
is not recorded in the file.

**It is a component of no record.** It is computed at the moment the document
is written, from the two instants printed next to it, and nothing in the model
carries it. That is what removes the risk a stored duration would create: there
is no third number for the two timestamps to disagree with.

**It is derived from the truncated instants.** The document shows
milliseconds, so the duration must be the one a reader can recompute from what
the document shows, not from nanoseconds the document does not contain. For a
start of ``09:14:00.250999999Z`` and an end of ``09:48:00.000Z`` the raw
instants are 2 039 749 ms apart and the recorded value is **2 039 750**, which
is the difference between ``09:14:00.250Z`` and ``09:48:00.000Z`` -- the only
two values the document actually contains.

**A reader should validate it, never store it.** A document whose duration
disagrees with its own timestamps is corrupt.

Validation a reader must also apply
===================================

A document that parses as JSON is not yet a manifest. Everything below is a
rule of the format, and a reader that skips one accepts a record that CometGUI
could not have written.

.. list-table::
   :header-rows: 1
   :widths: 34 66

   * - Rule
     - Why it is not optional

   * - **The schema version decides first.**
     - Resolve ``schemaVersion`` before looking at any other member, so that a
       document this build cannot interpret is refused for that reason and not
       for whatever else happens to be wrong with it.

   * - **Every member this format defines is present.**
     - An absent member is a schema disagreement. ``null`` is a value; absence
       is not.

   * - **Every path is absolute.**
     - The specification asks for "the canonical path at time of run", and a
       relative path is not one: it means nothing without the working directory
       that was current when it was captured, which the document does not
       record and a later reader does not have. This applies to
       ``executablePath``, ``workingDirectory``, ``files[].path`` and both log
       paths.

   * - **Both digests are present and hexadecimal**, 32 characters for MD5 and
       64 for SHA-256.
     - A file recorded with one digest is a file half-verified. A writer emits
       lower case; a reader that compares digest strings should normalise case
       rather than assume it, because the model's own check accepts either.

   * - **Every settings key matches**
       ``[a-z0-9]+(\.[a-z0-9-]+)+``.
     - Without the rule, two stages spell the same key two ways, both are
       accepted, and a reader looking for one silently misses the other.

   * - **Every language tag is canonical**, in the exact form
       ``Locale.toLanguageTag()`` writes.
     - ``Locale.forLanguageTag`` never fails: handed something that is not a
       language tag it returns the root locale, so a corrupted tag would be
       read back as "no locale" and the field that exists to explain a
       locale-dependent difference would be the field that quietly lost its
       value. Re-render the parsed tag and require it to match the document.

   * - **Every time zone resolves** to a real zone id.
     - Same argument, one field along.

   * - ``durationMillis`` **equals the millisecond difference of the two
       instants beside it**, and is ``null`` exactly when ``end`` is.
     - This is the one arithmetic claim the document makes about itself. An
       interval that has not ended has no duration, so a number there is
       refused too.

   * - **No** ``end`` **is before its** ``start``.
     - A negative duration is not a value a report can render or a reader can
       trust.

   * - ``sizeBytes`` **is not negative**, ``role`` and ``projectId`` are not
       blank, and ``runId`` is a legal run identifier.
     - Each of these is a value that also names something on disk or in a
       report.

   * - **A status or a direction is matched exactly.**
     - No trimming, no case folding, no "close enough". Quietly accepting
       ``Completed`` would mean a record whose meaning cannot be re-derived
       from its own bytes.

   * - **Timestamps resolve strictly.**
     - Under a lenient resolver ``2026-02-30`` does not fail: it becomes 28
       February, so a corrupted date is read back as a different, plausible
       one.

The JSON itself is read strictly as well, and each of these is a refusal rather
than a repair: a duplicate member name in one object (first-wins and last-wins
are both defensible and they disagree); a trailing comma, a comment, a
single-quoted string or an unquoted member name (each is a dialect some tool
emits, and every one of them means the file was written or edited by something
that is not this application); ``NaN``, ``Infinity``, a fraction, an exponent, a
leading zero or a leading ``+``; an integer outside the range of a signed
64-bit type, rejected rather than widened, because silently losing the low
digits of a byte count is exactly the defect a provenance record rules out; a
raw control character in a string, an unknown escape, a malformed ``\u`` escape
or an unpaired surrogate; a byte-order mark before the document; and anything
at all after the top-level value -- two documents concatenated by a failed
write look exactly like one valid document followed by rubbish, and that is the
shape this catches.

.. _ref-provenance-format-redaction:

Redaction, as a reader sees it
==============================

``R-SEC-03`` requires that credentials, tokens and passwords never reach a
provenance record. They are removed **before** the document is written, and the
consequences for a reader are three.

**The marker is a value in the document.** A redacted value is the literal
string ``[REDACTED]``, and it appears wherever any other string value could.
It is square-bracketed rather than asterisked because the same rule set feeds
``provenance.rst``, where ``**`` opens strong emphasis.

**Redaction is one-way.** The document does not carry the original, does not
say what rule fired, and does not mark the member in any other way. Nothing in
the file lets a reader recover or identify what was removed.

**A value that equals the marker is ambiguous, and the format does not resolve
the ambiguity.** ``"[REDACTED]"`` may be a value that was cleared, or a value
that genuinely was the eleven characters ``[REDACTED]``. A reader must not
report one as the other, and must not treat the marker as a sentinel meaning
"absent" -- absence is ``null``.

Three kinds of redaction reach the document, which is worth knowing when
reading one:

* **Textual.** Every string value in the document has been through the rules --
  credential-bearing URLs, ``Authorization`` headers, assignments whose name
  looks secret, recognisable token formats, PEM private keys. So a settings
  value may read
  ``https://ll-user:[REDACTED]@ll.example.org/up``: the host and the user
  survive, because a provenance record that redacted the server it uploaded to
  would be useless, and only the password is gone.
* **Positional, in** ``argv``. The element after a long secret-bearing flag is
  replaced whole -- the value after ``--password`` looks like any other word,
  and no text rule could see it. **Only long** ``--`` **flags do this**;
  single-letter options are ordinary options for real scientific tools
  (Comet's own ``-P`` names the parameter file), so a positional rule over them
  would blank a file path about as often as a password.
* **By name, in** ``environment``. A variable named ``LIMELIGHT_API_KEY`` or
  ``GITHUB_TOKEN`` holds a credential whatever its value looks like, so its
  value is replaced whole. **The name survives**, always: names are part of the
  schema and of the run's shape, and a member called ``[REDACTED]`` would be
  useless to everyone.

Non-secret content is byte-identical to what the run held. Over-redaction is
not a safe failure here -- it corrupts a record the specification requires to
be complete -- so a path, a digest, a version number and a Comet parameter line
all come through untouched.

.. _ref-provenance-format-event-log:

The event log
=============

A manifest is written once, at the end. **A run that crashed has no manifest**,
and what it leaves instead is the event log: an append-only file that grows
while the run happens and is readable at every instant in between. Each record
is on the storage device before the append that wrote it is reported complete,
so a log still sitting in a buffer when the JVM dies is not a state this format
permits.

The line format
---------------

One JSON object per line, terminated by ``\n``. The bytes are UTF-8. There is
no enclosing array and no document-level structure of any kind::

    {"seq":1,"time":"2026-08-31T09:15:00.000Z","type":"run.started","payload":{"run.id":"R-1"}}
    {"seq":2,"time":"2026-08-31T09:15:01.000Z","type":"stage.started","payload":{"stage":"comet"}}

That is the whole recovery strategy. A single top-level JSON array cannot
survive a crash, because the closing bracket is written last and a dying
process never writes it -- every general-purpose parser then rejects the entire
document and a run's whole history is lost to one missing character. With one
record per line the damage a crash does is confined to the last one.

A line is **compact**: no spaces anywhere outside string values, and the four
members always in this order.

.. list-table::
   :header-rows: 1
   :widths: 14 16 70

   * - Member
     - Type
     - Meaning

   * - ``seq``
     - number
     - This event's position in the log, counting from 1, with no gaps. Written
       in ASCII digits only. The log assigns it; a caller cannot choose one.

   * - ``time``
     - string
     - When the event happened, in the same fixed-width UTC form the manifest
       uses, and truncated to milliseconds the same way. The year is always
       four digits.

   * - ``type``
     - string
     - What happened; one of the seven
       :ref:`event types <ref-provenance-format-event-types>`.

   * - ``payload``
     - object
     - The details: string to string, **sorted by key**, possibly ``{}``.

**Payload keys** match ``(?:[a-z0-9]+(\.[a-z0-9-]+)+)|[a-z0-9]+`` -- the
settings-key rule, plus the one relaxation that a single bare segment is legal.
A payload is scoped to one event and that event already carries its type, so a
``stage`` key inside a ``stage.started`` event cannot collide with anything and
spelling it ``stage.name`` would only restate the type. The anti-drift half of
the rule is kept in full: lower-case ASCII, digits, dots and hyphens; no
underscore, no camel case, no space, no empty segment.

The keys this format pins are ``run.id``, ``stage``, ``tool``,
``tool.version``, ``file.path``, ``file.md5``, ``file.sha256``, ``message`` and
``status``.

Payload values are redacted exactly as the manifest's are, by the same rule
set.

.. _ref-provenance-format-event-types:

Event types
-----------

.. list-table::
   :header-rows: 1
   :widths: 22 78

   * - ``type``
     - What it says happened

   * - ``run.started``
     - The run began: the moment everything later in the log is relative to.

   * - ``stage.started``
     - One stage of the workflow began -- the search, the rescoring, a
       conversion.

   * - ``stage.finished``
     - One stage ended, however it ended. The outcome is in the payload; the
       type says only that the stage is no longer running.

   * - ``tool.invoked``
     - A tool was launched.

   * - ``file.hashed``
     - A file's MD5 and SHA-256 were computed, so that the digests are in the
       log as they land.

   * - ``warning.raised``
     - Something the run survived but a scientist must be told about.

   * - ``run.finished``
     - The run ended. **An event of this type always carries the terminal
       status** under the ``status`` key, as one of the
       :ref:`status values <ref-provenance-format-status>`. A log
       whose last line said only "the run finished" could not answer the
       question a failed run raises.

Reading a damaged log
---------------------

Recovery **does not fail on damage** -- damage is what it is for. A file that
cannot be opened or read at all is an error; anything wrong with the file's
*content* comes back as a list of defects beside every event that survived.

Both halves matter and neither may be inferred from the other. A reader that
threw on the first bad byte would discard a crashed run's history -- and the
crashed run is the one whose history is worth having, because the successful
one has a manifest. A reader that quietly skipped what it could not parse would
hand back a plausible-looking history with an unannounced hole in it.

.. list-table::
   :header-rows: 1
   :widths: 26 74

   * - Defect
     - What it means and how it is treated

   * - **A torn final line**
     - Bytes after the last newline. This is the ordinary signature of a crash:
       the process died between the write and the terminator. **Those bytes are
       never parsed, even when they look complete** -- without the terminator
       there is no way to tell a whole record from one whose tail never reached
       the disk.

   * - **A malformed line**
     - A line that *does* end in a newline and is not a record this application
       wrote: an empty line, a line that is not valid UTF-8, a line whose JSON
       is not the exact form above. Trailing ``NUL`` bytes -- what several
       filesystems leave in a file's last block after a power loss -- arrive
       here. It means something other than a crash: a file that was edited,
       transferred in text mode, concatenated, or damaged.

   * - **A sequence gap**
     - A line whose ``seq`` is not one more than the previous one. This is the
       only damage that leaves no trace in the bytes: a lost record takes its
       whole line with it and every remaining line is perfectly well formed.
       Reading continues from the number actually found, so one hole produces
       one defect rather than one per later line.

Every defect carries its line number, the byte offset at which the damaged line
starts, and what was wrong -- and **never a quotation from the file**. The
bytes of a damaged log are not necessarily bytes this application wrote and
redacted, so a message echoing them could carry a credential out of a file and
into a log or a screen.

Two further properties a reader may rely on:

* **An empty file is a valid, intact log with no events.** A run can die before
  its first append, and there is nothing wrong with what it left behind.
* **A resumed run heals the tear.** Reopening a log whose last line has no
  terminator writes the newline first, then continues from one past the highest
  sequence number found. Without that, the first new record would be
  concatenated onto the torn one and the two would read back as a single
  malformed line -- the old crash would have eaten a record from the new run.

Where each specification fact is recorded
=========================================

The specification's tool-provenance and application-provenance lists name
facts, not members. Most have a member of their own; the rest live in the two
open namespaces and are written by the stage that owns them.

.. list-table::
   :header-rows: 1
   :widths: 44 56

   * - Fact
     - Where it is

   * - Logical tool name, reported version, release tag, executable path and
       its two digests, managed-or-local, artefact identity, probed
       capabilities, argument array, working directory, environment, start,
       end, duration, exit code, log paths and checksums, failure state,
       version warnings
     - ``tools[]`` and ``tools[].execution``, each with its own member.

   * - A safely rendered command for display
     - **Not a member.** It is a pure function of ``argv``, and the format
       records the array rather than a second rendering of it that could
       disagree with it.

   * - CometGUI version, build identifier, OS and version, architecture, JVM
       version, locale, time zone
     - ``application``, each with its own member.

   * - Project and run identifiers
     - ``run.projectId`` and ``run.runId``.

   * - The generated Comet parameter file's hash, and the archived copy
     - ``files[]``, as an entry with a Comet-parameter ``role``. Archiving the
       copy into the run directory is the search stage's job; this format
       records the file that resulted.

   * - Percolator scientific settings including the effective seed
     - ``settings``, under ``percolator.seed`` and the stage's own keys.

   * - Result-view q filters for a derived export, Limelight conversion
       parameters
     - ``settings``, under keys the exporting and converting stages pin.

   * - PDV launch and version
     - ``tools[]``, as an invocation like any other.

Format history
==============

.. list-table::
   :header-rows: 1
   :widths: 14 22 64

   * - Version
     - Introduced
     - Change

   * - 1
     - Phase 04
     - The first published format: everything on this page.
